CREATE TABLE IF NOT EXISTS documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    file_name     VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100),
    size          BIGINT NOT NULL,
    s3_key        VARCHAR(1000) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    uploaded_by   VARCHAR(255) NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_documents_project_id
    ON documents (project_id);

CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents (status);
