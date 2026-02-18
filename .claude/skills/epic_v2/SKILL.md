---
name: epic_v2
description: Implement a specific epic end-to-end — worktree, code, test, PR, review, merge. Supports auto-merge for automated phase execution. Pass the epic number as an argument (e.g. /epic_v2 7).
---

# Epic Implementation Workflow (v2)

Implement the specified epic using a **Scout → Builder pipeline** for context efficiency.

## Architecture

You are the **orchestrator**. You stay lean and delegate all heavy work to subagents:

1. **Scout agent** — explores codebase, reads docs, studies patterns → writes a self-contained implementation brief to a file
2. **Builder agent** — reads ONLY the brief file → implements, tests, commits, pushes, creates PR

This prevents the builder from burning 50%+ of its context on research it only needs the conclusions from. The scout's context is discarded after it produces the brief.

**Context budget rule**: The orchestrator NEVER reads ARCHITECTURE.md, full phase task files, or CLAUDE.md subdirectory files. That is exclusively the scout's job.

## Arguments

Epic number (e.g., `/epic_v2 7` or `/epic_v2 5A` for slices).

Append `auto-merge` to skip merge confirmation (e.g., `/epic_v2 83A auto-merge`). Used by the `run-phase.sh` wrapper for hands-off execution.

## Auto-Merge Mode

If the user's prompt contains the word **`auto-merge`**:
- **Step 5 (Merge)** proceeds immediately after review passes — no user confirmation
- If review returns `REQUEST_CHANGES` after the fix cycle, **stop and report** (do not force-merge)
- All other steps remain identical

Without `auto-merge` in the prompt, behavior is identical to `/epic` — asks user before merging.

## Task Tracking

Create high-level tasks for visibility:
- "Scout Epic {N}" — while scout researches and writes brief
- "Implement Epic {N}" — while builder codes
- "Review PR #{num}" — while reviewer checks
- "Merge Epic {N}" — after review passes (or user approves)

## Step 0 — Validate (Orchestrator, Lightweight)

1. Extract the epic number from the user's input.
2. Read `TASKS.md` (overview-only) to identify the phase and linked task file.
3. Read ONLY the Epic overview, Dependency Graph and Implementation order of the phase task file to get the Epic Overview table.
4. If the epic is marked **Done**, stop and inform the user.
5. Extract: **scope** (Frontend/Backend/Both), **dependencies** (verify Done), **task IDs**.
6. Check for existing work:
```bash
git branch -a | grep "epic-{N}" ; gh pr list --state all --search "Epic {N}" | head -5
```
If work exists, inform the user and ask how to proceed.

## Step 1 — Create Worktree (Orchestrator)

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree add ../worktree-epic-<N> -b epic-<N>/<descriptive-name>
```

Create this BEFORE dispatching agents so the scout can write the brief into it.

## Step 2 — Dispatch Scout Agent

Launch a **blocking** `general-purpose` subagent. The scout explores the main repo and writes a brief file into the worktree.

### Scout Prompt Template

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
Check where similar files live and mirror that structure.

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

### Backend — Tiered Build Strategy (minimizes build time AND context usage)

All commands run from: cd /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}/backend

The strategy has 3 tiers. Use the CHEAPEST tier that answers your current question:
- **Tier 1 (Compile)**: ~30s — "Does it compile?" Use while iterating on code.
- **Tier 2 (Targeted Tests)**: ~2-3min — "Do MY new/changed tests pass?" Use after code is written.
- **Tier 3 (Full Verify)**: ~10-15min — "Does everything pass?" Use ONCE before commit/PR.

**Step 0: Kill zombie processes (prevents connection pool exhaustion)**
  pgrep -f 'surefire.*worktree-epic' | xargs kill 2>/dev/null || true; sleep 2

**Step 1: Format**
  ./mvnw spotless:apply 2>&1 | tail -3

**Step 2: Tier 1 — Compile check (run after writing code, before tests)**
  ./mvnw compile test-compile -q > /tmp/mvn-epic-{SLICE}.log 2>&1; if [ $? -eq 0 ]; then echo "COMPILE OK"; else echo "COMPILE FAILED"; grep -E '\[ERROR\]' /tmp/mvn-epic-{SLICE}.log | head -20; fi

  Fix any compilation errors. Repeat Tier 1 until clean. This is fast — use it freely.

**Step 3: Tier 2 — Targeted tests (run only YOUR new/modified test classes)**
  ./mvnw test -Dtest="{NEW_TEST_CLASSES}" -q > /tmp/mvn-epic-{SLICE}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "TARGETED TESTS PASSED"; grep 'Tests run:' /tmp/mvn-epic-{SLICE}.log | tail -1; else echo "TARGETED TESTS FAILED"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); echo "FAILED: $FAILED"; fi

  Replace {NEW_TEST_CLASSES} with the comma-separated test class names from this epic
  (e.g., "ChecklistTemplateServiceTest,ChecklistTemplateControllerTest").
  If tests also need integration test runner: add -Dit.test="{NEW_TEST_CLASSES}" and use verify instead of test.

  If targeted tests fail, get full stack traces (with full logging to see what went wrong):
    ./mvnw test -Dtest="{FAILED_CLASSES}" 2>&1 | tail -80

  Fix and repeat Tier 2 until your tests pass. Do NOT jump to Tier 3 with failing targeted tests.

**Step 4: Tier 3 — Full verify (run ONCE before commit, confirms no regressions)**
  ./mvnw clean verify -q > /tmp/mvn-epic-{SLICE}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "FULL BUILD SUCCESS"; grep 'Tests run:' /tmp/mvn-epic-{SLICE}.log | tail -1; else echo "FULL BUILD FAILED (exit $MVN_EXIT)"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); if [ -n "$FAILED" ]; then echo "FAILED TESTS: $FAILED"; else grep -E '\[ERROR\]' /tmp/mvn-epic-{SLICE}.log | head -20; fi; fi

  If Tier 3 fails on tests that are NOT yours: fix the regression (you likely broke an import or
  changed a shared method signature). Re-run ONLY the failed tests with full logging:
    ./mvnw verify -Dit.test="{FAILED_CLASSES}" -Dtest="{FAILED_CLASSES}" 2>&1 | tail -80
  Then re-run Tier 3 to confirm green.

IMPORTANT: NEVER run ./mvnw clean verify without -q — full output burns 30-60KB of context per run.
If you need to debug a compilation error, read the log file with grep:
  grep -n 'ERROR\|cannot find symbol\|Caused by' /tmp/mvn-epic-{SLICE}.log | head -30

IMPORTANT: Do NOT skip tiers. Always go 1→2→3. Do NOT run Tier 3 repeatedly to iterate on
failures — drop back to Tier 1 or 2, fix, then re-run Tier 3 once.

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

## Step 3 — Dispatch Builder Agent

Verify the brief file exists, then launch a **blocking** `general-purpose` subagent:

### Builder Prompt Template

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

### 2. Build & Verify (Tiered — read the brief carefully)
Follow the **tiered build strategy** from the brief's "Build & Verify" section:
  - **Tier 1 (compile)**: Run after writing code. Fast (~30s). Iterate here for compile errors.
  - **Tier 2 (targeted tests)**: Run only YOUR new test classes. (~2-3min). Iterate here for test failures.
  - **Tier 3 (full verify)**: Run ONCE before commit. (~10-15min). Confirms no regressions.

Always go 1→2→3. Never jump straight to Tier 3. Never run Tier 3 repeatedly to iterate.
Build output is redirected to log files — only summaries enter your context.
If the build fails:
  1. Kill zombie surefire processes first: pgrep -f 'surefire.*worktree-epic' | xargs kill 2>/dev/null || true; sleep 2
  2. Read the relevant log file to understand the error
  3. Fix the root cause (not symptoms)
  4. Drop back to Tier 1 or 2, fix, then re-attempt Tier 3. Max 3 Tier-3 attempts.
  5. If still failing after 3 Tier-3 attempts, stop and report what's failing and your hypotheses. Do NOT keep retrying — zombie processes accumulate and cause connection pool exhaustion.

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
When done, report:
- PR number and URL
- Files created/modified count
- Test results summary
- Any deviations from the brief or issues encountered

Do NOT stop to ask questions. Use the brief to resolve ambiguity.
If the brief is genuinely missing critical info, note it in the PR description
and make your best judgment call.
```

## Step 4 — Code Review

Extract the PR number from the builder's response. Write the diff to a file, then dispatch review:

```bash
gh pr diff {PR_NUMBER} > /tmp/pr-{PR_NUMBER}.diff
```

Launch a **blocking** `general-purpose` subagent:

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
  use set_config for session vars), exposed internal endpoints, missing access control
- **Data corruption**: Missing @Transactional, race conditions, incorrect cascade types

### High (should fix)
- **Convention violations**: Anti-patterns from CLAUDE.md — ThreadLocal instead of ScopedValue,
  OSIV issues, wrong exception patterns, missing bean validation
- **Test gaps**: New endpoints without tests, integration tests missing tenant isolation
  assertions, tests without provisionTenant/planSyncService setup
- **Frontend/backend parity**: Permission logic doesn't match across layers

### Medium
- Dead code, duplicated logic, missing error handling at system boundaries

## Output Format
Return structured findings:

# Review: PR #{PR_NUMBER}
## Verdict: {APPROVE | REQUEST_CHANGES}
## Critical
- **[file:line]** Issue → Fix: suggestion
## High
{same}
## Medium
{same}
## Summary
- Issues: N critical, N high, N medium
- {1-2 sentence assessment}

Only report issues you're >80% confident about. Include file:line for every finding.
```

If critical or high issues are found, dispatch another `general-purpose` subagent to fix them in the worktree. Pass the review findings AND the brief file path so the fixer has full context.

## Step 5 — Merge

### Auto-Merge Mode (prompt contains "auto-merge")

If the review verdict is **APPROVE** (after any fix cycles): merge immediately, no confirmation needed.

If the review verdict is **REQUEST_CHANGES** and the fix cycle did not resolve all critical/high issues: **STOP** and report the unresolved issues. Do not merge. Exit with a clear error message so the wrapper script knows this slice failed.

### Manual Mode (no "auto-merge" in prompt)

**Ask the user before merging.** Do not auto-merge.

### Merge Procedure (both modes)

```bash
# 1. Merge the PR
gh pr merge {PR_NUMBER} --squash --delete-branch

# 2. Clean up worktree
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../worktree-epic-<N> --force
git pull origin main
git fetch --prune
```

Then update task status — **ALL THREE locations must be updated**:
1. Mark the slice **Done** in the **Implementation Order table** row (the row starting with `| **{SLICE}** |` — add `**Done** (PR #{PR_NUMBER})` in the last column). **THIS IS CRITICAL** — the phase script checks this specific table to determine completion.
2. Mark the epic **Done** in the **Epic Overview table** at the top of the task file (if all slices in the epic are done).
3. Update the status column in `TASKS.md` overview.
- Commit and push from main repo

## Guardrails

- **Context hygiene**: Orchestrator NEVER reads architecture/ARCHITECTURE.md, full task files, or subdirectory CLAUDE.md files
- **Brief is the contract**: Builder works from the brief only — if the brief is wrong, re-run the scout, don't have the builder explore
- **Build output stays in files**: All build/test output goes to `/tmp/` log files — only summaries enter agent context
- **No over-implementation**: Builder implements ONLY the brief's task list
- **Verify before done**: Green build required before PR creation
- **No duplicate work**: Check `git branch -a` and `gh pr list` before starting
- **Scope boundary**: If uncertain (and not in auto-merge mode), STOP and ask the user

## Recovery

If the builder fails:
1. Check its output summary for error details
2. If fixable: dispatch a new `general-purpose` agent with the same brief path + the error context
3. If the brief was insufficient: re-run the scout with additional guidance (e.g., "also extract the auth filter pattern")
4. If worktree is broken:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../worktree-epic-<N> --force
git branch -D epic-<N>/<branch-name> 2>/dev/null
```
Then restart from Step 1.
