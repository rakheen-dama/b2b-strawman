# ADR-288: Contract Review Output as Document-as-Report Over Inline Annotations

**Status**: Accepted

**Context**:

Phase 74 introduces a contract review AI skill that analyses uploaded contracts and produces structured findings -- severity-ranked risks, missing protections, non-standard clauses, and statutory cross-references. The question is how to present these findings to the reviewing attorney. Two primary approaches exist: generating a standalone review report document, or annotating the original contract inline with findings attached to specific clauses.

The existing document system (Phase 12/31/42) provides a mature pipeline: `Document` entity with Tiptap JSON content, rich editor for viewing and editing, PDF export via the DOCX pipeline, sharing via the customer portal, and integration with matters. The review output includes clause references (e.g., "Clause 12.3") but operates on extracted text -- the AI does not have positional information within the original document's rendered layout.

Phase 72 established the pattern of AI skills producing execution gates that, on approval, create or modify entities. The contract review skill needs a clear "what happens on gate approval" action.

**Options Considered**:

1. **Document-as-report -- new Document entity with structured Tiptap content (CHOSEN)** -- On gate approval, create a new `Document` entity linked to the matter, containing the review findings formatted as a structured Tiptap document (executive summary, severity-grouped findings, statutory references, recommendations). The report appears in the matter's documents tab alongside the reviewed contract.
   - Pros:
     - **Fits the existing document system perfectly.** No new entities, no new pipelines, no new UI components for document display. The report is a Document -- it renders in the Tiptap viewer, exports to PDF, is shareable via the portal, and appears in the matter's document list.
     - **Shareable and printable.** Attorneys frequently share contract review notes with clients, opposing counsel, or colleagues. A standalone document is the natural format for sharing. PDF export is already available.
     - **Archival value.** The review report persists as a permanent record of the AI-assisted review at a point in time. It is discoverable in searches, linked to the matter, and included in matter exports.
     - **Clean gate action.** The gate's `proposed_action` is "create a document" -- a well-understood, reversible action. On approval, one Document is created. On rejection, nothing happens.
     - **No Tiptap extension work.** Inline annotations would require building or integrating a Tiptap annotation extension (marks, decorations, sidebar panel, annotation CRUD). This is a significant frontend effort with uncertain UX.
   - Cons:
     - **Findings are not visually linked to the original text.** The attorney reads the report alongside the contract, cross-referencing clause numbers manually. For long contracts, this requires scrolling between two documents.
     - **Redundancy.** The report duplicates clause text from the original document (quoting the relevant clause in the finding). This increases document size but ensures the report is self-contained.
     - **No interactive annotation experience.** Attorneys accustomed to markup tools (Track Changes, Acrobat annotations) may find a separate report less intuitive than inline annotations.

2. **Inline annotations on the original document -- Tiptap annotation extension** -- Build a Tiptap annotation system that attaches findings to specific text ranges in the original document. Each finding appears as a highlight with a sidebar panel showing details.
   - Pros:
     - **Visual proximity.** Findings appear directly on the clause they reference. No cross-referencing needed.
     - **Familiar UX.** Resembles Track Changes / Acrobat annotations that attorneys already use.
     - **Interactive.** The attorney can click a finding to see details, dismiss irrelevant findings, and add notes.
   - Cons:
     - **Significant frontend effort.** Requires a Tiptap annotation extension: custom marks for highlights, a sidebar panel for finding details, annotation CRUD, and serialisation in the Tiptap JSON schema. Estimated 2-3 weeks of focused frontend work.
     - **Positional mapping problem.** The AI operates on extracted plain text, not the Tiptap JSON structure. Mapping clause references from the AI output back to specific text ranges in the Tiptap document is fragile -- text extraction may reorder, collapse whitespace, or lose structural boundaries.
     - **Original document mutation.** Annotations modify the original document's Tiptap JSON (adding marks). The attorney may not want AI annotations on the source document, especially if the document is shared externally.
     - **Not self-contained.** Annotations are only visible within the Tiptap editor. They cannot be exported to PDF or shared as a standalone artefact without additional export logic.
     - **Scope creep.** The annotation system would need its own CRUD (create, update, delete annotations), its own UI (sidebar, tooltips, colour coding), and its own persistence model. This is a feature domain, not a feature.

3. **Separate findings entity with no document output -- lightweight but less shareable** -- Store findings as structured data (new FindingEntity linked to the document and matter) without generating a document. Display findings in a custom UI panel.
   - Pros:
     - **Lightweight.** No document creation, no Tiptap rendering. Findings are structured data displayed in a table or list.
     - **Queryable.** Findings can be filtered, searched, and aggregated across matters.
   - Cons:
     - **Not shareable.** Findings exist only in the Kazi UI. No PDF export, no portal sharing, no email attachment. Attorneys cannot send the review to a client.
     - **New entity and new UI.** Requires a new persistence domain (finding entity, repository, service, controller) and new frontend components. More infrastructure than Option 1 which reuses the existing Document pipeline.
     - **No archival document.** The review is ephemeral -- it exists as structured data but not as a document of record. For compliance and professional indemnity purposes, a document is more valuable.

**Decision**: Option 1 -- Document-as-report. On gate approval, the system creates a new `Document` entity with the review findings rendered as structured Tiptap content. The document is linked to the matter and the original reviewed document.

**Rationale**:

The document-as-report approach maximises value with minimal infrastructure investment. The entire document pipeline -- Tiptap rendering, PDF export, portal sharing, matter attachment, search -- is already built. Creating a review report as a Document entity is a one-service operation (`AiReviewReportGenerator` calls `DocumentService.createDocument()`). The report is immediately usable: viewable in the editor, exportable to PDF, shareable with clients.

Inline annotations (Option 2) would deliver a superior interactive experience but at disproportionate cost. The Tiptap annotation extension is a multi-week frontend effort, and the positional mapping between extracted text and Tiptap JSON is technically fragile. The annotation system is a feature domain in itself (CRUD, UI, serialisation, export). For Phase 74's goal of demonstrating AI value through the system of record, a self-contained report document delivers 80% of the value at 20% of the effort.

The migration path is clear: if inline annotations prove valuable (based on attorney feedback), a future phase can build the annotation system and optionally generate both a report document and inline annotations from the same review output. The structured `ContractReviewOutput` supports both rendering targets.

**Consequences**:

- Positive:
  - Zero new entities for contract review output. Reuses the existing `Document` entity and pipeline.
  - Review reports are shareable, exportable, archivable -- attorneys can send them to clients.
  - Clean gate action: "create a document" is well-understood and reversible (delete the document).
  - `AiReviewReportGenerator` is the only new service needed for output rendering.

- Negative:
  - No visual proximity between findings and the original clause text. Cross-referencing requires reading two documents side by side. Mitigated: findings include quoted clause text and clear clause references.
  - No interactive annotation experience in v1. Attorneys accustomed to markup tools may find this less intuitive.

- Neutral:
  - The review report Document has `source = AI_GENERATED` and `ai_execution_id` metadata ([ADR-292](ADR-292-ai-generated-document-provenance.md)) for provenance tracking.
  - The report uses a standardised Tiptap structure: title, executive summary, severity-grouped findings (each with clause reference, description, legal basis, recommendation), missing protections, overall assessment.
  - The `ContractReviewOutput` record is rendering-agnostic -- it can drive both document-as-report (Phase 74) and inline annotations (future) without structural changes.

- Related: [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiSkill interface -- contract review implements it), [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (execution gates -- report creation requires attorney approval), [ADR-292](ADR-292-ai-generated-document-provenance.md) (AI-generated document provenance -- report is tagged as AI-generated)
