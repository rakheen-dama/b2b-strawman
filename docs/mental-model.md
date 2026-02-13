# DocTeams — System Mental Model

> A visual guide to the architecture, actors, domain model, and key workflows of the DocTeams multi-tenant B2B SaaS platform.

---

## 1. System Context — Who Talks to What

```mermaid
flowchart TB
    subgraph Actors
        Owner["🔑 Org Owner"]
        Admin["⚙️ Org Admin"]
        Member["👤 Org Member"]
        Portal["🔗 Portal Contact<br/>(external client)"]
    end

    subgraph "DocTeams Platform"
        FE["Next.js 16 Frontend<br/>(App Router, React 19)"]
        BE["Spring Boot 4 Backend<br/>(Java 25, Hibernate 7)"]
    end

    subgraph "External Services"
        Clerk["Clerk<br/>(Auth, Orgs, RBAC, Webhooks)"]
        S3["AWS S3<br/>(File Storage)"]
        PG[("PostgreSQL 16<br/>(Neon — schema-per-tenant)")]
    end

    Owner --> FE
    Admin --> FE
    Member --> FE
    Portal -->|"magic link"| FE

    FE -->|"Bearer JWT"| BE
    FE -->|"Svix webhooks"| BE
    Clerk -->|"webhooks"| FE
    FE --> Clerk
    BE --> PG
    BE -->|"presigned URLs"| S3
    FE -->|"direct upload"| S3
```

---

## 2. High-Level Architecture

```mermaid
flowchart TB
    subgraph Internet
        Browser["Browser<br/>(React 19)"]
        ClerkCloud["Clerk Cloud"]
    end

    subgraph "Next.js 16 Frontend"
        Middleware["Clerk Middleware<br/>(auth + org sync)"]
        ServerComp["Server Components<br/>(data fetching via api.ts)"]
        ClientComp["Client Components<br/>(dialogs, forms, tabs)"]
        WebhookRoute["POST /api/webhooks/clerk<br/>(Svix verify + forward)"]
        ServerActions["Server Actions<br/>(mutations + revalidatePath)"]
    end

    subgraph "Spring Boot 4 Backend"
        SecurityChain["Security Filter Chain<br/>JWT → Member → Tenant"]
        StaffAPI["/api/** — 24 Controllers<br/>(projects, tasks, time, docs, ...)"]
        InternalAPI["/internal/** — Provisioning<br/>(member sync, plan sync)"]
        PortalAPI["/portal/** — Customer Portal<br/>(auth, projects, docs)"]
        Services["Domain Services<br/>(22 packages)"]
        EventPublisher["Domain Event Publisher<br/>(Spring ApplicationEvents)"]
        Repositories["JPA Repositories<br/>(Hibernate 7 + @Filter)"]
    end

    subgraph "Data Layer"
        PG[("PostgreSQL 16<br/>public + tenant_shared + tenant_&lt;hash&gt; + portal")]
        S3[("AWS S3<br/>org/{orgId}/project/{projId}/{docId}")]
    end

    Browser --> Middleware --> ServerComp
    Browser --> ClientComp
    ClerkCloud -->|"org/member webhooks"| WebhookRoute

    ServerComp -->|"Bearer JWT"| SecurityChain
    ServerActions -->|"Bearer JWT"| SecurityChain
    WebhookRoute -->|"X-API-KEY"| InternalAPI

    SecurityChain --> StaffAPI
    SecurityChain --> PortalAPI
    StaffAPI --> Services
    InternalAPI --> Services
    PortalAPI --> Services
    Services --> Repositories
    Services --> EventPublisher
    Repositories --> PG
    Services -->|"presigned URLs"| S3
    EventPublisher -->|"audit, notifications,<br/>portal read-model sync"| Services
```

---

## 3. Actor Model & Roles

```mermaid
flowchart LR
    subgraph "Org Roles (from Clerk JWT)"
        Owner["🔑 org:owner<br/>→ ROLE_ORG_OWNER"]
        Admin["⚙️ org:admin<br/>→ ROLE_ORG_ADMIN"]
        Member["👤 org:member<br/>→ ROLE_ORG_MEMBER"]
    end

    subgraph "Project Roles (app-managed)"
        Lead["📋 project_lead<br/>(creator or assigned)"]
        Contrib["🔧 contributor<br/>(added to project)"]
    end

    subgraph "External"
        PortalContact["🔗 Portal Contact<br/>(magic link, no Clerk)"]
        System["🤖 System<br/>(webhooks, cron)"]
    end

    Owner -->|"full access<br/>+ billing + delete + team"| Lead
    Admin -->|"project CRUD<br/>+ customer mgmt"| Lead
    Member -->|"access depends<br/>on project membership"| Contrib
    PortalContact -->|"read-only<br/>+ comment"| Contrib
```

### Permission Matrix

| Action | Owner | Admin | Member (no project role) | Project Lead | Contributor | Portal Contact |
|--------|:-----:|:-----:|:------------------------:|:------------:|:-----------:|:--------------:|
| Create Project | ✅ | ✅ | ✅ | — | — | — |
| Edit Project | ✅ | ✅ | — | ✅ | — | — |
| Delete Project | ✅ | — | — | — | — | — |
| Upload Document | ✅ | ✅ | — | ✅ | ✅ | — |
| Manage Tasks | ✅ | ✅ | — | ✅ | — | — |
| Claim Task | ✅ | ✅ | — | ✅ | ✅ | — |
| Log Time | ✅ | ✅ | — | ✅ | ✅ | — |
| Add Comment | ✅ | ✅ | — | ✅ | ✅ | ✅ (visible only) |
| Manage Team | ✅ | — | — | — | — | — |
| Manage Billing | ✅ | — | — | — | — | — |
| View Portal | — | — | — | — | — | ✅ |

> **Security-by-obscurity**: Unauthorized users receive `404 Not Found` instead of `403 Forbidden` — `ProjectAccessService` returns "not found" for projects the caller can't see.

---

## 4. Domain Model

### 4.1 Global Schema (`public`)

```mermaid
erDiagram
    organizations {
        uuid id PK
        varchar clerk_org_id UK
        varchar name
        varchar slug
        enum tier "STARTER | PRO"
        varchar plan_slug
        enum provisioning_status "PENDING | IN_PROGRESS | COMPLETED | FAILED"
        timestamp created_at
    }

    org_schema_mapping {
        uuid id PK
        varchar clerk_org_id FK
        varchar schema_name "tenant_shared or tenant_hash"
        enum tier "STARTER | PRO"
    }

    subscriptions {
        uuid id PK
        uuid organization_id FK
        varchar plan_slug
        enum status "ACTIVE | CANCELLED"
        timestamp current_period_start
        timestamp current_period_end
    }

    processed_webhooks {
        varchar svix_id PK
        varchar event_type
        timestamp processed_at
    }

    organizations ||--|| org_schema_mapping : "maps to"
    organizations ||--o| subscriptions : "has"
```

### 4.2 Tenant Schema (per-org)

```mermaid
erDiagram
    projects ||--o{ documents : "contains"
    projects ||--o{ tasks : "has"
    projects ||--o{ project_members : "has members"
    projects }o--o{ customers : "linked via customer_projects"
    members ||--o{ project_members : "assigned to"
    members ||--o{ time_entries : "logs"
    members ||--o{ notifications : "receives"
    tasks ||--o{ time_entries : "tracked by"
    tasks ||--o{ comments : "discussed in"
    documents ||--o{ comments : "discussed in"
    customers ||--o{ portal_contacts : "has contacts"

    projects {
        uuid id PK
        varchar name
        text description
        uuid created_by FK
        varchar tenant_id "row isolation (shared)"
    }

    members {
        uuid id PK
        varchar clerk_user_id
        varchar email
        varchar name
        varchar org_role "owner | admin | member"
        varchar tenant_id
    }

    project_members {
        uuid id PK
        uuid project_id FK
        uuid member_id FK
        varchar project_role "lead | contributor"
        varchar tenant_id
    }

    documents {
        uuid id PK
        uuid project_id FK
        varchar file_name
        varchar s3_key
        enum status "PENDING | UPLOADED | FAILED"
        enum scope "PROJECT | ORG | CUSTOMER"
        enum visibility "INTERNAL | SHARED"
        varchar tenant_id
    }

    customers {
        uuid id PK
        varchar name
        varchar email
        enum status "ACTIVE | ARCHIVED"
        varchar tenant_id
    }

    tasks {
        uuid id PK
        uuid project_id FK
        varchar title
        enum status "OPEN | IN_PROGRESS | COMPLETED | CANCELLED"
        enum priority "LOW | MEDIUM | HIGH | URGENT"
        uuid assignee_id FK
        date due_date
        int version "optimistic lock"
        varchar tenant_id
    }

    time_entries {
        uuid id PK
        uuid task_id FK
        uuid member_id FK
        date entry_date
        int duration_minutes
        boolean billable
        varchar tenant_id
    }

    comments {
        uuid id PK
        varchar entity_type "TASK | DOCUMENT"
        uuid entity_id
        uuid author_member_id FK
        text body
        enum visibility "INTERNAL | CUSTOMER_VISIBLE"
        uuid parent_id "threaded replies"
        varchar tenant_id
    }

    notifications {
        uuid id PK
        uuid recipient_member_id FK
        varchar type
        varchar title
        text body
        boolean is_read
        varchar tenant_id
    }

    audit_events {
        uuid id PK
        varchar event_type
        varchar entity_type
        uuid entity_id
        uuid actor_id
        jsonb details
        timestamp occurred_at
        varchar tenant_id
    }

    portal_contacts {
        uuid id PK
        uuid customer_id FK
        varchar email
        varchar display_name
        enum role "PRIMARY | BILLING | GENERAL"
        enum status "ACTIVE | SUSPENDED | ARCHIVED"
        varchar tenant_id
    }
```

### 4.3 Portal Read-Model Schema (`portal`)

```mermaid
erDiagram
    portal_projects {
        uuid id PK
        varchar org_id
        uuid customer_id
        varchar name
        int document_count
        int comment_count
        timestamp updated_at
    }

    portal_documents {
        uuid id PK
        varchar org_id
        uuid customer_id
        uuid portal_project_id FK
        varchar title
        varchar s3_key
        bigint size
    }

    portal_comments {
        uuid id PK
        varchar org_id
        uuid portal_project_id FK
        varchar author_name
        text content
        timestamp created_at
    }

    portal_project_summaries {
        uuid id PK
        varchar org_id
        uuid customer_id
        decimal total_hours
        decimal billable_hours
        timestamp last_activity_at
    }

    portal_projects ||--o{ portal_documents : "has"
    portal_projects ||--o{ portal_comments : "has"
    portal_projects ||--|| portal_project_summaries : "summarized by"
```

---

## 5. Multitenancy — How Isolation Works

```mermaid
flowchart TB
    Request["Incoming Request<br/>+ Bearer JWT with org_id"] --> JWTFilter["JWT Auth Filter<br/>(validate via Clerk JWKS)"]

    JWTFilter --> TenantFilter["TenantFilter"]

    TenantFilter --> Cache{"Caffeine Cache<br/>lookup org_id"}
    Cache -->|"hit"| TenantInfo
    Cache -->|"miss"| DB["Query<br/>public.org_schema_mapping"]
    DB --> TenantInfo["TenantInfo<br/>(schema, tier)"]

    TenantInfo --> Bind["ScopedValue.where()<br/>TENANT_ID = schema<br/>ORG_ID = clerkOrgId"]

    Bind --> TierBranch{"tier?"}

    TierBranch -->|"PRO<br/>(dedicated schema)"| ProPath
    TierBranch -->|"STARTER<br/>(shared schema)"| StarterPath

    subgraph ProPath["Pro: Schema Isolation"]
        SetSearch["SET search_path TO tenant_abc123"]
        ProQuery["SELECT * FROM projects<br/>(schema provides isolation)"]
        SetSearch --> ProQuery
    end

    subgraph StarterPath["Starter: Row-Level Isolation"]
        SetSearchShared["SET search_path TO tenant_shared"]
        SetConfig["SET app.current_tenant = org_xyz<br/>(for RLS)"]
        EnableFilter["Enable Hibernate @Filter<br/>(WHERE tenant_id = org_xyz)"]
        StarterQuery["SELECT * FROM projects<br/>WHERE tenant_id = 'org_xyz'"]
        SetSearchShared --> SetConfig --> EnableFilter --> StarterQuery
    end

    ProQuery --> Response["JSON Response"]
    StarterQuery --> Response
```

### Why Both Hibernate @Filter AND Postgres RLS?

| Layer | Protects Against | Mechanism |
|-------|-----------------|-----------|
| **Hibernate @Filter** | JPQL/HQL queries returning wrong tenant's data | `WHERE tenant_id = :tenantId` appended to all queries |
| **Postgres RLS** | Native SQL / raw JDBC bypassing Hibernate | `USING (tenant_id = current_setting('app.current_tenant'))` |
| **Schema isolation (Pro)** | Everything — data physically separated | `SET search_path TO tenant_<hash>` |

---

## 6. Request Lifecycle — Security Filter Chain

```mermaid
sequenceDiagram
    participant Browser
    participant NextJS as Next.js<br/>(Server Component)
    participant Clerk as Clerk Cloud
    participant JWTFilter as JWT Auth Filter
    participant MemberFilter as Member Filter
    participant TenantFilter as Tenant Filter
    participant Controller as Controller<br/>(@PreAuthorize)
    participant Service as Domain Service
    participant Hibernate as Hibernate 7<br/>(TenantIdentifierResolver)
    participant DB as PostgreSQL

    Browser->>NextJS: Request + session cookie
    NextJS->>Clerk: getToken() with org claims
    Clerk-->>NextJS: JWT {sub, o.id, o.rol}
    NextJS->>JWTFilter: GET /api/... (Bearer JWT)

    Note over JWTFilter: Validate signature via JWKS<br/>Extract sub, org_id, org_role<br/>Map org:role → ROLE_ORG_*

    JWTFilter->>MemberFilter: Authenticated request

    Note over MemberFilter: Lookup member by clerk_user_id<br/>Bind: MEMBER_ID, ORG_ROLE<br/>(via ScopedValue.where().run())

    MemberFilter->>TenantFilter: + member context

    Note over TenantFilter: Resolve org_id → schema name<br/>(Caffeine cache, 1h TTL)<br/>Bind: TENANT_ID, ORG_ID<br/>(via ScopedValue.where().run())

    TenantFilter->>Controller: Fully contextualized request
    Controller->>Service: Business logic call

    Service->>Hibernate: Repository method

    Note over Hibernate: TenantIdentifierResolver<br/>reads RequestScopes.TENANT_ID<br/>→ SET search_path TO tenant_xxx

    Hibernate->>DB: SQL query (tenant-scoped)
    DB-->>Hibernate: Results
    Hibernate-->>Service: Entities
    Service-->>Controller: Response DTO
    Controller-->>Browser: JSON
```

### ScopedValue Bindings (Java 25 — JEP 506)

```mermaid
flowchart LR
    subgraph "RequestScopes (static final ScopedValue)"
        TENANT["TENANT_ID<br/>'tenant_abc123'"]
        MEMBER["MEMBER_ID<br/>UUID"]
        ROLE["ORG_ROLE<br/>'admin'"]
        ORG["ORG_ID<br/>'org_xyz'<br/>(shared schema only)"]
        CUST["CUSTOMER_ID<br/>(portal only)"]
        PORTAL["PORTAL_CONTACT_ID<br/>(portal only)"]
    end

    TenantFilter -->|"binds"| TENANT
    TenantFilter -->|"binds (shared)"| ORG
    MemberFilter -->|"binds"| MEMBER
    MemberFilter -->|"binds"| ROLE
    CustomerAuthFilter -->|"binds (portal)"| CUST
    CustomerAuthFilter -->|"binds (portal)"| PORTAL

    TENANT --> TenantResolver["TenantIdentifierResolver<br/>(Hibernate)"]
    MEMBER --> Controllers["Controllers<br/>(requireMemberId())"]
    ROLE --> Auth["@PreAuthorize<br/>(hasRole checks)"]
    ORG --> HibFilter["Hibernate @Filter<br/>(tenant_id = ?)"]
```

> **Why ScopedValue over ThreadLocal?** Auto-cleanup when lambda exits (no try-finally), immutable within scope (no mid-request mutation), O(1) memory with virtual threads (no copying), and `StructuredTaskScope` inherits bindings automatically.

---

## 7. Key Use Cases

### 7.1 Organization Onboarding

```mermaid
sequenceDiagram
    actor User
    participant Clerk
    participant NextJS as Next.js
    participant API as Spring Boot
    participant DB as PostgreSQL

    User->>Clerk: Sign up + Create Organization
    Clerk->>NextJS: Webhook: organization.created

    NextJS->>NextJS: verifyWebhook() (Svix)
    NextJS->>API: POST /internal/orgs/provision<br/>{clerkOrgId, orgName}

    rect rgb(240, 248, 255)
        Note over API,DB: All orgs start as STARTER
        API->>DB: INSERT organizations (tier=STARTER)
        API->>DB: INSERT org_schema_mapping<br/>(org → "tenant_shared")
        API->>DB: UPDATE status = COMPLETED
    end

    API-->>NextJS: 201 Created

    Note over User,DB: Clerk also sends membership.created
    Clerk->>NextJS: Webhook: organizationMembership.created
    NextJS->>API: POST /internal/members/sync<br/>{clerkUserId, email, role}
    API->>DB: UPSERT member in tenant_shared<br/>(with tenant_id = org_id)
```

### 7.2 Starter → Pro Upgrade

```mermaid
sequenceDiagram
    actor User
    participant FE as Settings / Billing
    participant API as Spring Boot
    participant DB as PostgreSQL

    User->>FE: Click "Upgrade to Pro"
    FE->>API: POST /api/billing/change-plan<br/>{planSlug: "pro"}

    rect rgb(255, 240, 240)
        Note over API,DB: TenantUpgradeService
        API->>DB: CREATE SCHEMA tenant_hash
        API->>DB: Flyway: run V1-V18 migrations
        API->>DB: Copy data FROM tenant_shared<br/>WHERE tenant_id = org_id
        API->>DB: BEGIN TX
        API->>DB: UPDATE org_schema_mapping<br/>SET schema_name = 'tenant_hash'
        API->>DB: DELETE FROM tenant_shared.*<br/>WHERE tenant_id = org_id
        API->>DB: COMMIT
    end

    API->>API: Evict tenant cache
    API->>DB: UPDATE org tier=PRO, status=COMPLETED
    API-->>FE: 200 OK
    Note over User: Next request resolves to dedicated schema
```

### 7.3 Document Upload (Presigned URL)

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant NextJS as Next.js
    participant API as Spring Boot
    participant S3 as AWS S3

    User->>Browser: Select file to upload
    Browser->>NextJS: Submit upload
    NextJS->>API: POST /api/projects/{id}/documents/upload-init<br/>{fileName, contentType, size}
    API->>API: Auth + tenant resolution
    API->>API: Generate S3 key:<br/>org/{orgId}/project/{projId}/{docId}
    API->>S3: Generate presigned PUT URL (1h expiry)
    API->>API: INSERT document (status=PENDING)
    API-->>Browser: {documentId, presignedUrl}

    Browser->>S3: PUT file directly (presigned URL)
    S3-->>Browser: 200 OK

    Browser->>API: POST /api/documents/{id}/confirm
    API->>API: UPDATE status = UPLOADED
    API->>API: Publish DocumentUploadedEvent
    API-->>Browser: Complete

    Note over API: Event handlers fire (AFTER_COMMIT):<br/>AuditEvent logged<br/>Notification to project leads<br/>Portal read-model updated (if SHARED)
```

### 7.4 Task & Time Tracking Workflow

```mermaid
stateDiagram-v2
    [*] --> OPEN: Task created
    OPEN --> IN_PROGRESS: Claimed by member
    IN_PROGRESS --> OPEN: Released (unclaim)
    IN_PROGRESS --> COMPLETED: Marked done
    IN_PROGRESS --> CANCELLED: Cancelled
    COMPLETED --> OPEN: Reopened
    OPEN --> CANCELLED: Cancelled

    state IN_PROGRESS {
        [*] --> Working
        Working --> LogTime: Log time entry
        LogTime --> Working: Continue
        Working --> Comment: Add comment
        Comment --> Working: Continue
    }
```

```mermaid
flowchart LR
    subgraph "Time Tracking Data Flow"
        Log["Log Time Entry<br/>(task + date + minutes + billable)"]
        TaskRollup["Task Time<br/>sum per task"]
        ProjectSummary["Project Time Summary<br/>(by member, by task, date range)"]
        MyWork["My Work View<br/>(cross-project tasks + time)"]
    end

    Log --> TaskRollup
    TaskRollup --> ProjectSummary
    TaskRollup --> MyWork
```

### 7.5 Customer Portal Access

```mermaid
sequenceDiagram
    actor Admin as Org Admin
    actor Client as External Client
    participant App as DocTeams App
    participant API as Spring Boot
    participant PortalUI as Portal UI
    participant ReadModel as Portal Read-Model

    Admin->>App: Create Customer then Add Portal Contact
    App->>API: POST /api/customers/{id}/contacts<br/>{email, displayName, role}
    API->>API: Create PortalContact entity

    Admin->>App: Request magic link for contact
    App->>API: POST /portal/auth/request-link
    API->>API: Generate 32-byte token<br/>Store SHA-256 hash (15min TTL)
    API-->>Admin: Magic link URL

    Admin-->>Client: Share link via email

    Client->>PortalUI: Click magic link
    PortalUI->>API: POST /portal/auth/exchange<br/>{token}
    API->>API: Validate hash + expiry<br/>Mark token as used
    API-->>PortalUI: Portal JWT (1h TTL)<br/>{customerId, contactId, orgId}

    Client->>PortalUI: View shared projects
    PortalUI->>API: GET /portal/projects<br/>(Portal JWT)
    API->>ReadModel: Query portal.portal_projects<br/>WHERE customer_id = ? AND org_id = ?
    ReadModel-->>API: Projects list
    API-->>PortalUI: Filtered projects (read-only)

    Client->>PortalUI: Add comment on document
    PortalUI->>API: POST /portal/comments
    API->>API: Comment visibility = CUSTOMER_VISIBLE
```

### 7.6 Notification & Event Flow

```mermaid
flowchart TB
    subgraph "Trigger Events (in Domain Services)"
        CommentCreated["CommentCreatedEvent"]
        TaskAssigned["TaskAssignedEvent"]
        TaskClaimed["TaskClaimedEvent"]
        TaskStatus["TaskStatusChangedEvent"]
        DocUploaded["DocumentUploadedEvent"]
        MemberAdded["MemberAddedToProjectEvent"]
    end

    subgraph "Spring ApplicationEventPublisher"
        Publisher["publishEvent()"]
    end

    subgraph "Event Handlers (AFTER_COMMIT)"
        NotifHandler["NotificationEventHandler<br/>(fanout to recipients)"]
        AuditHandler["AuditEventHandler<br/>(append to audit_events)"]
        PortalHandler["PortalEventHandler<br/>(sync to portal read-model)"]
    end

    subgraph "Delivery"
        InApp["In-App Notifications<br/>(polling every 30s)"]
        AuditLog["Audit Log<br/>(JSONB details)"]
        PortalRM["Portal Read-Model<br/>(JDBC upserts)"]
        Email["Email Channel<br/>(stub for future)"]
    end

    CommentCreated --> Publisher
    TaskAssigned --> Publisher
    TaskClaimed --> Publisher
    TaskStatus --> Publisher
    DocUploaded --> Publisher
    MemberAdded --> Publisher

    Publisher --> NotifHandler --> InApp
    Publisher --> AuditHandler --> AuditLog
    Publisher --> PortalHandler --> PortalRM
    NotifHandler -.->|"future"| Email
```

---

## 8. Webhook & Internal API Topology

```mermaid
flowchart TB
    Clerk["Clerk Cloud<br/>(Svix webhooks)"]

    subgraph "Next.js Webhook Handler"
        Route["POST /api/webhooks/clerk<br/>(verify Svix signature)"]
    end

    subgraph "Spring Boot /internal/** (API-key secured)"
        Provision["POST /internal/orgs/provision<br/>Create schema + Flyway + mapping"]
        MemberSync["POST /internal/orgs/{id}/members<br/>Upsert member from Clerk"]
        PlanSync["POST /internal/orgs/plan-sync<br/>Update tier + evict cache"]
    end

    Clerk -->|"organization.created"| Route
    Clerk -->|"organization.updated"| Route
    Clerk -->|"organizationMembership.*"| Route
    Clerk -->|"subscription.*"| Route

    Route -->|"org.created"| Provision
    Route -->|"membership.*"| MemberSync
    Route -->|"subscription.*"| PlanSync
```

---

## 9. Infrastructure & Deployment

```mermaid
flowchart TB
    subgraph "Internet"
        Users["Users / Browsers"]
        ClerkSvc["Clerk Cloud"]
    end

    subgraph "AWS"
        subgraph "Public Subnets"
            ALB["Public ALB<br/>HTTPS:443"]
            IntALB["Internal ALB<br/>HTTP:8080"]
        end

        subgraph "Private Subnets (ECS Fargate)"
            FE["Next.js :3000<br/>(~208MB image)"]
            BE["Spring Boot :8080<br/>(~289MB image)"]
        end

        ECR["ECR<br/>(container images)"]
        SM["Secrets Manager"]
        S3["S3 Bucket<br/>(docteams-env)"]
        CW["CloudWatch<br/>(logs + insights)"]
    end

    subgraph "External"
        Neon[("Neon Postgres<br/>+ PgBouncer")]
    end

    Users -->|"HTTPS"| ALB
    ClerkSvc -->|"webhooks"| ALB
    ALB -->|"/*"| FE
    ALB -->|"/api/*"| BE
    FE -->|"X-API-KEY"| IntALB --> BE
    BE --> Neon
    BE --> S3
    BE --> SM
```

### Connection Architecture

```mermaid
flowchart LR
    subgraph "Spring Boot"
        HikariCP["HikariCP Pool<br/>(10 connections)"]
        MigrationDS["Migration DataSource<br/>(direct, no pool)"]
        PortalDS["Portal DataSource<br/>(portal schema, JDBC)"]
    end

    HikariCP -->|"app traffic<br/>SET search_path"| PgBouncer["Neon PgBouncer<br/>(transaction mode)"]
    MigrationDS -->|"DDL only<br/>CREATE SCHEMA, ALTER TABLE"| Direct["Neon Direct"]
    PortalDS -->|"portal queries"| Direct

    PgBouncer --> PG[("PostgreSQL 16")]
    Direct --> PG
```

---

## 10. Frontend Application Map

```mermaid
flowchart TB
    subgraph "Route Groups"
        Marketing["(marketing)/<br/>Landing Page, Pricing"]
        Auth["(auth)/<br/>Sign-In, Sign-Up"]
        App["(app)/org/[slug]/<br/>Authenticated App Shell"]
        Portal["portal/<br/>Customer Portal"]
    end

    subgraph "App Shell Layout"
        Sidebar["DesktopSidebar<br/>(dark olive-950,<br/>motion indicator)"]
        Header["Header<br/>(breadcrumbs,<br/>NotificationBell,<br/>OrgSwitcher,<br/>UserButton)"]
    end

    subgraph "Pages"
        Dashboard["Dashboard<br/>(stats + recent projects)"]
        Projects["Projects"]
        Customers["Customers"]
        MyWork["My Work<br/>(cross-project)"]
        Team["Team"]
        Notifications["Notifications"]
        Settings["Settings + Billing"]
    end

    subgraph "Project Detail (5 tabs)"
        Docs["Documents Tab"]
        Tasks["Tasks Tab"]
        Members["Members Tab"]
        Time["Time Tab"]
        Activity["Activity Tab"]
    end

    App --> Sidebar
    App --> Header
    App --> Dashboard
    App --> Projects
    App --> Customers
    App --> MyWork
    App --> Team
    App --> Notifications
    App --> Settings

    Projects --> Docs
    Projects --> Tasks
    Projects --> Members
    Projects --> Time
    Projects --> Activity
```

---

## 11. Backend Package Map

```mermaid
flowchart TB
    subgraph "Cross-Cutting Infrastructure"
        config["config/<br/>Security, Hibernate, S3,<br/>Flyway, Retry"]
        multitenancy["multitenancy/<br/>RequestScopes, ScopedFilterChain,<br/>TenantFilter, ConnectionProvider,<br/>TenantFilterTransactionManager"]
        security["security/<br/>JWT filter, API key filter,<br/>ClerkJwtAuthConverter, Roles"]
        event["event/<br/>10 domain event records"]
        exception["exception/<br/>ResourceNotFound,<br/>Forbidden, PlanLimit"]
    end

    subgraph "Core Domain (tenant-scoped)"
        project["project/<br/>Project, Controller,<br/>Service, ProjectAccess"]
        document["document/<br/>Document, presigned URLs,<br/>scopes + visibility"]
        member["member/<br/>Member, ProjectMember,<br/>MemberSyncService,<br/>ProjectAccessService"]
        customer["customer/<br/>Customer, CustomerProject,<br/>linking + archive"]
        task["task/<br/>Task, claim/release,<br/>status + priority"]
    end

    subgraph "Supporting Domain (tenant-scoped)"
        timeentry["timeentry/<br/>TimeEntry, projections,<br/>project summaries"]
        comment["comment/<br/>Comment, threaded,<br/>visibility control"]
        notification["notification/<br/>Notification, preferences,<br/>templates, channels"]
        audit["audit/<br/>AuditEvent (immutable),<br/>query API, builder"]
        activity["activity/<br/>Feed aggregation<br/>(query-time, not stored)"]
        mywork["mywork/<br/>Cross-project view<br/>(self-scoped queries)"]
    end

    subgraph "Platform Services"
        provisioning["provisioning/<br/>TenantProvisioning,<br/>TenantUpgrade,<br/>Organization, Tier"]
        billing["billing/<br/>Subscription,<br/>PlanSyncService"]
        portal["portal/<br/>PortalContact, MagicLink,<br/>auth, read-model sync"]
        customerbackend["customerbackend/<br/>Portal read-model repos,<br/>event handlers (JDBC)"]
    end
```

---

## 12. Database Schema Map (4 schemas)

```mermaid
flowchart TB
    subgraph "public schema"
        orgs["organizations"]
        mapping["org_schema_mapping"]
        subs["subscriptions"]
        webhooks["processed_webhooks"]
    end

    subgraph "tenant_shared (Starter orgs — RLS)"
        sp["projects"]
        sd["documents"]
        sm["members"]
        spm["project_members"]
        sc["customers"]
        st["tasks"]
        ste["time_entries"]
        scm["comments"]
        sn["notifications"]
        sa["audit_events"]
        spc["portal_contacts"]
        smlt["magic_link_tokens"]
    end

    subgraph "tenant_hash (each Pro org)"
        dp["projects"]
        dd["documents"]
        dm["members"]
        dpm["project_members"]
        dc["customers"]
        dt["tasks"]
        dte["time_entries"]
        dcm["comments"]
        dn["notifications"]
        da["audit_events"]
        dpc["portal_contacts"]
        dmlt["magic_link_tokens"]
    end

    subgraph "portal schema (read-model)"
        pp["portal_projects"]
        pd["portal_documents"]
        pcm["portal_comments"]
        ps["portal_project_summaries"]
    end

    mapping -->|"STARTER"| sp
    mapping -->|"PRO"| dp
    orgs --> mapping
```

---

## 13. Development Phase Timeline

```mermaid
gantt
    title DocTeams Development Phases
    dateFormat X
    axisFormat Phase %s

    section Core Platform
    Scaffolding + Auth + Multitenancy     :done, p1a, 0, 1
    Core API + UI + Infra + CI/CD         :done, p1b, 1, 2

    section Billing
    Tiered Tenancy + Plan Enforcement     :done, p2, 2, 3
    Self-Managed Subscriptions            :done, p2b, 3, 4

    section Design
    Frontend Design Overhaul              :done, p3, 4, 5

    section Customers and Tasks
    Customers + Doc Scopes + Tasks + Portal :done, p4, 5, 6

    section Time Tracking
    Time Entries + My Work                :done, p5, 6, 7

    section Audit
    Audit Infrastructure + Events + Query :done, p6, 7, 8

    section Social
    Comments + Notifications + Activity   :done, p65, 8, 9

    section Portal Backend
    Read-Model + Domain Events            :active, p7, 9, 10
    Portal APIs + Thymeleaf Harness       :p7b, 10, 11

    section Financials
    Rate Cards + Budgets                  :p8a, 11, 12
    Profitability Reports                 :p8b, 12, 13
```

### Stats

| Metric | Count |
|--------|------:|
| Backend packages | 22 |
| Domain entities | 26 |
| REST controllers | 24 |
| API endpoints | ~80 |
| Domain events | 21 |
| DB migrations | 25 |
| Frontend pages | ~15 |
| Frontend components | ~60 |
| Backend tests | ~600 |
| Frontend tests | ~230 |

---

## Glossary

| Term | Definition |
|------|-----------|
| **Tenant** | An organization's isolated data partition — either a dedicated schema or row-filtered view |
| **Schema-per-tenant** | Pro orgs get `tenant_<hash>` — full PostgreSQL schema isolation |
| **Shared schema** | Starter orgs share `tenant_shared` with Hibernate `@Filter` + Postgres RLS |
| **ScopedValue** | Java 25 (JEP 506) replacement for ThreadLocal — immutable, auto-cleanup, virtual-thread safe |
| **Portal Contact** | External client who accesses via magic link — no Clerk account, time-limited JWT |
| **Domain Event** | In-process Spring `ApplicationEvent` for audit, notifications, and portal sync |
| **Read-Model** | Denormalized `portal` schema — maintained by event handlers, queried by portal APIs |
| **Magic Link** | 32-byte random token (SHA-256 hashed, 15min TTL, single-use) for portal authentication |
| **ProjectAccess** | Record from `ProjectAccessService.checkAccess()` — canView, canEdit, canManage, canDelete, projectRole |
| **TenantInfo** | Cached tuple of (schemaName, tier) resolved from org_schema_mapping |
