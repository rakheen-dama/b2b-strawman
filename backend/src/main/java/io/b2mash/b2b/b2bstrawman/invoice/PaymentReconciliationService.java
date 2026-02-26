package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.payment.WebhookResult;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes verified webhook results and reconciles payment state with invoice lifecycle. */
@Service
public class PaymentReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

  private final InvoiceService invoiceService;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;

  public PaymentReconciliationService(
      InvoiceService invoiceService,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      AuditService auditService,
      NotificationService notificationService) {
    this.invoiceService = invoiceService;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
  }

  /** Processes a verified webhook result, dispatching to the appropriate handler by status. */
  @Transactional
  public void processWebhookResult(WebhookResult result, String providerSlug) {
    String invoiceIdStr = result.metadata().get("invoiceId");
    if (invoiceIdStr == null) {
      log.warn("Webhook result missing invoiceId in metadata");
      return;
    }

    UUID invoiceId;
    try {
      invoiceId = UUID.fromString(invoiceIdStr);
    } catch (IllegalArgumentException e) {
      log.warn("Webhook result has invalid invoiceId: {}", invoiceIdStr);
      return;
    }

    var invoice = invoiceRepository.findById(invoiceId).orElse(null);
    if (invoice == null) {
      log.warn("Webhook references unknown invoice: {}", invoiceId);
      return;
    }

    // Verify that a PaymentEvent with matching sessionId exists for this invoice,
    // preventing crafted webhooks from marking arbitrary invoices as paid.
    if (result.sessionId() != null
        && !paymentEventRepository.existsBySessionIdAndStatus(
            result.sessionId(), PaymentEventStatus.CREATED)
        && !paymentEventRepository.existsBySessionIdAndStatus(
            result.sessionId(), PaymentEventStatus.PENDING)) {
      log.warn(
          "No CREATED/PENDING PaymentEvent found for sessionId={} on invoice {} — ignoring webhook",
          result.sessionId(),
          invoiceId);
      return;
    }

    switch (result.status()) {
      case COMPLETED -> handlePaymentCompleted(invoice, result, providerSlug);
      case FAILED -> handlePaymentFailed(invoice, result, providerSlug);
      case EXPIRED -> handlePaymentExpired(invoice, result, providerSlug);
      default -> log.warn("Unhandled payment status: {}", result.status());
    }
  }

  private void handlePaymentCompleted(Invoice invoice, WebhookResult result, String providerSlug) {
    // Idempotency: skip if already PAID
    if (invoice.getStatus() == InvoiceStatus.PAID) {
      log.info("Invoice {} already paid, skipping webhook", invoice.getId());
      return;
    }

    String paymentRef =
        result.paymentReference() != null ? result.paymentReference() : result.sessionId();

    // Record payment via InvoiceService — fromWebhook=true skips gateway call
    invoiceService.recordPayment(invoice.getId(), paymentRef, true);

    // Write COMPLETED payment event
    var completedEvent =
        new PaymentEvent(
            invoice.getId(),
            providerSlug,
            result.sessionId(),
            PaymentEventStatus.COMPLETED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    if (result.paymentReference() != null) {
      completedEvent.setPaymentReference(result.paymentReference());
    }
    paymentEventRepository.save(completedEvent);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.completed")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("WEBHOOK")
            .details(
                Map.of(
                    "provider",
                    providerSlug,
                    "session_id",
                    result.sessionId() != null ? result.sessionId() : "",
                    "payment_reference",
                    paymentRef,
                    "amount",
                    invoice.getTotal().toPlainString()))
            .build());

    log.info("Reconciled completed payment for invoice {} via {}", invoice.getId(), providerSlug);
  }

  private void handlePaymentFailed(Invoice invoice, WebhookResult result, String providerSlug) {
    // Write FAILED payment event
    var failedEvent =
        new PaymentEvent(
            invoice.getId(),
            providerSlug,
            result.sessionId(),
            PaymentEventStatus.FAILED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    paymentEventRepository.save(failedEvent);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.failed")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("WEBHOOK")
            .details(Map.of("provider", providerSlug, "invoice_number", invoice.getInvoiceNumber()))
            .build());

    // Notify admins + owners
    notificationService.notifyAdminsAndOwners(
        "PAYMENT_FAILED",
        "Online payment for Invoice " + invoice.getInvoiceNumber() + " failed",
        null,
        "INVOICE",
        invoice.getId());

    log.info("Recorded failed payment for invoice {} via {}", invoice.getId(), providerSlug);
  }

  private void handlePaymentExpired(Invoice invoice, WebhookResult result, String providerSlug) {
    // Write EXPIRED payment event
    var expiredEvent =
        new PaymentEvent(
            invoice.getId(),
            providerSlug,
            result.sessionId(),
            PaymentEventStatus.EXPIRED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    paymentEventRepository.save(expiredEvent);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.session.expired")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("WEBHOOK")
            .details(Map.of("provider", providerSlug, "invoice_number", invoice.getInvoiceNumber()))
            .build());

    // Notify invoice creator
    if (invoice.getCreatedBy() != null) {
      notificationService.createNotification(
          invoice.getCreatedBy(),
          "PAYMENT_LINK_EXPIRED",
          "Payment link for Invoice " + invoice.getInvoiceNumber() + " has expired",
          null,
          "INVOICE",
          invoice.getId(),
          null);
    }

    log.info("Recorded expired payment for invoice {} via {}", invoice.getId(), providerSlug);
  }
}
