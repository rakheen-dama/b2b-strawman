---
name: template-epic
description: Build a slice of the java-keycloak-multitenant-saas template — scout→builder pipeline with auto-merge. Pass the slice ID (e.g. /template-epic T2A).
---

# Template Epic — Scout→Builder Pipeline

Build a single slice of the `java-keycloak-multitenant-saas` starter template.

## Paths

| What | Path |
|------|------|
| Template repo | `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas` |
| DocTeams repo (reference) | `/Users/rakheendama/Projects/2026/b2b-strawman` |
| Task file | `{TEMPLATE}/tasks/TASKS.md` |
| Architecture | `{TEMPLATE}/docs/architecture.md` |
| Requirements | `{TEMPLATE}/docs/requirements.md` |
| ADRs | `{TEMPLATE}/adr/ADR-T*.md` |
| CLAUDE.md | `{TEMPLATE}/CLAUDE.md` |

Where `{TEMPLATE}` = `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas`

## Arguments

Slice ID: e.g., `/template-epic T2A`

## Architecture

You are the **orchestrator**. You stay lean and delegate:

1. **Scout agent** — reads template docs + DocTeams reference patterns → writes `.epic-brief.md` to a worktree
2. **Builder agent** — reads ONLY the brief → implements, tests, commits, pushes, creates PR

The orchestrator NEVER reads architecture docs or full task files. That's the scout's job.

## Step 0 — Validate (Orchestrator, Lightweight)

1. Extract the slice ID from user input (e.g., `T2A`).
2. Read `{TEMPLATE}/tasks/TASKS.md` — ONLY the Epic Overview table (~15 lines).
3. Find the epic row for this slice. Extract: **name**, **scope**, **deps**, **effort**.
4. If the epic is marked **Done**, stop.
5. Verify dependencies are **Done**.
6. Check for existing branches:
```bash
cd /Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas
git branch -a | grep -i "{SLICE}" 2>/dev/null || true
```

## Step 1 — Create Worktree (Orchestrator)

```bash
cd /Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas
git worktree add ../worktree-template-{SLICE} -b template-{SLICE}/{descriptive-name}
```

## Step 2 — Dispatch Scout Agent

Launch a **blocking** `general-purpose` subagent:

```
You are a **codebase scout** preparing an implementation brief for slice {SLICE} of the java-keycloak-multitenant-saas template.

Write a self-contained brief to:
  /Users/rakheendama/Projects/2026/worktree-template-{SLICE}/.epic-brief.md

## Research Steps

### 1. Task Specifications
Read `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas/tasks/TASKS.md`.
Find Epic {EPIC} and extract ALL task descriptions for slice {SLICE}.
Include exact file paths, field names, endpoints, and test expectations.

### 2. Architecture Context
Read `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas/docs/architecture.md`.
Find sections relevant to this slice. Extract entity definitions, API endpoints,
sequence diagrams, migration SQL, and implementation guidance.

### 3. ADRs
Read any ADRs referenced by the tasks from `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas/adr/`.

### 4. Conventions
Read `/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas/CLAUDE.md`.
Extract ALL conventions, the Spring Boot 4 gotchas table, and the package structure.

### 5. Reference Patterns (CRITICAL — from DocTeams)
Find similar implementations in DocTeams at `/Users/rakheendama/Projects/2026/b2b-strawman/`:

**For backend slices** — find and include FULL source of:
- Entity (similar domain) → from backend/src/main/java/.../
- Repository → with custom queries
- Service → with @Transactional, ScopedValue access
- Controller → with DTO records, response patterns
- Integration test → with setup and assertions

**For gateway slices** — find and include FULL source of:
- GatewaySecurityConfig → from gateway/src/main/java/.../config/
- application.yml → route config
- BFF controller (if any)

**For frontend slices** — find and include FULL source of:
- Server component → data fetching pattern
- Client component → form handling, Shadcn UI
- Server action → API call pattern
- Page layout → route group structure

Use the DocTeams CLAUDE.md reference table in the template's CLAUDE.md to find the right
reference files for each domain (multitenancy, gateway, access requests, portal, etc.).

### 6. Existing Template Code
Read key existing files in the template worktree that this slice builds on.
List the current directory structure to understand what's already implemented.

### 7. File Plan
Determine exact file paths for ALL new files to create.
Base package: `io.github.rakheendama.starter`

## Brief Format

Write to `/Users/rakheendama/Projects/2026/worktree-template-{SLICE}/.epic-brief.md`:

---
# Implementation Brief: Slice {SLICE} — {TITLE}

## Scope
{Backend | Gateway | Frontend | Docs}

## Tasks
{Numbered list with FULL descriptions from TASKS.md}

## File Plan
### Create
{Exact paths with one-line purpose}
### Modify
{Exact paths with what to change — if any}

## Reference Patterns
### {Pattern Type} (from {source file path})
```{lang}
{FULL source code}
```

## Conventions
{ALL rules from CLAUDE.md — Spring Boot 4 gotchas VERBATIM}

## Architecture Context
{Relevant entity fields, API endpoints, migration SQL from architecture doc}

## Build & Verify

### Backend
All commands from: cd /Users/rakheendama/Projects/2026/worktree-template-{SLICE}/backend

**Kill zombies first:**
  pgrep -f 'surefire.*worktree-template' | xargs kill 2>/dev/null || true; sleep 2

**Tier 1 — Compile (~30s):**
  ./mvnw compile test-compile -q > /tmp/mvn-template-{SLICE}.log 2>&1; [ $? -eq 0 ] && echo "COMPILE OK" || (echo "COMPILE FAILED"; grep '\[ERROR\]' /tmp/mvn-template-{SLICE}.log | head -20)

**Tier 2 — Targeted tests (~2min):**
  ./mvnw test -Dtest="{NEW_TEST_CLASSES}" -q > /tmp/mvn-template-{SLICE}.log 2>&1; [ $? -eq 0 ] && echo "TESTS PASSED" || (echo "TESTS FAILED"; grep -rl 'failures="[1-9]' target/surefire-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||')

**Tier 3 — Full verify (~5min, run ONCE before commit):**
  ./mvnw clean verify -q > /tmp/mvn-template-{SLICE}.log 2>&1; [ $? -eq 0 ] && echo "BUILD SUCCESS" || (echo "BUILD FAILED"; grep '\[ERROR\]' /tmp/mvn-template-{SLICE}.log | head -20)

### Gateway
Same commands but from: cd /Users/rakheendama/Projects/2026/worktree-template-{SLICE}/gateway

### Frontend
  cd /Users/rakheendama/Projects/2026/worktree-template-{SLICE}/frontend
  NODE_OPTIONS="" pnpm install > /dev/null 2>&1
  NODE_OPTIONS="" pnpm run build > /tmp/build-template-{SLICE}.log 2>&1
  NODE_OPTIONS="" pnpm test > /tmp/test-template-{SLICE}.log 2>&1

IMPORTANT: Include FULL code for reference patterns. The builder's ONLY input is this brief.
---
```

## Step 3 — Dispatch Builder Agent

Verify the brief exists, then launch a **blocking** `general-purpose` subagent with `model: "opus"`:

```
You are implementing **Slice {SLICE}** of the java-keycloak-multitenant-saas template.

Worktree: /Users/rakheendama/Projects/2026/worktree-template-{SLICE}

## First Step
Read: /Users/rakheendama/Projects/2026/worktree-template-{SLICE}/.epic-brief.md

## Workflow

### 1. Implement
- Follow the File Plan exactly
- Adapt Reference Patterns (don't copy DocTeams variable names or package names)
- Base package: io.github.rakheendama.starter (NOT io.b2mash.b2b.b2bstrawman)
- Respect ALL conventions from the brief

### 2. Build & Verify (Tiered)
Follow the build strategy from the brief. Always go Tier 1→2→3.
Max 3 Tier-3 attempts. If still failing, stop and report.

### 3. Commit & Push
- Stage only files you changed: git add <specific files>
- Commit: git commit -m "feat(template-{SLICE}): {description}"
- Push: git push -u origin template-{SLICE}/{branch-name}

### 4. Create PR
gh pr create --title "Slice {SLICE}: {TITLE}" --body "$(cat <<'EOF'
## Summary
{What this slice implements}

## Changes
{Key files added/modified}

## Test plan
{Build commands and what was verified}
EOF
)"

Report the PR URL when done.
```

## Step 4 — Review & Merge (Orchestrator)

1. Read the PR diff: `gh pr diff {PR_NUM} | head -500`
2. Launch a **code-reviewer** agent:
```
Review PR #{PR_NUM} for the java-keycloak-multitenant-saas template.
Check: compilation correctness, convention adherence, no DocTeams-specific code leaked,
Spring Boot 4 imports correct, base package is io.github.rakheendama.starter.
```
3. If review passes → merge immediately (auto-merge mode):
```bash
cd /Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas
gh pr merge {PR_NUM} --squash --delete-branch
git pull origin main
```
4. If review finds issues → dispatch builder to fix, then re-review. Max 2 fix cycles.

## Step 5 — Cleanup & Mark Done (Orchestrator)

1. Remove worktree:
```bash
cd /Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas
git worktree remove ../worktree-template-{SLICE} 2>/dev/null || true
```

2. Mark epic as Done in `{TEMPLATE}/tasks/TASKS.md`:
   - Update the Status column for this epic's row to `**Done**`
   - Only mark Done when ALL slices in the epic are complete
   - For multi-slice epics: mark individual slice completion in the Slice table

3. Update `{TEMPLATE}/CLAUDE.md` Current Progress table.

4. Report completion with: slice ID, PR number, files created, tests passing.
