package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled worker that polls external accounting systems for payment data across all tenants.
 * Iterates tenants via {@link TenantScopedRunner}, queries CONNECTED Xero connections, and
 * delegates to {@link AccountingSyncService#pollPaymentsForConnection} for each connection.
 *
 * <p>Per-connection exception isolation ensures that a failure polling one connection does not
 * prevent polling of remaining connections. Per-tenant isolation is provided by {@link
 * TenantScopedRunner#forEachTenant}.
 *
 * <p>Payment matching logic delegated to {@link AccountingSyncService#pollPaymentsForConnection}:
 * matches external payments to Kazi invoices, detects amount drift, and transitions invoices to
 * PAID on successful reconciliation.
 */
@Service
public class AccountingPaymentPollWorker {

  private static final Logger log = LoggerFactory.getLogger(AccountingPaymentPollWorker.class);

  private final TenantScopedRunner tenantScopedRunner;
  private final AccountingXeroConnectionRepository xeroConnectionRepository;
  private final AccountingSyncService syncService;

  public AccountingPaymentPollWorker(
      TenantScopedRunner tenantScopedRunner,
      AccountingXeroConnectionRepository xeroConnectionRepository,
      AccountingSyncService syncService) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.xeroConnectionRepository = xeroConnectionRepository;
    this.syncService = syncService;
  }

  @Scheduled(fixedDelay = 900_000)
  public void pollAllConnections() {
    log.debug("AccountingPaymentPollWorker: starting poll cycle");
    int[] totalPolled = {0};

    tenantScopedRunner.forEachTenant(
        (tenantId, orgId) -> {
          int polled = pollForTenant();
          totalPolled[0] += polled;
        });

    if (totalPolled[0] > 0) {
      log.info("AccountingPaymentPollWorker: poll cycle complete. Polled: {}", totalPolled[0]);
    }
  }

  private int pollForTenant() {
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
