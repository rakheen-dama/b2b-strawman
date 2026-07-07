# Fix Spec: LZKC-002 — Hydration mismatch on /proposals (CreateProposalDialog radix aria-controls)

## Problem
Day 7 / OBS-704 check: reproducible hydration error on every fresh load of firm `/proposals` — `CreateProposalDialog > DialogTrigger` radix `aria-controls` id differs server vs client. Cosmetic (tree regenerates client-side), page functional. Regression-class of the OBS-704 fix-verification checkpoint. Evidence: `.playwright-mcp/console-2026-07-06T08-58-30-443Z.log`.

## Root Cause (verified)
- `frontend/components/proposals/create-proposal-dialog.tsx:204-205` renders `<Dialog><DialogTrigger asChild>` unconditionally; radix stamps the Dialog's `React.useId()`-derived content id as `aria-controls` on the trigger in SSR HTML.
- Commit `129a1f845` ("OBS-704 v3") removed the previous mount-gate on the reasoning that React 19 `useId()` is SSR-stable. That holds only when the SSR tree and first client commit are structurally identical — and the org app-shell above the page (`frontend/app/(app)/org/[slug]/layout.tsx:119-181`) is not hydration-stable: confirmed client-divergent shell nodes rendered before `children` include the `next/dynamic(ssr:false)` command-palette subtree (`command-palette-provider.tsx:6-9,49`) and auth-gated header controls (`auth-header-controls.tsx:21` — `if (!isLoaded) return null`). Any such divergence shifts subsequent `useId()` values, including the dialog's.
- The existing regression guard `frontend/__tests__/components/dialog-family-ssr.snapshot.test.tsx` renders the dialog in isolation via `renderToString`, where the tree trivially matches — structurally blind to this class (why OBS-704 "verified" but regressed).

## Fix
1. Make the dialog's SSR output id-free: lazy-load `CreateProposalDialog` at the page with `next/dynamic(..., { ssr: false })` (or reinstate a correct mount-gate inside the component). Radix Dialog exposes no public prop to pin `contentId`, so client-only rendering is the robust minimal fix that is independent of tree position.
2. Strengthen the guard test: render the dialog inside a representative shell (with a client-divergent sibling above it), not in isolation, so this regression class is actually caught.
3. Flag (not in scope): the true root fix is stabilizing the divergent shell nodes — that would also resolve any other latent useId drift; needs a runtime hydration diff to pin the exact node. Propose as a follow-up if the class recurs.

## Scope
Frontend only
Files to modify: `frontend/app/(app)/org/[slug]/proposals/page.tsx` (dynamic import) or `create-proposal-dialog.tsx` (mount-gate), `frontend/__tests__/components/dialog-family-ssr.snapshot.test.tsx`
Files to create: none
Migration needed: no

## Verification
Two independent fresh loads of `/proposals` (QA's repro condition): zero hydration errors in console. Dialog still opens and creates a proposal. Full frontend vitest green.

## Estimated Effort
M (30 min – 2 hr)
