package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves public portal branding (org name, logo URL, brand color, footer text) for the
 * unauthenticated portal login page. Owns the cross-schema read: the org→tenant-schema mapping and
 * org name live in the {@code public} schema, while branding fields live in the tenant's {@code
 * OrgSettings} row. The tenant read is performed inside a tenant-scoped read-only transaction via
 * {@link RequestScopes#callForTenant}.
 */
@Service
public class PortalBrandingService {

  private static final Logger log = LoggerFactory.getLogger(PortalBrandingService.class);
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final OrganizationRepository organizationRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final StorageService storageService;
  private final TransactionTemplate readOnlyTxTemplate;

  public PortalBrandingService(
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      OrganizationRepository organizationRepository,
      OrgSettingsRepository orgSettingsRepository,
      StorageService storageService,
      PlatformTransactionManager txManager) {
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.organizationRepository = organizationRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.storageService = storageService;
    this.readOnlyTxTemplate = new TransactionTemplate(txManager);
    this.readOnlyTxTemplate.setReadOnly(true);
  }

  /**
   * Returns the public branding for the given Clerk org id.
   *
   * @throws ResourceNotFoundException if the org has no schema mapping or no organization record.
   */
  public BrandingResponse getBranding(String orgId) {
    // Resolve org -> tenant schema (public schema)
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    // Get org name from public schema
    var org =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    // Read OrgSettings within tenant scope with a read-only transaction
    OrgSettings settings =
        RequestScopes.callForTenant(
            mapping.getSchemaName(),
            null,
            () ->
                readOnlyTxTemplate.execute(
                    status -> orgSettingsRepository.findForCurrentTenant().orElse(null)));

    String logoUrl = null;
    String brandColor = null;
    String footerText = null;

    if (settings != null) {
      logoUrl = generateLogoUrl(settings.getLogoS3Key());
      brandColor = settings.getBrandColor();
      footerText = settings.getDocumentFooterText();
    }

    return new BrandingResponse(org.getName(), logoUrl, brandColor, footerText);
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

  public record BrandingResponse(
      String orgName, String logoUrl, String brandColor, String footerText) {}
}
