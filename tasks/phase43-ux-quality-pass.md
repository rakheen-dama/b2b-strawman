# Phase 43 — UX Quality Pass: Empty States, Contextual Help & Error Recovery

Phase 43 is a **frontend-heavy UX quality pass** that addresses the cold-start experience and day-to-day usability gaps. After 42 phases of feature development, the platform has deep capability but new organisations land on empty charts, blank lists, and generic error messages. This phase closes the gap between *functional* and *welcoming*.

The work spans three pillars: (1) meaningful empty states that guide users toward their first actions, (2) inline contextual help on complex features, and (3) categorised error recovery with actionable messages. Underpinning all three is a new **i18n-ready message catalog** — structured JSON files with a `useMessage` hook.

The backend surface is minimal: one V66 migration adding a column to `org_settings`, an `OnboardingService`, and an `OnboardingController` with two endpoints. Everything else is frontend architecture.

**Architecture doc**: `architecture/phase43-ux-quality-pass.md`

**Dependencies on prior phases**:
- Phase 8 (Rate Cards, Budgets): `OrgSettings` entity (extended with `onboarding_dismissed_at`), rate/budget features referenced in help text
- Phase 10 (Invoicing): Invoice lifecycle referenced in help text and empty states
- Phase 11 (Custom Fields, Tags, Views): Custom field/tag/view features referenced in help text and empty states
- Phase 12 (Document Templates): Template features referenced in help text and empty states
- Phase 20 (Auth Abstraction): Auth context used for permission denial handling

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 327 | i18n Message Catalog Foundation | Frontend | -- | S | 327A | **Done** (PR #626) |
| 328 | Empty States System & Page Integration | Frontend | 327 | M | 328A, 328B | **Done** (PR #627, #628) |
| 329 | Getting Started Checklist | Both | 327 | M | 329A, 329B | **Done** (PR #629, #630) |
| 330 | Inline Contextual Help | Frontend | 327 | M | 330A, 330B | **Done** (PR #631, #632) |
| 331 | Error Recovery & Feedback | Frontend | 327 | M | 331A, 331B | |

---

## Dependency Graph

```
FOUNDATION (single slice)
──────────────────────────

[E327A Message catalog:
 useMessage hook,
 types, 5 JSON files
 (en/empty-states.json,
  en/help.json,
  en/errors.json,
  en/getting-started.json,
  en/common.json)
 + unit tests]
        |
        +──────────────────+──────────────────+──────────────────+
        |                  |                  |                  |
EMPTY STATES TRACK    ONBOARDING TRACK    HELP TRACK       ERROR TRACK
──────────────────    ────────────────    ──────────────    ───────────

[E328A EmptyState     [E329A Backend:     [E330A HelpTip    [E331A Error
 component enhance,    V66 migration,      component,        classification,
 Tier 1+2 pages        OrgSettings col,    help points       ErrorBoundary,
 (~8 integrations)     OnboardingService,  #1-#11            toast wrapper
 + tests]              OnboardingController (rates, budgets,  + tests]
        |              + int tests]        invoicing)               |
[E328B Tier 3+4              |             + tests]          [E331B Permission
 pages (~10              [E329B Frontend:        |            Denied component,
 integrations)          GettingStartedCard, [E330B Help        form validation,
 + dashboard widgets    useOnboardingProgress, points #12-#22   page integration
 + tests]               dashboard integration (templates,       + tests]
                        + tests]            fields, dashboard,
                                           other)
                                           + tests]
```

**Parallel opportunities**:
- E327A is the foundation — all other epics depend on it.
- After E327A: E328A, E329A, E330A, and E331A can all run in parallel (4 concurrent builders).
- E328B depends on E328A. E329B depends on E329A. E330B depends on E330A. E331B depends on E331A.
- E329A (backend) and E329B (frontend) are sequential — the frontend needs the backend endpoint.

---

## Implementation Order

### Stage 0: Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 327 | 327A | `useMessage` hook, TypeScript types, 5 JSON namespace files (`empty-states.json`, `help.json`, `errors.json`, `getting-started.json`, `common.json`). ~8 new files (~6 tests). Frontend only. | **Done** (PR #626) |

### Stage 1: Parallel Feature Tracks (4 concurrent)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 328 | 328A | Enhanced `EmptyState` component (`onAction`, `secondaryLink` props, `max-w-md`), Tier 1 pages (projects, customers, team, my work, documents) + Tier 2 pages (time entries, invoices, templates). ~10 modified files (~6 tests). Frontend only. | **Done** (PR #627) |
| 1b (parallel) | 329 | 329A | V66 migration (`onboarding_dismissed_at` on `org_settings`), `OrgSettings` entity update, `OnboardingService`, `OnboardingController` (2 endpoints), DTOs. ~7 new/modified files (~10 tests). Backend only. | **Done** (PR #629) |
| 1c (parallel) | 330 | 330A | `HelpTip` component (Popover + CircleHelp icon), help points #1-#11 (rates, budgets, invoicing). ~8 new/modified files (~5 tests). Frontend only. | **Done** (PR #631) |
| 1d (parallel) | 331 | 331A | `classifyError()` utility, `ErrorBoundary` + `ErrorFallback` components, `showToast()` wrapper, ErrorBoundary integration in org layout. ~6 new/modified files (~8 tests). Frontend only. | **Done** (PR #633) |

### Stage 2: Dependent Second Slices (4 concurrent)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 328 | 328B | Tier 3 pages (profitability, budget tab, activity, notifications, comments) + Tier 4 pages (rate cards, custom fields, saved views, tags) + dashboard widget empty states. ~12 modified files (~4 tests). Frontend only. | **Done** (PR #628) |
| 2b (parallel) | 329 | 329B | `GettingStartedCard` component, `useOnboardingProgress` SWR hook, dashboard integration (card above grid), dismiss flow. ~5 new/modified files (~5 tests). Frontend only. | **Done** (PR #630) |
| 2c (parallel) | 330 | 330B | Help points #12-#22 (templates, custom fields, dashboard/reports, other). ~11 modified files (~3 tests). Frontend only. | **Done** (PR #632) |
| 2d (parallel) | 331 | 331B | `PermissionDenied` component, form validation improvements (scroll-to-error, catalog messages), integration into ~5 mutation handlers. ~8 new/modified files (~5 tests). Frontend only. | |

### Timeline

```
Stage 0: [327A]                                                    (single, foundation)
Stage 1: [328A] // [329A] // [330A] // [331A]                     (4 parallel tracks)
Stage 2: [328B] // [329B] // [330B] // [331B]                     (4 parallel tracks)
```

**Critical path**: 327A -> 329A -> 329B (3 slices sequential at most — backend must precede frontend for onboarding).

**Fastest path with parallelism**: 10 slices total, 3 on critical path. With 4 concurrent builders in Stage 1 and Stage 2, the phase completes in effectively 3 sequential rounds.

---

## Epic 327: i18n Message Catalog Foundation

**Goal**: Establish the message catalog infrastructure — JSON namespace files, the `useMessage` hook with interpolation and missing-key warnings, and TypeScript types. This is the foundational slice that all other epics depend on.

**References**: Architecture doc Sections 11.2, ADR-168.

**Dependencies**: None — greenfield infrastructure.

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **327A** | 327.1--327.8 | `useMessage` hook with `{{variable}}` interpolation, dev-mode missing-key warnings, 5 JSON namespace files with all Phase 43 copy, TypeScript types for namespaces and hook return. ~8 new files (~6 tests). Frontend only. | **Done** (PR #626) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 327.1 | Create `MessageNamespace` type and `UseMessageReturn` interface | 327A | | New file: `frontend/src/messages/index.ts`. Type: `MessageNamespace = 'empty-states' \| 'help' \| 'errors' \| 'getting-started' \| 'common'`. Interface: `UseMessageReturn { t: (code: string, interpolations?: Record<string, string>) => string }`. Export `useMessage(namespace: MessageNamespace, locale?: string): UseMessageReturn`. |
| 327.2 | Implement `useMessage` hook | 327A | 327.1 | In `frontend/src/messages/index.ts`. Static imports of all 5 JSON files. Dot-path key lookup via `code.split('.').reduce()`. `{{variable}}` interpolation via `replace(/\{\{(\w+)\}\}/g, ...)`. Dev-mode `console.warn` for missing keys. Production fallback returns the raw code string. `locale` parameter defaults to `'en'` (unused in this phase). |
| 327.3 | Create `empty-states.json` | 327A | | New file: `frontend/src/messages/en/empty-states.json`. ~18 page entries with `heading`, `description`, `cta` keys per architecture doc Section 11.3 inventory (pages 1-18). Dot-path convention: `{page}.{view}.{heading\|description\|cta}`. |
| 327.4 | Create `help.json` | 327A | | New file: `frontend/src/messages/en/help.json`. ~22 help point entries with `title` and `body` keys per architecture doc Section 11.5 inventory. Convention: `{domain}.{topic}.{title\|body}`. Each body is 1-3 sentences, specific to DocTeams features. |
| 327.5 | Create `errors.json` | 327A | | New file: `frontend/src/messages/en/errors.json`. ~15 entries: 7 API error codes (`api.validation`, `api.forbidden`, `api.notFound`, `api.conflict`, `api.serverError`, `api.networkError`, `api.rateLimited`), 6 validation messages (`validation.required`, `validation.email`, `validation.positiveNumber`, `validation.maxLength`, `validation.minLength`, `validation.url`), error boundary messages (`boundary.heading`, `boundary.description`). |
| 327.6 | Create `getting-started.json` | 327A | | New file: `frontend/src/messages/en/getting-started.json`. ~20 entries: card title/subtitle, 6 step entries (`{step_code}.label`, `{step_code}.description`), dismiss confirmation text, progress text template, completion message. |
| 327.7 | Create `common.json` | 327A | | New file: `frontend/src/messages/en/common.json`. Shared labels if needed (e.g., `permission.denied.heading`, `permission.denied.description`, `permission.denied.cta`). Can be minimal — populated as needed by later epics. |
| 327.8 | Write unit tests for `useMessage` hook | 327A | 327.2 | New file: `frontend/__tests__/use-message.test.ts`. Tests (~6): (1) `resolves simple key from namespace`; (2) `resolves nested dot-path key`; (3) `interpolates {{variable}} tokens`; (4) `interpolates multiple variables`; (5) `returns raw code for missing key in production`; (6) `logs console.warn for missing key in development`. Mock `process.env.NODE_ENV` for dev/prod distinction. Pattern: `frontend/__tests__/` for test file location. |

### Key Files

**Slice 327A -- Create:**
- `frontend/src/messages/index.ts` — hook implementation, types, re-exports
- `frontend/src/messages/en/empty-states.json` — ~18 page empty state entries
- `frontend/src/messages/en/help.json` — ~22 help point entries (title + body)
- `frontend/src/messages/en/errors.json` — ~15 error/validation entries
- `frontend/src/messages/en/getting-started.json` — ~20 checklist entries
- `frontend/src/messages/en/common.json` — shared labels
- `frontend/__tests__/use-message.test.ts` — 6 unit tests

**Slice 327A -- Read for context:**
- `frontend/CLAUDE.md` — conventions, anti-patterns (RSC serialization boundary relevant for hook design)
- `frontend/components/empty-state.tsx` — existing component to understand current string patterns
- `frontend/hooks/use-notification-polling.ts` — existing hook pattern

### Architecture Decisions

- **Plain JSON + custom hook over next-intl/react-i18next**: See ADR-168. Zero dependencies, ~30 lines of code, compatible with any i18n library added later. The file layout (`messages/{locale}/`) and hook signature match both major framework conventions.
- **Static imports over dynamic imports**: Total catalog ~15-20KB — small enough to bundle statically. No loading states, no layout shift, no waterfall.
- **`{{variable}}` interpolation**: Matches the existing `{{entity.field}}` template variable syntax from Phase 12 (document templates). Consistent developer experience.
- **Dev-mode warnings**: `console.warn` for missing keys catches typos during development without breaking the UI in production.

---

## Epic 328: Empty States System & Page Integration

**Goal**: Enhance the existing `EmptyState` component with `onAction` and `secondaryLink` props, then integrate actionable empty states across ~18 pages using message catalog strings.

**References**: Architecture doc Section 11.3, empty state page inventory.

**Dependencies**: Epic 327 (message catalog must exist for string resolution).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **328A** | 328.1--328.6 | Enhanced `EmptyState` component (`onAction`, `secondaryLink` props, `max-w-md`), Tier 1 pages (projects, customers, team, my work, documents) + Tier 2 pages (time entries, invoices, templates). ~10 modified files (~6 tests). Frontend only. | **Done** (PR #627) |
| **328B** | 328.7--328.12 | Tier 3 pages (profitability, budget tab, activity, notifications, comments) + Tier 4 pages (rate cards, custom fields, saved views, tags) + dashboard widget empty states. ~12 modified files (~4 tests). Frontend only. | **Done** (PR #628) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 328.1 | Enhance `EmptyState` component with new props | 328A | 327A | Modify: `frontend/components/empty-state.tsx`. Add optional `onAction?: () => void` prop (onClick handler for CTA button, alternative to `actionHref`). Add optional `secondaryLink?: { label: string; href: string }` prop. Add `max-w-md` to description `<p>`. When `onAction` is provided, render a `<Button onClick={onAction}>` instead of `<Link>`. Render `secondaryLink` as `<Link className="text-sm text-teal-600 hover:text-teal-700">` below the CTA. Preserve all existing prop behaviour — additive changes only. |
| 328.2 | Integrate EmptyState on projects list page | 328A | 328.1 | Modify: `frontend/app/(app)/org/[slug]/projects/page.tsx`. Import `useMessage('empty-states')`. When project list is empty, render `<EmptyState icon={FolderOpen} title={t('projects.list.heading')} description={t('projects.list.description')} actionLabel={t('projects.list.cta')} onAction={() => /* open create dialog */} />`. The page is a Server Component — resolve strings at render time or pass to a client wrapper. Pattern: check how existing empty state is handled (may be a conditional render around the table). |
| 328.3 | Integrate EmptyState on customers list page | 328A | 328.1 | Modify: `frontend/app/(app)/org/[slug]/customers/page.tsx`. Same pattern as 328.2. Icon: `Users`. Codes: `customers.list.heading`, `customers.list.description`, `customers.list.cta`. CTA: open create customer dialog. |
| 328.4 | Integrate EmptyState on team, My Work, documents pages | 328A | 328.1 | Modify 3 files: `frontend/app/(app)/org/[slug]/team/page.tsx` (Icon: `UserPlus`, code: `team.list.*`, CTA: open invite dialog), `frontend/app/(app)/org/[slug]/my-work/page.tsx` (Icon: `ClipboardList`, code: `myWork.list.*`, CTA: navigate to projects), `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` documents section (Icon: `FileText`, code: `documents.list.*`, CTA: open create document dialog). |
| 328.5 | Integrate EmptyState on Tier 2 pages (time entries, invoices, templates) | 328A | 328.1 | Modify 3 files: time entries page (Icon: `Clock`, code: `timeEntries.list.*`, CTA: open log time dialog), invoices list page (Icon: `Receipt`, code: `invoices.list.*`, CTA: open create invoice dialog — description mentions prerequisites), templates list page (Icon: `LayoutTemplate`, code: `templates.list.*`, CTA: open create template dialog). Identify correct page files by exploring `frontend/app/(app)/org/[slug]/` routes. |
| 328.6 | Write tests for enhanced EmptyState and 3 page integrations | 328A | 328.5 | New file: `frontend/__tests__/empty-state.test.tsx`. Tests (~6): (1) `renders with onAction prop and calls handler`; (2) `renders secondaryLink with correct href`; (3) `applies max-w-md to description`; (4) `backwards compatible — existing props still work`; (5) `projects list renders EmptyState when empty` (spot check); (6) `customers list renders EmptyState when empty` (spot check). Use `@testing-library/react`, `vi.fn()` for click handlers. Add `afterEach(() => cleanup())` per frontend CLAUDE.md. |
| 328.7 | Integrate EmptyState on Tier 3 pages (profitability, budget, activity) | 328B | 328A | Modify 3 files: profitability page (Icon: `TrendingUp`, code: `profitability.page.*`, CTA: navigate to rate card settings — use `secondaryLink`), budget tab on project detail (Icon: `PiggyBank`, code: `budget.tab.*`, CTA: open budget config), activity feed on project detail (Icon: `Activity`, code: `activity.feed.*`, no CTA — explanatory only). |
| 328.8 | Integrate EmptyState on notifications and comments | 328B | 328A | Modify 2 files: notifications page (Icon: `Bell`, code: `notifications.page.*`, no CTA — "You're all caught up"), comments section on task/document (Icon: `MessageSquare`, code: `comments.section.*`, CTA: focus comment input). |
| 328.9 | Integrate EmptyState on Tier 4 settings pages | 328B | 328A | Modify 4 files: rate cards settings (Icon: `DollarSign`, code: `rates.settings.*`, CTA: open add rate dialog), custom fields settings (Icon: `Settings2`, code: `customFields.settings.*`, CTA: open create field dialog), saved views (Icon: `Filter`, code: `views.list.*`, CTA: open create view dialog), tags (Icon: `Tag`, code: `tags.list.*`, CTA: open create tag dialog). |
| 328.10 | Add dashboard widget empty states | 328B | 328A | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx` and relevant widget components in `frontend/components/dashboard/`. Add per-widget empty states (Icon: `BarChart3`, code: `dashboard.{widget}.*`, CTA: navigate to relevant creation page). Widgets: profitability, project health, recent activity, team capacity. Each widget checks its data and renders `EmptyState` when empty. |
| 328.11 | Write tests for Tier 3-4 integrations | 328B | 328.10 | New file: `frontend/__tests__/empty-state-pages.test.tsx`. Tests (~4): (1) `profitability page renders EmptyState with settings link`; (2) `notifications page renders EmptyState when no notifications`; (3) `rate cards settings renders EmptyState when no rates`; (4) `dashboard widget renders EmptyState when no data`. Spot-check tests — not exhaustive per page. |
| 328.12 | Verify no regressions on populated pages | 328B | 328.10 | Manual verification step: ensure existing populated pages still render correctly. No test file — builder should run `pnpm build` and `pnpm test` to confirm no regressions. |

### Key Files

**Slice 328A -- Modify:**
- `frontend/components/empty-state.tsx` — add `onAction`, `secondaryLink`, `max-w-md`
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — projects list empty state
- `frontend/app/(app)/org/[slug]/customers/page.tsx` — customers list empty state
- `frontend/app/(app)/org/[slug]/team/page.tsx` — team page empty state
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — My Work empty state
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — documents section empty state
- Time entries, invoices, templates page files (identify exact paths)

**Slice 328A -- Create:**
- `frontend/__tests__/empty-state.test.tsx`

**Slice 328A -- Read for context:**
- `frontend/src/messages/en/empty-states.json` — string codes to use
- `frontend/src/messages/index.ts` — `useMessage` hook usage
- `frontend/components/empty-state.tsx` — existing component interface

**Slice 328B -- Modify:**
- Profitability page, budget tab, activity feed, notifications page, comments section
- Rate cards settings, custom fields settings, saved views, tags pages
- Dashboard page and widget components in `frontend/components/dashboard/`

**Slice 328B -- Create:**
- `frontend/__tests__/empty-state-pages.test.tsx`

**Slice 328B -- Read for context:**
- `frontend/components/empty-state.tsx` — enhanced component from 328A
- `frontend/components/dashboard/` — widget component inventory
- `frontend/src/messages/en/empty-states.json` — string codes

### Architecture Decisions

- **Additive enhancement over replacement**: The `EmptyState` component gains new optional props. No existing call sites need changes — backward compatible.
- **EmptyState stays as Server Component**: The component does NOT call `useMessage` internally. Pages resolve strings and pass plain text props. This avoids forcing `"use client"` on `EmptyState`, which would break its usage in Server Component pages.
- **Two-slice split by tier**: Tier 1-2 pages (328A) are the highest-impact, user-first-encounter pages. Tier 3-4 (328B) are secondary pages and settings. This ensures the most visible improvements land first.

---

## Epic 329: Getting Started Checklist

**Goal**: Add a "Getting Started" checklist to the dashboard that guides new orgs through first-time setup. Backend computes progress from entity counts (computed-on-read pattern). Frontend renders a dismissible card on the dashboard.

**References**: Architecture doc Sections 11.4, 11.7, 11.8, 11.9, ADR-169.

**Dependencies**: Epic 327 (message catalog for checklist labels).

**Scope**: Backend (329A) + Frontend (329B)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **329A** | 329.1--329.7 | V66 migration (`onboarding_dismissed_at` on `org_settings`), `OrgSettings` entity update, `OnboardingService` (computed-on-read from 6 repositories), `OnboardingController` (2 endpoints), DTO records, role-based dismiss. ~7 new/modified files (~10 tests). Backend only. | **Done** (PR #629) |
| **329B** | 329.8--329.13 | `GettingStartedCard` component, `useOnboardingProgress` SWR hook, dashboard integration (card above grid), dismiss flow with confirmation popover, auto-hide on completion. ~5 new/modified files (~5 tests). Frontend only. | **Done** (PR #630) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 329.1 | Create V66 migration | 329A | | New file: `backend/src/main/resources/db/migration/tenant/V66__add_onboarding_dismissed_at.sql`. SQL: `ALTER TABLE org_settings ADD COLUMN onboarding_dismissed_at TIMESTAMPTZ;`. No index needed — only read via PK lookup. Pattern: `backend/.../db/migration/tenant/V65__add_docx_template_support.sql` for version numbering. |
| 329.2 | Update `OrgSettings` entity | 329A | 329.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add `@Column(name = "onboarding_dismissed_at") private Instant onboardingDismissedAt;`. Add getter/setter. Add `dismissOnboarding()` method: `this.onboardingDismissedAt = Instant.now()`. Add `isOnboardingDismissed()`: `return onboardingDismissedAt != null`. |
| 329.3 | Create DTO records | 329A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingProgressResponse.java`. Records: `OnboardingProgressResponse(List<OnboardingStep> steps, boolean dismissed, int completedCount, int totalCount)`, `OnboardingStep(String code, boolean completed)`. Pattern: `backend/.../billingrun/dto/BillingRunDtos.java` for record conventions. |
| 329.4 | Create `OnboardingService` | 329A | 329.2, 329.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingService.java`. `@Service`. Constructor injection: `OrgSettingsRepository`, `ProjectRepository`, `CustomerRepository`, `MemberRepository`, `TimeEntryRepository`, `BillingRateRepository`, `InvoiceRepository`. Method `getProgress()`: (1) load OrgSettings via `findFirst()`, (2) check `isOnboardingDismissed()`, (3) run 6 count queries (`count() > 0` for project/customer/timeEntry/billingRate/invoice, `count() > 1` for member), (4) assemble `OnboardingProgressResponse`. Method `dismiss()`: (1) load OrgSettings, (2) call `dismissOnboarding()`, (3) save. Idempotent — if already dismissed, no-op. |
| 329.5 | Create `OnboardingController` | 329A | 329.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingController.java`. `@RestController @RequestMapping("/api/onboarding")`. `@GetMapping("/progress")` — any authenticated member, returns `OnboardingProgressResponse`. `@PostMapping("/dismiss") @ResponseStatus(HttpStatus.NO_CONTENT)` — `@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")`, delegates to `onboardingService.dismiss()`. Pattern: `backend/.../settings/OrgSettingsController.java` for thin controller. |
| 329.6 | Write integration tests for progress endpoint | 329A | 329.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingControllerTest.java`. Tests (~10): (1) `getProgress_noEntities_allStepsIncomplete`; (2) `getProgress_withProject_createProjectComplete`; (3) `getProgress_withCustomer_addCustomerComplete`; (4) `getProgress_twoMembers_inviteMemberComplete`; (5) `getProgress_withTimeEntry_logTimeComplete`; (6) `getProgress_withBillingRate_setupRatesComplete`; (7) `getProgress_withInvoice_createInvoiceComplete`; (8) `dismiss_ownerRole_setsTimestamp`; (9) `dismiss_memberRole_returns403`; (10) `dismiss_idempotent_returns204`. Use `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`. Use `TestCustomerFactory.createActiveCustomer()` where needed. |
| 329.7 | Test dismissed state in progress response | 329A | 329.6 | Part of test file from 329.6. Additional test: `getProgress_dismissed_returnsDismissedTrue` — dismiss, then call progress, verify `dismissed: true` is returned. Verify steps still computed correctly even after dismiss. |
| 329.8 | Create `useOnboardingProgress` SWR hook | 329B | 329A | New file: `frontend/hooks/use-onboarding-progress.ts`. SWR hook calling `GET /api/onboarding/progress`. Config: `revalidateOnFocus: true`, `dedupingInterval: 30000`. Returns `{ data, error, isLoading, mutate }`. Include `dismiss()` function that calls `POST /api/onboarding/dismiss` and optimistically updates SWR cache (`{ ...data, dismissed: true }`). Use `api.ts` fetch wrapper for auth headers. Pattern: `frontend/hooks/use-notification-polling.ts` for SWR hook convention. |
| 329.9 | Create `GettingStartedCard` component | 329B | 329.8 | New file: `frontend/components/dashboard/getting-started-card.tsx`. `"use client"`. Uses `useOnboardingProgress()` and `useMessage('getting-started')`. Renders Shadcn `Card` with: title from catalog (`card.title`), progress text ("{n} of 6 complete"), progress bar (`bg-teal-600`, width proportional to completedCount/totalCount), 6 step rows (check icon: completed = `text-teal-600 fill-teal-600`, incomplete = `text-slate-300`; label from catalog; arrow link to page). Dismiss button in card header — opens Shadcn `Popover` with "Are you sure? This can't be undone." confirmation. Hidden when `dismissed === true` OR `completedCount === totalCount`. |
| 329.10 | Integrate GettingStartedCard in dashboard | 329B | 329.9 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Import and render `<GettingStartedCard />` above the main dashboard grid. The card self-manages its visibility (hidden when dismissed or complete). No conditional rendering needed in the dashboard — the card handles its own show/hide logic internally. |
| 329.11 | Add dismiss server action | 329B | 329.8 | If not handled entirely in the SWR hook's `dismiss()` function, add a server action in `frontend/app/(app)/org/[slug]/dashboard/actions.ts` that calls `POST /api/onboarding/dismiss`. Alternatively, the SWR hook can call the API directly from the client — simpler for this use case since the hook already manages the state. Decide based on existing patterns. |
| 329.12 | Write frontend tests for GettingStartedCard | 329B | 329.9 | New file: `frontend/__tests__/getting-started-card.test.tsx`. Tests (~5): (1) `renders progress bar with correct width`; (2) `renders completed steps with teal check icon`; (3) `renders incomplete steps with slate circle`; (4) `hides when dismissed is true`; (5) `hides when all steps complete`. Mock `useOnboardingProgress` to return controlled data. Add `afterEach(() => cleanup())`. |
| 329.13 | Verify dismiss flow end-to-end | 329B | 329.12 | Manual verification: open dashboard → see card → click Dismiss → confirm → card disappears → refresh → card still gone. Not a test file — builder runs `pnpm build` + `pnpm test` to confirm. |

### Key Files

**Slice 329A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V66__add_onboarding_dismissed_at.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingProgressResponse.java` (contains both DTOs)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/onboarding/OnboardingControllerTest.java`

**Slice 329A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — add `onboardingDismissedAt` field + methods

**Slice 329A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` — thin controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsRepository.java` — `findFirst()` method pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` — `count()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` — `count()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — `count()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — `count()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/rate/BillingRateRepository.java` — `count()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java` — `count()` method

**Slice 329B -- Create:**
- `frontend/hooks/use-onboarding-progress.ts`
- `frontend/components/dashboard/getting-started-card.tsx`
- `frontend/__tests__/getting-started-card.test.tsx`

**Slice 329B -- Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — add GettingStartedCard above grid

**Slice 329B -- Read for context:**
- `frontend/hooks/use-notification-polling.ts` — SWR hook pattern
- `frontend/components/dashboard/kpi-card.tsx` — dashboard card styling pattern
- `frontend/lib/api.ts` — API client for auth headers
- `frontend/src/messages/en/getting-started.json` — string codes

### Architecture Decisions

- **Computed-on-read over event-driven tracking**: See ADR-169. Six `count()` queries on small tables add <10ms. Always accurate, no stale state, no event wiring. Self-healing if entities are deleted.
- **Dedicated `OnboardingService`**: Queries 6 repositories — too many dependencies for `OrgSettingsService`. Easy to delete when onboarding is no longer needed.
- **`onboarding_dismissed_at` on `OrgSettings`**: Single timestamp column, no separate table. Dismissal is org-wide (shared across all members). Idempotent dismiss endpoint.
- **SWR over server action for data fetching**: Dashboard is a client component with interactive widgets. SWR fits the pattern: fetch on mount, refetch on focus, cache for 30s.

---

## Epic 330: Inline Contextual Help

**Goal**: Create the `HelpTip` component (Popover + CircleHelp icon) and place it at ~22 points across complex feature pages (rates, budgets, invoicing, templates, custom fields, dashboard, other).

**References**: Architecture doc Section 11.5, help point inventory.

**Dependencies**: Epic 327 (message catalog for help text).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **330A** | 330.1--330.5 | `HelpTip` component with Popover integration, help points #1-#11 (rates, budgets, invoicing). ~8 new/modified files (~5 tests). Frontend only. | **Done** (PR #631) |
| **330B** | 330.6--330.9 | Help points #12-#22 (templates, custom fields, dashboard/reports, other). ~11 modified files (~3 tests). Frontend only. | **Done** (PR #632) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 330.1 | Create `HelpTip` component | 330A | 327A | New file: `frontend/components/help-tip.tsx`. `"use client"`. Props: `code: string` (e.g., `"rates.hierarchy"` — resolves to `help:{code}.title` and `help:{code}.body`). Uses `useMessage('help')`. Renders: `CircleHelp` icon (Lucide, `size-4`, `text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300`, `cursor-pointer`). Shadcn `Popover` + `PopoverTrigger` + `PopoverContent`. Content: title (`text-sm font-semibold text-slate-900 dark:text-slate-100`), body (`text-sm text-slate-600 dark:text-slate-400 max-w-xs`), `p-4`. Dismiss: click outside or Escape (Popover default). |
| 330.2 | Add help points #1-#5 (rates) | 330A | 330.1 | Modify 2-3 files in `frontend/` rate-related pages/components. Points: (1) `rates.hierarchy` on rate cards settings page heading, (2) `rates.costVsBilling` on cost rates section heading, (3) `rates.billableTime` on time entry form billable toggle, (4) `rates.currency` on org settings currency field, (5) `rates.snapshots` on rate cards settings info section. Pattern: `<h3 className="flex items-center gap-2">Section Title <HelpTip code="rates.hierarchy" /></h3>`. Max 2-3 per page. |
| 330.3 | Add help points #6-#8 (budgets) | 330A | 330.1 | Modify 1-2 files in `frontend/` budget-related components. Points: (6) `budget.types` on budget config tab heading, (7) `budget.alerts` on budget alert threshold field, (8) `budget.vsActual` on budget status display. Located in `frontend/components/budget/` or project detail budget tab. |
| 330.4 | Add help points #9-#11 (invoicing) | 330A | 330.1 | Modify 2-3 files in `frontend/` invoice-related pages/components. Points: (9) `invoices.lifecycle` on invoices list page heading, (10) `invoices.unbilledTime` on create invoice dialog, (11) `invoices.numbering` on invoice settings or detail. Located in `frontend/app/(app)/org/[slug]/invoices/` and `frontend/components/invoices/`. |
| 330.5 | Write tests for HelpTip component | 330A | 330.1 | New file: `frontend/__tests__/help-tip.test.tsx`. Tests (~5): (1) `renders CircleHelp icon`; (2) `opens popover on click`; (3) `displays title and body from message catalog`; (4) `closes on click outside`; (5) `handles missing code gracefully (shows fallback)`. Mock `useMessage` hook or use actual JSON files. Add `afterEach(() => cleanup())` — Popover uses Radix UI under the hood. |
| 330.6 | Add help points #12-#14 (templates) | 330B | 330A | Modify 2-3 files in `frontend/` template-related pages/components. Points: (12) `templates.variables` on template editor heading, (13) `templates.tiptapVsWord` on template creation format selector, (14) `templates.packs` on templates list pack section. Located in `frontend/components/templates/` and template pages. |
| 330.7 | Add help points #15-#16 (custom fields) | 330B | 330A | Modify 1-2 files in `frontend/` custom field pages/components. Points: (15) `fields.types` on create field dialog, (16) `fields.scoping` on custom fields settings heading. Located in `frontend/components/field-definitions/` or settings pages. |
| 330.8 | Add help points #17-#22 (dashboard, reports, other) | 330B | 330A | Modify 5-6 files. Points: (17) `dashboard.utilisation` on utilisation widget, (18) `dashboard.projectHealth` on project health widget, (19) `dashboard.profitability` on profitability page heading, (20) `views.saved` on saved views heading, (21) `tags.overview` on tags settings heading, (22) `notifications.preferences` on notification settings heading. Located in `frontend/components/dashboard/`, profitability page, views/tags/notifications settings pages. |
| 330.9 | Write tests for HelpTip page integrations | 330B | 330.8 | New file: `frontend/__tests__/help-tip-integration.test.tsx`. Tests (~3): (1) `rate cards page renders HelpTip with correct code`; (2) `budget tab renders HelpTip`; (3) `template editor renders HelpTip`. Spot-check tests — verify the component is rendered in context, not exhaustive per page. |

### Key Files

**Slice 330A -- Create:**
- `frontend/components/help-tip.tsx` — HelpTip component
- `frontend/__tests__/help-tip.test.tsx` — 5 component tests

**Slice 330A -- Modify:**
- Rate cards settings page/components (~2-3 files)
- Budget config components (~1-2 files)
- Invoice list/create dialog (~2-3 files)

**Slice 330A -- Read for context:**
- `frontend/src/messages/en/help.json` — help text content
- `frontend/src/messages/index.ts` — `useMessage` hook
- `frontend/components/ui/popover.tsx` — Shadcn Popover component

**Slice 330B -- Modify:**
- Template editor/list components (~2-3 files)
- Custom fields settings (~1-2 files)
- Dashboard widget components (~3-4 files)
- Profitability, views, tags, notifications settings pages (~4 files)

**Slice 330B -- Create:**
- `frontend/__tests__/help-tip-integration.test.tsx`

### Architecture Decisions

- **Popover over Tooltip**: Tooltips appear on hover and vanish on mouse leave — unsuitable for multi-sentence help text. Popovers are click-activated and persist until dismissed, matching the mental model of a help panel.
- **Two-slice split by domain complexity**: 330A covers the most complex help points (rates, budgets, invoicing — features with the steepest learning curve). 330B covers less complex features.
- **Maximum 2-3 help tips per page**: Avoids visual clutter. If a page already has 3 help tips, additional help for that page should be deferred or consolidated.

---

## Epic 331: Error Recovery & Feedback

**Goal**: Categorise API errors with actionable recovery, add error boundary components, standardise toast notifications, create a PermissionDenied component, and improve form validation messages.

**References**: Architecture doc Section 11.6.

**Dependencies**: Epic 327 (message catalog for error messages).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **331A** | 331.1--331.6 | `classifyError()` utility, `ErrorBoundary` + `ErrorFallback` components, `showToast()` wrapper, ErrorBoundary integration in org layout. ~6 new/modified files (~8 tests). Frontend only. | **Done** (PR #633) |
| **331B** | 331.7--331.12 | `PermissionDenied` component, form validation improvements (scroll-to-error, catalog messages), integration of `classifyError` + `showToast` into ~5 mutation handlers. ~8 new/modified files (~5 tests). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 331.1 | Create `classifyError()` utility | 331A | 327A | New file: `frontend/src/lib/error-handler.ts`. Types: `ErrorCategory = 'validation' \| 'forbidden' \| 'notFound' \| 'conflict' \| 'serverError' \| 'networkError' \| 'rateLimited'`. Interface: `ClassifiedError { category: ErrorCategory; messageCode: string; retryable: boolean; action?: 'retry' \| 'refresh' \| 'goBack' \| 'contactAdmin' }`. Function: `classifyError(error: unknown): ClassifiedError`. Classification per architecture doc flowchart: inspect `error.response?.status` or `error.status`, fallback to `networkError` if no response. Parse RFC 9457 `ProblemDetail` body for additional context when available. |
| 331.2 | Create `showToast()` wrapper | 331A | 327A, 331.1 | In `frontend/src/lib/error-handler.ts` (same file). Function: `showToast(type: 'success' \| 'error' \| 'warning' \| 'info', messageCode: string, options?: { namespace?: MessageNamespace; interpolations?: Record<string, string>; onRetry?: () => void })`. Resolves message from catalog, calls `toast.success()` / `toast.error()` / etc. from `sonner`. Error toasts: persistent (no auto-dismiss), include "Try again" action button if `onRetry` provided. Success toasts: auto-dismiss 4s. Warning: 6s. Info: 4s. |
| 331.3 | Create `ErrorBoundary` class component | 331A | 327A | New file: `frontend/components/error-boundary.tsx`. `"use client"`. React class component implementing `componentDidCatch(error, errorInfo)`. State: `{ hasError: boolean; error: Error \| null }`. `static getDerivedStateFromError(error)`: returns `{ hasError: true, error }`. Renders `children` normally; when error caught, renders `<ErrorFallback>`. Props: `{ children: ReactNode; fallback?: ReactNode }`. Reset method: clears error state (for "Try again" button). |
| 331.4 | Create `ErrorFallback` component | 331A | 331.3 | In `frontend/components/error-boundary.tsx` (same file). Renders: `AlertTriangle` icon (Lucide, `size-12`, `text-red-500`), heading from catalog (`errors:boundary.heading`), description from catalog (`errors:boundary.description`), "Try again" button (resets boundary), "Refresh page" button (`window.location.reload()`), "Go back" button (`router.back()`). Centred layout matching `EmptyState` visual language (`flex flex-col items-center py-24 gap-4`). |
| 331.5 | Integrate ErrorBoundary in org layout | 331A | 331.3 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. Wrap the page content area (children) inside `<ErrorBoundary>`. Keep sidebar and header OUTSIDE the boundary so navigation remains functional during page errors. Pattern: `<Sidebar /><Header /><ErrorBoundary>{children}</ErrorBoundary>`. |
| 331.6 | Write tests for classifyError, ErrorBoundary, showToast | 331A | 331.5 | New file: `frontend/__tests__/error-handling.test.tsx`. Tests (~8): (1) `classifyError_400_returnsValidation`; (2) `classifyError_403_returnsForbidden`; (3) `classifyError_404_returnsNotFound`; (4) `classifyError_409_returnsConflict`; (5) `classifyError_500_returnsServerError`; (6) `classifyError_noResponse_returnsNetworkError`; (7) `ErrorBoundary_catchesRenderError_showsFallback`; (8) `ErrorBoundary_resetButton_clearsError`. For ErrorBoundary test: create a child component that throws, verify fallback renders. Add `afterEach(() => cleanup())`. |
| 331.7 | Create `PermissionDenied` component | 331B | 327A | New file: `frontend/components/permission-denied.tsx`. Props: `{ feature?: string }` (optional, for specific message like "You don't have access to invoicing"). Renders: `ShieldOff` icon (Lucide, `size-12`, `text-slate-400`), heading from catalog (`common:permission.denied.heading` or `errors:api.forbidden`), description from catalog, "Go to dashboard" button (`Link` to dashboard). Centred layout matching `EmptyState`. Can be a Server Component (no hooks needed — strings can be hardcoded or resolved server-side). |
| 331.8 | Create form validation helper | 331B | 327A | New file or addition to `frontend/src/lib/error-handler.ts`. Function: `scrollToFirstError()` — finds the first element with `aria-invalid="true"` or `.text-red-600` and calls `scrollIntoView({ behavior: 'smooth', block: 'center' })` + `focus()`. Used in form submit handlers after validation failure. |
| 331.9 | Integrate `classifyError` + `showToast` into project mutation handlers | 331B | 331A | Modify: `frontend/app/(app)/org/[slug]/projects/actions.ts`. Replace ad-hoc `toast()` calls in catch blocks with `classifyError(error)` + `showToast('error', classified.messageCode, { onRetry: retryable ? retryFn : undefined })`. Apply same pattern to 1-2 other high-traffic action files (e.g., `customers/actions.ts`, `team/actions.ts`). |
| 331.10 | Integrate `classifyError` + `showToast` into invoice/time entry mutations | 331B | 331A | Modify 2-3 files: invoice actions, time entry actions. Same pattern as 331.9. Target the most commonly used mutation handlers. Do not attempt to migrate ALL action files — just the top 5 most-used. |
| 331.11 | Add form validation messages from catalog | 331B | 327A | Modify 2-3 form components that currently use generic "Invalid value" messages. Replace with catalog messages: `validation.required`, `validation.email`, `validation.positiveNumber`, `validation.maxLength`. Add `scrollToFirstError()` call on form submit failures. Target: customer create form, project create form, invoice create form. |
| 331.12 | Write tests for PermissionDenied and form helpers | 331B | 331.7, 331.8 | New file: `frontend/__tests__/permission-denied.test.tsx`. Tests (~5): (1) `renders ShieldOff icon and heading`; (2) `renders dashboard link`; (3) `renders custom feature name in message`; (4) `scrollToFirstError finds and focuses invalid field`; (5) `classifyError integrated in action file returns correct toast` (spot check). |

### Key Files

**Slice 331A -- Create:**
- `frontend/src/lib/error-handler.ts` — `classifyError()`, `showToast()`, `ClassifiedError` types
- `frontend/components/error-boundary.tsx` — `ErrorBoundary` class component + `ErrorFallback`
- `frontend/__tests__/error-handling.test.tsx` — 8 tests

**Slice 331A -- Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` — wrap children in ErrorBoundary

**Slice 331A -- Read for context:**
- `frontend/src/messages/en/errors.json` — error message codes
- `frontend/src/messages/index.ts` — `useMessage` hook
- `frontend/lib/api.ts` — existing error handling patterns
- `frontend/app/(app)/org/[slug]/layout.tsx` — current layout structure (sidebar, header, children)

**Slice 331B -- Create:**
- `frontend/components/permission-denied.tsx` — PermissionDenied component
- `frontend/__tests__/permission-denied.test.tsx` — 5 tests

**Slice 331B -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/actions.ts` — integrate classifyError + showToast
- `frontend/app/(app)/org/[slug]/customers/actions.ts` — integrate classifyError + showToast
- `frontend/app/(app)/org/[slug]/team/actions.ts` — integrate classifyError + showToast
- 2-3 additional action files (invoices, time entries)
- 2-3 form components for validation message improvements

**Slice 331B -- Read for context:**
- `frontend/src/lib/error-handler.ts` — classifyError and showToast from 331A
- `frontend/components/error-boundary.tsx` — ErrorFallback visual pattern (match PermissionDenied layout)
- Existing action files — understand current catch block patterns

### Architecture Decisions

- **Frontend classification over backend error codes**: The backend already returns structured `ProblemDetail` responses. Adding a classification enum to every error response would require touching every controller. Frontend classification maps existing status codes to UX-appropriate messages.
- **React class component for ErrorBoundary**: Error boundaries require `componentDidCatch` — this is a React limitation (no hooks equivalent). The boundary is a thin wrapper; the `ErrorFallback` it renders is a functional component.
- **Sidebar/header outside boundary**: Navigation must remain functional during page errors. Wrapping only the content area means users can always navigate away from a broken page.
- **Incremental migration of toast calls**: Only the top 5 most-used action files are migrated in this phase. Remaining files migrate incrementally in future phases. This keeps the slice scope manageable.

---

## Risk Register

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | `EmptyState` enhancement breaks existing call sites | M | Additive-only props (`onAction`, `secondaryLink`). No existing prop removed or renamed. Run full test suite after changes. |
| 2 | `useMessage` hook client-only but needed in Server Components | H | Design: pages resolve strings using the hook in `"use client"` wrappers or import JSON directly in Server Components. `EmptyState` itself stays as Server Component — strings are passed as plain text props. |
| 3 | Help point pages are complex — builder may struggle to find correct insertion points | M | Task notes specify exact file paths and the `<h3>Title <HelpTip code="..." /></h3>` pattern. Builder scouts read component headings, not full implementation. |
| 4 | V66 migration conflicts with another phase running concurrently | L | Check latest migration number before implementation. If V66 is taken, use V67. The migration is a single `ALTER TABLE ADD COLUMN` — trivially reorderable. |
| 5 | SWR dependency for `useOnboardingProgress` hook — SWR may not be installed | M | Check `frontend/package.json` for SWR. If not present, use a simple `useEffect` + `fetch` pattern with manual cache state instead. |
| 6 | `ErrorBoundary` as class component — may conflict with React 19 patterns | L | Class components are fully supported in React 19. Error boundaries are the one case where class components are still required. No risk. |
| 7 | Too many page modifications in 328B (12 files) | M | Split guidance: each file modification is small (add/replace a conditional render with `EmptyState`). The component is already proven from 328A. If scope is too large, defer dashboard widget empty states to a follow-up. |
| 8 | Message catalog JSON keys drift from actual usage | L | Dev-mode `console.warn` catches typos. CI lint could verify key usage in future — out of scope for this phase. |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/empty-state.tsx` - Core component to enhance with new props (onAction, secondaryLink)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Entity to extend with onboardingDismissedAt column
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/layout.tsx` - Layout to wrap with ErrorBoundary
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/dashboard/page.tsx` - Dashboard page for GettingStartedCard integration
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase43-ux-quality-pass.md` - Full architecture doc with all specifications and inventories
