package io.b2mash.b2b.b2bstrawman.invoice;

/**
 * Invoice lifecycle status enum. Enforces valid state transitions for invoice workflow.
 *
 * <p>Valid transitions:
 *
 * <ul>
 *   <li>DRAFT → APPROVED (explicit approval)
 *   <li>APPROVED → SENT (sent to customer)
 *   <li>APPROVED → VOID (cancelled before sending)
 *   <li>SENT → PAID (payment recorded)
 *   <li>SENT → VOID (cancelled after sending)
 *   <li>PAID and VOID are terminal states (no transitions out)
 * </ul>
 *
 * <p>Invalid transitions include: DRAFT → SENT, DRAFT → PAID, APPROVED → DRAFT, etc.
 */
public enum InvoiceStatus {
  /** Draft invoice — editable, no invoice number assigned. */
  DRAFT,

  /** Approved — invoice number assigned, no longer editable, ready to send. */
  APPROVED,

  /** Sent to customer — awaiting payment. */
  SENT,

  /** Payment received — terminal state. */
  PAID,

  /** Cancelled/voided — terminal state. Time entries can be re-invoiced. */
  VOID;

  /**
   * Checks if a transition from the current status to the target status is valid.
   *
   * @param target the target status
   * @return true if the transition is allowed, false otherwise
   */
  public boolean canTransitionTo(InvoiceStatus target) {
    return switch (this) {
      case DRAFT -> target == APPROVED;
      case APPROVED -> target == SENT || target == VOID;
      case SENT -> target == PAID || target == VOID;
      case PAID, VOID -> false; // Terminal states
    };
  }
}
