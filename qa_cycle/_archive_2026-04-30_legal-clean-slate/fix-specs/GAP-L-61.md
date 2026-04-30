# Fix Spec: GAP-L-61 — Disbursement "Submit for Approval" CTA missing

## Problem

Day 21 Phase D (cycle-1 verify, 2026-04-25 SAST,
`qa_cycle/checkpoint-results/day-21.md`) blocks: a freshly-created legal
disbursement at `approvalStatus=DRAFT` has **no UI affordance anywhere** to
transition to `PENDING_APPROVAL`. QA browser-walked the detail page, the
matter Disbursements tab, and the org-level list — DOM enumeration on the
detail-page header returned exactly two action buttons: `["Upload receipt",
"Edit"]`. The Approve/Reject panel only renders for `PENDING_APPROVAL`, and
nothing transitions DRAFT → PENDING_APPROVAL from the browser. State is
stranded: row `bb9ee2ac-b1e5-4e2f-bf43-e40a63809530` (Sipho's RAF matter,
R 1 250,00 SHERIFF_FEES, ZERO_RATED_PASS_THROUGH, DRAFT/UNBILLED) cannot
reach an APPROVED state through the UI.

This is a **HIGH BLOCKER** because Day 28 fee-note generation is gated on it:
`DisbursementRepository.findUnbilledBillableByCustomerId` filters
`approvalStatus='APPROVED' AND billingStatus='UNBILLED'`, so any DRAFT row
from the L-57 dialog is invisible to the fee-note picker. Until L-61 lands,
GAP-L-63 verify cannot proceed.

## Root Cause (confirmed, not hypothesised)

The full backend wiring exists; only the frontend trigger is missing.

**Backend — endpoint exists and is correct:**

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementController.java:82-86`

```java
@PostMapping("/{id}/submit")
@RequiresCapability("MANAGE_DISBURSEMENTS")
public ResponseEntity<DisbursementResponse> submit(@PathVariable UUID id) {
  return ResponseEntity.ok(disbursementService.submitForApproval(id));
}
```

Path is `/api/legal/disbursements/{id}/submit` — gated by
`MANAGE_DISBURSEMENTS` (the same capability that gates create / update /
upload-receipt, lines 43 / 58 / 112). NOT `APPROVE_DISBURSEMENTS` —
submitting is the "submitter" action, distinct from approving.

> **Note on dispatch wording:** the QA punch-list referenced
> `/submit-for-approval`. The actual route is `/submit` (verb-only). The
> service method is `submitForApproval(...)` — that's where the dispatch
> picked up the longer string from.

**Frontend — API client helper exists with zero callers:**

`frontend/lib/api/legal-disbursements.ts:167-169`

```ts
export async function submitForApproval(id: string): Promise<DisbursementResponse> {
  return api.post<DisbursementResponse>(`/api/legal/disbursements/${id}/submit`);
}
```

`grep -rn "submitForApproval" frontend/app frontend/components frontend/lib`
returns exactly one match (the definition above). Zero callers in pages,
client components, or server actions.

**Frontend — server action does not exist:**

`frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` exports
`createDisbursementAction`, `updateDisbursementAction`, `uploadReceiptAction`,
`approveDisbursementAction`, `rejectDisbursementAction` — but **no**
`submitForApprovalAction`. The import block at the top of `actions.ts`
(lines 6–20) imports `approveDisbursement`, `createDisbursement`,
`listDisbursements`, `listUnbilled`, `rejectDisbursement`,
`updateDisbursement`, `uploadReceipt` — **no** `submitForApproval`.

**Frontend — detail-client doesn't render any submit affordance:**

`frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx:129-142`

```tsx
<div className="flex shrink-0 gap-2">
  {editable && (
    <>
      <Button variant="outline" size="sm" onClick={() => setUploadOpen(true)}>
        <Upload className="mr-1.5 size-4" /> Upload receipt
      </Button>
      <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
        <Pencil className="mr-1.5 size-4" /> Edit
      </Button>
    </>
  )}
</div>
```

`editable` is `disbursement.approvalStatus === "DRAFT" || ... === "PENDING_APPROVAL"`
(line 57-58). No third button. The approval panel below
(`disbursement-approval-panel.tsx:55-57`) early-returns `null` when status
isn't PENDING_APPROVAL, so the page goes silent on DRAFT rows.

## Fix

Three concrete edits, all frontend-only. No backend changes, no migration.

### File 1 (modify): `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts`

**Add to the import block at lines 6–20** — alongside the existing
`approveDisbursement as approveDisbursementApi` etc. — the new helper:

```ts
submitForApproval as submitForApprovalApi,
```

**Add a new exported server action** below
`uploadReceiptAction` (after line 112) and above
`approveDisbursementAction`. Mirror `approveDisbursementAction`'s shape but
guard with the **submitter capability** (MANAGE_DISBURSEMENTS), not approver:

```ts
function hasManageDisbursementCapability(caps: {
  isAdmin: boolean;
  isOwner: boolean;
  capabilities: string[];
}): boolean {
  return caps.isAdmin || caps.isOwner || caps.capabilities.includes("MANAGE_DISBURSEMENTS");
}

export async function submitForApprovalAction(
  slug: string,
  id: string
): Promise<ActionResult<DisbursementResponse>> {
  const caps = await fetchMyCapabilities();
  if (!hasManageDisbursementCapability(caps)) {
    return {
      success: false,
      error: "You do not have permission to submit disbursements for approval.",
    };
  }

  try {
    const result = await submitForApprovalApi(id);
    revalidatePath(`/org/${slug}/legal/disbursements`);
    revalidatePath(`/org/${slug}/legal/disbursements/${id}`);
    if (result?.projectId) {
      revalidatePath(`/org/${slug}/projects/${result.projectId}`);
    }
    return { success: true, data: result };
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Failed to submit disbursement for approval";
    return { success: false, error: message };
  }
}
```

Place `hasManageDisbursementCapability` next to the existing
`hasApproveDisbursementCapability` helper (lines 23–29) — keep both helpers
together for readability. Do not delete or modify the approver helper.

### File 2 (modify): `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx`

**Add an import** to the existing actions import (line 25):

```ts
import {
  submitForApprovalAction,
  uploadReceiptAction,
} from "@/app/(app)/org/[slug]/legal/disbursements/actions";
```

(currently only `uploadReceiptAction` is imported; expand to include the new
action.)

**Add `Send` to the lucide-react import** (line 5) for the icon — keep the
existing imports:

```ts
import { ArrowLeft, Pencil, Send, Upload } from "lucide-react";
```

**Add a submit handler + isSubmitting state** alongside the existing upload
handler (after line 79). Mirror the upload handler's shape:

```tsx
const [isSubmittingForApproval, setIsSubmittingForApproval] = useState(false);
const [submitError, setSubmitError] = useState<string | null>(null);

const handleSubmitForApproval = useCallback(async () => {
  setSubmitError(null);
  setIsSubmittingForApproval(true);
  try {
    const result = await submitForApprovalAction(slug, disbursement.id);
    if (result.success && result.data) {
      setDisbursement(result.data);
    } else {
      setSubmitError(result.error ?? "Failed to submit disbursement for approval.");
    }
  } catch {
    setSubmitError("An unexpected error occurred while submitting for approval.");
  } finally {
    setIsSubmittingForApproval(false);
  }
}, [slug, disbursement.id]);
```

**Render the new CTA inside the existing button row** (lines 129–142). Add
it BEFORE the existing `Upload receipt` button so it reads
`Submit for Approval / Upload receipt / Edit` left-to-right. Visibility gate:
**only DRAFT** (do NOT reuse the `editable` flag — `editable` includes
PENDING_APPROVAL, where the approval panel takes over and a Submit button
would be wrong). No frontend capability check — backend already enforces
`MANAGE_DISBURSEMENTS` and the server action returns a permission error if
the user lacks it; the inline error UI displays it.

```tsx
<div className="flex shrink-0 gap-2">
  {disbursement.approvalStatus === "DRAFT" && (
    <Button
      variant="default"
      size="sm"
      onClick={handleSubmitForApproval}
      disabled={isSubmittingForApproval}
      data-testid="disbursement-submit-for-approval-button"
    >
      <Send className="mr-1.5 size-4" />
      {isSubmittingForApproval ? "Submitting..." : "Submit for Approval"}
    </Button>
  )}
  {editable && (
    <>
      <Button variant="outline" size="sm" onClick={() => setUploadOpen(true)}>
        <Upload className="mr-1.5 size-4" />
        Upload receipt
      </Button>
      <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
        <Pencil className="mr-1.5 size-4" />
        Edit
      </Button>
    </>
  )}
</div>
```

**Render `submitError` inline** below the button row (a small `<p
role="alert" className="text-sm text-red-600">{submitError}</p>` block,
conditional on `submitError` being non-null). Place it just under the
`<div className="flex items-start justify-between gap-4">` close on line 143
so it doesn't disrupt the two-column grid below.

### File 3 (no edit, just verify): `frontend/components/legal/disbursement-approval-panel.tsx`

**Do NOT modify.** The early-return on
`approvalStatus !== "PENDING_APPROVAL"` (line 56) is correct — once submit
flips status to PENDING_APPROVAL, `setDisbursement(result.data)` in the
detail-client triggers a re-render and the panel takes over for the
Approve/Reject leg automatically.

### Why the backend is fine as-is

`POST /api/legal/disbursements/{id}/submit` is implemented (controller line
82, service method `submitForApproval`). Capability is `MANAGE_DISBURSEMENTS`
which is correct: submitter ≠ approver. The fix is **purely the frontend
trigger** that calls the existing helper.

### What gets wired (visual flow after fix)

1. DRAFT row → `/org/<slug>/legal/disbursements/<id>` → header shows three
   buttons: `[Submit for Approval] [Upload receipt] [Edit]`.
2. Click "Submit for Approval" → action runs → `setDisbursement(result.data)`
   → status flips to PENDING_APPROVAL.
3. Header `editable` is still `true` (PENDING_APPROVAL is editable),
   `approvalStatus !== "DRAFT"` so the new submit button hides; Upload /
   Edit remain.
4. The approval panel below now passes its `approvalStatus ===
   "PENDING_APPROVAL"` gate and renders Approve / Reject buttons.
5. Approver (Owner/Admin or APPROVE_DISBURSEMENTS capability holder) clicks
   Approve → existing `approveDisbursementAction` flow → APPROVED.
6. Day 28 fee-note dialog can now find the row.

### Optional enhancement (out of scope for this fix)

Adding the same Submit CTA on the Disbursements list rows
(`disbursement-list-view.tsx` and the org-level list) is a UX nicety but NOT
required to unblock L-63 — the detail-page CTA is sufficient. Defer.

## Scope

- **Frontend only.**
- Files to modify: 2
  - `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` (add one
    import + one capability helper + one server action ≈ 35 lines added)
  - `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx`
    (add one icon import + one action import + state hooks + handler +
    render block ≈ 30 lines added/changed)
- Files to create: **none**
- Migration needed: **no**
- Backend change: **no**
- Service restart: **no** — frontend HMR picks up the change automatically.
  **NEEDS_REBUILD = false.**
- Env / config: none
- Tests: optionally add a Vitest at
  `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/__tests__/detail-client.test.tsx`
  asserting the Submit button renders for DRAFT and disappears for
  PENDING_APPROVAL/APPROVED. Existing tests in `__tests__/disbursement-list-view.test.tsx`
  + `__tests__/disbursement-approval-panel.test.tsx` give the testing
  pattern. Not strictly required for this fix to merge — QA browser-driven
  verification is the authoritative gate.

## Verification

After fix lands and frontend HMR picks it up (no service restart needed):

1. **QA re-runs Day-21 Phase D against existing DRAFT row**
   `bb9ee2ac-b1e5-4e2f-bf43-e40a63809530` (browser-driven via Playwright
   MCP — REST forbidden for state mutation per HARD rule):
   1. Navigate to `/org/mathebula-partners/legal/disbursements/bb9ee2ac-b1e5-4e2f-bf43-e40a63809530`.
   2. Header should show three buttons: `[Submit for Approval] [Upload
      receipt] [Edit]`.
   3. Click "Submit for Approval".
   4. Status badge should flip from `Draft` to `Pending Approval`; Submit
      button disappears; Upload/Edit remain.
   5. The Approve/Reject panel ("Approval Required" card) should now
      appear below.

2. **Approve as Owner/Admin** (Bob — admin — has APPROVE_DISBURSEMENTS via
   `isAdmin`):
   1. Click "Approve" → optional notes dialog → confirm.
   2. Status flips PENDING_APPROVAL → APPROVED.

3. **Read-only DB SELECT** (allowed per HARD rule):

   ```sql
   SELECT id, approval_status, billing_status, approved_at, approved_by_member_id
   FROM tenant_5039f2d497cf.legal_disbursements
   WHERE id = 'bb9ee2ac-b1e5-4e2f-bf43-e40a63809530';
   ```

   Expect: `approval_status='APPROVED'`, `billing_status='UNBILLED'`,
   `approved_at` non-null, `approved_by_member_id` = Bob's memberId.

4. **L-63 verify (Day 28 dispatch) unblocked**: re-walk the fee-note
   creation dialog on Sipho's customer detail; the disbursement should now
   appear in the unbilled-disbursements list and the fee-note dialog should
   permit attaching it. (Out of scope for L-61 verification per se, but the
   downstream gate L-61 was blocking.)

5. **Permission probe (regression)**: as Carol (member, no
   MANAGE_DISBURSEMENTS), navigate to a DRAFT disbursement detail page and
   click Submit for Approval. Backend returns 403; frontend renders the
   "You do not have permission..." inline error from the action result.
   Backend already enforces — this is just confirming the frontend doesn't
   crash on the error path.

## Estimated Effort

**S (≤ 30 min)** — two file edits, ~65 lines added total, zero backend, zero
migration, zero service restart. Pattern mirrors the existing
`approveDisbursementAction` / approval-panel wiring exactly. CodeRabbit
risk-surface narrow (no new dependencies, no schema, no API surface).

## Parallelisation Notes

**SAFE for parallel Dev execution alongside other open backend specs.** This
fix touches only two frontend files in
`frontend/app/(app)/org/[slug]/legal/disbursements/` — no overlap with any
backend epic or other frontend area. Can be merged independently of any
other in-flight cycle work.

## Status Triage

**SPEC_READY.** Suggested approach validated end-to-end:

- Backend route + service confirmed at `DisbursementController.java:82-86`
  (path is `/submit`, capability is `MANAGE_DISBURSEMENTS`).
- API client helper confirmed at `legal-disbursements.ts:167-169` with
  `grep -rn submitForApproval` proving zero callers.
- Server-actions file confirmed at
  `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts` with five
  existing `*DisbursementAction` exports — `submitForApprovalAction` is the
  missing sixth and follows the same shape.
- Detail-client confirmed at
  `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx`
  with `editable` flag at line 57-58 (DRAFT||PENDING_APPROVAL) — but the
  Submit CTA must gate on **DRAFT only**, not `editable`, to avoid showing
  the button while the Approve/Reject panel is also rendering.
- Capability gate chosen as `MANAGE_DISBURSEMENTS` (matches backend) rather
  than `APPROVE_DISBURSEMENTS` — submitter and approver are distinct roles
  by design.
