-- =============================================================================
-- V68: Pending Invitations table (Phase 42 -- Invitation flow)
-- =============================================================================

CREATE TABLE pending_invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL,
    org_role_id UUID         NOT NULL REFERENCES org_roles (id),
    invited_by  UUID         NOT NULL REFERENCES members (id),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pending_invitations_email ON pending_invitations (email);
CREATE INDEX idx_pending_invitations_status ON pending_invitations (status);
