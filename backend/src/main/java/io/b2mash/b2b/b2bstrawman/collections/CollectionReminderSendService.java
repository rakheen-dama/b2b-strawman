package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.GateAction;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.integration.email.SendResult;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends ONE approved collection reminder (Phase 83, ADR-326/327). Mirrors {@code
 * InvoiceEmailService} step for step: resolve provider → {@code buildBaseContext} → render the
 * {@code collection-reminder} Thymeleaf template → rate-limit check → {@code
 * EmailMessage.withTracking} → send → delivery log. Invoked ONLY from {@code
 * GateActionExecutor.executeSendCollectionReminder} — no direct send endpoint exists by
 * construction.
 *
 * <p><strong>Frame owns facts</strong>: the template renders the invoice facts table (number,
 * amount, due date, days overdue) and the payment CTA (paymentUrl with always-populated portalUrl
 * fallback, GAP-L-64) from context values; the AI-drafted content contributes only the subject and
 * the letter paragraphs.
 *
 * <p>Unlike {@code InvoiceEmailService} (fire-and-forget listener, broad catch-log), this runs
 * inside the gate-approve transaction. Provider send failure is deliberately NOT an exception path:
 * the activity is recorded {@code SEND_FAILED} (scan re-proposes with a fresh draft — the email
 * never left) and the method returns normally so the gate stays APPROVED and the audit trail stays
 * truthful. Only malformed action data (missing activity/invoice) throws.
 */
@Service
public class CollectionReminderSendService {

  private static final Logger log = LoggerFactory.getLogger(CollectionReminderSendService.class);
  private static final String TEMPLATE_NAME = "collection-reminder";
  private static final String REFERENCE_TYPE = "COLLECTION_REMINDER";

  static final String REASON_RATE_LIMITED = "rate_limited";
  static final String REASON_PROVIDER_FAILURE = "provider_failure";
  static final String REASON_NO_RECIPIENT = "no_recipient";

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final CollectionActivityRepository activityRepository;
  private final InvoiceRepository invoiceRepository;
  private final AuditService auditService;
  private final String portalBaseUrl;

  public CollectionReminderSendService(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      CollectionActivityRepository activityRepository,
      InvoiceRepository invoiceRepository,
      AuditService auditService,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.activityRepository = activityRepository;
    this.invoiceRepository = invoiceRepository;
    this.auditService = auditService;
    this.portalBaseUrl = portalBaseUrl;
  }

  public void sendReminder(GateAction.SendCollectionReminderAction action) {
    CollectionActivity activity =
        activityRepository
            .findOneById(action.collectionActivityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "CollectionActivity", action.collectionActivityId()));
    Invoice invoice =
        invoiceRepository
            .findById(action.invoiceId())
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", action.invoiceId()));

    String recipientEmail = invoice.getCustomerEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      // The recipient disappeared between propose and approve — retryable, no email left.
      log.warn(
          "Collection reminder for activity {} has no recipient — marking SEND_FAILED",
          activity.getId());
      activity.markSendFailed(null, REASON_NO_RECIPIENT);
      activityRepository.save(activity);
      return;
    }

    // 1. Resolve provider
    EmailProvider provider =
        integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

    // 2. Build context (base + reminder-specific). Frame owns facts: the template renders these
    // values; the AI contributes only subject + letter paragraphs.
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(invoice.getCustomerName(), null);
    context.put("customerName", invoice.getCustomerName());
    context.put("invoiceNumber", invoice.getInvoiceNumber());
    context.put("amount", invoice.getTotal() != null ? invoice.getTotal().toPlainString() : null);
    context.put("currency", invoice.getCurrency());
    context.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
    context.put(
        "daysOverdue",
        invoice.getDueDate() != null
            ? String.valueOf(ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now()))
            : String.valueOf(activity.getDaysOverdueAtAction()));
    // AI-drafted parts (approved on the gate).
    context.put("subject", action.subject());
    context.put("reminderBodyHtml", action.bodyHtml());
    // GAP-L-64 — always populate portalUrl so the CTA renders with a working href even when no
    // PSP is configured (paymentUrl null keeps the Pay Now block hidden).
    context.put("portalUrl", portalBaseUrl + "/invoices/" + invoice.getId());
    context.put("paymentUrl", invoice.getPaymentUrl());

    // 3. Render template
    var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

    // 4. Check rate limit
    String tenantSchema = RequestScopes.TENANT_ID.get();
    if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
      log.warn("Rate limit exceeded for collection reminder, activity={}", activity.getId());
      deliveryLogService.recordRateLimited(
          REFERENCE_TYPE, activity.getId(), TEMPLATE_NAME, recipientEmail, provider.providerId());
      // Retryable; markSkipped retains the gateId so the draft stays traceable (§2.2).
      activity.markSkipped(REASON_RATE_LIMITED);
      activityRepository.save(activity);
      return;
    }

    // 5. Construct message
    var message =
        EmailMessage.withTracking(
            recipientEmail,
            rendered.subject(),
            rendered.htmlBody(),
            rendered.plainTextBody(),
            null,
            REFERENCE_TYPE,
            activity.getId().toString(),
            tenantSchema);

    // 6. Send — a provider that throws is normalized to a failed SendResult so the approve
    // transaction is never poisoned by a transport error.
    SendResult result;
    try {
      result = provider.sendEmail(message);
    } catch (RuntimeException e) {
      log.error(
          "Collection reminder send threw for activity={}: {}", activity.getId(), e.getMessage());
      result = new SendResult(false, null, e.getMessage());
    }

    // 7. Record delivery
    var deliveryLog =
        deliveryLogService.record(
            REFERENCE_TYPE,
            activity.getId(),
            TEMPLATE_NAME,
            recipientEmail,
            provider.providerId(),
            result);

    if (result.success()) {
      activity.markSent(deliveryLog.getId());
      activityRepository.save(activity);
      // Ids/numbers only in details — no client PII (POPIA).
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("collections.reminder.sent")
              .entityType("collection_activity")
              .entityId(activity.getId())
              .details(
                  Map.of(
                      "invoice_id", invoice.getId().toString(),
                      "invoice_number",
                          invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                      "stage", activity.getStage().name(),
                      "delivery_log_id", deliveryLog.getId().toString()))
              .build());
      log.info(
          "Collection reminder sent for activity={} invoice={}", activity.getId(), invoice.getId());
    } else {
      // Approved but the mail provider failed; no email left. Scan-retryable — the next scan
      // re-proposes with a fresh draft.
      activity.markSendFailed(deliveryLog.getId(), REASON_PROVIDER_FAILURE);
      activityRepository.save(activity);
      log.warn(
          "Collection reminder failed for activity={}: {}",
          activity.getId(),
          result.errorMessage());
    }
  }
}
