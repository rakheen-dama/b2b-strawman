# Phase 66 Ideation — `consulting-za` Vertical Profile (Pack-Only Agency Content)
**Date**: 2026-04-17

## Decision
Phase 66 closes the third vertical demo rail (agency/consulting) as **pack-only content** for a new `consulting-za` profile, plus one small UI widget (team utilization). No new backend entities, services, or migrations. Targets the Zolani Creative 90-day lifecycle script.

## Roadmap (agreed order 1 → 3 → 2 → 4)
1. **Phase 66 — Agency vertical pack** (this phase)
2. **Next: Legal depth-II** — conveyancing template, matter closure workflow, disbursements module, deadline-to-calendar scheduling
3. **After: Integrations layer** — real Xero / QuickBooks / calendar / Slack adapters beyond BYOAK stubs
4. **Later: AI depth** — drafting (proposals, letters, summaries), ingestion, agentic workflows with human-in-the-loop

## Rationale for Phase 66 pick
- Demo-readiness QA cycle (2026-04-12) flagged 8 gaps making the agency rail look generic. Every gap is content (field packs, rate cards, templates, automation, retainer configs, proposal content), not missing features.
- Platform primitives already cover agency ops: `RetainerAgreement` (Phase 17), `Proposal` (Phase 32), `UtilizationService` (Phase 38), automations (Phase 37), Tiptap templates + clauses (Phases 12/27/31). No backend work needed.
- Exercises the brand-new Phase 65 pack install pipeline under a third vertical without inventing new pipeline work.
- Closes the demo story so all three rails (legal / accounting / agency) have parity before Phase 67 legal depth.

## Key Design Decisions
1. **Pack-only, no backend module.** Consulting doesn't have unique primitives the way legal (trust accounting) or accounting (deadlines) do. Captured as an ADR candidate.
2. **`consulting-za` coexists with `consulting-generic`.** Generic profile preserved as future international-fork shell, untouched this phase. Naming pattern `{vertical}-{country}` layered over `{vertical}-generic` — candidate ADR.
3. **`campaign_type`, `channel`, `deliverable_type`, `retainer_tier`, `creative_brief_url` stay as custom fields.** Phase 63 only graduated universal fields. These are agency-specific.
4. **Terminology stays light.** Only 3 overrides (Customer→Client, Time Entry→Time Log, Rate Card→Billing Rates). No Project→Engagement — legal's over-override in Phase 64 was a cautionary tale.
5. **One UI item, profile-gated not module-gated.** Utilization widget reuses Phase 38 endpoint + Phase 53 chart primitive; visible only under `consulting-za` via a `useProfile()` check or `<ModuleGate>` wrapper.
6. **Campaigns + Creative Review Rounds explicitly parked.** Both interesting but architecturally non-trivial — separate future phase.

## Scope Snapshot
- 1 profile manifest + 2 field packs + 1 rate pack + 1 project template pack (5 templates) + 1 automation pack (6 rules) + 1 document template pack (4 templates) + 1 clause pack (8 clauses) + 1 request pack + 1 terminology key
- 1 dashboard widget (team utilization, profile-gated)
- QA lifecycle script retarget + screenshot baselines
- Approx 6 epics, ~11 slices

## Vertical Gap Closure (post Phase 66)
- Agency demo has content parity with legal/accounting
- Still missing (deliberately): campaigns-above-projects, creative review rounds, agency-specific dashboards beyond utilization widget
- These become ideation input for a future dedicated agency phase if real customer feedback pulls that way

## Next Step
`/architecture requirements/claude-code-prompt-phase66.md`
