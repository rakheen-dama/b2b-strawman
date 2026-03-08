# Handover: Phase 39 — Remaining Issues After Seeder Fix

## Branch: `fix/phase39-platform-admin-flow`

## What Was Fixed in This Session

### Bug 1: Seeders query `public` schema (FIXED)
- Created `TenantTransactionHelper` in `multitenancy/` — explicitly sets `search_path` via native query before running seeder logic
- All 7 seeders updated to use it instead of raw `ScopedValue + TransactionTemplate`
- All 4909 backend tests pass

### Bug 2: Tenant resolution fails for Keycloak users (FIXED)
- **Root cause**: `AccessRequestApprovalService.approve()` stored the Keycloak org UUID in `org_schema_mapping.clerk_org_id`, but Keycloak JWTs contain the org **alias** (slug) in the `organization` claim, not the UUID
- JWT example: `organization=[avengers]` — the alias "avengers" is what `ClerkJwtUtils.extractKeycloakOrgId()` returns
- Fix: `approve()` now passes the slug to `provisionTenant()` instead of the KC UUID
- The KC UUID is still stored in `AccessRequest.keycloakOrgId` for `inviteUser()` calls

### Bug 3: MemberFilter used placeholder email (FIXED)
- `MemberFilter.lazyCreateMember()` only extracted real email/name from JWT when `jitProvisioningEnabled=true`
- Fix: Always extract email/name from JWT when available (Keycloak JWTs include them; Clerk JWTs may not, falling back to placeholder)
- Removed unused `jitProvisioningEnabled` field from `MemberFilter`

## What Still Needs Testing

### 1. Clean up stale data before re-testing
The user's DB has stale entries from failed provisioning attempts:
- `public.organizations` has 3 entries (1 failed) — these used the KC UUID, not the slug
- `public.org_schema_mapping` may have UUID-keyed entries
- Tenant schemas from previous attempts may exist

**Cleanup steps:**
```sql
-- Connect to the DB
docker exec -it e2e-postgres psql -U postgres -d app
-- OR for dev: psql -U postgres -d docteams

-- Check current state
SELECT * FROM public.organizations;
SELECT * FROM public.org_schema_mapping;
SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%';

-- Delete stale entries
DELETE FROM public.org_schema_mapping;
DELETE FROM public.organizations;

-- Drop stale tenant schemas
DROP SCHEMA IF EXISTS tenant_d9b590647a21 CASCADE;
-- (drop any other tenant_* schemas from failed attempts)

-- Also clear the access_requests if needed
UPDATE public.access_requests SET status = 'PENDING', keycloak_org_id = NULL, provisioning_error = NULL, reviewed_by = NULL, reviewed_at = NULL WHERE status != 'PENDING';
```

### 2. Re-test the full flow
1. Start dev stack: `bash compose/scripts/dev-up.sh`
2. Backend: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run`
3. Gateway: `cd gateway && ./mvnw spring-boot:run`
4. Frontend: `cd frontend && NODE_OPTIONS="" pnpm dev`
5. Submit access request → verify OTP via Mailpit (localhost:8025)
6. Login as `padmin@docteams.local` / `Admin123!` → approve the request
7. Check DB: `org_schema_mapping.clerk_org_id` should now be the slug (e.g., "avengers"), not a UUID
8. User accepts invitation email → logs in
9. Verify: member row should be created in the tenant schema's `members` table with real email/name from JWT

### 3. Double-email UX issue (Keycloak config, not code)
When a new user accepts the org invitation:
1. They get Email 1: "Invitation to join the Organization" (correct)
2. After accepting, they complete their profile
3. They then get Email 2: "Verify your email address" (extra/confusing)
4. Clicking verify takes them to Keycloak directly to re-enter their name

**This is a Keycloak realm configuration issue**, not a code fix:
- In Keycloak admin → Realm Settings → Login → "Verify Email" should be **disabled** for the `docteams` realm if you don't want email verification after invitation
- OR configure the invitation flow to mark the email as already verified (since the OTP flow already verified it)
- Keycloak 26 org invite endpoint creates the user with `emailVerified=false` by default. You can either:
  - Set `emailVerified=true` in the invite call (if the API supports it)
  - Disable the "Verify Email" required action in the realm
  - Use a custom authentication flow that skips email verification for invited users

### 4. MemberSyncService search_path (potential future issue)
`MemberSyncService.syncMember()` uses the same `ScopedValue + TransactionTemplate` pattern that had the search_path bug. It's NOT called in the Keycloak flow (no webhooks), so it's not immediately broken. But if/when Keycloak webhooks are added, this should also use `TenantTransactionHelper`. The service constructs its own `TransactionTemplate` internally.

## Files Changed (Uncommitted)

| File | Change |
|------|--------|
| `multitenancy/TenantTransactionHelper.java` | NEW — tenant-scoped transaction helper |
| `fielddefinition/FieldPackSeeder.java` | Uses `TenantTransactionHelper` |
| `template/TemplatePackSeeder.java` | Uses `TenantTransactionHelper` |
| `clause/ClausePackSeeder.java` | Uses `TenantTransactionHelper` |
| `compliance/CompliancePackSeeder.java` | Uses `TenantTransactionHelper` |
| `reporting/StandardReportPackSeeder.java` | Uses `TenantTransactionHelper` |
| `informationrequest/RequestPackSeeder.java` | Uses `TenantTransactionHelper` |
| `automation/template/AutomationTemplateSeeder.java` | Uses `TenantTransactionHelper` |
| `accessrequest/AccessRequestApprovalService.java` | Passes slug to `provisionTenant()` |
| `member/MemberFilter.java` | Always uses JWT email/name; removed `jitProvisioningEnabled` |
| `accessrequest/AccessRequestApprovalServiceTest.java` | Updated expectations for slug |
| `tasks/handover-phase39-fixes.md` | Updated status |
| Also uncommitted from before: `application.yml`, `application-keycloak.yml` | Config fixes |

## Test Results
- All 4909 backend tests pass (0 failures)
- `AccessRequestApprovalServiceTest`: 9/9 pass with updated slug expectations
