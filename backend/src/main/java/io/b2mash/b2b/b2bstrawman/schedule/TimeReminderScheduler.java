package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
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

  private final OrgSchemaMappingRepository mappingRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final NotificationService notificationService;
  private final NotificationPreferenceRepository notificationPreferenceRepository;
  private final TransactionTemplate transactionTemplate;

  public TimeReminderScheduler(
      OrgSchemaMappingRepository mappingRepository,
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      TimeEntryRepository timeEntryRepository,
      NotificationService notificationService,
      NotificationPreferenceRepository notificationPreferenceRepository,
      TransactionTemplate transactionTemplate) {
    this.mappingRepository = mappingRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.notificationService = notificationService;
    this.notificationPreferenceRepository = notificationPreferenceRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Scheduled(fixedRate = 900000)
  public void checkTimeReminders() {
    log.info("Time reminder scheduler started");
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
   *
   * @return true if a notification was created
   */
  private boolean checkMemberTimeLogged(UUID memberId, LocalDate today, OrgSettings orgSettings) {
    // Check opt-out preference
    var prefOpt =
        transactionTemplate.execute(
            tx ->
                notificationPreferenceRepository.findByMemberIdAndNotificationType(
                    memberId, "TIME_REMINDER"));
    if (prefOpt != null && prefOpt.isPresent() && !prefOpt.get().isInAppEnabled()) {
      return false;
    }

    // Query total minutes logged today
    var totalMinutes =
        transactionTemplate.execute(
            tx -> timeEntryRepository.sumDurationMinutesByMemberIdAndDate(memberId, today));
    if (totalMinutes == null) {
      totalMinutes = 0;
    }

    int threshold =
        orgSettings.getTimeReminderMinMinutes() != null
            ? orgSettings.getTimeReminderMinMinutes()
            : 240;

    if (totalMinutes < threshold) {
      double loggedHours = totalMinutes / 60.0;
      double thresholdHours = threshold / 60.0;
      String title =
          "You have logged %.1f of %.1f hours today".formatted(loggedHours, thresholdHours);
      String body = "Don't forget to log your time!";

      notificationService.createNotification(
          memberId, "TIME_REMINDER", title, body, null, null, null);
      return true;
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
