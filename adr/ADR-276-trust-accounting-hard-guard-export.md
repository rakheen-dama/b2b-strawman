# ADR-276: Trust-Accounting Hard Guard Against Accounting Export

**Status**: Accepted

**Date**: 2026-05-03

**Context**: South Africa's Legal Practice Act §86 (and the LSSA / LPC rules under it) requires that a law firm's trust money be ledger-isolated from the firm's operating money. Trust funds are client property held on deposit; they are not the firm's revenue and must not be commingled with operating-account general-ledger flows. Phase 60 ships Kazi's trust-accounting subsystem (`verticals/legal/trustaccounting/` — `TrustAccount`, `TrustTransaction`, `TrustReconciliationService`, `TrustLedger`) which maintains an isolated ledger inside Kazi.

Phase 71 introduces general-ledger export to Xero. If a trust-related invoice were pushed into Xero's operating-account general ledger, that would be a regulatory breach — even if the firm later reconciled it correctly. The breach happens at the moment of mis-recording, not at the moment of mis-payment. The product must therefore refuse to export trust-related invoices to Xero.

The refusal must be deterministic, fail-closed, and auditable. It cannot be a tenant-configurable flag (a misconfigured tenant would breach the regulation), and it cannot be a probabilistic AI check (the Compliance Assistant is deferred to Phase 72+, and §86 is regulatory not heuristic).

**Options Considered**:

1. **Hard-coded fail-closed Java guard with three-tier evaluation.** A `TrustBoundaryGuard` service with deterministic checks: (1) `Invoice.isTrustInvoice == true`, (2) any `InvoiceLine` linked to a `TrustAccount`, (3) customer has any non-zero open trust balance via `TrustLedger`. Evaluated *before* the sync entry is written; refusal produces a `BLOCKED_TRUST_BOUNDARY` sync entry and an audit event. No bypass capability. No UI override.
   - Pros: Deterministic — same input, same output, every time.
   - Pros: Fail-closed — if the guard's data sources are unreachable, refuse the export rather than allow.
   - Pros: Auditable — every refusal writes an audit event with the reason code and detail.
   - Pros: Cannot be disabled by tenant misconfiguration.
   - Cons: A new trust-related field added to `Invoice` post-71 must remember to extend the guard. Mitigation: documented.

2. **Tenant-configurable allow/block flag on `OrgIntegration.config_json`.** Tenants opt in to trust-export with a checkbox.
   - Pros: Tenants who *want* to export some trust line for an unusual reason can.
   - Cons: A misconfigured tenant breaches §86. The product is the regulator's enforcement layer, not the tenant's preference.
   - Cons: "Default off" is no defence if "default on" is one click away — the regulator's view is "did the system permit the breach?"
   - Cons: No reasonable use-case for "yes export trust to Xero." Trust ledgers belong in the trust subsystem, not the operating GL.

3. **AI-mediated check via Compliance Assistant.** A specialist AI agent decides whether each invoice is trust-related and refuses if so.
   - Pros: Could catch novel patterns (e.g. trust-implied disbursement descriptions).
   - Cons: Probabilistic. False negatives breach §86. Unacceptable.
   - Cons: Compliance Assistant is deferred to Phase 72+ in the Phase 70 roadmap.
   - Cons: Even when shipped, the assistant should *augment* a deterministic guard, not replace it.

**Decision**: Option 1 — hard-coded fail-closed Java guard with three-tier evaluation. No bypass. No tenant override. No AI in the loop.

**Rationale**: §86 is a regulatory boundary, not a product preference. The product's job is to make breach impossible by construction. Deterministic Java code is auditable in source review; tenant-configurable flags are not (they require runtime audit of every tenant's settings). AI inference cannot be relied on for regulatory boundaries because false negatives compound.

The three-tier evaluation order (flag → line linkage → customer balance) is short-circuit, ordered cheapest-first. The flag check is a single boolean read; line-linkage is one indexed query; customer-balance is one balance-lookup. The guard runs *before* the sync entry is written, so a refused invoice never enters the drain queue. Refusal produces a `BLOCKED_TRUST_BOUNDARY` sync-entry row (visible in the sync log) and a `integration.xero.push_blocked_trust` audit event with the reason code and detail. The invoice itself is unaffected — only the Xero push is refused.

The audit event is the regulator-facing artefact: "the system was asked to push invoice X to Xero on date D; it refused because tier-N check matched; here is the actor and the entity state at the moment of refusal." This is sufficient for compliance review.

The guard cannot be bypassed via UI (no "force push" button for trust-blocked invoices) or via API (the bypass capability simply does not exist). The decision to never ship a bypass is itself part of this ADR — future phases may not introduce one without a new ADR superseding this.

**Consequences**:

- Positive: Regulatory breach by construction is impossible.
- Positive: Source-reviewable enforcement — auditors can read the guard code in 30 lines.
- Positive: Audit trail for every refusal.
- Positive: Trust-vs-operating boundary is a single Java service; future Phase-72+ Compliance Assistant can augment but not replace it.
- Negative: A novel trust-pattern not captured by the three tiers (e.g. a future "trust-related" tag on a custom field) could leak through. Mitigation: the three tiers cover all current Phase 60 trust state. New trust-related fields must extend the guard explicitly — documented in the guard's class-level Javadoc.
- Negative: A misclassified non-trust invoice that happens to have a customer with an unrelated open trust balance gets refused. Mitigation: rare in practice; user re-categorises the trust balance and re-pushes. Operational impact accepted.
- Neutral: The guard runs in the same process as the sync service; no network call. Latency is negligible.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync entry semantics), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (per-tenant trust ledger), Phase 60 trust-accounting subsystem ADRs.
