# ADR-048: Invoice Numbering Strategy

**Status**: Accepted

**Context**: Invoices need human-readable sequential numbers for identification, communication with customers, and accounting purposes. The numbering scheme must work across both tenancy models (dedicated schema for Pro orgs, shared `tenant_shared` schema for Starter orgs) and produce gap-free sequences that accountants expect.

Key constraints:
- Invoice numbers must be unique per tenant (not globally).
- Numbers should be sequential and gap-free — gaps raise audit red flags in accounting.
- Numbers must be assigned atomically to prevent duplicates under concurrent requests.
- Draft invoices that are deleted before approval should not consume a number (otherwise, gaps appear).

**Options Considered**:

1. **PostgreSQL sequence per tenant** — Create a dedicated database sequence (e.g., `invoice_seq_<tenantId>`) for each tenant. Use `nextval()` to generate numbers.
   - Pros:
     - Native PostgreSQL feature, very fast, atomic by design.
     - No table-level locking needed.
   - Cons:
     - Sequences are DDL objects — creating one per tenant requires `CREATE SEQUENCE` at tenant provisioning time. This adds provisioning complexity.
     - Sequences are not transactional: if a transaction rolls back after calling `nextval()`, the number is consumed, creating a gap. This violates the gap-free requirement.
     - For shared-schema tenants, managing per-tenant sequences within a single schema is cumbersome (naming conventions, cleanup).
     - Sequences cannot be easily reset or inspected via standard JPA/Hibernate.

2. **Counter table with pessimistic locking** — A single `invoice_number_seq` table with one row per tenant. Use `SELECT next_value FROM invoice_number_seq WHERE tenant_id = :id FOR UPDATE` to atomically read and increment.
   - Pros:
     - Gap-free: the counter only increments when the transaction commits (approval transaction). If the transaction rolls back, the counter is not incremented.
     - Works identically in dedicated and shared schemas.
     - Simple to implement — no DDL at provisioning time. The row is lazily created on first invoice approval.
     - Easy to inspect, reset, or adjust via standard SQL.
     - The `FOR UPDATE` lock is held only for the duration of the approval transaction — a low-frequency operation.
   - Cons:
     - `FOR UPDATE` serializes concurrent approvals for the same tenant. Two admins approving invoices simultaneously will be serialized.
     - Slightly more overhead than a raw sequence (row-level lock vs. sequence lock).
     - Requires a separate table that is not a standard JPA entity (accessed via native SQL).

3. **Application-layer counter with optimistic locking** — Store `nextInvoiceNumber` as a field on the `OrgSettings` entity. Use `@Version` to detect concurrent updates and retry.
   - Pros:
     - No extra table — reuses existing `OrgSettings` entity.
     - Standard JPA optimistic locking.
   - Cons:
     - Contaminates `OrgSettings` with invoicing concerns. Every settings update would increment the version, potentially conflicting with invoice number generation.
     - Optimistic locking requires retry logic — if two approvals collide, one fails and must retry. This complicates the approval flow.
     - `OrgSettings` uses `@Version` for its own purposes; mixing concerns creates coupling.
     - For gap-free numbering, the retry must re-read the counter, which adds complexity.

**Decision**: Counter table with pessimistic locking (Option 2).

**Rationale**: Invoice approval is a low-frequency operation — even a busy organization might approve 10-20 invoices per day. The brief serialization from `SELECT FOR UPDATE` is imperceptible at this volume. The gap-free guarantee is critical for accounting compliance, and the counter table provides this naturally through transaction semantics. The table is lazily initialized (first approval inserts the row), avoiding provisioning changes. The approach works identically for both dedicated-schema and shared-schema tenants.

Numbers are assigned at approval time (DRAFT → APPROVED transition), not at draft creation. This ensures that deleted drafts do not consume numbers. Drafts display a temporary reference (`DRAFT-{uuid-short}`) in the UI.

The format is `INV-NNNN` with zero-padded 4 digits. Numbers exceeding 9999 expand naturally (`INV-10000`). The `INV-` prefix is hardcoded for v1; future enhancement: configurable prefix per org in `OrgSettings`.

**Consequences**:
- A new `invoice_number_seq` table is added in V22 migration, for both tenant and shared schemas.
- The table is accessed via native SQL in `InvoiceNumberService`, not through JPA repositories.
- Concurrent invoice approvals for the same tenant are serialized at the database level. This is acceptable for the expected approval volume.
- The `invoiceNumber` field on `Invoice` is nullable — drafts have `null` until approved.
- The unique index on `(tenant_id, invoice_number)` only covers non-null values (PostgreSQL skips NULLs in unique indexes), so multiple drafts can coexist.
- Future configurability (prefix, format, fiscal year reset) requires only changes to `InvoiceNumberService` and `OrgSettings` — no schema changes.
