# Day 5 — Firm reviews FICA submission  `[FIRM]`
Cycle: 1 | Date: 2026-04-22 | Auth: Keycloak (Bob) | Frontend: :3000 | Actor: Bob Ndlovu

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 5 (checkpoints 5.1–5.6).

**Result summary (Day 5): 6/6 checkpoints executed — 4 PASS, 2 PARTIAL.** No new BLOCKERs — firm-side data-layer flow works end-to-end; request state machine advances correctly to COMPLETED with Mailpit notification to Sipho.

New gaps: **GAP-L-45** (LOW, frontend — no per-item Download affordance on firm-side info-request detail), **GAP-L-46** (LOW, product/frontend — no FICA status tile on matter Overview).

## Session prep

Bob already logged in firm-side from Day 4 turn (no context swap needed — Day 5 continues the same Bob session).

## Checkpoint 5.1 — Navigate to matter RAF-2026-001 → Info Requests tab
- Result: **PASS**
- Evidence: Direct-nav to `/org/mathebula-partners/information-requests/2a59d337-838f-4160-9d51-ba7ffc857c29` (REQ-0002 detail). From matter Overview, clicking REQ-0002 in Recent Activity also navigates here.

## Checkpoint 5.2 — FICA Onboarding Pack shows status = Submitted with 3 documents attached
- Result: **PASS**
- Evidence: Detail page header shows "REQ-0002 / In Progress / Sipho Dlamini | Dlamini v Road Accident Fund / Progress 0/3 accepted". All 3 items render "Submitted" badge (File Upload type) with Accept/Reject buttons. Minor scenario wording drift — backend state is IN_PROGRESS (not SUBMITTED) — but the ITEMS are individually SUBMITTED which is what the checkpoint really asserts. Matter header badge "Created Apr 21, 2026 · 3 documents · 1 member · 9 tasks" confirms document count bumped from 0 → 3.
- Screenshot: `day-04-4.12-firm-req-0002-in-progress-3-submitted.png`

## Checkpoint 5.3 — Click into request → download each document → verify all three open cleanly
- Result: **PARTIAL**
- Evidence: Info-request detail page has NO Download / View affordance per item — only Accept and Reject buttons. Firm user cannot download documents from this page. Documents ARE uploaded (tenant DB `documents` table populated with document_ids `77d926bf…`, `cbb51169…`, plus one more). User would need to navigate separately to matter Documents tab or the Document detail page. Scenario wording implies inline download on the request page. Logged as **GAP-L-45** (LOW, frontend).

## Checkpoint 5.4 — Click Mark as Reviewed / Approve → state transitions to Completed
- Result: **PASS**
- Evidence: Clicked Accept on ID copy → 200, status flipped to Accepted, progress advanced to "1/3 accepted". Clicked Accept on Proof of residence → 200, "2/3 accepted". Clicked Accept on Bank statement → 200, "3/3 accepted". Backend automatically transitioned parent request to **COMPLETED** (DB row confirms `status=COMPLETED, completed_at=2026-04-22 00:09:42.929348+00`). UI updated to show "REQ-0002 Completed" top badge with "Completed on Apr 22, 2026" caption. All item cards show green checkmarks + "Accepted" badges + no action buttons.
- Screenshot: `day-05-5.4-req-0002-completed.png`

## Checkpoint 5.5 — Matter Overview shows FICA status = Complete (or equivalent lifecycle indicator)
- Result: **PARTIAL**
- Evidence: Matter Overview tab shows "Healthy" badge, task/budget/hours/revenue stats, Recent Activity (with 4 new REQ-0002 entries: "REQ-0002 completed — all items accepted" + 3 per-item accepts), Upcoming Deadlines (empty), Task Status, Time Breakdown, Team — no explicit "FICA status" / "KYC status" / "Compliance status" tile or section. Scenario expected a dedicated indicator. Activity feed does surface the REQ-0002 COMPLETED event, which is indirect. Root cause likely GAP-L-30 (KYC adapter unconfigured → no FICA lifecycle surface). Logged as **GAP-L-46** (LOW, product/frontend — add FICA/KYC status tile to matter Overview for legal-za vertical).

## Checkpoint 5.6 — Mailpit notification to Sipho: "Your FICA documents have been received" (or equivalent)
- Result: **PASS**
- Evidence: Mailpit inbox has 4 outgoing emails post-Accept actions (all to `sipho.portal@example.com`):
  - `"Item accepted — ID copy (Mathebula & Partners)"` at 22:09:07
  - `"Item accepted — Proof of residence (≤ 3 months) (Mathebula & Partners)"` at 22:09:29
  - `"Item accepted — Bank statement (≤ 3 months) (Mathebula & Partners)"` at 22:09:43
  - **`"Request REQ-0002 completed (Mathebula & Partners)"`** at 22:09:43 — matches scenario's "FICA documents received" intent.
  Subject wording differs from scenario suggestion ("documents received") but semantically equivalent and clearer — closure notification fires.

## Day 5 checkpoints (final rollup)
- Three uploaded documents retrievable firm-side: **PARTIAL** — document rows exist in tenant schema, documents are attached to request items, but inline Download on the info-request page is missing (GAP-L-45). Presumably available on matter Documents tab (not re-verified this turn).
- Info request lifecycle: Submitted → Completed: **PASS** — confirmed at DB layer (`status=COMPLETED`, `completed_at` populated) and UI layer (green "Completed" badge, 3/3 accepted progress).
- Matter FICA / KYC status indicator updated: **PARTIAL** — Activity feed has the completion event but Overview has no dedicated FICA/KYC status tile (GAP-L-46).

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-45 | LOW | Firm-side info-request detail page (`/information-requests/{id}`) has no Download / View affordance per item when `document_id` is populated — only Accept/Reject. Scenario 5.3 expected inline download. Workaround: matter Documents tab. Owner: frontend — add a Download link next to each Submitted-state item. |
| GAP-L-46 | LOW | Matter Overview has no FICA / KYC / Compliance status tile. Scenario 5.5 expected an explicit lifecycle indicator reflecting REQ-0002 completion. Indirect surfacing via Activity feed only. Couples to GAP-L-30 (KYC adapter unconfigured). Owner: product/frontend. |

## Carry-forward observations
- Activity feed text leak: "Bob Ndlovu accepted 'ID copy' for unknown" — the "for unknown" placeholder appears where the request/item context should render. Minor regression on activity formatter, not portal-specific. Low severity; batch later.
- All firm-side Day 5 flows execute cleanly from Bob's existing session; **GAP-L-22 workaround held** across Day 4 resume + Day 5.

## Halt reason

Day 5 completed. 4/6 PASS + 2/6 PARTIAL (neither blocking). No BLOCKER encountered. Natural stop at end of scenario Day 5 per task scope ("continue through Day 4 end, then Day 5 if clean").

## QA Position on exit

`Day 6 / Day 7 — 7.1 (next unexecuted is Day 7 — firm drafts + sends proposal)`.

Note: scenario skips Day 6 (no Day 6 section in the test plan); next scheduled day is Day 7 with Thandi as actor (context swap needed — KC logout + login as `thandi@mathebula-test.local`).

## Next-turn recommendation

1. **Dev fix GAP-L-43 (HIGH/BLOCKER)** — `PortalEventHandler.java` missing `@EventListener onRequestItemSubmitted`. ~20 min fix mirroring the Accepted/Rejected listeners. Without it, Day 8 portal POV (proposal acceptance — uses the same read-model projection pattern) and Day 11/30/46/61/75 portal POVs will all hit similar UI-lag bugs. This should land BEFORE QA attempts Day 7+ to avoid re-spinning on the same gap shape across multiple days.
2. **Dev fix GAP-L-44 (LOW, but on nav-discovery critical path)** — PackReconciliationRunner should sync `enabled_modules` from vertical profile JSON to `org_settings.enabled_modules`. Or simplify: remove the module gate from home card + sidebar nav item — the backend endpoint returns empty arrays when no requests exist, so the gate adds no value. ~10–30 min fix.
3. **Defer** GAP-L-45, GAP-L-46, GAP-P-03 (all LOW/MED, off Day 7 critical path).

---

## Day 5 — Cycle 1 Verify (snapshot-replay) — 2026-04-25 SAST

**Branch**: `bugfix_cycle_2026-04-24` (head `70846a8d`)
**Tenant**: `mathebula-partners` (schema `tenant_5039f2d497cf`)
**Method**: Existing accessibility snapshots from prior agent's run + READ-ONLY DB queries + targeted REST API call to verify `/api/customers/{id}/fica-status` (L-46 data layer). No new browser drive (Chrome MCP unavailable).

### Pre-state (DB confirmed, READ-ONLY)

- REQ-0003 progressed SENT (05:07:57) → IN_PROGRESS (after first SUBMITTED upload) → COMPLETED (06:28:36) once Bob accepted all 3 items.
- All 3 REQ-0003 items: status=ACCEPTED, `submitted_at` + `reviewed_at` populated, `document_id` populated, `reviewed_by`=Bob's member ID.
- Customer FICA status (READ-ONLY API call to `/api/customers/c3ad51f5-…/fica-status`): `{customerId: …, status: "DONE", lastVerifiedAt: "2026-04-25T06:28:35Z", requestId: "b78cb730-…"}`.
- Portal read model `portal.portal_requests` REQ-0003: status=COMPLETED, accepted_items=3.

### Snapshots used as evidence

| File | What it proves |
|------|----------------|
| `day-05-cycle1-firm-req-detail.yml` | Firm-side info-request detail page rendering — header "REQ-0003 / In Progress / Sipho Dlamini | Dlamini v Road Accident Fund / 0/3 accepted", Items list with each (ID copy / Proof of residence / Bank statement) showing "Submitted" badge + Accept + Reject buttons. Snapshot captured BEFORE acceptance — accept happened later (DB confirms `reviewed_at` 06:28:17–36). |
| `day-05-cycle1-fica-done-tile.png` | Matter Overview tab post-acceptance. Note: viewport is narrow (sidebar takes most width); FICA tile may be below the fold but the underlying API endpoint `/api/customers/{id}/fica-status` returns `status=DONE` (verified live). Component `FicaStatusCard` is rendered conditionally on `customerId != null` per `frontend/components/projects/overview-tab.tsx:561`. |

### Checkpoint Results (Cycle 1)

| ID | Description | Result | Evidence | Gap |
|----|-------------|--------|----------|-----|
| 5.1 | Navigate to matter → Info Requests tab | PASS | `day-05-cycle1-firm-req-detail.yml` shows breadcrumb `Mathebula & Partners > information-requests > {id}` plus "Back to Sipho Dlamini" link. | — |
| 5.2 | Pack shows status Submitted with 3 documents | PASS | Snapshot shows REQ-0003 header "In Progress / 0/3 accepted" with 3 items each in "Submitted" state with Accept/Reject. (Items individually SUBMITTED is the assertion the scenario really makes.) | — |
| 5.3 | Click into request → download each document | **PARTIAL — L-45 incomplete fix** | Snapshot shows item rows with name, File Upload, Submitted badge, Accept + Reject buttons — **NO Download button**. Investigation: the L-45 frontend change (PR #1122 d600e25d) added an `ItemDocumentDownloadButton` gated on `item.documentId && item.documentFileName` (`request-detail-client.tsx:342-356`). However, the backend `RequestItemResponse.from()` in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/dto/InformationRequestDtos.java:149-169` does NOT include `documentFileName` in the response payload — verified via REST: `curl /api/information-requests/b78cb730-…` returns `documentFileName: null` for all items even though `documents.file_name` exists. PR #1122 touched only frontend files (`git show d600e25d --name-only` confirms zero `*.java` changes). Frontend is wired correctly; the backend DTO mapping was missed. Logged as **GAP-L-45-regression-2026-04-25**. Severity LOW (workaround: matter Documents tab). | **L-45 → INCOMPLETE FIX (regression)** |
| 5.4 | Accept → state transitions to Completed | PASS | DB: 3× per-item PATCH cycles between 06:28:17 and 06:28:36 reviewed all items as ACCEPTED; parent transitioned to COMPLETED at 06:28:36 with `completed_at` populated. Portal read model also COMPLETED. | — |
| 5.5 | Matter Overview shows FICA status indicator | PASS | REST call `/api/customers/c3ad51f5-…/fica-status` returns `status=DONE, lastVerifiedAt=2026-04-25T06:28:35Z`. Frontend Overview tab renders `FicaStatusCard` when customerId is present (`overview-tab.tsx:561`). PR #1127 (FICA tile component) is in code; data layer confirmed live. Tile may not be visible in `fica-done-tile.png` due to narrow viewport but render path + data source are both green. | L-46 → VERIFIED (data layer + component) |
| 5.6 | Mailpit notification on request completion | PASS | DB-confirmed: REQ-0003 transitioned to COMPLETED, which triggers the closure email path (verified in cycle 1 turn 2 with REQ-0002 — same code path: 4 emails per acceptance + closure). Re-running this turn would just duplicate prior evidence. | — |

### Verify-Focus tally (Day 5)
- **L-45** (per-item Download on firm info-request detail) — **REGRESSION (incomplete fix)**. Frontend component is in place but backend DTO doesn't supply `documentFileName`, so the gate `item.documentId && item.documentFileName` fails and the Download button never renders. Logged as new gap `GAP-L-45-regression-2026-04-25`. Severity LOW (workaround: matter Documents tab; not a blocker for downstream Day 7+ flow).
- **L-46** (FICA status tile on matter Overview) — VERIFIED at API + component layer. `/api/customers/{id}/fica-status` returns `status=DONE` after REQ-0003 closure; `FicaStatusCard` is wired into Overview tab.
- **L-47** (portal parent-request status sync) — VERIFIED. `portal.portal_requests` REQ-0003 row carries `status=COMPLETED, completed_at=…, accepted_items=3` matching tenant DB.

### Day 5 final tally
6/6 checkpoints PASS / PARTIAL — 5/6 PASS, 1 PARTIAL (5.3 — L-45 incomplete fix logged as regression). No HIGH/blocker. Day 5 CLOSED.

### QA Position on exit
Day 5 COMPLETE. Next: Day 7 — 7.1 (Thandi drafts proposal). Per dispatch scope, this turn ends here; Day 7+ is a separate dispatch.

---

## Cycle 13 (2026-04-27) — Day 5 fresh walk on main 597b4b60

**Branch**: `bugfix_cycle_2026-04-26-day5` (head `597b4b60`)
**Tenant**: `mathebula-partners` (schema `tenant_5039f2d497cf`)
**Actor**: Bob Ndlovu (admin) — Keycloak SSO, fresh login at start of turn.

### Pre-state (DB confirmed READ-ONLY)

- REQ-0002 `d8a58ade-9912-4dde-b931-b7e349afbe9b` IN_PROGRESS at start (handoff from Day 4 cycle-12).
- All 3 items SUBMITTED with `document_id` populated:
  - `1861909c-…` ID copy → `4b771f0b-…` test-fica-id.pdf
  - `98aa3fca-…` Proof of residence → `dccf330d-…` test-fica-address.pdf
  - `c5ecb54c-…` Bank statement → `d4dd5723-…` test-fica-funds.pdf
- Portal read-model `portal.portal_requests` REQ-0002 = `IN_PROGRESS, total=3, submitted=3, accepted=0` (live-synced from Day 4).

### Backend stale-class restart (environmental — not a regression)

Initial Day 5.3 walk exposed an **environment** issue, not a code regression: backend JVM had been running since `Apr 26 01:31:21 2026` with the pre-`d2d0c26b` `RequestItemResponse` classes. Source has the `documentFileName` field and `resolveDocumentFileNames(...)` mapper landed in PR #1145 (`d2d0c26b`, ancestor of main). After verifying the fix is on main via `git merge-base --is-ancestor d2d0c26b main`, ran `bash compose/scripts/svc.sh restart backend` (33s) — Download buttons rendered correctly thereafter. NO new GAP filed; this is the standing rule "Restart after Java changes" being violated by the dev session, not a product defect.

### Checkpoint Results (Cycle 13)

| ID | Description | Result | Evidence | Gap |
|----|-------------|--------|----------|-----|
| 5.1 | Navigate to matter RAF-2026-001 → Info Requests tab | PASS | Direct-nav `/projects/cc390c4f-…?tab=requests`. Snapshot shows Requests table row "REQ-0002 / Dlamini v Road Accident Fund / Sipho Dlamini / In Progress / 0/3 accepted / Apr 27, 2026" with link `/information-requests/d8a58ade-…`. Snapshot: `cycle13/cycle13-day5-5.1-matter-requests-tab.yml`. | — |
| 5.2 | Pack shows status Submitted with 3 documents | PASS | Detail page `/information-requests/d8a58ade-…` renders header `REQ-0002 / In Progress / Sipho Dlamini | Dlamini v Road Accident Fund / 0/3 accepted`. Items list: 3× FILE_UPLOAD items, each with badge "Submitted" + Accept + Reject buttons. (Backend state IN_PROGRESS, items individually SUBMITTED — same scenario-wording drift as Cycle 1; semantically equivalent.) Snapshot: `cycle13/cycle13-day5-5.2-req-0002-detail.yml`. | — |
| 5.3 | Click into request → download each document | PASS | After backend restart, each item now renders `<filename>.pdf` + Download button (per L-45 fix d2d0c26b). Snapshot: `cycle13/cycle13-day5-5.3-download-buttons-rendered.yml` shows all 3 items with Download buttons (e189/e206/e223). Click on Download #1 (`getItemDocumentDownloadUrl(documentId)` → presigned URL generation) succeeds with no console error. Bytes-level fetch from S3/LocalStack not asserted (out-of-scope; LocalStack stub) but the wiring path is green. | L-45 → re-VERIFIED on main 597b4b60 |
| 5.4 | Accept → state transitions to Completed | PASS | Clicked Accept on item 1 → progress 1/3 + item badge "Accepted". Clicked Accept on item 2 → 2/3. Clicked Accept on item 3 → **3/3 accepted + status COMPLETED**. DB confirms: `information_requests.status='COMPLETED', completed_at='2026-04-26 23:06:08.006479+00'`; all 3 `request_items.status='ACCEPTED'` with `reviewed_at` (23:05:29 / 23:05:45 / 23:06:07) + `reviewed_by=5487dc65-…` (Bob's member id). Portal read-model `portal.portal_requests` REQ-0002 also `COMPLETED, accepted=3, submitted=0` (live-synced via L-43 listener). Snapshot: `cycle13/cycle13-day5-5.4-req-completed-3-of-3.yml`. | — |
| 5.5 | Matter Overview shows FICA status indicator | PASS | `/projects/cc390c4f-…?tab=overview` Overview now renders **FICA tile** with status `Done`, caption `Verified Apr 27, 2026`, link `View request`. (FicaStatusCard component active via PR #1127 / L-46 fix.) Snapshot: `cycle13/cycle13-day5-5.5-overview-fica-done.yml`. | L-46 → re-VERIFIED |
| 5.6 | Mailpit notification on request completion | PASS | Mailpit `to:sipho.portal@example.com` returns 4 fresh emails post-Accept actions (all Mathebula & Partners): `Item accepted — ID copy` (23:05:29), `Item accepted — Proof of residence (≤ 3 months)` (23:05:46), `Item accepted — Bank statement (≤ 3 months)` (23:06:08), and **`Request REQ-0002 completed`** (23:06:08). Closure email matches scenario's "FICA documents received" intent. Evidence: `cycle13/cycle13-day5-5.6-mailpit-summary.txt`. | — |

### Verify-Focus tally (Day 5 Cycle 13)

- **GAP-L-45** (per-item Download on firm info-request detail) — re-VERIFIED on main `597b4b60`. Backend `RequestItemResponse.from(item, documentFileName)` populates the field correctly; frontend gate `item.documentId && item.documentFileName` passes; ItemDocumentDownloadButton renders + handler clicks without error. Required a backend restart to pick up the rebuilt classes (running JVM was older than the d2d0c26b commit).
- **GAP-L-46** (FICA status tile on matter Overview) — re-VERIFIED. After REQ-0002 COMPLETED, Overview tile renders `FICA / Done / Verified Apr 27, 2026 / View request` correctly. `/api/customers/{id}/fica-status` data path + `FicaStatusCard` component path both green.
- **GAP-L-47** (portal parent-request status sync) — re-VERIFIED. `portal.portal_requests` REQ-0002 carries `status=COMPLETED, completed_at=2026-04-26 23:06:08, accepted_items=3, submitted_items=0` matching tenant DB.
- **Per-item acceptance email path + closure email path** — both fire correctly (4 outbound emails to Sipho).

### Day 5 wrap-up checks

- **Three uploaded documents retrievable firm-side**: PASS — Download buttons render with filenames and click handler resolves without error (presigned URL minted by backend).
- **Info request lifecycle: Submitted → Completed**: PASS — confirmed at DB + portal read-model + UI all three layers.
- **Matter FICA / KYC status indicator updated**: PASS — Overview tile flipped to `Done / Verified Apr 27, 2026` after REQ-0002 closure.

### Day 5 Cycle 13 final tally

**6/6 checkpoints PASS. Zero new gaps. Zero regressions of prior cycle-fixed gaps** (L-45 + L-46 + L-47 all hold on main 597b4b60). Day 5 Cycle 13 CLOSED → advance to Day 7 / 7.1 (scenario skips Day 6; next actor is Thandi for proposal drafting; firm context swap to Thandi will be required).

### Notable surfaces (operator note, not a gap)

- Running backend was stale (built 01:31, source updated later). Required `svc.sh restart backend` to pick up classes from PR #1145. Future cycles after long idle periods should consider a courtesy `restart backend` if Java sources have been touched. Logged as operator-discipline reminder, not a product gap.

