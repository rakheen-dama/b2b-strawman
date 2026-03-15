package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Keycloak organization creation and tenant schema provisioning. Requires the caller
 * to be a platform admin.
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
  public CreateOrgResponse createOrg(@Valid CreateOrgRequest request) {
    if (!RequestScopes.isPlatformAdmin()) {
      throw new ForbiddenException(
          "Platform admin required", "Only platform administrators can create organizations");
    }

    String name = request.name().trim();
    String slug = toSlug(name);
    String userId = extractUserId();

    log.info("Creating org '{}' (slug: {}) for user {}", name, slug, userId);

    if (keycloakAdminClient == null) {
      throw new InvalidStateException(
          "Keycloak not configured",
          "Keycloak admin client not configured — set keycloak.admin.auth-server-url");
    }

    // Create Keycloak organization
    String orgId;
    try {
      orgId = keycloakAdminClient.createOrganization(name, slug, userId);
    } catch (Exception e) {
      log.error("Failed to create Keycloak organization '{}': {}", name, e.getMessage());
      throw new ResourceConflictException(
          "Organization creation failed",
          "Failed to create organization '" + name + "': " + e.getMessage());
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

    // Add creator as member
    keycloakAdminClient.addMember(orgId, userId);

    // Assign owner role (best-effort)
    try {
      keycloakAdminClient.updateMemberRole(orgId, userId, "owner");
    } catch (Exception e) {
      log.warn("Failed to assign owner role to user {}: {}", userId, e.getMessage());
    }

    // Provision tenant schema
    tenantProvisioningService.provisionTenant(slug, name);

    log.info("Organization '{}' created successfully (orgId={}, slug={})", name, orgId, slug);
    return new CreateOrgResponse(orgId, slug);
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

  public record CreateOrgRequest(
      @NotBlank(message = "Organization name is required") String name) {}

  public record CreateOrgResponse(String orgId, String slug) {}
}
