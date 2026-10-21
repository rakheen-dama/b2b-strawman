package io.b2mash.b2b.b2bstrawman.customer;

import java.math.BigDecimal;
import java.util.UUID;

/** Native projection for {@link ClientListRowRepository#findAggregates} — one row per client id. */
public interface ClientListAggregateProjection {

  UUID getClientId();

  Long getOpenMatterCount();

  String getFeeNoteCurrency();

  BigDecimal getOutstandingFeeNoteTotal();

  Long getOutstandingFeeNoteCount();
}
