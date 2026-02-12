# Billing Analysis: Removing Clerk Billing Dependency

**Date**: 2026-02-10
**Context**: Stripe is not available for South African entities. Clerk Billing hard-depends on Stripe. The plan/tier/tenancy model (Starter shared-schema, Pro schema-per-tenant, member limits) must remain unchanged. PSP choice is deferred — system must work with zero PSP integration.

---

## 1. Current State — Clerk Billing Dependency Audit

### Verdict: Surgical Removal, NOT Phase 2 Restart

The codebase has **zero Stripe code**. Clerk Billing abstracts it entirely. The backend plan/tier machinery is already PSP-agnostic — `PlanSyncService`, `Tier`, `PlanLimits`, `TenantUpgradeService` never knew about Clerk Billing or Stripe. The surgery is removing Clerk Billing from the frontend and adding a `subscriptions` table so we own plan state.

### Clerk Billing Touchpoints (exhaustive)

| # | File | What it does | Action |
|---|------|-------------|--------|
| 1 | `frontend/.../settings/billing/page.tsx` | `<PricingTable for="organization" />` | **Replace** — custom plan display page |
| 2 | `frontend/lib/webhook-handlers.ts` L217-257 | `syncPlan()` for `subscription.created/updated` | **Remove** — no Clerk subscription events |
| 3 | `frontend/lib/webhook-handlers.ts` L281-286 | `routeWebhookEvent()` subscription cases | **Remove** — dead code after #2 |
| 4 | `frontend/components/team/invite-member-form.tsx` L26 | `organization.maxAllowedMemberships` | **Replace** — backend limits API |
| 5 | `frontend/docs/clerk-billing-setup.md` | Clerk Dashboard config guide | **Delete** |

### Already PSP-Agnostic (Zero Changes)

| File | What it does |
|------|-------------|
| `PlanSyncService.syncPlan(orgId, planSlug)` | Maps planSlug → Tier, triggers upgrade |
| `PlanSyncController` | Receives `{clerkOrgId, planSlug}` — generic internal API |
| `PlanLimits` | `STARTER_MAX_MEMBERS=2, PRO_MAX_MEMBERS=10` |
| `Tier` enum | `STARTER, PRO` |
| `TenantUpgradeService.upgrade()` | Full Starter→Pro schema migration |
| `MemberSyncService.enforceMemberLimit()` | Reads tier from DB, checks count |
| `PlanLimitExceededException` | HTTP 403 + upgrade prompt |
| All RLS policies, Hibernate @Filter | Row isolation for shared schema |
| All 201+ integration tests | No billing coupling |

---

## 2. Approach: Self-Hosted Subscriptions, No PSP

### Design Principles

1. **Own the subscription state** — a `subscriptions` table in our DB is the single source of truth for what plan an org is on. No external system needed to resolve plan state.
2. **Plan changes via internal API** — admin/operator sets plans via `POST /internal/orgs/{orgId}/set-plan`. This works for manual invoicing, sales-led upgrades, or any future PSP.
3. **No premature PSP abstraction** — no `BillingProvider` interface, no checkout URLs, no webhook parsers. When a PSP is chosen, we'll know exactly what shape the integration needs. The PSP surface will be small: one webhook endpoint + one checkout redirect.
4. **Frontend shows plan state, not billing** — the billing page displays current plan, limits, and usage. Upgrade CTA is a "Contact us" or placeholder until a PSP is wired in.

### Why This Works for B2B

B2B SaaS billing is often sales-led anyway. Many B2B companies operate with:
- Manual invoicing + bank transfers
- Sales team upgrades orgs after contract signing
- Finance team reconciles offline

A self-service checkout is a nice-to-have, not a launch blocker. The internal API lets ops manage plans immediately.

---

## 3. What Changes

### 3A. New: `subscriptions` Table

```sql
-- Global migration: V5__add_subscriptions.sql
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    plan_slug       VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    current_period_start TIMESTAMPTZ,
    current_period_end   TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscriptions_org UNIQUE (organization_id)
);

-- Seed existing orgs with STARTER subscription
INSERT INTO subscriptions (organization_id, plan_slug, status, created_at, updated_at)
SELECT id, COALESCE(plan_slug, 'starter'), 'ACTIVE', now(), now()
FROM organizations
ON CONFLICT (organization_id) DO NOTHING;
```

**Intentionally excluded**: `psp_provider`, `psp_subscription_id`, `psp_customer_id`. These columns are added when a PSP is chosen — no point defining them now when we don't know the shape.

**Status values**: `ACTIVE`, `CANCELLED`. More statuses (PAST_DUE, TRIALING) added when PSP defines what those mean for us.

### 3B. New: Backend Subscription + Billing Endpoints

**Entity + Repository**: `Subscription` entity, `SubscriptionRepository` (standard JPA).

**SubscriptionService**: Thin orchestrator that:
- Creates a STARTER subscription on org provisioning (hook into existing `TenantProvisioningService`)
- Delegates plan changes to existing `PlanSyncService.syncPlan()`
- Provides read access to current subscription + limits

**New Endpoints**:

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `GET /api/billing/subscription` | GET | JWT | Current org's subscription (plan, status, limits, usage) |
| `POST /internal/billing/set-plan` | POST | API Key | Admin sets an org's plan — creates/updates subscription + calls `PlanSyncService.syncPlan()` |

The `GET /api/billing/subscription` response shape:

```json
{
  "planSlug": "starter",
  "tier": "STARTER",
  "status": "ACTIVE",
  "limits": {
    "maxMembers": 2,
    "currentMembers": 1
  }
}
```

### 3C. Modified: Frontend Billing Page

Replace `<PricingTable>` with a server component that:
1. Calls `GET /api/billing/subscription` to get current plan + usage
2. Displays plan name, member limit, current member count
3. Shows a "Contact us to upgrade" CTA (or whatever makes sense pre-PSP)
4. When a PSP is eventually wired in, the CTA becomes a checkout redirect — one line change

### 3D. Modified: Frontend Member Limit Check

Replace `organization.maxAllowedMemberships` (populated by Clerk Billing) with data from the team page's server-side data fetch. Two options:

**Option A** (simpler): The team page already fetches members. Add a server action or API call for limits:
```tsx
const limits = await apiClient<BillingLimits>("/api/billing/subscription");
// Pass limits.maxMembers to InviteMemberForm as a prop
```

**Option B** (no extra call): Derive limit from member count + a known constant. But this duplicates `PlanLimits` logic in the frontend — Option A is cleaner.

### 3E. Removed: Clerk Subscription Webhook Handlers

Remove from `webhook-handlers.ts`:
- `SubscriptionEventData` interface
- `syncPlan()` function
- `handleSubscriptionCreated()` / `handleSubscriptionUpdated()`
- `subscription.created` / `subscription.updated` cases in `routeWebhookEvent()`

All other Clerk webhook handlers (org created/updated/deleted, membership CRUD) are unrelated to billing and stay.

### 3F. Deleted: `clerk-billing-setup.md`

No longer relevant.

---

## 4. Future PSP Integration Surface (When Ready)

When a PSP is eventually chosen, the integration points are minimal:

| What | Where | Size |
|------|-------|------|
| Webhook endpoint | New `POST /api/webhooks/{psp}` controller | ~50 lines |
| Webhook verification | PSP SDK call in the controller | ~10 lines |
| Checkout redirect | New `POST /api/billing/checkout` that returns PSP URL | ~30 lines |
| PSP columns on subscriptions | `ALTER TABLE` migration adding `psp_*` columns | ~5 lines |
| Frontend checkout button | Replace "Contact us" CTA with redirect | ~5 lines |

The webhook handler calls `SubscriptionService.changePlan()` which calls `PlanSyncService.syncPlan()` — same pipeline that already works. Total PSP-specific code: ~100 lines + a migration.

---

## 5. Implementation Plan — Single Epic

### Epic 28: Self-Hosted Subscriptions & Clerk Billing Removal

**Scope**: Add subscriptions table, admin plan API, billing display page. Remove all Clerk Billing integration. No PSP.

#### Backend

- [ ] `V5__add_subscriptions.sql` — table + seed from existing orgs
- [ ] `Subscription` entity + `SubscriptionRepository`
- [ ] `SubscriptionService` — create on provisioning, change plan, read subscription + limits
- [ ] `BillingController` — `GET /api/billing/subscription`
- [ ] `AdminBillingController` — `POST /internal/billing/set-plan`
- [ ] Hook subscription creation into `TenantProvisioningService`
- [ ] Integration tests: subscription lifecycle, set-plan, limits response

#### Frontend

- [ ] Replace `<PricingTable>` billing page with custom plan display
- [ ] Replace `maxAllowedMemberships` in `InviteMemberForm` with backend limits
- [ ] Remove `subscription.*` handlers from `webhook-handlers.ts`
- [ ] Delete `clerk-billing-setup.md`
- [ ] Tests for billing page + invite form limit gating

#### Cleanup

- [ ] Remove `PlanSyncRequest` type from `internal-api.ts` (if only used by subscription handlers)
- [ ] Update architecture/ARCHITECTURE.md — billing section, remove Clerk Billing references
- [ ] Update ADR-010 or add ADR-017 documenting the billing decoupling decision

---

## 6. Summary

| Question | Answer |
|----------|--------|
| **Restart Phase 2?** | **No.** Epics 23-27 intact. All plan/tier/tenancy code untouched. |
| **PSP choice?** | **Deferred.** System works with admin-managed plans, no PSP required. |
| **PSP surface when ready** | ~100 lines: 1 webhook endpoint, 1 checkout redirect, 1 migration |
| **Premature abstractions?** | **None.** No `BillingProvider` interface. No checkout URLs. No webhook parsers. |
| **What we own** | Subscription state in our DB. Plan resolution, limits, enforcement — all self-hosted. |
| **Implementation** | 1 epic (backend: migration + 3 classes + tests, frontend: 4 file changes + tests) |
| **Existing tests** | 201+ backend tests unaffected |
