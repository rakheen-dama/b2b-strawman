-- V41__create_email_delivery_log.sql

CREATE TABLE email_delivery_log (
    id              UUID            DEFAULT gen_random_uuid() PRIMARY KEY,
    recipient_email VARCHAR(320)    NOT NULL,
    template_name   VARCHAR(100)    NOT NULL,
    reference_type  VARCHAR(30)     NOT NULL,
    reference_id    UUID,
    status          VARCHAR(20)     NOT NULL,
    provider_message_id VARCHAR(200),
    provider_slug   VARCHAR(50)     NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Webhook lookup by provider message ID
CREATE INDEX idx_email_delivery_log_provider_message_id
    ON email_delivery_log (provider_message_id)
    WHERE provider_message_id IS NOT NULL;

-- Find deliveries for a specific business entity
CREATE INDEX idx_email_delivery_log_reference
    ON email_delivery_log (reference_type, reference_id);

-- Admin dashboard: filter by status and date range
CREATE INDEX idx_email_delivery_log_status_created
    ON email_delivery_log (status, created_at);

-- Time-range queries for rate limit stats
CREATE INDEX idx_email_delivery_log_created_at
    ON email_delivery_log (created_at);

-- CHECK: valid status values
ALTER TABLE email_delivery_log
    ADD CONSTRAINT chk_email_delivery_status
    CHECK (status IN ('SENT', 'DELIVERED', 'BOUNCED', 'FAILED', 'RATE_LIMITED'));

-- CHECK: valid reference types
ALTER TABLE email_delivery_log
    ADD CONSTRAINT chk_email_delivery_reference_type
    CHECK (reference_type IN ('NOTIFICATION', 'INVOICE', 'MAGIC_LINK', 'TEST'));
