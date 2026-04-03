# ADR-223: Billing Method as Separate Dimension

**Status**: Accepted

**Context**:

The platform's subscription model (Phase 57) uses `SubscriptionStatus` (TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE, SUSPENDED, GRACE_PERIOD, EXPIRED, LOCKED) to control tenant access. The `SubscriptionGuardFilter` reads this status to determine whether a tenant has read-write, read-only, or no access. This model works well for the PayFast self-service flow where status transitions are driven by payment events.

However, the platform needs to support multiple commercial arrangements beyond automated card billing: debit orders (manual EFT reconciliation), pilot partnerships (no payment expected), complimentary access (internal/strategic), and invoice-based billing. Each arrangement has different lifecycle behaviour — pilot tenants should not auto-expire, complimentary tenants should never see a "Subscribe" CTA, debit order tenants need admin-managed periods. The question is where to encode this information: within the existing `SubscriptionStatus` enum, as a separate field on the `Subscription` entity, or as a separate entity entirely.

**Options Considered**:

1. **Add new status values (PILOT, DEBIT_ORDER, COMPLIMENTARY) to SubscriptionStatus** — Encode the billing arrangement as additional subscription statuses.
   - Pros:
     - Single field captures everything — no schema change beyond new enum values
     - Simple to query: "all pilot tenants" = `WHERE status = 'PILOT'`
   - Cons:
     - Conflates access control with commercial arrangement. A PILOT tenant that needs to be temporarily locked would have no PILOT+LOCKED compound state.
     - Every new billing arrangement requires `SubscriptionGuardFilter` changes — a security-critical component should not change for commercial reasons.
     - The state machine (`VALID_TRANSITIONS` map) becomes unwieldy. PILOT would need transitions to ACTIVE, LOCKED, etc., duplicating the existing status lifecycle for each billing type.
     - Scheduled jobs would need to handle a growing matrix of status values.

2. **Separate `billing_method` field on Subscription entity (chosen)** — Add a `VARCHAR(30)` column that captures the commercial arrangement independently of the access-control status.
   - Pros:
     - Clean separation of concerns: status = "what access does this tenant have?", billing method = "how does this tenant pay?"
     - `SubscriptionGuardFilter` does not change — access control remains purely status-based.
     - State machine stays simple: the same 8 statuses, the same transition map. Billing method is orthogonal.
     - Scheduled jobs filter by billing method where relevant (trial expiry), but the core status logic is unchanged.
     - A PILOT tenant in ACTIVE status and a PAYFAST tenant in ACTIVE status receive identical access treatment.
     - Adding a new billing method (e.g., Stripe, bank integration) is a one-line enum addition with no security impact.
   - Cons:
     - Two fields to manage instead of one — admin overrides must consider both dimensions.
     - Queries that need both dimensions require a compound filter (`WHERE status = 'ACTIVE' AND billing_method = 'PILOT'`).

3. **Separate BillingArrangement entity** — Create a new table `billing_arrangements` with its own lifecycle, linked to `subscriptions` via FK.
   - Pros:
     - Full flexibility: per-arrangement configuration fields, history tracking, multiple arrangements per tenant.
     - Could support future scenarios like "tenant has both a PayFast subscription and a debit order for overflow billing."
   - Cons:
     - Over-engineered for the current need. There is exactly one billing arrangement per tenant, and it has no lifecycle of its own (no status transitions, no history, no configuration beyond the method enum).
     - Extra join for every billing query.
     - No current requirement for multiple arrangements per tenant or arrangement-specific configuration.
     - Violates the project's YAGNI principle: "do not create provider/adapter patterns until there are two concrete implementations."

**Decision**: Option 2 — Add a separate `billing_method` field on the `Subscription` entity.

**Rationale**:

The core insight is that access control (what a tenant can do) and commercial arrangement (how a tenant pays) are orthogonal concerns. A pilot tenant in ACTIVE status should have the same read-write access as a PayFast tenant in ACTIVE status. A complimentary tenant that needs to be temporarily locked should transition to LOCKED status exactly like any other tenant — the billing method does not change.

Option 1 fails this test. If PILOT is a status, then a pilot tenant that needs to be locked requires either a compound PILOT_LOCKED state (exponential blowup) or losing the PILOT information when transitioning to LOCKED. Neither is acceptable.

Option 3 is premature abstraction. The billing method is a single enum value with no per-method configuration, no lifecycle, and no requirement for history or multiplicity. A dedicated table adds schema complexity and query overhead for no current benefit.

Option 2 is the minimum correct model: a single column that captures a meaningful dimension without complicating the access-control model. The `SubscriptionGuardFilter` — the most security-critical component in the billing pipeline — does not change. Scheduled jobs gain a simple `WHERE billing_method IN (...)` clause. The admin panel gets a clear two-axis view: status (what access) x billing method (how they pay).

**Consequences**:

- The `SubscriptionGuardFilter` remains unchanged. Access control is purely status-based. This is the most important consequence — adding billing methods never requires security review.
- Scheduled jobs must be updated to filter by billing method where relevant. The trial expiry job only expires PAYFAST and MANUAL subscriptions. The grace period expiry job applies to all billing methods.
- The `SubscriptionStatusCache` is enhanced to include `billingMethod` in the cached record, avoiding a DB query for billing API responses.
- The `BillingResponse` DTO gains `billingMethod` and `adminManaged` fields. The frontend billing page adapts its UI based on `adminManaged`.
- Future billing methods (Stripe, bank integration, etc.) require only an enum addition and potentially a webhook handler — no changes to access control, guard filter, or state machine.
- The default billing method is MANUAL, which is backward-compatible with all existing subscriptions. A data migration sets PAYFAST for subscriptions with existing PayFast tokens.
- Related: [ADR-219](ADR-219-subscription-state-machine-design.md) (subscription status state machine), [ADR-221](ADR-221-read-only-enforcement-strategy.md) (read-only enforcement via SubscriptionGuardFilter).
