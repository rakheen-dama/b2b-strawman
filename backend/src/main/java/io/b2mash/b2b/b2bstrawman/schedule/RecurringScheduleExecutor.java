package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled component that executes all due recurring schedules across all tenants. Runs daily at
 * 02:00 UTC. Each tenant is processed independently — errors in one tenant do not affect others.
 *
 * <p>The loop is in the executor (not the service) so that each {@code executeSingleSchedule} call
 * goes through the Spring proxy and the {@code REQUIRES_NEW} transaction propagation takes effect.
 */
@Component
public class RecurringScheduleExecutor {

  private static final Logger log = LoggerFactory.getLogger(RecurringScheduleExecutor.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final RecurringScheduleService scheduleService;

  public RecurringScheduleExecutor(
      OrgSchemaMappingRepository mappingRepository, RecurringScheduleService scheduleService) {
    this.mappingRepository = mappingRepository;
    this.scheduleService = scheduleService;
  }

  @Scheduled(cron = "0 0 2 * * *")
  public void executeSchedules() {
    log.info("Recurring schedule executor started");
    var mappings = mappingRepository.findAll();
    int totalProcessed = 0;
    int totalCreated = 0;

    for (var mapping : mappings) {
      try {
        int[] result =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
                .call(() -> processSchedulesForTenant());
        totalProcessed += result[0];
        totalCreated += result[1];
      } catch (Exception e) {
        log.error("Failed to process schedules for schema {}", mapping.getSchemaName(), e);
      }
    }

    log.info(
        "Recurring schedule executor completed: {} schedules processed, {} projects created",
        totalProcessed,
        totalCreated);
  }

  /**
   * Processes all due schedules for the current tenant. Each schedule is executed in its own
   * transaction via the service proxy, providing error isolation.
   */
  private int[] processSchedulesForTenant() {
    LocalDate today = LocalDate.now();
    var dueSchedules = scheduleService.findDueSchedules();

    int processed = 0;
    int created = 0;

    for (var schedule : dueSchedules) {
      try {
        boolean projectCreated = scheduleService.executeSingleSchedule(schedule, today);
        processed++;
        if (projectCreated) {
          created++;
        }
      } catch (Exception e) {
        log.error("Failed to execute schedule {}: {}", schedule.getId(), e.getMessage(), e);
        processed++;
      }
    }

    return new int[] {processed, created};
  }
}
