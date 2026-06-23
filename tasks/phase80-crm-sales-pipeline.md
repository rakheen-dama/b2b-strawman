# Phase 80 — CRM / Sales Pipeline

Phase 80 adds the missing top-of-funnel layer to Kazi's revenue engine: a single org-configurable sales pipeline of `PipelineStage`s and a first-class `Deal` (opportunity) entity that always belongs to a `Customer`. It is a foundation-quality, fork-friendly domain (default-on for every vertical) that reuses everything downstream — winning a deal flips it WON, nudges the customer `PROSPECT → ONBOARDING`, and hands off to the **existing** `Proposal → Project` orchestration via a single thin event listener. The only genuinely new code is two entities, a guarded `DealTransitionService`, the win-loop glue, a pipeline-summary aggregation, and the board/list/detail/settings frontend. `Deal` becomes field-able / taggable / saved-view-able / audited by **registering with existing registries**, not re-implementing them.

This phase ships as **8 epics (573–580)**, expanded to **12 numbered slices** to honour the 6–10 file / ~800 LOC slice-sizing budget. The 8 architecture capability slices (§11.10) form the epic spine; the foundation slice and the two frontend slices are split where they exceed the budget. No slice mixes backend + frontend scope.

**Migration high-water at phase start**: tenant **V129** (`V129__create_mcp_egress_consents.sql`). Phase 80 ships **two** tenant migrations: **V130** (CRM tables + `proposals.deal_id`) and **V131** (field-group applicable-entity widening for `DEAL`).

---

## Open Questions

- **Slice 1 (foundation) sizing.** Architecture Slice 1 combines V130 + V131 migrations, two entities + three enums, `DealCounter`/`DealNumberService`, two repositories, `Capability` constants, the `DealPipelinePackSeeder` + 4 stage-pack JSONs + 4 vertical-profile edits + `PackStatusSettings` field. That is ~18 files and two distinct concerns (persistence model vs. pack-seeding). **Resolution**: split into **573A** (migrations + entities + enums + repos + counter + `Capability` constants ≈ 11 files) and **573B** (pack-seeder + stage-pack JSONs + vertical-profile wiring + `PackStatusSettings` + seeder test ≈ 10 files).
- **Slice 7 (frontend board + settings) sizing.** Architecture Slice 7 combines the `/pipeline` board + list + intake dialog + filters + `settings/pipeline` stage-config + `lib/api/crm.ts`. That exceeds the 6–10 file budget. **Resolution**: split into **579A** (`lib/api/crm.ts` + board + list + intake dialog + filters ≈ 9 files) and **579B** (`settings/pipeline` stage-config page + reorder/edit/archive components ≈ 6 files). `lib/api/crm.ts` lands in 579A so both frontend epics consume it.
- **Slice 8 (frontend detail + tabs + widget) sizing.** Architecture Slice 8 combines deal detail + customer Deals tab + dashboard widget + E2E capstone. **Resolution**: split into **580A** (deal detail page + proposals chips + activity tab + customer `CustomerGroupedTabs` Deals tab ≈ 8 files) and **580B** (dashboard `PipelineSummaryWidget` + dashboard composition + `dashboard-types.ts` + E2E capstone + vertical-pack QA ≈ 6 files).
- **`DEAL` DB CHECK constraint.** §11.7 notes V131 may collapse to a column-existence guard if no DB-level CHECK on `field_groups.applicable_entity` exists in the tenant baseline. **Resolution**: verify the tenant baseline DDL at implementation time of 577A; if no named CHECK exists, V131 is the idempotent `ADD COLUMN IF NOT EXISTS` guard only. Numbering reservation (V131) stands regardless.
- **`source` as string, not enum.** `Deal.source` is a free-form `VARCHAR(40)` (forks extend without migration). No enum is introduced.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 573 | Migration + Entities + Capability + Stage Seeding | Backend | — | L | 573A, 573B | **Done** (PR #1487) |
| 574 | Deal CRUD + Intake + Filtered List | Backend | 573A | L | 574A | **Done** (PR #1488) |
| 575 | DealTransitionService + Customer Nudge + Events/Audit/Activity | Backend | 574A | L | 575A | **Done** (PR #1489) |
| 576 | Deal↔Proposal Link + Win-Loop Event Glue | Backend | 575A | M | 576A | **Done** (PR #1491) |
| 577 | Field / Tag / Saved-View / Audit-Metadata Registration | Backend | 574A, 575A | M | 577A | **Done** (PR #1495) |
| 578 | Pipeline Summary Aggregation (backend) | Backend | 574A | M | 578A | |
| 579 | Frontend — Board + List + Intake + Stage Settings | Frontend | 574A, 575A, 578A | L | 579A, 579B | |
| 580 | Frontend — Deal Detail + Customer Tab + Dashboard Widget + QA Capstone | Frontend | 576A, 578A, 579A | L | 580A, 580B | |

**Slice count: 12** (8 architecture capability slices expanded to 12 numbered slices for the 6–10 file / ~800 LOC budget). Backend/frontend split preserved per slice — no slice mixes both scopes.

---

## Dependency Graph

```
PHASES already complete (reused, not rebuilt):
  Phase 4  (Customer — Customer entity, LifecycleStatus PROSPECT→ONBOARDING→…, CustomerService.createProspect)
  Phase 11/23 (Custom fields / field packs / tags / saved views — EntityType, FieldPackSeeder, EntityTagService, SavedViewService)
  Phase 6/69 (Audit — AuditService, AuditEventBuilder, AuditEventTypeRegistry, AuditEventGroup, ADR-261)
  Phase 6.5  (Activity feed — ActivityMessageFormatter; Comments; Notifications)
  Phase 65/66 (Pack-seeding — AbstractPackSeeder, vertical-profiles/*.json, PackStatusSettings)
  Phase 77   (Grouped-tabs customer detail — CustomerGroupedTabs)
  Proposals  (proposal/ — Proposal, ProposalOrchestrationService.acceptProposal, ProposalAcceptedEvent, ProposalCounter/NumberService)
  Dashboard  (dashboard/ — DashboardController/Service, TeamUtilizationWidget pattern)
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 1 — Database Foundation (sequential)                │
        │   [573A  V130 + V131 migrations; PipelineStage + Deal     │
        │          entities; StageType/DealStatus enums;            │
        │          DealCounter + DealNumberService; DealRepository   │
        │          + PipelineStageRepository; Capability constants] │
        │                       │                                   │
        │                       ▼                                   │
        │   [573B  DealPipelinePackSeeder + deal-pipeline-packs/    │
        │          *.json (legal/consulting/accounting/default) +   │
        │          vertical-profiles/*.json wiring +                │
        │          OrgSettings.PackStatusSettings.deal_pipeline]    │
        └──────────────────────────────────────────────────────────┘
                                 │
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 2 — Core CRUD (sequential after 573A)               │
        │   [574A  DealService + DealIntakeService +                │
        │          DealRepository.findFiltered + DealController +    │
        │          dto/* + CRUD/intake/isolation/capability tests]  │
        └──────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┬────────────────┐
                ▼                ▼                ▼                ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 3 — Transition + Registration + Summary (parallel)  │
        │   [575A  DealTransitionService + crm/event/* +            │
        │          customer nudge + audit/activity + notification]  │
        │   [577A  EntityType.DEAL + V131 + custom-field round-trip │
        │          + tag/saved-view confirm + audit group]          │  (577A also needs 575A audit events)
        │   [578A  PipelineSummaryService + summary endpoint + DTO] │
        └──────────────────────────────────────────────────────────┘
                                 │  (576A after 575A)
                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 4 — Win Loop (after 575A)                           │
        │   [576A  Proposal.dealId + DealProposalAcceptedListener + │
        │          /api/deals/{id}/proposals create/link/list +     │
        │          win-loop idempotency test]                       │
        └──────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┴────────────────┐
                ▼                                  ▼
        ┌──────────────────────────────────────────────────────────┐
        │ Stage 5 — Frontend (after 574A/575A/578A; 576A for 580)   │
        │   [579A  lib/api/crm.ts + /pipeline board + list +        │
        │          intake dialog + filters]                         │
        │                       │                                   │
        │                       ▼                                   │
        │   [579B  settings/pipeline stage-config page]            │
        │                       │                                   │
        │                       ▼                                   │
        │   [580A  deal detail page + proposals chips + activity +  │
        │          CustomerGroupedTabs Deals tab]                   │
        │                       │                                   │
        │                       ▼                                   │
        │   [580B  PipelineSummaryWidget + dashboard compose +      │
        │          dashboard-types + E2E capstone + vertical QA]    │
        └──────────────────────────────────────────────────────────┘
```

**Parallel opportunities:**
- After **574A** lands, **575A**, **577A**, and **578A** can all proceed; **577A** additionally needs **575A**'s audit-event emission to fully test the activity feed, so sequence **575A → 577A** if running serially, or have 577A's activity assertions land after 575A.
- **576A** runs after **575A** (it reuses `markWon` and `firstWonStage()`).
- Frontend slices **579A → 579B → 580A → 580B** are sequential (each builds on `lib/api/crm.ts` from 579A and prior UI).

---

## Implementation Order

### Stage 1 — Database Foundation (sequential)

| Order | Slice | Summary |
|-------|-------|---------|
| 1a | **573A** ✅ Done (PR #1487) | V130 (`pipeline_stages` + `deals` + `proposals.deal_id` + indexes) and V131 (field-group widening guard); `PipelineStage` + `Deal` rich-domain entities; `StageType` + `DealStatus` enums; `DealCounter` + `DealCounterRepository` + `DealNumberService`; `DealRepository` + `PipelineStageRepository` (with `findOneById`, `findByCustomerId`, `findByLinkedProposalId` stub); `Capability` constants `VIEW_DEALS`/`MANAGE_DEALS`/`CLOSE_DEALS`/`MANAGE_PIPELINE` (default-on). |
| 1b | **573B** ✅ Done (PR #1487) | `DealPipelinePackSeeder extends AbstractPackSeeder`; `DealPipelinePackDefinition`; `resources/deal-pipeline-packs/{legal-za,consulting-za,accounting-za,default}.json`; `vertical-profiles/*.json` `packs.deal_pipeline` wiring; `OrgSettings.PackStatusSettings.deal_pipeline` idempotency field; `PipelineStageService` (config + invariants + `DeleteGuard`); seeder + invariants tests. |

### Stage 2 — Core CRUD (sequential after 573A)

| Order | Slice | Summary |
|-------|-------|---------|
| 2a | **574A** ✅ Done (PR #1488) | `DealService` (CRUD + filtered list); `DealIntakeService` (atomic customer+deal, reuse `CustomerService.createProspect`); `DealRepository.findFiltered` JPQL; `DealController`; `dto/{IntakeRequest,DealResponse,DealUpdateRequest,StageDto}`; CRUD/intake integration test, capability-gating test, **tenant-isolation test (mandatory)**. |

### Stage 3 — Transition + Registration + Summary (parallel after 574A)

| Order | Slice | Summary | Runs in parallel with |
|-------|-------|---------|-----------------------|
| 3a | **575A** ✅ Done (PR #1489) | `DealTransitionService` (move/win/lose/re-open + customer PROSPECT→ONBOARDING nudge); `crm/event/{DealStageChangedEvent,DealWonEvent,DealLostEvent,DealWonEventHandler}`; `dto/TransitionRequest`; audit-metadata registration; `ActivityMessageFormatter` cases; deal-won notification; `DealTransitionServiceTest`. | 578A |
| 3b | **577A** ✅ Done (PR #1495) | `EntityType.DEAL`; V131 verification + apply; confirm `EntityTag`/`SavedView` free-form `"DEAL"`; `AuditEventGroup` `CRM`/`SALES` constant; custom-field round-trip + saved-view/tag filter tests. | 578A |
| 3c | **578A** | `PipelineSummaryService` (weighted value, win rate 90-day window, per-stage breakdown, avg deal size / days-to-close); `dashboard/dto/PipelineSummaryResponse`; `GET /api/dashboard/pipeline-summary`; `PipelineSummaryServiceTest`. | 575A, 577A |

### Stage 4 — Win Loop (after 575A)

| Order | Slice | Summary |
|-------|-------|---------|
| 4a | **576A** ✅ | `proposal/Proposal.java` `deal_id` mapped column + getter/`setDealId`; `DealRepository.findByLinkedProposalId`; `DealProposalAcceptedListener` (`@TransactionalEventListener(AFTER_COMMIT)`, `runForTenantOnShard`, idempotent); `DealProposalService` create/link/list; `/api/deals/{id}/proposals` endpoints on `DealController`; `DealProposalWinLoopTest`. **Done** (PR #1491) |

### Stage 5 — Frontend (sequential after 574A/575A/578A; 576A for 580)

| Order | Slice | Summary |
|-------|-------|---------|
| 5a | **579A** | `frontend/lib/api/crm.ts`; `/pipeline/page.tsx` Kanban board (drag→transition) + list view toggle; intake dialog; filters (saved-views/tags); header weighted value + win rate. |
| 5b | **579B** | `settings/pipeline/page.tsx` stage config: reorder, edit name/probability/type, archive, with guards (mirror `settings/capacity`). |
| 5c | **580A** | `pipeline/[id]/page.tsx` deal detail (overview, stage history, linked customer, linked proposals chips + create/send, comments, `<AuditTimeline>`); `CustomerGroupedTabs` Deals tab + inline new-deal. |
| 5d | **580B** | `components/dashboard/pipeline-summary-widget.tsx`; compose into `dashboard/page.tsx`; `lib/dashboard-types.ts` `PipelineSummaryResponse`; E2E capstone (enquiry→stages→proposal→win→customer advanced); vertical-pack QA. |

### Timeline

```
Stage 1: [573A] -> [573B]                          <- foundations
Stage 2: [574A]                                     <- core CRUD
Stage 3: [575A] // [577A] // [578A]                 <- 3-way parallel (577A activity-tests after 575A)
Stage 4: [576A]                                     <- win loop after 575A
Stage 5: [579A] -> [579B] -> [580A] -> [580B]       <- sequential frontend
```

---

## Epic 573: Migration + Entities + Capability + Stage Seeding

**Goal**: Lay the database foundation and domain model for the entire CRM phase. V130 creates the two new tenant-scoped tables (`pipeline_stages`, `deals`) plus the `proposals.deal_id` link column and all indexes; V131 reserves the field-group widening for `DEAL`. The `PipelineStage` and `Deal` rich-domain entities, their enums, the human-friendly deal-number counter, and the two repositories provide the persistence layer. The `Capability` enum gains four default-on CRM constants. The pack-seeder seeds vertical-appropriate default stages idempotently at provisioning.

**References**: Architecture §11.2 (Domain Model), §11.6e/§11.6f (Capability + pack-seeding), §11.7 (Migrations V130/V131), §11.8 (backend change table, annotated `Deal` entity, repository JPQL), §11.10 Slice 1; [ADR-313](../adr/ADR-313-crm-lead-model.md), [ADR-314](../adr/ADR-314-pipeline-stage-model.md), [ADR-317](../adr/ADR-317-crm-capability-gating.md).

**Dependencies**: Phase 4 (`Customer`); Proposals (`ProposalCounter`/`ProposalNumberService` as counter pattern); Phase 65/66 (`AbstractPackSeeder`, `vertical-profiles/*.json`, `OrgSettings.PackStatusSettings`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **573A** ✅ Done (PR #1487) | 573A.1–573A.9 | ~11 backend files (2 migrations + 2 entities + 2 enums + 1 counter + 1 counter-repo + 1 number-service + 2 repos + 1 `Capability` mod) | V130 + V131 migrations; `PipelineStage`/`Deal` entities; `StageType`/`DealStatus` enums; `DealCounter` + repo + `DealNumberService`; `DealRepository` + `PipelineStageRepository`; `Capability` constants. |
| **573B** ✅ Done (PR #1487) | 573B.1–573B.6 | ~10 backend files (1 seeder + 1 pack-def + 4 JSON packs + N profile edits + 1 `PackStatusSettings` mod + 1 stage service + tests) | `DealPipelinePackSeeder` + `DealPipelinePackDefinition`; 4 stage-pack JSONs; `vertical-profiles/*.json` wiring; `PackStatusSettings.deal_pipeline`; `PipelineStageService`; seeder + invariants tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 573A.1 | Create V130 migration | `backend/src/main/resources/db/migration/tenant/V130__create_crm_pipeline_tables.sql` | verified by 573B.6 + 574A tests (runs clean) | `db/migration/tenant/V129__create_mcp_egress_consents.sql` for format | SQL verbatim from §11.7: `pipeline_stages` (incl. nullable `pipeline_id`, `default_probability_pct` CHECK 0–100, `stage_type` CHECK IN OPEN/WON/LOST, `archived`); `deals` (`deal_number` UNIQUE, `customer_id` NOT NULL, `status` CHECK, `value_amount` NUMERIC(19,2), nullable `probability_pct` CHECK 0–100, `custom_fields`/`applied_field_groups` jsonb, nullable `pipeline_id`, FK `fk_deal_stage` → `pipeline_stages`); `ALTER TABLE proposals ADD COLUMN IF NOT EXISTS deal_id UUID`. Indexes: `idx_deals_stage`, `idx_deals_customer`, `idx_deals_owner`, `idx_deals_status`, partial `idx_deals_open_by_stage WHERE status='OPEN'`, partial `idx_deals_won_at`/`idx_deals_lost_at`, partial `idx_proposals_deal WHERE deal_id IS NOT NULL`. All `CREATE TABLE/INDEX IF NOT EXISTS`. `customer_id`/`owner_id` raw UUID (no FK), matching prevailing convention. |
| 573A.2 | Create V131 migration | `backend/src/main/resources/db/migration/tenant/V131__add_deal_entity_type_constraint.sql` | verified by 577A | `db/migration/tenant/V126__*` idempotent ALTER pattern | `ALTER TABLE field_groups ADD COLUMN IF NOT EXISTS applicable_entity_values jsonb`. If (and only if) a named CHECK on `field_groups.applicable_entity` exists in the tenant baseline, drop-and-recreate it to include `'DEAL'` (commented template in §11.7). Verify baseline DDL before applying; otherwise V131 is the column-existence guard only. |
| 573A.3 | Create `StageType` + `DealStatus` enums | `crm/StageType.java`, `crm/DealStatus.java` | covered by 573A.9 | `proposal/ProposalStatus.java` | `StageType`: `OPEN, WON, LOST`. `DealStatus`: `OPEN, WON, LOST` with `public static final Set<DealStatus> TERMINAL_STATUSES = Set.of(WON, LOST)`. |
| 573A.4 | Create `PipelineStage` entity | `crm/PipelineStage.java` | 573B.6 | `proposal/Proposal.java` entity pattern; §11.2.1 field table | `@Table(name = "pipeline_stages")`. Fields per §11.2.1: `name`, `position`, `defaultProbabilityPct`, `stageType` (`@Enumerated(EnumType.STRING)`, len 10), `archived`, nullable `pipelineId`, `createdBy`, timestamps. `@PrePersist`/`@PreUpdate`. No `tenant_id`, no `@Filter`. |
| 573A.5 | Create `Deal` entity | `crm/Deal.java` | 573B.6, 574A, 575A | §11.8 annotated `Deal` entity (verbatim) | Rich-domain per §11.8 code block: raw-UUID refs (`customerId`/`stageId`/`ownerId`, NOT `@ManyToOne`); `status` default OPEN; jsonb `customFields`/`appliedFieldGroups`; guarded methods `markWon`/`markLost`/`reopen`/`moveToOpenStage` + guarded setters + `effectiveProbabilityPct(int)` + `weightedValue(int)` + private `requireStatus`. Throw `InvalidStateException`. `pipelineId` reserved (always null v1). |
| 573A.6 | Create `DealCounter` + repository | `crm/DealCounter.java`, `crm/DealCounterRepository.java` | 574A | `proposal/ProposalCounter.java`, `ProposalCounterRepository.java` | Per-tenant monotonic counter backing `deal_number`. Mirror `ProposalCounter` exactly (same locking/increment shape). |
| 573A.7 | Create `DealNumberService` | `crm/DealNumberService.java` | 574A | `proposal/ProposalNumberService.java` | `nextDealNumber()` → `DEAL-0001` format. Mirror `ProposalNumberService`. |
| 573A.8 | Create `DealRepository` + `PipelineStageRepository` | `crm/DealRepository.java`, `crm/PipelineStageRepository.java` | 573B.6, 574A | §11.8 repository JPQL block; `proposal/ProposalRepository.java` | `DealRepository`: `findOneById` (throws `ResourceNotFoundException`), `findByCustomerId`, `findByLinkedProposalId` (correlated subquery via `Proposal.dealId` — body lands in 576A but signature stubbed here, or defer to 576A). `findFiltered` Page query lands in 574A. `PipelineStageRepository`: `findOneById`, `findAllByOrderByPositionAsc`, `findFirstByStageTypeAndArchivedFalseOrderByPositionAsc`. |
| 573A.9 | Add `Capability` CRM constants | `orgrole/Capability.java` | covered by 574A capability-gating test | existing `Capability.java` values + default role-grant sets | Add `VIEW_DEALS`, `MANAGE_DEALS`, `CLOSE_DEALS`, `MANAGE_PIPELINE`. **Default-on**: grant `VIEW_DEALS`/`MANAGE_DEALS`/`CLOSE_DEALS` to the standard roles (Owner/Admin/Project Lead/Contributor per §11.9), `MANAGE_PIPELINE` to Owner/Admin only. Follow the `CUSTOMER_MANAGEMENT`/`PROJECT_MANAGEMENT` grant pattern. Do NOT register a `VerticalModuleGuard` module (ADR-317). |
| 573B.1 | Create `PipelineStageService` | `crm/PipelineStageService.java` | 573B.6 | `tag/`-style delete-guard usage; `DeleteGuard` | Stage CRUD + reorder + archive. Invariants (§11.2.1): always ≥1 OPEN, ≥1 WON, ≥1 LOST (block removing the last of each type); delete-with-deals blocked via existing `DeleteGuard` (archive instead). `firstOpenStage()`/`firstWonStage()`/`firstLostStage()` helpers used by intake/transition. |
| 573B.2 | Create `DealPipelinePackDefinition` + `DealPipelinePackSeeder` | `seeder/DealPipelinePackDefinition.java`, `seeder/DealPipelinePackSeeder.java` | 573B.6 | `seeder/RatePackSeeder.java` + `RatePackDefinition.java`; `seeder/AbstractPackSeeder.java` | Seeder `extends AbstractPackSeeder`, reads `resources/deal-pipeline-packs/*.json`. Idempotent (skip if `PackStatusSettings.deal_pipeline` already seeded). Definition record models ordered stages with name/position/defaultProbabilityPct/stageType. Invoke at provisioning + `verticals/VerticalProfileReconciliationSeeder`. |
| 573B.3 | Create stage-pack JSONs | `resources/deal-pipeline-packs/{legal-za,consulting-za,accounting-za,default}.json` | 573B.6 | existing pack JSONs under `resources/` (rate/schedule packs) | Stage sets per §11.6f: legal-za (Enquiry→Conflict check→Engagement→Won/Lost), consulting-za (Lead→Qualified→Proposal sent→Negotiation→Won/Lost), accounting-za (Enquiry→Scoping→Engagement letter→Won/Lost), default (Lead→Qualified→Proposal→Won/Lost). Each stage: `name`, `position`, `defaultProbabilityPct`, `stageType`. Won=100, Lost=0. |
| 573B.4 | Wire `deal_pipeline` into vertical profiles | `resources/vertical-profiles/{legal-za,consulting-za,accounting-za,default}.json` (and any others present) | 573B.6 | existing `packs` map entries in each profile | Add a `deal_pipeline` key to each profile's `packs` map pointing at the matching stage-pack. List the actual profile files at implementation time; edit all. |
| 573B.5 | Extend `PackStatusSettings` | `settings/OrgSettings.java` (`PackStatusSettings` embeddable) | 573B.6 | existing `PackStatusSettings` fields (rate/schedule/field-pack tracking) | Add a `deal_pipeline` tracking field for idempotency (re-provision safe). |
| 573B.6 | Seeder + invariants tests | `backend/src/test/java/.../crm/DealPipelinePackSeederTest.java`, `crm/PipelineStageInvariantsTest.java` | ~9 tests: (1) V130 runs clean; (2) `PipelineStage`/`Deal` persist + `findOneById` round-trip; (3) default-profile stages seeded; (4) legal-za stages seeded with correct stageTypes; (5) re-provision idempotent (no duplicate stages); (6) cannot remove last OPEN stage; (7) cannot remove last WON; (8) cannot remove last LOST; (9) delete stage with deals blocked → archive | `tasks/phase71` repo-test setup (`@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`); existing pack-seeder tests | Provision tenant, bind `RequestScopes.TENANT_ID`, assert seed + invariant behaviour. |

### Key Files

**Create (backend):**
- `backend/src/main/resources/db/migration/tenant/V130__create_crm_pipeline_tables.sql`
- `backend/src/main/resources/db/migration/tenant/V131__add_deal_entity_type_constraint.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/PipelineStage.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/Deal.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/StageType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealCounter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealCounterRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealNumberService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/PipelineStageRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/PipelineStageService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/DealPipelinePackSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/DealPipelinePackDefinition.java`
- `backend/src/main/resources/deal-pipeline-packs/{legal-za,consulting-za,accounting-za,default}.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealPipelinePackSeederTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/PipelineStageInvariantsTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` — add 4 default-on CRM constants
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — `PackStatusSettings.deal_pipeline`
- `backend/src/main/resources/vertical-profiles/*.json` — `packs.deal_pipeline` wiring

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java` — tenant-aware entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalCounter.java`, `ProposalNumberService.java` — counter pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java`, `RatePackSeeder.java` — seeder pattern
- `backend/src/main/resources/db/migration/tenant/V129__create_mcp_egress_consents.sql` — migration format

### Architecture Decisions

- **Deal-linked-to-Customer, no separate Lead** ([ADR-313](../adr/ADR-313-crm-lead-model.md)) — `deals.customer_id` is NOT NULL; intake (574A) creates customer+deal atomically.
- **Data-driven `stageType`, single multi-pipeline-ready pipeline** ([ADR-314](../adr/ADR-314-pipeline-stage-model.md)) — terminal behaviour is a property of `stage_type`, not a name match; `pipeline_id` reserved nullable for a future parent (always NULL in v1).
- **CRM is a default-on capability, not a vertical module** ([ADR-317](../adr/ADR-317-crm-capability-gating.md)) — four `Capability` constants granted in default role sets; no `VerticalModuleGuard`. Vertical stage defaults via pack-install, not hand-written SQL.

### Non-scope
- No CRUD service / controller (574A). No transition logic (575A). No proposal link (576A). No summary (578A). No frontend.

---

## Epic 574: Deal CRUD + Intake + Filtered List

**Goal**: Build the read/write surface for deals: standard CRUD, the atomic intake operation that either attaches to an existing customer or creates a `PROSPECT` customer + deal in one transaction (reusing `CustomerService`), and the filtered/paginated list query backing both the board and the list view. This is the slice that proves tenant isolation and capability gating.

**References**: Architecture §11.3a (intake), §11.3d (filtered list JPQL), §11.4 (Deals CRUD/intake API + JSON shapes), §11.8 (DTOs, testing strategy), §11.10 Slice 2; [ADR-313](../adr/ADR-313-crm-lead-model.md).

**Dependencies**: 573A (entities, repos, counter, `Capability`).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **574A** ✅ Done (PR #1488) | 574A.1–574A.7 | ~9 backend files (1 service + 1 intake service + 1 repo mod + 1 controller + 4 DTOs + 3 test files) | `DealService`, `DealIntakeService`, `DealRepository.findFiltered`, `DealController`, DTOs; CRUD/intake + capability + tenant-isolation tests. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 574A.1 | Add `findFiltered` to `DealRepository` | `crm/DealRepository.java` (modify) | 574A.6 | §11.3d JPQL block | `Page<Deal> findFiltered(stageId, ownerId, customerId, status, source, fromDate, toDate, Pageable)` — all params nullable-guarded `(:x IS NULL OR …)`, `ORDER BY d.updatedAt DESC`. Tag/saved-view predicate composition lands here as optional appended filters (resolve tag → `EntityTag` join in service). |
| 574A.2 | Create deal DTOs | `crm/dto/IntakeRequest.java`, `crm/dto/DealResponse.java`, `crm/dto/DealUpdateRequest.java`, `crm/dto/StageDto.java` | 574A.5/.6 | §11.4 JSON shapes; existing `proposal/dto/*` | `IntakeRequest` (nullable `customerId`, nested `customer`, `title`, nullable `stageId`, `valueAmount`, nullable `ownerId`, `source`, `expectedCloseDate`) — **clean, not UI-coupled** (the `intake-triage` AI seam). `DealResponse` includes `effectiveProbabilityPct` + `weightedValue` + `stageName` (resolved in service). `DealUpdateRequest` covers value/owner/expectedClose/probabilityOverride/source/title/customFields. |
| 574A.3 | Create `DealService` | `crm/DealService.java` | 574A.6 | `proposal/ProposalService.java`; `customer/CustomerService.java` | `@Transactional` CRUD: create against existing customer, read, update (guarded setters — editable in any status), guarded delete (block if linked proposals exist — coordinate with 576A; for now block none, finalize guard in 576A), filtered list (resolves stage names + effective probability for each row). Audit `deal.created` via `AuditEventBuilder`. `@RequiresCapability` per §11.9. |
| 574A.4 | Create `DealIntakeService` | `crm/DealIntakeService.java` | 574A.6 | §11.3a code block; `customer/CustomerService.createProspect` | `@Transactional intake(IntakeRequest)`: if `customerId` present → `customerService.requireExisting`; else `customerService.createProspect(...)`. Resolve stage (`stageId` or `stageService.firstOpenStage()`). `Deal.create(...)` with currency defaulted from `OrgSettings`. Audit `deal.created`. **Reuse `CustomerService`, do not duplicate validation.** |
| 574A.5 | Create `DealController` | `crm/DealController.java` | 574A.5/.6 | `proposal/ProposalController.java` (thin, one service call each) | Endpoints (§11.4): `POST /api/deals/intake` (`MANAGE_DEALS`, 201 + Location), `GET /api/deals` (`VIEW_DEALS`, paginated `VIA_DTO`), `GET /api/deals/{id}` (`VIEW_DEALS`), `POST /api/deals` (`MANAGE_DEALS`), `PUT /api/deals/{id}` (`MANAGE_DEALS`), `DELETE /api/deals/{id}` (`MANAGE_DEALS`, guarded). Transition + proposals endpoints land in 575A/576A. |
| 574A.6 | CRUD + intake integration tests | `backend/src/test/java/.../crm/DealCrudIntegrationTest.java` | ~8 tests: create against existing customer (201); intake inline PROSPECT (asserts customer created with `lifecycleStatus=PROSPECT` + deal OPEN in first OPEN stage); get (200 + JSON shape incl. `effectiveProbabilityPct`/`weightedValue`); update value/owner; filtered list by stage/owner/status; delete; 404 on missing | `DealCrudIntegrationTest` row in §11.8; phase71 MockMvc setup | MockMvc; assert 201/200/404 + JSON. |
| 574A.7 | Capability-gating + tenant-isolation tests | `backend/src/test/java/.../crm/DealCapabilityGatingTest.java`, `crm/DealTenantIsolationTest.java` | ~6 tests: member without `VIEW_DEALS` → 403 on GET; without `MANAGE_DEALS` → 403 on intake/create/update/delete; **tenant B cannot read tenant A's deal (cross-tenant `findById` returns not-found under B's schema); tenant B `GET /api/deals` excludes A's deals** | §11.8 `DealCapabilityGatingTest` + `DealTenantIsolationTest` (mandatory) | Tenant-isolation test is **mandatory** per requirements. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealIntakeService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/IntakeRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/DealResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/DealUpdateRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/StageDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealCrudIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealCapabilityGatingTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealTenantIsolationTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealRepository.java` — add `findFiltered`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — `createProspect`/`requireExisting`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java` — thin-controller pattern

### Architecture Decisions

- **Atomic intake reuses `CustomerService`** ([ADR-313](../adr/ADR-313-crm-lead-model.md)) — no duplicated customer validation; `customer_id` always non-null.
- **`IntakeRequest` is UI-decoupled** — the legal `intake-triage` specialist can call the same path programmatically (integration seam; wiring out of scope).

### Non-scope
- No win/lose transition (575A). No proposal link / delete-guard-on-proposals (576A). No custom-field registration (577A). No summary (578A). No frontend.

---

## Epic 575: DealTransitionService + Customer Nudge + Events/Audit/Activity

**Goal**: Build the guarded lifecycle path — the only code that writes `Deal.status`/`wonAt`/`lostAt`/`lostReason`. Moving within OPEN stages, winning (with the `Customer` PROSPECT→ONBOARDING nudge), losing (reason required), and re-opening, each emitting a domain event, an audit entry, an activity-feed message, and (for win) a notification.

**References**: Architecture §11.3b (transition service code), §11.5.3 (lose-without-reason guard), §11.6c/d (audit metadata + activity formatter), §11.4 (transition API), §11.8 (testing), §11.10 Slice 3; [ADR-314](../adr/ADR-314-pipeline-stage-model.md), [ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md).

**Dependencies**: 574A (controller, DTOs, service); 573A (`Deal` rich-domain methods).

**Scope**: Backend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **575A** ✅ Done (PR #1489) | 575A.1–575A.8 | ~9 backend files (1 service + 1 DTO + 4 event/handler files + 2 registry mods + 1 controller mod + 1 test) | `DealTransitionService`; `crm/event/*`; `TransitionRequest`; audit-metadata + activity-formatter registration; deal-won notification; `DealTransitionServiceTest`. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 575A.1 | Create `crm/event/*` events | `crm/event/DealStageChangedEvent.java`, `DealWonEvent.java`, `DealLostEvent.java` | 575A.8 | `proposal/ProposalAcceptedEvent.java` (record + tenant/org fields) | `DealWonEvent(UUID dealId, UUID customerId, UUID ownerId, String tenantId, String orgId)`. `DealStageChangedEvent(dealId, stageId, tenantId, orgId)`. `DealLostEvent(dealId, lostReason, tenantId, orgId)`. |
| 575A.2 | Create `DealWonEventHandler` | `crm/event/DealWonEventHandler.java` | 575A.8 | `proposal/ProposalAcceptedEventHandler.java` (`@TransactionalEventListener(AFTER_COMMIT)`, `runForTenant`) | AFTER_COMMIT handler; `RequestScopes.runForTenant`; dispatches `NotificationType.DEAL_WON` to owner. |
| 575A.3 | Create `TransitionRequest` DTO | `crm/dto/TransitionRequest.java` | 575A.8 | §11.4 transition JSON | `targetStageId` (required), nullable `probabilityOverride`, nullable `lostReason`. |
| 575A.4 | Create `DealTransitionService` | `crm/DealTransitionService.java` | 575A.8 | §11.3b code block (verbatim); `task/TaskLifecycleService.java` guard pattern | `@Transactional transition(dealId, req)`: switch on target `stageType`. OPEN → dispatch by current status (`reopen` if terminal else `moveToOpenStage`). WON → `markWon` + `customerNudge` (PROSPECT→ONBOARDING only, never downgrade) + `DealWonEvent` + audit `deal.won` + notify. LOST → require `lostReason` (else `InvalidStateException` → 400) + `markLost` + `DealLostEvent` + audit. Re-open → audit `deal.reopened`. `@RequiresCapability`: `MANAGE_DEALS` for OPEN moves, `CLOSE_DEALS` for win/lose/reopen (per §11.9 — controller-level split or in-service check). |
| 575A.5 | Add transition endpoint to `DealController` | `crm/DealController.java` (modify) | 575A.8 | §11.4; existing controller | `POST /api/deals/{id}/transition` → `DealTransitionService.transition`. Capability split per §11.9. |
| 575A.6 | Register audit-event metadata | `audit/AuditEventTypeRegistry.java`, `audit/AuditEventGroup.java` | 575A.8 | §11.6c table; ADR-261 longest-dotted-prefix | Add `AuditEventTypeMetadata` rows for `deal.created`, `deal.stage_changed`, `deal.won`, `deal.lost` (INFO), `deal.reopened` (NOTICE), group `CRM`/`SALES`. Add a `SALES`/`CRM` constant to `AuditEventGroup` if none fits. |
| 575A.7 | Add `ActivityMessageFormatter` cases + `NotificationType.DEAL_WON` | `activity/ActivityMessageFormatter.java`, `notification/NotificationType.java` (or equivalent) | 575A.8 | §11.6d; existing formatter switch | Add `case "deal.created"/"deal.stage_changed"/"deal.won"/"deal.lost"/"deal.reopened"` producing human-readable text. Add `DEAL_WON` (and optionally `DEAL_ASSIGNED`) to the notification-type enum. |
| 575A.8 | `DealTransitionServiceTest` | `backend/src/test/java/.../crm/DealTransitionServiceTest.java` | ~9 tests: OPEN→OPEN move recomputes effective probability + emits `DealStageChangedEvent`; win sets WON/`wonAt`/prob→100; **win nudges customer PROSPECT→ONBOARDING**; **win does NOT downgrade an ACTIVE customer (no-op)**; double-win rejected (`InvalidStateException`); lose-without-reason → 400; lose sets LOST/`lostAt`/reason; re-open from WON clears `wonAt`/`lostAt`/`lostReason`; audit rows written with `entityType="DEAL"` | §11.8 `DealTransitionServiceTest` row | Assert event publication (e.g. `@RecordApplicationEvents`) + audit + customer state. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealTransitionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/event/DealStageChangedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/event/DealWonEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/event/DealLostEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/event/DealWonEventHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/TransitionRequest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealTransitionServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealController.java` — add transition endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistry.java`, `audit/AuditEventGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationType.java` (verify exact path)

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalAcceptedEventHandler.java` — AFTER_COMMIT + `runForTenant`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskLifecycleService.java` — guard pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — `transitionLifecycleStatus` / `LifecycleStatus`

### Architecture Decisions

- **Guarded transition, never a free `stage` write** ([ADR-314](../adr/ADR-314-pipeline-stage-model.md)) — terminal semantics driven by `stageType`; `status`/`wonAt`/`lostAt`/`lostReason` only via this service.
- **Customer nudge only-if-PROSPECT** ([ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md)) — never downgrade; ACTIVE/DORMANT customers are a deliberate no-op.

### Non-scope
- No proposal-acceptance win loop (576A — reuses `markWon`/`firstWonStage`). No summary. No frontend.

---

## Epic 576: Deal↔Proposal Link + Win-Loop Event Glue

**Goal**: Close the funnel loop by reusing the existing proposal→project orchestration. Map the new `proposals.deal_id` column on `Proposal`, expose create/link/list endpoints, and add the single thin AFTER_COMMIT listener that marks a deal-linked proposal's deal WON on acceptance — idempotent so a manually-won deal isn't double-won.

**References**: Architecture §11.2.3 (link column), §11.3c (win-loop listener code), §11.5.2 (sequence), §11.4 (deal↔proposal API), §11.8 (`findByLinkedProposalId`, testing), §11.10 Slice 4; [ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md).

**Dependencies**: 575A (`markWon`, `firstWonStage`, `DealWonEvent`); proposal domain (`ProposalAcceptedEvent`, `ProposalService`).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **576A** | 576A.1–576A.6 | ~8 backend files (1 `Proposal` mod + 1 repo mod + 1 listener + 1 service + 1 controller mod + 1 DTO + 1 test) | `Proposal.dealId` mapped column; `findByLinkedProposalId`; `DealProposalAcceptedListener`; create/link/list endpoints; win-loop idempotency test. **Done** (PR #1491) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 576A.1 | Add `dealId` to `Proposal` | `proposal/Proposal.java` (modify) | 576A.6 | §11.2.3; existing `Proposal` columns | `@Column(name = "deal_id") private UUID dealId;` + getter + `setDealId` — a **mapped column, NOT a JPA association** (no `@ManyToOne`); ungated result-reference setter. Column already created in V130 (573A.1). No other `Proposal` internals change. |
| 576A.2 | Implement `findByLinkedProposalId` | `crm/DealRepository.java` (modify) | 576A.6 | §11.8 repo JPQL block | Correlated-subquery JPQL: `SELECT d FROM Deal d WHERE d.id = (SELECT p.dealId FROM Proposal p WHERE p.id = :proposalId)`. Valid now that `Proposal.dealId` is mapped. |
| 576A.3 | Create `DealProposalAcceptedListener` | `crm/DealProposalAcceptedListener.java` | 576A.6 | §11.3c code block (verbatim); `proposal/ProposalAcceptedEventHandler.java` | `@TransactionalEventListener(AFTER_COMMIT)` on `ProposalAcceptedEvent`; `RequestScopes.runForTenant(tenantId, orgId, …)`; `findByLinkedProposalId` → if `status != WON` → `markWon(firstWonStage)` + audit `deal.won` (`via=proposal_acceptance`) + publish `DealWonEvent`. **Idempotent** (no double-win). |
| 576A.4 | Create `DealProposalService` | `crm/DealProposalService.java` | 576A.6 | `proposal/ProposalService.java`; §11.4 | `createFromDeal(dealId)` — pre-fill `customerId` + `valueAmount`, set `proposal.dealId` (delegate to `ProposalService` for drafting). `linkExisting(dealId, proposalId)`. `listForDeal(dealId)` (status chips). Finalize `DealService` delete-guard: block delete when linked proposals exist. |
| 576A.5 | Add proposal endpoints to `DealController` | `crm/DealController.java` (modify), `crm/dto/LinkedProposalDto.java` | 576A.6 | §11.4 deal↔proposal table | `GET /api/deals/{id}/proposals` (`VIEW_DEALS`), `POST /api/deals/{id}/proposals` (`MANAGE_DEALS`, create), `POST /api/deals/{id}/proposals/{proposalId}/link` (`MANAGE_DEALS`). `LinkedProposalDto` carries id/number/status/amount. |
| 576A.6 | `DealProposalWinLoopTest` | `backend/src/test/java/.../crm/DealProposalWinLoopTest.java` | ~5 tests: accept a deal-linked proposal → AFTER_COMMIT → deal marked WON; **idempotent — already-WON deal is a no-op (no double-win)**; proposal with no linked deal → no effect; create-from-deal pre-fills customer+value+`dealId`; link existing sets `dealId`; delete-deal-with-linked-proposal blocked | §11.8 `DealProposalWinLoopTest` row | Use the real `ProposalAcceptedEvent` path; assert transactional commit ordering. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealProposalAcceptedListener.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealProposalService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/dto/LinkedProposalDto.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealProposalWinLoopTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java` — add `dealId` mapped column
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealRepository.java` — `findByLinkedProposalId`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealController.java` — proposal endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealService.java` — delete-guard on linked proposals

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalAcceptedEvent.java`, `ProposalOrchestrationService.java`, `ProposalService.java`

### Architecture Decisions

- **Reuse, do not rebuild** ([ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md)) — FK on `proposals` (one-deal-to-many-proposals), one AFTER_COMMIT listener, no parallel project-creation/customer-activation engine. Direct win is status-only; acceptance win is idempotent — precedence prevents double-win.

### Non-scope
- No changes to `Proposal` lifecycle/orchestration internals. No bundled "win→everything" macro. No frontend.

---

## Epic 577: Field / Tag / Saved-View / Audit-Metadata Registration

**Goal**: Make `Deal` a first-class field-able / taggable / saved-view-able / audited entity by registering it with the existing registries — not re-implementing them. Add `EntityType.DEAL`, apply V131, confirm the free-form tag/saved-view discriminators accept `"DEAL"`, and round-trip custom fields on a deal.

**References**: Architecture §11.6a/b/c (registry integration), §11.7 (V131), §11.10 Slice 5; [ADR-316](../adr/ADR-316-deal-as-registered-entity.md).

**Dependencies**: 574A (deal entity + CRUD exists); 575A (audit events emitted — needed to assert the activity feed).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **577A** | 577A.1–577A.5 | ~7 backend files (1 enum mod + 1 optional field-pack JSON + 1 service mod for field-group apply + 2 test files + V131 verify) | `EntityType.DEAL`; V131 application; field-group auto-apply on deal create; tag/saved-view free-form confirm; round-trip tests. **Done** (PR #1495) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 577A.1 | Add `DEAL` to `EntityType` | `fielddefinition/EntityType.java` (modify) | 577A.5 | §11.6a; existing enum (`PROJECT, TASK, CUSTOMER, INVOICE`) | Becomes `PROJECT, TASK, CUSTOMER, INVOICE, DEAL`. Entity-type boundary is enforced in Java; this is the primary widening. |
| 577A.2 | Verify/apply V131 | `db/migration/tenant/V131__add_deal_entity_type_constraint.sql` (verify; created in 573A.2) | 577A.5 | §11.7 note | Confirm the field-group applicable-entity machinery accepts `"DEAL"`. If no DB CHECK exists, V131 stays the column-existence guard. |
| 577A.3 | Wire deal field-group auto-apply | `crm/DealService.java` / `crm/DealIntakeService.java` (modify) | 577A.5 | `customer/CustomerService.java` `applied_field_groups` handling; field-definition service | On deal create/intake, resolve auto-apply field groups for `EntityType.DEAL` into `Deal.appliedFieldGroups`; persist `customFields`. Update accepts custom-field map. Optional: `resources/field-packs/*.json` with `"entityType":"DEAL"` discovered by existing `FieldPackSeeder`. |
| 577A.4 | Confirm tag + saved-view free-form `DEAL` + audit group | (read-only confirm `tag/EntityTagService.java`, `view/SavedViewService.java`); `audit/AuditEventGroup.java` (verify CRM group from 575A.6) | 577A.5 | §11.6b/c | `EntityTag.entityType` + `SavedView.entityType` are free-form Strings — **zero registration**; pass `"DEAL"`. Confirm `findFiltered` (574A) composes tag/saved-view predicates. Ensure the `CRM`/`SALES` audit group constant exists (added in 575A.6). |
| 577A.5 | Registration round-trip tests | `backend/src/test/java/.../crm/DealCustomFieldsTest.java`, `crm/DealTagSavedViewFilterTest.java` | ~6 tests: custom-field write+read round-trip on a deal; auto-apply field group attached at create; tag a deal with `entityType="DEAL"` and filter `findFiltered` by tag; create a `SavedView(entityType="DEAL")` and resolve its filter set; deal audit events resolve to the `CRM`/`SALES` group; activity feed renders `deal.*` messages | §11.8 Slice 5 tests | Reuse existing field/tag/saved-view test harness. |

### Key Files

**Create (backend):**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealCustomFieldsTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/DealTagSavedViewFilterTest.java`
- (optional) `backend/src/main/resources/field-packs/deal-*.json`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java` — add `DEAL`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealService.java` / `DealIntakeService.java` — field-group apply

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTagService.java`, `view/SavedViewService.java` — free-form discriminators
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — `custom_fields`/`applied_field_groups` precedent

### Architecture Decisions

- **Register, don't re-implement** ([ADR-316](../adr/ADR-316-deal-as-registered-entity.md)) — `Deal` plugs into existing field-definition (`EntityType.DEAL` + V131), free-form tags/saved-views, and audit-metadata registries.

### Non-scope
- No new tag/saved-view subsystem. No summary. No frontend.

---

## Epic 578: Pipeline Summary Aggregation (backend)

**Goal**: A purpose-built, single-query summary endpoint returning open weighted pipeline value, per-stage breakdown, win rate over a date window, average deal size, and optional average days-to-close — the v1 reporting surface (full `ReportDefinition` reports deferred).

**References**: Architecture §11.3d (aggregation SQL), §11.4 (summary API + JSON), §11.8 (testing), §11.10 Slice 6; [ADR-318](../adr/ADR-318-pipeline-metrics.md).

**Dependencies**: 574A (deals exist, repository in place).

**Scope**: Backend only

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **578A** | 578A.1–578A.4 | ~6 backend files (1 service + 1 repo mod / native queries + 1 DTO + 1 controller mod + 1 test) | `PipelineSummaryService`; native aggregation queries; `PipelineSummaryResponse`; `GET /api/dashboard/pipeline-summary`; `PipelineSummaryServiceTest`. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 578A.1 | Add aggregation queries to `DealRepository` | `crm/DealRepository.java` (modify) | 578A.4 | §11.3d SQL blocks (per-stage + win-rate) | Native queries: per-stage breakdown (OPEN deals only, `effectiveProb = COALESCE(probability_pct, default_probability_pct)` in SQL, grouped by OPEN stage ordered by position); win-rate window counts (`won`/`lost` by `won_at`/`lost_at >= :windowStart`). Single query each — no N+1. |
| 578A.2 | Create `PipelineSummaryResponse` DTO | `dashboard/dto/PipelineSummaryResponse.java` | 578A.4 | §11.4 summary JSON | `openWeightedValue`, `currency`, `winRate`, `windowFrom`/`windowTo`, `averageDealSize`, `averageDaysToClose`, `stages[]` (stageId/stageName/dealCount/totalValue/weightedValue). |
| 578A.3 | Create `PipelineSummaryService` + endpoint | `crm/PipelineSummaryService.java`, `dashboard/DashboardController.java` (modify), `dashboard/DashboardService.java` (modify) | 578A.4 | `dashboard/` team-utilization summary pattern; §11.3d | `@Transactional(readOnly = true) getSummary(SummaryFilter)`: per-stage breakdown; open weighted value `Σ(value×effProb/100)` over OPEN; win rate `won/(won+lost)` over **trailing 90 days** default (window shared with widget); avg deal size; optional avg days-to-close (`wonAt − createdAt`). Single org currency v1 (defaulted from `OrgSettings`; documented limitation). `GET /api/dashboard/pipeline-summary?from&to&ownerId` — `VIEW_DEALS`, admin/owner-scoped. |
| 578A.4 | `PipelineSummaryServiceTest` | `backend/src/test/java/.../crm/PipelineSummaryServiceTest.java` | ~7 tests: weighted value `Σ(value×effProb)` over OPEN only (WON/LOST excluded); per-stage totals + count; effective probability override vs stage default; WON=100/LOST=0 in math; win rate over window; avg deal size; avg days-to-close | §11.8 `PipelineSummaryServiceTest` row | Seed deals across stages/statuses; assert aggregation math exactly. |

### Key Files

**Create (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/PipelineSummaryService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/dto/PipelineSummaryResponse.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/crm/PipelineSummaryServiceTest.java`

**Modify (backend):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealRepository.java` — aggregation queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java`, `DashboardService.java` — summary endpoint

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/` — existing dashboard summary/widget backend pattern (e.g. team utilization)

### Architecture Decisions

- **Summary endpoint over `ReportDefinition` for v1** ([ADR-318](../adr/ADR-318-pipeline-metrics.md)) — weighted value over OPEN only; win rate window/counting fixed (trailing 90 days, shared with widget); single org currency assumption (no FX) documented.

### Non-scope
- No `ReportDefinition` reports, exports, scheduled delivery, forecasting/quota. No frontend widget (580B).

---

## Epic 579: Frontend — Board + List + Intake + Stage Settings

**Goal**: The primary CRM workspace UI: the `/pipeline` Kanban board (drag-to-move triggers the transition endpoint; dropping into WON/LOST opens the win/lose confirm) with a list-view toggle and saved-view/tag filters, the new-enquiry intake action, and the `settings/pipeline` stage-configuration page. The shared `lib/api/crm.ts` module lands here.

**References**: Architecture §11.5.1/.3 (intake + drag sequences), §11.8 (frontend change table), §11.10 Slice 7; `frontend/CLAUDE.md` (Next.js 16, Shadcn, Keycloak).

**Dependencies**: 574A (CRUD/intake/list API), 575A (transition API), 578A (summary for header). 579B depends on 579A (`lib/api/crm.ts`).

**Scope**: Frontend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **579A** | 579A.1–579A.5 | ~9 frontend files (1 api module + 1 page + ~5 components + 1 test) | `lib/api/crm.ts`; `/pipeline` board + list toggle; intake dialog; filters; header weighted value + win rate. |
| **579B** | 579B.1–579B.3 | ~6 frontend files (1 page + ~4 components + 1 test) | `settings/pipeline` stage config: reorder, edit name/probability/type, archive, with guards. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 579A.1 | Create `lib/api/crm.ts` | `frontend/lib/api/crm.ts` | 579A.5 | `frontend/lib/api/capacity.ts`, `schedules.ts` | Typed functions: deal CRUD, `listDeals(filter)`, `intake`, `transition`, `getStages`, stage config, `pipelineSummary`, proposal link/create. Next.js 16 server-action conventions. |
| 579A.2 | Create `/pipeline` page (board + list toggle) | `frontend/app/(app)/org/[slug]/pipeline/page.tsx`, `components/pipeline/PipelineBoard.tsx`, `components/pipeline/DealCard.tsx`, `components/pipeline/PipelineListView.tsx` | 579A.5 | existing board/list patterns; Shadcn; `params` is a Promise | Kanban columns = ordered OPEN stages (+ collapsed WON/LOST); cards show customer name, title, value, weighted/probability, owner avatar. Drag-to-move → `transition`; drop into WON/LOST opens confirm. Column headers show count + total/weighted. List view = filterable/sortable table reusing saved-views/tags/custom-field filter components. Header shows open weighted value + win rate (from summary). |
| 579A.3 | Create intake dialog | `frontend/components/pipeline/IntakeDialog.tsx` | 579A.5 | existing create dialogs (customer/proposal) | "New enquiry / New deal": pick existing customer or create PROSPECT inline; title/value/stage/owner/source/expected close. Drives `intake`. |
| 579A.4 | Win/lose confirm + filters | `frontend/components/pipeline/WinLoseDialog.tsx`, `components/pipeline/PipelineFilters.tsx` | 579A.5 | §11.5.3 (lose requires reason); existing saved-views/tags filter components | Lose dialog requires a reason (400 → reopen dialog). Filters reuse saved-views/tags components. |
| 579A.5 | Frontend gates + Playwright move-across-stages | `frontend/.../__tests__/pipeline.test.tsx` (+ Playwright spec) | `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; Playwright: create enquiry → card appears in first OPEN column → drag across stages → drop into Lost requires reason | §11.8 Slice 7 tests | "PASS means observed" — browser → backend log → DB. |
| 579B.1 | Create `settings/pipeline` page | `frontend/app/(app)/org/[slug]/settings/pipeline/page.tsx`, `components/settings/StageConfigList.tsx` | 579B.3 | `frontend/app/(app)/org/[slug]/settings/capacity/page.tsx` | Stage config: list ordered stages with archived flag; reorder (drag), edit, archive, delete. Mirror `settings/capacity` shell + settings layout. |
| 579B.2 | Stage edit + guards | `frontend/components/settings/StageEditDialog.tsx`, `components/settings/StageReorder.tsx` | 579B.3 | existing settings dialogs | Edit name / default probability / stage type; archive with guard (can't remove last OPEN/WON/LOST); delete disabled when deals attached (surface backend `DeleteGuard` error). |
| 579B.3 | Frontend gates | `frontend/.../__tests__/settings-pipeline.test.tsx` | `pnpm lint/build/test` + format:check; component test: reorder persists, edit persists, last-of-type guard surfaces | §11.8 Slice 7 tests | — |

### Key Files

**Create (frontend):**
- `frontend/lib/api/crm.ts`
- `frontend/app/(app)/org/[slug]/pipeline/page.tsx`
- `frontend/components/pipeline/{PipelineBoard,DealCard,PipelineListView,IntakeDialog,WinLoseDialog,PipelineFilters}.tsx`
- `frontend/app/(app)/org/[slug]/settings/pipeline/page.tsx`
- `frontend/components/settings/{StageConfigList,StageEditDialog,StageReorder}.tsx`

**Read for context:**
- `frontend/lib/api/capacity.ts`, `schedules.ts` — API module pattern
- `frontend/app/(app)/org/[slug]/settings/capacity/page.tsx` — settings-page pattern
- `frontend/CLAUDE.md` — Next.js 16 (`params` Promises), Shadcn, Keycloak conventions

### Architecture Decisions

- Drag-to-move → transition endpoint; WON/LOST drop opens guarded confirm (lose requires reason, §11.5.3). Stage config enforces the same invariants as the backend (mirror, not duplicate logic).

### Non-scope
- No deal detail page (580A). No dashboard widget (580B). No E2E win capstone (580B).

---

## Epic 580: Frontend — Deal Detail + Customer Tab + Dashboard Widget + QA Capstone

**Goal**: Complete the UI surface: the deal detail page (overview, stage history, linked customer, linked proposals with status chips + create/send, comments, activity timeline), the customer-page Deals tab via `CustomerGroupedTabs`, the dashboard pipeline-summary widget, and the full end-to-end win-flow capstone plus vertical-pack QA.

**References**: Architecture §11.5.2 (win sequence), §11.4.2 (dashboard widget), §11.8 (frontend change table, deal detail, widget), §11.10 Slice 8; [ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md), [ADR-318](../adr/ADR-318-pipeline-metrics.md).

**Dependencies**: 576A (proposal link API), 578A (summary API), 579A (`lib/api/crm.ts`, board).

**Scope**: Frontend only

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **580A** | 580A.1–580A.4 | ~8 frontend files (1 page + ~4 components + 1 customer-tabs mod + 1 test) | Deal detail page; proposals chips + create/send; activity tab; `CustomerGroupedTabs` Deals tab + inline new-deal. |
| **580B** | 580B.1–580B.4 | ~6 files (1 widget + 1 dashboard mod + 1 types + E2E spec + QA) | `PipelineSummaryWidget`; dashboard composition; `dashboard-types.ts`; E2E capstone; vertical-pack QA. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 580A.1 | Create deal detail page | `frontend/app/(app)/org/[slug]/pipeline/[id]/page.tsx`, `components/pipeline/DealOverview.tsx` | 580A.4 | task/customer detail-page convention; `params` Promise | Overview: value, stage, probability, owner, source, expected close, custom fields; stage history; linked customer link. |
| 580A.2 | Linked proposals + activity + comments | `frontend/components/pipeline/DealProposalsPanel.tsx`, `components/pipeline/DealActivityTab.tsx` | 580A.4 | existing proposal status chips; `<AuditTimeline>`; existing `CommentSection` | Proposals list with status chips (DRAFT/SENT/ACCEPTED/…) + "create/send proposal" + "link existing"; activity tab reuses `<AuditTimeline>`; comments reuse existing component. |
| 580A.3 | Customer Deals tab | `frontend/app/(app)/org/[slug]/customers/[id]/...` (`CustomerGroupedTabs` integration), `components/pipeline/CustomerDealsTab.tsx` | 580A.4 | Phase 77 `CustomerGroupedTabs` | Add a **Deals** tab listing that customer's deals (`listDeals({customerId})`) with inline "new deal" intake. |
| 580A.4 | Frontend gates | `frontend/.../__tests__/deal-detail.test.tsx` | `pnpm lint/build/test` + format:check; component tests: detail renders, proposals chips render, customer Deals tab lists deals | §11.8 Slice 8 | — |
| 580B.1 | Create `PipelineSummaryWidget` + types | `frontend/components/dashboard/pipeline-summary-widget.tsx`, `frontend/lib/dashboard-types.ts` (modify) | 580B.4 | `frontend/components/dashboard/` (TeamUtilizationWidget pattern; SWR poll) | Open weighted value, deals-by-stage mini-bar, win rate. Add `PipelineSummaryResponse` TS interface to `dashboard-types.ts`. Admin/owner-scoped (consistent with other widgets). |
| 580B.2 | Compose widget into dashboard | `frontend/app/(app)/org/[slug]/dashboard/page.tsx` (modify) | 580B.4 | existing dashboard grid composition | Add `<PipelineSummaryWidget />` to the grid. |
| 580B.3 | E2E win-flow capstone | `frontend/tests/e2e/pipeline-win-flow.spec.ts` (mock-auth + KC) | E2E: enquiry → move across stages → link/send a proposal → accept → assert deal WON + customer advanced PROSPECT→ONBOARDING + proposal/project path reachable; widget renders | §11.8 E2E row; requirements §Test strategy | "PASS means observed" (browser → backend log → DB); reproduce-before-fix. |
| 580B.4 | Vertical-pack QA + frontend gates | (QA testplan; no source) | verify legal-za / consulting-za / accounting-za / default tenants seed correct stages on provisioning; `pnpm lint/build/test` + format:check; full `./mvnw verify` clean | §11.6f stage sets | Capstone QA: provision each vertical, confirm seeded stage names/types/probabilities; confirm board renders per-vertical columns. |

### Key Files

**Create (frontend):**
- `frontend/app/(app)/org/[slug]/pipeline/[id]/page.tsx`
- `frontend/components/pipeline/{DealOverview,DealProposalsPanel,DealActivityTab,CustomerDealsTab}.tsx`
- `frontend/components/dashboard/pipeline-summary-widget.tsx`
- `frontend/tests/e2e/pipeline-win-flow.spec.ts`

**Modify (frontend):**
- `frontend/app/(app)/org/[slug]/customers/[id]/...` — `CustomerGroupedTabs` Deals tab
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — compose widget
- `frontend/lib/dashboard-types.ts` — `PipelineSummaryResponse`

**Read for context:**
- `frontend/components/dashboard/` — widget + SWR-poll pattern
- Phase 77 `CustomerGroupedTabs` component — grouped-tabs customer detail
- existing `<AuditTimeline>` + `CommentSection` components

### Architecture Decisions

- **Reuse the proposal flow + AFTER_COMMIT win loop** ([ADR-315](../adr/ADR-315-win-proposal-conversion-reuse.md)) — UI surfaces the link/create/send; acceptance flips the board via the 576A listener.
- **Summary widget shares the endpoint's 90-day window** ([ADR-318](../adr/ADR-318-pipeline-metrics.md)) — widget and endpoint never disagree.

### Non-scope
- No portal exposure of deals (firm-internal). No `ReportDefinition` reports. No `intake-triage` wiring (seam only).

---

## Cross-Cutting Notes

- **Tenant isolation is mandatory** — `DealTenantIsolationTest` (574A.7) is a required gate; schema-per-tenant means `findById` is already isolated, but the test must prove cross-tenant `findById`/list returns not-found/empty under another tenant's schema. No `tenant_id` columns, no `@Filter`, no RLS (per `backend/CLAUDE.md`).
- **Migration numbering** — V130 + V131 are the only two tenant migrations; high-water at phase start is V129. Both idempotent (`IF NOT EXISTS`), run per tenant at provisioning + startup. No backfill (new tables). Stage seeding is pack-seeding, not SQL.
- **Package convention** — all new backend code lives in the new `crm/` package (entities, repos, services, controllers, `dto/`, `event/`, enums in-package), mirroring `proposal/`/`retainer/`. Dashboard summary DTO lives under `dashboard/dto/`.
- **Quality gates** — backend: full `./mvnw verify` clean; frontend: `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; E2E on mock-auth + Keycloak stacks.

### Critical Files for Implementation

- `backend/src/main/resources/db/migration/tenant/V130__create_crm_pipeline_tables.sql` (and `V131__add_deal_entity_type_constraint.sql`) — the schema foundation everything depends on.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/Deal.java` — the rich-domain aggregate root with all guarded transition methods (verbatim pattern in architecture §11.8).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealTransitionService.java` — the only writer of deal status/win/lose/reopen + the customer-lifecycle nudge.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/crm/DealProposalAcceptedListener.java` — the single thin AFTER_COMMIT listener that closes the win→proposal loop (reuses `ProposalAcceptedEvent`).
- `frontend/lib/api/crm.ts` — the shared frontend API module consumed by every frontend slice (board, settings, detail, widget).
