package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for draining pending accounting sync entries for a single tenant. Delegates to {@link
 * AccountingSyncWorker#drainForTenant()} which contains the batch processing, retry, back-off, and
 * dead-letter logic.
 *
 * <p>Extracted from {@link AccountingSyncWorker#drainPendingEntries()}.
 */
@Component
public class AccountingSyncDrainHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncDrainHandler.class);

  private final AccountingSyncWorker syncWorker;

  public AccountingSyncDrainHandler(AccountingSyncWorker syncWorker) {
    this.syncWorker = syncWorker;
  }

  @Override
  public String jobType() {
    return "accounting_sync_drain";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int processed = syncWorker.drainForTenant();
    if (processed > 0) {
      log.info("AccountingSyncDrainHandler: drained {} entries", processed);
    }
  }
}
