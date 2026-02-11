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
    tenant_id    VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Email uniqueness scoped to tenant (allows same email across orgs in shared schema)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_customers_email_tenant') THEN
    ALTER TABLE customers ADD CONSTRAINT uq_customers_email_tenant UNIQUE (email, tenant_id);
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_customers_tenant_id
    ON customers (tenant_id);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_id_status
    ON customers (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_id_email
    ON customers (tenant_id, email);

CREATE INDEX IF NOT EXISTS idx_customers_created_by
    ON customers (created_by);

-- Row-Level Security
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_customers') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_customers ON customers
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
