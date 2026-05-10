# Invoice Tax — Acceptance Test Spec

> **Feature ID**: `invoice-tax`
> **Phase(s)**: 26 (core), 10 (foundation — `invoicing`)
> **Last Updated**: 2026-05-07
> **Status**: DRAFT

## Overview

Invoice Tax covers the org's tax-rate library, the tax calculation engine that converts a line amount + rate into a tax amount (exclusive or inclusive), per-line tax application and snapshotting, invoice-level reconciliation (`taxAmount`, `taxBreakdown`, `hasPerLineTax`, `taxInclusive`), and the org-level display labels (`taxLabel`, `taxRegistrationNumber`, `taxRegistrationLabel`). It is a sibling of `invoicing` (CRUD/lifecycle) and `invoice-generation` (time→invoice generation), all in the `invoice-core` inseparable group. Tax rates are immutable once snapshotted onto a non-DRAFT invoice line. **Out of scope**: invoice CRUD/lifecycle (`invoicing`), generation from unbilled time (`invoice-generation`), legal disbursement VAT-treatment (`legal-disbursements`).

## Prerequisites

- E2E mock-auth stack running (`bash compose/scripts/e2e-up.sh`).
- Seed data with an ACTIVE customer (with prerequisite fields populated for send-path tests).
- Org settings have `taxLabel`, `taxRegistrationNumber`, `taxRegistrationLabel`, and `taxInclusive` configurable; for tests, baseline is: `taxLabel="VAT"`, `taxInclusive=false`.
- At least one seeded tax rate (e.g., `VAT 15%`, marked default) and one zero-rated entry recommended (`Zero Rated 0%`).
- Alice (owner) and Bob (admin) hold `FINANCIAL_VISIBILITY` capability; Carol (member) does **not** (unless seeded otherwise).
- For `invoicing` integration tests, `INVOICING` capability is also required (see `invoicing.md`).

## Test Environment

- **Stack**: E2E mock-auth (frontend `http://localhost:3001`, backend `http://localhost:8081`, mock IDP `http://localhost:8090`).
- **Auth**: Mock IDP (Alice=owner, Bob=admin, Carol=member).
- **Org**: `e2e-test-org` (`orgId=org_e2e_test`).
- **Currency**: `ZAR` for all examples.

## Acceptance Criteria

### AC-001: List tax rates (active vs. all)

**Given** the tenant has multiple tax rates (mix of active + inactive, default + non-default)
**When** the user GETs `/api/tax-rates?includeInactive=` with `false` (default) or `true`
**Then** the response is a list of `TaxRateResponse` filtered accordingly

**Test Cases:**

| # | Scenario | Query | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | List active only (default) | `GET /api/tax-rates` | 200; only `active=true` rates | P0 |
| 2 | List active only (explicit) | `?includeInactive=false` | 200; only `active=true` rates | P0 |
| 3 | List all (including inactive) | `?includeInactive=true` | 200; both active and inactive returned | P0 |
| 4 | Empty tenant | tenant with zero rates | 200; empty list `[]` | P1 |
| 5 | Sort order | active rates with mixed `sortOrder` values | Returned in ascending `sortOrder` (verify by reading the response array) | P1 |
| 6 | Authenticated member can list | Carol (no `FINANCIAL_VISIBILITY`) | 200; list still returned (controller has **no** `@RequiresCapability` on GET — see `TaxRateController.java:32-38`) | P0 |
| 7 | Unauthenticated request | no JWT | 401 | P1 |
| 8 | Cross-tenant isolation | Alice from tenant A queries tenant B | only tenant A's rates (schema-per-tenant) | P0 |
| 9 | Response field shape | any | each item has `id, name, rate, isDefault, isExempt, active, sortOrder, createdAt, updatedAt` | P1 |

**Automation Notes:**
- Endpoint: `GET /api/tax-rates`. List is intentionally unrestricted so members can render historical tax rate data on existing invoices (per controller comment, `TaxRateController.java:32-33`).
- UI: tax rate table at `/org/{slug}/settings/tax` shows active rates + may show inactive ones with a status column.

---

### AC-002: Create tax rate

**Given** an authenticated user with `FINANCIAL_VISIBILITY` capability
**When** the user POSTs `/api/tax-rates` with `{name, rate, isDefault, isExempt, sortOrder}`
**Then** a new tax rate is created; if `isDefault=true` it atomically replaces the prior default

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Happy path — VAT 15% | `{name:"VAT", rate:15.00, isDefault:false, isExempt:false, sortOrder:0}` | 201; `Location: /api/tax-rates/{id}`; body matches `TaxRateResponse` | P0 |
| 2 | Create as default (no prior default) | `{name:"GST", rate:10.00, isDefault:true, ...}` | 201; `isDefault=true`; only this rate has `isDefault=true` for the tenant | P0 |
| 3 | Create as default (prior default exists) | prior `VAT` is default; create `GST` with `isDefault:true` | 201; new rate `isDefault=true`; `VAT.isDefault` flipped to `false` (atomic replacement); audit `tax_rate.default_changed` emitted | P0 |
| 4 | Exempt rate at 0% | `{name:"Exempt", rate:0.00, isExempt:true}` | 201 | P0 |
| 5 | Exempt rate at non-zero (invalid) | `{name:"Bad", rate:5.00, isExempt:true}` | 400 InvalidStateException ("Exempt tax rates must have a rate of 0.00") | P0 |
| 6 | Duplicate name | second create with existing `name` | 409 ResourceConflictException ("A tax rate with this name already exists.") | P0 |
| 7 | Missing name | `{name:"", rate:15}` | 400 (`@NotBlank`) | P1 |
| 8 | Name over 100 chars | length 101 | 400 (`@Size(max=100)`) | P2 |
| 9 | Rate below 0 | `{rate: -1}` | 400 (`@DecimalMin("0.00")`) | P0 |
| 10 | Rate above 99.99 | `{rate: 100.00}` | 400 (`@DecimalMax("99.99")`) | P0 |
| 11 | Rate at max boundary | `{rate: 99.99}` | 201 | P1 |
| 12 | Rate at min boundary | `{rate: 0.00, isExempt:false}` | 201 (zero-rated, distinct from exempt) | P0 |
| 13 | Member without `FINANCIAL_VISIBILITY` | Carol creates | 403 ForbiddenException | P0 |
| 14 | Audit emitted | happy path | `tax_rate.created` audit row exists | P1 |

**Automation Notes:**
- UI: `/org/{slug}/settings/tax` → "Add Tax Rate" button (no explicit `data-testid` — selector `getByRole("button", {name: /add|new|create/i})` per existing E2E pattern in `tax-settings.spec.ts:59`).
- Add dialog: name input (`getByRole("textbox", {name: /name/i})`), rate input (selector `input[name*="rate"]`), exempt toggle, default toggle, "Create" submit button.
- Default-replacement warning: dialog shows "This will replace the current default tax rate" when "Set as default" is toggled on while a default already exists.

---

### AC-003: Update tax rate

**Given** an existing tax rate
**When** the user PUTs `/api/tax-rates/{id}` with `{name, rate, isDefault, isExempt, sortOrder, active}`
**Then** the rate is updated; if rate, name, or exempt status changed, all DRAFT invoice lines using this rate are recalculated; non-DRAFT invoice lines are **not** affected (snapshots remain)

**Test Cases:**

| # | Scenario | Setup | Input | Expected | Priority |
|---|----------|-------|-------|----------|----------|
| 1 | Rename | `VAT` exists | `{name:"VAT-ZA", rate:15.00, ...}` | 200; name updated; DRAFT lines' `taxRateName` snapshot refreshed to "VAT-ZA" | P0 |
| 2 | Change percentage | `VAT 15%` exists, used on DRAFT line with amount 1000 → tax 150 | `{rate: 14.00, ...}` | 200; DRAFT line's `taxRatePercent=14.00`, `taxAmount=140.00`; APPROVED line still shows 15.00 + 150.00 (snapshot frozen) | P0 |
| 3 | Mark exempt | `Zero 0%` exists | `{rate:0.00, isExempt:true}` | 200; DRAFT lines snapshot refreshed; tax amount for those lines becomes 0 (short-circuit) | P0 |
| 4 | Mark exempt with non-zero rate | non-zero rate | `{rate:5.00, isExempt:true}` | 400 ("Exempt tax rates must have a rate of 0.00") | P0 |
| 5 | Promote to default (prior default exists) | another rate is default | `{isDefault:true, ...}` | 200; old default has `isDefault=false` after; audit `tax_rate.default_changed` emitted | P0 |
| 6 | Demote default | only default rate | `{isDefault:false, ...}` | 200; tenant now has zero default rates (allowed) | P1 |
| 7 | Activate inactive rate | `active=false` | `{active:true, ...}` | 200; rate visible in active list again | P1 |
| 8 | Deactivate via update | `active=true` | `{active:false, ...}` | 200; rate becomes inactive (alternative to DELETE endpoint) | P1 |
| 9 | Rename to existing name | other rate has name "VAT" | `{name:"VAT"}` | 409 ("A tax rate with this name already exists.") | P0 |
| 10 | Rename to its own name (no-op) | rate has name "VAT" | `{name:"VAT"}` | 200 (allowed; uniqueness check excludes self) | P1 |
| 11 | Update non-existent rate | bogus UUID | 404 | P1 |
| 12 | Member without capability | Carol | 403 | P0 |
| 13 | Audit emitted | happy path | `tax_rate.updated` audit row | P1 |

**Automation Notes:**
- UI edit dialog (`edit-tax-rate-dialog.tsx`): pre-filled fields, "Save" submit button.
- API verification: after rate change, GET DRAFT invoice using the rate, assert `lines[].taxRatePercent` and `lines[].taxAmount` reflect new values; APPROVED invoice using the same rate is unchanged.

---

### AC-004: Deactivate tax rate

**Given** an active tax rate
**When** the user DELETEs `/api/tax-rates/{id}`
**Then** if no DRAFT invoice line references it, the rate is set `active=false` and `isDefault=false`; otherwise the request is blocked

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Deactivate unused active rate | rate not on any line | 204; rate becomes `active=false`, `isDefault=false` | P0 |
| 2 | Deactivate rate referenced by DRAFT line | rate on ≥1 DRAFT line | 400 InvalidStateException ("Tax rate is referenced by N draft invoice line(s)") | P0 |
| 3 | Deactivate rate referenced only by APPROVED/SENT/PAID lines | rate snapshotted on non-DRAFT lines | 204; deactivation allowed (non-DRAFT lines retain their snapshot) | P0 |
| 4 | Deactivate default rate (no DRAFT references) | rate is current default | 204; rate becomes inactive **and** `isDefault=false` (deactivation clears the flag per `TaxRate.deactivate()` at `TaxRate.java:89-92`) | P0 |
| 5 | Deactivate already-inactive rate | rate already `active=false` | 204 (idempotent — final state matches request) | P2 |
| 6 | Deactivate non-existent rate | bogus UUID | 404 | P1 |
| 7 | Member without capability | Carol | 403 | P0 |
| 8 | Audit emitted | happy path | `tax_rate.deactivated` audit row | P1 |
| 9 | After deactivation, rate excluded from active list | post-deactivate | `GET /api/tax-rates` (default `includeInactive=false`) does not return it; `?includeInactive=true` does | P0 |
| 10 | After deactivation, line editor select hides it | UI refresh after deactivation | rate is filtered from `<TaxRateSelect>` (only `active=true` rendered) | P1 |

**Automation Notes:**
- UI: ban icon in tax-rate-table only shown for **active and non-default** rates. Default and inactive rates do not surface a deactivate trigger (per `tax-rates.test.tsx` "shows deactivate button only for active non-default rates").
- AlertDialog: title "Deactivate Tax Rate", body confirmation, error inline (e.g., "Tax rate is referenced by 3 draft invoice line(s)").
- Action button: "Deactivate" (destructive variant).

---

### AC-005: Apply tax rate to an invoice line

**Given** a DRAFT invoice with a line item, and at least one active tax rate
**When** the user creates or updates a line with `taxRateId` populated
**Then** the rate is loaded, validated as active, and snapshotted onto the line; tax amount is calculated using the org's `taxInclusive` flag and the line amount

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Apply VAT 15% to ex-tax line of 1000.00 | org `taxInclusive=false` | line: `taxRateId, taxRateName="VAT", taxRatePercent=15.00, taxAmount=150.00, taxExempt=false`; invoice `subtotal=1000.00, taxAmount=150.00, total=1150.00, hasPerLineTax=true` | P0 |
| 2 | Apply VAT 15% to inclusive line of 1150.00 | org `taxInclusive=true` | line: `taxAmount=150.00` (extracted from gross); invoice `total=1150.00` (equals subtotal because tax is embedded) | P0 |
| 3 | Apply zero-rated 0% rate | org `taxInclusive=false` | line: `taxAmount=0.00`; line still appears in `taxBreakdown` (zero-rated, **not** exempt) | P0 |
| 4 | Apply exempt rate (rate=0, isExempt=true) | exempt rate, line amount 1000 | line: `taxAmount=0.00, taxExempt=true`; line **excluded** from `taxBreakdown` (per `TaxCalculationService.buildTaxBreakdown` at line 71) | P0 |
| 5 | Apply rate to non-DRAFT line | invoice APPROVED | 409 (line edits blocked outside DRAFT — covered by `invoicing` AC-005-2) | P0 |
| 6 | Apply non-existent `taxRateId` | bogus UUID | 404 ResourceNotFoundException | P0 |
| 7 | Apply inactive `taxRateId` | rate exists but `active=false` | 404 (filtered out at lookup) | P0 |
| 8 | Clear tax (set `taxRateId=null`) | line had a rate | line: `taxRateId, taxRateName, taxRatePercent, taxAmount` all null; `taxExempt=false` (per `InvoiceLine.clearTaxRate()`) | P1 |
| 9 | Replace one rate with another | line had VAT 15%, switch to GST 10% | new snapshot replaces old; `taxAmount` recalculated | P1 |
| 10 | Frontend select sends literal "none" | UI uses `value="none"` for clear option (`invoice-line-editor.tsx:231`) | **See Known Bug #1** — backend expects null, not "none" | P1 |

**Automation Notes:**
- Endpoint: `POST /api/invoices/{id}/lines` or `PUT /api/invoices/{id}/lines/{lineId}` with `{taxRateId}` in the body.
- UI: tax-rate select (`<TaxRateSelect>`) in line editor renders `"{name} ({rate}%)"` (e.g., "VAT (15%)") — only active rates are listed.
- API verification: re-GET invoice; assert `lines[].taxRateId`, `taxAmount`, and invoice-level `taxAmount`, `total`, `hasPerLineTax`.

---

### AC-006: Auto-apply default tax rate

**Given** the org has a default tax rate set, and an invoice has lines without an explicit `taxRateId`
**When** `InvoiceTaxService.applyDefaultTaxToLines(invoiceId)` runs (called by invoice-generation flow)
**Then** every line gets the default rate applied (snapshot + tax amount)

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Default exists, lines have no rate | default `VAT 15%`; 2 manual lines @ 500 each | both lines get `VAT, 15%, 75.00`; invoice `taxAmount=150.00` | P0 |
| 2 | Default exists, line already has a rate | line has `GST 10%` already | line is **overwritten** with default (per agent note: "lines that already have a rate get overwritten"). **Verify and document** — see Known Bug #2 | P1 |
| 3 | No default rate | tenant has no rate with `isDefault=true` | early return; no rate applied; lines stay un-taxed | P0 |
| 4 | Default rate is inactive | default flag on an inactive rate (edge case after re-activation/race) | service should fail or skip; verify behaviour against `TaxRateRepository.findByIsDefaultTrue()` | P2 |

**Automation Notes:**
- This service path is internal — usually only triggered by invoice-generation. Direct API testing requires invoking the generation endpoint (which sets `taxRateId=null` initially, then default-applies).
- Tested in concert with `invoice-generation` spec; here we verify behaviour, not the trigger.

---

### AC-007: Tax calculation — exclusive (tax-on-top)

**Given** org `taxInclusive=false` and a line with amount A and rate R%
**When** tax is calculated
**Then** `tax = A × (R/100)`, rounded HALF_UP to 2 decimals

**Arithmetic test cases** (Financial Accuracy section also covers these):

| # | A (amount) | R (%) | Expected tax | Expected total (line + tax) |
|---|-----------|-------|--------------|---------------------------|
| 1 | 1000.00 | 15.00 | 150.00 | 1150.00 |
| 2 | 100.00 | 15.00 | 15.00 | 115.00 |
| 3 | 33.33 | 15.00 | 5.00 (33.33 × 0.15 = 4.9995 → HALF_UP 5.00) | 38.33 |
| 4 | 0.10 | 15.00 | 0.02 (0.10 × 0.15 = 0.015 → HALF_UP 0.02) | 0.12 |
| 5 | 1000.00 | 0.00 | 0.00 | 1000.00 |
| 6 | 1000.00 | 99.99 | 999.90 | 1999.90 |
| 7 | 0.00 | 15.00 | 0.00 | 0.00 |

**Automation Notes:** Verify via `POST /api/invoices/{id}/lines` with rate applied, then re-GET and assert `lines[].taxAmount` exactly. Use `expect(line.taxAmount).toBe(150.00)` — strict equality, not `toBeCloseTo`.

---

### AC-008: Tax calculation — inclusive (extract from gross)

**Given** org `taxInclusive=true` and a line with gross amount G and rate R%
**When** tax is calculated
**Then** `divisor = 1 + R/100` (10-decimal precision); `exTax = G / divisor` (HALF_UP, scale 2); `tax = G − exTax`

**Arithmetic test cases:**

| # | G (gross amount) | R (%) | Expected exTax | Expected tax | Note |
|---|------------------|-------|---------------|--------------|------|
| 1 | 1150.00 | 15.00 | 1000.00 | 150.00 | Clean reverse |
| 2 | 100.00 | 15.00 | 86.96 (100/1.15 = 86.95652... → HALF_UP 86.96) | 13.04 | |
| 3 | 1.15 | 15.00 | 1.00 | 0.15 | |
| 4 | 1000.00 | 0.00 | 1000.00 | 0.00 | divisor=1.00 |
| 5 | 0.00 | 15.00 | 0.00 | 0.00 | |
| 6 | 99.99 | 99.99 | 50.00 (99.99 / 1.9999 = 49.9975 → HALF_UP 50.00) | 49.99 | Boundary |

**Automation Notes:**
- The calculation is **per-line** in `TaxCalculationService.calculateLineTax`. The invoice-level total in inclusive mode equals the subtotal (tax is embedded), per `Invoice.recalculateTotals` when `hasPerLineTax && taxInclusive`.
- Set `taxInclusive=true` via org settings (`PUT /api/org-settings` — covered in `settings-general`). For tests, an API helper or seed flag may be required.

---

### AC-009: Per-line tax detection (`hasPerLineTax`)

**Given** an invoice with a mix of lines (some with rate, some without)
**When** the response is built
**Then** `hasPerLineTax = true` iff at least one line has `taxRateId != null`

**Test Cases:**

| # | Scenario | Lines | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | All lines have rate | 3 lines, all with VAT | `hasPerLineTax=true` | P0 |
| 2 | One line has rate | 2 lines, 1 with VAT, 1 without | `hasPerLineTax=true` | P0 |
| 3 | No line has rate | 2 lines, neither with rate | `hasPerLineTax=false` | P0 |
| 4 | Empty invoice | 0 lines | `hasPerLineTax=false` (`Stream.anyMatch` on empty is false) | P1 |
| 5 | Exempt-rated line | 1 line with exempt rate | `hasPerLineTax=true` (exempt is still a rate snapshot) | P1 |
| 6 | Manual tax field hidden when `hasPerLineTax=true` | UI on invoice detail | "Tax Amount" input is hidden — covered by `invoice-tax.test.tsx` "hides manual tax input when hasPerLineTax is true" | P1 |
| 7 | Manual tax input visible when `hasPerLineTax=false` and DRAFT | UI | "Tax Amount" input visible | P1 |

---

### AC-010: Tax breakdown grouping

**Given** an invoice with multiple lines, possibly using multiple distinct tax rates
**When** the invoice is fetched
**Then** `taxBreakdown` contains one entry per distinct (rateName, ratePercent) pair, with `taxableAmount` and `taxAmount` summed; exempt lines are excluded; insertion order is preserved

**Test Cases:**

| # | Scenario | Lines | Expected `taxBreakdown` | Priority |
|---|----------|-------|------------------------|----------|
| 1 | Single rate, multiple lines | 3 lines × VAT 15% (amounts 100, 200, 300) | 1 entry: `(VAT, 15.00, taxableAmount=600, taxAmount=90)` | P0 |
| 2 | Two distinct rates | 2 lines × VAT 15% (200) + 1 line × GST 10% (100) | 2 entries: `(VAT, 15.00, 400, 60)`, `(GST, 10.00, 100, 10)` | P0 |
| 3 | Same name, different percent | rate "VAT" at 15% historic snapshot + rate "VAT" at 14% new snapshot on different lines | 2 entries: `(VAT, 15.00, ...)`, `(VAT, 14.00, ...)` (key is name+percent, not name alone) | P1 |
| 4 | Exempt line excluded | 1 normal VAT line + 1 exempt line | `taxBreakdown` has only the VAT entry; exempt line absent (per `TaxCalculationService.java:71`) | P0 |
| 5 | Zero-rated NOT excluded | 1 line with `Zero Rated 0%` | breakdown entry: `(Zero Rated, 0.00, taxableAmount=line.amount, taxAmount=0.00)` | P0 |
| 6 | No per-line tax | no line has rate | empty list `[]` | P0 |
| 7 | Order is insertion order | line A: GST, line B: VAT, line C: GST | breakdown: `[GST, VAT]` (LinkedHashMap preserves first-encountered order) | P1 |

**Automation Notes:**
- UI: `invoice-totals-section.tsx` renders each breakdown entry as `"{name} ({percent}%)"` followed by formatted currency. Verify two distinct rows when two rates present.
- Subtle behaviour: exempt vs zero-rated **differ in the breakdown** even though both produce 0 tax. This is by design (per code comment in `TaxCalculationService.java:31-32`).

---

### AC-011: Tax-inclusive UI indicator

**Given** org `taxInclusive=true`
**When** the user views invoice totals (detail page)
**Then** a small indicator "Prices include {taxLabel}" is rendered below the total (e.g., "Prices include VAT")

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Inclusive + label set | `taxInclusive=true, taxLabel="VAT"` | indicator visible: "Prices include VAT" | P1 |
| 2 | Inclusive + null label | `taxInclusive=true, taxLabel=null` | indicator hidden (covered by unit test "does not show tax-inclusive note when taxLabel is null") | P1 |
| 3 | Exclusive (default) | `taxInclusive=false` | indicator hidden | P1 |
| 4 | Inclusive + custom label | `taxInclusive=true, taxLabel="GST"` | "Prices include GST" | P2 |

**Automation Notes:**
- Selector: `getByText(/Prices include/i)`.
- Style: `text-xs text-slate-500`, below total, after a divider.

---

### AC-012: Tax registration display fields (org-level)

**Given** org settings with `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`
**When** an invoice is rendered (preview HTML, detail page, portal view)
**Then** the org-level tax identity is shown in the header/footer of the invoice

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | All three set | `{taxRegistrationNumber:"4123456789", taxRegistrationLabel:"VAT Number", taxLabel:"VAT"}` | preview/detail shows "VAT Number: 4123456789" somewhere in the rendered invoice | P1 |
| 2 | Number set, label null | `{taxRegistrationNumber:"4123456789", taxRegistrationLabel:null}` | falls back to default label (`"Tax Number"` per UI default) | P2 |
| 3 | Number null | `{taxRegistrationNumber:null}` | row is hidden (no empty label rendered) | P2 |
| 4 | Read-time vs snapshot | invoice approved when number was "X"; org changes to "Y"; re-fetch | re-fetched invoice shows "Y" — **not snapshotted at issue time**. **See `invoicing.md` Known Bug #4** | P1 |

**Automation Notes:**
- Settings UI: `/org/{slug}/settings/tax` has the org-level form (not the rate table). Fields: "Tax Registration Number", "Registration Label", "Tax Label", "Tax-inclusive pricing" toggle.
- Org settings update endpoint: `PUT /api/org-settings` (covered in `settings-general`).

---

### AC-013: Tax rate referenced by line — display in line table

**Given** an invoice with `hasPerLineTax=true`
**When** the invoice detail page renders the line table
**Then** a "Tax" column is shown; per-line cells display `"{name} ({percent}%)"` and the formatted tax amount; exempt lines display "Exempt" badge

**Test Cases (covered partially by `invoice-tax.test.tsx`):**

| # | Scenario | Expected | Priority |
|---|----------|----------|----------|
| 1 | "Tax" column visible when `hasPerLineTax=true` | column header visible | P0 |
| 2 | "Tax" column hidden when `hasPerLineTax=false` | no "Tax" header | P0 |
| 3 | Cell shows `"VAT (15%)"` + tax amount | line with VAT 15% on 1000 | "VAT (15%)" + "R150.00" rendered | P0 |
| 4 | Exempt badge for exempt line | line with exempt rate | "Exempt" badge text visible | P0 |

**Automation Notes:**
- Line table component: `invoice-line-table.tsx`. Selector: `getByRole("columnheader", {name: /Tax/})` for header.
- No `data-testid` attributes on cells — text-based selectors only.

---

### AC-014: Snapshot immutability after non-DRAFT transition

**Given** an invoice line with a tax rate snapshot, and the invoice has been APPROVED
**When** the underlying `TaxRate` is later edited (rate changed, name changed, deactivated)
**Then** the line's snapshot fields (`taxRateName`, `taxRatePercent`, `taxAmount`, `taxExempt`) remain unchanged

**Test Cases:**

| # | Scenario | Setup | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | Rate % changed after APPROVAL | INV approved with line @ VAT 15%, tax 150; later VAT updated to 14% | line still shows `taxRatePercent=15.00, taxAmount=150.00` on re-GET | P0 |
| 2 | Rate renamed after APPROVAL | rate "VAT" → "VAT-ZA" | line still shows `taxRateName="VAT"` | P0 |
| 3 | Rate deactivated after APPROVAL | rate set inactive (only allowed because no DRAFT references) | line still resolves correctly; `taxRateId` may point at inactive rate but snapshot fields are intact | P0 |
| 4 | Rate exempt-flag flipped after APPROVAL | rate flips from non-exempt to exempt (unusual edge case) | line still has historical `taxExempt=false`; tax amount intact | P1 |
| 5 | Compare DRAFT vs APPROVED behaviour on same rate update | DRAFT invoice + APPROVED invoice both use VAT 15%; update VAT to 14% | DRAFT line recalculated to 14% / new tax; APPROVED line unchanged at 15% / old tax (per `TaxRateService` recalculation guarded by `InvoiceStatus.DRAFT`) | P0 |

**Automation Notes:**
- This is the core invariant of the snapshotting design. Verifying it requires: create + approve invoice → update underlying rate → re-GET invoice → assert no change.
- Failure mode would be a regression where `batchRecalculateDraftLines` accidentally widens to all statuses.

---

### AC-015: Tax settings page (org-level config)

**Given** an authenticated user with appropriate capability
**When** they visit `/org/{slug}/settings/tax`
**Then** the page renders org-level tax config form + tax-rate management table

**Test Cases:**

| # | Scenario | Expected | Priority |
|---|----------|----------|----------|
| 1 | Page heading | "Tax Settings" h1 visible (covered by `tax-settings.test.tsx`) | P1 |
| 2 | Org form fields visible | "Tax Registration Number", "Registration Label", "Tax Label", "Tax-inclusive pricing" toggle | P1 |
| 3 | Tax rate table visible | columns: Name, Rate, Default, Status, Actions | P1 |
| 4 | Empty state when no rates | "No tax rates yet" + "Create your first tax rate..." (covered) | P1 |
| 5 | Default badge | rate with `isDefault=true` shows "Default" badge | P1 |
| 6 | Exempt badge | rate with `isExempt=true` shows "Exempt" badge | P1 |
| 7 | Inactive rate row | shows "Inactive" status; deactivate trigger absent | P1 |
| 8 | Back link | "Settings" link in breadcrumb returns to `/org/{slug}/settings` | P2 |
| 9 | Save org settings | edit `taxLabel` to "GST" + submit | 200; subsequent invoice detail shows "GST" wherever taxLabel is rendered | P1 |

---

## State Machine Tests

Tax rates have a simple lifecycle:

| From | To | Trigger | Guards | Expected |
|------|----|---------|--------|----------|
| (new) | active | `POST /api/tax-rates` | name unique; rate ∈ [0,99.99]; if exempt then rate=0 | 201 |
| active (default=false) | active (default=true) | `PUT` with `isDefault:true` | none | 200; prior default demoted |
| active (default=true) | active (default=false) | `PUT` with `isDefault:false` | none | 200; tenant has zero defaults |
| active | inactive | `DELETE` or `PUT active=false` | no DRAFT lines reference it | 204 / 200; `isDefault` cleared |
| active (default=true) | inactive | `DELETE` | no DRAFT references | 204; both `active=false` and `isDefault=false` |
| inactive | active | `PUT active=true` | none | 200 |

### Forbidden / blocked

| Attempt | Reason | Error |
|---------|--------|-------|
| Deactivate while DRAFT lines reference it | invariant: snapshot integrity | 400 ("Tax rate is referenced by N draft invoice line(s)") |
| Set `isExempt=true` with non-zero rate | invariant: exempt = 0% | 400 ("Exempt tax rates must have a rate of 0.00") |
| Create with duplicate name | uniqueness | 409 |
| Set rate < 0 or > 99.99 | bounds | 400 |
| Two rates with `isDefault=true` simultaneously | invariant: at most one default | impossible — atomic replacement on create/update |

---

## Permission Matrix

| Action | Endpoint | Owner | Admin | Member | Notes |
|--------|----------|-------|-------|--------|-------|
| List tax rates | `GET /api/tax-rates` | ✅ | ✅ | ✅ | No `@RequiresCapability` — intentional, all members can read for invoice rendering (per controller comment) |
| Create tax rate | `POST /api/tax-rates` | ✅ | ✅ | ❌ (403) | `FINANCIAL_VISIBILITY` |
| Update tax rate | `PUT /api/tax-rates/{id}` | ✅ | ✅ | ❌ | `FINANCIAL_VISIBILITY` |
| Deactivate tax rate | `DELETE /api/tax-rates/{id}` | ✅ | ✅ | ❌ | `FINANCIAL_VISIBILITY` |
| Apply tax to line | (via invoice line endpoints) | ✅ | ✅ | ❌ (403 from `INVOICING`) | inherits invoice RBAC |
| Edit org tax settings | `PUT /api/org-settings` | ✅ | ✅ | ❌ | covered in `settings-general` |

> Note: `FINANCIAL_VISIBILITY` is a distinct capability from `INVOICING`. A role can have one but not the other. For the seeded E2E roles, owner and admin hold both; member holds neither.

**Test Cases (P1):**

| # | User | Action | Expected |
|---|------|--------|----------|
| 1 | Carol (member) | List rates | 200 (allowed) |
| 2 | Carol (member) | Create rate | 403 |
| 3 | Carol (member) | Update rate | 403 |
| 4 | Carol (member) | Deactivate rate | 403 |
| 5 | Bob (admin) | Full CRUD | all succeed |
| 6 | Alice (owner) | Full CRUD | all succeed |
| 7 | Cross-tenant: Alice from tenant A queries tenant B's rate | `GET /api/tax-rates` | only tenant A's rates returned |

---

## Financial Accuracy

All amounts use `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Rate stored at scale 2; calculation divisor at scale 10 to minimise rounding drift.

### Exclusive (tax-on-top): `tax = amount × (rate / 100)`, HALF_UP scale 2

| # | Inputs | Calculation | Expected tax | Expected total |
|---|--------|-------------|--------------|----------------|
| 1 | amount=1000.00, rate=15% | 1000 × 0.15 | 150.00 | 1150.00 |
| 2 | amount=33.33, rate=15% | 33.33 × 0.15 = 4.9995 → HALF_UP | 5.00 | 38.33 |
| 3 | amount=0.10, rate=15% | 0.10 × 0.15 = 0.015 → HALF_UP | 0.02 | 0.12 |
| 4 | amount=100.00, rate=99.99% | 100 × 0.9999 = 99.99 | 99.99 | 199.99 |
| 5 | amount=1000.00, rate=0% | 0 | 0.00 | 1000.00 |
| 6 | exempt rate at any amount | short-circuit | 0.00 | (= amount) |

### Inclusive (extract from gross): `divisor = 1 + rate/100` (scale 10); `exTax = gross / divisor` (scale 2 HALF_UP); `tax = gross − exTax`

| # | Inputs (gross) | Divisor | exTax | Tax |
|---|----------------|---------|-------|-----|
| 1 | 1150.00 @ 15% | 1.15 | 1000.00 | 150.00 |
| 2 | 100.00 @ 15% | 1.15 | 86.96 (100/1.15=86.95652... HALF_UP) | 13.04 |
| 3 | 1.15 @ 15% | 1.15 | 1.00 | 0.15 |
| 4 | 1000.00 @ 0% | 1.00 | 1000.00 | 0.00 |
| 5 | 99.99 @ 99.99% | 1.9999 | 50.00 (99.99/1.9999=49.9975 HALF_UP) | 49.99 |

### Multi-line aggregation

| # | Lines | Per-line tax | Invoice subtotal | Invoice taxAmount | Invoice total |
|---|-------|--------------|-----------------|-------------------|---------------|
| 1 | 3 × (qty=1, price=100, VAT 15%) | 15 each | 300.00 | 45.00 | 345.00 |
| 2 | 2 × VAT 15% on 200 + 1 × GST 10% on 100 | 30, 30, 10 | 500.00 | 70.00 | 570.00 |
| 3 | 1 × VAT 15% on 100 + 1 × Exempt | 15, 0 | 100.00 + exemptLineAmount | 15.00 (exempt excluded from breakdown) | subtotal + 15 |
| 4 | 1 × Zero Rated 0% on 100 | 0 | 100.00 | 0.00 (but breakdown row exists) | 100.00 |
| 5 | Inclusive mode: 2 × VAT 15% on 1150 (gross) | 150 each | 2300.00 | 300.00 | 2300.00 (total = subtotal because tax embedded) |

---

## Cross-Feature Integration Points

| Integration | Related Feature | What to Verify |
|-------------|----------------|----------------|
| Tax rate snapshot lifecycle | `invoicing` | Line snapshot frozen after invoice exits DRAFT (covered by AC-014) |
| Default tax auto-application | `invoice-generation` | Generated invoices have default rate applied to TIME and EXPENSE lines via `InvoiceTaxService.applyDefaultTaxToLines` |
| `taxInclusive` flag source | `settings-general` | Org settings is the source of truth for `taxInclusive`; tax-rates page also exposes the toggle |
| `taxLabel`, registration display | `settings-general` | Rendered on invoice detail/preview/PDF; **not snapshotted** — see `invoicing.md` Known Bug #4 |
| Disbursement VAT treatment | `legal-disbursements` | Legal vertical uses a separate `VatTreatment` enum (STANDARD_15, ZERO_RATED_PASS_THROUGH, EXEMPT) on disbursements — **not** the `TaxRate` table; calculation path differs |
| Portal tax display | `portal-invoices` | Portal-rendered invoice shows the same `taxBreakdown` (read-only) |
| Audit | `audit-trail` | `tax_rate.created`, `tax_rate.updated`, `tax_rate.deactivated`, `tax_rate.default_changed` events |
| Custom tax categories | `invoicing` (TaxType enum) | `Invoice.taxType` enum (VAT, GST, SALES_TAX, NONE) is metadata only — does **not** drive calculation; tested in `invoicing.md` |

---

## Known Bugs

| # | Description | Severity | Source | Status |
|---|-------------|----------|--------|--------|
| 1 | Frontend `<TaxRateSelect>` (`invoice-line-editor.tsx:231`) uses literal value `"none"` for the clear-tax option, but the backend `applyTaxToLine` expects `null` to mean "use org default rate" — not "clear tax". The "none" string is also not a UUID. The form must translate `"none"` → `null` at submit time, **or** the server must coerce. Behaviour for the "none" path is implicit and undocumented. | Medium | `frontend/components/invoices/invoice-line-editor.tsx:231,235-238` | OPEN |
| 2 | `InvoiceTaxService.applyDefaultTaxToLines` overwrites existing rate snapshots with the org default per the agent's read of the code. This means: invoice generation flow can clobber a manually-set rate on a draft line if `applyDefaultTaxToLines` is called after the line was edited. **Verify behaviour against the actual service code and clarify whether this is intentional or a bug.** | Medium | `InvoiceTaxService.applyDefaultTaxToLines` | OPEN — needs verification |
| 3 | Deactivating a tax rate is permitted when only non-DRAFT (APPROVED/SENT/PAID) lines reference it. After deactivation, those historical invoices retain their snapshots correctly, but a re-render may attempt to re-resolve the rate by ID for display purposes (e.g., to show "active" / "deprecated" status next to the rate name). The UI does not currently surface this state to the user. | Low | `TaxRateService.deactivateTaxRate` + `tax-rate-table.tsx` | OPEN — UX gap |
| 4 | The "Zero-rated" vs "Exempt" distinction is subtle: both produce 0 tax, but zero-rated lines appear in `taxBreakdown` while exempt lines do not. There is no UI explanation of this difference at the rate-creation step. Users may inadvertently mark a 0% rate as exempt and then be confused why it doesn't show in the invoice breakdown. | Low | `add-tax-rate-dialog.tsx` + `TaxCalculationService.buildTaxBreakdown:71` | OPEN — UX gap |
| 5 | `InvoiceResponse.taxLabel`, `taxRegistrationNumber`, `taxRegistrationLabel` are read from current org settings at every read (not snapshotted at issue date). For long-lived audit/legal compliance (e.g., tax authorities re-inspecting invoices issued 5 years ago), the values shown will be the current org values, not those in effect when the invoice was issued. **This is the same defect noted in `invoicing.md` Known Bug #4.** Cross-referenced here because tax registration is the most consequential field. | Medium | `InvoiceResponse` builder | OPEN — needs product decision |
| 6 | Existing E2E tax-settings spec (`tax-settings.spec.ts`) is mostly skip-friendly (`test.skip(true, "...")` everywhere) — passes whether the page works or not. Effectively zero hard coverage. | Medium | `frontend/e2e/tests/settings/tax-settings.spec.ts` | OPEN — automation quality |
| 7 | Tax rate has no `description` field. If the founder wants to surface explanatory text per rate (e.g., "Use for non-VAT-registered buyers"), this requires schema + UI work. | Info | `TaxRate.java` | NOT-A-BUG |
| 8 | Calling `PUT /api/tax-rates/{id}` with both `isDefault:false` and the rate is currently the only default leaves the tenant with **zero default rates**. Subsequent invoice generation has no rate to auto-apply (early return in `applyTaxToLine`). The behaviour is correct per the code, but worth flagging — UI doesn't warn the user. | Low | `TaxRateService.updateTaxRate` + `add-tax-rate-dialog.tsx` | OPEN — UX gap |

---

## Playwright Test File Mapping

| Spec File | Coverage |
|-----------|----------|
| `frontend/e2e/tests/settings/tax-settings.spec.ts` (existing — **rewrite**, currently mostly skips) | AC-015 (1-4,6-9), AC-002 (1-3,5-7,12), AC-003 (1-3,5,7-10), AC-004 (1-2,4,9,10) |
| `frontend/e2e/tests/invoices/invoice-tax-application.spec.ts` (new) | AC-005 (1-9), AC-006 (1-3), AC-009 (1-5), AC-010 (1-7) |
| `frontend/e2e/tests/invoices/invoice-tax-arithmetic.spec.ts` (new) | AC-007 (all rows), AC-008 (all rows), Financial Accuracy multi-line table |
| `frontend/e2e/tests/invoices/invoice-tax-snapshot.spec.ts` (new) | AC-014 (1-5) — the snapshot immutability invariant |
| `frontend/e2e/tests/invoices/invoice-tax-display.spec.ts` (new) | AC-011 (1-4), AC-012 (1-3), AC-013 (1-4) |
| `frontend/e2e/tests/settings/tax-settings-rbac.spec.ts` (new) | Permission Matrix (all P1 cases) |
| `frontend/__tests__/components/invoices/invoice-tax.test.tsx` (existing, keep) | AC-009 (6,7), AC-010 (UI bits), AC-011 (1-3), AC-013 (1-4) |
| `frontend/__tests__/app/settings/tax-rates.test.tsx` (existing, keep) | AC-015 (3-7) |
| `frontend/__tests__/app/settings/tax-settings.test.tsx` (existing, keep) | AC-015 (1,2) |

---

## Out-of-Scope (deferred to sibling specs)

- **Invoice CRUD/lifecycle** → `invoicing`.
- **Invoice generation from time/expenses** (including auto-applying default tax to TIME + EXPENSE lines) → `invoice-generation`.
- **Disbursement VAT treatment** (legal vertical, separate calculation path) → `legal-disbursements`.
- **Email delivery / portal tax display rendering** → `invoice-email`, `portal-invoices`.
- **Org settings form** (`taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `taxInclusive` editing) → `settings-general` (this spec verifies the **effects** of the settings on tax behaviour, not the form CRUD itself).
- **Reporting on tax** (e.g., VAT 201 returns, period summaries) → `reports`.
