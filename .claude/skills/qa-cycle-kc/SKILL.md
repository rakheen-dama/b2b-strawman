---
name: qa-cycle-kc
description: Run an autonomous QA cycle against the Keycloak dev stack — dispatches QA, Product, Dev, and Infra subagents in a loop until the lifecycle scenario passes end-to-end. Uses dev ports (3000/8080/8443/8180) with real Keycloak authentication. Usage - /qa-cycle-kc <scenario-file> [gap-report] [--resume]
---

# QA Cycle (Keycloak Dev Stack) — In-Session Orchestration

Run all QA cycle agent turns directly in this session against the **Keycloak dev stack** (not the E2E mock-auth stack). Each agent role (QA, Product, Dev, Infra) is dispatched as a subagent via the Agent tool. You (the orchestrator) inspect results between turns and adapt when things go wrong.

## Environment — Keycloak Dev Stack

| Service | URL | Notes |
|---------|-----|-------|
| Frontend | http://localhost:3000 | Keycloak auth mode |
| Backend | http://localhost:8080 | Spring profile: `local,keycloak` |
| Gateway (BFF) | http://localhost:8443 | OAuth2 session management |
| Keycloak | http://localhost:8180 | Admin: admin/admin |
| Postgres | localhost:5432 | User: postgres |
| Mailpit UI | http://localhost:8025 | SMTP on 1025 |
| LocalStack | localhost:4566 | S3 |

**Compose file**: `compose/docker-compose.yml` with `compose/.env.keycloak`
**Start**: `cp compose/.env.keycloak compose/.env && bash compose/scripts/dev-up.sh --all`
**Stop**: `bash compose/scripts/dev-down.sh`

### Keycloak Users (after seeding)

| User | Email | Password | Role |
|------|-------|----------|------|
| Alice | alice@example.com | password | owner |
| Bob | bob@example.com | password | admin |
| Carol | carol@example.com | password | member |
| Platform Admin | padmin@docteams.local | password | platform-admin |

**Organization**: acme-corp

### Keycloak Login Flow (Playwright)

Authentication in Keycloak mode uses a multi-redirect OIDC flow:

1. Navigate to any protected route (e.g., `http://localhost:3000/dashboard`)
2. Frontend middleware detects no `SESSION` cookie → redirects to `http://localhost:8443/oauth2/authorization/keycloak`
3. Gateway initiates OAuth2 code flow → redirects browser to Keycloak login page
4. **Fill in username** (e.g., `alice@example.com`) and **password** (`password`)
5. Submit the Keycloak login form
6. Keycloak authenticates → redirects back to gateway callback → gateway creates session → redirects to frontend
7. Frontend now has `SESSION` cookie → user is authenticated

**IMPORTANT**: The Keycloak login page is served by Keycloak itself (custom DocTeams theme). The form fields are typically `#username` and `#password` with a submit button `#kc-login`.

### Prerequisites

The gateway container references Keycloak by Docker hostname (`keycloak`). When it redirects the browser to the Keycloak login page, the URL contains `keycloak:8180`. For this to work from the host machine, add to `/etc/hosts`:

```
127.0.0.1 keycloak
```

The Infra agent should verify this before starting the stack.

## Why In-Session (not bash script)

The bash script approach is terminal on failure. In-session orchestration gives you:

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
IF Dev Stack = "Not running" AND OPEN blockers tagged "Infra":
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
{IF seed fix: Read the infra-seed prompt at scripts/qa-cycle/prompts/infra-seed.md (if it exists)}
{IF rebuild: Rebuild the dev stack after Dev fixes have been merged.}

## Your Job
{IF first start: Start the Keycloak dev stack, seed Keycloak and backend data.}
{IF rebuild: Rebuild specific services after Dev fixes.}

## Environment — Keycloak Dev Stack
- Compose file: compose/docker-compose.yml with compose/.env.keycloak
- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Gateway (BFF): http://localhost:8443
- Keycloak: http://localhost:8180 (admin/admin)
- Postgres: localhost:5432 (user: postgres, pass: postgres, db: docteams)
- Mailpit: http://localhost:8025
- LocalStack: localhost:4566

## Prerequisites Check
1. Verify `/etc/hosts` contains `127.0.0.1 keycloak` — if not, report it and exit.
2. Verify Docker is running.

## Starting the Stack (first time)
1. Copy env file: `cp compose/.env.keycloak compose/.env`
2. Start all services: `bash compose/scripts/dev-up.sh --all`
3. Wait for all services to be healthy (the script does this).
4. Run Keycloak bootstrap: `bash compose/scripts/keycloak-bootstrap.sh`
5. Run Keycloak seed: `bash compose/scripts/keycloak-seed.sh`
6. Provision backend org via internal API:
   ```bash
   # Provision the org (matches keycloak-seed.sh org alias)
   curl -s -o /dev/null -w "%{http_code}" \
     -X POST "http://localhost:8080/internal/orgs/provision" \
     -H "Content-Type: application/json" \
     -H "X-API-KEY: local-dev-api-key-change-in-production" \
     -d '{"clerkOrgId":"acme-corp","orgName":"Acme Corp","verticalProfile":"accounting-za"}'

   # Sync plan to PRO
   curl -s -o /dev/null -w "%{http_code}" \
     -X POST "http://localhost:8080/internal/orgs/plan-sync" \
     -H "Content-Type: application/json" \
     -H "X-API-KEY: local-dev-api-key-change-in-production" \
     -d '{"clerkOrgId":"acme-corp","planSlug":"pro"}'
   ```
7. Sync members (alice, bob, carol) via internal API — use Keycloak user IDs:
   ```bash
   # Get Keycloak user IDs
   KCADM="docker exec b2b-keycloak /opt/keycloak/bin/kcadm.sh"
   $KCADM config credentials --server http://localhost:8180 --realm master --user admin --password admin
   ALICE_KC_ID=$($KCADM get "users?username=alice@example.com&exact=true" -r docteams | jq -r '.[0].id')
   BOB_KC_ID=$($KCADM get "users?username=bob@example.com&exact=true" -r docteams | jq -r '.[0].id')
   CAROL_KC_ID=$($KCADM get "users?username=carol@example.com&exact=true" -r docteams | jq -r '.[0].id')

   # Sync each member
   for entry in "$ALICE_KC_ID alice@example.com Alice_Owner owner" "$BOB_KC_ID bob@example.com Bob_Admin admin" "$CAROL_KC_ID carol@example.com Carol_Member member"; do
     set -- $entry
     curl -s -o /dev/null -w "%{http_code}" \
       -X POST "http://localhost:8080/internal/members/sync" \
       -H "Content-Type: application/json" \
       -H "X-API-KEY: local-dev-api-key-change-in-production" \
       -d "{\"clerkOrgId\":\"acme-corp\",\"clerkUserId\":\"$1\",\"email\":\"$2\",\"name\":\"$(echo $3 | tr '_' ' ')\",\"avatarUrl\":\"\",\"orgRole\":\"$4\"}"
   done
   ```

## Rebuilding (after Dev fixes)
1. `bash compose/scripts/dev-rebuild.sh {services}` (e.g., `backend`, `frontend`, or both)
2. Wait for health checks to pass.
3. Clear NEEDS_REBUILD from status.md.

## State File
Read and update: qa_cycle/status.md

## Guard Rails
- Commit directly to {BRANCH} (infra changes, not feature PRs)
- Run backend tests if you change seeder code
- Read backend/CLAUDE.md before making backend changes
- If rebuild fails after 2 attempts, report the error and exit
```

### QA Agent

Launch a **blocking** `general-purpose` subagent:

```
You are the **QA Agent** for the QA cycle on branch `{BRANCH}`.

## Your Job
Execute the lifecycle script via Playwright MCP against the **Keycloak dev stack** (http://localhost:3000).
Record pass/fail for each checkpoint. Stop when you hit a blocker.

## Before You Start
1. Read `qa_cycle/status.md` — check "QA Position" for where to resume.
2. Read the scenario file: `{SCENARIO_FILE}`
3. Skip to the day/checkpoint in QA Position.
4. Check which gaps are FIXED — verify those first.

## Keycloak Authentication via Playwright
To log in as a user (e.g., Alice):
1. Navigate to `http://localhost:3000/dashboard` (or any protected route)
2. You will be redirected through the gateway to the Keycloak login page
3. Wait for the Keycloak login form to appear (look for `#username` or `input[name="username"]`)
4. Fill in username: `alice@example.com`
5. Fill in password: `password`
6. Click the login button (`#kc-login` or `input[type="submit"]`)
7. Wait for redirect back to the frontend — you should land on `/org/acme-corp/dashboard` or similar
8. Verify you see the authenticated UI (sidebar, user avatar, etc.)

To switch users:
1. Log out first (if the app has a sign-out button, click it; otherwise clear cookies)
2. Follow the login steps above with the new user's credentials

**Available users:**
| User | Email | Password | Role |
|------|-------|----------|------|
| Alice | alice@example.com | password | owner |
| Bob | bob@example.com | password | admin |
| Carol | carol@example.com | password | member |

**Organization slug**: acme-corp

## Execution Rules
- One day at a time. Complete all checkpoints before moving to next day.
- Record every checkpoint: ID, Result (PASS/FAIL/PARTIAL), Evidence
- On blocker: Stop. Log it. Exit. Do NOT skip ahead.
- On non-cascading bug: Log it and continue.
- Check console errors after each page navigation.
- Take screenshots on failures for evidence.

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

### 3. Build & Verify
**Backend** (if in scope):
  cd backend
  ./mvnw spotless:apply 2>&1 | tail -3
  ./mvnw compile test-compile -q > /tmp/mvn-fix.log 2>&1
  # Targeted tests, then full verify before PR

**Frontend** (if in scope):
  cd frontend
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/lint-fix.log 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build > /tmp/build-fix.log 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm test > /tmp/test-fix.log 2>&1

### 4. Commit & Push
git add <specific files>
git commit -m "fix({GAP_ID}): {short description}"
git push -u origin fix/{GAP_ID}

### 5. Create PR
gh pr create --base {BRANCH} --title "Fix {GAP_ID}: {summary}" --body "..."

### 6. Self-Review, then Merge
gh pr merge {PR_NUMBER} --squash --delete-branch
git checkout {BRANCH} && git pull origin {BRANCH}

### 7. Update Status
Set gap status to FIXED in qa_cycle/status.md.
If backend/seed/docker changed: add NEEDS_REBUILD to Current State.
Add log entry. Commit and push to {BRANCH}.

## Guard Rails
- One fix per PR
- Green build required before PR
- Don't touch code outside the spec's scope
- Max 3 build attempts — report failure and exit if still broken
- If spec is wrong, note in status.md and exit

## Environment
- Postgres host: b2mash.local:5432
- LocalStack host: b2mash.local:4566
- pnpm: /opt/homebrew/bin/pnpm
- NODE_OPTIONS="" needed before pnpm commands
- SHELL=/bin/bash prefix for docker build
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
| Keycloak login failed | Check Keycloak health, check `/etc/hosts`, check gateway session store |

### Retry Tracking

Keep a mental counter (or note in status.md log) of retries per gap:

```
| GAP-008 | attempt 1 | FIXED → VERIFIED |
| GAP-008A | attempt 1 | FIXED → REOPENED |
| GAP-008A | attempt 2 | FIXED → VERIFIED |
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

## Differences from /qa-cycle (E2E Mock-Auth)

| Aspect | /qa-cycle (E2E) | /qa-cycle-kc (Keycloak) |
|--------|-----------------|-------------------------|
| Frontend | http://localhost:3001 | http://localhost:3000 |
| Backend | http://localhost:8081 | http://localhost:8080 |
| Auth | Mock IDP (port 8090) | Keycloak (port 8180) via Gateway BFF (8443) |
| Login flow | Navigate to `/mock-login`, click Sign In | OIDC redirect → Keycloak login form → fill email/password |
| Compose file | `docker-compose.e2e.yml` | `docker-compose.yml` + `.env.keycloak` |
| Start script | `e2e-up.sh` | `dev-up.sh --all` |
| Seed | Docker seed container (automatic) | `keycloak-bootstrap.sh` + `keycloak-seed.sh` + internal API calls |
| Postgres | localhost:5433, db: app | localhost:5432, db: docteams |
| Prerequisite | None | `/etc/hosts` entry for `keycloak` |

## Guardrails

- **Orchestrator stays lean**: Never read the scenario file, ARCHITECTURE.md, or CLAUDE.md subdirectories
- **State is in status.md**: All decisions derive from reading this file
- **Sequential agent turns**: One agent at a time, inspect result, then decide next
- **Dev uses worktree isolation**: Prevents polluting the parent branch with broken code
- **Infra commits directly**: Seed/rebuild changes go straight to the parent branch
- **No blind retries**: If something fails, diagnose WHY before retrying
- **Commit after every turn**: Each agent commits its state changes before returning
- **Keycloak session awareness**: If QA agent reports auth errors, check gateway health and Keycloak status before retrying
