-- ============================================================================
-- backfill-portal-documents-from-soa.sql
-- ----------------------------------------------------------------------------
-- One-time data correction for the GAP-OBS-Day61 / E2.5 fix
-- ("publish DocumentGeneratedEvent on SoA generation").
--
-- Context:
--   Before this fix, StatementService.generate persisted the GeneratedDocument
--   + paired Document rows but bypassed DocumentService.confirmUpload — so
--   neither DocumentGeneratedEvent nor (for some pre-L-74 SoA rows)
--   DocumentCreatedEvent fired. As a result, portal.portal_documents may have
--   missing rows for SoA artefacts in dev/QA tenants.
--
--   This script reconciles the portal projection by upserting a portal_documents
--   row for every Statement of Account whose paired Document is portal-visible
--   (visibility IN ('SHARED','PORTAL')). It is idempotent — running it again
--   only refreshes synced_at on already-correct rows.
--
-- Usage:
--   Run per-tenant. The script uses the tenant's search_path so it sources
--   documents/generated_documents/projects from that tenant's schema while
--   writing to the global portal.portal_documents table.
--
--   psql "$DATABASE_URL" \
--     -v tenant_schema=tenant_xxx \
--     -v org_id=org_xxx \
--     -f tasks/backfill-portal-documents-from-soa.sql
--
--   Or, in a psql session:
--     SET search_path TO tenant_xxx;
--     -- (then paste the INSERT statement below; substitute :"org_id")
--
-- Scope:
--   - Only rows whose generated_documents.template_slug starts with
--     'statement-of-account' (covers system + cloned variants).
--   - Only paired Documents in portal-visible state ('SHARED' or 'PORTAL').
--   - Fans out to every PortalContact-linked customer for the project, mirroring
--     PortalEventHandler.onDocumentCreated's loop over
--     readModelRepo.findCustomerIdsByProjectId(...).
-- ============================================================================

SET search_path TO :"tenant_schema", public;

INSERT INTO portal.portal_documents (
    id,
    org_id,
    customer_id,
    portal_project_id,
    title,
    content_type,
    size,
    scope,
    s3_key,
    uploaded_at,
    synced_at
)
SELECT
    d.id                    AS id,
    :'org_id'               AS org_id,
    pc.customer_id          AS customer_id,
    d.project_id            AS portal_project_id,
    d.file_name             AS title,
    d.content_type          AS content_type,
    d.size                  AS size,
    d.scope                 AS scope,
    d.s3_key                AS s3_key,
    d.uploaded_at           AS uploaded_at,
    now()                   AS synced_at
FROM documents d
JOIN generated_documents gd
    ON gd.document_id = d.id
JOIN document_templates dt
    ON dt.id = gd.template_id
JOIN projects p
    ON p.id = d.project_id
JOIN portal_contacts pc
    ON pc.customer_id = p.customer_id
WHERE
        dt.slug LIKE 'statement-of-account%'
    AND d.visibility IN ('SHARED', 'PORTAL')
    AND d.status = 'UPLOADED'
    AND d.scope = 'PROJECT'
ON CONFLICT (id) DO UPDATE
SET title        = EXCLUDED.title,
    content_type = EXCLUDED.content_type,
    size         = EXCLUDED.size,
    scope        = EXCLUDED.scope,
    s3_key       = EXCLUDED.s3_key,
    synced_at    = now();

-- Sanity check: how many rows now exist for SoA artefacts in this tenant.
SELECT count(*) AS soa_portal_document_rows
FROM portal.portal_documents pd
JOIN documents d ON d.id = pd.id
JOIN generated_documents gd ON gd.document_id = d.id
JOIN document_templates dt ON dt.id = gd.template_id
WHERE pd.org_id = :'org_id'
  AND dt.slug LIKE 'statement-of-account%';
