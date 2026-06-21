# Phase 80 Ideation — CRM / Sales Pipeline — 2026-06-21

**Output:** `requirements/claude-code-prompt-phase80.md` (READY for `/architecture`).
**Type:** foundation-quality domain phase (top of revenue funnel). Fork-portable to all verticals.
**Builds on prior ideation:** independent of Phase 79 (auth hardening, in flight: 568 done, 569–572 open).

## Lighthouse domain
SA small-to-medium law firms, but explicitly foundation-first (legal/consulting/accounting/agency all share an intake pipeline).

## Decision path (and the two near-misses)
Founder picked **Resource Planning & Capacity** first — but scouting found it **~90% already built**
(`resource_planning` module: `MemberCapacity`/`ResourceAllocation`/`LeaveBlock`, capacity grid `/resources`,
utilization `/resources/utilization`, full CRUD, dashboard widget; gated default-off, on for consulting-za).
Second candidate **Proposals/Engagement Letters** — also **already built** (`proposal/`, DRAFT→SENT→ACCEPTED,
`ProposalAcceptedEventHandler` auto-creates a project). The CLAUDE.md "check what's built first" gate caught both.
Re-scouted for verified-absent domains → **CRM / sales pipeline is genuinely absent** (Customer has a `PROSPECT`
status but no deal/stage/opportunity entity). Founder chose it. Comms-hub/inbound-email (absent, heavier,
vertical-variable) and knowledge-base (absent, weak fit) were the also-rans.

## Why CRM wins
The one missing link in the revenue funnel. Today the chain starts mid-stream (Customer already exists →
Proposal → Project → Invoice). CRM adds the top: enquiry → qualify → deal → send existing Proposal → won →
convert. Maximal reuse, revenue-proximate, every fork needs it.

## Key design preferences (locked)
- **Lead = Deal linked to Customer** (no separate Lead entity, no dual-mode). Intake creates PROSPECT customer + Deal atomically. Reuses existing lifecycle.
- **Win flow = link + reuse Proposal flow.** Win flips Deal→WON, nudges Customer PROSPECT→ONBOARDING, reuses existing Proposal-accept→Project orchestration. No parallel engine, no full auto-orchestration.
- **Single configurable pipeline** (multi-pipeline-ready schema, but one ships). Stages have `stageType` OPEN/WON/LOST (data-driven terminals) + default probability.
- **Foundation, default-ON** capability (`CRM`) — contrast `resource_planning` (vertical-gated, default-off). Stages vertical-seeded via pack-install.
- **`Deal` is a registered field-able/taggable/saved-view/audited entity** — plug into existing registries, don't rebuild.
- **Reporting v1 = summary endpoint + dashboard widget**; full ReportDefinition reports deferred.
- `intake-triage` AI specialist = integration seam only (Deal create API callable by it), not wired this phase.

## 8 epics (approx, for /breakdown)
1. Deal + PipelineStage entities + migration + capability reg + default stage seed
2. Deal CRUD + intake (customer+deal atomic) + filtered list
3. DealTransitionService (win/lose/move) + customer nudge + events/audit/activity
4. Deal↔Proposal link + win-loop event glue (proposal-accept → deal WON)
5. Field/tag/saved-view/audit-metadata registration for Deal
6. Pipeline summary aggregation (weighted value, win rate) + dashboard widget
7. Frontend — pipeline board (Kanban) + list + intake + settings stage config
8. Frontend — deal detail + Customer "Deals" tab; + vertical stage packs + QA capstone

## ADRs: 313 (lead model) · 314 (pipeline/stage) · 315 (win→proposal) · 316 (Deal-as-field-able-entity) · 317 (capability default-on + vertical seed) · 318 (metrics defs)

## Domain notes
- Vertical stage seeds: legal Enquiry→Conflict-check→Engagement; consulting Lead→Qualified→Proposal→Negotiation; accounting Enquiry→Scoping→Engagement-letter; default Lead→Qualified→Proposal.
- Deals are firm-internal — **no portal exposure**.
- Latest tenant migration is `V99`; resolve next free `V` at build (don't hardcode V100).
- **Lesson reinforced:** scout the codebase for the chosen domain BEFORE writing the spec — two of three front-runners were already built. /ideate nearly produced a duplicate-Phase spec twice.
