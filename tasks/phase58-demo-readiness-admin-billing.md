# Phase 58 — Demo Readiness & Admin Billing Controls

Phase 58 closes the gap between "the platform can process payments" (Phase 57) and "the platform is ready to demo to prospects." It introduces a billing method dimension that separates commercial arrangement from access-control status, an admin billing management panel, one-click demo tenant provisioning with realistic seed data per vertical, and safe demo tenant cleanup. The design is additive and non-breaking -- the SubscriptionGuardFilter does not change.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 427 | Billing Method Dimension | Backend | -- | M | 427A, 427B | **Done** (PRs #887, #888) |
| 428 | Admin Billing Management | Backend + Frontend | 427 | L | 428A, 428B | **Done** (PRs #889, #890) |
| 429 | Demo Tenant Provisioning | Backend + Frontend | 427 | L | 429A, 429B | **Done** (PRs #891, #892) |
| 430 | Demo Data Seeding | Backend | 429 | L | 430A, 430B, 430C | **Done** (PRs #893, #894, #895) |
| 431 | Demo Tenant Cleanup | Backend + Frontend | 427 | L | 431A, 431B | |

## Dependency Graph

```
[E427 Billing Method] ─────────────────────────────────────────┐
    │                                                          │
    ├──► [E428 Admin Billing Mgmt]                             │
    │         ├── 428A (Backend)                               │
    │         └── 428B (Frontend)                              │
    │                                                          │
    ├──► [E429 Demo Provisioning]                              │
    │         ├── 429A (Backend)                               │
    │         └── 429B (Frontend)                              │
    │              │                                           │
    │              └──► [E430 Demo Data Seeding]               │
    │                     ├── 430A (Base + Generic seeder)     │
    │                     ├── 430B (Accounting + Legal seeder) │
    │                     └── 430C (Reseed endpoint + tests)   │
    │                                                          │
    └──► [E431 Demo Cleanup] ◄─────────────────────────────────┘
              ├── 431A (Backend)
              └── 431B (Frontend)
```

**Parallel tracks**: After Epic 427 (Billing Method Dimension) lands, Epics 428 (Admin Billing), 429 (Demo Provisioning), and 431 (Demo Cleanup) can all begin in parallel -- they have zero dependency on each other. Epic 430 depends on Epic 429. Within each multi-slice epic, backend slices must complete before frontend slices.

## Implementation Order

### Stage 1: Backend Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 427: Billing Method Dimension | V18 migration + entity changes + cache enhancement + scheduled job updates are the prerequisite for all other Phase 58 work. |

### Stage 2: Backend Services (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 428A: Admin Billing Backend | Admin billing service + controller + DTOs. Depends on billing method entity from 427. Independent of 429, 431. | **Done** (PR #889) |
| 2b | Epic 429A: Demo Provisioning Backend | Demo provision service + controller + KeycloakAdminClient extensions. Depends on PILOT billing method from 427. Independent of 428, 431. | **Done** (PR #891) |
| 2c | Epic 431A: Demo Cleanup Backend | Cleanup service + controller + safety validation. Depends on `isCleanupEligible()` from 427. Independent of 428, 429. | **Done** (PR #896) |

### Stage 3: Demo Data Seeding (After Provisioning Backend)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 430A: Base + Generic Seeder | BaseDemoDataSeeder + GenericDemoDataSeeder. Must exist before provisioning can seed data. | **Done** (PR #893) |
| 3b | Epic 430B: Accounting + Legal Seeders | Profile-specific seeders. Can begin after 430A provides the base class. | **Done** (PR #894) |
| 3c | Epic 430C: Reseed Endpoint + Integration Tests | Reseed endpoint on DemoAdminController + comprehensive data consistency tests. After 430A and 430B. | **Done** (PR #895) |

### Stage 4: Frontend (After Respective Backend Slices)

| Order | Epic | Rationale |
|-------|------|-----------|
| 4a | Epic 428B: Admin Billing Frontend | Platform admin billing page, detail slide-over. Depends on 428A backend API. **Done** (PR #890) |
| 4b | Epic 429B: Demo Provisioning Frontend | Demo creation form + success state. Depends on 429A backend API. **Done** (PR #892) |
| 4c | Epic 431B: Demo Cleanup + Billing Adaptation Frontend | Demo tenant list, delete confirmation dialog, billing page adaptation for `adminManaged`. Depends on 431A backend API. |

### Timeline

```
Stage 1:  [E427A] [E427B]                               <- foundation (must complete first)
Stage 2:  [E428A] [E429A] [E431A]                       <- parallel backend (after E427)
Stage 3:  [E430A] [E430B] [E430C]                       <- sequential seeding (after E429A)
Stage 4:  [E428B] [E429B] [E431B]                       <- parallel frontend (after respective backends)
```

---

## Epic 427: Billing Method Dimension

**Goal**: Create the V18 global migration that adds `billing_method` and `admin_note` columns to the `subscriptions` table, migrates existing PayFast-subscribed tenants. Add the `BillingMethod` enum with helper methods (`isAdminManaged()`, `isTrialAutoExpiring()`, `isCleanupEligible()`). Extend the `Subscription` entity with `billingMethod` and `adminNote` fields plus the `adminTransitionTo()` method. Enhance `SubscriptionStatusCache` with `CachedSubscriptionInfo` record. Update `BillingResponse` DTO with `billingMethod`, `adminManaged`, `adminNote` fields. Modify `SubscriptionExpiryJob` to filter trial expiry by billing method. Update `SubscriptionItnService` to set `billingMethod = PAYFAST` on first payment. Add `canSubscribe` logic that respects billing method. Includes entity persistence tests and scheduled job tests.

**References**: `architecture/phase58-demo-readiness-admin-billing.md` Sections 11.2, 11.3.6, 11.3.7, 11.4.3, 11.7; ADR-223

**Dependencies**: None (foundation epic)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **427A** | 427.1--427.6 | V18 global migration, `BillingMethod` enum, `Subscription` entity extensions (`billingMethod`, `adminNote`, `adminTransitionTo()`), `CachedSubscriptionInfo` record in `SubscriptionStatusCache`, updated `BillingResponse` DTO, updated `SubscriptionRepository` with billing method filter query | **Done** (PR #887) |
| **427B** | 427.7--427.12 | `SubscriptionExpiryJob` billing method filter, `SubscriptionItnService` PAYFAST auto-set, `canSubscribe` logic update, `BillingMethodTest`, `SubscriptionExpiryJobBillingMethodTest`, existing test updates | **Done** (PR #888) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 427.1 | Create V18 global migration for billing method dimension | 427A | | `backend/src/main/resources/db/migration/global/V18__subscription_billing_method.sql`. ALTER TABLE subscriptions ADD COLUMN billing_method VARCHAR(30) NOT NULL DEFAULT 'MANUAL'; ADD COLUMN admin_note TEXT; CREATE INDEX idx_subscriptions_billing_method ON subscriptions(billing_method); Data migration: UPDATE subscriptions SET billing_method = 'PAYFAST' WHERE payfast_token IS NOT NULL AND billing_method = 'MANUAL'; Add COMMENT ON COLUMN for both new columns. Use IF NOT EXISTS for idempotency. Pattern: follow `V17__subscription_payments.sql` structure. |
| 427.2 | Create `BillingMethod` enum | 427A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingMethod.java`. Enum values: PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL. Methods: `isAdminManaged()` (returns `this != PAYFAST`), `isTrialAutoExpiring()` (returns `this == PAYFAST || this == MANUAL`), `isCleanupEligible()` (returns `this == PILOT || this == COMPLIMENTARY`). Pattern: follow existing enum patterns in codebase (e.g., `SubscriptionStatus` inside `Subscription.java` or `AccessRequestStatus.java`). |
| 427.3 | Extend `Subscription` entity with billing method fields | 427A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java`. Add `billingMethod` field (`@Enumerated(EnumType.STRING)`, `@Column(name = "billing_method")`, default `BillingMethod.MANUAL`). Add `adminNote` field (String, nullable, `@Column(name = "admin_note")`). Add `adminTransitionTo(SubscriptionStatus)` method with `ADMIN_ALLOWED_TARGETS` set (TRIALING, ACTIVE, GRACE_PERIOD, LOCKED) -- throws `InvalidStateException` for disallowed targets. Add getter/setter for `billingMethod` and `adminNote`. Pattern: follow existing field style in `Subscription.java` -- no Lombok, explicit getters. |
| 427.4 | Enhance `SubscriptionStatusCache` with `CachedSubscriptionInfo` | 427A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionStatusCache.java`. Add `CachedSubscriptionInfo` record (SubscriptionStatus status, BillingMethod billingMethod). Change cache type from `Cache<UUID, SubscriptionStatus>` to `Cache<UUID, CachedSubscriptionInfo>`. Preserve existing `getStatus(UUID)` method by unwrapping `info.status()`. Add new `getInfo(UUID)` method returning full `CachedSubscriptionInfo`. Update cache population to include `billingMethod`. Update `evict()` method (no change needed -- same key). Pattern: follow existing cache structure in `SubscriptionStatusCache.java`. |
| 427.5 | Update `BillingResponse` DTO with billing method fields | 427A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingResponse.java`. Add fields: `String billingMethod`, `boolean adminManaged`, `String adminNote`. Update `from()` static factory to populate these from `Subscription.billingMethod`. Update `canSubscribe` logic: subscribable only if status allows AND (`billingMethod == PAYFAST` or `billingMethod == MANUAL`). Update `canCancel` logic: cancellable only if ACTIVE and `billingMethod == PAYFAST`. Pattern: follow existing record structure. |
| 427.6 | Add billing method filter query to `SubscriptionRepository` | 427A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionRepository.java`. Add: `List<Subscription> findBySubscriptionStatusAndBillingMethodInAndTrialEndsAtBefore(SubscriptionStatus status, List<BillingMethod> methods, Instant cutoff)`. This is a Spring Data derived query -- no JPQL needed. Pattern: follow existing query methods in `SubscriptionRepository.java`. |
| 427.7 | Update `SubscriptionExpiryJob` with billing method filter | 427B | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java`. In `processTrialExpiry()`: replace `findBySubscriptionStatusAndTrialEndsAtBefore(TRIALING, now)` with `findBySubscriptionStatusAndBillingMethodInAndTrialEndsAtBefore(TRIALING, List.of(BillingMethod.PAYFAST, BillingMethod.MANUAL), now)`. Grace period and pending cancellation expiry: no changes needed. Pattern: follow existing job method structure. |
| 427.8 | Update `SubscriptionItnService` to set PAYFAST billing method | 427B | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnService.java`. After processing first successful payment (where subscription transitions to ACTIVE): if `subscription.getBillingMethod() != BillingMethod.PAYFAST`, set `subscription.setBillingMethod(BillingMethod.PAYFAST)`. This handles MANUAL -> PAYFAST transition on first card payment. Pattern: follow existing ITN handler logic. |
| 427.9 | Update `SubscriptionService.getSubscription()` to use cache info | 427B | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java`. In `getSubscription()` and `BillingResponse.from()` builder: use `subscriptionStatusCache.getInfo()` where applicable, or load `billingMethod` from subscription entity for the DTO factory. Ensure `BillingResponse` includes `billingMethod`, `adminManaged`, and `adminNote`. |
| 427.10 | Create `BillingMethodTest` | 427B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingMethodTest.java`. ~10 tests: (1) BillingMethod.MANUAL is default on new subscription, (2) isAdminManaged returns true for DEBIT_ORDER/PILOT/COMPLIMENTARY/MANUAL, false for PAYFAST, (3) isTrialAutoExpiring returns true for PAYFAST/MANUAL, false for others, (4) isCleanupEligible returns true for PILOT/COMPLIMENTARY, false for others, (5) adminTransitionTo ACTIVE succeeds, (6) adminTransitionTo PENDING_CANCELLATION throws InvalidStateException, (7) adminTransitionTo PAST_DUE throws InvalidStateException, (8) billingMethod persists and retrieves correctly (integration), (9) adminNote persists and retrieves correctly, (10) V18 migration sets existing PayFast subscriptions to PAYFAST. Unit tests for enum methods, integration tests for persistence. Pattern: follow `SubscriptionLifecycleTest.java` for unit tests, `SubscriptionEntityTest.java` for persistence tests. |
| 427.11 | Create `SubscriptionExpiryJobBillingMethodTest` | 427B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJobBillingMethodTest.java`. ~6 tests: (1) trial expiry processes MANUAL subscriptions with expired trial, (2) trial expiry processes PAYFAST subscriptions with expired trial, (3) trial expiry SKIPS PILOT subscriptions even with expired trial, (4) trial expiry SKIPS COMPLIMENTARY subscriptions, (5) trial expiry SKIPS DEBIT_ORDER subscriptions, (6) grace period expiry applies to ALL billing methods. Setup: create subscriptions in various billing methods with past trial dates. Pattern: follow `SubscriptionExpiryJobTest.java` setup. |
| 427.12 | Update existing billing tests for billing method field | 427B | | Modify `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingControllerIntegrationTest.java` and `SubscriptionIntegrationTest.java`. Update assertions to include `billingMethod` and `adminManaged` in `BillingResponse`. Update any test that creates subscriptions to account for the new default `MANUAL` billing method. Verify `canSubscribe` is false for PILOT subscriptions. Verify `canCancel` is false for non-PAYFAST subscriptions. Pattern: follow existing test patterns. |

### Key Files

**Slice 427A -- Create:**
- `backend/src/main/resources/db/migration/global/V18__subscription_billing_method.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingMethod.java`

**Slice 427A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java` -- add billingMethod, adminNote, adminTransitionTo()
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionStatusCache.java` -- CachedSubscriptionInfo enhancement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingResponse.java` -- add billingMethod, adminManaged, adminNote
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionRepository.java` -- add billing method filter query

**Slice 427A -- Read for context:**
- `backend/src/main/resources/db/migration/global/V17__subscription_payments.sql` -- migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionGuardFilter.java` -- verify no changes needed

**Slice 427B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingMethodTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJobBillingMethodTest.java`

**Slice 427B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java` -- billing method filter
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnService.java` -- set PAYFAST on first payment
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java` -- use billingMethod in response
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingControllerIntegrationTest.java` -- update assertions
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionIntegrationTest.java` -- update assertions

**Slice 427B -- Read for context:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJobTest.java` -- test pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionLifecycleTest.java` -- unit test pattern

### Architecture Decisions

- **V18 migration**: Global migration (public schema) because subscriptions is a global entity. The migration is backward-compatible via `DEFAULT 'MANUAL'` so all existing subscriptions get a valid billing method without downtime.
- **Data migration within V18**: Subscriptions with existing `payfast_token` are updated to `PAYFAST`. This is a one-time idempotent update.
- **BillingMethod as standalone enum**: Unlike `SubscriptionStatus` which is nested inside `Subscription.java`, `BillingMethod` is a standalone enum because it is referenced by multiple services (AdminBillingService, DemoProvisionService, DemoCleanupService) and contains behavior methods. Per ADR-223.
- **Two-slice decomposition**: 427A covers migration, enum, entity, cache, and DTO changes (the data model foundation). 427B covers scheduled job updates, ITN handler, service updates, and tests (the behavioral layer). Each slice touches 6-8 files.
- **SubscriptionGuardFilter unchanged**: The guard filter reads `subscriptionStatus` only. `billingMethod` is invisible to access control. This is the most critical design invariant of Phase 58.

---

## Epic 428: Admin Billing Management

**Goal**: Create the `AdminBillingService` for listing tenants with billing info, overriding subscription status and billing method, extending trials, with full audit trail. Create `AdminBillingController` following the `PlatformAdminController` pattern with `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. Build the platform admin billing frontend: tenant list page with status/method badges and filters, billing detail slide-over with override controls, trial extension, and admin note management. Includes backend integration tests and frontend component tests.

**References**: `architecture/phase58-demo-readiness-admin-billing.md` Sections 11.3.1, 11.4.1, 11.6, 11.8; ADR-223

**Dependencies**: Epic 427 (billing method entity, BillingMethod enum, adminTransitionTo)

**Scope**: Backend + Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **428A** | 428.1--428.6 | `AdminBillingService`, `AdminBillingController`, admin DTOs (`AdminBillingOverrideRequest`, `AdminTenantBillingResponse`, `ExtendTrialRequest`), audit event integration, `AdminBillingEndpointTest` | **Done** (PR #889) |
| **428B** | 428.7--428.12 | Platform admin billing page (tenant list with badges, filters, search), billing detail slide-over (status/method changes, trial extension, admin note), layout update with "Billing" nav, frontend tests | **Done** (PR #890) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 428.1 | Create admin billing DTOs | 428A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingDtos.java`. Records: `AdminBillingOverrideRequest(String status, String billingMethod, Instant currentPeriodEnd, @NotBlank String adminNote)`, `AdminTenantBillingResponse(UUID organizationId, String organizationName, String verticalProfile, String subscriptionStatus, String billingMethod, Instant trialEndsAt, Instant currentPeriodEnd, Instant graceEndsAt, Instant createdAt, int memberCount, String adminNote, boolean isDemoTenant)`, `ExtendTrialRequest(int days)`. Static factory on `AdminTenantBillingResponse.from(Organization, Subscription, long memberCount, String verticalProfile)`. Pattern: follow existing DTO records in `BillingResponse.java`. |
| 428.2 | Create `AdminBillingService` | 428A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingService.java`. Methods: `List<AdminTenantBillingResponse> listTenants(String statusFilter, String billingMethodFilter, String profileFilter, String search)` -- joins organizations + subscriptions + member counts + vertical profiles; `AdminTenantBillingResponse getTenant(UUID orgId)` -- loads org + subscription + member count; `AdminTenantBillingResponse overrideBilling(UUID orgId, AdminBillingOverrideRequest request)` -- validates override rules (no PENDING_CANCELLATION/PAST_DUE, no PAYFAST without token, adminNote required), calls `subscription.adminTransitionTo()` for status, sets billingMethod, evicts cache, emits audit; `AdminTenantBillingResponse extendTrial(UUID orgId, int days)` -- validates TRIALING status, extends trialEndsAt. Inject: `SubscriptionRepository`, `OrganizationRepository`, `MemberRepository` (for count), `SubscriptionStatusCache`, `AuditService`. Pattern: follow `SubscriptionService.java` for service structure, `AccessRequestApprovalService.java` for admin action pattern. |
| 428.3 | Create `AdminBillingController` | 428A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingController.java`. Class-level `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. `@RequestMapping("/api/platform-admin/billing")`. Endpoints: `GET /tenants` (query params: status, billingMethod, profile, search), `GET /tenants/{orgId}`, `PUT /tenants/{orgId}/status` (body: AdminBillingOverrideRequest), `POST /tenants/{orgId}/extend-trial` (body: ExtendTrialRequest). Each method is a one-liner delegating to `AdminBillingService`. Return `ResponseEntity<T>`. Pattern: follow `PlatformAdminController.java` security pattern, thin controller discipline from CLAUDE.md. |
| 428.4 | Add audit event types for admin billing actions | 428A | | Modify audit event types (if using enum or string constants): Add `SUBSCRIPTION_ADMIN_STATUS_CHANGE`, `SUBSCRIPTION_ADMIN_TRIAL_EXTENDED`, `SUBSCRIPTION_ADMIN_BILLING_METHOD_CHANGED`. In `AdminBillingService`, use `AuditEventBuilder` to emit events with old/new status, billing method, admin note, admin user ID. Pattern: follow existing `AuditEventBuilder` usage in `AccessRequestApprovalService.java`. |
| 428.5 | Register `/api/platform-admin/billing` in SecurityConfig | 428A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` (if needed). Ensure `/api/platform-admin/**` path is permitted for authenticated users (the `@PreAuthorize` annotation handles admin-level authorization). Check if existing config already covers this path pattern. Pattern: follow how `/api/platform-admin/access-requests` is configured. |
| 428.6 | Create `AdminBillingEndpointTest` | 428A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingEndpointTest.java`. ~12 tests: (1) GET /tenants returns list with billing info, (2) GET /tenants with status filter, (3) GET /tenants with billingMethod filter, (4) GET /tenants with search by org name, (5) GET /tenants/{orgId} returns detail, (6) PUT status override changes status and billing method, (7) PUT override rejects PENDING_CANCELLATION, (8) PUT override rejects PAYFAST without token, (9) PUT override requires adminNote, (10) POST extend-trial extends trial for TRIALING org, (11) POST extend-trial rejects non-TRIALING org, (12) non-admin user gets 403. Setup: create 3-4 orgs with different statuses/methods. Pattern: follow `BillingControllerIntegrationTest.java` setup with `@SpringBootTest` + `@AutoConfigureMockMvc`. |
| 428.7 | Create platform admin billing actions | 428B | | `frontend/app/(app)/platform-admin/billing/actions.ts`. Server actions: `listBillingTenants(status?, billingMethod?, profile?, search?)`, `getBillingTenant(orgId)`, `overrideBilling(orgId, data)`, `extendTrial(orgId, days)`. Each calls `api.get/put/post` to `/api/platform-admin/billing/tenants/*`. TypeScript interfaces: `AdminTenantBilling`, `AdminBillingOverride`. Pattern: follow `frontend/app/(app)/platform-admin/access-requests/actions.ts` exactly. |
| 428.8 | Create billing tenant list page | 428B | | `frontend/app/(app)/platform-admin/billing/page.tsx`. Server component. Calls `listBillingTenants()`. Renders table with columns: Org Name, Profile, Status (badge), Billing Method (badge), Trial/Period End, Members, Created. Filters: status dropdown, billing method dropdown, profile dropdown. Search: org name text input. Badge colors per architecture spec (ACTIVE=green, TRIALING=blue, LOCKED=red, PILOT=purple, etc.). Pattern: follow `access-requests/page.tsx` layout and structure. |
| 428.9 | Create billing status and method badge components | 428B | | `frontend/components/billing/status-badge.tsx` and `frontend/components/billing/method-badge.tsx`. Reusable badge components with color mapping per architecture spec. Uses existing `Badge` from `components/ui/badge.tsx` with appropriate variant classes. Pattern: follow existing `Badge` variant pattern in `components/ui/badge.tsx`. |
| 428.10 | Create billing detail slide-over component | 428B | | `frontend/components/billing/billing-detail-sheet.tsx`. Client component (`"use client"`). Uses Shadcn `Sheet` component. Props: tenant billing data. Sections: status + method display with badges, key dates timeline, action controls (change status dropdown, change billing method dropdown, extend trial days input, set period end date picker), admin note textarea. "Save Changes" button batches all changes into one `overrideBilling()` call. Loading state during save. Toast on success. Uses `react-hook-form` + Zod schema for form validation. Pattern: follow Shadcn Sheet pattern, `react-hook-form` conventions from CLAUDE.md. |
| 428.11 | Update platform admin layout with billing nav | 428B | | Modify `frontend/app/(app)/platform-admin/layout.tsx`. Add navigation link for "Billing" pointing to `/platform-admin/billing`. If sidebar exists, add item. If not, ensure the billing route is accessible. Pattern: follow existing layout structure. |
| 428.12 | Create frontend billing tests | 428B | | `frontend/__tests__/billing-admin.test.tsx`. ~6 tests: (1) billing list renders tenant rows with correct badges, (2) status badge shows correct color for each status, (3) method badge shows correct color for each method, (4) billing detail sheet renders with tenant data, (5) override form submits correctly, (6) extend trial input validates positive number. Pattern: follow existing test patterns with Vitest + Testing Library. Ensure `afterEach(() => cleanup())` for Sheet/Dialog components. |

### Key Files

**Slice 428A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingController.java` (new file -- the existing `AdminBillingController.java` is for internal API key endpoints; this is a new platform-admin JWT-authed controller. Name it `PlatformBillingController.java` if a naming conflict exists)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingEndpointTest.java`

**Slice 428A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` -- ensure /api/platform-admin/billing path is covered

**Slice 428A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminController.java` -- @PreAuthorize pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformSecurityService.java` -- isPlatformAdmin() method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingController.java` -- existing internal endpoints to preserve
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- audit pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- audit service interface

**Slice 428B -- Create:**
- `frontend/app/(app)/platform-admin/billing/page.tsx`
- `frontend/app/(app)/platform-admin/billing/actions.ts`
- `frontend/components/billing/status-badge.tsx`
- `frontend/components/billing/method-badge.tsx`
- `frontend/components/billing/billing-detail-sheet.tsx`
- `frontend/__tests__/billing-admin.test.tsx`

**Slice 428B -- Modify:**
- `frontend/app/(app)/platform-admin/layout.tsx` -- add billing nav

**Slice 428B -- Read for context:**
- `frontend/app/(app)/platform-admin/access-requests/page.tsx` -- admin page pattern
- `frontend/app/(app)/platform-admin/access-requests/actions.ts` -- server action pattern
- `frontend/components/ui/badge.tsx` -- badge variants
- `frontend/components/ui/sheet.tsx` -- Sheet component (if exists, otherwise use Dialog)
- `frontend/lib/api.ts` -- API client pattern

### Architecture Decisions

- **Separate controller from existing `AdminBillingController`**: The existing `AdminBillingController.java` uses API key auth for `/internal/billing/*` (service-to-service). The new platform admin controller uses JWT + `@PreAuthorize` for `/api/platform-admin/billing/*`. These are preserved as separate controllers because they use different auth mechanisms and serve different consumers. If naming conflict exists, use `PlatformBillingController.java` for the new controller.
- **Shared `AdminBillingService`**: Both the existing internal endpoints and the new platform admin endpoints delegate to the same `AdminBillingService`. This ensures consistent business logic regardless of entry point.
- **Slide-over vs. separate page**: Billing detail is a slide-over (Sheet), not a separate route. This keeps the admin in the context of the tenant list for fast switching between tenants. Per architecture Section 11.6.

---

## Epic 429: Demo Tenant Provisioning

**Goal**: Create the `DemoProvisionService` that orchestrates one-click demo tenant creation: Keycloak org + user setup, tenant schema provisioning via existing `TenantProvisioningService`, subscription override to ACTIVE/PILOT, and optional demo data seeding trigger. Extend `KeycloakAdminClient` with `findUserByEmail()` and `createUser()` methods. Create `DemoAdminController` with the provision endpoint. Build the platform admin demo creation frontend form. Includes backend integration tests.

**References**: `architecture/phase58-demo-readiness-admin-billing.md` Sections 11.3.2, 11.4.2, 11.5.1; ADR-224

**Dependencies**: Epic 427 (PILOT billing method for subscription override)

**Scope**: Backend + Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **429A** | 429.1--429.6 | `DemoProvisionService`, `DemoAdminController` (provision endpoint only), `DemoDtos` (provision request/response), `KeycloakAdminClient` extensions (`findUserByEmail`, `createUser`), `DemoProvisionServiceTest` | **Done** (PR #891) |
| **429B** | 429.7--429.11 | Platform admin demo page with creation form (org name, profile radio, admin email, seed toggle), success state with login URL, layout update with "Demo" nav, frontend tests | **Done** (PR #892) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 429.1 | Create demo DTOs | 429A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java`. Records: `DemoProvisionRequest(@NotBlank String organizationName, @NotBlank String verticalProfile, @NotBlank @Email String adminEmail, boolean seedDemoData)`, `DemoProvisionResponse(UUID organizationId, String organizationSlug, String organizationName, String verticalProfile, String loginUrl, boolean demoDataSeeded, String adminNote)`. Pattern: follow `AdminBillingDtos.java` record style. |
| 429.2 | Extend `KeycloakAdminClient` with user management methods | 429A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`. Add `Optional<String> findUserByEmail(String email)` -- searches Keycloak users by email, returns user ID if found. Add `String createUser(String email, String firstName, String lastName, String tempPassword)` -- creates Keycloak user, returns user ID. These are REST calls to Keycloak Admin REST API (`/admin/realms/{realm}/users`). Pattern: follow existing methods in `KeycloakAdminClient.java` (which already handles org creation, member management). Also check `KeycloakProvisioningClient.java` in `accessrequest/` for additional patterns. |
| 429.3 | Create `DemoProvisionService` | 429A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java`. Orchestrates: (1) Generate slug from org name via `SchemaNameGenerator`, (2) Create Keycloak org via `keycloakAdminClient.createOrganization()`, (3) Find or create Keycloak user via `findUserByEmail()` / `createUser()`, (4) Add user to org with OWNER role, (5) Call `tenantProvisioningService.provisionTenant(slug, name, verticalProfile)`, (6) Override subscription to ACTIVE/PILOT with admin note "Demo tenant created by {admin}", (7) If `seedDemoData` and DemoDataSeeder is available: call seeder (inject optionally -- seeder may not exist until Epic 430). Return `DemoProvisionResponse`. Inject: `KeycloakAdminClient`, `TenantProvisioningService`, `SubscriptionRepository`, `SchemaNameGenerator`, `OrganizationRepository`. `@Transactional` for DB operations. Pattern: follow `OrgProvisioningService.java` for provisioning orchestration. |
| 429.4 | Create `DemoAdminController` with provision endpoint | 429A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java`. Class-level `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. `@RequestMapping("/api/platform-admin/demo")`. Endpoint: `POST /provision` (body: DemoProvisionRequest, returns DemoProvisionResponse). One-liner delegating to `DemoProvisionService.provisionDemo()`. Return `ResponseEntity<DemoProvisionResponse>`. Pattern: follow `PlatformAdminController.java` and thin controller discipline. |
| 429.5 | Add audit event for demo provisioning | 429A | | In `DemoProvisionService.provisionDemo()`: emit audit event `DEMO_TENANT_PROVISIONED` with org name, vertical profile, admin email, admin user ID, org ID. Use `AuditEventBuilder`. Pattern: follow existing audit integration in `AccessRequestApprovalService.java`. |
| 429.6 | Create `DemoProvisionServiceTest` | 429A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionServiceTest.java`. ~8 tests: (1) provision creates Keycloak org + user + schema, (2) provision sets subscription to ACTIVE/PILOT, (3) provision with existing Keycloak user reuses user (no duplicate), (4) provision with seedDemoData=false skips seeding, (5) provision returns correct loginUrl, (6) provision emits audit event, (7) non-admin user gets 403 on endpoint, (8) invalid vertical profile returns 400. Integration test with Testcontainers. Mock `KeycloakAdminClient` (external service). Pattern: follow `BillingControllerIntegrationTest.java` setup. |
| 429.7 | Create platform admin demo actions | 429B | | `frontend/app/(app)/platform-admin/demo/actions.ts`. Server actions: `provisionDemo(data: DemoProvisionFormData)` -- calls `api.post("/api/platform-admin/demo/provision", data)`, returns `DemoProvisionResponse`. TypeScript interfaces: `DemoProvisionFormData`, `DemoProvisionResponse`. Pattern: follow `access-requests/actions.ts`. |
| 429.8 | Create Zod schema for demo provision form | 429B | | `frontend/lib/schemas/demo-provision.ts`. Zod schema: `organizationName` (string, min 1, max 255), `verticalProfile` (enum: "GENERIC", "ACCOUNTING", "LEGAL"), `adminEmail` (string, email), `seedDemoData` (boolean, default true). Export `DemoProvisionFormData` type. Pattern: follow existing schemas in `frontend/lib/schemas/`. |
| 429.9 | Create demo provisioning page with creation form | 429B | | `frontend/app/(app)/platform-admin/demo/page.tsx`. Client component wrapper with server component page. Form fields: Organization Name (text input, placeholder "Demo -- Accounting Firm"), Vertical Profile (radio group with descriptions: Generic -- "Marketing agency/consultancy", Accounting -- "South African accounting firm", Legal -- "South African law firm"), Admin Email (email input), Seed Demo Data (Switch/toggle, default on). "Create Demo Tenant" button with loading state. On success: show success card with org name, profile badge, login URL (copyable), and "Create Another" button. Uses `react-hook-form` + Zod resolver. Pattern: follow form patterns in CLAUDE.md, existing page structure in `access-requests/page.tsx`. |
| 429.10 | Update platform admin layout with demo nav | 429B | | Modify `frontend/app/(app)/platform-admin/layout.tsx`. Add navigation link for "Demo" pointing to `/platform-admin/demo`. Pattern: follow existing layout structure. If layout already modified in 428B, just add the demo link alongside billing. |
| 429.11 | Create frontend demo tests | 429B | | `frontend/__tests__/demo-provision.test.tsx`. ~5 tests: (1) form renders all fields, (2) submit with valid data calls action, (3) submit with invalid email shows error, (4) success state shows org details and login URL, (5) vertical profile radio selection works. Pattern: follow Vitest + Testing Library conventions. |

### Key Files

**Slice 429A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionServiceTest.java`

**Slice 429A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java` -- add findUserByEmail(), createUser()

**Slice 429A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- provisionTenant() interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgProvisioningService.java` -- provisioning orchestration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java` -- Keycloak API pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/SchemaNameGenerator.java` -- slug generation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/PlatformAdminController.java` -- @PreAuthorize pattern

**Slice 429B -- Create:**
- `frontend/app/(app)/platform-admin/demo/page.tsx`
- `frontend/app/(app)/platform-admin/demo/actions.ts`
- `frontend/lib/schemas/demo-provision.ts`
- `frontend/__tests__/demo-provision.test.tsx`

**Slice 429B -- Modify:**
- `frontend/app/(app)/platform-admin/layout.tsx` -- add demo nav

**Slice 429B -- Read for context:**
- `frontend/app/(app)/platform-admin/access-requests/page.tsx` -- admin page pattern
- `frontend/app/(app)/platform-admin/access-requests/actions.ts` -- server action pattern
- `frontend/lib/schemas/` -- existing Zod schema pattern
- `frontend/components/ui/radio-group.tsx` -- radio group component (if exists)

### Architecture Decisions

- **DemoProvisionService calls TenantProvisioningService directly**: Per ADR-224, the demo flow bypasses the access request pipeline. `TenantProvisioningService.provisionTenant()` is the single entrypoint for schema creation + Flyway migrations + pack seeding. Demo provisioning adds Keycloak user setup and subscription override as pre/post-processing.
- **Optional DemoDataSeeder injection**: `DemoProvisionService` injects the seeder optionally (`@Autowired(required = false)` or `Optional<DemoDataSeeder>`). This allows Epic 429 to be implemented before Epic 430 -- the seeder simply does nothing until it exists.
- **New `demo/` package**: Demo provisioning, seeding, and cleanup are a new domain concern. They get their own package (`demo/`) rather than being added to `billing/` or `provisioning/`. This follows the feature-per-package convention from CLAUDE.md.

---

## Epic 430: Demo Data Seeding

**Goal**: Create the demo data seeder hierarchy: `BaseDemoDataSeeder` abstract base class with shared utilities (relative date generation, SA business name pools, member creation, time entry distribution, invoice generation), `GenericDemoDataSeeder` (agency/consultancy data), `AccountingDemoDataSeeder` (SA accounting firm with SARS deadlines), `LegalDemoDataSeeder` (SA law firm, conditional on Phase 55 modules). Add reseed endpoint to `DemoAdminController`. Wire seeder into `DemoProvisionService`. Includes comprehensive data consistency integration tests.

**References**: `architecture/phase58-demo-readiness-admin-billing.md` Sections 11.3.3, 11.3.5, 11.6; ADR-225

**Dependencies**: Epic 429 (provisioning service provides tenant context for seeding)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **430A** | 430.1--430.5 | `BaseDemoDataSeeder` abstract base with utilities, `GenericDemoDataSeeder` (agency/consultancy), wire into `DemoProvisionService`, basic seeder unit test | **Done** (PR #893) |
| **430B** | 430.6--430.9 | `AccountingDemoDataSeeder` (SA accounting firm), `LegalDemoDataSeeder` (SA law firm, conditional), profile-specific unit tests | **Done** (PR #894) |
| **430C** | 430.10--430.14 | Reseed endpoint + `DemoReseedResponse` DTO, reseed service method (truncate transactional data, preserve config, re-seed), `DemoDataSeederTest` (integration -- data consistency), `DemoReseedTest` | **Done** (PR #895) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 430.1 | Create `BaseDemoDataSeeder` abstract base class | 430A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/BaseDemoDataSeeder.java`. Abstract class providing: `Instant daysAgo(int n)` / `monthsAgo(int n)` (relative to now), `String randomSaBusinessName(Random, List<String>)`, `List<UUID> createMembers(String schema, UUID orgId, int count)` (creates Member entities via MemberRepository in tenant context), `void createTimeEntries(...)` (distributes realistic hours across days/members), `void createInvoicesFromTimeEntries(...)` (generates invoices from unbilled time entries with 15% VAT, marks entries as billed), `Random seededRandom(UUID orgId)` (deterministic seed for reproducible data). Uses `TenantTransactionHelper.executeInTenantTransaction()` for tenant context operations. Inject repositories: `MemberRepository`, `CustomerRepository`, `ProjectRepository`, `TaskRepository`, `TimeEntryRepository`, `InvoiceRepository`, `InvoiceLineRepository`, `ProposalRepository`, `DocumentRepository`, `ExpenseRepository`, `CommentRepository`, `NotificationRepository`, `BillingRateRepository`, `ProjectBudgetRepository`. Abstract method: `void seedProfileData(String schemaName, UUID orgId, List<UUID> memberIds)` -- implemented by profile-specific seeders. Pattern: follow `AbstractPackSeeder.java` for base class pattern. |
| 430.2 | Create `GenericDemoDataSeeder` | 430A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/GenericDemoDataSeeder.java`. Extends `BaseDemoDataSeeder`. Implements `seedProfileData()`. Creates: Customers -- "Acme Holdings (Pty) Ltd", "Cape Digital Solutions", "Highveld Manufacturing", "Karoo Wine Estate", "Sandton Retail Group" (5 customers, mix of ACTIVE/ONBOARDING/PROSPECT). Projects -- "Website Redesign", "Q1 Strategy Review", "Brand Identity Refresh", "Social Media Campaign", "Annual Report Design" + 3-5 more (10 projects, mix of ACTIVE/COMPLETED/ON_HOLD). Tasks -- design briefs, content creation, client reviews (50 tasks). Time entries -- 200 entries across 3 months. Rates -- R850-R1,500/hr. Invoices -- 10 invoices from unbilled time entries. Proposals -- 4 proposals. Documents, Expenses, Comments, Notifications per architecture data volume table. Pattern: follow data consistency rules from architecture Section 11.6.4. |
| 430.3 | Create public `DemoDataSeeder` dispatcher | 430A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java`. Spring `@Service`. Method: `void seed(String schemaName, UUID orgId, String verticalProfile)`. Resolves profile to seeder implementation: GENERIC -> GenericDemoDataSeeder, ACCOUNTING -> AccountingDemoDataSeeder, LEGAL -> LegalDemoDataSeeder. Falls back to GenericDemoDataSeeder if profile-specific seeder not found. Pattern: follow `VerticalProfileRegistry.java` for profile-based dispatch. |
| 430.4 | Wire `DemoDataSeeder` into `DemoProvisionService` | 430A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java`. Inject `DemoDataSeeder` (now required, not optional -- Epic 430 provides it). In `provisionDemo()`, after subscription override (Step 6), if `seedDemoData == true`: call `demoDataSeeder.seed(schemaName, orgId, verticalProfile)`. Set `demoDataSeeded = true` in response. |
| 430.5 | Create basic seeder unit test | 430A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/GenericDemoDataSeederTest.java`. ~5 tests: (1) seeder creates correct number of customers (5), (2) seeder creates correct number of projects (8-12), (3) all time entries reference valid tasks, (4) relative dates are within expected range (today to 3 months ago), (5) deterministic seed produces reproducible data. These can be integration tests with Testcontainers for DB. Pattern: follow `SubscriptionEntityTest.java` for persistence test setup. |
| 430.6 | Create `AccountingDemoDataSeeder` | 430B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/AccountingDemoDataSeeder.java`. Extends `BaseDemoDataSeeder`. SA accounting firm data: Customers -- "Van der Merwe & Associates", "Protea Trading (Pty) Ltd", "Karoo Investments", "Disa Financial Services", "Berg & Berg Attorneys". Projects -- "2025 Annual Financials -- Protea Trading", "VAT Registration -- Disa Financial", "BBBEE Audit -- Van der Merwe", "Monthly Bookkeeping -- Karoo Investments", "SARS ITR14 -- Berg & Berg" + recurring monthly engagements. Tasks -- tax return prep, financial statement review, SARS submission, bank reconciliation. Rates -- R650-R1,200/hr. SARS deadlines seeded via deadline type infrastructure if available. Compliance checklists via checklist infrastructure if available. Pattern: same structure as `GenericDemoDataSeeder`. |
| 430.7 | Create `LegalDemoDataSeeder` | 430B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/LegalDemoDataSeeder.java`. Extends `BaseDemoDataSeeder`. SA law firm data: Customers -- "Dlamini Property Trust", "Naidoo & Partners Developers", "Botha Family Estate", "Msimang Transport (Pty) Ltd", "Cele Holdings". Projects -- "Dlamini -- Property Transfer DE-2026-001", "Naidoo -- Commercial Lease Review", "Botha -- Estate Administration", "Msimang -- Labour Dispute". Tasks -- due diligence, contract drafting, FICA verification, settlement. Rates -- R1,200-R3,500/hr. Legal-specific entities (court dates, adverse parties, tariff items) only seeded if Phase 55 modules exist: check `VerticalModuleRegistry.getModule("court_calendar").isPresent()`. If not present, skip legal-specific entities gracefully. Pattern: same structure as `GenericDemoDataSeeder`. |
| 430.8 | Register profile seeders in `DemoDataSeeder` dispatcher | 430B | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java`. Register `AccountingDemoDataSeeder` and `LegalDemoDataSeeder` in the profile dispatch map. Inject all three seeders. |
| 430.9 | Create profile-specific seeder tests | 430B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/AccountingDemoDataSeederTest.java` (~3 tests: correct customer names, accounting-specific projects, rates in R650-R1,200 range). `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/LegalDemoDataSeederTest.java` (~3 tests: correct customer names, legal-specific projects, rates in R1,200-R3,500 range, graceful skip if legal modules absent). Pattern: follow `GenericDemoDataSeederTest.java`. |
| 430.10 | Add `DemoReseedResponse` DTO | 430C | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java`. Add `DemoReseedResponse(UUID organizationId, String organizationName, boolean success, String verticalProfile, String error)`. |
| 430.11 | Create reseed service method | 430C | | Add to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java` (or create separate `DemoReseedService.java`). Method: `DemoReseedResponse reseed(UUID orgId)`. Steps: (1) validate `billingMethod.isCleanupEligible()`, (2) TRUNCATE transactional tables within tenant schema: notifications, comments, expenses, invoice_lines, invoices, proposals, documents, time_entries, task_items, tasks, project_members, customer_projects, projects, customers (use CASCADE or ordered deletes). Preserve config: org_settings, field_definitions, templates, compliance packs, billing rates, report definitions. (3) Re-run `demoDataSeeder.seed(schema, orgId, profile)`. (4) Return DemoReseedResponse. Use `TenantTransactionHelper` for tenant context. Pattern: follow existing transaction patterns. |
| 430.12 | Add reseed endpoint to `DemoAdminController` | 430C | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java`. Add: `POST /{orgId}/reseed` (path variable: orgId, returns DemoReseedResponse). One-liner delegating to service. Pattern: thin controller discipline. |
| 430.13 | Create `DemoDataSeederTest` (integration -- data consistency) | 430C | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java`. ~8 tests: (1) invoice line items match actual time entries (FK valid, amounts correct), (2) invoice totals = sum of line items + 15% VAT, (3) budget utilization matches actual time entry hours, (4) all time entries reference valid tasks on valid projects, (5) task assignees are project members, (6) chronological ordering (customer created before project, project before tasks, etc.), (7) one project is intentionally over-budget (~120% utilization), (8) dispatcher routes to correct profile seeder. Full integration test with provisioned tenant schema. Pattern: follow `SubscriptionIntegrationTest.java` for integration setup with Testcontainers. |
| 430.14 | Create `DemoReseedTest` | 430C | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoReseedTest.java`. ~5 tests: (1) reseed clears all transactional data (customers, projects, tasks, time entries, invoices = 0), (2) reseed preserves configuration data (org_settings, field_definitions, templates), (3) reseed re-populates transactional data (counts match expected), (4) reseed rejected for PAYFAST billing method, (5) reseed rejected for MANUAL billing method. Integration test. Pattern: follow seeder test setup. |

### Key Files

**Slice 430A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/BaseDemoDataSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/GenericDemoDataSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/GenericDemoDataSeederTest.java`

**Slice 430A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java` -- wire seeder

**Slice 430A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` -- base seeder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/` -- Customer entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/` -- Project entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/` -- Task entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/` -- TimeEntry entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/` -- Invoice + InvoiceLine entities
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/` -- TenantTransactionHelper or similar

**Slice 430B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/AccountingDemoDataSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/LegalDemoDataSeeder.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/AccountingDemoDataSeederTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/LegalDemoDataSeederTest.java`

**Slice 430B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java` -- register new seeders

**Slice 430B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/GenericDemoDataSeeder.java` -- reference for profile-specific seeders
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` -- module existence check for legal
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/` -- deadline type entities (for accounting deadlines)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/` -- compliance entities (for FICA checklists)

**Slice 430C -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoReseedTest.java`

**Slice 430C -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java` -- add DemoReseedResponse
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java` -- add reseed method (or create DemoReseedService)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java` -- add reseed endpoint

### Architecture Decisions

- **Three-slice decomposition**: Demo data seeding is the largest coding effort in Phase 58 (~1,000 lines across seeders). Splitting into base+generic (430A), profile-specific (430B), and reseed+integration tests (430C) keeps each slice under 800 new lines. 430A establishes the framework, 430B fills in profile data, 430C adds the reseed capability and comprehensive testing.
- **Service-based seeding through entity layer**: Per ADR-225, seeders create records using existing repositories and entity constructors. This ensures schema resilience (new NOT NULL columns surface as compile errors), validation compliance, and date freshness (all dates relative to today).
- **Deterministic random with org-based seed**: `seededRandom(UUID orgId)` ensures the same demo tenant produces the same data if reseeded. This aids debugging and demo reproducibility.
- **Graceful legal module detection**: `LegalDemoDataSeeder` checks `VerticalModuleRegistry.getModule("court_calendar").isPresent()` before seeding legal-specific entities. If Phase 55 modules are not present, legal seeders still create standard entities (customers, projects, tasks) with legal-themed names.

---

## Epic 431: Demo Tenant Cleanup

**Goal**: Create the `DemoCleanupService` with ordered multi-step destruction (audit -> Keycloak -> schema drop -> public record cleanup -> cache eviction -> S3 cleanup), safety validation (billing method must be PILOT or COMPLIMENTARY, exact org name confirmation), and per-step error reporting. Add cleanup endpoint to `DemoAdminController`. Build frontend: demo tenant list with PILOT/COMPLIMENTARY filter, delete confirmation dialog with exact name match, reseed button with loading state, and billing page adaptation for admin-managed tenants. Includes backend integration tests and frontend component tests.

**References**: `architecture/phase58-demo-readiness-admin-billing.md` Sections 11.3.4, 11.5.3, 11.8; ADR-226

**Dependencies**: Epic 427 (BillingMethod.isCleanupEligible() for safety validation)

**Scope**: Backend + Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **431A** | 431.1--431.6 | `DemoCleanupService` (multi-step cleanup with error handling), `DemoCleanupRequest`/`DemoCleanupResponse` DTOs, cleanup endpoint on `DemoAdminController`, `DemoCleanupServiceTest` | **Done** (PR #896) |
| **431B** | 431.7--431.12 | Demo tenant list (filtered PILOT/COMPLIMENTARY view), delete confirmation dialog (org name match), reseed button, billing page adaptation (hide PayFast UI for `adminManaged`), frontend tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 431.1 | Add cleanup DTOs | 431A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java`. Add `DemoCleanupRequest(@NotBlank String confirmOrganizationName)`, `DemoCleanupResponse(UUID organizationId, String organizationName, boolean keycloakCleaned, boolean schemaCleaned, boolean publicRecordsCleaned, boolean s3Cleaned, List<String> errors)`. |
| 431.2 | Create `DemoCleanupService` | 431A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoCleanupService.java`. Method: `DemoCleanupResponse cleanup(UUID orgId, String confirmName)`. Steps: (1) Load org + subscription -- validate `billingMethod.isCleanupEligible()` (throw `ForbiddenException` if not), validate `confirmName` matches org name (case-sensitive, throw `InvalidStateException` if mismatch). (2) Audit: log `DEMO_TENANT_DELETED` via `AuditService` BEFORE any destruction. (3) Keycloak: via `KeycloakAdminClient` -- list org members, for each: check if member belongs to other orgs (if only this org: delete user, else: remove from org), delete org. (4) Schema: execute `DROP SCHEMA IF EXISTS {schema} CASCADE` via `JdbcTemplate` on migration datasource. (5) Public records: delete from `subscription_payments`, `subscriptions`, `org_schema_mapping`, `members`, `organizations` WHERE org_id matches. (6) Cache: evict `SubscriptionStatusCache`. (7) S3 (best-effort): delete `{tenant-id}/` prefix via S3 service. Each step wrapped in try-catch. Track success per step. Log errors but continue. Return `DemoCleanupResponse` with per-step status. Inject: `OrganizationRepository`, `SubscriptionRepository`, `SubscriptionPaymentRepository`, `KeycloakAdminClient`, `JdbcTemplate`, `SubscriptionStatusCache`, `AuditService`, S3 service. Pattern: follow architecture Section 11.3.4 step ordering. |
| 431.3 | Add cleanup endpoint to `DemoAdminController` | 431A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java`. Add: `DELETE /{orgId}` (path variable: orgId, body: DemoCleanupRequest, returns DemoCleanupResponse). One-liner: `demoCleanupService.cleanup(orgId, request.confirmOrganizationName())`. Pattern: thin controller discipline. |
| 431.4 | Add Keycloak org membership check methods | 431A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`. Add: `List<String> listOrgMemberIds(String kcOrgId)` -- returns user IDs in this org. `List<String> getUserOrganizations(String userId)` -- returns org IDs this user belongs to. `void removeOrgMember(String kcOrgId, String userId)` -- removes user from org. `void deleteUser(String userId)` -- deletes Keycloak user. `void deleteOrganization(String kcOrgId)` -- deletes Keycloak org. These are REST calls to Keycloak Admin REST API. Pattern: follow existing methods in `KeycloakAdminClient.java`. |
| 431.5 | Add public schema cleanup queries | 431A | | In `DemoCleanupService`: use `JdbcTemplate` or repositories for ordered deletion from public schema: `DELETE FROM subscription_payments WHERE subscription_id IN (SELECT id FROM subscriptions WHERE organization_id = ?)`, `DELETE FROM subscriptions WHERE organization_id = ?`, `DELETE FROM org_schema_mapping WHERE external_org_id = ?`, `DELETE FROM members WHERE organization_id = ?` (if public member table exists), `DELETE FROM organizations WHERE id = ?`. Use `@Qualifier("migrationDataSource")` JdbcTemplate for DDL operations (schema drop). Pattern: follow existing DB operation patterns. |
| 431.6 | Create `DemoCleanupServiceTest` | 431A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoCleanupServiceTest.java`. ~8 tests: (1) cleanup succeeds for PILOT tenant, (2) cleanup succeeds for COMPLIMENTARY tenant, (3) cleanup rejects PAYFAST tenant (ForbiddenException), (4) cleanup rejects MANUAL tenant, (5) cleanup rejects DEBIT_ORDER tenant, (6) wrong confirmName throws InvalidStateException, (7) partial failure (Keycloak fails) continues with remaining steps and reports errors, (8) audit event emitted before destruction. Mock `KeycloakAdminClient`. Integration test for DB operations. Pattern: follow `BillingControllerIntegrationTest.java`. |
| 431.7 | Create demo tenant list and cleanup actions | 431B | | `frontend/app/(app)/platform-admin/demo/actions.ts` (extend existing from 429B). Add: `listDemoTenants()` -- calls `api.get("/api/platform-admin/billing/tenants?billingMethod=PILOT")` or similar filtered endpoint, `deleteDemoTenant(orgId, confirmName)` -- calls `api.delete("/api/platform-admin/demo/{orgId}", { confirmOrganizationName })`, `reseedDemoTenant(orgId)` -- calls `api.post("/api/platform-admin/demo/{orgId}/reseed")`. TypeScript interfaces: `DemoCleanupResponse`, `DemoReseedResponse`. Pattern: follow existing actions pattern. |
| 431.8 | Create demo tenant list component | 431B | | Add to `frontend/app/(app)/platform-admin/demo/page.tsx` (or separate list component). Below the creation form, add a "Demo Tenants" section. Table: Org Name, Profile (badge), Status (badge), Created, Members. Row actions: "Reseed Data" button, "Delete Tenant" button (destructive variant). Filter: shows only PILOT and COMPLIMENTARY tenants. Pattern: follow access-requests table pattern. |
| 431.9 | Create delete confirmation dialog | 431B | | `frontend/components/billing/delete-tenant-dialog.tsx`. Client component (`"use client"`). Props: tenant data (name, profile, memberCount, createdAt), onConfirm callback. Uses Shadcn `AlertDialog`. Content: org name, profile badge, member count, creation date, warning text ("This will permanently delete the organization, all its data, the database schema, and Keycloak resources. This action cannot be undone."), text input ("Type the organization name to confirm"), "Delete Tenant" button (disabled until exact name match). On confirm: call `deleteDemoTenant()`, show loading, show success/failure toast. Pattern: follow existing AlertDialog patterns, ensure `afterEach(() => cleanup())` in tests. |
| 431.10 | Adapt billing page for admin-managed tenants | 431B | | Modify `frontend/app/(app)/org/[slug]/settings/billing/page.tsx`. Read `billingMethod` and `adminManaged` from `BillingResponse`. When `adminManaged === true`: hide PayFast subscribe CTA, hide cancel button, hide payment history. Show billing method badge (e.g., "Pilot Partner" in purple). Show message: "Your account is managed by your administrator." For GRACE_PERIOD: "Contact your administrator to restore access." For LOCKED: full-page "Contact your administrator" with support info. Modify `frontend/lib/internal-api.ts`: add `billingMethod`, `adminManaged`, `adminNote` fields to `BillingResponse` interface. Pattern: follow existing conditional rendering in billing page. |
| 431.11 | Create reseed button with loading state | 431B | | In demo tenant list row actions: "Reseed Data" button calls `reseedDemoTenant(orgId)`. Shows loading spinner during operation. On success: toast "Demo data reseeded successfully." On failure: toast with error message. Use existing button loading state pattern. |
| 431.12 | Create frontend cleanup and adaptation tests | 431B | | `frontend/__tests__/demo-cleanup.test.tsx`. ~6 tests: (1) delete dialog shows warning and org details, (2) delete button disabled until exact name typed, (3) delete button enabled when exact name matches, (4) billing page shows PayFast UI when adminManaged=false, (5) billing page hides PayFast UI when adminManaged=true, (6) billing page shows "managed by administrator" for PILOT. Pattern: follow Vitest + Testing Library conventions. |

### Key Files

**Slice 431A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoCleanupService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoCleanupServiceTest.java`

**Slice 431A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoDtos.java` -- add cleanup request/response
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoAdminController.java` -- add delete endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java` -- add cleanup methods

**Slice 431A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- schema creation pattern (reverse for drop)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/` -- S3 service for cleanup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/` -- schema naming, org_schema_mapping
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/` -- DataSource configuration for migration JdbcTemplate

**Slice 431B -- Create:**
- `frontend/components/billing/delete-tenant-dialog.tsx`
- `frontend/__tests__/demo-cleanup.test.tsx`

**Slice 431B -- Modify:**
- `frontend/app/(app)/platform-admin/demo/page.tsx` -- add demo tenant list section
- `frontend/app/(app)/platform-admin/demo/actions.ts` -- add list, delete, reseed actions
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` -- adapt for adminManaged
- `frontend/lib/internal-api.ts` -- add billingMethod, adminManaged, adminNote to BillingResponse

**Slice 431B -- Read for context:**
- `frontend/components/ui/alert-dialog.tsx` -- AlertDialog pattern
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` -- existing billing page structure
- `frontend/app/(app)/org/[slug]/settings/billing/actions.ts` -- existing billing actions
- `frontend/components/billing/subscribe-button.tsx` -- PayFast subscribe UI to conditionally hide

### Architecture Decisions

- **Billing method as safety classifier**: Per ADR-226, cleanup is restricted to `PILOT` and `COMPLIMENTARY` billing methods. This is enforced at the service layer via `BillingMethod.isCleanupEligible()`. No additional `is_demo` flag is needed.
- **Best-effort cleanup with per-step reporting**: Each cleanup step is wrapped in try-catch. If Keycloak fails, schema drop still proceeds. The response reports which steps succeeded and which failed, allowing the admin to diagnose and retry.
- **Audit before destruction**: The audit event is logged BEFORE any destructive operation. Even if cleanup partially fails, there is a record of the attempt, who initiated it, and when.
- **Billing page adaptation in cleanup epic**: The billing page frontend change (showing "managed by administrator" for non-PayFast tenants) is placed in Epic 431B rather than 428B because it depends on the full `BillingResponse` DTO changes from 427 and is thematically linked to the admin-managed tenant experience. This keeps the admin billing frontend (428B) focused on the admin panel.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionStatusCache.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/platform-admin/access-requests/actions.ts`