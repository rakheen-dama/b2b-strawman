-- V4: Migrate projects.created_by and documents.uploaded_by from VARCHAR (Clerk user ID) to UUID FK → members(id)

-- Step 1: Backfill members from existing ownership columns (placeholder data for any not yet synced via webhook)
INSERT INTO members (clerk_user_id, email, name, org_role)
SELECT DISTINCT created_by, created_by || '@placeholder.internal', created_by, 'member'
FROM projects
WHERE created_by IS NOT NULL
ON CONFLICT (clerk_user_id) DO NOTHING;

INSERT INTO members (clerk_user_id, email, name, org_role)
SELECT DISTINCT uploaded_by, uploaded_by || '@placeholder.internal', uploaded_by, 'member'
FROM documents
WHERE uploaded_by IS NOT NULL
ON CONFLICT (clerk_user_id) DO NOTHING;

-- Step 2: Add temporary UUID columns
ALTER TABLE projects ADD COLUMN created_by_member_id UUID;
ALTER TABLE documents ADD COLUMN uploaded_by_member_id UUID;

-- Step 3: Populate temp columns via JOIN on members.clerk_user_id
UPDATE projects p
SET created_by_member_id = m.id
FROM members m
WHERE m.clerk_user_id = p.created_by;

UPDATE documents d
SET uploaded_by_member_id = m.id
FROM members m
WHERE m.clerk_user_id = d.uploaded_by;

-- Step 4: Drop old VARCHAR columns, rename temp columns
ALTER TABLE projects DROP COLUMN created_by;
ALTER TABLE projects RENAME COLUMN created_by_member_id TO created_by;
ALTER TABLE projects ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE documents DROP COLUMN uploaded_by;
ALTER TABLE documents RENAME COLUMN uploaded_by_member_id TO uploaded_by;
ALTER TABLE documents ALTER COLUMN uploaded_by SET NOT NULL;

-- Step 5: Add FK constraints
ALTER TABLE projects ADD CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES members(id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES members(id);
