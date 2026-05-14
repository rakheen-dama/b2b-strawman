# QA Cycle Status — Accounting ZA 90-Day Lifecycle (Keycloak)

- **Branch**: `bugfix_cycle_2026-05-14`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025)
- **Started**: 2026-05-14
- **Mode**: Clean slate. All Docker volumes wiped. Keycloak bootstrapped (padmin only). Orgs/users will be created through real onboarding flow.

## Per-Day Workflow (NON-NEGOTIABLE)

For each day-N walk in this cycle:

1. **QA agent walks Day N end-to-end.** Records every checkpoint PASS/FAIL/PARTIAL with evidence. Files OBS-* gaps for every defect.
2. **Triage every gap.** Product agent reads each new gap and decides: SPEC_READY, WONT_FIX-EXEMPT, or scenario-amendment.
3. **Fix every spec.** Dev agent: reproduce → full verify → marker → commit → PR → review → merge.
4. **PR the bugfix branch into main.** Each fix is its own PR against main.
5. **Address review findings.** All reviewer flags addressed before merge.
6. **Merge.** Pre-merge gate hook blocks unless verify markers are green.
7. **Retest each fix on main with QA agent.** Mark VERIFIED only after observed end-to-end PASS.
8. **Only then advance QA Position.** Day N+1 starts only when all Day N gaps are VERIFIED on main.

## Mandate (from user)
- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in.
- **No production data — backward data compatibility is NOT a priority.** All data is disposable.
- No workarounds besides Mailpit API for OTP/invite-link extraction and dev-only Keycloak issues.
- All other bugs must be **fixed** at the checkpoint they appear.
- Reviewed PRs to **main**, merged, retested before continuing.
- Frontend must run **clean** — no JavaScript/Next.js errors in logs.
- No SQL shortcuts. APIs and browser UI only.
- AI provider 5xx → wait and retry, do not stop.

## QA Position
- **Day**: 9 — COMPLETE (8 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED — no new gaps)
- **Next checkpoint**: Day 10 (Bob uploads bank statements to bookkeeping engagement documents)
- **Day 0 deferred items resolved**: Field promotion inline (0.36) VERIFIED via Day 1 create dialog, no duplicates (0.37) VERIFIED. Engagement field promotion (0.38) VERIFIED via Day 3 New Engagement dialog. Cancel dialog (0.39) deferred (non-blocking). Modules page (0.44-0.45), billing screenshot (0.52) remain deferred.
- **All Day 0 gaps resolved**: OBS-4002 VERIFIED, OBS-4003 VERIFIED, OBS-4004 VERIFIED
- **Sipho Dlamini client ID**: 31986024-382f-48ac-abb9-5dfa64fde531
- **Sipho lifecycle**: ACTIVE (transitioned through PROSPECT → ONBOARDING → ACTIVE via FICA/KYC checklist completion)
- **Sipho Tax Return engagement ID**: 583ee45e-40b5-4846-9082-92f69f0f5f17 (Tax Return — Individual ITR12, Ref: TR-2026-0001, Type: TAX_RETURN, 7 tasks, Carol assigned to 4, 2.5h logged total (1.0h Carol Day 4 + 1.5h Carol Day 7), 2 IT3a docs uploaded by Carol, 1 document comment by Bob)
- **Kgosi Holdings client ID**: 90d93d67-b462-4fe9-9732-656af5ab889e
- **Kgosi lifecycle**: ACTIVE (transitioned through PROSPECT → ONBOARDING → ACTIVE via FICA/KYC checklist completion, 8/8 required items, 3 skipped)
- **Kgosi Monthly Bookkeeping engagement ID**: a32c67d5-8e09-47b9-82ec-f0e82fa94ec4 (Monthly Bookkeeping, Ref: BK-2026-03-0001, Type: BOOKKEEPING, 6 tasks, Carol added as member Day 9, 5.0h logged (3.0h Bob Day 8 "Bank reconciliation" + 2.0h Carol Day 9 "Debtors recon"), R 3,450 unbilled)
- **Kgosi Year-End Pack engagement ID**: 388d5104-7789-4ad6-bb6c-6d045e9663f3 (Year-End Pack / Annual Financial Statements, 7 tasks, 2.0h logged by Thandi Day 7 on "Request & receive trial balance")
- **Total hours this month**: 9.5h (Sipho 2.5h + Bookkeeping 5.0h + Year-End Pack 2.0h)

## Stack State
- Dev Stack: **Running** (backend :8080, gateway :8443, frontend :3000, portal :3002, KC :8180, Mailpit :8025, Postgres :5432, LocalStack :4566)
- NEEDS_REBUILD: false

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|
| OBS-4001 | Approve button on access-requests requires JS-level click to surface confirmation dialog | LOW | QA | WONT_FIX | 0 | Playwright automation quirk, not a product bug. Dialog works correctly for real users. QA agent should use `{ force: true }` or `evaluate` click. |
| OBS-4002 | Missing engagement templates: Payroll (monthly) and Trust AFS not in accounting-za pack | MEDIUM | Dev | VERIFIED | 0 | Added Payroll (Monthly) + Annual Trust Financial Statements templates to accounting-za.json. PR #1305 merged. Full verify: 5209 tests, 0 failures. Retest: 7/7 templates present on clean-slate stack. |
| OBS-4003 | Logo upload not tested -- no test logo file available | INFO | QA | VERIFIED | 0 | Valid 200x200 green (#1B5E20) PNG created at `qa_cycle/test-fixtures/thornton-logo.png` (763 bytes). Retest: uploaded via UI, preview renders (blob + S3 sidebar logo). |
| OBS-4004 | Automations page not found in settings navigation | MEDIUM | Dev | VERIFIED | 0 | Root cause: `automation_builder` not in accounting-za enabledModules. Fix: added to vertical profile JSON. PR #1304 merged. Full verify: 5209 tests, 0 failures. Retest: Automations link in settings nav, 13 rules including 4 accounting-specific. |

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Infra | Clean slate setup: volumes wiped, KC bootstrapped, all services started | Stack running |
| 0 | QA | Day 0 full walk: Phase A (access request + OTP), Phase B (admin approval), Phase C (KC registration), Phase D (team invites), Phase E (settings/rates/tax), Phase F (custom fields), Phase G (templates), Phase H (progressive disclosure), Phase I (billing) | 37 PASS / 1 PARTIAL / 10 DEFERRED / 0 FAIL |
| 0 | Product | Triage OBS-4001 through OBS-4004. OBS-4001: WONT_FIX (Playwright quirk). OBS-4002: escalated to MEDIUM + SPEC_READY (Trust AFS blocks Day 16). OBS-4003: SPEC_READY (fixture generation). OBS-4004: SPEC_READY (profile config fix). | 1 WONT_FIX, 3 SPEC_READY |
| 0 | Dev | Fix OBS-4004: add `automation_builder` to accounting-za enabledModules. Updated vertical profile JSON + 2 test assertions. | PR #1304 merged. Full verify: 5209 tests, 0 failures. |
| 0 | Dev | Fix OBS-4002: add Payroll (Monthly) + Annual Trust Financial Statements templates to accounting-za project template pack. 7 templates total (was 5). | PR #1305 merged. Full verify: 5209 tests, 0 failures. |
| 0 | Orchestrator | Fix OBS-4003: generated valid 200x200 green PNG test fixture at `qa_cycle/test-fixtures/thornton-logo.png`. | FIXED (QA fixture, no code change) |
| 1 | Infra | Clean-slate rebuild after OBS-4002 + OBS-4004 fixes. Volumes wiped, KC bootstrapped, all services restarted. | Stack running |
| 1 | QA | Day 0 retest after OBS-4002/4003/4004 fixes. Full walk: Phases A-I. Re-provisioned org + 3 users. Retested all 3 fixes. | 37 PASS / 0 PARTIAL / 8 DEFERRED / 0 FAIL. OBS-4002 VERIFIED (7/7 templates). OBS-4003 VERIFIED (logo uploaded+previews). OBS-4004 VERIFIED (automations page loads, 13 rules). |
| 1 | QA | Day 1 walk: Bob logs in, creates Sipho Dlamini as client (standard + promoted fields), verifies detail page | 6 PASS / 0 FAIL. Sipho ID: 31986024-382f-48ac-abb9-5dfa64fde531 |
| 2 | QA | Day 2 walk: Transition Sipho PROSPECT→ONBOARDING→ACTIVE. FICA/KYC checklist (11 items: 8 completed with docs, 3 skipped). Activation prereqs (City+Country). | 3 PASS / 0 FAIL. Sipho now ACTIVE. |
| 1 | QA | Day 1 walk: Login as Bob (Admin), create client Sipho Dlamini with accounting-za promoted fields (entity type, tax number, address), verify Prospect status in list, verify promoted fields inline on detail page. Field promotion Day 0 deferred items 0.36/0.37 now VERIFIED. | 6 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. |
| 3 | QA | Day 3 walk: Create "Sipho Dlamini — 2025/26 Tax Return" engagement from Tax Return — Individual (ITR12) template. 7 tasks instantiated. Added Carol as member, assigned 4 initial tasks. Field promotion (0.38) VERIFIED. | 6 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Engagement ID: 583ee45e-40b5-4846-9082-92f69f0f5f17 |
| 4 | QA | Day 4 walk: Carol logs 1.0h on Sipho engagement ("Document collection -- client portal"). Thandi creates Kgosi Holdings (Pty) Ltd with all accounting-za promoted fields inline (wow moment screenshot). Onboarding completed: FICA/KYC 8/8 required, 3 skipped. Auto-activated. | 8 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Kgosi ID: 90d93d67-b462-4fe9-9732-656af5ab889e |
| 5 | QA | Day 5 walk: Carol uploads 2 IT3a PDFs (employer + investment certificates) to Sipho engagement Documents tab. Thandi creates Kgosi Holdings Monthly Bookkeeping engagement from template (6 tasks: bank recon, creditors recon, debtors recon, VAT calc, mgmt accounts, month-end close). | 5 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Kgosi Bookkeeping ID: a32c67d5-8e09-47b9-82ec-f0e82fa94ec4 |
| 6 | QA | Day 6 walk: Bob reviews uploaded IT3a docs on Sipho engagement, adds document-level comment "@Carol Need proof of retirement annuity contribution" (Internal only). Thandi creates Kgosi Holdings Year-End Pack engagement from template (7 tasks: request TB, review TB & journals, draft AFS, director approval, CIPC filing, tax computation, final archiving). Wow moment screenshot captured. | 10 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Year-End Pack ID: 388d5104-7789-4ad6-bb6c-6d045e9663f3 |
| 7 | QA | Day 7 walk: Carol logs 1.5h on Sipho engagement ("Drafted tax return in eFiling") against Prepare ITR12 task (R 450/hr = R 675). Thandi logs 2.0h on Kgosi Year-End Pack ("Initial planning meeting + scope confirmation") against Request & receive trial balance task (R 1500/hr = R 3000). | 4 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Sipho total: 2.5h. Year-End Pack total: 2.0h. |
| 8 | QA | Day 8 walk: Bob logs 3.0h on Kgosi Monthly Bookkeeping ("Mar bank recon + creditors") against Bank reconciliation task (R 850/hr = R 2,550). Verified Time tab, Overview, and Dashboard all reflect entry. | 7 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Bookkeeping total: 3.0h. Monthly hours: 7.5h. |
| 9 | QA | Day 9 walk: Bob adds Carol as member to Kgosi Bookkeeping engagement (prerequisite -- scenario gap). Carol logs 2.0h on Debtors reconciliation task ("Debtors recon", R 450/hr = R 900). Verified Time tab (5h total, 2 contributors, 2 entries), Overview (5.0h, team breakdown), and Dashboard (9.5h monthly). | 8 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps. Bookkeeping total: 5.0h. Monthly hours: 9.5h. |
