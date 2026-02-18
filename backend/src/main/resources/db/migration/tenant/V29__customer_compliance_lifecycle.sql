-- V29: Customer compliance & lifecycle
-- Phase 14 — lifecycle state machine, checklist engine, data requests, retention policies

-- ============================================================
-- 1. Customer table changes
-- ============================================================

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'PROSPECT',
    ADD COLUMN IF NOT EXISTS lifecycle_status_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS lifecycle_status_changed_by UUID,
    ADD COLUMN IF NOT EXISTS offboarded_at TIMESTAMP WITH TIME ZONE;

-- Backfill: all existing active customers to ACTIVE lifecycle status
UPDATE customers SET lifecycle_status = 'ACTIVE'
WHERE lifecycle_status = 'PROSPECT' AND status = 'ACTIVE';
-- Backfill: archived customers to OFFBOARDED
UPDATE customers SET lifecycle_status = 'OFFBOARDED', offboarded_at = updated_at
WHERE lifecycle_status = 'PROSPECT' AND status = 'ARCHIVED';

CREATE INDEX IF NOT EXISTS idx_customers_lifecycle_status
    ON customers (lifecycle_status);

-- ============================================================
-- 2. OrgSettings changes
-- ============================================================

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS dormancy_threshold_days INTEGER DEFAULT 90,
    ADD COLUMN IF NOT EXISTS data_request_deadline_days INTEGER DEFAULT 30,
    ADD COLUMN IF NOT EXISTS compliance_pack_status JSONB;

-- ============================================================
-- 3. Checklist templates
-- ============================================================

CREATE TABLE IF NOT EXISTS checklist_templates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    slug                VARCHAR(200) NOT NULL,
    description         TEXT,
    customer_type       VARCHAR(20) NOT NULL,
    source              VARCHAR(20) NOT NULL DEFAULT 'ORG_CUSTOM',
    pack_id             VARCHAR(100),
    pack_template_key   VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT true,
    auto_instantiate    BOOLEAN NOT NULL DEFAULT true,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_checklist_templates_slug UNIQUE (slug),
    CONSTRAINT chk_checklist_templates_slug CHECK (slug ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT chk_checklist_templates_pack CHECK (
        (pack_id IS NULL AND pack_template_key IS NULL) OR
        (pack_id IS NOT NULL AND pack_template_key IS NOT NULL)
    )
);

-- Index: find active templates by customer type for auto-instantiation
CREATE INDEX IF NOT EXISTS idx_checklist_templates_active_type
    ON checklist_templates (active, auto_instantiate, customer_type)
    WHERE active = true;

-- ============================================================
-- 4. Checklist template items
-- ============================================================

CREATE TABLE IF NOT EXISTS checklist_template_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id             UUID NOT NULL REFERENCES checklist_templates(id) ON DELETE CASCADE,
    name                    VARCHAR(300) NOT NULL,
    description             TEXT,
    sort_order              INTEGER NOT NULL,
    required                BOOLEAN NOT NULL DEFAULT true,
    requires_document       BOOLEAN NOT NULL DEFAULT false,
    required_document_label VARCHAR(200),
    depends_on_item_id      UUID REFERENCES checklist_template_items(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Index: fetch items by template in order
CREATE INDEX IF NOT EXISTS idx_checklist_template_items_template_sort
    ON checklist_template_items (template_id, sort_order);

-- ============================================================
-- 5. Checklist instances
-- ============================================================

CREATE TABLE IF NOT EXISTS checklist_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES checklist_templates(id),
    customer_id     UUID NOT NULL REFERENCES customers(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    completed_by    UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_checklist_instances_customer_template UNIQUE (customer_id, template_id)
);

-- Index: find instances by customer
CREATE INDEX IF NOT EXISTS idx_checklist_instances_customer
    ON checklist_instances (customer_id);

-- Index: find instances by status (for compliance dashboard)
CREATE INDEX IF NOT EXISTS idx_checklist_instances_status
    ON checklist_instances (status);

-- ============================================================
-- 6. Checklist instance items
-- ============================================================

CREATE TABLE IF NOT EXISTS checklist_instance_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id             UUID NOT NULL REFERENCES checklist_instances(id) ON DELETE CASCADE,
    template_item_id        UUID NOT NULL REFERENCES checklist_template_items(id),
    name                    VARCHAR(300) NOT NULL,
    description             TEXT,
    sort_order              INTEGER NOT NULL,
    required                BOOLEAN NOT NULL,
    requires_document       BOOLEAN NOT NULL,
    required_document_label VARCHAR(200),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at            TIMESTAMP WITH TIME ZONE,
    completed_by            UUID,
    notes                   TEXT,
    document_id             UUID REFERENCES documents(id),
    depends_on_item_id      UUID REFERENCES checklist_instance_items(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_checklist_instance_items_instance_template
        UNIQUE (instance_id, template_item_id)
);

-- Index: fetch items by instance in order
CREATE INDEX IF NOT EXISTS idx_checklist_instance_items_instance_sort
    ON checklist_instance_items (instance_id, sort_order);

-- Index: find items by status within an instance (completion check)
CREATE INDEX IF NOT EXISTS idx_checklist_instance_items_instance_status
    ON checklist_instance_items (instance_id, required, status);

-- Index: find items blocked by a specific item
CREATE INDEX IF NOT EXISTS idx_checklist_instance_items_depends_on
    ON checklist_instance_items (depends_on_item_id)
    WHERE depends_on_item_id IS NOT NULL;

-- ============================================================
-- 7. Data subject requests
-- ============================================================

CREATE TABLE IF NOT EXISTS data_subject_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customers(id),
    request_type    VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    description     TEXT NOT NULL,
    rejection_reason TEXT,
    deadline        DATE NOT NULL,
    requested_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    requested_by    UUID NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    completed_by    UUID,
    export_file_key VARCHAR(1000),
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Index: list open requests
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_status
    ON data_subject_requests (status);

-- Index: requests by customer
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_customer
    ON data_subject_requests (customer_id);

-- Index: approaching deadline
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_deadline
    ON data_subject_requests (deadline)
    WHERE status IN ('RECEIVED', 'IN_PROGRESS');

-- ============================================================
-- 8. Retention policies
-- ============================================================

CREATE TABLE IF NOT EXISTS retention_policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_type     VARCHAR(20) NOT NULL,
    retention_days  INTEGER NOT NULL,
    trigger_event   VARCHAR(30) NOT NULL,
    action          VARCHAR(20) NOT NULL DEFAULT 'FLAG',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_retention_policies_type_trigger UNIQUE (record_type, trigger_event)
);
