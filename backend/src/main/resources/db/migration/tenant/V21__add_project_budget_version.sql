-- V21: Add optimistic locking version column to project_budgets
ALTER TABLE project_budgets ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
