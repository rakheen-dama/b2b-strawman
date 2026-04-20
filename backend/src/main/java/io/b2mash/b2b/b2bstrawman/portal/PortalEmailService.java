package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends magic link emails to portal contacts. Fire-and-forget: exceptions are caught and logged,
 * never propagated. This ensures that token generation always succeeds even if email delivery
 * fails.
 */
@Service
public class PortalEmailService {

  private static final Logger log = LoggerFactory.getLogger(PortalEmailService.class);
  private static final String TEMPLATE_NAME = "portal-magic-link";
  private static final String REFERENCE_TYPE = "MAGIC_LINK";

  // Epic 498B — per-event and digest portal-notification templates + delivery-log reference types.
  private static final String DIGEST_TEMPLATE_NAME = "portal-weekly-digest";
  private static final String DIGEST_REFERENCE_TYPE = "PORTAL_DIGEST";
  private static final String TRUST_ACTIVITY_TEMPLATE_NAME = "portal-trust-activity";
  private static final String TRUST_ACTIVITY_REFERENCE_TYPE = "PORTAL_TRUST_ACTIVITY";
  private static final String DEADLINE_TEMPLATE_NAME = "portal-deadline-approaching";
  private static final String DEADLINE_REFERENCE_TYPE = "PORTAL_DEADLINE";
  private static final String RETAINER_CLOSED_TEMPLATE_NAME = "portal-retainer-period-closed";
  private static final String RETAINER_CLOSED_REFERENCE_TYPE = "PORTAL_RETAINER";

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final OrganizationRepository organizationRepository;
  private final String portalBaseUrl;
  private final String productName;

  public PortalEmailService(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      OrganizationRepository organizationRepository,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.organizationRepository = organizationRepository;
    this.portalBaseUrl = portalBaseUrl;
    this.productName = productName;
  }

  /**
   * Sends a magic link email to a portal contact. Fire-and-forget: exceptions are caught and
   * logged, never propagated. Constructs the full magic link URL internally from the raw token.
   *
   * @param contact the portal contact to email
   * @param rawToken the raw token string to embed in the magic link URL
   * @param tokenId the MagicLinkToken UUID (used as referenceId in delivery log)
   */
  public void sendMagicLinkEmail(PortalContact contact, String rawToken, UUID tokenId) {
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping magic link email for contact {} -- no email address", contact.getId());
      return;
    }

    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping magic link email for contact {} -- no tenant context", contact.getId());
      return;
    }

    try {
      // 1. Resolve provider
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      // 2. Build context (base + magic-link-specific). No unsubscribe for transactional emails.
      String orgId = contact.getOrgId();
      String magicLinkUrl = portalBaseUrl + "/auth/exchange?token=" + rawToken + "&orgId=" + orgId;
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("magicLinkUrl", magicLinkUrl);
      context.put("expiryMinutes", String.valueOf(MagicLinkService.TOKEN_TTL_MINUTES));

      // Resolve org name directly from OrganizationRepository since ORG_ID ScopedValue
      // may not be bound during portal auth flow (only TENANT_ID is bound)
      String orgName = resolveOrgName(orgId);
      context.put("orgName", orgName);
      context.put("subject", "Your portal access link from " + orgName);

      // 3. Render template
      var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

      // 4. Check rate limit
      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for magic link email, contact={}", contact.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, tokenId, TEMPLATE_NAME, recipientEmail, provider.providerId());
        return;
      }

      // 5. Construct message (no attachment for magic link emails)
      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              tokenId.toString(),
              tenantSchema);

      // 6. Send (no attachment)
      var result = provider.sendEmail(message);

      // 7. Record delivery
      deliveryLogService.record(
          REFERENCE_TYPE, tokenId, TEMPLATE_NAME, recipientEmail, provider.providerId(), result);

      if (result.success()) {
        log.info("Magic link email sent for contact={} to={}", contact.getId(), recipientEmail);
      } else {
        log.warn(
            "Magic link email failed for contact={}: {}", contact.getId(), result.errorMessage());
      }
    } catch (Exception e) {
      log.error("Unexpected error sending magic link email for contact={}", contact.getId(), e);
    }
  }

  /** Resolves the org name from the global organizations table, falling back to product name. */
  private String resolveOrgName(String orgId) {
    if (orgId == null) {
      return productName;
    }
    return organizationRepository
        .findByClerkOrgId(orgId)
        .map(org -> org.getName())
        .orElse(productName);
  }

  // ─── Epic 498B: per-event + weekly-digest portal notification sends ───────────

  /**
   * Sends the weekly digest email (Epic 498B) to a portal contact. Fire-and-forget. The caller
   * (typically {@code PortalDigestScheduler}) must supply a fully assembled {@code digestContext}
   * map — this method only handles render, rate-limit, send, and delivery-log persistence.
   *
   * @return {@code true} if the provider accepted the message, {@code false} otherwise
   */
  public boolean sendDigestEmail(PortalContact contact, Map<String, Object> digestContext) {
    return sendPortalNotification(
        contact, digestContext, DIGEST_TEMPLATE_NAME, DIGEST_REFERENCE_TYPE);
  }

  /** Sends the trust-activity per-event email (Epic 498B). Fire-and-forget. */
  public boolean sendTrustActivityEmail(PortalContact contact, Map<String, Object> context) {
    return sendPortalNotification(
        contact, context, TRUST_ACTIVITY_TEMPLATE_NAME, TRUST_ACTIVITY_REFERENCE_TYPE);
  }

  /** Sends the deadline-reminder per-event email (Epic 498B). Fire-and-forget. */
  public boolean sendDeadlineReminderEmail(PortalContact contact, Map<String, Object> context) {
    return sendPortalNotification(
        contact, context, DEADLINE_TEMPLATE_NAME, DEADLINE_REFERENCE_TYPE);
  }

  /** Sends the retainer-period-closed per-event email (Epic 498B). Fire-and-forget. */
  public boolean sendRetainerClosedEmail(PortalContact contact, Map<String, Object> context) {
    return sendPortalNotification(
        contact, context, RETAINER_CLOSED_TEMPLATE_NAME, RETAINER_CLOSED_REFERENCE_TYPE);
  }

  /**
   * Shared implementation for Epic 498B portal notification sends. Mirrors {@link
   * #sendMagicLinkEmail} but takes a pre-assembled context map and a template/reference pair. The
   * {@code referenceId} stored in the delivery log is {@link PortalContact#getId()} — there is no
   * separate record per send for these message types.
   */
  private boolean sendPortalNotification(
      PortalContact contact,
      Map<String, Object> context,
      String templateName,
      String referenceType) {
    if (contact == null) {
      log.warn("Skipping portal notification ({}) -- contact is null", templateName);
      return false;
    }
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn(
          "Skipping portal notification ({}) for contact {} -- no email address",
          templateName,
          contact.getId());
      return false;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn(
          "Skipping portal notification ({}) for contact {} -- no tenant context",
          templateName,
          contact.getId());
      return false;
    }
    if (context == null) {
      log.warn(
          "Skipping portal notification ({}) for contact {} -- context is null",
          templateName,
          contact.getId());
      return false;
    }

    try {
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      var rendered = emailTemplateRenderer.render(templateName, context);

      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn(
            "Rate limit exceeded for portal notification ({}) contact={}",
            templateName,
            contact.getId());
        deliveryLogService.recordRateLimited(
            referenceType, contact.getId(), templateName, recipientEmail, provider.providerId());
        return false;
      }

      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              referenceType,
              contact.getId().toString(),
              tenantSchema);

      var result = provider.sendEmail(message);

      deliveryLogService.record(
          referenceType,
          contact.getId(),
          templateName,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info(
            "Portal notification sent template={} contact={} to={}",
            templateName,
            contact.getId(),
            recipientEmail);
      } else {
        log.warn(
            "Portal notification failed template={} contact={} error={}",
            templateName,
            contact.getId(),
            result.errorMessage());
      }
      return result.success();
    } catch (Exception e) {
      log.error(
          "Unexpected error sending portal notification template={} contact={}",
          templateName,
          contact.getId(),
          e);
      return false;
    }
  }
}
