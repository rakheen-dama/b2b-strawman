# ADR-209: Court Date vs. Deadline Architecture -- Separate Entity Model

**Status**: Proposed
**Date**: 2026-03-31
**Phase**: 55 (Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff)

## Context

Phase 55 introduces a court calendar for legal tenants. The platform already has a deadline calendar for accounting tenants (Phase 51): `DeadlineTypeRegistry` defines calculation rules, `DeadlineCalculationService` computes deadlines on-the-fly from each client's `financial_year_end` custom field, and `FilingStatus` tracks whether a filing has been completed. The question is whether court dates should reuse this infrastructure or use a separate entity model.

The two systems track fundamentally different things:

- **Accounting deadlines** are *derived*. They are computed from a single input (financial year-end) using regulatory rules (e.g., "provisional tax is due 6 months after FYE"). The firm does not enter due dates -- the system calculates them. No storage of deadline dates is needed (ADR-197). The access pattern is firm-wide: "show me all deadlines across all clients for the next 3 months."

- **Legal court dates** are *externally set*. The court schedules hearing dates, trial dates, and filing deadlines. The firm enters them manually. Each date has rich metadata: court name, case reference, judge assignment, outcome. The access pattern is per-matter: "show me all court dates for this matter" and firm-wide: "show me all upcoming court dates."

## Options Considered

### Option A: Reuse DeadlineTypeRegistry Pattern

Extend `DeadlineTypeRegistry` with legal deadline types. Add court date types as "deadlines" with externally-entered dates instead of calculated dates. Extend `FilingStatus` with court-specific columns (court_name, judge, outcome).

- **Pros:** Single calendar infrastructure for all verticals. Consistent API shape. Less new code.
- **Cons:** Accounting deadlines are stateless (calculated), court dates are stateful (entered). `FilingStatus` would need to serve two fundamentally different purposes -- tracking whether something was filed vs. tracking a multi-state court event lifecycle (SCHEDULED -> POSTPONED -> HEARD). The `DeadlineCalculationService` on-the-fly computation pattern does not apply to manually entered dates. Prescription trackers add a third pattern (calculated date with interruption). Forcing these into one model creates a lowest-common-denominator abstraction that serves neither well.

### Option B: Separate Entity Model for Event-Based Dates (Selected)

Create a new `CourtDate` entity specifically designed for court appearances, with its own lifecycle (SCHEDULED, POSTPONED, HEARD, CANCELLED), rich metadata, and per-matter queries. Create a separate `PrescriptionTracker` entity for prescription periods.

- **Pros:** Each entity is purpose-built for its domain. Court date lifecycle (postpone, cancel, record outcome) maps cleanly to entity state transitions. No forced abstraction over different concerns. Prescription tracking has its own semantics (interruption, expiry warnings). The accounting deadline system remains untouched -- no risk of regression. Each module can evolve independently.
- **Cons:** Two separate calendar systems in the codebase. If a future requirement needs a "unified calendar view," a query-time aggregation layer would be needed. More entities and tables.

### Option C: Unified Calendar Abstraction

Create a generic `CalendarEvent` entity with a type discriminator (ACCOUNTING_DEADLINE, COURT_DATE, PRESCRIPTION). Abstract the differences behind a common interface.

- **Pros:** Single query for "all upcoming events." Extensible for future event types.
- **Cons:** The abstraction leaks immediately. Court dates have court_name, judge, outcome. Deadlines have calculation rules and filing status. Prescriptions have interruption dates. A discriminator-based model with 10+ nullable columns specific to each type is a worse design than separate tables. The query-time unification (a UNION query or application-level merge) is trivial compared to the cost of a bad entity model.

## Decision

**Option B -- Separate entity model for court dates and prescription trackers.**

## Rationale

1. **Different data sources:** Accounting deadlines are derived from data (fiscal year-end) and rules (regulatory formulas). Court dates are entered by users based on external events. Prescription dates are calculated but have stateful interruption. These are three distinct patterns that should not be forced into one model.

2. **Different lifecycles:** A deadline is "pending" until "filed" -- two states. A court date goes through SCHEDULED -> POSTPONED -> HEARD or CANCELLED, with outcome recording. A prescription tracker goes through RUNNING -> WARNED -> INTERRUPTED or EXPIRED. These lifecycle differences are best expressed as purpose-built entities, not as a generic state machine.

3. **Different access patterns:** Deadlines are queried firm-wide by date range (the primary view). Court dates are queried per-matter (the primary view) and firm-wide (the calendar view). The per-matter query is a simple `WHERE project_id = ?` on the court_dates table, which is efficient and clear.

4. **Multi-vertical isolation:** Accounting and legal modules should be as independent as possible. Sharing an entity model creates coupling -- a change to accommodate one vertical's needs could break the other. Separate entities allow each module to evolve without coordination.

5. **Future unified calendar:** If needed, a "unified calendar" view can aggregate across `court_dates`, `prescription_trackers`, and calculated deadlines at query time. This is a read-side concern, not a write-side concern. The query cost is low (3 simple queries, application-level merge) and does not justify compromising the write model.

## Consequences

- **Positive:** Court dates have a clean, purpose-built entity with meaningful lifecycle states
- **Positive:** Prescription tracking has its own semantics without conflating with deadline or court date concepts
- **Positive:** Accounting deadline system remains completely untouched -- zero regression risk
- **Positive:** Each module can add entity fields independently without affecting the other
- **Negative:** Two separate table sets for date-based events (court_dates + prescription_trackers vs. deadline calculation)
- **Negative:** A future "unified calendar" view requires query-time aggregation across multiple sources
- **Mitigations:** The existing `/api/court-calendar/upcoming` endpoint provides a unified view for legal dates; a cross-vertical calendar would be a future phase concern if the need arises
