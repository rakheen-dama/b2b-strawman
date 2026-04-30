# Day 7 — Firm drafts + sends proposal (LSSA tariff fee estimate) — Checkpoint Results

**Date**: 2026-04-30
**QA Agent**: cycle 7 (post OBS-501/502 fix)
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (firm `:3000`, portal `:3002`, backend `:8080`, gateway `:8443`, KC `:8180`, Mailpit `:8025`)

---

## Verifications (carried over from Day 5 fix cycle)

### OBS-501 — Matter FICA Status Card "View request" link → /information-requests/{id}

- Logged in firm `:3000` as Bob Ndlovu (Admin) via Keycloak.
- Navigated to matter RAF-2026-001 → Overview tab.
- FICA Status Card on the right rail of Overview shows "FICA Done — Verified Apr 30, 2026 — View request".
- Inspected DOM: `<a href="/org/mathebula-partners/information-requests/7f8f9422-e8ae-4966-976e-85f90199d6c2">` (canonical segment, not `/requests/`).
- Clicked View request → navigated to `http://localhost:3000/org/mathebula-partners/information-requests/7f8f9422-e8ae-4966-976e-85f90199d6c2`.
- Page title `REQ-0001`, status `Completed`, progress `3/3 accepted`, all 3 items render with `Accepted` chip + downloadable file (`fica-id.pdf`, `fica-address.pdf`, `fica-bank.pdf`).
- NOT a 404. No console errors.

**Evidence**: `qa_cycle/evidence/day-07/obs-501-verify-fica-card-link-overview.png`, `obs-501-verify-fica-card-link.png`.
**Status**: FIXED → **VERIFIED**.

### OBS-502 — Portal envelope counter shows accepted/total when COMPLETED

- Standing portal session as Sipho Dlamini still valid (post-Day-5 logged in).
- Visited `/requests` index — single row "REQ-0001 — Dlamini v Road Accident Fund — COMPLETED — 3/3 accepted". Counter literal reads "3/3 accepted" (not "0/3 submitted"). PASS.
- Visited `/requests/7f8f9422-e8ae-4966-976e-85f90199d6c2` detail — header reads "3/3 accepted • status COMPLETED"; each item renders "Submitted — status: ACCEPTED". PASS.
- Zero console errors on both pages.

**Evidence**: `qa_cycle/evidence/day-07/obs-502-verify-portal-list.png`, `obs-502-verify-portal-detail.png`.
**Status**: FIXED → **VERIFIED**.

---

## Day 7 Execution

### Pre-flight: Logged out as Bob, logged in as Thandi (Owner)

- Firm `:3000` user-menu → Sign out → redirected to `/`.
- Re-navigated to matter, KC challenge appeared, signed in as `thandi@mathebula-test.local` / `SecureP@ss1`.
- Landed on `/dashboard` → drove to `/org/mathebula-partners/projects/b7e319f7-fd7e-4526-a8b3-b40b1f85b34b`.

### 7.1 — Navigate to matter RAF-2026-001 → click + New Proposal

- The matter Overview action bar exposes a button labelled **"New Engagement Letter"** (legal-za terminology mapping: `Proposal → Engagement Letter`). There is no separate "+ New Proposal" CTA, AND no Proposals tab on the matter sidebar — proposals are managed at the org-level page `/org/{slug}/proposals` (titled "Engagement Letters" via terminology mapping).
- Clicked **New Engagement Letter** on the matter → modal dialog opened with `Client = Sipho Dlamini` (disabled / pre-filled from matter context). PASS — entry point reachable.

### 7.2 — Proposal template dropdown (Litigation Engagement — RAF) — **NOT IMPLEMENTED**

- The "New Engagement Letter" dialog has only these inputs: `Title`, `Client`, `Fee Model` (Fixed Fee / Hourly / Retainer / Contingency), `Hourly Rate Note (optional)`, `Expiry Date (optional)`. Some fee models swap to `Retainer Amount + Currency + Hours Included`.
- **There is NO template-selection dropdown.** Verified via DOM inspection of `[role="dialog"]` and source-grep of `frontend/components/proposals/create-proposal-dialog.tsx` (only occurrence of "template" is the i18n `TerminologyText template="…"` substitution prop, not a feature template picker).
- The legal-za doc-template pack (`backend/src/main/resources/template-packs/legal-za/pack.json`) DOES define a document template `engagement-letter-litigation` titled "Engagement Letter — Litigation" with `category: "ENGAGEMENT_LETTER"`, but it surfaces via the matter's "Generate Document" action — it produces a **document**, not a **proposal**. Document templates and proposal authoring are two different code paths in this build.
- Filed **OBS-701** (proposal authoring lacks template/library integration) — see status.md.
- Marked checkpoint FAIL but proceeded with available authoring fields.

### 7.3 — Fee estimate section pre-populates LSSA tariff line items — **NOT IMPLEMENTED**

- No fee-estimate line-item builder in the dialog. Hourly fee model exposes only a single free-text `Hourly Rate Note` input — no per-line tariff picker, no totals calculation, no VAT line.
- The matter has a "Fee Estimate" tab (`Budget` mapped to "Fee Estimate" for legal-za) — that's a separate matter-level budget surface, not a proposal-attached estimate.
- **Folded under OBS-701** (same root: proposal authoring is a thin lifecycle wrapper, not a document-builder).
- FAIL — workaround: stuffed the rate breakdown into the free-text `Hourly Rate Note`.

### 7.4 — Adjust estimated hours: 30h Bob + 5h Thandi = ZAR estimate — **NOT IMPLEMENTED**

- No hours-input → no auto-calculation. Used the `Hourly Rate Note` to encode the breakdown as plain text:
  > `R 2 500/hr (LSSA tariff High Court Party-and-Party 2024/2025) — 30h Bob Ndlovu (attorney) + 5h Thandi Mathebula (senior partner) ≈ R 87 500.00 estimate.`
- FAIL — folded under OBS-701.

### 7.5 — Add engagement scope in Tiptap editor — **NOT IMPLEMENTED**

- No Tiptap editor (or any rich-text scope field) on the proposal dialog.
- Verified `frontend/components/proposals/create-proposal-dialog.tsx` — there is NO `<Editor>`, `<RichText>`, `useEditor`, or `@tiptap/*` import.
- FAIL — folded under OBS-701.

### 7.6 — Effective date / expiry — partial

- No "effective date" field. There IS an `Expiry Date (optional)` field. Set expiry = 2026-05-12 (Day 17 from scenario Day 0 of 2026-04-25; Day 17 = 2026-05-12).
- The detail page renders "Expires May 13, 2026" — display shows +1 day vs the ISO input. Likely UTC-vs-local-tz formatter quirk; flagged as **OBS-702** (cosmetic).
- PARTIAL.

### 7.7 — Click Save → Draft

- Submitted dialog → POST `/api/proposals` (verified backend log `Created proposal 9042d2a4-aa3f-45ba-9a58-cf0cfe8988b0 (PROP-0001) for customer a30bb16b-743c-45a5-9fb5-13167fb92fde`) → redirected to `/org/mathebula-partners/proposals/9042d2a4-aa3f-45ba-9a58-cf0cfe8988b0` — **status badge = Draft**, PROP-0001 reference number assigned. PASS.
- Evidence: `qa_cycle/evidence/day-07/day-07-dialog-filled.png`, `day-07-proposal-draft.png`.

### 7.8 — Click Send for Acceptance → confirmation → Confirm

- Detail page exposes a **Send Proposal** button (top-right). Clicked it → "Send Proposal" sub-dialog opened with `Recipient = Select a contact` combobox.
- Opened combobox → only option `Sipho Dlamini (sipho.portal@example.com)`. Selected.
- Clicked **Send** → dialog closed, page reloaded.

### 7.9 — Status transitions to Sent, acceptance URL generated

- Detail page header now shows status badge **Sent** with `Sent: Apr 30, 2026` field added to Proposal Details. Action button changed to `Withdraw`. PASS.
- Backend log confirms: `Sent proposal 9042d2a4-aa3f-45ba-9a58-cf0cfe8988b0 to contact c99db0e9-6745-465e-a542-3c842e829758` and `Portal sync completed for proposal PROP-0001 after commit`.
- Evidence: `qa_cycle/evidence/day-07/day-07-proposal-sent.png`.

### 7.10 — Mailpit → proposal email to sipho.portal@example.com — **NO EMAIL SENT**

- Polled Mailpit `GET /api/v1/messages` immediately after Send and again after a 5-second + 8-second delay — message count remained 11 (top message: "Request REQ-0001 completed (Mathebula & Partners)" at `2026-04-30T06:34:35Z`). The proposal was sent at `2026-04-30T07:26:25Z`. **No new email arrived for Sipho.**
- Backend log shows: `AutomationActionExecutor — Scheduled action 6f91c5a8-… (type SEND_NOTIFICATION) for execution at 2026-05-05T07:26:25Z` — that's a **5-day-out follow-up** ("Proposal Follow-up (5 days)"), not an immediate "proposal sent" email.
- The email template `backend/src/main/resources/templates/email/portal-new-proposal.html` exists but is **not invoked on send**. No `ProposalEmailService` or equivalent handler appears in the `ProposalService.send` path; only `ProposalPortalSyncEventHandler` (the in-product portal projection sync) fires.
- Filed **OBS-703** (Send Proposal does not dispatch portal email — portal contact has no out-of-band notification of new proposal).
- FAIL.

### 7.11 — Verify email body contains click-through link — **N/A**

- No email body to verify. FAIL by dependency on 7.10.

### Compensating verification: portal /proposals shows the new proposal

- Switched to portal `:3002` as Sipho (standing session valid).
- `/proposals` renders "Awaiting Your Response" section with single row: `PROP-0001 — Engagement Letter — Litigation (Dlamini v RAF) — SENT — 30 Apr 2026 — - — View`. PASS — firm→portal sync IS working at the projection level even without email.
- Evidence: `qa_cycle/evidence/day-07/day-07-portal-proposals-list.png`.
- Zero console errors.

---

## Day 7 Checkpoint Summary

| Checkpoint | Result | Notes |
|------------|--------|-------|
| Proposal template from legal-za doc-template pack is instantiable | **FAIL** | OBS-701 — no template/library integration in proposal authoring |
| LSSA tariff line items render in fee estimate (tariff integration verified) | **FAIL** | OBS-701 — no fee-estimate line-item builder; only free-text rate note |
| Proposal dispatched, magic-link / secure link email sent to portal contact | **FAIL** | OBS-703 — Send Proposal does not invoke email service; portal projection works but contact has no email notification |

## Console Hygiene

- Firm: 1 hydration mismatch console error on `/org/{slug}/proposals` index page (Engagement Letters page). Filed as **OBS-704**.
- Firm matter Overview, FICA card detail navigation, proposal authoring/sending: zero console errors.
- Portal `/requests`, `/requests/[id]`, `/proposals`: zero console errors.

## New Gaps Filed

- **OBS-701** (bug) — Proposal authoring has no template/document-pack integration; missing fee-estimate line-item builder; no Tiptap scope editor. Day 7.2-7.5 cannot be performed as written. Severity: bug (blocks Day 7 fidelity but workable via free-text fields).
- **OBS-702** (nit) — Expiry date displays +1 day vs ISO input (likely UTC↔local tz formatter). Cosmetic; ISO `2026-05-12` displays as "May 13, 2026".
- **OBS-703** (bug) — `Send Proposal` does not dispatch a portal email to the recipient. Backend has the template (`portal-new-proposal.html`) but no service invocation on the send path; only a 5-day follow-up automation is scheduled. Day 8 (Sipho clicks email link) is therefore blocked unless we substitute the portal `/proposals` list for the email entry point.
- **OBS-704** (nit) — Hydration mismatch on `/org/{slug}/proposals` page (Engagement Letters index). The mismatch is on the `<button>` rendered by the New-Proposal `DialogTrigger`. Minor — page still works.

## QA Position

- **Day 7 BLOCKED on OBS-701 + OBS-703** for full-fidelity scenario execution. The available authoring surface produced a Sent proposal that's visible on the portal (Day 8.1 alternative path: portal `/proposals` → click View). Email-link click-through (Day 8.1 canonical) cannot be tested.
- Recommend: Product triage of OBS-701/702/703/704 → if OBS-703 is fixed, Day 8 can run via canonical email-link path; OBS-701 is a larger feature gap that may end up WONT_FIX with scenario amendment (mirror Day 4's OBS-401/402/403 pattern of recognising the proposal product surface as canonical and rewriting Day 7 to fit).
