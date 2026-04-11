# Fix Spec: GAP-S5-02 — Court date creation NPE on null project.customer_id

## Priority
HIGH — depends on GAP-S5-03 but ALSO deserves a defensive null-guard regardless, since
multi-customer matters (legitimate future case) will hit the same crash.

## Problem
`POST /api/court-dates` crashes with `InvalidDataAccessApiUsageException: The given id must not be
null` whenever the linked project has `customer_id = NULL`. The transaction rolls back, no court
date is persisted, and the UI shows a generic "An unexpected error occurred". Blocks Session 5
step 5.26–5.28 entirely.

## Root Cause (confirmed via grep)
Files:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarService.java:513`
  — `customerRepository.findById(entity.getCustomerId())` is called unconditionally. When
  `entity.getCustomerId()` is null, Spring Data throws `InvalidDataAccessApiUsageException`.
- Same file, line 170 — `courtDate` is constructed with `project.getCustomerId()`, which is
  currently NULL for all template-created matters (GAP-S5-03).

## Fix Steps
1. In `CourtCalendarService.toResponse(CourtDate entity)` (around line 504), null-guard the
   customer lookup:
   ```java
   private CourtDateResponse toResponse(CourtDate entity) {
     String projectName = null;
     String customerName = null;

     var project = projectRepository.findById(entity.getProjectId()).orElse(null);
     if (project != null) {
       projectName = project.getName();
     }

     if (entity.getCustomerId() != null) {
       var customer = customerRepository.findById(entity.getCustomerId()).orElse(null);
       if (customer != null) {
         customerName = customer.getName();
       }
     }

     return buildResponse(entity, projectName, customerName);
   }
   ```
2. Also audit the batch `toResponse(entity, projectMap, customerMap)` overload at line 521 — it
   already tolerates `null` via map lookup, so no change needed, but verify with a unit test.
3. Add a targeted test in `CourtCalendarServiceTest` (or a new one) that creates a court date
   against a matter with no `customer_id` and asserts the response comes back with
   `customerName = null` and no exception.

## Scope
- Backend only
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarService.java`
  - `backend/src/test/java/.../courtcalendar/CourtCalendarServiceTest.java` (add test)
- Migration needed: no

## Verification
After GAP-S5-03 is fixed, this NPE should stop happening naturally. This defensive fix is still
required in case (a) GAP-S5-03 fix misses some path, or (b) a future multi-customer matter is
introduced. Re-run Session 5 step 5.26 — court date creates, appears in list, status SCHEDULED.

## Estimated Effort
S (< 15 min)
