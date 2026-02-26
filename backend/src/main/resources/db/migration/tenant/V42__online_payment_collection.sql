-- V42__online_payment_collection.sql
-- Extends Invoice entity with payment link fields
-- Creates PaymentEvent entity for payment tracking

-- 1. Add payment columns to invoices table
ALTER TABLE invoices
    ADD COLUMN payment_session_id VARCHAR(255),
    ADD COLUMN payment_url VARCHAR(1024),
    ADD COLUMN payment_destination VARCHAR(50) NOT NULL DEFAULT 'OPERATING';

-- 2. Create payment_events table
CREATE TABLE payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    provider_slug VARCHAR(50) NOT NULL,
    session_id VARCHAR(255),
    payment_reference VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_destination VARCHAR(50) NOT NULL DEFAULT 'OPERATING',
    provider_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Indexes on payment_events
CREATE INDEX idx_payment_events_invoice_id ON payment_events (invoice_id);
CREATE INDEX idx_payment_events_session_id ON payment_events (session_id);
CREATE INDEX idx_payment_events_status ON payment_events (status, created_at);

-- 4. Partial index on invoice payment_session_id
CREATE INDEX idx_invoices_payment_session_id ON invoices (payment_session_id)
    WHERE payment_session_id IS NOT NULL;
