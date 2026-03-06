# Admin-Approved Tenant Provisioning

> Standalone architecture document. Extends Phase 36 (Keycloak + Gateway BFF Migration). ADR files go in `adr/`.

---

This document describes replacing the current self-registration flow (Clerk webhooks trigger automatic provisioning) with an **admin-approved provisioning flow**. A visitor submits an access request via a public form. A product admin reviews and approves/rejects requests. On approval, the backend orchestrates Keycloak organization creation, tenant schema provisioning, and an owner invitation — all in a single imperative sequence.

**Dependencies**: Phase 20 (auth abstraction layer), Phase 36 (Keycloak + Gateway BFF). This flow assumes Keycloak 26.5+ is the identity provider and the Spring Cloud Gateway BFF is in place.

**Supersedes**: [ADR-143](../adr/ADR-143-tenant-provisioning-strategy.md) (JIT provisioning is no longer the primary path; admin-triggered provisioning replaces it).

### What's New

| Capability | Before (Clerk) | After (Admin-Approved) |
|---|---|---|
| Tenant creation trigger | User self-registers, creates org in Clerk UI, webhook fires | Admin approves an access request, backend orchestrates |
| Provisioning initiator | Clerk webhook (async, eventually consistent) | Admin action (synchronous orchestration) |
| First user onboarding | Automatic — user who created org is immediately the owner | Invitation-based — approved user receives Keycloak org invite with Owner role |
| Failure visibility | Silent webhook failures, retry via Svix dashboard | Admin sees provisioning status, can retry from dashboard |
| Gatekeeping | None — anyone with email can create an org | Explicit admin approval required |
| Public-facing form | Sign-up page (Clerk hosted) | Access request form (custom, no auth required) |

**Out of scope**: Auto-approval rules (e.g., email domain allowlists), bulk import of access requests, self-service org creation for existing authenticated users, multi-org membership.

---

## 1. End-to-End Flow

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Visitor    │     │   Next.js App    │     │  Spring Boot     │     │  Keycloak   │
│  (no auth)   │     │  (public form)   │     │  Backend API     │     │  26.5+      │
└──────┬───────┘     └────────┬─────────┘     └────────┬─────────┘     └──────┬──────┘
       │                      │                        │                      │
       │  1. Fill form        │                        │                      │
       │─────────────────────>│                        │                      │
       │                      │  2. POST /api/         │                      │
       │                      │     access-requests    │                      │
       │                      │───────────────────────>│                      │
       │                      │                        │  3. Persist          │
       │                      │                        │     (PENDING)        │
       │  4. "Request         │                        │                      │
       │     submitted"       │                        │                      │
       │<─────────────────────│                        │                      │
       │                      │                        │                      │
       │                      │                        │                      │
┌──────┴───────┐              │                        │                      │
│ Product      │              │                        │                      │
│ Admin        │              │                        │                      │
└──────┬───────┘              │                        │                      │
       │  5. GET /admin/      │                        │                      │
       │     access-requests  │                        │                      │
       │─────────────────────>│───────────────────────>│                      │
       │                      │                        │                      │
       │  6. POST /admin/     │                        │                      │
       │     access-requests/ │                        │                      │
       │     {id}/approve     │                        │                      │
       │─────────────────────>│───────────────────────>│                      │
       │                      │                        │                      │
       │                      │                        │  7a. Create KC Org   │
       │                      │                        │─────────────────────>│
       │                      │                        │<─────────────────────│
       │                      │                        │                      │
       │                      │                        │  7b. Create schema   │
       │                      │                        │      + seed packs    │
       │                      │                        │  (local DB)          │
       │                      │                        │                      │
       │                      │                        │  7c. Create mapping  │
       │                      │                        │      (org → schema)  │
       │                      │                        │                      │
       │                      │                        │  7d. Invite user     │
       │                      │                        │      (Owner role)    │
       │                      │                        │─────────────────────>│
       │                      │                        │<─────────────────────│
       │                      │                        │                      │
       │                      │                        │  7e. Mark APPROVED   │
       │                      │                        │                      │
       │  8. "Approved —      │                        │                      │
       │     invite sent"     │                        │                      │
       │<─────────────────────│                        │                      │
       │                      │                        │                      │
       │                      │                        │                      │
┌──────┴───────┐              │                        │                      │
│  Invitee     │              │                        │                      │
│  (email)     │              │                        │                      │
└──────┬───────┘              │                        │                      │
       │  9. Click invite     │                        │                      │
       │     link in email    │                        │                      │
       │─────────────────────────────────────────────────────────────────────>│
       │                      │                        │  10. Keycloak hosted │
       │                      │                        │      register/login  │
       │<───────────────────────────────────────────────────────────────────── │
       │                      │                        │                      │
       │  11. Redirect to app │                        │                      │
       │─────────────────────>│                        │                      │
       │                      │  12. First request     │                      │
       │                      │      with JWT          │                      │
       │                      │───────────────────────>│                      │
       │                      │                        │  13. TenantFilter:   │
       │                      │                        │      resolve schema  │
       │                      │                        │  14. MemberFilter:   │
       │                      │                        │      JIT member sync │
       │                      │                        │      (Owner role)    │
       │                      │                        │                      │
       │  15. Dashboard       │                        │                      │
       │<─────────────────────│<───────────────────────│                      │
```

**Key insight**: Steps 7a–7e execute synchronously in a single backend service call. The schema is fully created and seeded before the invitation is sent, eliminating the "user arrives before schema exists" race condition present in the webhook-driven flow.

---

## 2. Access Request Entity Model

### 2.1 Entity Design

The `AccessRequest` entity lives in the **public** schema (not tenant-scoped — it exists before any tenant does).

```java
@Entity
@Table(name = "access_requests", schema = "public")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String contactName;

    @Column(nullable = false, length = 320)
    private String contactEmail;

    @Column(length = 1000)
    private String reason;           // Optional: why they want access

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;  // Null unless REJECTED

    // Provisioning tracking
    private String keycloakOrgId;    // Set after KC org creation (step 7a)
    private String tenantSchema;     // Set after schema creation (step 7b)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant reviewedAt;
    private String reviewedBy;       // Admin user ID who approved/rejected
}
```

### 2.2 Status Lifecycle

```
PENDING ──┬── approve() ──> PROVISIONING ──> APPROVED
          │
          └── reject()  ──> REJECTED
```

| Status | Meaning |
|---|---|
| `PENDING` | Submitted, awaiting admin review |
| `PROVISIONING` | Admin clicked "Approve", orchestration in progress |
| `APPROVED` | Keycloak org created, schema provisioned, invite sent |
| `REJECTED` | Admin rejected with optional reason |
| `FAILED` | Provisioning failed mid-orchestration (admin can retry) |

The `PROVISIONING` status prevents double-clicks and concurrent approval attempts. It is set atomically before orchestration begins.

### 2.3 Database Migration

```sql
-- V55__create_access_requests.sql (global schema)
CREATE TABLE IF NOT EXISTS access_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name    VARCHAR(200)  NOT NULL,
    contact_name    VARCHAR(100)  NOT NULL,
    contact_email   VARCHAR(320)  NOT NULL,
    reason          VARCHAR(1000),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    keycloak_org_id VARCHAR(100),
    tenant_schema   VARCHAR(50),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ,
    reviewed_by     VARCHAR(100)
);

CREATE INDEX idx_access_requests_status ON access_requests(status);
CREATE INDEX idx_access_requests_email ON access_requests(contact_email);
```

### 2.4 API Design

**Public endpoints** (no auth required):

| Method | Path | Description |
|---|---|---|
| `POST /api/access-requests` | Submit a new access request | Body: `{ companyName, contactName, contactEmail, reason? }` |

**Admin endpoints** (requires `PRODUCT_ADMIN` role, not org-scoped):

| Method | Path | Description |
|---|---|---|
| `GET /admin/access-requests` | List all requests (paginated, filterable by status) | Query params: `status`, `page`, `size` |
| `GET /admin/access-requests/{id}` | Get single request details | |
| `POST /admin/access-requests/{id}/approve` | Approve and trigger provisioning | |
| `POST /admin/access-requests/{id}/reject` | Reject with optional reason | Body: `{ reason? }` |
| `POST /admin/access-requests/{id}/retry` | Retry a FAILED provisioning | |

**Security note**: The `/admin/**` endpoints are protected by a new `PRODUCT_ADMIN` realm role in Keycloak (distinct from org-level roles). This is a super-admin who manages the platform, not a per-tenant admin. The Spring Security config adds:

```java
.requestMatchers("/admin/**").hasAuthority("ROLE_PRODUCT_ADMIN")
```

### 2.5 Rate Limiting

The public `POST /api/access-requests` endpoint is rate-limited to prevent abuse:
- **Per-IP**: 5 requests per hour
- **Per-email**: 3 requests per 24 hours (checked against existing requests in DB)
- **Implementation**: Spring Boot rate limiter (Bucket4j or simple DB-count check)

---

## 3. Keycloak Provisioning Service Design

### 3.1 Service Architecture

The `AdminProvisioningService` replaces webhook-triggered provisioning with an imperative orchestration. It reuses the existing `TenantProvisioningService` for schema creation but wraps it with Keycloak API calls.

```java
@Service
public class AdminProvisioningService {

    private final Keycloak keycloakAdmin;           // keycloak-admin-client
    private final TenantProvisioningService tenantProvisioner;
    private final AccessRequestRepository accessRequestRepository;
    private final TransactionTemplate txTemplate;

    /**
     * Orchestrates the full approval flow:
     * 1. Mark request as PROVISIONING (atomic, prevents double-clicks)
     * 2. Create Keycloak Organization
     * 3. Create tenant schema + seed packs
     * 4. Create org_schema_mapping
     * 5. Send invitation to requester with intended Owner role
     * 6. Mark request as APPROVED
     *
     * On failure at any step: mark FAILED, record error, allow retry.
     */
    @Transactional
    public ProvisioningResult approve(UUID accessRequestId) {
        var request = accessRequestRepository.findById(accessRequestId)
            .orElseThrow(() -> new NotFoundException("Access request not found"));

        if (request.getStatus() != AccessRequestStatus.PENDING
            && request.getStatus() != AccessRequestStatus.FAILED) {
            throw new IllegalStateException(
                "Can only approve PENDING or FAILED requests, current: " + request.getStatus());
        }

        // Step 1: Atomic status transition
        request.markProvisioning();
        accessRequestRepository.saveAndFlush(request);

        try {
            // Step 2: Create Keycloak Organization
            String kcOrgId = createKeycloakOrg(request.getCompanyName());
            request.setKeycloakOrgId(kcOrgId);
            accessRequestRepository.saveAndFlush(request);

            // Step 3-4: Create schema + mapping (delegates to existing service)
            var result = tenantProvisioner.provisionTenant(kcOrgId, request.getCompanyName());
            request.setTenantSchema(result.schemaName());
            accessRequestRepository.saveAndFlush(request);

            // Step 5: Send Keycloak invitation
            sendOwnerInvitation(kcOrgId, request);

            // Step 6: Mark approved
            request.markApproved(getCurrentAdminId());
            accessRequestRepository.save(request);

            return ProvisioningResult.success(result.schemaName());

        } catch (Exception e) {
            request.markFailed();
            accessRequestRepository.save(request);
            throw new ProvisioningException(
                "Provisioning failed for request " + accessRequestId, e);
        }
    }
}
```

### 3.2 Keycloak Admin Client Integration

**Dependency** (Maven):

```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>26.0.8</version>
</dependency>
```

**Configuration** (`application.yml`):

```yaml
keycloak:
  admin:
    server-url: ${KEYCLOAK_URL:http://localhost:8080}
    realm: ${KEYCLOAK_REALM:docteams}
    client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:admin-cli}
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:}
    # For service-account auth (preferred over username/password)
```

**Client bean**:

```java
@Configuration
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloakAdminClient(
            @Value("${keycloak.admin.server-url}") String serverUrl,
            @Value("${keycloak.admin.realm}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret) {

        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    }
}
```

### 3.3 Organization Creation

```java
private String createKeycloakOrg(String companyName) {
    OrganizationRepresentation org = new OrganizationRepresentation();
    org.setName(toSlug(companyName));  // "Acme Corp" -> "acme-corp"
    org.setEnabled(true);

    // Custom attributes for internal tracking
    Map<String, List<String>> attrs = new HashMap<>();
    attrs.put("displayName", List.of(companyName));
    attrs.put("tier", List.of("STARTER"));
    org.setAttributes(attrs);

    try (Response response = keycloakAdmin
            .realm(realmName)
            .organizations()
            .create(org)) {

        if (response.getStatus() == 201) {
            // Extract org ID from Location header
            String location = response.getHeaderString("Location");
            return location.substring(location.lastIndexOf('/') + 1);
        }

        throw new ProvisioningException(
            "Keycloak org creation failed: HTTP " + response.getStatus());
    }
}
```

### 3.4 Invitation with Owner Role (Gap Mitigation)

Keycloak 26.5 cannot attach roles to invitations natively (issue #45238). The workaround stores the intended role in the `access_requests` table and assigns it on first login via JIT member sync.

```java
private void sendOwnerInvitation(String kcOrgId, AccessRequest request) {
    // Send invitation via Keycloak Admin API
    keycloakAdmin
        .realm(realmName)
        .organizations()
        .get(kcOrgId)
        .members()
        .inviteUser(
            request.getContactEmail(),
            request.getContactName().split(" ")[0],  // firstName
            request.getContactName().contains(" ")
                ? request.getContactName().substring(
                    request.getContactName().indexOf(' ') + 1)
                : ""  // lastName
        );

    // Store intended role for JIT assignment
    // (read by MemberFilter on first login — see Section 5)
}
```

**JIT role assignment** (in `MemberFilter` or `MemberSyncService`):

When a user first logs in after accepting an invitation, the `MemberFilter` detects no existing member record. It then:
1. Checks the `access_requests` table for a matching `keycloak_org_id` + `contact_email`
2. Reads the intended role (always `owner` for the initial requester)
3. Creates the `Member` entity with `orgRole = "org:owner"`
4. Assigns the Owner role in Keycloak via Admin API:

```java
// Assign org role after user joins
private void assignOrgRole(String kcOrgId, String userId, String roleName) {
    // Get or create the organization role
    OrganizationResource orgResource = keycloakAdmin
        .realm(realmName)
        .organizations()
        .get(kcOrgId);

    // Keycloak org roles are custom — create if not exists
    // Then assign to the member
    // (Exact API depends on KC 26.5 role management endpoints)
}
```

See [ADR-156](../adr/ADR-156-invitation-role-gap-mitigation.md) for full analysis of this workaround.

### 3.5 Failure Handling & Compensation

Since Keycloak API calls and database operations span two transactional systems, failures at different steps require different handling:

| Failure Point | State | Recovery |
|---|---|---|
| Step 2 fails (KC org creation) | `PROVISIONING`, no KC org | Safe to retry — no cleanup needed |
| Step 3 fails (schema creation) | `PROVISIONING`, KC org exists | Retry: `provisionTenant()` is idempotent. Optionally delete KC org on permanent failure |
| Step 5 fails (invitation) | `PROVISIONING`, schema ready | Retry: invitation is idempotent. Schema is complete, only the invite needs resending |
| Step 6 fails (mark approved) | Schema + invite done | Retry: status update is idempotent. User can already accept invite |

The `POST /admin/access-requests/{id}/retry` endpoint handles FAILED requests. It inspects the current state (which steps completed based on `keycloakOrgId`, `tenantSchema` being set) and resumes from the failed step.

```java
public ProvisioningResult retry(UUID accessRequestId) {
    var request = accessRequestRepository.findById(accessRequestId)
        .orElseThrow();

    if (request.getStatus() != AccessRequestStatus.FAILED) {
        throw new IllegalStateException("Can only retry FAILED requests");
    }

    request.markProvisioning();
    accessRequestRepository.saveAndFlush(request);

    try {
        // Resume from where it left off
        String kcOrgId = request.getKeycloakOrgId();
        if (kcOrgId == null) {
            kcOrgId = createKeycloakOrg(request.getCompanyName());
            request.setKeycloakOrgId(kcOrgId);
            accessRequestRepository.saveAndFlush(request);
        }

        if (request.getTenantSchema() == null) {
            var result = tenantProvisioner.provisionTenant(kcOrgId, request.getCompanyName());
            request.setTenantSchema(result.schemaName());
            accessRequestRepository.saveAndFlush(request);
        }

        // Invitation is idempotent — always (re)send
        sendOwnerInvitation(kcOrgId, request);

        request.markApproved(getCurrentAdminId());
        accessRequestRepository.save(request);
        return ProvisioningResult.success(request.getTenantSchema());

    } catch (Exception e) {
        request.markFailed();
        accessRequestRepository.save(request);
        throw new ProvisioningException("Retry failed for request " + accessRequestId, e);
    }
}
```

---

## 4. JWT Claim Mapping (Keycloak → Spring Security)

### 4.1 Keycloak JWT Structure

With the `organization` scope enabled in Keycloak 26.5+, JWTs include:

```json
{
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "email": "alice@acme.com",
  "preferred_username": "alice",
  "realm_access": {
    "roles": ["default-roles-docteams", "PRODUCT_ADMIN"]
  },
  "organization": {
    "acme-corp": {
      "id": "8a3b1c2d-4e5f-6789-abcd-ef0123456789"
    }
  }
}
```

**Key differences from Clerk JWT v2**:

| Aspect | Clerk v2 | Keycloak 26.5 |
|---|---|---|
| Org claim key | `"o"` (Map) | `"organization"` (Map of maps) |
| Org ID location | `o.id` (String) | `organization.<name>.id` (nested) |
| Org role location | `o.rol` (String) | Not in JWT by default (see 4.3) |
| Org slug location | `o.slg` (String) | Map key (e.g., `"acme-corp"`) |
| Multi-org | Single org in claim | Map may contain multiple entries |
| Realm roles | N/A | `realm_access.roles` (List) |

### 4.2 Updated `ClerkJwtUtils` (Rename to `JwtClaimExtractor`)

The existing `ClerkJwtUtils` already has Keycloak format detection. With admin-approved provisioning, the Clerk path can be removed entirely (Clerk is gone). Rename to `JwtClaimExtractor`:

```java
public final class JwtClaimExtractor {

    private static final String KC_ORG_CLAIM = "organization";

    /**
     * Extract the active organization ID from a Keycloak JWT.
     * Single-org constraint: takes the first (and only) entry.
     */
    public static String extractOrgId(Jwt jwt) {
        Map<String, Object> orgClaim = jwt.getClaim(KC_ORG_CLAIM);
        if (orgClaim == null || orgClaim.isEmpty()) return null;

        // Single-org: first entry is the active org
        var firstEntry = orgClaim.entrySet().iterator().next();
        Object value = firstEntry.getValue();

        if (value instanceof Map<?, ?> orgData) {
            Object id = orgData.get("id");
            if (id instanceof String s) return s;
        }

        // Fallback: org name as identifier (shouldn't happen with proper mapper config)
        return firstEntry.getKey();
    }

    /**
     * Extract the org slug (Keycloak org name).
     */
    public static String extractOrgSlug(Jwt jwt) {
        Map<String, Object> orgClaim = jwt.getClaim(KC_ORG_CLAIM);
        if (orgClaim == null || orgClaim.isEmpty()) return null;
        return orgClaim.keySet().iterator().next();
    }

    /**
     * Extract org role. Keycloak does NOT include org roles in JWTs by default.
     * Fallback: look up from Member entity via MemberFilter.
     *
     * If a custom protocol mapper is configured, the structure would be:
     * { "organization": { "acme-corp": { "id": "...", "roles": ["owner"] } } }
     */
    public static String extractOrgRole(Jwt jwt) {
        Map<String, Object> orgClaim = jwt.getClaim(KC_ORG_CLAIM);
        if (orgClaim == null || orgClaim.isEmpty()) return null;

        var firstEntry = orgClaim.entrySet().iterator().next();
        Object value = firstEntry.getValue();

        if (value instanceof Map<?, ?> orgData) {
            Object roles = orgData.get("roles");
            if (roles instanceof List<?> roleList && !roleList.isEmpty()) {
                String role = roleList.getFirst().toString();
                // Normalize to org:role format
                return role.startsWith("org:") ? role : "org:" + role;
            }
        }

        // Role not in JWT — MemberFilter will resolve from DB
        return null;
    }

    /**
     * Check for PRODUCT_ADMIN realm role (platform super-admin).
     */
    public static boolean isProductAdmin(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return false;

        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> roleList) {
            return roleList.contains("PRODUCT_ADMIN");
        }
        return false;
    }
}
```

### 4.3 Org Role Resolution Strategy

Keycloak's built-in `organization` scope mapper does **not** include org-specific roles in the JWT. Three options exist:

| Option | Pros | Cons | Chosen |
|---|---|---|---|
| **A: Custom protocol mapper** | Roles in every JWT, no DB lookup needed | Must deploy a custom mapper JAR into Keycloak, maintenance burden | No |
| **B: DB-backed role lookup** | Zero Keycloak customization, role changes take effect immediately | Extra DB query per request (cacheable) | **Yes** |
| **C: Phase Two extension** | Rich org claims in JWT, managed extension | Third-party dependency, may diverge from upstream KC | No |

**Decision**: Use **Option B** — the `MemberFilter` already resolves the member record per request. The `Member.orgRole` field is the source of truth. This matches the current system where the role stored in the DB (synced from Clerk) is used for authorization decisions.

The flow:
1. `TenantFilter` extracts org ID from JWT → resolves tenant schema
2. `MemberFilter` looks up `Member` by user ID in tenant schema → binds `RequestScopes.ORG_ROLE`
3. Controllers/services read `RequestScopes.ORG_ROLE` (unchanged)

This means `JwtClaimExtractor.extractOrgRole()` returning `null` is the expected path. The role is resolved from the database, not the token.

### 4.4 TenantFilter Changes

Minimal changes needed — the filter already supports Keycloak JWT format:

```java
// Before (dual-provider detection)
String orgId = ClerkJwtUtils.extractOrgId(jwt);

// After (Keycloak only)
String orgId = JwtClaimExtractor.extractOrgId(jwt);
```

The JIT provisioning path in `TenantFilter.attemptJitProvisioning()` can be **retained as a safety net** but should not be the primary provisioning path. The schema should already exist by the time a user accepts an invitation (admin provisioned it during approval).

### 4.5 Spring Security Config Changes

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            // Public endpoints (no auth)
            .requestMatchers("/api/access-requests").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/portal/**").permitAll()

            // Product admin endpoints (realm role, not org role)
            .requestMatchers("/admin/**").hasAuthority("ROLE_PRODUCT_ADMIN")

            // Internal endpoints (API key auth, separate filter)
            .requestMatchers("/internal/**").permitAll()

            // Everything else requires authentication
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtAuthenticationConverter(keycloakJwtAuthConverter())
            )
        )
        .build();
}

@Bean
public JwtAuthenticationConverter keycloakJwtAuthConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthorities =
        new JwtGrantedAuthoritiesConverter();

    // Custom converter to extract realm_access.roles
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        // Standard scope-based authorities
        Collection<GrantedAuthority> authorities =
            new ArrayList<>(grantedAuthorities.convert(jwt));

        // Add realm roles (e.g., PRODUCT_ADMIN)
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof List<?> roleList) {
                roleList.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .forEach(authorities::add);
            }
        }

        return authorities;
    });

    return converter;
}
```

---

## 5. Member JIT Sync (First Login After Invite Acceptance)

When a user accepts a Keycloak invitation and logs in for the first time, the backend needs to create a `Member` record in the tenant schema. This happens in the existing `MemberFilter`:

### 5.1 Enhanced MemberFilter Flow

```
JWT arrives with org claim → TenantFilter resolves schema → MemberFilter:
  1. Look up Member by userId in tenant schema
  2. If found: bind RequestScopes.MEMBER_ID, ORG_ROLE → continue
  3. If NOT found (first login):
     a. Check access_requests table for matching (keycloak_org_id, contact_email)
     b. Determine role: if access_requests match → "org:owner", else → "org:member"
     c. Create Member entity with orgRole
     d. Bind RequestScopes → continue
```

### 5.2 Role Determination Priority

On first login, the intended role is determined by:

1. **Access request match**: If `access_requests.keycloak_org_id` matches the JWT's org ID AND `contact_email` matches the JWT's email → `org:owner` (this is the original requester)
2. **Subsequent invitations**: For users invited by the org owner/admin later (via the product UI) → default to `org:member` unless a role is specified in a separate invitation metadata table
3. **Fallback**: `org:member`

---

## 6. Frontend Components

### 6.1 Public Access Request Form

Route: `/request-access` (public, no auth required)

```
┌─────────────────────────────────────┐
│  Request Access to DocTeams         │
│                                     │
│  Company Name    [_______________]  │
│  Your Name       [_______________]  │
│  Email Address   [_______________]  │
│  Why do you need access? (optional) │
│  [_________________________________]│
│  [_________________________________]│
│                                     │
│  [ Submit Request ]                 │
│                                     │
│  ✓ We'll review your request and    │
│    send you an invitation if        │
│    approved.                        │
└─────────────────────────────────────┘
```

- Server Action submits to `POST /api/access-requests`
- Shows success confirmation after submission
- Rate-limited (see 2.5)

### 6.2 Admin Access Request Dashboard

Route: `/admin/access-requests` (requires `PRODUCT_ADMIN` role)

This is a **platform-level** admin page, not org-scoped. It lives outside the `(app)/org/[slug]/` route group.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Access Requests                                    [Filter: All ▾]│
│─────────────────────────────────────────────────────────────────────│
│  Company         Contact           Email           Status   Action │
│  Acme Corp       Alice Johnson     alice@acme.com  PENDING  [✓][✗]│
│  Beta LLC        Bob Smith         bob@beta.io     PENDING  [✓][✗]│
│  Gamma Inc       Carol Lee         carol@gamma.co  APPROVED  —    │
│  Delta Ltd       Dave Kim          dave@delta.com  REJECTED  —    │
│  Epsilon Co      Eve Park          eve@eps.com     FAILED   [↻]   │
│─────────────────────────────────────────────────────────────────────│
│  Showing 1-5 of 12                              [◀ 1 2 3 ▶]      │
└─────────────────────────────────────────────────────────────────────┘
```

- Approve action calls `POST /admin/access-requests/{id}/approve`
- Reject shows a dialog for optional reason
- Retry button for FAILED provisioning
- Filter by status (PENDING, APPROVED, REJECTED, FAILED)

---

## 7. Impact on Existing System

### 7.1 Files to Remove

| File/Package | Reason |
|---|---|
| `frontend/app/api/webhooks/clerk/route.ts` | No more Clerk webhooks |
| `frontend/lib/webhook-handlers.ts` | Clerk event routing logic |
| `frontend/app/(auth)/sign-up/` | No self-registration page |
| `frontend/lib/auth/providers/clerk.ts` | Clerk provider implementation |
| `backend/.../webhook/ProcessedWebhook.java` | Clerk webhook idempotency tracking |
| `backend/.../webhook/ProcessedWebhookRepository.java` | Same |

### 7.2 Files to Modify

| File | Change |
|---|---|
| `ClerkJwtUtils.java` → `JwtClaimExtractor.java` | Remove Clerk format, keep Keycloak only |
| `TenantFilter.java` | Update JWT extractor import, keep JIT as safety net |
| `MemberFilter.java` / `MemberSyncService.java` | Add JIT member creation with access-request role lookup |
| `SecurityConfig.java` | Add `/api/access-requests` permit, `/admin/**` product-admin role check, Keycloak JWT converter |
| `TenantProvisioningService.java` | No changes — called by `AdminProvisioningService` |
| `frontend/lib/auth/server.ts` | Remove Clerk dispatch, Keycloak-only |
| `frontend/lib/auth/middleware.ts` | Remove Clerk middleware path |
| `frontend/app/(app)/create-org/` | Remove or redirect to request-access |

### 7.3 Files to Add

| File | Purpose |
|---|---|
| `backend/.../accessrequest/AccessRequest.java` | Entity |
| `backend/.../accessrequest/AccessRequestRepository.java` | Repository |
| `backend/.../accessrequest/AccessRequestService.java` | CRUD + validation |
| `backend/.../accessrequest/AccessRequestController.java` | Public + admin endpoints |
| `backend/.../provisioning/AdminProvisioningService.java` | Orchestrates KC org + schema + invite |
| `backend/.../config/KeycloakAdminConfig.java` | KC admin client bean |
| `db/migration/global/V55__create_access_requests.sql` | DDL migration |
| `frontend/app/request-access/page.tsx` | Public form |
| `frontend/app/admin/access-requests/page.tsx` | Admin dashboard |
| `frontend/components/admin/access-request-table.tsx` | Table component |

### 7.4 What Stays Unchanged

- All 54 tenant migrations (V1–V54)
- All 6 pack seeders
- `SchemaMultiTenantConnectionProvider`, `TenantIdentifierResolver`
- `RequestScopes` (ScopedValue bindings)
- `org_schema_mapping` table and resolution logic
- All domain entities and business logic (projects, customers, tasks, time entries, invoices, etc.)
- Frontend pages within `(app)/org/[slug]/` (dashboard, projects, team, settings, etc.)

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| KC org creation succeeds but schema fails → orphaned KC org | Low | Medium | Retry endpoint resumes from failed step; compensation deletes KC org on permanent failure |
| Invitation email not delivered | Medium | Medium | Admin dashboard shows status; resend button available |
| Role not assigned on first login (JIT gap) | Low | High | Access request table is source of truth for intended role; fallback to `org:member` is safe |
| Admin bottleneck — all tenants require manual approval | Medium | Low | Acceptable for B2B SaaS; auto-approval rules can be added later |
| Keycloak downtime blocks provisioning | Low | High | Admin sees FAILED status, retries when KC is back. Schema creation is independent of KC |
| Public form spam | Medium | Low | Rate limiting (IP + email), optional CAPTCHA for high-volume periods |

---

## 9. ADRs

- [ADR-154: Admin-Approved Provisioning Flow](../adr/ADR-154-admin-approved-provisioning-flow.md) — Supersedes ADR-143
- [ADR-155: Access Request Lifecycle Model](../adr/ADR-155-access-request-lifecycle-model.md)
- [ADR-156: Keycloak Invitation Role Gap Mitigation](../adr/ADR-156-invitation-role-gap-mitigation.md)
