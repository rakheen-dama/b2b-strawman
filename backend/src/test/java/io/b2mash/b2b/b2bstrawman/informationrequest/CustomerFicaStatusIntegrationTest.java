package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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

/**
 * End-to-end coverage for {@code GET /api/customers/{id}/fica-status} (GAP-L-46).
 *
 * <p>Verifies the three status states — {@code NOT_STARTED} (no FICA request), {@code IN_PROGRESS}
 * (FICA request exists but not all items accepted), {@code DONE} (every item ACCEPTED). Uses direct
 * JDBC to mark items ACCEPTED and to back-fill the {@code pack_id} column, since the public
 * template API does not expose pack identifiers (they come from pack JSON seeding).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerFicaStatusIntegrationTest {

  private static final String ORG_ID = "org_fica_status_test";
  private static final String PACK_ID = "fica-onboarding-pack";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String ficaTemplateId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FICA Status Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_fica_owner", "fica_owner@test.com", "Fica Owner", "owner");

    // Verify tenant schema resolved correctly (used implicitly by helpers).
    orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    ficaTemplateId = createFicaTemplate();
  }

  @Test
  void returnsNotStartedWhenNoFicaRequestExists() throws Exception {
    String freshCustomer = createCustomer();

    mockMvc
        .perform(
            get("/api/customers/{id}/fica-status", freshCustomer)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(freshCustomer))
        .andExpect(jsonPath("$.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.lastVerifiedAt").doesNotExist())
        .andExpect(jsonPath("$.requestId").doesNotExist());
  }

  @Test
  void returnsInProgressWhenFicaRequestHasUnacceptedItems() throws Exception {
    String isolatedCustomer = createCustomer();
    String isolatedContact =
        createPortalContact(isolatedCustomer, "ip-fica@test.com", "IP Fica Contact");
    createFicaRequest(isolatedCustomer, isolatedContact);

    mockMvc
        .perform(
            get("/api/customers/{id}/fica-status", isolatedCustomer)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(isolatedCustomer))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.lastVerifiedAt").doesNotExist())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void returnsDoneWhenEveryItemIsAcceptedAndRequestCompleted() throws Exception {
    String doneCustomer = createCustomer();
    String doneContact = createPortalContact(doneCustomer, "done-fica@test.com", "Done Contact");
    String requestId = createFicaRequest(doneCustomer, doneContact);

    // Flip the request through SENT → IN_PROGRESS so items can be moved
    // to SUBMITTED → ACCEPTED via the accept endpoint below. We also mark
    // completed_at so the DONE branch's lastVerifiedAt can be asserted.
    markRequestSent(requestId);

    // Mark every item SUBMITTED (the portal normally does this; we
    // short-circuit because this test's focus is firm-side status).
    List<String> itemIds = getItemIds(requestId);
    for (String itemId : itemIds) {
      markItemSubmitted(requestId, itemId);
    }

    // Accept each item via the API so audit + lifecycle events fire
    // (the request auto-transitions to COMPLETED when the last
    // required item is accepted).
    for (String itemId : itemIds) {
      mockMvc
          .perform(
              post("/api/information-requests/{id}/items/{itemId}/accept", requestId, itemId)
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(
            get("/api/customers/{id}/fica-status", doneCustomer)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(doneCustomer))
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.lastVerifiedAt").isNotEmpty())
        .andExpect(jsonPath("$.requestId").value(requestId));
  }

  // --- helpers ---

  private String createCustomer() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Fica Customer %s", "email": "fica-%s@test.com", "type": "INDIVIDUAL"}
                        """
                            .formatted(UUID.randomUUID(), UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createPortalContact(String cid, String email, String displayName) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    String contactId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
            .formatted(schema),
        contactId,
        ORG_ID,
        cid,
        email,
        displayName);
    return contactId;
  }

  private String createFicaTemplate() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/request-templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "FICA Onboarding Pack",
                          "description": "FICA onboarding",
                          "items": [
                            {"name": "ID copy", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                            {"name": "Proof of residence", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 1},
                            {"name": "Bank statement", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 2}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    // Back-fill the pack_id column — the public API does not expose it.
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".request_templates SET pack_id = ? WHERE id = ?::uuid".formatted(schema),
        PACK_ID,
        templateId);
    return templateId;
  }

  private String createFicaRequest(String cid, String contactId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner"))
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
                            .formatted(ficaTemplateId, cid, contactId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private void markRequestSent(String requestId) throws Exception {
    mockMvc
        .perform(
            post("/api/information-requests/{id}/send", requestId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
        .andExpect(status().isOk());
  }

  @SuppressWarnings("unchecked")
  private List<String> getItemIds(String requestId) throws Exception {
    var result =
        mockMvc
            .perform(
                get("/api/information-requests/{id}", requestId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_fica_owner")))
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id");
  }

  private void markItemSubmitted(String requestId, String itemId) {
    String schema = SchemaNameGenerator.generateSchemaName(ORG_ID);
    jdbcTemplate.update(
        "UPDATE \"%s\".request_items SET status = 'SUBMITTED', submitted_at = now(), document_id = ?::uuid WHERE id = ?::uuid"
            .formatted(schema),
        UUID.randomUUID().toString(),
        itemId);
  }
}
