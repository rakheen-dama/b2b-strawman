-- V141__drop_schedules.sql
-- Phase 86 (Epic 600A, ADR-331): remove the Phase 16/30 recurring project schedules — the
-- recurring_schedules / schedule_executions tables and the schedule-pack status column. The
-- Settings → Time Tracking reminder (TimeReminderScheduler/TimeReminderHandler) is re-homed to
-- timeentry/ in the same PR and owns no schema. Throwaway migration — squashed into tenant V1 at
-- zero tenants (ADR-331); no production data exists.
--
-- No enum-string row cleanup is needed (595B §4): the removed notification types
-- (RECURRING_PROJECT_CREATED, SCHEDULE_SKIPPED, SCHEDULE_COMPLETED, POST_CREATE_ACTION_FAILED),
-- the recurring_schedule_execute job type and the schedule.* audit types are free strings with no
-- CHECK constraint and no @Enumerated mapping; no capability, module id or pack_install value is
-- schedule-owned (pack_installs does not exist — the table is pack_install and never holds
-- 'schedule').

-- 1. Tables (V30:56-100; child first). schedule_executions.schedule_id -> recurring_schedules is
--    the only remaining inbound FK (retainer_agreements.schedule_id went with V138).
--    post_create_actions (V81:42-43) is a recurring_schedules column and goes with the table
--    (Epic 600B.2 = n/a). uq_schedule_template_customer_freq, chk_lead_time_non_negative,
--    chk_end_after_start, chk_schedule_status, chk_schedule_frequency,
--    uq_execution_schedule_period and all idx_recurring_schedules_* go with the tables.
DROP TABLE IF EXISTS schedule_executions;
DROP TABLE IF EXISTS recurring_schedules;

-- 2. Pack-status column (V81:51; PackStatusSettings.schedulePackStatus removed in 600A.4).
--    OrgSettingsSchemaSnapshotTest pin updated in the same PR (§11.7(f)).
ALTER TABLE org_settings DROP COLUMN IF EXISTS schedule_pack_status;
