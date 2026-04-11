# Fix Spec: GAP-S5-04 — "Link Adverse Party" dialog Customer select is blank

## Priority
MEDIUM — primarily a cascade from GAP-S5-03, but also a design smell: the dialog asks for a
Customer at all. The matter already has a customer; the adverse-party→matter link should not
need one.

## Problem
The "Link Adverse Party" dialog on the matter's Adverse Parties tab has a Customer select that
reads from `project.customer_id`. Since that column is NULL for template-created matters, the
select shows only "-- Select customer --" with no options. Submit button is disabled.

## Root Cause (confirmed via grep + QA evidence)
Files:
- Frontend: the dialog component under `frontend/components/adverse-parties/` or similar
  (inferred from QA evidence — dialog has three selects: Adverse Party, Customer, Relationship).
  Located under `frontend/app/(app)/org/[slug]/projects/[id]/` since the dialog opens from the
  matter's Adverse Parties tab.
- The Customer select likely reads from `project.customerId` via a prop or server action.

Grep-confirmed cascade: GAP-S5-03 root cause in `ProjectTemplateService.instantiateTemplate()` at
line 520 creates Project without setting `customerId`.

## Fix Steps
Choose ONE of two approaches, depending on whether GAP-S5-03 is fixed in the same turn:

**Option A (preferred, after GAP-S5-03 fix):** No code change required — the existing select
will populate from `project.customerId` once that column is backfilled.

**Option B (defensive, if we want matter-level linking to be robust):**
1. In the Link Adverse Party dialog, remove the Customer select entirely. The customer is
   implicit in the matter (derived from `customer_projects` join). Adverse-party→matter link
   does not need a customer discriminator.
2. In the server action that creates the `ProjectAdverseParty` link row, derive the customer
   from `customerProjectRepository.findByProjectId(projectId)` — take the first one (or omit
   the customer entirely from the link record if the schema allows).
3. Update the backend `AdversePartyService.linkToMatter` (or equivalent) to accept only
   `projectId` + `adversePartyId` + `relationship`, and look up the customer internally.

## Scope
- Option A: no code change, verification only
- Option B: Frontend + Backend (small)
- Migration needed: no

## Verification
After GAP-S5-03 fix:
1. Re-run Session 5 step 5.30 (Link adverse party to matter). Dialog opens, Customer select
   pre-populates with Lerato Mthembu. Fill Relationship = Opposing Party. Click Link Party.
2. Verify in DB: `SELECT * FROM project_adverse_parties;` should show the link row.
3. Matter detail "Adverse Parties" tab should now show Road Accident Fund listed.

## Estimated Effort
S (< 15 min) — verification only if GAP-S5-03 is fixed first
