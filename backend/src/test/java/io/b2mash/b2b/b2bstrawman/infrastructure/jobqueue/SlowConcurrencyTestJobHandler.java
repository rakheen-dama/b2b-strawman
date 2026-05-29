package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test handler that sleeps briefly and records the maximum number of concurrent executions
 * observed. Used by {@code JobWorkerParallelismTest} (S2) to prove the worker executes a claimed
 * batch with real in-pod parallelism rather than one job at a time.
 */
@Component
@Profile("test")
public class SlowConcurrencyTestJobHandler implements JobHandler {

  public static final String JOB_TYPE = "slow_concurrency_test_job";
  private static final long SLEEP_MS = 300;

  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger maxInFlight = new AtomicInteger();

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int now = inFlight.incrementAndGet();
    maxInFlight.accumulateAndGet(now, Math::max);
    try {
      Thread.sleep(SLEEP_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      inFlight.decrementAndGet();
    }
  }

  /** Highest number of concurrent {@link #execute} calls seen since the last {@link #clear()}. */
  public int maxInFlight() {
    return maxInFlight.get();
  }

  public void clear() {
    inFlight.set(0);
    maxInFlight.set(0);
  }
}
