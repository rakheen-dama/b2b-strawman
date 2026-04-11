# Fix Spec: GAP-S5-03 — project.customer_id NULL on "New from Template" flow

## Priority
HIGH — CASCADING_FIX_FIRST (unblocks GAP-S5-02 court-date crash and GAP-S5-04 adverse-party link dialog)

## Problem
Matters created via the "New from Template" dialog leave `projects.customer_id` NULL. The customer
link is only stored in the `customer_projects` join table. Downstream features that read
`project.customerId` (Court Calendar, Link-Adverse-Party dialog, likely billing) silently break.
Evidence: Session 5 step 5.17.2 DB query confirmed NULL for both Sipho and Lerato matters. The
GAP-S5-02 NPE at `CourtCalendarService.toResponse:513` is a direct consequence of this.

## Root Cause (confirmed via grep)
`ProjectTemplateService.instantiateTemplate()` creates the Project entity and persists it, then
only inserts the `CustomerProject` join row — the single-owner FK is never set.

Files:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java:520` —
  `var project = new Project(resolvedName, description, memberId);` — Project constructor takes
  (name, description, createdBy) only. `customerId` is never assigned.
- Same file, lines 524-527 — only `customerProjectRepository.save(new CustomerProject(...))` is
  written; `project.setCustomerId(customer.getId())` is missing.
- Same file, `instantiateFromTemplate()` (line 627) — same defect for scheduler-driven
  instantiation (RecurringScheduleService). Fix must apply to both code paths.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:115` — `setCustomerId(UUID)`
  setter exists and is public.

## Fix Steps
1. In `ProjectTemplateService.instantiateTemplate()` (around line 521), after
   `project = projectRepository.save(project)`, add:
   ```java
   if (customer != null) {
     project.setCustomerId(customer.getId());
     project = projectRepository.save(project);
   }
   ```
   Place this BEFORE the existing `customerProjectRepository.save(...)` call so the ordering is
   clear (single-owner FK first, join-table second).
2. Apply the identical change inside `instantiateFromTemplate()` (around line 635) for the
   scheduler code path — same pattern, same location.
3. Add a data-repair one-liner in `V*__backfill_project_customer_id.sql` (new tenant migration)
   that populates the FK for any existing projects with a single linked customer:
   ```sql
   UPDATE projects p
   SET customer_id = cp.customer_id
   FROM customer_projects cp
   WHERE p.id = cp.project_id
     AND p.customer_id IS NULL
     AND (SELECT COUNT(*) FROM customer_projects WHERE project_id = p.id) = 1;
   ```
   This repairs the Mathebula test tenant without re-seeding.
4. Extend `ProjectTemplateServiceTest` with a case that asserts `project.getCustomerId()` equals
   the selected customer after instantiation. Use `TestCustomerFactory.createActiveCustomer(...)`.

## Scope
- Backend only (Java + one tenant SQL migration)
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplateService.java`
  - `backend/src/test/java/.../projecttemplate/ProjectTemplateServiceTest.java` (add assertion)
- Files to create:
  - `backend/src/main/resources/db/migration/tenant/V{next}__backfill_project_customer_id.sql`
- Migration needed: yes (data repair, tenant schema)

## Verification
Re-run Session 5 steps 5.14–5.17 (create matter from Litigation template). Then DB query:
```sql
SELECT id, name, customer_id FROM projects WHERE name LIKE '%RAF%' OR name LIKE '%Civil dispute%';
```
Both rows should now show non-NULL `customer_id`. Then re-run Session 5 step 5.26 (create court
date) — should PASS (GAP-S5-02 auto-unblocks). Then re-run 5.30 (Link Adverse Party) — Customer
select should populate (GAP-S5-04 auto-unblocks).

## Estimated Effort
S (< 30 min)
