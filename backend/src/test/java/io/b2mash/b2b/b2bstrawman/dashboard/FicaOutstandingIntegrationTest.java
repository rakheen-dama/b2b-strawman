package io.b2mash.b2b.b2bstrawman.dashboard;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createCustomerWithStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/dashboard/fica-outstanding} (Phase 87, Epic 614A). Dedicated org; clients:
 *
 * <ul>
 *   <li>ONBOARDING, no FICA request, clock started 10 days ago → NOT_STARTED, listed first
 *   <li>ACTIVE with a DRAFT FICA request → IN_PROGRESS
 *   <li>ACTIVE with a COMPLETED, all-accepted FICA request → DONE, excluded
 *   <li>Onboarded through the real path (firm onboarding template → Start Onboarding → items
 *       completed via the API; ONBOARDING or auto-ACTIVE) with no FICA request → DONE via the
 *       completed onboarding checklist (ADR-348), excluded but counted
 *   <li>PROSPECT and OFFBOARDED clients without FICA → out of scope, excluded
 *   <li>Archived client re-activated via the lifecycle API (status ARCHIVED, lifecycle ACTIVE, no
 *       FICA) → excluded and not counted
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicaOutstandingIntegrationTest {
  private static final String ORG_ID = "org_dash_fica_outstanding";
  private static final String OWNER = "user_dfo_owner";
  private static final String MEMBER = "user_dfo_member";
  private static final String PACK_ID = "fica-onboarding-pack";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String schema;
  private String ficaTemplateId;
  private UUID ownerId;
  private String notStartedId;
  private String inProgressId;
  private String inProgressRequestId;
  private String doneId;
  private String prospectId;
  private String offboardedId;
  private String archivedId;
  private String checklistDoneId;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "FICA Outstanding Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "dfo_owner@test.com", "DFO Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "dfo_member@test.com", "DFO Member", "member");
    ficaTemplateId = createFicaTemplate();

    notStartedId = createClient("Bravo Onboarding Client", LifecycleStatus.ONBOARDING);
    inProgressId = createClient("Alpha Active In Progress", LifecycleStatus.ACTIVE);
    doneId = createClient("Charlie Active Done", LifecycleStatus.ACTIVE);
    prospectId = createClient("Delta Prospect", LifecycleStatus.PROSPECT);
    offboardedId = createClient("Echo Offboarded", LifecycleStatus.OFFBOARDED);
    archivedId = createArchivedActiveClient("Foxtrot Archived Reactivated");

    // Clock start 10 days ago for the NOT_STARTED client → longest outstanding.
    jdbcTemplate.update(
        "UPDATE \"%s\".customers SET lifecycle_status_changed_at = now() - interval '10 days' WHERE id = ?::uuid"
            .formatted(schema),
        notStartedId);

    inProgressRequestId =
        createFicaRequest(inProgressId, createPortalContact(inProgressId, "dfo-ip@test.com"));

    String doneRequestId =
        createFicaRequest(doneId, createPortalContact(doneId, "dfo-done@test.com"));
    completeWithAllItemsAccepted(doneRequestId);

    // Checklist-onboarded client (ADR-348): real path, no FICA request.
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    TestChecklistHelper.createOnboardingTemplate(mockMvc, owner, "DFO Onboarding");
    checklistDoneId =
        TestEntityHelper.createCustomer(
            mockMvc,
            owner,
            "Golf Checklist Onboarded",
            "dfo-golf-" + UUID.randomUUID() + "@test.com");
    TestChecklistHelper.startOnboarding(mockMvc, checklistDoneId, owner);
    TestChecklistHelper.completeChecklistItems(mockMvc, checklistDoneId, owner);
  }

  @Test
  void listsOutstandingOnboardingAndActiveClientsOnly() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/dashboard/fica-outstanding").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(10))
            .andExpect(jsonPath("$.outstandingCount").value(2))
            // Not-started, in-progress, request-DONE and checklist-DONE clients.
            .andExpect(jsonPath("$.totalActiveClients").value(4))
            .andExpect(jsonPath("$.clients", hasSize(2)))
            // Longest outstanding first.
            .andExpect(jsonPath("$.clients[0].clientId").value(notStartedId))
            .andExpect(jsonPath("$.clients[0].clientName").value("Bravo Onboarding Client"))
            .andExpect(jsonPath("$.clients[0].lifecycleStatus").value("ONBOARDING"))
            .andExpect(jsonPath("$.clients[0].ficaStatus").value("NOT_STARTED"))
            .andExpect(jsonPath("$.clients[0].requestId").doesNotExist())
            .andExpect(jsonPath("$.clients[0].openedAt").isNotEmpty())
            .andExpect(jsonPath("$.clients[0].daysOutstanding").value(10))
            .andExpect(jsonPath("$.clients[1].clientId").value(inProgressId))
            .andExpect(jsonPath("$.clients[1].lifecycleStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.clients[1].ficaStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.clients[1].requestId").value(inProgressRequestId))
            .andExpect(jsonPath("$.clients[1].daysOutstanding").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> listed = JsonPath.read(body, "$.clients[*].clientId");
    assertThat(listed)
        .doesNotContain(doneId, checklistDoneId, prospectId, offboardedId, archivedId);
  }

  @Test
  void archivedClientInActiveLifecycleIsExcludedAndNotCounted() throws Exception {
    // Precondition: the client really is archived yet in an in-scope lifecycle.
    var row =
        jdbcTemplate.queryForMap(
            "SELECT status, lifecycle_status FROM \"%s\".customers WHERE id = ?::uuid"
                .formatted(schema),
            UUID.fromString(archivedId));
    assertThat(row).containsEntry("status", "ARCHIVED").containsEntry("lifecycle_status", "ACTIVE");

    String body =
        mockMvc
            .perform(
                get("/api/dashboard/fica-outstanding")
                    .param("limit", "50")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            // Only the four non-archived ONBOARDING/ACTIVE clients count (incl. checklist-DONE).
            .andExpect(jsonPath("$.totalActiveClients").value(4))
            .andExpect(jsonPath("$.outstandingCount").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> listed = JsonPath.read(body, "$.clients[*].clientId");
    assertThat(listed).doesNotContain(archivedId);
  }

  @Test
  void batchStatusMatchesSingleClientEndpoint() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/dashboard/fica-outstanding").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> clients = JsonPath.read(body, "$.clients");
    assertThat(clients).isNotEmpty();
    for (var client : clients) {
      mockMvc
          .perform(
              get("/api/clients/{id}/fica-status", client.get("clientId"))
                  .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value(client.get("ficaStatus")));
    }
    // And the excluded DONE client really is DONE on the single-client read.
    mockMvc
        .perform(
            get("/api/clients/{id}/fica-status", doneId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
    // And the excluded checklist-onboarded client is DONE from its onboarding checklist.
    mockMvc
        .perform(
            get("/api/clients/{id}/fica-status", checklistDoneId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.checklistInstanceId").isNotEmpty())
        .andExpect(jsonPath("$.requestId").doesNotExist());
  }

  @Test
  void limitTruncatesClientsButNotTheCount() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/fica-outstanding")
                .param("limit", "1")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(1))
        .andExpect(jsonPath("$.outstandingCount").value(2))
        .andExpect(jsonPath("$.clients", hasSize(1)))
        .andExpect(jsonPath("$.clients[0].clientId").value(notStartedId));
  }

  @Test
  void limitIsClampedToOneThroughFifty() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/fica-outstanding")
                .param("limit", "0")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(1))
        .andExpect(jsonPath("$.clients", hasSize(1)));
    mockMvc
        .perform(
            get("/api/dashboard/fica-outstanding")
                .param("limit", "500")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(50))
        .andExpect(jsonPath("$.clients", hasSize(2)));
  }

  @Test
  void memberWithoutCustomerManagementIsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/fica-outstanding").with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isForbidden());
  }

  // --- helpers ---

  private String createClient(String name, LifecycleStatus lifecycleStatus) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        customerRepository
                            .saveAndFlush(
                                createCustomerWithStatus(
                                    name,
                                    "dfo-" + UUID.randomUUID() + "@test.com",
                                    ownerId,
                                    lifecycleStatus))
                            .getId()
                            .toString()));
  }

  /**
   * Real API path to an archived client in an in-scope lifecycle: archive (→ status ARCHIVED,
   * lifecycle OFFBOARDED), then reactivate OFFBOARDED → ACTIVE via the lifecycle transition, which
   * does not touch {@code status}.
   */
  private String createArchivedActiveClient(String name) throws Exception {
    String id = createClient(name, LifecycleStatus.ONBOARDING);
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    mockMvc
        .perform(delete("/api/clients/{id}", id).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    mockMvc
        .perform(
            post("/api/clients/{id}/transition", id)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE"}
                    """))
        .andExpect(status().isOk());
    return id;
  }

  private String createPortalContact(String clientId, String email) {
    String contactId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
            .formatted(schema),
        contactId,
        ORG_ID,
        clientId,
        email,
        "DFO Contact");
    return contactId;
  }

  private String createFicaTemplate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/request-templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "FICA Onboarding Pack",
                          "description": "FICA onboarding",
                          "items": [
                            {"name": "ID copy", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                            {"name": "Proof of residence", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 1}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    // Back-fill the pack_id column — the public API does not expose it.
    jdbcTemplate.update(
        "UPDATE \"%s\".request_templates SET pack_id = ? WHERE id = ?::uuid".formatted(schema),
        PACK_ID,
        templateId);
    return templateId;
  }

  private String createFicaRequest(String clientId, String contactId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "requestTemplateId": "%s",
                          "clientId": "%s",
                          "portalContactId": "%s",
                          "reminderIntervalDays": 7
                        }
                        """
                            .formatted(ficaTemplateId, clientId, contactId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  /** SENT → items SUBMITTED (JDBC, the portal's job) → accept each via the API → COMPLETED. */
  private void completeWithAllItemsAccepted(String requestId) throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    mockMvc
        .perform(post("/api/information-requests/{id}/send", requestId).with(owner))
        .andExpect(status().isOk());
    var detail =
        mockMvc
            .perform(get("/api/information-requests/{id}", requestId).with(owner))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> itemIds = JsonPath.read(detail, "$.items[*].id");
    for (String itemId : itemIds) {
      jdbcTemplate.update(
          "UPDATE \"%s\".request_items SET status = 'SUBMITTED', submitted_at = now(), document_id = ?::uuid WHERE id = ?::uuid"
              .formatted(schema),
          UUID.randomUUID().toString(),
          itemId);
    }
    for (String itemId : itemIds) {
      mockMvc
          .perform(
              post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                  .with(owner))
          .andExpect(status().isOk());
    }
  }
}
