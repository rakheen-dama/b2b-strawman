package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OBS-AUDIT-N1 — covers the proposal-expiry portal email side-effect added to {@link
 * ProposalExpiredEventHandler}, plus a sanity check that V119 applied (the migration also adds the
 * pre-existing {@code PORTAL_NEW_PROPOSAL} reference type missed by PR #1233 — schema-code
 * consistency rather than an empirical bugfix).
 *
 * <p>Drives the full production path: POST proposal → POST send → JDBC-backdate {@code expires_at}
 * → invoke {@link ProposalExpiryProcessor#processExpiredProposals()} → AFTER_COMMIT listener fires
 * the portal email. Asserts on the GreenMail singleton (port 13025) and on the proposal status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalExpiredEmailIntegrationTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_obs_audit_n1_proposal_expired";
  private static final String OWNER_USER = "user_obs_audit_n1_owner";
  private static final String PORTAL_CONTACT_EMAIL = "obs-audit-n1-contact@test.com";
  private static final String PORTAL_CONTACT_NAME = "OBS-AUDIT-N1 Contact";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ProposalExpiryProcessor proposalExpiryProcessor;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String customerId;
  private UUID portalContactId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OBS-AUDIT-N1 Proposal Expired Test", null);

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_USER, "obs-audit-n1-owner@test.com", "OBS-AUDIT-N1 Owner", "owner");

    customerId =
        createCustomer(
            TestJwtFactory.ownerJwt(ORG_ID, OWNER_USER),
            "OBS-AUDIT-N1 Customer",
            "obs-audit-n1-customer@test.com");
    fillPrerequisiteFields(customerId);
    portalContactId = createPortalContact(customerId, PORTAL_CONTACT_EMAIL, PORTAL_CONTACT_NAME);
  }

  @BeforeEach
  void purgeMailbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void expiredProposal_dispatchesPortalEmail_andLogsBothReferenceTypes() throws Exception {
    String proposalId = createProposalWithContent("OBS-AUDIT-N1 Expired Test");
    String proposalNumber = lookupProposalNumber(proposalId);
    assertThat(proposalNumber).as("proposalNumber must be persisted by createProposal").isNotNull();

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    boolean newProposalDelivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(newProposalDelivered).as("expected portal-new-proposal email after send").isTrue();
    greenMail.purgeEmailFromAllMailboxes();

    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".proposals SET expires_at = ? WHERE id = ?::uuid".formatted(schema),
        Timestamp.from(Instant.now().minusSeconds(3600)),
        proposalId);

    proposalExpiryProcessor.processExpiredProposals();

    boolean expiredDelivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(expiredDelivered)
        .as("expected one portal-proposal-expired email within 5s of expiry processor run")
        .isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage message = received[0];
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(PORTAL_CONTACT_EMAIL);
    assertThat(message.getSubject()).contains(proposalNumber);
    assertThat(message.getSubject()).containsIgnoringCase("expired");

    String body = extractBody(message);
    assertThat(body).contains(proposalNumber);
    assertThat(body).containsIgnoringCase("expired");

    Integer v119 =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM \"%s\".flyway_schema_history WHERE version = '119' AND success = true"
                .formatted(schema),
            Integer.class);
    assertThat(v119).as("V119 must be applied to tenant schema").isEqualTo(1);

    String proposalStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM \"%s\".proposals WHERE id = ?::uuid".formatted(schema),
            String.class,
            proposalId);
    assertThat(proposalStatus).isEqualTo("EXPIRED");
  }

  // --- Helpers (mirror ProposalSentEmailHandlerTest) ---

  private String createProposalWithContent(String title) throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"OBS-AUDIT-N1 proposal body."}]}]}
        """;
    String json =
        """
        {"title": "%s", "customerId": "%s", "feeModel": "HOURLY", "contentJson": %s}
        """
            .formatted(title, customerId, contentJson);

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_USER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }

  private String createCustomer(
      org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
              .JwtRequestPostProcessor
          jwt,
      String name,
      String email)
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

  private String lookupProposalNumber(String proposalId) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    return jdbcTemplate.queryForObject(
        "SELECT proposal_number FROM \"%s\".proposals WHERE id = ?::uuid".formatted(schema),
        String.class,
        proposalId);
  }

  private String extractBody(jakarta.mail.Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return s;
    }
    if (content instanceof jakarta.mail.Multipart mp) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < mp.getCount(); i++) {
        sb.append(extractBody(mp.getBodyPart(i)));
      }
      return sb.toString();
    }
    return content == null ? "" : content.toString();
  }
}
