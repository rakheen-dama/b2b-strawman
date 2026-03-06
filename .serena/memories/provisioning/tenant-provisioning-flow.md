# Complete Tenant Provisioning Flow Analysis

## Overview
The tenant provisioning flow is a multi-stage process triggered by the Clerk webhook handler via the `/internal/orgs/provision` endpoint. The entire sequence ensures schema isolation, database structure setup, and feature pack initialization for each new organization.

## 1. Entry Point: ProvisioningController

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningController.java`
**Endpoint**: `POST /internal/orgs/provision`
**Authorization**: API Key (`X-API-KEY` header via `ApiKeyAuthFilter`)

### Request/Response
- **ProvisioningRequest**: `clerkOrgId` (required), `orgName` (required)
- **ProvisioningResponse**: `schemaName`, `message`, `status`
- **Status codes**: 
  - 201 CREATED: New provisioning succeeded
  - 409 CONFLICT: Tenant already provisioned (idempotent)

## 2. Core Service: TenantProvisioningService

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`

### Key Features
- **Retry logic**: `@Retryable` with maxAttempts=3, exponential backoff (1s, 2s, 4s)
- **Idempotency**: Checks `OrgSchemaMappingRepository` before provisioning (idempotent on retry)
- **Transactional state tracking**: Via `Organization.provisioningStatus` enum (PENDING → IN_PROGRESS → COMPLETED/FAILED)

### Provisioning Sequence (Lines 57-109)

1. **Idempotency check** (line 64-68)
   - Query `org_schema_mapping` by Clerk org ID
   - Return early if already provisioned

2. **Create/fetch Organization** (line 71-74)
   - Insert new `Organization` record (public schema) if not exists
   - Default tier: STARTER
   - Default plan slug: null (will be set based on tier)

3. **Mark IN_PROGRESS** (line 76-78)
   - Update `Organization.provisioningStatus = IN_PROGRESS`
   - Flush to DB (prevents concurrent retries)

4. **Generate schema name** (line 81)
   - Call `SchemaNameGenerator.generateSchemaName(clerkOrgId)`
   - Format: `tenant_<12-hex-chars>` (deterministic hash of namespace + Clerk org ID)

5. **Create schema** (line 87)
   - Call `createSchema(schemaName)` → SQL: `CREATE SCHEMA IF NOT EXISTS "tenant_*"`
   - Schema name validation: must match regex `^tenant_[0-9a-f]{12}$`

6. **Run Flyway migrations** (line 88)
   - Call `runTenantMigrations(schemaName)`
   - Executes all `classpath:db/migration/tenant/V*.sql` files against new schema
   - 54+ migrations creating all tenant-scoped tables (projects, documents, members, customers, tasks, invoices, etc.)
   - `baselineOnMigrate=true` prevents baseline issues on fresh schemas

7. **Seed packs** (lines 89-93) - **Five independent seeders**:
   - `fieldPackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` → Field packs (JSON-defined field definitions, groups)
   - `templatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` → Template packs (document templates)
   - `clausePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` → Clause packs (document clauses + template associations)
   - `compliancePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` → Compliance packs (checklist templates, retention policies)
   - `standardReportPackSeeder.seedForTenant(schemaName, clerkOrgId)` → Standard reports (3 hardcoded: Timesheet, Invoice Aging, Project Profitability)

8. **Create schema mapping** (line 94)
   - Insert `OrgSchemaMapping` record (public schema): `(clerkOrgId, schemaName)`
   - **Critical**: Done LAST so TenantFilter only resolves to schema once all tables + packs exist
   - Prevents race condition with first request

9. **Create subscription** (line 95-96)
   - Derive plan slug from `org.getPlanSlug()` or default to "starter"
   - Call `subscriptionService.createSubscription(org.getId(), planSlug)`
   - Inserts `Subscription(organizationId, planSlug)` record (public schema) with status=ACTIVE

10. **Mark COMPLETED** (line 98-99)
    - Update `Organization.provisioningStatus = COMPLETED`

### Exception Handling (lines 103-108)
- Catches all exceptions → marks org as FAILED → rethrows `ProvisioningException`
- Retry handler will retry if cause is `ProvisioningException` (not `IllegalArgumentException`)

## 3. Pack Seeders (Feature Initialization)

All seeders follow a common pattern:
- Bind tenant context via `ScopedValue.where(RequestScopes.TENANT_ID, schemaName).run(...)`
- Execute within transaction via `transactionTemplate.executeWithoutResult(...)`
- Check `OrgSettings.{field|template|clause|compliance|report}PackStatus` for idempotency
- Load pack definitions from classpath resources
- Create entities in tenant schema

### 3a. FieldPackSeeder
**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`

**Packs**: Loaded from `classpath:field-packs/*.json`
**Entities created**:
- `FieldGroup` (entity type, name, slug, autoApply flag)
- `FieldDefinition` (entity type, name, slug, fieldType, options, validation, requiredForContexts)
- `FieldGroupMember` (links field definitions to groups with sort order)

**Tracks**: `OrgSettings.fieldPackStatus` (list of `{packId, version}` JSON objects)

### 3b. TemplatePackSeeder
**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`

**Packs**: Loaded from `classpath:template-packs/*/pack.json`
**Entities created**:
- `DocumentTemplate` (name, slug, category, entity type, content JSON, CSS, source=PLATFORM, pack reference)
- Content and CSS loaded from sibling files relative to pack.json

**Tracks**: `OrgSettings.templatePackStatus` (list of `{packId, version}` JSON objects)

### 3c. ClausePackSeeder
**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClausePackSeeder.java`

**Packs**: Loaded from `classpath:clause-packs/*/pack.json`
**Entities created**:
- `Clause` (title, slug, body as JSONB Map, category, source=SYSTEM, sort order)
- `TemplateClause` (links clauses to templates, tracks required status, sort order)

**Template associations**: Per pack definition, links seeded clauses to templates

**Tracks**: `OrgSettings.clausePackStatus` (list of `{packId, version}` JSON objects)

### 3d. CompliancePackSeeder
**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackSeeder.java`

**Packs**: Loaded from `classpath:compliance-packs/*/pack.json`
**Entities created** (3 categories):

1. **Checklist Templates** (FICA packs + onboarding):
   - `ChecklistTemplate` (name, slug, customer type, description, autoInstantiate flag)
   - `ChecklistTemplateItem` (template items with dependencies, required document flags)
   - Two-pass creation: first all items, then resolve dependencies

2. **Field Definitions** (compliance-specific):
   - `FieldGroup` + `FieldDefinition` + `FieldGroupMember` (CUSTOMER entity type)
   - Parsed from pack's `fieldDefinitions` array

3. **Retention Policies**:
   - `RetentionPolicy` (record type, trigger event, retention days, action)
   - Unique constraint: (record_type, trigger_event)

**Special behavior**: FICA packs have `autoInstantiate=false`, so templates created with `active=false`

**Tracks**: `OrgSettings.compliancePackStatus` (list of `{packId, version}` JSON objects)

### 3e. StandardReportPackSeeder
**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/StandardReportPackSeeder.java`

**Packs**: Hardcoded (no classpath JSON loading)
**Pack ID**: "standard-reports" (version 1)

**Reports created** (3 fixed):
1. **Timesheet Report**:
   - Parameters: dateFrom, dateTo, groupBy (member|project|date), projectId, memberId
   - Columns: groupLabel, totalHours, billableHours, nonBillableHours, entryCount
   - Template: HTML/CSS with Thymeleaf directives for data binding + branding support

2. **Invoice Aging Report**:
   - Parameters: asOfDate, customerId
   - Columns: invoiceNumber, customerName, issueDate, dueDate, amount, currency, daysOverdue, ageBucketLabel
   - Buckets: Current, 1-30 days, 31-60 days, 61-90 days, 90+ days
   - Template: HTML/CSS with bucket summary + detail table

3. **Project Profitability Report**:
   - Parameters: dateFrom, dateTo, projectId, customerId
   - Columns: projectName, customerName, currency, billableHours, revenue, cost, margin, marginPercent
   - Summary cards: totalRevenue, totalCost, totalMargin, overallMarginPercent
   - Template: HTML/CSS with margin color coding (green/red)

**All templates**: Support branding (logo, brand color, footer text via OrgSettings)

**Tracks**: `OrgSettings.reportPackStatus` (list of `{packId, version}` JSON objects)

## 4. Flyway Migrations

### Startup Sequence

1. **FlywayConfig** (line 12-20 of `config/FlywayConfig.java`)
   - `@Bean(initMethod = "migrate")`
   - Runs global migrations (`classpath:db/migration/global`) against `public` schema
   - Executed at Spring context initialization (before `ApplicationRunner` beans)

2. **TenantMigrationRunner** (line 14-16 of `provisioning/TenantMigrationRunner.java`)
   - `@Component @Order(50)` implements `ApplicationRunner`
   - Runs AFTER global migrations
   - Queries `OrgSchemaMappingRepository.findAll()` for all tenant schemas
   - Runs `classpath:db/migration/tenant` migrations for each schema
   - Idempotent: Flyway tracks applied migrations in `flyway_schema_history` (per schema)

### Global Schema (public)
**Migrations**: V1-V12 (12 total)
- V1: `org_schema_mapping` table (Clerk org ID → schema name mapping)
- V2: `organizations` table (metadata + provisioning status)
- V3: `processed_webhooks` table (idempotency for Clerk webhooks)
- V4-V12: Portal read model, payment handling, subscription fields

### Tenant Schema (tenant_*)
**Migrations**: V1-V54 (54 total)
- V1-V6: Core entities (projects, documents, members, project members)
- V7-V8: Customers + customer-project links
- V9: Tasks
- V10: Document scope extension
- V11: Time entries
- V12: Audit events
- V13-V15: Comments, notifications, notification preferences
- V16: Portal contacts + magic link tokens
- V17-V20: Rate cards, budgets, indexes
- V21: Invoices
- V22-V26: Field definitions, tags, custom field columns, saved views
- V27-V28: Document templates + branding
- V29: Customer lifecycle (onboarding, compliance)
- V30: Project templates + recurring schedules
- V31: Retainer agreements
- V32-V34: Task items + template task items
- V35: Report definitions
- V36-V37: Integrations + portal comments
- V38-V43: Custom field maturity, generated documents, comment source, email delivery, payments, taxes
- V44-V47: Clauses, acceptance requests, entity lifecycle integrity
- V48-V54: Document content migrations, expenses, proposals, fixed fee invoices, field prerequisite contexts

## 5. OrgSettings and Pack Status Tracking

**File**: `settings/OrgSettings` entity (implicit from seeders)

Each pack status field is a JSONB array of objects: `[{packId: string, version: number}, ...]`

Tracked fields:
- `fieldPackStatus`
- `templatePackStatus`
- `clausePackStatus`
- `compliancePackStatus`
- `reportPackStatus`

**Idempotency mechanism**:
1. Seeder checks if pack already applied: `settings.getFieldPackStatus().stream().anyMatch(entry => packId.equals(entry.get("packId")))`
2. If already applied, skip (log warning)
3. If not applied, apply pack + record in status

## 6. Data Flow Summary

```
POST /internal/orgs/provision {clerkOrgId, orgName}
    ↓
ProvisioningController.provisionTenant()
    ↓
TenantProvisioningService.provisionTenant()
    ├─ Check idempotency (org_schema_mapping)
    ├─ Create Organization (public schema, status=PENDING)
    ├─ Mark IN_PROGRESS
    ├─ Generate schema name via SchemaNameGenerator
    ├─ CREATE SCHEMA tenant_*
    ├─ Run Flyway migrations (V1-V54)
    │   └─ Create all tenant tables + constraints
    ├─ Seed packs (5 seeders, each with ScopedValue binding + transaction):
    │   ├─ FieldPackSeeder → FieldGroup, FieldDefinition, FieldGroupMember
    │   ├─ TemplatePackSeeder → DocumentTemplate
    │   ├─ ClausePackSeeder → Clause, TemplateClause
    │   ├─ CompliancePackSeeder → ChecklistTemplate, ChecklistTemplateItem, RetentionPolicy, (field defs)
    │   └─ StandardReportPackSeeder → ReportDefinition (3 hardcoded)
    ├─ Create OrgSchemaMapping (public schema) ← LAST (critical for isolation)
    ├─ Create Subscription (public schema)
    ├─ Mark COMPLETED
    └─ Return 201 CREATED {schemaName, "Tenant provisioned successfully", "COMPLETED"}

---

Startup sequence (ApplicationRunner):
    1. FlywayConfig.migrate() → global migrations (public schema)
    2. TenantMigrationRunner.run() → tenant migrations for all existing schemas
```

## 7. Key Design Decisions (ADRs)

- **Deterministic schema naming** (UUID v5 hash): Allows idempotent reproduction of schema name from Clerk org ID
- **Mapping created last**: Prevents TenantFilter from resolving to incompletely provisioned schemas
- **ScopedValue binding in seeders**: Ensures all pack seeding happens in correct tenant context
- **Idempotency at multiple levels**: 
  - Org schema mapping check (service level)
  - OrgSettings pack status tracking (seeder level)
  - Flyway baseline-on-migrate + idempotent SQL (migration level)
- **No retry on IllegalArgumentException**: Schema name validation failure is programmer error, not transient
- **Global vs. tenant migrations split**: Allows independent scaling of global reference tables vs. tenant data

## 8. Critical Path to Keycloak Migration

**No changes required for**:
- Provisioning endpoint structure (remains same)
- Schema creation + Flyway sequence (authentication-agnostic)
- Pack seeding (all use ScopedValue, no auth assumptions)
- OrgSchemaMapping + Organization tables (auth-provider agnostic)

**Audit points for auth migration**:
- Member sync mechanism (currently Clerk webhook-based) → Keycloak event listener
- Clerk org ID in JWT claim `o.id` → Keycloak org ID in token claim (TBD)
- Subscription creation plan slug derivation (currently org.getPlanSlug()) → confirm strategy

**Test preservation**:
- Integration tests using `TenantProvisioningService.provisionTenant()` should remain valid
- Flyway test migrations (V*MigrationTest) are independent of auth
