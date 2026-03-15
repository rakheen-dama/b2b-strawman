# QA Cycle Status — 2026-03-15

## Current State

- **QA Position**: Day 1, Checkpoint 1.4 (BLOCKED by GAP-027)
- **Cycle**: 1
- **E2E Stack**: Running
- **Branch**: `bugfix_cycle_2026-03-15`
- **Scenario**: `tasks/phase47-lifecycle-script.md`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
| GAP-008 | Accounting template pack not seeded (only 3 generic templates, missing 7 accounting-specific) | blocker | VERIFIED | Infra | — | 0 | Fixed and verified: All 10 templates visible (3 common + 7 accounting-za) |
| GAP-008A | Org settings page "Coming Soon" — cannot rename org or set currency | major | OPEN | Dev | — | 0 | |
| GAP-008B | FICA field groups not auto-attached during customer creation | major | OPEN | Dev | — | 1 | Only Contact & Address shown in Step 2 |
| GAP-008C | Projects page JS error on first load (TypeError: null ref) | bug | OPEN | Dev | — | 0 | Race condition, non-cascading |
| GAP-001 | PROPOSAL_SENT automation trigger does not exist | major | OPEN | Dev | — | 0 | Engagement letter follow-up blocked |
| GAP-002 | FIELD_DATE_APPROACHING automation trigger does not exist | major | OPEN | Dev | — | 0 | SARS deadline reminders blocked |
| GAP-003 | CHECKLIST_COMPLETED automation trigger does not exist | major | OPEN | Dev | — | 14 | FICA completion notification blocked |
| GAP-004 | Statement-of-account template is a stub | major | OPEN | Dev | — | 90 | CustomerContextBuilder missing invoice history |
| GAP-005 | Terminology overrides not loaded at runtime | minor | WONT_FIX | — | — | 0 | Not blocking QA — cosmetic |
| GAP-006 | Rate card defaults not auto-seeded from profile | minor | WONT_FIX | — | — | 0 | Manual setup works, not blocking |
| GAP-007 | Delayed automation triggers cannot be verified | minor | WONT_FIX | — | — | 14 | Testing limitation, not blocking |
| GAP-009 | FICA checklist does not filter by entity type | major | OPEN | Dev | — | 3 | Trust/sole-prop get wrong items |
| GAP-010 | Trust-specific custom fields missing | major | OPEN | Dev | — | 3 | Cannot serve trust clients properly |
| GAP-011 | No retainer overage/overflow billing | major | OPEN | Dev | — | 30 | Cannot bill out-of-scope retainer work |
| GAP-012 | No effective hourly rate per retainer report | major | OPEN | Dev | — | 60 | Manual calculation needed |
| GAP-013 | No proposal/engagement letter lifecycle tracking | minor | OPEN | Dev | — | 1 | No pending proposals dashboard |
| GAP-014 | No disbursement/expense invoicing workflow | minor | OPEN | Dev | — | 45 | Expense recovery unclear |
| GAP-015 | No bulk time entry creation | minor | OPEN | Dev | — | 30 | Tedious but functional |
| GAP-016 | No SA-specific invoice PDF formatting | minor | OPEN | Dev | — | 30 | SARS requirements may not be met |
| GAP-017 | No recurring engagement auto-creation | minor | OPEN | Dev | — | 1 | Year-end rollover is manual |
| GAP-018 | No client onboarding progress tracker | minor | OPEN | Dev | — | 1 | No aggregated onboarding view |
| GAP-019 | Currency displays as USD not ZAR | cosmetic | OPEN | Dev | — | 0 | Unprofessional for SA firm |
| GAP-020 | Portal auth for info requests unclear | minor | OPEN | Dev | — | 1 | Client token flow untested |
| GAP-021 | No SARS integration or eFiling export | minor | WONT_FIX | — | — | 75 | Future enhancement |
| GAP-022 | No engagement letter auto-creation from template | cosmetic | WONT_FIX | — | — | 75 | Nice to have |
| GAP-023 | No saved views on list pages | minor | OPEN | Dev | — | 90 | Re-apply filters every visit |
| GAP-024 | No aged debtors report | major | OPEN | Dev | — | 90 | Cannot manage cash flow |
| GAP-025 | Team member list API calls port 8080 instead of 8081 in E2E stack | bug | OPEN | Dev | — | 0 | Member list shows "No members found" despite 3 members existing; ERR_CONNECTION_REFUSED to localhost:8080/api/members |
| GAP-026 | FICA/KYC checklist template not seeded by accounting-za pack | major | OPEN | Dev | — | 0 | Only generic "Generic Client Onboarding" (4 items) exists; expected "FICA/KYC — SA Accounting" with 9 items |
| GAP-027 | Customer pages SSR crash after ONBOARDING lifecycle transition | blocker | OPEN | Dev | — | 1 | All customer pages (list + detail) crash with TypeError null ref after transitioning any customer to ONBOARDING. Backend API works fine. Blocks all remaining Day 1+ checkpoints. |
| GAP-028 | Customer detail page intermittent render crash | major | OPEN | Dev | — | 1 | Pages sometimes render as empty table instead of content. Related to GAP-008C. Became persistent after ONBOARDING transition (GAP-027). |

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → ✓
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-15T22:35Z | Setup | Initial status seeded from gap report |
| 2026-03-15T23:12Z | Infra | GAP-008 FIXED: Added verticalProfile to ProvisioningController DTO + seed.sh. All 10 templates seeded (3 common + 7 accounting-za). E2E stack running. |
| 2026-03-16T00:15Z | QA | Day 0 execution complete (cycle 1). 13 checkpoints: 8 PASS, 3 PARTIAL, 2 FAIL. GAP-008 VERIFIED. New gaps: GAP-025 (team API port), GAP-026 (FICA checklist not seeded). No blockers for Day 1. |
| 2026-03-15T21:27Z | QA | Day 1 execution partial (cycle 1). 10 checkpoints: 2 PASS, 1 PARTIAL, 7 FAIL (6 blocked). Kgosi Construction created with all 16 custom fields. Checklist partially tested (generic 4-item, not FICA 9-item). ONBOARDING transition succeeded in backend but caused cascading frontend crash (GAP-027 BLOCKER). New gaps: GAP-027 (SSR crash, blocker), GAP-028 (intermittent render crash). QA halted — cannot proceed past checkpoint 1.4. |
