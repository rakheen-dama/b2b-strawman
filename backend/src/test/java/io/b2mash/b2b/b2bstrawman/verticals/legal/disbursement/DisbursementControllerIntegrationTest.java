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
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
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
 * Controller-level integration tests for {@link DisbursementController} (Epic 486B, task 486.16).
 *
 * <p>Covers module guard, capability checks, happy-path CRUD + status, validation errors, list
 * filters, and multipart receipt upload. Uses two provisioned tenants — one with the {@code
 * disbursements} module enabled (legal) and one without (non-legal) — so the module-guard branch
 * can be asserted at the HTTP boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementControllerIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_disbursement_ctrl_legal";
  private static final String NON_LEGAL_ORG_ID = "org_disbursement_ctrl_nonlegal";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String legalTenantSchema;
  private String nonLegalTenantSchema;

  private UUID legalOwnerMemberId;

  private UUID customerId;
  private UUID projectId;
  private UUID otherProjectId;

  @BeforeAll
  void setup() throws Exception {
    // --- Legal tenant ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Disbursement Controller Test Firm", null);

    legalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_disb_ctrl_owner",
                "disb_ctrl_owner@test.com",
                "Disbursement Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_disb_ctrl_member",
        "disb_ctrl_member@test.com",
        "Disbursement Ctrl Member",
        "member");

    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    // Enable disbursements module + seed customer + projects within tenant scope.
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "Disbursement Ctrl Client", "disb_ctrl@test.com", legalOwnerMemberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Disbursement Ctrl Matter",
                          "Matter for disbursement controller tests",
                          legalOwnerMemberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var other =
                      new Project(
                          "Ctrl Other Matter",
                          "Second matter for list filters",
                          legalOwnerMemberId);
                  other.setCustomerId(customerId);
                  other = projectRepository.saveAndFlush(other);
                  otherProjectId = other.getId();
                }));

    // --- Non-legal tenant (module NOT enabled) ---
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "Non-Legal Ctrl Firm", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NON_LEGAL_ORG_ID,
        "user_disb_nonlegal_owner",
        "disb_nonlegal_owner@test.com",
        "NonLegal Owner",
        "owner");
    nonLegalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();

    // Make sure the non-legal tenant has an OrgSettings row with NO modules enabled.
    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of());
                      orgSettingsRepository.save(settings);
                    }));
  }

  // ==========================================================================
  // Module guard
  // ==========================================================================

  @Test
  void nonLegalTenant_GET_isForbidden_fromModuleGuard() throws Exception {
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_disb_nonlegal_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("disbursements"));
  }

  // ==========================================================================
  // Capability checks
  // ==========================================================================

  @Test
  void legalTenant_memberWithoutApproveDisbursements_POST_approve_returns403() throws Exception {
    // Owner creates + submits a disbursement so it is in PENDING_APPROVAL.
    var created = createOfficeDisbursement("Counsel fee awaiting approval");
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + created + "/submit")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk());

    // Default Member role does NOT carry APPROVE_DISBURSEMENTS.
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + created + "/approve")
                .with(TestJwtFactory.memberJwt(LEGAL_ORG_ID, "user_disb_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"attempted approval\"}"))
        .andExpect(status().isForbidden());
  }

  // ==========================================================================
  // Happy-path CRUD + response shape
  // ==========================================================================

  @Test
  void legalTenant_admin_POST_create_validBody_returns201_andResponseShape() throws Exception {
    mockMvc
        .perform(
            post("/api/legal/disbursements")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(projectId, "Sheriff service fees", "250.00", "SHERIFF_FEES")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.category").value("SHERIFF_FEES"))
        .andExpect(jsonPath("$.description").value("Sheriff service fees"))
        .andExpect(jsonPath("$.amount").value(250.00))
        // SHERIFF_FEES defaults to ZERO_RATED_PASS_THROUGH.
        .andExpect(jsonPath("$.vatTreatment").value("ZERO_RATED_PASS_THROUGH"))
        .andExpect(jsonPath("$.vatAmount").value(0))
        .andExpect(jsonPath("$.paymentSource").value("OFFICE_ACCOUNT"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"));
  }

  @Test
  void legalTenant_GET_byId_returnsFullDisbursementResponseShape() throws Exception {
    var id = createOfficeDisbursement("One-shot counsel fee");

    mockMvc
        .perform(
            get("/api/legal/disbursements/" + id)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.category").value("COUNSEL_FEES"))
        .andExpect(jsonPath("$.description").value("One-shot counsel fee"))
        .andExpect(jsonPath("$.amount").value(250.00))
        .andExpect(jsonPath("$.vatTreatment").value("STANDARD_15"))
        .andExpect(jsonPath("$.vatAmount").value(37.50))
        .andExpect(jsonPath("$.paymentSource").value("OFFICE_ACCOUNT"))
        .andExpect(jsonPath("$.trustTransactionId").doesNotExist())
        .andExpect(jsonPath("$.incurredDate").value("2026-04-01"))
        .andExpect(jsonPath("$.supplierName").value("Supplier Co"))
        .andExpect(jsonPath("$.approvalStatus").value("DRAFT"))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"))
        .andExpect(jsonPath("$.createdBy").value(legalOwnerMemberId.toString()))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());
  }

  // ==========================================================================
  // Validation / state errors
  // ==========================================================================

  @Test
  void legalTenant_PATCH_onApprovedRow_returns400_withInvalidStatePayload() throws Exception {
    var id = createOfficeDisbursement("To be approved then patched");

    mockMvc
        .perform(
            post("/api/legal/disbursements/" + id + "/submit")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + id + "/approve")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"ok\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/legal/disbursements/" + id)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"should not be allowed\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot update disbursement"));
  }

  @Test
  void legalTenant_POST_writeOff_blankReason_returns400_validationFailure() throws Exception {
    var id = createOfficeDisbursement("To be written off");

    mockMvc
        .perform(
            post("/api/legal/disbursements/" + id + "/write-off")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"));
  }

  // ==========================================================================
  // List filters
  // ==========================================================================

  @Test
  void legalTenant_GET_listWithFilters_narrowsResults() throws Exception {
    // Use otherProjectId for this test only to fully isolate from other tests that seed projectId.
    var a = createOfficeDisbursement("Filter-A-unique-marker", otherProjectId, "SHERIFF_FEES");
    var b = createOfficeDisbursement("Filter-B-unique-marker", otherProjectId, "COUNSEL_FEES");
    var c = createOfficeDisbursement("Filter-C-unique-marker", otherProjectId, "COURT_FEES");

    // Approve `a` so approvalStatus=APPROVED filter isolates it.
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + a + "/submit")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/legal/disbursements/" + a + "/approve")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"ok\"}"))
        .andExpect(status().isOk());

    // Filter by otherProjectId — exactly 3 rows (all seeded above).
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .param("projectId", otherProjectId.toString())
                .param("size", "50")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(3)));

    // Filter by approvalStatus=APPROVED within otherProjectId — only `a`.
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .param("projectId", otherProjectId.toString())
                .param("approvalStatus", "APPROVED")
                .param("size", "50")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(a));

    // Filter by billingStatus=UNBILLED + otherProjectId — all 3 match (approved is still UNBILLED).
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .param("projectId", otherProjectId.toString())
                .param("billingStatus", "UNBILLED")
                .param("size", "50")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(3)));

    // Filter by category=COUNSEL_FEES + otherProjectId — only `b`.
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .param("projectId", otherProjectId.toString())
                .param("category", "COUNSEL_FEES")
                .param("size", "50")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(b));

    // Filter by category=COURT_FEES + otherProjectId — only `c`.
    mockMvc
        .perform(
            get("/api/legal/disbursements")
                .param("projectId", otherProjectId.toString())
                .param("category", "COURT_FEES")
                .param("size", "50")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(c));
  }

  // ==========================================================================
  // Receipt multipart
  // ==========================================================================

  @Test
  void legalTenant_POST_receiptMultipart_persistsDocumentId_andLinksOnRow() throws Exception {
    var id = createOfficeDisbursement("Needs a receipt");

    var file =
        new MockMultipartFile(
            "file", "receipt.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

    var uploadResult =
        mockMvc
            .perform(
                multipart("/api/legal/disbursements/" + id + "/receipt")
                    .file(file)
                    .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.receiptDocumentId").isNotEmpty())
            .andReturn();

    var documentId =
        JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.receiptDocumentId")
            .toString();

    // Re-read via GET — link must persist.
    mockMvc
        .perform(
            get("/api/legal/disbursements/" + id)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receiptDocumentId").value(documentId));
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private String createOfficeDisbursement(String description) throws Exception {
    return createOfficeDisbursement(description, projectId, "COUNSEL_FEES");
  }

  private String createOfficeDisbursement(String description, UUID pid, String category)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/legal/disbursements")
                    .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(pid, description, "250.00", category)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createBody(UUID pid, String description, String amount, String category) {
    return """
        {
          "projectId": "%s",
          "customerId": "%s",
          "category": "%s",
          "description": "%s",
          "amount": %s,
          "paymentSource": "OFFICE_ACCOUNT",
          "incurredDate": "2026-04-01",
          "supplierName": "Supplier Co",
          "supplierReference": "REF-001"
        }
        """
        .formatted(pid, customerId, category, description, amount);
  }

  private void runInLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
