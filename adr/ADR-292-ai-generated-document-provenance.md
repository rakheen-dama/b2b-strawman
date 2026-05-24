# ADR-292: AI-Generated Document Provenance Tracking

**Status**: Accepted

**Context**:

Phase 74 introduces two AI skills that produce `Document` entities: the contract review skill creates a review report document, and the drafting skill creates a template-filled draft document. Both documents are created through the existing document pipeline (`DocumentService.createDocument()` and `DocumentGenerationService`) and appear in the matter's documents tab alongside human-created documents.

For audit, trust, and liability purposes, the firm needs to know which documents were AI-generated, which AI execution produced them, and who approved the generation. The Attorneys Act liability framework (ADR-281) requires that AI-assisted work product is traceable -- if a review report contains an incorrect legal analysis, or a draft document includes a hallucinated clause, the firm must be able to trace the document back to the AI invocation, the prompt that produced it, and the attorney who approved it.

The question is how to record this provenance on the document. Three approaches exist: metadata fields on the existing Document entity, a separate provenance entity linked to Document, or audit trail only (no structural tracking, rely on audit events).

**Options Considered**:

1. **Document metadata fields (`source`, `ai_execution_id`) -- lightweight provenance on the existing entity (CHOSEN)** -- Add two nullable fields to the `Document` entity: `source` (VARCHAR, enum: `MANUAL`, `AI_GENERATED`, `TEMPLATE_GENERATED`) and `ai_execution_id` (UUID, FK to `ai_executions.id`, nullable). Existing documents default to `MANUAL`. AI-generated documents set `source = AI_GENERATED` and `ai_execution_id` to the execution that produced them.
   - Pros:
     - **Queryable.** "Show me all AI-generated documents" is a simple WHERE clause. "Which execution produced this document?" is a single FK lookup. No joins required.
     - **Visible in the UI.** The document list can display a small badge ("AI-generated") based on the `source` field. The document detail page can link to the AI execution history for full provenance (prompt, output, cost, who invoked, who approved).
     - **Lightweight.** Two columns on an existing table. No new entity, no new repository, no new service. The migration is a single `ALTER TABLE ADD COLUMN` (in practice, a new Flyway migration or an addition to V127).
     - **Backward-compatible.** Existing documents get `source = MANUAL` by default. No data migration beyond setting the default. The nullable `ai_execution_id` means non-AI documents simply have NULL.
     - **Supports future filtering.** The compliance dashboard or document search can filter by source to show only AI-generated documents for review. This supports firm-wide AI usage auditing.
   - Cons:
     - **Limited provenance depth.** The fields tell you "this was AI-generated" and "by this execution," but not the full chain (which gate, who approved, what the AI reasoning was). The full chain requires following the FK: Document -> AiExecution -> AiExecutionGate. This is a join, not a single-row lookup.
     - **Schema change on a core entity.** Adding columns to `Document` affects all tenants (tenant-scoped migration). The migration is lightweight (two nullable columns) but touches a high-traffic table.
     - **`source` enum may grow.** Future sources (imported from external system, migrated from legacy) would need new enum values. VARCHAR enum is migration-friendly, so this is a minor concern.

2. **Separate `DocumentProvenance` entity -- dedicated provenance tracking** -- Create a new `DocumentProvenance` entity linked to `Document` (1:1) that stores the full provenance chain: source, execution ID, gate ID, approved by, prompt hash, model version, etc.
   - Pros:
     - **Rich provenance.** Can store the full chain in one entity: source, execution, gate, approver, prompt version, model, cost, timestamp.
     - **No schema change on Document.** The core entity stays clean. Provenance is a separate concern in a separate table.
     - **Extensible.** Future provenance needs (import source, migration source, external system reference) can add fields without touching Document.
   - Cons:
     - **Over-engineered for the use case.** The full provenance chain is already traceable via FKs: Document -> AiExecution -> AiExecutionGate. A dedicated entity duplicates data that is already queryable through joins. The additional convenience of a denormalised provenance entity does not justify a new table, entity, repository, and service.
     - **1:1 relationship smell.** A `DocumentProvenance` entity with a 1:1 relationship to `Document` is essentially two halves of the same entity. If every Document has provenance (even if just `source = MANUAL`), the provenance entity is mandatory -- which means it should be on the Document itself. If only AI-generated documents have provenance, the provenance entity is sparse -- which means nullable fields on Document are simpler.
     - **Join overhead.** Displaying the "AI-generated" badge on the document list requires a LEFT JOIN to `DocumentProvenance`. Metadata fields on Document avoid this join.

3. **Audit trail only -- no structural tracking, rely on audit events** -- Don't add provenance fields to Document. Instead, rely on the existing audit trail: `AI_SKILL_INVOKED` + `AI_GATE_APPROVED` audit events contain the execution ID and the action taken ("created document {id}"). Provenance is reconstructable from the audit log.
   - Pros:
     - **Zero schema changes.** No new fields, no new entities. The audit log already contains the information.
     - **Separation of concerns.** The Document entity stays focused on document data. Provenance is an audit concern, not a data model concern.
   - Cons:
     - **Not queryable.** "Show me all AI-generated documents" requires scanning the audit log for `AI_GATE_APPROVED` events with action type `CREATE_REVIEW_REPORT` or `CREATE_DRAFT_DOCUMENT`, extracting the document IDs, and joining back to Document. This is a multi-step query that cannot be indexed.
     - **Not visible in the UI without infrastructure.** Displaying an "AI-generated" badge on the document list requires a real-time audit log lookup per document. This is too slow for list rendering.
     - **Fragile linkage.** The audit event references the document by ID in its metadata JSON. If the metadata format changes, the linkage breaks. Structural FKs are more resilient.
     - **Audit events are append-only.** If the audit log is purged or archived (common for GDPR retention compliance), the provenance link is lost.

**Decision**: Option 1 -- Add `source` (VARCHAR) and `ai_execution_id` (UUID, nullable FK) fields to the `Document` entity. Existing documents default to `source = MANUAL`.

**Rationale**:

Document provenance needs to be queryable ("which documents are AI-generated?") and visible ("show me the AI badge"). Both requirements demand structural fields on the entity, not audit log reconstruction. Metadata fields on Document are the simplest approach that satisfies both requirements: a WHERE clause for querying, a field check for UI rendering, and a FK for full provenance chain traversal.

A separate provenance entity (Option 2) is over-engineered because the provenance data is minimal (two fields) and the full chain is already navigable via FKs. The 1:1 relationship between Document and DocumentProvenance is a code smell -- if every document has exactly one provenance record, the provenance data belongs on the document.

Audit trail only (Option 3) provides the data in principle but not in practice. The firm cannot filter their document list by "AI-generated" without a real-time audit log scan. The compliance officer cannot produce a report of "all AI-generated documents this quarter" without a JSON-path query on unindexed audit event metadata. These are deal-breakers for a feature that needs to be immediately visible and queryable.

The `source` field also covers non-AI provenance. `TEMPLATE_GENERATED` marks documents created through the existing template generation pipeline (Phase 42). This distinction helps the firm understand their document creation patterns -- how many documents are fully manual, template-assisted, or AI-assisted.

**Consequences**:

- Positive:
  - Simple querying: `WHERE source = 'AI_GENERATED'` for all AI documents. No joins, no JSON path queries.
  - UI badge: document list shows "AI" indicator based on `source` field. Document detail links to AI execution history via `ai_execution_id`.
  - Full provenance chain: Document.ai_execution_id -> AiExecution -> AiExecutionGate (who approved, when, with what reasoning).
  - Backward-compatible: existing documents get `source = MANUAL` (or NULL -> defaulted in code). No data migration.

- Negative:
  - Schema change on Document entity. Two nullable columns added via migration. Lightweight but touches a core table.
  - Limited to AI and template provenance in v1. Future source types (import, migration) require adding enum values. VARCHAR enum is migration-friendly.
  - Provenance depth is one hop (execution ID only). Full chain requires FK traversal. Acceptable for the use case -- the compliance officer clicks through to the execution detail, not the gate detail.

- Neutral:
  - The `source` field is a VARCHAR enum, not a database enum, following the convention from `AiFirmProfile.risk_calibration`. Valid values: `MANUAL`, `AI_GENERATED`, `TEMPLATE_GENERATED`.
  - `ai_execution_id` is nullable -- only AI-generated documents have an execution reference. Non-AI documents have NULL.
  - The migration adds the columns to the existing `documents` table in V127 (alongside the compliance audit tables). `source` has a NOT NULL constraint with DEFAULT `'MANUAL'` so existing rows are backfilled automatically. `ai_execution_id` is nullable (only set for AI-generated documents). Partial indexes on both columns avoid bloating the index for the majority of manually-created documents.
  - `AiReviewReportGenerator` and `AiDraftDocumentGenerator` are responsible for setting `source` and `ai_execution_id` when creating documents. No changes to existing document creation paths.

- Related: [ADR-288](ADR-288-contract-review-document-as-report.md) (contract review produces a Document -- needs provenance), [ADR-289](ADR-289-template-guided-drafting-over-freeform.md) (drafting produces a Document -- needs provenance), [ADR-281](ADR-281-execution-gate-pattern-attorney-liability.md) (execution gates -- the approval chain is part of the provenance trail), [ADR-284](ADR-284-document-reading-s3-vision-no-vector-store.md) (document reading -- the reviewed document is input, the report document is output)
