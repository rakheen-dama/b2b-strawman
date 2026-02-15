# ADR-057: Template Storage

**Status**: Accepted

**Context**: Document templates contain Thymeleaf HTML content (typically 5-50KB per template), optional custom CSS, and metadata (name, category, entity type). The storage location affects tenant isolation, deployment model, and operational complexity. The system must support both Pro tenants (dedicated schemas) and Starter tenants (shared schema with `tenant_id` column), and must work in a containerized multi-instance deployment on ECS/Fargate where instances share no local filesystem.

The template content must be tenant-scoped — each org has its own set of templates (including cloned platform templates with org-specific modifications). The storage solution must support transactional consistency: when a template is created or updated, the metadata and content must be committed atomically.

**Options Considered**:

1. **Database (TEXT column in PostgreSQL)** — Store template content and CSS as `TEXT` columns alongside template metadata in the `document_templates` table.
   - Pros:
     - Tenant isolation is automatic — the same `@FilterDef`/`@Filter` and RLS policies that protect all other entities protect templates. No separate access control layer needed.
     - Transactional consistency — metadata and content are updated atomically in a single transaction.
     - No additional infrastructure — PostgreSQL is already the primary data store.
     - Works identically for Pro (dedicated schema) and Starter (shared schema) tenants.
     - Simple CRUD operations — standard JPA `save()` and `findById()`.
     - Backup and restore included in database backup strategy.
     - No deployment coupling — templates travel with the database, not the container image.
   - Cons:
     - Templates stored as TEXT occupy database storage. At 50KB per template, 100 templates per tenant, 1,000 tenants = ~5GB. This is trivial for PostgreSQL.
     - Large TEXT columns can affect query performance if included in list queries. Mitigation: exclude `content` and `css` from list projections (select only metadata fields).
     - No CDN caching or edge delivery — but templates are only accessed server-side (by the rendering pipeline), never by browsers directly.

2. **S3 (object storage)** — Store template content as objects in S3, with the S3 key referenced from the database metadata record.
   - Pros:
     - Effectively unlimited storage — well-suited for very large templates (if they existed).
     - Could serve template content directly to browsers (for future WYSIWYG editor) via pre-signed URLs.
     - Separates "hot" metadata (frequently queried) from "cold" content (loaded only at render time).
   - Cons:
     - Breaks transactional consistency — database metadata and S3 content are updated in separate operations. If one fails, the template is in an inconsistent state. Requires compensation logic (delete S3 object if DB insert fails, etc.).
     - Adds latency to every render — S3 GetObject adds 20-100ms per template load (vs. ~1ms for a database read on the same host).
     - Tenant isolation must be enforced via S3 key naming conventions and IAM policies — separate from the database tenant model.
     - Two infrastructure dependencies instead of one for template CRUD.
     - S3 versioning would need to be managed separately from database versioning.
     - Operational overhead: S3 bucket policies, lifecycle rules, cross-region replication if needed.

3. **Filesystem (classpath or mounted volume)** — Store template content as files on the filesystem, either as classpath resources (compiled into the JAR) or on a mounted volume.
   - Pros:
     - Simplest for read-only platform templates (already used for invoice preview in Phase 10).
     - Fast I/O — no network calls.
     - Template editing tools (IDEs) work directly with files.
   - Cons:
     - Not viable for tenant-customized templates — each container instance would need to write to a shared filesystem.
     - No tenant isolation at the filesystem level — all instances share the same files.
     - Not compatible with ECS/Fargate deployment where containers are ephemeral and share no local state.
     - Requires a shared filesystem (EFS/NFS) for multi-instance deployment — adds infrastructure complexity and cost.
     - No transactional guarantees — file writes are not atomic with database metadata updates.
     - Starter/Pro tenant model cannot be mapped to filesystem paths.
     - Would work for platform templates only — org-custom templates would need a different storage mechanism, creating two code paths.

**Decision**: Database storage via TEXT columns (Option 1).

**Rationale**: Database storage aligns perfectly with the existing tenant isolation model. Every tenant-scoped entity in DocTeams lives in PostgreSQL — projects, customers, invoices, custom field definitions, tags, and now templates. Adding templates to the database means they automatically benefit from `@FilterDef`/`@Filter` (Hibernate), RLS policies (PostgreSQL), schema isolation (Pro tenants), and the established backup/restore strategy. There is no new infrastructure to deploy, monitor, or secure.

The storage overhead is negligible. Templates are small (5-50KB of HTML + CSS). Even at aggressive scale (1,000 tenants x 100 templates x 50KB), the total is ~5GB — a trivial amount for PostgreSQL. The `content` and `css` columns are excluded from list queries (metadata-only projections), so the large TEXT values do not affect query performance for the template management UI.

S3 (Option 2) would add latency and break transactional consistency for no meaningful benefit at this scale. The "unlimited storage" advantage is irrelevant — template sizes are bounded by what a human can author in HTML. Filesystem (Option 3) is architecturally incompatible with the containerized deployment model and the multi-tenant data model.

**Consequences**:
- `content` (TEXT, NOT NULL) and `css` (TEXT, nullable) columns on the `document_templates` table.
- List endpoints (`GET /api/templates`) return metadata-only projections — `content` and `css` are excluded. Use `GET /api/templates/{id}` for full content.
- Template content is subject to PostgreSQL's TEXT size limit (effectively unlimited — up to 1GB per value). No practical constraint for HTML templates.
- Database migrations and backups include template content — no separate backup strategy needed.
- Platform seed templates are read from classpath (JSON + HTML files in `template-packs/`) at provisioning time and copied into the database as tenant-scoped records. The classpath files are read once during seeding, not at render time.
- Future consideration: if a WYSIWYG template editor is added, the editor loads content from the API (database), not from the filesystem.
