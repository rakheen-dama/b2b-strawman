package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePartiallyPaidEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Generates the payment receipt after a fee-note payment commits (Phase 89 632B, ADR-364, G88-F32).
 * Every payment source (Record Payment, settle balance, trust fee transfer, PSP webhook, Xero pull)
 * posts through {@code InvoiceTransitionService.postPayment}, which publishes {@link
 * InvoicePaidEvent} / {@link InvoicePartiallyPaidEvent} with {@code details.payment_event_id}.
 *
 * <p>Same contract as the fee-note PDF on send ({@code InvoiceEmailEventListener}): AFTER_COMMIT,
 * an independent REQUIRES_NEW transaction ({@link PaymentReceiptService#generateForPaymentEvent}),
 * and a failure is logged at ERROR with the invoice and payment-event ids — it never fails or rolls
 * back the payment. The lazy path (portal download, firm "Generate receipt") covers any receipt the
 * listener could not produce.
 */
@Component
public class PaymentReceiptListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentReceiptListener.class);

  private final PaymentReceiptService paymentReceiptService;

  public PaymentReceiptListener(PaymentReceiptService paymentReceiptService) {
    this.paymentReceiptService = paymentReceiptService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoicePaid(InvoicePaidEvent event) {
    handle(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        event.entityId(),
        event.details(),
        event.actorMemberId(),
        event.createdByMemberId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoicePartiallyPaid(InvoicePartiallyPaidEvent event) {
    handle(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        event.entityId(),
        event.details(),
        event.actorMemberId(),
        event.createdByMemberId());
  }

  private void handle(
      String tenantId,
      String orgId,
      String shardId,
      UUID invoiceId,
      Map<String, Object> details,
      UUID actorMemberId,
      UUID createdByMemberId) {
    if (tenantId == null) {
      log.warn("Payment event has null tenantId, receipt dropped: invoice={}", invoiceId);
      return;
    }
    Object rawPaymentEventId = details != null ? details.get("payment_event_id") : null;
    if (rawPaymentEventId == null) {
      log.warn("Payment event carries no payment_event_id, receipt skipped: invoice={}", invoiceId);
      return;
    }
    UUID paymentEventId = UUID.fromString(rawPaymentEventId.toString());
    // B2 / §14.12 #4: provider-posted payments (PSP webhook, Xero pull) have no member.
    UUID actor = actorMemberId != null ? actorMemberId : createdByMemberId;
    String actorSource =
        actorMemberId != null
            ? PaymentReceiptService.ACTOR_POSTING_MEMBER
            : PaymentReceiptService.ACTOR_INVOICE_CREATOR;
    try {
      RequestScopes.runForTenantOnShard(
          tenantId,
          orgId,
          shardId,
          () ->
              paymentReceiptService.generateForPaymentEvent(
                  invoiceId, paymentEventId, actor, actorSource));
    } catch (Exception e) {
      // Never fail the payment (InvoiceEmailEventListener contract) — the lazy path regenerates.
      log.error(
          "Failed to generate payment receipt for invoice={} paymentEvent={}",
          invoiceId,
          paymentEventId,
          e);
    }
  }
}
