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

**Verify-ownership rule**: Subagents (builder + fixers) build only up to **Tier 2** (targeted tests green), then commit/push. The **orchestrator** owns the single **Tier 3** full `mvn verify`, run once on the final commit, in the background (Step 4.6). This is the efficiency backbone of v2: subagents never die mid-verify, no full verify runs on a soon-to-be-superseded intermediate commit, and the builder opens the PR at Tier-2 green so **Backend CI runs in parallel** with reviews and the orchestrator verify instead of as tail latency.

## Arguments

Epic number or slice ID:
- **Full epic** (`/epic_v2 450`): Implements ALL slices (450A, 450B, ...) in a **single PR**. This is the default for `run-phase.sh`.
- **Single slice** (`/epic_v2 450A`): Implements only that slice. Use for manual reruns or targeted fixes.

The orchestrator detects the input type: digits-only = full epic, digits + letter = single slice.

Append `auto-merge` to skip merge confirmation (e.g., `/epic_v2 450 auto-merge`). Used by the `run-phase.sh` wrapper for hands-off execution.

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
2. **Detect input type**:
   - Digits only (e.g., `450`) → **full epic mode** — will implement all slices in one PR
   - Digits + letter (e.g., `450A`) → **single slice mode** — implements only that slice
3. Read `TASKS.md` (overview-only) to identify the phase and linked task file.
4. Read ONLY the Epic overview, Dependency Graph and Implementation order of the phase task file to get the Epic Overview table.
5. If the epic is marked **Done**, stop and inform the user.
6. **For full epic mode**: Read the Slices table under the epic heading to identify ALL slices (e.g., 450A, 450B). Check which are already **Done** — skip those. If ALL slices are Done, stop. Collect task IDs from ALL remaining slices.
7. Extract: **scope** (Frontend/Backend/Both), **dependencies** (verify Done), **task IDs** (across all remaining slices if full epic).
8. Check for existing work:
```bash
git branch -a | grep "epic-{N}" ; gh pr list --state all --search "Epic {N}" | head -5
```
If work exists (open PRs, merged slice PRs covering remaining work), inform the user and ask how to proceed.

## Step 1 — Create Worktree (Orchestrator)

```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman
# Full epic mode: use the epic number (e.g., epic-450)
# Single slice mode: use the slice ID (e.g., epic-450A)
git worktree add ../worktree-epic-<ID> -b epic-<ID>/<descriptive-name>
```

Where `<ID>` is the epic number (full epic mode) or slice ID (single slice mode).

Create this BEFORE dispatching agents so the scout can write the brief into it.

## Step 2 — Dispatch Scout Agent

Launch a **blocking** `general-purpose` subagent. The scout explores the main repo and writes a brief file into the worktree.

### Scout Prompt Template

```
You are a **codebase scout** preparing an implementation brief for Epic {ID}.
({ID} is the epic number in full-epic mode, or slice ID in single-slice mode.)

Your job: explore the codebase thoroughly and write a SELF-CONTAINED brief to:
  /Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md

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

Write the brief to `/Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md`
using this exact structure:

---
# Implementation Brief: Epic {ID} — {TITLE}

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

All commands run from: cd /Users/rakheendama/Projects/2026/worktree-epic-{ID}/backend

The strategy has 3 tiers. Use the CHEAPEST tier that answers your current question:
- **Tier 1 (Compile)**: ~30s — "Does it compile?" Use while iterating on code.
- **Tier 2 (Targeted Tests)**: ~2-3min — "Do MY new/changed tests pass?" Use after code is written.
- **Tier 3 (Full Verify)**: ~10-20min — "Does everything pass?" **Run by the ORCHESTRATOR only**, exactly once, on the FINAL commit (Step 4.6).

**Subagents (builder + fixers) STOP at Tier 2.** They never run Tier 3. Rationale: the full verify is long-running; a subagent's turn routinely ends mid-verify, which SIGTERM-kills its own build (the "exit 143 / forked VM terminated" noise — not a real failure) and forces the orchestrator to salvage the half-done state. Worse, every Tier 3 a subagent runs is on an intermediate commit that the next fix cycle immediately invalidates — pure waste (~20 min each). The orchestrator owns ONE authoritative Tier 3 on the final commit, run in the background where it can't be turn-killed, in parallel with Backend CI and the re-reviews.

**Step 0: Kill zombie processes (prevents connection pool exhaustion)**
  pgrep -f 'surefire.*worktree-epic' | xargs kill 2>/dev/null || true; sleep 2

**Step 1: Format**
  ./mvnw spotless:apply 2>&1 | tail -3

**Step 2: Tier 1 — Compile check (run after writing code, before tests)**
  ./mvnw compile test-compile -q > /tmp/mvn-epic-{ID}.log 2>&1; if [ $? -eq 0 ]; then echo "COMPILE OK"; else echo "COMPILE FAILED"; grep -E '\[ERROR\]' /tmp/mvn-epic-{ID}.log | head -20; fi

  Fix any compilation errors. Repeat Tier 1 until clean. This is fast — use it freely.

**Step 3: Tier 2 — Targeted tests (run only YOUR new/modified test classes)**
  ./mvnw test -Dtest="{NEW_TEST_CLASSES}" -q > /tmp/mvn-epic-{ID}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "TARGETED TESTS PASSED"; grep 'Tests run:' /tmp/mvn-epic-{ID}.log | tail -1; else echo "TARGETED TESTS FAILED"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); echo "FAILED: $FAILED"; fi

  Replace {NEW_TEST_CLASSES} with the comma-separated test class names from this epic
  (e.g., "ChecklistTemplateServiceTest,ChecklistTemplateControllerTest").
  If tests also need integration test runner: add -Dit.test="{NEW_TEST_CLASSES}" and use verify instead of test.

  If targeted tests fail, get full stack traces (with full logging to see what went wrong):
    ./mvnw test -Dtest="{FAILED_CLASSES}" 2>&1 | tail -80

  Fix and repeat Tier 2 until your tests pass. Do NOT jump to Tier 3 with failing targeted tests.

**Step 4: Tier 3 — Full verify (ORCHESTRATOR-ONLY — subagents must NOT run this)**
This command is run by the orchestrator in Step 4.6, in the background, once, on the final commit:
  ./mvnw clean verify -q > /tmp/mvn-epic-{ID}.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "FULL BUILD SUCCESS"; grep 'Tests run:' /tmp/mvn-epic-{ID}.log | tail -1; else echo "FULL BUILD FAILED (exit $MVN_EXIT)"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); if [ -n "$FAILED" ]; then echo "FAILED TESTS: $FAILED"; else echo "NO NAMED TEST FAILURES — env/fork crash (exit 143 / SIGTERM); re-kill zombies and re-run once"; grep -E '\[ERROR\]' /tmp/mvn-epic-{ID}.log | head -20; fi; fi

  Interpreting a Tier 3 failure (orchestrator): if exit is non-zero but NO test class shows up
  in the surefire/failsafe reports with failures/errors, it is an environmental zombie-JVM/heap
  crash (SIGTERM / "forked VM terminated" / "Process Exit Code: 143"), NOT a code failure —
  pkill the stale JVMs, wait 3s, and re-run once. A REAL regression names the failing class in
  the reports; fix the root cause, push, and the gate re-runs.

IMPORTANT: NEVER run ./mvnw clean verify without -q — full output burns 30-60KB of context per run.
If you need to debug a compilation error, read the log file with grep:
  grep -n 'ERROR\|cannot find symbol\|Caused by' /tmp/mvn-epic-{ID}.log | head -30

IMPORTANT (subagents): go 1→2 then STOP. Get Tier 2 green, commit, push, and report. Do NOT run
Tier 3 — the orchestrator owns it. (Frontend subagents: run lint/build/test as below, then stop.)

### Frontend

All commands run from: cd /Users/rakheendama/Projects/2026/worktree-epic-{ID}/frontend

  NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/lint-epic-{ID}.log 2>&1; LINT_EXIT=$?; if [ $LINT_EXIT -ne 0 ]; then echo "LINT FAILED"; tail -20 /tmp/lint-epic-{ID}.log; fi
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build > /tmp/build-epic-{ID}.log 2>&1; BUILD_EXIT=$?; if [ $BUILD_EXIT -ne 0 ]; then echo "BUILD FAILED"; tail -30 /tmp/build-epic-{ID}.log; fi
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm test > /tmp/test-epic-{ID}.log 2>&1; TEST_EXIT=$?; if [ $TEST_EXIT -ne 0 ]; then echo "TESTS FAILED"; tail -30 /tmp/test-epic-{ID}.log; else echo "ALL TESTS PASSED"; tail -3 /tmp/test-epic-{ID}.log; fi

IMPORTANT: Include FULL code for reference patterns. The implementer's ONLY reference
material is this brief. Be generous with code, strict with structure.

When finished, confirm: "Brief written to {path}" and list the section sizes (line counts).
```

## Step 3 — Dispatch Builder Agent

Verify the brief file exists, then launch a **blocking** `general-purpose` subagent with `model: "opus"`:

### Builder Prompt Template

```
You are implementing **Epic {ID}** in the worktree at:
  /Users/rakheendama/Projects/2026/worktree-epic-{ID}

## First Step — Read Your Brief
Read: /Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md
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

### 2. Build & Verify — STOP AT TIER 2 (do NOT run the full verify)
Follow the brief's tiered build strategy, but run ONLY Tier 1 and Tier 2:
  - **Tier 1 (compile)**: Run after writing code. Fast (~30s). Iterate here for compile errors.
  - **Tier 2 (targeted tests)**: Run only YOUR new test classes. (~2-3min). Iterate here until green.
  - **Tier 3 (full verify)**: **DO NOT RUN.** The orchestrator owns the single full verify on your
    final commit. If you start `./mvnw clean verify`, it will likely be killed when your turn ends
    mid-run (exit 143) and you'll waste ~20 min on an intermediate commit anyway.

Go 1→2, get Tier 2 GREEN, then proceed straight to commit/push/PR. Build output is redirected to
log files — only summaries enter your context.
If a build fails:
  1. Kill zombie surefire processes first: pgrep -f 'surefire.*worktree-epic' | xargs kill 2>/dev/null || true; sleep 2
  2. Read the relevant log file to understand the error (grep, don't cat)
  3. Fix the root cause (not symptoms)
  4. Drop back to Tier 1, then re-run Tier 2. Iterate until Tier 2 is green.
  5. If Tier 2 won't go green after several honest attempts, stop and report what's failing and your hypotheses.

When reading log files for errors, use targeted reads:
  grep -n "ERROR\|FAILURE\|Caused by" /tmp/mvn-epic-{ID}.log | tail -20
  NOT: cat /tmp/mvn-epic-{ID}.log (this defeats the purpose of output redirection)

### 3. Commit & Push
- Stage only files you changed: `git add <specific files>`
- Commit: `git commit -m "feat(epic-{ID}): {DESCRIPTION}"`
- Push: `git push -u origin epic-{ID}/{BRANCH_NAME}`

### 4. Create PR (this is intentional at Tier-2 green — it starts Backend CI in parallel)
Creating the PR now triggers the path-filtered Backend CI, which runs the full `mvn verify` in the
cloud. That CI run then overlaps with the code reviews, fix cycles, and the orchestrator's local
full verify — instead of being pure tail latency at the end. In the Test plan section, state plainly
that targeted tests are green and the full verify is owned by the orchestrator + Backend CI.

gh pr create --title "Epic {ID}: {TITLE}" --body "$(cat <<'EOF'
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
- **Tier 2 targeted-test result** (the `Tests run:` line) — this is your verification bar, NOT a full verify
- Confirmation you did NOT run Tier 3 (the orchestrator owns it)
- Any deviations from the brief or issues encountered

Do NOT stop to ask questions. Use the brief to resolve ambiguity.
If the brief is genuinely missing critical info, note it in the PR description
and make your best judgment call.
```

## Step 4 — Combined Code Review (Superpowers reviews are the gate; CodeRabbit is collected)

The merge gate depends on TWO things: (a) the two `superpowers:code-reviewer` subagent reviews
dispatched below, AND (b) a real CodeRabbit review collected and addressed. Both must be reflected
in the PR body audit trail (Step 4.5) before merge — the merge-gate hook enforces this.

CodeRabbit typically arrives within 5–10 minutes of PR creation. The collection strategy is:
**start polling in background immediately after PR creation**, then collect results after the
superpowers reviews and fixer cycles complete. This gives CodeRabbit 10–40+ minutes of wall-clock
time (the superpowers reviews + fix cycles take significant time). If CodeRabbit still hasn't
arrived after all fix cycles complete, do ONE final 5-min bounded wait before marking DEFERRED.

### 4.1 — Dispatch two superpowers reviews in parallel (BLOCKING)

Extract the PR number from the builder's response. Write the diff to a file:

```bash
gh pr diff {PR_NUMBER} > /tmp/pr-{PR_NUMBER}.diff
```

In a SINGLE message, dispatch BOTH of the following Agent calls in parallel.
Use `subagent_type: "superpowers:code-reviewer"` for both.
Do NOT use `run_in_background` — both reviews must complete before the merge step proceeds.

**Agent A — Bug, security, and quality lens**:

```
You are the BUG / QUALITY reviewer for PR #{PR_NUMBER} on the multi-tenant B2B SaaS platform.

## Setup
1. Read the diff: /tmp/pr-{PR_NUMBER}.diff
2. Read the brief: /Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md
3. Read conventions: backend/CLAUDE.md and/or frontend/CLAUDE.md based on scope.

## What to check

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

## Output format
Return structured findings:

# Review: PR #{PR_NUMBER} (Bug/Quality)
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

**Agent B — Architecture and design lens**:

```
You are the ARCHITECTURE reviewer for PR #{PR_NUMBER} on the multi-tenant B2B SaaS platform.

## Setup
1. Read the diff: /tmp/pr-{PR_NUMBER}.diff
2. Read the brief: /Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md
3. Read conventions: backend/CLAUDE.md and/or frontend/CLAUDE.md based on scope.
4. Skim relevant ADRs in architecture/ if the change touches a documented boundary.

## What to check

### Critical (blocks merge)
- **Layering violations**: Controller calling repository directly, service leaking JPA entities
  to controller, cross-package reach-throughs that bypass an existing service interface
- **Boundary breakage**: Multitenancy/audit/security boundary violated (e.g., new code paths
  that side-step `RequestScopes.runForTenant`, audit events emitted without resolver, security
  filters bypassed)
- **ADR contradictions**: Change directly contradicts an existing ADR in architecture/
  without an ADR amendment

### High (should fix)
- **Premature abstraction**: New interface with single implementation and no extension seam
  in the brief; over-parameterized config; speculative generics
- **Under-abstraction**: Three+ near-identical copies of the same logic introduced in this PR
- **Convention drift**: New code introduces a pattern that conflicts with an established one
  in the same package (e.g., new enum style when neighbouring files use a different one)
- **Missing extension seams** explicitly required by the brief

### Medium
- Cohesion / responsibility issues (god class, anaemic service)
- Naming that obscures intent
- Tests that bind to internals rather than behaviour

## Output format
Return structured findings:

# Review: PR #{PR_NUMBER} (Architecture)
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

### 4.2 — Collect CodeRabbit review (overlaps with superpowers review time)

CodeRabbit runs asynchronously after PR creation. The strategy is to let it run in parallel with
the superpowers reviews and fix cycles, then collect its findings before the final merge decision.

**Step 4.2a — Immediately after dispatching superpowers reviews (Step 4.1), start a background
CodeRabbit poll** using Bash `run_in_background`. This runs concurrently with the superpowers
reviews and any fix cycles, giving CodeRabbit the full duration of Steps 4.1–4.4 to complete
(typically 10–40+ minutes of wall-clock time):

```bash
PR_NUMBER={PR_NUMBER}
OWNER_REPO="rakheen-dama/b2b-strawman"
CR_STATE="missing"
# Poll every 30s for up to 20 iterations (10 min). CodeRabbit usually arrives in 5-10 min.
# This runs in background while superpowers reviews + fix cycles proceed.
for i in $(seq 1 20); do
  REVIEWS=$(gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/reviews --jq '[.[] | select(.user.login == "coderabbitai[bot]")] | length' 2>/dev/null || echo 0)
  INLINE=$(gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/comments --jq '[.[] | select(.user.login == "coderabbitai[bot]")] | length' 2>/dev/null || echo 0)
  if [ "${REVIEWS:-0}" -gt 0 ] || [ "${INLINE:-0}" -gt 0 ]; then
    CR_STATE="present"
    break
  fi
  sleep 30
done
echo "CR_STATE=$CR_STATE after $i iterations"
```

**Step 4.2b — After all superpowers fix cycles complete (end of Step 4.4), collect CodeRabbit
findings.** By this point the background poll has likely completed. Read its output. If
`CR_STATE=present`, collect the findings:

```bash
PR_NUMBER={PR_NUMBER}
OWNER_REPO="rakheen-dama/b2b-strawman"

gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/comments \
  --jq '.[] | select(.user.login == "coderabbitai[bot]") | "[\(.path):\(.line // .original_line)] \(.body)"' \
  > /tmp/cr-inline-$PR_NUMBER.txt 2>/dev/null || true
gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/reviews \
  --jq '.[] | select(.user.login == "coderabbitai[bot]") | .body' \
  > /tmp/cr-reviews-$PR_NUMBER.txt 2>/dev/null || true
gh api repos/$OWNER_REPO/issues/$PR_NUMBER/comments \
  --jq '.[] | select(.user.login == "coderabbitai[bot]") | .body' \
  > /tmp/cr-summary-$PR_NUMBER.txt 2>/dev/null || true
```

If the background poll finished with `CR_STATE=missing` (CodeRabbit didn't arrive in 10 min),
do ONE more check right now — CodeRabbit may have arrived during the fix cycles that ran after
the poll ended:

```bash
REVIEWS=$(gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/reviews --jq '[.[] | select(.user.login == "coderabbitai[bot]")] | length' 2>/dev/null || echo 0)
INLINE=$(gh api repos/$OWNER_REPO/pulls/$PR_NUMBER/comments --jq '[.[] | select(.user.login == "coderabbitai[bot]")] | length' 2>/dev/null || echo 0)
if [ "${REVIEWS:-0}" -gt 0 ] || [ "${INLINE:-0}" -gt 0 ]; then
  CR_STATE="present"
fi
```

If still missing after this final check, mark as DEFERRED. This should be rare — CodeRabbit
typically arrives well within the combined superpowers + fix cycle duration.

Read the three collection files. If any have actionable findings (not just the boilerplate "No
actionable comments were generated" or "Review failed"), dispatch a fixer agent (same pattern as
Step 4.3) to address them. This is a SEPARATE fix cycle from the superpowers fixes.

Record the outcome — this string MUST be pasted verbatim into the PR body in Step 4.5:
- If `CR_STATE=present` and there are actionable findings → `## CodeRabbit: REVIEWED — N findings, addressed in fixer pass`
- If `CR_STATE=present` and no actionable findings → `## CodeRabbit: REVIEWED — no actionable findings`
- If `CR_STATE=missing` after all waits → `## CodeRabbit: DEFERRED — not arrived after full review cycle; superpowers reviews are the merge gate`

### 4.3 — Merge findings & dispatch fixer

Combine the two superpowers reviews into a single deduplicated list. If the same file:line is
flagged by both, keep the more detailed wording.

If NO Critical or High findings exist from either superpowers review, skip the fixer and proceed
directly to Step 4.4.

If ANY Critical or High findings exist, dispatch a **blocking** `general-purpose` subagent to fix
them all in one pass:

```
You are fixing code review findings for PR #{PR_NUMBER} in worktree:
  /Users/rakheendama/Projects/2026/worktree-epic-{ID}

## Superpowers Bug/Quality Findings (PRIMARY — address all Critical and High)
{Paste Agent A findings. If verdict was APPROVE with no findings, write "None".}

## Superpowers Architecture Findings (PRIMARY — address all Critical and High)
{Paste Agent B findings. If verdict was APPROVE with no findings, write "None".}

## Implementation Brief (for context)
Read: /Users/rakheendama/Projects/2026/worktree-epic-{ID}/.epic-brief.md

## Instructions
1. Read each finding carefully. Verify the issue exists in the current code before fixing.
2. For superpowers Critical/High findings: fix ALL of them. If a finding is a false positive,
   explain WHY with evidence (file:line proof).
3. Medium findings: fix only if trivially fixable while you're already in the file. Don't expand scope.
4. After fixing, verify with Tier 1 + Tier 2 ONLY — do NOT run the full verify (`./mvnw clean verify`).
   The orchestrator owns the single full verify on the final commit; a Tier 3 here will be killed when
   your turn ends and wastes ~20 min on a commit the next cycle may supersede.
   - Format: cd /Users/rakheendama/Projects/2026/worktree-epic-{ID}/backend && ./mvnw spotless:apply 2>&1 | tail -3
   - Compile check: ./mvnw compile test-compile -q > /tmp/mvn-fix-{ID}.log 2>&1; echo $?
   - Targeted tests (the classes touching your fix): ./mvnw test -Dtest="{TEST_CLASSES}" -q > /tmp/mvn-fix-{ID}.log 2>&1; echo $?; grep 'Tests run:' /tmp/mvn-fix-{ID}.log | tail -1
5. Commit: git commit -m "fix: address review findings for Epic {ID}"
6. Push: git push

## Report Format
For EACH finding, report one of:
- ✅ FIXED — {what you changed}
- ⏭️ FALSE POSITIVE — {evidence why this is not a real issue}
- ⚠️ DEFERRED — {why this can't be fixed in this PR, e.g., requires architectural change}
```

### 4.4 — Re-review after fix (max 2 cycles)

After the fixer pushes, re-dispatch ONLY Agent A (bug/quality) on the new diff to confirm no
regressions. Do NOT re-dispatch Agent B unless Agent A flags an architectural regression.

If Agent A returns Critical or High findings on the second pass, run ONE more fix cycle (max 2
fix cycles total). If findings persist after 2 cycles, note them in the PR description and STOP
(in auto-merge mode, exit non-zero so the wrapper script knows this slice failed).

### 4.4b — Collect and address CodeRabbit findings

After all superpowers fix cycles are complete (Step 4.4), collect CodeRabbit findings per
Step 4.2b. If CodeRabbit has actionable findings, dispatch a fixer agent to address them
(same pattern as Step 4.3 but with CodeRabbit findings as PRIMARY input). This is a separate
commit from the superpowers fixes. The fixer runs Tier 1 + Tier 2 ONLY, then commits and pushes —
it does NOT run the full verify (the orchestrator's Step 4.6 covers it).

**Batching tip (saves a fix cycle + a CI run):** if the background CodeRabbit poll (Step 4.2a)
has ALREADY reported `CR_STATE=present` by the time you reach Step 4.3, collect CR findings FIRST
and feed them into the SAME fixer as the superpowers findings — one fixer pass, one commit, one
CI re-trigger instead of two. Only fall back to a separate CR fixer cycle when CodeRabbit arrives
after the superpowers fixer has already been dispatched.

### 4.5 — Append review audit trail to PR body (REQUIRED before merge)

The merge-gate hook (`.claude/hooks/pre-pr-merge-gate.sh`) inspects the PR body for proof that
reviews actually ran. **Without this step, merge will be rejected.** Append a `## Code Review Audit Trail`
section to the PR body containing:
- Agent A (bug/quality) **final** verdict block (after fix cycles) — must contain `## Verdict: APPROVE`
  OR a `## Verdict: REQUEST_CHANGES` paired with an explicit "all Critical/High resolved" line.
- Agent B (architecture) **final** verdict block — same rule.
- The CodeRabbit status line from Step 4.2b (`## CodeRabbit: REVIEWED ...` or `## CodeRabbit: DEFERRED ...`).

```bash
# Build the audit trail block from the saved verdicts and CR status
cat > /tmp/pr-{PR_NUMBER}-audit.md <<'EOF'

---

## Code Review Audit Trail

### Superpowers — Bug/Quality (Agent A)
{Paste Agent A's FINAL verdict block verbatim — including the `## Verdict: APPROVE` line and the findings sections.}

### Superpowers — Architecture (Agent B)
{Paste Agent B's FINAL verdict block verbatim.}

### CodeRabbit
{The status line from Step 4.2b — one of:
  ## CodeRabbit: REVIEWED — N findings, addressed in fixer pass
  ## CodeRabbit: REVIEWED — no actionable findings
  ## CodeRabbit: DEFERRED — not arrived after full review cycle; superpowers reviews are the merge gate
}

### Fix Cycles Run
{e.g., "1 fix cycle (Agent A flagged 2 High; addressed in commit abc123)" or "0 (clean approve on first pass)"}
EOF

# Get current PR body, append audit, write back
gh pr view {PR_NUMBER} --json body --jq .body > /tmp/pr-{PR_NUMBER}-body.md
cat /tmp/pr-{PR_NUMBER}-audit.md >> /tmp/pr-{PR_NUMBER}-body.md
gh pr edit {PR_NUMBER} --body-file /tmp/pr-{PR_NUMBER}-body.md
```

The hook checks the PR body for ALL THREE markers before allowing merge:
- Two occurrences of `## Verdict: APPROVE` (one per superpowers reviewer)
- One `## CodeRabbit: REVIEWED` OR `## CodeRabbit: DEFERRED` line

If any marker is missing or malformed, the merge will be denied with a clear reason. Fix the
audit trail and retry — do NOT bypass the hook.

### 4.6 — Orchestrator-owned full verify (the single Tier 3 — run ONCE on the final commit)

This is the ONLY place a full `mvn verify` runs locally. Run it **after the final fix cycle**, on
the final commit, so it is never invalidated by a later fix. Run it in the **background** so it
cannot be killed by a turn ending mid-run, and so it overlaps with the re-reviews and Backend CI.

```bash
# Clear any stale JVMs first (prevents the exit-143 fork crash), then verify in the background.
pkill -9 -f 'worktree-epic-{ID}' 2>/dev/null; pgrep -f surefire | xargs kill -9 2>/dev/null; sleep 3
# Run as run_in_background:true — you are notified on completion; do not poll-block on it.
cd /Users/rakheendama/Projects/2026/worktree-epic-{ID}/backend && ./mvnw clean verify -q > /tmp/mvn-{ID}-final.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "FULL BUILD SUCCESS"; grep 'Tests run:' /tmp/mvn-{ID}-final.log | tail -1; else echo "FULL BUILD FAILED (exit $MVN_EXIT)"; FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd,); if [ -n "$FAILED" ]; then echo "REAL FAILED TESTS: $FAILED"; else echo "NO NAMED TEST FAILURES — env/fork crash (143); re-kill zombies + re-run once"; fi; fi
```

Interpreting the result:
- **FULL BUILD SUCCESS** → local merge gate satisfied.
- **REAL FAILED TESTS: <names>** → genuine regression. Dispatch a fixer (Tier-1/Tier-2 only), push,
  re-run THIS step. (A push also re-triggers Backend CI.)
- **NO NAMED TEST FAILURES** with non-zero exit → environmental SIGTERM/fork crash, not code. Re-kill
  zombies, wait 3s, re-run this step once. Do not treat it as a code failure.

While this runs, the two re-reviews (Step 4.4) and Backend CI proceed in parallel — none of them
block each other.

## Step 5 — Merge

### Auto-Merge Mode (prompt contains "auto-merge")

Merge immediately if ALL of the following are true (after fix cycles):
- Both superpowers reviews (bug/quality AND architecture) returned **APPROVE** OR all their
  Critical and High findings were fixed
- **Local full verify green** — the Step 4.6 background run reported `FULL BUILD SUCCESS` (exit 0)
- **Backend CI green** — `gh pr checks {n}` shows the `Backend` job as `pass` (and any other
  non-skipped required check passes). The local verify and CI run the same `mvn verify`; require
  BOTH because CI is the source of truth but the path-filtered Backend job does not always trigger
  (see the retarget-CI gotcha) — if CI shows no Backend run, push an empty/synchronize commit to
  force it rather than trusting the local verify alone.
- The PR's GitHub merge state is `MERGEABLE` (`gh pr view {n} --json mergeStateStatus`; `UNSTABLE`
  is fine only while a non-required check is still pending — confirm Backend itself is `pass`)
- **Step 4.5 audit trail has been written to the PR body** — the merge-gate hook will reject
  the merge otherwise. Verify with: `gh pr view {n} --json body --jq .body | grep -c "## Verdict: APPROVE"`
  must return 2 (or matching "all Critical/High resolved" line), and `grep -E "## CodeRabbit: (REVIEWED|DEFERRED)"` must match once.

CodeRabbit findings, if any, were addressed in Step 4.4b. CodeRabbit runs in background during
the full review cycle (typically 10–40+ min of wall-clock). DEFERRED is only used if CodeRabbit
genuinely never arrived after the entire superpowers + fix cycle duration.

If any Critical or High finding from EITHER superpowers review remains unresolved after 2 fix
cycles: **STOP** and report the unresolved issues. Do not merge. Exit with a clear error message
so the wrapper script knows this slice failed.

### Manual Mode (no "auto-merge" in prompt)

**Ask the user before merging.** Do not auto-merge.

### Merge Procedure (both modes)

```bash
# 1. Remove the worktree FIRST. gh pr merge --delete-branch tries to switch the local branch to
#    main after merging; if main is checked out in another worktree (it always is — the main repo),
#    that step fails with "'main' is already used by worktree". Removing the epic worktree first
#    avoids the conflict. (The GitHub-side merge still succeeds even if the local step errors, but
#    do it in this order to keep the run clean.)
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../worktree-epic-<N> --force

# 2. Merge the PR (run from the main repo, not the worktree)
gh pr merge {PR_NUMBER} --squash --delete-branch

# 3. If the remote/local branch wasn't auto-deleted (e.g. the post-merge checkout errored),
#    clean it up explicitly:
git branch -D epic-<N>/<branch-name> 2>/dev/null
git push origin --delete epic-<N>/<branch-name> 2>/dev/null

# 4. Sync main
git pull origin main
git fetch --prune
```

Then update task status — **for EACH slice implemented in this PR** (all slices in full-epic mode, one slice in single-slice mode), update **ALL FOUR locations**:
1. Mark the slice **Done** in the **Detail Section** row (the row starting with `| **{SLICE_ID}** |` under the epic's Tasks heading — add `**Done** (PR #{PR_NUMBER})` in the last column). **THIS IS THE MOST CRITICAL** — the `run-phase.sh` script checks these rows to determine completion. If you skip this, the phase script will re-run the epic or crash.
2. Mark the slice **Done** in the **Implementation Order table** row (the row with `| {order} | Epic {N} | {SLICE_ID} |` — update the last column).
3. Mark the epic **Done** in the **Epic Overview table** at the top of the task file (when all slices in the epic are done — which is always true in full-epic mode).
4. Update the status column in `TASKS.md` overview.

In **full-epic mode**, you must update ALL slices (e.g., both 450A and 450B) in a single pass. Do not mark only one slice.

- Commit and push from main repo

## Guardrails

- **Context hygiene**: Orchestrator NEVER reads architecture/ARCHITECTURE.md, full task files, or subdirectory CLAUDE.md files
- **Brief is the contract**: Builder works from the brief only — if the brief is wrong, re-run the scout, don't have the builder explore
- **Build output stays in files**: All build/test output goes to `/tmp/` log files — only summaries enter agent context
- **No over-implementation**: Builder implements ONLY the brief's task list
- **Tier split is non-negotiable**: subagents (builder + fixers) stop at Tier 2; the orchestrator runs the single Tier 3 (Step 4.6) once on the final commit. No subagent runs `mvn clean verify`.
- **PR early, on Tier-2 green**: the builder opens the PR as soon as targeted tests pass, so Backend CI runs in parallel with reviews + the orchestrator verify (not as tail latency). Do NOT open it on "it compiles" — Tier 2 must be green first.
- **Merge gate = local full verify AND Backend CI both green** (plus reviews + audit trail). Fix-on-either: if local verify OR CI reports a real failure, fix (Tier-2 only), push, both re-run.
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
