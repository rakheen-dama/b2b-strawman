# Fix Spec: LZKC-024 — StageReorder DndContext lacks stable `id` (LZKC-001 hydration class)

## Problem
Carried forward from the 2026-07-06 cycle: `StageReorder.tsx` renders a dnd-kit `DndContext` without an `id` prop. dnd-kit then derives its `aria-describedby` id from a module-level counter that drifts between the SSR and client passes → hydration-mismatch class (identical to LZKC-001 on the pipeline board, fixed in the prior cycle).

## Root Cause (confirmed)
`frontend/components/settings/StageReorder.tsx:106`:

```tsx
<DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
```

No `id`. The fixed sibling shows the exact pattern and carries the explanatory comment: `frontend/components/pipeline/PipelineBoard.tsx:182-183` (`<DndContext id="pipeline-board" …>` with the LZKC-001 comment block at :177-181). Grep confirms these are the only two `DndContext` call sites in the frontend — StageReorder is the last unfixed one.

## Fix
One line at `frontend/components/settings/StageReorder.tsx:106`: add `id="stage-reorder"` to the `DndContext`. Optionally copy the one-sentence LZKC-001 comment for future readers.

## Scope
Frontend only.
Files to modify: `frontend/components/settings/StageReorder.tsx`.
Migration needed: no.

## Verification
- Load the pipeline-stages settings page (Settings → the page rendering StageReorder) with a fresh SSR pass → zero hydration warnings in console.
- Gate: `pnpm lint && pnpm build && pnpm test` + prettier.
- Good candidate to bundle with other one-line frontend fixes ONLY if the orchestrator authorizes a same-class cluster (§7); otherwise own PR.

## Estimated Effort
S (< 15 min)
