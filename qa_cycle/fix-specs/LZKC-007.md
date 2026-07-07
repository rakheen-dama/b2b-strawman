# Fix Spec: LZKC-007 + LZKC-017 (cluster) — Generated documents not client-ready (letterhead / banking / locale)

## Problem
Day 28 / 28.5 (LZKC-007, Medium): rendered fee-note document and cover letter have no letterhead logo (branding logo exists since Day 1), no firm details, no banking details, no matter reference code; preview header "Invoice: DRAFT".
Day 61 / 61.4 (LZKC-017, Low): SoA PDF — "VAT Reg:" blank, "Payment Instructions" section empty, no letterhead/contact block, INV-0001 not referenced, mixed number locales ("R50 000,00" vs "1250.00").

## Root Cause (verified — per symptom)
- **Banking details ("Payment Instructions" empty):** template `template-packs/legal-za/statement-of-account.json:286` binds the body to `org.bankingDetails`; `verticals/legal/statement/StatementOfAccountContextBuilder.java:194` hardcodes `orgMap.putIfAbsent("bankingDetails", "")`. **No banking-details field exists anywhere in OrgSettings** (`settings/BrandingSettings.java:23-30` holds only logoS3Key/brandColor/documentFooterText; repo-wide grep for bank fields in settings/ is empty). Empty by construction.
- **Letterhead logo:** `template/TemplateContextHelper.buildOrgContext` (lines 96-112) already presigns `org.logoUrl` from branding — but **no template references it** (cover letter, closure letter, SoA, invoice-za all render only `org.name` as `<h1>`). Data available, templates never emit it.
- **Firm contact details:** not in the org context at all — `buildOrgContext` sets only name, defaultCurrency, brandColor, documentFooterText, taxRegistrationNumber, logoUrl. No org address/phone fields exist in OrgSettings.
- **"VAT Reg:" blank:** wired correctly (`TemplateContextHelper.java:94` ← `settings.getTax().getTaxRegistrationNumber()`); blank because the QA tenant never set a tax registration number. **Data, not defect** for this sub-symptom.
- **Mixed number locale:** two render paths in `template/TiptapRenderer` — `renderLoopTable` (line 315) uses per-column `format` attrs → ZA currency "R50 000,00"; standalone variables go through `resolveVariable` (lines 286-288) whose type hints come from `formatHints`, and `StatementService.renderHtml` passes an **empty map** (`StatementService.java:365`), bypassing `PdfRenderingService.buildFormatHints` (`PdfRenderingService.java:200-209`) → plain `String.valueOf(BigDecimal)` → "1250.00". (`StatementOfAccountContextBuilder` also never calls `populateLocale`.)
- **"Invoice: DRAFT" header:** `templates/invoice-preview.html:238-240` — terminology part handled in LZKC-009 site 1; "DRAFT" itself is correct behaviour for an unsent draft.
- **Matter reference on fee note / INV-0001 on SoA:** template content gaps — fold the matter-reference into LZKC-012's new fee-note template; SoA fee-note itemisation is a template/product content decision (deferred portion).

## Fix — split disposition

### Part 1 — SPEC_READY now (quick, mechanical)
1. **Locale consistency (S):** `StatementService.renderHtml` (`StatementService.java:365`) — pass real format hints (reuse `PdfRenderingService.buildFormatHints` or equivalent hints for `summary.*`, `fees.*`, `disbursements.total`, `trust.*`) and have `StatementOfAccountContextBuilder` set `_locale`, so standalone amounts render "R1 250,00" like the loop tables.
2. **Letterhead logo (S-M):** add an `org.logoUrl` `<img>` node (with graceful absent-logo handling — `TiptapRenderer` behaviour for empty src must be checked) to the four generated-doc templates: `common/invoice-cover-letter.json`, `legal-za/matter-closure-letter.json`, `legal-za/statement-of-account.json`, and the new `legal-za/fee-note-za.json` from LZKC-012. Same existing-tenant seeding caveat as LZKC-010/012.

### Part 2 — DEFERRED-PROPOSED (epic: "client-ready document letterhead & payment details")
- Banking details: new OrgSettings fields/embeddable group (+ Flyway migration + `OrgSettingsSchemaSnapshotTest` pin update per backend/CLAUDE.md), a Settings UI for the firm to enter them, `buildOrgContext` exposure, and template bindings. This is a feature (data model + UI + docs), not a bug fix — needs product decisions (field shape, which docs show it).
- Firm contact block (address/phone/email on letterhead): same dependency on new OrgSettings fields + Settings UI.
- SoA itemising fee notes by reference (INV-0001 line): template/content design decision.

### NOT-A-DEFECT note
"VAT Reg:" blank is unset tenant data — scenario should have the firm enter its VAT number in Settings (Day 1) or accept blank; flagging for orchestrator (scenario amendment = product decision, not self-authorized).

## Scope
Backend only (Part 1)
Files to modify: `StatementService.java`, `StatementOfAccountContextBuilder.java`, 3-4 template-pack JSONs
Files to create: none (Part 1)
Migration needed: maybe (existing-tenant template refresh, shared with LZKC-010/012)

## Verification
Regenerate SoA on a scratch closed matter: all amounts in ZA locale; logo renders at top of SoA/closure letter/cover letter/fee note. Part 2 verification deferred with the epic.

## Estimated Effort
Part 1: M. Part 2: L (> 2 hr) — DEFERRED-PROPOSED.

## Cluster members
LZKC-007 + LZKC-017 — same defect family: generated-document rendering lacks org letterhead/payment data and consistent formatting; single pipeline (`TiptapRenderer`/`TemplateContextHelper`/template packs). Cluster PR needs orchestrator authorization per CLAUDE.md §7.
