# ADR-214: Gateway BFF ALB Routing

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

The application uses a Gateway BFF (Backend for Frontend) pattern where the Spring Cloud Gateway handles OAuth2 authentication, session management, and token relay to the backend API. The current ALB routes `/api/*` directly to the backend and `/*` to the frontend. With the Gateway BFF, the question is how the ALB should route traffic -- should all traffic flow through the gateway, or should the frontend be reachable directly?

The gateway's responsibilities are:
1. Serving `/bff/*` endpoints (login, logout, session status)
2. Proxying `/api/**` to the backend with `TokenRelay` filter (exchanges session cookie for JWT)
3. Managing HTTP sessions (Redis-backed) for Keycloak OAuth2 code flow

The frontend's responsibilities are:
1. Serving SSR pages and static assets
2. Making server-side calls to backend via internal ALB (`/internal/*` with API key)
3. Making client-side calls to the gateway (`/bff/*`, `/api/*`) via the browser

## Options Considered

### Option 1: Gateway-First (All Traffic Through Gateway)

All traffic to `app.heykazi.com` hits the gateway first. The gateway proxies non-API requests to the frontend.

- **Pros:** Single entry point simplifies security reasoning. All requests have session context available. Gateway can enforce authentication on all routes.
- **Cons:** Gateway becomes a bottleneck for static assets and SSR pages. Adds latency to every page load (extra network hop). Gateway must understand Next.js routing (serve static files, handle RSC payloads). Doubles the load on gateway tasks. Spring Cloud Gateway is not designed to proxy arbitrary web applications -- it proxies API calls.

### Option 2: Split Routing (Selected)

Frontend serves static/SSR directly from the ALB. Only `/bff/*` and `/api/*` route through the gateway. Host-based routing separates portal and Keycloak.

```
app.heykazi.com/*        → frontend (direct)
app.heykazi.com/bff/*    → gateway (higher priority rule)
app.heykazi.com/api/*    → gateway (higher priority rule)
portal.heykazi.com/*     → portal (host-based)
auth.heykazi.com/*       → keycloak (host-based)
```

- **Pros:** Frontend serves pages at full speed (no gateway hop). Gateway only handles what it's designed for (OAuth2, token relay). Reduces gateway load -- only API and BFF calls pass through it. Matches the existing Next.js architecture (server components fetch via internal ALB, client components fetch via gateway). Each service is independently scalable.
- **Cons:** Two different auth models coexist at the ALB level (path-based for app, host-based for portal/auth). Frontend pages are accessible without session validation at the ALB level (authentication is handled by the frontend middleware and server-side auth checks). More ALB listener rules to manage.

### Option 3: Dual ALB

Separate ALBs for the main application (frontend + gateway) and for supporting services (portal, Keycloak).

- **Pros:** Stronger isolation between application and auth infrastructure. Independent scaling of ALB capacity. Easier to reason about security boundaries.
- **Cons:** Doubles ALB cost (~$20/month per ALB + LCU charges). Two ACM certificates needed (or wildcard on both). More complex DNS setup. Unnecessary at 5-20 tenant scale -- a single ALB can handle the load. Adds operational complexity for no proportional benefit.

## Decision

**Option 2 -- Split routing with path-based and host-based rules on a single ALB.**

## Rationale

1. **Gateway is an API proxy, not a web server.** Spring Cloud Gateway MVC is designed to proxy API calls with filters (TokenRelay, DedupeResponseHeader). Routing all web traffic through it would require it to handle Next.js SSR responses, static assets, and RSC streaming -- none of which it's built for.
2. **Latency matters for page loads.** Every page load through the gateway adds a network hop (~5-10ms within the VPC). For SSR pages that already fetch data from the backend, this compounds. Direct ALB-to-frontend routing eliminates this overhead for the most common request type.
3. **The frontend already handles auth middleware.** The Next.js middleware (`proxy.ts`) checks for valid sessions and redirects unauthenticated users to the Keycloak login flow via the gateway's `/bff/login` endpoint. The ALB doesn't need to enforce authentication -- the application layer handles it.
4. **ALB path-based routing supports priority ordering.** Rules with lower priority numbers match first. `/bff/*` and `/api/*` at priority 30 and 40 match before the catch-all `/*` at priority 50. This ensures API calls always route to the gateway even though the frontend catch-all would also match.
5. **Single ALB is cost-efficient.** At 5-20 tenants, traffic is low enough that a single ALB handles all 5 services. ALBs charge per hour plus per LCU -- adding a second ALB roughly doubles the base cost for no functional benefit.

## Consequences

- **Positive:** Frontend pages load at full speed. Gateway handles only API/BFF traffic (lower CPU, smaller task size). Each service scales independently. Simpler gateway configuration (no web proxying rules).
- **Negative:** ALB listener rules are more complex (5 rules with host + path conditions). Frontend pages are accessible at the ALB level without session validation (relies on application-level auth). Developers must understand which paths route where.
- **Mitigations:** ALB listener rules are documented in the architecture doc with a clear priority table. Frontend middleware enforces authentication for all `/(app)` routes. The split between "what hits the gateway" (`/api/*`, `/bff/*`) and "what hits the frontend" (everything else on `app.heykazi.com`) matches the existing client-side fetch architecture -- no code changes needed in the frontend.
