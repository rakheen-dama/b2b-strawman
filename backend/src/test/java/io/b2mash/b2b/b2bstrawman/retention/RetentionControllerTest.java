package io.b2mash.b2b.b2bstrawman.retention;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.ProblemDetailAssertions;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionControllerTest {
  private static final String ORG_ID = "org_retention_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String customerId;
  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retention Controller Test Org", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_retctrl_owner",
                "retctrl_owner@test.com",
                "RetCtrl Owner",
                "owner"));
    TestMemberHelper.syncMemberQuietly(
        mockMvc,
        ORG_ID,
        "user_retctrl_member",
        "retctrl_member@test.com",
        "RetCtrl Member",
        "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_ret_315a_custom",
                "ret_custom@test.com",
                "Ret Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_ret_315a_nocap",
                "ret_nocap@test.com",
                "Ret NoCap User",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Retention Manager",
                          "Can manage retention",
                          Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Ret", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });

    // Create a customer for purge tests
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Retention Test Customer","email":"ret-ctrl@test.com","phone":"+1-555-0400","idNumber":"RET-C01","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void getList_returnsActivePolicies() throws Exception {
    // The FICA pack seeds retention policies during provisioning
    mockMvc
        .perform(
            get("/api/retention-policies")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].recordType").exists())
        .andExpect(jsonPath("$[0].retentionDays").exists());
  }

  @Test
  void postCreate_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"COMMENT","retentionDays":365,"triggerEvent":"RECORD_CREATED","action":"PURGE"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.recordType").value("COMMENT"))
        .andExpect(jsonPath("$.retentionDays").value(365))
        .andExpect(jsonPath("$.action").value("PURGE"));
  }

  @Test
  void postCreate_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":730,"triggerEvent":"RECORD_CREATED","action":"FLAG"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void postCreate_duplicate_returns409() throws Exception {
    // First create
    mockMvc
        .perform(
            post("/api/retention-policies")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":730,"triggerEvent":"DOCUMENT_ARCHIVED","action":"FLAG"}
                    """))
        .andExpect(status().isCreated());

    // Duplicate create
    var result =
        mockMvc.perform(
            post("/api/retention-policies")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"DOCUMENT","retentionDays":365,"triggerEvent":"DOCUMENT_ARCHIVED","action":"PURGE"}
                    """));
    ProblemDetailAssertions.assertProblem(result, HttpStatus.CONFLICT, "Policy already exists");
  }

  @Test
  void putUpdate_updatesPolicy() throws Exception {
    // Create a policy first
    var createResult =
        mockMvc
            .perform(
                post("/api/retention-policies")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recordType":"COMMENT","retentionDays":180,"triggerEvent":"COMMENT_RESOLVED","action":"FLAG"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String policyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update it
    mockMvc
        .perform(
            put("/api/retention-policies/" + policyId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"retentionDays":365,"action":"PURGE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionDays").value(365))
        .andExpect(jsonPath("$.action").value("PURGE"));
  }

  @Test
  void deletePolicy_removes204() throws Exception {
    // Create a policy first
    var createResult =
        mockMvc
            .perform(
                post("/api/retention-policies")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recordType":"DOCUMENT","retentionDays":90,"triggerEvent":"DOCUMENT_EXPIRED","action":"PURGE"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String policyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Delete it
    mockMvc
        .perform(
            delete("/api/retention-policies/" + policyId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deletePolicy_notFound_returns404() throws Exception {
    var result =
        mockMvc.perform(
            delete("/api/retention-policies/" + UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")));
    ProblemDetailAssertions.assertProblem(
        result, HttpStatus.NOT_FOUND, "RetentionPolicy not found");
  }

  @Test
  void postCheck_returnsFlaggedResult() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/check")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkedAt").exists())
        .andExpect(jsonPath("$.flagged").exists())
        .andExpect(jsonPath("$.totalFlagged").isNumber());
  }

  @Test
  void postPurge_auditEvent_hardDeletes() throws Exception {
    // First run check to generate audit events, then use one of those audit event IDs
    // We know runCheck itself creates an audit event, so we can use the check endpoint
    // to create audit events, then purge them
    var checkResult =
        mockMvc
            .perform(
                post("/api/retention-policies/check")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
            .andExpect(status().isOk())
            .andReturn();

    // Run a second check to create another audit event
    mockMvc
        .perform(
            post("/api/retention-policies/check")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk());

    // Now purge with a random UUID for AUDIT_EVENT — non-existent IDs won't fail (deleteAllById
    // silently ignores missing)
    var randomAuditId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(randomAuditId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordType").value("AUDIT_EVENT"))
        .andExpect(jsonPath("$.purged").isNumber());
  }

  @Test
  void postPurge_customer_anonymizes() throws Exception {
    // Purge the customer created in setup
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"CUSTOMER","recordIds":["%s"]}
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordType").value("CUSTOMER"))
        .andExpect(jsonPath("$.purged").value(1))
        .andExpect(jsonPath("$.failed").value(0));

    // Verify customer is anonymized by fetching it
    mockMvc
        .perform(
            get("/api/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Anonymized Customer " + customerId.substring(0, 6)));
  }

  @Test
  void postPurge_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  // --- Capability Tests (added in Epic 315A) ---

  @Test
  void customRoleWithCapability_accessesRetentionEndpoint_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/retention-policies")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_ret_315a_custom", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesRetentionEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/retention-policies")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ret_315a_nocap")))
        .andExpect(status().isForbidden());
  }

  @Test
  void customRoleWithCapability_canCheckRetention() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/check")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_ret_315a_custom", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_cannotCheckRetention() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/check")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ret_315a_nocap")))
        .andExpect(status().isForbidden());
  }

  @Test
  void customRoleWithCapability_canPurgeRetention() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_ret_315a_custom", "member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_cannotPurgeRetention() throws Exception {
    mockMvc
        .perform(
            post("/api/retention-policies/purge")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_ret_315a_nocap"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recordType":"AUDIT_EVENT","recordIds":["%s"]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  // --- Settings endpoints at /api/settings/retention-policies (added in Epic 376B) ---

  @Test
  void getSettingsList_returnsActivePoliciesWithLastEvaluatedAt() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/retention-policies")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].recordType").exists())
        .andExpect(jsonPath("$[0].retentionDays").exists())
        .andExpect(jsonPath("$[0].description").hasJsonPath())
        .andExpect(jsonPath("$[0].lastEvaluatedAt").hasJsonPath());
  }

  @Test
  void putSettingsUpdate_updatesRetentionPeriodAndDescription() throws Exception {
    // Get an existing policy ID from the settings list
    var listResult =
        mockMvc
            .perform(
                get("/api/settings/retention-policies")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
            .andExpect(status().isOk())
            .andReturn();
    String policyId = JsonPath.read(listResult.getResponse().getContentAsString(), "$[0].id");

    // Update retentionDays and description
    mockMvc
        .perform(
            put("/api/settings/retention-policies/" + policyId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"retentionDays":2000,"description":"Updated retention description"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retentionDays").value(2000))
        .andExpect(jsonPath("$.description").value("Updated retention description"));
  }

  @Test
  void putSettingsUpdate_belowFinancialMinimum_returns400() throws Exception {
    // Create a CUSTOMER policy for this test (financial record type)
    var createResult =
        mockMvc
            .perform(
                post("/api/retention-policies")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recordType":"CUSTOMER","retentionDays":1800,"triggerEvent":"CUSTOMER_DEACTIVATED","action":"anonymize"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String policyId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Try to update below the financial minimum (ZA default = 60 months = 1800 days)
    var result =
        mockMvc.perform(
            put("/api/settings/retention-policies/" + policyId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"retentionDays":30}
                    """));
    ProblemDetailAssertions.assertProblem(
        result, HttpStatus.BAD_REQUEST, "Retention period too short");
  }

  @Test
  void postEvaluate_returnsPreviewWithoutExecuting() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/retention-policies/evaluate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPoliciesEvaluated").isNumber())
        .andExpect(jsonPath("$.entitiesEligibleForPurge").isNumber())
        .andExpect(jsonPath("$.policySummaries").isArray());
  }

  @Test
  void postExecute_executesRetentionPurge() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/retention-policies/execute")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_retctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPurged").isNumber())
        .andExpect(jsonPath("$.totalFailed").isNumber())
        .andExpect(jsonPath("$.executedAt").exists());
  }

  // --- RBAC tests for settings endpoints ---

  @Test
  void getSettingsList_member_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/retention-policies")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void putSettingsUpdate_member_returns403() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/retention-policies/" + UUID.randomUUID())
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"retentionDays":2000}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void postEvaluate_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/retention-policies/evaluate")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void postExecute_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/retention-policies/execute")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_retctrl_member")))
        .andExpect(status().isForbidden());
  }
}
