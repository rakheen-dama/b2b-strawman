# Day 11 — Sipho sees trust balance on portal `[PORTAL]` — Cycle 2026-07-12

**Actor**: Sipho Dlamini on :3002 (magic-link session from Day 4, still valid).

| # | Result | Evidence |
|---|--------|----------|
| 11.1 | PASS | Mailpit `nmNfE3itQAHWYiu8kaxaaN` 21:21:32Z (3 min after firm posting): "Mathebula & Partners: Trust account activity" → sipho.portal@example.com; body: Date 12 Jul 2026, Type DEPOSIT, Amount R 50 000,00 |
| 11.2 | PASS | Email "View trust ledger" link → `http://localhost:3002/trust/66451e87-4723-49c4-b363-e696b68ff6b0` — renders authenticated, no re-login |
| 11.3 | PASS (same behaviour note as prior cycle) | `/trust` auto-forwards to the single matter ledger when only one matter has trust activity. Page has: trust balance card at top, Transactions table, Statements section ("No statement documents yet") |
| 11.4 | PASS | Trust balance card **R 50 000,00** "As of 12 Jul 2026" — matches firm-side Day 10 posting exactly |
| 11.5 | PASS | Transactions row: 12 Jul 2026 · DEPOSIT · "Initial trust deposit — RAF-2026-001" · R 50 000,00. Client-safe firm-entered copy, no internal tags, < 140 chars |
| 11.6 | PASS | Ledger line-level history: single deposit row with Amount + Running balance (R 50 000,00 / R 50 000,00) |
| 11.7 | PASS | 📸 `day-11-portal-trust-balance.png` |
| 11.8 | PASS | All amounts ZAR SA-locale ("R 50 000,00", space grouping + comma decimals); no $/€/£ |

## Day 11 day-level checkpoints

- Trust deposit visible on portal (email ~3 min, page immediate): PASS
- Amount matches firm-side Section 86 ledger exactly: PASS
- Description sanitisation (client-safe copy only): PASS
- ZAR currency throughout: PASS

Console: 0 errors.

## Gaps

- None new. Observation (pre-existing, present in prior cycle's accepted evidence): ledger balance card labels the matter as "Matter 66451e87" (truncated UUID) rather than the matter name — cosmetic, client-facing polish candidate.
