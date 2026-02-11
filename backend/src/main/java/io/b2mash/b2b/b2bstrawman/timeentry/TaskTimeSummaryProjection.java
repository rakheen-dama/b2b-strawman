package io.b2mash.b2b.b2bstrawman.timeentry;

import java.util.UUID;

/** Spring Data projection interface for per-task time aggregation within a project. */
public interface TaskTimeSummaryProjection {

  UUID getTaskId();

  String getTaskTitle();

  Long getBillableMinutes();

  Long getTotalMinutes();

  Long getEntryCount();
}
