-- =============================================================================
-- V68: PendingInvitation table (Phase 46 -- RBAC Decoupling)
-- =============================================================================

CREATE TABLE pending_invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255)  NOT NULL,
    org_role_id UUID          NOT NULL REFERENCES org_roles (id),
    invited_by  UUID          NOT NULL REFERENCES members (id),
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ
);

-- Only one active (PENDING) invitation per email per tenant
CREATE UNIQUE INDEX uq_pending_invitation_email_pending
    ON pending_invitations (email) WHERE (status = 'PENDING');

-- Lookup by email + status for MemberFilter lazy-create path
CREATE INDEX idx_pending_invitations_email_status
    ON pending_invitations (email, status);
