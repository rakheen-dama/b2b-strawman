package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Controller integration test for {@link DisbursementController}. Covers capability gating, module
 * guard, request validation, and the happy-path CRUD + state-transition endpoints end-to-end
 * through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementControllerIntegrationTest {
  private static final String LEGAL_ORG_ID = "org_disb_ctrl_legal";
  private static final String NON_LEGAL_ORG_ID = "org_disb_ctrl_consulting";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String legalTenantSchema;
  private String nonLegalTenantSchema;
  private UUID legalOwnerMemberId;
  private UUID legalCustomerId;
  private UUID legalProjectId;
  private UUID nonLegalProjectId;
  private UUID nonLegalCustomerId;

  @BeforeAll
  void setup() throws Exception {
    // --- Legal tenant (has disbursements module) ---
    legalTenantSchema =
        provisioningService
            .provisionTenant(LEGAL_ORG_ID, "Disb Ctrl Legal Org", "legal-za")
            .schemaName();

    legalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_disb_ctrl_owner",
                "disb_ctrl_owner@test.com",
                "Disb Ctrl Owner",
                "owner"));
    // Member role for the 403 capability test — has MANAGE_DISBURSEMENTS but NOT
    // APPROVE_DISBURSEMENTS per V100 capability seeding.
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_disb_ctrl_member",
        "disb_ctrl_member@test.com",
        "Disb Ctrl Member",
        "member");

    // Ensure disbursements + trust_accounting modules are enabled
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("disbursements", "trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Seed a customer + project in the legal tenant for controller create/list tests
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Disb Ctrl Client",
                                  "disb_ctrl_client@test.com",
                                  legalOwnerMemberId));
                      legalCustomerId = customer.getId();

                      var project =
                          new Project(
                              "Disb Ctrl Matter", "Test matter for disb ctrl", legalOwnerMemberId);
                      project.setCustomerId(legalCustomerId);
                      project = projectRepository.saveAndFlush(project);
                      legalProjectId = project.getId();
                    }));

    // --- Non-legal tenant (does NOT have disbursements module) ---
    nonLegalTenantSchema =
        provisioningService
            .provisionTenant(NON_LEGAL_ORG_ID, "Disb Ctrl Consulting Org", "consulting-za")
            .schemaName();

    var nonLegalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                NON_LEGAL_ORG_ID,
                "user_disb_ctrl_consulting_owner",
                "disb_ctrl_consulting_owner@test.com",
                "Disb Ctrl Consulting Owner",
                "owner"));

    // Seed a customer + project in the non-legal tenant (to pass JSR-303 @NotNull
    // on CreateDisbursementRequest before reaching the module-guard check).
    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalTenantSchema)
        .where(RequestScopes.ORG_ID, NON_LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nonLegalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Non-legal Client",
                                  "nonlegal_client@test.com",
                                  nonLegalOwnerMemberId));
                      nonLegalCustomerId = customer.getId();

                      var project =
                          new Project(
                              "Non-legal Project", "Non-legal engagement", nonLegalOwnerMemberId);
                      project.setCustomerId(nonLegalCustomerId);
                      project = projectRepository.saveAndFlush(project);
                      nonLegalProjectId = project.getId();
                    }));
  }

  // --- 486.16: Module guard ---

  @Test
  void create_onNonLegalTenant_isBlockedByModuleGuard() throws Exception {
    // consulting-za profile does not enable the "disbursements" module, so the module guard
    // in DisbursementService.create() must reject the call.
    // ModuleNotEnabledException is mapped to HTTP 403.
    mockMvc
        .perform(
            post("/api/legal/disbursements")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_disb_ctrl_consulting_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson(nonLegalProjectId, nonLegalCustomerId, "Blocked body")))
        .andExpect(status().isForbidden());
  }

  // --- 486.16: Capability guard ---

  @Test
  void approve_asMember_returns403() throws Exception {
    // member role has MANAGE_DISBURSEMENTS but NOT APPROVE_DISBURSEMENTS per V100 seeding.
    // Path params use a throwaway UUID — the @RequiresCapability check fires before the handler.
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + UUID.randomUUID() + "/approve")
                .with(TestJwtFactory.memberJwt(LEGAL_ORG_ID, "user_disb_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"ok\"}"))
        .andExpect(status().isForbidden());
  }

  // --- 486.16: Happy-path create ---

  @Test
  void create_withValidBody_returns201AndExpectedShape() throws Exception {
    mockMvc
        .perform(
            post("/api/legal/disbursements")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson(legalProjectId, legalCustomerId, "Sheriff fee entry")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.projectId").value(legalProjectId.toString()))
        .andExpect(jsonPath("$.customerId").value(legalCustomerId.toString()))
        .andExpect(jsonPath("$.description").value("Sheriff fee entry"))
        .andExpect(jsonPath("$.amount").value(100.00))
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.category").value("SHERIFF_FEES"))
        // Statutory pass-through category defaults to ZERO_RATED_PASS_THROUGH
        .andExpect(jsonPath("$.vatTreatment").value("ZERO_RATED_PASS_THROUGH"))
        .andExpect(jsonPath("$.paymentSource").value("OFFICE_ACCOUNT"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"));
  }

  // --- 486.16: Update on APPROVED row blocked ---

  @Test
  void update_onApprovedRow_returns400() throws Exception {
    // Create → submit → approve, then PATCH — service's state-machine guard throws
    // InvalidStateException which maps to HTTP 400 via GlobalExceptionHandler.
    var ownerJwt = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner");
    var created =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createRequestJson(
                            legalProjectId, legalCustomerId, "To be approved and locked")))
            .andExpect(status().isCreated())
            .andReturn();
    var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(post("/api/legal/disbursements/" + id + "/submit").with(ownerJwt))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + id + "/approve")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"looks good\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/legal/disbursements/" + id)
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"cannot edit after approval\"}"))
        .andExpect(status().isBadRequest());
  }

  // --- 486.16: Write-off without reason ---

  @Test
  void writeOff_withoutReason_returns400() throws Exception {
    // Bean-validation @NotBlank on WriteOffRequest.reason triggers MethodArgumentNotValidException
    // which maps to 400. (Empty-string reason also yields 400 via @NotBlank.)
    var ownerJwt = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner");
    var created =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createRequestJson(
                            legalProjectId, legalCustomerId, "To be written off badly")))
            .andExpect(status().isCreated())
            .andReturn();
    var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            post("/api/legal/disbursements/" + id + "/write-off")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  // --- 486.16: List with filters ---

  @Test
  void list_withFilters_narrowsResults() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner");

    // Seed two disbursements on the same project: one will stay DRAFT/UNBILLED,
    // one will be submitted + approved so approval_status = APPROVED.
    var draftResult =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createRequestJson(legalProjectId, legalCustomerId, "Filter test DRAFT")))
            .andExpect(status().isCreated())
            .andReturn();
    var draftId = JsonPath.read(draftResult.getResponse().getContentAsString(), "$.id").toString();

    var approvedResult =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createRequestJson(legalProjectId, legalCustomerId, "Filter test APPROVED")))
            .andExpect(status().isCreated())
            .andReturn();
    var approvedId =
        JsonPath.read(approvedResult.getResponse().getContentAsString(), "$.id").toString();
    mockMvc
        .perform(post("/api/legal/disbursements/" + approvedId + "/submit").with(ownerJwt))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + approvedId + "/approve")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"ok\"}"))
        .andExpect(status().isOk());

    // approvalStatus=APPROVED should yield only the approved one
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .with(ownerJwt)
                .param("projectId", legalProjectId.toString())
                .param("approvalStatus", "APPROVED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + approvedId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + draftId + "')]").doesNotExist());

    // billingStatus=UNBILLED should include both (neither is billed yet)
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .with(ownerJwt)
                .param("projectId", legalProjectId.toString())
                .param("billingStatus", "UNBILLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + approvedId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id=='" + draftId + "')]").exists());
  }

  // --- 486.16: Multipart receipt upload ---

  @Test
  void uploadReceipt_persistsDocumentIdAndLinksItOnRow() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner");
    var created =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createRequestJson(legalProjectId, legalCustomerId, "Receipt upload test")))
            .andExpect(status().isCreated())
            .andReturn();
    var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

    var file =
        new MockMultipartFile(
            "file", "receipt.pdf", MediaType.APPLICATION_PDF_VALUE, "receipt bytes".getBytes());

    mockMvc
        .perform(multipart("/api/legal/disbursements/" + id + "/receipt").file(file).with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.receiptDocumentId").isNotEmpty());
  }

  // --- 486.16: Get one ---

  @Test
  void getOne_returnsFullDisbursementResponse() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner");
    var created =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestJson(legalProjectId, legalCustomerId, "Get-one test")))
            .andExpect(status().isCreated())
            .andReturn();
    var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(get("/api/legal/disbursements/" + id).with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.projectId").value(legalProjectId.toString()))
        .andExpect(jsonPath("$.customerId").value(legalCustomerId.toString()))
        .andExpect(jsonPath("$.description").value("Get-one test"))
        .andExpect(jsonPath("$.category").exists())
        .andExpect(jsonPath("$.vatTreatment").exists())
        .andExpect(jsonPath("$.paymentSource").exists())
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"))
        .andExpect(jsonPath("$.createdBy").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  // --- Helpers ---

  private String createRequestJson(UUID projectId, UUID customerId, String description) {
    return """
        {
          "projectId": "%s",
          "customerId": "%s",
          "description": "%s",
          "amount": 100.00,
          "currency": "ZAR",
          "category": "SHERIFF_FEES",
          "paymentSource": "OFFICE_ACCOUNT",
          "incurredDate": "2026-03-01",
          "supplierName": "Supplier X"
        }
        """
        .formatted(projectId, customerId, description);
  }
}
