# Slice 627A — reproduction (before), task 627.1

- Date: 2026-10-28. Stack: E2E mock-auth stack (frontend :3001, backend :8081), product code built from main (c94a6e18f-equivalent).
- User: Alice (owner), org `e2e-test-org`, tenant schema `tenant_7d218705360b` (from `public.org_schema_mapping`).
- Viewport 1440x900 unless stated otherwise. DB access was read-only SELECTs. Every data change went through the UI as Alice.

## Key IDs

| What | ID |
|---|---|
| ACTIVE litigation matter "Ndlovu — transition repro mvrll4pa359c" (work_type LITIGATION) | `7a577920-a45b-4148-8595-72f6c77cb9d6` |
| CLOSED litigation matter "Ndlovu v RAF — Depth II mvrll4pa359c" | `a3d1f3e8-535c-48fd-8148-7a285ed04559` |
| COMPLETED matter created for this repro, "627A repro — completed matter" | `b06e6fb8-f0b0-4cbe-9f9c-ae21829727c6` |
| Client "Sipho Ndlovu mvrll4pa359c" | `421235da-e59f-47fc-be08-be9a3e777b7d` |

## G-B9: the matter header has no case number, court or opposing party. REPRODUCED

**Storage.** The values live in `projects.custom_fields` (jsonb). The definitions sit in field group `legal_za_matter` ("SA Legal — Matter Details", id `66b3af2f-…`):

| slug | name | field_type | options |
|---|---|---|---|
| `case_number` | Case Number | TEXT | none |
| `court_name` | Court | TEXT | none |
| `opposing_party` | Opposing Party | TEXT | none |
| `opposing_attorney` | Opposing Attorney | TEXT | none |
| `advocate_name` | Advocate | TEXT | none |
| `date_of_instruction` | Date of Instruction | DATE | none |
| `estimated_value` | Estimated Value | NUMBER | none |

All three target fields are plain TEXT. None is a select field, so stored values are not split into option values and labels.

**Starting data.** No matter in the tenant had any of the three values (`custom_fields = {}` on every LITIGATION matter). The only populated custom fields belong to the conveyancing matter (`erf_number`, `deeds_office`, `property_address`).

**Steps.**
1. Opened `/org/e2e-test-org/matters/7a577920-…`, went to the Details group, then "Custom fields" (`tab-item-fields`). The inputs are `#cf-case_number`, `#cf-court_name` and `#cf-opposing_party`.
2. Entered Case Number `2026/12345`, Court `Gauteng Division, Pretoria` and Opposing Party `Road Accident Fund`, then clicked "Save Custom Fields".
3. DB check: `custom_fields = {"court_name": "Gauteng Division, Pretoria", "case_number": "2026/12345", "opposing_party": "Road Accident Fund"}`.
4. Reloaded the matter and read the header DOM.

**Observed.**
- The header `innerText` reads `Ndlovu — transition repro mvrll4pa359c / Active / LITIGATION / Sipho Ndlovu mvrll4pa359c / Close Matter / Complete Matter`.
- `headerHasCase`, `headerHasCourt` and `headerHasOpp` are all `false`.
- Header testids: `matter-name`, `project-status-badge`, `header-lifecycle-actions`, `close-matter-btn`, `complete-project-btn`, `project-overflow-menu`.
- The Overview tab does not show the values either: `main.innerText` does not contain `2026/12345`.
- The values appear only as input values on `?tab=fields` (screenshot 03).

**Client header.** `/clients/421235da-…` has:
- `custom_fields.id_passport_number = "8501015800087"` (TEXT field in group `legal_za_client`);
- core column `customers.id_number = "8501015800087"`.

The header reads `Sipho Ndlovu mvrll4pa359c / Active / Onboarding / sipho.ndlovu…@example.com / Since 28 Oct 2026 · 2 engagements / Activate Customer / More actions`. The ID number is absent from the header and from the `?tab=details` panel. It appears only in the `#cf-id_passport_number` input on `?tab=fields`.

Screenshots:
- 01: overview header at 1440
- 02: overview header at 1024
- 03: custom fields populated, header empty
- 04: client header

## G-B14: when the URL has a tab, a click does not switch tabs. REPRODUCED

**Tab model.** Group triggers are `role=tab` buttons. Groups with more than one item open a menu of `role=menuitem` entries; each entry's tab value is the panel id suffix. Nothing on the matter page writes to the URL.

| Group (testid) | Items (`tab-item-<value>`) |
|---|---|
| Details (`tab-group-details`) | details "Details", fields "Custom fields", team "Team" |
| Overview (`tab-group-overview`) | overview (direct tab, no menu) |
| Work (`tab-group-work`) | tasks "Tasks", documents "Documents", correspondence "Correspondence", generated "Generated documents" |
| Fees (`tab-group-finance`) | time "Time recording", disbursements "Disbursements", budget "Fee estimate", rates "Tariffs", financials "Financials", statements "Statements of account", trust "Trust" |
| Client (`tab-group-client`) | clients "Clients", requests "Information requests", customer-comments "Client comments", adverse-parties "Adverse parties" |
| Diary (`tab-group-schedule`) | court-dates (direct tab, no menu) |
| Activity (`tab-group-activity`) | activity "Activity", audit "Audit trail" |

**Steps.**
1. Load `/matters/7a577920-…?tab=tasks`. The active panel is `tasks`.
2. Click Fees, then "Time recording" (`tab-item-time`) and wait 2 s. The URL is still `?tab=tasks`, the active panel is still `tasks`, and the selected group is still `tab-group-work`. **Clicking does nothing.** Clicking Overview (a direct tab) with `?tab=tasks` in the URL is also ignored. Screenshots 05 and 06.

**Reverse direction.**
- Load the matter with no `?tab`, then click Fees › Time recording. The panel switches to `time`, but **the URL is not updated** (no `?tab=`).
- Click Overview, then the "Task completion" KPI link (`href="?tab=tasks"`). The URL becomes `?tab=tasks` and the panel switches to `tasks`, so a client-side `?tab` navigation works.
- `router.push('?tab=time')` switches the panel to `time`.
- Conclusion: the URL drives the active tab whenever `?tab` is present, and clicks only update local state, which the URL value overrides.

**Client page comparison.** On `/clients/421235da-…`, clicking Details › Custom fields changed the URL to `?tab=fields` and the panel to `fields`. The client page already treats the URL as the source of truth.
- Client groups: Details (details/fields/tags), Overview, Matters (`tab-group-work`), Fees (`tab-group-finance`), FICA & compliance (`tab-group-compliance`), Activity.
- Client Overview links: `?tab=matters`, `?tab=onboarding`, `?tab=invoices`, `?tab=time`.

## G-A6: the New Matter dialog shows a "Coming Soon" conflict-check card. REPRODUCED

**Steps.** Matters list, then the "New Matter" button opens the dialog "Create Matter".

**Observed card text** (no testids on the card or its button): `Conflict Check` · badge `Coming Soon` · `Run a conflict of interest check before creating this matter.` · button `Run Conflict Check` (**disabled**, no title). The dialog has no opposing-party field. Its fields are Name, Description, Due Date, Client, Reference Number, Priority and Work Type. Screenshot 07.

**Surprise.** The sidebar link "New matter" goes to the `/matters/new` page, which is a different form (Client, Description, Template, Matter Name, Reference Number, Priority, Work Type, plus an AI Matter Intake card). That page has **no conflict card at all** (screenshot 08).

**Conflict Check page** (`/conflict-check`, sidebar Matters › Conflict check):
- Tabs: "Run Check" and "History (0)".
- Fields: Name to Check * (`checkedName`); ID Number (`checkedIdNumber`, placeholder `e.g. 8501015800087`); Registration Number (`checkedRegistrationNumber`); Check Type * (select `NEW_CLIENT`/`NEW_MATTER`/`PERIODIC_REVIEW`, default `NEW_CLIENT`); Client (optional) (select of customer UUIDs); Matter (optional) (select of project UUIDs).
- Button: "Run Conflict Check".

**Prefill from the query string:**
- `?name=Road Accident Fund&clientId=421235da-…` prefills **nothing** (screenshot 09).
- `?checkedName=Road Accident Fund&customerId=421235da-…&checkType=NEW_MATTER&projectId=7a57…` prefills Name = `Road Accident Fund` and Client = `421235da-…`. `checkType` and `projectId` are ignored: Check Type stays `NEW_CLIENT` and Matter stays blank (screenshot 10).
- So prefill **already exists**, but only for the keys `customerId`, `checkedName` and `checkedIdNumber`. The page reads these in `conflict-check/page.tsx:13-31` (read for the record only, no change).

## G-D2: a COMPLETED matter does not offer Close. REPRODUCED

**Steps.**
1. Created "627A repro — completed matter" through the New Matter dialog (client Sipho Ndlovu mvrll4pa359c, no tasks).
2. ACTIVE header actions: `Close Matter` (`close-matter-btn`), `Complete Matter` (`complete-project-btn`), and the overflow menu (`project-overflow-menu`) holding `Archive Matter`. Screenshot 11.
3. Clicked Complete Matter. The alertdialog reads "Complete Matter — Mark 627A repro — completed matter as completed? This will prevent new tasks and time entries from being added." with buttons Cancel / Complete (`confirm-complete-btn`).
4. DB check: `status = COMPLETED`, `completed_at = 2026-10-28 04:31:00Z`.

**COMPLETED header** after reload:
- Badge `Completed`.
- Primary action `Archive` (`archive-project-btn`).
- The overflow menu `project-overflow-menu` holds `Reopen Matter` (no testid).
- **There is no Close Matter action anywhere.** Screenshots 12 and 13.
- Note: the brief predicted "Reopen only". In fact Archive is the primary button and Reopen sits in the overflow menu.

**For comparison**, the CLOSED matter `a3d1f3e8-…` header shows only `Reopen Matter` (`reopen-matter-btn`).

## A17: `data-testid="sidebar-lifecycle-action"` is not rendered. CONFIRMED (none rendered)

- DOM count is 0 on the ACTIVE, COMPLETED and CLOSED matter pages and on the client detail page. No `[data-testid*=sidebar]` elements are present.
- Grep, for context only: the testid exists only in `frontend/components/projects/matter-sidebar.tsx:209`.
- `MatterSidebar` is imported only by `__tests__/components/projects/matter-sidebar.test.tsx`; no app route renders it. The other references are scenario prose: `qa/testplan/demos/legal-za-{full-lifecycle,90day}-keycloak.md` and `qa/testplan/legal-testplan-audit-2026-10-19.md`.

## Side effects on the stack (all made through the UI)

- Matter `7a577920-…`: custom fields `case_number`, `court_name` and `opposing_party` were set.
- New matter `b06e6fb8-…` was created and completed.
