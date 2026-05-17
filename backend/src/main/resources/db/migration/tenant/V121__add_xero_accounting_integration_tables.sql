-- =============================================================================
-- V121: Xero Accounting Integration Tables
-- Phase 71 — accounting_xero_connection, accounting_sync_entry, accounting_tax_code_mapping
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. accounting_xero_connection
-- Tracks OAuth2 connection metadata for a tenant's Xero org.
-- One row per OrgIntegration (unique constraint on org_integration_id).
-- Refresh tokens live in SecretStore (org_secrets table), never here.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounting_xero_connection (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_integration_id      UUID NOT NULL,
    xero_tenant_id          VARCHAR(50) NOT NULL,
    xero_org_name           VARCHAR(255) NOT NULL,
    connected_by_member_id  UUID NOT NULL,
    connected_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_token_refresh_at   TIMESTAMPTZ,
    access_token_expires_at TIMESTAMPTZ NOT NULL,
    scope                   VARCHAR(500) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'CONNECTED',
    last_poll_at            TIMESTAMPTZ,
    disconnected_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_axc_org_integration
        FOREIGN KEY (org_integration_id) REFERENCES org_integrations(id),
    CONSTRAINT uq_axc_org_integration
        UNIQUE (org_integration_id),
    CONSTRAINT ck_axc_status
        CHECK (status IN ('CONNECTED', 'REFRESH_FAILED', 'REVOKED'))
);

-- Index: lookup by status for payment poll worker
CREATE INDEX IF NOT EXISTS idx_axc_status
    ON accounting_xero_connection (status);

-- -----------------------------------------------------------------------------
-- 2. accounting_sync_entry
-- Sync queue and state machine for outbound pushes and inbound pull records.
-- The sync worker drains entries sorted by (state, next_attempt_at).
-- The invoice detail page looks up entries by (entity_type, entity_id).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounting_sync_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(20) NOT NULL,
    entity_id           UUID NOT NULL,
    provider_id         VARCHAR(20) NOT NULL,
    direction           VARCHAR(10) NOT NULL,
    state               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count       INT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ,
    last_error_code     VARCHAR(50),
    last_error_detail   TEXT,
    external_reference  VARCHAR(100),
    external_id         VARCHAR(100),
    trigger             VARCHAR(30) NOT NULL DEFAULT 'EVENT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT ck_ase_entity_type
        CHECK (entity_type IN ('INVOICE', 'CUSTOMER', 'PAYMENT_PULL')),
    CONSTRAINT ck_ase_direction
        CHECK (direction IN ('PUSH', 'PULL')),
    CONSTRAINT ck_ase_state
        CHECK (state IN (
            'PENDING', 'IN_FLIGHT', 'COMPLETED', 'FAILED_RETRYING',
            'DEAD_LETTER', 'BLOCKED_TRUST_BOUNDARY', 'RECONCILE_DRIFT'
        )),
    CONSTRAINT ck_ase_trigger
        CHECK (trigger IN ('EVENT', 'MANUAL_RETRY', 'FORCE_RESYNC'))
);

-- Index: worker drain query — state + next_attempt_at for efficient polling
-- The worker queries: WHERE state IN ('PENDING', 'FAILED_RETRYING') AND next_attempt_at <= now
CREATE INDEX IF NOT EXISTS idx_ase_drain
    ON accounting_sync_entry (state, next_attempt_at)
    WHERE state IN ('PENDING', 'FAILED_RETRYING');

-- Index: invoice/customer status lookup — "what's the sync status of this invoice?"
CREATE INDEX IF NOT EXISTS idx_ase_entity_lookup
    ON accounting_sync_entry (entity_type, entity_id);

-- Index: external_reference lookup for payment pull matching
CREATE INDEX IF NOT EXISTS idx_ase_external_reference
    ON accounting_sync_entry (external_reference)
    WHERE external_reference IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 3. accounting_tax_code_mapping
-- Maps Kazi tax modes to Xero tax codes. Pre-seeded with ZA defaults.
-- One mapping per (provider_id, kazi_tax_mode) pair.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounting_tax_code_mapping (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         VARCHAR(20) NOT NULL,
    kazi_tax_mode       VARCHAR(30) NOT NULL,
    external_tax_code   VARCHAR(50) NOT NULL,
    display_label       VARCHAR(100) NOT NULL,
    is_default          BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_atcm_provider_tax_mode
        UNIQUE (provider_id, kazi_tax_mode),
    CONSTRAINT ck_atcm_kazi_tax_mode
        CHECK (kazi_tax_mode IN (
            'STANDARD_15', 'ZERO_RATED', 'EXEMPT', 'OUT_OF_SCOPE', 'STANDARD_OTHER'
        ))
);

-- -----------------------------------------------------------------------------
-- 4. Pre-seed ZA tax code defaults
-- These are inserted on migration but will also be inserted by XeroOAuthService
-- on first connect if they don't already exist. ON CONFLICT ensures idempotency.
-- NOTE: STANDARD_OTHER is intentionally NOT pre-seeded — it has no universal ZA default.
-- Tenants needing it (non-ZA, or future VAT rate changes) configure it manually via UI.
-- -----------------------------------------------------------------------------
INSERT INTO accounting_tax_code_mapping
    (provider_id, kazi_tax_mode, external_tax_code, display_label, is_default)
VALUES
    ('xero', 'STANDARD_15', 'OUTPUT2', 'Standard Rate (15%)', true),
    ('xero', 'ZERO_RATED', 'ZERORATEDOUTPUT', 'Zero Rated Output', true),
    ('xero', 'EXEMPT', 'EXEMPTOUTPUT', 'Exempt Output', true),
    ('xero', 'OUT_OF_SCOPE', 'NONE', 'No Tax / Out of Scope', true)
ON CONFLICT (provider_id, kazi_tax_mode) DO NOTHING;
