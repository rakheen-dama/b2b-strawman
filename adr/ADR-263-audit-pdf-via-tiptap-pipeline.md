# ADR-263: PDF Export Rides the Existing Tiptap → PDF Pipeline

**Status**: Accepted

**Context**:

Phase 69 ships compliance-grade PDF export of the audit log. The PDF is the *legal artefact* — what gets attached to a subpoena response, what an auditor reviews, what a regulator inspects. CSV is for internal review and re-import; PDF is the formal record. Both are mandatory deliverables of the phase.

Kazi already has a Tiptap → PDF pipeline used for invoices ([Phase 10](../architecture/phase10-invoicing-billing.md)), document templates ([Phase 12](../architecture/phase12-document-templates.md), [Phase 31](../architecture/phase31-templates-and-partials.md)), proposal documents (Phase 32), engagement letters, statements of account ([ADR-250](ADR-250-statement-of-account-template-and-context.md)), and more. The pipeline is established: a Tiptap JSON document template, a context-binding step, and a render step that produces PDF bytes via the engine selected in [ADR-056](ADR-056-pdf-engine-selection.md) and [ADR-165](ADR-165-pdf-conversion-strategy.md). The infrastructure is mature, has been through multiple PDF-engine iterations, and is the standard for every document-shaped output Kazi produces.

The audit log PDF is structurally a long table with a branded header and a footer page-numbering convention — the same shape as a long invoice or a long matter statement. The question is whether to render it through the existing pipeline or introduce dedicated PDF tooling.

**Options Considered**:

1. **Introduce a dedicated PDF library (e.g. iText, Apache PDFBox).** Add a new dependency. Build a `AuditPdfWriter` class that constructs the PDF imperatively, page by page, table-row by table-row.
   - Pros:
     - Direct control over PDF structure: page breaks, repeated table headers, font metrics, embedded TrueType, exact pixel placement. Useful for compliance documents where layout precision matters.
     - Some PDF libraries (iText) have first-class table support that handles row-spanning across page breaks more robustly than HTML/CSS-driven engines.
     - No template authoring overhead — the PDF is built in code.
   - Cons:
     - Adds a meaningful dependency. iText has a complex licence (AGPL with a commercial alternative); PDFBox is Apache-licensed but lower-level. Either way, the codebase grows a second PDF-generation path.
     - Two PDF code paths to maintain. Invoices, proposals, statements all go through Tiptap → PDF; audit exports would go through the dedicated library. Engineers reading the codebase have to know which code path applies to which output.
     - Reinvents the chunked-output / streaming infrastructure that the Tiptap pipeline already has, since a 10k-row PDF should not buffer in memory.
     - Audit-export PDFs have the same shape as long-table financial documents (long invoices, statements of account). Building a parallel infrastructure for the same shape is duplication.

2. **Reuse the Tiptap → PDF pipeline with a new template (CHOSEN).** Author a new Tiptap template `audit-export.tiptap.json` under the existing template pack. Bind audit-event rows + filter summary + branding as the context. Render through the existing engine.
   - Pros:
     - Zero new infrastructure. Same template authoring conventions, same engine, same chunked-output streaming, same branding integration (org logo, tenant name) that every other Kazi PDF uses.
     - The audit PDF inherits all the Phase 12 / 31 / 42 hardening — the engine has been through compatibility iterations and is the codebase standard.
     - One PDF generation path. Adding a new document shape (e.g. a future Phase 75 trust ledger PDF) follows the same pattern: author a template, bind context, ship.
     - Templates are version-controlled and reviewable as text. Layout changes are PR-reviewable.
     - Streaming is built-in: the Tiptap pipeline supports chunked output for long documents — the same mechanism that lets a 200-line invoice render without OOM lets a 10k-row audit export render without OOM.
   - Cons:
     - Tiptap templates are HTML/CSS-driven; precise layout control is less granular than an imperative PDF API. For a long table, this is mostly fine — the existing invoice and statement templates already handle long tables correctly via the engine's page-break logic.
     - Template authoring requires familiarity with the existing template pack conventions. Mitigation: this is a one-time cost; the template is small.
     - The 10 000-row cap (§12.3.2 of the architecture doc) is partially driven by the engine's behaviour on very large tables. An imperative library might cap higher. Counter: the cap is also a UX decision (a 50k-row PDF is unreadable regardless of engine); the cap aligns with the human-review use case.

3. **HTML → headless-Chrome rendering.** Generate the audit-export HTML page on the server, render it via a headless Chrome process to PDF.
   - Pros:
     - Full CSS3 fidelity. Modern flexbox/grid layouts render exactly as in the browser.
     - Could share the *same* React component used for the on-screen audit log, just with a print stylesheet.
   - Cons:
     - Adds a headless-Chrome dependency to the backend stack. This is a heavyweight dependency: Docker image grows substantially, Chromium binaries need updating, sandbox concerns.
     - Headless Chrome is a known operational liability — flaky font handling, intermittent crashes on long pages, memory pressure on chunked rendering.
     - Diverges from every other PDF output in the codebase. Becomes a one-off path.
     - Headless Chrome is not a streaming output — it renders the full HTML then snapshots, which fights the 10k-row cap requirement.

**Decision**: Option 2 — reuse the existing Tiptap → PDF pipeline with a new `audit-export` template.

**Rationale**:

The audit-export PDF is shape-equivalent to existing long-table documents (invoices, statements of account, time-entry summaries) that already render through the Tiptap pipeline. The infrastructure handles long tables, chunked output, branding integration, and font management correctly. Building a parallel PDF code path with a different library would duplicate that work for no observable user benefit — a reader of the audit-export PDF cannot tell, and should not need to, whether it was rendered by Tiptap or by iText.

The Tiptap pipeline has been through three Phase-level iterations ([Phase 12](../architecture/phase12-document-templates.md) — initial; [Phase 31](../architecture/phase31-templates-and-partials.md) — partials; [Phase 42](../architecture/phase42-pdf-engine-cutover.md) — engine cutover via [ADR-165](ADR-165-pdf-conversion-strategy.md)). It is the codebase standard. New document shapes default to it unless there is a specific reason — and the audit log is not such a reason.

A dedicated library (Option 1) is a tempting "pure tool for the job" choice, but the cost is a parallel infrastructure that the codebase doesn't need. The codebase's PDF tooling is already a pure tool for *long-table financial-shaped documents*, which is exactly what the audit export is.

Headless Chrome (Option 3) is the wrong tool here — the operational overhead is large, the streaming story is poor, and it diverges from every other PDF output.

The 10 000-row cap is partly an engine-driven choice but mostly a UX decision: a PDF longer than that is not human-readable as a forensic artefact. CSV exists for the 100k-row use case; PDF is for the audit-review use case. The cap also gives a clean ProblemDetail boundary — a 413 with `rowCount` and `cap` is more useful than "the PDF will take 4 minutes to render."

**Consequences**:

- Positive:
  - Single PDF code path in the codebase. The audit-export PDF is rendered the same way as invoices, statements, engagement letters, and proposals.
  - Zero new dependencies. The phase ships without growing the backend dependency tree.
  - The new template `audit-export.tiptap.json` is small (a header partial, a row template, a footer partial) and lives under the existing template pack — discoverable to any engineer who has touched a previous Tiptap template.
  - Streaming is inherited: the existing pipeline supports chunked output, which lets a 10k-row PDF render with bounded memory.
  - Branding (org logo, tenant name, brand colour) is automatic via the existing context-binding step.

- Negative:
  - Layout precision is HTML/CSS-driven rather than imperative. For a long table this is fine; for a more complex layout (multi-column, intricate tables) this could become limiting. Audit exports do not need such layouts in the foreseeable future.
  - Template authoring requires understanding the existing conventions. Onboarding cost for an engineer new to the template pack.
  - The 10 000-row cap is partly engine-driven. A 50k-row PDF would be impractical regardless of engine, but a different engine might raise the cap higher. Mitigation: the 10k cap is also a UX floor; CSV serves the high-row-count case.

- Neutral:
  - The template lives at `backend/src/main/resources/templates/audit-export.tiptap.json` — same location convention as other system templates.
  - Future shape iterations (e.g. a "compact" mode showing only severity + actor + entity) can ship as additional templates or as template parameters.
  - If a future phase needs precise layout control (e.g. court-filing-specific PDF formatting), that is a separate decision that may revisit the engine choice for that specific output. The audit export remains on Tiptap.

- Related: [ADR-056](ADR-056-pdf-engine-selection.md) (PDF engine selection — the engine this ADR commits to), [ADR-165](ADR-165-pdf-conversion-strategy.md) (PDF conversion strategy — the conversion mechanism), [ADR-166](ADR-166-template-format-coexistence.md) (Tiptap template format), [ADR-250](ADR-250-statement-of-account-template-and-context.md) (statement of account template — same shape, same pipeline), [ADR-264](ADR-264-audit-export-is-auditable.md) (the export is itself audited — both PDF and CSV exports trigger the reflexive event).
