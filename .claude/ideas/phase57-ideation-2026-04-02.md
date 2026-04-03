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

## PSP Research (2026-04-02 follow-up)
- **Stripe in SA**: Available via Paystack (acquired 2020). "Extended network" — Paystack API underneath, not Stripe API directly. ZAR supported, subscription APIs available, but needs verification that SA-specific feature set matches marketing claims.
- **Paystack**: Better developer experience than PayFast. REST API, proper webhooks (subscription.create, charge.success, invoice.create, expiring_cards). Dunning is limited — marks "attention" on failure, retries next cycle only. Pricing: 2.9% + R1 cards, 2% EFT.
- **PayFast**: Recurring billing announced Jan 2026 — new/immature. Form-encoded redirects, ITN webhooks. Onboarding process unclear/difficult. Founder feeling blocked by PayFast onboarding.
- **Decision**: Keep PayFast integration as built (Epic 421 done). Use admin endpoints (`/internal/billing/activate`, `/internal/billing/extend-trial`) for demos/pilots while PayFast onboarding clears. No PSP switch needed — adapter pattern means swapping is cheap later.
- **Key insight**: B2B SaaS at 5-20 tenants doesn't need automated card billing. Admin-activate + EFT invoice is standard for SA B2B launches. Automated billing is a scale problem (~50+ tenants), not a launch problem.
- **Billing method dimension (Option B) — approved, deferred to separate phase**: Separate `billing_method` column (PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL) from `subscription_status`. Guard filter only checks status. Needs its own architecture doc and planning before implementation.

## Phase Roadmap (Updated)
- Phase 55: Legal Foundations (specced)
- Phase 56: Production Infrastructure (specced)
- **Phase 57: Tenant Subscription Payments** (in progress — backend done, frontend remaining)
