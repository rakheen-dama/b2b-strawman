package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
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
      @Nullable KeycloakProvisioningClient keycloakProvisioningClient,
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
  @Transactional(noRollbackFor = Exception.class)
  public AccessRequest approve(UUID requestId, String adminUserId) {
    if (keycloakProvisioningClient == null) {
      throw new IllegalStateException(
          "Keycloak admin client not configured — set keycloak.admin.auth-server-url");
    }

    var request = findPendingRequest(requestId);

    String orgName = request.getOrganizationName();
    String slug = slugify(orgName);

    try {
      // Idempotent: skip KC org creation if already done (retry after partial failure)
      String kcOrgId = request.getKeycloakOrgId();
      if (kcOrgId == null) {
        kcOrgId = keycloakProvisioningClient.createOrganization(orgName, slug);
        log.info("Created Keycloak organization '{}' with ID {}", orgName, kcOrgId);
        // Persist kcOrgId immediately so retries won't create a duplicate org
        request.setKeycloakOrgId(kcOrgId);
        accessRequestRepository.save(request);
      } else {
        log.info(
            "Keycloak org {} already exists for request {}, skipping creation", kcOrgId, requestId);
      }

      tenantProvisioningService.provisionTenant(kcOrgId, orgName);
      log.info("Provisioned tenant schema for org {}", kcOrgId);

      keycloakProvisioningClient.inviteUser(kcOrgId, request.getEmail());
      log.info("Sent invitation to {} for org {}", request.getEmail(), kcOrgId);

      request.setStatus(AccessRequestStatus.APPROVED);
      request.setReviewedBy(adminUserId);
      request.setReviewedAt(Instant.now());
      request.setProvisioningError(null);

      return accessRequestRepository.save(request);
    } catch (Exception e) {
      log.error("Approval failed for request {}: {}", requestId, e.getMessage(), e);
      String errorMsg = e.getMessage();
      if (errorMsg != null && errorMsg.length() > 500) {
        errorMsg = errorMsg.substring(0, 500);
      }
      request.setProvisioningError(errorMsg);
      accessRequestRepository.save(request);
      throw e;
    }
  }

  /**
   * Lists access requests, optionally filtered by status.
   *
   * @param status optional status filter; if null, returns all requests
   * @return list of access requests ordered by creation date ascending
   */
  @Transactional(readOnly = true)
  public List<AccessRequest> listRequests(@Nullable AccessRequestStatus status) {
    if (status != null) {
      return accessRequestRepository.findByStatusOrderByCreatedAtAsc(status);
    }
    return accessRequestRepository.findAll(Sort.by("createdAt"));
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
