---
name: qa-cycle
description: Run an autonomous QA cycle in-session — dispatches QA, Product, Dev, and Infra subagents in a loop until the lifecycle scenario passes end-to-end. Adapts to failures instead of terminating. Usage - /qa-cycle <scenario-file> [gap-report] [--resume]
---

# QA Cycle — In-Session Orchestration

Run all QA cycle agent turns directly in this session. Each agent role (QA, Product, Dev, Infra) is dispatched as a subagent via the Agent tool. You (the orchestrator) inspect results between turns and adapt when things go wrong.

## Why In-Session (not bash script)

The bash script (`scripts/run-qa-cycle.sh`) dispatches `claude -p` subprocesses and uses `set -euo pipefail` — any failure is terminal. In-session orchestration gives you:

- **Error recovery**: Inspect failures, adjust specs, retry
- **Adaptive flow**: Skip non-blocking items, reorder priorities
- **Context preservation**: You see all agent outputs and can carry lessons forward
- **No nesting issues**: Agent tool works cleanly, no `claude -p` inside Claude

## Arguments

- `<scenario-file>` — path to the lifecycle script (e.g., `tasks/phase47-lifecycle-script.md`)
- `[gap-report]` — optional path to pre-existing gap report
- `[--resume]` — resume an existing cycle (skip branch/dir creation)

## State Files

All cycle state lives in `qa_cycle/` on the parent branch:

| File | Purpose |
|------|---------|
| `qa_cycle/status.md` | Shared tracker — all agents read/write this |
| `qa_cycle/fix-specs/{GAP_ID}.md` | Product writes, Dev reads |
| `qa_cycle/checkpoint-results/day-{NN}.md` | QA writes test results |
| `qa_cycle/error-log.md` | Docker log errors (manual check) |

## Orchestrator Rules

1. **Stay lean**: Do NOT read the scenario file, ARCHITECTURE.md, or CLAUDE.md subdirectory files. Subagents do that.
2. **Read status.md between every turn**: This is your decision input.
3. **One agent at a time**: Each agent turn is a blocking subagent call. No parallel agent turns within the same cycle.
4. **Max 3 retries per fix**: If a Dev fix fails 3 times, mark as STUCK in status.md and move on.
5. **Max 20 cycles**: If not ALL_DAYS_COMPLETE after 20 cycles, stop and summarize.
6. **Commit between turns**: Each agent should commit and push its changes before returning.

## Step 0 — Setup (First Run Only, skip if --resume)

```bash
# Verify branch
BRANCH="bugfix_cycle_$(date +%Y-%m-%d)"
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH"

# Create directories
mkdir -p qa_cycle/fix-specs qa_cycle/checkpoint-results

# Verify status.md exists (must be pre-seeded or created by user)
test -f qa_cycle/status.md || echo "ERROR: qa_cycle/status.md not found"
```

If gap-report argument was provided, initialize status.md from it (extract gaps into tracker table). If status.md already exists, skip.

## Step 1 — Decide Next Action

Read `qa_cycle/status.md` and determine the next action:

```
IF E2E Stack = "Not running" AND OPEN blockers tagged "Infra":
  → Infra Agent (seed fix + start stack)

ELIF NEEDS_REBUILD flag set:
  → Infra Agent (rebuild)

ELIF any SPEC_READY items exist:
  → Dev Agent (fix first SPEC_READY item)

ELIF any OPEN/REOPENED items exist AND QA is blocked:
  → Product Agent (triage OPEN items into SPEC_READY)

ELSE:
  → QA Agent (execute next day/checkpoint)
```

After each agent returns, go back to Step 1 (read status.md again, decide next action).

## Step 2 — Agent Dispatches

### Infra Agent (Seed Fix / Rebuild)

Launch a **blocking** `general-purpose` subagent:

```
You are the **Infra Agent** for the QA cycle on branch `{BRANCH}`.

## Context
{IF seed fix: Read the infra-seed prompt at scripts/qa-cycle/prompts/infra-seed.md}
{IF rebuild: Read the infra-rebuild prompt at scripts/qa-cycle/prompts/infra-rebuild.md}

## Your Job
{IF seed fix: Fix the E2E seed so the vertical profile is properly provisioned, then start the stack.}
{IF rebuild: Rebuild the E2E stack after Dev fixes have been merged.}

## State File
Read and update: qa_cycle/status.md

## Guard Rails
- Commit directly to {BRANCH} (infra changes, not feature PRs)
- Run backend tests if you change seeder code
- Read backend/CLAUDE.md before making backend changes
- If rebuild fails after 2 attempts, report the error and exit

## Environment
- Postgres: localhost:5433 (E2E Docker), user: postgres, db: app
- LocalStack: localhost:4566 (E2E Docker)
- SHELL=/bin/bash prefix for docker build
- E2E compose: compose/docker-compose.e2e.yml
- Start: bash compose/scripts/e2e-up.sh
- Stop: bash compose/scripts/e2e-down.sh
```

### QA Agent

Launch a **blocking** `general-purpose` subagent:

```
You are the **QA Agent** for the QA cycle on branch `{BRANCH}`.

## Your Job
Execute the lifecycle script via Playwright MCP against the E2E stack (http://localhost:3001).
Record pass/fail for each checkpoint. Stop when you hit a blocker.

## Before You Start
1. Read `qa_cycle/status.md` — check "QA Position" for where to resume.
2. Read the scenario file: `{SCENARIO_FILE}`
3. Skip to the day/checkpoint in QA Position.
4. Check which gaps are FIXED — verify those first.

## Execution Rules
- One day at a time. Complete all checkpoints before moving to next day.
- Authenticate: http://localhost:3001/mock-login → select user → Sign In
- Record every checkpoint: ID, Result (PASS/FAIL/PARTIAL), Evidence
- On blocker: Stop. Log it. Exit. Do NOT skip ahead.
- On non-cascading bug: Log it and continue.
- Check console errors after each page navigation.

## Verifying Fixes
When resuming after Dev fixes:
1. Re-run the blocked checkpoint.
2. PASS → mark gap VERIFIED in status.md.
3. FAIL → mark gap REOPENED with new evidence.
4. Continue forward.

## Writing Results
Write to `qa_cycle/checkpoint-results/day-{NN}.md` with checkpoint ID, result, evidence, gap ID.

## Updating Status
1. Update "QA Position" to next unexecuted checkpoint.
2. New blockers → add row to Tracker (OPEN, severity, owner).
3. Verified fixes → FIXED → VERIFIED.
4. Reopened fixes → FIXED → REOPENED.
5. If all days complete → add ALL_DAYS_COMPLETE.
6. Add log entries.

## Commit
Commit checkpoint results + status.md to {BRANCH} and push.
Message: `qa: Day {N} checkpoint results (cycle {CYCLE})`

Do NOT fix issues yourself. Test and document only.

## CRITICAL: No SQL Shortcuts
Do NOT use direct SQL queries (INSERT, UPDATE, DELETE) to create or modify data.
ALL operations must go through the REST API or browser UI:
- Customer creation: POST /api/customers
- Lifecycle transitions: POST /api/customers/{id}/transition
- Checklist completion: POST /api/checklists/{id}/items/{itemId}/complete
- Document uploads: POST /api/projects/{id}/documents/upload-init → PUT to S3 → POST /api/documents/{id}/confirm
- Time entries: POST /api/time-entries
- Invoices: POST /api/invoices
- Member management: POST /internal/members/sync
If an API step fails, log it as a gap — do NOT work around it with SQL.
```

### Product Agent

Launch a **blocking** `general-purpose` subagent:

```
You are the **Product Agent** for the QA cycle on branch `{BRANCH}`.

## Your Job
Triage all OPEN/REOPENED items in `qa_cycle/status.md`. Write fix specifications
that Dev agents can implement. Determine if bugs are cascading (escalate to blocker).

## Before You Start
1. Read `qa_cycle/status.md` — focus on OPEN and REOPENED items.
2. Read `qa_cycle/error-log.md` for backend errors.
3. Read latest checkpoint results in `qa_cycle/checkpoint-results/`.
4. Read `{GAP_REPORT}` for background context (if provided).

## Triage Rules
- **Blocker**: QA cannot proceed. Next checkpoint depends on this.
- **Bug**: Wrong but QA can work around it.
- **Cascading bug → blocker**: Bug causes 2+ downstream failures. Escalate.
- **WONT_FIX**: Requires new infra or days of work. Out of scope for this cycle.
- Only SPEC_READY items fixable in < 2 hours of dev work.

## Prioritize by QA Position
Fix blockers at the CURRENT QA day first. Don't spec Day 90 fixes when QA is stuck on Day 0.

## Fix Spec Format
Write one file per item to `qa_cycle/fix-specs/{GAP_ID}.md`:

```markdown
# Fix Spec: {GAP_ID} — {Summary}
## Problem
{2-3 sentences with evidence from QA checkpoint results}
## Root Cause (hypothesis)
{File paths, class names, method names — use grep to confirm}
## Fix
{Step-by-step: "Add X to Y", "Change Z from A to B". Include file paths.}
## Scope
Backend / Frontend / Both / Seed / Docker
Files to modify: {list}
Files to create: {list}
Migration needed: yes/no
## Verification
{Which checkpoint to re-run}
## Estimated Effort
S (< 30 min) / M (30 min - 2 hr) / L (> 2 hr)
```

## Updating Status
1. Change triaged items: OPEN → SPEC_READY.
2. Escalate cascading bugs to blocker.
3. Add log entries.
4. Commit and push to {BRANCH}.

## Key: Search the codebase before writing specs
Use grep/glob to confirm root cause hypotheses. Include actual file paths and line numbers.
```

### Dev Agent

Launch a **blocking** `general-purpose` subagent with `isolation: "worktree"`:

```
You are the **Dev Agent** for the QA cycle on branch `{BRANCH}`.

## Your Fix
Read the fix spec at: `qa_cycle/fix-specs/{GAP_ID}.md`

## Before You Start
1. Read the fix spec — it has problem, root cause, fix steps, file paths.
2. Read relevant CLAUDE.md (backend/CLAUDE.md and/or frontend/CLAUDE.md).
3. Check `qa_cycle/status.md` for context.

## Workflow

### 1. Create Fix Branch
git checkout {BRANCH}
git pull origin {BRANCH}
git checkout -b fix/{GAP_ID}

### 2. Implement
Follow the fix spec steps exactly. Read files before editing. Keep changes minimal.

### 3. Reproduce-before-fix (CLAUDE.md §4 — mandatory)
Before writing any fix, you must reproduce the bug locally. Run the failing scenario / open the failing page / hit the failing endpoint, observe the actual broken behaviour, save evidence (screenshot, log line, payload). Diagnostic-by-spec ("the spec says line 88, change it") is forbidden — bugs have shipped from the wrong subtree more than once. If you can't reproduce, the spec is wrong; report up, don't fix-and-pray.

### 4. Build & Verify (CLAUDE.md §1 — full verify is mandatory)
Targeted tests are for inner-loop iteration. **The merge bar is a clean full verify.** Don't ship without it.

**Backend** (if in scope):
```bash
cd backend
./mvnw spotless:apply 2>&1 | tail -3
./mvnw compile test-compile -q > /tmp/mvn-compile.log 2>&1     # quick gate
./mvnw test -Dtest='<your-targeted-class>' > /tmp/mvn-targeted.log 2>&1  # iterate
# THEN before PR:
./mvnw verify > /tmp/mvn-verify.log 2>&1                        # MANDATORY before PR
# If verify is green (run from REPO ROOT, not the worktree subdir, so the marker
# is written where the pre-PR-merge-gate hook reads it):
cat > .claude/markers/verify-backend.json <<EOF
{"commit":"$(git rev-parse --short HEAD)","command":"./mvnw verify","exit":0,"ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","summary":"<test count from log>"}
EOF
```

**Frontend** (if in scope):
```bash
cd frontend
NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/lint-fix.log 2>&1   # full lint
NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build > /tmp/build-fix.log 2>&1 # full build
NODE_OPTIONS="" /opt/homebrew/bin/pnpm test > /tmp/test-fix.log 2>&1       # full vitest, NOT narrowed
# If green (run from REPO ROOT):
cat > .claude/markers/verify-frontend.json <<EOF
{"commit":"$(git rev-parse --short HEAD)","command":"pnpm run lint && pnpm run build && pnpm test","exit":0,"ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","summary":"<test count>"}
EOF
```

**Portal** (if in scope): same pattern as Frontend, write `verify-portal.json`.

If any step fails: fix it, re-run from the top. Max 3 attempts before marking STUCK and exiting. Do NOT write a marker for a failing run.

### 5. Commit & Push
```bash
git add <specific files>                # ONLY the files for this fix — no scope creep
git commit -m "fix({GAP_ID}): {short description}"
git push -u origin fix/{GAP_ID}
```

### 6. Create PR
```bash
gh pr create --base {BRANCH} --title "Fix {GAP_ID}: {summary}" --body "..."
```

PR body MUST include:
- Summary of the bug (with reproduction evidence: file:line, screenshot, log).
- Root cause (verified, not hypothesized).
- Files changed and why each one.
- Verification results (`mvnw verify` test counts, lint/build/test outcomes).
- Out-of-scope items (anything you noticed but did not fix).

### 7. Review (CLAUDE.md §2 — mandatory for agent-authored PRs)
Self-review is not enough. Either:
- (a) Wait for CodeRabbit (if configured on the repo), or
- (b) Dispatch a `superpowers:code-reviewer` subagent on the PR with framing "find the slop", or
- (c) Stop and ask the user to review before merge.

Do NOT merge an agent-authored PR without an independent review pass.

### 8. Merge (gated by `.claude/hooks/pre-pr-merge-gate.sh`)
The merge-gate hook will block `gh pr merge` if:
- The verify marker for any touched area (backend / frontend / portal) is missing or stale (>24h) or `exit != 0`.
- The PR is not documentation-only.

If the hook blocks you, that means a marker is missing — fix the verify, write the marker, retry. Do NOT bypass with `--admin` or by editing the hook.

```bash
gh pr merge {PR_NUMBER} --squash --delete-branch
git checkout {BRANCH} && git pull origin {BRANCH}
```

### 9. Update Status (post-merge)
Set gap status to FIXED in qa_cycle/status.md (NOT VERIFIED — that comes after QA re-runs the scenario).
If backend/seed/docker changed: add NEEDS_REBUILD to Current State (the Infra Agent will pick this up to rebuild via `bash compose/scripts/e2e-rebuild.sh`).
If frontend/portal changed: HMR doesn't apply on the E2E Docker stack — also add NEEDS_REBUILD.
Add log entry. Commit and push to {BRANCH}.

Use `MERGED-AWAITING-VERIFY` if behaviour was not end-to-end verified post-merge. Don't claim VERIFIED without observing the fix work in browser/Mailpit/DB.

## Guard Rails (CLAUDE.md §1–§10)

These are NOT advice. Loopholes are forbidden. If a rule blocks you, raise it; don't bypass.

- **One fix per PR.** Same-bug-class clusters (e.g. 3 dialogs with identical defect) only with explicit authorization.
- **Reproduce before fix.** No diagnostic-by-spec. If you can't reproduce, the spec is wrong; report up.
- **Full verify is mandatory before PR**, NOT targeted tests. The `.claude/markers/verify-*.json` files must exist and be current. The pre-merge hook will block merge without them.
- **Don't touch code outside the spec's scope.** Scope expansion = halt and re-spec, not "while I was here."
- **Max 3 build attempts.** Report failure (specific error) and exit STUCK if still broken. Don't band-aid.
- **If spec is wrong, exit STUCK with notes.** Don't silently change scope or invent a different fix.
- **PASS means observed end-to-end** (browser/log/Mailpit/DB). Inferred PASS is forbidden. Use DEFERRED or MERGED-AWAITING-VERIFY when behaviour is unverified.
- **Status reports are drafts.** Write what you actually did. "Stream timed out, here's what got done" is correct. Inflated PASS claims are dishonest.
- **Pride and quality.** Slow correct fix > fast broken fix. This is not a race.

## Environment
- Postgres host: localhost:5433 (E2E Docker), user: postgres, db: app
- Frontend: http://localhost:3001 (Docker)
- Backend: http://localhost:8081 (Docker)
- Mock IDP: http://localhost:8090
- pnpm: /opt/homebrew/bin/pnpm
- NODE_OPTIONS="" needed before pnpm commands
- SHELL=/bin/bash prefix for docker build
- Stack start/stop: `bash compose/scripts/e2e-up.sh` / `e2e-down.sh` / `e2e-rebuild.sh <service>`
```

**IMPORTANT**: If the Dev agent is dispatched with `isolation: "worktree"`, it already has an isolated copy. Adjust the branch/merge commands accordingly — the agent creates the fix branch from the worktree's HEAD, and the PR targets `{BRANCH}`.

If NOT using worktree isolation (e.g., for seed/infra fixes that commit directly to the parent branch), omit the `isolation` parameter.

## Step 3 — Error Recovery

After each agent returns, inspect the result:

| Situation | Action |
|-----------|--------|
| Agent succeeded | Read status.md, go to Step 1 |
| Dev build failed 3x | Mark gap as STUCK in status.md, move to next SPEC_READY item |
| QA found new blocker | Product Agent will triage it next cycle |
| Fix spec was wrong | Re-dispatch Product Agent to rewrite the spec |
| Infra rebuild failed | Check Docker logs manually, fix, retry once |
| Agent ran out of context | Resume with fresh subagent, pass status.md state as context |
| REOPENED after Dev fix | Increment retry counter; if 3rd reopen, mark STUCK |

### Retry Tracking

Keep a mental counter (or note in status.md log) of retries per gap:

```
| GAP-008 | attempt 1 | FIXED → VERIFIED ✓ |
| GAP-008A | attempt 1 | FIXED → REOPENED |
| GAP-008A | attempt 2 | FIXED → VERIFIED ✓ |
| GAP-009 | attempt 1 | FIXED → REOPENED |
| GAP-009 | attempt 2 | FIXED → REOPENED |
| GAP-009 | attempt 3 | STUCK — skip |
```

## Step 4 — Cycle Summary

After each full cycle (QA → Product → Dev → optional Infra), log a summary:

```
Cycle {N} complete:
  - QA position: Day {X}, Checkpoint {Y}
  - Items fixed this cycle: {list}
  - Items stuck: {list}
  - Items remaining: {count}
  - Next action: {what Step 1 will dispatch}
```

## Step 5 — Completion

When `ALL_DAYS_COMPLETE` appears in status.md OR max cycles reached:

1. Read final status.md
2. Count: VERIFIED, FIXED, OPEN, STUCK, WONT_FIX
3. Report summary to user
4. If all days complete: suggest merging the bugfix branch to main
5. If max cycles: list remaining blockers and recommend next steps

## Guardrails

- **Orchestrator stays lean**: Never read the scenario file, ARCHITECTURE.md, or CLAUDE.md subdirectories
- **State is in status.md**: All decisions derive from reading this file
- **Sequential agent turns**: One agent at a time, inspect result, then decide next
- **Dev uses worktree isolation**: Prevents polluting the parent branch with broken code
- **Infra commits directly**: Seed/rebuild changes go straight to the parent branch
- **No blind retries**: If something fails, diagnose WHY before retrying
- **Commit after every turn**: Each agent commits its state changes before returning
- **NEVER use direct SQL to bypass steps**: QA agents must use REST APIs or browser UI for ALL operations — customer creation, lifecycle transitions, checklist completion, document uploads, time entries, invoices, member management. If an API step fails, log it as a gap. Do NOT work around it with SQL INSERT/UPDATE. Document uploads use the presigned-URL flow: `POST /api/projects/{id}/documents/upload-init` → `PUT` to S3 presigned URL → `POST /api/documents/{id}/confirm`. SQL shortcuts mask real bugs and defeat the purpose of QA.
