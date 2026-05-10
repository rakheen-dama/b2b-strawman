# `frontend/` repo

The Next.js 16 staff workspace. Per-repo entry point — for module-level detail
follow the anchors out into `30-modules/` and the discovery report.

## 1. Role

Staff workspace for firm owners, admins, and members. Runs on **port 3000**
(via gateway 8443 in Keycloak production mode; direct to backend 8081 in
mock-auth E2E mode). Next.js 16 App Router, React 19, TypeScript 5,
Tailwind v4, Shadcn UI. **Single fetch client** at
`→ frontend/lib/api/client.ts:72`; no per-feature fetch wrappers exist.
**Vertical branching at runtime**, driven by `OrgSettings.verticalProfile` →
`OrgProfileProvider` → `TerminologyProvider` + `ModuleGate` (see
`20-cross-cutting/multi-vertical.md`).

## 2. Tech stack

- **Framework:** Next.js 16.2.4 (App Router, RSC-first, `output: "standalone"`)
- **Runtime:** React 19.2.3, TypeScript 5.9 (strict, `bundler` resolution)
- **Styling:** Tailwind CSS v4 (CSS-first — no `tailwind.config.ts`; tokens
  defined as OKLCH custom properties in `→ frontend/app/globals.css`)
- **UI primitives:** Shadcn (new-york style) over the unified `radix-ui`
  package (never `@radix-ui/react-*`)
- **Forms:** Zod 4 + react-hook-form + `@hookform/resolvers`
- **Data:** SWR 2 for client-side; server actions + `lib/api/client.ts` on
  the server
- **Animation:** `motion/react` (Framer Motion) — client-only
- **Icons:** `lucide-react`
- **Rich text:** Tiptap 3 (templates, clauses, document editor)
- **Testing:** Vitest 4 + Testing Library + happy-dom (unit);
  Playwright 1.59 (E2E)
- **Package manager:** pnpm (never npm)

## 3. Discovery report

Full structural map: `→ kazi-architecture/_discovery/A2-frontend-map.md`
(directory tree, ~68 pages, endpoint inventory, type catalogue, vertical
signals, component conventions).

## 4. Top-level directory map

| Dir | Role |
|---|---|
| `app/` | App Router routes — three groups: `(app)` authenticated shell, `(mock-auth)` E2E login, root public page → `frontend/app/` |
| `components/` | `ui/` Shadcn primitives (customised) + per-feature dirs (`customers/`, `invoices/`, `trust/`, `legal/`, `automations/`, `assistant/`, etc.) → `frontend/components/` |
| `lib/` | Single fetch client (`api/client.ts`), auth abstraction (`auth/`), domain types (`types/`), Zod schemas (`schemas/`), server actions (`actions/`), nav definitions (`nav-items.ts`), capability/terminology providers → `frontend/lib/` |
| `hooks/` | Client-side hooks (`use-assistant-chat.ts`, SWR helpers) → `frontend/hooks/` |
| `__tests__/` | Vitest unit tests (colocated `*.test.tsx` also allowed) → `frontend/__tests__/` |
| `e2e/` | Playwright configs and specs (lifecycle, legal-depth-ii, etc.) → `frontend/e2e/` |
| `public/` | Static assets → `frontend/public/` |
| `proxy.ts` | Next.js middleware entry, delegates to `lib/auth/middleware.ts` → `frontend/proxy.ts` |

## 5. Build & run commands

```bash
pnpm install                                    # install deps
pnpm dev                                        # Keycloak mode, port 3000
NEXT_PUBLIC_AUTH_MODE=mock pnpm dev             # mock-auth dev (E2E shape)
pnpm lint && pnpm build && pnpm test            # merge bar — full suite, no path narrowing
pnpm test:e2e                                   # Playwright E2E
bash compose/scripts/svc.sh restart frontend    # agent-managed restart
```

HMR picks up TypeScript changes automatically — no rebuild needed for source
edits. The merge bar is non-negotiable per root `CLAUDE.md` quality gates
(§1 Build & test bar): full vitest, never `--testPathPattern` narrowed.

## 6. Test stack

- **Unit:** Vitest + Testing Library (`@testing-library/react` + `happy-dom`),
  config in `→ frontend/vitest.config.ts` with `@/*` alias.
  Radix Dialog/AlertDialog tests must `afterEach(() => cleanup())` — see
  `frontend/CLAUDE.md` Testing section.
- **E2E:** Playwright, multiple configs:
  `e2e/playwright.config.ts`, `playwright.legal-lifecycle.config.ts`,
  `playwright.legal-depth-ii.config.ts`. Drives the mock-auth Docker stack
  (port 3001 / backend 8081) — see `agent-e2e-stack.md` in user memory.
- Full vitest at the merge bar; no path-narrowed runs (per root `CLAUDE.md`
  §5 Test scoping).

## 7. Deployment unit

Docker image built from `frontend/Dockerfile`; `next.config.ts` sets
`output: "standalone"` so the image ships only the standalone server bundle.
Runs on **port 3000** (compose service `b2b-frontend`).

- **Keycloak mode (production / `pnpm dev` default):** browser → port 3000
  → middleware → gateway 8443 (BFF) → backend 8080. SESSION cookie carries
  identity; `X-XSRF-TOKEN` on mutations. Token never reaches JS.
- **Mock mode (`NEXT_PUBLIC_AUTH_MODE=mock`):** browser → port 3001 → mock
  IDP cookie → backend 8081 directly. JWT in `mock-auth-token` cookie.

One legacy redirect lives in `next.config.ts`:
`/org/:slug/settings/team` → `/org/:slug/team` (HTTP 301).

## 8. Most-edited / hottest areas

- `app/(app)/org/[slug]/dashboard/` — Phase 53/65/70 polish, KPI strip,
  module-gated widgets (court dates, sensitive events) →
  `frontend/app/(app)/org/[slug]/dashboard/page.tsx`
- `app/(app)/org/[slug]/trust-accounting/*` — legal vertical UI (overview,
  transactions, client ledgers, reconciliation, interest, investments,
  reports). Module-gated on `trust_accounting`, capability-gated on
  `VIEW_TRUST` → see `60-verticals/legal-za.md`
- `app/(app)/org/[slug]/settings/*` — many sub-pages (general, billing,
  notifications, rates, tax, custom-fields, automations/ai-queue,
  trust-accounting, integrations, features, …) → 25+ pages, frequent change
- `components/assistant/` — AI assistant panel, SSE streaming chat,
  ToolUseCard / ConfirmationCard / ToolResultCard surfaces →
  `frontend/components/assistant/`; hook at
  `→ frontend/hooks/use-assistant-chat.ts:128`
- `components/automations/` — Phase 37+ automation rule editor, AI queue
  surfacing → `frontend/components/automations/`
- `lib/api/` — every domain wrapper sits here; barrel at
  `→ frontend/lib/api/index.ts`. New endpoints land here, never as a
  per-feature fetch helper.
- `lib/auth/` — provider abstraction (mock vs Keycloak); the *only* place
  auth mode branches → `frontend/lib/auth/server.ts:19`,
  `frontend/lib/auth/providers/keycloak-bff.ts:35`,
  `frontend/lib/auth/providers/mock/server.ts`

## 9. Profile / environment quirks

| Variable | Side | Meaning |
|---|---|---|
| `NEXT_PUBLIC_AUTH_MODE` | client | `keycloak` (default, prod + local dev) or `mock` (E2E only). The single auth-mode switch. |
| `NEXT_PUBLIC_GATEWAY_URL` | client | Public gateway URL (Keycloak mode) |
| `GATEWAY_URL` | server | Server-side BFF target (Keycloak mode) |
| `BACKEND_URL` | server | Spring Boot base URL (mock mode + internal API) |
| `INTERNAL_API_KEY` | server | Key for `/internal/*` calls from `lib/internal-api.ts` |

Local-machine quirks (per user memory):

- `NODE_OPTIONS=--openssl-legacy-provider` may be set in the user shell —
  **clear it** with `NODE_OPTIONS=""` before `pnpm dev`/`pnpm build`,
  Next.js 16 rejects the flag.
- pnpm lives at `/opt/homebrew/bin/pnpm`; agent invocations may need
  `SHELL=/bin/bash` prefix or `--dir` flag (zoxide alias breaks `cd`).
- `NEXT_PUBLIC_PORTAL_API_URL` is **not** a frontend variable — it belongs
  to the portal repo (`portal/`); see `70-repos/portal.md`.

## 10. Reference modules

The frontend touches every module page in `30-modules/` — there is no
clean 1:1 mapping. The authoritative pointer is the bounded-contexts page:

- Module-by-module entry: `→ kazi-architecture/10-bounded-contexts.md`
- Auth + multi-tenancy seam: `→ kazi-architecture/20-cross-cutting/auth.md`,
  `→ kazi-architecture/20-cross-cutting/multitenancy.md`
- Vertical branching seam (terminology + module gates):
  `→ kazi-architecture/20-cross-cutting/multi-vertical.md`
- Sibling repo entrypoints: `→ kazi-architecture/70-repos/backend.md`,
  `→ kazi-architecture/70-repos/gateway.md`,
  `→ kazi-architecture/70-repos/portal.md`

Conventions for editing this page: `→ kazi-architecture/99-conventions.md`
(repo-page budget ≤ 300 lines, anchors required for structural claims).
