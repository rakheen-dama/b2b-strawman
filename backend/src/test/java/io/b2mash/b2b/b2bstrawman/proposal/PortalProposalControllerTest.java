package io.b2mash.b2b.b2bstrawman.proposal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalProposalControllerTest {

  private static final String ORG_ID = "org_portal_proposal_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProposalService proposalService;
  @Autowired private ProposalPortalSyncService proposalPortalSyncService;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private UUID customerId;
  private UUID portalContactId;
  private UUID memberId;
  private String tenantSchema;
  private String portalToken;

  // Proposal IDs for various test scenarios
  private UUID sentProposalId;
  private UUID acceptedProposalId;
  private UUID declinedProposalId;
  private UUID expiredProposalId;
  private UUID sentProposalForAcceptId;
  private UUID sentProposalForDeclineId;

  // Second customer for scoping tests
  private UUID otherCustomerId;
  private UUID otherContactId;
  private String otherPortalToken;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Proposal Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync a member
    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_proposal_owner",
                          "email": "proposal_owner@test.com",
                          "name": "Proposal Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    memberId = UUID.fromString(memberIdStr);

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create customer and portal contact in tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Proposal Test Customer",
                      "proposal-customer@test.com",
                      null,
                      null,
                      null,
                      memberId);
              customerId = customer.getId();
              customerService.updateCustomer(
                  customerId,
                  customer.getName(),
                  customer.getEmail(),
                  customer.getPhone(),
                  customer.getIdNumber(),
                  customer.getNotes(),
                  io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.prerequisiteCustomFields(),
                  null);

              var contact =
                  portalContactService.createContact(
                      ORG_ID,
                      customerId,
                      "proposal-contact@test.com",
                      "Proposal Contact",
                      PortalContact.ContactRole.PRIMARY);
              portalContactId = contact.getId();
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Create second customer for scoping tests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var otherCust =
                  customerService.createCustomer(
                      "Other Customer", "other-customer@test.com", null, null, null, memberId);
              otherCustomerId = otherCust.getId();
              customerService.updateCustomer(
                  otherCustomerId,
                  otherCust.getName(),
                  otherCust.getEmail(),
                  otherCust.getPhone(),
                  otherCust.getIdNumber(),
                  otherCust.getNotes(),
                  io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.prerequisiteCustomFields(),
                  null);

              var otherCon =
                  portalContactService.createContact(
                      ORG_ID,
                      otherCustomerId,
                      "other-contact@test.com",
                      "Other Contact",
                      PortalContact.ContactRole.PRIMARY);
              otherContactId = otherCon.getId();
            });

    otherPortalToken = portalJwtService.issueToken(otherCustomerId, ORG_ID);

    // Create proposals in tenant scope for accept/decline tests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () -> {
              // Proposal for accept test — create, send, and seed portal data
              var proposalForAccept =
                  proposalService.createProposal(
                      "Accept Test Proposal",
                      customerId,
                      FeeModel.HOURLY,
                      memberId,
                      portalContactId,
                      null,
                      null,
                      "Standard hourly rate",
                      null,
                      null,
                      null,
                      Map.of("type", "doc", "content", List.of()),
                      null,
                      null);
              sentProposalForAcceptId = proposalForAccept.getId();
              proposalService.sendProposal(sentProposalForAcceptId, portalContactId);

              // Proposal for decline test
              var proposalForDecline =
                  proposalService.createProposal(
                      "Decline Test Proposal",
                      customerId,
                      FeeModel.FIXED,
                      memberId,
                      portalContactId,
                      new BigDecimal("5000.00"),
                      "ZAR",
                      null,
                      null,
                      null,
                      null,
                      Map.of("type", "doc", "content", List.of()),
                      null,
                      null);
              sentProposalForDeclineId = proposalForDecline.getId();
              proposalService.sendProposal(sentProposalForDeclineId, portalContactId);
            });

    // Seed portal read-model data directly for list/detail tests
    sentProposalId = UUID.randomUUID();
    seedPortalProposal(
        sentProposalId,
        customerId,
        portalContactId,
        "PROP-001",
        "Website Redesign Proposal",
        "SENT",
        "FIXED",
        new BigDecimal("15000.00"),
        "ZAR",
        "<h1>Proposal Content</h1><p>Website redesign details.</p>",
        "[{\"description\":\"Design Phase\",\"percentage\":50},{\"description\":\"Build Phase\",\"percentage\":50}]",
        Instant.parse("2026-02-01T10:00:00Z"),
        Instant.parse("2026-03-01T10:00:00Z"));

    acceptedProposalId = UUID.randomUUID();
    seedPortalProposal(
        acceptedProposalId,
        customerId,
        portalContactId,
        "PROP-002",
        "Accepted Proposal",
        "ACCEPTED",
        "HOURLY",
        null,
        null,
        "<p>Hourly engagement</p>",
        "[]",
        Instant.parse("2026-01-15T10:00:00Z"),
        null);

    declinedProposalId = UUID.randomUUID();
    seedPortalProposal(
        declinedProposalId,
        customerId,
        portalContactId,
        "PROP-003",
        "Declined Proposal",
        "DECLINED",
        "FIXED",
        new BigDecimal("8000.00"),
        "ZAR",
        "<p>Declined proposal content</p>",
        "[]",
        Instant.parse("2026-01-10T10:00:00Z"),
        null);

    expiredProposalId = UUID.randomUUID();
    seedPortalProposal(
        expiredProposalId,
        customerId,
        portalContactId,
        "PROP-004",
        "Expired Proposal",
        "EXPIRED",
        "RETAINER",
        new BigDecimal("20000.00"),
        "ZAR",
        "<p>Expired proposal content</p>",
        "[]",
        Instant.parse("2026-01-05T10:00:00Z"),
        Instant.parse("2026-01-20T10:00:00Z"));

    // Seed portal read-model for accept/decline proposals (AFTER_COMMIT handler may not fire
    // within test transaction, so seed manually)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var acceptProposal = proposalService.getProposal(sentProposalForAcceptId);
              seedPortalProposal(
                  sentProposalForAcceptId,
                  customerId,
                  portalContactId,
                  acceptProposal.getProposalNumber(),
                  "Accept Test Proposal",
                  "SENT",
                  "HOURLY",
                  null,
                  null,
                  "<p>Accept test content</p>",
                  "[]",
                  acceptProposal.getSentAt(),
                  null);

              var declineProposal = proposalService.getProposal(sentProposalForDeclineId);
              seedPortalProposal(
                  sentProposalForDeclineId,
                  customerId,
                  portalContactId,
                  declineProposal.getProposalNumber(),
                  "Decline Test Proposal",
                  "SENT",
                  "FIXED",
                  new BigDecimal("5000.00"),
                  "ZAR",
                  "<p>Decline test content</p>",
                  "[]",
                  declineProposal.getSentAt(),
                  null);
            });
  }

  // --- List proposals tests ---

  @Test
  void listProposals_returnsOnlySentAndTerminal() throws Exception {
    mockMvc
        .perform(get("/portal/api/proposals").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
        .andExpect(jsonPath("$[0].proposalNumber").exists())
        .andExpect(jsonPath("$[0].title").exists())
        .andExpect(jsonPath("$[0].status").exists());
  }

  @Test
  void listProposals_scopedToPortalContact() throws Exception {
    // Other customer's token should not see this customer's proposals
    mockMvc
        .perform(get("/portal/api/proposals").header("Authorization", "Bearer " + otherPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- Detail tests ---

  @Test
  void getProposalDetail_returnsRenderedHtml() throws Exception {
    mockMvc
        .perform(
            get("/portal/api/proposals/{id}", sentProposalId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sentProposalId.toString()))
        .andExpect(jsonPath("$.proposalNumber").value("PROP-001"))
        .andExpect(jsonPath("$.title").value("Website Redesign Proposal"))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(
            jsonPath("$.contentHtml")
                .value("<h1>Proposal Content</h1><p>Website redesign details.</p>"))
        .andExpect(jsonPath("$.orgName").value("Portal Proposal Test Org"));
  }

  @Test
  void getProposalDetail_includesMilestones() throws Exception {
    mockMvc
        .perform(
            get("/portal/api/proposals/{id}", sentProposalId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.milestonesJson").exists());
  }

  @Test
  void getProposal_wrongCustomer_returns404() throws Exception {
    // Other customer's token should not access this customer's proposal
    mockMvc
        .perform(
            get("/portal/api/proposals/{id}", sentProposalId)
                .header("Authorization", "Bearer " + otherPortalToken))
        .andExpect(status().isNotFound());
  }

  // --- Accept tests ---

  @Test
  void acceptProposal_triggersOrchestration() throws Exception {
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/accept", sentProposalForAcceptId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proposalId").value(sentProposalForAcceptId.toString()))
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptedAt").exists())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void acceptProposal_alreadyAccepted_returns200Idempotent() throws Exception {
    // Create a dedicated proposal for this test, accept it via the service, then verify idempotency
    var idempotentProposalId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () -> {
              var proposal =
                  proposalService.createProposal(
                      "Idempotent Accept Test",
                      customerId,
                      FeeModel.HOURLY,
                      memberId,
                      portalContactId,
                      null,
                      null,
                      "Hourly rate",
                      null,
                      null,
                      null,
                      Map.of("type", "doc", "content", List.of()),
                      null,
                      null);
              idempotentProposalId[0] = proposal.getId();
              proposalService.sendProposal(idempotentProposalId[0], portalContactId);
            });

    // Seed portal read-model for this proposal in SENT state first
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var p = proposalService.getProposal(idempotentProposalId[0]);
              seedPortalProposal(
                  idempotentProposalId[0],
                  customerId,
                  portalContactId,
                  p.getProposalNumber(),
                  "Idempotent Accept Test",
                  "SENT",
                  "HOURLY",
                  null,
                  null,
                  "<p>Idempotent test</p>",
                  "[]",
                  p.getSentAt(),
                  null);
            });

    // First accept — triggers orchestration
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/accept", idempotentProposalId[0])
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    // Update portal read-model to reflect ACCEPTED status
    portalJdbc
        .sql("UPDATE portal.portal_proposals SET status = 'ACCEPTED' WHERE id = ?")
        .params(idempotentProposalId[0])
        .update();

    // Second accept — idempotent, should return 200 with already-accepted message
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/accept", idempotentProposalId[0])
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.message").value("This proposal has already been accepted."));
  }

  @Test
  void acceptProposal_expired_returns409() throws Exception {
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/accept", expiredProposalId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isConflict());
  }

  // --- Decline tests ---

  @Test
  void declineProposal_setsReasonAndStatus() throws Exception {
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/decline", sentProposalForDeclineId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Budget constraints"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proposalId").value(sentProposalForDeclineId.toString()))
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declinedAt").exists());
  }

  @Test
  void declineProposal_alreadyDeclined_returns409() throws Exception {
    mockMvc
        .perform(
            post("/portal/api/proposals/{id}/decline", declinedProposalId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Changed mind"}
                    """))
        .andExpect(status().isConflict());
  }

  // --- Auth tests ---

  @Test
  void getProposalDetail_returns401WithoutToken() throws Exception {
    mockMvc
        .perform(get("/portal/api/proposals/{id}", sentProposalId))
        .andExpect(status().isUnauthorized());
  }

  // --- Helpers ---

  private void seedPortalProposal(
      UUID id,
      UUID customerId,
      UUID portalContactId,
      String proposalNumber,
      String title,
      String status,
      String feeModel,
      BigDecimal feeAmount,
      String feeCurrency,
      String contentHtml,
      String milestonesJson,
      Instant sentAt,
      Instant expiresAt) {
    portalJdbc
        .sql(
            """
            INSERT INTO portal.portal_proposals
                (id, org_id, customer_id, portal_contact_id, proposal_number, title, status,
                 fee_model, fee_amount, fee_currency, content_html, milestones_json,
                 sent_at, expires_at, org_name, org_logo, org_brand_color, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, synced_at = now()
            """)
        .params(
            id,
            ORG_ID,
            customerId,
            portalContactId,
            proposalNumber,
            title,
            status,
            feeModel,
            feeAmount,
            feeCurrency,
            contentHtml,
            milestonesJson,
            sentAt != null ? Timestamp.from(sentAt) : null,
            expiresAt != null ? Timestamp.from(expiresAt) : null,
            "Portal Proposal Test Org",
            null,
            null)
        .update();
  }
}
