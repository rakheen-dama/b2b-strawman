-- Phase 67, Epic 489B — add optimistic-lock `version` column on projects so two
-- concurrent matter closures (or any concurrent lifecycle transition) cannot
-- both commit and duplicate downstream effects (closure log rows, notifications,
-- audit events). Hibernate's `@Version` on Project.version drives this column.
--
-- Idempotent: safe to re-run / run against a schema where it already exists.
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
