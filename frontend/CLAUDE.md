# Frontend CLAUDE.md

Next.js 16 (App Router) / React 19 / TypeScript 5 frontend for a multi-tenant B2B SaaS platform. Auth via Clerk, UI via Shadcn (new-york style) + Tailwind CSS v4.

## Build & Run

```bash
pnpm install         # Install dependencies
pnpm dev             # Dev server on port 3000
pnpm run build       # Production build
pnpm run lint        # ESLint (flat config)
```

## Project Structure

```
frontend/
├── app/
│   ├── (marketing)/                  # Public pages (landing, pricing)
│   │   └── page.tsx
│   ├── (auth)/                       # Clerk auth pages
│   │   ├── sign-in/[[...sign-in]]/page.tsx
│   │   └── sign-up/[[...sign-up]]/page.tsx
│   ├── (app)/                        # Authenticated app shell
│   │   └── org/[slug]/
│   │       ├── layout.tsx            # Org-scoped layout
│   │       ├── dashboard/page.tsx
│   │       ├── projects/
│   │       │   ├── page.tsx          # Project list
│   │       │   └── [id]/page.tsx     # Project detail + documents
│   │       ├── team/page.tsx
│   │       └── settings/page.tsx
│   ├── api/
│   │   └── webhooks/
│   │       └── clerk/route.ts        # Clerk webhook handler
│   ├── globals.css                   # Tailwind v4 + Shadcn CSS variables
│   └── layout.tsx                    # Root layout with ClerkProvider
├── components/
│   ├── ui/                           # Shadcn UI components (generated)
│   ├── projects/                     # Project-specific components
│   ├── documents/                    # Document-specific components
│   └── team/                         # Team management components
├── lib/
│   ├── api.ts                        # Spring Boot API client (attaches Bearer JWT)
│   └── utils.ts                      # cn() helper from Shadcn
├── hooks/                            # Custom React hooks
├── middleware.ts                     # Clerk middleware with org sync
├── components.json                   # Shadcn UI config
├── next.config.ts
├── tsconfig.json
├── eslint.config.mjs                 # ESLint flat config
└── postcss.config.mjs
```

## Key Conventions

### Route Groups

- `(marketing)/` — Public pages, no auth required
- `(auth)/` — Sign-in/sign-up pages using Clerk components
- `(app)/` — Authenticated routes, wrapped with Clerk protection

All authenticated routes are org-scoped under `(app)/org/[slug]/`.

### Path Aliases

- `@/*` maps to project root (configured in `tsconfig.json`)
- Import as `@/components/ui/button`, `@/lib/utils`, `@/hooks/use-something`

### Component Conventions

- Prefer React Server Components (RSC) by default — only add `"use client"` when needed
- Feature components go in `components/{feature}/` (e.g., `components/projects/`)
- Shared UI primitives in `components/ui/` (Shadcn-managed, don't edit directly)
- Custom reusable components outside `ui/` should follow Shadcn patterns (forwardRef, CVA variants, cn())

### Styling

- **Tailwind CSS v4** — CSS-first config, no `tailwind.config.ts`
- PostCSS via `@tailwindcss/postcss` plugin
- CSS variables for theming in `app/globals.css`
- Use `cn()` from `@/lib/utils` to merge Tailwind classes (clsx + tailwind-merge)
- Animation via `tw-animate-css`
- Fonts: Geist Sans (`--font-geist-sans`) and Geist Mono (`--font-geist-mono`) via `next/font/google`

### Shadcn UI

- Style: **new-york**
- RSC-enabled: `true`
- Icon library: **lucide** (`lucide-react`)
- Base color: **neutral**
- CSS variables: enabled
- Add components via: `npx shadcn@latest add <component>`
- Config in `components.json`

### TypeScript

- Strict mode enabled
- Module resolution: `bundler`
- Target: `ES2017`
- Use `interface` for component props, `type` for unions/utility types

### ESLint

- Flat config (`eslint.config.mjs`)
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- Run with `pnpm run lint`

## Anti-Patterns — Never Do This

- Never use `@radix-ui/react-*` packages — use the unified `radix-ui` package instead
- Never put `"use client"` on a component unless it uses hooks, event handlers, or browser APIs
- Never call `auth()` in client components — it's server-only
- Never access `params` without `await` — Next.js 16 params are Promises
- Never skip `cssLayerName: "clerk"` on ClerkProvider — breaks Tailwind v4
- Never use `npm` — this project uses `pnpm`

## Next.js 16 Patterns

### Async Params (Breaking Change from 15)

Params are Promises in layouts and pages:
```tsx
export default async function Page({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
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

### Middleware (`middleware.ts`)

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
- Claims: `sub` (user ID), `org_id`, `org_role`, `org_slug`

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
- Membership/invitation events: no-op stubs for MVP
- Deduplication via `svix-id` header

## Data Fetching

- **Server Components**: fetch data on the server, pass as props
- **Server Actions**: for mutations (form submissions, etc.)
- Backend API calls go through `lib/api.ts` with JWT auth
- S3 uploads: browser uploads directly via presigned URL from backend

## Environment Variables

| Variable                            | Side   | Description                     |
| ----------------------------------- | ------ | ------------------------------- |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Client | Clerk public key                |
| `CLERK_SECRET_KEY`                  | Server | Clerk backend key               |
| `CLERK_WEBHOOK_SIGNING_SECRET`      | Server | Svix webhook verification       |
| `BACKEND_URL`                       | Server | Spring Boot internal ALB URL    |
| `INTERNAL_API_KEY`                  | Server | API key for `/internal/*` calls |

Variables prefixed `NEXT_PUBLIC_` are exposed to the browser. All others are server-only.
