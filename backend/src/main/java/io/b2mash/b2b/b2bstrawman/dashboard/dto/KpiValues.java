package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/**
 * KPI values without trend data, used for previous-period comparison. Same shape as the main KPI
 * fields in {@link KpiResponse}.
 */
public record KpiValues(
    int activeProjectCount,
    double totalHoursLogged,
    Double billablePercent,
    int overdueTaskCount,
    Double averageMarginPercent) {}
