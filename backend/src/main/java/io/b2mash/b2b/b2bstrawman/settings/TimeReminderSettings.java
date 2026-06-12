package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Time-tracking reminder settings group (Wave 3.5 embeddable refactor). Holds the firm's
 * daily-time-entry reminder configuration: the enabled toggle, the working-day CSV (e.g. {@code
 * "MON,TUE,WED,THU,FRI"}), the time-of-day the reminder fires, and the minimum logged minutes below
 * which a member is nudged. Consumed by the time-reminder scheduler/handler.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). {@code time_reminder_enabled} is NOT NULL, so it is modelled as
 * a primitive {@code boolean} (defaults to {@code false}) and the embedded never fully materialises
 * as NULL on reload; the remaining three columns are nullable. The {@code time_reminder_time}
 * column stays mapped to {@link LocalTime} ({@code time without time zone}).
 *
 * <p>Field-level setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the
 * embeddable has no reference to the owning entity's timestamp. Hibernate dirty-checks the embedded
 * columns and persists changes regardless. The {@link #updateTimeReminderSettings} domain mutator
 * preserves the same atomic four-field update semantics the entity method used to provide.
 */
@Embeddable
public class TimeReminderSettings {

  private static final Map<String, DayOfWeek> DAY_ABBREVIATIONS =
      Map.of(
          "MON", DayOfWeek.MONDAY,
          "TUE", DayOfWeek.TUESDAY,
          "WED", DayOfWeek.WEDNESDAY,
          "THU", DayOfWeek.THURSDAY,
          "FRI", DayOfWeek.FRIDAY,
          "SAT", DayOfWeek.SATURDAY,
          "SUN", DayOfWeek.SUNDAY);

  @Column(name = "time_reminder_enabled", nullable = false)
  private boolean timeReminderEnabled;

  @Column(name = "time_reminder_days", length = 50)
  private String timeReminderDays;

  @Column(name = "time_reminder_time")
  private LocalTime timeReminderTime;

  @Column(name = "time_reminder_min_minutes")
  private Integer timeReminderMinMinutes;

  protected TimeReminderSettings() {}

  public boolean isTimeReminderEnabled() {
    return timeReminderEnabled;
  }

  public void setTimeReminderEnabled(boolean timeReminderEnabled) {
    this.timeReminderEnabled = timeReminderEnabled;
  }

  public String getTimeReminderDays() {
    return timeReminderDays;
  }

  public void setTimeReminderDays(String timeReminderDays) {
    this.timeReminderDays = timeReminderDays;
  }

  public LocalTime getTimeReminderTime() {
    return timeReminderTime;
  }

  public void setTimeReminderTime(LocalTime timeReminderTime) {
    this.timeReminderTime = timeReminderTime;
  }

  public Integer getTimeReminderMinMinutes() {
    return timeReminderMinMinutes;
  }

  public void setTimeReminderMinMinutes(Integer timeReminderMinMinutes) {
    this.timeReminderMinMinutes = timeReminderMinMinutes;
  }

  /** Returns the minimum hours threshold, computed from minutes. Defaults to 4.0 if not set. */
  public double getTimeReminderMinHours() {
    return timeReminderMinMinutes != null ? timeReminderMinMinutes / 60.0 : 4.0;
  }

  /** Parses timeReminderDays CSV (e.g. "MON,TUE,WED,THU,FRI") into a Set of DayOfWeek. */
  public Set<DayOfWeek> getWorkingDays() {
    if (timeReminderDays == null || timeReminderDays.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(timeReminderDays.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> DAY_ABBREVIATIONS.get(s.toUpperCase(Locale.ROOT)))
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
  }

  /** Updates all time reminder fields atomically. */
  public void updateTimeReminderSettings(
      boolean enabled, String days, LocalTime time, Integer minMinutes) {
    this.timeReminderEnabled = enabled;
    this.timeReminderDays = days;
    this.timeReminderTime = time;
    this.timeReminderMinMinutes = minMinutes;
  }
}
