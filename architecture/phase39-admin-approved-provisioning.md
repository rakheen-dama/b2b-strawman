# Phase 39 — Admin-Approved Org Provisioning

> Standalone architecture document. ADR files go in `adr/`.

---

## 39. Phase 39 — Admin-Approved Org Provisioning

Phase 39 replaces the self-registration provisioning flow with an **admin-approved access request pipeline**. Instead of users self-creating organizations (via Keycloak or Clerk), prospective customers submit a public access request form with company email verification (OTP). A platform administrator reviews pending requests and approves or rejects them. On approval, the system creates a Keycloak Organization, provisions the tenant schema, and sends a Keycloak invitation to the requester with an Owner role. The requester accepts the invite, registers, and on first login is JIT-synced as the tenant owner.

This phase introduces the concept of a **platform admin** — a cross-tenant role that does not exist in the current system. Platform admins are identified via a Keycloak group (`platform-admins`) whose membership is managed in the Keycloak admin console. The group claim is included in the JWT, and the gateway + backend enforce access to platform-admin endpoints.

**Dependencies on prior phases**:
- **Phase 36** (Keycloak + Gateway BFF): `KeycloakAdminClient` (org creation, invitations), Gateway BFF session management, auth abstraction layer
- **Phase 13** (Dedicated Schema): `TenantProvisioningService` (schema creation, Flyway migrations, data pack seeding)
- **Phase 6.5** (Notifications): Email sending infrastructure (reused for OTP delivery)

### What's New

| Capability | Before Phase 39 | After Phase 39 |
|---|---|---|
| Org creation trigger | Self-service (user creates org, JIT provisioning) | Admin approval triggers provisioning |
| Entry point | Keycloak registration + org creation | Public access request form (no auth required) |
| Email verification | Keycloak handles at registration | OTP verification on access request form (before any Keycloak interaction) |
| Platform admin role | Does not exist | `platform-admins` Keycloak group with dedicated admin panel |
| Access request tracking | -- | `access_requests` table in public schema with full audit trail |
| JIT provisioning | Enabled (schema created on first authenticated request) | Disabled (schema created at approval time) |
| Spam/abuse prevention | Keycloak CAPTCHA / rate limiting | Company email validation (block free providers) + OTP verification |

**Out of scope**: Rejection notification emails (silent rejection for now), custom seed packs per industry/vertical (all current packs seeded uniformly), multi-product platform admin panel (single product for now, designed for future expansion), self-service org creation for existing platform admins, access request editing after submission, waitlist/queuing mechanics.

**Constraint**: Single Keycloak realm (`docteams`). Platform admins are users in the same realm, distinguished by group membership. This scales to multiple products sharing the same Keycloak instance — each product checks for its own group (e.g., `platform-admins-docteams`, `platform-admins-product-b`).

---

### 39.1 Overview

Phase 39 adds a pre-authentication layer to the tenant onboarding pipeline. The current flow (user registers -> creates org -> JIT provisions) is replaced with a gated flow where provisioning only happens after explicit admin approval. This is common in B2B SaaS where customer onboarding involves sales qualification, compliance checks, or capacity planning.

The core abstractions:

1. **AccessRequest** — A public-schema entity capturing the prospective customer's details (email, name, org name, country, industry). Tracks OTP verification and approval status.
2. **OTP Verification** — Time-limited one-time password sent to the requester's company email. Verified before the request is persisted as `PENDING`. Blocks free email providers (gmail.com, yahoo.com, etc.).
3. **Platform Admin Panel** — A dedicated frontend route (`/platform-admin/access-requests`) visible only to users in the `platform-admins` Keycloak group. Lists pending requests with approve/reject actions.
4. **Approval Pipeline** — On approval: create Keycloak org -> provision tenant schema -> send Keycloak invitation to requester with Owner role. Reuses existing `KeycloakAdminClient` and `TenantProvisioningService`.
5. **Invitation Acceptance** — Requester receives Keycloak invitation email, clicks link, registers account, and on first login is JIT-synced as tenant owner via existing `MemberFilter` / member sync.

---

### 39.2 Flow Sequence

```
Visitor                    Backend              Keycloak         Platform Admin
  |                          |                     |               |
  |-- POST /access-requests ->|                    |               |
  |   (email, name, org,     |                     |               |
  |    country, industry)    |                     |               |
  |                          |-- Validate company  |               |
  |                          |   email domain      |               |
  |                          |-- Generate OTP      |               |
  |                          |-- Send OTP email -->-|---> SMTP      |
  |<-- 200 "Check email" ----|                     |               |
  |                          |                     |               |
  |-- POST /access-requests/ |                     |               |
  |   verify (email + OTP) ->|                     |               |
  |                          |-- Verify OTP        |               |
  |                          |-- Save request      |               |
  |                          |   (status: PENDING) |               |
  |<-- 200 "Submitted" ------|                     |               |
  |                          |                     |               |
  |                          |                     |          +----|
  |                          |<--------------------|---------| GET /access-requests
  |                          |-- Return list ----->|-------->| (review queue)
  |                          |                     |          |    |
  |                          |<--------------------|---------| POST /{id}/approve
  |                          |                     |          +----|
  |                          |-- Create KC Org --->|               |
  |                          |-- Provision schema  |               |
  |                          |   (Flyway + seeds)  |               |
  |                          |-- Send KC invite -->|---> Email     |
  |                          |-- Mark APPROVED     |               |
  |                          |                     |               |
  |<--- Invitation email ----|---------------------|               |
  |-- Click invite link ---->|-------------------->|               |
  |-- Register (set pwd) --->|-------------------->|               |
  |-- Login via Gateway ---->|-------------------->|               |
  |                          |-- JIT member sync   |               |
  |                          |   (Owner role)      |               |
  |<--- Dashboard ----------|                     |               |
```

---

### 39.3 Domain Model

#### 39.3.1 AccessRequest Entity (New — Public Schema)

An AccessRequest captures a prospective customer's details and tracks the approval lifecycle.

| Field | Java Type | DB Column | DB Type | Constraints | Notes |
|-------|-----------|-----------|---------|-------------|-------|
| `id` | `UUID` | `id` | `UUID` | PK, default `gen_random_uuid()` | Auto-generated |
| `email` | `String` | `email` | `VARCHAR(255)` | NOT NULL | Company email (validated, OTP-verified) |
| `fullName` | `String` | `full_name` | `VARCHAR(200)` | NOT NULL | Requester's name |
| `organizationName` | `String` | `organization_name` | `VARCHAR(200)` | NOT NULL | Desired org name |
| `country` | `String` | `country` | `VARCHAR(100)` | NOT NULL | Country |
| `industry` | `String` | `industry` | `VARCHAR(100)` | NOT NULL | Industry/vertical |
| `status` | `AccessRequestStatus` | `status` | `VARCHAR(20)` | NOT NULL, default `'PENDING'` | `PENDING_VERIFICATION`, `PENDING`, `APPROVED`, `REJECTED` |
| `otpHash` | `String` | `otp_hash` | `VARCHAR(255)` | Nullable | BCrypt hash of OTP (cleared after verification) |
| `otpExpiresAt` | `Instant` | `otp_expires_at` | `TIMESTAMPTZ` | Nullable | OTP expiry (e.g., 10 minutes) |
| `otpAttempts` | `int` | `otp_attempts` | `INTEGER` | NOT NULL, default 0 | Rate limit OTP guessing (max 5) |
| `otpVerifiedAt` | `Instant` | `otp_verified_at` | `TIMESTAMPTZ` | Nullable | When email was verified |
| `reviewedBy` | `String` | `reviewed_by` | `VARCHAR(255)` | Nullable | Platform admin user ID who approved/rejected |
| `reviewedAt` | `Instant` | `reviewed_at` | `TIMESTAMPTZ` | Nullable | When the decision was made |
| `keycloakOrgId` | `String` | `keycloak_org_id` | `VARCHAR(255)` | Nullable | Set on approval after KC org creation |
| `provisioningError` | `String` | `provisioning_error` | `TEXT` | Nullable | Captures error if provisioning fails during approval |
| `createdAt` | `Instant` | `created_at` | `TIMESTAMPTZ` | NOT NULL | Immutable |
| `updatedAt` | `Instant` | `updated_at` | `TIMESTAMPTZ` | NOT NULL | Updated on mutation |

**Unique constraint**: `(email, status)` partial unique index where `status IN ('PENDING_VERIFICATION', 'PENDING')` — prevents duplicate pending requests for the same email.

**Indexes**:
- `idx_access_requests_status` on `status` (admin query: list pending)
- `idx_access_requests_email` on `email` (lookup for OTP verification)

#### 39.3.2 AccessRequestStatus Enum

```java
public enum AccessRequestStatus {
    PENDING_VERIFICATION,  // Form submitted, OTP not yet verified
    PENDING,               // OTP verified, awaiting admin review
    APPROVED,              // Admin approved, provisioning complete
    REJECTED               // Admin rejected (silent)
}
```

#### 39.3.3 Blocked Email Domains

A configurable list of free/personal email providers. Validated server-side on form submission.

```yaml
app:
  access-request:
    blocked-email-domains:
      - gmail.com
      - yahoo.com
      - hotmail.com
      - outlook.com
      - aol.com
      - icloud.com
      - mail.com
      - protonmail.com
      - zoho.com
    otp-expiry-minutes: 10
    otp-max-attempts: 5
```

---

### 39.4 Platform Admin Identity

#### 39.4.1 Keycloak Group

A Keycloak group `platform-admins` is created in the `docteams` realm. Users added to this group receive the group membership in their JWT via a Group Membership Mapper (built-in Keycloak protocol mapper — no custom SPI needed).

**JWT claim** (after adding Group Membership Mapper to `gateway-bff` client scope):

```json
{
  "sub": "user-uuid",
  "groups": ["platform-admins"],
  "organization": { "some-org": { "id": "...", "roles": ["owner"] } }
}
```

**Future multi-product expansion**: When adding Product B, create a `platform-admins-product-b` group. Each product's gateway checks for its own group. The admin panel routes are product-specific.

#### 39.4.2 Backend Authorization

The backend extracts the `groups` claim from the JWT and exposes it via `RequestScopes`:

```java
// New ScopedValue
public static final ScopedValue<Set<String>> GROUPS = ScopedValue.newInstance();

// In TenantFilter or a new PlatformAdminFilter
Set<String> groups = ClerkJwtUtils.extractGroups(jwt);
ScopedValue.where(RequestScopes.GROUPS, groups).run(() -> ...);
```

Platform admin endpoints use `@PreAuthorize`:

```java
@PreAuthorize("@platformSecurity.isPlatformAdmin()")
@GetMapping("/api/platform-admin/access-requests")
public List<AccessRequestDto> listPendingRequests() { ... }
```

#### 39.4.3 Gateway Routing

The gateway proxies `/api/platform-admin/**` to the backend, same as existing `/api/**` routes. The backend enforces authorization, not the gateway.

#### 39.4.4 Frontend Route Guard

The platform admin panel lives at `/platform-admin/access-requests`. The route checks for the `platform-admins` group in the auth context:

```typescript
// In getAuthContext() — extend AuthContext with groups
interface AuthContext {
  userId: string;
  orgId: string | null;
  orgSlug: string | null;
  orgRole: string | null;
  groups: string[];  // NEW
}
```

Route layout checks `groups.includes("platform-admins")` and renders 404 for non-admins.

---

### 39.5 API Contracts

#### 39.5.1 Public Endpoints (No Authentication)

**Submit Access Request**

```
POST /api/access-requests
Content-Type: application/json

{
  "email": "jane@acme-corp.com",
  "fullName": "Jane Smith",
  "organizationName": "Acme Corp",
  "country": "South Africa",
  "industry": "Accounting"
}

Response 200:
{
  "message": "Verification code sent to jane@acme-corp.com",
  "expiresInMinutes": 10
}

Response 400 (blocked domain):
{
  "error": "Please use a company email address"
}

Response 409 (duplicate pending):
{
  "error": "A request for this email is already pending"
}
```

**Verify OTP**

```
POST /api/access-requests/verify
Content-Type: application/json

{
  "email": "jane@acme-corp.com",
  "otp": "847293"
}

Response 200:
{
  "message": "Email verified. Your access request has been submitted for review."
}

Response 400 (invalid/expired):
{
  "error": "Invalid or expired verification code"
}

Response 429 (too many attempts):
{
  "error": "Too many attempts. Please request a new code."
}
```

#### 39.5.2 Platform Admin Endpoints (Requires `platform-admins` Group)

**List Access Requests**

```
GET /api/platform-admin/access-requests?status=PENDING
Authorization: Bearer <jwt-with-platform-admins-group>

Response 200:
{
  "content": [
    {
      "id": "uuid",
      "email": "jane@acme-corp.com",
      "fullName": "Jane Smith",
      "organizationName": "Acme Corp",
      "country": "South Africa",
      "industry": "Accounting",
      "status": "PENDING",
      "otpVerifiedAt": "2026-03-07T10:00:00Z",
      "createdAt": "2026-03-07T09:55:00Z"
    }
  ]
}
```

**Approve Request**

```
POST /api/platform-admin/access-requests/{id}/approve
Authorization: Bearer <jwt-with-platform-admins-group>

Response 200:
{
  "message": "Organization provisioned and invitation sent",
  "keycloakOrgId": "kc-org-uuid",
  "schemaName": "tenant_abc123def456"
}

Response 409 (already processed):
{
  "error": "Request has already been processed"
}

Response 500 (provisioning failure):
{
  "error": "Provisioning failed: <details>",
  "requestStatus": "PENDING"  // Not marked approved if provisioning fails
}
```

**Reject Request**

```
POST /api/platform-admin/access-requests/{id}/reject
Authorization: Bearer <jwt-with-platform-admins-group>

Response 200:
{
  "message": "Request rejected"
}
```

---

### 39.6 Approval Pipeline (Backend)

The approval handler orchestrates the full provisioning sequence. This is the critical path — it must be atomic (all-or-nothing) and idempotent.

```java
@Transactional
public AccessRequest approve(UUID requestId, String adminUserId) {
    AccessRequest request = repository.findById(requestId)
        .orElseThrow(() -> new NotFoundException("Access request not found"));

    if (request.getStatus() != AccessRequestStatus.PENDING) {
        throw new ConflictException("Request already processed");
    }

    try {
        // 1. Create Keycloak Organization
        String orgAlias = slugify(request.getOrganizationName());
        String kcOrgId = keycloakAdminClient.createOrganization(
            request.getOrganizationName(), orgAlias);

        // 2. Provision tenant schema (reuses existing service)
        String schemaName = tenantProvisioningService.provisionTenant(
            kcOrgId, request.getOrganizationName());

        // 3. Send Keycloak invitation to requester (Owner role)
        keycloakAdminClient.inviteUser(kcOrgId, request.getEmail());

        // 4. Mark approved
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setKeycloakOrgId(kcOrgId);
        request.setReviewedBy(adminUserId);
        request.setReviewedAt(Instant.now());

        return repository.save(request);

    } catch (Exception e) {
        // Capture error but don't mark as approved
        request.setProvisioningError(e.getMessage());
        repository.save(request);
        throw new ProvisioningException("Approval failed: " + e.getMessage(), e);
    }
}
```

**Idempotency**: If the admin retries after a partial failure:
- `createOrganization()` — Keycloak returns existing org if alias matches
- `provisionTenant()` — already idempotent (checks for existing schema)
- `inviteUser()` — Keycloak ignores duplicate invitations

---

### 39.7 OTP Verification Flow (Backend)

```java
public void submitRequest(AccessRequestSubmission dto) {
    // 1. Validate company email domain
    String domain = dto.email().substring(dto.email().indexOf('@') + 1);
    if (blockedDomains.contains(domain.toLowerCase())) {
        throw new BadRequestException("Please use a company email address");
    }

    // 2. Check for existing pending request
    if (repository.existsPendingByEmail(dto.email())) {
        throw new ConflictException("A request for this email is already pending");
    }

    // 3. Generate 6-digit OTP
    String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

    // 4. Save request with hashed OTP
    AccessRequest request = new AccessRequest();
    request.setEmail(dto.email());
    request.setFullName(dto.fullName());
    request.setOrganizationName(dto.organizationName());
    request.setCountry(dto.country());
    request.setIndustry(dto.industry());
    request.setStatus(AccessRequestStatus.PENDING_VERIFICATION);
    request.setOtpHash(passwordEncoder.encode(otp));
    request.setOtpExpiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES));
    repository.save(request);

    // 5. Send OTP email
    emailService.sendOtp(dto.email(), otp, dto.fullName());
}

public void verifyOtp(String email, String otp) {
    AccessRequest request = repository.findByEmailAndStatus(
        email, AccessRequestStatus.PENDING_VERIFICATION)
        .orElseThrow(() -> new NotFoundException("No pending verification for this email"));

    if (request.getOtpAttempts() >= maxOtpAttempts) {
        throw new TooManyRequestsException("Too many attempts. Please request a new code.");
    }

    if (request.getOtpExpiresAt().isBefore(Instant.now())) {
        throw new BadRequestException("Verification code has expired");
    }

    request.setOtpAttempts(request.getOtpAttempts() + 1);

    if (!passwordEncoder.matches(otp, request.getOtpHash())) {
        repository.save(request);
        throw new BadRequestException("Invalid verification code");
    }

    // OTP verified — promote to PENDING
    request.setStatus(AccessRequestStatus.PENDING);
    request.setOtpVerifiedAt(Instant.now());
    request.setOtpHash(null);  // Clear OTP after verification
    repository.save(request);
}
```

---

### 39.8 Frontend Components

#### 39.8.1 Public Access Request Form

**Route**: `/request-access` (outside `(app)` layout — no auth required)

**Components**:
- `RequestAccessForm` — Multi-step form:
  - Step 1: Email, Full Name, Organization Name, Country, Industry
  - Step 2: OTP input (shown after form submission)
  - Step 3: Success message ("Your request has been submitted for review")
- Country field: dropdown or combobox
- Industry field: dropdown (predefined list matching existing seed data categories)
- Email validation: real-time domain check (client-side blocked domain list + server-side validation)

#### 39.8.2 Platform Admin Panel

**Route**: `/platform-admin/access-requests` (inside `(app)` layout — requires auth + `platform-admins` group)

**Components**:
- `AccessRequestsTable` — Filterable table of access requests
  - Columns: Org Name, Email, Name, Country, Industry, Submitted, Status
  - Filters: Status (PENDING / APPROVED / REJECTED / ALL)
  - Default view: PENDING requests sorted by creation date (oldest first)
- `ApproveDialog` — Confirmation dialog before approving (shows org name, email, confirms provisioning will happen)
- `RejectDialog` — Simple confirmation dialog

**Layout**: New `(platform-admin)` route group with its own layout that checks for `platform-admins` group membership. Renders a simple sidebar with "Access Requests" link. Designed to accommodate future platform-admin pages.

---

### 39.9 Database Migration

**Migration**: Next available global migration version (public schema).

```sql
-- V__create_access_requests.sql (global migration, public schema)

CREATE TABLE access_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    organization_name VARCHAR(200) NOT NULL,
    country         VARCHAR(100) NOT NULL,
    industry        VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    otp_hash        VARCHAR(255),
    otp_expires_at  TIMESTAMPTZ,
    otp_attempts    INTEGER NOT NULL DEFAULT 0,
    otp_verified_at TIMESTAMPTZ,
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ,
    keycloak_org_id VARCHAR(255),
    provisioning_error TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Prevent duplicate pending requests for same email
CREATE UNIQUE INDEX idx_access_requests_email_pending
    ON access_requests (email)
    WHERE status IN ('PENDING_VERIFICATION', 'PENDING');

CREATE INDEX idx_access_requests_status ON access_requests (status);
CREATE INDEX idx_access_requests_email ON access_requests (email);
```

---

### 39.10 Keycloak Configuration Changes

1. **Create `platform-admins` group** in `docteams` realm
2. **Add Group Membership Mapper** to `gateway-bff` client scope:
   - Mapper type: `Group Membership`
   - Token Claim Name: `groups`
   - Full group path: `false`
   - Add to ID token: `true`
   - Add to access token: `true`
3. **Disable self-service org creation** — Remove the create-org flow from the frontend (or gate it behind platform-admin)
4. **Update realm-export.json** with the group and mapper configuration
5. **Update keycloak-seed.sh** to create the group and assign initial platform admin user

---

### 39.11 Impact Assessment

#### What Changes

| Component | Change | Effort |
|-----------|--------|--------|
| **Backend: new `accessrequest/` package** | Entity, repo, service, controller (public + admin endpoints) | Medium |
| **Backend: `EmailService`** | OTP email template (reuse existing email infra) | Small |
| **Backend: `ClerkJwtUtils`** | Extract `groups` claim from JWT | Small |
| **Backend: `RequestScopes`** | Add `GROUPS` ScopedValue | Small |
| **Backend: Security config** | Permit unauthenticated access to `/api/access-requests/**` | Small |
| **Backend: new `PlatformSecurityService`** | `isPlatformAdmin()` check against `RequestScopes.GROUPS` | Small |
| **Gateway: route config** | Add `/api/platform-admin/**` proxy route | Small |
| **Frontend: `/request-access` route** | Public multi-step form with OTP | Medium |
| **Frontend: `/platform-admin/` routes** | Admin panel with access request table, approve/reject | Medium |
| **Frontend: `AuthContext`** | Add `groups: string[]` field | Small |
| **Frontend: auth providers** | Extract `groups` from BFF `/bff/me` response | Small |
| **Frontend: navigation** | Platform admin sidebar link (conditional on group) | Small |
| **Keycloak: realm config** | `platform-admins` group + Group Membership Mapper | Small |
| **Keycloak: seed script** | Create group, assign initial admin | Small |
| **JIT provisioning** | Disable (`app.jit-provisioning.enabled=false`) | Small |
| **Self-service org creation** | Remove/gate the create-org frontend flow | Small |
| **Flyway: global migration** | `access_requests` table DDL | Small |

#### What Stays The Same

- `TenantProvisioningService` — called from approval handler instead of JIT filter
- `KeycloakAdminClient` — `createOrganization()`, `inviteUser()` already exist
- Gateway BFF session management — unchanged
- All tenant-scoped domain logic — unchanged
- Member sync / JIT member creation on first login — unchanged
- Seed packs — unchanged (all packs seeded uniformly)
- Auth abstraction layer (`NEXT_PUBLIC_AUTH_MODE`) — unchanged (extended, not replaced)

#### Risk Register

| Risk | Mitigation |
|------|------------|
| Approval provisioning fails mid-way (KC org created, schema fails) | Idempotent retry: admin can re-approve. `provisioningError` field captures failure details. |
| OTP email delivery delays | 10-minute expiry window. User can re-submit form to get new OTP. |
| Spam submissions before OTP verification | `PENDING_VERIFICATION` records auto-expire. Periodic cleanup job deletes unverified requests older than 24h. |
| Platform admin JWT missing `groups` claim | Startup check: verify Group Membership Mapper exists in Keycloak client scope. |
| Slug collision on org names | `slugify()` + uniqueness check against Keycloak. Append numeric suffix if collision. |
| Keycloak invitation email not themed | Reuse existing Keycloakify email theme from Phase 36. |

---

### 39.12 Deployment Topology (No Change)

The existing topology (Browser -> Gateway -> Backend -> Postgres + Keycloak) is unchanged. The access request form calls the backend directly via the gateway proxy (unauthenticated path). The platform admin panel uses the same BFF session as all other authenticated routes.

```
                    PUBLIC                        AUTHENTICATED
                    (no session)                  (BFF session)

  /request-access -----> Gateway -----> Backend   /platform-admin/* ---> Gateway ---> Backend
                         (proxy)     /api/access-  (BFF session)      (proxy)    /api/platform-
                                      requests                                    admin/*
                                         |                                           |
                                         v                                           v
                                    access_requests                         access_requests
                                    (public schema)                         (read + approve)
                                         |                                       |
                                         |                              on approve:
                                         |                              KeycloakAdminClient
                                         |                              TenantProvisioningService
                                         v                                       v
                                      EmailService                      Keycloak Org + Invite
                                      (send OTP)                        tenant_* schema
```
