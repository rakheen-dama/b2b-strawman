package io.b2mash.b2b.b2bstrawman.integration.kyc;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KycVerificationServiceTest {
  private static final String ORG_ID = "org_kyc_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private KycVerificationService kycVerificationService;
  @Autowired private ChecklistInstanceItemRepository checklistInstanceItemRepository;
  @Autowired private ChecklistInstanceRepository checklistInstanceRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry integrationRegistry;
  @Autowired private SecretStore secretStore;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private final AtomicInteger counter = new AtomicInteger(0);

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "KYC Service Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_kyc_svc_owner",
                "kyc_svc@test.com",
                "KYC Svc Owner",
                "owner"));

    // Create shared test data: customer
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("KYC Test Corp", "kyc_test@test.com", memberId));
                  customerId = customer.getId();
                }));
  }

  // --- 456.11: Service happy path tests ---

  @Test
  void noopAdapterReturnsErrorAndItemUnchanged() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = createChecklistItem();
                  var request =
                      new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

                  // NoOp adapter returns ERROR, so item should remain unchanged
                  var result =
                      kycVerificationService.verifyIdentity(
                          customerId, item.getId(), request, true, memberId);

                  assertThat(result.status()).isEqualTo(KycVerificationStatus.ERROR);

                  var updated =
                      checklistInstanceItemRepository.findById(item.getId()).orElseThrow();
                  assertThat(updated.getStatus()).isEqualTo("PENDING");
                  assertThat(updated.getVerificationProvider()).isNull();
                }));
  }

  @Test
  void needsReviewUpdatesVerificationColumnsButItemStaysPending() {
    // Configure the checkid adapter which always returns NEEDS_REVIEW
    runInTenant(
        () -> {
          configureKycProvider("checkid");
          transactionTemplate.executeWithoutResult(
              tx -> {
                var item = createChecklistItem();
                var request =
                    new KycVerificationRequest("9001015009087", "Jane Smith", null, "SA_ID");

                var result =
                    kycVerificationService.verifyIdentity(
                        customerId, item.getId(), request, true, memberId);

                assertThat(result.status()).isEqualTo(KycVerificationStatus.NEEDS_REVIEW);

                var updated = checklistInstanceItemRepository.findById(item.getId()).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo("PENDING");
                assertThat(updated.getVerificationProvider()).isEqualTo("checkid");
                assertThat(updated.getVerificationStatus()).isEqualTo("NEEDS_REVIEW");
                assertThat(updated.getVerificationReference()).startsWith("CID-");
              });
          cleanupKycProvider();
        });
  }

  @Test
  void errorStatusDoesNotUpdateChecklistItem() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = createChecklistItem();
                  var request =
                      new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

                  // NoOp returns ERROR
                  var result =
                      kycVerificationService.verifyIdentity(
                          customerId, item.getId(), request, true, memberId);

                  assertThat(result.status()).isEqualTo(KycVerificationStatus.ERROR);

                  var updated =
                      checklistInstanceItemRepository.findById(item.getId()).orElseThrow();
                  assertThat(updated.getStatus()).isEqualTo("PENDING");
                  assertThat(updated.getVerificationProvider()).isNull();
                  assertThat(updated.getVerificationStatus()).isNull();
                  assertThat(updated.getVerificationReference()).isNull();
                }));
  }

  @Test
  void popiaConsentMetadataRecordedWithCorrectActorAndTimestamp() {
    runInTenant(
        () -> {
          configureKycProvider("checkid");
          transactionTemplate.executeWithoutResult(
              tx -> {
                var item = createChecklistItem();
                var request =
                    new KycVerificationRequest("9001015009087", "Jane Smith", null, "SA_ID");

                kycVerificationService.verifyIdentity(
                    customerId, item.getId(), request, true, memberId);

                var updated = checklistInstanceItemRepository.findById(item.getId()).orElseThrow();
                assertThat(updated.getVerificationMetadata()).isNotNull();
                assertThat(updated.getVerificationMetadata())
                    .containsKey("consent_acknowledged_at");
                assertThat(updated.getVerificationMetadata().get("consent_acknowledged_by"))
                    .isEqualTo(memberId.toString());
              });
          cleanupKycProvider();
        });
  }

  // --- 456.12: Consent and error handling tests ---

  @Test
  void consentNotAcknowledgedThrowsInvalidStateException() {
    // Create item in its own transaction, then test the exception separately.
    // This avoids UnexpectedRollback from mixing assertThatThrownBy with transactionTemplate.
    var itemId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = createChecklistItem();
                  itemId[0] = item.getId();
                }));

    runInTenant(
        () -> {
          var request = new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");
          assertThatThrownBy(
                  () ->
                      kycVerificationService.verifyIdentity(
                          customerId, itemId[0], request, false, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void checklistItemNotFoundThrowsResourceNotFoundException() {
    runInTenant(
        () -> {
          var request = new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");
          var nonExistentItemId = UUID.randomUUID();

          assertThatThrownBy(
                  () ->
                      kycVerificationService.verifyIdentity(
                          customerId, nonExistentItemId, request, true, memberId))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  // --- 456.13: Integration status tests ---

  @Test
  void kycConfiguredReturnsTrueWithProvider() {
    runInTenant(
        () -> {
          configureKycProvider("checkid");
          transactionTemplate.executeWithoutResult(
              tx -> {
                var status = kycVerificationService.getKycIntegrationStatus();
                assertThat(status.configured()).isTrue();
                assertThat(status.provider()).isEqualTo("checkid");
              });
          cleanupKycProvider();
        });
  }

  @Test
  void kycNotConfiguredReturnsFalse() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var status = kycVerificationService.getKycIntegrationStatus();
                  assertThat(status.configured()).isFalse();
                  assertThat(status.provider()).isNull();
                }));
  }

  // --- 456.14: Audit event tests ---

  @Test
  void successfulVerificationEmitsCompletedAuditEvent() {
    runInTenant(
        () -> {
          configureKycProvider("checkid");
          transactionTemplate.executeWithoutResult(
              tx -> {
                var item = createChecklistItem();
                var request =
                    new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

                kycVerificationService.verifyIdentity(
                    customerId, item.getId(), request, true, memberId);

                var auditEvents =
                    auditEventRepository.findByFilter(
                        "checklist_instance_item",
                        item.getId(),
                        null,
                        "kyc_verification.completed",
                        null,
                        null,
                        Pageable.ofSize(10));
                assertThat(auditEvents.getContent()).isNotEmpty();
                assertThat(auditEvents.getContent().getFirst().getEventType())
                    .isEqualTo("kyc_verification.completed");
              });
          cleanupKycProvider();
        });
  }

  @Test
  void noopProviderCallEmitsFailedAuditEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = createChecklistItem();
                  var request =
                      new KycVerificationRequest("9001015009087", "John Doe", null, "SA_ID");

                  kycVerificationService.verifyIdentity(
                      customerId, item.getId(), request, true, memberId);

                  // NoOp returns ERROR, which emits kyc_verification.failed
                  var auditEvents =
                      auditEventRepository.findByFilter(
                          "checklist_instance_item",
                          item.getId(),
                          null,
                          "kyc_verification.failed",
                          null,
                          null,
                          Pageable.ofSize(10));
                  assertThat(auditEvents.getContent()).isNotEmpty();
                  assertThat(auditEvents.getContent().getFirst().getEventType())
                      .isEqualTo("kyc_verification.failed");
                }));
  }

  // --- Helpers ---

  private ChecklistInstanceItem createChecklistItem() {
    int idx = counter.incrementAndGet();

    // Create a unique template per test to avoid unique constraint on (customer_id, template_id)
    var template =
        new ChecklistTemplate(
            "KYC Test Template " + idx,
            "Template for KYC test " + idx,
            "kyc-test-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8),
            "ANY",
            "CUSTOM",
            false);
    template = checklistTemplateRepository.saveAndFlush(template);

    var templateItem = new ChecklistTemplateItem(template.getId(), "FICA Verification", 1, true);
    templateItem = checklistTemplateItemRepository.saveAndFlush(templateItem);

    var instance = new ChecklistInstance(template.getId(), customerId, Instant.now());
    instance = checklistInstanceRepository.saveAndFlush(instance);

    var item =
        new ChecklistInstanceItem(
            instance.getId(),
            templateItem.getId(),
            "FICA Verification " + idx,
            "Verify identity document",
            1,
            true,
            false,
            null);
    return checklistInstanceItemRepository.saveAndFlush(item);
  }

  private void configureKycProvider(String slug) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          var existing = orgIntegrationRepository.findByDomain(IntegrationDomain.KYC_VERIFICATION);
          if (existing.isPresent()) {
            var integration = existing.get();
            integration.updateProvider(slug, null);
            integration.enable();
            orgIntegrationRepository.save(integration);
          } else {
            var integration = new OrgIntegration(IntegrationDomain.KYC_VERIFICATION, slug);
            integration.enable();
            orgIntegrationRepository.save(integration);
          }
          // Store a dummy API key so the adapter can resolve it
          secretStore.store(
              IntegrationKeys.apiKey(IntegrationDomain.KYC_VERIFICATION, slug), "test-api-key");
        });
    // Evict the cache so the registry picks up the new provider
    integrationRegistry.evict(tenantSchema, IntegrationDomain.KYC_VERIFICATION);
  }

  private void cleanupKycProvider() {
    transactionTemplate.executeWithoutResult(
        tx -> {
          orgIntegrationRepository
              .findByDomain(IntegrationDomain.KYC_VERIFICATION)
              .ifPresent(
                  integration -> {
                    secretStore.delete(
                        IntegrationKeys.apiKey(
                            IntegrationDomain.KYC_VERIFICATION, integration.getProviderSlug()));
                    orgIntegrationRepository.delete(integration);
                  });
        });
    // Evict the cache so the registry doesn't serve stale config
    integrationRegistry.evict(tenantSchema, IntegrationDomain.KYC_VERIFICATION);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
