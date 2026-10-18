-- Project templates carry a work type (e.g. the legal-za pack's matterType) that matters created
-- from the template inherit when the caller does not supply one. Mirrors projects.work_type.
ALTER TABLE project_templates ADD COLUMN IF NOT EXISTS work_type VARCHAR(50);
