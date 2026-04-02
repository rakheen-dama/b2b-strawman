-- V17__subscription_payments.sql
-- Restructure subscriptions table for PayFast lifecycle model and create subscription_payments audit trail.

-- 1. Add new lifecycle columns to subscriptions (all nullable initially for data migration)
ALTER TABLE subscriptions ADD COLUMN subscription_status VARCHAR(30);
ALTER TABLE subscriptions ADD COLUMN payfast_token VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN trial_ends_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN grace_ends_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN monthly_amount_cents INTEGER;
ALTER TABLE subscriptions ADD COLUMN currency VARCHAR(3) DEFAULT 'ZAR';
ALTER TABLE subscriptions ADD COLUMN last_payment_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN next_billing_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN payfast_payment_id VARCHAR(255);

-- 2. Migrate existing data based on plan_slug
UPDATE subscriptions SET subscription_status = 'ACTIVE' WHERE plan_slug = 'pro';
UPDATE subscriptions SET subscription_status = 'TRIALING', trial_ends_at = now() + INTERVAL '14 days'
    WHERE plan_slug = 'starter' OR plan_slug IS NULL;

-- Defensive catch-all: any remaining NULL subscription_status
UPDATE subscriptions SET subscription_status = 'TRIALING', trial_ends_at = now() + INTERVAL '14 days'
    WHERE subscription_status IS NULL;

-- 3. Set NOT NULL constraint after data migration
ALTER TABLE subscriptions ALTER COLUMN subscription_status SET NOT NULL;

-- 4. Drop old columns from subscriptions
ALTER TABLE subscriptions DROP COLUMN IF EXISTS plan_slug;
ALTER TABLE subscriptions DROP COLUMN IF EXISTS status;

-- 5. Drop old columns from organizations
ALTER TABLE organizations DROP COLUMN IF EXISTS tier;
ALTER TABLE organizations DROP COLUMN IF EXISTS plan_slug;

-- 6. Create subscription_payments table
CREATE TABLE IF NOT EXISTS subscription_payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id     UUID NOT NULL REFERENCES subscriptions(id),
    payfast_payment_id  VARCHAR(255) NOT NULL,
    amount_cents        INTEGER NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    status              VARCHAR(30) NOT NULL,
    payment_date        TIMESTAMPTZ NOT NULL,
    raw_itn             JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Create indexes
CREATE INDEX IF NOT EXISTS idx_sub_payments_subscription ON subscription_payments(subscription_id);
CREATE INDEX IF NOT EXISTS idx_sub_payments_payfast_id ON subscription_payments(payfast_payment_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(subscription_status);
