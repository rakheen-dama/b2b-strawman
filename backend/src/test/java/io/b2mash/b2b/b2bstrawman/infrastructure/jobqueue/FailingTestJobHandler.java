package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test handler that always throws to exercise the retry and dead-letter paths. Tracks the number of
 * execution attempts for assertions.
 */
@Component
@Profile("test")
public class FailingTestJobHandler implements JobHandler {

  public static final String JOB_TYPE = "failing_test_job";

  private final AtomicInteger attemptCount = new AtomicInteger(0);

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    attemptCount.incrementAndGet();
    throw new RuntimeException("Simulated job failure");
  }

  /** Returns the total number of execution attempts. */
  public int getAttemptCount() {
    return attemptCount.get();
  }

  /** Resets the attempt counter — call in {@code @BeforeEach}. */
  public void clear() {
    attemptCount.set(0);
  }
}
