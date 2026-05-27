package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test handler that records execution calls for integration test assertions. Stores the tenant ID
 * from the bound {@code ScopedValue} into a concurrent queue.
 */
@Component
@Profile("test")
public class TestJobHandler implements JobHandler {

  public static final String JOB_TYPE = "test_job";

  private final ConcurrentLinkedQueue<String> executions = new ConcurrentLinkedQueue<>();

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    executions.add(RequestScopes.requireTenantId());
  }

  /** Returns all recorded tenant IDs in execution order. */
  public ConcurrentLinkedQueue<String> getExecutions() {
    return executions;
  }

  /** Clears the execution record — call in {@code @BeforeEach}. */
  public void clear() {
    executions.clear();
  }
}
