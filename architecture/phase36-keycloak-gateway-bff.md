# Keycloak + Spring Cloud Gateway BFF Migration

> Standalone architecture document. ADR files go in `adr/`.

---

## Keycloak + Spring Cloud Gateway BFF Migration

This phase replaces Clerk with **Keycloak 26.5** as the identity provider and introduces a **Spring Cloud Gateway BFF** (Backend-for-Frontend) that keeps all OAuth2 tokens server-side. The browser never sees a JWT — only an opaque HttpOnly session cookie. The backend remains a stateless resource server with minimal changes (YAML config + JWT claim extractor).

Phase 20 introduced an auth provider abstraction layer (`lib/auth/providers/`) and a mock IDP for E2E testing. This phase builds on that foundation: `keycloak-bff.ts` becomes the third provider alongside `clerk.ts` and `mock/server.ts`, selected at build time via `NEXT_PUBLIC_AUTH_MODE=keycloak`.

**Dependencies on prior phases**: Phase 20 (auth abstraction layer, mock IDP, `NEXT_PUBLIC_AUTH_MODE` dispatch). No other phase dependencies — this is a cross-cutting infrastructure change.

### What's New

| Capability | Before | After |
|---|---|---|
| Identity provider | Clerk (hosted SaaS, per-MAU pricing) | Keycloak 26.5 (self-hosted, zero license cost) |
| Token storage | JWTs accessible to browser JavaScript | Tokens server-side only (Spring Session JDBC in PostgreSQL) |
| Browser credential | Clerk session token / JWT in `Authorization` header | Opaque `SESSION` cookie (HttpOnly, Secure, SameSite=Lax) |
| Enterprise SSO | Clerk Enterprise add-on (paid) | Keycloak org-linked IdPs (built-in, free) |
| Custom domains | Clerk Pro plan | Reverse proxy config (self-managed) |
| Audit/compliance | Clerk Enterprise API | Keycloak Event Listeners + existing audit infrastructure |
| Login/registration UI | Clerk embedded components (`<SignIn>`, `<SignUp>`) | Keycloak-hosted themed pages |
| API proxy pattern | Direct: SPA → Backend (Bearer JWT) | BFF: SPA → Gateway (session cookie) → Backend (Bearer JWT) |
| User data sovereignty | Clerk's US cloud | Self-hosted PostgreSQL (your infrastructure) |

**Out of scope**: Multi-organization membership (single org per user enforced), mobile app authentication (future phase — mobile would use standard OAuth2 PKCE without the BFF), Clerk data migration (no production users exist), SAML IdP configuration for specific customer orgs (infrastructure is ready, individual customer IdP linking is operational).

**Constraint**: Single org per user. Users cannot belong to multiple Keycloak organizations. This eliminates the known multi-org JWT claim bug (Keycloak issues #33556, #41127) and simplifies tenant resolution — the JWT always contains exactly one organization.

---

### 1. Overview

The architecture introduces two new services and modifies the auth flow:

```
┌─────────────────────────────────────────────────────────────────────┐
│  Production Topology                                                │
│                                                                     │
│  ┌──────────┐     ┌─────────────────┐     ┌──────────────────┐     │
│  │ Browser  │────>│ Spring Cloud    │────>│ Spring Boot      │     │
│  │ (Next.js)│     │ Gateway BFF     │     │ Backend API      │     │
│  │          │<────│ :8443           │<────│ :8080            │     │
│  └──────────┘     └────────┬────────┘     └──────────────────┘     │
│       │                    │                                        │
│       │           ┌────────┴────────┐                               │
│       │           │ PostgreSQL      │                               │
│       │           │ SPRING_SESSION  │                               │
│       │           │ tables          │                               │
│       │           └─────────────────┘                               │
│       │                                                             │
│       │           ┌─────────────────┐                               │
│       └──────────>│ Keycloak 26.5   │                               │
│    (login/reg     │ :8443/auth      │                               │
│     redirects)    │ Single realm    │                               │
│                   └─────────────────┘                               │
└─────────────────────────────────────────────────────────────────────┘
```

**Request flow (authenticated API call)**:

1. Browser sends `GET /api/customers` with `SESSION=abc123` cookie
2. Gateway looks up session in `SPRING_SESSION` table (PostgreSQL)
3. Gateway extracts `OAuth2AuthorizedClient` from session (contains access token)
4. If access token expired, gateway uses refresh token to obtain new one (transparent)
5. Gateway proxies request to backend with `Authorization: Bearer <jwt>`
6. Backend validates JWT against Keycloak's JWKS endpoint (standard Spring Security OAuth2)
7. `TenantFilter` + `MemberFilter` extract org/member from JWT claims (existing flow)

**Login flow**:

1. SPA navigates to `{gateway}/oauth2/authorization/keycloak`
2. Gateway redirects to Keycloak authorization endpoint
3. User authenticates at Keycloak (themed login page — password, Google, or linked corporate IdP)
4. Keycloak redirects back to gateway with authorization code
5. Gateway exchanges code for tokens (access + refresh + id_token) — **server-side only**
6. Tokens stored in PostgreSQL via Spring Session JDBC
7. Gateway sets `SESSION` cookie (HttpOnly, Secure, SameSite=Lax) and redirects to SPA

---

### 2. Keycloak 26.5 Configuration

#### 2.1 Realm & Organization Model

Single realm (`docteams`) with organizations enabled. Each tenant = one Keycloak organization.

| Keycloak Concept | DocTeams Mapping |
|---|---|
| Realm | `docteams` (single realm for all tenants) |
| Organization | One per tenant (maps to `org_schema_mapping.clerk_org_id`) |
| User | One per person (maps to `member.clerk_user_id`) |
| Organization membership | User belongs to exactly one org |
| Organization role | `owner`, `admin`, `member` (maps to existing `Roles.java` constants) |
| Realm client | `gateway-bff` (confidential client for the Spring Cloud Gateway) |
| Identity provider | Google (realm-level), plus optional per-org corporate IdPs |

**Realm configuration**:

- Organizations: enabled
- Identity-first login: enabled (email field first, then auth options)
- Registration: enabled (self-registration for invited users)
- Email verification: required
- Login theme: custom `docteams` theme (see Section 8)
- SMTP: configured for transactional emails (invite, password reset, verification)
- User locale: enabled

#### 2.2 Organization Roles

Keycloak organization roles map directly to existing application roles:

| Keycloak Org Role | Spring Authority | Existing Usage |
|---|---|---|
| `owner` | `ROLE_ORG_OWNER` | Full org access, billing, member management |
| `admin` | `ROLE_ORG_ADMIN` | Project management, member invites |
| `member` | `ROLE_ORG_MEMBER` | Task execution, time tracking |

These are assigned per-organization. Since users belong to exactly one org, there's no ambiguity.

#### 2.3 JWT Token Structure

The Keycloak access token with the built-in `organization` scope and custom role mapper:

```json
{
  "iss": "https://auth.docteams.com/realms/docteams",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "aud": "gateway-bff",
  "exp": 1709300000,
  "iat": 1709299700,
  "scope": "openid profile email organization",
  "email": "alice@example.com",
  "name": "Alice Owner",
  "organization": {
    "acme-corp": {
      "id": "a56bea03-5904-470a-b21c-92b7f1069d44",
      "roles": ["owner"]
    }
  }
}
```

**Differences from Clerk v2 JWT**:

| Claim | Clerk v2 | Keycloak 26.5 |
|---|---|---|
| Org claims location | `o` (nested object) | `organization` (nested object) |
| Org ID | `o.id` (string) | `organization.<slug>.id` (string) |
| Org role | `o.rol` (string: `"owner"`) | `organization.<slug>.roles` (array: `["owner"]`) |
| Org slug | `o.slg` (string) | Key of the `organization` map |
| User ID format | `user_xxx` (Clerk ID) | UUID (Keycloak ID) |

See [ADR-138](../adr/ADR-138-keycloak-jwt-claim-structure.md).

#### 2.4 Custom Protocol Mapper (Organization Roles)

The built-in `organization` mapper includes org name and optional ID, but **not roles**. A custom Script Mapper adds roles to the token:

**Mapper name**: `organization-roles-mapper`
**Mapper type**: Script Mapper (JavaScript)
**Token claim name**: Applied within `organization` claim
**Add to**: Access token, ID token

```javascript
// Script Mapper: adds roles array to each organization in the claim
var orgs = user.getOrganizations();
var orgClaim = {};
orgs.forEach(function(org) {
    var roles = org.getRoles().stream()
        .map(function(r) { return r.getName(); })
        .collect(java.util.stream.Collectors.toList());
    orgClaim[org.getAlias()] = {
        "id": org.getId(),
        "roles": roles
    };
});
token.getOtherClaims().put("organization", orgClaim);
```

> Note: The exact Script Mapper API may vary by Keycloak version. Test against 26.5 and adjust if needed. An alternative is a custom Java protocol mapper SPI deployed as a JAR — more robust but requires compilation. See [ADR-139](../adr/ADR-139-keycloak-org-role-mapper.md).

#### 2.5 Client Configuration

**Client ID**: `gateway-bff`
**Client type**: Confidential (client secret)
**Access type**: Standard flow (authorization code)
**Valid redirect URIs**: `https://app.docteams.com/login/oauth2/code/keycloak`, `http://localhost:8443/login/oauth2/code/keycloak`
**Post-logout redirect URIs**: `https://app.docteams.com/`, `http://localhost:3000/`
**Web origins**: `https://app.docteams.com`, `http://localhost:3000`
**Default client scopes**: `openid`, `profile`, `email`, `organization`

#### 2.6 Google Social Login

Google configured as a realm-level identity provider:

1. Create OAuth 2.0 credentials in Google Cloud Console
2. Add Google IdP in Keycloak Admin Console (Identity Providers > Google)
3. Paste Client ID and Client Secret
4. Set redirect URI from Keycloak's config into Google's authorized redirect URIs
5. Default scopes: `openid profile email`

Users see "Sign in with Google" on the Keycloak login page alongside username/password.

#### 2.7 Invitation Flow

Keycloak 26.5 provides native organization invitations:

1. Admin invites member via DocTeams UI → calls Keycloak Admin API: `POST /admin/realms/docteams/orgs/{orgId}/invitations`
2. Keycloak sends invitation email (customizable template) with realm, org, and inviter info
3. Invitee clicks link → redirected to Keycloak registration page (if new) or login page (if existing)
4. After authentication, invitee is prompted to accept/decline the invitation
5. On acceptance: user is added as org member with specified role
6. Keycloak fires an Admin Event (`CREATE` on `org/{orgId}/members/{userId}`)
7. Event Listener (see Section 5) calls `/internal/members/sync` on the backend

**Invitation status tracking** (26.5 feature): Pending/Expired status, resend capability, Admin Console Invitations tab, REST API for invitation CRUD.

---

### 3. Spring Cloud Gateway BFF

A dedicated Spring Boot application acting as an OAuth2 confidential client and token mediating backend. See [ADR-140](../adr/ADR-140-bff-pattern-token-storage.md).

#### 3.1 Dependencies

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.2</version>
</parent>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

**Design decision**: `spring-cloud-starter-gateway-server-webmvc` (servlet stack) chosen over `webflux` (reactive). Rationale: matches the existing Spring Boot backend's servlet stack, uses standard `HttpSession` with Spring Session JDBC natively, avoids introducing reactive programming complexity. See [ADR-141](../adr/ADR-141-gateway-servlet-vs-reactive.md).

#### 3.2 Configuration

```yaml
server:
  port: 8443

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:app}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:changeme}

  session:
    store-type: jdbc
    jdbc:
      initialize-schema: always
      table-name: SPRING_SESSION
    timeout: 8h

  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: gateway-bff
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid,profile,email,organization
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_ISSUER:http://localhost:8180/realms/docteams}

  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: backend-api
              uri: ${BACKEND_URL:http://localhost:8080}
              predicates:
                - Path=/api/**
              filters:
                - TokenRelay=
                - SaveSession
```

> **Note**: There is no `backend-internal` route. `/internal/**` endpoints are blocked at the security layer with `denyAll()` — proxying internal API-key endpoints through the gateway would bypass internal-only authentication.

#### 3.3 Security Configuration

```java
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/actuator/health", "/bff/me").permitAll()
                .requestMatchers("/internal/**").denyAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("SESSION")
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );
        return http.build();
    }

    @Bean
    public OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler() {
        // RP-Initiated Logout: invalidates Keycloak session too
    }
}
```

#### 3.4 User Info Endpoint (`/bff/me`)

The gateway exposes a `/bff/me` endpoint so the SPA knows who the logged-in user is. This replaces Clerk's `auth()` and `currentUser()` functions.

```java
@RestController
@RequestMapping("/bff")
public class BffController {

    @GetMapping("/me")
    public Map<String, Object> me(
            @AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return Map.of("authenticated", false);
        }

        Map<String, Object> orgClaim = user.getClaim("organization");
        // Single org: extract the one entry
        var orgEntry = orgClaim.entrySet().iterator().next();
        String orgSlug = (String) orgEntry.getKey();
        Map<String, Object> orgData = (Map<String, Object>) orgEntry.getValue();

        return Map.of(
            "authenticated", true,
            "userId", user.getSubject(),
            "email", user.getEmail(),
            "name", user.getFullName(),
            "picture", Objects.toString(user.getPicture(), ""),
            "orgId", orgData.get("id"),
            "orgSlug", orgSlug,
            "orgRole", ((List<?>) orgData.get("roles")).getFirst()
        );
    }
}
```

#### 3.5 CSRF for SPA

Since the BFF uses session cookies, CSRF protection is mandatory. The `CookieCsrfTokenRepository.withHttpOnlyFalse()` sets an `XSRF-TOKEN` cookie readable by JavaScript. The SPA includes the token as `X-XSRF-TOKEN` header on mutating requests.

**SPA integration** (in `lib/api.ts`):

```typescript
// Read CSRF token from cookie
function getCsrfToken(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function apiRequest<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  // Add CSRF token for mutating requests
  if (options.method && options.method !== "GET") {
    const csrf = getCsrfToken();
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
    credentials: "include", // Send session cookie
  });
  // ...
}
```

#### 3.6 Session Storage

Spring Session JDBC creates two tables in the shared PostgreSQL database (public schema):

| Table | Purpose |
|---|---|
| `SPRING_SESSION` | Session metadata: ID, creation time, last access, expiry, principal |
| `SPRING_SESSION_ATTRIBUTES` | Serialized session attributes (contains `OAuth2AuthorizedClient` with tokens) |

**Session lifecycle**:
- Created on successful OAuth2 login
- Updated on every request (last access time)
- Expired after `spring.session.timeout` (8 hours default)
- Cleanup job runs every 60 seconds, deletes expired sessions
- On logout: session invalidated, cookie cleared, Keycloak session ended (RP-Initiated Logout)

**Performance**: 1-2 database queries per proxied API call (session lookup + access time update). Acceptable for B2B workloads. If this becomes a bottleneck, Spring Session also supports Redis as a drop-in replacement.

---

### 4. Backend Changes

The backend requires minimal changes. It remains a stateless OAuth2 resource server — the gateway handles all session/token management.

#### 4.1 Spring Profile: `keycloak`

**`application-keycloak.yml`**:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER:http://localhost:8180/realms/docteams}
          jwk-set-uri: ${KEYCLOAK_JWKS:http://localhost:8180/realms/docteams/protocol/openid-connect/certs}
```

Activated via `--spring.profiles.active=keycloak` (or `SPRING_PROFILES_ACTIVE=keycloak`).

#### 4.2 JWT Claim Extractor (Provider-Agnostic)

Replace `ClerkJwtUtils` + `ClerkJwtAuthenticationConverter` with a strategy pattern:

**Interface** (`security/JwtClaimExtractor.java`):

```java
public interface JwtClaimExtractor {
    String extractOrgId(Jwt jwt);
    String extractOrgSlug(Jwt jwt);
    String extractOrgRole(Jwt jwt);
}
```

**Clerk implementation** (`security/ClerkJwtClaimExtractor.java`):

```java
@Component
@Profile("!keycloak")
public class ClerkJwtClaimExtractor implements JwtClaimExtractor {
    // Reads from "o" nested claim — existing ClerkJwtUtils logic
    @Override
    public String extractOrgId(Jwt jwt) {
        Map<String, Object> org = jwt.getClaim("o");
        return org != null ? (String) org.get("id") : null;
    }

    @Override
    public String extractOrgSlug(Jwt jwt) {
        Map<String, Object> org = jwt.getClaim("o");
        return org != null ? (String) org.get("slg") : null;
    }

    @Override
    public String extractOrgRole(Jwt jwt) {
        Map<String, Object> org = jwt.getClaim("o");
        return org != null ? (String) org.get("rol") : null;
    }
}
```

**Keycloak implementation** (`security/KeycloakJwtClaimExtractor.java`):

```java
@Component
@Profile("keycloak")
public class KeycloakJwtClaimExtractor implements JwtClaimExtractor {
    // Reads from "organization" claim — single org assumed
    @Override
    public String extractOrgId(Jwt jwt) {
        var entry = getSingleOrgEntry(jwt);
        return entry != null ? (String) ((Map<?, ?>) entry.getValue()).get("id") : null;
    }

    @Override
    public String extractOrgSlug(Jwt jwt) {
        var entry = getSingleOrgEntry(jwt);
        return entry != null ? (String) entry.getKey() : null;
    }

    @Override
    public String extractOrgRole(Jwt jwt) {
        var entry = getSingleOrgEntry(jwt);
        if (entry == null) return null;
        var roles = (List<?>) ((Map<?, ?>) entry.getValue()).get("roles");
        return roles != null && !roles.isEmpty() ? (String) roles.getFirst() : null;
    }

    private Map.Entry<String, Object> getSingleOrgEntry(Jwt jwt) {
        Map<String, Object> orgClaim = jwt.getClaim("organization");
        if (orgClaim == null || orgClaim.isEmpty()) return null;
        return orgClaim.entrySet().iterator().next(); // Single org
    }
}
```

**Updated converter** (`security/JwtAuthenticationConverter.java`):

```java
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final JwtClaimExtractor claimExtractor;

    public JwtAuthenticationConverter(JwtClaimExtractor claimExtractor) {
        this.claimExtractor = claimExtractor;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String orgRole = claimExtractor.extractOrgRole(jwt);
        String springRole = ROLE_MAPPING.getOrDefault(orgRole, Roles.AUTHORITY_ORG_MEMBER);
        var authorities = List.of(new SimpleGrantedAuthority(springRole));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
```

See [ADR-142](../adr/ADR-142-jwt-claim-extractor-strategy.md).

#### 4.3 Filter Chain Changes

`TenantFilter` and `MemberFilter` currently use `ClerkJwtUtils` directly. They must be updated to use the `JwtClaimExtractor` interface:

**TenantFilter** — change `ClerkJwtUtils.extractOrgId(jwt)` → `claimExtractor.extractOrgId(jwt)`
**MemberFilter** — change `ClerkJwtUtils.extractOrgRole(jwt)` → `claimExtractor.extractOrgRole(jwt)`

Both filters receive `JwtClaimExtractor` via constructor injection. The active profile determines which implementation is injected.

#### 4.4 Member ID Mapping

Clerk uses `user_xxx` format for user IDs. Keycloak uses UUIDs. The `member.clerk_user_id` column stores the external IdP user ID regardless of format.

- With Clerk: `member.clerk_user_id = "user_abc123"` (Clerk user ID from JWT `sub`)
- With Keycloak: `member.clerk_user_id = "f47ac10b-58cc-4372-a567-0e02b2c3d479"` (Keycloak user UUID from JWT `sub`)

**No schema migration needed** — the column is `VARCHAR` and accepts both formats. Consider renaming `clerk_user_id` → `external_user_id` for clarity (cosmetic, optional).

#### 4.5 Webhook/Internal Endpoint Changes

The existing `/internal/orgs/provision` and `/internal/members/sync` endpoints don't change. The Keycloak Event Listener (Section 5) calls them with the same payload format, just with Keycloak IDs instead of Clerk IDs.

---

### 5. Tenant Provisioning (Replacing Clerk Webhooks)

Currently, Clerk fires webhooks → frontend webhook handler → `POST /internal/orgs/provision`. With Keycloak, there are two options. See [ADR-143](../adr/ADR-143-tenant-provisioning-strategy.md).

#### 5.1 Option A: Keycloak Event Listener SPI (Recommended)

A custom Java class deployed as a JAR into Keycloak's `providers/` directory. It listens for organization lifecycle events and calls the backend's internal API.

**Events handled**:

| Keycloak Event | Action | Backend Endpoint |
|---|---|---|
| Organization created (Admin Event) | Provision tenant schema | `POST /internal/orgs/provision` |
| Organization member added | Sync member to tenant schema | `POST /internal/members/sync` |
| Organization member removed | Remove member from tenant schema | `DELETE /internal/members/{id}` |
| Organization member role changed | Update member role | `POST /internal/members/sync` |

**Payload mapping** (Keycloak event → backend internal API):

```json
// POST /internal/orgs/provision
{
  "clerkOrgId": "<keycloak-org-id>",
  "orgName": "<org-display-name>",
  "orgSlug": "<org-alias>"
}

// POST /internal/members/sync
{
  "clerkOrgId": "<keycloak-org-id>",
  "clerkUserId": "<keycloak-user-id>",
  "email": "<user-email>",
  "name": "<user-display-name>",
  "avatarUrl": "<user-picture-url>",
  "orgRole": "<org-role-name>"
}
```

> Note: The field names use `clerk` prefix for backward compatibility with existing backend code. Renaming to `externalOrgId`/`externalUserId` is optional cosmetic cleanup.

**Deployment**: The Event Listener JAR is mounted into Keycloak's `providers/` directory via Docker volume.

#### 5.2 Option B: Just-in-Time Provisioning

On first login, if no tenant schema exists for the user's org, provision it on the fly. This eliminates the need for an Event Listener SPI.

**Flow**:
1. User creates org in Keycloak (via registration or admin) → no backend call yet
2. User logs in → Gateway → JWT contains `organization.acme-corp.id`
3. `TenantFilter` looks up `org_schema_mapping` for the org ID
4. If not found → calls `TenantProvisioningService.provision()` synchronously
5. Full provisioning pipeline executes:
   - Create `Organization` record (public schema)
   - Generate schema name (`tenant_<12-hex>`)
   - `CREATE SCHEMA`
   - Run 54 Flyway tenant migrations (V1–V54)
   - Seed field packs (field definitions + groups from classpath JSON)
   - Seed template packs (document templates + CSS)
   - Seed clause packs (clauses + template associations)
   - Seed compliance packs (checklist templates, retention policies, FICA packs)
   - Seed standard reports (Timesheet, Invoice Aging, Project Profitability)
   - Create `OrgSchemaMapping` record
   - Create `Subscription` (STARTER tier, ACTIVE)
6. Continue with original request

**Member JIT sync**: Similarly, `MemberFilter` detects unknown `sub` claim and calls `MemberSyncService.syncMember()` using claims from the JWT (`email`, `name`, `orgRole`). The Keycloak JWT includes these in standard claims (`email`, `name`) and the custom `organization` claim (`roles`).

**Trade-off**: Adds 2-5 seconds latency to the very first request for a new org (schema creation + 54 migrations + 5 pack seeders). Acceptable for B2B (org creation is rare), but less clean than the event-driven approach. Subsequent requests from the same org are instant (schema mapping cached).

**Idempotency**: The existing provisioning pipeline is fully idempotent at every stage — `OrgSchemaMapping` uniqueness constraint, Flyway migration history, pack status tracking in `OrgSettings`. Safe to retry or trigger from multiple concurrent requests.

**Recommendation**: Start with Option B for the proof-of-concept (zero Keycloak SPI code), then implement Option A for production.

---

### 6. Frontend Changes

#### 6.1 New Auth Provider (`lib/auth/providers/keycloak-bff.ts`)

```typescript
// lib/auth/providers/keycloak-bff.ts
import "server-only";

const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8443";

interface BffUserInfo {
  authenticated: boolean;
  userId: string;
  email: string;
  name: string;
  picture: string;
  orgId: string;
  orgSlug: string;
  orgRole: string;
}

export async function getAuthContext(): Promise<AuthContext> {
  const res = await fetch(`${GATEWAY_URL}/bff/me`, {
    headers: { cookie: getRequestCookies() }, // Forward session cookie from incoming request
    cache: "no-store",
  });
  if (!res.ok) throw new Error("Not authenticated");
  const user: BffUserInfo = await res.json();
  return {
    orgId: user.orgId,
    orgSlug: user.orgSlug,
    orgRole: user.orgRole,
    userId: user.userId,
  };
}

export async function getAuthToken(): Promise<string> {
  // In BFF mode, the frontend never sees tokens.
  // API calls go through the gateway which adds the Bearer token.
  // This function should not be called in keycloak mode.
  throw new Error("getAuthToken() is not available in BFF mode. API calls should route through the gateway.");
}

export async function getCurrentUserEmail(): Promise<string | null> {
  const res = await fetch(`${GATEWAY_URL}/bff/me`, {
    headers: { cookie: getRequestCookies() },
    cache: "no-store",
  });
  if (!res.ok) return null;
  const user: BffUserInfo = await res.json();
  return user.email;
}
```

#### 6.2 API Client Changes

The `lib/api.ts` client currently adds `Authorization: Bearer <token>` to every request. In BFF mode, it sends `credentials: "include"` instead (so the browser includes the session cookie) and adds the CSRF token for mutations.

```typescript
// lib/api.ts — BFF mode
async function apiRequest<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (AUTH_MODE === "keycloak") {
    // BFF mode: CSRF token for mutations, session cookie sent automatically
    if (options.method && options.method !== "GET") {
      const csrf = getCsrfToken();
      if (csrf) headers["X-XSRF-TOKEN"] = csrf;
    }
  } else {
    // Clerk/mock mode: Bearer token
    const token = await getAuthToken();
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
    credentials: AUTH_MODE === "keycloak" ? "include" : "same-origin",
  });
  // ...
}
```

**Important change**: In BFF mode, `API_BASE` points to the gateway origin (not the backend directly). The gateway proxies `/api/**` to the backend.

#### 6.3 Provider Dispatch Update

**`lib/auth/server.ts`**:

```typescript
const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export async function getAuthContext(): Promise<AuthContext> {
  if (AUTH_MODE === "mock") return mockGetAuthContext();
  if (AUTH_MODE === "keycloak") return keycloakGetAuthContext();
  return clerkGetAuthContext();
}
```

#### 6.4 Login/Logout

| Action | Clerk Mode | Keycloak BFF Mode |
|---|---|---|
| Login | Navigate to `/sign-in` (Clerk component) | Redirect to `{gateway}/oauth2/authorization/keycloak` |
| Logout | Clerk's `signOut()` | Redirect to `{gateway}/logout` |
| Registration | Navigate to `/sign-up` (Clerk component) | Handled by Keycloak registration page |

#### 6.5 UI Component Replacements

| Clerk Component | Keycloak Replacement | Notes |
|---|---|---|
| `<SignIn>` | Redirect to Keycloak login page | No custom component needed |
| `<SignUp>` | Redirect to Keycloak registration page | No custom component needed |
| `<UserButton>` | Custom `<UserMenu>` component | Reads from `/bff/me`, shows avatar + name + sign-out |
| `<OrganizationSwitcher>` | **Removed** | Single org per user — no switching needed |
| `useUser()` | Custom `useCurrentUser()` hook | Calls `/bff/me`, caches in React context |
| `useOrganization()` | Custom `useOrgMembers()` hook | Calls backend `/api/members` (already exists) |

**Team management** (`invite-member-form.tsx`, `member-list.tsx`, `pending-invitations.tsx`):
- Currently calls Clerk SDK (`organization.inviteMember()`, `organization.getInvitations()`)
- Change to: server actions that call Keycloak Admin API via gateway
- Gateway proxies `/bff/admin/invite`, `/bff/admin/members`, `/bff/admin/invitations` to Keycloak Admin REST API

---

### 7. Middleware Changes

The Next.js middleware currently uses `clerkMiddleware()` for route protection. In Keycloak BFF mode:

- **Route protection**: Middleware checks for the `SESSION` cookie. If absent on a protected route, redirect to `{gateway}/oauth2/authorization/keycloak`.
- **Org sync from URL**: Not needed — single org per user, org context comes from the JWT (via `/bff/me`).
- **Implementation**: Add `keycloakMiddleware()` alongside `clerkMiddleware()` and `mockMiddleware()`, selected by `AUTH_MODE`.

```typescript
function createKeycloakMiddleware(): NextMiddleware {
  return async (request: NextRequest) => {
    if (isPublicRoute(request)) return NextResponse.next();

    const sessionCookie = request.cookies.get("SESSION");
    if (!sessionCookie) {
      const loginUrl = `${GATEWAY_URL}/oauth2/authorization/keycloak`;
      const returnUrl = request.nextUrl.pathname;
      return NextResponse.redirect(`${loginUrl}?redirect_uri=${encodeURIComponent(returnUrl)}`);
    }

    return NextResponse.next();
  };
}
```

---

### 8. Keycloak Theming

Keycloak's login/registration/email pages must match the DocTeams visual identity. See [ADR-144](../adr/ADR-144-keycloak-theming-strategy.md).

#### 8.1 Approach: Keycloakify

[Keycloakify](https://keycloakify.dev/) is a React-based theming tool that generates Keycloak themes from React components. This allows using the same Tailwind CSS + Shadcn patterns as the main app.

**Advantages over raw Freemarker templates**:
- Write theme in React/TypeScript (familiar to team)
- Use Tailwind CSS (consistent with main app)
- Hot-reload during development
- Output is a standard Keycloak theme JAR

**Theme scope**:

| Page | Customization Level |
|---|---|
| Login (username/password + Google) | Full: match DocTeams branding, logo, colors |
| Registration | Full: match branding, minimal fields (name, email, password) |
| Password reset | Moderate: match branding |
| Email verification | Moderate: match branding |
| Invitation acceptance | Moderate: match branding |
| Error pages | Light: match branding |

**Deployment**: Theme JAR mounted into Keycloak's `themes/` directory via Docker volume. Realm configured to use `docteams` theme.

#### 8.2 Email Templates

Keycloak's email templates (Freemarker) customized for:
- Invitation emails (org name, inviter name, custom branding)
- Password reset
- Email verification
- Login notification (optional)

---

### 9. Docker Compose Integration

#### 9.1 Development Stack

Add Keycloak and Gateway to `compose/docker-compose.yml`:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.5.0
    command: start-dev --import-realm --features=scripts
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: postgres
      KC_DB_PASSWORD: changeme
      KC_HOSTNAME: localhost
      KC_HTTP_PORT: 8180
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json
      - ./keycloak/providers/:/opt/keycloak/providers/  # Event Listener SPI
      - ./keycloak/themes/:/opt/keycloak/themes/        # Custom theme
    ports:
      - "8180:8180"
    depends_on:
      postgres:
        condition: service_healthy

  gateway:
    build:
      context: ../gateway
      dockerfile: Dockerfile
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: app
      DB_USER: postgres
      DB_PASSWORD: changeme
      KEYCLOAK_ISSUER: http://keycloak:8180/realms/docteams
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET}
      BACKEND_URL: http://backend:8080
    ports:
      - "8443:8443"
    depends_on:
      keycloak:
        condition: service_healthy
      postgres:
        condition: service_healthy
```

**Keycloak database**: Separate `keycloak` database in the same PostgreSQL instance (or a separate schema in `app` database). Keycloak manages its own schema — no conflict with app tables or Spring Session tables.

#### 9.2 Realm Export/Import

A `realm-export.json` file contains the full realm configuration:
- Realm settings (name, registration, email verification)
- Client (`gateway-bff`) with secret
- Organization settings
- Google IdP configuration (placeholder client ID/secret for dev)
- Custom protocol mapper
- Default roles
- Email/login theme selection

Imported on first start via `--import-realm`. This makes the dev environment reproducible.

#### 9.3 E2E Stack

The existing E2E stack (`compose/docker-compose.e2e.yml`) continues to use the mock IDP for fast, isolated testing. The Keycloak + Gateway stack is for development and integration testing, not E2E automation.

Rationale: Keycloak login pages involve browser redirects and form submissions that slow down E2E tests. The mock IDP provides instant token issuance for test automation.

---

### 10. Swappable Architecture Summary

The entire system can run in three modes, selected by environment variables:

| Mode | `NEXT_PUBLIC_AUTH_MODE` | `SPRING_PROFILES_ACTIVE` | IdP | Token Flow |
|---|---|---|---|---|
| **Clerk** (current) | `clerk` | `local` | Clerk SaaS | Browser → JWT → Backend |
| **Keycloak** (new) | `keycloak` | `keycloak` | Keycloak self-hosted | Browser → Session cookie → Gateway → JWT → Backend |
| **Mock** (E2E) | `mock` | `e2e` | Mock IDP container | Browser → Mock JWT cookie → Backend |

**Reversibility**: Switching back to Clerk requires only changing env vars and redeploying. No code changes, no database migrations, no data loss. The Gateway and Keycloak simply don't get deployed.

#### 10.1 Production Topology Diagram

```
+---------------------------------------------------------------------------------+
|  Three-Mode Auth Architecture -- Production Topology                            |
|                                                                                 |
|  MODE 1: Clerk (NEXT_PUBLIC_AUTH_MODE=clerk, SPRING_PROFILES_ACTIVE=local)      |
|                                                                                 |
|  +----------+  Clerk JWT  +------------------+  Bearer JWT  +----------------+  |
|  | Browser  |------------>|  Next.js (3000)  |------------->| Spring Boot    |  |
|  | (Clerk   |<------------|  ClerkProvider   |<-------------|  Backend(8080) |  |
|  |  widget) |             |  auth()          |              | Clerk JWKS     |  |
|  +----------+             +------------------+              +----------------+  |
|       |                                                                         |
|       +-------------------> Clerk SaaS (JWT issuance + validation)              |
|                                                                                 |
|  Token flow: Browser authenticates with Clerk widget. Clerk issues a JWT        |
|  stored in a Clerk cookie. Next.js server reads the JWT via auth() and          |
|  forwards it as Authorization: Bearer to the backend. Backend validates          |
|  the JWT signature against Clerk's JWKS endpoint.                               |
|                                                                                 |
|  MODE 2: Keycloak BFF (NEXT_PUBLIC_AUTH_MODE=keycloak,                          |
|                         SPRING_PROFILES_ACTIVE=keycloak)                         |
|                                                                                 |
|  +----------+  SESSION   +------------------+  SESSION   +------------------+   |
|  | Browser  |---cookie-->|  Next.js (3000)  |---cookie-->|  Gateway (8443)  |   |
|  |          |<-----------|  keycloak-bff    |<-----------|  Spring Cloud GW |   |
|  +----------+            |  provider        |            |  /bff/me         |   |
|       |                  +------------------+            +--------+---------+   |
|       |                                                  Bearer JWT |           |
|       |                                                            v            |
|       |                                                  +----------------+     |
|       |                                                  | Spring Boot    |     |
|       |                                                  |  Backend(8080) |     |
|       |                                                  | Keycloak JWKS  |     |
|       |                                                  +----------------+     |
|       |                                                                         |
|       +-----------> Keycloak (8180) ---login/logout---->                        |
|                     (self-hosted)                                                |
|                     Spring Session JDBC (PostgreSQL)                             |
|                                                                                 |
|  Token flow: Browser redirected to Keycloak for login. After authentication,    |
|  Gateway receives OAuth2 tokens and stores them in a server-side session         |
|  (Spring Session JDBC). Browser only receives an opaque SESSION cookie.          |
|  Next.js forwards the SESSION cookie to Gateway's /bff/me for user info.        |
|  Gateway adds a Bearer JWT to proxied requests to the backend.                   |
|                                                                                 |
|  MODE 3: Mock E2E (NEXT_PUBLIC_AUTH_MODE=mock, SPRING_PROFILES_ACTIVE=e2e)      |
|                                                                                 |
|  +----------+  mock-auth  +------------------+  Bearer JWT  +----------------+  |
|  | Browser  |--token----->|  Next.js (3001)  |------------->| Spring Boot    |  |
|  |          |<------------|  mock provider   |<-------------|  Backend(8081) |  |
|  +----------+             |  reads cookie    |              | Mock IDP JWKS  |  |
|       |                   +------------------+              +----------------+  |
|       |                                                                         |
|       +-------------------> Mock IDP (8090)                                     |
|                             (Node.js, RSA keys)                                 |
|                             /token + /jwks.json                                 |
|                                                                                 |
|  Token flow: Playwright fixture calls Mock IDP /token endpoint to get a JWT.    |
|  JWT is stored as mock-auth-token cookie. Next.js server reads the cookie       |
|  and forwards the JWT as Authorization: Bearer to the backend. Backend           |
|  validates against Mock IDP's JWKS endpoint.                                     |
+---------------------------------------------------------------------------------+
```

#### 10.2 Mode Switching Matrix

| To switch from... | ...to Clerk | ...to Keycloak | ...to Mock |
|---|---|---|---|
| Frontend env | `NEXT_PUBLIC_AUTH_MODE=clerk` (default) | `NEXT_PUBLIC_AUTH_MODE=keycloak` | `NEXT_PUBLIC_AUTH_MODE=mock` |
| Backend profile | `SPRING_PROFILES_ACTIVE=local` | `SPRING_PROFILES_ACTIVE=keycloak` | `SPRING_PROFILES_ACTIVE=e2e` |
| Services needed | Clerk SaaS (external) | Keycloak + Gateway (self-hosted) | Mock IDP container |
| Code changes required | None | None | None |
| JIT provisioning | Disabled (Clerk webhooks provision) | Enabled (first request provisions) | Disabled (seed script provisions) |

---

### 11. Implementation Phases

#### Phase A: Foundation (no disruption to current dev)

| # | Task | Files | Notes |
|---|---|---|---|
| A1 | Add Keycloak to Docker Compose (dev stack) | `compose/docker-compose.yml`, `compose/keycloak/` | Keycloak container + realm import |
| A2 | Create realm export with org config, Google IdP placeholder, client | `compose/keycloak/realm-export.json` | Reproducible dev setup |
| A3 | Create Spring Cloud Gateway BFF project | `gateway/` (new module) | Maven project, dependencies, config |
| A4 | Configure Spring Session JDBC | `gateway/` | Auto-create tables in shared PostgreSQL |
| A5 | Test: login via Keycloak, session created, `/bff/me` returns user info | Manual | Prove the flow works end-to-end |
| A6 | Configure org + custom role mapper in Keycloak | `compose/keycloak/realm-export.json` | Org claim with roles in JWT |

#### Phase B: Backend provider-agnostic refactor

| # | Task | Files | Notes |
|---|---|---|---|
| B1 | Create `JwtClaimExtractor` interface | `security/JwtClaimExtractor.java` | Strategy pattern |
| B2 | Extract `ClerkJwtClaimExtractor` from existing utils | `security/ClerkJwtClaimExtractor.java` | `@Profile("!keycloak")` |
| B3 | Create `KeycloakJwtClaimExtractor` | `security/KeycloakJwtClaimExtractor.java` | `@Profile("keycloak")` |
| B4 | Refactor `JwtAuthenticationConverter` to use interface | `security/JwtAuthenticationConverter.java` | Inject `JwtClaimExtractor` |
| B5 | Update `TenantFilter` and `MemberFilter` | `security/TenantFilter.java`, `security/MemberFilter.java` | Use `JwtClaimExtractor` |
| B6 | Add `application-keycloak.yml` | `backend/src/main/resources/` | Keycloak issuer + JWKS |
| B7 | Run existing test suite with both profiles | Tests | Verify nothing breaks |

#### Phase C: Frontend provider

| # | Task | Files | Notes |
|---|---|---|---|
| C1 | Create `lib/auth/providers/keycloak-bff.ts` | Frontend auth | `/bff/me` based auth context |
| C2 | Update `lib/auth/server.ts` dispatch | Frontend auth | Add `keycloak` case |
| C3 | Update `lib/api.ts` for BFF mode | Frontend API | Session cookie + CSRF instead of Bearer |
| C4 | Create `keycloakMiddleware()` | Frontend middleware | Session cookie check |
| C5 | Create custom `<UserMenu>` component | Frontend component | Replace `<UserButton>` |
| C6 | Update login/logout flows | Frontend auth | Redirect to gateway endpoints |

#### Phase D: Team management + theming

| # | Task | Files | Notes |
|---|---|---|---|
| D1 | Create gateway admin proxy endpoints | `gateway/` | `/bff/admin/*` → Keycloak Admin API |
| D2 | Rewire `invite-member-form.tsx` | Frontend component | Call gateway admin proxy |
| D3 | Rewire `member-list.tsx` | Frontend component | Call backend `/api/members` (already works) |
| D4 | Rewire `pending-invitations.tsx` | Frontend component | Call gateway admin proxy |
| D5 | Create Keycloakify theme project | `compose/keycloak/theme/` | React + Tailwind theme |
| D6 | Customize login, registration, password reset pages | Theme | Match DocTeams branding |
| D7 | Customize email templates | Theme | Invitation, verification, reset emails |

#### Phase E: Provisioning + testing

| # | Task | Files | Notes |
|---|---|---|---|
| E1 | Implement JIT tenant provisioning (Option B) | Backend `TenantFilter` | Provision on first request if schema missing |
| E2 | OR: Build Keycloak Event Listener SPI (Option A) | `compose/keycloak/providers/` | Push-based provisioning |
| E3 | Integration test: full signup → org creation → first login → API access | E2E | Complete flow verification |
| E4 | Integration test: invite member → accept → member sync | E2E | Invitation flow |
| E5 | Integration test: Google login → org membership | E2E | Social login flow |
| E6 | Performance test: session lookup overhead | Load test | Verify acceptable latency |
| E7 | Switch env vars, verify Clerk mode still works | Regression | Confirm reversibility |

---

### 12. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Keycloak org role mapper doesn't work as documented | Medium | High | Phase A proves this early; fallback to custom Java SPI mapper |
| Spring Cloud Gateway 5.x incompatibility with Spring Boot 4.0.2 | Low | High | Check version matrix before starting; fallback to 4.3.x + Boot 3.4.x |
| Keycloak theming takes longer than estimated | Medium | Medium | Use minimal theme first (logo + colors only), polish later |
| Session JDBC performance under load | Low | Medium | Monitor query times; switch to Redis if needed |
| CSRF integration complexity with Next.js SSR | Medium | Low | Well-documented pattern; spring-addons library simplifies |
| Keycloak Event Listener SPI complexity | Medium | Medium | Start with JIT provisioning (Option B), add SPI later |
| Keycloak upgrade path (future versions) | Low | Low | Realm export/import makes version upgrades straightforward |

---

## ADR Index

| ADR | Title | Decision |
|---|---|---|
| [ADR-138](../adr/ADR-138-keycloak-jwt-claim-structure.md) | Keycloak JWT claim structure | Use built-in `organization` scope + custom role mapper; single org per user |
| [ADR-139](../adr/ADR-139-keycloak-org-role-mapper.md) | Organization role mapper strategy | Script Mapper first, upgrade to Java SPI if Script Mapper is insufficient |
| [ADR-140](../adr/ADR-140-bff-pattern-token-storage.md) | BFF pattern and token storage | Spring Cloud Gateway BFF with Spring Session JDBC; no tokens in browser |
| [ADR-141](../adr/ADR-141-gateway-servlet-vs-reactive.md) | Gateway servlet vs reactive stack | WebMVC (servlet) to match existing backend stack |
| [ADR-142](../adr/ADR-142-jwt-claim-extractor-strategy.md) | JWT claim extractor strategy | Interface + Spring Profile-based selection for Clerk/Keycloak swap |
| [ADR-143](../adr/ADR-143-tenant-provisioning-strategy.md) | Tenant provisioning without Clerk webhooks | JIT provisioning for MVP, Event Listener SPI for production |
| [ADR-144](../adr/ADR-144-keycloak-theming-strategy.md) | Keycloak theming strategy | Keycloakify (React) for login/registration; Freemarker for emails |
