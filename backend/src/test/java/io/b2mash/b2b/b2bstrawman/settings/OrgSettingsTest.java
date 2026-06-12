package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class OrgSettingsTest {

  @Test
  void defaultTimeReminderEnabled_isFalse() {
    var settings = new OrgSettings("USD");
    assertThat(settings.getTimeReminder().isTimeReminderEnabled()).isFalse();
  }

  @Test
  void defaultPortalNotificationDocTypes_matchesV117CanonicalDefault() {
    var settings = new OrgSettings("USD");
    assertThat(settings.getPortal().getPortalNotificationDocTypes())
        .containsExactly("matter-closure-letter", "statement-of-account");
  }

  @Test
  void getWorkingDays_parsesCorrectly() {
    var settings = new OrgSettings("USD");
    settings.getTimeReminder().setTimeReminderDays("MON,WED,FRI");
    var days = settings.getTimeReminder().getWorkingDays();
    assertThat(days)
        .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
  }

  @Test
  void getWorkingDays_emptyString_returnsEmptySet() {
    var settings = new OrgSettings("USD");
    settings.getTimeReminder().setTimeReminderDays("");
    assertThat(settings.getTimeReminder().getWorkingDays()).isEmpty();
  }

  @Test
  void getWorkingDays_null_returnsEmptySet() {
    var settings = new OrgSettings("USD");
    // timeReminderDays is null by default from constructor
    assertThat(settings.getTimeReminder().getWorkingDays()).isEmpty();
  }

  @Test
  void getWorkingDays_invalidAbbreviation_skippedGracefully() {
    var settings = new OrgSettings("USD");
    settings.getTimeReminder().setTimeReminderDays("MON,INVALID,FRI");
    var days = settings.getTimeReminder().getWorkingDays();
    assertThat(days).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
  }

  @Test
  void getTimeReminderMinHours_returns4ForDefault240Minutes() {
    var settings = new OrgSettings("USD");
    settings.getTimeReminder().setTimeReminderMinMinutes(240);
    assertThat(settings.getTimeReminder().getTimeReminderMinHours()).isEqualTo(4.0);
  }

  @Test
  void getTimeReminderMinHours_returnsDefaultWhenNull() {
    var settings = new OrgSettings("USD");
    // timeReminderMinMinutes is null by default
    assertThat(settings.getTimeReminder().getTimeReminderMinHours()).isEqualTo(4.0);
  }
}
