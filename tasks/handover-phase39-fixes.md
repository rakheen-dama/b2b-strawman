# Handover: Phase 39 Platform Admin Flow Fixes

## Branch: `fix/phase39-platform-admin-flow`

Two commits + uncommitted changes. One remaining bug needs fixing.

## FIXED: Seeders query `public` schema instead of tenant schema

### Symptom
When platform admin clicks approve:
1. KC org created ✓
2. Tenant schema created ✓
3. Flyway 62 migrations applied ✓
4. `FieldPackSeeder` fails: `ERROR: relation "org_settings" does not exist`
5. Nothing committed — no `public.organizations` row, no `public.org_schema_mapping` row
6. Access request stays PENDING, dialog shows error

### Root Cause (confirmed via logs, NOT yet fixed)
`FieldPackSeeder.seedPacksForTenant()` (line 60-63) does:
```java
ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
    .where(RequestScopes.ORG_ID, orgId)
    .run(() -> transactionTemplate.executeWithoutResult(tx -> doSeedPacks(tenantId)));
```

`doSeedPacks()` calls `orgSettingsRepository.findForCurrentTenant()` which generates:
```sql
SELECT ... FROM org_settings
```

This runs against `public` schema. The `org_settings` table is in `tenant_xxx` (created by Flyway V17).

**The `TransactionTemplate` (default `PROPAGATION_REQUIRED`) gets a connection from HikariCP that has `search_path = public`.** Binding `TENANT_ID` via ScopedValue tells the `TenantIdentifierResolver` the right schema, but the Hibernate `MultiTenantConnectionProvider` is NOT invoked to set `search_path` on the connection.

### What was tried (did NOT fix it)
1. Removed `keycloak.admin.*` from `application.yml` (empty defaults → URI crash) — moved to `application-keycloak.yml` ✓ fixed the URI crash
2. Removed `@Transactional` from `approve()`, used explicit `TransactionTemplate` calls so provisioning runs outside any transaction — **still fails** because seeders' own `TransactionTemplate` connections still get `search_path = public`

### The actual problem to solve
The Hibernate multitenancy connection routing (`MultiTenantConnectionProvider.getConnection(tenantId)`) is not being invoked when seeders use `TransactionTemplate`. The seeder binds `TENANT_ID` ScopedValue, and `TenantIdentifierResolver` reads it, but the connection provider doesn't set `search_path`.

### Fix applied

Created `TenantTransactionHelper` in `multitenancy/` — explicitly sets `search_path` via native query on the transactional `EntityManager` before running seeder logic. All 7 seeders updated to use it instead of raw `ScopedValue.where() + TransactionTemplate`.

**Root cause**: Hibernate's `MultiTenantConnectionProvider.getConnection(tenantId)` is not invoked for JDBC connections obtained via `TransactionTemplate` during provisioning. The `JpaTransactionManager` creates an `EntityManager` but the underlying connection retains `search_path = public` from HikariCP's `connection-init-sql`. The `ScopedValue` binding and `TenantIdentifierResolver` correctly resolve the tenant, but the connection provider's `setSearchPath()` never fires.

**Files changed**:
- NEW: `multitenancy/TenantTransactionHelper.java` — binds ScopedValues, starts transaction, forces `search_path`, runs callback
- `fielddefinition/FieldPackSeeder.java` — uses `TenantTransactionHelper`
- `template/TemplatePackSeeder.java` — uses `TenantTransactionHelper`
- `clause/ClausePackSeeder.java` — uses `TenantTransactionHelper`
- `compliance/CompliancePackSeeder.java` — uses `TenantTransactionHelper`
- `reporting/StandardReportPackSeeder.java` — uses `TenantTransactionHelper`
- `informationrequest/RequestPackSeeder.java` — uses `TenantTransactionHelper`
- `automation/template/AutomationTemplateSeeder.java` — uses `TenantTransactionHelper`

All 4909 backend tests pass.

## What Was Fixed (committed — dff9c032)

1. Dashboard routing — platform admin redirect
2. Create-org routing — same fix
3. Gateway security — `permitAll()` for public access-request endpoints
4. Access requests response type — plain array, not paginated
5. Error visibility — error banner on page
6. Landing page links — `/request-access` in keycloak mode
7. Keycloak registration disabled
8. Seed script — dedicated platform admin user
9. Mail config — Mailpit SMTP

## Uncommitted changes

1. `backend/src/main/resources/application.yml` — removed `keycloak.admin.*` block (empty defaults caused URI crash)
2. `backend/src/main/resources/application-keycloak.yml` — added `keycloak.admin.*` with local defaults
3. `backend/.../accessrequest/AccessRequestApprovalService.java` — removed `@Transactional` from `approve()`, uses explicit `TransactionTemplate` (did NOT fix seeder issue but is a better design regardless)

## Testing

1. Clean up: `DROP SCHEMA IF EXISTS tenant_d9b590647a21 CASCADE;`
2. Start: `bash compose/scripts/dev-up.sh`
3. Backend: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run`
4. Gateway: `cd gateway && ./mvnw spring-boot:run`
5. Frontend: `cd frontend && NODE_OPTIONS="" pnpm dev`
6. Submit new access request → verify OTP via Mailpit (localhost:8025) → login as `padmin@docteams.local` / `Admin123!` → approve
7. Check: `public.organizations` and `public.org_schema_mapping` should have rows
