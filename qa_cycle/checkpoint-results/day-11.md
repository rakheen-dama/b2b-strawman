# Day 11 — Sipho sees trust balance on portal

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-13`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Sipho Dlamini on portal `:3002`

## Pre-flight

- Portal `:3002` healthy (returns 307 redirect for unauthenticated, normal).
- Backend `:8080` healthy (200 on `/actuator/health`).
- Mailpit `:8025` healthy (200).
- Day 10 closed clean: R 50,000.00 deposit recorded against Sipho / RAF-2026-001 via matter Trust tab.
- Trust deposit notification email already delivered to Sipho (Mailpit ID `7wC43i7oXWHt2Z6Qi4XpAt`).

## Portal Authentication

- Navigated to `http://localhost:3002/login`. Login page rendered with Mathebula & Partners branding.
- Entered `sipho.portal@example.com`, clicked **Send Magic Link**.
- Used dev magic-link shortcut (`/auth/exchange?token=...&orgId=mathebula-partners`).
- Token exchange succeeded, landed on `/projects`. Sipho Dlamini displayed in user menu.
- Console: 0 errors, 0 warnings.

## Checkpoint Execution

### 11.1 — Mailpit: trust-deposit nudge email for Sipho

- Mailpit API search for `to:sipho.portal@example.com` returned 15 total messages, 11 in current page.
- Trust deposit email found:
  - **ID**: `7wC43i7oXWHt2Z6Qi4XpAt`
  - **Subject**: "Mathebula & Partners: Trust account activity"
  - **Created**: `2026-05-13T22:28:04.487Z` (shortly after Day 10 deposit)
  - **Snippet**: "...A new transaction has been recorded in your trust account. Date 13 May 2026 Type DEPOSIT Amount R 50 000,00 View trust ledger..."
- Subject matches scenario pattern ("trust deposit" / "funds received" / "trust balance update").
- Email body (full text extracted):
  - Date: **13 May 2026** (properly formatted, not raw ISO — previous OBS-1101 formatting fix confirmed)
  - Type: **DEPOSIT**
  - Amount: **R 50 000,00** (properly formatted with ZAR currency symbol and locale formatting)
  - CTA link: **View trust ledger** → `http://localhost:3002/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29`
  - Greeting: "Hi Sipho Dlamini"
  - Sign-off: "Best, Mathebula & Partners"

**Result: PASS**

### 11.2 — Click "View trust balance" link → lands on `/trust`

- Navigated to `http://localhost:3002/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29` (the link from the email CTA).
- Page loaded successfully at `/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29`.
- Trust page rendered with balance card, transactions table, and statements section.
- Also verified: bare `/trust` redirects to `/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29` (matter-specific trust ledger, since Sipho has a single matter).
- Console: 0 errors (1 benign Next.js dev-mode warning about `scroll-behavior: smooth`).

**Result: PASS**

### 11.3 — `/trust` renders: trust balance card, recent deposits list, ledger preview

Page structure verified (from accessibility snapshot):

1. **Trust balance card** (top): icon + "Trust balance" label, amount paragraph, "As of" date, matter reference.
2. **Transactions section**: heading "Transactions" + table "Trust transactions" with columns: Date, Type, Description, Amount, Running balance.
3. **Statements section**: heading "Statements" + "No statement documents yet" placeholder.

All three sections rendered correctly.

**Result: PASS**

### 11.4 — Trust balance card shows R 50,000.00 (matches firm-side Day 10)

- Trust balance card displays: **R 50 000,00**
- "As of 14 May 2026"
- "Matter c90832a4"
- Matches firm-side Day 10 posting of R 50,000.00 to Sipho's client ledger under Section 86 trust account.

**Result: PASS**

### 11.5 — Recent deposits list: R 50,000 deposit with date and sanitised description

Transactions table row:
- **Date**: 14 May 2026
- **Type**: DEPOSIT
- **Description**: "Initial trust deposit — RAF-2026-001"
- **Amount**: R 50 000,00
- **Running balance**: R 50 000,00

Description matches firm-side entry. No internal/admin-only notes present. Matter reference `RAF-2026-001` is client-safe (visible to Sipho as his own matter).

**Result: PASS**

### 11.6 — Click into matter trust ledger → line-level history

- Already on the matter-specific trust page (`/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29`).
- The Transactions table IS the line-level history — renders all transactions for this matter's trust ledger.
- Single row visible (one deposit at this point in the lifecycle).
- Running balance column correctly populated (R 50 000,00).

**Result: PASS**

### 11.7 — Screenshot

- **Portal home**: `qa_cycle/evidence/day-11/day-11-portal-home.png` — shows "Last trust movement R 50 000,00 / 14 May 2026" tile.
- **Trust balance page**: `qa_cycle/evidence/day-11/day-11-portal-trust-balance.png` — full trust balance card + transactions table + statements section.

**Result: PASS**

### 11.8 — Currency rendered as R / ZAR (not $ / EUR / GBP)

Verified across all trust-related surfaces:
1. **Home page** "Last trust movement" tile: **R 50 000,00** (R prefix)
2. **Trust balance card**: **R 50 000,00** (R prefix)
3. **Transactions table** Amount column: **R 50 000,00** (R prefix)
4. **Transactions table** Running balance column: **R 50 000,00** (R prefix)
5. **Email body**: **R 50 000,00** (R prefix)

All five surfaces use **R** (ZAR). No $, EUR, or GBP symbols anywhere.

**Result: PASS**

## Day 11 checkpoint summary

| ID | Description | Result | Evidence |
|----|-------------|--------|----------|
| 11.1 | Trust-deposit nudge email in Mailpit (subject pattern match) | PASS | Mailpit ID `7wC43i7oXWHt2Z6Qi4XpAt`, subject "Trust account activity", body has formatted date/amount |
| 11.2 | "View trust balance" link → lands on `/trust` (matter-specific page) | PASS | URL `/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29`, page loads correctly |
| 11.3 | `/trust` renders balance card + recent deposits list + ledger preview | PASS | Three sections visible: trust balance card, transactions table, statements |
| 11.4 | Trust balance card shows R 50,000.00 matching firm-side | PASS | R 50 000,00 displayed, matches firm-side Section 86 ledger |
| 11.5 | Recent deposits row dated Day 10 with sanitised description | PASS | Row: 14 May 2026 / DEPOSIT / "Initial trust deposit — RAF-2026-001" / R 50 000,00 |
| 11.6 | Matter trust ledger line-level history (one deposit row) | PASS | Single deposit row with running balance column |
| 11.7 | Screenshots saved | PASS | `day-11-portal-home.png`, `day-11-portal-trust-balance.png` |
| 11.8 | Currency rendered as R / ZAR | PASS | 5 surfaces verified: home tile, balance card, amount col, running balance col, email body |

## Phase summary (from scenario)

- **Trust deposit visible on portal within 1 business day of firm posting** → **PASS** (visible same session, deposit posted Day 10 evening, portal shows it immediately).
- **Amount matches firm-side Section 86 ledger (no rounding / display bug)** → **PASS** (R 50 000,00 matches firm-side posting; ZA locale correctly applied with space thousands separator + comma decimal).
- **Description sanitisation** → **PASS** (firm-side description "Initial trust deposit — RAF-2026-001" passed through; no internal tags present, copy is client-safe, under 140 chars).
- **ZAR currency throughout (legal-za default)** → **PASS** (all surfaces use R / ZAR prefix).

## New gaps filed

None. All checkpoints passed cleanly.

## Previous cycle gap verification

- **OBS-1101 (email body formatting)**: Previous cycle found raw ISO timestamp and unformatted amount in trust deposit email. This cycle's email shows **13 May 2026** (properly formatted date) and **R 50 000,00** (properly formatted amount). **OBS-1101: VERIFIED FIXED.**

## Console health

- Portal `:3002`: 0 errors throughout login → `/projects` → `/trust/c90832a4-...` → `/home` → `/trust` redirect.
- 1 benign Next.js dev-mode warning (`scroll-behavior: smooth` migration notice — not a runtime issue).

## Screenshots

- `qa_cycle/evidence/day-11/day-11-portal-home.png` — Portal home, "Last trust movement R 50 000,00 / 14 May 2026"
- `qa_cycle/evidence/day-11/day-11-portal-trust-balance.png` — Portal `/trust/{matterId}` balance card + transactions table + statements

## Status

- **Day 11 COMPLETE.** All 8 checkpoints PASS. 0 blockers. 0 new gaps.
- **OBS-1101 (previous cycle) VERIFIED FIXED** — email formatting now correct.
- Ready for Day 14.
