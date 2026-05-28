package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for polling external accounting systems for payment data for a single tenant.
 * Delegates to {@link AccountingPaymentPollWorker#pollForTenant()} which queries CONNECTED Xero
 * connections and polls each for payment updates.
 *
 * <p>Extracted from {@link AccountingPaymentPollWorker#pollAllConnections()}.
 */
@Component
public class AccountingPaymentPollHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AccountingPaymentPollHandler.class);

  private final AccountingPaymentPollWorker pollWorker;

  public AccountingPaymentPollHandler(AccountingPaymentPollWorker pollWorker) {
    this.pollWorker = pollWorker;
  }

  @Override
  public String jobType() {
    return "accounting_payment_poll";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int polled = pollWorker.pollForTenant();
    if (polled > 0) {
      log.info("AccountingPaymentPollHandler: polled {} connections", polled);
    }
  }
}
