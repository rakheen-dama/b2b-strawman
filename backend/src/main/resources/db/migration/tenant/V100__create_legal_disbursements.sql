-- ============================================================
-- V100__create_legal_disbursements.sql
-- Architecture filename intent: V96__create_legal_disbursements.sql
--   (V96–V99 were consumed by interim migrations on disk; bumped to V100.)
-- Phase 67 Epic 486: Legal Disbursements (SA Legal Practice Act — pass-through costs)
-- ============================================================

-- ============================================================
-- 1. Legal Disbursements
-- ============================================================

CREATE TABLE IF NOT EXISTS legal_disbursements (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID NOT NULL REFERENCES projects(id),
    customer_id             UUID NOT NULL REFERENCES customers(id),
    incurred_date           DATE NOT NULL,
    category                VARCHAR(30) NOT NULL,
    description             TEXT NOT NULL,
    amount                  DECIMAL(15,2) NOT NULL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    vat_treatment           VARCHAR(30) NOT NULL,
    vat_amount              DECIMAL(15,2) NOT NULL,
    supplier_name           VARCHAR(200),
    receipt_document_id     UUID REFERENCES documents(id),
    payment_source          VARCHAR(20) NOT NULL,
    trust_transaction_id    UUID REFERENCES trust_transactions(id),
    approval_status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approval_notes          TEXT,
    approved_by             UUID,
    approved_at             TIMESTAMPTZ,
    billing_status          VARCHAR(20) NOT NULL DEFAULT 'UNBILLED',
    billed_invoice_line_id  UUID,
    write_off_reason        TEXT,
    written_off_at          TIMESTAMPTZ,
    created_by              UUID NOT NULL REFERENCES members(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_legal_disb_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_legal_disb_category
        CHECK (category IN (
            'SHERIFF_FEES', 'DEEDS_OFFICE_FEES', 'COURT_FEES', 'COUNSEL_FEES',
            'EXPERT_WITNESS_FEES', 'TRAVEL', 'COPYING_AND_PRINTING',
            'CORRESPONDENT_FEES', 'OTHER'
        )),
    CONSTRAINT chk_legal_disb_vat_treatment
        CHECK (vat_treatment IN ('STANDARD_15', 'ZERO_RATED_PASS_THROUGH', 'EXEMPT')),
    CONSTRAINT chk_legal_disb_payment_source
        CHECK (payment_source IN ('OFFICE_ACCOUNT', 'TRUST_ACCOUNT')),
    CONSTRAINT chk_legal_disb_trust_link
        CHECK (
            (payment_source = 'TRUST_ACCOUNT' AND trust_transaction_id IS NOT NULL)
         OR (payment_source = 'OFFICE_ACCOUNT' AND trust_transaction_id IS NULL)
        ),
    CONSTRAINT chk_legal_disb_approval_status
        CHECK (approval_status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_legal_disb_billing_status
        CHECK (billing_status IN ('UNBILLED', 'BILLED', 'WRITTEN_OFF')),
    CONSTRAINT chk_legal_disb_writeoff_reason
        CHECK (billing_status <> 'WRITTEN_OFF' OR write_off_reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_legal_disb_project
    ON legal_disbursements (project_id);

CREATE INDEX IF NOT EXISTS idx_legal_disb_customer
    ON legal_disbursements (customer_id);

CREATE INDEX IF NOT EXISTS idx_legal_disb_unbilled
    ON legal_disbursements (project_id)
    WHERE billing_status = 'UNBILLED';

CREATE INDEX IF NOT EXISTS idx_legal_disb_pending_approval
    ON legal_disbursements (project_id)
    WHERE approval_status = 'PENDING_APPROVAL';

CREATE INDEX IF NOT EXISTS idx_legal_disb_trust_txn
    ON legal_disbursements (trust_transaction_id)
    WHERE trust_transaction_id IS NOT NULL;

-- ============================================================
-- 2. Extend trust_transactions.transaction_type to accept DISBURSEMENT_PAYMENT
--    (required for trust-linked disbursement validation in DisbursementService)
-- ============================================================

ALTER TABLE trust_transactions DROP CONSTRAINT IF EXISTS chk_trust_txn_type;
ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_type
    CHECK (transaction_type IN (
        'DEPOSIT', 'PAYMENT', 'TRANSFER_IN', 'TRANSFER_OUT',
        'FEE_TRANSFER', 'REFUND', 'INTEREST_CREDIT', 'INTEREST_LPFF',
        'REVERSAL', 'DISBURSEMENT_PAYMENT'
    ));

-- ============================================================
-- 3. Capability Seeding — 6 new capabilities for disbursement + matter closure
--    Defaults per arch §67.7:
--      Owner   = all 6
--      Admin   = all except OVERRIDE_MATTER_CLOSURE
--      Member  = MANAGE_DISBURSEMENTS + GENERATE_STATEMENT_OF_ACCOUNT only
-- ============================================================

-- Owner (6 capabilities)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_DISBURSEMENTS' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'WRITE_OFF_DISBURSEMENTS' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'WRITE_OFF_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'CLOSE_MATTER' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'CLOSE_MATTER'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'OVERRIDE_MATTER_CLOSURE' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'OVERRIDE_MATTER_CLOSURE'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT' FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );

-- Admin (5 capabilities — excludes OVERRIDE_MATTER_CLOSURE)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS' FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'APPROVE_DISBURSEMENTS' FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'APPROVE_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'WRITE_OFF_DISBURSEMENTS' FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'WRITE_OFF_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'CLOSE_MATTER' FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'CLOSE_MATTER'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT' FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );

-- Member (2 capabilities only)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_DISBURSEMENTS' FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_DISBURSEMENTS'
  );

INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'GENERATE_STATEMENT_OF_ACCOUNT' FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'GENERATE_STATEMENT_OF_ACCOUNT'
  );
