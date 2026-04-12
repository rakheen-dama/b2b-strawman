# Fix Spec: GAP-S3-03 — FICA checklist completion blocked by document-upload UX + Country/TaxNumber gates

## Priority
MEDIUM — long-term blocker for PROSPECT → ACTIVE lifecycle via UI. Two sub-issues.

## Problem
Two gates combine to make it impossible to walk a client to ACTIVE through the UI:

**Sub-issue 1**: 6 of the 8 required Legal Client Onboarding items have `requiresDocument: true`
and the combobox shows "No documents uploaded" until a document is uploaded via a separate
Documents tab. Then the user has to re-open each checklist item and link the document. The
QA-observed "silent no-op" on Confirm is actually a swallowed `InvalidStateException: Document
required` error at `ChecklistInstanceService.completeItem:222` — the frontend action handler
drops the error instead of surfacing it.

**Sub-issue 2**: Customer Readiness widget blocks activation on "Country is required" and "Tax
Number is required". These are silent post-create gates rather than required fields on the
intake form.

## Root Cause (confirmed via grep)
Sub-issue 1:
- `backend/.../checklist/ChecklistInstanceService.java:222` — backend correctly throws
  `InvalidStateException("Document required", ...)` when `documentId == null` for a
  `requiresDocument` item.
- `frontend/app/(app)/org/[slug]/customers/[id]/checklist-actions.ts` (server action wrapper) —
  likely swallows the error or returns a plain success object; the UI's combobox shows "No
  documents uploaded" and the Confirm button silently no-ops.
- The FICA pack at `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`
  sets `requiresDocument: true` on 6 items — this is correct for final compliance, but UX
  needs either upload-in-place or a free-text-with-attestation fallback for early-stage intake.

Sub-issue 2:
- Customer Readiness widget — grep `frontend/components/customers/` for "Country is required"
  and "Tax Number is required" to find the component. The gate logic reads a `requiredFields`
  array from the server.
- Customer create dialog — Country is a select on page 1, Tax Number is not a first-class
  field.

## Fix Steps
Split into two smaller fixes. If budget is tight, do only 1.A + 2 for the unblock and defer
1.B (inline upload) to a follow-up cycle.

### Sub-fix 1.A — Surface the "Document required" error clearly
1. In `frontend/app/(app)/org/[slug]/customers/[id]/checklist-actions.ts`, ensure the server
   action rethrows the backend error so react-hook-form's `onSubmit` catches it. Pattern:
   ```ts
   try {
     return await api.post(...);
   } catch (err) {
     throw err; // do not swallow
   }
   ```
2. In the checklist item UI (Mark Complete form), show the error inline:
   ```tsx
   {error && <p className="text-sm text-red-600">{error.message}</p>}
   ```
3. Also disable the Confirm button when `requiresDocument === true && !documentId` with
   `title="Upload a document and select it first"`. Removes the silent no-op.

### Sub-fix 1.B — Inline "Upload & Link" button (optional, larger)
Add a "Upload Document" button inside the Mark Complete form that opens the existing
`UploadDocumentDialog` and auto-links the resulting document to this checklist item on
success. Reuses existing upload plumbing — scope is ~1 hr.

### Sub-fix 2 — Make Country and Tax Number first-class fields
1. In `frontend/lib/schemas/customer.ts`, add:
   - `country` as required (already optional? — upgrade to required)
   - `taxNumber` as optional on the schema, BUT enforce it before PROSPECT → ACTIVE lifecycle
     transition. Backend already does this via the Customer Readiness widget — just surface it
     on the Create Customer dialog as a first-class field with a note "Required for activation".
2. In `frontend/components/customers/create-customer-dialog.tsx`, add a "Tax Number" input on
   Step 1 of 2 alongside Registration Number.
3. No backend change — the gate already exists server-side.

## Scope
- Frontend + possibly backend DTO
- Files to modify:
  - `frontend/app/(app)/org/[slug]/customers/[id]/checklist-actions.ts` (error rethrow)
  - Checklist item component (find under `frontend/components/customers/` or
    `frontend/components/checklist/`)
  - `frontend/lib/schemas/customer.ts`
  - `frontend/components/customers/create-customer-dialog.tsx`
- Migration needed: no

## Verification
1. Re-run Session 3 step 3.13 — click "Mark Complete" on a required-document item without
   uploading a document → inline error "This item requires a document upload. Please upload:
   Certified copy of SA ID / passport" appears (instead of silent no-op).
2. Re-run Session 3 Phase C with a proper document upload + link flow — auto-transition to
   ACTIVE fires once all items + Country + Tax Number are set.

## Estimated Effort
Sub-fix 1.A + 2 only: M (~1 hr)
Include 1.B: approaches L — consider splitting into a separate fix-spec if scoped.
