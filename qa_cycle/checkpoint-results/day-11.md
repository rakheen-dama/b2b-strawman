# Day 11 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Sipho Dlamini (portal client)

---

## Pre-check: Authenticate as Sipho on Portal

Navigated to `http://localhost:3002/login`. Entered `sipho.portal@example.com`, clicked "Send Magic Link". Dev-mode link appeared. Clicked dev-mode link -> authenticated as Sipho Dlamini. Landed on `/projects` (Matters page). Sidebar shows: Home, Matters, Trust, Deadlines, Fee Notes, Engagement Letters, Requests, Activity, Profile, Logout. Header shows "Sipho Dlamini". Legal terminology consistent throughout.

---

## Day 11 — Sipho sees trust balance on portal `[PORTAL]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 11.1 | Mailpit -> verify trust-deposit nudge email arrived for `sipho.portal@example.com` | **PASS** | Email ID: `2vAeXukAmtRwnXVCDjppDg`. Subject: "Mathebula & Partners: Trust account activity". From: noreply@kazi.app. Body: "A new transaction has been recorded in your trust account." Table: Date=30 May 2026, Type=DEPOSIT, Amount=R 50 000,00. Contains "View trust ledger" link to `http://localhost:3002/trust/d80aeac5-d5f4-4690-9291-193f05e3785d`. |
| 11.2 | Click the "View trust balance" link -> lands on `/trust` | **PASS** | Email link targets `/trust/d80aeac5-d5f4-4690-9291-193f05e3785d` (matter-specific trust ledger). Page renders correctly with trust balance card and transaction table. Sidebar `/trust` nav item also routes to this page (auto-redirects since Sipho has only one matter). |
| 11.3 | Verify `/trust` renders: trust balance card at top, recent deposits list, ledger preview | **PASS** | Page structure: (1) **Trust balance card** at top with shield icon and "Trust balance" label, (2) **Transactions table** with Date/Type/Description/Amount/Running balance columns, (3) **Statements section** ("No statement documents yet"). All three sections render correctly. |
| 11.4 | Trust balance card shows **R 50,000.00** (matches firm-side Day 10 posting) | **PASS** | Trust balance card displays **R 50 000,00** in large bold text. Sub-text: "As of 30 May 2026", "Matter d80aeac5". Matches firm-side trust account balance (R 50 000,00), client ledger (R 50 000,00), and matter trust tab (R 50 000,00) from Day 10 verification. |
| 11.5 | Recent deposits list shows the R50,000 deposit dated Day 10 with source description | **PASS** | Transactions table: single row: Date=30 May 2026, Type=DEPOSIT, Description="Initial trust deposit -- RAF-2026-001", Amount=R 50 000,00, Running balance=R 50 000,00. Description is client-safe copy (no `[internal]` tags, 37 chars, well under 140 char limit). |
| 11.6 | Click into the matter trust ledger -> line-level history renders | **PASS** | Already on the matter-specific trust ledger at `/trust/d80aeac5-...`. Transaction table shows full line-level history with running balance column. Single deposit transaction renders correctly. |
| 11.7 | Screenshot: `day-11-portal-trust-balance.png` | **PASS** | Screenshot captured: `day-11-portal-trust-balance.png` showing trust balance card (R 50 000,00), transactions table with deposit row, sidebar with Trust active, user "Sipho Dlamini" in header. |
| 11.8 | Verify currency rendered as **R** / **ZAR** (not $ / EUR / GBP) | **PASS** | All monetary values on the page use **R** prefix: trust balance card (R 50 000,00), transaction amount (R 50 000,00), running balance (R 50 000,00). ZAR formatting with comma decimal separator and space thousands separator. No $ / EUR / GBP visible anywhere. |

---

## Day 11 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Trust deposit visible on portal within 1 business day of firm posting | **PASS** | Deposit recorded on Day 10 (firm side) is immediately visible on portal Day 11 (same session). Trust email notification sent at time of posting. Zero delay. |
| Amount matches firm-side Section 86 ledger (no rounding / display bug) | **PASS** | Portal trust balance: R 50 000,00. Firm trust account: R 50 000,00. Client ledger: R 50 000,00. Matter trust tab: R 50 000,00. All four surfaces reconcile exactly. Zero rounding discrepancy. |
| Description sanitisation — any firm-internal `[internal]` tags stripped, copy <= 140 chars, safe fallback if no client-safe copy | **PASS** | Description on portal: "Initial trust deposit -- RAF-2026-001" (37 chars). No `[internal]` tags present. Client-safe, descriptive copy. |
| ZAR currency throughout (legal-za default) | **PASS** | All amounts rendered with **R** prefix and ZA locale formatting (comma decimal, space thousands). Consistent across trust balance card, transaction table amount column, and running balance column. |

---

## Console Errors

**Zero JavaScript errors** during Day 11 portal execution. One non-error warning in console (favicon-related, cosmetic).

## Gaps Filed

None. Day 11 passed cleanly with zero gaps.

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf` (unchanged)
- **Matter ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d` (unchanged)
- **Matter Reference**: RAF-2026-001 (unchanged)
- **Proposal ID**: `40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86` (unchanged)
- **Trust Account**: "Mathebula Trust -- Main", SECTION_86, Balance R 50 000,00
- **Trust Transaction**: DEP/2026/001, DEPOSIT, R 50 000,00, RECORDED, 2026-05-30
- **Portal Trust Ledger URL**: `/trust/d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Trust Notification Email ID**: `2vAeXukAmtRwnXVCDjppDg`
