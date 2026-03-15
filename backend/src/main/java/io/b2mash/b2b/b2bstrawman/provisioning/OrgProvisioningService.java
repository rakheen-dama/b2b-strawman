package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Keycloak organization creation and tenant schema provisioning. Requires the caller
 * to be a platform admin (enforced by {@code @PreAuthorize} on the controller).
 */
@Service
public class OrgProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(OrgProvisioningService.class);

  private final KeycloakAdminClient keycloakAdminClient;
  private final TenantProvisioningService tenantProvisioningService;
  private final OrganizationRepository organizationRepository;

  public OrgProvisioningService(
      @Nullable KeycloakAdminClient keycloakAdminClient,
      TenantProvisioningService tenantProvisioningService,
      OrganizationRepository organizationRepository) {
    this.keycloakAdminClient = keycloakAdminClient;
    this.tenantProvisioningService = tenantProvisioningService;
    this.organizationRepository = organizationRepository;
  }

  /**
   * Creates a new organization: Keycloak org + tenant schema.
   *
   * @param request the create org request containing the org name
   * @return response with the org ID and slug
   */
  public OrgController.CreateOrgResponse createOrg(OrgController.CreateOrgRequest request) {
    if (keycloakAdminClient == null) {
      throw new InvalidStateException(
          "Keycloak not configured",
          "Organization creation requires Keycloak Admin to be configured");
    }
    String name = request.name().trim();
    String slug = toSlug(name);
    String userId = extractUserId();

    if (slug.isBlank()) {
      throw new InvalidStateException(
          "Invalid organization name",
          "Organization name must contain at least one alphanumeric character");
    }

    log.info("Creating org '{}' (slug: {}) for user {}", name, slug, userId);

    // Create Keycloak organization
    String orgId;
    try {
      orgId = keycloakAdminClient.createOrganization(name, slug, userId);
    } catch (Exception e) {
      log.error("Failed to create Keycloak organization '{}': {}", name, e.getMessage());
      throw new ResourceConflictException(
          "Organization creation failed", "Organization with this name may already exist");
    }

    if (orgId == null) {
      // Fallback: look up by alias
      var org = keycloakAdminClient.findOrganizationByAlias(slug);
      orgId = org != null ? (String) org.get("id") : null;
    }

    if (orgId == null) {
      throw new InvalidStateException(
          "Organization creation failed",
          "Keycloak organization creation succeeded but no ID was returned");
    }

    // Post-creation steps with compensation on failure
    try {
      keycloakAdminClient.addMember(orgId, userId);
      keycloakAdminClient.updateMemberRole(orgId, userId, "owner");
      tenantProvisioningService.provisionTenant(slug, name);
    } catch (Exception e) {
      log.error(
          "Post-creation steps failed for org '{}' (orgId={}), attempting compensation: {}",
          name,
          orgId,
          e.getMessage());
      try {
        keycloakAdminClient.deleteOrganization(orgId);
        log.info("Compensated: deleted Keycloak org {} after post-creation failure", orgId);
      } catch (Exception compensationEx) {
        log.error(
            "Compensation failed — orphaned Keycloak org {} may exist: {}",
            orgId,
            compensationEx.getMessage());
      }
      throw new InvalidStateException(
          "Organization creation failed",
          "Organization setup failed after Keycloak org was created");
    }

    log.info("Organization '{}' created successfully (orgId={}, slug={})", name, orgId, slug);
    return new OrgController.CreateOrgResponse(orgId, slug);
  }

  private String extractUserId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      return jwtAuth.getToken().getSubject();
    }
    throw new InvalidStateException(
        "Authentication required", "Could not extract user ID from authentication context");
  }

  static String toSlug(String name) {
    return name.toLowerCase()
        .trim()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
