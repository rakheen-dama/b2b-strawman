# Fix Spec: BUG-CYCLE26-03 — In-context Run Conflict Check on client detail

## Problem

Client detail page (`/customers/[id]`) has no in-context **Run Conflict Check** action. The conflict-check workspace exists at `/conflict-check` but the firm-side user has to leave the client, go to a global page, and re-select the same client from a dropdown. Day 2 demo wow-moment expectation is "on the client record, click Run Conflict Check".

Evidence: `qa_cycle/checkpoint-results/day-02.md` §2.5 (cycle-6) — "action bar has only: Change Status / Generate Document / Export Data / Edit / Archive" and confirmed reproducible at line 106 in cycle-6 verification.

## Root Cause (verified)

Customer detail action bar in `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (lines 480–549) renders only the lifecycle/document/export/edit/archive set. There is no link or button to `/conflict-check` despite the workspace already accepting `customerId` as a form value (`frontend/components/legal/conflict-check-form.tsx:60` `defaultValues.customerId = ""`).

The conflict-check form already supports pre-population — it just needs initial values plumbed in via either props or `searchParams`. Currently neither is wired:

```
frontend/app/(app)/org/[slug]/conflict-check/page.tsx:7
export default async function ConflictCheckPage({ params }: { params: Promise<{ slug: string }> }) {
```

— no `searchParams` argument, no prefill prop into `<ConflictCheckClient>`.

OrgSettings is already fetched on the customer detail page for admins (`settingsRes` at line 148) so we can gate the button on `enabledModules.includes("conflict_check")` without an additional API call.

## Fix

Two changes — both frontend only:

1. **Add a "Run Conflict Check" button to the customer detail action bar** in `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`.
   - Place between `LifecycleTransitionDropdown` and `GenerateDocumentDropdown` (admin-only block, lines 481–549).
   - Render only when `customer.lifecycleStatus !== "ANONYMIZED"` AND `enabledModules.includes("conflict_check")`. Capture `enabledModules` from the existing `settingsRes` (line 144) — promote it out of the `isAdmin` `try` so non-admin paths can render the button too if needed (or keep admin-only — see Scope below).
   - Use `<Link>` from `next/link` styled as a Shadcn `<Button variant="outline" size="sm">` with the `Scale` lucide icon (or `ShieldAlert`).
   - `href` = `/org/${slug}/conflict-check?customerId=${customer.id}&checkedName=${encodeURIComponent(customer.name)}&checkedIdNumber=${encodeURIComponent(customer.idNumber ?? "")}` — only include params that are non-empty.
   - Label: `<TerminologyText template="Run Conflict Check" />` (no terminology placeholder needed; static text).

2. **Wire searchParams → form defaults** in the conflict-check route:
   - `frontend/app/(app)/org/[slug]/conflict-check/page.tsx` — accept `searchParams: Promise<{ customerId?: string; checkedName?: string; checkedIdNumber?: string }>` (Next.js 16 async). Forward as `initialFormDefaults` prop to `<ConflictCheckClient>`.
   - `frontend/app/(app)/org/[slug]/conflict-check/conflict-check-client.tsx` — accept `initialFormDefaults?: Partial<PerformConflictCheckFormData>` prop, forward to `<ConflictCheckForm>`.
   - `frontend/components/legal/conflict-check-form.tsx` — accept `initialFormDefaults?: Partial<PerformConflictCheckFormData>` prop and merge into `defaultValues` (lines 53–63). Default `checkType` stays `"NEW_CLIENT"` if not provided.

The result: clicking the button on Sipho's detail page deep-links to `/conflict-check?customerId=c4f70d86-...&checkedName=Sipho+Dlamini&checkedIdNumber=8501015800088` with the Run Check tab pre-filled and ready to submit.

## Scope

- **Frontend only**.
- Files to modify:
  - `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — promote `settingsRes` fetch out of admin block (or duplicate a lightweight `enabledModules` fetch); add the action button; add `Scale` (or `ShieldAlert`) import from `lucide-react`.
  - `frontend/app/(app)/org/[slug]/conflict-check/page.tsx` — accept `searchParams`, forward to client.
  - `frontend/app/(app)/org/[slug]/conflict-check/conflict-check-client.tsx` — accept and forward `initialFormDefaults`.
  - `frontend/components/legal/conflict-check-form.tsx` — accept `initialFormDefaults`, merge into `useForm`'s `defaultValues`.
- Files to create: none.
- Migration needed: no.
- Backend: no changes.

## Verification

Re-run Day 2 checkpoint 2.5:

1. Log in as Bob (admin) at Mathebula & Partners.
2. Navigate to `/customers/{sipho-id}`.
3. Confirm a `Run Conflict Check` button is visible in the header action bar (right of `LifecycleTransitionDropdown`).
4. Click it → land on `/conflict-check?customerId=…&checkedName=Sipho%20Dlamini&checkedIdNumber=8501015800088`.
5. Run Check tab is active. Form has Name="Sipho Dlamini", ID Number="8501015800088", Customer-link="Sipho Dlamini" pre-filled.
6. Click Run → green "No Conflict" result. DB shows a new `conflict_checks` row with `customer_id` FK to Sipho.

Also: confirm the button does NOT render when the `conflict_check` module is disabled (e.g., on the Acme demo tenant). Confirm it does NOT render on ANONYMIZED customers.

Frontend unit test: extend any existing customer-detail test with a render assertion that the button appears for an ACTIVE customer + `enabledModules.includes("conflict_check")`.

## Estimated Effort

S (< 30 min) — three file edits, all small, no backend or migration work.
