package io.b2mash.b2b.b2bstrawman.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService.ProvisioningResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestApprovalServiceTest {

  @Autowired private AccessRequestApprovalService approvalService;
  @Autowired private AccessRequestRepository accessRequestRepository;
  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private TenantProvisioningService tenantProvisioningService;

  private static final String ADMIN_USER_ID = "admin_001";
  private static final String KC_ORG_ID = "kc-org-123";

  @BeforeEach
  void setUp() {
    accessRequestRepository.deleteAll();
  }

  @Test
  void approve_pendingRequest_createsOrgAndProvisionsTenant() {
    var request = createPendingRequest("approve-happy@acme.com", "Acme Corp");

    when(keycloakProvisioningClient.createOrganization("Acme Corp", "acme-corp"))
        .thenReturn(KC_ORG_ID);
    when(tenantProvisioningService.provisionTenant("acme-corp", "Acme Corp", "legal-za", "ZA"))
        .thenReturn(ProvisioningResult.success("tenant_acme"));

    var result = approvalService.approve(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    verify(keycloakProvisioningClient).createOrganization("Acme Corp", "acme-corp");
    verify(tenantProvisioningService).provisionTenant("acme-corp", "Acme Corp", "legal-za", "ZA");
    verify(keycloakProvisioningClient).inviteUser(KC_ORG_ID, "approve-happy@acme.com");
  }

  @Test
  void approve_setsKeycloakOrgIdAndReviewFields() {
    var request = createPendingRequest("approve-fields@acme.com", "Fields Corp");

    when(keycloakProvisioningClient.createOrganization("Fields Corp", "fields-corp"))
        .thenReturn(KC_ORG_ID);
    when(tenantProvisioningService.provisionTenant("fields-corp", "Fields Corp", "legal-za", "ZA"))
        .thenReturn(ProvisioningResult.success("tenant_fields"));

    var result = approvalService.approve(request.getId(), ADMIN_USER_ID);

    var saved = accessRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(saved.getKeycloakOrgId()).isEqualTo(KC_ORG_ID);
    assertThat(saved.getReviewedBy()).isEqualTo(ADMIN_USER_ID);
    assertThat(saved.getReviewedAt()).isNotNull();
    assertThat(saved.getProvisioningError()).isNull();
  }

  @Test
  void approve_nonExistentRequest_throwsNotFound() {
    UUID fakeId = UUID.randomUUID();

    assertThatThrownBy(() -> approvalService.approve(fakeId, ADMIN_USER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void approve_alreadyApproved_throwsConflict() {
    var request = createPendingRequest("already-approved@acme.com", "Already Approved Corp");
    request.setStatus(AccessRequestStatus.APPROVED);
    accessRequestRepository.save(request);

    assertThatThrownBy(() -> approvalService.approve(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void approve_alreadyRejected_throwsConflict() {
    var request = createPendingRequest("already-rejected@acme.com", "Already Rejected Corp");
    request.setStatus(AccessRequestStatus.REJECTED);
    accessRequestRepository.save(request);

    assertThatThrownBy(() -> approvalService.approve(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void approve_provisioningFails_savesErrorAndKeycloakOrgId() {
    var request = createPendingRequest("provision-fail@acme.com", "Fail Corp");

    when(keycloakProvisioningClient.createOrganization("Fail Corp", "fail-corp"))
        .thenReturn(KC_ORG_ID);
    when(tenantProvisioningService.provisionTenant("fail-corp", "Fail Corp", "legal-za", "ZA"))
        .thenThrow(new RuntimeException("Schema creation failed"));

    assertThatThrownBy(() -> approvalService.approve(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Schema creation");

    var saved = accessRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(saved.getProvisioningError()).isEqualTo("Schema creation failed");
    assertThat(saved.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
    assertThat(saved.getKeycloakOrgId()).isEqualTo(KC_ORG_ID);
    verify(keycloakProvisioningClient, never()).inviteUser(anyString(), anyString());
  }

  @Test
  void approve_retryAfterPartialFailure_skipsOrgCreation() {
    var request = createPendingRequest("retry@acme.com", "Retry Corp");
    // Simulate partial failure: kcOrgId already saved from first attempt
    request.setKeycloakOrgId(KC_ORG_ID);
    request.setProvisioningError("Schema creation failed");
    accessRequestRepository.save(request);

    when(tenantProvisioningService.provisionTenant("retry-corp", "Retry Corp", "legal-za", "ZA"))
        .thenReturn(ProvisioningResult.success("tenant_retry"));

    var result = approvalService.approve(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    assertThat(result.getKeycloakOrgId()).isEqualTo(KC_ORG_ID);
    assertThat(result.getProvisioningError()).isNull();
    // Should NOT have called createOrganization again
    verify(keycloakProvisioningClient, never()).createOrganization(anyString(), anyString());
    verify(tenantProvisioningService).provisionTenant("retry-corp", "Retry Corp", "legal-za", "ZA");
    verify(keycloakProvisioningClient).inviteUser(KC_ORG_ID, "retry@acme.com");
  }

  @Test
  void reject_pendingRequest_setsRejectedStatus() {
    var request = createPendingRequest("reject-happy@acme.com", "Reject Corp");

    var result = approvalService.reject(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.REJECTED);
    assertThat(result.getReviewedBy()).isEqualTo(ADMIN_USER_ID);
    assertThat(result.getReviewedAt()).isNotNull();

    var saved = accessRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(AccessRequestStatus.REJECTED);
  }

  @Test
  void reject_alreadyApproved_throwsConflict() {
    var request = createPendingRequest("reject-conflict@acme.com", "Conflict Corp");
    request.setStatus(AccessRequestStatus.APPROVED);
    accessRequestRepository.save(request);

    assertThatThrownBy(() -> approvalService.reject(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(ResourceConflictException.class);
  }

  private AccessRequest createPendingRequest(String email, String orgName) {
    var request = new AccessRequest(email, "Test User", orgName, "ZA", "Legal Services");
    request.setStatus(AccessRequestStatus.PENDING);
    return accessRequestRepository.save(request);
  }
}
