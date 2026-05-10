# Projects

**Bounded context:** see [`10-bounded-contexts.md` § projects](../10-bounded-contexts.md).

## Purpose

`Project` is the unit-of-delivery aggregate — a customer-linked container for tasks, time entries, expenses, documents, and invoice lines. It owns a four-state lifecycle (`ACTIVE → COMPLETED → ARCHIVED`, plus the legal-vertical-only `CLOSED`) with validated transitions and audit on every move, and is the access-control boundary for tenant members via `ProjectMember`. The aggregate also anchors the retention clock on closure (ADR-249, owned by `customer-lifecycle.md`'s retention concern but stamped on `Project`). Sibling aggregates — tasks, time entries, expenses, capacity allocations — keep their own modules; this page covers Project lifecycle, project-level access, project setup readiness, and the project-scoped budget threshold seam.

UI label is "Matter" in the legal vertical and "Engagement" in the accounting vertical (`60-verticals/`); the backend says **Project** everywhere except in `verticals/legal/closure/MatterClosure*` where compliance closure logic lives.

## Entities owned

- `Project` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:24` — aggregate root. Columns: `name`, `description`, `status`, `customerId`, `dueDate`, `createdBy`, `completedAt/By`, `archivedAt/By`, `closedAt`, `retentionClockStartedAt`, `referenceNumber`, `priority`, `workType`, `customFields` (jsonb), `appliedFieldGroups` (jsonb), `version` (`@Version` optimistic lock — `Project.java:41`).
- `ProjectStatus` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectStatus.java:7` — enum `ACTIVE | COMPLETED | ARCHIVED | CLOSED` with the allowed-transition table at `:19`. Only `ARCHIVED` is terminal (`:40`); `CLOSED` is non-terminal (supports reopen) and is reached only via legal-vertical paths (`:11` javadoc).
- `ProjectPriority` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectPriority.java:4` — `LOW | MEDIUM | HIGH`.
- `ProjectMember` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java:14` — per-project access grant (`projectId`, `memberId`, `projectRole`, `addedBy`). **Anchored here** for access-control purposes; the table physically lives in the `member/` package alongside `Member`. See Open Questions for the ownership ambiguity flagged in `_discovery/A1-backend-map.md:38`.
- `ProjectAccess` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccess.java` — value record (`canView, canEdit, canManageMembers, canDelete, projectRole`) returned by `ProjectAccessService`.
- `ProjectSetupStatus` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/ProjectSetupStatusService.java:45` — **computed on-the-fly** value (not an entity), returned by `GET /api/projects/{id}/setup-status` (ADR-066). Aggregates rate, budget, member, and field-group readiness.
- `CustomerProject` (join) `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProject.java:14` — many-to-many link Customer↔Project; **owned by** `customer-lifecycle` per `_discovery/A1-backend-map.md:155`. Cross-linked here.

## REST surface

`ProjectController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java:46` (~18 endpoints, base `/api/projects`, anchored against `_discovery/A1-backend-map.md:388`):

| Method + path | Capability gate | Notes |
|---|---|---|
| `GET /` | none (member access via `ProjectAccessService`) | List with view/status/dueBefore/customerId filters `→ ProjectController.java:70`. |
| `GET /{id}` | none (access via `requireViewAccess`) | `→ ProjectController.java:191`. |
| `POST /` | `PROJECT_MANAGEMENT` | Create `→ ProjectController.java:201-202`. |
| `PUT /{id}` | none on annotation; `requireEditAccess` inside service `→ ProjectService.java:344` | Update `→ ProjectController.java:223`. |
| `DELETE /{id}` | none on annotation; service-level guard | Delete `→ ProjectController.java:245`. |
| `PATCH /{id}/complete` | `PROJECT_MANAGEMENT` | Lifecycle: ACTIVE→COMPLETED `→ ProjectController.java:251-252`. |
| `PATCH /{id}/archive` | `PROJECT_MANAGEMENT` | Lifecycle: any→ARCHIVED `→ ProjectController.java:264-265`. |
| `PATCH /{id}/reopen` | `PROJECT_MANAGEMENT` | Lifecycle: COMPLETED/ARCHIVED/CLOSED→ACTIVE `→ ProjectController.java:273-274`. |
| `PUT /{id}/field-groups` | `PROJECT_MANAGEMENT` | Apply field groups `→ ProjectController.java:282-283`. |
| `POST /{id}/tags`, `GET /{id}/tags` | none | Tag attach `→ ProjectController.java:292,301`. |
| `GET /{id}/setup-status` | none | Computed readiness (ADR-066) `→ ProjectController.java:310`. |
| `GET /{id}/unbilled-summary` | `PROJECT_MANAGEMENT` | Billable-time aggregate `→ ProjectController.java:315-316`. |
| `GET /{id}/upcoming-deadlines` | none | Tasks + custom-field date deadlines `→ ProjectController.java:321`. |

A1 also lists `PUT /{id}/custom-fields` and `PUT /{id}/tags` against ProjectController (`_discovery/A1-backend-map.md:388`); the controller's `@PutMapping("/{id}/field-groups")` covers field-group binding, and tag binding goes through the tag endpoints above.

`PATCH /{id}/close` (compliance-gated) is **not** on `ProjectController`. The legal-vertical close path is owned by `MatterClosureService` `→ verticals/legal/closure/MatterClosureService.java` (per glossary `→ kazi-architecture/glossary.md:169`); see `60-verticals/legal-za.md`.

`ProjectMemberController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberController.java:22` (3 endpoints, base `/api/projects/{projectId}/members`):

- `GET /` — list project members (`:30`).
- `POST /` — add member (`:39`).
- `DELETE /{memberId}` — remove member (`:50`).

Project-scoped sub-resources are owned by sibling modules: `/api/projects/{projectId}/tasks/*` (tasks), `/api/projects/{projectId}/time` (time-entry), `/api/projects/{projectId}/expenses` (expenses), `/api/projects/{projectId}/documents` (documents-templates) — see `_discovery/A1-backend-map.md:389,393-395`.

## Frontend pages / components

Anchors against `_discovery/A2-frontend-map.md`:

- `frontend/app/(app)/org/[slug]/projects/page.tsx` — project list with filters, views, tags `→ A2-frontend-map.md:111`.
- `frontend/app/(app)/org/[slug]/projects/actions.ts` — server actions (`createProject`, `updateProject`, `archiveProject`, etc.) `→ A2-frontend-map.md:112`.
- `frontend/app/(app)/org/[slug]/projects/new/page.tsx` — create-project form (sibling of list).
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — project detail with tabs (tasks, docs, time, expenses, budget) — the **entity detail page** that ADR-067 frames as the contextual action surface `→ A2-frontend-map.md:114`.
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` — project-detail server actions `→ A2-frontend-map.md:116`.
- `frontend/app/(app)/org/[slug]/projects/[id]/member-actions.ts` — `fetchProjectMembers`, `addProjectMember`, `removeProjectMember` (calls `/api/projects/{id}/members`) `→ A2-frontend-map.md:117`.
- `frontend/app/(app)/org/[slug]/projects/[id]/matter-closure-actions.ts` and `matter-reopen-actions.ts` — legal-vertical close/reopen surface (cross-link `60-verticals/legal-za.md`).
- `frontend/lib/types/project.ts:11` — `Project` type; `ProjectStatus` at `:6`; `MyWorkTaskItem`/`MyWorkTimeSummary` at `:95` `→ A2-frontend-map.md:337-340`.
- `frontend/lib/types/member.ts:3` — `ProjectMember` and `ProjectRole` `→ A2-frontend-map.md:346`.
- `frontend/components/projects/` — list table + create dialog `→ A2-frontend-map.md:433`.
- `frontend/components/dashboard/ProjectHealthWidget` — RAG-style health indicator (ADR-045) `→ A2-frontend-map.md:431`.

## Domain events

**Emitted:**

- `ProjectCreatedEvent` — `ProjectService.java:271`.
- `ProjectUpdatedEvent` — `ProjectService.java:416`.
- `ProjectCompletedEvent` — `ProjectService.java:521`. Listed in A1's per-package event table `→ A1-backend-map.md:453`.
- `ProjectArchivedEvent` — `ProjectService.java:570`. `→ A1-backend-map.md:453`.
- `ProjectReopenedEvent` — `ProjectService.java:618`. `→ A1-backend-map.md:453`.
- `MemberAddedToProjectEvent` — `ProjectMemberService.java:87`. Owned by the `member/` package per `→ A1-backend-map.md:459`.
- `BudgetThresholdEvent` — **not emitted from this module**; published by `BudgetCheckService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetCheckService.java:137` after time-entry mutations cross a project budget threshold. Anchored here because the event carries `projectId` and bridges into `invoicing` and `automation` (`TriggerType.BUDGET_THRESHOLD_REACHED` `→ backend/.../automation/TriggerTypeMapping.java:34`). Glossary cross-ref `→ kazi-architecture/glossary.md:65`.
- `RecurringProjectCreatedEvent` — emitted by `ProjectScheduleService` (`projecttemplate/`) — owned by `30-modules/project-templates.md`. `→ A1-backend-map.md:466`.

All Project lifecycle events are members of the sealed `DomainEvent` hierarchy at `backend/.../event/DomainEvent.java:18`.

**Consumed:** none inside `project/` itself. Listeners live in `notification/NotificationService` (`MemberAddedToProjectEvent`, `ProjectCompletedEvent`, `ProjectArchivedEvent`, `BudgetThresholdEvent` — `→ A1-backend-map.md:475`) and the automation engine for trigger evaluation (`TRIGGER_TYPE = PROJECT_STATUS_CHANGED, BUDGET_THRESHOLD_REACHED` per `_discovery/A7-vocabulary.md:272`).

## Cross-cutting touchpoints

### Capability gate: `PROJECT_MANAGEMENT`

Anchored at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:10`. All write endpoints on `ProjectController` carry `@RequiresCapability("PROJECT_MANAGEMENT")` (see REST table). Read endpoints rely on per-project access via `ProjectAccessService` rather than a capability annotation. The capability is enforced by `CapabilityAuthorizationManager` `→ A6-cross-cutting.md` §2 (line 113) — see `30-modules/identity-access.md` for the wider mechanism.

### Per-project access control: `ProjectAccessService`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectAccessService.java:13` is the access seam used **inside the service layer** — not on the controller. It returns a `ProjectAccess` record with five booleans (`canView, canEdit, canManageMembers, canDelete, projectRole`):

- `org:owner` — full access on any project that exists in the tenant (`:26-32`).
- `org:admin` — view/edit/manage-members on any tenant project; cannot delete (`:34-40`).
- `org:member` — access only if a `ProjectMember` row exists; `PROJECT_LEAD` role grants edit + manage-members, otherwise view-only (`:42-51`).

Two helpers wrap the check with the canonical exception pattern: `requireViewAccess` throws `ResourceNotFoundException` on miss (security-by-obscurity — `:58-64`); `requireEditAccess` throws `ForbiddenException` after a successful view check (`:71-78`). `ProjectService` invokes these on every read/write entry point — anchored examples at `ProjectService.java:137, :344, :427, :472, :549, :594`.

This is the canonical "tenant member can be on the org but not on a project" gate. The capability gate (`PROJECT_MANAGEMENT`) controls *whether the org permits this kind of action at all*; `ProjectAccessService` controls *whether this actor can act on this specific project*.

### Audit on lifecycle transitions

Every status transition (`complete`, `archive`, `reopen`, `closeMatter`) writes an audit event in-transaction via `AuditService.log(...)`. Same pattern as the rest of the codebase per `_discovery/A6-cross-cutting.md` §3. `Project.update(...)` and `setReferenceNumber/setPriority/setWorkType` bump `updatedAt` (`Project.java:228, :207, :216, :225`) but do not, on their own, emit a domain event — only the lifecycle transitions do.

### ScopedValue context: project ID is **not** bound

Verified against `→ backend/.../multitenancy/RequestScopes.java:23` — the carriers are `TENANT_ID, MEMBER_ID, ORG_ROLE, ORG_ID, CUSTOMER_ID, PORTAL_CONTACT_ID, AUTOMATION_EXECUTION_ID, CAPABILITIES, GROUPS`. There is no `PROJECT_ID` ScopedValue. `CUSTOMER_ID` and `PORTAL_CONTACT_ID` are bound by the portal filter chain so portal endpoints are auto-scoped to their customer, but staff-side project access is checked **per call** via `ProjectAccessService.requireViewAccess(projectId, actor)`. Confirmed in `_discovery/A6-cross-cutting.md` §1 (`RequestScopes.java:23` carrier list) — no project carrier exists.

### Optimistic locking on lifecycle transitions

`Project.version` (`Project.java:41-43`) is a Hibernate `@Version` column added in Phase 67 (Epic 489B) to prevent concurrent matter closures from both succeeding and duplicating downstream effects (closure-log rows, notifications, audit events). Concurrent writers get `OptimisticLockException` → HTTP 409 via the standard exception mapping. The column has `NOT NULL DEFAULT 0`; `version` is left null on new instances so Spring Data JPA's `isNew()` check still fires INSERT.

### Setup status: computed, not persisted (ADR-066)

`GET /api/projects/{id}/setup-status` returns a freshly-computed `ProjectSetupStatus` (`setupstatus/ProjectSetupStatusService.java:45,81`). There is no `setup_status` table — readiness is recomputed on every call against the source-of-truth tables (rates, budgets, project members, custom-field values). Same pattern is used by `CustomerReadiness`, `DocumentGenerationReadiness`, and `UnbilledTimeSummary` `→ A1-backend-map.md:54`.

### Read-only when archived

`Project.isReadOnly()` (`Project.java:323`) returns true iff status is `ARCHIVED`. Mutation paths consult this; archived projects block edits while remaining queryable.

## Vertical specifics

- **Terminology overlay.** UI rename: Project → "Matter" (legal-za) and Project → "Engagement" (accounting-za) `→ frontend/lib/terminology-map.ts:21,45` (anchored via glossary `→ kazi-architecture/glossary.md:215, :119, :168`). Backend uses **Project** uniformly except in `verticals/legal/closure/MatterClosure*` services. Renderer wires through `TerminologyProvider` per `_discovery/A6-cross-cutting.md` §5.
- **`Project.workType`** (`Project.java:114`) — free-text classification (length 50). Vertical packs may seed values: glossary entry `→ kazi-architecture/glossary.md:286` describes "e.g. Audit, Litigation". The field has no enum; the convention is per-vertical pack content (see `30-modules/packs.md`).
- **`CLOSED` state is legal-only.** `ProjectStatus.CLOSED` (`ProjectStatus.java:11`) is reachable only via `Project.closeMatter(...)` (`Project.java:299`) which is invoked by `MatterClosureService` (legal vertical). Non-legal tenants never reach CLOSED because the only call sites are module-guarded `→ verticals/legal/closure/`. CLOSED is non-terminal — `reopenMatter` (`Project.java:315`) goes back to ACTIVE while preserving the original `retentionClockStartedAt` (ADR-249, `Project.java:310`).
- **Capabilities `CLOSE_MATTER` / `OVERRIDE_MATTER_CLOSURE`** `→ Capability.java` (anchored via glossary `:67`) exist in the universal enum but are only granted by legal-vertical seeded roles.
- Vertical fields (e.g. `AdversePartyLink → Project` for legal conflict checks `→ kazi-architecture/glossary.md:37`) are owned by their vertical packages, not by `project/`.

## Active ADRs

- **ADR-018** (document-scope-model) — `Document.scope = PROJECT | CUSTOMER`, anchoring documents to projects vs customers `→ adr/ADR-018-document-scope-model.md`. Cross-link `30-modules/documents-templates.md`.
- **ADR-045** (project-health-scoring) — discrete rule-based RAG algorithm; surfaced via `DashboardController`'s `/api/projects/{id}/health` and `/api/dashboard/project-health` `→ A1-backend-map.md:401`. Owns `HealthStatus` enum `→ backend/.../dashboard/HealthStatus.java:4`.
- **ADR-060** (lifecycle-status-core-field) — `ProjectStatus` is a first-class `@Column` on the entity (not derived, not in a side table). Same shape as `Customer.lifecycleStatus`, `Invoice.status`, etc.
- **ADR-066** (computed-status-over-persisted) — `ProjectSetupStatus` is computed on every request, **not** event-synchronised into a `setup_status` table. ADR-066 lists the eight mutation paths a persisted approach would have to listen to (`adr/ADR-066-computed-status-over-persisted.md:31`) and rejects that complexity. Note: this ADR governs `setup` status (readiness) only — the lifecycle `ProjectStatus` field IS persisted per ADR-060.
- **ADR-067** (entity-detail-page-action-surface) — `projects/[id]/page.tsx` is the action hub for the project; tabs (tasks, docs, time, expenses, budget) hang off it rather than each living on a separate top-level page.
- **ADR-249** (retention-clock-starts-on-closure) — `Project.retentionClockStartedAt` is stamped on the first transition that anchors retention (`complete` for non-legal, `closeMatter` for legal — `Project.java:259, :303`) and is **never** overwritten on reopen. The retention concern itself is owned by `customer-lifecycle.md`.

All six are Active per `90-adr-index.md` (lines 113-114, 133, 160, 385, 394).

## Key flows

- `50-flows/matter-to-cash.md` — project lifecycle from intake through closure, time/expense capture, billing.
- `50-flows/proposal-to-engagement-to-billing.md` — proposal acceptance → project creation → engagement billing.
- `50-flows/automation-trigger-to-action.md` — covers the `PROJECT_STATUS_CHANGED` and `BUDGET_THRESHOLD_REACHED` automation triggers that consume this module's events.

## Open questions / known fragility

- **`ProjectMember` ownership ambiguity.** The entity lives in `member/` (`backend/.../member/ProjectMember.java:14`) alongside `Member` and the staff-identity machinery, but is consumed almost exclusively by the `projects` bounded context for access control. `_discovery/A1-backend-map.md:38` lists it under the `member` package. `30-modules/identity-access.md` lists it as "co-owned conceptually with `projects` but the table lives in this package." This page anchors it under projects for access-control purposes; the package layout disagrees. Resolving requires either moving the class to `project/` or formally ceding the ownership claim — flagged for a follow-up pass.
- **Computed-vs-persisted boundary on Project.** ADR-066 governs `ProjectSetupStatus` (computed). ADR-060 governs `ProjectStatus` (persisted lifecycle field). It is easy to misread the pair — confirm against the entity: `Project.status` is `@Column(name = "status", nullable = false)` (`Project.java:52-53`) — persisted; `ProjectSetupStatus` is a service-returned record assembled from rates/budget/members/fields with no JPA mapping (`setupstatus/ProjectSetupStatusService.java:81`).
- **`MatterClosureService` ownership.** The CLOSED transition uses `Project.closeMatter(...)` on this aggregate but the orchestration (closure gates, closure log) lives in `verticals/legal/closure/`. The `closeMatter(UUID)` signature is the 489A-minimal stub; the full `closeMatter(UUID, ClosureRequest)` service hook lives outside this module (`Project.java:295-298` javadoc).
- **`workType` is free-text.** No enum, no validation. Packs may seed values, but agents adding work-type-conditional logic should not assume a closed set.
- **`PATCH /{id}/close` is not on `ProjectController`.** A1 lists it (`_discovery/A1-backend-map.md:388`) but the actual REST surface for legal closure is the matter-closure controller in `verticals/legal/closure/`; see `60-verticals/legal-za.md`. The list-endpoint count of ~18 includes endpoints that have since been split or relocated — the table above reflects the controller as it currently exists.
