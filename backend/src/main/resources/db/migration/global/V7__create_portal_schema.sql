-- V7__create_portal_schema.sql

-- Create the portal schema for read-model entities
CREATE SCHEMA IF NOT EXISTS portal;

-- PortalProject: denormalized project view per customer
CREATE TABLE IF NOT EXISTS portal.portal_projects (
    id              UUID NOT NULL,
    org_id          VARCHAR(255) NOT NULL,
    customer_id     UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    description     TEXT,
    document_count  INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_portal_projects_org_customer
    ON portal.portal_projects(org_id, customer_id);

-- PortalDocument: shared-visibility documents
CREATE TABLE IF NOT EXISTS portal.portal_documents (
    id                  UUID PRIMARY KEY,
    org_id              VARCHAR(255) NOT NULL,
    customer_id         UUID,
    portal_project_id   UUID,
    title               VARCHAR(500) NOT NULL,
    content_type        VARCHAR(100),
    size                BIGINT,
    scope               VARCHAR(20) NOT NULL,
    s3_key              VARCHAR(1000) NOT NULL,
    uploaded_at         TIMESTAMP,
    synced_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_documents_project
    ON portal.portal_documents(portal_project_id);

CREATE INDEX IF NOT EXISTS idx_portal_documents_org_customer
    ON portal.portal_documents(org_id, customer_id);

-- PortalComment: customer-visible comments
CREATE TABLE IF NOT EXISTS portal.portal_comments (
    id                  UUID PRIMARY KEY,
    org_id              VARCHAR(255) NOT NULL,
    portal_project_id   UUID NOT NULL,
    author_name         VARCHAR(255) NOT NULL,
    content             TEXT NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    synced_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_comments_project
    ON portal.portal_comments(portal_project_id);

-- PortalProjectSummary: time/billing rollup stub
CREATE TABLE IF NOT EXISTS portal.portal_project_summaries (
    id              UUID NOT NULL,
    org_id          VARCHAR(255) NOT NULL,
    customer_id     UUID NOT NULL,
    total_hours     DECIMAL(10,2) NOT NULL DEFAULT 0,
    billable_hours  DECIMAL(10,2) NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMP,
    synced_at       TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (id, customer_id)
);
