# ADR-165: PDF Conversion Strategy for Word Templates

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 42 (Word Template Pipeline)

## Context

When generating documents from Word templates, the primary output is `.docx`. However, users may also want PDF output (for sharing, printing, or archival). Converting `.docx` to PDF with high fidelity is notoriously difficult — Word's rendering engine is proprietary and no open-source tool perfectly replicates it. The platform needs a best-effort PDF conversion that handles common formatting (fonts, tables, headers/footers, page layout) while being honest about limitations.

PDF conversion is optional — if no converter is available, the system returns only the `.docx` output. This is explicitly documented as acceptable behavior.

## Options Considered

1. **LibreOffice headless as primary, docx4j PDF as fallback (chosen)** — Use `soffice --headless --convert-to pdf` when LibreOffice is installed, fall back to docx4j's FO-based PDF export otherwise, and gracefully degrade to DOCX-only if neither is available.
   - Pros: LibreOffice has the best open-source Word rendering fidelity, handles complex formatting/images/tables well, widely available in Docker images, docx4j fallback covers environments without LibreOffice, graceful degradation means no hard dependency
   - Cons: LibreOffice is a heavy dependency (~500MB in Docker), subprocess invocation adds latency and complexity (temp files, process management), docx4j PDF quality is mediocre (missing fonts, layout differences), two code paths to maintain

2. **LibreOffice headless only** — Require LibreOffice in all environments.
   - Pros: Single code path, best fidelity, well-understood behavior
   - Cons: Heavy Docker image, not available in all environments (local dev without LibreOffice), hard dependency creates deployment friction, no fallback if LibreOffice process fails

3. **docx4j PDF export only** — Use docx4j's built-in DOCX→FO→PDF pipeline.
   - Pros: Pure Java (no external process), simpler deployment, no Docker image bloat
   - Cons: Significantly lower fidelity than LibreOffice, struggles with complex layouts, font substitution issues, tables often misaligned, images may be misplaced, not production-quality for professional documents

4. **No PDF conversion** — Only output `.docx`, require users to convert manually.
   - Pros: Zero complexity, no fidelity concerns, fastest implementation
   - Cons: Poor UX for users who need PDFs, breaks the "generate and share" workflow, firms often need PDF for client-facing documents

## Decision

Use **LibreOffice headless as primary with docx4j fallback** (Option 1). PDF conversion is best-effort and optional — the system never fails a generation request because PDF conversion is unavailable.

## Rationale

Professional services firms need PDF output for client-facing documents, but the primary value of the Word pipeline is preserving `.docx` formatting — PDF is secondary. LibreOffice headless provides the best open-source fidelity and is easy to add to Docker images (`libreoffice-core` package). The docx4j fallback covers local development and environments where LibreOffice isn't installed. The graceful degradation pattern (try LibreOffice → try docx4j → return DOCX only) means the feature works everywhere, just with varying PDF quality.

The subprocess approach for LibreOffice is well-established in the Java ecosystem. The implementation writes the merged `.docx` to a temp file, invokes `soffice --headless --convert-to pdf`, reads the output PDF, and cleans up. Process timeout (30s) prevents hangs.

## Consequences

- **Positive**: Best available PDF fidelity via LibreOffice, graceful degradation means no hard failures
- **Positive**: Local development works without LibreOffice (docx4j or DOCX-only)
- **Negative**: LibreOffice adds ~500MB to Docker image size; consider a separate "document conversion" sidecar service in future if image size becomes a concern
- **Negative**: Two conversion code paths to maintain and test
- **Negative**: PDF output may not perfectly match Word rendering — this must be documented for users ("PDF is best-effort; for pixel-perfect output, open the .docx in Word and print to PDF")
- **Neutral**: docx4j dependency is only needed for fallback PDF — it can be made optional via Maven profile if desired
