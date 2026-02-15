# ADR-056: PDF Engine Selection

**Status**: Accepted

**Context**: Phase 12 introduces document template rendering with PDF output. The system needs to convert self-contained HTML (with inline CSS) produced by Thymeleaf into downloadable PDF files. The engine must run within the JVM (Spring Boot 4 on ECS/Fargate), handle CSS-styled documents with images (org logos via S3 pre-signed URLs), and produce professional-quality output suitable for client-facing documents (engagement letters, statements of work, project summaries).

Key requirements: no external process dependency (the backend runs in a container with no browser or OS-level rendering tools), reasonable performance for on-demand single-document generation (not batch), and CSS support sufficient for professional document layout (headers, footers, tables, typography, colors, borders). The existing codebase already produces self-contained HTML for invoice preview (Phase 10) using Thymeleaf with inline styles — the PDF engine must consume this format directly.

**Options Considered**:

1. **OpenHTMLToPDF** — Pure Java library (built on Apache PDFBox) that renders HTML/CSS to PDF. Supports CSS 2.1 plus selected CSS3 properties (border-radius, CSS variables, @page). Active community maintenance.
   - Pros:
     - Pure Java — no external process, no native dependencies. Runs on any JVM, including containers.
     - Good CSS support: CSS 2.1 complete, `@page` for page sizing and margins, `border-radius`, basic CSS3. Sufficient for professional documents.
     - Built on Apache PDFBox — well-tested PDF library with permissive licensing (Apache 2.0).
     - Supports external images via URL (pre-signed S3 URLs for logos).
     - Thread-safe — multiple concurrent renders without shared state.
     - Reasonable performance: typical A4 document renders in 50-200ms.
     - Small footprint: ~5MB added to classpath.
   - Cons:
     - No flexbox or grid — templates must use tables or floats for multi-column layouts.
     - Limited web font support — fonts must be embedded or use system font fallbacks.
     - CSS variable support is partial in older versions.
     - Not pixel-perfect with browser rendering — minor differences in line spacing, font metrics.

2. **Flying Saucer (xhtmlrenderer)** — Older pure Java HTML-to-PDF library. Precursor to OpenHTMLToPDF.
   - Pros:
     - Pure Java, no external dependencies.
     - Mature — has been around since 2004.
     - Generates PDF via iText (older versions) or Apache PDFBox (fork).
   - Cons:
     - Less actively maintained — last significant release in 2020.
     - CSS support is strictly CSS 2.1 — no CSS3 properties at all (no border-radius, no CSS variables).
     - Requires strict XHTML input — Thymeleaf output may need additional sanitization.
     - Image handling is less robust than OpenHTMLToPDF.
     - Community has largely migrated to OpenHTMLToPDF.

3. **Headless Chrome / Puppeteer (via process exec or container sidecar)** — Launch a headless Chromium browser to render HTML and capture as PDF.
   - Pros:
     - Pixel-perfect rendering — identical to what users see in Chrome.
     - Full CSS support including flexbox, grid, animations, web fonts.
     - Can render any HTML that a browser can display.
   - Cons:
     - External process dependency — requires Chromium installed in the container image. Adds ~400MB to the container.
     - Process management complexity: spawning, monitoring, and recycling browser processes from Java.
     - Higher resource usage: each render launches a browser context (~50-100MB RAM per render).
     - Startup latency: cold start of headless Chrome takes 1-3 seconds.
     - Security surface: running a full browser engine inside the container introduces potential vulnerabilities.
     - Not suitable for ECS/Fargate with small task sizes — memory overhead is significant.
     - Breaks the "pure Java" stack constraint.

4. **iText (commercial) / OpenPDF** — Programmatic PDF generation libraries (not HTML-to-PDF).
   - Pros:
     - Fine-grained PDF control — programmatic layout, precise positioning.
     - iText is extremely mature and feature-rich.
     - OpenPDF is the LGPL fork of iText 4.
   - Cons:
     - Not HTML-to-PDF — templates would need to be translated from HTML/CSS to programmatic Java API calls. This defeats the purpose of using Thymeleaf templates.
     - iText 7+ uses AGPL licensing — requires purchasing a commercial license for SaaS use.
     - OpenPDF is based on iText 4 (2009) — limited features compared to modern alternatives.
     - Template authors would need to learn a different templating approach, not HTML/CSS.

**Decision**: OpenHTMLToPDF (Option 1).

**Rationale**: OpenHTMLToPDF is the natural fit for this architecture. The codebase already produces self-contained HTML with inline CSS via Thymeleaf (invoice preview, Phase 10). OpenHTMLToPDF consumes exactly this format — the pipeline is: Thymeleaf template → HTML string → OpenHTMLToPDF → PDF bytes. No format translation, no external processes, no licensing concerns.

The CSS limitations (no flexbox/grid) are acceptable for document templates. Professional documents have been produced with table-based layouts for decades. The template authoring guide documents these constraints explicitly, and the platform-shipped templates demonstrate correct patterns. Templates that work in the HTML preview will produce near-identical PDF output.

Headless Chrome (Option 3) was rejected primarily because it violates the "no external process" constraint and adds significant container overhead. The templates are controlled by the platform (not arbitrary user HTML), so browser-level rendering fidelity is unnecessary. Flying Saucer (Option 2) was rejected because OpenHTMLToPDF is its actively-maintained successor with strictly better CSS support. iText/OpenPDF (Option 4) was rejected because it would require abandoning the HTML/CSS template model entirely.

**Consequences**:
- Maven dependency: `com.openhtmltopdf:openhtmltopdf-pdfbox` (core) and optionally `com.openhtmltopdf:openhtmltopdf-svg-support` for SVG logos.
- Template CSS must stay within CSS 2.1 + supported CSS3 subset. The template editor variable reference panel includes a CSS compatibility note.
- Multi-column layouts in templates must use `<table>` or CSS `float` — document this in template authoring guidelines and platform-shipped templates.
- Performance: suitable for on-demand single-document generation. If batch generation (hundreds of documents) is added in a future phase, consider a thread pool with bounded concurrency.
- Font embedding: configure default fonts (Helvetica/Arial equivalents) in the OpenHTMLToPDF builder for consistent rendering across environments.
- Image resolution: org logos are fetched via pre-signed S3 URLs during rendering. The URL TTL (5 minutes) is sufficient for the render cycle.
