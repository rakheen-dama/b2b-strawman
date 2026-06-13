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
- **Day**: 5 walked — 3 open gaps block the next day (per-day workflow: all Day 5 gaps must be VERIFIED on main before Day 7, the scenario's next day, starts)
- **Next checkpoint**: Day 5 gap triage (Product) → fix (Dev) → retest (QA); then Day 7 — Firm drafts + sends proposal (7.1, actor Thandi, firm :3000)
- **Completed**: Day 0 (32/32 checkpoints PASS + 4/4 summary checkpoints PASS, zero gaps — see `checkpoint-results/day-00.md`); Day 1 (7/7 checkpoints PASS + 3/3 summary checkpoints PASS, zero gaps — see `checkpoint-results/day-01.md`); Day 2 (7/10 PASS, 1 PARTIAL-exempt KYC, 2 SKIPPED-exempt + 2/3 summary PASS, 1 PARTIAL-exempt, zero new gaps — see `checkpoint-results/day-02.md`); Day 3 (14/14 PASS + 4/4 summary PASS, zero gaps — see `checkpoint-results/day-03.md`); Day 4 (14/14 PASS + 5/5 summary PASS, zero gaps — see `checkpoint-results/day-04.md`); Day 5 (5/6 PASS + 1 FAIL, 2/2 portal spot-check PASS, 3/4 summary PASS + 1 FAIL — see `checkpoint-results/day-05.md`)
- **Resolved**: none
- **Open gaps**: OBS-503 (HIGH — portal-submitted docs stuck PENDING, firm Download fails silently), OBS-504 (LOW — activity feed misattributes info-request recipient to actor), OBS-505 (MEDIUM — seeded AI-specialist automations reference non-existent specialist IDs INTAKE/BILLING/INBOX → 404 + backend ERROR on envelope completion)
- **Fixed (awaiting verify)**: none
- **Exempt gaps**: none (see carry-over exemptions above; Day 2 KYC-not-configured + OBS-201 404s noted under exemptions, not filed)
- **Created Day 0**: org `mathebula-partners` (legal-za, tenant_5039f2d497cf); users thandi (Owner) / bob (Admin) / carol (Member) @mathebula-test.local
- **Created Day 1**: firm branding (logo in S3 `org/tenant_5039f2d497cf/branding/logo.png`, brand colour `#1B3358`); trust account "Mathebula Trust — Main" (Standard Bank · 051001 · 12345678, SECTION_86, Primary, R 0,00)
- **Created Day 2**: client **Sipho Dlamini** INDIVIDUAL — ID `2211a80a-5523-4a6d-8f96-0d638dff88f6` (`/org/mathebula-partners/customers/2211a80a-5523-4a6d-8f96-0d638dff88f6`); ID/Passport 8501015800088, Preferred Correspondence EMAIL, sipho.portal@example.com, +27 82 555 0101, 12 Loveday St Johannesburg 2001 ZA; lifecycle Prospect/Active; conflict check #1 No Conflict (13/06/2026 01:22:58)
- **Created Day 3**: matter **Dlamini v Road Accident Fund** — ID `08ad56c4-ff5e-49c2-a034-cb5fa04b462c`, ref RAF-2026-001, RAF template (9 tasks), lead Bob Ndlovu, Work Type Litigation, Court "Gauteng Division, Pretoria", Opposing Party "Road Accident Fund"; info request **REQ-0001** — ID `de3d6962-6018-43bf-852d-d366d1a4d626`, FICA Onboarding Pack (3 items), Sent, due 2026-06-20; magic-link email Mailpit `hhoVkD8UxgQaLsn2dG2oNu` (token `nsJKu6Q0-pI87cRzaViMkltCnxF1hrwoGaPQzusxvFM`)
- **Created Day 4**: Sipho's portal session established via magic-link (no Keycloak); REQ-0001 now **3/3 submitted, envelope IN_PROGRESS** — fica-id.pdf / fica-address.pdf / fica-bank.pdf uploaded+submitted through portal UI, stored in S3 `org/mathebula-partners/project/08ad56c4-…/{07d78e24,60639e26,c13bb325}-…`; awaits firm per-item Accepts (Day 5)
- **Created Day 5**: REQ-0001 **COMPLETED** (3/3 accepted, "Completed on 13 Jun 2026") via Bob's per-item Accepts; matter FICA card **Done / Verified 13 Jun 2026**; 4 notification emails to Sipho in Mailpit (3× item-accepted + 1× completed); portal shows COMPLETED + 3/3 accepted. Document rows still **PENDING** (OBS-503); failed automation execution for rule `0556d81d…` + AUTOMATION_ACTION_FAILED admin notification (OBS-505)

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
| OBS-503 | Portal-submitted FICA documents stuck PENDING — firm-side Download fails silently ("Document has not been uploaded yet"); docs in S3 but never confirmed | HIGH | Product | OPEN | 5 | `PortalInformationRequestService.submitItem` never calls `document.confirmUpload()`; no portal confirm endpoint (firm flow uses `POST /api/documents/{id}/confirm`). Firm `DocumentService.getPresignedDownloadUrl` (L488) gates on UPLOADED. Secondary: firm UI swallows `success:false` with no toast. Evidence in `checkpoint-results/day-05.md`. |
| OBS-504 | Matter Activity feed misattributes info-request recipient: "Information request REQ-0001 sent to Bob Ndlovu" (actually sent to Sipho Dlamini) | LOW | Product | OPEN | 5 | `ActivityMessageFormatter` L101–103 falls back to actorName when `details.contact_name` absent; `InformationRequestService` send() audit details never include `contact_name`. Pre-existing since PR #545. |
| OBS-505 | Seeded AI-specialist automation templates reference non-existent specialist IDs (INTAKE/BILLING/INBOX vs registered intake-za/billing-za/inbox-za) — INFORMATION_REQUEST_COMPLETED automation fails 404, backend ERROR + admin AUTOMATION_ACTION_FAILED notification | MEDIUM | Product | OPEN | 5 | Bug class: all 6 INVOKE_AI_SPECIALIST template actions across `ai-specialist-{common,legal-za,consulting-za}.json` can never execute. `InvokeAiSpecialistActionExecutor:67` passes raw template id to `SpecialistRegistry.requireById`. Latent since PR #1300. NOT the OBS-201 exemption (backend wiring, fails pre-provider in any mode). |

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Orchestrator | Archived completed 2026-05-30 cycle state; created branch `bugfix_cycle_2026-06-13`; seeded fresh status.md | Setup complete, Infra agent next |
| 1 | Infra | Clean slate setup: svc stop all, `dev-down.sh --clean` (volumes wiped), `dev-up.sh`, Keycloak bootstrap (padmin only), svc start all, Mailpit cleared, 0 tenant schemas confirmed, padmin token grant verified | All services healthy |
| 2 | QA | Day 0 executed: access request + OTP, padmin approval (legal-za auto-assigned, provisioning COMPLETED), Thandi/Bob/Carol KC registrations, team invites — full onboarding flow via browser UI; Mailpit API for OTP/invite links only | 32/32 checkpoints PASS, 0 gaps; Day 1 next |
| 3 | QA | Day 1 executed as Thandi: logo upload + brand colour #1B3358 (persists across logout/KC re-login, S3-served), LSSA 2024/2025 tariff schedule verified (19 items, 4(a) R 7800/day + 4(c) R 780/hr), Section 86 trust account created (Mathebula Trust — Main, R 0,00) | 7/7 checkpoints PASS + 3/3 summary PASS, 0 gaps; Day 2 next |
| 4 | QA | Day 2 executed as Bob (context swap, fresh KC login): Sipho Dlamini created as INDIVIDUAL with SA Legal promoted fields (ID 8501015800088, Pref. Correspondence EMAIL), conflict check No Conflict (green, History 1), FICA "Verify with AI" disabled (adapter not configured — exempt). Client ID 2211a80a-5523-4a6d-8f96-0d638dff88f6 | 7/10 PASS + 1 PARTIAL-exempt + 2 SKIPPED-exempt; summary 2/3 PASS + 1 PARTIAL-exempt; 0 new gaps; Day 3 next |
| 5 | QA | Day 3 executed as Bob (session carried over): RAF matter created from "Litigation (Road Accident Fund -- RAF)" template (Dlamini v Road Accident Fund, RAF-2026-001, 9 tasks, lead Bob), header card + 7-group tab bar verified by testid, promoted fields inline (Court/Case# on Fields only), FICA Onboarding Pack REQ-0001 sent to Sipho (3 items, due 2026-06-20), portal contact ACTIVE-linked, magic-link email in Mailpit. No-Expenses-subtab proven intentional legal-za dedupe (project-tabs.tsx:163-167) | 14/14 PASS + 4/4 summary PASS; 0 new gaps; Day 4 next |
| 6 | QA | Day 4 executed as Sipho (fresh portal context :3002): magic-link from Mailpit clicked → `/auth/exchange` token login, zero Keycloak; /home pending=1, firm logo + #1B3358 navy verified computed; 3 FICA PDFs uploaded+submitted per-item via portal UI file-chooser (fica-id/address/bank.pdf); envelope SENT→IN_PROGRESS (1/3→3/3), items SUBMITTED; /home pending→0; uploads proven in LocalStack S3 (3 objects under matter path); footer "Powered by Kazi" everywhere; only console error = portal favicon 404 (cosmetic) | 14/14 PASS + 5/5 summary PASS; 0 new gaps; Day 5 next |
| 7 | QA | Day 5 executed as Bob (fresh KC login :3000): Client group tab → Requests sub-tab → REQ-0001 (In Progress, 0/3 accepted, canonical `/information-requests/{id}` link — OBS-501 holds); 3 items Submitted with PDFs; **Download FAIL** — all 3 server actions return `success:false "Document has not been uploaded yet"` silently (docs in S3 but DB rows PENDING; portal flow never confirms upload) → **OBS-503 HIGH**; per-item Accepts 0/3→3/3, envelope auto-Completed "Completed on 13 Jun 2026"; FICA card Done/Verified, canonical link; Activity feed full trail but "sent to Bob Ndlovu" recipient misattribution → **OBS-504 LOW**; Mailpit 3× item-accepted + 1× completed to Sipho; backend ERROR on completion: automation rule invokes non-existent specialist `INTAKE` (registered: `intake-za`) → **OBS-505 MEDIUM**; portal spot-check on live Sipho session: COMPLETED + 3/3 accepted, header `3/3 accepted • status COMPLETED` (OBS-502 holds) | 5/6 PASS + 1 FAIL; 2/2 portal spot-check PASS; 3/4 summary PASS + 1 FAIL; 3 gaps filed (OBS-503/504/505); Day 7 blocked pending fixes |
