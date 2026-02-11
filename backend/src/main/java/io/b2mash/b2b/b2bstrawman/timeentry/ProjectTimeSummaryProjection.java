package io.b2mash.b2b.b2bstrawman.timeentry;

/** Spring Data projection interface for project-level time aggregation totals. */
public interface ProjectTimeSummaryProjection {

  Long getBillableMinutes();

  Long getNonBillableMinutes();

  Long getTotalMinutes();

  Long getContributorCount();

  Long getEntryCount();
}
