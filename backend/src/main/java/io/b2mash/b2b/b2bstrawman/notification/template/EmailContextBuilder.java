package io.b2mash.b2b.b2bstrawman.notification.template;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the common base context for email templates. Provides org branding (logo, brand color,
 * footer), recipient info, and platform URLs. All email templates receive this base context.
 */
@Component
public class EmailContextBuilder {

  private static final Logger log = LoggerFactory.getLogger(EmailContextBuilder.class);
  private static final String DEFAULT_BRAND_COLOR = "#2563EB";
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final StorageService storageService;
  private final String appBaseUrl;

  public EmailContextBuilder(
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      StorageService storageService,
      @Value("${docteams.app.base-url:http://localhost:3000}") String appBaseUrl) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.storageService = storageService;
    this.appBaseUrl = appBaseUrl;
  }

  /**
   * Builds the common base context for email templates.
   *
   * @param recipientName the recipient's display name
   * @param unsubscribeUrl the unsubscribe URL (null for non-notification emails)
   * @return mutable map of base context variables
   */
  public Map<String, Object> buildBaseContext(String recipientName, String unsubscribeUrl) {
    var context = new HashMap<String, Object>();

    // Org name from global organizations table
    String orgName = resolveOrgName();
    context.put("orgName", orgName);

    // Branding from tenant-scoped OrgSettings
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);

    String orgLogoUrl = null;
    String brandColor = DEFAULT_BRAND_COLOR;
    String footerText = null;

    if (settings != null) {
      orgLogoUrl = generateLogoUrl(settings.getLogoS3Key());
      brandColor =
          settings.getBrandColor() != null ? settings.getBrandColor() : DEFAULT_BRAND_COLOR;
      footerText = settings.getDocumentFooterText();
    }

    context.put("orgLogoUrl", orgLogoUrl);
    context.put("brandColor", brandColor);
    context.put("footerText", footerText);
    context.put("recipientName", recipientName);
    context.put("unsubscribeUrl", unsubscribeUrl);
    context.put("appUrl", appBaseUrl);

    return context;
  }

  private String resolveOrgName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    if (orgId == null) {
      return "DocTeams";
    }
    return organizationRepository
        .findByClerkOrgId(orgId)
        .map(org -> org.getName())
        .orElse("DocTeams");
  }

  private String generateLogoUrl(String logoS3Key) {
    if (logoS3Key == null) {
      return null;
    }
    try {
      return storageService.generateDownloadUrl(logoS3Key, LOGO_URL_EXPIRY).url();
    } catch (RuntimeException e) {
      log.warn("Failed to generate logo download URL for key: {}", logoS3Key, e);
      return null;
    }
  }
}
