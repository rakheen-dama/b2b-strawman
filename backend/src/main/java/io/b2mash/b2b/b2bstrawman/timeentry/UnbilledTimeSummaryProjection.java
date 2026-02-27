package io.b2mash.b2b.b2bstrawman.timeentry;

/** Spring Data projection interface for unbilled time entry count and hours aggregation. */
public interface UnbilledTimeSummaryProjection {

  long getEntryCount();

  double getTotalHours();
}
