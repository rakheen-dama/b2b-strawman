-- Project templates carry a work type (e.g. the legal-za pack's matterType) that matters created
-- from the template inherit when the caller does not supply one. Mirrors projects.work_type.
ALTER TABLE project_templates ADD COLUMN IF NOT EXISTS work_type VARCHAR(50);

-- Backfill templates seeded from the legal-za pack before this column existed: the pack seeder
-- never re-applies an installed pack, so they would otherwise keep NULL. Names and work types
-- mirror project-template-packs/legal-za.json (name -> matterType). Idempotent: only NULL rows.
UPDATE project_templates pt
SET work_type = v.work_type
FROM (VALUES
    ('Litigation (Road Accident Fund -- RAF)', 'LITIGATION'),
    ('Litigation (Personal Injury / General)', 'LITIGATION'),
    ('Deceased Estate Administration', 'ESTATES'),
    ('Collections (Debt Recovery)', 'COLLECTIONS'),
    ('Commercial (Corporate & Contract)', 'COMMERCIAL'),
    ('Property Transfer (Conveyancing)', 'CONVEYANCING')
) AS v(name, work_type)
WHERE pt.name = v.name
  AND pt.source = 'SEEDER'
  AND pt.work_type IS NULL;
