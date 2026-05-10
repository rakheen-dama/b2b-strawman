# ADR-273: One-Way Accounting Sync Model (Permanent)

**Status**: Accepted

**Context**:

Phase 71 introduces a data sync path between Kazi and Xero. The sync model determines which system is the source of truth for each entity type and in which direction data flows. This decision has deep implications for conflict resolution complexity, data freshness guarantees, and the operational burden on small firms.

Kazi manages three entity types that have counterparts in Xero: customers (Xero contacts), invoices (Xero invoices), and payments (Xero payments / invoice payment allocations). The question is whether data should flow bidirectionally (changes in either system propagate to the other) or unidirectionally (one system is the source of truth per entity type, and data flows in one direction only).

The founder made an explicit, permanent product decision during the 2026-05-03 ideation session: Kazi is the source of truth for customers and invoices (push to Xero), and Xero is the source of truth for payment reconciliation (pull to Kazi). This is not a v1 simplification to be revisited — it is the intended long-term model. Time entries never sync; they stay in Kazi as the system of record. This ADR records the rationale and trade-offs of that decision.

**Options Considered**:

1. **One-way push for invoices/customers, one-way pull for payments (CHOSEN)** — Kazi pushes invoices and customer records to Xero on domain events (approval, creation, update). Xero payment status is polled and pulled back to Kazi to update AR aging. No data flows from Xero to Kazi for invoices or customers after the initial one-time customer import. No data flows from Kazi to Xero for payments.
   - Pros:
     - No conflict resolution needed. Each entity type has exactly one source of truth. An invoice changed in both systems simultaneously is impossible because Xero's copy is read-only (the Xero user cannot edit an invoice pushed from Kazi without breaking the reference link — and if they do, the drift is detected and surfaced, not auto-resolved).
     - Dramatically simpler sync engine. The `AccountingSyncService` only needs to handle push (enqueue on event, drain queue, call adapter) and pull (poll on schedule, match by reference, write `PaymentEvent`). No merge logic, no last-writer-wins, no vector clocks, no conflict UI.
     - Matches the real-world workflow. In a small SA professional-services firm, the practitioner creates the invoice in the practice-management system and the bookkeeper sees it in Xero. The bookkeeper records the payment in Xero when it clears the bank. This is the natural direction of data flow — the integration mirrors it.
     - Payment pull solves the actual business problem: Kazi's AR aging is stale because Kazi does not know when a client paid. Pulling payment status from Xero closes this gap without requiring Kazi to own payment recording.
     - The push model is idempotent by design ([ADR-278](ADR-278-idempotent-push-via-external-reference.md)): re-pushing an invoice updates the Xero record rather than creating a duplicate. This makes retry safe and eliminates the "ghost invoice" class of bugs.
   - Cons:
     - If a bookkeeper corrects an invoice amount directly in Xero (e.g., a rounding adjustment), that correction does not flow back to Kazi. Kazi's invoice total and Xero's invoice total diverge. This is detected as `RECONCILE_DRIFT` and surfaced in the UI, but the user must manually reconcile.
     - Customer data updated in Xero (e.g., a new email address entered by the bookkeeper directly in Xero contacts) does not flow back to Kazi. The Kazi customer record becomes stale for fields that the bookkeeper manages in Xero. Mitigated by the fact that Kazi is positioned as the primary customer record for practice-management data — the bookkeeper should update the customer in Kazi, and the change pushes to Xero.
     - Payment pull has a latency window (up to 15 minutes by default, configurable to 5 minutes). A payment recorded in Xero at 10:01 may not appear in Kazi until 10:16. For AR aging and reporting this is acceptable, but it means the Kazi invoice detail page shows "unpaid" for a few minutes after payment.

2. **Bidirectional sync for invoices and customers** — Changes in either Kazi or Xero propagate to the other system. A conflict resolution strategy (last-writer-wins, Kazi-wins, or manual resolution) handles simultaneous edits.
   - Pros:
     - Data is always fresh in both systems. A bookkeeper who corrects an invoice in Xero sees the correction reflected in Kazi within the sync interval.
     - Customer data stays consistent across systems regardless of where it is edited.
     - Theoretically the "most complete" integration — both systems always agree.
   - Cons:
     - Conflict resolution is the dominant complexity. Two users editing the same invoice simultaneously (one in Kazi, one in Xero) produces a conflict. Every conflict resolution strategy has failure modes: last-writer-wins silently drops edits; Kazi-wins ignores legitimate bookkeeper corrections; manual resolution creates a queue of conflicts that small firms will never process.
     - Bidirectional sync requires change-detection on the Xero side (webhooks or polling for modified-since), change-detection on the Kazi side (domain events), and a merge layer that compares timestamps, field-level diffs, and version vectors. This is a 3–5x increase in sync-engine complexity compared to one-way push.
     - Xero's webhook delivery is unreliable ([ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)). Bidirectional sync that depends on Xero webhooks for timely change notification inherits that unreliability. Falling back to polling for inbound changes adds the same latency as one-way pull but with the added complexity of merge logic.
     - The tax-code mapping introduces a lossy transformation. A Kazi invoice line with tax mode `STANDARD_15` is pushed as Xero tax code `OUTPUT2`. If a bidirectional sync pulls that invoice back, the reverse mapping must recover `STANDARD_15` from `OUTPUT2` — which works for the default mapping but breaks if the tenant has customized the mapping (multiple Kazi modes could map to the same Xero code). This makes round-trip fidelity impossible in the general case.
     - Small firms do not need bidirectional sync. The practitioner works in Kazi; the bookkeeper works in Xero. They do not edit the same invoice in both systems. The "concurrent edit" scenario that drives conflict resolution complexity is a theoretical concern, not an observed workflow.

3. **Event-sourced bidirectional sync with CRDT-style merge** — Both systems emit events; a sync mediator maintains an event log and applies CRDT (conflict-free replicated data type) merge rules to produce a consistent state.
   - Pros:
     - Theoretically eliminates conflicts by design — CRDTs guarantee convergence without coordination.
     - Full audit trail of every change in both systems, with causal ordering.
     - Academically elegant; proven in distributed-systems literature.
   - Cons:
     - Massive over-engineering for the problem. Kazi is a practice-management tool for 5–30 person firms, not a distributed database. The engineering effort to implement CRDT merge for invoices (with line items, tax codes, dates, amounts, and status transitions) is disproportionate to the business value.
     - Xero does not emit CRDT-compatible events. Xero's API is a REST CRUD surface, not an event log. Retrofitting CRDT semantics onto Xero's change-detection (modified-since polling or webhooks) requires a translation layer that introduces the same complexity Option 2 has, plus CRDT overhead.
     - No off-the-shelf CRDT library handles the domain semantics of invoices (status transitions are order-dependent, not commutative; you cannot merge APPROVED and VOID — the result depends on which happened first).
     - Developer onboarding cost is extreme. The team maintaining this system must understand CRDTs, causal ordering, and merge semantics. For a practice-management integration, this is unjustifiable.

**Decision**: Option 1 — One-way push for invoices and customers (Kazi to Xero), one-way pull for payments (Xero to Kazi). This is a permanent product decision, not a v1 simplification.

**Rationale**:

The one-way model is not a compromise — it is the correct architecture for the problem domain. Practice-management systems and accounting systems have different owners within a firm. The practitioner (lawyer, accountant, consultant) creates invoices and manages client records in the practice-management system. The bookkeeper processes payments and manages the general ledger in the accounting system. Data flows from the practitioner's tool to the bookkeeper's tool (invoices, customers) and from the bookkeeper's tool back to the practitioner's tool (payment status). This is the natural workflow; the integration mirrors it.

Bidirectional sync (Option 2) solves a problem that does not exist in the target market. Small SA professional-services firms do not have workflows where the same invoice is edited concurrently in both Kazi and Xero. The bookkeeper receives the invoice from Kazi and processes it as-is. If the bookkeeper needs to adjust the invoice (rare — typically a rounding correction or a tax code override), they do so in Xero and the adjustment stays in Xero. Kazi detects the divergence as `RECONCILE_DRIFT` and surfaces it — the practitioner can then decide whether to update the Kazi record. This explicit-drift-detection approach is more transparent and less error-prone than silent merge logic.

The payment-pull direction is equally natural. Payments clear the bank account, which is reconciled in Xero. Xero knows when an invoice is paid before Kazi does. Pulling that status into Kazi keeps the AR aging report current without requiring the practitioner to manually mark invoices as paid in Kazi — which they consistently forget to do, leading to permanently stale receivables data.

The permanence of this decision is important. Bidirectional sync is not on the roadmap for Phase 72, 73, or any foreseeable phase. The sync engine, the UI, the audit trail, and the error handling are all designed around the one-way model. Future phases may add more entity types to the push path (e.g., credit notes in Phase 72+) or reduce the payment-pull latency (webhooks in Phase 72+), but the direction of flow is fixed.

**Consequences**:

- Positive:
  - Sync engine complexity is manageable for a small team. Push is event-driven (domain event -> enqueue -> drain -> call adapter). Pull is schedule-driven (poll -> match -> write `PaymentEvent`). No merge logic, no conflict UI, no version vectors.
  - Each entity type has exactly one source of truth. Debugging "why does Kazi show X but Xero shows Y" is straightforward: check the sync entry for the push result, or check the last poll for the pull result. There is no third "merged" state to reason about.
  - The `AccountingSyncService` can be built and tested in isolation. Push tests verify that a Kazi invoice produces the correct Xero payload. Pull tests verify that a Xero payment produces the correct `PaymentEvent`. No cross-system merge tests needed.
  - Drift detection (`RECONCILE_DRIFT` state on sync entries) is an explicit, auditable surface. When the Xero side diverges from the Kazi side, the system tells the user rather than silently resolving the conflict.

- Negative:
  - Bookkeeper corrections made directly in Xero do not flow back to Kazi. The Kazi invoice record may become stale for fields the bookkeeper adjusts. Users must be educated that Kazi is the source of truth for invoices and customers — edits should be made in Kazi, not in Xero.
  - Payment pull latency (5–15 minutes) means Kazi's "paid" status lags behind Xero's. For real-time AR dashboards, this is noticeable but acceptable. The alternative (Xero webhooks) has reliability issues documented in [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md).
  - The one-time customer import (Section 5 of Phase 71 spec) is the only inbound customer flow. After import, customer data in Xero that diverges from Kazi is not detected or reconciled. Firms that maintain customer records in Xero will find their Xero contacts drifting from their Kazi customers over time.

- Neutral:
  - Time entries never sync in either direction. They remain in Kazi as the system of record. This is a permanent scope boundary, not a Phase 71 limitation.
  - The `AccountingProvider` port ([ADR-088](ADR-088-integration-port-package-structure.md)) has `syncInvoice` and `syncCustomer` methods (push) and the new `AccountingPaymentSource` port ([ADR-279](ADR-279-sibling-payment-source-port.md)) has `getPaymentsModifiedSince` (pull). Neither port has methods for inbound invoice or customer sync — the API surface enforces the one-way model at the interface level.

- Related: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero-only adapter), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (dedicated sync service), [ADR-277](ADR-277-poll-over-webhooks-payment-reconciliation-v1.md) (poll over webhooks for payment pull), [ADR-278](ADR-278-idempotent-push-via-external-reference.md) (idempotent push), [ADR-279](ADR-279-sibling-payment-source-port.md) (payment source port), [ADR-088](ADR-088-integration-port-package-structure.md) (integration port structure).
