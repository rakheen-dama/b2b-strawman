package io.b2mash.b2b.b2bstrawman.task;

import java.time.LocalDate;
import java.util.Set;

/**
 * Value object representing a simplified RRULE (RFC 5545 subset). Supports DAILY, WEEKLY, MONTHLY,
 * and YEARLY frequencies with an interval multiplier.
 *
 * <p>Format: {@code FREQ=MONTHLY;INTERVAL=2} or {@code FREQ=DAILY} (interval defaults to 1).
 */
public record RecurrenceRule(String frequency, int interval) {

  private static final Set<String> SUPPORTED_FREQUENCIES =
      Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY");

  public RecurrenceRule {
    if (frequency == null || !SUPPORTED_FREQUENCIES.contains(frequency)) {
      throw new IllegalArgumentException("Unsupported frequency: " + frequency);
    }
    if (interval < 1) {
      throw new IllegalArgumentException("Interval must be >= 1, got: " + interval);
    }
  }

  /**
   * Parses an RRULE string into a RecurrenceRule. Supports formats like {@code
   * FREQ=MONTHLY;INTERVAL=2} or {@code FREQ=DAILY} (interval defaults to 1).
   *
   * @throws IllegalArgumentException if the format is invalid or frequency is unsupported
   */
  public static RecurrenceRule parse(String rruleString) {
    if (rruleString == null || rruleString.isBlank()) {
      throw new IllegalArgumentException("RRULE string must not be null or blank");
    }

    String freq = null;
    int interval = 1;

    String[] parts = rruleString.split(";");
    for (String part : parts) {
      String[] keyValue = part.split("=", 2);
      if (keyValue.length != 2) {
        throw new IllegalArgumentException("Invalid RRULE component: " + part);
      }
      switch (keyValue[0].toUpperCase()) {
        case "FREQ" -> freq = keyValue[1].toUpperCase();
        case "INTERVAL" -> {
          try {
            interval = Integer.parseInt(keyValue[1]);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid INTERVAL value: " + keyValue[1]);
          }
        }
        default -> {
          // Ignore unknown components for forward compatibility
        }
      }
    }

    if (freq == null) {
      throw new IllegalArgumentException("RRULE must contain FREQ component: " + rruleString);
    }

    return new RecurrenceRule(freq, interval);
  }

  /**
   * Calculates the next due date based on the current due date and this rule's frequency/interval.
   *
   * @param currentDueDate the current due date; if null, uses today's date
   * @return the next due date
   */
  public LocalDate calculateNextDueDate(LocalDate currentDueDate) {
    LocalDate base = currentDueDate != null ? currentDueDate : LocalDate.now();
    return switch (frequency) {
      case "DAILY" -> base.plusDays(interval);
      case "WEEKLY" -> base.plusWeeks(interval);
      case "MONTHLY" -> base.plusMonths(interval);
      case "YEARLY" -> base.plusYears(interval);
      default -> throw new IllegalStateException("Unexpected frequency: " + frequency);
    };
  }
}
