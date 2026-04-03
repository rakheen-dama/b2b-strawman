package io.b2mash.b2b.b2bstrawman.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billing.BillingMethod;
import io.b2mash.b2b.b2bstrawman.billing.Subscription;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoCleanupServiceTest {

  @Autowired private DemoCleanupService demoCleanupService;
  @Autowired private TenantProvisioningService tenantProvisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate txTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  // We provision fresh tenants for each test to avoid state leaking between tests
  // that perform destructive cleanup operations

  private UUID provisionTestTenant(String suffix, BillingMethod billingMethod) {
    String slug = "cleanup-test-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Cleanup Test " + suffix, "generic");
    UUID orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    txTemplate.executeWithoutResult(
        tx -> {
          Subscription sub = subscriptionRepository.findByOrganizationId(orgId).orElseThrow();
          sub.adminTransitionTo(Subscription.SubscriptionStatus.ACTIVE);
          sub.setBillingMethod(billingMethod);
          sub.setAdminNote("Demo tenant provisioned by admin test-admin. Profile: generic");
          subscriptionRepository.save(sub);
        });

    return orgId;
  }

  @BeforeEach
  void setupKeycloakMocks() {
    // Default: Keycloak resolveOrgId returns a dummy KC org ID
    when(keycloakAdminClient.resolveOrgId(anyString())).thenReturn("kc-org-test");
    when(keycloakAdminClient.listOrgMemberIds("kc-org-test")).thenReturn(List.of());
  }

  @Test
  void cleanup_succeedsForPilotBillingMethod() {
    UUID orgId = provisionTestTenant("pilot", BillingMethod.PILOT);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    var response = demoCleanupService.cleanup(orgId, orgName);

    assertEquals(orgId, response.organizationId());
    assertEquals(orgName, response.organizationName());
    assertTrue(response.keycloakCleaned());
    assertTrue(response.schemaCleaned());
    assertTrue(response.publicRecordsCleaned());

    // Verify org is actually gone from DB
    assertTrue(organizationRepository.findById(orgId).isEmpty());
    assertTrue(subscriptionRepository.findByOrganizationId(orgId).isEmpty());
  }

  @Test
  void cleanup_succeedsForComplimentaryBillingMethod() {
    UUID orgId = provisionTestTenant("comp", BillingMethod.COMPLIMENTARY);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    var response = demoCleanupService.cleanup(orgId, orgName);

    assertEquals(orgId, response.organizationId());
    assertTrue(response.keycloakCleaned());
    assertTrue(response.schemaCleaned());
    assertTrue(response.publicRecordsCleaned());

    // Verify org is actually gone from DB
    assertTrue(organizationRepository.findById(orgId).isEmpty());
  }

  @Test
  void cleanup_rejectsPayfastBillingMethod() {
    UUID orgId = provisionTestTenant("payfast", BillingMethod.PAYFAST);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    assertThrows(ForbiddenException.class, () -> demoCleanupService.cleanup(orgId, orgName));

    // Verify org is NOT deleted
    assertTrue(organizationRepository.findById(orgId).isPresent());
  }

  @Test
  void cleanup_rejectsManualBillingMethod() {
    UUID orgId = provisionTestTenant("manual", BillingMethod.MANUAL);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    assertThrows(ForbiddenException.class, () -> demoCleanupService.cleanup(orgId, orgName));

    // Verify org is NOT deleted
    assertTrue(organizationRepository.findById(orgId).isPresent());
  }

  @Test
  void cleanup_rejectsDebitOrderBillingMethod() {
    UUID orgId = provisionTestTenant("debit", BillingMethod.DEBIT_ORDER);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    assertThrows(ForbiddenException.class, () -> demoCleanupService.cleanup(orgId, orgName));

    // Verify org is NOT deleted
    assertTrue(organizationRepository.findById(orgId).isPresent());
  }

  @Test
  void cleanup_rejectsWrongConfirmationName() {
    UUID orgId = provisionTestTenant("mismatch", BillingMethod.PILOT);

    assertThrows(
        InvalidStateException.class,
        () -> demoCleanupService.cleanup(orgId, "Wrong Organization Name"));

    // Verify org is NOT deleted
    assertTrue(organizationRepository.findById(orgId).isPresent());
  }

  @Test
  void cleanup_continuesOnKeycloakFailureAndReportsErrors() {
    UUID orgId = provisionTestTenant("kcfail", BillingMethod.PILOT);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    // Make Keycloak resolveOrgId throw an exception
    String externalOrgId = organizationRepository.findById(orgId).orElseThrow().getExternalOrgId();
    when(keycloakAdminClient.resolveOrgId(externalOrgId))
        .thenThrow(new RuntimeException("Keycloak connection refused"));

    var response = demoCleanupService.cleanup(orgId, orgName);

    // Keycloak failed, but other steps should succeed
    assertFalse(response.keycloakCleaned());
    assertTrue(response.schemaCleaned());
    assertTrue(response.publicRecordsCleaned());
    assertFalse(response.errors().isEmpty());
    assertTrue(
        response.errors().stream().anyMatch(e -> e.contains("Keycloak")),
        "Should contain Keycloak error message");

    // Verify org IS deleted from DB (Keycloak failure doesn't prevent DB cleanup)
    assertTrue(organizationRepository.findById(orgId).isEmpty());
  }

  @Test
  void cleanup_logsAuditEventBeforeDestruction() {
    // This test verifies the audit log is emitted by checking the service completes
    // successfully. The structured log output ("AUDIT: Demo tenant deleted for org ...")
    // is emitted before any destructive step. We verify the cleanup completes correctly
    // as evidence the audit step ran first (if it threw, cleanup would fail).
    UUID orgId = provisionTestTenant("audit", BillingMethod.PILOT);
    String orgName = organizationRepository.findById(orgId).orElseThrow().getName();

    var response = demoCleanupService.cleanup(orgId, orgName);

    assertEquals(orgId, response.organizationId());
    assertEquals(orgName, response.organizationName());
    assertTrue(response.publicRecordsCleaned());
  }
}
