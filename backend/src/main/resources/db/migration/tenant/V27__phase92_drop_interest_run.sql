-- Phase 92 / 651A (ADR-383; architecture §16.7.10 without lpff_rates, which 651E drops): the
-- calculated interest run is removed outright. Its three tables are DROPPED, not kept read-only:
-- no real tenant ever posted a run (founder decision 5), the figures they hold are calculations
-- Kazi now says were wrong in law, and V16 already removed the postings they pointed at and
-- cleared their trust_transaction_id links. Any rows on a walked dev schema go with them, by
-- design (ADR-383); dev and e2e stacks are reset.
--
-- Children first, no CASCADE. Nothing outside these three tables references them: the only
-- foreign keys into them are interest_allocation_lines -> interest_allocations (V12) and
-- interest_allocations -> interest_runs (V1). Dropping a table drops its own indexes, constraints
-- and outbound foreign keys (to lpff_rates, trust_accounts, customers, projects,
-- trust_transactions).
--
-- KEPT: idx_trust_txn_project — created in V12 beside interest_allocation_lines, but it is an
-- index on trust_transactions and the matter-cell debit guard uses it. lpff_rates stays until
-- 651E. Audit history keeps its interest_run events (audit rows are never rewritten).
--
-- Idempotent: DROP TABLE IF EXISTS (no catalog lookup, so no current_schema() guard is needed).
DROP TABLE IF EXISTS interest_allocation_lines;
DROP TABLE IF EXISTS interest_allocations;
DROP TABLE IF EXISTS interest_runs;
