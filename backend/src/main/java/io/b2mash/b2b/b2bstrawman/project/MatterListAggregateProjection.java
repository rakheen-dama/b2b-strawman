package io.b2mash.b2b.b2bstrawman.project;

import java.math.BigDecimal;
import java.util.UUID;

/** Native projection for {@link MatterListRowRepository#findAggregates} — one row per matter id. */
public interface MatterListAggregateProjection {

  UUID getMatterId();

  String getClientName();

  UUID getFeeEarnerId();

  String getFeeEarnerName();

  Long getUnbilledMinutes();

  BigDecimal getUnbilledTimeValue();

  BigDecimal getUnbilledDisbursementAmount();

  Long getOpenTaskCount();
}
