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
2. **Slices sized by cost and risk, not file count**: one slice = one PR with ~90 min of fixed overhead. Split only where it lowers risk, improves review, or lets work land independently (see Slice Sizing Rules). File counts are estimates you record, not bounds you cut to.
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
- Large, genuinely separable backend work (a new service + listener chain, a new event family) may
  still be its own slice when its frontend consumer is independently observable.

### Split Rule (Phase 92 lesson)
Phase 92's breakdown re-cut 21 → 35 slices (13 → 25 certain backend verifies) mainly to meet a 25-file
bound; real PRs then landed at 22–87 code files anyway and the founder dropped the bound. The 14 extra
slices cost ≈ 15–20 h of fixed overhead.
- Split a slice ONLY when the split lowers risk, improves review quality, or lets work land
  independently. Never solely to meet a file count.
- Every split states its added fixed cost: "+1 verify, +~90 min" (or "+~70 min" for a no-verify slice).
- A file count is a guideline: record the expected count in the slice row; do not re-cut to hit it.
  Flag a slice estimated above ~60 code files for a founder look, with the cost of the split you are
  NOT proposing.
- Hard limit (tooling, not policy): `gh pr diff` fails past ~300 files, which fails the merge gate.

### Size Estimation — count the test and frontend fan-out (Phase 92 lesson)
646A was estimated at ≈ 48 files and landed at 87. The miss was almost entirely tests — fixtures that
write rows directly, tests asserting removed fields/methods, mocks of removed repository methods — and
frontend type fallout. Before estimating any slice that changes a schema, entity, DTO or repository
method, grep the fan-out and report **main / test / frontend** counts separately (e.g. "≈ 31 main ·
40 test · 6 FE"). Example, for removing `Invoice.legacyTotal` (column `legacy_total`) and
`InvoiceRepository.sumLegacyTotals`:

~~~bash
T=backend/src/test
# tests that construct or write the entity directly (factories, builders, native SQL fixtures)
grep -rlE 'new Invoice\(|InvoiceTestFactory|Invoice\.builder\(|INSERT INTO invoices' $T | wc -l
# tests asserting the removed field (getter, JSON path, column name)
grep -rlE 'getLegacyTotal\(|legacyTotal|legacy_total' $T | wc -l
# mocks / stubs / verifications of the removed repository method
grep -rlE '(when|given|verify)\(.*sumLegacyTotals\(' $T | wc -l
# frontend + portal types, fixtures, vitest and e2e that read the field
grep -rlE 'legacyTotal|legacy_total' frontend portal packages --include='*.ts' --include='*.tsx' --exclude-dir=node_modules | wc -l
~~~

Union the file lists (a file hit by two patterns counts once) and add the main-code files.

### Wall-clock Cost Model
Count verifies AND estimate wall-clock. Phase 92 measured per-slice fixed overhead: scout ~15 min,
review loops ~30, OBS/E2E gates ~12, full backend verify ~22, CI wait + merge + bookkeeping ~15 —
**≈ 90 min per slice** (≈ 70 without a backend verify), on top of build effort (~20 min for a small
slice, more for large ones).
- Estimated phase duration = slices × ~90 min fixed overhead + Σ per-slice build effort. State it in
  the task file and the summary.
- **Flag any phase above ~40 h** and propose the merges that would bring it down (with the risk each
  merge adds).

### Open Questions
Every open question in the task file carries a status in its own column:
- `RULED` — decided; cite who and when (e.g. "founder 2026-11-16").
- `DEFAULT-ACCEPTED` — the proposed default was explicitly accepted (cite who and when).
- `OPEN — blocks <slice ids>` — anything else, including "at proposed default, unruled".
Carry every unruled item of the architecture's Founder decisions table and every Cross-cutting
decision DEFERRED to a slice in as an open question with its status. A pending slice must not start
while an `OPEN — blocks` item names it.

### Epic Structure Rules
- Number epics starting from {STARTING_EPIC_NUMBER}
- Use the exact table format from existing epics in TASKS.md
- Each epic gets: Goal, Dependencies, Estimated Effort (S/M/L), Status (empty), Tasks table
- Slice naming: {STARTING_EPIC_NUMBER}A, {STARTING_EPIC_NUMBER}B, etc. Letter slices in build order;
  when re-cutting, re-letter so letters still follow build order (don't append 646E after 646B if it
  builds between 646A and 646B) — unless a slice has already shipped under its letter.
- Task IDs: {STARTING_EPIC_NUMBER}.1, {STARTING_EPIC_NUMBER}.2, etc.
- Include a "Notes" column with specific file paths and patterns to follow
- Add dependency arrows between epics

### Ordering Principles
- Database migrations FIRST within each domain
- Backend before frontend (frontend depends on API)
- Group by domain, not by layer
- Independent domains can run in parallel — document this

### Machine-readable Run Order (MANDATORY)
`scripts/run-phase.sh` never parses the prose Implementation Order table. The task file MUST contain
exactly ONE line, directly under `## Implementation Order`:

<!-- run-order: 645A 649A 646A 649B 646B ... -->

listing EVERY slice id (conditional slices included) in build order — stage order, serial. The
runner executes exactly this list (Done slices skipped), refuses unknown or duplicate ids, and warns
about slice rows left out. It must agree with the Implementation Order table. (Phase 92: with no such
line, the runner used row order, ran 646A before 649A, and halted the phase.)

### What Each Task Row Must Include
- Size estimate as main / test / frontend counts (see Size Estimation) and "verify: yes/no"
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
- Open questions table with a Status column (RULED / DEFAULT-ACCEPTED / OPEN — blocks <slice ids>)
- Epic Overview table
- Cost estimate: slices, certain + conditional backend verifies, estimated duration (cost model);
  flag if above ~40 h
- Dependency Graph (ASCII art)
- Implementation Order table with stages, and directly under its heading the single
  `<!-- run-order: ... -->` line
- Full epic sections with Slices table, Tasks table, Key Files, Architecture Decisions
- If you split any architecture slice: a "Changes from §N.x" table with the reason and the added
  fixed cost (+verify, +~90 min) of every split

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
4. Run `./scripts/run-phase.sh {N} --dry-run`: it must report `Order source: run-order line`, no
   missing-slice warning, and the same order as the Implementation Order table.

## Step 3 — Update TASKS.md

1. Read the TASKS.md overview table again.
2. Append new rows after the last existing epic:
   - A phase header row: `| **Phase {N} — {Title}** | | | | | | See [tasks/{filename}](tasks/{filename}) |`
   - One row per epic from the new task file (epic number, name, scope, deps, effort, slices, empty status).
3. Use the Edit tool to insert the rows at the correct position (before the `---` separator after the last overview row).

## Step 4 — Present Summary

Show the user:
- Number of epics created
- Number of slices total, certain + conditional backend verifies, and the estimated duration
  (flagged if above ~40 h)
- Parallel tracks (which epics can run concurrently)
- File locations: task file path and TASKS.md link
- Open questions grouped by status — list every `OPEN — blocks` item with the slices it blocks and
  ask for rulings before `/phase_v2` starts

## Error Handling

- **No architecture section found**: Ask the user for the source document path.
- **ADRs referenced but missing**: Warn the user; proceed with available context.
- **Plan agent output too short**: Re-dispatch with a more explicit prompt asking for full detail.
- **Epic numbering conflict**: If the starting epic number already exists in TASKS.md, increment until a free number is found.
