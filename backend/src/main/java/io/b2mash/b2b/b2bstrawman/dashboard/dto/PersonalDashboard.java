package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.List;

/** Personal dashboard response combining utilization, matter breakdown, deadlines, and trend. */
public record PersonalDashboard(
    UtilizationSummary utilization,
    List<MatterBreakdownEntry> matterBreakdown,
    int overdueTaskCount,
    List<UpcomingDeadline> upcomingDeadlines,
    List<TrendPoint> trend) {}
