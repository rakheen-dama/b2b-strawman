# Phase 68 Ideation — Portal Redesign & Vertical Parity
**Date**: 2026-04-18

## Lighthouse domain
All three verticals (`legal-za`, `accounting-za`, `consulting-za`). Portal has been generic since Phase 22; six months of vertical work shipped firm-side only. Phase 68 brings portal up to parity + redesigns nav + establishes client-POV QA cycle cadence.

## Decision
Phase 68 ships:
1. **Nav restructure** — top horizontal (3 items) → slim left rail (scalable to 10+). Client-first visual identity, NOT mirror of firm app's zoned sidebar/command palette (Phase 44).
2. **Portal trust ledger** (`legal-za`) — balance + transactions + Statement-of-Account documents list.
3. **Portal retainer usage** (`consulting-za` + `legal-za`) — hour bank + consumption log + renewal date.
4. **Portal deadline visibility** (`accounting-za` + `legal-za`) — polymorphic list: filing + court + prescription + opt-in custom-field dates.
5. **Portal notifications** — weekly digest + per-event nudges via existing Phase 24 `PortalEmailService` + preferences page.
6. **Mobile polish pass** across ALL portal pages (new + existing) — screenshot baselines at sm/md/lg.
7. **Client-POV 90-day QA script + screenshots + gap report** — first client-perspective lifecycle script.

## Roadmap (agreed order)
1. Phase 67 — Legal depth-II (in flight — started this week)
2. **Phase 68 — Portal redesign + vertical parity** ← THIS
3. Phase 69 — Audit view (firm admin + portal activity trail) — user-requested split-out
4. After: Integrations layer (Xero / Sage Pastel / calendar / Slack) per original Phase 67 ideation roadmap

## Key design decisions
1. **Nav = slim left rail (option B)**. Founder chose B over mirror-firm-app (A) or grouped top-nav (C). Portal must feel different from firm tool — client-first, skim-use, lower density.
2. **No new backend entities.** All portal surfaces = read-model extensions + sync handlers + controllers + frontend. Pattern per Phase 7 / 22 / 25 / 28 / 32 / 34. Candidate ADR.
3. **All three verticals in one phase (option All).** Skipping one would leave that vertical's portal story visibly half-baked during QA cycles.
4. **Tight redesign scope + mobile polish bundled into same phase.** Existing pages get responsive pass in Epic F alongside nav work; not a separate phase.
5. **Description sanitisation** for trust transactions + retainer consumption (strip `[internal]`, truncate 140 chars, fallback). Candidate ADR — applied anywhere firm-internal text surfaces to portal.
6. **Retainer member display on portal** = `FIRST_NAME_ROLE` default (e.g. "Alice (Attorney)"). Configurable via OrgSettings. Candidate ADR.
7. **Custom-field deadlines are portal-opt-in** via `FieldDefinition.portalVisibleDeadline` flag. Prevents signal-over-noise rot. Candidate ADR.
8. **No double-send rule** for notifications — if event already emails (Phase 24/28/32/34), Phase 68 uses the existing path; only new events (trust/deadline/retainer) get new channels. Candidate ADR.
9. **Polymorphic `portal_deadline_view` table** — one table, many firm sources. Candidate ADR.
10. **Explicitly client-first identity** — no command palette, no dark mode, no zoned sidebar. Simpler than firm tool by design.

## Scope snapshot
- 7 epics, ~14 slices
- No new domain entities; 4 new portal read-model tables + 1 polymorphic + prefs
- 3 new firm-side OrgSettings fields + 1 new `FieldDefinition` flag (small firm-side additions)
- 6 new portal pages + nav shell rewrite
- Weekly digest scheduler + 3 new portal-specific notification channels
- Playwright baselines for 3 breakpoints × all portal pages
- 90-day client-POV lifecycle script

## Explicitly parked
- **Audit view (firm + portal activity trail)** → Phase 69.
- Multi-contact / per-client portal roles; two-way messaging / DM; i18n; PWA / offline; disbursement portal view (follows Phase 67 stabilisation); Statement-of-Account scheduled auto-delivery; command palette; dark mode; portal search; multi-currency.

## Domain notes
- Portal today has **zero visibility into six months of vertical work**. Every QA cycle will surface this until Phase 68 ships.
- Notifications are the re-engagement multiplier. Clients without an active info request / pending acceptance have no reason to come back today.
- First-ever client-POV lifecycle script — expect gap report to reveal 5–10 UX issues that feed a polish slice or Phase 69 scope.

## Next step
`/architecture requirements/claude-code-prompt-phase68.md`
