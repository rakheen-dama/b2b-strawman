# ADR-276: Trust-Accounting Hard Guard Against Accounting Export

**Status**: Accepted

**Context**:

Phase 60 introduced trust accounting for the legal-za vertical, implementing the South African Legal Practice Act Section 86 requirements. Trust accounts hold client money in a fiduciary capacity — the firm does not own these funds, and they must be kept strictly separate from the firm's operating account. The trust ledger, trust transactions, client ledger cards, reconciliation runs, and interest allocations are all implemented as tenant-scoped entities ([ADR-230](ADR-230-double-entry-trust-ledger.md)).

Phase 71 introduces accounting export (push invoices and customers to Xero). The intersection of these two systems creates a regulatory boundary: trust-related financial data must never be exported to the firm's operating-account general ledger in Xero. If a trust invoice (an invoice for disbursements paid from trust, or an invoice where the source of payment is a client's trust balance) were pushed to Xero, it would appear in the firm's operating income — a regulatory violation that the Legal Practice Council audits for.

The question is how to enforce this boundary: as a configurable rule, as a soft warning, or as a hard-coded guard that cannot be bypassed.

The relevant entities from Phase 60 are: `TrustAccount` (bank accounts holding trust money), `TrustTransaction` (ledger entries), `ClientLedgerCard` (per-client trust balance view). The relevant entity from Phase 10 is `Invoice` (which may or may not be trust-related, indicated by fields added in Phase 60). The `AccountingSyncService` ([ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)) is the only path through which invoices reach Xero.

**Options Considered**:

1. **Configurable rule in the automation engine** — Add a Phase 37 rule condition that checks trust-related flags before allowing sync. Tenants can enable/disable the guard via the rule UI. Default-on for legal-za vertical, default-off for accounting-za and consulting-za.
   - Pros:
     - Flexible. Non-legal firms (accounting-za, consulting-za) that have no trust accounting can disable the guard, avoiding false positives if the trust-related fields are accidentally set.
     - Consistent with the automation framework's design: conditions are configurable, not hard-coded.
     - The tenant "owns" their compliance posture — the platform provides the tool, the firm decides whether to use it.
   - Cons:
     - A legal-za firm could disable the guard accidentally (or a junior staff member could do so without understanding the regulatory implications). The Legal Practice Act does not care whether the breach was accidental — it is a breach. The platform would be complicit in enabling the violation.
     - If sync is not routed through the rule engine ([ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)), the configurable-rule approach does not apply. The guard must live in the sync service, not in the rule engine.
     - Regulatory compliance should not be a tenant choice. The platform should enforce the law, not offer an opt-out. A firm that disables the trust guard and gets audited by the Legal Practice Council creates reputational risk for Kazi as a platform.
     - The trust-boundary check requires querying trust-accounting entities (`TrustAccount`, `ClientLedgerCard`) that the rule engine's condition evaluator has no built-in support for. Adding these as condition predicates would couple the rule engine to trust-accounting domain knowledge.

2. **Soft warning with override** — The sync service checks trust-related flags before pushing. If a trust-related invoice is detected, the push is paused and the user is shown a warning: "This invoice is trust-related and should not be exported. Override?" If they confirm, the push proceeds.
   - Pros:
     - The user is informed and makes a conscious decision. No silent refusal — the platform explains and the human decides.
     - Handles edge cases where the trust-related flag is set incorrectly (e.g., an invoice incorrectly tagged as trust-related due to a data entry error). The user can override and push.
     - Less paternalistic than a hard guard. The platform advises; the firm decides.
   - Cons:
     - The override creates a bypass path for a regulatory requirement. Section 86 compliance is not advisory — it is mandatory. An override that allows trust money to appear in the operating ledger is a compliance gap, regardless of whether the user clicked "confirm."
     - The override creates an audit liability. If the firm pushes a trust invoice to Xero via override, the platform has a record of facilitating the violation. This is worse than not having the integration at all.
     - In practice, users click through warnings without reading them. A confirmation dialog is not a meaningful control for a regulatory requirement — it is security theatre. The Legal Practice Council auditor will not accept "we showed them a warning" as a defence.
     - The override introduces a complex UX flow in the sync path (which is normally automatic and background). A trust-related invoice would pause in a "waiting for user confirmation" state, requiring a dedicated UI and notification to surface it. This is substantial frontend and backend work for a flow that should never complete successfully.

3. **Hard-coded guard with audit-only refusal — no bypass, no override (CHOSEN)** — The `TrustBoundaryGuard` is a pure Java service that evaluates an invoice + its lines + its customer against trust-accounting criteria. If any criterion matches (invoice is trust-flagged, any line item references a trust account, customer has active trust balances), the push is unconditionally refused. A sync entry is created with `state=BLOCKED_TRUST_BOUNDARY` and `last_error_code=TRUST_BOUNDARY`. An audit event `integration.xero.push_blocked_trust` is emitted with the full reason. The invoice detail page shows a passive notice. No UI action exists to bypass the guard.
   - Pros:
     - Regulatory compliance is unconditional. The platform enforces Section 86 regardless of tenant configuration, user role, or UI interaction. There is no path — API, UI, or automation — that pushes trust-related data to Xero.
     - Fails closed by design. If the guard encounters an error evaluating trust-related criteria (e.g., the trust-account lookup fails), the invoice is blocked — not allowed through. "When in doubt, refuse" is the correct posture for a regulatory boundary.
     - Full audit trail. Every refusal is recorded as both a `BLOCKED_TRUST_BOUNDARY` sync entry and an audit event. The Legal Practice Council auditor can see that the system refused to export trust data, when, and why.
     - Simple implementation. The guard is a pure function: `(Invoice, List<InvoiceLine>, Customer) -> TrustBoundaryDecision(allowed, reason)`. No external state, no configuration, no user interaction. Testable with unit tests.
     - No UX complexity. The invoice detail page shows a passive notice ("Not pushed to Xero — trust-related invoice") with a link to the audit event. No dialog, no confirmation, no override button. The user understands why the invoice was not synced without being asked to make a decision they should not be making.
   - Cons:
     - No escape hatch for false positives. If the trust-related flag is set incorrectly (data entry error), the user cannot force-push the invoice to Xero. They must first fix the data (un-flag the invoice as trust-related, or correct the line item's source account) and then the sync will proceed naturally on the next event.
     - Non-legal verticals (accounting-za, consulting-za) may never encounter the guard because they have no trust accounting. The guard still runs on every invoice push (checking `isTrustInvoice`, querying line item source accounts, checking customer trust balances). For tenants with no trust entities, these checks are fast (the queries return empty), but they are non-zero cost. Acceptable for the regulatory guarantee they provide.
     - The guard checks customer trust balances, which means a customer with an active trust balance on one matter has ALL their invoices blocked — even invoices for non-trust matters. This is conservative but potentially over-broad. The reasoning: if a client has active trust money with the firm, any financial data about that client flowing to the operating ledger creates a commingling risk that the Legal Practice Council flags.

**Decision**: Option 3 — Hard-coded `TrustBoundaryGuard` with audit-only refusal. No bypass, no override, no configuration. Fails closed.

**Rationale**:

The trust-accounting boundary is a regulatory requirement, not a business rule. Section 86 of the Legal Practice Act requires strict separation between trust funds and operating funds. Pushing a trust-related invoice to the firm's Xero general ledger would commingle trust data with operating data — even if the money itself is not transferred, the appearance of trust-related revenue in the operating ledger is a compliance red flag that auditors look for.

The platform's responsibility is to make compliance violations impossible, not merely difficult. A configurable rule (Option 1) makes violations possible through misconfiguration. A soft warning with override (Option 2) makes violations possible through user action. Only a hard guard (Option 3) makes violations structurally impossible within the system. The founder's decision is explicit: "trust accounting stays out — hard guard, fails closed, audit event on every refusal."

The "no escape hatch" concern (false positives) is addressed by fixing the data, not by bypassing the guard. If an invoice is incorrectly flagged as trust-related, the correct action is to un-flag it in Kazi (which corrects the data model) and then the sync proceeds. This is the right UX: the system refuses to sync incorrect data; the user corrects the data; the system syncs the corrected data. The guard is not a blocking wall — it is a data-quality signal.

The conservative approach of blocking ALL invoices for customers with active trust balances (not just trust-flagged invoices) is deliberate. In SA legal practice, a client who has trust money deposited with the firm is in a fiduciary relationship. Financial data about that client flowing to the operating general ledger — even for a non-trust matter — creates audit risk. The Legal Practice Council inspector who sees "Client X" in both the trust ledger and the operating ledger will ask questions. Blocking all exports for trust-active customers eliminates that audit surface. If this proves too conservative in practice, a future phase can narrow the scope to trust-flagged invoices only, but starting conservative is the correct posture for a regulatory boundary.

**Consequences**:

- Positive:
  - Section 86 compliance is structurally enforced. No configuration, no user action, and no API call can push trust-related data to Xero. The platform is compliant by construction.
  - Full audit trail on every refusal. The `integration.xero.push_blocked_trust` audit event captures the invoice ID, the reason (which criterion matched: trust-flagged invoice, trust-source line item, or trust-active customer), and the timestamp. Available for Legal Practice Council inspection.
  - The `BLOCKED_TRUST_BOUNDARY` state in `accounting_sync_entry` surfaces in the sync-log UI, making the refusal visible to the firm. They can see which invoices were blocked and why.
  - The guard is pure and testable: input is an invoice + lines + customer, output is a decision. No external dependencies, no state, no side effects. Unit tests can cover all criteria combinations exhaustively.

- Negative:
  - Over-broad blocking for trust-active customers. A consulting-related invoice for a client who also has a trust balance with the firm will be blocked. This may cause confusion for firms with mixed trust/non-trust work for the same client. The sync-log entry explains why, and the user can restructure their data (separate customers for trust vs non-trust matters) if the blocking is problematic.
  - Non-zero runtime cost on every invoice push. The guard queries `InvoiceLine.disbursement_id → LegalDisbursement.trust_account_id` chain and `ClientLedgerCard` balances for the invoice's customer. For tenants with no trust accounting entities, these queries return empty results quickly. For legal-za tenants with large trust ledgers, the balance check is an aggregate query — but it runs once per invoice push (not per API call), so the cost is amortized.
  - No self-service resolution for false positives. The user must fix the data (un-flag the invoice or correct the line item source) rather than clicking an "override" button. This is by design but adds friction in edge cases.

- Neutral:
  - The guard runs in the `enqueueInvoicePush` method of `AccountingSyncService`, before a sync entry is persisted as PENDING. A blocked invoice gets an entry with `state=BLOCKED_TRUST_BOUNDARY` (for visibility) but never enters the retry queue. It is a terminal state.
  - Verticals without trust accounting (accounting-za, consulting-za) will never trigger the guard because they have no trust entities. The checks still run (defense in depth) but return `allowed=true` immediately.
  - The guard does not check payment-pull operations. Payments flowing from Xero to Kazi (recording that an invoice was paid) are always allowed — the payment pull updates the invoice status, it does not export trust data.

- Related: [ADR-230](ADR-230-double-entry-trust-ledger.md) (double-entry trust ledger — the entities the guard queries), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (dedicated sync service — where the guard executes), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way sync — only outbound push is guarded), [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md) (Xero adapter — the destination being guarded against), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant — trust entities are tenant-scoped).
