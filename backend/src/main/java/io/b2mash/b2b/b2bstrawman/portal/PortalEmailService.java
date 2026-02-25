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
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;

  public PortalEmailService(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
  }

  /**
   * Sends a magic link email to a portal contact. Fire-and-forget: exceptions are caught and
   * logged, never propagated.
   *
   * @param contact the portal contact to email
   * @param magicLinkUrl the full magic link URL
   * @param tokenId the MagicLinkToken UUID (used as referenceId in delivery log)
   */
  public void sendMagicLinkEmail(PortalContact contact, String magicLinkUrl, UUID tokenId) {
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
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("magicLinkUrl", magicLinkUrl);
      context.put("expiryMinutes", "15");
      String orgName = (String) context.get("orgName");
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
}
