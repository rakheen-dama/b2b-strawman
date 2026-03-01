# ADR-127: Portal Proposal Rendering

**Status**: Accepted

**Context**:

Proposals contain a rich text body stored as Tiptap JSON in the `contentJson` JSONB column. When a portal contact views a proposal in the portal, this content must be rendered into a human-readable format. The portal is a separate Next.js application that currently renders projects, invoices, documents, and acceptance requests. The question is how to render proposal content in the portal: client-side with the Tiptap editor library, server-side with pre-rendered HTML stored in the portal read-model, or as a PDF download only.

A critical property of proposals is that they are **immutable after sending**. Once a proposal transitions from DRAFT to SENT, its `contentJson` cannot be modified. This means the rendered output for a sent proposal will never change — the same Tiptap JSON always produces the same HTML. This immutability property is important because it determines whether freshness is a concern for the rendering strategy.

The proposal body includes variable references (e.g., `{{client_name}}`, `{{fee_total}}`) that must be resolved with actual values before the client sees the proposal. In the firm-side frontend, these variables are resolved client-side using the Tiptap editor's preview mode. In the portal, the rendering approach determines where variable resolution happens.

The existing portal read-model pattern (Phase 7, Phase 22, Phase 28) stores denormalized data in the `portal` schema. For documents, the portal stores S3 keys and serves files directly. For acceptance requests, the portal stores status and metadata. In both cases, the firm-side application is the source of truth, and the portal schema stores a read-optimized projection.

**Options Considered**:

1. **Client-side Tiptap rendering in the portal** -- Store the raw `contentJson` (Tiptap JSON) in the portal read-model. The portal Next.js app includes the Tiptap editor library in read-only mode and renders the JSON client-side. Variable resolution happens in the browser using context data fetched from the portal API.
   - Pros:
     - Rendering is always fresh — changes to the Tiptap rendering engine automatically apply to portal views
     - No server-side rendering step needed in the sync pipeline
     - The portal read-model stores only the raw JSON (smaller payload than rendered HTML)
   - Cons:
     - Requires adding Tiptap as a dependency to the portal app (~200KB+ JavaScript bundle for the editor, extensions, and StarterKit)
     - Variable resolution must happen client-side, requiring the portal API to expose variable context data (customer name, org name, fee amounts)
     - Custom Tiptap nodes (variable chips, loop tables from Phase 31) must be registered in the portal app — tight coupling to the firm-side editor configuration
     - If custom node types are added in the firm app but not the portal, rendering breaks silently
     - Portal contacts with slow connections or disabled JavaScript cannot view proposals

2. **Server-side rendered HTML in the portal read-model** -- On send, the backend renders the Tiptap JSON to HTML using `TiptapRenderer` (from Phase 31), resolves all variables with actual values, and stores the resulting HTML string in `portal_proposals.content_html`. The portal app receives pre-rendered HTML and displays it within a branded wrapper.
   - Pros:
     - No Tiptap JavaScript dependency in the portal app — just sanitized HTML rendering
     - Variables are resolved once at send time using the backend's variable context (customer, org, fee data) — no client-side resolution needed
     - Immutability guarantee: the HTML is rendered at send time and never changes, which is correct because proposals are immutable after SENT
     - Faster portal page load — no JSON parsing, no editor initialization, no variable resolution
     - Portal rendering is resilient: HTML works with JavaScript disabled, works on slow connections, works with any browser
     - Decoupled: if the firm app changes its Tiptap node types, already-sent proposals in the portal are unaffected
   - Cons:
     - Larger payload in the portal read-model (HTML is typically 2-5x larger than the Tiptap JSON)
     - The HTML snapshot is frozen at send time — if a rendering bug is fixed in `TiptapRenderer`, already-sent proposals keep the old rendering (acceptable: proposals are immutable)
     - Requires the `TiptapRenderer` to be invoked during the send flow, adding latency to the send action (~50-100ms)

3. **PDF-only rendering** -- On send, render the Tiptap JSON to PDF using the existing Tiptap → HTML → OpenHTMLToPDF pipeline. Store the PDF in S3 and serve it through the portal as a downloadable/viewable document (same as generated documents).
   - Pros:
     - Reuses the full PDF generation pipeline from Phase 12/31
     - PDF is a familiar format for professional documents (proposals, engagement letters)
     - No portal rendering complexity — just an iframe or download link
   - Cons:
     - Breaks the interactive portal experience — accept/decline buttons cannot be embedded in a PDF viewer
     - The portal proposal page would be split: PDF viewer for content + separate section for fee summary and action buttons — disjointed UX
     - PDF generation is slower (~500ms-2s) than HTML rendering (~50ms)
     - PDFs are not responsive — poor experience on mobile devices
     - Cannot display dynamic elements like milestone tables or fee summaries within the document flow

**Decision**: Option 2 -- Server-side rendered HTML in the portal read-model.

**Rationale**:

The immutability property of sent proposals makes server-side rendering the natural choice. Once a proposal is sent, its content never changes. Rendering to HTML at send time captures a perfect snapshot that the portal can serve forever without re-rendering. This is the same conceptual pattern as generating a PDF — capturing the document at a point in time — but with HTML instead of PDF, which enables an interactive portal experience with inline accept/decline buttons, fee summaries, and milestone tables.

The alternative — shipping the Tiptap editor library to the portal — adds significant bundle size and tight coupling between the firm-side editor configuration and the portal rendering. Every custom Tiptap node (variable chips, loop tables, clause blocks) would need to be registered in both applications. If a new node type is added to the firm app, the portal would need to be updated simultaneously, or proposals using that node would render incorrectly. Server-side rendering eliminates this coupling entirely: the backend's `TiptapRenderer` is the single source of truth for how Tiptap JSON becomes HTML.

Variable resolution is another compelling argument. The backend has direct access to all context data (customer name, org name, fee amounts, portal contact details) at send time. Client-side resolution would require exposing this data through the portal API and implementing variable substitution logic in JavaScript — duplicating work that the backend already does.

The HTML payload size increase (2-5x over raw JSON) is acceptable. A typical proposal body renders to 10-50KB of HTML. The portal schema already stores multi-KB payloads (document metadata, invoice line items, acceptance request details). The storage cost is negligible.

Option 3 (PDF-only) was rejected because it breaks the interactive portal experience. Proposals need inline accept/decline buttons, dynamic fee summaries, and a responsive layout — none of which are possible within a PDF viewer. PDF export can be added as an optional feature later without affecting the core portal rendering.

**Consequences**:

- `ProposalPortalSyncService` calls `TiptapRenderer.renderToHtml(contentJson, variableContext)` during the send flow and stores the result in `portal_proposals.content_html`
- Variable resolution happens once at send time — the stored HTML contains resolved values, not variable placeholders
- The portal app renders `content_html` as sanitized HTML inside a branded wrapper (org logo, brand color)
- No Tiptap JavaScript dependency in the portal app — reduces bundle size and decouples from firm-side editor configuration
- Already-sent proposals retain their rendered HTML even if `TiptapRenderer` is updated — this is correct behavior, not a bug
- The send action incurs ~50-100ms additional latency for HTML rendering (acceptable for a user-initiated action)
- If proposal PDF export is added later, it will use the same `TiptapRenderer` output fed into OpenHTMLToPDF — the pipeline is already in place
- Related: [ADR-119](ADR-119-editor-library-selection.md), [ADR-120](ADR-120-document-storage-format.md), [ADR-124](ADR-124-proposal-storage-model.md)
