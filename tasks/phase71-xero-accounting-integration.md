# Phase 71 — Xero Accounting Integration (One-Way Sync)

> **Architecture**: [`architecture/phase71-xero-accounting-integration.md`](../architecture/phase71-xero-accounting-integration.md)
> **Requirements**: [`requirements/claude-code-prompt-phase71.md`](../requirements/claude-code-prompt-phase71.md)
> **ADRs**: [ADR-272](../adr/ADR-272-xero-only-accounting-adapter-v1.md), [ADR-273](../adr/ADR-273-one-way-accounting-sync-permanent.md), [ADR-274](../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md), [ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md), [ADR-276](../adr/ADR-276-trust-accounting-hard-guard-export.md), [ADR-277](../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md), [ADR-278](../adr/ADR-278-idempotent-push-via-external-reference.md), [ADR-279](../adr/ADR-279-sibling-payment-source-port.md)
> **Predecessors**: Phase 21 (Integration Ports + BYOAK -- `IntegrationRegistry`, `OrgIntegration`, `SecretStore`, `AccountingProvider` port), Phase 10 (Invoicing & Billing -- `Invoice`, `InvoiceLine`, lifecycle events), Phase 25 (Online Payment Collection -- `PaymentEvent`), Phase 41/46 (Capabilities + `CapabilityAuthorizationService`), Phase 60/61 (Trust Accounting -- `TrustAccount`, `LegalDisbursement`, `ClientLedgerCard`)
> **Starting epic**: 517 . Last completed: 516 (Phase 70 QA capstone)
> **Migration high-water at phase start**: tenant **V120**. Phase 71 ships **one** tenant migration (V121).

Phase 71 delivers the first external accounting integration for Kazi: a tenant-connectable, OAuth2-based Xero integration that pushes invoices and customers from Kazi to Xero on approval, pulls payment status from Xero on a schedule, and surfaces sync state in the settings UI. The integration is the primary commercial unlock for the accounting-za vertical -- small SA accounting practices cannot adopt a system that does not push invoices into the accountant's general ledger -- and is a quality-of-life unlock for legal-za and consulting-za firms whose bookkeeper already lives in Xero.

The phase builds on the Phase 21 `AccountingProvider` port (already defined with `NoOpAccountingProvider`, `InvoiceSyncRequest`, `CustomerSyncRequest`, `LineItem`, `AccountingSyncResult`), the Phase 10 invoice lifecycle events (`InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoiceVoidedEvent`), and the Phase 25 `PaymentEvent` entity. All sync orchestration is owned by a dedicated `AccountingSyncService` -- not the Phase 37 rule engine ([ADR-274](../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)). Trust-accounting data (Phase 60/61) is guarded by a fail-closed `TrustBoundaryGuard` that refuses to push any trust-related invoice to Xero ([ADR-276](../adr/ADR-276-trust-accounting-hard-guard-export.md)).

Three strategic constraints bound the phase: (1) **Xero only for v1** -- Sage Pastel deferred ([ADR-272](../adr/ADR-272-xero-only-accounting-adapter-v1.md)). (2) **One-way sync** -- push invoices/customers, pull payments; no bidirectional sync ([ADR-273](../adr/ADR-273-one-way-accounting-sync-permanent.md)). (3) **No PlanTier** -- capability-gated only; `PlanTier`, `@RequiresPlan`, `<PlanGate>` must not be reintroduced.

---

## Open Questions

- **Slice H sizing.** Architecture Slice H combines backend controller + frontend settings page + 4 frontend components + API client functions. This exceeds the 8-12 file budget for a single slice. **Resolution**: split into **524A** (backend controller -- OAuth endpoints + settings + tax mappings + customer import) and **524B** (frontend Xero settings page + connection card + tax mapping editor + customer import + settings form + API client). 524A stays at ~7 files; 524B stays at ~8 files.
- **Slice I sizing.** Architecture Slice I combines frontend sync log page + sync summary + 3 components + 2 modified detail pages + backend sync controller. **Resolution**: split into **525A** (backend sync log/summary/retry controller endpoints) and **525B** (frontend sync log page + table + badges + summary widget + invoice/customer status chips + modified detail pages). 525A stays at ~5 files; 525B stays at ~9 files.
- **Capability enum additions.** `INTEGRATION_MANAGE` is referenced in the architecture but does not currently exist in the `Capability` enum at `backend/.../orgrole/Capability.java`. The existing integrations settings UI uses `@RequiresCapability` with capability strings resolved from `OrgRole`. Phase 71 adds three new capability enum values: `INTEGRATION_MANAGE`, `INTEGRATION_VIEW_SYNC_STATUS`, `FINANCIAL_RECONCILE`. These land in the foundation slice (517A) alongside the migration.
- **`InvoiceSyncRequest` modification.** The existing record needs `externalReference` and `customerEmail` fields added. `LineItem` needs a `taxMode` field. These additive changes are backward-compatible and land in 517A.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 517 | Migration + Entities + Repositories + Port Extensions | Backend | -- | L | 517A, 517B | Done |
| 518 | AccountingSyncService + Worker + Event Listeners | Backend | 517 | L | 518A, 518B | |
| 519 | XeroApiClient + XeroOAuthService | Backend | 517A | M | 519A | |
| 520 | XeroAccountingProvider Adapter + Mappers | Backend | 517A, 519A | M | 520A, 520B | |
| 521 | Trust Boundary Guard | Backend | 518A | S | 521A | |
| 522 | Payment Pull (Poll Worker Completion) | Backend | 518A, 520A | M | 522A | |
| 523 | One-Time Customer Import | Backend | 519A, 520A | S | 523A | |
| 524 | Frontend -- Connection Management + Settings | Both | 519A, 520B | L | 524A, 524B | |
| 525 | Frontend -- Sync Log + Status Chips | Both | 518A, 524A | L | 525A, 525B | |

**Slice count: 13** (9 architecture slices expanded to 13 numbered slices to honour the 6-10 files / ~800 LOC slice-sizing budget). Backend-frontend split is preserved per slice -- no slice mixes both scopes except where the architecture explicitly co-locates a thin backend controller with its frontend consumers (524A is backend-only; 524B is frontend-only; similarly 525A/525B).

---

## Dependency Graph

```
PHASES already complete:
  Phase 10 (Invoicing — Invoice, InvoiceLine, InvoiceTransitionService, InvoiceApprovedEvent)
  Phase 21 (Integration Ports — AccountingProvider, OrgIntegration, SecretStore, IntegrationRegistry)
  Phase 25 (Payment Collection — PaymentEvent, PaymentGateway)
  Phase 41/46 (Capabilities + CapabilityAuthorizationService)
  Phase 60/61 (Trust Accounting — TrustAccount, LegalDisbursement, ClientLedgerCard)
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 1 — Database Foundation (sequential)               │
        │                                                          │
        │   [517A  V121 migration + AccountingXeroConnection +     │
        │          AccountingSyncEntry + AccountingTaxCodeMapping   │
        │          entities + repos + enums + Capability enum      │
        │          additions + InvoiceSyncRequest/LineItem mods]   │
        │                       │                                  │
        │                       ▼                                  │
        │   [517B  AccountingPaymentSource port +                  │
        │          ExternalPaymentEvent record +                   │
        │          NoOpAccountingProvider modification +            │
        │          AccountingTaxCodeMappingService]                 │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 2 — Core Services (parallel after 517B)            │
        │                                                          │
        │   [518A  AccountingSyncService + AccountingSyncWorker +  │
        │          AccountingSyncEventListener +                   │
        │          SyncService enqueue/retry/summary logic +       │
        │          TrustBoundaryDecision record]                   │
        │                                                          │
        │   [519A  XeroApiClient + XeroOAuthService +              │
        │          XeroRateLimitException + XeroConnectionStatus   │
        │          enum + SecretStore token management]            │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 3 — Adapter + Guard (parallel after 518A + 519A)   │
        │                                                          │
        │   [518B  AccountingPaymentPollWorker skeleton +           │
        │          worker drain integration with SyncService]      │
        │                                                          │
        │   [520A  XeroAccountingProvider +                         │
        │          XeroInvoicePayloadMapper]                       │
        │                                                          │
        │   [520B  XeroContactPayloadMapper +                      │
        │          AccountingTaxCodeMappingService integration]    │
        │                                                          │
        │   [521A  TrustBoundaryGuard + integration with           │
        │          AccountingSyncService.enqueueInvoicePush]       │
        └─────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 4 — Advanced Features (parallel after Stage 3)     │
        │                                                          │
        │   [522A  Payment pull completion — wire                  │
        │          AccountingPaymentPollWorker to                  │
        │          XeroAccountingProvider.getPaymentsModifiedSince │
        │          + payment matching + drift detection]           │
        │                                                          │
        │   [523A  XeroCustomerImportService — one-time import     │
        │          from Xero contacts + dedup + import guard]      │
        │                                                          │
        │   [524A  XeroIntegrationController — all REST endpoints  │
        │          for connect/callback/disconnect/settings/tax/   │
        │          import + AccountingSyncController for retry/    │
        │          resync endpoints]                               │
        └─────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Stage 5 — Frontend (sequential after 524A)               │
        │                                                          │
        │   [524B  Xero settings page + XeroConnectionCard +       │
        │          XeroTaxMappingEditor + XeroCustomerImport +     │
        │          XeroSettingsForm + API client functions +        │
        │          modified integrations page]                     │
        │                       │                                  │
        │                       ▼                                  │
        │   [525A  Sync summary/log/retry controller endpoints]    │
        │                       │                                  │
        │                       ▼                                  │
        │   [525B  Sync log page + SyncLogTable +                  │
        │          SyncEntryStateBadge + XeroSyncSummary +         │
        │          XeroStatusChip + XeroContactBadge +             │
        │          modified invoice/customer detail pages]         │
        └─────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- After **517B** lands, **518A** and **519A** can run in parallel -- the sync service depends on entities (517A/B) but not on the Xero API client; conversely the Xero client depends on entities but not the sync service.
- Stage 3 is a wide fan-out: **518B**, **520A**, **520B**, and **521A** can all run in parallel once their respective dependencies from Stage 2 have landed.
- **522A**, **523A**, and **524A** parallelise in Stage 4.
- Frontend slices (**524B**, **525A**, **525B**) are sequential because each builds on the previous.

---

## Implementation Order

### Stage 1 -- Database Foundation (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **517A** | V121 migration (3 tables + indexes + ZA tax seed data); `AccountingXeroConnection` entity + repository; `AccountingSyncEntry` entity + repository (with drain query, entity lookup, external reference match); `AccountingTaxCodeMapping` entity + repository; `SyncState`, `SyncDirection`, `SyncEntityType`, `SyncTrigger` enums; `XeroConnectionStatus` enum; `Capability` enum additions (`INTEGRATION_MANAGE`, `INTEGRATION_VIEW_SYNC_STATUS`, `FINANCIAL_RECONCILE`); `InvoiceSyncRequest` modification (add `externalReference`, `customerEmail`); `LineItem` modification (add `taxMode`). **Done** (PR #1325) |
| 1b | **517B** | `AccountingPaymentSource` port interface; `ExternalPaymentEvent` record; `NoOpAccountingProvider` modification (add `implements AccountingPaymentSource` with empty-list return); `AccountingTaxCodeMappingService` (CRUD + reset-to-defaults); entity persistence tests + repository query tests. **Done** (PR #1326) |

### Stage 2 -- Core Services (parallel after 517B)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 2a | **518A** | `AccountingSyncService` (enqueue, retry, summary, poll orchestration); `AccountingSyncWorker` (`@Scheduled(fixedDelay = 30_000)` drain worker with retry back-off); `AccountingSyncEventListener` (subscribes to invoice + customer domain events); `TrustBoundaryDecision` record; enqueue idempotency + state transition + back-off schedule + dead-letter tests. **Done** (PR #1327) | 519A |
| 2b | **519A** | `XeroApiClient` (`RestClient` wrapper with bearer-token, refresh-on-401, rate-limit headers, `Xero-tenant-id` header); `XeroOAuthService` (authorization URL builder, code-exchange, refresh-token rotation, disconnect); `XeroRateLimitException`; token exchange + refresh cycle + refresh failure cascade tests. | 518A |

### Stage 3 -- Adapter + Guard (parallel after Stage 2)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **518B** | `AccountingPaymentPollWorker` skeleton (`@Scheduled(fixedDelay = 900_000)`); tenant iteration via `TenantScopedRunner.forEachTenant()`; cursor update on `connection.lastPollAt`; wired to `NoOpAccountingProvider` until 522A completes the real binding. | 520A, 520B, 521A |
| 3b | **520A** | `XeroAccountingProvider` (`implements AccountingProvider, AccountingPaymentSource`); `XeroInvoicePayloadMapper` (pure function: `InvoiceSyncRequest` + tax mappings to Xero invoice JSON); invoice mapping accuracy + `getPaymentsModifiedSince` mapping tests. | 518B, 520B, 521A |
| 3c | **520B** | `XeroContactPayloadMapper` (pure function: `CustomerSyncRequest` to Xero contact JSON); wire `AccountingTaxCodeMappingService` into mappers for tax code resolution; contact mapping + tax code resolution tests. | 518B, 520A, 521A |
| 3d | **521A** | `TrustBoundaryGuard` service (three guard conditions + fail-closed + skip for non-legal tenants); integration with `AccountingSyncService.enqueueInvoicePush`; audit event emission for blocked pushes; all guard condition tests + fail-closed on DB error + non-legal tenant skip test. | 518B, 520A, 520B |

### Stage 4 -- Advanced Features (parallel after Stage 3)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 4a | **522A** | Complete `AccountingPaymentPollWorker` body -- wire to `XeroAccountingProvider.getPaymentsModifiedSince`; payment matching logic in `AccountingSyncService.pollPaymentsForConnection`; `PaymentEvent` creation with `provider_slug = "xero"`; invoice transition to `PAID` via `InvoiceTransitionService`; amount drift detection and `RECONCILE_DRIFT` state; happy-path + drift + idempotent re-poll + Xero-native skip tests. | 523A, 524A |
| 4b | **523A** | `XeroCustomerImportService`; pagination of Xero contacts; dedup logic (email, then name+taxNumber); one-time guard (import already run check); customer creation with `PROSPECT` status and `external_reference`; pagination + dedup + guard + summary tests. | 522A, 524A |
| 4c | **524A** | `XeroIntegrationController` (OAuth connect/callback/disconnect + connection status + settings + tax mappings + customer import endpoints); `AccountingSyncController` (sync summary + entries + retry + resync + reconcile endpoints); controller RBAC gate tests. | 522A, 523A |

### Stage 5 -- Frontend (sequential after 524A)

| Order | Slice | Summary |
|-------|-------|---------|
| 5a | **524B** | `frontend/app/(app)/org/[slug]/settings/integrations/xero/page.tsx`; `XeroConnectionCard.tsx`; `XeroTaxMappingEditor.tsx`; `XeroCustomerImport.tsx`; `XeroSettingsForm.tsx`; API client functions in `frontend/lib/api/integrations.ts`; modified integrations settings page (add Xero card linking to new sub-page). |
| 5b | **525A** | Sync summary/log/retry/resync/reconcile controller endpoint tests (these complement 524A -- the controller itself was created in 524A; this slice adds the sync-specific frontend API client functions and wires the summary/entries queries). |
| 5c | **525B** | `frontend/app/(app)/org/[slug]/settings/integrations/xero/sync-log/page.tsx`; `SyncLogTable.tsx`; `SyncEntryStateBadge.tsx`; `XeroSyncSummary.tsx`; `XeroStatusChip.tsx`; `XeroContactBadge.tsx`; modified invoice detail page (add status chip); modified customer detail page (add contact badge). |

### Timeline

```
Stage 1: [517A] -> [517B]                                               <- foundations
Stage 2: [518A] // [519A]                                               <- 2-way parallel
Stage 3: [518B] // [520A] // [520B] // [521A]                           <- 4-way parallel
Stage 4: [522A] // [523A] // [524A]                                     <- 3-way parallel
Stage 5: [524B] -> [525A] -> [525B]                                     <- sequential frontend
```

A realistic day-by-day cadence: 517A days 1-3; 517B days 3-5; 518A + 519A days 5-9 (2-way parallel); 518B + 520A + 520B + 521A days 9-14 (4-way parallel); 522A + 523A + 524A days 14-19 (3-way parallel); 524B days 19-22; 525A days 22-24; 525B days 24-28.

---

## Epic 517: Migration + Entities + Repositories + Port Extensions

**Goal**: Lay the database foundation and domain model for the entire Xero integration. The V121 migration creates three new tenant-scoped tables (`accounting_xero_connection`, `accounting_sync_entry`, `accounting_tax_code_mapping`) with all indexes and ZA default seed data. Entity and repository classes provide the persistence layer. The existing `AccountingProvider` port is extended with a sibling `AccountingPaymentSource` interface for payment pull. The `Capability` enum gains three new values for Phase 71's RBAC gates.

**References**: Architecture Section 11.2 (Domain Model), Section 11.7 (Database Migrations), Section 11.8.1 (Implementation Guidance -- entity and repo patterns), Section 11.10 Slice A; [ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md), [ADR-278](../adr/ADR-278-idempotent-push-via-external-reference.md), [ADR-279](../adr/ADR-279-sibling-payment-source-port.md).

**Dependencies**: Phase 21 (`OrgIntegration`, `SecretStore`, `AccountingProvider` port, `IntegrationAdapter` annotation); Phase 25 (`PaymentEvent` entity shape -- no dependency, just pattern reference).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **517A** | 517A.1-517A.9 | ~12 backend files (1 migration + 3 entities + 3 repos + 4 enums + 2 modified records + 1 enum modification) | V121 migration with 3 tables + indexes + ZA seed data; `AccountingXeroConnection` entity + repo; `AccountingSyncEntry` entity + repo (with drain query); `AccountingTaxCodeMapping` entity + repo; `SyncState`, `SyncDirection`, `SyncEntityType`, `SyncTrigger` enums; `Capability` enum additions; `InvoiceSyncRequest` + `LineItem` modifications for `externalReference` and `taxMode`. **Done** (PR #1325) |
| **517B** | 517B.1-517B.5 | ~6 backend files (1 port interface + 1 record + 1 service + 1 provider modification + 2 test files) | `AccountingPaymentSource` port interface; `ExternalPaymentEvent` record; `AccountingTaxCodeMappingService` (CRUD + reset-to-defaults); `NoOpAccountingProvider` modification to implement `AccountingPaymentSource`; entity persistence tests; repository query tests. **Done** (PR #1326) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 517A.1 | Create V121 tenant migration | `backend/src/main/resources/db/migration/tenant/V121__add_xero_accounting_integration_tables.sql` | verified by 517B.5 (migration runs clean in test context) | existing `V120__add_ai_specialist_invocations_and_llm_calls.sql` for migration format | SQL verbatim from architecture Section 11.7: three tables (`accounting_xero_connection`, `accounting_sync_entry`, `accounting_tax_code_mapping`), four indexes (`idx_axc_status`, `idx_ase_drain`, `idx_ase_entity_lookup`, `idx_ase_external_reference`), one unique constraint (`uq_atcm_provider_tax_mode`), ZA tax code seed data (4 rows: STANDARD_15, ZERO_RATED, EXEMPT, OUT_OF_SCOPE). All `CREATE TABLE IF NOT EXISTS` + `ON CONFLICT DO NOTHING` for idempotency. |
| 517A.2 | Create `SyncState` enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncState.java` | covered by 517B.5 | existing enum style in `integration/payment/PaymentStatus.java` | Values: `PENDING`, `IN_FLIGHT`, `COMPLETED`, `FAILED_RETRYING`, `DEAD_LETTER`, `BLOCKED_TRUST_BOUNDARY`, `RECONCILE_DRIFT`. |
| 517A.3 | Create `SyncDirection`, `SyncEntityType`, `SyncTrigger` enums | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncDirection.java`, `SyncEntityType.java`, `SyncTrigger.java` | covered by 517B.5 | existing enum style | `SyncDirection`: `PUSH`, `PULL`. `SyncEntityType`: `INVOICE`, `CUSTOMER`, `PAYMENT_PULL`. `SyncTrigger`: `EVENT`, `MANUAL_RETRY`, `FORCE_RESYNC`. |
| 517A.4 | Create `AccountingXeroConnection` entity + repository | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/AccountingXeroConnection.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/AccountingXeroConnectionRepository.java` | 517B.5 | existing entity pattern in `integration/OrgIntegration.java`; existing repo pattern in `integration/OrgIntegrationRepository.java` | Entity per architecture Section 11.2.1. `@Table(name = "accounting_xero_connection")`. All fields from the domain model. Repository with `findOneById(UUID)`, `findByOrgIntegrationId(UUID)`, `findByStatus(String)`. No `@FilterDef`/`@Filter`/`TenantAware` -- schema-per-tenant handles isolation. Constructor injection, no Lombok, `@PrePersist`/`@PreUpdate` timestamps. Add `XeroConnectionStatus` enum (`CONNECTED`, `REFRESH_FAILED`, `REVOKED`) in the `xero/` subpackage. |
| 517A.5 | Create `AccountingSyncEntry` entity + repository | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEntry.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEntryRepository.java` | 517B.5 | architecture Section 11.8.3 provides full entity code; architecture Section 11.8.4 provides full repository code | Entity verbatim from architecture Section 11.8.3 with state transition methods (`markInFlight`, `markCompleted`, `markFailedRetrying`, `markDeadLetter`, `markBlockedTrustBoundary`, `resetForRetry`). Repository verbatim from architecture Section 11.8.4 with drain query, entity lookup, active entry check, external reference match, filtered page, and count-by-state queries. |
| 517A.6 | Create `AccountingTaxCodeMapping` entity + repository | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMapping.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingRepository.java` | 517B.5 | existing entity pattern | Entity per architecture Section 11.2.3. `@Table(name = "accounting_tax_code_mapping")`. Repository with `findByProviderId(String)`, `findByProviderIdAndKaziTaxMode(String, String)`. Unique constraint `(provider_id, kazi_tax_mode)` enforced at DB level. |
| 517A.7 | Modify `InvoiceSyncRequest` + `LineItem` records | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/InvoiceSyncRequest.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/LineItem.java` | covered by 517B.5 | existing record shape | Add `externalReference` (String) and `customerEmail` (String) to `InvoiceSyncRequest`. Add `taxMode` (String) to `LineItem`. These are additive fields -- existing callers passing the old shape will need to be updated (only `NoOpAccountingProvider` in tests currently). |
| 517A.8 | Add `Capability` enum values | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` | covered by 517B.5 | existing enum values in `Capability.java` | Add three values: `INTEGRATION_MANAGE` (owner + admin), `INTEGRATION_VIEW_SYNC_STATUS` (owner + admin), `FINANCIAL_RECONCILE` (owner only -- add to `OWNER_ONLY` set). Follow existing pattern: add to enum, add `FINANCIAL_RECONCILE` to the `OWNER_ONLY` `Set.of(...)`. |
| 517A.9 | Create `XeroConnectionStatus` enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroConnectionStatus.java` | covered by 517B.5 | existing enum style | Values: `CONNECTED`, `REFRESH_FAILED`, `REVOKED`. Used by `AccountingXeroConnection.status` field. |
| 517B.1 | Create `AccountingPaymentSource` port interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingPaymentSource.java` | covered by 517B.5 | existing port interface in `AccountingProvider.java` | `public interface AccountingPaymentSource { String providerId(); List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since); }` per [ADR-279](../adr/ADR-279-sibling-payment-source-port.md). |
| 517B.2 | Create `ExternalPaymentEvent` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/ExternalPaymentEvent.java` | covered by 517B.5 | existing record style in `AccountingSyncResult.java` | `public record ExternalPaymentEvent(String externalInvoiceReference, String externalPaymentId, BigDecimal amount, String currency, Instant paidAt, String status)`. |
| 517B.3 | Modify `NoOpAccountingProvider` to implement `AccountingPaymentSource` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java` | covered by 517B.5 | existing `NoOpAccountingProvider` -- add interface | Add `implements AccountingPaymentSource` alongside existing `implements AccountingProvider`. Add `getPaymentsModifiedSince()` returning `List.of()`. Update `syncInvoice`/`syncCustomer` signatures to match modified `InvoiceSyncRequest`/`CustomerSyncRequest`. |
| 517B.4 | Create `AccountingTaxCodeMappingService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingService.java` | 517B.5 | existing service pattern in `integration/IntegrationService.java` | `@Service`. Methods: `getByProvider(String providerId)`, `update(UUID id, String externalTaxCode, String displayLabel)`, `resetToDefaults(String providerId)`, `resolveForTaxMode(String providerId, String kaziTaxMode)`. Reset-to-defaults deletes non-default rows and re-inserts ZA defaults. |
| 517B.5 | Entity persistence + repository query integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingSyncEntryRepositoryTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingServiceTest.java` | ~8 tests: (1) V121 migration runs clean; (2) AccountingSyncEntry persist + findOneById round-trip; (3) drain query returns PENDING entries ordered by next_attempt_at; (4) entity lookup by entity_type + entity_id; (5) external reference match for completed pushes; (6) AccountingXeroConnection persist + findByOrgIntegrationId; (7) TaxCodeMappingService CRUD; (8) TaxCodeMapping reset-to-defaults restores ZA seed data | existing repo test pattern in `backend/src/test/java/.../integration/OrgIntegrationRepository*`; use `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` | Standard integration test setup: provision tenant, bind `RequestScopes.TENANT_ID`, verify entity round-trips and query correctness. No Testcontainers Docker -- embedded Postgres via `TestcontainersConfiguration`. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V121__add_xero_accounting_integration_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/AccountingXeroConnection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/AccountingXeroConnectionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroConnectionStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEntryRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncState.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncDirection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncEntityType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/SyncTrigger.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMapping.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingPaymentSource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/ExternalPaymentEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingSyncEntryRepositoryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingTaxCodeMappingServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/InvoiceSyncRequest.java` -- add `externalReference`, `customerEmail`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/LineItem.java` -- add `taxMode`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java` -- implement `AccountingPaymentSource`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- add 3 new values

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java` -- entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegrationRepository.java` -- repository pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingProvider.java` -- existing port interface
- `backend/src/main/resources/db/migration/tenant/V120__add_ai_specialist_invocations_and_llm_calls.sql` -- migration format reference

### Architecture Decisions

- **Separate `AccountingPaymentSource` interface** ([ADR-279](../adr/ADR-279-sibling-payment-source-port.md)) -- interface segregation; push and pull are orthogonal capabilities. `XeroAccountingProvider` implements both; `NoOpAccountingProvider` implements both (empty-list return for pull).
- **OAuth2 augmentation via separate table** ([ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md)) -- `accounting_xero_connection` is Xero-specific metadata; adding 10 nullable columns to `OrgIntegration` for one provider would violate single-responsibility.
- **Idempotent push via external reference** ([ADR-278](../adr/ADR-278-idempotent-push-via-external-reference.md)) -- `external_reference` field on `AccountingSyncEntry` carries the Kazi-side dedup key (`KAZI-INV-{uuid}` for invoices, `KAZI-CUST-{uuid}` for customers).
- **No `PlanTier`** -- capabilities (`INTEGRATION_MANAGE`, `INTEGRATION_VIEW_SYNC_STATUS`, `FINANCIAL_RECONCILE`) are the sole authorisation mechanism.

### Non-scope

- No sync service logic (lands in 518A).
- No Xero API client or OAuth service (lands in 519A).
- No controller endpoints (land in 524A).
- No frontend (lands in 524B/525B).

---

## Epic 518: AccountingSyncService + Worker + Event Listeners

**Goal**: Build the core sync orchestration engine -- the `AccountingSyncService` that enqueues invoice and customer pushes, the `AccountingSyncWorker` that drains the sync queue with retry and back-off, the `AccountingPaymentPollWorker` skeleton for payment pull, and the `AccountingSyncEventListener` that subscribes to invoice and customer domain events to trigger sync automatically.

**References**: Architecture Section 11.3.2-11.3.4 (Invoice Push, Customer Push, Payment Pull flows), Section 11.3.7 (Retry and Dead-Letter), Section 11.3.8 (Rate Limit Handling), Section 11.10 Slices B+F; [ADR-274](../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md), [ADR-277](../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md).

**Dependencies**: Epic 517 (entities + repos + enums + port extensions).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **518A** | 518A.1-518A.6 | ~8 backend files (1 service + 1 worker + 1 event listener + 1 record + 4 test files) | `AccountingSyncService` (enqueue invoice/customer push, retry from dead-letter, sync summary, poll orchestration); `AccountingSyncWorker` (`@Scheduled(fixedDelay = 30_000)` drain worker with exponential back-off and dead-letter); `AccountingSyncEventListener` (subscribes to `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoiceVoidedEvent`, `CustomerCreatedEvent`, `CustomerUpdatedEvent`); `TrustBoundaryDecision` record. **Done** (PR #1327) |
| **518B** | 518B.1-518B.3 | ~4 backend files (1 worker + 1 test file + 2 modifications) | `AccountingPaymentPollWorker` skeleton (`@Scheduled(fixedDelay = 900_000)`); tenant iteration via `TenantScopedRunner.forEachTenant()`; connection status check; cursor update; wired to `NoOpAccountingProvider` until 522A replaces with real adapter. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 518A.1 | Create `TrustBoundaryDecision` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryDecision.java` | covered by 518A.6 | architecture Section 11.6 pseudocode | `public record TrustBoundaryDecision(boolean allowed, String reason) { public static TrustBoundaryDecision allowed() {...} public static TrustBoundaryDecision refused(String reason) {...} }`. Placed in sync package; consumed by `AccountingSyncService` and `TrustBoundaryGuard` (521A). |
| 518A.2 | Create `AccountingSyncService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` | 518A.6 | architecture Section 11.3.2-11.3.4 service signatures | `@Service`. Methods: `enqueueInvoicePush(UUID invoiceId, SyncTrigger trigger)` -- checks for existing CONNECTED Xero connection, checks for active pending/in-flight entry (idempotent guard), creates `AccountingSyncEntry` with PENDING state; `enqueueCustomerPush(UUID customerId, SyncTrigger trigger)` -- same pattern; `retryFromDeadLetter(UUID syncEntryId)` -- resets entry for manual retry; `getSyncSummary()` -- count by state + oldest pending + last completed; `pollPaymentsForConnection(UUID connectionId)` -- skeleton that loads connection, delegates to provider, updates cursor. Trust guard integration point is a method parameter `TrustBoundaryDecision` -- the guard call itself is wired in 521A. Initially the trust guard is null (no-op allow-all) until 521A integrates it. |
| 518A.3 | Create `AccountingSyncWorker` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java` | 518A.6 | architecture Section 11.3.2 step 6 (worker drain); existing `@Scheduled` pattern in Phase 70 `AiInvocationExpirySweeper` | `@Service`. `@Scheduled(fixedDelay = 30_000)`. `drainPendingEntries()`: uses `TenantScopedRunner.forEachTenant()` to iterate active tenants; for each tenant, queries `AccountingSyncEntryRepository.findDrainableEntries(Instant.now(), PageRequest.of(0, 25))`; for each entry: marks `IN_FLIGHT`, resolves `AccountingProvider` from `IntegrationRegistry`, builds request, calls `syncInvoice`/`syncCustomer`, classifies result (success/rate-limited/transient/validation/auth), updates state. Retry back-off schedule: attempts 1-5 at 1m, 5m, 15m, 1h, 6h. After attempt 5: `DEAD_LETTER`. Rate limit: catches `XeroRateLimitException`, stops draining for this tenant for remainder of cycle. |
| 518A.4 | Create `AccountingSyncEventListener` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEventListener.java` | 518A.6 | existing event listener pattern in `notification/NotificationEventHandler.java` or `portal/PortalContactAutoProvisioner.java` | `@Component`. Methods: `@TransactionalEventListener(phase = AFTER_COMMIT) void onInvoiceApproved(InvoiceApprovedEvent e)` -- calls `syncService.enqueueInvoicePush(e.invoiceId(), SyncTrigger.EVENT)`; `onInvoiceSent(InvoiceSentEvent e)` -- same; `onInvoiceVoided(InvoiceVoidedEvent e)` -- same; `onCustomerCreated(CustomerCreatedEvent e)` -- calls `syncService.enqueueCustomerPush(e.customerId(), SyncTrigger.EVENT)`; `onCustomerUpdated(CustomerUpdatedEvent e)` -- same. Each handler checks for CONNECTED Xero connection first; if none, returns silently (no-op provider fallback). Import events from `io.b2mash.b2b.b2bstrawman.event.*` and `io.b2mash.b2b.b2bstrawman.customerbackend.event.*`. |
| 518A.5 | Create domain events for sync lifecycle | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/XeroSyncEntryCompletedEvent.java`, `XeroSyncEntryDeadLetteredEvent.java` | covered by 518A.6 | existing event pattern in `event/InvoiceApprovedEvent.java` | Records published by `AccountingSyncService` / `AccountingSyncWorker` after state transitions commit. `XeroSyncEntryCompletedEvent(UUID syncEntryId, SyncEntityType entityType, UUID entityId, String externalId)`. `XeroSyncEntryDeadLetteredEvent(UUID syncEntryId, SyncEntityType entityType, UUID entityId, String errorCode)`. Published via `ApplicationEventPublisher`. |
| 518A.6 | Integration tests for sync service + worker + event listener | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncServiceTest.java`, `AccountingSyncWorkerTest.java` | ~8 tests: (1) enqueueInvoicePush creates PENDING entry; (2) idempotent re-enqueue of pending entry is no-op; (3) worker marks entry IN_FLIGHT then COMPLETED on success; (4) worker retries with back-off on transient error; (5) worker moves to DEAD_LETTER after 5 attempts; (6) validation error goes straight to DEAD_LETTER; (7) retryFromDeadLetter resets entry to PENDING; (8) event listener fires enqueue on InvoiceApprovedEvent | existing service test pattern; use `@MockitoBean` on `AccountingProvider` (no real Xero calls) | Standard `@SpringBootTest` setup with `@MockitoBean` for `AccountingProvider`. Verify state machine transitions, back-off schedule values, idempotency guard, and event listener wiring. |
| 518B.1 | Create `AccountingPaymentPollWorker` skeleton | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` | 518B.3 | architecture Section 11.3.4 step 1-2 | `@Service`. `@Scheduled(fixedDelay = 900_000)` (15 minutes). `pollAllConnections()`: uses `TenantScopedRunner.forEachTenant()`; for each tenant, loads `AccountingXeroConnectionRepository.findByStatus("CONNECTED")`; for each connection, calls `AccountingSyncService.pollPaymentsForConnection(connection.getId())`; wraps in try-catch for per-connection exception isolation. Skeleton version: the actual `pollPaymentsForConnection` body delegates to `AccountingPaymentSource.getPaymentsModifiedSince()` which returns empty list from `NoOpAccountingProvider` -- full wiring to Xero lands in 522A. |
| 518B.2 | Wire payment poll into AccountingSyncService | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` (modify) | 518B.3 | architecture Section 11.3.4 steps 2-4 | Add `pollPaymentsForConnection(UUID connectionId)` implementation: load connection, get provider via `IntegrationRegistry`, check `instanceof AccountingPaymentSource`, call `getPaymentsModifiedSince(connection.getLastPollAt())`, iterate results with matching stub (full matching logic in 522A), update `connection.setLastPollAt(Instant.now())`. |
| 518B.3 | Payment poll worker tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorkerTest.java` | ~3 tests: (1) poll worker iterates tenants and calls pollPaymentsForConnection; (2) poll worker skips REVOKED connections; (3) poll worker handles exception from one connection without affecting others | existing test pattern | `@MockitoBean` on `AccountingPaymentSource`. Verify tenant iteration, connection status filter, exception isolation. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryDecision.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorker.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEventListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/XeroSyncEntryCompletedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/XeroSyncEntryDeadLetteredEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncWorkerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorkerTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceApprovedEvent.java` -- event shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/CustomerCreatedEvent.java` -- event shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` -- event listener pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` -- provider resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` -- tenant iteration (verify class name; may be `RequestScopes` + provisioning iteration pattern)

### Architecture Decisions

- **Dedicated `AccountingSyncService`** ([ADR-274](../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)) -- retry/idempotency/rate-limit semantics do not fit the Phase 37 rule executor. The sync service is the sole sync path.
- **Event-driven enqueue** -- `AccountingSyncEventListener` is the only automated push trigger. No controller or rule engine action calls `enqueue*` directly (manual resync creates a new entry via `retryFromDeadLetter` or `forceResync`).
- **Polling over webhooks for payment pull** ([ADR-277](../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)) -- 15-minute polling is sufficient for v1; webhook reliability and public-endpoint configuration burden deferred.

### Non-scope

- No Xero API calls (lands in 519A/520A).
- No trust boundary guard logic (lands in 521A).
- No payment matching logic beyond skeleton (lands in 522A).
- No controller endpoints (lands in 524A/525A).

---

## Epic 519: XeroApiClient + XeroOAuthService

**Goal**: Build the HTTP transport layer for Xero and the OAuth2 lifecycle management. The `XeroApiClient` wraps the Xero REST API with bearer-token attachment, automatic refresh-on-401, rate-limit header observance, and the `Xero-tenant-id` header. The `XeroOAuthService` manages the full OAuth2 authorization code flow with PKCE -- initiate, callback, token refresh, and disconnect.

**References**: Architecture Section 11.3.1 (OAuth2 Connection Flow), Section 11.3.8 (Rate Limit Handling), Section 11.10 Slice C; [ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md).

**Dependencies**: Epic 517A (`AccountingXeroConnection` entity + repo, `SecretStore`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **519A** | 519A.1-519A.6 | ~8 backend files (1 API client + 1 OAuth service + 1 exception + 1 config + 4 test files) | `XeroApiClient` (`RestClient` wrapper); `XeroOAuthService` (OAuth2 lifecycle); `XeroRateLimitException`; Xero configuration properties; token exchange + refresh cycle + refresh failure cascade + rate limit header parsing tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 519A.1 | Create Xero configuration properties | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroProperties.java` | covered by 519A.6 | existing config properties pattern; use `@ConfigurationProperties(prefix = "kazi.xero")` | Properties: `clientId`, `clientSecret`, `redirectUri`, `authorizeUrl` (default `https://login.xero.com/identity/connect/authorize`), `tokenUrl` (default `https://identity.xero.com/connect/token`), `connectionsUrl` (default `https://api.xero.com/connections`), `apiBaseUrl` (default `https://api.xero.com/api.xro/2.0`), `revocationUrl`. Add corresponding entries in `application-test.yml` with test values. |
| 519A.2 | Create `XeroRateLimitException` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroRateLimitException.java` | covered by 519A.6 | architecture Section 11.3.8 | `public class XeroRateLimitException extends RuntimeException { private final Duration retryAfter; ... }`. Carries `retryAfter` from Xero's `Retry-After` response header. Caught by `AccountingSyncWorker` to pause tenant drain. |
| 519A.3 | Create `XeroApiClient` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroApiClient.java` | 519A.6 | existing `RestClient` usage pattern in the codebase | `@Service`. Uses Spring's `RestClient` (not `WebClient`). Methods: `createOrUpdateInvoice(String xeroTenantId, Map<String, Object> invoicePayload, String accessToken)` -- POST/PUT to `/Invoices`; `createOrUpdateContact(String xeroTenantId, Map<String, Object> contactPayload, String accessToken)` -- POST/PUT to `/Contacts`; `getInvoicesModifiedSince(String xeroTenantId, Instant since, String accessToken)` -- GET `/Invoices?where=UpdatedDateUTC>{since}&Statuses=PAID`; `getContacts(String xeroTenantId, int page, String accessToken)` -- GET `/Contacts?page={page}`; `getTaxRates(String xeroTenantId, String accessToken)` -- GET `/TaxRates`; `getConnections(String accessToken)` -- GET `/connections`. Each method: attaches `Authorization: Bearer {token}` + `Xero-tenant-id: {xeroTenantId}` headers, reads `X-Rate-Limit-Remaining` and `Retry-After` from response, throws `XeroRateLimitException` when remaining < 5, returns deserialized JSON. Refresh-on-401 is handled by calling `XeroOAuthService.refreshAccessToken()` on 401 response and retrying once. |
| 519A.4 | Create `XeroOAuthService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroOAuthService.java` | 519A.6 | architecture Section 11.3.1 (OAuth2 Connection Flow) | `@Service`. Records: `XeroConnectResult(String authorizationUrl, String state)`, `XeroCallbackResult(UUID connectionId, String xeroOrgName)`. Methods: `initiateConnect(UUID memberId)` -- generates PKCE verifier + state, builds authorization URL with scopes `offline_access openid profile email accounting.transactions accounting.contacts`, stores state-to-verifier mapping in `SecretStore`; `handleCallback(String code, String state, UUID memberId)` -- validates state, exchanges code for tokens via Xero token endpoint, calls `/connections` for tenant ID + org name, upserts `OrgIntegration`, creates `AccountingXeroConnection`, stores tokens in `SecretStore` keyed `{orgIntegrationId}:xero:access` and `{orgIntegrationId}:xero:refresh`, pre-seeds tax mappings (calls `AccountingTaxCodeMappingService`), emits audit event; `disconnect(UUID memberId)` -- revokes refresh token, marks connection REVOKED, deletes tokens from SecretStore, disables OrgIntegration; `refreshAccessToken(UUID connectionId)` -- retrieves refresh token, calls token endpoint, stores new tokens, updates `last_token_refresh_at` and `access_token_expires_at`, after 3 consecutive failures transitions to REFRESH_FAILED. |
| 519A.5 | Create Xero domain events | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroConnectionEstablishedEvent.java`, `XeroConnectionRevokedEvent.java` | covered by 519A.6 | existing event pattern | `XeroConnectionEstablishedEvent(UUID connectionId, String xeroOrgName, UUID memberId)`. `XeroConnectionRevokedEvent(UUID connectionId, String xeroOrgName, UUID memberId)`. Published after connection state changes commit. |
| 519A.6 | Integration tests for OAuth service + API client | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroOAuthServiceTest.java`, `XeroApiClientTest.java` | ~7 tests: (1) initiateConnect generates valid authorization URL with PKCE; (2) handleCallback exchanges code for tokens and creates connection; (3) handleCallback stores tokens in SecretStore; (4) refreshAccessToken updates tokens on success; (5) 3 consecutive refresh failures transition connection to REFRESH_FAILED; (6) disconnect revokes token and marks REVOKED; (7) XeroApiClient throws XeroRateLimitException when remaining < 5 | `@MockitoBean` on `RestClient` (mock HTTP); existing `@SpringBootTest` test shape | Mock the Xero HTTP endpoints via `@MockitoBean` on the `RestClient.Builder` or use `MockRestServiceServer`. No WireMock, no fake HTTP server per repo convention. Test that token storage uses `SecretStore` correctly. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroProperties.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroRateLimitException.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroApiClient.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroOAuthService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroConnectionEstablishedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroConnectionRevokedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroOAuthServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroApiClientTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java` -- upsert target
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationService.java` -- integration management pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/AccountingXeroConnection.java` -- connection entity (517A)
- SecretStore service (find via `grep -r "SecretStore" backend/src/main/java/`)

### Architecture Decisions

- **OAuth2 tokens in SecretStore** ([ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md)) -- refresh tokens and access tokens are never stored in `AccountingXeroConnection`; they live in the encrypted `SecretStore` keyed by `{orgIntegrationId}:xero:access|refresh`.
- **Refresh-on-401 strategy** -- `XeroApiClient` catches 401, calls `XeroOAuthService.refreshAccessToken()`, retries the original request once. If refresh fails, the error propagates and the sync worker classifies it as `AUTH_EXPIRED`.
- **PKCE flow** -- Authorization code flow with PKCE (code verifier + code challenge) for security. State parameter stored in `SecretStore` for validation on callback.

### Non-scope

- No provider adapter (lands in 520A).
- No payload mappers (lands in 520A/520B).
- No controller endpoints (lands in 524A).

---

## Epic 520: XeroAccountingProvider Adapter + Mappers

**Goal**: Build the Xero-specific implementation of `AccountingProvider` and `AccountingPaymentSource` -- the adapter that translates Kazi domain objects into Xero API payloads and Xero responses back into Kazi result types. Includes two pure-function payload mappers for invoice and contact translation, with tax code resolution through the `AccountingTaxCodeMappingService`.

**References**: Architecture Section 11.8.1 (Implementation Guidance), Section 11.10 Slice D; [ADR-272](../adr/ADR-272-xero-only-accounting-adapter-v1.md).

**Dependencies**: Epic 517A (entities + repos + port interfaces), Epic 519A (`XeroApiClient` + `XeroOAuthService`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **520A** | 520A.1-520A.4 | ~6 backend files (1 provider + 1 mapper + 2 test files + 2 context reads) | `XeroAccountingProvider` (`implements AccountingProvider, AccountingPaymentSource`); `XeroInvoicePayloadMapper` (pure function: `InvoiceSyncRequest` + tax mappings to Xero invoice JSON); invoice mapping accuracy + syncInvoice + syncCustomer + getPaymentsModifiedSince tests. |
| **520B** | 520B.1-520B.3 | ~4 backend files (1 mapper + 1 service integration + 2 test files) | `XeroContactPayloadMapper` (pure function: `CustomerSyncRequest` to Xero contact JSON); wire `AccountingTaxCodeMappingService` into `XeroInvoicePayloadMapper` for tax code resolution; contact mapping + tax code resolution tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 520A.1 | Create `XeroAccountingProvider` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroAccountingProvider.java` | 520A.4 | existing `NoOpAccountingProvider.java` as adapter pattern; `@IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "xero")` | `@Component @IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "xero")`. Implements `AccountingProvider` + `AccountingPaymentSource`. Constructor injects `XeroApiClient`, `XeroOAuthService`, `XeroInvoicePayloadMapper`, `XeroContactPayloadMapper`, `AccountingXeroConnectionRepository`, `SecretStore`. `syncInvoice(InvoiceSyncRequest)`: loads connection, gets access token from SecretStore, delegates to mapper for payload, calls `xeroApiClient.createOrUpdateInvoice()`, returns `AccountingSyncResult`. `syncCustomer(CustomerSyncRequest)`: same pattern with contact mapper. `getPaymentsModifiedSince(Instant since)`: calls `xeroApiClient.getInvoicesModifiedSince()`, maps Xero paid-invoice response to `List<ExternalPaymentEvent>`. `testConnection()`: calls `xeroApiClient.getConnections()` to verify token validity. |
| 520A.2 | Create `XeroInvoicePayloadMapper` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroInvoicePayloadMapper.java` | 520A.4 | architecture Section 11.8.1 | `@Component`. Pure function: `Map<String, Object> map(InvoiceSyncRequest request, List<AccountingTaxCodeMapping> taxMappings)`. Builds Xero invoice JSON shape: `Type=ACCREC`, `Contact.Name`, `Contact.EmailAddress`, `Date` (issue date), `DueDate`, `Reference` (set to `request.externalReference()` for idempotency per [ADR-278](../adr/ADR-278-idempotent-push-via-external-reference.md)), `Status=AUTHORISED`, `LineItems[]` with `Description`, `Quantity`, `UnitAmount`, `TaxType` (resolved from tax mappings by `lineItem.taxMode()`). Handles zero-rated disbursement lines (separate tax code). |
| 520A.3 | Create `XeroPaymentResponseMapper` (private/package-private in provider) | Within `XeroAccountingProvider.java` or as a package-private helper | covered by 520A.4 | architecture Section 11.3.4 step 2 | Maps Xero paid-invoice response JSON to `ExternalPaymentEvent` records. Extracts `Reference` field to match back to Kazi's `external_reference`. Extracts `Payments[0].Amount`, `Payments[0].PaymentID`, `Payments[0].Date`. |
| 520A.4 | Integration tests for provider + invoice mapper | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroAccountingProviderTest.java`, `XeroInvoicePayloadMapperTest.java` | ~6 tests: (1) invoice mapper produces correct Xero JSON shape with all fields; (2) invoice mapper resolves tax codes from mappings; (3) invoice mapper includes external_reference in Reference field; (4) syncInvoice delegates to XeroApiClient and returns success; (5) syncCustomer delegates correctly; (6) getPaymentsModifiedSince maps Xero response to ExternalPaymentEvent list | `@MockitoBean` on `XeroApiClient`; mapper tests are pure-function unit tests | Mapper tests do not need Spring context -- pure unit tests. Provider tests use `@MockitoBean` on `XeroApiClient` to verify delegation. |
| 520B.1 | Create `XeroContactPayloadMapper` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroContactPayloadMapper.java` | 520B.3 | architecture Section 11.8.1 | `@Component`. Pure function: `Map<String, Object> map(CustomerSyncRequest request)`. Builds Xero contact JSON shape: `Name`, `EmailAddress`, `Addresses[0]` (Street, City, PostalCode, Country), `AccountNumber` (set to external reference if present). |
| 520B.2 | Wire tax code mapping into invoice mapper | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroInvoicePayloadMapper.java` (modify), `AccountingTaxCodeMappingService.java` (read) | covered by 520B.3 | -- | Inject `AccountingTaxCodeMappingService` into `XeroInvoicePayloadMapper` constructor. Add `resolveForTaxMode(providerId, kaziTaxMode)` call in the mapper for each line item. Handle missing mapping gracefully (default to `NONE` if no mapping found). |
| 520B.3 | Contact mapper + tax code resolution tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroContactPayloadMapperTest.java` | ~4 tests: (1) contact mapper produces correct Xero contact JSON; (2) contact mapper handles null/empty address fields; (3) tax code resolution falls back to NONE for unknown tax mode; (4) tax code resolution uses tenant-specific mappings | pure unit tests for mapper; Spring context test for resolution | |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroAccountingProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroInvoicePayloadMapper.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroContactPayloadMapper.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroAccountingProviderTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroInvoicePayloadMapperTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroContactPayloadMapperTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/AccountingProvider.java` -- port interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/NoOpAccountingProvider.java` -- adapter pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationAdapter.java` -- annotation

### Architecture Decisions

- **Xero-only adapter** ([ADR-272](../adr/ADR-272-xero-only-accounting-adapter-v1.md)) -- no Sage Pastel or QuickBooks stubs. Ship one adapter done well.
- **Idempotent push via Reference field** ([ADR-278](../adr/ADR-278-idempotent-push-via-external-reference.md)) -- the `Reference` field on the Xero invoice carries `KAZI-INV-{uuid}`. Re-pushing updates the existing Xero record.
- **Pure-function mappers** -- mappers are stateless `@Component` beans that transform Kazi DTOs to Xero JSON shapes. No side effects, no API calls. Easy to unit test.

### Non-scope

- No sync service integration (already done in 518A).
- No trust boundary guard (lands in 521A).
- No controller endpoints (lands in 524A).

---

## Epic 521: Trust Boundary Guard

**Goal**: Implement the regulatory safeguard mandated by the Legal Practice Act Section 86. The `TrustBoundaryGuard` is a deterministic Java service that evaluates three conditions and refuses to push any trust-related invoice to Xero. The guard is fail-closed -- if any trust-related entity lookup fails, the push is refused. For non-legal tenants (no trust accounting tables provisioned), the guard allows all pushes.

**References**: Architecture Section 11.6 (Trust Accounting Guard -- full specification including pseudocode), Section 11.10 Slice E; [ADR-276](../adr/ADR-276-trust-accounting-hard-guard-export.md).

**Dependencies**: Epic 518A (`AccountingSyncService.enqueueInvoicePush` -- integration point for guard call).

**Scope**: Backend only

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **521A** | 521A.1-521A.4 | ~5 backend files (1 guard service + 1 sync service modification + 1 audit integration + 2 test files) | `TrustBoundaryGuard` service with three guard conditions; integration with `AccountingSyncService.enqueueInvoicePush`; audit event emission for blocked pushes; comprehensive guard condition tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 521A.1 | Create `TrustBoundaryGuard` service | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryGuard.java` | 521A.4 | architecture Section 11.6 pseudocode (verbatim) | `@Service`. Constructor injects `DisbursementRepository` (from `verticals/legal/disbursement/`) and `ClientLedgerCardRepository` (from `verticals/legal/trustaccounting/ledger/`). Method `evaluate(Invoice invoice, List<InvoiceLine> lines, Customer customer)` returns `TrustBoundaryDecision`. Three conditions checked in order: (1) `invoice.getCustomFields().get("is_trust_invoice") == true`; (2) any `InvoiceLine.disbursementId != null` with linked disbursement having `trustAccountId != null`; (3) `clientLedgerCardRepository.sumNonZeroBalancesForCustomer(customer.getId()) != 0`. Fail-closed: if any lookup throws, return `refused("Guard evaluation failed: " + e.getMessage())`. Skip for non-legal: if `DisbursementRepository` bean is not present or trust tables do not exist, return `allowed()` (use `@Autowired(required = false)` or try-catch on repository call). |
| 521A.2 | Integrate guard with `AccountingSyncService.enqueueInvoicePush` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` (modify) | 521A.4 | architecture Section 11.3.2 steps 3-4 | Modify `enqueueInvoicePush` to: (1) load Invoice + InvoiceLines + Customer; (2) call `trustBoundaryGuard.evaluate(invoice, lines, customer)`; (3) if refused: create sync entry with `state = BLOCKED_TRUST_BOUNDARY`, set `lastErrorCode = "TRUST_BOUNDARY"`, set `lastErrorDetail = decision.reason()`, emit audit event `integration.xero.push_blocked_trust`; (4) if allowed: proceed with existing PENDING enqueue. Also add currency check before enqueue: if `invoice.currency != orgSettings.currency`, create entry with `state = DEAD_LETTER`, `lastErrorCode = MULTI_CURRENCY`. |
| 521A.3 | Audit event emission for blocked pushes | Within `AccountingSyncService.java` (already modified in 521A.2) | covered by 521A.4 | architecture Section 11.6 audit event structure; existing audit emission pattern in `notification/NotificationEventHandler.java` | Emit audit event `integration.xero.push_blocked_trust` with details: `invoiceNumber`, `customerName`, `reason`, `syncEntryId`. Use existing `ApplicationEventPublisher` + audit event handler pattern. |
| 521A.4 | Trust boundary guard integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryGuardTest.java` | ~7 tests: (1) invoice with `is_trust_invoice=true` is refused; (2) invoice line linked to trust-account disbursement is refused; (3) customer with non-zero trust balance is refused; (4) non-trust invoice is allowed; (5) fail-closed on DB error (repository throws) results in refused; (6) non-legal tenant (no trust tables) results in allowed; (7) enqueueInvoicePush creates BLOCKED_TRUST_BOUNDARY entry when guard refuses | `@SpringBootTest` with real embedded Postgres | Setup: provision a test tenant, create trust-related entities (TrustAccount, LegalDisbursement, ClientLedgerCard) for some tests, omit them for others. Verify that the guard correctly evaluates all three conditions and that the sync service creates the correct entry states. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryGuard.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryGuardTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` -- add trust guard call + currency check in `enqueueInvoicePush`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursement.java` -- disbursement entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementRepository.java` -- disbursement repo
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCard.java` -- ledger card entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository.java` -- ledger card repo
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- invoice entity + `customFields` JSONB

### Architecture Decisions

- **Hard guard, no bypass** ([ADR-276](../adr/ADR-276-trust-accounting-hard-guard-export.md)) -- Section 86 regulatory boundary. No UI action, no API endpoint, no configuration flag can bypass the guard. The entry is a permanent record of the refusal.
- **Fail-closed** -- if any trust-related entity lookup fails (DB error, missing data), the guard refuses. Trust boundary violations are worse than missed syncs.
- **Skip for non-legal tenants** -- if the Phase 60 trust accounting tables do not exist in this tenant's schema (e.g. accounting-za or consulting-za vertical), the guard allows all pushes. The check is skipped entirely.

### Non-scope

- No UI surface for trust boundary blocks (lands in 525B as a badge in the sync log and invoice detail page).

---

## Epic 522: Payment Pull (Poll Worker Completion)

**Goal**: Complete the payment pull flow by wiring the `AccountingPaymentPollWorker` (created as skeleton in 518B) to the real `XeroAccountingProvider.getPaymentsModifiedSince()` adapter. Implements the full payment matching logic: match Xero payments to Kazi invoices via external reference, detect amount drift, create `PaymentEvent` records, and transition invoices to PAID.

**References**: Architecture Section 11.3.4 (Payment Pull Flow), Section 11.10 Slice F; [ADR-277](../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md).

**Dependencies**: Epic 518A (`AccountingSyncService.pollPaymentsForConnection` skeleton), Epic 520A (`XeroAccountingProvider.getPaymentsModifiedSince`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **522A** | 522A.1-522A.4 | ~5 backend files (2 service modifications + 1 event + 2 test files) | Complete `pollPaymentsForConnection` body; payment matching logic; `PaymentEvent` creation with `provider_slug = "xero"`; invoice transition to PAID; amount drift detection; `RECONCILE_DRIFT` state; `XeroPaymentReconciledEvent`. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 522A.1 | Complete `AccountingSyncService.pollPaymentsForConnection` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` (modify) | 522A.4 | architecture Section 11.3.4 steps 2-4 | Replace skeleton with full implementation. For each `ExternalPaymentEvent` from provider: (1) look up Kazi invoice via `AccountingSyncEntryRepository.findCompletedPushByExternalReference(event.externalInvoiceReference())`; (2) if not found: log as Xero-native invoice, skip; (3) if found and Kazi invoice not yet PAID: check amount `|event.amount - invoice.total| > 0.01` -- if drift, create sync entry with `RECONCILE_DRIFT` state + `DRIFT_DETECTED` error code + audit event; if match, create `PaymentEvent` with `provider_slug = "xero"` and `payment_reference = event.externalPaymentId()` and `payment_destination = "XERO_RECONCILE"`, call `InvoiceTransitionService.recordPayment(invoiceId, paymentRef)`, create sync entry with `COMPLETED` + `direction = PULL` + audit event; (4) if already PAID: skip (idempotent). Update `connection.setLastPollAt(Instant.now())`. Return `PaymentPollSummary(matched, drifted, skipped)`. |
| 522A.2 | Wire poll worker to real adapter | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` (modify) | 522A.4 | -- | Modify the poll worker to resolve `XeroAccountingProvider` from `IntegrationRegistry` instead of `NoOpAccountingProvider`. The worker already calls `pollPaymentsForConnection` -- the change is in the service method body (522A.1), not the worker itself. Verify the provider resolution uses `IntegrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class)` or similar pattern. |
| 522A.3 | Create `XeroPaymentReconciledEvent` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/XeroPaymentReconciledEvent.java` | covered by 522A.4 | existing event pattern | `XeroPaymentReconciledEvent(UUID invoiceId, String invoiceNumber, BigDecimal amount, String xeroPaymentId)`. Published after successful payment match and invoice transition. |
| 522A.4 | Payment pull integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollIntegrationTest.java` | ~5 tests: (1) happy-path payment match creates PaymentEvent and transitions invoice to PAID; (2) amount drift creates RECONCILE_DRIFT sync entry; (3) Xero-native invoice (no matching external_reference) is skipped; (4) already-PAID invoice is skipped (idempotent); (5) poll updates connection.lastPollAt | `@MockitoBean` on `AccountingPaymentSource` | Setup: create invoices with completed PUSH sync entries containing external_references. Mock `getPaymentsModifiedSince` to return matching payment events. Verify PaymentEvent creation and invoice status transition. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/XeroPaymentReconciledEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollIntegrationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` -- complete `pollPaymentsForConnection`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingPaymentPollWorker.java` -- wire to real adapter

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceTransitionService.java` -- `recordPayment` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentEvent.java` -- PaymentEvent entity
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentEventRepository.java` -- for dedup check

### Architecture Decisions

- **Polling over webhooks** ([ADR-277](../adr/ADR-277-poll-over-webhooks-payment-reconciliation-v1.md)) -- 15-minute poll interval is sufficient for v1 AR aging freshness.
- **Amount drift detection** -- if `|xeroAmount - kaziTotal| > 0.01`, the entry goes to `RECONCILE_DRIFT` with `DRIFT_DETECTED` error code. Manual reconciliation (capability `FINANCIAL_RECONCILE`) is required to close the drift.
- **PaymentEvent reuse** -- Xero-originated payments create `PaymentEvent` records with `provider_slug = "xero"` and `payment_destination = "XERO_RECONCILE"`, consistent with the existing Phase 25 payment model.

### Non-scope

- No manual reconciliation endpoint (lands in 524A as `POST /api/integrations/sync/{entryId}/reconcile`).
- No frontend drift UI (lands in 525B).

---

## Epic 523: One-Time Customer Import

**Goal**: Implement the one-time customer import from Xero contacts. When a Xero connection is first established, the integration UI offers an import flow that paginates Xero contacts, deduplicates against existing Kazi customers, and creates new `Customer` records in `PROSPECT` status with `external_reference` set to the Xero contact ID.

**References**: Architecture Section 11.3.5 (One-Time Customer Import), Section 11.10 Slice G.

**Dependencies**: Epic 519A (`XeroApiClient.getContacts` for pagination), Epic 520A (`XeroAccountingProvider` for provider resolution).

**Scope**: Backend only

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **523A** | 523A.1-523A.3 | ~4 backend files (1 service + 1 summary record + 2 test files) | `XeroCustomerImportService`; Xero contact pagination; dedup by email then name+taxNumber; one-time guard; customer creation with PROSPECT status + external_reference + IMPORTED_FROM_XERO tag; import summary. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 523A.1 | Create `CustomerImportSummary` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/CustomerImportSummary.java` | covered by 523A.3 | existing record style | `public record CustomerImportSummary(int created, int skippedDuplicate, int skippedNoEmail, int total) {}` |
| 523A.2 | Create `XeroCustomerImportService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroCustomerImportService.java` | 523A.3 | architecture Section 11.3.5 | `@Service`. Method: `importCustomersFromXero(UUID connectionId, UUID actorMemberId)` returns `CustomerImportSummary`. Steps: (1) load `AccountingXeroConnection`, verify `status = CONNECTED`; (2) check one-time guard -- if connection's `configJson` contains `"customersImported": true`, throw `ResourceConflictException(409)`; (3) get access token from `SecretStore`; (4) paginate Xero contacts via `XeroApiClient.getContacts(xeroTenantId, page, accessToken)` in a loop (pages of 100); (5) for each contact: skip if no email (`skippedNoEmail++`); match by email (case-insensitive) against existing customers -- if match, set `external_reference` on existing customer, `skippedDuplicate++`; match by (name, taxNumber) -- if match, same; create new `Customer` with `lifecycleStatus = PROSPECT`, `external_reference = xeroContactId`, tag `IMPORTED_FROM_XERO`; (6) mark connection as imported (set `customersImported: true` in configJson or a dedicated boolean); (7) emit audit event `integration.xero.customers_imported` with summary; (8) return summary. |
| 523A.3 | Customer import integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroCustomerImportServiceTest.java` | ~5 tests: (1) import creates new customers for contacts with no Kazi match; (2) import skips contacts without email; (3) import deduplicates by email (case-insensitive); (4) import deduplicates by name+taxNumber when email doesn't match; (5) second import attempt returns 409 (one-time guard) | `@MockitoBean` on `XeroApiClient` | Mock `getContacts` to return test contact data. Verify customer creation calls, dedup logic, and one-time guard. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroCustomerImportService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/CustomerImportSummary.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroCustomerImportServiceTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- customer creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- dedup query (findByEmail)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroApiClient.java` -- `getContacts` method (519A)

### Architecture Decisions

- **One-time import** -- the import runs once per connection. Re-running requires disconnecting and reconnecting. This prevents duplicate imports and keeps Kazi as the source of truth for customer data after the initial migration.
- **PROSPECT status** -- imported customers are created in PROSPECT status, not ACTIVE. This follows the existing `CustomerService` lifecycle: PROSPECT customers can be reviewed and transitioned to ACTIVE by the firm.
- **No ongoing customer pull** -- permanent decision. After import, Kazi is the source of truth.

### Non-scope

- No controller endpoint (lands in 524A).
- No frontend import button (lands in 524B).

---

## Epic 524: Frontend -- Connection Management + Settings

**Goal**: Build the Xero integration settings page and the REST controller that powers it. The backend controller exposes all Xero management endpoints (OAuth connect/callback/disconnect, connection status, settings, tax mappings, customer import). The frontend delivers the full settings experience: connection card with connect/disconnect actions, tax code mapping editor, customer import button, sync settings form.

**References**: Architecture Section 11.4 (API Surface), Section 11.8.2 (Frontend Changes), Section 11.10 Slices H; [ADR-275](../adr/ADR-275-oauth2-augmentation-org-integration.md).

**Dependencies**: Epic 519A (`XeroOAuthService`), Epic 520B (`AccountingTaxCodeMappingService` integration).

**Scope**: Both (524A = backend controller, 524B = frontend)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **524A** | 524A.1-524A.4 | ~7 backend files (2 controllers + 1 DTO package + 4 test files) | `XeroIntegrationController` (OAuth connect/callback/disconnect + connection status + settings + tax mappings + customer import + tax rates proxy); `AccountingSyncController` (sync summary + entries + entry detail + invoice sync status + retry + resync + reconcile); controller RBAC gate tests. |
| **524B** | 524B.1-524B.7 | ~9 frontend files (1 page + 4 components + 1 settings form + 1 API client + 1 page modification + i18n) | Xero settings page; `XeroConnectionCard`; `XeroTaxMappingEditor`; `XeroCustomerImport`; `XeroSettingsForm`; API client functions; modified integrations page. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 524A.1 | Create `XeroIntegrationController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroIntegrationController.java` | 524A.4 | existing `IntegrationController.java` pattern; backend Controller Discipline (`backend/CLAUDE.md`) | `@RestController @RequestMapping("/api/integrations/xero")`. All methods are one-line service delegates. Endpoints: `GET /connect` (`@RequiresCapability("INTEGRATION_MANAGE")`) -- delegates to `xeroOAuthService.initiateConnect()`; `GET /callback` (`@RequiresCapability("INTEGRATION_MANAGE")`) -- delegates to `xeroOAuthService.handleCallback()`; `GET /connection` (`@RequiresCapability("INTEGRATION_MANAGE")`) -- delegates to `xeroOAuthService.getConnectionStatus()`; `DELETE /connection` (`@RequiresCapability("INTEGRATION_MANAGE")`) -- delegates to `xeroOAuthService.disconnect()`; `GET /tax-mappings` -- delegates to `taxCodeMappingService.getByProvider("xero")`; `PUT /tax-mappings/{id}` -- delegates to `taxCodeMappingService.update()`; `POST /tax-mappings/reset` -- delegates to `taxCodeMappingService.resetToDefaults()`; `GET /tax-rates` -- delegates to `xeroApiClient.getTaxRates()` (proxy); `POST /import-customers` -- delegates to `importService.importCustomersFromXero()`; `GET /settings` -- delegates to service; `PUT /settings` -- delegates to service. |
| 524A.2 | Create `AccountingSyncController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncController.java` | 524A.4 | Controller Discipline rules | `@RestController @RequestMapping("/api/integrations/sync")`. Endpoints: `GET /summary` (`@RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")`) -- delegates to `syncService.getSyncSummary()`; `GET /entries` (`@RequiresCapability("INTEGRATION_VIEW_SYNC_STATUS")`) -- delegates to `syncEntryRepository.findFiltered()` with `Pageable`; `GET /entries/{id}` -- delegates to `syncEntryRepository.findOneById()`; `GET /invoice/{invoiceId}/status` -- delegates to `syncEntryRepository.findByEntity(INVOICE, invoiceId)`; `POST /{entryId}/retry` -- delegates to `syncService.retryFromDeadLetter()`; `POST /invoice/{invoiceId}/resync` -- delegates to `syncService.enqueueInvoicePush(invoiceId, FORCE_RESYNC)`; `POST /{entryId}/reconcile` (`@RequiresCapability("FINANCIAL_RECONCILE")`) -- delegates to `syncService.resolveReconcileDrift()`. |
| 524A.3 | Create response DTOs | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/dto/XeroConnectionResponse.java`, `SyncSummaryResponse.java`, `SyncEntryResponse.java` | covered by 524A.4 | existing DTO pattern -- nested records or dedicated dto package | Response shapes per architecture Section 11.4 API surface. `XeroConnectionResponse`: id, xeroOrgName, status, connectedAt, lastTokenRefreshAt, accessTokenExpiresAt, scope, lastPollAt. `SyncSummaryResponse`: pending, inFlight, completedLast24h, failedRetrying, deadLetter, blockedTrustBoundary, reconcileDrift, oldestPendingAt, lastCompletedAt. `SyncEntryResponse`: all fields from entity mapped to camelCase JSON. |
| 524A.4 | Controller integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroIntegrationControllerIntegrationTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncControllerIntegrationTest.java` | ~8 tests: (1) GET /connect requires INTEGRATION_MANAGE; (2) GET /connection returns 404 when not connected; (3) DELETE /connection requires INTEGRATION_MANAGE; (4) GET /tax-mappings returns seeded defaults; (5) POST /import-customers requires INTEGRATION_MANAGE; (6) GET /sync/summary requires INTEGRATION_VIEW_SYNC_STATUS; (7) POST /sync/{entryId}/retry requires INTEGRATION_VIEW_SYNC_STATUS; (8) POST /sync/{entryId}/reconcile requires FINANCIAL_RECONCILE | MockMvc + `@SpringBootTest`; `@MockitoBean` on `XeroOAuthService`, `XeroApiClient` | Standard HTTP integration tests. Verify RBAC gates by testing with member JWTs that have/lack required capabilities. |
| 524B.1 | Create Xero settings page | `frontend/app/(app)/org/[slug]/settings/integrations/xero/page.tsx` | -- | existing `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` pattern | Server component. Loads connection status, sync summary, tax mappings. Renders `XeroConnectionCard`, `XeroSyncSummary` (if connected), `XeroTaxMappingEditor` (if connected), `XeroCustomerImport` (if connected and not yet imported), `XeroSettingsForm` (if connected). |
| 524B.2 | Create `XeroConnectionCard` component | `frontend/components/integrations/XeroConnectionCard.tsx` | -- | existing `IntegrationCard.tsx` pattern | `"use client"`. Displays connection status: "Connected to {xeroOrgName}" (green) / "Reconnect required" (amber) / "Not connected" (default). Actions: "Connect to Xero" button (opens OAuth flow via `GET /connect` -> redirect to Xero consent screen), "Disconnect" button (confirmation dialog -> `DELETE /connection`). Wrap in `<CapabilityGate capability="INTEGRATION_MANAGE">`. |
| 524B.3 | Create `XeroTaxMappingEditor` component | `frontend/components/integrations/XeroTaxMappingEditor.tsx` | -- | table editor patterns in existing settings pages | `"use client"`. Table with columns: Kazi Tax Mode, Xero Tax Code (editable dropdown populated from `GET /tax-rates`), Display Label (editable). Save button per row calls `PUT /tax-mappings/{id}`. "Reset to Defaults" button calls `POST /tax-mappings/reset` with confirmation dialog. |
| 524B.4 | Create `XeroCustomerImport` component | `frontend/components/integrations/XeroCustomerImport.tsx` | -- | existing button + summary patterns | `"use client"`. Button "Import Customers from Xero" with loading state. Calls `POST /import-customers`. On success, displays summary: "{created} created, {skippedDuplicate} duplicates skipped, {skippedNoEmail} skipped (no email)". Button hidden after successful import. |
| 524B.5 | Create `XeroSettingsForm` component | `frontend/components/integrations/XeroSettingsForm.tsx` | -- | existing settings form patterns | `"use client"`. Fields: Payment poll interval (dropdown: 5/15/30/60 minutes), Push trigger (radio: "On approval" / "On send"), Auto-sync enabled (toggle). Save calls `PUT /settings`. |
| 524B.6 | Add API client functions | `frontend/lib/api/integrations.ts` (modify) | -- | existing API client pattern in same file | Add functions: `initiateXeroConnect()`, `getXeroConnection()`, `disconnectXero()`, `getXeroTaxMappings()`, `updateXeroTaxMapping(id, data)`, `resetXeroTaxMappings()`, `getXeroTaxRates()`, `importXeroCustomers()`, `getXeroSettings()`, `updateXeroSettings(data)`, `getSyncSummary()`, `getSyncEntries(params)`, `getSyncEntry(id)`, `getInvoiceSyncStatus(invoiceId)`, `retrySyncEntry(id)`, `forceResyncInvoice(invoiceId)`, `reconcileSyncEntry(id)`. |
| 524B.7 | Modify integrations settings page to add Xero card | `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` (modify) | -- | existing card pattern for other domains | Add a link card for Xero under the "Accounting" domain section. Card shows connection status (connected/disconnected) and links to `/settings/integrations/xero`. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroIntegrationController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/dto/XeroConnectionResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/dto/SyncSummaryResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/dto/SyncEntryResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroIntegrationControllerIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncControllerIntegrationTest.java`

**Create (frontend):**
- `frontend/app/(app)/org/[slug]/settings/integrations/xero/page.tsx`
- `frontend/components/integrations/XeroConnectionCard.tsx`
- `frontend/components/integrations/XeroTaxMappingEditor.tsx`
- `frontend/components/integrations/XeroCustomerImport.tsx`
- `frontend/components/integrations/XeroSettingsForm.tsx`

**Modify (frontend):**
- `frontend/lib/api/integrations.ts` -- add Xero API client functions
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` -- add Xero card

**Read for context:**
- `frontend/components/integrations/IntegrationCard.tsx` -- card component pattern
- `frontend/components/integrations/SetApiKeyDialog.tsx` -- dialog pattern
- `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts` -- server action pattern

### Architecture Decisions

- **Controller Discipline** -- both controllers are pure delegation. Every endpoint is a one-liner that calls one service method and wraps the result in `ResponseEntity`. No business logic, no orchestration, no helper methods.
- **Capability gates, not plan gates** -- all endpoints use `@RequiresCapability`. No `@RequiresPlan`, no `PlanTier`, no `<PlanGate>`.
- **Xero brand compliance** -- the "Connect to Xero" button should follow Xero's brand guidelines (blue button with Xero logo) as required for OAuth2 partner apps.

### Non-scope

- No sync log page (lands in 525B).
- No invoice/customer status chips (lands in 525B).

---

## Epic 525: Frontend -- Sync Log + Status Chips

**Goal**: Build the sync observability layer -- the sync log page with filterable, paginated entries, state badges, retry actions, and the inline status chips on invoice and customer detail pages. Also completes the sync summary widget on the Xero settings page and adds the controller endpoint tests for sync-specific queries.

**References**: Architecture Section 11.4.3 (Sync Status and Log), Section 11.8.2 (Frontend Changes), Section 11.10 Slice I.

**Dependencies**: Epic 518A (`AccountingSyncService` -- sync summary, entry queries), Epic 524A (`AccountingSyncController` endpoints).

**Scope**: Both (525A = backend test completion, 525B = frontend)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **525A** | 525A.1-525A.2 | ~3 backend files (1 service method + 1 test file + 1 DTO) | Add `resolveReconcileDrift(UUID entryId)` method to `AccountingSyncService`; add `PaymentPollSummary` record; sync controller integration test completion. |
| **525B** | 525B.1-525B.7 | ~9 frontend files (1 page + 3 components + 2 chips + 2 page modifications + actions) | Sync log page; `SyncLogTable`; `SyncEntryStateBadge`; `XeroSyncSummary` widget; `XeroStatusChip` on invoice detail; `XeroContactBadge` on customer detail. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 525A.1 | Add `resolveReconcileDrift` to AccountingSyncService | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` (modify) | 525A.2 | -- | New method `resolveReconcileDrift(UUID entryId, String resolution)`: loads entry, verifies state is `RECONCILE_DRIFT`, transitions to `COMPLETED`, sets `lastErrorDetail` to resolution note, emits audit event `integration.xero.reconcile_resolved`. |
| 525A.2 | Sync controller test completion | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncControllerIntegrationTest.java` (extend from 524A.4) | ~4 additional tests: (1) GET /sync/entries returns paginated results filtered by state; (2) GET /sync/invoice/{id}/status returns latest sync entry for invoice; (3) POST /sync/{entryId}/reconcile transitions RECONCILE_DRIFT to COMPLETED; (4) POST /sync/{entryId}/reconcile returns 403 without FINANCIAL_RECONCILE capability | add to existing test file from 524A.4 | |
| 525B.1 | Create sync log page | `frontend/app/(app)/org/[slug]/settings/integrations/xero/sync-log/page.tsx`, `frontend/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions.ts` | -- | existing paginated list page patterns | Server component. Loads sync entries with server-side pagination and filtering. URL query params: `state` (dropdown filter), `entityType` (dropdown filter), `page`, `size`. Renders `SyncLogTable`. Back link to Xero settings page. |
| 525B.2 | Create `SyncLogTable` component | `frontend/components/integrations/SyncLogTable.tsx` | -- | existing table patterns with action menus | `"use client"`. Columns: Entity Type (badge), Entity (link to invoice/customer), Direction (PUSH/PULL), State (via `SyncEntryStateBadge`), Attempts, Last Error, Created, Completed, Actions (dropdown: Retry if DEAD_LETTER, Open in Xero if COMPLETED with externalId, Reconcile if RECONCILE_DRIFT). Action handlers call API functions. |
| 525B.3 | Create `SyncEntryStateBadge` component | `frontend/components/integrations/SyncEntryStateBadge.tsx` | -- | existing badge patterns | Small component mapping `SyncState` to colored badge: PENDING (blue), IN_FLIGHT (amber), COMPLETED (green), FAILED_RETRYING (orange), DEAD_LETTER (red), BLOCKED_TRUST_BOUNDARY (purple), RECONCILE_DRIFT (yellow). |
| 525B.4 | Create `XeroSyncSummary` widget | `frontend/components/integrations/XeroSyncSummary.tsx` | -- | existing dashboard widget patterns | `"use client"`. Displays sync summary counts from `GET /sync/summary`. Each count is a clickable link to the sync log page filtered by that state. Shows `oldestPendingAt` and `lastCompletedAt` timestamps. Positioned on the Xero settings page (524B.1 already has a slot for it). |
| 525B.5 | Create `XeroStatusChip` for invoice detail | `frontend/components/invoices/XeroStatusChip.tsx` | -- | existing inline status chip patterns | `"use client"`. Inline chip on the invoice detail page. Calls `GET /sync/invoice/{invoiceId}/status` (or receives data from parent). States: "Not synced" (gray), "Pending" (blue), "Synced to Xero" (green, with external ID link), "Failed" (red, with last error + Retry button), "Blocked (trust)" (purple, with audit link), "Reconcile drift" (yellow, with reconcile action if FINANCIAL_RECONCILE capability). |
| 525B.6 | Create `XeroContactBadge` for customer detail | `frontend/components/customers/XeroContactBadge.tsx` | -- | existing badge patterns | `"use client"`. Inline badge on customer detail page. If a successful customer push exists (check via sync entry lookup), shows "Xero contact: {externalId}". No retry UI on customer level. |
| 525B.7 | Modify invoice + customer detail pages | `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` (modify), `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (modify) | -- | existing page modification patterns | Add `<XeroStatusChip invoiceId={invoice.id} />` to the invoice detail page (near the status/actions area). Add `<XeroContactBadge customerId={customer.id} />` to the customer detail page (near the header metadata). Both conditionally render only when a Xero connection is active (check via `getXeroConnection()` or a feature flag). |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/PaymentPollSummary.java`

**Create (frontend):**
- `frontend/app/(app)/org/[slug]/settings/integrations/xero/sync-log/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions.ts`
- `frontend/components/integrations/SyncLogTable.tsx`
- `frontend/components/integrations/SyncEntryStateBadge.tsx`
- `frontend/components/integrations/XeroSyncSummary.tsx`
- `frontend/components/invoices/XeroStatusChip.tsx`
- `frontend/components/customers/XeroContactBadge.tsx`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java` -- add `resolveReconcileDrift`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` -- add `XeroStatusChip`
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- add `XeroContactBadge`

**Read for context:**
- `frontend/components/integrations/IntegrationCard.tsx` -- card component style
- `frontend/components/dashboard/` -- widget patterns
- `frontend/components/audit/` -- badge patterns

### Architecture Decisions

- **Sync log is the primary observability tool** -- bookkeepers need to see "did this invoice make it to Xero" at a glance. The sync log page is the central view; the invoice status chip is a convenience shortcut.
- **FINANCIAL_RECONCILE for drift resolution** -- only owners can manually mark a drifted invoice as reconciled. This is a deliberate friction point for a potentially risky action.
- **No automated drift resolution** -- amount mismatches require human judgment. Phase 72+ may add AI-assisted reconciliation.

### Non-scope

- No QA capstone (would be a separate Phase 71 QA epic if needed).
- No notification handler integration (could be a follow-up slice -- the domain events are already published; wiring NotificationEventHandler is additive).

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Epics | 9 (517-525) |
| Slices | 13 (517A, 517B, 518A, 518B, 519A, 520A, 520B, 521A, 522A, 523A, 524A, 524B, 525A, 525B) |
| Backend slices | 11 |
| Frontend slices | 2 (524B, 525B) |
| Mixed slices | 0 |
| New backend files | ~45 |
| New frontend files | ~12 |
| Modified backend files | ~6 |
| Modified frontend files | ~3 |
| Test classes | ~14 |
| Migration files | 1 (V121) |
| New capabilities | 3 (INTEGRATION_MANAGE, INTEGRATION_VIEW_SYNC_STATUS, FINANCIAL_RECONCILE) |
| New tables | 3 (accounting_xero_connection, accounting_sync_entry, accounting_tax_code_mapping) |
| New indexes | 4 (idx_axc_status, idx_ase_drain, idx_ase_entity_lookup, idx_ase_external_reference) |
| Estimated total LOC | ~5,500-6,500 |

### Critical Files for Implementation
- `backend/src/main/resources/db/migration/tenant/V121__add_xero_accounting_integration_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/xero/XeroAccountingProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/TrustBoundaryGuard.java`
- `frontend/app/(app)/org/[slug]/settings/integrations/xero/page.tsx`