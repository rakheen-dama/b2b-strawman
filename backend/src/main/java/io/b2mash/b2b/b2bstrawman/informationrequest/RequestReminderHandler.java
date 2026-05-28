package io.b2mash.b2b.b2bstrawman.informationrequest;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for checking and sending request reminders for a single tenant. Delegates to {@link
 * RequestReminderScheduler#processTenant()} which checks org settings, iterates pending information
 * requests, and sends reminder emails for overdue items.
 *
 * <p>Extracted from {@link RequestReminderScheduler#checkRequestReminders()}.
 */
@Component
public class RequestReminderHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(RequestReminderHandler.class);

  private final RequestReminderScheduler requestReminderScheduler;

  public RequestReminderHandler(RequestReminderScheduler requestReminderScheduler) {
    this.requestReminderScheduler = requestReminderScheduler;
  }

  @Override
  public String jobType() {
    return "request_reminder_check";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int sent = requestReminderScheduler.processTenant();
    if (sent > 0) {
      log.info("RequestReminderHandler: sent {} reminders", sent);
    }
  }
}
