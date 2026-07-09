package io.b2mash.b2b.b2bstrawman.collections;

/**
 * Lifecycle status of a {@link CollectionActivity} row. Transitions happen in place on the single
 * (invoice, stage) row; the state machine is:
 *
 * <pre>
 * PROPOSED ──approve──▶ SENT                              (terminal)
 * PROPOSED ──approve, provider failure──▶ SEND_FAILED     (scan-retryable)
 * PROPOSED ──reject──▶ REJECTED                           (terminal for this stage — ADR-326)
 * PROPOSED ──gate expires (72h)──▶ SKIPPED(gate_expired)  (scan-retryable)
 * PROPOSED ──invoice PAID/VOID──▶ CANCELLED_PAYMENT       (terminal)
 * SKIPPED  ──next scan, condition cleared──▶ PROPOSED     (row updated in place, new gate)
 * SEND_FAILED ──next scan──▶ PROPOSED                     (fresh draft; email never left)
 * FLAGGED  (ESCALATION stage only)                        (terminal)
 * </pre>
 *
 * <p><strong>Retryable vs terminal, and why.</strong> {@link #SKIPPED} and {@link #SEND_FAILED}
 * mean <em>no email ever reached the client</em> — the scan may safely re-propose (the row
 * transitions back to {@link #PROPOSED} with a fresh gate; the unique index is untouched because it
 * is the same row). {@link #REJECTED} means a human decided <em>not</em> to chase this invoice at
 * this stage — the scan must not nag the approver with the same stage again; the invoice simply
 * progresses to the next stage when its threshold passes. {@link #SENT} and {@link
 * #CANCELLED_PAYMENT} are self-evidently terminal.
 *
 * <ul>
 *   <li>{@link #PROPOSED} — a draft reminder awaits approval on an {@code AiExecutionGate}.
 *   <li>{@link #SENT} — terminal: the reminder email was dispatched.
 *   <li>{@link #SEND_FAILED} — scan-retryable: approved but the mail provider failed; no email
 *       left.
 *   <li>{@link #REJECTED} — terminal for this stage: a human declined to chase at this stage.
 *   <li>{@link #CANCELLED_PAYMENT} — terminal: the invoice was PAID/VOID before sending.
 *   <li>{@link #SKIPPED} — scan-retryable: a precondition blocked sending (e.g. gate expired, no
 *       recipient); may be re-proposed on a later scan.
 *   <li>{@link #FLAGGED} — terminal: an ESCALATION-stage row raised for a partner call (no email).
 * </ul>
 */
public enum CollectionActivityStatus {
  PROPOSED,
  SENT,
  SEND_FAILED,
  REJECTED,
  CANCELLED_PAYMENT,
  SKIPPED,
  FLAGGED
}
