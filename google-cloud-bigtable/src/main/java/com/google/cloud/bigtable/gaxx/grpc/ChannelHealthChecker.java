package com.google.cloud.bigtable.gaxx.grpc;

import com.google.common.collect.EvictingQueue; // Assuming Guava EvictingQueue
    import com.google.common.util.concurrent.FutureCallback;
    import com.google.common.util.concurrent.Futures;
    import com.google.common.util.concurrent.ListenableFuture;

    import java.time.Instant;
    import java.util.concurrent.Executor; // For general Executor interface
    import java.util.concurrent.Future;
    import java.util.concurrent.ScheduledExecutorService;
    import java.util.concurrent.ScheduledFuture;
    import java.util.concurrent.TimeUnit;
    import java.util.concurrent.atomic.AtomicInteger;
    import java.util.concurrent.locks.ReadWriteLock;
    import java.util.concurrent.locks.ReentrantReadWriteLock;
// Import for your 'Channel' interface/class and 'probe' method

// Placeholder for Channel and probe method
interface Channel {
  // ...
}

public class ChannelHealthChecker {

  // Configuration constants
  private static final int WINDOW_DURATION_MINUTES = 5; // minutes
  private static final int PROBE_RATE_SECONDS = 30; // seconds
  private static final int PROBE_DEADLINE_MILLISECONDS = 500; // milliseconds
  private static final int MIN_PROBES_FOR_EVALUATION = 4; // Changed name for clarity
  private static final int FAILURE_PERCENT_THRESHOLD = 60; // percent

  private final Channel channel;
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
  public ChannelHealthChecker(Channel channel, ScheduledExecutorService executor) {
    // Calculate queue capacity: 5 minutes * 60 seconds/minute / 30 seconds/probe = 10 probes
    int queueCapacity = (WINDOW_DURATION_MINUTES * 60) / PROBE_RATE_SECONDS;
    this.probeResults = EvictingQueue.create(queueCapacity); // Guava EvictingQueue
    this.channel = channel;
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
    probesInFlight.incrementAndGet(); // Increment when we *attempt* to run a probe

    try {
      // Assume 'probe' method is provided externally and returns a ListenableFuture<Boolean>
      // (e.g., from a gRPC channel or similar)
      ListenableFuture<Boolean> probeFuture = (ListenableFuture<Boolean>) probe(channel, PROBE_DEADLINE_MILLISECONDS, startTime);

      Futures.addCallback(
          Futures.withTimeout(probeFuture, PROBE_DEADLINE_MILLISECONDS, TimeUnit.MILLISECONDS, probeExecutor),
          new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              probeFinished(startTime, result);
            }

            @Override
            public void onFailure(Throwable t) {
              // This handles timeouts and other exceptions during probe execution
              probeFinished(startTime, /*success=*/ false);
              // Consider logging the error here
              // System.err.println("Probe failed or timed out: " + t.getMessage());
            }
          },
          probeExecutor // Execute the callback on the probeExecutor's thread
      );
    } catch (Exception e) {
      // This catches exceptions that happen *before* the ListenableFuture is successfully created/scheduled
      // e.g., if the 'probe' method itself throws an immediate exception
      probeResultsLock.writeLock().lock();
      try {
        probeResults.add(new ProbeResult(startTime, false));
      } finally {
        probeResultsLock.writeLock().unlock();
      }
      probesInFlight.decrementAndGet(); // Decrement as the probe never truly "started" as a Future
      // Consider logging this failure to schedule/start the probe
      // System.err.println("Failed to start probe: " + e.getMessage());
    }
  }

  // This method is called by the FutureCallback when the probe (successfully or unsuccessfully) completes
  void probeFinished(Instant startTime, boolean success) {
    probeResultsLock.writeLock().lock();
    try {
      probeResults.add(new ProbeResult(startTime, success));
    } finally {
      probeResultsLock.writeLock().unlock();
    }
    probesInFlight.decrementAndGet();
  }

  private int recentProbesSent() {
    // Also count in-flight requests.
    int historicalProbesCount;
    probeResultsLock.readLock().lock();
    try {
      historicalProbesCount = probeResults.size();
    } finally {
      probeResultsLock.readLock().unlock();
    }
    return probesInFlight.get() + historicalProbesCount;
  }

  public int recentlyFailedProbes() {
    // A failure is any probe that
    // - Returned any error code
    // - Has not completed (is still in flight)
    // - Failed to start (handled in runProbe's catch block, added to probeResults)

    // Count in-flight requests as failed.
    int failedProbes = probesInFlight.get(); // Probes currently in flight are considered failed for health check

    probeResultsLock.readLock().lock();
    try {
      for (ProbeResult probeResult : probeResults) {
        if (!probeResult.isSuccessful()) {
          failedProbes++;
        }
      }
    } finally {
      probeResultsLock.readLock().unlock();
    }
    return failedProbes;
  }

  public boolean healthy() {
    int totalProbes = recentProbesSent();
    if (totalProbes == 0) {
      // No probes sent yet. Default to healthy until enough data is collected.
      return true;
    }

    // If there aren't enough probes for a meaningful statistical evaluation,
    // consider the system healthy. This prevents premature "unhealthy" status.
    if (totalProbes < MIN_PROBES_FOR_EVALUATION) {
      return true;
    }

    int failedProbes = recentlyFailedProbes();
    // Calculate failure percentage using double for accurate division
    double failureRate = ((double) failedProbes / totalProbes) * 100;

    return failureRate < FAILURE_PERCENT_THRESHOLD;
  }

  // Placeholder for the external probe method
  // This method should ideally return a ListenableFuture<Boolean>
  private Future<?> probe(Channel channel, int deadlineMillis, Instant startTime) {
    // Simulate an asynchronous probe operation
    // In a real scenario, this would involve calling a service,
    // potentially with a timeout, and returning a Future.
    return probeExecutor.schedule(() -> {
      // Simulate probe success/failure randomly for demonstration
      return Math.random() > 0.2; // 80% success rate
    }, (long) (Math.random() * deadlineMillis), TimeUnit.MILLISECONDS);
  }
}
