package com.google.cloud.bigtable.gaxx.grpc;

import java.time.Instant;
import java.time.Duration;
import com.google.cloud.bigtable.gaxx.grpc.BigtableChannelPool.Entry;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.function.Supplier;
import com.google.common.collect.ImmutableList;
public class ChannelPoolHealthChecker {
  // private final BigtableChannelPool channelPool;
  private final Supplier<ImmutableList<Entry>> entrySupplier;
  private Map<Entry, ChannelHealthChecker> healthCheckers;
  private Instant lastEviction = Instant.MIN;
  private ScheduledExecutorService executor;

  // Assume this is called by some other code. what does that mean?
  public ChannelPoolHealthChecker(Supplier<ImmutableList<Entry>> entrySupplier) {
    this.healthCheckers = Collections.synchronizedMap(new WeakHashMap<Entry, ChannelHealthChecker>());
    this.entrySupplier = entrySupplier;
    // Last time that a channel was evicted. Used for rate-limiting.
    this.lastEviction = Instant.MIN;
    this.executor = Executors.newSingleThreadScheduledExecutor();
    int EVALUATION_FREQUENCY = 30; //seconds
    int INITIAL_DELAY = 30; //seconds

    executor.scheduleAtFixedRate(this::detectAndRemoveOutlierEntries, INITIAL_DELAY, EVALUATION_FREQUENCY, TimeUnit.SECONDS);
  }

  @Nullable
  private Entry findOutlierEntry() {
    ArrayList<ChannelHealthChecker> unhealthyEntries = new ArrayList<ChannelHealthChecker>();
    for (Entry entry: entrySupplier.get()) {
      ChannelHealthChecker healthChecker = this.healthCheckers.computeIfAbsent(entry, e -> new ChannelHealthChecker(e, executor));
      if (!healthChecker.healthy()) {
        unhealthyEntries.add(healthChecker);
      }
    }
    if (unhealthyEntries.isEmpty()) {
      return null;
    }
    int BAD_CHANNEL_CIRCUITBREAKER_PERCENT = 70; // percent
    // Round up so that pools with 1 channel can still evict the channel.
    int maxUnhealthyEntries = (int) Math.ceil(this.entrySupplier.get().size() * BAD_CHANNEL_CIRCUITBREAKER_PERCENT / 100.0);
    if (unhealthyEntries.size() > maxUnhealthyEntries) {
      // Too many channels are bad. The problem isn't one that's likely to be solved by
      // evicting channels - and, in fact, may be exacerbated by churn.
      return null;
    }
    // Pick the channel with the most failed probes.
    unhealthyEntries.sort(Comparator.comparingInt(ChannelHealthChecker::recentlyFailedProbes).reversed());
    return unhealthyEntries.get(0).entry;
  }

  private void detectAndRemoveOutlierEntries() {
    Duration MIN_EVICTION_INTERVAL = Duration.ofMinutes(10); // minutes
    if (Instant.now().isBefore(this.lastEviction.plus(MIN_EVICTION_INTERVAL))) {
      // Primitive but effective rate-limiting.
      return;
    }
    Entry outlier = findOutlierEntry();
    if (outlier != null) {
      this.lastEviction = Instant.now();
      outlier.getManagedChannel().enterIdle();
      if (this.healthCheckers.containsKey(outlier)) {
        this.healthCheckers.get(outlier).stop();
        this.healthCheckers.remove(outlier);
      }
    }
  }
}
