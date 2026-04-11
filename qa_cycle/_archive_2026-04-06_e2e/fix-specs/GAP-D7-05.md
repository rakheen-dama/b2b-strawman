# Fix Spec: GAP-D7-05 — Adverse Parties tab has no Add button

## Problem
The Adverse Parties tab on a matter/project shows linked adverse parties correctly but provides no "Add Adverse Party" or "Link Adverse Party" button. Users cannot link adverse parties to matters via the UI.

## Root Cause (confirmed)
The `ProjectAdversePartiesTab` component (`frontend/components/legal/project-adverse-parties-tab.tsx`) only renders a table of existing links and an Unlink action. There is no button or dialog to add/link new adverse parties.

However, a `LinkAdversePartyDialog` component EXISTS at `frontend/components/legal/link-adverse-party-dialog.tsx` and an `AdversePartyDialog` at `frontend/components/legal/adverse-party-dialog.tsx`. These components are built but never integrated into the tab.

## Fix

**`frontend/components/legal/project-adverse-parties-tab.tsx`:**

Add a "Link Adverse Party" button that opens the existing `LinkAdversePartyDialog`:

```tsx
import { LinkAdversePartyDialog } from "@/components/legal/link-adverse-party-dialog";

// In the component return, before the table:
<div className="flex items-center justify-between">
  <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
    Adverse Parties
  </h3>
  <LinkAdversePartyDialog
    slug={slug}
    projectId={projectId}
    onSuccess={() => mutate()}
  />
</div>
```

Need to verify the `LinkAdversePartyDialog` component's interface matches and it provides the trigger button internally.

## Scope
- 1 frontend file: `project-adverse-parties-tab.tsx` (import + render LinkAdversePartyDialog)
- Possibly minor adjustments to LinkAdversePartyDialog props

## Verification
1. Open a matter > Adverse Parties tab
2. Verify "Link Adverse Party" button appears
3. Click it — dialog opens with search/create options
4. Link an adverse party
5. Verify it appears in the table
6. Run `pnpm test` — adverse parties tests pass

## Estimated Effort
30 minutes
