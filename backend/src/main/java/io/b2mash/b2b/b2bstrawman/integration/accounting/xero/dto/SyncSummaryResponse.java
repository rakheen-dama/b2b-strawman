package io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto;

import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState;
import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for the sync summary endpoint. Aggregates sync entry counts by state with timestamp
 * markers for the oldest pending and last completed entries.
 */
public record SyncSummaryResponse(
    long pending,
    long inFlight,
    long completed,
    long failedRetrying,
    long deadLetter,
    long blockedTrustBoundary,
    long reconcileDrift,
    Instant oldestPendingAt,
    Instant lastCompletedAt) {

  public static SyncSummaryResponse from(
      Map<SyncState, Long> stateCounts, Instant oldestPendingAt, Instant lastCompletedAt) {
    return new SyncSummaryResponse(
        stateCounts.getOrDefault(SyncState.PENDING, 0L),
        stateCounts.getOrDefault(SyncState.IN_FLIGHT, 0L),
        stateCounts.getOrDefault(SyncState.COMPLETED, 0L),
        stateCounts.getOrDefault(SyncState.FAILED_RETRYING, 0L),
        stateCounts.getOrDefault(SyncState.DEAD_LETTER, 0L),
        stateCounts.getOrDefault(SyncState.BLOCKED_TRUST_BOUNDARY, 0L),
        stateCounts.getOrDefault(SyncState.RECONCILE_DRIFT, 0L),
        oldestPendingAt,
        lastCompletedAt);
  }
}
