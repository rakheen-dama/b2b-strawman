# ADR-274: Dedicated AccountingSyncService Instead of Phase 37 Rule Action

**Status**: Accepted

**Context**:

Phase 71 needs a sync engine to push invoices and customers from Kazi to Xero and to pull payment status from Xero to Kazi. Phase 37 (Workflow Automations) already provides a rule engine with trigger-condition-action semantics, an `ActionExecution` log, and a scheduled trigger infrastructure. The question is whether accounting sync should be implemented as a new action type within the Phase 37 rule engine (e.g., `INVOKE_ACCOUNTING_SYNC`) or as a dedicated `AccountingSyncService` with its own queue, worker, retry logic, and observability surface.

The founder decided during the 2026-05-03 ideation session that sync is not routed through the rule engine. This ADR captures the technical rationale for that decision and the trade-offs involved.

The Phase 37 rule engine is designed for user-configurable, tenant-authored automation rules: "when an invoice is approved AND the customer is tagged VIP, THEN send a notification AND update a custom field." The engine evaluates conditions against event payloads, executes actions, logs results in `ActionExecution`, and provides a rule-execution audit trail. It is optimised for correctness of condition evaluation and visibility of rule firing, not for retry semantics, rate-limit back-pressure, or idempotent external-system calls.

**Options Considered**:

1. **Phase 37 rule action `INVOKE_ACCOUNTING_SYNC`** — Add a new action type to the rule engine. When an `InvoiceApprovedEvent` fires and the rule's conditions match, the action calls the `AccountingProvider` adapter to push the invoice to Xero. The `ActionExecution` log records the result. Retry is handled by the rule engine's existing retry mechanism.
   - Pros:
     - Reuses existing infrastructure. The rule engine already listens to domain events, evaluates conditions, executes actions, and logs results. Adding `INVOKE_ACCOUNTING_SYNC` as an action type is a relatively small code change.
     - Tenants can configure sync behaviour via the existing rule UI: "when invoice is approved AND customer is not tagged 'internal', sync to Xero." The condition layer provides flexibility without custom code.
     - The `ActionExecution` log already provides execution history, error messages, and retry status. The sync-status UI could piggyback on the existing rule-execution-log page.
     - Consistent architecture: all event-driven side effects flow through the rule engine. No parallel event-processing path to maintain.
   - Cons:
     - The rule engine's retry mechanism is not designed for external-API call patterns. Rule actions retry on failure with a fixed delay (currently 3 retries, 30-second intervals). Accounting sync needs exponential back-off (1m, 5m, 15m, 1h, 6h) because Xero rate limits and transient failures require progressively longer waits. Retrofitting exponential back-off into the rule engine would affect all action types, not just sync.
     - Rate-limit handling does not fit the rule executor. When Xero returns a `429 Too Many Requests` with a `Retry-After: 45` header, the sync worker must pause all Xero calls for that tenant connection for 45 seconds. The rule engine has no concept of per-provider rate-limit back-pressure — it processes actions independently. Adding rate-limit awareness to the rule engine would couple it to external-provider semantics that don't apply to other action types (send-email, update-field, create-task).
     - Idempotency requirements are different. A rule action "send notification" is inherently idempotent (sending the same notification twice is annoying but not harmful). A sync action "create invoice in Xero" is catastrophic if not idempotent — duplicate invoices in the client's general ledger. The `external_reference` dedup mechanism ([ADR-278](ADR-278-idempotent-push-via-external-reference.md)) must be tightly integrated with the sync path. Bolting it onto the rule engine's `ActionExecution` would require adding `external_reference` and `external_id` fields to a table designed for rule-action results, not external-system state tracking.
     - The trust-boundary guard ([ADR-276](ADR-276-trust-accounting-hard-guard-export.md)) must run before the push. Implementing it as a rule condition ("if invoice is NOT trust-related") is fragile — a tenant could accidentally delete or disable the rule, bypassing the regulatory boundary. The guard must be hard-coded, not configurable.
     - Debugging Xero `429` errors, `401` token-refresh failures, and `400` validation rejections through the `ActionExecution` log would be miserable. The log is designed for "rule fired, action succeeded/failed" — not for multi-attempt external-API call chains with rate-limit headers, token-refresh side effects, and payload-validation details. A dedicated `accounting_sync_entry` table with sync-specific fields (attempt_count, next_attempt_at, external_reference, external_id, last_error_code) provides a far better debugging and observability surface.
     - Payment pull (Xero to Kazi) has no trigger event. It runs on a schedule (every 15 minutes). The Phase 37 rule engine's `SCHEDULED` trigger could theoretically invoke a "poll Xero for payments" action, but this couples the sync polling interval to the rule engine's scheduler — a different subsystem with different lifecycle, different configuration UI, and different operational concerns. The Phase 71 spec explicitly states: "Do NOT reuse Phase 70's `SCHEDULED` automation trigger."

2. **Dedicated `AccountingSyncService` with its own queue and worker (CHOSEN)** — A standalone Spring `@Service` with an `accounting_sync_entry` table as the work queue. Domain events (via `AccountingSyncEventListener`) enqueue sync entries. A `@Scheduled` worker drains the queue, calls the adapter, handles retries with exponential back-off, observes rate limits, and manages dead-letter entries. Payment pull runs on a separate `@Scheduled` worker. The trust-boundary guard is hard-coded in the enqueue path.
   - Pros:
     - Retry semantics are purpose-built for external-API calls: exponential back-off (1m, 5m, 15m, 1h, 6h), max 5 attempts, dead-letter on exhaustion, never-retry on 400-class errors. These semantics are baked into the worker, not shoehorned into a generic rule-action retry mechanism.
     - Rate-limit back-pressure is first-class. The worker reads `X-Rate-Limit-Remaining` and `Retry-After` headers from Xero responses and adjusts drain speed per tenant connection. No other subsystem is affected.
     - The `accounting_sync_entry` table is designed for sync observability: `state` (PENDING, IN_FLIGHT, COMPLETED, FAILED_RETRYING, DEAD_LETTER, BLOCKED_TRUST_BOUNDARY, RECONCILE_DRIFT), `attempt_count`, `next_attempt_at`, `external_reference`, `external_id`, `last_error_code`, `last_error_detail`. This table powers the sync-log UI directly — no translation layer needed.
     - The trust-boundary guard runs in the `enqueueInvoicePush` method, before a sync entry is persisted. It is hard-coded, not configurable. A tenant cannot disable it via the rule UI because it is not a rule.
     - Payment pull has its own `@Scheduled` worker with its own interval (15 minutes by default, tenant-configurable). It does not share lifecycle or configuration with the rule engine's scheduler.
     - The sync service is a single, focused subsystem. A developer debugging a sync failure reads `AccountingSyncService`, `AccountingSyncWorker`, and `accounting_sync_entry`. They do not need to understand the rule engine's condition evaluator, action executor, or execution-log schema.
   - Cons:
     - Parallel event-processing path. Domain events now have two consumers: the rule engine (for user-configured automations) and the `AccountingSyncEventListener` (for accounting sync). This is a valid architectural concern — two subsystems listening to the same events could lead to ordering issues or duplicate processing. Mitigated by the fact that the two consumers are independent: the rule engine does whatever the tenant's rules say, and the sync listener enqueues a push. They do not interact.
     - More code to maintain. The sync service, worker, and sync-entry entity are new infrastructure. If the rule engine already had the right retry, rate-limit, and observability semantics, reusing it would be less code. But it does not have those semantics, and adding them would be more disruptive than building a focused sync service.
     - No tenant-configurable conditions on sync. A tenant cannot say "only sync invoices above R1000" without custom code. If this need arises, a future phase could add a rule action `INVOKE_ACCOUNTING_SYNC` that calls into the sync service (as a thin bridge, not as the primary sync path).

3. **Hybrid: rule engine triggers sync, dedicated service handles execution** — The rule engine evaluates conditions and fires an `INVOKE_ACCOUNTING_SYNC` action, but the action's implementation delegates to `AccountingSyncService.enqueueInvoicePush()`. The rule engine owns the trigger and condition; the sync service owns the execution, retry, and observability.
   - Pros:
     - Tenants get configurable conditions via the rule UI.
     - The sync service handles retry, rate-limit, and observability with purpose-built logic.
     - Clean separation: rule engine decides *whether* to sync; sync service decides *how* to sync.
   - Cons:
     - Adds a mandatory rule for sync to work. Every tenant must have a rule "when invoice approved, invoke accounting sync" or invoices do not sync. If the rule is accidentally deleted, sync stops silently. This is a footgun for non-technical users.
     - The trust-boundary guard must still be hard-coded in the sync service (not as a rule condition), so the rule engine's condition layer adds flexibility for a use case that does not exist yet (tenant-configurable sync conditions) while adding a failure mode that does exist (deleted rule = broken sync).
     - Two subsystems must be healthy for sync to work: the rule engine must fire the action, and the sync service must drain the queue. A bug in the rule engine's event listener could silently prevent sync entries from being created, and the sync service would show an empty queue — no errors, no dead letters, just silence. Debugging this requires tracing through two subsystems.
     - The `ActionExecution` log would contain an entry for "invoked accounting sync" that is immediately obsolete — the real execution state lives in `accounting_sync_entry`. Two logs for one operation.

**Decision**: Option 2 — Dedicated `AccountingSyncService` with its own queue, worker, retry logic, rate-limit handling, and observability surface. The rule engine is not involved in accounting sync in Phase 71.

**Rationale**:

The rule engine and the sync service solve fundamentally different problems. The rule engine is a user-configurable automation framework: tenants define triggers, conditions, and actions; the engine evaluates and executes them. The sync service is an infrastructure component: it pushes data to an external system with retry, idempotency, rate-limit observance, and dead-letter handling. These are different operational profiles with different failure modes, different retry semantics, and different observability needs.

The strongest argument for separation is the trust-boundary guard. The Legal Practice Act Section 86 boundary ([ADR-276](ADR-276-trust-accounting-hard-guard-export.md)) must be enforced unconditionally — no tenant configuration, no rule conditions, no UI toggle can bypass it. If sync were a rule action, the guard would need to be either a mandatory, undeletable rule condition (which the rule engine does not support — all rules are tenant-editable) or a hard-coded check in the action implementation (which makes the rule layer a pass-through that adds complexity without value). A dedicated sync service with the guard hard-coded in the enqueue path is the simplest and safest design.

The retry and rate-limit semantics seal the decision. Xero's rate limits (60 calls/minute, 5000/day per connection) and transient-failure patterns (429 with Retry-After, 500 on downstream outage) require purpose-built back-pressure logic that the rule engine does not have and should not acquire. Adding per-provider rate-limit awareness to the rule engine would couple a general-purpose automation framework to external-API-specific concerns, violating the single-responsibility principle and making the rule engine harder to maintain for its primary use case (tenant automations).

A future phase (72+) may add a rule action `INVOKE_ACCOUNTING_SYNC` that acts as a thin bridge — the rule evaluates conditions and calls `AccountingSyncService.enqueueInvoicePush()`. This would give tenants configurable sync conditions without coupling the sync engine to the rule engine's execution model. But this is not needed in Phase 71 and should not be pre-built.

**Consequences**:

- Positive:
  - The sync service is purpose-built and self-contained. Retry, rate-limit, idempotency, dead-letter, and trust-boundary logic are co-located in one package (`integration/accounting/sync/`). A developer working on sync does not need to understand the rule engine.
  - The trust-boundary guard is hard-coded and unconditional. No tenant action can bypass it. This satisfies the Section 86 regulatory requirement with the simplest possible enforcement mechanism.
  - The `accounting_sync_entry` table provides a purpose-built observability surface for the sync-log UI. State, attempt count, error codes, and external references are first-class columns, not JSONB fields in a generic execution log.
  - The rule engine remains focused on its primary use case (tenant-configurable automations) without acquiring external-API-specific concerns (rate limits, token refresh, idempotent push).

- Negative:
  - Two event-processing paths exist for domain events: the rule engine's `DomainEventListener` and the sync service's `AccountingSyncEventListener`. Both listen to `InvoiceApprovedEvent` and similar events. This is architecturally clean (independent consumers, no ordering dependency) but requires documentation so future developers understand why two listeners exist.
  - No tenant-configurable sync conditions in Phase 71. A tenant cannot say "only sync invoices for customer X" or "skip invoices below R500." If this need arises, the hybrid approach (Option 3) can be built as an additive feature without changing the sync service.

- Neutral:
  - The `AccountingSyncEventListener` subscribes to the same domain events that the rule engine consumes (`InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoiceVoidedEvent`, `CustomerCreatedEvent`, `CustomerUpdatedEvent`). The two consumers are independent — the rule engine processes rules, the sync listener enqueues sync entries. Neither blocks or depends on the other.
  - The Phase 37 `ActionExecution` table is not used for sync. Sync execution history lives entirely in `accounting_sync_entry`. The two tables serve different subsystems with different schemas and different query patterns.

- Related: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero-only adapter), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way sync model), [ADR-276](ADR-276-trust-accounting-hard-guard-export.md) (trust-boundary guard), [ADR-278](ADR-278-idempotent-push-via-external-reference.md) (idempotent push), [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) (JSONB config precedent in Phase 37), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant — sync entries are tenant-scoped).
