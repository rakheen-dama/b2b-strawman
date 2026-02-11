---
name: phase
description: Orchestrate an entire development phase — runs each epic slice sequentially via subagents, reviews, merges, then advances to the next. Pass the phase number (e.g. /phase 4).
---

# Phase Orchestration Workflow

Run all remaining epic slices in a development phase, one at a time, using subagents for implementation. The orchestrator (you) stays lean — delegating all heavy work to background agents.

## Arguments

Phase number (e.g., `/phase 4`). Optionally append a starting slice: `/phase 4 from 39A`.

## Principles

1. **Context hygiene**: Your main context is precious. NEVER read large files, diffs, or codebases yourself. Delegate ALL research and implementation to subagents.
2. **One slice at a time**: Complete one slice fully (implement → review → fix → merge) before starting the next. Speed is not the objective — correctness is.
3. **Background agents**: Run implementation agents with `run_in_background: true`. Monitor via notifications. Only read output files if you need specifics.
4. **Minimal task tracking**: Create high-level tasks per slice (not per sub-task). Update as slices complete.
5. **No code writing**: You are the orchestrator. You approve, dispatch, and merge. You do not write code.

## Step 0 — Build the Execution Plan

1. Read the **TASKS.md overview table** (first ~55 lines only) to find the phase.
2. If the phase links to an external file (e.g., `tasks/phase4-customers-tasks-portal.md`), read that file's **Epic Overview table and dependency graph** (NOT the full task details — the subagent reads those).
3. Build an ordered list of slices, respecting dependencies and skip any marked **Done**.
4. Present the execution plan to the user and wait for approval.

## Step 1 — Execute Each Slice

For each slice in order:

### 1a. Dispatch Implementation Agent

Launch a `general-purpose` background agent with this prompt template:

```
You are implementing **Epic {SLICE}** end-to-end.

## Before you start
1. Read `backend/CLAUDE.md` and/or `frontend/CLAUDE.md` (based on scope)
2. Read the full epic detail from `{TASK_FILE}` — specifically tasks {TASK_IDS}
3. Study existing pattern files in the codebase (entities, controllers, tests from similar epics)

## Workflow
### Create worktree
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree add ../worktree-epic-{SLICE} -b epic-{SLICE}/{BRANCH_NAME}

### Implement
All work in /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}
Implement ONLY tasks {TASK_IDS}. Do NOT touch files outside scope.

### Build & verify
Backend: cd .../worktree-epic-{SLICE}/backend && ./mvnw spotless:apply && ./mvnw clean verify
Frontend: cd .../worktree-epic-{SLICE}/frontend && NODE_OPTIONS="" /opt/homebrew/bin/pnpm install && NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint && NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build

### Commit & push
git add <specific files>
git commit -m "feat(epic-{SLICE}): {DESCRIPTION}"
git push -u origin epic-{SLICE}/{BRANCH_NAME}

### Create PR
gh pr create --title "Epic {SLICE}: {TITLE}" --body "..."

Do NOT stop to ask questions. Read the task file and codebase patterns to resolve ambiguity.
```

### 1b. Wait for Completion

Wait for the background agent notification. Check the result summary.

### 1c. Code Review

Launch a `general-purpose` agent (NOT `code-reviewer` — it lacks Bash access):

```
Review PR #{PR_NUMBER} at /Users/rakheendama/Projects/2026/b2b-strawman.
Run: git fetch origin && gh pr diff {PR_NUMBER}
Read: backend/CLAUDE.md (and frontend/CLAUDE.md if applicable)
Check for: bugs, security issues, access control bypasses, tenant isolation leaks,
convention violations. Report ONLY high-confidence issues.
```

### 1d. Fix Review Issues (if any)

If the review finds issues, dispatch another `general-purpose` agent to fix them in the worktree, re-verify, commit, and push.

### 1e. Merge

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

### 1f. Advance

Move to the next slice. Repeat from 1a.

## Environment Notes (pass to all subagents)

```
- Postgres host: b2mash.local:5432
- LocalStack host: b2mash.local:4566
- pnpm: /opt/homebrew/bin/pnpm
- NODE_OPTIONS="" needed before pnpm commands
- SHELL=/bin/bash prefix for docker build
- Maven wrapper: ./mvnw from backend dir
- Use JPQL findOneById() not findById() (bypasses @Filter)
- Tests need planSyncService.syncPlan(orgId, "pro-plan") for Pro tier orgs
- Spring Security Test jwt() mock needs .authorities() set explicitly
- @FilterDef/@Filter for tenant isolation on all entities
- ScopedValue pattern (not ThreadLocal) — RequestScopes.TENANT_ID, MEMBER_ID, ORG_ROLE
- Hibernate @Filter NOT applied to @Modifying DELETE/UPDATE — add tenantId clause manually
- code-reviewer agent type lacks Bash — use general-purpose for reviews that need gh pr diff
```

## Anti-Patterns

- **Do NOT** read full task files or diffs in your own context — delegate to agents
- **Do NOT** implement multiple slices in parallel — one at a time, verify each
- **Do NOT** write code yourself — you are the orchestrator
- **Do NOT** skip code review — every PR gets reviewed before merge
- **Do NOT** merge without a green build — agents must verify before PR creation
- **Do NOT** forget to update status files after merge

## Recovery

If an agent fails or produces bad output:
1. Check the output file for error details
2. If worktree exists but is broken: `git worktree remove ../worktree-epic-{SLICE} --force`
3. Delete the branch: `git branch -D epic-{SLICE}/{BRANCH_NAME}`
4. Re-dispatch a fresh agent from scratch
