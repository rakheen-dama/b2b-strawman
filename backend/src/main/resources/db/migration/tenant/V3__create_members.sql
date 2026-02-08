CREATE TABLE IF NOT EXISTS members (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    name           VARCHAR(255),
    avatar_url     VARCHAR(1000),
    org_role       VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_members_clerk_user_id UNIQUE (clerk_user_id)
);

CREATE INDEX IF NOT EXISTS idx_members_clerk_user_id
    ON members (clerk_user_id);
