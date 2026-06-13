package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression guard for the OBS-505 cross-package cascade: a seeded {@code INVOKE_AI_SPECIALIST}
 * automation on the {@code INFORMATION_REQUEST_COMPLETED} trigger fires synchronously when the last
 * required item of an information request is accepted. In the absence of a configured AI provider
 * (the default for a freshly provisioned tenant) that action's runner throws from an
 * {@code @Transactional} integration guard. Before the fix, that nested rollback-only marking
 * poisoned the triggering {@code acceptItem} transaction, surfacing as {@code
 * UnexpectedRollbackException} so the request never persisted as COMPLETED.
 *
 * <p>The invariant under test: a side-effecting automation action that throws must NEVER roll back
 * the business transaction that triggered the domain event. Accepting the final required item must
 * still complete the request (HTTP 200 + status COMPLETED), and the automation failure must be
 * recorded as a soft failure (FAILED automation execution + {@code AUTOMATION_ACTION_FAILED}
 * notification) — not a rollback. The isolation lives in {@code AutomationActionExecutor}, which
 * runs each action in its own {@code REQUIRES_NEW} transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InformationRequestAiAutomationIsolationIntegrationTest {

  private static final String ORG_ID = "org_inforeq_ai_isolation_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  @Autowired private RequestItemRepository requestItemRepository;
  @Autowired private PortalContactRepository portalContactRepository;

  private String memberIdOwner;
  private String customerId;
  private String portalContactId;
  private String projectId;
  private String schema;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    // Default provisioning seeds the legal-za automation pack, which includes an
    // INVOKE_AI_SPECIALIST action on the INFORMATION_REQUEST_COMPLETED trigger. No AI provider is
    // configured, so the action's runner throws when it fires — exactly the OBS-505 cascade.
    provisioningService.provisionTenant(ORG_ID, "InfoReq AI Isolation Test Org", null);
    schema = SchemaNameGenerator.generateSchemaName(ORG_ID);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_iso_owner", "iso_owner@test.com", "Iso Owner", "owner");

    customerId = createActiveCustomer(TestJwtFactory.ownerJwt(ORG_ID, "user_iso_owner"));
    portalContactId = createPortalContact(customerId, "iso-portal@test.com", "Iso Portal User");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_iso_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Iso Test Project", "customerId": "%s"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id").toString();
  }

  @Test
  void acceptingFinalItem_whenAiActionThrows_stillCompletesAndRecordsSoftFailure()
      throws Exception {
    // Sanity check: this tenant actually has an enabled INFORMATION_REQUEST_COMPLETED automation
    // with an INVOKE_AI_SPECIALIST action — otherwise the test would pass vacuously.
    Long aiRules =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM \"%s\".automation_rules r JOIN \"%s\".automation_actions a"
                    .formatted(schema, schema)
                + " ON a.rule_id = r.id WHERE r.enabled = true AND r.trigger_type ="
                + " 'INFORMATION_REQUEST_COMPLETED' AND a.action_type = 'INVOKE_AI_SPECIALIST'",
            Long.class);
    assertThat(aiRules)
        .as("tenant must have a seeded INVOKE_AI_SPECIALIST rule on INFORMATION_REQUEST_COMPLETED")
        .isNotNull()
        .isGreaterThanOrEqualTo(1L);

    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_iso_owner");

    // Create a request with a single required item, send it, submit the item.
    var createResult =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "projectId": "%s",
                          "portalContactId": "%s",
                          "items": [
                            {"name": "Bank Statements", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0}
                          ]
                        }
                        """
                            .formatted(customerId, projectId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String requestId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(post("/api/information-requests/%s/send".formatted(requestId)).with(jwt))
        .andExpect(status().isOk());

    var items = findRequestItems(requestId);
    assertThat(items).hasSize(1);
    String itemId = items.getFirst().get("id").toString();
    simulateItemSubmission(requestId, itemId);

    long failedExecsBefore = countFailedAutomationExecutions();

    // Accept the last required item. This auto-completes the request and fires the AI automation,
    // which throws. The request must STILL complete (no UnexpectedRollbackException).
    mockMvc
        .perform(
            post("/api/information-requests/%s/items/%s/accept".formatted(requestId, itemId))
                .with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // The request is durably COMPLETED (re-read it).
    mockMvc
        .perform(get("/api/information-requests/%s".formatted(requestId)).with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // The completion audit event was written (triggering transaction committed cleanly).
    Long completedAudits =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM \"%s\".audit_events WHERE event_type =".formatted(schema)
                + " 'information_request.completed' AND entity_id = ?::uuid",
            Long.class,
            requestId);
    assertThat(completedAudits).isNotNull().isGreaterThanOrEqualTo(1L);

    // The automation failure was recorded as a SOFT failure (not a rollback): a FAILED automation
    // execution and an AUTOMATION_ACTION_FAILED notification both persisted in the outer tx.
    assertThat(countFailedAutomationExecutions())
        .as("AI automation action failure should be recorded as a FAILED execution")
        .isGreaterThan(failedExecsBefore);

    List<Map<String, Object>> failureNotifications =
        jdbcTemplate.queryForList(
            "SELECT id, type FROM \"%s\".notifications WHERE type = ?".formatted(schema),
            "AUTOMATION_ACTION_FAILED");
    assertThat(failureNotifications)
        .as("a soft AUTOMATION_ACTION_FAILED notification should be recorded")
        .isNotEmpty();
  }

  private long countFailedAutomationExecutions() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM \"%s\".automation_executions WHERE status = 'ACTIONS_FAILED'"
                .formatted(schema),
            Long.class);
    return count == null ? 0L : count;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> findRequestItems(String reqId) {
    return jdbcTemplate.queryForList(
        "SELECT id, name, status FROM \"%s\".request_items WHERE request_id = ?::uuid ORDER BY sort_order"
            .formatted(schema),
        reqId);
  }

  private void simulateItemSubmission(String reqId, String itemIdToSubmit) {
    // Mirror the repository-driven submission pattern used by
    // InformationRequestReadModelSyncIntegrationTest: load the RequestItem through its repository
    // inside the tenant ScopedValue and drive the domain transition (PENDING -> SUBMITTED) via
    // RequestItem.submit(...), rather than fabricating state with raw SQL.
    runInTenant(
        () -> {
          var item = requestItemRepository.findById(UUID.fromString(itemIdToSubmit)).orElseThrow();
          item.submit(UUID.randomUUID());
          requestItemRepository.save(item);
        });
  }

  private String createActiveCustomer(JwtRequestPostProcessor jwt) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Iso Test Customer", "email": "iso-cust@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String cid = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    mockMvc
        .perform(
            post("/api/customers/" + cid + "/transition")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.transitionToActive(mockMvc, cid, jwt);
    return cid;
  }

  private String createPortalContact(String custId, String email, String displayName) {
    // Mirror InformationRequestReadModelSyncIntegrationTest: persist a real PortalContact through
    // its repository inside the tenant ScopedValue, rather than hand-rolling a raw SQL INSERT.
    final UUID[] contactId = new UUID[1];
    runInTenant(
        () -> {
          var contact =
              new PortalContact(
                  ORG_ID,
                  UUID.fromString(custId),
                  email,
                  displayName,
                  PortalContact.ContactRole.PRIMARY);
          contactId[0] = portalContactRepository.save(contact).getId();
        });
    return contactId[0].toString();
  }

  /** Runs the action with the tenant ScopedValue bound to this test's provisioned schema. */
  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }
}
