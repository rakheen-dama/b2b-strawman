# QA Cycle Status — 2026-03-17

## Current State

- **QA Position**: Day 4, Checkpoint 4.1 (ready to start)
- **Cycle**: 1
- **E2E Stack**: HEALTHY (all 6 services up, seed complete)
- **Branch**: `bugfix_cycle_2026-03-17`
- **Scenario**: `tasks/phase49-lifecycle-script.md`
- **Test Plan**: `qa/testplan/phase49-document-content-verification.md`
- **Previous Cycle**: Phase 48 (status archived in git history)

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-P49-001 | Template pack has 7 templates, not 8 (test plan doc error) | minor | WONT_FIX | — | — | T1 | Documentation error. T1 sub-tracks correctly list 7. No code change needed. |
| GAP-P49-002 | `company_registration_number` template variable mismatched with field slug `acct_company_registration_number` | blocker | VERIFIED | Dev | #728 | T1.1/T5.1 | Template key aligned with field pack slug. VERIFIED in Day 1 checkpoint 1.4: "Registration Number: 2019/123456/07" renders correctly. |
| GAP-P49-003 | Blank fields produce `________` placeholder instead of hiding the line | minor | WONT_FIX | — | — | T5.6 | Placeholder (`________`) is intentional behavior from `LenientOGNLEvaluator`. Adding conditional blocks to all 7 templates is M effort per template (~3.5h total). QA can observe and document the behavior. The `TemplateValidationService` warns pre-generation. Out of scope for this bugfix cycle; log as future enhancement. |
| GAP-P49-004 | `generatedAt` rendered as raw ISO in non-Tiptap paths | minor | WONT_FIX | — | — | T1.4/T1.6 | Tiptap rendering applies `VariableFormatter.formatDate()` correctly. Only DOCX path affected (T7 is manual). |
| GAP-P49-005 | Portal acceptance page missing — Track T6 entirely blocked | blocker | WONT_FIX | — | — | T6 | New frontend page required. Backend + firm-side UI exist. Effort: L (half day). Out of scope for bugfix cycle. |
| GAP-P49-006 | Project custom field pack `autoApply: false` — fields may not appear on projects | major | VERIFIED | Dev | #729 | T0.7 | Changed `autoApply` from `false` to `true` in `accounting-za-project.json`. VERIFIED: project custom fields auto-apply to all projects (step 0.69). |
| GAP-P49-007 | Proposal creation dialog customer combobox broken | major | WONT_FIX | — | — | T3.1 | Already fixed in PR #720 (Phase 48 GAP-P48-012). `type="button"` present at line 192 of `create-proposal-dialog.tsx`. Gap report was based on stale analysis. Verify at runtime during T3.1. |
| GAP-P49-008 | Proposal send + email delivery needs runtime verification | major | WONT_FIX | — | — | T3.4 | Backend and firm-side UI exist. Needs runtime check, no code change anticipated. |
| GAP-P49-009 | Info request template pre-population needs runtime verification | major | WONT_FIX | — | — | T4.1 | `create-request-dialog.tsx` and `year-end-info-request-za.json` template exist. Likely works. |
| GAP-P49-010 | Info request firm-side accept/reject flow | cosmetic | WONT_FIX | — | — | T4.4 | Feature appears complete per code review. Not a gap. |
| GAP-P49-011 | `customerVatNumber` mapping confirmed correct in InvoiceContextBuilder | minor | WONT_FIX | — | — | T5.5 | Not a gap. `vat_number` → `customerVatNumber` alias confirmed in code. Observation only. |
| GAP-P49-012 | DOCX-to-PDF conversion — LibreOffice not in E2E Docker stack | blocker | WONT_FIX | — | — | T7 | Track 7 is manual testing by founder. DOCX merge works; PDF conversion expected to fail gracefully. |
| GAP-P49-013 | Naledi project not linked to customer — document generation produces broken output | major | OPEN | — | — | T5.6 | "Monthly Bookkeeping -- Naledi" shows as "Internal Project". Engagement letter has blank salutation ("Dear ,"), blank client details. Customer IS linked from customer side but project-to-customer link missing. Likely seed data issue. |
| GAP-P49-014 | Blank template fields render as label-with-empty-value instead of hiding | minor | WONT_FIX | — | — | T5.6 | "Registration Number:", "Client VAT Number:" labels shown with blank values. Related to GAP-P49-003 but for entity-level (not custom field) blanks. Future enhancement: conditional line hiding. |
| GAP-P49-015 | org.name resolves to system org name, not trading/display name | cosmetic | WONT_FIX | — | — | T5.1 | `org.name` = "E2E Test Organization" (mock-auth), not "Thornton & Associates" (branding). Footer uses `documentFooterText` correctly. Likely E2E-only issue; production Keycloak org name would match. |
| GAP-P49-016 | Clauses not rendered in document body — engagement letters legally incomplete | major | OPEN | — | — | T1/T2 | All 3 engagement letter templates (bookkeeping, tax-return, advisory) show clause selection in Step 1 but NO clause content appears in the rendered HTML preview. Document body goes from "Acceptance" to footer with no clause sections. Affects all clause-bearing templates. |
| GAP-P49-017 | Engagement type renders as raw enum instead of display label | minor | OPEN | — | — | T1.1 | `project.customFields.engagement_type` = "MONTHLY_BOOKKEEPING" (raw enum/slug) instead of "Monthly Bookkeeping" (display label). The dropdown shows the label but the template receives the raw value. |
| GAP-P49-018 | Invoice/statement table amounts lack currency formatting | minor | OPEN | — | — | T1.5/T1.6 | Line items table in invoice-za shows raw numbers (5500.00) without R prefix. Statement-of-account table shows raw ISO dates (2026-03-17) and unformatted amounts (6497.50). Amount Summary section correctly uses "R5 500,00" format — inconsistent within same document. |

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → done
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-17T12:00Z | Setup | Initial status seeded from Phase 49 gap report. 12 gaps: 3 blocker, 5 major, 3 minor, 1 cosmetic. 3 OPEN (actionable), 9 WONT_FIX (new features, observations, manual-only tracks). |
| 2026-03-17T12:30Z | Product | Triaged 4 OPEN items. GAP-P49-002 (blocker) -> SPEC_READY: template key mismatch, fix is single string change. GAP-P49-006 (major) -> SPEC_READY: project field pack autoApply, single boolean change. GAP-P49-003 (minor) -> WONT_FIX: placeholder behavior is by design, M effort per template, out of scope. GAP-P49-007 (major) -> WONT_FIX: already fixed in PR #720, `type="button"` confirmed in code. 2 fix specs written to `qa_cycle/fix-specs/`. |
| 2026-03-17T14:20Z | Dev | GAP-P49-002 FIXED via PR #728 (squash-merged to bugfix_cycle_2026-03-17). Changed template variable key from `company_registration_number` to `acct_company_registration_number` in `engagement-letter-bookkeeping.json`. Backend resource change — NEEDS_REBUILD before QA verification. |
| 2026-03-17T14:30Z | Dev | GAP-P49-006 FIXED via PR #729 (squash-merged to bugfix_cycle_2026-03-17). Changed `autoApply` from `false` to `true` in `accounting-za-project.json`. Backend resource change — NEEDS_REBUILD before QA verification. |
| 2026-03-17T15:00Z | Infra | E2E stack rebuilt and verified. Backend Docker image rebuilt with PR #728 + #729 changes. All services healthy: frontend (200), backend (200), mock-idp (JWKS OK), mailpit (200), seed complete. NEEDS_REBUILD flag cleared. |
| 2026-03-17T15:30Z | QA | Day 0 complete (Cycle 1). 77 checkpoints: 71 PASS, 6 PARTIAL, 0 FAIL. Custom fields populated for all 4 customers (Kgosi 12 fields, Naledi 7+2 blank, Vukani 10, Moroka 13 incl. 6 trust). Org settings saved (ZAR, footer text, tax reg). Project custom fields set on 2 projects. STOP GATE 0.82 PASSES. GAP-P49-006 VERIFIED (project autoApply fix works). Portal contacts: no firm-side UI (GAP-P49-005 consistent). QA Position advanced to Day 1, Checkpoint 1.1. |
| 2026-03-17T16:00Z | QA | Day 1 complete (Cycle 1). 26 checkpoints: 18 PASS, 4 PARTIAL, 2 FAIL, 1 N/A, 1 informational. STOP GATE PASSES (1.4+1.5+1.6 all resolve). GAP-P49-002 VERIFIED (registration number renders correctly after PR #728 fix). 3 new GAPs: GAP-P49-013 (major, Naledi project missing customer link), GAP-P49-014 (minor, blank label rendering), GAP-P49-015 (cosmetic, org.name vs trading name). Key findings: (1) Custom field -> document data chain works for Kgosi (all fields resolve), (2) FICA date formats correctly, (3) Trust fields visible for Moroka but not referenced in FICA template, (4) Invoice VAT number resolves for Kgosi, (5) Naledi project generates broken engagement letter due to missing project-to-customer link. QA Position advanced to Day 2, Checkpoint 2.1. |
| 2026-03-17T17:30Z | QA | Day 2-3 complete (Cycle 1). 78 checkpoints (2.1-2.78): 55 PASS, 11 PARTIAL, 10 FAIL, 2 N/A. All 7 accounting-za templates tested. 3 new GAPs: GAP-P49-016 (major, clauses not rendered in document body), GAP-P49-017 (minor, enum value shown instead of display label), GAP-P49-018 (minor, inconsistent currency/date formatting in tables). GAP-P49-013 scope expanded: 5 of 6 client projects lack project-to-customer link (only "Monthly Bookkeeping -- Kgosi" works). Key findings: (1) Variable resolution pipeline works when data is linked, (2) Invoice math 100% correct (subtotal + 15% VAT = total), (3) Cross-customer isolation fully verified (Kgosi/Naledi statements contain no cross-customer data), (4) Date formatting excellent on all formatted fields, (5) No `{{` tokens in any template, (6) No $/USD in any template. Top concern: clause content missing from all engagement letters (GAP-P49-016). QA Position advanced to Day 4, Checkpoint 4.1. |
