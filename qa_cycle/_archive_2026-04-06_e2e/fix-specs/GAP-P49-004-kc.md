# Fix Spec: GAP-P49-004 — No pre-generation warning for missing custom fields

## Problem
When generating a document from a template that references custom fields (e.g., `customer.customFields.company_registration_number`), the system generates silently even when those fields have blank/null values. The `TemplateValidationService.validateRequiredFields()` only checks fields listed in the template's `requiredContextFields` metadata. Most templates do not declare custom fields in their `requiredContextFields`, so `allPresent` returns `true` even when template variables will render as blank.

The validation service correctly checks nested entity fields (e.g., `{"entity": "customer", "field": "name"}`), but it cannot check individual custom field keys because custom fields are stored as a nested map (`customer.customFields.*`), and the validation only goes one level deep into the entity map.

## Root Cause (confirmed)
Two separate issues:

**Issue A: Templates don't declare custom fields as required.** The `requiredContextFields` metadata on templates (stored in `DocumentTemplate.requiredContextFields` JSONB) is either null or only includes top-level fields like `customer.name`. No templates currently declare custom field keys.

**Issue B: Validation cannot check nested paths.** The `TemplateValidationService.validateRequiredFields()` (line 53) does `((Map<String, Object>) entityMap).get(field)`. For a field like `customFields.sars_tax_reference`, this would look up key `"customFields.sars_tax_reference"` in the customer map, which doesn't exist. It would need to support dot-path traversal within the entity map.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java`

## Fix
### Option A: Template variable analysis-based warnings (recommended)
Instead of relying on manually-maintained `requiredContextFields`, automatically detect blank variables by analyzing the template's variable keys against the context.

1. Add a new method to `TemplateValidationService`:
   ```java
   public List<String> detectBlankVariables(Map<String, Object> document, Map<String, Object> context)
   ```
   This method uses `TemplateVariableAnalyzer.extractVariableKeys(document)` to get all variable keys in the template, then resolves each key against the context (using the same dot-path resolution as `TiptapRenderer.resolveVariable()`). Variables that resolve to null or blank are returned as warnings.

2. Call this in `GeneratedDocumentService.generateDocument()` (after line 141) and `PdfRenderingService.previewWithValidation()` (after line 157). Return warnings alongside the validation result.

3. On the frontend, display warnings in the generation dialog (Step 2 / preview step) as a yellow alert: "The following fields are blank and will appear empty in the document: [list]". Include a "Generate Anyway" button.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java` (new method)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` (call new method)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` (include in preview response)
- Frontend generation dialog component (display warnings)

### Option B: Support nested paths in requiredContextFields (simpler but manual)
Modify `TemplateValidationService.validateRequiredFields()` to support dot-path notation in the `field` value. For example, `{"entity": "customer", "field": "customFields.sars_tax_reference"}` would traverse `customerMap -> customFields -> sars_tax_reference`.

This is simpler to implement but requires manually maintaining `requiredContextFields` on each template, which is error-prone and won't catch variables added via template editing.

## Scope
Backend + Frontend
Files to modify: see Option A above
Migration needed: no

## Verification
- Generate an engagement letter with a blank `company_registration_number` on the customer
- Verify a warning is shown in the preview step listing the blank field
- Verify the user can still proceed by acknowledging the warning
- Re-run Track T5.6

## Estimated Effort
M (1-2 hr) for Option A; S (30 min) for Option B
