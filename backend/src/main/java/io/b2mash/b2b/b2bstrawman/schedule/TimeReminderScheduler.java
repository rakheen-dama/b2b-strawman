package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled component that checks all tenants every 15 minutes and sends in-app time reminder
 * notifications to members who have logged less than the configured minimum hours for the day.
 *
 * <p>Each tenant is processed independently — errors in one tenant do not affect others.
 */
@Component
public class TimeReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(TimeReminderScheduler.class);

  /** Scheduler runs every 15 minutes (900 000 ms). */
  private static final long CHECK_INTERVAL_MS = 900_000;

  private final OrgSchemaMappingRepository mappingRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final NotificationService notificationService;
  private final NotificationRepository notificationRepository;
  private final TransactionTemplate transactionTemplate;

  public TimeReminderScheduler(
      OrgSchemaMappingRepository mappingRepository,
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      TimeEntryRepository timeEntryRepository,
      NotificationService notificationService,
      NotificationRepository notificationRepository,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.notificationService = notificationService;
    this.notificationRepository = notificationRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedRate = CHECK_INTERVAL_MS)
  public void checkTimeReminders() {
    log.debug("Time reminder scheduler started");
    var mappings = mappingRepository.findAll();
    int remindersCreated = 0;

    for (var mapping : mappings) {
      try {
        int created =
            ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
                .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
                .call(() -> processTenant());
        remindersCreated += created;
      } catch (Exception e) {
        log.error("Failed to process time reminders for schema {}", mapping.getSchemaName(), e);
      }
    }

    log.info("Time reminder scheduler completed: {} reminders created", remindersCreated);
  }

  private int processTenant() {
    var orgSettingsOpt =
        transactionTemplate.execute(tx -> orgSettingsRepository.findForCurrentTenant());

    if (orgSettingsOpt == null || orgSettingsOpt.isEmpty()) {
      return 0;
    }

    var orgSettings = orgSettingsOpt.get();

    if (!orgSettings.isTimeReminderEnabled()) {
      return 0;
    }

    if (!isWithinTimeWindow(orgSettings.getTimeReminderTime())) {
      return 0;
    }

    return processOrgReminders(orgSettings);
  }

  private int processOrgReminders(OrgSettings orgSettings) {
    var today = LocalDate.now(ZoneOffset.UTC);
    DayOfWeek todayDow = today.getDayOfWeek();

    if (!orgSettings.getWorkingDays().contains(todayDow)) {
      return 0;
    }

    // TODO: Paginate member query for v2 (findAll is acceptable for v1 — members are
    // tenant-scoped and typical tenants have <100 members)
    var members = transactionTemplate.execute(tx -> memberRepository.findAll());
    if (members == null || members.isEmpty()) {
      return 0;
    }

    int created = 0;
    for (var member : members) {
      if (checkMemberTimeLogged(member.getId(), today, orgSettings)) {
        created++;
      }
    }
    return created;
  }

  /**
   * Checks if a member has logged enough time today and creates a reminder notification if not.
   * Preference checks are handled by {@link NotificationService#createIfEnabled}. Deduplication
   * prevents multiple reminders per member per day.
   *
   * @return true if a notification was created
   */
  private boolean checkMemberTimeLogged(UUID memberId, LocalDate today, OrgSettings orgSettings) {
    // Single transaction for time sum query + dedup check
    var shouldNotify =
        transactionTemplate.execute(
            tx -> {
              // Deduplication: skip if a TIME_REMINDER was already sent today
              var startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
              if (notificationRepository.existsByTypeAndRecipientMemberIdAndCreatedAtAfter(
                  "TIME_REMINDER", memberId, startOfDay)) {
                return false;
              }

              // Query total minutes logged today
              Integer totalMinutes =
                  timeEntryRepository.sumDurationMinutesByMemberIdAndDate(memberId, today);
              int logged = totalMinutes != null ? totalMinutes : 0;

              int threshold =
                  orgSettings.getTimeReminderMinMinutes() != null
                      ? orgSettings.getTimeReminderMinMinutes()
                      : 240;

              return logged < threshold;
            });

    if (Boolean.TRUE.equals(shouldNotify)) {
      int threshold =
          orgSettings.getTimeReminderMinMinutes() != null
              ? orgSettings.getTimeReminderMinMinutes()
              : 240;

      // Build message and create notification in a single transaction
      var notification =
          transactionTemplate.execute(
              tx -> {
                Integer minutes =
                    timeEntryRepository.sumDurationMinutesByMemberIdAndDate(memberId, today);
                int logged = minutes != null ? minutes : 0;

                double loggedHours = logged / 60.0;
                double thresholdHours = threshold / 60.0;
                String title =
                    "You have logged %.1f of %.1f hours today"
                        .formatted(loggedHours, thresholdHours);
                String body = "Don't forget to log your time!";

                return notificationService.createIfEnabled(
                    memberId, "TIME_REMINDER", title, body, null, null, null);
              });
      return notification != null;
    }

    return false;
  }

  /**
   * Checks if the current UTC time falls within the 15-minute window starting at the configured
   * reminder time.
   */
  boolean isWithinTimeWindow(LocalTime reminderTime) {
    if (reminderTime == null) {
      return false;
    }
    var now = LocalTime.now(ZoneOffset.UTC);
    var windowEnd = reminderTime.plusMinutes(15);

    // Handle normal case (no midnight wrap)
    if (windowEnd.isAfter(reminderTime)) {
      return !now.isBefore(reminderTime) && now.isBefore(windowEnd);
    }

    // Handle midnight wrap (e.g., 23:50 + 15min = 00:05)
    return !now.isBefore(reminderTime) || now.isBefore(windowEnd);
  }
}
