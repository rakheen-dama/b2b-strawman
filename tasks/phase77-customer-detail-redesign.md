# Phase 77 — Customer Detail Page Redesign

Phase 77 restructures the customer detail page (`/org/[slug]/customers/[id]`) from a vertical-stack layout to a **header card + grouped tabs** layout. This is a **frontend-only** phase — no backend changes, no migrations, no new API endpoints. The server component in `page.tsx` continues to fetch all the same data; only the rendering structure changes.

The current layout has 7 action buttons sprawled horizontally, a metadata wall pushing tabs below the fold, and 11 flat tabs in a single row. Phase 77 applies the same header card + grouped tabs pattern that the matter detail page uses post-Phase 73: a compact `ClientHeaderCard` with smart primary action, a `ClientOverflowMenu` consolidating all secondary actions, and a `GroupedTabBar` (shared component) collapsing 15 tab IDs into 6 logical groups.

**Architecture doc**: `architecture/phase77-customer-detail-redesign.md`

**ADRs**: [ADR-298](../adr/ADR-298-shared-grouped-tab-bar.md) (shared GroupedTabBar), [ADR-299](../adr/ADR-299-header-card-entity-detail-layout.md) (header card layout)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 556 | Shared Component Extraction | Frontend | -- | M | 556A, 556B | **Done** (PR #1391) |
| 557 | ClientHeaderCard + ClientOverflowMenu | Frontend | -- | M | 557A, 557B | **Done** (PR #1392) |
| 558 | CustomerGroupedTabs + Tab Panels | Frontend | 556 | M | 558A, 558B | **Done** (PR #1393) |
| 559 | ClientOverviewTab | Frontend | -- | S | 559A | |
| 560 | Page Integration + Polish | Frontend | 556, 557, 558, 559 | L | 560A, 560B | |
| 561 | QA Testplan Updates | Frontend | 556-560 | S | 561A | |

## Dependency Graph

```
556A (GroupedTabBar → shared)     557A (ClientHeaderCard)
     |                                |
     v                                v
556B (Tab Groups Split +          557B (ClientOverflowMenu)
     CUSTOMER_TAB_GROUPS)             |
     |                                |
     v                                |
558A (CustomerGroupedTabs)            |
     |                                |
     v                                |
558B (Details/Fields/Tags tabs)       |
     |                                |
     +------+-------------------------+
     |      |
     |   559A (ClientOverviewTab)
     |      |
     +------+
        |
        v
     560A (page.tsx Integration)
        |
        v
     560B (Polish + Edge Cases)
        |
        v
     561A (QA Testplan Updates)
```

**Parallel tracks**:
- 556 (Shared Component Extraction) and 557 (Header Card + Overflow) have no shared dependencies — can start immediately in parallel.
- 558 depends on 556 (needs shared `GroupedTabBar` and `CUSTOMER_TAB_GROUPS`).
- 559 (Overview Tab) depends on nothing — can run in parallel with 556/557/558.
- 560 depends on all prior epics (integration step).
- 561 runs last after all layout changes are complete.

## Implementation Order

### Stage 1: Foundation (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 556 | 556A | Move `GroupedTabBar` from `components/projects/` to `components/shared/`. Update `ProjectTabs` import. Verify matter detail page unchanged. **Done** (PR #1391) |
| 1b | Epic 556 | 556B | Split `tab-groups.ts` into shared types + entity-specific constants. Create `CUSTOMER_TAB_GROUPS`. Depends on 556A. **Done** (PR #1391) |
| 1c | Epic 557 | 557A | `ClientHeaderCard` with name, badges, contact, context, smart primary action. Independent of 556. **Done** (PR #1392) |
| 1d | Epic 557 | 557B | `ClientOverflowMenu` with all 8 actions and gating rules. Depends on 557A (overflow renders inside header card). **Done** (PR #1392) |

### Stage 2: Tabs + Overview (Parallel, after 556B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 558 | 558A | `CustomerGroupedTabs` component wiring `GroupedTabBar` with `CUSTOMER_TAB_GROUPS`. Module-gating and URL state. Depends on 556B. **Done** (PR #1393) |
| 2b | Epic 558 | 558B | `ClientDetailsTab`, `ClientFieldsTab`, `ClientTagsTab`. Depends on 558A (tabs must exist as the container). **Done** (PR #1393) |
| 2c | Epic 559 | 559A | `ClientOverviewTab` aggregating setup guidance, financial summary, AI panels. Independent — component built in isolation. |

### Stage 3: Integration (after Stage 1 + Stage 2)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 560 | 560A | Refactor `page.tsx` to wire all components. Depends on all prior slices. |
| 3b | Epic 560 | 560B | Responsive polish, dark mode, edge-case testing. Depends on 560A. |

### Stage 4: QA (after Stage 3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 561 | 561A | Migrate all QA testplan selectors. Add `MIGRATION-NOTES.md` Customer Detail section. Depends on all prior slices. |

### Timeline

```
Stage 1:  [556A → 556B]  //  [557A → 557B]      ← foundation (parallel tracks)
Stage 2:  [558A → 558B]  //  [559A]              ← tabs + overview (after 556B)
Stage 3:  [560A → 560B]                          ← integration + polish (after all above)
Stage 4:  [561A]                                 ← QA testplan (after all above)
```

---

## Epic 556: Shared Component Extraction

**Goal**: Move `GroupedTabBar` from the projects domain to a shared location. Split `tab-groups.ts` into shared types, project-specific constants, and customer-specific constants. Create `CUSTOMER_TAB_GROUPS` constant. Verify that matter detail page tab navigation is unaffected.

**References**: Architecture doc Sections 77.3 (shared component extraction), 77.4 (tab group configuration). [ADR-298](../adr/ADR-298-shared-grouped-tab-bar.md).

**Dependencies**: None (first epic)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **556A** | 556.1-556.3 | Move `GroupedTabBar` to `components/shared/`, update `ProjectTabs` import, verify existing tests pass (~0 new tests, run existing) | **Done** (PR #1391) |
| **556B** | 556.4-556.8 | Extract shared types to `tab-group-types.ts`, rename `tab-groups.ts` to `project-tab-groups.ts`, create `customer-tab-groups.ts`, unit tests (~8 tests) | **Done** (PR #1391) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 556.1 | Move `GroupedTabBar` to shared location | 556A | | Move `frontend/components/projects/grouped-tab-bar.tsx` to `frontend/components/shared/grouped-tab-bar.tsx`. Create the `components/shared/` directory. The component source is unchanged — only the file location changes. ~0 lines changed in the component itself. Pattern: the component is entity-agnostic (accepts `groups: TabGroup[]` prop). Per ADR-298, `components/shared/` is the correct home for cross-domain UI patterns. |
| 556.2 | Update `ProjectTabs` import path | 556A | | Modify `frontend/components/projects/project-tabs.tsx`. Change import from `from "@/components/projects/grouped-tab-bar"` to `from "@/components/shared/grouped-tab-bar"`. Single import line change. Verify no other files import from the old path (only `ProjectTabs` is a consumer). ~1 line changed. |
| 556.3 | Verify existing grouped tab bar tests pass | 556A | | Run `frontend/__tests__/components/projects/grouped-tab-bar.test.tsx` and `frontend/__tests__/components/projects/project-tabs-grouped.test.tsx`. Update import paths in test files if they import `GroupedTabBar` directly from the old path. Verify matter detail page tab navigation is unaffected (all existing tests pass). ~2-4 import line changes in test files. |
| 556.4 | Extract shared types to `tab-group-types.ts` | 556B | | Create `frontend/lib/constants/tab-group-types.ts`. Extract from current `tab-groups.ts` (162 lines): `TabDefinition` interface, `TabGroup` interface, `resolveTabFromUrl(tabParam, groups)` function (already parameterised by `groups`), `getGroupForTab(tabId, groupMap)` function (refactored to accept `groupMap` parameter — currently uses module-level constant, has zero external callers so safe to change signature), new `buildTabIdToGroupMap(groups)` helper. **Critical**: the shared `resolveTabFromUrl` must NOT include the `members → staffing` redirect (that is matter-specific). Extract the pure group/tab resolver only. ~60 lines. Pattern: follow `lib/constants/promoted-field-slugs.ts` for constant export structure. |
| 556.5 | Rename `tab-groups.ts` to `project-tab-groups.ts` | 556B | | Rename `frontend/lib/constants/tab-groups.ts` to `frontend/lib/constants/project-tab-groups.ts`. This file retains `TAB_GROUPS` (the matter-specific constant), `MEMBERS_TAB_REDIRECT`, and `TAB_ID_TO_GROUP_MAP`. Update the file to import shared types from `tab-group-types.ts`: `import type { TabGroup, TabDefinition } from "./tab-group-types"`. Update `TAB_ID_TO_GROUP_MAP` to use `buildTabIdToGroupMap(TAB_GROUPS)` from shared. Move the `members → staffing` redirect logic: `ProjectTabs` must apply `MEMBERS_TAB_REDIRECT` **before** calling `resolveTabFromUrl`. ~20 lines changed. |
| 556.6 | Update all imports referencing old `tab-groups.ts` | 556B | | Update import paths in all files that import from `@/lib/constants/tab-groups`: (1) `frontend/components/projects/project-tabs.tsx` — change to `from "@/lib/constants/project-tab-groups"`, (2) `frontend/components/shared/grouped-tab-bar.tsx` — change type imports to `from "@/lib/constants/tab-group-types"`, (3) any test files that import tab group constants. Grep for `tab-groups` across frontend to find all consumers. ~3-5 import lines changed. |
| 556.7 | Create `customer-tab-groups.ts` | 556B | | Create `frontend/lib/constants/customer-tab-groups.ts`. Define `CUSTOMER_TAB_GROUPS: readonly TabGroup[]` with 6 groups per architecture doc Section 77.4.1: Details (details, fields, tags), Overview (overview), Work (projects, documents, generated), Finance (invoices, rates, retainer, financials, trust), Compliance (onboarding, requests), Activity (audit). Export `CUSTOMER_TAB_ID_TO_GROUP_MAP` using `buildTabIdToGroupMap(CUSTOMER_TAB_GROUPS)`. Import shared types from `tab-group-types.ts`. ~50 lines. Pattern: follow the exact `TabGroup[]` shape from `project-tab-groups.ts`. |
| 556.8 | Add unit tests for shared types and customer tab groups | 556B | | Create `frontend/__tests__/lib/constants/tab-group-types.test.ts` (~8 tests): (1) `resolveTabFromUrl(null, CUSTOMER_TAB_GROUPS)` returns `{ groupId: "overview", tabId: "overview" }`, (2) `resolveTabFromUrl("invoices", CUSTOMER_TAB_GROUPS)` returns `{ groupId: "finance", tabId: "invoices" }`, (3) `resolveTabFromUrl("work", CUSTOMER_TAB_GROUPS)` returns group-level alias `{ groupId: "work", tabId: "projects" }`, (4) `resolveTabFromUrl("unknown", CUSTOMER_TAB_GROUPS)` falls back to overview, (5) `CUSTOMER_TAB_ID_TO_GROUP_MAP` maps all 15 tab IDs correctly, (6) `buildTabIdToGroupMap` handles empty groups, (7) `resolveTabFromUrl` with project `TAB_GROUPS` still works (backward compat), (8) `getGroupForTab("invoices", CUSTOMER_TAB_ID_TO_GROUP_MAP)` returns "finance". Pattern: follow `__tests__/components/projects/grouped-tab-bar.test.tsx` for test structure. |

### Key Files

**Slice 556A — Create:**
- `frontend/components/shared/grouped-tab-bar.tsx` (moved from `components/projects/`)

**Slice 556A — Modify:**
- `frontend/components/projects/project-tabs.tsx` — Update import path for GroupedTabBar
- `frontend/__tests__/components/projects/grouped-tab-bar.test.tsx` — Update import path if importing directly
- `frontend/__tests__/components/projects/project-tabs-grouped.test.tsx` — Update import path if importing directly

**Slice 556A — Delete:**
- `frontend/components/projects/grouped-tab-bar.tsx` (moved, not duplicated)

**Slice 556A — Read for context:**
- `frontend/components/projects/project-tabs.tsx` — Sole consumer of GroupedTabBar, verify no other imports
- `adr/ADR-298-shared-grouped-tab-bar.md` — Decision rationale

**Slice 556B — Create:**
- `frontend/lib/constants/tab-group-types.ts`
- `frontend/lib/constants/customer-tab-groups.ts`
- `frontend/__tests__/lib/constants/tab-group-types.test.ts`

**Slice 556B — Modify:**
- `frontend/lib/constants/tab-groups.ts` — Rename to `project-tab-groups.ts`, update imports
- `frontend/components/projects/project-tabs.tsx` — Update import from `tab-groups` to `project-tab-groups`
- `frontend/components/shared/grouped-tab-bar.tsx` — Update type imports to `tab-group-types`

**Slice 556B — Read for context:**
- `frontend/lib/constants/tab-groups.ts` — Current 162-line file to split
- `architecture/phase77-customer-detail-redesign.md` — Section 77.3 (refactoring plan), Section 77.4.1 (CUSTOMER_TAB_GROUPS constant)

### Architecture Decisions

- **`components/shared/` as the new shared directory**: Per ADR-298, `components/shared/` is the correct home for cross-domain UI patterns. `GroupedTabBar` is the first occupant. The directory must not become a dumping ground — only components with genuine cross-domain usage belong here.
- **Parameterised utilities, not entity-specific copies**: `resolveTabFromUrl` already accepts a `groups` parameter. `getGroupForTab` is refactored to accept a `groupMap` parameter. Both work for any `TabGroup[]` without modification.
- **`members → staffing` redirect stays in `ProjectTabs`**: The shared `resolveTabFromUrl` is a pure resolver with no entity-specific redirects. `ProjectTabs` applies the redirect before calling the shared function. `CustomerGroupedTabs` calls the shared function directly.
- **`tab-groups.ts` renamed, not duplicated**: Renaming to `project-tab-groups.ts` makes the file's scope explicit. A barrel re-export at the old path was considered but rejected — there are only 2-3 consumers, so direct import updates are simpler.

---

## Epic 557: ClientHeaderCard + ClientOverflowMenu

**Goal**: Create the customer header card with name, status badges, contact summary, context line, smart primary action button, and overflow menu. These components are developed in isolation (no `page.tsx` integration yet). The header card mirrors `MatterHeaderCard` structure but with customer-specific content.

**References**: Architecture doc Sections 77.2.4 (ClientHeaderCard), 77.2.5 (smart primary action), 77.2.6 (ClientOverflowMenu). [ADR-299](../adr/ADR-299-header-card-entity-detail-layout.md).

**Dependencies**: None. Can be developed in parallel with Epic 556.

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **557A** | 557.1-557.4 | `ClientHeaderCard` with name, badges, contact, context line, smart primary action for all 8 lifecycle states. Unit tests (~6 tests) | **Done** (PR #1392) |
| **557B** | 557.5-557.8 | `ClientOverflowMenu` with all 8 menu items, gating rules, OBS-2103-safe dialog rendering. Unit tests (~6 tests) | **Done** (PR #1392) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 557.1 | Create `ClientHeaderCard` component | 557A | | Create `frontend/components/customers/client-header-card.tsx`. `"use client"`. Props interface per architecture doc Section 77.2.4: `customerId`, `customerName`, `customerStatus`, `lifecycleStatus`, `email`, `phone`, `lifecycleStatusChangedAt`, `linkedProjectCount`, `kycSummary`, `xeroConnected`, `slug`, `isAdmin`, `isOwner`. All props serializable (RSC boundary). Renders inside Shadcn `Card` with `data-testid="client-header-card"`. Layout: 5 rows per Section 77.2.4 content table — name (row 1), badges (row 2), contact (row 3), context (row 4), actions (row 5). Name: `font-display text-xl font-semibold`, `line-clamp-2` with `Tooltip` for overflow. Badges: inline `Badge` row, `flex-wrap gap-2`. Contact: `text-sm text-slate-600`. Context: `text-sm text-muted-foreground`. Actions: `flex shrink-0 items-center gap-2`, right-aligned. ~120 lines. Pattern: follow `components/projects/matter-header-card.tsx` (122 lines) for card structure and Shadcn Card usage. |
| 557.2 | Implement smart primary action logic | 557A | | Within `client-header-card.tsx`. Add `getSmartPrimaryAction(lifecycleStatus)` function that returns `{ label, variant, disabled, tooltip } | null` per architecture doc Section 77.2.5 table: PROSPECT → "Start Onboarding" (accent), ONBOARDING → "Activate Customer" (accent, disabled if blockers), ACTIVE/DORMANT → "Edit" (outline), OFFBOARDING → "Complete Offboarding" (accent), OFFBOARDED/ANONYMIZED → null, ARCHIVED → "Restore" (outline). Render as `Button` in the actions row. When null, render only the overflow trigger. ~40 lines within the component. Pattern: pure function of lifecycle status, no state management needed. |
| 557.3 | Add status badges rendering | 557A | | Within `client-header-card.tsx`. Render badge row: (1) Customer status badge — `Badge variant={customerStatus === "ACTIVE" ? "success" : "neutral"}`, (2) Lifecycle status badge — map lifecycle status to badge variant (PROSPECT → neutral, ONBOARDING → warning, ACTIVE → success, DORMANT → neutral, OFFBOARDING → warning, OFFBOARDED → neutral, ANONYMIZED → destructive), (3) KYC badge — if `kycSummary` exists: verified → `Badge variant="success"` with CheckCircle icon, not verified → `Badge variant="warning"`, (4) Xero badge — if `xeroConnected`: `Badge variant="neutral"` with external link icon. Follow existing `XeroContactBadge.tsx` and `kyc-status-badge.tsx` patterns in `components/customers/`. ~30 lines within the component. |
| 557.4 | Add unit tests for `ClientHeaderCard` | 557A | | Create `frontend/__tests__/components/customers/client-header-card.test.tsx` (~6 tests): (1) renders customer name with line-clamp, (2) renders "Start Onboarding" button for PROSPECT lifecycle, (3) renders "Edit" button for ACTIVE lifecycle, (4) renders no primary action for OFFBOARDED lifecycle, (5) renders KYC and Xero badges when present, (6) hides primary action and shows minimal badges for ANONYMIZED state. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/projects/overflow-actions-menu.test.tsx` for test structure. Use `@testing-library/react` + `happy-dom`. |
| 557.5 | Create `ClientOverflowMenu` component | 557B | | Create `frontend/components/customers/client-overflow-menu.tsx`. `"use client"`. Props: `customerId`, `customerName`, `customerStatus`, `lifecycleStatus`, `slug`, `isAdmin`, `isOwner`, `isAnonymized`, `templates` (for Generate Document), `aiProviderConfigured`, `conflictCheckEnabled`, `kycConfigured`, `kycVerified`. Uses Shadcn `DropdownMenu` with `MoreHorizontal` icon trigger, `data-testid="client-overflow-trigger"`. Menu structure per architecture doc Section 77.2.6: Edit Client, Summarise Activity, separator, Generate Document, Run Conflict Check, Verify KYC, separator, Export Data, separator, Anonymize (destructive), Archive (destructive). Gating rules per Section 77.2.6 table. When ANONYMIZED, show only Export Data. **Critical OBS-2103**: Dialogs (Generate Document, Export Data, Anonymize, Archive) rendered **outside** `DropdownMenuContent` with controlled `open` state. Menu items set state (e.g., `setGenerateDialogOpen(true)`), dialogs render as siblings of `<DropdownMenu>`. ~150 lines. Pattern: follow `components/projects/overflow-actions-menu.tsx` (229 lines) — especially lines 64-69 for dialog rendering pattern. |
| 557.6 | Wire existing dialog components into overflow menu | 557B | | Within `client-overflow-menu.tsx`, render controlled dialogs as siblings of `<DropdownMenu>`: (1) `EditCustomerDialog` — import from `components/customers/edit-customer-dialog.tsx`, controlled via `editOpen` state, (2) `DataExportDialog` — import from `components/customers/data-export-dialog.tsx`, controlled via `exportOpen` state, (3) `AnonymizeCustomerDialog` — import from `components/customers/anonymize-customer-dialog.tsx`, controlled via `anonymizeOpen` state, (4) `ArchiveCustomerDialog` — import from `components/customers/archive-customer-dialog.tsx`, controlled via `archiveOpen` state. Each dialog uses `open` + `onOpenChange` props for controlled state. Generate Document navigates or opens a route-based dialog (follow existing Generate Document pattern in `page.tsx`). ~40 lines of dialog wiring. Pattern: follow exact OBS-2103 pattern from `overflow-actions-menu.tsx`. |
| 557.7 | Wire `ClientOverflowMenu` into `ClientHeaderCard` | 557B | | Modify `frontend/components/customers/client-header-card.tsx`. Import and render `<ClientOverflowMenu>` in the actions row (row 5), adjacent to the smart primary action button. Pass all required props through from `ClientHeaderCard` props (add any missing props to the `ClientHeaderCardProps` interface: `templates`, `aiProviderConfigured`, `conflictCheckEnabled`, `kycConfigured`, `kycVerified`). ~15 lines added to header card. |
| 557.8 | Add unit tests for `ClientOverflowMenu` | 557B | | Create `frontend/__tests__/components/customers/client-overflow-menu.test.tsx` (~6 tests): (1) renders all menu items for admin user with all modules, (2) hides Anonymize for non-owner, (3) hides Archive for non-admin, (4) shows only Export Data for ANONYMIZED customer, (5) hides Generate Document when no templates, (6) hides Conflict Check when conflict_check module disabled. Mock dialog components as simple divs. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/projects/overflow-actions-menu.test.tsx`. |

### Key Files

**Slice 557A — Create:**
- `frontend/components/customers/client-header-card.tsx`
- `frontend/__tests__/components/customers/client-header-card.test.tsx`

**Slice 557A — Read for context:**
- `frontend/components/projects/matter-header-card.tsx` — Reference pattern for header card structure (122 lines)
- `frontend/components/customers/kyc-status-badge.tsx` — KYC badge rendering
- `frontend/components/customers/XeroContactBadge.tsx` — Xero badge rendering
- `frontend/components/customers/completeness-badge.tsx` — Completeness ring pattern
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Current header section to understand data available (lines ~200-400)
- `frontend/CLAUDE.md` — RSC serialization boundary, anti-patterns

**Slice 557B — Create:**
- `frontend/components/customers/client-overflow-menu.tsx`
- `frontend/__tests__/components/customers/client-overflow-menu.test.tsx`

**Slice 557B — Modify:**
- `frontend/components/customers/client-header-card.tsx` — Add overflow menu rendering in actions row

**Slice 557B — Read for context:**
- `frontend/components/projects/overflow-actions-menu.tsx` — Reference pattern for overflow menu with OBS-2103-safe dialog rendering (229 lines, especially lines 64-69)
- `frontend/components/customers/edit-customer-dialog.tsx` — Dialog to wire
- `frontend/components/customers/data-export-dialog.tsx` — Dialog to wire
- `frontend/components/customers/anonymize-customer-dialog.tsx` — Dialog to wire
- `frontend/components/customers/archive-customer-dialog.tsx` — Dialog to wire
- `frontend/CLAUDE.md` — Dialog Trigger Composition anti-pattern (OBS-2103)

### Architecture Decisions

- **`ClientHeaderCard` is a separate component from `MatterHeaderCard`**: Per ADR-299 (Neutral consequences), entity identity content differs significantly. Sharing a layout component would require too many conditional branches. Two focused components are easier to maintain.
- **Smart primary action as a pure function of lifecycle status**: No additional state management needed. The lifecycle status is fetched server-side and passed as a prop. The button variant is a pure function of status.
- **Overflow menu renders inside the header card**: Unlike `MatterHeaderCard` where `OverflowActionsMenu` is rendered adjacent by `page.tsx`, `ClientHeaderCard` includes the overflow menu internally. This is a deliberate simplification — the header card owns its actions row.
- **OBS-2103 dialog rendering pattern**: Dialogs triggered from overflow menu items are rendered **outside** `DropdownMenuContent` as controlled components. This prevents the Radix Slot collision. The pattern is established in `overflow-actions-menu.tsx` and documented in `frontend/CLAUDE.md`.
- **`ClientOverflowMenu` props include all gating data**: Rather than having the menu query for module flags, all gating data is passed as props from the server component through the header card. This keeps the client component pure and testable.

---

## Epic 558: CustomerGroupedTabs + Tab Panels

**Goal**: Create `CustomerGroupedTabs` to replace `CustomerTabs`. Wire up `GroupedTabBar` with `CUSTOMER_TAB_GROUPS`. Create the three new tab panel components for the Details group: `ClientDetailsTab`, `ClientFieldsTab`, `ClientTagsTab`. Implement module-gating and URL state management for all 15 tab IDs.

**References**: Architecture doc Sections 77.2.7 (CustomerGroupedTabs), 77.4.2 (group visibility rules), 77.4.3 (URL resolution). [ADR-298](../adr/ADR-298-shared-grouped-tab-bar.md).

**Dependencies**: Epic 556 (shared `GroupedTabBar` at `components/shared/` and `CUSTOMER_TAB_GROUPS` in `lib/constants/customer-tab-groups.ts` must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **558A** | 558.1-558.4 | `CustomerGroupedTabs` component with `GroupedTabBar`, module-gating, URL state management, all 15 tab IDs, unit tests (~8 tests) | **Done** (PR #1393) |
| **558B** | 558.5-558.8 | `ClientDetailsTab`, `ClientFieldsTab`, `ClientTagsTab` panel components, unit tests (~4 tests) | **Done** (PR #1393) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 558.1 | Create `CustomerGroupedTabs` component | 558A | | Create `frontend/components/customers/customer-grouped-tabs.tsx`. `"use client"`. Props interface per architecture doc Section 77.2.7: `detailsPanel`, `fieldsPanel`, `tagsPanel`, `overviewPanel`, `projectsPanel`, `documentsPanel`, `generatedPanel?`, `invoicesPanel?`, `ratesPanel?`, `retainerPanel?`, `financialsPanel?`, `trustPanel?`, `onboardingPanel?`, `requestsPanel?`, `auditPanel?` (all `ReactNode`). Internal: (1) Import `GroupedTabBar` from `@/components/shared/grouped-tab-bar`, (2) Import `CUSTOMER_TAB_GROUPS` from `@/lib/constants/customer-tab-groups`, (3) Import `resolveTabFromUrl` from `@/lib/constants/tab-group-types`, (4) Call `useSearchParams()` to read `?tab=` param, (5) Call `resolveTabFromUrl(tabParam, visibleGroups)` to get active group + tab, (6) Build `visibleGroups` by applying module-gating to `CUSTOMER_TAB_GROUPS` (see 558.2), (7) Render `<GroupedTabBar>` + conditional panel content via `Tabs`/`TabsContent`. ~150 lines. Pattern: follow `components/projects/project-tabs.tsx` (342 lines) for structure, but customer-specific. |
| 558.2 | Implement module-gating logic | 558A | | Within `customer-grouped-tabs.tsx`. Apply visibility rules per architecture doc Section 77.4.2: (1) Call `useAuditTabVisible()` for audit tab gating (replicate from existing `customer-tabs.tsx` line 103), (2) Call `useModuleEnabled("trust_accounting")` for trust tab (or receive as prop), (3) Gate each sub-tab by `!!panel` presence + module/capability checks: `generated: !!generatedPanel`, `invoices: !!invoicesPanel`, `rates: !!ratesPanel`, `retainer: !!retainerPanel`, `financials: !!financialsPanel`, `trust: !!trustPanel && trustAccountingEnabled`, `onboarding: !!onboardingPanel`, `requests: !!requestsPanel`, `audit: !!auditPanel && auditVisible`. (4) Group-level rule: hide group if all sub-tabs hidden. (5) Single-tab group behaviour: already handled by `GroupedTabBar`. Apply terminology rewriting via `useTerminology()` to tab labels. ~40 lines within the component. Pattern: follow module-gating in `project-tabs.tsx` (search for `useOrgProfile`, `useAuditTabVisible`). |
| 558.3 | Implement URL state management | 558A | | Within `customer-grouped-tabs.tsx`. Tab changes update URL via `useRouter().replace()` (no history entry). Default tab: `overview` (not `projects` as current). Handle `handleTabChange(tabId)` callback from `GroupedTabBar` — update `?tab=` search param. Read initial tab from `searchParams.get("tab")`. All existing tab IDs backward compatible: `projects`, `documents`, `onboarding`, `invoices`, `retainer`, `requests`, `rates`, `generated`, `financials`, `trust`, `audit`. New IDs: `details`, `fields`, `tags`, `overview`. ~20 lines within the component. Pattern: follow `project-tabs.tsx` URL state pattern. |
| 558.4 | Add unit tests for `CustomerGroupedTabs` | 558A | | Create `frontend/__tests__/components/customers/customer-grouped-tabs.test.tsx` (~8 tests): (1) renders GroupedTabBar with 6 groups when all panels provided, (2) hides Finance group when all finance panels are null, (3) hides Activity group when auditVisible is false, (4) defaults to overview tab when no `?tab=` param, (5) resolves `?tab=invoices` to Finance group, (6) resolves group-level alias `?tab=work` to projects, (7) hides trust tab when trust_accounting disabled, (8) renders Compliance as standalone tab when only requests visible. Mock `useSearchParams`, `useRouter`, `useOrgProfile`, `useAuditTabVisible`, `useTerminology`. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/projects/project-tabs-grouped.test.tsx`. |
| 558.5 | Create `ClientDetailsTab` component | 558B | | Create `frontend/components/customers/client-details-tab.tsx`. `"use client"`. Props: `addressData` (address block props), `contactData` (contact card props), `businessDetails` (registration number, tax number, entity type, financial year end). Renders 2-column grid on desktop, single column on mobile: (1) `CustomerAddressBlock` (existing component from `customer-address-block.tsx`), (2) `CustomerContactCard` (existing component from `customer-contact-card.tsx`), (3) Business Details card below (full width, Shadcn `Card` with key-value rows). Same content as currently rendered inline above tabs, just inside a tab panel. `data-testid="client-details-tab"`. ~60 lines. Pattern: follow layout of current inline metadata section in `page.tsx`. |
| 558.6 | Create `ClientFieldsTab` component | 558B | | Create `frontend/components/customers/client-fields-tab.tsx`. `"use client"`. Props: `customerId`, `fieldDefinitions`, `fieldGroups`, `groupMembers`, `canEdit`. Renders `FieldGroupSelector` (from `components/field-definitions/FieldGroupSelector.tsx`) + `CustomFieldSection` (from `components/field-definitions/CustomFieldSection.tsx`). Same components, same props as current inline usage. `data-testid="client-fields-tab"`. ~40 lines. Pattern: follow existing `CustomFieldSection` + `FieldGroupSelector` usage in `page.tsx` (search for these components in the current customer `page.tsx`). |
| 558.7 | Create `ClientTagsTab` component | 558B | | Create `frontend/components/customers/client-tags-tab.tsx`. `"use client"`. Props: `customerId`, `customerTags`, `allTags`, `canEdit`. Renders `TagInput` (from `components/tags/TagInput.tsx`). Simple wrapper. `data-testid="client-tags-tab"`. ~25 lines. Pattern: follow existing `TagInput` usage in `page.tsx`. |
| 558.8 | Add unit tests for tab panel components | 558B | | Create `frontend/__tests__/components/customers/client-tab-panels.test.tsx` (~4 tests): (1) `ClientDetailsTab` renders address and contact cards, (2) `ClientFieldsTab` renders CustomFieldSection, (3) `ClientTagsTab` renders TagInput, (4) `ClientDetailsTab` renders business details card with key-value rows. Mock `CustomerAddressBlock`, `CustomerContactCard`, `CustomFieldSection`, `FieldGroupSelector`, `TagInput` as simple divs. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/customers/customer-address-block.test.tsx`. |

### Key Files

**Slice 558A — Create:**
- `frontend/components/customers/customer-grouped-tabs.tsx`
- `frontend/__tests__/components/customers/customer-grouped-tabs.test.tsx`

**Slice 558A — Read for context:**
- `frontend/components/customers/customer-tabs.tsx` — Current 215-line flat tab implementation (understand TabId union, module-gating, panel rendering)
- `frontend/components/projects/project-tabs.tsx` — Reference pattern for grouped tab integration (342 lines)
- `frontend/components/shared/grouped-tab-bar.tsx` — The shared component being consumed
- `frontend/lib/constants/customer-tab-groups.ts` — Tab group configuration (from 556B)
- `frontend/lib/constants/tab-group-types.ts` — Shared types and `resolveTabFromUrl` (from 556B)
- `frontend/components/audit/audit-timeline-tab.tsx` — `useAuditTabVisible()` hook
- `frontend/lib/org-profile.ts` — `useOrgProfile()` and `useModuleEnabled()` hooks
- `frontend/lib/terminology.ts` — `useTerminology()` for label rewriting

**Slice 558B — Create:**
- `frontend/components/customers/client-details-tab.tsx`
- `frontend/components/customers/client-fields-tab.tsx`
- `frontend/components/customers/client-tags-tab.tsx`
- `frontend/__tests__/components/customers/client-tab-panels.test.tsx`

**Slice 558B — Read for context:**
- `frontend/components/customers/customer-address-block.tsx` — Reused in details tab
- `frontend/components/customers/customer-contact-card.tsx` — Reused in details tab
- `frontend/components/field-definitions/CustomFieldSection.tsx` — Reused in fields tab
- `frontend/components/field-definitions/FieldGroupSelector.tsx` — Reused in fields tab
- `frontend/components/tags/TagInput.tsx` — Reused in tags tab
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Current inline rendering of address, contact, fields, tags (understand data flow and prop shapes)

### Architecture Decisions

- **`CustomerGroupedTabs` replaces `CustomerTabs` entirely**: The old `CustomerTabs` component (Framer Motion underline, 11 flat tabs) is superseded. It is deleted once `page.tsx` integration is complete in Epic 560.
- **Default tab changed from `projects` to `overview`**: The Overview tab provides a dashboard summary that orients the user before drilling into specific tabs. Deep links using `?tab=projects` continue to work.
- **Panel props as `ReactNode`**: Following the same pattern as `ProjectTabs`, all tab panel content is pre-rendered by the server component and passed as `ReactNode` props. This preserves the RSC boundary — `CustomerGroupedTabs` is a client component that receives serializable JSX.
- **Module-gating inside `CustomerGroupedTabs`, not in `page.tsx`**: The component internally calls `useAuditTabVisible()` and checks module flags. The server component may pass a non-null panel even when the user lacks the capability — the client component applies the final visibility gate.
- **Tab panel components are thin wrappers**: `ClientDetailsTab`, `ClientFieldsTab`, and `ClientTagsTab` are layout wrappers around existing components. They add `data-testid` attributes and responsive grid layout, but do not add new business logic.

---

## Epic 559: ClientOverviewTab

**Goal**: Create the Overview tab that aggregates setup guidance, lifecycle prompt, financial summary (unbilled time + retainer status), template readiness, and AI panels into a single dashboard view. This is the default landing tab when navigating to a customer detail page.

**References**: Architecture doc Sections 77.2.2 (component tree, overview children), 77.10 Slice 77D.

**Dependencies**: None for the component itself (developed in isolation). Integration with `CustomerGroupedTabs` happens in Epic 560.

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **559A** | 559.1-559.4 | `ClientOverviewTab` with setup progress, lifecycle prompt, financial summary grid, template readiness, AI panels, empty state. Unit tests (~5 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 559.1 | Create `ClientOverviewTab` component | 559A | | Create `frontend/components/customers/client-overview-tab.tsx`. `"use client"`. Props: `setupProgressData` (SetupProgressCard props or null), `lifecyclePrompt` (ReactNode or null), `unbilledTimeData` ({ amount, hours, createInvoiceHref, viewTimeHref } or null), `activeRetainer` (retainer summary data or null), `templateReadiness` (TemplateReadinessCard props or null), `pendingSuggestions` (ReactNode or null), `ficaPanel` (ReactNode or null). Layout: vertical stack of conditional sections. `data-testid="client-overview-tab"`. ~100 lines. Pattern: follow architecture doc Section 77.10 Slice 77D for content sections A-F. |
| 559.2 | Implement overview content sections | 559A | | Within `client-overview-tab.tsx`. Render sections top-to-bottom: (A) Client Readiness — `SetupProgressCard` (import from `components/setup/`), conditionally rendered when `setupProgressData` exists. (B) Lifecycle Action Prompt — render `lifecyclePrompt` ReactNode (pre-rendered by server component). (C) Financial Summary — 2-column grid on desktop (`grid gap-4 md:grid-cols-2`): unbilled time card (left) + retainer status card (right). Unbilled time card: Shadcn `Card` showing amount, hours, "Create Invoice" and "View Time" links. Retainer status card: Shadcn `Card` showing retainer amount, status, period usage if available. (D) Template Readiness — `TemplateReadinessCard` (import from `components/setup/`), conditionally rendered. (E) Pending AI Suggestions — render `pendingSuggestions` ReactNode. (F) FICA Panel — render `ficaPanel` ReactNode. ~60 lines of content rendering within the component. Pattern: follow existing setup guidance rendering in current `page.tsx`. |
| 559.3 | Implement "everything looks good" empty state | 559A | | Within `client-overview-tab.tsx`. When all sections are empty (no setup data, no lifecycle prompt, no unbilled time, no retainer, no template readiness, no AI panels), render a clean summary: customer name (passed as additional prop `customerName`), lifecycle status badge, linked project count, "Everything looks good" message with a CheckCircle icon. `text-center py-12` styling. ~20 lines. Pattern: follow empty state patterns in `components/ui/` (search for "no results" or "empty" patterns). |
| 559.4 | Add unit tests for `ClientOverviewTab` | 559A | | Create `frontend/__tests__/components/customers/client-overview-tab.test.tsx` (~5 tests): (1) renders SetupProgressCard when setup data exists, (2) hides SetupProgressCard when setup data is null, (3) renders unbilled time and retainer cards in 2-column grid, (4) renders "everything looks good" state when all sections empty, (5) renders AI suggestion and FICA panels when provided. Mock `SetupProgressCard`, `TemplateReadinessCard` as simple divs. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/customers/customer-setup-guidance.test.tsx`. |

### Key Files

**Slice 559A — Create:**
- `frontend/components/customers/client-overview-tab.tsx`
- `frontend/__tests__/components/customers/client-overview-tab.test.tsx`

**Slice 559A — Read for context:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Current inline rendering of setup progress, unbilled time, template readiness, lifecycle prompt, AI panels (understand data shapes and prop types)
- `frontend/components/setup/` — SetupProgressCard, ActionCard, TemplateReadinessCard components
- `frontend/components/assistant/queue/pending-suggestions-widget.tsx` — AI suggestions widget
- `frontend/components/ai/fica-verification-panel.tsx` — FICA verification panel
- `frontend/components/projects/kpi-dashboard.tsx` — Reference for dashboard-style tab content (from Phase 73)
- `frontend/__tests__/components/customers/customer-setup-guidance.test.tsx` — Test pattern reference

### Architecture Decisions

- **Overview sections receive pre-rendered ReactNode props where possible**: Lifecycle prompt, pending suggestions, and FICA panel are pre-rendered by the server component and passed as `ReactNode`. This avoids duplicating server-side data fetching logic in the client component.
- **Financial summary uses new Shadcn Cards, not existing components**: The unbilled time card and retainer status card are new inline card renders (not separate component files) because they are simple key-value displays specific to the overview tab. If they grow in complexity, they can be extracted later.
- **"Everything looks good" is not an empty state — it is a positive confirmation**: When there are no outstanding items, the overview should communicate that the client is in good standing, not that there is nothing to show. This follows a different pattern from table empty states ("No results found").
- **`ClientOverviewTab` is a client component**: It needs to conditionally render sections based on prop presence, which involves client-side logic. The sections themselves may include interactive elements (links, buttons).

---

## Epic 560: Page Integration + Polish

**Goal**: Refactor `page.tsx` to wire all new components together. Slim the server component from ~983 lines to ~400 lines of data fetching + component composition. Verify responsive behaviour, dark mode, and edge cases across all lifecycle states and role combinations.

**References**: Architecture doc Sections 77.2.8 (data flow), 77.6 (responsive behaviour), 77.8 (migration path), 77.10 Slice 77E.

**Dependencies**: Epics 556 (shared extraction), 557 (header card + overflow), 558 (grouped tabs + tab panels), 559 (overview tab). All components must exist before integration.

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **560A** | 560.1-560.5 | Refactor `page.tsx`: remove old header/inline sections, wire `ClientHeaderCard` → banner → `CustomerGroupedTabs`. Build all panel ReactNodes. Delete old `CustomerTabs`. Full `pnpm lint && pnpm build && pnpm test` pass. | |
| **560B** | 560.6-560.9 | Responsive polish, dark mode audit, edge-case testing, integration test. (~4 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 560.1 | Refactor `page.tsx` header section | 560A | | Modify `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. Remove the current header section that renders: customer name h1, status badge, 7 action buttons (Summarise, Change Status, Generate Document, Export, Anonymize, Edit, Archive), inline email/phone, lifecycle info. Replace with `<ClientHeaderCard>` component, passing all required props (customerId, customerName, customerStatus, lifecycleStatus, email, phone, lifecycleStatusChangedAt, linkedProjectCount, kycSummary, xeroConnected, slug, isAdmin, isOwner, templates, aiProviderConfigured, conflictCheckEnabled, kycConfigured, kycVerified). All props derived from existing data fetches. ~80 lines removed, ~20 lines added. Pattern: follow architecture doc Section 77.2.8 data flow diagram. |
| 560.2 | Refactor `page.tsx` inline metadata sections | 560A | | Remove inline rendering of: (1) address block (`CustomerAddressBlock`), (2) contact card (`CustomerContactCard`), (3) business details, (4) field group selector + custom fields (`FieldGroupSelector`, `CustomFieldSection`), (5) tags (`TagInput`), (6) setup progress card, (7) unbilled time card, (8) template readiness card, (9) lifecycle action prompt, (10) AI panels (PendingSuggestionsWidget, FicaVerificationPanel). All of these are now rendered inside tab panels. Build `ReactNode` panel props: `detailsPanel={<ClientDetailsTab .../>}`, `fieldsPanel={<ClientFieldsTab .../>}`, `tagsPanel={<ClientTagsTab .../>}`, `overviewPanel={<ClientOverviewTab .../>}`. Existing panels (projectsPanel, documentsPanel, etc.) remain unchanged. ~200 lines removed, ~30 lines of panel construction added. |
| 560.3 | Wire `CustomerGroupedTabs` into `page.tsx` | 560A | | Replace `<CustomerTabs>` with `<CustomerGroupedTabs>`. Pass all panel ReactNode props. The page structure becomes: back link → `<ClientHeaderCard>` → (anonymized banner, conditional) → `<CustomerGroupedTabs detailsPanel={...} fieldsPanel={...} tagsPanel={...} overviewPanel={...} projectsPanel={...} documentsPanel={...} .../>`. Keep the anonymized banner between header card and tab bar (full-width, always visible regardless of active tab). ~30 lines changed. Pattern: follow architecture doc Section 77.8.1 Step 8. |
| 560.4 | Delete old `CustomerTabs` component | 560A | | Delete `frontend/components/customers/customer-tabs.tsx` (215 lines). This component is fully replaced by `CustomerGroupedTabs`. Verify no other files import from this path (grep for `customer-tabs` across frontend). If any test files reference it, update or remove them. ~0 lines created (deletion only). |
| 560.5 | Run full build verification | 560A | | Run `pnpm lint && pnpm build && pnpm test` in the frontend directory. Fix any lint errors (unused imports from removed sections), build errors (missing props, broken imports), or test failures. This is the critical integration verification step. Expect ~10-20 fixes across 3-5 files. |
| 560.6 | Dark mode audit for all new components | 560B | | Audit all new components from Epics 557-559 for dark mode support. Checklist: (1) `ClientHeaderCard` — `dark:text-slate-50` on name, `dark:text-slate-400` on contact/context, `dark:border-slate-800` on card border, (2) `ClientOverflowMenu` — uses Shadcn `DropdownMenu` (inherits dark mode), verify destructive item red text in dark, (3) `CustomerGroupedTabs` — uses shared `GroupedTabBar` (inherits), verify active indicator, (4) `ClientOverviewTab` — financial summary cards use `bg-card` (inherits), empty state text contrast, (5) Tab panel components — use existing components (inherit). Fix any missing `dark:` variants. ~10-15 lines across 3-4 files. |
| 560.7 | Edge-case styling fixes | 560B | | Test and fix styling for edge cases per architecture doc Section 77.10 Slice 77E: (1) Short name ("John Smith") — no excessive whitespace, (2) Long name ("Kgosi Holdings International (Pty) Ltd — Johannesburg Regional Office") — `line-clamp-2` works, tooltip shows full text, (3) No custom fields — Fields tab renders empty state, (4) 3+ field groups — Fields tab scrolls correctly, (5) All modules enabled (max tabs) — GroupedTabBar fits, (6) Non-admin user (min tabs) — Finance group hidden, (7) Anonymized customer — read-only state, header card shows minimal actions, (8) PROSPECT vs ACTIVE vs OFFBOARDED — smart primary action correct per lifecycle. ~10-15 lines across 2-3 files. |
| 560.8 | Responsive behaviour verification | 560B | | Verify responsive layout per architecture doc Section 77.6: (1) Desktop (>= lg, 1024px+) — full-width card, all inline, standard dropdowns, (2) Tablet (md, 768-1023px) — badges inline, actions right-aligned, (3) Mobile (< md, < 768px) — badges wrap, actions stack below content, dropdowns may use full-width popover. Fix header card `flex-wrap` and `justify-between` layout for each breakpoint. GroupedTabBar already handles narrow screens via Radix collision detection. ~5-10 lines if fixes needed. |
| 560.9 | Add integration test | 560B | | Create `frontend/__tests__/components/customers/customer-detail-integration.test.tsx` (~4 tests): (1) Full page render with ClientHeaderCard and CustomerGroupedTabs, (2) Overview tab is default (no `?tab=` param), (3) Clicking Details group shows ClientDetailsTab content, (4) Anonymized customer shows banner between card and tabs. Mock server component data, `useSearchParams`, `useRouter`. Always `afterEach(() => cleanup())`. Pattern: follow `__tests__/components/projects/responsive-layout.test.tsx`. |

### Key Files

**Slice 560A — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Major refactor (~983 lines slimmed to ~400 lines)

**Slice 560A — Delete:**
- `frontend/components/customers/customer-tabs.tsx` — Replaced by `CustomerGroupedTabs`

**Slice 560A — Read for context:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Full current implementation (understand all data fetching, prop construction, inline sections)
- `frontend/components/customers/client-header-card.tsx` — From 557A
- `frontend/components/customers/customer-grouped-tabs.tsx` — From 558A
- `frontend/components/customers/client-overview-tab.tsx` — From 559A
- `frontend/components/customers/client-details-tab.tsx` — From 558B
- `frontend/components/customers/client-fields-tab.tsx` — From 558B
- `frontend/components/customers/client-tags-tab.tsx` — From 558B
- `architecture/phase77-customer-detail-redesign.md` — Section 77.2.8 (data flow), Section 77.8 (migration path)

**Slice 560B — Modify:**
- `frontend/components/customers/client-header-card.tsx` — Dark mode fixes, responsive fixes
- `frontend/components/customers/client-overview-tab.tsx` — Dark mode fixes, empty state contrast
- `frontend/components/customers/client-details-tab.tsx` — Edge-case conditional rendering
- `frontend/components/customers/client-fields-tab.tsx` — Edge-case empty state

**Slice 560B — Create:**
- `frontend/__tests__/components/customers/customer-detail-integration.test.tsx`

### Architecture Decisions

- **`page.tsx` remains a server component**: All data fetching stays server-side. The refactoring only changes how fetched data is distributed to the new component tree. No API changes, no new endpoints.
- **All panel ReactNodes pre-rendered server-side**: Following the same pattern as the matter detail page. The server component builds each panel as JSX and passes it as a `ReactNode` prop to `CustomerGroupedTabs`. This preserves server-side rendering for all tab content.
- **Old `CustomerTabs` deleted, not deprecated**: Direct replacement. No feature flag, no gradual migration. Rollback is via git revert.
- **Anonymized banner placement**: Between header card and tab bar, full-width. Visible regardless of active tab. Same content and styling as current implementation.
- **~983 lines → ~400 lines**: The reduction comes from removing inline metadata rendering, action button rows, setup guidance, and AI panels from the main flow. All of this content moves into tab panel components. The remaining lines are data fetching + component composition.

---

## Epic 561: QA Testplan Updates

**Goal**: Migrate all QA lifecycle scripts to use grouped tab navigation and overflow menu selectors for the customer detail page. Update field promotion checkpoints. Add Phase 75 sharding note. Document the Customer Detail Migration in `MIGRATION-NOTES.md`.

**References**: Architecture doc Sections 77.9.1-77.9.6 (QA testplan migration). Phase 73 Epic 537 for format precedent.

**Dependencies**: Epics 556-560 (all layout changes must be complete before updating QA scripts).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **561A** | 561.1-561.6 | Update 10 QA testplan files, migrate tab/action/content selectors, update field promotion checkpoints, add sharding note, update MIGRATION-NOTES.md | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 561.1 | Update `48-lifecycle-script.md` | 561A | | Modify `qa/testplan/48-lifecycle-script.md`. HIGH impact. Migrate all customer detail tab navigation steps to grouped tab pattern per architecture doc Section 77.9.2: flat tab clicks → `tab-group-*` + `tab-item-*` two-step. Migrate lifecycle transition button selectors: header row buttons → `client-header-card` smart primary action (`data-testid="client-header-card"` + `getByRole('button', { name: /start onboarding/i })`). Migrate Generate Document → overflow menu two-step (`client-overflow-trigger` → `generate document`). Migrate content references: setup progress, custom fields, tags now in specific tabs (Overview, Fields, Tags). ~30-40 selector changes across the file. |
| 561.2 | Update `regression-test-suite.md` | 561A | | Modify `qa/testplan/regression-test-suite.md`. HIGH impact. Update: Customer CRUD section (line ~166), lifecycle badges (line ~178), Generate Document (line ~366), Export/Anonymize (lines ~478-481), page objects (lines ~711-721). Migrate all customer detail page selectors to new patterns: action buttons → overflow menu, flat tabs → grouped tabs, inline metadata → tab panels. Update page object definitions to reflect new component structure (`client-header-card`, `client-overflow-trigger`, `customer-grouped-tabs`). ~25-30 selector changes. |
| 561.3 | Update demo lifecycle scripts | 561A | | Modify: (1) `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` — HIGH impact: field promotion checkpoints (lines ~132, ~401, ~415), Onboarding tab (line ~204), client detail screenshots (line ~250), onboarding flow (line ~320). Add Details tab navigation step before field promotion assertions. (2) `qa/testplan/demos/legal-za-90day-keycloak.md` — HIGH impact: field promotion checkpoints (lines ~150, ~157-158), lifecycle badges (lines ~218, ~230). (3) `qa/testplan/demos/admin-audit-30day-keycloak.md` — MEDIUM impact: Customer Audit tab (lines ~195-207) now under Activity group. ~20-25 selector changes total across 3 files. |
| 561.4 | Update remaining QA testplan files | 561A | | Modify: (1) `qa/testplan/legal-onboarding-keycloak.md` — MEDIUM impact: client detail page references (lines ~239, ~252, ~325, ~393), lifecycle badges. Also add Phase 75 sharding note per architecture doc Section 77.9.6: at lines ~482, ~511 where `SELECT * FROM public.org_schema_mapping;` is referenced, add note: "verify `shard_id` is `primary` for the new tenant." (2) `qa/testplan/qa-legal-lifecycle-test-plan.md` — MEDIUM impact: client detail references (lines ~72, ~125, ~446, ~544). (3) `qa/testplan/phase49-document-content-verification.md` — MEDIUM impact: custom fields section (lines ~142, ~203, ~251-252), Requests tab (lines ~521, ~560), Generate Document (lines ~375, ~395). (4) `qa/testplan/phase74-ai-lifecycle-scenario.md` — LOW impact: "Verify with AI" button now in Overview tab (line ~106). (5) `qa/testplan/demo-readiness-keycloak-master.md` — LOW impact: customer detail page field references (line ~199). ~15-20 selector changes total across 5 files. |
| 561.5 | Update `MIGRATION-NOTES.md` | 561A | | Modify `qa/testplan/MIGRATION-NOTES.md`. Add a **Customer Detail Migration (Phase 77)** section following the format of the existing Matter Detail Migration section. Include: (1) Tab navigation selector migration table per architecture doc Section 77.9.2, (2) Action button selector migration table per Section 77.9.3, (3) Content relocation table per Section 77.9.4, (4) Field promotion checkpoint changes per Section 77.9.5. This serves as the canonical reference for QA authors so they know the current selector conventions. ~60 lines of documentation. |
| 561.6 | Verification pass — no stale selectors remain | 561A | | Grep all files in `qa/testplan/` for stale customer detail selectors: (1) `page.getByText('Edit')` in customer detail context (should be overflow menu), (2) `page.getByText('Generate Document')` (should be overflow menu), (3) `page.getByText('Export Data')` (should be overflow menu), (4) flat tab references like `page.getByRole('tab', { name: 'Projects' })` in customer detail context (should be `tab-group-work`), (5) references to inline metadata "above tabs" that now live in Details tab. Fix any remaining stale references. This is a sweep/verification task, not a modification task. ~5-10 fixes if any remain. |

### Key Files

**Slice 561A — Modify:**
- `qa/testplan/48-lifecycle-script.md` — Primary lifecycle script, most customer detail steps
- `qa/testplan/regression-test-suite.md` — Customer CRUD, lifecycle, Generate Document, page objects
- `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` — Field promotion, onboarding, screenshots
- `qa/testplan/demos/legal-za-90day-keycloak.md` — Field promotion, lifecycle badges
- `qa/testplan/demos/admin-audit-30day-keycloak.md` — Customer Audit tab under Activity
- `qa/testplan/legal-onboarding-keycloak.md` — Client detail references + Phase 75 sharding note
- `qa/testplan/qa-legal-lifecycle-test-plan.md` — Client detail, lifecycle, Generate Document
- `qa/testplan/phase49-document-content-verification.md` — Custom fields, Requests tab, Generate Document
- `qa/testplan/phase74-ai-lifecycle-scenario.md` — "Verify with AI" in Overview tab
- `qa/testplan/demo-readiness-keycloak-master.md` — Customer detail field references
- `qa/testplan/MIGRATION-NOTES.md` — Add Customer Detail Migration section

**Slice 561A — Read for context:**
- `architecture/phase77-customer-detail-redesign.md` — Sections 77.9.1-77.9.6 (full QA impact assessment, selector migration tables)
- `qa/testplan/MIGRATION-NOTES.md` — Existing Matter Detail Migration section (format reference)
- `frontend/components/customers/client-header-card.tsx` — Verify `data-testid` values match QA selectors
- `frontend/components/customers/client-overflow-menu.tsx` — Verify `data-testid` values match QA selectors
- `frontend/components/shared/grouped-tab-bar.tsx` — Verify `data-testid` values (`tab-group-*`, `tab-item-*`) match QA selectors

### Architecture Decisions

- **Two-step tab navigation in QA scripts**: Grouped tabs require two actions (click group, click sub-tab) instead of one (click flat tab). QA scripts must reflect this. The `data-testid` pattern (`tab-group-*` + `tab-item-*`) provides stable selectors.
- **Selector migration documented in `MIGRATION-NOTES.md`**: The canonical migration reference lives alongside QA scripts, not in architecture docs. This is where QA authors look when writing new scripts.
- **Field promotion checkpoint update**: Promoted fields are still first-class fields on the Details tab, but the assertion changes from "visible on page load" to "visible when Details tab is active." Steps must add a navigation step.
- **Phase 75 sharding note is minimal**: One-line addition to `legal-onboarding-keycloak.md`. Sharding is disabled by default; the note documents the new `shard_id` column in `org_schema_mapping` for completeness.
- **Verification pass as a distinct task**: Task 561.6 is a sweep to catch any stale selectors that individual updates may have missed. This prevents regression where old selectors silently fail in E2E tests.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/projects/grouped-tab-bar.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/constants/tab-groups.ts`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/customers/customer-tabs.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/projects/overflow-actions-menu.tsx`
