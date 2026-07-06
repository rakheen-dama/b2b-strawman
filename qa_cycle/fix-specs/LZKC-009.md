# Fix Spec: LZKC-009 — Firm-side "Invoice" terminology leaks (5 sites, split disposition)

## Problem
Day 28 + Day 90: firm-side "Invoice" leaks despite legal-za substitution: (1) document preview header "Invoice: DRAFT"; (2) send-validation copy "…required to send an invoice"; (3) Generate Document menu shows "Invoice Cover Letter"; (4) audit-trail entity facet "Invoice"; (5) Create Client dialog "(required to send an invoice; collectable later)".

## Root Cause (verified — per site)
1. Preview header: `backend/src/main/resources/templates/invoice-preview.html:238-240` (`'Invoice: ' + (invoiceNumber != null ? invoiceNumber : 'DRAFT')`, also `<title>` line 6); rendered by `invoice/InvoiceRenderingService.java` with a Thymeleaf Context carrying no terminology.
2. Validation copy: `invoice/InvoiceValidationService.java:114` ("Tax Number is required to send an invoice") and `prerequisite/StructuralPrerequisiteCheck.java:187` (`displayName() + " is required to send an invoice"`). No terminology in validation strings.
3. Template display name: seed data `template-packs/common/pack.json:28-29` ("Invoice Cover Letter") + heading in `common/invoice-cover-letter.json:15`; rendered raw from DB rows at `frontend/components/templates/GenerateDocumentDropdown.tsx:128`. Common pack serves ALL verticals — renaming is a per-vertical data/pack design question.
4. Audit facet: `audit/DatabaseAuditService.java:533` — `titleCase(entity_type)` → "Invoice"; no terminology mapping in facet building.
5. Create Client helper: `frontend/components/customers/create-customer-dialog.tsx:405` — component ALREADY uses `useTerminology`; string just wasn't wired.

Backend terminology infrastructure that CAN be reused: `notification/template/EmailTerminology.java` (static per-profile noun map, `forProfile(...)`).

## Fix (split — SPEC_READY portion = sites 5, 2, 1)
- **Site 5 (S)**: `create-customer-dialog.tsx:405` → interpolate `t("invoice")` → "(required to send a fee note; collectable later)". Watch the article ("an invoice" → "a fee note"): make the article part of the interpolated copy, not hardcoded.
- **Site 2 (S-M)**: resolve the invoice term in `InvoiceValidationService` / `StructuralPrerequisiteCheck` via `EmailTerminology.forProfile(orgSettings.getVerticalProfile())` (or a small shared TermResolver) and build "…required to send a fee note". Both messages come from the send-validation path where OrgSettings is reachable.
- **Site 1 (S-M)**: `InvoiceRenderingService` passes `invoiceTerm` into the Thymeleaf context; `invoice-preview.html:6,238-240` uses it ("Fee Note: DRAFT").

## Deferred portion (orchestrator decision required)
- **Site 3 (DEFERRED-PROPOSED)**: renaming the seeded "Invoice Cover Letter" template per vertical is a template-pack data design question (common pack is shared; needs a legal-za override or display-name terminology layer) — and may be superseded by LZKC-012, which introduces a proper legal-za fee-note document as the client-facing artefact. Defer until LZKC-012's fix lands.
- **Site 4 (DEFERRED-PROPOSED)**: terminology-aware audit entity facets is a cross-cutting audit-plane change (facets are built from free-string entity types for ALL entities); a one-off "invoice"→"Fee Note" special case in `DatabaseAuditService.titleCase` would be a hack. Propose folding into a broader "terminology in audit/reporting surfaces" item.

## Scope
Both (sites 1-2 backend, site 5 frontend)
Files to modify: `create-customer-dialog.tsx`, `InvoiceValidationService.java`, `StructuralPrerequisiteCheck.java`, `InvoiceRenderingService.java`, `invoice-preview.html`
Files to create: none
Migration needed: no

## Verification
Legal-za tenant: Create Client dialog says "fee note"; Send validation panel says "…send a fee note"; invoice preview header reads "Fee Note: DRAFT" (or "Fee Note: INV-000x"). Non-legal tenant unchanged ("invoice"). Note: LZKC-008 also edits `InvoiceValidationService.checkCustomerTaxNumber` — coordinate the two fixes (same file, compatible changes).

## Estimated Effort
M (30 min – 2 hr) for the SPEC_READY portion

## Cluster members
Single gap ID, but internally split: sites 1/2/5 SPEC_READY, sites 3/4 DEFERRED-PROPOSED (need orchestrator decision).
