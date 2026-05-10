# `portal/` repo

End-customer-facing Next.js app. **Separate deployable from the staff `frontend/`** — different trust boundary, different auth stack, no shared code path. See `30-modules/customer-portal.md` for the backend side of the bounded context.

## Role

The customer portal lets a tenant org's external clients sign in, see their projects/invoices/proposals/info-requests/trust/retainer/deadlines, and act on a small write surface (comments, accepts, uploads). Runs on **port 3002**. Auth is **magic-link → portal JWT → `localStorage`** `→ portal/lib/auth.ts:52` — not Keycloak. Calls the Spring Boot backend **directly** at `NEXT_PUBLIC_PORTAL_API_URL` (default `http://localhost:8080`); **bypasses the gateway entirely** `→ portal/lib/api-client.ts:4`. The trust boundary is hard: no shared transit path with the staff frontend (ADR-076).

## Tech stack

- Next.js 16 (App Router, `output: "standalone"`) `→ portal/next.config.ts:3`
- React 19, TypeScript 5
- Tailwind CSS v4 + `tw-animate-css`, Concrete-Studio palette in `app/globals.css`
- **Shadcn UI installed independently** in this repo (`components.json` + `components/ui/`) — verified separate from `frontend/components/ui/`; no shared package
- Radix UI primitives (`radix-ui` umbrella)
- `jose` for JWT decode (expiry check) `→ portal/lib/auth.ts:76`
- `lucide-react` icons
- Same Sora / IBM Plex / JetBrains Mono fonts as staff frontend (loaded in `app/layout.tsx`)
- pnpm workspace; vitest + happy-dom for unit; Playwright for portal-specific E2E

`→ portal/package.json`

## Discovery report

- `_discovery/A3-portal-gateway-map.md` (Portal App section, §1–§6 + §11–§12)

## Top-level directory map

| Path | Purpose |
|---|---|
| `app/layout.tsx` | Root HTML shell; loads Sora/Plex/JetBrains fonts; brand CSS variables `→ portal/app/layout.tsx` |
| `app/(authenticated)/` | Route group — auth-guarded pages (home, projects, invoices, proposals, requests, deadlines, trust, retainer, activity, profile) `→ portal/app/(authenticated)/` |
| `app/auth/exchange/` | Magic-link token exchange page (no auth required) `→ portal/app/auth/exchange/page.tsx:41` |
| `app/login/` | Email + orgId form; requests magic link `→ portal/app/login/page.tsx:109` |
| `app/accept/[token]/` | Token-gated public document acceptance flow (no portal session) `→ portal/app/accept/[token]/` |
| `app/not-found.tsx` | 404 |
| `components/` | Portal-specific UI: `portal-sidebar`, `portal-topbar`, `portal-header`, `portal-footer`, `branding-provider`, `comment-section`, `document-list`, `invoice-line-table`, `invoice-status-badge`, `pending-acceptances-list`, `project-card`, `proposal-status-badge`, `task-list`, `trust/`, `retainer/`, `deadlines/`, `ui/` `→ portal/components/` |
| `hooks/` | `use-auth.ts`, `use-portal-context.ts`, `use-branding.ts`, `use-payment-status.ts` `→ portal/hooks/` |
| `lib/auth.ts` | JWT `localStorage` store; expiry logic `→ portal/lib/auth.ts:52` |
| `lib/api-client.ts` | `portalFetch` / `publicFetch`; bearer injection; 401 → clearAuth + hard redirect `→ portal/lib/api-client.ts:16` |
| `lib/types.ts` | Portal-side response types |
| `lib/terminology-map.ts` | Vertical terminology overrides (consulting-za, accounting-za, legal-za) `→ portal/lib/terminology-map.ts:1` |
| `lib/terminology.tsx` | `TerminologyProvider` + `useTerminology()` |
| `lib/nav-items.ts` | `PORTAL_NAV_ITEMS` + `filterNavItems()` (profile/module gating) `→ portal/lib/nav-items.ts:32` |
| `lib/format.ts` | Currency, date, number formatting |
| `lib/brand.ts` | Brand-color CSS variable application |
| `lib/utils.ts` | `cn()` and small helpers |
| `lib/api/trust.ts` | Trust API client (summary, transactions, statement docs) `→ portal/lib/api/trust.ts:68` |
| `lib/api/deadlines.ts` | Deadlines API client `→ portal/lib/api/deadlines.ts:76` |
| `lib/api/retainer.ts` | Retainer API client (hour-bank, consumption) `→ portal/lib/api/retainer.ts` |
| `lib/api/acceptance.ts` | Public acceptance API (token-gated, pre-auth) `→ portal/lib/api/acceptance.ts:11` |
| `lib/api/notification-preferences.ts` | Phase 68 portal-notification opt-ins |
| `public/` | Static assets |
| `middleware.ts` | Root `/` → `/home` redirect; matcher only on `/` `→ portal/middleware.ts:8` |
| `e2e/` | Portal-only Playwright suite (helpers, tests, screenshots) `→ portal/e2e/` |

The full route map (18 handlers) lives in `_discovery/A3-portal-gateway-map.md:30-54`.

## Build & run

```bash
# Install
pnpm install                                    # from portal/

# Dev (HMR, port 3002)
pnpm dev                                        # → http://localhost:3002

# Build / start (standalone output)
pnpm build && pnpm start

# Agent service-management (Keycloak-mode-equivalent — portal has its own auth)
bash compose/scripts/svc.sh restart portal      # PID-tracked, logs in .svc/logs/portal.log
```

Backend dependency: a running backend on port 8080 (or set `NEXT_PUBLIC_PORTAL_API_URL`). Frontend HMR — TypeScript changes are picked up automatically; no restart needed for `.ts`/`.tsx` edits.

## Test stack

- **Unit:** `pnpm test` → Vitest + happy-dom + `@testing-library/react` `→ portal/vitest.config.mts`. Tests under `lib/__tests__/` and `components/__tests__/` and `lib/api/__tests__/`.
- **Coverage:** `@vitest/coverage-v8` available; not wired to a script — run `vitest --coverage` ad-hoc.
- **E2E:** `pnpm test:e2e:portal-client-90day` → portal-specific Playwright config `→ portal/playwright.portal.config.ts`. Suite location: `portal/e2e/tests/`. Helpers at `portal/e2e/helpers/`. Screenshots at `portal/e2e/screenshots/`.
- **Mock-auth N/A:** the staff `frontend/` mock-auth pattern (port 3001 stack) does **not** apply — portal has its own magic-link flow and the E2E tests drive that flow directly (request-link → exchange → JWT in localStorage). No Keycloak indirection.

## Deployment unit

- **Docker image** built from `portal/Dockerfile` (Next.js standalone runtime).
- **Port:** 3002.
- **Backend transit:** direct to backend (`NEXT_PUBLIC_PORTAL_API_URL`, default `http://localhost:8080`). **Not routed through `gateway/`** — the gateway only fronts the staff frontend (`/api/**` → backend with OAuth2 token relay) `→ _discovery/A3-portal-gateway-map.md:243-247`.
- **Public surface:** `/`, `/login`, `/auth/exchange`, `/accept/[token]/*` are unauthenticated. Everything under `(authenticated)/` requires a portal JWT (client-side guard).

## Auth model

Magic-link → portal JWT → `localStorage`. Keys written by `storeAuth()` `→ portal/lib/auth.ts:52`:

| Key | Contents | Cleared on logout? |
|---|---|---|
| `portal_jwt` | HS256 JWT (1h TTL, ADR-077) — `customer_id`, `portal_contact_id`, `org_id`, `email` | Yes |
| `portal_customer` | `{ id, name, email, orgId }` | Yes |
| `portal_last_org_id` | Last-known orgId | **No** — kept post-logout so deep-link returns after expiry resolve the tenant `→ portal/lib/auth.ts:58` |

Trust boundary, separation, and matrix: `20-cross-cutting/auth-and-rbac.md` and `_discovery/A3-portal-gateway-map.md:251-258`.

## Most-edited / hottest areas

| Path | Phase(s) |
|---|---|
| `app/(authenticated)/trust/` | Phase 60 (foundational), Phase 67 (statements), Phase 68 (vertical parity) |
| `app/(authenticated)/deadlines/` | Phase 67, Phase 68 |
| `app/(authenticated)/retainer/` | Phase 68 |
| `app/accept/[token]/` | Phase 28 (acceptance), Phase 68 (redesign) |
| `components/portal-sidebar.tsx` | Vertical-aware nav (profile + module gating) |
| `lib/api/` | Per-domain typed clients (trust, deadlines, retainer, acceptance) — added phase by phase |

## Profile / environment quirks

- **`NEXT_PUBLIC_PORTAL_API_URL`** — direct backend URL. Default `http://localhost:8080`. **Not** the gateway. Misconfiguring this to a gateway URL will break auth (the gateway has no `/portal/auth/*` route).
- **`middleware.ts` matcher is `["/"]` only** `→ portal/middleware.ts:17`. The middleware does not gate auth; auth gating is client-side in `app/(authenticated)/layout.tsx`.
- **`portal_last_org_id` retention.** Survives logout by design — supports deep-link returns after JWT expiry resolving the tenant without a re-prompt for orgId.
- **Standalone output** (`next.config.ts:3`) — Docker image uses Next's minimal runtime; if a dependency is dynamically required, ensure it is reachable from the standalone bundle.

## Architectural notes

- **Zero code shared with staff frontend.** The portal and `frontend/` are separate Next.js apps with separate `node_modules`, separate Shadcn installs, and separate component libraries. Consolidation (shared `@b2mash/ui` package) is a recurring discussion but **no consolidation has happened** — see `_discovery/A6-cross-cutting.md` and `30-modules/customer-portal.md` open question §5.
- **Terminology map duplicated.** `portal/lib/terminology-map.ts` and `frontend/lib/terminology-map.ts` ship the same content in two bundles `→ _discovery/A6-cross-cutting.md:218`. Renaming a profile key requires edits in both files. Backend exposes only `terminologyKey`; the *map* lives in the frontend bundles. Known cross-app drift risk.
- **Foundational phases:** Phase 22 (customer-portal-frontend) introduced the app; Phase 68 (portal-redesign-vertical-parity) brought trust/retainer/deadlines surfaces and the slim left-rail nav (ADR-252).
- **Bounded-context boundary lives in the read-model, not the wire.** A portal bug cannot leak draft invoices or internal comments because the read-model **filters at sync time** (ADR-078), not at read time. The portal client is consequently free to be "thin" — it does not enforce visibility, the backend's projection does.
