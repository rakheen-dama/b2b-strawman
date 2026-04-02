-- V18__subscription_billing_method.sql
-- Add billing_method dimension and admin_note to subscriptions table.

-- 1. Add new columns
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS billing_method VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS admin_note TEXT;

-- 2. Data migration: mark PayFast-linked subscriptions
UPDATE subscriptions SET billing_method = 'PAYFAST'
    WHERE payfast_token IS NOT NULL AND billing_method = 'MANUAL';

-- 3. Create index on billing_method for filtered queries
CREATE INDEX IF NOT EXISTS idx_subscriptions_billing_method ON subscriptions(billing_method);

-- 4. Column documentation
COMMENT ON COLUMN subscriptions.billing_method IS 'Payment method: PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL';
COMMENT ON COLUMN subscriptions.admin_note IS 'Admin-only note explaining non-standard billing arrangements';
