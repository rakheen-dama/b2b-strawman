# Day 45 — Firm: second info request + second trust deposit `[FIRM]` — cycle 2026-07-12 (executed 2026-07-13)

**Actor**: Bob Ndlovu (:3000, existing KC session).

**Balance amendment for this cycle**: the scenario's amended R 71 000 expectation included a prior-cycle R 1 000 OBS-1101 verify deposit that does NOT exist in this cycle's data. Correct expectation here: R 50 000 (Day 10) + R 20 000 (Day 45) = **R 70 000**.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 45.1 | PASS | Matter RAF-2026-001 → Client · Requests → New Request (ad-hoc, portal contact Sipho pre-selected): items "Hospital discharge summary" + "Orthopaedic report", due 2026-07-20 (Day 52), Send Now → **REQ-0003 · Sent · 0/2 accepted · 13 Jul 2026** in requests table. (Product dialog has no request-title field — request identified as REQ-0003 on the matter; scenario's "Supporting medical evidence" title is advisory. Numbering REQ-0003 because REQ-0002 is Moroka's — matches prior cycle) |
| 45.2 | PASS | Mailpit `9riaNx3n63YRmSVKw57HCC` → sipho.portal@example.com, subject "Information request REQ-0003 from Mathebula & Partners", body "2 item(s)", **View Request** = fresh magic-link (`/auth/exchange?token=…&orgId=mathebula-partners`) |
| 45.3 | PASS | Trust Accounting → Transactions → Record Transaction ▸ Record Deposit: client Sipho, matter Dlamini v RAF, **R 20 000**, ref DEP/2026/003, description "Top-up per engagement letter", date 2026-07-13 → row **DEP/2026/003 · Deposit · R 20 000,00 · RECORDED** |
| 45.4 | PASS (no dual-approval leg) | Deposits post directly to RECORDED (no approval workflow for deposits — same product shape as Days 10/14; dual-approval applies to payments). Client Ledgers: **Sipho Dlamini R 70 000,00** (deposits R 70 000,00 / payments R 0,00); Moroka unchanged R 25 000,00 |
| 45.5 | PASS | Matter Finance > Trust: balance **R 70 000,00**, Deposits R 70 000,00, last transaction 2026/07/13. 📸 `day-45-matter-trust-70000.png` |

## Day-level checkpoints

- Second info request dispatched: **PASS**
- Trust balance reconciles (client ledger + matter trust tab): **PASS at R 70 000** (cycle-correct amendment of the scripted R 71 000 — no OBS-1101 carry-over deposit exists this cycle)

## New gaps

None.

## Observations

- Trust-activity email to Sipho fired within seconds of posting (Mailpit `M7RVmLeghCjgD4hAZBfBzU`: DEPOSIT R 20 000,00, deep link `:3002/trust/66451e87…`) — faster than prior cycle's ~3 min.
- Matter trust tab renders "Last transaction: 2026/07/13" (slash format) while tables elsewhere use "13 Jul 2026" — cosmetic date-locale inconsistency, observation only.
- Console clean across all Day-45 navigations (one 404 was QA's own wrong URL guess `/trust`, not a product route).

## Handoff for Day 46

- REQ-0003 id visible at `/org/mathebula-partners/information-requests/…` (link in requests table); portal magic link token in Mailpit `9riaNx3n63YRmSVKw57HCC`.
- Sipho trust balance R 70 000 (3 transactions: DEP/2026/001 R 50 000 12 Jul, DEP/2026/003 R 20 000 13 Jul; Moroka's DEP/2026/002 R 25 000 must NOT appear on portal).
