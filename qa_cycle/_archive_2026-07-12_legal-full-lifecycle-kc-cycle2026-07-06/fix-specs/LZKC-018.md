# Fix Spec: LZKC-018 — Matter Closure Letter template variables all blank

## Problem
Day 61 / 61.8: the client-facing Matter Closure Letter PDF renders ALL dynamic variables blank — Date, Reason for closure, Total fees billed, Total disbursements, Duration (months) — while static copy and client/matter/org names render fine.

## Root Cause (verified)
- Template `backend/src/main/resources/template-packs/legal-za/matter-closure-letter.json` needs `closure.date` (l.19), `closure.reason_label` (l.50), `closure.notes` (l.55), `matter.total_fees_billed` (l.67), `matter.total_disbursements` (l.74), `matter.duration_months` (l.81), `org.principal_attorney` (l.100).
- The template is registered `primaryEntityType: PROJECT` (`legal-za/pack.json:109-112`), so generation (`verticals/legal/closure/MatterClosureService.java:376-379` → `GeneratedDocumentService.generateForProject` → `PdfRenderingService.findBuilder(PROJECT)`, `PdfRenderingService.java:81, 211-219`) dispatches **`ProjectContextBuilder`**, which only populates `project.*` / `customer.*` / `org.*`.
- The purpose-built **`MatterClosureContextBuilder`** (`verticals/legal/closure/MatterClosureContextBuilder.java`) is dead code: it deliberately does not implement `TemplateContextBuilder` (its own comment, lines 35-39) and has zero callers repo-wide. Every `closure.*` / `matter.*` key therefore resolves to `""`.
- `org.principal_attorney`: no backing OrgSettings field exists anywhere; even the dead builder sets `""` (`MatterClosureContextBuilder.java:127`) — out of scope here (leave blank; flag optional template-line removal as a product call).

**NOT the same mechanism as LZKC-010** (that is a placeholder-name typo in a wired builder) — separate specs by design.

## Fix
Thread the closure context through the existing pipeline via an extra-context overload:
1. `GeneratedDocumentService`: add `generateForProject(UUID projectId, String slug, ..., Map<String,Object> extraContext)` that merges `extraContext` over the builder-produced context before rendering (base overload delegates with `Map.of()`).
2. `MatterClosureService.generateClosureLetterSafely` (line 376): build the `closure.*` / `matter.*` entries using `MatterClosureContextBuilder` (revive it as a plain collaborator — it already computes reason label, fees, disbursements, duration) from the in-scope `ClosureRequest` + project data, and pass them as `extraContext`.
3. Ensure date/currency values are formatted consistently with `VariableFormatter` conventions (pass format hints or pre-format in the builder as it does today).

## Scope
Backend only
Files to modify: `GeneratedDocumentService.java`, `MatterClosureService.java`, `MatterClosureContextBuilder.java`
Files to create: none
Migration needed: no

## Verification
Close a scratch matter with "Generate closure letter" checked → download the PDF from the portal: Date, Reason (Concluded), Total fees billed, Total disbursements, Duration all populated. Integration test: generate closure letter and assert rendered HTML contains the closure values.

## Estimated Effort
M (30 min – 2 hr)
