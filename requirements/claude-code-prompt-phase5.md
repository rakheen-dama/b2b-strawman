You are a senior SaaS architect working on an existing multi‑tenant “DocTeams” style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema‑per‑tenant) tiers.
- Projects per organization.
- Documents stored in S3, with metadata in Neon Postgres.
- Internal staff users authenticated via Clerk, with org‑scoped RBAC.
- Emerging domain concepts for Customers and document scopes (org / project / customer), but **no live customer portal yet**.

For the **next phase**, I want to focus on **internal work management** only, not on the customer portal itself.

***

## Objective of this phase

Design and specify the **task lifecycle model** and related capabilities inside the existing architecture:

- Tasks attached to projects.
- Claim/unclaim behaviour for tasks.
- Time entries on tasks (billable vs non‑billable).
- “My Work” view for staff (assigned + unassigned tasks across projects).
- Per‑project rollups of total billable/non‑billable time.

Add only the **minimal seams** necessary so that a future customer portal can *consume* task and status data, but **do not** design or build the portal architecture in this phase.

The output should be an updated architecture section (e.g. “Phase 3 — Task & Time Lifecycle”) plus ADRs.

***

## Context and constraints

1. **Architecture and stack (must remain unchanged at a high level)**

- Frontend: Next.js (app router, TypeScript, Tailwind, Shadcn), staff‑facing only.
- Backend: Spring Boot 4 (Java 25), REST API.
- Auth: Clerk for staff users + organizations.
- DB: Neon Postgres with:
    - Starter tier: shared schema + tenant column / RLS.
    - Pro tier: schema‑per‑tenant with Flyway provisioning.
- Storage: S3 for documents.
- Runtime: containerized, ECS/Fargate.

2. **Domain baseline**

Assume the core app currently has:

- Organization (tenant).
- Project belonging to an Organization.
- Staff User (via Clerk).
- Customer (client) belonging to an Organization, linked to Projects.
- Document with a scope (org / project / customer).

You may refine or reference these as needed, but **do not redesign tenancy or identity**.

3. **Scope of this phase**

**In scope:**

- Designing the full domain model and flows for:
    - Tasks.
    - Claim/unclaim.
    - Time entries (billable/non‑billable).
    - Aggregations (per user and per project).
- Backend endpoints and internal services required to support:
    - “My Work” view for staff.
    - Project‑level task and time summaries.
- Minimal event/notification seams so a future customer portal could:
    - Listen for project/task/status changes.
    - Read task summaries/status for customer‑visible views.

**Explicitly out of scope:**

- Customer portal UI or backend.
- Customer authentication and portal‑side authorization.
- Separation into a second deployable service.
- Deep event bus / integration architecture.

We only want **lightweight seams** now (e.g., domain events or clearly defined read endpoints) that future phases can build on.

***

## What I want you to produce

Produce a **self‑contained markdown document** suitable to merge into `ARCHITECTURE.md` as a new section (e.g. “Phase 3 — Task & Time Lifecycle”), plus new ADR entries.

### 1. Domain model for tasks and time

Define the domain model additions in a way that works for both Starter and Pro tenants:

- **Task**:
    - Belongs to a Project (and therefore an Organization).
    - Fields such as:
        - `id`, `project_id`, `title`, `description`.
        - `status` (e.g. open, in_progress, blocked, done).
        - `priority`, `due_date`, timestamps.
        - `assigned_user_id` (nullable for unassigned).
        - Optional `customer_id` link if needed later for portal visibility.
- **Task lifecycle**:
    - Creation, update, status transitions.
    - Claim/unclaim flow:
        - Unassigned → claimed by a staff member.
        - Reassignment rules (who can unassign/reassign).
- **TimeEntry**:
    - Belongs to a Task (and therefore to a Project and Organization).
    - Fields such as:
        - `id`, `task_id`, `user_id`.
        - `date`, `duration` (e.g., minutes).
        - `billable` flag, optional rate, description/notes.
- **Aggregations**:
    - How project‑level totals (billable/non‑billable) are computed:
        - On the fly via queries, or
        - Materialized in summary tables / cached columns.

Include a Mermaid **entity‑relationship** diagram showing Organizations, Projects, Users, Tasks, TimeEntries (and Customers if needed for context).

### 2. Core flows and backend behaviour

Describe the key flows and the backend responsibilities:

1. **“My Work” view for staff**
    - Requirements:
        - Staff sees:
            - All tasks assigned to them across all projects in their current Organization.
            - All unassigned tasks in projects they belong to.
    - Specify:
        - Required queries/services.
        - How tenant boundaries and RBAC are applied.
        - Any paging/filtering considerations.

2. **Task creation & management**
    - Creating tasks (typically in the context of a project).
    - Editing tasks (fields that are editable and by whom).
    - Status transitions (what’s allowed and who can perform them).
    - Claim/unclaim rules:
        - Who can claim (e.g. any project member).
        - Whether tasks can be unclaimed or reassigned and under what roles.

3. **Time entry lifecycle**
    - Recording time against a task:
        - Creating a time entry.
        - Editing/deleting time entries (ownership & permissions).
    - Billable vs non‑billable handling:
        - How the flag is used in summaries.
        - Any basic validation (e.g. non‑negative durations, date constraints).

4. **Project rollups**
    - How to obtain:
        - Total billable time per project.
        - Total non‑billable time per project.
    - Consider:
        - Trade‑offs between calculated views vs materialized aggregates.
        - How this interacts with Starter (shared schema) vs Pro (per schema).

Include at least one Mermaid **sequence diagram** for:

- User viewing “My Work” (request → backend → DB queries).
- User claiming a task and adding time to it.

### 3. API surface (high‑level)

Define the **API surface** for the internal staff‑facing app (no need for exhaustive OpenAPI, but enough to be clear):

- Endpoints for:
    - List “my tasks” across projects.
    - List unassigned tasks in my projects.
    - Create/update tasks.
    - Claim/unclaim task.
    - Create/update/delete time entries.
    - Get project task/time summary.
- For each, specify:
    - HTTP method and a sample path.
    - Auth/tenant requirements (e.g. must be logged in, must belong to org, must be member of the project).
    - Whether it’s read‑heavy, write‑heavy, or mixed.

Emphasize how these APIs fit cleanly into the existing Spring Boot + Next.js architecture.

### 4. Minimal seams for future customer portal

Without designing the portal itself, define the **minimal integration seams** that will make portal integration easy later:

- Either:
    - A **small set of domain events** (e.g. `ProjectStatusChanged`, `TaskCreated`, `TaskStatusChanged`) with shapes described conceptually, or
    - A **small set of read‑only endpoints** that a future portal service could call to fetch task/project summaries for a given customer.
- For each seam:
    - Clarify what is exposed (fields, identifiers).
    - Clarify what remains internal (e.g. staff‑only fields, internal notes).
- Make sure:
    - There’s a clear mapping from a **Customer** to the tasks/projects they can see.
    - Tenant boundaries are preserved.

Keep this section intentionally light: we just want hooks, not a full eventing platform or portal API.

### 5. ADRs for key decisions

Add ADR‑style sections (within the doc or as separate ADRs) for at least:

1. **Task modelling approach**
    - Single `Task` entity per Project vs more complex workflow engine.
2. **Claim/unclaim semantics**
    - How claiming works, and why this model is chosen.
3. **Time tracking model**
    - Time entries attached to tasks vs directly to projects; billable/non‑billable strategy.
4. **Aggregation strategy**
    - On‑the‑fly queries vs materialized summaries.
5. **Future portal seams**
    - Why we expose only minimal events/endpoints now and defer portal architecture.

Use the ADR format we’ve been using (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Do **not** change the core multi‑tenant or billing architecture.
- Stay within the existing tech stack; no new major technologies.
- Keep everything generic and reusable across business domains.
- Focus on clarity and implementability: an engineer should be able to derive Epics and Tasks from this without re‑asking requirements.

Return a single markdown document as your answer, ready to be merged as the new “Phase 5 — Task & Time Lifecycle” section (plus ADRs).

Sources
