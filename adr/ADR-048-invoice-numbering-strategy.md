# ADR-048: Invoice Numbering Strategy

**Status**: Accepted

**Context**: Phase 10 introduces invoices with human-readable sequential numbers (e.g., "INV-0001"). Invoice numbers serve two purposes: external communication with clients (printed on invoices, referenced in correspondence) and internal record-keeping (sequential order implies chronological audit trail). The numbering strategy must satisfy several constraints:

1. **Tenant isolation**: Numbers must be sequential *within* a tenant, not globally. Two tenants can independently have "INV-0001" without conflict.
2. **Gap-free appearance**: Business users expect sequential invoice numbers without gaps. Regulatory contexts in some jurisdictions require gap-free numbering for tax compliance.
3. **Draft lifecycle**: Invoices start as drafts that may be deleted before finalization. If numbers were assigned at draft creation, deletions would create visible gaps.
4. **Multi-tenant schema model**: Pro tenants have dedicated schemas; Starter tenants share `tenant_shared`. The solution must work for both models.
5. **Concurrency**: Multiple users within an org may approve invoices simultaneously; the numbering mechanism must prevent duplicates.

**Options Considered**:

1. **PostgreSQL SEQUENCE per tenant** -- Create a dedicated database sequence for each tenant schema.
   - Pros:
     - Native PostgreSQL concurrency control (atomic `nextval()`).
     - Extremely fast -- no row locking or table contention.
     - Well-understood semantics.
   - Cons:
     - Sequences cannot be shared across tenants in `tenant_shared` -- would need one sequence per Starter tenant, dynamically named (e.g., `invoice_seq_{tenant_id}`), which is complex to manage.
     - Sequences are gap-prone by design (`nextval()` does not roll back with the transaction). Deleted drafts or failed approval transactions leave gaps.
     - Cannot easily be reset, reformatted, or prefixed without DDL changes.
     - Dynamic sequence creation for Starter tenants adds provisioning complexity.

2. **Counter table with row-level locking** -- A dedicated `invoice_counters` table with one row per tenant. The counter is incremented inside the approval transaction using `SELECT ... FOR UPDATE`.
   - Pros:
     - Works identically for both Pro (row per schema, `tenant_id` = NULL) and Starter (row per `tenant_id` value in `tenant_shared`) tenants.
     - Transactional: if the approval transaction rolls back, the counter is not incremented -- gap-free by default.
     - Counter value is a plain integer, easily formatted with any prefix at the application layer.
     - No dynamic DDL required -- table is created once per schema in the V23 migration.
     - Future-proof: adding configurable prefixes, date-based numbering, or format strings requires only application logic changes, not schema changes.
   - Cons:
     - Row-level lock during approval creates a serialization point. Concurrent approvals for the same tenant wait on the lock. Acceptable because invoice approval is infrequent (perhaps a few per day per org).
     - Slightly more complex than a bare `nextval()` call.

3. **MAX(invoice_number) + 1 query** -- Compute the next number by querying the maximum existing invoice number at approval time.
   - Pros:
     - No separate counter table needed.
     - Simple to understand.
   - Cons:
     - Race condition under concurrent approvals (two transactions read the same MAX, both insert the same number). Requires a unique index violation to detect and retry.
     - Parsing the integer from formatted strings (e.g., "INV-0042" → 42) adds fragility.
     - Full table scan or index scan on every approval, scaling with invoice count.
     - Voided invoices retain their numbers (by design), so MAX always reflects the true high-water mark, but the query is still heavier than a counter lookup.

**Decision**: Counter table with row-level locking (Option 2).

**Rationale**: The counter table approach is the best fit for DocTeams' multi-tenant model. It works uniformly across dedicated and shared schemas without dynamic DDL. The transactional semantics naturally prevent gaps: the counter only advances when the approval transaction commits. The serialization cost is negligible -- invoice approval is a low-frequency operation (single-digit per day per org, at most), so the `SELECT ... FOR UPDATE` lock is held for milliseconds.

The counter row is lazily initialized: the first approval for a tenant creates the counter row with value 1 (using `INSERT ... ON CONFLICT DO UPDATE SET next_number = invoice_counters.next_number + 1 RETURNING next_number`). This avoids the need for a provisioning step to seed counter rows.

Invoice numbers are assigned **at approval time, not at draft creation**. Drafts display a temporary reference ("DRAFT-{short-uuid}") in the UI. This ensures that deleted drafts never consume a number, and the sequential numbers reflect the chronological order of finalized invoices.

The `INV-` prefix is hardcoded in v1. The formatting logic (`String.format("INV-%04d", counter)`) lives in `InvoiceNumberService`, making it trivial to add configurable prefixes (stored in `OrgSettings`) in a future phase.

**Consequences**:
- A new `invoice_counters` table is created in V23 with columns `(id UUID PK, tenant_id VARCHAR, next_number INTEGER DEFAULT 1)`.
- `InvoiceNumberService` encapsulates all numbering logic: counter increment, formatting, and future prefix configuration.
- Invoice numbers are unique per tenant, enforced by a unique index on `invoices(invoice_number)` (per-schema for Pro) and `invoices(tenant_id, invoice_number)` (for Starter in `tenant_shared`).
- Concurrent approval within the same tenant serializes on the counter row. At expected volumes (< 10 approvals/day/org), this is not a bottleneck.
- Future configurability (custom prefixes, date-based numbering, starting number) requires only changes to `InvoiceNumberService` and `OrgSettings`, not schema changes.
- Voided invoices retain their number permanently. The counter never decrements.
