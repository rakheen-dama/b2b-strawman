# Remaining Gaps — QA Phase 55 (Legal Foundations)

> QA cycle ran 2026-04-04 on branch `bugfix_cycle_2026-04-04`.
> 12 of 14 gaps were fixed and verified (PRs #909–#919).
> 2 gaps deferred as WONT_FIX during the bugfix cycle — documented below for future resolution.

---

## GAP-P55-001: `DemoProvisionService.addMember` fails in Keycloak 26.5

**Severity**: Major (blocks demo provisioning only — production onboarding unaffected)

### Symptom

`POST /organizations/{orgId}/members` returns **400 "User does not exist"** for users that exist in the realm. Affects `DemoProvisionService` and any code path calling `KeycloakAdminClient.addMember()`.

### Root Cause

**Not an endpoint change — a request body format change in KC 26.x.**

Our code sends a JSON object:
```java
// KeycloakAdminClient.java:106-115
restClient.post()
    .uri("/organizations/{orgId}/members", orgId)
    .contentType(MediaType.APPLICATION_JSON)
    .body(Map.of("id", userId))   // <-- sends {"id": "uuid"}
```

Keycloak 26.x changed the expected body to a **plain UUID string**. From the [KC REST API docs](https://www.keycloak.org/docs-api/latest/rest-api/index.html#_organizations):

> "Payload should contain only id of the user to be added to the organization (UUID with or without quotes). Surrounding whitespace characters will be trimmed."

KC parses the entire body as a UUID lookup. When it receives `{"id": "..."}`, it fails to match any user because it treats the whole JSON blob as the UUID.

### Fix

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java`

Change `addMember()` to send the raw UUID string instead of a JSON object:

```java
// Before (broken in KC 26.x):
.body(Map.of("id", userId))

// After (correct for KC 26.x):
.body("\"" + userId + "\"")
```

Content-Type stays `application/json`. Response codes: **201** = success, **409** = already a member.

### Scope

- **Files to modify**: `KeycloakAdminClient.java` (1 line change in `addMember()`)
- **Migration**: None
- **Risk**: Low — only changes the serialization format, same endpoint
- **Effort**: S (< 30 min) now that root cause is known

### Alternative Endpoints (not needed)

| Endpoint | Purpose | When to use |
|----------|---------|-------------|
| `POST .../members` (plain UUID body) | Direct membership grant | Programmatic provisioning (our case) |
| `POST .../members/invite-existing-user` | Sends invitation email | When user should confirm via email |
| `POST .../members/invite-user` | Email-based invite/registration | When user may not exist yet |

### Verification

```bash
# After fix, demo provisioning should complete without 400 errors:
curl -sf -X POST "http://localhost:8180/admin/realms/docteams/organizations/{orgId}/members" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "\"$USER_ID\""
# Expected: 201 Created
```

---

## GAP-P55-013: No manual trigger for court date reminder job

**Severity**: Minor (QA convenience only — job logic verified correct via code review)

### Symptom

`CourtDateReminderJob` runs on cron (6 AM daily) with no way to trigger it manually. During QA, we couldn't verify that reminder notifications are generated because the job hadn't run since the test data was seeded.

### Why It Was Deferred

- The job logic was verified correct via code review during QA
- GAP-P55-002 (dynamic prescription status) addressed the primary user-visible impact
- Adding an internal endpoint is a convenience enhancement, not a bug fix

### Suggested Fix

Add an internal endpoint to trigger the job on demand (guarded by platform-admin or internal-only access):

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/legal/courtcalendar/CourtDateReminderJob.java`

```java
// Add a controller or wire into existing internal API:
// POST /internal/jobs/court-date-reminders/trigger
// Response: 200 with count of reminders generated
```

### Scope

- **Files to modify**: `CourtDateReminderJob.java` (extract logic to callable method), new controller or add to existing internal controller
- **Migration**: None
- **Effort**: S (< 30 min)

### Verification

```bash
curl -sf -X POST "http://localhost:8080/internal/jobs/court-date-reminders/trigger" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 with reminder count
# Then check: GET /api/notifications?type=COURT_DATE_REMINDER
```

---

## GAP-P55-014b: "Customer:" label persists in Overview tab for legal tenants

**Severity**: Cosmetic (follow-up to GAP-P55-014, which fixed the matter detail header only)

### Symptom

The Overview tab card still displays "Customer:" instead of "Client:" for legal-vertical tenants. GAP-P55-014 (PR #913) fixed the matter detail header in `projects/[id]/page.tsx`, but the Overview tab components were not in scope.

### Affected Files

- `frontend/components/overview-health-header.tsx`
- `frontend/components/overview-tab.tsx`

(Grep for hardcoded `"Customer"` strings in these files.)

### Fix

Replace hardcoded `"Customer:"` labels with `<TerminologyText template="{Customer}:" />` — same pattern used in the PR #913 fix.

### Scope

- **Files to modify**: 2 frontend components (see above)
- **Migration**: None
- **Effort**: S (< 15 min)
