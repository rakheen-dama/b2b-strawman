# Reconciliation wizard: calculate when the review step opens (fix/recon-wizard-initial-calculate)

Observed on the e2e mock-auth stack (:3001), signed in as Alice (owner), with the frontend rebuilt from this branch.
Driven by a Playwright-library script (`obs.js`); the log is in `obs-summary.txt`.

| Shot | What it shows |
|---|---|
| `before-step4-uncalculated-1440.png` | Before (#1778 head): step 4 opens with every balance at R 0,00 and "not balanced" |
| `01-step4-on-entry-1440.png` | After: step 4 opens on **calculated** balances. Bank R 3 750,00, Cashbook R 290 249,98, Adjusted Bank R 290 249,98 (= cashbook); auto-match resolved 2 / 2 lines; 0 console/page errors |
| `02-step4-on-entry-1024.png` | Same at 1024×768 |

Flow: primary trust account, period end 2026-11-30 → upload `recon-statement.csv` (2 lines matching the stack's DEP/PAY
transactions) → Run Auto-Match ("Auto-matched 2 lines") → Review Matches.

**Why this run still reads "not balanced" (separate issue, not in this PR):** the Client Ledger Total (R 290 275,50)
exceeds the cashbook by exactly R 25,52, which is the stack's `INTEREST_LPFF` transaction (no client). The cashbook
counts it as a debit, while no client ledger card carries it. So no three-way reconciliation can balance after an
interest run on this data. That is a domain question about the interest posting model, flagged separately. Completing a
balanced reconciliation that auto-match fully resolved is covered by vitest
("calculates the reconciliation on entering review so it can be completed").
