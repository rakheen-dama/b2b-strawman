# Frontend CLAUDE.md

Next.js 16 (App Router) / React 19 / TypeScript 5 frontend for a multi-tenant B2B SaaS platform. Auth via Clerk (default) or Keycloak (self-hosted), selectable via `NEXT_PUBLIC_AUTH_MODE`. UI via Shadcn (new-york style) + Tailwind CSS v4. Custom "Signal Deck" design system — precise, high-contrast, information-forward aesthetic with cool slate palette and teal accents.

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
│   ├── auth/                         # Auth abstraction layer (provider-agnostic)
│   │   ├── server.ts                 # Dispatches to clerk/keycloak/mock based on AUTH_MODE
│   │   ├── middleware.ts             # Auth middleware factory (Clerk/Keycloak/mock)
│   │   ├── types.ts                  # AuthContext, shared types
│   │   ├── utils.ts                  # Shared auth utilities
│   │   ├── index.ts                  # Public exports
│   │   ├── providers/
│   │   │   ├── clerk.ts              # Clerk server-side auth functions
│   │   │   ├── keycloak.ts           # Keycloak server-side auth functions (next-auth)
│   │   │   └── mock/                 # Mock auth provider (E2E testing)
│   │   └── client/
│   │       ├── auth-provider.tsx     # Conditional ClerkProvider / SessionProvider
│   │       ├── keycloak-context.tsx  # Keycloak SessionProvider wrapper
│   │       ├── mock-context.tsx      # Mock auth context
│   │       ├── hooks.ts             # useAuth, useOrganization hooks
│   │       └── index.ts
│   ├── api.ts                        # Spring Boot API client (attaches Bearer JWT)
│   ├── internal-api.ts               # Types for internal/billing API
│   ├── nav-items.ts                  # Sidebar navigation item definitions
│   └── utils.ts                      # cn() helper from Shadcn
├── hooks/                            # Custom React hooks
├── __tests__/                        # Test files
├── auth.ts                          # next-auth v5 config (Keycloak OIDC provider, token refresh)
├── proxy.ts                         # Clerk auth proxy with org sync
├── types/
│   └── next-auth.d.ts               # next-auth session type augmentation
├── components/
│   └── auth/                        # Auth-mode-specific components
│       ├── keycloak-create-org-form.tsx
│       ├── keycloak-sign-in.tsx
│       ├── mock-login-form.tsx
│       ├── mock-org-switcher.tsx
│       └── mock-user-button.tsx
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
- Never import `@clerk/nextjs` directly in feature code — use `lib/auth/server.ts` functions instead. Direct Clerk imports break Keycloak and mock modes.

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

## Authentication

Auth is provider-agnostic via `lib/auth/` abstraction layer. The active provider is selected by `NEXT_PUBLIC_AUTH_MODE` (build-time, tree-shakeable): `clerk` (default), `keycloak`, or `mock`.

### Auth Dispatch (`lib/auth/server.ts`)

All server-side auth calls go through five functions that dispatch to the active provider:
- `getAuthContext()` — Returns `{ userId, orgId, orgRole, orgSlug }` or redirects
- `getAuthToken()` — Returns Bearer token for backend API calls
- `getCurrentUserEmail()` — Returns current user's email
- `requireRole(role)` — Throws/redirects if role insufficient
- `hasPlan(plan)` — Checks billing plan

### Clerk Mode (default, `AUTH_MODE=clerk`)

- `clerkMiddleware()` in `proxy.ts` protects `(app)/**` routes
- `organizationSyncOptions` auto-activates org from URL slug pattern
- `ClerkProvider` wraps app in root `layout.tsx`
- Server components call `auth().getToken()` for JWTs
- Webhook route (`/api/webhooks/clerk`) handles org/member provisioning

### Keycloak Mode (`AUTH_MODE=keycloak`)

- **next-auth v5** (Auth.js) handles OIDC flows, session management, and token refresh
- Config in `auth.ts` (project root): Keycloak OIDC provider, JWT/session callbacks
- `SessionProvider` wraps app instead of `ClerkProvider`
- Route handler: `app/api/auth/[...nextauth]/route.ts`
- `createKeycloakMiddleware()` checks next-auth session cookie, redirects unauthenticated users
- Token refresh handled automatically via `refreshAccessToken()` in `auth.ts` callbacks
- Org selection via `kc_org` authorization parameter (Keycloak organization feature)
- **No webhooks** — provisioning is synchronous via `POST /api/orgs` (backend creates Keycloak org + tenant schema)
- **Invitation-only** — no public sign-up. First user creates org at `/create-org`, subsequent users invited by admins

### Keycloak UI Components

| Clerk Component | Keycloak Replacement | File |
|---|---|---|
| `<SignIn>` | Redirect to Keycloak login | `components/auth/keycloak-sign-in.tsx` |
| `<SignUp>` | Redirect to `/create-org` | `app/(auth)/sign-up/page.tsx` |
| `<CreateOrganization>` | Custom form → `POST /api/orgs` | `components/auth/keycloak-create-org-form.tsx` |
| `<UserButton>` | Custom dropdown | `components/auth-header-controls.tsx` |
| `<OrganizationSwitcher>` | Custom dropdown, re-auth with `kc_org` | `components/auth-header-controls.tsx` |
| `ClerkProvider` | `SessionProvider` (next-auth) | `lib/auth/client/auth-provider.tsx` |

### JWT for Backend Calls

- Server components call `getAuthToken()` from `lib/auth/server.ts` (provider-agnostic)
- JWT passed as `Authorization: Bearer <token>` to Spring Boot API
- Claims: `sub` (user ID), `o.id` (org ID), `o.rol` (org role), `o.slg` (org slug) — both Clerk and Keycloak produce this structure

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

### Common (all modes)

| Variable                            | Side   | Description                     |
| ----------------------------------- | ------ | ------------------------------- |
| `NEXT_PUBLIC_AUTH_MODE`             | Client | Auth provider: `clerk` (default), `keycloak`, or `mock` |
| `BACKEND_URL`                       | Server | Spring Boot internal ALB URL    |
| `INTERNAL_API_KEY`                  | Server | API key for `/internal/*` calls |

### Clerk Mode (`AUTH_MODE=clerk`, default)

| Variable                            | Side   | Description                     |
| ----------------------------------- | ------ | ------------------------------- |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Client | Clerk public key                |
| `CLERK_SECRET_KEY`                  | Server | Clerk backend key               |
| `CLERK_WEBHOOK_SIGNING_SECRET`      | Server | Svix webhook verification       |

### Keycloak Mode (`AUTH_MODE=keycloak`)

| Variable                            | Side   | Description                     |
| ----------------------------------- | ------ | ------------------------------- |
| `KEYCLOAK_CLIENT_ID`               | Server | OIDC client ID (`docteams-web`) |
| `KEYCLOAK_CLIENT_SECRET`           | Server | OIDC client secret              |
| `KEYCLOAK_ISSUER`                  | Server | Keycloak realm URL (e.g., `http://localhost:9090/realms/docteams`) |
| `KEYCLOAK_DEFAULT_ORG`            | Server | Optional: pre-select org during login (skips org selection screen) |
| `NEXTAUTH_SECRET`                  | Server | Random 32-char string for next-auth session encryption |
| `NEXTAUTH_URL`                     | Server | App URL for next-auth callbacks (e.g., `http://localhost:3000`) |

Variables prefixed `NEXT_PUBLIC_` are exposed to the browser. All others are server-only.
