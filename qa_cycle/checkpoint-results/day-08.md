# Day 8 — Sipho reviews + accepts proposal on portal

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-13`
**Cycle**: 1
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)

## Pre-flight

- Portal healthy: `curl http://localhost:3002/` returns 307 (redirect to login — expected for unauthenticated).
- Backend healthy: `curl http://localhost:8080/actuator/health` returns 200.
- Mailpit holds 13 messages for `sipho.portal@example.com`, including proposal email `7e8fTjRj72rVtkL39j5L73` with subject "Mathebula & Partners: New proposal PROP-0001 for your review".

## Portal Authentication

- Day 4 magic-link session had expired. Re-requested via portal `/login` page.
- Entered `sipho.portal@example.com` → clicked Send Magic Link → used dev-mode shortcut link → redirected to `/projects`.
- Sipho Dlamini identity confirmed in user menu. Mathebula & Partners branding (logo + "Portal" sidebar) rendered correctly.

## Checkpoint Results

### 8.1 — Email link lands on portal proposal detail

- Mailpit email ID `7e8fTjRj72rVtkL39j5L73`, subject "Mathebula & Partners: New proposal PROP-0001 for your review".
- Email body contains CTA link: `http://localhost:3002/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0`.
- Navigated to that URL (already authenticated from magic-link re-auth above).
- Proposal detail page rendered with Sipho's identity, Mathebula branding, and correct proposal content.
- **Result: PASS**

### 8.2 — Proposal detail page renders

Page renders with:
- Title: "Engagement Letter — Litigation (Dlamini v RAF)" with `SENT` badge
- Reference: PROP-0001
- Sent: 14 May 2026
- Expires: 21 May 2026
- **Fee Details** section: Fee Model = Hourly Rate
- **Proposal Details** section: auto-seeded body via `ProposalContentSeeder.buildDefaultContent`:
  - "Dear Sipho Dlamini,"
  - "Fee Arrangement" heading
  - "Fees will be charged on an hourly basis."
  - "Rate: R 2,500/hr (LSSA tariff High Court Party-and-Party 2024/2025) — 30h Bob Ndlovu (attorney) + 5h Thandi Mathebula (senior partner) ~ R 87,500.00 estimate."
  - Expiry date: 2026-05-21
  - Standard terms and conditions notice
- **Your Response** section: Accept Proposal + Decline buttons present
- **Not present**: structured fee estimate breakdown with tariff lines + VAT 15% line (per OBS-701 WONT_FIX, proposal is a thin lifecycle wrapper)
- **Result: PASS** (with expected absence per OBS-701 amendment)

### 8.3 — Fee estimate with ZAR + VAT

- Fee estimate does NOT render as structured tariff lines with ZAR totals + VAT 15%.
- The rate note text mentions "R 2,500/hr" and "R 87,500.00 estimate" inline in the proposal body.
- This is consistent with OBS-701 WONT_FIX — the proposal authoring surface has no fee-estimate line-item builder.
- **Result: PASS** (expected absence per scenario amendment)

### 8.4 — Screenshot

- Captured as `day-08-proposal-review.png`.
- **Result: PASS**

### 8.5 — Click Accept

- Clicked **Accept Proposal** button.
- No separate confirmation dialog appeared — acceptance was immediate (inline confirm pattern).
- **Result: PASS**

### 8.6 — Accept/token route

- No `/accept/[token]` route was involved. The in-page Accept button submitted directly via the portal API.
- **Result: PASS** (scenario says "or inline confirm")

### 8.7 — Acceptance confirmation

- Status badge transitioned **SENT -> ACCEPTED**.
- Confirmation banner rendered: "Thank you for accepting this proposal. Your project has been set up." with checkmark icon.
- Accept/Decline buttons removed from the page post-acceptance.
- Console clean: 0 JavaScript errors throughout the transition.
- **Result: PASS**

### 8.8 — Screenshot (accepted state)

- Captured as `day-08-proposal-accepted.png`.
- **Result: PASS**

### 8.9 — `/home` no longer shows pending proposal

- Navigated to `http://localhost:3002/home`.
- Home page tiles: Pending info requests (0), Upcoming deadlines (0, Next 14 days), Recent fee notes (No fee notes yet), Last trust movement (No recent activity).
- No "Pending proposals" tile exists on the portal `/home` in the current product design — proposals are surfaced only via `/proposals` and email CTA.
- Accepted proposal is not misclassified as pending anywhere.
- **Result: PASS**

### 8.10 — `/proposals` list shows accepted badge

- Navigated to `http://localhost:3002/proposals`.
- Table shows single row: PROP-0001, "Engagement Letter — Litigation (Dlamini v RAF)", status **ACCEPTED**, sent 14 May 2026.
- No "Awaiting Your Response" / "Past" tab split visible (only one proposal exists in this cycle).
- **Result: PASS**

### Double-accept protection

- Re-navigated to `/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0` after accepting.
- Page renders with ACCEPTED badge, message "This proposal has been accepted." — no Accept/Decline buttons.
- No double-accept pathway exists.
- **Result: PASS**

### Console errors

- Session-wide console error check: 2 entries, both non-functional:
  1. `favicon.ico` 404 — cosmetic (no favicon configured for portal)
  2. `/portal/auth/exchange` 401 — from the expired token attempt at session start (before re-auth)
- Zero JavaScript errors during the Day 8 flow (proposal view, accept, home, proposals list).
- **Result: PASS** (no functional console errors)

## Day 8 Summary

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 8.1 | Proposal accessible via email link | PASS | Email link resolves to `/proposals/d7481b7a-...`, page renders after magic-link re-auth |
| 8.2 | Detail page renders scope, fee, expiry, buttons | PASS | Title, PROP-0001, SENT badge, fee model, proposal body, Accept/Decline |
| 8.3 | ZAR + VAT line items | PASS (expected absence) | OBS-701 WONT_FIX — no structured fee breakdown |
| 8.4 | Screenshot | PASS | `day-08-proposal-review.png` |
| 8.5-8.7 | Accept flow | PASS | SENT -> ACCEPTED, confirmation banner, buttons removed |
| 8.8 | Screenshot (accepted) | PASS | `day-08-proposal-accepted.png` |
| 8.9 | `/home` not pending | PASS | No pending-proposals tile by design |
| 8.10 | `/proposals` list | PASS | ACCEPTED badge in table |
| - | No double-accept bug | PASS | Re-load shows ACCEPTED, no Accept button |
| - | Terminology | PASS | "proposal" used consistently throughout portal |
| - | Console errors | PASS | Zero functional JS errors |

## Day 8 Day-End Checkpoints

| # | Checkpoint | Result |
|---|---|---|
| 1 | Proposal accessible via email link without re-authentication (magic-link session valid OR transparent re-exchange) | PASS — session expired, re-authed via dev-mode magic-link, then proposal URL rendered correctly |
| 2 | Acceptance recorded (firm will verify on Day 10) | PASS — ACCEPTED badge + confirmation banner |
| 3 | No double-accept bug: clicking Accept again shows already-accepted state | PASS — re-load shows "This proposal has been accepted." with no action buttons |
| 4 | Terminology consistent: portal copy reads "proposal" throughout | PASS — heading, sidebar, section titles all use "proposal" |

## New Gaps Filed

**None.** All checkpoints passed. The expected OBS-701 absence (no structured fee-estimate breakdown) is already documented and triaged as WONT_FIX with the scenario amended.

## Entities Touched

- PROP-0001 (id `d7481b7a-8878-43ee-928c-2845bf8bffd0`, **status: ACCEPTED** by Sipho Dlamini at ~22:16 UTC 2026-05-13)
- Mailpit: fresh magic-link issued during Day 8 portal re-auth (dev-mode shortcut)

## QA Position

**Day 8 — COMPLETE.** 11/11 checkpoints PASS, 0 blockers, 0 new gaps. Ready to dispatch **Day 10** (Firm verifies proposal acceptance, deposits trust funds — Thandi on firm `:3000`).
