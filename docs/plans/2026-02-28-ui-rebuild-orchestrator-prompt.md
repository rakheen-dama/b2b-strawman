# UI Rebuild Orchestrator Prompt

Copy everything below the line into a new Claude Code session.

---

## Task

You are orchestrating the build of `frontend-v2/`, a complete UI rebuild of a B2B SaaS practice-management app. The existing `frontend/` must not be touched — it's in active development.

## Key Files to Read First

1. `docs/plans/2026-02-28-premium-ui-rebuild-design.md` — the full design specification (navigation architecture, page patterns, component system, all 53 pages)
2. `docs/plans/2026-02-28-premium-ui-rebuild-plan.md` — the 25-task implementation plan with exact file paths, code snippets, and commit messages
3. `frontend/CLAUDE.md` — conventions for the existing frontend (same stack, same patterns apply to v2)
4. `CLAUDE.md` — project-level instructions

## What frontend-v2 Is

- A **new Next.js 16 project** in `frontend-v2/` at the project root
- Same tech stack as `frontend/`: React 19, Tailwind CSS v4, Shadcn UI, Clerk auth
- **New deps**: `@tanstack/react-table`, `react-hook-form`, `@hookform/resolvers`, `zod`, `nuqs`, `sonner`
- Consumes the **same backend API** (Spring Boot on port 8080) — no backend changes
- Shares code copied from `frontend/`: `lib/types.ts`, `lib/api.ts`, `lib/auth/`, `lib/format.ts`, `lib/utils.ts`, `hooks/`, `app/api/` routes

## What's Different from frontend/

- **Navigation**: 64px icon rail (7 workspace zones) + 48px top bar + 40px contextual sub-nav bar. Replaces the 240px full sidebar.
- **Page layouts**: 4 standardized patterns (List, Detail, Dashboard, Settings) using shared layout components
- **Data tables**: TanStack Table v8 with server-side sorting, row selection, sticky headers — replaces ad-hoc table markup
- **Forms**: React Hook Form + Zod for complex forms (invoice line items, rate forms)
- **URL state**: `nuqs` for tab selection, filters, pagination — all shareable/bookmarkable
- **Toasts**: Sonner replaces any existing toast approach
- **Buttons**: 4 primary variants (Primary=teal, Secondary=slate, Ghost, Destructive). Primary is now teal-600, not slate-950.

## Execution Strategy

The plan has 25 tasks. Here's the dependency graph:

```
Task 0 (scaffold)
  ↓
Tasks 1-3 (tokens, shadcn, nav model) — sequential
  ↓
Tasks 4-6 (shell components, layouts, toasts) — sequential
  ↓
Tasks 7-10 (DataTable, page patterns, StatusBadge, EmptyState) — 7 is large; 8,9,10 can parallel after 7
  ↓
Tasks 11-17 (zone pages) — ALL 7 are independent, can run in parallel
  ↓
Tasks 18-23 (command palette, shortcuts, dark mode, portal, landing, pre-org) — ALL 6 independent
  ↓
Task 24 (final verification) — sequential, last
```

### Recommended execution

**Phase A — Sequential (Tasks 0-6):** Run one subagent at a time. Each task builds on the previous. Verify `pnpm build` passes after each.

**Phase B — Semi-parallel (Tasks 7-10):** Task 7 (DataTable) is the foundation — run it first. Then run Tasks 8, 9, 10 in parallel (they don't depend on each other, but do depend on Shadcn primitives from Task 2).

**Phase C — Parallel (Tasks 11-17):** All 7 zone page tasks are independent. Each creates its own route files + feature components. Run as many in parallel as practical (3-4 concurrent subagents works well). Each subagent needs the design doc for its zone's page specs.

**Phase D — Parallel (Tasks 18-23):** 6 independent polish tasks. Run in parallel.

**Phase E — Sequential (Task 24):** Final build + test + lint verification.

### Subagent instructions template

For each subagent, provide:

```
You are building frontend-v2/ for a B2B SaaS app.

Read these files first:
- docs/plans/2026-02-28-premium-ui-rebuild-plan.md (find Task N)
- docs/plans/2026-02-28-premium-ui-rebuild-design.md (for design specs)
- frontend/CLAUDE.md (conventions)

Execute Task N exactly as described in the plan.
All work goes in frontend-v2/ — do NOT modify frontend/.
After completing the task, run: cd frontend-v2 && pnpm build
Commit with the message specified in the plan.
```

For zone page tasks (11-17), also tell the subagent:

```
Copy server actions and data fetching logic from the corresponding frontend/ pages:
- frontend/src/app/(app)/org/[slug]/{feature}/page.tsx — copy the data fetching
- frontend/src/app/(app)/org/[slug]/{feature}/actions.ts — copy server actions
- frontend/src/components/{feature}/ — reference for behavior, but rebuild UI with DataTable/PageHeader/DetailPage patterns

The backend API is unchanged. All endpoints, request/response shapes, and auth patterns are identical.
```

### Verifying progress

After each phase, verify:
```bash
cd frontend-v2 && pnpm build    # Must succeed
cd frontend-v2 && pnpm test     # Must pass
cd frontend-v2 && pnpm lint     # Should be clean
```

### When to use worktrees vs main branch

All work happens on `main`. No worktrees needed — `frontend-v2/` is already isolated by being a separate directory. Each subagent commits directly to main on their task's files (all under `frontend-v2/`). No merge conflicts since zone tasks touch different files.

If you prefer isolation, use `isolation: "worktree"` on the Agent tool — but the subagent must still write files under `frontend-v2/` (which exists in the worktree since it's committed to main).
