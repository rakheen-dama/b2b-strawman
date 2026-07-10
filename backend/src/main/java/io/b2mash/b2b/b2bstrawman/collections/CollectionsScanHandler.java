package io.b2mash.b2b.b2bstrawman.collections;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for the daily collections scan (Phase 83, ADR-325). Tenant scope is pre-bound by the
 * {@code JobWorker}; this delegates to {@link CollectionsScanService#scanForTenant()}.
 */
@Component
public class CollectionsScanHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(CollectionsScanHandler.class);

  private final CollectionsScanService scanService;

  public CollectionsScanHandler(CollectionsScanService scanService) {
    this.scanService = scanService;
  }

  @Override
  public String jobType() {
    return "collections_scan";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    var result = scanService.scanForTenant();
    log.info(
        "CollectionsScanHandler: scan finished — proposed={}, skipped={}, escalated={},"
            + " superseded={}",
        result.proposed(),
        result.skipped(),
        result.escalated(),
        result.superseded());
  }
}
