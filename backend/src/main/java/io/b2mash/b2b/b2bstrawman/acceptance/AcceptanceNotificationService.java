package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
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
import io.b2mash.b2b.b2bstrawman.portal.PortalLinkBuilder;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

  /** GAP-L-51: engagement letters keep their legal-firm wording (scenario 4.3b pins it). */
  static final String ENGAGEMENT_LETTER_LABEL = "engagement letter";

  /** Defensive only: generated_documents.template_id is a NOT NULL foreign key. */
  static final String FALLBACK_LABEL = "document";

  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final IntegrationRegistry integrationRegistry;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditService auditService;
  private final PortalLinkBuilder portalLinkBuilder;
  private final DocumentTemplateRepository documentTemplateRepository;

  AcceptanceNotificationService(
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      IntegrationRegistry integrationRegistry,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      ApplicationEventPublisher eventPublisher,
      AuditService auditService,
      PortalLinkBuilder portalLinkBuilder,
      DocumentTemplateRepository documentTemplateRepository) {
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.integrationRegistry = integrationRegistry;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.eventPublisher = eventPublisher;
    this.auditService = auditService;
    this.portalLinkBuilder = portalLinkBuilder;
    this.documentTemplateRepository = documentTemplateRepository;
  }

  /**
   * G-B16 (Phase 88 626G): what the acceptance subject calls the document, from the generated
   * document's template: {@value #ENGAGEMENT_LETTER_LABEL} for an {@code ENGAGEMENT_LETTER}
   * template (GAP-L-51), otherwise the template's name in lower case ("offer to purchase"), and
   * {@value #FALLBACK_LABEL} when no template resolves or its name is blank.
   */
  String documentLabel(GeneratedDocument doc) {
    if (doc.getTemplateId() == null) {
      return FALLBACK_LABEL;
    }
    return documentTemplateRepository
        .findById(doc.getTemplateId())
        .map(AcceptanceNotificationService::labelOf)
        .orElse(FALLBACK_LABEL);
  }

  private static String labelOf(DocumentTemplate template) {
    if (template.getCategory() == TemplateCategory.ENGAGEMENT_LETTER) {
      return ENGAGEMENT_LETTER_LABEL;
    }
    String name = template.getName() == null ? "" : template.getName().trim();
    return name.isEmpty() ? FALLBACK_LABEL : name.toLowerCase(Locale.ROOT);
  }

  void sendAcceptanceEmail(
      AcceptanceRequest request,
      PortalContact contact,
      GeneratedDocument doc,
      String templateName) {
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
      // Public token page — the token resolves the firm, so no orgId (§13.6.6).
      String acceptanceUrl = portalLinkBuilder.acceptance(request.getRequestToken());
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("documentFileName", doc.getFileName());
      context.put("acceptanceUrl", acceptanceUrl);
      context.put("expiresAtFormatted", EMAIL_DATE_FORMAT.format(request.getExpiresAt()));

      String orgName = (String) context.get("orgName");
      String docLabel = documentLabel(doc);

      if (TEMPLATE_REMINDER.equals(templateName) && request.getSentAt() != null) {
        context.put("sentAtFormatted", EMAIL_DATE_FORMAT.format(request.getSentAt()));
        context.put(
            "subject",
            "Reminder: " + orgName + " -- Please review " + docLabel + " for acceptance");
      } else {
        context.put(
            "subject",
            orgName + " -- Please review " + docLabel + " for acceptance: " + doc.getFileName());
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

  /**
   * Audits {@code acceptance.expired} and publishes the domain event. {@code projectId} is the
   * request's matter (ADR-349 correlation key; null when the document has none) — resolved by the
   * caller, which owns the document lookups.
   */
  void publishExpiredEvent(AcceptanceRequest request, UUID projectId) {
    // Audit: acceptance.expired
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("acceptance.expired")
            .entityType("acceptance_request")
            .entityId(request.getId())
            .actorType("SYSTEM")
            .source("INTERNAL")
            .details(
                AuditDetails.matterScoped(
                    projectId,
                    request.getCustomerId(),
                    Map.of("expired_at", request.getExpiresAt().toString())))
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
