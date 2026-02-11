-- V12: Extend documents table with scope, customer_id, and visibility columns
-- Epic 41A — Document scope extension for org-scoped and customer-scoped documents

-- Add scope column (PROJECT, ORG, CUSTOMER) — existing documents default to PROJECT
ALTER TABLE documents ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'PROJECT';

-- Add customer_id column — nullable FK to customers table (for CUSTOMER-scoped docs)
ALTER TABLE documents ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES customers(id) ON DELETE SET NULL;

-- Add visibility column (INTERNAL, SHARED) — existing documents default to INTERNAL
ALTER TABLE documents ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- Make project_id nullable (ORG-scoped docs have no project)
ALTER TABLE documents ALTER COLUMN project_id DROP NOT NULL;

-- CHECK constraint for valid FK combinations per scope:
-- PROJECT scope: project_id NOT NULL, customer_id NULL
-- ORG scope: project_id NULL, customer_id NULL
-- CUSTOMER scope: customer_id NOT NULL (project_id optional — can be linked to a project)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_documents_scope_fks') THEN
    ALTER TABLE documents ADD CONSTRAINT chk_documents_scope_fks CHECK (
      (scope = 'PROJECT' AND project_id IS NOT NULL) OR
      (scope = 'ORG' AND project_id IS NULL AND customer_id IS NULL) OR
      (scope = 'CUSTOMER' AND customer_id IS NOT NULL)
    );
  END IF;
END $$;

-- Indexes for scope-aware queries
CREATE INDEX IF NOT EXISTS idx_documents_scope
    ON documents (scope);

CREATE INDEX IF NOT EXISTS idx_documents_customer_id
    ON documents (customer_id);

CREATE INDEX IF NOT EXISTS idx_documents_scope_visibility
    ON documents (scope, visibility);
