package io.b2mash.b2b.b2bstrawman.timeentry;

/**
 * Derived billing status for a time entry. Not persisted — computed from the entry's {@code
 * billable} flag and {@code invoiceId} FK.
 *
 * <ul>
 *   <li>{@code UNBILLED} — billable = true AND invoiceId IS NULL (available for invoicing)
 *   <li>{@code BILLED} — invoiceId IS NOT NULL (locked, part of an active invoice)
 *   <li>{@code NON_BILLABLE} — billable = false (never invoiceable)
 * </ul>
 */
public enum BillingStatus {
  UNBILLED,
  BILLED,
  NON_BILLABLE
}
