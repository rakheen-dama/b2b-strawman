# Phase 26 — Invoice Tax Handling

Phase 26 adds structured tax handling to the DocTeams invoicing system. The existing invoice model treats tax as a flat `taxAmount` field on the `Invoice` entity — a manually entered number with no connection to tax rates, no per-line-item calculation, and no VAT/GST registration support. This phase replaces that minimal approach with proper tax infrastructure: org-configurable tax rates, per-line-item tax calculation, automatic tax application on generated invoices, tax-inclusive/exclusive pricing support, and a tax breakdown display on invoices, previews, PDFs, and the customer portal.

**Architecture doc**: `architecture/phase26-invoice-tax-handling.md`

**ADRs**:
- [ADR-101](../adr/ADR-101-tax-calculation-strategy.md) — Tax Calculation Strategy (calculate on save, store per-line)
- [ADR-102](../adr/ADR-102-tax-inclusive-total-display.md) — Tax-Inclusive Total Display (Xero model for inclusive totals)
- [ADR-103](../adr/ADR-103-tax-rate-immutability.md) — Tax Rate Immutability (snapshot at creation + auto-refresh on DRAFT)

**Migration**: V43 tenant — `tax_rates` table + `org_settings` columns + `invoice_lines` columns.

**Dependencies on prior phases**: Phase 10 (Invoicing), Phase 8 (OrgSettings/Rate Cards), Phase 12 (Document Templates/PDF), Phase 7+22 (Portal), Phase 6 (Audit), Phase 17 (Retainers).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 181 | TaxRate Entity Foundation + Migration | Backend | — | M | 181A, 181B | **Done** (PRs #376, #377) |
| 182 | Tax Calculation Engine + InvoiceLine Extension | Backend | 181 | M | 182A, 182B | **Done** (PRs #378, #379) |
| 183 | Tax Application in Invoice Flows | Backend | 182 | L | 183A, 183B | **Done** (PRs #380, #381) |
| 184 | Invoice Preview, PDF + Portal Tax Display | Backend + Portal | 183 | M | 184A, 184B | **Done** (PRs #382, #383) |
| 185 | Tax Settings + Rate Management Frontend | Frontend | 181 | M | 185A, 185B | **Done** (PRs #384, #385) |
| 186 | Invoice Editor Tax UI | Frontend | 183, 185 | M | 186A | **Done** (PR #386) |

---

## Dependency Graph

```
[E181A TaxRate Entity + Migration + Repository]
        |
[E181B TaxRate Service + Controller + Audit + Tests]
        |
        +----------------------------+
        |                            |
[E182A TaxCalculationService       [E185A OrgSettings Tax Fields
 + InvoiceLine Extension]            + Tax Settings UI]
        |                            |
[E182B Invoice.recalculateTotals   [E185B Tax Rate Management
 + Tax Breakdown DTO + Tests]        Table + CRUD Dialogs]
        |
[E183A Tax in Line CRUD + Generation
 + Manual Override Rejection]
        |
[E183B Tax in Retainer Generation
 + Draft Batch Recalc + Tests]
        |
        +----------------------------+
        |                            |
[E184A Preview Template            [E186A Invoice Editor
 + Context Assembly + Tests]        Tax Rate Dropdown
        |                            + Breakdown Display]
[E184B Portal Read-Model
 + Sync Event + Portal UI]
```

**Parallel opportunities**:
- Epics 182 and 185 are fully independent after 181B — can start in parallel immediately.
- After 183B completes: Epics 184 and 186 can start in parallel (186 also requires 185B for tax rate fetch).
- 184A and 184B are sequential (preview context before portal sync).
- 185A and 185B are sequential (org settings backend before rate management UI).

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 181 | 181A | V43 migration (`tax_rates` table + `org_settings` columns + `invoice_lines` columns + seed data) + `TaxRate` entity + `TaxRateRepository` + repository integration tests. ~6 new files, ~1 migration file. Backend only. | **Done** (PR #376) |
| 0b | 181 | 181B | `TaxRateService` (CRUD + single-default + deactivation guard) + `TaxRateController` + DTOs + audit events + RBAC + integration tests. ~6 new files, ~1 modified file. Backend only. | **Done** (PR #377) |

### Stage 1: Calculation Engine + Settings (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 182 | 182A | `TaxCalculationService` + `InvoiceLine` tax field extension (5 fields + `applyTaxRate()` method) + `TaxBreakdownEntry` record + unit tests for calculation. ~3 new files, ~1 modified file. Backend only. | **Done** (PR #378) |
| 1b (parallel) | 185 | 185A | `OrgSettings` tax fields (4 fields + update method) + settings API extension + audit event + frontend "Tax Settings" card (registration, label, inclusive toggle) + tests. ~1 modified backend file, ~3 new/modified frontend files. | **Done** (PR #384) |

### Stage 2: Calculation integration + Rate management UI (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 182 | 182B | `Invoice.recalculateTotals()` update (per-line tax sum + tax-inclusive total) + tax breakdown assembly utility + integration tests. ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #379) |
| 2b (parallel) | 185 | 185B | Tax rate management table on settings page + Add/Edit/Deactivate dialogs + default badge + deactivation error handling + frontend tests. ~3 new frontend files, ~1 modified frontend file. | **Done** (PR #385) |

### Stage 3: Invoice flows (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 183 | 183A | Wire tax into `InvoiceService.addLineItem()`/`updateLineItem()` + `generateFromUnbilledTime()` + reject manual `taxAmount` when per-line active (422) + extend DTOs (`AddLineItemRequest`, `UpdateLineItemRequest`, `InvoiceLineResponse`, `InvoiceResponse`) + integration tests. ~4 modified files, ~0-1 new files. Backend only. | **Done** (PR #380) |
| 3b | 183 | 183B | Wire tax into `RetainerPeriodService.closePeriod()` + `TaxRateService.updateTaxRate()` batch recalculation of DRAFT lines + backward compatibility tests + integration tests. ~3 modified files. Backend only. | **Done** (PR #381) |

### Stage 4: Display layers (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 184 | 184A | `invoice-preview.html` template update (tax column, breakdown section, registration number, inclusive note) + `InvoiceService.renderPreview()` context assembly + integration tests. ~2 modified files. Backend only. | **Done** (PR #382) |
| 4b (parallel) | 186 | 186A | Tax rate dropdown on invoice line add/edit + per-line tax display + tax breakdown in totals (replaces manual input) + tax-inclusive indicator + TypeScript types + frontend tests. ~5 modified frontend files, ~1 new frontend file. | **Done** (PR #386) |

### Stage 5: Portal (sequential, after 184A)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 184 | 184B | `InvoiceSyncEvent` tax fields + `PortalInvoiceView` extension + `PortalEventHandler` mapping + portal invoice detail tax breakdown display + tests. ~4 modified backend files, ~2 modified portal files. | **Done** (PR #383) |

### Timeline

```
Stage 0: [181A] --> [181B]                                          (sequential)
Stage 1: [182A] // [185A]                                           (parallel, after 181B)
Stage 2: [182B] // [185B]                                           (parallel, after respective Stage 1)
Stage 3: [183A] --> [183B]                                          (sequential, after 182B)
Stage 4: [184A] // [186A]                                           (parallel, after 183B; 186A also after 185B)
Stage 5: [184B]                                                     (after 184A)
```

**Critical path**: 181A -> 181B -> 182A -> 182B -> 183A -> 183B -> 184A -> 184B

---

## Epic 181: TaxRate Entity Foundation + Migration

**Goal**: Create the `TaxRate` entity, repository, service, controller, database migration V43, seed data, audit events, and RBAC. This is the foundation for all subsequent tax handling work.

**References**: Architecture doc Sections 26.2.1 (TaxRate entity), 26.3.1 (CRUD flows), 26.4.1 (API surface), 26.7 (migration SQL), 26.8.1 (new files), 26.9 (permissions). ADR-103.

**Dependencies**: None — this is the foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **181A** | 181.1--181.7 | V43 migration (`tax_rates` table + `org_settings` tax columns + `invoice_lines` tax columns + seed data + indexes) + `TaxRate` entity + `TaxRateRepository` with custom queries + repository integration tests. ~5 new files, ~1 migration file. Backend only. | **Done** (PR #376) |
| **181B** | 181.8--181.16 | `TaxRateService` (create, update, deactivate, list, getDefault with single-default enforcement + deactivation guard) + DTOs (`CreateTaxRateRequest`, `UpdateTaxRateRequest`, `TaxRateResponse`) + `TaxRateController` at `/api/tax-rates` with RBAC + audit events + integration tests. ~7 new files, ~0-1 modified files. Backend only. | **Done** (PR #377) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 181.1 | Create V43 tenant migration | 181A | | New file: `db/migration/tenant/V43__invoice_tax_handling.sql`. (1) ALTER `org_settings` ADD `tax_registration_number VARCHAR(50)`, `tax_registration_label VARCHAR(30) DEFAULT 'Tax Number'`, `tax_label VARCHAR(20) DEFAULT 'Tax'`, `tax_inclusive BOOLEAN NOT NULL DEFAULT false`. (2) CREATE `tax_rates` table per architecture doc Section 26.7 with `chk_tax_rate_range` + `chk_exempt_zero` constraints. (3) CREATE INDEX `idx_tax_rates_active_sort` on `tax_rates (active, sort_order)`. (4) ALTER `invoice_lines` ADD `tax_rate_id UUID REFERENCES tax_rates(id)`, `tax_rate_name VARCHAR(100)`, `tax_rate_percent DECIMAL(5,2)`, `tax_amount DECIMAL(14,2)`, `tax_exempt BOOLEAN NOT NULL DEFAULT false`. (5) CREATE INDEX `idx_invoice_lines_tax_rate` on `invoice_lines (tax_rate_id)`. (6) INSERT seed tax rates: Standard (15%, default), Zero-rated (0%), Exempt (0%, isExempt). Note: architecture doc says V42 but V42 is taken by Phase 25. |
| 181.2 | Create `TaxRate` entity | 181A | 181.1 | New file: `tax/TaxRate.java`. `@Entity`, `@Table(name = "tax_rates")`. Fields per architecture doc Section 26.2.1: id (UUID PK), name (String 100), rate (BigDecimal 5,2), isDefault (boolean), isExempt (boolean), active (boolean default true), sortOrder (int), createdAt (Instant), updatedAt (Instant). Protected no-arg constructor for JPA. Public constructor `(String name, BigDecimal rate, boolean isDefault, boolean isExempt, int sortOrder)`. `@PreUpdate` for `updatedAt`. Update methods for mutable fields. Pattern: `invoice/PaymentEvent.java` entity style (no Lombok, constructor injection). |
| 181.3 | Create `TaxRateRepository` | 181A | 181.2 | New file: `tax/TaxRateRepository.java`. `JpaRepository<TaxRate, UUID>`. Custom queries: `List<TaxRate> findByActiveOrderBySortOrder(boolean active)`, `List<TaxRate> findAllByOrderBySortOrder()`, `Optional<TaxRate> findByIsDefaultTrue()`, `boolean existsByName(String name)`, `boolean existsByNameAndIdNot(String name, UUID id)`. |
| 181.4 | Write entity unit tests | 181A | 181.2 | New file: `tax/TaxRateTest.java`. Tests: (1) constructor_sets_all_fields, (2) constructor_sets_timestamps, (3) active_defaults_to_true, (4) update_methods_modify_fields. ~4 tests. |
| 181.5 | Write repository integration tests | 181A | 181.3 | New file: `tax/TaxRateRepositoryIntegrationTest.java`. Tests: (1) save_and_findById, (2) findByActiveOrderBySortOrder_returns_active_only, (3) findAllByOrderBySortOrder_returns_all, (4) findByIsDefaultTrue_returns_default, (5) findByIsDefaultTrue_returns_empty_when_none, (6) existsByName_true_when_exists, (7) existsByNameAndIdNot_excludes_self. ~7 tests. Pattern: `invoice/PaymentEventRepositoryIntegrationTest.java`. |
| 181.6 | Verify migration runs on Testcontainers | 181A | 181.1 | Covered by repository integration tests: Flyway runs V43 automatically. Verify: `tax_rates` table created, seed data present (3 rows), `org_settings` columns added, `invoice_lines` columns added. |
| 181.7 | Verify seed data in repository test | 181A | 181.5 | Add test in `TaxRateRepositoryIntegrationTest`: (8) seed_data_creates_three_default_rates — verify Standard (15%, default), Zero-rated (0%), Exempt (0%, exempt) exist after migration. ~1 additional test. |
| 181.8 | Create `CreateTaxRateRequest` DTO | 181B | | New file: `tax/dto/CreateTaxRateRequest.java`. Java record with validation: `@NotBlank @Size(max=100) String name`, `@NotNull @DecimalMin("0.00") @DecimalMax("99.99") BigDecimal rate`, `boolean isDefault`, `boolean isExempt`, `int sortOrder`. |
| 181.9 | Create `UpdateTaxRateRequest` DTO | 181B | | New file: `tax/dto/UpdateTaxRateRequest.java`. Same fields and validation as `CreateTaxRateRequest`. |
| 181.10 | Create `TaxRateResponse` DTO | 181B | | New file: `tax/dto/TaxRateResponse.java`. Java record: `UUID id`, `String name`, `BigDecimal rate`, `boolean isDefault`, `boolean isExempt`, `boolean active`, `int sortOrder`, `Instant createdAt`, `Instant updatedAt`. Static factory `from(TaxRate)`. |
| 181.11 | Create `TaxRateService` | 181B | 181.3 | New file: `tax/TaxRateService.java`. `@Service`. Constructor injection: `TaxRateRepository`, `AuditService`. Methods: `createTaxRate(CreateTaxRateRequest)` — validate name uniqueness, exempt-zero constraint, single-default enforcement (unset previous default in same transaction), publish audit event `tax_rate.created`. `updateTaxRate(UUID id, UpdateTaxRateRequest)` — same validations, publish `tax_rate.updated`. `deactivateTaxRate(UUID id)` — deactivation guard (check DRAFT invoice lines, return 409 with count if used; needs `InvoiceLineRepository` injection or custom query), clear default if deactivating default, publish `tax_rate.deactivated`. `listTaxRates(boolean includeInactive)`. `getDefaultTaxRate()` — returns `Optional<TaxRate>`. Pattern: `rate/BillingRateService.java` for CRUD service structure. |
| 181.12 | Add deactivation guard query | 181B | 181.11 | Add to `TaxRateRepository` (or via a query on `InvoiceLineRepository`): `long countDraftInvoiceLinesForTaxRate(UUID taxRateId)`. This requires a join query: `SELECT COUNT(il) FROM InvoiceLine il JOIN il.invoice i WHERE il.taxRateId = :taxRateId AND i.status = 'DRAFT'`. Alternatively, add `countByTaxRateIdAndInvoice_Status(UUID taxRateId, InvoiceStatus status)` to `InvoiceLineRepository`. Choose the approach that avoids a circular dependency — since `TaxRateService` may need `InvoiceLineRepository`, inject it. |
| 181.13 | Create `TaxRateController` | 181B | 181.11, 181.10 | New file: `tax/TaxRateController.java`. `@RestController`, `@RequestMapping("/api/tax-rates")`. Endpoints: `GET /` (list, `@RequestParam(defaultValue = "false") boolean includeInactive`), `POST /` (`@PreAuthorize("hasAnyRole('ADMIN','OWNER')")`), `PUT /{id}` (same RBAC), `DELETE /{id}` (same RBAC). Pattern: `rate/BillingRateController.java`. |
| 181.14 | Wire audit events | 181B | 181.11 | In `TaxRateService`: use `AuditService.record()` with event types `tax_rate.created`, `tax_rate.updated`, `tax_rate.deactivated`, `tax_rate.default_changed`. Include relevant details (name, rate, isDefault, changedFields). Pattern: existing audit calls in `rate/BillingRateService.java` or `invoice/InvoiceService.java`. |
| 181.15 | Write service unit tests | 181B | 181.11 | New file: `tax/TaxRateServiceTest.java`. Tests: (1) createTaxRate_saves_rate, (2) createTaxRate_duplicate_name_throws, (3) createTaxRate_new_default_unsets_previous, (4) createTaxRate_exempt_with_nonzero_rate_throws, (5) updateTaxRate_changes_fields, (6) updateTaxRate_new_default_unsets_previous, (7) deactivateTaxRate_sets_inactive, (8) deactivateTaxRate_used_on_draft_returns_409, (9) deactivateTaxRate_clears_default_if_was_default, (10) listTaxRates_excludes_inactive, (11) listTaxRates_includes_inactive_when_requested, (12) getDefaultTaxRate_returns_default. ~12 tests. |
| 181.16 | Write controller integration tests | 181B | 181.13 | New file: `tax/TaxRateControllerIntegrationTest.java`. MockMvc tests: (1) GET returns list, (2) POST creates rate (ADMIN), (3) POST 403 for MEMBER, (4) PUT updates rate, (5) DELETE deactivates rate, (6) DELETE 409 when used on draft, (7) GET with includeInactive. ~7 tests. Pattern: `rate/BillingRateControllerIntegrationTest.java`. |

### Key Files

**Slice 181A — Create:**
- `backend/src/main/resources/db/migration/tenant/V43__invoice_tax_handling.sql`
- `backend/src/main/java/.../tax/TaxRate.java`
- `backend/src/main/java/.../tax/TaxRateRepository.java`
- `backend/src/test/java/.../tax/TaxRateTest.java`
- `backend/src/test/java/.../tax/TaxRateRepositoryIntegrationTest.java`

**Slice 181B — Create:**
- `backend/src/main/java/.../tax/dto/CreateTaxRateRequest.java`
- `backend/src/main/java/.../tax/dto/UpdateTaxRateRequest.java`
- `backend/src/main/java/.../tax/dto/TaxRateResponse.java`
- `backend/src/main/java/.../tax/TaxRateService.java`
- `backend/src/main/java/.../tax/TaxRateController.java`
- `backend/src/test/java/.../tax/TaxRateServiceTest.java`
- `backend/src/test/java/.../tax/TaxRateControllerIntegrationTest.java`

**Slice 181B — Read for context:**
- `backend/src/main/java/.../invoice/InvoiceLineRepository.java` — for deactivation guard query
- `backend/src/main/java/.../audit/AuditService.java` — audit event pattern
- `backend/src/main/java/.../billingrate/BillingRateController.java` — CRUD controller pattern
- `backend/src/main/java/.../billingrate/BillingRateService.java` — CRUD service pattern

### Architecture Decisions

- **TaxRate in `tax/` package (not `rate/`)**: Tax rates are conceptually distinct from billing/cost rates. Billing rates have org/project/customer hierarchy; tax rates are flat org-level. Separate package avoids confusion.
- **Soft-active, not soft-delete**: `active = false` preserves FK integrity for `invoice_lines.tax_rate_id`. Deactivated rates remain in DB but hidden from selection.
- **Single-default enforced in service, not DB**: Matches the existing pattern for billing rates. No partial unique index needed.
- **V43 migration (not V42)**: V42 is taken by Phase 25 (online payment collection). Architecture doc references "V42" but this is stale.
- **Seed data in migration**: Each tenant schema gets 3 default tax rates (Standard 15%, Zero-rated 0%, Exempt 0%) via INSERT in V43. New tenant provisioning runs all migrations, so no separate seeding code needed.

---

## Epic 182: Tax Calculation Engine + InvoiceLine Extension

**Goal**: Create the `TaxCalculationService` with tax-exclusive and tax-inclusive formulas, extend `InvoiceLine` with 5 tax fields, update `Invoice.recalculateTotals()` to handle per-line tax, and create the `TaxBreakdownEntry` DTO.

**References**: Architecture doc Sections 26.2.2 (InvoiceLine tax fields), 26.2.4 (Invoice behavioral changes), 26.3.5 (calculation formulas), 26.3.6 (recalculation logic). ADR-101, ADR-102.

**Dependencies**: Epic 181 (TaxRate entity must exist for FK and type references).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **182A** | 182.1--182.7 | `TaxCalculationService` with `calculateLineTax()` (exclusive + inclusive + exempt + zero-rated) + `InvoiceLine` extension (5 tax fields + `applyTaxRate()` + `refreshTaxSnapshot()` methods) + `TaxBreakdownEntry` record + unit tests for calculation service. ~3 new files, ~1 modified file. Backend only. | **Done** (PR #378) |
| **182B** | 182.8--182.13 | `Invoice.recalculateTotals()` update (per-line tax sum, tax-inclusive total per ADR-102) + tax breakdown assembly utility in `TaxCalculationService` + integration tests for recalculation. ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #379) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 182.1 | Create `TaxCalculationService` | 182A | | New file: `tax/TaxCalculationService.java`. `@Service`. Method: `BigDecimal calculateLineTax(BigDecimal amount, BigDecimal ratePercent, boolean taxInclusive, boolean taxExempt)`. Exempt short-circuits to ZERO. Tax-exclusive: `amount * ratePercent / 100` (scale 2, HALF_UP). Tax-inclusive: `amount - (amount / (1 + ratePercent / 100))` (intermediate scale 10, final scale 2, HALF_UP). See architecture doc Section 26.3.5 for full implementation. |
| 182.2 | Create `TaxBreakdownEntry` record | 182A | | New file: `tax/dto/TaxBreakdownEntry.java`. Java record: `String rateName`, `BigDecimal ratePercent`, `BigDecimal taxableAmount`, `BigDecimal taxAmount`. Used for grouping line-level tax by rate for display. |
| 182.3 | Extend `InvoiceLine` with tax fields | 182A | | Modify: `invoice/InvoiceLine.java`. Add 5 fields: `@Column(name = "tax_rate_id") UUID taxRateId`, `@Column(name = "tax_rate_name", length = 100) String taxRateName`, `@Column(name = "tax_rate_percent", precision = 5, scale = 2) BigDecimal taxRatePercent`, `@Column(name = "tax_amount", precision = 14, scale = 2) BigDecimal taxAmount`, `@Column(name = "tax_exempt") boolean taxExempt`. Add getters/setters. |
| 182.4 | Add `applyTaxRate()` method to `InvoiceLine` | 182A | 182.3 | In `InvoiceLine.java`: method `applyTaxRate(TaxRate taxRate, BigDecimal calculatedTaxAmount)` that sets `taxRateId`, `taxRateName`, `taxRatePercent`, `taxExempt` from the TaxRate entity and `taxAmount` from the calculated value. Convenience for snapshot + store. |
| 182.5 | Add `refreshTaxSnapshot()` method to `InvoiceLine` | 182A | 182.3 | In `InvoiceLine.java`: method `refreshTaxSnapshot(TaxRate taxRate)` that re-copies `name`, `rate`, `isExempt` from the TaxRate without changing `taxAmount` (caller recalculates separately). Used by DRAFT batch refresh in ADR-103. |
| 182.6 | Add `clearTaxRate()` method to `InvoiceLine` | 182A | 182.3 | In `InvoiceLine.java`: method `clearTaxRate()` that sets all 5 tax fields to null/false/zero. Used when user explicitly removes tax from a line. |
| 182.7 | Write `TaxCalculationService` unit tests | 182A | 182.1 | New file: `tax/TaxCalculationServiceTest.java`. Tests: (1) exclusive_standard_rate (100 * 15% = 15.00), (2) exclusive_zero_rate (100 * 0% = 0.00), (3) exclusive_rounding (133.33 * 15% = 20.00), (4) inclusive_standard_rate (115 inclusive 15% = 15.00), (5) inclusive_rounding, (6) exempt_returns_zero_regardless_of_rate, (7) zero_rated_not_short_circuited (returns 0 but different from exempt), (8) edge_case_001_amount, (9) edge_case_large_amount, (10) edge_case_9999_rate. ~10 tests. |
| 182.8 | Update `Invoice.recalculateTotals()` | 182B | 182.3 | Modify: `invoice/Invoice.java`. Change `recalculateTotals()` signature to accept `List<InvoiceLine> lines` and `boolean taxInclusive`. Add logic per architecture doc Section 26.3.6: if any line has non-null `taxRateId`, compute `taxAmount` as sum of line `taxAmount` values; if `taxInclusive && hasPerLineTax`, set `total = subtotal`; else `total = subtotal + taxAmount`. Preserve legacy behavior when no per-line tax. See ADR-102 implementation sketch. |
| 182.9 | Add tax breakdown assembly to `TaxCalculationService` | 182B | 182.2, 182.3 | Add method to `TaxCalculationService`: `List<TaxBreakdownEntry> buildTaxBreakdown(List<InvoiceLine> lines)`. Groups lines by `taxRateName + taxRatePercent`, sums `amount` (taxableAmount) and `taxAmount` per group. Excludes exempt lines. Returns empty list if no per-line tax. |
| 182.10 | Add `hasPerLineTax()` utility to `Invoice` or `InvoiceService` | 182B | 182.3 | Add method: `boolean hasPerLineTax(List<InvoiceLine> lines)` — returns `true` if any line has non-null `taxRateId`. Can be a static utility on `Invoice` or a method in `TaxCalculationService`. Used by recalculateTotals, preview, response building. |
| 182.11 | Update callers of `recalculateTotals()` | 182B | 182.8 | Modify: `invoice/InvoiceService.java`. Update all existing calls to `recalculateTotals()` to pass the line list and `taxInclusive` flag (load from `OrgSettingsRepository`). Inject `OrgSettingsRepository` if not already injected. This is a signature change — all existing callers must be updated. Verify backward compatibility: when no lines have tax, behavior is identical to before. |
| 182.12 | Write `Invoice.recalculateTotals()` unit tests | 182B | 182.8 | New file or extend existing: `invoice/InvoiceRecalculationTest.java`. Tests: (1) no_per_line_tax_preserves_manual_taxAmount, (2) per_line_tax_sums_line_amounts, (3) tax_exclusive_total_is_subtotal_plus_tax, (4) tax_inclusive_total_equals_subtotal, (5) mixed_lines_with_and_without_tax, (6) all_exempt_lines_zero_tax. ~6 tests. |
| 182.13 | Write tax breakdown assembly tests | 182B | 182.9 | Add tests to `TaxCalculationServiceTest.java` or new file: (1) buildTaxBreakdown_groups_by_rate, (2) buildTaxBreakdown_excludes_exempt, (3) buildTaxBreakdown_empty_when_no_per_line_tax, (4) buildTaxBreakdown_multiple_rates. ~4 tests. |

### Key Files

**Slice 182A — Create:**
- `backend/src/main/java/.../tax/TaxCalculationService.java`
- `backend/src/main/java/.../tax/dto/TaxBreakdownEntry.java`
- `backend/src/test/java/.../tax/TaxCalculationServiceTest.java`

**Slice 182A — Modify:**
- `backend/src/main/java/.../invoice/InvoiceLine.java`

**Slice 182B — Modify:**
- `backend/src/main/java/.../invoice/Invoice.java`
- `backend/src/main/java/.../invoice/InvoiceService.java`

**Slice 182B — Create:**
- `backend/src/test/java/.../invoice/InvoiceRecalculationTest.java` (or extend existing)

**Read for context:**
- `backend/src/main/java/.../invoice/InvoiceLine.java` — existing fields and methods
- `backend/src/main/java/.../invoice/Invoice.java` — existing `recalculateTotals()` method
- `backend/src/main/java/.../settings/OrgSettingsRepository.java` — for loading taxInclusive flag

### Architecture Decisions

- **TaxCalculationService, not inline in InvoiceService (ADR-101)**: Keeps invoice service focused on lifecycle, delegates all tax math to a dedicated service. Single responsibility.
- **Denormalized snapshots on InvoiceLine (ADR-101, ADR-103)**: 5 fields snapshot tax rate data at line creation. Finalized invoices are immutable legal documents.
- **Xero model for tax-inclusive totals (ADR-102)**: In inclusive mode, `total = subtotal` (amounts already include tax). `taxAmount` is informational (extracted tax portion). Formula `total = subtotal + taxAmount` only holds in exclusive mode and legacy invoices.
- **Exempt vs. zero-rated distinction**: Exempt lines short-circuit to zero tax and are excluded from tax breakdown. Zero-rated lines return zero tax naturally but appear in the breakdown with R0.00.

---

## Epic 183: Tax Application in Invoice Flows

**Goal**: Wire the tax calculation engine into all invoice creation, editing, and generation flows: manual line CRUD, unbilled time generation, retainer period generation, manual tax override rejection, and batch recalculation on tax rate change.

**References**: Architecture doc Sections 26.3.2 (manual line creation), 26.3.3 (unbilled time generation), 26.3.4 (retainer generation), 26.3.7 (manual override rules), 26.4.2 (modified line endpoints), 26.4.3 (modified invoice response). ADR-103 (batch refresh).

**Dependencies**: Epic 182 (calculation engine and InvoiceLine extension must exist).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **183A** | 183.1--183.9 | Wire tax into `InvoiceService.addLineItem()` / `updateLineItem()` (accept `taxRateId`, snapshot, calculate, auto-default) + `generateFromUnbilledTime()` (apply default rate to generated lines) + reject manual `taxAmount` on `updateDraft()` when per-line tax active (422) + extend DTOs + extend `InvoiceResponse` with tax breakdown + integration tests. ~5 modified files. Backend only. | **Done** (PR #380) |
| **183B** | 183.10--183.16 | Wire tax into `RetainerPeriodService.closePeriod()` (apply default rate to retainer invoice lines) + `TaxRateService.updateTaxRate()` batch recalculation of DRAFT lines (ADR-103) + backward compatibility tests + integration tests. ~3 modified files. Backend only. | **Done** (PR #381) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 183.1 | Extend `AddLineItemRequest` with `taxRateId` | 183A | | Modify: `invoice/dto/AddLineItemRequest.java`. Add `Optional<UUID> taxRateId` field (nullable). When present: use specified rate. When absent: auto-apply org default. Explicit null: no tax. |
| 183.2 | Extend `UpdateLineItemRequest` with `taxRateId` | 183A | | Modify: `invoice/dto/UpdateLineItemRequest.java`. Add `Optional<UUID> taxRateId` field. Same semantics as AddLineItemRequest. |
| 183.3 | Extend `InvoiceLineResponse` with tax fields | 183A | | Modify: `invoice/dto/InvoiceLineResponse.java`. Add: `UUID taxRateId`, `String taxRateName`, `BigDecimal taxRatePercent`, `BigDecimal taxAmount`, `boolean taxExempt`. Update `from(InvoiceLine)` factory. |
| 183.4 | Extend `InvoiceResponse` with tax breakdown | 183A | | Modify: `invoice/dto/InvoiceResponse.java`. Add: `List<TaxBreakdownEntry> taxBreakdown`, `boolean taxInclusive`, `String taxRegistrationNumber`, `String taxRegistrationLabel`, `String taxLabel`, `boolean hasPerLineTax`. Update `from()` factory or builder to compute breakdown using `TaxCalculationService.buildTaxBreakdown()`. Load OrgSettings for tax config fields. |
| 183.5 | Wire tax into `InvoiceService.addLineItem()` | 183A | 183.1 | Modify: `invoice/InvoiceService.java`. In `addLineItem()`: (1) If `taxRateId` provided: load `TaxRate`, verify active, snapshot + calculate. (2) If not provided and not explicitly null: load default via `TaxRateService.getDefaultTaxRate()`, apply if present. (3) If explicitly null: no tax. (4) Load `OrgSettings.taxInclusive`. (5) Call `TaxCalculationService.calculateLineTax()`. (6) Call `line.applyTaxRate()`. (7) Recalculate invoice totals. Inject `TaxRateService`, `TaxCalculationService` if not already injected. |
| 183.6 | Wire tax into `InvoiceService.updateLineItem()` | 183A | 183.2 | Modify: `invoice/InvoiceService.java`. Same logic as `addLineItem()` for tax rate handling. If `taxRateId` changes: re-snapshot + recalculate. If cleared: call `clearTaxRate()`. Recalculate invoice totals. |
| 183.7 | Wire tax into `InvoiceService.generateFromUnbilledTime()` | 183A | | Modify: `invoice/InvoiceService.java`. After creating all lines from time entries, load default tax rate. If present: apply to all generated lines (snapshot + calculate). Load `OrgSettings.taxInclusive`. Recalculate invoice totals. |
| 183.8 | Reject manual `taxAmount` when per-line tax active | 183A | | Modify: `invoice/InvoiceService.java`. In `updateDraft()`: if request contains `taxAmount` AND `invoiceLineRepository.existsByInvoiceIdAndTaxRateIdIsNotNull(invoiceId)`, return 422 with message per architecture doc Section 26.3.7. Add query `existsByInvoiceIdAndTaxRateIdIsNotNull(UUID invoiceId)` to `InvoiceLineRepository`. |
| 183.9 | Write integration tests for line tax + generation | 183A | 183.5, 183.7 | New file: `tax/InvoiceTaxIntegrationTest.java`. Tests: (1) addLineItem_with_taxRateId_applies_tax, (2) addLineItem_auto_defaults_when_no_taxRateId, (3) addLineItem_explicit_null_no_tax, (4) updateLineItem_changes_tax_rate, (5) updateLineItem_clears_tax_rate, (6) generateFromUnbilledTime_applies_default_tax, (7) generateFromUnbilledTime_no_default_no_tax, (8) updateDraft_rejects_manual_taxAmount_when_per_line_active, (9) updateDraft_allows_manual_taxAmount_when_no_per_line_tax, (10) invoice_response_includes_tax_breakdown, (11) invoice_response_includes_org_tax_settings, (12) backward_compatibility_legacy_invoice_unchanged. ~12 tests. |
| 183.10 | Wire tax into `RetainerPeriodService.closePeriod()` | 183B | | Modify: `retainer/RetainerPeriodService.java`. Inject `TaxRateService`, `TaxCalculationService`, `OrgSettingsRepository`. After creating invoice lines: load default tax rate. If present: apply to all lines (base fee + overage). Load `OrgSettings.taxInclusive`. Calculate per-line tax. Recalculate invoice totals (pass lines + taxInclusive to updated `recalculateTotals()`). See architecture doc Section 26.3.4 step-by-step flow. |
| 183.11 | Add DRAFT line batch recalculation to `TaxRateService.updateTaxRate()` | 183B | | Modify: `tax/TaxRateService.java`. In `updateTaxRate()`: if rate percentage, name, or exempt flag changed, find all DRAFT invoice lines with this `taxRateId` via `InvoiceLineRepository.findByTaxRateIdAndInvoice_Status(taxRateId, DRAFT)` (add this query to `InvoiceLineRepository`). For each line: `refreshTaxSnapshot(taxRate)`, recalculate `taxAmount` via `TaxCalculationService`. Collect affected invoice IDs, recalculate each invoice's totals. Run in same transaction. See ADR-103 implementation sketch. |
| 183.12 | Add `findByTaxRateIdAndInvoice_Status` query | 183B | | Modify: `invoice/InvoiceLineRepository.java`. Add: `List<InvoiceLine> findByTaxRateIdAndInvoice_Status(UUID taxRateId, InvoiceStatus status)`. Spring Data derived query using the `invoice` association's `status` field. |
| 183.13 | Write retainer tax integration tests | 183B | 183.10 | New or extend: `retainer/RetainerPeriodTaxIntegrationTest.java`. Tests: (1) closePeriod_applies_default_tax_to_lines, (2) closePeriod_no_default_no_tax, (3) closePeriod_tax_inclusive_total_calculation, (4) closePeriod_exempt_rate_zero_tax. ~4 tests. |
| 183.14 | Write batch recalculation integration tests | 183B | 183.11 | New or extend: `tax/TaxRateBatchRecalcIntegrationTest.java`. Tests: (1) updateTaxRate_recalculates_draft_lines, (2) updateTaxRate_does_not_touch_approved_lines, (3) updateTaxRate_updates_parent_invoice_totals, (4) updateTaxRate_name_change_refreshes_snapshot, (5) updateTaxRate_no_change_no_recalculation. ~5 tests. |
| 183.15 | Write backward compatibility tests | 183B | | In `InvoiceTaxIntegrationTest.java` or separate: (1) legacy_invoice_no_per_line_tax_displays_flat_taxAmount, (2) legacy_invoice_total_equals_subtotal_plus_taxAmount, (3) legacy_invoice_updateDraft_allows_manual_taxAmount. ~3 tests. |
| 183.16 | Verify `deleteLineItem` recalculates totals correctly | 183B | | Test: (1) deleteLineItem_with_tax_recalculates_invoice_totals, (2) deleteLineItem_last_taxed_line_reverts_to_manual_mode. ~2 tests. Ensure existing `deleteLineItem()` in `InvoiceService` calls `recalculateTotals()` with updated line list. |

### Key Files

**Slice 183A — Modify:**
- `backend/src/main/java/.../invoice/dto/AddLineItemRequest.java`
- `backend/src/main/java/.../invoice/dto/UpdateLineItemRequest.java`
- `backend/src/main/java/.../invoice/dto/InvoiceLineResponse.java`
- `backend/src/main/java/.../invoice/dto/InvoiceResponse.java`
- `backend/src/main/java/.../invoice/InvoiceService.java`
- `backend/src/main/java/.../invoice/InvoiceLineRepository.java`

**Slice 183A — Create:**
- `backend/src/test/java/.../tax/InvoiceTaxIntegrationTest.java`

**Slice 183B — Modify:**
- `backend/src/main/java/.../retainer/RetainerPeriodService.java`
- `backend/src/main/java/.../tax/TaxRateService.java`
- `backend/src/main/java/.../invoice/InvoiceLineRepository.java`

**Slice 183B — Create:**
- `backend/src/test/java/.../retainer/RetainerPeriodTaxIntegrationTest.java`
- `backend/src/test/java/.../tax/TaxRateBatchRecalcIntegrationTest.java`

**Read for context:**
- `backend/src/main/java/.../invoice/InvoiceService.java` — existing `addLineItem()`, `updateLineItem()`, `generateFromUnbilledTime()`, `updateDraft()`
- `backend/src/main/java/.../retainer/RetainerPeriodService.java` — existing `closePeriod()` invoice creation flow
- `backend/src/main/java/.../settings/OrgSettingsRepository.java` — for `taxInclusive` flag

### Architecture Decisions

- **Auto-default tax rate on line creation**: When `taxRateId` is not provided (absent, not explicitly null), the org's default rate is applied automatically. This reduces user friction — most lines will use the standard rate. Explicit null overrides to "no tax" for exempt items.
- **422 rejection for manual taxAmount with per-line tax (Section 26.3.7)**: Prevents conflicting tax information. Clear error message guides users to edit line-level rates instead.
- **Batch recalculation on rate change (ADR-103)**: DRAFT lines auto-refresh when a rate's percentage or name changes. APPROVED+ invoices are never touched. Same-transaction consistency.
- **RetainerPeriodService direct injection**: Retainer invoice creation bypasses `InvoiceService`, so `TaxRateService` and `TaxCalculationService` are injected directly. This is documented in the architecture doc Section 26.3.4.

---

## Epic 184: Invoice Preview, PDF + Portal Tax Display

**Goal**: Update the invoice HTML preview template with tax column, breakdown section, and registration number. Extend the portal read-model and sync events with tax breakdown data. Update portal invoice detail to display tax breakdown.

**References**: Architecture doc Sections 26.6.1 (preview template), 26.6.2 (preview context), 26.6.3 (portal read-model). ADR-102 (display model).

**Dependencies**: Epic 183 (invoice flows must populate tax data for display to work).

**Scope**: Backend + Portal

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **184A** | 184.1--184.7 | Update `invoice-preview.html` Thymeleaf template (tax column on line items, tax breakdown section, registration number in header, tax-inclusive note) + update preview context assembly in `InvoiceService.renderPreview()` + integration tests for HTML output. ~2 modified files. Backend only. | **Done** (PR #382) |
| **184B** | 184.8--184.14 | Extend `InvoiceSyncEvent` with tax breakdown fields + extend `PortalInvoiceView` + update `PortalEventHandler` mapping + portal invoice detail tax breakdown display + portal `invoice-line-table` tax column + tests. ~4 modified backend files, ~2 modified portal files. Backend + Portal. | **Done** (PR #383) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 184.1 | Add tax registration number to preview template header | 184A | | Modify: `backend/src/main/resources/templates/invoice-preview.html`. Add conditional block near org details: show `taxRegistrationLabel: taxRegistrationNumber` when `taxRegistrationNumber != null`. See architecture doc Section 26.6.1. |
| 184.2 | Add tax column to line items table | 184A | | Modify: `invoice-preview.html`. Add conditional `<th>` and `<td>` for tax column when `hasPerLineTax` is true. Cell shows `taxRateName taxRatePercent%` or "Exempt" for exempt lines. Column omitted entirely for legacy invoices. |
| 184.3 | Add tax breakdown section to totals | 184A | | Modify: `invoice-preview.html`. Replace single "Tax" row with conditional rendering: (1) Legacy (no per-line tax): show single "Tax" row with flat `taxAmount`. (2) Per-line tax: iterate `taxBreakdown` list, show each rate name + percentage + amount. (3) Tax-inclusive note: "All amounts include {taxLabel}" when `taxInclusive && hasPerLineTax`. See architecture doc Section 26.6.1 HTML snippets. |
| 184.4 | Update preview context assembly | 184A | | Modify: `invoice/InvoiceService.java` (in `renderPreview()` or `buildPreviewContext()` method). Add template variables: `hasPerLineTax`, `taxBreakdown` (from `TaxCalculationService.buildTaxBreakdown()`), `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `taxInclusive` (from `OrgSettings`). See architecture doc Section 26.6.2 variable table. |
| 184.5 | Write preview integration tests | 184A | 184.4 | New or extend: `invoice/InvoicePreviewTaxIntegrationTest.java`. Tests: (1) preview_contains_tax_breakdown_section, (2) preview_contains_registration_number, (3) preview_contains_tax_column_on_lines, (4) preview_shows_inclusive_note_when_tax_inclusive, (5) preview_legacy_invoice_shows_flat_tax, (6) preview_exempt_lines_show_exempt_label. ~6 tests. Render HTML and assert content contains expected strings. |
| 184.6 | Verify PDF generation with tax breakdown | 184A | 184.5 | Test: (1) pdf_generation_includes_tax_breakdown — generate PDF from preview HTML with tax data, verify it renders without errors. No pixel-perfect assertion, just smoke test that OpenHTMLToPDF doesn't choke on the updated template. ~1 test. |
| 184.7 | Test backward compatibility for preview | 184A | | Test: (1) preview_legacy_invoice_no_tax_column — verify legacy invoices (no per-line tax) render identically to before (no tax column, single Tax row). ~1 test. |
| 184.8 | Extend `InvoiceSyncEvent` with tax fields | 184B | | Modify: `backend/src/main/java/.../customerbackend/event/InvoiceSyncEvent.java`. Add fields: `List<TaxBreakdownEntry> taxBreakdown`, `String taxRegistrationNumber`, `String taxRegistrationLabel`, `String taxLabel`, `boolean taxInclusive`, `boolean hasPerLineTax`. If `InvoiceSyncEvent` is a record, add fields to constructor. Update all construction sites. |
| 184.9 | Update `InvoiceSyncEvent` publisher | 184B | 184.8 | Modify: wherever `InvoiceSyncEvent` is created/published (likely `invoice/InvoiceService.java` or an event publisher). Compute tax breakdown from invoice lines, load OrgSettings for tax config, populate new fields on the event. |
| 184.10 | Extend `PortalInvoiceView` | 184B | | Modify: `backend/src/main/java/.../customerbackend/model/PortalInvoiceView.java`. Add fields: `taxBreakdown` (JSON list), `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `taxInclusive`, `hasPerLineTax`. If this is a record, update constructor and all construction sites. Consider storing `taxBreakdown` as JSONB in the portal schema — may require a global migration (V9). |
| 184.11 | Update `PortalEventHandler` mapping | 184B | 184.10 | Modify: `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java`. In the invoice sync handler method: map new fields from `InvoiceSyncEvent` to `PortalInvoiceView`. |
| 184.12 | Update portal invoice detail display | 184B | 184.10 | Modify: `portal/app/(authenticated)/invoices/[id]/page.tsx` (or equivalent invoice detail page in portal). Display tax breakdown section matching preview layout: subtotal, each tax rate line, total. Show registration number. Show inclusive note. Conditionally hide tax details for legacy invoices. |
| 184.13 | Update portal `invoice-line-table` | 184B | | Modify: `portal/components/invoice-line-table.tsx`. Add conditional tax column (when `hasPerLineTax`): show rate name + percentage per line, or "Exempt". Match the preview template's line item tax display. |
| 184.14 | Write portal sync + display tests | 184B | 184.11, 184.12 | Backend: test event handler maps tax fields correctly. Portal frontend: test tax breakdown renders when present, hidden when legacy. ~4 backend tests, ~3 portal frontend tests. |

### Key Files

**Slice 184A — Modify:**
- `backend/src/main/resources/templates/invoice-preview.html`
- `backend/src/main/java/.../invoice/InvoiceService.java` (preview context)

**Slice 184A — Create:**
- `backend/src/test/java/.../invoice/InvoicePreviewTaxIntegrationTest.java`

**Slice 184B — Modify:**
- `backend/src/main/java/.../customerbackend/event/InvoiceSyncEvent.java`
- `backend/src/main/java/.../customerbackend/model/PortalInvoiceView.java`
- `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java`
- `portal/components/invoice-line-table.tsx`
- `portal/app/(authenticated)/invoices/[id]/page.tsx` (or equivalent)

**Read for context:**
- `backend/src/main/resources/templates/invoice-preview.html` — existing template structure
- `backend/src/main/java/.../customerbackend/event/InvoiceSyncEvent.java` — existing event fields
- `backend/src/main/java/.../customerbackend/model/PortalInvoiceView.java` — existing view model

### Architecture Decisions

- **Conditional tax column**: The tax column on line items is only shown when `hasPerLineTax` is true. Legacy invoices see no visual change — backward compatible.
- **Portal sync carries pre-computed breakdown**: The `InvoiceSyncEvent` carries the tax breakdown list rather than raw line data. Portal read-model stores the breakdown directly (as JSONB or denormalized columns). Portal display code reads pre-computed data with no calculation.
- **"Includes" language for tax-inclusive mode**: Following Xero convention, inclusive mode shows "Includes VAT: R1,500" rather than adding tax on top. This matches user expectations in SA/AU/NZ markets.

---

## Epic 185: Tax Settings + Rate Management Frontend

**Goal**: Extend the OrgSettings backend with tax configuration fields and build the frontend UI for tax settings (registration number, label, inclusive toggle) and tax rate management (table + CRUD dialogs).

**References**: Architecture doc Sections 26.2.3 (OrgSettings tax fields), 26.4.4 (settings API), 26.8.3 (frontend changes). Requirements Sections 6, 8.

**Dependencies**: Epic 181 (TaxRate entity and API must exist for rate management).

**Scope**: Backend (OrgSettings extension) + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **185A** | 185.1--185.7 | Extend `OrgSettings.java` with 4 tax fields + update settings API (accept + return tax fields) + audit event `org_settings.tax_configured` + frontend "Tax Settings" card (registration number, label, inclusive toggle) + backend test + frontend test. ~1 modified backend file, ~2 modified frontend files, ~1 new frontend file. Backend + Frontend. | **Done** (PR #384) |
| **185B** | 185.8--185.14 | Tax rate management table on settings page (or `settings/tax/page.tsx`) + Add Tax Rate dialog + Edit dialog + Deactivate action + default badge + deactivation error toast + frontend API actions + frontend tests. ~3 new frontend files, ~1 modified frontend file. Frontend only. | **Done** (PR #385) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 185.1 | Extend `OrgSettings` entity with tax fields | 185A | | Modify: `settings/OrgSettings.java`. Add 4 fields: `@Column(name = "tax_registration_number", length = 50) String taxRegistrationNumber`, `@Column(name = "tax_registration_label", length = 30) String taxRegistrationLabel`, `@Column(name = "tax_label", length = 20) String taxLabel`, `@Column(name = "tax_inclusive") boolean taxInclusive`. Add getters/setters. Add `updateTaxSettings()` method that updates all 4 fields. |
| 185.2 | Extend settings API to accept tax fields | 185A | 185.1 | Modify: `settings/OrgSettingsController.java` (or `OrgSettingsService.java`). Extend the update endpoint request DTO with: `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `taxInclusive`. Extend the GET response to return these fields. Validation: `taxRegistrationNumber` max 50 chars (optional), `taxRegistrationLabel` max 30 chars, `taxLabel` max 20 chars. |
| 185.3 | Add audit event for tax settings update | 185A | 185.2 | In `OrgSettingsService.java` (or controller): when tax settings fields change, publish audit event `org_settings.tax_configured` with changed field details. Pattern: existing audit calls in settings service. |
| 185.4 | Write backend settings tax test | 185A | 185.2 | Extend existing `OrgSettingsControllerIntegrationTest.java` or new file: (1) updateSettings_saves_tax_fields, (2) getSettings_returns_tax_fields, (3) updateSettings_validates_max_lengths. ~3 tests. |
| 185.5 | Create "Tax Settings" card on settings page | 185A | 185.2 | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx`. Add a "Tax" card linking to the tax settings page. Pattern: existing settings cards (email, integrations, rates, etc.). |
| 185.6 | Create tax settings page | 185A | 185.5 | New file: `frontend/app/(app)/org/[slug]/settings/tax/page.tsx`. Form with: Tax Registration Number (text input), Tax Registration Label (text input, placeholder "e.g., VAT Number"), Tax Label (text input, placeholder "e.g., VAT, GST"), Tax-Inclusive Pricing (toggle with explanation text). Save button updates via existing settings API. Load current values on mount. Pattern: `frontend/app/(app)/org/[slug]/settings/email/page.tsx` or similar settings sub-page. |
| 185.7 | Write frontend tax settings tests | 185A | 185.6 | New file: `frontend/__tests__/app/settings/tax-settings.test.tsx`. Tests: (1) renders_form_with_current_values, (2) saves_updated_values, (3) toggle_inclusive_shows_explanation. ~3 tests. |
| 185.8 | Create tax rate management table | 185B | | Add to `frontend/app/(app)/org/[slug]/settings/tax/page.tsx` (below settings form) or create separate component. Data table with columns: Name, Rate (%), Default (badge), Status (Active/Inactive), Actions. Fetch from `GET /api/tax-rates?includeInactive=true`. Pattern: existing settings data tables (e.g., rates page, tags page). |
| 185.9 | Create "Add Tax Rate" dialog | 185B | 185.8 | New component: `frontend/components/settings/add-tax-rate-dialog.tsx`. Dialog with fields: Name (text), Rate % (number), Is Default (toggle), Is Exempt (toggle). On submit: `POST /api/tax-rates`. Show confirmation when setting new default: "This will replace {current} as the default tax rate." Pattern: existing CRUD dialogs in settings. |
| 185.10 | Create "Edit Tax Rate" dialog | 185B | 185.8 | New component: `frontend/components/settings/edit-tax-rate-dialog.tsx`. Same fields as add dialog, pre-populated. On submit: `PUT /api/tax-rates/{id}`. Same default confirmation. |
| 185.11 | Add deactivate action | 185B | 185.8 | In tax rate table: "Deactivate" action on each row. Calls `DELETE /api/tax-rates/{id}`. If 409: show error toast with "Cannot deactivate: used on N draft invoice(s)." If success: refresh table. |
| 185.12 | Create tax rate server actions | 185B | | New file: `frontend/app/(app)/org/[slug]/settings/tax/actions.ts`. Server actions: `createTaxRate()`, `updateTaxRate()`, `deactivateTaxRate()`, `getTaxRates()`. Pattern: existing settings actions files. |
| 185.13 | Add TypeScript types for tax rates | 185B | | Modify: `frontend/lib/api-types.ts` (or equivalent). Add `TaxRateResponse` type: `{ id, name, rate, isDefault, isExempt, active, sortOrder, createdAt, updatedAt }`. Add `TaxBreakdownEntry` type: `{ rateName, ratePercent, taxableAmount, taxAmount }`. |
| 185.14 | Write frontend tax rate management tests | 185B | 185.8, 185.9, 185.11 | New file: `frontend/__tests__/app/settings/tax-rates.test.tsx`. Tests: (1) renders_rate_table, (2) add_dialog_creates_rate, (3) edit_dialog_updates_rate, (4) deactivate_shows_confirmation, (5) deactivate_error_shows_toast, (6) default_badge_displayed, (7) exempt_rate_validation. ~7 tests. |

### Key Files

**Slice 185A — Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java`
- `backend/src/main/java/.../settings/OrgSettingsController.java` (or service)
- `frontend/app/(app)/org/[slug]/settings/page.tsx`

**Slice 185A — Create:**
- `frontend/app/(app)/org/[slug]/settings/tax/page.tsx`
- `frontend/__tests__/app/settings/tax-settings.test.tsx`

**Slice 185B — Create:**
- `frontend/components/settings/add-tax-rate-dialog.tsx`
- `frontend/components/settings/edit-tax-rate-dialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/tax/actions.ts`
- `frontend/__tests__/app/settings/tax-rates.test.tsx`

**Slice 185B — Modify:**
- `frontend/app/(app)/org/[slug]/settings/tax/page.tsx`
- `frontend/lib/api-types.ts`

**Read for context:**
- `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` — settings sub-page pattern
- `frontend/components/settings/` — existing settings dialog patterns
- `backend/src/main/java/.../settings/OrgSettingsController.java` — existing API

### Architecture Decisions

- **Tax settings on separate sub-page (`settings/tax/`)**: Tax rates are conceptually distinct from billing/cost rates. A dedicated sub-page avoids confusing "tax rates" with "billing rates" in the same UI. The settings hub gets a "Tax" card linking here.
- **Combined settings + rate management on one page**: Tax settings form (registration number, label, inclusive toggle) and tax rate table live on the same page. They are closely related — changing the tax label affects how rates are displayed on invoices. No need for separate sub-routes.
- **Audit event on tax settings change**: Tracks when an org configures its tax identity. Useful for compliance audit trails.

---

## Epic 186: Invoice Editor Tax UI

**Goal**: Add tax rate selection, per-line tax display, and tax breakdown to the invoice editor frontend. Replace the manual tax amount input with a computed breakdown when per-line tax is active.

**References**: Architecture doc Section 26.8.3 (frontend changes). Requirements Section 7.

**Dependencies**: Epic 183 (API must return tax data on invoice responses), Epic 185 (tax rate fetch must be available for dropdown population).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **186A** | 186.1--186.10 | Tax rate dropdown on invoice line add/edit forms + per-line tax amount display (read-only) + tax breakdown in totals section (replaces manual tax input when per-line active) + manual tax input visibility logic + tax-inclusive indicator + extended TypeScript types + fetch tax rates for dropdown + frontend tests. ~5 modified files, ~1 new test file. Frontend only. | **Done** (PR #386) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 186.1 | Extend invoice TypeScript types | 186A | | Modify: `frontend/lib/api-types.ts`. Extend `InvoiceLineResponse` type with: `taxRateId`, `taxRateName`, `taxRatePercent`, `taxAmount`, `taxExempt`. Extend `InvoiceResponse` type with: `taxBreakdown` (array of `TaxBreakdownEntry`), `taxInclusive`, `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `hasPerLineTax`. Note: `TaxRateResponse` and `TaxBreakdownEntry` may already be added in Epic 185B — just verify/extend. |
| 186.2 | Fetch tax rates on invoice detail page | 186A | | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`. Fetch active tax rates (`GET /api/tax-rates`) and pass to `invoice-detail-client` component as a prop. Used to populate the tax rate dropdown on line forms. |
| 186.3 | Add tax rate dropdown to line add/edit form | 186A | 186.2 | Modify: `frontend/components/invoices/invoice-detail-client.tsx`. In the line item add/edit form section: add a Combobox/Select for tax rate selection. Populated from the tax rates prop. Shows rate name and percentage (e.g., "Standard VAT (15%)"). Pre-selected to the org's default rate for new lines. "None" option to explicitly remove tax. |
| 186.4 | Pass `taxRateId` in line CRUD actions | 186A | 186.3 | Modify: `frontend/app/(app)/org/[slug]/invoices/actions.ts`. Update `addLineItem()` and `updateLineItem()` server actions to include `taxRateId` in the request body. Handle null for "no tax" override. |
| 186.5 | Add per-line tax amount display | 186A | 186.3 | Modify: `frontend/components/invoices/invoice-line-table.tsx`. Add a "Tax" column to the line items table. Show `taxRateName (taxRatePercent%)` and `taxAmount` per line. Show "Exempt" for exempt lines. Column conditionally rendered only when `hasPerLineTax` is true. |
| 186.6 | Add tax breakdown in totals section | 186A | | Modify: `frontend/components/invoices/invoice-detail-client.tsx`. In the invoice totals area: when `hasPerLineTax` is true, replace the manual "Tax Amount" input with a read-only tax breakdown section. Show each `TaxBreakdownEntry` as a row: "VAT (15%): R1,500.00". Show subtotal, breakdown rows, and total. |
| 186.7 | Add manual tax input visibility logic | 186A | 186.6 | In `invoice-detail-client.tsx`: the old "Tax Amount" input field is only shown when `hasPerLineTax` is false AND invoice is DRAFT. When `hasPerLineTax` is true, the manual input is hidden and the computed breakdown is shown instead. This matches the backend's 422 rejection rule. |
| 186.8 | Add tax-inclusive indicator | 186A | | In `invoice-detail-client.tsx`: when `taxInclusive` is true (from invoice response), show a note: "Prices include {taxLabel}". Update the line item unit price label to "Unit Price (inc. {taxLabel})". |
| 186.9 | Write frontend invoice editor tax tests | 186A | 186.3, 186.5, 186.6 | New file: `frontend/__tests__/components/invoices/invoice-tax.test.tsx`. Tests: (1) tax_rate_dropdown_renders_with_rates, (2) new_line_pre_selects_default_rate, (3) tax_column_shows_on_lines_with_tax, (4) tax_breakdown_replaces_manual_input, (5) manual_input_shown_for_legacy_invoice, (6) tax_inclusive_note_displayed, (7) none_option_clears_tax, (8) exempt_line_shows_exempt, (9) tax_breakdown_groups_by_rate. ~9 tests. |
| 186.10 | Verify invoice list page handles new response fields | 186A | | Check that the invoice list page (`frontend/app/(app)/org/[slug]/invoices/page.tsx`) handles the extended `InvoiceResponse` without errors. The list likely only shows `total` — no changes needed, but verify no TypeScript errors from new optional fields. |

### Key Files

**Slice 186A — Modify:**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- `frontend/components/invoices/invoice-detail-client.tsx`
- `frontend/components/invoices/invoice-line-table.tsx`
- `frontend/app/(app)/org/[slug]/invoices/actions.ts`
- `frontend/lib/api-types.ts`

**Slice 186A — Create:**
- `frontend/__tests__/components/invoices/invoice-tax.test.tsx`

**Read for context:**
- `frontend/components/invoices/invoice-detail-client.tsx` — existing invoice editor structure
- `frontend/components/invoices/invoice-line-table.tsx` — existing line table columns
- `frontend/app/(app)/org/[slug]/invoices/actions.ts` — existing server actions

### Architecture Decisions

- **Inline tax rate dropdown (not separate line form)**: The existing invoice editor handles line add/edit inline within `invoice-detail-client.tsx`. The tax rate dropdown is added to the same inline form section, maintaining the existing UX pattern.
- **Conditional UI based on `hasPerLineTax`**: The manual tax input and the computed breakdown are mutually exclusive UI states. This prevents user confusion about which tax mechanism is active.
- **Pre-selection of default rate**: New lines auto-select the org's default tax rate in the dropdown. Users can change or remove it. This mirrors the backend auto-default behavior.

---

## Summary

| Metric | Value |
|--------|-------|
| Epics | 6 (181--186) |
| Slices | 11 (181A, 181B, 182A, 182B, 183A, 183B, 184A, 184B, 185A, 185B, 186A) |
| New backend files | ~18 |
| Modified backend files | ~14 |
| New frontend files | ~8 |
| Modified frontend files | ~8 |
| Modified portal files | ~2 |
| Migration | V43 (tenant) |
| Estimated backend tests | ~85-95 |
| Estimated frontend tests | ~25-30 |
| Critical path length | 8 slices (181A -> 181B -> 182A -> 182B -> 183A -> 183B -> 184A -> 184B) |
| Max parallelism | 2 tracks after Stage 0 |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` - Core file modified in 3 slices: tax in line CRUD, generation, preview context
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` - Extended with 5 tax fields, 3 tax methods
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` - recalculateTotals() rewritten for per-line tax + inclusive mode
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/invoices/invoice-detail-client.tsx` - Invoice editor UI: tax dropdown, breakdown, manual input toggle
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Extended with 4 tax configuration fields