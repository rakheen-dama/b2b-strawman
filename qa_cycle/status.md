# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — Cycle 2026-07-06

- **Branch**: `bugfix_cycle_2026-07-06`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-07-06
- **Mode**: Fresh cycle. Prior cycle (AI core live verification, 2026-06-14/15) archived to `_archive_2026-06-15_ai-core-live-verification/`. Orgs/users for this scenario are created through the real onboarding flow per the scenario's Day 0.
- **Note**: Prior-cycle data (e.g. `verifain-attorneys` org) may still exist in the DB — the scenario creates its own org; QA must not reuse stale orgs unless the scenario says to.

## Mandate

- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired (per CLAUDE.md §6 WONT_FIX exemptions). Everything else is own/fix.
- No SQL shortcuts — all operations via REST API or browser UI. Only legitimate REST use is the Mailpit email API.
- QA drives the browser via Playwright MCP (not claude-in-chrome).

## QA Position

- **Day/Checkpoint**: Day 11 COMPLETE (trust nudge email 34s, portal ledger reconciles R 50 000, ZAR locale clean; /trust auto-forwards to single-matter ledger — behaviour note). Next: Day 14 (14.1 — onboard Moroka Family Trust isolation target, as Thandi).

## Dev Stack

- **Status**: Running (2026-07-06) — Docker infra healthy (Postgres :5432, Keycloak :8180, Mailpit :8025, LocalStack :4566); local services all RUNNING+HEALTHY (backend :8080, gateway :8443, frontend :3000, portal :3002); Keycloak bootstrap applied (padmin@docteams.local); backend log clean (0 ERROR), 4 tenants reconciled; Mailpit API OK (0 messages).

## Tracker

| Gap ID | Day/Checkpoint | Summary | Severity | Owner | Status |
|--------|----------------|---------|----------|-------|--------|
| LZKC-001 | Day 2 / 2.11 | React hydration mismatch on `/pipeline` — dnd-kit DealCard `aria-describedby` differs server vs client (`DndDescribedBy-0` vs `-3`); console error, cosmetic only, drag works | Low | Product | OPEN |
| LZKC-002 | Day 7 / OBS-704 check | Reproducible hydration mismatch on firm `/proposals` index — `CreateProposalDialog > DialogTrigger` radix `aria-controls` id differs server vs client; console error on every fresh load; cosmetic, page functional. Regression-class of OBS-704 fix-verification | Medium | Product | OPEN |
| LZKC-003 | Day 7 / 7.2 | Engagement-letter dialog description reads "Create a engagement letter" — article not adjusted by legal-za term substitution | Low | Product | OPEN |
| LZKC-004 | Day 8 / terminology | Mixed "proposal"/"engagement letter" vocabulary on client-facing copy: email subject "New proposal PROP-0001…" + seeded letter body "This proposal expires…" vs portal chrome "Engagement Letter" | Low | Product | OPEN |
| LZKC-005 | Day 10 / 10.2c | Deal-won notification is in-app only — no "You won a deal" email to deal owner in Mailpit; backend `DealWonEventHandler` logs notification sent but no mail dispatch. Scenario expects owner email | Medium | Product | OPEN |

## Log

- **2026-07-06 (QA, Day 11)** — Sipho portal: trust-activity email arrived 34s after posting (subject "Trust account activity", DEPOSIT R 50 000,00, ledger deep-link); ledger renders balance card + running-balance transaction row with client-safe description; ZAR locale clean. All PASS, zero gaps. Note: `/trust` auto-forwards to the single matter ledger when only one matter has trust activity.
- **2026-07-06 (QA, Day 10)** — Thandi: PROP-0001 Accepted firm-side (audit actor shows "System" for portal accept — noted for Day 85). Deal won via board drag (stepped-mouse workaround for dnd-kit; `dragTo` alone insufficient) → Won 100%, win-nudge Prospect→Onboarding observed both-states. LZKC-005 (Medium): no deal-won email to owner, in-app only. Trust deposit R 50 000 (DEP/2026/001) posts RECORDED (no dual-approval for deposits); reconciles across client ledger, matter Finance>Trust, cashbook. Observation: proposal acceptance auto-created a 2nd matter "Engagement Letter — Litigation (Dlamini v RAF)" for Sipho — will appear on portal /projects (Days 15/75/90 counts).
- **2026-07-06 (QA, Days 7–8)** — **Recovery**: found a prior QA session had executed Day 7 (~7.1–7.13) at 23:46Z and died without recording (PROP-0001 Sent + stray PROP-0002 draft + deal-linked PROP-0003 already existed; Mailpit/backend-log evidence). Re-executed 7.1–7.6 live (minted duplicate PROP-0004 before discovery; drafts have no Delete UI — PROP-0002/0004 remain as documented residue, never sync to portal) and verified all remaining Day-7 checkpoints against observed state: send/log/email/portal-projection/deal-Engagement-60%/PROP-0003-on-deal all PASS. OBS-702 tz-drift PASS; OBS-704 console-clean **FAIL** → LZKC-002 (reproducible hydration error on firm `/proposals`, CreateProposalDialog radix id). Day 8 as Sipho (fresh magic link): email link → detail (SENT) → Accept → ACCEPTED inline confirm, reload shows accepted-state (no double-accept), /home clear, list badge ACCEPTED — all PASS; 8.3 VAT-line PARTIAL per OBS-701 product shape (no new gap). New gaps: LZKC-002 (Medium), LZKC-003 (Low, "a engagement" grammar), LZKC-004 (Low, proposal/engagement-letter terminology mix). Deal-proposal numbering deviates from script (PROP-0003 not PROP-0002) due to dead-run residue.
- **2026-07-06 (QA, Day 5)** — Bob reviewed REQ-0001: 3 PDFs retrievable (presigned URL curl-verified 200 + valid PDF), Accept×3 → envelope Completed, FICA card "Done/Verified" with canonical /information-requests route (OBS-501 ✓), full activity trail, 3+1 notification emails in Mailpit, portal shows COMPLETED 3/3 accepted (OBS-502 ✓). All PASS, zero gaps.
- **2026-07-06 (QA, Day 4)** — Sipho magic-link login (:3002, no Keycloak), /home + branding + identity verified, 3 FICA PDFs uploaded and per-item submitted (1/3→3/3, envelope IN_PROGRESS per OBS-403), pending count → 0, footer "Powered by Kazi". All PASS. Infra: portal dev server crashed (next MODULE_NOT_FOUND) as fallout of the Day-2 pnpm install under running servers — restarted portal+frontend, page healthy; not a product bug.
- **2026-07-06 (QA, Day 3)** — RAF matter created from legal template (RAF-2026-001, `/projects/272be4f8…`), header card + 7 grouped tabs verified, SA-legal fields on Details>Fields (single location, Court set), Correspondence MCP empty state verified, FICA Onboarding Pack REQ-0001 sent to Sipho (3 items), magic-link email in Mailpit → portal :3002 exchange URL. All PASS, zero gaps.
- **2026-07-06 (QA, Day 2)** — Bob login (branding persists cross-login). Sipho created (INDIVIDUAL, SA-legal step-2 promoted fields incl. ID number), conflict check "No Conflict", KYC skipped (no adapter — mandate exemption), DEAL-0001 enquiry created R87,500 and dragged to Conflict check (30%). Gap LZKC-001 (Low): /pipeline hydration mismatch from dnd-kit aria id. Infra: stale node_modules caused '@dnd-kit/core not found' build error on first /pipeline load — fixed with `pnpm install --frozen-lockfile` (deps were already in package.json+lockfile; env-only issue).
- **2026-07-06 (QA, Day 1)** — Branding (logo→S3 + #1B3358) saved and persisted across reload; LSSA 2024/2025 High Court schedule pre-seeded (19 items, 4(a) R7800/day + 4(c) R780/hr verified); Section 86 trust account "Mathebula Trust — Main" created with dual approval, dashboard shows R 0,00. All Day 1 checkpoints PASS, zero gaps.
- **2026-07-06 (QA, Day 0)** — Session 0 reset executed (stale prior-cycle Mathebula org/schema/KC users removed per scenario 0.C/0.D/0.E; backend restarted clean). Day 0 executed end-to-end via Playwright on the Keycloak stack: access request + OTP (Mailpit), padmin approval (tenant `tenant_5039f2d497cf` provisioned, `vertical_profile=legal-za`), Thandi KC registration → org dashboard with full legal nav, Bob (Admin) + Carol (Member) invited + registered. ALL Day 0 checkpoints PASS, zero gaps. Noted QA-harness quirk: after OAuth redirect chains, trusted pointer events stop reaching the page — workaround is explicit re-`goto` after every auth redirect (documented in day-00.md; not a product bug).
- **2026-07-06 (Infra, stack start)** — Docker daemon was down; started Docker Desktop, then `dev-up.sh` (all 4 containers healthy, Keycloak SSL fix reapplied + KC restart). Polled `realms/docteams` → 200 first attempt. Ran `keycloak-bootstrap.sh` (idempotent; mappers, padmin, DCR trusted hosts, lifetimes). `svc.sh start all`: backend ready 36s, gateway 6s, frontend 3s, portal 3s — all RUNNING+HEALTHY per `svc.sh status`. Mailpit API sane (`total:0`). Backend startup log has 0 ERROR lines; pack reconciliation 4/4 tenants OK. Prior-cycle data left untouched. No issues.
- **2026-07-06 (Orchestrator, cycle init)** — Branch `bugfix_cycle_2026-07-06` created. Previous cycle state archived to `_archive_2026-06-15_ai-core-live-verification/` (status.md + checkpoint-results + fix-specs). Fresh tracker seeded. Dev stack down → next action: Infra Agent (start stack).
