# ADR-164: DOCX Processing Library Selection

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 42 (Word Template Pipeline)

## Context

DocTeams needs to parse, manipulate, and merge `.docx` (Office Open XML) files for the Word template pipeline. The library must:
- Parse `.docx` XML to discover `{{variable}}` merge fields
- Handle Word's split-run problem (merge fields split across multiple XML runs)
- Replace merge field text with resolved values while preserving formatting
- Process paragraphs, tables, headers, and footers
- Run on the JVM (Spring Boot 4 / Java 25)
- Be open-source with a permissive license

## Options Considered

1. **Apache POI (XWPF) (chosen)** — The de facto Java library for Office file manipulation. XWPF module handles `.docx` specifically.
   - Pros: Mature (20+ years), well-documented, Apache 2.0 license, handles all OOXML elements (paragraphs, tables, headers/footers, images), large community, actively maintained, direct run-level access for split-run handling
   - Cons: API is verbose, large dependency footprint (~10MB), no built-in template/merge engine (must implement merge logic manually), memory-heavy for very large documents

2. **docx4j** — A comprehensive Java library for OOXML manipulation, includes content control binding and PDF export.
   - Pros: Higher-level API for some operations, built-in PDF export (via FO), content control data binding, JAXB-based (type-safe XML access)
   - Cons: Heavier dependency than POI, slower development pace, complex API surface, PDF export quality is mediocre, LGPL license (more restrictive), merge field handling still requires custom code, less community support than POI

3. **docx-stamper** — A Java templating library built on top of docx4j, designed specifically for document generation from templates.
   - Pros: Purpose-built for template merging, expression language support, repeat/conditional directives, minimal custom code needed
   - Cons: Built on docx4j (inherits its dependency weight + LGPL concerns), limited control over split-run handling, smaller community, less flexibility for custom merge field syntax (`{{...}}`), last release may lag behind docx4j versions

4. **Aspose.Words for Java** — Commercial library with comprehensive Word document processing.
   - Pros: Best-in-class fidelity, built-in mail merge, excellent PDF conversion, handles every Word feature
   - Cons: Commercial license ($thousands/year), proprietary, vendor lock-in, overkill for merge-field replacement, not open source

## Decision

Use **Apache POI (XWPF)** (Option 1). It provides the right level of abstraction — direct access to runs within paragraphs for split-run handling, without imposing an opinionated templating model. The merge logic is custom but straightforward, and POI's maturity and Apache 2.0 license align with the project's open-source stack.

## Rationale

The core technical challenge is split-run handling — Word splits `{{customer.name}}` across multiple XML `<w:r>` elements. This requires run-level access to concatenate text, find merge fields, and replace across run boundaries. POI's XWPF API exposes runs directly via `XWPFParagraph.getRuns()` and `XWPFRun.setText()`, giving full control over the merge algorithm. docx4j offers similar access but with more complexity and a more restrictive license. docx-stamper abstracts away run handling but uses its own expression syntax, making it harder to reuse the existing `{{variable}}` convention. Aspose is technically superior but the commercial license is unjustifiable for a merge-field replacement use case.

The manual merge implementation (~200-300 lines of code) is a one-time cost that gives full control over edge cases like nested formatting, empty runs, and multi-paragraph fields.

## Consequences

- **Positive**: Full control over merge algorithm, permissive license, large community for troubleshooting, well-tested with real-world `.docx` files
- **Positive**: No dependency on docx4j means the PDF conversion strategy (ADR-165) is independently chosen
- **Negative**: Must implement and test split-run handling manually — this is the single hardest part of the pipeline
- **Negative**: Large dependency footprint (POI + POI-OOXML + POI-OOXML-Schemas), though the project already uses Maven so dependency management is straightforward
- **Neutral**: No built-in PDF export — PDF conversion is handled separately via LibreOffice headless or docx4j (see [ADR-165](ADR-165-pdf-conversion-strategy.md))
