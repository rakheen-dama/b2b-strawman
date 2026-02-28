# ADR-119: Editor Library Selection

**Status**: Accepted

**Context**:

DocTeams needs a rich text editor for document templates and clauses, replacing raw HTML/Thymeleaf editing. The editor must support custom node types (variable chips, clause blocks, loop tables), work with Shadcn UI, serialize to/from JSON, and be MIT licensed. It is used for both template authoring (full documents) and clause editing (fragments). The same editor component will be reused in Phase 32 (Proposals).

**Options Considered**:

1. **Tiptap (ProseMirror-based)** -- MIT license, headless (bring-your-own-UI), largest ProseMirror ecosystem. Used by Notion, GitLab, Linear. Custom node API via extensions. React integration via @tiptap/react. Serializes to/from JSON natively.
   - Pros:
     - MIT license -- no commercial licensing concern
     - Massive ecosystem: hundreds of community extensions, 10+ years of ProseMirror production use underneath
     - Custom node support is first-class: extensions expose `addNodeView()` and `addInputRules()` for variable chips, clause blocks, and loop table nodes
     - Headless by design -- no default styling conflicts with Shadcn UI; toolbar is fully owned by the application
     - JSON serialization is native and lossless: `editor.getJSON()` / `editor.commands.setContent(json)` round-trip without transformation
     - Active maintenance, large community, used by major products (Notion, GitLab, Linear, Loom)
     - Tree-shakeable: only extensions imported are bundled
   - Cons:
     - ProseMirror schema and transaction model has a learning curve for custom node development
     - Bundle size: ~150KB gzipped for a full setup with common extensions
     - v2 API is still evolving in some areas; occasional breaking changes between minor versions

2. **BlockNote** -- Built on ProseMirror/Tiptap, provides pre-built block-style UI. Custom blocks API.
   - Pros:
     - Notion-like block UX out of the box with slash commands and block handles
     - Custom block support built on the same Tiptap extension system
     - Reuses the ProseMirror/Tiptap ecosystem
   - Cons:
     - AGPL license -- requires open-sourcing the SaaS product or purchasing a commercial license; non-starter for a commercial B2B product
     - Opinionated block-centric UI conflicts with Shadcn design system; significant effort to suppress or replace default styling
     - Block-centric data model is less suited to inline nodes (variable chips must live inside block paragraphs, not as top-level blocks)
     - Smaller community than raw Tiptap; custom node patterns at the complexity required (recursive clause blocks, loop tables) have fewer references

3. **Plate (Slate-based)** -- React-first editor framework using Slate.js. Plugin ecosystem and Shadcn-compatible UI primitives via plate-ui.
   - Pros:
     - React-native architecture with hooks and context
     - Clean Slate data model: documents are plain JSON arrays of nodes
     - plate-ui provides Shadcn-compatible primitives, reducing toolbar implementation effort
     - Growing plugin ecosystem
   - Cons:
     - Smaller ecosystem than ProseMirror; fewer proven custom node patterns at the complexity required (recursive clause blocks, loop tables with per-row variable expansion)
     - Slate's data model has changed between major versions, creating migration risk
     - Fewer production references at the scale and complexity of structured document authoring
     - plate-ui components are pre-styled opinionated primitives -- integration with an existing Shadcn design system requires more care than a fully headless approach

4. **Markdown + Handlebars** -- Store templates as Markdown with Handlebars expressions. Render via marked + handlebars.js. Editor is a plain `<textarea>` or a lightweight Markdown editor (e.g., CodeMirror).
   - Pros:
     - Simple storage format: human-readable, diffable, no proprietary schema
     - No heavy editor library dependency -- CodeMirror or a `<textarea>` suffices
     - Handlebars is expression-safe by design (no arbitrary code execution, logic-less by default)
     - Easiest implementation path for basic variable substitution
   - Cons:
     - Markdown is lossy for rich document formatting: tables with per-cell styling, precise indentation, custom block types, and multi-column layouts do not round-trip cleanly
     - No WYSIWYG for variable chips or clause blocks -- authors see raw `{{variableName}}` syntax rather than a rendered chip; UX is poor for non-technical users
     - Clause blocks (reusable fragments with their own variable scopes) do not fit naturally into Markdown -- would require custom syntax that diverges from standard Markdown parsers
     - Loop tables (one row per collection item, with per-cell variable expansion) require complex custom Handlebars helpers and special rendering logic
     - PDF output quality is lower: Markdown-to-HTML-to-PDF pipelines produce generic formatting without document-level precision control
     - Format mismatch: Phase 12 already established OpenHTMLToPDF as the PDF engine; feeding it styled HTML from a WYSIWYG editor produces better results than Markdown-to-HTML conversion

**Decision**: Option 1 -- Tiptap (ProseMirror-based).

**Rationale**:

The primary hard constraint is MIT licensing. BlockNote's AGPL license is a non-starter for a commercial SaaS product without a commercial license agreement. This eliminates Option 2 immediately.

Among the remaining options, Tiptap's headless architecture is the decisive advantage for this codebase. DocTeams uses Shadcn UI throughout; a headless editor means the toolbar, bubble menu, and floating menu are React components built with Shadcn primitives -- no style overrides, no CSS specificity battles. Plate offers similar headless potential via plate-ui but requires more integration work to align with an existing design system, and its Slate foundation has a smaller ecosystem for the specific custom node patterns required here.

The three custom node types driving the complexity decision are:

- **Variable chips**: Inline, non-editable atoms that display `{{variableName}}` as a styled chip. ProseMirror's node view API (`addNodeView()`) handles this natively with a React component. Slate can also do this, but with fewer community examples at this pattern.
- **Clause blocks**: Block-level nodes that wrap a subtree of content and carry metadata (clause ID, conditional expression). ProseMirror's schema allows nesting arbitrary content inside custom block nodes with strict validation. Slate's recursive tree is more permissive but less constrainable.
- **Loop tables**: Table nodes that iterate over a collection variable, rendering one row per item with per-cell variable chips. ProseMirror's table extension (`@tiptap/extension-table`) provides the structural foundation; custom node views handle the loop metadata. No equivalent exists in the Plate ecosystem at this complexity.

Markdown was rejected because it cannot represent variable chips, clause blocks, or loop tables as first-class structural elements. Authors would work with raw syntax rather than a WYSIWYG representation, which is incompatible with the non-technical end-user personas (account managers, paralegals) who will author templates.

JSON serialization is a requirement for lossless storage and re-editing. Tiptap's native `getJSON()` / `setContent()` round-trip is deterministic and schema-validated. Markdown round-trip is not lossless for the custom node types required.

**Consequences**:

- Frontend gains `@tiptap/react`, `@tiptap/starter-kit`, and selected `@tiptap/extension-*` packages as new dependencies (~150KB gzipped for a representative extension set)
- Custom node development requires familiarity with the ProseMirror schema and node view API; this is a one-time learning investment shared across all custom node implementations
- The `EditorComponent` is designed as a reusable React component with an extension prop, usable for template editing (full documents), clause editing (fragments), and Phase 32 proposals (contract composition) without duplication
- Editor routes (template editor, clause editor) are dynamically imported (`next/dynamic`) to avoid loading the editor bundle on non-authoring pages -- no impact on dashboard or list page performance
- Lock-in: Tiptap JSON becomes the canonical storage format for template and clause content. Migration away from Tiptap would require a format conversion pass over all stored templates. This is an acceptable trade-off given Tiptap's active maintenance and ecosystem stability.
- ADR-057 (template storage -- database) is unaffected: the stored JSON blob format is now explicitly Tiptap JSON rather than an unspecified rich text format
- Related: [ADR-057](ADR-057-template-storage-database.md) (template storage model), [ADR-120](ADR-120-document-storage-format.md) (storage format depends on editor choice)
