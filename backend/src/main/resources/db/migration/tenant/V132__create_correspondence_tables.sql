-- db/migration/tenant/V132__create_correspondence_tables.sql
-- Phase 81: inbound correspondence domain + Document->Correspondence link.
-- Per-tenant schema (search_path = tenant). No tenant_id column (schema-per-tenant isolation).

CREATE TABLE correspondence (
    id                 UUID PRIMARY KEY,
    customer_id        UUID,                       -- nullable FK -> customers(id)
    project_id         UUID,                       -- nullable FK -> projects(id)  (matter)
    direction          VARCHAR(10)  NOT NULL DEFAULT 'INBOUND',
    subject            VARCHAR(500),
    body_text          TEXT,
    body_html          TEXT,
    from_address       VARCHAR(320) NOT NULL,
    to_addresses       JSONB,
    cc_addresses       JSONB,
    sent_at            TIMESTAMPTZ,
    received_at        TIMESTAMPTZ,
    thread_key         VARCHAR(255),
    message_id         VARCHAR(512) NOT NULL,
    source             VARCHAR(30)  NOT NULL DEFAULT 'MCP',
    filed_by_member_id UUID         NOT NULL,
    filed_at           TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_correspondence_linkage CHECK (customer_id IS NOT NULL OR project_id IS NOT NULL)
);

-- Idempotency key: re-filing the same email is a no-op (find-then-insert; this is the race backstop).
-- message_id is tenant-scoped because the schema IS the tenant boundary.
CREATE UNIQUE INDEX ux_correspondence_message_id ON correspondence (message_id);

-- List a matter's / client's correspondence newest-first (matter-detail tab, paginated).
CREATE INDEX ix_correspondence_project  ON correspondence (project_id, received_at DESC);
CREATE INDEX ix_correspondence_customer ON correspondence (customer_id, received_at DESC);

-- Thread grouping hook (v2); cheap to add now, avoids a later migration.
CREATE INDEX ix_correspondence_thread   ON correspondence (thread_key);

-- Link a filed attachment (a Document) back to its correspondence. Mirrors documents.ai_execution_id.
ALTER TABLE documents ADD COLUMN correspondence_id UUID;   -- nullable; set on attach_document confirm
CREATE INDEX ix_documents_correspondence ON documents (correspondence_id);
