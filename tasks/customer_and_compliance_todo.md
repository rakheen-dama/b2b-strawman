# Customer Onboarding & Compliance Fixes

**Date:** 2026-02-20
**Status:** Planning complete, ready for implementation
**Reference:** `docs/findings-customer-lifecycle-onboarding.md`

---

## Phase 1 — Quick Wins (No Test Breakage)

### 1.1 Fix SettingsResponse to Include Compliance Pack Status
- [x] Expand `OrgSettingsController.SettingsResponse` to include `compliancePackStatus`, `dormancyThresholdDays`, `dataRequestDeadlineDays`
- [x] Update `OrgSettingsService.getSettingsWithBranding()` to populate the new fields
- [x] Verify frontend compliance settings page (`/org/[slug]/settings/compliance`) now shows seeded packs
- [x] Write integration test for `GET /api/settings` returning compliance pack data

**Files:**
- `backend/.../orgsettings/OrgSettingsController.java` — expand `SettingsResponse` record
- `backend/.../orgsettings/OrgSettingsService.java` — populate new fields
- `frontend/lib/types.ts` — verify `OrgSettings` interface already has the fields (it does)

### 1.2 Add Customer Type Selector to Create Form
- [x] Add `customerType` dropdown to `CreateCustomerDialog` (Individual / Company / Trust)
- [x] Add `customerType` to `CreateCustomerRequest` in `frontend/lib/types.ts`
- [x] Update `createCustomer` server action to send `customerType` in POST body
- [x] Add `customerType` to `EditCustomerDialog` (display + edit)
- [x] Update `UpdateCustomerRequest` to include `customerType`

**Files:**
- `frontend/components/customers/create-customer-dialog.tsx` — add Select dropdown
- `frontend/components/customers/edit-customer-dialog.tsx` — add Select dropdown
- `frontend/lib/types.ts` — add `TRUST` to `CustomerType`, add field to request types
- `frontend/app/(app)/org/[slug]/customers/actions.ts` — pass `customerType` in form data

### 1.3 Add TRUST to Frontend CustomerType
- [x] Update `CustomerType` from `"INDIVIDUAL" | "COMPANY"` to `"INDIVIDUAL" | "COMPANY" | "TRUST"`
- [x] Update checklist template form's `CUSTOMER_TYPES` array to include Trust

### 1.4 Reseed Compliance Packs for Existing Tenants (PR #272)
- [x] Add `CompliancePackReseedRunner` — runs on boot, seeds packs for all existing tenants
- [x] Add `@Order(50)` to `TenantMigrationRunner` to guarantee migrations run before reseeder
- [x] Add `PATCH /api/settings/compliance` endpoint for dormancy/data-request settings
- [x] Add missing `setDormancyThresholdDays` setter on `OrgSettings`
- [x] Integration tests for reseeder idempotency + PATCH endpoint

**Files:**
- `frontend/lib/types.ts` (line 121)
- `frontend/components/compliance/CreateChecklistTemplateForm.tsx` (lines 16-19)

---

## Phase 2 — Lifecycle Default Fix (Test Migration Required)

### 2.1 Create Shared Test Factory
- [ ] Create `TestCustomerFactory` utility class in test sources
- [ ] `createActiveCustomer(name, email, memberId)` — uses 8-arg constructor with explicit `LifecycleStatus.ACTIVE`
- [ ] `createCustomerWithStatus(name, email, memberId, status)` — configurable lifecycle status
- [ ] `createProspectCustomer(name, email, memberId)` — uses PROSPECT (new default)

**File:** `backend/src/test/java/.../testutil/TestCustomerFactory.java` (new)

### 2.2 Change Customer Default to PROSPECT
- [ ] Change `Customer.java` line 94: `this.lifecycleStatus = LifecycleStatus.PROSPECT`
- [ ] Update all three constructors to default to PROSPECT instead of ACTIVE

**File:** `backend/.../customer/Customer.java` (lines 82-120)

### 2.3 Migrate Affected Test Files (~21 files, ~195 tests)

**Retainer tests (4 files, ~47 tests):**
- [ ] `RetainerAgreementServiceTest.java` — update `createCustomer()` helper to use factory
- [ ] `RetainerPeriodServiceTest.java` — update `createCustomer()` helper
- [ ] `RetainerConsumptionListenerTest.java` — update `createHourBankSetup()` helper
- [ ] `RetainerNotificationTest.java` — update customer creation

**Retainer controller tests (3 files, ~38 tests):**
- [ ] `RetainerAgreementControllerTest.java` — transition customer to ACTIVE after API creation
- [ ] `RetainerPeriodControllerTest.java` — same pattern
- [ ] `RetainerSummaryControllerTest.java` — same pattern

**Invoice tests (5 files, ~53 tests):**
- [ ] `InvoiceIntegrationTest.java` — update `@BeforeAll` customer creation
- [ ] `InvoiceLifecycleIntegrationTest.java` — update `@BeforeAll` + time entry setup
- [ ] `InvoiceControllerIntegrationTest.java` — update `@BeforeAll`
- [ ] `UnbilledTimeIntegrationTest.java` — update `@BeforeAll`
- [ ] `InvoicePreviewIntegrationTest.java` — update customer creation

**Time entry tests (1 file, ~5 tests):**
- [ ] `TimeEntryBillingIntegrationTest.java` — update line 121 comment + customer creation

**Schedule tests (2 files, ~6 tests):**
- [ ] `RecurringScheduleServiceTest.java` — update `@BeforeAll`
- [ ] `RecurringScheduleExecutorTest.java` — fix 1 inline 6-arg usage (line 599)

**Report tests (2 files, ~14 tests):**
- [ ] `CustomerProfitabilityTest.java` — update `setup()` customer creation
- [ ] `OrgProfitabilityTest.java` — same

**Customer/portal tests (2 files, ~13 tests):**
- [ ] `CustomerProjectIntegrationTest.java` — transition customer after API creation
- [ ] `PortalIntegrationTest.java` — already uses lifecycleService, verify transitions

**Audit tests (1 file, ~3 tests):**
- [ ] `CustomerServiceAuditTest.java` — transition customer after API creation

**Lifecycle/readiness tests (1 file):**
- [ ] `CustomerReadinessServiceTest.java` — update assertions (default now PROSPECT)

### 2.4 Fix Frontend Lifecycle Label
- [ ] Change `ONBOARDING → ACTIVE` label from "Reactivate" to "Activate"

**File:** `frontend/components/compliance/LifecycleTransitionDropdown.tsx` (line 30)

---

## Phase 3 — Design Gap Fixes (Optional, Lower Priority)

### 3.1 Remove PROSPECT → ACTIVE Shortcut
- [ ] Remove `ACTIVE` from `PROSPECT.allowedTransitions` in `LifecycleStatus.java`
- [ ] Update frontend `ALLOWED_TRANSITIONS` map to remove `PROSPECT → ACTIVE`
- [ ] Force all customers through onboarding

### 3.2 Add OFFBOARDED → ACTIVE Reactivation
- [ ] Add `ACTIVE` to `OFFBOARDED.allowedTransitions` in `LifecycleStatus.java`
- [ ] Update frontend transition map
- [ ] Add "Reactivate" transition label and confirmation dialog

### 3.3 Align Archive with Lifecycle
- [ ] When archiving a customer, set `lifecycleStatus = OFFBOARDED` if not already terminal
- [ ] When unarchiving, restore to `DORMANT` (not ACTIVE)

### 3.4 Auto-Dormancy Transition
- [ ] Add scheduled job to run `CustomerLifecycleService.runDormancyCheck()`
- [ ] Auto-transition candidates from ACTIVE → DORMANT
- [ ] Send notification to org admins

---

## Phase 4 — Checklist Document Picker (Frontend Only)

### 4.1 Replace UUID Text Input with Document Dropdown
- [ ] Thread `customerDocuments` prop from `page.tsx` → `ChecklistInstancePanel` → `ChecklistInstanceItemRow`
- [ ] Replace `<Input placeholder="Document ID (UUID)">` with a `<Select>` dropdown showing document filenames
- [ ] Filter dropdown to `status === "UPLOADED"` documents only
- [ ] Handle empty state: show "No documents uploaded" when customer has no documents

**No backend changes needed** — `GET /api/documents?scope=CUSTOMER&customerId=<id>` already exists and is already fetched server-side in `page.tsx` (line 91).

**Files:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — pass `customerDocuments` to `ChecklistInstancePanel`
- `frontend/components/compliance/ChecklistInstancePanel.tsx` — accept + forward `customerDocuments` prop
- `frontend/components/compliance/ChecklistInstanceItemRow.tsx` — swap `<Input>` for `<Select>` (lines 181–186)

---

## Verification Checklist

After all phases:
- [ ] Create a new customer via UI → starts as PROSPECT
- [ ] Compliance packs visible in Settings → Compliance
- [ ] Customer type (Individual/Company/Trust) selectable on creation
- [ ] "Start Onboarding" action card appears for PROSPECT customers
- [ ] Clicking "Start Onboarding" transitions to ONBOARDING + instantiates generic checklist
- [ ] Completing all checklist items auto-transitions to ACTIVE
- [ ] ACTIVE customer can create projects, invoices, time entries
- [ ] All backend tests pass (`./mvnw verify -q`)
- [ ] All frontend tests pass (`pnpm test`)

---

## Key Files Reference

| Category | Path |
|----------|------|
| Customer entity | `backend/.../customer/Customer.java` |
| Lifecycle enum | `backend/.../customer/LifecycleStatus.java` |
| Lifecycle service | `backend/.../compliance/CustomerLifecycleService.java` |
| Lifecycle guard | `backend/.../compliance/CustomerLifecycleGuard.java` |
| Checklist instantiation | `backend/.../compliance/ChecklistInstantiationService.java` |
| Pack seeder | `backend/.../compliance/CompliancePackSeeder.java` |
| Settings controller | `backend/.../orgsettings/OrgSettingsController.java` |
| Settings service | `backend/.../orgsettings/OrgSettingsService.java` |
| V29 migration | `backend/.../resources/db/migration/tenant/V29__customer_compliance_lifecycle.sql` |
| Frontend create dialog | `frontend/components/customers/create-customer-dialog.tsx` |
| Frontend edit dialog | `frontend/components/customers/edit-customer-dialog.tsx` |
| Frontend types | `frontend/lib/types.ts` |
| Frontend transitions | `frontend/components/compliance/LifecycleTransitionDropdown.tsx` |
| Customer actions | `frontend/app/(app)/org/[slug]/customers/actions.ts` |
| Compliance settings | `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx` |
