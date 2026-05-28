package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for processing court date reminders and prescription warnings for a single tenant.
 * Delegates to {@link CourtDateReminderJob#processTenant()} which checks module enablement,
 * processes upcoming court dates and prescription trackers, and creates reminder notifications.
 *
 * <p>Extracted from {@link CourtDateReminderJob#execute()}.
 */
@Component
public class CourtDateReminderHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(CourtDateReminderHandler.class);

  private final CourtDateReminderJob courtDateReminderJob;

  public CourtDateReminderHandler(CourtDateReminderJob courtDateReminderJob) {
    this.courtDateReminderJob = courtDateReminderJob;
  }

  @Override
  public String jobType() {
    return "court_date_reminder";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int created = courtDateReminderJob.processTenant();
    if (created > 0) {
      log.info("CourtDateReminderHandler: created {} notifications", created);
    }
  }
}
