# The Scout-Builder Pattern: How I Stopped Burning Context Windows

*Part 2 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents.*

---

By Week 2 of the build, I had a problem.

My AI coding agent was spending more time *reading* the codebase than *writing* code. Each new feature required the agent to re-read the architecture doc (108KB), the backend conventions (12KB), the frontend conventions (10KB), and 3–4 reference files to understand existing patterns. By the time it had enough context to start implementing, 50% of its context window was gone.

Context windows in AI models aren't infinite. They're a fixed budget — every file you read, every conversation turn, every tool output eats into it. When you burn half your budget on orientation, the agent starts dropping details. It forgets the naming convention you use for services. It skips a test it should've written. It produces code that *almost* follows your patterns but doesn't quite match.

The solution was embarrassingly simple: split the work into two agents.

## The Problem: One Agent Doing Everything

Here's what a single-agent workflow looked like for implementing a new feature:

```
Agent starts with: task description (2KB)

Agent reads: CLAUDE.md (6KB)
Agent reads: backend/CLAUDE.md (12KB)
Agent reads: architecture/ARCHITECTURE.md (108KB)
Agent reads: phase task file (45KB)
Agent reads: reference entity file (3KB)
Agent reads: reference service file (5KB)
Agent reads: reference controller file (4KB)
Agent reads: reference test file (6KB)
Agent explores: existing code structure (varies, 10-30KB)

Context used before writing ANY code: ~200KB+
```

That's a lot of context spent on orientation. And it gets worse with every file the agent reads while implementing — error logs, build output, test results. By the time the feature was done, the agent was running on fumes.

The symptoms were predictable:
- Entity fields that didn't match the established naming convention
- Missing `@RequiresCapability` annotations on controller endpoints
- Test classes that didn't follow the `TestCustomerFactory` pattern I'd established
- SQL migrations with lowercase table names when the convention was `snake_case`

Small things. But small things compound into a codebase that feels inconsistent — and inconsistency is the enemy of maintainability.

## The Solution: Two Agents, One Brief

The Scout-Builder pattern splits implementation into two phases, each handled by a separate agent with a separate context window.

### The Scout

The scout's only job is *research*. It reads everything and produces a single document: the **epic brief**.

```
Scout reads: CLAUDE.md, backend/CLAUDE.md, frontend/CLAUDE.md
Scout reads: Architecture doc (relevant sections only)
Scout reads: Phase task file (specific epic)
Scout reads: 3-4 reference files (recent, working examples)
Scout reads: Existing related code (to understand current state)

Scout writes: .epic-brief.md (40-50KB, self-contained)
```

The brief is a contract. It contains:

1. **Task list** — every file to create or modify, in order
2. **File plan** — full paths, what each file should contain
3. **Reference patterns** — *verbatim* code from existing working examples (not summaries, not descriptions — actual code)
4. **Conventions** — the anti-patterns section from CLAUDE.md, copied word-for-word
5. **Migration details** — exact version number, table names, column types
6. **Build commands** — what to run and how to check if it passed

The critical insight: the brief includes **actual code** from reference patterns, not descriptions of code. "Follow the pattern in CustomerService" is useless — the builder would have to read CustomerService itself. Instead, the brief includes the relevant 40 lines of CustomerService directly. The builder can pattern-match from the example without ever opening the file.

### The Builder

The builder's only job is *implementation*. It reads one file: the brief.

```
Builder reads: .epic-brief.md (40-50KB)

Builder writes: entity, repository, service, controller
Builder writes: migration, tests, frontend components
Builder runs: build commands (output redirected to /tmp/)
Builder creates: PR
```

The builder never reads CLAUDE.md. Never reads the architecture doc. Never explores the codebase. Everything it needs is in the brief. This keeps its context window almost entirely available for writing and debugging code.

### Why This Works

**Context efficiency.** The scout uses ~200KB of context on reading, but its output (the brief) is only 40-50KB. That's a 4:1 compression ratio. The builder gets the same information in a quarter of the space.

**Disposable context.** When the scout finishes the brief, its context is discarded. All that orientation work — the 108KB architecture doc, the convention files, the reference exploration — is gone. It served its purpose and doesn't clutter the builder's workspace.

**Reproducible quality.** Because the brief includes verbatim reference patterns, the builder produces code that looks like the existing codebase. Not "inspired by" — actually matches. Same method signatures, same error handling, same test structure.

**Debuggable failures.** When something goes wrong, I can read the brief. If the brief has wrong information, it's a scout problem. If the brief is correct but the implementation is wrong, it's a builder problem. Clear blame boundary.

## What a Brief Looks Like

Here's an abbreviated example from a real brief (Epic 82A — Invoice CRUD):

```markdown
# Epic Brief: 82A — Invoice Entity + Repository + Service

## Task List
1. Create V23__create_invoices.sql migration
2. Create Invoice.java entity
3. Create InvoiceLine.java entity
4. Create InvoiceStatus.java enum
5. Create InvoiceRepository.java
6. Create InvoiceLineRepository.java
7. Create InvoiceService.java (CRUD operations)
8. Create InvoiceController.java (REST endpoints)
9. Create InvoiceIntegrationTest.java
10. Run build: ./mvnw clean verify -q

## Migration: V23__create_invoices.sql

Based on V19 (reference: OrgSettings migration):

    CREATE TABLE invoices (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        customer_id UUID NOT NULL REFERENCES customers(id),
        project_id UUID REFERENCES projects(id),
        invoice_number VARCHAR(50) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
        ...
    );

## Reference Pattern: Entity

From ProjectBudget.java (the most recent entity addition):

    @Entity
    @Table(name = "project_budgets")
    public class ProjectBudget {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "project_id", nullable = false)
        private Project project;

        // ... full entity code here, not summarized
    }

## Reference Pattern: Service

From BudgetService.java:

    @Service
    @Transactional(readOnly = true)
    public class BudgetService {
        private final ProjectBudgetRepository budgetRepository;
        private final ProjectRepository projectRepository;

        // constructor injection, no @Autowired

        @Transactional
        public ProjectBudget create(UUID projectId, CreateBudgetRequest request) {
            var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
            // ... validation, creation, audit event
        }
    }

## Convention Reminders (from backend/CLAUDE.md)

- Controllers: ONE service call per endpoint. No repository injection.
- Authorization: @RequiresCapability("MANAGE_INVOICES") on write endpoints
- Error handling: throw semantic exceptions, never catch in controllers
- Tests: use TestCustomerFactory.createActiveCustomer() for test customers
- DO NOT add @Filter or tenant_id columns (schema boundary handles isolation)
```

The real briefs are 40-50KB and include full reference implementations, not snippets. The builder can literally copy-paste the pattern and adapt the names.

## The Model Tiering Angle

Here's where cost optimization comes in. The scout and builder don't need to be the same model — and they shouldn't be.

**Scouts run on Sonnet** (the mid-tier model). The scout's job is mechanical: read files, extract relevant patterns, format a document. It doesn't make creative decisions. It doesn't need to reason about architecture. It's assembling a briefing packet.

**Builders run on Sonnet** too. Surprised? The builder is also following a recipe. The brief tells it exactly what to create, with exact patterns to follow. There's no ambiguity to resolve, no tradeoffs to evaluate. It's transcription with adaptation.

**Reviewers run on Opus** (the top-tier model). This is where judgment matters. The reviewer needs to spot tenant isolation leaks that the builder didn't think about. It needs to evaluate whether the error handling is appropriate. It needs to decide if a convention violation is a real problem or a false positive.

The insight: **constrained tasks don't need expensive models. Quality gates do.** Throwing Opus at every step is like hiring a senior architect to write boilerplate — wasteful. But having a senior architect review the boilerplate? That's exactly what senior architects are for.

In practice, this halved my API costs while maintaining code quality. The reviewer is the quality gate, and it runs on the model with the best judgment.

## The Orchestration Layer

The scout and builder don't coordinate directly. An orchestrator manages the pipeline:

```
Orchestrator:
  1. Read task file → identify next unfinished slice
  2. Create git worktree (isolation)
  3. Dispatch scout → wait for brief
  4. Dispatch builder → wait for PR
  5. Dispatch reviewer → wait for verdict
  6. If approved: merge PR, remove worktree
  7. If rejected: dispatch fixer, then re-review
  8. Update status file → advance to next slice
```

Each step is a **blocking call** to a separate agent session. The scout runs, finishes, returns the brief path. The builder runs, finishes, returns the PR number. No parallel agents. No background tasks. Simple sequential pipeline.

Why blocking? Because background agents are unreliable. I learned this the hard way — early versions of the `/phase` skill dispatched agents in the background, and the orchestrator would freeze waiting for results that never came back. Blocking calls are slower but deterministic.

The orchestrator itself stays lean. It never reads architecture docs, never reads source code, never reads convention files. Its entire context budget goes to coordination — reading status, dispatching agents, handling failures. If it tried to also understand the code, it would hit context limits after 4-5 slices.

## The Second-Generation Script

Even with blocking calls, the orchestrator eventually fills its context window. After 4-5 slices, there's enough history (dispatch results, PR numbers, status updates) that new slices get degraded context.

The fix: don't use an AI agent as the orchestrator at all.

`run-phase.sh` is a 120-line bash script that reads the task file, loops through slices, and dispatches a *fresh* Claude Code session per slice. Each session gets a clean context window. No accumulated history. No degradation.

```bash
# Simplified version of the actual script
for slice in $(extract_remaining_slices "$TASK_FILE"); do
    echo "Starting slice: $slice"
    claude -p "/epic_v2 $slice auto-merge" --model opus
    if [ $? -ne 0 ]; then
        echo "Slice $slice failed. Stopping."
        exit 1
    fi
done
```

This is how Phase 12 ran fully automated for 4.5 hours — 6 slices, each with a fresh context, each producing a clean PR.

## When the Pattern Breaks Down

The scout-builder split isn't magic. It has failure modes:

**Stale reference patterns.** If the scout picks a reference file that was written 3 phases ago and doesn't match current conventions, the builder will produce outdated code. Mitigation: scouts are instructed to prefer the *most recent* additions.

**Incomplete briefs.** If the scout misses a convention (say, the new `@RequiresCapability` annotation from Phase 20), the builder won't know to use it. Mitigation: the CLAUDE.md files are the single source of truth, and scouts are required to include the anti-patterns section verbatim.

**Cross-slice dependencies.** If Slice B depends on code from Slice A that hasn't merged yet, the scout for Slice B won't see it. Mitigation: slices run sequentially, and each new scout session sees the latest merged code.

**Refactoring slices.** The pattern works best for *additive* work (new entities, new features). Refactoring slices that modify 20+ existing files need more exploration than a brief can capture. For these, I sometimes let the builder spawn research sub-agents to explore the codebase.

## Try It Yourself

You don't need my exact skill system to use this pattern. The core idea is:

1. **One agent reads; a different agent writes.** Don't make the same agent do both.
2. **The brief is the contract.** It must be self-contained. If the builder needs to read any file other than the brief, the scout failed.
3. **Include actual code, not descriptions.** "Follow the pattern in UserService" is worthless. Paste the relevant 30 lines.
4. **Cheap models for constrained work, expensive models for judgment.** Don't pay for reasoning where compliance is enough.

The pattern scales. Whether you're building one feature or running 55 phases, the economics stay the same: research once, write once, review once.

---

*Next in this series: [Model Tiering: When to Use the Expensive Model and When Not To](03-model-tiering.md)*

*Previous: [I Shipped 843 Pull Requests in 8 Weeks](01-i-shipped-843-prs-in-8-weeks.md)*
