# Day 90 — Final regression + exit sweep `[FIRM]` + `[PORTAL]` — cycle 2026-07-12 (run 2026-07-13)

**Actors**: Thandi (firm :3000, standing session), Sipho (portal :3002, Day-88 magic-link session).

## Environment anomaly (root-caused, documented — not a product gap)

Mid-sweep, `/invoices/collections` returned **HTTP 500** — `Module not found: Can't resolve 'dompurify'` in `frontend/app/(app)/org/[slug]/invoices/collections/reminder-queue.tsx` — and the broken Turbopack compile graph then cascaded 500s onto subsequently visited routes (trust-accounting, tariffs, settings/*). Root cause: `dompurify@^3.4.12` **is properly declared** in `frontend/package.json` (added by Epic 591B, PR #1541 `ebbcb1256`, merged AFTER the frontend dev server started 2026-07-08) but was absent from `node_modules` — the long-running dev stack never had `pnpm install` re-run post-merge. Remediation (infra maintenance, not a product fix): `pnpm install` at workspace root + `svc.sh restart frontend`. Post-restart, **all previously-500 routes return 200 with zero console errors**. Product code is correct (CI/fresh builds install deps); flagged for the stack-runbook: after merges that touch package.json, re-install before HMR-serving.

## Firm-side regression sweep

| # | Result | Evidence |
|---|--------|----------|
| 90.1 | PARTIAL → **LZKC-031** (new, Low) — LZKC-021 fix HOLDS at all its filed sites | Walked **25 firm routes** (dashboard, my-work, calendar, court-calendar, projects, customers, pipeline, proposals, retainers, compliance, conflict-check, legal/adverse-parties, invoices, invoices/billing-runs, invoices/collections, trust-accounting +client-ledgers, legal/tariffs, settings/general, settings/billing, team, reports, schedules, profitability, notifications) with programmatic innerText scans for \b(project\|customer\|invoice)\b. **LZKC-021 re-verification: HOLDS** — My Work subtitle and Calendar subtitle/"All Projects" filter/"Projects" tab all gone (zero hits on Calendar). Prior-cycle LZKC-009 Create-Client evidence fixed: dialog now reads "required to send a **fee note**". Core surfaces clean: Dashboard / Matters / Clients / Fee Notes / Engagement Letters / Pipeline / Trust Accounting (+ ledgers) / Court Calendar / Conflict Check / Adverse Parties / Tariffs / Team / Collections — zero leaks. **Residual sites found (→ LZKC-031)**: My Work task-table column header "PROJECT" ×2 + blurb "…calculate costs, and generate accurate invoices."; Compliance "No customers currently in onboarding" + "Check for Dormant Customers"; Billing Runs table headers "CUSTOMERS"/"INVOICES"; Settings>General "documents shared across all projects" + "default currency for invoices…" + "Label shown on invoices and documents"; Reports "Invoice Aging Report"/"…per project"/"grouped by member, project…"; Schedules "automate project creation" ×2; Profitability "project profitability, and customer profitability" + "Customer Profitability"; Notifications "Invoice INV-0001 … has been paid/sent". LZKC-009 sites 3/4 remain known-deferred (not re-filed) |
| 90.2 | PASS | Create Client (2-step): promoted core fields (Name/Type/Email/Phone/Tax Number/Address/Country) + step-2 BUSINESS DETAILS incl. SA entity types — no generic Custom Fields duplication; Create Matter: promoted Client/Reference Number/Priority/Work Type + integrated Conflict Check — no custom-field surface; New Fee Note: opens client-picker (no custom-field surface). Task dialog: in-cycle evidence Days 21/28/60 (task flows all matter-scoped legal terminology). No promoted-slug regression anywhere this cycle |
| 90.3 | PASS | Full nav (all groups expanded): Matters, Recurring Schedules, Trust Accounting (7 sub-pages: Transactions/Client Ledgers/Reconciliation/Interest/Investments/Trust Reports), Court Calendar, Conflict Check + Adverse Parties, Tariffs, Mandates, Compliance, Fee Notes, Billing Runs, Collections, Pipeline, Engagement Letters, Profitability, Reports, AI Intake/Reviews — all legal modules present, zero accounting/consulting vertical items |
| 90.4 | PASS | Settings > Billing: "Billing — Trial · Manual · Managed Account — Your account is managed by your administrator." Term scan: starter/pro/upgrade/tier/per-seat all 0 hits |
| 90.5 | PASS (post-remediation) | Console listeners across all 25 firm routes post-restart: **zero JS errors** — notably NO dashboard SVG-path error (**LZKC-011 fix holds**) and NO /pipeline hydration mismatch (**LZKC-001 fix holds**). Pre-remediation dompurify errors were environment-only (above) |
| 90.6 | PASS | Mailpit 25 messages; recipients strictly {sipho.portal ×19, moroka.portal ×2, thandi ×2 (invite + verification), bob ×1, carol ×1 (invites)}; Moroka emails only to moroka.portal@. Backend log: zero ERROR lines, zero mail failures/bounces. **LZKC-022 org-scope leg: unexercised this cycle** — no firm-side notification email fired in 90 days (only Day-0 invites/verification; DEAL_WON email N/A per opt-in default, trust approvals in-app) — recorded honestly, no claim made |

## Portal-side regression sweep

| # | Result | Evidence |
|---|--------|----------|
| 90.7 | PASS | All 10 routes walked (`/home /projects /invoices /trust /deadlines /proposals /requests /activity /profile /settings/notifications`): all **HTTP 200**, **0 console errors**, **0 HTTP 5xx** (response listener). /invoices INV-0001 PAID; /trust R 0,00; /requests REQ-0001+0003 COMPLETED |
| 90.8 | PASS (**BLOCKER-severity gate — zero drift from Day 15**) | URL probes (Sipho session): `/projects/{morokaMatter}` → "The requested resource was not found… you may not have access"; `/requests/{morokaReq}` → not found; `/trust/{morokaMatter}` → "No trust balance is recorded…" — all denied, zero content leak. API probes (live Sipho JWT captured from portal→backend Authorization header, sub=d0c7daf5…): `GET /portal/projects/690b8246…` → **404** "No project found"; `/portal/requests/a2452183…` → **404**; `/portal/documents/40d050cc…/presign-download` → **404** — all existence-denying; positive control `/portal/projects/66451e87…` → **200**; `/portal/projects` list → only Sipho's 2 projects (0 moroka/est-2026 refs); `/portal/trust/summary` → only Sipho's matter, balance 0.00 |
| 90.9 | PASS | Final digest = Day-75 digest (`3GqhueqL8RYKTdir8DCNis`): full-HTML scan zero moroka/est-2026/peter/deceased/estate/25 000 refs; content = Sipho's INV-0001 + his 3 trust transactions only |
| 90.10 | PARTIAL (one find → folded into LZKC-031) | Portal vocabulary internally consistent: Matters / Trust / Deadlines / Fee Notes / Engagement Letters / Requests / Activity; no "case file", no stray "engagement"/"task" synonyms in page copy. **One firm-vocabulary leak**: `/profile` shows "Role: **General Customer**" (should be client-facing wording) — included as LZKC-031 site |

## Day 90 day-level checkpoints

- Both regression sweeps pass: **PASS** (firm sweep with LZKC-031 terminology residual, Low; portal sweep with one LZKC-031 site)
- Isolation holds at Day 90 (zero drift from Day 15): **PASS**
- Mailpit clean — no bounced/failed emails either side: **PASS**

## New gaps

- **LZKC-031 (Low)** — Day-90 terminology sweep: residual Project/Customer/Invoice copy sites beyond the surfaces fixed by LZKC-009/021 (full site list in 90.1 + portal profile "General Customer" in 90.10). Same defect class as LZKC-021; a copy-audit sweep, not per-site one-liners.
- **LZKC-032 (Low)** — Billing-run notification copy renders the run's empty name as literal quotes and doesn't pluralize: "Billing run \"\" — 1 invoices sent" / "Billing run \"\" completed — 1 invoices generated" (billing_runs.name is genuinely empty in DB — Day-28 wizard doesn't require one; template should fall back to period/reference and pluralize).

## Exit-checkpoint assessment (E.1–E.16)

| Exit | Status | Note |
|------|--------|------|
| E.1 | PASS | All scenario days (0–90) executed this cycle; every skip logged with rationale (KYC Day 2 mandate exemption; 85.2 conditional not met; PayFast→mock gateway per Day-0 note) |
| E.2 | PARTIAL | All 7+ wow screenshots captured this cycle; visual-regression comparison vs Phase 68 Epic 500B baselines NOT run (no baseline harness wired into this cycle) |
| E.3 | PASS | This cycle's gap report: LZKC-028 (Low), LZKC-029 (Medium), LZKC-030 (Medium), LZKC-031 (Low), LZKC-032 (Low) — **zero BLOCKER, zero HIGH**. Carried-forward LZKC-023…027 also ≤ Medium |
| E.4 | PASS | Tier removal: Settings > Billing (Day 90), team invite flow (Day 0), member/team page (Days 0 + 90) — zero tier vocabulary |
| E.5 | PASS | Field promotion verified Days 2/3/21/28 + Day-90 re-check of Client/Matter/Fee Note dialogs — no CustomFieldSection duplication |
| E.6 | PASS | Progressive disclosure: Day 0 + Day 90 full-nav sweep — 4 legal modules + legal-only extras, no cross-vertical leaks |
| E.7 | PASS | Keycloak flow E2E Day 0 (request-access → OTP → padmin approval → owner register → invites → Bob/Carol) — zero mock IDP |
| E.8 | PASS | Portal magic-link only: Days 4/8/11/15/30/46/61 fresh links or standing JWT sessions; Day-88 mid-day re-auth via fresh magic link honoured redirectTo; Days 75/90 standing sessions — zero Keycloak forms portal-side across all 90 days |
| E.9 | PARTIAL | LZKC-021 fix holds and prior LZKC-009 evidence site fixed, but Day-90 deep scan found residual terminology sites (LZKC-031, Low) + LZKC-009 sites 3/4 known-deferred; portal internally consistent bar one LZKC-031 site |
| E.10 | PASS | **BLOCKER gate**: Day 15 + Day 90 isolation probes pass at list, URL, and API levels; digest Moroka-free; zero drift over the cycle |
| E.11 | PASS | Trust reconciliation identical across firm Section 86 ledger / matter Trust tab / portal `/trust` at Days 11 (R 50 000), 45/46 (R 70 000), 60/61/75/90 (R 0,00) |
| E.12 | PASS (mock gateway) | Day 28 generation + Day 30 payment E2E; webhook chain processed→recorded→reconciled; firm PAID < 60 s; gateway is the dev mock, not PayFast sandbox (documented) |
| E.13 | PASS | Day 60 multi-user gate resolution (Bob trust approval†, REQ-0003 accepts, tasks closed, court date HEARD) → clean-path closure, SoA + closure letter generated, Day-61 portal download. † single-approver per config — see LZKC-029 (scenario/product-default gap, Medium, open) |
| E.14 | PASS | Day 85: matter activity actor filter returns firm users (Thandi/Bob) AND portal contact (Sipho) events across the cycle |
| E.15 | **NOT RUN** (orchestrator-owned) | Full test-suite gate (backend `./mvnw verify`; frontend `pnpm test`/`typecheck`/`lint`; portal `lint && build`) is the cycle-exit merge gate, run by the orchestrator before cycle close — not executed inside this QA session |
| E.16 | PASS (with note) | Single clean pass Days 0–90: **zero dev-fix dispatches mid-loop, zero BLOCKERs**. Note: one infra intervention (pnpm install + frontend restart for post-merge dependency staleness — environment, not a bug fix), and 5 new non-blocking gaps (LZKC-028…032) feed the fix loop |

## Verdict

**All scenario days (0–90) executed and recorded in one pass.** Isolation gate (E.10) holds everywhere at all three levels with zero drift. All prior-cycle fix re-verifications HOLD (LZKC-001/002/003/004/006/007pt1/008/010/011/012pt1/013/014/015/017pt1/018/019/020/021/022-portal-leg). Open work is fix-loop disposition of LZKC-028…032 (all ≤ Medium) + carried-forward items, plus the orchestrator-owned E.15 suite gate and E.2 visual-baseline comparison.
