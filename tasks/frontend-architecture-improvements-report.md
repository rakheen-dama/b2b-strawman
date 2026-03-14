# Frontend Architecture Improvements — Completion Report

**PR**: #669 (merged 2026-03-14)
**Plan**: `frontend/FRONTEND-IMPROVEMENT-PLAN.md`
**Branch**: `frontend-architecture-improvements` → `main`

---

## What Was Done

### Phase A: Clerk Removal (3 epics, ~1,630 lines deleted)

| Epic | Scope | Key Changes |
|------|-------|-------------|
| **A1** | Remove Clerk auth provider & pages | Deleted `lib/auth/providers/clerk.ts`, `app/(auth)/` route group, webhook handlers. Removed `@clerk/nextjs` + `svix` from package.json. Simplified auth dispatch to keycloak + mock only. Updated 40 files. |
| **A2** | Delete remaining dead code | Removed Clerk types from `internal-api.ts`, cleaned 12 files of Clerk references in comments/test mocks. Zero `@clerk` imports remain. |
| **A3** | Documentation & config cleanup | Updated `CLAUDE.md` and `frontend/CLAUDE.md` to reflect Keycloak-only auth. Rewrote auth sections, env var tables, anti-patterns. Deleted `docs/webhook-setup.md`. |

### Phase B: Code Organisation (3 epics)

| Epic | Scope | Key Changes |
|------|-------|-------------|
| **B1** | Split `lib/types.ts` (1,609 lines) | Created 13 domain modules under `lib/types/`: common, project, customer, document, invoice, member, task, template, field, billing, settings, expense. Barrel re-export via `index.ts` — zero import breakage. |
| **B2** | Split `lib/api.ts` (791 lines) | Created `lib/api/client.ts` (core infrastructure) + 7 domain modules: fields, tags, views, document-templates, settings, generated-documents. Updated 12 existing api modules to import from `./client`. |
| **B3** | Split 7 oversized components | Extracted 22 sub-components + custom hooks. Line counts: invoice-detail (974→179), FieldDefinitionDialog (969→341), TemplateEditorClient (887→452), task-detail-sheet (809→386), invoice-generation-dialog (784→405), cherry-pick-step (704→291), task-list-panel (676→353). |

### Phase C: Developer Experience (3 epics)

| Epic | Scope | Key Changes |
|------|-------|-------------|
| **C1** | Zod + React Hook Form | Added `react-hook-form`, `@hookform/resolvers`, `zod`. Created `components/ui/form.tsx` (Shadcn Form). Created `lib/schemas/` with customer, project, invite-member schemas. Migrated 5 form components. Documented pattern in `frontend/CLAUDE.md`. |
| **C2** | SWR client caching | Added `swr`. Created `lib/swr/fetcher.ts` and `lib/swr/test-utils.ts`. Migrated 7 components: notification polling, onboarding progress, notification dropdown, add-member/link-customer/link-project dialogs, comment section. Documented in `frontend/CLAUDE.md`. |
| **C3** | Dynamic imports | Added `next/dynamic` lazy loading for 6 heavy components: CommandPaletteDialog, GenerateDocumentDialog, HorizontalBarChart (2x), DocumentEditor (2x). Added Skeleton fallbacks. Created `vitest.setup.ts` mock for `next/dynamic`. |

### Phase D: Polish (2 epics)

| Epic | Scope | Key Changes |
|------|-------|-------------|
| **D1** | Accessibility | Added skip-to-content link in root layout with `id="main-content"` target. Added `aria-live="polite"` on notification badge + 5 loading states. Verified Radix dialogs handle focus return. |
| **D2** | Split server action files | Split 5 files (308-511 lines) into 11 focused modules: templates (3), invoices (2), resources (2), billing-runs (2), team (2). Updated 30 importing files. |

---

## Metrics

| Metric | Value |
|--------|-------|
| Total commits | 22 |
| Files changed | ~209 |
| New files created | ~55 |
| Lines removed (Clerk) | ~1,630 |
| New dependencies | `react-hook-form`, `zod`, `swr` |
| Tests passing | 1,525/1,528 (3 pre-existing locale failures) |
| Lint issues | 47 (all pre-existing) |

---

## Review Findings — Follow-up Items

The following items were identified during code review. They are non-blocking but should be addressed in future work.

### Important (Should Fix)

1. **Unused SWR exports** — `defaultSWROptions` and `conditionalKey()` in `lib/swr/fetcher.ts` are defined but never imported. Each SWR-using component inlines its own config. Either integrate `defaultSWROptions` into an app-level `SWRConfig` provider, or remove the unused exports.

2. **Duplicate Zod schemas** — `createCustomerSchema` and `editCustomerSchema` in `lib/schemas/customer.ts` are identical. Same for project schemas. Deduplicate with a base schema:
   ```typescript
   const customerBaseSchema = z.object({ /* fields */ });
   export const createCustomerSchema = customerBaseSchema;
   export const editCustomerSchema = customerBaseSchema.extend({ /* edit-specific */ });
   ```

3. **`next/dynamic` test mock ignores `loading` option** — The `vitest.setup.ts` mock accepts only the import function, silently dropping the `loading` fallback. Future tests checking loading states will behave differently from production. Fix:
   ```typescript
   default: (importFn, options?) => {
     // Use options?.loading as Suspense fallback
   }
   ```

4. **Incomplete invite schema** — `inviteMemberSchema` in `lib/schemas/invite-member.ts` only validates `emailAddress`. If the form also collects a role, add it to the schema.

### Suggestions (Nice to Have)

5. **Missing type modules** — The plan called for `notification.ts` and `comment.ts` type modules under `lib/types/`. These types remain defined inline in their respective action files. Not a regression, but the type splitting is incomplete vs the plan.

6. **SWRTestProvider coverage** — `lib/swr/test-utils.ts` is used in only 4 test files. Other SWR-using components may have tests without this provider, risking cache leakage between tests.

---

## Execution Notes

- **Parallelism**: Used git worktree isolation to run up to 5 builder agents in parallel. Conservative wave strategy: Wave 1 (A1+B1+B2), Wave 2 (B3+D1+D2), Wave 3 (C1+C2+C3).
- **Merge conflicts**: Agents branched from stale base commits, requiring manual conflict resolution for D2 (overlapped with A1+B3) and A2 (redundant with A1). The A2 merge was aborted and replaced with a targeted cleanup agent.
- **Pre-existing issues**: 3 `PreviewPanel.test.tsx` locale formatting failures (`en-ZA`) and 47 lint issues exist on main before this PR — all unrelated to this work.
