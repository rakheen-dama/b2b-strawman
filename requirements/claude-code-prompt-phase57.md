# Phase 57 — Tenant Subscription Payments (PayFast Recurring Billing)

## System Context

HeyKazi is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 56 phases of functionality. The platform currently has a billing subsystem built in Phase 2 with the following state:

- **`Subscription` entity** (`billing/Subscription.java`): JPA entity in `public` schema with `planSlug`, `status` (ACTIVE, CANCELLED), `currentPeriodStart`, `currentPeriodEnd`, `cancelledAt`. One subscription per organization.
- **`SubscriptionService`**: Creates starter subscriptions on provisioning, handles plan changes, returns billing info. Currently all plan changes are instant no-ops — no payment validation.
- **`BillingController`**: `GET /api/billing/subscription` (returns billing status) and `POST /api/billing/upgrade` (simulated upgrade — just flips the planSlug, no checkout).
- **`Tier` enum** (STARTER, PRO) with `PlanLimits` class: Enforces member limits (STARTER=2, PRO=10). Originally designed for a dual-tier model with shared schema for Starter (removed in Phase 13 — all tenants now get dedicated schemas).
- **`PlanSyncService`**: Resolves tier from planSlug and updates Organization entity. Vestige of Clerk Billing webhook sync.
- **Existing PayFast adapter** (`integration/payment/PayFastPaymentGateway.java`): Implements `PaymentGateway` interface for **tenant-owned BYOAK accounts** (invoice payments). Supports one-time checkout sessions, ITN webhook handling, signature verification, IP allowlist. This is tenant-scoped (resolved per-org via `IntegrationRegistry`) — **not usable for platform billing**.
- **Integration port pattern** (Phase 21): `IntegrationRegistry`, `SecretStore`, `IntegrationDomain.PAYMENT`. Designed for tenant-scoped BYOAK integrations.
- **Admin-approved provisioning** (Phase 39): New orgs are provisioned by a platform admin — there is no self-service signup.

**The problem**: HeyKazi has no way to collect money. The subscription entity exists, tier enforcement works, but the "Upgrade to Pro" button is a no-op. Production infrastructure (Phase 56) is being built — the platform cannot go live without real payment collection. The current dual-tier model (STARTER/PRO) is also overcomplicated for launch — there should be a single plan with a trial period.

**The fix**: Replace the simulated tier system with a single-plan subscription model powered by PayFast recurring billing. Every new org gets a trial with full access, then must subscribe via PayFast or lose write access after a grace period.

## Objective

Implement real subscription billing for the HeyKazi platform using PayFast's subscription (recurring) payment system. This phase:

1. **Replaces the dual-tier model with a single subscription lifecycle**: TRIALING → ACTIVE → PAST_DUE → CANCELLED → GRACE → LOCKED. No Starter tier, no Pro tier — just "subscribed" or "not subscribed."
2. **Integrates PayFast recurring billing**: Platform-owned PayFast merchant account (not BYOAK). Hosted checkout page for subscription creation. ITN webhooks for recurring payment confirmations and failures.
3. **Implements trial management**: Configurable trial duration (default 14 days). Full feature access during trial. Scheduled expiry detection.
4. **Implements cancellation and grace period**: Cancellation takes effect at period end. 2-month read-only grace period with persistent banner. Hard lock after grace period expires.
5. **Enforces read-only mode**: Write operations blocked during grace period via filter/interceptor. Read access preserved. Clear messaging about subscription status.
6. **Provides billing UI**: Trial countdown and subscribe CTA, subscription status page, cancellation confirmation flow.
7. **Removes dead tier code**: `Tier` enum, `PlanLimits` per-tier branching, simulated upgrade endpoint — all replaced by the new lifecycle.

***

## Constraints and assumptions

1. **Architecture constraints**
   - Platform-owned PayFast merchant credentials stored in application config (environment variables or AWS Secrets Manager) — NOT in the tenant-scoped `SecretStore`. This is fundamentally different from the BYOAK pattern.
   - The existing `PaymentGateway` interface is for tenant invoice payments and should NOT be reused for platform subscription billing. Different domain, different credential source, different webhook handling.
   - New code lives in the `billing/` package (extend existing), not in `integration/payment/`.
   - Subscription state lives in `public` schema (global, not tenant-scoped).
   - PayFast subscription API uses form-encoded POST to PayFast's hosted page, not a REST API. The "subscription" is created by including `subscription_type` and `recurring_amount` fields in the initial checkout redirect.

2. **PayFast subscription mechanics**
   - PayFast subscriptions work via ITN (Instant Transaction Notification) — a server-to-server POST to a configured `notify_url`.
   - Initial payment: redirect user to PayFast hosted page → user pays → PayFast sends ITN with payment confirmation + subscription token.
   - Recurring payments: PayFast charges the card automatically on the billing cycle. Sends ITN for each charge (success or failure).
   - Cancellation: Cancel via PayFast API (`PUT /subscriptions/{token}/cancel`) using the subscription token returned from initial payment.
   - PayFast sandbox available at `sandbox.payfast.co.za` for testing.
   - ITN security: signature verification (MD5 hash of sorted params + passphrase) and source IP validation (PayFast IP range).
   - The platform's `notify_url` must be publicly accessible — route through the Gateway BFF or directly to backend via ALB.

3. **Business rules**
   - **Single plan**: One price, flat-rate, monthly billing. No tiers, no per-seat pricing.
   - **Trial**: Every new org starts with a configurable trial period (default 14 days). Full feature access during trial. Trial begins at org provisioning time.
   - **No downgrade**: There is no free tier to fall back to. Cancellation = loss of write access after period end + grace.
   - **Grace period**: 2 months of read-only access after subscription expires or is cancelled. Banner shown on every page. After grace period, hard lock (cannot access the app at all — redirect to a "resubscribe" page).
   - **Pricing stored in config, not database**: The plan price is an application property (e.g., `heykazi.billing.monthly-price-cents=49900`). No plan catalog entity needed for a single plan.

4. **What NOT to build**
   - No payment method management UI (PayFast handles this via their portal)
   - No platform-generated subscription invoices/receipts (PayFast provides payment confirmations)
   - No dunning/retry emails for failed payments (future phase, uses Phase 24 email infrastructure)
   - No proration or mid-cycle changes (single plan, no upgrades/downgrades)
   - No multiple currency support (ZAR only for PayFast)
   - No annual billing option (monthly only for v1)
   - No self-service signup integration (Phase 39 admin-approved provisioning remains — billing hooks into existing flow)

***

## Section 1 — Subscription Lifecycle Model

### 1.1 State Machine

Replace `Tier` enum and `SubscriptionStatus` (ACTIVE, CANCELLED) with a richer lifecycle:

```
TRIALING ──[subscribes]──→ ACTIVE ──[cancels]──→ PENDING_CANCELLATION
    │                        │                         │
    │                        │                    [period ends]
    │                   [payment fails]                │
    │                        │                    GRACE_PERIOD
    │                        ↓                         │
    │                    PAST_DUE ──[recovers]──→ ACTIVE
    │                        │                         │
    │                   [exhausts retries]        [2 months]
    │                        │                         │
    │                        ↓                         ↓
    │                    SUSPENDED                  LOCKED
    │                        │
[trial expires              │
 without payment]      [pays manually
    │                  or support action]
    ↓                        │
EXPIRED ────────────────────→ (resubscribe flow)
```

States:
- **TRIALING**: Full access. Trial countdown visible. Subscribe CTA prominent.
- **ACTIVE**: Full access. Payment current. Next billing date shown.
- **PENDING_CANCELLATION**: Full access until period end. "Your subscription ends on {date}" banner.
- **PAST_DUE**: Full access (PayFast retries automatically). "Payment failed — please update your payment method" banner.
- **SUSPENDED**: Read-only access. PayFast exhausted retries. "Your subscription is suspended" banner. Same treatment as grace period.
- **GRACE_PERIOD**: Read-only access. 2-month window. "Subscribe to regain full access" banner with countdown.
- **EXPIRED**: Same as GRACE for trial-specific expiry. Transitions to LOCKED after grace period.
- **LOCKED**: No access. Redirect to resubscribe page. Data preserved but inaccessible.

### 1.2 Data Model Changes

Restructure the existing `Subscription` entity:

```sql
-- Modify existing subscriptions table (public schema)
ALTER TABLE subscriptions
  ADD COLUMN subscription_status VARCHAR(30) NOT NULL DEFAULT 'TRIALING',
  ADD COLUMN payfast_token VARCHAR(255),          -- PayFast subscription token for cancellation API
  ADD COLUMN trial_ends_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN grace_ends_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN monthly_amount_cents INTEGER,        -- snapshot of price at subscription time
  ADD COLUMN currency VARCHAR(3) DEFAULT 'ZAR',
  ADD COLUMN last_payment_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN next_billing_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN payfast_payment_id VARCHAR(255);      -- latest ITN m_payment_id

-- Drop columns that are no longer needed
ALTER TABLE subscriptions DROP COLUMN plan_slug;
-- Keep status column temporarily for migration, then drop

-- Payment history for audit trail
CREATE TABLE subscription_payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id UUID NOT NULL REFERENCES subscriptions(id),
  payfast_payment_id VARCHAR(255) NOT NULL,
  amount_cents INTEGER NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'ZAR',
  status VARCHAR(30) NOT NULL,                    -- COMPLETE, FAILED, REFUNDED
  payment_date TIMESTAMP WITH TIME ZONE NOT NULL,
  raw_itn JSONB,                                  -- full ITN payload for debugging
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_sub_payments_subscription ON subscription_payments(subscription_id);
```

Remove from `Organization` entity:
- `tier` column (no tiers — subscription status determines access)
- `planSlug` column (no plan catalog)

### 1.3 Removed Concepts

Delete or simplify:
- `Tier` enum → delete entirely
- `PlanLimits` class → replace with single `SubscriptionLimits` (one set of limits, not per-tier)
- `PlanSyncService` → delete (vestige of Clerk Billing)
- `BillingController.upgrade()` → replace with `subscribe()` (initiates PayFast checkout)
- All references to "Starter" and "Pro" in frontend copy, settings pages, feature gating

### 1.4 Member Limits

With a single plan, member limits are straightforward:
- Application property: `heykazi.billing.max-members=10` (configurable, not hardcoded)
- Enforced by the existing `MemberFilter` / capability check infrastructure
- During trial: same limits as paid (full access means full access)
- During grace/locked: no new members (write operations blocked)

***

## Section 2 — PayFast Subscription Integration

### 2.1 Platform PayFast Configuration

Platform-owned credentials in application config:

```yaml
heykazi:
  billing:
    payfast:
      merchant-id: ${PAYFAST_MERCHANT_ID}
      merchant-key: ${PAYFAST_MERCHANT_KEY}
      passphrase: ${PAYFAST_PASSPHRASE}
      sandbox: ${PAYFAST_SANDBOX:true}         # true for dev/staging, false for prod
    monthly-price-cents: 49900                  # R499.00/month — adjust as needed
    trial-days: 14
    grace-period-days: 60                       # 2 months
    currency: ZAR
    item-name: "HeyKazi Professional"
    notify-url: ${HEYKAZI_BASE_URL}/api/webhooks/subscription  # ITN callback
    return-url: ${HEYKAZI_FRONTEND_URL}/settings/billing?result=success
    cancel-url: ${HEYKAZI_FRONTEND_URL}/settings/billing?result=cancelled
```

### 2.2 Checkout Flow

1. Owner clicks "Subscribe" on billing page → `POST /api/billing/subscribe`
2. Backend generates PayFast form data:
   - `merchant_id`, `merchant_key`
   - `amount` (monthly price)
   - `item_name` ("HeyKazi Professional")
   - `subscription_type: 1` (PayFast subscription flag)
   - `recurring_amount` (same as amount for flat-rate)
   - `frequency: 3` (monthly)
   - `cycles: 0` (indefinite)
   - `custom_str1: {organizationId}` (for ITN → org mapping)
   - `notify_url`, `return_url`, `cancel_url`
   - `signature` (MD5 hash of sorted params + passphrase)
3. Backend returns the form data + PayFast URL to frontend
4. Frontend redirects user to PayFast hosted page (or opens in new tab)
5. User completes payment on PayFast
6. PayFast sends ITN to `notify_url` → backend processes, updates subscription to ACTIVE
7. User is redirected back to `return_url` → billing page shows updated status

### 2.3 ITN Webhook Handling

New endpoint: `POST /api/webhooks/subscription` (unauthenticated — PayFast server-to-server)

ITN processing:
1. **Validate source IP** — PayFast publishes their IP ranges. Reject requests from unknown IPs.
2. **Validate signature** — Reconstruct the signature from the POST params (excluding `signature`) + passphrase. Compare with received `signature`.
3. **Parse ITN fields**:
   - `m_payment_id` — PayFast's unique payment ID
   - `pf_payment_id` — PayFast reference
   - `payment_status` — `COMPLETE`, `FAILED`, `PENDING`
   - `custom_str1` — organization ID (set during checkout)
   - `token` — subscription token (for future cancellation API calls)
   - `amount_gross` — payment amount
4. **Idempotency** — Check if `m_payment_id` already processed (deduplicate against `subscription_payments` table).
5. **Route by payment_status**:
   - `COMPLETE`: Mark subscription ACTIVE, record payment, update `last_payment_at` and `next_billing_at`, store `token` if first payment.
   - `FAILED`: If subscription is ACTIVE, transition to PAST_DUE. Record failed payment.
   - `CANCELLED`: Transition to GRACE_PERIOD, set `grace_ends_at`.
6. **Return HTTP 200** — PayFast requires 200 OK response regardless of processing outcome.

### 2.4 Cancellation via PayFast API

When a tenant owner clicks "Cancel Subscription":
1. `POST /api/billing/cancel` → backend calls PayFast API: `PUT https://api.payfast.co.za/subscriptions/{token}/cancel` with merchant credentials.
2. Subscription transitions to PENDING_CANCELLATION.
3. Access remains until `currentPeriodEnd`.
4. After period end, transition to GRACE_PERIOD, set `grace_ends_at = currentPeriodEnd + 60 days`.
5. After grace ends, transition to LOCKED.

### 2.5 Resubscribe Flow

A tenant in GRACE_PERIOD, EXPIRED, SUSPENDED, or LOCKED state can resubscribe:
1. Same checkout flow as initial subscription (Section 2.2).
2. On successful payment, transition to ACTIVE, clear `grace_ends_at`.
3. For LOCKED tenants, the resubscribe page is the only accessible page.

***

## Section 3 — Read-Only Enforcement

### 3.1 Strategy: Servlet Filter

Add a `SubscriptionGuardFilter` in the Spring Security filter chain (after `MemberFilter`, before controllers):
- On each request, resolve the org's subscription status from a cache (not a DB query per request).
- If status is GRACE_PERIOD, SUSPENDED, or EXPIRED:
  - Allow GET/HEAD/OPTIONS requests (read-only).
  - Block POST/PUT/PATCH/DELETE with HTTP 403 and a structured error: `{ "type": "subscription_required", "title": "Subscription required", "detail": "Your subscription has expired. Subscribe to regain full access.", "resubscribeUrl": "/settings/billing" }`.
- If status is LOCKED:
  - Block ALL requests except `GET /api/billing/*` (so they can see billing page and resubscribe).
  - Return HTTP 403 with redirect hint.
- If status is TRIALING or ACTIVE or PENDING_CANCELLATION or PAST_DUE:
  - Allow all requests (full access).

### 3.2 Subscription Status Cache

- Cache subscription status per org in a lightweight in-memory cache (Caffeine, 5-minute TTL).
- ITN webhook handler evicts cache on status change.
- Avoids a DB query on every request.

### 3.3 Frontend Enforcement

- API calls during grace/locked return 403 with `subscription_required` type.
- Frontend catches this error type globally (Axios/fetch interceptor) and shows a modal/banner.
- Write-action buttons (Create, Edit, Delete) are disabled when subscription is not active.
- Banner component shows on every page during GRACE_PERIOD/SUSPENDED/EXPIRED with contextual message and subscribe/resubscribe CTA.

***

## Section 4 — Subscription Status Detection (Scheduled Jobs)

### 4.1 Trial Expiry Job

A scheduled job (Spring `@Scheduled` or Quartz) runs daily:
- Query all subscriptions where `subscription_status = 'TRIALING' AND trial_ends_at < now()`.
- Transition each to EXPIRED, set `grace_ends_at = now() + 60 days`.
- Optional: trigger notification to org owner ("Your trial has expired").

### 4.2 Grace Period Expiry Job

A scheduled job runs daily:
- Query all subscriptions where `subscription_status IN ('GRACE_PERIOD', 'EXPIRED', 'SUSPENDED') AND grace_ends_at < now()`.
- Transition each to LOCKED.
- Optional: trigger notification to org owner ("Your account has been locked").

### 4.3 Pending Cancellation End Job

A scheduled job runs daily:
- Query all subscriptions where `subscription_status = 'PENDING_CANCELLATION' AND current_period_end < now()`.
- Transition each to GRACE_PERIOD, set `grace_ends_at = current_period_end + 60 days`.

***

## Section 5 — Backend API Changes

### 5.1 Billing Endpoints (refactored)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/billing/subscription` | JWT (any member) | Returns subscription status, trial/grace countdown, next billing date |
| `POST` | `/api/billing/subscribe` | JWT (OWNER only) | Generates PayFast checkout form data, returns redirect URL + form fields |
| `POST` | `/api/billing/cancel` | JWT (OWNER only) | Cancels PayFast subscription, transitions to PENDING_CANCELLATION |
| `GET` | `/api/billing/payments` | JWT (OWNER/ADMIN) | Returns payment history from `subscription_payments` table |
| `POST` | `/api/webhooks/subscription` | Unauthenticated (IP + signature validated) | PayFast ITN callback |

### 5.2 Billing Response DTO

```java
public record BillingResponse(
    String status,                    // TRIALING, ACTIVE, PAST_DUE, etc.
    Instant trialEndsAt,              // null if not trialing
    Instant currentPeriodEnd,         // null if trialing
    Instant graceEndsAt,              // null if not in grace
    Instant nextBillingAt,            // null if not active
    int monthlyAmountCents,
    String currency,
    LimitsResponse limits,
    boolean canSubscribe,             // true if TRIALING, EXPIRED, GRACE_PERIOD, LOCKED
    boolean canCancel                 // true if ACTIVE
) {
    public record LimitsResponse(int maxMembers, long currentMembers) {}
}
```

### 5.3 Subscribe Response DTO

```java
public record SubscribeResponse(
    String paymentUrl,                // PayFast hosted page URL
    Map<String, String> formFields    // form data for POST redirect
) {}
```

### 5.4 Admin/Internal Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/internal/billing/extend-trial` | API key | Extends trial for a specific org (admin tool) |
| `POST` | `/internal/billing/activate` | API key | Manually activates subscription (support override) |

***

## Section 6 — Frontend Changes

### 6.1 Billing Settings Page (refactored)

Current: Shows plan name and simulated upgrade button.
New: Full subscription management UI.

**States and their UI:**

| Status | Page Content |
|--------|-------------|
| TRIALING | Trial countdown ("14 days remaining"), feature list, prominent "Subscribe — R499/month" CTA button |
| ACTIVE | "Active subscription", next billing date, payment history list, "Cancel Subscription" link (with confirmation dialog) |
| PENDING_CANCELLATION | "Cancelling on {date}", message about continued access, "Resubscribe" CTA, payment history |
| PAST_DUE | Warning banner ("Payment failed"), link to PayFast to update payment method, payment history showing failed entry |
| GRACE_PERIOD / EXPIRED / SUSPENDED | "Your subscription has expired", grace period countdown, "Subscribe to regain full access" CTA, read-only explanation |
| LOCKED | Full-page "Resubscribe" CTA with org name, data preservation message, subscribe button |

### 6.2 Global Subscription Banner

A persistent banner component rendered in the app shell (layout):
- Not shown for TRIALING (first 7 days) — avoid noise during initial exploration
- Shown for TRIALING (last 7 days): "Your trial ends in {N} days — Subscribe now"
- Shown for PENDING_CANCELLATION: "Your subscription ends on {date}"
- Shown for PAST_DUE: "Payment failed — update your payment method" (warning style)
- Shown for GRACE_PERIOD / EXPIRED / SUSPENDED: "Read-only mode — subscribe to regain full access" (error style)
- Not shown for ACTIVE
- Banner is dismissible per-session for TRIALING/PENDING_CANCELLATION, persistent for PAST_DUE/GRACE/EXPIRED

### 6.3 Write-Action Gating

- Global context provider (`SubscriptionContext`) that exposes `isWriteEnabled` boolean
- Wraps existing mutation buttons (Create Project, Create Customer, Save, Delete, etc.) with disabled state when `isWriteEnabled = false`
- Tooltip on disabled buttons: "Subscribe to enable this action"
- API error interceptor catches `subscription_required` 403 responses and shows a modal with subscribe CTA

### 6.4 PayFast Redirect Handling

The subscribe flow uses a form POST redirect to PayFast (not a popup):
1. User clicks "Subscribe" → API returns form fields
2. Frontend creates a hidden form, populates fields, submits to PayFast URL
3. User completes payment on PayFast
4. PayFast redirects to `return_url` with query params
5. Billing page detects `?result=success` or `?result=cancelled`, polls `GET /api/billing/subscription` for updated status (ITN may arrive before or after redirect)

***

## Section 7 — Migration and Cleanup

### 7.1 Database Migration

A global migration that modifies the existing `subscriptions` table and creates `subscription_payments`. Migration must handle existing data:
- Existing subscriptions with `plan_slug = 'pro'` → `subscription_status = 'ACTIVE'`
- Existing subscriptions with `plan_slug = 'starter'` → `subscription_status = 'TRIALING'`, `trial_ends_at = now() + 14 days` (give existing free tenants a trial window)

### 7.2 Code Cleanup

Remove dead code:
- `Tier.java` enum
- `PlanLimits.java` class (replace with `SubscriptionLimits` with single set of values)
- `PlanSyncService.java` (Clerk billing vestige)
- `PlanSyncController.java` (Clerk billing vestige)
- `Organization.tier` and `Organization.planSlug` fields
- Frontend: Starter/Pro references, `PricingTable` imports (if any remain), upgrade dialog
- `BillingController.upgrade()` endpoint
- All test code referencing Tier.STARTER / Tier.PRO

### 7.3 Impact on Existing Features

- `PlanLimits.maxMembers(tier)` calls throughout the codebase → replace with `SubscriptionLimits.maxMembers()` (single value)
- `Organization.getTier()` checks → replace with subscription status checks where needed
- Feature gating in frontend that checks tier → replace with subscription status checks
- `TenantProvisioningService` → create TRIALING subscription instead of STARTER subscription

***

## Section 8 — Testing Strategy

### 8.1 Backend Tests

- **SubscriptionLifecycleTest**: State machine transitions (TRIALING → ACTIVE, ACTIVE → PENDING_CANCELLATION, etc.)
- **PayFastSubscriptionServiceTest**: Form data generation, signature calculation, checkout URL construction
- **PayFastItnWebhookTest**: Signature verification, IP validation, idempotency, status routing (COMPLETE/FAILED/CANCELLED)
- **SubscriptionGuardFilterTest**: Read-only enforcement per status, allowed paths for LOCKED, passthrough for ACTIVE/TRIALING
- **SubscriptionScheduledJobsTest**: Trial expiry detection, grace period expiry, pending cancellation transition
- **BillingControllerIntegrationTest**: Subscribe endpoint, cancel endpoint, payments list, status endpoint per lifecycle state

### 8.2 Frontend Tests

- Billing page renders correctly for each subscription status
- Subscribe button triggers form POST redirect
- Cancel flow shows confirmation dialog
- Banner appears/disappears based on status
- Write-action buttons disabled during grace period
- API error interceptor handles `subscription_required` 403

### 8.3 Manual QA Scenarios

- Full lifecycle: provision org → trial → subscribe via PayFast sandbox → receive ITN → verify ACTIVE → cancel → verify grace → verify locked
- Trial expiry: provision org → wait for scheduled job (or trigger manually) → verify grace period
- Failed payment: simulate failed ITN → verify PAST_DUE banner → simulate recovery ITN → verify ACTIVE
- Resubscribe: from GRACE_PERIOD, complete new checkout → verify ACTIVE

***

## ADR Topics

1. **Subscription state machine design** — Why a single-plan lifecycle replaces the Starter/Pro tier model. Trade-offs of lifecycle complexity vs. business simplicity. Why GRACE_PERIOD is a separate state from CANCELLED.
2. **Platform vs. tenant PayFast integration** — Why platform billing uses direct config (not the BYOAK `IntegrationRegistry`/`SecretStore` pattern). Separation of concerns: platform billing ≠ tenant invoice payments.
3. **Read-only enforcement strategy** — Filter-level HTTP method blocking vs. capability-based blocking vs. database-level read-only. Why filter-level is simplest and sufficient for v1.
4. **Trial and grace expiry detection** — Scheduled job (poll) vs. on-access check (lazy). Trade-offs of immediacy vs. simplicity. Why daily scheduled job is sufficient.

***

## Style and boundaries

- Extend the existing `billing/` package — do not create a new top-level package
- PayFast-specific code in a `billing/payfast/` sub-package (service, ITN handler, form builder)
- `SubscriptionPayment` entity in `billing/` package (audit trail for payments)
- Follow existing controller discipline: thin controllers, service handles all logic
- Subscription status cache: use Spring's `@Cacheable` with Caffeine (already a dependency) or a simple `ConcurrentHashMap` with TTL
- Frontend: billing page at existing `/settings/billing` route, banner in app shell layout
- Config properties under `heykazi.billing.*` namespace
- Reuse existing `AuditEventBuilder` for subscription lifecycle audit events
- Reuse existing notification infrastructure for trial/grace/locked alerts to org owners
