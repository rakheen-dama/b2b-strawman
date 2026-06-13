# Day 88 — Activity-feed wow moment (side-by-side firm + portal) `[FIRM → PORTAL]`

- **Cycle**: 32 (`bugfix_cycle_2026-06-13`)
- **Date**: 2026-06-13 SAST
- **Stack**: Keycloak dev stack (firm :3000, portal :3002, backend :8080, gateway :8443). All 4 services healthy.
- **Tooling**: **Playwright MCP exclusively.** Firm session (Thandi, Owner) persisted from Day 85 — no KC login. Portal session (Sipho) persisted from Day 75 — no magic-link.
- **Matter**: RAF-2026-001 `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (Dlamini v Road Accident Fund, CLOSED). Customer Sipho Dlamini.

## Result summary

- **88.1 PASS (with design note)** — firm matter Activity feed renders the full cross-entity history.
- **88.2 PASS** — screenshot `day-88-firm-activity-feed.png`.
- **88.3 PASS** — portal `/activity` route exists and renders (resolves prior cycle's OBS-Day75-NoPortalActivityTrail / Sprint-2 deferral). Sipho session live.
- **88.4 PARTIAL → OBS-8801 (MEDIUM, new)** — most client events present; **proposal-accept (Day 8), fee-note-sent (Day 28), matter-closed (Day 60) missing** from the portal Firm-actions trail.
- **88.5 PASS** — screenshot `day-88-portal-activity-trail.png`.
- **88.6 PARTIAL → OBS-8801** — narrative coherence holds for most milestones; the three above break it.

**Summary checkpoints**
- Firm + portal feeds each internally complete: **PARTIAL** (firm cross-entity feed complete sans project-self lifecycle rows; portal client trail complete sans 3 allowlisted firm milestones — OBS-8801).
- Semantic match across POVs / no leakage: **PASS for isolation + no internal over-disclosure**; **PARTIAL for completeness** (OBS-8801).

**Isolation: CLEAN.** Zero Moroka/Peter/EST-2026/liquidation/deceased/R25k on either portal tab. No internal-only firm events (time entries, disbursements, court date, internal tasks) leak to the portal — client-safe allowlist working. No `[internal]` tags, no filename leakage in download descriptions.

**Blocked?** NO. OBS-8801 is MEDIUM/non-blocking — the wow-moment surfaces both exist and reconcile for the bulk of the 90-day story.

---

## 88.1 `[FIRM]` Matter Activity feed — PASS (design note)

Navigated `/org/mathebula-partners/projects/08ad56c4-…?tab=activity` (the page's own `?tab=` searchParam is the active-tab source-of-truth — `project-tabs.tsx:116-117,232`). Activity group tab `aria-selected=true`. Clicked "Load more" 3× (real Playwright click worked here) → full feed, no more pages: **65 rows** (matches DB `count(*) WHERE details->>'project_id'=matter = 65`).

Full 90-day trail present (newest→oldest), all events on 2026-06-13 (clean-slate cycle — all days ran today, so 24h/7d/30d/90d windows are equivalent):
- portal.document.downloaded ×3 (SoA + closure letter, Day 61)
- statement.generated + closure-letter document generated (Day 60)
- task lifecycle: created / IN_PROGRESS / DONE / CANCELLED (Day 60) — 9 RAF tasks resolved
- REQ-0004 created/sent/2 items accepted/completed (Day 45–46)
- disbursement created/submitted/approved/billed (Day 21/28)
- portal.invoice.paid ×2 (Day 30)
- court_date.created (Day 21)
- time_entry logged 2h30m + 1h30m (Day 21)
- REQ-0002 created/sent/items accepted/completed (Day 7–ish)
- REQ-0001 (FICA) created/sent/3 items accepted/completed (Day 3–5)

**Design note (not a defect on its own — folded into OBS-8801):** 88.1 lists "matter created … matter closed". The matter Activity feed keys on `details->>'project_id'` (`AuditEventRepository.findByProjectId`, L56-77). The project-self lifecycle rows — `project.created_from_template`, `project.updated`, `matter_closure.closed` — are stored as `entity_type='project'`, `entity_id=<matter>` with **`details.project_id = NULL`**, so they do not appear in this feed. They DO surface in Settings → Audit Log entity filter (verified Day 85: created_from_template/updated/closed). Same emission-shape gap as OBS-8801.

Screenshot: `day-88-firm-activity-feed.png`. Firm console: 4 errors, all `/api/assistant/invocations` 404 (OBS-201 exempt). 0 genuine activity-feed JS errors.

## 88.3–88.6 `[PORTAL]` Sipho activity trail — PASS / PARTIAL (OBS-8801)

Portal `/activity` (route now exists — prior cycle's Sprint-2 gap resolved). Two tabs: **"Your actions"** + **"Firm actions"** (real `role="tab"` buttons). The "Firm actions" tab did not switch under real Playwright `.click()` (OBS-6002 recurrence — pointer/HMR friction); switched via the bound React `onClick` prop directly (page's own handler — backend + tab logic correct). Both tabs scanned Moroka-clean.

### "Your actions" tab (19 rows, client-POV) — PASS
- You downloaded a document ×3 (SoA + closure letter — **SoA download Day 61 ✓**)
- You submitted an information request item + started uploading (×2, 1 hr ago — **second info-req submit Day 46 ✓**)
- You paid a fee note ×2 (3h/5h — **fee-note paid Day 30 ✓**)
- You submitted info-request item + upload (7 hr — REQ-0002)
- You submitted info-request item + upload (×3, 18 hr — **FICA submit Day 4 ✓**)

Copy is client-safe ("You", generic "a document"/"a fee note" — no filenames, no internal tags).

### "Firm actions" tab (18 rows, firm-POV client-safe) — PARTIAL
Present: Statement of Account generated (Thandi), Document generated for you (Thandi), and full Information-request lifecycle (created/sent/items accepted/completed) for REQ-0001/0002/0004 by Thandi & Bob.

**Missing (OBS-8801):** proposal-accepted (Day 8), fee-note-sent / `invoice.sent` (Day 28), matter-closed (Day 60). These are on the client-safe allowlist `PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES` but are dropped because `findActivityFirmForCustomer` (`AuditEventRepository.java:253-277`) requires `details->>'project_id' IS NOT NULL`, and these rows are emitted with NULL `details.project_id`.

DB proof (tenant `tenant_5039f2d497cf`):
```
event_type            | total | with_project_id
document.generated    |   2   |   1
statement.generated   |   1   |   1
invoice.sent          |   2   |   0   ← dropped
matter_closure.closed |   1   |   0   ← dropped
proposal.accepted     |   1   |   0   ← dropped
```

### 88.4 expected vs observed
| Expected milestone | Portal surface | Result |
|---|---|---|
| FICA submit (Day 4) | Your actions | ✓ |
| Proposal accept (Day 8) | Firm actions | ✗ OBS-8801 |
| First trust balance VIEW (Day 11) | — | N/A — a read, not an audited mutation; never expected to be an activity row (not a defect) |
| Fee-note paid (Day 30) | Your actions | ✓ |
| Second info-req submit (Day 46) | Your actions | ✓ |
| SoA download (Day 61) | Your actions | ✓ |

### 88.6 narrative coherence — PARTIAL
Most client-visible firm events have matching client-side entries (info requests, SoA, closure letter, downloads, payments). The proposal-accept and matter-closed milestones break ≤1-day coherence (firm-side they exist; portal-side absent) — OBS-8801.

Screenshot: `day-88-portal-activity-trail.png` ("Your actions" tab). Portal console: **0 errors**.

## Carry-over exemptions observed (not re-filed)
- OBS-201 `/api/assistant/invocations` 404 (firm console) — exempt.
- OBS-6002 — Firm Activity group tab + portal Firm-actions tab did not switch under real `.click()`; drove via `?tab=` searchParam (firm) and bound React `onClick` (portal). Backend/filter logic correct. Tooling/HMR friction, already OPEN, NOT re-filed.
- Project-self lifecycle rows absent from matter Activity feed — folded into OBS-8801 (same emission-shape root cause), not a separate file.

## New gaps
- **OBS-8801** (MEDIUM, new) — client-safe firm milestones (`proposal.accepted`, `invoice.sent`, `matter_closure.closed`, + project-self lifecycle) dropped from portal Firm-actions trail AND firm matter Activity feed because emitted without `details.project_id`. Spec: `fix-specs/OBS-8801.md`.
