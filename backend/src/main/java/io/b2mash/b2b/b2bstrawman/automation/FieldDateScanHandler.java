package io.b2mash.b2b.b2bstrawman.automation;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for scanning DATE-type custom fields for a single tenant. Delegates to {@link
 * FieldDateScannerJob#scanTenant()} which checks field values against threshold days and publishes
 * approaching-date events.
 *
 * <p>Extracted from {@link FieldDateScannerJob#execute()}.
 */
@Component
public class FieldDateScanHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(FieldDateScanHandler.class);

  private final FieldDateScannerJob scannerJob;

  public FieldDateScanHandler(FieldDateScannerJob scannerJob) {
    this.scannerJob = scannerJob;
  }

  @Override
  public String jobType() {
    return "field_date_scan";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int fired = scannerJob.scanTenant();
    if (fired > 0) {
      log.info("FieldDateScanHandler: fired {} date-approaching events", fired);
    }
  }
}
