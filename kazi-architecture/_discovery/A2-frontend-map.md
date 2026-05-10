# A2 — Kazi Frontend Structural Map

**Scope:** `frontend/` only (not `portal/`). Next.js 16 App Router, React 19, TypeScript 5, Tailwind v4, Shadcn UI.
**Date:** 2026-05-10. **Mapped by:** architecture discovery agent.

---

## 1. Top-Level Directory Structure

```
frontend/
├── app/                  Route tree (App Router). All pages, layouts, server actions, API routes.
├── components/           React components: domain feature dirs + ui/ (Shadcn primitives).
├── lib/                  Utilities, API clients, auth abstraction, contexts, schemas, types.
├── hooks/                Custom React hooks (client-side only).
├── __tests__/            Unit test files (Vitest + Testing Library).
├── e2e/                  Playwright E2E test configs and specs.
├── public/               Static assets (not mapped).
├── proxy.ts              Next.js middleware entry — delegates to lib/auth/middleware.ts.
├── components.json       Shadcn UI config (style: new-york, RSC: true, icon: lucide).
├── next.config.ts        Next.js config: standalone output, one legacy redirect rule.
├── vitest.config.ts      Vitest config with @/* alias.
├── tsconfig.json         Strict TS, bundler module resolution, @/* alias to root.
├── eslint.config.mjs     ESLint flat config.
└── postcss.config.mjs    Tailwind v4 PostCSS config.
```

Sub-dirs of note:

| Dir | Role |
|-----|------|
| `app/` | Route tree, pages, layouts, server actions per route, API Route Handlers |
| `components/ui/` | Shadcn primitives (customised) — Button, Badge, Card, Dialog, Input, etc. |
| `components/<feature>/` | Domain feature components (customers/, invoices/, trust/, legal/, etc.) |
| `lib/api/` | Server-only domain API wrappers; barrel-exports via `lib/api/index.ts` |
| `lib/api/client.ts` | The single fetch client: `apiRequest`, `api.get/post/put/patch/delete` |
| `lib/auth/` | Auth abstraction (server.ts, client/, providers/) |
| `lib/types/` | All domain TypeScript interfaces; barrel via `lib/types/index.ts` |
| `lib/actions/` | Server Actions grouped by domain (dashboard.ts, notifications.ts, etc.) |
| `lib/schemas/` | Zod validation schemas (one file per domain) |
| `lib/swr/` | SWR utilities: `defaultSWROptions`, `conditionalKey()` |
| `lib/capabilities.tsx` | `CAPABILITIES` constants + `CapabilityProvider` + `RequiresCapability` |
| `lib/terminology.tsx` | `TerminologyProvider` + `useTerminology()` hook |
| `lib/terminology-map.ts` | Vertical terminology overrides keyed by profile ID |
| `lib/org-profile.tsx` | `OrgProfileProvider` + `useOrgProfile()` — carries verticalProfile, enabledModules |
| `lib/nav-items.ts` | Authoritative sidebar nav tree: `NAV_GROUPS`, `UTILITY_ITEMS`, `SETTINGS_ITEMS` |
| `lib/internal-api.ts` | Server-only billing types + `BillingResponse`/`SubscribeResponse` |
| `lib/format.ts` | `formatCurrency`, `formatDate`, `formatLocalDate` utilities |
| `hooks/` | `use-assistant-chat.ts`, `use-swr-*` client hooks |

**Design tokens:** Defined as OKLCH CSS custom properties in `app/globals.css`. No `tailwind.config.ts` — Tailwind v4 CSS-first. Token names follow `--background`, `--card`, `--primary`, `--accent` (teal-600), `--sidebar` (slate-950). Fonts loaded via `next/font/google` in `app/layout.tsx`: Sora (display), IBM Plex Sans (body), JetBrains Mono (mono/stats).

---

## 2. Route Map

### Route Groups

| Group | Purpose |
|-------|---------|
| `(app)/` | Authenticated shell. Contains the org-scoped layout and all feature pages. |
| `(mock-auth)/` | E2E-only mock login page. Protected: redirects to /sign-in in keycloak mode. |
| Root (no group) | Public landing page at `app/page.tsx`. |

### Full Route Tree

```
app/
├── page.tsx                                     Public marketing landing page
├── layout.tsx                                   Root layout: AuthProvider, fonts, Toaster
├── globals.css                                  Tailwind v4 tokens, OKLCH slate palette
│
├── (mock-auth)/
│   └── mock-login/
│       ├── layout.tsx                           Centered card shell for mock login
│       └── page.tsx                             E2E test login form (mock auth only)
│
├── api/                                         Next.js Route Handlers (no pages)
│   └── (assistant routes — file not found at api/assistant/route.ts;
│        assistant calls go directly to /api/assistant/chat on backend)
│
└── (app)/
    ├── layout.tsx                               Passthrough (no-op wrapper)
    ├── dashboard/
    │   └── page.tsx                             Auth redirect: sends user to /org/:slug/dashboard
    ├── create-org/
    │   └── page.tsx                             Org creation / platform-admin redirect
    └── org/[slug]/
        ├── layout.tsx                           ORG SHELL: fetches caps, settings, billing, user.
        │                                        Wraps: CapabilityProvider, OrgProfileProvider,
        │                                        TerminologyProvider, AssistantProvider,
        │                                        CommandPaletteProvider, RecentItemsProvider.
        │                                        Renders DesktopSidebar, MobileSidebar, header,
        │                                        AssistantPanel, NotificationBell.
        │
        ├── dashboard/
        │   └── page.tsx                         Org dashboard: KPIs, project health, team workload,
        │                                        recent activity, sensitive events, capacity strip,
        │                                        legal court-date widget (module-gated).
        │
        ├── my-work/
        │   └── page.tsx                         Personal work view: assigned tasks + time entries
        │
        ├── calendar/
        │   └── page.tsx                         Month-view calendar of tasks/deadlines
        │
        ├── court-calendar/                      [module: court_calendar]
        │   └── page.tsx                         Legal court dates + prescription trackers
        │
        ├── projects/
        │   ├── page.tsx                         Project list with filters, views, tags
        │   ├── actions.ts                        Server actions: createProject, archiveProject, etc.
        │   └── [id]/
        │       ├── page.tsx                     Project detail: tasks, docs, time, expenses,
        │       │                                rates, budget, members, comments, audit
        │       ├── actions.ts                   Server actions for project mutations
        │       └── member-actions.ts            Project member add/remove actions
        │
        ├── customers/
        │   ├── page.tsx                         Customer list with lifecycle filter, views, tags
        │   ├── view-actions.ts                  Saved-view server actions
        │   └── [id]/
        │       ├── page.tsx                     Customer detail: profile, checklists, docs,
        │       │                                unbilled time, KYC/FICA, data protection
        │       └── actions.ts                   Customer mutation server actions
        │
        ├── invoices/
        │   ├── page.tsx                         Invoice list with status summary cards
        │   ├── actions.ts                        Invoice mutation server actions
        │   ├── billing-runs/                    [module: bulk_billing]
        │   │   ├── page.tsx                     Batch billing runs list
        │   │   ├── [id]/
        │   │   │   └── page.tsx                 Billing run detail
        │   │   └── actions.ts
        │   └── [id]/
        │       └── page.tsx                     Invoice detail: lines, tax, payment, PDF preview
        │
        ├── proposals/
        │   └── page.tsx                         Proposal list + summary cards (INVOICING cap)
        │
        ├── retainers/
        │   └── page.tsx                         Retainer/mandate list (INVOICING cap)
        │
        ├── compliance/
        │   └── page.tsx                         Compliance dashboard: lifecycle dist, onboarding
        │                                        pipeline, data requests, dormancy check
        │
        ├── deadlines/                           [module: regulatory_deadlines]
        │   └── page.tsx                         Regulatory deadline calendar
        │
        ├── conflict-check/                      [module: conflict_check]
        │   └── page.tsx                         Conflict-of-interest check search + history
        │
        ├── legal/
        │   ├── adverse-parties/                 [module: conflict_check]
        │   │   └── page.tsx                     Adverse party registry
        │   └── tariffs/                         [module: lssa_tariff]
        │       └── page.tsx                     LSSA tariff schedule browser
        │
        ├── profitability/
        │   └── page.tsx                         Org-wide profitability + utilization (FIN_VIS cap)
        │
        ├── reports/
        │   └── page.tsx                         Report catalogue + run report (FIN_VIS cap)
        │
        ├── trust-accounting/                    [module: trust_accounting, VIEW_TRUST cap]
        │   ├── page.tsx                         Trust account overview + summary stats
        │   ├── transactions/
        │   │   └── page.tsx                     Trust transaction ledger with filters
        │   ├── client-ledgers/
        │   │   └── page.tsx                     Per-client trust ledger view
        │   ├── reconciliation/
        │   │   └── page.tsx                     Trust reconciliation workflow
        │   ├── interest/
        │   │   └── page.tsx                     LPFF interest runs + rate management
        │   ├── investments/
        │   │   └── page.tsx                     Trust investment tracking (Section 86)
        │   └── reports/
        │       └── page.tsx                     Trust-specific report catalogue
        │
        ├── schedules/
        │   └── page.tsx                         Recurring project schedules (PROJ_MGMT cap)
        │
        ├── notifications/
        │   └── page.tsx                         Notification inbox (paginated)
        │
        ├── resources/                           [module: resource_planning, RES_PLAN cap]
        │   ├── page.tsx                         Team capacity allocation grid
        │   └── utilization/
        │       └── page.tsx                     Team utilization charts + table
        │
        ├── team/
        │   └── page.tsx                         Team members list + invite + role management
        │
        └── settings/
            ├── page.tsx                         Redirect to /settings/general
            ├── general/
            │   └── page.tsx                     Org name, logo, branding, vertical profile,
            │                                    org documents, portal settings
            ├── billing/
            │   ├── page.tsx                     Subscription status, trial/grace countdown,
            │                                    payment history, subscribe/cancel
            │   └── actions.ts                   getSubscription, subscribe, cancel, getPayments
            ├── notifications/
            │   └── page.tsx                     Notification preference configuration
            ├── rates/
            │   └── page.tsx                     Billing rates, cost rates, default currency
            ├── tax/
            │   └── page.tsx                     Tax registration, labels, rates, inclusive flag
            ├── time-tracking/
            │   └── page.tsx                     Time reminder settings, default expense markup
            ├── custom-fields/
            │   └── page.tsx                     Custom field definitions + field groups
            ├── tags/
            │   └── page.tsx                     Org-wide tag management
            ├── templates/
            │   └── page.tsx                     Document template library
            ├── clauses/
            │   └── page.tsx                     Reusable clause library
            ├── checklists/
            │   └── page.tsx                     Checklist template management
            ├── acceptance/
            │   └── page.tsx                     Document acceptance expiry settings
            ├── compliance/
            │   └── page.tsx                     Retention policies, dormancy, data requests
            ├── project-templates/
            │   └── page.tsx                     Project blueprint templates
            ├── project-naming/
            │   └── page.tsx                     Auto-naming pattern config
            ├── request-templates/
            │   └── page.tsx                     Information request templates
            ├── request-settings/
            │   └── page.tsx                     Default request reminder interval
            ├── batch-billing/                   [module: bulk_billing, admin-only]
            │   └── page.tsx                     Async thresholds, email rate limits
            ├── capacity/
            │   └── page.tsx                     Default weekly capacity hours
            ├── email/                           [admin-only]
            │   └── page.tsx                     Email delivery logs and rate-limit stats
            ├── automations/                     [module: automation_builder, admin-only]
            │   ├── page.tsx                     Automation rules list + create
            │   └── ai-queue/
            │       └── page.tsx                 AI specialist invocation review queue
            ├── roles/                           [admin-only]
            │   └── page.tsx                     Custom roles + capability matrix
            ├── audit-log/                       [admin-only]
            │   └── page.tsx                     Paginated audit event log with filters
            ├── integrations/
            │   └── page.tsx                     Third-party integration cards (accounting,
            │                                    email, payment, KYC)
            ├── trust-accounting/               [module: trust_accounting, admin-only]
            │   └── page.tsx                     Trust account setup, LPFF rates, Section 86
            └── features/                        [admin-only]
                └── page.tsx                     Feature module enable/disable toggles
```

Approximate page count: ~68 distinct `page.tsx` files.

---

## 3. Backend API Surface Used

### Call Architecture

There is a single centralised fetch client. There is no per-feature `api.ts` with its own fetch setup. All HTTP calls route through:

- `lib/api/client.ts` — `apiRequest()` / `api.{get,post,put,patch,delete}()` — server-only, attaches auth → `lib/api/client.ts:72`
- Domain wrappers in `lib/api/<domain>.ts` call `api.get/post/etc` — server-only
- Server Actions (`"use server"`) call domain wrappers or `api.*` directly
- Client components use SWR wrapping server actions, never call the API client directly
- The AI assistant chat hook (`hooks/use-assistant-chat.ts:128`) is the sole exception: it calls `/api/assistant/chat` via `fetch` directly from the browser (SSE streaming, needs client-side abort control)

**In Keycloak (BFF) mode:** `API_BASE` = `GATEWAY_URL`. The client forwards `SESSION` cookie and adds `X-XSRF-TOKEN` for mutations. Token is never in JS.
**In mock mode:** `API_BASE` = `BACKEND_URL`. Bearer token from cookie is attached.

### Endpoint Inventory (sampled)

| Endpoint | Method | Caller |
|----------|--------|--------|
| `/api/me/capabilities` | GET | `lib/api/capabilities.ts:17` |
| `/api/settings` | GET/PUT | `lib/api/settings.ts:9,31` |
| `/api/settings/logo` | POST/DELETE | `lib/api/settings.ts:46,69` |
| `/api/dashboard/kpis` | GET | `lib/actions/dashboard.ts:20` |
| `/api/dashboard/project-health` | GET | `lib/actions/dashboard.ts:27` |
| `/api/dashboard/team-workload` | GET | `lib/actions/dashboard.ts:36` |
| `/api/dashboard/activity` | GET | `lib/actions/dashboard.ts:46` |
| `/api/customers/completeness-summary/aggregated` | GET | `lib/actions/dashboard.ts:57` |
| `/api/customers` | GET/POST | pages via `api.*` |
| `/api/customers/{id}` | GET/PUT/PATCH | customer detail page actions |
| `/api/projects` | GET/POST | projects page |
| `/api/projects/{id}` | GET/PUT/DELETE | project detail actions |
| `/api/invoices` | GET/POST | invoices page |
| `/api/invoices/{id}` | GET/PUT | invoice detail |
| `/api/billing/subscription` | GET | `settings/billing/actions.ts:17` |
| `/api/billing/subscribe` | POST | `settings/billing/actions.ts:21` |
| `/api/billing/cancel` | POST | `settings/billing/actions.ts:24` |
| `/api/billing/payments` | GET | `settings/billing/actions.ts:28` |
| `/api/integrations` | GET | `lib/api/integrations.ts:17` |
| `/api/integrations/providers` | GET | `lib/api/integrations.ts:21` |
| `/api/integrations/{domain}` | PUT | `lib/api/integrations.ts:25` |
| `/api/schedules` | GET/POST/PUT/DELETE | `lib/api/schedules.ts` |
| `/api/capacity/team` | GET | `lib/api/capacity.ts` |
| `/api/automations/rules` | GET | `lib/api/automations.ts` |
| `/api/legal/court-dates` | GET/POST | court-calendar actions |
| `/api/legal/conflict-checks` | GET/POST | conflict-check actions |
| `/api/legal/adverse-parties` | GET/POST | adverse-parties actions |
| `/api/legal/tariffs` | GET | tariffs actions |
| `/api/trust/accounts` | GET | trust-accounting actions |
| `/api/trust/transactions` | GET | trust-transactions page |
| `/api/trust/client-ledgers` | GET | client-ledgers page |
| `/api/trust/reconciliation` | GET/POST | reconciliation page |
| `/api/trust/interest` | GET/POST | interest page |
| `/api/trust/investments` | GET/POST | investments page |
| `/api/reports` | GET | `lib/api/reports.ts` |
| `/api/audit-events` | GET | `lib/api/audit-events.ts` |
| `/api/notifications` | GET | notification actions |
| `/api/assistant/chat` | POST (SSE stream) | `hooks/use-assistant-chat.ts:128` |
| `/bff/me` | GET | `lib/auth/providers/keycloak-bff.ts:35` |

Pattern summary: endpoints follow Spring Boot RESTful conventions (`/api/<resource>` + `/{id}` + sub-resources). All are JSON except the assistant endpoint which is SSE. The billing internal API (`/api/billing/*`) hits the backend directly via `lib/internal-api.ts` using `INTERNAL_API_KEY`; the public-facing `getSubscription()` in `settings/billing/actions.ts` uses the standard `api.get` client.

---

## 4. Domain Types

All frontend types are hand-written (no OpenAPI generation). Single barrel: `lib/types/index.ts` re-exports all domain files.

### Type Files and Key Interfaces

| File | Key types |
|------|-----------|
| `lib/types/common.ts` | `TagResponse`, `ProblemDetail`, `CompletenessScore`, `AggregatedCompletenessResponse` |
| `lib/types/customer.ts:18` | `Customer` — id, name, email, lifecycleStatus, customerType, tags, customFields, promoted address/tax fields |
| `lib/types/customer.ts:6` | `LifecycleStatus` — `PROSPECT\|ONBOARDING\|ACTIVE\|DORMANT\|OFFBOARDING\|OFFBOARDED\|ANONYMIZED` |
| `lib/types/customer.ts:140` | `ChecklistTemplateResponse`, `ChecklistInstanceResponse`, `ChecklistItemStatus` |
| `lib/types/customer.ts:203` | `DataRequestResponse`, `DataRequestType`, `AnonymizationResult`, `RetentionPolicy` |
| `lib/types/project.ts:11` | `Project` — id, name, status, customerId, dueDate, retentionClockStartedAt, retentionEndsOn, tags, customFields |
| `lib/types/project.ts:6` | `ProjectStatus` — `ACTIVE\|COMPLETED\|ARCHIVED\|CLOSED` |
| `lib/types/project.ts:67` | `ProjectTimeSummary`, `MemberTimeSummary`, `TaskTimeSummary` |
| `lib/types/project.ts:95` | `MyWorkTaskItem`, `MyWorkTimeSummary` |
| `lib/types/invoice.ts:40` | `InvoiceResponse` — id, invoiceNumber, status, subtotal, taxAmount, total, lines, paymentSessionId |
| `lib/types/invoice.ts:9` | `InvoiceStatus` — `DRAFT\|APPROVED\|SENT\|PAID\|VOID` |
| `lib/types/invoice.ts:11` | `InvoiceLineType` — `TIME\|EXPENSE\|RETAINER\|MANUAL\|TARIFF\|FIXED_FEE` |
| `lib/types/invoice.ts:157` | `UnbilledTimeResponse`, `UnbilledProjectGroup`, `UnbilledDisbursementEntry` |
| `lib/types/task.ts:9` | `Task` — id, projectId, title, status, priority, assigneeId, estimatedHours, customFields |
| `lib/types/member.ts:3` | `OrgMember`, `ProjectMember`, `ProjectRole` |
| `lib/types/legal.ts:15` | `CourtDate` — projectId, dateType, scheduledDate, courtName, status |
| `lib/types/legal.ts:46` | `PrescriptionTracker` — prescriptionType, causeOfActionDate, prescriptionDate |
| `lib/types/legal.ts:86` | `ConflictCheck`, `ConflictMatch`, `ConflictCheckResult` |
| `lib/types/legal.ts:114` | `AdverseParty`, `AdversePartyLink` |
| `lib/types/legal.ts:148` | `TariffSchedule`, `TariffItem` |
| `lib/types/trust.ts:7` | `TrustAccount` — accountType (`GENERAL\|INVESTMENT\|SECTION_86`), requireDualApproval |
| `lib/types/trust.ts:39` | `TrustTransactionType` (10 variants including DISBURSEMENT_PAYMENT) |
| `lib/types/settings.ts:5` | `OrgSettings` — verticalProfile, enabledModules, terminologyNamespace, aiEnabled, taxRegistrationNumber |
| `lib/types/billing.ts` | `BillingRate`, `CostRate`, `TaxRateResponse`, `BudgetStatusResponse`, `ProjectProfitabilityResponse` |
| `lib/types/document.ts` | `Document`, `GeneratedDocument`, `TemplateListResponse` |
| `lib/types/proposal.ts` | `ProposalResponse` |
| `lib/types/expense.ts` | `ExpenseResponse`, `ExpenseCategory` |
| `lib/types/kyc.ts` | KYC check types |
| `lib/types/fica.ts` | FICA/AML types |
| `lib/types/deadline.ts` | `DeadlineResponse` |
| `lib/types/data-protection.ts` | Data protection jurisdiction types |
| `lib/types/field.ts` | `FieldDefinitionResponse`, `FieldGroupResponse`, `EntityType` |
| `lib/internal-api.ts:22` | `BillingResponse` — status, trialEndsAt, limits.maxMembers, billingMethod |

---

## 5. Cross-Cutting Frontend Concerns

### Auth Flow

- Entry point: `proxy.ts` → `lib/auth/middleware.ts` (Next.js middleware, runs on every request)
- Server-side identity: `lib/auth/server.ts:19` — `getAuthContext()` delegates to active provider
- Keycloak BFF provider (`lib/auth/providers/keycloak-bff.ts:35`): calls `GATEWAY_URL/bff/me` forwarding `SESSION` cookie; result cached per RSC render via `React.cache`
- Mock provider (`lib/auth/providers/mock/server.ts`): reads JWT from `mock-auth-token` cookie; used only when `NEXT_PUBLIC_AUTH_MODE=mock`
- Client-side: `lib/auth/client/auth-provider.tsx` wraps the tree; `useAuth()` hook gives client components access to user identity (userId, orgSlug, role)
- `getAuthContext()` is server-only (enforced by `import "server-only"`); client components use `useAuth()`

### Feature/Module Gating

Two mechanisms coexist:

1. **`ModuleGate` component** (`components/module-gate.tsx:11`) — client-side, reads `enabledModules` from `OrgProfileProvider`. Used inline in pages and dashboard widgets. Example: `<ModuleGate module="court_calendar">` in dashboard page.
2. **`isModuleEnabledServer()`** (`lib/api/settings.ts:21`) — server-side, fetches settings and short-circuits data fetching before calling backend list endpoints. Used in route page components that should render a disabled fallback.
3. **`RequiresCapability`** — from `lib/capabilities.tsx`, wraps UI sections. Backed by `CapabilityProvider` seeded in org layout.
4. Nav-item guards (`requiredModule`, `requiredCapability` fields in `lib/nav-items.ts`) — sidebar hides items the user lacks.

### Tenant Context Propagation

- Org slug comes from the URL parameter `[slug]`, resolved in `org/[slug]/layout.tsx`
- All backend API calls are tenant-scoped by the auth token (session carries orgId; backend resolves tenant schema from it)
- Frontend does not explicitly pass a tenant header; the gateway/backend derives tenant from the session
- `OrgProfileProvider` carries `verticalProfile`, `enabledModules`, `terminologyNamespace` — fetched once in the org layout and propagated via context to all child pages/components

### Layout and Navigation Shell

- **DesktopSidebar** (`components/desktop-sidebar.tsx`) — slate-950 dark sidebar, Motion animated active indicator, groups from `NAV_GROUPS`. Injects `--brand-color` CSS var from `brandColor` org setting.
- **MobileSidebar** (`components/mobile-sidebar.tsx`) — Sheet-based, uses `NAV_ITEMS` flat array.
- **CommandPaletteProvider** (`components/command-palette-provider.tsx`) — dynamically imports `CommandPaletteDialog`; triggered by Cmd+K.
- **Breadcrumbs** (`components/breadcrumbs.tsx`) — pathname-based.
- **NotificationBell** (`components/notifications/notification-bell.tsx`) — polls via SWR.
- **AssistantPanel** (`components/assistant/assistant-panel.tsx`) — Sheet panel, opens via `AssistantTrigger`; uses SSE streaming to `/api/assistant/chat`.

### AI Assistant Integration

- `AssistantProvider` (`components/assistant/assistant-provider.tsx`) — context: `isOpen`, `isAiEnabled`, `toggle()`. `aiEnabled` gate checked server-side in org layout from `OrgSettings.aiEnabled`.
- `useAssistantChat` hook (`hooks/use-assistant-chat.ts`) — manages message list, streaming SSE via `fetch` directly to `/api/assistant/chat` (backend endpoint, not a Next.js route handler). Supports specialist mode via `specialistId` option.
- Chat UI: `AssistantPanel` renders `UserMessage`, `AssistantMessage`, `ToolUseCard`, `ConfirmationCard`, `ToolResultCard`, `ErrorCard` sub-components.
- AI Queue review page: `settings/automations/ai-queue/page.tsx` — surfaces AI specialist invocations via `lib/api/ai-invocations.ts`.

---

## 6. Component Library Conventions

### `components/ui/` — Shadcn Primitives (Customised)

All Shadcn primitives have been restyled to the Signal Deck aesthetic. Key customisations:

- **Button** — pill-shaped (`rounded-full`) for primary/accent/soft/destructive; `rounded-md` for outline/secondary/ghost. Variants: `default, soft, accent, destructive, outline, secondary, ghost, plain, link`. → `components/ui/button.tsx:7`
- **Badge** — semantic variants: `lead, member, owner, admin, starter, pro, success, warning, destructive, neutral`.
- **Card** — slate borders, `shadow-sm`, `rounded-lg`.
- **Dialog / AlertDialog** — slate-950/25 backdrop, Motion enter/exit. Dialog-owns-button pattern to avoid Radix Slot collision under React 19 (OBS-2103, PR #1242).
- **Input / Textarea** — `focus-visible:ring-slate-500`.

Radix import: `import { Slot } from "radix-ui"` (unified package, not `@radix-ui/react-*`). → `components/ui/button.tsx:3`

### `components/<feature>/` — Domain Components

| Dir | Contents |
|-----|----------|
| `components/dashboard/` | KPI cards, MetricsStrip, ProjectHealthWidget, TeamWorkloadWidget, SensitiveEventsWidget, AdminStatsColumn, GettingStartedCard |
| `components/customers/` | CreateCustomerDialog, EditCustomerDialog, ArchiveCustomerDialog, CompletenessBadge, CustomFieldBadges |
| `components/projects/` | Project list table, create dialog |
| `components/invoices/` | StatusBadge, CreateInvoiceButton, invoice line editor |
| `components/billing/` | SubscribeButton, CancelConfirmDialog, PaymentHistory, TrialCountdown, GraceCountdown, SubscriptionBanner |
| `components/trust/` | Trust account widgets, Section866Advisory |
| `components/legal/` | UpcomingCourtDatesWidget, ConflictCheckClient |
| `components/automations/` | RuleList, automation rule editor |
| `components/schedules/` | ScheduleList, ScheduleCreateDialog |
| `components/capacity/` | AllocationGrid, UtilizationTable, UtilizationChart |
| `components/settings/` | GeneralSettingsForm, VerticalProfileSection, TaxSettingsForm, FeaturesSettingsForm |
| `components/assistant/` | AssistantPanel, AssistantProvider, AssistantTrigger, UserMessage, AssistantMessage, ToolUseCard, ConfirmationCard |
| `components/notifications/` | NotificationBell, NotificationsPageClient, NotificationPreferencesForm |
| `components/compliance/` | LifecycleDistributionSection, RetentionPolicyTable, CompliancePackList |
| `components/integrations/` | IntegrationCard, EmailIntegrationCard, PaymentIntegrationCard, KycIntegrationCard |
| `components/marketing/` | NavBar, HeroSection, BuiltForAfrica, FeaturesSection, PricingPreview, CtaSection, Footer |

### Design System Notes

- **Fonts:** `--font-sora` (display/h1), `--font-plex` (body/buttons), `--font-jetbrains` (mono/KPIs). Loaded via `next/font/google` in root layout.
- **Animation:** `tw-animate-css` for Tailwind utilities; `motion/react` (Framer Motion) for sidebar active indicator, dialogs, page transitions. Must only import in `"use client"` files.
- **Form pattern:** Zod schemas in `lib/schemas/<domain>.ts` + react-hook-form + Shadcn `Form` components. `lib/schemas/index.ts` barrel-exports all.

---

## 7. Vertical Signals

Vertical branching is runtime (not build-time). The `verticalProfile` string from `OrgSettings` drives all differentiation.

### Terminology Override System

- `lib/terminology-map.ts:1` — `TERMINOLOGY` object with three keys:
  - `"consulting-za"` — renames Customer→Client, Time Entry→Time Log, Rate Card→Billing Rates
  - `"accounting-za"` — renames Project→Engagement, Proposal→Engagement Letter, Rate Card→Fee Schedule
  - `"legal-za"` — renames Project→Matter, Task→Action Item, Invoice→Fee Note, Expense→Disbursement, Retainer→Mandate, Rate Card→Tariff Schedule, Budget→Fee Estimate
- `lib/terminology.tsx:28` — `TerminologyProvider` builds a map from the profile; `useTerminology()` returns `t(term)` translator
- `TerminologyHeading` (`components/terminology-heading.tsx`) and `TerminologyText` are client wrappers calling `t()`

### Profile-Gated Components

- `useProfile()` (`lib/hooks/useProfile.ts:26`) — returns `ProfileId`: `"consulting-za" | "legal-za" | "accounting-za" | "consulting-generic" | null`
- `TeamUtilizationWidget` (`components/dashboard/team-utilization-widget.tsx:18`) — self-gates: `shouldFetch = profile === "consulting-za"`. Renders nothing for other profiles.
- `VerticalProfileSection` in general settings — UI for selecting/changing the vertical profile.

### Module-Gated Routes (vertical-specific)

| Module slug | Nav label | Route | Profile association |
|-------------|-----------|-------|---------------------|
| `court_calendar` | Court Calendar | `/org/:slug/court-calendar` | legal-za |
| `conflict_check` | Conflict Check, Adverse Parties | `/org/:slug/conflict-check`, `/org/:slug/legal/adverse-parties` | legal-za |
| `lssa_tariff` | Tariffs | `/org/:slug/legal/tariffs` | legal-za |
| `trust_accounting` | Trust Accounting (6 sub-routes) | `/org/:slug/trust-accounting/*` | legal-za |
| `regulatory_deadlines` | Deadlines | `/org/:slug/deadlines` | legal/accounting |
| `bulk_billing` | Billing Runs | `/org/:slug/invoices/billing-runs` | any |
| `resource_planning` | Resources, Utilization | `/org/:slug/resources/*` | consulting-za |
| `automation_builder` | Automations, AI Queue | `/org/:slug/settings/automations/*` | any |

Dashboard also conditionally renders `<UpcomingCourtDatesWidget>` inside a `<ModuleGate module="court_calendar">` → `app/(app)/org/[slug]/dashboard/page.tsx:210`.

### Settings-Level Vertical Branching

- `VerticalProfileSection` in `settings/general/page.tsx` — shows current profile + allows admin to switch
- `settings/trust-accounting/page.tsx` — only shown when `trust_accounting` module enabled (admin-only)
- `settings/features/page.tsx` — module enable/disable toggles for the org

---

## Notable Absences / Negative Findings

- No Next.js Route Handlers found at `app/api/assistant/route.ts` — the AI assistant calls `/api/assistant/chat` directly on the backend (port 8080/8443), not via a Next.js proxy.
- No OpenAPI code generation — all types are hand-written and parallel Java backend DTOs.
- No Clerk — fully removed; `lib/auth/` abstraction replaced it entirely.
- No `@radix-ui/react-*` imports — unified `radix-ui` package used throughout.
- No `tailwind.config.ts` — Tailwind v4 CSS-first, tokens defined in `globals.css`.
- No per-feature `lib/<feature>/api.ts` files with their own fetch setup — everything flows through the single `lib/api/client.ts` client.
- No portal routes — the portal is a separate Next.js app at `portal/`.

---

## Essential Files Reference

| File | Significance |
|------|-------------|
| `frontend/lib/api/client.ts` | Single fetch client — all HTTP flows through here |
| `frontend/lib/api/index.ts` | Barrel re-exporting all domain API modules |
| `frontend/lib/types/index.ts` | Barrel re-exporting all domain types |
| `frontend/lib/types/customer.ts` | Customer, lifecycle, checklist, data-protection types |
| `frontend/lib/types/project.ts` | Project, Task time summaries, MyWork types |
| `frontend/lib/types/invoice.ts` | Invoice, InvoiceLine, UnbilledTime, Retainer types |
| `frontend/lib/types/legal.ts` | CourtDate, Prescription, ConflictCheck, AdverseParty, Tariff |
| `frontend/lib/types/trust.ts` | TrustAccount, TrustTransaction, TrustInvestment |
| `frontend/lib/terminology-map.ts` | Vertical terminology overrides (consulting-za, accounting-za, legal-za) |
| `frontend/lib/org-profile.tsx` | OrgProfileProvider — verticalProfile + enabledModules propagation |
| `frontend/lib/nav-items.ts` | Authoritative sidebar nav groups + settings items + requiredModule guards |
| `frontend/lib/capabilities.tsx` | CAPABILITIES constants + CapabilityProvider |
| `frontend/app/(app)/org/[slug]/layout.tsx` | Org shell: fetches caps/settings/billing, wraps all providers |
| `frontend/app/(app)/org/[slug]/dashboard/page.tsx` | Dashboard: shows vertical branching (court dates, sensitive events, module gates) |
| `frontend/lib/auth/server.ts` | Auth provider switcher (keycloak vs mock) |
| `frontend/lib/auth/providers/keycloak-bff.ts` | BFF provider: SESSION cookie forwarding to /bff/me |
| `frontend/hooks/use-assistant-chat.ts` | AI assistant SSE streaming hook |
| `frontend/components/module-gate.tsx` | Module gating component |
| `frontend/components/desktop-sidebar.tsx` | Sidebar shell with brand colour injection |
| `frontend/lib/hooks/useProfile.ts` | Narrows verticalProfile to known union type |

---

**Summary:** The Kazi frontend has 3 route groups (`(app)`, `(mock-auth)`, root public), approximately 68 pages, and 40+ distinct route paths under `/org/[slug]/`. The API-call pattern is strictly unified: a single server-only `lib/api/client.ts` handles all fetch with auth, domain modules wrap it, and client components consume SWR over server actions — no per-feature fetch clients exist. The sole exception is the AI assistant, which streams SSE directly to the backend via browser `fetch`. Vertical branching concentrates in three places: the `TERMINOLOGY` map in `lib/terminology-map.ts` (3 profiles: legal-za, accounting-za, consulting-za), the `ModuleGate` component used to conditionally render entire route sections, and `requiredModule`/`requiredCapability` guards on nav items. Legal-specific routes (trust-accounting, court-calendar, tariffs, conflict-check, adverse-parties) are all module-gated at both nav and page level.
