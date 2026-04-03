# Phase 59 — User Help Documentation Site

Phase 59 adds self-service user documentation to the HeyKazi platform -- a standalone Nextra 4 documentation site at `docs.heykazi.com` containing ~30 MDX articles covering feature guides, getting-started walkthroughs, admin references, and vertical-specific content. This phase introduces no backend code, no database migrations, and no new entities. The deliverables are: (1) a new standalone Nextra project in the monorepo `docs/` directory, (2) ~30 MDX content files drafted from codebase analysis, and (3) targeted frontend changes to wire contextual deep-links from existing help touchpoints to specific documentation pages.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 432 | Doc Site Scaffold & Home Page | Docs | -- | M | 432A, 432B | **Done** (PRs #898, #899) |
| 433 | Getting Started & Core Feature Guides | Docs (Content) | 432 | M | 433A, 433B | **Done** (PRs #900, #901) |
| 434 | Admin & Settings Guides | Docs (Content) | 432 | S | 434A | **Done** (PR #902) |
| 435 | P1 Feature Guides | Docs (Content) | 432 | M | 435A, 435B | **Done** (PRs #903, #904) |
| 436 | P2 Features & Vertical Guides | Docs (Content) | 432 | M | 436A, 436B | **Done** (PRs #905, #906) |
| 437 | Frontend Contextual Deep-Links | Frontend | 432 | M | 437A, 437B | |

## Dependency Graph

```
[E432 Doc Site Scaffold & Home Page] ──────────────────────────────┐
    │                                                               │
    ├──► [E433 Getting Started & Core Feature Guides]               │
    │         ├── 433A (3 getting-started + projects)               │
    │         └── 433B (customers, tasks, time-tracking, invoicing) │
    │                                                               │
    ├──► [E434 Admin & Settings Guides]                             │
    │         └── 434A (team, org-settings, integrations, billing)  │
    │                                                               │
    ├──► [E435 P1 Feature Guides]                                   │
    │         ├── 435A (documents, proposals, expenses, rate-cards)  │
    │         └── 435B (reports, resource, automations, portal)     │
    │                                                               │
    ├──► [E436 P2 Features & Vertical Guides]                       │
    │         ├── 436A (custom-fields, info-requests, ai-assistant) │
    │         └── 436B (3 accounting + 3 legal stubs)               │
    │                                                               │
    └──► [E437 Frontend Contextual Deep-Links]                      │
              ├── 437A (docsLink, HelpTip, EmptyState, sidebar)     │
              └── 437B (~19 call-site wiring + tests)               │
```

**Parallel tracks**: After Epic 432 (Scaffold) lands, Epics 433-436 (all content) and Epic 437 (frontend links) can all begin in parallel -- they have zero dependency on each other. Within Epic 433, Slice 433A must complete before 433B (first-project.mdx cross-links to the projects guide). Within Epic 437, Slice 437A must complete before 437B (utility and component changes are prerequisites for call-site wiring).

## Implementation Order

### Stage 1: Infrastructure Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 432: Doc Site Scaffold & Home Page (432A **Done** PR #898, 432B **Done** PR #899) | Nextra project scaffold, theming, _meta.ts files, home page, and build verification. All content slices depend on this. |

### Stage 2: Content & Frontend (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 433: Getting Started & Core Feature Guides (433A **Done** PR #900, 433B **Done** PR #901) | P0 content -- 8 articles covering onboarding and core workflows. Highest priority for launch. |
| 2b | Epic 434: Admin & Settings Guides (434A **Done** PR #902) | P0 + P1 admin content -- 4 articles covering team management, settings, integrations, billing. |
| 2c | Epic 435: P1 Feature Guides (435A **Done** PR #903, 435B **Done** PR #904) | P1 content -- 8 feature guides for secondary features. |
| 2d | Epic 436: P2 Features & Vertical Guides (436A **Done** PR #905, 436B **Done** PR #906) | P2 features + vertical-specific content. Lowest content priority. |
| 2e | Epic 437A: Frontend Component Changes | docsLink utility, HelpTip extension, EmptyState fix, sidebar Help item. Independent of content. |

### Stage 3: Frontend Wiring (After Component Changes)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3 | Epic 437B: Call-Site Wiring & Tests | Wire ~19 EmptyState/HelpTip instances with doc links. Depends on 437A component changes. Links will work once content merges. |

### Timeline

```
Stage 1:  [E432A] [E432B]                                       <- scaffold (must complete first)
Stage 2:  [E433A] [E433B] [E434A] [E435A] [E435B] [E436A] [E436B] [E437A]  <- parallel content + frontend
Stage 3:  [E437B]                                                <- call-site wiring (after 437A)
```

---

## Epic 432: Doc Site Scaffold & Home Page

**Goal**: Create the `docs/` project directory with a fully functional Nextra 4 site. Configure the Next.js project scaffold (package.json, next.config.ts, tsconfig.json, PostCSS, Tailwind v4). Apply HeyKazi branding via custom CSS (Sora/IBM Plex Sans/JetBrains Mono fonts, slate OKLCH palette, teal accents, dark mode). Build all `_meta.ts` navigation files for the 5 content sections. Create the home page with hero section and category cards. Add public assets (logo, favicon). Verify the site builds and deploys correctly.

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.2, 11.3, 11.4.1, 11.6, 11.8.3; ADR-227, ADR-228

**Dependencies**: None (foundation epic)

**Scope**: Docs

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **432A** | 432.1--432.7 | Nextra 4 project scaffold: package.json, next.config.ts, tsconfig.json, postcss.config.mjs, app/globals.css (Tailwind v4 with brand tokens), app/layout.tsx (fonts, Nextra theme, header, footer), app/[[...mdxPath]]/page.tsx (catch-all renderer), mdx-components.tsx, public/ assets, .gitignore, .env.local | **Done** (PR #898) |
| **432B** | 432.8--432.13 | All `_meta.ts` navigation files (top-level + 6 section metas), home page (content/index.mdx) with hero and category cards, HomeCards custom component, build verification | **Done** (PR #899) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 432.1 | Create `docs/package.json` with Nextra 4 dependencies | 432A | | `docs/package.json`. Dependencies: `next` (^16), `nextra` (^4), `nextra-theme-docs` (^4), `react` (^19), `react-dom` (^19). Dev dependencies: `typescript`, `@types/react`, `@types/node`, `tailwindcss` (^4), `@tailwindcss/postcss`, `postcss`. Scripts: `dev` (`next dev -p 3003`), `build` (`next build`), `start` (`next start -p 3003`). Set `"private": true`. Pattern: check Nextra 4 docs for exact peer dependency versions. |
| 432.2 | Create `docs/next.config.ts` with Nextra plugin | 432A | | `docs/next.config.ts`. Import `nextra` from `"nextra"`. Call `nextra({ contentDirBasePath: "/" })` to create `withNextra`. Export `withNextra({ reactStrictMode: true })`. See architecture Section 11.2.2 for exact code. |
| 432.3 | Create `docs/tsconfig.json` and `docs/postcss.config.mjs` | 432A | | `docs/tsconfig.json`: extend Next.js defaults, strict mode, include `"next-env.d.ts"`, paths `@/*` mapping to `./*`. `docs/postcss.config.mjs`: export `{ plugins: { "@tailwindcss/postcss": {} } }`. Pattern: match frontend/postcss.config.mjs structure for PostCSS. |
| 432.4 | Create `docs/app/globals.css` with HeyKazi brand tokens | 432A | | `docs/app/globals.css`. Import Tailwind via `@import "tailwindcss"`. Define `:root` CSS custom properties: `--nextra-primary-hue: 175deg`, `--nextra-primary-saturation: 60%`. Override Nextra content heading fonts to `var(--font-display)` (Sora). Override code blocks to `var(--font-mono)` (JetBrains Mono). See architecture Section 11.3.2 for token values. Tune colors to match HeyKazi's slate OKLCH palette -- reference `frontend/app/globals.css` for exact OKLCH values. |
| 432.5 | Create `docs/app/layout.tsx` with fonts, Nextra theme, header/footer | 432A | | `docs/app/layout.tsx`. Load Sora, IBM Plex Sans, JetBrains Mono via `next/font/google`. Export root `<html>` + `<body>` with Nextra `Layout` component. Configure Navbar with logo ("HeyKazi Docs"), projectLink (`https://app.heykazi.com`). Configure Footer with copyright. Call `getPageMap()` for navigation. Set metadata title/description. See architecture Section 11.2.2 for exact code. Read `frontend/app/layout.tsx` for font loading pattern. |
| 432.6 | Create `docs/app/[[...mdxPath]]/page.tsx` catch-all renderer and `docs/mdx-components.tsx` | 432A | | `docs/app/[[...mdxPath]]/page.tsx`: Nextra catch-all page with `generateStaticParams`, `generateMetadata`, and MDX rendering. See architecture Section 11.2.2 for exact code. `docs/mdx-components.tsx`: export `useMDXComponents` from `nextra-theme-docs`. Verify against Nextra 4 API -- the `useMDXComponents` export is a function, not a hook, called at module scope. |
| 432.7 | Create `docs/public/` assets and `docs/.gitignore` and `docs/.env.local` | 432A | | `docs/public/logo.svg`: copy HeyKazi logo SVG from existing frontend public assets (check `frontend/public/` for logo). `docs/public/favicon.ico`: copy from frontend. `docs/.gitignore`: `.next/`, `node_modules/`, `.env.local` (same pattern as `frontend/.gitignore`). `docs/.env.local`: `NEXT_PUBLIC_APP_URL=https://app.heykazi.com`. |
| 432.8 | Create top-level `docs/content/_meta.ts` | 432B | | `docs/content/_meta.ts`. Export navigation order: `index` (hidden page type), `"getting-started"`, `features`, `admin`, `verticals`. See architecture Section 11.2.3 for exact code. |
| 432.9 | Create section `_meta.ts` files for all 6 content directories | 432B | | Create 6 files: `docs/content/getting-started/_meta.ts` (3 entries: quick-setup, invite-your-team, first-project), `docs/content/features/_meta.ts` (16 entries in architecture-specified order), `docs/content/admin/_meta.ts` (4 entries), `docs/content/verticals/_meta.ts` (2 entries: accounting, legal with "Coming Soon" label), `docs/content/verticals/accounting/_meta.ts` (3 entries), `docs/content/verticals/legal/_meta.ts` (3 entries). See architecture Sections 11.2.3 for exact code for features and verticals _meta files. |
| 432.10 | Create `docs/components/home-cards.tsx` | 432B | | `docs/components/home-cards.tsx`. Client component. Grid of feature-category cards linking to section landing pages: Getting Started, Features, Administration, Accounting Firms, Legal Firms. Each card: icon, title, description, link. Style with Tailwind: lifted white cards (`bg-white shadow-sm`), slate borders, teal hover accent. Use `"use client"` directive for hover interactions. Pattern: follow HeyKazi card aesthetic from `frontend/components/ui/card.tsx`. |
| 432.11 | Create `docs/content/index.mdx` home page | 432B | | `docs/content/index.mdx`. Hero section: title "HeyKazi Documentation", subtitle describing the platform. Import and render `<HomeCards />` component below hero. Include quick links section with top 3 getting-started articles. Frontmatter: title "HeyKazi Documentation", description for SEO. ~300 words. Reference architecture Section 11.7 for home page spec. |
| 432.12 | Create placeholder MDX files for all sections | 432B | | Create minimal placeholder `.mdx` files in each content directory so the navigation renders correctly and `pnpm build` succeeds. Each placeholder: frontmatter with title + description, single `# Title` heading, one sentence "Content coming soon." This ensures Flexsearch indexing and nav generation work. Create all 29 placeholder files matching the architecture Section 11.2.1 file tree. These will be overwritten by content epics 433-436. |
| 432.13 | Verify build and search functionality | 432B | | Run `cd docs && pnpm install && pnpm build` -- must exit 0. Verify: all section headings appear in sidebar nav, home page renders with cards, search bar is functional (returns placeholder results), dark mode toggle works, fonts render correctly (Sora for headings, IBM Plex Sans for body). Document any Vercel project configuration needed (root directory, framework preset, node version). |

### Key Files

**Slice 432A -- Create:**
- `docs/package.json`
- `docs/next.config.ts`
- `docs/tsconfig.json`
- `docs/postcss.config.mjs`
- `docs/app/globals.css`
- `docs/app/layout.tsx`
- `docs/app/[[...mdxPath]]/page.tsx`
- `docs/mdx-components.tsx`
- `docs/public/logo.svg`
- `docs/public/favicon.ico`
- `docs/.gitignore`
- `docs/.env.local`

**Slice 432A -- Read for context:**
- `frontend/app/globals.css` -- OKLCH color tokens to replicate
- `frontend/app/layout.tsx` -- font loading pattern (Sora, IBM Plex Sans, JetBrains Mono)
- `frontend/public/` -- logo and favicon source

**Slice 432B -- Create:**
- `docs/content/_meta.ts`
- `docs/content/getting-started/_meta.ts`
- `docs/content/features/_meta.ts`
- `docs/content/admin/_meta.ts`
- `docs/content/verticals/_meta.ts`
- `docs/content/verticals/accounting/_meta.ts`
- `docs/content/verticals/legal/_meta.ts`
- `docs/components/home-cards.tsx`
- `docs/content/index.mdx`
- `docs/content/**/*.mdx` (29 placeholder files)

**Slice 432B -- Read for context:**
- `frontend/components/ui/card.tsx` -- card styling pattern for HomeCards component

### Architecture Decisions

- **Separate `docs/` top-level directory**: The doc site is a standalone Next.js project, not a subdirectory of `frontend/`. This ensures independent builds, independent Vercel deployment, and zero impact on the main app's bundle size. Per ADR-228.
- **Placeholder MDX files in scaffold**: Creating all 29 placeholders in the scaffold ensures the navigation tree, search index, and build are fully functional from day one. Content epics overwrite these placeholders. This avoids build failures when _meta.ts references files that don't exist.
- **Port 3003 for local dev**: The doc site runs on port 3003 to avoid conflicts with frontend (3000) and portal (3002).
- **Two-slice decomposition**: 432A covers pure infrastructure (project files, config, build tooling). 432B covers content structure and the home page. 432A touches 12 files; 432B touches ~38 files (7 _meta files + 1 component + 1 home page + 29 placeholders), but the placeholders are trivial boilerplate.

---

## Epic 433: Getting Started & Core Feature Guides

**Goal**: Write the 8 highest-priority (P0) content articles: 3 getting-started walkthroughs (quick-setup, invite-your-team, first-project) and 5 core feature guides (projects, customers, tasks, time-tracking, invoicing). These articles are the minimum viable documentation set -- they cover the platform's primary workflow from onboarding through invoicing. Each article is drafted by analyzing the relevant codebase area (controllers, services, frontend components, existing inline help text from `lib/messages/en/help.json` and `lib/messages/en/empty-states.json`).

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.4.2, 11.4.3, 11.7; ADR-229

**Dependencies**: Epic 432 (doc site scaffold must exist)

**Scope**: Docs (Content)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **433A** | 433.1--433.4 | 3 getting-started guides (quick-setup, invite-your-team, first-project) + projects.mdx feature guide. 4 articles, ~3,900 estimated words. | **Done** (PR #900) |
| **433B** | 433.5--433.8 | 4 core feature guides (customers, tasks, time-tracking, invoicing). 4 articles, ~3,500 estimated words. | **Done** (PR #901) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 433.1 | Write `getting-started/quick-setup.mdx` | 433A | | `docs/content/getting-started/quick-setup.mdx`. ~1,000 words. Cover: signing up, first login experience, org creation, navigating the dashboard, getting-started checklist overview. Follow the content template from architecture Section 11.4.2. **Read for context**: `frontend/components/setup/` (getting-started checklist components), `frontend/lib/messages/en/getting-started.json` (checklist item labels), `frontend/app/(app)/org/[slug]/dashboard/page.tsx` (dashboard layout), `frontend/app/(app)/create-org/page.tsx` (org creation flow). |
| 433.2 | Write `getting-started/invite-your-team.mdx` | 433A | | `docs/content/getting-started/invite-your-team.mdx`. ~800 words. Cover: navigating to team settings, sending invitations, role selection (Admin, Member), what invited users see, managing pending invitations. **Read for context**: `frontend/app/(app)/org/[slug]/team/page.tsx` (team page), `frontend/components/team/` (invitation components), `frontend/lib/messages/en/help.json` (team-related help text), `frontend/lib/messages/en/empty-states.json` (team empty state text). |
| 433.3 | Write `getting-started/first-project.mdx` | 433A | | `docs/content/getting-started/first-project.mdx`. ~1,200 words. Cover: end-to-end walkthrough: create a customer, create a project, add tasks, log time, create an invoice. This is the cornerstone onboarding article. Cross-links to projects, customers, tasks, time-tracking, and invoicing guides. Use Nextra `<Steps>` component. **Read for context**: `frontend/app/(app)/org/[slug]/projects/page.tsx` (project creation), `frontend/app/(app)/org/[slug]/customers/page.tsx` (customer creation), `frontend/components/projects/` (project form components), `frontend/lib/messages/en/empty-states.json` (empty state descriptions for each entity). |
| 433.4 | Write `features/projects.mdx` | 433A | | `docs/content/features/projects.mdx`. ~900 words. Cover: what projects are, key concepts (status lifecycle, project types, project members), creating/editing projects, project detail tabs (tasks, time, documents, activity), templates, archiving. Note vertical terminology ("Engagements" in accounting, "Matters" in legal). **Read for context**: `frontend/app/(app)/org/[slug]/projects/` (project list and detail pages), `frontend/components/projects/` (project components), `frontend/lib/messages/en/help.json` (project help tooltips), `frontend/lib/messages/en/empty-states.json` (project empty state). |
| 433.5 | Write `features/customers.mdx` | 433B | | `docs/content/features/customers.mdx`. ~800 words. Cover: what customers are, customer types (individual, company), creating/editing customers, customer detail tabs (projects, invoices, documents, contacts), customer lifecycle (onboarding, active, dormant), linking projects to customers. **Read for context**: `frontend/app/(app)/org/[slug]/customers/` (customer pages), `frontend/components/customers/` (customer components), `frontend/lib/messages/en/help.json` (customer help text), `frontend/lib/messages/en/empty-states.json`. |
| 433.6 | Write `features/tasks.mdx` | 433B | | `docs/content/features/tasks.mdx`. ~800 words. Cover: task creation, assignees, status workflow (open, in progress, done), task detail sheet, claiming/releasing tasks, bulk operations, saved views, tags, linking tasks to projects. **Read for context**: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (tasks tab on project detail), `frontend/components/tasks/` (task components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 433.7 | Write `features/time-tracking.mdx` | 433B | | `docs/content/features/time-tracking.mdx`. ~900 words. Cover: logging time entries, timer vs manual entry, billable/non-billable, rate application, bulk time entry, calendar view, My Work time section, time entry approval workflow. **Read for context**: `frontend/components/time-entries/` (time entry components), `frontend/components/time-tracking/` (timer components), `frontend/app/(app)/org/[slug]/my-work/` (My Work page), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 433.8 | Write `features/invoicing.mdx` | 433B | | `docs/content/features/invoicing.mdx`. ~1,000 words. Cover: invoice creation (from unbilled time, manual), invoice lifecycle (draft, sent, paid, overdue, written-off), line items, tax configuration, PDF generation, payment recording, billing runs, invoice list and detail views. **Read for context**: `frontend/app/(app)/org/[slug]/invoices/` (invoice pages), `frontend/components/invoices/` (invoice components), `frontend/components/billing-runs/` (billing run components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |

### Key Files

**Slice 433A -- Create (overwrite placeholders):**
- `docs/content/getting-started/quick-setup.mdx`
- `docs/content/getting-started/invite-your-team.mdx`
- `docs/content/getting-started/first-project.mdx`
- `docs/content/features/projects.mdx`

**Slice 433A -- Read for context:**
- `frontend/components/setup/` -- getting-started checklist components
- `frontend/lib/messages/en/getting-started.json` -- checklist item labels
- `frontend/lib/messages/en/help.json` -- inline help text (canonical terminology)
- `frontend/lib/messages/en/empty-states.json` -- empty state descriptions
- `frontend/app/(app)/org/[slug]/projects/` -- project pages
- `frontend/components/projects/` -- project components

**Slice 433B -- Create (overwrite placeholders):**
- `docs/content/features/customers.mdx`
- `docs/content/features/tasks.mdx`
- `docs/content/features/time-tracking.mdx`
- `docs/content/features/invoicing.mdx`

**Slice 433B -- Read for context:**
- `frontend/app/(app)/org/[slug]/customers/` -- customer pages
- `frontend/components/customers/` -- customer components
- `frontend/components/tasks/` -- task components
- `frontend/components/time-entries/` -- time entry components
- `frontend/components/time-tracking/` -- timer components
- `frontend/app/(app)/org/[slug]/invoices/` -- invoice pages
- `frontend/components/invoices/` -- invoice components
- `frontend/lib/messages/en/help.json` -- inline help text
- `frontend/lib/messages/en/empty-states.json` -- empty state descriptions

### Architecture Decisions

- **Two-slice decomposition**: Split by article type -- getting-started guides (narrative, longer) in 433A with projects as the natural companion, core CRUD feature guides in 433B. This keeps each slice at 4 articles.
- **Codebase reading as prerequisite**: Each content task specifies which codebase files the agent must read to understand the feature. This is critical for accuracy -- articles must use the exact UI labels from the i18n message catalog, describe the actual workflow (not an imagined one), and reference real terminology.
- **Cross-linking**: `first-project.mdx` in 433A cross-links to feature guides. If 433B has not merged yet, the links will point to placeholder content. This is acceptable -- the links will resolve correctly once both slices merge.

---

## Epic 434: Admin & Settings Guides

**Goal**: Write the 4 admin/settings guides covering team management and permissions, organization settings, integrations configuration, and billing/subscription management. These articles target org admins who configure the platform for their team. Two are P0 (team-permissions, billing) and two are P1 (org-settings, integrations).

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.4.2, 11.4.3, 11.7; ADR-229

**Dependencies**: Epic 432 (doc site scaffold must exist)

**Scope**: Docs (Content)

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **434A** | 434.1--434.4 | 4 admin guides (team-permissions, org-settings, integrations, billing). 4 articles, ~2,800 estimated words. | **Done** (PR #902) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 434.1 | Write `admin/team-permissions.mdx` | 434A | | `docs/content/admin/team-permissions.mdx`. ~800 words. Cover: inviting members, removing members, role types (Owner, Admin, Member), custom roles (capabilities matrix), permission inheritance, what each capability grants access to. **Read for context**: `frontend/app/(app)/org/[slug]/team/page.tsx` (team page), `frontend/components/team/` (team management components), `frontend/components/roles/` (role management components), `frontend/lib/capabilities.tsx` (capability definitions), `frontend/lib/messages/en/help.json` (team/role help text). |
| 434.2 | Write `admin/org-settings.mdx` | 434A | | `docs/content/admin/org-settings.mdx`. ~700 words. Cover: org name/slug, logo, timezone, default currency, terminology overrides (renaming features per vertical), notification preferences, data retention settings. **Read for context**: `frontend/app/(app)/org/[slug]/settings/` (settings pages), `frontend/components/settings/` (settings components), `frontend/lib/terminology-map.ts` (terminology override system), `frontend/lib/messages/en/help.json`. |
| 434.3 | Write `admin/integrations.mdx` | 434A | | `docs/content/admin/integrations.mdx`. ~600 words. Cover: integration settings page, BYOAK (Bring Your Own API Key) model, configuring Claude AI assistant API key, email integration, payment gateway (PayFast) setup, available integrations and their status. **Read for context**: `frontend/app/(app)/org/[slug]/settings/` (settings pages -- look for integrations route), `frontend/components/integrations/` (integration components), `frontend/lib/messages/en/help.json`. |
| 434.4 | Write `admin/billing.mdx` | 434A | | `docs/content/admin/billing.mdx`. ~700 words. Cover: subscription plans (Starter, Pro), billing lifecycle (trial, active, grace period, locked), payment methods, viewing invoices, upgrading/downgrading plans, what happens when a subscription expires, contacting support. **Read for context**: `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` (billing page), `frontend/components/billing/` (billing components), `frontend/lib/subscription-context.tsx` (subscription state), `frontend/lib/messages/en/help.json`. |

### Key Files

**Slice 434A -- Create (overwrite placeholders):**
- `docs/content/admin/team-permissions.mdx`
- `docs/content/admin/org-settings.mdx`
- `docs/content/admin/integrations.mdx`
- `docs/content/admin/billing.mdx`

**Slice 434A -- Read for context:**
- `frontend/app/(app)/org/[slug]/team/page.tsx` -- team management page
- `frontend/app/(app)/org/[slug]/settings/` -- settings pages (org settings, billing, integrations)
- `frontend/components/team/` -- team/invite components
- `frontend/components/roles/` -- role management
- `frontend/components/billing/` -- billing components
- `frontend/components/integrations/` -- integration settings
- `frontend/components/settings/` -- settings components
- `frontend/lib/capabilities.tsx` -- capability definitions
- `frontend/lib/terminology-map.ts` -- terminology override system
- `frontend/lib/subscription-context.tsx` -- subscription state
- `frontend/lib/messages/en/help.json` -- inline help text

### Architecture Decisions

- **Single slice for 4 articles**: All 4 admin guides share overlapping codebase context (settings pages, team pages, billing pages). The agent reads settings-area code once and writes all 4 articles. Splitting would force redundant codebase reads.
- **P0 and P1 mixed in one slice**: Separating team-permissions and billing (P0) from org-settings and integrations (P1) would create two 2-article slices -- below the minimum efficient size. Grouping all 4 is more efficient.

---

## Epic 435: P1 Feature Guides

**Goal**: Write 8 feature guides for secondary features: documents, proposals, expenses, rate-cards/budgets, reports, resource-planning, workflow-automations, and customer-portal. These are P1 articles -- important for a complete documentation set but not blocking launch.

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.4.2, 11.4.3, 11.7; ADR-229

**Dependencies**: Epic 432 (doc site scaffold must exist)

**Scope**: Docs (Content)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **435A** | 435.1--435.4 | 4 feature guides: documents, proposals, expenses, rate-cards-budgets. ~2,900 estimated words. | **Done** (PR #903) |
| **435B** | 435.5--435.8 | 4 feature guides: reports, resource-planning, workflow-automations, customer-portal. ~2,900 estimated words. | **Done** (PR #904) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 435.1 | Write `features/documents.mdx` | 435A | | `docs/content/features/documents.mdx`. ~900 words. Cover: document types (uploaded, generated from template), uploading documents, document scopes (project, customer, org), template-based generation (DOCX templates, merge fields), document preview, version history, e-signing integration. **Read for context**: `frontend/app/(app)/org/[slug]/documents/` (document pages), `frontend/components/documents/` (document components), `frontend/components/editor/` (template editor), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.2 | Write `features/proposals.mdx` | 435A | | `docs/content/features/proposals.mdx`. ~700 words. Cover: what proposals are, creating proposals, proposal sections, sending to customers, customer approval workflow, converting approved proposals to projects (engagement orchestration), proposal templates. **Read for context**: `frontend/app/(app)/org/[slug]/proposals/` (proposal pages), `frontend/components/proposals/` (proposal components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.3 | Write `features/expenses.mdx` | 435A | | `docs/content/features/expenses.mdx`. ~500 words. Cover: logging expenses, expense categories, linking expenses to projects/customers, billable vs non-billable expenses, including expenses on invoices, expense reports. **Read for context**: `frontend/components/expenses/` (expense components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.4 | Write `features/rate-cards-budgets.mdx` | 435A | | `docs/content/features/rate-cards-budgets.mdx`. ~800 words. Cover: org-level default rates, member-level rates, customer-specific rates, project-specific rates, rate hierarchy (which rate applies), project budgets (hours and revenue), budget alerts, profitability tracking. **Read for context**: `frontend/components/rates/` (rate card components), `frontend/components/budget/` (budget components), `frontend/components/profitability/` (profitability components), `frontend/app/(app)/org/[slug]/profitability/` (profitability page), `frontend/lib/messages/en/help.json`. |
| 435.5 | Write `features/reports.mdx` | 435B | | `docs/content/features/reports.mdx`. ~700 words. Cover: available report types (timesheet, invoice aging, project profitability), running reports, filters and parameters, export formats (CSV, PDF), scheduling reports. **Read for context**: `frontend/app/(app)/org/[slug]/reports/` (reports pages), `frontend/components/reports/` (report components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.6 | Write `features/resource-planning.mdx` | 435B | | `docs/content/features/resource-planning.mdx`. ~700 words. Cover: what resource planning is, allocating team members to projects, capacity view, utilization tracking, identifying over/under-allocated members, planning horizon. **Read for context**: `frontend/app/(app)/org/[slug]/resources/` (resource planning pages), `frontend/components/capacity/` (capacity components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.7 | Write `features/workflow-automations.mdx` | 435B | | `docs/content/features/workflow-automations.mdx`. ~800 words. Cover: what automations are, trigger types (status change, date-based, field change), action types (notify, assign, update field, create task), creating automation rules, testing automations, common automation recipes. **Read for context**: `frontend/components/automations/` (automation components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 435.8 | Write `features/customer-portal.mdx` | 435B | | `docs/content/features/customer-portal.mdx`. ~700 words. Cover: what the customer portal is, setting up portal access for customers, what customers can see (projects, invoices, documents, tasks), portal contact management, portal comments, information request uploads. **Read for context**: `frontend/components/portal/` (portal-related components in main app), `frontend/app/(app)/org/[slug]/customers/` (customer detail -- portal contacts section), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |

### Key Files

**Slice 435A -- Create (overwrite placeholders):**
- `docs/content/features/documents.mdx`
- `docs/content/features/proposals.mdx`
- `docs/content/features/expenses.mdx`
- `docs/content/features/rate-cards-budgets.mdx`

**Slice 435A -- Read for context:**
- `frontend/app/(app)/org/[slug]/documents/` -- document pages
- `frontend/components/documents/` -- document components
- `frontend/components/editor/` -- template editor
- `frontend/app/(app)/org/[slug]/proposals/` -- proposal pages
- `frontend/components/proposals/` -- proposal components
- `frontend/components/expenses/` -- expense components
- `frontend/components/rates/` -- rate card components
- `frontend/components/budget/` -- budget components
- `frontend/components/profitability/` -- profitability components
- `frontend/lib/messages/en/help.json` -- inline help text
- `frontend/lib/messages/en/empty-states.json` -- empty state text

**Slice 435B -- Create (overwrite placeholders):**
- `docs/content/features/reports.mdx`
- `docs/content/features/resource-planning.mdx`
- `docs/content/features/workflow-automations.mdx`
- `docs/content/features/customer-portal.mdx`

**Slice 435B -- Read for context:**
- `frontend/app/(app)/org/[slug]/reports/` -- reports pages
- `frontend/components/reports/` -- report components
- `frontend/app/(app)/org/[slug]/resources/` -- resource planning pages
- `frontend/components/capacity/` -- capacity components
- `frontend/components/automations/` -- automation components
- `frontend/components/portal/` -- portal-related components
- `frontend/lib/messages/en/help.json` -- inline help text
- `frontend/lib/messages/en/empty-states.json` -- empty state text

### Architecture Decisions

- **Two-slice decomposition by domain affinity**: 435A groups document-related and financial features (documents, proposals, expenses, rate-cards). 435B groups operational and external features (reports, resources, automations, portal). This minimizes redundant codebase reading within each slice.
- **Parallelizable with 433**: Both 435A and 435B can run in parallel with Epic 433 slices since they operate on different MDX files and read different codebase areas.

---

## Epic 436: P2 Features & Vertical Guides

**Goal**: Write the 3 remaining P2 feature guides (custom-fields-tags, information-requests, ai-assistant), 3 accounting-specific vertical guides (SARS deadlines, recurring engagements, compliance packs), and 3 legal stub articles (court-calendar, conflict-checks, LSSA tariff). This completes the full 30-article content inventory.

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.4.2, 11.4.3, 11.7; ADR-229

**Dependencies**: Epic 432 (doc site scaffold must exist)

**Scope**: Docs (Content)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **436A** | 436.1--436.3 | 3 P2 feature guides: custom-fields-tags, information-requests, ai-assistant. ~1,900 estimated words. | **Done** (PR #905) |
| **436B** | 436.4--436.9 | 3 accounting guides + 3 legal stubs. 6 articles, ~2,400 estimated words (stubs are ~200 words each). | **Done** (PR #906) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 436.1 | Write `features/custom-fields-tags.mdx` | 436A | | `docs/content/features/custom-fields-tags.mdx`. ~700 words. Cover: what custom fields are, field types (text, number, date, dropdown, checkbox), creating field definitions, field groups, auto-apply field groups, tags (creating, applying, filtering by), saved views with custom field columns, conditional field visibility. **Read for context**: `frontend/components/field-definitions/` (field definition components), `frontend/components/tags/` (tag components), `frontend/components/views/` (saved view components), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 436.2 | Write `features/information-requests.mdx` | 436A | | `docs/content/features/information-requests.mdx`. ~600 words. Cover: what information requests are, creating request templates, sending requests to customers via portal, customer upload workflow, tracking request status, linking requests to projects. **Read for context**: `frontend/components/information-requests/` (information request components), `frontend/app/(app)/org/[slug]/information-requests/` (information request pages), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 436.3 | Write `features/ai-assistant.mdx` | 436A | | `docs/content/features/ai-assistant.mdx`. ~600 words. Cover: what the AI assistant does, BYOAK setup (bringing your own Claude API key), what questions the assistant can answer, how it accesses org data (tool-calling), conversation history, limitations. **Read for context**: `frontend/components/assistant/` (assistant components), `frontend/components/integrations/` (BYOAK key setup), `frontend/lib/messages/en/help.json`, `frontend/lib/messages/en/empty-states.json`. |
| 436.4 | Write `verticals/accounting/sars-deadlines.mdx` | 436B | | `docs/content/verticals/accounting/sars-deadlines.mdx`. ~700 words. Cover: SARS deadline management for South African accounting firms, deadline types (IRP6, IRP5, IT12, VAT, PAYE), filing status tracking, deadline calendar view, automated reminders, linking deadlines to customers. Include "Terminology Note" section per architecture guidelines. **Read for context**: `frontend/components/deadlines/` (deadline components), `frontend/app/(app)/org/[slug]/deadlines/` (deadline pages), `frontend/lib/deadline-utils.ts` (deadline type definitions), `frontend/lib/messages/en/help.json`. |
| 436.5 | Write `verticals/accounting/recurring-engagements.mdx` | 436B | | `docs/content/verticals/accounting/recurring-engagements.mdx`. ~600 words. Cover: what recurring engagements are (annual audit, monthly bookkeeping), schedule configuration, automatic project creation from schedules, post-schedule actions, linking to project templates. Cross-link to `/features/projects` for general project management. **Read for context**: `frontend/components/schedules/` (schedule components), `frontend/app/(app)/org/[slug]/schedules/` (schedule pages), `frontend/lib/schedule-constants.ts` (schedule configuration), `frontend/lib/messages/en/help.json`. |
| 436.6 | Write `verticals/accounting/compliance-packs.mdx` | 436B | | `docs/content/verticals/accounting/compliance-packs.mdx`. ~500 words. Cover: what compliance packs are, pre-configured checklists for customer onboarding (FICA, IRBA, tax registrations), applying packs to customers, tracking completion, customizing pack contents. Cross-link to `/features/customers` for customer management. **Read for context**: `frontend/components/compliance/` (compliance components), `frontend/app/(app)/org/[slug]/compliance/` (compliance pages), `frontend/lib/compliance-api.ts` (compliance API), `frontend/lib/messages/en/help.json`. |
| 436.7 | Write `verticals/legal/court-calendar.mdx` (stub) | 436B | | `docs/content/verticals/legal/court-calendar.mdx`. ~200 words. Stub article using the legal stub template from architecture Section 11.4.2. Title: "Court Calendar (Coming Soon)". Include `<Callout type="info">` with coming-soon notice. "What's Planned" section describing court date tracking, prescription alerts, hearing reminders. "What's Available Today" section linking to generic project deadline features. **Read for context**: `architecture/phase59-user-help-documentation.md` Section 11.4.2 (stub template). |
| 436.8 | Write `verticals/legal/conflict-checks.mdx` (stub) | 436B | | `docs/content/verticals/legal/conflict-checks.mdx`. ~200 words. Stub article. Title: "Conflict Checks (Coming Soon)". "What's Planned": adverse party registry, automated conflict search, conflict report generation. "What's Available Today": link to customer management for manual conflict tracking. **Read for context**: `architecture/phase59-user-help-documentation.md` Section 11.4.2 (stub template). |
| 436.9 | Write `verticals/legal/lssa-tariff.mdx` (stub) | 436B | | `docs/content/verticals/legal/lssa-tariff.mdx`. ~200 words. Stub article. Title: "LSSA Tariff Billing (Coming Soon)". "What's Planned": LSSA tariff schedule lookup, tariff-based invoice line items, standard vs non-standard fee calculation. "What's Available Today": link to invoicing guide for manual fee entry. **Read for context**: `architecture/phase59-user-help-documentation.md` Section 11.4.2 (stub template). |

### Key Files

**Slice 436A -- Create (overwrite placeholders):**
- `docs/content/features/custom-fields-tags.mdx`
- `docs/content/features/information-requests.mdx`
- `docs/content/features/ai-assistant.mdx`

**Slice 436A -- Read for context:**
- `frontend/components/field-definitions/` -- field definition components
- `frontend/components/tags/` -- tag components
- `frontend/components/views/` -- saved view components
- `frontend/components/information-requests/` -- information request components
- `frontend/app/(app)/org/[slug]/information-requests/` -- information request pages
- `frontend/components/assistant/` -- AI assistant components
- `frontend/components/integrations/` -- BYOAK key setup
- `frontend/lib/messages/en/help.json` -- inline help text
- `frontend/lib/messages/en/empty-states.json` -- empty state text

**Slice 436B -- Create (overwrite placeholders):**
- `docs/content/verticals/accounting/sars-deadlines.mdx`
- `docs/content/verticals/accounting/recurring-engagements.mdx`
- `docs/content/verticals/accounting/compliance-packs.mdx`
- `docs/content/verticals/legal/court-calendar.mdx`
- `docs/content/verticals/legal/conflict-checks.mdx`
- `docs/content/verticals/legal/lssa-tariff.mdx`

**Slice 436B -- Read for context:**
- `frontend/components/deadlines/` -- deadline components
- `frontend/app/(app)/org/[slug]/deadlines/` -- deadline pages
- `frontend/lib/deadline-utils.ts` -- deadline type definitions
- `frontend/components/schedules/` -- schedule components
- `frontend/app/(app)/org/[slug]/schedules/` -- schedule pages
- `frontend/lib/schedule-constants.ts` -- schedule configuration
- `frontend/components/compliance/` -- compliance components
- `frontend/app/(app)/org/[slug]/compliance/` -- compliance pages
- `frontend/lib/compliance-api.ts` -- compliance API
- `frontend/lib/messages/en/help.json` -- inline help text
- `architecture/phase59-user-help-documentation.md` Section 11.4.2 -- stub template

### Architecture Decisions

- **Two-slice decomposition**: 436A groups the 3 P2 feature guides (unrelated to any vertical). 436B groups all 6 vertical articles (3 accounting + 3 legal stubs) because they share vertical-specific codebase context (deadline utils, schedule constants, compliance API).
- **6 articles in 436B**: This exceeds the 3-5 guideline by 1, but the 3 legal stubs are only ~200 words each (effectively a copy-paste of the stub template with different content). The total word count (~2,400) is comparable to a 3-article slice. The agent reads the stub template once and produces all 3 stubs with minimal additional codebase reading.
- **Stubs reference Phase 55**: The legal stub content describes features planned for Phase 55 (not yet built). The stubs link to existing generic features as workarounds. No Phase 55 codebase reading is needed.

---

## Epic 437: Frontend Contextual Deep-Links

**Goal**: Implement all frontend changes to wire contextual deep-links from the existing HeyKazi app to the documentation site. Create the `docsLink()` utility function. Extend the `HelpTip` component with an optional `docsPath` prop that renders a "Learn more" link. Update `EmptyState` to detect external URLs and render `<a target="_blank">` instead of `<Link>`. Add a "Help" item to the sidebar utility navigation. Wire ~12 EmptyState call sites and ~7 HelpTip instances with documentation links. Write Vitest tests for all component changes.

**References**: `architecture/phase59-user-help-documentation.md` Sections 11.5, 11.8.1, 11.8.2, 11.8.4

**Dependencies**: Epic 432 (doc site must exist for URL targeting; however, this slice can be developed in parallel -- links will resolve once content slices merge)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **437A** | 437.1--437.7 | `docsLink()` utility, HelpTip `docsPath` extension, EmptyState external URL detection, NavItem `external` flag, sidebar Help item, sidebar external link rendering (desktop + mobile), Vitest tests for all component changes | |
| **437B** | 437.8--437.10 | Wire ~12 EmptyState call sites with `secondaryLink` props, wire ~7 HelpTip instances with `docsPath` props, add `NEXT_PUBLIC_DOCS_URL` to frontend environment | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 437.1 | Create `frontend/lib/docs.ts` with `docsLink()` utility | 437A | | `frontend/lib/docs.ts`. Export `docsLink(path: string): string` function. Reads `NEXT_PUBLIC_DOCS_URL` env var (default: `"https://docs.heykazi.com"`). Concatenates base URL + path. See architecture Section 11.5.1 for exact code. |
| 437.2 | Extend `HelpTip` with optional `docsPath` prop | 437A | | Modify `frontend/components/help-tip.tsx`. Add `docsPath?: string` to `HelpTipProps` interface. When provided, render a "Learn more" link below the popover body text: `<a href={docsLink(docsPath)} target="_blank" rel="noopener noreferrer">` styled with `text-teal-600 hover:text-teal-700`. Import `ExternalLink` icon from `lucide-react` (size-3). Import `docsLink` from `@/lib/docs`. See architecture Section 11.5.2 for exact JSX. |
| 437.3 | Update `EmptyState` external URL detection | 437A | | Modify `frontend/components/empty-state.tsx`. In the `secondaryLink` rendering section: detect if `href.startsWith("http")`. If true, render `<a href={href} target="_blank" rel="noopener noreferrer">` instead of Next.js `<Link>`. This is a ~3-line conditional change. No `"use client"` directive change needed. See architecture Section 11.5.3 for details. |
| 437.4 | Add `external` flag to `NavItem` interface in `lib/nav-items.ts` | 437A | | Modify `frontend/lib/nav-items.ts`. Add `external?: boolean` to the `NavItem` interface (or type). Add Help item to `UTILITY_ITEMS` array: `{ label: "Help", href: () => "https://docs.heykazi.com", icon: BookOpen, external: true }`. Import `BookOpen` from `lucide-react`. See architecture Section 11.5.4 for exact code. Note: the `href` function returns a static URL (not org-scoped). |
| 437.5 | Update `desktop-sidebar.tsx` for external link rendering | 437A | | Modify `frontend/components/desktop-sidebar.tsx`. In the UTILITY_ITEMS rendering loop: when `item.external` is true, render `<a href={item.href(slug)} target="_blank" rel="noopener noreferrer">` instead of `<Link>`. Optionally add a small `ExternalLink` icon indicator (size-3, muted color). Pattern: check existing sidebar item rendering code to determine exact insertion point. |
| 437.6 | Update `mobile-sidebar.tsx` for external link rendering | 437A | | Modify `frontend/components/mobile-sidebar.tsx`. Same external link handling as desktop sidebar. When `item.external` is true, render `<a target="_blank">` instead of `<Link>`. Pattern: match the desktop sidebar implementation from 437.5. |
| 437.7 | Write Vitest tests for docsLink, HelpTip, EmptyState, and sidebar | 437A | | Create `frontend/__tests__/docs-integration.test.tsx`. Tests: (1) `docsLink("/features/invoicing")` returns `https://docs.heykazi.com/features/invoicing`, (2) `docsLink` uses custom `NEXT_PUBLIC_DOCS_URL` when set, (3) HelpTip renders "Learn more" link when `docsPath` is provided, (4) HelpTip does NOT render "Learn more" when `docsPath` is omitted, (5) EmptyState renders `<a target="_blank">` for external `secondaryLink.href`, (6) EmptyState renders `<Link>` for internal `secondaryLink.href`, (7) UTILITY_ITEMS contains Help item with `external: true`. Pattern: follow existing test patterns in `frontend/__tests__/`. Use `afterEach(() => cleanup())` if using Radix/Shadcn popover components. |
| 437.8 | Wire ~12 EmptyState call sites with documentation links | 437B | | Add `secondaryLink={{ label: "Read the guide", href: docsLink("/path") }}` to EmptyState instances across the app. Import `docsLink` from `@/lib/docs` at each call site. Full mapping per architecture Section 11.5.5: (1) projects list empty -> `/features/projects`, (2) customers list empty -> `/features/customers`, (3) tasks list empty -> `/features/tasks`, (4) time entries empty -> `/features/time-tracking`, (5) invoices list empty -> `/features/invoicing`, (6) documents list empty -> `/features/documents`, (7) proposals list empty -> `/features/proposals`, (8) reports page -> `/features/reports`, (9) automations empty -> `/features/workflow-automations`, (10) information requests empty -> `/features/information-requests`, (11) resource planning empty -> `/features/resource-planning`, (12) AI assistant first use -> `/features/ai-assistant`. Each is a 3-4 line prop addition. |
| 437.9 | Wire ~7 HelpTip instances with documentation links | 437B | | Add `docsPath="/path"` prop to existing HelpTip instances. Full mapping per architecture Section 11.5.5: (1) rate cards settings -> `/features/rate-cards-budgets`, (2) custom fields settings -> `/features/custom-fields-tags`, (3) team settings page -> `/admin/team-permissions`, (4) org settings page -> `/admin/org-settings`, (5) integrations settings -> `/admin/integrations`, (6) billing page -> `/admin/billing`, (7) getting started checklist -> `/getting-started/quick-setup`, (8) customer portal contacts -> `/features/customer-portal`. Each is a single prop addition to an existing `<HelpTip>` JSX element. |
| 437.10 | Add `NEXT_PUBLIC_DOCS_URL` to frontend environment | 437B | | Add `NEXT_PUBLIC_DOCS_URL` to `frontend/.env.local` (for local dev: `http://localhost:3003`). Document the variable in any relevant `.env.example` or README. For production deployment on Vercel, the variable should be set to `https://docs.heykazi.com` (or left unset to use the default). |

### Key Files

**Slice 437A -- Create:**
- `frontend/lib/docs.ts`
- `frontend/__tests__/docs-integration.test.tsx`

**Slice 437A -- Modify:**
- `frontend/components/help-tip.tsx` -- add `docsPath` prop and "Learn more" link
- `frontend/components/empty-state.tsx` -- external URL detection for `secondaryLink`
- `frontend/lib/nav-items.ts` -- `external` flag on `NavItem`, Help item in `UTILITY_ITEMS`
- `frontend/components/desktop-sidebar.tsx` -- external link rendering
- `frontend/components/mobile-sidebar.tsx` -- external link rendering

**Slice 437A -- Read for context:**
- `frontend/components/help-tip.tsx` -- current HelpTip implementation
- `frontend/components/empty-state.tsx` -- current EmptyState implementation
- `frontend/lib/nav-items.ts` -- current NavItem interface and UTILITY_ITEMS array
- `frontend/components/desktop-sidebar.tsx` -- current sidebar item rendering
- `frontend/components/mobile-sidebar.tsx` -- current mobile sidebar rendering
- `frontend/__tests__/` -- existing test patterns

**Slice 437B -- Modify:**
- ~12 page files containing EmptyState instances (projects/page.tsx, customers/page.tsx, invoices/page.tsx, documents pages, proposals pages, reports pages, tasks pages, time-tracking pages, automations pages, information-requests pages, resources pages, assistant pages)
- ~7 page/component files containing HelpTip instances (rate card settings, custom field settings, team page, org settings, integrations settings, billing page, getting-started checklist)
- `frontend/.env.local` -- add `NEXT_PUBLIC_DOCS_URL`

**Slice 437B -- Read for context:**
- `architecture/phase59-user-help-documentation.md` Section 11.5.5 -- full link mapping table
- Each call-site file to locate the exact `<EmptyState>` or `<HelpTip>` JSX element

### Architecture Decisions

- **Two-slice decomposition**: 437A covers all component and utility changes (6 files modified, 2 files created). 437B covers the ~19 call-site wiring changes across the app (each a small prop addition). This separation ensures the reusable infrastructure is tested and stable before mass-wiring.
- **External link detection in EmptyState**: Uses `href.startsWith("http")` as the heuristic. This is simple and sufficient -- all doc site links are absolute URLs, all internal links are relative paths. No need for a more complex URL parsing approach.
- **Sidebar Help item**: The Help link is added to `UTILITY_ITEMS` (not `MAIN_ITEMS`) because it is a utility/meta navigation item alongside Notifications and Settings. The `external: true` flag is a new concept for the sidebar renderer -- both desktop and mobile sidebar must handle it.
- **No build-time link validation**: Links from the main app to the doc site are not validated at build time because the two projects have independent builds. If a doc page is renamed, the link will 404. Mitigation: use stable URL paths that map to feature domains (e.g., `/features/invoicing`) and avoid renaming. A future Playwright E2E test could verify link integrity.
- **Tests in Slice 437A**: All Vitest tests ship in the same slice as the component changes they test. This follows the project convention where tests belong with the code they verify.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase59-user-help-documentation.md`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/help-tip.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/empty-state.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/nav-items.ts`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/messages/en/help.json`
