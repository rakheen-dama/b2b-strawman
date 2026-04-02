# Phase 57 Ideation — Tenant Subscription Payments
**Date**: 2026-04-02

## Decision
Single-plan subscription billing via PayFast recurring payments. Replace the STARTER/PRO dual-tier model with a lifecycle-based subscription (trial → active → cancelled → grace → locked).

## Rationale
56 phases built, production infrastructure being stood up — can't go live without real payment collection. The existing `Subscription` entity and `BillingController` are shells with no real PSP integration. The dual-tier model (Starter/Pro from Phase 2) is dead weight since Phase 13 removed shared schema. Simplifying to a single plan removes complexity while covering the launch scenario (5-20 tenants, flat-rate pricing).

### Key Decisions
1. **PayFast only** — no Stripe (SA entity limitation). PayFast-managed recurring billing (not ad-hoc tokenization) to minimize code.
2. **Single plan, flat-rate** — no per-seat, no tiers. One price. Simplest possible model.
3. **Platform-owned credentials** — NOT the BYOAK `IntegrationRegistry` pattern from Phase 25. Direct config (env vars).
4. **14-day trial default** (configurable). Full access during trial.
5. **No downgrade** — cancel = access until period end → 2-month read-only grace with banner → hard lock.
6. **Read-only enforcement via servlet filter** — HTTP method check (block POST/PUT/PATCH/DELETE). Simplest approach for v1.
7. **`Tier` enum and `PlanLimits` deleted** — replaced by subscription status + single `SubscriptionLimits`.

## Founder Preferences
- PayFast as sole PSP for platform billing (confirmed)
- Flat-rate monthly pricing (confirmed)
- No Starter/free tier — trial is the "free" period (confirmed)
- 2-month read-only grace period before hard lock (confirmed)
- No downgrade path — just cancel (confirmed)

## Phase Roadmap (Updated)
- Phase 55: Legal Foundations (specced)
- Phase 56: Production Infrastructure (specced)
- **Phase 57: Tenant Subscription Payments** (spec written)
