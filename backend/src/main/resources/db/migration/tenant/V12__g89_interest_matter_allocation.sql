-- =====================================================================================
-- G89-F22 / G89-F12 (Batch A, ADR-366): interest credits are allocated to matters.
-- Each interest_allocations row (one per client per run, unchanged) gets child lines, one per
-- matter bucket the client held money in during the period (pro-rata by balance-days); a NULL
-- project_id line is the client-level (unmattered) bucket. Posting writes one INTEREST_CREDIT per
-- line, carrying the line's matter. No backfill (D6): a posted run without lines keeps its single
-- unmattered credit. No ON DELETE CASCADE (house rule): the service deletes lines first.
-- Idempotent.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS interest_allocation_lines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    interest_allocation_id uuid NOT NULL,
    project_id uuid,                                -- NULL = unmattered (client-level) bucket
    balance_days numeric(20,2) NOT NULL,
    weight numeric(30,10) NOT NULL,
    client_share numeric(15,2) NOT NULL,
    trust_transaction_id uuid,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT interest_allocation_lines_pkey PRIMARY KEY (id),
    CONSTRAINT chk_interest_alloc_line_share CHECK (client_share > 0),
    CONSTRAINT interest_allocation_lines_allocation_fkey FOREIGN KEY (interest_allocation_id) REFERENCES interest_allocations(id),
    CONSTRAINT interest_allocation_lines_project_fkey FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT interest_allocation_lines_txn_fkey FOREIGN KEY (trust_transaction_id) REFERENCES trust_transactions(id)
);

-- One line per (allocation, matter) and at most one unmattered line per allocation
CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_alloc_line_matter
    ON interest_allocation_lines (interest_allocation_id, project_id) WHERE project_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_alloc_line_unmattered
    ON interest_allocation_lines (interest_allocation_id) WHERE project_id IS NULL;

-- Matter sub-ledger cell reads: the G89-F12 debit guard (account, client, matter) and the
-- per-matter interest buckets
CREATE INDEX IF NOT EXISTS idx_trust_txn_project
    ON trust_transactions (project_id, customer_id, trust_account_id) WHERE project_id IS NOT NULL;
