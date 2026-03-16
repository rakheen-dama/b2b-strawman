# Fix Spec: GAP-008B — FICA field groups not auto-attached during customer creation

## Problem
During customer creation wizard Step 2, only the "Contact & Address" field group is shown. The accounting-specific field groups ("SA Accounting — Client Details", "Company FICA Details", "FICA Compliance") are not auto-attached. Users must manually add them via "Add Group" on the customer detail page after creation. This is a UX friction issue — the 16 accounting fields should be part of the intake flow.

## Root Cause (hypothesis)
The customer creation wizard Step 2 shows field groups that are configured as "intake" groups (i.e., groups marked for display during entity creation). The accounting-specific field groups from the `accounting-za-customer.json` field pack and compliance packs are not configured with an intake flag.

The intake system is managed via the `prerequisite_contexts` or `intake` configuration on field groups. The customer creation dialog fetches intake fields from `/api/field-definitions/intake?entityType=CUSTOMER` (via `fetchIntakeFields`). Only field groups with an intake flag are returned.

The accounting field packs seed field groups but do not mark them as intake groups.

## Fix

### Option A: Mark accounting field groups as intake groups
**File**: `backend/src/main/resources/field-packs/accounting-za-customer.json`

Add an `intake: true` property to the field groups in the pack definition. This requires:
1. Adding an `intake` field to the `FieldGroup` entity if not already present
2. Setting `intake = true` during field pack seeding for accounting customer field groups

### Option B (simpler): Auto-attach accounting field groups for accounting-za tenants
In the `CustomerService.createCustomer()` method, if no `appliedFieldGroups` are provided, automatically attach all field groups that match the tenant's vertical profile.

### Option C (recommended — lowest risk): Update field pack JSON to include intake flag
Check if the `FieldGroup` entity already has an `intake` or `autoApply` property. If it does, set it in the field pack JSON. If not, this is a larger change.

This is a **major** gap but requires investigation into the intake system architecture. Marking as SPEC_READY with the understanding that the dev agent should investigate the intake configuration first.

**Investigation needed by dev agent:**
1. Check if `FieldGroup` entity has an `intake` or `autoApply` column
2. Check how `fetchIntakeFields` endpoint determines which groups to return
3. If no intake system exists for field groups, this is a WONT_FIX for this cycle (manual "Add Group" works as a workaround)

## Scope
Backend — field pack configuration or field group entity.

Files to investigate:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`
- `backend/src/main/resources/field-packs/accounting-za-customer.json`
- The `/api/field-definitions/intake` endpoint implementation

Files to modify: TBD based on investigation

Migration needed: possibly (if adding intake column to field_groups)

## Verification
Re-run Day 1 checkpoint 1.1:
1. Create a new customer with type "Company"
2. Step 2 should show "SA Accounting — Client Details" and "Company FICA Details" field groups alongside "Contact & Address"
3. All 16+ accounting fields should be available during creation

## Estimated Effort
M (30 min - 2 hr) — depends on whether intake system already supports field group flagging
