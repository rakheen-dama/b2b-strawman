-- V29__drop_payfast_subscription.sql
-- Phase 86 (Epic 602A, ADR-335 / ADR-331): remove the PayFast platform subscription path. Manual
-- firm-level billing is the only tenant billing model; the tenant invoice pay-now gateway
-- (integration.payment) is unaffected. Throwaway migration — squashed into global V1 (ADR-331).
-- Number: next free at merge (spec said V28; V28 was taken by 598B).

-- 1. Rewrite rows that map to the deleted BillingMethod.PAYFAST constant BEFORE dropping columns
--    (global V18:9-10 back-filled 'PAYFAST' where payfast_token IS NOT NULL). Without this,
--    @Enumerated(STRING) fails to load the row once the constant is gone.
UPDATE subscriptions SET billing_method = 'MANUAL' WHERE billing_method = 'PAYFAST';

-- 2. ITN payment audit trail (global V17:36-50); idx_sub_payments_* go with the table.
DROP TABLE IF EXISTS subscription_payments;

-- 3. PayFast lifecycle columns (global V17:6,9-13). currency is orphaned too: the surviving price
--    display reads heykazi.billing.currency, not this column.
ALTER TABLE subscriptions
    DROP COLUMN IF EXISTS payfast_token,
    DROP COLUMN IF EXISTS payfast_payment_id,
    DROP COLUMN IF EXISTS monthly_amount_cents,
    DROP COLUMN IF EXISTS currency,
    DROP COLUMN IF EXISTS last_payment_at,
    DROP COLUMN IF EXISTS next_billing_at;

-- 4. billing_method has no CHECK constraint (V18:5); refresh its column comment (V18:16).
COMMENT ON COLUMN subscriptions.billing_method IS 'Payment method: DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL';
