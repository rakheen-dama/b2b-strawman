package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/** What triggered the creation of a sync entry. */
public enum SyncTrigger {
  /** Automatic event-driven enqueue (e.g., invoice finalized). */
  EVENT,
  /** Manual retry from dead-letter queue by a user. */
  MANUAL_RETRY,
  /** Force resync requested by a user (re-push even if already completed). */
  FORCE_RESYNC
}
