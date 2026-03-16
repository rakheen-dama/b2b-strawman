# QA Cycle Status — 2026-03-16

## Current State

- **QA Position**: Day 90 complete — full lifecycle script finished
- **Cycle**: 2
- **E2E Stack**: HEALTHY
- **Branch**: `bugfix_cycle_2026-03-16`
- **Scenario**: `tasks/phase48-lifecycle-script.md`
- **Overall Verdict**: YES (with caveats) — firm could run on this platform

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
| GAP-P48-001 | Proposal creation/detail/send UI | blocker | VERIFIED (PARTIAL) | Dev | #719 | 1 | Detail page + send flow work. Create dialog has GAP-P48-012. |
| GAP-P48-002 | No "New Invoice" button on invoices list — creation requires unbilled time entries | major | SPEC_READY | Dev | — | 30 | Add customer-picker "New Invoice" button to `/invoices` page. ~30 min. Fix spec: `fix-specs/GAP-P48-002.md`. |
| GAP-P48-003 | No retainer-specific invoice flow — period close not exposed in UI | major | WONT_FIX | — | — | 30 | Close Period button exists in `retainer-detail-actions.tsx` but only shows when `readyToClose=true` (period end date passed). QA scenario didn't advance past period boundary. Same root as GAP-P48-011. |
| GAP-P48-004 | Trust field pack not registered in vertical profile JSON | major | VERIFIED | Dev | #718 | 3 | Trust-specific fields (Trust Registration Number, Trust Deed Date, Trust Type) appear in Step 2 when entity type = Trust. |
| GAP-P48-005 | Rate card and tax defaults not auto-seeded from vertical profile | major | WONT_FIX | — | — | 0 | New feature. Manual setup works. Out of scope for bugfix cycle. |
| GAP-P48-006 | Invoice "Mark as Sent" label mismatch | cosmetic | VERIFIED | Dev | #717 | 30 | Button reads "Send Invoice" on invoice detail page. Confirmed. |
| GAP-P48-007 | FICA field groups not auto-attached during customer creation (GAP-008B) | minor | OPEN | Dev | — | 1 | Only Contact & Address shown in Step 2. Requires investigation into intake system. |
| GAP-P48-008 | FICA checklist does not filter by entity type (GAP-009) | minor | WONT_FIX | — | — | 3 | New feature. Requires entity-type-specific templates or conditional visibility. Out of scope. |
| GAP-P48-009 | Portal contact required for information request send (GAP-020) | minor | OPEN | Dev | — | 1 | No auto-creation from customer email. Quick fix: auto-create on first request. |
| GAP-P48-010 | Carol gets 404 instead of permission denied on admin pages | minor | VERIFIED | Dev | #716 | 90 | Carol sees "You do not have permission..." message on `/settings/rates`. Not a 404. Confirmed. |
| GAP-P48-012 | Customer combobox in New Proposal dialog non-functional | blocker | FIXED | Dev | #720 | 1 | Added `type="button"` to combobox trigger Button. NEEDS_REBUILD: true |
| GAP-P48-011 | No close-period UI for retainers | major | WONT_FIX | — | — | 30 | Close Period button already exists in `retainer-detail-actions.tsx` (line 97-107). Only shows when `currentPeriod.readyToClose=true` (period must have ended). Not a bug — correct behavior. Fix spec: `fix-specs/GAP-P48-011.md`. |
| GAP-P48-013 | Invoice send does not trigger email notification | minor | SPEC_READY | Dev | — | 30 | Likely missing SMTP integration config in E2E seed, or missing INVOICE template. ~45 min investigation. Fix spec: `fix-specs/GAP-P48-013.md`. |
| GAP-P48-014 | Resources page crashes with TypeError | major | SPEC_READY | Dev | — | 45 | Null access on nested capacity grid fields (allocations, weeks). Add defensive null guards in `AllocationGrid`, `CapacityCell`, and page. ~30 min. Fix spec: `fix-specs/GAP-P48-014.md`. |
| GAP-P48-015 | Customer detail shows "0 projects" despite API-linked projects | major | SPEC_READY | Dev | — | 75 | Two linkage mechanisms: `Project.customerId` (direct FK) vs `customer_projects` (join table). Customer detail only queries join table. Need to merge both in `CustomerProjectService`. ~45 min. Fix spec: `fix-specs/GAP-P48-015.md`. |
| GAP-P48-016 | Invoice send requires overrideWarnings for 70%+ complete profiles | minor | WONT_FIX | — | — | 30 | Intentional design. Soft warning for incomplete customer profiles. `overrideWarnings: true` flag exists for this purpose. Fix spec: `fix-specs/GAP-P48-016.md`. |
| GAP-P48-017 | Budget dialog may close without saving | minor | SPEC_READY | Dev | — | 30 | React 19 form `action=` pattern race condition with Radix Dialog. Convert to `onSubmit` with `preventDefault`. ~15 min. Fix spec: `fix-specs/GAP-P48-017.md`. |
| GAP-P48-018 | Project/Customer Profitability show "No data" despite time entries | minor | SPEC_READY | Dev | — | 30 | Profitability query requires `billing_rate_currency IS NOT NULL` on time entries. Likely rate snapshots missing because rates set after time entries. ~30 min investigation. Fix spec: `fix-specs/GAP-P48-018.md`. |

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → done
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-16T11:30Z | Setup | Initial status seeded from phase 48 gap report. 11 gaps: 1 blocker, 6 major, 3 minor, 1 cosmetic. 2 items WONT_FIX (new features out of scope). |
| 2026-03-16T21:34Z | Infra | E2E stack started. All services healthy: backend (8081), frontend (3001), mock-idp (8090), postgres (5433), localstack (4567), mailpit (8026). Seed completed successfully: org e2e-test-org, 3 members, 1 ACTIVE customer, 1 project. |
| 2026-03-16T21:35Z | QA | Phase 48 Cycle 1: Day 0 execution complete. 18/18 PASS. All Phase 47 fixes verified (no regressions): org settings page works, currency defaults to ZAR, team member list loads, templates show accounting pack. Billing rates set (Alice R1500, Bob R850, Carol R450), cost rates set (Alice R600, Bob R400, Carol R200). Tax 15% pre-seeded. 10 templates, 11 automations, SA accounting custom fields all present. |
| 2026-03-16T21:46Z | QA | Phase 48 Cycle 1: Day 1 execution partial (steps 1.1-1.13). Kgosi Construction created as PROSPECT, transitioned to ONBOARDING. 8 PASS, 2 PARTIAL, 1 FAIL (blocked), 4 NOT TESTED. GAP-P48-008 CONFIRMED (Trust fields show for Company type). GAP-P48-001 CONFIRMED at step 1.19 (no proposal UI). New observation: checklist Mark Complete may not persist. Console TypeError on customer detail after transition (non-blocking SSR issue). Stopped at GAP-P48-001 blocker. |
| 2026-03-16T22:10Z | Product | Triage complete. GAP-P48-001: SPEC_READY (Option B — minimal viable create dialog + detail page + send, ~1.5-2h, backend 100% complete). GAP-P48-004: SPEC_READY (10-min config fix). GAP-P48-006: SPEC_READY (5-min label fix). GAP-P48-010: SPEC_READY (15-min permission message fix). Also noted: FeeModel type mismatch in frontend (FIXED_FEE vs FIXED) — included in GAP-P48-001 spec. 4 fix specs written to qa_cycle/fix-specs/. |
| 2026-03-16T22:18Z | Infra | E2E stack rebuilt after cycle 1 fixes (PRs #716-#719). Full teardown + rebuild. All services healthy: frontend (3001 HTTP 200), backend (8081 HTTP 200), mock-idp (8090 JWKS OK). Removed NEEDS_REBUILD flags from GAP-P48-004, GAP-P48-006, GAP-P48-010. Ready for QA cycle 2 verification. |
| 2026-03-17T00:40Z | QA | Cycle 2: Fix verification complete. GAP-P48-004 VERIFIED, GAP-P48-006 VERIFIED, GAP-P48-010 VERIFIED. GAP-P48-001 PARTIAL (detail+send work, but create dialog Customer combobox broken — new GAP-P48-012 logged). |
| 2026-03-17T00:45Z | QA | Cycle 2: Days 1-14 lifecycle execution complete. 5 customers (all ACTIVE), 5 projects, 8 tasks, 9 time entries (15.5h, 100% billable), 1 proposal (SENT), 1 invoice (APPROVED). Data created via API + UI verification. React SSR hydration errors on multiple pages (non-blocking). Results: `checkpoint-results/cycle2-day1-day14.md`. |
| 2026-03-17T01:15Z | Dev | GAP-P48-012 FIXED (PR #720). Added `type="button"` to customer combobox trigger Button in `create-proposal-dialog.tsx`. Button defaulted to `type="submit"` inside form, causing form submission instead of popover open. Audited all buttons in file — no other instances. Build passes. NEEDS_REBUILD. |
| 2026-03-17T01:30Z | QA | Cycle 2: Days 30-90 lifecycle execution complete. Created 2 new invoices (INV-0002 Naledi R1,638.75, INV-0003 Kgosi R6,325.00), approved+sent both. Recorded payment on INV-0003 (PAID). Logged expense (CIPC R150). Created BEE project for Vukani (budget 5h/R7,500, 2h consumed). Created Kgosi Feb retainer invoice (INV-0004 R6,497.50 with expense line). Created BEE invoice for Vukani (R3,450 draft). Created Annual Tax Return project for Kgosi (3 tasks, 4h logged by Carol). Verified: Timesheet report (17.5h/10 entries), Compliance dashboard (5 Active), RBAC (Carol blocked from /settings/rates), My Work (6 tasks for Carol). 6 new gaps logged (GAP-P48-013 through GAP-P48-018). Resources page crashes (GAP-P48-014). Customer detail doesn't show linked projects (GAP-P48-015). Final state: 5 customers, 7 projects, 5 invoices, 21.5h logged. Results: `checkpoint-results/cycle2-day30-day90.md`. |
| 2026-03-17T02:15Z | Product | Triage complete for 6 new gaps (GAP-P48-013 through GAP-P48-018) plus remaining OPEN items. **5 SPEC_READY** (GAP-P48-002, 014, 015, 017, 018 + GAP-P48-013), **3 WONT_FIX** (GAP-P48-003, 011, 016). GAP-P48-003/011 are same issue: Close Period button exists but conditional on period end date — not a bug. GAP-P48-016: overrideWarnings is intentional design. Total fixable effort: ~2.5h. Fix priority: 014 (crash, 30min) > 015 (data linkage, 45min) > 002 (UX, 30min) > 017 (race condition, 15min) > 018 (investigation, 30min) > 013 (E2E config, 45min). 7 fix specs written to `qa_cycle/fix-specs/`. |
