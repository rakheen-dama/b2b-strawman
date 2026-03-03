package io.b2mash.b2b.b2bstrawman.keycloak;

import io.b2mash.b2b.b2bstrawman.keycloak.dto.CreateOrgResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.InvitationResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.UserOrgResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates organization management for Keycloak auth mode. Coordinates Keycloak Admin API calls
 * with tenant provisioning and member sync. Conditionally activated only when {@code
 * keycloak.admin.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true")
public class OrgManagementService {

  private static final Logger log = LoggerFactory.getLogger(OrgManagementService.class);

  private final KeycloakAdminService keycloakAdminService;
  private final TenantProvisioningService provisioningService;
  private final MemberSyncService memberSyncService;

  public OrgManagementService(
      KeycloakAdminService keycloakAdminService,
      TenantProvisioningService provisioningService,
      MemberSyncService memberSyncService) {
    this.keycloakAdminService = keycloakAdminService;
    this.provisioningService = provisioningService;
    this.memberSyncService = memberSyncService;
  }

  /**
   * Creates an organization: Keycloak org + tenant schema + creator as owner. On provisioning
   * failure, compensates by deleting the Keycloak organization.
   *
   * @param name the organization display name
   * @param creatorUserId the Keycloak user ID of the creator (from JWT sub claim)
   * @param creatorEmail the email of the creator
   * @param creatorName the display name of the creator
   * @return the created org's slug and Keycloak org ID
   */
  public CreateOrgResponse createOrganization(
      String name, String creatorUserId, String creatorEmail, String creatorName) {
    String alias = toSlug(name);
    log.info("Creating organization: name={}, alias={}, creator={}", name, alias, creatorUserId);

    // Step 1: Create org in Keycloak
    var keycloakOrg = keycloakAdminService.createOrganization(name, alias);
    var orgId = keycloakOrg.id();

    try {
      // Step 2: Provision tenant schema
      provisioningService.provisionTenant(orgId, name);

      // Step 3: Add creator as member in Keycloak org
      keycloakAdminService.addMember(orgId, creatorUserId);

      // Step 4: Sync creator as owner in the tenant
      memberSyncService.syncMember(orgId, creatorUserId, creatorEmail, creatorName, null, "owner");

      log.info("Organization created successfully: orgId={}, alias={}", orgId, alias);
      return new CreateOrgResponse(alias, orgId);
    } catch (Exception e) {
      log.error(
          "Failed to complete organization setup for orgId={}, compensating by deleting Keycloak org",
          orgId,
          e);
      try {
        keycloakAdminService.deleteOrganization(orgId);
        log.info("Compensating delete completed for orgId={}", orgId);
      } catch (Exception deleteEx) {
        log.error("Compensating delete also failed for orgId={}", orgId, deleteEx);
      }
      throw new RuntimeException("Organization creation failed: " + e.getMessage(), e);
    }
  }

  /** Lists all organizations the given user belongs to. */
  public List<UserOrgResponse> listUserOrganizations(String userId) {
    log.debug("Listing organizations for user: userId={}", userId);
    return keycloakAdminService.getUserOrganizations(userId).stream()
        .map(org -> new UserOrgResponse(org.id(), org.name(), org.alias(), null))
        .toList();
  }

  /** Invites a user to an organization by email. */
  public void inviteToOrganization(String orgId, String email) {
    log.info("Inviting user to organization: orgId={}, email={}", orgId, email);
    keycloakAdminService.inviteToOrganization(orgId, email);
  }

  /** Lists pending invitations for an organization. */
  public List<InvitationResponse> listInvitations(String orgId) {
    log.debug("Listing invitations for organization: orgId={}", orgId);
    return keycloakAdminService.listInvitations(orgId).stream()
        .map(inv -> new InvitationResponse(inv.id(), inv.email(), "PENDING", null))
        .toList();
  }

  /** Cancels a pending invitation. */
  public void cancelInvitation(String orgId, String invitationId) {
    log.info("Cancelling invitation: orgId={}, invitationId={}", orgId, invitationId);
    keycloakAdminService.cancelInvitation(orgId, invitationId);
  }

  /** Converts an organization name to a URL-safe slug. */
  static String toSlug(String name) {
    return name.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
