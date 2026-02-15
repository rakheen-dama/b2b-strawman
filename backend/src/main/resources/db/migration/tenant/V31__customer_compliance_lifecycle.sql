-- V31__customer_compliance_lifecycle.sql
-- Phase 13: Customer Compliance & Lifecycle
-- Adds: lifecycle status on customers, checklist engine tables,
--        data subject requests, retention policies

-- =============================================================================
-- 1. Customer table alterations
-- =============================================================================

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'PROSPECT',
    ADD COLUMN IF NOT EXISTS lifecycle_status_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS lifecycle_status_changed_by UUID,
    ADD COLUMN IF NOT EXISTS offboarded_at TIMESTAMP WITH TIME ZONE;

-- Validate lifecycle_status values at the database level
ALTER TABLE customers
    ADD CONSTRAINT chk_customer_lifecycle_status
    CHECK (lifecycle_status IN ('PROSPECT','ONBOARDING','ACTIVE','DORMANT','OFFBOARDED'));

-- Index for filtering customers by lifecycle status (compliance dashboard, saved views)
CREATE INDEX IF NOT EXISTS idx_customers_lifecycle_status
    ON customers(lifecycle_status);

-- Index for tenant + lifecycle status (shared schema queries)
CREATE INDEX IF NOT EXISTS idx_customers_tenant_lifecycle
    ON customers(tenant_id, lifecycle_status);

-- Backfill: set existing customers (created before this migration) to ACTIVE lifecycle status.
-- They already exist and are in use, so they're past the onboarding stage.
UPDATE customers SET lifecycle_status = 'ACTIVE',
    lifecycle_status_changed_at = created_at,
    lifecycle_status_changed_by = created_by
WHERE lifecycle_status = 'PROSPECT';

-- =============================================================================
-- 2. OrgSettings table alterations
-- =============================================================================

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS dormancy_threshold_days INTEGER DEFAULT 90,
    ADD COLUMN IF NOT EXISTS data_request_deadline_days INTEGER DEFAULT 30,
    ADD COLUMN IF NOT EXISTS compliance_pack_status JSONB;

-- =============================================================================
-- 3. Checklist Templates
-- =============================================================================

CREATE TABLE IF NOT EXISTS checklist_templates (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255),
    name                 VARCHAR(200)  NOT NULL,
    slug                 VARCHAR(200)  NOT NULL,
    description          TEXT,
    customer_type        VARCHAR(20)   NOT NULL DEFAULT 'ANY',
    source               VARCHAR(20)   NOT NULL DEFAULT 'ORG_CUSTOM',
    pack_id              VARCHAR(100),
    pack_template_key    VARCHAR(200),
    active               BOOLEAN       NOT NULL DEFAULT true,
    auto_instantiate     BOOLEAN       NOT NULL DEFAULT true,
    sort_order           INTEGER       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Unique slug per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_template_tenant_slug
    ON checklist_templates(tenant_id, slug);

-- List active templates for a customer type
CREATE INDEX IF NOT EXISTS idx_checklist_template_active
    ON checklist_templates(tenant_id, active, customer_type);

-- RLS
ALTER TABLE checklist_templates ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'checklist_templates_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY checklist_templates_tenant_isolation ON checklist_templates
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- 4. Checklist Template Items
-- =============================================================================

CREATE TABLE IF NOT EXISTS checklist_template_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              VARCHAR(255),
    template_id            UUID          NOT NULL REFERENCES checklist_templates(id) ON DELETE CASCADE,
    name                   VARCHAR(300)  NOT NULL,
    description            TEXT,
    sort_order             INTEGER       NOT NULL DEFAULT 0,
    required               BOOLEAN       NOT NULL DEFAULT true,
    requires_document      BOOLEAN       NOT NULL DEFAULT false,
    required_document_label VARCHAR(200),
    depends_on_item_id     UUID          REFERENCES checklist_template_items(id) ON DELETE SET NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- List items for a template, ordered
CREATE INDEX IF NOT EXISTS idx_checklist_template_items_template
    ON checklist_template_items(template_id, sort_order);

-- Shared schema
CREATE INDEX IF NOT EXISTS idx_checklist_template_items_tenant
    ON checklist_template_items(tenant_id);

-- RLS
ALTER TABLE checklist_template_items ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'checklist_template_items_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY checklist_template_items_tenant_isolation ON checklist_template_items
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- 5. Checklist Instances
-- =============================================================================

CREATE TABLE IF NOT EXISTS checklist_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    template_id     UUID          NOT NULL REFERENCES checklist_templates(id),
    customer_id     UUID          NOT NULL REFERENCES customers(id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    completed_by    UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- One instance per template per customer per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_instance_customer_template
    ON checklist_instances(tenant_id, customer_id, template_id);

-- List instances for a customer
CREATE INDEX IF NOT EXISTS idx_checklist_instance_customer
    ON checklist_instances(tenant_id, customer_id, status);

-- RLS
ALTER TABLE checklist_instances ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'checklist_instances_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY checklist_instances_tenant_isolation ON checklist_instances
      USING (tenant_id = current_setting(''app.current_tenant'', true))';
  END IF;
END $$;

-- =============================================================================
-- 6. Checklist Instance Items
-- =============================================================================

CREATE TABLE IF NOT EXISTS checklist_instance_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              VARCHAR(255) NOT NULL,
    instance_id            UUID          NOT NULL REFERENCES checklist_instances(id) ON DELETE CASCADE,
    template_item_id       UUID          NOT NULL REFERENCES checklist_template_items(id),
    name                   VARCHAR(300)  NOT NULL,
    description            TEXT,
    sort_order             INTEGER       NOT NULL,
    required               BOOLEAN       NOT NULL,
    requires_document      BOOLEAN       NOT NULL,
    required_document_label VARCHAR(200),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    completed_at           TIMESTAMP WITH TIME ZONE,
    completed_by           UUID,
    notes                  TEXT,
    document_id            UUID          REFERENCES documents(id) ON DELETE SET NULL,
    depends_on_item_id     UUID          REFERENCES checklist_instance_items(id) ON DELETE SET NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- One instance item per template item per instance
CREATE UNIQUE INDEX IF NOT EXISTS uq_checklist_instance_item
    ON checklist_instance_items(instance_id, template_item_id);

-- List items for an instance, ordered
CREATE INDEX IF NOT EXISTS idx_checklist_instance_items_instance
    ON checklist_instance_items(instance_id, sort_order);

-- Shared schema
CREATE INDEX IF NOT EXISTS idx_checklist_instance_items_tenant
    ON checklist_instance_items(tenant_id);

-- RLS
ALTER TABLE checklist_instance_items ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'checklist_instance_items_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY checklist_instance_items_tenant_isolation ON checklist_instance_items
      USING (tenant_id = current_setting(''app.current_tenant'', true))';
  END IF;
END $$;

-- =============================================================================
-- 7. Data Subject Requests
-- =============================================================================

CREATE TABLE IF NOT EXISTS data_subject_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(255) NOT NULL,
    customer_id       UUID          NOT NULL REFERENCES customers(id),
    request_type      VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',
    description       TEXT          NOT NULL,
    rejection_reason  TEXT,
    deadline          DATE          NOT NULL,
    requested_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    requested_by      UUID          NOT NULL,
    completed_at      TIMESTAMP WITH TIME ZONE,
    completed_by      UUID,
    export_file_key   VARCHAR(500),
    notes             TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Open requests by status
CREATE INDEX IF NOT EXISTS idx_data_requests_status
    ON data_subject_requests(tenant_id, status);

-- Requests for a customer
CREATE INDEX IF NOT EXISTS idx_data_requests_customer
    ON data_subject_requests(tenant_id, customer_id);

-- Approaching deadlines
CREATE INDEX IF NOT EXISTS idx_data_requests_deadline
    ON data_subject_requests(tenant_id, deadline);

-- Recent requests (compliance dashboard "open requests" list)
CREATE INDEX IF NOT EXISTS idx_data_requests_requested
    ON data_subject_requests(tenant_id, requested_at DESC);

-- RLS
ALTER TABLE data_subject_requests ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'data_subject_requests_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY data_subject_requests_tenant_isolation ON data_subject_requests
      USING (tenant_id = current_setting(''app.current_tenant'', true))';
  END IF;
END $$;

-- =============================================================================
-- 8. Retention Policies
-- =============================================================================

CREATE TABLE IF NOT EXISTS retention_policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    record_type     VARCHAR(30)   NOT NULL,
    retention_days  INTEGER       NOT NULL,
    trigger_event   VARCHAR(30)   NOT NULL,
    action          VARCHAR(20)   NOT NULL DEFAULT 'FLAG',
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_retention_days_positive CHECK (retention_days > 0)
);

-- One policy per record type per trigger per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_retention_policy
    ON retention_policies(tenant_id, record_type, trigger_event);

-- Active policies for a tenant
CREATE INDEX IF NOT EXISTS idx_retention_policies_active
    ON retention_policies(tenant_id, active);

-- RLS
ALTER TABLE retention_policies ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'retention_policies_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY retention_policies_tenant_isolation ON retention_policies
      USING (tenant_id = current_setting(''app.current_tenant'', true))';
  END IF;
END $$;
