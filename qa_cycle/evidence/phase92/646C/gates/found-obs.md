# 646C OBS — found items (reported, NOT fixed)

No new defect in 646C's scope. Every figure on the review step agreed with the API and with read-only SQL
in all three observed states (`obs/obs-result.json` → `states[*].table`).

Two observations follow. The first is already tracked. The second is an ordering note: I observed what
happened, but the harm it could cause is a hypothesis I did not reproduce.

## O-1 — The manual-match pane offers only APPROVED transactions (already tracked: 652B)

- **Seen in:** `obs/05-b-01-review-opened.png` (and `.txt`).
- **What happened:** the "Unmatched Trust Transactions" pane listed only `FEE/MOROKA/… R 8 500,00`. The
  RECORDED QuickCollect deposit `DEP/QC/…` (R 45 000, posted, unlinked) was missing, although the same
  review step counts it as the R 45 000 outstanding deposit.
- **Cause:** `fetchTransactions(…, { status: "APPROVED" })` in
  `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx`. This predates 646C.
- **Tracked:** `tasks/phase92-trust-books-provably-right.md` 652B / 652.4 ("manual-match pane lists only
  APPROVED"). Nothing new to file.

## O-2 — The period-end refusal comes after the statement has been imported and auto-matched (hypothesis, low)

**Seen in:** `obs/08-d1-refusal-statement-after-period-end.png`, `obs-sums.out` [final: bank statements].

**Steps:**
1. Choose period end 2026-11-14.
2. Upload a statement whose last line is dated 2026-11-17. The import succeeds.
3. Run Auto-Match.
4. Click Review Matches. Only now does `createReconciliation` refuse, with "This statement has lines
   dated after 14 November 2026…".

**What I observed:**
- The refused statement stays imported against the account (2 statements in the table).
- No reconciliation row is created (count stayed at 1).

**Hypothesis (not reproduced):** auto-match runs on that statement before the refusal. Any links it makes
(`bank_statement_lines.trust_transaction_id`, `trust_transactions.bank_statement_line_id`) would persist
even though no reconciliation uses the statement. A link to a line dated on or before another draft's
period end removes that transaction from the draft's outstanding items.

In this run auto-match linked 0 lines, because the amounts were chosen not to match anything. So no harm
was observed, and per the audit-hypothesis rule this needs an integration test before anyone acts on it.
It may also be intended: a real statement line is valid bank evidence whatever the period.

**Suggested owner:** 652C (wizard defects), or no action if the order is intended.
