# Frontend Architecture Improvement Plan

**Date**: 2026-03-13
**Scope**: Architectural and technical improvements to the Next.js 16 frontend
**Constraint**: No functionality regressions; Clerk removal in favour of Keycloak + mock IDP

---

## Table of Contents

1. [Current State Summary](#1-current-state-summary)
2. [Findings](#2-findings)
3. [Implementation Plan](#3-implementation-plan)

---

## 1. Current State Summary

| Metric | Value |
|--------|-------|
| Framework | Next.js 16, React 19, TypeScript 5 |
| Component files | 375 (283 are `"use client"`) |
| `lib/types.ts` | 1,608 lines (monolith) |
| `lib/api.ts` | 791 lines (monolith) |
| Largest component | `invoice-detail-client.tsx` (974 lines) |
| Components > 500 lines | 20 |
| Auth provider | Clerk (production), mock IDP (E2E), Keycloak BFF (partial) |
| State management | None (React Context + URL params + server actions) |
| Data fetching | Server Components + manual `useEffect` in client components |
| Form library | None (plain `useState`) |
| Bundle splitting | None (no `next/dynamic`) |
| Clerk-touching files | 32 (including tests) |

**Overall health: 7/10** — Solid server-first architecture with good auth abstraction. Main debts are file bloat, missing form validation, no client-side caching, and Clerk coupling.

---

## 2. Findings

### F1. Auth Provider — Clerk Removal

**Severity: Strategic**

The `lib/auth/` abstraction layer (Phase 20) already decouples 44+ files from `@clerk/nextjs`. The abstraction is well-designed — build-time `NEXT_PUBLIC_AUTH_MODE` switch, tree-shakeable providers, platform-level types (`SessionIdentity`, `AuthContext`).

**What stays**:
- `lib/auth/types.ts` — domain types (unchanged)
- `lib/auth/server.ts` — provider dispatch (simplified)
- `lib/auth/middleware.ts` — `createAuthMiddleware()` factory (simplified)
- `lib/auth/client/auth-provider.tsx` — conditional wrapper (simplified)
- `lib/auth/providers/keycloak-bff.ts` — becomes the primary provider
- `lib/auth/providers/mock/server.ts` — stays for E2E

**What gets deleted**:
- `lib/auth/providers/clerk.ts` — entire file
- `app/(auth)/sign-in/[[...sign-in]]/page.tsx` — Clerk hosted page
- `app/(auth)/sign-up/[[...sign-up]]/page.tsx` — Clerk hosted page
- `app/(auth)/layout.tsx` — split-screen Clerk auth layout
- `app/api/webhooks/clerk/route.ts` + `route.test.ts` — Clerk webhook handler
- `lib/webhook-handlers.ts` + `.test.ts` — Clerk event handlers
- `proxy.ts` — re-implemented as simple middleware export

**Dependencies to remove from `package.json`**:
- `@clerk/nextjs`
- `svix` (webhook verification)

**Environment variables to remove**:
- `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`
- `CLERK_SECRET_KEY`
- `CLERK_WEBHOOK_SIGNING_SECRET`

**Files requiring edits** (Clerk references in non-auth code):
- `components/team/invite-member-form.tsx` — uses Clerk invitation API
- `components/team/pending-invitations.tsx` — lists Clerk invitations
- `components/team/member-list.tsx` — Clerk user metadata
- `app/(app)/org/[slug]/team/actions.ts` — Clerk org member operations
- `app/(app)/create-org/page.tsx` — Clerk `CreateOrganization` component
- `components/sidebar-user-footer.tsx` — Clerk `UserButton`
- `components/auth-header-controls.tsx` — Clerk sign-out
- `components/marketing/hero-section.tsx` — Clerk sign-in links
- `components/marketing/pricing-preview.tsx` — Clerk sign-up links
- `lib/internal-api.ts` — references Clerk webhook types

**Impact**: Team management (invite, pending invitations, member list) relies heavily on Clerk APIs. These need Keycloak equivalents — either direct Keycloak Admin API calls from the backend, or new backend endpoints that wrap Keycloak operations.

---

### F2. Monolithic Types File

**Severity: Moderate**

`lib/types.ts` at 1,608 lines is a single file containing every API type for every domain. This creates:
- Poor navigability (jump-to-definition lands in a giant file)
- Merge conflicts when multiple features touch it simultaneously
- No logical grouping enforced by the module system

**Recommendation**: Split into domain modules under `lib/types/`:
```
lib/types/
├── index.ts           # Re-exports everything (backwards-compatible)
├── project.ts         # Project, CreateProjectRequest, ProjectStatus
├── customer.ts        # Customer, CustomerStatus, CustomerLifecycleStatus
├── document.ts        # Document, DocumentScope, UploadInitRequest
├── invoice.ts         # InvoiceResponse, InvoiceLine, InvoiceStatus
├── member.ts          # OrgMember, ProjectMember, ProjectRole
├── task.ts            # Task, TaskStatus, TimeEntry
├── template.ts        # Template types, GeneratedDocument
├── field.ts           # FieldDefinition, FieldGroup, Tag, SavedView
├── billing.ts         # Plan, BillingRate, CostRate, Budget
├── notification.ts    # Notification, NotificationPreference
├── comment.ts         # Comment types
├── common.ts          # PaginatedResponse, ProblemDetail, shared enums
└── ...
```

The `index.ts` barrel re-export means **zero breaking changes** — existing `import { X } from "@/lib/types"` continues to work.

---

### F3. Monolithic API Client

**Severity: Moderate**

`lib/api.ts` at 791 lines mixes:
- Core fetch wrapper + error handling (~100 lines)
- Auth header logic (~50 lines)
- 50+ domain-specific API functions (templates, tags, fields, views, settings, documents, etc.)

**Recommendation**: Split into:
```
lib/api/
├── client.ts          # Core apiRequest(), ApiError, handleApiError, getAuthFetchOptions
├── index.ts           # Re-exports client + domain modules
├── projects.ts        # fetchProjects, createProject, etc.
├── customers.ts       # fetchCustomers, createCustomer, etc.
├── invoices.ts        # fetchInvoices, generateInvoice, etc.
├── templates.ts       # fetchTemplates, previewTemplate, etc.
├── fields.ts          # fetchFieldDefinitions, createFieldGroup, etc.
├── settings.ts        # fetchOrgSettings, updateBranding, etc.
├── capabilities.ts    # (already exists — good pattern)
├── billing-runs.ts    # (already exists — good pattern)
├── retainers.ts       # (already exists — good pattern)
└── ...
```

Note: `lib/api/capabilities.ts`, `billing-runs.ts`, and `retainers.ts` already follow this pattern — the rest of `api.ts` should be migrated to match.

---

### F4. Oversized Components

**Severity: Moderate**

20 components exceed 500 lines. The worst offenders:

| Component | Lines | Issue |
|-----------|-------|-------|
| `invoice-detail-client.tsx` | 974 | Multiple edit modes, line items, payment history in one component |
| `FieldDefinitionDialog.tsx` | 969 | Complex multi-step form with preview |
| `TemplateEditorClient.tsx` | 887 | Full editor + preview + save logic |
| `task-detail-sheet.tsx` | 809 | Detail view + comments + time entries + sub-items |
| `invoice-generation-dialog.tsx` | 784 | Multi-step wizard |
| `cherry-pick-step.tsx` | 704 | Complex table selection |
| `task-list-panel.tsx` | 676 | List + filters + drag-and-drop |

**Recommendation**: Extract into sub-components:
- Separate **data/state hooks** from **presentation**
- Extract **form sections** into their own components
- Extract **table/list renderers** from container logic
- Multi-step dialogs: extract each step as a component

For example, `invoice-detail-client.tsx` could become:
```
components/invoices/
├── invoice-detail-client.tsx    # Container (state + routing)
├── invoice-header.tsx           # Status badge, actions, metadata
├── invoice-line-editor.tsx      # Line item CRUD
├── invoice-payment-section.tsx  # Payment events + history
└── invoice-totals.tsx           # Summary calculations
```

---

### F5. No Form Validation Library

**Severity: Moderate**

All forms use raw `useState` with ad-hoc validation (`if (!name?.trim())`). This leads to:
- No schema-based validation
- Inconsistent error messages
- No field-level error display
- Validation logic mixed with UI code
- Duplicated patterns across 40+ form dialogs

**Recommendation**: Adopt `react-hook-form` + `zod`:
- `zod` schemas colocated with types (or derived from them)
- `react-hook-form` for state management, dirty tracking, submission
- `@hookform/resolvers/zod` to bridge them
- Shadcn `Form` component already supports this pattern

This doesn't need a big-bang migration. New forms use the pattern; existing forms are migrated opportunistically when touched.

---

### F6. No Client-Side Data Caching

**Severity: Low-Moderate**

Client components that fetch data (dialogs, sheets) use raw `useEffect` + `useState`. This means:
- No request deduplication (same data fetched multiple times)
- No stale-while-revalidate
- No background refresh
- Manual loading/error state management in every component
- Notification polling is a custom hook (`useNotificationPolling`) instead of a library-managed interval

**Recommendation**: The current Server Component-first approach is correct and should stay. For the minority of **client-side fetches** (dialogs, sheets, polling), consider `SWR` (lighter than React Query, fits Next.js better):
- Replace manual `useEffect` fetch patterns with `useSWR`
- Notification polling: `useSWR` with `refreshInterval: 30000`
- Dialog data: `useSWR` with `revalidateOnFocus: false`

This is **low priority** because most data flows through Server Components where Next.js handles caching.

---

### F7. No Code Splitting / Dynamic Imports

**Severity: Low-Moderate**

Zero usage of `next/dynamic` across the entire codebase. Heavy client components are bundled eagerly:
- Template editor (Tiptap) — ~800+ lines
- Recharts (charting) — multiple report pages
- Invoice generation wizard — 784 lines
- All 283 `"use client"` components bundled into the page they appear on

**Recommendation**: Lazy-load heavy components that aren't visible on initial render:

Priority targets:
1. **Tiptap editor** (largest JS dependency, only used on template/document pages)
2. **Recharts** (only on reports/profitability pages)
3. **Dialog/sheet components** (not visible until user action)
4. **Command palette** (only visible on Cmd+K)

---

### F8. Server Action Files Growing Large

**Severity: Low**

Several `actions.ts` files are becoming large:

| File | Lines |
|------|-------|
| `settings/templates/actions.ts` | 511 |
| `invoices/actions.ts` | 370 |
| `resources/actions.ts` | 360 |
| `invoices/billing-runs/new/actions.ts` | 334 |
| `team/actions.ts` | 308 |

These files mix multiple unrelated server actions. As they grow, they become hard to navigate and test.

**Recommendation**: Split when > 300 lines. Group by operation type (CRUD, generation, payment, etc.).

---

### F9. `"use client"` Ratio

**Severity: Informational**

283 of 375 component files (75%) are `"use client"`. This is expected for a SaaS app with heavy interactivity (forms, dialogs, tables, drag-and-drop). The actual **data fetching** correctly stays in Server Components (pages/layouts), so the client components are thin wrappers for interactivity.

No action needed — just worth noting that the boundary is correctly placed.

---

### F10. Accessibility Gaps

**Severity: Low**

- Good ARIA fundamentals (Radix UI provides this for free)
- Missing `aria-live` on dynamic content (notification counts, loading states)
- No skip-to-content link
- No explicit focus management after dialog close in some components

**Recommendation**: Address incrementally:
1. Add skip-to-content link in root layout
2. Add `aria-live="polite"` on notification badge count
3. Audit dialog close and ensure focus return patterns work

---

### F11. Security — Unsafe HTML Rendering

**Severity: Low**

One instance of unsafe HTML rendering in `components/templates/generation-clause-step.tsx`. The HTML comes from server-rendered template clauses, not user input. Risk is low but should be documented.

**Recommendation**: Add a DOMPurify sanitization step if template content ever becomes user-editable.

---

### F12. Org Provisioning After Clerk Removal

**Severity: None (already solved)**

Phase 36 already rewired org creation to use the gateway `POST /bff/orgs` endpoint. The Clerk `<CreateOrganization>` component is dead code in Keycloak mode. JIT tenant provisioning handles schema creation on first request.

**Action needed**: Delete the Clerk code path only.

---

### F13. Team Management After Clerk Removal

**Severity: Low (already solved)**

Team features currently depend on Clerk APIs, but Phase 36 already built all Keycloak equivalents in the gateway's `AdminProxyController`:

| Clerk Feature | Existing Gateway Replacement |
|---|---|
| Invite member | `POST /bff/admin/invite` (email + role) |
| List pending invitations | `GET /bff/admin/invitations` |
| Revoke invitation | `DELETE /bff/admin/invitations/{id}` |
| List members | `GET /bff/admin/members` |
| Change member role | `PATCH /bff/admin/members/{id}/role` |

All endpoints are gated by `@PreAuthorize("@bffSecurity.isAdmin(#user)")` and use `KeycloakAdminClient` under the hood.

**Action needed**: Delete the Clerk code branches only. The Keycloak paths are already active and working.

---

## 3. Implementation Plan

### Phase A: Clerk Removal & Keycloak Consolidation

**Priority: HIGH — Strategic**
**Estimated scope: 3 epics (pure deletion — all Keycloak flows already work end-to-end from Phase 36)**

> **Note**: Phase 36 already rewired the entire frontend for Keycloak — org creation
> (`POST /bff/orgs`), team management (`/bff/admin/*`), auth pages (gateway OAuth2 redirect),
> and user menu (`user-menu-bff.tsx`) all work today. The Clerk code paths are dead code
> when `NEXT_PUBLIC_AUTH_MODE=keycloak`. This phase removes them entirely and collapses
> the auth mode switch to keycloak + mock only.

#### Epic A1: Remove Clerk Auth Provider & Auth Pages
- Delete `lib/auth/providers/clerk.ts`
- Delete `app/(auth)/` route group (Clerk sign-in/sign-up pages)
- Simplify `lib/auth/server.ts` — remove Clerk branch, keep keycloak + mock
- Simplify `lib/auth/middleware.ts` — remove Clerk middleware
- Simplify `lib/auth/client/auth-provider.tsx` — remove ClerkProvider branch
- Replace `proxy.ts` with simplified middleware (keycloak + mock only)
- Remove `@clerk/nextjs`, `svix` from `package.json`
- Remove Clerk env vars from `.env*` files (`NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SIGNING_SECRET`)
- Remove `cssLayerName: "clerk"` references

#### Epic A2: Delete Clerk Webhook Infrastructure & Dead Code
- Delete `app/api/webhooks/clerk/route.ts` + `route.test.ts`
- Delete `lib/webhook-handlers.ts` + `.test.ts`
- Delete `lib/internal-api.ts` (Clerk-specific types)
- Remove Clerk references from components that already have Keycloak code paths:
  - `components/team/invite-member-form.tsx` — delete Clerk branch
  - `components/team/pending-invitations.tsx` — delete Clerk branch
  - `components/team/member-list.tsx` — delete Clerk branch
  - `app/(app)/org/[slug]/team/actions.ts` — delete Clerk branch
  - `components/sidebar-user-footer.tsx` — delete Clerk `UserButton` branch
  - `components/auth-header-controls.tsx` — delete Clerk branch
  - `components/marketing/hero-section.tsx` — update sign-in links
  - `components/marketing/pricing-preview.tsx` — update sign-up links
- Update tests that mock Clerk APIs

#### Epic A3: Documentation & Config Cleanup
- Update `CLAUDE.md` and `frontend/CLAUDE.md` — remove all Clerk references, anti-patterns, env vars
- Remove `NEXT_PUBLIC_AUTH_MODE` branching from docs (keycloak is now the only production mode)
- Update `.env.example` / `.env.local` files
- Verify build + all tests pass with Clerk dependency fully removed

---

### Phase B: Code Organisation & Module Splitting

**Priority: MEDIUM — Maintainability**
**Estimated scope: 3 epics**

#### Epic B1: Split `lib/types.ts` into Domain Modules
- Create `lib/types/` directory with domain-specific files
- Move types, keeping `lib/types/index.ts` barrel export for backwards compatibility
- Update imports in files that import specific types (optional, can stay via barrel)
- Verify build + all tests pass

#### Epic B2: Split `lib/api.ts` into Domain Modules
- Extract core client to `lib/api/client.ts` (apiRequest, ApiError, handleApiError, getAuthFetchOptions)
- Move domain functions to `lib/api/{domain}.ts` files
- Keep `lib/api/index.ts` barrel export
- Match the pattern already used by `capabilities.ts`, `billing-runs.ts`, `retainers.ts`
- Verify build + all tests pass

#### Epic B3: Split Oversized Components
- Target the top 10 files > 500 lines
- Extract sub-components, custom hooks, and form sections
- No functionality changes — pure refactoring
- Verify build + all tests pass

---

### Phase C: Developer Experience & Quality

**Priority: MEDIUM — Velocity**
**Estimated scope: 3 epics**

#### Epic C1: Form Validation with Zod + React Hook Form
- Add `react-hook-form`, `@hookform/resolvers`, `zod` dependencies
- Create shared zod schemas in `lib/schemas/` (derived from types)
- Migrate 3-5 high-traffic forms as the reference pattern:
  - `create-customer-dialog.tsx`
  - `create-project-dialog.tsx`
  - `invite-member-form.tsx` (after Keycloak rewrite)
- Document the pattern in `frontend/CLAUDE.md`
- Remaining forms migrated opportunistically when touched

#### Epic C2: Client-Side Data Caching (SWR)
- Add `swr` dependency
- Create `lib/swr/fetcher.ts` wrapper around `api.get()` (handles auth)
- Migrate notification polling to `useSWR` with `refreshInterval`
- Migrate dialog/sheet data fetches (5-10 components)
- Document the pattern in `frontend/CLAUDE.md`

#### Epic C3: Dynamic Imports for Heavy Components
- Add `next/dynamic` imports for:
  - Tiptap editor components
  - Recharts report components
  - Large dialog/wizard components (invoice generation, template editor)
  - Command palette
- Add `<Skeleton>` loading fallbacks
- Verify no hydration mismatches
- Measure bundle size improvement

---

### Phase D: Polish & Hardening

**Priority: LOW — Quality of life**
**Estimated scope: 2 epics**

#### Epic D1: Accessibility Improvements
- Add skip-to-content link in root layout
- Add `aria-live` regions for dynamic content (notification count, loading states)
- Audit dialog focus management (return focus on close)
- Add keyboard shortcut documentation in command palette

#### Epic D2: Split Large Server Action Files
- Target action files > 300 lines
- Split by operation type (CRUD, generation, payment, etc.)
- No functionality changes
- Verify build + all tests pass

---

### Dependency Graph

```
Phase A (Clerk Removal — pure deletion, all Keycloak flows already work)
 A1: Remove Clerk Provider & Auth Pages
   |
   v
 A2: Delete Webhook Infra & Dead Code
   |
   v
 A3: Documentation & Config Cleanup

Phase B (Code Organisation)         — independent of Phase A
 B1: Split types.ts  +  B2: Split api.ts   (parallel)
        |                    |
        +--- both done ------+
                  |
                  v
            B3: Split large components

Phase C (Developer Experience)      — B1/B2 recommended first
 C1: Form validation  +  C2: SWR  +  C3: Dynamic imports   (all parallel)

Phase D (Polish)                    — independent, anytime
 D1: Accessibility  +  D2: Split action files   (parallel)
```

**Recommended execution order**:
1. **A1** then **A2** then **A3** (sequential — each builds on prior deletion)
2. **B1 + B2** (parallel) then **B3**
3. **C1 + C2 + C3** (parallel)
4. **D1 + D2** (parallel, anytime)

Phases B, C, and D can run in parallel with Phase A since they don't overlap.

---

### What This Plan Does NOT Change

- **Server Component-first architecture** — correct, stays
- **Tailwind v4 / Signal Deck design system** — stays
- **Shadcn UI customizations** — stays
- **App Router structure** — stays (only auth route group deleted)
- **Server Actions for mutations** — stays
- **Vitest + RTL testing** — stays
- **Playwright E2E with mock IDP** — stays
- **`lib/auth/` abstraction pattern** — stays (simplified, not removed)
