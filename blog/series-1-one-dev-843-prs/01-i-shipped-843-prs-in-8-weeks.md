# I Shipped 843 Pull Requests in 8 Weeks. Here's What Actually Happened.

*This is Part 1 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents. No hype. Just what happened.*

---

At the end of January 2026, I started building a multi-tenant B2B SaaS platform from scratch. Eight weeks later, I had 843 merged pull requests, 240,000 lines of Java, 111,000 lines of TypeScript, 83 database entities, 99 migration files, and a product that an actual accounting firm could use for about 75% of their daily work.

I did this alone. Well — sort of.

I had Claude Code, Anthropic's AI coding assistant, running as my development team. I built custom automation skills that turned it into a pipeline: an architect, a scout, a builder, a reviewer, and an orchestrator. On good days, the system shipped 15+ PRs without me touching the keyboard. On bad days, I rolled back an entire phase and started over.

This post is the honest version of that story. Not the LinkedIn version.

## What I Built

The product is called DocTeams — a team-based document hub for professional services firms. Think: accounting practices, law firms, consulting shops. The kind of businesses that manage clients, projects, documents, time tracking, invoicing, and compliance checklists.

Here's what's in the box:

- **Multi-tenant architecture** with PostgreSQL schema-per-tenant isolation (each customer gets their own database schema — their data literally cannot mix with anyone else's)
- **83 domain entities** spanning projects, documents, customers, tasks, time entries, invoices, rates, budgets, templates, notifications, comments, audit logs, compliance checklists, and more
- **Full invoicing pipeline** — time tracking feeds into unbilled hours, which generate invoice line items, with configurable rates at org/project/customer levels
- **Profitability reporting** — revenue vs. cost rates, budget tracking, utilization dashboards
- **Document template engine** — Handlebars templates rendered to HTML/PDF via OpenHTMLToPDF, with branding per tenant
- **Compliance packs** — FICA/KYC checklists for South African accounting firms, with customer lifecycle state machines (PROSPECT → ONBOARDING → ACTIVE)
- **Recurring project schedules** — monthly bookkeeping, annual audits, whatever your firm does on repeat
- **Retainer billing** — fixed-fee agreements with consumption tracking and period close

The tech stack: Java 25, Spring Boot 4, Hibernate 7, PostgreSQL 16, Flyway, Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Shadcn UI, Keycloak for auth.

Not a toy. Not a tutorial project. A real product with real edge cases, real error handling, and real tests.

## The Timeline

Here's roughly how it went:

**Week 1–2 (Feb 4–17)**: Scaffolding, auth (Clerk initially, later migrated to Keycloak), org management, webhook infrastructure, tenant provisioning, core projects & documents APIs, frontend shell. About 150 PRs.

**Week 3–4 (Feb 18–Mar 3)**: Customers, tasks, document scoping, time tracking, audit infrastructure, notifications, comments, activity feeds. The product starts feeling like a product. About 250 PRs.

**Week 5–6 (Mar 4–17)**: Rate cards, budgets, profitability reports, invoicing, document templates, tags, custom fields, saved views. The financial backbone. Also: Phase 35 (Keycloak migration) gets rolled back entirely. About 250 PRs.

**Week 7–8 (Mar 18–31)**: Customer compliance, lifecycle state machines, project templates, recurring schedules, retainers, auth abstraction, E2E testing, vertical-specific packs for accounting firms, gap analysis. About 190 PRs.

1,026 commits in February. 738 in March. 17 custom Claude Code skills built along the way to automate the process.

## How the AI Pipeline Works

I didn't sit there typing prompts all day. That would've been slower than just writing the code myself.

Instead, I built a system. Here's the pipeline for shipping a single feature (what I call a "slice"):

1. **I write the architecture.** A requirements document, API specs, database schema, ADRs explaining key tradeoffs. This is the part that actually requires thinking. AI can't do this — it doesn't know what your users need or what compliance means in your market.

2. **A "scout" agent reads everything.** It reads my architecture doc, the existing codebase conventions (documented in CLAUDE.md files), recent similar code as reference patterns, and the specific task description. It produces a self-contained "brief" — a 40–50KB document that has everything a builder needs to implement the slice.

3. **A "builder" agent reads only the brief and implements.** It writes the entity, repository, service, controller, migration, tests, and frontend components. It runs the build. It commits and pushes. It opens a PR.

4. **A "reviewer" agent reads the diff.** It checks for tenant isolation leaks, SQL injection, convention violations, missing test coverage. It approves or requests changes.

5. **If approved, it merges.** If not, a "fixer" agent addresses the findings, pushes, and the reviewer runs again.

6. **The orchestrator advances to the next slice.**

This whole thing runs from a single bash command: `./scripts/run-phase.sh 12`. It reads the phase task file, loops through each slice, dispatches fresh agent sessions per slice (so context windows never fill up), and stops on failures.

Phase 12 (Document Templates) ran fully automated in 4.5 hours. 6 slices, 6 PRs, zero manual intervention.

## What Actually Goes Wrong

Now here's the part the AI hype accounts skip.

### Phase 35: The Full Rollback

In early March, I ran a phase to migrate the entire authentication system from Clerk to Keycloak. 27 commits, 14 PRs. The agents executed it cleanly — tests passed, builds succeeded, code review found no critical issues.

Then I tried to actually *use* the product.

The migration had subtle integration issues that unit tests don't catch. Session handling edge cases. Token refresh timing. Middleware configuration that worked in isolation but broke in sequence.

I rolled back the entire phase. `git reset --hard` to before the first PR, force-pushed to main. Tagged the abandoned work as `phase-35-backup` in case I needed to cherry-pick anything later. Started over with a different approach.

27 commits. Gone. That's what "AI-assisted development" looks like in practice.

### The RLS Reversal

In Phase 2, I built a tiered tenancy model. "Starter" tenants got shared-schema isolation with Postgres Row-Level Security policies. "Pro" tenants got dedicated schemas. It was architecturally elegant. It was also unnecessarily complex.

By Phase 13, I ripped out the entire shared-schema path. Deleted `TenantAware`, `TenantAwareEntityListener`, `TenantFilterTransactionManager`, `TenantInfo`, `TenantUpgradeService`, and 7 test files. Stripped `@Filter`/`@FilterDef`/`tenant_id` columns from all 27 entities. Renumbered the migrations.

The lesson: an AI agent will happily build whatever architecture you spec. It won't tell you the architecture is over-engineered. That's still your job.

### Bugs the Reviewer Caught

The review agent (running on the more expensive Opus model) caught real issues that would've shipped:

- A **BACKEND_URL leak** in a download endpoint that would've exposed internal infrastructure URLs to the frontend
- A **Server-Side Template Injection** risk in the document template system — user-supplied Handlebars templates that could escape the sandbox
- **Tenant isolation violations** where a query could theoretically return data from another tenant's schema (before Phase 13 simplified this away)
- **Frontend/backend permission parity gaps** where the backend granted permissions that the frontend didn't enforce

These aren't hypothetical. These are bugs that passed the builder, passed the tests, and got caught in review. The review step isn't optional decoration — it's where the value is.

## The Part That Wasn't Automated

Not everything went through the pipeline. Some of the most valuable AI-assisted work was *collaborative*, not delegated.

**Product ideation was a conversation.** I built a custom `/ideate` skill — essentially a product strategist that knows the codebase. When I needed to figure out what accounting firms actually need for FICA compliance, or how customer lifecycle state machines should work, or whether retainer billing justified its own entity model, I'd start an ideation session. The AI brought domain research, competitive analysis, and pattern knowledge. I brought market intuition, user empathy, and the ability to say "no, that's over-engineered for a 3-person firm." Together we'd produce a requirements document. Neither of us could've written it alone.

**Architecture decisions were pair-designed.** The `/architecture` skill took a requirements doc and produced API specs, schema designs, and ADRs — but not in a vacuum. I'd push back on decisions. "Why a separate entity instead of a JSONB column?" "Is this migration safe to run on a live database?" The ADRs captured the *reasoning*, not just the outcome. When I reversed the RLS decision in Phase 13, the original ADR-012 explained *why* we'd tried it, and ADR-064 explained *why* we stopped. Future-me (or a future contributor) can read the trail.

**Code quality was a continuous discipline.** Between feature phases, I'd run ad-hoc analysis sessions — finding duplicated patterns across services, extracting shared utilities (`DeleteGuard`, `AuditDeltaBuilder`, `FieldGroupResolver`), simplifying abstractions that had grown too clever. This wasn't glamorous pipeline work. It was the maintenance that kept the codebase navigable at 240K lines of Java. Without it, the AI agents would've been pattern-matching against increasingly messy reference code, and quality would've degraded.

The pipeline built features. The conversations shaped the product. The cleanup kept it healthy.

## The Cost of Honesty

Let me be direct about what this approach *doesn't* do:

**AI didn't make product decisions alone.** The ideation sessions were collaborative — AI as thinking partner, not decision-maker. I could ask "what do accounting firms need for client onboarding?" and get a thorough research dump, but the judgment call — "FICA compliance checklists are the priority, not a fancy onboarding wizard" — was mine.

**AI didn't talk to users.** The gap analysis for the accounting vertical came from my understanding of the South African market, FICA compliance requirements, and SARS deadlines. The AI helped me *structure* that knowledge into specs. It didn't *generate* the knowledge.

**AI didn't recover from architectural mistakes.** When Phase 35 failed, I had to diagnose *why* it failed, decide the right remediation strategy, and plan the replacement approach. The AI rolled back the code. I rolled back the thinking.

**AI produced bugs.** Lots of them. The QA cycle for the accounting vertical found 27 gaps, including a blocker (missing proposal workflow). The agents built what I specified — they didn't notice what I *didn't* specify.

**This cost real money.** API usage for 594 agent sessions across 8 weeks adds up. I'll share exact numbers in a future post, but it's not trivial. Model tiering (using cheaper models for scouts/builders, expensive models for reviewers) was essential.

## What I'd Tell Someone Starting This

1. **Architecture is not delegatable.** Spend 80% of your time on requirements docs, API specs, and ADRs. The implementation is the easy part.

2. **Build the review pipeline first.** If I had to choose between an AI that writes code and an AI that reviews code, I'd pick the reviewer every time.

3. **Automate the boring parts.** The custom skills (`/epic`, `/phase`, `/qa-cycle`) took time to build but paid for themselves by Week 3. Without them, I'd have spent half my time context-switching between tool windows.

4. **Plan for failures.** Worktrees, backup tags, rollback scripts. Every phase has an undo button. The one time I needed it (Phase 35), it saved days.

5. **Don't believe the "build a SaaS in a weekend" posts.** I've been a developer for years. I understand multi-tenancy, auth, billing, compliance. The AI amplified my existing knowledge. It didn't create knowledge I didn't have.

## What's Next

In the next post, I'll break down the Scout-Builder pattern — the specific technique that made this pipeline possible by solving the biggest bottleneck in AI-assisted development: context window management.

For now, here's the honest summary: One developer, 8 weeks, 843 PRs, one production-grade SaaS platform. The AI was the workforce. The architecture was mine. The failures were real. And the product works.

---

*Next in this series: [The Scout-Builder Pattern: How I Stopped Burning Context Windows](02-scout-builder-pattern.md)*

*If you're building something similar, I'm extracting the multi-tenant foundation into an open-source template. [Subscribe for updates](#) when it's ready.*
