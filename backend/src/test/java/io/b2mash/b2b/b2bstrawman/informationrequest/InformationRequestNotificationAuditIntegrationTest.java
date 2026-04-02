package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InformationRequestNotificationAuditIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inforeq_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String memberIdOwner;
  private String customerId;
  private String portalContactId;
  private String projectId;
  private String requestId;
  private String itemId;
  private String schema;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "InfoReq Notif Test Org", null);
    schema = SchemaNameGenerator.generateSchemaName(ORG_ID);

    memberIdOwner = syncMember("user_notif_owner", "notif_owner@test.com", "Notif Owner", "owner");

    // Create active customer for project creation
    customerId = createActiveCustomer(ownerJwt());

    // Create portal contact
    portalContactId = createPortalContact(customerId, "portal@test.com", "Portal User");

    // Create project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Test Project", "customerId": "%s"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id").toString();
  }

  @Test
  @Order(1)
  void auditEventCreatedOnRequestCreation() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "projectId": "%s",
                          "portalContactId": "%s",
                          "items": [
                            {"name": "Bank Statements", "description": "Jan-Dec", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0},
                            {"name": "Tax Number", "description": "Company tax number", "responseType": "TEXT_RESPONSE", "required": true, "sortOrder": 1}
                          ]
                        }
                        """
                            .formatted(customerId, projectId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    var auditEvents = findAuditEvents("information_request.created");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.entityId()).isEqualTo(UUID.fromString(requestId));
    assertThat(event.details()).containsKey("request_number");
    assertThat(event.details()).containsKey("customer_id");
  }

  @Test
  @Order(2)
  void auditEventDetailsContainProjectId() {
    var auditEvents = findAuditEvents("information_request.created");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.details()).containsKey("project_id");
    assertThat(event.details().get("project_id")).isEqualTo(projectId);
  }

  @Test
  @Order(3)
  void auditEventCreatedOnSend() throws Exception {
    mockMvc
        .perform(post("/api/information-requests/%s/send".formatted(requestId)).with(ownerJwt()))
        .andExpect(status().isOk());

    var auditEvents = findAuditEvents("information_request.sent");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.entityId()).isEqualTo(UUID.fromString(requestId));
    assertThat(event.details()).containsKey("project_id");
  }

  @Test
  @Order(4)
  void auditEventCreatedOnItemAccept() throws Exception {
    // Look up item IDs
    var items = findRequestItems(requestId);
    assertThat(items).hasSizeGreaterThanOrEqualTo(2);
    itemId = items.getFirst().get("id").toString();

    // Simulate item submission via JDBC (portal flow)
    simulateItemSubmission(requestId, itemId);

    mockMvc
        .perform(
            post("/api/information-requests/%s/items/%s/accept".formatted(requestId, itemId))
                .with(ownerJwt()))
        .andExpect(status().isOk());

    var auditEvents = findAuditEvents("information_request.item_accepted");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.entityId()).isEqualTo(UUID.fromString(itemId));
    assertThat(event.details()).containsKey("item_name");
    assertThat(event.details()).containsKey("project_id");
  }

  @Test
  @Order(5)
  void auditEventCreatedOnItemReject() throws Exception {
    var items = findRequestItems(requestId);
    String secondItemId = items.get(1).get("id").toString();

    // Simulate item submission
    simulateItemSubmission(requestId, secondItemId);

    mockMvc
        .perform(
            post("/api/information-requests/%s/items/%s/reject".formatted(requestId, secondItemId))
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Incomplete data\"}"))
        .andExpect(status().isOk());

    var auditEvents = findAuditEvents("information_request.item_rejected");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.details()).containsKey("reason");
    assertThat(event.details().get("reason")).isEqualTo("Incomplete data");
    assertThat(event.details()).containsKey("project_id");
  }

  @Test
  @Order(6)
  void auditEventCreatedOnCompletion() throws Exception {
    // Re-submit and accept the second (rejected) item to trigger auto-complete
    var items = findRequestItems(requestId);
    String secondItemId = items.get(1).get("id").toString();
    simulateItemSubmission(requestId, secondItemId);

    mockMvc
        .perform(
            post("/api/information-requests/%s/items/%s/accept".formatted(requestId, secondItemId))
                .with(ownerJwt()))
        .andExpect(status().isOk());

    var auditEvents = findAuditEvents("information_request.completed");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.details()).containsKey("auto_completed");
    assertThat(event.details()).containsKey("project_id");
  }

  @Test
  @Order(7)
  void auditEventCreatedOnCancel() throws Exception {
    // Create a new request to cancel
    var result =
        mockMvc
            .perform(
                post("/api/information-requests")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "projectId": "%s",
                          "portalContactId": "%s",
                          "items": [
                            {"name": "Cancel Test Item", "responseType": "FILE_UPLOAD", "required": true, "sortOrder": 0}
                          ]
                        }
                        """
                            .formatted(customerId, projectId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String cancelRequestId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    // Send then cancel
    mockMvc
        .perform(
            post("/api/information-requests/%s/send".formatted(cancelRequestId)).with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/information-requests/%s/cancel".formatted(cancelRequestId)).with(ownerJwt()))
        .andExpect(status().isOk());

    var auditEvents = findAuditEvents("information_request.cancelled");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getLast();
    assertThat(event.entityId()).isEqualTo(UUID.fromString(cancelRequestId));
    assertThat(event.details()).containsKey("project_id");
  }

  @Test
  @Order(8)
  void auditEventDetailsContainCorrectJsonbFields() {
    // The creation audit event should have all expected fields
    var auditEvents = findAuditEvents("information_request.created");
    assertThat(auditEvents).isNotEmpty();
    var event = auditEvents.getFirst();
    assertThat(event.details()).containsKey("request_number");
    assertThat(event.details()).containsKey("customer_id");
    assertThat(event.entityType()).isEqualTo("information_request");
  }

  @Test
  @Order(9)
  void activityFeedReturnsRequestEventsForProject() throws Exception {
    mockMvc
        .perform(get("/api/projects/%s/activity".formatted(projectId)).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  @Order(10)
  void activityMessageFormattedCorrectly() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/projects/%s/activity".formatted(projectId)).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String content = result.getResponse().getContentAsString();
    List<String> messages = JsonPath.read(content, "$.content[*].message");
    // Should contain at least one information request related message
    assertThat(messages)
        .anyMatch(
            m ->
                m.contains("information request")
                    || m.contains("accepted")
                    || m.contains("rejected")
                    || m.contains("completed"));
  }

  @Test
  @Order(11)
  void inAppNotificationCreatedOnCompletion() {
    // The completed event should have created an in-app notification for the request creator
    var notifications =
        jdbcTemplate.queryForList(
            "SELECT id, title, type FROM \"%s\".notifications WHERE recipient_member_id = ?::uuid AND type = ?"
                .formatted(schema),
            memberIdOwner,
            "INFORMATION_REQUEST_COMPLETED");
    assertThat(notifications).isNotEmpty();
    assertThat(notifications.getLast().get("title").toString()).contains("completed");
  }

  @Test
  @Order(12)
  void inAppNotificationForItemSubmittedRequiresPortalEvent() {
    // The RequestItemSubmittedEvent is published from PortalInformationRequestService,
    // which is not exercised in this test (we simulate via JDBC).
    // Verify the notification type is registered.
    assertThat(io.b2mash.b2b.b2bstrawman.notification.NotificationService.NOTIFICATION_TYPES)
        .contains("INFORMATION_REQUEST_ITEM_SUBMITTED");
    assertThat(io.b2mash.b2b.b2bstrawman.notification.NotificationService.NOTIFICATION_TYPES)
        .contains("INFORMATION_REQUEST_COMPLETED");
    assertThat(io.b2mash.b2b.b2bstrawman.notification.NotificationService.NOTIFICATION_TYPES)
        .contains("INFORMATION_REQUEST_DRAFT_CREATED");
  }

  // ========== Helpers ==========

  private record AuditRow(
      UUID id, String eventType, String entityType, UUID entityId, Map<String, Object> details) {}

  @SuppressWarnings("unchecked")
  private List<AuditRow> findAuditEvents(String eventType) {
    return jdbcTemplate.query(
        "SELECT id, event_type, entity_type, entity_id, details FROM \"%s\".audit_events WHERE event_type = ? ORDER BY occurred_at"
            .formatted(schema),
        (rs, rowNum) -> {
          Map<String, Object> details = Map.of();
          String detailsJson = rs.getString("details");
          if (detailsJson != null) {
            try {
              details =
                  new com.fasterxml.jackson.databind.ObjectMapper()
                      .readValue(detailsJson, Map.class);
            } catch (Exception ignored) {
            }
          }
          return new AuditRow(
              UUID.fromString(rs.getString("id")),
              rs.getString("event_type"),
              rs.getString("entity_type"),
              UUID.fromString(rs.getString("entity_id")),
              details);
        },
        eventType);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> findRequestItems(String reqId) {
    return jdbcTemplate.queryForList(
        "SELECT id, name, status FROM \"%s\".request_items WHERE request_id = ?::uuid ORDER BY sort_order"
            .formatted(schema),
        reqId);
  }

  private void simulateItemSubmission(String reqId, String itemIdToSubmit) {
    jdbcTemplate.update(
        "UPDATE \"%s\".request_items SET status = 'SUBMITTED', submitted_at = now(), document_id = ?::uuid WHERE id = ?::uuid"
            .formatted(schema),
        UUID.randomUUID().toString(),
        itemIdToSubmit);
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
                        {"name": "Notif Test Customer", "email": "notif-cust@test.com", "type": "INDIVIDUAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String cid = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    // Transition PROSPECT -> ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + cid + "/transition")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Complete all checklist items (auto-transitions ONBOARDING -> ACTIVE)
    TestChecklistHelper.completeChecklistItems(mockMvc, cid, jwt);
    return cid;
  }

  private String createPortalContact(String custId, String email, String displayName) {
    String contactId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
            .formatted(schema),
        contactId,
        ORG_ID,
        custId,
        email,
        displayName);
    return contactId;
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
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_notif_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }
}
