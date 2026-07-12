# Day 11 — Sipho sees trust balance on portal `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini on :3002 (magic-link session from Day 8, still valid).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 11.1 | PASS | Mailpit 09:09:17 (34s after firm posting): "Mathebula & Partners: Trust account activity" to sipho.portal@example.com — Date 6 Jul 2026, Type DEPOSIT, Amount R 50 000,00 |
| 11.2 | PASS | Email link "View trust ledger" → `http://localhost:3002/trust/272be4f8-…` (matter trust ledger) — renders authenticated, no re-login |
| 11.3 | PASS (behaviour note) | `/trust` auto-forwards to the single matter ledger `/trust/272be4f8-…` when only one matter has trust activity ("Back to trust" returns to the same ledger). Rendered page contains everything 11.3 expects: trust balance card at top, transactions list, ledger with running balance, Statements section ("No statement documents yet") |
| 11.4 | PASS | Trust balance card **R 50 000,00** "As of 6 Jul 2026" — matches firm-side Day 10 posting exactly |
| 11.5 | PASS | Transactions list: 6 Jul 2026 · DEPOSIT · "Initial trust deposit — RAF-2026-001" · R 50 000,00. Description is the client-safe firm-entered copy, no internal tags, < 140 chars |
| 11.6 | PASS | Matter trust ledger renders line-level history: single deposit row with Amount + Running balance columns (R 50 000,00 / R 50 000,00) |
| 11.7 | PASS | 📸 `day-11-portal-trust-balance.png` |
| 11.8 | PASS | All amounts render as **R 50 000,00** (ZAR, SA locale space-grouping + comma decimals); no $/€/£ anywhere |

## Day 11 day-level checkpoints

- Trust deposit visible on portal (email 34s, page immediate): **PASS**
- Amount matches firm-side Section 86 ledger exactly: **PASS**
- Description sanitisation (client-safe copy only): **PASS**
- ZAR currency throughout: **PASS**

## Gaps

None.
