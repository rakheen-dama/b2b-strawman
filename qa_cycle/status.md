# QA Cycle Status — 2026-03-15

## Current State

- **QA Position**: Day 1, Checkpoint 1.4 (BLOCKED by GAP-027) — GAP-027 FIXED, awaiting rebuild
- **Cycle**: 1
- **E2E Stack**: Running
- **NEEDS_REBUILD**: true (frontend changed — E2E stack needs rebuild before re-verification)
- **Branch**: `bugfix_cycle_2026-03-15`
- **Scenario**: `tasks/phase47-lifecycle-script.md`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
| GAP-008 | Accounting template pack not seeded (only 3 generic templates, missing 7 accounting-specific) | blocker | VERIFIED | Infra | — | 0 | Fixed and verified: All 10 templates visible (3 common + 7 accounting-za) |
| GAP-008A | Org settings page "Coming Soon" — cannot rename org or set currency | major | WONT_FIX | — | — | 0 | Requires new feature (org settings CRUD). Out of scope for bugfix cycle. Workaround: branding set via Templates page. |
| GAP-008B | FICA field groups not auto-attached during customer creation | major | SPEC_READY | Dev | — | 1 | Only Contact & Address shown in Step 2. Fix spec: `qa_cycle/fix-specs/GAP-008B.md` |
| GAP-008C | Projects page JS error on first load (TypeError: null ref) | bug | SPEC_READY | Dev | — | 0 | Race condition, non-cascading. Fix spec: `qa_cycle/fix-specs/GAP-008C.md` |
| GAP-001 | PROPOSAL_SENT automation trigger does not exist | major | WONT_FIX | — | — | 0 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-002 | FIELD_DATE_APPROACHING automation trigger does not exist | major | WONT_FIX | — | — | 0 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-003 | CHECKLIST_COMPLETED automation trigger does not exist | major | WONT_FIX | — | — | 14 | New automation trigger type = new feature. Out of scope for bugfix cycle. |
| GAP-004 | Statement-of-account template is a stub | major | OPEN | Dev | — | 90 | Day 90 — not triaged yet (QA hasn't reached Day 90). |
| GAP-005 | Terminology overrides not loaded at runtime | minor | WONT_FIX | — | — | 0 | Not blocking QA — cosmetic |
| GAP-006 | Rate card defaults not auto-seeded from profile | minor | WONT_FIX | — | — | 0 | Manual setup works, not blocking |
| GAP-007 | Delayed automation triggers cannot be verified | minor | WONT_FIX | — | — | 14 | Testing limitation, not blocking |
| GAP-009 | FICA checklist does not filter by entity type | major | OPEN | Dev | — | 3 | Day 3 — not triaged yet (QA blocked at Day 1). |
| GAP-010 | Trust-specific custom fields missing | major | OPEN | Dev | — | 3 | Day 3 — not triaged yet (QA blocked at Day 1). |
| GAP-011 | No retainer overage/overflow billing | major | WONT_FIX | — | — | 30 | New feature. Out of scope for bugfix cycle. |
| GAP-012 | No effective hourly rate per retainer report | major | WONT_FIX | — | — | 60 | New feature. Out of scope for bugfix cycle. |
| GAP-013 | No proposal/engagement letter lifecycle tracking | minor | WONT_FIX | — | — | 1 | New feature (proposal dashboard). Out of scope. |
| GAP-014 | No disbursement/expense invoicing workflow | minor | WONT_FIX | — | — | 45 | New feature. Out of scope for bugfix cycle. |
| GAP-015 | No bulk time entry creation | minor | WONT_FIX | — | — | 30 | UX enhancement. Out of scope for bugfix cycle. |
| GAP-016 | No SA-specific invoice PDF formatting | minor | WONT_FIX | — | — | 30 | New feature. Out of scope for bugfix cycle. |
| GAP-017 | No recurring engagement auto-creation | minor | WONT_FIX | — | — | 1 | New feature. Out of scope for bugfix cycle. |
| GAP-018 | No client onboarding progress tracker | minor | WONT_FIX | — | — | 1 | New feature. Out of scope for bugfix cycle. |
| GAP-019 | Currency displays as USD not ZAR | cosmetic | OPEN | Dev | — | 0 | Low priority. QA can proceed with USD display. |
| GAP-020 | Portal auth for info requests unclear | minor | OPEN | Dev | — | 1 | Low priority. Portal flow not blocking core QA path. |
| GAP-021 | No SARS integration or eFiling export | minor | WONT_FIX | — | — | 75 | Future enhancement |
| GAP-022 | No engagement letter auto-creation from template | cosmetic | WONT_FIX | — | — | 75 | Nice to have |
| GAP-023 | No saved views on list pages | minor | WONT_FIX | — | — | 90 | UX enhancement. Out of scope for bugfix cycle. |
| GAP-024 | No aged debtors report | major | OPEN | Dev | — | 90 | Day 90 — not triaged yet (QA blocked at Day 1). |
| GAP-025 | Team member list API calls port 8080 instead of 8081 in E2E stack | bug | FIXED | Dev | #688 | 0 | Added `NEXT_PUBLIC_BACKEND_URL=http://localhost:8081` build arg to E2E compose + declared ARG in Dockerfile. PR #688 merged. |
| GAP-026 | FICA/KYC checklist template not seeded by accounting-za pack | major | SPEC_READY | Dev | — | 0 | Template IS seeded but created as inactive (`active=false`) because `autoInstantiate=false`. Fix spec: `qa_cycle/fix-specs/GAP-026.md` |
| GAP-027 | Customer pages SSR crash after ONBOARDING lifecycle transition | blocker | FIXED | Dev | #687 | 1 | Null guards added to `customerReadiness.requiredFields` accesses + error boundaries. PR #687 merged. |
| GAP-028 | Customer detail page intermittent render crash | major | FIXED | Dev | #687 | 1 | Same root cause as GAP-027. Fixed by PR #687. |

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
| 2026-03-16T01:30Z | Product | Triage cycle 1 complete. 5 items SPEC_READY (GAP-027, GAP-026, GAP-025, GAP-008B, GAP-008C). 11 items WONT_FIX (new features out of scope). GAP-028 deduped with GAP-027 (same root cause). Priority order: GAP-027 (blocker) > GAP-026 (FICA checklist) > GAP-025 (team API port) > GAP-008C/GAP-008B (lower priority). Fix specs written to `qa_cycle/fix-specs/`. |
| 2026-03-16T02:00Z | Dev | GAP-027 FIXED: Added null guards (`?.` / `?? 0` / `?? []`) to all `customerReadiness.requiredFields` accesses in customer detail page. Added error boundaries at `customers/error.tsx` and `customers/[id]/error.tsx`. GAP-028 also fixed (same root cause). PR #687 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true (frontend changed). |
| 2026-03-16T02:15Z | Dev | GAP-025 FIXED: Added `NEXT_PUBLIC_BACKEND_URL=http://localhost:8081` as Docker build arg in `compose/docker-compose.e2e.yml` and declared `ARG NEXT_PUBLIC_BACKEND_URL` in `frontend/Dockerfile`. PR #688 merged to `bugfix_cycle_2026-03-15`. NEEDS_REBUILD=true (frontend build arg changed). |
