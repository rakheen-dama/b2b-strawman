# ADR-249: Retention Clock Starts on Matter Closure, Not on Archival

**Status**: Accepted

**Context**:

Phase 50 introduced the data-protection compliance module with a `RetentionPolicy` entity (per-org + per-entity retention rules) and a scheduled retention-sweep job that identifies records eligible for anonymisation or purge. Retention rules answer the question "when is this data allowed or required to be erased?" For most horizontal entities (customers, projects, invoices), retention is anchored to last-activity timestamps or entity-archival timestamps.

Legal matters are different. SA professional-conduct rules and POPIA require law firms to retain matter files — correspondence, contracts, attendance notes, trust ledger entries — for a statutory minimum period after the matter is **closed** (typically 5 years; longer for certain matter types). The retention clock's starting gun is the closure event, not the last-activity date and not when a user hid the project from a dashboard.

Phase 67 introduces `Project.status = CLOSED` as a distinct state with compliance semantics ([ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md)). The question is which event within the closure flow triggers the retention-policy row insert, and how reopening affects it.

Three anchoring candidates exist:

1. **Completion** (`COMPLETED`) — the horizontal "work is done" signal, which any project can hit. Completion is not a legal-closure act; a completed matter can still be reopened for follow-up work without starting the retention clock.
2. **Archival** (`ARCHIVED`) — the horizontal visibility-only state. Archival is user choice about dashboard clutter, not a compliance event; a matter can be archived without being closed, and vice versa.
3. **Closure** (`CLOSED`) — the legal-specific compliance event introduced in Phase 67. Closure requires passing 9 compliance gates (trust zero, disbursements settled, final bill, etc.) and is the moment the firm's professional-conduct obligations transition from "active matter" to "retained record."

A secondary question is how reopening interacts with retention. If a closed matter is reopened (`CLOSED` → `ACTIVE`), the retention policy must be suspended — it is no longer retained data, it is active data. If retention has already elapsed and the data has been anonymised or purged, reopen cannot meaningfully restore the matter.

**Options Considered**:

1. **Anchor retention to `ARCHIVED` (the horizontal signal)** — the existing `Project.archive()` transition fires retention-policy insert. Closure is a separate concern that does not touch retention.
   - Pros:
     - Single universal retention anchor across all verticals — accounting, consulting, legal all use `ARCHIVED` the same way
     - No coupling between `verticals/legal/closure/` and the retention module
     - Existing Phase 50 retention plumbing remains unchanged
   - Cons:
     - Mismatch with SA legal professional-conduct rules, which tie retention to closure, not archival
     - Legal firms would have to archive immediately on closure to start the retention clock — conflating two distinct acts
     - Non-legal tenants would hit retention on archival, but legal tenants genuinely need closure as the anchor; forcing legal to use archival is backwards
     - Archiving an active matter would accidentally start a legal retention clock, a dangerous side-effect

2. **Anchor retention to `CLOSED` for legal matters; retain `ARCHIVED`-anchored retention for non-legal entities** — profile-aware retention trigger. `MatterClosedEvent` inserts `RetentionPolicy` row under `legal-za` (and future legal profiles); non-legal projects continue to use archival or last-activity anchors per existing Phase 50 rules. Reopen (`CLOSED` → `ACTIVE`) soft-cancels the retention row.
   - Pros:
     - Matches SA legal professional-conduct rules — retention starts when the matter is closed, not when it is hidden
     - Preserves horizontal retention behaviour for non-legal entities
     - Reopen-awareness: legitimate reopening suspends retention without losing the original closure timestamp (soft-cancel flag, not delete)
     - `MatterClosureLog` + retention policy together give a complete audit: when closed, when retention started, when (if ever) retention was cancelled by reopen
     - Future legal variants (UK, AU) reuse the same wiring — legal closure → retention — with potentially different retention periods per profile
   - Cons:
     - Two retention anchors in the system (archival for horizontal, closure for legal) — slight conceptual overhead
     - Reopen logic must guard against retention already having elapsed — not permitted if data has been anonymised/purged
     - New optional column on `RetentionPolicy` (soft-cancel flag or equivalent) for reopen handling

3. **Anchor retention to `COMPLETED`** — use the horizontal completion signal as the retention anchor for legal matters. Closure is a parallel concern that records compliance state but does not start retention.
   - Pros:
     - Reuses an existing transition without introducing a new retention anchor
     - No coupling between closure and retention modules
   - Cons:
     - `COMPLETED` is reachable without passing any legal compliance gates — retention starts on incomplete/partial work
     - A matter can be `COMPLETED` → reopened → `COMPLETED` multiple times before being closed; each completion restarts the retention clock, which is wrong
     - Completion is not a legal act; tying retention to it conflates horizontal and vertical semantics
     - Professional-conduct rules unambiguously identify closure as the anchor

**Decision**: Option 2 — retention clock anchors on `MATTER_CLOSED` for legal matters; horizontal entities continue to use archival or last-activity anchors per existing Phase 50 rules; reopen soft-cancels the retention row.

**Rationale**:

**Matches the professional rule.** SA LSSA guidance and POPIA both frame retention in terms of matter closure. Anchoring to `CLOSED` means the product is compliant by default — the clock starts exactly when the profession says it should. Any other anchor (archival, completion) puts the product into tension with the rule and forces firms to work around it.

**Preserves horizontal behaviour.** Non-legal tenants do not close matters; their retention model continues to use whatever anchor Phase 50 established (archival or last-activity). The decision is specifically about legal matters and does not change behaviour for accounting, consulting, or future non-legal verticals.

**Reopen-aware.** Reopening a closed matter is a real scenario: a client returns with follow-up work, an appeal is filed, discovery surfaces new evidence. A reopened matter is no longer retained data — it is active. The retention row is soft-cancelled (an `inactiveAt` timestamp or `cancelledBy` reference, not a delete) so the history is preserved for audit. If the matter is closed again, a new retention row is inserted with a fresh anchor. If retention has elapsed and the data has already been anonymised/purged, reopen is not permitted — the `MatterClosureService.reopen()` path guards against this case by checking whether the retention sweep has run.

**Audit coherence.** `MatterClosureLog` records the closure act and its gate evaluation. `RetentionPolicy` records the retention obligation. The two tables cross-reference via `projectId` and both carry timestamps; investigators can reconstruct the full lifecycle: when closed (by whom, with override status and justification), when retention started, when (if ever) reopened, when retention next elapses.

**Per-profile retention period.** `OrgSettings.legalMatterRetentionYears` defaults to 5 for `legal-za` (statutory minimum under current SA guidance) and is per-org configurable. Firms handling longer-retention matter types (estate work, some conveyancing) can set a longer default. Future legal profiles (UK, AU) set their own default based on local rules.

**No retrofit.** Phase 50's retention infrastructure is already in place — we are adding a new event producer (`MatterClosedEvent`) and extending one column on `RetentionPolicy` for soft-cancel semantics. No sweeping changes to the retention module.

**Consequences**:

- `MatterClosedEvent` domain event handler inserts a `RetentionPolicy` row with `entityType = PROJECT`, `entityId = projectId`, `retentionStart = closure.closedAt`, `retentionYears = orgSettings.legalMatterRetentionYears ?? 5`.
- `RetentionPolicy` gains a nullable soft-cancel column (name TBD at implementation — e.g., `cancelledAt timestamp`, `cancelledByEvent varchar`) to record reopen-driven cancellations. Migration in `V97__matter_closure.sql` adds the column.
- `MatterClosureService.reopen()` soft-cancels the active retention row for the project; if no active retention row exists (e.g., retention already elapsed), reopen is blocked with a 409 Conflict + problem detail explaining the matter data has been anonymised.
- Re-closure (close → reopen → close) inserts a fresh retention row with a new anchor; the old (cancelled) row is preserved for audit.
- Retention sweeps skip soft-cancelled rows automatically.
- `OrgSettings.legalMatterRetentionYears` defaults to 5 under `legal-za` profile seeding; configurable per-org.
- Non-legal entities (customers, non-legal projects, invoices, documents) continue to use existing Phase 50 retention triggers — this ADR does not change horizontal retention behaviour.
- Statement of Account generation, disbursement recording, and trust-transaction posting on a closed matter are blocked by `VerticalModuleGuard` + entity-status checks — retention exists because the matter is inert.
- Related: [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md), [ADR-T001-data-protection-overview.md](ADR-T001-data-protection-overview.md) or equivalent Phase 50 ADR (cross-reference the actual retention ADR number at implementation), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md).
