---
name: phase
description: Orchestrate an entire development phase — runs each epic slice sequentially via subagents, reviews, merges, then advances to the next. Pass the phase number (e.g. /phase 4).
---

# Phase Orchestration Workflow

Run all remaining epic slices in a development phase, one at a time, using a **Scout → Builder pipeline** per slice. The orchestrator (you) stays lean — delegating ALL heavy work to subagents.

## Arguments

Phase number (e.g., `/phase 4`). Optionally append a starting slice: `/phase 4 from 39A`.

## IMPORTANT: Prefer /phase_v2

If `scripts/run-phase.sh` exists, use `/phase_v2` instead. Run it in a **terminal tab** (not inside a Claude session):

```bash
./scripts/run-phase.sh <phase-number> [starting-slice]
```

The v2 approach gives each slice a fresh context window and sends macOS notifications on completion/failure. This v1 skill is retained for cases where v2 isn't set up or you need interactive merge approval.

## Principles

1. **Context hygiene**: Your main context is precious. NEVER read large files, diffs, or codebases yourself. Delegate ALL research and implementation to subagents.
2. **Scout → Builder split**: Each slice gets TWO subagents — a scout (research, produces brief) and a builder (reads only the brief, implements). This prevents the builder from burning 50%+ context on file reads.
3. **One slice at a time**: Complete one slice fully (scout → build → review → fix → merge) before starting the next.
4. **Blocking agents**: Run ALL subagents as **blocking** Task calls (do NOT use `run_in_background`). Background agents cause the orchestrator to freeze — no reliable resume mechanism.
5. **Minimal task tracking**: Create high-level tasks per slice (not per sub-task). Update as slices complete.
6. **No code writing**: You are the orchestrator. You approve, dispatch, and merge. You do not write code.
7. **Never launch `run-phase.sh` in the background** — if you do, you'll end up polling it with `sleep`+`ps` loops, wasting your entire context window on monitoring. Either use blocking Task calls (this skill) or run the script in a terminal (v2).

### Model Allocation

| Agent | Model | Rationale |
|-------|-------|-----------|
| Scout | **opus** | Information gathering + brief formatting — constrained by template |
| Builder | **opus** | Pattern-following implementation — constrained by brief |
| Reviewer | **opus** | Quality gate — catches subtle issues in tenant isolation, security, conventions |
| Fixer | **opus** | Must evaluate findings critically — skip false positives, not just blindly apply |

Always pass the `model` parameter on each Task call.

## Step 0 — Build the Execution Plan

1. Read `TASKS.md` to find the phase and its epic rows (overview-only file, safe to read in full).
2. The phase header row contains a `See [tasks/phase{N}-*.md]` link. Read ONLY that file's **Epic Overview table and dependency graph** — use `Read(file, limit=60)`. The subagent reads the full task details, NOT you.
3. Build an ordered list of slices, respecting dependencies and skip any marked **Done**.
4. Present the execution plan to the user and wait for approval.

## Step 1 — Execute Each Slice

For each slice in order:

### 1a. Clean Up Stale State (if any)

Before dispatching, check for leftover worktrees/branches from a previous failed attempt:

```bash
git worktree list | grep "worktree-epic-{SLICE}" && git worktree remove ../worktree-epic-{SLICE} --force
git branch -D epic-{SLICE}/{BRANCH_NAME} 2>/dev/null || true
```

### 1b. Create Worktree

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree add ../worktree-epic-{SLICE} -b epic-{SLICE}/{BRANCH_NAME}
```

Create before dispatching agents so the scout can write the brief into it.

### 1c. Dispatch Scout Agent

Launch a **blocking** `general-purpose` agent with `model: "sonnet"`:

```
You are a **codebase scout** preparing an implementation brief for Epic {SLICE}.

Your job: explore the codebase thoroughly and write a SELF-CONTAINED brief to:
  /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/.epic-brief.md

The brief must give an implementer EVERYTHING they need to build this epic correctly
WITHOUT reading any other files. Include actual code — not summaries.

## Research Steps (in this order)

### 1. Task Specifications
Read `{TASK_FILE}` — extract FULL task descriptions and acceptance criteria for tasks {TASK_IDS}.
Include exact field names, SQL schemas, API endpoints, and test scenarios from the spec.

### 2. Conventions & Anti-Patterns
Read `backend/CLAUDE.md` and/or `frontend/CLAUDE.md` (scope: {SCOPE}).
Extract ALL conventions and the COMPLETE anti-patterns section verbatim. These prevent
debugging spirals — missing even one can cost hours.

### 3. Architecture Context
Search ARCHITECTURE.md for sections relevant to this epic (grep for keywords, don't read
the full 2400-line file). Extract relevant ADRs (check `adr/` directory too).
Include only what directly impacts this epic's implementation decisions.

### 4. Reference Patterns (CRITICAL)
Find the most similar RECENTLY IMPLEMENTED feature and extract ONE complete example of each:

**Backend** (if in scope):
- Entity (with @FilterDef, @Filter, tenant awareness, constructors)
- Repository interface (with custom JPQL queries like findOneById)
- Service class (with @Transactional, ScopedValue access, validation)
- Controller (with @PreAuthorize, DTO records, response patterns)
- Integration test (with FULL @BeforeAll setup: provisionTenant, planSyncService, MockMvc config)
- Flyway migration SQL (naming convention, tenant_id columns, indexes)

**Frontend** (if in scope):
- Server component (data fetching, permission checks)
- Client component ("use client", form handling, Shadcn UI)
- Server action (revalidation, error handling)
- Test file (with afterEach cleanup, mock patterns, render helpers)

Prefer the MOST RECENTLY modified examples (check git log if needed).
Include the FULL source code of each pattern — not excerpts or summaries.
Prefix each with its file path so the implementer knows the naming convention.

### 5. Integration Points
Identify existing services, entities, repositories, and API endpoints the new code must
interact with. Include their key method signatures and class locations.

### 6. File Structure
Determine exact file paths for all new files, following existing package/directory conventions.

## Brief Format

Write the brief to `/Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/.epic-brief.md`
using this exact structure:

---
# Implementation Brief: Epic {SLICE} — {TITLE}

## Scope
{Backend | Frontend | Both}

## Tasks
{Numbered list with FULL descriptions and acceptance criteria from the task file}

## File Plan
### Create
{Exact paths with one-line purpose}
### Modify
{Exact paths with what to change}

## Reference Patterns
### {Pattern Type} (from {source file path})
```{lang}
{FULL source code — not excerpts}
```
{Repeat for each pattern type}

## Conventions
{ALL relevant rules from CLAUDE.md — include anti-patterns VERBATIM}

## Integration Points
{Classes and method signatures the new code calls or extends}

## Migration Notes
{Schema, table structure, naming convention — if applicable}

## Build & Verify
{Exact commands — see below}

## Environment
- Postgres host: b2mash.local:5432
- LocalStack host: b2mash.local:4566
- pnpm: /opt/homebrew/bin/pnpm
- NODE_OPTIONS="" needed before pnpm commands
- SHELL=/bin/bash prefix for docker build
- Maven wrapper: ./mvnw from backend dir
---

## Build Commands (include these exactly in the brief)

### Backend — Two-Pass Strategy (minimizes context usage)

All commands run from: cd /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/backend

**Step 1: Format**
  ./mvnw spotless:apply 2>&1 | tail -3

**Step 2: Silent full build (Pass 1)**
  ./mvnw clean verify -q > /tmp/mvn-epic-{SLICE}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "BUILD SUCCESS"; grep 'Tests run:' /tmp/mvn-epic-{SLICE}.log | tail -1; else echo "BUILD FAILED (exit $MVN_EXIT)"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); if [ -n "$FAILED" ]; then echo "FAILED TESTS: $FAILED"; else grep -E '\[ERROR\]' /tmp/mvn-epic-{SLICE}.log | head -20; fi; fi

**Step 3: Re-run ONLY failed tests with full output (only if Pass 1 failed with test failures)**
  ./mvnw verify -Dit.test="{FAILED_CLASSES}" -Dtest="{FAILED_CLASSES}" 2>&1 | tail -80

  (Replace {FAILED_CLASSES} with the comma-separated list from Step 2's FAILED TESTS output)
  This gives the agent full stack traces for ONLY the failing tests — typically 50-80 lines
  instead of 500-1100 for the full suite.

**Step 4: Silent re-verify after fixing (Pass 2)**
  ./mvnw clean verify -q > /tmp/mvn-epic-{SLICE}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "BUILD SUCCESS"; grep 'Tests run:' /tmp/mvn-epic-{SLICE}.log | tail -1; else echo "STILL FAILING"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); echo "FAILED: $FAILED"; fi

IMPORTANT: NEVER run ./mvnw clean verify without -q — full output burns 30-60KB of context per run.
If you need to debug a compilation error, read the log file with grep:
  grep -n 'ERROR\|cannot find symbol\|Caused by' /tmp/mvn-epic-{SLICE}.log | head -30

### Frontend

All commands run from: cd /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/frontend

  NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/lint-epic-{SLICE}.log 2>&1; LINT_EXIT=$?; if [ $LINT_EXIT -ne 0 ]; then echo "LINT FAILED"; tail -20 /tmp/lint-epic-{SLICE}.log; fi
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build > /tmp/build-epic-{SLICE}.log 2>&1; BUILD_EXIT=$?; if [ $BUILD_EXIT -ne 0 ]; then echo "BUILD FAILED"; tail -30 /tmp/build-epic-{SLICE}.log; fi
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm test > /tmp/test-epic-{SLICE}.log 2>&1; TEST_EXIT=$?; if [ $TEST_EXIT -ne 0 ]; then echo "TESTS FAILED"; tail -30 /tmp/test-epic-{SLICE}.log; else echo "ALL TESTS PASSED"; tail -3 /tmp/test-epic-{SLICE}.log; fi

IMPORTANT: Include FULL code for reference patterns. The implementer's ONLY reference
material is this brief. Be generous with code, strict with structure.

When finished, confirm: "Brief written to {path}" and list the section sizes (line counts).
```

### 1d. Dispatch Builder Agent

Verify the brief file was written (check scout output), then launch a **blocking** `general-purpose` agent with `model: "sonnet"`:

```
You are implementing **Epic {SLICE}** in the worktree at:
  /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}

## First Step — Read Your Brief
Read: /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/.epic-brief.md
This file contains EVERYTHING you need: tasks, file plan, code patterns, conventions,
build commands, and integration points. Do NOT read ARCHITECTURE.md, TASKS.md, or
CLAUDE.md files — the brief already contains the relevant extracts.

## Workflow

### 1. Implement
- Follow the File Plan from the brief exactly
- Adapt Reference Patterns to the new feature (don't copy-paste variable names blindly)
- Respect ALL Conventions and Anti-Patterns from the brief
- Implement ONLY the tasks in the brief — nothing more
- If the brief mentions files to modify, read those specific files before editing

### 2. Build & Verify
Run the exact build commands from the brief's "Build & Verify" section.
Build output is redirected to log files — only summaries enter your context.
If the build fails:
  1. Read the relevant log file to understand the error
  2. Fix the root cause (not symptoms)
  3. Re-run. Max 3 fix cycles.
  4. If still failing after 3 cycles, stop and report what's failing and your hypotheses.

When reading log files for errors, use targeted reads:
  grep -n "ERROR\|FAILURE\|Caused by" /tmp/mvn-epic-{SLICE}.log | tail -20
  NOT: cat /tmp/mvn-epic-{SLICE}.log (this defeats the purpose of output redirection)

### 3. Commit & Push
- Stage only files you changed: `git add <specific files>`
- Commit: `git commit -m "feat(epic-{SLICE}): {DESCRIPTION}"`
- Push: `git push -u origin epic-{SLICE}/{BRANCH_NAME}`

### 4. Create PR
gh pr create --title "Epic {SLICE}: {TITLE}" --body "$(cat <<'EOF'
## Summary
{What this epic implements — from the brief's Tasks section}

## Changes
{Bulleted list of key files/components added or modified}

## Tasks Completed
{Checklist of task IDs from the brief}

## Test plan
{Build commands and manual verification steps}

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

### 5. Report Back
When done, report: PR number/URL, files created/modified count, test results, any issues.

Do NOT stop to ask questions. Use the brief to resolve ambiguity.
```

### 1e. Process Result

Extract the PR number from the builder's response. If the agent failed, go to Recovery.

### 1f. Code Review

Write the diff to a file, then dispatch review:

```bash
gh pr diff {PR_NUMBER} > /tmp/pr-{PR_NUMBER}.diff
```

Launch a **blocking** `general-purpose` agent with `model: "opus"` (quality gate — do NOT downgrade):

```
You are reviewing PR #{PR_NUMBER} for the DocTeams multi-tenant SaaS platform.

## Setup
1. Read the diff: /tmp/pr-{PR_NUMBER}.diff
2. Read conventions: backend/CLAUDE.md {and/or frontend/CLAUDE.md based on scope}

## What to Check

### Critical (blocks merge)
- **Tenant isolation**: Missing @FilterDef/@Filter on new entities, using findById() instead
  of findOneById() (bypasses @Filter), missing tenant_id, missing RLS for shared-schema
- **Security**: Missing @PreAuthorize, SQL injection (never string concat in native queries,
  use set_config for session vars), exposed internal endpoints
- **Data corruption**: Missing @Transactional, race conditions, incorrect cascade types

### High (should fix)
- **Convention violations**: Anti-patterns from CLAUDE.md — ThreadLocal instead of ScopedValue,
  OSIV issues, wrong exception patterns, missing bean validation
- **Test gaps**: New endpoints without tests, missing tenant isolation assertions,
  tests without provisionTenant/planSyncService setup
- **Frontend/backend parity**: Permission logic doesn't match across layers

### Medium
- Dead code, duplicated logic, missing error handling at system boundaries

## Output Format
# Review: PR #{PR_NUMBER}
## Verdict: {APPROVE | REQUEST_CHANGES}
## Critical / High / Medium
- **[file:line]** Issue → Fix: suggestion

Only report issues you're >80% confident about.
```

### 1g. Fix Review Issues (if any)

If the review finds critical issues, dispatch another `general-purpose` agent with `model: "opus"` to fix them in the worktree. The fixer must evaluate each finding critically — skip false positives and explain why, don't blindly apply everything. Pass the review findings AND the brief file path (`/Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/.epic-brief.md`) so the fixer has full context for conventions.

### 1h. Merge

After review passes, merge using this exact sequence:

```bash
# Merge PR
gh pr merge {PR_NUMBER} --squash --delete-branch

# Clean up worktree
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../worktree-epic-{SLICE} --force
git pull origin main
git fetch --prune
```

Then update task status:
- In the phase task file (e.g., `tasks/phase4-customers-tasks-portal.md`) — mark slice Done
- In `TASKS.md` overview row — update status
- Commit and push status update from main repo

### 1i. Advance

Move to the next slice. Repeat from 1a.

## Anti-Patterns

- **Do NOT** use `run_in_background: true` for Task calls — the orchestrator freezes with no reliable resume
- **Do NOT** read phase task files in full in your own context — use `limit=60` for overview, delegate full reads to scouts
- **Do NOT** implement multiple slices in parallel — one at a time, verify each
- **Do NOT** write code yourself — you are the orchestrator
- **Do NOT** skip code review — every PR gets reviewed before merge
- **Do NOT** merge without a green build — builder must verify before PR creation
- **Do NOT** forget to update status files after merge
- **Do NOT** dispatch a single agent that does both research AND implementation — always split into scout + builder

## Recovery

If an agent fails or produces bad output:
1. Check the output for error details
2. If the scout produced a bad brief: re-dispatch scout with additional guidance
3. If the builder failed with a good brief: dispatch a new builder with the same brief + error context
4. If worktree is broken: `git worktree remove ../worktree-epic-{SLICE} --force`
5. Delete the branch: `git branch -D epic-{SLICE}/{BRANCH_NAME} 2>/dev/null`
6. Re-start the slice from 1b (create worktree)
