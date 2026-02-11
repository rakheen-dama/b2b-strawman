package io.b2mash.b2b.b2bstrawman.timeentry;

import java.util.UUID;

/** Spring Data projection interface for batch task duration totals (My Work task enrichment). */
public interface TaskDurationProjection {

  UUID getTaskId();

  Long getTotalMinutes();
}
