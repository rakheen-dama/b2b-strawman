package io.b2mash.b2b.b2bstrawman.compliance;

/** Severity-bucketed counts of compliance audit findings for a report. */
public record FindingCounts(int critical, int high, int medium, int low, int info) {}
