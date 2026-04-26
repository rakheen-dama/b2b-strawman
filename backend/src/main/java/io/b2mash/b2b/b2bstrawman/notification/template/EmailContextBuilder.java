package io.b2mash.b2b.b2bstrawman.notification.template;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
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
 * footer), recipient info, terminology overrides (GAP-L-65), and platform URLs. All email templates
 * receive this base context.
 */
@Component
public class EmailContextBuilder {

  private static final Logger log = LoggerFactory.getLogger(EmailContextBuilder.class);
  private static final String DEFAULT_BRAND_COLOR = "#2563EB";
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final StorageService storageService;
  private final EmailTerminology emailTerminology;
  private final String appBaseUrl;
  private final String productName;

  public EmailContextBuilder(
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      StorageService storageService,
      EmailTerminology emailTerminology,
      @Value("${docteams.app.base-url:http://localhost:3000}") String appBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.storageService = storageService;
    this.emailTerminology = emailTerminology;
    this.appBaseUrl = appBaseUrl;
    this.productName = productName;
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
    OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElse(null);

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

    // GAP-L-65 -- terminology overrides keyed by verticalProfile (e.g. legal-za "Invoice" ->
    // "Fee Note"). Convenience keys avoid Thymeleaf null-safety gymnastics in templates.
    String namespace = settings != null ? settings.getVerticalProfile() : null;
    Map<String, String> terminology = emailTerminology.resolve(namespace);
    context.put("terminology", terminology);
    context.put("invoiceTerm", terminology.getOrDefault("Invoice", "Invoice"));
    context.put("invoiceTermLower", terminology.getOrDefault("invoice", "invoice"));
    context.put("invoiceTermPlural", terminology.getOrDefault("Invoices", "Invoices"));
    context.put("invoiceTermPluralLower", terminology.getOrDefault("invoices", "invoices"));

    return context;
  }

  private String resolveOrgName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    if (orgId == null) {
      return productName;
    }
    return organizationRepository
        .findByClerkOrgId(orgId)
        .map(org -> org.getName())
        .orElse(productName);
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
