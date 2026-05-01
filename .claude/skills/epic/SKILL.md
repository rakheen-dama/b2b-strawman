---
name: epic
description: Implement a specific epic end-to-end — worktree, code, test, PR, review, merge. Pass the epic number as an argument (e.g. /epic 7).
---

# Epic Implementation Workflow

Implement the specified epic using a **Scout → Builder pipeline** for context efficiency.

## Architecture

You are the **orchestrator**. You stay lean and delegate all heavy work to subagents:

1. **Scout agent** — explores codebase, reads docs, studies patterns → writes a self-contained implementation brief to a file
2. **Builder agent** — reads ONLY the brief file → implements, tests, commits, pushes, creates PR

This prevents the builder from burning 50%+ of its context on research it only needs the conclusions from. The scout's context is discarded after it produces the brief.

**Context budget rule**: The orchestrator NEVER reads ARCHITECTURE.md, full phase task files, or CLAUDE.md subdirectory files. That is exclusively the scout's job.

## Arguments

Epic number (e.g., `/epic 7` or `/epic 5A` for slices).

## Task Tracking

Create high-level tasks for visibility:
- "Scout Epic {N}" — while scout researches and writes brief
- "Implement Epic {N}" — while builder codes
- "Review PR #{num}" — while reviewer checks
- "Merge Epic {N}" — after user approves

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

### 2. Build & Verify (CLAUDE.md §1 — full verify is mandatory)
Run the exact build commands from the brief's "Build & Verify" section.
Build output is redirected to log files — only summaries enter your context.

**Targeted tests are inner-loop only. The merge bar is a clean full verify** (`./mvnw verify` for backend, `pnpm lint && pnpm build && pnpm test` for frontend/portal). Don't ship without it.

If the build fails:
  1. Read the relevant log file to understand the error
  2. Fix the root cause (not symptoms)
  3. Re-run. Max 3 fix cycles.
  4. If still failing after 3 cycles, stop and report what's failing and your hypotheses.

When reading log files for errors, use targeted reads:
  grep -n "ERROR\|FAILURE\|Caused by" /tmp/mvn-epic-{SLICE}.log | tail -20
  NOT: cat /tmp/mvn-epic-{SLICE}.log (this defeats the purpose of output redirection)

### 2b. Write Verify Marker (required by pre-PR-merge-gate hook)
After full verify is green, write the marker file the merge-gate hook reads. The hook runs at `gh pr merge` time, which the orchestrator invokes from the **main repo** (`/Users/rakheendama/Projects/2026/b2b-strawman`), not the worktree. So write the marker into the main repo's `.claude/markers/` — `cd` there first so the relative path resolves correctly:

```bash
# Write marker to main repo (NOT the worktree subdir) so the hook can read it.
cd /Users/rakheendama/Projects/2026/b2b-strawman

# If backend changed:
cat > .claude/markers/verify-backend.json <<EOF
{"commit":"$(git rev-parse --short HEAD)","command":"./mvnw verify","exit":0,"ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","summary":"<test count from log>"}
EOF

# If frontend changed:
cat > .claude/markers/verify-frontend.json <<EOF
{"commit":"$(git rev-parse --short HEAD)","command":"pnpm run lint && pnpm run build && pnpm test","exit":0,"ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","summary":"<test count>"}
EOF

# Then cd back to your worktree to continue:
cd /Users/rakheendama/Projects/2026/worktree-epic-{SLICE}
```

**Portal**: same pattern, write `verify-portal.json`. Do NOT write a marker for a failing run.

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

## Step 5 — Merge (With Confirmation, gated by `.claude/hooks/pre-pr-merge-gate.sh`)

**Ask the user before merging.** Do not auto-merge.

The merge-gate hook will block `gh pr merge` if:
- The verify marker for any touched area (backend / frontend / portal) is missing or stale (>24h) or `exit != 0`.
- The PR is not documentation-only.

If the hook blocks you, that means a marker is missing or stale — fix the verify, write the marker, retry. **Do NOT bypass with `--admin` or by editing the hook.** If you genuinely think the hook is wrong, raise it and fix the hook upstream.

If user-approved and gate-clear:

```bash
# 1. Merge the PR
gh pr merge {PR_NUMBER} --squash --delete-branch

# 2. Clean up worktree
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../worktree-epic-<N> --force
git pull origin main
git fetch --prune
```

Then update task status:
- Mark the slice/epic **Done** in the phase task file
- Update the status column in `TASKS.md`
- Commit and push from main repo

## Guardrails (CLAUDE.md §1–§10)

These are NOT advice. Loopholes are forbidden. If a rule blocks you, raise it; don't bypass.

- **Context hygiene**: Orchestrator NEVER reads architecture/ARCHITECTURE.md, full task files, or subdirectory CLAUDE.md files
- **Brief is the contract**: Builder works from the brief only — if the brief is wrong, re-run the scout, don't have the builder explore
- **Build output stays in files**: All build/test output goes to `/tmp/` log files — only summaries enter agent context
- **No over-implementation**: Builder implements ONLY the brief's task list (one slice per PR)
- **Full verify is mandatory before PR**, NOT targeted tests. The `.claude/markers/verify-*.json` files must exist and be current. The pre-merge hook will block merge without them.
- **Mandatory review pass**. CodeRabbit (preferred), `superpowers:code-reviewer` subagent, or human eyeball — never merge an agent-authored PR without one.
- **Scope boundary**: If scope expands during a slice ("while I was here, this related thing…"), STOP and re-spec. Don't bundle.
- **Max 3 build attempts.** Report failure (specific error) and exit STUCK if still broken. Don't band-aid.
- **PASS means observed end-to-end**. Inferred PASS is forbidden. "Build green" alone is NOT PASS — it's compile-pass; behavioural verification (test or manual run) is required.
- **No duplicate work**: Check `git branch -a` and `gh pr list` before starting
- **Pride and quality.** Slow correct slice > fast broken slice. This is not a race.

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
