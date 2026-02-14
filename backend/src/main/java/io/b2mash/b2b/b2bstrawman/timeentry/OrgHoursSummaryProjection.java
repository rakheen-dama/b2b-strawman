package io.b2mash.b2b.b2bstrawman.timeentry;

/** Spring Data projection interface for org-level hours aggregation. */
public interface OrgHoursSummaryProjection {

  Long getTotalMinutes();

  Long getBillableMinutes();
}
