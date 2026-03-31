# Series 1: "One Dev, 843 PRs"

Building a production B2B SaaS platform with AI coding agents. No hype. Just what happened.

## Posts

| # | Title | Status | Words |
|---|-------|--------|-------|
| 01 | [I Shipped 843 Pull Requests in 8 Weeks](01-i-shipped-843-prs-in-8-weeks.md) | Draft | ~2,800 |
| 02 | [The Scout-Builder Pattern](02-scout-builder-pattern.md) | Draft | ~2,500 |
| 03 | [Model Tiering: When to Use the Expensive Model](03-model-tiering.md) | Draft | ~2,100 |
| 04 | [Phase Execution: From Architecture Doc to 17 Merged PRs](04-phase-execution.md) | Draft | ~3,000 |
| 05 | [The Reviewer is the Product](05-the-reviewer-is-the-product.md) | Draft | ~2,600 |
| 06 | [Skills, Hooks, and Memory](06-skills-hooks-and-memory.md) | Draft | ~2,400 |
| 07 | [What AI Agents Can't Do](07-what-ai-agents-cant-do.md) | Draft | ~2,500 |

**Total: ~18,000 words across 7 posts**

## Series Arc

- **01**: The hook. Origin story, real numbers, honest failures. Introduces all three collaboration modes (pipeline, ideation, maintenance).
- **02**: The technical breakthrough — context window management via scout-builder split.
- **03**: Cost optimization — not all tasks need the expensive model. The invoicing exception.
- **04**: End-to-end walkthrough of Phase 8 (17 slices). The `run-phase.sh` bash loop. Why sequential beats parallel. When it goes wrong.
- **05**: The counterintuitive argument — invest more in review than generation. Real bugs caught: URL leaks, SSTI, permission gaps, isolation violations.
- **06**: The meta-system. `/ideate` as product co-design, `/architecture` as pair-design, code quality discipline (shared utilities, constructor bloat, refactoring). How the system improves itself.
- **07**: The capstone. What AI fundamentally can't do: product decisions, architecture evaluation, organizational navigation, production learning, conviction under pressure. What this means for developer careers.

## Publishing Plan

- Platform: Substack / Beehiiv (TBD)
- Cross-post: Dev.to, Hashnode
- Frequency: Biweekly (one post every two weeks)
- Launch with Post 01
- Full series runs ~14 weeks

## Before Publishing Checklist

- [ ] Verify all numbers against latest git stats (843 PRs, 1764 commits, dates, LOC)
- [ ] Add actual API cost figures or realistic ranges
- [ ] Add screenshots / diagrams (brief example, pipeline diagram, phase 8 task file)
- [ ] Review tone — honest, not self-deprecating; confident, not braggy
- [ ] Proof-read for factual accuracy (ADR numbers, entity counts, phase details)
- [ ] Add newsletter subscribe CTA and link to template repo
- [ ] Create Open Graph images for social sharing
- [ ] Set up cross-posting workflow (canonical URL on own domain)
