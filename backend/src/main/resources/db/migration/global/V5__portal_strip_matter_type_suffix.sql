-- G89-F26 / founder decision D12: the portal read-model copy of a matter's description carried the
-- "Matter type: <ENUM>" suffix the legal-za project templates (pack v3) appended. Tenant V13 strips
-- it from project_templates / projects; this applies the same rule to portal.portal_projects so the
-- portal matter page stops printing it without a portal re-sync (the existing resync is a manual,
-- wipe-and-rebuild internal endpoint). Same regex as tenant V13; idempotent.

UPDATE portal.portal_projects
SET description = NULLIF(regexp_replace(description, '\s*Matter type: [A-Z_]+$', ''), '')
WHERE description ~ '\s*Matter type: [A-Z_]+$';
