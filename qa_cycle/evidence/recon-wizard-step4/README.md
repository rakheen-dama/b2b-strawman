# Reconciliation wizard: step 4 crash (fix/recon-wizard-auto-match-lines)

Observed on the e2e mock-auth stack (:3001), signed in as Alice (owner), using a Playwright-library script
(`obs.js`). The MCP browser profile was locked by another session.

| Shot | What it shows |
|---|---|
| `before-step4-crash-1440.png` | Before the fix (e2e stack built from the Phase 90 643C head, same wizard code as `main`): Review Matches → route error boundary; console `TypeError: Cannot read properties of undefined (reading 'length')` |
| `04-step4-review-1440.png` | Fix @ 907305c44: step 4 renders the split pane with all 4 statement lines; 0 console/page errors |
| `05-step4-review-1024.png` | Same at 1024×768 |

Flow: primary trust account, period end 2026-11-30 → upload `recon-statement.csv` (4 lines) → Run Auto-Match → Review Matches.
All 4 lines read "Unmatched" because an earlier reproduction run on this stack had already matched the two seeded
transactions to its own statement. The vitest covers post-match statuses reaching the review step.

**Not fixed here (follow-up):** step 4 opens with every balance at R 0,00 and "not balanced", because
`handleStartReview` creates the reconciliation but never calls `calculateReconciliation`. Complete stays disabled until a
match / exclude / unmatch triggers a recalculation.
