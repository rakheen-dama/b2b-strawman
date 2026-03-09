# Phase 43 — UX Quality Pass: Empty States, Contextual Help & Error Recovery

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After 42 phases, the platform has a rich feature set: projects, customers, tasks, time tracking, invoicing, rate cards, budgets, profitability dashboards, document templates (Tiptap + Word pipeline), comments, notifications, activity feeds, audit trails, custom fields, tags, saved views, and operational dashboards.

**The problem**: When a new organisation is provisioned, users land on a dashboard with empty charts and blank lists. There is no guided onboarding, no contextual help on complex features, and error states are generic. The platform *works* but doesn't *feel* welcoming or self-explanatory. This hurts both demo impressions and early-user retention.

**The fix**: A medium-depth UX quality pass across three pillars — meaningful empty states that guide action, inline contextual help on complex features, and categorised error recovery with actionable messages. All user-facing strings are centralised in an i18n-ready message catalog (not scattered across components).

## Objective

1. **Establish an i18n message catalog** on the frontend — a structured JSON-based system where all user-facing copy (empty states, help text, error messages, form labels) is defined by code and looked up at render time. Not a full i18n framework with locale switching — just the catalog pattern, lookup hook, and namespace structure. Locale switching layers on top later.
2. **Replace blank/generic empty states** on all list and dashboard pages (~15 pages) with actionable, contextual empty states — each with a heading, description, illustration/icon, and a primary CTA that takes the user directly to the creation flow.
3. **Add a "Getting Started" checklist** to the dashboard that guides new orgs through first-time setup steps, tracks completion, and is dismissible.
4. **Add inline contextual help** (~20-25 help points) on complex features — rate cards, budget configuration, invoice generation, template variables, custom fields — via a reusable tooltip/popover component sourced from the message catalog.
5. **Improve error handling UX** — categorise API errors into user-fixable vs. system errors, add error boundary components with recovery actions, improve form validation messages, and handle permission denials gracefully.

## Constraints & Assumptions

- **All user-facing strings in the message catalog.** No hardcoded strings in components for any new copy introduced by this phase. Existing strings are NOT migrated wholesale — only strings touched by this phase move to the catalog. Future phases can incrementally migrate more.
- **Message catalog is JSON files in the frontend.** Structure: `frontend/src/messages/en/` with namespace files (`empty-states.json`, `help.json`, `errors.json`, `getting-started.json`). A thin `useMessage(namespace, code)` hook resolves strings. No external CMS, no database storage, no runtime fetching.
- **No full i18n framework yet.** No `next-intl`, `react-i18next`, or locale negotiation. The catalog is English-only with the *structure* that makes adding locales trivial later (add `frontend/src/messages/af/` for Afrikaans, swap the import). The hook signature should accept a locale parameter that defaults to `'en'`.
- **Empty state component is reusable.** A single `EmptyState` component used everywhere, with props for icon, heading (message code), description (message code), primary CTA (label + action), and optional secondary link.
- **Getting Started checklist is per-org, stored server-side.** Completion state persists across sessions and members. Use a JSONB column on `OrgSettings` (already exists) or a lightweight `onboarding_progress` table — architect decides. The checklist is dismissible (a `dismissed_at` timestamp). Once dismissed, it never reappears for that org, even if steps are incomplete.
- **Checklist steps are product-defined, not configurable.** The ~6 steps are hardcoded in the frontend (with codes from the message catalog). The backend only tracks which step codes are marked complete and whether the checklist is dismissed.
- **Help content is static.** No admin-editable help text, no rich media, no external docs links (yet). Each help point is a 1-3 sentence explanation pulled from the message catalog. The `HelpTip` component renders a `?` icon that opens a popover.
- **Error classification happens on the frontend.** The backend already returns structured error responses (HTTP status codes, error codes in response body for validation). The frontend adds a classification layer that maps status codes + error codes to user-friendly messages from the catalog.
- **No changes to backend error response format.** The existing Spring Boot error responses are sufficient. This phase adds frontend interpretation, not backend changes (except the Getting Started progress tracking endpoint).
- **Shadcn UI components.** All new UI components use the existing Shadcn UI primitives (Tooltip, Popover, Card, Alert, Button). No new UI library dependencies.

---

## Section 1 — i18n Message Catalog

### File Structure

```
frontend/src/messages/
├── index.ts              — re-exports, useMessage hook, types
├── en/
│   ├── empty-states.json — codes for all empty state copy
│   ├── help.json         — codes for all contextual help text
│   ├── errors.json       — codes for all error messages
│   ├── getting-started.json — codes for checklist items
│   └── common.json       — shared labels (optional, for common patterns)
```

### Message Code Convention

Dot-delimited, hierarchical: `{page}.{element}.{property}`

Examples:
```json
// empty-states.json
{
  "projects.list.heading": "No projects yet",
  "projects.list.description": "Projects organise your work, documents, and time tracking. Create your first project to get started.",
  "projects.list.cta": "Create project",

  "dashboard.profitability.heading": "Profitability data not available",
  "dashboard.profitability.description": "Set up rate cards in Settings to start tracking project profitability.",
  "dashboard.profitability.link": "Go to rate card settings",

  "invoices.list.heading": "No invoices yet",
  "invoices.list.description": "Generate invoices from tracked time or create them manually. You'll need at least one project with logged time.",
  "invoices.list.cta": "Create invoice"
}

// help.json
{
  "rates.hierarchy.title": "Rate card hierarchy",
  "rates.hierarchy.body": "Rates are resolved in order: customer rate → project rate → organisation default. The most specific rate wins. This lets you set a standard hourly rate and override it for specific clients or projects.",

  "budget.setup.title": "Project budgets",
  "budget.setup.body": "Set a budget in hours, currency, or both. The system tracks actual time and cost against your budget and can alert you at configurable thresholds (e.g., 75%, 90%)."
}

// errors.json
{
  "api.validation": "Please check the highlighted fields and try again.",
  "api.forbidden": "You don't have permission for this action. Contact your organisation admin to update your role.",
  "api.notFound": "This item may have been deleted or moved. Go back and try again.",
  "api.conflict": "This record was updated by someone else. Refresh the page to see the latest version.",
  "api.serverError": "Something went wrong on our end. Please try again in a moment.",
  "api.networkError": "Unable to reach the server. Check your connection and try again.",
  "api.rateLimited": "Too many requests. Please wait a moment before trying again."
}
```

### useMessage Hook

```typescript
// Signature
function useMessage(namespace: string): {
  t: (code: string, interpolations?: Record<string, string>) => string;
}

// Usage
const { t } = useMessage('empty-states');
return <p>{t('projects.list.description')}</p>;

// With interpolation (for dynamic values)
const { t } = useMessage('errors');
return <p>{t('api.planLimit', { feature: 'projects', limit: '5' })}</p>;
// errors.json: "api.planLimit": "You've reached the {{feature}} limit ({{limit}}) for your plan. Upgrade to add more."
```

Simple `{{variable}}` interpolation in the hook — matches the template variable syntax used elsewhere in the platform. No pluralisation, no ICU message format — keep it simple.

---

## Section 2 — Empty State Component & Page Integration

### EmptyState Component

```
<EmptyState
  icon={FolderOpen}                          — Lucide icon component
  heading="empty-states:projects.list.heading"     — message code (namespace:code)
  description="empty-states:projects.list.description"
  cta={{ label: "empty-states:projects.list.cta", onClick: () => ... }}
  secondaryLink={{ label: "...", href: "/..." }}   — optional
/>
```

The component renders:
- A muted icon (48px, `text-muted-foreground`)
- Heading text (h3, `text-lg font-semibold`)
- Description text (p, `text-muted-foreground text-sm`, max-width ~400px for readability)
- Primary CTA button (default variant)
- Optional secondary link (text link below CTA)
- Centred layout, vertical stack, comfortable padding

### Pages to Integrate

Each page should show the `EmptyState` when its primary list/table has zero items. The component replaces whatever currently renders in the empty case (blank table, "No items found" text, etc.).

**Tier 1 — Primary entity pages (user hits these first):**

| Page | Icon | CTA Action | Notes |
|------|------|------------|-------|
| Projects list | FolderOpen | Open create project dialog | |
| Customers list | Users | Open create customer dialog | |
| Team page | UserPlus | Open invite member dialog | |
| My Work | ClipboardList | Navigate to projects (to find tasks) | Different — no direct "create" action |
| Documents list (project-scoped) | FileText | Open create document dialog | |

**Tier 2 — Time & billing pages (need upstream data):**

| Page | Icon | CTA Action | Notes |
|------|------|------------|-------|
| Time entries (My Work / project) | Clock | Open log time dialog | |
| Invoices list | Receipt | Open create invoice dialog | Description mentions needing time entries or customers first |
| Templates list | LayoutTemplate | Open create template dialog | |

**Tier 3 — Derived/analytical pages (need data from multiple sources):**

| Page | Icon | CTA Action | Notes |
|------|------|------------|-------|
| Profitability page | TrendingUp | Link to rate card settings | "Set up rate cards to see profitability" |
| Budget tab (project detail) | PiggyBank | Open budget config | "Configure a budget for this project" |
| Activity feed (project detail) | Activity | None (explanatory only) | "Activity will appear here as your team works on this project" |
| Notifications page | Bell | None (explanatory only) | "You're all caught up. Notifications appear when..." |
| Comments section (task/doc) | MessageSquare | Focus the comment input | "Start a conversation about this [task/document]" |
| Dashboard widgets (various) | BarChart3 | Link to relevant creation page | Each widget has its own empty state |

**Tier 4 — Settings & configuration pages:**

| Page | Icon | CTA Action | Notes |
|------|------|------------|-------|
| Rate cards (settings) | DollarSign | Open add rate dialog | |
| Custom fields (settings) | Settings2 | Open create field dialog | |
| Saved views | Filter | Open create view dialog | |
| Tags | Tag | Open create tag dialog | |

---

## Section 3 — Getting Started Checklist

### Checklist Items

6 items, ordered by natural setup flow:

| Step | Code | Label | Completion Trigger | Link |
|------|------|-------|--------------------|------|
| 1 | `create_project` | Create your first project | Org has ≥1 project | /projects |
| 2 | `add_customer` | Add a customer | Org has ≥1 customer | /customers |
| 3 | `invite_member` | Invite a team member | Org has ≥2 members | /settings/team |
| 4 | `log_time` | Log time on a task | Org has ≥1 time entry | /my-work |
| 5 | `setup_rates` | Set up your rate card | Org has ≥1 billing rate | /settings/rates |
| 6 | `create_invoice` | Generate your first invoice | Org has ≥1 invoice | /invoices |

### Backend

**Endpoint**: `GET /api/onboarding/progress`

Returns:
```json
{
  "steps": [
    { "code": "create_project", "completed": true },
    { "code": "add_customer", "completed": false },
    ...
  ],
  "dismissed": false,
  "completedCount": 2,
  "totalCount": 6
}
```

Completion is **computed on read**, not tracked via events. The endpoint checks entity counts directly (e.g., `projectRepository.count() > 0`). This is simpler than event-driven tracking and always accurate.

**Endpoint**: `POST /api/onboarding/dismiss`

Sets `dismissed_at` timestamp on OrgSettings. Returns 204.

**Storage**: Add `onboarding_dismissed_at TIMESTAMP NULL` to `org_settings` table. No separate table needed — onboarding state is org-level.

### Frontend

**`GettingStartedCard` component** — renders as a Card on the dashboard, positioned above the main dashboard grid.

- Title: "Getting started with DocTeams" (from message catalog)
- Progress indicator: "2 of 6 complete" with a subtle progress bar
- Each step: checkbox icon (checked/unchecked), label, arrow link to the relevant page
- Dismiss button: "Dismiss" text button in card header, confirms with a small "Are you sure?" popover
- Hidden when `dismissed === true` or all 6 steps are complete

**Data fetching**: SWR/React Query hook that calls `GET /api/onboarding/progress`. Refetch on window focus (so completing a step in another tab updates the checklist). Cache for 30 seconds.

---

## Section 4 — Inline Contextual Help

### HelpTip Component

```
<HelpTip code="help:rates.hierarchy" />
```

Renders a `?` icon (CircleHelp from Lucide, 16px, `text-muted-foreground`) that opens a Shadcn Popover on click containing:
- Title (from `{code}.title` in message catalog) — bold, text-sm
- Body (from `{code}.body` in message catalog) — text-sm, text-muted-foreground, max-width 320px

The popover dismisses on click outside or Escape. No "learn more" links in v1.

### Help Points to Add

**Rate cards & billing (~5 points):**
- Rate hierarchy explanation (org → project → customer override logic)
- Cost rates vs. billing rates (what each is used for)
- Billable vs. non-billable time entries (how this affects invoicing)
- Currency settings (where the default currency is set, what it affects)
- Rate snapshots (why changing a rate doesn't retroactively change past time entries)

**Budgets (~3 points):**
- Budget types (hours-only, currency-only, or both)
- Alert thresholds (what triggers budget warnings, where to configure)
- Budget vs. actual (how the system calculates consumed budget)

**Invoicing (~3 points):**
- Invoice lifecycle (draft → approved → sent → paid)
- Unbilled time (how time entries are selected for billing, what "unbilled" means)
- Invoice numbering (how the auto-incrementing number works, where the prefix is set)

**Document templates (~3 points):**
- Template variables (the `{{entity.field}}` syntax, where to find available variables)
- Tiptap vs. Word templates (when to use each approach)
- Template packs (what the common pack includes, how to clone/customise)

**Custom fields (~2 points):**
- Field types (text, number, date, select — what each supports)
- Field scoping (which entity types support custom fields)

**Dashboard & reports (~3 points):**
- Utilisation rate (how it's calculated — billable hours ÷ total capacity)
- Project health scoring (what the health indicators mean)
- Profitability margin (revenue − cost, how cost is derived from cost rates × hours)

**Other (~3 points):**
- Saved views (what's saved: filters, sort, column selection)
- Tags (cross-entity, how they differ from custom fields)
- Notification preferences (what each channel controls)

Total: ~22 help points.

### Placement Guidelines

- Place `HelpTip` next to section headings or form group labels, not next to individual fields
- Use a consistent pattern: `<h3>Section Title <HelpTip code="..." /></h3>`
- Do not add help tips to self-explanatory UI (e.g., a "Name" text field doesn't need a `?`)
- Maximum 2-3 help tips visible on any single page to avoid visual clutter

---

## Section 5 — Error Recovery & Feedback

### API Error Classification

Create a frontend utility (`frontend/src/lib/error-handler.ts`) that classifies API errors and returns user-friendly messages:

```typescript
type ErrorCategory = 'validation' | 'forbidden' | 'notFound' | 'conflict' | 'serverError' | 'networkError' | 'rateLimited';

function classifyError(error: unknown): {
  category: ErrorCategory;
  messageCode: string;       // code in errors.json
  retryable: boolean;
  action?: 'retry' | 'refresh' | 'goBack' | 'contactAdmin';
}
```

Classification rules:
- 400 → `validation` (retryable: false, action: none — field-level errors handle it)
- 401 → redirect to login (not classified, handled by auth layer)
- 403 → `forbidden` (retryable: false, action: `contactAdmin`)
- 404 → `notFound` (retryable: false, action: `goBack`)
- 409 → `conflict` (retryable: true, action: `refresh`)
- 422 → `validation` (same as 400)
- 429 → `rateLimited` (retryable: true, action: `retry`)
- 500/502/503 → `serverError` (retryable: true, action: `retry`)
- Network error (no response) → `networkError` (retryable: true, action: `retry`)

### Error Boundary Component

A React error boundary (`ErrorBoundary`) that catches render errors and shows a recovery UI:

```
<ErrorBoundary fallback={<ErrorFallback />}>
  <PageContent />
</ErrorBoundary>
```

`ErrorFallback` renders:
- An appropriate icon (AlertTriangle)
- The error message from the catalog
- Recovery action button(s) based on classification:
  - `retry` → "Try again" button (calls a retry callback)
  - `refresh` → "Refresh page" button
  - `goBack` → "Go back" button (router.back())
  - `contactAdmin` → text with admin contact guidance

Wrap each main page content area in an `ErrorBoundary`. Do not wrap the entire app shell — sidebar and header should remain functional.

### Toast Improvements

Standardise toast notifications:
- **Success**: Green check icon, auto-dismiss after 4 seconds. E.g., "Project created", "Time entry saved"
- **Error**: Red alert icon, does NOT auto-dismiss (user must close). Shows the classified error message from the catalog. Includes a "Retry" button for retryable errors.
- **Warning**: Amber icon, auto-dismiss after 6 seconds. For non-blocking issues (e.g., "Budget is 90% consumed").
- **Info**: Blue icon, auto-dismiss after 4 seconds. For neutral notifications.

### Form Validation

- Use field-level inline error messages (red text below the field) from the message catalog
- On submit failure, scroll to the first error field and focus it
- For fields with complex validation (e.g., rate must be positive, email format), show specific guidance not just "Invalid value"
- Add `errors.json` entries for common validation patterns: `"validation.required"`, `"validation.email"`, `"validation.positiveNumber"`, `"validation.maxLength"`, etc.

### Permission Denial Handling

When a user lacks permission for a feature (403 from API or frontend capability check after Phase 41):
- Do NOT show a blank page or redirect silently
- Show a `PermissionDenied` component: lock icon, "You don't have access to [feature name]", "Contact your organisation admin to update your role", with a "Go to dashboard" button
- For inline elements (e.g., a "Create Invoice" button the user can't use), disable the button and show a tooltip explaining why

---

## Out of Scope

- **Full i18n with locale switching.** This phase builds the catalog structure only. Adding Afrikaans, Zulu, or other locale files is a future phase.
- **Migrating all existing hardcoded strings.** Only strings related to empty states, help text, errors, and the getting started checklist are added to the catalog. Existing button labels, page titles, etc. stay as-is unless touched by this phase.
- **Interactive product tours / walkthrough overlays.** The getting started checklist is a passive guide, not a step-by-step interactive tour.
- **Analytics on user behaviour.** No tracking of which help tips are clicked, which empty states are seen, or where users drop off. That's a future concern.
- **Backend error response changes.** The backend error format is sufficient. This phase adds frontend interpretation only.
- **Illustrations / custom artwork.** Empty states use Lucide icons, not custom illustrations. If brand illustrations are desired later, the `EmptyState` component can accept a custom illustration prop, but creating artwork is out of scope.
- **Onboarding for specific user roles.** The getting started checklist is org-level, not role-specific. A "new member" onboarding flow is a separate concern.
- **Dark mode adjustments.** The existing dark mode support in Shadcn/Tailwind should handle new components automatically. No special dark mode work.

## ADR Topics

- **ADR: Message catalog strategy** — JSON files vs. `next-intl` vs. `react-i18next`. Decision: plain JSON + custom hook for simplicity. Justification: avoids framework lock-in, the structure is compatible with any i18n library added later, and the app doesn't need pluralisation or ICU format yet.
- **ADR: Getting started completion tracking** — computed-on-read (count entities) vs. event-driven (store completion events). Decision: computed-on-read. Justification: always accurate, no event wiring needed, performance is fine (6 simple count queries on small tables).

## Style & Boundaries

- All new components use Shadcn UI primitives (Card, Button, Popover, Tooltip, Alert)
- All user-facing strings go through the message catalog — no inline strings
- Empty states should feel warm and helpful, not clinical. Use encouraging language: "Create your first project" not "No projects found"
- Help text should be concise (1-3 sentences) and specific to DocTeams, not generic. "Rates are resolved in order: customer → project → org default" not "A rate card is a pricing structure"
- Error messages should be actionable: always tell the user what to do next, not just what went wrong
- The `useMessage` hook should log a console warning in development if a code is missing (helps catch typos)
- The Getting Started card should feel like a natural part of the dashboard, not an intrusive overlay
