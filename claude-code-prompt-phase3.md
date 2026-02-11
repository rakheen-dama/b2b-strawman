You are a senior software architect/engineer working on an existing multi‑tenant SaaS starter.

The repository already contains:

- `ARCHITECTURE.md`, which now includes a new section **“9. Phase 2 — Billing & Tiered Tenancy”** describing the design for:
    - Clerk Billing–based subscription management.
    - Two subscription tiers (Starter: shared schema, 2 members; Pro: schema‑per‑tenant, 10 members).
    - Changes to multitenancy, onboarding, and provisioning flows.
    - Updated diagrams and ADRs.
- `TASKS.md`, which currently documents Phase 1 work and some generic housekeeping.

Your job is to create **actionable, engineering‑ready Epics and Tasks** for **Phase 2**, and append them to `TASKS.md`.

### Important constraints

- Do **not** restate or re‑design the architecture. Treat `ARCHITECTURE.md` (including section 9) and the ADRs as final.
- You **must** read and base your work strictly on:
    - The current `ARCHITECTURE.md` (especially the Phase 2 section).
    - The existing contents and structure of `TASKS.md` (respect its format and conventions).
- Do **not** change previous tasks or epics.

### Output format and structure

Update `TASKS.md` by **adding** a new section for Phase 2. Use markdown only.

1. Introduce a new top‑level heading:

   ```markdown
   ## Phase 2 — Billing & Tiered Tenancy
   ```

2. Under that heading, define **Epics** and **Tasks** using the **existing style** already used in `TASKS.md`. For example, if the file uses:

    - “Epic:” headings with bullet lists of tasks
    - or a numbered list of Epics with sub‑tasks
    - or a table with columns like `ID | Title | Owner | Status`

   then you must follow that exact pattern.

3. For each **Epic**:

    - Provide:
        - A short, outcome‑oriented title (e.g. “Integrate Clerk Billing into Org Lifecycle”).
        - A brief 1–3 sentence description that references the relevant parts of `ARCHITECTURE.md` (by section/heading if possible, not by line number).
    - Ensure the set of Epics together covers all major aspects of Phase 2 as described in `ARCHITECTURE.md`, including but not limited to:
        - Clerk Billing integration and subscription lifecycle.
        - Tier logic and plan awareness in both frontend and backend.
        - Shared‑schema vs schema‑per‑tenant handling (Starter vs Pro).
        - Member limits and feature enforcement.
        - Provisioning flows and any required data model changes.
        - Observability, testing, and migration readiness for a potential future Starter→Pro upgrade path.

4. For each **Epic**, define a set of **Tasks**:

    - Each task must be:
        - Small enough to be completed by one engineer in 0.5–2 days.
        - Immediately actionable (clear outcome, no “research only” tasks unless explicitly required by the architecture).
        - Implementable without re‑asking for requirements.
    - Tasks should:
        - Reference concrete changes (e.g. “Add X field to Y model”, “Implement Z API endpoint”, “Update tenant resolver to read plan from …”).
        - Link back to the appropriate section of `ARCHITECTURE.md` and/or ADR ID where relevant (e.g. “per ADR‑03”).
    - Include tasks for:
        - Code changes.
        - Configuration and environment updates.
        - Tests (unit/integration/e2e where appropriate).
        - Documentation updates (if required by the architecture, e.g. new admin flows).

5. Maintain traceability:

    - Where possible, annotate Epics and Tasks with:
        - A short reference to the relevant **ADR** (e.g. “ADR‑2: Tier‑dependent tenancy model”).
        - A reference to the relevant subsection of Phase 2 in `ARCHITECTURE.md` (e.g. “See 9.2.2 Plan Enforcement Strategy”).

6. Be explicit about **dependencies**:

    - If a task depends on another task or Epic, mark it clearly using the same notation `TASKS.md` already uses (e.g. “Depends on: T‑123” or “Blocked by: Epic P2‑1”).
    - Order tasks within an Epic roughly in implementation sequence (e.g. model/schema changes before API endpoints, before UI changes).

### Level of detail

- Do **not** generate code.
- Do **not** re‑list the full architecture.
- Focus on making tasks **implementation‑ready** and **unambiguous**:
    - An engineer reading `TASKS.md` + `ARCHITECTURE.md` should be able to start work without further clarification.

### Final instructions

- Return only the **updated `TASKS.md` content** as a single markdown document, including its existing content plus the new “Phase 2 — Billing & Tiered Tenancy” section appended appropriately.
- Preserve all existing sections and formatting; do not reorder or delete prior content.

