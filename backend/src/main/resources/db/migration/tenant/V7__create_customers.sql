-- V9: Create customers table
-- Epic 37A — Customer entity for client-aware system

CREATE TABLE IF NOT EXISTS customers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone        VARCHAR(50),
    id_number    VARCHAR(100),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes        TEXT,
    created_by   UUID NOT NULL REFERENCES members(id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Email uniqueness per schema
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_customers_email') THEN
    ALTER TABLE customers ADD CONSTRAINT uq_customers_email UNIQUE (email);
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_customers_status
    ON customers (status);

CREATE INDEX IF NOT EXISTS idx_customers_email
    ON customers (email);

CREATE INDEX IF NOT EXISTS idx_customers_created_by
    ON customers (created_by);
