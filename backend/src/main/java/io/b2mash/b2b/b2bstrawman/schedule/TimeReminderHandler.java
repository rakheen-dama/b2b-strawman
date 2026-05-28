package io.b2mash.b2b.b2bstrawman.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for checking time reminders for a single tenant. Delegates to {@link
 * TimeReminderScheduler#processTenant()} which checks org settings, time window, working days, and
 * creates reminder notifications for members who have not logged sufficient time.
 *
 * <p>Extracted from {@link TimeReminderScheduler#checkTimeReminders()}.
 */
@Component
public class TimeReminderHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(TimeReminderHandler.class);

  private final TimeReminderScheduler timeReminderScheduler;

  public TimeReminderHandler(TimeReminderScheduler timeReminderScheduler) {
    this.timeReminderScheduler = timeReminderScheduler;
  }

  @Override
  public String jobType() {
    return "time_reminder_check";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int created = timeReminderScheduler.processTenant();
    if (created > 0) {
      log.info("TimeReminderHandler: created {} reminders", created);
    }
  }
}
