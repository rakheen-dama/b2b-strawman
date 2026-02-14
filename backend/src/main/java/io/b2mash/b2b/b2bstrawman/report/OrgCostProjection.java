package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection interface for org-level cost aggregation grouped by project and currency. */
public interface OrgCostProjection {

  UUID getProjectId();

  String getProjectName();

  String getCurrency();

  BigDecimal getCostValue();
}
