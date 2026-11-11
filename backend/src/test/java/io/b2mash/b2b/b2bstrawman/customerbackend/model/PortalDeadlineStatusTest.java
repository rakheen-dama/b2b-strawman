package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** G89-F71: the portal deadline status rule, at its boundaries. Pure unit test. */
class PortalDeadlineStatusTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 3, 16);

  @ParameterizedTest
  @CsvSource({
    "-30, OVERDUE",
    "-1, OVERDUE",
    "0, DUE_SOON",
    "1, DUE_SOON",
    "7, DUE_SOON",
    "8, UPCOMING",
    "60, UPCOMING"
  })
  void deriveFollowsTheDueDate(int daysFromToday, String expected) {
    assertThat(PortalDeadlineStatus.derive(TODAY.plusDays(daysFromToday), TODAY))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "UPCOMING, -1, OVERDUE",
    "UPCOMING, 7, DUE_SOON",
    "DUE_SOON, 8, UPCOMING",
    "OVERDUE, 10, UPCOMING",
    "COMPLETED, -1, COMPLETED",
    "COMPLETED, 3, COMPLETED",
    "CANCELLED, 30, CANCELLED",
    "CANCELLED, -2, CANCELLED"
  })
  void effectiveKeepsTerminalAndReDerivesTheRest(
      String stored, int daysFromToday, String expected) {
    assertThat(PortalDeadlineStatus.effective(stored, TODAY.plusDays(daysFromToday), TODAY))
        .isEqualTo(expected);
  }

  @Test
  void dueSoonCutoffIsTheLastDueSoonDay() {
    LocalDate cutoff = PortalDeadlineStatus.dueSoonCutoff(TODAY);
    assertThat(cutoff).isEqualTo(TODAY.plusDays(PortalDeadlineStatus.DUE_SOON_DAYS));
    assertThat(PortalDeadlineStatus.derive(cutoff, TODAY)).isEqualTo("DUE_SOON");
    assertThat(PortalDeadlineStatus.derive(cutoff.plusDays(1), TODAY)).isEqualTo("UPCOMING");
  }
}
