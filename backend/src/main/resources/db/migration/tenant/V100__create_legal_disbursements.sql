-- ============================================================
-- V100__create_legal_disbursements.sql
-- Phase 67: Legal Depth II — LegalDisbursement entity + capability seeding
-- Architecture §67.8.1 (originally named V96; renumbered to next free slot)
-- ============================================================

CREATE TABLE IF NOT EXISTS legal_disbursements (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    customer_id           UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    category              VARCHAR(30) NOT NULL,
    description           TEXT NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    vat_treatment         VARCHAR(30) NOT NULL,
    vat_amount            DECIMAL(15,2) NOT NULL DEFAULT 0,
    payment_source        VARCHAR(20) NOT NULL,
    trust_transaction_id  UUID REFERENCES trust_transactions(id) ON DELETE RESTRICT,
    incurred_date         DATE NOT NULL,
    supplier_name         VARCHAR(200) NOT NULL,
    supplier_reference    VARCHAR(100),
    receipt_document_id   UUID REFERENCES documents(id) ON DELETE SET NULL,
    approval_status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    approval_notes        TEXT,
    billing_status        VARCHAR(20) NOT NULL DEFAULT 'UNBILLED',
    invoice_line_id       UUID,
    write_off_reason      TEXT,
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_legal_disbursements_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_legal_disbursements_category
        CHECK (category IN (
            'SHERIFF_FEES','COUNSEL_FEES','SEARCH_FEES','DEEDS_OFFICE_FEES',
            'COURT_FEES','ADVOCATE_FEES','EXPERT_WITNESS','TRAVEL','OTHER')),
    CONSTRAINT ck_legal_disbursements_vat_treatment
        CHECK (vat_treatment IN ('STANDARD_15','ZERO_RATED_PASS_THROUGH','EXEMPT')),
    CONSTRAINT ck_legal_disbursements_payment_source
        CHECK (payment_source IN ('OFFICE_ACCOUNT','TRUST_ACCOUNT')),
    CONSTRAINT ck_legal_disbursements_trust_link
        CHECK ((payment_source = 'TRUST_ACCOUNT' AND trust_transaction_id IS NOT NULL)
            OR (payment_source = 'OFFICE_ACCOUNT' AND trust_transaction_id IS NULL)),
    CONSTRAINT ck_legal_disbursements_approval_status
        CHECK (approval_status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED')),
    CONSTRAINT ck_legal_disbursements_billing_status
        CHECK (billing_status IN ('UNBILLED','BILLED','WRITTEN_OFF')),
    CONSTRAINT ck_legal_disbursements_writeoff_reason
        CHECK (billing_status <> 'WRITTEN_OFF' OR write_off_reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_legal_disbursements_project
    ON legal_disbursements (project_id);
CREATE INDEX IF NOT EXISTS idx_legal_disbursements_customer
    ON legal_disbursements (customer_id);
CREATE INDEX IF NOT EXISTS idx_legal_disbursements_billing_status
    ON legal_disbursements (billing_status)
    WHERE billing_status = 'UNBILLED';
CREATE INDEX IF NOT EXISTS idx_legal_disbursements_approval_status
    ON legal_disbursements (approval_status)
    WHERE approval_status = 'PENDING_APPROVAL';
CREATE INDEX IF NOT EXISTS idx_legal_disbursements_trust_tx
    ON legal_disbursements (trust_transaction_id)
    WHERE trust_transaction_id IS NOT NULL;

-- ============================================================
-- Capability Seeding — MANAGE_DISBURSEMENTS, APPROVE_DISBURSEMENTS,
-- WRITE_OFF_DISBURSEMENTS, CLOSE_MATTER, OVERRIDE_MATTER_CLOSURE,
-- GENERATE_STATEMENT_OF_ACCOUNT (Phase 67 §67.7)
--
-- Owner: all 6
-- Admin: all EXCEPT OVERRIDE_MATTER_CLOSURE
-- Member: MANAGE_DISBURSEMENTS + GENERATE_STATEMENT_OF_ACCOUNT only
-- ============================================================

-- Owner: MANAGE_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

-- Owner: APPROVE_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_DISBURSEMENTS'
  );

-- Owner: WRITE_OFF_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'WRITE_OFF_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'WRITE_OFF_DISBURSEMENTS'
  );

-- Owner: CLOSE_MATTER
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'CLOSE_MATTER'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'CLOSE_MATTER'
  );

-- Owner: OVERRIDE_MATTER_CLOSURE (owner-only)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'OVERRIDE_MATTER_CLOSURE'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'OVERRIDE_MATTER_CLOSURE'
  );

-- Owner: GENERATE_STATEMENT_OF_ACCOUNT
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );

-- Admin: MANAGE_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

-- Admin: APPROVE_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_DISBURSEMENTS'
  );

-- Admin: WRITE_OFF_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'WRITE_OFF_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'WRITE_OFF_DISBURSEMENTS'
  );

-- Admin: CLOSE_MATTER
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'CLOSE_MATTER'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'CLOSE_MATTER'
  );

-- Admin: GENERATE_STATEMENT_OF_ACCOUNT
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );

-- Member: MANAGE_DISBURSEMENTS
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS'
FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

-- Member: GENERATE_STATEMENT_OF_ACCOUNT
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT'
FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );
