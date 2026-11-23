package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test factory (657A): an {@link OpeningAuthority} without a persisted take-on sign-off — for tests
 * and the {@code TrustBooksFixture} take-on path only. Production builds one only through {@link
 * OpeningAuthority#forBatch} (657C).
 */
public final class OpeningAuthorities {

  private OpeningAuthorities() {}

  public static OpeningAuthority of(
      UUID batchId, LocalDate cutoverDate, UUID signedBy, Instant signedAt) {
    return new OpeningAuthority(batchId, cutoverDate, signedBy, signedAt);
  }
}
