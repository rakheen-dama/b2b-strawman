-- V57: Create information_requests and request_items tables
-- Phase 34: Client Information Requests (Epic 253A)

-- 1. Information requests
CREATE TABLE IF NOT EXISTS information_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_number          VARCHAR(20)     NOT NULL,
    request_template_id     UUID            REFERENCES request_templates(id),
    customer_id             UUID            NOT NULL,
    project_id              UUID,
    portal_contact_id       UUID            NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    reminder_interval_days  INTEGER,
    last_reminder_sent_at   TIMESTAMPTZ,
    sent_at                 TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    created_by              UUID            NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_information_requests_number UNIQUE (request_number)
);

CREATE INDEX IF NOT EXISTS idx_information_requests_customer ON information_requests (customer_id);
CREATE INDEX IF NOT EXISTS idx_information_requests_project ON information_requests (project_id);
CREATE INDEX IF NOT EXISTS idx_information_requests_portal_contact ON information_requests (portal_contact_id);
CREATE INDEX IF NOT EXISTS idx_information_requests_status ON information_requests (status);
CREATE INDEX IF NOT EXISTS idx_information_requests_reminder ON information_requests (status, last_reminder_sent_at)
    WHERE status IN ('SENT', 'IN_PROGRESS');

-- 2. Request items
CREATE TABLE IF NOT EXISTS request_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID            NOT NULL REFERENCES information_requests(id) ON DELETE CASCADE,
    template_item_id    UUID,
    name                VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000),
    response_type       VARCHAR(20)     NOT NULL,
    required            BOOLEAN         NOT NULL DEFAULT TRUE,
    file_type_hints     VARCHAR(200),
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    document_id         UUID,
    text_response       TEXT,
    rejection_reason    VARCHAR(500),
    submitted_at        TIMESTAMPTZ,
    reviewed_at         TIMESTAMPTZ,
    reviewed_by         UUID,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_items_request ON request_items (request_id);
CREATE INDEX IF NOT EXISTS idx_request_items_status ON request_items (status);
CREATE INDEX IF NOT EXISTS idx_request_items_review ON request_items (request_id, status)
    WHERE status = 'SUBMITTED';
