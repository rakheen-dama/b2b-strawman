# Phase 58 Ideation — Demo Readiness & Admin Billing Controls
**Date**: 2026-04-02

## Decision
Demo readiness phase: billing method dimension, admin billing controls, one-click demo provisioning per vertical, realistic seed data, safe demo cleanup.

## Rationale
Emerged from PayFast onboarding friction — founder blocked on PSP signup, needs to demo/pilot without automated payments. Phase 57 built the subscription lifecycle but assumed PayFast would be ready. The admin activation endpoints exist but lack UI and flexibility. No way to quickly spin up a realistic demo for prospect meetings.

### Key Decisions
1. **Billing method as separate dimension (Option B)** — `billing_method` column independent of `subscription_status`. Guard filter unchanged. Scheduled jobs respect method. Values: PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL.
2. **Admin billing UI in platform panel** — not just API endpoints. Table + detail slide-over for managing all tenants.
3. **One-click demo provisioning** — bypasses access request → OTP → approval pipeline. Picks vertical profile, creates Keycloak org + schema + packs + admin user + ACTIVE/PILOT subscription.
4. **Per-vertical demo data** — Generic (agency), Accounting (SARS/tax), Legal (matters/court — if Phase 55 entities exist). ~3 months of realistic SA business data.
5. **Safe cleanup with billing method guard** — can only delete PILOT/COMPLIMENTARY tenants. Changing to DEBIT_ORDER/PAYFAST protects from accidental deletion.
6. **Tenant impersonation deferred** — too complex for this phase (Keycloak token exchange + BFF session management).

## Founder Preferences
- Demo seed data per vertical profile (confirmed)
- Platform admin should manage billing without API calls (confirmed)
- Demo cleanup via button, not manual DB work (confirmed)
- Tenant impersonation acknowledged as nifty but deferred for complexity (confirmed)

## Phase Roadmap Context
- Phase 56: Production Infrastructure (done)
- Phase 57: Tenant Subscription Payments (in progress — backend done, frontend remaining)
- **Phase 58: Demo Readiness & Admin Billing Controls** (spec written)
- Phase 55: Legal Foundations (specced, not started)
