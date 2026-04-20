-- V20__portal_retainer_read_model.sql
-- Epic 496A: Portal retainer usage read-model tables. Tenant isolation is at the row level via
-- customer_id (portal schema is shared across tenants). Mirrors V19 (portal trust ledger) shape.

CREATE TABLE IF NOT EXISTS portal.portal_retainer_summary (
    id                   UUID         PRIMARY KEY,
    customer_id          UUID         NOT NULL,
    name                 VARCHAR(300) NOT NULL,
    period_type          VARCHAR(20)  NOT NULL CHECK (period_type IN ('MONTHLY','QUARTERLY','ANNUAL')),
    hours_allotted       NUMERIC(8,2),
    hours_consumed       NUMERIC(8,2) NOT NULL DEFAULT 0,
    hours_remaining      NUMERIC(8,2),
    period_start         DATE         NOT NULL,
    period_end           DATE         NOT NULL,
    rollover_hours       NUMERIC(8,2) NOT NULL DEFAULT 0,
    next_renewal_date    DATE,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','PAUSED')),
    last_synced_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_retainer_summary_customer_status
    ON portal.portal_retainer_summary (customer_id, status);

-- customer_id on the entry enables a tenant-bounded delete during backfill drift repair,
-- mirroring the V19 portal_trust_transaction pattern.
CREATE TABLE IF NOT EXISTS portal.portal_retainer_consumption_entry (
    id                    UUID         PRIMARY KEY,
    retainer_id           UUID         NOT NULL,
    customer_id           UUID         NOT NULL,
    occurred_at           DATE         NOT NULL,
    hours                 NUMERIC(6,2) NOT NULL,
    description           VARCHAR(140),
    member_display_name   VARCHAR(80),
    last_synced_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Portal list queries scope to retainer_id and order by occurrence desc.
CREATE INDEX IF NOT EXISTS idx_portal_retainer_entry_retainer_occurred
    ON portal.portal_retainer_consumption_entry (retainer_id, occurred_at DESC);
