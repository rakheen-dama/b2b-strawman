# Day 85 — Firm final closure paperwork

## Day 85 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Actor**: Thandi (KC session live from prior turn)
**Tooling**: plugin Playwright (`mcp__plugin_playwright_playwright__*`); read-only Docker `psql` exec on `b2b-postgres` DB `docteams`. Zero SQL/REST mutations.
**Wall-clock**: ~10 min.

### Per-checkpoint table

| # | Step | Result | Evidence |
|---|------|--------|----------|
| 85.1 | RAF-2026-001 (CLOSED) → closure letter attached Day 60 | **PASS** | Documents tab lists `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf` (1.6 KB, status Uploaded, Apr 25). Also a second closure letter from re-close cycle (`-2026-04-26.pdf`). `qa_cycle/checkpoint-results/day-85-cycle1-firm-documents-tab.yml` |
| 85.2 | Generate final closing letter via doc-template pack | **N/A — already attached** | Closure letter generated via `MatterClosureService.generateClosureLetterSafely` at close time (Day 60); auto-attached. Tenant workflow does NOT require additional standalone closing correspondence beyond closure letter. |
| 85.3 | Matter retention policy persists; `end_date ≈ today + 5 years - 25 days` | **PARTIAL** — DB persists correctly but no UI surface | DB: `projects.retention_clock_started_at = 2026-04-25 20:44:20`, computed `end_date = 2031-04-24 20:44:20` (= start + 1825 days). Scenario expects "today + 5y - 25d" because scenario assumes 25 days have elapsed since closure. In real-time walk, closure happened 2 hours ago so elapsed = `02:14:17`, not 25 days. Math is correct: `end_date = clock_start + 1825 days` per ADR-249. **Sub-gap**: no firm UI exposes `retention_clock_started_at` / computed end-date on matter detail page (Overview, header, or Settings). Only global `retention_policies` rows visible at `/settings/data-protection` (CUSTOMER 1825d, AUDIT_EVENT 2555d). Matches pre-existing OBS-Day60-RetentionShape Sprint-2 followup. `qa_cycle/checkpoint-results/day-85-cycle1-data-protection-retention.png` |
| 85.4a | Audit Log filter by matter = RAF-2026-001 → full 85-day history | **PARTIAL** — matter Activity tab shows full firm-side history but only USER actor events (no audit-log surface filter UI) | Matter Activity tab (`?tab=activity`) renders complete firm-side audit feed: matter_closure×2, statement.generated×2, document generated×2 (closure letters), trust_refund.recorded (Bob), task.cancelled×9 (Thandi), info_request acceptances×3, REQ-0007 completion, info_request.cancelled×2, plus older Day 0–60 events scrollable via "Load more". Type filters present (All / Tasks / Documents / Comments / Members / Time). `qa_cycle/checkpoint-results/day-85-cycle1-firm-activity-feed.png` |
| 85.4b | Filter by actor = Sipho (portal actor) → portal actions recorded | **FAIL — NEW GAP-L-75** | Matter Activity feed has NO actor filter UI. DB inspection: `audit_events WHERE actor_type='PORTAL_CONTACT'` returns only 2 rows tenant-wide (`acceptance.viewed`, `acceptance.accepted` from Day 8 proposal). Sipho's FICA upload (Day 4), proposal accept (Day 8), trust balance view (Day 11), fee-note pay (Day 30), info-req submit (Day 46), SoA download (Day 61) are NOT recorded as `PORTAL_CONTACT` audit events on matter `e788a51b-…`. Matter Activity feed shows zero portal-actor entries. Reconfirms pre-existing Sprint-2 followup `OBS-Day61-NoPortalDocAuditEvent` plus widens scope: portal-side write events are not emitted to `audit_events` either. Cross-actor audit-trail completeness (E.14) is **not met**. |
| 85.5 | Optional snapshot `day-85-firm-audit-filtered.png` | **CAPTURED** | `qa_cycle/checkpoint-results/day-85-cycle1-firm-activity-feed.png` |

### Day 85 checkpoints (rollup)

- [x] **Matter retention row persists correctly** — DB column-based per ADR-249; computed `end_date = 2031-04-24` matches `clock_start + 1825 days`. **Caveat**: no UI surface for per-matter retention data (deferred Sprint-2 per OBS-Day60-RetentionShape).
- [ ] **Audit log filters by actor work for BOTH firm users AND portal contacts (Phase 50 + Phase 69 readiness)** — **FAIL**. (a) No actor filter UI exists firm-side (only type filter on matter Activity tab); (b) portal write events not emitted to `audit_events`; (c) global Phase 69 audit-log surface not yet shipped. NEW GAP-L-75.

### Console health

- 0 errors / 0 warnings on matter Documents/Activity/Overview tabs.
- 0 errors / 0 warnings on `/settings/data-protection`.
- 1 expected error on `/audit-log` 404 probe (route doesn't exist — Phase 69 not shipped).

### NEW Gaps surfaced

**GAP-L-75 — HIGH (verify-cycle blocker for E.14)**: Cross-actor audit log surface missing.
- (a) No firm-side audit-log UI with filter-by-matter + filter-by-actor controls (Phase 69 not shipped).
- (b) Matter Activity feed (current fallback per scenario) has type filters only, no actor filter.
- (c) Portal-contact write events not persisted to `audit_events` (only `acceptance.viewed/accepted` are; FICA upload, fee-note pay, doc download, info-req submit are NOT).
- **Severity**: HIGH for verify cycle (blocks E.14); MED for prod (Phase 69 carve-out is intentional roadmap deferral, but portal-actor event emission gap is a Phase 50 readiness defect).
- **Suggested fix**: SPEC required from Product. Two surfaces:
  1. Phase 69 audit-log page (M-L, ~1 sprint) — net-new.
  2. Portal-contact audit emission (S-M, ~3-4 hr) — extend `PortalDocumentService` + `PortalInvoiceService` + `PortalTrustService` + `PortalInformationRequestService` to emit `audit_events` rows with `actor_type=PORTAL_CONTACT, actor_id=<portal_contact_id>` mirroring USER pattern.

### Informational obs (NOT new gaps; reconfirm Sprint-2 followups)

- **OBS-Day85-NoMatterRetentionUI** (= continuation of OBS-Day60-RetentionShape): per-matter retention metadata persists in `projects.retention_clock_started_at` column but no UI surface exposes it. Sprint 2: add a "Retention" card to matter detail Overview tab showing `clock_started_at`, `end_date` (computed as `clock_start + 1825 days`), `days_remaining`, action (FLAG default).
- **OBS-Day85-NoActorFilterMatterActivity** (= subset of GAP-L-75): even within USER actor scope, matter Activity tab cannot filter by individual user (e.g. "show only Bob's actions"). Sprint 2 (low priority, mostly demo-cosmetic).

### Headline outcome

Day 85 **PARTIAL COMPLETE**:
- 85.1 PASS (closure letter present + downloadable).
- 85.2 N/A (no additional correspondence required by tenant workflow).
- 85.3 PASS at DB level / FAIL at UI surface (no per-matter retention UI; column-based design works correctly).
- 85.4 PARTIAL — firm-side activity feed shows full USER history; portal-actor history MISSING (NEW GAP-L-75).
- 85.5 CAPTURED.

**Demo-impact**: GAP-L-75 (audit cross-actor) directly blocks exit checkpoint **E.14** ("Audit trail completeness — Day 85 audit log filters return both firm-user events and portal-contact events over the 90 days"). Day 88 (firm + portal activity-feed wow moment side-by-side) is also impacted because portal `/profile` has no activity surface (already captured as OBS-Day75-NoPortalActivityTrail) and audit_events table is missing portal-write rows.

### Next action

QA → Day 88 (firm + portal activity-feed wow moment). **Caveat**: Day 88.3-88.6 will likely BLOCK on portal activity-trail surface absence (per Day 75 cycle-1 OBS-Day75-NoPortalActivityTrail) and on `audit_events` portal-actor emission gap (per GAP-L-75). Recommend Product triage of L-75 before Day 88 dispatch.

### Evidence files

- `qa_cycle/checkpoint-results/day-85-cycle1-firm-documents-tab.yml`
- `qa_cycle/checkpoint-results/day-85-cycle1-firm-activity-tab.yml`
- `qa_cycle/checkpoint-results/day-85-cycle1-firm-activity-panel.yml`
- `qa_cycle/checkpoint-results/day-85-cycle1-firm-overview.yml`
- `qa_cycle/checkpoint-results/day-85-cycle1-data-protection.yml`
- `qa_cycle/checkpoint-results/day-85-cycle1-firm-activity-feed.png`
- `qa_cycle/checkpoint-results/day-85-cycle1-data-protection-retention.png`
- `qa_cycle/checkpoint-results/day-85-cycle1-matter-overview-closed.png`
- `qa_cycle/checkpoint-results/day-85-cycle1-matter-documents-tab.yml`
