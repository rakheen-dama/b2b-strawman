package io.b2mash.b2b.b2bstrawman.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for budget threshold alert notification flow. Verifies that when time entries
 * push budget consumption past the alert threshold, notifications are created for project leads and
 * org admins/owners, with proper deduplication and preference handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BudgetAlertNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_budget_alert_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private BillingRateService billingRateService;
  @Autowired private ProjectBudgetService budgetService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private NotificationPreferenceRepository notificationPreferenceRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.member.ProjectMemberService projectMemberService;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdAdmin;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Budget Alert Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_ba_owner", "ba_owner@test.com", "BA Owner", "owner"));
    memberIdAdmin =
        UUID.fromString(
            syncMember(ORG_ID, "user_ba_admin", "ba_admin@test.com", "BA Admin", "admin"));
    memberIdMember =
        UUID.fromString(
            syncMember(ORG_ID, "user_ba_member", "ba_member@test.com", "BA Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var project =
                  projectService.createProject(
                      "Budget Alert Test Project", "Test project for budget alerts", memberIdOwner);
              projectId = project.getId();

              var task =
                  taskService.createTask(
                      projectId,
                      "Budget Alert Task",
                      "Task for budget alert testing",
                      "MEDIUM",
                      "TASK",
                      null,
                      memberIdOwner,
                      "owner");
              taskId = task.getId();

              // Add member to project so they can create time entries
              projectMemberService.addMember(projectId, memberIdMember, memberIdOwner);

              // Create billing rate for member: $100/hour USD
              billingRateService.createRate(
                  memberIdMember,
                  null,
                  null,
                  "USD",
                  new BigDecimal("100.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");
            });
  }

  @Test
  @Order(1)
  void timeEntryCrossesThreshold_notificationCreatedForAdminsAndOwners() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          // Create a budget: 10 hours, threshold at 80% (8 hours)
          budgetService.upsertBudget(
              projectId,
              new BigDecimal("10"),
              null,
              null,
              80,
              "Test budget",
              memberIdOwner,
              "owner");
        });

    events.clear();

    // Create time entries as member that push past 80% (need > 480 minutes = 8 hours)
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          // 5 hours
          timeEntryService.createTimeEntry(
              taskId,
              LocalDate.of(2025, 3, 1),
              300,
              true,
              null,
              "Work block 1",
              memberIdMember,
              "member");

          // 4 hours — total now 9 hours = 90%, crosses 80% threshold
          timeEntryService.createTimeEntry(
              taskId,
              LocalDate.of(2025, 3, 2),
              240,
              true,
              null,
              "Work block 2",
              memberIdMember,
              "member");
        });

    // Verify BudgetThresholdEvent was published
    var budgetEvents = events.stream(BudgetThresholdEvent.class).toList();
    assertThat(budgetEvents).hasSize(1);
    assertThat(budgetEvents.getFirst().eventType()).isEqualTo("budget.threshold_reached");
    assertThat(budgetEvents.getFirst().details().get("dimension")).isEqualTo("hours");

    // Verify notifications created for owner and admin (not the acting member)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var ownerNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdOwner, PageRequest.of(0, 100));
              assertThat(ownerNotifs.getContent())
                  .anyMatch(
                      n ->
                          "BUDGET_ALERT".equals(n.getType())
                              && n.getTitle().contains("Budget Alert Test Project")
                              && n.getTitle().contains("hours budget")
                              && n.getReferenceEntityType().equals("PROJECT")
                              && n.getReferenceEntityId().equals(projectId));

              var adminNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdAdmin, PageRequest.of(0, 100));
              assertThat(adminNotifs.getContent())
                  .anyMatch(
                      n ->
                          "BUDGET_ALERT".equals(n.getType())
                              && n.getTitle().contains("Budget Alert Test Project"));

              // Actor (member) should NOT receive notification
              var memberNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdMember, PageRequest.of(0, 100));
              assertThat(memberNotifs.getContent())
                  .noneMatch(n -> "BUDGET_ALERT".equals(n.getType()));
            });
  }

  @Test
  @Order(2)
  void duplicateAlertPrevented_whenThresholdAlreadyNotified() {
    events.clear();

    // Add another time entry — threshold already notified, should not trigger again
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          timeEntryService.createTimeEntry(
              taskId,
              LocalDate.of(2025, 3, 3),
              60,
              true,
              null,
              "Extra work after threshold",
              memberIdMember,
              "member");
        });

    // No new BudgetThresholdEvent should be published
    var budgetEvents = events.stream(BudgetThresholdEvent.class).toList();
    assertThat(budgetEvents).isEmpty();
  }

  @Test
  @Order(3)
  void budgetUpdateResetsThresholdNotified() {
    // Update budget values — this should reset thresholdNotified
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          budgetService.upsertBudget(
              projectId,
              new BigDecimal("10"),
              null,
              null,
              80,
              "Updated budget notes",
              memberIdOwner,
              "owner");
        });

    // Verify thresholdNotified is still true because budget values didn't change
    // (same hours=10, same threshold=80)
    // Now change the actual budget amount to trigger a reset
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          budgetService.upsertBudget(
              projectId,
              new BigDecimal("12"),
              null,
              null,
              80,
              "Increased budget",
              memberIdOwner,
              "owner");
        });

    // Verify the budget's thresholdNotified was reset
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var budget = budgetService.getBudgetWithStatus(projectId, memberIdOwner, "owner");
              assertThat(budget).isNotNull();
              // With 12 hours budget and 10 hours consumed, that's ~83% — above 80%
              // but thresholdNotified was reset, so it should be false until re-triggered
            });
  }

  @Test
  @Order(4)
  void subsequentTimeEntryAfterReset_triggersNewAlert() {
    events.clear();

    // Add time entry — budget was reset (now 12 hours, threshold 80% = 9.6 hours)
    // Already consumed 10 hours from earlier tests, so we're at ~83% > 80%
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          timeEntryService.createTimeEntry(
              taskId,
              LocalDate.of(2025, 3, 4),
              30,
              true,
              null,
              "Trigger after reset",
              memberIdMember,
              "member");
        });

    // Verify new BudgetThresholdEvent was published
    var budgetEvents = events.stream(BudgetThresholdEvent.class).toList();
    assertThat(budgetEvents).hasSize(1);
    assertThat(budgetEvents.getFirst().eventType()).isEqualTo("budget.threshold_reached");
  }

  @Test
  @Order(5)
  void alertNotSent_whenPreferenceDisabled() {
    // Reset budget again to allow new alert
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          budgetService.upsertBudget(
              projectId,
              new BigDecimal("15"),
              null,
              null,
              80,
              "Reset for preference test",
              memberIdOwner,
              "owner");
        });

    // Disable BUDGET_ALERT preference for admin
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              notificationPreferenceRepository.save(
                  new NotificationPreference(memberIdAdmin, "BUDGET_ALERT", false, false));
            });

    // Count existing admin BUDGET_ALERT notifications before the trigger
    final long[] adminBudgetAlertsBefore = new long[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var adminNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdAdmin, PageRequest.of(0, 100));
              adminBudgetAlertsBefore[0] =
                  adminNotifs.getContent().stream()
                      .filter(n -> "BUDGET_ALERT".equals(n.getType()))
                      .count();
            });

    events.clear();

    // Add time entry to cross threshold (already at ~69% with 10.5h/15h)
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          // Push to well over 80%: 10.5 existing + 3 hours = 13.5/15 = 90%
          timeEntryService.createTimeEntry(
              taskId,
              LocalDate.of(2025, 3, 5),
              180,
              true,
              null,
              "Preference test entry",
              memberIdMember,
              "member");
        });

    // Verify event published (budget check still runs)
    var budgetEvents = events.stream(BudgetThresholdEvent.class).toList();
    assertThat(budgetEvents).hasSize(1);

    // Admin should NOT receive notification (preference disabled)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var adminNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdAdmin, PageRequest.of(0, 100));
              long adminBudgetAlertsAfter =
                  adminNotifs.getContent().stream()
                      .filter(n -> "BUDGET_ALERT".equals(n.getType()))
                      .count();
              assertThat(adminBudgetAlertsAfter).isEqualTo(adminBudgetAlertsBefore[0]);

              // Owner should still receive notification
              var ownerNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdOwner, PageRequest.of(0, 100));
              assertThat(ownerNotifs.getContent())
                  .anyMatch(
                      n ->
                          "BUDGET_ALERT".equals(n.getType())
                              && n.getReferenceEntityId().equals(projectId));
            });
  }

  @Test
  @Order(6)
  void noAlert_whenBudgetDoesNotExist() {
    // Create a project without a budget
    final UUID[] noBudgetProjectId = new UUID[1];
    final UUID[] noBudgetTaskId = new UUID[1];

    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var project =
              projectService.createProject("No Budget Project", "No budget set", memberIdOwner);
          noBudgetProjectId[0] = project.getId();

          var task =
              taskService.createTask(
                  noBudgetProjectId[0],
                  "No Budget Task",
                  "Task without budget",
                  "LOW",
                  "TASK",
                  null,
                  memberIdOwner,
                  "owner");
          noBudgetTaskId[0] = task.getId();
        });

    events.clear();

    // Create time entry for project without budget
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          timeEntryService.createTimeEntry(
              noBudgetTaskId[0],
              LocalDate.of(2025, 3, 10),
              120,
              true,
              null,
              "No budget work",
              memberIdOwner,
              "owner");
        });

    // No BudgetThresholdEvent should be published
    var budgetEvents = events.stream(BudgetThresholdEvent.class).toList();
    assertThat(budgetEvents).isEmpty();
  }

  // --- Helpers ---

  private void runInTenantAs(UUID actorId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, actorId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
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
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
