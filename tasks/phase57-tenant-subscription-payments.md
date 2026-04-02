# Phase 57 -- Tenant Subscription Payments

Phase 57 replaces HeyKazi's simulated billing system with real subscription payments via PayFast recurring billing. The dual-tier model (STARTER/PRO) is eliminated in favor of a single-plan lifecycle: every organization starts with a configurable trial, subscribes via PayFast hosted checkout, and loses write access if they stop paying. The phase covers backend lifecycle management, PayFast integration, read-only enforcement, frontend billing UI, and removal of all dead tier code.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 419 | Data Model & Config Foundation | Backend | -- | M | 419A, 419B | |
| 420 | Subscription Lifecycle Service & Billing API | Backend | 419 | L | 420A, 420B | |
| 421 | PayFast Platform Integration | Backend | 419, 420 | M | 421A, 421B | |
| 422 | Read-Only Enforcement & Scheduled Jobs | Backend | 419, 420 | M | 422A, 422B | |
| 423 | Frontend Billing Page & Components | Frontend | 420 | M | 423A, 423B | |
| 424 | Frontend Banner, Context & Error Interceptor | Frontend | 423 | M | 424A, 424B | |
| 425 | Backend Cleanup -- Dead Tier Code | Backend | 419-422 | M | 425A, 425B | |
| 426 | Frontend Cleanup -- Dead Tier Components & Test Refs | Frontend | 423, 424 | S | 426A | |

## Dependency Graph

```
[E419 Data Model] ──────────────────────────────────────────────────────────────┐
    │                                                                           │
    ├──► [E420 Lifecycle Service + API] ──────────────────┬──────────────────────┤
    │         │                                           │                     │
    │         ├──► [E421 PayFast Integration]              │                     │
    │         │                                           │                     │
    │         ├──► [E422 Guard Filter + Jobs]              │                     │
    │         │                                           │                     │
    │         └──► [E423 Frontend Billing Page] ──► [E424 Banner + Context]      │
    │                                                      │                    │
    │                                                      ▼                    │
    │                                              [E426 FE Cleanup]            │
    │                                                                           │
    └──────────────────────────────────────────────► [E425 BE Cleanup] ◄─────────┘
```

**Parallel tracks**: After Epic 420 (Lifecycle Service + API) lands, Epics 421 (PayFast), 422 (Guard + Jobs), and 423 (Frontend Billing Page) can all begin in parallel -- they have zero dependency on each other. Epic 424 depends on 423. Epics 425 and 426 (cleanup) run last, after all other epics are complete.

## Implementation Order

### Stage 1: Backend Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 419: Data Model & Config Foundation | Migration + entities + config properties are the prerequisite for all other Phase 57 work. |

### Stage 2: Lifecycle Service

| Order | Epic | Rationale |
|-------|------|-----------|
| 2 | Epic 420: Subscription Lifecycle Service & Billing API | Service layer + API endpoints build on top of the data model. All other epics depend on the subscription lifecycle being operational. |

### Stage 3: PayFast + Guard + Frontend Billing (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 421: PayFast Platform Integration | Depends on entity model (419) and lifecycle service (420) for state transitions. Independent of 422 and 423. |
| 3b | Epic 422: Read-Only Enforcement & Scheduled Jobs | Depends on entity model (419) and lifecycle service (420) for status resolution. Independent of 421 and 423. |
| 3c | Epic 423: Frontend Billing Page & Components | Depends on API endpoints from Epic 420. Independent of 421 and 422. |

### Stage 4: Frontend Banner + Context (After Billing Page)

| Order | Epic | Rationale |
|-------|------|-----------|
| 4 | Epic 424: Frontend Banner, Context & Error Interceptor | Depends on billing page components from Epic 423. Adds cross-cutting frontend infrastructure. |

### Stage 5: Cleanup (After All Feature Epics)

| Order | Epic | Rationale |
|-------|------|-----------|
| 5a | Epic 425: Backend Cleanup -- Dead Tier Code | Removes `Tier`, `PlanLimits`, `PlanSyncService`, updates 50+ test files. Must run after all backend epics (419-422) are stable. |
| 5b | Epic 426: Frontend Cleanup -- Dead Tier Components | Removes `plan-badge`, `upgrade-*` components and frontend tier references. Must run after 423 and 424 are stable. |

### Timeline

```
Stage 1:  [E419]                                   <- foundation (must complete first)
Stage 2:  [E420]                                   <- lifecycle service (must complete second)
Stage 3:  [E421] [E422] [E423]                     <- parallel (after E420)
Stage 4:  [E424]                                   <- after E423
Stage 5:  [E425] [E426]                            <- parallel cleanup (after all feature epics)
```

---

## Epic 419: Data Model & Config Foundation

**Goal**: Create the V17 global migration that restructures the `subscriptions` table, creates the `subscription_payments` table, migrates existing data, and drops dead columns. Add the `SubscriptionStatus` enum (8 states), restructured `Subscription` entity, new `SubscriptionPayment` entity, `SubscriptionPaymentRepository`, `BillingProperties` and `PayFastBillingProperties` config records, and application config. Includes entity persistence tests and config binding verification.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 2, 3, 4.1, 9; ADR-219

**Dependencies**: None (foundation epic)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **419A** | 419.1--419.5 | V17 global migration, `SubscriptionStatus` enum, restructured `Subscription` entity, `SubscriptionPayment` entity + repository, migration integration test | |
| **419B** | 419.6--419.10 | `BillingProperties` + `PayFastBillingProperties` config records, application config additions, updated `SubscriptionRepository` queries, config binding test, entity persistence test | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 419.1 | Create V17 global migration for subscription restructuring | 419A | | `backend/src/main/resources/db/migration/global/V17__subscription_payments.sql`. Add columns to `subscriptions`: `subscription_status VARCHAR(30)`, `payfast_token VARCHAR(255)`, `trial_ends_at TIMESTAMPTZ`, `grace_ends_at TIMESTAMPTZ`, `monthly_amount_cents INTEGER`, `currency VARCHAR(3) DEFAULT 'ZAR'`, `last_payment_at TIMESTAMPTZ`, `next_billing_at TIMESTAMPTZ`, `payfast_payment_id VARCHAR(255)`. Migrate existing data: `plan_slug = 'pro'` -> `subscription_status = 'ACTIVE'`; `plan_slug = 'starter'` or NULL -> `subscription_status = 'TRIALING'`, `trial_ends_at = now() + 14 days`. Set NOT NULL on `subscription_status`. Drop `plan_slug` and `status` columns. Drop `tier` and `plan_slug` from `organizations`. Create `subscription_payments` table (UUID PK, subscription_id FK, payfast_payment_id, amount_cents, currency, status, payment_date, raw_itn JSONB, created_at). Create indexes: `idx_sub_payments_subscription`, `idx_sub_payments_payfast_id`, `idx_subscriptions_status`. Use `IF NOT EXISTS` / `IF EXISTS` for idempotency. Pattern: follow `V16__enable_pg_trgm.sql` structure. |
| 419.2 | Create `SubscriptionStatus` enum in `Subscription.java` | 419A | | Replace existing 2-state `SubscriptionStatus` enum inside `billing/Subscription.java` with 8-state enum: `TRIALING`, `ACTIVE`, `PENDING_CANCELLATION`, `PAST_DUE`, `SUSPENDED`, `GRACE_PERIOD`, `EXPIRED`, `LOCKED`. Move to standalone file or keep nested -- follow existing `PaymentStatus` enum pattern. |
| 419.3 | Restructure `Subscription` entity | 419A | | Modify `billing/Subscription.java`. Remove `planSlug` and `status` fields. Add new fields per architecture Section 3.1: `subscriptionStatus` (`@Enumerated(EnumType.STRING)`), `payfastToken`, `trialEndsAt`, `graceEndsAt`, `monthlyAmountCents`, `currency`, `lastPaymentAt`, `nextBillingAt`, `payfastPaymentId`. Keep existing fields: `id`, `organizationId`, `currentPeriodStart`, `currentPeriodEnd`, `cancelledAt`, `createdAt`, `updatedAt`. Add `transitionTo(SubscriptionStatus)` method with valid transition enforcement. Update constructor to accept `organizationId` and set `subscriptionStatus = TRIALING`, `trialEndsAt`. Remove `changePlan()` method. Pattern: follow existing entity structure (no Lombok, explicit getters). |
| 419.4 | Create `SubscriptionPayment` entity and `PaymentStatus` enum | 419A | | `billing/SubscriptionPayment.java`. JPA entity mapped to `subscription_payments` table in `public` schema. Fields: UUID `id` (PK, `@GeneratedValue(strategy = GenerationType.UUID)`), UUID `subscriptionId`, String `payfastPaymentId`, int `amountCents`, String `currency`, `PaymentStatus status` (`@Enumerated(EnumType.STRING)`) with values COMPLETE/FAILED/REFUNDED, Instant `paymentDate`, `Map<String, String> rawItn` (`@JdbcTypeCode(SqlTypes.JSON)`, `columnDefinition = "jsonb"`), Instant `createdAt`. Protected no-arg constructor for JPA. Pattern: follow `billing/Subscription.java` entity structure. |
| 419.5 | Create `SubscriptionPaymentRepository` | 419A | | `billing/SubscriptionPaymentRepository.java`. Extends `JpaRepository<SubscriptionPayment, UUID>`. Methods: `List<SubscriptionPayment> findBySubscriptionIdOrderByPaymentDateDesc(UUID subscriptionId)`, `boolean existsByPayfastPaymentId(String payfastPaymentId)` (idempotency check), `Page<SubscriptionPayment> findBySubscriptionId(UUID subscriptionId, Pageable pageable)`. Pattern: follow `billing/SubscriptionRepository.java`. |
| 419.6 | Create `BillingProperties` config record | 419B | | `billing/BillingProperties.java`. `@ConfigurationProperties(prefix = "heykazi.billing")`. Record with fields: `int monthlyPriceCents` (default 49900), `int trialDays` (default 14), `int gracePeriodDays` (default 60), `String currency` (default "ZAR"), `String itemName` (default "HeyKazi Professional"), `String notifyUrl`, `String returnUrl`, `String cancelUrl`, `int maxMembers` (default 10). Add `@EnableConfigurationProperties(BillingProperties.class)` to a config class or the main application. Pattern: follow existing `AuditRetentionProperties.java` as reference for `@ConfigurationProperties` records. |
| 419.7 | Create `PayFastBillingProperties` config record | 419B | | `billing/payfast/PayFastBillingProperties.java`. `@ConfigurationProperties(prefix = "heykazi.billing.payfast")`. Record with fields: `String merchantId`, `String merchantKey`, `String passphrase`, `boolean sandbox` (default true). Enable via `@EnableConfigurationProperties`. Pattern: follow `BillingProperties.java` from task 419.6. |
| 419.8 | Add application config for billing properties | 419B | | Add `heykazi.billing.*` properties to `application.yml` and `application-local.yml`. Include PayFast sandbox config with placeholder environment variable references (`${PAYFAST_MERCHANT_ID}`, etc.). Set defaults for `monthly-price-cents: 49900`, `trial-days: 14`, `grace-period-days: 60`, `currency: ZAR`, `item-name: "HeyKazi Professional"`, `max-members: 10`. Add `notify-url`, `return-url`, `cancel-url` with `${HEYKAZI_BASE_URL}` and `${HEYKAZI_FRONTEND_URL}` placeholders. For `application-test.yml`, set `sandbox: true` and dummy merchant values. |
| 419.9 | Add scheduled job queries to `SubscriptionRepository` | 419B | | Modify `billing/SubscriptionRepository.java`. Add methods: `List<Subscription> findBySubscriptionStatusAndTrialEndsAtBefore(SubscriptionStatus status, Instant cutoff)`, `List<Subscription> findBySubscriptionStatusInAndGraceEndsAtBefore(List<SubscriptionStatus> statuses, Instant cutoff)`, `List<Subscription> findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus status, Instant cutoff)`, `Optional<Subscription> findByOrganizationId(UUID organizationId)`. These are Spring Data derived queries -- no JPQL needed. Pattern: existing `SubscriptionRepository.java`. |
| 419.10 | Add entity persistence tests and config binding tests | 419B | | `billing/SubscriptionEntityTest.java` (~8 tests): persist and retrieve `Subscription` with new fields, persist `SubscriptionPayment`, verify `rawItn` JSONB round-trip, verify `findByOrganizationId`, verify scheduled job queries (setup subscriptions in various states with past timestamps, assert queries return correct rows). `billing/BillingPropertiesTest.java` (~3 tests): verify `BillingProperties` binds from test config, verify `PayFastBillingProperties` binds, verify default values. Pattern: follow `SubscriptionIntegrationTest.java` test setup. |

### Key Files

**Slice 419A -- Create:**
- `backend/src/main/resources/db/migration/global/V17__subscription_payments.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionPayment.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionPaymentRepository.java`

**Slice 419A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java` -- restructure entity, replace enum

**Slice 419A -- Read for context:**
- `backend/src/main/resources/db/migration/global/V16__enable_pg_trgm.sql` -- migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionRepository.java` -- repository pattern

**Slice 419B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingProperties.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PayFastBillingProperties.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionEntityTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingPropertiesTest.java`

**Slice 419B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionRepository.java` -- add scheduled job queries
- `backend/src/main/resources/application.yml` -- billing config
- `backend/src/main/resources/application-local.yml` -- local billing config
- `backend/src/test/resources/application-test.yml` -- test billing config

**Slice 419B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditRetentionProperties.java` -- `@ConfigurationProperties` pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionIntegrationTest.java` -- test setup pattern

### Architecture Decisions

- **V17 migration**: Global migration (public schema) because subscriptions and subscription_payments are global entities, not tenant-scoped. Follows the existing `global/` migration path.
- **Data migration strategy**: Existing `plan_slug = 'pro'` becomes ACTIVE (they were conceptually paying); `plan_slug = 'starter'` becomes TRIALING with a 14-day window. This gives existing free tenants a trial period rather than immediately locking them.
- **SubscriptionStatus as nested enum vs standalone**: Keep nested in `Subscription.java` if the existing `SubscriptionStatus` was nested; otherwise follow the codebase convention. The 8-state enum replaces both the 2-state `SubscriptionStatus` and the `Tier` enum.
- **Two-slice decomposition**: 419A covers the migration and entity changes (the foundation files). 419B covers config, queries, and tests (the configuration layer). This keeps each slice under 8 files created.
- **BillingProperties vs SubscriptionLimits**: Per architecture Section 2.4, no separate `SubscriptionLimits` class -- `BillingProperties.maxMembers()` replaces `PlanLimits.maxMembers(Tier)` directly.

---

## Epic 420: Subscription Lifecycle Service & Billing API

**Goal**: Rewrite `SubscriptionService` with lifecycle methods (subscribe, cancel, getSubscription, getPayments), rewrite `BillingController` with new endpoints, create `BillingResponse` and `SubscribeResponse` DTOs, update `TenantProvisioningService` to create TRIALING subscriptions, and add internal admin endpoints for trial extension and manual activation. Includes integration tests for all billing API endpoints.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 5, 7; ADR-219

**Dependencies**: Epic 419 (entity model, config properties)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **420A** | 420.1--420.5 | Rewritten `SubscriptionService` with lifecycle methods, `BillingResponse` + `SubscribeResponse` DTOs, rewritten `BillingController` with subscribe/cancel/payments/status endpoints, updated `TenantProvisioningService` | |
| **420B** | 420.6--420.10 | Internal admin endpoints (extend-trial, activate), `AdminBillingController` refactor, `BillingControllerIntegrationTest`, `SubscriptionLifecycleTest` | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 420.1 | Rewrite `SubscriptionService` with lifecycle methods | 420A | | Modify `billing/SubscriptionService.java`. Remove `upgradePlan()`, `changePlan()`, all `PlanSyncService` and `PlanLimits` references. Add methods: `getSubscription(String orgId)` returns `BillingResponse` (resolves org by externalOrgId, loads subscription, computes `canSubscribe`/`canCancel`, counts members via `MemberRepository`), `initiateSubscribe(String orgId)` validates status allows subscription (TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, LOCKED) and returns placeholder for PayFast form data (full PayFast integration in Epic 421), `cancelSubscription(String orgId)` transitions to PENDING_CANCELLATION and returns updated `BillingResponse`, `getPayments(String orgId, Pageable pageable)` returns paginated `SubscriptionPayment` list. Use `BillingProperties` for price and limit values. All `@Transactional`. Pattern: follow existing `SubscriptionService.java` thin-controller delegation. |
| 420.2 | Create `BillingResponse` and `SubscribeResponse` DTOs | 420A | | `billing/BillingResponse.java`: record with `String status`, `Instant trialEndsAt`, `Instant currentPeriodEnd`, `Instant graceEndsAt`, `Instant nextBillingAt`, `int monthlyAmountCents`, `String currency`, `LimitsResponse limits`, `boolean canSubscribe`, `boolean canCancel`. Nested `LimitsResponse(int maxMembers, long currentMembers)`. Static factory `from(Subscription, long memberCount, BillingProperties)`. `billing/SubscribeResponse.java`: record with `String paymentUrl`, `Map<String, String> formFields`. Pattern: follow existing DTO records in controller files. |
| 420.3 | Rewrite `BillingController` with new endpoints | 420A | | Modify `billing/BillingController.java`. Remove `upgrade()` endpoint and `UpgradeRequest` DTO. Add: `GET /api/billing/subscription` (any member, returns `BillingResponse`), `POST /api/billing/subscribe` (`@RequiresCapability("INVOICING")` -- OWNER only, returns `SubscribeResponse`), `POST /api/billing/cancel` (`@RequiresCapability("INVOICING")` -- OWNER only, returns `BillingResponse`), `GET /api/billing/payments` (`@RequiresCapability("MANAGE_TEAM")` -- OWNER/ADMIN, returns `Page<PaymentResponse>`). Each method is a one-liner delegating to `SubscriptionService`. Create `PaymentResponse` record (id, payfastPaymentId, amountCents, currency, status, paymentDate). Pattern: follow existing `BillingController.java` thin-controller discipline. |
| 420.4 | Update `TenantProvisioningService` for TRIALING subscriptions | 420A | | Modify `provisioning/TenantProvisioningService.java`. Change subscription creation from `new Subscription(orgId, "starter")` to `new Subscription(orgId)` which sets `subscriptionStatus = TRIALING` and `trialEndsAt = Instant.now().plus(Duration.ofDays(billingProperties.trialDays()))`. Inject `BillingProperties` into `TenantProvisioningService`. Remove any `PlanSyncService` calls. |
| 420.5 | Add `canSubscribe` and `canCancel` logic to `SubscriptionService` | 420A | | In `SubscriptionService`: `canSubscribe` is true when status is TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, or LOCKED. `canCancel` is true when status is ACTIVE. `isWriteEnabled` is true for TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE. These are computed in the `BillingResponse.from()` factory. |
| 420.6 | Refactor `AdminBillingController` for internal endpoints | 420B | | Modify `billing/AdminBillingController.java`. Add: `POST /internal/billing/extend-trial` (accepts `ExtendTrialRequest(UUID organizationId, int additionalDays)`, extends `trialEndsAt` for TRIALING subscriptions), `POST /internal/billing/activate` (accepts `ActivateRequest(UUID organizationId)`, manually transitions to ACTIVE regardless of current state). Both require API key auth (existing `ApiKeyAuthFilter` covers `/internal/*`). Pattern: follow existing `AdminBillingController.java` structure. |
| 420.7 | Add lifecycle transition validation to `Subscription.transitionTo()` | 420B | | In `Subscription.java`, enforce valid transitions in `transitionTo()`: TRIALING -> ACTIVE or EXPIRED; ACTIVE -> PENDING_CANCELLATION or PAST_DUE; PENDING_CANCELLATION -> GRACE_PERIOD or ACTIVE; PAST_DUE -> ACTIVE or SUSPENDED; SUSPENDED -> ACTIVE or LOCKED; GRACE_PERIOD -> ACTIVE or LOCKED; EXPIRED -> ACTIVE or LOCKED; LOCKED -> ACTIVE. Throw `InvalidStateException` for invalid transitions. |
| 420.8 | Create `BillingControllerIntegrationTest` | 420B | | `billing/BillingControllerIntegrationTest.java`. ~12 tests: GET subscription returns correct status for TRIALING org, GET subscription returns correct `canSubscribe`/`canCancel` flags, POST subscribe with OWNER returns 200, POST subscribe with non-OWNER returns 403, POST cancel with OWNER transitions to PENDING_CANCELLATION, POST cancel with non-ACTIVE status returns 400, GET payments returns paginated results, GET payments for OWNER returns list, extend-trial internal endpoint extends trial, activate internal endpoint transitions to ACTIVE. Pattern: follow `SubscriptionIntegrationTest.java` setup with `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)`. |
| 420.9 | Create `SubscriptionLifecycleTest` | 420B | | `billing/SubscriptionLifecycleTest.java`. ~10 tests: valid transitions (TRIALING->ACTIVE, ACTIVE->PENDING_CANCELLATION, PENDING_CANCELLATION->GRACE_PERIOD, GRACE_PERIOD->LOCKED, EXPIRED->ACTIVE, LOCKED->ACTIVE, PAST_DUE->ACTIVE, PAST_DUE->SUSPENDED, SUSPENDED->LOCKED, ACTIVE->PAST_DUE), invalid transitions (TRIALING->LOCKED, ACTIVE->EXPIRED, LOCKED->TRIALING) throw `InvalidStateException`. Can be unit tests (no Spring context needed). |
| 420.10 | Update existing `SubscriptionIntegrationTest` | 420B | | Modify `billing/SubscriptionIntegrationTest.java`. Update to use new lifecycle model: replace `planSlug` references with `subscriptionStatus`, update assertions for new `BillingResponse` shape, ensure existing tests pass with the restructured entity. If the test is too tightly coupled to old model, rewrite relevant test cases. |

### Key Files

**Slice 420A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java` -- full rewrite
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingController.java` -- full rewrite
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- TRIALING subscription creation

**Slice 420A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscribeResponse.java`

**Slice 420A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java` -- restructured entity from 419A
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingProperties.java` -- config from 419B
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` -- for member count

**Slice 420B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/AdminBillingController.java` -- add internal endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java` -- add `transitionTo()` validation
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionIntegrationTest.java` -- update for new model

**Slice 420B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingControllerIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionLifecycleTest.java`

**Slice 420B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ApiKeyAuthFilter.java` -- internal endpoint auth pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionIntegrationTest.java` -- existing test setup

### Architecture Decisions

- **Service-level cancellation without PayFast**: In this epic, the cancel endpoint transitions the subscription to PENDING_CANCELLATION in the database but does NOT call the PayFast cancellation API. The PayFast API integration is added in Epic 421. This allows the lifecycle model to be tested independently of the payment gateway.
- **Subscribe endpoint returns placeholder**: The subscribe endpoint validates that the subscription status allows subscription and returns a response shape, but the PayFast form generation is implemented in Epic 421. This epic establishes the API contract.
- **Two-slice decomposition**: 420A (service rewrite + controller + provisioning) establishes the core lifecycle. 420B (internal endpoints + transition validation + tests) layers on admin tooling and comprehensive testing.

---

## Epic 421: PayFast Platform Integration

**Goal**: Implement the `PlatformPayFastService` for checkout form generation with MD5 signature, the ITN webhook endpoint with IP validation and signature verification, PayFast cancellation API client, and idempotent payment recording. Wires the PayFast integration into the subscribe and cancel flows established in Epic 420.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 4.2--4.5; ADR-220

**Dependencies**: Epic 419 (entity model), Epic 420 (lifecycle service)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **421A** | 421.1--421.5 | `PlatformPayFastService` (form generation, MD5 signature, sandbox/prod URL), wire into `SubscriptionService.subscribe()`, `PlatformPayFastServiceTest` | |
| **421B** | 421.6--421.11 | `SubscriptionItnController` + `SubscriptionItnService` (webhook endpoint, IP validation, signature verification, ITN processing, idempotency), PayFast cancellation API client, `SecurityConfig` webhook path permit, `SubscriptionItnIntegrationTest` | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 421.1 | Create `PlatformPayFastService` with form generation | 421A | | `billing/payfast/PlatformPayFastService.java`. Method: `SubscribeResponse generateCheckoutForm(UUID organizationId)`. Builds `LinkedHashMap<String, String>` with fields: `merchant_id`, `merchant_key`, `return_url`, `cancel_url`, `notify_url`, `amount` (formatted as rands: cents/100 with 2 decimal places), `item_name`, `subscription_type` ("1"), `recurring_amount`, `frequency` ("3" = monthly), `cycles` ("0" = indefinite), `custom_str1` (organizationId.toString()), `signature`. Selects URL based on `sandbox` flag: `https://sandbox.payfast.co.za/eng/process` vs `https://www.payfast.co.za/eng/process`. Injects `PayFastBillingProperties` and `BillingProperties`. Pattern: new class in `billing/payfast/` sub-package. |
| 421.2 | Implement MD5 signature generation | 421A | | In `PlatformPayFastService`: private method `generateSignature(Map<String, String> data)`. Takes all form fields (excluding `signature`), URL-encodes values, concatenates as `key=value` pairs joined by `&`, appends `&passphrase=<passphrase>`, computes MD5 hash. Returns lowercase hex string. Helper: `formatCentsToRands(int cents)` returns String like "499.00". |
| 421.3 | Wire `PlatformPayFastService` into `SubscriptionService.subscribe()` | 421A | | Modify `billing/SubscriptionService.java`. Inject `PlatformPayFastService`. In `initiateSubscribe()`, after validating status, call `platformPayFastService.generateCheckoutForm(orgInternalId)` and return the `SubscribeResponse`. |
| 421.4 | Create `PlatformPayFastServiceTest` | 421A | | `billing/payfast/PlatformPayFastServiceTest.java`. ~6 unit tests: form fields contain all required PayFast fields, `custom_str1` contains organization ID, signature is valid MD5 hash, sandbox URL used when `sandbox = true`, production URL used when `sandbox = false`, `formatCentsToRands` correctly converts 49900 to "499.00" and 100 to "1.00". Use mock `PayFastBillingProperties` and `BillingProperties`. |
| 421.5 | Add PayFast cancellation API client to `PlatformPayFastService` | 421A | | In `PlatformPayFastService`: method `cancelPayFastSubscription(String payfastToken)`. Makes HTTP PUT to `https://api.payfast.co.za/subscriptions/{token}/cancel` (or sandbox equivalent) with headers: `merchant-id`, `version: v1`, `timestamp` (ISO-8601), `signature` (MD5 of header params + passphrase). Uses Spring `RestClient` (or `WebClient`). Throws `InvalidStateException` on failure. ~3 additional unit tests for cancellation request construction. |
| 421.6 | Create `SubscriptionItnController` (webhook endpoint) | 421B | | `billing/SubscriptionItnController.java`. Single endpoint: `POST /api/webhooks/subscription`. Accepts `@RequestParam Map<String, String> params` (PayFast sends form-encoded POST). Delegates to `SubscriptionItnService.processItn(params, request.getRemoteAddr())`. Returns `ResponseEntity.ok().build()` (PayFast requires 200). Thin controller -- one-liner delegation. |
| 421.7 | Create `SubscriptionItnService` with IP and signature validation | 421B | | `billing/SubscriptionItnService.java`. Method `processItn(Map<String, String> params, String sourceIp)`. Step 1: `validateSourceIp(sourceIp)` -- checks IP is within PayFast range `197.97.145.144/28` (197.97.145.144-159). Allows `127.0.0.1` and `0:0:0:0:0:0:0:1` in sandbox mode. Throws `ForbiddenException` on failure. Step 2: `validateSignature(params)` -- reconstructs MD5 from all params except `signature`, sorted alphabetically, URL-encoded values, appends passphrase. Compares with `params.get("signature")`. Throws `ForbiddenException` on mismatch. |
| 421.8 | Implement ITN payment processing and idempotency | 421B | | In `SubscriptionItnService`: Step 3: extract `m_payment_id`, `custom_str1` (org ID), `payment_status`, `token`, `amount_gross`. Step 4: idempotency check -- `subscriptionPaymentRepository.existsByPayfastPaymentId(mPaymentId)` -- if true, return early. Step 5: route by `payment_status`: `COMPLETE` -> transition subscription to ACTIVE, store `payfastToken` (first payment), set `lastPaymentAt`, compute `nextBillingAt` (+ 1 month), clear `graceEndsAt`, record `SubscriptionPayment`. `FAILED` -> if ACTIVE, transition to PAST_DUE, record failed payment. `CANCELLED` -> if PAST_DUE transition to SUSPENDED; if ACTIVE/PENDING_CANCELLATION transition to GRACE_PERIOD with `graceEndsAt`. Step 6: evict subscription status cache (Epic 422 wires this). Step 7: create audit event. All `@Transactional`. |
| 421.9 | Register webhook path in `SecurityConfig` | 421B | | Modify `config/SecurityConfig.java` (or equivalent security config). Permit `/api/webhooks/subscription` as unauthenticated (it uses its own IP + signature validation). Ensure it bypasses JWT auth, tenant filter, and member filter. Pattern: check how `/api/webhooks/payment/payfast` (existing BYOAK webhook) is configured. |
| 421.10 | Wire PayFast cancellation into `SubscriptionService.cancel()` | 421B | | Modify `billing/SubscriptionService.java`. In `cancelSubscription()`, before transitioning to PENDING_CANCELLATION, call `platformPayFastService.cancelPayFastSubscription(subscription.getPayfastToken())`. Handle case where `payfastToken` is null (trialing tenant cancelling -- just transition, no API call). |
| 421.11 | Create `SubscriptionItnIntegrationTest` | 421B | | `billing/SubscriptionItnIntegrationTest.java`. ~10 tests: valid ITN with COMPLETE status transitions subscription to ACTIVE, duplicate `m_payment_id` is idempotent (no double-transition), FAILED ITN transitions ACTIVE to PAST_DUE, CANCELLED ITN transitions to SUSPENDED/GRACE, invalid signature returns processing without state change (returns 200 per PayFast requirement but logs error), ITN stores `payfastToken` on first payment, payment record created with correct fields, `rawItn` JSONB stored correctly. Note: IP validation tests may need to mock `request.getRemoteAddr()` or use a test configuration that allows localhost. Pattern: follow `SubscriptionIntegrationTest.java` setup. |

### Key Files

**Slice 421A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PlatformPayFastService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PlatformPayFastServiceTest.java`

**Slice 421A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java` -- wire PayFast form generation

**Slice 421A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PayFastBillingProperties.java` -- config from 419B
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/PayFastPaymentGateway.java` -- reference for signature pattern (do NOT reuse, just reference)

**Slice 421B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnIntegrationTest.java`

**Slice 421B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` -- permit webhook path (or `SecurityBeanConfig.java`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java` -- wire cancellation API

**Slice 421B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/PaymentWebhookController.java` -- existing webhook pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClientIpResolver.java` -- IP resolution helper

### Architecture Decisions

- **Separate from BYOAK**: `PlatformPayFastService` is intentionally separate from `PayFastPaymentGateway` (tenant BYOAK). Different credential source (config vs SecretStore), different payment type (recurring vs one-time), different entities. See ADR-220.
- **IP validation in service, not filter**: The ITN webhook is unauthenticated -- IP and signature validation happen in `SubscriptionItnService`, not in a filter. This keeps the webhook path simple (permit in SecurityConfig, validate in service).
- **Always return 200**: PayFast requires 200 OK regardless of processing outcome. Even on signature failure, log the error but return 200. PayFast retries on non-200 responses, which would cause noise.
- **Two-slice decomposition**: 421A (outbound -- form generation + cancellation API) and 421B (inbound -- ITN webhook + processing). Outbound is simpler and can be tested with unit tests. Inbound requires integration tests with database state verification.

---

## Epic 422: Read-Only Enforcement & Scheduled Jobs

**Goal**: Implement the `SubscriptionGuardFilter` for blocking write HTTP methods during grace/locked states, the `SubscriptionStatusCache` (Caffeine-based, 5-minute TTL), and the three daily scheduled expiry jobs (trial expiry, grace period expiry, pending cancellation end). Wire the filter into the SecurityConfig filter chain at position 5.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 5, 6; ADR-221, ADR-222

**Dependencies**: Epic 419 (entity model), Epic 420 (lifecycle service for status resolution)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **422A** | 422.1--422.5 | `SubscriptionStatusCache` (Caffeine), `SubscriptionGuardFilter`, `SecurityConfig` filter chain update, `SubscriptionGuardFilterTest` | |
| **422B** | 422.6--422.10 | `SubscriptionExpiryJob` (3 scheduled methods), cache eviction wiring from ITN service, `SubscriptionExpiryJobTest` | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 422.1 | Create `SubscriptionStatusCache` | 422A | | `billing/SubscriptionStatusCache.java`. `@Component`. Caffeine cache with 5-minute `expireAfterWrite` TTL, `maximumSize(1000)`. Method `getStatus(UUID organizationId)` returns `SubscriptionStatus` from cache, loading from `SubscriptionRepository.findByOrganizationId()` on miss (defensive default: `TRIALING` if no subscription found). Method `evict(UUID organizationId)` invalidates cache entry. Constructor injects `SubscriptionRepository`. Pattern: follow existing Caffeine usage in codebase (check if Caffeine is already a dependency; it is listed in architecture as available). |
| 422.2 | Create `SubscriptionGuardFilter` | 422A | | `billing/SubscriptionGuardFilter.java`. Extends `OncePerRequestFilter`. `doFilterInternal()`: skip if `RequestScopes.ORG_ID` is not bound (unauthenticated paths). Resolve `orgId` from `RequestScopes.ORG_ID`. Look up internal org UUID via `OrganizationRepository.findByExternalOrgId()`. Get status from `SubscriptionStatusCache`. For read-only states (GRACE_PERIOD, SUSPENDED, EXPIRED): if HTTP method is POST/PUT/PATCH/DELETE and path does NOT start with `/api/billing/`, write 403 ProblemDetail with `type: "subscription_required"`. For LOCKED: if path does NOT start with `/api/billing/`, write 403 ProblemDetail with `type: "subscription_locked"`. Otherwise: `chain.doFilter()`. Private helpers: `isReadOnlyState()`, `isMutatingMethod()`, `isBillingPath()`. Pattern: follow `MemberFilter.java` filter structure. |
| 422.3 | Wire `SubscriptionGuardFilter` into SecurityConfig filter chain | 422A | | Modify `config/SecurityConfig.java`. Add `SubscriptionGuardFilter` at position 5 in the filter chain (after `MemberFilter`, before `PlatformAdminFilter`). Ensure the filter is added via `.addFilterAfter(subscriptionGuardFilter, MemberFilter.class)` or equivalent. Also ensure `/internal/*` paths bypass the guard (they use API key auth, not tenant-scoped). Pattern: follow existing filter chain registration in `SecurityConfig.java`. |
| 422.4 | Implement ProblemDetail error responses in the guard filter | 422A | | In `SubscriptionGuardFilter`: `writeSubscriptionRequiredResponse(response)` sets status 403, content-type `application/problem+json`, writes JSON: `{"type":"subscription_required","title":"Subscription required","detail":"Your subscription has expired. Subscribe to regain full access.","resubscribeUrl":"/settings/billing"}`. `writeLockedResponse(response)` similar but with `type: "subscription_locked"`, `title: "Account locked"`. Use `ObjectMapper` for JSON serialization. |
| 422.5 | Create `SubscriptionGuardFilterTest` | 422A | | `billing/SubscriptionGuardFilterTest.java`. ~12 integration tests: GET allowed during GRACE_PERIOD (200), POST blocked during GRACE_PERIOD (403 with `subscription_required`), PUT blocked during SUSPENDED (403), DELETE blocked during EXPIRED (403), GET blocked during LOCKED except billing path (403 with `subscription_locked`), GET `/api/billing/subscription` allowed during LOCKED (200), POST `/api/billing/subscribe` allowed during GRACE_PERIOD (200), GET allowed during TRIALING (200), POST allowed during ACTIVE (200), POST allowed during PENDING_CANCELLATION (200), POST allowed during PAST_DUE (200), unauthenticated request passes through (no ORG_ID bound). Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc`. Setup subscriptions in different states, make requests with appropriate JWTs. |
| 422.6 | Create `SubscriptionExpiryJob` with trial expiry method | 422B | | `billing/SubscriptionExpiryJob.java`. `@Component`. `@Scheduled(cron = "0 0 3 * * *")` method `processTrialExpiry()`: queries `subscriptionRepository.findBySubscriptionStatusAndTrialEndsAtBefore(TRIALING, Instant.now())`, transitions each to EXPIRED, sets `graceEndsAt = Instant.now().plus(Duration.ofDays(billingProperties.gracePeriodDays()))`, saves, evicts cache, creates audit event. Logs count. Injects `SubscriptionRepository`, `BillingProperties`, `SubscriptionStatusCache`, `AuditService`. Pattern: follow `DormancyScheduledJob` pattern (check codebase for existing `@Scheduled` jobs). |
| 422.7 | Add grace period expiry method to `SubscriptionExpiryJob` | 422B | | In `SubscriptionExpiryJob.java`: `@Scheduled(cron = "0 5 3 * * *")` method `processGraceExpiry()`. Queries `subscriptionRepository.findBySubscriptionStatusInAndGraceEndsAtBefore(List.of(GRACE_PERIOD, EXPIRED, SUSPENDED), Instant.now())`. Transitions each to LOCKED, saves, evicts cache, creates audit event. Logs count. |
| 422.8 | Add pending cancellation end method to `SubscriptionExpiryJob` | 422B | | In `SubscriptionExpiryJob.java`: `@Scheduled(cron = "0 10 3 * * *")` method `processPendingCancellationEnd()`. Queries `subscriptionRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(PENDING_CANCELLATION, Instant.now())`. Transitions each to GRACE_PERIOD, sets `graceEndsAt = sub.getCurrentPeriodEnd().plus(Duration.ofDays(billingProperties.gracePeriodDays()))`, saves, evicts cache, creates audit event. Logs count. |
| 422.9 | Wire cache eviction from ITN service and admin endpoints | 422B | | Modify `billing/SubscriptionItnService.java` (from 421B): inject `SubscriptionStatusCache`, call `statusCache.evict(orgId)` after any status change in `processItn()`. Modify `billing/SubscriptionService.java`: inject `SubscriptionStatusCache`, call `evict()` after `cancelSubscription()` and admin `extendTrial()`/`activate()` methods. |
| 422.10 | Create `SubscriptionExpiryJobTest` | 422B | | `billing/SubscriptionExpiryJobTest.java`. ~8 integration tests: TRIALING subscription with past `trialEndsAt` transitions to EXPIRED with grace period set, TRIALING subscription with future `trialEndsAt` is untouched, GRACE_PERIOD with past `graceEndsAt` transitions to LOCKED, EXPIRED with past `graceEndsAt` transitions to LOCKED, SUSPENDED with past `graceEndsAt` transitions to LOCKED, GRACE_PERIOD with future `graceEndsAt` is untouched, PENDING_CANCELLATION with past `currentPeriodEnd` transitions to GRACE_PERIOD with grace set, PENDING_CANCELLATION with future `currentPeriodEnd` is untouched. Call job methods directly (no need to test cron scheduling). Pattern: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`. |

### Key Files

**Slice 422A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionStatusCache.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionGuardFilter.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionGuardFilterTest.java`

**Slice 422A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` -- add filter to chain

**Slice 422A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` -- filter pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` -- filter chain position reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- ORG_ID ScopedValue
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/OrganizationRepository.java` -- org lookup

**Slice 422B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJobTest.java`

**Slice 422B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionItnService.java` -- cache eviction (if Epic 421 already landed)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java` -- cache eviction

**Slice 422B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- audit event creation pattern
- Existing `@Scheduled` job in codebase for cron pattern reference

### Architecture Decisions

- **Filter-level HTTP method check**: Blocks all POST/PUT/PATCH/DELETE uniformly during grace/locked. No per-endpoint granularity. Allowlist for `/api/billing/*` only. See ADR-221.
- **Caffeine cache**: 5-minute TTL, max 1000 entries. In-memory only -- no distributed cache needed for v1. Eviction on state changes keeps staleness window small.
- **Daily scheduled jobs**: Three separate `@Scheduled` methods at 3:00, 3:05, and 3:10 AM. Staggered by 5 minutes to avoid database contention. 24-hour detection delay is acceptable for day/month-scale timers. See ADR-222.
- **Two-slice decomposition**: 422A (guard filter + cache) handles the synchronous request path. 422B (scheduled jobs + cache eviction wiring) handles the asynchronous background processing.

---

## Epic 423: Frontend Billing Page & Components

**Goal**: Rewrite the billing settings page to render lifecycle-aware content for all 8 subscription states. Create `SubscribeButton` (PayFast form POST redirect), `CancelConfirmDialog`, `PaymentHistory`, `TrialCountdown`, and `GraceCountdown` components. Rewrite `billing/actions.ts` with subscribe, cancel, and payments server actions.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 8.1, 8.2, 8.6

**Dependencies**: Epic 420 (API endpoints must exist for server actions to call)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **423A** | 423.1--423.5 | Updated `BillingResponse` type in `internal-api.ts`, rewritten `billing/actions.ts` (subscribe, cancel, payments server actions), `SubscribeButton` + `CancelConfirmDialog` components, rewritten `billing/page.tsx` (lifecycle-aware rendering for 8 states) | |
| **423B** | 423.6--423.10 | `PaymentHistory`, `TrialCountdown`, `GraceCountdown` components, PayFast redirect handling (`?result=success` polling), `billing-page.test.tsx` rewrite, `subscribe-button.test.tsx`, `cancel-confirm-dialog.test.tsx` | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 423.1 | Update `BillingResponse` type in `internal-api.ts` | 423A | | Modify `frontend/lib/internal-api.ts`. Replace existing `BillingResponse` type with new shape: `{ status: string; trialEndsAt: string | null; currentPeriodEnd: string | null; graceEndsAt: string | null; nextBillingAt: string | null; monthlyAmountCents: number; currency: string; limits: { maxMembers: number; currentMembers: number }; canSubscribe: boolean; canCancel: boolean }`. Add `SubscribeResponse` type: `{ paymentUrl: string; formFields: Record<string, string> }`. Add `PaymentResponse` type: `{ id: string; payfastPaymentId: string; amountCents: number; currency: string; status: string; paymentDate: string }`. |
| 423.2 | Rewrite `billing/actions.ts` with new server actions | 423A | | Rewrite `frontend/app/(app)/org/[slug]/settings/billing/actions.ts`. Add server actions: `getSubscription()` calls `GET /api/billing/subscription`, `subscribe()` calls `POST /api/billing/subscribe` and returns `SubscribeResponse`, `cancelSubscription()` calls `POST /api/billing/cancel`, `getPayments()` calls `GET /api/billing/payments`. Remove old `upgradePlan()` action. Use existing `api()` client from `lib/api.ts` (attaches Bearer JWT). Pattern: follow existing `actions.ts` pattern in the billing directory. |
| 423.3 | Create `SubscribeButton` component | 423A | | `frontend/components/billing/subscribe-button.tsx`. Client component (`"use client"`). On click: calls `subscribe()` server action, receives `SubscribeResponse`, creates a hidden `<form>` element with `action={paymentUrl}` and `method="POST"`, populates hidden `<input>` fields from `formFields`, calls `form.submit()`. Shows loading spinner during API call. Props: `disabled?: boolean`, `className?: string`. Uses Shadcn `Button` component. Pattern: follow existing button component patterns in `components/billing/`. |
| 423.4 | Create `CancelConfirmDialog` component | 423A | | `frontend/components/billing/cancel-confirm-dialog.tsx`. Client component. Uses Shadcn `AlertDialog` with confirmation text: "Are you sure you want to cancel your subscription? You'll retain full access until {currentPeriodEnd}. After that, you'll have 60 days of read-only access." Confirm button calls `cancelSubscription()` server action. Shows loading state during API call. Calls `router.refresh()` on success. Props: `currentPeriodEnd: string`, `onCancel: () => void`. |
| 423.5 | Rewrite billing page for lifecycle-aware rendering | 423A | | Rewrite `frontend/app/(app)/org/[slug]/settings/billing/page.tsx`. Server component that calls `getSubscription()`. Renders different content based on `status`: TRIALING (trial countdown, feature list, Subscribe CTA), ACTIVE (next billing date, Cancel link, payment history), PENDING_CANCELLATION ("Cancelling on {date}", Resubscribe CTA, payment history), PAST_DUE (warning banner, link to PayFast portal, payment history), GRACE_PERIOD/EXPIRED/SUSPENDED (grace countdown, Subscribe CTA, read-only explanation), LOCKED (full-page Resubscribe CTA, data preservation message). Uses Shadcn `Card`, `Badge`, `Separator` components. Format amounts as "R499.00" (ZAR). Pattern: follow existing billing page layout structure. |
| 423.6 | Create `PaymentHistory` component | 423B | | `frontend/components/billing/payment-history.tsx`. Server or client component that calls `getPayments()`. Renders a Shadcn `Table` with columns: Date, Amount (formatted as "R499.00"), Status (badge: COMPLETE=green, FAILED=red, REFUNDED=amber), PayFast Reference. Empty state: "No payments yet." Props: none (fetches internally). Pattern: follow existing table patterns in project/customer list pages. |
| 423.7 | Create `TrialCountdown` and `GraceCountdown` components | 423B | | `frontend/components/billing/trial-countdown.tsx`: displays "X days remaining in your trial" with a progress bar (14 days total). Props: `trialEndsAt: string`. `frontend/components/billing/grace-countdown.tsx`: displays "X days remaining to resubscribe" with warning styling. Props: `graceEndsAt: string`. Both compute days remaining using `date-fns` or native Date math. Pattern: follow existing date utility usage in `lib/date-utils.ts`. |
| 423.8 | Implement PayFast redirect handling | 423B | | In `billing/page.tsx`: detect `?result=success` or `?result=cancelled` search params. On `result=success`: show "Processing your payment..." message, poll `getSubscription()` every 2 seconds (up to 30 seconds) waiting for status to change to ACTIVE. On status change, show success message and refresh page. On `result=cancelled`: show "Payment was cancelled" dismissible message. Use `useSearchParams()` in a client component wrapper. |
| 423.9 | Rewrite `billing-page.test.tsx` | 423B | | Rewrite `frontend/app/(app)/org/[slug]/settings/billing/billing-page.test.tsx`. ~8 tests: renders trial countdown for TRIALING status, renders subscribe CTA for TRIALING, renders active subscription details for ACTIVE, renders cancel link for ACTIVE, renders cancellation notice for PENDING_CANCELLATION, renders payment failed warning for PAST_DUE, renders grace countdown for GRACE_PERIOD, renders full-page resubscribe for LOCKED. Mock `getSubscription()` server action to return different `BillingResponse` shapes. Pattern: follow existing `billing-page.test.tsx` testing pattern (Vitest + React Testing Library). |
| 423.10 | Create `subscribe-button.test.tsx` and `cancel-confirm-dialog.test.tsx` | 423B | | `frontend/components/billing/subscribe-button.test.tsx`: ~3 tests (calls subscribe action on click, creates form with correct fields, shows loading state). `frontend/components/billing/cancel-confirm-dialog.test.tsx`: ~3 tests (shows confirmation text, calls cancel action on confirm, shows loading state). Pattern: follow existing component test patterns in `components/billing/`. |

### Key Files

**Slice 423A -- Modify:**
- `frontend/lib/internal-api.ts` -- update BillingResponse type
- `frontend/app/(app)/org/[slug]/settings/billing/actions.ts` -- rewrite server actions
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` -- rewrite billing page

**Slice 423A -- Create:**
- `frontend/components/billing/subscribe-button.tsx`
- `frontend/components/billing/cancel-confirm-dialog.tsx`

**Slice 423A -- Read for context:**
- `frontend/lib/api.ts` -- API client pattern
- `frontend/components/billing/upgrade-button.tsx` -- existing component structure (will be deleted in Epic 426)
- `frontend/components/billing/upgrade-confirm-dialog.tsx` -- dialog pattern reference

**Slice 423B -- Create:**
- `frontend/components/billing/payment-history.tsx`
- `frontend/components/billing/trial-countdown.tsx`
- `frontend/components/billing/grace-countdown.tsx`

**Slice 423B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/billing/billing-page.test.tsx` -- rewrite tests

**Slice 423B -- Create (tests):**
- `frontend/components/billing/subscribe-button.test.tsx`
- `frontend/components/billing/cancel-confirm-dialog.test.tsx`

**Slice 423B -- Read for context:**
- `frontend/lib/date-utils.ts` -- date formatting utilities
- `frontend/components/billing/upgrade-prompt.test.tsx` -- existing test pattern reference

### Architecture Decisions

- **PayFast form POST redirect**: Not a popup or iframe. The frontend creates a hidden `<form>` and submits it, causing a full-page redirect to PayFast. This is PayFast's recommended integration pattern for subscriptions.
- **Polling on return**: After PayFast redirects back with `?result=success`, the frontend polls the subscription API because the ITN webhook may arrive before or after the user redirect. Polling every 2 seconds for up to 30 seconds covers the typical ITN delivery window.
- **Two-slice decomposition**: 423A (page structure + core actions + CTA components) establishes the page. 423B (supporting components + redirect handling + tests) fills in the details and adds test coverage.

---

## Epic 424: Frontend Banner, Context & Error Interceptor

**Goal**: Create the global `SubscriptionBanner` component rendered in the org-scoped layout, the `SubscriptionContext` provider for write-action gating (`isWriteEnabled` boolean), and the API error interceptor that catches `subscription_required` and `subscription_locked` 403 responses. Wire the banner into the app shell layout.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 8.4, 8.5, 8.6

**Dependencies**: Epic 423 (billing page components and server actions)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **424A** | 424.1--424.5 | `SubscriptionContext` provider + `useSubscription()` hook, `SubscriptionBanner` component, layout integration, `subscription-context.test.tsx` | |
| **424B** | 424.6--424.9 | API error interceptor for `subscription_required`/`subscription_locked` 403 responses, write-action gating examples (disable mutation buttons), `subscription-banner.test.tsx`, `api-error-interceptor.test.tsx` | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 424.1 | Create `SubscriptionContext` provider and `useSubscription()` hook | 424A | | `frontend/lib/subscription-context.tsx`. Client component (`"use client"`). `SubscriptionContextValue` interface: `{ status: string; isWriteEnabled: boolean; canSubscribe: boolean; canCancel: boolean }`. `isWriteEnabled` is true for TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE. `canSubscribe` is true for TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, LOCKED. `canCancel` is true for ACTIVE only. Provider component `SubscriptionProvider` accepts `billingResponse` prop and computes values via `useMemo`. Export `useSubscription()` hook. |
| 424.2 | Create `SubscriptionBanner` component | 424A | | `frontend/components/billing/subscription-banner.tsx`. Client component. Uses `useSubscription()`. Rendering rules per architecture Section 8.4: TRIALING >7 days: hidden; TRIALING <=7 days: info banner (blue) "Your trial ends in {N} days -- Subscribe now" (dismissible per-session via `useState`); ACTIVE: hidden; PENDING_CANCELLATION: warning banner (amber) "Your subscription ends on {date}" (dismissible per-session); PAST_DUE: warning banner (amber) "Payment failed -- update your payment method" (persistent); GRACE_PERIOD/EXPIRED/SUSPENDED: error banner (red) "Read-only mode -- subscribe to regain full access" (persistent); LOCKED: hidden (full-page redirect handles this). Banner links to `/settings/billing`. Uses Shadcn `Alert` or custom banner styling. |
| 424.3 | Wire `SubscriptionProvider` and `SubscriptionBanner` into org layout | 424A | | Modify `frontend/app/(app)/org/[slug]/layout.tsx`. Fetch subscription status via server action or API call (server-side). Pass `billingResponse` to `SubscriptionProvider` wrapping the layout children. Render `SubscriptionBanner` at the top of the content area (after the header, before the main content). Ensure the banner is visible on every org-scoped page. |
| 424.4 | Add session-based dismissibility to banner | 424A | | In `SubscriptionBanner`: use `useState` or `sessionStorage` to track dismissed state. Info and warning banners are dismissible per-session (close button that sets state, reappears on new session). Error banners (GRACE/EXPIRED/SUSPENDED) are persistent (no close button). |
| 424.5 | Create `subscription-context.test.tsx` | 424A | | `frontend/lib/__tests__/subscription-context.test.tsx`. ~6 tests: `isWriteEnabled` is true for TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE. `isWriteEnabled` is false for GRACE_PERIOD, EXPIRED, SUSPENDED, LOCKED. `canSubscribe` is true for TRIALING, GRACE_PERIOD, EXPIRED, SUSPENDED, LOCKED. `canSubscribe` is false for ACTIVE, PENDING_CANCELLATION, PAST_DUE. `canCancel` is true only for ACTIVE. Render `SubscriptionProvider` with test values and assert context output via `useSubscription()`. |
| 424.6 | Add API error interceptor for subscription-related 403s | 424B | | Modify `frontend/lib/api.ts` (or create `frontend/lib/subscription-error-handler.ts`). Add response interceptor that catches 403 responses where `body.type === "subscription_required"` or `body.type === "subscription_locked"`. For `subscription_required`: show a toast/modal with "Subscribe to continue" and link to billing page. For `subscription_locked`: redirect to billing page. Do NOT intercept other 403 responses (permission-based 403s should pass through normally). |
| 424.7 | Add write-action gating to key mutation buttons | 424B | | Demonstrate the `useSubscription()` pattern by disabling a few key mutation buttons: In `projects/page.tsx` (or the "Create Project" button component): add `disabled={!isWriteEnabled}` and tooltip "Subscribe to enable this action". Apply the same pattern to 1-2 other prominent create/edit buttons as examples. Other pages can be updated incrementally -- this task establishes the pattern. |
| 424.8 | Create `subscription-banner.test.tsx` | 424B | | `frontend/components/billing/subscription-banner.test.tsx`. ~8 tests: banner hidden for ACTIVE, banner hidden for TRIALING >7 days, info banner shown for TRIALING <=7 days, warning banner for PENDING_CANCELLATION, warning banner for PAST_DUE, error banner for GRACE_PERIOD, error banner for EXPIRED, dismiss button works for info/warning banners, dismiss button absent for error banners. Mock `useSubscription()`. |
| 424.9 | Create `api-error-interceptor.test.tsx` | 424B | | `frontend/lib/__tests__/api-error-interceptor.test.tsx`. ~4 tests: intercepts 403 with `subscription_required` type, intercepts 403 with `subscription_locked` type, does NOT intercept 403 without type field, does NOT intercept 403 with other type (e.g., `forbidden`). |

### Key Files

**Slice 424A -- Create:**
- `frontend/lib/subscription-context.tsx`
- `frontend/components/billing/subscription-banner.tsx`
- `frontend/lib/__tests__/subscription-context.test.tsx`

**Slice 424A -- Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` -- add provider and banner

**Slice 424A -- Read for context:**
- `frontend/lib/capabilities.tsx` -- existing context provider pattern
- `frontend/app/(app)/org/[slug]/layout.tsx` -- layout structure

**Slice 424B -- Create:**
- `frontend/components/billing/subscription-banner.test.tsx`
- `frontend/lib/__tests__/api-error-interceptor.test.tsx`

**Slice 424B -- Modify:**
- `frontend/lib/api.ts` -- add error interceptor
- `frontend/app/(app)/org/[slug]/projects/page.tsx` -- example write-action gating (or relevant create button component)

**Slice 424B -- Read for context:**
- `frontend/lib/error-handler.ts` -- existing error handling pattern
- `frontend/app/(app)/org/[slug]/projects/page.tsx` -- existing page for gating example

### Architecture Decisions

- **Context provider over prop drilling**: `SubscriptionContext` provides subscription status to any component in the tree without prop drilling. This is consistent with the existing `capabilities.tsx` pattern.
- **Banner in layout, not per-page**: The banner is rendered in the org-scoped layout so it appears on every page automatically. No per-page integration needed.
- **Session-based dismissibility**: Info/warning banners can be dismissed per-session (useState or sessionStorage). Error banners are persistent because the user needs to take action.
- **Two-slice decomposition**: 424A (context + banner + layout wiring) establishes the infrastructure. 424B (error interceptor + write gating + tests) adds the cross-cutting enforcement layer.

---

## Epic 425: Backend Cleanup -- Dead Tier Code

**Goal**: Delete `Tier.java`, `PlanLimits.java`, `PlanSyncService.java`, `PlanSyncController.java`, and their test files. Remove `tier` and `planSlug` fields from `Organization.java`. Update `MemberSyncService` to use `BillingProperties.maxMembers()`. Update `AssistantService` to use subscription status instead of tier check. Update all test files that call `planSyncService.syncPlan(ORG_ID, "pro-plan")` to use a new test helper method.

**References**: `architecture/phase57-tenant-subscription-payments.md` Sections 7, 10

**Dependencies**: Epics 419-422 (all backend feature epics must be stable)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **425A** | 425.1--425.5 | Delete `Tier.java`, `PlanLimits.java`, `PlanSyncService.java`, `PlanSyncController.java`. Remove `tier`/`planSlug` from `Organization.java`. Update `MemberSyncService` and `AssistantService`. Create test utility method to replace `planSyncService.syncPlan()` calls. | |
| **425B** | 425.6--425.9 | Update all test files (~50+) that reference `planSyncService.syncPlan(ORG_ID, "pro-plan")` to use the new test helper. Delete `PlanSyncIntegrationTest.java`. Verify no remaining references. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 425.1 | Delete `Tier.java` and `PlanLimits.java` | 425A | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Tier.java` (enum with STARTER, PRO). Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanLimits.java` (per-tier member limits class). |
| 425.2 | Delete `PlanSyncService.java` and `PlanSyncController.java` | 425A | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanSyncService.java` (Clerk Billing vestige -- syncs tier from plan slug). Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanSyncController.java` (dead endpoint). |
| 425.3 | Remove `tier` and `planSlug` from `Organization.java` | 425A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java`. Remove `@Enumerated(EnumType.STRING) Tier tier` field, `String planSlug` field, `getTier()` method, `getPlanSlug()` method, `updatePlan(Tier, String)` method. Remove import of `Tier`. The migration in V17 already dropped these columns from the database. |
| 425.4 | Update `MemberSyncService` to use `BillingProperties.maxMembers()` | 425A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java`. Replace `PlanLimits.maxMembers(org.getTier())` with `billingProperties.maxMembers()`. Inject `BillingProperties` via constructor. Remove import of `PlanLimits` and `Tier`. |
| 425.5 | Update `AssistantService` to use subscription status check | 425A | | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantService.java`. Replace `org.getTier() != Tier.PRO` check with subscription status check: allow AI assistant when status is TRIALING, ACTIVE, PENDING_CANCELLATION, or PAST_DUE (full-access states). Use `SubscriptionRepository.findByOrganizationId()` or `SubscriptionStatusCache.getStatus()`. Remove import of `Tier`. |
| 425.6 | Create test utility to replace `planSyncService.syncPlan()` | 425B | | Since `planSyncService.syncPlan(ORG_ID, "pro-plan")` is called in 50+ test files to set up the org's tier to PRO (which unlocks features that require a paid subscription), create a replacement. Option A: create a `TestSubscriptionHelper` utility that creates an ACTIVE subscription for a given org ID. Option B: if the guard filter is lenient during tests (test profile), remove the call entirely. The approach depends on what `syncPlan` actually does in tests -- it likely sets the org's tier so that `PlanLimits` checks pass. With `PlanLimits` deleted and member limits now in `BillingProperties`, the test setup may just need to ensure a subscription exists in ACTIVE or TRIALING state. |
| 425.7 | Update test files batch 1: schedule, projecttemplate, compliance, reporting | 425B | | Update test files that import `PlanSyncService` and call `planSyncService.syncPlan()`. Replace with the new test helper or direct subscription creation. Files include: `ProjectTemplateServiceTest.java`, `TimeReminderSchedulerTest.java`, `RecurringScheduleControllerTest.java`, `RecurringScheduleServiceTest.java`, `RecurringScheduleExecutorTest.java`, `InstantiateTemplateIntegrationTest.java`, `ProjectTemplateControllerTest.java`, `CustomerLifecycleControllerTest.java`, `CustomerLifecycleServiceTest.java`, `ComplianceProvisioningTest.java`, `CompliancePackControllerTest.java`, `ReportExecutionServiceTest.java`, `ReportRenderingServiceTest.java`, `ReportingControllerTest.java`, `TimesheetReportQueryTest.java`, `InvoiceAgingReportQueryTest.java`, `ProjectProfitabilityReportQueryTest.java`, `StandardReportPackSeederTest.java`. |
| 425.8 | Update test files batch 2: portal, assistant, legal, settings, other | 425B | | Continue updating test files: `PortalAuthIntegrationTest.java`, `PortalIntegrationTest.java`, `PortalBrandingControllerIntegrationTest.java`, `ConflictCheckServiceTest.java`, `ConflictCheckControllerTest.java`, `AdversePartyServiceTest.java`, `AdversePartyControllerTest.java`, `CourtCalendarUpcomingTest.java`, `CoreReadToolsTest.java`, `FinancialReadToolsTest.java`, `SearchAndNavigationToolsTest.java`, `WriteToolsTest.java`, `CostRateControllerTest.java`, `CostRateIntegrationTest.java`, `FieldValidationBugFixIntegrationTest.java`, `ConditionalVisibilityIntegrationTest.java`, `BrandingIntegrationTest.java`, `ProposalControllerTest.java`, `ExpenseControllerTest.java`, `NotificationServiceIntegrationTest.java`, `TemplateTagRepositoryTest.java`, `AutoDraftInformationRequestTest.java`, remaining test files. Delete `PlanSyncIntegrationTest.java`. |
| 425.9 | Verify no remaining references to deleted code | 425B | | Run grep across the codebase for `Tier.STARTER`, `Tier.PRO`, `PlanLimits`, `PlanSyncService`, `PlanSyncController`, `planSlug`, `org.getTier()`, `org.updatePlan()`. Fix any remaining references. Ensure all tests pass after cleanup. This is a verification task -- if references remain, fix them in this slice. |

### Key Files

**Slice 425A -- Delete:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Tier.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanLimits.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanSyncService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanSyncController.java`

**Slice 425A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java` -- remove tier/planSlug
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` -- use BillingProperties
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantService.java` -- subscription status check

**Slice 425B -- Modify (50+ files):**
- All test files listed in tasks 425.7 and 425.8

**Slice 425B -- Delete:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/PlanSyncIntegrationTest.java`

**Slice 425B -- Create:**
- Test utility class (e.g., `testutil/TestSubscriptionHelper.java` or method in existing test helper)

### Architecture Decisions

- **Two-slice decomposition**: 425A (delete code + update production source files) is a focused set of ~7 files. 425B (update 50+ test files + delete test file + verification) is a large but mechanical batch update. The test file changes are all the same pattern: replace `planSyncService.syncPlan(ORG_ID, "pro-plan")` with the new test helper.
- **Test helper strategy**: The ~50 test files that call `planSyncService.syncPlan()` all use it to set up a "paid" org that passes feature gates. With the tier system removed, the test helper needs to create an ACTIVE subscription for the org. This is a mechanical replacement.
- **Organization entity cleanup**: The `tier` and `planSlug` columns were already dropped by the V17 migration in Epic 419. This task removes the Java fields and methods that reference them.

---

## Epic 426: Frontend Cleanup -- Dead Tier Components & Test Refs

**Goal**: Delete the dead tier-based billing components (`plan-badge`, `upgrade-button`, `upgrade-card`, `upgrade-prompt`, `upgrade-confirm-dialog` and their tests). Update frontend files that reference tiers, plan slugs, or the deleted components. Verify no remaining references.

**References**: `architecture/phase57-tenant-subscription-payments.md` Section 10.1

**Dependencies**: Epics 423, 424 (frontend feature epics must be stable)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **426A** | 426.1--426.5 | Delete dead components and their tests, update files with tier/planSlug references, verify no remaining references | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 426.1 | Delete dead billing components and their tests | 426A | | Delete: `frontend/components/billing/plan-badge.tsx`, `plan-badge.test.tsx`, `upgrade-button.tsx`, `upgrade-card.tsx`, `upgrade-prompt.tsx`, `upgrade-prompt.test.tsx`, `upgrade-confirm-dialog.tsx`, `upgrade-confirm-dialog.test.tsx`. These are replaced by `subscribe-button.tsx` and `cancel-confirm-dialog.tsx` created in Epic 423. |
| 426.2 | Update `invite-member-form.tsx` to remove tier references | 426A | | Modify `frontend/components/team/invite-member-form.tsx`. Remove any references to `Tier`, plan badges, or upgrade prompts. Member limits are now sourced from the billing API response (`limits.maxMembers`). Update `invite-member-form.test.tsx` similarly. |
| 426.3 | Update settings and page files to remove tier/planSlug references | 426A | | Check and update: `frontend/app/(app)/org/[slug]/layout.tsx` (remove plan badge/tier display if present), `frontend/app/(app)/org/[slug]/team/page.tsx` (remove tier-based member limit display), `frontend/app/(app)/org/[slug]/projects/page.tsx` (remove upgrade prompts), `frontend/components/settings/vertical-profile-section.tsx` (remove tier references), `frontend/app/(app)/org/[slug]/settings/general/profile-actions.ts` (remove plan-related actions). |
| 426.4 | Update test files with tier/planSlug references | 426A | | Update: `frontend/__tests__/app/settings/integrations.test.tsx`, `frontend/app/(app)/org/[slug]/projects/projects-page.test.tsx`, `frontend/__tests__/invite-role-selection.test.tsx`, `frontend/__tests__/empty-state-pages.test.tsx`. Remove mock data with tier/planSlug fields, update assertions that check for tier badges or upgrade prompts. |
| 426.5 | Verify no remaining frontend references to dead code | 426A | | Run grep across frontend for: `plan-badge`, `upgrade-button`, `upgrade-card`, `upgrade-prompt`, `upgrade-confirm-dialog`, `PlanBadge`, `UpgradeButton`, `UpgradeCard`, `UpgradePrompt`, `UpgradeConfirmDialog`, `Tier`, `planSlug`, `STARTER`, `PRO` (as plan names). Fix any remaining references. Ensure `pnpm run build` and `pnpm test` pass. |

### Key Files

**Slice 426A -- Delete:**
- `frontend/components/billing/plan-badge.tsx`
- `frontend/components/billing/plan-badge.test.tsx`
- `frontend/components/billing/upgrade-button.tsx`
- `frontend/components/billing/upgrade-card.tsx`
- `frontend/components/billing/upgrade-prompt.tsx`
- `frontend/components/billing/upgrade-prompt.test.tsx`
- `frontend/components/billing/upgrade-confirm-dialog.tsx`
- `frontend/components/billing/upgrade-confirm-dialog.test.tsx`

**Slice 426A -- Modify:**
- `frontend/components/team/invite-member-form.tsx` -- remove tier references
- `frontend/components/team/invite-member-form.test.tsx` -- update tests
- `frontend/app/(app)/org/[slug]/layout.tsx` -- remove plan badge (if present)
- `frontend/app/(app)/org/[slug]/team/page.tsx` -- remove tier display
- Various test files listed in task 426.4

**Slice 426A -- Read for context:**
- `frontend/lib/internal-api.ts` -- updated BillingResponse type (from Epic 423)

### Architecture Decisions

- **Single cleanup slice**: The frontend cleanup is smaller than the backend cleanup (8 files to delete, ~10 files to update). It fits within a single slice.
- **Order after feature epics**: This runs after Epics 423 and 424 to ensure the replacement components are stable before deleting the old ones.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/billing/page.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/layout.tsx`
