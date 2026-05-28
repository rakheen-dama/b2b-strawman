package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

  private final TenantScopedRunner tenantScopedRunner;
  private final RecurringScheduleService scheduleService;
  private final JobEnqueuer jobEnqueuer;
  private final JobQueueProperties jobQueueProperties;

  public RecurringScheduleExecutor(
      TenantScopedRunner tenantScopedRunner,
      RecurringScheduleService scheduleService,
      JobEnqueuer jobEnqueuer,
      JobQueueProperties jobQueueProperties) {
    this.tenantScopedRunner = tenantScopedRunner;
    this.scheduleService = scheduleService;
    this.jobEnqueuer = jobEnqueuer;
    this.jobQueueProperties = jobQueueProperties;
  }

  @SchedulerLock(name = "recurring_schedule_execute_schedules", lockAtLeastFor = "5m")
  @Scheduled(cron = "0 0 2 * * *")
  public void executeSchedules() {
    log.info("Recurring schedule executor started");

    if (jobQueueProperties.isDualMode("recurring_schedule_execute")) {
      int[] totals = {0, 0}; // [processed, created]
      tenantScopedRunner.forEachTenant(
          (tenantId, orgId) -> {
            int[] result = processSchedulesForTenant();
            totals[0] += result[0];
            totals[1] += result[1];
          });

      log.info(
          "Recurring schedule executor completed: {} schedules processed, {} projects created",
          totals[0],
          totals[1]);
    }

    jobEnqueuer.fanOutToAllTenants("recurring_schedule_execute", null);
  }

  /**
   * Processes all due schedules for the current tenant. Each schedule is executed in its own
   * transaction via the service proxy, providing error isolation.
   */
  int[] processSchedulesForTenant() {
    var dueSchedules = scheduleService.findDueSchedules();

    int processed = 0;
    int created = 0;

    for (var schedule : dueSchedules) {
      try {
        boolean projectCreated = scheduleService.executeSingleSchedule(schedule);
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
