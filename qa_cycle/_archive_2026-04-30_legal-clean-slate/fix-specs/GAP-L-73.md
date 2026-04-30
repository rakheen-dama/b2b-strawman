# Fix Spec: GAP-L-73 — Portal matter status badge stays "ACTIVE" after firm-side close

## Problem (verbatim from QA evidence)

Day 61 cycle-1 verify (2026-04-25 ~21:42 SAST,
`qa_cycle/checkpoint-results/day-61.md` §"Day 61 Re-Verify — Cycle 1"
Step 61.2 "Matter detail status"). Sipho's portal session at
`/projects/e788a51b-3a73-…` renders matter header status badge
**"ACTIVE"** even though firm-side + DB confirm:

- `tenant_5039f2d497cf.projects.id=e788a51b-…`,
  `status='CLOSED'`,
  `closed_at='2026-04-25 20:44:20'`,
  `retention_clock_started_at='2026-04-25 20:44:20'`.

Evidence:
`qa_cycle/checkpoint-results/day-61-cycle1-portal-matter-detail.yml`
line 61.

**Severity LOW** — matter is read-only-effective on portal anyway
(all 9 Sipho-facing tasks `CANCELLED`, no portal actions available),
but the "ACTIVE" badge is confusing for the client who will think
the matter is still in progress while the firm has actually concluded
it.

**Does NOT block** any Day 61 / 75 / 85 / 88 / 90 scenario step or
exit checkpoint — Sipho can still navigate, see invoices, see trust
ledger, and (post L-74) see the closure pack. Pure cosmetic gap.

## Root Cause (grep-confirmed)

The portal `/projects/{id}` matter detail endpoint reads from the
denormalised projection `portal.portal_projects.status`, which is
populated by the `PortalEventHandler` listener chain. The status
column is updated only on `ProjectUpdatedEvent` —
`MatterClosureService` publishes a `MatterClosedEvent` instead, so
the projection never sees the close and `portal_projects.status`
stays at its initial "ACTIVE" value forever after closure.

### Read path (where "ACTIVE" comes from)

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalProjectController.java`
  line 48-58 — `getProjectDetail(@PathVariable UUID id)` calls
  `portalReadModelService.getProjectDetail(id, customerId, orgId)`,
  builds `PortalProjectDetailResponse` with `project.status()`
  (line 57).
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalReadModelService.java`
  line 68-72 — `getProjectDetail(...)` delegates to
  `readModelRepository.findProjectDetail(projectId, customerId, orgId)`
  which returns a `PortalProjectView`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/repository/PortalReadModelRepository.java`
  line 324-336 (`findProjectsByCustomer`) and line 338+
  (`findProjectDetail`) both run
  `SELECT id, org_id, customer_id, name, status, … FROM
  portal.portal_projects WHERE …` — i.e. status comes straight from
  the projection table column, no JOIN to `projects.status`.

### Write path (when projection is updated — and isn't)

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java`
  line 126-156 — `onCustomerProjectLinked(CustomerProjectLinkedEvent)`
  upserts `portal_projects` with hardcoded `"ACTIVE"` (line 146)
  on first link. This is the source of the stale value.
- Line 178-201 — `onProjectUpdated(ProjectUpdatedEvent event)` is the
  ONLY listener that calls
  `readModelRepo.updatePortalProjectDetails(projectId, customerId,
  event.getName(), status, event.getDescription())` with
  `status = event.getStatus() != null ? event.getStatus() : "ACTIVE"`
  (line 187).
- `ProjectUpdatedEvent` is published only by
  `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java`
  line 373 — i.e. firm-side `ProjectService.update(...)` calls.

### The disconnect

`MatterClosureService.performClose` (line 189-247) transitions the
project status via `project.closeMatter(actingMemberId)` +
`projectRepository.save(project)` (lines 205-206) and publishes a
`MatterClosedEvent` (line 237-239) — but **never** publishes a
`ProjectUpdatedEvent`. Same for `MatterClosureService.reopen`
(publishes `MatterReopenedEvent`, not `ProjectUpdatedEvent`).

Result: the portal projection stays at the initial "ACTIVE" value
written when the customer was first linked to the project.
`MatterClosureNotificationHandler` (the only existing
`MatterClosedEvent` listener) only fires firm-side admin
notifications — it doesn't touch the portal projection.

## Chosen Fix — Add a portal-projection update to `MatterClosureNotificationHandler`

Smallest, most targeted shape: extend the existing
`MatterClosureNotificationHandler` (the `@TransactionalEventListener`
on `MatterClosedEvent`) to also call
`readModelRepo.updatePortalProjectDetails(...)` with the new status,
so any portal projections for the customers linked to this project
get the status flip.

Considered alternatives:

- **Alt A — Publish `ProjectUpdatedEvent` from `MatterClosureService`
  in addition to `MatterClosedEvent`.** Rejected: introduces a
  semantic conflation (closure is not a generic project update), and
  the existing `ProjectUpdatedEvent` payload doesn't naturally
  include closure-specific fields (closure log id, retention anchor,
  etc.) that future portal-projection refinements will likely want.
- **Alt B — Add a new `PortalClosureProjectionHandler` listener
  (separate class) on `MatterClosedEvent`.** Considered, but
  `MatterClosureNotificationHandler` already has the AFTER_COMMIT +
  REQUIRES_NEW + try/catch idioms wired correctly for portal-affecting
  side effects; adding a second listener for one
  `updatePortalProjectDetails` call is over-engineering. Choose the
  smallest expansion.

Resolve the project name + description inside the handler (the
existing event doesn't carry them) by re-reading
`projectRepository.findById(event.projectId())` — same pattern
`PortalEventHandler.onCustomerProjectLinked` already uses (line
133). On reopen, mirror the same pattern in
`MatterClosureNotificationHandler.onMatterReopened` so the badge
correctly flips back to "ACTIVE".

## Fix — step-by-step

### 1. Inject `ProjectRepository` + `PortalReadModelRepository` into `MatterClosureNotificationHandler`

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/event/MatterClosureNotificationHandler.java`:

```java
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;

private final NotificationService notificationService;
private final ProjectRepository projectRepository;
private final PortalReadModelRepository portalReadModelRepository;

public MatterClosureNotificationHandler(
    NotificationService notificationService,
    ProjectRepository projectRepository,
    PortalReadModelRepository portalReadModelRepository) {
  this.notificationService = notificationService;
  this.projectRepository = projectRepository;
  this.portalReadModelRepository = portalReadModelRepository;
}
```

### 2. In `onMatterClosed` — also flip the portal projection status

After the existing `notificationService.notifyAdminsAndOwners(...)`
call inside the try-block (line 39-40):

```java
syncPortalProjectionStatus(event.projectId(), "CLOSED");
```

Add the helper at the bottom of the class:

```java
private void syncPortalProjectionStatus(UUID projectId, String status) {
  try {
    var project = projectRepository.findById(projectId).orElse(null);
    if (project == null) {
      log.warn("Project {} not found when syncing portal projection status={}",
          projectId, status);
      return;
    }
    // Update every portal_projects row that mirrors this project
    // (one per customer linked to the project — mirrors
    // PortalEventHandler.onProjectUpdated row-by-row pattern).
    var customerIds = portalReadModelRepository.findCustomerIdsByProjectId(
        projectId, RequestScopes.requireOrgId());
    for (var customerId : customerIds) {
      portalReadModelRepository.updatePortalProjectDetails(
          projectId, customerId, project.getName(), status, project.getDescription());
    }
  } catch (Exception e) {
    log.warn(
        "Failed to sync portal projection status for project={}, status={}",
        projectId, status, e);
  }
}
```

(`RequestScopes.requireOrgId()` is the canonical org-id accessor —
see `backend/CLAUDE.md` "Multitenancy" section. The existing
`PortalEventHandler` uses `event.getOrgId()` because its events are
the portal-domain `PortalDomainEvent` shape; the
`MatterClosedEvent` doesn't carry `orgId`, so we fall back to
the request-scoped value — which is bound by the time the
AFTER_COMMIT listener fires on the publisher thread, per the
existing handler's Javadoc.)

### 3. In `onMatterReopened` — flip back to "ACTIVE"

After the existing `notificationService.notifyAdminsAndOwners(...)`
in `onMatterReopened` (line 53-58):

```java
syncPortalProjectionStatus(event.projectId(), "ACTIVE");
```

So a closed→reopened matter goes CLOSED → ACTIVE on the portal too.

### 4. Test

Extend `MatterClosureNotificationHandlerTest` (or
`MatterClosureServiceIntegrationTest` if it already covers the
notification handler bind-up):

```java
@Test
void close_flipsPortalProjectionStatus_toClosed() {
  // Setup: project + customer + customer_projects link so
  // portal_projects row exists with status='ACTIVE'.
  matterClosureService.close(projectId,
      new ClosureRequest(reason, notes, false, false, null), memberId);

  var portalDetail = portalReadModelService.getProjectDetail(
      projectId, customerId, orgId);
  assertThat(portalDetail.status()).isEqualTo("CLOSED");
}

@Test
void reopen_flipsPortalProjectionStatus_backToActive() {
  // Setup: closed project as above
  matterClosureService.reopen(projectId,
      new ReopenRequest("re-engaged"), memberId);

  var portalDetail = portalReadModelService.getProjectDetail(
      projectId, customerId, orgId);
  assertThat(portalDetail.status()).isEqualTo("ACTIVE");
}
```

## Scope

**Backend-only.** No DB migration, no frontend, no event-shape
change.

**Files modified:**

1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/event/MatterClosureNotificationHandler.java`
   — inject `ProjectRepository` + `PortalReadModelRepository`; add
   `syncPortalProjectionStatus(...)` helper; call it from both
   `onMatterClosed` and `onMatterReopened`.

**Files added:**

2. `backend/src/test/.../verticals/legal/closure/event/` — extension
   to the existing `MatterClosureNotificationHandlerTest` (or a new
   file if absent) covering close → CLOSED + reopen → ACTIVE
   portal-projection status transitions.

**Lines touched (estimate):** ~25 production + ~40 test.

**NEEDS_REBUILD:** true (backend Java change — Spring restart
required).

## Verification (per QA — re-walk after fix lands)

1. Backend rebuild + restart on Keycloak dev stack (port 8080).
2. SQL spot-check on Dlamini matter (already CLOSED from Day 60):
   `SELECT status FROM portal.portal_projects WHERE id =
   'e788a51b-…';`. **Expected before fix**: `'ACTIVE'` (stale).
   **After fix without backfill**: still `'ACTIVE'` (the existing
   row was projected pre-fix; no event fires retroactively). To get
   the existing row updated, EITHER (a) run a one-time data-backfill
   `UPDATE portal.portal_projects SET status = 'CLOSED' WHERE id IN
   (SELECT id FROM tenant_5039f2d497cf.projects WHERE status =
   'CLOSED');` for the verify-cycle, OR (b) close a fresh test
   matter to exercise the new code path end-to-end.
3. Re-load Sipho's portal `/projects/e788a51b-…` matter detail page.
   **Expected**: status badge renders "CLOSED" (or whatever the
   portal frontend's `CLOSED` variant displays — see frontend
   `portal/app/projects/[id]/page.tsx` for the badge mapping; if it
   only knows ACTIVE, that's a separate frontend follow-up).
4. Optional cross-check: reopen the matter firm-side via
   `MatterClosureService.reopen` → portal projection should flip
   back to "ACTIVE" badge.

Note: if the portal frontend `/projects/[id]/page.tsx` doesn't
have a "CLOSED" badge variant, it'll render the raw string
"CLOSED" or fall back to a default styling. That's a portal-frontend
polish task for Sprint 2 (`L-73-followup`); the backend change here
is sufficient to make the data correct.

## Estimated Effort

**S (~1.5 hr)** all-in:

- ~25 min — handler code change (2 listener method updates + 1
  helper + 2 constructor injections).
- ~30 min — tests (close + reopen status assertions).
- ~10 min — restart + SQL backfill on Dlamini matter for QA.
- ~25 min — buffer for any Spring-context wiring quirks or
  test-fixture portal-customer linkage.

Comfortably within the SPEC_READY <2 hr lower bar.

## Sprint 2 follow-up (carved out, NOT in scope here)

- **L-73-followup** — Portal frontend `/projects/[id]/page.tsx`
  status-badge variant for "CLOSED" matters (greyed-out / archived
  styling, "Past" tab grouping in `/projects` list per scenario step
  75.6). Not needed for verify-cycle exit since the backend now
  emits the correct value; frontend just renders whatever it gets.
