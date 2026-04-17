# Fix Spec: GAP-D0-07 — No onboarding checklist seeded for accounting-za profile

## Problem
When transitioning a client from PROSPECT to ONBOARDING, the Onboarding tab shows "No checklists yet." with a "Manually Add Checklist" button. No FICA/KYC checklist is automatically created. The accounting-za profile includes the `fica-kyc-za` compliance pack which seeds a checklist template, but the template has `"autoInstantiate": false`, so it's never automatically attached to customers during onboarding. Reported at Day 2 checkpoint 2.2.

## Root Cause (hypothesis)
In `backend/src/main/resources/compliance-packs/fica-kyc-za/pack.json` line 12:
```json
"autoInstantiate": false
```

The `ChecklistInstantiationService.instantiateForCustomer()` (line 42) queries for templates where `active = true AND autoInstantiate = true`. Since the FICA template has `autoInstantiate: false`, it's excluded from the auto-instantiation query.

Compare with the legal-za individual onboarding pack (`legal-za-individual-onboarding/pack.json`) which likely has `autoInstantiate: true`.

## Fix
1. In `backend/src/main/resources/compliance-packs/fica-kyc-za/pack.json`, change line 12:
   ```json
   "autoInstantiate": true
   ```

2. **Important**: This change only affects NEW tenants provisioned after the fix. For the existing QA tenant ("Thornton & Associates"), the checklist template was already seeded with `autoInstantiate = false` in the DB. To fix the existing tenant:
   - Option A: Run a SQL update on the tenant schema: `UPDATE checklist_templates SET auto_instantiate = true WHERE slug = 'fica-kyc-za-accounting';`
   - Option B: Trigger pack reconciliation which should update the existing template.

3. Verify that `ChecklistInstantiationService.instantiateForCustomer()` is called during the PROSPECT -> ONBOARDING transition. Check the lifecycle transition service to confirm it invokes checklist instantiation.

## Scope
Backend / Seed data
Files to modify:
- `backend/src/main/resources/compliance-packs/fica-kyc-za/pack.json` — set `autoInstantiate: true`
Files to create: none
Migration needed: no (pack seeder handles updates on reconciliation)

## Verification
Re-run Day 2 checkpoint: Create a new client, transition to ONBOARDING. Onboarding tab should automatically show the "FICA KYC — SA Accounting" checklist with 11 items (Certified ID Copy, Proof of Residence, Company Registration, Tax Clearance Certificate, Bank Confirmation Letter, etc.).

## Estimated Effort
S (< 30 min)
