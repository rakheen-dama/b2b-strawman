-- V82__seeder_nullable_fk_columns.sql
-- Epic 384A: Allow seeded recurring schedules and billing rates without FK targets.
--
-- 1. recurring_schedules.customer_id: Template schedules created by pack seeders have
--    customer_id = NULL until the tenant assigns a customer and activates the schedule.
--
-- 2. billing_rates.member_id: Seeded rate tiers (Partner, Manager, etc.) represent role-based
--    defaults, not rates for specific members. member_id is NULL for these seeded entries.

-- Make customer_id nullable on recurring_schedules
ALTER TABLE recurring_schedules
    ALTER COLUMN customer_id DROP NOT NULL;

-- The existing unique constraint uq_schedule_template_customer_freq includes customer_id.
-- With nullable customer_id, multiple seeded schedules for the same template+frequency
-- are allowed (NULL != NULL in SQL unique constraints), which is the desired behavior.

-- Make member_id nullable on billing_rates
ALTER TABLE billing_rates
    ALTER COLUMN member_id DROP NOT NULL;
