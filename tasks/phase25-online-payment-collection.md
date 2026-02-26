# Phase 25 — Online Payment Collection

Phase 25 wires up real online payment collection for the DocTeams platform. Tenants connect their own Stripe or PayFast account (BYOAK model), invoices get "Pay Now" links on send, clients pay via hosted checkout pages, and webhooks automatically mark invoices as PAID. The legacy `PaymentProvider` interface is replaced by a `PaymentGateway` port aligned with the integration port pattern (Phase 21). A new `PaymentEvent` entity provides full audit trail of payment activity.

**Architecture doc**: `architecture/phase25-online-payment-collection.md`

**ADRs**:
- [ADR-098](../adr/ADR-098-payment-gateway-interface-design.md) — PaymentGateway Interface Design (consolidate PaymentProvider into port pattern)
- [ADR-099](../adr/ADR-099-webhook-tenant-identification-payments.md) — Webhook Tenant Identification for Payments (tenant schema in PSP metadata)
- [ADR-100](../adr/ADR-100-payment-link-lifecycle.md) — Payment Link Lifecycle (generate on SENT, manual refresh)

**Migration**: V42 tenant — `payment_events` table + 3 columns on `invoices`. V9 global — payment fields on portal read-model.

**Dependencies on prior phases**: Phase 6 (Audit), Phase 6.5 (Notifications), Phase 7 (Portal Backend), Phase 10 (Invoicing), Phase 12 (OrgSettings branding), Phase 21 (Integration Ports), Phase 22 (Portal Frontend), Phase 24 (Email Delivery).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 174 | PaymentGateway Port + NoOp Adapter + InvoiceService Migration | Backend | -- | M | 174A, 174B | **Done** |
| 175 | PaymentEvent Entity + Migration + Invoice Extension | Backend | 174 | M | 175A, 175B | **Done** |
| 176 | Stripe Adapter | Backend | 174 | M | 176A, 176B | **Done** |
| 177 | PayFast Adapter | Backend | 174 | M | 177A, 177B | **Done** |
| 178 | Payment Link Generation + Webhook Reconciliation | Backend | 174, 175 | L | 178A, 178B | |
| 179 | Portal Payment Flow + Read-Model Extension | Both | 178 | M | 179A, 179B | |
| 180 | Integration Settings UI + Invoice Payment UX | Frontend | 178 | M | 180A, 180B | |

---

## Dependency Graph

```
[E174A PaymentGateway Port + VOs]
        |
[E174B NoOp Adapter + InvoiceService Migration]
        |
        +------------------+------------------+
        |                  |                  |
[E175A PaymentEvent     [E176A Stripe      [E177A PayFast
 Entity + Migration]     Session + Webhook]  Session + Webhook]
        |                  |                  |
[E175B Invoice Ext +    [E176B Stripe      [E177B PayFast
 Payment Events API]     Tests + ConnTest]   Tests + ConnTest]
        |                  |                  |
        +------------------+------------------+
                           |
              [E178A PaymentLinkService + WebhookController]
                           |
              [E178B PaymentReconciliationService + Audit + Notifications]
                           |
          +----------------+----------------+
          |                                 |
  [E179A Portal Backend               [E180A Integration
   Extension + PayNow]                 Settings PAYMENT Card]
          |                                 |
  [E179B Portal Success               [E180B Invoice Detail
   + Cancel Pages]                     Payment UX]
```

**Parallel opportunities**:
- Epics 175, 176, and 177 are fully independent after 174B -- can start in parallel immediately.
- After 178B completes: Epics 179 and 180 can start in parallel.
- 179A and 179B are sequential (portal backend before portal frontend pages).
- 180A and 180B are sequential (integration card before invoice UX).

---

## Implementation Order

### Stage 0: Port Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 174 | 174A | `PaymentGateway` port interface + 6 value objects + `PaymentStatus` enum + `IntegrationRegistry.resolveBySlug()` | **Done** (PR #362) |
| 0b | 174 | 174B | `NoOpPaymentGateway` + delete `PaymentProvider`/`MockPaymentProvider` + migrate `InvoiceService` to registry resolution + `IntegrationService.testConnection(PAYMENT)` | **Done** (PR #363) |

### Stage 1: Entity + Adapters (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 175 | 175A | V42 migration + `PaymentEvent` entity + `PaymentEventStatus` enum + `PaymentEventRepository` + repository tests | **Done** (PR #364) |
| 1b (parallel) | 176 | 176A | `StripePaymentGateway` session creation + webhook handling + `stripe-java` Maven dependency | **Done** (PR #366) |
| 1c (parallel) | 177 | 177A | `PayFastPaymentGateway` session creation + ITN webhook handling + signature generation | **Done** (PR #368) |

### Stage 2: Invoice extension + adapter completion (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 175 | 175B | Invoice entity extension (3 columns) + `GET /api/invoices/{id}/payment-events` endpoint + manual payment writes PaymentEvent | **Done** (PR #365) |
| 2b (parallel) | 176 | 176B | Stripe status query + connection test + session expiry + comprehensive tests | **Done** (PR #367) |
| 2c (parallel) | 177 | 177B | PayFast IP validation + server confirmation + connection test + comprehensive tests | **Done** (PR #369) |

### Stage 3: Orchestration (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 178 | 178A | `PaymentLinkService` + `PaymentWebhookController` + `POST /api/invoices/{id}/refresh-payment-link` + SecurityConfig webhook permit | **Done** (PR #370) |
| 3b | 178 | 178B | `PaymentReconciliationService` + `InvoiceService.send()` trigger + session cancellation on manual payment + audit events + notifications | |

### Stage 4: Frontend (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 179 | 179A | Portal backend: `payment-status` endpoint + `PortalInvoiceView` extension + V9 global migration + invoice sync handler update | |
| 4b (parallel) | 180 | 180A | Stripe + PayFast configuration fields on PAYMENT integration card + test connection | |

### Stage 5: Frontend completion (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 179 | 179B | Portal: "Pay Now" button + payment-success page + payment-cancelled page + status polling | |
| 5b (parallel) | 180 | 180B | Invoice detail: payment link section + copy link + regenerate button + payment event history + invoice list indicator + email template "Pay Now" button | |

### Timeline

```
Stage 0: [174A] --> [174B]                                          (sequential)
Stage 1: [175A] // [176A] // [177A]                                 (parallel, after 174B)
Stage 2: [175B] // [176B] // [177B]                                 (parallel, after respective Stage 1)
Stage 3: [178A] --> [178B]                                          (sequential, after 175B + at least one adapter)
Stage 4: [179A] // [180A]                                           (parallel, after 178B)
Stage 5: [179B] // [180B]                                           (parallel, after respective Stage 4)
```

**Critical path**: 174A -> 174B -> 175A -> 175B -> 178A -> 178B -> 180A -> 180B

---

## Epic 174: PaymentGateway Port + NoOp Adapter + InvoiceService Migration

**Goal**: Establish the `PaymentGateway` port interface in `integration/payment/`, create the `NoOpPaymentGateway` default adapter, delete the legacy `PaymentProvider`/`MockPaymentProvider`, and migrate `InvoiceService` to resolve payment gateways via `IntegrationRegistry`.

**References**: Architecture doc Sections 25.3 (port interface design), 25.3.2 (PaymentGateway interface), 25.3.3 (value objects), 25.3.4 (NoOpPaymentGateway), 25.3.5 (InvoiceService migration), 25.3.6 (IntegrationService update). ADR-098.

**Dependencies**: None -- this is the foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **174A** | 174.1--174.7 | `PaymentGateway` port interface + `CheckoutRequest`, `CreateSessionResult`, `WebhookResult`, `PaymentStatus` enum + relocate `PaymentRequest`/`PaymentResult` to `integration/payment/` + `IntegrationRegistry.resolveBySlug()` method + unit tests. ~8 new files, ~1 modified file. Backend only. | **Done** (PR #362) |
| **174B** | 174.8--174.15 | `NoOpPaymentGateway` adapter + delete `PaymentProvider`/`MockPaymentProvider` + migrate `InvoiceService` to registry resolution + `IntegrationService.testConnection(PAYMENT)` update + fix all broken tests. ~1 new file, ~5 modified files, ~3 deleted files. Backend only. | **Done** (PR #363) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 174.1 | Create `PaymentGateway` port interface | 174A | | New file: `integration/payment/PaymentGateway.java`. Methods: `providerId()`, `createCheckoutSession(CheckoutRequest)`, `handleWebhook(String, Map)`, `queryPaymentStatus(String)`, `recordManualPayment(PaymentRequest)`, `default expireSession(String)`, `testConnection()`. Pattern: `integration/email/EmailProvider.java`. |
| 174.2 | Create `CheckoutRequest` value object | 174A | | New file: `integration/payment/CheckoutRequest.java`. Java record: `invoiceId`, `invoiceNumber`, `amount`, `currency`, `customerEmail`, `customerName`, `successUrl`, `cancelUrl`, `metadata` (Map). See architecture doc Section 25.3.3. |
| 174.3 | Create `CreateSessionResult` value object | 174A | | New file: `integration/payment/CreateSessionResult.java`. Java record with static factories: `notSupported()`, `success()`, `failure()`. See architecture doc Section 25.3.3. |
| 174.4 | Create `WebhookResult` value object | 174A | | New file: `integration/payment/WebhookResult.java`. Java record: `verified`, `eventType`, `sessionId`, `paymentReference`, `status`, `metadata`. |
| 174.5 | Create `PaymentStatus` enum | 174A | | New file: `integration/payment/PaymentStatus.java`. Values: `PENDING`, `COMPLETED`, `FAILED`, `EXPIRED`, `CANCELLED`. |
| 174.6 | Relocate `PaymentRequest` and `PaymentResult` to `integration/payment/` | 174A | | New files: `integration/payment/PaymentRequest.java`, `integration/payment/PaymentResult.java`. Copy shapes from `invoice/PaymentRequest.java` and `invoice/PaymentResult.java`. Do NOT delete originals yet (174B handles deletion + import updates). |
| 174.7 | Add `resolveBySlug()` method to `IntegrationRegistry` | 174A | | Modify: `integration/IntegrationRegistry.java`. New method: `<T> T resolveBySlug(IntegrationDomain domain, String slug, Class<T> portInterface)`. Looks up adapter from internal `adapterMap` by domain + slug. Throws `IllegalArgumentException` if not found. Used by webhook controller. Write unit test in existing `IntegrationRegistryTest` or new test. ~3 tests. |
| 174.8 | Create `NoOpPaymentGateway` adapter | 174B | 174.1 | New file: `integration/payment/NoOpPaymentGateway.java`. `@IntegrationAdapter(domain = PAYMENT, slug = "noop")`. `recordManualPayment()` returns success with `MANUAL-{uuid}` reference. `createCheckoutSession()` returns `notSupported()`. `testConnection()` returns success. Pattern: `integration/email/NoOpEmailProvider.java`. |
| 174.9 | Delete `PaymentProvider` interface | 174B | 174.8 | Delete: `invoice/PaymentProvider.java`. |
| 174.10 | Delete `MockPaymentProvider` | 174B | 174.8 | Delete: `invoice/MockPaymentProvider.java`. Remove `payment.provider=mock` property from `application.yml` and test configs if present. |
| 174.11 | Delete original `PaymentRequest`/`PaymentResult` from `invoice/` | 174B | 174.6 | Delete: `invoice/PaymentRequest.java`, `invoice/PaymentResult.java`. |
| 174.12 | Migrate `InvoiceService` to registry resolution | 174B | 174.8, 174.9 | Modify: `invoice/InvoiceService.java`. Replace `PaymentProvider` constructor injection with `IntegrationRegistry` injection. Add private `resolvePaymentGateway()` method. Update `recordPayment()` to call `gateway.recordManualPayment()`. Update all imports from `invoice.PaymentRequest` to `integration.payment.PaymentRequest`. |
| 174.13 | Update `IntegrationService.testConnection()` for PAYMENT | 174B | 174.8 | Modify: `integration/IntegrationService.java`. In the `testConnection()` switch case for PAYMENT, change from `throw` to `integrationRegistry.resolve(domain, PaymentGateway.class).testConnection()`. |
| 174.14 | Write unit tests for `NoOpPaymentGateway` | 174B | 174.8 | New file: `integration/payment/NoOpPaymentGatewayTest.java`. Tests: (1) createCheckoutSession_returns_notSupported, (2) recordManualPayment_returns_success, (3) recordManualPayment_generates_MANUAL_prefix_reference, (4) testConnection_returns_success, (5) queryPaymentStatus_returns_PENDING, (6) handleWebhook_returns_unverified. ~6 tests. |
| 174.15 | Fix all broken tests after migration | 174B | 174.12 | Modify: any test files that inject `PaymentProvider` or `MockPaymentProvider`. Update imports to `integration.payment.PaymentGateway` / `PaymentRequest` / `PaymentResult`. Verify all existing invoice tests pass with `NoOpPaymentGateway` as default. |

### Key Files

**Slice 174A -- Create:**
- `backend/src/main/java/.../integration/payment/PaymentGateway.java`
- `backend/src/main/java/.../integration/payment/CheckoutRequest.java`
- `backend/src/main/java/.../integration/payment/CreateSessionResult.java`
- `backend/src/main/java/.../integration/payment/WebhookResult.java`
- `backend/src/main/java/.../integration/payment/PaymentStatus.java`
- `backend/src/main/java/.../integration/payment/PaymentRequest.java`
- `backend/src/main/java/.../integration/payment/PaymentResult.java`

**Slice 174A -- Modify:**
- `backend/src/main/java/.../integration/IntegrationRegistry.java`

**Slice 174B -- Create:**
- `backend/src/main/java/.../integration/payment/NoOpPaymentGateway.java`
- `backend/src/test/java/.../integration/payment/NoOpPaymentGatewayTest.java`

**Slice 174B -- Delete:**
- `backend/src/main/java/.../invoice/PaymentProvider.java`
- `backend/src/main/java/.../invoice/MockPaymentProvider.java`
- `backend/src/main/java/.../invoice/PaymentRequest.java`
- `backend/src/main/java/.../invoice/PaymentResult.java`

**Slice 174B -- Modify:**
- `backend/src/main/java/.../invoice/InvoiceService.java`
- `backend/src/main/java/.../integration/IntegrationService.java`
- Any existing test files referencing `PaymentProvider` / `MockPaymentProvider`

**Read for context:**
- `integration/email/EmailProvider.java` -- Port interface pattern
- `integration/email/NoOpEmailProvider.java` -- NoOp adapter pattern
- `integration/IntegrationRegistry.java` -- Resolution logic
- `integration/IntegrationAdapter.java` -- Annotation
- `invoice/InvoiceService.java` -- Current `PaymentProvider` injection

### Architecture Decisions

- **Single interface consolidation (ADR-098)**: `PaymentProvider` deleted, `PaymentGateway` is the sole port interface for the PAYMENT domain. All adapters (noop, stripe, payfast) implement it.
- **`resolveBySlug()` for webhooks**: Webhook controller needs to resolve a specific adapter by slug (not by tenant config). This new method complements the existing `resolve()` which uses tenant config.
- **`NoOpPaymentGateway` with slug `"noop"`**: Fixes the existing `IntegrationDomain.PAYMENT` `defaultSlug = "noop"` mismatch where no `"noop"` adapter existed.
- **Value objects relocated to `integration/payment/`**: `PaymentRequest` and `PaymentResult` move from `invoice/` to follow the port pattern (all value objects in the port package).

---

## Epic 175: PaymentEvent Entity + Migration + Invoice Extension

**Goal**: Create the `PaymentEvent` entity for tracking payment activity, write the V42 migration, extend the Invoice entity with payment link fields, and wire manual payment recording to create PaymentEvent records.

**References**: Architecture doc Sections 25.2.1 (PaymentEvent entity), 25.2.2 (Invoice extension), 25.2.3 (PaymentEventStatus), 25.14 (migration SQL), 25.9 (manual payment coexistence).

**Dependencies**: Epic 174 (uses `PaymentGateway` value types for consistency).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **175A** | 175.1--175.7 | V42 migration (`payment_events` table + invoice columns) + `PaymentEvent` entity + `PaymentEventStatus` enum + `PaymentEventRepository` + repository integration tests. ~5 new files, ~1 migration file. Backend only. | **Done** (PR #364) |
| **175B** | 175.8--175.14 | Invoice entity extension (3 fields + getters/setters) + `PaymentEventResponse` DTO + `GET /api/invoices/{id}/payment-events` endpoint + `InvoiceService.recordPayment()` writes manual PaymentEvent + integration tests. ~2 new files, ~3 modified files. Backend only. | **Done** (PR #365) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 175.1 | Create V42 tenant migration | 175A | | New file: `db/migration/tenant/V42__online_payment_collection.sql`. (1) ALTER `invoices` ADD `payment_session_id VARCHAR(255)`, `payment_url VARCHAR(1024)`, `payment_destination VARCHAR(50) NOT NULL DEFAULT 'OPERATING'`. (2) CREATE `payment_events` table with all columns per architecture doc Section 25.14. (3) Create 3 indexes on `payment_events` + 1 partial index on `invoices.payment_session_id`. Note: architecture doc says V41 but V41 is taken by Phase 24. |
| 175.2 | Create `PaymentEventStatus` enum | 175A | | New file: `invoice/PaymentEventStatus.java`. Values: `CREATED`, `PENDING`, `COMPLETED`, `FAILED`, `EXPIRED`, `CANCELLED`. |
| 175.3 | Create `PaymentEvent` entity | 175A | 175.1, 175.2 | New file: `invoice/PaymentEvent.java`. `@Entity`, `@Table(name = "payment_events")`. Fields per architecture doc Section 25.2.1. Constructor for creating new events. `updateStatus(PaymentEventStatus)` method. `@PrePersist`/`@PreUpdate` for timestamps. Pattern: standard entity, no multitenancy boilerplate. |
| 175.4 | Create `PaymentEventRepository` | 175A | 175.3 | New file: `invoice/PaymentEventRepository.java`. `JpaRepository<PaymentEvent, UUID>`. Methods: `List<PaymentEvent> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId)`, `Optional<PaymentEvent> findBySessionIdAndStatus(String sessionId, PaymentEventStatus status)`, `boolean existsBySessionIdAndStatus(String sessionId, PaymentEventStatus status)`. |
| 175.5 | Write repository integration tests | 175A | 175.4 | New file: `invoice/PaymentEventRepositoryIntegrationTest.java`. Tests: (1) save_and_findById, (2) findByInvoiceIdOrderByCreatedAtDesc_returns_ordered, (3) findBySessionIdAndStatus_finds_match, (4) findBySessionIdAndStatus_returns_empty_when_no_match, (5) existsBySessionIdAndStatus. ~5 tests. Pattern: existing repository tests (e.g., `InvoiceRepositoryIntegrationTest`). |
| 175.6 | Write entity unit tests | 175A | 175.3 | New file: `invoice/PaymentEventTest.java`. Tests: (1) constructor_sets_fields, (2) updateStatus_changes_status_and_updatedAt, (3) constructor_sets_default_timestamps. ~3 tests. |
| 175.7 | Verify migration runs on Testcontainers | 175A | 175.1 | In repository test: verify `@Sql` or Flyway runs V42 successfully. Table exists, columns correct. |
| 175.8 | Extend `Invoice` entity with payment fields | 175B | 175.1 | Modify: `invoice/Invoice.java`. Add fields: `paymentSessionId` (String, nullable), `paymentUrl` (String, nullable), `paymentDestination` (String, not null, default "OPERATING"). Add getters/setters. |
| 175.9 | Create `PaymentEventResponse` DTO | 175B | 175.3 | New file: `invoice/dto/PaymentEventResponse.java`. Java record: `id`, `providerSlug`, `sessionId`, `paymentReference`, `status`, `amount`, `currency`, `paymentDestination`, `createdAt`, `updatedAt`. Static factory `from(PaymentEvent)`. |
| 175.10 | Add `GET /api/invoices/{id}/payment-events` endpoint | 175B | 175.9, 175.4 | Modify: `invoice/InvoiceController.java`. New method: `getPaymentEvents(@PathVariable UUID id)`. Returns `List<PaymentEventResponse>`. Uses `PaymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc()`. Verify invoice exists and member has access. |
| 175.11 | Wire manual payment to create PaymentEvent | 175B | 175.4 | Modify: `invoice/InvoiceService.java`. In `recordPayment()`: after marking invoice as PAID, create a `PaymentEvent` with `providerSlug = "manual"`, `status = COMPLETED`, `amount = invoice.getTotal()`, `currency = invoice.getCurrency()`. Save via repository. |
| 175.12 | Extend `InvoiceResponse` with payment fields | 175B | 175.8 | Modify: `invoice/dto/InvoiceResponse.java` (or wherever the response DTO lives). Add `paymentSessionId`, `paymentUrl`, `paymentDestination`. Update `from(Invoice)` factory. |
| 175.13 | Write integration tests for payment events endpoint | 175B | 175.10 | New file: `invoice/PaymentEventsControllerIntegrationTest.java`. Tests: (1) getPaymentEvents_returns_empty_for_new_invoice, (2) getPaymentEvents_returns_events_after_manual_payment, (3) getPaymentEvents_returns_403_for_non_member, (4) getPaymentEvents_returns_404_for_unknown_invoice. ~4 tests. |
| 175.14 | Write integration test for manual payment event creation | 175B | 175.11 | In existing `InvoiceServiceIntegrationTest` or new test: verify `recordPayment()` creates a `PaymentEvent` with `providerSlug = "manual"`, `status = COMPLETED`. ~2 tests. |

### Key Files

**Slice 175A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V42__online_payment_collection.sql`
- `backend/src/main/java/.../invoice/PaymentEvent.java`
- `backend/src/main/java/.../invoice/PaymentEventStatus.java`
- `backend/src/main/java/.../invoice/PaymentEventRepository.java`
- `backend/src/test/java/.../invoice/PaymentEventRepositoryIntegrationTest.java`
- `backend/src/test/java/.../invoice/PaymentEventTest.java`

**Slice 175B -- Create:**
- `backend/src/main/java/.../invoice/dto/PaymentEventResponse.java`
- `backend/src/test/java/.../invoice/PaymentEventsControllerIntegrationTest.java`

**Slice 175B -- Modify:**
- `backend/src/main/java/.../invoice/Invoice.java`
- `backend/src/main/java/.../invoice/InvoiceController.java`
- `backend/src/main/java/.../invoice/InvoiceService.java`
- `backend/src/main/java/.../invoice/dto/InvoiceResponse.java` (or equivalent)

**Read for context:**
- `invoice/Invoice.java` -- Entity to extend
- `invoice/InvoiceController.java` -- Endpoint patterns
- `invoice/InvoiceService.java` -- `recordPayment()` method to modify
- `invoice/dto/` -- Existing DTO patterns

### Architecture Decisions

- **V42 not V41**: Architecture doc references V41 but that is taken by Phase 24 (`email_delivery_log`). Phase 25 uses V42.
- **PaymentEvent in `invoice/` package**: Follows "organize by feature" convention. Payment events are intrinsically tied to invoices.
- **`paymentDestination` default `OPERATING`**: Seam for future trust accounting. No behavior change now.
- **Manual payment creates PaymentEvent**: Provides a complete audit trail even for offline payments.

---

## Epic 176: Stripe Adapter

**Goal**: Implement the `StripePaymentGateway` adapter with session creation, webhook handling, status query, session expiry, connection test, and per-request API key resolution.

**References**: Architecture doc Sections 25.4 (Stripe adapter), 25.4.1--25.4.6. ADR-098, ADR-099.

**Dependencies**: Epic 174 (implements `PaymentGateway` interface).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **176A** | 176.1--176.7 | `StripePaymentGateway` with `createCheckoutSession()` + `handleWebhook()` + `stripe-java` Maven dependency + `resolveApiKey()` via SecretStore + unit tests with mocked Stripe SDK. ~2 new files, ~1 modified file. Backend only. | **Done** (PR #366) |
| **176B** | 176.8--176.13 | `queryPaymentStatus()` + `testConnection()` + `expireSession()` + `recordManualPayment()` + currency conversion helper + comprehensive tests. ~1 modified file, ~1 new test file. Backend only. | **Done** (PR #367) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 176.1 | Add `stripe-java` Maven dependency | 176A | | Modify: `backend/pom.xml`. Add `com.stripe:stripe-java:28.2.0`. |
| 176.2 | Create `StripePaymentGateway` class skeleton | 176A | 174.1 | New file: `integration/payment/StripePaymentGateway.java`. `@Component`, `@IntegrationAdapter(domain = PAYMENT, slug = "stripe")`. Inject `SecretStore`, `IntegrationRegistry` (or `OrgIntegrationRepository`). Private `resolveApiKey()` and `resolveWebhookSecret()` methods using `SecretStore.getSecret()`. Pattern: `SmtpEmailProvider` for `SecretStore` usage. |
| 176.3 | Implement `createCheckoutSession()` | 176A | 176.2 | In `StripePaymentGateway`. Build `SessionCreateParams` with mode PAYMENT, line item from invoice amount (smallest currency unit conversion), `client_reference_id`, `customer_email`, `success_url`/`cancel_url`, metadata (`tenantSchema`, `invoiceId`). Use per-request `RequestOptions.builder().setApiKey(apiKey).build()`. Catch `StripeException`. See architecture doc Section 25.4.2. |
| 176.4 | Implement `toSmallestUnit()` currency helper | 176A | | In `StripePaymentGateway` (private method). Converts `BigDecimal` amount to `long` smallest unit. For ZAR/USD/EUR/GBP: multiply by 100. Handle zero-decimal currencies (JPY). |
| 176.5 | Implement `handleWebhook()` | 176A | 176.2 | In `StripePaymentGateway`. Extract `Stripe-Signature` header, call `Webhook.constructEvent()` with webhook signing secret. Handle `checkout.session.completed` (extract `payment_intent`, metadata, return COMPLETED) and `checkout.session.expired` (return EXPIRED). Catch `SignatureVerificationException`. See architecture doc Section 25.4.3. |
| 176.6 | Write unit tests for session creation | 176A | 176.3, 176.4 | New file: `integration/payment/StripePaymentGatewayTest.java`. Mock Stripe SDK calls. Tests: (1) createCheckoutSession_builds_correct_params, (2) createCheckoutSession_includes_metadata_tenantSchema, (3) createCheckoutSession_converts_ZAR_to_cents, (4) createCheckoutSession_handles_stripe_exception, (5) toSmallestUnit_ZAR, (6) toSmallestUnit_JPY. ~6 tests. |
| 176.7 | Write unit tests for webhook handling | 176A | 176.5 | In `StripePaymentGatewayTest.java`. Tests: (1) handleWebhook_completed_returns_payment_reference, (2) handleWebhook_expired_returns_expired_status, (3) handleWebhook_invalid_signature_returns_unverified, (4) handleWebhook_unknown_event_type_returns_unverified. ~4 tests. |
| 176.8 | Implement `queryPaymentStatus()` | 176B | 176.2 | In `StripePaymentGateway`. Call `Session.retrieve(sessionId, requestOptions)`. Map `session.getStatus()`: `"complete"` -> `COMPLETED`, `"expired"` -> `EXPIRED`, `"open"` -> `PENDING`. Catch `StripeException`. See architecture doc Section 25.4.4. |
| 176.9 | Implement `testConnection()` | 176B | 176.2 | In `StripePaymentGateway`. Call `Balance.retrieve(requestOptions)`. Success -> `ConnectionTestResult(true, "stripe", null)`. Catch `StripeException` -> `ConnectionTestResult(false, "stripe", e.getMessage())`. See architecture doc Section 25.4.6. |
| 176.10 | Implement `expireSession()` | 176B | 176.2 | In `StripePaymentGateway`. Call `Session.expire(sessionId, requestOptions)`. Catch and log `StripeException` (no-throw). |
| 176.11 | Implement `recordManualPayment()` | 176B | | In `StripePaymentGateway`. Same as `NoOpPaymentGateway` -- generates `MANUAL-{uuid}` reference. No Stripe API call needed for manual payments. |
| 176.12 | Write unit tests for remaining methods | 176B | 176.8-176.11 | In `StripePaymentGatewayTest.java`. Tests: (1) queryPaymentStatus_complete_returns_COMPLETED, (2) queryPaymentStatus_expired_returns_EXPIRED, (3) queryPaymentStatus_open_returns_PENDING, (4) testConnection_success, (5) testConnection_failure, (6) expireSession_calls_stripe, (7) recordManualPayment_returns_success. ~7 tests. |
| 176.13 | Add Stripe application properties | 176B | | Modify: `application.yml`. Add `docteams.stripe.webhook-url` placeholder. No secret keys in config (all via `SecretStore`). |

### Key Files

**Slice 176A -- Create:**
- `backend/src/main/java/.../integration/payment/StripePaymentGateway.java`
- `backend/src/test/java/.../integration/payment/StripePaymentGatewayTest.java`

**Slice 176A -- Modify:**
- `backend/pom.xml`

**Slice 176B -- Modify:**
- `backend/src/main/java/.../integration/payment/StripePaymentGateway.java`
- `backend/src/main/resources/application.yml`

**Read for context:**
- `integration/email/SmtpEmailProvider.java` -- SecretStore usage pattern
- `integration/secret/SecretStore.java` -- Secret resolution
- `integration/IntegrationRegistry.java` -- Adapter resolution
- `invoice/Invoice.java` -- Fields to reference in checkout request

### Architecture Decisions

- **Per-request API key**: No global `Stripe.apiKey`. Each call uses `RequestOptions` with the tenant's key. Multi-tenant safe.
- **Mocked Stripe SDK in tests**: Tests mock `Session.create()` / `Session.retrieve()` / `Webhook.constructEvent()`. No live Stripe calls.
- **Currency smallest unit conversion**: Stripe expects amounts in smallest units (cents for ZAR/USD). `toSmallestUnit()` handles zero-decimal currencies.

---

## Epic 177: PayFast Adapter

**Goal**: Implement the `PayFastPaymentGateway` adapter with redirect URL construction, MD5 signature generation, ITN webhook validation (IP + signature + server confirmation), and sandbox/production URL switching.

**References**: Architecture doc Sections 25.5 (PayFast adapter), 25.5.1--25.5.6. ADR-099.

**Dependencies**: Epic 174 (implements `PaymentGateway` interface).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **177A** | 177.1--177.7 | `PayFastPaymentGateway` with `createCheckoutSession()` (redirect URL construction + MD5 signature) + `handleWebhook()` (ITN parsing + signature verification). ~2 new files. Backend only. | **Done** (PR #368) |
| **177B** | 177.8--177.14 | ITN IP validation + server confirmation via `RestClient` + `queryPaymentStatus()` + `testConnection()` + `recordManualPayment()` + sandbox toggle + comprehensive tests. ~1 modified file, ~1 test file. Backend only. | **Done** (PR #369) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 177.1 | Create `PayFastPaymentGateway` class skeleton | 177A | 174.1 | New file: `integration/payment/PayFastPaymentGateway.java`. `@Component`, `@IntegrationAdapter(domain = PAYMENT, slug = "payfast")`. Inject `SecretStore`, `RestClient.Builder`. Private `resolveConfig()` returns merchant ID, key, passphrase. `@Value("${docteams.payfast.sandbox:true}")` for sandbox toggle. |
| 177.2 | Implement `generateSignature()` helper | 177A | 177.1 | Private method in `PayFastPaymentGateway`. Takes parameter map + passphrase. URL-encode all params in alphabetical order, append passphrase, MD5 hash. Return hex string. Per PayFast documentation. |
| 177.3 | Implement `createCheckoutSession()` | 177A | 177.1, 177.2 | Build payment data map: `merchant_id`, `merchant_key`, `return_url`, `cancel_url`, `notify_url`, `amount`, `item_name`, `email_address`, `custom_str1` (tenantSchema), `custom_str2` (invoiceId). Generate signature. Construct redirect URL. Generate local session ID (UUID). Return `CreateSessionResult.success()`. See architecture doc Section 25.5.2. |
| 177.4 | Implement `parseFormData()` helper | 177A | | Private method. Parses URL-encoded form data from ITN body into `Map<String, String>`. |
| 177.5 | Implement `handleWebhook()` -- signature verification | 177A | 177.2, 177.4 | Parse ITN params. Verify signature: recalculate from all params except `signature` field, compare with received `signature`. Map `payment_status`: `COMPLETE` -> `COMPLETED`, `FAILED` -> `FAILED`, `PENDING` -> `PENDING`. Extract `custom_str1` (tenant), `custom_str2` (invoiceId), `pf_payment_id` (reference). Return `WebhookResult`. See architecture doc Section 25.5.3. |
| 177.6 | Write unit tests for signature generation | 177A | 177.2 | New file: `integration/payment/PayFastPaymentGatewayTest.java`. Tests: (1) generateSignature_matches_known_vector, (2) generateSignature_url_encodes_special_chars, (3) generateSignature_alphabetical_order. ~3 tests. |
| 177.7 | Write unit tests for session creation and webhook parsing | 177A | 177.3, 177.5 | In `PayFastPaymentGatewayTest.java`. Tests: (1) createCheckoutSession_includes_custom_str1_tenant, (2) createCheckoutSession_uses_sandbox_url, (3) createCheckoutSession_formats_amount_two_decimals, (4) handleWebhook_valid_signature_returns_verified, (5) handleWebhook_invalid_signature_returns_unverified, (6) handleWebhook_maps_COMPLETE_to_COMPLETED. ~6 tests. |
| 177.8 | Implement ITN IP validation | 177B | 177.5 | Private method `isPayFastIp(String sourceIp)`. Check against PayFast IP range `197.97.145.144/28`. Extract from `X-Forwarded-For` header. Production and sandbox use the same range. |
| 177.9 | Implement ITN server confirmation | 177B | 177.1 | Private method `confirmWithPayFast(String payload)`. POST the ITN data to `https://www.payfast.co.za/eng/query/validate` (or sandbox equivalent). Verify response body is `"VALID"`. Use `RestClient`. |
| 177.10 | Wire IP validation and server confirmation into `handleWebhook()` | 177B | 177.8, 177.9 | Modify `handleWebhook()` to call `isPayFastIp()` first, then signature verification, then `confirmWithPayFast()`. Return unverified if any step fails. |
| 177.11 | Implement `queryPaymentStatus()` | 177B | | Returns `PaymentStatus.PENDING`. PayFast has no session status API. Caller checks `PaymentEvent` table. |
| 177.12 | Implement `testConnection()` and `recordManualPayment()` | 177B | | `testConnection()`: returns success with advisory message. `recordManualPayment()`: same as `NoOpPaymentGateway`. |
| 177.13 | Add PayFast application properties | 177B | | Modify: `application.yml`. Add `docteams.payfast.sandbox: true`. |
| 177.14 | Write comprehensive tests | 177B | 177.8-177.12 | In `PayFastPaymentGatewayTest.java`. Tests: (1) isPayFastIp_valid_ip, (2) isPayFastIp_invalid_ip, (3) confirmWithPayFast_valid_response (mock RestClient), (4) confirmWithPayFast_invalid_response, (5) handleWebhook_rejects_non_payfast_ip, (6) handleWebhook_rejects_failed_server_confirmation, (7) queryPaymentStatus_returns_PENDING, (8) testConnection_returns_advisory. ~8 tests. |

### Key Files

**Slice 177A -- Create:**
- `backend/src/main/java/.../integration/payment/PayFastPaymentGateway.java`
- `backend/src/test/java/.../integration/payment/PayFastPaymentGatewayTest.java`

**Slice 177B -- Modify:**
- `backend/src/main/java/.../integration/payment/PayFastPaymentGateway.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/.../integration/payment/PayFastPaymentGatewayTest.java`

**Read for context:**
- `integration/email/SmtpEmailProvider.java` -- SecretStore usage pattern
- `integration/secret/SecretStore.java` -- Secret resolution API

### Architecture Decisions

- **No external SDK**: PayFast uses standard HTTP (`RestClient`) and MD5 hashing. No third-party library needed.
- **4-step ITN validation**: IP check -> signature verification -> data verification (implicit in amount matching) -> server confirmation POST. All 4 must pass for `verified = true`.
- **Local session ID**: PayFast has no "session" concept. `createCheckoutSession()` generates a UUID for tracking. The redirect URL itself is the session.
- **Sandbox toggle**: `docteams.payfast.sandbox` defaults to `true`. Production switches URL and potentially IP range validation.

---

## Epic 178: Payment Link Generation + Webhook Reconciliation

**Goal**: Build the orchestration layer: `PaymentLinkService` for link generation/refresh, `PaymentWebhookController` for receiving PSP callbacks, `PaymentReconciliationService` for processing webhook results, and wire everything into `InvoiceService`.

**References**: Architecture doc Sections 25.6 (PaymentLinkService), 25.7 (webhook reconciliation), 25.9 (manual payment coexistence), 25.13 (audit events + notifications), 25.16 (permissions).

**Dependencies**: Epics 174 (port interface), 175 (PaymentEvent entity, Invoice extension).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **178A** | 178.1--178.8 | `PaymentLinkService` (generate + refresh + cancel) + `PaymentWebhookController` + `POST /api/invoices/{id}/refresh-payment-link` + SecurityConfig webhook permit + `IntegrationRegistry.resolveBySlug()` wiring + integration tests. ~4 new files, ~2 modified files. Backend only. | **Done** (PR #370) |
| **178B** | 178.9--178.16 | `PaymentReconciliationService` + `InvoiceService.send()` trigger + session cancellation on manual payment + audit events (7 types) + notifications (3 types) + idempotency + end-to-end integration tests. ~2 new files, ~2 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 178.1 | Create `PaymentLinkService` | 178A | 174.1, 175.3 | New file: `invoice/PaymentLinkService.java`. `@Service`. Methods: `generatePaymentLink(Invoice)`, `refreshPaymentLink(Invoice)`, `cancelActiveSession(Invoice)`. Inject `IntegrationRegistry`, `InvoiceRepository`, `PaymentEventRepository`, `AuditService`. Builds `CheckoutRequest` with `RequestScopes.TENANT_ID.get()` in metadata. See architecture doc Section 25.6.1. |
| 178.2 | Implement URL builders in `PaymentLinkService` | 178A | 178.1 | Private methods: `buildSuccessUrl(UUID invoiceId)`, `buildCancelUrl(UUID invoiceId)`. Read `docteams.app.portal-base-url` from config. Success URL: `{portalBase}/invoices/{id}/payment-success`. Cancel URL: `{portalBase}/invoices/{id}/payment-cancelled`. |
| 178.3 | Create `PaymentWebhookController` | 178A | | New file: `integration/payment/PaymentWebhookController.java`. `@RestController`, `@RequestMapping("/api/webhooks/payment")`. Method: `handleWebhook(@PathVariable provider, @RequestBody payload, @RequestHeader headers)`. Extract tenant schema from payload (per ADR-099), set `ScopedValue`, resolve adapter by slug, call `handleWebhook()`. Always return 200. See architecture doc Section 25.7.1. |
| 178.4 | Implement `extractTenantSchema()` in webhook controller | 178A | 178.3 | Private method. For Stripe: parse raw JSON, extract `data.object.metadata.tenantSchema`. For PayFast: parse form data, extract `custom_str1`. Use Jackson `ObjectMapper` for Stripe JSON parsing. |
| 178.5 | Add webhook path to SecurityConfig `permitAll()` | 178A | | Modify: `security/SecurityConfig.java`. Add `/api/webhooks/payment/**` to the existing `permitAll()` list, alongside Clerk webhooks. |
| 178.6 | Add `POST /api/invoices/{id}/refresh-payment-link` endpoint | 178A | 178.1 | Modify: `invoice/InvoiceController.java`. New method: `refreshPaymentLink(@PathVariable UUID id)`. Validate invoice exists, status is SENT, member has access. Call `PaymentLinkService.refreshPaymentLink()`. Return updated `InvoiceResponse`. |
| 178.7 | Add `docteams.app.portal-base-url` property | 178A | | Modify: `application.yml`. Add property for portal URL used in success/cancel URL construction. Default: `http://localhost:3002` for local dev. |
| 178.8 | Write integration tests for `PaymentLinkService` and webhook controller | 178A | 178.1-178.6 | New file: `invoice/PaymentLinkServiceIntegrationTest.java`. Tests: (1) generatePaymentLink_with_noop_skips, (2) refreshPaymentLink_cancels_old_creates_new, (3) cancelActiveSession_marks_event_cancelled. New file: `integration/payment/PaymentWebhookControllerIntegrationTest.java`. Tests: (4) handleWebhook_unknown_provider_returns_200, (5) handleWebhook_missing_tenant_returns_200, (6) refreshPaymentLink_endpoint_returns_updated_invoice, (7) refreshPaymentLink_on_non_SENT_invoice_returns_409. ~7 tests across 2 files. |
| 178.9 | Create `PaymentReconciliationService` | 178B | 175.4 | New file: `invoice/PaymentReconciliationService.java`. `@Service`. Method: `processWebhookResult(WebhookResult, String providerSlug)`. Switch on result status: COMPLETED -> `handlePaymentCompleted()`, FAILED -> `handlePaymentFailed()`, EXPIRED -> `handlePaymentExpired()`. Inject `InvoiceService`, `PaymentEventRepository`, `AuditService`, `NotificationService`. See architecture doc Section 25.7.2. |
| 178.10 | Implement `handlePaymentCompleted()` | 178B | 178.9 | Idempotent: skip if invoice already PAID. Call `InvoiceService.recordPayment(invoiceId, paymentReference, true)` (fromWebhook overload). Write COMPLETED `PaymentEvent`. Log `payment.completed` audit event. |
| 178.11 | Implement `handlePaymentFailed()` and `handlePaymentExpired()` | 178B | 178.9 | Write FAILED/EXPIRED `PaymentEvent`. Log audit events (`payment.failed`, `payment.session.expired`). Notify admins (PAYMENT_FAILED) or invoice creator (PAYMENT_LINK_EXPIRED). |
| 178.12 | Add `fromWebhook` overload to `InvoiceService.recordPayment()` | 178B | | Modify: `invoice/InvoiceService.java`. New overload: `recordPayment(UUID invoiceId, String paymentReference, boolean fromWebhook)`. If `fromWebhook = true`, skip `gateway.recordManualPayment()` call (payment already confirmed by PSP). Directly transition SENT -> PAID. See architecture doc Section 25.3.5. |
| 178.13 | Wire `PaymentLinkService` into `InvoiceService.send()` | 178B | 178.1 | Modify: `invoice/InvoiceService.java`. After `invoice.markSent()` and save, call `paymentLinkService.generatePaymentLink(invoice)`. Before email send. See architecture doc Section 25.6.2. |
| 178.14 | Wire session cancellation into `InvoiceService.recordPayment()` | 178B | 178.1 | Modify: `invoice/InvoiceService.java`. In `recordPayment()` (non-webhook): after marking PAID, if `invoice.getPaymentSessionId() != null`, call `paymentLinkService.cancelActiveSession(invoice)`. Write manual COMPLETED `PaymentEvent`. See architecture doc Section 25.9.2. |
| 178.15 | Wire `PaymentReconciliationService` into `PaymentWebhookController` | 178B | 178.9, 178.3 | Modify: `PaymentWebhookController`. After `result.verified()`, call `reconciliationService.processWebhookResult(result, provider)`. |
| 178.16 | Write integration tests for reconciliation and wiring | 178B | 178.9-178.15 | New file: `invoice/PaymentReconciliationServiceIntegrationTest.java`. Tests: (1) processWebhookResult_COMPLETED_marks_invoice_PAID, (2) processWebhookResult_COMPLETED_idempotent_when_already_paid, (3) processWebhookResult_FAILED_creates_event, (4) processWebhookResult_EXPIRED_creates_event, (5) invoiceService_send_calls_generatePaymentLink, (6) invoiceService_recordPayment_cancels_active_session, (7) manual_payment_writes_manual_PaymentEvent, (8) audit_event_logged_on_payment_completed. ~8 tests. |

### Key Files

**Slice 178A -- Create:**
- `backend/src/main/java/.../invoice/PaymentLinkService.java`
- `backend/src/main/java/.../integration/payment/PaymentWebhookController.java`
- `backend/src/test/java/.../invoice/PaymentLinkServiceIntegrationTest.java`
- `backend/src/test/java/.../integration/payment/PaymentWebhookControllerIntegrationTest.java`

**Slice 178A -- Modify:**
- `backend/src/main/java/.../invoice/InvoiceController.java`
- `backend/src/main/java/.../security/SecurityConfig.java`
- `backend/src/main/resources/application.yml`

**Slice 178B -- Create:**
- `backend/src/main/java/.../invoice/PaymentReconciliationService.java`
- `backend/src/test/java/.../invoice/PaymentReconciliationServiceIntegrationTest.java`

**Slice 178B -- Modify:**
- `backend/src/main/java/.../invoice/InvoiceService.java`
- `backend/src/main/java/.../integration/payment/PaymentWebhookController.java`

**Read for context:**
- `invoice/InvoiceService.java` -- `send()` and `recordPayment()` methods
- `integration/IntegrationRegistry.java` -- `resolveBySlug()` method (from 174A)
- `security/SecurityConfig.java` -- `permitAll()` patterns
- `audit/AuditService.java` -- Audit logging pattern
- `notification/NotificationService.java` -- Notification pattern

### Architecture Decisions

- **Webhook always returns HTTP 200**: Both Stripe and PayFast interpret non-200 as failure and retry. Always return 200, even on processing errors. Log issues for investigation.
- **Tenant identification from unsigned metadata (ADR-099)**: Safe because unsigned metadata is only used for secret/schema lookup. All mutations happen after full signature verification.
- **Session cancellation on manual payment**: Prevents double payment. Stripe supports `Session.expire()`. PayFast sessions expire naturally.
- **`fromWebhook` overload**: Avoids calling `gateway.recordManualPayment()` when the PSP has already confirmed payment. The adapter call is skipped; only the invoice state transition happens.

---

## Epic 179: Portal Payment Flow + Read-Model Extension

**Goal**: Extend the portal backend with payment-status polling, sync payment fields to the portal read-model, and build the portal frontend "Pay Now" button, success page, and cancel page.

**References**: Architecture doc Sections 25.8 (portal payment flow), 25.8.1--25.8.4, 25.14 (V9 global migration).

**Dependencies**: Epic 178 (payment links must be generated and webhook processing must work).

**Scope**: Both (Backend + Portal Frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **179A** | 179.1--179.7 | Portal backend: V9 global migration (payment fields on portal read-model) + `PortalInvoiceView` extension + `GET /api/portal/invoices/{id}/payment-status` endpoint + invoice sync handler update + integration tests. ~1 new migration, ~3 modified files, ~1 new test file. Backend only. | |
| **179B** | 179.8--179.14 | Portal frontend: "Pay Now" button on invoice detail + payment-success page with status polling + payment-cancelled page + API client extension + frontend tests. ~3 new files, ~2 modified files. Portal frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 179.1 | Create V9 global migration | 179A | | New file: `db/migration/global/V9__portal_payment_fields.sql`. ALTER `portal_read.portal_invoices` ADD `payment_url VARCHAR(1024)`, `payment_session_id VARCHAR(255)`. See architecture doc Section 25.14. |
| 179.2 | Extend `PortalInvoiceView` entity | 179A | 179.1 | Modify: `customerbackend/model/PortalInvoiceView.java` (or equivalent). Add `paymentUrl` (String, nullable) and `paymentSessionId` (String, nullable) fields + getters. |
| 179.3 | Create `PaymentStatusResponse` DTO | 179A | | New file: `customerbackend/controller/PaymentStatusResponse.java` (or in `dto/` sub-package). Java record: `status` (String), `paidAt` (String, nullable). |
| 179.4 | Add `GET /api/portal/invoices/{id}/payment-status` endpoint | 179A | 179.2, 179.3 | Modify: `customerbackend/controller/PortalInvoiceController.java`. New method returns `PaymentStatusResponse` with invoice status and paidAt timestamp. Verify portal contact has access to the invoice. |
| 179.5 | Update invoice sync handler for payment fields | 179A | 179.2 | Modify: `customerbackend/handler/InvoiceEventHandler.java` (or equivalent event handler). When syncing invoice to portal read-model, include `paymentUrl` and `paymentSessionId` fields. |
| 179.6 | Write integration tests for payment-status endpoint | 179A | 179.4 | New file: `customerbackend/controller/PortalPaymentStatusIntegrationTest.java`. Tests: (1) getPaymentStatus_returns_SENT_for_unpaid, (2) getPaymentStatus_returns_PAID_with_paidAt, (3) getPaymentStatus_returns_404_for_unknown_invoice, (4) getPaymentStatus_verifies_portal_contact_access. ~4 tests. |
| 179.7 | Write integration test for sync handler | 179A | 179.5 | In existing sync handler test: add test verifying `paymentUrl` is synced to portal read-model when invoice is sent with payment link. ~1 test. |
| 179.8 | Add "Pay Now" button to portal invoice detail page | 179B | 179A | Modify: `portal/app/(authenticated)/invoices/[id]/page.tsx`. Conditional rendering: if `status === 'SENT'` and `paymentUrl` is non-null, show prominent "Pay Now" button (external `<a>` redirect). If `paymentUrl` is null, show "Contact {org} to arrange payment". If `status === 'PAID'`, show "Paid" badge. |
| 179.9 | Create payment success page | 179B | | New file: `portal/app/(authenticated)/invoices/[id]/payment-success/page.tsx`. Shows "Payment received -- thank you!". Polls `GET /api/portal/invoices/{id}/payment-status` every 3s for up to 30s. Shows "Payment is being processed" while polling. Shows "Payment confirmed" with reference when PAID. |
| 179.10 | Implement status polling hook | 179B | 179.9 | In success page or custom hook. `useEffect` with `setInterval(3000)`. Stops when status is PAID or 30s timeout. Cleans up on unmount. |
| 179.11 | Create payment cancelled page | 179B | | New file: `portal/app/(authenticated)/invoices/[id]/payment-cancelled/page.tsx`. Shows "Payment was cancelled. You can try again." Displays "Pay Now" button (same paymentUrl). Link back to invoice detail. |
| 179.12 | Extend portal API client | 179B | | Modify: `portal/lib/api/invoices.ts` (or equivalent). Add `getPaymentStatus(invoiceId)` function. Returns `{ status, paidAt }`. |
| 179.13 | Extend portal invoice type | 179B | | Modify: `portal/lib/types.ts` (or equivalent). Add `paymentUrl?: string` and `paymentSessionId?: string` to Invoice type. |
| 179.14 | Write portal frontend tests | 179B | 179.8-179.11 | New file: `portal/app/(authenticated)/invoices/__tests__/payment.test.tsx`. Tests: (1) renders_PayNow_button_when_paymentUrl_present, (2) hides_PayNow_when_status_PAID, (3) shows_contact_message_when_no_paymentUrl, (4) success_page_shows_processing_message, (5) cancelled_page_shows_retry_button. ~5 tests. |

### Key Files

**Slice 179A -- Create:**
- `backend/src/main/resources/db/migration/global/V9__portal_payment_fields.sql`
- `backend/src/main/java/.../customerbackend/controller/PaymentStatusResponse.java`
- `backend/src/test/java/.../customerbackend/controller/PortalPaymentStatusIntegrationTest.java`

**Slice 179A -- Modify:**
- `backend/src/main/java/.../customerbackend/model/PortalInvoiceView.java`
- `backend/src/main/java/.../customerbackend/controller/PortalInvoiceController.java`
- `backend/src/main/java/.../customerbackend/handler/InvoiceEventHandler.java`

**Slice 179B -- Create:**
- `portal/app/(authenticated)/invoices/[id]/payment-success/page.tsx`
- `portal/app/(authenticated)/invoices/[id]/payment-cancelled/page.tsx`
- `portal/app/(authenticated)/invoices/__tests__/payment.test.tsx`

**Slice 179B -- Modify:**
- `portal/app/(authenticated)/invoices/[id]/page.tsx`
- `portal/lib/api/invoices.ts`
- `portal/lib/types.ts`

**Read for context:**
- `customerbackend/controller/PortalInvoiceController.java` -- Existing portal invoice endpoints
- `customerbackend/model/PortalInvoiceView.java` -- Portal read-model entity
- `customerbackend/handler/` -- Event handler pattern for portal sync
- `portal/app/(authenticated)/invoices/[id]/page.tsx` -- Existing invoice detail page

### Architecture Decisions

- **V9 global migration**: Portal read-model lives in `portal_read` schema (global, not tenant). Payment fields added there separately from V42 tenant migration.
- **Status polling on success page**: Webhook delivery is async. Success page polls every 3s for up to 30s. If webhook hasn't arrived, shows "processing" message.
- **No auto-refresh on portal GET (ADR-100)**: Portal reads are side-effect-free. Expired links show a message directing client to contact the org. Refresh is tenant-initiated.

---

## Epic 180: Integration Settings UI + Invoice Payment UX

**Goal**: Add Stripe and PayFast configuration fields to the PAYMENT integration card, extend the invoice detail page with payment link management (copy, regenerate, event history), add payment indicators to the invoice list, and conditionally render the "Pay Now" button in the invoice email template.

**References**: Architecture doc Sections 25.10 (Integration Settings), 25.9.3 (Invoice Detail), 25.6.3 (Email Pay Now button), 25.10.4 (Invoice List indicator).

**Dependencies**: Epic 178 (backend endpoints must exist for integration card and invoice payment operations).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **180A** | 180.1--180.7 | PAYMENT integration card: Stripe config fields (API key, webhook secret, webhook URL) + PayFast config fields (merchant ID/key, passphrase, sandbox, ITN URL) + provider selector dropdown + test connection + frontend tests. ~3 modified files, ~1 new component file. Frontend only. | |
| **180B** | 180.8--180.15 | Invoice detail: payment link section (copy link, regenerate button) + payment event history table + invoice list payment indicator + email template "Pay Now" conditional button + API client extension + frontend tests. ~3 modified files, ~1 new component, ~1 modified template. Frontend + Backend template. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 180.1 | Add Stripe configuration fields to PAYMENT card | 180A | | Modify: `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` (or integration card component). Add fields: "Secret Key" (password input, stored in SecretStore), "Webhook Signing Secret" (password input, stored in SecretStore/configJson), "Webhook URL" (read-only, computed: `{appUrl}/api/webhooks/payment/stripe`). See architecture doc Section 25.10.2. |
| 180.2 | Add PayFast configuration fields to PAYMENT card | 180A | | In same component. Add fields: "Merchant ID" (text input, configJson), "Merchant Key" (text input, configJson), "Passphrase" (password input, SecretStore), "Use PayFast Sandbox" (toggle, configJson), "ITN Callback URL" (read-only, computed: `{appUrl}/api/webhooks/payment/payfast`). See architecture doc Section 25.10.3. |
| 180.3 | Add provider selector dropdown | 180A | | In PAYMENT card. Dropdown: "None (Manual Only)", "Stripe", "PayFast". When selecting a provider, show the corresponding configuration fields. Persist via existing integration settings server actions. |
| 180.4 | Update default state display | 180A | | When no provider configured: badge shows "Manual Payments Only", description: "Connect a payment provider to enable online invoice payments for your clients." |
| 180.5 | Wire test connection for Stripe | 180A | | "Test Connection" button calls `POST /api/integrations/PAYMENT/test`. Show success/failure result. PayFast shows advisory note instead of test button. |
| 180.6 | Extend integration types | 180A | | Modify: `frontend/lib/types.ts`. Add PAYMENT provider config shapes: `StripePaymentConfig`, `PayFastPaymentConfig`. Extend existing integration types. |
| 180.7 | Write frontend tests for PAYMENT card | 180A | | New tests in existing integrations test file or new file. Tests: (1) renders_provider_selector, (2) shows_stripe_fields_when_stripe_selected, (3) shows_payfast_fields_when_payfast_selected, (4) shows_manual_only_when_none_selected, (5) webhook_url_is_readonly. ~5 tests. |
| 180.8 | Add payment link section to invoice detail page | 180B | | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`. New section (visible when invoice is SENT + paymentUrl non-null): display payment URL with "Copy Link" button (clipboard API). "Regenerate Link" button (calls refresh endpoint). Conditional on status. |
| 180.9 | Implement "Copy Link" functionality | 180B | 180.8 | Use `navigator.clipboard.writeText(paymentUrl)`. Toast notification on success. |
| 180.10 | Implement "Regenerate Link" button | 180B | 180.8 | Server action calling `POST /api/invoices/{id}/refresh-payment-link`. Show loading state. Update invoice data on success via `revalidatePath`. |
| 180.11 | Create PaymentEventHistory component | 180B | | New file: `frontend/components/invoices/PaymentEventHistory.tsx`. Table displaying `PaymentEvent` records: columns for status (with badge), provider, reference, timestamp. Fetches from `GET /api/invoices/{id}/payment-events`. Sorted by createdAt desc. Empty state: "No payment events yet." |
| 180.12 | Add payment indicator to invoice list | 180B | | Modify: `frontend/app/(app)/org/[slug]/invoices/page.tsx`. Small credit card icon (or "Online" badge) next to invoices where `paymentUrl` is non-null. Distinguishes online-payment-enabled invoices from manual-only. |
| 180.13 | Add "Pay Now" button to invoice email template | 180B | | Modify: `backend/src/main/resources/templates/email/invoice-delivery.html`. Add conditional block: `<div th:if="${paymentUrl}">` with styled "Pay Now" anchor tag. Blue button, centered. See architecture doc Section 25.6.3. |
| 180.14 | Extend frontend API client and types | 180B | | Modify: `frontend/lib/api/invoices.ts` (or server actions). Add `refreshPaymentLink(invoiceId)`, `getPaymentEvents(invoiceId)`. Modify: `frontend/lib/types.ts`. Add `PaymentEvent` type. Extend `Invoice` type with `paymentSessionId`, `paymentUrl`, `paymentDestination`. |
| 180.15 | Write frontend tests | 180B | 180.8-180.12 | Tests: (1) renders_payment_link_section_for_SENT_invoice, (2) hides_payment_link_for_DRAFT, (3) copy_link_button_calls_clipboard, (4) regenerate_button_calls_server_action, (5) PaymentEventHistory_renders_table, (6) invoice_list_shows_payment_indicator, (7) PaymentEventHistory_shows_empty_state. ~7 tests. |

### Key Files

**Slice 180A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` (or integration card components)
- `frontend/lib/types.ts`

**Slice 180A -- Read for context:**
- Existing PAYMENT integration card rendering (currently shows "Manual Payments Only" or similar)
- `frontend/lib/api.ts` -- API client patterns

**Slice 180B -- Create:**
- `frontend/components/invoices/PaymentEventHistory.tsx`

**Slice 180B -- Modify:**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- `frontend/lib/api/invoices.ts` (or equivalent server actions)
- `frontend/lib/types.ts`
- `backend/src/main/resources/templates/email/invoice-delivery.html`

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` -- Current integration card structure
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` -- Current invoice detail layout
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- Current invoice list
- `frontend/components/invoices/` -- Existing invoice components
- `backend/src/main/resources/templates/email/invoice-delivery.html` -- Email template to extend

### Architecture Decisions

- **Webhook URL is read-only**: Displayed for the tenant to copy into their Stripe/PayFast dashboard. Computed from `appUrl` + provider slug.
- **PayFast has no test connection**: Advisory message replaces the test button.
- **Email "Pay Now" button is conditional**: Only rendered when `paymentUrl` context variable is non-null. Invoices without payment links (NoOp org) get the standard email without a button.
- **180B touches one backend template file**: The `invoice-delivery.html` Thymeleaf template gets the conditional "Pay Now" block. This is a minor cross-boundary touch justified by keeping the email template change in the same slice as the UI changes it relates to.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` - Core file to modify: registry migration, send trigger, manual payment event creation, session cancellation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` - Add resolveBySlug() method, pattern for all adapter resolution
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/email/EmailProvider.java` - Reference pattern for PaymentGateway port interface design
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/MockPaymentProvider.java` - Legacy code to delete, understand current behavior before replacing
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` - Add webhook path to permitAll() list
agentId: a4788789d0b266e3d (for resuming to continue this agent's work if needed)
<usage>total_tokens: 119495
tool_uses: 28
duration_ms: 353761</usage>
