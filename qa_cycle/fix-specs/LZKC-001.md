# Fix Spec: LZKC-001 — Hydration mismatch on /pipeline (dnd-kit aria-describedby)

## Problem
Day 2 / 2.11 (still present Day 90): console hydration error on firm `/pipeline` — DealCard `aria-describedby` differs server vs client (`DndDescribedBy-0` vs `DndDescribedBy-3`). Cosmetic; drag works.

## Root Cause (verified)
- `frontend/components/pipeline/PipelineBoard.tsx:177` — `<DndContext>` is created **without an `id` prop**. `@dnd-kit/core@^6.3.1` derives the accessibility `aria-describedby` target (`DndDescribedBy-<n>`) from a module-level incrementing counter when no `id` is given; the counter advances differently during SSR vs client hydration, so server emits `-0` and client `-3`.
- The attribute lands on the draggable from `useDraggable` in `frontend/components/pipeline/DealCard.tsx:49`.
- NOT the same mechanism as LZKC-002 (radix useId drift from shell-tree divergence) — separate specs, no cluster.

## Fix
`PipelineBoard.tsx:177`: pass a stable id — `<DndContext id="pipeline-board" …>`. dnd-kit then uses the deterministic id on both passes. (Alternative `ssr:false` lazy-load rejected — larger change, loses SSR.)

## Scope
Frontend only
Files to modify: `frontend/components/pipeline/PipelineBoard.tsx`
Files to create: none
Migration needed: no

## Verification
Fresh load of `/pipeline` on the Keycloak stack with devtools console open: zero hydration errors across 2+ reloads (LZKC-001's repro). Drag a deal card to confirm dnd still works.

## Estimated Effort
S (< 30 min)
