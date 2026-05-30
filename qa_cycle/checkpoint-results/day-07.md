# Day 7 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner — signs proposals)

---

## Pre-check: Login as Thandi

Navigated to `http://localhost:3000/dashboard` -> redirected to Keycloak login at `:8180`. Entered `thandi@mathebula-test.local` / `SecureP@ss1`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Sidebar shows "TM" avatar, org "Mathebula & Partners", user "Thandi Mathebula" (thandi@mathebula-test.local). Dashboard shows 1 Active Matter ("Dlamini v Road Accident Fund"), recent activity with FICA completion events from Day 5.

---

## Day 7 — Firm drafts + sends proposal (engagement letter) `[FIRM]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 7.1 | Navigate to matter RAF-2026-001 -> click + New Engagement Letter | **PASS** | Navigated to `/org/mathebula-partners/projects/d80aeac5-d5f4-4690-9291-193f05e3785d`. Matter detail page with header card: "Dlamini v Road Accident Fund", badges Active + Litigation, ref RAF-2026-001, client "Sipho Dlamini". Clicked "More actions" overflow menu -> "New Engagement Letter" menu item (legal-za term map for "+ New Proposal"). Dialog opened. |
| 7.2 | Dialog opens with Client pre-filled = Sipho Dlamini (disabled) | **PASS** | "New Engagement Letter" dialog shows Client combobox displaying "Sipho Dlamini" with `disabled` attribute (pre-filled from matter context, not editable). Dialog subtitle: "Create a engagement letter for a client engagement." |
| 7.3 | Set Title = "Engagement Letter — Litigation (Dlamini v RAF)" | **PASS** | Title textbox filled with "Engagement Letter — Litigation (Dlamini v RAF)". Placeholder was "e.g. Annual Audit Proposal". |
| 7.4 | Fee Model = Hourly; set Hourly Rate Note | **PASS** | Fee Model combobox pre-selected as "Hourly" (legal-za default). Hourly Rate Note filled with "R 2,500/hr (LSSA tariff High Court Party-and-Party 2024/2025) — 30h Bob Ndlovu (attorney) + 5h Thandi Mathebula (senior partner) ≈ R 87,500.00 estimate." |
| 7.5 | Set Expiry Date = Day 17 (2026-06-16) | **PASS** | Expiry Date input (`type="date"`) filled with `2026-06-16`. Accepted ISO format correctly. |
| 7.6 | Click Create Proposal -> redirected to proposal detail; status = Draft, PROP-0001 | **PASS** | Clicked "Create Engagement Letter". Redirected to `/org/mathebula-partners/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`. Status badge: **Draft**. Reference: **PROP-0001**. Proposal Details: Fee Model=Hourly, Hourly Rate=full text, Created=May 30, 2026, Expires=Jun 16, 2026. Breadcrumb shows "Engagement Letters" (legal terminology). |
| 7.7 | Click Send Proposal -> select Sipho Dlamini -> Send | **PASS** | Clicked "Send Proposal" button. "Send Proposal" dialog opened with Recipient combobox showing "Select a contact". Clicked combobox -> listbox shows single option: "Sipho Dlamini (sipho.portal@example.com)". Selected Sipho. "Send" button enabled. Clicked "Send". |
| 7.8 | Status transitions to Sent; Sent date appears; action button = Withdraw | **PASS** | After Send: status badge changed from "Draft" to **Sent**. New "Sent: May 30, 2026" field appeared in Proposal Details. Action button changed from "Send Proposal" to **Withdraw**. Notification count incremented from 2 to 3. |
| 7.9 | Backend log confirms send + portal sync | **PASS** | Backend logs confirm: (1) `Sent proposal 40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86 to contact 02a7bed0-eb20-4771-866f-842a4138e7ce`, (2) `Portal sync completed for proposal PROP-0001 after commit`, (3) `Portal notification sent template=portal-new-proposal contact=02a7bed0-eb20-4771-866f-842a4138e7ce to=sipho.portal@example.com`. Automation rule "Proposal Follow-up (5 days)" also triggered. |
| 7.10 | Mailpit -> proposal email to sipho.portal@example.com with portal link (OBS-703 fix) | **PASS** | Email received at `sipho.portal@example.com`. Subject: "Mathebula & Partners: New proposal PROP-0001 for your review". Body contains: "New Proposal for Your Review", "Hi Sipho Dlamini", "PROP-0001", "Engagement Letter — Litigation (Dlamini v RAF)", "View Proposal" link to `http://localhost:3002/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`, "This proposal will expire on 16 June 2026". OBS-703 fix verified: email contains click-through to portal proposal URL. |
| 7.11 | Portal `/proposals` shows PROP-0001 in "Awaiting Your Response" with SENT status | **PASS** | Context swap to portal (:3002). Authenticated as Sipho via fresh magic-link. Navigated to `/proposals` (sidebar: "Engagement Letters"). Page heading: "Engagement Letters". Section heading: "Awaiting Your Response". Table row: PROP-0001 / "Engagement Letter — Litigation (Dlamini v RAF)" / status **SENT** / 30 May 2026 / View link to `/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`. Firm->portal projection working correctly. |

---

## Day 7 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal lifecycle: Draft -> Sent succeeds end-to-end | **PASS** | Full lifecycle: overflow menu "New Engagement Letter" -> fill form (Title, Fee Model=Hourly, Rate Note, Expiry) -> Create -> Draft (PROP-0001) -> Send to Sipho -> Sent. All transitions clean, no errors. |
| Portal email dispatched (OBS-703) — subject + body verified, link reaches `/proposals/{id}` on portal | **PASS** | Email subject: "Mathebula & Partners: New proposal PROP-0001 for your review". Body: proper branding, proposal details, "View Proposal" CTA linking to `http://localhost:3002/proposals/40e7fd6b-...`. Link format correct (portal domain + proposal UUID path). |
| Portal `/proposals` projection shows PROP-0001 (firm->portal sync) | **PASS** | Portal `/proposals` page shows PROP-0001 under "Awaiting Your Response" with SENT status, correct title, sent date. Backend log confirmed `Portal sync completed for proposal PROP-0001 after commit`. |
| Frontend console clean (no hydration mismatch on `/proposals` index — OBS-704) | **PASS** | Firm side: only `/api/assistant/invocations` 404 errors (known OBS-201 WONT_FIX-EXEMPT). Portal side: zero JS errors (only favicon.ico 404 cosmetic). No hydration mismatches on either firm or portal `/proposals` pages. |
| Expiry date renders consistently (no +1-day tz drift — OBS-702) | **PASS** | Input: `2026-06-16` (ISO date). Firm detail: "Jun 16, 2026". Email body: "16 June 2026". No timezone drift detected. All three representations refer to the same date. |

---

## Console Errors

### Firm side (during Day 7 execution)
- 4x `/api/assistant/invocations` 404 — known OBS-201 (WONT_FIX-EXEMPT, AI assistant endpoint not wired in KC mode). Non-blocking.
- Zero JavaScript/hydration/rendering errors.

### Portal side (during checkpoint 7.11 verification)
- 1x `favicon.ico` 404 — cosmetic.
- 1x `portal/auth/exchange` 401 — from expired token attempt (QA procedural, not product bug).
- Zero JavaScript/hydration/rendering errors.

## Gaps Filed

None. Day 7 passed cleanly with zero new gaps.

## Entity IDs (for downstream days)

- **Proposal ID**: `40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`
- **Proposal Reference**: PROP-0001
- **Proposal URL (firm)**: `/org/mathebula-partners/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`
- **Proposal URL (portal)**: `/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`
- **Portal Contact ID (Sipho)**: `02a7bed0-eb20-4771-866f-842a4138e7ce`
- **Proposal Email ID (Mailpit)**: `iSrVSUpZMRgoYWrS5CRW3M`
