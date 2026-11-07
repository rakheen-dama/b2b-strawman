-- G89-F26 / founder decision D12: the legal-za project template pack (v3) ended every template
-- description with "Matter type: <ENUM>" (e.g. "Matter type: LITIGATION"), and matters created
-- from those templates copied it into their own description. Pack v4 no longer ships the suffix;
-- the project-template seeder never re-applies an installed pack, so existing rows are fixed here.
-- Only a trailing "Matter type: " + upper-case enum is removed; a description that is nothing but
-- the suffix becomes NULL. Idempotent: rows without the suffix never match.
-- The portal read-model copy (portal.portal_projects.description) is fixed by global V5.

UPDATE project_templates
SET description = NULLIF(regexp_replace(description, '\s*Matter type: [A-Z_]+$', ''), '')
WHERE source = 'SEEDER'
  AND description ~ '\s*Matter type: [A-Z_]+$';

UPDATE projects
SET description = NULLIF(regexp_replace(description, '\s*Matter type: [A-Z_]+$', ''), '')
WHERE description ~ '\s*Matter type: [A-Z_]+$';
