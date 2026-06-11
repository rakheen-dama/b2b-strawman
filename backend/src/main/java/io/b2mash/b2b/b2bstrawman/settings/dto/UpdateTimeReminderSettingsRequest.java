package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to update time-entry reminder scheduling (enabled flag, days, time, min minutes). */
public record UpdateTimeReminderSettingsRequest(
    Boolean timeReminderEnabled,
    @Size(max = 50, message = "timeReminderDays must be at most 50 characters")
        @Pattern(
            regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
            message = "timeReminderDays must be a comma-separated list of valid day abbreviations")
        String timeReminderDays,
    @Pattern(
            regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
            message = "timeReminderTime must be in HH:mm format")
        String timeReminderTime,
    @Min(value = 0, message = "timeReminderMinMinutes must be non-negative")
        Integer timeReminderMinMinutes) {}
