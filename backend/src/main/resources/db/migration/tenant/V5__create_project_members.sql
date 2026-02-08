-- V5: Create project_members junction table and backfill project creators as leads

CREATE TABLE IF NOT EXISTS project_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    member_id    UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    project_role VARCHAR(50) NOT NULL,
    added_by     UUID REFERENCES members(id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_members_project_member UNIQUE (project_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_project_members_member_id
    ON project_members (member_id);

-- Enforce exactly one lead per project at the database level
CREATE UNIQUE INDEX IF NOT EXISTS idx_project_members_unique_lead
    ON project_members (project_id) WHERE project_role = 'lead';

-- Backfill: project creator becomes lead (added_by is NULL for backfilled rows)
INSERT INTO project_members (project_id, member_id, project_role)
SELECT id, created_by, 'lead'
FROM projects
ON CONFLICT (project_id, member_id) DO NOTHING;
