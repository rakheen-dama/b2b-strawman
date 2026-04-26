# Phase 69 Ideation — Firm Audit View
**Date**: 2026-04-20

## Lighthouse domain
All three verticals (`legal-za`, `accounting-za`, `consulting-za`). Legally-sensitive writes (matter-closure overrides, trust approvals, data-protection actions) have been piling up in `audit_events` since Phase 6 (Feb 2026) with no admin UI to read them. Compliance questions currently require raw `psql`.

## Decision
Phase 69 ships **firm-side audit view only** — portal activity trail is explicitly deferred. Scope:
1. **Backend extensions** — facet endpoints for filter dropdowns, streamed CSV + PDF export, event-type metadata registry (severity + group + human label), severity filter support.
2. **Global audit log page** at `/settings/audit-log` — filters, paginated table, expandable rows, generic diff / JSON viewer, 4 built-in filter presets (Sensitive / Compliance / Security / Financial approvals).
3. **Reusable `<AuditTimeline>`** dropped into Customer / Project / Invoice / Trust-transaction / Matter-closure / Proposal / Information-request detail pages as an "Audit" tab.
4. **DSAR pack integration** — new `audit-trail/` folder in Phase 50 export pack (events.json + events.csv + README.txt). Unsanitised per POPIA §23.
5. **Sensitive-events dashboard widget** — small tile on company dashboard showing last-7-day counts by severity + top-5 recent WARNING/CRITICAL.
6. **Admin-POV 30-day QA capstone** — new script, screenshots, gap report.

## Key design decisions
1. **Portal activity trail explicitly out.** Founder call 2026-04-20 — keep Phase 69 tight, defer the "client sees firm's actions" story.
2. **Generic diff viewer, not per-event templates for v1.** ~60 event types; handcrafted template-per-event costs too many slices and ages fast. Candidate ADR.
3. **Severity derived at read time, not persisted.** Immutable historical rows pre-date the classification; deriving from an in-code registry at read time keeps historical rows untouched and lets classifications evolve. Candidate ADR.
4. **DSAR audit-trail is unsanitised.** POPIA §23 entitles subject to their full record — internal notes included. Contrast with Phase 68 portal sanitisation. Candidate ADR.
5. **PDF export rides existing Tiptap pipeline** (Phase 12 / 31 / 42) — new `audit-export` template in the doc-template pack. No new PDF library. Candidate ADR.
6. **Audit export is itself auditable** — `audit.export.generated` event. Reflexive logging closes the "did anyone extract our data?" question. Candidate ADR.
7. **Event-type metadata registry is Java in-code** — no migration unless builder strongly prefers table-backed. Longest-prefix-wins. Single source of truth for label + severity + group.
8. **Capability is `TEAM_OVERSIGHT`** — existing; no new capability introduced.
9. **No changes to event writers.** Missing coverage gets logged in gap report for Phase 70, not fixed opportunistically.
10. **10 000-row cap on PDF export**, CSV uncapped (streamed). PDF is the legal artefact; CSV is internal review.

## Scope snapshot
- 6 epics, ~10 slices (A1–A2, B1–B2, C1–C2, D1, E1, F1–F2)
- 0 new entities; at most 1 migration (V108) if event-type registry goes table-backed, default is zero migrations
- 5 new backend endpoints (3 facets + 2 exports) + 1 filter-param extension (severity)
- 1 new frontend page + 1 reusable component + 7 detail-page integrations + 1 dashboard widget
- `audit-trail/` folder added to DSAR export pack (Phase 50 extension, not rewrite)

## Explicitly parked
- Portal activity trail (client sees firm actions).
- Template-per-event-type registry.
- Streaming / websocket live tail.
- Alert routing (email / Slack / webhook push on sensitive events) — integrations phase.
- Retention-policy UI.
- Saved custom filters.
- Behavioural analytics / heatmaps.
- Tamper-proof hash-chained log.
- Audit in the Phase 19 reporting engine.
- Full-text search across `details` JSONB.
- Cross-tenant platform-admin audit view.

## Phase roadmap after 69
- **Phase 70** candidates (unchanged from Phase 68 ideation): Integrations layer (Xero / Sage Pastel / calendar / Slack) — commercial unlock for `accounting-za` and `consulting-za`. Alternative: AI depth, or a polish phase driven by Phase 68/69 gap reports.
- Portal activity trail likely returns as a Phase 71+ candidate once integrations or AI are dispatched.

## Domain notes
- Matter-closure override events (Phase 67) write `override_used=true` + justification verbatim into `details.justification`. The Phase 67 gap report flagged this as PASS at the write layer — Phase 69 is the read layer.
- Trust transaction approvals (Phase 60), data-protection actions (Phase 50), member role changes (Phase 41), security events (login failure / permission denied — Phase 52) all land in `audit_events` today and become first-class filterable / exportable in this phase.
- POPIA §23 obliges responsible parties to provide a data subject with their full record on request — Phase 50's DSAR pack currently excludes the audit trail; Phase 69 closes that gap.

## Next step
`/architecture requirements/claude-code-prompt-phase69.md`
