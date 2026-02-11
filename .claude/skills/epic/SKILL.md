---
name: epic
description: Implement a specific epic end-to-end — worktree, code, test, PR, review, merge. Pass the epic number as an argument (e.g. /epic 7).
---

# Epic Implementation Workflow

Implement the specified epic number end-to-end using git worktrees.

## Arguments

This skill requires an epic number (e.g., `/epic 7` or `/epic 5A` for slices).

## Task Tracking

Use Claude Code task tools throughout this workflow to give the user visibility into progress:

1. **At the start** (after Step 0): Create one task per epic task from TASKS.md (e.g., "Implement task 7.1: Create Project entity"). Use the task ID from TASKS.md as a prefix in the subject.
2. **As you work**: Mark each task `in_progress` before starting it, `completed` when done.
3. **Add workflow tasks** for non-code steps: "Create worktree & branch", "Build & verify", "Create PR", "Code review", "Merge".
4. **If blocked**: Keep the task `in_progress` and create a new task describing the blocker.

This gives the user a live progress view of the epic implementation.

## Step 0 — Validate & Gather Context

1. Extract the epic number from the user's input.
2. Find the epic definition:
   - Read the Epic Overview table in `TASKS.md` to identify the epic and check for a phase-specific task file link (e.g., `tasks/phase4-customers-tasks-portal.md`).
   - If the overview row links to an external file, read that file for the full epic detail.
   - Otherwise, locate the epic section within `TASKS.md` itself.
   - If the epic is marked **Done**, stop and inform the user.
3. Identify:
   - **Scope**: Frontend, Backend, Both, or Infra (from the Epic Overview table)
   - **Dependencies**: Which epics must be complete first — verify they are marked Done
   - **Tasks**: The specific task table for this epic — these define the ONLY work to do
4. Run `git branch -a` and `gh pr list --state all` to check for existing branches/PRs for this epic. If work already exists, inform the user and ask how to proceed.

## Step 1 — Plan

Before writing any code, present a concise implementation plan:
- List every file you expect to create or modify
- Note which tasks from TASKS.md each file addresses
- Identify anything out of scope — call it out explicitly
- Wait for user approval before proceeding

## Step 2 — Create Worktree & Branch

```bash
# Branch naming convention: epic-N/descriptive-name (kebab-case)
# Examples: epic-7/core-api-projects, epic-5A/tenant-provisioning-schema
git worktree add ../worktree-epic-<N> -b epic-<N>/<descriptive-name>
```

All implementation work happens inside the worktree directory.

## Step 3 — Read Subdirectory CLAUDE.md

Before writing code, read the relevant CLAUDE.md:
- **Backend scope** → `backend/CLAUDE.md`
- **Frontend scope** → `frontend/CLAUDE.md`
- **Both** → read both
- **Infra** → `infra/CLAUDE.md` (if it exists)

Follow all conventions, patterns, and anti-patterns described there.

## Step 4 — Implement

- Implement ONLY the tasks listed in the epic's task table in TASKS.md
- Do NOT touch files outside the epic's scope
- Do NOT implement tasks belonging to other epics
- If a task is already marked Done, skip it
- Follow existing code patterns — check similar completed epics for reference

## Step 5 — Build & Test

Run the appropriate verification commands using tasks or seperate subagents if possible from within the worktree:

**Backend** (from worktree `backend/` directory):
```bash
./mvnw spotless:apply    # Format first
./mvnw clean verify      # Compile + test
```

**Frontend** (from worktree `frontend/` directory):
```bash
pnpm install
pnpm run lint
pnpm run build
```

If tests fail, fix them. Iterate until green. Do not skip or disable tests.

## Step 6 — Commit

- Use conventional commit messages: `feat(epic-N): <description>`
- Stage only the files you changed — do not use `git add -A`
- One commit per logical unit of work (multiple commits are fine)

## Step 7 — Push & Create PR

```bash
git push -u origin epic-<N>/<descriptive-name>
```

Create the PR targeting `main`:
```bash
gh pr create --title "Epic <N>: <Epic Name>" --body "$(cat <<'EOF'
## Summary
<What this epic implements — reference TASKS.md>

## Changes
<Bulleted list of key files/components added or modified>

## Epic Tasks Completed
<Checklist of task IDs from TASKS.md that this PR addresses>

## Test plan
<How to verify — build commands, manual testing steps>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

## Step 8 — Code Review

Run the code-reviewer agent on the PR diff:
- Check for bugs, security issues, and adherence to project conventions
- Fix any critical issues found
- Push fixes as additional commits

## Step 9 — Merge (With Confirmation)

**Ask the user before merging.** Do not auto-merge.

If approved, follow this exact sequence to avoid worktree/branch conflicts:

```bash
# 1. Merge the PR (this deletes the remote branch)
gh pr merge --squash --delete-branch
```

```bash
# 2. Navigate back to the main repo BEFORE cleaning up
cd /Users/rakheendama/Projects/2026/b2b-strawman

# 3. Remove the worktree (the branch ref was deleted by --delete-branch)
git worktree remove ../worktree-epic-<N> --force

# 4. Pull the squash-merged commit into main
git pull origin main

# 5. Prune stale branch refs
git fetch --prune
```

Then update the task status in the appropriate task file:
- For epics 1–36: update status in `TASKS.md`
- For epics 37+: update status in the linked phase file (e.g., `tasks/phase4-customers-tasks-portal.md`) AND the overview row in `TASKS.md`

Commit and push the status update from the main repo (not the worktree).

## Guardrails

- **Scope boundary**: If you're unsure whether something is in scope, STOP and ask
- **No over-implementation**: Resist the urge to "improve" adjacent code
- **No duplicate work**: If another branch/PR already implemented something, skip it
- **Verify before done**: Never mark complete without a green build
- **Config preservation**: When editing config files, preserve existing content
