-- V73__create_field_date_notification_log.sql
-- Epic 360A: Field date scanner deduplication table

CREATE TABLE IF NOT EXISTS field_date_notification_log (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    entity_type     VARCHAR(20)  NOT NULL,
    entity_id       UUID         NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    days_until      INTEGER      NOT NULL,
    fired_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Deduplication index: prevent duplicate alerts for same entity/field/threshold
CREATE UNIQUE INDEX IF NOT EXISTS uq_field_date_notification_dedup
    ON field_date_notification_log (entity_type, entity_id, field_name, days_until);

-- Lookup index: find all notifications for an entity
CREATE INDEX IF NOT EXISTS idx_field_date_notification_entity
    ON field_date_notification_log (entity_type, entity_id);
