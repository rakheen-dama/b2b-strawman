# Fix Spec: GAP-L-93 — Closure dialog has no Statement-of-Account flag

## Problem

Closure Step-2 dialog only offers `Generate closure letter` checkbox. Scenario `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md §60.8` expects an inline `Generate Statement of Account` flag so the SoA is auto-attached to the matter on close. Today the operator must remember to invoke the standalone `Generate Statement of Account` button on the matter header AFTER closure — extra cognitive step; risk of forgotten SoA on closed matters; loses the at-close audit pair (closure letter + SoA in the same closure_log row).

Evidence:
- `qa_cycle/checkpoint-results/cycle46-day60-19-closure-step2.yml` lines 438-477 (Generate-closure-letter is the only checkbox in the dialog).
- `qa_cycle/checkpoint-results/day-60.md §Day 60 Cycle 46 Walk §60.8`.

## Root Cause (verified)

Frontend dialog `frontend/components/legal/matter-closure-dialog.tsx` lines 277-298 renders only one checkbox (`generateClosureLetter`). The Zod schema `frontend/lib/schemas/matter-closure.ts:8-15` has only `generateClosureLetter: z.boolean()` — no `generateStatementOfAccount` field.

Backend DTO `backend/src/main/java/.../verticals/legal/closure/dto/ClosureRequest.java:12-18` likewise only carries `boolean generateClosureLetter`. `MatterClosureService.close` lines 168-172 fans out only the closure-letter best-effort generator; there is no SoA hook.

The SoA generation flow `StatementService.generate(projectId, request, memberId)` already exists, is `@Transactional`, and produces the same kind of artefact (PORTAL-visible Document + GeneratedDocument + DocumentGeneratedEvent) — so plumbing it into closure is purely a UI + DTO + orchestration patch, no new template/PDF work.

## Fix

**Backend:**

1. Add `boolean generateStatementOfAccount` to `ClosureRequest.java`:
   ```java
   public record ClosureRequest(
       @NotNull(message = "reason is required") ClosureReason reason,
       @Size(max = 5000) String notes,
       boolean generateClosureLetter,
       boolean generateStatementOfAccount,
       boolean override,
       @Size(max = 5000) String overrideJustification) {}
   ```

2. In `MatterClosureService.close` (lines 168-184), after the closure-letter `REQUIRES_NEW` block, add a parallel best-effort SoA call:

   ```java
   UUID soaDocId = null;
   if (req.generateStatementOfAccount()) {
     soaDocId = self.generateSoaSafely(projectId, actingMemberId);
   }
   ```

   New method (mirrors `generateClosureLetterSafely`):
   ```java
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public UUID generateSoaSafely(UUID projectId, UUID actingMemberId) {
     try {
       // SoA period defaults: matter open date → today. Caller can re-generate with custom period
       // afterwards via the Generate Statement of Account button if a different window is needed.
       LocalDate today = LocalDate.now(ZoneOffset.UTC);
       LocalDate openDate = projectRepository.findById(projectId)
           .map(p -> p.getCreatedAt() != null
               ? p.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
               : today.minusYears(1))
           .orElse(today.minusYears(1));
       var resp = statementService.generate(
           projectId,
           new GenerateStatementRequest(null, openDate, today),
           actingMemberId);
       return resp.id();
     } catch (RuntimeException e) {
       log.warn("SoA generation failed for project={}; closure proceeds without SoA", projectId, e);
       return null;
     }
   }
   ```

3. Inject `StatementService` into `MatterClosureService`. **Spring already wires it; no circular-dependency concern because MatterClosureService is not used by StatementService.**

4. Extend `CloseMatterResponse.java` with `UUID statementOfAccountDocumentId` (nullable) — mirrors `closureLetterDocumentId` shape.

**Frontend:**

1. Schema `frontend/lib/schemas/matter-closure.ts`:
   ```ts
   export const closeMatterSchema = z
     .object({
       reason: closureReasonEnum,
       notes: z.string().trim().max(5000).optional(),
       generateClosureLetter: z.boolean(),
       generateStatementOfAccount: z.boolean(),
       override: z.boolean(),
       overrideJustification: z.string().trim().max(5000).optional(),
     })
     .refine(...);  // unchanged
   ```

2. Dialog `frontend/components/legal/matter-closure-dialog.tsx`:
   - Add to `defaultValues` (line 79-86): `generateStatementOfAccount: true`.
   - Add a second `FormField` for `generateStatementOfAccount` directly after the existing `generateClosureLetter` field (line 277-298), copy-pasting the JSX with new label "Generate Statement of Account" and helper "A PDF Statement of Account (Section 86 ledger reconciliation) will be attached to this matter."
   - Pass `generateStatementOfAccount: values.generateStatementOfAccount` to `closeMatterAction`.

3. Action `frontend/app/(app)/org/[slug]/projects/[id]/matter-closure-actions.ts`: pass through new flag to backend POST body.

## Scope

Backend + Frontend.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/dto/ClosureRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/dto/CloseMatterResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`
- `frontend/lib/schemas/matter-closure.ts`
- `frontend/components/legal/matter-closure-dialog.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/matter-closure-actions.ts`

Files to create: none.
Migration needed: no.

## Verification

1. Restart backend.
2. Frontend HMR auto-picks up.
3. Open a fresh ACTIVE legal matter with at least one billable time entry. Click `Close Matter` → Step-2 → assert TWO checkboxes visible (Generate closure letter + Generate Statement of Account, both default-checked).
4. Confirm close → Documents tab shows BOTH the closure letter PDF AND the statement-of-account PDF.
5. DB: `matter_closure_log.closure_letter_document_id` populated; `CloseMatterResponse.statementOfAccountDocumentId` returned (verify in network tab or via subsequent close-log GET).
6. Toggle either checkbox off → only the unchecked artefact is suppressed.

## Estimated Effort

**S (2-3 hours)** — straightforward DTO + form + orchestration; no new domain logic.

## Tests

Backend `MatterClosureServiceTest`:
- `close_withGenerateSoa_invokesStatementService` — mock StatementService, assert `generateSoaSafely` invoked with the right projectId.
- `close_soaFailureDoesNotRollbackClose` — StatementService throws → close still committed, response carries null `statementOfAccountDocumentId`.

Frontend `__tests__/legal/matter-closure-dialog.test.tsx`:
- `renders both checkboxes by default checked`.
- `submits with both flags true by default`.
- `unchecking either flag passes false to the action`.

## Regression Risk

`generateStatementOfAccount=false` keeps closure flow byte-identical to today's behaviour. Existing tests pin the `generateClosureLetter` path; new field is independent.
The default `generateStatementOfAccount=true` is a UX choice — agreed-on per scenario §60.8 ("SoA must be attached at closure") so this is not a surprise.

## Dispatch Recommendation

**Defer-to-later-cycle (LOW severity).** Day 61 unblocks without it: the standalone "Generate Statement of Account" top-bar button is fully functional today. If GAP-L-94 + L-95 are this cycle's primary fixes, **only bundle L-93 if cycle 47 still has Dev capacity after L-94/L-95 land** (S effort, low risk, but two-touch frontend+backend change). Otherwise carry to cycle 48.
