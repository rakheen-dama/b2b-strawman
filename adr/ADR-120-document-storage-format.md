# ADR-120: Document Storage Format

**Status**: Accepted

**Context**:

Document templates and clauses currently store content as TEXT columns containing Thymeleaf HTML. Phase 31 replaces the editor with Tiptap (ProseMirror-based, per ADR-119). We need to decide how to store document content — the format must support lossless editor round-trips, accommodate custom node types (variables, clause blocks, loop tables), and be efficiently queryable for operations like "find all templates using variable X" or "find all templates containing clause Y".

This is a multi-tenant SaaS (schema-per-tenant) with PostgreSQL 16. Templates and clauses are tenant-scoped entities.

**Options Considered**:

1. **Tiptap JSON stored as JSONB** -- Store the native Tiptap document JSON in a JSONB column. The editor serializes to this format natively. Custom nodes (variable, clauseBlock, loopTable) are typed JSON objects within the document tree.
   - Pros:
     - Lossless round-trip: the editor reads and writes the same JSON structure with no intermediate parsing
     - Structured and queryable: JSONB operators (`@>`, `jsonb_path_query`) and GIN indexes support variable-usage and clause-reference lookups
     - Custom nodes are first-class typed objects within the tree -- no fragile HTML encoding or data-attribute conventions
     - No format translation layer between editor and storage
     - JSONB validation at the database level rejects malformed documents at write time
   - Cons:
     - JSON is less human-readable than HTML when inspecting raw data
     - Storage footprint is 20-40% larger than minified HTML for equivalent content
     - Backend must implement a TiptapRenderer that walks the JSON tree to produce HTML for PDF generation

2. **HTML stored as TEXT (keep current format, overlay WYSIWYG editor)** -- Keep HTML storage, use Tiptap's HTML import/export. Custom nodes stored as data-attributed HTML elements (e.g., `<span data-variable="customer.name">`).
   - Pros:
     - Human-readable and inspectable without tooling
     - Existing Thymeleaf renderer can be partially reused
     - Smaller migration surface: no column type change for templates that remain unconverted
   - Cons:
     - HTML→Tiptap→HTML round-trips are lossy: the editor normalizes whitespace, reorders attributes, and drops empty elements, producing phantom diffs on every save
     - Custom nodes must be serialized and deserialized as HTML, which is fragile -- attribute ordering, escaping, and nesting rules are not enforced by the format
     - HTML parsing is required on both client and server side
     - Tiptap's HTML export does not guarantee exact input preservation; behavior may change across Tiptap versions
     - SSTI surface remains if any code path interprets the stored HTML as a Thymeleaf template

3. **Markdown with front-matter metadata** -- Store as Markdown with YAML front-matter for structural metadata. Variables represented as `{{customer.name}}`, clause markers as `<!-- clause:slug -->` comments.
   - Pros:
     - Human-readable and diffable in version control or logs
     - Small storage footprint
     - Familiar syntax for technical users reviewing raw content
   - Cons:
     - Markdown is lossy for rich formatting: cell merging, precise column widths, and mixed inline styles have no standard representation
     - No native WYSIWYG round-trip: Tiptap's Markdown extension is not a first-class serializer and requires custom extensions for every custom node
     - Loop tables and clause blocks require non-standard syntax not supported by any Markdown renderer
     - Poor fit for structured documents with mixed content types (tables, signature blocks, conditional sections)

4. **Portable document JSON (custom schema)** -- Design a custom JSON schema independent of Tiptap's internal format, with a bidirectional converter to and from Tiptap's document model.
   - Pros:
     - Editor-agnostic: replacing Tiptap with another editor would not require a storage migration
     - Full control over the schema and query-optimized field layout
   - Cons:
     - Requires a bidirectional converter with full fidelity for all node types; any gap is a data-loss bug
     - Translation layer adds latency and failure modes on every read and write
     - All custom nodes require dual representation (Tiptap format and custom format)
     - No ecosystem tooling supports the custom format; debugging and introspection require bespoke tooling
     - Premature abstraction: there is no current plan to replace Tiptap, and the Tiptap JSON format is simple enough to convert programmatically if a migration ever becomes necessary

**Decision**: Option 1 -- Tiptap JSON stored as JSONB.

**Rationale**:

The lossless round-trip property is the deciding factor. HTML round-trips through Tiptap are inherently lossy: the editor normalizes whitespace, reorders attributes, and may drop empty elements. This creates phantom diffs where content appears changed on every save even when the user made no edits. Over time, phantom diffs corrupt document history and undermine user trust in the editor.

JSONB gives structured queryability that TEXT cannot provide. Finding all templates that reference a specific clause or variable requires a `@>` containment query or `jsonb_path_query` against the JSONB column, both of which can be accelerated with a GIN index. The equivalent operation against an HTML TEXT column requires either a full-text substring scan or a separate denormalized index table maintained by the application.

Custom nodes (variables, clause blocks, loop tables) are typed JSON objects within the Tiptap document tree. They serialize and deserialize without any encoding convention: the node type, attributes, and children are explicit fields in the JSON. In contrast, HTML encoding requires inventing and maintaining a data-attribute convention for each node type, and any inconsistency between the serializer and the deserializer is a silent data-loss bug.

The custom JSON schema (Option 4) is premature abstraction. Tiptap was chosen specifically for its ProseMirror ecosystem, and decoupling storage from the editor format introduces a translation layer with no current benefit. If a future editor migration becomes necessary, the Tiptap JSON structure is simple and well-documented enough to convert programmatically as part of that migration.

JSONB storage is 20-40% larger than minified HTML, but document templates are small (typically under 50KB) and few per tenant. The storage cost is negligible relative to the operational benefits.

**Consequences**:

- `document_templates.content` column changes from TEXT to JSONB (V48 migration, with `legacy_content` TEXT column preserving original HTML for any org-custom content that cannot be programmatically converted)
- `clauses.body` column changes from TEXT to JSONB (V48 migration, with `legacy_body` TEXT column for the same reason)
- Backend `TiptapRenderer` walks the JSONB document tree to produce HTML for PDF generation via the existing OpenHTMLToPDF pipeline
- Frontend reads and writes the JSONB content directly to and from the Tiptap editor instance with no intermediate parsing step
- GIN index on `document_templates.content` and `clauses.body` enables efficient JSONB containment queries for variable-usage and clause-reference lookups
- JSONB validation at write time rejects structurally malformed documents before they reach the rendering pipeline
- Future features enabled by structured storage: variable-usage analytics, clause dependency graphs, structured content diffing between template versions
- Related: [ADR-119](ADR-119-editor-library-selection.md) (editor choice that makes JSONB the natural storage format), [ADR-121](ADR-121-rendering-pipeline-architecture.md) (how the backend renders JSONB to HTML for PDF), [ADR-122](ADR-122-content-migration-strategy.md) (migration approach for existing TEXT content)
