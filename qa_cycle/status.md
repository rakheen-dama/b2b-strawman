# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — Cycle 2026-06-13

- **Branch**: `bugfix_cycle_2026-06-13`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-06-13
- **Mode**: Clean slate. All Docker volumes wiped. Keycloak bootstrapped (padmin only). Orgs/users created through real onboarding flow.
- **Context**: Regression re-run of the full lifecycle after the 2026-06 simplification roadmap landed on main (26 PRs #1403–#1435: pnpm workspace + @b2mash packages, OrgSettings → @Embeddables, security fixes, expense-markup write path). Previous cycle (2026-05-30) completed ALL_DAYS_COMPLETE; its state is archived at `qa_cycle/_archive_2026-05-30_legal-full-lifecycle-kc/`.

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

## Known carry-over exemptions (from 2026-05-30 cycle — do not re-file)
- OBS-201 class: `/api/assistant/invocations` 404 on client detail (AI infra client-side proxy not wired in KC mode) — WONT_FIX-EXEMPT.
- OBS-6001 class: no separate Statement of Account email after closure — working as designed (PortalDocumentNotificationHandler 5-min dedup coalesces closure-pack emails).
- OBS-2101 class: no tariff dropdown on time entry (non-tariff path) — WONT_FIX from prior cycle.
- OBS-701 class: fee-estimate structure/VAT line absent on portal proposal view — WONT_FIX from prior cycle.
- KYC/FICA adapter not configured — expected PARTIAL per mandate.
- Payments: mock gateway only — expected per mandate.

## QA Position
- **Day**: 2 complete — Day 3 next
- **Next checkpoint**: Day 3 — Create RAF matter, send FICA info request (3.1, actor Bob, still logged in)
- **Completed**: Day 0 (32/32 checkpoints PASS + 4/4 summary checkpoints PASS, zero gaps — see `checkpoint-results/day-00.md`); Day 1 (7/7 checkpoints PASS + 3/3 summary checkpoints PASS, zero gaps — see `checkpoint-results/day-01.md`); Day 2 (7/10 PASS, 1 PARTIAL-exempt KYC, 2 SKIPPED-exempt + 2/3 summary PASS, 1 PARTIAL-exempt, zero new gaps — see `checkpoint-results/day-02.md`)
- **Resolved**: none
- **Open gaps**: none
- **Fixed (awaiting verify)**: none
- **Exempt gaps**: none (see carry-over exemptions above; Day 2 KYC-not-configured + OBS-201 404s noted under exemptions, not filed)
- **Created Day 0**: org `mathebula-partners` (legal-za, tenant_5039f2d497cf); users thandi (Owner) / bob (Admin) / carol (Member) @mathebula-test.local
- **Created Day 1**: firm branding (logo in S3 `org/tenant_5039f2d497cf/branding/logo.png`, brand colour `#1B3358`); trust account "Mathebula Trust — Main" (Standard Bank · 051001 · 12345678, SECTION_86, Primary, R 0,00)
- **Created Day 2**: client **Sipho Dlamini** INDIVIDUAL — ID `2211a80a-5523-4a6d-8f96-0d638dff88f6` (`/org/mathebula-partners/customers/2211a80a-5523-4a6d-8f96-0d638dff88f6`); ID/Passport 8501015800088, Preferred Correspondence EMAIL, sipho.portal@example.com, +27 82 555 0101, 12 Loveday St Johannesburg 2001 ZA; lifecycle Prospect/Active; conflict check #1 No Conflict (13/06/2026 01:22:58)

## Stack State
- Dev Stack: **Running** (clean-slate start 2026-06-13)
- Docker infra: volumes wiped via `dev-down.sh --clean`, fresh start via `dev-up.sh` — Postgres :5432, LocalStack :4566, Mailpit :8025/:1025, Keycloak :8180 all healthy
- Keycloak: realm `docteams` ready; bootstrap complete — platform admin `padmin@docteams.local / password` (platform-admins group) is the ONLY user; no organizations; padmin password grant verified via token endpoint (admin-cli)
- Local services (svc.sh, all RUNNING + HEALTHY):
  - backend — PID 12154, port 8080 (/actuator/health UP)
  - gateway — PID 12387, port 8443 (/actuator/health UP)
  - frontend — PID 12531, port 3000
  - portal — PID 12595, port 3002
- Postgres: 0 tenant schemas (only `public` + `portal` app schemas present)
- Mailpit: cleared — 0 messages
- NEEDS_REBUILD: false

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Orchestrator | Archived completed 2026-05-30 cycle state; created branch `bugfix_cycle_2026-06-13`; seeded fresh status.md | Setup complete, Infra agent next |
| 1 | Infra | Clean slate setup: svc stop all, `dev-down.sh --clean` (volumes wiped), `dev-up.sh`, Keycloak bootstrap (padmin only), svc start all, Mailpit cleared, 0 tenant schemas confirmed, padmin token grant verified | All services healthy |
| 2 | QA | Day 0 executed: access request + OTP, padmin approval (legal-za auto-assigned, provisioning COMPLETED), Thandi/Bob/Carol KC registrations, team invites — full onboarding flow via browser UI; Mailpit API for OTP/invite links only | 32/32 checkpoints PASS, 0 gaps; Day 1 next |
| 3 | QA | Day 1 executed as Thandi: logo upload + brand colour #1B3358 (persists across logout/KC re-login, S3-served), LSSA 2024/2025 tariff schedule verified (19 items, 4(a) R 7800/day + 4(c) R 780/hr), Section 86 trust account created (Mathebula Trust — Main, R 0,00) | 7/7 checkpoints PASS + 3/3 summary PASS, 0 gaps; Day 2 next |
| 4 | QA | Day 2 executed as Bob (context swap, fresh KC login): Sipho Dlamini created as INDIVIDUAL with SA Legal promoted fields (ID 8501015800088, Pref. Correspondence EMAIL), conflict check No Conflict (green, History 1), FICA "Verify with AI" disabled (adapter not configured — exempt). Client ID 2211a80a-5523-4a6d-8f96-0d638dff88f6 | 7/10 PASS + 1 PARTIAL-exempt + 2 SKIPPED-exempt; summary 2/3 PASS + 1 PARTIAL-exempt; 0 new gaps; Day 3 next |
