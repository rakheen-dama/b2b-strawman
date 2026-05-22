# Day 11 Checkpoint Results — Sipho sees trust balance on portal `[PORTAL]`

**Date**: 2026-05-21
**Actor**: Sipho Dlamini (portal contact, sipho.portal@example.com)
**Stack**: Portal :3002, Backend :8080, Mailpit :8025
**Auth**: Fresh magic link via `POST /portal/auth/request-link` -> token exchange at `/auth/exchange`

---

## Checkpoint Results

### 11.1 — Trust-deposit nudge email arrived
**PASS**

- Mailpit message ID: `Sh2rCoJathH4tkDu9o93bF`
- Subject: "Mathebula & Partners: Trust account activity"
- From: `noreply@kazi.app` (correct Kazi branding, not docteams.app)
- To: `sipho.portal@example.com`
- Body contains:
  - Firm name: "Mathebula & Partners"
  - Greeting: "Hi Sipho Dlamini"
  - Description: "A new transaction has been recorded in your trust account."
  - Date: 21 May 2026
  - Type: DEPOSIT
  - Amount: R 50 000,00
  - CTA link: `http://localhost:3002/trust/85b09bb3-5cdd-42b9-8364-1bea1e83153d`

### 11.2 — Click "View trust balance" link lands on `/trust`
**PASS**

- Email CTA "View trust ledger" links to `http://localhost:3002/trust/85b09bb3-5cdd-42b9-8364-1bea1e83153d`
- Page loads successfully as the matter-specific trust ledger page
- Portal sidebar shows Trust nav item highlighted
- "Back to trust" link at top of page navigates to `/trust` index (which auto-redirects to the only matter with trust activity)

### 11.3 — `/trust` renders: trust balance card, recent deposits list, ledger preview
**PASS**

Page layout (top to bottom):
1. **Trust balance card**: "Trust balance" header, "Current balance" label, **R 50 000,00** amount, "As of 21 May 2026" date
2. **Recent deposits list** (mobile-friendly card view): 1 deposit listed with description, date, amount, running balance
3. **Transaction table** (desktop view): columns Date | Type | Description | Amount | Running balance — 1 row
4. **Statements section**: "No statement documents yet" (correct — no SoA generated yet)

### 11.4 — Trust balance card shows R 50,000.00
**PASS**

- Balance card displays: **R 50 000,00**
- Matches firm-side Day 10 posting exactly (R 50,000.00 deposited via DEP/2026/001)
- Date stamp: "As of 21 May 2026"
- Matter reference displayed as "Matter 85b09bb3" (truncated UUID — acceptable)

### 11.5 — Recent deposits list shows R50,000 deposit with source description
**PASS**

Transaction list entry:
- Description: "Initial trust deposit — RAF-2026-001" (client-safe copy, no internal tags)
- Date: "21 May 2026 · DEPOSIT"
- Amount: R 50 000,00
- Running balance: "Bal R 50 000,00"

Description sanitisation verified:
- No `[internal]` tags present
- Description is client-facing and readable
- Length well under 140 chars
- Matter reference RAF-2026-001 included (appropriate for client visibility)

### 11.6 — Click into matter trust ledger, line-level history renders
**PASS**

The email link lands directly on the matter trust ledger (`/trust/85b09bb3-...`).
Line-level transaction table renders with:
- Date: 21 May 2026
- Type: DEPOSIT
- Description: Initial trust deposit — RAF-2026-001
- Amount: R 50 000,00
- Running balance: R 50 000,00

Only 1 transaction at this point (the Day 10 deposit). Table columns are correct and data is consistent.

### 11.7 — Screenshot
**NOTED** — Trust balance card with first deposit visible on `/trust/85b09bb3-...`. Page renders cleanly with balance card, transaction list, and transaction table all showing R 50 000,00.

### 11.8 — Currency rendered as R / ZAR
**PASS**

- All monetary values on the page use "R" prefix (South African Rand)
- Zero occurrences of `$`, `€`, or `£` on the page
- Three currency instances found: balance card (R 50 000,00), transaction amount (R 50 000,00), running balance (R 50 000,00)
- Format is "R XX XXX,XX" (space-separated thousands, comma decimal — standard ZA locale)

---

## Day 11 Summary Checkpoints

| Checkpoint | Description | Status |
|-----------|-------------|--------|
| Trust deposit visible on portal within 1 business day | Email sent same day as firm posting; portal shows deposit immediately | **PASS** |
| Amount matches firm-side Section 86 ledger | Portal R 50 000,00 = Firm R 50,000.00 (DEP/2026/001) | **PASS** |
| Description sanitisation | "Initial trust deposit — RAF-2026-001" — no internal tags, client-safe, < 140 chars | **PASS** |
| ZAR currency throughout | R prefix used exclusively, no $, EUR, GBP | **PASS** |

---

## Console Errors
**Zero** — No JavaScript errors on `/trust/85b09bb3-...` page load or navigation.

## New Gaps
**Zero** — No new gaps identified on Day 11.

## Observations
- The `/trust` index route auto-redirects to `/trust/{matterId}` when the portal contact has only one matter with trust activity. This is acceptable UX.
- The trust email subject uses "Trust account activity" rather than "trust deposit" / "funds received" / "trust balance update" as suggested in the scenario — functionally correct and clear.
- Matter reference in the balance card shows truncated UUID "Matter 85b09bb3" rather than the matter title. This is a minor display choice, not a bug — the full matter context is visible in the transaction descriptions.
- Email sender is `noreply@kazi.app` (OBS-707 fix verified continuing to hold).
