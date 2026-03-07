-- V62__create_resource_planning_tables.sql
-- Phase 38: Resource Planning & Capacity

-- ─── Member Capacities ───
CREATE TABLE IF NOT EXISTS member_capacities (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id      UUID         NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    weekly_hours   NUMERIC(5,2) NOT NULL CHECK (weekly_hours > 0 AND weekly_hours <= 168),
    effective_from DATE         NOT NULL CHECK (EXTRACT(ISODOW FROM effective_from) = 1),
    effective_to   DATE,
    note           VARCHAR(500),
    created_by     UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Capacity resolution: find the latest applicable record for a member + date
CREATE INDEX idx_member_capacities_member_effective
    ON member_capacities (member_id, effective_from DESC);

COMMENT ON TABLE member_capacities IS 'Configurable weekly capacity per member with effective date ranges';


-- ─── Resource Allocations ───
CREATE TABLE IF NOT EXISTS resource_allocations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       UUID         NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    week_start      DATE         NOT NULL CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    allocated_hours NUMERIC(5,2) NOT NULL CHECK (allocated_hours > 0 AND allocated_hours <= 168),
    note            VARCHAR(500),
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_allocation_member_project_week
        UNIQUE (member_id, project_id, week_start)
);

-- Grid view: all allocations for a member in a date range
CREATE INDEX idx_allocations_member_week
    ON resource_allocations (member_id, week_start);

-- Project staffing view: all allocations for a project in a date range
CREATE INDEX idx_allocations_project_week
    ON resource_allocations (project_id, week_start);

-- Team grid: all allocations in a date range (no member filter)
CREATE INDEX idx_allocations_week
    ON resource_allocations (week_start);

COMMENT ON TABLE resource_allocations IS 'Planned hours per member per project per week (ISO Monday start)';


-- ─── Leave Blocks ───
CREATE TABLE IF NOT EXISTS leave_blocks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id  UUID        NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL CHECK (end_date >= start_date),
    note       VARCHAR(500),
    created_by UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Capacity reduction: find leave blocks overlapping a date range
CREATE INDEX idx_leave_blocks_member_dates
    ON leave_blocks (member_id, start_date, end_date);

COMMENT ON TABLE leave_blocks IS 'Date-range unavailability markers that reduce effective capacity';


-- ─── OrgSettings Extension ───
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS default_weekly_capacity_hours NUMERIC(5,2) DEFAULT 40.00;

COMMENT ON COLUMN org_settings.default_weekly_capacity_hours
    IS 'Org-wide default weekly capacity when no MemberCapacity record exists for a member';
