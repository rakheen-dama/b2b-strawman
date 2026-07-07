# Fix Spec: LZKC-014 — Matter closure history renders raw member UUID instead of name

## Problem
Day 60 / 60.22: after closing RAF-2026-001, the matter closure history renders "Closed by 0768ccd3-8ebd-4d35-880f-ecb0bcf9f0d8" instead of "Thandi Mathebula". The Reopen path has the same defect (`reopenedBy` is also a raw UUID).

## Root Cause (verified)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/dto/ClosureLogResponse.java:9-24` — the record exposes `UUID closedBy` / `UUID reopenedBy` only; `from(log)` copies the raw IDs, no name field.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java:510-517` — `getLog()` maps `ClosureLogResponse::from` with no name resolution; `MemberNameResolver` is not injected.
- `frontend/components/projects/closure-history-section.tsx:117` — renders `Closed by {entry.closedBy}` verbatim (type `closedBy: string` in `frontend/lib/api/matter-closure.ts:57-69`).
- The codebase already has the canonical pattern: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberNameResolver.java` — `resolveName(UUID)` / batch `resolveNames(Collection<UUID>)` (used by the activity/audit plane).

## Fix
1. `ClosureLogResponse`: add `String closedByName` and `String reopenedByName`; change `from(...)` to accept a resolved-names map (or the two resolved names).
2. `MatterClosureService.getLog()` (line 510): inject `MemberNameResolver`, collect all `closedBy`/`reopenedBy` UUIDs from the log entries, batch-resolve via `resolveNames(...)`, pass into the mapper.
3. Frontend `frontend/lib/api/matter-closure.ts`: add `closedByName?: string; reopenedByName?: string` to the closure-log entry type.
4. `frontend/components/projects/closure-history-section.tsx:117`: render `Closed by {entry.closedByName ?? entry.closedBy}` (same for reopened).

## Scope
Both (backend DTO + frontend render)
Files to modify: `ClosureLogResponse.java`, `MatterClosureService.java`, `frontend/lib/api/matter-closure.ts`, `frontend/components/projects/closure-history-section.tsx`
Files to create: none
Migration needed: no

## Verification
Re-run Day 60 checkpoint 60.22 view: matter detail → closure history shows "Closed by Thandi Mathebula". Backend: extend the closure-log integration test to assert `closedByName` present. Reopen+re-close a scratch matter to verify `reopenedByName` too.

## Estimated Effort
S (< 30 min)
