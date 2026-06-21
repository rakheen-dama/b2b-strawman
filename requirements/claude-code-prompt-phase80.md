# Phase 80 — CRM / Sales Pipeline (Deals, Stages & the Win→Proposal Loop)

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-06-21). This is a **foundation-quality, fork-friendly** domain phase: every vertical fork (legal, consulting, accounting, agency) inherits a sales pipeline. It is the **top of the revenue funnel** — the one missing link before the existing `Customer → Proposal → Project → Invoice` chain.
>
> **Scouting note (why this phase exists, and what it must NOT duplicate):** ideation verified the codebase against three candidate domains. **Resource Planning & Capacity** is already ~90% built (the `resource_planning` module: `MemberCapacity`, `ResourceAllocation`, `LeaveBlock`, capacity grid at `/resources`, utilization at `/resources/utilization`) — **do not touch or rebuild it**. **Proposals / Engagement Letters** is already built (`backend/.../proposal/` — `Proposal` entity, DRAFT→SENT→ACCEPTED, `ProposalAcceptedEventHandler` auto-creates a project) — **this phase reuses it, does not reimplement it**. The genuinely **absent** domain, verified by source search, is a **CRM / sales pipeline**: `Customer` carries a `PROSPECT` `LifecycleStatus` but there is **no deal / stage / pipeline / opportunity entity**. Phase 80 fills exactly that gap.

## System Context

Kazi is a mature multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 / Java 25 backend, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway). 79 phases have shipped. The revenue engine is complete end-to-end **except its entry point**:

- **Customers** (`backend/.../customer/`) — `Customer` entity with `LifecycleStatus` enum: `PROSPECT`, `ONBOARDING`, `ACTIVE`, `DORMANT`, `OFFBOARDING`, `OFFBOARDED`, `ANONYMIZED`. A prospect today is a `Customer` in `PROSPECT` state; there is **no separate lead/opportunity record**.
- **Proposals** (`backend/.../proposal/`) — `Proposal` entity (fixed-fee / hourly / retainer / contingency), lifecycle `DRAFT → SENT → ACCEPTED | DECLINED | EXPIRED`, content as Tiptap JSON. `ProposalAcceptedEventHandler` + `ProposalOrchestrationService` create a `Project` on acceptance (`createdProjectId` on `Proposal`), optionally from a project template.
- **Projects / Invoices / Tax / Payments / Retainers / Profitability** — all built downstream of a customer + proposal.
- **Cross-cutting infra this phase reuses (do not rebuild):** custom fields + field packs (Phases 11/23, field-able entities via the field-definition/field-pack system), tags + saved views (Phases 11/23), activity feed + comments (Phase 6.5), audit (Phases 6/69, `AuditService` + metadata registry), notifications (Phases 6.5/24), the **grouped-tabs customer detail page** (Phase 77, `CustomerGroupedTabs`), **pack-seeding** for vertical profiles (Phases 65/66, `PackInstaller` + profile manifests), the **capability / vertical-module gating** pattern (`@RequiresCapability`, `VerticalModuleGuard`, `org_settings.enabled_modules`), and the **legal `intake-triage` AI specialist** (Phase 70/72 — currently a read-only triage memo with no place to persist a deal).

The funnel today **starts mid-stream**: "a `Customer` already exists → write a `Proposal` → it becomes a `Project` → `Invoice`." There is no concept of an **enquiry that has not yet been won**, no **pipeline**, no **weighted forecast**, no **win/loss tracking**. Phase 80 adds that layer and wires its "won" exit into the existing proposal→project orchestration.

### Founder decisions that constrain this phase (2026-06-21 ideation)

- **Lead model = Deal linked to Customer (no separate Lead entity).** A `Deal` (opportunity) **always** belongs to a `Customer`. An inbound enquiry is captured as a single intake action that creates a `PROSPECT` `Customer` **and** an open `Deal`. This deliberately reuses the existing `PROSPECT` lifecycle and avoids Lead↔Customer duplication / dual-mode code paths. (A separate lightweight pre-customer Lead entity and dual-mode deals were both considered and **rejected** for v1.)
- **Win flow = link + reuse the Proposal flow (no new conversion engine).** From a `Deal` you can create/link a `Proposal`. Winning a deal flips `Deal → WON`, nudges the `Customer` `PROSPECT → ONBOARDING`, and relies on the **existing** `Proposal`-accept→`Project` orchestration for project creation. **Do not** build a parallel conversion/orchestration engine, and **do not** auto-create proposal+project+kickoff in one step (full auto-orchestration was rejected as too much v1 surface).
- **Single configurable pipeline per org.** One ordered set of `PipelineStage`s, org-configurable, vertical-seeded. **Multiple pipelines per org is deferred** (design the schema so it is not painful to add later, but ship one).
- **CRM is foundation, default-ON for every fork.** Unlike `resource_planning` (vertical-gated, default-off), the sales pipeline is core practice-management and ships enabled for all tenants. Use the existing capability pattern (a `CRM` / `SALES_PIPELINE` capability) but default it on; do not hide it behind a vertical module flag.
- **`intake-triage` is an integration point, not a rebuild.** The legal AI triage specialist should be *able* to create/populate a `Deal` via the same API, but wiring it is out of scope — just design the `Deal` create API to be callable by it and note the seam.
- **Reporting v1 = summary endpoint + dashboard widget.** Weighted pipeline value, win rate, per-stage totals via a purpose-built summary endpoint. **Full `ReportDefinition`-based pipeline reports are deferred.**

## Objective

Give every firm a single view of its live sales opportunities and close the funnel loop. Specifically: (1) capture enquiries as `Deal`s against a `Customer`; (2) move them across configurable pipeline stages on a Kanban board with weighted value + win-rate visibility; (3) on win, flip the deal, advance the customer lifecycle, and hand off to the **existing** proposal→project chain; (4) make `Deal` a first-class, field-able, taggable, audited, commentable entity that surfaces on the customer page and the dashboard. One new bounded context (`crm` / `deal`), maximal reuse of everything downstream.

## Constraints & Assumptions

- **Reuse, do not duplicate.** `Deal` links to the existing `Customer`; winning reuses the existing `Proposal` → `Project` orchestration; custom fields / tags / saved views / activity / comments / audit / notifications are the **existing** subsystems, registered for a new entity type — not reimplemented. Verify the field-definition / saved-view / audit-metadata registries support registering a new entity type and follow that path.
- **No separate Lead table, no dual-mode deal.** Every `Deal` has a non-null `customerId`. Intake creates customer+deal atomically.
- **Single pipeline, multi-pipeline-ready schema.** Ship one pipeline per org. The `Deal`→`PipelineStage` relationship and stage ownership should not foreclose a future `Pipeline` parent, but no `Pipeline` entity / selector ships in v1.
- **Stage semantics are explicit.** A `PipelineStage` carries an ordered position, a default win-probability %, and a **stage type** (`OPEN` / `WON` / `LOST`) so terminal stages are data-driven, not name-matched. Exactly the `WON`/`LOST` stages drive `Deal.status`.
- **Win/lose is a guarded transition, not a free `stage` write.** Moving a deal into a `WON`/`LOST` stage runs through a transition service (mirroring the existing task/project lifecycle services) that sets `status`, `wonAt`/`lostAt`, requires a `lostReason` on loss, emits events, writes audit + activity, and performs the customer-lifecycle nudge. Re-opening a closed deal is allowed but audited.
- **Money is first-class and tenant-correct.** `Deal.value` is an amount + currency; default the currency from `OrgSettings`. Weighted value = Σ(value × effective probability). Be explicit and consistent about rounding and currency mixing (assume single org currency for v1; document the assumption).
- **Capability gating, default-on.** Gate write endpoints with the existing `@RequiresCapability` pattern using a new `CRM`/`SALES_PIPELINE` capability that is enabled for all tenants by default. Do not introduce a `VerticalModuleGuard` module flag for it.
- **Tenant isolation.** All new tables live in the per-tenant schema (`db/migration/tenant/`), all queries are member/tenant-scoped through the existing `TenantFilter`/`MemberFilter`/`RequestScopes` pipeline. No cross-tenant deal visibility — verify with an isolation test.
- **Migration numbering.** Add a new `db/migration/tenant/V{next}__create_crm_pipeline_tables.sql` (resolve the actual next free `V` number at build time — the latest is `V99__add_retention_clock_to_projects.sql`; do not hardcode `V100` without confirming).
- **Package convention.** Mirror a recent domain package (e.g. `retainer/` or `proposal/`): root entities, `*Repository`, `*Service`, `*Controller`, `dto/`, `event/`, enums in-package. Pick the directory name at `/architecture` (`crm/` recommended, with `Deal`/`PipelineStage` inside).
- **Frontend conventions.** Pages under `frontend/app/(app)/org/[slug]/...`; API module under `frontend/lib/api/` mirroring `capacity.ts`/`schedules.ts`; Next.js 16 (params are Promises); Shadcn UI; reuse the saved-views/tags/custom-fields UI components and the grouped-tabs customer pattern. Route UI design questions through the Shadcn / Next.js conventions.
- **Test strategy.** Backend: full `./mvnw verify` clean; unit + integration tests for deal CRUD, the win/lose transition (incl. customer-lifecycle nudge + proposal linkage), weighted-value/win-rate aggregation, capability gating, and a tenant-isolation test. Frontend/portal: `pnpm lint && pnpm build && pnpm test` green + prettier `format:check`. E2E (Playwright, mock-auth + Keycloak stacks): create enquiry → move across stages → link/send a proposal → win → assert customer advanced + proposal/project path reachable. **"PASS means observed"** — verify browser → backend log → DB, reproduce-before-fix for any bug.

---

## Section 1 — Data Model

### 1.1 `PipelineStage` (org-configurable stages)
- Fields (guidance, finalize at `/architecture`): `id` (UUID), `name`, `position` (int, ordered), `defaultProbabilityPct` (int 0–100), `stageType` (enum `OPEN` | `WON` | `LOST`), `archived` (boolean, soft-hide without breaking historical deals), audit columns.
- Constraints: at least one `OPEN`, and at least one `WON` and one `LOST` stage must exist (seeded). Deleting a stage with deals attached is blocked (reuse the existing delete-guard pattern) — archive instead.
- Seeded per vertical via pack-seeding (Section 6). A sensible org-agnostic default set ships for forks with no vertical profile.

### 1.2 `Deal` (the opportunity / pipeline unit)
- Fields (guidance): `id` (UUID), `customerId` (UUID, **non-null** FK), `title`, `stageId` (FK), `status` (enum `OPEN` | `WON` | `LOST`, derived from the stage's `stageType` on transition), `valueAmount` (decimal), `valueCurrency` (default from `OrgSettings`), `probabilityPct` (int, defaults from the stage, **overridable** per deal), `expectedCloseDate` (LocalDate, nullable), `ownerId` (member UUID, defaults to creator), `source` (configurable string/enum — e.g. Referral, Website, Inbound, Existing-client), `wonAt` / `lostAt` (instants, nullable), `lostReason` (string, required when lost), audit columns (`createdBy`, timestamps).
- A `Deal` is a **field-able** entity (custom fields), **taggable**, and appears in **saved views** — register it with those existing registries rather than adding bespoke columns for ad-hoc attributes.
- Relationship to `Proposal`: a `Deal` may have zero or more associated `Proposal`s (Section 3). Model the link (FK on `Proposal` to `dealId`, or a join) so a deal's proposals are queryable; choose the lower-impact side at `/architecture`.

### 1.3 Effective probability & weighted value
- `effectiveProbability(deal)` = `deal.probabilityPct` if set, else the stage's `defaultProbabilityPct`. `WON` = 100, `LOST` = 0 regardless of override.
- `weightedValue(deal)` = `valueAmount × effectiveProbability / 100`. Pipeline weighted value = Σ over `OPEN` deals.

---

## Section 2 — Deal Lifecycle, CRUD & Transitions (Backend)

### 2.1 Intake (create customer + deal atomically)
- An intake endpoint/operation that, given enquiry details, **either** attaches a `Deal` to an existing `Customer` **or** creates a new `PROSPECT` `Customer` and the `Deal` in one transaction. Reuse the existing customer-creation service; do not duplicate validation.
- This same create path must be callable programmatically (the `intake-triage` AI seam) — keep the request DTO clean and not UI-coupled.

### 2.2 CRUD + filtered list
- Standard create / read / update / delete (soft-delete or guarded hard-delete consistent with the codebase). Update covers value, owner, expected close, probability override, source, title, custom fields, tags.
- A filtered list endpoint (by stage, owner, customer, source, status, date window, tag, saved-view) backing both the board and the list view. Reuse saved-view query infrastructure.

### 2.3 Stage transition (win / lose / move)
- A `DealTransitionService` (mirror `TaskLifecycleService` / project lifecycle services) handles stage moves:
  - Moving within `OPEN` stages: update `stageId`, recompute effective probability, emit `DealStageChangedEvent`, audit + activity.
  - Moving to a `WON` stage: set `status=WON`, `wonAt`, probability→100; **nudge `Customer` `PROSPECT → ONBOARDING`** (only if currently PROSPECT — never downgrade); emit `DealWonEvent`; audit + activity + notification.
  - Moving to a `LOST` stage: require `lostReason`; set `status=LOST`, `lostAt`, probability→0; emit `DealLostEvent`; audit + activity.
  - Re-opening a closed deal (back to an `OPEN` stage): permitted, clears `wonAt`/`lostAt`/`lostReason`, audited.
- All transitions tenant/member-scoped and capability-gated.

### 2.4 Events, audit, notifications
- Register deal events with the **existing** activity feed + audit metadata registry (severity/group) so deals appear in the global audit log and entity activity tabs.
- Notifications (reuse existing channels): deal assigned to a new owner; deal won; (optional, low-cost) deal idle past expected close date — keep idle-nudge **deferred** unless trivial.

---

## Section 3 — Win→Proposal Loop (reuse, do not rebuild)

### 3.1 Create / link a proposal from a deal
- From a `Deal`, the user can create a new `Proposal` pre-populated with the deal's customer + value, or link an existing one. The `Proposal` domain owns drafting/sending/acceptance unchanged.
- Surface the deal's proposals (status chips: DRAFT/SENT/ACCEPTED/…) on the deal detail.

### 3.2 Acceptance closes the loop
- When a linked `Proposal` is **accepted**, the existing `ProposalAcceptedEventHandler` already creates the `Project`. This phase adds: on proposal-acceptance for a deal-linked proposal, also move the `Deal` to `WON` (if not already) so the board reflects reality — implement as a listener on the existing proposal-accepted event, not by modifying proposal internals.
- Conversely, marking a deal `WON` directly does **not** force-create a proposal/project (status-only win is valid); the two paths are complementary. Document the precedence so a deal isn't double-won.

### 3.3 No parallel orchestration
- Do not introduce a second project-creation path, a second customer-activation engine, or a bundled "win→everything" macro. The only new orchestration is the thin, event-driven `Deal↔Proposal`/`Customer-lifecycle` glue above.

---

## Section 4 — Pipeline Aggregation & Reporting (lean)

### 4.1 Pipeline summary endpoint
- A purpose-built summary endpoint returning: per-stage deal count + total value + weighted value; total open weighted pipeline value; win rate over a date window (won / (won+lost) by `wonAt`/`lostAt`); average deal size; optionally average days-to-close. All tenant-scoped, optionally filterable by owner.
- Define the win-rate window and counting rules explicitly (e.g. trailing 90 days by close date) and keep them consistent with what the widget renders.

### 4.2 Dashboard widget
- A pipeline-summary widget on the company dashboard (mirror the existing dashboard widget pattern, e.g. `TeamUtilizationWidget`): open weighted value, deals-by-stage mini-bar, win rate. Admin/owner-scoped consistent with other dashboard widgets.

### 4.3 Deferred
- Full `ReportDefinition`-based pipeline reports, exports, scheduled delivery, forecasting/quota — **out of scope** (note as future; the summary endpoint is the v1 surface).

---

## Section 5 — Frontend

### 5.1 Pipeline board + list (`/pipeline`)
- New route `frontend/app/(app)/org/[slug]/pipeline/page.tsx` with two views:
  - **Kanban board:** columns = ordered `OPEN` stages (+ collapsed WON/LOST), cards = deals (customer name, title, value, weighted/probability, owner avatar). Drag-to-move triggers the transition endpoint; dropping into WON/LOST opens the win/lose confirm (lost requires a reason). Column headers show count + total/weighted value.
  - **List view:** filterable/sortable table reusing the saved-views + tags + custom-field filter components.
- Header shows total open weighted pipeline value + win rate (from the summary endpoint).

### 5.2 Deal detail
- A deal detail surface (sheet or page, match the task/customer detail convention): overview (value, stage, probability, owner, source, expected close, custom fields), stage history, linked customer, linked proposals with status chips + "create/send proposal" action, comments, and an activity tab (reuse `<AuditTimeline>` / activity components).

### 5.3 Customer "Deals" tab
- Add a **Deals** tab to the grouped-tabs customer detail page (Phase 77, `CustomerGroupedTabs`) listing that customer's deals with quick "new deal" intake. This is the natural account view.

### 5.4 Intake action
- A "New enquiry / New deal" entry (from `/pipeline`, the customer page, and global new-menu if present) driving the Section 2.1 intake: pick existing customer or create a PROSPECT inline, set title/value/stage/owner/source.

### 5.5 Settings — stage configuration
- `frontend/app/(app)/org/[slug]/settings/pipeline/page.tsx` (mirror `settings/capacity`): reorder stages, edit name + default probability + stage type, archive stages, with guards (can't remove the last OPEN/WON/LOST, can't delete a stage with deals).

### 5.6 API module
- `frontend/lib/api/crm.ts` (or `deals.ts`) mirroring `capacity.ts`: deal CRUD, list/filter, transition, intake, pipeline summary, stage config, proposal link/create.

---

## Section 6 — Vertical Stage Seeding (pack)

- Seed default `PipelineStage` sets per vertical profile via the existing pack-seeding infra (Phases 65/66, `PackInstaller` + profile manifest), so each fork gets sensible stages on provisioning:
  - **legal-za:** Enquiry → Conflict check → Engagement → (Won) / (Lost) — align names with the `intake-triage` vocabulary.
  - **consulting-za:** Lead → Qualified → Proposal sent → Negotiation → (Won) / (Lost).
  - **accounting-za:** Enquiry → Scoping → Engagement letter → (Won) / (Lost).
  - **default (no profile):** Lead → Qualified → Proposal → Won / Lost.
- Each seed includes default probabilities and the correct `stageType`s. Seeding is idempotent (re-provisioning safe) and follows the existing pack-install pattern — do not hand-write per-tenant SQL.

---

## Out of Scope

- **Separate pre-customer Lead entity** and any Lead↔Customer conversion plumbing (decided: Deal-linked-to-Customer).
- **Multiple pipelines per org** and a pipeline selector (schema stays multi-pipeline-ready; only one ships).
- **Full win auto-orchestration** (auto proposal+project+kickoff in one click) — reuse the proposal-accept path only.
- **Email / inbox auto-capture of leads** — depends on the absent inbound-email/communication hub (separately deferred).
- **Lead scoring / AI auto-qualification** beyond the `intake-triage` integration seam (which this phase only leaves a door open for, not builds).
- **Marketing automation:** sequences, campaigns, drip emails, public web-form lead-capture pages.
- **Forecasting / quota management / sales targets.**
- **Full `ReportDefinition` pipeline reports, exports, scheduled delivery** (v1 = summary endpoint + widget).
- **Portal exposure of deals** (deals are firm-internal; the client portal does not show pipeline).
- **Wiring the legal `intake-triage` specialist** to persist deals (integration point designed, not implemented).

---

## ADR Topics to Address

- **ADR-313**: Lead model — `Deal`-linked-to-`Customer` (reusing the `PROSPECT` lifecycle) vs a separate Lead entity vs dual-mode; why the single-entity model is correct for the foundation and how intake creates customer+deal atomically.
- **ADR-314**: Pipeline & stage model — single configurable pipeline with `stageType` (`OPEN`/`WON`/`LOST`) and data-driven terminal semantics; probability source (stage default vs deal override); multi-pipeline-ready schema without shipping multi-pipeline.
- **ADR-315**: Win→Proposal/Project conversion — reuse of the existing `Proposal`-accept→`Project` orchestration via events; the customer `PROSPECT→ONBOARDING` nudge; precedence rules so a deal isn't double-won; why no parallel conversion engine.
- **ADR-316**: `Deal` as a registered field-able / taggable / saved-view / audited entity — how it plugs into the existing field-definition, saved-view, and audit-metadata registries rather than bespoke implementations.
- **ADR-317**: Capability gating & default-on — why CRM ships as a default-enabled foundation capability (contrast with the vertical-gated, default-off `resource_planning` module) and how stages are vertical-seeded via pack-install.
- **ADR-318**: Pipeline metrics — weighted-value and win-rate definitions, date-window/counting rules, currency-mixing assumption (single org currency v1), and the summary-endpoint-over-`ReportDefinition` decision for v1.

---

## Style & Boundaries

- Follow `backend/CLAUDE.md` (Spring Boot 4, Hibernate 7, multitenancy, `TenantFilter`/`MemberFilter`/`RequestScopes`, delete-guard, audit/activity patterns) and `frontend/CLAUDE.md` (Next.js 16, Keycloak, Shadcn).
- **Reuse over rebuild:** `Customer`, `Proposal`, custom fields/field packs, tags, saved views, activity, comments, audit, notifications, grouped-tabs customer page, pack-seeding, capability gating, dashboard-widget pattern, the existing lifecycle-transition-service shape.
- **One bounded context** (`crm`/`deal`). New per-tenant tables only; no changes to `Customer`/`Proposal` internals beyond the thin event-driven glue and the deal↔proposal link.
- Honour the project quality gates: backend → `./mvnw verify` clean (full suite); frontend/portal → `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; tenant-isolation test mandatory; **"PASS means observed"** (browser → backend log → DB), reproduce-before-fix.
- Frontend design (board, deal detail, intake) goes through Shadcn/Next.js conventions and matches the existing app shell + Phase 77 detail-page patterns.

---

## Next step

`/architecture requirements/claude-code-prompt-phase80.md` — generates the architecture section + ADRs (313–318). Then `/breakdown 80` for epics/slices (target ~8 epics / ~16 slices). Reproduce-before-fix applies to any bug surfaced during build.
