-- ============================================================
-- V85__create_trust_accounting_tables.sql
-- Phase 60: Trust Accounting (Legal Practice Act Section 86)
-- ============================================================

-- ============================================================
-- 1. Trust Accounts
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_accounts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_name                VARCHAR(200) NOT NULL,
    bank_name                   VARCHAR(200) NOT NULL,
    branch_code                 VARCHAR(20) NOT NULL,
    account_number              VARCHAR(30) NOT NULL,
    account_type                VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    is_primary                  BOOLEAN NOT NULL DEFAULT true,
    require_dual_approval       BOOLEAN NOT NULL DEFAULT false,
    payment_approval_threshold  DECIMAL(15,2),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    opened_date                 DATE NOT NULL,
    closed_date                 DATE,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trust_account_type
        CHECK (account_type IN ('GENERAL', 'INVESTMENT')),
    CONSTRAINT chk_trust_account_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_trust_account_threshold
        CHECK (payment_approval_threshold IS NULL OR payment_approval_threshold > 0),
    CONSTRAINT chk_trust_account_closed_date
        CHECK (closed_date IS NULL OR closed_date >= opened_date)
);

-- Only one primary general trust account per tenant
CREATE UNIQUE INDEX IF NOT EXISTS idx_trust_accounts_primary_general
    ON trust_accounts (account_type)
    WHERE is_primary = true AND account_type = 'GENERAL';

CREATE INDEX IF NOT EXISTS idx_trust_accounts_status
    ON trust_accounts (status);

-- ============================================================
-- 2. LPFF Rates
-- ============================================================

CREATE TABLE IF NOT EXISTS lpff_rates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id    UUID NOT NULL REFERENCES trust_accounts(id),
    effective_from      DATE NOT NULL,
    rate_percent        DECIMAL(5,4) NOT NULL,
    lpff_share_percent  DECIMAL(5,4) NOT NULL,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_lpff_rate_account_date
        UNIQUE (trust_account_id, effective_from),
    CONSTRAINT chk_lpff_rate_percent
        CHECK (rate_percent >= 0 AND rate_percent <= 1),
    CONSTRAINT chk_lpff_share_percent
        CHECK (lpff_share_percent >= 0 AND lpff_share_percent <= 1)
);

CREATE INDEX IF NOT EXISTS idx_lpff_rates_account_date
    ON lpff_rates (trust_account_id, effective_from DESC);

-- ============================================================
-- 3. Trust Transactions
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_transactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id            UUID NOT NULL REFERENCES trust_accounts(id),
    transaction_type            VARCHAR(20) NOT NULL,
    amount                      DECIMAL(15,2) NOT NULL,
    customer_id                 UUID REFERENCES customers(id),  -- NULL only for INTEREST_LPFF (firm-level outflow, not client-specific)
    project_id                  UUID REFERENCES projects(id),
    counterparty_customer_id    UUID REFERENCES customers(id),
    invoice_id                  UUID REFERENCES invoices(id),
    reference                   VARCHAR(200) NOT NULL,
    description                 TEXT,
    transaction_date            DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    approved_by                 UUID,
    approved_at                 TIMESTAMPTZ,
    second_approved_by          UUID,
    second_approved_at          TIMESTAMPTZ,
    rejected_by                 UUID,
    rejected_at                 TIMESTAMPTZ,
    rejection_reason            VARCHAR(500),
    reversal_of                 UUID REFERENCES trust_transactions(id),
    reversed_by_id              UUID REFERENCES trust_transactions(id),
    bank_statement_line_id      UUID,
    recorded_by                 UUID NOT NULL REFERENCES members(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trust_txn_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_trust_txn_type
        CHECK (transaction_type IN (
            'DEPOSIT', 'PAYMENT', 'TRANSFER_IN', 'TRANSFER_OUT',
            'FEE_TRANSFER', 'REFUND', 'INTEREST_CREDIT', 'INTEREST_LPFF', 'REVERSAL'
        )),
    CONSTRAINT chk_trust_txn_status
        CHECK (status IN ('RECORDED', 'AWAITING_APPROVAL', 'APPROVED', 'REJECTED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_trust_txn_account_date
    ON trust_transactions (trust_account_id, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_trust_txn_customer
    ON trust_transactions (customer_id, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_trust_txn_status
    ON trust_transactions (status)
    WHERE status = 'AWAITING_APPROVAL';

CREATE INDEX IF NOT EXISTS idx_trust_txn_invoice
    ON trust_transactions (invoice_id)
    WHERE invoice_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trust_txn_reconciliation
    ON trust_transactions (bank_statement_line_id)
    WHERE bank_statement_line_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trust_txn_reference
    ON trust_transactions (reference);

-- ============================================================
-- 4. Client Ledger Cards
-- ============================================================

CREATE TABLE IF NOT EXISTS client_ledger_cards (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id        UUID NOT NULL REFERENCES trust_accounts(id),
    customer_id             UUID NOT NULL REFERENCES customers(id),
    balance                 DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_deposits          DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_payments          DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_fee_transfers     DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_interest_credited DECIMAL(15,2) NOT NULL DEFAULT 0,
    last_transaction_date   DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_client_ledger_account_customer
        UNIQUE (trust_account_id, customer_id),
    CONSTRAINT chk_client_ledger_balance_non_negative
        CHECK (balance >= 0),
    CONSTRAINT chk_client_ledger_deposits_non_negative
        CHECK (total_deposits >= 0),
    CONSTRAINT chk_client_ledger_payments_non_negative
        CHECK (total_payments >= 0),
    CONSTRAINT chk_client_ledger_fee_transfers_non_negative
        CHECK (total_fee_transfers >= 0),
    CONSTRAINT chk_client_ledger_interest_non_negative
        CHECK (total_interest_credited >= 0)
);

CREATE INDEX IF NOT EXISTS idx_client_ledger_balance
    ON client_ledger_cards (trust_account_id, balance DESC)
    WHERE balance > 0;

-- ============================================================
-- 5. Bank Statements
-- ============================================================

CREATE TABLE IF NOT EXISTS bank_statements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id    UUID NOT NULL REFERENCES trust_accounts(id),
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    opening_balance     DECIMAL(15,2) NOT NULL,
    closing_balance     DECIMAL(15,2) NOT NULL,
    file_key            VARCHAR(500) NOT NULL,
    file_name           VARCHAR(200) NOT NULL,
    format              VARCHAR(20) NOT NULL,
    line_count          INTEGER NOT NULL,
    matched_count       INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'IMPORTED',
    imported_by         UUID NOT NULL REFERENCES members(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bank_stmt_period
        CHECK (period_end >= period_start),
    CONSTRAINT chk_bank_stmt_format
        CHECK (format IN ('CSV', 'OFX')),
    CONSTRAINT chk_bank_stmt_status
        CHECK (status IN ('IMPORTED', 'MATCHING_IN_PROGRESS', 'MATCHED', 'RECONCILED')),
    CONSTRAINT chk_bank_stmt_counts
        CHECK (line_count >= 0 AND matched_count >= 0 AND matched_count <= line_count)
);

CREATE INDEX IF NOT EXISTS idx_bank_stmt_account_period
    ON bank_statements (trust_account_id, period_end DESC);

-- ============================================================
-- 6. Bank Statement Lines
-- ============================================================

CREATE TABLE IF NOT EXISTS bank_statement_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_statement_id       UUID NOT NULL REFERENCES bank_statements(id),
    line_number             INTEGER NOT NULL,
    transaction_date        DATE NOT NULL,
    description             VARCHAR(500) NOT NULL,
    reference               VARCHAR(200),
    amount                  DECIMAL(15,2) NOT NULL,
    running_balance         DECIMAL(15,2),
    match_status            VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    trust_transaction_id    UUID REFERENCES trust_transactions(id),
    match_confidence        DECIMAL(3,2),
    excluded_reason         VARCHAR(200),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_bank_line_match_status
        CHECK (match_status IN ('UNMATCHED', 'AUTO_MATCHED', 'MANUALLY_MATCHED', 'EXCLUDED')),
    CONSTRAINT chk_bank_line_confidence
        CHECK (match_confidence IS NULL OR (match_confidence >= 0 AND match_confidence <= 1))
);

CREATE INDEX IF NOT EXISTS idx_bank_line_statement
    ON bank_statement_lines (bank_statement_id, line_number);

CREATE INDEX IF NOT EXISTS idx_bank_line_unmatched
    ON bank_statement_lines (match_status)
    WHERE match_status = 'UNMATCHED';

-- Add FK from trust_transactions back to bank_statement_lines
-- (deferred because bank_statement_lines didn't exist when trust_transactions was created)
-- Wrapped in DO block for idempotency (ALTER TABLE ADD CONSTRAINT has no IF NOT EXISTS)
DO $$ BEGIN
    ALTER TABLE trust_transactions
        ADD CONSTRAINT fk_trust_txn_bank_line
        FOREIGN KEY (bank_statement_line_id) REFERENCES bank_statement_lines(id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================
-- 7. Trust Reconciliations
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_reconciliations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id        UUID NOT NULL REFERENCES trust_accounts(id),
    period_end              DATE NOT NULL,
    bank_statement_id       UUID REFERENCES bank_statements(id),
    bank_balance            DECIMAL(15,2) NOT NULL,
    cashbook_balance        DECIMAL(15,2) NOT NULL,
    client_ledger_total     DECIMAL(15,2) NOT NULL,
    outstanding_deposits    DECIMAL(15,2) NOT NULL DEFAULT 0,
    outstanding_payments    DECIMAL(15,2) NOT NULL DEFAULT 0,
    adjusted_bank_balance   DECIMAL(15,2) NOT NULL,
    is_balanced             BOOLEAN NOT NULL DEFAULT false,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    completed_by            UUID,
    completed_at            TIMESTAMPTZ,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trust_recon_status
        CHECK (status IN ('DRAFT', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_trust_recon_account_period
    ON trust_reconciliations (trust_account_id, period_end DESC);

-- ============================================================
-- 8. Interest Runs
-- ============================================================

CREATE TABLE IF NOT EXISTS interest_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id    UUID NOT NULL REFERENCES trust_accounts(id),
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    lpff_rate_id        UUID NOT NULL REFERENCES lpff_rates(id),
    total_interest      DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_lpff_share    DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_client_share  DECIMAL(15,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by         UUID,
    posted_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_interest_run_period
        CHECK (period_end >= period_start),
    CONSTRAINT chk_interest_run_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'POSTED')),
    CONSTRAINT chk_interest_run_total
        CHECK (total_interest >= 0),
    CONSTRAINT chk_interest_run_lpff
        CHECK (total_lpff_share >= 0),
    CONSTRAINT chk_interest_run_client
        CHECK (total_client_share >= 0)
);

-- Prevent overlapping interest runs for the same trust account
CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_run_no_overlap
    ON interest_runs (trust_account_id, period_start, period_end)
    WHERE status IN ('DRAFT', 'APPROVED', 'POSTED');

CREATE INDEX IF NOT EXISTS idx_interest_run_account_period
    ON interest_runs (trust_account_id, period_end DESC);

-- ============================================================
-- 9. Interest Allocations
-- ============================================================

CREATE TABLE IF NOT EXISTS interest_allocations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interest_run_id         UUID NOT NULL REFERENCES interest_runs(id),
    customer_id             UUID NOT NULL REFERENCES customers(id),
    average_daily_balance   DECIMAL(15,2) NOT NULL,
    days_in_period          INTEGER NOT NULL,
    gross_interest          DECIMAL(15,2) NOT NULL,
    lpff_share              DECIMAL(15,2) NOT NULL,
    client_share            DECIMAL(15,2) NOT NULL,
    trust_transaction_id    UUID REFERENCES trust_transactions(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_interest_alloc_run_customer
        UNIQUE (interest_run_id, customer_id),
    CONSTRAINT chk_interest_alloc_days
        CHECK (days_in_period > 0),
    CONSTRAINT chk_interest_alloc_gross
        CHECK (gross_interest >= 0),
    CONSTRAINT chk_interest_alloc_lpff
        CHECK (lpff_share >= 0),
    CONSTRAINT chk_interest_alloc_client
        CHECK (client_share >= 0)
);

CREATE INDEX IF NOT EXISTS idx_interest_alloc_run
    ON interest_allocations (interest_run_id);

-- ============================================================
-- 10. Trust Investments
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_investments (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_account_id            UUID NOT NULL REFERENCES trust_accounts(id),
    customer_id                 UUID NOT NULL REFERENCES customers(id),
    institution                 VARCHAR(200) NOT NULL,
    account_number              VARCHAR(50) NOT NULL,
    principal                   DECIMAL(15,2) NOT NULL,
    interest_rate               DECIMAL(5,4) NOT NULL,
    deposit_date                DATE NOT NULL,
    maturity_date               DATE,
    interest_earned             DECIMAL(15,2) NOT NULL DEFAULT 0,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    withdrawal_date             DATE,
    withdrawal_amount           DECIMAL(15,2),
    deposit_transaction_id      UUID NOT NULL REFERENCES trust_transactions(id),
    withdrawal_transaction_id   UUID REFERENCES trust_transactions(id),
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_trust_invest_principal
        CHECK (principal > 0),
    CONSTRAINT chk_trust_invest_rate
        CHECK (interest_rate >= 0 AND interest_rate <= 1),
    CONSTRAINT chk_trust_invest_earned
        CHECK (interest_earned >= 0),
    CONSTRAINT chk_trust_invest_status
        CHECK (status IN ('ACTIVE', 'MATURED', 'WITHDRAWN')),
    CONSTRAINT chk_trust_invest_maturity
        CHECK (maturity_date IS NULL OR maturity_date >= deposit_date)
);

CREATE INDEX IF NOT EXISTS idx_trust_invest_account_status
    ON trust_investments (trust_account_id, status);

CREATE INDEX IF NOT EXISTS idx_trust_invest_customer
    ON trust_investments (customer_id);

CREATE INDEX IF NOT EXISTS idx_trust_invest_maturity
    ON trust_investments (maturity_date)
    WHERE status = 'ACTIVE' AND maturity_date IS NOT NULL;

-- ============================================================
-- 11. Capability Seeding — VIEW_TRUST, MANAGE_TRUST, APPROVE_TRUST_PAYMENT
-- ============================================================

-- Owner: VIEW_TRUST
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_TRUST'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_TRUST'
  );

-- Owner: MANAGE_TRUST
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_TRUST'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_TRUST'
  );

-- Owner: APPROVE_TRUST_PAYMENT
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_TRUST_PAYMENT'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_TRUST_PAYMENT'
  );

-- Admin: VIEW_TRUST
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_TRUST'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_TRUST'
  );

-- Admin: MANAGE_TRUST
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_TRUST'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_TRUST'
  );

-- Member: VIEW_TRUST only
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_TRUST'
FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_TRUST'
  );
