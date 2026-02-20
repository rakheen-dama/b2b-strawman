# ADR-076: Separate Next.js App for Customer Portal

**Status**: Accepted
**Date**: 2026-02-20

**Context**:

The customer portal needs a production frontend. The existing `frontend/` app is a Next.js 16 application authenticated via Clerk, with a sidebar-based layout, org-scoped routing (`/org/[slug]/*`), and server-side data fetching using Clerk session tokens. The portal has fundamentally different requirements: magic link authentication (no Clerk), a simplified header-only navigation (no sidebar), org branding via CSS custom properties, and client-side data fetching via portal JWTs stored in localStorage.

The question is whether to build the portal UI within the existing `frontend/` app (as a route group with separate middleware) or as a standalone Next.js application in the monorepo.

**Options Considered**:

1. **Separate Next.js app in `portal/` (chosen)** -- A new Next.js 16 application at the monorepo root, alongside `frontend/` and `backend/`. Own `package.json`, build config, and Dockerfile. Shadcn UI components duplicated from `frontend/`.
   - Pros: Complete auth isolation (no Clerk middleware interference); independent deployment and scaling; clear security boundary (portal app cannot accidentally expose admin routes or data); simpler build pipeline (no conditional middleware logic); each app has a focused, coherent layout system; independent feature velocity (portal changes don't risk admin regressions).
   - Cons: Shadcn UI component duplication (~20-30 small files); separate `node_modules` and build; two Docker images to deploy; design token changes must be applied in both apps.

2. **Route group within existing `frontend/`** -- Add `frontend/app/(portal)/*` route group with separate layout, middleware branching based on path prefix.
   - Pros: No component duplication; shared `node_modules` and build; single Docker image; design tokens automatically shared.
   - Cons: Middleware complexity (must branch between Clerk auth for admin routes and portal JWT auth for portal routes, with different redirect targets); risk of admin-only code leaking into portal bundle; single deployment unit means portal changes require full frontend deployment; layout system conflicts (sidebar vs. no-sidebar); increased bundle size for portal users who don't need admin components; Clerk SDK loaded for all routes even though portal doesn't use it.

3. **Shared package monorepo with Turborepo** -- Extract shared components into a `packages/ui/` workspace, with `frontend/` and `portal/` as separate apps consuming the shared package.
   - Pros: No component duplication; explicit dependency management; supports future apps (e.g., mobile web shell); industry-standard monorepo pattern.
   - Cons: Significant infrastructure overhead (Turborepo config, workspace dependencies, shared package versioning); premature abstraction (only two consumers, and both are stable); increased build complexity; shared package changes trigger rebuilds of both apps; debugging cross-package issues is harder; adds a learning curve for contributors.

**Decision**: Option 1 -- separate Next.js app in `portal/`.

**Rationale**:

The fundamental driver is **auth isolation**. The admin frontend uses Clerk with server-side session management, RBAC middleware, and org-scoped routing. The portal uses magic link JWTs stored client-side with no server-side session. Combining these two auth systems in a single Next.js app would require complex middleware branching -- every request would need to determine which auth system applies based on the URL path. This is fragile and a security risk: a middleware bug could apply the wrong auth check to the wrong route.

The component duplication cost is low. Shadcn UI components are small, self-contained files (typically 20-80 lines each). The portal needs perhaps 15-20 of the ~30 components in the admin app. These files change infrequently -- they are generated from the Shadcn CLI and rarely modified. The time cost of copying them is measured in minutes, not hours.

The Turborepo option (Option 3) solves the duplication problem but introduces infrastructure complexity that is not justified by two apps sharing a few dozen small files. If a third frontend app is added in the future, the shared package approach becomes worthwhile. Until then, duplication is the simpler path.

**Consequences**:

- Positive:
  - Clean auth boundary -- Clerk never loads in the portal, portal JWT logic never runs in the admin app
  - Independent deployment -- portal can be deployed without touching the admin frontend
  - Simpler reasoning -- each app has one auth system, one layout system, one purpose
  - Security isolation -- a bug in the portal cannot expose admin routes or data
  - Independent scaling -- portal traffic patterns differ from admin traffic

- Negative:
  - Shadcn UI components are duplicated (~20-30 files, ~1500 lines total; mitigated by their stability and small size)
  - Design token changes (colors, fonts) must be applied to both `frontend/tailwind.config.ts` and `portal/tailwind.config.ts` (mitigated by infrequent changes and grep-ability)
  - Two Docker images to build and deploy (mitigated by identical Dockerfile patterns and shared CI pipeline)
