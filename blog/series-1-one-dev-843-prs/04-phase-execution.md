# Phase Execution: From Architecture Doc to 17 Merged PRs Without Touching the Keyboard

*Part 4 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents.*

---

Phase 8 was the stress test.

Rate Cards, Budgets, and Profitability. Seventeen slices. A 3-level billing rate hierarchy (org → project → customer), cost rates, budget configuration with alerts, and four profitability reports (project, customer, organization, utilization). Frontend and backend. New entities, new migrations, new services, new pages, new test suites.

This phase would tell me if the automation actually worked at scale — or if it would crumble under the weight of interconnected features that depended on each other.

It ran for about six hours. Seventeen slices in, seventeen PRs merged, zero manual code edits. Here's how.

## The Input: Architecture + Task File

Every phase starts with two documents I write by hand (with help from the `/architecture` skill):

**The architecture doc** (`architecture/phase8-rate-cards-budgets-profitability.md`) contains:
- API endpoint designs with request/response shapes
- Database schema (migrations, entity relationships)
- ADRs explaining key tradeoffs
- Sequence diagrams for complex flows

Phase 8 had 5 ADRs:
- ADR-039: Rate hierarchy (org defaults → project overrides → customer overrides)
- ADR-040: Snapshotting (when a time entry is created, it captures the *current* rate, so rate changes don't retroactively alter history)
- ADR-041: Multi-currency (support it in the schema, but default to org currency for now)
- ADR-042: Single budget per project (not per-phase or per-task — keep it simple)
- ADR-043: Margin-aware profitability (revenue minus cost, not just revenue)

**The task file** (`tasks/phase8-rate-cards-budgets-profitability.md`) breaks the architecture into slices:

```markdown
| Slice | Scope    | Description                           | Dependencies |
|-------|----------|---------------------------------------|-------------|
| 67A   | Backend  | OrgSettings entity + migration        | None        |
| 67B   | Backend  | OrgSettings service + controller      | 67A         |
| 68A   | Backend  | BillingRate entity + migration        | 67A         |
| 68B   | Backend  | BillingRate service + rate resolution  | 68A         |
| 69A   | Frontend | Rate management settings page         | 68B         |
| 69B   | Frontend | Billable UX (time entry enrichment)   | 68B         |
| 70A   | Backend  | CostRate entity + service             | 67A         |
| 70B   | Backend  | Budget entity + service + alerts      | 67A         |
| 71A   | Frontend | Budget tab on project detail          | 70B         |
| 72A   | Backend  | Profitability report service          | 68B, 70A    |
| 72B   | Backend  | Report endpoints (project/customer)   | 72A         |
| 73A   | Frontend | Profitability page + nav              | 72B         |
| 73B   | Frontend | Project financials tab                | 72B         |
| 73C   | Frontend | Customer financials tab               | 72B         |
| 74A   | Backend  | Budget status indicators              | 70B         |
| 74B   | Frontend | Budget status in project list         | 74A         |
| 74C   | Frontend | Budget alert notifications            | 74A         |
```

Seventeen slices. Each one is a self-contained unit of work: 6-12 files, buildable, testable, mergeable independently. The dependency column ensures they run in the right order.

I don't write code in these documents. I write *what* to build and *why*. The agents handle *how*.

## The Execution: `run-phase.sh`

The actual command:

```bash
./scripts/run-phase.sh 8
```

That's it. The script reads the task file, identifies which slices are still marked "TODO", and loops through them:

```bash
for slice in $(extract_remaining_slices "$TASK_FILE"); do
    echo "[$(date)] Starting slice: $slice"
    claude -p "/epic_v2 $slice auto-merge" --model opus

    if [ $? -ne 0 ]; then
        echo "[$(date)] Slice $slice FAILED. Stopping."
        exit 1
    fi

    echo "[$(date)] Slice $slice complete."
done
```

Each iteration launches a *fresh* Claude Code session. That's the critical design decision. The session gets a clean context window, reads only what it needs for that specific slice, and exits when done. No accumulated history. No context degradation.

The `auto-merge` flag tells the epic skill to merge the PR automatically if the reviewer approves. Without it, the system pauses for manual approval — useful when I want to inspect something, but unnecessary for a well-tested phase.

I ran it with `caffeinate -is` on macOS to prevent the machine from sleeping, and monitored via `tail -f tasks/.phase-8-progress.log`.

## What Happens Inside Each Slice

Let me walk through Slice 68B (BillingRate service + rate resolution) as a concrete example. This is one of the more interesting slices because rate resolution has real logic — it's not just CRUD.

### Step 1: Worktree Creation

The orchestrator creates a git worktree:

```bash
git worktree add ../worktree-epic-68B -b epic-68B/billing-rate-service
```

Worktrees give each slice an isolated copy of the repo. If the builder breaks something, it doesn't affect the main branch. If the slice fails entirely, `git worktree remove` cleans up.

### Step 2: Scout

A Sonnet agent reads:
- `backend/CLAUDE.md` (conventions, anti-patterns)
- `architecture/phase8-rate-cards-budgets-profitability.md` (rate hierarchy section, ADR-039, ADR-040)
- `tasks/phase8-rate-cards-budgets-profitability.md` (slice 68B task list)
- `BudgetService.java` (reference service pattern — most recent)
- `BillingRate.java` and `BillingRateRepository.java` (from 68A, just merged)

It produces `.epic-brief.md` — a 45KB document containing the task list, file plan, reference patterns (verbatim code from BudgetService), the rate resolution algorithm, and the anti-patterns list.

The rate resolution logic is spelled out in the brief:

```
Rate Resolution Order:
1. Customer-specific rate for this project → if exists, use it
2. Project default rate → if exists, use it
3. Org default rate → if exists, use it
4. Throw RateNotFoundException

Each level can override hourly_rate and/or currency.
Snapshot the resolved rate at time entry creation (ADR-040).
```

### Step 3: Builder

A Sonnet agent reads *only* the brief and implements:

- `RateResolutionService.java` — the 3-level resolution chain
- `BillingRateService.java` — CRUD operations + rate resolution delegation
- `BillingRateController.java` — REST endpoints with `@RequiresCapability("MANAGE_RATES")`
- `RateSnapshot.java` — embeddable value object for capturing resolved rates
- `BillingRateIntegrationTest.java` — tests for CRUD + rate resolution (org default, project override, customer override, fallback)

Then runs the build:

```bash
./mvnw clean verify -q 2>&1 > /tmp/mvn-epic-68B.log
```

The `-q` flag suppresses Maven's verbose output. If the build fails, the builder reads *only the failed test names* from Surefire reports (not the full output), fixes the issue, and re-runs. This two-pass approach keeps context usage minimal.

Build passes. The builder commits, pushes, and opens a PR.

### Step 4: Reviewer

An Opus agent reads the PR diff and `backend/CLAUDE.md`. It checks:

- **Tenant isolation**: Does the rate resolution query use the tenant schema? (Yes — schema boundary, no manual filtering needed.)
- **Security**: Are write endpoints protected with `@RequiresCapability`? (Yes.)
- **Convention compliance**: One service call per controller endpoint? (Yes.) Constructor injection? (Yes.) No `@Autowired`? (Yes.)
- **Test coverage**: Are all resolution levels tested? (Yes — org default, project override, customer override, fallback to exception.)
- **Domain logic**: Does the snapshot capture happen at the right time? (Yes — in TimeEntryService when creating an entry, not in RateResolutionService.)

Verdict: **APPROVE**.

### Step 5: Merge and Advance

```bash
gh pr merge 141 --squash --delete-branch
git worktree remove ../worktree-epic-68B --force
```

The orchestrator updates the task file: Slice 68B → **Done**, PR #141. Advances to the next slice (69A — frontend rate management page).

Total time for this slice: roughly 18 minutes.

## The Dependency Chain in Practice

Slices run sequentially, but the *dependency graph* matters for correctness.

Slice 69A (Frontend — Rate management settings page) depends on 68B (Backend — rate service). The scout for 69A sees the merged controller from 68B and can read the endpoint signatures, response shapes, and error codes. The brief includes the API contract:

```
GET  /api/rates                    → List<BillingRateResponse>
POST /api/rates                    → BillingRateResponse
PUT  /api/rates/{id}               → BillingRateResponse
DELETE /api/rates/{id}             → 204 No Content
GET  /api/rates/resolve?customerId=&projectId=  → ResolvedRateResponse
```

The frontend builder implements against this contract. Because the backend is already merged and running (it would be, if the dev server were up), the frontend could even be tested against real endpoints. In practice, the frontend tests mock the API calls — but the contract is grounded in real code, not a spec that might drift.

## When It Goes Wrong

Phase 8 was clean. Not every phase was.

### Phase 35: The Full Rollback

I covered this in Post 1, but here's the execution perspective. Phase 35 (Keycloak auth migration) ran 27 commits across 14 PRs. Every slice passed its build. Every reviewer approved. The pipeline did exactly what it was designed to do.

The problem was upstream: the architecture was wrong. Token refresh timing assumptions, session handling edge cases, middleware ordering — these were *design* flaws, not implementation flaws. The agents faithfully executed a flawed plan.

The rollback:

```bash
git reset --hard 22d38eaf
git push --force origin main
git tag phase-35-backup HEAD@{1}
```

Everything after the pre-Phase-35 commit: gone. The `phase-35-backup` tag preserves the work in case I want to cherry-pick individual fixes later.

The lesson: **the pipeline amplifies your architecture decisions, good and bad.** A good architecture produces 17 clean PRs. A bad architecture produces 14 PRs you have to throw away. The cost of a bad architecture decision is multiplied by the speed of execution.

This is why I spend 80% of my time on requirements and architecture, not code. The code is cheap now. The *decisions* are expensive.

### Build Failures Mid-Phase

Occasionally a builder produces code that doesn't compile. Usually it's a missing import, a wrong method signature, or a test that references a fixture that doesn't exist yet.

The builder's first reaction is to read the error (from the Surefire XML, not the full log) and fix it. This works 80% of the time. If it fails twice, the session exits with a non-zero status, `run-phase.sh` stops, and I get notified.

At that point I have choices:
1. Fix the issue manually and restart from that slice
2. Read the builder's log, adjust the brief, and re-run
3. Skip the slice and come back to it

I almost always choose option 1 — manual fix and restart. It's faster, and `run-phase.sh` picks up from the next unfinished slice.

### Context Window Exhaustion

In the v1 orchestrator (before `run-phase.sh`), context would fill up after 4-5 slices. The orchestrator accumulated dispatch results, PR numbers, status updates, and error logs. By slice 5, it was operating with degraded context — forgetting conventions, misreading task files, producing lower-quality dispatches.

The v2 solution (fresh session per slice) eliminated this entirely. Each slice gets the full context window. The bash script handles sequencing — it doesn't need AI for that.

## The Numbers

Phase 8 stats:
- **17 slices**, 17 PRs (#133–#150)
- **~6 hours** total execution time
- **4 new entities**: OrgSettings, BillingRate, CostRate, ProjectBudget
- **4 new migrations**: V19, V20, V21
- **~100 new backend tests** (total: ~830)
- **~40 new frontend tests** (total: ~282)
- **Zero manual code edits** during execution

Other phase stats for comparison:
- Phase 12 (Document Templates): 6 slices, 4.5 hours, fully automated
- Phase 6.5 (Notifications/Comments): 15 slices, ~8 hours
- Phase 20 (Auth Abstraction): 10 slices, 2h 44m (the fastest — lots of mechanical refactoring)

The variance comes from slice complexity. A backend CRUD slice takes ~15 minutes. A frontend page with multiple interactive components takes ~25 minutes. A refactoring slice touching 20+ files takes ~30 minutes.

## Why Sequential, Not Parallel

You might wonder: if slices are independent (some are), why not run multiple in parallel?

I tried. It didn't work well, for three reasons:

1. **Git conflicts.** Parallel builders modify the same files (imports, module registrations, navigation menus). Merge conflicts are annoying to resolve automatically and risky to resolve wrong.

2. **Stale references.** Slice 72A (profitability report service) depends on 68B (rate resolution) and 70A (cost rates). If they run in parallel, 72A's scout doesn't see the merged code from the other two. The brief would be incomplete.

3. **Failure recovery.** If Slice 3 of 5 parallel slices fails, what do you do? The other 4 are already running. Do you kill them? Let them finish? Merge them out of order? Sequential execution makes failure recovery trivial: stop, fix, restart.

The throughput loss from sequential execution is smaller than you'd think. Each slice is 15-30 minutes. The bottleneck isn't execution speed — it's the time I spend writing the architecture doc and task file. Once those are done, six hours of unattended execution is fast enough.

## Try This Yourself

You don't need 17 slices to try phase execution. Start with 3:

1. **Write a task file.** Three slices, sequentially dependent. Slice A: backend entity + migration. Slice B: backend service + controller + tests. Slice C: frontend page.

2. **Write a brief for each slice** (or have a scout do it). Include reference patterns from your existing code.

3. **Run them sequentially.** One agent per slice, fresh session each time.

The key insights:
- **Fresh context per slice** prevents degradation
- **Dependencies run in order** — never parallelize interconnected work
- **The task file is the source of truth** — agents read it to know what's done and what's next
- **Failures are cheap** — worktrees isolate damage, rollback is trivial

The pipeline doesn't make you faster at *thinking*. It makes the *gap between thinking and shipping* disappear.

---

*Next in this series: [The Reviewer is the Product: Why AI Code Review Matters More Than AI Code Writing](05-the-reviewer-is-the-product.md)*

*Previous: [Model Tiering](03-model-tiering.md)*
