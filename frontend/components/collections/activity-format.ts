// Collection-activity display helpers shared by the Debtor book (666A, `debtor-book-table.tsx`) and
// the chase-history ledger (fee-note detail + client detail, `collections-history-data-table.tsx`).
// Consolidated in Phase 91 666B (D-74). Pure: no directive, no JSX.

/**
 * Maps a collection-activity status to a Badge variant. Mirrors the precedent in
 * 591B's `collections-client.tsx` and extends it to the full ledger status set:
 *   SENT → success; SEND_FAILED / FLAGGED → destructive; PROPOSED → warning;
 *   REJECTED / CANCELLED_PAYMENT / SKIPPED (and any unknown) → neutral.
 */
export function activityBadgeVariant(status: string) {
  switch (status) {
    case "SENT":
      return "success" as const;
    case "SEND_FAILED":
    case "FLAGGED":
      return "destructive" as const;
    case "PROPOSED":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

/**
 * Generic `SNAKE_CASE`/`snake_case` → "Title Case" formatter. Used for the stage, the status label
 * and the machine-readable `reason`, so newly-added backend statuses/reasons render sensibly
 * instead of being dropped by an enum-keyed lookup.
 */
export function formatSnakeCase(value: string): string {
  return value
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
