# Document Content Verification — Cycle 2 Fix Verification

**Date**: 2026-03-24
**Branch**: `bugfix_cycle_doc_verify_2026-03-24`
**Agent**: QA Agent
**Focus**: Re-test 4 FIXED gaps from Cycle 1 (PRs #830, #831)

---

## Summary

| GAP ID | Summary | Severity | Cycle 1 | Cycle 2 | Verdict |
|--------|---------|----------|---------|---------|---------|
| GAP-P49-001 | SARS Tax Reference blank in Tax Return template | major | FAIL | PASS | VERIFIED |
| GAP-P49-002 | Statement of Account invoice table empty | major | FAIL | PASS | VERIFIED |
| GAP-P49-003 | FICA verification date blank | major | FAIL | PASS | VERIFIED |
| GAP-P49-005 | Blank field dangling labels | cosmetic | FAIL | PASS | VERIFIED |

**Result**: 4/4 FIXED gaps verified. All pass.

---

## GAP-P49-001: SARS Tax Reference in Tax Return Template

**Template**: `engagement-letter-annual-tax-return` (f7dafe8f)
**Entity**: Project "Annual Tax Return 2026 Updated" (236ce695) linked to Kgosi Holdings QA Cycle2
**Fix**: PR #830 (debug logging in ProjectContextBuilder, data confirmed present in DB)

### Pre-conditions verified
- Kgosi Holdings QA Cycle2 custom fields: `sars_tax_reference=9012345678` (confirmed via GET /api/customers/7ef4e736)
- All 15 custom fields present on customer

### Test execution
- `POST /api/templates/f7dafe8f/preview` with `entityId=236ce695`
- Response: 5788 chars HTML

### Result: PASS
- **SARS Tax Reference: 9012345678** appears in rendered output
- Context: `Client: Kgosi Holdings QA Cycle2 SARS Tax Reference: 9012345678 Scope of Work`
- Customer name resolves correctly
- Org name resolves correctly

### Note on conditionalBlock update
The template content in the database was stale (did not include `conditionalBlock` wrappers from PR #831). After updating the template via PUT with the pack JSON content, the SARS Tax Reference still renders correctly and is now also conditionally hidden when the field is blank.

---

## GAP-P49-002: Statement of Account Invoice Table

**Template**: `statement-of-account` (454a4bb5)
**Entity**: Customer "Naledi Corp QA" (4160e3cb)
**Fix**: PR #830 (`invoices` LoopSource registered in VariableMetadataRegistry, invoice query logging added)

### Pre-conditions verified
- Invoice INV-0001 exists for Naledi Corp QA (APPROVED, subtotal=R7,000, tax=R1,050, total=R8,050)

### Test execution
- `POST /api/templates/454a4bb5/preview` with `entityId=4160e3cb`
- Response: 1960 chars HTML

### Result: PASS
- **INV-0001** appears in invoice table
- Table has 2 rows (header + 1 data row)
- Invoice data: `INV-0001 | 24 March 2026 | 23 April 2026 | R8,050.00 | ZAR | APPROVED | R8,050.00`
- **Total Outstanding: R8,050.00** renders correctly at bottom
- Currency formatting correct (R with non-breaking space: `R8&nbsp;050,00`)

---

## GAP-P49-003: FICA Verification Date

**Template**: `fica-confirmation-letter` (b92886e9)
**Entity**: Customer "Naledi Corp QA" (4160e3cb)
**Fix**: PR #830 (debug logging in CustomerContextBuilder, data confirmed present in DB)

### Pre-conditions verified
- Naledi Corp QA custom fields: `fica_verification_date=2026-01-16` (confirmed via GET /api/customers/4160e3cb)
- All 14 custom fields present on customer

### Test execution
- `POST /api/templates/b92886e9/preview` with `entityId=4160e3cb`
- Response: 2147 chars HTML

### Result: PASS
- **Verification Date: 16 January 2026** appears in rendered output
- Date formatted correctly (raw `2026-01-16` converted to `16 January 2026`)
- Context: `Client: Naledi Corp QA Verification Date: 16 January 2026 Documents Verified`

---

## GAP-P49-005: Blank Field Dangling Labels

**Templates**: `fica-confirmation-letter`, `engagement-letter-annual-tax-return`, `engagement-letter-monthly-bookkeeping`
**Fix**: PR #831 (6 optional custom field paragraphs wrapped in `conditionalBlock` with `isNotEmpty` operator)

### Important discovery
The template content in the database was NOT automatically updated by the backend restart. PLATFORM templates are seeded once during initial provisioning and are not refreshed from pack JSONs on subsequent restarts. The `conditionalBlock` wrappers from PR #831 were present in the pack JSON files on disk but not in the database templates.

**Resolution**: Updated all 3 templates via `PUT /api/templates/{id}` with the fixed content from the pack JSONs. All 3 returned HTTP 200.

### Test: FICA Confirmation for customer with NO custom fields

**Entity**: Lifecycle Chain C4 (7290f4ee) — 0 custom fields set

**Before update (stale DB content)**:
- "Verification Date:" appeared as dangling label with no value after it

**After update (with conditionalBlock)**:
- "Verification Date:" label is completely hidden
- Naledi (all fields) HTML: 2147 chars
- C4 (no fields) HTML: 2101 chars (46 chars smaller — the hidden label+value)

### Test: Tax Return for Kgosi (mixed — some fields present, some absent)

**Entity**: Project "Annual Tax Return 2026 Updated" (236ce695) — Kgosi customer has `sars_tax_reference` but project has no `tax_year` or `sars_submission_deadline`

**Result**:
- **SARS Tax Reference: 9012345678** — SHOWN (customer has the field) - correct
- **Tax Year:** — HIDDEN (project has no `tax_year` custom field) - correct
- **SARS Submission Deadline:** — HIDDEN (project has no `sars_submission_deadline`) - correct

### Result: PASS
All `conditionalBlock` wrappers work correctly. Labels are hidden when the referenced field is empty/missing and shown when the field has a value.

### Deployment note
PLATFORM template content is NOT auto-refreshed from pack JSONs on restart. When pack JSONs are updated (as in PR #831), existing tenant template records must be updated via one of:
1. Manual PUT to the template API (as done in this verification)
2. A migration script that updates template content from pack JSONs
3. Enhancement to the template pack seeder to detect and apply content changes on startup

This should be tracked as a follow-up item.

---

## Updated Scorecard (Cycle 2)

| Track | Tested | Pass | Fail | Partial | Skipped |
|-------|--------|------|------|---------|---------|
| T0 Data Prep | 17 | 14 | 0 | 0 | 3 |
| T1 Template Fidelity | 50 | 47 | 0 | 1 | 2 |
| T2 Clause Assembly | 12 | 8 | 0 | 0 | 4 |
| T5 Custom Field Flow | 14 | 13 | 0 | 0 | 1 |
| T3 Engagement Pipeline | -- | -- | -- | -- | SKIPPED |
| T4 Info Request Loop | -- | -- | -- | -- | SKIPPED |
| T6 E-Signing | -- | -- | -- | -- | SKIPPED |
| **Total** | **93** | **82** | **0** | **1** | **10** |

**Pass rate**: 82/83 tested = **99%** | **0 blockers** | 0 major | 1 minor (GAP-P49-004 DEFERRED) | 0 cosmetic

---

## Verification Method
All tests executed via direct API calls (`curl`) to the backend at `http://localhost:8080`. Auth via Keycloak token (user: thandi@thornton-test.local, org: thornton-associates). Template previews generated via `POST /api/templates/{id}/preview` with entity IDs. HTML output parsed and inspected programmatically.
