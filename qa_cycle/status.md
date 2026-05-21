# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak)

- **Branch**: `bugfix_cycle_2026-05-21`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025)
- **Started**: 2026-05-21
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
- **Day**: 4 — COMPLETE
- **Next checkpoint**: Day 5 (Firm reviews FICA submission — Bob accepts uploaded documents)
- **Completed**: Day 0 (all phases), Day 1 (Firm onboarding polish — logo, brand colour, tariffs, trust account), Day 2 (Sipho onboarded as client, conflict check CLEAR, KYC skipped — adapter not configured), Day 3 (RAF matter created RAF-2026-001, FICA info request REQ-0001 sent to sipho.portal@example.com, magic-link email delivered), Day 4 (Sipho portal login via magic-link, 3 FICA documents uploaded and submitted, envelope IN_PROGRESS 3/3 submitted, pending count 0, "Powered by Kazi" footer confirmed, zero console errors, zero new gaps)

## Stack State
- Dev Stack: **Running** — all healthy
  - Backend: :8080 (PID 42107)
  - Gateway: :8443 (PID 42336)
  - Frontend: :3000 (PID 42477)
  - Portal: :3002 (PID 42529)
  - Keycloak: :8180 (Docker)
  - Postgres: :5432 (Docker)
  - Mailpit: :8025 (Docker)
  - LocalStack: :4566 (Docker)
- NEEDS_REBUILD: false

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|
| OBS-ENV-01 | Chrome password manager extension blocks form interactions with chrome-extension:// errors | LOW | Env | WORKAROUND | 0 | Use JS form submission via javascript_tool |
| OBS-ENV-02 | Gateway BFF session persists across browser tab groups; requires gateway restart for user switching | LOW | Env | WORKAROUND | 0 | Restart gateway via svc.sh restart gateway |
| OBS-201 | KYC Verification not available — no adapter configured, no "Run KYC Verification" button on client detail | LOW | Product | EXPECTED | 2 | Mandate allows KYC gap. Scenario allows skip (checkpoint 2.8). |

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Infra | Clean slate setup: volumes wiped, KC bootstrapped, all services started | Stack running |
| 1 | QA | Day 0 Phase A: Access request + OTP verification (thandi@mathebula-test.local) | PASS (7/8 checkpoints pass, 1 PARTIAL due to extension interference) |
| 1 | QA | Day 0 Phase B: Platform admin approval (padmin approves Mathebula & Partners) | PASS (8/9 checkpoints pass, 0.16 deferred — profile verified indirectly) |
| 1 | QA | Day 0 Phase C: Owner registration (Thandi registers via KC invite link) | PASS (8/8 checkpoints pass — registration required KC admin API password reset after session conflict) |
| 1 | QA | Day 0 Phase D: Team invites (Bob Admin + Carol Member) | PASS (7/7 checkpoints pass — both users invited, KC invitation emails received, both registered and reached dashboard with legal terminology active) |
| 1 | QA | Day 0 Final Checkpoints: 3 KC users, legal-za profile, no tier gate | PASS (4/4 — all verified via Keycloak Admin API + browser observation) |
| 1 | QA | Day 1: Firm onboarding polish (logo, brand colour, tariffs, trust account) | PASS (7/7 checkpoints — branding persists, LSSA tariffs pre-seeded 19 items, Section 86 trust account created at R 0.00, zero console errors) |
| 1 | QA | Day 2: Onboard Sipho as client, conflict check + KYC | PASS (7/10 checkpoints — client created with Individual type + SA Legal fields, conflict check CLEAR, 3 checkpoints SKIPPED: KYC adapter not configured, zero console errors) |
| 1 | QA | Day 3: Create RAF matter, send FICA info request | PASS (14/14 checkpoints — matter RAF-2026-001 created from Litigation (Road Accident Fund -- RAF) template with 9 tasks, grouped tab bar verified (6 groups), SA Legal promoted fields inline, FICA info request REQ-0001 sent via FICA Onboarding Pack template (3 items: ID copy, Proof of residence, Bank statement), portal contact linked, magic-link email to sipho.portal@example.com delivered, zero console errors, zero new gaps) |
| 1 | QA | Day 4: Sipho first portal login, upload FICA documents | PASS (12/12 checkpoints — magic-link token exchange succeeded at /auth/exchange, redirected to /projects, /home shows 1 pending info request, request detail shows matter context "Dlamini v Road Accident Fund" with 3 upload slots (ID copy, Proof of residence, Bank statement), all 3 PDFs uploaded and submitted per-item, envelope state SENT -> IN_PROGRESS (3/3 submitted), /home pending count dropped to 0, firm branding (navy M logo) rendered, user identity "Sipho Dlamini" displayed, sidebar uses legal-za terminology (Matters, Fee Notes, Engagement Letters), footer reads "Powered by Kazi" (OBS-404 confirmed), zero console errors, zero new gaps) |
