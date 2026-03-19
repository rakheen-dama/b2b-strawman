# ADR-199: Filing Status Lazy Creation

**Status**: Accepted
**Date**: 2026-03-19
**Phase**: 51 (Accounting Practice Management Essentials)

## Context

Phase 51's regulatory deadline calendar shows calculated deadlines for all clients, with a filing status overlay indicating whether each deadline has been completed. The `FilingStatus` entity records user-entered states: "filed" (with filing date, notes, and reference) or "not_applicable" (with reason).

The question is when and how `FilingStatus` records should be created. The calculated deadline dates are ephemeral — computed on-the-fly from the client's `financial_year_end` custom field ([ADR-197](ADR-197-calculated-vs-stored-deadlines.md)). But the filing status is user-entered data that must be persisted.

There are three possible creation strategies: pre-populate all possible filing statuses when a client's FYE is set, create them lazily when the user first changes a status, or batch-create them on the first calendar load for a given date range.

## Options Considered

### Option 1: Pre-Populate on FYE Set/Change

When a customer's `financial_year_end` custom field is set or updated, generate `FilingStatus` records for all applicable deadline types for the next N years (e.g., 3 years forward).

- **Pros:**
  - All filing status records exist in the database from the start — no "does this record exist?" check at query time
  - Can query filing statuses directly with standard SQL: `SELECT * FROM filing_statuses WHERE status = 'pending' AND due_date < now()`
  - The database is the single source of truth for both the deadline date and the filing status

- **Cons:**
  - **Cardinality explosion:** 200 clients x 8 deadline types x 3 years = 4,800 records. For a larger firm with 500 clients, that's 12,000 records — the vast majority with status "pending" (the default state that conveys no user-entered information)
  - **Synchronization on FYE change:** If a client's FYE changes, all "pending" records for future periods must be recalculated and regenerated. "Filed" records must be preserved. This is the same synchronization problem identified in [ADR-197](ADR-197-calculated-vs-stored-deadlines.md) but at the filing status level
  - **Trigger complexity:** Requires a listener or hook on the custom field system to detect when `financial_year_end` is set or changed. The custom field system (Phase 11/23) does not currently have per-field-slug change hooks — it would need new infrastructure
  - **Applicability changes require regeneration:** If a client becomes VAT-registered (populates `vat_number`), new VAT deadline filing status records must be generated. If VAT registration is removed, existing VAT filing statuses become orphaned
  - **Rolling horizon:** Every year, new filing status records must be generated for the next year. Requires a periodic job or on-demand generation when the calendar is viewed beyond the pre-populated range

### Option 2: Lazy Creation on First Status Change (Selected)

No `FilingStatus` records are pre-created. When `DeadlineCalculationService` computes deadlines and finds no matching filing status record, the deadline status defaults to "pending" (or "overdue" if past due). A `FilingStatus` record is created only when a user explicitly marks a deadline as "filed" or "not_applicable."

- **Pros:**
  - **Minimal storage:** The table contains only records where a human made a decision. For a firm with 200 clients, if 60% of deadlines are filed per year and the rest are pending, the table has ~960 records per year instead of 4,800
  - **No synchronization:** If a client's FYE changes, there is nothing to recalculate. The next calendar load computes new deadline dates, and existing filing statuses remain valid for their (customer_id, deadline_type_slug, period_key) combination. If the period_key no longer corresponds to a calculated deadline (because the FYE changed), the filing status simply has no matching deadline — it is historical data
  - **No triggers or hooks:** No need to detect FYE changes or applicability changes. The calculation is always fresh; filing statuses are always user-entered
  - **Unique constraint is natural:** `(customer_id, deadline_type_slug, period_key)` uniquely identifies a filing status record. Upserting on this constraint is straightforward
  - **"Pending" is the absence of a record, not a record with a value.** This is semantically correct: "pending" means "no one has told us anything about this filing." It is the default state of reality, not a user action.

- **Cons:**
  - **Cannot query "all pending deadlines" from the database alone:** To find pending deadlines, the service must calculate all deadlines, then subtract those with filing status records. This is a LEFT JOIN in application logic, not in SQL
  - **No database-level count of pending deadlines:** Aggregate queries like "how many pending deadlines this month" require computing all deadlines, then counting those without matching filing statuses
  - **Batch operations require upsert logic:** Marking 20 deadlines as "filed" requires 20 INSERT ... ON CONFLICT statements (or equivalent JPA logic). Each may be a new record or an update to an existing one

### Option 3: Batch Creation on First Calendar Load

When a user first loads the deadline calendar for a given date range, generate `FilingStatus` records for all calculated deadlines that don't have one yet. Subsequent loads simply query existing records.

- **Pros:**
  - Combines the query simplicity of Option 1 with the on-demand nature of Option 2
  - No triggers or hooks needed — records are created when they are needed (at display time)
  - Database queries can be used for filtering and aggregation after the initial batch creation

- **Cons:**
  - **First-load latency:** Generating hundreds of filing status records on the first calendar load creates a noticeable delay. The user sees a spinner while the system creates records they didn't ask for
  - **Write on read:** A GET request (calendar load) causes INSERT operations. This violates the principle that read operations should not have side effects. It also causes issues with read replicas, caching, and load balancers that expect GET requests to be idempotent
  - **Still has synchronization issues:** If FYE changes, the next calendar load needs to detect new deadline dates that don't have filing statuses and create them — while not creating duplicates for deadlines that already have records with the old due dates
  - **Storage growth:** Same as Option 1 — all deadlines get filing status records, even those where the user never makes a decision. The table grows to 4,800+ records per firm regardless of actual usage

## Decision

**Option 2 — Lazy creation on first status change.**

## Rationale

The design principle is: **the database should contain what humans told the system, not what the system can derive.** Deadline due dates are derived from FYE + rules. "Pending" is the default state of reality before any human decision. Only when a staff member explicitly marks a filing as "filed" or "not applicable" has new information been created that must be persisted.

1. **"Pending" is not data — it is the absence of data.** Creating a database record to say "no one has done anything about this yet" is like creating a to-do item that says "nothing has happened." The record exists solely to have a "pending" status, which could be inferred by the absence of the record. Lazy creation respects this distinction.

2. **The query cost is acceptable.** The "LEFT JOIN in application logic" concern from Option 2's cons is real but bounded. `DeadlineCalculationService` already loads all customers with FYE values and calculates all deadlines in a date range. Batch-loading filing statuses for the resulting (customer_id, slug, period_key) tuples is a single indexed query:

   ```sql
   SELECT * FROM filing_statuses
   WHERE (customer_id, deadline_type_slug, period_key) IN ((...), (...), ...)
   ```

   The unique constraint index `uq_filing_status_customer_deadline_period` makes this query efficient. The overlay (matching filing statuses to calculated deadlines) is a HashMap lookup in Java — O(1) per deadline.

3. **The filing status table stays small.** For a firm with 200 clients and ~1,600 deadline-periods per year, if 70% are eventually marked as "filed" and 5% as "not applicable," the table grows by ~1,200 records per year. After 5 years, that is 6,000 records — trivial for PostgreSQL. Compare with Option 1's 24,000 records (4,800/year x 5 years), where 30% of records carry no information.

4. **No synchronization logic is needed.** This is the most important consequence. Options 1 and 3 require detecting changes to `financial_year_end`, `vat_number`, and other custom fields that affect deadline applicability. The custom field system does not have per-field-slug change hooks — adding this infrastructure solely for filing status pre-population would be a disproportionate investment. Lazy creation eliminates the entire problem.

## Consequences

- **Positive:**
  - Filing status table is small and contains only meaningful data (human decisions)
  - No triggers, hooks, or listeners on custom field changes
  - No periodic batch jobs to pre-populate or clean up filing statuses
  - FYE changes require no filing status recalculation
  - Unique constraint provides natural upsert semantics

- **Negative:**
  - "All pending deadlines" queries cannot be expressed in pure SQL — they require the calculation service to compute deadlines first, then subtract filed ones. This is acceptable because the calculation service is already the entry point for all deadline queries
  - Dashboard widgets showing "X pending deadlines" require invoking the full calculation pipeline. If this becomes a performance concern, a lightweight cache (e.g., a denormalized count on OrgSettings updated on filing status change) can be added later
  - Batch filing status operations use upsert (INSERT ... ON CONFLICT DO UPDATE) rather than simple UPDATE, adding slight complexity to the repository layer

- **Neutral:**
  - The `FilingStatus` entity has a CHECK constraint limiting status to "filed" and "not_applicable." There is no "pending" status value in the database. The "pending" and "overdue" states are computed by `DeadlineCalculationService` based on the absence of a filing status record and the comparison of due date to current date.
