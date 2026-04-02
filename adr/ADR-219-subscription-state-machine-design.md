# ADR-219: Subscription State Machine Design

**Status**: Proposed  
**Date**: 2026-04-02  
**Phase**: 57

## Context

HeyKazi's billing system was originally built in Phase 2 with a dual-tier model (STARTER/PRO) and a minimal `SubscriptionStatus` enum (`ACTIVE`, `CANCELLED`). The STARTER tier was designed for a shared-schema model that was eliminated in Phase 13 (all tenants now receive dedicated schemas via ADR-064). The "Upgrade to Pro" button is a no-op that flips the `planSlug` field without any payment validation.

With production infrastructure being finalized in Phase 56, the platform needs a real billing model before launch. The dual-tier system is overcomplicated for a single-price product: there is no free tier to fall back to, no per-seat pricing, and no plan catalog. Every organization should either be paying or on trial — and if they stop paying, they should lose write access after a grace period.

The current `SubscriptionStatus` enum (2 states) cannot express trial periods, payment failures, grace periods, or the difference between "voluntarily cancelled" and "payment lapsed." A richer lifecycle model is needed.

## Options Considered

### Option 1: Keep Tier Model + Add Payment States

Extend the existing `Tier` enum (STARTER, PRO) and add payment-related states to `SubscriptionStatus`. STARTER remains as a free tier, PRO requires payment.

- **Pros:** Minimal structural change. Preserves the option for a free tier in the future. `PlanLimits` per-tier logic stays as-is.
- **Cons:** The free tier does not exist as a product concept — every org gets a dedicated schema regardless of tier (Phase 13), so STARTER vs PRO is cosmetic. Maintaining two tiers doubles the test surface for a distinction that has no business meaning. `PlanLimits` branching on tier adds complexity for a single set of limits. The `Tier` enum is a vestige of Clerk Billing integration that no longer exists.

### Option 2: Single-Plan Lifecycle States (Selected)

Replace `Tier` and the 2-state `SubscriptionStatus` with a single lifecycle enum covering the full subscription journey: `TRIALING`, `ACTIVE`, `PENDING_CANCELLATION`, `PAST_DUE`, `SUSPENDED`, `GRACE_PERIOD`, `EXPIRED`, `LOCKED`.

- **Pros:** Matches the actual business model (one plan, one price). Eliminates dead code (`Tier`, `PlanLimits` branching, `PlanSyncService`). Each state maps to a clear UX treatment and access policy. Grace period and trial are first-class states, not bolted-on fields. State transitions are explicit and auditable.
- **Cons:** More states to test (8 vs 2). Requires migrating all `Tier` references across the codebase. Frontend must handle more UI variations. Cannot trivially add a free tier later (would need to reintroduce a concept).

### Option 3: External Subscription Management (Delegate to PayFast)

Treat PayFast as the source of truth for subscription state. Backend queries PayFast API for current status on each request (or caches it). No local state machine — just map PayFast's status to access decisions.

- **Pros:** No local state management. PayFast handles retries, dunning, cancellation timing. Less code to maintain.
- **Cons:** PayFast's API does not expose a rich subscription status — ITN events are the primary mechanism, and they are push-only (no reliable pull API for current state). Requires network call for every access decision (or cache with staleness risk). Cannot implement custom grace periods or trial logic. Tight coupling to PayFast — switching PSPs would require rebuilding the entire access model. No offline resilience (if PayFast is down, no access decisions possible).

## Decision

**Option 2 — Single-plan lifecycle states.**

## Rationale

1. **Business alignment.** HeyKazi is launching with a single plan at a single price point. The tier model was inherited from an era when shared vs dedicated schemas mattered (pre-Phase 13). Since all tenants now get dedicated schemas, the STARTER/PRO distinction has no infrastructure meaning.

2. **Explicit lifecycle beats implicit inference.** A `GRACE_PERIOD` state is clearer than "cancelled + grace_ends_at > now()." Each state has a defined access policy (full, read-only, locked), a defined UX (banner type, CTA), and a defined set of allowed transitions. This makes the system predictable for operators and testable for developers.

3. **GRACE_PERIOD as a separate state from CANCELLED.** Cancellation in PayFast is a deliberate user action — the user chose to stop paying. Grace period can result from either cancellation (period end) or trial expiry — the org didn't choose to lose access, it happened by time passage. Separating these states allows different UX treatments (empathetic messaging for trial expiry vs neutral messaging for cancellation) and different operational handling (support can extend grace but not un-cancel a PayFast subscription).

4. **Dead code removal.** `Tier.java`, `PlanLimits.java`, and `PlanSyncService.java` are vestiges of the Clerk Billing integration removed in Phase 20. They serve no purpose and add confusion. A lifecycle model provides a clean replacement.

## Consequences

- **Positive:** Simpler mental model (one plan, eight states). Eliminates ~150 lines of dead tier/plan code. Each state maps to exactly one access level and one UX treatment. Audit trail captures every state transition. Frontend can use a single `status` field for all conditional rendering.
- **Negative:** 8 states require 8 UX treatments on the billing page and 8 test cases for the guard filter. Migration must handle existing `plan_slug` data. Adding a free tier in the future requires reintroducing a plan concept (acceptable trade-off — YAGNI until needed).
- **Migration:** Existing subscriptions with `plan_slug = 'pro'` become `ACTIVE`; those with `plan_slug = 'starter'` become `TRIALING` with a 14-day trial window. The `plan_slug` and `status` columns are dropped after data migration.
