-- V55: Create information request tables (request templates, template items, counters)
-- Phase 34: Client Information Requests (Epic 252A)

-- 1. Request templates
CREATE TABLE IF NOT EXISTS request_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000),
    source          VARCHAR(20)     NOT NULL DEFAULT 'CUSTOM',
    pack_id         VARCHAR(100),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_templates_active ON request_templates (active);
CREATE INDEX IF NOT EXISTS idx_request_templates_pack_id ON request_templates (pack_id);

-- 2. Request template items
CREATE TABLE IF NOT EXISTS request_template_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID            NOT NULL REFERENCES request_templates(id) ON DELETE CASCADE,
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000),
    response_type   VARCHAR(20)     NOT NULL,
    required        BOOLEAN         NOT NULL DEFAULT TRUE,
    file_type_hints VARCHAR(200),
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_template_items_template ON request_template_items (template_id);

-- 3. Request counters (singleton for request numbering, used by RequestNumberService in Epic 253)
CREATE TABLE IF NOT EXISTS request_counters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    next_number     INTEGER         NOT NULL DEFAULT 1,
    singleton       BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_request_counter_positive CHECK (next_number > 0),
    CONSTRAINT request_counters_singleton UNIQUE (singleton),
    CONSTRAINT chk_request_counter_singleton CHECK (singleton = TRUE)
);

-- Seed the counter with a single row (idempotent)
INSERT INTO request_counters (id, next_number)
VALUES (gen_random_uuid(), 1)
ON CONFLICT DO NOTHING;
