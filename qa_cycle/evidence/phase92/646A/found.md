# 646A — out-of-scope observations (not fixed in #1788)

Observed by the orchestrator-run OBS on the lifecycle stack built from 3285515df (2026-11-16).

1. **Client-ledger detail Type filter still offers "Interest Credit" and "Interest LPFF" pills.** V16 removed both types from `chk_trust_txn_type`, so these filters can never match. Frontend type unions / filter lists are 646E's scope ("filter options from the endpoint", brief §1 OUT-of-scope table). Screenshot: `obs-screens/03-client-ledger-*.png`.
2. **`chk_trust_txn_customer_required` still names `INTEREST_LPFF`.** Harmless (no such rows can exist after V16). The constraint is dropped and replaced by `chk_trust_txn_ledger_target` in 646B (task 646.11).
3. **CodeRabbit (declined):** stale `INTEREST_*` rows could persist in the portal read model on a schema that had posted interest runs. Declined per founder decision 5 / R12 (no real tenant posted a run; no remediation tooling; environments are reset). Portal re-projection is 648D.
4. **EXPLAIN (R-8):** on a single-account 50 000-row probe the planner prefers `idx_trust_txn_account_date` over `idx_trust_txn_ledger_sum` (9–21 ms either way). Re-check on a multi-account, vacuumed dataset if list pages slow down (ADR-378 snapshot escape hatch).
