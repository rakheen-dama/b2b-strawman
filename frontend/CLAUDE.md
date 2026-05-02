# Frontend CLAUDE.md

Next.js 16 (App Router) / React 19 / TypeScript 5 frontend for a multi-tenant B2B SaaS platform. Auth via Keycloak (production) or mock provider (E2E), UI via Shadcn (new-york style) + Tailwind CSS v4. Custom "Signal Deck" design system — precise, high-contrast, information-forward aesthetic with cool slate palette and teal accents.

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
│   ├── (mock-auth)/                  # Mock auth pages (E2E only)
│   ├── api/                          # API routes
│   ├── globals.css                   # Tailwind v4 + slate color tokens
│   └── layout.tsx                    # Root layout with AuthProvider
├── components/
│   ├── ui/                           # Shadcn UI components (customized — see below)
│   ├── projects/                     # Project-specific components
│   ├── documents/                    # Document-specific components
│   ├── team/                         # Team management components
│   ├── billing/                      # Subscription lifecycle, payment history
│   ├── marketing/                    # Landing page section components
│   ├── desktop-sidebar.tsx           # Dark slate-950 sidebar with Motion indicator
│   ├── mobile-sidebar.tsx            # Sheet-based mobile sidebar
│   └── breadcrumbs.tsx               # Pathname-based breadcrumb nav
├── lib/
│   ├── auth/                         # Auth abstraction layer (Keycloak + mock providers)
│   │   ├── index.ts                  # Public API re-exports
│   │   ├── types.ts                  # AuthContext type definition
│   │   ├── server.ts                 # Server-side getAuthContext()
│   │   ├── middleware.ts             # Auth middleware factory
│   │   ├── providers/               # Provider implementations
│   │   │   ├── keycloak-bff.ts      # Keycloak BFF provider (production)
│   │   │   └── mock/server.ts       # Mock provider (E2E)
│   │   └── client/                  # Client-side auth hooks + context
│   │       ├── auth-provider.tsx    # React context provider
│   │       ├── hooks.ts             # useAuth() hook
│   │       └── cookie-util.ts       # Session cookie helpers
│   ├── api.ts                        # Spring Boot API client (attaches Bearer JWT)
│   ├── internal-api.ts               # Types for internal/billing API
│   ├── nav-items.ts                  # Sidebar navigation item definitions
│   └── utils.ts                      # cn() helper from Shadcn
├── hooks/                            # Custom React hooks
├── __tests__/                        # Test files
├── proxy.ts                         # Auth middleware entry point (delegates to lib/auth/middleware)
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

| Role         | Font               | CSS Variable     | Usage                                                                                |
| ------------ | ------------------ | ---------------- | ------------------------------------------------------------------------------------ |
| Display      | **Sora**           | `--font-display` | h1, hero text, headings — geometric, sharp, tight tracking. Use `font-display` class |
| Body / UI    | **IBM Plex Sans**  | `--font-sans`    | Paragraphs, buttons, labels, table text — engineered precision                       |
| Code / Stats | **JetBrains Mono** | `--font-mono`    | Code blocks, KPI numbers, inline stats. Use `font-mono tabular-nums` for data        |

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

- `(app)/` — Authenticated routes, protected by auth middleware
- `(mock-auth)/` — Mock login page for E2E testing only
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
- Never call `getAuthContext()` in client components — it's server-only; use `useAuth()` hook instead
- Never access `params` without `await` — Next.js 16 params are Promises
- Never import from `@clerk/nextjs` — Clerk has been fully removed; use `lib/auth/` abstraction
- Never use `npm` — this project uses `pnpm`
- Never use olive/neutral/zinc/gray color classes — use the **slate** scale instead
- Never use indigo for accents — use **teal** instead
- Never import `motion` in server components — it's client-only. Only import in `"use client"` files.
- Never pass functions or component references as props from Server Components to `"use client"` components — Next.js 16 throws a runtime serialization error. Pass serializable data (strings, objects) instead.
- Never place two `<DialogTrigger asChild>` / `<AlertDialogTrigger asChild>` Radix siblings adjacent in the same parent JSX — they collide under React 19 + Radix `Slot` reconciliation and only one trigger wires up its `onClick`. See _Dialog Trigger Composition_ below.

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

### Dialog Trigger Composition

When a dialog or alert-dialog renders next to another `<*Trigger asChild>` sibling in the same parent JSX block (e.g., an Edit + Delete action pair in a table row), **the dialog component must own its trigger button**: render a plain `<Button>` directly inside `<Dialog>` / `<AlertDialog>` and accept `triggerLabel` / `triggerVariant` / `triggerSize` / `triggerClassName` / `triggerIcon` props from the consumer. Do not use `<DialogTrigger asChild>` or `<AlertDialogTrigger asChild>` to wrap a consumer-supplied child at adjacency sites.

**Why (Class-3 / OBS-2103):** two adjacent `<*Trigger asChild>{children}</*Trigger>` siblings collide under React 19 + Radix `Slot` reconciliation. Both call `cloneElement` on the inner child at the same unkeyed sibling position, so only one trigger wires up its `onClick`. Symptom: visible button, unresponsive click. Eliminating the `Slot` composition at the call site eliminates the bug class by elimination, not detection.

✅ **Good — dialog owns its button:**

```tsx
interface EditCustomerDialogProps {
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
  /* ...domain props */
}

export function EditCustomerDialog({
  triggerLabel,
  triggerVariant = "outline",
  triggerSize = "sm",
  triggerClassName,
  triggerIcon,
}: EditCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        onClick={() => setOpen(true)}
      >
        {triggerIcon}
        {triggerLabel}
      </Button>
      <DialogContent>{/* ... */}</DialogContent>
    </Dialog>
  );
}
```

Call site: `<EditCustomerDialog triggerLabel="Edit" triggerIcon={<Pencil />} customer={c} slug={s} />`. Both the dialog and its call site are Client Components — adjacency contexts (interactive table rows) are inherently `"use client"`, so passing pre-rendered `ReactNode` JSX is fine here. See _RSC Serialization Boundary_ above for the Server → Client cases that aren't.

❌ **Bad — two adjacent `asChild` triggers in the same parent JSX:**

```tsx
// Each wrapper internally renders <*Trigger asChild>{children}</*Trigger>.
// Two of them side by side trigger the OBS-2103 collision.
<div className="flex justify-end gap-1">
  <EditFooDialog foo={f}>
    <Button size="sm">Edit</Button>
  </EditFooDialog>
  <DeleteFooDialog fooId={f.id}>
    <Button size="sm" variant="destructive">
      Delete
    </Button>
  </DeleteFooDialog>
</div>
```

**Children-API dialogs are still acceptable when there is at most one `asChild` Trigger in the adjacency** (e.g., `LogExpenseDialog` placed next to a non-`Slot` sibling). The collision only fires with two adjacent `Slot` siblings; a single `Slot` at a row position is fine.

Precedent: PR #1242 introduced the dialog-owns-button pattern (`EditCustomerDialog`, `ArchiveCustomerDialog`). PR #1263 propagated it to four more adjacency sites (`comments/`, `rates/customer-rates-tab.tsx`, `rates/project-rates-tab.tsx`, `expenses/`). PR #1262 removed a related dead `mountRoot` workaround that the same composition class had motivated.

## Next.js 16 Patterns

### Async Params (Breaking Change from 15)

Params are Promises in layouts and pages:

```tsx
export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
}
```

### Radix UI Imports

Shadcn components use the bundled `radix-ui` package (not `@radix-ui/react-*`):

```tsx
import { Slot } from "radix-ui";
```

## Authentication

Auth is provider-agnostic via the `lib/auth/` abstraction layer. The active provider is selected by `NEXT_PUBLIC_AUTH_MODE`:

| Mode       | Provider                     | Usage                         |
| ---------- | ---------------------------- | ----------------------------- |
| `keycloak` | Keycloak BFF via API Gateway | Production, local dev         |
| `mock`     | Mock IDP (cookie-based)      | E2E testing, agent navigation |

### Auth Architecture

- **`proxy.ts`** — Entry point for Next.js middleware, delegates to `lib/auth/middleware.ts`
- **`lib/auth/server.ts`** — `getAuthContext()` returns an `AuthContext` (userId, orgId, orgRole, orgSlug, token) regardless of provider
- **`lib/auth/client/`** — `AuthProvider` React context + `useAuth()` hook for client components
- **`lib/auth/providers/`** — Provider implementations (keycloak-bff, mock)

### API Client (`lib/api.ts`)

- Centralized fetch wrapper that attaches Bearer JWT from auth context
- In Keycloak mode, routes through the API Gateway (`GATEWAY_URL`)
- Handles error responses and token refresh

## Data Fetching

- **Server Components**: fetch data on the server, pass as props
- **Server Actions**: for mutations (form submissions, etc.)
- Backend API calls go through `lib/api.ts` with JWT auth
- Billing data: `GET /api/billing/subscription` returns plan, limits, member count
- S3 uploads: browser uploads directly via presigned URL from backend

### Client Data Fetching (SWR)

For client components that need to fetch data (dialogs, sheets, polling), use [SWR](https://swr.vercel.app/) instead of manual `useEffect` + `useState` patterns. SWR provides caching, deduplication, revalidation on focus, and error retry out of the box.

**When to use SWR vs Server Components:**

- **Server Components** (default): Page-level data, initial renders, SEO-critical content. Data fetched on the server via `lib/api.ts`.
- **SWR**: Client-side data needs — dialogs that fetch on open, polling (notifications), data that refreshes without page navigation, sheets/panels that load data lazily.

**Pattern — wrapping server actions with SWR:**

```tsx
import useSWR from "swr";
import { fetchSomeData } from "@/lib/actions/some-action";

// Conditional fetch (dialog): pass null key when closed to pause fetching
const { data, error, isLoading, mutate } = useSWR(open ? "unique-cache-key" : null, () =>
  fetchSomeData()
);

// Polling: use refreshInterval
const { data } = useSWR("unread-count", () => fetchUnreadCount(), {
  refreshInterval: 30_000,
});

// After a mutation, call mutate() to revalidate cached data
await mutate();
```

**Key conventions:**

- SWR fetcher functions call server actions (not direct API/fetch calls) — this preserves the server-side auth boundary
- Use `null` key to pause fetching (e.g., when a dialog is closed)
- Use unique, descriptive cache keys (e.g., `"comments-TASK-{id}"`, `"notification-unread-count"`)
- Utilities in `lib/swr/fetcher.ts`: `defaultSWROptions`, `conditionalKey()`

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

| Variable                  | Side   | Description                                            |
| ------------------------- | ------ | ------------------------------------------------------ |
| `NEXT_PUBLIC_AUTH_MODE`   | Client | Auth provider: `keycloak` (production) or `mock` (E2E) |
| `NEXT_PUBLIC_GATEWAY_URL` | Client | API Gateway URL (Keycloak mode)                        |
| `GATEWAY_URL`             | Server | API Gateway URL for server-side BFF calls              |
| `BACKEND_URL`             | Server | Spring Boot internal ALB URL                           |
| `INTERNAL_API_KEY`        | Server | API key for `/internal/*` calls                        |

Variables prefixed `NEXT_PUBLIC_` are exposed to the browser. All others are server-only.

**Auth mode:** Always `keycloak` in production. Set to `mock` only in the E2E Docker stack. See `.env.keycloak` for a complete local dev example.

## Form Patterns (Zod + React Hook Form)

Forms use **Zod** schemas for validation and **react-hook-form** with `@hookform/resolvers/zod` for state management. Shadcn `Form` components (`components/ui/form.tsx`) provide accessible field wrappers.

### Schema location

- `lib/schemas/` — one file per domain (e.g., `customer.ts`, `project.ts`, `invite-member.ts`)
- `lib/schemas/index.ts` — barrel re-exports

### Pattern

```tsx
// 1. Define schema in lib/schemas/<domain>.ts
import { z } from "zod";
export const createFooSchema = z.object({
  name: z.string().min(1, "Name is required").max(255),
  email: z.string().email("Invalid email").optional().or(z.literal("")),
});
export type CreateFooFormData = z.infer<typeof createFooSchema>;

// 2. Use in component
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
} from "@/components/ui/form";

const form = useForm<CreateFooFormData>({
  resolver: zodResolver(createFooSchema),
  defaultValues: { name: "", email: "" },
});

// 3. Wrap form in <Form {...form}> and use FormField/FormItem/FormControl/FormMessage
<Form {...form}>
  <form onSubmit={form.handleSubmit(onSubmit)}>
    <FormField
      control={form.control}
      name="name"
      render={({ field }) => (
        <FormItem>
          <FormLabel>Name</FormLabel>
          <FormControl>
            <Input {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  </form>
</Form>;
```

### Key rules

- Validation errors display inline via `<FormMessage />` — no manual `if (!name.trim())` checks
- When server actions expect `FormData`, build it from validated values (not from DOM)
- For multi-step wizards, use `form.trigger(["field1", "field2"])` to validate specific fields before advancing
- Keep schemas in `lib/schemas/` — never inline Zod schemas in components
