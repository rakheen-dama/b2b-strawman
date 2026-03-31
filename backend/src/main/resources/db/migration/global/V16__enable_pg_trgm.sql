-- V16__enable_pg_trgm.sql
-- Enable pg_trgm extension for fuzzy name matching (used by conflict check in Phase 55)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
