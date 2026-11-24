-- Phase 93 / 657C (architecture §17.2.7, §17.7.6, ADR-391): the immutable take-on report and its rendered files.
-- Tenant V36 (guide V34; V35 = 656A go-live checklist; next free at build time, P-1). New tables only. No CASCADE.
-- take_on_report_files and take_on_report_renders are first written in 657D (the 647A precedent).
CREATE TABLE IF NOT EXISTS take_on_reports (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    trust_account_id uuid NOT NULL,
    cutover_date date NOT NULL,
    snapshot jsonb NOT NULL,
    snapshot_sha256 varchar(64) NOT NULL,
    generated_by uuid NOT NULL,
    generated_at timestamptz NOT NULL,
    CONSTRAINT take_on_reports_pkey PRIMARY KEY (id),
    CONSTRAINT uq_take_on_reports_batch UNIQUE (batch_id),
    CONSTRAINT take_on_reports_batch_fkey FOREIGN KEY (batch_id) REFERENCES take_on_batches(id),
    CONSTRAINT take_on_reports_account_fkey FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_take_on_reports_account ON take_on_reports (trust_account_id, cutover_date DESC);

CREATE TABLE IF NOT EXISTS take_on_report_files (
    id uuid NOT NULL,
    report_id uuid NOT NULL,
    format varchar(10) NOT NULL,
    s3_key varchar(500) NOT NULL,
    sha256 varchar(64) NOT NULL,
    file_size bigint NOT NULL,
    rendered_at timestamptz NOT NULL,
    CONSTRAINT take_on_report_files_pkey PRIMARY KEY (id),
    CONSTRAINT uq_take_on_report_files_format UNIQUE (report_id, format),
    CONSTRAINT chk_take_on_report_file_format CHECK (format IN ('PDF', 'CSV')),
    CONSTRAINT take_on_report_files_report_fkey FOREIGN KEY (report_id) REFERENCES take_on_reports(id)
);

-- Render attempts, kept apart from the @Immutable snapshot (§17.3.12): five failures stop the sweep.
CREATE TABLE IF NOT EXISTS take_on_report_renders (
    report_id uuid NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    last_error_code varchar(50),
    last_attempt_at timestamptz,
    failed_at timestamptz,
    CONSTRAINT take_on_report_renders_pkey PRIMARY KEY (report_id),
    CONSTRAINT chk_take_on_report_render_attempts CHECK (attempts >= 0),
    CONSTRAINT take_on_report_renders_report_fkey FOREIGN KEY (report_id) REFERENCES take_on_reports(id)
);
