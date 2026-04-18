# ADR-250: Statement of Account as a Template + Context Builder, Not a New Entity

**Status**: Accepted

**Context**:

A Statement of Account is an SA legal billing artifact that summarises a client matter's financial position over a period: fees charged, disbursements incurred, trust activity (deposits, payments, current balance), previous balance owing, payments received, and closing balance owing. It is a common document at milestone points — end of month, end of matter phase, or at closure — and is distinct from an invoice. An invoice is a *demand* for payment covering a specific billing period; a Statement of Account is an *informational summary* of all activity up to a point in time, whether billed or not.

Phase 12 introduced document templates + rendering pipeline; Phase 31 migrated templates to Tiptap JSON with a variable resolver; Phase 42 added DOCX template support. Tiptap variables are resolved at render time by context builders — `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder` — each of which assembles a variable map from the target entity plus related data. Generated documents are stored as `GeneratedDocument` rows + S3 objects, with full regenerability and audit trail.

The design question for Statement of Account is whether it warrants a dedicated `Statement` entity (like `Invoice` or `Proposal`) with its own lifecycle, numbering, persistence, and controller, or whether it fits cleanly within the existing template + context + generated-document pipeline. A third path is to model it as an invoice variant (a `StatementInvoice` subclass or an invoice with a flag).

The Statement of Account has three properties that distinguish it from an invoice:

1. **Not a demand for payment** — it does not create a receivable, does not have an `issued` / `paid` lifecycle, does not increment an invoice counter, does not interact with the payment gateway.
2. **Not immutable** — an invoice, once sent, is immutable (lines cannot be edited, numbering is final). A Statement of Account is regeneratable at any point for any period; each generation is a fresh snapshot.
3. **Includes trust activity** — invoices never include trust transactions; statements always do (the trust balance is a material part of the matter's financial picture for the client).

**Options Considered**:

1. **New `Statement` entity with lifecycle, numbering, and persistence** — mirror `Invoice`: entity with status enum (DRAFT / GENERATED / SENT), auto-numbered reference, line items, stored period boundaries, controller for CRUD.
   - Pros:
     - First-class object — a statement is a known, queryable, auditable record
     - Can be emailed, portal-published, re-downloaded without regeneration
     - Statement numbering gives firms a reference for client correspondence ("Per our statement SOA-2026-003...")
     - Possible future extensions (scheduled auto-generation, client-portal display) have a natural home
   - Cons:
     - Significant plumbing: new table, migration, repository, service, controller, DTOs, events, audit
     - Statements are fundamentally derived data — the content is a function of (project, period). Persisting the derived snapshot risks the stored snapshot drifting from the live data (fees added retroactively, trust transactions amended)
     - Two parallel billing-adjacent entities (Invoice, Statement) with overlapping semantics but different rules — maintenance burden
     - Out of proportion with Phase 67 scope — matter closure and disbursements are the primary features; statements are supporting

2. **Statement as a template + context builder on top of the existing document-template pipeline** — new `StatementOfAccountContextBuilder` assembles variables (`fees.*`, `disbursements.*`, `trust.*`, `summary.*`) for a given `(projectId, periodStart, periodEnd)`. System-owned Tiptap template `statement-of-account.json` ships with the `legal-za` template pack. Generation goes through the existing `DocumentGenerationService` and produces a `GeneratedDocument` row + S3 object. No new entity.
   - Pros:
     - Reuses Phase 12/31 infrastructure entirely — zero plumbing for entity/repo/controller/events
     - Regenerability is natural — every generation is a fresh snapshot from live data; stored documents are immutable artifacts, live data is the source of truth
     - `GeneratedDocument` already captures audit trail (who generated, when, for which matter), so statements inherit that audit without new work
     - Firms can customise the statement format by cloning the system template (existing Phase 12 clone pattern) — no new UI
     - Tiptap variable system already supports the nested shape statements need (`statement.period_start`, `fees.total_hours`, etc.)
     - Matches the existing pattern for other informational documents (engagement letter, closure letter, monthly retainer report)
   - Cons:
     - No first-class "statement" object in queries — cannot ask "what statements were generated this month for customer X" without joining through `GeneratedDocument` + filtering by template type
     - Statement numbering is deterministic (computed from period + matter) rather than sequential — slightly less firm-friendly
     - Scheduled auto-generation (future phase) would need a wrapper job that calls the generation endpoint; no natural home for schedule metadata

3. **Statement as an invoice variant** — `Invoice` gains a `type` field (STANDARD / STATEMENT); statement invoices are generated through the invoice pipeline but marked non-billable, carry no receivable, and render via a different template.
   - Pros:
     - Reuses invoice pipeline (lines, lookup, list views)
     - Statements appear alongside invoices in the billing view
   - Cons:
     - Conflates two different concepts; every invoice flow must now check `type` to decide whether to apply standard rules
     - Invoice lifecycle (DRAFT → ISSUED → PAID) does not apply to statements, so the type flag disables large parts of the existing logic
     - Invoice numbering sequence gets fragmented — statements either share the sequence (visually confusing — SOA uses an invoice number) or need their own parallel sequence (complicates counter logic)
     - A statement includes trust activity, which invoices have no concept of — adds trust-specific fields to an otherwise-horizontal invoice
     - High surface area for bugs and high maintenance cost in exchange for small convenience

**Decision**: Option 2 — Statement of Account is a Tiptap template + `StatementOfAccountContextBuilder`, rendered via the existing document-generation pipeline as a `GeneratedDocument`. No new entity.

**Rationale**:

**Statements are derived, not a domain fact.** The canonical financial data — time entries, disbursements, invoices, trust transactions — lives in its authoritative tables. A Statement of Account is a rendering of that data for a given period. Persisting the rendered artifact (via `GeneratedDocument` + S3) captures what was shown to the client at a point in time; persisting a separate domain object (Option 1) adds a shadow of truth that can drift. The derived-data model matches the semantics: statements are not facts about the world, they are reports about facts.

**Regeneration is the default behaviour.** Firms amend historical time entries, approve late disbursements, post adjusting trust transactions. Each generation of a Statement of Account reflects the current state of the data as of the generation timestamp. The `GeneratedDocument` carries a timestamp + generating-user + period boundaries so the rendered snapshot is reproducible; the live data can be re-rendered at any time. A domain-object approach (Option 1) would either freeze the snapshot (introducing drift risk when users expect the statement to reflect current data) or regenerate every retrieval (defeating the persistence).

**Reuses existing infrastructure.** `DocumentGenerationService` (Phase 12), Tiptap variable resolver (Phase 31), template-pack install (Phase 12 + Phase 65), `GeneratedDocument` entity, audit, notifications — all of it is already in place. The new code for Phase 67 is (a) one context builder class under `verticals/legal/statement/`, (b) one new Tiptap template JSON under `template-packs/legal-za/`, (c) one controller endpoint, (d) one frontend dialog. That is orders of magnitude less plumbing than a new entity would require.

**Statement numbering.** The template includes a deterministic reference (`SOA-{projectId-short}-{yyyymmdd}`) generated by the context builder. This is less firm-friendly than a sequential counter but avoids the counter complexity, and firms that want sequential numbering can clone the template and wire a custom variable to a server-supplied counter in a later phase.

**Conveyancing, closure letters, retainer reports use the same pattern.** Phase 12 / Phase 66 / Phase 67 all produce informational documents via the same template + context + GeneratedDocument path. The Statement of Account is consistent with this family, not a novel entity shape. Keeping the pattern unified reduces the product's conceptual surface area.

**Doesn't foreclose future first-classification.** If usage signal demands it — firms want searchable statement lists, scheduled generation, or portal-side delivery — a future phase can introduce a thin `Statement` pointer entity that references the underlying `GeneratedDocument` and adds queryability + numbering without re-plumbing the rendering path. The Phase 67 decision is a "simplest thing that works," not a permanent prohibition.

**Consequences**:

- New Tiptap template `statement-of-account.json` ships in `template-packs/legal-za/` with the standard SA legal format (firm header → client block → matter reference → fees section → disbursements section → trust activity → summary → payment instructions footer).
- New context builder `StatementOfAccountContextBuilder` lives under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/`. Assembles `statement.*`, `matter.*`, `customer.*`, `fees.*`, `disbursements.*`, `trust.*`, `summary.*` variable namespaces from live data.
- New endpoint `POST /api/matters/{projectId}/statements` takes `{ periodStart, periodEnd, templateId? }`, invokes the context builder + `DocumentGenerationService`, and returns a `GeneratedDocument` with HTML preview + PDF. Default template resolves to the system `statement-of-account` template when `templateId` is omitted.
- Capability `GENERATE_STATEMENT_OF_ACCOUNT` (Owner + Admin + Member) gates the endpoint; module-gated under `disbursements` (statements are only meaningful when the firm tracks fees + disbursements + trust together).
- No `Statement` entity, no statement-specific migration, no statement counter.
- Statement reference is deterministic: `SOA-{projectId first 8 chars}-{generation yyyymmdd}`. Suffix with `-{HHmm}` if multiple statements are generated on the same date to ensure uniqueness in casual reference use.
- Frontend matter-detail page adds a "Generate Statement of Account" action (module-gated) that opens a dialog with period pickers + template selector + preview/download buttons. Previously-generated statements list inherits from the existing matter-documents tab (filter by template slug).
- Domain event `STATEMENT_GENERATED` emitted on each generation; audit logged.
- Future: if scheduled auto-generation or portal-side delivery is required, a subsequent phase wraps this endpoint behind a scheduler / portal read-model sync without changing the core path.
- Related: [ADR-244](ADR-244-pack-only-vertical-profiles.md), [ADR-181](ADR-181-vertical-profile-structure.md), [ADR-247](ADR-247-legal-disbursement-sibling-entity.md), [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md), [ADR-251](ADR-251-acceptance-eligible-template-manifest-flag.md).
