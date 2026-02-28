# ADR-123: Template-Clause Association Source of Truth

**Status**: Accepted

**Context**:

Document templates can include clauses (legal terms, conditions, etc.). Currently, the `template_clauses` join table is the authoritative source for which clauses belong to a template, their order, and whether they are required. The template editor exposes a separate "Clauses" tab where users manage these associations independently from the template content.

Phase 31 introduces `clauseBlock` nodes directly within the Tiptap JSON document tree. In the new editor, clauses are positioned precisely within the document as visible block elements rather than dumped at a placeholder position. This creates a dual-source problem: the document JSON contains `clauseBlock` nodes (with `clauseId`, ordering implied by document position, and a `required` flag), while the `template_clauses` table contains the same information. Both claim to describe which clauses belong to the template, in what order, and with what required status. Which is authoritative?

The system also has a `ClauseResolver` service that reads `template_clauses` to determine default clause selections during document generation, and a "Used in N templates" reverse-lookup in the clause library that relies on joins against the same table.

**Options Considered**:

1. **Document JSON is primary, table synced on save** -- The `clauseBlock` nodes in the Tiptap document are the clause configuration. On template save, the backend extracts all `clauseBlock` nodes from the document JSON, diffs against the current `template_clauses` rows, and creates, deletes, or reorders rows to match. The table becomes a materialized query index.
   - Pros:
     - WYSIWYG consistency: what the user sees in the editor is the clause configuration, with no hidden state in a separate tab
     - Single source of truth (the document) eliminates state divergence after save
     - Natural UX: dragging a clause block reorders it; deleting a clause block removes it -- no separate association management
     - `ClauseResolver` continues to work unchanged (reads from the synced table)
     - "Used in N templates" reverse-lookup remains efficient (join table with `clauseId` index)
   - Cons:
     - Sync logic runs on every save (additional complexity in the save path)
     - Table is momentarily stale between saves; direct table manipulation via `TemplateClauseController` could desync from the document
     - `ClauseResolver` reads stale data if a user has added a clause block but not yet saved

2. **Table remains primary, document reflects table** -- Keep `template_clauses` as the source of truth. `clauseBlock` nodes in the document are decorative and display-only. The editor reads from the table to know which clause blocks to show. Adding or removing a clause block in the editor triggers an immediate API call to the `TemplateClause` endpoints.
   - Pros:
     - No migration of association logic; `ClauseResolver` and all downstream consumers are unchanged
     - Immediate persistence -- no save action required to commit a clause change
     - Existing `TemplateClauseController` API remains fully functional and canonical
   - Cons:
     - Document and table can desync (block present in document but not in table, or vice versa)
     - UX is confusing: moving a clause block in the editor does not reorder the association unless an API call is also made, so the document position and the stored order diverge
     - Every clause interaction triggers a network call, degrading editor responsiveness
     - Defeats the purpose of unified editing -- clauses are still managed as a separate concern

3. **Document JSON only, table deleted** -- Remove the `template_clauses` table entirely. All clause association queries use JSONB containment queries on the document content column. `ClauseResolver` reads clause blocks directly from the document JSON.
   - Pros:
     - True single source of truth with no sync logic
     - Simpler schema; no dual-state bugs possible
   - Cons:
     - JSONB queries are slower than indexed table joins for "find templates using clause X"
     - `ClauseResolver` must parse document JSON on every generation, replacing a simple indexed table query with a JSON walk
     - Existing `TemplateClauseController` endpoints break, requiring removal or full rewrite
     - Generation dialog pre-population (which reads `template_clauses`) must be rewritten
     - No efficient index for clause-to-template reverse lookups; the "Used in N templates" count degrades to a full-scan JSONB containment query

**Decision**: Option 1 -- Document JSON is primary, table synced on save.

**Rationale**:

The core UX principle of Phase 31 is that the editor document is the document. There is no hidden state in a separate tab or association table. When a user drags a clause block from position three to position one, that drag is the reorder. When they delete a clause block, that deletion is the removal. Any design that requires the user to also take an action in a separate "Clauses" tab to persist what they just did in the editor undermines this principle.

Keeping the `template_clauses` table as a synced materialized view preserves the table's value for downstream query patterns without making it authoritative:

- `ClauseResolver` reads the table to determine default clause selections during generation. This is a hot path that should remain a simple indexed table query, not a JSONB parse.
- The clause library's "Used in N templates" indicator requires a reverse lookup from a `clauseId` to all templates containing it. This is efficient on a join table with a `clauseId` index and would be a full-scan JSONB containment query without the table.
- The generation dialog pre-populates from `template_clauses`. Keeping this working avoids rewriting the generation flow in Phase 31.

The sync algorithm on save is straightforward and bounded: walk the document JSON tree once to collect all `clauseBlock` nodes with their attributes (`clauseId`, document position as `sortOrder`, `required` flag); load the current `template_clauses` rows for the template; diff the two sets; delete removed associations, insert added ones, update changed `sortOrder` or `required` values. This diff-and-flush runs in a single transaction with the template content save, so the table is always consistent with the saved document.

The `TemplateClauseController` direct-manipulation endpoints (`POST /clauses`, `DELETE /clauses/{id}`) become deprecated in Phase 31. They remain functional for backward compatibility but the frontend stops invoking them in favour of document editing. Callers that write directly to the table without also updating the document JSON will find their changes overwritten on the next template save; this is documented behaviour for the deprecated API.

Option 3 (table deleted) was rejected because JSONB containment queries cannot be made as efficient as indexed table joins for the reverse-lookup case, and `ClauseResolver` is called in the document generation hot path where parse cost matters. Option 2 (table primary) was rejected because it makes every clause interaction a network round-trip and produces a document whose visual state can silently disagree with the persisted association order.

**Consequences**:

- Template save triggers a `TemplateClauseSync` step: extract `clauseBlock` nodes from document JSON, diff against current `template_clauses` rows, flush creates/updates/deletes in the same transaction
- `template_clauses` rows are eventually consistent with the document JSON; between saves the table may be stale -- acceptable for all current consumers (`ClauseResolver`, "Used in N templates" count, generation dialog)
- `ClauseResolver` is unchanged; it continues to read `template_clauses` with no modifications
- The clause library's "Used in N templates" reverse-lookup continues to use a `template_clauses` join with a `clauseId` index
- `TemplateClauseController` direct-manipulation endpoints (`POST`, `DELETE`) are deprecated; changes made through them will be overwritten on next template save; this is documented in the controller and in the API reference
- The separate "Clauses" tab in the old template editor is removed; clause management is now inline in the document editor
- Future consideration: if the generation dialog ever supports per-generation clause customisation that is not saved back to the template, `template_clauses` (or a generation-scoped variant) remains the correct persistence layer for that transient override state
- Related: [ADR-119](ADR-119-editor-library-selection.md), [ADR-120](ADR-120-document-storage-format.md)
