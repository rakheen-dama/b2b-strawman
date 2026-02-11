package io.b2mash.b2b.b2bstrawman.timeentry;

import java.util.UUID;

/** Spring Data projection interface for per-project time aggregation in My Work view. */
public interface MyWorkProjectTimeSummaryProjection {

  UUID getProjectId();

  String getProjectName();

  Long getBillableMinutes();

  Long getNonBillableMinutes();

  Long getTotalMinutes();
}
