package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection interface for per-member per-currency value breakdown. */
public interface MemberValueProjection {

  UUID getMemberId();

  String getCurrency();

  BigDecimal getBillableValue();

  BigDecimal getCostValue();
}
