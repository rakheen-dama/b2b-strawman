# Architecture Document — Writing Prompt

This file is read by the Step 1 writing subagent. It is NOT loaded into the orchestrator.

## Size Budget

The output document should be **600–1000 lines (40–55KB)**. Prioritize depth in core sections, compress supporting sections.

| Section | Budget | Priority |
|---------|--------|----------|
| Domain Model | up to 200 lines | **Core** — full field tables, ER diagram, design rationale |
| API Surface | up to 200 lines | **Core** — endpoint tables, JSON shapes, query params |
| Database Migrations | up to 150 lines | **Core** — full SQL, indexes, RLS policies |
| Core Flows & Permissions | up to 150 lines | High — flows + inline permission rules |
| Capability Slices | up to 100 lines | High — primary interface to `/breakdown` skill |
| Sequence Diagrams | up to 80 lines | Supporting — 2–3 key flows |
| Overview | up to 40 lines | Supporting — concise summary |
| Additional Sections | up to 80 lines | Conditional — only if requirements demand |
| ADR Index | up to 20 lines | Supporting — link table only |

Prefer **tables over prose**. One SQL example per migration, not three. If a section exceeds its budget, split into subsections or move details to the Capability Slices section where they become actionable.

## Required Sections

The document MUST include all of these, adapted to whatever the requirements ask for:

### Section N.1 — Overview
- 2–3 paragraph summary of what this phase adds
- "What's new" table comparing existing vs new capabilities

### Section N.2 — Domain Model
- New entities with full field tables (field, type, constraints, notes)
- Design decisions for each entity (inline, not separate)
- Updated Mermaid **ER diagram** showing ALL tenant-schema entities (existing + new)
- Call out what's unchanged from prior phases

### Section N.3 — Core Flows & Permissions
- One subsection per major flow (as described in requirements)
- SQL query examples for non-trivial queries
- Service-layer method signatures (conceptual Java)
- Tenant boundary explanation (how it works for Starter + Pro)
- **RBAC / permission rules inline per flow** (role → allowed operations table)
- Role hierarchy: Owner > Admin > Project Lead > Contributor
- Paging/filtering considerations

### Section N.4 — API Surface
- Tables with: Method, Path, Description, Auth, Read/Write
- Request/response JSON shapes for key endpoints
- Query parameter documentation
- Group by capability area (not by HTTP method)

### Section N.5 — Sequence Diagrams
- At least 2 Mermaid **sequence diagrams** for key flows
- Include: Actor → Browser → Next.js → Spring Boot → DB
- Show error/conflict paths where relevant (e.g., race conditions)

### Section N.6 — Additional Sections (as needed)
- Portal seams, security considerations, S3 key structure, etc.
- Whatever the requirements call for that doesn't fit above
- Omit this section entirely if nothing is needed

### Section N.7 — Database Migrations
- Migration number, description, full SQL
- Index definitions with rationale
- RLS policies for shared-schema support
- Backfill strategy if modifying existing tables
- Note any prerequisite indexes from prior migrations

### Section N.8 — Capability Slices

This is the **primary downstream interface** — the `/breakdown` skill converts these into epics.

Start with a machine-parseable contract block:

```markdown
<!-- BREAKDOWN-CONTRACT
phase: {N}
title: {Phase Title}
slices: {count}
new_entities: [{Entity1}, {Entity2}]
new_migrations: [{V_N__description}]
depends_on_entities: [{existing entities this phase touches}]
adr_refs: [{ADR-NNN}, ...]
-->
```

Then for each slice:
- 3–6 independently deployable slices
- Each with: scope (backend/frontend), key deliverables, dependencies, test expectations
- Include specific file paths and patterns to follow
- Designed for the `/breakdown` skill to turn into full epics

### Section N.9 — ADR Index
- Table linking to all ADRs (existing referenced + new)

## Writing Style Rules

- Match the voice and formatting of existing architecture docs in `architecture/`
- Use `code blocks` for SQL, Java, JSON, and file paths
- Use Mermaid for all diagrams (ER, sequence, flowchart)
- Reference existing ADRs by number and link: `[ADR-019](../adr/ADR-019-task-claim-workflow.md)` (relative from `architecture/`)
- Include a merge instruction at the top: `> Merge into architecture/ARCHITECTURE.md as **Section N**.`
- Every design decision should state *why*, not just *what*
- Explicitly call out what's out of scope

## ADR Format

For each ADR required by the requirements, create `adr/ADR-{NNN}-{kebab-case-title}.md`:

```markdown
# ADR-{NNN}: {Title}

**Status**: Accepted

**Context**: {Why this decision is needed — 1-2 paragraphs}

**Options Considered**:

1. **{Option name}** — {Brief description}
   - Pros: {bulleted}
   - Cons: {bulleted}

2. ... (at least 3 options per ADR)

**Decision**: {Which option and why — 1 sentence}

**Rationale**: {Detailed reasoning — 1-3 paragraphs}

**Consequences**:
- {Bulleted list of implications}
```

### ADR Quality Rules

- At least 3 options per ADR (never binary — always consider a third path)
- Pros and cons for every option (not just the chosen one)
- Rationale must reference the specific constraints of this project (multi-tenant, Starter/Pro tiers, existing patterns)
- Consequences must include both positive and negative implications
- Link to other ADRs where relevant

## Environment Notes

```text
- Backend base package: io.b2mash.b2b.b2bstrawman
- Entity pattern: @FilterDef + @Filter + TenantAware + TenantAwareEntityListener
- Repository pattern: JPQL findOneById() (not JPA findById — bypasses @Filter)
- Migration location: backend/src/main/resources/db/migration/{global,tenant}/
- ADR location: adr/ADR-{NNN}-{kebab-case}.md
- Shared schema: tenant_shared with tenant_id column + Hibernate @Filter + RLS
- Dedicated schema: tenant_<hash> with SET search_path
- Request context: ScopedValue via RequestScopes (not ThreadLocal)
- Permission pattern: ProjectAccessService.requireViewAccess() → ProjectAccessResult.canEdit()
- Exception pattern: Semantic exceptions from exception/ package → ProblemDetail responses
- Frontend: Next.js App Router, server actions in lib/actions/, Shadcn UI components
```
