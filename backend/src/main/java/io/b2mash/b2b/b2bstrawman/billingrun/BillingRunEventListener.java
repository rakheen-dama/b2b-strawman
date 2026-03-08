package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunCompletedEvent;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunFailuresEvent;
import io.b2mash.b2b.b2bstrawman.billingrun.BillingRunEvents.BillingRunSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BillingRunEventListener {

  private static final Logger log = LoggerFactory.getLogger(BillingRunEventListener.class);

  private final NotificationService notificationService;

  public BillingRunEventListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @EventListener
  public void onBillingRunCompleted(BillingRunCompletedEvent event) {
    handleInCurrentTenantScope(
        () -> {
          try {
            var title =
                "Billing run \"%s\" completed — %d invoices generated"
                    .formatted(
                        event.runName() != null ? event.runName() : "", event.totalInvoices());
            notificationService.notifyAdminsAndOwners(
                "BILLING_RUN_COMPLETED", title, null, "BILLING_RUN", event.billingRunId());
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for billing_run.completed run={}",
                event.billingRunId(),
                e);
          }
        });
  }

  @EventListener
  public void onBillingRunFailures(BillingRunFailuresEvent event) {
    handleInCurrentTenantScope(
        () -> {
          try {
            var title =
                "Billing run \"%s\" had %d failures"
                    .formatted(
                        event.runName() != null ? event.runName() : "", event.failureCount());
            notificationService.notifyAdminsAndOwners(
                "BILLING_RUN_FAILURES", title, null, "BILLING_RUN", event.billingRunId());
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for billing_run.failures run={}",
                event.billingRunId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBillingRunSent(BillingRunSentEvent event) {
    handleInCurrentTenantScope(
        () -> {
          try {
            var title =
                "Billing run \"%s\" — %d invoices sent"
                    .formatted(event.runName() != null ? event.runName() : "", event.totalSent());
            notificationService.notifyAdminsAndOwners(
                "BILLING_RUN_SENT", title, null, "BILLING_RUN", event.billingRunId());
          } catch (Exception e) {
            log.warn(
                "Failed to create notifications for billing_run.sent run={}",
                event.billingRunId(),
                e);
          }
        });
  }

  /**
   * Re-binds the current tenant and org ScopedValues so that the REQUIRES_NEW transaction in
   * notifyAdminsAndOwners() resolves the correct schema. The billing run events don't carry
   * tenantId, but RequestScopes are still bound from the original HTTP request.
   */
  private void handleInCurrentTenantScope(Runnable action) {
    if (RequestScopes.TENANT_ID.isBound()) {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, RequestScopes.TENANT_ID.get());
      if (RequestScopes.ORG_ID.isBound()) {
        carrier = carrier.where(RequestScopes.ORG_ID, RequestScopes.ORG_ID.get());
      }
      carrier.run(action);
    } else {
      action.run();
    }
  }
}
