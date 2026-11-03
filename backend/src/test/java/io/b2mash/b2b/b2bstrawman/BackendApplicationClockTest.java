package io.b2mash.b2b.b2bstrawman;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
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
}
