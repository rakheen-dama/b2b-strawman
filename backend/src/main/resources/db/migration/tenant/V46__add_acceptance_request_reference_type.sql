-- Add ACCEPTANCE_REQUEST to the email_delivery_log reference_type check constraint
ALTER TABLE email_delivery_log
    DROP CONSTRAINT chk_email_delivery_reference_type;

ALTER TABLE email_delivery_log
    ADD CONSTRAINT chk_email_delivery_reference_type
    CHECK (reference_type IN ('NOTIFICATION', 'INVOICE', 'MAGIC_LINK', 'TEST', 'ACCEPTANCE_REQUEST'));
