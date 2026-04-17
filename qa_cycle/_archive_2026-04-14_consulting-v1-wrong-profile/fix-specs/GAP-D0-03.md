# Fix Spec: GAP-D0-03 — Create Engagement dialog title says "Create Project"

## Problem
The "New Engagement" dialog heading says "Create Project" instead of "Create Engagement" when the accounting-za vertical profile is active. The sidebar and breadcrumbs correctly show "Engagements" (not "Projects"), but the create dialog title and submit button are hardcoded strings that bypass the terminology system. Reported at Day 0 checkpoint 0.38.

## Root Cause (hypothesis)
In `frontend/components/projects/create-project-dialog.tsx`:
- Line 127: `<DialogTitle>Create Project</DialogTitle>` — hardcoded, not wrapped in `<TerminologyText>`
- Line 290: `{isSubmitting ? "Creating..." : "Create Project"}` — hardcoded submit button text

The accounting-za terminology map (`frontend/lib/terminology-map.ts` line 3) maps `"Project" -> "Engagement"`, so `<TerminologyText template="Create {Project}" />` would render "Create Engagement".

The component does NOT import `TerminologyText` or `useTerminology` — confirmed by grep showing zero matches for either in the file.

## Fix
1. In `frontend/components/projects/create-project-dialog.tsx`:

   a. Add import:
   ```ts
   import { TerminologyText } from "@/components/terminology-text";
   ```

   b. Line 127 — Replace:
   ```tsx
   <DialogTitle>Create Project</DialogTitle>
   ```
   With:
   ```tsx
   <DialogTitle><TerminologyText template="Create {Project}" /></DialogTitle>
   ```

   c. Line 290 — Replace:
   ```tsx
   {isSubmitting ? "Creating..." : "Create Project"}
   ```
   With:
   ```tsx
   {isSubmitting ? "Creating..." : <TerminologyText template="Create {Project}" />}
   ```

2. Verify the component already has `"use client"` directive (it does — it's a dialog with state).

## Scope
Frontend
Files to modify:
- `frontend/components/projects/create-project-dialog.tsx` (2 string replacements)
Files to create: none
Migration needed: no

## Verification
Re-run Day 0 checkpoint 0.38: open New Engagement dialog, verify title reads "Create Engagement" (not "Create Project"). Also verify the submit button reads "Create Engagement".

## Estimated Effort
S (< 30 min)
