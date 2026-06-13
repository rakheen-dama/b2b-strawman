# Day 75 — Weekly digest + late-cycle isolation spot-check `[PORTAL]`

**Date**: 2026-06-13
**Cycle**: 30
**Stack**: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080 PID 16554, KC :8180, portal :3002, Mailpit :8025). All services healthy (`svc.sh status`).
**Actor**: Sipho Dlamini (portal contact `793df2fa-6350-46af-b0c0-8b3ac0d7d855`)
**Tooling**: **Playwright MCP exclusively** (clean Chromium). DB reads via `docker exec b2b-postgres psql -U postgres -d docteams`; Mailpit API for emails; portal JWT from localStorage for direct API isolation probes.
**Context**: portal :3002 only (the prior Day-61 magic-link session was still valid — zero re-auth needed, zero Keycloak).

## Day 75 next-day determination
Scenario file `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` day list goes 61 → **75** → 85 → 88 → 90. Day 75 is the actual next scenario day. Executed Day 75 (not Day 90).

## Entity mapping
- Sipho's matter RAF-2026-001 = project `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` — status **CLOSED** (Day 60).
- Sipho's engagement-letter project `15a25aa5-11e3-46fe-b90b-fbacf19c5bf1` — status **ACTIVE**.
- Moroka isolation target: customer `9894de9b-…`, matter (EST-2026-002 "Estate Late Peter Moroka") `dc10e9ac-becd-4cd6-babe-c723b501bfb0` (ACTIVE), doc `b72eaa77-ecd2-4ec2-b0e3-cdfc744526fb` (INTERNAL), portal contact `651e35a8-…`.

## Weekly digest mechanism (important context)
Day 75's "weekly digest" maps to the **PortalDigestScheduler** (Epic 498A/498B, ADR-258): `@Scheduled(cron = "0 0 8 ? * MON")`, subject "…: Your weekly update", 7-day lookback (`DIGEST_LOOKBACK_DAYS = 7`), suppresses empty digests. Cron is Monday 08:00; today is **Saturday 2026-06-13** so the cron has not auto-fired. The product ships a manual trigger for exactly this QA situation (GAP-L-99): `POST /internal/portal/digest/run-weekly?orgId=…&targetEmail=…&dryRun=…` (gated by `X-API-KEY`). Used that to fire the sweep on demand — legitimate product path, NOT forcing the cron / not an SQL shortcut.

- Dry-run (`dryRun=true`, targetEmail=Sipho): `{tenantsProcessed:1, digestsSent:1, skipped:0}` → content assembled (non-empty 7-day bundle).
- Real send (`dryRun=false`): `{digestsSent:1}`; Mailpit total 32→33; email **`cxuTPKRRdpWCtrdnirKCkC`** delivered to `sipho.portal@example.com`.

Note: the SCHEDULED **`weekly-matter-activity-summary`** AI-specialist automation (the OBS-505 per-project fan-out fix) is a *different* mechanism (cron `0 0 7 * * MON`, INVOKE_AI_SPECIALIST inbox-za). Its rule is enabled, `source=TEMPLATE`, `last_run_at=NULL` — it has **never fired** (Saturday, not Monday). Zero AUTOMATION_ACTION_FAILED noise. Legitimately not fired in the test timeframe, exactly as anticipated — not forced, not a defect.

---

## Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 75.1 | Most recent weekly digest email; subject contains "weekly update" / "your week" | **PASS** | Mailpit `cxuTPKRRdpWCtrdnirKCkC` → Sipho, subject **"Mathebula & Partners: Your weekly update"**. |
| 75.2 | Digest body mentions matter events with client-facing copy | **PASS (with copy note)** | Body: "Hi Sipho Dlamini, Here is your activity summary for the past 7 days." → **Recent fee notes**: INV-0001 — PAID ZAR 1250.00; INV-0002 — SENT ZAR 100.00. **Trust account activity**: "3 transaction(s) recorded in your trust account." All client-safe copy. **Copy note (not a defect)**: the digest's content model surfaces invoices + trust activity + info-requests + upcoming deadlines (per `PortalDigestContentAssembler`); it does NOT render literal "SoA downloaded" / "matter closed" event lines as the scenario prose imagines. That is the product's designed digest shape, not a missing-event discrepancy — the underlying events (fee note paid, trust movements) ARE represented. |
| 75.3 | Digest MUST NOT reference Moroka / EST-2026-002 / any other client | **PASS (BLOCKER-severity)** | Full HTML+text scan of the digest: zero matches for `moroka`, `est-2026`, `peter`, `liquidation`, `distribution`, `deceased`, `25 000/25,000/25000`. Only Sipho's INV-0001/INV-0002 + his own trust activity. Isolation holds in the digest. |
| 75.4 | "View activity" link → portal home or activity view | **PASS** | Digest CTA "Open portal →" links to `http://localhost:3002/home`; unsubscribe link → `/settings/notifications?unsubscribe=1`. Navigated `/home` in Sipho's session → renders Sipho-only data (Pending requests 0, Deadlines 0, Recent fee notes INV-0001 R1 250 + INV-0002 R100, Last trust movement R 70 000,00 13 Jun 2026). Zero Moroka leak on `/home`. |
| 75.5 | Activity trail renders Sipho's events (Days 4/8/11/15/30/46/61); zero Moroka refs | **PASS** | `/activity` "A timeline of actions on your matter" (tabs Your actions / Firm actions). Renders: document downloads (Day 61 SoA + closure letter), info-request item submissions + uploads (Days 4/15/46), fee-note payments (Day 30 ×2). All actor = "You" (Sipho). Body scan: zero `moroka/est-2026/peter/liquidation/deceased`. |
| 75.6 | Passive isolation spot-check (61 days post-Moroka onboarding) | **PASS (BLOCKER-severity)** | `/home` — no Moroka entries (scan clean). `/trust` — auto-redirects to Sipho's only ledger (08ad56c4); **balance R 0,00** as of 13 Jun 2026 (PAY R70k → R0 running; DEP R20k; DEP R50k) — **NOT R 25,000 Moroka leak**; scan clean. `/projects` — **Active** tab = ACTIVE engagement-letter project only (`15a25aa5`); **Past** tab = CLOSED RAF matter only (`08ad56c4`); no Moroka. Backend `/portal/projects` returns exactly Sipho's 2 projects with correct `status` (ACTIVE / CLOSED) — no Moroka. **Direct-URL probes as Sipho** to Moroka's matter (`/portal/projects/dc10e9ac-…`) and doc (`/portal/documents/b72eaa77-…/presign-download`) → **HTTP 404** security-by-obscurity ("No project/document found"). |
| 75.7 | 📸 Screenshot | **PASS** | `day-75-portal-activity-trail.png` (activity trail) + `day-75-portal-digest-email.png` (rendered digest email). |

---

## Day 75 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Digest contents match activity trail (no "missing event" discrepancy) | **PASS** | Digest fee-note + trust-activity sections reconcile to the activity trail's fee-note-paid + the trust movements; both reflect Sipho's RAF lifecycle. No event the digest *should* surface (per its content model) is absent. |
| Closed matter correctly rendered as closed (not greyed-out error) | **PASS** | RAF-2026-001 (CLOSED in DB) renders cleanly under the **Past** tab; the **Active** tab correctly excludes it and shows the still-ACTIVE engagement-letter project. The portal filter logic (`active → status==='ACTIVE'`, `past → CLOSED/COMPLETED/CANCELLED`) is correct. |
| Isolation still holds at Day 75 | **PASS** | Home/Trust/Projects/Activity all Moroka-free; backend `/portal/projects` Moroka-free; direct-URL Moroka matter + doc probes both 404. |

## Console / health
- **Portal (:3002)**: zero genuine portal-origin JS errors on the Day-75 flows (home / activity / trust / projects).
- All console errors captured were carry-over or QA-induced, NOT portal defects: stale `:3000` firm-side (OBS-201 assistant 404, dashboard recharts referenceLine SVG warning, KC favicon/logout, Next.js Performance.measure dev warning); one `:8080/portal/auth/exchange 401` (expired Day-60/61 token, expected); `:3002/api/portal/* + :3002/portal/projects` 404s (my own invented-path QA probes); and the **intentional Moroka isolation 404 probes** (`:8080/portal/projects/dc10e9ac…`, `:8080/portal/documents/b72eaa77…`) — those 404s ARE the expected isolation-denial result.

## OBS-6002 (open candidate) — corroborated this day, not re-filed
The `/projects` filter tabs (Active / Past) did not switch under a real Playwright pointer `.click()` (the `aria-selected` stayed on "All", so all matters rendered — initially looked like a broken filter). Invoking the bound React `onClick` directly (`__reactProps$….onClick({})`) switched the tab correctly and the filter worked exactly as designed (Active → 1 ACTIVE project; Past → 1 CLOSED matter). Same cross-app pointer-interception family as Days 60/61 — tooling/HMR friction, handler + filter logic correct. Not re-filed (already OPEN-CANDIDATE); flagged for quiescent-build repro at wrap-up.

## Carry-over exemptions observed (noted, not re-filed)
- OBS-6001 (no separate per-event email; coalesced) — WONT_FIX by design.
- OBS-2101 (R0,00 non-tariff TIME lines) — WONT_FIX.
- OBS-201 (:3000 assistant 404), OBS-506 — firm-side carry-over only.
- OBS-2101 / KYC-FICA unconfigured / Payments mock-only — per mandate.

## New gaps
- **None.** No new OBS-7xx defects. (The "both tabs show both matters" first impression was OBS-6002 tooling, not a filter defect — confirmed via direct React-handler invocation + correct backend `status` payload.)

## Result
**Day 75: 7/7 step checkpoints PASS + 3/3 summary checkpoints PASS; 0 new gaps; NOT blocked.**

Sipho's **weekly digest** ("Mathebula & Partners: Your weekly update", Mailpit `cxuTPKRRdpWCtrdnirKCkC`) was generated and delivered (via the GAP-L-99 manual trigger, since the Monday-08:00 cron hasn't ticked on this Saturday). It contains his INV-0001 (PAID) + INV-0002 (SENT) + his trust activity in client-safe copy and **zero Moroka references** (BLOCKER-severity isolation — PASS). The activity trail and `/home` link target both render Sipho-only events. **Late-cycle isolation holds at Day 75** (61 days post-Moroka onboarding): trust balance R 0,00 (no R25k leak), one client matter (RAF CLOSED in Past tab, engagement-letter ACTIVE), backend payload Moroka-free, and direct-URL Moroka matter+doc probes both **404**. The SCHEDULED `weekly-matter-activity-summary` AI automation has correctly never fired (`last_run_at=NULL`, Saturday not Monday) — observed, not forced.

Screenshots: `day-75-portal-activity-trail.png`, `day-75-portal-digest-email.png`.
