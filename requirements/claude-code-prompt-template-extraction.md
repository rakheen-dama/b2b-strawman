# Requirements: java-keycloak-multitenant-saas Starter Template

## Context

This is **not** a new phase of DocTeams. It is a **separate, standalone project** — a production-grade
multitenant SaaS starter template extracted from the patterns and infrastructure built across 50+
phases of DocTeams development.

The template will live in its own repository (`java-keycloak-multitenant-saas`) with a clean git
history, independent documentation, and a companion blog series published on Medium.com.

**The DocTeams codebase is the reference implementation.** Relevant patterns should be adapted and
simplified — not copy-pasted wholesale. The template must stand alone: a developer cloning it should
understand everything without DocTeams context.

## Objective

Create a production-grade, opinionated multitenant SaaS starter template that demonstrates:

1. **Schema-per-tenant isolation** using Hibernate 7 + PostgreSQL 16
2. **Modern Java 25** patterns — ScopedValues (JEP 506), Virtual Threads, Records
3. **Spring Cloud Gateway BFF** — session management, CSRF, token relay (no secrets in frontend)
4. **Keycloak 26.5** — organization-scoped identity, invitations, RBAC
5. **Gated tenant registration** — access request → OTP → platform admin approval → provisioning
6. **Customer portal with magic links** — password-free external access
7. **Next.js 16 frontend** — App Router, Server Components, Server Actions, Tailwind + Shadcn

The template includes a simple but meaningful domain model (Projects, Customers, Comments) that
demonstrates tenant-scoped CRUD, entity relationships, and dual-auth writes (members + portal customers).

A 10-part blog series ("Zero to Prod") accompanies the template, published on Medium.com with
source drafts in the repository.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 25 (ScopedValues final, Virtual Threads) |
| Backend | Spring Boot | 4.x |
| Gateway | Spring Cloud Gateway (WebMVC) | Latest stable |
| Frontend | Next.js (App Router) | 16 |
| UI | React 19, Tailwind CSS v4, Shadcn UI | Latest |
| Auth | Keycloak | 26.5 |
| Database | PostgreSQL | 16 |
| Email (dev) | Mailpit | Latest |
| Build | Maven (backend/gateway), pnpm (frontend) | - |
| Containers | Docker Compose | v2 |

## Constraints & Assumptions

1. **New repository** — clean history, no DocTeams git ancestry
2. **No billing/subscriptions** — template focuses on tenancy and access, not monetization
3. **No file storage** — no S3/LocalStack; the domain doesn't require it
4. **No audit trail** — keep the template focused; auditing is a future addition
5. **No notifications beyond transactional email** — OTP, invite, magic link emails only
6. **No vertical-specific seeding** — empty tenant schemas with migrations only
7. **Two roles only** — owner and member (stored in product DB, not Keycloak org roles)
8. **Virtual threads enabled** from day one (`spring.threads.virtual.enabled=true`)
9. **Blog drafts in repo** — `docs/blog/` directory with Markdown source for all 10 posts
10. **Gateway is required** — all frontend API calls go through the gateway; backend is never exposed directly to the browser

---

## 1. Repository Structure

```
java-keycloak-multitenant-saas/
├── compose/
│   ├── docker-compose.yml          # Postgres, Keycloak, Mailpit
│   ├── docker-compose.dev.yml      # Backend + Gateway + Frontend (optional, for full-stack Docker)
│   ├── keycloak/
│   │   └── realm-export.json       # Pre-configured realm with orgs enabled
│   ├── data/
│   │   └── postgres/
│   │       └── init.sql            # Create app + keycloak databases
│   └── scripts/
│       ├── dev-up.sh               # Start infrastructure
│       ├── dev-down.sh             # Stop infrastructure (--clean to wipe volumes)
│       └── keycloak-bootstrap.sh   # Bootstrap platform-admins group + initial admin user
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/io/github/rakheendama/starter/
│       ├── StarterApplication.java
│       ├── multitenancy/           # Schema-per-tenant core
│       ├── config/                 # Hibernate MT, security, web config
│       ├── security/               # JWT validation, platform admin check
│       ├── provisioning/           # Schema creation + Flyway orchestration
│       ├── accessrequest/          # Registration + OTP + admin approval
│       ├── organization/           # Org entity + basic settings
│       ├── member/                 # Invite, accept, profile sync, roles
│       ├── customer/               # Customer CRUD (tenant-scoped)
│       ├── project/                # Project CRUD (linked to customer)
│       ├── comment/                # Comments (dual author: member + customer)
│       └── portal/                 # Magic link generation, token validation, portal API
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-local.yml
│       └── db/migration/
│           ├── public/             # Public schema migrations (access_requests, org_schema_mappings, magic_link_tokens)
│           └── tenant/             # Tenant schema migrations (organization, member, customer, project, comment)
├── gateway/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/io/github/rakheendama/starter/gateway/
│       ├── GatewayApplication.java
│       └── config/
│           └── GatewaySecurityConfig.java    # OAuth2, session, CSRF, routes
│   └── src/main/resources/
│       └── application.yml                   # Route definitions, token relay
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   ├── next.config.ts
│   └── app/
│       ├── (public)/                         # Landing page, request-access form
│       ├── (platform-admin)/                 # Access request queue (approve/reject)
│       ├── (app)/                            # Tenant dashboard, projects, customers, members, settings
│       ├── portal/                           # Magic link portal (read-only + comments)
│       └── accept/                           # Invitation acceptance redirect
├── docs/
│   └── blog/
│       ├── 01-architecture-and-why-schema-per-tenant.md
│       ├── 02-one-command-dev-environment.md
│       ├── 03-the-multitenancy-core.md
│       ├── 04-spring-cloud-gateway-as-bff.md
│       ├── 05-tenant-registration-pipeline.md
│       ├── 06-members-roles-and-profile-sync.md
│       ├── 07-your-first-domain-entity.md
│       ├── 08-security-hardening.md
│       ├── 09-the-magic-link-portal.md
│       └── 10-portal-comments-dual-auth-writes.md
├── README.md                                 # Quick start, architecture overview, blog links
├── CLAUDE.md                                 # AI-assisted development guide
└── LICENSE
```

---

## 2. Infrastructure (Docker Compose)

### 2.1 Services

**postgres (PostgreSQL 16 Alpine)**
- Port: 5432
- Databases: `app` (application data), `keycloak` (Keycloak internal)
- Init script creates both databases
- Healthcheck: `pg_isready`
- Volume: `postgres_data` (persistent)

**keycloak (Keycloak 26.5)**
- Port: 8180 (HTTP, dev mode)
- Command: `start-dev --import-realm`
- Realm: `starter` (imported from `realm-export.json`)
- Realm configuration:
  - Organizations enabled
  - Email configured to use Mailpit SMTP
  - Client: `starter-gateway` (confidential, authorization code flow)
  - Client: `starter-admin-cli` (service account for provisioning API calls)
- Admin credentials: `admin` / configurable via env var
- Database: same Postgres instance, `keycloak` database
- Healthcheck: HTTP `/health/ready`

**mailpit (Mailpit)**
- SMTP: 1025
- Web UI: 8025
- Used for: OTP emails, invitation emails, magic link emails
- No persistent storage needed

### 2.2 Scripts

**dev-up.sh**
- Starts all infrastructure services
- Waits for health checks
- Runs `keycloak-bootstrap.sh` (idempotent: creates `platform-admins` group + initial admin user)
- Prints connection info summary

**dev-down.sh**
- Stops all services
- `--clean` flag wipes volumes (full reset)

**keycloak-bootstrap.sh**
- Creates `platform-admins` group in `starter` realm (if not exists)
- Creates initial platform admin user (configurable email/password) and adds to group
- Uses Keycloak Admin CLI (`kcadm.sh`) via the `starter-admin-cli` service account
- Idempotent — safe to run multiple times

---

## 3. Backend — Multitenancy Core

### 3.1 RequestScopes (Java 25 ScopedValues)

Reference: DocTeams `multitenancy/RequestScopes.java`

```
RequestScopes
├── TENANT_ID : ScopedValue<String>     # Schema name (e.g., "tenant_abc123")
├── ORG_ID    : ScopedValue<String>     # Keycloak org ID (UUID)
├── MEMBER_ID : ScopedValue<UUID>       # Member UUID within tenant
├── ORG_ROLE  : ScopedValue<String>     # "owner" or "member"
```

- Replace all ThreadLocal usage with ScopedValues
- `ScopedFilterChain.runScoped()` bridges ScopedValue binding with servlet filter chain
- Virtual threads enabled — ScopedValues are the correct context propagation mechanism

### 3.2 Tenant Filter

Reference: DocTeams `multitenancy/TenantFilter.java`

- Servlet filter that intercepts all `/api/**` requests (except public endpoints)
- Extracts `org_id` claim from JWT (relayed by gateway)
- Resolves `org_id` → `schema_name` via `OrgSchemaMappingRepository` (with cache)
- Binds `TENANT_ID`, `ORG_ID`, `MEMBER_ID`, `ORG_ROLE` via `ScopedValue.where().run()`
- Public endpoints bypass: `/api/access-requests/**`, `/api/portal/**`

### 3.3 Schema Routing (Hibernate 7)

Reference: DocTeams `multitenancy/SchemaMultiTenantConnectionProvider.java`, `HibernateMultiTenancyConfig.java`

- `MultiTenantConnectionProvider` implementation that sets `SET search_path TO <schema>`
- `CurrentTenantIdentifierResolver` reads from `RequestScopes.TENANT_ID`
- Fallback to `public` schema when no tenant bound
- Hibernate property: `hibernate.multiTenancy=SCHEMA`

### 3.4 OrgSchemaMapping

Reference: DocTeams `multitenancy/OrgSchemaMapping.java`

- Entity in **public** schema: `org_schema_mappings(id, org_id, schema_name, created_at)`
- Maps Keycloak org ID → PostgreSQL schema name
- Lookup cached (Caffeine, 5-minute TTL)
- Created during tenant provisioning (the last step, making it a commit marker)

### 3.5 Tenant Logging Filter

- Adds MDC entries: `tenant_id`, `org_id`, `user_id`, `request_id`
- All log output automatically tagged with tenant context
- Request ID generated per request (UUID)

---

## 4. Backend — Tenant Provisioning

### 4.1 TenantProvisioningService

Reference: DocTeams `provisioning/TenantProvisioningService.java`

**Simplified for template** — no vertical seeding, no subscription creation.

Provisioning pipeline (idempotent, retriable):

1. **Validate** — Check `OrgSchemaMapping` doesn't already exist
2. **Create Organization record** — in public schema, mark `IN_PROGRESS`
3. **Generate schema name** — deterministic from org slug (e.g., `tenant_<hash>`)
4. **Create schema** — `CREATE SCHEMA IF NOT EXISTS <schema_name>`
5. **Run Flyway migrations** — tenant migration scripts against new schema
6. **Create OrgSchemaMapping** — this is the commit marker (last step)
7. **Mark Organization COMPLETED**

Error handling:
- Errors captured in `AccessRequest.provisioningError` field
- Each step is idempotent — safe to retry the entire pipeline
- Flyway migrations use `IF NOT EXISTS` guards

### 4.2 Flyway Configuration

Two migration locations:
- `db/migration/public/` — runs once on app startup against public schema
- `db/migration/tenant/` — runs per-tenant during provisioning and on app startup for existing tenants

Public schema migrations:
- `V1__create_access_requests.sql`
- `V2__create_org_schema_mappings.sql`
- `V3__create_magic_link_tokens.sql`

Tenant schema migrations:
- `V1__create_organizations.sql`
- `V2__create_members.sql`
- `V3__create_customers.sql`
- `V4__create_projects.sql`
- `V5__create_comments.sql`

---

## 5. Backend — Access Request Flow

### 5.1 AccessRequest Entity (Public Schema)

Reference: DocTeams `accessrequest/AccessRequest.java`

```
access_requests
├── id                  : UUID (PK)
├── email               : VARCHAR(255) NOT NULL
├── full_name           : VARCHAR(255) NOT NULL
├── organization_name   : VARCHAR(255) NOT NULL
├── country             : VARCHAR(100)
├── industry            : VARCHAR(100)
├── status              : VARCHAR(20) DEFAULT 'PENDING_VERIFICATION'
│   ── PENDING_VERIFICATION → PENDING → APPROVED | REJECTED
├── otp_hash            : VARCHAR(255)    # BCrypt hash, never store raw
├── otp_expires_at      : TIMESTAMP
├── otp_attempts         : INT DEFAULT 0
├── otp_verified_at     : TIMESTAMP
├── reviewed_by         : VARCHAR(255)    # Admin who approved/rejected
├── reviewed_at         : TIMESTAMP
├── keycloak_org_id     : VARCHAR(255)    # Set on approval
├── provisioning_error  : TEXT            # Captures failure details
├── created_at          : TIMESTAMP
└── updated_at          : TIMESTAMP
```

### 5.2 Public Endpoints (Unauthenticated)

**POST /api/access-requests** — Submit registration request
- Input: `{ email, fullName, organizationName, country, industry }`
- Generates 6-digit OTP, stores BCrypt hash
- Sends OTP email via Mailpit
- Returns generic `{ message, expiresInMinutes }` (prevents email enumeration)
- Blocked email domains list (configurable)

**POST /api/access-requests/verify** — Verify OTP
- Input: `{ email, otp }`
- Validates OTP against hash, checks expiry, increments attempts
- Max attempts configurable (default: 5)
- On success: status → PENDING
- Returns `{ message }`

### 5.3 Platform Admin Endpoints (Authenticated + Platform Admin Role)

**GET /api/platform-admin/access-requests?status={status}** — List requests
- Optional status filter
- Returns list of `AccessRequestResponse` DTOs
- Protected by `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`

**POST /api/platform-admin/access-requests/{id}/approve** — Approve request
- Triggers provisioning pipeline:
  1. Create Keycloak organization (using org name as display name, slug as ID)
  2. Provision tenant schema (idempotent)
  3. Invite requester to Keycloak org as creator/owner
  4. Mark request APPROVED
- Each step has its own short transaction (seeders need separate DB connections)
- Errors captured in `provisioning_error`, request stays in PENDING state

**POST /api/platform-admin/access-requests/{id}/reject** — Reject request
- Status → REJECTED, records reviewer + timestamp

### 5.4 Platform Admin Detection

- Keycloak `platform-admins` group membership → mapped to JWT `groups` claim
- `PlatformSecurityService.isPlatformAdmin()` checks JWT groups claim
- Platform admin is orthogonal to tenant roles — a user can be both

---

## 6. Backend — Member Management

### 6.1 Member Entity (Tenant Schema)

```
members
├── id              : UUID (PK)
├── keycloak_user_id : VARCHAR(255) UNIQUE NOT NULL
├── email           : VARCHAR(255) NOT NULL
├── display_name    : VARCHAR(255)
├── role            : VARCHAR(20) NOT NULL   # 'owner' or 'member'
├── status          : VARCHAR(20) DEFAULT 'ACTIVE'   # ACTIVE, SUSPENDED
├── first_login_at  : TIMESTAMP
├── last_login_at   : TIMESTAMP
├── created_at      : TIMESTAMP
└── updated_at      : TIMESTAMP
```

### 6.2 Member Sync (First Login)

Reference: DocTeams `member/MemberSyncService.java`

- On first authenticated request, if member not found in tenant schema:
  - Create member record from JWT claims (sub, email, name)
  - Set role from Keycloak org membership (org creator = owner, invited = member)
  - Record `first_login_at`
- On subsequent logins: update `last_login_at`, sync email/name if changed

### 6.3 Member Invitation

- Owner invites member by email via Keycloak Organizations API
- Keycloak sends invitation email (using Mailpit SMTP in dev)
- Invitee clicks link → Keycloak registration → redirect to app → member sync creates record
- Owner can also remove members (removes from Keycloak org)

### 6.4 Endpoints

**GET /api/members** — List members in current tenant
**GET /api/members/me** — Current member profile
**POST /api/members/invite** — Invite by email (owner only)
**DELETE /api/members/{id}** — Remove member (owner only, cannot remove self)
**PATCH /api/members/{id}/role** — Change role (owner only)

---

## 7. Backend — Domain Model (Project, Customer, Comment)

### 7.1 Customer Entity (Tenant Schema)

```
customers
├── id              : UUID (PK)
├── name            : VARCHAR(255) NOT NULL
├── email           : VARCHAR(255) NOT NULL
├── company         : VARCHAR(255)
├── status          : VARCHAR(20) DEFAULT 'ACTIVE'   # ACTIVE, ARCHIVED
├── created_by      : UUID (FK → members.id)
├── created_at      : TIMESTAMP
└── updated_at      : TIMESTAMP
```

**Endpoints:**
- `GET /api/customers` — List customers (with search/filter)
- `GET /api/customers/{id}` — Get customer detail
- `POST /api/customers` — Create customer
- `PUT /api/customers/{id}` — Update customer
- `DELETE /api/customers/{id}` — Archive customer (soft delete)

### 7.2 Project Entity (Tenant Schema)

```
projects
├── id              : UUID (PK)
├── title           : VARCHAR(255) NOT NULL
├── description     : TEXT
├── status          : VARCHAR(20) DEFAULT 'ACTIVE'   # ACTIVE, COMPLETED, ARCHIVED
├── customer_id     : UUID (FK → customers.id) NOT NULL
├── created_by      : UUID (FK → members.id)
├── created_at      : TIMESTAMP
└── updated_at      : TIMESTAMP
```

**Endpoints:**
- `GET /api/projects` — List projects (filter by customer, status)
- `GET /api/projects/{id}` — Get project detail (includes customer info)
- `POST /api/projects` — Create project (linked to customer)
- `PUT /api/projects/{id}` — Update project
- `PATCH /api/projects/{id}/status` — Change status
- `DELETE /api/projects/{id}` — Archive project

### 7.3 Comment Entity (Tenant Schema)

```
comments
├── id              : UUID (PK)
├── project_id      : UUID (FK → projects.id) NOT NULL
├── content         : TEXT NOT NULL
├── author_type     : VARCHAR(20) NOT NULL   # 'MEMBER' or 'CUSTOMER'
├── author_id       : UUID NOT NULL          # FK → members.id OR customers.id
├── author_name     : VARCHAR(255)           # Denormalized for display
├── created_at      : TIMESTAMP
└── updated_at      : TIMESTAMP
```

Key design: `author_type` discriminator enables dual-auth writes. Members comment from the
tenant app, customers comment from the magic link portal. Both land in the same table.

**Tenant App Endpoints:**
- `GET /api/projects/{projectId}/comments` — List comments on project
- `POST /api/projects/{projectId}/comments` — Add comment (author_type=MEMBER)
- `DELETE /api/comments/{id}` — Delete own comment

**Portal Endpoints:**
- `GET /api/portal/projects/{projectId}/comments` — List comments (portal auth)
- `POST /api/portal/projects/{projectId}/comments` — Add comment (author_type=CUSTOMER)

---

## 8. Backend — Magic Link Portal

### 8.1 MagicLinkToken Entity (Public Schema)

Reference: DocTeams `portal/MagicLinkToken.java`

```
magic_link_tokens
├── id              : UUID (PK)
├── token_hash      : VARCHAR(255) UNIQUE NOT NULL   # SHA-256 of raw token
├── customer_id     : UUID NOT NULL
├── org_id          : VARCHAR(255) NOT NULL           # Keycloak org ID
├── expires_at      : TIMESTAMP NOT NULL              # 15-minute TTL
├── used_at         : TIMESTAMP                       # Single-use enforcement
├── created_at      : TIMESTAMP
```

- Raw token: 32 bytes crypto random → Base64 URL-safe encoding
- Only the SHA-256 hash is stored (raw token never persisted)
- Rate limit: 3 tokens per customer per 5 minutes
- Single-use: once exchanged, `used_at` is set

### 8.2 Portal JWT

- Separate from Keycloak JWTs — issued by the backend
- Algorithm: HS256 with configurable secret (`PORTAL_JWT_SECRET`)
- TTL: 1 hour
- Claims: `sub` (customer ID), `org_id` (Keycloak org ID), `type: "customer"`
- Backend validates portal JWTs on `/api/portal/**` endpoints

### 8.3 Portal Endpoints (Unauthenticated / Portal JWT)

**POST /api/portal/auth/request-link** — Request magic link
- Input: `{ email, orgId }`
- Resolves customer by email + org → tenant schema
- Sends magic link email (Mailpit in dev)
- Generic response (prevents email enumeration)
- Dev/test profiles: also returns magic link URL in response

**POST /api/portal/auth/exchange** — Exchange token for portal JWT
- Input: `{ token }`
- Verifies: hash lookup, expiry, single-use, customer status (ACTIVE only)
- Returns: `{ token (portal JWT), customerName, expiresAt }`

**GET /api/portal/projects** — List customer's projects (portal JWT required)
- Scoped to the customer ID in the portal JWT
- Returns projects with status and comment count

**GET /api/portal/projects/{id}** — Project detail (portal JWT required)
- Validates project belongs to customer
- Returns project with full comment list

**POST /api/portal/projects/{projectId}/comments** — Add comment (portal JWT required)
- `author_type=CUSTOMER`, `author_id` from portal JWT

---

## 9. Gateway — Spring Cloud Gateway BFF

### 9.1 Security Configuration

Reference: DocTeams `gateway/config/GatewaySecurityConfig.java`

**OAuth2 Login:**
- Provider: Keycloak OIDC (`starter` realm)
- Client: `starter-gateway` (confidential, authorization code flow)
- Post-login redirect: `{frontendUrl}/dashboard`
- Logout: OIDC-initiated, invalidates session, deletes SESSION cookie

**Session Management:**
- Spring Session JDBC (Postgres-backed)
- Cookie: `SESSION` (HttpOnly, SameSite=Lax, Secure in production)
- Timeout: 8 hours

**CSRF:**
- CookieCsrfTokenRepository (HttpOnly=false — frontend reads the cookie)
- Ignored for BFF endpoints

**Authorization Rules:**
- Public: `/`, `/error`, `/actuator/health`, `/bff/me`, `/bff/csrf`
- Public forms: `/api/access-requests/**`, `/api/access-requests/verify`
- Public portal: `/api/portal/**`
- Denied: `/internal/**`
- All other `/api/**`: authenticated

### 9.2 Routes

```yaml
routes:
  - id: backend-api
    uri: ${BACKEND_URL:http://localhost:8080}
    predicates:
      - Path=/api/**
    filters:
      - TokenRelay=
```

- `TokenRelay` filter adds the OAuth2 access token as `Authorization: Bearer` header
- Portal routes (`/api/portal/**`) pass through unauthenticated (portal JWT handled by backend)

### 9.3 BFF Endpoints

**GET /bff/me** — Current user session info
- Returns: `{ authenticated, email, name, orgId, roles }` or `{ authenticated: false }`
- Frontend polls this to determine auth state

**GET /bff/csrf** — CSRF token
- Returns CSRF token for frontend to include in state-changing requests

**POST /bff/logout** — Logout
- Invalidates session, triggers OIDC logout, clears cookies

---

## 10. Frontend — Next.js 16

### 10.1 Auth Integration

- No Clerk, no NextAuth — auth is entirely Gateway-managed
- Frontend checks `/bff/me` on load to determine auth state
- Login: redirect to Gateway OAuth2 endpoint → Keycloak → callback → session
- Logout: POST to `/bff/logout`
- CSRF: read `XSRF-TOKEN` cookie, send as `X-XSRF-TOKEN` header on mutations
- All API calls: relative URLs (e.g., `/api/projects`) — Gateway proxies to backend

### 10.2 Public Pages — `(public)/`

**Landing Page** (`/`)
- Clean marketing-style page explaining the platform
- "Request Access" CTA button

**Request Access Form** (`/request-access`)
- Multi-step wizard (3 steps):
  1. **Form**: email, full name, organization name, country (select), industry (select)
     - Real-time blocked domain validation
  2. **OTP Verification**: 6-digit code input, countdown timer, resend button
  3. **Success**: confirmation message

### 10.3 Platform Admin Pages — `(platform-admin)/`

**Access Request Queue** (`/platform-admin/access-requests`)
- Tabbed interface: ALL | PENDING | APPROVED | REJECTED
- Table: org name, email, name, country, industry, submitted date, status, actions
- PENDING rows show Approve / Reject buttons
- Approve dialog: confirms side effects (creates org, provisions schema, sends invite)
- Reject dialog: simple confirmation
- Protected: redirect to `/` if current user is not platform admin (check `/bff/me`)

### 10.4 Tenant App Pages — `(app)/`

**Layout:**
- Sidebar navigation: Dashboard, Projects, Customers, Members, Settings
- Header: org name, user avatar/name, logout button
- Breadcrumbs

**Dashboard** (`/dashboard`)
- Summary cards: total projects (by status), total customers, total members
- Recent projects list (last 5)

**Projects** (`/projects`)
- Table: title, customer, status, created date, comment count
- Filter by status, search by title
- Create project dialog (title, description, select customer)
- Project detail page (`/projects/[id]`):
  - Project info (title, description, status, linked customer)
  - Status change buttons (Active → Completed, Archive)
  - Comments section (threaded list, add comment form)
  - "Share Portal Link" button (sends magic link to customer email)

**Customers** (`/customers`)
- Table: name, email, company, status, project count
- Create / edit customer dialog
- Customer detail page (`/customers/[id]`):
  - Customer info
  - List of linked projects
  - "Send Portal Link" button

**Members** (`/members`)
- Table: name, email, role, status, last login
- Invite member dialog (email input) — owner only
- Change role / remove member — owner only

**Settings** (`/settings`)
- Organization name display
- Basic org settings (future extensibility placeholder)

### 10.5 Portal Pages — `portal/`

**Portal Login** (`/portal`)
- Email + org identifier input
- "Send me a link" button → magic link email
- Dev mode: displays magic link directly
- Token exchange: paste link or token → exchange for portal JWT → redirect to portal dashboard

**Portal Dashboard** (`/portal/projects`)
- List of customer's projects (title, status, comment count)
- Clean, read-focused design — distinct from tenant app

**Portal Project Detail** (`/portal/projects/[id]`)
- Project info (read-only)
- Comment list (all comments: member + customer, distinguished by author_type badge)
- Add comment form (customer can comment)

**Portal Layout:**
- Minimal: org branding (name), customer name, logout
- No sidebar — simple top nav
- Token stored in localStorage (cleared on "logout")

### 10.6 Accept Invite Page — `accept/`

**Invitation Landing** (`/accept`)
- Redirect target after Keycloak invitation acceptance
- If user already has session: redirect to `/dashboard`
- If not: redirect to Gateway login (which goes to Keycloak)

---

## 11. Blog Series — "Zero to Prod"

All drafts live in `docs/blog/`. Each post should be 1500-2500 words, include relevant code
snippets from the template, and link to the specific files in the GitHub repository.

### Post 1: Architecture & Why Schema-Per-Tenant
- The three isolation models: row-level, schema-per-tenant, database-per-tenant
- Trade-offs of each (complexity, isolation, cost, operations)
- Why schema-per-tenant hits the sweet spot for most B2B SaaS
- Architecture diagram: Frontend → Gateway → Backend → Postgres (with schema routing)
- Preview of what the full template delivers

### Post 2: One-Command Dev Environment
- Docker Compose walkthrough: Postgres, Keycloak, Mailpit
- The `dev-up.sh` script and why idempotent bootstrapping matters
- Keycloak realm configuration: orgs enabled, clients configured
- "Run one command, get a production-like environment"

### Post 3: The Multitenancy Core
- Java 25 ScopedValues — what they are, why they replace ThreadLocal
- The TenantFilter: JWT → org_id → schema_name → ScopedValue binding
- Hibernate 7 schema routing: MultiTenantConnectionProvider + TenantIdentifierResolver
- OrgSchemaMapping: the lookup table that ties it all together
- Code walkthrough with the actual template files

### Post 4: Spring Cloud Gateway as BFF
- Why the frontend should never see a JWT
- Session management: Spring Session JDBC, HttpOnly cookies
- Token relay: how the gateway adds the Bearer token for the backend
- CSRF protection: the cookie → header dance
- Route configuration walkthrough
- Security comparison: SPA with tokens vs. BFF pattern

### Post 5: Tenant Registration Pipeline
- The full flow: visitor → form → OTP → admin panel → approve → provision
- AccessRequest entity and state machine
- OTP security: BCrypt hashing, attempt limiting, expiry
- Provisioning orchestration: Keycloak org + schema + Flyway + invitation
- Why each step is idempotent (and why that matters)
- Email enumeration prevention

### Post 6: Members, Roles & Profile Sync
- Keycloak org invitations
- First-login member sync: JWT claims → member record
- Product-layer roles (owner/member) vs. Keycloak org roles
- Why we chose product-layer roles (the Keycloak org roles problem)
- The accept-invite flow end-to-end

### Post 7: Your First Domain Entity
- Project + Customer as the worked example
- Entity, migration, repository, service, controller pattern
- Tenant-scoped CRUD: how every query is automatically scoped
- Linking entities: Project → Customer foreign key
- "This is the pattern every future entity will follow"

### Post 8: Security Hardening
- Tenant isolation proof: what happens if you tamper with org_id
- Schema boundary enforcement: no cross-tenant data access possible
- JWT validation layers (gateway session + backend JWT verification)
- Platform admin as a separate concern
- OTP brute-force protection
- Magic link security: hashing, single-use, expiry, rate limiting
- OWASP considerations addressed

### Post 9: The Magic Link Portal
- Why magic links > password-based customer portals
- MagicLinkToken: crypto random + SHA-256 hash + single-use
- Portal JWT: separate auth mechanism coexisting with Keycloak
- The exchange flow: token → verify → issue portal JWT
- Frontend: clean read-only portal UI
- Cross-boundary security: portal JWT scoped to customer within tenant

### Post 10: Portal Comments — Dual-Auth Writes
- The `author_type` discriminator pattern
- Members write from the tenant app, customers write from the portal
- Same entity, different auth mechanisms, same database table
- How the backend resolves the author based on auth context
- UI: distinguishing member vs. customer comments visually
- "This pattern extends to any entity that needs external input"

---

## 12. Security Considerations

### Tenant Isolation
- Schema boundary: each tenant's data lives in a separate PostgreSQL schema
- No `WHERE tenant_id = ?` clauses needed — Hibernate routes to the correct schema
- Cross-tenant access impossible at the database level (schema search_path)
- OrgSchemaMapping is the single lookup point — cached but validated on every request

### Authentication Layers
- **Gateway session**: HttpOnly cookie, CSRF protected, Keycloak-backed
- **Backend JWT**: validated on every request (signature, expiry, issuer, audience)
- **Portal JWT**: separate HS256 tokens, customer-scoped, 1-hour TTL
- **Platform admin**: Keycloak group membership, checked via JWT groups claim

### Data Protection
- OTP: BCrypt hashed, never stored raw, attempt-limited, time-limited
- Magic link tokens: SHA-256 hashed, single-use, 15-minute TTL, rate-limited
- No raw tokens in database, no tokens in logs
- Email enumeration prevention: generic responses on all public endpoints

### Gateway as Security Boundary
- Frontend never sees JWT tokens (session cookie only)
- Backend never exposed directly to browser
- CSRF on all state-changing requests
- Token relay only for authenticated sessions

---

## 13. Out of Scope

- Billing / subscriptions / plan enforcement / payment integration
- File storage / S3 / uploads
- Audit trail / event logging
- Notification system (beyond transactional emails)
- Vertical-specific seeding or field customization
- Advanced RBAC (beyond owner/member)
- Rate cards, budgets, profitability
- Document templates / PDF generation
- Workflow automation
- Custom fields / tags / views
- Recurring work / retainers
- Reporting / analytics dashboards
- CI/CD pipeline configuration
- Production deployment guides (blog post 10 touches on this conceptually)
- SSL/TLS configuration for local dev (Keycloak runs HTTP in dev mode)
- Monitoring / observability (mentioned in blog post 10 as a future topic)

---

## 14. ADR Topics to Address

The template should include a lightweight `docs/decisions/` directory with short ADRs:

1. **Schema-per-tenant over row-level isolation** — trade-offs, operational implications
2. **ScopedValues over ThreadLocal** — Java 25 decision, virtual thread compatibility
3. **Product-layer roles over Keycloak org roles** — why Keycloak org roles were problematic
4. **Gateway BFF over direct API access** — security boundary, session management
5. **Magic links over customer accounts** — portal auth strategy, no-password UX
6. **Dual-author comments** — discriminator pattern for multi-auth writes
7. **Idempotent provisioning** — why every step must be retriable

---

## 15. Testing Strategy

### Backend
- **Unit tests**: Service layer with mocked repositories
- **Integration tests**: `@SpringBootTest` with Testcontainers (Postgres + Keycloak)
- **Multitenancy tests**: Verify schema isolation — create entities in tenant A, confirm invisible from tenant B
- **Provisioning tests**: Full pipeline test — create access request, approve, verify schema + migrations
- **Portal tests**: Magic link generation, exchange, JWT validation, cross-tenant isolation

### Frontend
- **Component tests**: Vitest + Testing Library for form components
- **E2E tests**: Playwright against the full Docker stack
  - Happy path: request access → verify OTP → admin approve → owner login → create customer → create project → comment → share portal link → customer views portal → customer comments

### Key Test Scenarios
1. Tenant A cannot see Tenant B's data (schema isolation proof)
2. Expired OTP is rejected
3. Used magic link cannot be reused
4. Portal JWT scoped to correct customer — cannot access other customer's projects
5. Member role cannot invite/remove members (owner only)
6. Platform admin endpoints reject non-admin users

---

## 16. Developer Experience

### Quick Start (README)

```bash
# 1. Start infrastructure
cd compose && ./scripts/dev-up.sh

# 2. Start backend (terminal 1)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Start gateway (terminal 2)
cd gateway && ./mvnw spring-boot:run

# 4. Start frontend (terminal 3)
cd frontend && pnpm install && pnpm dev

# Open http://localhost:3000
# Mailpit UI: http://localhost:8025
# Keycloak Admin: http://localhost:8180 (admin/admin)
```

### First-Run Walkthrough

1. Open http://localhost:3000 → click "Request Access"
2. Fill form, submit → check Mailpit for OTP email
3. Enter OTP → request goes to PENDING
4. Log in as platform admin → approve the request
5. Check Mailpit for invitation email → click link → register in Keycloak
6. Redirected to dashboard → you're the owner of a new tenant
7. Create a customer, create a project, add a comment
8. Share portal link → check Mailpit → click magic link → see the portal

---

## Style & Boundaries

- **Clean, modern code** — Java 25 features (records, sealed types, pattern matching where appropriate), but don't force patterns where simple code works
- **Minimal dependencies** — no Lombok, no MapStruct; use records for DTOs, manual mapping
- **Convention over configuration** — sensible Spring Boot defaults, override only when needed
- **Self-documenting** — clear naming, package-per-feature, short methods
- **Blog-friendly** — code should be readable in a blog post context; avoid dense abstractions
- **No over-engineering** — this is a starter template, not a framework. Simple > clever.
