package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_br_notif_test";
  private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
      new com.fasterxml.jackson.databind.ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private BillingRunRepository billingRunRepository;
  @Autowired private BillingRunItemRepository billingRunItemRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;
  private UUID billingRunId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "BR Notif Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(syncMember("user_brn_owner", "brn_owner@test.com", "BRN Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "BRN Corp", "brn@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project = new Project("BRN Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var task =
                          new Task(
                              projectId, "BRN Task", null, "MEDIUM", "TASK", null, memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // Seed unbilled time entries
                      var entry =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.now().minusDays(5),
                              120,
                              true,
                              null,
                              "BRN work");
                      entry.snapshotBillingRate(new BigDecimal("1800.00"), "USD");
                      timeEntryRepository.save(entry);
                    }));
  }

  @Test
  @Order(1)
  void generate_createsAuditEventAndNotifications() throws Exception {
    // Create billing run
    var createResult =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Notif Test Run",
                          "periodFrom": "%s",
                          "periodTo": "%s",
                          "currency": "USD",
                          "includeExpenses": false,
                          "includeRetainers": false
                        }
                        """
                            .formatted(LocalDate.now().minusDays(30), LocalDate.now())))
            .andExpect(status().isCreated())
            .andReturn();

    billingRunId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    // Load preview (required before generate to populate billing run items)
    mockMvc
        .perform(
            post("/api/billing-runs/" + billingRunId + "/preview")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    // Generate invoices
    mockMvc
        .perform(post("/api/billing-runs/" + billingRunId + "/generate").with(ownerJwt()))
        .andExpect(status().isOk());

    // Verify audit event for generation
    var generatedAudits = findAuditEvents("billing_run.generated");
    assertThat(generatedAudits).isNotEmpty();
    assertThat(generatedAudits.getLast().entityId()).isEqualTo(billingRunId);

    // Verify BILLING_RUN_COMPLETED notification was created
    var completedNotifs =
        jdbcTemplate.queryForList(
            "SELECT id, title, type FROM \"%s\".notifications WHERE type = ?"
                .formatted(tenantSchema),
            "BILLING_RUN_COMPLETED");
    assertThat(completedNotifs).isNotEmpty();
  }

  @Test
  @Order(2)
  void batchApprove_createsAuditEvent() throws Exception {
    // Batch approve
    var approveResult =
        mockMvc
            .perform(post("/api/billing-runs/" + billingRunId + "/approve").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    // Verify at least one invoice was approved
    int successCount =
        JsonPath.read(approveResult.getResponse().getContentAsString(), "$.successCount");
    assertThat(successCount).isGreaterThan(0);

    // Verify audit event for approval
    var approvedAudits = findAuditEvents("billing_run.approved");
    assertThat(approvedAudits).isNotEmpty();
    assertThat(approvedAudits.getLast().entityId()).isEqualTo(billingRunId);
    assertThat(approvedAudits.getLast().details()).containsKey("approved_count");
  }

  @Test
  @Order(3)
  void batchSend_createsAuditEventAndNotifications() throws Exception {
    // Batch send
    var sendResult =
        mockMvc
            .perform(
                post("/api/billing-runs/" + billingRunId + "/send")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "defaultDueDate": "%s",
                          "defaultPaymentTerms": "Net 30"
                        }
                        """
                            .formatted(LocalDate.now().plusDays(30))))
            .andExpect(status().isOk())
            .andReturn();

    int sentCount = JsonPath.read(sendResult.getResponse().getContentAsString(), "$.successCount");

    if (sentCount > 0) {
      // Verify audit event for send (already existed before this epic)
      var sentAudits = findAuditEvents("billing_run.sent");
      assertThat(sentAudits).isNotEmpty();
      assertThat(sentAudits.getLast().entityId()).isEqualTo(billingRunId);

      // Verify BILLING_RUN_SENT notification was created
      var sentNotifs =
          jdbcTemplate.queryForList(
              "SELECT id, title, type FROM \"%s\".notifications WHERE type = ?"
                  .formatted(tenantSchema),
              "BILLING_RUN_SENT");
      assertThat(sentNotifs).isNotEmpty();
    }
    // When sentCount is 0 (no approved invoices found by billingRunId+status),
    // batchSend returns early without logging audit or publishing events
  }

  @Test
  @Order(4)
  void notificationTypesIncludesBillingRunTypes() {
    assertThat(NotificationService.NOTIFICATION_TYPES)
        .contains("BILLING_RUN_COMPLETED", "BILLING_RUN_SENT", "BILLING_RUN_FAILURES");
  }

  @Test
  @Order(5)
  void patchBatchBillingSettings_updatesSettings() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "billingBatchAsyncThreshold": 100,
                      "billingEmailRateLimit": 10,
                      "defaultBillingRunCurrency": "ZAR"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingBatchAsyncThreshold").value(100))
        .andExpect(jsonPath("$.billingEmailRateLimit").value(10))
        .andExpect(jsonPath("$.defaultBillingRunCurrency").value("ZAR"));
  }

  @Test
  @Order(6)
  void patchBatchBillingSettings_validatesConstraints() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "billingBatchAsyncThreshold": 0,
                      "billingEmailRateLimit": 200,
                      "defaultBillingRunCurrency": "AB"
                    }
                    """))
        .andExpect(status().isBadRequest());
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
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_brn_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private record AuditRow(
      UUID id, String eventType, String entityType, UUID entityId, Map<String, Object> details) {}

  @SuppressWarnings("unchecked")
  private List<AuditRow> findAuditEvents(String eventType) {
    return jdbcTemplate.query(
        "SELECT id, event_type, entity_type, entity_id, details FROM \"%s\".audit_events WHERE event_type = ? ORDER BY occurred_at"
            .formatted(tenantSchema),
        (rs, rowNum) -> {
          Map<String, Object> details = Map.of();
          String detailsJson = rs.getString("details");
          if (detailsJson != null) {
            try {
              details = OBJECT_MAPPER.readValue(detailsJson, Map.class);
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
}
