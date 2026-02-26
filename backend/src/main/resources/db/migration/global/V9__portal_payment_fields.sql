-- V9: Add payment fields to portal invoice read-model
ALTER TABLE portal.portal_invoices
    ADD COLUMN IF NOT EXISTS payment_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS payment_session_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
