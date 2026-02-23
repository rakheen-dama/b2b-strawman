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
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public branding endpoint for the customer portal. Returns org branding (name, logo URL, brand
 * color, footer text) without authentication, enabling the portal login page to display branded
 * content.
 */
@RestController
public class PortalBrandingController {

  private static final Logger log = LoggerFactory.getLogger(PortalBrandingController.class);
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final OrganizationRepository organizationRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final StorageService storageService;
  private final TransactionTemplate readOnlyTxTemplate;

  public PortalBrandingController(
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

  @GetMapping("/portal/branding")
  public ResponseEntity<BrandingResponse> getBranding(@RequestParam String orgId) {
    // Resolve org -> tenant schema
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    // Get org name from public schema
    var org =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    // Read OrgSettings within tenant scope with a transaction
    OrgSettings settings =
        ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
            .call(
                () ->
                    readOnlyTxTemplate.execute(
                        status -> orgSettingsRepository.findForCurrentTenant().orElse(null)));

    // Build response
    String logoUrl = null;
    String brandColor = null;
    String footerText = null;

    if (settings != null) {
      logoUrl = generateLogoUrl(settings.getLogoS3Key());
      brandColor = settings.getBrandColor();
      footerText = settings.getDocumentFooterText();
    }

    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(new BrandingResponse(org.getName(), logoUrl, brandColor, footerText));
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
