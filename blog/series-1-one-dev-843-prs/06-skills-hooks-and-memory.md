# Skills, Hooks, and Memory: Teaching Your AI Agent to Get Better Over Time

*Part 6 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents.*

---

By Week 4, I had a problem I didn't expect: the AI was making the same mistakes twice.

Not the same *code* mistakes — those got fixed and stayed fixed. I mean *process* mistakes. An agent would try to read a 108KB architecture file in a context where it only had room for 50KB. A builder would run `mvn clean verify` without the `-q` flag and burn 30KB of context on Maven output. A reviewer would flag a "missing test" that actually existed in a different file.

Each time, I'd fix it in the moment. And the next session, it would happen again. Because AI agents don't remember between sessions. Every new session starts from zero.

Unless you teach them.

## The Three Persistence Layers

I ended up with three distinct systems for teaching my agents:

### 1. CLAUDE.md — The Constitution

Every project can have a `CLAUDE.md` file (and subdirectory-specific ones like `backend/CLAUDE.md`) that Claude Code reads at the start of every session. These are convention files — the rules of the codebase.

Mine started small: "use Sonnet for scouts." Over 55 phases, they grew into detailed playbooks:

```markdown
## Backend Anti-Patterns (from backend/CLAUDE.md)

- NEVER add @Filter or tenant_id columns — schema boundary handles isolation
- NEVER inject repositories into controllers — one service call per endpoint
- NEVER use @Autowired — constructor injection only
- NEVER use float/double for money — BigDecimal only
- NEVER run ./mvnw without -q flag — full output burns 30-60KB of context
```

The anti-patterns section is the most valuable part. It's a list of mistakes that were made once and must never be repeated. Every scout includes this section verbatim in the brief. Every builder sees it. Every reviewer checks against it.

The key insight: **CLAUDE.md files are read automatically.** You don't need to remember to include them. They're always there, shaping every session.

### 2. Skills — Reusable Workflows

Skills are custom slash commands. When I type `/epic 82A`, it triggers a multi-step workflow that dispatches scouts, builders, and reviewers in sequence. When I type `/ideate`, it starts a product strategy conversation.

I built 17 skills over 8 weeks:

| Skill | Purpose |
|-------|---------|
| `/ideate` | Product strategy sessions — explore what to build next |
| `/architecture` | Produce API specs, schemas, and ADRs from requirements |
| `/breakdown` | Decompose architecture into epic slices and tasks |
| `/phase` | Orchestrate an entire development phase |
| `/epic` | Implement one epic end-to-end |
| `/review` | Code review a PR |
| `/qa-cycle` | Autonomous QA testing cycle |
| `/fix-tests` | Diagnose and fix broken tests |
| `/regression` | Run full regression suite |

Each skill is a markdown file with explicit instructions — not prose descriptions, but runbook-style commands. "Read this file. Extract these sections. Write the brief to this path. Dispatch this agent." I learned early that AI agents don't infer tool parameters from prose. "Read the first 55 lines" doesn't translate to `Read(file, limit=55)` — you have to be explicit.

The skills evolved over time. `/phase` v1 ran everything in a single session and hit context limits after 4-5 slices. `/phase` v2 delegates to a bash script that launches fresh sessions per slice. `/epic` v1 had the orchestrator reading architecture docs; v2 delegated all reading to scouts. Each version encoded a lesson learned.

### 3. Memory — Cross-Session Knowledge

Claude Code has a file-based memory system — structured notes that persist across conversations. Mine tracks:

- **Environment quirks**: "Postgres host is `b2mash.local:5432`, not localhost"
- **Lessons learned**: "Phase 54 shipped 7 PRs without running the stack; 3 bugs survived. Infra phases need actual execution verification."
- **Phase status**: which phases are done, which PRs they produced
- **Decision context**: "Builders switched to Opus for invoicing phase — Sonnet underperformed on financial domain logic"

Memory fills the gap between CLAUDE.md (permanent conventions) and skills (reusable workflows). It's the institutional knowledge that doesn't fit neatly into either.

## The Ideation Skill: AI as Product Partner

The skill I'm most proud of isn't part of the pipeline. It's `/ideate`.

Here's how a typical ideation session works:

1. I type `/ideate`. The skill loads context about the current project state — what's been built, what phases are complete, what vertical we're targeting.

2. I describe what I'm thinking: "I want to add retainer billing for accounting firms. Monthly fixed-fee engagements where time is tracked against the retainer amount."

3. The AI pushes back, fills gaps, and explores edges:
   - "Should unused retainer hours roll over to the next period?"
   - "How does this interact with the existing invoicing system — does a retainer generate invoices automatically at period close?"
   - "Accounting firms in SA often have hybrid billing — some clients on retainer, some on hourly. Does the system need to support both per-customer?"

4. We go back and forth. I bring market knowledge ("small accounting firms don't want complexity — no rollover, no partial periods"). The AI brings systematic thinking ("if you don't track rollover, you need to handle the period-close event differently").

5. The output is a requirements document — a `.md` file in the `requirements/` directory that feeds directly into `/architecture`.

This isn't delegation. It's pair-design. I couldn't write these specs alone as quickly, because I'd miss edge cases. The AI couldn't write them alone at all, because it doesn't know what a 3-person accounting firm in Johannesburg actually needs.

## The Architecture Skill: Collaborative Technical Design

`/architecture` takes the requirements doc from `/ideate` and produces:

- API endpoint designs (paths, methods, request/response shapes)
- Database schema (migrations, entity relationships, index strategy)
- ADRs (Architecture Decision Records) explaining key tradeoffs

But it's not a fire-and-forget command. I review each ADR and push back:

**Me**: "ADR-048 proposes invoice numbering with a global sequence. But each tenant needs its own sequence — `INV-001` for Tenant A and `INV-001` for Tenant B simultaneously."

**AI**: "You're right. I'll change to a per-tenant `InvoiceCounter` entity with `SELECT ... FOR UPDATE` locking to prevent gaps."

**Me**: "Is the lock contention a problem at scale?"

**AI**: "For a firm generating <100 invoices per month, no. If you're targeting firms with >1000 invoices/day, we'd need a different approach. Given your target market..."

This conversation became ADR-048. The reasoning is preserved. When a future developer asks "why is there a separate InvoiceCounter entity instead of using a database sequence?", the ADR explains the constraint and the decision.

## Code Quality: The Sessions Between Phases

Feature phases get the glory. But the sessions that kept the codebase healthy were the unglamorous ones:

**Duplicate detection.** After Phase 8 (Rate Cards) and Phase 10 (Invoicing), I noticed both had similar "lookup entity, validate, create, audit" patterns. A quick analysis session identified 4 shared abstractions worth extracting.

**Shared utilities.** `DeleteGuard` (prevent deletion of entities with dependents), `AuditDeltaBuilder` (track field-level changes), `FieldGroupResolver` (attach custom field packs to entities), `ActorContext` (resolve the current user for audit trails) — these emerged from refactoring sessions, not feature phases.

**Constructor bloat monitoring.** When a service hits 10+ constructor dependencies, it's doing too much. Analysis sessions would flag these and suggest splits. `CustomerService` → `CustomerService` + `CustomerLifecycleService`. `InvoiceService` → `InvoiceService` + `InvoiceNumberService`.

**Test cleanup.** After major refactoring phases, test files accumulate dead imports, redundant setup, and outdated assertions. A `/fix-tests` session cleans these up.

None of this ships features. All of it prevents the codebase from becoming unmaintainable. At 240K lines of Java and 111K lines of TypeScript, maintainability isn't optional — it's survival.

The irony: AI agents produce code faster than a human can review it for quality. So you need AI agents to review the quality. It's turtles all the way down.

## Building Your Own Skill Library

You don't need 17 skills to start. Here's the progression:

**Week 1: Write a CLAUDE.md.** Document your conventions. Anti-patterns especially. This single file improves every AI interaction with your codebase.

**Week 2: Build one skill.** Pick your most repetitive workflow — probably something like "implement a feature: create branch, write code, run tests, create PR." Encode the steps explicitly.

**Week 3: Add memory.** Start noting things that surprised you. Environment quirks. Decisions that aren't obvious from the code. Mistakes that happened twice.

**Week 4: Iterate.** Your first skill will be wrong. You'll discover the AI interprets a step differently than you intended. Rewrite it as a runbook with exact commands. Then build the next skill.

The skill library grows organically from pain points. If you find yourself giving the same instruction to an AI for the third time, it's a skill.

## What This Looks Like at Scale

After 8 weeks and 55 phases:

- **CLAUDE.md files**: 3 files (root + backend + frontend), ~30KB total of conventions
- **Skills**: 17 reusable workflows
- **Memory entries**: 50+ notes covering lessons, phase status, environment quirks
- **Session logs**: 594 tracked sessions with tool call metrics

The system learns. Not in the "AGI is coming" sense. In the boring, practical sense: each mistake becomes a rule, each rule prevents the next mistake, and the rules are read by every future session. It's the same feedback loop that makes any engineering team better over time — just encoded in files instead of tribal knowledge.

---

*Next in this series: [What AI Agents Can't Do (And What That Means for Your Career)](07-what-ai-agents-cant-do.md)*

*Previous: [The Reviewer is the Product](05-the-reviewer-is-the-product.md)*
