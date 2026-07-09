# ADR-325: Collections Domain & Dunning Engine — Derived Overdue, Ledger Idempotency, Policy-as-Embeddable, Scan-on-Job-Queue

**Status**: Accepted

**Context**:

The revenue engine ends at `SENT`: nothing chases an unpaid invoice, and `InvoiceStatus` (`DRAFT, APPROVED, SENT, PAID, VOID`) has no overdue notion — the `invoice-aging` report derives it at query time. Phase 83 adds a dunning engine: a graduated reminder policy, a per-customer exclusion, a chase ledger, a daily scan producing gated reminder drafts, and cancellation the moment an invoice is paid via any of the three payment routes (PSP webhook → `PaymentReconciliationService`, Xero pull → `AccountingPaymentPollWorker`, manual record-payment) — all of which converge on `InvoiceService.recordPayment` and publish `InvoicePaidEvent`. The design questions: how overdue is represented, how repeat-scan idempotency is guaranteed, where the policy lives, how the scan is scheduled, and how payment cancellation is wired.

**Options Considered**:

### A. Representing "overdue"

1. **Keep it derived — `status = SENT AND due_date < today` (CHOSEN)**
   - Pros: zero change to the invoice lifecycle; matches how `InvoiceAgingReportQuery` already computes it; no transition code in payment/void/due-date-edit flows; nothing to backfill or keep consistent.
   - Cons: every consumer re-derives (mitigated: the scan and the debtors query are the only two consumers, both native SQL sharing the same predicate).
2. **Add `OVERDUE` to `InvoiceStatus`** — a scheduled job flips SENT→OVERDUE.
   - Pros: status is directly filterable everywhere; visible in existing status chips.
   - Cons: cross-cuts every invoice flow (payment must handle SENT *and* OVERDUE; void likewise; due-date edits must un-flip); Xero sync, portal read-model, and reports all pattern-match on status today and would need auditing; a derived fact stored as state can go stale between job runs. High blast radius for zero informational gain.
3. **A materialized overdue snapshot table** refreshed by the scan.
   - Pros: fast debtor reads; decouples read shape from `invoices`.
   - Cons: a cache to invalidate on every payment/void/edit; per-tenant volumes (hundreds–thousands of invoices) make the native query trivially fast anyway; premature.

### B. Scan idempotency

1. **A stateful ledger row per `(invoice, stage)` with a UNIQUE index (CHOSEN)** — `collection_activities` rows transition in place (`PROPOSED → SENT | REJECTED | CANCELLED_PAYMENT`; `SKIPPED`/`SEND_FAILED` retryable).
   - Pros: double-proposal is impossible at the DB level regardless of scan re-runs, retries, or overlap; "has stage N been handled?" is one indexed lookup; the ledger doubles as the chase history the customer page and triage need; `@Version` arbitrates the payment-vs-approval race.
   - Cons: transitions-in-place means the row is not an append-only log (mitigated: the append-only trail lives in the audit plane, which records every transition).
2. **Append-only activity log, dedupe by query** — "no reminder if one was sent for this stage".
   - Pros: pure history, no state machine.
   - Cons: idempotency by query is racy without a partial unique index per status subset, which is harder to reason about than one row per stage; distinguishing retryable skips from terminal rejections requires interpreting the newest row per stage — a fold the state machine gives for free.
3. **Idempotency keys on the gate only** (no collections table) — dedupe via `AiExecutionGate` lookups.
   - Pros: one less table.
   - Cons: gates expire and are pruned/reviewed by different rules; sent-history would live in email delivery logs keyed by template; the domain would have no queryable chase record at all. The ledger *is* the product surface, not just a lock.

### C. Where the policy lives

1. **`CollectionsSettings` embeddable on `OrgSettings` (CHOSEN)** — enable flag + 4 stage thresholds, following `TimeReminderSettings`.
   - Pros: exact fit for the established Wave-3.5 pattern (per-tenant single-value config groups); settings API/UI conventions already exist; one NOT NULL boolean avoids the null-reload pitfall; v1 policy is deliberately fixed-shape.
   - Cons: `OrgSettingsSchemaSnapshotTest` pin update required (deliberate friction, accepted); arbitrary per-stage customisation later would outgrow an embeddable (acceptable — explicitly out of scope).
2. **A `collection_policies` entity** — one row per tenant, room for future stage lists.
   - Pros: extensible to arbitrary stages/templates.
   - Cons: a one-row table with joins for nothing today; builds v2 shape speculatively against "smallest correct version" and the fixed-shape founder decision.
3. **Per-customer policies with an org default** — policy entity keyed by customer, nullable.
   - Pros: maximum flexibility.
   - Cons: v1 needs exactly one per-customer knob (exclusion), which a boolean column provides; full per-customer policies multiply the scan's stage arithmetic and the settings UI for a need nobody has stated.

### D. Scheduling & payment cancellation wiring

1. **Job-queue `JobHandler`s + AFTER_COMMIT listeners on existing events (CHOSEN)** — `collections_scan` (daily) / `cash_digest` (weekly) as thin handlers like `AccountingPaymentPollHandler`; cancellation via one `CollectionsPaymentListener` on `InvoicePaidEvent`/`InvoiceVoidedEvent`.
   - Pros: per-tenant fanout, ShedLock/sharding handled by `JobWorker` (Phase 75 exists precisely for this); one listener covers all three payment routes *and future ones* — any path that marks an invoice paid already must publish `InvoicePaidEvent` or invoice email/notifications would be broken; AFTER_COMMIT guarantees no cancellation for rolled-back payments; **no new `DomainEvent`** — the sealed permits-list is untouched.
   - Cons: cancellation is asynchronous-after-commit — a microscopic window where a concurrent approve can still fire (resolved deterministically by PENDING-only gate semantics + `@Version`; see ADR-326).
2. **Raw `@Scheduled` methods** for scan/digest.
   - Pros: less indirection.
   - Cons: violates the Phase 75 convention (per-tenant background work goes through the job queue); re-solves ShedLock/multi-shard fanout ad hoc.
3. **Synchronous cancellation hooks inside the three payment call sites**.
   - Pros: no event latency at all.
   - Cons: three call sites to patch and keep patched (a fourth route silently misses cancellation); couples invoice internals to collections; the event already exists and is the platform's designed seam.

**Decision**: Derived overdue (A1) + stateful `(invoice, stage)` ledger with a UNIQUE index (B1) + `CollectionsSettings` embeddable with a `Customer.collectionsExempt` column (C1) + job-queue handlers and one AFTER_COMMIT payment listener with zero new domain events (D1).

**Rationale**:

1. **Blast radius over convenience.** An `OVERDUE` status touches every invoice flow, the Xero sync mapper, the portal read-model, and report queries; a derived predicate touches two new queries. In a 130-migration codebase the change with the smaller correctness surface wins.
2. **Idempotency must be structural.** The scan will re-run — retries, overlapping schedules, machine restarts are the Phase 75 job queue's normal weather. A DB uniqueness guarantee cannot be raced; a query-based dedupe can. The same row then serves the product need (chase history) — one mechanism, two jobs.
3. **Every piece lands on an existing rail.** Settings → embeddable pattern; scheduling → job queue; cancellation → domain events that all three payment routes already publish. The phase adds one table, one column, five settings columns, and no new event types — maximum reuse was the founder's explicit constraint.

**Consequences**:

- Positive: scan re-runs are provably harmless; chase history is first-class queryable data; cancellation automatically covers any future payment route that publishes `InvoicePaidEvent`; no invoice-domain regression surface; sealed `DomainEvent` untouched.
- Positive: exclusion is one predicate in the scan's candidate query — excluded customers cost nothing and produce no ledger noise.
- Negative: `OrgSettingsSchemaSnapshotTest` and the audit catalogue-count test (36 → 41) need deliberate pin updates — accepted friction that forces review of exactly these changes.
- Negative: a payment reversal (`InvoicePaymentReversedEvent`, invoice back to SENT) does not resurrect `CANCELLED_PAYMENT` stages; chasing resumes at the next un-actioned stage. Documented, acceptable for the rare reversal case.
- Negative: fixed-shape policy means per-stage templates or extra stages need a v2 with a real policy entity; the embeddable would then be migrated into it.
- Related: [ADR-326](ADR-326-gated-send-safety-model.md) (what the scan's output is allowed to do), [ADR-327](ADR-327-ai-reminder-drafting-debtor-triage.md) (how drafts are produced), [ADR-328](ADR-328-weekly-cash-digest.md) (the digest job on the same rails), [ADR-319](ADR-319-inbound-correspondence-domain.md) (entity conventions mirrored).
