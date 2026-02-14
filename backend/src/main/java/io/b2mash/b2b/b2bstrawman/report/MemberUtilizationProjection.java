package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection interface for member utilization aggregation grouped by member. */
public interface MemberUtilizationProjection {

  UUID getMemberId();

  String getMemberName();

  BigDecimal getTotalHours();

  BigDecimal getBillableHours();

  BigDecimal getNonBillableHours();
}
