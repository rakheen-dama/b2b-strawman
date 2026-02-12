---
name: next-phase
description: "Conversational next-phase advisor — analyzes project state, identifies high-value feature gaps through a lean/portable SaaS lens, and produces a requirements spec. Usage: /next-phase"
---

# Next Phase Advisor Workflow

A conversational skill that helps decide **what to build next** by analyzing the current project state, identifying feature gaps, and evaluating candidates through lean SaaS lenses (profitability, sustainability, portability). Produces a `claude-code-prompt-phase{N}.md` requirements spec as output.

## Philosophy

Every candidate feature is evaluated through **four lenses**:

| Lens | Question |
|------|----------|
| **Lean** | Does this deliver validated learning or user value with minimum effort? |
| **Profitable** | Does this directly enable revenue, reduce churn, or increase willingness-to-pay? |
| **Sustainable** | Does this scale without linear cost increase? Does it reduce operational burden? |
| **Portable** | Could this be extracted as a module and dropped into a different B2B SaaS product? |

Features that score well on all four lenses are **pluggable domain candidates** — reusable SaaS building blocks.

## Arguments

- No required arguments.
- Optional: a focus area hint (e.g., `/next-phase billing`, `/next-phase collaboration`). If provided, bias candidate generation toward that area but still present alternatives.

## Context Budget

The orchestrator (you) stays lean:
- Read `TASKS.md` (overview only, ~76 lines)
- Read first 30 lines of each `tasks/phase{N}-*.md` to get status summaries
- Read `MEMORY.md` for epic status summary
- Delegate deep codebase analysis to a **Scout agent**

**NEVER** read full architecture docs, full phase task files, or subdirectory CLAUDE.md files from the orchestrator.

---

## Workflow

### Step 0 — Gather project state

Dispatch a **Scout agent** (`subagent_type: general-purpose`) to analyze the current project state. Use this prompt template:

```
You are a **project state analyst** for a multi-tenant B2B SaaS platform.

Your job: analyze the current state of the project and produce a structured summary.

## What to do

1. Read `TASKS.md` (the overview file).
2. Read `tasks/reference.md` for the dependency graph and implementation order.
3. List files in `tasks/` — note which phases exist and their file sizes.
4. For each phase task file, read the FIRST 40 lines to get the status summary and epic list.
5. Read recent git log (last 20 commits) to see what was recently completed.
6. Check for any open PRs via `gh pr list`.
7. List the main entity types by scanning `backend/src/main/java/**/entity/` directories.
8. List the main frontend routes by scanning `frontend/app/(platform)/` directories.
9. Read both `backend/CLAUDE.md` and `frontend/CLAUDE.md` for current conventions and anti-patterns.

## What to produce

Write a structured report with these sections:

### Completed Features
For each phase, list: phase name, epic range, one-line summary of what it added.

### Current Capabilities
Group by domain area:
- **Core platform**: auth, tenancy, member management
- **Domain entities**: what entities exist, their relationships
- **UI surface area**: what pages/views exist
- **Infrastructure**: deployment, CI/CD, monitoring
- **Quality**: test counts, test types, coverage gaps
- **Billing/monetization**: what's implemented

### Planned but not started
What phases/epics are specified but not yet built?

### Known gaps
What's obviously missing for a production B2B SaaS? Think about:
- Communication (email, notifications, comments, chat)
- Reporting/analytics (dashboards, exports, charts)
- Search (cross-entity, full-text)
- Integrations (webhooks outbound, API keys, OAuth apps)
- Self-service (onboarding flows, help center, feedback)
- Admin tools (impersonation, feature flags, tenant management)
- Security (2FA enforcement, session management, IP allowlists)
- Performance (caching, CDN, rate limiting, pagination optimization)
- Data management (import/export, bulk operations, archiving)
- Workflow (templates, automation rules, recurring items)

### Dependency insights
What features would UNLOCK other features if built first?
What features have no dependencies and could be built in parallel?

Be thorough but concise. This report will be used to generate next-phase candidates.
```

### Step 1 — Synthesize candidates

After the scout returns, **you** (the orchestrator) synthesize 4-6 candidate phases.

For each candidate, assess:

1. **Name**: Short phase name (e.g., "Notifications & Activity", "Reporting & Exports")
2. **Scope**: 2-3 sentence description of what's included
3. **Effort estimate**: Small (2-3 epics), Medium (4-6 epics), Large (7+ epics)
4. **Lean score**: How much validated user value per unit effort?
5. **Profit score**: Direct revenue impact or churn reduction potential?
6. **Sustainability score**: Does it reduce ops burden or scale well?
7. **Portability score**: Could this be a standalone module in another SaaS?
8. **Dependencies**: What must exist first? What does this unlock?
9. **Risk**: What's the biggest technical or product risk?

Use a scoring shorthand: `+++` (strong), `++` (moderate), `+` (weak), `-` (negative).

**Candidate generation heuristics** (apply these filters):

- **Always consider**: Features that every B2B SaaS eventually needs (notifications, reporting, search, integrations)
- **Prioritize**: Features that build on recently completed infrastructure (e.g., notifications build on Phase 6 audit events)
- **Deprioritize**: Features that are product-specific and hard to port (e.g., industry-specific compliance)
- **Flag**: Features that are prerequisites for multiple other features (force-multipliers)
- **Include at least one "boring but valuable"**: operational improvements, developer experience, test coverage, performance

### Step 2 — Present candidates to user

Present candidates in a **comparison table** with the lens scores, then use `AskUserQuestion` to let the user pick or refine:

```markdown
| # | Candidate | Effort | Lean | Profit | Sustain | Portable | Unlocks |
|---|-----------|--------|------|--------|---------|----------|---------|
| 1 | ...       | Medium | +++  | ++     | +++     | +++      | ...     |
| 2 | ...       | Small  | ++   | +++    | ++      | ++       | ...     |
| ...
```

Then ask:

```
AskUserQuestion:
  question: "Which direction interests you most for the next phase?"
  options:
    - label: "{Candidate 1 name}"
      description: "{One-line value prop}"
    - label: "{Candidate 2 name}"
      description: "{One-line value prop}"
    - label: "{Candidate 3 name}"
      description: "{One-line value prop}"
    - label: "Combine elements"
      description: "Mix features from multiple candidates into a custom phase"
```

### Step 3 — Deep-dive the chosen direction

Based on user selection, do a **focused deep-dive**:

1. If the user picked a single candidate → flesh out the scope into concrete features
2. If the user said "combine" → ask which elements from which candidates
3. If the user typed a custom direction → adapt

For the chosen direction, present a **feature breakdown** with 2-3 options per major decision:

```
AskUserQuestion:
  question: "For {Feature X}, which approach do you prefer?"
  options:
    - label: "Minimal (lean)"
      description: "Just the core — {specifics}. Can extend later."
    - label: "Full (comprehensive)"
      description: "Complete implementation — {specifics}. More effort but less rework."
    - label: "Stub + seams"
      description: "Build the interfaces and data model, stub the implementation. Fastest to unblock dependents."
```

Repeat for each major scope decision (typically 2-4 questions).

### Step 4 — Draft the requirements spec

Once scope is confirmed, draft a `claude-code-prompt-phase{N}.md` file following the established format:

**Use the existing phase prompt files as templates.** The structure is:

1. **Preamble**: "You are a senior SaaS architect..." + current system state
2. **Objective**: What this phase adds (numbered list)
3. **Constraints and assumptions**: Stack constraints, tenancy rules, permission model, out-of-scope items
4. **"What I want you to produce"**: Detailed specification for each feature area
   - Data model (entities, fields, indexes)
   - API endpoints (method, path, params, DTOs, permissions)
   - Frontend (components, pages, integration points)
   - Cross-cutting concerns (audit, events, testing)
5. **ADR requests**: Key decisions that need architectural documentation
6. **Style and boundaries**: Design principles, what not to change

**Before writing**, present a brief outline to the user:

```
AskUserQuestion:
  question: "Here's the outline for the Phase {N} spec. Ready to generate, or want to adjust?"
  options:
    - label: "Generate it"
      description: "Write the full spec to claude-code-prompt-phase{N}.md"
    - label: "Adjust scope"
      description: "I want to add/remove/change something first"
```

### Step 5 — Write and confirm

1. Write the spec to `claude-code-prompt-phase{N}.md` (where N is the next phase number or N.5 for interstitial phases).
2. Present a summary of what was written:
   - Section count
   - Entity count
   - API endpoint count
   - ADR count
   - Estimated epic count (rough)
3. Suggest next steps:
   - `/architecture claude-code-prompt-phase{N}.md` to generate the architecture doc
   - `/breakdown {N}` to break it into epics/slices/tasks
   - Or iterate on the spec if the user wants changes

---

## Anti-Patterns

1. **Don't skip the conversation.** The whole point of this skill is to be interactive. Never auto-generate a spec without presenting candidates and getting user input at Steps 2, 3, and 4.

2. **Don't read full architecture docs from the orchestrator.** Delegate to the scout agent. The orchestrator's job is synthesis and conversation, not research.

3. **Don't propose product-specific features without flagging portability.** Every feature should be evaluated as "would this work in a different SaaS?" If it wouldn't, explicitly call that out.

4. **Don't generate massive phases.** Lean means small batches. If a phase exceeds 8 epics, suggest splitting it. The sweet spot is 4-6 epics per phase.

5. **Don't forget to update the system context.** The preamble of the spec ("The current system already has...") must reflect the ACTUAL current state, not a stale copy from a previous phase's prompt.

6. **Don't over-specify implementation.** The spec is a REQUIREMENTS document, not an architecture doc. Describe WHAT and WHY, not exactly HOW. The `/architecture` skill handles the how.

---

## Portability Assessment Framework

When evaluating features for pluggability, use this framework:

### Tier 1 — Universal SaaS primitives (every B2B SaaS needs these)
- Authentication & authorization
- Multi-tenancy
- Billing & subscriptions
- Notifications (in-app + email)
- Audit trail
- Comments / activity feeds
- Search
- User preferences / settings

### Tier 2 — Common SaaS patterns (most B2B SaaS products benefit)
- Reporting & analytics dashboards
- Data export (CSV, PDF)
- Webhook integrations (outbound)
- API key management
- Role-based permissions (custom roles)
- Onboarding / setup wizards
- Help center / knowledge base integration

### Tier 3 — Domain-adjacent features (valuable but less portable)
- Workflow automation / rules engine
- Template systems
- Customer portal
- White-labeling
- Marketplace / plugin system

### Tier 4 — Domain-specific features (not portable)
- Industry compliance (HIPAA, SOC2, etc.)
- Domain-specific entities
- Specialized calculations or algorithms

**Prioritize Tier 1 and 2 features** when generating candidates. Tier 3 is acceptable if it builds on Tier 1/2 foundations. Tier 4 should be flagged as non-portable.

---

## Example Session Flow

```
User: /next-phase

[Scout agent analyzes project state]

Orchestrator: "Here's where the project stands: [brief summary].
I've identified 5 candidate directions, scored through lean/profit/sustain/portable lenses:

| # | Candidate               | Effort | Lean | Profit | Sustain | Portable |
|---|-------------------------|--------|------|--------|---------|----------|
| 1 | Notifications & Activity| Medium | +++  | ++     | +++     | +++      |
| 2 | Reporting & Exports     | Medium | ++   | +++    | ++      | +++      |
| 3 | Search & Filtering      | Small  | +++  | +      | +++     | +++      |
| 4 | Webhook Integrations    | Small  | ++   | ++     | +++     | +++      |
| 5 | Test Coverage & DevEx   | Small  | +    | +      | +++     | +        |

Which direction interests you?"

User: "I like 1 but also want some of 2"

Orchestrator: "Great combo — notifications make the platform feel alive, and reporting
makes it indispensable. Let me scope a combined phase..."

[Asks 2-3 scope questions via AskUserQuestion]

Orchestrator: "Here's the outline: [summary]. Ready to generate the spec?"

User: "Go for it"

[Writes claude-code-prompt-phase{N}.md]

Orchestrator: "Done — spec written with 5 sections, 3 new entities, 12 API endpoints,
and 4 ADR requests. Next: /architecture claude-code-prompt-phase{N}.md"
```
