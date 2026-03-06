-- V14__portal_requests.sql

CREATE TABLE portal.portal_requests (
    id                  UUID PRIMARY KEY,
    request_number      VARCHAR(20)     NOT NULL,
    customer_id         UUID            NOT NULL,
    portal_contact_id   UUID            NOT NULL,
    project_id          UUID,
    project_name        VARCHAR(300),
    org_id              VARCHAR(255)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    total_items         INTEGER         NOT NULL DEFAULT 0,
    submitted_items     INTEGER         NOT NULL DEFAULT 0,
    accepted_items      INTEGER         NOT NULL DEFAULT 0,
    rejected_items      INTEGER         NOT NULL DEFAULT 0,
    sent_at             TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    synced_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portal_requests_contact ON portal.portal_requests (portal_contact_id);
CREATE INDEX idx_portal_requests_customer ON portal.portal_requests (customer_id, org_id);
CREATE INDEX idx_portal_requests_status ON portal.portal_requests (status);

CREATE TABLE portal.portal_request_items (
    id                  UUID PRIMARY KEY,
    request_id          UUID            NOT NULL REFERENCES portal.portal_requests(id) ON DELETE CASCADE,
    name                VARCHAR(200)    NOT NULL,
    description         TEXT,
    response_type       VARCHAR(20)     NOT NULL,
    required            BOOLEAN         NOT NULL DEFAULT TRUE,
    file_type_hints     VARCHAR(200),
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    rejection_reason    VARCHAR(500),
    document_id         UUID,
    text_response       TEXT,
    synced_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portal_request_items_request ON portal.portal_request_items (request_id);
