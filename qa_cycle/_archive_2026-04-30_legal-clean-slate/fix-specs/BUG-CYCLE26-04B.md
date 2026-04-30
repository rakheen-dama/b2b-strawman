# Fix Spec: BUG-CYCLE26-04B — KYC discoverability on client detail (status badge + Verify shortcut)

## Problem

Client detail page (`/customers/[id]`) does not surface KYC at all — no status badge in the header, no "Verify KYC" action button, no KYC tab. Today the only path to KYC verification is: open the **Onboarding** tab → expand a checklist instance → find a row with `verificationProvider` set → click the per-row "Verify Now" button. That's the right place for the verification dialog itself (it requires a `checklistInstanceItemId` per `KycVerificationController.KycVerifyRequest`), but the client header gives the firm-side user no signal that KYC is available, pending, or already verified.

Day 2 demo wow-moment-2 expects "click Run KYC Verification on the client record → KYC badge renders green with verification timestamp". The backend writes `KycVerification` rows linked to `customer_id`; the client detail just doesn't read them.

Evidence: `qa_cycle/checkpoint-results/day-02.md` §2.8 + §2.9 — "no Run KYC Verification button on client detail … no tab, no action button … no KYC status badge".

## Root Cause (verified)

`frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` already fetches `kycStatus` (line 247: `api.get<KycIntegrationStatus>("/api/integrations/kyc/status")`) and passes `kycConfigured={kycStatus.configured}` deep into `ChecklistInstancePanel` (line 815) so the per-row Verify Now button can render. But the page never:
1. Fetches the customer's most recent KYC verification result.
2. Renders a KYC status badge in the header (next to `LifecycleStatusBadge` at line 457).
3. Surfaces a "Verify KYC" shortcut in the action bar (lines 481–549) that scrolls to / opens the onboarding tab.

There is no per-customer KYC fetch endpoint exposed yet. The backend has `KycVerificationService.getResult(reference)` (single result by provider reference) and the verification rows are written keyed to `customerId` — but there is no `GET /api/customers/{id}/kyc` or equivalent. The closest existing data is `KycVerifyResponse` returned at verification time, which the dialog already receives. We can either:

(a) **Read from existing checklist data** — onboarding checklists already show "Verified via {provider}" badges (`ChecklistInstanceItemRow.tsx:161`). We can derive a customer-level KYC summary from `checklistInstances` in the page-level fetch (line 229). Most tenants have at most one active onboarding checklist per customer, and within that one or zero rows have `verificationProvider` populated.

(b) **Add a small backend endpoint** `GET /api/customers/{id}/kyc/status` returning the latest verification result for this customer.

Option (a) is zero-backend, ships in the same PR as 04A's frontend toggle, and matches the existing data shape. Option (b) is cleaner but doubles the scope. Going with (a) — the onboarding-tab Verify Now path already enforces the checklist-item gating, so deriving the badge from checklist row state stays consistent.

## Fix

Three frontend changes — all in `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` plus one small new component:

1. **Derive a customer-level KYC summary from existing checklist data.** After the `checklistInstances` fetch (around line 232), walk the checklist items looking for any row whose `verificationStatus === "VERIFIED"` (or whatever the current field is — see Snippet below). Collapse to one of three states:
   - `unverified` — no checklist row has `verificationProvider` set OR all are pending.
   - `pending` — at least one row has `verificationProvider` but no `verifiedAt`.
   - `verified` — at least one row has `verificationStatus="VERIFIED"`. Pick the latest by `verifiedAt`.

   Pseudocode (verify the actual field names by reading `ChecklistInstanceResponse` and `ChecklistInstanceItemResponse` in `frontend/lib/types/`):
   ```tsx
   const kycSummary = (() => {
     if (!kycStatus.configured) return null;
     for (const inst of checklistInstances) {
       for (const item of inst.items ?? []) {
         if (item.verificationStatus === "VERIFIED" && item.verificationProvider) {
           return { state: "verified" as const, provider: item.verificationProvider, verifiedAt: item.verifiedAt };
         }
       }
     }
     for (const inst of checklistInstances) {
       for (const item of inst.items ?? []) {
         if (item.verificationProvider) {
           return { state: "pending" as const };
         }
       }
     }
     return { state: "unverified" as const };
   })();
   ```

2. **Render a KYC status badge in the header** next to `LifecycleStatusBadge` (line 457). New component `frontend/components/customers/kyc-status-badge.tsx`:
   ```tsx
   "use client";
   import { ShieldCheck, ShieldAlert } from "lucide-react";
   import { Badge } from "@/components/ui/badge";
   import { formatDate } from "@/lib/format";

   export function KycStatusBadge({ summary }: { summary: { state: "unverified" | "pending" | "verified"; provider?: string; verifiedAt?: string } }) {
     if (summary.state === "verified") {
       return (
         <Badge variant="success" title={`Verified via ${summary.provider} on ${formatDate(summary.verifiedAt!)}`}>
           <ShieldCheck className="mr-1 size-3" />
           KYC Verified
         </Badge>
       );
     }
     if (summary.state === "pending") {
       return <Badge variant="warning"><ShieldAlert className="mr-1 size-3" />KYC Pending</Badge>;
     }
     return null; // don't clutter the header for unverified state
   }
   ```
   In `customers/[id]/page.tsx` line 457 area:
   ```tsx
   {kycSummary && <KycStatusBadge summary={kycSummary} />}
   ```

3. **Add a "Verify KYC" shortcut to the action bar** (lines 481–549). Render only when `kycStatus.configured && kycSummary?.state !== "verified" && customer.lifecycleStatus !== "ANONYMIZED"`. Use a `<Link href={`/org/${slug}/customers/${id}#onboarding`}>` styled as `<Button variant="outline" size="sm">` with the `ShieldCheck` icon (already imported at line 94). Label: "Verify KYC". The link anchors to `#onboarding` so the user lands directly on the Onboarding tab where the per-row Verify Now button lives.

   Note: the onboarding tab needs to honour the `#onboarding` hash. Inspect `frontend/components/customers/customer-tabs.tsx` — if it doesn't already read `window.location.hash` to set the active tab, add a small `useEffect` to do so. (~5 lines; verify before estimating.)

## Scope

- **Frontend only**.
- Files to modify:
  - `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — derive `kycSummary`, render badge in header, add Verify-KYC button to action bar.
  - `frontend/components/customers/customer-tabs.tsx` — read `#onboarding` hash on mount and select that tab (if not already supported).
- Files to create:
  - `frontend/components/customers/kyc-status-badge.tsx`
- Migration needed: no.
- Backend: no changes.

## Verification

Re-run Day 2 §2.8–§2.10 after BUG-CYCLE26-04A is merged so the integration can actually be enabled:

1. Enable KYC integration (per 04A). Confirm `kycStatus.configured=true`.
2. Navigate to Sipho's customer detail. Header now shows no badge yet (state=unverified — empty checklist or no `verificationProvider` row). Action bar shows **Verify KYC** button.
3. Open Onboarding tab → expand the FICA / KYC checklist (Day-3 territory but the row exists from Day-2 onboarding-pack seed). The row with `verificationProvider="verifynow"` shows "Verify Now". Click it → KYC dialog → Verify.
4. Page re-renders. Header now shows green **KYC Verified** badge. Action bar Verify-KYC button has disappeared.
5. Click **Verify KYC** action button (before verification): page should land on `#onboarding`, scroll to / activate the Onboarding tab, and the user's Verify Now click target is one click away.

Capture `day-02-kyc-verified.png` finally — green KYC Verified badge in client-detail header.

Frontend unit test: render `customer-detail` test with mock checklist data to assert badge state transitions.

## Estimated Effort

M (30 min – 2 hr) — three files, requires reading the actual `ChecklistInstanceItemResponse` field names (`verificationStatus`, `verifiedAt`) before writing the summary derivation, plus possibly extending `customer-tabs.tsx` to read `#onboarding` hash. Smaller if the tabs already handle hashes.

**Sequencing note**: This depends on BUG-CYCLE26-04A landing first, otherwise the badge can never reach the "verified" state in QA. Land 04A, verify it green, then ship 04B.
