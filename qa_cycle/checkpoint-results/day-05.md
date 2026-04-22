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
