package io.b2mash.b2b.b2bstrawman.config;

import java.time.ZoneId;

/**
 * The product-wide business time zone. Every calendar date or wall-clock time a user reasons about
 * (trust posting dates, due dates, deadlines, statement periods, reminders, schedules, "today") is
 * South African time, independent of the JVM or container zone. Storage instants, wire formats,
 * provider billing months and audit-export timestamps stay UTC.
 *
 * <p>Server code reads "today" from the injected {@link java.time.Clock} bean (which runs in this
 * zone) so tests can pin it; {@link #ZONE} is for converting stored instants to a business date.
 */
public final class KaziTime {

  /** Compile-time constant so it can be used in {@code @Scheduled(zone = ...)}. */
  public static final String ZONE_ID = "Africa/Johannesburg";

  public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

  private KaziTime() {}
}
