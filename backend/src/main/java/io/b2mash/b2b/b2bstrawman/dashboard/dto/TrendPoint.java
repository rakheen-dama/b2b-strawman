package io.b2mash.b2b.b2bstrawman.dashboard.dto;

/** A single data point in a KPI trend series (e.g. hours per day/week/month). */
public record TrendPoint(String period, double value) {}
