# Fix Spec: LZKC-006 — Billing-run wizard dead-ends after successful generation

## Problem
Day 28 / 28.4: clicking Next from wizard step 3 generates the invoices (backend log confirms ONE successful generate, run PREVIEW→COMPLETED) but the wizard then renders "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED" and steps 4 (Review Drafts) / 5 (Send) are unreachable. Workaround was the run detail page.

## Root Cause (verified)
Double-generate caused by a mis-ordered guard in the step-4 mount effect, triggered by React 19 StrictMode double-mounting on the dev stack:
- Step 3 Next (`frontend/components/billing-runs/cherry-pick-step.tsx:397` → `billing-run-wizard.tsx:52 handleNext`) just advances; generation happens in `frontend/components/billing-runs/review-drafts-step.tsx:76-139` generate-on-mount effect calling `generateAction` (`frontend/app/(app)/org/[slug]/invoices/billing-runs/new/billing-step-actions.ts:38`).
- The `hasGenerated` ref is set too late: line 106 `if (cancelled) return;` runs BEFORE line 112 `hasGenerated.current = true;`. StrictMode mount→cleanup→remount: the first mount's generate succeeds, but its resolution hits the `cancelled` early-return and never sets the ref; the remount sees `hasGenerated === false` and issues a SECOND generate against the now-COMPLETED run.
- Backend correctly rejects the second call: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunGenerationService.java:87-92` — `InvalidStateException("…Only billing runs in PREVIEW status can be generated. Current status: " + status)` — the exact string the wizard shows. Backend behaviour is correct; no backend change.
- Note: StrictMode double-invoke is dev-only, so a prod build likely doesn't reproduce — but the guard ordering is objectively wrong (any cancel-then-remount, e.g. fast tab switches, re-triggers), and the QA/demo environment is the dev stack.

## Fix
`review-drafts-step.tsx`: move `hasGenerated.current = true;` to immediately BEFORE `await generateAction(billingRunId)` (line ~105), resetting to `false` only on a real generation failure (to keep retry possible). The StrictMode remount then takes the existing "already generated → reload items" branch (lines 83-101). Optional hardening: if `generateAction` returns the PREVIEW-status error, treat COMPLETED as success and load drafts instead of rendering the error.

## Scope
Frontend only
Files to modify: `frontend/components/billing-runs/review-drafts-step.tsx`
Files to create: none
Migration needed: no

## Verification
Re-run Day 28 wizard end-to-end on the dev stack: step 3 Next → step 4 Review Drafts renders the generated fee note → step 5 Send reachable. Backend log shows exactly one generate call. Cancel-and-reenter the wizard to confirm the reload branch.

## Estimated Effort
S (< 30 min)
