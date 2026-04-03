# Phase 58 — Demo Readiness & Admin Billing Controls

## System Context

HeyKazi is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 57 phases of functionality. The platform is approaching production readiness with infrastructure (Phase 56) and subscription billing (Phase 57) in place. The current state relevant to this phase:

- **Subscription billing** (Phase 57): `Subscription` entity with lifecycle states (TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE, SUSPENDED, GRACE_PERIOD, EXPIRED, LOCKED). PayFast recurring billing integration. `SubscriptionGuardFilter` enforces read-only for grace/locked tenants. `SubscriptionStatusCache` (Caffeine, 5-min TTL). Scheduled jobs for trial/grace expiry. Admin endpoints exist: `POST /internal/billing/extend-trial` and `POST /internal/billing/activate`.
- **Platform admin panel** (Phase 39): Admin-approved org provisioning. Platform admin identity infrastructure (API key auth for internal endpoints, JWT for admin panel). Access request → OTP verification → admin approval → Keycloak org + schema provisioning pipeline. Admin panel at `/platform-admin` with request queue and approval UI.
- **Vertical profiles** (Phase 49): `VerticalProfile` enum (GENERIC, ACCOUNTING, LEGAL), `ProfileRegistry`, `ModuleRegistry`. Profiles control which modules are enabled, what packs are seeded, and sidebar/settings visibility. `OrgSettings.verticalProfile` field.
- **Keycloak + Gateway BFF** (Phase 36): Spring Cloud Gateway BFF handles auth. JIT tenant & member provisioning on first login. Keycloak realm with org-scoped roles.
- **Seed infrastructure**: Pack seeders exist for each domain (compliance packs, field packs, template packs, clause packs, rate packs, automation templates, deadline types). These run during org provisioning via `TenantProvisioningService` and are profile-aware.
- **Existing entities available for demo data**: Customer, Project, Task, TimeEntry, Invoice, InvoiceLine, Proposal, Document, Comment, Notification, Expense, Retainer, BillingRate, ProjectBudget, Allocation, RecurringTask, AutomationRule, and more.

**The problem**: The platform can collect payments via PayFast but has no flexibility for demos, pilots, or manual billing arrangements. Platform admins cannot manage subscription state from the admin panel — they'd need to hit internal API endpoints directly. There's no way to quickly spin up a realistic-looking demo tenant for a prospect meeting. And demo tenants accumulate with no cleanup mechanism.

**The fix**: Add a billing method dimension to subscriptions, build admin billing controls into the platform panel, create one-click demo tenant provisioning with realistic seed data per vertical, and provide safe demo tenant cleanup.

## Objective

1. **Separate billing method from subscription status**: A new `billing_method` field captures the commercial arrangement (PayFast, debit order, pilot, complimentary, manual) independently of the access-control status. The guard filter and lifecycle remain unchanged — only scheduled jobs and UI adapt.
2. **Admin billing management**: Platform admin panel gets a billing section to view and manage all tenant subscriptions — change billing method, extend trials, activate, lock — without touching API endpoints directly.
3. **One-click demo tenant provisioning**: Platform admin can create a fully provisioned tenant (Keycloak org, schema, packs, admin user) for any vertical profile, skipping the access request → approval pipeline.
4. **Realistic demo data seeding**: Per-vertical seed data that populates 3 months of realistic business activity — customers, projects, tasks, time entries, invoices, proposals — so dashboards and reports look populated.
5. **Safe demo tenant cleanup**: One-click tenant destruction for demo/pilot tenants with safety rails to prevent accidental deletion of paying tenants.

***

## Constraints and Assumptions

1. **Architecture constraints**
   - `billing_method` lives on the existing `Subscription` entity (public schema). No new tables needed for this dimension.
   - The `SubscriptionGuardFilter` does NOT check `billing_method` — access control remains purely status-based.
   - Demo provisioning reuses the existing `TenantProvisioningService` pipeline (Keycloak org creation, schema provisioning, pack seeding) but skips the access request / OTP / approval steps.
   - Demo data seeding is a separate service from pack seeding. Packs define configuration (field definitions, templates, rates). Demo data creates transactional records (customers, projects, time entries, invoices).
   - Tenant cleanup is destructive and irreversible. Safety rails are mandatory.

2. **Vertical profile constraints**
   - Demo data must be profile-aware. An accounting firm demo should have SARS deadlines and tax filing statuses. A legal firm demo should have matters with appropriate task structures (court dates and adverse parties depend on Phase 55 — seed what exists, skip what doesn't).
   - The GENERIC profile gets a broadly applicable dataset (marketing agency / consultancy feel).
   - Demo data should use realistic South African business names, addresses, and context.

3. **Security constraints**
   - All admin endpoints require platform-admin authentication (existing `PlatformAdminSecurity` from Phase 39).
   - Demo tenant cleanup must be doubly confirmed (confirmation dialog + org name typed to match).
   - Cleanup is restricted to tenants with `billing_method` in (PILOT, COMPLIMENTARY) — paying tenants cannot be deleted through this flow.

4. **What NOT to build**
   - No tenant impersonation (deferred — requires Keycloak token exchange and BFF session management)
   - No multi-tenant billing dashboard with revenue analytics (future — this is admin tooling, not BI)
   - No self-service billing method changes by tenants (admin-only)
   - No billing method migration tooling (e.g., PILOT → PAYFAST is just an admin status change + tenant subscribes via PayFast)
   - No demo data customization UI (seed data is fixed per profile — admin can't pick which entities to seed)

***

## Section 1 — Billing Method Dimension

### 1.1 Data Model

Add a `billing_method` column to the existing `subscriptions` table:

```sql
-- Global migration (public schema)
ALTER TABLE subscriptions
  ADD COLUMN billing_method VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
```

`BillingMethod` enum:

| Value | Meaning | Trial Auto-Expires? | Set By |
|-------|---------|---------------------|--------|
| `PAYFAST` | Automated card billing via PayFast | Yes | ITN webhook (on first successful payment) |
| `DEBIT_ORDER` | Manual EFT/debit order, admin reconciles monthly | No | Platform admin |
| `PILOT` | Pilot partner, no payment expected | No | Platform admin (also set by demo provisioning) |
| `COMPLIMENTARY` | Indefinite free access (internal, strategic partners) | No | Platform admin |
| `MANUAL` | Default — invoice + EFT, admin manages lifecycle | Yes (same as PAYFAST) | Default on provisioning |

### 1.2 Subscription Entity Changes

Add to `Subscription.java`:
- `billingMethod` field (`@Enumerated(STRING)`, default `MANUAL`)
- `adminNote` field (`String`, nullable) — free-text note from admin (e.g., "Pilot agreement signed 2026-04-01", "Debit order ref: DO-12345")

### 1.3 Scheduled Job Changes

**Trial expiry job**: Only expire subscriptions where `billing_method IN ('PAYFAST', 'MANUAL')`. Subscriptions with `PILOT`, `COMPLIMENTARY`, or `DEBIT_ORDER` billing methods do not auto-expire — admin manages their lifecycle.

**Grace period expiry job**: Applies to ALL billing methods. If an admin sets a tenant to GRACE_PERIOD (regardless of billing method), the grace timer runs. This ensures even pilot tenants that are explicitly set to grace will eventually lock.

**Pending cancellation job**: Only applies to `PAYFAST` (the only method with automated cancellation via PSP).

### 1.4 Impact on Existing Phase 57 Code

- `SubscriptionService.createTrialSubscription()`: Set `billingMethod = MANUAL` (default). Demo provisioning will override to `PILOT`.
- `PayFastItnWebhookHandler`: On first successful payment, set `billingMethod = PAYFAST` (transition from MANUAL/PILOT to PAYFAST means tenant subscribed via card).
- `SubscriptionGuardFilter`: **No changes**. It only reads `subscriptionStatus`.
- `SubscriptionStatusCache`: Cache key remains org ID → status. Add `billingMethod` to the cached record so the billing API can return it without a DB query.
- `BillingController.getSubscription()`: Include `billingMethod` and `adminManaged` flag in response.

### 1.5 Updated Billing Response DTO

```java
public record BillingResponse(
    String status,                    // TRIALING, ACTIVE, PAST_DUE, etc.
    String billingMethod,             // PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL
    boolean adminManaged,             // true if billingMethod is not PAYFAST
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    Instant graceEndsAt,
    Instant nextBillingAt,            // null if not PAYFAST
    int monthlyAmountCents,
    String currency,
    String adminNote,                 // null if no admin note set
    LimitsResponse limits,
    boolean canSubscribe,             // true if eligible AND billingMethod allows self-service
    boolean canCancel                 // true if ACTIVE and billingMethod = PAYFAST
) {
    public record LimitsResponse(int maxMembers, long currentMembers) {}
}
```

***

## Section 2 — Admin Billing Management

### 2.1 Admin API Endpoints

Extend the existing internal API (Phase 39's `PlatformAdminSecurity`):

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/internal/billing/tenants` | Platform admin | List all orgs with subscription status, billing method, trial/grace dates |
| `GET` | `/internal/billing/tenants/{orgId}` | Platform admin | Detailed subscription info for a specific org |
| `PUT` | `/internal/billing/tenants/{orgId}/status` | Platform admin | Admin override: set status + billing method + period end + note |
| `POST` | `/internal/billing/tenants/{orgId}/extend-trial` | Platform admin | Extend trial by N days (refactored from existing endpoint) |

**Admin status override request:**

```java
public record AdminBillingOverrideRequest(
    String status,                    // TRIALING, ACTIVE, GRACE_PERIOD, LOCKED
    String billingMethod,             // Optional — keeps current if null
    Instant currentPeriodEnd,         // Optional — sets when the current period expires
    String adminNote                  // Required — audit trail reason
) {}
```

**Validation rules:**
- Cannot set status to PENDING_CANCELLATION or PAST_DUE (these are PSP-driven states)
- Cannot set `billingMethod = PAYFAST` without an existing PayFast token (tenant must subscribe through PayFast checkout)
- `adminNote` is required for all admin overrides (audit trail)

**Admin tenant list response:**

```java
public record AdminTenantBillingResponse(
    UUID organizationId,
    String organizationName,
    String verticalProfile,           // GENERIC, ACCOUNTING, LEGAL
    String subscriptionStatus,
    String billingMethod,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    Instant graceEndsAt,
    Instant createdAt,
    int memberCount,
    String adminNote,
    boolean isDemoTenant              // billing_method IN (PILOT, COMPLIMENTARY) — cleanup-eligible
) {}
```

### 2.2 Admin Panel Frontend — Billing Section

Add a "Billing" section to the platform admin panel (`/platform-admin/billing`):

**Tenant billing list page:**
- Table: org name, vertical profile, status badge (color-coded), billing method badge, trial/period end date, member count
- Filters: by status, by billing method, by vertical profile
- Sort: by created date, by trial end (soonest first), by status
- Search: by org name

**Tenant billing detail (slide-over or modal):**
- Current status + billing method with badges
- Timeline: key dates (created, trial end, period end, grace end)
- Admin actions:
  - "Change Billing Method" dropdown
  - "Extend Trial" button (input: number of days)
  - "Activate" button (for TRIALING/EXPIRED tenants)
  - "Lock" button (with confirmation)
  - "Set Period End" date picker
- Admin note text field (persisted on save)
- Audit log: recent admin actions on this tenant's subscription (from audit events table)

### 2.3 Audit Integration

All admin billing actions emit audit events via the existing `AuditEventBuilder`:
- `SUBSCRIPTION_ADMIN_STATUS_CHANGE`: old status → new status, billing method, admin note, admin user ID
- `SUBSCRIPTION_ADMIN_TRIAL_EXTENDED`: org ID, extended by N days, new trial end date
- `SUBSCRIPTION_ADMIN_BILLING_METHOD_CHANGED`: old method → new method, admin note

***

## Section 3 — Demo Tenant Provisioning

### 3.1 Provisioning Flow

Platform admin "Create Demo Tenant" flow that bypasses the access request pipeline:

1. Admin navigates to `/platform-admin/demo` (or a "Create Demo" button on the billing page)
2. Admin fills a minimal form:
   - **Organization name** (text input, default: "Demo — {Profile} Firm")
   - **Vertical profile** (dropdown: Generic, Accounting, Legal)
   - **Admin email** (text input — the demo user who'll log in. Can be the platform admin's own email for self-demos, or a prospect's email)
   - **Seed demo data** (checkbox, default: checked)
3. Backend executes:
   a. Creates Keycloak organization + admin user (or links existing Keycloak user if email matches)
   b. Provisions tenant schema via existing `TenantProvisioningService`
   c. Seeds profile-specific packs (field packs, template packs, etc.) — existing behavior
   d. Creates subscription with `status = ACTIVE`, `billingMethod = PILOT`, `adminNote = "Demo tenant created by {admin}"`
   e. If "Seed demo data" checked: triggers demo data seeding (Section 4)
4. Returns the org details + login URL to the admin

### 3.2 API Endpoint

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/internal/demo/provision` | Platform admin | Creates a fully provisioned demo tenant |

**Request:**

```java
public record DemoProvisionRequest(
    String organizationName,
    String verticalProfile,           // GENERIC, ACCOUNTING, LEGAL
    String adminEmail,                // Keycloak user to assign as owner
    boolean seedDemoData              // default true
) {}
```

**Response:**

```java
public record DemoProvisionResponse(
    UUID organizationId,
    String organizationSlug,
    String organizationName,
    String verticalProfile,
    String loginUrl,                  // Direct login URL for the demo
    boolean demoDataSeeded,
    String adminNote
) {}
```

### 3.3 Implementation Notes

- Reuse `TenantProvisioningService` — the existing provisioning pipeline handles schema creation, Flyway migrations, and pack seeding. The demo flow just calls it directly instead of going through the access request → approval pipeline.
- Keycloak user creation: Use `KeycloakAdminClient` (Phase 46) to create or find the user, add them to the new org with OWNER role.
- The demo tenant is immediately usable — no onboarding checklist or approval wait.
- If the admin email already exists in Keycloak (e.g., the platform admin doing a self-demo), just add them to the new org. Don't create a duplicate user.

***

## Section 4 — Demo Data Seeding

### 4.1 Seed Service Architecture

A `DemoDataSeeder` service that populates a tenant with realistic transactional data. This is separate from pack seeding (which creates configuration). Demo data seeding creates business records.

```
DemoDataSeeder
├── GenericDemoDataSeeder          (marketing agency / consultancy)
├── AccountingDemoDataSeeder       (small SA accounting firm)
└── LegalDemoDataSeeder            (small SA law firm)
```

Each seeder extends a `BaseDemoDataSeeder` that provides shared utilities:
- Date generation relative to "today" (so data always looks fresh)
- Realistic South African business name generation
- Member creation with realistic names
- Time entry distribution (realistic hours per day/week)

### 4.2 Data Volume (per demo tenant)

Target: enough data to make dashboards, reports, and lists look realistic without overwhelming the UI.

| Entity | Count | Notes |
|--------|-------|-------|
| Members | 4 | Owner + 3 team members with different roles |
| Customers | 5-8 | Mix of active, onboarding, prospect statuses |
| Projects | 8-12 | Mix of active, completed, on-hold. Linked to customers |
| Tasks | 40-60 | Distributed across projects, mix of statuses and assignees |
| Time entries | 150-250 | ~3 months of data, realistic daily hours (6-8h billable), varied across members |
| Invoices | 8-12 | Mix of draft, sent, paid, overdue. Linked to time entries |
| Proposals | 3-5 | Mix of draft, sent, accepted, expired |
| Documents | 5-8 | Generated from templates (engagement letters, proposals) |
| Comments | 15-25 | Distributed across tasks and documents |
| Expenses | 10-15 | Travel, software, office supplies |
| Notifications | 10-20 | Recent activity notifications |
| Billing rates | Per member + per project overrides | Realistic SA professional rates |
| Project budgets | Per active project | Hours and currency budgets with ~60-80% utilization |

### 4.3 Vertical-Specific Data

**Generic (Agency/Consultancy):**
- Customers: "Acme Holdings (Pty) Ltd", "Cape Digital Solutions", "Highveld Manufacturing", etc.
- Projects: "Website Redesign", "Q1 Strategy Review", "Brand Identity Refresh", etc.
- Tasks: Design-oriented task names, content creation, client reviews
- Rates: R850-R1,500/hr depending on role

**Accounting:**
- Customers: "Van der Merwe & Associates", "Protea Trading (Pty) Ltd", "Karoo Investments", etc.
- Projects: "2025 Annual Financials", "VAT Registration", "BBBEE Audit", "Monthly Bookkeeping — Protea Trading", etc.
- Tasks: Tax return preparation, financial statement review, SARS submission, bookkeeping
- Recurring engagements: Monthly bookkeeping projects with recurring tasks
- Rates: R650-R1,200/hr (partner rates higher)
- Filing deadlines: SARS ITR14, provisional tax, VAT201 (seeded via deadline type infrastructure from Phase 51)
- Compliance checklists: FICA verification, tax clearance, CIPC annual returns

**Legal (if Phase 55 entities exist — otherwise skip legal-specific entities):**
- Customers: "Dlamini Property Trust", "Naidoo & Partners Developers", "Botha Family Estate", etc.
- Projects/Matters: "Dlamini — Property Transfer DE-2026-001", "Naidoo — Commercial Lease Review", etc.
- Tasks: Due diligence, contract drafting, FICA verification, settlement
- Rates: R1,200-R3,500/hr (candidate attorney to senior partner)
- Court dates, adverse parties, tariff items: Only seeded if Phase 55 entities exist in the schema. Check via `ModuleRegistry.isEnabled("legal")`.

### 4.4 Data Consistency Rules

Demo data must be internally consistent:
- Time entries link to real tasks on real projects assigned to real members
- Invoices contain line items matching actual unbilled time entries (mark those entries as billed)
- Invoice totals match line item sums with correct tax calculations
- Budget utilization percentages reflect actual time entries
- Profitability numbers are realistic (positive margins on most projects, one project intentionally over-budget)
- Proposal amounts align with project scopes
- Activity timeline makes chronological sense (customer created → project created → tasks added → time logged → invoice generated)
- Allocation data (Phase 38) reflects actual project assignments

### 4.5 Reseed Endpoint

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/internal/demo/{orgId}/reseed` | Platform admin | Deletes all transactional data and reseeds. Does NOT delete configuration (packs, templates, settings). |

This is useful for resetting a demo tenant between prospect meetings.

Implementation: `DELETE` all transactional records in the tenant schema (time entries, invoices, tasks, projects, customers, etc.) then run the seeder again. Order matters — delete child records before parents. Use `TRUNCATE ... CASCADE` within the tenant schema for efficiency.

***

## Section 5 — Demo Tenant Cleanup

### 5.1 Cleanup Flow

1. Platform admin views tenant list → identifies demo tenants (badge: "Demo" or "Pilot")
2. Clicks "Delete Tenant" → confirmation dialog appears
3. Dialog shows:
   - Organization name, vertical profile, member count, creation date
   - Warning: "This will permanently delete the organization, all its data, the database schema, and Keycloak resources. This action cannot be undone."
   - Text input: "Type the organization name to confirm"
4. On confirmation, backend executes cleanup

### 5.2 Cleanup API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `DELETE` | `/internal/demo/{orgId}` | Platform admin | Destroys a demo tenant completely |

**Request:**

```java
public record DemoCleanupRequest(
    String confirmOrganizationName    // Must match org name exactly
) {}
```

**Safety validations:**
- Organization must have `billing_method` IN (`PILOT`, `COMPLIMENTARY`). Reject if `PAYFAST`, `DEBIT_ORDER`, or `MANUAL`.
- `confirmOrganizationName` must match the actual organization name (case-sensitive).
- Emit audit event before starting cleanup (so there's a record even if cleanup partially fails).

### 5.3 Cleanup Steps (ordered)

1. **Audit**: Log `DEMO_TENANT_DELETED` event with org details, admin user, timestamp
2. **Keycloak cleanup**: Remove organization + all member associations via `KeycloakAdminClient`. Remove Keycloak users that are ONLY in this org (don't delete users who belong to other orgs too).
3. **Drop tenant schema**: `DROP SCHEMA IF EXISTS {schema_name} CASCADE` — removes all tenant-scoped data in one operation.
4. **Clean public schema records**: Delete from `subscriptions`, `organizations`, `members`, `subscription_payments` where `organization_id = {orgId}`.
5. **Cache eviction**: Evict `SubscriptionStatusCache` entry for this org.
6. **S3 cleanup** (optional, best-effort): Delete the org's S3 prefix (`{tenant-id}/`) from the documents bucket. Log warning if S3 cleanup fails but don't block the overall cleanup.

### 5.4 Error Handling

Cleanup is a multi-step process that can partially fail. Strategy:
- Execute steps in order. If any step fails, log the error and continue with remaining steps (best-effort cleanup).
- Return a response indicating which steps succeeded and which failed.
- Admin can retry cleanup for partially failed deletions.

```java
public record DemoCleanupResponse(
    UUID organizationId,
    String organizationName,
    boolean keycloakCleaned,
    boolean schemaCleaned,
    boolean publicRecordsCleaned,
    boolean s3Cleaned,
    List<String> errors               // Empty if all steps succeeded
) {}
```

***

## Section 6 — Frontend Changes

### 6.1 Billing Page — Billing Method Awareness

The existing billing page (Phase 57, Epic 423) adapts based on `billingMethod`:

**For `billingMethod = PAYFAST` (self-service) — no changes from Phase 57 spec:**
- Subscribe CTA, cancel button, payment history, PayFast redirect handling

**For admin-managed billing methods (`DEBIT_ORDER`, `PILOT`, `COMPLIMENTARY`, `MANUAL`):**

| Status | Page Content |
|--------|-------------|
| TRIALING | Trial info + billing method badge (e.g., "Pilot Partner"). No subscribe CTA. Message: "Your account is managed by your administrator" |
| ACTIVE | Billing method badge, current period end (if set). Message: "Your account is managed by your administrator" |
| GRACE_PERIOD | Read-only notice. Message: "Contact your administrator to restore access" |
| LOCKED | Full-page message: "Contact your administrator" with support email |

Hide PayFast-specific UI (payment history, cancel button, subscribe CTA) when `adminManaged = true`.

### 6.2 Platform Admin Panel — Billing Section

New route: `/platform-admin/billing`

**Tenant billing list:**
- Table columns: Org Name, Profile, Status (badge), Billing Method (badge), Trial/Period End, Members, Created
- Row action menu: Edit Billing, Extend Trial, Reseed Data, Delete (for demo tenants only)
- Filters: status dropdown, billing method dropdown, profile dropdown
- Search: org name text search
- Badge colors: ACTIVE=green, TRIALING=blue, GRACE_PERIOD=amber, LOCKED=red, PILOT=purple, COMPLIMENTARY=teal

**Billing detail slide-over** (opens when clicking a tenant row):
- Header: org name + profile badge
- Status section: current status + billing method + key dates
- Actions section: Change Status dropdown, Change Billing Method dropdown, Extend Trial (days input), Set Period End (date picker)
- Admin note: text area, saved with any action
- History section: recent admin audit events for this tenant
- Footer: "Save Changes" button (batches all changes into one API call)

### 6.3 Platform Admin Panel — Demo Section

New route: `/platform-admin/demo`

**Create Demo Tenant form:**
- Organization Name (text input, placeholder: "Demo — Accounting Firm")
- Vertical Profile (radio group: Generic, Accounting, Legal — with brief description of each)
- Admin Email (text input — email of the person who'll log into the demo)
- Seed Demo Data (toggle, default: on)
- "Create Demo Tenant" button → loading state → success with org details + login URL

**Demo tenant list** (could be a filtered view of the billing list, or a separate list):
- Shows only tenants with `billing_method` IN (PILOT, COMPLIMENTARY)
- Columns: Org Name, Profile, Status, Created, Members
- Actions: Reseed Data, Delete Tenant
- Delete action triggers the confirmation dialog (Section 5.1)

***

## Section 7 — Testing Strategy

### 7.1 Backend Tests

- **BillingMethodTest**: Verify billing method is set correctly on provisioning, updated by admin endpoints, returned in billing response. Verify scheduled jobs respect billing method filters.
- **AdminBillingEndpointTest**: CRUD operations on tenant billing. Validation rules (can't set PAYFAST without token, adminNote required). Authorization (only platform admins).
- **DemoProvisionServiceTest**: Full provisioning flow — Keycloak org created, schema provisioned, packs seeded, subscription set to ACTIVE/PILOT. Verify idempotency (same email doesn't create duplicate Keycloak user).
- **DemoDataSeederTest**: Verify data consistency — invoices match time entries, budgets reflect actual hours, chronological ordering. Test each vertical profile.
- **DemoCleanupServiceTest**: Full cleanup flow — schema dropped, public records removed, Keycloak cleaned. Verify safety rails (can't delete PAYFAST tenant). Verify partial failure handling.
- **DemoReseedTest**: Verify reseed deletes transactional data but preserves configuration.

### 7.2 Frontend Tests

- Billing page adapts UI based on billing method (PAYFAST shows card UI, PILOT shows admin-managed UI)
- Admin panel billing list renders with correct badges and filters
- Admin billing detail slide-over allows status/method changes
- Demo provisioning form validates inputs and shows success state
- Demo tenant delete confirmation requires exact name match
- Reseed button shows loading state and success toast

***

## ADR Topics

1. **Billing method as a separate dimension** — Why `billing_method` is independent of `subscription_status` rather than adding more status values. Access control (status) vs. commercial arrangement (method) are orthogonal concerns. Adding PILOT/DEBIT_ORDER as statuses would require guard filter changes for each new billing arrangement.
2. **Demo provisioning bypass** — Why demo provisioning skips the access request pipeline instead of auto-approving a request. The access request flow is designed for real prospects (OTP verification, admin review). Demo provisioning is an admin tool — forcing it through the approval pipeline adds friction for no benefit.
3. **Demo data seeding strategy** — Why demo data is seeded via a service rather than SQL scripts or database snapshots. Service-based seeding uses the existing entity layer (validations, audit events, lifecycle transitions), stays in sync as entities evolve across phases, and is profile-aware.
4. **Tenant cleanup safety model** — Why cleanup is restricted to PILOT/COMPLIMENTARY billing methods. Deleting a paying tenant's data is catastrophic and irreversible. The billing method acts as a safety classification: if you're paying, you're protected. Upgrading a demo tenant to a real tenant (changing billing method to DEBIT_ORDER/PAYFAST) automatically protects it from accidental cleanup.

***

## Style and Boundaries

- Billing method dimension: extend existing `billing/` package — add `BillingMethod` enum, modify `Subscription` entity
- Admin billing endpoints: extend existing `billing/` package controllers, add `AdminBillingService`
- Demo provisioning: new `demo/` package under backend root (or `platform/demo/` if a platform package exists). `DemoProvisionService`, `DemoDataSeeder`, `DemoCleanupService`
- Demo data seeders: `demo/seed/` sub-package. `BaseDemoDataSeeder` (shared utilities), `GenericDemoDataSeeder`, `AccountingDemoDataSeeder`, `LegalDemoDataSeeder`
- Admin panel frontend: extend existing platform admin routes (`/platform-admin/billing`, `/platform-admin/demo`)
- Follow existing controller discipline: thin controllers, service handles all logic
- Reuse existing `AuditEventBuilder` for all admin actions
- Reuse existing `KeycloakAdminClient` for org/user management
- Demo data uses realistic South African names, addresses, business types, and currency (ZAR)
- All admin endpoints behind `PlatformAdminSecurity` (existing Phase 39 infrastructure)
