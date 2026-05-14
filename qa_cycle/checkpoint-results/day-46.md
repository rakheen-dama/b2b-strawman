# Day 46 — Sipho responds to REQ-0003 + Trust re-check + Isolation spot-check (PORTAL)

**Branch**: `bugfix_cycle_2026-05-13`
**Cycle**: 2 (2026-05-14)
**Actor**: Sipho Dlamini (portal `:3002`)
**Stack health (pre-test)**: backend :8080 (200), portal :3002 (307 redirect to /login — expected), all healthy.

## Result: PASS — Day 46 COMPLETE. All 7 checkpoints pass.

---

## 46.1 — Magic-link re-auth: PASS

Navigated to `http://localhost:3002/login` → entered `sipho.portal@example.com` → clicked Send Magic Link → dev-mode auto-link rendered (`?token=8ZudWjywrDTtv7GZijaw7wgdGv5vGuKaDlYNYfEYTSU&orgId=mathebula-partners`) → exchange → redirected to `/projects` → navigated to `/home`. User menu shows "Sipho Dlamini". Console clean (HMR + React DevTools info only).

## 46.2 — `/home` pending info requests + REQ-0003 detail surface: PASS

`/home` shows **"Pending info requests: 1"** → clicked into it → `/requests` → 2 requests listed:
- **REQ-0003**: Dlamini v Road Accident Fund, status **SENT**, **0/2 submitted**
- **REQ-0001**: Dlamini v Road Accident Fund, COMPLETED, 3/3 accepted (from Day 4/5)

Clicked REQ-0003 → `/requests/d67b81e8-b916-4fca-a213-d570e700d14f` → detail page renders 2 required items:
1. **Hospital discharge summary** (required, File Upload) — "Complete discharge summary from treating hospital following the accident"
2. **Orthopaedic specialist report** (required, File Upload) — "Report from orthopaedic specialist on injuries sustained in the accident"

Both items show `Upload file` button + disabled `Upload and submit` button. Header: `0/2 submitted • status SENT`.

## 46.3 — Upload + submit both PDFs: PASS

Used `qa_cycle/test-fixtures/test-doc.pdf` for both items.

**Item 1 (Hospital discharge summary)**:
- Clicked `Upload file for Hospital discharge summary` → file chooser → selected `test-doc.pdf` → Upload-and-submit button enabled.
- Clicked `Upload and submit` → item transitions to `Submitted — status: SUBMITTED`.
- Envelope counter updates: `1/2 submitted • status IN_PROGRESS`.

**Item 2 (Orthopaedic specialist report)**:
- First attempt failed with 401 (session expired between items — portal JWT timeout). Redirected to `/login`.
- Re-authenticated via magic-link → navigated back to REQ-0003 detail → item 1 still shows SUBMITTED, item 2 still pending.
- Clicked `Upload file for Orthopaedic specialist report` → file chooser → selected `test-doc.pdf` → Upload-and-submit enabled.
- Clicked `Upload and submit` → item transitions to `Submitted — status: SUBMITTED`.
- Envelope counter updates: `2/2 submitted • status IN_PROGRESS`.

Note: Envelope stays `IN_PROGRESS` after 2/2 items submitted — firm-side Accept/Review transitions it to `COMPLETED`. This is by-design (confirmed in prior cycles).

## 46.4 — Trust balance R 70,000: PASS

Clicked sidebar `Trust` link → portal redirects to `/trust/c90832a4-c993-4eaa-9ea7-404a259b0e29` (single-matter contact). Trust panel renders:
- **Trust balance: R 70 000,00**
- As of 14 May 2026
- Matter c90832a4

**Scenario amendment**: Scenario expects R 71,000 (carrying Day 14 R 1,000 OBS-1101 verify deposit from prior cycle). In this clean-slate cycle 2, that R 1,000 deposit was never made (Day 14 only created the R 25,000 Moroka deposit). R 70,000 is correct for this cycle's data (R 50,000 Day 10 + R 20,000 Day 45). Same amendment already applied at Day 45.

## 46.5 — Transaction list: PASS (with amendment)

Two deposit rows visible, descending by running balance:

| Date | Type | Description | Amount | Running balance |
|------|------|-------------|--------|-----------------|
| 14 May 2026 | DEPOSIT | Top-up per engagement letter | R 20 000,00 | R 70 000,00 |
| 14 May 2026 | DEPOSIT | Initial trust deposit — RAF-2026-001 | R 50 000,00 | R 50 000,00 |

**Scenario amendment**: Scenario expects 3 rows (Day 10 R 50k + Day 14 R 1k + Day 45 R 20k). In this clean-slate cycle, only 2 deposits exist (Day 10 R 50k + Day 45 R 20k). Amounts and running-balance arithmetic correct. Dates all show 14 May 2026 (single-day E2E run).

## 46.6 — Passive isolation spot-check: PASS

Body-text regex scans across 3 portal pages:

| Page | Body length | `/moroka/i` | `/EST-2026/i` | `/R 25.?000/i` | `/liquidation/i` |
|------|-------------|-------------|---------------|----------------|-------------------|
| `/trust/c90832a4-...` | 464 chars | false | false | false | false |
| `/projects` | 448 chars | false | false | false | false |
| `/home` | 286 chars | false | false | false | false |

Zero Moroka data leakage. Tenant/customer isolation holds at portal level, 32+ scenario-days after Moroka onboarding.

## 46.7 — Pending info requests dropped: PASS

`/home` "Pending info requests" card shows **0** (was 1 before submission). Both items moved out of pending after submission.

## 46.8 — Screenshot (optional)

Skipped (accessibility snapshot evidence captured inline above).

---

## Day 46 Checkpoints (Summary)

- [x] Second info request lifecycle complete (2/2 items submitted from portal, envelope IN_PROGRESS)
- [x] Trust balance visible on portal — R 70,000.00 (amended from scenario R 71,000 for clean-slate cycle)
- [x] Transaction list correct — 2 deposits with accurate running balances
- [x] Isolation holds — zero Moroka leakage across /trust, /projects, /home
- [x] Pending info-request counter drops to 0 after submission

## Console & Network

- 1 x 401 error on first upload attempt for item 2 — caused by portal JWT session expiry between items. Resolved by re-auth. No further errors after re-auth.
- All other console entries benign: React DevTools info, HMR connected, scroll-behavior warning (pre-existing Next.js noise).

## Scenario Amendments (same as Day 45)

- 46.4: R 71,000 → **R 70,000** (no Day 14 R 1,000 OBS-1101 carry-over deposit in clean-slate cycle 2)
- 46.5: 3 transaction rows → **2 rows** (same reason — only Day 10 R 50k + Day 45 R 20k exist in this cycle)

## New Gaps

None.
