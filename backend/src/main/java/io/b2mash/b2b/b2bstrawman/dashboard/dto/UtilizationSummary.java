package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/** Utilization metrics for a member over a date range. */
public record UtilizationSummary(double totalHours, double billableHours, Double billablePercent) {}
