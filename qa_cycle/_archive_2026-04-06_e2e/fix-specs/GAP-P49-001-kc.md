# Fix Spec: GAP-P49-001 — SARS Tax Reference blank in Tax Return template

## Problem
The `engagement-letter-annual-tax-return` template (PROJECT-scoped) references `customer.customFields.sars_tax_reference` at line 55 of the template JSON. The QA agent confirmed Kgosi Holdings has `sars_tax_reference` set to "9012345678" via API PUT (HTTP 200). However, when the tax return engagement letter is generated for the Kgosi "Annual Tax Return 2026 Updated" project, the SARS Tax Reference label appears with a blank value. Critically, `customer.name` DOES resolve to "Kgosi Holdings QA Cycle2" in the same render, proving the customer is found via the `CustomerProject` join.

## Root Cause (hypothesis)
The `ProjectContextBuilder.buildContext()` resolves the customer and includes `customFields` in the customer map (lines 96-101). The `resolveDropdownLabels()` helper passes non-DROPDOWN fields through unchanged. The slug `sars_tax_reference` in the field pack (`accounting-za-customer.json`, line 40) matches the template variable key.

Two hypotheses to verify at runtime:

**H1 (most likely): Custom fields were replaced or cleared by a subsequent API call.** The `PUT /api/customers/{id}` endpoint does a FULL REPLACEMENT of custom fields (not a merge). If the QA agent later updated the customer without including `sars_tax_reference` in the payload, the field would be silently dropped.

**H2: The customer's custom fields JSONB is missing the key.** The `CustomFieldValidator.validate()` strips keys that don't match an active `FieldDefinition` slug (`CustomFieldValidator.java`, line 75-77). If the field definition for `sars_tax_reference` was somehow inactive for this tenant, the value would be stripped on save.

## Fix
1. **Add debug logging** in `ProjectContextBuilder.buildContext()` to log the customer customFields map contents at DEBUG level when building a PROJECT-scoped context.
   - File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java`
   - After line 101 (after resolving customFields), add: `log.debug("Project {} customer {} customFields keys: {}", entityId, resolvedCustomerId, customer.getCustomFields() != null ? customer.getCustomFields().keySet() : "null");`

2. **Verify at runtime**: Query the Kgosi customer's `custom_fields` JSONB directly:
   ```sql
   SELECT custom_fields FROM customers WHERE name LIKE 'Kgosi%';
   ```
   If `sars_tax_reference` is missing, the issue is a data problem (the QA agent's PUT lost the field). Re-set the field and re-test.

3. **No code change needed** if the root cause is H1 (data issue). If H2 (field definitions inactive), ensure the field pack is applied correctly.

## Scope
Backend — investigation + debug logging
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java` (add debug logging)
Migration needed: no

## Verification
- Query `custom_fields` JSONB for the Kgosi customer in the tenant schema
- Re-populate `sars_tax_reference` on Kgosi via API PUT (include ALL custom fields to avoid replacement issue)
- Re-generate the tax return engagement letter
- Verify "SARS Tax Reference: 9012345678" appears in the output
- Re-run Track T1.2

## Estimated Effort
S (< 30 min) — primarily investigation; likely a data issue rather than a code bug
