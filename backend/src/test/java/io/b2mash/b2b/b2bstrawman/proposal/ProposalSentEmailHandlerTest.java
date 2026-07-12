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
 * OBS-703 — covers {@link ProposalSentEmailHandler}, the AFTER_COMMIT listener that fires the
 * portal-new-proposal email when a proposal is sent. Drives the production code path via {@code
 * POST /api/proposals/{id}/send} so we exercise the full pipeline from controller through service
 * through event publication and the listener.
 *
 * <p>Asserts against the {@link GreenMailTestSupport} JVM singleton on its dynamic port (per
 * backend CLAUDE.md — never start a new GreenMail instance).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalSentEmailHandlerTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_obs703_proposal_email";
  private static final String PORTAL_CONTACT_EMAIL = "obs703-contact@test.com";
  private static final String PORTAL_CONTACT_NAME = "OBS-703 Contact";

  // LZKC-004 — legal-za tenant, used to assert engagement-letter email vocabulary
  private static final String LEGAL_ORG_ID = "org_lzkc004_proposal_email";
  private static final String LEGAL_CONTACT_EMAIL = "lzkc004-contact@test.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String customerId;
  private UUID portalContactId;
  private String legalCustomerId;
  private UUID legalPortalContactId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OBS-703 Proposal Email Test", null);

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_obs703_owner", "obs703-owner@test.com", "OBS-703 Owner", "owner");

    customerId =
        createCustomer(
            TestJwtFactory.ownerJwt(ORG_ID, "user_obs703_owner"),
            "OBS-703 Customer",
            "obs703-customer@test.com");
    fillPrerequisiteFields(ORG_ID, customerId);
    portalContactId =
        createPortalContact(ORG_ID, customerId, PORTAL_CONTACT_EMAIL, PORTAL_CONTACT_NAME);

    provisioningService.provisionTenant(LEGAL_ORG_ID, "LZKC-004 Law Firm", "legal-za");
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_lzkc004_owner",
        "lzkc004-owner@test.com",
        "Law Owner",
        "owner");
    legalCustomerId =
        createCustomer(
            TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_lzkc004_owner"),
            "LZKC-004 Customer",
            "lzkc004-customer@test.com");
    fillPrerequisiteFields(LEGAL_ORG_ID, legalCustomerId);
    legalPortalContactId =
        createPortalContact(LEGAL_ORG_ID, legalCustomerId, LEGAL_CONTACT_EMAIL, "LZKC-004 Contact");
  }

  @BeforeEach
  void purgeMailbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void sendProposal_dispatchesPortalNewProposalEmail() throws Exception {
    String proposalId =
        createProposalWithContent(ORG_ID, "user_obs703_owner", customerId, "OBS-703 Send Test");
    String proposalNumber = lookupProposalNumber(ORG_ID, proposalId);
    assertThat(proposalNumber).as("proposalNumber must be persisted by createProposal").isNotNull();

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_obs703_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    // Wait briefly for the AFTER_COMMIT listener (different transaction) to dispatch.
    boolean delivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(delivered).as("expected one portal-new-proposal email within 5s").isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage message = received[0];
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(PORTAL_CONTACT_EMAIL);
    assertThat(message.getSubject()).contains(proposalNumber);
    // LZKC-004 regression guard: generic tenants keep the "proposal" vocabulary.
    assertThat(message.getSubject()).contains("New proposal");
    assertThat(message.getSubject()).containsIgnoringCase("review");

    String body = extractBody(message);
    assertThat(body).contains("/proposals/" + proposalId);
    assertThat(body).contains("View Proposal");
    assertThat(body).contains(proposalNumber);
  }

  @Test
  void sendProposal_legalZa_usesEngagementLetterVocabulary() throws Exception {
    String proposalId =
        createProposalWithContent(
            LEGAL_ORG_ID, "user_lzkc004_owner", legalCustomerId, "LZKC-004 Send Test");
    String proposalNumber = lookupProposalNumber(LEGAL_ORG_ID, proposalId);
    assertThat(proposalNumber).isNotNull();

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_lzkc004_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(legalPortalContactId)))
        .andExpect(status().isOk());

    boolean delivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(delivered).as("expected one portal-new-proposal email within 5s").isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage message = received[0];
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(LEGAL_CONTACT_EMAIL);

    // LZKC-004: subject must speak engagement-letter vocabulary, never "proposal".
    assertThat(message.getSubject()).contains("New engagement letter " + proposalNumber);
    assertThat(message.getSubject()).doesNotContainIgnoringCase("proposal");

    // Body chrome likewise (the portal URL still contains "/proposals/" — that's a path, not copy).
    String body = extractBody(message);
    assertThat(body).contains("New Engagement Letter for Your Review");
    assertThat(body).contains("has sent you an engagement letter for your review");
    assertThat(body).contains("View Engagement Letter");
    assertThat(body).doesNotContain("View Proposal");
    assertThat(body).contains(proposalNumber);
  }

  // --- Helpers ---

  private String createProposalWithContent(String orgId, String userId, String custId, String title)
      throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"OBS-703 proposal body."}]}]}
        """;
    String json =
        """
        {"title": "%s", "customerId": "%s", "feeModel": "HOURLY", "contentJson": %s}
        """
            .formatted(title, custId, contentJson);

    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(TestJwtFactory.ownerJwt(orgId, userId))
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

  private UUID createPortalContact(
      String orgId, String customerIdStr, String email, String displayName) {
    UUID contactId = UUID.randomUUID();
    String schema = SchemaNameGenerator.generateSchemaName(orgId);
    jdbcTemplate.update(
        ("INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name,"
                + " role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?,"
                + " 'PRIMARY', 'ACTIVE', now(), now())")
            .formatted(schema),
        contactId.toString(),
        orgId,
        customerIdStr,
        email,
        displayName);
    return contactId;
  }

  private void fillPrerequisiteFields(String orgId, String customerIdStr) {
    String schema = SchemaNameGenerator.generateSchemaName(orgId);
    TestCustomerFactory.fillPrerequisiteFields(jdbcTemplate, schema, customerIdStr);
  }

  private String lookupProposalNumber(String orgId, String proposalId) {
    String schema = SchemaNameGenerator.generateSchemaName(orgId);
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
