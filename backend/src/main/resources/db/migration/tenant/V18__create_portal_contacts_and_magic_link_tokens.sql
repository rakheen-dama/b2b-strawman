-- V18: Create portal_contacts and magic_link_tokens tables, add tasks.customer_visible
-- Epic 54A -- PortalContact entity & persistent magic links

-- 1. portal_contacts table
CREATE TABLE IF NOT EXISTS portal_contacts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         VARCHAR(255) NOT NULL,
    customer_id    UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    email          VARCHAR(255) NOT NULL,
    display_name   VARCHAR(255),
    role           VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tenant_id      VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Email uniqueness scoped to customer (same email can't be added twice to same customer)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_portal_contacts_email_customer') THEN
    ALTER TABLE portal_contacts ADD CONSTRAINT uq_portal_contacts_email_customer UNIQUE (email, customer_id);
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_portal_contacts_email_org
    ON portal_contacts (email, org_id);

CREATE INDEX IF NOT EXISTS idx_portal_contacts_customer
    ON portal_contacts (customer_id);

CREATE INDEX IF NOT EXISTS idx_portal_contacts_tenant
    ON portal_contacts (tenant_id);

-- Row-Level Security
ALTER TABLE portal_contacts ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_portal_contacts') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_portal_contacts ON portal_contacts
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- 2. magic_link_tokens table (no tenant_id -- cross-tenant lookup by hash)
CREATE TABLE IF NOT EXISTS magic_link_tokens (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_contact_id  UUID NOT NULL REFERENCES portal_contacts(id) ON DELETE CASCADE,
    token_hash         VARCHAR(64) NOT NULL,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at            TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_ip         VARCHAR(45)
);

-- Token hash uniqueness
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_magic_link_tokens_hash') THEN
    ALTER TABLE magic_link_tokens ADD CONSTRAINT uq_magic_link_tokens_hash UNIQUE (token_hash);
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_magic_link_tokens_contact_created
    ON magic_link_tokens (portal_contact_id, created_at);

CREATE INDEX IF NOT EXISTS idx_magic_link_tokens_expires
    ON magic_link_tokens (expires_at);

-- 3. Add customer_visible column to tasks
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'customer_visible'
  ) THEN
    ALTER TABLE tasks ADD COLUMN customer_visible BOOLEAN NOT NULL DEFAULT false;
  END IF;
END $$;
