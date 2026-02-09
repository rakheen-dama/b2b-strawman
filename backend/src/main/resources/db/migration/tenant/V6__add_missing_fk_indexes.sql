-- V6: Add indexes on FK columns that were missing from V4

CREATE INDEX IF NOT EXISTS idx_projects_created_by
    ON projects (created_by);

CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by
    ON documents (uploaded_by);
