# Phase 26 — Invoice Tax Handling

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a complete invoicing system (Phase 10) that supports DRAFT → APPROVED → SENT → PAID lifecycle, unbilled time generation, line items, HTML preview, and PDF generation. Tax handling is currently minimal — `Invoice` has a single `taxAmount` field (BigDecimal, default 0) that is manually entered as a flat value. There is no tax rate configuration, no per-line-item tax calculation, no VAT/GST registration number support, and no tax-inclusive/exclusive distinction.

**Existing infrastructure this phase builds on:**

- **Invoice entity** (`invoice/Invoice.java`): Fields include `subtotal` (sum of line amounts), `taxAmount` (flat BigDecimal, manually set via `updateDraft()`), `total` (subtotal + taxAmount). Status enum: DRAFT, APPROVED, SENT, PAID, VOID. `InvoiceService.updateDraft()` accepts `taxAmount` as a request parameter and stores it as-is. The `recalculateTotals()` method sums line amounts into `subtotal` and adds `taxAmount` to get `total`.
- **InvoiceLine entity** (`invoice/InvoiceLine.java`): Fields include `description`, `quantity` (10,4), `unitPrice` (12,2), `amount` (14,2, calculated as quantity × unitPrice). No tax-related fields. Optional source references: `projectId`, `timeEntryId`, `retainerPeriodId`.
- **Invoice generation from time** (`invoice/InvoiceService.java`): `generateFromUnbilledTime()` creates lines from time entries (hours × snapshotted rate). Sets `taxAmount = 0` on generated invoices.
- **Retainer invoice generation** (`retainer/RetainerPeriodService.java`): Generates invoices from retainer periods with line items. Sets `taxAmount = 0`.
- **OrgSettings** (`settings/OrgSettings.java`): Has `defaultCurrency` (String, required), `logoS3Key`, `brandColor`, `documentFooterText`. No tax-related fields.
- **Rate card infrastructure** (`rate/`): `BillingRate` and `CostRate` entities with 3-level hierarchy (org → project → customer). `BillingRateService` resolves effective rate. This pattern is a reference for how TaxRate could be structured, though tax rates are simpler (org-level only for v1).
- **Invoice HTML preview** (`invoice/InvoicePreviewService.java`): Generates HTML preview from invoice data. Currently shows subtotal, tax (flat amount), and total. Template is Thymeleaf-based.
- **Invoice PDF generation** (Phase 12): `PdfRenderingService` converts HTML preview to PDF. The preview template drives what appears in the PDF.
- **Custom fields on invoices** (Phase 23): `Invoice` has `customFields` (JSONB Map) and `appliedFieldGroups` (JSONB List). Custom field values are rendered in preview/PDF.
- **Portal invoice view** (Phase 22): `portal/` app displays invoice details including line items, subtotal, tax, total. Reads from portal read-model.
- **Portal read-model** (Phase 7 + 22): `PortalInvoiceProjection` includes `subtotal`, `taxAmount`, `total`. `InvoiceEventHandler` syncs invoice state to read-model.
- **Document templates** (Phase 12): `DocumentTemplate` with Thymeleaf rendering. Invoice context builder assembles template data. Tax fields would need to be added to the context.
- **Flyway migrations**: Current latest is V39 (Phase 24). Next available is V40.

## Objective

Add proper tax handling to invoicing so that invoices correctly calculate, display, and report tax (VAT/GST/sales tax). After this phase:

- Orgs configure their tax identity (registration number, default tax rate, tax-inclusive/exclusive preference) in org settings
- Tax rates are defined at the org level with a name, percentage, and active/default status
- Each invoice line item can have a tax rate applied, with tax calculated automatically
- Invoice totals show a proper tax breakdown: subtotal (ex-tax), tax by rate, and total (inc-tax)
- Generated invoices (from unbilled time or retainer periods) automatically apply the org's default tax rate
- The invoice HTML preview, PDF, and portal view all display the tax breakdown correctly
- The org's tax registration number appears on invoices (required in most jurisdictions for tax-compliant invoicing)
- Existing invoices with manual flat tax amounts continue to work (backward compatible)

## Constraints & Assumptions

- **Tax-exclusive is the default convention.** Line item `unitPrice` and `amount` are pre-tax values. Tax is calculated on top. An org-level toggle allows switching to tax-inclusive (where line amounts include tax and the tax portion is extracted). This matches SA B2B convention and most practice management tools.
- **Org-level tax rates only for v1.** Unlike billing rates (which have org → project → customer hierarchy), tax rates are configured at the org level. Per-customer tax exemptions and per-line overrides are the extent of flexibility needed. A full multi-jurisdiction tax engine is out of scope.
- **Single tax rate per line item.** No compound tax (tax-on-tax), no multiple taxes per line. One rate per line, one tax column. This covers VAT, GST, and simple sales tax scenarios.
- **Zero-rated and exempt are distinct.** Zero-rated (0% tax, still reported as taxable) vs. exempt (no tax applies, excluded from tax reporting). Both are supported as tax rate entries.
- **Backward compatibility.** Existing invoices with `taxAmount` set as a flat value must continue to display correctly. The migration adds new columns but doesn't alter existing data. When an invoice has per-line tax rates, `taxAmount` is recalculated as the sum of line-level taxes. When no line-level tax rates exist (legacy invoices), the flat `taxAmount` is used as-is.
- **No tax filing or reporting integration.** This phase does not generate tax returns, connect to SARS eFiling, or produce MTD-compliant submissions. Tax amounts on invoices feed into the existing reporting/export pipeline (Phase 19) which can be used for manual tax preparation.
- **All new entities and columns are tenant-scoped** (schema-per-tenant, no multitenancy boilerplate needed per Phase 13).

## Detailed Requirements

### 1. Org Tax Configuration (OrgSettings Extension)

**Problem:** OrgSettings has no tax-related fields. Orgs need to configure their tax identity and default behavior to generate tax-compliant invoices.

**Requirements:**
- Add the following fields to `OrgSettings`:
  - `String taxRegistrationNumber` — org's VAT/GST/tax ID (nullable, displayed on invoices when present). Label varies by jurisdiction but the field is a simple string. Max 50 characters.
  - `String taxRegistrationLabel` — the label for the tax ID field (e.g., "VAT Number", "GST Number", "Tax ID"). Defaults to "Tax Number" if not set. Max 30 characters.
  - `String taxLabel` — the label used for tax on invoices (e.g., "VAT", "GST", "Tax"). Defaults to "Tax". Max 20 characters.
  - `boolean taxInclusive` — whether line item prices include tax (default: `false`, meaning tax-exclusive).
- Add a "Tax Settings" section to the org settings UI (Settings page) with fields for the above.
- The tax registration number and label should appear on invoice previews/PDFs in the header/footer area alongside org details.
- Validation: `taxRegistrationNumber` is optional but when provided must be non-blank. `taxLabel` and `taxRegistrationLabel` have sensible defaults.

### 2. TaxRate Entity & CRUD

**Problem:** There is no concept of named, reusable tax rates. The current `taxAmount` is a raw number with no connection to a rate definition.

**Requirements:**
- Create a `TaxRate` entity in a new `tax/` package:
  - `UUID id`
  - `String name` — display name (e.g., "Standard VAT", "Zero-rated", "Exempt"). Max 100 characters, required.
  - `BigDecimal rate` — percentage as decimal (e.g., 15.00 for 15%). Precision (5,2). Range: 0.00 to 99.99.
  - `boolean isDefault` — whether this rate is automatically applied to new invoice lines. Only one rate can be default per org.
  - `boolean isExempt` — whether this rate represents a tax exemption (affects display: shows "Exempt" instead of "0%" and excludes from tax totals in reports).
  - `boolean active` — soft-active flag. Inactive rates are hidden from selection but preserved on existing invoice lines.
  - `int sortOrder` — display ordering.
  - `Instant createdAt`, `Instant updatedAt`
- `TaxRateRepository` — standard JPA repository.
- `TaxRateService` with:
  - `createTaxRate(CreateTaxRateRequest)` — validates uniqueness of name within org, enforces single-default constraint (creating a new default unsets the previous one).
  - `updateTaxRate(UUID id, UpdateTaxRateRequest)` — same validations.
  - `deleteTaxRate(UUID id)` — soft-delete (sets `active = false`). Cannot delete if rate is used on any DRAFT invoice lines (return a 409 with count of affected invoices). Can delete if only used on finalized invoices (APPROVED/SENT/PAID/VOID).
  - `listTaxRates(boolean includeInactive)` — returns all rates, ordered by `sortOrder`.
  - `getDefaultTaxRate()` — returns the default rate, or empty if none set.
- REST controller: `TaxRateController` at `/api/tax-rates`:
  - `GET /api/tax-rates` — list rates (query param: `includeInactive`, default false)
  - `POST /api/tax-rates` — create rate (ADMIN/OWNER only)
  - `PUT /api/tax-rates/{id}` — update rate (ADMIN/OWNER only)
  - `DELETE /api/tax-rates/{id}` — deactivate rate (ADMIN/OWNER only)
- **Seed data**: When a new tenant is provisioned (or via a migration for existing tenants), create two default tax rates:
  - "Standard" — 15%, isDefault=true, sortOrder=0 (matches SA VAT rate, but the rate is editable)
  - "Zero-rated" — 0%, isDefault=false, sortOrder=1
  - "Exempt" — 0%, isDefault=false, isExempt=true, sortOrder=2
- Publish audit events for tax rate create/update/delete.

### 3. Per-Line Tax on InvoiceLine

**Problem:** InvoiceLine has no tax fields. Tax is a flat amount on the parent invoice with no connection to line items.

**Requirements:**
- Add the following fields to `InvoiceLine`:
  - `UUID taxRateId` — reference to `TaxRate` (nullable for backward compatibility). When null, the line has no tax applied (legacy behavior).
  - `String taxRateName` — denormalized snapshot of the tax rate name at time of invoice creation/edit (so the invoice is self-contained even if the rate is later renamed/deleted). Max 100 characters.
  - `BigDecimal taxRatePercent` — denormalized snapshot of the rate percentage (5,2). Stored so tax can be recalculated from the line without looking up the rate.
  - `BigDecimal taxAmount` — calculated tax for this line (14,2). For tax-exclusive: `amount × taxRatePercent / 100`. For tax-inclusive: `amount - (amount / (1 + taxRatePercent / 100))`.
  - `boolean taxExempt` — denormalized from `TaxRate.isExempt`. Affects display and reporting.
- When a `taxRateId` is set on a line:
  - Snapshot `taxRateName`, `taxRatePercent`, and `taxExempt` from the `TaxRate` entity.
  - Calculate `taxAmount` based on the org's `taxInclusive` setting.
  - The line's `amount` field remains the pre-tax amount (in tax-exclusive mode) or the tax-inclusive amount (in tax-inclusive mode). The `taxAmount` is always the tax portion.
- **Invoice total recalculation** (`Invoice.recalculateTotals()`):
  - `subtotal` = sum of all line `amount` values (unchanged behavior).
  - If any lines have `taxRateId` set: `taxAmount` = sum of all line `taxAmount` values. This replaces the flat manual tax.
  - If no lines have `taxRateId` set: `taxAmount` remains as the manually entered value (backward compatibility).
  - `total` = for tax-exclusive: `subtotal + taxAmount`. For tax-inclusive: `subtotal` (since amounts already include tax, and `taxAmount` is the extracted tax portion for display).
  - **Architect: clarify the total calculation for tax-inclusive mode** — options: (a) `total = subtotal` and `taxAmount` is informational, (b) `total = subtotal` and display shows both the ex-tax subtotal and the inclusive total. Reference how Xero handles this (they show ex-tax subtotal, tax, and inclusive total regardless of mode).

### 4. Tax Application in Invoice Creation & Generation

**Problem:** Invoice lines created manually (via the UI) or generated (from unbilled time / retainer periods) don't apply tax rates.

**Requirements:**

#### 4a. Manual Invoice Line Creation/Edit
- When creating or editing an invoice line via the API, accept an optional `taxRateId` field.
- If `taxRateId` is provided: look up the rate, snapshot fields, calculate tax.
- If `taxRateId` is not provided but the org has a default tax rate: auto-apply the default rate (can be overridden to null/different rate on edit).
- If `taxRateId` is explicitly set to null: no tax on this line (exempt/override).
- Update the `InvoiceLineRequest` DTO to include optional `taxRateId`.

#### 4b. Invoice Generation from Unbilled Time
- `InvoiceService.generateFromUnbilledTime()` currently creates lines with no tax.
- After generating lines, apply the org's default tax rate to all lines (if one exists).
- Recalculate invoice totals with per-line tax.

#### 4c. Invoice Generation from Retainer Period
- `RetainerPeriodService` generates invoices with line items for the period.
- Same behavior: apply default tax rate to generated lines.

#### 4d. Manual Tax Amount Override
- The existing flat `taxAmount` field on the invoice update endpoint must continue to work for backward compatibility.
- **Rule**: If any invoice line has a `taxRateId`, the invoice-level `taxAmount` is read-only (calculated from lines). The API should reject attempts to manually set `taxAmount` when per-line tax is active (return 422 with an explanatory message).
- If no lines have `taxRateId`, the manual `taxAmount` field works as before.

### 5. Invoice Preview, PDF & Portal Display Updates

**Problem:** The invoice HTML preview, PDF, and portal view show a single "Tax" line. They need to show a proper tax breakdown by rate.

**Requirements:**
- **Tax breakdown section** on invoice preview/PDF/portal:
  - Group line-level tax amounts by rate name.
  - Display: `Subtotal: R10,000.00` / `VAT (15%): R1,500.00` / `Zero-rated: R0.00` (only if there are zero-rated lines) / `Total: R11,500.00`.
  - If multiple tax rates are used on the same invoice, show each rate on its own line in the breakdown.
  - Exempt lines are excluded from the tax breakdown (they have no tax row).
  - If the invoice uses legacy flat tax (no per-line rates): show the existing single "Tax" line as before.
- **Tax registration number** on invoice:
  - Display the org's `taxRegistrationNumber` with its `taxRegistrationLabel` (e.g., "VAT Number: 4012345678") in the invoice header, near the org details.
  - Only display if `taxRegistrationNumber` is set.
- **Line item tax column**:
  - Add a "Tax" or "VAT" column to the invoice line items table showing the rate name and percentage (e.g., "VAT 15%") or "Exempt" for exempt lines. Column is omitted if no lines have tax rates (legacy invoices).
- **Tax-inclusive indicator**:
  - If the org uses tax-inclusive pricing, add a note: "All amounts include {taxLabel}" near the totals section.
- Update the `InvoicePreviewService` to include tax breakdown data in the template context.
- Update the portal read-model `PortalInvoiceProjection` to include the tax breakdown (list of rate name + amount pairs) and tax registration number.
- Update the `InvoiceEventHandler` to sync tax breakdown data to the portal read-model.

### 6. Frontend — Tax Rate Management

**Problem:** There is no UI for managing tax rates.

**Requirements:**
- Add a "Tax Rates" section to the Settings page (near or within the existing org settings area).
- Display a table of tax rates with columns: Name, Rate (%), Default (badge), Status (Active/Inactive).
- "Add Tax Rate" button opens a dialog with fields: Name, Rate (%), Is Default (toggle), Is Exempt (toggle).
- Edit and deactivate actions on each row.
- If deactivation is blocked (rate used on draft invoices), show an error toast with the count of affected invoices.
- The default rate has a visual indicator (badge or star icon).
- Setting a new default shows a confirmation: "This will replace {current default} as the default tax rate."

### 7. Frontend — Tax on Invoice Create/Edit

**Problem:** The invoice creation/edit UI has a manual "Tax Amount" input. It needs per-line tax rate selection.

**Requirements:**
- **Line item tax rate selector**: Add a tax rate dropdown to each line item row in the invoice editor. Populated from `GET /api/tax-rates`. Shows rate name and percentage. Default: pre-selected to the org's default rate for new lines.
- **Per-line tax display**: Show the calculated tax amount per line (read-only, calculated from rate × amount).
- **Tax breakdown in totals**: Replace the single "Tax Amount" input with a read-only tax breakdown section (matching the preview layout). Show subtotal, tax by rate, total.
- **Manual tax override**: The old "Tax Amount" input is only shown when no lines have tax rates applied (legacy mode). Once any line has a tax rate, the manual input is hidden and replaced by the calculated breakdown.
- **Tax-inclusive toggle**: If the org has `taxInclusive = true`, show a note in the invoice editor: "Prices include {taxLabel}". The line `unitPrice` field label updates to "Unit Price (inc. {taxLabel})".
- **Remove tax from line**: Clearing the tax rate dropdown on a line removes tax from that line (sets `taxRateId` to null). Useful for exempt items.

### 8. Frontend — Tax Settings in Org Settings

**Problem:** OrgSettings UI has no tax configuration section.

**Requirements:**
- Add a "Tax" card/section to the Settings page with:
  - Tax Registration Number — text input
  - Tax Registration Label — text input with placeholder "e.g., VAT Number, GST Number"
  - Tax Label — text input with placeholder "e.g., VAT, GST, Tax"
  - Tax-Inclusive Pricing — toggle with explanation: "When enabled, all prices entered include tax. Tax will be extracted and shown separately on invoices."
- Save button updates `OrgSettings` via the existing settings API.
- Show a preview of how the tax info will appear on invoices (optional nice-to-have, not required).

### 9. Audit Events

**Requirements:**
- Record audit events for:
  - `tax_rate.created` — tax rate created (details: name, rate, isDefault)
  - `tax_rate.updated` — tax rate modified (details: changed fields)
  - `tax_rate.deactivated` — tax rate deactivated
  - `tax_rate.default_changed` — default tax rate changed (details: from, to)
  - `org_settings.tax_configured` — tax settings updated (details: changed fields)

## Out of Scope

- Multi-jurisdiction tax (different rates for different regions/customers automatically)
- Compound tax / tax-on-tax
- Withholding tax
- Reverse charge VAT (B2B cross-border)
- Tax filing integration (SARS eFiling, MTD, BAS)
- Tax reports (beyond what the existing reporting pipeline provides when given correct tax amounts)
- Customer-level tax exemption certificates
- Tax rounding rules beyond standard banker's rounding (HALF_UP)
- Automated tax rate updates (rate changes require manual update)

## ADR Topics

The architect should produce ADRs for:

1. **Tax calculation strategy** — how tax is calculated and stored. Options: (a) calculate on save and store per-line (chosen approach in this spec, denormalized for performance and audit trail), (b) calculate on read from rate definitions (normalized but recalculation risk if rates change). Consider: what happens when a tax rate percentage is changed — should existing draft invoices be recalculated? Recommend: yes for DRAFT, no for APPROVED/SENT/PAID.
2. **Tax-inclusive total display** — how totals are presented in tax-inclusive mode. Reference Xero's approach (always shows ex-tax subtotal, tax, and inclusive total). Decide: does `Invoice.subtotal` store the ex-tax or inc-tax sum? Does `Invoice.total` equal `subtotal` in inclusive mode or `subtotal + taxAmount`? The choice affects every display and report.
3. **Tax rate immutability on finalized invoices** — how to ensure that changing or deleting a tax rate doesn't affect historical invoices. The spec proposes denormalized snapshots on `InvoiceLine` (`taxRateName`, `taxRatePercent`). Confirm this approach and decide: should the snapshot be taken at line creation time or at invoice approval time? Consider: a user might add lines to a DRAFT invoice over several days while the rate is being updated.

## Style & Boundaries

- The `TaxRate` entity follows the same pattern as `BillingRate` and `CostRate` in the `rate/` package — but lives in a new `tax/` package since tax is conceptually distinct from billing rates.
- The `TaxRateController` follows the existing CRUD controller pattern (see `BillingRateController`).
- Tax calculation logic lives in a `TaxCalculationService` (not inline in `InvoiceService`) — keeps invoice service focused on lifecycle, delegates math to the tax service.
- Frontend components follow existing patterns: settings card for tax config, data table for rate management, dropdown selector on invoice line editor.
- Flyway migration: V40 (next available after Phase 24's V39). Adds columns to `org_settings`, creates `tax_rates` table, adds columns to `invoice_lines`.
- The portal read-model sync (`InvoiceEventHandler`) must be updated to include tax breakdown data — this is a data flow change, not a new entity.
- All monetary calculations use `BigDecimal` with `RoundingMode.HALF_UP` and scale 2 for amounts, scale 2 for percentages.
