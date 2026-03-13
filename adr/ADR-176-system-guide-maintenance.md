# ADR-176: System Guide Maintenance Strategy

**Status**: Accepted
**Date**: 2026-03-12
**Phase**: Phase 45 — In-App AI Assistant (BYOAK)

## Context

The AI assistant needs deep knowledge of the DocTeams platform: all 77+ pages, navigation zones, settings areas, common workflows, and domain terminology. This knowledge is provided via a static markdown document (the "system guide") included in the system prompt for every conversation. The question is how this guide is authored and maintained as the platform evolves across phases.

The system guide lives at `backend/src/main/resources/assistant/system-guide.md` and is loaded as a classpath resource at startup. It should be 3,000-5,000 tokens — comprehensive enough to be useful but small enough to leave room in the context window for conversation history and tool results.

## Options Considered

### 1. **Fully manual markdown (chosen for v1)** — human-authored, developer-maintained

The system guide is a hand-written markdown file. Developers update it when features are added, pages are renamed, or workflows change. The content includes navigation structure, page descriptions, common workflows, and domain terminology.

- Pros:
  - Workflow descriptions require human understanding — automation would produce generic text
  - Full control over token budget — a human can prioritize what matters
  - No tooling dependencies — works immediately
  - Simple to review in PRs alongside feature changes
- Cons:
  - Risk of drift — developers forget to update the guide when adding features
  - Manual effort for each phase
  - No guarantee of completeness

### 2. **Semi-automated via dev skill (deferred, not rejected)** — `/refresh-ai-guide` that crawls route files and nav config

A Claude Code skill that reads `nav-items.ts`, route files, component dialog titles, and form fields to regenerate the navigation and pages sections of the guide. Manually-written sections (workflows, terminology) are preserved via section markers.

- Pros:
  - Navigation structure and page inventory are always up to date
  - Reduces manual effort for structural changes
  - Preserves human-authored workflow descriptions
- Cons:
  - Requires building and maintaining the skill
  - Generated content may not describe features well (dialog titles are not feature descriptions)
  - Blocks the phase on tooling development
  - Must handle edge cases (dynamic routes, conditional nav items, settings sub-pages)

### 3. **Runtime-generated from route metadata** — assembled dynamically from code annotations

The system guide is generated at application startup by introspecting route metadata, controller annotations, and a feature registry. No static file — the guide is assembled in memory.

- Pros:
  - Always perfectly in sync with the codebase
  - No manual maintenance
  - Could include feature flags and plan-tier gating
- Cons:
  - Route metadata on the frontend (Next.js) is not available to the backend
  - Controller annotations describe API endpoints, not user-facing features or workflows
  - Would require a new metadata annotation system across the codebase
  - Workflow descriptions cannot be auto-generated — they require domain knowledge
  - Significant engineering effort for marginal accuracy gains over manual authoring

## Decision

Use fully manual markdown for v1, with a documented structure that supports future automation. The system guide is hand-written and maintained by developers as part of each phase's deliverables.

## Rationale

The system guide's value comes from three categories of content: (1) navigation structure, (2) page/feature descriptions, and (3) common workflows. Category 1 could be automated. Categories 2 and 3 require human understanding of the domain — how professional services firms use the platform, what workflows they follow, and what terminology matters. Automating the wrong categories would produce a guide that is structurally complete but practically useless.

For v1, the manual approach is the fastest path to a working assistant. The guide is ~3-5K tokens (roughly 2-3 pages of markdown). Writing it once based on the current platform state takes 1-2 hours. Updating it per phase takes 15-30 minutes. This is acceptable maintenance burden.

The semi-automated skill (`/refresh-ai-guide`) is documented as a follow-up concern. The guide's section structure (Navigation, Pages, Workflows, Terminology) is designed with clear section markers so that a future skill can regenerate the navigation and pages sections while preserving the manually-written workflows and terminology.

## Consequences

- **Positive**: No tooling dependencies, full control over content quality, immediate availability, predictable token budget.
- **Negative**: Risk of drift if developers forget to update the guide. Mitigated by including "update system guide" as a checklist item in phase task files.
- **Neutral**: The guide structure uses section markers (`<!-- AUTO:NAV -->`, `<!-- MANUAL:WORKFLOWS -->`) that a future automation skill can key on, even though the markers are not functional in v1.
