# Kazi — Tech Presentation (Developers / Collaborators / First Hire)

> Audience: engineers who might collaborate, contract, or join — meetup talks, CTO-peer conversations, hiring. Goal: credibility + curiosity ("I want to see that codebase"). This is where the **horizontal platform story** lives — it stays out of firm-facing materials. 12 slides. Rendered deck: `decks/tech-deck.html`.
>
> Tone: engineer-to-engineer, numbers over adjectives, honest about trade-offs. The hook is the *process* — one person + an agent fleet shipping a production-grade vertical SaaS — with the architecture as proof it isn't slop.

---

**Slide 1 — Title.**
*Kazi: a production SaaS built by one person and a fleet of agents.* Practice management for SA law firms — and the build process is the interesting part.

**Slide 2 — The product in one slide.**
Multi-tenant practice management: matters, time → fee notes → payments → collections; LPA s86 trust accounting; FICA/POPIA compliance; client portal; document generation; Xero integration. Lighthouse vertical: South African small law firms.
Speaker note: 60 seconds max. The audience is here for the how.

**Slide 3 — The numbers.**
~18 months · **84 phases** shipped · **1,500+ merged PRs** · **~285 ADRs** · **135+ tenant schema migrations** · 3 deployable apps (frontend, portal, backend) + shared packages, one monorepo.
Speaker note: All numbers are real and checkable in the repo history — that's the point. Update from git before each presentation.

**Slide 4 — Architecture.**
Next.js 16 / React 19 / TypeScript frontend + client portal · Spring Boot 4 / **Java 25** backend · PostgreSQL 16, **schema-per-tenant** (Hibernate + Flyway) · Keycloak OIDC (orgs, RBAC, BFF pattern via gateway) · S3 for documents · pnpm workspace monorepo with shared UI/domain packages.
Speaker note: Deliberately boring choices, executed deeply. The interesting decisions are the next three slides.

**Slide 5 — Multitenancy: schema-per-tenant, all the way down.**
One schema per firm — hard isolation for legal data (a trust ledger is the last place you want a missing WHERE clause) · Tenant context carried on Java 25 **ScopedValue** (structured, immutable, virtual-thread-friendly — not ThreadLocal) · Flyway migrates every tenant schema; provisioning is an API call · Trade-off owned: migration fan-out cost vs. blast-radius containment — for compliance-heavy verticals, isolation wins.

**Slide 6 — The vertical-fork thesis.**
The platform is horizontal (projects, customers, time, billing, documents, audit); verticals are **profile packs** — terminology, modules, templates, compliance packs (legal-za: trust accounting, FICA, court calendar; accounting-za; consulting-za) · Module registry + capability-based RBAC gate vertical surface area per tenant · Legal was chosen as lighthouse *because* it's the hardest fork (statutory trust accounting) — surviving it validates the foundation.

**Slide 7 — The AI layer (the part that's actually new).**
Kazi exposes an **MCP server**: a firm's staff point their own Claude at their practice data · OAuth + per-firm POPIA consent gating; read-only by default · A distributed **skill pack** (kazi-legal-za): fee-note drafting, s86 trust screening, FICA gap review, email filing, intake triage · Write-back exists but is **gated**: the AI proposes, a human approves inside the app — an "AI gate" entity with full audit · In-app AI skills with per-skill cost metering.
Speaker note: Architecture beat: the AI layer sits on the same service layer + read-model as the UI — no parallel data path, no prompt-injected SQL.

**Slide 8 — The build process: an agent fleet with a constitution.**
Pipeline of custom skills: **/ideate → /architecture → /breakdown → /phase → /epic** — conversation to requirements to ADRs to epics to merged PRs · Each epic: an agent in an isolated git worktree; scout → builder pipelines; orchestrator owns merges · Every agent-authored PR passes review gates (independent reviewer agents + CodeRabbit) before merge.
Speaker note: The skills are versioned in the repo — the dev process itself is code-reviewed.

**Slide 9 — Keeping an agent fleet honest.**
Non-negotiable quality gates in CLAUDE.md: full `mvnw verify` + full frontend suite before merge — targeted tests never gate a merge · **"PASS means observed"**: QA agents drive a real browser against a real stack (mock-IDP Docker stack); inferring pass from "code looks right" is forbidden · **Reproduce-before-fix**: no fix without a local repro · Merge-gate hook physically blocks `gh pr merge` unless the bar is met · Lessons file: every correction becomes a standing rule.
Speaker note: This slide is the talk. Agent throughput is easy; agent *trust* is the engineering problem. These rules each exist because of a specific incident — tell one.

**Slide 10 — What the process caught (honest war stories).**
A "green" cycle where agents only ran same-package tests — broke a test elsewhere; rule: full verify or it didn't happen · Agents claiming PASS on unobserved behaviour — rule: browser-observed evidence or it's DEFERRED · Debt-register "fixes" that would have broken prod — rule: register entries are hypotheses until reproduced.
Speaker note: Pick 2–3, tell them as stories. Self-deprecating honesty differentiates this from AI-hype talks.

**Slide 11 — Where it's going.**
Now: 2–3 design-partner firms this quarter (GTM, not features) · Next build: auditor-defensible data take-on (migration wizard with trust control totals) · The fork thesis: same foundation → accounting firms, consultancies · Open engineering questions: multi-region, WIP take-on, deeper accounting integrations.

**Slide 12 — Work with me.**
Looking for: engineers curious about agent-orchestrated development at production quality; a possible first hire as design partners convert · What you get: a codebase with 285 ADRs of recorded *why*, a build process that survives scrutiny, and a real vertical market · hello@kazi.africa · kazi.africa.
Speaker note: For meetups, swap the hire framing to "come argue with me about the process — the repo receipts are real."

---

## Presenting variants

- **Meetup/conference (25 min):** slides 1, 3, 8, 9, 10 are the core; compress 4–7 into two slides.
- **CTO peer / diligence-adjacent:** lead 4–7 (architecture), keep 9 (controls), drop 12's hire framing.
- **Hiring conversation:** full deck, linger on 8–10, end on 12.
