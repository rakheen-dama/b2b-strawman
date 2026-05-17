package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/** State machine for accounting sync entries. */
public enum SyncState {
  /** Awaiting processing by the sync worker. */
  PENDING,
  /** Currently being processed. */
  IN_FLIGHT,
  /** Successfully synced to the external system. */
  COMPLETED,
  /** Failed but will be retried on the next attempt cycle. */
  FAILED_RETRYING,
  /** Max retries exceeded or non-retryable error — requires manual intervention. */
  DEAD_LETTER,
  /** Permanently blocked by trust boundary guard (e.g., trust account invoice). */
  BLOCKED_TRUST_BOUNDARY,
  /** Payment pull amounts do not match — requires reconciliation. */
  RECONCILE_DRIFT
}
