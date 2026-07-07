# Day 90 — Final regression + exit sweep `[FIRM]` + `[PORTAL]` — 2026-07-06

**Actors**: Thandi (firm :3000), Sipho (portal :3002).

## Firm-side regression sweep

| # | Result | Evidence |
|---|--------|----------|
| 90.1 | PARTIAL → **LZKC-021** (+ LZKC-009 evidence) | Walked 16 firm routes + full nav. Core surfaces clean: Matters / Clients / Fee Notes / Engagement Letters / Pipeline / Trust Accounting / Court Calendar / Conflict Check / Tariffs — zero Project/Customer/Invoice in page copy. **Leaks found**: My Work subtitle "Your tasks and time tracking **across all projects**"; Calendar subtitle "View upcoming due dates **across all projects**" + "**All Projects**" filter + "**Projects**" filter tab (→ LZKC-021). Create Client dialog: "Tax Number (required to send an **invoice**; collectable later)" (added as evidence to LZKC-009) |
| 90.2 | PASS (in-cycle evidence) | Create Client dialog re-opened: step 1 = promoted core fields (Name/Type/Email/Phone/Tax Number/Address/Country), no generic Custom Fields duplication; step-2 SA-legal promoted fields (incl. ID number) verified this cycle on Day 2; matter-create promoted fields Day 3; New Fee Note opens client-picker (no custom-field surface). No promoted-slug regression into CustomFieldSection observed anywhere this cycle |
| 90.3 | PASS | Nav (all groups expanded): Matters, Trust Accounting (7 sub-pages), Court Calendar, Conflict Check + Adverse Parties, Tariffs, Mandates, Compliance, Fee Notes, Billing Runs, Pipeline, Engagement Letters — all legal modules present; zero accounting/consulting vertical items |
| 90.4 | PASS | Settings > Billing: "Billing — Trial · Manual · Managed Account — Your account is managed by your administrator." No tier/upgrade/seat vocabulary (scan: starter/pro/upgrade/tier/per-seat all absent) |
| 90.5 | PASS (known-gap noise only) | Console across 16-route walk: only dashboard SVG path error (LZKC-011, known) + /pipeline hydration mismatch (LZKC-001, known) + 404s from two QA-guessed URLs (`/conflicts`, `/tariffs` — real routes are `/conflict-check`, `/legal/tariffs`; not product errors). Zero new JS errors |
| 90.6 | PASS | Mailpit: 26 messages total, recipients strictly {sipho.portal ×20, moroka.portal ×2, thandi ×2, bob ×1, carol ×1}; Moroka emails only to moroka.portal@. Backend log: **0 ERROR lines**, digest sweep "0 errors", no mail failures/bounces |

## Portal-side regression sweep

| # | Result | Evidence |
|---|--------|----------|
| 90.7 | PASS | All 10 routes walked (`/home /projects /invoices /trust /deadlines /proposals /requests /activity /profile /settings/notifications`): all render, **0 console errors** (favicon 404 excluded as env noise), **0 HTTP 5xx**. /requests shows REQ-0003 COMPLETED 2/2; /invoices INV-0001 PAID; /trust R 0,00 |
| 90.8 | PASS (**BLOCKER-severity gate**) | Re-ran Day 15 probes vs Moroka IDs. URL probes: `/projects/54baf135…` → "The requested resource was not found… you may not have access"; `/requests/6b6b6b7d…` → not found; `/trust/54baf135…` → "No trust balance is recorded… not found". API probes (Sipho JWT from `portal_jwt`): `GET /portal/projects/{moroka}` → **404** "No project found"; `/portal/requests/{moroka}` → **404**; `/portal/documents/{moroka}/presign-download` → **404**; `/portal/projects` list → only Sipho's 2 projects. **Zero drift from Day 15** |
| 90.9 | PASS | Final digest = Day 75 digest (`Se4qdVQNpS4N7KXguVoLqd`): full-body scan zero Moroka/EST-2026/R25k references; only Sipho's INV-0001 + his trust activity |
| 90.10 | PASS | Portal vocabulary internally consistent: Matters / Fee Notes / Engagement Letters / Requests / Trust; no "case file", no stray "engagement"/"task" synonyms in walked page copy (matter-detail Tasks table is the designed feature, not a synonym leak) |

## Day 90 day-level checkpoints

- Both regression sweeps pass: **PASS** (firm sweep with LZKC-021 terminology finding, Low)
- Isolation holds at Day 90 (zero drift from Day 15): **PASS**
- Mailpit clean, no bounced/failed emails: **PASS**

## Exit-checkpoint assessment (E.1–E.16)

| Exit | Status | Note |
|------|--------|------|
| E.1 | PASS | All scenario days executed; every skip logged with rationale (KYC mandate exemption Day 2; 85.2 conditional; PayFast→mock gateway 0.G) |
| E.2 | PARTIAL | 7+ wow screenshots captured this cycle; visual-regression comparison vs Phase 68 baselines NOT run (no baseline harness in this cycle) |
| E.3 | **FAIL (open)** | **LZKC-012 (High)** — client-facing fee-note PDF wrong/empty — still OPEN; must be fixed before demo-ready |
| E.4 | PASS | Tier removal verified: Settings > Billing (Day 90), team invite flow (Day 0.27), member page (Day 0) |
| E.5 | PASS | Field promotion verified Days 2/3/21/28 + Day 90 re-check |
| E.6 | PASS | Progressive disclosure verified (Day 0 + Day 90 nav sweep) |
| E.7 | PASS | Keycloak E2E (Day 0) — zero mock IDP |
| E.8 | PASS | Portal magic-link only: Days 4/8/11/15/30/46/61/75/88/90 all via magic-link/JWT sessions, zero KC on portal |
| E.9 | PARTIAL | Firm-side Invoice leaks (LZKC-009) + projects leaks (LZKC-021) open; portal internally consistent |
| E.10 | PASS | Day 15 + Day 90 isolation probes pass at list, URL, and API levels |
| E.11 | PASS | Trust reconciliation identical across firm ledger / matter tab / portal at Days 11 (R 50 000), 46 (R 70 000), 61+ (R 0,00) |
| E.12 | PASS (mock gateway) | Day 28 generation + Day 30 payment E2E; firm PAID reflected <60s; gateway is the dev mock, not PayFast sandbox (documented Day 0.G) |
| E.13 | PASS | Day 60 multi-user gate resolution → clean-path closure, SoA generated, portal download Day 61 |
| E.14 | PASS | Day 85: actor filter returns firm users AND portal contact events over the cycle |
| E.15 | **NOT RUN** | Full test-suite gate (backend `./mvnw verify`, frontend/portal lint+build+test) is the cycle-exit merge gate — to be run by the orchestrator before cycle close; not executed inside this QA session |
| E.16 | N/A this pass | Bug-fix cycle by design — 21 tracker gaps (LZKC-001…021) await Product/Dev disposition; not a clean single pass |

## New gaps

- **LZKC-021 (Low)** — "projects" terminology leaks on My Work ("…across all projects") and Calendar ("…across all projects", "All Projects" filter, "Projects" tab) despite legal-za substitution elsewhere.

## Verdict

**All scenario days (0–90) executed and recorded.** Isolation gate (E.10) holds everywhere. Exit to demo-ready is blocked only by open gaps — chiefly LZKC-012 (High) — and the E.15 test-suite gate, which are fix-cycle work, not further QA execution.
