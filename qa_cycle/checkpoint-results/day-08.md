# Day 8 — Sipho reviews + accepts proposal (engagement letter) — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Sipho Dlamini (returning portal contact). Reused the Day 4/7 magic-link portal session on :3002 — still valid, header showed "Sipho Dlamini" logged in, zero re-auth needed. Email-link entry verified: Mailpit proposal email `nso7TsKUKSxPWXvKkDhXwR` (subject "Mathebula & Partners: New proposal PROP-0001 for your review") carries the single CTA link `http://localhost:3002/proposals/6a1b35fc-b342-4101-abd7-f2ab8ffad26e`; navigating there loaded the proposal directly (session valid, no re-exchange).
**Driver**: QA agent via Playwright MCP — portal browser UI only on :3002; Mailpit API used only to confirm the proposal email + extract the link; backend log read for the acceptance-event + orchestration confirmation.
**Pre-checks**: svc.sh status — backend (PID 45933) / gateway / frontend / portal all RUNNING+HEALTHY.
**Result**: **9/9 in-scope checkpoints PASS (8.6 N/A — inline accept, no token route) + 4/4 summary checkpoints PASS. Zero new gaps.**

## Created / changed Day 8
- Proposal / Engagement Letter **PROP-0001** (`6a1b35fc-b342-4101-abd7-f2ab8ffad26e`) status **SENT → ACCEPTED** by contact Sipho Dlamini (`793df2fa-6350-46af-b0c0-8b3ac0d7d855`).
- Acceptance orchestration auto-provisioned a new matter/project **`15a25aa5-11e3-46fe-b90b-fbacf19c5bf1`** (lead member `ca39e4b1-…`) — firm-side projection to be verified Day 10.

## Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 8.1 | Mailpit → open proposal email → click proposal link → lands on `/proposals/[id]` on portal | PASS | Mailpit `nso7TsKUKSxPWXvKkDhXwR` (to sipho.portal@example.com) contains single proposal CTA → `http://localhost:3002/proposals/6a1b35fc-…`. Navigated there; page loaded as Sipho (banner "Sipho Dlamini"), no re-auth / no `/auth/exchange` round-trip — magic-link session from Day 4/7 still valid. |
| 8.2 | Proposal detail renders: scope, fee, effective/expiry dates, Accept/Decline buttons | PASS | Heading "Engagement Letter — Litigation (Dlamini v RAF)", badge **SENT**, PROP-0001, "Sent: 13 Jun 2026", "Expires: 20 Jun 2026". **Fee Details** (Fee Model = Hourly Rate). **Engagement Letter Details** body: "Dear Sipho Dlamini," + Fee Arrangement ("Fees will be charged on an hourly basis"; Rate note "R 2,500/hr (LSSA tariff…) ≈ R 87,500.00 estimate"; "This proposal expires on 2026-06-20"; standard T&Cs). Buttons **Accept Engagement Letter** + **Decline** present. |
| 8.3 | Fee estimate renders with ZAR symbol + VAT 15% line | PASS-with-exemption (OBS-701) | Fee is HOURLY model: ZAR currency present in the rate note ("R 2,500/hr … ≈ R 87,500.00"). No structured fee-line table / no explicit VAT 15% line — this is the **OBS-701** carry-over (fee-estimate structure/VAT line absent on portal proposal view; WONT_FIX from prior cycle). Not re-filed. |
| 8.4 | 📸 Screenshot `day-08-proposal-review.png` | PASS | Saved (full page, SENT state with Accept/Decline). |
| 8.5 | Click Accept → confirmation (dialog or inline) | PASS | Clicked "Accept Engagement Letter". Inline confirm — page transitioned in place to accepted state (no modal). |
| 8.6 | (If `/accept/[token]` route) complete acceptance step | N/A | This tenant does NOT route portal accept through `/accept/[token]`. Accept is a single inline action on `/proposals/[id]`. Not applicable — not counted as PASS or FAIL. |
| 8.7 | Confirm acceptance → status **ACCEPTED**, timestamp + actor recorded | PASS | Badge **SENT → ACCEPTED**; inline banner "Thank you for accepting this engagement letter. Your matter has been set up." Backend `.svc/logs/backend.log` (11:24:11Z, tenant `tenant_5039f2d497cf`): `Proposal 6a1b35fc-… accepted by contact 793df2fa-…` (actor = Sipho) → `Created project 15a25aa5-… with lead member ca39e4b1-…` → `Orchestration complete for proposal 6a1b35fc-…: project=15a25aa5-…` → `Portal accept completed…` → `Post-commit actions completed for accepted proposal PROP-0001`. |
| 8.8 | 📸 Screenshot `day-08-proposal-accepted.png` — success state | PASS | Saved (full page, ACCEPTED badge + "Thank you for accepting…" confirmation). |
| 8.9 | `/home` → "Pending proposals" no longer shows this proposal | PASS | Portal `/home`: surface cards = Pending info requests **0**, Upcoming deadlines **0**, Recent fee notes ("No fee notes yet."), Last trust movement ("No recent activity"). No pending-proposal entry for PROP-0001. |
| 8.10 | `/proposals` list → accepted proposal shows "Accepted" badge / moves to Past | PASS | Portal `/proposals` table: row PROP-0001 / "Engagement Letter — Litigation (Dlamini v RAF)" / status **ACCEPTED** / Sent 13 Jun 2026 / Fee **-** (OBS-701) / View. No longer under "Awaiting Your Response". |

## Day 8 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal accessible via email link without re-auth (magic-link valid OR transparent re-exchange) | PASS | Mailpit CTA link opened the proposal directly as Sipho; no Keycloak, no `/auth/exchange` re-prompt — Day 4/7 magic-link session still live. |
| Acceptance recorded (firm verifies Day 10) | PASS | Backend: single `accepted by contact 793df2fa-…` event + orchestration created matter `15a25aa5-…`. 0 ERROR/WARN at accept time. Firm-side projection deferred to Day 10. |
| No double-accept bug: re-visiting shows already-accepted state, not a second transition | PASS | After accept, Accept/Decline buttons gone. Reloaded `/proposals/[id]` → badge **ACCEPTED**, banner "This engagement letter has been accepted.", **no action buttons** — no re-accept path. Backend `accepted by contact` count for this proposal = **exactly 1**. |
| Terminology consistent | PASS-with-note | Portal chrome uses the established **legal-za term mapping "Engagement Letter"** (sidebar, page heading, breadcrumb "Back to engagement letters") — same convention verified Day 7. Body copy still reads "proposal" ("This proposal expires…", "subject to our standard terms"). The proposal↔Engagement Letter mapping is the intended legal-za vertical terminology, not a defect; consistent with Day 7. |

## Console notes
- **Portal side (:3002)** — current `/proposals/[id]` accepted-state page: **0 errors, 0 warnings**. `/home` and `/proposals` index also clean for Day 8.
- The `all:true` console aggregate surfaced only **`localhost:3000` (firm-side)** 404s carried over from Day 5/7 navigation: OBS-201 `/api/assistant/invocations?...PENDING_APPROVAL` 404s and OBS-506 `/api/assistant/specialists/INTAKE/sessions` 404 + `[SpecialistLauncher] startSession failed`. None are on the portal :3002 Day 8 flow. Both exempt/known carry-overs.
- No real JavaScript/hydration/render errors on the portal during Day 8.

## Carry-over exemptions observed (not re-filed)
- **OBS-701**: portal proposal view shows no structured fee-estimate / VAT 15% line — confirmed by Fee Details (Fee Model only) + Fee column "-" on `/proposals`. Expected, WONT_FIX carry-over.
- **OBS-201 / OBS-506**: `/api/assistant/*` 404s + uppercase specialist-id launcher 404 — firm-side (:3000) only, from prior-day navigation. Exempt, not re-filed.

## Gaps filed
None. Day 8 passed cleanly with zero new gaps.

## Note for Day 10
Acceptance orchestration auto-created matter/project `15a25aa5-11e3-46fe-b90b-fbacf19c5bf1` (separate from the Day 3 RAF matter `08ad56c4-…`). Day 10 ("Firm activates matter, deposits trust funds") should confirm the firm-side projection of the accepted engagement letter and reconcile which matter the firm activates.
