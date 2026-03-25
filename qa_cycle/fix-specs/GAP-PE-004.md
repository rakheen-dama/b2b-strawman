# Fix Spec: GAP-PE-004 — Portal read model not auto-populated on project creation

## Problem
The `portal.portal_projects` table is empty despite 7+ projects existing in the tenant schema with customer links. The portal project list works (it queries the tenant schema directly via `PortalQueryService`), but project detail returns 404 because it queries the empty portal read model via `PortalReadModelService`. A manual resync via `POST /internal/portal/resync/{orgId}` fixes the issue, but projects should be auto-synced on creation.

Evidence from QA Cycle 2 (T3.2.1): "404 error — No project found with id ... Root cause: portal.portal_projects read model table was empty."

## Root Cause (confirmed)

The `PortalEventHandler.onCustomerProjectLinked()` correctly handles `CustomerProjectLinkedEvent` to upsert portal projects. However, `ProjectService.createProject()` (`backend/src/main/java/.../project/ProjectService.java`, lines 148-150) creates the `CustomerProject` join record directly via `customerProjectRepository.save()` **without publishing a `CustomerProjectLinkedEvent`**.

Only `CustomerProjectService.linkCustomerToProject()` (line 97-98) publishes the event:
```java
eventPublisher.publishEvent(
    new CustomerProjectLinkedEvent(customerId, projectId, orgId, tenantId));
```

The proposal acceptance flow calls `ProjectService.createProject()` (which sets `customerId` and creates the join record silently), not `CustomerProjectService.linkCustomerToProject()`. So no `CustomerProjectLinkedEvent` is ever fired, and `PortalEventHandler` never learns about the new project.

The same issue exists in `ProjectTemplateService.instantiateTemplate()` if it delegates to `ProjectService.createProject()` with a `customerId`.

## Fix

**Option A (minimal, recommended):** Add event publishing to `ProjectService.createProject()` when a `customerId` is provided.

After the `customerProjectRepository.save()` call in `ProjectService.createProject()` (around line 149), add:
```java
if (customerId != null) {
    customerProjectRepository.save(new CustomerProject(customerId, project.getId(), createdBy));

    // Publish event for portal read model sync
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerProjectLinkedEvent(customerId, project.getId(), orgId, tenantId));
}
```

Also add the same event publishing in `ProjectService.updateProject()` when a new customer link is created (around line 243):
```java
if (newCustomerId != null
    && !customerProjectRepository.existsByCustomerIdAndProjectId(newCustomerId, id)) {
    customerProjectRepository.save(new CustomerProject(newCustomerId, id, actor.memberId()));

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new CustomerProjectLinkedEvent(newCustomerId, id, orgId, tenantId));
}
```

## Scope
Backend only
Files to modify:
- `backend/src/main/java/.../project/ProjectService.java` — publish `CustomerProjectLinkedEvent` in `createProject()` and `updateProject()`
Files to verify:
- `backend/src/main/java/.../customerbackend/handler/PortalEventHandler.java` — confirm `onCustomerProjectLinked` already handles the event correctly (it does)
Migration needed: no

## Verification
1. Create a new proposal, send it, accept it via portal API
2. Check `portal.portal_projects` table — the auto-created project should appear WITHOUT manual resync
3. Navigate to the project detail in the portal — should load successfully (no 404)
4. Re-run T3.2.1 (project detail) and T4.6.5 (auto-created project visible in portal)

## Estimated Effort
S (< 30 min)
