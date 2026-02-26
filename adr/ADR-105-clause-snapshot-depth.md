# ADR-105: Clause Snapshot Depth

**Status**: Accepted

**Context**:

When a document is generated with clauses, the system records a `GeneratedDocument` entity with metadata about the template, entity, file location, and context snapshot. Phase 27 adds clause selection to this pipeline: users pick and reorder clauses at generation time, and the rendered PDF includes clause content. The question is how much clause information to persist alongside the `GeneratedDocument` for traceability and audit purposes.

The traceability requirement comes from the professional services domain: firms need to know which clauses appeared in a generated engagement letter or statement of work. If a dispute arises about terms, the firm must be able to identify which clauses were included and what they said. The rendered PDF itself is the authoritative legal document — it contains the actual clause text as rendered at generation time.

Phase 27 explicitly excludes clause version history (no `clauseVersion` column, no change tracking on `DocumentClause`). Clauses are mutable: admins edit them in-place. Version tracking is a future consideration. This constraint affects which snapshot options provide meaningful value in v1.

**Options Considered**:

1. **Metadata only (chosen)** -- Store an array of `{clauseId, slug, title, sortOrder}` in a JSONB column on `GeneratedDocument`. The rendered PDF itself contains the actual clause text. No clause body text stored separately in the database.
   - Pros:
     - Minimal storage overhead: metadata is ~200 bytes per clause vs. 500-2000 bytes per clause body.
     - The PDF IS the authoritative snapshot — it preserves the exact rendered output including Thymeleaf-resolved variables, CSS styling, and clause ordering.
     - Simple schema: one JSONB array column, no additional tables or relationships.
     - Sufficient for the primary query: "which clauses were included in this document?" — answered by reading the metadata array.
     - Does not close any doors: a future versioning phase can enrich the JSONB structure without schema migration (just richer objects in the same column).
   - Cons:
     - Cannot answer "what did clause X say when document Y was generated?" without opening the PDF. The raw Thymeleaf source of the clause at generation time is not stored.
     - No programmatic diffing capability: cannot compare "current clause body vs. what was in the document" without PDF text extraction.

2. **Full body snapshot** -- Store the complete clause body text (pre-rendering Thymeleaf source) alongside metadata in the JSONB column: `{clauseId, slug, title, sortOrder, body}`.
   - Pros:
     - Can reconstruct exactly what Thymeleaf source went into a document — useful for "did this clause change since generation?" comparisons.
     - Enables programmatic diffing between current clause content and the version used at generation time.
     - Self-contained: no need to open the PDF to understand what clause text was included.
   - Cons:
     - Significant storage increase: each clause body is 500-2000 characters of HTML/Thymeleaf. A document with 8 clauses adds 4-16KB to the JSONB column, multiplied by every generated document. For a firm generating 50 documents/month, that is 2.4-9.6MB/year of clause body text alone.
     - Duplicates information already in the rendered PDF — the PDF contains the resolved output, and the snapshot stores the unresolved source. Neither is truly the "canonical" version.
     - Without version tracking (explicitly excluded from v1), the diff capability has limited utility. Knowing "the clause changed since generation" is only useful if you can see the history of changes, which v1 does not provide.
     - The stored body is pre-rendering Thymeleaf source, not the resolved HTML. To see what the user actually saw, you still need the PDF.

3. **Content hash** -- Store metadata plus a SHA-256 hash of each clause body: `{clauseId, slug, title, sortOrder, bodyHash}`. Enables "has this clause changed since generation?" without storing full text.
   - Pros:
     - Low storage: adds only 64 characters (hex-encoded SHA-256) per clause.
     - Enables change detection: compare `bodyHash` against `SHA256(currentClause.body)` to flag "clause has been modified since this document was generated."
     - Good middle ground between metadata-only and full body snapshot.
   - Cons:
     - Cannot reconstruct original content from hash — change detection without version history is not very useful in v1. The system can say "this clause changed" but cannot say "here is what it said before."
     - Adds complexity for marginal benefit: hashing logic in the snapshot builder, comparison logic in the UI, and the result is a boolean "changed/unchanged" flag with no actionable next step (since there is no version history to revert to or diff against).
     - The hash does not account for Thymeleaf rendering context — two documents using the same clause body but different entity data would have the same hash, even though the rendered output differs.

**Decision**: Option 1 -- Metadata only.

**Rationale**:

The platform is not yet in production, and v1 explicitly excludes clause version history. The rendered PDF preserves the authoritative content of each clause as it was at generation time — including resolved Thymeleaf variables, applied CSS, and clause ordering. The PDF is the legal document, not the database record.

Option 2 (full body snapshot) stores the Thymeleaf source, not the rendered output. This means it captures `${customer.name}` rather than "Acme Corp" — the pre-rendering source is not what the user saw or what has legal weight. The storage cost is non-trivial for data that duplicates (in a less useful form) what the PDF already contains. The diff capability it enables is premature without version tracking: knowing a clause changed is only useful if you can see the change history and revert or re-generate.

Option 3 (content hash) adds complexity for a feature — change detection — that has no actionable outcome in v1. The system could display a "clause modified since generation" badge, but the user cannot act on it (no version history to view, no "regenerate with original clause" capability). This is engineering overhead for a UI element that raises questions the system cannot answer.

Metadata-only satisfies the actual v1 requirement: "which clauses were included in this document?" The answer is an array of `{clauseId, slug, title, sortOrder}`. If a future phase adds clause versioning, the JSONB column can be enriched with `bodyHash`, `body`, or `versionId` without a schema migration — just richer objects in the same array.

**Consequences**:

- Positive:
  - `GeneratedDocument` gets a `clauseSnapshots` JSONB column: `[{clauseId, slug, title, sortOrder}, ...]`. Minimal storage, simple to query.
  - Storage remains efficient even with heavy document generation. No clause body duplication in the database.
  - The JSONB structure is forward-compatible: adding `bodyHash`, `body`, or `versionId` fields in a future phase requires no schema migration.
  - Audit events for document generation can reference clause IDs from the snapshot without additional lookups.

- Negative:
  - "What did this clause say when document Y was generated?" requires downloading and viewing the PDF. No programmatic access to historical clause content.
  - No change detection: cannot flag "clause X has been modified since this document was generated" without opening and comparing PDFs.
  - If a clause is deleted, the snapshot retains its ID and slug but the referenced entity no longer exists. The metadata becomes a tombstone reference — sufficient for audit trails but not for reconstruction.

- Neutral:
  - The `clauseSnapshots` column is nullable — documents generated without clauses (pre-Phase 27 or clause-free generations) have null, not an empty array.
  - The snapshot is written once at generation time and never updated. No batch-refresh logic (unlike ADR-103's tax rate snapshots on DRAFT invoices).

- Related: [ADR-104](ADR-104-clause-rendering-strategy.md) (how clauses are rendered into documents), [ADR-058](ADR-058-rendering-context-assembly.md) (context builder pattern for document generation).
