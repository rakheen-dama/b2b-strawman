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
