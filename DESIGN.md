# DocTeams Frontend Design Document

> **Status:** Draft
> **Date:** 2026-02-11
> **Inspiration:** Catalyst UI Kit (app chrome) + Oatmeal Olive Instrument (marketing surfaces)

## Design Philosophy

Blend two complementary design languages:

- **Oatmeal** — warm editorial personality for public-facing pages (landing, auth). Instrument Serif display font, custom olive color scale, generous spacing, pill-shaped buttons.
- **Catalyst** — clean data-focused precision for the authenticated app shell (sidebar, tables, dialogs). Motion-animated indicators, minimal shadows, accessible Headless UI patterns.

The goal is a distinctive B2B SaaS frontend that doesn't look like a default component library template.

---

## 1. Global Design Tokens

### Fonts

| Role | Current | Proposed | Source |
|------|---------|----------|--------|
| Display (h1, hero, stats) | Geist Sans | **Instrument Serif** (Google Fonts) | Oatmeal |
| Body (paragraphs, UI) | Geist Sans | **Inter** (Google Fonts) | Both templates |
| Code | Geist Mono | **Geist Mono** (unchanged) | — |

### Color Scale

Replace neutral zinc/gray with a custom **olive** scale using OKLCH color space (perceptually uniform):

```
olive-50:  oklch(98.8% 0.003 106.5)   -- lightest background
olive-100: oklch(96.6% 0.005 106.5)   -- page background (light mode)
olive-200: oklch(93%   0.007 106.5)   -- borders, subtle backgrounds
olive-300: oklch(88%   0.011 106.6)   -- hover borders
olive-400: oklch(73.7% 0.021 106.9)   -- muted text, timestamps
olive-500: oklch(58%   0.031 107.3)   -- mid-tone
olive-600: oklch(46.6% 0.025 107.3)   -- secondary text
olive-700: oklch(39.4% 0.023 107.4)   -- body text
olive-800: oklch(28.6% 0.016 107.4)   -- strong text
olive-900: oklch(22.8% 0.013 107.4)   -- headings
olive-950: oklch(15.3% 0.006 107.1)   -- darkest (sidebar bg, primary buttons)
```

### Accent Color

**Indigo** for primary interactive elements (CTAs, active states, Lead badge):

```
indigo-500: oklch(58.5% 0.233 277.1)
indigo-600: oklch(51.1% 0.262 276.9)
```

### Semantic Colors

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `--background` | olive-50 | olive-950 | Page background |
| `--foreground` | olive-900 | white | Primary text |
| `--muted` | olive-100 | olive-900 | Muted backgrounds |
| `--muted-foreground` | olive-600 | olive-400 | Secondary text |
| `--border` | olive-200 | olive-800 | Borders |
| `--primary` | olive-950 | white | Primary buttons |
| `--primary-foreground` | white | olive-950 | Primary button text |
| `--accent` | indigo-600 | indigo-500 | Interactive accents |
| `--destructive` | red-600 | red-500 | Destructive actions |
| `--sidebar` | olive-950 | olive-950 | Sidebar background |
| `--sidebar-foreground` | white | white | Sidebar text |

### Spacing

Increase breathing room throughout. Key changes:

| Context | Current | Proposed |
|---------|---------|----------|
| Section gaps | gap-4/6 | **gap-8/12/16** |
| Content padding | p-6 | **px-6 lg:px-10** |
| Card internal | py-6 px-6 | **p-6 lg:p-8** |
| Page header to content | mb-4 | **mb-8** |

### Border Radius

| Element | Current | Proposed |
|---------|---------|----------|
| Cards | rounded-xl | **rounded-lg** (0.5rem) |
| Primary buttons | rounded-md | **rounded-full** (pill) |
| Secondary buttons | rounded-md | **rounded-md** (keep) |
| Badges | rounded-full | **rounded-full** (keep) |
| Inputs | rounded-md | **rounded-md** (keep) |
| Dialogs | rounded-lg | **rounded-xl** |

### Shadows

Minimal. Rely on borders for definition:

| Element | Current | Proposed |
|---------|---------|----------|
| Cards | shadow-sm | **none** (border only) |
| Cards (hover) | — | **shadow-sm** (subtle elevation on hover) |
| Dialogs | default | **shadow-lg** (prominent) |
| Dropdowns | default | **shadow-md** |

### Animations

Add **Motion** (Framer Motion) for:
- Sidebar active item indicator (spring-animated bar)
- Tab underline indicator (layout animation)
- Dialog enter/exit (fade + scale 95% → 100%)
- Page transitions (fade-in on mount)

---

## 2. Landing Page (`/`)

**Current:** Simple gradient background, heading, two CTA buttons. Generic.

**Proposed:** Full editorial marketing page with multiple sections.

### 2.1 Hero Section

```
Layout: Left-aligned text (60%) + app screenshot (40%)
```

- **Eyebrow:** AnnouncementBadge — "New: Pro plan with dedicated infrastructure" + arrow link
- **Headline:** Instrument Serif, `text-5xl/12 sm:text-[5rem]/20`, olive-950, `text-balance`
  - e.g. "Document collaboration for modern teams"
- **Subtitle:** Inter, `text-lg/8`, olive-700. 2-3 sentence value prop.
- **CTAs:** Two pill buttons side by side:
  - "Get Started" — solid olive-950, white text (primary)
  - "Sign In" — soft/translucent olive-950/10 (secondary)
- **Screenshot:** App screenshot (dashboard or project view) with colored wallpaper background (olive-200 tint). Rounded-lg, subtle shadow.
- **Background:** Olive-50 (warm tint, not pure white)

### 2.2 Logo Bar

```
Layout: Centered, responsive grid (3 cols mobile, 6 cols desktop)
```

- "Trusted by teams at" label in olive-400, text-sm, uppercase tracking-wide
- 6 placeholder company logos in olive-400 monochrome treatment
- Separator above and below (olive-200)

### 2.3 Features Section

```
Layout: Two-column alternating (text left/screenshot right, then swap)
```

Three features, each with:
- **Icon:** Lucide icon in olive-600
- **Heading:** Instrument Serif, text-2xl
- **Description:** Inter, text-base/7, olive-700. 2-3 sentences.
- **Screenshot:** Relevant app screenshot with wallpaper treatment

Features:
1. **Document Management** — Upload, organize, and share documents across your team
2. **Project Collaboration** — Assign roles, manage members, track project progress
3. **Enterprise Isolation** — Dedicated database schemas for Pro teams, shared infrastructure for starters

### 2.4 Stats Section

```
Layout: 3-column grid, centered
```

- Large Instrument Serif numbers (`text-4xl sm:text-5xl`, olive-950)
- Label below each (`text-sm`, olive-600)
- Stats: "10,000+ Documents" / "500+ Teams" / "99.9% Uptime"
- Subtle olive-100 background strip

### 2.5 Pricing Preview

```
Layout: 2-column card grid (stacked on mobile)
```

Two Oatmeal-style pricing cards:

**Starter (Free):**
- Olive-950/2.5 background
- "Starter" label + "Free" price
- Feature list with checkmarks: 2 members, shared infrastructure, row-level isolation, community support
- "Get Started" outline button

**Pro ($X/mo):**
- Olive-950/2.5 background + "Most popular" badge (indigo)
- "Pro" label + price
- Feature list: 10 members, dedicated infrastructure, schema isolation, priority support
- "Get Started" solid button (indigo)

### 2.6 Testimonials

```
Layout: 3-column grid (stacked on mobile)
```

Three testimonial cards:
- Auto-inserted opening quote mark (olive-300, large)
- Quote text in Inter, olive-800
- Divider
- Avatar (round, 48px) + Name (semibold) + Company/title (olive-600)
- Card: olive-950/2.5 background, rounded-lg

### 2.7 CTA Section

```
Layout: Centered, generous vertical padding (py-24)
```

- Instrument Serif heading: "Ready to get started?"
- Subtitle in olive-700
- "Create your workspace" solid pill button (olive-950)

### 2.8 Footer

```
Layout: Full-width, olive-950 background
```

- DocTeams logo (white)
- Link groups: Product, Company, Legal
- Copyright line
- Social links (optional)

---

## 3. Auth Pages (`/sign-in`, `/sign-up`)

**Current:** Centered Clerk components on plain background.

**Proposed:** Split-screen layout for desktop, full-width form on mobile.

### Layout

```
Desktop: [Left Panel 55%] [Right Panel 45%]
Mobile:  [Full-width form]
```

**Left Panel (desktop only, `hidden md:flex`):**
- Olive-100 background
- DocTeams logo (top-left)
- Centered content:
  - Instrument Serif heading: "Welcome back" (sign-in) / "Create your workspace" (sign-up)
  - Subtitle in olive-700: one-liner tagline
  - Optional: faded app screenshot or abstract olive-toned pattern

**Right Panel:**
- White background
- Clerk `<SignIn />` or `<SignUp />` centered vertically and horizontally
- Clean, no distractions

---

## 4. App Shell (Layout for `/org/[slug]/*`)

**Current:** Basic sidebar + sheet mobile + header with org switcher.

**Proposed:** Dark sidebar (Catalyst-inspired) with warm olive tones, clean header.

### 4.1 Sidebar (Desktop)

```
Width: 240px (w-60), fixed position
Background: olive-950
Text: white / white/60 (muted)
```

**Structure:**
- **Header:** DocTeams logo (white) + org name (truncated). Clickable for org switcher.
- **Body (main nav):**
  - Dashboard (LayoutDashboard icon)
  - Projects (FolderKanban icon)
  - Team (Users icon)
  - Settings (Settings icon)
- **Footer:** User avatar (round, 32px) + name + email (truncated). Clickable for user menu.

**Item States:**
- Default: white/60 text, transparent background
- Hover: white text, olive-800 background
- Active/Current: white text, white/5 background + **Motion-animated left indicator bar** (2px wide, indigo-500, spring animation on route change)

**Dividers:** Thin white/10 separators between sections.

### 4.2 Sidebar (Mobile)

```
Trigger: Hamburger button in header (md:hidden)
Component: Sheet (slide from left)
```

Same content as desktop sidebar. Sheet backdrop: olive-950/25.

### 4.3 Header

```
Height: h-14 (56px)
Background: white (light) / olive-950 (dark)
Border: bottom olive-200
```

- **Left:** Mobile hamburger (md:hidden) + breadcrumbs (org name > current page)
- **Right:** Org switcher dropdown + Clerk UserButton

### 4.4 Content Area

```
Background: olive-50 (light) / olive-925 (dark)
Padding: px-6 lg:px-10
Max-width: max-w-7xl mx-auto
```

All page content renders inside this container.

---

## 5. Dashboard (`/org/[slug]/dashboard`)

**Current:** Stats card + quick actions + recent projects. Flat.

**Proposed:** Spacious overview with visual hierarchy and optional activity feed.

### 5.1 Page Header

- Instrument Serif h1: "Dashboard"
- Subtitle: org name in olive-600, Inter

### 5.2 Stats Row

```
Layout: 4-column responsive grid (2 cols mobile, 4 cols lg)
```

Four stat cards:
- **Number:** Instrument Serif, text-3xl, olive-950
- **Label:** Inter text-sm, olive-600
- **Background:** white, olive-200 border
- Stats: Projects / Documents / Team Members / Storage Used

### 5.3 Quick Actions

```
Layout: Horizontal row of pill buttons
```

- "New Project" — solid olive-950 (primary)
- "Invite Member" — soft olive-950/10
- "Upload Document" — soft olive-950/10

### 5.4 Recent Projects

```
Layout: Catalyst-style table (not cards)
```

- **Columns:** Name (link), Lead, Documents (count), Last Updated
- **Rows:** Click-anywhere to navigate (Catalyst linked row pattern)
- **Hover:** Olive-50 row highlight
- **Limit:** 5 most recent, "View all projects" link below

### 5.5 Activity Feed (new section)

```
Layout: Below recent projects (or right column on xl screens)
```

- Simple vertical timeline
- Each entry: timestamp (olive-400) + description + actor name
- Events: "Alice uploaded document.pdf", "Bob joined Project X", "Carol created Project Y"
- Subtle left border (olive-200) connecting entries

---

## 6. Projects List (`/org/[slug]/projects`)

**Current:** Grid of basic cards.

**Proposed:** Upgraded card grid with better visual hierarchy.

### 6.1 Page Header

- Instrument Serif h1: "Projects"
- Subtitle: project count in olive-600
- Right-aligned: "New Project" pill button (solid olive-950)

### 6.2 Upgrade Prompt (Starter only)

```
Layout: Full-width card above grid
```

- Olive-100 background with indigo left border (4px)
- Text: "Upgrade to Pro for dedicated infrastructure and schema isolation"
- "Learn more" link → billing page
- Dismiss button (x)

### 6.3 Project Cards

```
Layout: Grid — 1 col (mobile) → 2 col (md) → 3 col (lg), gap-6
```

Each card:
- **White background**, olive-200 border, rounded-lg
- **Top row:** Project name (font-semibold, olive-950) + role badge (pill):
  - Lead: indigo background, white text
  - Member: olive-200 background, olive-700 text
- **Middle:** Description, 2 lines max (line-clamp-2), olive-600
- **Bottom row:** Document count icon + member count icon + creation date. All olive-400, text-sm.
- **Hover:** border-olive-300 + shadow-sm transition

### 6.4 Empty State

- Large Lucide icon (FolderOpen, olive-300, 64px)
- Instrument Serif heading: "No projects yet"
- Subtitle: "Create your first project to get started"
- "New Project" pill button (solid)

---

## 7. Project Detail (`/org/[slug]/projects/[id]`)

**Current:** Header + documents panel + members panel stacked vertically.

**Proposed:** Header + tabbed content (Documents / Members) with Catalyst table patterns.

### 7.1 Header

- **Project name:** Instrument Serif, text-2xl
- **Role badge:** Pill badge next to name (Lead/Member)
- **Description:** Inter, olive-600, below name
- **Meta line:** Created date + document count + member count. Olive-400, text-sm. Separated by dots.
- **Actions (right-aligned):**
  - "Edit" — outline button (visible to admin/lead)
  - "Delete" — ghost destructive button (visible to owner)

### 7.2 Tabs

```
Component: Catalyst-style tabs with Motion-animated underline
```

Two tabs: **Documents** / **Members**

Underline indicator: 2px indigo-500 bar that slides between tabs using Motion `layoutId` animation.

### 7.3 Documents Tab

**Upload Zone (top):**
- Dashed olive-300 border, rounded-lg
- Centered: upload icon (olive-400) + "Drag files here or click to browse"
- Hover: olive-200 background, border-olive-400
- Active drag: indigo-100 background, border-indigo-400

**Upload Progress (below zone, when active):**
- List of UploadProgressItems
- Each: filename + progress bar (indigo fill) + status text + cancel/retry button
- Completed items fade out after 3 seconds

**Documents Table (Catalyst-style):**

| Column | Width | Content |
|--------|-------|---------|
| Name | flex | Filename (truncated) + file icon |
| Size | 100px | Formatted size (e.g., "2.4 MB") |
| Status | 100px | Badge: Uploaded (green), Processing (amber), Failed (red) |
| Uploaded | 140px | Relative date ("2 hours ago") |
| Actions | 60px | Download icon button (olive-600 hover:olive-950) |

- Row hover: olive-50 background
- Empty state: "No documents yet. Upload your first file above."

### 7.4 Members Tab

**Header row:**
- "Members" label + count badge
- Right: "Add Member" button (outline) — opens cmdk dialog

**Members Table (Catalyst-style):**

| Column | Width | Content |
|--------|-------|---------|
| Member | flex | Avatar circle (initials, 32px) + name + email below |
| Role | 100px | Badge: Lead (indigo), Member (olive) |
| Added | 140px | Date |
| Actions | 60px | Dropdown (kebab icon): Remove, Transfer Lead |

- Actions column: only visible to project leads
- Row hover: olive-50 background
- Empty state: "No members yet. Add your first team member."

---

## 8. Team Page (`/org/[slug]/team`)

**Current:** Tabs + invite form + basic tables.

**Proposed:** Cleaner layout with better visual separation.

### 8.1 Page Header

- Instrument Serif h1: "Team"
- Member count badge (olive-200 background, text-sm)

### 8.2 Invite Section (admin only)

```
Layout: Card at top of page, olive-50 background
```

- Heading: "Invite a team member" (Inter semibold)
- Form: email input + role select dropdown + "Send Invite" pill button (solid)
- All inline on desktop, stacked on mobile
- **Plan limit indicator:** Progress bar below form: "3 of 10 members" (olive fill for Starter, indigo for Pro)
- At limit: input disabled, message "Member limit reached" + "Upgrade" link (indigo)

### 8.3 Tabs

```
Tabs: Members / Pending Invitations
Indicator: Motion-animated underline (indigo-500)
```

### 8.4 Members Table

| Column | Width | Content |
|--------|-------|---------|
| Member | flex | Avatar (initials, 32px) + full name |
| Email | 200px | Email address (olive-600) |
| Role | 100px | Badge: Owner (amber), Admin (slate), Member (olive) |
| Joined | 140px | Formatted date |

- Load more pagination: "Load more" text button at bottom
- Row hover: olive-50

### 8.5 Pending Invitations Table

| Column | Width | Content |
|--------|-------|---------|
| Email | flex | Invited email |
| Role | 100px | Badge (same color scheme as members) |
| Invited | 140px | Date |
| Actions | 80px | "Revoke" ghost button (destructive) |

- Previous/Next pagination at bottom

---

## 9. Settings Hub (`/org/[slug]/settings`)

**Current:** Single card linking to Billing.

**Proposed:** Settings category grid with room for future expansion.

### 9.1 Page Header

- Instrument Serif h1: "Settings"

### 9.2 Category Cards

```
Layout: 2-column grid (1 col mobile), gap-6
```

Each card:
- White background, olive-200 border, rounded-lg
- Left: icon in olive-100 circle (48px)
- Center: title (semibold) + description (olive-600)
- Right: chevron-right icon (olive-400)
- Hover: border-olive-300, slight translate-x (1px)
- Click: navigate to sub-page

**Cards:**

| Card | Icon | Title | Description | Status |
|------|------|-------|-------------|--------|
| Billing & Plan | CreditCard | Billing | Manage your subscription and view usage | Active |
| Organization | Building2 | Organization | Update org name, logo, and details | Coming soon |
| Security | Shield | Security | Configure authentication and access policies | Coming soon |
| Integrations | Puzzle | Integrations | Connect third-party tools and services | Coming soon |

- "Coming soon" cards: 50% opacity, cursor-not-allowed, "Coming soon" badge (olive-200)

---

## 10. Billing Page (`/org/[slug]/settings/billing`)

**Current:** Plan card + comparison table + upgrade card.

**Proposed:** Oatmeal-style pricing card aesthetic with visual usage indicators.

### 10.1 Page Header

- Instrument Serif h1: "Billing"
- Back link to Settings (chevron-left + "Settings")

### 10.2 Current Plan Card

```
Layout: Full-width card
```

- **Plan name:** Instrument Serif text-xl + plan badge (Starter: olive, Pro: indigo)
- **Description:** Plan summary line in olive-600
- **Usage section:**
  - "Members" label + "3 of 10" count
  - Progress bar: fill color matches plan (olive for Starter, indigo for Pro)
  - Percentage label

### 10.3 Plan Comparison (visible when on Starter)

```
Layout: 2 side-by-side cards (stacked mobile)
```

**Starter Card:**
- Olive-950/2.5 background
- "Starter" heading (Inter semibold)
- "Free" price (Instrument Serif, text-3xl)
- "For small teams getting started" subtitle
- Feature list with check icons (olive-500):
  - 2 team members
  - Shared infrastructure
  - Row-level data isolation
  - Community support
- Current plan indicator (if active): "Current plan" badge

**Pro Card:**
- Olive-950/2.5 background
- **"Most popular" badge** (indigo pill, top-right)
- "Pro" heading (Inter semibold)
- "$X/month" price (Instrument Serif, text-3xl)
- "For growing teams that need more" subtitle
- Feature list with check icons (indigo-500):
  - 10 team members
  - Dedicated infrastructure
  - Schema-level data isolation
  - Priority support
- "Upgrade to Pro" solid pill button (indigo) — if on Starter
- "Current plan" badge — if on Pro

### 10.4 Upgrade CTA (Starter only)

```
Layout: Full-width section, olive-100 background, rounded-lg
```

- Sparkles icon (indigo)
- Instrument Serif heading: "Ready for dedicated infrastructure?"
- Subtitle: "Upgrade to Pro for schema isolation, more members, and priority support"
- "Upgrade to Pro" solid button (indigo)
- Clicking opens UpgradeConfirmDialog

---

## 11. Dialogs & Overlays

### 11.1 Global Dialog Styling

All dialogs share:
- **Backdrop:** olive-950/25 (not black/50 — warmer, less harsh)
- **Animation:** Motion — enter: opacity 0→1, scale 95%→100% (spring); exit: reverse
- **Shape:** rounded-xl
- **Shadow:** shadow-lg
- **Max-width:** sm (default), configurable per dialog

### 11.2 Create Project Dialog

- **Title:** "Create Project" (Inter semibold)
- **Fields:**
  - Name input (required) — olive focus ring
  - Description textarea (optional)
- **Actions:**
  - "Create" — solid pill button (olive-950)
  - "Cancel" — plain text button

### 11.3 Edit Project Dialog

- Same layout as Create, pre-populated fields
- "Save Changes" solid pill + "Cancel" plain

### 11.4 Delete Project Confirmation

- **Style:** AlertDialog with red accent
- **Red top border** (4px, destructive color)
- **Icon:** AlertTriangle in red circle
- **Title:** "Delete Project"
- **Description:** "This action cannot be undone. This will permanently delete **{project name}** and all associated documents."
- **Actions:**
  - "Delete" — destructive pill button (red)
  - "Cancel" — plain text button

### 11.5 Add Member Dialog (cmdk)

- **Component:** Command palette overlay
- **Search input** at top with search icon
- **Member list:** scrollable, each item:
  - Avatar circle (initials) + name (semibold) + email (olive-600)
  - Hover: olive-100 background
  - Click: add member + close dialog
- **Empty state:** "No members found"
- **Already added:** show "Already a member" label, item disabled

### 11.6 Transfer Lead Dialog

- **Title:** "Transfer Lead Role"
- **Description:** "Transfer the lead role to **{member name}**? You will become a regular member."
- **Actions:**
  - "Transfer" — solid pill (indigo)
  - "Cancel" — plain text

### 11.7 Upgrade Confirmation Dialog

- **Title:** "Upgrade to Pro"
- **Summary card:** Current (Starter) → New (Pro) with arrow
- **Price:** "$X/month" (Instrument Serif, large)
- **Key benefits:** 3 bullet points with checkmarks
- **Actions:**
  - "Confirm Upgrade" — solid pill (indigo)
  - "Cancel" — plain text

---

## 12. Component Specification

### 12.1 Buttons

**Variants:**

| Variant | Background | Text | Border | Radius | Usage |
|---------|-----------|------|--------|--------|-------|
| Solid (primary) | olive-950 | white | none | full (pill) | Primary CTAs |
| Solid (accent) | indigo-600 | white | none | full (pill) | Upgrade, special actions |
| Soft | olive-950/10 | olive-950 | none | full (pill) | Secondary CTAs |
| Outline | transparent | olive-950 | olive-200 | md | Form secondary buttons |
| Ghost | transparent | olive-700 | none | md | Tertiary actions |
| Plain | transparent | olive-700 | none | none | Inline text buttons |
| Destructive | red-600 | white | none | full (pill) | Delete, remove |

**Sizes:**

| Size | Padding | Text | Height |
|------|---------|------|--------|
| sm | px-3 py-1 | text-sm | h-8 |
| md | px-4 py-2 | text-sm | h-9 |
| lg | px-5 py-2.5 | text-base | h-10 |

### 12.2 Badges

| Variant | Background | Text | Usage |
|---------|-----------|------|-------|
| Lead | indigo-100 | indigo-700 | Project lead role |
| Member | olive-100 | olive-700 | Project member role |
| Owner | amber-100 | amber-700 | Org owner role |
| Admin | slate-100 | slate-700 | Org admin role |
| Starter | olive-100 | olive-700 | Starter plan |
| Pro | indigo-100 | indigo-700 | Pro plan |
| Success | green-100 | green-700 | Uploaded status |
| Warning | amber-100 | amber-700 | Processing status |
| Destructive | red-100 | red-700 | Failed status, error |
| Neutral | olive-100 | olive-600 | Counts, misc |

All badges: rounded-full, text-xs, px-2.5 py-0.5, font-medium.

### 12.3 Tables (Catalyst Pattern)

Tables follow the Catalyst context-based design:

- **Container:** horizontal scroll on small screens, full-width on desktop
- **Header:** olive-600 text, text-xs uppercase tracking-wide, border-b olive-200
- **Rows:** border-b olive-100 (last:border-0), py-3 px-4
- **Hover:** olive-50 background transition
- **Linked rows:** entire row clickable (overlay anchor pattern)
- **Dense mode:** py-2 (for compact views)
- **Striped mode:** odd:bg-olive-50 (optional)

### 12.4 Inputs (Catalyst Pattern)

- **Border:** olive-200 (light), olive-700 (dark)
- **Focus:** olive-500 ring (2px offset), not default blue
- **Background:** white (light), olive-900 (dark)
- **Placeholder:** olive-400
- **Invalid:** red-500 ring, red-600 border
- **Disabled:** olive-100 background, olive-400 text, cursor-not-allowed

### 12.5 Cards

- **Background:** white (light), olive-900 (dark)
- **Border:** olive-200 (light), olive-800 (dark)
- **Radius:** rounded-lg
- **Shadow:** none (default), shadow-sm (hover)
- **Hover (interactive):** border-olive-300, shadow-sm, transition-all duration-150

---

## 13. Responsive Breakpoints

```
sm:  640px   — 2-col grids, show secondary table columns
md:  768px   — sidebar visible (desktop), hide hamburger
lg:  1024px  — 3-col grids, full table columns
xl:  1280px  — max-width content, optional side panels
```

All layouts are mobile-first. The sidebar collapses to a sheet below `md`.

---

## 14. Dark Mode

Both templates support dark mode. Key mappings:

| Element | Light | Dark |
|---------|-------|------|
| Page background | olive-50 | olive-950 |
| Card background | white | olive-900 |
| Text primary | olive-950 | white |
| Text secondary | olive-600 | olive-400 |
| Borders | olive-200 | olive-800 |
| Sidebar | olive-950 | olive-950 (same) |
| Table hover | olive-50 | olive-800 |
| Input background | white | olive-900 |

Dark mode toggle is not required for MVP but the token system supports it.

---

## 15. Implementation Phases

### Phase 1: Foundation

- [ ] Update `globals.css` with olive color scale and new semantic tokens
- [ ] Add Instrument Serif + Inter fonts (replace Geist Sans)
- [ ] Install `motion` (Framer Motion) package
- [ ] Update base component styles (buttons, badges, inputs, cards)

### Phase 2: App Shell

- [ ] Redesign sidebar (dark olive-950, Motion-animated indicators)
- [ ] Redesign header (breadcrumbs, cleaner layout)
- [ ] Update content area (olive-50 background, max-width container)
- [ ] Mobile sidebar sheet styling

### Phase 3: Landing Page

- [ ] Hero section (Instrument Serif heading, CTAs, screenshot)
- [ ] Logo bar
- [ ] Features section (two-column alternating)
- [ ] Stats section
- [ ] Pricing preview cards
- [ ] Testimonials
- [ ] CTA + Footer

### Phase 4: Core App Pages

- [ ] Dashboard (stats, quick actions, recent projects table, activity feed)
- [ ] Projects list (upgraded cards, empty state)
- [ ] Project detail (tabbed layout, Catalyst tables)

### Phase 5: Supporting Pages

- [ ] Team page (invite section, member/invitation tables)
- [ ] Settings hub (category cards)
- [ ] Billing page (pricing cards, usage indicators)

### Phase 6: Auth & Dialogs

- [ ] Auth pages (split-screen layout)
- [ ] All dialog restyling (warm backdrop, Motion animations)
- [ ] Empty states across all pages

### Phase 7: Polish

- [ ] Page transition animations
- [ ] Loading states and skeletons
- [ ] Dark mode pass (verify all tokens)
- [ ] Accessibility audit (focus states, ARIA, contrast ratios)
- [ ] Performance audit (font loading, CSS size)
