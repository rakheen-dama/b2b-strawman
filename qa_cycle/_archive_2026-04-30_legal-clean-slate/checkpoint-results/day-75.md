# Day 75 — Weekly digest + late-cycle isolation spot-check

## Day 75 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Tooling**: plugin Playwright (`mcp__plugin_playwright_playwright__*`) for browser-driven portal navigation; magic-link self-service via portal `/login` (per L-72 deferral); Mailpit `GET /api/v1/messages` + `/api/v1/search` for digest-email lookup (only legitimate REST use — same path as cycle-1 Day 61); Docker `psql` exec on `b2b-postgres` (DB=`docteams`) for read-only DB state confirmation. Zero SQL/REST mutations.

### Pre-flight DB / scheduler state

- `tenant_5039f2d497cf.projects` for Sipho's customer (`Sipho Dlamini`): 3 matters — `e788a51b-… Dlamini v Road Accident Fund (CLOSED)`, `af9b14b2-… L-37 Regression Probe (ACTIVE)`, `db5ff54a-… L-37 Conveyancing Probe (ARCHIVED)`. All linked to customer `Sipho Dlamini`. **No Moroka cross-link.**
- Moroka matter `89201af5-f6e0-4d9a-952e-a2af6e5b70ee Estate Late Peter Moroka` exists in `projects` (status=ACTIVE) — linked to a separate customer; verifying Sipho cannot see it.
- Portal contacts: `127d1c7d-… Sipho Dlamini sipho.portal@example.com` and `881e5f2f-… Moroka Family Trust moroka.portal@example.com` — distinct, isolated.
- `PortalDigestScheduler.scheduledRun()` cron `0 0 8 ? * MON` (Monday 08:00 SAST). Today is **Sunday 2026-04-26 00:48 SAST**. Next scheduled fire: ~**31 hours from now**. **No manual REST/admin trigger exists** for the digest sweep (`grep -r "runWeeklyDigest" backend/src/main` yields only the scheduled annotation + the public method called by the cron entry point itself; no `Controller`/`Endpoint` exposes it). Backend log `.svc/logs/backend.log` shows zero `digest sweep` / `digest sent` lines.
- Mailpit search for "weekly update" / "Your week" returns 0 hits (`/api/v1/search?query=weekly+update` → `count: 0`). 11 historical emails to `sipho.portal@example.com` (latest is the 22:36 magic-link send by this verify session); zero are weekly-digest sends.

### Per-checkpoint table

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **75.1** | Mailpit → open most recent weekly digest email for `sipho.portal@example.com` (subject contains "weekly update" / "your week") | **DEFERRED — environmental limitation** — `PortalDigestScheduler` is cron-driven only (`0 0 8 ? * MON`); no manual/admin trigger exists. Today is Sunday, next fire ~Mon 08:00 (31h). No digest in Mailpit. **Not a gap** — the scheduler is correctly wired (Phase 68, ADR-258); a verify-cycle limitation that the trigger cannot be invoked on demand. **NEW informational OBS-Day75-NoManualDigestTrigger** (Sprint 2 nice-to-have: expose `/internal/portal/digest/run-weekly` admin/dev-profile endpoint for QA + on-demand testing). | n/a |
| **75.2** | Digest body mentions matter events (fee note paid, SoA downloaded, matter closed) using client-facing copy | **DEFERRED** — gated on 75.1. Static analysis: `PortalDigestContentAssembler.assemble(contactId, 7)` builds a 7-day rollup from PortalEvent projections; templates at `backend/src/main/resources/templates/email/portal-weekly-digest.html` use loop sections (recent activity, invoices, requests). Subject template = `${orgName}: Your weekly update` (per `PortalDigestScheduler:210`). | n/a |
| **75.3** | Digest MUST NOT reference Moroka / EST-2026-002 / any other client | **DEFERRED** — gated on 75.1. Defensible by code-path: `PortalDigestContentAssembler.assemble(contactId, …)` is per-`PortalContact.id`; tenant-scoped via ScopedValue + per-contact `PortalEvent` filters. Cross-contact leak would require a Hibernate filter bypass, not present in scheduler/assembler grep. | n/a |
| **75.4** | Click "View activity" link in digest → lands on portal home or activity view | **DEFERRED** — gated on 75.1. Code: `PortalDigestScheduler:212` sets `context.put("portalHomeUrl", portalBaseUrl + "/home")`; CTA in template links to `${portalHomeUrl}`. Renders the home dashboard (verified working in 75.6). | n/a |
| **75.5** | Activity trail renders events from Days 4, 8, 11, 15, 30, 46, 61 — all are Sipho's. Zero Moroka references. | **PARTIAL — no portal-wide activity trail surface exists** — `/profile` shows contact info only (Name, Email, Role, Customer); `/home` shows tile dashboard (pending requests count, deadlines count, recent invoices, last trust movement). No timeline/activity-feed component on the portal as of today. **NEW informational OBS-Day75-NoPortalActivityTrail** (expected to surface as a gap in Day 88.3-88.5 portal activity trail review). Defer to Sprint 2 / Day 88 dispatch. **What IS verifiable on isolation grounds**: `/home` shows ONLY Sipho's invoices (INV-0001 R 1 250, INV-0002 R 100); zero Moroka entries. | `day-75-cycle1-portal-home.yml`, `day-75-cycle1-portal-profile.yml` |
| **75.6a — `/home` no Moroka entries** | Magic-link self-service via portal `/login?orgId=mathebula-partners` → token `Q9oxUoLn2nlnyb-1YPbgWd_KG-2aOY5BMWf1XXOGkQI` extracted from inline dev-mode link → `/auth/exchange` → `/home` | **PASS** — Home dashboard tiles: "Pending info requests: 3" (Sipho's existing requests), "Upcoming deadlines: 0", Recent invoices = INV-0001 R 1 250,00 + INV-0002 R 100,00 (both Sipho-only), "Last trust movement: No recent activity" (cosmetic — endpoint returning 404, see console-error obs below). **Zero Moroka references anywhere on /home.** | `day-75-cycle1-portal-home.yml` |
| **75.6b — `/trust` shows residual against RAF only** | Navigate to `/trust` → auto-redirected to `/trust/e788a51b-…` (single-matter view since Sipho only has trust against one matter) | **PASS** — Trust balance card: **R 0,00** (correct: refunded on Day 60 closure). Transactions list: 3 entries — `25 Apr 2026 REFUND R 70 000,00 → R 0,00` ("Refund full residual trust balance to client on matter closure (RAF-2026-001) — L-69 fix verification"), `25 Apr 2026 DEPOSIT R 20 000,00 → R 70 000,00` ("Top-up per engagement letter — RAF-2026-001"), `25 Apr 2026 DEPOSIT R 50 000,00 → R 50 000,00` ("Initial trust deposit — RAF-2026-001"). All 3 transactions explicitly reference RAF-2026-001. **Zero R 25,000 Moroka leak.** Statements section empty (informational; SoA `document_id` resolved fine in cycle-1 Day 61 walk via Documents tab). | `day-75-cycle1-portal-trust.yml`, `day-75-cycle1-portal-trust-balance.png` |
| **75.6c — `/projects` shows RAF as CLOSED** | Navigate to `/projects` | **PARTIAL — see scope clarification** — Matters list shows 3 matter cards: "L-37 Conveyancing Probe", "L-37 Regression Probe", "Dlamini v Road Accident Fund". **DB-confirmed all 3 belong to customer `Sipho Dlamini`** (`projects.customer_id` joins to `customers.name='Sipho Dlamini'`); no Moroka cross-link. The L-37 probes are scenario-incidental regression-test artifacts (created during L-37 verify cycle by tooling), NOT isolation leaks. Scenario 75.6 expectation "one matter only (RAF-2026-001)" is technically violated by these test-artifact matters but isolation invariant (no cross-customer leak) holds. **No "Past" tab grouping for closed matters** (matters list is flat — closed matter shown alongside active ones with no visual differentiation other than the in-detail CLOSED badge). **NEW informational OBS-Day75-NoPastTabForClosedMatters** (Sprint 2 polish — already noted as L-73-followup in earlier triage by Product). **Click into Dlamini matter** → matter detail header renders status badge **CLOSED** (L-73 fix still working from Day 61 re-walk). | `day-75-cycle1-portal-projects.yml`, `day-75-cycle1-portal-matters-list.png`, `day-75-cycle1-portal-matter-detail.yml`, `day-75-cycle1-portal-matter-detail-closed.png` |
| **75.7** | Optional screenshot `day-75-portal-digest-plus-activity.png` | **N/A** (digest deferred per 75.1) — captured isolation-spot-check screenshots instead: `day-75-cycle1-portal-{matters-list,trust-balance,matter-detail-closed}.png`. | as named |
| **Console errors** | `browser_console_messages level=error` over 6 portal navigations + 1 magic-link exchange | **PARTIAL — pre-existing 404s** — 2 errors on `/home`: `GET http://localhost:8080/portal/trust/movements?limit=1 → 404`. The portal "Last trust movement" tile fetches a non-existent endpoint. **NEW informational OBS-Day75-PortalTrustMovementsEndpoint404** (LOW — cosmetic, tile renders "No recent activity" gracefully). Sprint 2 followup: either implement the endpoint or remove the tile. All other portal pages (login, exchange, projects, trust, profile, matter-detail) → 0 errors. | console log |

### Decision

**Day 75 — PARTIAL COMPLETE.** Late-cycle isolation invariant **HOLDS** end-to-end (75.6 a/b/c all PASS on isolation grounds): Sipho sees zero Moroka data on `/home`, `/trust`, `/projects`, or matter detail. Trust balance correctly R 0,00 reconciling to the Day-60 refund-out. Closed matter renders CLOSED badge in detail header (L-73 still working). Weekly digest sub-flow (75.1-75.4) is **DEFERRED** with environmental rationale (cron-only trigger, next fire Monday 08:00 SAST, no admin REST endpoint exists). Activity-trail surface (75.5) does not exist in current portal build — deferred to Day 88 dispatch.

**Headline outcome**: Late-cycle isolation passes; digest path is structurally wired (scheduler + content assembler + email template all in code) but cannot be invoked on demand for verification this cycle. Three NEW informational observations (none HARD blockers); two pre-existing observations reconfirmed.

### NEW observations (informational, NOT new gaps)

- **OBS-Day75-NoManualDigestTrigger (informational, Sprint 2):** No admin/dev-profile REST endpoint to invoke `PortalDigestScheduler.runWeeklyDigest()` on demand. Cron-only fire (Monday 08:00). Suggested S-fix (~30 min): expose `POST /internal/portal/digest/run-weekly` (API-key gated, like other `/internal/*` endpoints) OR add to dev-portal harness (`@Profile({"local","dev"})`) so QA can verify the digest path without waiting for Monday's cron.
- **OBS-Day75-NoPortalActivityTrail (informational, expected to gap-out Day 88.3-88.5):** No portal `/activity`, `/timeline`, or activity-feed surface. Profile shows contact-info only; home shows tile dashboard. Scenario step 88.3 ("activity trail on `/home` or `/profile`") expects this surface — will become an explicit gap when Day 88 dispatches. Defer.
- **OBS-Day75-NoPastTabForClosedMatters (informational, L-73-followup Sprint 2):** Matters list (`/projects`) is flat — no "Past" or "Closed" tab grouping. Closed matter renders CLOSED badge ONLY in detail header. Per scenario 75.6 ("verify it either shows as closed or moves to a 'Past' tab"). Already triaged by Product as L-73-followup.
- **OBS-Day75-PortalTrustMovementsEndpoint404 (informational, LOW):** `/home` "Last trust movement" tile fetches `GET http://localhost:8080/portal/trust/movements?limit=1` → 404. UI degrades gracefully (renders "No recent activity"). Either implement the endpoint or remove the tile.

### Reconfirmed (pre-existing) observations

- **OBS-Day61-NoPortalActivityTrail / OBS-Day61-NoPortalDocAuditEvent**: still applicable on Day 75 (no portal-side audit emit; no activity-trail surface).

### NEW gaps opened in Day 75 walk

(none — all observations above are deferred to Sprint 2 / Day 88, and isolation invariant holds for the spot-check)

### Time

~12 min wall-clock (mostly portal navigation + DB confirms + Mailpit lookup), well under 45 min target.

---

## Day 75 Walk — Cycle 54 — 2026-04-28 SAST

**Branch**: `bugfix_cycle_2026-04-26-day75` (cut from main `c419f2c7` Day 62 skip-PR)
**Tooling**: Playwright MCP (`mcp__playwright__*`) for portal navigation; Mailpit `GET /api/v1/messages` for digest-email lookup; read-only `psql` for DB sanity. Zero SQL/REST mutations.
**Tenant**: `tenant_5039f2d497cf` (mathebula-partners)
**Portal actor**: Sipho Dlamini (`sipho.portal@example.com`, portal_contact `f3f74a9d-3540-483a-80bc-6f5ef4e911bb`, customer `c4f70d86-c292-4d02-9f6f-2e900099ba57`)

### Pre-flight

| Service | Port | Status |
|--------|------|--------|
| backend | 8080 | 200 (`/actuator/health`) |
| frontend | 3000 | 200 |
| portal | 3002 | 307 healthy |
| Mailpit | 8025 | 200 |

Backend uptime: ~12h 11m (PID 53170, started 2026-04-27 ~23:40 SAST). Day-of-week today: **Tuesday** (`date +%u` = 2).

Magic link issued → Mailpit `GfcMUMJGdRkb4jv7U9Zjh3` (2026-04-28T09:56:12Z) → token exchange → Sipho landed on `/projects`.

### Per-checkpoint table — Cycle 54

| Step | Result | Evidence |
|------|--------|----------|
| **75.1** Mailpit weekly digest email | **BLOCKED** — see GAP-L-99. Mailpit search `subject:digest`, `subject:weekly`, `subject:week` all return 0 hits. To-Sipho mailbox total = 3 emails (2 magic-link + 1 Day 60 SoA notification); zero digest emails ever delivered. Backend uptime started ~23:40 Mon 27th, AFTER Mon 08:00 cron tick — scheduler has not fired during this backend's life. No public REST trigger exists (`grep -rn runWeeklyDigest backend/src/main` confirms zero callers outside `scheduledRun()` and integration tests). | n/a (negative result) |
| **75.2** Digest body mentions matter events | **BLOCKED** (75.1) | n/a |
| **75.3** Digest must NOT reference Moroka | **BLOCKED** (75.1) | n/a |
| **75.4** "View activity" link | **BLOCKED** (75.1) | n/a |
| **75.5** Activity trail (Sipho only, zero Moroka) | **PASS** with cosmetic note (raw event keys still rendered in some rows). `/activity` "Your actions" tab: Document downloaded ×2 (Day 61 SoA + closure letter, 1h ago); `portal.request_item.submitted` + `portal.document.upload_initiated` runs (Days 4/45 FICA + medical evidence). "Firm actions" tab: `statement.generated`, `document.generated`, `information_request.*`, `disbursement.*`, `time_entry.*`, `court_date.created` — all by Bob/Thandi on RAF-only. `grep -ic 'moroka\|EST-2026\|0cb199f2\|340c5bb2'` on both YAML snapshots → **0 matches**. | `cycle54-day75-75.5-portal-activity.yml`, `cycle54-day75-75.5b-portal-activity-firm.yml` |
| **75.6a** `/home` no Moroka entries | **PASS**. Tiles: Pending info requests = 2 (REQ-0001 + REQ-0004 SENT, both RAF), Upcoming deadlines = 0, Recent fee notes INV-0001 R 5 160,00 (Sipho's), Last trust movement R 20 000,00 27 Apr 2026 (Day 45 RAF top-up). Zero Moroka references. | `cycle54-day75-75.6b-portal-home.yml` |
| **75.6b** `/trust` RAF-only balance, NO R 25 000 Moroka leak | **PASS**. `/trust` redirected to `/trust/cc390c4f-…`; balance = R 70 100,00; 3 deposit rows all RAF (R 50k Day 10 + R 100 cycle-29 retest + R 20k Day 45 top-up). DB cross-check: 4 trust_transactions in tenant — 3 RAF (R 50k/100/20k) + 1 Moroka (R 25k EST-2026-002, project `340c5bb2-…`, customer `0cb199f2-…`). Sipho's portal correctly excludes the Moroka row. | `cycle54-day75-75.6c-portal-trust.yml` |
| **75.6c** `/projects` shows RAF-only matter as CLOSED | **PARTIAL**. `/projects` All tab shows 2 matters: `Cycle19 Verify` (ACTIVE) + `Dlamini v Road Accident Fund` (CLOSED). Past tab: RAF only. Active tab: `Cycle19 Verify` only. DB: 3 projects total — 2 Sipho (RAF CLOSED + Cycle19 Verify ACTIVE) + 1 Moroka (`Estate Late Peter Moroka`, customer `0cb199f2-…`). **Moroka NOT visible** — isolation holds. Scenario expectation "one matter only" is violated by the residual `Cycle19 Verify` test artifact (BUG-CYCLE26-07 verification leftover) — NOT an isolation issue, but a QA-test-data hygiene issue. Closed status correctly hidden behind Past tab. | `cycle54-day75-75.6a-portal-projects.yml`, `cycle54-day75-75.6d-portal-projects-past-tab.yml`, `cycle54-day75-75.6e-portal-projects-active-tab.yml` |
| **75.7** Optional screenshot | **N/A** — digest portion BLOCKED; activity portion captured via YAML evidence. Skipped per "PNG only when visual layout is the assertion" rule. | n/a |
| **Console errors** | `/auth/exchange` 1× favicon 404 (cosmetic, ignored). `/projects`, `/home`, `/trust`, `/trust/cc390c4f-…`, `/activity` — 0 errors. | console logs |

### Day 75 Cycle 54 checkpoint summary

- [x] **Digest contents match activity trail** — BLOCKED on GAP-L-99 (no digest ever delivered).
- [x] **Closed matter correctly rendered as closed** — PASS (RAF appears in Past tab; CLOSED in DB).
- [x] **Isolation still holds at Day 75** — PASS (zero Moroka leakage anywhere on Sipho's portal).

### Counts (Cycle 54)

- **PASS**: 4 (75.5 ×2 tabs, 75.6a, 75.6b)
- **PARTIAL**: 1 (75.6c — extra Sipho test-artifact matter, not isolation)
- **BLOCKED**: 4 (75.1, 75.2, 75.3, 75.4 — gated on weekly-digest send)
- **N/A**: 1 (75.7 optional screenshot)

### New gap opened (Cycle 54)

**GAP-L-99** (MEDIUM, OPEN, owner=product) — `PortalDigestScheduler` cron-only trigger (`0 0 8 ? * MON`) with no public REST trigger or admin UI button blocks Day 75 walks whenever the backend was started or restarted after the most recent Monday 08:00 tick. Walk-blocker for the digest portion (75.1–75.4) of the lifecycle scenario.

This complements the prior cycle 1 OBS-Day75-NoManualDigestTrigger note (which was deferred-Sprint-2). Day 75 has now been walked twice (cycle 1 → DEFERRED, cycle 54 → BLOCKED) and the scenario stays unwalkable end-to-end. Recommended fix: expose `POST /internal/portal/digest/run-weekly` (API-key gated) or `POST /api/admin/portal/digest/run-now` (owner-only via @RequiresCapability). S effort, ~30 min.

### Anomalies / observations

1. **Weekly digest never fires in QA**: cron requires backend liveness at Mon 08:00 server time + ≥1 digest-eligible event in 7-day lookback for ≥1 ACTIVE portal contact. Backend restarts (frequent in QA cycles — every Java change) routinely miss the cron window. Without a manual trigger, the scenario step is unfulfillable.
2. **Residual cycle-19 matter** (`Cycle19 Verify`, ACTIVE, Sipho's customer) — leftover from BUG-CYCLE26-07 verification flow. Causes 75.6c PARTIAL but not an isolation issue.
3. **Day 60 trust withdrawal row missing** — Day 60 fee-note paid R 15 000 was not recorded as a FEE_TRANSFER row in `trust_transactions`. Portal `/trust` shows R 70 100 (3-deposit sum) rather than the R 55 100 residual that the closure SoA narrative implies. Pre-existing Day 60-era concern, not Day 75 scope.
4. **Activity-feed raw event keys** (e.g. `portal.request_item.submitted`, `statement.generated`, `information_request.sent`) — only `information_request.item_accepted` has humanised copy per BUG-CYCLE26-10. Outside Day 75 scope but flagged.

### References

- Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` §Day 75 (lines 721–741)
- Scheduler: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java`
- Cadence settings UI: `frontend/components/settings/portal-settings-section.tsx`

---

## Cycle 54 Retest (PR #1201 on main) — 2026-04-28 SAST

**Branch**: `bugfix_cycle_2026-04-26-day75-retest` cut from `main` `5e9cb58f` (PR #1201 squash-merge — manual digest trigger + Day 75 walk results).
**Tooling**: `curl` against new `POST /internal/portal/digest/run-weekly` endpoint (legitimate REST surface — like Mailpit API); Mailpit `GET /api/v1/messages` + `GET /api/v1/message/{id}` for digest body inspection; `grep` for isolation-invariant checks; Playwright MCP for §75.4 (locked — see anomaly #1).
**Backend**: fresh JVM serving main `5e9cb58f` (~27s warmup at retest start; healthy `UP`).
**Internal API key**: `local-dev-api-key-change-in-production` (X-API-KEY header).

### Endpoint behavior matrix

| Test | Request | Expected | Actual | Result |
|------|---------|----------|--------|--------|
| Auth — missing key | `POST …/run-weekly?targetEmail=sipho.portal@example.com` (no header) | 401 | **HTTP/1.1 401** | **PASS** |
| Auth — invalid key | `POST …` `X-API-KEY: bogus-key-12345` | 401 | **HTTP/1.1 401** | **PASS** |
| DryRun | `POST …?targetEmail=sipho.portal@example.com&dryRun=true` `X-API-KEY: local-dev-api-key-change-in-production` | response indicates would-have-been-sent; Mailpit unchanged | **`{"tenantsProcessed":4,"digestsSent":1,"skipped":0,"dryRun":true,"errors":[]}`** + Mailpit total 4 → 4 | **PASS** |
| Real send | `POST …?targetEmail=sipho.portal@example.com` `X-API-KEY: local-dev-api-key-change-in-production` | response indicates digest sent; Mailpit gains 1 message | **`{"tenantsProcessed":4,"digestsSent":1,"skipped":0,"dryRun":false,"errors":[]}`** + Mailpit total 4 → 5 | **PASS** |

### Per-checkpoint table — Cycle 54 retest

| Step | Result | Evidence |
|------|--------|----------|
| **75.1** Mailpit weekly digest email; subject contains "weekly update" | **PASS** — Mailpit message `XuTUtmtbAK3nvAqcGSge2H` delivered to `sipho.portal@example.com` 2026-04-28T11:38:37.861Z. Subject: **"Mathebula & Partners: Your weekly update"** (literal scenario phrase match). | `cycle54-retest-PR1201-GAP-L-99-digest-message.json`, `cycle54-retest-PR1201-GAP-L-99-step4-real-send.json` |
| **75.2** Body references Sipho's RAF activity (fee note, SoA, matter closure) | **PASS** — body greets "Hi Sipho Dlamini,"; "Recent fee notes" section lists **INV-0001 PAID ZAR 5160.00** (Sipho's only invoice, paid Day 60); "Information requests" section: "You have 3 open information request(s)" (REQ-0001, REQ-0004, REQ-0005 — all Sipho's per cycle 54 walk); "Trust account activity" section: "3 transaction(s) recorded in your trust account" (R 50k Day 10 + R 100 cycle-29 + R 20k Day 45 — all RAF-2026-001 deposits). All references use client-facing copy. CTA `Open portal →` href = `http://localhost:3002/home`. | `cycle54-retest-PR1201-GAP-L-99-digest-body.txt`, `cycle54-retest-PR1201-GAP-L-99-digest-body.html` |
| **75.3** Digest must NOT reference Moroka / EST-2026-002 / any other client | **PASS — isolation invariant HOLDS** — `grep -ic` on both text and HTML body: `moroka`=0, `EST-2026`=0, `estate`=0, `25 000`/`25,000`/`25000`=0 (Moroka's R 25 000 trust deposit). Only client name in body is "Sipho Dlamini" (greeting). | `cycle54-retest-PR1201-GAP-L-99-summary.txt` §Step 6 |
| **75.4** Click digest CTA → land authenticated on portal `/home` | **DEFERRED-ON-ENVIRONMENT** — Playwright MCP user-data-dir `/Users/rakheendama/Library/Caches/ms-playwright/mcp-chrome-5d273ba` held by foreign claude session (PID 4237 etime ~47h+, Chromium PID 25373 alive `--user-data-dir=…/mcp-chrome-5d273ba`). Identical pattern to **GAP-L-98** (RESOLVED in cycle 52 by orchestrator unblock). All routing wiring verified: magic-link issued (token `i2nybOZ_IMD2QJpAaZfvvvP1lIO87ouTRkiazEYu_d8`, Mailpit `9JYVpY7zQpbtWkyG9XDN57`); `GET /auth/exchange` returns 200 (37 KB rendered "Verifying" UI); `GET /home` returns 200 (49 KB rendered); digest CTA href = `http://localhost:3002/home` (verified via grep on emitted HTML). Browser-level authenticated /home render was already established in cycle-1 day-75 §75.6a (2026-04-25, `day-75-cycle1-portal-home.yml`). NOT a product gap; orchestrator-resolvable infra. | `cycle54-retest-PR1201-GAP-L-99-summary.txt` §Step 7 |

### Decision

**GAP-L-99 fix VERIFIED on main.** All four endpoint contract tests pass: 401 missing key, 401 bad key, dryRun honored (no email send + correct response), real send produces exactly 1 digest in Mailpit per call. Response shape `{tenantsProcessed, digestsSent, skipped, dryRun, errors[]}` matches PR #1200 spec. Cron path untouched.

**§75.1, §75.2, §75.3 PASS on main** — digest email arrives, content references Sipho's actual RAF activity (INV-0001 PAID, 3 open info requests, 3 trust deposits), isolation invariant holds (zero Moroka / EST-2026 / R 25 000 leakage in either text or HTML body).

**§75.4 DEFERRED-ON-ENVIRONMENT** — Playwright MCP locked by orphan foreign claude session. Routing-level evidence sufficient (CTA href correct, /auth/exchange + /home both 200, magic-link path works); browser-walk authenticated home render previously established cycle 1.

### Anomalies / observations

1. **OBS-Cycle54-PlaywrightLockedByForeignSession** (informational, infra) — `mcp-chrome-5d273ba` SingletonLock points to live PID 25373; foreign `claude --dangerously-skip-permissions --chrome` session (PID 4237, etime ~47h+) holds it. `mcp__playwright__browser_navigate` returns "Browser is already in use … use --isolated to run multiple instances of the same browser". Not a product defect; same precedent as GAP-L-98 (cycle 52 — resolved by orchestrator killing the foreign session). Logged here to inform orchestrator that the QA cycle would benefit from one of: (a) periodic stale-lock sweep before dispatching browser-using QA agents, (b) `--isolated` flag on the Playwright MCP launcher, (c) a hook that detects stale locks and unlinks them.
2. **`tenantsProcessed: 4` on a single-org test** — endpoint sweeps all tenants and applies `targetEmail` filter at the contact level. `digestsSent: 1` confirms exactly Sipho's portal contact was matched and emailed. Working as designed (see PR #1200 spec).
3. **Pre-existing trust-balance anomaly** (Day 60 R 15 000 fee-transfer not in `trust_transactions`) — out of GAP-L-99 retest scope; flagged in cycle-54 walk anomaly #3, still pre-existing.

### References

- Endpoint: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalController.java`
- Scheduler refactor: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java`
- Spec: `qa_cycle/fix-specs/GAP-L-99.md`
- Fix PR: #1200 (squash `c1c7afd7` cycle-branch) → #1201 (squash `5e9cb58f` main)
- Retest evidence: `qa_cycle/checkpoint-results/cycle54-retest-PR1201-GAP-L-99-*.{json,txt,html}`

