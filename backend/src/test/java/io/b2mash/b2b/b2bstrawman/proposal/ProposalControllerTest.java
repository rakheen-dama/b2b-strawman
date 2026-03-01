package io.b2mash.b2b.b2bstrawman.proposal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proposal_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String customerId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember("user_prop_ctrl_owner", "prop_ctrl_owner@test.com", "PROP Owner", "owner");
    memberIdAdmin =
        syncMember("user_prop_ctrl_admin", "prop_ctrl_admin@test.com", "PROP Admin", "admin");
    memberIdMember =
        syncMember("user_prop_ctrl_member", "prop_ctrl_member@test.com", "PROP Member", "member");

    customerId = createCustomer(ownerJwt());
  }

  // ==================== CRUD Happy Paths ====================

  @Test
  void shouldCreateProposal() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(adminJwt())
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
                .with(ownerJwt())
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
        .perform(get("/api/proposals").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldGetProposalById() throws Exception {
    String proposalId = createProposal("Get Test Proposal", "HOURLY", null, null);

    mockMvc
        .perform(get("/api/proposals/" + proposalId).with(memberJwt()))
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
                .with(adminJwt())
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
        .perform(delete("/api/proposals/" + proposalId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/proposals/" + proposalId).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldListProposalsWithStatusFilter() throws Exception {
    createProposal("Filtered Proposal", "HOURLY", null, null);

    mockMvc
        .perform(get("/api/proposals").with(memberJwt()).param("status", "DRAFT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ==================== Validation & Guards ====================

  @Test
  void shouldRejectCreateWithMissingTitle() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(adminJwt())
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
                .with(adminJwt())
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
                .with(adminJwt())
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
        .perform(get("/api/proposals/" + UUID.randomUUID()).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectFixedFeeWithoutAmount() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(adminJwt())
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
        .perform(delete("/api/proposals/" + proposalId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectMemberFromCreating() throws Exception {
    mockMvc
        .perform(
            post("/api/proposals")
                .with(memberJwt())
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
        .perform(delete("/api/proposals/" + proposalId).with(adminJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateProposal_sentStatus_returns409() throws Exception {
    String proposalId = createProposal("Update Sent Guard", "HOURLY", null, null);
    transitionToSent(proposalId);

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId)
                .with(ownerJwt())
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
        .perform(delete("/api/proposals/" + proposalId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // ==================== Milestones ====================

  @Test
  void shouldReplaceMilestonesOnFixedProposal() throws Exception {
    String proposalId = createProposal("Milestone Proposal", "FIXED", "50000.00", "ZAR");

    mockMvc
        .perform(
            put("/api/proposals/" + proposalId + "/milestones")
                .with(adminJwt())
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
                .with(adminJwt())
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
                .with(adminJwt())
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
                .with(adminJwt())
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
        .perform(get("/api/proposals/stats").with(adminJwt()))
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
        .perform(get("/api/proposals/stats").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // ==================== Customer-scoped ====================

  @Test
  void shouldListProposalsForCustomer() throws Exception {
    // Create a proposal for our customer
    createProposal("Customer Scoped Proposal", "HOURLY", null, null);

    mockMvc
        .perform(get("/api/customers/" + customerId + "/proposals").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.toString()))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
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

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    assert location != null : "Expected Location header to be present";
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private void transitionToSent(String proposalId) {
    String schema =
        io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET status = 'SENT', sent_at = now() WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_prop_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_prop_ctrl_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_prop_ctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
