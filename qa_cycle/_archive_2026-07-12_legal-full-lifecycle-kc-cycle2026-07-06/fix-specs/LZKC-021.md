# Fix Spec: LZKC-021 — "projects" terminology leaks on My Work and Calendar

## Problem
Day 90 / 90.1: My Work subtitle reads "Your tasks and time tracking across all projects"; Calendar subtitle "View upcoming due dates across all projects", plus "All Projects" filter placeholder/option and "Projects" filter tab — despite legal-za substitution working on sibling pages.

## Root Cause (verified)
`TerminologyProvider` wraps the whole `org/[slug]` subtree (`frontend/app/(app)/org/[slug]/layout.tsx:131`) — these pages simply never call the helper:
- `frontend/app/(app)/org/[slug]/my-work/my-work-header.tsx:27` — hardcoded subtitle; client component, does not import `useTerminology`. Note "tasks" leaks too (legal-za task→Action Item).
- `frontend/app/(app)/org/[slug]/calendar/page.tsx:42` — hardcoded subtitle; server component.
- `frontend/app/(app)/org/[slug]/calendar/calendar-page-client.tsx:119` (`<SelectValue placeholder="All Projects" />`), `:122` (`<SelectItem value="all">All Projects</SelectItem>`), `:152` (filter tab label `{t === "ALL" ? "All" : t === "TASK" ? "Tasks" : "Projects"}` — caution: `t` here is the loop variable, NOT the terminology function; "Tasks" leaks here too).

## Fix
1. `my-work-header.tsx`: add `useTerminology()`; subtitle → `Your ${t("tasks")} and time tracking across all ${t("projects")}` (adjust casing per map keys).
2. `calendar/page.tsx:42`: server component — use the established RSC-safe pattern: `<TerminologyText template="View upcoming due dates across all {projects}" />`.
3. `calendar-page-client.tsx`: add `useTerminology()` (rename to avoid colliding with the loop variable `t` at line 152, e.g. `const { t: term } = ...` or rename the loop var); substitute lines 119, 122, 152 (`All ${term("Projects")}`, `term("Projects")`, `term("Tasks")`).

## Scope
Frontend only
Files to modify: `my-work-header.tsx`, `calendar/page.tsx`, `calendar-page-client.tsx`
Files to create: none
Migration needed: no

## Verification
Legal-za tenant: My Work subtitle says "…across all matters" (and "action items" if the map casing supports it); Calendar shows "All Matters" filter and "Matters" tab. Non-legal tenant unchanged. Re-run the Day-90 firm terminology sweep on these two routes.

## Estimated Effort
S (< 30 min)
