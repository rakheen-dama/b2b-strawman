# Fix Spec: GAP-S4-02 — Legal FICA pack does not branch on client_type (TRUST / INDIVIDUAL)

## Priority
HIGH — breaks FICA s21A compliance promise for legal-za vertical. Trust clients get an
individual-biased pack with no trustee-specific items.

## Problem
Both INDIVIDUAL (Sipho) and TRUST (Moroka) clients are seeded with the same 11-item "Legal
Client Onboarding" pack. Trust Deed is wrongly Skippable, and there are no items for Letters of
Authority, Trustee IDs, Proof of trust banking, or SARS trust tax number.

## Root Cause (confirmed via grep)
Files:
- `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json:8` — `"customerType":
  "ANY"` — one pack for all client types.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java:41-43`
  — seeder filter:
  ```java
  templateRepository.findByActiveAndAutoInstantiateAndCustomerTypeIn(
      true, true, List.of(customerType, "ANY"));
  ```
  This already supports per-customer-type templates. **The infrastructure is correct; only the
  pack data needs to branch.**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackSeeder.java:125` —
  `pack.customerType()` flows into `new ChecklistTemplate(..., pack.customerType(), ...)`.
  Confirmed that changing pack.json's `customerType` will produce templates scoped correctly.

## Fix Steps
1. **Rename the existing pack** to INDIVIDUAL-specific:
   - Change `compliance-packs/legal-za-onboarding/pack.json`:
     - `"packId"` from `legal-za-onboarding` to `legal-za-individual-onboarding`
     - `"name"` from "SA Legal — Client Onboarding" to "SA Legal — Individual Client Onboarding"
     - `"customerType"` from `"ANY"` to `"INDIVIDUAL"`
     - Directory rename: `compliance-packs/legal-za-onboarding/` →
       `compliance-packs/legal-za-individual-onboarding/`
   - Remove "Company Registration Docs" and "Trust Deed" items (they don't belong on individual
     packs) — or leave them as `required: false` if product wants a single pack structure.
2. **Create a new trust-specific pack**:
   - New file: `compliance-packs/legal-za-trust-onboarding/pack.json` with:
     ```json
     {
       "packId": "legal-za-trust-onboarding",
       "name": "SA Legal — Trust Client Onboarding",
       "description": "South African FICA checklist for trust clients per FIC Act s21A — trustee verification, trust deed, Master's Office authority.",
       "version": "1.0.0",
       "verticalProfile": "legal-za",
       "jurisdiction": "ZA",
       "customerType": "TRUST",
       "checklistTemplate": {
         "name": "Legal Trust Client Onboarding",
         "slug": "legal-za-trust-client-onboarding",
         "autoInstantiate": true,
         "items": [
           { "name": "Trust Deed", "description": "Certified copy of the trust deed, including all amendments.", "sortOrder": 1, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Certified copy of trust deed", "dependsOnItemKey": null },
           { "name": "Letters of Authority", "description": "Master's Office Letters of Authority confirming trustee appointments.", "sortOrder": 2, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Letters of Authority (Master)", "dependsOnItemKey": null },
           { "name": "Trustee 1 ID", "description": "Certified ID copy of first trustee.", "sortOrder": 3, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Certified ID — Trustee 1", "dependsOnItemKey": null },
           { "name": "Trustee 2 ID", "description": "Certified ID copy of second trustee (if applicable).", "sortOrder": 4, "required": false, "requiresDocument": true, "requiredDocumentLabel": "Certified ID — Trustee 2", "dependsOnItemKey": null },
           { "name": "Proof of Trust Banking", "description": "Trust bank account confirmation letter or statement.", "sortOrder": 5, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Trust banking confirmation", "dependsOnItemKey": null },
           { "name": "SARS Trust Tax Number", "description": "SARS trust tax registration number.", "sortOrder": 6, "required": true, "requiresDocument": false, "requiredDocumentLabel": null, "dependsOnItemKey": null },
           { "name": "Beneficial Ownership Declaration", "description": "Declaration of trust beneficial owners per FIC Act s21A(2)(b).", "sortOrder": 7, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Beneficial ownership declaration", "dependsOnItemKey": null },
           { "name": "Source of Funds Declaration", "description": "Declaration of the source of trust funds.", "sortOrder": 8, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Source of funds declaration", "dependsOnItemKey": null },
           { "name": "Engagement Letter Signed", "description": "Signed engagement letter returned by the trustees.", "sortOrder": 9, "required": true, "requiresDocument": true, "requiredDocumentLabel": "Signed engagement letter", "dependsOnItemKey": null },
           { "name": "Conflict Check Performed", "description": "Conflict of interest check run against the trust name and all trustees.", "sortOrder": 10, "required": true, "requiresDocument": false, "requiredDocumentLabel": null, "dependsOnItemKey": null },
           { "name": "FICA Risk Assessment", "description": "Risk rating completed per FIC Act Risk Management and Compliance Programme.", "sortOrder": 11, "required": true, "requiresDocument": false, "requiredDocumentLabel": null, "dependsOnItemKey": "proof-of-trust-banking" },
           { "name": "Sanctions Screening", "description": "Trust and all trustees screened against UN + SA sanctions lists.", "sortOrder": 12, "required": true, "requiresDocument": false, "requiredDocumentLabel": null, "dependsOnItemKey": "trustee-1-id" }
         ]
       }
     }
     ```
3. **Pack versioning and reseed** — the `CompliancePackSeeder` tracks applied packs in
   `OrgSettings.compliancePackStatus`. For existing tenants (Mathebula), bumping the version is
   required. Options:
   - (a) Bump the renamed pack's version from `1.0.0` to `2.0.0` and add a new `2.0.0` to the
     new trust pack.
   - (b) Write a one-off repair in `PackReconciliationRunner` to drop the old
     `legal-za-onboarding` status entry so both new packs apply on next startup.
   - Recommend (a) for cleanliness.
4. **Existing customer cleanup**: the Moroka client already has the wrong 11-item pack
   instance. Since the instance is orphaned once the template is deleted/renamed, the QA turn
   should re-create Moroka (or a DB script should cancel the old instance + instantiate the
   new one on startup). Simpler: leave the QA turn to create a new trust client after the fix
   — the existing Moroka instance can be ignored.
5. **Add a companion COMPANY pack** (optional but recommended for parity):
   `compliance-packs/legal-za-company-onboarding/pack.json` with Company Registration Docs,
   Director IDs, BEE Certificate, etc. Out of scope for this cycle if the effort budget is
   tight — defer as GAP-S4-02.1 follow-up.

## Scope
- Backend resource files only (no Java changes — the seeder already supports customerType
  branching).
- Files to modify:
  - `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json` (rename +
    customerType change)
- Files to create:
  - `backend/src/main/resources/compliance-packs/legal-za-trust-onboarding/pack.json`
- Migration needed: no schema change; pack versioning handled by `OrgSettings.compliancePackStatus`

## Verification
1. Restart backend (pack seeder runs on startup).
2. Create a new TRUST client via the UI. Transition to ONBOARDING.
3. Onboarding tab should now show "Legal Trust Client Onboarding" with 12 items including
   Letters of Authority, Trustee 1 ID, Trustee 2 ID, Proof of Trust Banking, SARS Trust Tax
   Number. Trust Deed should be Required (not Skippable).
4. Create a new INDIVIDUAL client. Onboarding tab should show "Legal Individual Client
   Onboarding" with the original items (Proof of Identity, Proof of Address, etc.) — NO trustee
   items.

## Estimated Effort
M (~1 hr — pack.json creation + version bump + reseed verification)
