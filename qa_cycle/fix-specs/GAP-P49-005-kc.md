# Fix Spec: GAP-P49-005 — Blank field produces dangling label

## Problem
When a template variable resolves to blank (e.g., `customer.customFields.company_registration_number` when not set), the rendered output shows "Registration Number: " with a trailing colon and whitespace but no value. The label is not hidden and no fallback like "N/A" is shown. This produces a visually messy document.

Example from QA:
```
Registration Number:
Engagement Type:
```

Both labels appear with blank values when custom fields are not set.

## Root Cause (confirmed)
The Tiptap document structure for these fields places the label as a `text` node and the value as a `variable` node in the same `paragraph`:
```json
{
  "type": "paragraph",
  "content": [
    { "type": "text", "marks": [{"type": "bold"}], "text": "Registration Number:" },
    { "type": "text", "text": " " },
    { "type": "variable", "attrs": { "key": "customer.customFields.acct_company_registration_number" } }
  ]
}
```

When the variable resolves to `""` (empty string, from `VariableFormatter.format(null, ...)` returning `""`), the label paragraph renders but the value is invisible. The `TiptapRenderer.resolveVariable()` method (line 276-289) returns `""` for null values. There is no mechanism to hide the entire paragraph when the variable is blank.

The TiptapRenderer already supports `conditionalBlock` nodes (line 181-193) with operators like `isNotEmpty`. But the templates currently wrap these field lines in plain `paragraph` nodes instead of `conditionalBlock` nodes.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java` (line 284: `if (current == null) return "";`)

## Fix
### Option A: Wrap label+value in conditionalBlock (recommended, no code change)
Update the template JSON to wrap each label+value pair in a `conditionalBlock` that only renders when the value is non-empty:

```json
{
  "type": "conditionalBlock",
  "attrs": {
    "fieldKey": "customer.customFields.acct_company_registration_number",
    "operator": "isNotEmpty"
  },
  "content": [
    {
      "type": "paragraph",
      "content": [
        { "type": "text", "marks": [{"type": "bold"}], "text": "Registration Number:" },
        { "type": "text", "text": " " },
        { "type": "variable", "attrs": { "key": "customer.customFields.acct_company_registration_number" } }
      ]
    }
  ]
}
```

This uses the existing `conditionalBlock` node type and `isNotEmpty` operator already supported by the TiptapRenderer (line 181-193). No backend code changes needed.

Templates to update:
- `engagement-letter-tax-return.json` — wrap SARS Tax Reference, Tax Year, SARS Submission Deadline
- `engagement-letter-bookkeeping.json` — wrap Registration Number, Engagement Type
- `fica-confirmation.json` — wrap Verification Date
- Any other templates with optional custom field labels

File paths:
- `backend/src/main/resources/template-packs/accounting-za/engagement-letter-tax-return.json`
- `backend/src/main/resources/template-packs/accounting-za/engagement-letter-bookkeeping.json`
- `backend/src/main/resources/template-packs/accounting-za/fica-confirmation.json`

### Option B: Renderer-level "N/A" fallback (alternative)
Modify `TiptapRenderer.resolveVariable()` to return "N/A" or "--" instead of `""` for null values. This is simpler but less flexible (all blank fields get the same treatment, and some contexts may prefer hiding the field entirely).

Not recommended because it conflates "field not applicable" with "field not yet populated".

## Scope
Backend (template JSON only, no Java code changes for Option A)
Files to modify:
- `backend/src/main/resources/template-packs/accounting-za/engagement-letter-tax-return.json`
- `backend/src/main/resources/template-packs/accounting-za/engagement-letter-bookkeeping.json`
- `backend/src/main/resources/template-packs/accounting-za/fica-confirmation.json`
Migration needed: no (templates are re-seeded on startup via `TemplatePackSeeder`)

## Verification
- Set custom fields on a test customer, then clear one field (e.g., remove `sars_tax_reference`)
- Generate the tax return engagement letter
- Verify the "SARS Tax Reference:" line is completely hidden (not showing with blank value)
- Set the field, regenerate, verify the line appears with the value
- Re-run Track T5.6

## Estimated Effort
S (< 30 min) — JSON template edits only, no Java code changes
