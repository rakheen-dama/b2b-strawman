# Day 88 — Activity feed wow moment (side-by-side firm + portal)

## Day 88 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Actor**: Sipho Dlamini (portal magic-link, fresh) + Thandi Mathebula (firm, KC SSO live).
**Tooling**: plugin Playwright (`mcp__plugin_playwright_playwright__*`); read-only Docker `psql` exec on `b2b-postgres` DB `docteams`. Mailpit REST for magic-link extract only. Zero SQL/REST mutations.
**Wall-clock**: ~10 min (L-75c verify ~5 min + Day 88 ~5 min).

---

### Slice 1 — L-75c Verify (PR #1139, `a76b6a93`) → **VERIFIED**

#### V1: Fresh portal-contact writes on Dlamini matter

Two write paths exercised on existing closed Dlamini matter (`e788a51b-3a73-456c-b932-8d5bd27264c2`); third path attempted but pivoted per spec guidance (matter is CLOSED → no DRAFT info-request items available, and creating a fresh disbursement on a closed matter would require firm-side mutations on closed matter).

| # | Write path | Action | New `audit_events` row |
|---|------------|--------|------------------------|
| 1 | **Doc download — SoA** | Sipho clicked "Download statement-of-account-…2026-04-25.pdf" in portal Documents tab → presigned-URL tab opened | `c6ccd29c-…` `portal.document.downloaded` `actor_id=127d1c7d-…` `details.project_id=e788a51b-…` `details.file_name=statement-of-account-…` |
| 2 | **Doc download — closure letter** | Sipho clicked "Download matter-closure-letter-…2026-04-26.pdf" → presigned-URL tab opened | `1d804205-…` `portal.document.downloaded` `actor_id=127d1c7d-…` `details.project_id=e788a51b-…` `details.file_name=matter-closure-letter-…` |
| 3 | **Portal comment** | Sipho posted comment "L-75c verify: portal comment from Sipho — testing PORTAL_CONTACT audit emission" on Dlamini matter via portal Comments box → visible in feed as "Sipho Dlamini just now" | `99390304-…` `comment.created` `actor_type=PORTAL_CONTACT` (canonical, not legacy `PORTAL_USER` — drive-by rename per spec verified) `actor_id=127d1c7d-…` `details.project_id=e788a51b-…` `details.actor_name="Sipho Dlamini"` `details.body=…` `details.source=PORTAL` |
| 4 | (Skipped) Info-request item submit | All 5 REQ-* on Dlamini are COMPLETED or CANCELLED — no DRAFT items left to submit. Per spec: "If matter-closure restrictions block N out of 4 sites, report it and proceed". | — |
| 5 | (Skipped) Mock-payment complete | Setup requires firm-side new disbursement + new fee note on closed matter. Per spec: pivot rather than power through. | — |

#### V2: DB assertion — count went from 2 → 5

```sql
SELECT id, event_type, actor_type, actor_id, occurred_at, details->>'project_id' AS project_id
FROM tenant_5039f2d497cf.audit_events
WHERE actor_type='PORTAL_CONTACT'
ORDER BY occurred_at DESC LIMIT 10;
```

```
                  id                  |         event_type         |   actor_type   |               actor_id               |          occurred_at          |              project_id
--------------------------------------+----------------------------+----------------+--------------------------------------+-------------------------------+--------------------------------------
 99390304-0ac4-4ccd-974c-0129a562544d | comment.created            | PORTAL_CONTACT | 127d1c7d-c974-4c79-9985-feed9321c167 | 2026-04-25 23:35:35.932941+00 | e788a51b-3a73-456c-b932-8d5bd27264c2
 1d804205-1232-4e3e-a081-1e0acb14ce27 | portal.document.downloaded | PORTAL_CONTACT | 127d1c7d-c974-4c79-9985-feed9321c167 | 2026-04-25 23:34:47.578109+00 | e788a51b-3a73-456c-b932-8d5bd27264c2
 c6ccd29c-0947-4b10-9ec8-74e087de136a | portal.document.downloaded | PORTAL_CONTACT | 127d1c7d-c974-4c79-9985-feed9321c167 | 2026-04-25 23:34:41.931489+00 | e788a51b-3a73-456c-b932-8d5bd27264c2
 04dfaa8f-a2de-40e2-ad03-1b2481c6e8e0 | acceptance.accepted        | PORTAL_CONTACT |                                      | 2026-04-25 09:56:48.46284+00  |
 3584362e-a4e6-4045-8a9d-b0f527778367 | acceptance.viewed          | PORTAL_CONTACT |                                      | 2026-04-25 09:56:26.58842+00  |
```

- **Count**: 2 → **5** (3 new rows from cycle-1, all attributable to Sipho via `actor_id=127d1c7d-…`).
- **Distinct event_types observed**: **4** (`acceptance.viewed`, `acceptance.accepted`, `portal.document.downloaded`, `comment.created`) — spec target was ≥ 2 distinct types.
- **Dlamini-scoped rows**: 3 / 5 (the 2 acceptance rows from Day 8 are tied to a separate `acceptance` entity_id, not the project).
- **L-75c additional findings**:
  - `details.project_id=e788a51b-…` populated correctly on all 3 new rows (load-bearing for L-75b future actor-filter UI).
  - `actor_id` populated correctly with the portal contact UUID — fixes the empty-actor-id observed on the 2 baseline rows.
  - `comment.created` event uses canonical `PORTAL_CONTACT` (verifies the drive-by `PORTAL_USER` → `PORTAL_CONTACT` rename per spec).

#### V3: L-75c → **VERIFIED**

Tracker updated. New audit row IDs documented above.

---

### Slice 2 — Day 88 (Activity-feed wow moment)

#### Per-checkpoint table

| # | Step | Result | Evidence |
|---|------|--------|----------|
| 88.1 | `[FIRM]` Matter Activity tab → full 90-day history renders | **PASS** + bonus | `day-88-cycle1-firm-activity-feed.png`, `day-88-cycle1-firm-activity-tab.yml`. Activity tab now shows: **Sipho Dlamini commented on project "project" (2 min ago)**, **System performed portal.document.downloaded on document (3 min ago)** ×2, then full firm-side history (Thandi closure letters ×2, statement.generated ×2, Bob trust_refund.recorded, Thandi task.cancelled ×9, REQ-0007 completed, info-req acceptances ×3, "Load more" available for older 90-day events). **NEW**: portal-actor entries are now interleaved with firm-actor entries — direct consequence of L-75c landing. |
| 88.2 | 📸 Screenshot `day-88-firm-activity-feed.png` | **CAPTURED** | `day-88-cycle1-firm-activity-feed.png` (full-page) |
| 88.3 | `[PORTAL]` Sipho login + activity trail on `/home` or `/profile` | **GAP-DEFERRED — OBS-Day75-NoPortalActivityTrail (Sprint 2)** | `/home` shows 4 tile cards (Pending info requests=3, Upcoming deadlines=0 / Next 14 days, Recent invoices INV-0001/0002, Last trust movement="No recent activity") — **NO activity-feed card**. `/profile` shows contact info only (Name, Email, Role, Customer). `/activity` route → 404 "Go Home" page. `day-88-cycle1-portal-home.yml`, `day-88-cycle1-portal-profile.yml`, `day-88-cycle1-portal-home-no-activity-card.png`. |
| 88.4 | Portal activity trail shows: FICA submit (D4), proposal accept (D8), trust view (D11), fee-note paid (D30), info-req submit (D46), SoA download (D61) | **EVIDENCED VIA DB (no portal UI surface)** | `day-88-cycle1-portal-side-audit-events.sql`. Backend now captures portal events for Sipho + Dlamini matter: 3 rows (`comment.created` 23:35, `portal.document.downloaded` ×2 at 23:34) — these are cycle-1 fresh rows. Historical Day 4–61 events were never emitted (pre-L-75c data plane was incomplete) and will not retroactively appear. From now forward all 5 emit sites in PR #1139 will populate `audit_events` correctly. **Portal-half data plane is fixed; portal-half UI surface is Sprint 2.** |
| 88.5 | 📸 Screenshot `day-88-portal-activity-trail.png` | **PARTIAL — captured `/home` showing tile-only design (no activity card)** | `day-88-cycle1-portal-home-no-activity-card.png`. Demonstrates portal currently has no equivalent of firm matter Activity tab. |
| 88.6 | Verify narrative coherence: every client-visible firm event has matching client-side entry within ≤ 1 day delay | **PARTIAL** | Firm side now sees all client-visible portal-actor actions (comments, doc downloads) within seconds via matter Activity tab. Reverse direction (client seeing firm-actor actions on portal) NOT demonstrated — portal `/home` has no activity surface; portal Matters detail view shows tasks + documents + comments as separate sections, not a unified feed; client cannot view "Thandi sent invoice INV-0002 → 2 hr ago" in a chronological narrative. |

#### Day 88 checkpoints (rollup)

- [x] **Firm-side activity feed internally complete** — full 90-day history renders, including new cross-actor portal entries (now Sipho + System rows visible alongside Thandi/Bob).
- [ ] **Portal-side activity feed internally complete** — **GAP-DEFERRED to Sprint 2** (OBS-Day75-NoPortalActivityTrail). Portal `/home` is tile-based, no activity-feed card. Portal `/activity` route absent. Data plane is now ready (post-L-75c) to feed such a surface when it ships.
- [ ] **Semantic match across POVs** — **PARTIAL**. Firm sees portal events ✓. Portal sees firm events: only via per-section tab views (Documents, Comments, Invoices), not via a unified activity feed.

#### Console health

- 0 errors / 1 expected warning on portal navigation flow.
- 0 errors on firm matter Activity tab (`?tab=activity`).
- 1 expected error on portal `/activity` route probe (404 — route does not exist; matches OBS-Day75-NoPortalActivityTrail).

---

### Headline outcome

**Day 88 PARTIAL COMPLETE — major demo win on firm half:**
- 88.1 PASS+ (firm matter Activity tab now shows full 90-day cross-actor history including portal-contact events — direct payoff from L-75c landing).
- 88.2 CAPTURED.
- 88.3 GAP-DEFERRED (portal activity surface absent, Sprint 2).
- 88.4 EVIDENCED VIA DB (data plane fixed; UI surface deferred).
- 88.5 PARTIAL (captured `/home` tile-only state).
- 88.6 PARTIAL (firm→portal direction works; portal→firm direction limited to per-section tabs, no unified feed).

**Demo-impact**:
- **Firm-side wow moment**: ✓ DEMO-READY. Activity tab on Dlamini matter now reads as a coherent 90-day narrative with client AND firm actors interleaved. This is the headline of the slide.
- **Portal-side wow moment**: ✗ NOT DEMO-READY at the wow-moment level. No "Sipho's 90-day activity trail" surface exists on portal. Closing-demo must either (a) skip side-by-side and show firm-only Activity tab, or (b) ship Sprint-2 portal `/activity` route per OBS-Day75-NoPortalActivityTrail before final demo.

**E.14 (audit-trail completeness exit gate)** verdict update:
- **Data plane**: ✓ MET (post-L-75c — portal-contact write events now flow to `audit_events` with canonical actor_type + load-bearing project_id).
- **Firm UI surface**: ✓ MET (matter Activity tab renders cross-actor history including portal entries).
- **Firm UI filter-by-actor**: ✗ NOT MET (L-75b deferred to Sprint 2 — no actor combobox on Activity tab).
- **Portal UI surface**: ✗ NOT MET (L-75a / OBS-Day75-NoPortalActivityTrail — Phase 69 / Sprint 2 work).
- **Net**: E.14 partially met — data + primary firm UI surface are in place; secondary surfaces (filter UI, portal UI) deferred to Sprint 2.

### Reconfirmed observations / no new gaps

- **OBS-Day75-NoPortalActivityTrail** — reconfirmed (portal `/home` no activity card; `/profile` no activity surface; `/activity` 404).
- **OBS-Day85-NoActorFilterMatterActivity** (= part of L-75b) — reconfirmed (Activity tab still has only 6 type-filter buttons: All/Tasks/Documents/Comments/Members/Time; no actor filter).
- **Pre-L-75c historical data**: portal events from Days 4, 8, 11, 30, 46, 61 of the scenario will never retroactively appear — they were not emitted at the time. Forward-only fix. Acceptable per Dev triage 2026-04-25.

### Next action

QA → Day 90 + exit checkpoints sweep (final regression). Day 88 firm-side win + L-75c verified is sufficient evidence to proceed; remaining portal-side gaps (88.3-88.6) tracked as Sprint-2 work and do NOT block Day 90.

### Evidence files

- **L-75c verify**:
  - `qa_cycle/checkpoint-results/day-88-cycle1-l75c-portal-contact-events.sql` (5-row dump with full `details` column)
  - `qa_cycle/checkpoint-results/day-88-cycle1-l75c-portal-comment-posted.png` (Sipho's comment visible in portal Comments section)
- **Day 88 firm-side**:
  - `qa_cycle/checkpoint-results/day-88-cycle1-firm-activity-tab.yml` (461 lines, full Activity tabpanel snapshot)
  - `qa_cycle/checkpoint-results/day-88-cycle1-firm-activity-feed.png` (full-page screenshot showing Sipho's 3 portal entries at top of feed)
  - `qa_cycle/checkpoint-results/day-88-cycle1-firm-client-comments-tab.png` (Client Comments tab — defaulted to Overview which also shows "Recent Activity" card with Sipho entries)
- **Day 88 portal-side**:
  - `qa_cycle/checkpoint-results/day-88-cycle1-portal-home.yml` (tile-only home, no activity card)
  - `qa_cycle/checkpoint-results/day-88-cycle1-portal-profile.yml` (contact info only)
  - `qa_cycle/checkpoint-results/day-88-cycle1-portal-home-no-activity-card.png` (visual evidence of the gap)
  - `qa_cycle/checkpoint-results/day-88-cycle1-portal-side-audit-events.sql` (DB evidence that portal events ARE captured even though no UI surface exposes them to Sipho)

---

## Day 88 Walk — Cycle 56 — 2026-04-28 SAST

**Branch**: `bugfix_cycle_2026-04-26-day88` (cut from `main` `c3f85629` — Day 85 walk PR #1203 squash-merge).
**Actor**: Bob Ndlovu (admin, KC SSO live from prior session — documented password worked first try, no rotation needed; cycle-55 advisory drift not encountered) + Sipho Dlamini (portal magic-link — fresh `t3epqfSaOGh2oT3yrayZB5GkZnpJLO8ac3siY7FRx5g` exchanged via Mailpit msg `AKH5w5TXJVBhZKrtvifQvE`).
**Tooling**: `mcp__plugin_playwright_playwright__*` worked first try (orchestrator's prior cleanup of foreign sessions held — `browser_navigate http://localhost:3000/dashboard` returned 200 immediately, KC session restored from prior cycle). Backend PID 42803 (JVM serving main `c3f85629`). Read-only Docker `psql` exec on `b2b-postgres` for audit-events spot checks.
**Wall-clock**: ~10 min.

### Per-checkpoint table

| # | Step | Result | Evidence |
|---|------|--------|----------|
| 88.1 | `[FIRM]` Matter Activity tab → full 90-day history renders | **PASS** | Matter at `/org/mathebula-partners/projects/cc390c4f-…?tab=activity` (URL slug is `projects` not `matters` — sidebar label is "Matters"). Activity tab shows full cross-actor history: portal.document.downloaded ×2 (Sipho, "5 hours ago"), statement.generated ×2 (Bob 15h, Thandi 20h), Thandi-generated `matter-closure-letter-…-2026-04-27.pdf` (20h), portal.request_item.submitted ×2 + portal.document.upload_initiated ×2 (Sipho, 22h), info_request REQ-0005 created+sent (Bob, 22h), REQ-0004 created+sent (Bob, 23h), portal.invoice.paid (Sipho, yesterday), disbursement.billed/approved/submitted (Bob, yesterday), time_entry.created/deleted ×4 (Bob, yesterday). Click "Load more" → REQ-0001/0002 created+sent, REQ-0002 completed, item_accepted ×3, more time_entry/disbursement/court_date events. Actor-filter combobox lists `All actors / Bob Ndlovu / System / Thandi Mathebula` (cycle-55 GAP-L-75 fix carries forward). DB verification: 20 distinct event_types on the matter (`SELECT event_type, actor_type, COUNT(*) FROM audit_events WHERE details->>'project_id'='cc390c4f-…' OR entity_id='cc390c4f-…'` returned 20 rows including 3 PORTAL_CONTACT types: portal.document.upload_initiated×5, portal.request_item.submitted×5, portal.document.downloaded×2, portal.invoice.paid×1). `cycle56-day88-88.1-firm-activity-tab.yml`, `cycle56-day88-88.1-audit-events-by-type.txt`. |
| 88.2 | 📸 Screenshot `day-88-firm-activity-feed.png` | **CAPTURED** | `cycle56-day88-88.2-firm-activity-feed.png` (full-page; shows interleaved Sipho + Bob + Thandi timeline). |
| 88.3 | `[PORTAL]` Sipho login + activity trail on `/home` or `/profile` | **PASS — NEW SURFACE LIVE** | Magic-link auth via Mailpit `AKH5w5TXJVBhZKrtvifQvE` exchanged successfully → `/projects` (initial landing). `/home` now shows tiles: Pending info requests = 2 (correct, GAP-L-92 verified), Upcoming deadlines = 0, Recent fee notes, **Last trust movement R 20 000,00 27 Apr 2026** (was "No recent activity" in cycle 1; trust-deposit summary now wired). Crucially — **portal sidebar nav now contains an "Activity" link** (cycle 1 reported `/activity` returned 404). `/profile` still contact info only (Name/Email/Role/Customer). `cycle56-day88-88.3-portal-home.yml`, `cycle56-day88-88.3-portal-profile.yml`. |
| 88.4 | Activity trail shows: FICA submit (D4), proposal accept (D8), trust view (D11), fee-note paid (D30), info-req submit (D46), SoA download (D61) | **PARTIAL — surface live, historical data forward-only** | `/activity` route renders timeline page with **two tabs**: "Your actions" (Sipho's 12 PORTAL_CONTACT rows visible — Document downloaded ×2, portal.request_item.submitted ×5, portal.document.upload_initiated ×5, all timestamped 4h–1d ago) + "Firm actions" (Bob/Thandi: statement.generated ×2, document.generated, information_request.created/sent ×4 each, info_request.item_accepted ×3, info_request.completed, time_entry.created ×4, time_entry.deleted ×2, disbursement.billed/approved/submitted/created, court_date.created, matter_closure.closed, project.updated ×2, project.created_from_template). **Pre-L-75c historical events (Day 4 FICA, Day 8 proposal accept, Day 11 trust view, Day 30 fee-pay, Day 46 info-req submit, Day 61 SoA download) NOT shown** — these were never emitted to `audit_events` at the time and remain forward-only per Dev triage 2026-04-25. **NEW concern**: "Firm actions" tab exposes internal-only event types to the client (time_entry.*, disbursement.*, court_date.created) — see GAP-L-100 below. `cycle56-day88-88.3-portal-activity.yml`, `cycle56-day88-88.4-portal-activity-firm.yml`, `cycle56-day88-88.4-portal-contact-events.txt`. |
| 88.5 | 📸 Screenshot `day-88-portal-activity-trail.png` | **CAPTURED** | `cycle56-day88-88.5-portal-activity-trail.png` (Your actions tab) + `cycle56-day88-88.4-portal-activity-firm-tab.png` (Firm actions tab — shows the internal-event leakage). |
| 88.6 | Verify narrative coherence: every client-visible firm event has matching client-side entry within ≤ 1 day delay | **PASS at narrative level / PARTIAL on event filtering** | Coherence matrix at `cycle56-day88-88.6-coherence-matrix.txt`. Firm → Portal direction: `statement.generated` ×2, `document.generated` (closure letter), `information_request.*`, `matter_closure.closed` all surface on portal Firm actions tab within seconds. Portal → Firm direction: Sipho's 12 PORTAL_CONTACT rows + 1 portal.invoice.paid (NULL actor_id) all appear on firm Activity feed (rendered as "System" per cycle-55 OBS-Cycle55-PortalContactBucketedAsSystem cosmetic). Internal isolation HOLDS — `/projects` lists only Dlamini, no Moroka leakage; activity tabs contain zero "moroka/EST-2026/estate" matches (`grep -ic` 0). **Caveat**: portal Firm actions tab does NOT filter by client-relevance, so the client sees billable-time entries and disbursement workflow events that should arguably be hidden. |

### Day 88 checkpoints (rollup)

- [x] **Firm and portal activity feeds each internally complete** — Firm: full 90-day cross-actor history, type+actor filters work. Portal: `/activity` route now live with Your/Firm tabs, data plane intact for forward events.
- [x] **Semantic match across POVs** — narrative coherence holds; firm sees portal events, portal sees firm events. **However**: portal Firm-actions tab over-shares internal event types (NEW GAP-L-100). 

### Console health

- 0 production errors, 0 warnings on firm matter Activity tab.
- 0 production errors on portal `/activity`, `/home`, `/profile`, `/projects`.
- 4 expected 404s in console history: 2 for early `/matters/...` URL probe before discovering `/projects` slug; 2 favicon 404s (8180 KC, 3002 portal). 1 Next.js scroll-behavior advisory warning. None block the walk.

### NEW Gaps surfaced

**GAP-L-100 — MEDIUM (privacy / over-disclosure)**: Portal `/activity` "Firm actions" tab exposes internal-only firm event types to portal contacts. Sipho (a client) can see `time_entry.created` ×4, `time_entry.deleted` ×2, `disbursement.billed/approved/submitted/created` (4 rows), `court_date.created` ×1 on the matter timeline — all rendered as raw event_type slugs with the firm user's name. In legal practice management, billable time-entry mutations and internal disbursement workflow are typically NOT surfaced to clients (clients see fee notes / statements that aggregate them, not the line-item events). court_date.created is borderline (clients should know court dates, but typically through a "Court date scheduled" notification, not a raw audit-event slug).
- **Severity**: MEDIUM (privacy / over-disclosure of internal billing detail; not a tenant-isolation breach — events ARE for Sipho's matter — but reveals firm internals at a granularity clients should not see).
- **Suggested fix**: SPEC required from Product. Two parts:
  1. **Allow-list filter** on portal `/activity` Firm-actions endpoint — surface only `statement.generated`, `document.generated` (when shared-to-portal flag set), `information_request.created/sent/completed`, `matter_closure.closed`, `proposal.sent`, `payment.received` (the "client-relevant" set). Hide `time_entry.*`, `disbursement.*`, `court_date.*`, `project.updated`, `task.*`, etc.
  2. **Humanise the labels** — render display strings (e.g. "Statement generated by Bob Ndlovu" / "Closure letter shared with you" / "Information request REQ-0005 sent to you") instead of raw event_type slugs (`statement.generated`, `portal.request_item.submitted`).
- **Effort**: S (single backend filter + frontend label-map; mirrors the well-established `MessageFormatterService` pattern firm-side).

### Reconfirmed observations / no new gaps for these

- **OBS-Day75-NoPortalActivityTrail** — **CLOSED** by build state. Cycle 1 (2026-04-25) reported `/activity` returned 404; cycle 56 confirms surface is live with Your/Firm tabs. Recommend tracker update.
- **OBS-Cycle55-PortalContactBucketedAsSystem** (LOW, Sprint-3-cosmetic) — reconfirmed firm-side: matter Activity tab still labels Sipho's PORTAL_CONTACT rows as "System". Data plane (actor_id populated correctly on 12/13 rows) is unchanged.
- **OBS-Cycle55-PortalInvoicePaidNullActorId** (LOW, observability) — reconfirmed: 1 portal.invoice.paid row still has NULL actor_id (`PaymentReconciliationService:90-135` omits `.actorId(...)`). Same as cycle 55.
- **Pre-L-75c historical-data backfill** — reconfirmed forward-only per Dev triage 2026-04-25. Day 4/8/11/30/46/61 events not in `audit_events`, will not retroactively appear on portal `/activity`. Acceptable.

### Anomalies (non-product, tooling/data)

- **KC password drift**: NOT encountered this cycle. Bob's KC session restored from a previous cycle (cookies preserved across `browser_navigate http://localhost:3000/dashboard`); first navigation landed straight on `/org/mathebula-partners/dashboard` without form re-submission. Cycle-55 OBS-Cycle55-KCFormDoubleSubmit not exercised.
- **Sidebar URL slug inconsistency**: Sidebar label is "Matters" but URL is `/projects/`. Initial probe at `/matters/...` returned 404. This is consistent across the codebase (entity is `Project` in DB, "Matter" is the SA legal terminology label). Not a regression. Documented for next-session handoff.

### Headline outcome

Day 88 **FULLY COMPLETE** with one new MED-severity gap (GAP-L-100, privacy/over-disclosure):
- 88.1 PASS — firm matter Activity tab shows full 90-day cross-actor history (USER + PORTAL_CONTACT events interleaved; type + actor filters functional).
- 88.2 CAPTURED.
- 88.3 PASS — portal `/activity` surface NEW LIVE in cycle 56 (was 404 in cycle 1); cycle-1 OBS-Day75-NoPortalActivityTrail can close.
- 88.4 PARTIAL — surface live, but pre-L-75c historical events absent (forward-only fix per Dev triage); also surfaces internal firm events to client (GAP-L-100).
- 88.5 CAPTURED (both Your actions + Firm actions tab screenshots).
- 88.6 PASS at narrative level — semantic match across POVs holds; isolation HOLDS (zero Moroka leakage).

**Demo-impact**:
- **Firm-side wow moment**: ✓ DEMO-READY (cross-actor 90-day timeline interleaved on matter Activity tab, with both type and actor filters).
- **Portal-side wow moment**: ✓ DEMO-READY structurally (route exists, data flows for new events) but needs polish before final demo: (a) GAP-L-100 internal-event filter, (b) raw event_type slug → human labels, (c) acknowledge pre-L-75c historical absence.
- **E.10 (Isolation gate)**: ✓ HOLDS (Moroka 0 references on Sipho's portal across `/home`, `/projects`, `/activity`, `/profile`).
- **E.14 (Audit trail completeness)**: ✓ MET at firm + portal data planes; portal UI surface NEW (cycle 56).

### Next action

QA → Day 90 (final regression + exit sweep) on a fresh `bugfix_cycle_2026-04-26-day90` branch cut from main HEAD after this Day 88 PR merges. GAP-L-100 is informational for Sprint 2 polish, NOT a Day 90 blocker (does not affect terminology sweep, field promotion sweep, progressive disclosure, or tier-removal scope).

### Evidence files

- **Firm-side**:
  - `qa_cycle/checkpoint-results/cycle56-day88-88.1-firm-activity-tab.yml` (full Activity tab snapshot, 380 lines)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.1-audit-events-by-type.txt` (DB count: 20 event types on RAF matter, USER + PORTAL_CONTACT split)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.2-firm-activity-feed.png` (full-page screenshot, interleaved cross-actor timeline)
- **Portal-side**:
  - `qa_cycle/checkpoint-results/cycle56-day88-88.3-portal-home.yml` (home page with new Activity nav link + last-trust-movement tile populated)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.3-portal-profile.yml` (contact-info-only profile)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.3-portal-activity.yml` (Your actions tab — 12 Sipho rows)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.4-portal-activity-firm.yml` (Firm actions tab — leaks internal events; basis for GAP-L-100)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.4-portal-contact-events.txt` (DB dump: 13 PORTAL_CONTACT rows tenant-wide, 12 carry Sipho's `f3f74a9d-…` actor_id)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.5-portal-activity-trail.png` (portal /activity Your actions tab)
  - `qa_cycle/checkpoint-results/cycle56-day88-88.4-portal-activity-firm-tab.png` (portal /activity Firm actions tab — shows internal-event leakage for GAP-L-100)
- **Coherence**:
  - `qa_cycle/checkpoint-results/cycle56-day88-88.6-coherence-matrix.txt` (semantic-match matrix)
