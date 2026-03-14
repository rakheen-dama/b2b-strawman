You-- Gateway test schema: provides pending_invitations table for H2 in-memory DB.
-- In production, this table is created by the backend's Flyway migration (V16).
CREATE TABLE IF NOT EXISTS pending_invitations (
    id         UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    org_slug   VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'member',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
