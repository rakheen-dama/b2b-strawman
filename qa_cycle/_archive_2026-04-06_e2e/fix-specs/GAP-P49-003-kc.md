# Fix Spec: GAP-P49-003 — FICA verification date blank despite being populated

## Problem
The FICA Confirmation Letter template (CUSTOMER-scoped, slug `fica-confirmation-letter`) references `customer.customFields.fica_verification_date` at line 57. The QA agent set `fica_verification_date` to "2026-01-16" on Naledi Corp QA via API PUT (HTTP 200). The template renders `customer.name` correctly ("Naledi Corp QA") but the "Verification Date:" label shows a blank value. The field is defined as `fieldType: "DATE"` in the field pack (`accounting-za-customer.json`, line 142-147).

## Root Cause (hypothesis)
Same root cause pattern as GAP-P49-001. Two hypotheses:

**H1 (most likely): The custom field was cleared by a subsequent PUT call.** The `PUT /api/customers/{id}` endpoint replaces ALL custom fields. If a later update omitted `fica_verification_date` from the payload, the value was silently dropped. The QA agent performed multiple updates to Naledi (T0.3: "14 fields set") — if any subsequent PUT was missing this field, it would be erased.

**H2: DATE-type value was rejected by the validator.** The `CustomFieldValidator.validateDate()` method (line 187-215) parses the value via `LocalDate.parse(str)` and expects `YYYY-MM-DD` format. If the QA agent sent a different format (e.g., ISO-8601 with time component like `"2026-01-16T00:00:00Z"`), the validator would reject it and the entire PUT would fail with HTTP 400, not 200. Since the PUT returned 200, this is unlikely.

**H3: The `resolveDropdownLabels()` method inadvertently transforms the value.** Checking `TemplateContextHelper.resolveDropdownLabels()` (line 171-201): it only filters for `FieldType.DROPDOWN` fields and only modifies those. DATE fields pass through unchanged. This is NOT the root cause.

## Fix
1. **Verify at runtime**: Query Naledi's custom fields to check if `fica_verification_date` is present:
   ```sql
   SELECT custom_fields->'fica_verification_date' FROM customers WHERE name LIKE 'Naledi%';
   ```

2. **If field is missing**: Re-set via API PUT, ensuring ALL 14 fields are included in the payload (not just the date field). Test the template again.

3. **If field is present but still blank in template**: Add debug logging in `CustomerContextBuilder.buildContext()` (after line 72) to log the custom fields map:
   ```java
   log.debug("Customer {} customFields: {}", entityId,
       customer.getCustomFields() != null ? customer.getCustomFields().keySet() : "null");
   ```
   File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`

4. **Preventive fix (recommended)**: Add a `fica_verification_date` to the `requiredContextFields` on the FICA confirmation template definition. This ensures the validation service flags it as missing before generation:
   ```json
   "requiredContextFields": [
     {"entity": "customer", "field": "name"},
     {"entity": "customer", "field": "customFields"}
   ]
   ```
   Note: The current validation only checks top-level fields within an entity map, not nested custom field keys. Checking `customFields` as a whole only verifies the map exists, not individual keys within it. See GAP-P49-004 for the deeper validation improvement.

## Scope
Backend — investigation + debug logging
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java` (debug logging)
Migration needed: no

## Verification
- Query `custom_fields` JSONB for Naledi in the tenant schema
- Re-populate `fica_verification_date` on Naledi via API PUT with all fields
- Re-generate FICA Confirmation Letter
- Verify "Verification Date: 16 January 2026" appears (DATE format via `VariableFormatter.formatDate()`)
- Re-run Tracks T1.7 and T5.2

## Estimated Effort
S (< 30 min) — primarily investigation; likely same data issue as GAP-P49-001
