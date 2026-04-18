# ADR-248: Matter Closure as a Distinct `CLOSED` State with Enforced Compliance Gates

**Status**: Accepted

**Context**:

The horizontal `Project` entity has a three-state lifecycle: `ACTIVE` → `COMPLETED` → `ARCHIVED`, with a `reopen()` method that returns to `ACTIVE`. Transitions are enforced by `ProjectLifecycleGuard` and carry no compliance semantics — completing and archiving a project is purely a visibility concern, letting users filter finished work out of active lists.

For SA law firms, closing a matter is a different kind of action. The Legal Practice Act and LSSA professional-conduct rules impose preconditions before a matter can be "closed":

1. The client's trust-account balance for the matter must be zero — any residual funds must be transferred to the client or to the firm's office account before closure.
2. All disbursements incurred on behalf of the client must be either billed through or formally written off — a matter cannot close with unapproved or unbilled disbursements hanging.
3. A final bill must have been issued — the firm cannot close a matter with unbilled time or outstanding fees.
4. No future court dates may be scheduled against the matter.
5. No prescription trackers may still be running (uninterrupted prescription is a professional-negligence liability).
6. No tasks may remain in an open state (`IN_PROGRESS` or `BLOCKED`) — work should be complete or explicitly abandoned.
7. No pending information requests (Phase 34) may be outstanding against the client for this matter.
8. No pending document-acceptance requests (Phase 28) may be awaiting client sign-off.
9. All approved disbursements must be either billed through or formally written off (a tighter form of item 2, separated for per-gate error messaging).

Matter closure is also the event that starts the statutory retention clock (POPIA + Legal Practice Act typically require a minimum 5-year retention for closed matters). Without a distinct closure event, the retention clock has no anchor.

The design question is how to express this. Three shapes are possible: piggyback on `COMPLETED`/`ARCHIVED` with warning banners, introduce a new `CLOSED` status with enforced gates, or leave closure entirely to an external workflow with no lifecycle change. The first conflates visibility with compliance; the second adds lifecycle complexity but matches the domain; the third leaves firms managing closure in a spreadsheet outside the product.

A second question is what happens when a gate fails but the firm legitimately needs to close the matter anyway — e.g., a small trust residual that cannot be refunded because the client cannot be reached, or an old matter with technical gate failures that predate the product. Closure must be possible in edge cases without undermining the default safety.

**Options Considered**:

1. **Reuse `COMPLETED` + `ARCHIVED` with advisory warning banners** — no new status, no enforcement. On attempt to complete or archive a legal project, show a warning banner listing unmet gates; the user clicks through.
   - Pros:
     - No new migration, no new state, no new domain methods
     - Simplest possible implementation
     - Non-legal tenants see no change
   - Cons:
     - Warnings are advisory — nothing prevents closure with unresolved trust balances or unbilled disbursements, the two concerns the Legal Practice Act treats as mandatory
     - Retention clock has no anchor — `COMPLETED` and `ARCHIVED` are visibility states, not compliance events
     - The product does not differentiate a matter that was correctly closed from one that was quietly abandoned
     - Audit trail is thin — "user completed the matter despite X" is not the same quality of evidence as "user closed with override and justification Y"

2. **Introduce a new `CLOSED` status with enforced pre-closure gates and an owner-only override path** — `CLOSED` is a new terminal state reachable from `ACTIVE` or `COMPLETED` via an explicit `closeMatter(ClosureRequest)` call. All 9 gates evaluated server-side; closure blocks if any fails unless caller has `OVERRIDE_MATTER_CLOSURE` capability and provides a ≥20-character justification. Closure event triggers retention-policy insert. `ARCHIVED` remains a separate horizontal concern (visibility-only) reachable from any terminal state.
   - Pros:
     - Closure semantics cleanly distinct from completion and archival
     - Gates enforce professional-conduct rules at the point of action, not as passive warnings
     - Retention clock has a natural anchor (`MatterClosedEvent`)
     - Override path with audit-logged justification gives firms edge-case escape without eroding the default
     - Capability model cleanly separates `CLOSE_MATTER` (admin + owner) from `OVERRIDE_MATTER_CLOSURE` (owner only)
     - Future vertical profiles can add their own closure gates without touching the horizontal `Project` lifecycle
   - Cons:
     - New `ProjectStatus` value requires migration (CHECK constraint extension) and updates to every list/filter that enumerates statuses
     - Legal tenants now have two terminal-ish states (`CLOSED` is terminal re. compliance; `ARCHIVED` can still be reached from `CLOSED`) — users must understand the distinction
     - Reopen flow is more complex — retention clock must be paused, and reopen after retention expiry is not permitted

3. **Closure workflow entirely outside `Project` lifecycle — a separate `MatterClosure` entity tracks closure state, gates, and retention separately from the project status** — `Project.status` is untouched; matter closure is a parallel record that references the project and drives retention independently. The project stays `ACTIVE` or `COMPLETED` forever from the horizontal perspective.
   - Pros:
     - Zero change to horizontal `Project` lifecycle
     - Non-legal tenants see no new status or migration
     - Closure concerns cleanly encapsulated in a vertical entity
   - Cons:
     - Matter visibility gets confused — does a closed matter still appear in "active" lists because `Project.status = ACTIVE`?
     - Every query that filters out closed matters needs a join to the `MatterClosure` table, a cross-cutting concern that hits most tenant queries
     - Reopen flow requires deleting or soft-deleting the `MatterClosure` row rather than a lifecycle transition
     - The Project entity's own notion of "alive" diverges from the legal profession's notion of "open" — a persistent source of product confusion

**Decision**: Option 2 — introduce `CLOSED` as a new `ProjectStatus` with server-enforced compliance gates and an owner-only override path.

**Rationale**:

**Compliance events deserve first-class state.** Closing a matter is an act with legal consequences — retention clock starts, professional-conduct bar clears, audit trail finalises. Modelling it as state rather than metadata makes the consequences visible in every UI surface that shows a matter. Firms recognise "closed" as a defined professional act; the product should match.

**Enforcement is the point.** The Legal Practice Act and LSSA rules are not advisory. Trust balance = 0 before closure is a hard rule (Section 86), not a warning. Modelling gates as server-enforced (return 409 Conflict when unmet, unless override) puts the compliance weight at the point of decision rather than on a user to read and heed. Advisory warnings (Option 1) erode over time; enforcement does not.

**Override path preserves pragmatism.** Real firms close matters with small trust residuals that cannot be refunded (client unreachable), or with ancient gate failures that predate the product. Requiring an `OVERRIDE_MATTER_CLOSURE` capability bound to the `owner` role, plus a ≥20-character justification that lands in the audit log + closure log's `gateReport` JSONB, gives a defensible escape hatch: override is possible, but visible, logged, and restricted.

**Distinct from `ARCHIVED`.** `CLOSED` is a compliance event; `ARCHIVED` remains a visibility concern. A matter can be `CLOSED` and still `ARCHIVED` later (e.g., years after closure, to hide from dashboards). This mirrors real practice: a closed matter lives in the filing cabinet for the retention period before it is archived out of daily view.

**Retention anchor.** The `MatterClosedEvent` naturally pairs with a `RetentionPolicy` insert (Phase 50 infrastructure) carrying `retention_start = closure_date` and `retention_years = orgSettings.legalMatterRetentionYears` (default 5). Reopening stops the retention timer via a soft-cancel flag on the retention row. Without a `CLOSED` state, retention has no event to subscribe to.

**Cross-vertical impact minimal.** `ProjectStatus.CLOSED` is added to the Java enum and to the `projects.status` CHECK constraint for every tenant (schema-per-tenant), but only legal tenants can ever reach the state because the transition goes through `verticals/legal/closure/MatterClosureService` which is module-gated. Accounting and consulting tenants see `CLOSED` in the enum but never encounter it in data.

**Gate composability.** Defining a `ClosureGate` interface with one implementation per gate (TrustBalanceGate, DisbursementsSettledGate, FinalBillIssuedGate, etc.) lets future legal depth phases add gates (e.g., conveyancing-specific "all deeds-office documents returned") without touching the core service. The gate list is registered per profile, allowing UK/AU legal variants to swap gates.

**Consequences**:

- `ProjectStatus` enum gains `CLOSED`; `projects.status` CHECK constraint in migration `V97__matter_closure.sql` includes `'CLOSED'`; `projects.closed_at` timestamp column added.
- `Project.closeMatter(ClosureRequest)` domain method mirrors existing `complete()` / `archive()` guard pattern; transition guard allows `ACTIVE` → `CLOSED` and `COMPLETED` → `CLOSED`.
- `Project.reopen()` is extended to handle `CLOSED` → `ACTIVE`, with owner-only authorisation and retention-timer cancellation.
- New `MatterClosureLog` entity + table captures every closure attempt: who, when, gate evaluation JSONB, override flag, override justification, reopen timestamp. Audit-grade immutable record.
- New capabilities: `CLOSE_MATTER` (Owner + Admin), `OVERRIDE_MATTER_CLOSURE` (Owner only).
- New `MatterClosureService` lives under `verticals/legal/closure/`. Gate implementations are Spring beans discovered by interface.
- `MatterClosedEvent` domain event triggers `RetentionPolicy` insert per [ADR-249](ADR-249-retention-clock-starts-on-closure.md), notification to owner + admin, audit log entry.
- Frontend matter detail page gains a "Close Matter" action (legal module only) that runs `GET closure/evaluate` → renders gate report → optionally submits `POST closure/close` with override path for owners.
- Matter list views default to excluding `CLOSED` matters; an explicit filter chip surfaces them.
- Non-legal tenants never reach `CLOSED` — service entry is module-gated; UI surface is `<ModuleGate module="matter_closure">`.
- Future legal verticals (UK/AU) add country-specific gates by registering new `ClosureGate` beans under their own profile without modifying the service.
- Related: [ADR-238](ADR-238-entity-type-varchar-vs-enum.md), [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-247](ADR-247-legal-disbursement-sibling-entity.md), [ADR-249](ADR-249-retention-clock-starts-on-closure.md), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md).
