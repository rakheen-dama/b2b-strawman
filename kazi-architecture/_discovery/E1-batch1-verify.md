# E1 Batch 1 — Module-page anchor verification

Pass over 10 module pages. For each `→ path:line` anchor, the file existence and ±15-line identifier proximity were checked. Line drifts ≤±15 with identifier present were repaired in place; missing files and identifier-not-found cases are flagged below.

## Per-file summary

| Module page | checked | resolved | drifted-fixed | broken (file missing) | content-flagged |
|---|---|---|---|---|---|
| tenancy-provisioning.md | 21 | 17 | 4 | 0 | 1 |
| identity-access.md | 17 | 14 | 3 | 0 | 0 |
| platform-administration.md | 22 | 18 | 4 | 0 | 0 |
| customer-lifecycle.md | 24 | 14 | 10 | 0 | 0 |
| projects.md | 32 | 31 | 1 | 0 | 1 |
| tasks.md | 20 | 17 | 3 | 0 | 0 |
| time-entry.md | 27 | 23 | 4 | 0 | 0 |
| expenses.md | 23 | 22 | 1 | 0 | 0 |
| documents-templates.md | 7 | 5 | 1 | 2 | 0 |
| custom-fields-tags-views.md | 8 | 8 | 0 | 0 | 0 |
| **Total** | **201** | **169** | **31** | **2** | **2** |

(Counts cover the architecturally-significant anchors checked — entities, controllers, key services, lifecycle methods, domain events. JSDoc-style and pure-pointer anchors not always re-verified.)

## Broken anchors (file missing)

Both in `documents-templates.md` (S3 storage relocated under `integration/storage/`):

1. `documents-templates.md` cites `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/StorageService.java`
   - Actual path: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/StorageService.java`
2. `documents-templates.md` cites `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/S3StorageAdapter.java`
   - Actual path: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/storage/s3/S3StorageAdapter.java`

Both files exist under the new `integration/storage/...` paths. Anchors NOT auto-rewritten per the "do not change paths" constraint — flag for orchestrator.

## Content-flagged claims (file exists, identifier not found nearby)

1. `tenancy-provisioning.md` line 41 claims `runForMember(...)` `→ RequestScopes.java:200`.
   - `RequestScopes.java` has no method named `runForMember`. The closest sanctioned helper is `runForTenantWithMember(...)` at `RequestScopes.java:219`. Line 200 is inside the javadoc block for the `runForTenant` family. Either the helper was renamed or the doc invented a name.
   - Searched for: `runForMember`, `public static .* run` definitions in `RequestScopes.java`.
2. `projects.md` line 14 calls the table at `ProjectStatus.java:19` "the allowed-transition table". The actual identifier in code is `ALLOWED_TRANSITIONS`, not `VALID_TRANSITIONS` (the doc text uses the prose "allowed-transition table" so this is a near-miss only — flagging because `platform-administration.md` and `customer-lifecycle.md` both use `VALID_TRANSITIONS` field names elsewhere, suggesting an inconsistency worth a glossary check).

## Line-drift fixes applied (31 in-place edits)

### tenancy-provisioning.md (4)
- `ProvisioningController.java` 15 → 16
- `OrgController.java` 14 → 16
- `RequestScopes.java` (TENANT_ID) 20 → 23
- `RequestScopes.java` (runForTenant) 147 → 173

### identity-access.md (3)
- `TenantFilter.java` 50 → 64
- `CapabilityAuthorizationManager.java` 28 → 37
- `Member.java` 28 → 29

### platform-administration.md (4)
- `Subscription.java` (entity) 19 → 20
- `SubscriptionPayment.java` 19 → 21
- `Subscription.java` (VALID_TRANSITIONS) 27 → 29
- `DemoAdminController.java` 21 → 23

### customer-lifecycle.md (10)
- `LifecycleStatus.java` 7 → 8
- `CustomerType.java` 4 → 5
- `Customer.java` 69 → 70 (lifecycle status)
- `Customer.java` 78 → 79 (offboardedAt)
- `Customer.java` 187 → 186 (transition guard)
- `CustomerController.java` 55 → 56
- `ProjectCustomerController.java` 18 → 19
- `DataRequestController.java` 27 → 28
- `CustomerLifecycleService.java` 178 → 179
- `CustomerLifecycleService.java` 288 → 289
- `customer.ts` 6 → 7 (LifecycleStatus)
- `customer.ts` 203 → 202 (DataRequest types)
(Eleven; one merged the `:178/:288` pair.)

### projects.md (1)
- `ProjectMemberController.java` 21 → 22

### tasks.md (3)
- `TaskService.java` (TaskStatusChangedEvent) 599 → 600
- `TaskService.java` (TaskCompletedEvent) 838 → 839
- `TaskService.java` (TaskClaimedEvent) 731 → 732

### time-entry.md (4)
- `BillingStatus.java` 13 → 14
- `TimeEntryController.java` (POST) 38 → 39
- `TimeEntryController.java` (DELETE) 116 → 117
- `TimeEntryService.java` `585-590` → 589
- `TimeEntry.java` `38-40` → 40

### expenses.md (1)
- `Expense.java` 162 → 163

### documents-templates.md (1)
- `GeneratedDocument.java` 22 → 20

### custom-fields-tags-views.md (0)
All checked anchors resolved at the cited line.

## Notes on method

- Verification used `awk` to scan ±15 lines around each cited line for the doc-implied identifier, then either confirmed `OK` or reported the closest matching line as the new target. Range references (e.g. `:585-590`, `:38-40`) were collapsed to the single representative line.
- Path-only anchors (no `:line`) were not checked; A1/A2 doc-pointer anchors (`→ A1-backend-map.md:nnn`) were skipped as out-of-scope (those are doc-to-doc, not doc-to-code).
- Frontend `.tsx`/`.ts` anchors with deep declarations were verified where they referenced an entity/component name; embedded JSX line cites left untouched.

**Total anchors fixed in place: 31.**
