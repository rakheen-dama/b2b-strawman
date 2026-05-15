# Day 3 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-14`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, portal :3002, keycloak :8180, mailpit :8025
**Actor**: Bob Ndlovu (Admin) -- `bob@thornton-test.local`
**Status**: **DAY 3 COMPLETE** -- 6 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED

## Summary

All Day 3 checkpoints passed. Engagement "Sipho Dlamini -- 2025/26 Tax Return" was created from the "Tax Return -- Individual (ITR12)" template with 7 pre-populated tasks. The engagement was linked to client Sipho Dlamini with reference TR-2026-0001 and type TAX_RETURN. Carol Mokoena was added as a member and assigned 4 initial data-collection tasks (Collect IRP5/IT3(a) certificates, Medical aid & retirement fund certificates, Rental income schedule, Prepare ITR12).

Additionally, deferred checkpoint 0.38 (engagement field promotion) is now VERIFIED -- the "New from Template -- Configure" dialog renders Reference Number and Work Type as native inline inputs.

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 3.1 | On Sipho detail, click New Engagement | **PASS** | Navigated to Sipho Dlamini detail page (/customers/31986024-382f-48ac-abb9-5dfa64fde531). Clicked "New Engagement" link in the Engagements tab. Redirected to /projects?new=1&customerId=31986024-382f-48ac-abb9-5dfa64fde531. "New from Template -- Select Template" dialog opened automatically with 7 accounting-za templates listed. |
| 3.2 | Select template: Tax Return -- Individual | **PASS** | Selected "Tax Return -- Individual (ITR12)" from the template list (7 tasks). Clicked Next. "New from Template -- Configure" dialog opened with pre-filled engagement name "Sipho Dlamini - ITR12 2026", description "Individual income tax return preparation and SARS eFiling submission.", and Client = Sipho Dlamini (pre-linked from customer context). |
| 3.3 | Fill: Name, engagement_type, reference_number | **PASS** | Updated engagement name to "Sipho Dlamini -- 2025/26 Tax Return". Filled Reference Number = "TR-2026-0001". Filled Work Type = "TAX_RETURN". Left Engagement lead as Unassigned. Screenshot: `qa_cycle/evidence/day-03/new-engagement-from-template-configure.png` |
| 3.4 | Save -> engagement created with pre-populated tasks | **PASS** | Clicked "Create Engagement". Redirected to engagement detail page at /projects/583ee45e-40b5-4846-9082-92f69f0f5f17. Header shows: "Sipho Dlamini -- 2025/26 Tax Return", Active status, Ref: TR-2026-0001, Type: TAX_RETURN, Client: Sipho Dlamini, 7 tasks. SA Accounting -- Engagement Details custom field group auto-assigned with fields: Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity. |
| 3.5 | Verify tasks present (IT3a, medical aid, rental, SARS eFiling, review, submit) | **PASS** | Tasks tab shows 7 tasks, all Medium priority, all Open status: (1) Review assessment & sign-off, (2) SARS eFiling submission, (3) Prepare ITR12, (4) Capital gains schedule, (5) Rental income schedule, (6) Medical aid & retirement fund certificates, (7) Collect IRP5/IT3(a) certificates. All expected task types present. Screenshot: `qa_cycle/evidence/day-03/engagement-tasks-list.png` |
| 3.6 | Assign initial tasks to Carol | **PASS** | Added Carol Mokoena as engagement member via Members tab > Add Member. Returned to Tasks tab and assigned Carol to 4 initial tasks via task detail dialog assignee combobox: (1) Collect IRP5/IT3(a) certificates, (2) Medical aid & retirement fund certificates, (3) Rental income schedule, (4) Prepare ITR12. Remaining 3 tasks (Review assessment & sign-off, SARS eFiling submission, Capital gains schedule) left Unassigned for senior review. Screenshot: `qa_cycle/evidence/day-03/tasks-assigned-to-carol.png` |

---

## Day 0 Deferred Item Now Verified

| Day 0 ID | Checkpoint | Day 3 Result | Evidence |
|-----------|-----------|--------------|----------|
| 0.38 | Field promotion checkpoint (engagement): inline inputs | **VERIFIED** | The "New from Template -- Configure" dialog renders Reference Number and Work Type as native inline inputs in the configure step. These correspond to the promoted engagement field slugs (`reference_number` and `work_type`/engagement type). The SA Accounting -- Engagement Details custom field group (Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity) is auto-assigned on the engagement detail page. |

---

## Engagement Detail

| Field | Value |
|-------|-------|
| Engagement ID | 583ee45e-40b5-4846-9082-92f69f0f5f17 |
| Name | Sipho Dlamini -- 2025/26 Tax Return |
| Template | Tax Return -- Individual (ITR12) |
| Client | Sipho Dlamini (31986024-382f-48ac-abb9-5dfa64fde531) |
| Reference | TR-2026-0001 |
| Type | TAX_RETURN |
| Status | Active |
| Tasks | 7 (0 complete, 7 open) |
| Members | 1 (Carol Mokoena) |

## Task Assignments

| Task | Assignee | Priority | Status |
|------|----------|----------|--------|
| Collect IRP5/IT3(a) certificates | Carol Mokoena | Medium | Open |
| Medical aid & retirement fund certificates | Carol Mokoena | Medium | Open |
| Rental income schedule | Carol Mokoena | Medium | Open |
| Prepare ITR12 | Carol Mokoena | Medium | Open |
| Capital gains schedule | Unassigned | Medium | Open |
| Review assessment & sign-off | Unassigned | Medium | Open |
| SARS eFiling submission | Unassigned | Medium | Open |

---

## Console Errors

| Category | Count | Severity | Details |
|----------|-------|----------|---------|
| 404 /api/assistant/invocations | 7 | LOW | AI assistant API not implemented. Falls back gracefully. Pre-existing. |
| WebSocket HMR | ~1 | INFO | Dev-only hot module replacement. Not a product issue. |

**No new product-level console errors introduced by Day 3 operations.** All errors are pre-existing dev-mode issues noted during Day 0/1/2.

---

## Observations

1. **Template-driven engagement creation**: The "New from Template" flow is a two-step wizard: (1) select template from a searchable combobox, (2) configure engagement details. The template pre-fills the engagement name (pattern: "{client} - {template code} {year}"), description, and auto-links the client when launched from a client detail page.

2. **Task instantiation**: All 7 tasks from the "Tax Return -- Individual (ITR12)" template were correctly instantiated with meaningful names, descriptions, and Medium priority. The tasks cover the full tax return workflow: document collection, schedule preparation, ITR12 drafting, review, and submission.

3. **Member-gated task assignment**: Task assignees can only be selected from engagement members, not all org members. This required first adding Carol as an engagement member via the Members tab before she could be assigned to tasks. This is correct behavior -- it prevents assigning tasks to people who are not part of the engagement.

4. **Custom field groups auto-assigned**: The "SA Accounting -- Engagement Details" custom field group was automatically assigned to the engagement based on the accounting-za vertical profile. Fields include Tax Year, SARS Submission Deadline, Assigned Reviewer, and Complexity -- all relevant for tax return engagements.

5. **Engagement lead vs task assignee**: The Engagement lead field was left as "Unassigned" during creation. This is separate from individual task assignments. The scenario does not specify an engagement lead for Day 3.

6. **Work Type field maps to engagement_type**: The "Configure" dialog has a "Work Type" text field (not a dropdown) where we entered "TAX_RETURN". On the engagement detail page, this renders as "Type TAX_RETURN" in the header. The scenario expected an `engagement_type` promoted slug -- the UI uses "Work Type" as the label, which is functionally equivalent.

---

## Evidence Files

- `qa_cycle/evidence/day-03/new-engagement-from-template-configure.png` -- New from Template configure dialog with all fields filled
- `qa_cycle/evidence/day-03/engagement-tasks-list.png` -- Full-page screenshot of engagement detail with 7 tasks listed
- `qa_cycle/evidence/day-03/tasks-assigned-to-carol.png` -- Full-page screenshot showing 4 tasks assigned to Carol Mokoena

---

**Day 3 Result: 6 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**
**No new gaps filed.**
