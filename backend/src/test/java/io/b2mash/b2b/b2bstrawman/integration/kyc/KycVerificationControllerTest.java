package io.b2mash.b2b.b2bstrawman.integration.kyc;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KycVerificationControllerTest {

  private static final String ORG_ID = "org_kyc_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ChecklistInstanceItemRepository checklistInstanceItemRepository;
  @Autowired private ChecklistInstanceRepository checklistInstanceRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private IntegrationRegistry integrationRegistry;
  @Autowired private SecretStore secretStore;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID checklistItemId;
  private String verificationReference;
  private final AtomicInteger counter = new AtomicInteger(0);

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "KYC Controller Test Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_kyc_ctrl_owner",
                "kyc_ctrl@test.com",
                "KYC Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_kyc_ctrl_member",
        "kyc_ctrl_member@test.com",
        "KYC Ctrl Member",
        "member");

    // Create customer and checklist item, configure checkid adapter
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "KYC Ctrl Test Corp", "kyc_ctrl_test@test.com", memberId));
                  customerId = customer.getId();

                  var item = createChecklistItem();
                  checklistItemId = item.getId();

                  configureKycProvider("checkid");
                }));
  }

  // --- 457.3: Happy path integration tests ---

  @Test
  @Order(1)
  void postVerify_withValidRequest_returnsVerificationResult() throws Exception {
    mockMvc
        .perform(
            post("/api/kyc/verify")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_kyc_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "checklistInstanceItemId": "%s",
                      "idNumber": "9001015009087",
                      "fullName": "Jane Smith",
                      "idDocumentType": "SA_ID",
                      "consentAcknowledged": true
                    }
                    """
                        .formatted(customerId, checklistItemId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
        .andExpect(jsonPath("$.providerName").value("checkid"))
        .andExpect(jsonPath("$.providerReference").exists())
        .andExpect(jsonPath("$.checklistItemUpdated").value(true));

    // Capture the verification reference for the next test
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item =
                      checklistInstanceItemRepository.findById(checklistItemId).orElseThrow();
                  verificationReference = item.getVerificationReference();
                }));
  }

  @Test
  @Order(2)
  void getResult_withValidReference_returnsResult() throws Exception {
    mockMvc
        .perform(
            get("/api/kyc/result/" + verificationReference)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_kyc_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
        .andExpect(jsonPath("$.providerName").value("checkid"))
        .andExpect(jsonPath("$.providerReference").value(verificationReference));
  }

  @Test
  @Order(3)
  void getKycStatus_returnsConfiguredStatus() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/kyc/status")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_kyc_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.provider").value("checkid"));
  }

  // --- 457.4: Validation and error tests ---

  @Test
  @Order(4)
  void postVerify_withConsentFalse_returns400() throws Exception {
    // Create a fresh checklist item for this test
    var freshItemId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var item = createChecklistItem();
                  freshItemId[0] = item.getId();
                }));

    mockMvc
        .perform(
            post("/api/kyc/verify")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_kyc_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "checklistInstanceItemId": "%s",
                      "idNumber": "9001015009087",
                      "fullName": "Jane Smith",
                      "idDocumentType": "SA_ID",
                      "consentAcknowledged": false
                    }
                    """
                        .formatted(customerId, freshItemId[0])))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void postVerify_withMissingRequiredFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/kyc/verify")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_kyc_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "checklistInstanceItemId": "%s",
                      "consentAcknowledged": true
                    }
                    """
                        .formatted(customerId, checklistItemId)))
        .andExpect(status().isBadRequest());
  }

  // --- 457.5: Authorization test ---

  @Test
  @Order(6)
  void postVerify_withMemberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/kyc/verify")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_kyc_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "checklistInstanceItemId": "%s",
                      "idNumber": "9001015009087",
                      "fullName": "Jane Smith",
                      "idDocumentType": "SA_ID",
                      "consentAcknowledged": true
                    }
                    """
                        .formatted(customerId, checklistItemId)))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private ChecklistInstanceItem createChecklistItem() {
    int idx = counter.incrementAndGet();
    var template =
        new ChecklistTemplate(
            "KYC Ctrl Template " + idx,
            "Template for KYC controller test " + idx,
            "kyc-ctrl-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8),
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
    secretStore.store(
        IntegrationKeys.apiKey(IntegrationDomain.KYC_VERIFICATION, slug), "test-api-key");
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
