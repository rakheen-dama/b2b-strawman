package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Top-level grouping for audit events, used to bucket them into themed sections of the firm-audit
 * view (security incidents, compliance milestones, financial activity, data-protection events,
 * everything else).
 *
 * <p>Group is derived at read time from the static {@link AuditEventTypeRegistry} catalogue (per
 * ADR-261) and is <strong>never persisted</strong> on the {@link AuditEvent} entity.
 */
public enum AuditEventGroup {
  /** Authentication, authorization, role, and capability events. */
  SECURITY,

  /** Compliance milestones such as matter closures and audit-log exports. */
  COMPLIANCE,

  /** Financial activity such as trust transactions, invoices, and proposals. */
  FINANCIAL,

  /** Data-protection events (DSAR, export, anonymisation). */
  DATA,

  /**
   * Default fallback bucket for events that do not match a specific registered prefix or exact
   * type.
   */
  STANDARD
}
