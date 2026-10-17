package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeReminderHandler;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replacement-path test for Epic 600 (Phase 86 slice 6): Phase 16/30 recurring project schedules
 * are gone, the Settings → Time Tracking reminder survives (re-homed to {@code timeentry}). The
 * notification-preference catalogue a legal-za owner sees still carries every legal type and none
 * of the 17 types removed across slices 2–6; the schedule routes are plain 404s; the schedule
 * tables and the schedule-pack status column are dropped (V141).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationCatalogueWithoutSchedulesTest {
  private static final String ORG_ID = "org_notif_catalogue_no_schedules";
  private static final String OWNER = "user_ncws_owner";

  private static final List<String> LEGAL_TYPES =
      List.of(
          "COURT_DATE_REMINDER",
          "PRESCRIPTION_WARNING",
          "TRUST_PAYMENT_AWAITING_APPROVAL",
          "TRUST_PAYMENT_APPROVED",
          "TRUST_PAYMENT_REJECTED",
          "TRUST_RECONCILIATION_OVERDUE",
          "TRUST_INVESTMENT_MATURING",
          "TRUST_APPROVAL_AGING",
          "TRUST_RECONCILIATION_COMPLETED",
          "TRUST_INTEREST_POSTED",
          "MATTER_CLOSED",
          "MATTER_REOPENED",
          "TIME_REMINDER",
          "BILLING_RUN_COMPLETED",
          "BILLING_RUN_SENT",
          "BILLING_RUN_FAILURES",
          "INFORMATION_REQUEST_ITEM_SUBMITTED",
          "INFORMATION_REQUEST_COMPLETED",
          "INFORMATION_REQUEST_DRAFT_CREATED");

  /**
   * The 17 types removed by Phase 86 slices 2–6 (schedule 4, retainer 5, proposal 4, deal 1,
   * capacity 3).
   */
  private static final List<String> REMOVED_TYPES =
      List.of(
          // schedule (Epic 600)
          "RECURRING_PROJECT_CREATED",
          "SCHEDULE_SKIPPED",
          "SCHEDULE_COMPLETED",
          "POST_CREATE_ACTION_FAILED",
          // retainer (Epic 598)
          "RETAINER_PERIOD_READY_TO_CLOSE",
          "RETAINER_PERIOD_CLOSED",
          "RETAINER_APPROACHING_CAPACITY",
          "RETAINER_FULLY_CONSUMED",
          "RETAINER_TERMINATED",
          // proposal (Epic 597)
          "PROPOSAL_SENT",
          "PROPOSAL_ACCEPTED",
          "PROPOSAL_DECLINED",
          "PROPOSAL_EXPIRED",
          // deal (Epic 596)
          "DEAL_WON",
          // capacity (Epic 599)
          "ALLOCATION_CHANGED",
          "MEMBER_OVER_ALLOCATED",
          "LEAVE_CREATED");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private DataSource dataSource;
  @Autowired private List<JobHandler> jobHandlers;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Notification Catalogue Without Schedules Org", "legal-za")
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "ncws_owner@test.com", "NCWS Owner", "owner");
  }

  @Test
  void preferenceCatalogueKeepsEveryLegalTypeAndNoneOfTheRemovedTypes() throws Exception {
    assertThat(REMOVED_TYPES).hasSize(17).doesNotHaveDuplicates();
    var result =
        mockMvc
            .perform(
                get("/api/notifications/preferences").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.preferences", hasSize(NotificationService.NOTIFICATION_TYPES.size())))
            .andExpect(
                jsonPath(
                    "$.preferences[*].notificationType",
                    hasItems(LEGAL_TYPES.toArray(String[]::new))));
    for (String removed : REMOVED_TYPES) {
      result.andExpect(jsonPath("$.preferences[*].notificationType", not(hasItem(removed))));
    }
    assertThat(NotificationService.NOTIFICATION_TYPES)
        .containsAll(LEGAL_TYPES)
        .doesNotContainAnyElementsOf(REMOVED_TYPES);
  }

  @Test
  void scheduleRoutesAreGone() throws Exception {
    // Were RecurringScheduleController (@RequestMapping("/api/schedules")). Unmapped → 404.
    UUID id = UUID.randomUUID();
    for (String path :
        List.of("/api/schedules", "/api/schedules/" + id, "/api/schedules/" + id + "/executions")) {
      mockMvc
          .perform(get(path).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void scheduleSchemaObjectsAreDropped() {
    var jdbc = new JdbcTemplate(dataSource);
    for (String table : List.of("recurring_schedules", "schedule_executions")) {
      String regclass =
          jdbc.queryForObject(
              "SELECT to_regclass(?)::text", String.class, tenantSchema + "." + table);
      assertThat(regclass).as(table).isNull();
    }
    Integer column =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'org_settings' AND column_name = 'schedule_pack_status'",
            Integer.class,
            tenantSchema);
    assertThat(column).isZero();
  }

  @Test
  void timeReminderIsRehomedAndRecurringScheduleJobIsGone() {
    assertThat(jobHandlers)
        .filteredOn(h -> h instanceof TimeReminderHandler)
        .singleElement()
        .extracting(JobHandler::jobType)
        .isEqualTo("time_reminder_check");
    assertThat(jobHandlers).noneMatch(h -> "recurring_schedule_execute".equals(h.jobType()));
    assertThat(TimeReminderHandler.class.getPackageName())
        .isEqualTo("io.b2mash.b2b.b2bstrawman.timeentry");
  }

  @Test
  void schedulePackageIsAbsentFromTheClasspath() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    // Non-vacuous: the importer must actually see production classes.
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.timeentry")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.schedule")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.schedule."));
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder"))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
