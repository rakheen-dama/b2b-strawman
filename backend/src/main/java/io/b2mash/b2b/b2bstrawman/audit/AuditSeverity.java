package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Severity classification for audit events, derived at read time from the static metadata registry
 * (per ADR-261).
 *
 * <p>Severity is <strong>never persisted</strong> on the {@link AuditEvent} entity. It is computed
 * on-demand via {@link AuditEventTypeRegistry#resolve(String)} so that historical rows reclassify
 * automatically whenever the registry catalogue changes.
 *
 * <p>Ordering (low to high) is encoded by ordinal but should not be relied on by callers: the four
 * levels are conceptually independent and may be combined arbitrarily by a filter.
 */
public enum AuditSeverity {
  /** Routine activity. Default for any event without an explicit registry classification. */
  INFO,

  /**
   * Noteworthy but expected activity (e.g. a generic security event, a routine matter closure, a
   * trust transaction approval). Surfaced in firm-audit views for context, not alerted on.
   */
  NOTICE,

  /**
   * Activity that may indicate misuse or a policy violation (e.g. login failure, permission denied,
   * trust transaction rejected, role/capability change). Highlighted in firm-audit views.
   */
  WARNING,

  /**
   * Activity that demands immediate attention (e.g. matter closure with an override). Flagged
   * prominently in firm-audit views and intended for compliance review.
   */
  CRITICAL
}
