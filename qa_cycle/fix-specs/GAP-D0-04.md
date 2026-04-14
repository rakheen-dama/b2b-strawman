# Fix Spec: GAP-D0-04 — Trust-specific required fields shown for all entity types

## Problem
In the New Client wizard Step 2 ("Additional Information"), trust-specific fields (Trust Registration Number*, Trust Deed Date*, Trust Type*) appear with required asterisks for ALL entity types (e.g., Sole Proprietor, Pty Ltd), not just Trust. They don't block submission for non-trust types, but the asterisks are visually misleading. Reported at Day 0 checkpoint 0.37.

## Root Cause (hypothesis)
The intake fields endpoint `GET /api/field-definitions/intake?entityType=CUSTOMER` returns ALL field groups for the CUSTOMER entity type, including the trust variant group (`accounting-za-customer-trust`). The endpoint in `FieldDefinitionController.java` line 75-78 does not accept a `customerType` parameter, and `FieldDefinitionService.getIntakeFields()` (line 77) loads all groups without filtering by variant.

The `accounting-za-customer-trust` field pack defines Trust Registration Number, Trust Deed Date, and Trust Type as `required: true` (confirmed in checkpoint results). Since the intake endpoint returns all groups regardless of the selected entity type in the wizard, these trust-specific required fields appear for all entity types.

## Fix
This is a LOW priority cosmetic issue — trust fields don't block submission for non-trust types.

Two approaches:

**Option A (Frontend — simpler)**: In the create customer dialog, after fetching intake fields and knowing the selected `customerType`, filter out the trust variant group on the client side. The trust group can be identified by its slug (`accounting-za-customer-trust` or by containing "trust" in the group name). Only show trust-variant fields when `customerType === "Trust"`.

**Option B (Backend — cleaner)**: Add an optional `variant` or `customerType` query param to the intake fields endpoint. When provided, filter groups to only include those matching the variant or the base (non-variant) groups.

For this cycle, Option A is recommended (less risk, smaller change).

In `frontend/components/customers/create-customer-dialog.tsx`:
1. After receiving intake field groups, filter out groups whose slug contains "trust" when the selected entity type is not "TRUST".
2. Re-filter when the entity type dropdown changes.

## Scope
Frontend
Files to modify:
- `frontend/components/customers/create-customer-dialog.tsx` — filter trust variant groups based on selected entity type
Files to create: none
Migration needed: no

## Verification
Re-run Day 0 checkpoint 0.37: Create new client, select "Sole Proprietor" as entity type, advance to Step 2. Trust-specific fields should NOT appear. Change entity type to "Trust" — trust fields should appear with required asterisks.

## Estimated Effort
S (< 30 min)
