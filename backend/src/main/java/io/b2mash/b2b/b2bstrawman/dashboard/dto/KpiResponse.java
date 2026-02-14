package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.List;

/**
 * Org-level KPI response with trend data and previous-period comparison. Financial fields
 * (billablePercent, averageMarginPercent) are nullable — they are null for non-admin/non-owner
 * members.
 */
public record KpiResponse(
    int activeProjectCount,
    double totalHoursLogged,
    Double billablePercent,
    int overdueTaskCount,
    Double averageMarginPercent,
    List<TrendPoint> trend,
    KpiValues previousPeriod) {

  /** Returns a copy with financial fields (billablePercent, averageMarginPercent) set to null. */
  public KpiResponse withFinancialsRedacted() {
    return new KpiResponse(
        activeProjectCount, totalHoursLogged, null, overdueTaskCount, null, trend, previousPeriod);
  }
}
