# Fix Spec: LZKC-016 — Trust dual-approval first Approve registers silently

## Problem
Day 60 / 60.13: Bob's first Approve on PAY/2026/001 (R 70 000, dual-approver threshold) registered server-side (DB `approved_by` set) but with zero UI feedback — no toast, row stayed AWAITING APPROVAL with an active Approve button, no 1/2-approvals indicator; the only feedback was the inline "Second approver must be different" error when he clicked again.

## Root Cause (verified)
Backend model is correct and already exposes the needed state; the frontend discards it:
- Backend: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/trustaccounting/transaction/TrustTransactionService.java:760-772` `approveDualMode` — first approval sets `approvedBy`/`approvedAt`, status intentionally stays AWAITING_APPROVAL, `first_approved` audit emitted; second different approver completes (:785-791). Entity exposes `approvedBy/approvedAt/secondApprovedBy/secondApprovedAt` (`TrustTransaction.java:56-66`).
- The list API already returns these fields — typed at `frontend/lib/types/trust.ts:75-90`. **No API extension needed.**
- `frontend/components/trust/approval-badge.tsx:45-58` `handleApprove` — on success does nothing (no toast, no state change), relying on the action's revalidate.
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts:197-206` — approve action discards the returned transaction (`return { success: true }`).
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx:388` — passes only `transactionId` + `status` into `ApprovalBadge`, so the badge structurally cannot show first-approval progress; post-refresh row is indistinguishable from pre-click.

## Fix (frontend only)
1. `approval-badge.tsx handleApprove`: on success, `toast(...)` via sonner (already wired in `frontend/app/layout.tsx:4,50`) — e.g. "Approval recorded — 1 of 2" (message can branch on whether the returned/refetched tx is now APPROVED vs still awaiting second).
2. `transactions/page.tsx:388`: pass `approvedBy` / `secondApprovedBy` (or resolved first-approver display name if the DTO carries it — dev to check what the list row exposes) into `ApprovalBadge`; render "1 of 2 approvals" and disable/hide Approve for the member who already approved (backend still enforces at :789-791).
3. `handleApprove`: add `router.refresh()` after success so the row updates in place (client-invoked `revalidatePath` alone is unreliable for in-place refresh).
4. Optionally have the action return the updated transaction (`actions.ts:197-206`) so the toast can be precise — nice-to-have, not required.

## Scope
Frontend only
Files to modify: `frontend/components/trust/approval-badge.tsx`, `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx`, `frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts`
Files to create: none
Migration needed: no

## Verification
Record a scratch payment above the dual-approval threshold; first Approve as Bob → toast + row shows "1 of 2 approvals" + Bob's Approve disabled; second Approve as Thandi → row APPROVED. Repeat below-threshold payment to confirm single-approval path unchanged.

## Estimated Effort
M (30 min – 2 hr)
