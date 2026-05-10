# Platform Administration

**Bounded context:** see [`10-bounded-contexts.md` § platform-administration](../10-bounded-contexts.md).
**Java packages:** `accessrequest/`, `billing/` (subscription side — *not* `invoice/`).
**Last reviewed:** 2026-05-10.

## Purpose

Platform administration is the **provider-side (b2mash) administration of tenant orgs** on the Kazi SaaS platform. It owns three distinct concerns: (1) admin-gated org sign-up via OTP-verified `AccessRequest` flow, (2) platform-admin approval/reject pipeline with Keycloak org provisioning, (3) tenant-level subscription lifecycle (trial → active → grace → suspended) including PayFast subscription payments.

This module is **not** the in-tenant administration surface — that is `identity-access` (member roles, custom roles, capability matrix). It is also **not** the per-tenant customer-billing module — that is `invoicing` (`Invoice`, `InvoiceLine`, `BillingRun`). The two `billing` java packages refer to *different layers*: `billing/` (this module) is the b2mash-charges-the-tenant subscription layer; `invoice/` + `billingrun/` (the `invoicing` module) is the tenant-charges-its-customers layer.

## Entities owned

All three entities live in the **public schema** — they are shared/cross-tenant by design (the access request fires before any tenant exists; subscription billing crosses tenants).

| Entity | Table (schema) | Key fields | Scope |
|---|---|---|---|
| `AccessRequest` | `access_requests` (public) | `email`, `fullName`, `organizationName`, `status`, `otpHash`, `otpExpiresAt`, `reviewedBy` | shared `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequest.java:18` |
| `Subscription` | `subscriptions` (public) | `organizationId`, `subscriptionStatus`, `trialEndsAt`, `payfastToken` | shared `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java:20` |
| `SubscriptionPayment` | `subscription_payments` (public) | `subscriptionId`, `payfastPaymentId`, `amountCents`, `status`, `paymentDate` | shared `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionPayment.java:21` |

Public-schema status confirmed in `_discovery/A6-cross-cutting.md` §1: "the only public-schema entities are `organizations`, `org_schema_mapping`, `subscriptions`, `subscription_payments`, `access_requests`, and the portal read-model projection tables."

`Subscription.VALID_TRANSITIONS` (`backend/.../billing/Subscription.java:29`) defines the state machine: `TRIALING → ACTIVE | EXPIRED`; `ACTIVE → PENDING_CANCELLATION | PAST_DUE`; `PAST_DUE → ACTIVE | SUSPENDED`; `SUSPENDED → ACTIVE | LOCKED`. (Note: this state machine still exists in code despite the "no plan-tier subscriptions" product decision recorded in memory — see Open Questions.)

## REST surface

### Public (pre-auth) — gateway `permitAll`

| Endpoint | Verb | Notes |
|---|---|---|
| `/api/access-requests` | POST | Submit OTP request `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicController.java:24` |
| `/api/access-requests/verify` | POST | Verify OTP code `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestPublicController.java:30` |

Both endpoints are explicitly listed in the gateway `permitAll` matcher: `→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:52` (`.requestMatchers("/api/access-requests", "/api/access-requests/verify")`). Per A3 §9, no authentication header is required — the OTP itself is the credential for the verify call.

### Platform-admin (JWT + `platform-admins` group claim)

| Endpoint | Verb | Notes |
|---|---|---|
| `/api/platform-admin/access-requests` | GET | List pending/all requests `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminController.java:28` |
| `/api/platform-admin/access-requests/{id}/approve` | POST | Approve + provision `→ backend/.../accessrequest/PlatformAdminController.java:34` |
| `/api/platform-admin/access-requests/{id}/reject` | POST | Reject with reason `→ backend/.../accessrequest/PlatformAdminController.java:41` |
| `/api/platform-admin/billing/tenants` | GET | List orgs + subscription state `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/PlatformBillingController.java:32` |
| `/api/platform-admin/billing/tenants/{orgId}` | GET | Tenant detail `→ backend/.../billing/PlatformBillingController.java:42` |
| `/api/platform-admin/billing/tenants/{orgId}/extend-trial` | POST | Manual trial extension `→ backend/.../billing/PlatformBillingController.java:53` |
| `/api/platform-admin/demo/...` | varies | Demo provisioning + teardown `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java:23` (note: lives under `demo/`, not `accessrequest/`) |

Authorization is performed by `PlatformSecurityService` calls reading `RequestScopes.GROUPS`, which is populated by `PlatformAdminFilter`.

### Tenant-side (the org pays b2mash)

These are *not* under `/api/platform-admin` — they live under `/api/billing` and are called by the org owner during their normal session:

| Endpoint | Verb | Notes |
|---|---|---|
| `/api/billing/subscription` | GET | Current org subscription `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingController.java:23` |
| `/api/billing/subscribe` | POST | Initiate PayFast subscription `→ backend/.../billing/BillingController.java:28` |
| `/api/billing/cancel` | POST | Cancel subscription `→ backend/.../billing/BillingController.java:34` |
| `/api/billing/payments` | GET | Payment history `→ backend/.../billing/BillingController.java:40` |
| `/api/billing/itn` | POST | PayFast ITN webhook `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnController.java:32` |

### Internal (API-key)

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingController.java:17` — `/internal/billing/*` (`/set-plan`, `/extend-trial`, `/activate`) for ops scripts.

## Frontend pages / components

### Tenant-side (in the org shell)

- `frontend/app/(app)/create-org/page.tsx` — landing for users with no org yet; routes to access-request flow or demo provisioning.
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — subscription status, trial/grace countdown, payment history, subscribe/cancel; per A2 §2.
- `frontend/app/(app)/org/[slug]/settings/billing/actions.ts` — `getSubscription`, `subscribe`, `cancel`, `getPayments` server actions; per A2 §3.
- `frontend/components/billing/` — `SubscribeButton`, `CancelConfirmDialog`, `PaymentHistory`, `TrialCountdown`, `GraceCountdown`, `SubscriptionBanner`; per A2 §6.

### Platform-admin side (b2mash staff)

- `frontend/app/(app)/platform-admin/access-requests/` — review, approve, reject access requests.
- `frontend/app/(app)/platform-admin/billing/` — cross-tenant subscription management.
- `frontend/app/(app)/platform-admin/demo/` — demo-tenant provisioning + teardown.
- `frontend/app/(app)/platform-admin/layout.tsx` — group-claim-gated layout (the platform-admin shell sits *outside* the `org/[slug]/` tree because it operates without a tenant context).

## Domain events

_None recorded._ Neither `accessrequest/*.java` nor `billing/*.java` (excluding `payfast/`) publishes a `DomainEvent` (`grep -n "DomainEvent\|publishEvent\|@EventListener"` returned no hits). Audit emission happens via direct `auditService.log(...)` calls (the standard pattern per A6 §3) — not via the event bus. This is consistent with subscription lifecycle being a platform-tier concern that does not need to drive the per-tenant `automation` or `notifications` modules.

## Cross-cutting touchpoints

- **`PlatformAdminFilter`** binds `RequestScopes.GROUPS` from the JWT `groups` claim: `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/PlatformAdminFilter.java:27` (line 37: `Set<String> groups = JwtUtils.extractGroups(jwt);`). Filter chain position is sixth in the staff chain — after `subscriptionGuardFilter`, before `tenantLoggingFilter` (A6 §2 step 6). This means platform-admin checks read groups *after* tenant + member binding, but the controllers themselves do not require a tenant context.
- **Public-schema operation.** `AccessRequest`, `Subscription`, `SubscriptionPayment` are all `@Table(schema = "public")` (`Subscription.java:19`). They bypass schema-per-tenant isolation by design — there is no other way to talk about an org *before it has a schema* (access request) or *across orgs* (platform-admin billing list).
- **No tenant context bound during pre-org-creation flows.** The public access-request endpoints fire before any `OrgSchemaMapping` row exists; `TenantFilter` cannot resolve a tenant. This is fine because the controllers operate on the public schema only and never call into tenant-scoped services.
- **`SubscriptionGuardFilter`** (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionGuardFilter.java`) is a sibling concern — it runs in the staff filter chain and gates non-owner access during PAST_DUE/SUSPENDED. Per A6 §2: "must run after Member because it needs the role to allow owner-bypass for grace-period flows."
- **`SubscriptionExpiryJob`** (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java:54`, three cron jobs at 3am, 3:05am, 3:10am — per A6 §7) evaluates trial/grace/past-due transitions daily across all tenants via `TenantScopedRunner.forEachTenant`. This is the `SubscriptionEnforcementScheduler` referenced in `_discovery/A1-backend-map.md` §5 (named differently in code).
- **`SubscriptionStatusCache`** (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionStatusCache.java`) — in-memory cache so the guard filter does not hammer the DB on every request.
- **PayFast adapter.** `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PlatformPayFastService.java` is the only platform-PSP implementation. Per A1 §1.2, this is *separate from* the tenant-side payment integration (`integration/IntegrationDomain.PAYMENT`) — there is no port abstraction over platform billing, only over per-tenant invoice payments.
- **Audit emission.** Approval, rejection, subscription transitions, admin trial extensions all call `auditService.log(...)` directly — written into the *tenant* `audit_events` table on approval (because by then the schema exists) and into the public actor record otherwise.
- **Keycloak provisioning.** On approval, `KeycloakProvisioningClient` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`) creates the Keycloak org + invites the user, then `TenantProvisioningService` runs the schema + seed pipeline.

## Vertical specifics

This module is **vertical-agnostic**. The vertical profile is selected during org provisioning (the `verticalProfile` parameter on `TenantProvisioningService.provisionTenant(orgId, name, verticalProfile, country)` per `_discovery/A6-cross-cutting.md` §4 — "Vertical onboarding flow"). Platform admin merely *selects* the profile when approving an access request; the verticalisation logic itself lives in `tenancy-provisioning` and `vertical-profiles`.

See cross-links: [`tenancy-provisioning.md`](./tenancy-provisioning.md), [`vertical-profiles.md`](./vertical-profiles.md), [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md).

Subscription billing is uniform across verticals — there are no legal-only or accounting-only platform-tier features. (Trust-accounting hard-guards belong to `invoicing` + `trust-accounting`, not to the platform billing layer.)

## Active ADRs

Per `90-adr-index.md`:

- **ADR-154** admin-approved-provisioning-flow — matches the `tenant_registration_model` memory note (admin-gated, no self-serve).
- **ADR-155** access-request-lifecycle-model — `AccessRequest` status machine.
- **ADR-220** platform-vs-tenant-payfast-integration — the deliberate split between `PlatformPayFastService` (this module) and any tenant-side payment integration.
- **ADR-098** payment-gateway-interface-design — the canonical port shape for *tenant* payments (cross-link: not used by this module's `PlatformPayFastService`).
- **ADR-275** oauth2-augmentation-org-integration — sits in `OrgIntegration`; tangentially relevant if PayFast tokens migrate there in future.

The "no plan-tier subscriptions" product decision (memory: `project_no_plan_subscriptions`, `tenant_registration_model`) is **not** captured in an ADR. The plan-tier graveyard ADRs (010, 013, 014, 219, 222) sit in the **Stale** section of `90-adr-index.md` — abandoned but never marked `Status: Stale` in-file. Module pages should not link to them.

## Key flows

There is no dedicated flow page for admin onboarding (`50-flows/customer-onboarding-and-kyc.md` is the wrong target — that's customer-side). The flow inline:

1. **Sign-up request.** Prospective owner submits `POST /api/access-requests` with email, full name, desired organization name. Status: `PENDING_OTP_VERIFICATION`. OTP generated, hashed, persisted with `otpExpiresAt`; OTP emailed via the EMAIL integration port (foundational, always enabled).
2. **OTP verification.** Owner submits `POST /api/access-requests/verify` with email + OTP. Hash compared, expiry checked. On success: status → `PENDING_REVIEW`. The endpoint is `permitAll` at the gateway — the OTP itself is the credential.
3. **Email-domain validation.** `EmailDomainValidator` (`→ backend/.../accessrequest/EmailDomainValidator.java`) blocks disposable / blocklisted domains; sufficiently low-friction that legitimate orgs pass.
4. **Platform-admin review.** A b2mash staff member with the `platform-admins` Keycloak group claim hits `GET /api/platform-admin/access-requests` (PlatformAdminFilter binds GROUPS; controller checks via `PlatformSecurityService`).
5. **Approval.** `POST /api/platform-admin/access-requests/{id}/approve` runs `AccessRequestApprovalService`: marks the request `APPROVED`, calls `KeycloakProvisioningClient.createOrgAndInviteUser(...)`, then `TenantProvisioningService.provisionTenant(orgId, name, verticalProfile, country)`. The latter creates the schema, runs Flyway tenant migrations, sets the vertical profile, installs document + automation packs (per A6 §4).
6. **Subscription bootstrap.** `SubscriptionService.createTrialSubscription(orgId)` writes a `Subscription` row with `subscriptionStatus = TRIALING` and `trialEndsAt = now + trialDuration`.
7. **Owner sign-in.** Owner accepts Keycloak invite, signs in, lands on `/dashboard` → `/org/{slug}/dashboard`. The org is now active.
8. **Trial / grace lifecycle.** `SubscriptionExpiryJob` (3am daily) iterates all subscriptions: `TRIALING` past `trialEndsAt` → `EXPIRED` (or `ACTIVE` if PayFast token present); `PAST_DUE` past grace → `SUSPENDED`; etc. `SubscriptionGuardFilter` reads `SubscriptionStatusCache` and gates non-owner traffic accordingly.
9. **Rejection.** `POST /api/platform-admin/access-requests/{id}/reject` sets status `REJECTED` with `rejectionReason`; emails the prospect; no Keycloak / tenant artifacts created.

A demo-mode bypass exists per ADR-224: platform admins can hit `/api/platform-admin/demo/...` to provision a tenant directly without an `AccessRequest` row, used for sales demos and load testing.

## Open questions / known fragility

- **Plan-tier ADRs are a quiet graveyard.** ADR-010 (billing-integration), ADR-013 (plan-state-propagation), ADR-014 (plan-enforcement), ADR-219 (subscription-state-machine-design), ADR-222 (trial-and-grace-expiry-detection) all encode plan-tier mechanics that have been abandoned per `project_no_plan_subscriptions`. None carries an explicit `Status: Stale` marker; they sit in `adr/` indistinguishable from active ADRs except via `90-adr-index.md`. Module pages MUST consult the index before linking — direct ADR linkage is a footgun.
- **`Subscription.VALID_TRANSITIONS` still encodes a state machine** (`Subscription.java:27`) including `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED`, even though the product decision is "no plan tiers." The state machine is doing double duty as a trial/grace mechanism. This is a smell: code reality (state machine present, scheduler running) and product decision (no plan tiers) are not aligned. Either the scheduler is no-op in practice, or the no-plan-tier decision is narrower than memory suggests. Worth verifying.
- **PayFast is the only platform PSP — no port abstraction.** `PlatformPayFastService` is called directly from `BillingController` and `SubscriptionItnController`; there is no `PaymentGateway` interface for the platform tier. Contrast with the tenant side, where `IntegrationDomain.PAYMENT` plus `IntegrationRegistry.resolve(PAYMENT, ...)` (per A6 §5) resolves the adapter dynamically. Adding a second platform PSP (Stripe, etc.) requires extracting an interface — not a refactor blocker, but a known shape mismatch.
- **Two `BillingController` classes coexist** (`BillingController` for `/api/billing/*` and `PlatformBillingController` for `/api/platform-admin/billing/*` and `AdminBillingController` for `/internal/billing/*`). The naming hides the boundary — a reader has to grep `@RequestMapping` to know which one talks to PayFast vs which one is just a read-API for ops staff. Worth a class-rename pass at some point.
- **No `DomainEvent` emission.** Subscription transitions do not flow through the event bus, so the `automation` module cannot fire rules on subscription lifecycle events. If a tenant wants to "send a Slack message when our subscription enters PAST_DUE," they cannot. This is by design (subscription is a platform concern, not a tenant concern), but worth noting if a future feature wants the linkage.
- **Audit during pre-tenant flows.** Audit emission for `AccessRequest` mutations happens *before* a tenant schema exists, so the audit row cannot be written to the tenant `audit_events` table. The current implementation either skips the audit or writes to a public-schema actor table — worth verifying the exact location, since auditability of "who approved what access request" is a platform-trust concern.
