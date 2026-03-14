-- Stores intended roles for invited users.
-- Gateway writes at invite time; backend MemberFilter reads at first login.
-- Consumed (deleted) after the member record is created with the correct role.
CREATE TABLE IF NOT EXISTS pending_invitations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_slug   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'member',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_inv_org_email
    ON pending_invitations (org_slug, lower(email));
