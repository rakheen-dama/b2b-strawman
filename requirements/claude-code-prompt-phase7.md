You are a senior SaaS architect working on an existing multi‑tenant “DocTeams” style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema‑per‑tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org‑scoped RBAC.
- Neon Postgres + S3 + Spring Boot backend + Next.js frontend, running on ECS/Fargate.
- A roadmap that includes:
    - Phase 3: task & time lifecycle.
    - Phase 6: audit & compliance foundations.
    - A future customer portal as a separate concern.

For **Phase 7**, I want to take the **first real step toward a customer portal**, but in a controlled, backend‑focused way.

***

## Objective of Phase 7

Design and specify:

1. A **customer‑facing backend slice** (within the existing app) that models what a future portal will need: read models, APIs, and auth.
2. A **magic‑link–based customer authentication mechanism**.
3. A **local/dev‑only test UI** (Thymeleaf) to exercise the flows.

This is **not** the final customer portal product. It is a **production‑grade backend + auth** with an **internal‑only UI harness**.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (existing tenancy model).
    - S3, ECS/Fargate, Next.js staff app.
- For Phase 7:
    - Add a separate **backend slice** called `customerbackend` (e.g. top‑level package/module) within the same deployable for now.
    - Add (or point to) a **separate database/schema** for the customer portal read model (e.g. `customer` DB or schema).
- Do not introduce major new infrastructure (no separate microservice deployment yet, no new message broker); you can rely on:
    - Spring application events as the publication mechanism from core domain to the `customerbackend`.
    - Standard JDBC/Flyway patterns for the new DB/schema.

2. **Security posture for this phase**

- Magic links must be designed as if they were going to production (short‑lived, one‑time or limited‑use tokens, proper session handling).
- The **Thymeleaf UI** is:
    - For **local/dev/test profiles only**.
    - Explicitly **not exposed** in production environments.
    - Clearly documented as a **developer/admin test harness**, not real customer UI.

3. **Out of scope for Phase 7**

- Real, production‑grade customer frontend (e.g. separate Next.js portal app).
- Payment processor / PSP integration.
- Multi‑deployment split (separate portal service cluster) — that remains a future phase.
- Company‑level access control complexities (we’ll treat customers as contacts with emails for now).

***

## What I want you to produce

Produce a **self‑contained markdown document** that can be merged into `ARCHITECTURE.md` as “Phase 7 — Customer Portal Backend Prototype”, plus ADRs for key decisions.

### 1. Customer backend slice architecture

Define the architecture of the **customerbackend** slice:

- Location and boundaries:
    - Lives as a top‑level package or module (e.g. `com.example.customerbackend`) in the Spring Boot app.
    - Has its own:
        - Controllers (REST endpoints).
        - Services.
        - Repositories.
        - Data model/read entities.
- Database:
    - Uses a **separate database or schema** for portal read models (e.g. `customer_db` or `customer` schema).
    - Explain:
        - Which data is stored there (denormalized/cached views).
        - What remains authoritative in the core DB (projects, tasks, documents, etc.).
- Integration with core domain:
    - The core app publishes Spring `ApplicationEvent`s for relevant domain changes.
    - The `customerbackend` listens to those events and updates its read models.

Provide a Mermaid **component diagram** showing:

- Core backend (existing domain).
- `customerbackend` slice.
- Core DB vs customer DB/schema.
- S3.
- Application events flow between core and `customerbackend`.

### 2. Magic‑link customer auth model

Design a **magic‑link based auth system** for customers, suitable for future production use:

1. **Identity & contact model**

    - Introduce a concept like `PortalContact`:
        - Fields: `id`, `org_id`, `customer_id`, `email`, `role` (e.g. primary, billing, general), `status`, timestamps.
    - Map:
        - Email address → `PortalContact` → Customer entity in the core domain.

2. **Magic link tokens**

    - Define a `MagicLinkToken` model:
        - `id` (token identifier, or opaque string).
        - `portal_contact_id`.
        - `token_hash` (store hash, not raw token).
        - `expires_at`.
        - `used_at` (nullable).
        - `created_at`, `created_ip` (optional).
    - Semantics:
        - Short‑lived (e.g. 5–15 minutes).
        - Single use or low reuse count.
        - Tied to a specific `PortalContact`.

3. **Flow**

    - Request link:
        - Given an email and (optionally) org slug/id, generate a magic link token for the corresponding `PortalContact`.
        - Send an email with a link embedding the token (describe conceptually; no need to pick an email provider).
    - Login:
        - Customer clicks link → hits `/customerportal/login?token=...`.
        - Backend:
            - Validates token (exists, not expired, not used/exceeded).
            - Creates a **customer session** (e.g. HTTP session or signed JWT cookie) scoped to that `PortalContact` and its org/customers.
            - Marks token as used (or increments use count).
    - Session:
        - Subsequent requests to `/customerportal/*` use this session.
        - Session timeout policy (e.g. hours) vs token lifetime (minutes).

Clarify:

- How this customer session is **fully separate** from the staff Clerk session.
- How tenant boundaries are enforced (all portal requests are implicitly scoped by `PortalContact.org_id`).

### 3. Customer‑visible read model and APIs

Design the **read‑model schema** in the customer DB/schema and the APIs the `customerbackend` exposes:

1. **Read‑model entities**

   Define denormalized entities such as:

    - `PortalProject`:
        - `id`, `org_id`, `customer_id`, `external_project_id` (link to core), `name`, `status`, key dates, basic summary fields.
    - `PortalDocument`:
        - Only customer‑visible docs (subset of core documents).
        - `id`, `org_id`, `customer_id`, `portal_project_id`, `title`, `scope`, `s3_key` or download handle, metadata.
    - `PortalComment` or `PublicComment`:
        - Comments marked as customer‑visible, with author, timestamp, content.
    - Optionally a `PortalProjectSummary`/`PaymentSummary` stub:
        - High‑level fields like total billable hours/amount, paid, outstanding (can be mock or lightly derived from core for now).

Explain:

- Which fields are populated from core events vs calculated locally.
- How consistency is handled (eventual consistency is acceptable).

2. **APIs**

   Design a small set of REST endpoints under `/customerportal/api/*`, guarded by magic‑link session, for example:

    - `GET /customerportal/api/me/projects`:
        - List projects that the current `PortalContact` can see.
    - `GET /customerportal/api/projects/{id}`:
        - Project details + status, limited fields.
    - `GET /customerportal/api/projects/{id}/documents`:
        - List customer‑visible documents with download links/IDs.
    - `GET /customerportal/api/projects/{id}/comments`:
        - List public comments.
    - Optionally:
        - `GET /customerportal/api/projects/{id}/summary`:
            - Returns payment/time summary stub.

For each endpoint, specify:

- Auth requirement (must have valid magic‑link session).
- Tenant scoping (inferred from `PortalContact`).
- Generic nature (no legal‑only concepts hard‑coded).

Include a Mermaid **sequence diagram** for:

- Customer clicking a magic link → backend validates → session created → call to list projects → data coming from customer DB.

### 4. Event publication and consumption

Define how the core app and `customerbackend` interact via **Spring application events**:

1. **Event types from the core**

   Conceptual events such as:

    - `CustomerCreated/Updated`.
    - `ProjectCreated/Updated/StatusChanged`.
    - `DocumentCreated/Updated` with a flag indicating customer‑visible.
    - `PublicCommentAdded`.

For each:

- Outline the payload (IDs, key fields).
- Clarify that events are **in‑process Spring events for now**, but shaped so they can later be mapped to out‑of‑process messages (SQS, etc.).

2. **Event handlers in `customerbackend`**

    - Describe how `customerbackend` listens to these events and:
        - Creates/updates read‑model entities in the customer DB/schema.
    - Consider basic error handling (retries, dead‑letter path if needed later).

### 5. Local/dev‑only Thymeleaf UI

Define a **minimal test harness UI**:

- Accessible only under a `local`/`dev` Spring profile (e.g. conditional configuration).
- Views like:

    1. **Magic link generator page**:
        - Form to enter:
            - Email.
            - Org id/slug.
        - Backend creates a `PortalContact` if needed and generates a magic link token.
        - For dev/local:
            - Instead of sending email, display the magic link URL on the page so it can be manually clicked.

    2. **Portal view page**:
        - When accessed via a valid magic link:
            - Shows a simple list of the customer’s projects.
            - Clicking a project shows:
                - Status.
                - Customer‑visible documents.
                - Public comments.
                - Summary stub (if implemented).

Emphasize:

- This UI is not meant for production.
- All templates/controllers are wired only for dev/local profile.
- This is to **exercise and validate** the `customerbackend` + magic‑link auth + read models.

### 6. ADRs for key decisions

Add ADR‑style sections for at least:

1. **Magic link auth vs full account‑based auth for customers**:
    - Why magic links are chosen first.
    - When we might need to upgrade or supplement them.
2. **Separate customer DB/schema**:
    - Why the portal read model is stored separately from core domain tables.
    - How this sets up a future separate deployable.
3. **Use of Spring application events for now**:
    - Why in‑process events are acceptable as a first step.
    - How this maps to a future message bus (SQS, etc.).
4. **Local‑only Thymeleaf UI**:
    - Why the portal UI is currently an internal test harness.
    - How and when it will be replaced by a real portal frontend.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Keep the design **generic and domain‑agnostic** so it works for legal and non‑legal customers.
- Do not change the core multi‑tenant or staff auth model.
- Focus on:
    - Getting the customer backend slice, magic‑link auth, and read model right.
    - Keeping the UI deliberately minimal and dev‑only.

Return a single markdown document as your answer, ready to be merged as “Phase 7 — Customer Portal Backend Prototype” and ADRs.

