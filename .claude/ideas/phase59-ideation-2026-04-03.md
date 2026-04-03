# Phase 59 Ideation — User Help Documentation Site
**Date**: 2026-04-03

## Decision
Standalone Nextra doc site at `docs.heykazi.com` with comprehensive feature guides, vertical-specific sections, and contextual deep-links from the main app.

## Rationale
58 phases of deep functionality, zero user-facing documentation. Product approaching production with real tenants. Self-service docs critical for keeping support costs near zero at 5-20 tenant scale. Inline contextual help (Phase 43) and AI assistant (Phase 52) provide in-context guidance but can't replace a browsable, searchable knowledge base.

### Key Decisions
1. **Separate doc site** (not in-app) — `docs.heykazi.com` as standalone Nextra project. Founder explicitly prefers this over an embedded help center.
2. **Nextra 4** over Mintlify/Fumadocs/Docusaurus — same Next.js stack, MDX in monorepo, Vercel deployment, zero cost.
3. **Platform-authored** content only — no tenant-editable help. One well-maintained content set > per-tenant authoring infrastructure at this scale.
4. **Full content** — agents draft all ~26 articles from codebase knowledge. Founder reviews. Not just scaffolding.
5. **Text-only** for v1 — no screenshots (go stale), no videos. May add later once UI stabilizes post-launch.
6. **Contextual deep-links** — wire existing Phase 43 `HelpTooltip` and `EmptyStateCard` components to doc site pages via `docsPath` prop. ~20 link mappings.
7. **Vertical-aware** — accounting guides cover SARS deadlines, recurring engagements, compliance packs. Legal guides are stubs (Phase 55 not built yet).

## Founder Preferences
- Separate doc site, not in-app (confirmed)
- Nextra as doc framework (confirmed)
- Platform-authored only (confirmed)
- Full content with agent drafting (confirmed)
- Text-only, no screenshots for v1 (confirmed)

## Phase Roadmap (Updated)
- Phase 57: Tenant Subscription Payments (complete)
- Phase 58: Demo Readiness & Admin Billing Controls (nearly complete — 431B remaining)
- **Phase 59: User Help Documentation Site** (spec written)
- Phase 55: Legal Foundations (specced, not started)
