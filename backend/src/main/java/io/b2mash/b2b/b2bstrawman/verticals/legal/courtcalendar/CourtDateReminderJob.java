package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CourtDateReminderJob {

  private static final Logger log = LoggerFactory.getLogger(CourtDateReminderJob.class);
  private static final String MODULE_ID = "court_calendar";

  private final OrgSchemaMappingRepository mappingRepository;
  private final CourtDateRepository courtDateRepository;
  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final NotificationService notificationService;
  private final NotificationRepository notificationRepository;
  private final VerticalModuleGuard moduleGuard;
  private final ProjectRepository projectRepository;
  private final TransactionTemplate transactionTemplate;

  public CourtDateReminderJob(
      OrgSchemaMappingRepository mappingRepository,
      CourtDateRepository courtDateRepository,
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      NotificationService notificationService,
      NotificationRepository notificationRepository,
      VerticalModuleGuard moduleGuard,
      ProjectRepository projectRepository,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.courtDateRepository = courtDateRepository;
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.notificationService = notificationService;
    this.notificationRepository = notificationRepository;
    this.moduleGuard = moduleGuard;
    this.projectRepository = projectRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(cron = "${court.reminder.cron:0 0 6 * * *}")
  public void execute() {
    log.debug("Court date reminder job started");
    var mappings = mappingRepository.findAll();
    int totalNotifications = 0;

    for (var mapping : mappings) {
      try {
        int count =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getExternalOrgId())
                .call(() -> processTenant());
        totalNotifications += count;
      } catch (Exception e) {
        log.error(
            "Failed to process court date reminders for schema {}", mapping.getSchemaName(), e);
      }
    }

    if (totalNotifications > 0) {
      log.info("Court date reminder job completed: {} notifications created", totalNotifications);
    } else {
      log.debug("Court date reminder job completed: 0 notifications created");
    }
  }

  private int processTenant() {
    Integer count =
        transactionTemplate.execute(
            tx -> {
              if (!moduleGuard.isModuleEnabled(MODULE_ID)) {
                return 0;
              }

              int notifications = 0;
              notifications += processCourtDateReminders();
              notifications += processPrescriptionWarnings();
              return notifications;
            });

    return count != null ? count : 0;
  }

  private int processCourtDateReminders() {
    var today = LocalDate.now();
    // Find maximum reminder days to determine query range
    int maxReminderDays = 30;
    var upcomingDates =
        courtDateRepository.findByStatusInAndScheduledDateBetween(
            List.of("SCHEDULED", "POSTPONED"), today, today.plusDays(maxReminderDays));

    int created = 0;
    for (var courtDate : upcomingDates) {
      long daysUntil = ChronoUnit.DAYS.between(today, courtDate.getScheduledDate());
      if (daysUntil <= courtDate.getReminderDays()) {
        // Idempotency check
        if (notificationRepository.existsByTypeAndReferenceEntityId(
            "COURT_DATE_REMINDER", courtDate.getId())) {
          continue;
        }

        var title = "Court date in %d days: %s".formatted(daysUntil, courtDate.getDateType());
        var body =
            "%s -- %s. Scheduled for %s."
                .formatted(
                    courtDate.getCourtName(),
                    courtDate.getDescription() != null ? courtDate.getDescription() : "",
                    courtDate.getScheduledDate());

        notificationService.createIfEnabled(
            courtDate.getCreatedBy(),
            "COURT_DATE_REMINDER",
            title,
            body,
            "COURT_DATE",
            courtDate.getId(),
            courtDate.getProjectId());

        created++;
      }
    }
    return created;
  }

  private int processPrescriptionWarnings() {
    var today = LocalDate.now();
    var trackers =
        prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            List.of("RUNNING", "WARNED"), today.minusDays(1), today.plusDays(90));

    int created = 0;
    for (var tracker : trackers) {
      long daysUntil = ChronoUnit.DAYS.between(today, tracker.getPrescriptionDate());

      // Handle expired prescriptions
      if (daysUntil < 0) {
        tracker.setStatus("EXPIRED");
        prescriptionTrackerRepository.save(tracker);
        continue;
      }

      // Idempotency check
      if (notificationRepository.existsByTypeAndReferenceEntityId(
          "PRESCRIPTION_WARNING", tracker.getId())) {
        continue;
      }

      // Update status to WARNED if still RUNNING
      if ("RUNNING".equals(tracker.getStatus())) {
        tracker.setStatus("WARNED");
        prescriptionTrackerRepository.save(tracker);
      }

      // Determine title based on urgency
      String title;
      if (daysUntil <= 7) {
        title = "Prescription imminent";
      } else if (daysUntil <= 30) {
        title = "Prescription critical";
      } else {
        title = "Prescription warning";
      }

      // Resolve project name
      String projectName = "Unknown";
      var project = projectRepository.findById(tracker.getProjectId()).orElse(null);
      if (project != null) {
        projectName = project.getName();
      }

      var body =
          "Matter %s: Prescription expires on %s (%d days)."
              .formatted(projectName, tracker.getPrescriptionDate(), daysUntil);

      notificationService.createIfEnabled(
          tracker.getCreatedBy(),
          "PRESCRIPTION_WARNING",
          title,
          body,
          "PRESCRIPTION_TRACKER",
          tracker.getId(),
          tracker.getProjectId());

      created++;
    }
    return created;
  }
}
