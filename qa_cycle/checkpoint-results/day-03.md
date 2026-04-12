# Day 3 Checkpoint Results — Matter Creation from Template

**Date**: 2026-04-12
**Actor**: Bob Ndlovu (Admin)
**Scenario**: Sipho Dlamini litigation matter creation from template
**Branch**: `bugfix_cycle_demo_legal_day1_2026-04-12`

---

## Checkpoint Results

| CP | Description | Result | Evidence |
|----|-------------|--------|----------|
| 3.1 | On Sipho's client detail, click "New Matter" | PARTIAL | No "New Matter" button exists on the client detail page. Only "Link Project" (links existing projects). Matter created from the Matters list page via "New from Template" button instead. Gap: client detail lacks inline "New Matter" creation. |
| 3.2 | Select template: Litigation (Personal Injury / General) | PASS | Template dialog shows all 4 legal-za templates. "Litigation (Personal Injury / General)" selected with 9 tasks. |
| 3.3 | Fill matter fields: name, matter_type, case_number, court_name | PARTIAL | Matter name set to "Sipho Dlamini v. Standard Bank (civil)". Customer set to "Sipho Dlamini". However, `matter_type`, `case_number`, and `court_name` are NOT available as promoted fields in the "New from Template" dialog. The dialog only shows: Project name, Description, Customer, Project lead. Custom fields (Case Number, Court) were filled AFTER creation on the matter detail page via the "SA Legal -- Matter Details" custom fields section. The `matter_type` field is not present at all (neither in the creation dialog nor on the detail page custom fields). The description pre-populates "Matter type: LITIGATION" as text only. |
| 3.4 | Save -- matter created from template with pre-populated task list | PASS | Matter created successfully. Redirected to matter detail. 9 tasks pre-populated from template: Initial consultation & case assessment, Letter of demand, Issue summons / combined summons, File plea / exception / counterclaim, Discovery -- request & exchange documents, Pre-trial conference preparation, Trial / hearing attendance, Post-judgment -- taxation of costs / appeal, Execution -- warrant / attachment. All tasks Open, Medium priority. |
| 3.5 | Verify matter appears under Sipho's matter list | PASS | Navigated to Sipho's client detail. Projects tab shows 1 linked project: "Sipho Dlamini v. Standard Bank (civil)". Link navigates to matter detail. Header shows "1 project". |
| 3.6 | Navigate to matter detail -- verify task list is pre-populated from template (expect 5+ tasks) | PASS | Action Items tab shows 9 tasks from the Litigation template. All with descriptions (e.g., "Taking instructions, evaluating merits", "Pre-litigation demand per Prescription Act"). Task count badge shows "9". Overview shows 0/9 tasks complete. |
| 3.7 | Assign first task to Bob, second task to Carol | PASS | "Initial consultation & case assessment" assigned to Bob Ndlovu. "Letter of demand" assigned to Carol Mokoena. Required adding Bob and Carol as matter members first (via Members tab > Add Member), as the assignee dropdown only shows project members. Both assignments confirmed in task table. |
| 3.8 | Verify terminology: page heading says "Matter" (not "Project"), breadcrumb shows Clients > Sipho Dlamini > Matter > ... | PARTIAL | Breadcrumb: "Mathebula & Partners > Matters > Matter" -- correct. Page heading: "Sipho Dlamini v. Standard Bank (civil)" -- correct. "Client: Sipho Dlamini" label -- correct. FAILURES: (1) "Back to Projects" link (should be "Back to Matters"), (2) "Complete Project" button (should be "Complete Matter"), (3) "Project Info" field group label (should be "Matter Info"), (4) New from Template dialog labels: "Project name", "Customer (optional)", "Project lead", "Create Project" -- all use generic terms instead of legal vertical terminology, (5) Empty state on Matters page says "No projects yet" and "Projects organise your work..." |

---

## Summary

- **Total checkpoints**: 8
- **PASS**: 4 (CP 3.2, 3.4, 3.5, 3.6, 3.7 = 5 PASS actually)
- **PARTIAL**: 3 (CP 3.1, 3.3, 3.8)
- **FAIL**: 0

## New Gaps

| GAP_ID | Day / Checkpoint | Severity | Summary |
|--------|------------------|----------|---------|
| GAP-D3-01 | Day 3 / CP-3.1 | LOW | No "New Matter" button on client detail page. Only "Link Project" available. Users must navigate to Matters list page to create new matters. Scenario expects inline matter creation from client detail. |
| GAP-D3-02 | Day 3 / CP-3.3 | MED | `matter_type`, `case_number`, `court_name` fields are NOT available as promoted inputs in the "New from Template" dialog. The dialog only shows basic fields (name, description, customer, lead). Custom fields must be filled after creation on the detail page. The `matter_type` field is absent entirely as a dedicated field -- only present as text in the pre-filled description. |
| GAP-D3-03 | Day 3 / CP-3.8 | LOW | Matter detail page has multiple untranslated "Project" labels: "Back to Projects" link, "Complete Project" button, "Project Info" field group, "No projects yet" empty state. These survive the terminology override that correctly translates sidebar nav, breadcrumb, and page headings. Extends existing GAP-D1-01/D1-02 pattern to the matter detail page. |
| GAP-D3-04 | Day 3 / CP-3.8 | LOW | "New from Template" dialog uses generic terminology throughout: "Project name", "Customer (optional)", "Project lead (optional)", "Create Project". Should use vertical terms: "Matter name", "Client", "Matter lead", "Create Matter". |

## Console Errors

- 3 Radix hydration mismatch errors on the Matters list page (aria-controls IDs differ between SSR and client render). These are cosmetic React hydration warnings, not functional issues.
- 0 errors on the client detail page.

## Screenshots

- `qa_cycle/screenshots/cycle-2/day03-cp3.4-matter-detail-overview.png` -- Matter detail page showing heading, custom fields (Case Number, Court filled), breadcrumb with correct "Matter" terminology, "Back to Projects" terminology gap visible.
