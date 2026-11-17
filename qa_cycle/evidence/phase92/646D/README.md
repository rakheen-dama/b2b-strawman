# 646D — Read-side consumers on the trust equation + optional fee-transfer date (Phase 92 E2)

Builder evidence. OBS, E2E-LEGAL and the full backend verify are the orchestrator's.

- `red-run.txt` — the red runs on HEAD `f7246ced2` before any production change (root CLAUDE.md §4):
  - R1 (BE, `TrustPositionIntegrationTest`) — `trust-position` on balanced books holding Fund interest
    R250 and an unidentified deposit R300: `difference` 550.0 (expected 0), no `creditorBreakdown`.
  - R4 (BE, same class) — `cashbook-balance?asAt=2026-03-31` ignored `asAt` (10550.0, expected 10000.00);
    `total-balance` had no breakdown; an unknown account answered 200 / 0 (expected 404).
  - R2 (BE, `TrustBalanceTrendIntegrationTest`) — the ledger series stayed at 1000.0 in the month the Fund
    interest posted (expected 1050.00).
  - R3 (BE, `CatalogueBatch2TrustBillingTest`) — `get_trust_balance` without a client returned only
    `balanceMinor` (no creditor terms); an unknown account returned a silent zero.
  - R5 (BE, `TrustTransactionServiceTest` +3) — back-dated fee transfer over HTTP dated today
    (2026-11-17, expected 2026-04-08); a future date accepted (201, expected 400). The undated case is
    green on main by design (it pins the default).
  - F1 (FE, `trust-position-widget.test.tsx`) — no "Trust creditors" row. F2 (FE,
    `trust-transactions.test.tsx`) — no "Fee transfer date (optional)" field.
- `found.md` — F-646D-1 (no lower bound on the fee-transfer date, ruling D8 → 650A/649C), F-646D-2
  (harness dated-read follow-up, O11).

Builder gates (this worktree, Tier 2 — the full verify is NOT run by the builder):
- Backend targeted: 118 classes, 886 testcases (`<testcase>` count), 0 failures / 0 errors / 0 skipped —
  `**/dashboard/**`, `**/mcp/**`, `**/verticals/legal/trustaccounting/**`, `**/architecture/*Test` and
  the brief's 13 importer classes. `spotless:check` clean.
- Frontend: lint 0 errors (78 pre-existing warnings, none in changed files), typecheck, format:check,
  full vitest 480 files / 4 398 passed / 2 skipped, build — all green. (A first full vitest run had one
  unrelated 5 s timeout in `access-request/request-access-form.test.tsx` at load average ≈ 98; the
  re-run was fully green.)

Contract regenerations (both re-run without the flag: green):
- `backend/src/test/resources/wire/trust-contract.json` (`-Dtrust.contract.update=true`, ADR-365) —
  `records` gains `TotalBalanceResponse` and `TrustPositionResponse`; `enums` unchanged. FE twin
  `trust-wire-shapes.test.ts` gains the two literals.
- `backend/src/test/resources/mcp/tool-contract.json` (`-Dmcp.contract.update=true`, ADR-343) —
  `dto.McpTrustBalanceDto` gains `investedMinor`, `fundLedgerMinor`, `unidentifiedDepositsMinor`,
  `totalTrustCreditorsMinor`, `cashbookMinor`, `asAt` (appended); `get_trust_balance` description and
  the `customerId` parameter description corrected (§16.6.5).

Rulings applied (orchestrator, 2026-11-17): D1 one PR at 32 files (five commits by family), D2 endpoints
stay in `TrustTransactionController` / `ClientLedgerController`, D3 `…Minor` names (`balanceMinor`
unchanged), D4 no `asAt` = today, D5 4-arg `RecordFeeTransferRequest` kept + HTTP back-dated case, D6
unknown account → 404 / MCP `not_found` (falls out of `TrustBooksService.position`), D7 "Trust
creditors", D8 no lower bound (found.md), D9 trend field names kept.

OBS limit: before 647A no product path posts a non-client row, so on the stack the creditor total equals
the client ledgers; the non-zero non-client figures are proven in-test only (R1–R3).
