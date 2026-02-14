package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection interface for org-level revenue aggregation grouped by project and currency. */
public interface OrgRevenueProjection {

  UUID getProjectId();

  String getProjectName();

  String getCurrency();

  BigDecimal getBillableHours();

  BigDecimal getBillableValue();
}
