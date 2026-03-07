package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the approval and rejection of access requests. On approval, provisions a Keycloak
 * organization, a tenant schema, and sends an invitation to the requester.
 */
@Service
public class AccessRequestApprovalService {

  private static final Logger log = LoggerFactory.getLogger(AccessRequestApprovalService.class);

  private final AccessRequestRepository accessRequestRepository;
  private final KeycloakProvisioningClient keycloakProvisioningClient;
  private final TenantProvisioningService tenantProvisioningService;

  public AccessRequestApprovalService(
      AccessRequestRepository accessRequestRepository,
      KeycloakProvisioningClient keycloakProvisioningClient,
      TenantProvisioningService tenantProvisioningService) {
    this.accessRequestRepository = accessRequestRepository;
    this.keycloakProvisioningClient = keycloakProvisioningClient;
    this.tenantProvisioningService = tenantProvisioningService;
  }

  /**
   * Approves a pending access request. Provisions a Keycloak org, tenant schema, and sends an
   * invitation to the requester.
   *
   * @param requestId the access request ID
   * @param adminUserId the platform admin user ID performing the approval
   * @return the updated access request entity
   */
  public AccessRequest approve(UUID requestId, String adminUserId) {
    var request = findPendingRequest(requestId);

    String orgName = request.getOrganizationName();
    String slug = slugify(orgName);

    try {
      String kcOrgId = keycloakProvisioningClient.createOrganization(orgName, slug);
      log.info("Created Keycloak organization '{}' with ID {}", orgName, kcOrgId);

      tenantProvisioningService.provisionTenant(kcOrgId, orgName);
      log.info("Provisioned tenant schema for org {}", kcOrgId);

      keycloakProvisioningClient.inviteUser(kcOrgId, request.getEmail());
      log.info("Sent invitation to {} for org {}", request.getEmail(), kcOrgId);

      request.setStatus(AccessRequestStatus.APPROVED);
      request.setKeycloakOrgId(kcOrgId);
      request.setReviewedBy(adminUserId);
      request.setReviewedAt(Instant.now());

      return accessRequestRepository.save(request);
    } catch (Exception e) {
      log.error("Approval failed for request {}: {}", requestId, e.getMessage(), e);
      request.setProvisioningError(e.getMessage());
      accessRequestRepository.save(request);
      throw e;
    }
  }

  /**
   * Rejects a pending access request.
   *
   * @param requestId the access request ID
   * @param adminUserId the platform admin user ID performing the rejection
   * @return the updated access request entity
   */
  @Transactional
  public AccessRequest reject(UUID requestId, String adminUserId) {
    var request = findPendingRequest(requestId);

    request.setStatus(AccessRequestStatus.REJECTED);
    request.setReviewedBy(adminUserId);
    request.setReviewedAt(Instant.now());

    return accessRequestRepository.save(request);
  }

  private AccessRequest findPendingRequest(UUID requestId) {
    var request =
        accessRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("AccessRequest", requestId));

    if (request.getStatus() != AccessRequestStatus.PENDING) {
      throw new ResourceConflictException(
          "Access request not pending",
          "Access request " + requestId + " has status " + request.getStatus());
    }

    return request;
  }

  private String slugify(String input) {
    return input
        .toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
  }
}
