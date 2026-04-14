# Fix Spec: GAP-D3-02 — Promoted custom fields not in template creation dialog

## Problem
When creating a new matter from a template, promoted custom fields (matter_type, case_number, court_name) are not shown in the template creation dialog. The user must fill them on the detail page after creation. Observed during Day 3 checkpoint 3.3.

## Root Cause (confirmed)
The "New from Template" creation dialog (`frontend/components/projects/create-project-dialog.tsx`) collects project name, description, and client, but does not include promoted custom fields in its form. Promoted fields only render on the project detail page (`frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`, line 516, `data-testid="project-promoted-fields"`).

Adding promoted fields to the creation dialog requires:
1. Fetching the org's custom field definitions for projects at dialog open time
2. Filtering for promoted field slugs (`PROMOTED_PROJECT_SLUGS`)
3. Rendering dynamic form inputs based on field types (text, select, date)
4. Submitting the values as part of the project creation API call
5. Backend support for receiving custom field values during project creation (currently set via separate PATCH calls)

## Fix
**WONT_FIX** — This is a feature enhancement, not a bug. The current UX (fill promoted fields on detail page after creation) is a deliberate design choice and works correctly. The improvement would require:
- Frontend: Dynamic form generation in the creation dialog, SWR fetch for field definitions
- Backend: Possibly extending the create project endpoint to accept custom field values
- Estimated 4-6 hours of work for proper implementation

The current flow is functional and acceptable for the demo — users see the fields on the detail page and fill them inline.

## Scope
N/A — WONT_FIX

## Verification
N/A

## Estimated Effort
L (> 2 hr) — Feature enhancement spanning frontend + backend
