package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestExpiredEvent;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Handles email dispatch for the acceptance workflow: request emails, reminders, and confirmation
 * emails. Extracted from AcceptanceService to reduce its size and responsibility scope.
 */
@Service
class AcceptanceNotificationService {

  private static final Logger log = LoggerFactory.getLogger(AcceptanceNotificationService.class);
  private static final String REFERENCE_TYPE = "ACCEPTANCE_REQUEST";
  private static final String TEMPLATE_REMINDER = "acceptance-reminder";
  private static final String TEMPLATE_CONFIRMATION = "acceptance-confirmation";

  private static final DateTimeFormatter EMAIL_DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC);

  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final IntegrationRegistry integrationRegistry;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditService auditService;

  AcceptanceNotificationService(
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      IntegrationRegistry integrationRegistry,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      ApplicationEventPublisher eventPublisher,
      AuditService auditService) {
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.integrationRegistry = integrationRegistry;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.eventPublisher = eventPublisher;
    this.auditService = auditService;
  }

  void sendAcceptanceEmail(
      AcceptanceRequest request,
      PortalContact contact,
      GeneratedDocument doc,
      String templateName,
      String portalBaseUrl) {
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping acceptance email for contact {} -- no email address", contact.getId());
      return;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping acceptance email for contact {} -- no tenant context", contact.getId());
      return;
    }
    try {
      // 1. Resolve provider
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      // 2. Build context
      String acceptanceUrl = portalBaseUrl + "/accept/" + request.getRequestToken();
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("documentFileName", doc.getFileName());
      context.put("acceptanceUrl", acceptanceUrl);
      context.put("expiresAtFormatted", EMAIL_DATE_FORMAT.format(request.getExpiresAt()));

      String orgName = (String) context.get("orgName");

      if (TEMPLATE_REMINDER.equals(templateName) && request.getSentAt() != null) {
        context.put("sentAtFormatted", EMAIL_DATE_FORMAT.format(request.getSentAt()));
        context.put(
            "subject",
            "Reminder: " + orgName + " -- Please review engagement letter for acceptance");
      } else {
        context.put(
            "subject",
            orgName + " -- Please review engagement letter for acceptance: " + doc.getFileName());
      }

      // 3. Render template
      var rendered = emailTemplateRenderer.render(templateName, context);

      // 4. Rate limit check
      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for acceptance email, request={}", request.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, request.getId(), templateName, recipientEmail, provider.providerId());
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
              request.getId().toString(),
              tenantSchema);

      // 6. Send
      var result = provider.sendEmail(message);

      // 7. Record delivery
      deliveryLogService.record(
          REFERENCE_TYPE,
          request.getId(),
          templateName,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info(
            "Acceptance email ({}) sent for request={} to={}",
            templateName,
            request.getId(),
            recipientEmail);
      } else {
        log.warn(
            "Acceptance email ({}) failed for request={}: {}",
            templateName,
            request.getId(),
            result.errorMessage());
      }
    } catch (Exception e) {
      log.error(
          "Unexpected error sending acceptance email ({}) for request={}",
          templateName,
          request.getId(),
          e);
    }
  }

  void sendConfirmationEmail(
      AcceptanceRequest request, PortalContact contact, GeneratedDocument doc) {
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping confirmation email for contact {} -- no email address", contact.getId());
      return;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping confirmation email for contact {} -- no tenant context", contact.getId());
      return;
    }
    try {
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("documentFileName", doc.getFileName());
      context.put("acceptedAtFormatted", EMAIL_DATE_FORMAT.format(request.getAcceptedAt()));
      context.put("subject", "Confirmed: You have accepted " + doc.getFileName());

      var rendered = emailTemplateRenderer.render(TEMPLATE_CONFIRMATION, context);

      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for confirmation email, request={}", request.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE,
            request.getId(),
            TEMPLATE_CONFIRMATION,
            recipientEmail,
            provider.providerId());
        return;
      }

      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              request.getId().toString(),
              tenantSchema);

      var result = provider.sendEmail(message);

      deliveryLogService.record(
          REFERENCE_TYPE,
          request.getId(),
          TEMPLATE_CONFIRMATION,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info(
            "Acceptance confirmation email sent for request={} to={}",
            request.getId(),
            recipientEmail);
      } else {
        log.warn(
            "Acceptance confirmation email failed for request={}: {}",
            request.getId(),
            result.errorMessage());
      }
    } catch (Exception e) {
      log.error("Unexpected error sending confirmation email for request={}", request.getId(), e);
    }
  }

  void publishExpiredEvent(AcceptanceRequest request) {
    // Audit: acceptance.expired
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("acceptance.expired")
            .entityType("acceptance_request")
            .entityId(request.getId())
            .actorType("SYSTEM")
            .source("INTERNAL")
            .details(Map.of("expired_at", request.getExpiresAt().toString()))
            .build());

    eventPublisher.publishEvent(
        new AcceptanceRequestExpiredEvent(
            "acceptance_request.expired",
            "acceptance_request",
            request.getId(),
            null,
            null,
            "System",
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of(),
            request.getId()));
  }
}
