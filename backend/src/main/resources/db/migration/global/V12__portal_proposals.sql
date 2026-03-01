-- V12__portal_proposals.sql
-- Phase 32: Portal read-model for proposals

CREATE TABLE IF NOT EXISTS portal.portal_proposals (
    id                  UUID PRIMARY KEY,
    org_id              VARCHAR(255) NOT NULL,
    customer_id         UUID NOT NULL,
    portal_contact_id   UUID NOT NULL,
    proposal_number     VARCHAR(20) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    fee_model           VARCHAR(20) NOT NULL,
    fee_amount          NUMERIC(12,2),
    fee_currency        VARCHAR(3),
    content_html        TEXT NOT NULL,
    milestones_json     JSONB DEFAULT '[]',
    sent_at             TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    org_name            VARCHAR(255) NOT NULL,
    org_logo_url        VARCHAR(500),
    org_brand_color     VARCHAR(7),
    synced_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Portal contact's proposals (list query)
CREATE INDEX IF NOT EXISTS idx_portal_proposals_contact
    ON portal.portal_proposals(portal_contact_id, status);

-- Customer-scoped proposals
CREATE INDEX IF NOT EXISTS idx_portal_proposals_customer
    ON portal.portal_proposals(org_id, customer_id);
