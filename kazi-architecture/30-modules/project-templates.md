# Project Templates

**Status:** Phase C — filled.
**Bounded context:** see [`10-bounded-contexts.md` § project-templates](../10-bounded-contexts.md#project-templates).

## 1. Purpose

Project **blueprints** + **recurring schedules** for cron-driven project instantiation. The module is the **producer** side of the project lifecycle: a `ProjectTemplate` declares the shape of a project (default name pattern, billable flag, task tree, required customer-side prerequisites, optional information-request kickoff), and a `RecurringSchedule` binds a template to a customer with a frequency. A daily scheduler (`RecurringScheduleExecutor`, 02:00 UTC) instantiates new projects from due schedules, interpolating a final name from the template's pattern via `NameTokenResolver`. The instance side lives in `30-modules/projects.md` — once instantiation completes, the resulting `Project` is the unit of work, and this module is no longer in the path.

Templates are seeded as part of pack installation (per-vertical), reused by the manual "create project from template" flow on the projects page, and consumed by the scheduler. All three paths converge on the same `ProjectTemplateService.instantiate(...)` entry point.

## 2. Entities owned

| Entity | Anchor | Notable fields |
|---|---|---|
| `ProjectTemplate` | `→ backend/.../projecttemplate/ProjectTemplate.java:18` | `name`, `namePattern`, `description`, `billableDefault`, `source`, `active`, `requiredCustomerFieldIds (jsonb)`, `requestTemplateId` (`A1-backend-map.md:312`) |
| `TemplateTask` | `→ backend/.../projecttemplate/TemplateTask.java` | Per-template task blueprint (snapshot-on-instantiation per ADR-068 pattern; phase16 calls this out — `A5-phase-doc-skim.md:19`) |
| `TemplateTaskItem` | `→ backend/.../projecttemplate/TemplateTaskItem.java` | Sub-items under a template task |
| `RecurringSchedule` | `→ backend/.../schedule/RecurringSchedule.java:18` | `templateId`, `customerId`, `nameOverride`, `frequency` (`WEEKLY/FORTNIGHTLY/MONTHLY/QUARTERLY/SEMI_ANNUALLY/ANNUALLY`), `startDate`, `endDate`, `leadTimeDays`, `status` (`ACTIVE/PAUSED/COMPLETED`), `nextExecutionDate`, `lastExecutedAt`, `executionCount`, `projectLeadMemberId`, `postCreateActions (jsonb)` |
| `ScheduleExecution` | `→ backend/.../schedule/ScheduleExecution.java` | Per-fire history row exposed via `GET /api/schedules/{id}/executions` |

`RecurringSchedule.postCreateActions` is the JSONB carrier for ADR-198 (post-create-action-execution); accounting-vertical phase 51 was the first user (`A5-phase-doc-skim.md:54`).

The `Schedule` placeholder name in the prior stub resolves to `RecurringSchedule` — the schedule entity lives in a sibling `schedule/` package, not under `projecttemplate/`. This is a deliberate split: the schedule is the recurrence rule, the template is the blueprint, and they are joined by `templateId`. Glossary entry confirms: "Schedule (Project) — `ProjectSchedule` REST path `/api/schedules`" (`glossary.md:243`) — REST path is canonical, the implementation class is `RecurringSchedule`.

## 3. REST surface

`ProjectTemplateController` — `/api/project-templates` (~10 endpoints, `A1-backend-map.md:413`):

- `GET /api/project-templates` — list (`ProjectTemplateController.java:42`)
- `GET /api/project-templates/{id}` — read (`:47`)
- `POST /api/project-templates` — create (`:52`)
- `PUT /api/project-templates/{id}` — update (`:62`)
- `DELETE /api/project-templates/{id}` — delete (`:69`)
- `POST /api/project-templates/{id}/duplicate` — clone (`:76`)
- `POST /api/project-templates/from-project/{projectId}` — extract template from existing project (`:85`)
- `POST /api/project-templates/{id}/instantiate` — manual instantiation (`:95`)
- `PUT /api/project-templates/{id}/required-customer-fields` — set prerequisite field IDs (`:104`)
- `GET /api/project-templates/{id}/prerequisite-check` — preflight before instantiate (`:112`)

`RecurringScheduleController` — `/api/schedules` (`RecurringScheduleController.java:25`):

- `GET /api/schedules` — list (`:34`)
- `GET /api/schedules/{id}` — read (`:42`)
- `POST /api/schedules` — create (`:47`)
- `PUT /api/schedules/{id}` — update (`:56`)
- `DELETE /api/schedules/{id}` — delete (`:63`)
- `POST /api/schedules/{id}/pause` — pause (`:70`)
- `POST /api/schedules/{id}/resume` — resume (`:76`)
- `GET /api/schedules/{id}/executions` — execution history (`:82`)

Capability gate: `PROJ_MGMT` (template CRUD + schedule CRUD; mirrored by the `/schedules` page gate in `A2-frontend-map.md:182`).

## 4. Frontend pages / components

| Page / module | Anchor | Notes |
|---|---|---|
| Project blueprint templates | `frontend/.../settings/project-templates/page.tsx` (`A2-frontend-map.md:226-227`) | Template CRUD, task-tree editing, required-field selector, request-template attach. |
| Auto-naming pattern config | `frontend/.../settings/project-naming/page.tsx` (`A2-frontend-map.md:228-229`) | Org-wide name-pattern defaults; the page edits patterns, the resolver is `NameTokenResolver` (§6). |
| Recurring schedules | `frontend/.../schedules/page.tsx` (`A2-frontend-map.md:181-182`) | Live under top-level nav (not settings). Module-gated to `PROJ_MGMT` capability. Lists active schedules with next-fire date and execution history. |
| API clients | `frontend/lib/api/schedules.ts` (`A2-frontend-map.md:301`); template client via the standard settings module pattern | |
| Components | `components/schedules/` — `ScheduleList`, `ScheduleCreateDialog` (`A2-frontend-map.md:439`) | |

The split between `/settings/project-templates` (blueprint editor, low-frequency change) and `/schedules` (per-customer recurrences, high-frequency operational use) is deliberate — schedules are operational, templates are configuration.

## 5. Domain events

**Published (all from `RecurringScheduleService` via `ApplicationEventPublisher`):**

| Event | Anchor | When |
|---|---|---|
| `RecurringProjectCreatedEvent` | `→ schedule/event/RecurringProjectCreatedEvent.java`, published `RecurringScheduleService.java:742-758` | A schedule fires successfully and a project is instantiated. Fan-out: notification, automation, portal read-model. |
| `ScheduleSkippedEvent` | `→ schedule/event/ScheduleSkippedEvent.java`, published `RecurringScheduleService.java:467, 765-778` | A due schedule was not executed because the customer is missing prerequisite fields, the template is inactive, or the customer is in a non-instantiable state. Carries skip-reason metadata. |
| `ScheduleCompletedEvent` | `→ schedule/event/ScheduleCompletedEvent.java`, published `RecurringScheduleService.java:731, 781-792` | The schedule has reached its `endDate` (status transitions to `COMPLETED`). |
| `SchedulePausedEvent` | `→ schedule/event/SchedulePausedEvent.java` | The schedule is paused via `POST /api/schedules/{id}/pause`. |
| `TemplateCreatedEvent` | `→ projecttemplate/event/TemplateCreatedEvent.java` | A new `ProjectTemplate` is created (used by audit + automation). |

The three core schedule events are listed in the bounded-context summary (`10-bounded-contexts.md:320`) and the producer table (`A1-backend-map.md:466`). Notification fan-out is registered explicitly in the consumer table (`A1-backend-map.md:475`, "schedule events").

## 6. Cross-cutting touchpoints

### 6.1 Daily scheduler

`RecurringScheduleExecutor` (`→ schedule/RecurringScheduleExecutor.java:30`) runs `@Scheduled(cron = "0 0 2 * * *")` — daily at 02:00 UTC. It iterates tenants via `TenantScopedRunner.forEachTenant(...)` (`RecurringScheduleExecutor.java:34`), then per tenant calls `scheduleService.findDueSchedules()` and invokes `scheduleService.executeSingleSchedule(schedule)` once per row (`:57-59`). The loop is in the executor (not the service) so each `executeSingleSchedule` call goes through the Spring proxy and `REQUIRES_NEW` transaction propagation actually takes effect — error in one schedule does not poison the rest of the tenant's batch (`RecurringScheduleExecutor.java:11-15`).

This is the canonical instance of ADR-071 (daily-batch-scheduler) in the codebase.

### 6.2 Pre-calculated next-execution date

`RecurringSchedule.nextExecutionDate` is materialised on the row, not computed at query time. After each successful fire, the service advances it via `PeriodCalculator` (`→ projecttemplate/PeriodCalculator.java`) using `RecurringSchedule.frequency`. This is ADR-070 (pre-calculated-next-execution-date) — the daily query is `WHERE next_execution_date <= today AND status = 'ACTIVE'`, not a recurrence-rule walker.

### 6.3 Required-customer-fields prerequisite check

`ProjectTemplate.requiredCustomerFieldIds` (jsonb, `A1-backend-map.md:312`) is the per-template list of customer custom-field IDs that must be populated before instantiation can succeed. Both paths enforce it:

- **Manual** (`POST /api/project-templates/{id}/instantiate`): `ProjectTemplateService.java:505-510` calls `prerequisiteService.checkEngagementPrerequisites(customerId, templateId)`; on failure throws `PrerequisiteNotMetException` (HTTP 4xx). Frontend can preflight via `GET /{id}/prerequisite-check`.
- **Scheduled** (executor → `executeSingleSchedule`): `RecurringScheduleService.java:536-551` runs the same prerequisite check; on failure publishes `ScheduleSkippedEvent` (`:467`) with the missing-field metadata, advances `nextExecutionDate` to the next period, and attempts a notification to the project lead (`:551`). The schedule does **not** retry the missed period — see Open Questions §10.

The prerequisite framework lives in its own module (`prerequisite/PrerequisiteService`); ADRs ADR-130/131/132 cover storage and granularity. Project-template usage is the primary consumer.

### 6.4 Name-pattern interpolation

`NameTokenResolver` (`→ projecttemplate/NameTokenResolver.java:17`) does simple `String.replace()` substitution — no template engine. Supported tokens (`NameTokenResolver.java:24`): `{customer}`, `{month}`, `{month_short}`, `{year}`, `{period_start}`, `{period_end}`. If a parameter is null, the token is left unreplaced in the output (`:13-14`) — by design, so that a misconfigured schedule produces a visible "Bookkeeping - {customer} - {month} 2026" rather than a silent empty.

`RecurringSchedule.nameOverride` takes precedence over `ProjectTemplate.namePattern` when set; otherwise the template's pattern is used.

### 6.5 Audit + capability

All template/schedule mutations are audited via `AuditService.log(...)` calls in `ProjectTemplateService` and `RecurringScheduleService` (the canonical pattern per `A1-backend-map.md:477`). All endpoints are gated by `@RequiresCapability(PROJ_MGMT)` mirroring the frontend `/schedules` gate (`A2-frontend-map.md:182`).

### 6.6 Post-create actions

`RecurringSchedule.postCreateActions` (jsonb, `RecurringSchedule.java:71-73`) is the carrier for action chains run after a project is instantiated by a fire — covered by ADR-198 (post-create-action-execution). Phase 51 (accounting-practice-essentials) was the first user (`A5-phase-doc-skim.md:54`); the actions are interpreted by the automation module's executor, not by `RecurringScheduleService` directly.

### 6.7 Snapshot-on-instantiation

Phase 16 fixed the pattern as snapshot-on-instantiation (`A5-phase-doc-skim.md:19`), mirroring checklist and document templates: when a schedule fires, `ProjectTemplateService.instantiate` copies the current `TemplateTask`/`TemplateTaskItem` rows into the new `Project`'s tasks, freezing them. Subsequent edits to the template do not mutate already-instantiated projects.

## 7. Vertical specifics

Project templates are a **terminology-overlaid + pack-seeded** context. The engine is universal; vertical content ships as data.

- **Pack-seeded per vertical.** `ProjectTemplatePackSeeder` (`A1-backend-map.md:52`) installs per-vertical default templates at provisioning time. Phase 64 — legal vertical QA — was the first phase to seed legal-specific matter templates end-to-end (`A5-phase-doc-skim.md:67`: "New `ProjectTemplatePackSeeder`; the first time legal vertical was actually walked end-to-end"). Four matter templates are seeded for `legal-za` (litigation, conveyancing, deceased estate, divorce per phase64 + recent memory).
- **Terminology overlay.** In `legal-za` the UI label for "Project" is "Matter"; project templates surface as "matter templates" in copy. The entity name remains `ProjectTemplate` (no per-vertical fork).
- **Frequency catalogue.** `RecurringSchedule.frequency` enumerates `WEEKLY/FORTNIGHTLY/MONTHLY/QUARTERLY/SEMI_ANNUALLY/ANNUALLY` (`RecurringSchedule.java:33-35`). Accounting-vertical bookkeeping/VAT cycles drive the MONTHLY/QUARTERLY usage; legal-vertical recurrence is the exception (most matters are one-off).
- **Schedule pack seeding.** `SchedulePackSeeder` (sibling to `ProjectTemplatePackSeeder`, `A1-backend-map.md:52`) seeds vertical-default `RecurringSchedule` rows where the vertical has standard cycles (e.g., accounting-za monthly bookkeeping for an active customer).

## 8. Active ADRs

No project-template-named ADR is currently in the canonical-by-name list, but the cluster from `90-adr-index.md` is:

| ADR | Title | Note |
|---|---|---|
| ADR-070 | pre-calculated-next-execution-date | `RecurringSchedule.nextExecutionDate` materialisation |
| ADR-071 | daily-batch-scheduler | `RecurringScheduleExecutor` 02:00 UTC, per-tenant loop |
| ADR-130 | prerequisite-enforcement-strategy | `requiredCustomerFieldIds` enforcement |
| ADR-131 | prerequisite-context-granularity | per-template field-level granularity |
| ADR-132 | engagement-prerequisite-storage | jsonb on the template |
| ADR-137 | project-template-integration-scope | scope boundary with projects/automation |
| ADR-198 | post-create-action-execution | `postCreateActions` jsonb on schedule |
| ADR-068 | snapshot-based-templates | snapshot-on-instantiation pattern (shared with documents) |

ADR-198 sits in the automation cluster but is the contract between this module and automation, so it is anchored here too.

## 9. Key flows

- `50-flows/automation-trigger-to-action.md` — covers the `CREATE_PROJECT` action type (`30-modules/automation.md:22`), which calls into `ProjectTemplateService.instantiate(...)` from an automation rule. Project-template usage is the action-side, not the trigger-side.
- `50-flows/pack-install-and-vertical-onboarding.md` — covers `ProjectTemplatePackSeeder` and `SchedulePackSeeder` running during tenant provisioning, seeding the vertical-default templates and (where applicable) recurrences.

A dedicated `recurring-schedule-fire.md` flow (executor → due query → prerequisite check → instantiate → post-create-actions → events) is **not yet authored**; the anchors above are the source of truth in the meantime.

## 10. Open questions / known fragility

- **Skipped-schedule replay semantics.** When `executeSingleSchedule` skips a fire (prerequisite not met, customer not active), `nextExecutionDate` is advanced to the **next** period — the missed period is not re-attempted once the prerequisite is satisfied. There is no "catch-up on first eligible fire" mode, and `ScheduleSkippedEvent` consumers do not currently re-fire the schedule. For an accounting-za customer who completes onboarding mid-month, the first bookkeeping project will not back-fill the missed month. Whether this is intended or accidental needs an ADR.
- **Required-field prerequisite chain.** A template with `requiredCustomerFieldIds = [VAT_NUMBER, TAX_OFFICE]` is meaningful only after the customer has populated those fields. There is **no eventing** from custom-field updates back to schedules — i.e. no "the customer just filled in VAT number, retry the skipped schedule" path. The current model is "next daily run will succeed if fields are populated by then" plus the skip-event notification (`RecurringScheduleService.java:551`). For an org with strict deadlines (VAT submission), this is a known fragility.
- **Template versioning when pack-seeded.** Pack seeders re-run on provisioning + re-import, but there is no documented version model on `ProjectTemplate`. If a pack ships v2 of "Monthly bookkeeping" with extra tasks, the seeder's behaviour against an existing v1 row (overwrite? insert sibling? skip if `source=PACK`?) is not specified in the architecture docs and depends on `AbstractPackSeeder` semantics. Snapshot-on-instantiation (§6.7) means already-fired projects are unaffected — but the **template** itself drift is unmanaged, and a customer-edited PACK template is potentially overwritten on the next pack reseed. Compare to `AutomationRule.templateSlug` (`30-modules/automation.md:98`), which is the explicit re-keying mechanism for automation packs; project templates lack the equivalent.
- **Time zone of the daily fire.** The executor runs 02:00 **UTC** (`RecurringScheduleExecutor.java:30`). For a SAST tenant (UTC+2) this is 04:00 local — fine for overnight batch. For a tenant west of UTC, the fire happens in the previous calendar day local — the `LocalDate today = LocalDate.now()` inside the service uses the JVM default zone, not the tenant's, and there is no per-tenant time-zone override. Cross-region tenancy (currently single-region SA-only) would need to revisit.
- **Manual-instantiate vs scheduled-instantiate divergence.** Both paths converge on `ProjectTemplateService.instantiate`, but the prerequisite handling diverges: manual throws (caller sees the error), scheduled emits a skip event (caller is the cron, no human in the loop). If the prerequisite framework grows a "warn but allow" mode, the two paths must continue to handle it consistently — there is no shared decision point today, only a shared call site.
