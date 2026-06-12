# Day 5 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, portal :3002, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Bob Ndlovu (Admin — context swap from portal Day 4, fresh Keycloak login)

---

## Pre-check: Context swap + Login as Bob

- Closed portal browser context (Day 4 session)
- Navigated to `http://localhost:3000/dashboard` -> Keycloak login form at `:8180`
- Logged in as `bob@mathebula-test.local` / `SecureP@ss2` -> landed on `/org/mathebula-partners/dashboard`
- Sidebar confirmed: "BN" avatar, "Bob Ndlovu", bob@mathebula-test.local

---

## Day 5 — Firm reviews FICA submission `[FIRM]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 5.1 | Navigate to matter RAF-2026-001 -> Client group tab -> Requests sub-tab. Verify envelope row shows In Progress + 0/3 accepted. Link navigates to `/org/{slug}/information-requests/{id}`. | **PASS** | Navigated to matter detail -> Client tab dropdown -> "Requests" sub-tab. Table row: REQ-0001, Contact=Sipho Dlamini, Status=**In Progress**, Progress=**0/3 accepted**, Sent=May 30, 2026. Link URL: `/org/mathebula-partners/information-requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19` (correct canonical route per OBS-501). |
| 5.2 | Click REQ-0001 -> detail page renders 3 items with Submitted status and attached PDFs. | **PASS** | Request detail page at `/org/mathebula-partners/information-requests/0e800982-...`. Header: REQ-0001, status "In Progress". 3 items rendered: (1) **ID copy** — Submitted, fica-id.pdf attached, Download + Accept/Reject buttons. (2) **Proof of residence (<=3 months)** — Submitted, fica-address.pdf attached, Download + Accept/Reject buttons. (3) **Bank statement (<=3 months)** — Submitted, fica-bank.pdf attached, Download + Accept/Reject buttons. Contact: Sipho Dlamini, sipho.portal@example.com. Progress: 0/3 accepted. Due: Jun 6, 2026. |
| 5.3 | Verify each Download button is operational (no console errors on click). | **PASS** | Clicked all 3 Download buttons (fica-id.pdf, fica-address.pdf, fica-bank.pdf). Zero new console errors on any click. Headless Playwright won't persist the file but the handler fired without error. All errors in console are known OBS-201 (`/api/assistant/invocations` 404 — WONT_FIX-EXEMPT). |
| 5.4 | Accept each item in turn. Counter advances 0/3 -> 1/3 -> 2/3 -> 3/3. On third Accept, envelope auto-transitions to Completed. | **PASS** | Accepted sequentially: (1) ID copy -> status "Accepted", counter **1/3 accepted**, envelope still "In Progress". (2) Proof of residence -> status "Accepted", counter **2/3 accepted**, envelope still "In Progress". (3) Bank statement -> status "Accepted", counter **3/3 accepted**, envelope auto-transitioned to **Completed**. "Completed on May 30, 2026" stamp appeared. Accept/Reject buttons removed from all items after acceptance. No separate "Mark as Reviewed" button needed. |
| 5.5 | Matter Overview shows FICA status Done. Activity feed renders full audit trail. FICA Status Card "View request" link emits canonical `/org/{slug}/information-requests/{requestId}` route. | **PASS** | **FICA Status Card**: Status badge changed from "In Progress" to **"Done"**. Text: "Verified May 30, 2026". "View request" link: `/org/mathebula-partners/information-requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19` — correct canonical route (OBS-501 verification PASS). **Activity feed** (7-day lookback): "REQ-0001 completed -- all items accepted" (32s), "Bob Ndlovu accepted 'Bank statement'" (32s), "Bob Ndlovu accepted 'Proof of residence'" (46s), "Bob Ndlovu accepted 'ID copy'" (1m), 3x Sipho portal.request_item.submitted (8m), 3x Sipho portal.document.upload_initiated (8-9m), "Information request REQ-0001 sent" (16m), "Bob Ndlovu created information request REQ-0001" (16m). Full audit trail present. |
| 5.6 | Mailpit -> 3x per-item-accepted emails + 1x envelope-completed email to Sipho. | **PASS** | Mailpit shows 4 new emails to sipho.portal@example.com: (1) "Item accepted -- ID copy (Mathebula & Partners)", (2) "Item accepted -- Proof of residence (<=3 months) (Mathebula & Partners)", (3) "Item accepted -- Bank statement (<=3 months) (Mathebula & Partners)", (4) "Request REQ-0001 completed (Mathebula & Partners)". All 4 emails match the scenario spec exactly. |

---

## Day 5 — Portal-side post-completion spot-check `[PORTAL]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 5.PC1 | Portal `/requests` row for REQ-0001 shows status badge COMPLETED and counter 3/3 accepted (OBS-502 fix verification). | **PASS** | Context-swapped to portal (:3002), authenticated Sipho via fresh magic-link (`POST /portal/auth/request-link`). `/requests` page: REQ-0001 row shows "COMPLETED" badge and "3/3 accepted" counter. NOT "0/3 submitted" — OBS-502 fix verified. |
| 5.PC2 | Detail page header reads "3/3 accepted - status COMPLETED". | **PASS** | Clicked into REQ-0001 detail at `/requests/0e800982-...`. Header: "REQ-0001" / "Dlamini v Road Accident Fund" / **"3/3 accepted - status COMPLETED"**. All 3 items show "Submitted -- status: ACCEPTED". |

---

## Day 5 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Three uploaded documents retrievable firm-side | **PASS** | All 3 PDFs (fica-id.pdf, fica-address.pdf, fica-bank.pdf) visible with Download buttons on the info-request detail page. Download handler fired without errors on all 3. |
| Info request lifecycle: Sent -> IN_PROGRESS -> Completed | **PASS** | Envelope started at IN_PROGRESS (from Day 4 portal submissions). Per-item Accept advanced counter 0/3 -> 1/3 -> 2/3 -> 3/3. On third Accept, envelope auto-transitioned to **Completed** with "Completed on May 30, 2026" stamp. No separate "Mark as Reviewed" button — per-item Accept is the mechanism. |
| Matter FICA/KYC status indicator updated; FICA card "View request" link routes to `/information-requests/{id}` (OBS-501 fix verification) | **PASS** | FICA Status Card on matter Overview: status "Done", "Verified May 30, 2026". "View request" link: `/org/mathebula-partners/information-requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19` — canonical route confirmed (OBS-501 PASS). |
| Portal-side spot-check: REQ-0001 shows COMPLETED + 3/3 accepted (OBS-502 fix verification) | **PASS** | Portal `/requests`: COMPLETED badge + "3/3 accepted". Detail page: "3/3 accepted - status COMPLETED". All items "Submitted -- status: ACCEPTED". OBS-502 fix verified. |

---

## Console Errors

3x 404 errors for `/api/assistant/invocations` — all are the known OBS-201 (WONT_FIX-EXEMPT, AI assistant endpoint not wired in KC mode). These fire on the matter detail page for `contextEntityType=project`. No user-facing impact.

**Zero JavaScript/hydration/rendering errors during Day 5 execution (firm or portal).**

## Gaps Filed

None. Day 5 passed cleanly with zero new gaps.

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Matter "Dlamini v Road Accident Fund" ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter Reference**: RAF-2026-001
- **Info Request REQ-0001 ID**: `0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`
- **Info Request REQ-0001 Status**: 3/3 accepted, envelope **COMPLETED** (firm review complete)
- **FICA Status on Matter Overview**: **Done** — "Verified May 30, 2026"
- **Notification emails sent**: 4 (3x per-item-accepted + 1x envelope-completed)
