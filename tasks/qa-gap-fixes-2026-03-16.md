# QA Gap Fixes — 2026-03-16

Parent branch: `qa-gap-fixes-2026-03-16` (off `main`)
PRs target: `qa-gap-fixes-2026-03-16`
Final PR: `qa-gap-fixes-2026-03-16` → `main`

## Gaps Targeted

| ID | Summary | Priority | Effort | Status |
|----|---------|----------|--------|--------|
| GAP-019 | Currency displays as USD not ZAR | P1 | S | DONE (PR #696) |
| GAP-008B | FICA field groups not auto-attached during customer creation | P1 | S | DONE (PR #697) |
| GAP-010 | Trust-specific custom fields missing | P1 | M | DONE (PR #699) |
| GAP-009 | FICA checklist does not filter by entity type | P1 | M | DONE (PR #701) |
| GAP-020 | Portal contacts required for information requests | P2 | S | DONE (PR #698) |
| GAP-029 | React #418 hydration mismatch on all pages | P2 | S | DONE (PR #700) |

---

## Slice 1: GAP-019 — Currency Display Fix

**Branch**: `fix/gap-019-currency-display`
**Effort**: Small (backend + frontend)

### Root Cause
1. Backend: `TenantProvisioningService.setVerticalProfile()` creates `OrgSettings` with hardcoded `"USD"` — never reads the vertical profile JSON's `currency` field
2. Frontend: `formatCurrency()` in `lib/format.ts` uses `Intl.NumberFormat("en-US")` — produces `$` symbol even when currency is `ZAR`
3. Two retainer components hardcode `"USD"` literal instead of using org settings

### Changes

**Backend:**
- `TenantProvisioningService.java` — In `setVerticalProfile()`, after storing the profile, load the vertical profile JSON and apply its `currency` field to `OrgSettings.defaultCurrency`
- Test: Verify provisioning with `accounting-za` sets `defaultCurrency = "ZAR"`

**Frontend:**
- `lib/format.ts:53` — Replace hardcoded `"en-US"` with a locale derived from the currency code. Use a `currencyToLocale` map: `{ ZAR: "en-ZA", USD: "en-US", GBP: "en-GB", EUR: "de-DE" }` with `"en-US"` fallback
- `app/(app)/org/[slug]/retainers/[id]/page.tsx:127` — Replace `"USD"` literal with `defaultCurrency` from org settings
- `components/retainers/close-period-dialog.tsx:150,170,171` — Replace `"USD"` literals with `defaultCurrency` prop

**Seed Data (E2E):**
- `compose/seed/lib/rates-budgets.sh` — Change `"currency": "USD"` → `"currency": "ZAR"`
- `compose/seed/lib/invoices.sh` — Same
- `compose/seed/lib/proposals.sh` — Same

### Tests
- Backend: Integration test for provisioning with vertical profile currency
- Frontend: Existing format tests should be updated for locale-aware formatting
- Build: `./mvnw clean verify -q` + `pnpm build && pnpm test`

---

## Slice 2: GAP-008B — Auto-Attach FICA Field Groups

**Branch**: `fix/gap-008b-auto-attach-field-groups`
**Effort**: Small (single-line JSON + version bump)

### Root Cause
`accounting-za-customer.json` has `"autoApply": false`. The system's auto-apply mechanism works correctly — `common-customer.json` uses `"autoApply": true` and appears in Step 2. The FICA group just needs the same flag.

### Changes

**Backend:**
- `backend/src/main/resources/field-packs/accounting-za-customer.json` — Change `"autoApply": false` → `"autoApply": true`, bump `"version"` from 1 to 2
- The `PackReconciliationRunner` will detect the version change on next startup and re-apply, updating the `auto_apply` flag and retroactively attaching the group to existing customers

### Tests
- Backend: Verify that `FieldDefinitionService.getIntakeFields(CUSTOMER)` returns the accounting-za group when `autoApply=true`
- Build: `./mvnw clean verify -q` + `pnpm build`

---

## Slice 3: GAP-010 — Trust-Specific Custom Fields

**Branch**: `fix/gap-010-trust-custom-fields`
**Effort**: Medium (new field pack JSON + visibility conditions)

### Root Cause
No trust-specific fields exist. The existing `acct_company_registration_number` field is CIPC-specific and semantically wrong for trusts.

### Changes

**Backend:**
- Create `backend/src/main/resources/field-packs/accounting-za-customer-trust.json`:
  - Pack ID: `accounting-za-customer-trust`
  - Vertical profile: `accounting-za`
  - Group: `accounting_za_trust_details` ("SA Accounting — Trust Details")
  - `autoApply: true` (will appear in intake wizard)
  - Fields (all with `visibilityCondition: { dependsOnSlug: "acct_entity_type", operator: "eq", value: "TRUST" }`):
    - `trust_registration_number` (TEXT, required) — Master's Office reference number
    - `trust_deed_date` (DATE, required) — Date trust deed was executed
    - `trust_type` (DROPDOWN, required) — Options: INTER_VIVOS, TESTAMENTARY, BUSINESS
    - `trustee_names` (TEXT, optional) — Names of all trustees (comma-separated)
    - `trustee_type` (DROPDOWN, optional) — APPOINTED, EX_OFFICIO, BOTH
    - `letters_of_authority_date` (DATE, optional) — Date Letters of Authority were issued

- Note: The `visibilityCondition` on each field means they only display when `acct_entity_type = TRUST` is selected. The group may appear for all customers, but the fields inside are hidden unless entity type is Trust. This uses the existing `isFieldVisible()` mechanism in the frontend — no frontend changes needed.

- Important: The `dependsOnSlug: "acct_entity_type"` field is in the `accounting_za_client` group (Slice 2 makes it auto-apply). The trust group must declare `"dependsOn"` referencing the client group so it's resolved correctly.

### Tests
- Backend: Verify field pack seeding creates the group and fields
- Backend: Verify `visibilityCondition` is persisted correctly
- Build: `./mvnw clean verify -q` + `pnpm build`

---

## Slice 4: GAP-009 — Entity-Type-Specific FICA Checklists

**Branch**: `fix/gap-009-entity-type-checklist`
**Effort**: Medium (new column + 3 entity-type-specific packs)

### Root Cause
`ChecklistTemplateItem` has no `applicableEntityTypes` field. All 9 FICA items show for every customer regardless of entity type. The coarse `CustomerType` enum (INDIVIDUAL/COMPANY/TRUST) is insufficient — SA FICA needs 6 entity types from the `acct_entity_type` custom field.

### Architecture Decision
Add `applicable_entity_types` (JSONB, nullable `List<String>`) to `ChecklistTemplateItem`. At instantiation time, filter items by checking the customer's `acct_entity_type` custom field value against each item's `applicableEntityTypes`. `null` = applicable to all (backwards compatible).

This is preferred over separate templates because:
- One template to maintain, not 3-6
- Items shared across entity types don't need duplication
- Existing instantiation logic needs minimal changes

### Changes

**Backend — Migration:**
- New tenant migration `V72__checklist_item_entity_type_filter.sql`:
  ```sql
  ALTER TABLE checklist_template_items ADD COLUMN applicable_entity_types JSONB;
  ```

**Backend — Entity:**
- `ChecklistTemplateItem.java` — Add `applicableEntityTypes` field (`List<String>`, `@JdbcTypeCode(SqlTypes.JSON)`)
- `ChecklistInstanceItem.java` — Add snapshot field `applicableEntityTypes` for audit trail

**Backend — Service:**
- `ChecklistInstanceService.createFromTemplate()` — When iterating template items, if `item.getApplicableEntityTypes() != null`, look up the customer's `acct_entity_type` custom field value and skip the item if not in the list
- `ChecklistInstantiationService.instantiateForCustomer()` — Pass customer to instance creation so entity type filtering can occur

**Backend — Pack Definition:**
- `CompliancePackDefinition.CompliancePackItem` — Add `applicableEntityTypes` field
- `CompliancePackSeeder` — Map the field during seeding

**Backend — Pack JSON:**
- Update `compliance-packs/fica-kyc-za/pack.json` to add `applicableEntityTypes` per item:
  - Items 1, 2, 4, 5, 9 (ID/Residence/Tax Clearance/Bank/Source of Funds): `null` (all types)
  - Item 3 (Company Registration CM29): `["PTY_LTD", "CC", "NPC"]` — NOT for SOLE_PROPRIETOR, TRUST, PARTNERSHIP
  - Item 6 (Proof of Business Address): `null` (optional, all types)
  - Item 7 (Resolution/Mandate): `["PTY_LTD", "CC", "NPC", "TRUST"]` — NOT for SOLE_PROPRIETOR
  - Item 8 (Beneficial Ownership): `["PTY_LTD", "CC", "NPC", "TRUST"]` — NOT for SOLE_PROPRIETOR
  - NEW Item 10: "Letters of Authority (Master's Office)" — `["TRUST"]` only, required
  - NEW Item 11: "Trust Deed (Certified Copy)" — `["TRUST"]` only, required

- Bump pack `version` to trigger re-seeding

**Backend — DTO:**
- `ChecklistTemplateDtos` — Include `applicableEntityTypes` in response so frontend can display relevance info

**Frontend:**
- `ChecklistInstancePanel.tsx` — No changes needed (items are filtered at instantiation time, not display time)
- Optionally: Show a note on the "Add Checklist" dialog that items will be filtered by entity type

### Tests
- Backend: Test instantiation with a TRUST customer — verify Company Registration item is excluded, Letters of Authority is included
- Backend: Test instantiation with a SOLE_PROPRIETOR — verify Company Registration and Beneficial Ownership are excluded
- Backend: Test instantiation with no `acct_entity_type` set — verify all items included (backwards compatible)
- Build: `./mvnw clean verify -q` + `pnpm build`

---

## Slice 5: GAP-020 — Auto-Create Portal Contact

**Branch**: `fix/gap-020-auto-portal-contact`
**Effort**: Small (missing endpoint + auto-creation hook)

### Root Cause
Two issues:
1. `GET /api/customers/{id}/portal-contacts` endpoint does not exist — frontend silently catches 404 and shows empty
2. No auto-creation of portal contacts from customer email during lifecycle transition

### Changes

**Backend — New Endpoint:**
- `CustomerController.java` — Add `GET /api/customers/{id}/portal-contacts`:
  ```java
  @GetMapping("/{id}/portal-contacts")
  public List<PortalContactSummary> getPortalContacts(@PathVariable UUID id) {
      return portalContactService.listByCustomerId(id);
  }
  ```
- `PortalContactService.java` — Add `listByCustomerId(UUID customerId)` method returning `List<PortalContactSummary>`
- Add `PortalContactSummary` projection/DTO with `id`, `displayName`, `email`

**Backend — Auto-Creation:**
- `CustomerLifecycleService.java` — In the `PROSPECT → ONBOARDING` block (lines 144-148), after checklist instantiation, auto-create a portal contact:
  ```java
  if (!portalContactRepository.existsByEmailAndCustomerId(customer.getEmail(), customer.getId())) {
      portalContactService.createContact(orgId, customer.getId(), customer.getEmail(), customer.getName(), ContactRole.PRIMARY);
  }
  ```
- Inject `PortalContactService` and `PortalContactRepository` into `CustomerLifecycleService`

### Tests
- Backend: Test `GET /api/customers/{id}/portal-contacts` returns contacts
- Backend: Test `PROSPECT → ONBOARDING` transition creates a portal contact from customer email
- Backend: Test no duplicate portal contact if one already exists
- Build: `./mvnw clean verify -q` + `pnpm build`

---

## Slice 6: GAP-029 — Hydration Mismatch Fix

**Branch**: `fix/gap-029-hydration-mismatch`
**Effort**: Small (targeted fixes in ~10 files)

### Root Cause
Three categories of hydration mismatch:
1. Server Components calling `Date.now()` / `new Date()` — relative time output differs between SSR and hydration
2. `toLocaleDateString()` without explicit locale — server/client locale divergence
3. `window.location.origin` fallback in SSR context

### Changes

**Fix 1 — Relative Date Components (Primary):**
Create `components/ui/relative-date.tsx` — a `"use client"` component:
```tsx
"use client";
export function RelativeDate({ iso }: { iso: string }) {
  const [text, setText] = useState("");
  useEffect(() => { setText(formatRelativeDate(iso)); }, [iso]);
  return <span suppressHydrationWarning>{text}</span>;
}
```

Replace direct `formatRelativeDate()` calls in Server Components:
- `components/activity/activity-item.tsx:62` → `<RelativeDate iso={item.occurredAt} />`
- `components/projects/overview-tab.tsx:365` → `<RelativeDate iso={item.occurredAt} />`

**Fix 2 — Date.now() in Server Components:**
- `components/compliance/OnboardingPipelineSection.tsx:34` — Wrap days-elapsed calculation in a client component or use `suppressHydrationWarning`
- `components/compliance/DataRequestsSection.tsx:16` — Same treatment for deadline countdown
- `components/my-work/upcoming-deadlines.tsx:14` — Same for days remaining

**Fix 3 — Locale-less Date Formatting:**
- `components/templates/template-docx-sections.tsx:66` — Change `toLocaleDateString()` → `toLocaleDateString("en-ZA")`
- `components/compliance/RetentionCheckResults.tsx:59,75` — Change `toLocaleString()` → `toLocaleString("en-ZA")`
- `components/email/DeliveryLogTable.tsx:49` — Change `toLocaleString(undefined, ...)` → `toLocaleString("en-ZA", ...)`

**Fix 4 — Window check:**
- `components/integrations/PaymentIntegrationCard.tsx:219-221` — Move `baseUrl` into `useEffect`/`useState` to avoid SSR/client mismatch

### Tests
- Frontend: Verify no `suppressHydrationWarning` usage on `<body>` (granular only)
- Build: `pnpm build && pnpm test`

---

## Execution Order

1. **Slice 1 (GAP-019)** — Currency. No dependencies. Backend + frontend.
2. **Slice 2 (GAP-008B)** — Auto-attach. No dependencies. One-line JSON change.
3. **Slice 3 (GAP-010)** — Trust fields. Depends on Slice 2 (needs `accounting_za_client` group auto-applied for `dependsOn`).
4. **Slice 4 (GAP-009)** — Checklist filtering. Independent but benefits from Slice 3 being done (trust-specific items reference trust fields).
5. **Slice 5 (GAP-020)** — Portal contacts. Independent.
6. **Slice 6 (GAP-029)** — Hydration. Independent frontend-only.

## Review Process
- Each PR undergoes code review via `code-reviewer` agent before merge
- Backend changes: `./mvnw clean verify -q` must pass
- Frontend changes: `pnpm build && pnpm test` must pass
- Each slice is a separate PR to `qa-gap-fixes-2026-03-16`
