# ADR-018: Document Scope Representation

**Status**: Accepted

**Context**: Documents currently belong to exactly one Project (`project_id NOT NULL`). The platform needs to support three document scopes: **Org-level** (compliance files, policies), **Project-level** (matter working files), and **Customer-level** (ID docs, KYC forms). Customer-scoped documents may optionally be tied to a specific Project or exist as global Customer documents reusable across projects.

**Options Considered**:

1. **Separate tables per scope** — `org_documents`, `project_documents`, `customer_documents`, each with scope-appropriate foreign keys and identical file metadata columns.
   - Pros: Each table has only the FKs it needs; no nullable columns; clean schema.
   - Cons: Triplicates the table structure and all associated queries, repositories, services, and controllers. Cross-scope queries (e.g., "all documents I can see") require UNION across three tables. S3 presigned URL logic must handle three document types. Migration from the existing `documents` table is complex (move data, update FKs). Future scope additions require a new table each time.

2. **Single table with scope enum and nullable FKs** — Add a `scope` column (enum: `ORG`, `PROJECT`, `CUSTOMER`), make `project_id` nullable, add `customer_id` FK (nullable). Use a CHECK constraint to enforce valid FK combinations per scope.
   - Pros: Single table, single repository, single service. Cross-scope queries are simple WHERE clauses. Migration is additive (new columns + backfill existing rows as `scope = 'PROJECT'`). S3 presigned URL logic branches on scope. Hibernate `@Filter` and RLS work unchanged. Easy to add new scopes later (just extend the enum).
   - Cons: Nullable FKs can feel less "clean" than dedicated tables. CHECK constraint is the enforcement mechanism for valid combinations. Slightly wider rows (extra columns that are NULL for some scopes).

3. **Polymorphic inheritance (JPA `@Inheritance`)** — Base `Document` entity with `OrgDocument`, `ProjectDocument`, `CustomerDocument` subclasses, using `SINGLE_TABLE` or `JOINED` strategy.
   - Pros: Type-safe in Java; each subclass has only its relevant fields.
   - Cons: Hibernate inheritance adds query complexity (discriminator columns, type casting). `SINGLE_TABLE` is essentially Option 2 with more Java ceremony. `JOINED` adds unnecessary joins for every query. Complicates the existing `DocumentRepository` and `DocumentService`.

**Decision**: Single table with scope enum and nullable FKs (Option 2).

**Rationale**: The `documents` table already exists with production data. An additive migration (new columns + backfill) is the lowest-risk path. All three scopes share 90% of the same metadata (file name, content type, size, S3 key, status, uploaded_by, timestamps) — the only difference is which parent entity they reference. A single table keeps the repository layer simple and makes cross-scope queries trivial. The CHECK constraint ensures data integrity at the database level.

**Schema Changes**:

```sql
-- V11 tenant migration (after V9 customers + V10 customer_projects)
ALTER TABLE documents ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'PROJECT';
ALTER TABLE documents ADD COLUMN customer_id UUID REFERENCES customers(id) ON DELETE SET NULL;
ALTER TABLE documents ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';
ALTER TABLE documents ALTER COLUMN project_id DROP NOT NULL;

-- Backfill: all existing documents are project-scoped
-- (DEFAULT 'PROJECT' handles this for the scope column)

-- Enforce valid FK combinations per scope
ALTER TABLE documents ADD CONSTRAINT chk_document_scope CHECK (
  (scope = 'ORG'      AND project_id IS NULL AND customer_id IS NULL) OR
  (scope = 'PROJECT'  AND project_id IS NOT NULL) OR
  (scope = 'CUSTOMER' AND customer_id IS NOT NULL)
);
-- Note: PROJECT scope allows customer_id to be non-null (document about a customer within a project context)
-- CUSTOMER scope: project_id is optional (global customer doc vs project-specific customer doc)
```

**Visibility Column**: `visibility` (enum: `INTERNAL`, `SHARED`) is added now but only enforced when the customer portal is built. Default `INTERNAL` means all documents are staff-only. Staff can mark documents as `SHARED` to make them visible to linked Customers in the future portal.

**Consequences**:
- `documents.project_id` becomes nullable — existing code that assumes non-null must be updated.
- New `scope` column indexed for filtered queries.
- `customer_id` FK with `ON DELETE SET NULL` — deleting a Customer orphans their documents rather than cascading deletion (preserves audit trail).
- `visibility` column is forward-compatible for the customer portal — no schema changes needed later.
- S3 key structure extended: `org/{orgId}/org-docs/{docId}` for org scope, `org/{orgId}/customer/{customerId}/{docId}` for customer scope.
- `DocumentService` gains scope-aware query methods and upload-init variants per scope.
