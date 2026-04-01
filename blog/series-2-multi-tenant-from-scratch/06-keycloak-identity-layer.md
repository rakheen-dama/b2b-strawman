# Keycloak as Your Identity Layer: Orgs, RBAC, and JWT v2 Claims

*Part 6 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

Every B2B SaaS needs three things from its auth layer: users belong to organizations, organizations have roles, and roles determine what you can do. Sounds simple. The implementation details will keep you up at night.

DocTeams started with Clerk. It was fast to integrate and had a nice developer experience. Then I hit the walls: proprietary billing SDK (removed in Phase 2), CAPTCHA that blocked automated testing, JWT format changes between versions, and a pricing model that didn't fit B2B workloads.

I migrated to Keycloak. Self-hosted, standards-based (OIDC), with native organization support. Here's what the integration looks like.

## The JWT Contract

Everything in the auth pipeline flows from the JWT token. Here's what a Keycloak JWT looks like for a user in an organization:

```json
{
  "sub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "alice@thornton.co.za",
  "name": "Alice Thornton",
  "organization": ["thornton-associates"],
  "groups": [],
  "iss": "https://keycloak.example.com/realms/app",
  "aud": "b2b-strawman-api",
  "exp": 1711929600
}
```

The critical field is `organization` — a list containing the org alias. For Keycloak, the org alias *is* the org identifier we use for schema lookup. For Clerk (which we also support for backward compatibility), the JWT looks different:

```json
{
  "sub": "user_abc123",
  "email": "alice@thornton.co.za",
  "o": {
    "id": "org_xyz789",
    "rol": "org:owner",
    "slg": "thornton-associates"
  },
  "v": 2
}
```

The `JwtUtils` class abstracts over both formats:

```java
public static String extractOrgId(Jwt jwt) {
    // Try Clerk v2 format first: o.id
    String clerkId = extractClerkClaim(jwt, "id");
    if (clerkId != null) return clerkId;
    // Fall back to Keycloak: first entry in organization list
    return extractKeycloakOrgId(jwt);
}
```

This dual-format support was essential during the migration from Clerk to Keycloak — both formats worked simultaneously, so we could migrate users gradually.

## From JWT to Tenant Schema

The filter chain extracts the org identifier and resolves it to a PostgreSQL schema:

```
JWT → JwtUtils.extractOrgId() → "thornton-associates"
                                        │
                                        ▼
                          org_schema_mapping table
                     (clerk_org_id → schema_name)
                                        │
                                        ▼
                          "tenant_a1b2c3d4e5f6"
                                        │
                                        ▼
                     SET search_path TO tenant_a1b2c3d4e5f6
```

The `org_schema_mapping` table uses `clerk_org_id` as the column name (a naming artifact from the Clerk era). It maps whatever identifier the auth provider gives us — Clerk org ID, Keycloak org alias, anything — to the deterministic schema name.

## The Capability System

DocTeams doesn't use Spring Security authorities for authorization. Here's why:

Spring Security authorities are extracted from the JWT at authentication time. They're static for the request — you can't easily change them based on business logic. And they're stringly-typed: `ROLE_ADMIN`, `SCOPE_read`, etc.

Instead, DocTeams uses a **capability registry** (built in Phase 20). Capabilities are resolved dynamically from the member's org role:

```java
// In MemberFilter, after resolving the member:
Set<String> capabilities = orgRoleService.resolveCapabilities(memberId);
ScopedValue.where(RequestScopes.CAPABILITIES, capabilities)
    .run(() -> filterChain.doFilter(request, response));
```

Capabilities are checked declaratively on controller methods:

```java
@PostMapping
@RequiresCapability("MANAGE_INVOICES")
public ResponseEntity<InvoiceResponse> create(
        @PathVariable UUID customerId,
        @Valid @RequestBody CreateInvoiceRequest request) {
    var invoice = invoiceService.create(customerId, request);
    return ResponseEntity.created(URI.create("/api/invoices/" + invoice.getId()))
        .body(InvoiceResponse.from(invoice));
}
```

The `@RequiresCapability` annotation is enforced by a Spring Security method interceptor:

```java
@Component
public class CapabilityAuthorizationManager
        implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            MethodInvocation invocation) {
        RequiresCapability annotation =
            invocation.getMethod().getAnnotation(RequiresCapability.class);
        if (annotation == null) return null;

        return new AuthorizationDecision(
            RequestScopes.hasCapability(annotation.value()));
    }
}
```

**Why capabilities instead of roles?** Because roles are coarse. "Admin" means different things in different features. Capabilities like `MANAGE_INVOICES`, `VIEW_PROFITABILITY`, `MANAGE_RATES` give fine-grained control. An org can define a "Bookkeeper" role that has `MANAGE_INVOICES` and `VIEW_PROFITABILITY` but not `MANAGE_RATES` — without changing any code.

The module registry controls which capabilities are available per deployment:

```java
// Capabilities are grouped by module
// Modules can be enabled/disabled per vertical
INVOICING: [MANAGE_INVOICES, VIEW_INVOICES]
PROFITABILITY: [VIEW_PROFITABILITY, EXPORT_REPORTS]
RATES: [MANAGE_RATES, VIEW_RATES]
COMPLIANCE: [MANAGE_CHECKLISTS, VIEW_COMPLIANCE]
```

Owners and admins get all capabilities from all enabled modules. Custom roles get a subset. This is stored in the database, not in the JWT — so role changes take effect immediately without re-authentication.

## The Mock IDP for Testing

You can't run E2E tests against Keycloak in CI. It's too heavy, too slow to bootstrap, and too flaky for test isolation. Instead, DocTeams uses a **mock IDP** for testing:

```
compose/mock-idp/
├── index.js          # Express server
├── keys/             # RSA key pair (generated at startup)
└── Dockerfile
```

The mock IDP exposes three endpoints:

- `GET /.well-known/jwks.json` — returns the public key for JWT verification
- `POST /token` — returns a signed JWT for a given user
- `GET /userinfo` — returns user profile data

Spring Boot's `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` points to the mock IDP in test/E2E profiles. In production, it points to Keycloak.

Test users are deterministic:

```javascript
const USERS = {
  alice: { sub: 'user-alice', email: 'alice@test.com', name: 'Alice Test', role: 'org:owner' },
  bob:   { sub: 'user-bob',   email: 'bob@test.com',   name: 'Bob Test',   role: 'org:admin' },
  carol: { sub: 'user-carol', email: 'carol@test.com', name: 'Carol Test', role: 'org:member' }
};
```

Playwright tests authenticate by calling the mock IDP's token endpoint:

```typescript
// e2e/fixtures/auth.ts
export async function loginAs(page: Page, user: 'alice' | 'bob' | 'carol') {
    await page.goto('/mock-login');
    await page.click(`[data-user="${user}"]`);
    await page.waitForURL('**/dashboard');
}
```

This gives us deterministic, fast, Keycloak-free E2E testing. The auth abstraction in the frontend (`lib/auth/`) dispatches to either Keycloak or mock IDP based on `NEXT_PUBLIC_AUTH_MODE`:

```typescript
// lib/auth/index.ts
export async function getAuthContext(): Promise<AuthContext> {
    if (process.env.NEXT_PUBLIC_AUTH_MODE === 'mock') {
        return getMockAuthContext();
    }
    return getKeycloakAuthContext();
}
```

## Lessons from the Clerk → Keycloak Migration

The migration (Phase 35, then re-done more carefully) taught me several things:

**1. Abstract early.** The `lib/auth/` abstraction layer that decouples 44+ frontend files from `@clerk/nextjs/server` should've existed from day one. Migrating 44 files is a lot more work than designing a provider interface upfront.

**2. Normalize role formats.** Clerk uses `org:owner`, Keycloak uses `owner`. The normalization happens in `getAuthContext()` — strip the `org:` prefix. This took an embarrassing amount of debugging to figure out.

**3. JWT structure is a contract.** When Clerk changed from v1 to v2 JWT format (flat `org_id` → nested `o.id`), several things broke. `JwtUtils` exists specifically to absorb these changes in one place.

**4. Test the auth flow, not just the endpoints.** Phase 35 was rolled back because integration *tests* passed but the actual browser flow (token refresh, session handling, middleware ordering) had subtle timing issues. The E2E tests with mock IDP catch these — they exercise the real auth flow from login to API call.

---

*Next in this series: [The Customer Lifecycle State Machine: PROSPECT → ONBOARDING → ACTIVE](07-customer-lifecycle-state-machine.md)*

*Previous: [Flyway Dual-Path Migrations](05-flyway-dual-path-migrations.md)*
