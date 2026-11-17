# 646E — Registry as data (type endpoint, typeLabel/direction on the DTO, FE label map deleted)

Builder evidence. OBS, E2E-LEGAL and the full backend verify are the orchestrator's.

- `red-run.txt` — the red runs on HEAD `6db212b63` before any production change (root CLAUDE.md §4):
  F7 (BE, `TrustReportQueryIntegrationTest` — report `type` column printed `DEPOSIT`), F8 (FE — a
  payment's/refund's REVERSAL drawn as an outflow), R1 (FE — Reverse offered on a deposit whose
  contra is pending), labels (FE — local title-case map instead of the DTO), 646A found-1 (FE —
  "Interest Credit"/"Interest LPFF" pills on the client ledger).
  R1 was also browser-reproduced in the 649B gates README (item 3).
- `found.md` — out-of-scope defects: ReconciliationSplitPane raw enum (D7 → 646C), and the
  Transactions page's ignored `dateFrom`/`dateTo`/`clientId` filters.

Builder gates (this worktree): Tier-2 targeted backend tests — 114 classes, 1 045 testcases, 0
failures / 0 errors / 0 skipped; FE gate — lint 0 errors (78 pre-existing warnings), typecheck,
format:check, vitest 4 383 passed / 2 skipped (480 files), build — all green.
