# ADR-072: Admin-Triggered Period Close

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Retainer agreements operate on a periodic basis — monthly, quarterly, etc. At the end of each period, the system must finalize consumption data, calculate overage and rollover, generate an invoice, and open the next period. The question is whether this period close operation should happen automatically (scheduled job) or require explicit admin action.

The platform already has a daily scheduler pattern (ADR-071) for recurring schedule execution. It would be technically straightforward to add period closing to the same batch job. However, period closing is fundamentally different from project creation — it generates invoices (financial documents sent to customers) and calculates billing amounts that affect revenue recognition.

Professional services firms commonly review time entries at period end: verifying billable hours, correcting entries, ensuring nothing was missed. This review window is unpredictable — some firms close periods the day after, others wait a week for stragglers to submit timesheets.

**Options Considered**:

1. **Admin-triggered period close (chosen)** — The system detects when a period's end date has passed and surfaces it as "Ready to Close" in the dashboard. An admin reviews and clicks "Close Period" to finalize.
   - Pros: Human oversight on every financial operation — no surprise invoices; accommodates flexible review windows — firms close when ready, not when a scheduler fires; admin sees a preview before committing (consumed hours, overage, invoice lines); late time entries can be added before closing; aligns with how accounting firms already work (manual month-end close).
   - Cons: Periods can stay open indefinitely if admin doesn't act; requires dashboard attention and manual action; no guaranteed invoice cadence.

2. **Automated period close via scheduled job** — A daily scheduler (like ADR-071) checks for periods past their end date and auto-closes them, generating invoices automatically.
   - Pros: Zero admin effort — invoices generate on schedule; guaranteed cadence — clients receive invoices consistently; simpler UX — no "close period" workflow needed.
   - Cons: No review window — late time entries after auto-close require credit notes or invoice amendments; invoice generation without human review risks errors (wrong hours, missing entries); auto-generated invoices may not match what the firm intended to bill; undoing an auto-close is complex (void invoice, reopen period, re-close); firms with variable billing practices would need to pause/resume constantly.

3. **Hybrid — auto-close with grace period** — Period auto-closes N days after end date (configurable grace period), with option for early manual close.
   - Pros: Balances automation with review time; configurable per-firm.
   - Cons: Adds configuration complexity (grace period per retainer? per org?); still auto-generates invoices without explicit approval; "N days" doesn't account for holidays, sick days, or variable firm schedules; edge cases when admin edits during grace period.

**Decision**: Option 1 — admin-triggered period close.

**Rationale**:

Invoice generation is a financial operation with direct customer impact — an incorrect invoice damages client relationships and creates accounting reconciliation work. In the professional services domain, period-end review is a standard practice, not an overhead to be eliminated. Firms routinely:

- Wait for team members to submit final timesheets (especially contractors or part-time staff)
- Review entries for correct billable/non-billable classification
- Adjust allocations between projects or tasks
- Verify that retainer hours align with client expectations before billing

An automated system would need to handle all of these scenarios through compensating mechanisms (credit notes, amended invoices, reopen flows), which adds more complexity than the manual close it attempts to eliminate.

The system makes the manual process efficient through clear UX: the retainer dashboard prominently shows periods ready to close (sorted by how overdue they are), one-click close with a confirmation dialog showing the invoice preview, and escalating notifications to admins when periods remain unclosed. This is "assisted manual" — the system does the calculation and presents it, but the human approves.

The mitigation for forgotten periods is notification escalation: an initial "ready to close" notification when the period ends, followed by reminders at configurable intervals. This ensures periods are not lost, while still requiring human judgment on timing.

**Consequences**:

- Positive:
  - Every invoice is human-reviewed before generation — no surprise bills to customers
  - Firms can accommodate their own review cadence without system constraints
  - Late time entries are naturally handled — just add them before closing
  - No need for credit notes, invoice amendments, or period reopen flows
  - Matches established accounting practices (month-end close is always manual)

- Negative:
  - Periods can remain open indefinitely if admins are negligent (mitigated by escalating notifications)
  - Invoice generation cadence depends on admin discipline, not system guarantees
  - Dashboard requires regular attention — adds to admin workload
  - No "set and forget" option for firms that want fully automated billing (can be added as a future opt-in feature)
