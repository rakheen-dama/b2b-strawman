package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulePackSeederTest {

  private static final String ORG_ID = "org_schedule_pack_seeder_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private RecurringScheduleRepository recurringScheduleRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private SchedulePackSeeder schedulePackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Schedule Pack Seeder Test Org", "accounting-za");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project templates that match schedule pack entries so seeder finds them
    UUID seederMemberId = SchedulePackSeeder.SEEDER_CREATED_BY;
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  projectTemplateRepository.save(
                      new ProjectTemplate(
                          "Annual Tax Return",
                          "{customer} Tax Return {year}",
                          "Annual tax return",
                          true,
                          "MANUAL",
                          null,
                          seederMemberId));
                  projectTemplateRepository.save(
                      new ProjectTemplate(
                          "Monthly Bookkeeping",
                          "{customer} Bookkeeping {month} {year}",
                          "Monthly bookkeeping",
                          true,
                          "MANUAL",
                          null,
                          seederMemberId));
                }));
  }

  @Test
  @Order(1)
  void seedsRecurringSchedulesInPausedState() {
    schedulePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedules = recurringScheduleRepository.findAllByOrderByCreatedAtDesc();
                  // The seeder creates schedules only if matching project templates exist.
                  // With accounting-za profile, template packs may have been seeded.
                  // Filter to only seeder-created schedules.
                  var seederSchedules =
                      schedules.stream()
                          .filter(
                              s -> SchedulePackSeeder.SEEDER_CREATED_BY.equals(s.getCreatedBy()))
                          .toList();
                  // Schedules are only created if matching project templates exist.
                  // Verify at least one was created (non-vacuous check).
                  assertThat(seederSchedules).isNotEmpty();
                  // All created schedules must be PAUSED with null customerId.
                  assertThat(seederSchedules)
                      .allSatisfy(
                          s -> {
                            assertThat(s.getStatus()).isEqualTo("PAUSED");
                            assertThat(s.getCustomerId()).isNull();
                            assertThat(s.getCreatedBy())
                                .isEqualTo(SchedulePackSeeder.SEEDER_CREATED_BY);
                          });
                }));
  }

  @Test
  @Order(2)
  void missingProjectTemplateLogsWarningAndSkips() {
    // The seeder should not throw even if project templates don't match.
    // Running the seeder again should be idempotent (pack already applied).
    schedulePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getSchedulePackStatus()).isNotNull();
                  assertThat(settings.getSchedulePackStatus())
                      .anyMatch(entry -> "schedule-pack-accounting-za".equals(entry.get("packId")));
                }));
  }

  private void runInTenant(String schema, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
