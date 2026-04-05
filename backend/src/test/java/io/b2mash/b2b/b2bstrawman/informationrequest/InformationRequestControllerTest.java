package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InformationRequestControllerTest {
  private static final String ORG_ID = "org_inforeq_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String customerId;
  private String portalContactId;
  private String templateId;
  private String tenantSchema;
  private UUID memberIdOwnerUuid;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "InfoReq Controller Test Org", null);
    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_inforeq_owner",
            "inforeq_owner@test.com",
            "InfoReq Owner",
            "owner");
    memberIdOwnerUuid = UUID.fromString(memberIdOwner);
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_inforeq_admin",
            "inforeq_admin@test.com",
            "InfoReq Admin",
            "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_inforeq_member",
            "inforeq_member@test.com",
            "InfoReq Member",
            "member");
    customerId = createCustomer(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"));
    portalContactId = createPortalContact(customerId, "contact@test.com", "Test Contact");
    templateId = createRequestTemplate();

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inforeq_315a_custom",
                "inforeq_custom@test.com",
                "InfoReq Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inforeq_315a_nocap",
                "inforeq_nocap@test.com",
                "InfoReq NoCap User",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwnerUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "InfoReq Manager",
                          "Can manage information requests",
                          Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead IR", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // ========== Create Tests ==========

  @Test
  void shouldCreateFromTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requestTemplateId": "%s",
                      "customerId": "%s",
                      "portalContactId": "%s",
                      "reminderIntervalDays": 7
                    }
                    """
                        .formatted(templateId, customerId, portalContactId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.requestNumber").exists())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.customerName").value("InfoReq Test Customer"))
        .andExpect(jsonPath("$.portalContactName").value("Test Contact"))
        .andExpect(jsonPath("$.portalContactEmail").value("contact@test.com"))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].name").value("Bank Statements"))
        .andExpect(jsonPath("$.reminderIntervalDays").value(7));
  }

  @Test
  void shouldCreateAdHoc() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "portalContactId": "%s",
                      "items": [
                        {"name": "ID Document", "description": "Certified copy", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                        {"name": "Address", "description": "Current address", "responseType": "TEXT_RESPONSE", "required": false, "sortOrder": 1}
                      ]
                    }
                    """
                        .formatted(customerId, portalContactId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestNumber").exists())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].name").value("ID Document"))
        .andExpect(jsonPath("$.requestTemplateId").isEmpty());
  }

  // ========== Lifecycle Tests ==========

  @Test
  void shouldSendRequest() throws Exception {
    String requestId = createInfoRequest();
    mockMvc
        .perform(
            post("/api/information-requests/{id}/send", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").exists());
  }

  @Test
  void shouldCancelDraftRequest() throws Exception {
    String requestId = createInfoRequest();
    mockMvc
        .perform(
            post("/api/information-requests/{id}/cancel", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void shouldCancelSentRequest() throws Exception {
    String requestId = createInfoRequest();
    // Send first
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));
    // Then cancel
    mockMvc
        .perform(
            post("/api/information-requests/{id}/cancel", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void shouldNotCancelCompletedRequest() throws Exception {
    String requestId = createInfoRequestAndComplete();
    mockMvc
        .perform(
            post("/api/information-requests/{id}/cancel", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldNotSendEmptyRequest() throws Exception {
    // Create ad-hoc with no items
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId": "%s", "portalContactId": "%s"}
                        """
                            .formatted(customerId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String emptyRequestId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            post("/api/information-requests/{id}/send", emptyRequestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isBadRequest());
  }

  // ========== Item Review Tests ==========

  @Test
  void shouldAcceptSubmittedItem() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    // Get items
    var items = getItemIds(requestId);
    String itemId = items.get(0);

    // Simulate submission
    simulateItemSubmission(requestId, itemId);

    mockMvc
        .perform(
            post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id=='%s')].status".formatted(itemId)).value("ACCEPTED"));
  }

  @Test
  void shouldRejectSubmittedItem() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    var items = getItemIds(requestId);
    String itemId = items.get(0);
    simulateItemSubmission(requestId, itemId);

    mockMvc
        .perform(
            post("/api/information-requests/{id}/items/{itemId}/reject", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Incorrect document, please resubmit"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id=='%s')].status".formatted(itemId)).value("REJECTED"))
        .andExpect(
            jsonPath("$.items[?(@.id=='%s')].rejectionReason".formatted(itemId))
                .value("Incorrect document, please resubmit"));
  }

  @Test
  void shouldNotAcceptNonSubmittedItem() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    var items = getItemIds(requestId);
    String itemId = items.get(0); // Still PENDING

    mockMvc
        .perform(
            post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectRequireReason() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    var items = getItemIds(requestId);
    String itemId = items.get(0);
    simulateItemSubmission(requestId, itemId);

    mockMvc
        .perform(
            post("/api/information-requests/{id}/items/{itemId}/reject", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldAutoCompleteWhenAllRequiredAccepted() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    var items = getItemIds(requestId);
    // Template has 2 required items (index 0, 1) and 1 optional (index 2)
    // Get required item IDs from the response
    var getResult =
        mockMvc
            .perform(
                get("/api/information-requests/{id}", requestId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
            .andReturn();
    String responseBody = getResult.getResponse().getContentAsString();
    List<Map<String, Object>> itemsList = JsonPath.read(responseBody, "$.items");

    // Accept all required items
    for (var item : itemsList) {
      if ((boolean) item.get("required")) {
        String itemId = item.get("id").toString();
        simulateItemSubmission(requestId, itemId);
        mockMvc.perform(
            post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));
      }
    }

    // Verify auto-completed
    mockMvc
        .perform(
            get("/api/information-requests/{id}", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // ========== Numbering Tests ==========

  @Test
  void shouldGenerateSequentialNumbers() throws Exception {
    var result1 =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                        """
                            .formatted(templateId, customerId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String num1 =
        JsonPath.read(result1.getResponse().getContentAsString(), "$.requestNumber").toString();

    var result2 =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                        """
                            .formatted(templateId, customerId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String num2 =
        JsonPath.read(result2.getResponse().getContentAsString(), "$.requestNumber").toString();

    // Both should match REQ-NNNN pattern, and num2 > num1
    assertTrue(num1.startsWith("REQ-"), "Expected REQ- prefix, got: " + num1);
    assertTrue(num2.startsWith("REQ-"), "Expected REQ- prefix, got: " + num2);
    int n1 = Integer.parseInt(num1.substring(4));
    int n2 = Integer.parseInt(num2.substring(4));
    assertTrue(n2 > n1, "Expected sequential numbering: " + num1 + " then " + num2);
  }

  // ========== Update & Add Item Tests ==========

  @Test
  void shouldUpdateDraftRequest() throws Exception {
    String requestId = createInfoRequest();
    mockMvc
        .perform(
            put("/api/information-requests/{id}", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reminderIntervalDays": 14}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reminderIntervalDays").value(14));
  }

  @Test
  void shouldAddAdHocItem() throws Exception {
    String requestId = createInfoRequest();
    mockMvc
        .perform(
            post("/api/information-requests/{id}/items", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Extra Doc", "description": "Additional document", "responseType": "FILE_UPLOAD", "required": false, "sortOrder": 10}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(4));
  }

  // ========== Query Tests ==========

  @Test
  void shouldGetRequestWithItems() throws Exception {
    String requestId = createInfoRequest();
    mockMvc
        .perform(
            get("/api/information-requests/{id}", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(requestId))
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.customerName").exists())
        .andExpect(jsonPath("$.portalContactName").exists());
  }

  @Test
  void shouldListWithStatusFilter() throws Exception {
    // Create and send a request
    String sentRequestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", sentRequestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    mockMvc
        .perform(
            get("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .param("status", "SENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[?(@.status=='SENT')]").isNotEmpty());
  }

  @Test
  void shouldListWithCustomerFilter() throws Exception {
    mockMvc
        .perform(
            get("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .param("customerId", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldListWithProjectFilter() throws Exception {
    // Create a project first
    String projectId = createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"));

    // Create request with project
    mockMvc.perform(
        post("/api/information-requests")
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"requestTemplateId": "%s", "customerId": "%s", "projectId": "%s", "portalContactId": "%s"}
                """
                    .formatted(templateId, customerId, projectId, portalContactId)));

    mockMvc
        .perform(
            get("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .param("projectId", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].projectId").value(projectId));
  }

  // ========== Convenience Endpoints ==========

  @Test
  void shouldListByCustomer() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/{customerId}/information-requests", customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldListByProject() throws Exception {
    String projectId = createProject(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"));
    mockMvc.perform(
        post("/api/information-requests")
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"requestTemplateId": "%s", "customerId": "%s", "projectId": "%s", "portalContactId": "%s"}
                """
                    .formatted(templateId, customerId, projectId, portalContactId)));

    mockMvc
        .perform(
            get("/api/projects/{projectId}/information-requests", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].projectId").value(projectId));
  }

  // ========== Dashboard Summary ==========

  @Test
  void shouldGetDashboardSummary() throws Exception {
    mockMvc
        .perform(
            get("/api/information-requests/summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.byStatus").isMap())
        .andExpect(jsonPath("$.byStatus.DRAFT").isNumber())
        .andExpect(jsonPath("$.itemsPendingReview").isNumber())
        .andExpect(jsonPath("$.overdueRequests").isNumber())
        .andExpect(jsonPath("$.completionRateLast30Days").isNumber());
  }

  // ========== Validation Tests ==========

  @Test
  void shouldRejectInvalidCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, UUID.randomUUID(), portalContactId)))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectInvalidPortalContact() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectPortalContactFromDifferentCustomer() throws Exception {
    // Create another customer and contact
    String otherCustomerId = createCustomer2(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"));
    String otherContactId = createPortalContact(otherCustomerId, "other@test.com", "Other Contact");

    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, otherContactId)))
        .andExpect(status().isBadRequest());
  }

  // ========== RBAC Tests ==========

  @Test
  void shouldAllowMemberToList() throws Exception {
    mockMvc
        .perform(
            get("/api/information-requests")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_inforeq_member")))
        .andExpect(status().isOk());
  }

  @Test
  void shouldForbidMemberFromCreating() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_inforeq_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, portalContactId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdminToCreate() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inforeq_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, portalContactId)))
        .andExpect(status().isCreated());
  }

  // ========== Resend Notification ==========

  @Test
  void shouldResendNotification() throws Exception {
    String requestId = createInfoRequest();
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    mockMvc
        .perform(
            post("/api/information-requests/{id}/resend-notification", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  // ========== Helpers ==========

  private String createInfoRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s", "reminderIntervalDays": 5}
                        """
                            .formatted(templateId, customerId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createInfoRequestAndComplete() throws Exception {
    String requestId = createInfoRequest();
    // Send
    mockMvc.perform(
        post("/api/information-requests/{id}/send", requestId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));

    // Get items and accept all required
    var getResult =
        mockMvc
            .perform(
                get("/api/information-requests/{id}", requestId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
            .andReturn();
    String responseBody = getResult.getResponse().getContentAsString();
    List<Map<String, Object>> itemsList = JsonPath.read(responseBody, "$.items");

    for (var item : itemsList) {
      if ((boolean) item.get("required")) {
        String itemId = item.get("id").toString();
        simulateItemSubmission(requestId, itemId);
        mockMvc.perform(
            post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")));
      }
    }
    return requestId;
  }

  private List<String> getItemIds(String requestId) throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/information-requests/{id}", requestId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner")))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(body, "$.items[*].id");
    return ids;
  }

  private void simulateItemSubmission(String requestId, String itemId) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".request_items SET status = 'SUBMITTED', submitted_at = now(), document_id = ?::uuid WHERE id = ?::uuid"
            .formatted(schema),
        UUID.randomUUID().toString(),
        itemId);
  }

  private String createCustomer(JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "InfoReq Test Customer", "email": "inforeq-customer@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createCustomer2(JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Other Customer", "email": "other-customer@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createPortalContact(String customerId, String email, String displayName) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    String contactId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
            .formatted(schema),
        contactId,
        ORG_ID,
        customerId,
        email,
        displayName);
    return contactId;
  }

  private String createRequestTemplate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/request-templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inforeq_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test Template",
                          "description": "For testing",
                          "items": [
                            {"name": "Bank Statements", "description": "Jan-Dec", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                            {"name": "Tax Number", "description": "Company tax number", "responseType": "TEXT_RESPONSE", "required": true, "sortOrder": 1},
                            {"name": "Optional Doc", "description": "Nice to have", "responseType": "FILE_UPLOAD", "required": false, "sortOrder": 2}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createProject(JwtRequestPostProcessor jwt) throws Exception {
    // Projects require an ACTIVE customer; create and activate one
    String activeCustomerId = createActiveCustomerForProject(jwt);
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "InfoReq Test Project", "customerId": "%s"}
                        """
                            .formatted(activeCustomerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private static int projectCustomerCounter = 0;

  private String createActiveCustomerForProject(JwtRequestPostProcessor jwt) throws Exception {
    int counter = ++projectCustomerCounter;
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Project Customer %d", "email": "proj-cust-%d@test.com", "type": "INDIVIDUAL"}
                        """
                            .formatted(counter, counter)))
            .andExpect(status().isCreated())
            .andReturn();
    String activeCustomerId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    // Transition PROSPECT -> ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + activeCustomerId + "/transition")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Complete all checklist items (auto-transitions ONBOARDING -> ACTIVE)
    TestChecklistHelper.completeChecklistItems(mockMvc, activeCustomerId, jwt);
    return activeCustomerId;
  } // --- Capability Tests (added in Epic 315A) ---

  @Test
  void customRoleWithCapability_accessesInfoReqEndpoint_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_inforeq_315a_custom"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, portalContactId)))
        .andExpect(status().isCreated());
  }

  @Test
  void customRoleWithoutCapability_accessesInfoReqEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_inforeq_315a_nocap"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestTemplateId": "%s", "customerId": "%s", "portalContactId": "%s"}
                    """
                        .formatted(templateId, customerId, portalContactId)))
        .andExpect(status().isForbidden());
  }
}
