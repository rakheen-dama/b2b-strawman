# §1.4 — the "contains trust data" flag and the printed PDF come from one context (MEDIUM)

Source: `tasks/trust-accounting-open-issues-2026-11-23.md` §1.4; FU-3 residual (`qa_cycle/evidence/phase92-followups/FU-3/notes.md`, "Several contexts per generation"). Rating: **MEDIUM** (orchestrator, 2026-11-23): a narrow race whose effect is an access-control bypass (precedents 648F R-1 MEDIUM, R-2).

## Root cause

`GeneratedDocumentService.generateDocument` built the template context twice:

1. `pdfRenderingService.buildContext(...)` (+ `extraContext`) for required-field validation, and `printsTrustData(template, contextMap, clauses)` set `containsTrustData` from it;
2. `pdfRenderingService.generatePdf(...)` then called the context builder **again** and rendered from that second build.

Each build reads the trust block in its own `TrustSnapshotReads` snapshot (FU-3). A client's first-ever trust posting committing between the two builds left the flag `false` (first build: trust `status` "none") while the stored PDF printed the trust money (second build: "available"). The G89-F52 gate (`requireReadable` → `TrustDataVisibility.requireMayRead(isContainsTrustData())`) then let a member without `VIEW_TRUST` download it.

Reproduced on unmodified code (`red-run.txt`): first-ever deposit of R1 234,56 committed in a hook on `validateRequiredFields` (between the builds) → stored PDF prints R1 234,56, `containsTrustData = false`.

## Fix

One context build per generation:

- `PdfRenderingService.renderPdf(template, entityId, contextMap, clauses, extraFormatHints)` (new, public): renders from a context the caller already built and merged. `generatePdf(…6 args)` now builds + merges and delegates to it, so every other caller (`generatePdf` overloads) behaves exactly as before.
- `generateDocument` builds the merged context once (`buildContext` + `extraContext`, unchanged merge order), validates required fields against it, derives `containsTrustData` from it, and renders the PDF from the **same map** via `renderPdf`. Required-field validation now literally sees the context rendering uses (the old comment's promise). Clause resolution, extra-context merging and `extraFormatHints` are passed through unchanged; the file name is still derived from the rendering context.

Either a concurrent first posting is on all three (validation, flag, PDF) or on none.

## Other generation paths checked

| Path | Builds | Flag source | Verdict |
|---|---|---|---|
| `generateDocument` (generic Tiptap PDF) | was 2, now **1** | same map | **Fixed** |
| `generateDocx` (`GeneratedDocumentService` ~:750/:852) | 1 (`builder.buildContext` once; the merged DOCX and its converted PDF sibling come from `mergedBytes`) | `trustBlockAvailable(context)` on that same context | Already single-build; unchanged |
| `StatementService.generate` (Statement of Account) | 1 (`contextBuilder.build(...)`, rendered from that map) | `visibleToCurrentMember() && !summary.trustWithheld()`, from that context | Already single-build; unchanged |
| `previewHtml` / `previewWithValidation` | 1 each | — (no document stored, no flag) | Not affected |
| `PaiaManualGenerationService`, `AcceptanceCertificateService`, report exports, audit export | own HTML → `htmlToPdf` | no `containsTrustData` | Not affected |

## Tests

- New: `TrustSnapshotReadsIntegrationTest.clientTrustStatement_firstDepositCommittedBetweenValidationAndRender_flagMatchesThePdf` — reuses FU-3's race harness and the class's own spy set (one more `@MockitoSpyBean`, `TemplateValidationService`, in the same class, so no new context-cache key elsewhere). Asserts the stored flag equals "the PDF prints the deposit", and that the PDF is the validated (pre-deposit) context.
  - **Red** on main: `expected: true but was: false` (flag false, PDF prints the deposit) — `red-run.txt`.
  - **Green** after the fix: flag false, PDF without the deposit.
- Changed: `clientTrustStatement_placementApprovedBetweenTheReads_storedDocumentCountsItOnce` armed its posting at a read made from `PdfRenderingService.generatePdf`; `generateDocument` no longer calls it, so the hook now arms at `PdfRenderingService.buildContext` (the one build). Same assertion (the R30 000 is counted once), still green.
- Tier 2 includes the whole `template` and `verticals/legal/statement` test trees and `TrustSnapshotReadsIntegrationTest` (`../backdated-cover-all/tier2-run.txt`).

## Residual risk

- The trust **snapshot moment** is still the first trust read in the build (FU-3): a posting committed after it is not on the document; the next generation shows it. Consistent either way.
- `buildContextSnapshot` (stored on the record) carries only template name / entity ids — no trust figures — so it cannot disagree.
- HTML **previews** still show trust money to whoever may preview; that is governed by `TrustDataVisibility` inside the context builders, not by the stored flag, and is unchanged.
