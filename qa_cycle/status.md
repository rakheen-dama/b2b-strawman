# QA Cycle Status — Document Content Verification / Keycloak Dev Stack (2026-03-24)

## Current State

- **QA Position**: T5.6 COMPLETE — Cycle 1 done. Next: T3/T4/T6 require 90-day seed data
- **Cycle**: 1
- **Dev Stack**: READY — All 5 services running (Backend:8080, Frontend:3000, Gateway:8443, Keycloak:8180, Mailpit:8025)
- **Branch**: `bugfix_cycle_doc_verify_2026-03-24`
- **Scenario**: `qa/testplan/phase49-document-content-verification.md`
- **Focus**: Document content verification against Keycloak dev stack
- **Auth Mode**: Keycloak (not mock-auth). Login via Keycloak redirect flow.
- **Results**: `qa_cycle/checkpoint-results/doc-verify-cycle1.md`

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend | http://localhost:3000 | UP |
| Backend | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit | http://localhost:8025 | UP |

## Existing Data

- **Org**: "Thornton & Associates" (alias=thornton-associates, schema=tenant_4a171ca30392)
- **Org**: "QA Verify Corp" (alias=qa-verify-corp, schema=tenant_62aa7c96ab38)
- **Users**: padmin@docteams.local (platform-admin), thandi@thornton-test.local (owner), bob@thornton-test.local (member), qatest@thornton-verify.local (owner of QA Verify Corp)
- **Customers**: Naledi Corp QA (ACTIVE, fully onboarded, 14 custom fields set), Kgosi Holdings QA Cycle2 (OFFBOARDED, 15 custom fields set), Lifecycle Chain C4 (OFFBOARDED)
- **Invoices**: INV-0001 (Naledi Corp QA, APPROVED, R8,050 = R7,000 subtotal + R1,050 VAT)
- **Templates**: 13 templates active (7 PLATFORM accounting-za, 1 custom clone, 5 generic PLATFORM)
- All passwords: `password`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-P49-001 | SARS Tax Reference blank in Tax Return template | major | OPEN | backend | — | T1.2 | customer.customFields.sars_tax_reference not flowing into PROJECT-scoped template context |
| GAP-P49-002 | Statement of Account invoice table empty | major | OPEN | backend | — | T1.6 | CustomerContextBuilder not populating invoice list despite APPROVED invoice existing |
| GAP-P49-003 | FICA verification date blank despite being populated | major | OPEN | backend | — | T1.7, T5.2 | fica_verification_date (DATE type) not mapped in CustomerContextBuilder |
| GAP-P49-004 | No pre-generation warning for missing custom fields | minor | OPEN | backend | — | T5.6 | validationResult.allPresent=true even when template fields have blank values |
| GAP-P49-005 | Blank field produces dangling label | cosmetic | OPEN | backend/template | — | T5.6 | "Registration Number: " with blank value, no "N/A" fallback or conditional hide |

## Results Summary

| Track | ID | Test | Result | Evidence |
|-------|-----|------|--------|----------|
| T0 | T0.6 | Org Settings (currency, tax, branding, footer) | PASS | All values saved and verified via API |
| T0 | T0.7 | Project Custom Fields | PASS | engagement_type and tax_year set via UI |
| T1 | T1.1 | Engagement Letter: Monthly Bookkeeping | PASS | All variables resolve, 7 clauses present, PDF valid |
| T1 | T1.2 | Engagement Letter: Annual Tax Return | PARTIAL | org.name + customer.name PASS, SARS ref BLANK (GAP-001) |
| T1 | T1.3 | Engagement Letter: Advisory | PASS | All variables resolve, 4 clauses present |
| T1 | T1.4 | Monthly Report Cover | PASS | Date "23 March 2026", all fields resolve |
| T1 | T1.5 | SA Tax Invoice | PASS | Perfect: math correct, VAT calc, ZAR format, line items |
| T1 | T1.6 | Statement of Account | PARTIAL | Header/customer PASS, invoice table EMPTY (GAP-002) |
| T1 | T1.7 | FICA Confirmation Letter | PARTIAL | org+customer PASS, verification date BLANK (GAP-003) |
| T1 | T1.8 | Cross-Customer Isolation | PASS | No data leakage between Naledi and Kgosi |
| T2 | T2.1 | Default Clause Inclusion (7 clauses) | PASS | 4 required + 3 optional all present |
| T2 | T2.2 | Clause Variable Resolution | PASS | org.name and customer.name resolve in all clause bodies |
| T2 | T2.3 | Clause Selection UI | PASS | Required=locked, optional=toggleable, reorder buttons shown |
| T5 | T5.1 | Custom Field -> Document Flow | PASS | company_registration_number flows correctly after being set |
| T5 | T5.2 | FICA Verification Date Flow | FAIL | Field populated but not rendered (GAP-003) |
| T5 | T5.5 | Invoice Custom Field Flow | PASS | customerVatNumber + org.taxRegistrationNumber both resolve |
| T5 | T5.6 | Missing Field Behaviour | FAIL | No warning (GAP-004), dangling labels (GAP-005) |

## Scorecard

| Track | Tested | Pass | Fail | Partial | Skipped |
|-------|--------|------|------|---------|---------|
| T0 Data Prep | 17 | 14 | 0 | 0 | 3 |
| T1 Template Fidelity | 50 | 42 | 5 | 1 | 2 |
| T2 Clause Assembly | 12 | 8 | 0 | 0 | 4 |
| T5 Custom Field Flow | 14 | 9 | 3 | 1 | 1 |
| T3 Engagement Pipeline | — | — | — | — | SKIPPED |
| T4 Info Request Loop | — | — | — | — | SKIPPED |
| T6 E-Signing | — | — | — | — | SKIPPED |
| **Total** | **93** | **73** | **8** | **2** | **10** |

**Pass rate**: 73/83 tested = **88%** | **0 blockers** | 3 major | 1 minor | 1 cosmetic

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-24T00:00Z | Setup | Document content verification QA cycle initialized on branch bugfix_cycle_doc_verify_2026-03-24. Scenario: qa/testplan/phase49-document-content-verification.md. All services UP. Existing org: Thornton & Associates with ACTIVE customer (Naledi Corp QA). |
| 2026-03-24T22:40Z | QA Agent | Cycle 1 started. Keycloak login successful (auto-redirect to dashboard). API token obtained. |
| 2026-03-24T22:41Z | QA Agent | T0.6: Org settings configured — tax reg (4510067890), brand color (#1B5E20), footer text set. Verified via API. |
| 2026-03-24T22:47Z | QA Agent | T0.2-T0.3: Custom fields populated on Naledi (14 fields) and Kgosi (15 fields) via API PUT. |
| 2026-03-24T22:48Z | QA Agent | Invoice INV-0001 created for Naledi (2 line items, R7,000+R1,050 VAT=R8,050). Approved. |
| 2026-03-24T22:43Z | QA Agent | T1.1: Engagement letter (monthly bookkeeping) — all variables resolve, 7 clauses present, no {{. PDF valid (5,619 bytes). |
| 2026-03-24T22:44Z | QA Agent | T1.2-T1.4: Tax return, advisory, monthly report cover — all generate. SARS ref blank in tax return (GAP-001). |
| 2026-03-24T22:46Z | QA Agent | T1.5: SA Tax Invoice — perfect output. Math verified: 5500+1500=7000, 15%=1050, total=8050. |
| 2026-03-24T22:46Z | QA Agent | T1.6-T1.7: Statement of account (invoice table empty, GAP-002), FICA confirmation (date blank, GAP-003). |
| 2026-03-24T22:47Z | QA Agent | T1.8: Cross-customer isolation — PASS. No data leakage between Naledi and Kgosi. |
| 2026-03-24T22:50Z | QA Agent | T2.1-T2.3: Clause assembly via UI — 7 clauses shown in Step 1 dialog (4 required locked, 3 optional toggleable). All variables resolved in clause bodies. |
| 2026-03-24T22:50Z | QA Agent | T5.1-T5.6: Custom field flow tested. company_registration_number flows correctly. fica_verification_date does NOT flow (GAP-003). Missing field behavior: no warning, dangling labels (GAP-004, GAP-005). |
| 2026-03-24T22:55Z | QA Agent | Cycle 1 complete. 5 gaps logged (3 major, 1 minor, 1 cosmetic). 88% pass rate on tested items. T3/T4/T6 skipped (require 90-day seed data). |
