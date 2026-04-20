package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.dto.PortalSessionContextDto;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the aggregated session context for an authenticated portal contact.
 *
 * <p>Combines the tenant's vertical profile, enabled modules, terminology namespace and branding
 * into a single {@link PortalSessionContextDto}. Tenant / org identity is read from {@link
 * RequestScopes}, which is bound by {@code CustomerAuthFilter} for every authed {@code /portal/**}
 * request.
 *
 * <p>The branding resolution mirrors the one in {@link PortalBrandingController} (org name from the
 * {@code public} schema, brand color + presigned logo URL from tenant-scoped {@code OrgSettings}).
 * The logic is inlined rather than extracted into a shared helper because there are only two
 * call-sites and they operate under different tenant-binding regimes (pre-auth lookup by {@code
 * orgId} vs. post-auth read from already-bound {@code TENANT_ID}) -- YAGNI applies.
 */
@Service
public class PortalSessionContextService {

  private static final Logger log = LoggerFactory.getLogger(PortalSessionContextService.class);
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);

  private final OrganizationRepository organizationRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final StorageService storageService;

  public PortalSessionContextService(
      OrganizationRepository organizationRepository,
      OrgSettingsRepository orgSettingsRepository,
      StorageService storageService) {
    this.organizationRepository = organizationRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.storageService = storageService;
  }

  /**
   * Builds the session context for the currently authenticated portal contact.
   *
   * <p>Reads {@link RequestScopes#ORG_ID} and {@link RequestScopes#TENANT_ID} (both bound by {@code
   * CustomerAuthFilter}).
   *
   * @throws ResourceNotFoundException if the {@code Organization} row is missing from the public
   *     schema (should never happen for a validated portal JWT, but guarded defensively)
   */
  @Transactional(readOnly = true)
  public PortalSessionContextDto resolve() {
    String orgId = RequestScopes.requireOrgId();

    var org =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElse(null);

    String tenantProfile = settings != null ? settings.getVerticalProfile() : null;
    List<String> enabledModules =
        settings == null || settings.getEnabledModules() == null
            ? List.of()
            : List.copyOf(settings.getEnabledModules());
    String terminologyKey = resolveTerminologyKey(settings, tenantProfile);
    String brandColor = settings != null ? settings.getBrandColor() : null;
    String logoUrl = settings != null ? generateLogoUrl(settings.getLogoS3Key()) : null;

    return new PortalSessionContextDto(
        tenantProfile, enabledModules, terminologyKey, brandColor, org.getName(), logoUrl);
  }

  /**
   * Resolves the terminology key for the tenant.
   *
   * <p>Prefers the persisted {@link OrgSettings#getTerminologyNamespace() terminologyNamespace}
   * (seeded by {@code VerticalProfileRegistry} during provisioning -- e.g. {@code "en-ZA-legal"}).
   * Falls back to composing {@code "en-ZA-<short>"} from the {@code verticalProfile} id (stripping
   * the {@code -za} country suffix), and returns an empty string for generic / null profiles.
   */
  private String resolveTerminologyKey(OrgSettings settings, String tenantProfile) {
    if (settings != null) {
      String ns = settings.getTerminologyNamespace();
      if (ns != null && !ns.isBlank()) {
        return ns;
      }
    }
    if (tenantProfile == null || tenantProfile.isBlank()) {
      return "";
    }
    String shortProfile =
        tenantProfile.endsWith("-za")
            ? tenantProfile.substring(0, tenantProfile.length() - 3)
            : tenantProfile;
    if (shortProfile.equals("consulting-generic") || shortProfile.isBlank()) {
      return "";
    }
    return "en-ZA-" + shortProfile;
  }

  private String generateLogoUrl(String logoS3Key) {
    if (logoS3Key == null || logoS3Key.isBlank()) {
      return null;
    }
    try {
      return storageService.generateDownloadUrl(logoS3Key, LOGO_URL_EXPIRY).url();
    } catch (RuntimeException e) {
      log.warn("Failed to generate logo download URL", e);
      return null;
    }
  }
}
