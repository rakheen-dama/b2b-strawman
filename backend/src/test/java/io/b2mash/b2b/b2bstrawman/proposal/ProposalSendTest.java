package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalSendTest {
  private static final String ORG_ID = "org_proposal_send_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String memberIdOwner;
  private String customerId;
  private String secondCustomerId;
  private UUID portalContactId;
  private UUID secondCustomerContactId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Send Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_prop_send_owner",
            "prop_send_owner@test.com",
            "SEND Owner",
            "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_prop_send_admin", "prop_send_admin@test.com", "SEND Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_prop_send_member",
        "prop_send_member@test.com",
        "SEND Member",
        "member");

    customerId =
        createCustomer(
            TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"),
            "Send Test Customer",
            "send-customer@test.com");
    fillPrerequisiteFields(customerId);
    portalContactId = createPortalContact(customerId, "contact@send-test.com", "Send Contact");

    secondCustomerId =
        createCustomer(
            TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"),
            "Second Customer",
            "second-customer@test.com");
    fillPrerequisiteFields(secondCustomerId);
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Try to send again — should fail 409
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isConflict());
  }

  @Test
  void sendProposal_emptyContent_returns400() throws Exception {
    // BUG-CYCLE26-07: create-time seeder now populates content_json with a default Tiptap doc,
    // so omitting contentJson in the request no longer leaves the row empty. To keep gate-2
    // honest we forcibly clear content_json via direct SQL (mirrors what a future code path
    // explicitly mutating content back to empty would do) and then attempt to send.
    String proposalId = createProposalWithoutContent("Empty Content", "HOURLY");
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET content_json = '{}'::jsonb WHERE id = ?::uuid"
            .formatted(schema),
        proposalId);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isBadRequest());
  }

  // --- BUG-CYCLE26-07: create-time seeder produces sendable content ---

  @Test
  void createProposal_withoutContent_seedsDefaultDocSendableEndToEnd() throws Exception {
    // BUG-CYCLE26-07 regression: matter-level "+ New Engagement Letter" dialog never sends
    // contentJson. Without the seeder, sendProposal() would throw gate-2. With the seeder,
    // the persisted row has a non-empty Tiptap doc and the proposal sends successfully.
    String proposalId = createProposalWithoutContent("Seeded Default Content", "HOURLY");

    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    String contentJson =
        jdbcTemplate.queryForObject(
            "SELECT content_json::text FROM \"%s\".proposals WHERE id = ?::uuid".formatted(schema),
            String.class,
            proposalId);
    assertThat(contentJson).isNotNull();
    assertThat(contentJson).contains("\"type\"").contains("\"doc\"");
    assertThat(contentJson).contains("client_name");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
  }

  @Test
  void sendProposal_invalidPortalContact_returns404() throws Exception {
    String proposalId = createProposalWithContent("Bad Contact", "HOURLY", null, null);

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_send_member"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Try to withdraw with member — should fail 403
    mockMvc
        .perform(
            post("/api/proposals/{id}/withdraw", proposalId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_prop_send_member")))
        .andExpect(status().isForbidden());
  }

  // --- BUG-CYCLE26-06: INDIVIDUAL customer with portal_contact only ---

  @Test
  void sendProposal_individualCustomer_withPortalContact_succeeds_evenWhenContactColumnsNull()
      throws Exception {
    // BUG-CYCLE26-06: INDIVIDUAL customer where Customer.contact_name/contact_email are NULL
    // but an ACTIVE portal_contact bears the recipient identity. PROPOSAL_SEND prereq must
    // honour the portal_contact and let the send through. Mirrors Day 7 §7.8 cycle-14 scenario.
    String individualCustomerId =
        createCustomer(
            TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"),
            "Sipho Dlamini",
            "sipho@bug-cycle26-06.test");
    fillIndividualCustomerWithoutContactFields(individualCustomerId);
    UUID individualPortalContactId =
        createPortalContact(individualCustomerId, "sipho.portal@bug-cycle26-06.test", "Sipho D.");

    String proposalId =
        createProposalForCustomer(individualCustomerId, "Individual Send Test", "HOURLY");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(individualPortalContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
  }

  // --- 232.12: Withdraw test ---

  @Test
  void withdrawProposal_sentProposal_returnsDraft() throws Exception {
    String proposalId = createProposalWithContent("Withdraw Test", "HOURLY", null, null);

    // Send it first
    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Withdraw
    mockMvc
        .perform(
            post("/api/proposals/{id}/withdraw", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.toString()))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
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

  private void fillPrerequisiteFields(String customerIdStr) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    TestCustomerFactory.fillPrerequisiteFields(jdbcTemplate, schema, customerIdStr);
  }

  /**
   * BUG-CYCLE26-06: mirrors how INDIVIDUAL legal-za customers are persisted. address_line1 is set
   * (used in the proposal letter body), but contact_name/contact_email remain NULL on both the
   * entity column and JSONB — the recipient identity lives in portal_contact. Also activates the
   * customer so guard-checked operations (proposal send) are allowed.
   */
  private void fillIndividualCustomerWithoutContactFields(String customerIdStr) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET"
                + " custom_fields = '{\"address_line1\":\"12 Loveday St\",\"city\":\"Johannesburg\","
                + "\"country\":\"ZA\",\"tax_number\":\"VAT-IND-001\"}'::jsonb,"
                + " address_line1 = '12 Loveday St',"
                + " city = 'Johannesburg',"
                + " country = 'ZA',"
                + " tax_number = 'VAT-IND-001',"
                + " contact_name = NULL,"
                + " contact_email = NULL,"
                + " lifecycle_status = 'ACTIVE'"
                + " WHERE id = ?::uuid")
            .formatted(schema),
        customerIdStr);
  }

  private String createProposalForCustomer(String targetCustomerId, String title, String feeModel)
      throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Proposal body."}]}]}
        """;
    String json =
        """
        {"title": "%s", "customerId": "%s", "feeModel": "%s", "contentJson": %s}
        """
            .formatted(title, targetCustomerId, feeModel, contentJson);

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_prop_send_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }
}
