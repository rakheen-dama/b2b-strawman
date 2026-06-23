package io.b2mash.b2b.b2bstrawman.crm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data projection for the per-stage pipeline breakdown native query (Epic 578A). Column
 * aliases in {@link DealRepository#stageBreakdown(UUID)} map onto these getters (e.g. {@code AS
 * totalValue} → {@link #getTotalValue()}).
 */
public interface StageBreakdownProjection {
  UUID getStageId();

  String getStageName();

  int getStagePosition();

  long getDealCount();

  BigDecimal getTotalValue();

  BigDecimal getWeightedValue();
}
