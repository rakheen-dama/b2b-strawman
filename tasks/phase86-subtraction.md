# Phase 86 — Subtraction Record (archive precondition, baseline metrics, confirmed seams)

**Date:** 2026-10-16 (UTC 2026-10-15T22:20Z)   **Epic:** 595 (slices 595A/595B)   **HEAD at snapshot:** `37150816e` (main == `phase86/00-archive-precondition-seams`)
**Status:** Stage 0 analysis complete — seam list confirmed; breakdown amended per the STOP rule (§6). Three scope decisions D1–D3 (§5) were **ACCEPTED 2026-10-16 (user): keep all three**; the owning slices (604B.1, 601B.4, 599A.1/599A.3) carry the consequences and the ADR-333 / architecture §11.3.4 amendments. "After" column and closure report are 606C's.

Conventions (architecture §11.3): `$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`, `$T` = `backend/src/test/java/io/b2mash/b2b/b2bstrawman/`, `$R` = `backend/src/main/resources/`, `FE` = `frontend/`, `PO` = `portal/`. Every `path:line` below was read at HEAD `37150816e` (`sed -n '<line>p'`); a range `a-b` means the symbol's block starts at or near `a`.

---

## TL;DR

- **Archive verified.** `horizontal-platform-final` is on origin (annotated tag object `22b528628`), points at `7f47f9629`, which is an ancestor of `origin/main`; the mirror `rakheen-dama/kazi-horizontal-archive` resolves with `HEAD` = `7f47f9629`.
- **Merge-gate hook** now denies `gh pr merge` on `phase86/*` / `epic/86-*` branches when the tag is missing from origin (fail-closed). Manually tested (deny / allow / branch-scoped / `epic/86-*` alias).
- **Before metrics** match every figure the breakdown expected (1,628 / 176,320 / 796 / 404 / 133 / 25 / 121 / 33 / 41 / 12 / 126 / 22 / 69). Full-verify wall time is the orchestrator's.
- **Seams:** the §11.3 backend compile seams for all nine removal packages are confirmed (IntelliJ call hierarchy + import/FQN/simple-name greps). **Many line numbers have drifted** (e.g. `VerticalModuleRegistry`, `nav-items.ts`, `SecurityConfig`, the provisioning seeder wiring) and are corrected below. §3 holds **239 confirmed seam rows, 101 of them not covered by §11.3 or the breakdown**. Most of those are tests that would fail at compile or assertion time, but **nine are main-code seams the slices would trip on**: `PortalDigestContentAssembler` imports the deleted portal-retainer read model (598A, compile); `ReportService` profitability projections run on `ResourceAllocation` (599A, compile); `OrgSettingsService` injects `SchedulePackSeeder` (600A, compile); `AutomationConfigDeserializer:30` `PROPOSAL_SENT` (597A, compile) and the three `PROPOSAL_SENT` automation-template JSON rules, which stop deserializing once the constant goes and would break every tenant provisioning (597A.5, moved from 597B.3); `BillingResponse:44` reads the dropped `next_billing_at` (602A); `portal.portal_notification_preference.retainer_updates_enabled` column (598B, global V29, same PR as the entity field); `PortalEmailService` proposal/retainer senders (597A/598A); `EntityType.DEAL` (596A). All were added to the owning slices' task tables (§6).
- **Enum-string rewrites:** only `RESOURCE_PLANNING` is migration-seeded. `pack_installs` does not exist (the table is `pack_install`, and its `pack_type` never holds `deal_pipeline`/`schedule`/`rate`), so the three `DELETE FROM pack_installs …` lines are dropped from 596A.2/600A.3/604C.1.
- **Hedges:** all 6 §11.10 slice-1 hedges, the §11.3.11 build-time rows and Open Questions #1–#10, #12, #15, #18, #19, #28 are resolved in §5. Three items need an orchestrator decision: `generic-onboarding` (604B), `ai.specialist.failed` still emitted by the kept AI-skill pipeline (601B), and the time-based `/api/reports/utilization` endpoint that feeds the kept profitability page (599A/599C).

---

## 1. Archive verification record (595A.1)

Run from the worktree on 2026-10-16 (`bash -x`; output verbatim):

```
+ git fetch --tags origin
+ git tag -l horizontal-platform-final
horizontal-platform-final
+ git ls-remote --tags origin refs/tags/horizontal-platform-final
22b528628fd618e6ca7c6324521341ebbc43ea01	refs/tags/horizontal-platform-final
+ git rev-parse 'horizontal-platform-final^{commit}'
7f47f96295192aab1970b22a36231d73d4beada9
+ git rev-parse horizontal-platform-final
22b528628fd618e6ca7c6324521341ebbc43ea01
+ git cat-file -t horizontal-platform-final
tag
+ git merge-base --is-ancestor 'horizontal-platform-final^{commit}' origin/main
+ echo ANCESTOR_OK
ANCESTOR_OK
+ git ls-remote https://github.com/rakheen-dama/kazi-horizontal-archive HEAD
7f47f96295192aab1970b22a36231d73d4beada9	HEAD
+ git for-each-ref refs/tags/horizontal-platform-final '--format=%(taggerdate:iso) %(objectname) %(subject)'
2026-10-15 22:17:47 +0200 22b528628fd618e6ca7c6324521341ebbc43ea01 Last commit of the horizontal multi-vertical platform before Phase 86 subtraction (ADR-330). Mirrored to rakheen-dama/kazi-horizontal-archive.
+ git rev-parse origin/main HEAD
37150816ef05b1f93955d045e0bd4b9a86594a57
37150816ef05b1f93955d045e0bd4b9a86594a57
+ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-15T22:20:34Z
```

| Item | Value |
|---|---|
| Tag | `horizontal-platform-final` (annotated; `git cat-file -t` → `tag`) |
| Tag object | `22b528628fd618e6ca7c6324521341ebbc43ea01` |
| Tagged commit | `7f47f96295192aab1970b22a36231d73d4beada9` |
| Tagger date | 2026-10-15 22:17:47 +0200 |
| On origin | yes (`git ls-remote --tags origin`) |
| Ancestor of `origin/main` (`37150816e`) | yes (`ANCESTOR_OK`) — therefore an ancestor of every Phase 86 slice base |
| Mirror | https://github.com/rakheen-dama/kazi-horizontal-archive — `HEAD` → `7f47f96295192aab1970b22a36231d73d4beada9` (== tagged commit) |
| Verified on | 2026-10-15T22:20:34Z (2026-10-16 00:20 SAST) |
| Merge gate | `.claude/hooks/pre-pr-merge-gate.sh` denies `gh pr merge` on `phase86/*` / `epic/86-*` when the tag is absent on origin (§11.8.5); see the PR body for the manual test transcript |

Resurrection (ADR-330, recorded so it is not re-derived): `git fetch --tags origin && git checkout -b resurrect/<vertical> horizontal-platform-final`, then read §11.3's seam tables **and §3 below** in reverse — together they list every consumer the package had. The archive repo is the fallback if the tag is ever lost from origin. Read a pre-squash migration with `git show horizontal-platform-final:backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql`.

---

## 2. Before metrics (595A.3)

| # | Surface | Before (main `37150816e`, 2026-10-16) | Expected (breakdown / §11.1) | Match? | After (606C) |
|---|---|---|---|---|---|
| 1 | Backend main `.java` files | **1,628** | 1,628 | yes | 1,375 |
| 2 | Backend main lines | **176,320** | 176,320 | yes | 148,468 |
| 3 | Backend test `.java` files | **796** | 796 | yes | 703 |
| 4 | Backend test lines | **227,117** | — | n/a | 197,092 |
| 5 | Backend top-level packages (`$B` dirs) | **69** (70 entries − `BackendApplication.java`) | 69 | yes | 61 |
| 6 | Frontend vitest files (`*.test.ts[x]`) | **404** | 404 | yes | 348 |
| 7 | Frontend Playwright specs (`*.spec.ts`) | **76** | — | n/a | 58 |
| 8 | Portal vitest files | **42** | — | n/a | 38 |
| 9 | Portal Playwright specs | **5** | — | n/a | 5 |
| 10 | Frontend `.ts/.tsx` files / lines (excl. `node_modules`, `.next`) | **1,584 / 241,793** | — (scout: 1,585 / 241,799) | n/a — see note | 1,336 / 201,714 (git ls-files) |
| 11 | Portal `.ts/.tsx` files / lines | **117 / 15,205** | — | n/a | 105 / 13,103 |
| 12 | `packages/` `.ts/.tsx` files / lines (excl. `node_modules`, `dist`) | **12 / 747** | — | n/a | 12 / 662 |
| 13 | Tenant migrations (count / high-water) | **133 / `V135__add_cash_digest_reference_type.sql`** | 133 / V135 | yes | 1 / `V1__baseline.sql` |
| 14 | Global migrations (count / high-water) | **25 / `V25__create_shard_config.sql`** | 25 / V25 | yes | 1 / `V1__baseline.sql` |
| 15 | `@Entity` classes (files / annotations) | **121 / 121** | 121 | yes | 103 / 103 |
| 16 | `Capability` constants | **33** (removal targets at `:14` RESOURCE_PLANNING, `:40` AI_ASSISTANT_USE, `:71/:73/:75/:77` VIEW_DEALS/MANAGE_DEALS/CLOSE_DEALS/MANAGE_PIPELINE) | 33 | yes | 27 |
| 17 | Audit event-type catalogue entries | **41** (test pin `AuditEventTypeRegistryTest:81,121,122`; independent count: 41 `new AuditEventTypeMetadata(` in the `List.of` at `AuditEventTypeRegistry.java:35-229`; the other 2 occurrences are the resolver fallbacks at `:313`, `:342`) | 41 | yes | 29 |
| 18 | `VerticalModuleRegistry` modules (total / vertical / horizontal) | **12 / 8 / 4** (pins `VerticalModuleRegistryTest:22,192,98`) | 12 / 8 / 4 | yes | 8 / 6 / 2 |
| 19 | Frontend `page.tsx` | **126** | 126 | yes | 100 |
| 20 | Portal `page.tsx` | **22** | 22 | yes | 18 |
| 21 | Full backend verify wall time (`backend/scripts/verify.sh`) | 1555 s (25 min 55 s) — `backend/scripts/verify.sh -q` on 2026-10-16, 6084 tests run, 2 pre-existing calendar-rolling failures (PR #1563) | — | — | 751 s — 5,294 testcases / 0 F / 0 E / 26 skipped (§7.3) |

Note on row 10: the main repo has a generated, git-ignored `frontend/next-env.d.ts` (one file, six lines) that the fresh worktree does not, so it accounts for the whole difference from the scout's count. `git ls-files frontend | grep -cE '\.(ts|tsx)$'` = 1,584 in both trees, so 606C should re-run row 10 in a tree without `next-env.d.ts`, or with `git ls-files`.

Commands (bash, from the repo root; 606C re-runs these verbatim):

```bash
B=backend/src/main/java/io/b2mash/b2b/b2bstrawman
find backend/src/main/java -name '*.java' | wc -l                                   # 1
find backend/src/main/java -name '*.java' -print0 | xargs -0 cat | wc -l            # 2
find backend/src/test/java -name '*.java' | wc -l                                   # 3
find backend/src/test/java -name '*.java' -print0 | xargs -0 cat | wc -l            # 4
find $B -mindepth 1 -maxdepth 1 -type d | wc -l                                     # 5
for app in frontend portal; do                                                      # 6-11, 19-20
  find $app -path $app/node_modules -prune -o -path $app/.next -prune -o \( -name '*.test.ts' -o -name '*.test.tsx' \) -print | wc -l
  find $app -path $app/node_modules -prune -o -path $app/.next -prune -o -name '*.spec.ts' -print | wc -l
  find $app -path $app/node_modules -prune -o -path $app/.next -prune -o \( -name '*.ts' -o -name '*.tsx' \) -print | wc -l
  find $app -path $app/node_modules -prune -o -path $app/.next -prune -o \( -name '*.ts' -o -name '*.tsx' \) -print0 | xargs -0 cat | wc -l
  find $app/app -name page.tsx | wc -l
done
find packages -path '*/node_modules' -prune -o -path '*/dist' -prune -o \( -name '*.ts' -o -name '*.tsx' \) -print | wc -l          # 12
find packages -path '*/node_modules' -prune -o -path '*/dist' -prune -o \( -name '*.ts' -o -name '*.tsx' \) -print0 | xargs -0 cat | wc -l
M=backend/src/main/resources/db/migration
ls $M/tenant/*.sql | wc -l; ls $M/tenant/ | sort -V | tail -1                       # 13
ls $M/global/*.sql | wc -l; ls $M/global/ | sort -V | tail -1                       # 14
grep -rlE '^\s*@Entity\b' backend/src/main/java --include='*.java' | wc -l          # 15 (files)
grep -rhE '^\s*@Entity\b' backend/src/main/java --include='*.java' | wc -l          # 15 (annotations)
grep -cE '^\s+[A-Z_]+(\(|,|;)' $B/orgrole/Capability.java                           # 16
grep -n 'hasSize' backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistryTest.java   # 17 (pin)
grep -c 'new AuditEventTypeMetadata(' $B/audit/AuditEventTypeRegistry.java          # 17 (minus the 2 resolver fallbacks)
grep -c 'ModuleCategory.VERTICAL,' $B/verticals/VerticalModuleRegistry.java         # 18
grep -c 'ModuleCategory.HORIZONTAL,' $B/verticals/VerticalModuleRegistry.java       # 18
# 21: orchestrator — time `bash backend/scripts/verify.sh` on the slice tree
```

---
## 3. Confirmed seams (595B.1)

### 3.0 Method

- **Call hierarchy (IntelliJ `analyze_calls`, INCOMING, depth 1)** on the services other packages call. These calls returned trees that match the grep pass: `crm.PipelineSummaryService.getSummary` → only `dashboard/DashboardService.getPipelineSummary` (+ own tests). `proposal.ProposalService.getProposal` → only `proposal/*`, `crm/DealProposalService.linkExisting` and own tests. `billingrun.RetainerBillingService.{loadRetainerPreview,generateRetainerInvoices}` → only `billingrun/BillingRunService`. `capacity.CapacityService.{getMemberCapacity,getMemberEffectiveCapacity}` → only `capacity/*` + own tests. `billing.payfast.PlatformPayFastService.{generateCheckoutForm,cancelPayFastSubscription}` → only `billing/SubscriptionService.{initiateSubscribe,cancelSubscription}` + `SubscriptionItnIntegrationTest` + own tests. `deadline.DeadlineCalculationService.{calculateDeadlines,calculateSummary}` → `deadline/*`, `project/ProjectUpcomingDeadlinesService.getUpcomingDeadlines`, `ProjectUpcomingDeadlinesServiceTest`. Four calls **timed out** in the IDE (`assistant.provider.LlmChatProviderRegistry.{get,getAll}`, `retainer.RetainerPeriodService.closePeriod`, `schedule.RecurringScheduleService.list`). Those packages were covered by the grep passes below, which are authoritative for non-callable types anyway.
- **Grep passes** (scratch output `…/scratchpad/seams595/*.txt`): (1) import / FQN / wildcard references to `io.b2mash.b2b.b2bstrawman.<pkg>.` from outside the package (main + test): crm 4, proposal 18, retainer 44, capacity 2, schedule 24, assistant 19, billing.payfast 4, dev 0, deadline 6. (2) Simple-name references to every public type, outside the package and not on an import line: crm 18, proposal 85, retainer 89, capacity 6, schedule 56, assistant 47, deadline 7, all read by hand. (3) Named classes (`RatePackSeeder`, demo seeders, PayFast billing classes, `DevPortalController`, `*PackSeeder`, `TimeReminder*`, `InvokeAiSpecialist*`, `RetainerBillingService`, `CapacitySettings`, `PortalRetainerMemberDisplay`). (4) Case-insensitive keyword sweeps (`proposal`, `retainer`, `capacity|utiliz|allocation`, `schedule`, `assistant|specialist`, `PAYFAST|…`, `portal/dev`, `deadline|filing`, `RatePack`, `accounting-za|consulting-za|consulting-generic`) over `backend/src`, listing every file the breakdown and architecture do not name. (5) Removed enum constants / module ids / job types / email-template names. (6) Frontend and portal: route-prefix grep (line level), importers of every deleted `lib/`/`components/` module, and the removed string literals (capabilities, module ids, trigger/action types, notification types, `PAYFAST`).
- **Column "§11.3?":** `Y` = already in §11.3 or the breakdown task rows with the right line. `Y*` = listed, but the line numbers drifted or the claim is wrong (corrected here, and in the breakdown when it changes the work). `N` = not covered → added to the owning slice in `tasks/phase86-subtraction-legal-only.md` (§6). Tests inside a deleted package directory are not listed: they are deleted with it.

### 3.1 Epic 596 — `crm`

**596A (backend compile + migration)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/dashboard/DashboardService.java:9,81,98,107,115-121` | `crm.PipelineSummaryService` (import, field, ctor arg, assign, `getPipelineSummary`) | Y* (doc: `:9,81`) | 596A.3 | drop field/ctor arg/method |
| `$B/dashboard/DashboardController.java:7,141-156` (`@GetMapping("/api/dashboard/pipeline-summary")` `:148`, `@RequiresCapability("VIEW_DEALS")` `:149`) | `dashboard.dto.PipelineSummaryResponse`, `VIEW_DEALS` | Y* (doc: `:138-155`) | 596A.3 | drop endpoint |
| `$B/provisioning/TenantProvisioningService.java:19,84,108,130,247` | `seeder.DealPipelinePackSeeder` | Y* (doc: `:74-126`) | 596A.3 | drop import/field/ctor/assign/call |
| `$B/provisioning/PackReconciliationRunner.java:14,57,76,93,151` | `DealPipelinePackSeeder` | Y* (doc: `:46-71`) | 596A.3 | same |
| `$T/provisioning/TenantProvisioningServiceTest.java:30,75` | `@Mock DealPipelinePackSeeder` | Y | 596A.3 | drop mock |
| `$B/settings/PackStatusSettings.java:76,328-355` | `deal_pipeline_pack_status` + `get/record/isDealPipelinePack*` | Y | 596A.3 | drop |
| `$B/settings/OrgSettings.java:99-100` | `@AttributeOverride deal_pipeline_pack_status` | Y | 596A.3 | drop |
| `$B/verticals/VerticalProfileReconciliationSeeder.java` | — (no deal/pack-type code; it reconciles tax rates only) | Y* | 596A.3 | **no-op**: nothing to drop |
| `$R/vertical-profiles/legal-za.json:14` | `"deal_pipeline": ["deal-pipeline-legal-za"]` | Y | 596A.3 | remove key. No main-code reader of `VerticalProfile.packs()` exists, so the removal is cosmetic |
| `$B/seeder/DealPipelinePackSeeder.java:3-5` | `crm.{PipelineStage,PipelineStageRepository,StageType}` | Y | 596A.1 | deleted (test lives at `$T/crm/DealPipelinePackSeederTest.java`, deleted with `$T/crm/**`) |
| `$B/fielddefinition/EntityType.java:9` | `DEAL` constant (persisted by `FieldDefinition.entityType` `:28-30` and `FieldGroup.entityType` `:25-27`, `@Enumerated(STRING)`) | **N** | 596A.5 | delete constant + switch arms below + V136 row delete (§4) |
| `$B/fielddefinition/FieldDefinitionService.java:385`, `$B/fielddefinition/FieldGroupService.java:341` | `case DEAL -> "deals"` | **N** | 596A.5 | drop arm |
| `$B/template/VariableMetadataRegistry.java:109-111` | `case DEAL -> Set.of()` | **N** | 596A.5 | drop arm |
| `$B/customerbackend/service/DeadlinePortalSyncService.java:290` | `case TASK, INVOICE, DEAL` | **N** | 596A.5 | drop `DEAL` |
| `$B/activity/ActivityMessageFormatter.java:123-127,376` | five `deal.*` message arms (`deal.created`/`stage_changed`/`won`/`lost`/`reopened`) + `case "DEAL", "deal"` | **N** | 596B.5 | drop arms (strings; not compile-critical) |

**596B (residue, audit, notification, capability)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/view/ViewFilterService.java:26` (+ javadoc `:54,115`) | `"deals"` | Y | 596B.1 | drop |
| `$B/view/SearchFilterHandler.java:8,19,33` | `"DEAL"` | Y | 596B.1 | drop |
| `$B/audit/AuditEventGroup.java:25` | `SALES` (not persisted; no frontend audit-group filter lists `SALES` — only `SALES_TAX` hits) | Y | 596B.1 | drop |
| `$B/audit/AuditEventTypeRegistry.java:113-126` | 5 × `deal.*` | Y | 596B.2 | drop; `AuditEventTypeRegistryTest:76` comment, `:81,121,122` → 36, `:90` |
| `$B/notification/NotificationService.java:220` | `"DEAL_WON"` | Y | 596B.3 | drop |
| `$B/notification/channel/EmailNotificationChannel.java:225,338` | `DEAL_WON` → `notification-deal-won` | Y | 596B.3 | drop |
| `$T/notification/channel/EmailNotificationChannelIntegrationTest.java:231-267` | `deliver_maps_deal_won_to_template` (`"DEAL_WON"`, `"DEAL"`, template `notification-deal-won`) | **N** | 596B.5 | delete test method |
| `$B/orgrole/Capability.java:71,73,75,77` | `VIEW_DEALS`, `MANAGE_DEALS`, `CLOSE_DEALS`, `MANAGE_PIPELINE` (outside `crm/` only `DashboardController:149` uses one) | Y | 596B.3 | drop |

**596C (frontend)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `FE/app/(app)/org/[slug]/dashboard/page.tsx:16` | `PipelineSummaryWidget` | Y | 596C.2 | drop |
| `FE/app/(app)/org/[slug]/customers/[id]/page.tsx:33-34,38` | `@/lib/api/crm`, `@/components/pipeline` (`CustomerDealsTab`) | Y | 596C.2 | drop |
| `FE/lib/dashboard-types.ts:6` | `@/lib/api/crm` | Y | 596C.2 | drop |
| `FE/components/settings/{StageConfigList:38,45, StageEditDialog:26,28, StageReorder:21}.tsx` | `@/lib/schemas/deal`, `@/lib/api/crm` | Y | 596C.1 | deleted |
| `FE/lib/nav-items.ts:138-143` ("Pipeline", `VIEW_DEALS` `:142`), `:472-477` (SETTINGS "Pipeline") | nav | Y* (doc: `:153-158`, `:487-492`) | 596C.3 | drop |
| `FE/lib/capabilities.tsx:21-24,90-105` | 4 deal capabilities | Y | 596C.3 | drop |
| `FE/lib/types/field.ts:3` | `EntityType … \| "DEAL"` | **N** | 596C.5 | drop `"DEAL"` (mirror of the backend enum) |
| `FE/__tests__/pipeline/{deal-detail,pipeline-board-hydration,pipeline}.test.tsx` | `@/components/pipeline`, `@/lib/api/crm` | **N** | 596C.5 | delete |
| `FE/__tests__/settings/{settings-pipeline,stage-reorder-hydration}.test.tsx` | `@/lib/api/crm` | **N** | 596C.5 | delete |

Removal → removal: 8 of `proposal`'s 10 main-code importers sit in `crm/` (`crm/dto/LinkedProposalDto:3-4`, `crm/dto/CreateDealProposalRequest:3`, `crm/DealProposalAcceptedListener:4`, `crm/DealProposalService:10-12`, `crm/DealService:12`). That is why `crm` goes first, which leaves `audit/DatabaseAuditService` as `proposal`'s only external main-code consumer. `proposal.Proposal.deal_id` (`ProposalRepository:22-28`, `ProposalService:237-239`) stays mapped until 597A.

### 3.2 Epic 597 — `proposal` + legacy in-app portal + portal proposals

**597A (backend compile + migration)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/audit/DatabaseAuditService.java:15-16,59,71,81,319-349` | `proposal.{Proposal,ProposalRepository}` fan-out | Y | 597A.3 | drop |
| `$B/event/DomainEvent.java:50` | `ProposalSentEvent` in `permits` | Y | 597A.3 | drop |
| `$B/automation/TriggerType.java:12` | `PROPOSAL_SENT` | Y | 597A.3 | drop |
| `$B/automation/TriggerTypeMapping.java:15,44` | `ProposalSentEvent` | Y | 597A.3 | drop |
| `$B/automation/AutomationContext.java:14,67,213-219` | `buildProposalSent` | Y (no lines in doc) | 597A.3 | drop |
| `$B/automation/config/AutomationConfigDeserializer.java:30` | `PROPOSAL_SENT` (trigger-config switch) | **N** | 597A.5 | drop arm (compile-critical once the constant goes) |
| `$R/automation-templates/legal-za.json:28-49` (trigger `:33`), `common.json:127-148` (`:132`), `consulting-za.json:141-173` (`:146`); `$T/packs/LegalZaAutomationPackTest.java:50`, `ConsultingZaAutomationPackTest.java:47,73,85` | `"triggerType": "PROPOSAL_SENT"` rule objects (`engagement-letter-followup`, `proposal-sent-followup`, `consulting-za-proposal-followup-5d`) + slug/count pins | Y* (breakdown had legal-za/common in 597B.3; consulting-za not at all) | 597A.5 | delete the three rule objects and the pins **in 597A**: `AbstractPackSeeder.loadPacks` (`:166-187`) parses every pack JSON, `:176-179` rethrows a bind failure as `IllegalStateException`, and provisioning reaches it via `TenantProvisioningService.java:243` → `PackCatalogService.getUniversalPackIds:164` → `AutomationPackInstaller.availablePacks:65`. `LegalZaAutomationPackTest:109` `hasSize(64)` is the content-hash length, not a count |
| `$B/notification/NotificationEventHandler.java:18,436-453` | `onProposalSent(ProposalSentEvent)` | Y | 597A.3 | drop |
| `$B/portal/notification/PortalEmailNotificationChannel.java:56` | javadoc `{@code ProposalSentEvent}` | Y | 597A.3 | reword |
| `$B/portal/PortalEmailService.java:44-49,214-231` | `sendNewProposalEmail`, `sendProposalExpiredEmail`, `portal-new-proposal`/`portal-proposal-expired` templates, `PORTAL_NEW_PROPOSAL`/`PORTAL_PROPOSAL_EXPIRED` reference types (callers are only in `proposal/`) | **N** | 597A.5 | delete both methods + constants (their templates are deleted in 597A.1 and their reference types leave the CHECK in V137) |
| `$T/architecture/TenantScopeBindingTest.java:199-202` | 4 proposal handler FQNs | Y | 597A.3 | drop |
| `$T/automation/ProposalSentTriggerWiringTest.java` (whole file; `:6` imports `event.ProposalSentEvent`) | `ProposalSentEvent`, `TriggerType.PROPOSAL_SENT` | **N** | 597A.5 | delete (compile) |
| `$T/infrastructure/jobqueue/SchedulerMigrationBatch2ATest.java:71,99,116`, `SchedulerMigrationBatch2BTest.java:99` | job type `"proposal_expiry"` (`proposal/ProposalExpiryHandler:31`) asserted registered | **N** | 597A.5 | remove the string from the expected sets and the `job3` enqueue |
| `$T/prerequisite/ActionPointPrerequisiteTest.java:208-231,344-381,448-453` | `POST /api/proposals`, `/api/proposals/{id}/send` | Y (`$T/prerequisite/*`) | 597B.2 | delete the proposal cases |

**597B (residue)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/notification/template/EmailTerminology.java:12-13,28-31` | proposal → engagement-letter nouns | Y | 597B.1 | drop |
| `$B/notification/template/EmailContextBuilder.java:97-104` | `proposalTerm*` | Y | 597B.1 | drop |
| `$T/notification/template/EmailTerminologyTest.java:26`, `EmailContextBuilderTest.java:71,96` | pinned proposal terms | **N** | 597B.6 | drop assertions |
| `$B/template/TemplateCategory.java:9` | `PROPOSAL` | Y | 597B.1 | drop |
| `$T/template/DocumentAuditNotificationTest.java:141` | `TemplateCategory.PROPOSAL` | **N** | 597B.6 | switch to another category (compile) |
| `$B/prerequisite/PrerequisiteContext.java:7`; `PrerequisiteService.java:199`; `StructuralPrerequisiteCheck.java:54-55,91,110,130` | `PROPOSAL_SEND` | Y | 597B.2 | drop; tests `PrerequisiteServiceTest:230`, `StructuralPrerequisiteCheckTest:81` |
| `$B/setupstatus/CustomerReadinessService.java:222` | `PrerequisiteContext.valueOf(contextName)` over `field_definitions.required_for_contexts` jsonb (V53:18 backfilled `PROPOSAL_SEND`) | **N** | 597A.5 (V137 rewrite) | no code change (unknown names are already caught and logged at `:225-227`); V137 carries the defensive jsonb rewrite (§4) |
| `$B/audit/AuditEventTypeRegistry.java:112` | `proposal.*` | Y | 597B.4 | drop → 35 |
| `$B/notification/NotificationService.java:190-193`; `EmailNotificationChannel.java:222-223,331` | `PROPOSAL_*` | Y | 597B.4 | drop |
| `$T/notification/channel/EmailNotificationChannelIntegrationTest.java:363-384` | `deliver_proposal_email_links_to_org_scoped_proposal` | **N** | 597B.6 | delete test method |
| `$B/activity/ActivityMessageFormatter.java:83-98,332-334,372` | `proposal.*` activity strings + `getProposalNumber` | **N** | 597B.6 | drop arms + helper (dead after 597A) |
| `$B/portal/PortalActivityEventTypes.java:28-34` | `"proposal.sent"…"proposal.acceptance.completed"` in the client-facing allowlist | **N** (§11.3.11 row 9 says "no constants" — true for Java constants, but the allowlist holds 6 proposal strings) | 597B.6 | drop the 6 strings. `proposal.acceptance.completed` has **no emitter** anywhere in `$B` |
| `$B/portal/PortalActivityEventResponse.java:92-97` | proposal summaries ("Engagement letter …") | **N** | 597B.6 | drop arms |
| `$T/portal/PortalActivityServiceFirmFilterTest.java:253-295` | seeds `proposal.sent/accepted`, asserts summary | **N** | 597B.6 | re-fixture on a surviving firm-tab type (e.g. `invoice.sent`) |
| `$B/portal/notification/PortalNotificationPreference*`, `PortalDigestContentAssembler` | — (no proposal preference type or digest section exists) | Y* | 597B.4 | **no-op** for proposals (the retainer parts are 598) |
| `$R/automation-templates/legal-za.json:30-36`, `common.json` (`PROPOSAL_SENT`) | rules | Y* | ~~597B.3~~ → 597A.5 | moved to 597A (see the 597A row above) |

**597C / 597D (frontend, portal, packages)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `FE/components/projects/overflow-actions-menu.tsx:15` | `@/components/proposals` | Y | 597C.2 | drop |
| `FE/components/templates/*`, `settings/templates/{UploadDocxDialog,templates-content}.tsx`, `lib/types/template.ts` | template-category mirrors — **contain no `PROPOSAL`** | Y* | 597C.2 | **no-op** |
| `FE/lib/nav-items.ts:144-149` ("Proposals") | nav | Y* (doc: `:159-164`) | 597C.3 | drop |
| `FE/lib/api/automations.ts:25` | `"PROPOSAL_SENT"` in `TriggerType` union | Y (601C lists the file) | 597C.2 | drop the member |
| `FE/components/automations/trigger-config-form.tsx:56`, `rule-form.tsx:91`, `trigger-type-badge.tsx:16` | `PROPOSAL_SENT` | **N** | 597C.5 | drop (typecheck breaks once the union member goes; the two editor files are deleted later in 604D) |
| `FE/components/notifications/notification-preferences-form.tsx:28-31` | `PROPOSAL_*` labels | **N** | 597C.5 | drop |
| `FE/components/prerequisite/types.ts:7,14` | `PROPOSAL_SEND` | **N** | 597C.5 | drop |
| `FE/__tests__/proposals-dashboard.test.tsx` | `@/components/proposals`, `@/lib/types/proposal` | **N** | 597C.5 | delete |
| `FE/__tests__/components/dialog-family-ssr.snapshot.test.tsx:72` | `CreateProposalDialog` | **N** | 597C.5 | drop the case (+ snapshot) |
| `FE/e2e/tests/lifecycle-portal.spec.ts:207` | `GET /api/proposals` | **N** | 597C.5 | drop proposal steps |
| `packages/shared/src/format.ts:113-117` | `formatProposalExpiresAt` (sole importer `FE/app/(app)/org/[slug]/proposals/[id]/page.tsx`, deleted) | **N** | 597C.5 | delete the helper |
| `PO/app/(authenticated)/settings/__tests__/notifications.test.tsx` | proposal toggles | Y (597D.1 "page.tsx (+ test)") | 597D.1 | edit |
| `PO/lib/nav-items.ts:70-75,113` | `proposals` nav item + `portal.nav.proposals` label | Y* (doc: `:84-88,127`) | 597D.1 | drop |
| `PO/e2e/tests/responsive/portal-pages.spec.ts:39,48` | `/proposals` page entry | **N** | 597D.3 | drop entry |
| `PO/e2e/tests/portal-client-90day/day-14-30.spec.ts` (28 proposal/retainer mentions) | proposal + retainer steps | **N** | 597D.3 / 598D.3 | drop the deleted-feature steps only |

Removal → removal: `proposal/ProposalOrchestrationService:25-29` imports five `retainer` types (it creates retainers from accepted proposals). `proposal` is deleted before `retainer` (597 then 598), so no edit is needed. In the other order, 598 would have to cut this seam.

### 3.3 Epic 598 — `retainer` + portal retainer

**598A (backend compile + migration)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/billingrun/BillingRunService.java:12-13,37,44,49,171-184` | `RetainerBillingService`, retainer DTOs | Y | 598A.3 | drop (call hierarchy: only callers of `RetainerBillingService`) |
| `$B/billingrun/BillingRunController.java:12-13,161-173` | `/{id}/retainer-preview`, `/{id}/retainer-generate` | Y | 598A.3 | drop |
| `$B/billingrun/BillingRunLifecycleService.java:80`; `BillingRun.java:43-44,81,88,157`; `dto/BillingRunDtos.java:32,42,62,212-225` | `includeRetainers`, retainer DTOs | Y | 598A.3 | drop |
| `$T/billingrun/BillingRunControllerTest.java:154-336` (asserts `$.includeRetainers` at `:165`; posts `true` at `:189`) | `includeRetainers` | **N** | 598A.5 | drop the field from requests and assertions. `BillingRunGenerateTest:519` and `BillingRunNotificationTest:157,360` only send `"includeRetainers": false` (unknown JSON properties are ignored), so clean them up |
| `$B/customer/CustomerService.java:26,56,71,84,508-511` | `RetainerAgreementRepository` delete guard | Y | 598A.3 | drop |
| `$T/project/DeleteProtectionIntegrationTest.java:34,286-298,461-471` | `archive_customer_with_retainers_rejected` + `POST /api/retainers` helper | **N** | 598A.5 | delete method + helper |
| `$B/invoice/InvoiceLine.java:34-35,186`; `InvoiceLineType.java:7` | `retainer_period_id`, `RETAINER` | Y | 598A.3 | drop |
| `$B/portal/notification/PortalEmailNotificationChannel.java:9,47,183,358,475` | `RetainerPeriodRolloverEvent` listener | Y | 598A.3 | drop |
| `$B/portal/PortalEmailService.java:39-40,198-201` | `sendRetainerClosedEmail` (`portal-retainer-period-closed`, `PORTAL_RETAINER`) | **N** | 598A.5 | delete (sole caller is the listener above) |
| `$B/portal/notification/PortalDigestContentAssembler.java:7,11,34,39,49,56,61,118-150` | `customerbackend.model.PortalRetainerSummaryView`, `repository.PortalRetainerSummaryRepository` (**compile-critical**: both are deleted in 598A.1) | **N** | 598A.5 | drop the retainer snapshot section |
| `$R/templates/email/portal-weekly-digest.html` | `hasRetainers` / `retainerSummaries` | **N** | 598A.5 | drop the section |
| `$T/architecture/TenantScopeBindingTest.java:185` | `customerbackend.service.RetainerPortalSyncService` FQN | **N** | 598A.5 | drop |
| `$B/portal/PortalContextController.java` | — (lives in `portal/`, **not** `customerbackend/`; `grep -i retainer` → no match) | Y* (Open Q #6) | 598A.3 | **no-op**: remove the item |
| `$T/portal/PortalContextControllerIntegrationTest.java:93-99,149` | `"retainer_agreements"` in `enabledModules` | **N** | 598B.6 | drop (fails once the module leaves the registry) |
| `$B/multitenancy/RequestScopes.java:212,221`; `$B/customerbackend/service/TrustLedgerPortalSyncService.java:326`; `DeadlinePortalSyncService.java:29`; `$T/multitenancy/RequestScopesTest.java:335`; `$T/customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java:498` | comments citing `RetainerPortalSyncService` | **N** | — | comment-only; leave (dead-reference sweep is 606B/Phase 87) |

**598B (settings, registry, notifications, portal prefs)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/settings/PortalSettings.java:39-41` + `OrgSettingsController`/`OrgSettingsService`/`dto/SettingsResponse` | `portal_retainer_member_display` | Y | 598B.1 | drop |
| `$B/verticals/VerticalModuleRegistry.java:182-193` | `retainer_agreements` | Y* (doc: `:220-230`; breakdown corrected) | 598B.2 | drop |
| `$B/notification/NotificationService.java:178-182`; `EmailNotificationChannel.java:216-221,317-326` | `RETAINER_*` | Y | 598B.3 | drop |
| `$B/portal/notification/PortalNotificationPreference.java:34-35,54,65,70,88-89`; `PortalNotificationPreferencesResponse.java:11`; `PortalNotificationPreferenceController.java:50,74`; `PortalNotificationPreferenceService.java:68,101,111` | `retainerUpdatesEnabled` | Y (598B.3 "portal prefs retainer types", wildcard; now explicit) | 598B.3 | drop field end to end |
| global `V22__portal_notification_preference.sql:11` | `portal.portal_notification_preference.retainer_updates_enabled` column | **N** | 598B.3 (NEW global `V29`) | `ALTER TABLE portal.portal_notification_preference DROP COLUMN IF EXISTS retainer_updates_enabled;` in a 598B global migration (V29 = next free after V26 597A, V27 598A, V28 602A), in the **same** PR as the entity field removal (598B.3). Not in 598A's V27 |
| `$T/portal/notification/PortalNotificationPreferenceServiceIntegrationTest.java:73,103,111`; `PortalNotificationPreferenceControllerTest.java:118,132,147,176` | `retainerUpdatesEnabled` | **N** | 598B.6 | drop |
| `$T/notification/template/EmailTemplateRenderingIntegrationTest.java:164-180,244` | renders `notification-retainer` (deleted in 598A.1) | **N** | 598A.5 | delete method + list entry |

**598C / 598D (frontend, portal)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `FE/app/(app)/org/[slug]/customers/[id]/page.tsx:73-75` | `@/lib/api/retainers`, `customer-retainer-tab` | Y | 598C.2 | drop |
| `FE/app/(app)/org/[slug]/projects/[id]/page.tsx:224` | `/api/retainers` | Y | 598C.2 | drop |
| `FE/app/(app)/org/[slug]/projects/page.tsx:5` | `fetchRetainerSummary` from `@/lib/api/retainers` | **N** (598C.2 lists only `projects/[id]`) | 598C.4 | drop |
| `FE/lib/types/invoice.ts:242`; `FE/lib/api/billing-runs.ts:278,288` | retainer types/endpoints | Y | 598C.2 | drop |
| `FE/components/invoices/invoice-line-table.tsx:28` | `type === "RETAINER"` | **N** | 598C.4 | drop the branch |
| `FE/__tests__/retainers/{customer-retainer-tab,close-period-dialog,retainer-detail,retainer-dashboard}.test.tsx` | retainer components | **N** | 598C.4 | delete |
| `FE/components/notifications/notification-preferences-form.tsx:58-65` | `RETAINER_*` labels | **N** | 598C.4 | drop |
| `FE/lib/nav-items.ts:150-155` ("Retainers") | nav | Y* (doc: `:165-170`) | 598C.2 | drop |
| `FE/e2e/tests/admin-audit-30day/fixtures.ts:151` | `/api/retainers` | Y (599C.3 lists the file for capacity) | 598C.3 | drop retainer branch |
| `PO/lib/nav-items.ts:48-55,110`; `PO/lib/api/__tests__/retainer.test.ts` | retainer nav / API test | Y* (doc: `:61-68,123`) | 598D.1 | drop / delete |
| `PO/e2e/tests/responsive/portal-pages.spec.ts:45` | `/retainer` page entry | **N** | 598D.3 | drop entry |

### 3.4 Epic 599 — `capacity`

**599A (backend compile + migration)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/report/ReportService.java:4-5,59,70,79` | `capacity.{ResourceAllocation,ResourceAllocationRepository}` | Y | 599A.3 | drop |
| `$B/report/ReportService.java:17,23,152-227` | `getUtilization` + `ReportController.{MemberUtilizationRecord,UtilizationResponse}` | Y* (doc: `:152-165`) | 599A.3 | **D3 ACCEPTED (keep)** (§5 row D3): the method uses only `ReportRepository.getMemberUtilization` (time entries), not `capacity` |
| `$B/report/ReportService.java:87-150,235-334` (`includeProjections` branches at `:108`, `:142`, `:334`) and `:446-537` (`computeProjectProjections`, `computeCustomerProjections`, `computeOrgProjections`, `aggregateProjections` over `ResourceAllocation`) | allocation-driven profitability **projections** (compile-critical: they use the deleted repository) | **N** | 599A.5 | delete the projection path; `includeProjections` params at `ReportController.java:32,45,71` and the `ProjectionData projections` component in `ReportController.java:81-108,138` go with it |
| `$B/report/ReportController.java:53-62,113-123` | `GET /api/reports/utilization` + records | Y | 599A.1 | per D3 |
| `$B/report/ReportRepository.java:238-306,475-490`; `$B/report/MemberUtilizationProjection.java` | utilization queries | **N** | 599A.5 | per D3 (delete only if the endpoint goes) |
| `$T/report/UtilizationReportTest.java` (`GET /api/reports/utilization` at `:195-267`) | endpoint test | **N** | 599A.5 | per D3 |
| `$B/settings/OrgSettings.java:173-180,371-374` | `@Embedded CapacitySettings` + `getCapacity()` | Y* (doc: `:176-180`) | 599A.3 | drop |
| `$B/settings/OrgSettingsService.java:49,137,347-349,883-921`; `OrgSettingsController.java:10,151-157`; `dto/SettingsResponse.java:34` | `defaultWeeklyCapacityHours` / `PATCH /api/settings/capacity` | Y | 599A.3 | drop |
| `$T/settings/OrgSettingsEmbeddableNullReloadTest.java:88-90,150`; `OrgSettingsSchemaSnapshotTest.java:87` | capacity group / column pin | Y | 599A.3 | drop (pin −1, same PR) |
| `$B/notification/NotificationEventHandler.java` | `MemberOverAllocatedEvent` listener — **does not exist** (the event is published at `capacity/ResourceAllocationService.java:374` and consumed nowhere outside `capacity/`) | Y* | 599A.3 | **no-op**: remove the item |

**599B (registry, capability, notifications, docs)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/verticals/VerticalModuleRegistry.java:109-122` | `resource_planning` | Y* (doc: `:147-159`) | 599B.1 | drop |
| `$B/orgrole/Capability.java:14` | `RESOURCE_PLANNING` | Y | 599B.1 | drop |
| `$B/notification/NotificationService.java:198-200` | `ALLOCATION_CHANGED`, `MEMBER_OVER_ALLOCATED`, `LEAVE_CREATED` | Y | 599B.2 | drop |
| `$B/reporting/StandardReportPackSeeder.java` | utilization report definition — **none** (`grep -i utiliz $B/reporting/` → no match) | Y* (Open Q #7) | 599B.2 | **no-op** |
| `backend/CLAUDE.md:159` | `getCapacity()` in the 10-embeddable list | Y | 599B.2 | edit → 9 |

**599C (frontend)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `FE/app/(app)/org/[slug]/dashboard/page.tsx:15,28` | `team-utilization-widget`, `@/lib/api/capacity` | Y | 599C.2 | drop |
| `FE/components/dashboard/metrics-strip.tsx:10` (+ `resource_planning` gating) | `@/lib/api/capacity` | Y | 599C.2 | drop |
| `FE/app/(app)/org/[slug]/projects/[id]/page.tsx:74,81` | `@/components/capacity`, `@/lib/api/capacity` | Y | 599C.2 | drop |
| `FE/lib/nav-items.ts:301-317` ("Resources", "Utilization"; `RESOURCE_PLANNING` `:306,314`), `:467-471` (SETTINGS "Capacity") | nav | Y* (doc: `:316-331`, `:482-486`) | 599C.3 | drop |
| `FE/lib/capabilities.tsx:15,60` | `RESOURCE_PLANNING` | Y | 599C.2 | drop |
| `FE/app/(app)/org/[slug]/profitability/page.tsx:44`, `profitability/actions.ts:28` | `GET /api/reports/utilization` | **N** | 599C.5 | per D3 |
| `FE/app/(app)/org/[slug]/profitability/actions.ts:43,55`; `FE/components/profitability/profitability-content.tsx:24-72` | "Include Projections" toggle (`includeProjections`) | **N** | 599C.5 | remove the toggle (its backend goes in 599A) |
| `FE/__tests__/dashboard/{company-dashboard:33, metrics-strip:8}.test.tsx` | `@/lib/api/capacity`, `resource_planning` | **N** | 599C.5 | edit |
| `FE/__tests__/{module-gating,capability-gating,settings-features}.test.tsx`; `FE/components/__tests__/settings-sidebar.test.tsx` | `resource_planning` module | **N** | 599C.5 | drop cases |
| `FE/components/__tests__/{mobile-sidebar,desktop-sidebar,command-palette-dialog}.test.tsx`; `FE/__tests__/capabilities.test.tsx`; `FE/__tests__/team/member-role-management.test.tsx` | `RESOURCE_PLANNING` capability | **N** | 599C.5 | drop cases |
| `FE/components/notifications/notification-preferences-form.tsx:69-71` | capacity notification labels | **N** | 599C.5 | drop |
| `FE/__tests__/capacity/*` | capacity components | Y (`components/capacity/**` deleted) | 599C.1 | delete (tests dir not named; add) |

### 3.5 Epic 600 — `schedule` (time reminder re-homed)

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/timeentry/TimeEntryRepository.java:474` | javadoc "Used by TimeReminderScheduler" (no import, no query reference) | Y (Open Q #10) | 600A.1 | none (same simple name after the move) |
| `$B/projecttemplate/PeriodCalculator.java:3,40-66` (`recalculateNextExecutionOnResume` `:47,51`; `calculateNextPeriod` `:12` stays) | `schedule.RecurringSchedule` | Y* (doc: `:41-65`) | 600A.4 | drop |
| `$B/projecttemplate/ProjectTemplateService.java:44,83,108,131,285` | `RecurringScheduleRepository` delete guard | Y | 600A.4 | drop |
| `$B/projecttemplate/ProjectTemplateService.java:683` `instantiateFromTemplate(…)` | sole main caller `schedule/RecurringScheduleService.java:527`; test caller `$T/projecttemplate/PortalProjectSyncFromTemplateIntegrationTest.java:309` | **N** | — | becomes dead code in a surviving package. Per §11.1 Out of scope: **report in the 606C closure, do not prune** |
| `$B/notification/NotificationEventHandler.java:28-30,325-376` | `onRecurringProjectCreated`/`onScheduleSkipped`/`onScheduleCompleted` | Y | 600A.4 | drop |
| `$B/notification/NotificationService.java:26-28,175-177,207,677-774` (`RECURRING_SCHEDULE` refs `:734,765`) | schedule notification types + handlers | Y | 600B.1 | drop |
| `$B/notification/channel/EmailNotificationChannel.java:214-215,299-311` | schedule emails | Y | 600B.1 | drop |
| `$B/provisioning/TenantProvisioningService.java:22,83,107,129,246`; `PackReconciliationRunner.java:17,56,75,92,150` | `SchedulePackSeeder` | Y* (doc ranges) | 600A.4 | drop |
| `$B/settings/OrgSettingsService.java:18,65,79,91,1072` | `SchedulePackSeeder` bean, re-seeded on vertical-profile change (`:1065-1072`) | **N** | 600A.6 | drop (compile-critical) |
| `$B/settings/PackStatusSettings.java:68,272-300`; `OrgSettings.java:93-94` | `schedule_pack_status` | Y | 600A.4 | drop; snapshot pin `:110` −1 |
| `$T/provisioning/PackReconciliationRunnerTest.java:12-13,39,108,130` | `RecurringScheduleRepository`, `SchedulePackSeeder.SEEDER_CREATED_BY` | Y | 600A.4 | drop |
| `$T/provisioning/TenantProvisioningServiceTest.java:33,74,303,330` | `@Mock SchedulePackSeeder` + verifies | **N** (only `PackReconciliationRunnerTest` listed) | 600A.6 | drop |
| `$T/infrastructure/jobqueue/SchedulerMigrationBatch2ATest.java:69,97,114`; `SchedulerMigrationBatch2BTest.java:97` | job type `"recurring_schedule_execute"` (`schedule/RecurringScheduleHandler:30`) asserted registered | **N** | 600A.6 | remove from expected sets / `job1` enqueue. `time_reminder_check` (re-homed handler) **stays** in Batch 1 |
| `$T/notification/template/EmailTemplateRenderingIntegrationTest.java:145-160,243` | renders `notification-schedule` (deleted in 600A.2) | **N** | 600A.6 | delete method + list entry |
| `$T/projecttemplate/{PeriodCalculatorTest:6, ProjectTemplateControllerTest:25-26, ProjectTemplatePrerequisiteTest:20-22, ProjectTemplateServiceTest:23-24, V30MigrationTest:13-16}` | schedule fixtures | Y | 600A.2/600A.4 | as listed |
| `$R/db/migration/tenant/V81__regulatory_deadlines.sql:42-43` | `ALTER TABLE recurring_schedules ADD COLUMN post_create_actions` — the column lives on **`recurring_schedules`**, not `project_templates` (`ProjectTemplate.java` has no `postCreate*`) | Y* (Open Q #8 wrong table) | 600B.2 | **no-op**: the column goes with `DROP TABLE recurring_schedules` (600A.3) |
| `FE/__tests__/lzkc-031-terminology-residuals.test.tsx:5-6` | `@/components/schedules`, `@/lib/api/schedules` | **N** | 600C.3 | drop the schedule cases |
| `FE/components/notifications/notification-preferences-form.tsx:54-56,77` | schedule labels + `POST_CREATE_ACTION_FAILED` | **N** | 600C.3 | drop |
| `FE/lib/nav-items.ts:119-124` ("Recurring Schedules") | nav | Y* (doc: `:134-139`) | 600C.1 | drop |

### 3.6 Epic 601 — `assistant` (provider re-homed)

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/integration/IntegrationService.java:3-4,38,46,253` | `assistant.provider.{LlmChatProviderRegistry,ModelInfo}` | Y* (doc: `:249-253`; method at `:253`) | 601A.1 | import rewrite |
| `$B/integration/AiSettingsController.java:3,24-25` | `ModelInfo` | Y | 601A.1 | import rewrite |
| `$B/automation/executor/InvokeAiSpecialistActionExecutor.java:3-7` | `assistant.{invocation.*,specialist.*}` | Y | 601B.1 | deleted |
| `$B/automation/ActionType.java:10` | `INVOKE_AI_SPECIALIST` | Y | 601B.3 | drop |
| `$B/automation/config/ActionConfig.java:3-10` (`permits … InvokeAiSpecialistActionConfig` `:10`) | sealed permits | Y | 601B.3 | drop |
| `$B/automation/config/AutomationConfigDeserializer.java:46` | `InvokeAiSpecialistActionConfig` | Y | 601B.3 | drop |
| `$B/automation/AutomationContext.java:104` | comment | Y | 601B.3 | reword |
| `$B/automation/AutomationPollTriggersHandler.java:180-184`; `$B/project/ProjectRepository.java:50` | javadoc citing `INVOKE_AI_SPECIALIST` / AI-specialist fan-out | **N** | 601B.7 | reword comments (code stays) |
| `$B/audit/AuditEventTypeRegistry.java:127-161,166` | 7 × `ai.specialist.*` + comment | Y | 601B.4 | **D2 ACCEPTED (keep `ai.specialist.failed`; remove the other six)** (§5 row D2): it is still emitted by kept code |
| `$B/integration/ai/skill/AiExecutionPersistenceService.java:118,148,222-225` | emits `ai.specialist.failed` (Phase 72 skill pipeline, **kept**) | **N** | 601B.7 | per D2 |
| `$T/integration/ai/skill/AiSkillExecutionServiceTest.java:280-371` | counts `ai.specialist.failed` audit rows | **N** | 601B.7 | unaffected if the event keeps being emitted; re-check per D2 |
| `$T/architecture/TenantScopeBindingTest.java:132` | `AssistantController` exemption | Y | 601B.3 | drop |
| `$T/automation/AutomationSchedulerScheduledTriggerIntegrationTest.java:6-8` | `OutputApplier`, `InboxSummaryPayload`, `NonInteractiveSpecialistRunner` | Y | 601B.3 | re-fixture |
| `$T/automation/{AiSpecialistAutomationPackValidityTest:7, executor/InvokeAiSpecialistActionExecutorIntegrationTest:6-11}` | specialist types | Y | 601B.1 | delete |
| `$T/informationrequest/InformationRequestAiAutomationIsolationIntegrationTest.java:35,74,106-115` | asserts a seeded `INVOKE_AI_SPECIALIST` rule on `INFORMATION_REQUEST_COMPLETED` (OBS-505 guard) | **N** | 601B.7 | the premise (seeded specialist rule) disappears. Re-fixture on a surviving action type if the cross-package cascade guard still applies, otherwise delete it with a PR note. 601B builder decides and records |
| `$T/infrastructure/jobqueue/SchedulerMigrationBatch2BTest.java:74,110,145` | job type `"ai_invocation_expiry"` (`assistant/invocation/AiInvocationExpiryHandler:30`) | **N** | 601B.7 | remove from expected set / `job6` |
| `FE/app/(app)/org/[slug]/layout.tsx:20-22` | `@/components/assistant` | Y | 601C.1 | drop |
| `FE/components/settings/settings-sidebar.tsx:10` | `@/lib/api/assistant-specialists` | Y | 601C.2 | drop |
| `FE/components/settings/settings-nav-groups.ts:52` | `pendingCountEndpoint: /api/assistant/invocations…` | **N** | 601C.4 | drop the AI-queue entry |
| `FE/lib/api/automations.ts:42` | `"INVOKE_AI_SPECIALIST"` in `ActionType` union | Y | 601C.2 | drop |
| `FE/components/automations/action-form.tsx:379`, `action-list.tsx:55,65,75` | `INVOKE_AI_SPECIALIST` | **N** | 601C.4 | drop (typecheck; files are deleted later in 604D) |
| `FE/__tests__/assistant/{assistant-provider,message-components}.test.tsx` | `@/components/assistant` | **N** | 601C.4 | delete |
| `FE/lib/nav-items.ts:491-497` (SETTINGS "AI Review Queue") | nav | Y* (doc: `:506-512`) | 601C.2 | drop |
| `FE/lib/capabilities.tsx:17,70` | `AI_ASSISTANT_USE` | Y | 601C.2 | drop |
| launcher sites `customers/[id]/page.tsx:76`, `projects/[id]/page.tsx:57-59`, `invoices/[id]/page.tsx:32`, `information-requests/[id]/page.tsx:7-8`, `components/comments/comment-item.tsx:9`, `components/customers/create-customer-dialog.tsx:27-28`, `components/invoices/invoice-detail-client.tsx:24-25`, `components/invoices/invoice-generation-dialog.tsx:28-29` | `@/components/assistant` | Y | 601D.1 | drop |

`mcp/` imports nothing from `assistant` or `automation` (re-confirmed: no `assistant.` FQN under `$B/mcp/`). Outside `assistant/`, `integration/ai` touches only the `provider` sub-package (the skill pipeline's `ai.specialist.failed` string is the exception, see D2).

### 3.7 Epic 602 — PayFast platform subscription path

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/billing/SubscriptionService.java:3` (+ `initiateSubscribe`, `cancelSubscription`) | `billing.payfast.PlatformPayFastService` | Y | 602A.3 | drop |
| `$B/billing/SubscriptionItnService.java:3,123-124` | `PayFastBillingProperties`, `BillingMethod.PAYFAST` | Y | 602A.1 | deleted |
| `$B/billing/BillingController.java:28-44` | subscribe/cancel/payments | Y | 602A.3 | drop |
| `$B/security/SecurityConfig.java:142-143` | `.requestMatchers("/api/webhooks/subscription").permitAll()` | Y* (doc: `:140-141`, which is the **kept** `/api/webhooks/payment/**`) | 602A.3 | drop only `:142-143` |
| `$B/billing/BillingResponse.java:44` | `sub.getNextBillingAt()` (column dropped by V28) | **N** | 602A.5 | drop `nextBillingAt` from the response (+ `FE/lib/types/settings.ts`) |
| `$B/billing/BillingResponse.java:46,69` | `props.currency()` (breakdown removes `currency` from `BillingProperties`) | **N** | 602A.5 | either keep `BillingProperties.currency`/`monthlyPriceCents` (display only) or drop both together with `FE/app/(app)/org/[slug]/settings/billing/page.tsx:92` `formatAmount(billing.monthlyAmountCents, billing.currency)`. **Recommendation:** keep both (they describe the manual price, not PayFast) and trim only `itemName/notifyUrl/returnUrl/cancelUrl` |
| `$B/billing/Subscription.java:67-70,175-180,237-242` | `monthlyAmountCents`, `currency` field + accessors (no caller outside the class; `PaymentResponse:19` reads `SubscriptionPayment.getCurrency()`, deleted) | Y (H9) | 602A.3 | drop; V28 drops `currency` too |
| `$B/billing/BillingMethod.java:8,17-27`; `BillingResponse.java:19,35-36,51`; `SubscriptionExpiryJob.java:85`; `AdminBillingService.java:130-134` | `PAYFAST`, `isAdminManaged`, flags | Y | 602B.1 | as listed |
| `$T/billing/SubscriptionEntityTest.java:28,54-81` | `SubscriptionPayment`, `SubscriptionPaymentRepository` | **N** | 602A.5 | delete the payment cases (compile) |
| `$T/billing/BillingMethodTest.java:31-47` (+ PAYFAST cases) | `isAdminManaged()`, `PAYFAST` | **N** | 602B.4 | rewrite to the new truth table |
| `$T/billing/SubscriptionExpiryJobBillingMethodTest.java:70` | `BillingMethod.PAYFAST` | **N** | 602B.4 | re-fixture on `MANUAL` (compile) |
| `$T/demo/DemoCleanupServiceTest.java:112` | `BillingMethod.PAYFAST` | **N** | 602B.4 | re-fixture (compile) |
| `$T/demo/DemoReseedTest.java:195-199` | `BillingMethod.PAYFAST` | **N** | 602B.4 | re-fixture (compile) |
| `$T/billing/BillingControllerIntegrationTest.java:94-130` | subscribe/cancel/payments | Y | 602A.3 | reduce |
| `$T/billing/SubscriptionGuardFilterTest.java:78,96,163` | `POST /api/billing/subscribe` used as a write probe | **N** | 602B.4 | still passes (404 ≠ guard 403), but retarget to a kept POST so the test keeps proving "guard lets writes through" |
| `FE/app/(app)/org/[slug]/settings/billing/page.tsx:16,18,21,24,92` | subscribe-button, payment-history, payfast-result-poller, `billing-utils`, `monthlyAmountCents/currency` | Y | 602C.2 | drop the PayFast imports; `billing-utils` **stays** (H2) |
| `FE/__tests__/demo-cleanup.test.tsx:5` | `@/components/billing/subscribe-button` | **N** | 602C.4 | drop the import/case |
| `FE/components/billing/{billing-detail-sheet,billing-tenants-table}.tsx` | `PAYFAST` option/label (platform-admin) | **N** | 602C.4 | drop |
| `FE/app/(app)/org/[slug]/settings/billing/billing-page.test.tsx`; `FE/components/billing/subscription-banner.test.tsx`; `FE/lib/__tests__/subscription-context.test.tsx`; `FE/__tests__/billing-admin.test.tsx` | `PAYFAST` fixtures | **N** | 602C.4 | drop/re-fixture |

### 3.8 Epic 603 — dev harness + docs site

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/portal/CustomerAuthFilter.java:35-39,42,47,57,132-136` | `DEV_PORTAL_PROFILES`, `devPortalEnabled`, `/portal/dev/` skip | Y* | 603A.1 | **keep the constant and the skip**: `integration/payment/MockPaymentGateway.java:28,81` redirects to `/portal/dev/mock-payment` (kept `MockPaymentController`). Reword the javadoc only (H18) |
| `$T/portal/CustomerAuthFilterDevPortalGateTest.java:40,47,55` | `/portal/dev/generate-link` (harness) + `/portal/dev/mock-payment` | **N** | 603A.3 | retarget the `generate-link` assertions to `mock-payment`; keep the profile-gate assertions |
| `$T/security/SecurityIntegrationTest.java:84-93` | javadoc + `GET /portal/dev/generate-link` → 401 | **N** | 603A.3 | still passes (filter rejects without token in `test` profile); reword javadoc |
| `$T/architecture/LayerDependencyRulesTest.java:23` | `.doNotHaveSimpleName("DevPortalController")` | **N** | 603A.3 | drop the exemption |
| `$T/architecture/TenantScopeBindingTest.java:107,128` | `..dev..` exemption | Y | 603A.1 | drop |
| `$B/security/SecurityConfig.java:111-112` | comment | Y | 603A.1 | reword |
| `FE/lib/nav-items.ts:34,361-366` (Help item; `docsLink("")` at `:363`) | `docsLink` | Y* (doc: `:376-381`) | 603C.1 | drop |
| 16 `docsLink` call-site files (H6) | `@/lib/docs` | Y | 603C.1 | as listed |

### 3.9 Epic 604 — single profile, `deadline/`, automation builder

**604A (`deadline/`, registry, builder endpoints)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/project/ProjectUpcomingDeadlinesService.java:3-5,27,47,55,61,67,82` | `DeadlineCalculationService` | Y | 604A.2 | court dates only |
| `$T/project/ProjectUpcomingDeadlinesServiceTest.java:9-11,46` | mock | Y | 604A.2 | rewrite |
| `$T/project/ProjectUpcomingDeadlinesIntegrationTest.java:82,90,165-170` | enables `regulatory_deadlines`, asserts 3 `REGULATORY` rows | **N** | 604A.5 | rewrite to court-date rows only |
| `$B/verticals/VerticalModuleRegistry.java:85-95` (`regulatory_deadlines`), `:135-146` (`automation_builder`) | modules | Y* (doc: `:123-132`, `:173-183`) | 604A.3 | drop |
| `$T/portal/PortalContextControllerIntegrationTest.java:108-162` | provisions `accounting-za`, pins `regulatory_deadlines`, asserts `tenantProfile` = `accounting-za` | **N** | 604A.5 | rewrite on `legal-za` (also see 598B.6 row) |
| `$T/verticals/VerticalProfileIntegrationTest.java:86,220-232` | `GET /api/deadlines` on an accounting tenant | Y (`:75,87` listed) | 604B.3 | covered by the rewrite; note `:220-232` |
| `FE/app/(app)/org/[slug]/dashboard/page.tsx:13,205-207`; `FE/components/dashboard/deadline-widget.tsx` (imports `deadlines/actions` `:8`) | regulatory-deadline dashboard widget | **N** | 604D.4 | delete the widget + its mount |
| `FE/__tests__/dashboard/deadline-widget.test.tsx`; `FE/__tests__/deadlines/{deadlines-page,filing-status}.test.tsx` | deadlines UI | **N** | 604D.4 | delete |
| `FE/lib/nav-items.ts:162-169` ("Deadlines", `regulatory_deadlines` `:168`) | nav | Y* (doc: `:177-184`) | 604D.1 | drop |
| `FE/lib/deadline-utils.ts` | sole importers `components/deadlines/{FilingStatusDialog:27,BatchFilingActions:7}` | Y (H1) | 604D.1 | delete |

**604B / 604C (profiles, packs, rate packs, demo)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `$B/provisioning/TenantProvisioningService.java:21,81,105,127,244`; `PackReconciliationRunner.java:16,54,73,90,148` | `RatePackSeeder` | Y | 604C.2 | drop |
| `$B/settings/OrgSettingsService.java:17,64,78,90,1070` | `RatePackSeeder` bean (vertical-profile change re-seed). The `rate_pack_status` **accessors are not here**: they are `PackStatusSettings.java:64-65,243-263` + `OrgSettings.java:90-91` | Y* (wrong file in 604C.2) | 604C.2 | drop bean; drop accessors in `PackStatusSettings`/`OrgSettings` |
| `$T/provisioning/TenantProvisioningServiceTest.java:32,72,302,329,408` | `@Mock RatePackSeeder` + verifies | Y | 604C.2 | drop |
| `$B/verticals/VerticalProfileReconciliationSeeder.java` | — (no rate pack type) | Y* | 604C.2 | **no-op** |
| `$B/portal/PortalSessionContextService.java:106` | `shortProfile.equals("consulting-generic") \|\| shortProfile.isBlank()` | **N** | 604B.5 | drop only the `consulting-generic` disjunct; the `isBlank()` guard stays |
| `$B/automation/template/AutomationTemplateDefinition.java:15` | javadoc cites `consulting-za.json` | **N** | — (comment-only; 604C.3 sweep optional) | reword |
| `$T/demo/seed/DemoDataSeederIntegrationTest.java:46-47,68-69,313-318` | `GenericDemoDataSeeder`, `AccountingDemoDataSeeder`, `accounting-za` dispatch | **N** | 604B.5 | drop the non-legal cases (compile) |
| `$T/demo/DemoReseedTest.java:12,41,60` | `GenericDemoDataSeeder` | **N** | 604B.5 | re-fixture on the legal seeder (compile) |
| `$T/provisioning/ProvisioningIntegrationTest.java:88`; `$T/seeder/LegalPackSeederIntegrationTest.java:51`; `$T/verticals/legal/tariff/LegalTariffSeederIntegrationTest.java:43`; `$T/compliance/FicaKycZaPackTest.java:42` | provision an `accounting-za` tenant (usually as the "other vertical must not receive legal packs" control) | **N** | 604B.5 | keep the isolation intent: re-fixture the control tenant on `null`/no profile (never delete a guard test) |
| `$T/packs/ConveyancingPackInstallTest.java:112,120`; `$T/packs/ConveyancingRequestPackTest.java:27,69,77,136,146` | `accounting-za`/`consulting-za` control tenants | **N** | 604B.5 | same |
| `$T/verticals/VerticalProfileControllerTest.java:165-173` | switch to `consulting-generic` | **N** | 604B.5 | rewrite (unknown profile → 400) |
| `$T/accessrequest/AccessRequestApprovalServiceTest.java:153-156` | CSV rows `Accounting, accounting-za`, `Marketing/Consulting, consulting-za` | **N** | 604B.5 | → `legal-za` |
| `$T/customerbackend/controller/PortalTrustControllerIntegrationTest.java:42,224` | comments citing accounting-za | **N** | — | comment-only |
| `$T/prerequisite/PrerequisiteControllerTest.java:516`; `$T/invoice/InvoiceSentDocumentPersistenceIntegrationTest.java:176` | comments | **N** | — | comment-only |
| `$R/compliance-packs/generic-onboarding/` | consulting-za only in profiles, but pinned by tests | Y* (H10) | 604B.1 | **D1 ACCEPTED (keep)** (§5 row D1) — removed from the 604B.1 delete list |

**604D–604F (frontend / portal)**

| Caller path:line | Symbol referenced | §11.3? | Owning slice | Action |
|---|---|---|---|---|
| `FE/lib/nav-items.ts:484-490` ("Automations", `requiredModule: "automation_builder"` `:489`) and `:496` (AI Review Queue entry's `automation_builder`, already gone in 601C) | `automation_builder` | Y* (doc: `:499-505`) | 604D.2 | drop `requiredModule` |
| `FE/app/(app)/org/[slug]/settings/automations/{page,executions/page}.tsx` | `automation_builder` gate | Y | 604D.2 | drop gate (always-on admin page) |
| `FE/components/settings/settings-nav-groups.ts` (2 × `automation_builder`); `FE/components/dashboard/admin-stats-column.tsx` | `automation_builder` gating | **N** | 604D.4 | drop |
| `FE/__tests__/{module-gating,capability-gating,settings-features}.test.tsx`; `FE/components/__tests__/settings-sidebar.test.tsx`; `FE/__tests__/dashboard/company-dashboard.test.tsx` | `automation_builder` cases | **N** | 604D.4 | drop cases |
| `FE/__tests__/dashboard/deadline-widget.test.tsx:100-110` | `regulatory_deadlines` | **N** | 604D.4 | delete (see 604A) |
| `PO/lib/nav-items.ts:56-63` (deadlines `profiles: ["accounting-za","legal-za"]` `:61`) | profile list | Y* (doc: `:69-76`) | 604F.1 | → `["legal-za"]` |

---

## 4. Enum-string row-rewrite list (595B.2)

`grep -rn "@Enumerated(EnumType.STRING)" backend/src/main/java --include="*.java" | wc -l` → **75** fields. The ones whose enum loses a constant in 596–604 are below. Migration-seeded grants were confirmed with:

```
$ grep -l org_role_capabilities $R/db/migration/tenant/*.sql | xargs grep -ln 'RESOURCE_PLANNING\|VIEW_DEALS\|MANAGE_DEALS\|CLOSE_DEALS\|MANAGE_PIPELINE\|AI_ASSISTANT_USE'
backend/src/main/resources/db/migration/tenant/V67__create_org_role_tables.sql
$ grep -n "RESOURCE_PLANNING\|VIEW_DEALS\|MANAGE_DEALS\|CLOSE_DEALS\|MANAGE_PIPELINE\|AI_ASSISTANT_USE" $R/db/migration/tenant/*.sql
…/V67__create_org_role_tables.sql:57:    'TEAM_OVERSIGHT', 'CUSTOMER_MANAGEMENT', 'AUTOMATIONS', 'RESOURCE_PLANNING'
…/V67__create_org_role_tables.sql:66:    'TEAM_OVERSIGHT', 'CUSTOMER_MANAGEMENT', 'AUTOMATIONS', 'RESOURCE_PLANNING'
```

→ §11.9 is confirmed: **only `RESOURCE_PLANNING` is migration-seeded** (V67:57 owner, :66 admin). V122 seeds only `AI_MANAGE`/`AI_EXECUTE` (`:103,113,123`).

| Enum.CONSTANT | Entity field (file:line) | table.column | Migration-seeded rows? | DML the owning migration must carry | Owning slice / migration |
|---|---|---|---|---|---|
| `Capability.RESOURCE_PLANNING` | `$B/orgrole/OrgRole.java:41-45` (`@ElementCollection` + `@Enumerated(STRING) Set<Capability>`) | `org_role_capabilities.capability` | **YES** — V67:57,66 | `DELETE FROM org_role_capabilities WHERE capability = 'RESOURCE_PLANNING';` (**mandatory**; without it every owner/admin role load fails in `MemberFilter`) | 599A / nominal V140 |
| `Capability.VIEW_DEALS`, `MANAGE_DEALS`, `CLOSE_DEALS`, `MANAGE_PIPELINE` | same | same | NO (only role-editor grants on dev volumes) | defensive: `DELETE FROM org_role_capabilities WHERE capability IN ('VIEW_DEALS','MANAGE_DEALS','CLOSE_DEALS','MANAGE_PIPELINE');` (recommended: a dev volume with a custom role that carries one would otherwise break that role's load) | 596A / V136 |
| `Capability.AI_ASSISTANT_USE` | same | same | NO | defensive: `DELETE FROM org_role_capabilities WHERE capability = 'AI_ASSISTANT_USE';` | 601B / nominal V142 |
| `EntityType.DEAL` (**new**, §3.1) | `$B/fielddefinition/FieldDefinition.java:28-30`, `FieldGroup.java:25-27` | `field_definitions.entity_type`, `field_groups.entity_type` (+ `field_group_members` rows of those groups, FK-cascaded — builder confirms) | NO (no pack seeds `DEAL`; user-created only) | `DELETE FROM field_definitions WHERE entity_type = 'DEAL'; DELETE FROM field_groups WHERE entity_type = 'DEAL';` (builder checks FK order against `field_group_members`). Free-string planes (`entity_tags.entity_type = 'DEAL'`, `saved_views.entity_type = 'DEAL'`) are not enum-mapped and need no rewrite; optional cleanup `DELETE … WHERE entity_type='DEAL'` | 596A / V136 |
| `TriggerType.PROPOSAL_SENT` | `$B/automation/AutomationRule.java:35-37` | `automation_rules.trigger_type` | seeder-written (`automation-templates/legal-za.json:28-49`, `common.json:127-148`, `consulting-za.json:141-173`) | `DELETE FROM automation_rules WHERE trigger_type = 'PROPOSAL_SENT';` — `automation_actions.rule_id` and `automation_executions.rule_id` are `ON DELETE CASCADE` (V60:23,42), and `action_executions.action_id` is `ON DELETE SET NULL` (V60:57), so the breakdown's explicit `DELETE FROM automation_actions …` first is harmless but redundant. **The DML alone is not sufficient:** pack install runs after migrations (`TenantProvisioningService.java:243`) and `AutomationTemplateSeeder.applyPack` (`:89` `template.triggerType()`) re-creates these rows from the JSONs; worse, once the constant is deleted the JSONs fail to bind (`AbstractPackSeeder.java:176-179`), so the three rule objects must leave the JSONs in the same PR (597A.5) | 597A / V137 + 597A.5 JSON edits |
| `PrerequisiteContext.PROPOSAL_SEND` | **not `@Enumerated`**; stored as text in `field_definitions.required_for_contexts` jsonb (`FieldDefinition.java:74-75`); parsed by `CustomerReadinessService.java:222` (unknown → warn+skip) | `field_definitions.required_for_contexts` | V53:18 backfilled `["INVOICE_GENERATION","PROPOSAL_SEND"]` on pre-existing `common-customer` rows (fresh tenants: no match, because packs are seeded after migrations; no pack JSON contains `PROPOSAL_SEND`) | defensive: `UPDATE field_definitions SET required_for_contexts = required_for_contexts - 'PROPOSAL_SEND' WHERE required_for_contexts ? 'PROPOSAL_SEND';` | 597A / V137 |
| `TemplateCategory.PROPOSAL` | `$B/template/DocumentTemplate.java:39-41` | `document_templates.category` + `chk_dt_category` | NO (no pack uses it) | defensive `DELETE FROM document_templates WHERE category = 'PROPOSAL';` **before** the CHECK re-definition (otherwise `ADD CONSTRAINT` fails on such a row) | 597A / V137 |
| `InvoiceLineType.RETAINER` | `$B/invoice/InvoiceLine.java:49-51` | `invoice_lines.line_type` + `invoice_lines_line_type_check` | NO (only billing-run data; V50:63 back-filled historic rows) | `DELETE FROM invoice_lines WHERE line_type = 'RETAINER';` **before** the CHECK re-definition. **Decided: DELETE, not re-type** — ADR-331 zero-tenant squash: per-slice migrations are throwaway and there is no production data; consistent with the other enum-row DML | 598A / V138 |
| `ActionType.INVOKE_AI_SPECIALIST` | `$B/automation/AutomationAction.java:31-33` | `automation_actions.action_type` | seeder-written (`ai-specialist-*.json`) | `DELETE FROM automation_actions WHERE action_type = 'INVOKE_AI_SPECIALIST';` then rules left action-less. The engine tolerates zero actions (`AutomationEventListener.java:180`, `AutomationRuleService.java:331`), so restrict the rule delete to rules whose **only** actions were specialist ones (Open Q #11) | 601B / nominal V142 |
| `BillingMethod.PAYFAST` | `$B/billing/Subscription.java:90-92` | global `subscriptions.billing_method` | YES-ish: global `V18__subscription_billing_method.sql:9-10` back-filled `'PAYFAST'` where `payfast_token IS NOT NULL` | `UPDATE subscriptions SET billing_method = 'MANUAL' WHERE billing_method = 'PAYFAST';` **before** the column drops | 602A / global V28 |
| pack-type strings (`deal_pipeline`, `schedule`, `rate`) | `$B/packs/PackInstall` → table **`pack_install`** (singular, `V94__create_pack_install.sql:10`), `pack_type VARCHAR(64)` (`:13`) mapped from `packs/PackType` = `{DOCUMENT_TEMPLATE, AUTOMATION_TEMPLATE}` only. `DealPipelinePackSeeder:61` `"deal-pipeline"`, `SchedulePackSeeder:65` `"schedule"`, `RatePackSeeder:48` `"rate"` are `getPackTypeName()` **log labels** (`AbstractPackSeeder.java:60`); V95 back-fills only the two `PackType` values | — | — | **none** — `pack_installs` does not exist and the values never occur. Delete the `DELETE FROM pack_installs …` lines from 596A.2, 600A.3, 604C.1. Application status for these packs lives in `org_settings.{deal_pipeline,schedule,rate}_pack_status` jsonb (`PackStatusSettings.java:64-76`), which the drop migrations already remove | 596A / 600A / 604C |

CHECK constraints touched (re-confirmed against the migration files): `chk_dt_category` V102 (with `'PROPOSAL'`, V102:32), `invoice_lines_line_type_check` final V104:11 (with `'RETAINER'`), `chk_email_delivery_reference_type` final V135 (with `PORTAL_RETAINER`, `PORTAL_NEW_PROPOSAL`, `PORTAL_PROPOSAL_EXPIRED`), `chk_comment_entity_type` V37:6 = `('TASK','DOCUMENT','PROJECT')` (no removed entity; unchanged). `proposals_fee_model_check` (V90:22, includes `'RETAINER'`) goes with `DROP TABLE proposals`. V131 adds **no** CHECK for `DEAL` (its widening is commented out). Nothing else is needed.

---

## 5. Resolved hedges (595B.3)

| # | Hedge (source) | Finding (file:line evidence) | Decision / consequence for owning slice |
|---|---|---|---|
| H1 | `FE/lib/deadline-utils.ts` sole consumer? (§11.10 s1; OQ #18) | Only importers: `FE/components/deadlines/FilingStatusDialog.tsx:27`, `BatchFilingActions.tsx:7`. Staff deadline API calls live in `FE/app/(app)/org/[slug]/deadlines/actions.ts`; there is **no** `FE/lib/api/deadlines.ts`. **But** `FE/components/dashboard/deadline-widget.tsx:8` imports `fetchDeadlineSummary` from that route's `actions.ts` and is mounted at `dashboard/page.tsx:13,205-207` | 604D.1 deletes `deadline-utils.ts`, the route (incl. `actions.ts`) **and** the dashboard `DeadlineWidget` + mount + `__tests__/dashboard/deadline-widget.test.tsx` (added, §6) |
| H2 | `FE/lib/billing-utils.ts` sole use? (§11.10 s1; OQ #15) | Importers: `settings/billing/page.tsx:24` (kept), `components/billing/subscription-banner.tsx:7` (kept), `trial-countdown.tsx:5`, `grace-countdown.tsx:4`, `payment-history.tsx:11` (deleted). `formatAmount` is still used at `settings/billing/page.tsx:92` | **Keep** `billing-utils.ts`. 602C.1's "only if sole use" resolves to **no**; the row is corrected (§6) |
| H3 | `StandardReportPackSeeder` defines a utilization report? (§11.10 s1; OQ #7) | `grep -rn -i "utiliz" $B/reporting/` → no match | No. 599B.2 has nothing to remove; `DashboardWithoutCapacityTest` only asserts absence |
| H4 | `project_templates.post_create_actions` readers (§11.10 s1; OQ #8) | `V81__regulatory_deadlines.sql:42-43` = `ALTER TABLE recurring_schedules ADD COLUMN IF NOT EXISTS post_create_actions JSONB;` (section "2. recurring_schedules — post-create actions column"); `grep -n -i postCreate $B/projecttemplate/ProjectTemplate.java` → no match. Readers: `schedule/**`, `seeder/SchedulePack{Definition,Seeder}`, `schedule-packs/accounting-za.json` | Column is on **`recurring_schedules`**. It goes with `DROP TABLE recurring_schedules` (600A.3). 600B.2 is a **no-op**, so no extra throwaway number. Breakdown 600B.2 + OQ #8 corrected (§6) |
| H5 | `rate_pack_status` accessors in `OrgSettingsService` (§11.10 s1) | Accessors are `PackStatusSettings.java:64-65,243-263` (`getRatePackStatus`, `recordRatePackApplication`, `isRatePackApplied`) + `OrgSettings.java:90-91` (`@AttributeOverride`); writer `RatePackSeeder.java:68,71`. `OrgSettingsService.java:17,64,78,90,1070` holds the **`RatePackSeeder` bean** (and `:18,65,79,91,1072` the `SchedulePackSeeder` bean) to re-seed packs on a vertical-profile change (`:1065-1072`) | 604C.2 edits `PackStatusSettings` + `OrgSettings` for the column, and `OrgSettingsService` for the bean. 600A.4 must also drop the `SchedulePackSeeder` bean from `OrgSettingsService` (compile-critical, added §6) |
| H6 | Exact `docsLink` call-site list (§11.10 s1; 603C) | `grep -rln docsLink` over `FE/{app,components,lib,__tests__}` = 18 files = 16 call-site files + `lib/docs.ts` + `__tests__/docs-integration.test.tsx`. Sites (import line, use line): `nav-items.ts:34,363`; `customers/page.tsx:28,297`; `invoices/page.tsx:17,226`; `projects/page.tsx:35,255`; `resources/page.tsx:12,103`; `proposals/page.tsx:6,109`; `reports/page.tsx:9,49`; `components/assistant/empty-state.tsx:3,17`; `help-tip.tsx:6,35`; `tasks/time-entry-list.tsx:8,88`; `tasks/task-list-panel.tsx:9,317`; `information-requests/request-list.tsx:6,22`; `marketing/nav-bar.tsx:3,27`; `marketing/footer.tsx:2,10`; `automations/rule-list.tsx:24,147`; `documents/documents-panel.tsx:20,409` | 16 confirmed (matches 603C.1). The Help item is `nav-items.ts:361-366`, not `:376-381` (corrected, §6) |
| H7 | `PortalContextController` carries a retainer module field? (OQ #6) | File is `$B/portal/PortalContextController.java` (not `customerbackend/`); `grep -n -i retainer` → no match (exit 1). Its integration test `$T/portal/PortalContextControllerIntegrationTest.java:93-99,149` does assert `retainer_agreements` in `enabledModules` | No controller edit; 598A.3 item removed. The **test** is a new 598B.6 seam (§6) |
| H8 | `MockPaymentIntegrationSeeder` users | `integration/payment/MockPaymentIntegrationSeeder` (self), `provisioning/TenantProvisioningService`, `provisioning/PackReconciliationRunner`, `$T/provisioning/TenantProvisioningServiceTest` | **Stays** (invoice pay-now e2e) |
| H9 | `subscriptions.currency` readers after `monthly_amount_cents` goes (OQ #12) | `Subscription.java:69-70,179-180,241-242` only; `BillingResponse.java:46,69` reads `props.currency()` (`BillingProperties`), not the entity; `PaymentResponse.java:19` reads `SubscriptionPayment.getCurrency()` (deleted) | Orphaned → V28 also `DROP COLUMN IF EXISTS currency`; 602A.3 drops field + accessors. **Separate seam:** 602A.3 also lists `BillingProperties.currency` for removal, which would break `BillingResponse.java:46,69` and `settings/billing/page.tsx:92`. Recommend keeping `BillingProperties.{monthlyPriceCents,currency}` (§3.7); 602A.3 amended in place to keep them and to target `SecurityConfig:142-143` |
| H10 / **D1** | `compliance-packs/generic-onboarding/` referenced only by consulting-za? (OQ #19) | Profiles: only `vertical-profiles/consulting-za.json:14`. But pinned by tests: `$T/provisioning/PackReconciliationRunnerTest.java:70`, `$T/compliance/CompliancePackSeederTest.java:67,161,192`, `$T/compliance/CompliancePackControllerTest.java:116-165`, `$T/compliance/ChecklistInstantiationServiceTest.java:84` (comment), `$T/testutil/TestChecklistHelper.java:27` (javadoc). its `pack.json` declares no `verticalProfile` (`jurisdiction: null`, `customerType: ANY`), and `CompliancePackSeeder.getVerticalProfile` (`:98-99`) filters only on that field, so every tenant (legal-za included) gets it today. That is why the tests find it on a default tenant | **ACCEPTED 2026-10-16 (user): keep (Option A).** Option A (recommended): **keep** the pack in 86. It is profile-neutral, harmless, and the five test files use it as the generic fixture for the compliance-pack machinery. Re-evaluate in Phase 87 when the compliance catalogue is curated for legal. Option B: delete it in 604B.1 and re-fixture the 4 tests + helper on `legal-za-individual-onboarding` (adds ~5 test files to 604B). **ACCEPTED 2026-10-16 (user): Option A — keep.** 604B.1 and Open Question #19 updated; no architecture/ADR amendment needed |
| H11 | `POST_CREATE_ACTION_FAILED` ownership (OQ #9) | Only producer `schedule/RecurringScheduleService.java:680`; catalogue `NotificationService.java:207`; frontend label `notification-preferences-form.tsx:77`; test `$T/schedule/RecurringScheduleServiceTest.java:644-651` | Schedule-owned → 600B.1 removes the catalogue entry; 600C.1 removes the label |
| H12 | Acceptance already has a prerequisite context? (OQ #5) | `PrerequisiteContext` = {`LIFECYCLE_ACTIVATION`, `INVOICE_GENERATION`, `PROPOSAL_SEND`, `DOCUMENT_GENERATION`, `PROJECT_CREATION`} (`:5-9`); `grep -rn PrerequisiteContext $B/acceptance/` → no match | No. Nothing is added in 86 (Phase 87+) |
| H13 | `PrerequisiteService:199-230` / `StructuralPrerequisiteCheck` import `proposal/*`? (OQ #4) | `grep '^import' … \| grep -i proposal` → no match. `PROPOSAL_SEND` uses: `PrerequisiteContext.java:7`, `PrerequisiteService.java:199`, `StructuralPrerequisiteCheck.java:54-55,91,110,130` | No. 597B.2 stays in 597B |
| H14 | `TimeEntryRepository` references `TimeReminder*` via javadoc or query? (OQ #10) | `TimeEntryRepository.java:474` javadoc only ("Used by TimeReminderScheduler…"); no import, no query | Comment unchanged (simple name survives the move); 600A.1 row amended in place: no `TimeEntryRepository` edit (also OQ #10, Stage-1 row 1o, Key Files) |
| H15 | `AutomationRule` allows zero actions? (OQ #11, 601B.2 decides) | `AutomationEventListener.java:180` `if (actions.isEmpty())` → skip; `AutomationRuleService.java:331` `conditionsMet && !actions.isEmpty()` | Engine tolerates action-less rules → V142's rule delete may be restricted to rules whose only actions were `INVOKE_AI_SPECIALIST` (601B.2 decides) |
| H16 | `BillingConfig` binds only PayFast properties? (OQ #13 part) | `BillingConfig` = `@Configuration @EnableConfigurationProperties(BillingProperties.class)` (trial/grace days survive) | **Keep** `BillingConfig` (602A.3 "delete if sole purpose" → no) |
| H17 | Staff `proxy.ts` has no `/portal` rewrite; `portal/` owns `/portal`? (OQ #17) | `FE/proxy.ts` exists, `FE/middleware.ts` does not; `grep -n portal FE/proxy.ts` → no match; `compose/caddy/Caddyfile:17-18` `portal-dev.heykazi.com → reverse_proxy portal:3002`; `portal:` services in `compose/docker-compose.yml:215`, `docker-compose.prod.yml:206` | Confirmed; 597C.1 may delete `FE/app/portal/**` |
| H18 | `CustomerAuthFilter` dev-profile constant unused after `/portal/dev/` harness removal? (OQ #16) | `DEV_PORTAL_PROFILES` `:42`, `devPortalEnabled` `:47,57`, skip `:132-136`; javadoc `:35-39` says the set also covers `MockPaymentController`; `integration/payment/MockPaymentGateway.java:28,81` redirects to `/portal/dev/mock-payment`; `$T/portal/CustomerAuthFilterDevPortalGateTest.java:45-47` pins it | **Not unused → keep** the constant and the skip; reword javadoc only. 603A.1 corrected (§6) |
| H19 | Hook resolves `HEAD_BRANCH` early enough? (OQ #28) | `HEAD_BRANCH` resolved at `pre-pr-merge-gate.sh:53`, empty-check `:54-57`; docs-only early allow at `:70-73` | Inserted after `:57`, before `CHANGED_FILES`, so docs-only `phase86/*` PRs are checked too. Tested (PR body). Pre-existing: the `$1` guard at `:25-29` exits before stdin is read when the harness passes no argv (`.claude/settings.json` passes none). Unchanged and out of scope, flagged to the orchestrator |
| OQ1 | Consumers outside §11.3? | Yes: see §3 (`N` rows) and §6 | STOP rule applied: rows added before 596A starts |
| OQ2 | `pack_installs` column/values | Table `pack_install`; `pack_type` ∈ {`DOCUMENT_TEMPLATE`,`AUTOMATION_TEMPLATE`} | Drop the three `DELETE FROM pack_installs` lines (§4) |
| OQ3 | Deal/AI capabilities migration-seeded? | No (§4 grep) | Defensive deletes only |
| §11.3.11 r1 | `prerequisite/*`, `TemplateCategory` enum values | Re-confirmed: `PrerequisiteContext.java:7`, `TemplateCategory.java:9` (`ENGAGEMENT_LETTER` `:4`) | As architected |
| §11.3.11 r2 | `chk_comment_entity_type` | `V37__portal_comments_support.sql:6` → `('TASK','DOCUMENT','PROJECT')` | Unchanged |
| §11.3.11 r3 | Rate packs — any legal use? | `$R/rate-packs/` = `accounting-za.json`, `consulting-generic.json`, `consulting-za.json` only | Delete all three (604C.1) |
| §11.3.11 r5 | Migration-vs-seeder seed rows | Build-time at 605B.1 (provision with seeders disabled and diff) | Unchanged — slice 11 |
| §11.3.11 r6 | Legacy `legal-za-client-onboarding` re-activation | `$R/compliance-packs/` = `fica-kyc-za, generic-onboarding, legal-za-individual-onboarding, legal-za-trust-onboarding, sa-fica-company, sa-fica-individual`; the slug appears only in `V92`/`V93` and `compliance/ChecklistInstantiationService` | No re-activation risk; the guard removal stays optional (604C.3) |
| §11.3.11 r9 | `PortalActivityEventTypes` proposal/retainer constants | No Java constants, **but** the allowlist holds six `"proposal.*"` strings (`:28-34`) and `PortalActivityEventResponse.java:92-97` maps them | Correction: 597B.6 removes them and re-fixtures `PortalActivityServiceFirmFilterTest:253-295` (§6) |
| **D2** | `ai.specialist.*` registry entries removable (601B.4, count → 28)? | Kept `integration/ai/skill/AiExecutionPersistenceService.java:222-225` emits `"ai.specialist.failed"` (WARNING, `AuditEventTypeRegistry.java:144-147`); `AiSkillExecutionServiceTest:280-371` counts those rows. Removing the entry silently downgrades the event to the resolver default (INFO/STANDARD) | **ACCEPTED 2026-10-16 (user): keep** the `ai.specialist.failed` entry (catalogue 35 → **29**, not 28) and remove the other six. Audit event-type strings are untouched in this phase (ideation "Key design preferences"); renaming it to `ai.execution.failed` belongs to Phase 87. **Carried by 601B.4:** amend ADR-333 catalogue 28 → 29 (`adr/ADR-333-mcp-sole-conversational-ai-surface.md:15,52`), plus breakdown 601B.4 and the `AuditEventTypeRegistry` hotspot row (…→28 becomes …→29) |
| **D3** | Utilization endpoint removal (599A.1 / §11.3.4) | `ReportService.getUtilization` (`:152-227`) reads only `ReportRepository.getMemberUtilization` (time entries, `:238-306`). It has **no** capacity dependency, and it feeds the **kept** profitability page (`FE/app/(app)/org/[slug]/profitability/page.tsx:44`, `actions.ts:28`). The capacity dependency in `ReportService` is the projections path (`:446-537`), which must go regardless | **ACCEPTED 2026-10-16 (user): keep** `GET /api/reports/utilization` (+ `ReportRepository` queries, `MemberUtilizationProjection`, `UtilizationReportTest`) and delete only the allocation-driven projections + the "Include Projections" toggle. If the endpoint must go, 599C must also trim the profitability page's utilization section. **Carried by 599A.1/599A.3:** amend architecture §11.3.4 (`architecture/phase86-subtraction-legal-only.md:268` Deleted row and `:382` endpoint row) to keep the utilization endpoint |

---

## 6. Additions to the breakdown (STOP rule, §11.5.3)

`tasks/phase86-subtraction-legal-only.md` is **authoritative** for every task row; this section is only an index of which rows 595B touched (each marked `(595B)` in the breakdown) so they can be found. The §3 tables give the evidence per seam. Point-in-time as of the 595 PR; later slices may re-amend rows without updating this index.

- **Rows added:** 596A.5, 596B.5, 596C.5, 597A.5, 597B.6, 597C.5, 597D.3, 598A.5, 598B.6, 598C.4, 598D.3, 599A.5, 599C.5, 600A.6, 600C.3, 601B.7, 601C.4, 602A.5, 602B.4, 602C.4, 603A.3, 604A.5, 604B.5, 604D.4.
- **Rows amended in place (Notes or Files cell):** 596A.2, 596A.3, 596C.3, 597A.2, 597B.3 (moved to 597A.5), 597B.4, 597C.2, 597C.3, 597D.1, 598A.2, 598A.3, 598B.2, 598B.3, 598C.2, 598D.1, 599A.3, 599B.1, 599B.2, 599C.3, 600A.1, 600A.3, 600B.2, 600C.1, 601C.2, 602A.3, 602C.1, 603A.1, 603C.1, 604A.3, 604C.1, 604C.2, 604D.1, 604D.2, 604F.1. Added rows later re-amended by review: 596B.5, 597A.5, 598B.6.
- **Other breakdown sections amended:** header migration high-water (global V26–V29), the Stage 1 PayFast note (`SecurityConfig:142-143`), Implementation Order rows 1d/1e/1i/1o/1t/1∥a/2a/2d/2f, the "Merge-conflict hotspots and the V-number rule" table (`SecurityConfig` 602A line, global V29 for 598B), slice tables for 597B/598B, the 598B Goal and Key Files, the 600A Goal and Key Files, the 602 Key Files, 605B.2 (`global/V2 … V29`), Open Questions #10 and #25.
- **Deliberately not added:** `generic-onboarding` (**D1**, decision pending, 604B.1 unedited); `ProjectTemplateService.instantiateFromTemplate` dead after 600 (report in 606C, §11.1 out of scope); the five comment-only `N` rows with owner `—` in §3 (picked up by the 606B/606C stale-reference sweep).

Packages with **no** consumer outside §11.3 + breakdown in **main** code: `dev` (0 FQN refs; `CustomerAuthFilter` is listed), `billing/payfast` (2 main refs, both listed), `crm` (4 main refs, all listed — the `EntityType.DEAL` seam is a separate enum, not a `crm` import). Every other package gained at least one row.

---

## Squash record (605C)

Status: **VERIFIED** (observed 2026-10-18; root CLAUDE.md §3).

- **Squash commit:** `d8fb3983c` (PR #1577, squash of `63502bbbc` 605A + `5e583191b` 605B + `cb30d9f15` review doc fix). Series: tenant V1–V144 (133 + 9 Phase 86 drops) → `tenant/V1__baseline.sql`; global V1–V29 (25 + 4) → `global/V1__baseline.sql`.
- **Generation:** throwaway `postgres:16-alpine` (16.14) + `flyway/flyway:11.14.1` on a private docker network; pg_dump round-trip old-series vs V1 = empty diff (tenant 6,188 lines; public+portal 908 lines); seeded rows equal (org_roles 3, org_role_capabilities 47, tax_rates 3, accounting_tax_code_mapping 4, request_counters 1, shedlock 1, shard_config 1). Artefacts `/tmp/p86-squash/` (ephemeral).
- **sha256:** tenant snapshot `e81e6cd9de1e05cb365d5188833565d0c935a5a6a29b6f74c06e281a0f6b3a9b`; global snapshot `faeec9533aa135e7c7de81d372f3a9fce1d2e1a9508f7795382fd42139f91326`; tenant V1 `979b03a8fb07fdfe524fd9cda48eb66c9b4e222f86385a2975a4cf303a409921`; global V1 `9cbac47dabde0a7202563333f01d0d2998fa38a7e4fb175b091d1afd1ec282e5`.
- **Behaviour change (accepted):** old V7/V8/V10/V16/V27/V29/V127 used unscoped catalog guards, so 9 objects (uq_customers_email, chk_customers_customer_type, uq_customer_projects_customer_project, chk_documents_scope_fks, chk_documents_provenance, uq_portal_contacts_email_customer, uq_magic_link_tokens_hash, chk_brand_color_format, tasks.customer_visible) existed only in the first tenant per database. V1 gives every tenant the first-tenant shape.
- **Full verify:** `5e583191b` — 5294 testcases / 0 failures / 0 errors / 26 skipped (`/tmp/mvn-605-final.log`; marker `.claude/markers/verify-backend.json`). First attempt env-only failure (EmbeddedPostgres 10s startup timeout under load ~19 with orphaned embedded-PG processes) — orphans killed, re-run clean. Backend CI pass on `5e583191b` and `cb30d9f15`.
- **E2E stack (605C.1):** wiped + rebuilt from the squash branch; global + tenant `flyway_schema_history` = `1|1|t`; `e2e-test-org` = legal-za.
- **Playwright (mock-auth e2e stack; both configs refuse non-mock auth):** pre-squash baseline on main `a99b11f50` vs post-squash, same procedure:
  - legal-lifecycle: 65 passed / 15 failed / 6 skipped / 6 not run — identical before and after, same failing set.
  - legal-depth-ii: pre 0 / 3 failed / 15 skipped / 4 not run; post 0 / 4 failed / 14 skipped / 4 not run. The only post-only failure (`day-14-conveyancing.spec.ts:55`) was skipped pre-squash; post-squash it created the Property Transfer matter and failed only on a missing first-run screenshot baseline. No green→red → no squash defect.
  - Pre-existing failures (missing screenshot baselines; depth-ii day-05 disbursements `networkidle` 30s timeout blocking dependent tests; lifecycle locator failures) are unrelated to Phase 86 → 606C capstone.
  - Logs `/tmp/pw-605-{pre,post}-{lifecycle,depthii}.log`, artefacts `/tmp/pw-605-{pre,post}-artefacts/` (ephemeral). Gotcha: these configs write into tracked `frontend/test-results/` and `documentation/screenshots/` — restore the tree after running.
- **Dev stack (Keycloak):** volumes `docteams-dev_postgres_data` (48 MB) + `docteams-dev_localstack_data` backed up to the session scratchpad (`dev-volume-backup-pre-phase86-squash/*.tgz`) → `dev-down.sh --clean` → `dev-up.sh` → `keycloak-bootstrap.sh` → `./mvnw clean` (stale old V*.sql in `target/classes` caused "Found more than one migration with version 1") → `svc.sh start all` (all 4 healthy). Smoke: `POST /internal/orgs/provision` legal-za → 201 `tenant_56399d06a773`; global + tenant history `1|1|t`; org_settings legal-za; 10 automation rules; 47 role capabilities; `accounting-za` → 400 with no org row.
- **Still to do outside this repo:** wipe the VPS dev environment's Postgres volume before deploying an image built from `d8fb3983c` (BASELINE.md §6).

## 7. After — Phase 86 closure report (606C.2)

**Date:** 2026-10-18. **Measured on:** `phase86/12-docs-adr-capstone` (PR #1578) at `c6d530190`. The product code there is identical to main `97afbf223`; the 606 diff vs main touches only docs, 3 frontend test mocks and 2 deleted dead e2e specs. Status vocabulary follows root `CLAUDE.md` §3: VERIFIED means the run was observed, and anything not observed is DEFERRED.

### 7.1 Overall status

| Part | Status | Evidence |
|---|---|---|
| Subtraction (Epics 596–604) | **VERIFIED** | Each epic merged on a full backend verify plus a frontend/portal verify (markers in each epic's "Done" commit). A final full verify on the 606 head is in §7.3. |
| Migration squash (Epic 605) | **VERIFIED** | Old-series vs V1 parity proof (empty pg_dump diff), full verify, e2e + dev-stack re-provision smoke. See "Squash record (605C)" above. |
| Capstone (606C.1): full verifies | **VERIFIED** | §7.3 |
| Capstone (606C.1): legal Playwright configs | **DEFERRED, not green.** No failure was caused by Phase 86. | §7.4 |
| Capstone (606C.1): `ai-core-verification-keycloak.md` walk | **DEFERRED, needs the user** | §7.5 |
| **Phase 86 overall** | **Subtraction + squash VERIFIED; capstone PARTIAL** | Phase 86 is not "ALL_GREEN" until the two DEFERRED items are resolved or the user explicitly accepts them as deferred (T18). |

### 7.2 Before / After

Before = §2 (main `37150816e`, 2026-10-16) and architecture §11.1. After = commands from §2 / `.epic-brief.md` R6, re-run on `c6d530190`. "Predicted" = architecture §11.1 "After Phase 86" or the 606C.2 task row.

| Surface | Before | After (observed) | Predicted | Note |
|---|---|---|---|---|
| Backend main `.java` files / lines | 1,628 / 176,320 | **1,375 / 148,468** (−253 / −27,852) | ≈1,330 / ≈146k | **Differs:** 45 more files and ≈2.5k more lines than predicted. The §11.1 figure was explicitly an estimate ("recount at closure"). Kept or re-homed code (time reminders, AI provider, D1–D3 keeps) accounts for the gap. |
| Backend test `.java` files / lines | 796 / 227,117 | **703 / 197,092** (−93 / −30,025) | — | |
| Backend tests | 6,084 "Tests run" (Maven summary, 2026-10-16; 2 pre-existing calendar-rolling failures; PR #1563 date-stable fixtures) | **5,294 `<testcase>` / 0 failures / 0 errors / 26 skipped** | — | **Not like-for-like.** Before is the Maven "Tests run" summary; After counts `<testcase>` elements (the Maven `@Nested` aggregate is unreliable). Last like-for-like point: Epic 603 marker = 5,411 testcases (0 F / 0 E / 26 skipped). 603 → 604 = −121 (5,290; net test methods removed in Epic 604's diff); 604 → 605 = +4 (`BaselineSchemaParityTest`) → 5,294. |
| Full backend verify wall | 1,555 s (25 min 55 s) | **751 s (12 min 31 s)** | — | Commands differ slightly: Before was `verify.sh -q`, After was `./mvnw clean -q && verify.sh -q`. Machine load was not controlled. Treat the halving as indicative, not a benchmark. |
| Backend top-level packages | 69 | **61** | 61 | matches |
| `@Entity` classes (files / annotations) | 121 / 121 | **103 / 103** | 103 | matches |
| Tenant migrations | 133 (high-water `V135`) | **1** (`V1__baseline.sql`) | 1 | matches. Throwaway drops were V136–**V144** (9), not the predicted ≈8 (V136–V143). |
| Global migrations | 25 (high-water `V25`) | **1** (`V1__baseline.sql`) | 1 | matches. Throwaway drops were V26–**V29** (4), not the predicted ≈3 (V26–V28). |
| `Capability` constants | 33 | **27** | 27 | matches |
| Audit event-type catalogue | 41 | **29** (test pin `AuditEventTypeRegistryTest:84,131,132` `hasSize(29)`; 31 `new AuditEventTypeMetadata(` − 2 resolver fallbacks) | 28 (§11.1) / 29 (task row) | **Differs from §11.1:** `ai.specialist.failed` was kept because the AI-skill pipeline still emits it (decision D2). |
| `VerticalModuleRegistry` modules (total / vertical / horizontal) | 12 / 8 / 4 | **8 / 6 / 2** | 8 / 6 / 2 | matches |
| Vertical profiles | 4 | **1** (`legal-za.json`) | 1 | matches (the mechanism is retained, ADR-332) |
| Frontend `page.tsx` routes | 126 | **100** | 100 | matches |
| Portal `page.tsx` routes | 22 | **18** | 18 | matches |
| Frontend vitest files / test cases | 404 / not measured | **348 / 2,184 passed + 2 skipped** | — | No Before test-case count was recorded. |
| Portal vitest files / test cases | 42 / not measured | **38 / 177 passed** | — | No Before test-case count was recorded. Portal last measured at Epic 604 head `3fd80149c` and unchanged since. |
| Frontend Playwright specs (`*.spec.ts`) | 76 | **58** | — | 60 at `97afbf223`. 606 deleted 2 dead specs (`lifecycle.spec.ts`, `lifecycle-interactive.spec.ts`, T3). |
| Portal Playwright specs | 5 | **5** | — | unchanged |
| Frontend `.ts/.tsx` files / lines (`git ls-files`) | 1,584 / 241,793 | **1,336 / 201,714** (−248 / −40,079) | — | `find` gives 1,337 because of the untracked, generated `next-env.d.ts` (see the §2 row-10 note). |
| Portal `.ts/.tsx` files / lines (`git ls-files`) | 117 / 15,205 | **105 / 13,103** | — | |
| `packages/` `.ts/.tsx` files / lines | 12 / 747 | **12 / 662** | — | |
| QA demo scenarios | 7 | **5** | 5 | matches. The 5 are `admin-audit-30day`, `ai-core-verification`, `legal-za-90day`, `legal-za-full-lifecycle`, `portal-client-90day` (all `-keycloak`). |
| Playwright configs (`frontend/e2e/`) | 4 | **3** (+ `portal/playwright.portal.config.ts`, unchanged) | 3 | matches |
| Docs site / portals / AI conversational surfaces | `docs/` workspace; 3 portals; 3 AI surfaces | removed; 1 (`portal/`); 1 (MCP) + in-app skills/reviews/gates | same | not re-counted (structural; see Epics 597/601/603) |

### 7.3 Observed verification (606C.1, R5)

- **Backend:** full verify on `a3bc46554`, whose product code is identical to main `97afbf223`. Command: `./mvnw clean -q && bash scripts/verify.sh -q`. Result: **5,294 testcases / 0 failures / 0 errors / 26 skipped**, wall **751 s**, log `/tmp/mvn-606-final.log` (ephemeral). Status: **VERIFIED**. Before-Phase-86 baseline: 6,084 Tests run with 2 pre-existing failures (Epic 595, §2 row 21). Epic 603 marker: 5,411.
- **Frontend** (`a3bc46554`): `pnpm lint` 0 errors; clean `pnpm build` OK; `pnpm test` 348 files / 2,184 passed / 2 skipped; `pnpm format:check` clean. Status: **VERIFIED**.
- **Portal:** unchanged since Epic 604 head `3fd80149c`. Lint OK, build OK, vitest 38 files / 177 passed (Epic 604 marker; the portal has no `format:check` script). Status: **VERIFIED** (on the unchanged tree).

### 7.4 Legal Playwright capstone (606C.1, R2): DEFERRED, not green

**Deviation T1:** both configs throw unless `E2E_AUTH_MODE=mock`, so they ran on the **E2E mock-auth stack** (:3001), not the Keycloak dev stack that breakdown 606C.1 / §11.10 slice 12 originally named. The breakdown, the architecture doc and the 605C.1 row were amended in 606.

- **Procedure:** main `97afbf223`; fresh volume (`e2e-down.sh && e2e-up.sh`); `PLAYWRIGHT_BASE_URL=http://localhost:3001`; run with `--update-snapshots=changed`.
- **Green definition (T17):** screenshot baselines were never committed, so a first-run missing-baseline error is not a product failure. Every other hard and soft assertion was enforced. The generated PNGs were **not** committed.

| Config | Passed | Failed | Skipped | Did not run |
|---|---|---|---|---|
| `playwright.legal-lifecycle.config.ts` | 63 | 4 | 6 | 19 |
| `playwright.legal-depth-ii.config.ts` | 2 | 2 | 11 | 7 |

| Failing test | Cause | Class |
|---|---|---|
| lifecycle `day-00-firm-setup.spec.ts:326` (trust settings) | The union locator `:343-345` matches the `<Card data-testid="lpff-rates-section">` div, so `fill` throws "not an <input>". | Pre-existing spec drift. The trust settings page and `components/trust/` are unchanged vs the tag. |
| lifecycle `day-30-billing.spec.ts:32` (Sipho fee note) | The spec expects an inline create-fee-note dialog. The real `create-invoice-button.tsx` is a customer-picker popover that routes to the customer's invoices tab. | Pre-existing spec/UI drift. The spec is unchanged since `15e007d2e` (2026-04-11). |
| lifecycle `day-45-recon-prescription.spec.ts:41` (bank recon) | Bank CSV upload is not implemented. The spec's own comment `:74-77` documents this gap. | Pre-existing feature gap, documented in the spec. |
| lifecycle `day-90-review.spec.ts:61` (fee notes list) | No fee note exists because of the day-30 failure, and the seed creates none. | Knock-on from day-30. |
| depth-ii `day-05-disbursements.spec.ts:33` | **Root-caused in this session.** `components/breadcrumbs.tsx` renders a link for every path segment, so `/org/<slug>/legal` is RSC-prefetched. That route has no `page.tsx`, both at `horizontal-platform-final` and at HEAD. The prefetch 404s and never completes, so `waitForLoadState("networkidle")` times out. Probe: `GET /org/e2e-test-org/legal?_rsc=…` was still in flight after 8 s on the disbursements page; there was none on `/projects`. | **Pre-existing product bug** (dead breadcrumb link). Phase 86 only removed labels from `breadcrumbs.tsx`. |
| depth-ii `day-14-conveyancing.spec.ts:99` | The spec expects 10 conveyancing custom fields on a matter created from the Property Transfer template. The matter has `work_type` NULL because the spec never selects a work type, and template instantiation takes `workType` only from the request. See the pre-existing TODO in `ProjectTemplateService`: "when ProjectTemplate gains a workType column". | Pre-existing product gap. The code is unchanged vs the tag. |

- **Phase-86-caused failures: 0.** The pre-squash vs post-squash comparison (605C) showed no green→red. Each failure above is backed by code-diff evidence against `horizontal-platform-final` (`.epic-brief.md` R3 failure table).
- **Triage (T18, root `CLAUDE.md` §1):** none of these is fixed in Phase 86. All are listed as follow-ups in §7.8(a), one fix per PR, and no scenario or spec edits are made to dispose of them. The status stays **DEFERRED** until those fixes land and both configs are re-run green, or until the user explicitly accepts closure with them deferred.
- **Logs** (ephemeral): `/tmp/pw-606-lifecycle.log`, `/tmp/pw-606-depthii.log`. **Artefacts** (ephemeral): `/tmp/pw-606-artefacts/`. The tracked `frontend/test-results/` and `documentation/screenshots/` were restored after the runs.

### 7.5 `qa/testplan/demos/ai-core-verification-keycloak.md`: DEFERRED, needs the user

Not walked. Step V1 needs a human to enter a funded Anthropic key through the UI (`SetApiKeyDialog`); no environment-variable path exists. Expected spend is about R10–R30 (the last full run was R23.99). V11.1 overwrites the stored key, so the human must re-enter it afterwards. The Keycloak dev stack was wiped and re-provisioned on the squashed baseline in 605C, where the smoke test was VERIFIED (`tenant_56399d06a773`, history `1|1|t`), so it is ready for the walk. Runbook: `.epic-brief.md` R4.

### 7.6 Archive

- **Archived phases:** the `TASKS.md` rows are annotated, not deleted (606A).
  - Phase 16: Recurring Schedules part only ("Partially removed").
  - Phases 17, 32, 38, 45, 47, 51, 52, 59, 66, 70, 80: "Removed in Phase 86".
  - Phase 57: PayFast parts only ("PayFast parts removed").
  - Also removed but **not** annotated, per T8 (exactly 13 phases annotated): the Phase 7 Thymeleaf `/portal/dev` harness (Epic 603).
- **Tag:** `horizontal-platform-final`.
  - `git rev-parse --short horizontal-platform-final` → `22b528628`. This is the **annotated tag object**, not a commit.
  - `git rev-parse --short 'horizontal-platform-final^{commit}'` → **`7f47f9629`**. This matches the task row and §1, so there is no mismatch once the tag is peeled.
- **Mirror:** https://github.com/rakheen-dama/kazi-horizontal-archive (`HEAD` = `7f47f96295192aab1970b22a36231d73d4beada9`, verified 2026-10-15T22:20:34Z, §1). The resurrection procedure is in §1 and ADR-330.

### 7.7 Phase 86 PRs (main)

| PR | Squash commit | Subject |
|---|---|---|
| #1564 | `4414f3c67` | Epic 595: archive precondition, baseline metrics, confirmed seam list |
| #1565 | `632782c81` | Epic 596: remove crm (Phase 80) + Pipeline UI |
| #1566 | `924b92deb` | Epic 597: remove proposal (Phase 32) + legacy in-app portal + portal proposals |
| #1567 | `77ab00109` | fix(e2e-seed): boot seed completes (dead plan-sync dropped, activation address added, idempotent customer step) |
| #1568 | `21173f5b4` | fix(portal): resolve resync assignee names in tenant scope |
| #1569 | `17c607f83` | Epic 598: remove retainer (Phase 17) + portal retainer |
| #1570 | `9c3d9ec6a` | Epic 599: remove capacity (Phase 38) + Resources/Utilization UI |
| #1571 | `3ebb14a8b` | QA plans: remove capacity/resources steps (Epic 599 follow-up) |
| #1572 | `2768e9ea7` | Epic 600: remove recurring schedules, re-home time reminders |
| #1573 | `9907ab1a1` | Epic 601: remove assistant (Phases 45/52/70), re-home provider |
| #1574 | `5517ebebe` | Epic 602: remove PayFast platform subscription path (Phase 57) |
| #1575 | `f409e4a31` | Epic 603: remove Thymeleaf dev harness + `docs/` site + docsLink policy (Phases 7/59) |
| #1576 | `ce13cbec5` | Epic 604: single profile, legal-za only (non-legal profiles/packs/seeders/demo, `deadline/`, automation builder, rate packs, terminology) |
| #1577 | `d8fb3983c` | Epic 605: migration squash → tenant V1 / global V1 |
| #1578 | (open) | Epic 606: docs sweep + ADR supersession (606A/606B) + this closure report (606C) |

### 7.8 Follow-ups

**(a) Bugs found by the capstone.** None was caused by Phase 86. Each is one fix per PR and needs an orchestrator/user decision on the fix option.
1. **FIXED 2026-10-18 in #1579** (breadcrumbs render pageless prefixes `legal`, `ai`, `information-requests`, `settings/templates/[id]` as text; filesystem guard test; e2e networkidle 669 ms, day-05:33 passes — the next test, day-05:51 "create sheriff-fee disbursement", now runs and fails on its own pre-existing issue). Original note: **Dead `/legal` breadcrumb link.** `components/breadcrumbs.tsx` links every segment, including `/org/<slug>/legal`, which has no page. Fix options: render no link for segments without a route, or add a legal index page. This unblocks depth-ii day-05 and its dependent tests.
2. **FIXED 2026-10-18 in #1580 + #1581** (#1580: tenant V2 `project_templates.work_type`, seeder persists pack `matterType`, both instantiate paths fall back to the template's work type, V2 backfills seeded rows, BaselineSchemaParityTest converted to `.target("1")`; #1581: one `worktype.WorkTypes` normaliser — trim, spaces→`_`, upper-case — on every write path, case-insensitive field-group matching, saved-view work_type operands normalised, tenant V3 normalises existing rows, UI suggests LITIGATION/CONVEYANCING/ESTATES/COLLECTIONS/COMMERCIAL). Original note: **Template-created matters don't inherit a work type.** `ProjectTemplateService` takes `workType` from the request only (see its TODO). This is a product gap: conveyancing fields are not applied to Property Transfer template matters (depth-ii day-14:99).
   - **Follow-on (2026-10-18):** #1583 fixed a regression from #1581 (New Matter dialog taller than a 720px viewport with no scroll; datalist in a form grid pushed "Create Matter" off-screen → 4 lifecycle matter steps failed). #1582 rewrote the never-executed legal-depth-ii specs (native selects via `selectOption`, all required fields, hard assertions, standalone run-scoped data): depth-ii now **26 passed / 0 failed / 14 fixme** (was 2/1/16/3), standalone and after lifecycle. The day-05:51 failure was a spec defect, not a product bug.
   - **Product bugs now tracked as `test.fixme` in legal-depth-ii (grep `FIXME(depth-ii:`)** — fix candidates: ~~`page-transition-remount`~~ **FIXED #1584** (PageTransition AnimatePresence keyed by pathname replaced by `org/[slug]/template.tsx` CSS fade; un-parked test passes), `page-transition-blank` (NOT caused by PageTransition: the matter page's RSC response is aborted ~150–200 ms after a 200 and never re-requested; matter content intermittently blank — **most severe remaining**), `trust-link-payment-type` (trust-link dialog filters DISBURSEMENT_PAYMENT, ledger records PAYMENT → TRUST_ACCOUNT disbursements impossible from UI), `no-write-off-action` (API exists, no UI), `matter-unbilled-disbursements-summary` (missing UI), `soa-preview-unbounded-scale` (SoA dialog widens unboundedly once preview renders), `otp-clause-associations` (OTP "Conditions of Sale" empty — clause pack has no template associations), `checklist-stale-after-complete` (intermittent stale Pending), `admin-offered-closure-override` (frontend grants all capabilities to admin; backend correctly 403s), `retention-period-unconfigurable` (no UI/API to set legal_matter_retention_years). Environment limitation (not product): `e2e-s3-presigner-unreachable`.
   - Remaining legal-lifecycle failures (pre-existing spec/UI drift): day-00:326, day-30:32, day-45:41, day-90:61; plus day-60:41 / day-90:177 when reached (spec issues: interest-run dates unset + no LPFF rates; strict-mode multi-match selector).
3. **Lifecycle spec drift (4 tests):**
   - day-00:326: the locator matches the card div.
   - day-30:32: the inline fee-note dialog assumption is wrong. `:159` (Apex fixed fee) is the same flow; it failed in R3 and was not reached in this run.
   - day-45:41: the documented CSV-upload gap. Deciding whether to build the feature or re-scope the spec is a product call.
   - day-90:61: knock-on from day-30.
   - Also unresolved from R3: day-90:177 profitability (empty data vs strict multi-match). It was skipped in this run (serial block after the day-30 fee-note failure; day-30:159 likewise skipped); re-check after the day-30 fix.

**(b) Needs user authorisation.**
- QA demo rewording items listed in the PR #1578 body, "Needs user authorisation":
  - `portal-client-90day-keycloak.md`: three-tenant/all-profile wording; the Days 7/75 `/deadlines` prerequisite.
  - `admin-audit-30day-keycloak.md`: step 0.8 still expects `deal.won`, and its row-count threshold must be re-derived.
  - `ai-core-verification-keycloak.md:67,77`: "upgrade plan first if gated".
- The AI-core walk (§7.5).
- Acceptance or fix of the §7.4 DEFERRED Playwright items (T18).

**(c) Phase 87 list.**
- ADR-332 deviation: Settings → General still renders the vertical-profile picker / "Apply Profile" (`components/settings/vertical-profile-section.tsx`, `settings/general/page.tsx`) (F-B3).
- `components/marketing/built-for-africa.tsx` still advertises SARS deadlines, VAT and financial year-end (F-B2).
- Stale V-number code comments (F-D). The brief estimated ≈79 stale refs (12 main + ≈67 test). The F-D grep re-run on `c6d530190` returns **86** raw hits, which include intentional keeps (`BaselineSchemaParityTest`, the "(originally V…)" provenance comments). Triage with: `grep -rnE '(^|[^A-Za-z0-9_])V[0-9]{1,3}($|[^0-9])' backend/src/main backend/src/test | grep -v V1__baseline`.
- Dead code (F-E):
  - `AutomationTemplateController` `POST /api/automation-templates/{slug}/activate` and the list GET: no frontend/portal/gateway caller.
  - `MemberRateSeedingService`: effectively a no-op (legal-za has no `rateCardDefaults`).
  - `FieldPackSeeder` `applicableEntityValues` branch: no shipped pack uses it.
  - `LlmChatProvider.chat(…, Consumer<StreamEvent>)`: has the streaming/tool-use stack in `AnthropicLlmProvider` (≈250 lines) but zero production callers.
  - `ChecklistInstantiationService` guard: comment names a non-existent pack. The logic is kept; fix the comment.
  - `BillingRunSelectionService:719-721`: comment reflow.
  - Single-entry registries: `VerticalProfileRegistry`, `VerticalModuleRegistry` `defaultEnabledFor`, `LlmChatProviderRegistry`; portal `nav-items.ts` `profiles: ["legal-za"]`.
  - `ProjectTemplateService.instantiateFromTemplate`: dead after Epic 600.
  - `DeadlinePortalSyncService` FILING_SCHEDULE source + portal `FILING` type.
- **Bug:** `frontend/components/billing/billing-tenants-table.tsx` `PROFILE_FILTER_OPTIONS` (`GENERIC`/`ACCOUNTING`/`LEGAL`) never matches `legal-za`, and `AdminBillingService` ignores `profileFilter`.
- Full rewrite of `kazi-architecture/`; it carries a banner only (T7).
- Untrack `frontend/test-results/` (9 tracked Playwright output files, F-F).
- `requirements/claude-code-prompt-phase84.md:14`: stale V-numbers. Phase 84 lands as tenant `V2`.
- e2e specs that pass vacuously (F-C):
  - Kgosi/Naledi filters: `customer-data-protection.spec.ts`, `custom-fields.spec.ts`.
  - `portal-data-isolation.spec.ts`: skips without `KGOSI_EMAIL`.
  - Accounting-flavoured fallback regexes: `document-generation.spec.ts`.
- `ideate` skill domain lists and `next-phase` skill note.
- `portal/e2e/screenshots/portal-v2/portal-client-90day/README.md`: three-tenant instructions.

**(d) Ops.**
- Wipe the VPS dev environment's Postgres volume (`kazi-prod_postgres_data` only, then re-run `keycloak-bootstrap.sh`; see `DEPLOY-VPS.md`) **before** deploying any image built from `d8fb3983c` or later.
- The pre-squash dev-stack volume backups (`docteams-dev_postgres_data`, `docteams-dev_localstack_data`) are in the 605C session scratchpad, `dev-volume-backup-pre-phase86-squash/*.tgz`. The scratchpad is ephemeral, so copy them somewhere durable if they are wanted.
