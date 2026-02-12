# Phase 3 — Frontend Design Overhaul

Phase 3 replaces the default Shadcn/neutral styling with a distinctive design language inspired by two complementary templates: **Oatmeal** (warm editorial personality for public-facing surfaces) and **Catalyst** (clean data-focused precision for the app shell). See `DESIGN.md` for the full specification including color tokens, typography, component specs, and page-by-page mockup descriptions.

**Key design changes:**
- **Typography**: Instrument Serif for display headings, Inter for body/UI (replacing Geist Sans)
- **Color system**: Custom olive OKLCH scale replacing neutral grays, indigo accent for interactive elements
- **Component styling**: Pill-shaped primary buttons, minimal shadows (border-driven), olive-950 dark sidebar
- **Motion**: Framer Motion for sidebar indicators, tab underlines, dialog animations, page transitions

---

### Epic 30: Design Foundation

**Goal**: Establish the new design token system, replace fonts, install Motion, and restyle all base Shadcn components. This is the foundation that all subsequent design epics build on — no page-level changes, only primitives.

**References**: DESIGN.md §1 (Global Design Tokens), §12 (Component Specification)

**Dependencies**: None (builds on existing frontend)

**Scope**: Frontend

**Estimated Effort**: M

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **30A** | 30.1–30.4 | Design tokens: olive color scale, semantic token remapping, fonts, Motion install | Done (PR #60) |
| **30B** | 30.5–30.7 | Component restyling: Button, Badge, Card, Input, Dialog base updates | Done (PR #61) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 30.1 | Replace color system in globals.css with olive OKLCH scale | 30A | Done | **Light mode `:root`**: Replace all neutral `oklch(x 0 0)` values with olive-tinted equivalents from DESIGN.md §1. Key mappings: `--background` → olive-50 `oklch(98.8% 0.003 106.5)`, `--foreground` → olive-900 `oklch(22.8% 0.013 107.4)`, `--primary` → olive-950 `oklch(15.3% 0.006 107.1)`, `--muted` → olive-100 `oklch(96.6% 0.005 106.5)`, `--muted-foreground` → olive-600 `oklch(46.6% 0.025 107.3)`, `--border` → olive-200 `oklch(93% 0.007 106.5)`, `--accent` → indigo-600 `oklch(51.1% 0.262 276.9)`. Add full olive scale as custom properties (`--olive-50` through `--olive-950`) for direct use. Add `--indigo-500` and `--indigo-600`. **Dark mode `.dark`**: Invert appropriately — `--background` → olive-950, `--foreground` → white, `--card` → olive-900, `--border` → olive-800, `--muted` → olive-900, `--muted-foreground` → olive-400. **Sidebar vars**: `--sidebar` → olive-950, `--sidebar-foreground` → white for both light and dark. Add olive color utilities in `@theme inline` block for Tailwind classes (`--color-olive-50` through `--color-olive-950`, `--color-indigo-500`, `--color-indigo-600`). Update `--radius` to `0.5rem` (rounded-lg base). |
| 30.2 | Replace Geist Sans/Mono with Instrument Serif + Inter + Geist Mono | 30A | Done | In `app/layout.tsx`: import `Instrument_Serif` and `Inter` from `next/font/google` (replacing `Geist`). Configure: `Instrument_Serif` — weight `400`, italic `true`, variable `--font-display`, subsets `["latin"]`. `Inter` — variable `--font-sans`, subsets `["latin"]`. Keep `Geist_Mono` as `--font-mono`. Update `<body>` className to include all three variables. In `globals.css` `@theme inline`: update `--font-sans` to `var(--font-sans)` (Inter), add `--font-display: var(--font-display)` (Instrument Serif). Add utility class `.font-display` targeting Instrument Serif for headings. |
| 30.3 | Install Motion (Framer Motion) package | 30A | Done | Run `pnpm add motion`. This provides the `motion` import used for sidebar indicators (Epic 31), tab underlines (Epic 33), dialog animations (Epic 35), and page transitions (Epic 36). No code changes — just dependency installation. Verify package resolves correctly with `pnpm ls motion`. |
| 30.4 | Add Tailwind v4 theme extensions for new design tokens | 30A | Done | In `globals.css` `@theme inline` block: add `--color-olive-*` entries mapping to the olive custom properties from 30.1. Add `--color-indigo-500` and `--color-indigo-600`. Add `--color-accent-foreground: var(--accent-foreground)`. This enables Tailwind classes like `bg-olive-950`, `text-olive-600`, `border-olive-200`, `bg-indigo-600` etc. throughout the codebase. |
| 30.5 | Restyle Button component with new variants | 30B | Done | Update `components/ui/button.tsx` CVA variants per DESIGN.md §12.1. **Changes**: (1) `default` variant: `bg-primary text-primary-foreground` + `rounded-full` (pill shape). (2) Add `soft` variant: `bg-olive-950/10 text-olive-950 hover:bg-olive-950/15 rounded-full`. (3) `outline` variant: keep `rounded-md`, update border to `border-olive-200`. (4) `ghost` variant: `text-olive-700 hover:bg-olive-100 rounded-md`. (5) Add `plain` variant: `text-olive-700 hover:text-olive-950 p-0 h-auto` (no background, no padding). (6) `destructive` variant: `bg-red-600 text-white rounded-full`. (7) Add `accent` variant: `bg-indigo-600 text-white hover:bg-indigo-700 rounded-full`. (8) Size `lg`: `px-5 py-2.5 text-base h-10`. Keep `sm` and `md` sizes. |
| 30.6 | Restyle Badge component with semantic variants | 30B | Done | Update `components/ui/badge.tsx` CVA variants per DESIGN.md §12.2. Replace existing variants with: `lead` → `bg-indigo-100 text-indigo-700`, `member` → `bg-olive-100 text-olive-700`, `owner` → `bg-amber-100 text-amber-700`, `admin` → `bg-slate-100 text-slate-700`, `starter` → `bg-olive-100 text-olive-700`, `pro` → `bg-indigo-100 text-indigo-700`, `success` → `bg-green-100 text-green-700`, `warning` → `bg-amber-100 text-amber-700`, `destructive` → `bg-red-100 text-red-700`, `neutral` → `bg-olive-100 text-olive-600`. All badges: `rounded-full text-xs px-2.5 py-0.5 font-medium`. Keep `default` as alias for `neutral`. Keep `secondary` and `outline` as backward-compatible aliases. |
| 30.7 | Restyle Card, Input, and Dialog base components | 30B | Done | **Card** (`components/ui/card.tsx`): Update border to `border-olive-200 dark:border-olive-800`. Remove default shadow (`shadow-sm` → none). Add hover utility class: `group-hover:shadow-sm group-hover:border-olive-300 transition-all duration-150` on CardContent or via a new `interactive` variant. Radius: `rounded-lg`. **Input** (`components/ui/input.tsx`): Border `border-olive-200`, focus ring `focus-visible:ring-olive-500` (replacing default blue), placeholder `placeholder:text-olive-400`. **Dialog** (`components/ui/dialog.tsx`): Overlay backdrop → `bg-olive-950/25` (replacing `bg-black/50`). Content → `rounded-xl shadow-lg`. **Textarea**: Same border/focus treatment as Input. |

#### Key Files

**Modify:**
- `frontend/app/globals.css` — Olive color scale, semantic tokens, font variables, radius
- `frontend/app/layout.tsx` — Replace Geist with Instrument Serif + Inter fonts
- `frontend/components/ui/button.tsx` — Pill shape, new variants (soft, plain, accent)
- `frontend/components/ui/badge.tsx` — Semantic role/plan variants
- `frontend/components/ui/card.tsx` — Olive borders, no shadow, hover states
- `frontend/components/ui/input.tsx` — Olive border, olive focus ring
- `frontend/components/ui/dialog.tsx` — Warm backdrop, rounded-xl
- `frontend/components/ui/textarea.tsx` — Matching Input styling

**Install:**
- `motion` — Framer Motion (via `pnpm add motion`)

---

### Epic 31: App Shell Redesign

**Goal**: Transform the authenticated app shell (sidebar, header, content area) from the default Shadcn layout into a Catalyst-inspired dark sidebar with Motion-animated indicators and a cleaner header with breadcrumbs.

**References**: DESIGN.md §4 (App Shell)

**Dependencies**: Epic 30

**Scope**: Frontend

**Estimated Effort**: M

#### Tasks

| ID | Task | Status | Notes |
|----|------|------|-------|
| 31.1 | Redesign DesktopSidebar with dark olive-950 styling and Motion indicator | Done | Rewrite `components/desktop-sidebar.tsx`. **Background**: `bg-olive-950` (always dark, both light and dark mode). **Width**: `w-60` (240px, unchanged). **Structure**: (1) **Header**: DocTeams logo text in white + org name (truncated, `text-white/60`). (2) **Nav body**: items with `text-white/60` default, `text-white` on hover with `bg-olive-800` background, active state: `text-white bg-white/5`. (3) **Active indicator**: 2px-wide left bar using Motion `layoutId` animation (`bg-indigo-500`, spring transition, animates position on route change). (4) **Dividers**: `border-white/10` separators between header/nav/footer. (5) **Footer**: User avatar (round, 32px, initials) + user name + email (truncated, `text-white/60`). Use Clerk `useUser()` hook for user data. Import `motion` from `motion/react` for the animated indicator (`<motion.div layoutId="sidebar-indicator" />`). Icon size: `h-4 w-4`. |
| 31.2 | Redesign MobileSidebar with matching dark styling | Done | Update `components/mobile-sidebar.tsx`. Sheet content should match desktop sidebar: `bg-olive-950` background, white text, same nav item styling and active indicator. Sheet backdrop: update to `bg-olive-950/25`. Trigger button: hamburger icon, `md:hidden`. Sheet slides from left. Same footer section as desktop. Close sheet on nav link click (existing behavior). |
| 31.3 | Redesign header bar | Done | Update `app/(app)/org/[slug]/layout.tsx` header section. **Height**: `h-14` (unchanged). **Background**: `bg-white dark:bg-olive-950`. **Border**: `border-b border-olive-200 dark:border-olive-800`. **Left side**: Mobile hamburger (`md:hidden`) + breadcrumbs component (org name > current page name). Create a simple `Breadcrumbs` component that reads the pathname and renders org name + current section (Dashboard/Projects/Team/Settings). Use olive-400 separator and olive-600 text for non-active crumbs. **Right side**: `OrganizationSwitcher` + `PlanBadge` + `UserButton` (existing Clerk components). |
| 31.4 | Update content area styling | Done | In `app/(app)/org/[slug]/layout.tsx`: update `<main>` to `bg-olive-50 dark:bg-olive-950`. Add `max-w-7xl mx-auto` container. Increase padding: `px-6 lg:px-10 py-6`. This gives the content area a warm tinted background distinct from the white cards rendered inside it. |
| 31.5 | Create Breadcrumbs component | Done | Create `components/breadcrumbs.tsx` — client component that reads `usePathname()` and renders: org slug (as link to dashboard) + separator (ChevronRight icon, olive-400) + current page name. Map path segments to display names: `dashboard` → "Dashboard", `projects` → "Projects", `team` → "Team", `settings` → "Settings". For nested routes like `/projects/[id]`, show "Projects" as link + project name (or "Project" as fallback). Text styling: links in `text-olive-600 hover:text-olive-900`, current page in `text-olive-900 font-medium`. |

#### Key Files

**Modify:**
- `frontend/components/desktop-sidebar.tsx` — Full rewrite with dark olive-950 + Motion indicator
- `frontend/components/mobile-sidebar.tsx` — Dark styling matching desktop
- `frontend/app/(app)/org/[slug]/layout.tsx` — Header redesign + content area styling

**Create:**
- `frontend/components/breadcrumbs.tsx` — Breadcrumb navigation component

---

### Epic 32: Landing Page

**Goal**: Replace the minimal placeholder landing page with a full editorial marketing page featuring hero, logo bar, features, stats, pricing preview, testimonials, CTA, and footer sections. Oatmeal-inspired aesthetic with Instrument Serif typography and olive color palette.

**References**: DESIGN.md §2 (Landing Page)

**Dependencies**: Epic 30

**Scope**: Frontend

**Estimated Effort**: L

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **32A** | 32.1–32.3, 32.8 | Page structure: Hero + Logo bar + CTA + Footer | Done (PR #63) |
| **32B** | 32.4–32.7 | Feature sections: Features + Stats + Pricing + Testimonials | Done (PR #64) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 32.1 | Create Hero section | 32A | Done | Replace entire `app/page.tsx`. **Layout**: Left-aligned text (60%) + app screenshot placeholder (40%) on desktop, stacked on mobile. **Eyebrow**: `AnnouncementBadge` component — small pill with "New: Pro plan with dedicated infrastructure" + arrow-right icon, link to `/sign-up`. Background `bg-indigo-50 text-indigo-700`. **Headline**: Instrument Serif (`font-display`), `text-5xl sm:text-[5rem] leading-tight`, olive-950, `text-balance`. Copy: "Document collaboration for modern teams". **Subtitle**: Inter, `text-lg leading-8`, olive-700. 2–3 sentence value prop. **CTAs**: Two pill buttons — "Get Started" (`<Link href="/sign-up">`, solid olive-950/white), "Sign In" (`<Link href="/sign-in">`, soft variant `bg-olive-950/10`). **Screenshot area**: Placeholder div with `bg-olive-200 rounded-lg aspect-video` or actual app screenshot image. **Background**: `bg-olive-50`. |
| 32.2 | Create Logo bar section | 32A | Done | **Layout**: Centered, responsive grid (`grid-cols-3 md:grid-cols-6`). **Label**: "Trusted by teams at" — `text-olive-400 text-sm uppercase tracking-widest` centered. **Logos**: 6 placeholder company SVGs or text logos in `text-olive-400` monochrome treatment. Use generic placeholder names (e.g., "Acme", "Globex", "Initech", etc.) rendered as styled text if no SVGs. **Separators**: `border-t border-olive-200` above and below the section. **Spacing**: `py-16`. |
| 32.3 | Create CTA section | 32A | Done | **Layout**: Centered, `py-24` generous vertical padding. **Heading**: Instrument Serif, `text-3xl sm:text-4xl`, olive-950: "Ready to get started?". **Subtitle**: Inter, `text-lg`, olive-700: "Create your workspace in seconds. No credit card required." **Button**: "Create your workspace" — solid pill button (olive-950), links to `/sign-up`. **Background**: `bg-white` (subtle contrast from olive-50 sections). |
| 32.4 | Create Features section | 32B | Done | **Layout**: Two-column alternating (text left/screenshot right for odd, swap for even). Stacked on mobile. Three features, each with: **Icon** (Lucide icon in `text-olive-600`, 24px), **Heading** (Instrument Serif, `text-2xl`), **Description** (Inter, `text-base leading-7`, olive-700, 2–3 sentences), **Screenshot placeholder** (`bg-olive-100 rounded-lg aspect-video`). **Features**: (1) FileText icon — "Document Management" / "Upload, organize, and share documents across your team." (2) FolderKanban icon — "Project Collaboration" / "Assign roles, manage members, track project progress." (3) Shield icon — "Enterprise Isolation" / "Dedicated database schemas for Pro teams, shared infrastructure for starters." **Spacing**: `py-24`, `gap-16` between features. |
| 32.5 | Create Stats section | 32B | Done | **Layout**: 3-column centered grid (`grid-cols-1 sm:grid-cols-3`). **Numbers**: Instrument Serif, `text-4xl sm:text-5xl font-normal`, olive-950. **Labels**: Inter, `text-sm`, olive-600, below each number. **Stats**: "10,000+" / "Documents managed" — "500+" / "Active teams" — "99.9%" / "Uptime". **Background**: `bg-olive-100` strip with `py-16`. |
| 32.6 | Create Pricing Preview section | 32B | Done | **Layout**: 2-column card grid (`grid-cols-1 md:grid-cols-2`, `gap-8`), centered `max-w-4xl`. **Starter card**: `bg-olive-950/[0.025]` background, rounded-lg. "Starter" label (Inter semibold), "Free" price (Instrument Serif, `text-3xl`). Feature list with Check icons (`text-olive-500`): "2 team members", "Shared infrastructure", "Row-level data isolation", "Community support". "Get Started" outline button → `/sign-up`. **Pro card**: Same background + `border-2 border-indigo-200` highlight. "Most popular" badge (indigo pill, positioned top-right). "Pro" label, "$29/month" price (Instrument Serif, `text-3xl`). Feature list with Check icons (`text-indigo-500`): "10 team members", "Dedicated infrastructure", "Schema-level data isolation", "Priority support". "Get Started" solid accent button (indigo) → `/sign-up`. **Section heading**: Instrument Serif `text-3xl` centered "Simple, transparent pricing". |
| 32.7 | Create Testimonials section | 32B | Done | **Layout**: 3-column grid (`grid-cols-1 md:grid-cols-3`, `gap-6`), stacked on mobile. Three testimonial cards, each: `bg-olive-950/[0.025]` background, `rounded-lg`, `p-6`. **Auto opening quote**: `"` character in `text-olive-300 text-4xl font-serif` positioned top. **Quote text**: Inter, `text-olive-800`, 2–3 sentences. **Divider**: `border-t border-olive-200 mt-4 pt-4`. **Author**: Avatar circle (48px, `bg-olive-200 text-olive-600` with initials) + Name (`font-semibold`) + Title/Company (`text-olive-600 text-sm`). Use placeholder testimonials with realistic B2B SaaS quotes. |
| 32.8 | Create Footer | 32A | Done | **Background**: `bg-olive-950`, full-width. **Layout**: `max-w-7xl mx-auto`, `py-12 px-6`. **Top row**: DocTeams logo (white text, `font-display text-xl`) on left. **Link groups** in grid (`grid-cols-2 md:grid-cols-3`, `gap-8`): "Product" (Features, Pricing, Documentation), "Company" (About, Blog, Careers), "Legal" (Privacy, Terms, Security). Link text: `text-white/60 hover:text-white text-sm`. Group headings: `text-white font-medium text-sm mb-3`. **Bottom row**: `border-t border-white/10 mt-8 pt-8`. Copyright: "© 2026 DocTeams. All rights reserved." in `text-white/40 text-sm`. |

#### Key Files

**Modify:**
- `frontend/app/page.tsx` — Full rewrite with all marketing sections

**Create:**
- `frontend/components/marketing/hero-section.tsx` — Hero with headline, CTAs, screenshot
- `frontend/components/marketing/logo-bar.tsx` — Trusted-by logos
- `frontend/components/marketing/features-section.tsx` — Alternating feature blocks
- `frontend/components/marketing/stats-section.tsx` — Stats strip
- `frontend/components/marketing/pricing-preview.tsx` — Starter/Pro cards
- `frontend/components/marketing/testimonials-section.tsx` — Quote cards
- `frontend/components/marketing/cta-section.tsx` — Bottom CTA
- `frontend/components/marketing/footer.tsx` — Dark footer with links

---

### Epic 33: Core App Pages Redesign

**Goal**: Redesign the three most-used authenticated pages — Dashboard, Projects list, and Project detail — with Instrument Serif headings, olive-toned styling, Catalyst-inspired tables, and Motion-animated tabs.

**References**: DESIGN.md §5 (Dashboard), §6 (Projects List), §7 (Project Detail)

**Dependencies**: Epic 30, Epic 31

**Scope**: Frontend

**Estimated Effort**: L

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **33A** | 33.1–33.3 | Dashboard: stat cards, quick actions, recent projects table, activity feed | **Done** (PR #65) |
| **33B** | 33.4–33.8 | Projects: upgraded cards, project detail tabs, Catalyst tables | **Done** (PR #66) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 33.1 | Redesign Dashboard page header and stat cards | 33A | **Done** | Update `app/(app)/org/[slug]/dashboard/page.tsx`. **Page header**: Instrument Serif h1 `font-display text-3xl`: "Dashboard". Subtitle: org name in `text-olive-600`. **Stat cards**: 4-column responsive grid (`grid-cols-2 lg:grid-cols-4`, `gap-6`). Each card: white background, `border border-olive-200 rounded-lg p-6`. Number in Instrument Serif `font-display text-3xl text-olive-950`. Label in Inter `text-sm text-olive-600`. Stats: Projects count, Documents count, Team Members count, Storage Used (placeholder). Use existing API data where available. |
| 33.2 | Redesign Dashboard quick actions and recent projects | 33A | **Done** | **Quick actions**: Horizontal row of pill buttons below stats. "New Project" — solid primary (olive-950, links to create project). "Invite Member" — soft variant (`bg-olive-950/10`). "Upload Document" — soft variant. Use `<Link>` components to relevant pages. **Recent projects**: Replace card grid with Catalyst-style table. Columns: Name (link, `font-medium`), Lead (member name), Documents (count), Last Updated (relative date). Row hover: `hover:bg-olive-50`. Table header: `text-olive-600 text-xs uppercase tracking-wide border-b border-olive-200`. Limit to 5 most recent. "View all projects →" link below in `text-olive-600 hover:text-olive-900 text-sm`. |
| 33.3 | Add Dashboard activity feed section | 33A | **Done** | Below recent projects (or right column on `xl` screens, using `xl:grid-cols-[1fr_320px]` layout). **Activity feed**: Simple vertical timeline with left border (`border-l-2 border-olive-200`). Each entry: relative timestamp in `text-olive-400 text-xs` + description text + actor name in `font-medium`. Use placeholder data initially: "Alice uploaded document.pdf", "Bob joined Project X", "Carol created Project Y". Style as `pl-4 py-2` entries along the border. Heading: "Recent Activity" in Inter `font-semibold text-olive-900`. Wrap in a card with `border border-olive-200 rounded-lg p-6`. |
| 33.4 | Redesign Projects list page with upgraded cards | 33B | **Done** | Update `app/(app)/org/[slug]/projects/page.tsx`. **Page header**: Instrument Serif h1 `font-display text-3xl`: "Projects". Subtitle: project count in `text-olive-600`. Right-aligned: "New Project" pill button (solid olive-950). **Upgrade prompt** (Starter only): Full-width card above grid with `bg-olive-100 border-l-4 border-indigo-500 rounded-lg p-4`. Text: "Upgrade to Pro for dedicated infrastructure and schema isolation". "Learn more" link → billing page. Dismiss button (X icon). **Card grid**: `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6`. Each card per DESIGN.md §6.3: white background, `border border-olive-200 rounded-lg`. Top: project name (`font-semibold text-olive-950`) + role badge (Lead: indigo, Member: olive). Middle: description `text-olive-600 line-clamp-2`. Bottom: `text-olive-400 text-sm` — document count icon + member count icon + creation date. Hover: `hover:border-olive-300 hover:shadow-sm transition-all duration-150`. |
| 33.5 | Add empty state for Projects list | 33B | **Done** | When project list is empty, show: Large Lucide `FolderOpen` icon (`text-olive-300`, 64px/`w-16 h-16`). Instrument Serif heading `font-display text-xl`: "No projects yet". Subtitle: "Create your first project to get started" in `text-olive-600`. "New Project" pill button (solid). Center all content vertically and horizontally with `py-24`. |
| 33.6 | Redesign Project detail header | 33B | **Done** | Update `app/(app)/org/[slug]/projects/[id]/page.tsx`. **Header section**: Project name in Instrument Serif `font-display text-2xl`. Role badge (pill) next to name. Description in Inter `text-olive-600` below. **Meta line**: `text-olive-400 text-sm` — "Created {date} · {n} documents · {n} members" separated by middle dots. **Actions** (right-aligned): "Edit" outline button (visible to admin/lead) + "Delete" ghost destructive button (visible to owner). |
| 33.7 | Add Motion-animated tabs to Project detail | 33B | **Done** | Replace the existing stacked Documents/Members panels with a tabbed layout. Create `components/projects/project-tabs.tsx` — client component with two tabs: "Documents" and "Members". Use Shadcn Tabs as the base but add a Motion-animated underline indicator: `<motion.div layoutId="project-tab-indicator" className="absolute bottom-0 h-0.5 bg-indigo-500" />` that slides between tabs. Tab text: `text-olive-600` default, `text-olive-950 font-medium` active. Tab bar has `border-b border-olive-200`. Content renders below. |
| 33.8 | Apply Catalyst table patterns to Documents and Members tabs | 33B | **Done** | **Documents table**: Restyle existing documents panel as a Catalyst table per DESIGN.md §7.3. Columns: Name (flex, file icon + filename truncated), Size (100px, formatted), Status (100px, badge — success/warning/destructive), Uploaded (140px, relative date), Actions (60px, download icon button). Header: `text-olive-600 text-xs uppercase tracking-wide`. Row hover: `hover:bg-olive-50`. Empty state: "No documents yet. Upload your first file above." **Members table**: Restyle per DESIGN.md §7.4. Columns: Member (flex, avatar circle with initials 32px + name + email below), Role (100px, badge), Added (140px, date), Actions (60px, kebab dropdown — only for project leads). Row hover: `hover:bg-olive-50`. Header row: "Members" label + count badge + "Add Member" outline button (right-aligned). |

#### Key Files

**Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — Stat cards, quick actions, recent table, activity feed
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Card grid redesign, empty state, upgrade prompt
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Header, tabbed layout, Catalyst tables
- `frontend/components/documents/documents-panel.tsx` — Catalyst table styling
- `frontend/components/projects/project-members-panel.tsx` — Catalyst table styling

**Create:**
- `frontend/components/projects/project-tabs.tsx` — Motion-animated tab component

---

### Epic 34: Supporting Pages Redesign

**Goal**: Redesign the Team, Settings, and Billing pages with olive-toned styling, Catalyst table patterns, and improved visual hierarchy.

**References**: DESIGN.md §8 (Team), §9 (Settings), §10 (Billing)

**Dependencies**: Epic 30, Epic 31

**Scope**: Frontend

**Estimated Effort**: M

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **34A** | 34.1–34.3 | Team page: header, invite section, member and invitation tables | Done (PR #67) |
| **34B** | 34.4–34.5 | Settings hub and Billing page redesign | Done (PR #68) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 34.1 | Redesign Team page header and invite section | 34A | Done | Update `app/(app)/org/[slug]/team/page.tsx`. **Page header**: Instrument Serif h1 `font-display text-3xl`: "Team". Member count badge (`bg-olive-200 text-olive-700 rounded-full text-sm px-2.5 py-0.5`). **Invite section** (admin only): Card at top, `bg-olive-50 border border-olive-200 rounded-lg p-6`. Heading: "Invite a team member" (Inter `font-semibold`). Form: email input + role select + "Send Invite" pill button (solid), inline on desktop, stacked on mobile. **Plan limit indicator**: Progress bar below form. Label: "3 of 10 members" (`text-sm text-olive-600`). Bar fill: `bg-olive-500` for Starter, `bg-indigo-500` for Pro. At limit: input disabled, message "Member limit reached" + "Upgrade" link in indigo. |
| 34.2 | Redesign Team member and invitation tables | 34A | Done | **Tabs**: "Members" / "Pending Invitations" with Motion-animated underline (same pattern as project tabs — `motion.div layoutId="team-tab-indicator"`, `h-0.5 bg-indigo-500`). **Members table** per DESIGN.md §8.4: Columns — Member (avatar circle 32px with initials + full name), Email (200px, `text-olive-600`), Role (100px, badge: Owner amber, Admin slate, Member olive), Joined (140px, formatted date). Row hover: `hover:bg-olive-50`. "Load more" text button at bottom. **Pending invitations table** per DESIGN.md §8.5: Columns — Email (flex), Role (100px, badge), Invited (140px, date), Actions (80px, "Revoke" ghost destructive button). Previous/Next pagination at bottom. |
| 34.3 | Add avatar circle utility component | 34A | Done | Create `components/ui/avatar-circle.tsx` — simple component that renders a colored circle (32px default) with user initials. Props: `name` (string), `size` (number, default 32), `className` (optional). Background: derive from name hash to pick from a set of olive/muted colors (`bg-olive-200 text-olive-700`, `bg-indigo-100 text-indigo-700`, `bg-amber-100 text-amber-700`). Used by Team tables, Members tab, sidebar footer. |
| 34.4 | Redesign Settings hub page | 34B | Done | Update `app/(app)/org/[slug]/settings/page.tsx`. **Page header**: Instrument Serif h1 `font-display text-3xl`: "Settings". **Category cards**: 2-column grid (`grid-cols-1 md:grid-cols-2`, `gap-6`). Each card: white background, `border border-olive-200 rounded-lg p-6`. Left: icon in `bg-olive-100 rounded-full p-3` circle (48px). Center: title (`font-semibold`) + description (`text-olive-600 text-sm`). Right: `ChevronRight` icon (`text-olive-400`). Hover: `hover:border-olive-300 hover:shadow-sm transition-all`. Click: navigate. **Cards**: (1) CreditCard icon — "Billing" / "Manage your subscription and view usage" → `/settings/billing` (active). (2) Building2 icon — "Organization" / "Update org name, logo, and details" (coming soon). (3) Shield icon — "Security" / "Configure authentication and access policies" (coming soon). (4) Puzzle icon — "Integrations" / "Connect third-party tools and services" (coming soon). Coming soon cards: `opacity-50 cursor-not-allowed` with "Coming soon" badge (`bg-olive-200 text-olive-600 text-xs rounded-full px-2 py-0.5`). |
| 34.5 | Redesign Billing page | 34B | Done | Update `app/(app)/org/[slug]/settings/billing/page.tsx`. **Page header**: Instrument Serif h1 `font-display text-3xl`: "Billing". Back link: `ChevronLeft` + "Settings" in `text-olive-600 hover:text-olive-900 text-sm`. **Current plan card**: Full-width, `border border-olive-200 rounded-lg p-6`. Plan name in Instrument Serif `font-display text-xl` + plan badge (Starter: olive, Pro: indigo). Description line in `text-olive-600`. Usage section: "Members" label + "3 of 10" count. Progress bar with fill color matching plan. **Plan comparison** (Starter only): Same styling as landing page pricing cards (reuse `pricing-preview.tsx` or extract shared `PlanCard` component). Starter card with "Current plan" badge. Pro card with "Upgrade to Pro" accent button → triggers existing `UpgradeConfirmDialog`. **Upgrade CTA** (Starter only): `bg-olive-100 rounded-lg p-8`. Sparkles icon (indigo). Instrument Serif heading: "Ready for dedicated infrastructure?". Subtitle. "Upgrade to Pro" accent button. |

#### Key Files

**Modify:**
- `frontend/app/(app)/org/[slug]/team/page.tsx` — Header, invite section, table styling
- `frontend/components/team/invite-member-form.tsx` — Plan limit progress bar
- `frontend/components/team/member-list.tsx` — Catalyst table with avatars
- `frontend/components/team/pending-invitations.tsx` — Catalyst table styling
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Category cards grid
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — Pricing cards, usage indicators

**Create:**
- `frontend/components/ui/avatar-circle.tsx` — Initials avatar component

---

### Epic 35: Auth Pages & Dialog Restyling

**Goal**: Create a distinctive split-screen auth layout and restyle all dialog components with warm olive-toned backdrops, Motion animations, and consistent styling per DESIGN.md.

**References**: DESIGN.md §3 (Auth Pages), §11 (Dialogs & Overlays)

**Dependencies**: Epic 30

**Scope**: Frontend

**Estimated Effort**: M

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **35A** | 35.1–35.3 | Auth pages: split-screen layout for sign-in and sign-up | **Done** (PR #69) |
| **35B** | 35.4–35.8 | Dialog restyling: Motion animations, warm backdrops, all dialog updates, empty states | **Done** (PR #70) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 35.1 | Create split-screen auth layout | 35A | **Done** | Rewrite `app/(auth)/layout.tsx`. **Desktop** (`md+`): Two-panel layout — left 55% + right 45%. **Left panel** (`hidden md:flex flex-col`): `bg-olive-100` background. DocTeams logo (top-left, `p-8`). Centered content: heading slot (varies per page) + subtitle + optional faded app screenshot or abstract pattern. **Right panel**: `bg-white` background. Content centered vertically and horizontally (`flex items-center justify-center`). **Mobile**: Full-width, just the right panel content. Left panel hidden. |
| 35.2 | Style sign-in page | 35A | **Done** | Update `app/(auth)/sign-in/[[...sign-in]]/page.tsx`. Pass heading metadata to the auth layout (or render left panel content inline). Left panel heading: Instrument Serif `font-display text-3xl`: "Welcome back". Subtitle: "Sign in to your workspace" in `text-olive-700`. Right panel: `<SignIn />` Clerk component centered. No other changes needed — Clerk component renders its own form. |
| 35.3 | Style sign-up page | 35A | **Done** | Update `app/(auth)/sign-up/[[...sign-up]]/page.tsx`. Left panel heading: Instrument Serif `font-display text-3xl`: "Create your workspace". Subtitle: "Get started with DocTeams in seconds" in `text-olive-700`. Right panel: `<SignUp />` Clerk component centered. |
| 35.4 | Add Motion enter/exit animations to Dialog and AlertDialog | 35B | **Done** | Update `components/ui/dialog.tsx`: wrap `DialogContent` with Motion. Enter: `opacity: 0 → 1`, `scale: 0.95 → 1` (spring animation, `type: "spring", stiffness: 300, damping: 25`). Exit: reverse. Similarly update `components/ui/alert-dialog.tsx` `AlertDialogContent`. Import `motion` from `motion/react`. Use `AnimatePresence` for exit animations. Overlay already updated to `bg-olive-950/25` in Epic 30. |
| 35.5 | Restyle Create and Edit Project dialogs | 35B | **Done** | Update `components/projects/create-project-dialog.tsx` and `edit-project-dialog.tsx`. **Title**: Inter `font-semibold text-lg`. **Fields**: Name input (required) with olive focus ring, Description textarea (optional). **Actions**: "Create" / "Save Changes" — solid pill button (primary). "Cancel" — plain text button. Consistent spacing: `space-y-4` between fields, `flex justify-end gap-3` for action buttons. |
| 35.6 | Restyle Delete Project confirmation dialog | 35B | **Done** | Update `components/projects/delete-project-dialog.tsx`. AlertDialog with: **Red top border** (`border-t-4 border-red-500` on content). **Icon**: `AlertTriangle` from Lucide in `text-red-600` centered above title. **Title**: "Delete Project" in `font-semibold`. **Description**: "This action cannot be undone. This will permanently delete **{project name}** and all associated documents." (bold project name). **Actions**: "Delete" — destructive pill button (`bg-red-600 text-white rounded-full`). "Cancel" — plain text button. |
| 35.7 | Restyle Add Member and Transfer Lead dialogs | 35B | **Done** | **Add Member** (`components/projects/add-member-dialog.tsx`): Ensure cmdk search input has olive focus ring. Member items: avatar circle (initials) + name (`font-semibold`) + email (`text-olive-600`). Hover: `bg-olive-100`. "Already a member" label for existing members (`text-olive-400`, disabled). **Transfer Lead** (`components/projects/transfer-lead-dialog.tsx`): Title: "Transfer Lead Role". Description with bold member name. Actions: "Transfer" — accent pill button (indigo). "Cancel" — plain text button. |
| 35.8 | Add consistent empty states across all list pages | 35B | **Done** | Create `components/empty-state.tsx` — reusable component accepting `icon` (Lucide component), `title` (string), `description` (string), and optional `action` (ReactNode for CTA button). Renders: large icon (`text-olive-300 w-16 h-16`), Instrument Serif title (`font-display text-xl text-olive-900`), description (`text-olive-600`), and action button. Center with `py-24 flex flex-col items-center text-center gap-4`. Use in: Projects list (Epic 33 already covers this), Documents tab ("No documents yet. Upload your first file above.", FileText icon), Members tab ("No members yet. Add your first team member.", Users icon), Team page members (if empty). |

#### Key Files

**Modify:**
- `frontend/app/(auth)/layout.tsx` — Split-screen layout
- `frontend/app/(auth)/sign-in/[[...sign-in]]/page.tsx` — Left panel heading
- `frontend/app/(auth)/sign-up/[[...sign-up]]/page.tsx` — Left panel heading
- `frontend/components/ui/dialog.tsx` — Motion enter/exit animation
- `frontend/components/ui/alert-dialog.tsx` — Motion enter/exit animation
- `frontend/components/projects/create-project-dialog.tsx` — Pill buttons, spacing
- `frontend/components/projects/edit-project-dialog.tsx` — Pill buttons, spacing
- `frontend/components/projects/delete-project-dialog.tsx` — Red border, AlertTriangle, destructive pill
- `frontend/components/projects/add-member-dialog.tsx` — Olive focus ring, avatar styling
- `frontend/components/projects/transfer-lead-dialog.tsx` — Accent pill, plain cancel

**Create:**
- `frontend/components/empty-state.tsx` — Reusable empty state component

---

### Epic 36: Polish & Accessibility

**Goal**: Final pass adding page transition animations, loading skeletons, dark mode verification, accessibility audit, and performance optimization. This epic should be done last as it touches all pages and requires the design to be stable.

**References**: DESIGN.md §14 (Dark Mode), §13 (Responsive Breakpoints), §1.6 (Animations)

**Dependencies**: Epics 30–35

**Scope**: Frontend

**Estimated Effort**: M

#### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 36.1 | Add page transition animations | Done | Create `components/page-transition.tsx` — wrapper component using Motion `AnimatePresence` + `motion.div`. Enter: `opacity: 0 → 1` with slight `y: 8 → 0` slide-up, duration 200ms. Wrap the `{children}` in the org layout's `<main>` with this component. Use `key={pathname}` to trigger animation on route change. Keep subtle — this is polish, not flashy. |
| 36.2 | Add loading states and skeleton screens | Done | Create skeleton components for each major data view: `components/ui/skeleton.tsx` (base — animated pulse with `bg-olive-200/50 rounded-md`). Create page-specific loading files: `app/(app)/org/[slug]/dashboard/loading.tsx` (4 stat card skeletons + table skeleton), `app/(app)/org/[slug]/projects/loading.tsx` (6 card skeletons in grid), `app/(app)/org/[slug]/projects/[id]/loading.tsx` (header skeleton + tab bar + table skeleton). Use Shadcn Skeleton pattern if already available, otherwise create from scratch. |
| 36.3 | Dark mode verification pass | Done | Verify all olive tokens render correctly in dark mode. Check: (1) Page backgrounds: `bg-olive-50` → `dark:bg-olive-950`. (2) Cards: `bg-white` → `dark:bg-olive-900`. (3) Text: `text-olive-950` → `dark:text-white`, `text-olive-600` → `dark:text-olive-400`. (4) Borders: `border-olive-200` → `dark:border-olive-800`. (5) Sidebar: `bg-olive-950` in both modes (same). (6) Landing page: all sections readable in dark mode. (7) Tables: hover `bg-olive-50` → `dark:bg-olive-800`. Fix any missing dark mode classes. Note: dark mode toggle is not required for MVP, but tokens should work if `.dark` class is applied. |
| 36.4 | Accessibility audit | Done | Check all interactive elements for: (1) **Focus states**: All focusable elements should have `focus-visible:ring-2 focus-visible:ring-olive-500 focus-visible:ring-offset-2` (verify Input, Button, Link, Tab, Dialog triggers all have visible focus indicators). (2) **ARIA labels**: Sidebar navigation has `aria-label="Main navigation"`. Tables have `role="table"` or use semantic `<table>` elements. Dialog titles connected via `aria-labelledby`. (3) **Color contrast**: Run key text/background combinations through WCAG AA checker — `olive-600 on white` (body text), `olive-400 on white` (muted text), `white on olive-950` (sidebar). Fix any failing pairs by adjusting to darker/lighter olive step. (4) **Keyboard navigation**: Tab order through sidebar, header, main content. Escape closes dialogs. Enter/Space activates buttons. |
| 36.5 | Performance audit — font loading and CSS | Done | (1) **Font loading**: Verify `next/font/google` applies `font-display: swap` for Instrument Serif and Inter. Check that fonts don't cause layout shift (CLS) — use `size-adjust` if needed. (2) **CSS size**: Check that the olive color scale additions don't bloat the CSS bundle. Tailwind v4 tree-shakes unused utilities, but verify no duplicate custom properties. (3) **Image optimization**: Ensure any screenshots/images added to the landing page use `next/image` with proper `width`/`height` or `fill` + `sizes` attributes. (4) **Motion bundle**: Verify `motion` is only imported in client components that use it (tree-shaking). Check bundle size with `pnpm build` output. |

#### Key Files

**Create:**
- `frontend/components/page-transition.tsx` — Motion page transition wrapper
- `frontend/components/ui/skeleton.tsx` — Base skeleton component (if not already from Shadcn)
- `frontend/app/(app)/org/[slug]/dashboard/loading.tsx` — Dashboard skeleton
- `frontend/app/(app)/org/[slug]/projects/loading.tsx` — Projects list skeleton
- `frontend/app/(app)/org/[slug]/projects/[id]/loading.tsx` — Project detail skeleton

**Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` — Add page transition wrapper
- Various component files — Dark mode classes, focus states, ARIA attributes

---

### Phase 3 Implementation Order

#### Stage 1: Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 30: Design Foundation | All design epics depend on the new tokens, fonts, and base component styles. Must land first. |

#### Stage 2: Shell + Marketing (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 31: App Shell Redesign | Must complete before app page redesigns (Epics 33–34) since they render inside the shell. |
| 2b | Epic 32: Landing Page | Independent of app shell — uses the same tokens from Epic 30 but has no structural dependency on the sidebar/header. |
| 2c | Epic 35: Auth Pages & Dialogs | Auth pages are independent of the app shell. Dialog restyling can happen in parallel. |

**Rationale**: After the foundation lands, three independent tracks can run in parallel: the app shell, the marketing page, and auth/dialogs. Each only depends on Epic 30's design tokens.

#### Stage 3: App Pages (After Shell)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 33: Core App Pages Redesign | Depends on the shell (Epic 31) being complete since dashboard/projects render inside it. |
| 3b | Epic 34: Supporting Pages Redesign | Same dependency on shell. Can run in parallel with Epic 33. |

#### Stage 4: Polish

| Order | Epic | Rationale |
|-------|------|-----------|
| 4 | Epic 36: Polish & Accessibility | Must run last — touches all pages, requires stable design across the entire frontend. |

#### Phase 3 Summary Timeline

```
Stage 1:  [E30]
Stage 2:  [E31] [E32] [E35]     <- parallel (all depend only on E30)
Stage 3:  [E33] [E34]           <- parallel (after E31)
Stage 4:  [E36]                  <- after all others
```
