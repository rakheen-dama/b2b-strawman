-- V11__portal_acceptance_requests.sql
-- Phase 28: Portal read-model table for acceptance request list views

CREATE TABLE IF NOT EXISTS portal.portal_acceptance_requests (
    id                    UUID PRIMARY KEY,
    portal_contact_id     UUID NOT NULL,
    generated_document_id UUID NOT NULL,
    document_title        VARCHAR(500) NOT NULL,
    document_file_name    VARCHAR(500) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    request_token         VARCHAR(255) NOT NULL,
    sent_at               TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,
    org_name              VARCHAR(255) NOT NULL,
    org_logo              VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Portal contact's pending acceptances
CREATE INDEX IF NOT EXISTS idx_portal_acceptance_contact_status
    ON portal.portal_acceptance_requests (portal_contact_id, status);

-- Token lookup (for constructing links in portal list views)
CREATE INDEX IF NOT EXISTS idx_portal_acceptance_token
    ON portal.portal_acceptance_requests (request_token);
