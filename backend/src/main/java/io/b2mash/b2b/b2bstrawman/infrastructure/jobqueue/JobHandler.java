package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

/**
 * Interface for job execution dispatch. Implementations are discovered via Spring
 * {@code @Component} scanning and registered by {@link JobHandlerRegistry}.
 *
 * <p>The {@link #jobType()} return value must be unique across all registered handlers. The worker
 * calls {@link #execute(JsonNode)} with tenant scope already bound via {@code ScopedValue}.
 */
public interface JobHandler {

  /**
   * Returns the unique job type identifier this handler processes (e.g., "INVOICE_GENERATION").
   * Must be stable across restarts and unique across all registered handlers.
   */
  String jobType();

  /**
   * Executes the job with the given payload. Called by {@link JobWorker} with tenant scope ( {@code
   * TENANT_ID}, {@code ORG_ID}) already bound via {@code ScopedValue}.
   *
   * @param payload optional JSONB payload with job-type-specific data; may be null
   */
  void execute(@Nullable JsonNode payload);
}
