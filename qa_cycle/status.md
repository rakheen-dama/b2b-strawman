# QA Cycle Status — 2026-03-16

## Current State

- **QA Position**: Day 0, Checkpoint 0.1 (not started)
- **Cycle**: 0
- **E2E Stack**: Not running
- **Branch**: `bugfix_cycle_2026-03-16`
- **Scenario**: `tasks/phase48-lifecycle-script.md`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
| GAP-P48-001 | No proposal creation/detail UI — backend complete, frontend only has list view | blocker | OPEN | Dev | — | 1 | Blocks steps 1.19-1.24. Backend has full CRUD + lifecycle. Frontend needs detail page + create dialog. |
| GAP-P48-002 | No "New Invoice" button on invoices list — creation requires unbilled time entries | major | OPEN | Dev | — | 30 | Invoice creation only via customer detail page. Backend supports blank drafts but frontend blocks empty selections. |
| GAP-P48-003 | No retainer-specific invoice flow — period close not exposed in UI | major | OPEN | Dev | — | 30 | Backend `closePeriod()` creates invoices with fee + overage. No frontend button to trigger. |
| GAP-P48-004 | Trust field pack not registered in vertical profile JSON | major | OPEN | Dev | — | 3 | Pack exists at `accounting-za-customer-trust.json` but missing from `vertical-profiles/accounting-za.json` field array. 10-min fix. |
| GAP-P48-005 | Rate card and tax defaults not auto-seeded from vertical profile | major | WONT_FIX | — | — | 0 | New feature. Manual setup works. Out of scope for bugfix cycle. |
| GAP-P48-006 | Invoice "Mark as Sent" label mismatch | cosmetic | OPEN | Dev | — | 30 | Button says "Mark as Sent" but script expects "Send". Action does trigger email. 5-min fix. |
| GAP-P48-007 | FICA field groups not auto-attached during customer creation (GAP-008B) | minor | OPEN | Dev | — | 1 | Only Contact & Address shown in Step 2. Requires investigation into intake system. |
| GAP-P48-008 | FICA checklist does not filter by entity type (GAP-009) | minor | WONT_FIX | — | — | 3 | New feature. Requires entity-type-specific templates or conditional visibility. Out of scope. |
| GAP-P48-009 | Portal contact required for information request send (GAP-020) | minor | OPEN | Dev | — | 1 | No auto-creation from customer email. Quick fix: auto-create on first request. |
| GAP-P48-010 | Carol gets 404 instead of permission denied on admin pages | minor | OPEN | Dev | — | 90 | Settings > Rates uses `notFound()` for unauthorized. Should show permission message like /settings/general. 15-min fix. |
| GAP-P48-011 | No close-period UI for retainers | major | OPEN | Dev | — | 30 | Backend has `closePeriod()` endpoint. Frontend retainer detail page needs "Close Period" button. |

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → done
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-16T11:30Z | Setup | Initial status seeded from phase 48 gap report. 11 gaps: 1 blocker, 6 major, 3 minor, 1 cosmetic. 2 items WONT_FIX (new features out of scope). |
