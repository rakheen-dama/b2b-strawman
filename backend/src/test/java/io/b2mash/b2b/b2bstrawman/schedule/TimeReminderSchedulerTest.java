package io.b2mash.b2b.b2bstrawman.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeReminderSchedulerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_time_reminder_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TimeReminderScheduler scheduler;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private NotificationPreferenceRepository notificationPreferenceRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Time Reminder Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_reminder_owner", "reminder@test.com", "Reminder Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a project and task so we can log time entries
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      projectRepository.save(new Project("Test Project", "desc", memberId));
                  var task =
                      taskRepository.save(
                          new Task(
                              project.getId(),
                              "Test Task",
                              "desc",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberId));
                  taskId = task.getId();
                }));
  }

  @BeforeEach
  void cleanTestData() {
    // Clear notifications, preferences, and time entries before each test
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  notificationRepository.deleteAll();
                  notificationPreferenceRepository.deleteAll();
                  timeEntryRepository.deleteAll();
                }));
  }

  @Test
  void reminderNotSent_whenOrgDisabled() {
    // Setup: reminders disabled
    configureOrgSettings(false, todayDayAbbrev(), currentUtcTime(), 240);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var count =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getTotalElements();
                  assertThat(count).isZero();
                }));
  }

  @Test
  void reminderNotSent_whenNotWorkingDay() {
    // Setup: enabled but today is not a working day
    String notToday = nonTodayDayAbbrev();
    configureOrgSettings(true, notToday, currentUtcTime(), 240);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var count =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getTotalElements();
                  assertThat(count).isZero();
                }));
  }

  @Test
  void reminderSent_whenMemberBelowThreshold() {
    // Setup: enabled, today is working day, member has 0 minutes logged
    configureOrgSettings(true, todayDayAbbrev(), currentUtcTime(), 240);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getContent();
                  assertThat(notifications).hasSize(1);
                  assertThat(notifications.getFirst().getType()).isEqualTo("TIME_REMINDER");
                }));
  }

  @Test
  void reminderNotSent_whenMemberAboveThreshold() {
    // Setup: member has logged enough time (300 minutes > 240 threshold)
    configureOrgSettings(true, todayDayAbbrev(), currentUtcTime(), 240);
    logTimeEntry(300);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var count =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getTotalElements();
                  assertThat(count).isZero();
                }));
  }

  @Test
  void reminderNotSent_whenMemberOptedOut() {
    // Setup: member opted out of TIME_REMINDER notifications
    configureOrgSettings(true, todayDayAbbrev(), currentUtcTime(), 240);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    notificationPreferenceRepository.save(
                        new NotificationPreference(memberId, "TIME_REMINDER", false, false))));

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var count =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getTotalElements();
                  assertThat(count).isZero();
                }));
  }

  @Test
  void reminderNotSent_whenNotInTimeWindow() {
    // Setup: reminder time is far from current UTC time
    var farAwayTime = LocalTime.now(ZoneOffset.UTC).plusHours(3);
    configureOrgSettings(true, todayDayAbbrev(), farAwayTime, 240);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var count =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getTotalElements();
                  assertThat(count).isZero();
                }));
  }

  @Test
  void reminderSent_includesHoursLogged_inMessage() {
    // Setup: member has 60 minutes logged (1.0 hours), threshold is 240 (4.0 hours)
    configureOrgSettings(true, todayDayAbbrev(), currentUtcTime(), 240);
    logTimeEntry(60);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getContent();
                  assertThat(notifications).hasSize(1);
                  var notification = notifications.getFirst();
                  assertThat(notification.getTitle()).contains("1.0").contains("4.0");
                  assertThat(notification.getBody()).isNotBlank();
                }));
  }

  @Test
  void notificationCreated_withCorrectType() {
    configureOrgSettings(true, todayDayAbbrev(), currentUtcTime(), 240);

    scheduler.checkTimeReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications =
                      notificationRepository
                          .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
                          .getContent();
                  assertThat(notifications).hasSize(1);
                  var notification = notifications.getFirst();
                  assertThat(notification.getType()).isEqualTo("TIME_REMINDER");
                  assertThat(notification.getRecipientMemberId()).isEqualTo(memberId);
                  assertThat(notification.getReferenceEntityType()).isNull();
                  assertThat(notification.getReferenceEntityId()).isNull();
                }));
  }

  // --- Helpers ---

  private void configureOrgSettings(boolean enabled, String days, LocalTime time, int minMinutes) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings =
                      orgSettingsRepository
                          .findForCurrentTenant()
                          .orElseGet(() -> new OrgSettings("ZAR"));
                  settings.updateTimeReminderSettings(enabled, days, time, minMinutes);
                  orgSettingsRepository.save(settings);
                }));
  }

  private void logTimeEntry(int durationMinutes) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryRepository.save(
                        new TimeEntry(
                            taskId,
                            memberId,
                            LocalDate.now(ZoneOffset.UTC),
                            durationMinutes,
                            false,
                            null,
                            "test entry"))));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
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
                    {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                    """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }

  /** Returns the 3-letter abbreviation for today's day of week (e.g. "MON", "TUE"). */
  private String todayDayAbbrev() {
    return LocalDate.now(ZoneOffset.UTC)
        .getDayOfWeek()
        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        .toUpperCase()
        .substring(0, 3);
  }

  /** Returns the current UTC time (for aligning the time window). */
  private LocalTime currentUtcTime() {
    return LocalTime.now(ZoneOffset.UTC);
  }

  /** Returns a day abbreviation that is NOT today. */
  private String nonTodayDayAbbrev() {
    var today = LocalDate.now(ZoneOffset.UTC).getDayOfWeek();
    var otherDay = today == DayOfWeek.MONDAY ? DayOfWeek.TUESDAY : DayOfWeek.MONDAY;
    return otherDay.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase().substring(0, 3);
  }
}
