package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.payment.WebhookResult;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
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
  private final InvoiceLineRepository invoiceLineRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;
  private final PortalContactRepository portalContactRepository;

  public PaymentReconciliationService(
      InvoiceService invoiceService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      PaymentEventRepository paymentEventRepository,
      AuditService auditService,
      NotificationService notificationService,
      PortalContactRepository portalContactRepository) {
    this.invoiceService = invoiceService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
    this.portalContactRepository = portalContactRepository;
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

    // Row lock: serialises concurrent deliveries of the same webhook ahead of the session-id
    // idempotency check in handlePaymentCompleted (payment_events.session_id is not unique).
    var invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElse(null);
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
    // A card payment we can't tie to a checkout session can be neither verified (the crafted-
    // webhook check above is session-based) nor posted idempotently (the replay guard below is
    // too) — a replay would post the balance due again on a part-paid fee note. Refuse it.
    if (result.sessionId() == null || result.sessionId().isBlank()) {
      log.warn(
          "COMPLETED webhook from {} for invoice {} has no session id — not posted",
          providerSlug,
          invoice.getId());
      return;
    }
    // §13.2.3(c): idempotent on replay by session_id, not on status — a part-paid fee note is
    // still payable, so a status check would double-post a replayed webhook.
    if (paymentEventRepository.existsBySessionIdAndStatus(
        result.sessionId(), PaymentEventStatus.COMPLETED)) {
      log.info(
          "Session {} already reconciled on invoice {}; skipping replayed webhook",
          result.sessionId(),
          invoice.getId());
      return;
    }
    if (!invoice.isPayable()) {
      // Settled (or voided) through another route while the checkout was open — leave it for
      // reconciliation rather than refusing inside the webhook transaction.
      log.warn(
          "Invoice {} is {} — card payment for session {} not posted",
          invoice.getId(),
          invoice.getStatus(),
          result.sessionId());
      return;
    }

    // The amount the PSP was asked to collect: the checkout session's CREATED/PENDING event,
    // written by PaymentLinkService at the balance due when the link was generated.
    BigDecimal amount =
        sessionAmount(result.sessionId())
            .orElseGet(
                () -> {
                  log.warn(
                      "No checkout session amount for invoice {} (session {}); posting the balance"
                          + " due",
                      invoice.getId(),
                      result.sessionId());
                  return invoice.getBalanceDue();
                });

    String paymentRef =
        result.paymentReference() != null ? result.paymentReference() : result.sessionId();

    // Phase 88 U15 (ADR-347): post the real amount as CARD through the one payment entry point,
    // which writes the COMPLETED payment event (provider, session id, reference). An amount above
    // the balance due is capped at PAID and audited as invoice.payment_excess (A4).
    invoiceService.postPayment(
        invoice.getId(), PaymentPosting.card(amount, providerSlug, result.sessionId(), paymentRef));

    // Audit: payment.completed (SYSTEM actor — webhook-driven reconciliation)
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
                    result.sessionId(),
                    "payment_reference",
                    paymentRef,
                    "amount",
                    amount.toPlainString()))
            .build());

    // Audit: portal.invoice.paid (PORTAL_CONTACT actor — payer half of the story).
    // The invoice has no direct projectId column; we derive it from the lines
    // (single-matter invoices return one project_id; spanning invoices return empty).
    // The details.project_id field is load-bearing — AuditEventRepository.findByProjectId
    // filters on it for the matter Activity feed (GAP-L-75c E.14 audit-trail completeness).
    var projectIds = invoiceLineRepository.findDistinctProjectIdsByInvoiceId(invoice.getId());
    String projectId = projectIds.size() == 1 ? projectIds.get(0).toString() : "";

    // Webhook reconciliation runs without RequestScopes.MEMBER_ID bound (no portal-contact
    // session is on the call stack), so AuditEventBuilder cannot auto-populate actor_id.
    // Resolve deterministically from the invoice's customer using the same PRIMARY > BILLING >
    // GENERAL > createdAt ASC ordering as portal session resolution. If the customer has no
    // active portal_contact, leave actor_id null rather than fabricating attribution — this
    // preserves status-quo behaviour for that edge case.
    UUID portalActorId =
        invoice.getCustomerId() != null
            ? portalContactRepository
                .findPreferredActiveByCustomerId(invoice.getCustomerId())
                .map(PortalContact::getId)
                .orElse(null)
            : null;

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("portal.invoice.paid")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorId(portalActorId)
            .actorType("PORTAL_CONTACT")
            .source("PORTAL")
            .details(
                Map.of(
                    "project_id",
                    projectId,
                    "invoice_number",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                    "customer_id",
                    invoice.getCustomerId() != null ? invoice.getCustomerId().toString() : "",
                    "amount",
                    amount.toPlainString(),
                    "currency",
                    invoice.getCurrency() != null ? invoice.getCurrency() : "",
                    "payment_reference",
                    paymentRef,
                    "provider",
                    providerSlug))
            .build());

    log.info("Reconciled completed payment for invoice {} via {}", invoice.getId(), providerSlug);
  }

  /** The amount the checkout session was created for (its CREATED, else PENDING, event). */
  private Optional<BigDecimal> sessionAmount(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    return paymentEventRepository
        .findBySessionIdAndStatus(sessionId, PaymentEventStatus.CREATED)
        .or(
            () ->
                paymentEventRepository.findBySessionIdAndStatus(
                    sessionId, PaymentEventStatus.PENDING))
        .map(PaymentEvent::getAmount);
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
        "Online payment for fee note " + invoice.getInvoiceNumber() + " failed",
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
          "Payment link for fee note " + invoice.getInvoiceNumber() + " has expired",
          null,
          "INVOICE",
          invoice.getId(),
          null);
    }

    log.info("Recorded expired payment for invoice {} via {}", invoice.getId(), providerSlug);
  }
}
