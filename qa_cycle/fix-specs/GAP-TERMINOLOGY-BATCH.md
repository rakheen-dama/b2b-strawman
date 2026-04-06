# Fix Spec: GAP-D30-03 + GAP-D60-01 + GAP-D30-05 — Terminology batch (LOW)

## Problems
Three related terminology issues when `legal-za` profile is active:

1. **GAP-D30-03**: Invoice list page heading says "Invoices" and button says "New Invoice" instead of "Fee Notes"/"New Fee Note"
2. **GAP-D60-01**: Profitability report heading says "Project Profitability Report" and column says "Project" instead of "Matter Profitability Report"/"Matter"
3. **GAP-D30-05**: Project column on invoice line items shows `{client} - {type}` template placeholder — same root cause as GAP-D1-07, will be fixed by that spec

## Root Cause
Hardcoded English strings not using the `t()` translation function or the vertical profile terminology map.

## Fix

### GAP-D30-03: Invoice list headings
Find the invoice list page (`frontend/app/(app)/org/[slug]/invoices/page.tsx`) and replace hardcoded "Invoices" / "New Invoice" with `t("invoices")` / `t("new_invoice")`.

### GAP-D60-01: Report headings
Find the profitability report component and replace "Project Profitability Report" with `t("project_profitability_report")` and column header "Project" with `t("project")`.

### GAP-D30-05
Resolved by GAP-D1-07 fix (template name token resolution).

## Scope
- 2-3 frontend files
- Translation key additions if not already present

## Verification
1. With legal-za profile, navigate to Invoices — heading shows "Fee Notes"
2. Navigate to Reports > Profitability — heading shows "Matter Profitability Report"
3. Column headers show "Matter" not "Project"

## Estimated Effort
30 minutes total for both
