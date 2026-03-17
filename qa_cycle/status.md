# QA Cycle Status — 2026-03-17

## Current State

- **QA Position**: Day 0, Checkpoint 0.1 (not started)
- **Cycle**: 0
- **E2E Stack**: Not running
- **Branch**: `bugfix_cycle_2026-03-17`
- **Scenario**: `tasks/phase49-lifecycle-script.md`
- **Test Plan**: `qa/testplan/phase49-document-content-verification.md`
- **Previous Cycle**: Phase 48 (status archived in git history)

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Track | Notes |
|----|---------|----------|--------|-------|----|-------|-------|
| GAP-P49-001 | Template pack has 7 templates, not 8 (test plan doc error) | minor | WONT_FIX | — | — | T1 | Documentation error. T1 sub-tracks correctly list 7. No code change needed. |
| GAP-P49-002 | `company_registration_number` template variable mismatched with field slug `acct_company_registration_number` | blocker | SPEC_READY | Dev | — | T1.1/T5.1 | Fix spec: `qa_cycle/fix-specs/GAP-P49-002.md`. Change template key from `company_registration_number` to `acct_company_registration_number` in `engagement-letter-bookkeeping.json` line 55. Effort: S. |
| GAP-P49-003 | Blank fields produce `________` placeholder instead of hiding the line | minor | WONT_FIX | — | — | T5.6 | Placeholder (`________`) is intentional behavior from `LenientOGNLEvaluator`. Adding conditional blocks to all 7 templates is M effort per template (~3.5h total). QA can observe and document the behavior. The `TemplateValidationService` warns pre-generation. Out of scope for this bugfix cycle; log as future enhancement. |
| GAP-P49-004 | `generatedAt` rendered as raw ISO in non-Tiptap paths | minor | WONT_FIX | — | — | T1.4/T1.6 | Tiptap rendering applies `VariableFormatter.formatDate()` correctly. Only DOCX path affected (T7 is manual). |
| GAP-P49-005 | Portal acceptance page missing — Track T6 entirely blocked | blocker | WONT_FIX | — | — | T6 | New frontend page required. Backend + firm-side UI exist. Effort: L (half day). Out of scope for bugfix cycle. |
| GAP-P49-006 | Project custom field pack `autoApply: false` — fields may not appear on projects | major | SPEC_READY | Dev | — | T0.7 | Fix spec: `qa_cycle/fix-specs/GAP-P49-006.md`. Change `autoApply` from `false` to `true` in `accounting-za-project.json` line 10. Effort: S. |
| GAP-P49-007 | Proposal creation dialog customer combobox broken | major | WONT_FIX | — | — | T3.1 | Already fixed in PR #720 (Phase 48 GAP-P48-012). `type="button"` present at line 192 of `create-proposal-dialog.tsx`. Gap report was based on stale analysis. Verify at runtime during T3.1. |
| GAP-P49-008 | Proposal send + email delivery needs runtime verification | major | WONT_FIX | — | — | T3.4 | Backend and firm-side UI exist. Needs runtime check, no code change anticipated. |
| GAP-P49-009 | Info request template pre-population needs runtime verification | major | WONT_FIX | — | — | T4.1 | `create-request-dialog.tsx` and `year-end-info-request-za.json` template exist. Likely works. |
| GAP-P49-010 | Info request firm-side accept/reject flow | cosmetic | WONT_FIX | — | — | T4.4 | Feature appears complete per code review. Not a gap. |
| GAP-P49-011 | `customerVatNumber` mapping confirmed correct in InvoiceContextBuilder | minor | WONT_FIX | — | — | T5.5 | Not a gap. `vat_number` → `customerVatNumber` alias confirmed in code. Observation only. |
| GAP-P49-012 | DOCX-to-PDF conversion — LibreOffice not in E2E Docker stack | blocker | WONT_FIX | — | — | T7 | Track 7 is manual testing by founder. DOCX merge works; PDF conversion expected to fail gracefully. |

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
