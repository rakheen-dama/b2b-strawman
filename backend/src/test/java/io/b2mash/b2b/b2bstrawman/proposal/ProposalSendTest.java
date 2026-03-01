package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
class ProposalSendTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proposal_send_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String memberIdOwner;
  private String customerId;
  private String secondCustomerId;
  private UUID portalContactId;
  private UUID secondCustomerContactId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Send Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember("user_prop_send_owner", "prop_send_owner@test.com", "SEND Owner", "owner");
    syncMember("user_prop_send_admin", "prop_send_admin@test.com", "SEND Admin", "admin");
    syncMember("user_prop_send_member", "prop_send_member@test.com", "SEND Member", "member");

    customerId = createCustomer(ownerJwt(), "Send Test Customer", "send-customer@test.com");
    portalContactId = createPortalContact(customerId, "contact@send-test.com", "Send Contact");

    secondCustomerId = createCustomer(ownerJwt(), "Second Customer", "second-customer@test.com");
    secondCustomerContactId =
        createPortalContact(secondCustomerId, "contact2@send-test.com", "Second Contact");
  }

  // --- 232.10: Happy path tests ---

  @Test
  void sendProposal_draftWithValidContent_returnsOkAndSent() throws Exception {
    String proposalId = createProposalWithContent("Send Test Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
  }

  @Test
  void sendProposal_fixedFeeWithMilestones_succeeds() throws Exception {
    String proposalId = createProposalWithContent("Fixed Fee Proposal", "FIXED", "10000.00", "ZAR");

    // Add milestones that sum to 100
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/proposals/{id}/milestones", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {"description": "Phase 1", "percentage": 50.00, "relativeDueDays": 30},
                      {"description": "Phase 2", "percentage": 50.00, "relativeDueDays": 60}
                    ]
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void sendProposal_hourlyWithContent_succeeds() throws Exception {
    String proposalId = createProposalWithContent("Hourly Proposal", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void sendProposal_retainerWithContent_succeeds() throws Exception {
    String proposalId = createProposalWithRetainer("Retainer Proposal", "5000.00", "ZAR", "40.0");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void sendProposal_portalSyncWritesRow() throws Exception {
    String proposalId = createProposalWithContent("Portal Sync Test", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Verify portal.portal_proposals row exists
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM portal.portal_proposals WHERE id = ?::uuid",
            Integer.class,
            proposalId);
    assertThat(count).isEqualTo(1);
  }

  // --- 232.11: Validation failure tests ---

  @Test
  void sendProposal_sentStatus_returns409() throws Exception {
    String proposalId = createProposalWithContent("Already Sent", "HOURLY", null, null);

    // Send it first
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Try to send again — should fail 409
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isConflict());
  }

  @Test
  void sendProposal_emptyContent_returns400() throws Exception {
    // Create proposal without content (default empty map)
    String proposalId = createProposalWithoutContent("Empty Content", "HOURLY");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void sendProposal_invalidPortalContact_returns404() throws Exception {
    String proposalId = createProposalWithContent("Bad Contact", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void sendProposal_contactWrongCustomer_returns400() throws Exception {
    String proposalId = createProposalWithContent("Wrong Customer Contact", "HOURLY", null, null);

    // Use contact from second customer
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(secondCustomerContactId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void sendProposal_fixedFeeNoAmount_returns400() throws Exception {
    // Create proposal with FIXED but no fixedFeeAmount — requires direct SQL since API validates
    String proposalId = createProposalWithContent("Fixed No Amount", "HOURLY", null, null);

    // Switch to FIXED via direct SQL to bypass service validation
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET fee_model = 'FIXED', fixed_fee_amount = NULL WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isBadRequest());
  }

  // --- H2: Member role 403 tests ---

  @Test
  void sendProposal_memberRole_returns403() throws Exception {
    String proposalId = createProposalWithContent("Member Send Attempt", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void withdrawProposal_memberRole_returns403() throws Exception {
    String proposalId = createProposalWithContent("Member Withdraw Attempt", "HOURLY", null, null);

    // Send it first with owner
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Try to withdraw with member — should fail 403
    mockMvc
        .perform(post("/api/proposals/{id}/withdraw", proposalId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- 232.12: Withdraw test ---

  @Test
  void withdrawProposal_sentProposal_returnsDraft() throws Exception {
    String proposalId = createProposalWithContent("Withdraw Test", "HOURLY", null, null);

    // Send it first
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Withdraw
    mockMvc
        .perform(post("/api/proposals/{id}/withdraw", proposalId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.sentAt").isEmpty());
  }

  // --- Helpers ---

  private String createProposalWithContent(
      String title, String feeModel, String fixedFeeAmount, String fixedFeeCurrency)
      throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"This is proposal content."}]}]}
        """;
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"title\": \"").append(title).append("\",");
    json.append("\"customerId\": \"").append(customerId).append("\",");
    json.append("\"feeModel\": \"").append(feeModel).append("\",");
    json.append("\"contentJson\": ").append(contentJson);
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

  private String createProposalWithRetainer(
      String title, String retainerAmount, String retainerCurrency, String retainerHoursIncluded)
      throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Retainer proposal content."}]}]}
        """;
    String json =
        """
        {
          "title": "%s",
          "customerId": "%s",
          "feeModel": "RETAINER",
          "retainerAmount": %s,
          "retainerCurrency": "%s",
          "retainerHoursIncluded": %s,
          "contentJson": %s
        }
        """
            .formatted(
                title,
                customerId,
                retainerAmount,
                retainerCurrency,
                retainerHoursIncluded,
                contentJson);

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createProposalWithoutContent(String title, String feeModel) throws Exception {
    String json =
        """
        {"title": "%s", "customerId": "%s", "feeModel": "%s"}
        """
            .formatted(title, customerId, feeModel);

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createCustomer(JwtRequestPostProcessor jwt, String name, String email)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s", "type": "INDIVIDUAL"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private UUID createPortalContact(String customerIdStr, String email, String displayName) {
    UUID contactId = UUID.randomUUID();
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        ("INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name,"
                + " role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?,"
                + " 'PRIMARY', 'ACTIVE', now(), now())")
            .formatted(schema),
        contactId.toString(),
        ORG_ID,
        customerIdStr,
        email,
        displayName);
    return contactId;
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    assert location != null : "Expected Location header to be present";
    return location.substring(location.lastIndexOf('/') + 1);
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
            j -> j.subject("user_prop_send_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_prop_send_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
