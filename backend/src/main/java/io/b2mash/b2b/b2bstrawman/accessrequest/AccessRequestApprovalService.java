package io.b2mash.b2b.b2bstrawman.accessrequest;

import io.b2mash.b2b.b2bstrawman.accessrequest.dto.AccessRequestDtos.AccessRequestResponse;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates the approval and rejection of access requests. On approval, provisions a Keycloak
 * organization, a tenant schema, and sends an invitation to the requester.
 */
@Service
public class AccessRequestApprovalService {

  private static final Logger log = LoggerFactory.getLogger(AccessRequestApprovalService.class);

  private static final Map<String, String> INDUSTRY_TO_PROFILE =
      Map.of(
          "Accounting", "accounting-za",
          "Legal Services", "legal-za");

  private static final String DEFAULT_PASSWORD = "password";

  private final AccessRequestRepository accessRequestRepository;
  private final KeycloakProvisioningClient keycloakProvisioningClient;
  private final TenantProvisioningService tenantProvisioningService;
  private final TransactionTemplate txTemplate;
  private final boolean setDefaultPassword;

  public AccessRequestApprovalService(
      AccessRequestRepository accessRequestRepository,
      @Nullable KeycloakProvisioningClient keycloakProvisioningClient,
      TenantProvisioningService tenantProvisioningService,
      TransactionTemplate txTemplate,
      @Value("${app.keycloak.set-default-password:false}") boolean setDefaultPassword) {
    this.accessRequestRepository = accessRequestRepository;
    this.keycloakProvisioningClient = keycloakProvisioningClient;
    this.tenantProvisioningService = tenantProvisioningService;
    this.txTemplate = txTemplate;
    this.setDefaultPassword = setDefaultPassword;
  }

  /**
   * Approves a pending access request. Provisions a Keycloak org, tenant schema, and sends an
   * invitation to the requester.
   *
   * <p>This method intentionally does NOT use a single @Transactional because tenant provisioning
   * (seeders) needs its own DB connections with the correct tenant search_path. An outer
   * transaction would force seeders to reuse the platform admin's connection (search_path=public),
   * causing "relation does not exist" errors.
   *
   * @param requestId the access request ID
   * @param adminUserId the platform admin user ID performing the approval
   * @return the updated access request
   */
  public AccessRequest approve(UUID requestId, String adminUserId) {
    if (keycloakProvisioningClient == null) {
      throw new IllegalStateException(
          "Keycloak admin client not configured — set keycloak.admin.auth-server-url");
    }

    // Step 1: Validate and load the request (short transaction)
    var request =
        txTemplate.execute(
            tx -> {
              var req = findPendingRequest(requestId);
              return req;
            });

    String orgName = request.getOrganizationName();
    String slug = slugify(orgName);

    try {
      // Step 2: Create KC org if needed, persist kcOrgId immediately
      String kcOrgId = request.getKeycloakOrgId();
      if (kcOrgId == null) {
        kcOrgId = keycloakProvisioningClient.createOrganization(orgName, slug);
        log.info("Created Keycloak organization '{}' with ID {}", orgName, kcOrgId);
        final String orgId = kcOrgId;
        txTemplate.executeWithoutResult(
            tx -> {
              request.setKeycloakOrgId(orgId);
              accessRequestRepository.save(request);
            });
      } else {
        log.info(
            "Keycloak org {} already exists for request {}, skipping creation", kcOrgId, requestId);
      }

      // Step 3: Provision tenant schema (NO outer transaction — seeders manage their own)
      // Use the slug (= Keycloak org alias) as the org identifier for schema mapping,
      // because Keycloak JWTs include the alias in the "organization" claim, not the UUID.
      String verticalProfile = INDUSTRY_TO_PROFILE.get(request.getIndustry());
      tenantProvisioningService.provisionTenant(
          slug, orgName, verticalProfile, request.getCountry());
      log.info("Provisioned tenant schema for org {} (slug={})", kcOrgId, slug);

      // Step 4: Invite user via Keycloak and mark as org creator
      keycloakProvisioningClient.inviteUser(kcOrgId, request.getEmail());
      log.info("Sent invitation to {} for org {}", request.getEmail(), kcOrgId);
      keycloakProvisioningClient.setOrgCreator(kcOrgId, request.getEmail());

      // Step 4b: Set default password for local dev (skips email-based registration)
      if (setDefaultPassword) {
        keycloakProvisioningClient.setUserPassword(request.getEmail(), DEFAULT_PASSWORD);
        log.info("Set default password for {} (local dev mode)", request.getEmail());
      }

      // Step 5: Mark as approved (short transaction)
      return txTemplate.execute(
          tx -> {
            request.setStatus(AccessRequestStatus.APPROVED);
            request.setReviewedBy(adminUserId);
            request.setReviewedAt(Instant.now());
            request.setProvisioningError(null);
            return accessRequestRepository.save(request);
          });
    } catch (Exception e) {
      log.error("Approval failed for request {}: {}", requestId, e.getMessage(), e);
      String rawMsg = e.getMessage();
      String errorMsg =
          (rawMsg != null && rawMsg.length() > 500) ? rawMsg.substring(0, 500) : rawMsg;
      // Persist the error (separate transaction so it always commits)
      txTemplate.executeWithoutResult(
          tx -> {
            request.setProvisioningError(errorMsg);
            accessRequestRepository.save(request);
          });
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
  public List<AccessRequestResponse> listRequests(@Nullable AccessRequestStatus status) {
    List<AccessRequest> requests;
    if (status != null) {
      requests = accessRequestRepository.findByStatusOrderByCreatedAtAsc(status);
    } else {
      requests = accessRequestRepository.findAll(Sort.by("createdAt"));
    }
    return requests.stream().map(AccessRequestResponse::from).toList();
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
