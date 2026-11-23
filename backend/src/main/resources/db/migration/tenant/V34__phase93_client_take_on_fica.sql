-- Phase 93 / 655A (architecture §17.2.5, §17.2.11, ADR-388, ADR-389): imported-client provenance,
-- FICA attestations, review intervals.
-- Tenant V34 (guide V32/V33; V32 = 653A take-on core, V33 = 657A trust take-on; next free at build
-- time, P-1). Nullable columns / defaults only; no data change. No CASCADE.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS taken_on_at timestamptz;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS fica_review_months_low integer NOT NULL DEFAULT 36;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS fica_review_months_medium integer NOT NULL DEFAULT 24;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS fica_review_months_high integer NOT NULL DEFAULT 12;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'customers_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE customers ADD CONSTRAINT customers_take_on_batch_fkey FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_customer_taken_on' AND n.nspname = current_schema()) THEN
    ALTER TABLE customers ADD CONSTRAINT chk_customer_taken_on CHECK ((take_on_batch_id IS NULL) = (taken_on_at IS NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_org_settings_fica_review_months' AND n.nspname = current_schema()) THEN
    ALTER TABLE org_settings ADD CONSTRAINT chk_org_settings_fica_review_months CHECK (
      fica_review_months_low BETWEEN 1 AND 120 AND fica_review_months_medium BETWEEN 1 AND 120
      AND fica_review_months_high BETWEEN 1 AND 120);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS client_fica_attestations (
    id uuid NOT NULL,
    customer_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    take_on_batch_id uuid NOT NULL,
    take_on_row_id uuid NOT NULL,
    sign_off_id uuid NOT NULL,
    cdd_completed_date date NOT NULL,
    last_review_date date,
    risk_rating varchar(10) NOT NULL,
    fica_client_type varchar(30) NOT NULL,
    beneficial_ownership_status varchar(20) NOT NULL,
    screening_status varchar(20) NOT NULL,
    screening_date date,
    source_of_funds_status varchar(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    cdd_records_location varchar(500) NOT NULL,
    source_system_ref varchar(200),
    next_review_date date,
    attested_by uuid NOT NULL,
    attested_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    superseded_at timestamptz,
    CONSTRAINT client_fica_attestations_pkey PRIMARY KEY (id),
    CONSTRAINT chk_fica_att_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_fica_att_superseded CHECK ((status = 'SUPERSEDED') = (superseded_at IS NOT NULL)),
    CONSTRAINT chk_fica_att_risk CHECK (risk_rating IN ('LOW', 'MEDIUM', 'HIGH', 'UNRATED')),
    CONSTRAINT chk_fica_att_client_type CHECK (fica_client_type IN ('NATURAL_PERSON', 'COMPANY', 'CLOSE_CORPORATION', 'TRUST', 'PARTNERSHIP', 'OTHER')),
    CONSTRAINT chk_fica_att_bo CHECK (beneficial_ownership_status IN ('DONE', 'NOT_APPLICABLE', 'OUTSTANDING')),
    CONSTRAINT chk_fica_att_screening CHECK (screening_status IN ('SCREENED_CLEAR', 'SCREENED_MATCH', 'NOT_SCREENED')),
    CONSTRAINT chk_fica_att_screening_date CHECK (screening_status = 'NOT_SCREENED' OR screening_date IS NOT NULL),
    CONSTRAINT chk_fica_att_sof CHECK (source_of_funds_status IN ('DONE', 'NOT_REQUIRED', 'OUTSTANDING')),
    CONSTRAINT chk_fica_att_review CHECK ((risk_rating = 'UNRATED') = (next_review_date IS NULL)),
    CONSTRAINT client_fica_attestations_customer_fkey FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT client_fica_attestations_batch_fkey FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id),
    CONSTRAINT client_fica_attestations_row_fkey FOREIGN KEY (take_on_row_id) REFERENCES take_on_rows(id),
    CONSTRAINT client_fica_attestations_sign_off_fkey FOREIGN KEY (sign_off_id) REFERENCES take_on_sign_offs(id),
    CONSTRAINT client_fica_attestations_member_fkey FOREIGN KEY (attested_by) REFERENCES members(id)
);
-- One active attestation per client.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fica_att_active_customer ON client_fica_attestations (customer_id) WHERE status = 'ACTIVE';
-- The review queue's ordering (UNRATED first, then due date).
CREATE INDEX IF NOT EXISTS idx_fica_att_review ON client_fica_attestations (next_review_date NULLS FIRST) WHERE status = 'ACTIVE';
