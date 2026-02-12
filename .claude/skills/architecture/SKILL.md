---
name: architecture
description: Produce a self-contained architecture section and ADRs from a requirements prompt file. Usage: /architecture <requirements-file>. Example: /architecture claude-code-prompt-phase5.md
---

# Architecture Document Generation Workflow

Read a requirements/prompt file and produce a comprehensive architecture section (ready to merge into `ARCHITECTURE.md`) plus standalone ADR files in `adr/`.

## Arguments

- **Required**: Path to a requirements file (e.g., `/architecture docs/phase6-spec.md`)

The requirements file should describe what to design — domain model, flows, API surface, constraints, and what ADRs to produce.

## Principles

1. **Existing-architecture-first**: Before writing anything, deeply understand the current system — entities, conventions, ADR numbering, section numbering, migration numbering, coding patterns. New output must feel like a natural continuation.
2. **Delegate research**: Use subagents for codebase exploration. Keep the main context focused on writing.
3. **Self-contained output**: The main document should be mergeable into `ARCHITECTURE.md` as-is. ADRs should be standalone files following the established format.
4. **No code implementation**: Produce only architecture documents, ADRs, and implementation guidance. Never write Java, TypeScript, or SQL implementation files.
5. **Engineer-ready**: An engineer (or the `/breakdown` skill) should be able to derive epics, slices, and tasks from the output without re-asking requirements.

## Step 0 — Gather Context

Read the requirements file provided by the user. Then launch a **general-purpose** agent to gather codebase context and write it to a file (keeps the orchestrator's context lean):

```
Explore the current state of the codebase and write a context inventory to:
  /Users/rakheendama/Projects/2026/b2b-strawman/.arch-context.md

I need:

1. ARCHITECTURE.md — Read ONLY the section headers (grep for "^## ") and identify:
   - The last section number (e.g., "## 10. Phase 4 — ...")
   - The next available section number
2. ADR numbering — List all ADR files in adr/ and find the highest ADR number
3. Migration numbering — List Flyway migrations (global and tenant) and find the highest VN number
4. Entity inventory — List all @Entity classes in the backend (class name + file path only)
5. Backend package structure — List packages under src/main/java/io/b2mash/b2b/b2bstrawman/
6. Frontend route structure — List pages under frontend/app/(app)/org/[slug]/
7. Existing ER patterns — Read one recent entity (e.g., Task or TimeEntry) and include its
   FULL source as a reference pattern for the architect
8. Recent ADR format — Read the most recent ADR file and include it as a format template

Write all findings to the context inventory file. Format as structured markdown.
Do NOT read ARCHITECTURE.md in full (2400+ lines) — only grep section headers.

Working directory: /Users/rakheendama/Projects/2026/b2b-strawman
```

After the agent finishes, read only the summary sections of `.arch-context.md` (numbering, entity list) to present to the user. The full file will be read by the writing agent in Step 1.

Present a summary to the user:
- Next section number, next ADR number, next migration number
- Entities that exist and are relevant to the requirements
- Ask for confirmation before proceeding

## Step 1 — Produce the Architecture Document

Launch a **general-purpose** agent to write the architecture doc. Pass it:
- The requirements file path
- The context inventory: `/Users/rakheendama/Projects/2026/b2b-strawman/.arch-context.md`

The writing agent reads these two files and produces the architecture document. This keeps the orchestrator from holding the full architecture + requirements in its own context.

The output file goes to the project root: `{kebab-case-title}.md` (e.g., `phase6-notifications.md`).

### Required Sections (adapt headings to content)

The document MUST include all of these, adapted to whatever the requirements ask for:

#### Section N.1 — Overview
- 2-3 paragraph summary of what this phase adds
- "What's new" table comparing existing vs new capabilities

#### Section N.2 — Domain Model
- New entities with full field tables (field, type, constraints, notes)
- Design decisions for each entity (inline, not separate)
- Updated Mermaid **ER diagram** showing ALL tenant-schema entities (existing + new)
- Call out what's unchanged from prior phases

#### Section N.3 — Core Flows and Backend Behaviour
- One subsection per major flow (as described in requirements)
- SQL query examples for non-trivial queries
- Service-layer method signatures (conceptual Java)
- Tenant boundary explanation (how it works for Starter + Pro)
- RBAC / permission rules per operation
- Paging/filtering considerations

#### Section N.4 — API Surface
- Tables with: Method, Path, Description, Auth, Read/Write
- Request/response JSON shapes for key endpoints
- Query parameter documentation
- Group by capability area (not by HTTP method)

#### Section N.5 — Sequence Diagrams
- At least 2 Mermaid **sequence diagrams** for key flows
- Include: Actor → Browser → Next.js → Spring Boot → DB
- Show error/conflict paths where relevant (e.g., race conditions)

#### Section N.6 — Additional Sections (as needed)
- Portal seams, security considerations, S3 key structure, etc.
- Whatever the requirements call for that doesn't fit above

#### Section N.7 — Database Migrations
- Migration number, description, full SQL
- Index definitions with rationale
- RLS policies for shared-schema support
- Backfill strategy if modifying existing tables
- Note any prerequisite indexes from prior migrations

#### Section N.8 — Implementation Guidance
- Backend changes table (file → change)
- Frontend changes table (file → change)
- Entity code pattern (annotated Java example)
- Repository code pattern (JPQL example)
- Testing strategy table (test name → scope)

#### Section N.9 — Permission Model Summary
- Access control tables per entity/operation
- Role hierarchy: Owner > Admin > Project Lead > Contributor

#### Section N.10 — Capability Slices
- 3-6 independently deployable slices
- Each with: scope (backend/frontend), key deliverables, dependencies, test expectations
- Designed for the `/breakdown` skill to turn into full epics

#### Section N.11 — ADR Index
- Table linking to all ADRs (existing referenced + new)

### Writing Style Rules

- Match the voice and formatting of existing ARCHITECTURE.md sections
- Use `code blocks` for SQL, Java, JSON, and file paths
- Use Mermaid for all diagrams (ER, sequence, flowchart)
- Reference existing ADRs by number and link: `[ADR-019](adr/ADR-019-task-claim-workflow.md)`
- Include a merge instruction at the top: `> Merge into ARCHITECTURE.md as **Section N**.`
- Every design decision should state *why*, not just *what*
- Explicitly call out what's out of scope

## Step 2 — Produce ADR Files

For each ADR required by the requirements:

1. Create `adr/ADR-{NNN}-{kebab-case-title}.md`
2. Follow the established ADR format exactly:

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

## Step 3 — Quality Review

Launch a **code-reviewer** agent to verify:

```
Review the architecture document at {MAIN_DOC_PATH} and ADR files at adr/ADR-{NNN}*.md for:

1. Internal consistency — cross-references, entity names, field names, ADR numbers, section numbers
2. Completeness — all sections present, all requirements covered
3. Compatibility — follows existing entity patterns, correct migration/ADR numbering, permission model alignment
4. Implementability — can an engineer derive epics from this?

List issues if found. Be concise.
```

Fix any issues found by the reviewer.

## Step 4 — Present Summary

Show the user:
- Output file path (main document)
- ADR files created (with titles)
- Section number for ARCHITECTURE.md merge
- Migration number(s)
- Capability slice count
- Any open questions or assumptions made

## Error Handling

- **Requirements file not found**: Ask the user for the correct path.
- **Requirements too vague**: Ask clarifying questions before proceeding. Use AskUserQuestion for structured choices.
- **ADR numbering conflict**: If an ADR number already exists, increment until free.
- **Section numbering conflict**: Same approach — find the next free section number.
- **Review finds critical issues**: Fix and re-review. Do not present output with known issues.

## Environment Notes (for subagents)

```
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
