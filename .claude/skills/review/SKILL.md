---
name: review
description: Review a PR for bugs, security issues, convention violations, and tenant isolation leaks. Pass a PR number (e.g. /review 101). Optionally add "fix" to auto-fix issues (e.g. /review 101 fix).
---

# PR Review Workflow

Review a pull request against project conventions and known pitfalls. Produces structured findings. Optionally dispatches a fix agent.

## Arguments

- **Required**: PR number (e.g., `/review 101`)
- **Optional**: `fix` — auto-fix critical/high issues after review (e.g., `/review 101 fix`)

## Architecture

You are the **orchestrator**. You dispatch a review agent and present findings. If `fix` mode is requested and issues are found, you dispatch a fix agent.

**Context rules**:
- Read CLAUDE.md files yourself (they're small, ~12KB each) — you need them to validate findings
- NEVER read architecture/ARCHITECTURE.md or task files — not needed for code review
- The PR diff is the primary input — fetch it via `gh pr diff`

## Step 0 — Gather PR Metadata

```bash
gh pr view {PR_NUMBER} --json title,headRefName,baseRefName,changedFiles,additions,deletions,state
```

If the PR is closed/merged, inform the user and stop (unless they want to review anyway).

Determine scope from changed files:
- Files under `backend/` → backend scope
- Files under `frontend/` → frontend scope
- Both → both

## Step 1 — Read Conventions

Based on scope, read the relevant CLAUDE.md files yourself:
- Backend → `backend/CLAUDE.md`
- Frontend → `frontend/CLAUDE.md`
- Both → read both

These are small (~250 lines each) and you need them to validate the review agent's findings.

## Step 2 — Dispatch Review Agent

Launch a **blocking** `general-purpose` subagent with `model: "opus"` (quality gate — do NOT downgrade). Write the diff to a file to keep it out of the orchestrator's context:

```bash
gh pr diff {PR_NUMBER} > /tmp/pr-{PR_NUMBER}.diff
```

Then dispatch:

```
You are reviewing PR #{PR_NUMBER} for the DocTeams multi-tenant SaaS platform.

## Setup
1. Read the diff: /tmp/pr-{PR_NUMBER}.diff
2. Read conventions: backend/CLAUDE.md {and/or frontend/CLAUDE.md based on scope}

## What to Check

### Critical (blocks merge)
- **Tenant isolation leaks**: Missing @FilterDef/@Filter on new entities, using findById()
  instead of findOneById() (bypasses Hibernate @Filter), missing tenant_id column,
  missing RLS policy for shared-schema tables
- **Security**: Missing @PreAuthorize, privilege escalation, SQL injection (especially
  in native queries — never use string concat, use set_config for session vars),
  exposed internal endpoints, missing access control checks
- **Data corruption**: Missing @Transactional, race conditions, incorrect cascade types,
  missing NOT NULL constraints on required fields

### High (should fix before merge)
- **Convention violations**: Patterns from CLAUDE.md anti-patterns section — includes
  things like using ThreadLocal instead of ScopedValue, OSIV issues, wrong exception
  patterns, missing bean validation annotations
- **Test gaps**: New public endpoints/methods without test coverage, integration tests
  missing tenant isolation assertions, tests without proper @BeforeAll setup
  (provisionTenant, planSyncService.syncPlan for Pro tier)
- **Frontend/backend parity**: Backend allows something that frontend doesn't gate
  (or vice versa) — check permission logic matches

### Medium (nice to fix)
- **Code quality**: Unnecessary complexity, dead code, duplicated logic, missing error
  handling at system boundaries
- **Naming**: Inconsistent with existing patterns (check existing similar files)

### Low (nits — mention but don't block)
- Style inconsistencies that spotless/lint didn't catch
- Minor documentation gaps

## Output Format

Return findings as structured markdown:

# Review: PR #{PR_NUMBER} — {PR_TITLE}

## Verdict: {APPROVE | REQUEST_CHANGES | COMMENT}

## Critical
{If none: "None found."}
- **[{file}:{line}]** {Issue description}
  → Fix: {Specific suggestion}

## High
{Same format}

## Medium
{Same format}

## Low
{Same format}

## Summary
- Files reviewed: {count}
- Lines changed: +{additions} / -{deletions}
- Issues: {critical_count} critical, {high_count} high, {medium_count} medium, {low_count} low
- {1-2 sentence overall assessment}

IMPORTANT:
- Report ONLY issues you are confident about (>80% sure it's a real problem)
- Include the specific file path and line number for every finding
- For each finding, suggest a concrete fix (not just "fix this")
- Do NOT report style issues that spotless/eslint would catch
- Do NOT flag things that are clearly intentional patterns in the codebase
```

## Step 3 — Present Findings

Show the review agent's output to the user. Highlight the verdict and critical/high counts.

If there are no critical or high issues → recommend merge.

## Step 4 — Auto-Fix (only if `fix` mode was requested AND issues exist)

If the user passed `fix` and there are critical or high issues:

1. Identify the worktree or branch for this PR:
```bash
gh pr view {PR_NUMBER} --json headRefName -q .headRefName
```

2. Check if a worktree exists for this branch, or check it out:
```bash
git worktree list | grep "{BRANCH_NAME}" || git fetch origin {BRANCH_NAME} && git worktree add ../review-fix-{PR_NUMBER} {BRANCH_NAME}
```

3. Dispatch a **blocking** `general-purpose` fix agent with `model: "opus"` (must evaluate findings critically — skip false positives):
```
Fix the following review findings in the worktree at {WORKTREE_PATH}.

## Findings to Fix
{Paste only CRITICAL and HIGH findings from the review}

## Conventions
Read: backend/CLAUDE.md {and/or frontend/CLAUDE.md}

## Build & Verify After Fixing
Backend:
  cd {WORKTREE_PATH}/backend
  ./mvnw spotless:apply 2>&1 | tail -3
  ./mvnw clean verify -q > /tmp/mvn-review-fix.log 2>&1; MVN_EXIT=$?; if [ $MVN_EXIT -eq 0 ]; then echo "BUILD SUCCESS"; else echo "BUILD FAILED"; grep -E '\[ERROR\]' /tmp/mvn-review-fix.log | head -20; fi

Frontend:
  cd {WORKTREE_PATH}/frontend
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm install > /dev/null 2>&1
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run lint > /tmp/lint-review-fix.log 2>&1; if [ $? -ne 0 ]; then echo "LINT FAILED"; tail -20 /tmp/lint-review-fix.log; fi
  NODE_OPTIONS="" /opt/homebrew/bin/pnpm run build > /tmp/build-review-fix.log 2>&1; if [ $? -ne 0 ]; then echo "BUILD FAILED"; tail -30 /tmp/build-review-fix.log; fi

## After Fixing
- Stage only changed files: git add <specific files>
- Commit: git commit -m "fix: address review findings for PR #{PR_NUMBER}"
- Push: git push

Report what was fixed and what (if anything) couldn't be fixed.
```

4. After the fix agent completes, clean up the worktree if we created one:
```bash
# Only if we created the worktree (not if it already existed)
cd /Users/rakheendama/Projects/2026/b2b-strawman
git worktree remove ../review-fix-{PR_NUMBER} --force 2>/dev/null
```

## Step 5 — Final Report

Show the user:
- Original verdict
- Issues found (by severity)
- If fix mode: what was fixed, what wasn't, whether the build passes
- Recommendation: merge / needs more work / needs manual review

## Guardrails

- **Confidence filter**: Only report issues the agent is >80% sure about. False positives waste more time than they save.
- **No architecture/ARCHITECTURE.md reads**: Review is about code quality, not architecture compliance.
- **Diff to file**: Write the diff to `/tmp/` so it stays out of the orchestrator's context.
- **Build output to file**: All build commands redirect to `/tmp/` log files — never stream full Maven output.
- **Don't over-fix**: In fix mode, only address critical and high issues. Leave medium/low for the author.
- **Preserve worktrees**: If a worktree for this branch already exists (e.g., from `/epic`), use it — don't create a duplicate.
