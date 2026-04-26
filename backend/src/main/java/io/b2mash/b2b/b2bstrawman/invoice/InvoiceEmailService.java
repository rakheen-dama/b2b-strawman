package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailAttachment;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvoiceEmailService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceEmailService.class);
  private static final String TEMPLATE_NAME = "invoice-delivery";
  private static final String REFERENCE_TYPE = "INVOICE";

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final String portalBaseUrl;

  public InvoiceEmailService(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.portalBaseUrl = portalBaseUrl;
  }

  public void sendInvoiceEmail(Invoice invoice, byte[] pdfBytes) {
    String recipientEmail = invoice.getCustomerEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping invoice email for invoice {} -- no customer email", invoice.getId());
      return;
    }

    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping invoice email for invoice {} -- no tenant context", invoice.getId());
      return;
    }

    try {
      // 1. Resolve provider
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      // 2. Build context (base + invoice-specific)
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(invoice.getCustomerName(), null);
      context.put("customerName", invoice.getCustomerName());
      context.put("invoiceNumber", invoice.getInvoiceNumber());
      context.put("amount", invoice.getTotal().toPlainString());
      context.put("currency", invoice.getCurrency());
      context.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
      String orgName = (String) context.get("orgName");
      // GAP-L-65 -- subject uses the firm's terminology (e.g. "Fee Note INV-... from Acme Law"
      // for legal-za tenants). invoiceTerm always falls back to "Invoice" via EmailContextBuilder.
      String invoiceTerm = (String) context.get("invoiceTerm");
      context.put("subject", invoiceTerm + " " + invoice.getInvoiceNumber() + " from " + orgName);
      // GAP-L-64 — always populate portalUrl so the email "View Invoice" CTA renders with a
      // working href even when no PSP is configured (paymentUrl is null).
      context.put("portalUrl", portalBaseUrl + "/invoices/" + invoice.getId());
      context.put("paymentUrl", invoice.getPaymentUrl());
      // Note: passing invoice.getPaymentUrl() (may be null) preserves existing template
      // behaviour — the Pay Now block is gated on ${paymentUrl} and remains hidden when null.

      // 3. Render template
      var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

      // 4. Check rate limit
      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for invoice email, invoice={}", invoice.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, invoice.getId(), TEMPLATE_NAME, recipientEmail, provider.providerId());
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
              invoice.getId().toString(),
              tenantSchema);

      // 6. Construct attachment
      var attachment =
          new EmailAttachment(invoice.getInvoiceNumber() + ".pdf", "application/pdf", pdfBytes);

      // 7. Send with attachment
      var result = provider.sendEmailWithAttachment(message, attachment);

      // 8. Record delivery
      deliveryLogService.record(
          REFERENCE_TYPE,
          invoice.getId(),
          TEMPLATE_NAME,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info("Invoice email sent for invoice={} to={}", invoice.getId(), recipientEmail);
      } else {
        log.warn("Invoice email failed for invoice={}: {}", invoice.getId(), result.errorMessage());
      }
    } catch (Exception e) {
      log.error("Unexpected error sending invoice email for invoice={}", invoice.getId(), e);
    }
  }
}
