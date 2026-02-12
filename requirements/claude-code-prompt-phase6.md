You are a senior SaaS architect working on an existing multi‑tenant “DocTeams” style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with Starter (shared schema) and Pro (schema‑per‑tenant) tiers.
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org‑scoped RBAC.
- Neon Postgres + S3 + Spring Boot backend + Next.js frontend, running on ECS/Fargate.

For **Phase 6**, I want to focus on **backend‑only audit and compliance foundations**, not features that users see directly.

***

## Objective of this phase

Design and specify **audit trail and basic compliance infrastructure** that is:

- Multi‑tenant aware.
- Generic across domains (legal, consulting, etc.).
- Backend‑only in this phase (no new UI required, beyond maybe internal admin/debug endpoints later).

We’re aiming for:

- A robust **domain audit trail** (who did what, where, when).
- **Security/access events** logging.
- Basic **retention and integrity** strategy.
- Clear **tenant scoping** and access rules for logs.
- Light‑weight **extension points** so future phases can export or visualise audit data.

Do **not** pull onboarding into this phase; onboarding will be its own multi‑phase effort later.

***

## Constraints and assumptions

1. **Architecture and stack stay the same**

- Spring Boot 4 / Java 25 backend.
- Neon Postgres as the main DB (Starter shared schema, Pro schema‑per‑tenant).
- Clerk‑based auth for staff.
- ECS/Fargate for runtime.

2. **Domain baseline (for audit coverage)**

Assume we have the following key entities already modelled:

- Organization (tenant) and related tier.
- Project.
- Customer.
- User (staff).
- Task.
- TimeEntry.
- Document metadata (not binary; S3 is used for content).

Your design should treat these as the primary subjects for audit events.

3. **Scope limits**

**In scope:**

- Backend‑level audit/event model.
- Storage strategy for audit data.
- Minimal APIs / integration seams for retrieving audit data.
- Retention and access rules (conceptual + config).

**Out of scope:**

- New UI dashboards for auditors or end‑users.
- Full SIEM/log‑platform integration.
- Onboarding redesign.
- Complex regulatory frameworks (e.g., no full ISO/GDPR spec; just good, generic practice).

***

## What I want you to produce

Produce a **self‑contained markdown document** that can be merged into `ARCHITECTURE.md` as “Phase 6 — Audit & Compliance Foundations”, plus ADRs for key decisions.

### 1. Audit/event model

Define a **generic audit event model** suitable for multi‑tenant SaaS:

- Core fields:
    - `id`.
    - `occurred_at` (timestamp).
    - `org_id` / tenant identifier.
    - Actor: `user_id` (where available), plus “system”/“unknown” cases.
    - Action: a normalized `event_type` (e.g. `task.created`, `time_entry.updated`, `document.accessed`).
    - Subject:
        - `entity_type` (project, customer, task, time_entry, document, org, etc.).
        - `entity_id`.
    - Context:
        - `source` (API/UI/background job).
        - Request metadata (e.g. IP, user agent) where appropriate and not over‑collecting PII.
- Optional payload:
    - A small `details` JSON blob for key fields (e.g. status from/to, billable flag from/to), but *not* full document contents or secrets.

Clarify:

- Which entities/events **must** be logged in this phase:
    - At minimum: create/update/delete on Project, Customer, Task, TimeEntry, Document metadata, plus key security events (see below).
- How this model fits both:
    - Starter shared schema (where audit may reside).
    - Pro per‑schema tenants (do we centralise audit, or keep it in tenant schemas).

Include a simple Mermaid ER or class diagram showing the `audit_event` structure and its relationship to other entities.

### 2. Storage and multi‑tenant strategy

Decide and describe **where audit events are stored**, and how they are related to tenants:

- Options to consider:
    - Single global `audit_events` table in a shared schema with an `org_id` column.
    - Per‑tenant audit tables (aligned with per‑schema model).
    - Hybrid (e.g. global for coarse events, tenant‑local for sensitive ones).

You should:

- Pick a pragmatic approach that:
    - Scales for many tenants.
    - Makes tenant filtering efficient.
    - Keeps data isolation clear.
- Explain trade‑offs (e.g., global table + org_id vs per‑schema duplication).
- Ensure your design supports:
    - Per‑tenant queries (easily return events for org X).
    - Global operational queries (e.g. security monitoring) by platform operators.

### 3. What we log (domain vs security events)

Define **which events we log** at this stage:

1. **Domain events** (minimum set):

    - Project:
        - Created, updated (especially status changes), archived/deleted.
    - Customer:
        - Created, updated, attached/detached to projects.
    - Task:
        - Created, updated, status change, claimed/unclaimed, reassigned.
    - TimeEntry:
        - Created, updated, deleted.
    - Document meta
        - Created, updated, deleted (not binary contents).
        - Accessed (download / view) for certain scopes (e.g. customer documents, project documents).

2. **Security / access events** (minimum set):

    - Auth:
        - Login success/failure (to the extent we can read it from the backend).
    - Authorization:
        - Permission‑denied responses on protected endpoints.
    - Sensitive access:
        - Views/downloads of customer‑scope documents, if feasible.

Describe how these events will be **captured in the code**:

- E.g. service‑layer audit hooks, Spring AOP, HTTP filters, or explicit calls in key use cases.

### 4. Retention and integrity basics

Specify a **baseline policy and mechanism** for audit logs:

- Retention:
    - Default retention period(s) for audit events (e.g. 1–3 years, configurable via environment).
    - Differentiate if needed between:
        - Audit events (longer retention).
        - Operational logs (shorter retention).
- Integrity:
    - Application‑level immutability:
        - No updates to `audit_event` rows; only append and (if necessary) hard deletes via controlled maintenance jobs.
    - Optional forward‑looking design:
        - Concept for periodically hashing batches of audit records (e.g. daily), storing the hash in a separate table or external store, to **prove tamper‑evidence** later.
        - You do not need to implement hashing now, but explain how the model supports it.

Describe any **PII minimisation** decisions:

- What not to log (e.g. secrets, tokens, full ID numbers).
- How to avoid over‑collecting sensitive fields while still providing useful trails.

### 5. Tenant‑aware access to audit data

Define how **audit data can be queried** safely:

- For internal “platform operator” use (super admins):
    - Ability to query cross‑tenant logs with strict internal access controls.
- For future “org admin” use:
    - Ability to query **only their organization’s audit trail** (even if this is just a conceptual API for now).

Specify:

- How tenant filtering will be enforced technically (e.g., always scoping by `org_id` derived from auth, no free‑text filters).
- What roles/permissions are required to see audit data.

You may define one or two high‑level API shapes for future use, but keep them conceptual (e.g. `GET /internal/audit-events?orgId=...`).

### 6. Extension points (future integrations)

Without designing full SIEM integration:

- Define a simple **AuditLoggingService interface** (conceptually) that:
    - The domain code calls to record audit events.
    - Can later:
        - Write to DB.
        - Also forward to external log sinks (ELK, OpenSearch, third‑party log tools).
- Mention how this interface and event model can be reused by:
    - Future “download audit trail” features for legal/compliance clients.
    - Future portal service (e.g. portal may want some audit info to show customers what changed).

Keep this section intentionally light; focus on **not painting yourself into a corner**.

### 7. ADRs for key decisions

Add ADR‑style sections for at least:

1. **Audit storage location**
    - Global table vs per‑tenant vs hybrid.
2. **Audit event granularity**
    - Which entities and actions are logged and why.
3. **Retention strategy**
    - Default periods and configuration approach.
4. **Integrity approach**
    - Append‑only and optional hash/signature strategy.
5. **Abstraction for logging**
    - Why we use a dedicated `AuditLoggingService` (or similar) instead of sprinkling direct DB writes everywhere.

Use the same ADR format as earlier phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- Don’t change the core multi‑tenant, tasks, or billing architecture; just add audit/compliance capabilities.
- Keep everything **generic** and domain‑agnostic so legal, consulting, and other verticals can all benefit.
- Focus on clarity and implementability: engineers should be able to derive Epics/Tasks from this without re‑asking what to log or where it goes.

Return a single markdown document as your answer, ready to be merged as the new “Phase 6 — Audit & Compliance Foundations” section (plus ADRs).
