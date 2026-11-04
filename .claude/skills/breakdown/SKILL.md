---
name: breakdown
description: Break down a phase into epics, slices, and tasks from architecture docs. Usage: /breakdown <phase-number> [requirements-file]. Example: /breakdown 5 docs/phase5-spec.md
---

# Phase Breakdown Workflow

Turn architecture documentation into a detailed epic/slice/task breakdown, producing a phase task file and updating TASKS.md.

## Arguments

- **Required**: Phase number (e.g., `/breakdown 5`)
- **Optional**: Path to a requirements or spec file (e.g., `/breakdown 5 docs/phase5-requirements.md`)

If no requirements file is given, the skill looks for the phase section in `architecture/ARCHITECTURE.md`.

## Principles

1. **Architecture-driven**: Every epic traces back to a section in architecture/ARCHITECTURE.md or the requirements doc. No invented features.
2. **Agent-sized slices**: Each slice must be completable by a coding agent within ~60% of its context window — roughly 8–12 files touched, single scope (backend OR frontend, never both).
3. **Format consistency**: Output matches the exact format of existing phase task files (e.g., `tasks/phase4-customers-tasks-portal.md`).
4. **Delegate the heavy work**: Use a Plan agent for the actual breakdown. Keep your own context lean.

## Step 0 — Gather Inputs

1. Extract the phase number from the user's input.
2. Read `TASKS.md` (overview-only, ~76 lines) to determine:
   - The last epic number (so the new phase starts at N+1).
   - The existing phase naming pattern.
   - Which phases/epics are already Done.
3. If a requirements file was provided, read it. Otherwise:
   - Search `architecture/ARCHITECTURE.md` for a section matching "Phase {N}" (e.g., `## 10. Phase 5 — ...`).
   - If found, note the section number and any linked ADRs.
   - If not found, ask the user where the architecture for this phase lives.
4. Find any ADRs referenced by the architecture section:
   - Glob `adr/ADR-*.md` and identify those linked from the phase section.
5. Present a summary to the user:
   - Phase number, architecture source, ADR count, starting epic number.
   - Ask for confirmation before dispatching the Plan agent.

## Step 1 — Dispatch Plan Agent

Launch a **Plan** agent (subagent_type: `Plan`) with the following prompt template. Fill in the placeholders from Step 0.

```
You are a senior technical program manager creating an implementation plan for Phase {PHASE_NUMBER} of the DocTeams multi-tenant SaaS platform.

## Context to Read First (MANDATORY — read ALL before planning)

Architecture & requirements:
- {ARCHITECTURE_SOURCE} (e.g., "ARCHITECTURE.md — Section 10" or the requirements file path)
- {ADR_FILES} (list each ADR file path)

Existing patterns to match:
- `TASKS.md` — Read the overview table (~76 lines) for epic numbering, phase naming, and status conventions.
- The most recent phase task file (e.g., `tasks/phase5-task-time-lifecycle.md` or `tasks/phase4-customers-tasks-portal.md`) — Study 2-3 completed epics there for the FULL epic format (table structure, slice naming, task ID conventions, Key Files sections, Architecture Decisions sections). Your output MUST match this format exactly.
- `backend/CLAUDE.md` and `frontend/CLAUDE.md` — Conventions and anti-patterns to respect.

Reference implementations (study to calibrate slice sizes):
- Read ONE completed epic from the most recent phase task file — just its Tasks table and Key Files section (~50-80 lines). Don't read more than one; the format repeats.
- Do NOT read full backend/frontend source files. You are planning, not implementing.

Existing code to understand scope (LIGHTWEIGHT — just list files, don't read contents):
- `ls backend/src/main/java/io/b2mash/b2b/b2bstrawman/` — package structure only
- Find the next free migration number per series (tenant and global): `for s in tenant global; do echo "$s: $(ls backend/src/main/resources/db/migration/$s/ | sort -V | tail -1)"; done` — since the Phase 86 squash each series starts at `V1__baseline.sql` and new migrations are `V2+` (see `backend/db/BASELINE.md`)
- `ls frontend/app/(app)/org/[slug]/` — route structure only

## Task

Create the Phase {PHASE_NUMBER} epic breakdown. Follow these rules:

### Slice Sizing Rules (CRITICAL)
A slice is ONE pull request, built by one Builder agent from a pre-written brief. Builder context
windows are large; size slices by PR cost, not by layer (founder ruling 2026-11-04, Phase 89):
- Put the backend AND frontend/portal work for one gap or feature in the SAME slice when together
  they stay around 12-20 files (≤ ~1500 new lines). Do not split a single fix into a backend PR that
  ships an unread DTO field and a frontend PR that holds the only observable PASS.
- Every backend-touching PR costs a full `./mvnw verify` plus two reviews. Group same-epic items that
  share a file family so the number of PRs needing a backend verify is minimal; put frontend/portal/
  docs-only work in PRs that need no verify. Mark each slice "verify: yes/no".
- Multi-gap or multi-feature slices need the founder's explicit authorisation (root CLAUDE.md §7).
  Propose the grouping and the verify count per epic in the breakdown summary and ask; never silently
  over-split to avoid asking.
- Integration tests belong in the SAME slice as the code they test; the slice that observes the
  behaviour in the browser also removes the spec's fixme.
- Upper bounds still apply: a slice that needs >25 files touched or >300 files in the PR diff (merge
  hook limit) is too big — split by file family, not by layer.
- Large, genuinely separable backend work (a new service + listener chain, a new event family) may
  still be its own slice when its frontend consumer is independently observable.

### Epic Structure Rules
- Number epics starting from {STARTING_EPIC_NUMBER}
- Use the exact table format from existing epics in TASKS.md
- Each epic gets: Goal, Dependencies, Estimated Effort (S/M/L), Status (empty), Tasks table
- Slice naming: {STARTING_EPIC_NUMBER}A, {STARTING_EPIC_NUMBER}B, etc.
- Task IDs: {STARTING_EPIC_NUMBER}.1, {STARTING_EPIC_NUMBER}.2, etc.
- Include a "Notes" column with specific file paths and patterns to follow
- Add dependency arrows between epics

### Ordering Principles
- Database migrations FIRST within each domain
- Backend before frontend (frontend depends on API)
- Group by domain, not by layer
- Independent domains can run in parallel — document this

### What Each Task Row Must Include
- Specific files to create or modify (in the Notes column)
- Which existing file to use as a pattern/reference
- Test expectations (number of tests, what they cover)
- Migration details where relevant (table name, key columns, constraints)

### Output Format
Return the FULL markdown content. Start with:

# Phase {PHASE_NUMBER} — {PHASE_TITLE}

{Brief description}

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
...

Then each epic in full detail. Include:
- Epic Overview table
- Dependency Graph (ASCII art)
- Implementation Order table with stages
- Full epic sections with Slices table, Tasks table, Key Files, Architecture Decisions

Do NOT implement any code. Only produce the task breakdown document.

IMPORTANT: Return the FULL markdown content. Do not truncate or summarize.
```

## Step 2 — Create the Task File

Once the Plan agent returns:

1. Determine the output filename: `tasks/phase{N}-{kebab-case-title}.md`
   - Derive the title from the architecture section heading or the requirements doc.
   - Example: `tasks/phase5-notifications-and-activity.md`
2. Write the Plan agent's output to the task file using the Write tool.
3. Verify the file was created and has reasonable content (check line count, epic count).

## Step 3 — Update TASKS.md

1. Read the TASKS.md overview table again.
2. Append new rows after the last existing epic:
   - A phase header row: `| **Phase {N} — {Title}** | | | | | | See [tasks/{filename}](tasks/{filename}) |`
   - One row per epic from the new task file (epic number, name, scope, deps, effort, slices, empty status).
3. Use the Edit tool to insert the rows at the correct position (before the `---` separator after the last overview row).

## Step 4 — Present Summary

Show the user:
- Number of epics created
- Number of slices total
- Parallel tracks (which epics can run concurrently)
- File locations: task file path and TASKS.md link
- Any open questions or ambiguities discovered during breakdown

## Error Handling

- **No architecture section found**: Ask the user for the source document path.
- **ADRs referenced but missing**: Warn the user; proceed with available context.
- **Plan agent output too short**: Re-dispatch with a more explicit prompt asking for full detail.
- **Epic numbering conflict**: If the starting epic number already exists in TASKS.md, increment until a free number is found.
