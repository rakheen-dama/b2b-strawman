package io.b2mash.b2b.b2bstrawman;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * G88-F48: the application {@link Clock} bean is the single source of server "today", and it must
 * be South African time regardless of the JVM default zone (containers run in UTC).
 */
class BackendApplicationClockTest {

  @Test
  void clockBean_isAfricaJohannesburg() {
    Clock clock = new BackendApplication().clock();

    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Africa/Johannesburg"));
  }

  @Test
  void clockBean_datesLateUtcEveningAsNextSastDay() {
    ZoneId beanZone = new BackendApplication().clock().getZone();
    Clock atBoundary = Clock.fixed(Instant.parse("2026-10-29T23:30:00Z"), beanZone);

    assertThat(LocalDate.now(atBoundary)).isEqualTo(LocalDate.of(2026, 10, 30));
  }
}
