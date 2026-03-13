# Frontend Improvement Orchestrator Prompt

Copy everything below the line into a fresh Claude Code conversation.

---

## Role

You are an **orchestrator agent** coordinating the execution of the Frontend Architecture Improvement Plan at `frontend/FRONTEND-IMPROVEMENT-PLAN.md`. You do NOT write code yourself. You dispatch builder subagents (using the Agent tool with `isolation: "worktree"`) for each epic, review their output, and manage the merge/PR cycle.

## Critical Rules

1. **Read first**: Before starting, read these files:
   - `CLAUDE.md` (project root)
   - `frontend/CLAUDE.md`
   - `frontend/FRONTEND-IMPROVEMENT-PLAN.md`
2. **One epic per subagent**: Each builder agent gets exactly one epic. Never combine epics.
3. **Worktree isolation**: Every builder MUST use `isolation: "worktree"` to avoid conflicts.
4. **Verification before merge**: After each builder completes, run `pnpm run build` and `pnpm test` in the worktree. Do NOT merge failing code.
5. **Sequential within a phase, parallel across independent phases**: Follow the dependency graph strictly.
6. **No functionality regressions**: The app must build and all tests must pass after every epic merge.
7. **Redirect build/test output**: Always use `-q` or redirect to log files. Do NOT flood context with full build output.

## Execution Order

### Track 1: Phase A — Clerk Removal (sequential, 3 epics)

**A1 → A2 → A3** (each depends on prior)

These are in separate worktrees but merge sequentially to main. Phase A is pure deletion — all Keycloak flows already work from Phase 36.

### Track 2: Phase B — Code Organisation (starts after A2 merges, or in parallel if no overlap)

**B1 + B2** (parallel) → **B3** (after both merge)

B1 and B2 touch `lib/types.ts` and `lib/api.ts` respectively — no overlap. B3 (component splitting) depends on B1/B2 being done so imports are stable.

### Track 3: Phase C — Developer Experience (starts after B1+B2 merge)

**C1 + C2 + C3** (all parallel, independent)

### Track 4: Phase D — Polish (anytime, independent)

**D1 + D2** (parallel)

**You may run Track 2 (B1+B2) in parallel with Track 1 (A-series)** since they touch completely different files. Track 3 should wait for B1+B2. Track 4 can run anytime.

## Builder Brief Template

When dispatching a builder subagent, include ALL of the following in the prompt:

```
You are a builder agent implementing Epic {ID}: {TITLE}.

## Context
- Working directory: {worktree path — confirm with `pwd` before writing any files}
- Read `CLAUDE.md` and `frontend/CLAUDE.md` before making any changes
- Read `frontend/FRONTEND-IMPROVEMENT-PLAN.md` section for this epic

## Scope — Epic {ID}: {TITLE}
{Copy the exact bullet points from the improvement plan for this epic}

## Constraints
- Frontend only — do NOT touch backend/ or gateway/
- Do NOT modify files outside the epic scope
- All existing tests must continue to pass
- Run `pnpm run build` (redirect output to /tmp/build-{ID}.log) and `pnpm test` (redirect to /tmp/test-{ID}.log) before declaring done
- If build or tests fail, fix the issues — do not leave broken code
- Keep commits atomic and well-described

## Verification
Before declaring done:
1. `cd frontend && pnpm run build > /tmp/build-{ID}.log 2>&1` — must succeed
2. `cd frontend && pnpm test > /tmp/test-{ID}.log 2>&1` — must succeed
3. `cd frontend && pnpm run lint > /tmp/lint-{ID}.log 2>&1` — must succeed
4. Confirm no `@clerk/nextjs` imports remain (for Phase A epics): `grep -r "@clerk" frontend/lib frontend/app frontend/components --include="*.ts" --include="*.tsx"`
5. List all files changed/deleted/created
```

## Epic-Specific Builder Instructions

### Epic A1: Remove Clerk Auth Provider & Auth Pages
```
Scope:
- Delete `lib/auth/providers/clerk.ts`
- Delete `app/(auth)/` route group entirely (layout.tsx, sign-in/, sign-up/)
- Edit `lib/auth/server.ts` — remove the Clerk import and branch in every function. Keep keycloak + mock only.
- Edit `lib/auth/middleware.ts` — remove Clerk middleware function and its import. Keep keycloak + mock.
- Edit `lib/auth/client/auth-provider.tsx` — remove ClerkProvider import and branch. Keep keycloak + mock.
- Edit `proxy.ts` (root of frontend/) — simplify to only export keycloak + mock middleware
- Edit `package.json` — remove `@clerk/nextjs` and `svix` dependencies, then run `pnpm install`
- Remove Clerk env vars from any `.env*` files: NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY, CLERK_SECRET_KEY, CLERK_WEBHOOK_SIGNING_SECRET
- Search for and remove any `cssLayerName: "clerk"` references
- After all deletions, verify `pnpm run build` succeeds (some imports may break — fix them)
```

### Epic A2: Delete Clerk Webhook Infrastructure & Dead Code
```
Scope (depends on A1 being merged):
- Delete `app/api/webhooks/clerk/route.ts` and `app/api/webhooks/clerk/route.test.ts`
- Delete `lib/webhook-handlers.ts` and `lib/webhook-handlers.test.ts`
- Delete `lib/internal-api.ts` (contains Clerk-specific types)
- In the following files, find and remove the Clerk-specific code branches (look for AUTH_MODE === "clerk" or direct @clerk imports). Each file already has a working Keycloak code path — keep that, delete the Clerk path:
  - `components/team/invite-member-form.tsx`
  - `components/team/pending-invitations.tsx`
  - `components/team/member-list.tsx`
  - `app/(app)/org/[slug]/team/actions.ts`
  - `components/sidebar-user-footer.tsx`
  - `components/auth-header-controls.tsx`
- In `components/marketing/hero-section.tsx` — update any /sign-in or /sign-up links to point to /dashboard (gateway handles auth redirect)
- In `components/marketing/pricing-preview.tsx` — same link update
- Update or remove any test files that import/mock Clerk APIs
- Run a final grep: `grep -r "clerk\|Clerk\|@clerk" frontend/ --include="*.ts" --include="*.tsx" -l` — the only remaining references should be in CLAUDE.md docs or comments (which A3 will clean up)
```

### Epic A3: Documentation & Config Cleanup
```
Scope (depends on A2 being merged):
- Edit `CLAUDE.md` (project root) — remove Clerk references from: Tech Stack table, Environment Variables, Webhook Handler section, any Clerk-specific instructions
- Edit `frontend/CLAUDE.md` — remove: Clerk sections (Authentication, ClerkProvider, JWT for Backend Calls, Webhook Handler), Clerk env vars from table, Clerk anti-patterns (cssLayerName, auth() in client components), update Auth section to describe Keycloak-only architecture
- Update any `.env.example` or `.env.local` files — remove Clerk vars
- Consider simplifying `NEXT_PUBLIC_AUTH_MODE` — document that production is always "keycloak", "mock" is for E2E only
- Verify final build + test pass with zero Clerk references in code
```

### Epic B1: Split `lib/types.ts` into Domain Modules
```
Scope:
- Read `lib/types.ts` fully to understand all type groupings
- Create `lib/types/` directory
- Create domain files: common.ts, project.ts, customer.ts, document.ts, invoice.ts, member.ts, task.ts, template.ts, field.ts, billing.ts, notification.ts, comment.ts (and any others needed)
- Move types to their domain file. Types shared across domains go in common.ts.
- Create `lib/types/index.ts` that re-exports everything from all domain files
- Delete original `lib/types.ts`
- Verify NO import statements across the codebase break (they all use `@/lib/types` which now resolves to the barrel)
- Build + test must pass
```

### Epic B2: Split `lib/api.ts` into Domain Modules
```
Scope:
- Read `lib/api.ts` fully
- Note: `lib/api/capabilities.ts`, `lib/api/billing-runs.ts`, `lib/api/retainers.ts`, `lib/api/information-requests.ts` already exist — do NOT touch these
- Create `lib/api/client.ts` — move core infrastructure: apiRequest(), api object (get/post/put/patch/delete), ApiError class, handleApiError(), getAuthFetchOptions(), API_BASE constant
- Create domain files for the remaining functions in api.ts: templates.ts, fields.ts, tags.ts, views.ts, settings.ts, documents.ts, generated-documents.ts (group logically)
- Create/update `lib/api/index.ts` — re-export everything from client.ts + all domain files + existing files
- Delete original `lib/api.ts`
- Verify NO import statements break
- Build + test must pass
```

### Epic B3: Split Oversized Components
```
Scope:
- Target the top 7 files over 600 lines:
  1. invoice-detail-client.tsx (974 lines)
  2. FieldDefinitionDialog.tsx (969 lines)
  3. TemplateEditorClient.tsx (887 lines)
  4. task-detail-sheet.tsx (809 lines)
  5. invoice-generation-dialog.tsx (784 lines)
  6. cherry-pick-step.tsx (704 lines)
  7. task-list-panel.tsx (676 lines)
- For each: extract logical sub-sections into sibling component files in the same directory
- Extract custom hooks (useXxxState, useXxxForm) where state logic is heavy
- The parent component should become a thin orchestrator importing sub-components
- No functionality changes — pure refactoring
- Each parent file should end up under 400 lines ideally
- Build + test must pass
```

### Epic C1: Form Validation with Zod + React Hook Form
```
Scope:
- Run `pnpm add react-hook-form @hookform/resolvers zod`
- Create `lib/schemas/` directory
- Create schemas for 3-5 forms: customer (create/edit), project (create/edit), invite-member
- Migrate these form components to use react-hook-form + zod resolver
- Use Shadcn Form component pattern if available, or create a thin wrapper
- Show field-level validation errors inline
- Preserve all existing functionality (submit, error handling, success toast)
- Add a brief "Form Patterns" section to `frontend/CLAUDE.md` documenting the new approach
- Build + test must pass
```

### Epic C2: Client-Side Data Caching (SWR)
```
Scope:
- Run `pnpm add swr`
- Create `lib/swr/fetcher.ts` — a fetcher that wraps the existing api.get() with auth
- Identify 5-10 client components that use useEffect+useState for data fetching (dialogs, sheets, polling)
- Migrate `useNotificationPolling` hook to use useSWR with refreshInterval: 30000
- Migrate 4-5 dialog/sheet components from manual fetch to useSWR
- Add a brief "Client Data Fetching" section to `frontend/CLAUDE.md` documenting when to use SWR vs Server Components
- Build + test must pass
```

### Epic C3: Dynamic Imports for Heavy Components
```
Scope:
- Add `next/dynamic` lazy loading for:
  - Tiptap editor components (TemplateEditorClient, DocumentEditor, etc.)
  - Recharts-based components (profitability tables, sparkline charts, report results)
  - Large dialog/wizard components (invoice-generation-dialog, FieldDefinitionDialog, GenerateDocumentDialog)
  - Command palette dialog
- Add <Skeleton> loading fallbacks for each lazy-loaded component
- Verify no hydration mismatches (test by loading each page)
- Build + test must pass
```

### Epic D1: Accessibility Improvements
```
Scope:
- Add `<a href="#main-content" className="sr-only focus:not-sr-only ...">Skip to content</a>` in root layout, with matching `id="main-content"` on the main content wrapper
- Add `aria-live="polite"` on notification badge count element
- Audit 5 key dialogs for focus return on close (Dialog, AlertDialog, Sheet components)
- Add `aria-live="polite"` on loading state text ("Loading projects..." etc.) in 3-5 components
- Build + test must pass
```

### Epic D2: Split Large Server Action Files
```
Scope:
- Split these action files (> 300 lines) into logical sub-files:
  - `settings/templates/actions.ts` (511 lines) → template-crud-actions.ts, template-generation-actions.ts
  - `invoices/actions.ts` (370 lines) → invoice-crud-actions.ts, invoice-payment-actions.ts
  - `resources/actions.ts` (360 lines) → resource-actions.ts, allocation-actions.ts
  - `invoices/billing-runs/new/actions.ts` (334 lines) → billing-run-actions.ts, billing-step-actions.ts
  - `team/actions.ts` (308 lines) → member-actions.ts, invitation-actions.ts
- Update all imports in page.tsx files that reference these actions
- No functionality changes
- Build + test must pass
```

## Orchestrator Workflow Per Epic

1. **Dispatch builder** with epic-specific brief (use `isolation: "worktree"`)
2. **When builder returns**: read its summary of changes
3. **Verify in worktree**: run build + test + lint (redirect output)
4. **If failures**: resume the builder agent to fix issues
5. **If passing**: merge worktree branch to main, confirm clean merge
6. **Log progress**: update a running status in your conversation (epic ID, status, PR/commit)
7. **Move to next epic** per dependency graph

## Parallelism Strategy

Maximum concurrency plan (if resources allow):
- **Wave 1**: A1 + B1 + B2 + D1 + D2 (5 parallel — all independent)
- **Wave 2**: A2 (after A1) + B3 (after B1+B2)
- **Wave 3**: A3 (after A2) + C1 + C2 + C3 (after B1+B2)

Conservative plan (safer):
- **Wave 1**: A1 + B1 + B2 (3 parallel)
- **Wave 2**: A2 + B3 (2 parallel)
- **Wave 3**: A3 + C1 + C2 + C3 (4 parallel)
- **Wave 4**: D1 + D2 (2 parallel)

Use the conservative plan unless the user requests maximum parallelism.

## Completion Criteria

All epics done when:
- `grep -r "@clerk\|@clerk/nextjs\|svix" frontend/ --include="*.ts" --include="*.tsx"` returns nothing
- `pnpm run build` passes
- `pnpm test` passes
- `pnpm run lint` passes
- No file in `lib/` exceeds 800 lines
- `lib/types.ts` no longer exists (replaced by `lib/types/index.ts`)
- `lib/api.ts` no longer exists (replaced by `lib/api/index.ts`)
