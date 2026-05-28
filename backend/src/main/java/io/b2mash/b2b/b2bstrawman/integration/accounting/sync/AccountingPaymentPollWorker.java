package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled worker that polls external accounting systems for payment data across all tenants.
 * Enqueues per-tenant jobs via {@link JobEnqueuer#fanOutToAllTenants}; per-tenant work is handled
 * by {@link AccountingPaymentPollHandler}. Queries CONNECTED Xero connections and delegates to
 * {@link AccountingSyncService#pollPaymentsForConnection} for each connection.
 *
 * <p>Per-connection exception isolation ensures that a failure polling one connection does not
 * prevent polling of remaining connections. Per-tenant isolation is provided by the job queue
 * worker's ScopedValue binding.
 *
 * <p>Payment matching logic delegated to {@link AccountingSyncService#pollPaymentsForConnection}:
 * matches external payments to Kazi invoices, detects amount drift, and transitions invoices to
 * PAID on successful reconciliation.
 */
@Service
public class AccountingPaymentPollWorker {

  private static final Logger log = LoggerFactory.getLogger(AccountingPaymentPollWorker.class);

  private final AccountingXeroConnectionRepository xeroConnectionRepository;
  private final AccountingSyncService syncService;
  private final JobEnqueuer jobEnqueuer;

  public AccountingPaymentPollWorker(
      AccountingXeroConnectionRepository xeroConnectionRepository,
      AccountingSyncService syncService,
      JobEnqueuer jobEnqueuer) {
    this.xeroConnectionRepository = xeroConnectionRepository;
    this.syncService = syncService;
    this.jobEnqueuer = jobEnqueuer;
  }

  @SchedulerLock(name = "accounting_payment_poll_all_connections", lockAtLeastFor = "7m")
  @Scheduled(fixedDelay = 900_000)
  public void pollAllConnections() {
    log.debug("AccountingPaymentPollWorker: starting poll cycle");
    jobEnqueuer.fanOutToAllTenants("accounting_payment_poll", null);
  }

  int pollForTenant() {
    var connections = xeroConnectionRepository.findByStatus(XeroConnectionStatus.CONNECTED);
    int polled = 0;

    for (var connection : connections) {
      try {
        syncService.pollPaymentsForConnection(connection.getId());
        polled++;
      } catch (Exception e) {
        log.error(
            "Payment poll failed for connection {}: {}", connection.getId(), e.getMessage(), e);
      }
    }

    return polled;
  }
}
