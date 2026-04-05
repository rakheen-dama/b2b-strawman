package io.b2mash.b2b.b2bstrawman.proposal;

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
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalControllerTest {
  private static final String ORG_ID = "org_proposal_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String customerId;
  private String tenantSchema;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Controller Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_prop_ctrl_owner",
            "prop_ctrl_owner@test.com",
            "PROP Owner",
            "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_prop_ctrl_admin",
            "prop_ctrl_admin@test.com",
            "PROP Admin",
            "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_prop_ctrl_member",
            "prop_ctrl_member@test.com",
            "PROP Member",
            "member");

    customerId = createCustomer(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner role to owner member for capability-based auth
    UUID ownerMemberUuid = UUID.fromString(memberIdOwner);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberUuid).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_prop_314b_custom",
                "prop_custom@test.com",
                "Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_prop_314b_nocap",
                "prop_nocap@test.com",
                "NoCap User",
                "member"));

    // Assign OrgRoles within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Invoicer", "Can invoice", Set.of("INVOICING")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead", "No invoicing cap", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // ==================== CRUD Happy Paths ====================

  @Test
  void shouldCreateProposal() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "New Engagement Proposal",
                      "customerId": "%s",
                      "feeModel": "HOURLY"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.proposalNumber").exists())
        .andExpect(jsonPath("$.title").value("New Engagement Proposal"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.feeModel").value("HOURLY"))
        .andExpect(jsonPath("$.customerId").value(customerId));
  }

  @Test
  void shouldCreateFixedFeeProposal() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Fixed Fee Audit",
                      "customerId": "%s",
                      "feeModel": "FIXED",
                      "fixedFeeAmount": 25000.00,
                      "fixedFeeCurrency": "ZAR"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeModel").value("FIXED"))
        .andExpect(jsonPath("$.fixedFeeAmount").value(25000.00));
  }

  @Test
  void shouldListProposals() throws Exception {
    // Create a proposal first
    createProposal("List Test Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            get("/api/proposals").with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldGetProposalById() throws Exception {
    String proposalId = createProposal("Get Test Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            get("/api/proposals/" + proposalId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(proposalId))
        .andExpect(jsonPath("$.title").value("Get Test Proposal"));
  }

  @Test
  void shouldUpdateProposal() throws Exception {
    String proposalId = createProposal("Original Title", "HOURLY", null, null);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Updated Title",
                      "hourlyRateNote": "Standard rates apply"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Title"))
        .andExpect(jsonPath("$.hourlyRateNote").value("Standard rates apply"));
  }

  @Test
  void shouldDeleteProposal() throws Exception {
    String proposalId = createProposal("To Be Deleted", "HOURLY", null, null);

    mockMvc
        .perform(
            delete("/api/proposals/" + proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner")))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(
            get("/api/proposals/" + proposalId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldListProposalsWithStatusFilter() throws Exception {
    createProposal("Filtered Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            get("/api/proposals")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member"))
                .param("status", "DRAFT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ==================== Validation & Guards ====================

  @Test
  void shouldRejectCreateWithMissingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "feeModel": "HOURLY"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCreateWithMissingCustomerId() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "No Customer",
                      "feeModel": "HOURLY"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectCreateWithMissingFeeModel() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "No Fee Model",
                      "customerId": "%s"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404ForNonexistentProposal() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/" + UUID.randomUUID())
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectFixedFeeWithoutAmount() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Bad Fixed Fee",
                      "customerId": "%s",
                      "feeModel": "FIXED"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectMemberFromDeleting() throws Exception {
    String proposalId = createProposal("Member Cannot Delete", "HOURLY", null, null);

    mockMvc
        .perform(
            delete("/api/proposals/" + proposalId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectMemberFromCreating() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Member Cannot Create",
                      "customerId": "%s",
                      "feeModel": "HOURLY"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectAdminFromDeleting() throws Exception {
    String proposalId = createProposal("Admin Cannot Delete", "HOURLY", null, null);

    mockMvc
        .perform(
            delete("/api/proposals/" + proposalId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin")))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateProposal_sentStatus_returns409() throws Exception {
    String proposalId = createProposal("Update Sent Guard", "HOURLY", null, null);
    transitionToSent(proposalId);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Should Not Work"
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteProposal_sentStatus_returns409() throws Exception {
    String proposalId = createProposal("Delete Sent Guard", "HOURLY", null, null);
    transitionToSent(proposalId);

    mockMvc
        .perform(
            delete("/api/proposals/" + proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner")))
        .andExpect(status().isConflict());
  }

  // ==================== Milestones ====================

  @Test
  void shouldReplaceMilestonesOnFixedProposal() throws Exception {
    String proposalId = createProposal("Milestone Proposal", "FIXED", "50000.00", "ZAR");

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId + "/milestones")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {"description": "Phase 1 - Discovery", "percentage": 30.00, "relativeDueDays": 30},
                      {"description": "Phase 2 - Implementation", "percentage": 50.00, "relativeDueDays": 60},
                      {"description": "Phase 3 - Handover", "percentage": 20.00, "relativeDueDays": 90}
                    ]
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].description").value("Phase 1 - Discovery"))
        .andExpect(jsonPath("$[0].percentage").value(30.00))
        .andExpect(jsonPath("$[1].description").value("Phase 2 - Implementation"))
        .andExpect(jsonPath("$[2].description").value("Phase 3 - Handover"));
  }

  @Test
  void shouldRejectMilestonesOnHourlyProposal() throws Exception {
    String proposalId = createProposal("Hourly No Milestones", "HOURLY", null, null);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId + "/milestones")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {"description": "Phase 1", "percentage": 100.00, "relativeDueDays": 30}
                    ]
                    """))
        .andExpect(status().isBadRequest());
  }

  // ==================== Team Members ====================

  @Test
  void shouldReplaceTeamMembers() throws Exception {
    String proposalId = createProposal("Team Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId + "/team")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {"memberId": "%s", "role": "Lead Consultant"},
                      {"memberId": "%s", "role": "Analyst"}
                    ]
                    """
                        .formatted(memberIdOwner, memberIdAdmin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].role").value("Lead Consultant"))
        .andExpect(jsonPath("$[1].role").value("Analyst"));
  }

  @Test
  void shouldRejectDuplicateTeamMembers() throws Exception {
    String proposalId = createProposal("Duplicate Team Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId + "/team")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {"memberId": "%s", "role": "Lead"},
                      {"memberId": "%s", "role": "Also Lead"}
                    ]
                    """
                        .formatted(memberIdOwner, memberIdOwner)))
        .andExpect(status().isBadRequest());
  }

  // ==================== Stats ====================

  @Test
  void shouldReturnPipelineStats() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/stats")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_prop_ctrl_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDraft").isNumber())
        .andExpect(jsonPath("$.totalSent").isNumber())
        .andExpect(jsonPath("$.totalAccepted").isNumber())
        .andExpect(jsonPath("$.totalDeclined").isNumber())
        .andExpect(jsonPath("$.totalExpired").isNumber())
        .andExpect(jsonPath("$.conversionRate").isNumber())
        .andExpect(jsonPath("$.averageDaysToAccept").isNumber());
  }

  @Test
  void shouldRejectMemberFromStats() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/stats")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isForbidden());
  }

  // ==================== Customer-scoped ====================

  @Test
  void shouldListProposalsForCustomer() throws Exception {
    // Create a proposal for our customer
    createProposal("Customer Scoped Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/proposals")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_ctrl_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  // ==================== Capability Tests ====================

  @Test
  void customRoleWithCapability_accessesProposalEndpoint_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/stats")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_314b_custom")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesProposalEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/proposals/stats")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_314b_nocap")))
        .andExpect(status().isForbidden());
  }

  // ==================== Helpers ====================

  private String createProposal(
      String title, String feeModel, String fixedFeeAmount, String fixedFeeCurrency)
      throws Exception {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"title\": \"").append(title).append("\",");
    json.append("\"customerId\": \"").append(customerId).append("\",");
    json.append("\"feeModel\": \"").append(feeModel).append("\"");
    if (fixedFeeAmount != null) {
      json.append(", \"fixedFeeAmount\": ").append(fixedFeeAmount);
    }
    if (fixedFeeCurrency != null) {
      json.append(", \"fixedFeeCurrency\": \"").append(fixedFeeCurrency).append("\"");
    }
    json.append("}");

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.toString()))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
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
                        {"name": "Proposal Test Customer", "email": "proposal-customer@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private void transitionToSent(String proposalId) {
    String schema =
        io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'SENT', sent_at = now() WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);
  }
}
