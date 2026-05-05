# ADR-273: One-Way Accounting Sync (Push Invoices/Customers, Pull Payments) — Permanent

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 71 introduces the first real accounting integration. The product question is what direction(s) the sync runs in. Bidirectional sync between two systems-of-record is the largest single source of multi-tenant SaaS support pain in this category — last-write-wins, merge conflicts, "who is the source of truth," and the resulting 3am support escalations. The founder has made an explicit, recorded product call (2026-05-03 ideation): one-way push for invoices and customers (Kazi → Xero), one-way pull for payment status (Xero → Kazi), no merge logic, no conflict UI, no exception.

The two systems' overlapping responsibilities are real: Xero is the firm's general ledger, Kazi is the firm's practice-management + invoicing system. Both can theoretically own the customer record, the invoice record, and the payment record. Bidirectional sync would let either side originate either record. But for Kazi's ICP — small SA accounting / legal / consulting practices — the work originates in Kazi (time entries, matters, projects) and the bookkeeper's job is to *receive* it in Xero. The accountant rarely originates customer records or invoices in Xero except when consulting for a non-Kazi client; in that case those records are out-of-scope for Kazi sync.

**Options Considered**:

1. **One-way push (invoices, customers) + one-way pull (payments) — as designed.** Kazi originates customers and invoices; Xero is a target. Xero originates payments (because the bank feed lives there); Kazi receives them.
   - Pros: No merge code. No conflict UI. No "who wins" rules. Source-of-truth is unambiguous per record class.
   - Pros: Implementation budget fits 10 slices.
   - Pros: Future debuggability — every sync row has a clear direction and a clear cause.
   - Cons: Customer records created directly in Xero (e.g. by the accountant, outside Kazi) do not auto-appear in Kazi. Mitigated by the one-shot import on connect (covers the migration moment).
   - Cons: Invoices issued directly in Xero do not appear in Kazi at all (permanent — accepted).

2. **Bidirectional with last-write-wins.** Both sides can originate; conflicts resolved by `updated_at` comparison.
   - Pros: Either side can be authoritative.
   - Cons: Last-write-wins silently destroys edits. Worst-case outcome: a bookkeeper edits a customer's address in Xero at 14:00; an admin in Kazi edits the same customer's address at 14:01 (different change); next sync window the admin's edit overwrites the bookkeeper's silently. No audit trail for "the change you made yesterday no longer exists."
   - Cons: Requires bi-directional change detection and conflict resolution logic — a major engineering investment.
   - Cons: Founder explicitly rejected this model.

3. **Bidirectional with conflict-resolution UI.** Conflicts surface to the user for manual resolution.
   - Pros: No silent data loss.
   - Cons: Conflict UI is one of the highest-friction surfaces in B2B SaaS — users avoid it, conflicts pile up, support load explodes.
   - Cons: Required engineering: conflict detector, queue, UI, audit. Easily 20+ slices on its own.
   - Cons: Founder explicitly rejected this model.

**Decision**: Option 1 — one-way push for invoices + customers (Kazi → Xero), one-way pull for payments (Xero → Kazi). Permanent product decision.

**Rationale**: For Kazi's ICP, the source-of-truth pattern is naturally one-way per record class. Time entries and invoices are *originated* in Kazi (the practice-management side) and consumed by the bookkeeper in Xero. Payments are *originated* in Xero (because the bank feed lives there) and consumed by Kazi for AR-aging accuracy. The model maps to how the work actually flows in a small SA professional-services firm, so users will not be surprised by it.

The trade-off accepted: customer / invoice records originated in Xero (e.g. by the firm's accountant for a non-Kazi client) do not flow to Kazi. This is a feature, not a bug — those records are out of scope for Kazi anyway. The one-shot customer import on connect handles the existing-customer migration moment.

This is a *permanent* decision per the founder; future reversal would be a major product architecture change, not an ADR superseding this one.

**Consequences**:

- Positive: Zero merge code. Zero conflict UI. Zero "last-write-wins" support escalations.
- Positive: Source-of-truth is documentable in a one-paragraph onboarding tooltip ("Kazi is where invoices and customers live; Xero is where payments live").
- Positive: Sync state per record is one-dimensional — either "did this push succeed" or "did this pull succeed".
- Negative: Customers created in Xero outside the one-shot import window will not appear in Kazi. The user must either re-create them in Kazi or disconnect+reconnect to re-trigger import (which creates duplicates of the already-imported set — accepted limitation).
- Negative: Time entries are not pushed (permanent decision). Firms that bill hours in Xero cannot use Kazi's time-tracking with Xero billing. This is by design — Kazi is the system of record for billable time.
- Neutral: When the user side actively wants bidirectional ("I edited the Xero customer; please pull"), the answer is "edit it in Kazi instead; the change pushes." Documentation and onboarding lean into this framing.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (provider scope), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync engine), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) (pull mechanism), [ADR-278](ADR-278-idempotent-push-via-external-reference.md) (idempotency).
