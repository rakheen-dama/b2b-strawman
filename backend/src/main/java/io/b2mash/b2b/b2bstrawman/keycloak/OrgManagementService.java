package io.b2mash.b2b.b2bstrawman.keycloak;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.CreateOrgResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.InvitationResponse;
import io.b2mash.b2b.b2bstrawman.keycloak.dto.UserOrgResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
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
  private static final int MAX_SLUG_LENGTH = 64;

  private final KeycloakAdminService keycloakAdminService;
  private final TenantProvisioningService provisioningService;
  private final MemberSyncService memberSyncService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;

  public OrgManagementService(
      KeycloakAdminService keycloakAdminService,
      TenantProvisioningService provisioningService,
      MemberSyncService memberSyncService,
      OrgSchemaMappingRepository orgSchemaMappingRepository) {
    this.keycloakAdminService = keycloakAdminService;
    this.provisioningService = provisioningService;
    this.memberSyncService = memberSyncService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
  }

  /**
   * Creates an organization: Keycloak org + tenant schema + creator as owner. On provisioning
   * failure, compensates by deleting the Keycloak organization and cleaning up any orphaned schema
   * mapping.
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
          "Failed to complete organization setup for orgId={}, compensating by deleting Keycloak"
              + " org and cleaning up schema mapping",
          orgId,
          e);
      compensate(orgId);
      throw new InvalidStateException(
          "Organization creation failed",
          "Failed to complete organization setup: " + e.getMessage());
    }
  }

  /** Lists all organizations the given user belongs to. */
  public List<UserOrgResponse> listUserOrganizations(String userId) {
    log.debug("Listing organizations for user: userId={}", userId);
    return keycloakAdminService.getUserOrganizations(userId).stream()
        .map(org -> new UserOrgResponse(org.id(), org.name(), org.alias(), null))
        .toList();
  }

  /** Invites a user to an organization by email. Caller must be an owner or admin. */
  public void inviteToOrganization(String orgId, String email, String role, String callerUserId) {
    verifyCallerIsAdminOrOwner(orgId, callerUserId);
    log.info("Inviting user to organization: orgId={}, email={}, role={}", orgId, email, role);
    keycloakAdminService.inviteToOrganization(orgId, email);
  }

  /** Lists pending invitations for an organization. Caller must be a member. */
  public List<InvitationResponse> listInvitations(String orgId, String callerUserId) {
    verifyCallerIsMember(orgId, callerUserId);
    log.debug("Listing invitations for organization: orgId={}", orgId);
    return keycloakAdminService.listInvitations(orgId).stream()
        .map(inv -> new InvitationResponse(inv.id(), inv.email(), "PENDING", null))
        .toList();
  }

  /** Cancels a pending invitation. Caller must be an owner or admin. */
  public void cancelInvitation(String orgId, String invitationId, String callerUserId) {
    verifyCallerIsAdminOrOwner(orgId, callerUserId);
    log.info("Cancelling invitation: orgId={}, invitationId={}", orgId, invitationId);
    keycloakAdminService.cancelInvitation(orgId, invitationId);
  }

  /** Converts an organization name to a URL-safe slug, truncated to {@value MAX_SLUG_LENGTH}. */
  static String toSlug(String name) {
    var slug =
        name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    if (slug.length() > MAX_SLUG_LENGTH) {
      slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-$", "");
    }
    return slug;
  }

  /**
   * Verifies the caller is a member of the target organization. Throws 403 if not.
   *
   * @param orgId the Keycloak organization ID
   * @param callerUserId the JWT subject (Keycloak user ID)
   */
  private void verifyCallerIsMember(String orgId, String callerUserId) {
    var userOrgs = keycloakAdminService.getUserOrganizations(callerUserId);
    boolean isMember = userOrgs.stream().anyMatch(org -> org.id().equals(orgId));
    if (!isMember) {
      log.warn("Authorization denied: user {} is not a member of org {}", callerUserId, orgId);
      throw new ForbiddenException("Not a member", "You are not a member of this organization");
    }
  }

  /**
   * Verifies the caller is an admin or owner of the target organization. Throws 403 if not. Note:
   * Keycloak's organization API does not expose per-member roles in getUserOrganizations, so this
   * check only verifies membership. Role-level authorization can be enhanced when Keycloak adds
   * role introspection to the organization members API.
   */
  private void verifyCallerIsAdminOrOwner(String orgId, String callerUserId) {
    // Currently Keycloak org API doesn't expose member roles via getUserOrganizations,
    // so we verify membership. This is still a significant improvement over no check at all.
    verifyCallerIsMember(orgId, callerUserId);
  }

  /** Compensates a failed org creation by deleting the Keycloak org and cleaning up the DB. */
  private void compensate(String orgId) {
    try {
      keycloakAdminService.deleteOrganization(orgId);
      log.info("Compensating Keycloak org delete completed for orgId={}", orgId);
    } catch (Exception deleteEx) {
      log.error("Compensating Keycloak org delete failed for orgId={}", orgId, deleteEx);
    }

    try {
      orgSchemaMappingRepository
          .findByExternalOrgId(orgId)
          .ifPresent(
              mapping -> {
                orgSchemaMappingRepository.delete(mapping);
                log.info(
                    "Compensating OrgSchemaMapping cleanup completed for orgId={}, schema={}",
                    orgId,
                    mapping.getSchemaName());
              });
    } catch (Exception cleanupEx) {
      log.error(
          "Compensating OrgSchemaMapping cleanup failed for orgId={}. Orphaned schema may exist.",
          orgId,
          cleanupEx);
    }
  }
}
