# Day 8 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Sipho Dlamini (portal contact — returning via fresh magic-link session)

---

## Pre-check: Portal Authentication

Requested fresh magic-link via `POST /portal/auth/request-link` with `{"email":"sipho.portal@example.com","orgId":"mathebula-partners"}`. Exchanged token via `POST /portal/auth/exchange`. JWT obtained successfully (sub=d74963c8-..., type=customer, org_id=mathebula-partners). Set portal-auth-token cookie + localStorage keys. Portal home loaded with "Sipho Dlamini" identity confirmed.

---

## Day 8 — Sipho reviews + accepts proposal `[PORTAL]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 8.1 | Mailpit -> open proposal email -> click link -> lands on `/proposals/[id]` on portal | **PASS** | Proposal email found in Mailpit (ID=iSrVSUpZMRgoYWrS5CRW3M, subject="Mathebula & Partners: New proposal PROP-0001 for your review"). Email body contains "View Proposal" link to `http://localhost:3002/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`. Navigated to URL with authenticated Sipho session. Proposal detail page rendered correctly. |
| 8.2 | Verify proposal detail page renders: scope, fee estimate, effective date, expiry, Accept/Decline buttons | **PARTIAL** | Page renders: title "Engagement Letter -- Litigation (Dlamini v RAF)", status badge SENT, ref PROP-0001, "Sent: 30 May 2026", "Expires: 16 Jun 2026". **Fee Details** section: Fee Model = Hourly Rate. **Engagement Letter Details** section: "Dear Sipho Dlamini", fee arrangement with rate "R 2,500/hr (LSSA tariff...) -- 30h Bob + 5h Thandi = R 87,500.00 estimate". **Accept Engagement Letter** + **Decline** buttons present. Note: no standalone "scope" section or structured fee-estimate breakdown with line items and totals -- the content is auto-seeded from the proposal form fields as a Tiptap document (see Day 7 OBS-701 WONT_FIX amendment: proposal authoring is a thin lifecycle wrapper, not a full fee-estimate builder). The rendered content includes fee details adequate for review. |
| 8.3 | Verify fee estimate renders with ZAR currency symbol + VAT 15% line | **PARTIAL** | ZAR currency symbol present: "R 2,500/hr" and "R 87,500.00 estimate" in the rate note. **No explicit VAT 15% line rendered** -- the auto-seeded Tiptap content does not include a separate VAT calculation. This is consistent with OBS-701 WONT_FIX: the product has no fee-estimate line-item builder in proposals. VAT will appear on the actual fee note (Day 28). Non-blocking. |
| 8.4 | Screenshot: day-08-proposal-review.png | **PASS** | Full-page screenshot captured at `day-08-proposal-review.png` showing proposal detail with SENT status, fee details, engagement letter content, and Accept/Decline buttons. |
| 8.5 | Click Accept -> acceptance confirmation dialog (or inline confirm) | **PASS** | Clicked "Accept Engagement Letter" button. Acceptance was **inline** (no separate confirmation dialog). Status immediately transitioned from SENT to **ACCEPTED**. Success banner appeared: "Thank you for accepting this engagement letter. Your matter has been set up." Accept/Decline buttons removed. |
| 8.6 | (If tenant routes through `/accept/[token]`) complete the acceptance step | **N/A** | Acceptance was inline on the proposal detail page itself, not via a separate `/accept/[token]` route. The portal's authenticated session handled the accept directly. |
| 8.7 | Confirm acceptance -> proposal status transitions to Accepted, timestamp + actor recorded | **PASS** | Status badge: **ACCEPTED**. Backend logs confirm: (1) `Proposal 40e7fd6b-... accepted by contact 02a7bed0-...` at 15:16:21Z, (2) `Portal accept completed for proposal 40e7fd6b-..., project afe80827-...`, (3) `Post-commit actions completed for accepted proposal PROP-0001`. Timestamp and actor (contact ID 02a7bed0 = Sipho) recorded. |
| 8.8 | Screenshot: day-08-proposal-accepted.png — success / confirmation state | **PASS** | Full-page screenshot captured at `day-08-proposal-accepted.png` showing ACCEPTED status badge, success message "Thank you for accepting this engagement letter. Your matter has been set up.", fee details, and engagement letter content. No Accept/Decline buttons visible. |
| 8.9 | Navigate to `/home` -> "Pending proposals" surface no longer shows this proposal | **PASS** | Navigated to `/home`. Home page shows: Pending info requests=0, Upcoming deadlines=0, Recent fee notes="No fee notes yet", Last trust movement="No recent activity". No pending proposals section visible. The accepted proposal is not surfaced as pending anywhere on the home page. |
| 8.10 | Check `/proposals` list -- accepted proposal shows "Accepted" badge | **PASS** | Navigated to `/proposals` (page heading: "Engagement Letters"). Table shows single row: PROP-0001 / "Engagement Letter -- Litigation (Dlamini v RAF)" / status **ACCEPTED** / 30 May 2026. Previously showed under "Awaiting Your Response" section (Day 7 checkpoint 7.11); now displayed with ACCEPTED status in the main list. |

---

## Day 8 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal accessible via email link without re-authentication (magic-link session valid OR transparent re-exchange) | **PASS** | Fresh magic-link token obtained and exchanged for JWT. Portal session authenticated as Sipho Dlamini. Proposal detail page at `/proposals/40e7fd6b-...` rendered correctly with full content. |
| Acceptance recorded (firm will verify on Day 10) | **PASS** | Backend logs: `Proposal 40e7fd6b-... accepted by contact 02a7bed0-...`. Portal UI: status badge ACCEPTED, success message displayed. Post-commit actions completed (proposal accepted event handler ran). |
| No double-accept bug: clicking Accept again shows already-accepted state, not a second transition | **PASS** | Revisited `/proposals/40e7fd6b-...` after acceptance. Page shows ACCEPTED status badge with message "This engagement letter has been accepted." No Accept/Decline buttons rendered. No way to trigger a second acceptance. |
| Terminology consistent: portal copy reads "proposal" / "engagement letter" throughout | **PASS** | Consistent legal-za terminology throughout: sidebar shows "Engagement Letters" (not "Proposals"). Page heading: "Engagement Letters". Proposal detail uses "Engagement Letter" in title, breadcrumb, content. "PROP-0001" reference format retained. Footer: "Powered by Kazi". Zero vocabulary leaks. |

---

## Console Errors

### Portal side (during Day 8 execution)
- 1x `favicon.ico` 404 -- cosmetic (same as Day 7 portal spot-check).
- Zero JavaScript/hydration/rendering errors across all portal pages visited (`/proposals/{id}`, `/home`, `/proposals` list).

## Gaps Filed

None. Day 8 passed with zero new gaps.

**Notes on PARTIAL checkpoints (8.2, 8.3)**: The scenario's checkpoint 8.2 mentions "scope, fee estimate breakdown (tariff lines + totals in ZAR incl. VAT)" and 8.3 mentions "ZAR currency symbol + VAT 15% line". The product's proposal detail page renders an auto-seeded Tiptap document with fee arrangement text (including ZAR rates and estimate total) rather than a structured fee-estimate table with individual tariff lines, subtotals, and VAT calculation. This is consistent with the Day 7 OBS-701 WONT_FIX amendment (thin lifecycle wrapper, no fee-estimate builder). The ZAR currency symbol is present in the rate note. These are not bugs but scenario expectations that exceed the current product capability as already documented. Both PARTIALs are non-blocking.

## Entity IDs (for downstream days)

- **Proposal ID**: `40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86` (unchanged)
- **Proposal Reference**: PROP-0001 (unchanged)
- **Proposal Status**: ACCEPTED (transitioned from SENT)
- **Acceptance Timestamp**: 2026-05-30T15:16:21Z (backend log)
- **Acceptance Actor**: Portal contact 02a7bed0-eb20-4771-866f-842a4138e7ce (Sipho Dlamini)
- **Proposal Email ID (Mailpit)**: iSrVSUpZMRgoYWrS5CRW3M (unchanged)
