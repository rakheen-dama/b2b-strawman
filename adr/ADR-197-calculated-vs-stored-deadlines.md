# ADR-197: Calculated vs. Stored Regulatory Deadlines

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 51 (Accounting Practice Management Essentials)

## Context

Phase 51 introduces a regulatory deadline calendar showing all filing deadlines across all clients. Each client's deadlines are determined by their `financial_year_end` custom field combined with regulatory rules (e.g., SARS provisional tax is due 6 months after FYE). The question is whether these calculated deadline dates should be stored as entities in the database or computed on-the-fly each time the calendar is loaded.

The platform already has a custom field system (Phase 11/23) where `financial_year_end` is a DATE-type field on customers, and an automation engine (Phase 37/48) where `FieldDateScannerJob` fires notifications for approaching date-type fields. Neither of these store pre-computed derivative values — the scanner reads the field values directly.

## Options Considered

### Option 1: Store Deadlines as Entities (Pre-Populate)

Create a `RegulatoryDeadline` entity per client per deadline type per period. When a client's FYE is set or changed, generate all applicable deadline records for the next N years.

- **Pros:**
  - Simple queries: `SELECT * FROM regulatory_deadlines WHERE due_date BETWEEN ? AND ? AND status = 'pending'`
  - Can add client-specific overrides (e.g., extended deadline due to SARS arrangement) as column updates
  - Filing status and deadline due date are on the same entity — no cross-referencing needed
  - Pagination and sorting are trivial with database-level queries

- **Cons:**
  - **Synchronization problem:** If a client's FYE changes (rare but possible), all future deadline records must be recalculated and updated. Records that have been marked as "filed" must not be changed, while "pending" ones need recalculation — complex update logic
  - **Cardinality explosion:** 100 clients x 8 deadline types x 3 years forward = 2,400 rows to pre-generate. Every new client requires a batch insert. Every FYE change requires a batch recalculation
  - **Stale data risk:** If a deadline rule changes (e.g., SARS changes the provisional tax due date formula), all existing records contain the old due dates. A migration or batch update is needed
  - **Lifecycle complexity:** When should records be created? At client creation? At FYE field population? What about clients whose FYE is set after initial creation? What about clients who change from Pty Ltd to sole proprietor (changing which deadline types apply)?
  - **Redundant storage:** The due date is a deterministic function of FYE + deadline type. Storing it duplicates information that can always be derived

### Option 2: Calculate On-the-Fly from FYE + Rules (Selected)

Compute deadlines at query time. A `DeadlineCalculationService` reads each customer's `financial_year_end` custom field, applies each deadline type's calculation rule, and returns calculated deadline objects. Only the user-entered filing status (filed/not applicable) is stored.

- **Pros:**
  - **Always current:** If a client's FYE changes, the next calendar load immediately reflects the new deadlines. No synchronization, no stale data
  - **Rule changes are instant:** If SARS changes a deadline formula, update the `DeadlineTypeRegistry` code and all tenants immediately see correct dates. No data migration
  - **Minimal storage:** Only filing status records exist in the database — a few hundred rows even for large firms. No pre-population, no cleanup
  - **Applicability is dynamic:** If a client starts being VAT-registered (populates `vat_number`), VAT deadlines appear immediately on next load. No trigger or event handler needed
  - **Simple data model:** `FilingStatus` is a tiny tracking entity (like `FieldDateNotificationLog`). No lifecycle complexity, no cascading updates

- **Cons:**
  - Computation cost on every page load: iterate all clients x all deadline types. For 100 clients x 8 types, this is 800 calculations — fast in-memory, but grows linearly
  - Cannot do efficient database-level pagination or sorting — must calculate all deadlines in the range, then sort/filter in Java
  - Client-specific deadline overrides (e.g., "this client has an extension until date X") cannot be expressed — only the filed/not-applicable status is stored
  - No database-level full-text search across deadlines

### Option 3: Hybrid — Calculate and Cache in a Materialized View

Compute deadlines and store them in a materialized view (or cache table) that is refreshed periodically or on FYE change. Query the materialized data for calendar rendering.

- **Pros:**
  - Best of both: always accurate (via refresh), efficient queries (via materialized data)
  - Can add database-level indexes, pagination, and search

- **Cons:**
  - Materialized views in PostgreSQL are not per-schema — would need a different approach in the schema-per-tenant architecture
  - Adds operational complexity: refresh triggers, cache invalidation, staleness window
  - Overkill for v1 — the calculation is cheap and the firm size is small (most SA accounting firms have < 200 clients)

## Decision

**Option 2 — Calculate on-the-fly from FYE + rules.**

## Rationale

The core insight is that regulatory deadlines are a **derived property** of two inputs: the client's financial year-end and the regulatory rules. Storing derived values creates a synchronization problem that doesn't need to exist.

1. **FYE changes, while rare, do happen.** A Pty Ltd might change its financial year-end with CIPC approval. If deadlines are stored, this requires recalculating potentially dozens of records across multiple years — and distinguishing between "filed" records (which should not be recalculated) and "pending" records (which should). With on-the-fly calculation, the new FYE is automatically reflected on the next page load.

2. **Applicability is dynamic.** A client becomes VAT-registered when the staff member populates the `vat_number` custom field. With stored deadlines, this would require a trigger or listener to detect the field change and generate VAT deadline records. With calculation, the applicability rule simply checks for field presence — no event handling needed.

3. **The computation is cheap.** For a firm with 200 clients and 8 deadline types, the calculation involves:
   - One database query to load customers with FYE values (indexed on `lifecycle_status`)
   - One batch query to load filing statuses for the result set
   - ~1,600 date arithmetic operations in memory (trivial — sub-millisecond total)

   Even at 500 clients, this is well under 100ms. SA accounting firms in the target market (small to mid-size) rarely exceed 300 clients.

4. **Filing status is the only user-entered state.** The "pending" and "overdue" states are computed from the current date versus the due date — they are not user actions. Only "filed" and "not_applicable" are explicit user entries. Storing just these user-entered states in `FilingStatus` keeps the model honest: the database contains what the user told us, and the service computes everything else.

5. **Future migration path is clean.** If calculation performance becomes an issue at scale (unlikely for the target market), Option 3 (materialized cache) can be added later without changing the API contract. The `CalculatedDeadline` record returned by the service would simply come from a cache table instead of live computation. The controller and frontend are unaffected.

## Consequences

- **Positive:**
  - No synchronization bugs — deadlines are always consistent with current FYE and rules
  - Rule changes (legislative updates) take effect immediately by updating `DeadlineTypeRegistry`
  - `FilingStatus` is a simple, small entity — easy to reason about, easy to test
  - No cascading updates when client data changes
  - No pre-population logic, no cleanup jobs

- **Negative:**
  - Every calendar page load requires re-computation (mitigated by the low computation cost)
  - Cannot do database-level pagination — all deadlines in the range are calculated, then filtered/sorted in Java (acceptable for the expected data volumes)
  - Client-specific deadline extensions must be tracked via `notes` on filing status rather than as first-class date overrides (acceptable for v1)
  - No push-based cache invalidation — the calculation runs fresh on every request (for v1, this is simpler than managing a cache)

- **Neutral:**
  - The `DeadlineTypeRegistry` is static Java code, not database-driven. Adding new deadline types requires a code change and deployment. This is appropriate because deadline types are regulatory constants, not tenant-configurable data.
