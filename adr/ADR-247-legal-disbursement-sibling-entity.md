# ADR-247: Legal Disbursements as a Sibling Entity to Expense

**Status**: Accepted

**Context**:

South African legal practice has a first-class concept of "disbursements" — out-of-pocket costs paid on behalf of a client (sheriff fees, counsel fees, deeds-office fees, search fees, advocate fees, court fees, expert-witness costs). These are structurally similar to the existing `Expense` entity (an amount, a project, a date, a receipt, a billing lifecycle), but they differ materially in how they are treated under the Legal Practice Act, SA VAT law, and professional-conduct rules.

Four properties make a disbursement semantically different from a generic project expense:

1. **Payment source** — a disbursement can be paid from the firm's office account or from the client's trust account. Trust-paid disbursements require a linked `TrustTransaction` of type `DISBURSEMENT_PAYMENT` against the matter's client-ledger card. Office expenses never touch the trust module.
2. **VAT treatment** — sheriff, deeds-office, and court fees are zero-rated pass-throughs (no VAT charged to the client); counsel, search, expert-witness fees are standard-rated 15%. Standard `Expense` entries assume standard VAT everywhere.
3. **Approval workflow** — the Legal Practice Act requires attorney approval before disbursements are billed through to a client. `Expense` currently has no approval states; it moves straight from entry to unbilled/billed.
4. **Markup prohibition** — SA legal rules forbid marking up disbursements; they must be billed at cost. `Expense` has no notion of markup either, but nothing enforces cost-pricing either. A disbursement entity can encode this as an invariant.

Phase 67 must decide whether to express these differences by extending `Expense` (add columns, enum values, and conditional logic guarded by the `disbursements` vertical module) or by introducing a sibling entity `LegalDisbursement` under `verticals/legal/disbursement/`. The choice has long-term implications for the Expense entity, invoice pipeline coupling, and future vertical forks (UK/AU legal will want the same shape with different regulatory details).

The invoice pipeline already has a pragmatic parallel-path precedent: `TimeEntry` and `Expense` are not unified under a common `Billable` interface; `InvoiceCreationService.createDraft(...)` takes `timeEntryIds` and `expenseIds` as separate parameters and the unbilled-summary service queries both repositories independently.

**Options Considered**:

1. **Extend `Expense` with discriminator + nullable disbursement-only columns** — add `expense.type ∈ {STANDARD, DISBURSEMENT}`, plus nullable `disbursement_category`, `payment_source`, `trust_transaction_id`, `vat_treatment`, `approval_status`, `approved_by`, `approved_at`, `approval_notes`. Module-gate the disbursement endpoints; frontend shows disbursement UX only when the legal module is enabled.
   - Pros:
     - Zero retrofit of the invoice pipeline — `Expense` already integrates with `UnbilledTimeService` and `InvoiceCreationService`
     - One repository, one service, one controller to maintain
     - Existing `Expense` lifecycle methods (`markBilled`, `unbill`, `writeOff`, `restore`) are reused
     - Statement of Account context builder can query a single table for both fees and disbursements
   - Cons:
     - Eight nullable columns on `Expense` that are meaningless for non-legal tenants — schema bloat every accounting/consulting tenant carries
     - `ExpenseService` grows a conditional branch for every operation (if `type == DISBURSEMENT` then approval gate, trust linkage, VAT override, capability check) — the "God service" antipattern
     - Capability model becomes messy: `MANAGE_EXPENSES` cannot sensibly include `approve disbursement` since approvals are legal-specific; splitting the capability means `Expense` rows have two parallel permission models driven by a runtime field
     - Future UK/AU legal variants would need yet more columns on the shared entity (UK-specific VAT treatments, AU disbursement categories)
     - The bounded context for "legal practice" is diluted — legal concepts leak into the horizontal expense module

2. **Sibling entity `LegalDisbursement` under `verticals/legal/disbursement/`, parallel-path integration with the invoice pipeline** — new table, new repository, new service, new controller. The invoice pipeline gains a third parameter (`disbursementIds` on `createDraft`) and `UnbilledTimeService` is extended (behind a module guard) to include approved-unbilled disbursements in the unbilled summary. No shared `Billable` interface is introduced; the pattern mirrors the existing time-vs-expense parallel.
   - Pros:
     - Clean bounded context — all legal-specific billing concepts live under `verticals/legal/`
     - `Expense` remains untouched; non-legal tenants see no new columns
     - Approval workflow, trust linkage, payment source, and VAT treatment modelled as first-class fields on an entity that knows what they mean
     - Capability model is straightforward: `MANAGE_DISBURSEMENTS`, `APPROVE_DISBURSEMENTS`, `WRITE_OFF_DISBURSEMENTS` are distinct from expense capabilities
     - Future UK/AU legal variants extend `LegalDisbursement` with variant-specific categories and VAT treatments, not a shared horizontal entity
     - Module gating is structural (entity lives under `verticals/legal/`, package-level guards) rather than field-level runtime checks
     - Mirrors the precedent of `verticals/legal/trustaccounting/` and `verticals/legal/lssa/tariff/` — legal-specific domain objects live under `verticals/legal/`
   - Cons:
     - Three billing entry points (time, expense, disbursement) rather than two — the invoice pipeline is slightly more complex
     - Two unbilled-item paths to keep in sync when changes happen to the invoice generation contract
     - Statement of Account context builder queries two tables (`expense` + `legal_disbursement`) rather than one filtered query
     - Mutation methods (`markBilled`, `unbill`, `writeOff`) are implemented twice, once per entity

3. **Introduce a shared `Billable` interface across `TimeEntry`, `Expense`, and `LegalDisbursement`** — retrofit an interface on all three, refactor `InvoiceCreationService` to accept a single `List<Billable> items`, unify `UnbilledTimeService` into `UnbilledItemService`.
   - Pros:
     - Cleanest invoice pipeline — one abstraction, one code path
     - Future new billable types (subscription credits, retainers, flat-fee packages) slot in via the same interface
     - Shared mutation methods (`markBilled`, `unbill`, `writeOff`, `restore`) defined once on the interface default, overridable per implementation
   - Cons:
     - Retrofit scope is significant — `TimeEntry` uses a bare setter for billing status while `Expense` uses rich mutation methods; the APIs must converge, touching every existing time-entry caller
     - `TimeEntry` has project-member-scoped fields (rate snapshot, billable hours) that don't generalise; the interface becomes a leaky abstraction with optional methods
     - Risk of destabilising a working invoice pipeline for a benefit that's mostly aesthetic
     - Out of scope for a legal-depth phase — this is a horizontal invoice-pipeline refactor masquerading as a legal feature

**Decision**: Option 2 — `LegalDisbursement` as a sibling entity under `verticals/legal/disbursement/`, parallel-path integration with the invoice pipeline. No shared `Billable` interface.

**Rationale**:

**Bounded context integrity.** The legal module exists precisely to keep legal-specific concepts out of the horizontal stack. Trust accounting, LSSA tariffs, court calendar, conflict check, and adverse parties all live under `verticals/legal/` for the same reason: their rules, lifecycles, and regulatory framing do not generalise to accounting or consulting tenants. Disbursements belong in the same neighbourhood. Putting disbursement state on `Expense` would be the first crack in the principle and would set precedent for every subsequent vertical to bloat the horizontal entities.

**Non-legal tenants pay no cost.** Schema-per-tenant means the `legal_disbursements` table exists in every tenant's schema, but it remains empty for accounting/consulting tenants and `VerticalModuleGuard` blocks the service entry points from ever being called. Option 1 would push eight nullable columns into the `expenses` table for every tenant, with no benefit to non-legal tenants and a small real cost in index size, query planner complexity, and DTO payload size.

**Parallel-path matches existing pattern.** The invoice pipeline already treats `TimeEntry` and `Expense` as parallel inputs rather than polymorphic `Billable` items. `InvoiceCreationService.createDraft(...)` takes `timeEntryIds` and `expenseIds` as separate lists; `UnbilledTimeService` returns a summary with both categories broken out. Adding `disbursementIds` as a third parameter and extending the summary to include disbursements is consistent, additive, and low-risk. Retrofitting a `Billable` interface (Option 3) is a larger refactor with weaker justification.

**Capability and RBAC cleanliness.** Disbursement approval is a distinct operation requiring distinct capability (`APPROVE_DISBURSEMENTS`). Placing approval state on a generic `Expense` entity would either over-grant `MANAGE_EXPENSES` holders approval rights or force a capability split that's keyed on a runtime `type` field — a well-known antipattern.

**Future vertical forks.** When the platform adds `legal-uk` or `legal-au`, disbursement categories and VAT treatments will differ (UK VAT 20%, different pass-through rules; AU GST). Extending a shared `Expense` entity with per-country fields compounds the bloat. A `LegalDisbursement` entity can evolve per-country via profile-driven category lists and VAT treatment enums without touching the horizontal stack.

**Slight cost accepted.** Duplicating mutation methods (`markBilled`, `unbill`, `writeOff`, `restore`) across `Expense` and `LegalDisbursement` is a cost. It is a small, localised cost — these methods are short and stable — and the alternative (a shared abstraction) has a higher refactor cost and pulls in `TimeEntry`, which doesn't fit cleanly.

**Consequences**:

- New entity `LegalDisbursement` lives under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/` with its own repository, service, controller, DTOs, events, and capability set.
- `Expense` entity is not modified; existing expense behaviour is preserved in full.
- `InvoiceCreationService.createDraft(...)` gains a `List<UUID> disbursementIds` parameter; `UnbilledTimeService.getUnbilledTime(...)` extends its returned DTO to include `disbursements: List<UnbilledDisbursementDto>` when the `disbursements` module is enabled for the tenant.
- `InvoiceLine` gains a nullable `disbursementId` FK and a new valid `lineSource` string value `DISBURSEMENT` (added to the existing CHECK constraint per [ADR-238](ADR-238-entity-type-varchar-vs-enum.md)).
- No shared `Billable` interface is introduced; the three billable types (time, expense, disbursement) remain parallel paths. A future invoice-pipeline refactor could unify them, but that is out of scope for this phase.
- `VerticalModuleGuard` gates all disbursement endpoints; non-legal tenants receive 404 from every `/api/legal/disbursements/...` route.
- Disbursement categories, VAT treatments, and payment sources are enums in Java and varchar + CHECK in SQL per [ADR-238](ADR-238-entity-type-varchar-vs-enum.md).
- `Statement of Account` context builder ([ADR-250](ADR-250-statement-of-account-template-and-context.md)) queries `legal_disbursements` separately from `expenses` and aggregates in the service layer.
- Future UK/AU legal variants add their own disbursement categories and VAT treatments without touching `Expense` or the horizontal invoice pipeline.
- Related: [ADR-238](ADR-238-entity-type-varchar-vs-enum.md), [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-244](ADR-244-pack-only-vertical-profiles.md), [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md), [ADR-250](ADR-250-statement-of-account-template-and-context.md).
