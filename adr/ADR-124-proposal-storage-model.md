# ADR-124: Proposal Storage Model

**Status**: Accepted

**Context**:

Phase 32 introduces proposals as a new entity type in DocTeams. A proposal contains a rich text body (scope of work, terms), structured fee configuration, team assignments, milestone schedules, and orchestration references. The question is how to store this data: as a standalone entity with embedded Tiptap content, as a wrapper around the existing `GeneratedDocument` entity, or as a reference to a `DocumentTemplate` with runtime context overlays.

The existing document pipeline has two relevant entities: `DocumentTemplate` (reusable templates with Tiptap JSON content, variable definitions, and clause associations) and `GeneratedDocument` (a rendered instance of a template with context, stored as a PDF in S3). Neither was designed for proposal-specific concerns: fee models, milestone schedules, team assignment, acceptance orchestration, or sequential numbering. Proposals also have a unique lifecycle (DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED) that differs from both templates (no lifecycle) and generated documents (static after generation).

The system also has `AcceptanceRequest` (Phase 28), which tracks the lifecycle of accepting a generated document. One approach would be to generate a document from the proposal content and use `AcceptanceRequest` for the acceptance flow. However, proposal acceptance triggers orchestration (project creation, billing setup, team assignment) that `AcceptanceRequest` was never designed to handle. Mixing proposal orchestration into the acceptance pipeline would violate the single-responsibility boundary.

**Options Considered**:

1. **Standalone entity with embedded Tiptap content** -- A new `Proposal` entity with `contentJson JSONB` containing Tiptap document JSON directly, plus dedicated columns for fee configuration, lifecycle state, and orchestration references.
   - Pros:
     - Clean domain boundary: proposals own their lifecycle, content, and orchestration independently
     - No coupling to the document template/generation pipeline, which has different lifecycle semantics
     - Fee configuration (FIXED/HOURLY/RETAINER), milestones, team members, and acceptance orchestration are first-class columns, not bolted-on metadata
     - Querying is straightforward: `ProposalRepository` queries a single table with standard JPA
     - Follows the established pattern where entities own their content (e.g., `Comment.content`, `Clause.content`)
   - Cons:
     - Duplicates the Tiptap JSON storage pattern (same JSONB column type as `DocumentTemplate`)
     - No automatic PDF generation — if PDF export is needed later, it must be added to proposals specifically
     - Proposals cannot leverage clause association management from `TemplateClause` (unlikely need — proposals are custom documents, not template-based)

2. **Wrapper around GeneratedDocument** -- Create a `Proposal` entity that references a `GeneratedDocument` for its content. The proposal body is authored via a template, generated into a `GeneratedDocument`, and the `Proposal` entity wraps this with fee/lifecycle/orchestration metadata.
   - Pros:
     - Reuses existing PDF generation pipeline — proposal content is always available as a PDF
     - Leverages `GeneratedDocument`'s S3 storage for the rendered output
     - Could theoretically reuse `AcceptanceRequest` for the acceptance flow
   - Cons:
     - `GeneratedDocument` is a rendered snapshot (PDF in S3) — proposals need to be editable in DRAFT state, which conflicts with the "generated = immutable" model
     - Tight coupling: changes to the generation pipeline would affect proposals, and vice versa
     - `GeneratedDocument` was designed for firm-internal use (generate → download/view) not for client-facing interactive workflows
     - Proposal-specific concerns (fee models, milestones, team, orchestration) would still need a separate entity — the wrapper adds a layer of indirection without reducing complexity
     - The Tiptap content would need to live in both the template (for editing) and the generated document (for rendering), creating a dual-source problem

3. **Template reference with runtime overlay** -- Store only a `DocumentTemplate` reference on the proposal and apply fee/variable context at runtime. No content duplication — the template is the content.
   - Pros:
     - No content duplication — template changes automatically reflected in proposals referencing it
     - Leverages the full template editing and clause management infrastructure
   - Cons:
     - Proposals should be immutable after sending (what the client sees must not change). Template changes after send would alter sent proposals — a critical integrity violation
     - Each proposal needs unique content (client-specific scope of work, customized terms) — reusing a template directly defeats the purpose of per-client proposals
     - Fee configuration, milestones, team members, and orchestration still need separate storage — this option only addresses the content body, not the core proposal data model
     - The template system is designed for reusable patterns, not per-client documents

**Decision**: Option 1 -- Standalone entity with embedded Tiptap content.

**Rationale**:

Proposals are fundamentally different from documents. They have a unique lifecycle (DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED), unique data concerns (fee models, milestones, team assignments), and unique side effects (acceptance orchestration creating projects, invoices, retainers, and team assignments). Forcing proposals into the document template/generation pipeline would couple two systems that share only a content format (Tiptap JSON) but differ in every other dimension.

The Tiptap JSON content is a value — a piece of data that the proposal owns — not a relationship to a separate entity. Storing it as a JSONB column on the proposal entity is the same pattern used by `DocumentTemplate.content`, `Clause.content`, and `Comment.content`. There is no "duplication" to avoid because each proposal has unique content authored specifically for that client engagement.

The standalone approach also cleanly separates concerns for future evolution. If proposals need PDF export later, it can be added without affecting the document generation pipeline. If the document system evolves (new storage format, new rendering engine), proposals are unaffected. Conversely, if proposals gain features like versioning or approval workflows, the document system is not burdened with proposal-specific concerns.

Option 2 (wrapper around GeneratedDocument) was rejected primarily because the editing-in-DRAFT requirement conflicts with GeneratedDocument's immutable-after-generation model. Proposals must be freely editable until sent, then frozen — a lifecycle that GeneratedDocument does not support. Option 3 (template reference) was rejected because proposal content must be immutable after send, and shared-template content by definition changes when the template is edited.

**Consequences**:

- `Proposal` entity has a `contentJson JSONB` column containing Tiptap document JSON, independent of `DocumentTemplate` or `GeneratedDocument`
- The Tiptap editor component is reused for proposal authoring (same frontend component as template editor)
- "Start from template" is a copy operation: template's Tiptap JSON is copied into the proposal's `contentJson` at creation time; subsequent template changes do not affect the proposal
- Proposals do not appear in the generated documents list, acceptance requests list, or document template management — they are a separate domain
- PDF export is out of scope for v1; if added later, it would use the existing `TiptapRenderer` + OpenHTMLToPDF pipeline called from `ProposalService`, not the document generation pipeline
- Related: [ADR-119](ADR-119-editor-library-selection.md), [ADR-120](ADR-120-document-storage-format.md)
