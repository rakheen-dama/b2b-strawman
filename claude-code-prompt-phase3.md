You are a senior SaaS architect working on an existing multi‑tenant “DocTeams” style app.

The current state:

- Organizations = tenants, with:
    - Projects.
    - Documents stored in S3, metadata in Neon Postgres.
    - Tiered tenancy (Starter shared schema, Pro schema‑per‑tenant) as per `ARCHITECTURE.md` Phase 2.
- Users are staff inside organizations (via Clerk).
- There is no explicit notion of “customers/clients” yet, and no external client portal.

### New domain concepts to introduce

Extend the existing architecture and domain model to support:

1. **Customers (clients)**

    - Each Organization can onboard **Customers** (their clients).
    - A Customer:
        - Belongs to exactly one Organization.
        - Can be linked to 0..N Projects under that org.
    - For now, Customers are *data only* plus a future‑facing identity:
        - Contact details (name, email, phone, optional ID number).
        - Relationship to one or more Projects.
    - The design should make it easy to later:
        - Let Customers authenticate to a **customer portal** and see only:
            - Their own projects/cases.
            - Their own documents and selected project documents.
            - Status/updates and timelines.[3][8][9][1][2]

2. **Document scopes**

   We already have documents attached to projects. Extend the model so each document has a **scope** and relationship that reflects where it “lives”:

    - **Org scope**:
        - Organization‑level documents (compliance files, policies, firm registrations).
        - Visible to staff with appropriate org‑wide permissions.
    - **Project scope**:
        - Documents attached to a specific Project (matter working files, contracts, invoices, notes).
    - **Customer scope**:
        - Documents mainly about a Customer as a person/entity (ID docs, proof of address, engagement letters, KYC forms).
        - May or may not be tied to a single Project (e.g., global KYC stored once and reused across projects).
    - The model must:
        - Allow one document to be clearly scoped (org / project / customer) and linked with appropriate foreign keys.
        - Support permission rules that, in future, will drive what a Customer can see in the portal vs what is staff‑only.

3. **Tasks on projects with “claim” workflow**

    - Introduce **Tasks** entity, scoped to a Project (and therefore to an Organization).
    - A Task has:
        - Title, description, status (e.g. open / in_progress / done), timestamps.
        - Optional assignment to a specific staff member.
    - Support a **claim** pattern:
        - Tasks can initially be unassigned but visible to all project members.
        - Any eligible staff member can “claim” a task, which assigns it to them and removes it from the unclaimed pool for others.[5][6][4]
    - Include enough metadata to support future workflows (due dates, type, maybe priority).

4. **Future customer portal (not built now, but designed for)**

    - Do not implement the full portal yet.
    - Update the architecture so that:
        - There is a clear separation between **internal staff‑facing APIs/UI** and potential **customer‑facing APIs/UI**.
        - Permissions & scopes are designed so that:
            - Customers can authenticate (later) and see only their data.
            - Staff can see and manage everything for their org.
    - Include a high‑level sketch of:
        - How a future customer identity (e.g., separate Clerk instance, public invite link, or token‑based access) would map onto the existing Customer records.
        - Which document scopes and project fields would be visible to customers vs internal‑only.

### Requirements and constraints

- **Preserve the existing multi‑tenant and tiered architecture**:
    - Starter orgs remain in a shared schema; Pro orgs retain schema‑per‑tenant.
    - Customers, documents, tasks must work correctly in both tenancy models.
- **RBAC and data isolation**:
    - Customers are *not* tenants; they are children of an Organization tenant.
    - Ensure customer data is always tenant‑scoped and cannot leak across Organizations.[10][11]
- **No major tech stack changes**:
    - Same stack: Next.js + Clerk, Spring Boot + Neon, S3, ECS/Fargate.

### What I want from you (this agent)

1. **Architecture update (short and focused)**

    - Update or extend `ARCHITECTURE.md` to:
        - Introduce the `Customer`, `Task`, and enhanced `Document` domain concepts.
        - Show how they relate to Organization and Project in the data model (including in both shared schema and per‑schema setups).
        - Describe any new API surface: e.g. endpoints for customers, tasks, and scoped document queries.
        - Outline the permission model for:
            - Staff access to customers, documents, and tasks.
            - Future customer access via a portal (conceptual only for now).

2. **Mermaid diagrams**

    - Add or extend Mermaid diagrams to cover:
        - Domain model relationships: Organization, Customer, Project, Document (with scope), Task.
        - A sequence for:
            - Staff onboarding a new Customer and linking them to a Project.
            - Staff uploading a customer‑scoped document (e.g. proof of ID).
            - Staff creating and claiming tasks within a project.

3. **ADRs for new decisions**

   Add ADRs (in the existing ADR style) for at least:

    - Why Customers are modelled as children of Organization rather than separate tenants.
    - How document scope is represented (single enum + foreign keys vs separate tables per scope, etc.).
    - How tasks and “claiming” are modelled (single Task entity with status/assignee vs more complex workflow engine).
    - High‑level approach to future customer portal (e.g., reuse Clerk vs separate auth, how to map external identities to internal Customers).

4. **Backlog input (for later TASKS.md work)**

    - Provide a concise list of **capability slices** that future task planners can turn into Epics, such as:
        - “Customer lifecycle and linking to projects”
        - “Scoped document handling (org/project/customer)”
        - “Project task management with claim flow”
        - “Customer portal groundwork (auth model, read‑only views)”

   Do not write the actual TASKS.md items, but make the capabilities concrete enough that another agent can derive tasks directly.

### Style and constraints

- Do not re‑litigate existing Phase 1 & Phase 2 decisions unless the new concepts genuinely require tweaks.
- Keep the architecture changes additive and evolutionary.
- Return a single markdown document that can be merged into or replace `ARCHITECTURE.md`, plus the new ADRs.

