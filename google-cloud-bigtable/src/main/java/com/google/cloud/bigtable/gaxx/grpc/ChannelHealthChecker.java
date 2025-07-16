package com.google.cloud.bigtable.gaxx.grpc;

import com.google.common.collect.EvictingQueue; // Assuming Guava EvictingQueue
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.cloud.bigtable.gaxx.grpc.BigtableChannelPool.Entry;

import io.grpc.internal.ClientTransport.PingCallback;
import java.time.Instant;
import java.util.concurrent.Executor; // For general Executor interface
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChannelHealthChecker {

  // Configuration constants
  private static final int WINDOW_DURATION_MINUTES = 5; // minutes
  private static final int PROBE_RATE_SECONDS = 30; // seconds
  private static final int PROBE_DEADLINE_MILLISECONDS = 500; // milliseconds
  private static final int MIN_PROBES_FOR_EVALUATION = 4; // Changed name for clarity
  private static final int FAILURE_PERCENT_THRESHOLD = 60; // percent

  final Entry entry;
  private final ScheduledExecutorService probeExecutor; // Executor for running probes

  // Use volatile for shared mutable reference or ensure thread-safe assignment if reassigned
  private volatile ScheduledFuture<?> scheduledProbeFuture;

  // Use a ReadWriteLock for thread-safe access to probeResults
  private final ReadWriteLock probeResultsLock = new ReentrantReadWriteLock();
  private final EvictingQueue<ProbeResult> probeResults;

  // Count of probes currently in flight
  private final AtomicInteger probesInFlight = new AtomicInteger(0);

  class ProbeResult {
    final Instant startTime;
    final boolean success;

    ProbeResult(Instant startTime, boolean success) {
      this.startTime = startTime;
      this.success = success;
    }

    public boolean isSuccessful() {
      return success;
    }
  }

  // Assume this is called by some other code.
  // The executor passed should be a ScheduledExecutorService
  public ChannelHealthChecker(Entry entry, ScheduledExecutorService executor) {
    // Calculate queue capacity: 5 minutes * 60 seconds/minute / 30 seconds/probe = 10 probes
    int queueCapacity = (WINDOW_DURATION_MINUTES * 60) / PROBE_RATE_SECONDS;
    this.probeResults = EvictingQueue.create(queueCapacity); // Guava EvictingQueue
    this.entry = entry;
    this.probeExecutor = executor; // Initialize the executor

    // TODO: add some jitter. This scheduleAtFixedRate doesn't inherently add jitter.
    // For jitter, consider `schedule` with a random delay after each probe completes.
    this.scheduledProbeFuture = probeExecutor.scheduleAtFixedRate(
        this::runProbe,
        0, // Initial delay (can be 0 or small random)
        PROBE_RATE_SECONDS,
        TimeUnit.SECONDS
    );
  }

  public void stop() {
    if (scheduledProbeFuture != null) {
      scheduledProbeFuture.cancel(false); // Do not interrupt if running
      // No need to null out scheduledProbeFuture here if no restart mechanism
    }
    // If probeExecutor is private to this instance and not shared, consider shutting it down.
    // if (probeExecutor != null) {
    //     probeExecutor.shutdown();
    //     try {
    //         if (!probeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
    //             probeExecutor.shutdownNow();
    //         }
    //     } catch (InterruptedException ie) {
    //         probeExecutor.shutdownNow();
    //         Thread.currentThread().interrupt();
    //     }
    // }
  }

  private void runProbe() {
    Instant startTime = Instant.now();
    probesInFlight.incrementAndGet();
    try {
      // Assume that this probe method has been provided somewhere (in a similar manner to how channel priming works).
      int PROBE_DEADLINE = 500; // milliseconds
      Future probeFuture = PingAndWarm(channel, PROBE_DEADLINE, startTime);
      Futures.withTimeout(probeFuture, PROBE_DEADLINE, this.probeExecutor).addListener(this::probeFinished);
    catch {
        probeResults.add(ProbeResult(startTime, /*success=*/False));
        // TODO: are there cases where we double-decrement due to calling the listener and then getting an exception?
        probesInFlight.decrementAndGet();
      }
     }
    }

    void probeFinished(Instant startTime, bool success) {
      probeResults.add(ProbeResult(startTime, success));
      probesInFlight.decrementAndGet();
    }

    private int recentProbesSent() {
      // Also count in-flight requests.
      return probesInFlight.get() + probeResults.length();
    }

    public int recentlyFailedProbes() {
      // A failure is any probe that
      // - Returned any error code
      // - Has not completed (is still in flight)
      // - Failed to start

      // Count in-flight requests as failed.
      int failedProbes = probesInFlight.get();
      for (probeResult: probeResults) {
        if (!result.isSuccessful()) {
          failedProbes++;
        }
      }
      return failedProbes;
    }

    public boolean healthy() {
      MIN_PROBES_FOR_EVICTION = 4;
      int FAILURE_PERCENT = 60; // percent
      return recentProbesSent() <= MIN_PROBES_FOR_EVICTION || (recentlyFailedProbes() / recentProbesSent()) * 100 < FAILURE_PERCENT;
    }
  }
