-- Phase 93 / 653A (architecture §17.2.1–17.2.4, §17.7.2): take-on batches, sources, rows, sign-offs. Tenant V32 (guide V30/V31; next free at build time).
-- New tables only; no existing row is touched. No CASCADE.
CREATE TABLE IF NOT EXISTS take_on_batches (
    id uuid NOT NULL,
    dataset varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    name varchar(200),
    cutover_date date,
    trust_account_id uuid,
    bank_balance numeric(15,2),
    outstanding_deposits_total numeric(15,2),
    outstanding_payments_total numeric(15,2),
    cashbook_balance numeric(15,2),
    fund_ledger_balance numeric(15,2) NOT NULL DEFAULT 0,
    invested_total numeric(15,2) NOT NULL DEFAULT 0,
    mid_month_acknowledged boolean NOT NULL DEFAULT false,
    validation_hash varchar(64),
    validated_at timestamptz,
    total_rows integer NOT NULL DEFAULT 0,
    valid_rows integer NOT NULL DEFAULT 0,
    error_rows integer NOT NULL DEFAULT 0,
    duplicate_rows integer NOT NULL DEFAULT 0,
    block_count integer NOT NULL DEFAULT 0,
    created_rows integer NOT NULL DEFAULT 0,
    skipped_rows integer NOT NULL DEFAULT 0,
    failed_rows integer NOT NULL DEFAULT 0,
    summary jsonb,
    last_error varchar(1000),
    created_by uuid NOT NULL,
    commit_requested_by uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    commit_started_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    cancelled_by uuid,
    CONSTRAINT take_on_batches_pkey PRIMARY KEY (id),
    CONSTRAINT chk_take_on_batch_dataset CHECK (dataset IN ('CLIENTS', 'MATTERS', 'TRUST_POSITION', 'DEBTOR_BALANCES')),
    CONSTRAINT chk_take_on_batch_status CHECK (status IN ('UPLOADED', 'MAPPED', 'VALIDATED', 'COMMITTING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_take_on_batch_trust_account CHECK ((dataset = 'TRUST_POSITION') = (trust_account_id IS NOT NULL)),
    CONSTRAINT chk_take_on_batch_cutover CHECK (dataset NOT IN ('TRUST_POSITION', 'DEBTOR_BALANCES') OR cutover_date IS NOT NULL
                                                 OR status IN ('UPLOADED', 'MAPPED', 'CANCELLED')),
    CONSTRAINT chk_take_on_batch_outstanding CHECK (coalesce(outstanding_deposits_total, 0) >= 0
                                                    AND coalesce(outstanding_payments_total, 0) >= 0 AND invested_total >= 0),
    CONSTRAINT take_on_batches_trust_account_fkey FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id)
);
-- History list: newest first per dataset.
CREATE INDEX IF NOT EXISTS idx_take_on_batches_dataset_created ON take_on_batches (dataset, created_at DESC);
-- X5: at most one batch of a dataset committing at a time (double-submit and concurrent batches).
-- One committing batch per tenant (schema): the drain is single-threaded per tenant and this removes
-- every cross-dataset interleaving (§17.3.3).
CREATE UNIQUE INDEX IF NOT EXISTS uq_take_on_batches_committing ON take_on_batches ((true)) WHERE status = 'COMMITTING';
-- X5: one committed trust position per banking account.
CREATE UNIQUE INDEX IF NOT EXISTS uq_take_on_batches_trust_position ON take_on_batches (trust_account_id)
    WHERE dataset = 'TRUST_POSITION' AND status IN ('COMMITTING', 'COMPLETED');
-- Abandoned-batch purge (§17.3.14).
CREATE INDEX IF NOT EXISTS idx_take_on_batches_open_created ON take_on_batches (created_at)
    WHERE status IN ('UPLOADED', 'MAPPED', 'VALIDATED');

CREATE TABLE IF NOT EXISTS take_on_sources (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    section varchar(30) NOT NULL,
    file_name varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    file_format varchar(10) NOT NULL,
    file_size bigint NOT NULL,
    file_sha256 varchar(64) NOT NULL,
    s3_key varchar(500),
    sheet_names jsonb,
    sheet_name varchar(100),
    header_row integer NOT NULL DEFAULT 1,
    parse_options jsonb NOT NULL,
    column_mapping jsonb,
    row_count integer NOT NULL DEFAULT 0,
    malformed_count integer NOT NULL DEFAULT 0,
    blank_skipped integer NOT NULL DEFAULT 0,
    uploaded_by uuid NOT NULL,
    uploaded_at timestamptz NOT NULL,
    mapped_at timestamptz,
    purged_at timestamptz,
    CONSTRAINT take_on_sources_pkey PRIMARY KEY (id),
    CONSTRAINT uq_take_on_sources_batch_section UNIQUE (batch_id, section),
    CONSTRAINT chk_take_on_source_section CHECK (section IN ('MAIN', 'CLIENT_LEDGER', 'UNIDENTIFIED_DEPOSITS', 'INVESTMENTS', 'OUTSTANDING_ITEMS')),
    CONSTRAINT chk_take_on_source_format CHECK (file_format IN ('CSV', 'XLSX')),
    CONSTRAINT chk_take_on_source_size CHECK (file_size > 0 AND file_size <= 10485760),
    CONSTRAINT chk_take_on_source_header_row CHECK (header_row BETWEEN 1 AND 50),
    CONSTRAINT take_on_sources_batch_fkey FOREIGN KEY (batch_id) REFERENCES take_on_batches(id)
);

CREATE TABLE IF NOT EXISTS take_on_rows (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    source_id uuid NOT NULL,
    dataset varchar(30) NOT NULL,
    section varchar(30) NOT NULL,
    row_number integer NOT NULL,
    raw_values jsonb NOT NULL,
    mapped_values jsonb,
    source_ref varchar(200),
    dedup_key varchar(300),
    status varchar(20) NOT NULL,
    errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    warnings jsonb NOT NULL DEFAULT '[]'::jsonb,
    block boolean NOT NULL DEFAULT false,
    duplicate_of_id uuid,
    failure_reason varchar(1000),
    result_entity_type varchar(30),
    result_entity_id uuid,
    result_refs jsonb,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT take_on_rows_pkey PRIMARY KEY (id),
    CONSTRAINT uq_take_on_rows_source_row UNIQUE (source_id, row_number),
    CONSTRAINT chk_take_on_row_status CHECK (status IN ('PENDING', 'VALIDATED', 'ERROR', 'CREATED', 'SKIPPED_DUPLICATE', 'SKIPPED_ZERO', 'FAILED')),
    CONSTRAINT chk_take_on_row_block CHECK (NOT block OR status IN ('ERROR')),
    CONSTRAINT chk_take_on_row_result CHECK (status NOT IN ('CREATED', 'SKIPPED_DUPLICATE')
                                             OR (result_entity_type IS NOT NULL AND result_entity_id IS NOT NULL)),
    CONSTRAINT take_on_rows_batch_fkey FOREIGN KEY (batch_id) REFERENCES take_on_batches(id),
    CONSTRAINT take_on_rows_source_fkey FOREIGN KEY (source_id) REFERENCES take_on_sources(id)
);
-- Row table paging and status filters; the commit job's "next non-terminal row".
CREATE INDEX IF NOT EXISTS idx_take_on_rows_batch_status ON take_on_rows (batch_id, status, row_number);
-- Requirements decision 6 (RD-6): idempotency from dedup keys — the same record cannot be created twice, by any batch.
CREATE UNIQUE INDEX IF NOT EXISTS uq_take_on_rows_created_key ON take_on_rows (dataset, dedup_key)
    WHERE status = 'CREATED' AND dedup_key IS NOT NULL;
-- Later datasets resolve clients/matters by the previous system's id.
CREATE INDEX IF NOT EXISTS idx_take_on_rows_source_ref ON take_on_rows (dataset, lower(source_ref))
    WHERE status IN ('CREATED', 'SKIPPED_DUPLICATE');
-- Anonymisation redacts rows by the entity they produced.
CREATE INDEX IF NOT EXISTS idx_take_on_rows_result_entity ON take_on_rows (result_entity_id) WHERE result_entity_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS take_on_sign_offs (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    member_id uuid NOT NULL,
    member_name varchar(255) NOT NULL,
    role_name varchar(100) NOT NULL,
    statement_version varchar(40) NOT NULL,
    statement_text text NOT NULL,
    validation_hash varchar(64) NOT NULL,
    batch_defaults jsonb,
    signed_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoked_reason varchar(30),
    CONSTRAINT take_on_sign_offs_pkey PRIMARY KEY (id),
    CONSTRAINT chk_take_on_sign_off_kind CHECK (kind IN ('FICA_ATTESTATION', 'TRUST_SIGN_OFF')),
    CONSTRAINT chk_take_on_sign_off_revoked CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT chk_take_on_sign_off_reason CHECK (revoked_reason IS NULL OR revoked_reason IN ('REVALIDATED', 'REVOKED', 'CANCELLED')),
    CONSTRAINT take_on_sign_offs_batch_fkey FOREIGN KEY (batch_id) REFERENCES take_on_batches(id),
    CONSTRAINT take_on_sign_offs_member_fkey FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_take_on_sign_offs_live ON take_on_sign_offs (batch_id, kind) WHERE revoked_at IS NULL;

-- POPIA register (§17.6.5): seedJurisdictionDefaults runs only when the jurisdiction is set, so
-- existing ZA tenants get the take-on processing activity here, once.
INSERT INTO processing_activities (id, category, description, legal_basis, data_subjects, retention_period, recipients)
SELECT gen_random_uuid(), 'Client and Trust Take-On',
       'Migrating the firm''s client, matter, trust and debtor records from its previous system',
       'legal_obligation', 'Clients', 'As the migrated trust and FICA records', 'None'
WHERE EXISTS (SELECT 1 FROM org_settings WHERE data_protection_jurisdiction = 'ZA')
  AND NOT EXISTS (SELECT 1 FROM processing_activities WHERE category = 'Client and Trust Take-On');
