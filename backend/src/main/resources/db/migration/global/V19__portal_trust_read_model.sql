-- V19__portal_trust_read_model.sql
-- Epic 495A: Portal trust ledger read-model tables. Tenant isolation is at the row level via
-- customer_id (which uniquely identifies the firm customer in the global portal schema).

CREATE TABLE IF NOT EXISTS portal.portal_trust_balance (
    customer_id           UUID        NOT NULL,
    matter_id             UUID        NOT NULL,
    current_balance       DECIMAL(15,2) NOT NULL DEFAULT 0,
    last_transaction_at   TIMESTAMPTZ,
    last_synced_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, matter_id)
);

-- transaction_type intentionally has no CHECK constraint — the firm-side TrustTransaction enum
-- is the single source of truth for allowed values. Adding a new type firm-side (e.g., BANK_FEE)
-- would otherwise fail portal inserts with an opaque constraint violation.
CREATE TABLE IF NOT EXISTS portal.portal_trust_transaction (
    id                    UUID        PRIMARY KEY,
    customer_id           UUID        NOT NULL,
    matter_id             UUID        NOT NULL,
    transaction_type      VARCHAR(20) NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    running_balance       DECIMAL(15,2) NOT NULL,
    occurred_at           TIMESTAMPTZ NOT NULL,
    description           VARCHAR(140),
    reference             VARCHAR(100),
    last_synced_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Portal list queries scope to (customer_id, matter_id) and order by occurrence desc.
CREATE INDEX IF NOT EXISTS idx_portal_trust_txn_customer_matter_occurred
    ON portal.portal_trust_transaction (customer_id, matter_id, occurred_at DESC);
