-- V83__create_legal_foundation_tables.sql
-- Phase 55: Court Calendar, Conflict Check, LSSA Tariff
-- NOTE: pg_trgm extension is created by global migration V16__enable_pg_trgm.sql

-- ============================================================
-- 1. Court Dates
-- ============================================================
CREATE TABLE court_dates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL,
    customer_id     UUID NOT NULL,
    date_type       VARCHAR(30) NOT NULL,
    scheduled_date  DATE NOT NULL,
    scheduled_time  TIME,
    court_name      VARCHAR(200) NOT NULL,
    court_reference VARCHAR(100),
    judge_magistrate VARCHAR(200),
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    outcome         TEXT,
    reminder_days   INTEGER NOT NULL DEFAULT 7,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_court_dates_project_date ON court_dates (project_id, scheduled_date);
CREATE INDEX idx_court_dates_customer ON court_dates (customer_id);
CREATE INDEX idx_court_dates_status ON court_dates (status);
CREATE INDEX idx_court_dates_reminder ON court_dates (scheduled_date, status)
    WHERE status IN ('SCHEDULED', 'POSTPONED');

-- ============================================================
-- 2. Prescription Trackers
-- ============================================================
CREATE TABLE prescription_trackers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id           UUID NOT NULL,
    customer_id          UUID NOT NULL,
    cause_of_action_date DATE NOT NULL,
    prescription_type    VARCHAR(30) NOT NULL,
    custom_years         INTEGER,
    prescription_date    DATE NOT NULL,
    interruption_date    DATE,
    interruption_reason  VARCHAR(200),
    status               VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    notes                TEXT,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_prescription_trackers_project ON prescription_trackers (project_id);
CREATE INDEX idx_prescription_trackers_date ON prescription_trackers (prescription_date)
    WHERE status IN ('RUNNING', 'WARNED');
CREATE INDEX idx_prescription_trackers_customer ON prescription_trackers (customer_id);

-- ============================================================
-- 3. Adverse Parties
-- ============================================================
CREATE TABLE adverse_parties (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(300) NOT NULL,
    id_number           VARCHAR(20),
    registration_number VARCHAR(30),
    party_type          VARCHAR(20) NOT NULL,
    aliases             TEXT,
    notes               TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_adverse_parties_name_trgm ON adverse_parties
    USING GIN (name gin_trgm_ops);
CREATE INDEX idx_adverse_parties_id_number ON adverse_parties (id_number)
    WHERE id_number IS NOT NULL;
CREATE INDEX idx_adverse_parties_reg_number ON adverse_parties (registration_number)
    WHERE registration_number IS NOT NULL;
CREATE INDEX idx_adverse_parties_aliases_trgm ON adverse_parties
    USING GIN (aliases gin_trgm_ops);

-- ============================================================
-- 4. Adverse Party Links
-- ============================================================
CREATE TABLE adverse_party_links (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adverse_party_id UUID NOT NULL,
    project_id       UUID NOT NULL,
    customer_id      UUID NOT NULL,
    relationship     VARCHAR(30) NOT NULL,
    description      TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_adverse_party_project UNIQUE (adverse_party_id, project_id)
);

CREATE INDEX idx_adverse_party_links_project ON adverse_party_links (project_id);
CREATE INDEX idx_adverse_party_links_party ON adverse_party_links (adverse_party_id);
CREATE INDEX idx_adverse_party_links_customer ON adverse_party_links (customer_id);

-- ============================================================
-- 5. Conflict Checks
-- ============================================================
CREATE TABLE conflict_checks (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checked_name                VARCHAR(300) NOT NULL,
    checked_id_number           VARCHAR(20),
    checked_registration_number VARCHAR(30),
    check_type                  VARCHAR(20) NOT NULL,
    result                      VARCHAR(20) NOT NULL,
    conflicts_found             JSONB,
    resolution                  VARCHAR(30),
    resolution_notes            TEXT,
    waiver_document_id          UUID,
    checked_by                  UUID NOT NULL,
    resolved_by                 UUID,
    checked_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMP WITH TIME ZONE,
    customer_id                 UUID,
    project_id                  UUID
);

CREATE INDEX idx_conflict_checks_checked_by ON conflict_checks (checked_by);
CREATE INDEX idx_conflict_checks_checked_at ON conflict_checks (checked_at);
CREATE INDEX idx_conflict_checks_customer ON conflict_checks (customer_id)
    WHERE customer_id IS NOT NULL;
CREATE INDEX idx_conflict_checks_project ON conflict_checks (project_id)
    WHERE project_id IS NOT NULL;

-- ============================================================
-- 6. Tariff Schedules
-- ============================================================
CREATE TABLE tariff_schedules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(20) NOT NULL,
    court_level    VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE,
    is_active      BOOLEAN NOT NULL DEFAULT true,
    is_system      BOOLEAN NOT NULL DEFAULT false,
    source         VARCHAR(100),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_tariff_schedules_active ON tariff_schedules (category, court_level, is_active)
    WHERE is_active = true;

-- ============================================================
-- 7. Tariff Items
-- ============================================================
CREATE TABLE tariff_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id UUID NOT NULL REFERENCES tariff_schedules(id) ON DELETE CASCADE,
    item_number VARCHAR(20) NOT NULL,
    section     VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    unit        VARCHAR(30) NOT NULL,
    notes       TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_tariff_items_schedule ON tariff_items (schedule_id);
CREATE INDEX idx_tariff_items_description_trgm ON tariff_items
    USING GIN (description gin_trgm_ops);

-- ============================================================
-- 8. InvoiceLine Extension
-- ============================================================
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS tariff_item_id UUID;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS line_source VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_tariff_item ON invoice_lines (tariff_item_id)
    WHERE tariff_item_id IS NOT NULL;

-- ============================================================
-- 9. Capability Seeding — VIEW_LEGAL and MANAGE_LEGAL
-- ============================================================

-- Owner: add VIEW_LEGAL
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_LEGAL'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_LEGAL'
  );

-- Owner: add MANAGE_LEGAL
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_LEGAL'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_LEGAL'
  );

-- Admin: add VIEW_LEGAL
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_LEGAL'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_LEGAL'
  );

-- Admin: add MANAGE_LEGAL
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'MANAGE_LEGAL'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'MANAGE_LEGAL'
  );

-- Member: add VIEW_LEGAL only (no MANAGE_LEGAL)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'VIEW_LEGAL'
FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'VIEW_LEGAL'
  );
