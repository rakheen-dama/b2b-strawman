# ADR-274: Dedicated `AccountingSyncService` Instead of Phase 37 Rule Engine

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 37 shipped a rule-based automation engine with eight event-triggered rule types and six action executors. Phase 70 added an `INVOKE_AI_SPECIALIST` action type and a `SCHEDULED` trigger. A natural-seeming question for Phase 71 is: should "push invoice to Xero on approval" be a Phase 37 rule with an `INVOKE_ACCOUNTING_SYNC` action? That would slot accounting sync into existing infrastructure.

The semantics required by accounting sync — retry with exponential back-off, idempotency, rate-limit observance, dead-letter, refresh-on-401, in-transaction outbox writes — do not align with the rule engine's `ActionExecution` model. The rule engine is designed for "fire this action when this event happens, retry once on failure, log the result." Accounting sync needs "enqueue this work durably alongside the triggering state change, drain it on a schedule, classify failures by HTTP class, back-off per Xero's `Retry-After`, fail to dead-letter on validation errors, refresh-and-retry on 401."

A second consideration: when an invoice push fails with a Xero `429`, where does the user / engineer look to debug? Through the rule-engine `ActionExecution` log? Through a dedicated sync log? The dedicated log is much more discoverable — it surfaces "Xero said wait 60 seconds" cleanly; the rule engine surfaces it as "action failed, retried, succeeded eventually" with the rate-limit detail buried in payload JSON.

A third consideration — and this is the load-bearing one — is the **transactionality of the trigger**. AFTER_COMMIT event listeners (Phase 10's pattern, exemplified by `InvoiceEmailEventListener`) fire-and-log: if the listener crashes between commit and outbox write, the sync is silently lost. For email this is acceptable (resending an invoice email is a known recoverable). For invoice push to a general ledger this is not acceptable — "did we push this invoice to Xero?" is a money-correctness question and the answer must be durable.

**Options Considered**:

1. **Dedicated `AccountingSyncService` with in-transaction outbox + drain worker.** The outbox row is written inside the same `@Transactional` block as the invoice state change; a separate `@Scheduled` worker drains the outbox using `SELECT … FOR UPDATE SKIP LOCKED`. Customer push (lower stakes) uses the AFTER_COMMIT event-listener path. No coupling to Phase 37.
   - Pros: Outbox commit is atomic with invoice approval; cannot drift.
   - Pros: Retry / back-off / rate-limit / dead-letter logic owned by the sync service; not constrained by `ActionExecutor` shape.
   - Pros: Sync log is a first-class entity (`accounting_sync_entry`), not buried in `ActionExecution.payload`.
   - Pros: Worker mirrors `AutomationScheduler`'s `TenantScopedRunner.forEachTenant()` shape — same multi-tenant scheduler primitive, different consumer.
   - Cons: Two code paths for "external system call after a domain event" (rule engine for some, `AccountingSyncService` for accounting). Mitigated by being explicit about the boundary.

2. **Phase 37 rule action `INVOKE_ACCOUNTING_SYNC`.** A new action type that calls `AccountingProvider.syncInvoice` and logs to `ActionExecution`.
   - Pros: One execution model.
   - Pros: User can see the sync in the rule-execution log alongside other automations.
   - Cons: `ActionExecutor`'s retry semantics don't fit Xero (no rate-limit-aware back-off; no Retry-After observance; no per-call idempotency key).
   - Cons: Debugging a Xero `429` through `ActionExecution.payload` is much worse than through a dedicated sync log.
   - Cons: AFTER_COMMIT trigger is still single-attempt; the durability gap remains.
   - Cons: Couples accounting integration release cadence to rule-engine release cadence.

3. **Hybrid — events trigger outbox; rules can also enqueue.** AFTER_COMMIT events write outbox rows; Phase 37 rules can also call `AccountingSyncService.enqueue*` via an `INVOKE_ACCOUNTING_SYNC` action.
   - Pros: Best of both — owners get the dedicated sync engine, rule authors get a way to trigger ad-hoc syncs.
   - Cons: Two enqueue paths to test and debug.
   - Cons: AFTER_COMMIT durability gap not solved (still need the in-transaction outbox).
   - Cons: Defers the cleaner Option-1 architecture for marginal benefit. Phase 71 has no concrete rule-action use-case yet.

**Decision**: Option 1 — dedicated `AccountingSyncService` with in-transaction outbox writes for invoice push. AFTER_COMMIT event-listener path is acceptable for customer push (lower stakes). No `INVOKE_ACCOUNTING_SYNC` rule action in v1.

**Rationale**: The transactionality requirement is the load-bearing constraint. For invoice push, the outbox row MUST commit atomically with the invoice state change so that any in-flight crash leaves the system in a recoverable state ("either both committed or neither committed"). The Phase 10 `InvoiceService.approveInvoice(...)` method gains a direct call to `AccountingSyncService.enqueueInvoicePush(...)` inside its `@Transactional` block; the existing `InvoiceApprovedEvent` listener path is unchanged for downstream consumers (audit, notifications) but is **not** the trigger for sync.

For customer push, drift between Kazi and Xero is correctable on the next change (a stale customer name in Xero is annoying, not a money error). The cost-of-loss is much lower, so AFTER_COMMIT event-listener path is acceptable. This deliberate asymmetry is documented in §N.3.3 and §N.3.5 of the Phase 71 architecture doc.

Outbox rows carry an explicit `action` column (`UPSERT` / `VOID` / `PULL`) populated by the in-transaction enqueue call site. `InvoiceService.voidInvoice(...)` enqueues an entry with `action='VOID'` in its `@Transactional` block; the drain worker reads `action` and dispatches to `provider.syncInvoice(...)` with `payload.status='VOIDED'`, where `XeroAccountingProvider` branches on status to call Xero's void endpoint. This keeps the `AccountingProvider` port surface minimal (no `voidInvoice` method) while making the action explicit for auditability and for future adapter implementations.

The retry / back-off / rate-limit / dead-letter semantics are owned by `AccountingSyncService` and `AccountingPushDrainWorker`. The drain worker mirrors `AutomationScheduler`'s shape — `TenantScopedRunner.forEachTenant()` + `TransactionTemplate` + `SELECT … FOR UPDATE SKIP LOCKED` — but does not invoke `ActionExecutor`. A future Phase 72+ could add an `INVOKE_ACCOUNTING_SYNC` rule action that simply enqueues into the same outbox; the foundational machinery is reusable.

**Consequences**:

- Positive: Outbox commit is atomic with invoice state change. No in-flight loss window.
- Positive: Retry semantics are tuned to Xero (Retry-After, exponential back-off, validation-fast-fail).
- Positive: Sync log (`accounting_sync_entry`) is a first-class entity with first-class UI.
- Positive: Worker code is small and isolated — easy to maintain.
- Negative: Two "fire external action on domain change" patterns coexist (rule engine + sync engine). Documented; not a problem in practice because the boundary is clear.
- Negative: Rule authors cannot ad-hoc trigger Xero sync from a Phase 37 rule in v1. Accepted; revisit Phase 72+ if a real use-case emerges.
- Neutral: The drain worker `@Scheduled(fixedDelay=30_000)` is a separate Spring scheduler from `AutomationScheduler` — they coexist without contention.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md), [ADR-271](ADR-271-scheduled-trigger-extension.md) (Phase 70 SCHEDULED trigger — explicitly **not** reused here), [ADR-275](ADR-275-oauth2-augmentation-org-integration.md), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md), [ADR-278](ADR-278-idempotent-push-via-external-reference.md), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md).
