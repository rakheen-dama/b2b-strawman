# ADR-190: Module Guard Granularity

**Status**: Accepted
**Date**: 2026-03-18
**Phase**: 49 (Vertical Architecture)

## Context

Vertical-specific modules (trust accounting, court calendar, conflict check) need access gating per tenant. When a tenant does not have a module enabled in their `enabled_modules` JSONB array on `OrgSettings`, all API endpoints belonging to that module must return HTTP 403. This is a security boundary -- a tenant without trust accounting enabled must not be able to access trust accounting endpoints even by calling the API directly.

The platform already has two established patterns for gating access. First, `CustomerLifecycleGuard` is a `@Component` injected into services, called explicitly at the top of service methods: `lifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE)`. It reads entity state, evaluates a rule, and throws `InvalidStateException` on failure. Second, `@RequiresCapability` is a custom annotation on controller methods processed by `MemberFilter` during the Spring Security filter chain. It checks whether the authenticated member's role grants the required capability (e.g., `MANAGE_PROJECTS`). These two patterns serve different purposes: `@RequiresCapability` gates by *who* the user is (role-based), while `CustomerLifecycleGuard` gates by *what state* the domain is in (state-based).

Module gating is conceptually closer to state-based gating: the question is not "does this user have permission?" but "does this tenant have this module enabled?" The answer depends on the tenant's `OrgSettings.enabled_modules` configuration, which is tenant-wide (not per-user). The guard must be efficient (modules are checked on every request to vertical endpoints), cacheable (the enabled modules list doesn't change mid-request), and clear in its error messaging (the 403 response should tell the user which module is required and how to enable it).

## Options Considered

### Option 1: Controller-level explicit guard calls

Create a `VerticalModuleGuard` component with a `requireModule(String moduleId)` method. Inject it into controllers for vertical-specific endpoints and call it at the top of each endpoint method, before any business logic. The guard reads the current tenant's `enabled_modules` from `OrgSettings` (via `OrgSettingsService`, which has Caffeine caching), checks whether the requested module is in the list, and throws `ModuleNotEnabledException` (HTTP 403) if not.

```java
@GetMapping("/ledger")
public ResponseEntity<?> getLedger() {
    moduleGuard.requireModule("trust_accounting");
    return ResponseEntity.ok(trustAccountingService.getLedger());
}
```

- **Pros:**
  - Explicit and visible: every guarded endpoint has a clear guard call at the top of the method -- code reviewers can see at a glance which module is required
  - Follows the `CustomerLifecycleGuard` pattern exactly, which the team already understands
  - No framework magic: no interceptors, no AOP, no annotation processing -- just a method call
  - Easy to test: mock the guard in controller tests, verify it's called with the correct module ID
  - Flexible: different endpoints in the same controller can require different modules if needed
  - Error messages can be customized per module ("Trust Accounting module required" vs. "Court Calendar module required")

- **Cons:**
  - Boilerplate: every vertical endpoint method must include the guard call -- forgetting it is a security gap
  - The guard call is in the controller layer, but the security boundary is conceptually pre-controller -- a missed guard call means the request reaches business logic before being rejected
  - If a vertical module has 20 endpoints, that's 20 identical guard calls (though in practice, this phase creates only 1-3 stub endpoints per module)
  - No compile-time enforcement: nothing prevents a developer from adding a new endpoint to a vertical controller without the guard call

### Option 2: Service-level guard calls

Same `VerticalModuleGuard` component, but injected into service classes rather than controllers. Guard calls are placed at the top of service methods. This catches both controller-initiated calls and internal service-to-service calls.

```java
public TrustLedger getLedger() {
    moduleGuard.requireModule("trust_accounting");
    // ... business logic
}
```

- **Pros:**
  - Catches internal service-to-service calls that bypass controllers (e.g., a shared service calling trust accounting logic directly)
  - Keeps controllers thin and focused on HTTP concerns only, consistent with the project's "controllers are HTTP adapters" discipline
  - If a future scheduler or event handler invokes trust accounting logic, the guard still fires
  - The guard is closer to the business logic it protects, reducing the distance between the check and the protected code

- **Cons:**
  - Less visible to code reviewers: the guard call is buried in service methods rather than at the obvious entry point (the controller)
  - Service methods may be called in contexts where `RequestScopes.TENANT_ID` is not bound (e.g., background jobs, migration scripts), requiring defensive `isBound()` checks or a different guard behavior
  - Multiple service methods in the same module all need the guard call -- same boilerplate concern as Option 1, but deeper in the call stack
  - Services calling other services within the same module would trigger redundant guard checks (e.g., `TrustLedgerService.getLedger()` calls `TrustTransactionService.getTransactions()`, both check the guard)
  - The backend CLAUDE.md convention states controllers use `@RequiresCapability` for access control -- placing module guards in services creates an inconsistency where some access control is in controllers (capability) and some is in services (module)

### Option 3: Spring Security filter / URL-pattern interceptor

Create a `ModuleAccessFilter` in the Spring Security filter chain that intercepts requests matching vertical module URL patterns (e.g., `/api/trust-accounting/**`). The filter reads the tenant's `enabled_modules` and rejects requests to disabled modules before they reach the controller.

- **Pros:**
  - Zero per-endpoint code: all endpoints under `/api/trust-accounting/**` are automatically gated by the filter
  - Impossible to forget: new endpoints added to a vertical controller are automatically protected by the URL pattern
  - Clean separation: module access control is in the security layer (where `@RequiresCapability` also lives), not in business logic
  - Single configuration point: URL patterns are listed in one place (the filter or security config), making it easy to audit which URLs require which modules
  - Consistent with how `TenantFilter` and `MemberFilter` already work -- module access becomes another filter in the chain

- **Cons:**
  - URL-based matching is fragile: if a vertical endpoint doesn't follow the expected URL pattern (e.g., a shared endpoint that conditionally uses a module), the filter either misses it or over-blocks it
  - Module-to-URL mapping must be maintained separately from the controllers -- if a controller's `@RequestMapping` changes, the filter config must be updated
  - The filter runs before the controller, so it cannot access request body or path variable context that might be needed for fine-grained checks (e.g., "is this specific invoice using LSSA tariff?")
  - Shared services that conditionally use module features (e.g., `InvoiceService` checking `isModuleEnabled("lssa_tariff")`) cannot be gated by URL pattern -- the URL is `/api/invoices`, not a module-specific path
  - Adds complexity to the already 5-deep filter chain (ApiKey, JWT, Tenant, Member, Logging) -- debugging filter ordering issues has been a recurring pain point in the project
  - The `isModuleEnabled()` conditional check (for shared services) would still need a separate mechanism, creating two patterns for module access control

### Option 4: Custom annotation (`@RequiresModule`)

Create a `@RequiresModule("trust_accounting")` annotation that can be placed on controller methods or classes. A Spring `HandlerInterceptor` processes the annotation before the controller method executes, reading the tenant's `enabled_modules` and throwing `ModuleNotEnabledException` if the module is not enabled.

- **Pros:**
  - Declarative: the module requirement is visible in the method signature, similar to `@RequiresCapability`
  - Compile-time annotation: IDEs can search for usages, and annotation processors could validate module IDs
  - Can be applied at the class level (all endpoints in a controller require the same module) or method level (individual endpoints)
  - No boilerplate method calls: the annotation is a one-liner on the method declaration
  - Could be combined with `@RequiresCapability` for a unified security annotation model

- **Cons:**
  - Requires a `HandlerInterceptor` or AOP-based annotation processor -- the project explicitly avoids AOP for guard logic (the requirements state "No AOP, no interceptor chains, no framework abstractions")
  - The `@RequiresCapability` annotation is processed by `MemberFilter` (a servlet filter), not a `HandlerInterceptor`. Adding a new interceptor for `@RequiresModule` introduces a second annotation processing mechanism with different lifecycle timing
  - Interceptors have limited access to `ScopedValue`-based context (`RequestScopes.TENANT_ID`) depending on when they run relative to `TenantFilter` -- ordering bugs are subtle
  - Does not cover the `isModuleEnabled()` use case for shared services -- conditional module checks in `InvoiceService` still need an imperative guard call
  - If the annotation processing fails silently (e.g., the interceptor is not registered), endpoints are unprotected with no compile-time warning
  - Adds a new pattern that must be explained to future contributors: "use `@RequiresCapability` for role checks, `@RequiresModule` for module checks, and `lifecycleGuard.require...` for state checks" -- three distinct access control mechanisms

## Decision

**Option 1 -- Controller-level explicit guard calls.**

## Rationale

The `CustomerLifecycleGuard` pattern is the closest analog to module gating in the existing codebase. Both are state-based checks (is this customer in the right lifecycle state? is this module enabled for this tenant?) rather than role-based checks (does this user have permission?). Both operate on tenant-scoped data read from the database. Both throw specific exceptions that map to HTTP error responses. Using the same pattern -- an explicit method call at the guard point -- keeps the codebase consistent and reduces the cognitive load for developers who already understand how `CustomerLifecycleGuard` works.

The explicit call at the top of each controller method makes the security boundary visible to code reviewers. When reviewing a new vertical endpoint, a reviewer can immediately see whether the module guard is present. This visibility is worth the small amount of boilerplate. In practice, vertical modules in this phase have 1-3 stub endpoints each, and even mature modules are unlikely to exceed 10-15 endpoints -- the boilerplate cost is bounded.

The filter-based approach (Option 3) is attractive for its "impossible to forget" property, but it fails the shared-service use case. The `InvoiceService` checking `isModuleEnabled("lssa_tariff")` to conditionally include LSSA tariff rates operates on a URL (`/api/invoices`) that is not a module-specific path. A URL-pattern filter cannot gate this -- it requires an imperative `isModuleEnabled()` check in the service. If the platform needs both a filter for dedicated module endpoints and an imperative check for shared services, that is two module access patterns rather than one. A single `VerticalModuleGuard` component with both `requireModule()` (throwing) and `isModuleEnabled()` (boolean) methods serves both cases with one pattern.

The annotation approach (Option 4) violates the project's explicit constraint against AOP and interceptor-based guard logic. The requirements state: "No AOP, no interceptor chains, no framework abstractions. Match the simplicity of `CustomerLifecycleGuard`." The annotation also does not address the `isModuleEnabled()` conditional check, which is needed for shared services that have module-conditional behavior.

Service-level placement (Option 2) was considered but rejected because it creates inconsistency: role-based access control (`@RequiresCapability`) runs at the controller/filter layer, while module access control would run at the service layer. Keeping both at the controller level maintains a single "access control happens here" mental model. The concern about internal service-to-service calls is mitigated by the fact that vertical module services are isolated in their own packages (`verticals.legal.trustaccounting`) and are not called by core services -- core services use the `isModuleEnabled()` boolean check for conditional behavior, not the `requireModule()` throwing check.

## Consequences

- **Positive:**
  - One pattern for module access control: `VerticalModuleGuard` with `requireModule()` (throwing) and `isModuleEnabled()` (boolean)
  - Consistent with the established `CustomerLifecycleGuard` pattern -- no new framework concepts to learn
  - The guard is a plain `@Component` with constructor injection, testable with standard mocking
  - `ModuleNotEnabledException` maps to HTTP 403 with a descriptive message including the module name and an instruction to contact the administrator
  - The guard reads `enabled_modules` from `OrgSettings` via the existing Caffeine cache, adding zero database overhead for repeated checks within the same request

- **Negative:**
  - Every vertical endpoint must include an explicit `moduleGuard.requireModule(...)` call -- forgetting it is a security gap (mitigated by code review and integration tests that verify 403 responses for disabled modules)
  - No compile-time enforcement that all vertical endpoints are guarded -- developers must remember to add the call (mitigated by the convention that vertical controllers always start with the guard call, documented in the vertical controller stub template)
  - If a vertical module grows to 20+ endpoints, the repeated guard calls become verbose (mitigated by the fact that this is a small, bounded amount of boilerplate per method)

- **Neutral:**
  - The `VerticalModuleGuard` component lives in the `verticals/` package alongside the profile registries and module stub controllers (not in `security/`, which handles authentication, not domain-level gating)
  - `@RequiresCapability` continues to handle role-based access control independently -- the two mechanisms compose (a request must pass both the capability check and the module check)
  - Future phases that build out full module implementations will follow this same guard pattern, establishing consistency across the vertical codebase
