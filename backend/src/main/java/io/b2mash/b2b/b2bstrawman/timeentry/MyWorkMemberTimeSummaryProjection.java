package io.b2mash.b2b.b2bstrawman.timeentry;

/** Spring Data projection interface for member-level cross-project time aggregation totals. */
public interface MyWorkMemberTimeSummaryProjection {

  Long getBillableMinutes();

  Long getNonBillableMinutes();

  Long getTotalMinutes();
}
