package io.b2mash.b2b.b2bstrawman.timeentry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Native projection for {@link TimeEntryRepository#findUnbilledWipByMember()} — one row per member
 * and billing-rate currency. {@code currency} is null for entries recorded without a rate snapshot.
 */
public interface UnbilledWipByMemberProjection {

  UUID getMemberId();

  String getMemberName();

  String getCurrency();

  Long getMinutes();

  BigDecimal getValue();

  LocalDate getOldestEntryDate();
}
