# ADR-221: Read-Only Enforcement Strategy

**Status**: Proposed  
**Date**: 2026-04-02  
**Phase**: 57

## Context

When a tenant's subscription enters a non-paying state (GRACE_PERIOD, SUSPENDED, EXPIRED), the platform must enforce read-only access: tenants can view their data but cannot create, update, or delete anything. When the subscription reaches LOCKED, all access is blocked except the billing page.

This enforcement must be comprehensive (no write path should be missed), performant (no per-request database query), and clear to the user (structured error response that the frontend can render as a helpful message).

The existing filter chain has 6 filters (ApiKeyAuth, BearerToken+JWT, TenantFilter, MemberFilter, PlatformAdminFilter, TenantLoggingFilter). The new guard must operate after tenant and member context are resolved (to know which org's subscription to check) but before controllers execute.

## Options Considered

### Option 1: Servlet Filter (HTTP Method Check) (Selected)

Add a `SubscriptionGuardFilter` to the Spring Security filter chain after `MemberFilter` (position 5, shifting PlatformAdminFilter and TenantLoggingFilter to 6 and 7). The filter resolves the org's subscription status from a Caffeine cache, then:
- For GRACE_PERIOD/SUSPENDED/EXPIRED: blocks POST/PUT/PATCH/DELETE with HTTP 403 (`subscription_required` error type). Allows GET/HEAD/OPTIONS.
- For LOCKED: blocks all requests except `GET /api/billing/*`. Returns 403 with redirect hint.
- For TRIALING/ACTIVE/PENDING_CANCELLATION/PAST_DUE: passes through (full access).

- **Pros:** Comprehensive — catches every write attempt regardless of which controller handles it. Simple logic (HTTP method + path check). Consistent error response shape. Cache lookup is O(1) with Caffeine. Familiar pattern — matches existing guards (`CustomerLifecycleGuard`, `ProjectLifecycleGuard`) and filter chain architecture.
- **Cons:** Coarse-grained — blocks all POST/PUT/PATCH/DELETE, including operations that might be desirable during grace (e.g., cancellation management). Requires an allowlist for exceptions (`/api/billing/*`). Does not distinguish between "create" and "update" operations (both are blocked).

### Option 2: Capability-Based (Remove WRITE Capabilities)

Modify the `MemberFilter` to strip write-related capabilities from `RequestScopes.CAPABILITIES` when the subscription is in a read-only state. Controllers annotated with `@RequiresCapability("MANAGE_PROJECTS")` etc. would naturally fail authorization.

- **Pros:** Fine-grained — can selectively allow some write operations while blocking others. Uses the existing capability infrastructure. No new filter needed.
- **Cons:** Not all write operations are capability-guarded — some endpoints have no `@RequiresCapability` annotation (e.g., settings updates, profile changes). The capability system is designed for role-based access, not subscription-based access — mixing these concerns would make the authorization model harder to reason about. Would require auditing every controller to ensure coverage, and new controllers would need to remember to add capability checks. The "write" vs "read" distinction doesn't map cleanly to existing capabilities (e.g., `MANAGE_PROJECTS` covers both reading project settings and updating them).

### Option 3: Database-Level Read-Only (`SET default_transaction_read_only`)

Set `SET default_transaction_read_only = true` on the database connection for read-only tenants. Any INSERT/UPDATE/DELETE statement would fail at the database level.

- **Pros:** Bulletproof — no application code can bypass it. Zero risk of missed write paths. Works regardless of controller annotations or filter chain order.
- **Cons:** PostgreSQL `default_transaction_read_only` applies per-connection, not per-tenant — in a shared connection pool with schema-per-tenant isolation, this would require connection tagging and careful pool management. The error response is a raw SQL exception (`ERROR: cannot execute INSERT in a read-only transaction`), not a structured API error — the frontend cannot render a helpful message. Cannot allowlist specific write paths (e.g., `POST /api/billing/subscribe` must work even during grace period). The subscription status lives in the `public` schema — the connection provider would need to check it before setting `search_path`, adding latency to every request.

### Option 4: Service-Layer Interceptor (AOP)

Use a Spring AOP `@Around` aspect that intercepts all `@Service` methods annotated with a custom `@RequiresActiveSubscription` annotation. The aspect checks subscription status before allowing the method to execute.

- **Pros:** Fine-grained control. Can be applied selectively. Does not affect the filter chain.
- **Cons:** Requires annotating every write-path service method — high risk of missing one. New service methods must remember to add the annotation. AOP proxying adds complexity and makes debugging harder. Does not produce a clean HTTP error response — the exception thrown by the aspect must be mapped to an HTTP status somewhere. Testing requires Spring context for proxy creation.

## Decision

**Option 1 — Servlet filter with HTTP method check.**

## Rationale

1. **Comprehensiveness over granularity.** The business requirement is "block all writes during grace/locked." A filter that checks HTTP methods is inherently comprehensive — POST/PUT/PATCH/DELETE are the only ways to mutate state via HTTP. No controller or service method can be missed. The only exceptions are explicitly allowlisted paths (billing endpoints).

2. **Consistent with existing architecture.** The filter chain is the established mechanism for cross-cutting request validation (tenant resolution, member binding, platform admin checks). Adding another filter follows the same pattern and is discoverable by developers familiar with the codebase.

3. **Allowlist for billing.** The filter must permit `POST /api/billing/subscribe` and `POST /api/billing/cancel` even during grace period (so users can resubscribe or manage their billing). An explicit path allowlist (`/api/billing/*`) is simpler and more auditable than capability-based exceptions.

4. **Cache performance.** Subscription status is cached per-org in Caffeine with a 5-minute TTL. The cache lookup is O(1) in memory — no database query on the hot path. The ITN webhook handler evicts the cache on status change, so transitions are reflected within the TTL window.

5. **Structured error response.** The filter returns a RFC 9457 `ProblemDetail` with `type: "subscription_required"` — the frontend can intercept this globally and render a subscription banner/modal without per-endpoint error handling.

## Consequences

- **Positive:** Every write attempt is blocked without relying on controller-level annotations. Error responses are consistent and machine-readable. The filter is a single class (~60 lines) with straightforward test cases. Cache avoids per-request DB queries.
- **Negative:** Coarse — cannot allow "update profile photo" during grace while blocking "create project." All POST/PUT/PATCH/DELETE are treated equally. The allowlist (`/api/billing/*`) must be maintained as billing endpoints are added. Internal endpoints (`/internal/*`) must be excluded from the guard (they are API-key authenticated and not subject to subscription enforcement).
- **Mitigations:** The allowlist is small and stable (billing endpoints only). Internal endpoints are already handled by a separate security filter chain in `SecurityConfig`. If fine-grained write control is needed later, the filter can be extended with a path-based policy (e.g., `GRACE_ALLOWED_PATHS`).
