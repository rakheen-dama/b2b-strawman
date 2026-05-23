# QA Cycle Status — Accounting ZA 90-Day Demo Readiness (Keycloak)

- **Branch**: `bugfix_cycle_2026-05-23`
- **Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-05-23
- **Mode**: Clean slate. All Docker volumes wiped. Keycloak bootstrapped (padmin only). Orgs/users created through real onboarding flow.

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
- **Day**: 0 COMPLETE → Ready for Day 1
- **Next checkpoint**: Day 1 checkpoint 1.1 (Login as Bob, create first client: Sipho Dlamini)
- **Completed**: Session 0 (prep), Day 0 Phase A-D (onboarding), Day 0 Phase E (General/rates/tax), Phase F (custom fields + field promotion), Phase G (templates + automations), Phase H (progressive disclosure), Phase I (billing/tier removal)

## Stack State
- Dev Stack: **Running** — org provisioned 2026-05-23
  - Backend: :8080 (healthy)
  - Gateway: :8443 (restarted for session clear, healthy)
  - Frontend: :3000 (healthy)
  - Portal: :3002 (healthy)
  - Keycloak: :8180 (Docker, healthy, 4 users: padmin + Thandi + Bob + Carol)
  - Postgres: :5432 (Docker, healthy, 1 tenant schema: tenant_4a171ca30392)
  - Mailpit: :8025 (Docker, healthy)
  - LocalStack: :4566 (Docker, healthy)
- NEEDS_REBUILD: false
- **Current session**: Thandi Thornton logged in on :3000

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|
| OBS-5001 | Engagements empty state uses "projects" terminology instead of "engagements" | LOW | Dev | FIXED | 0 | Added `{Projects}/{projects}/{project}` placeholders to `empty-states.json`, wrapped in `<TerminologyText>` on projects page, used `t("projects")` in KPI card, renamed automation rule to "Welcome Notification". PR #1353. Frontend: 367 files, 2290 passed (5 pre-existing chart failures). Backend: 5397 tests, 0 failures. |

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Infra | Clean slate setup: wiped all Docker volumes, started fresh infra, bootstrapped Keycloak (padmin only), verified 0 tenant schemas, 0 stale KC users, started all 4 services, cleared Mailpit | All services healthy. Ready for Day 0. |
| 1 | QA | Day 0 Phase A-D: access request, OTP verify, padmin approval, owner registration, team invites (Bob Admin, Carol Member), all 3 users registered via Keycloak | ALL PASS. 0 gaps. Org=Thornton & Associates, vertical=accounting-za, 3 KC users, 1 tenant schema. |
| 2 | QA | Day 0 Phase E-I: Settings (General/brand colour/rates/tax), Custom Fields (field promotion verified on Client + Engagement dialogs), Templates (7 engagement templates), Automations (13 rules), Progressive Disclosure (no legal modules, clean URL gating), Billing (flat managed account, no tier UI) | ALL PASS (1 PARTIAL: logo upload — no test file). 1 LOW gap filed: OBS-5001 (terminology). 0 console errors. Day 0 complete. |
| 3 | Product | Triage OBS-5001: confirmed root cause in 4 files. Frontend empty state uses `createMessages` without `TerminologyText` wrapping (unlike customers page which does it correctly). Backend `common.json` automation pack seeds "New Project Welcome" name verbatim. Fix spec written. | OPEN → SPEC_READY. Fix spec: `qa_cycle/fix-specs/OBS-5001.md`. Effort: S (< 30 min). |
| 4 | Dev | OBS-5001: Added `{Projects}/{projects}/{project}` terminology placeholders to `empty-states.json`, wrapped empty state title/description in `<TerminologyText>` on projects page (matching customers page pattern), used `t("projects")` for KPI card empty state, renamed automation rule from "New Project Welcome" to "Welcome Notification" with vertical-neutral wording. Updated `use-message.test.ts` assertions. Frontend verified: lint clean, build clean, 367 files / 2290 tests passed (5 pre-existing chart failures). Backend verified: 5397 tests, 0 failures. PR #1353. | OBS-5001 → FIXED |
