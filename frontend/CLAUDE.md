# Frontend CLAUDE.md

Next.js 16 (App Router) / React 19 / TypeScript 5 frontend for a multi-tenant B2B SaaS platform. Auth via Clerk, UI via Shadcn (new-york style) + Tailwind CSS v4. Custom "Signal Deck" design system — precise, high-contrast, information-forward aesthetic with cool slate palette and teal accents.

## Build & Run

```bash
pnpm install         # Install dependencies
pnpm dev             # Dev server on port 3000
pnpm run build       # Production build
pnpm run lint        # ESLint (flat config)
pnpm test            # Vitest unit tests
```

## Project Structure

```
frontend/
├── app/
│   ├── page.tsx                      # Landing page (public)
│   ├── (auth)/                       # Clerk auth pages
│   │   ├── layout.tsx                # Split-screen auth layout
│   │   ├── sign-in/[[...sign-in]]/page.tsx
│   │   └── sign-up/[[...sign-up]]/page.tsx
│   ├── (app)/                        # Authenticated app shell
│   │   ├── layout.tsx
│   │   ├── dashboard/page.tsx        # Pre-org dashboard (no org selected)
│   │   ├── create-org/page.tsx       # Org creation page
│   │   └── org/[slug]/
│   │       ├── layout.tsx            # Org-scoped layout (sidebar + header)
│   │       ├── dashboard/page.tsx
│   │       ├── projects/
│   │       │   ├── page.tsx          # Project list
│   │       │   ├── actions.ts
│   │       │   └── [id]/
│   │       │       ├── page.tsx      # Project detail + documents
│   │       │       ├── actions.ts
│   │       │       └── member-actions.ts
│   │       ├── team/
│   │       │   ├── page.tsx
│   │       │   └── actions.ts
│   │       └── settings/
│   │           ├── page.tsx          # Settings hub
│   │           └── billing/
│   │               ├── page.tsx      # Billing + plan management
│   │               └── actions.ts
│   ├── api/
│   │   └── webhooks/
│   │       └── clerk/route.ts        # Clerk webhook handler
│   ├── globals.css                   # Tailwind v4 + slate color tokens
│   └── layout.tsx                    # Root layout with ClerkProvider
├── components/
│   ├── ui/                           # Shadcn UI components (customized — see below)
│   ├── projects/                     # Project-specific components
│   ├── documents/                    # Document-specific components
│   ├── team/                         # Team management components
│   ├── billing/                      # Plan badge, upgrade prompt/dialog
│   ├── marketing/                    # Landing page section components
│   ├── desktop-sidebar.tsx           # Dark slate-950 sidebar with Motion indicator
│   ├── mobile-sidebar.tsx            # Sheet-based mobile sidebar
│   └── breadcrumbs.tsx               # Pathname-based breadcrumb nav
├── lib/
│   ├── api.ts                        # Spring Boot API client (attaches Bearer JWT)
│   ├── internal-api.ts               # Types for internal/billing API
│   ├── nav-items.ts                  # Sidebar navigation item definitions
│   └── utils.ts                      # cn() helper from Shadcn
├── hooks/                            # Custom React hooks
├── __tests__/                        # Test files
├── proxy.ts                         # Clerk auth proxy with org sync
├── components.json                   # Shadcn UI config
├── vitest.config.ts                  # Vitest config with @/* alias
├── next.config.ts
├── tsconfig.json
├── eslint.config.mjs                 # ESLint flat config
└── postcss.config.mjs
```

## Design System

The frontend uses a "Signal Deck" design language — precise, information-forward, inspired by Linear/Vercel.

### Fonts

| Role | Font | CSS Variable | Usage |
|------|------|-------------|-------|
| Display | **Sora** | `--font-display` | h1, hero text, headings — geometric, sharp, tight tracking. Use `font-display` class |
| Body / UI | **IBM Plex Sans** | `--font-sans` | Paragraphs, buttons, labels, table text — engineered precision |
| Code / Stats | **JetBrains Mono** | `--font-mono` | Code blocks, KPI numbers, inline stats. Use `font-mono tabular-nums` for data |

All loaded via `next/font/google` in `app/layout.tsx`.

### Color System

Custom **slate** OKLCH scale (cool blue-gray, hue ~260). Defined as CSS custom properties in `globals.css`:

```
slate-50  → slate-950    (cool blue-gray tint, hue ~260)
teal-500, teal-600       (accent for interactive elements)
```

"Concrete Studio" palette (light mode): gray background (`oklch(94%)`) with lifted white cards (`shadow-sm`).

Semantic token mappings (light mode):
- `--background` → `oklch(94% 0.008 260)` (concrete gray), `--foreground` → slate-900
- `--card` → `oklch(99.5% 0.002 260)` (near-white, lifted)
- `--primary` → slate-950, `--muted` → slate-100, `--border` → slate-200
- `--accent` → teal-600 (CTAs, active states)
- `--sidebar` → slate-950 (dark sidebar, both modes)

### Animation

- **`tw-animate-css`** — Tailwind animation utilities (existing)
- **`motion`** (Framer Motion) — Sidebar active indicator, tab underlines, dialog enter/exit, page transitions. Import from `motion/react`.

## Key Conventions

### Route Groups

- `(auth)/` — Sign-in/sign-up pages using Clerk components
- `(app)/` — Authenticated routes, wrapped with Clerk protection
- Landing page is at `app/page.tsx` (root level, no route group)

All authenticated routes are org-scoped under `(app)/org/[slug]/`.

### Path Aliases

- `@/*` maps to project root (configured in `tsconfig.json`)
- Import as `@/components/ui/button`, `@/lib/utils`, `@/hooks/use-something`

### Component Conventions

- Prefer React Server Components (RSC) by default — only add `"use client"` when needed
- Feature components go in `components/{feature}/` (e.g., `components/projects/`)
- Marketing page sections go in `components/marketing/`
- Shared UI primitives in `components/ui/` — **these have been customized** (see below)
- Custom reusable components outside `ui/` should follow Shadcn patterns (forwardRef, CVA variants, cn())

### Shadcn UI — Customized Components

The `components/ui/` directory started from Shadcn scaffolding but **base components have been customized** with project-specific variants and slate styling. Key customizations:

- **Button** — Pill-shaped (`rounded-full`) primary/accent/soft/destructive variants. `soft` and `plain` variants added. Accent uses `bg-teal-600`.
- **Badge** — Semantic variants: `lead`, `member`, `owner`, `admin`, `starter`, `pro`, `success`, `warning`, `destructive`, `neutral`.
- **Card** — Slate borders, `shadow-sm` lift, `rounded-lg` (sharp, structured). Hover elevation on interactive cards.
- **Input / Textarea** — Slate border, `focus-visible:ring-slate-500` focus ring.
- **Dialog / AlertDialog** — `bg-slate-950/25` backdrop, `rounded-xl`, Motion enter/exit animations.

**When adding new Shadcn components** via `npx shadcn@latest add <component>`, review the generated output and adjust to match the slate color scheme (replace any neutral/zinc/gray references with slate equivalents, any indigo with teal).

### Shadcn Config

- Style: **new-york**
- RSC-enabled: `true`
- Icon library: **lucide** (`lucide-react`)
- CSS variables: enabled
- Config in `components.json`

### Styling

- **Tailwind CSS v4** — CSS-first config, no `tailwind.config.ts`
- PostCSS via `@tailwindcss/postcss` plugin
- CSS variables for theming in `app/globals.css`
- Use `cn()` from `@/lib/utils` to merge Tailwind classes (clsx + tailwind-merge)
- Slate color classes: `bg-slate-50` through `bg-slate-950`, `text-slate-*`, `border-slate-*`
- Teal accent classes: `bg-teal-500`, `bg-teal-600`, `text-teal-*`

### TypeScript

- Strict mode enabled
- Module resolution: `bundler`
- Target: `ES2017`
- Use `interface` for component props, `type` for unions/utility types

### ESLint

- Flat config (`eslint.config.mjs`)
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- Run with `pnpm run lint`

### Testing

- **Vitest** with `@testing-library/react` and `happy-dom`
- Config in `vitest.config.ts` — needs `resolve.alias` for `@/*` path alias
- Test files: colocated (`*.test.tsx`) or in `__tests__/` directory
- Radix UI components leak DOM between tests — always add `afterEach(() => cleanup())` in test files using Dialog/AlertDialog
- Use unique trigger text per test file to avoid "multiple elements found" errors
- If any tests are failing, make sure they pass before merging, even if you think they are existing issues

## Anti-Patterns — Never Do This

- Never use `@radix-ui/react-*` packages — use the unified `radix-ui` package instead
- Never put `"use client"` on a component unless it uses hooks, event handlers, or browser APIs
- Never call `auth()` in client components — it's server-only
- Never access `params` without `await` — Next.js 16 params are Promises
- Never skip `cssLayerName: "clerk"` on ClerkProvider — breaks Tailwind v4
- Never use `npm` — this project uses `pnpm`
- Never use olive/neutral/zinc/gray color classes — use the **slate** scale instead
- Never use indigo for accents — use **teal** instead
- Never import `motion` in server components — it's client-only. Only import in `"use client"` files.
- Never pass functions or component references as props from Server Components to `"use client"` components — Next.js 16 throws a runtime serialization error. Pass serializable data (strings, objects) instead.

### RSC Serialization Boundary

Next.js 16 strictly enforces that only plain serializable values (strings, numbers, booleans, plain objects, arrays) can be passed as props from Server Components to Client Components (`"use client"`).

**Never pass these from Server → Client Components:**
- Functions/callbacks (e.g., `onClick`, `generateHref`)
- React component references (e.g., `icon={Activity}` where `Activity` is a Lucide icon)
- Class instances

**Fix patterns:**
- **Icon props**: Remove `"use client"` if the component doesn't actually need client interactivity (no hooks, no event handlers, no browser APIs). `LucideIcon` components render fine in Server Components.
- **Callback props**: Replace function props with serializable data. E.g., instead of `generateHref={(id) => \`/path/${id}\`}`, pass `baseHref="/path"` and build the URL inside the client component.
- **Component props**: Pass pre-rendered `ReactNode` (JSX) instead of component references, or restructure so the icon renders in the Server Component parent.

## Next.js 16 Patterns

### Async Params (Breaking Change from 15)

Params are Promises in layouts and pages:

```tsx
export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
}
```

### Clerk + Tailwind v4 Compatibility

ClerkProvider requires cssLayerName to prevent CSS conflicts:

```tsx
<ClerkProvider appearance={{ cssLayerName: "clerk" }}>
```

### Radix UI Imports

Shadcn components use the bundled `radix-ui` package (not `@radix-ui/react-*`):

```tsx
import { Slot } from "radix-ui";
```

## Authentication (Clerk)

### Proxy (`proxy.ts`)

- `clerkMiddleware()` protects `(app)/**` routes
- `organizationSyncOptions` auto-activates org from URL slug pattern
- Patterns: `/org/:slug`, `/org/:slug/(.*)`
- Webhook route (`/api/webhooks/clerk`) excluded from auth (public route)

### ClerkProvider

- Wraps entire app in root `layout.tsx`
- Provides session context, org switching, user management

### JWT for Backend Calls

- Server components call `auth().getToken()` to get a Clerk JWT with org claims
- JWT passed as `Authorization: Bearer <token>` to Spring Boot API
- Claims: `sub` (user ID), `o.id` (org ID), `o.rol` (org role), `o.slg` (org slug) — Clerk JWT v2 nests under `"o"` map

### API Client (`lib/api.ts`)

- Centralized fetch wrapper that attaches Bearer JWT
- Points to `BACKEND_URL` (internal ALB in production)
- Handles error responses and token refresh

## Webhook Handler

Route: `app/api/webhooks/clerk/route.ts`

- Receives Clerk webhook events (POST)
- Verifies Svix signature via `verifyWebhook()` from `@clerk/nextjs/webhooks`
- On `organization.created`: forwards to Spring Boot `POST /internal/orgs/provision` with `X-API-KEY`
- On `organization.updated`/`deleted`: upsert/mark org metadata
- Membership events: sync to backend via `POST /internal/orgs/{id}/members/sync`
- Subscription events removed (Epic 28) — billing is self-managed

## Data Fetching

- **Server Components**: fetch data on the server, pass as props
- **Server Actions**: for mutations (form submissions, etc.)
- Backend API calls go through `lib/api.ts` with JWT auth
- Billing data: `GET /api/billing/subscription` returns plan, limits, member count
- S3 uploads: browser uploads directly via presigned URL from backend

### Paginated Responses (Spring Data VIA_DTO)

Backend paginated endpoints return this shape (not flat):
```json
{ "content": [...], "page": { "totalElements": 42, "totalPages": 3, "size": 20, "number": 0 } }
```

Frontend interfaces must nest pagination fields under `page`:
```typescript
interface PaginatedResponse<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}
```

## Environment Variables

| Variable                            | Side   | Description                     |
| ----------------------------------- | ------ | ------------------------------- |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Client | Clerk public key                |
| `CLERK_SECRET_KEY`                  | Server | Clerk backend key               |
| `CLERK_WEBHOOK_SIGNING_SECRET`      | Server | Svix webhook verification       |
| `BACKEND_URL`                       | Server | Spring Boot internal ALB URL    |
| `INTERNAL_API_KEY`                  | Server | API key for `/internal/*` calls |

Variables prefixed `NEXT_PUBLIC_` are exposed to the browser. All others are server-only.
