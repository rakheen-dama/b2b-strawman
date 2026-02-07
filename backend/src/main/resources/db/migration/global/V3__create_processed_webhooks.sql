CREATE TABLE IF NOT EXISTS processed_webhooks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    svix_id        VARCHAR(255) NOT NULL UNIQUE,
    event_type     VARCHAR(100) NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_processed_webhooks_svix_id
    ON processed_webhooks (svix_id);
