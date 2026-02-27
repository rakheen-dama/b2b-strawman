-- V45__create_acceptance_requests.sql
-- Phase 28: AcceptanceRequest table + OrgSettings extension for document acceptance

CREATE TABLE acceptance_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generated_document_id UUID NOT NULL,
    portal_contact_id     UUID NOT NULL,
    customer_id           UUID NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    request_token         VARCHAR(255) NOT NULL,
    sent_at               TIMESTAMPTZ,
    viewed_at             TIMESTAMPTZ,
    accepted_at           TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    acceptor_name         VARCHAR(255),
    acceptor_ip_address   VARCHAR(45),
    acceptor_user_agent   VARCHAR(500),
    certificate_s3_key    VARCHAR(1000),
    certificate_file_name VARCHAR(255),
    sent_by_member_id     UUID NOT NULL,
    revoked_by_member_id  UUID,
    reminder_count        INTEGER NOT NULL DEFAULT 0,
    last_reminded_at      TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique token for direct lookup from portal
CREATE UNIQUE INDEX idx_acceptance_requests_token
    ON acceptance_requests (request_token);

-- One active request per document-contact pair
-- (PENDING, SENT, VIEWED are "active" statuses; ACCEPTED, EXPIRED, REVOKED are terminal)
CREATE UNIQUE INDEX idx_acceptance_requests_active_unique
    ON acceptance_requests (generated_document_id, portal_contact_id)
    WHERE status IN ('PENDING', 'SENT', 'VIEWED');

-- Customer-level queries ("all acceptances for customer X")
CREATE INDEX idx_acceptance_requests_customer_status
    ON acceptance_requests (customer_id, status);

-- Per-document queries ("all acceptances for document Y")
CREATE INDEX idx_acceptance_requests_document
    ON acceptance_requests (generated_document_id);

-- Expiry batch processing
CREATE INDEX idx_acceptance_requests_expiry
    ON acceptance_requests (status, expires_at)
    WHERE status IN ('PENDING', 'SENT', 'VIEWED');

-- OrgSettings extension: acceptance expiry default
ALTER TABLE org_settings
    ADD COLUMN acceptance_expiry_days INTEGER;
