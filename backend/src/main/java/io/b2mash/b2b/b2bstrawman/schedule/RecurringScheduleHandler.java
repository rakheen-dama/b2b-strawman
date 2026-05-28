package io.b2mash.b2b.b2bstrawman.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for executing due recurring schedules for a single tenant. Delegates to {@link
 * RecurringScheduleExecutor#processSchedulesForTenant()} which finds due schedules and executes
 * each in its own transaction.
 *
 * <p>Extracted from {@link RecurringScheduleExecutor#executeSchedules()}.
 */
@Component
public class RecurringScheduleHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(RecurringScheduleHandler.class);

  private final RecurringScheduleExecutor executor;

  public RecurringScheduleHandler(RecurringScheduleExecutor executor) {
    this.executor = executor;
  }

  @Override
  public String jobType() {
    return "recurring_schedule_execute";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int[] result = executor.processSchedulesForTenant();
    if (result[0] > 0) {
      log.info(
          "RecurringScheduleHandler: processed {} schedules, created {} projects",
          result[0],
          result[1]);
    }
  }
}
