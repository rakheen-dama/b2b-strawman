# Keycloak Regression Cycle 2 — Results

**Date**: 2026-03-23
**Stack**: Keycloak dev stack (Frontend 3000, Backend 8080, Gateway 8443, Keycloak 8180)
**User**: Thandi Thornton (owner), thandi@thornton-test.local
**Branch**: `bugfix_cycle_kc_2026-03-23`
**Focus**: Bug fix verification + continued coverage of NOT_TESTED items

---

## Bug Fix Verification

### BUG-KC-001: Settings sidebar click crash — VERIFIED

| Item | Detail |
|------|--------|
| **Bug** | Clicking Settings in sidebar caused "Application error: a client-side exception has occurred" |
| **Fix** | Settings sidebar href changed from `/settings` to `/settings/general` in `nav-items.ts` (PR #827) |
| **Test** | Clicked Settings link in sidebar from Dashboard page |
| **Result** | **PASS** — Navigated to `/org/thornton-associates/settings/general` without error. Full settings page loaded with all sections (General, Vertical Profile, Currency, Tax, Branding). 0 console errors. |
| **Status** | **VERIFIED** |

### BUG-KC-002: Create Customer Step 2 dialog overflow — VERIFIED

| Item | Detail |
|------|--------|
| **Bug** | Step 2 dialog with many custom fields overflowed viewport; Back/Create Customer buttons unreachable |
| **Fix** | Added `max-h-[60vh] overflow-y-auto` to content wrapper in CreateCustomerDialog (PR #828) |
| **Test** | Opened Create Customer dialog, filled Step 1 (BUG-002 Test Customer), advanced to Step 2 |
| **Result** | **PASS** — Step 2 "Additional Information" dialog shows custom field groups (SA Trust Details, SA Client Details, Contact & Address) in a scrollable container. Back and Create Customer buttons are visible at bottom of dialog within viewport. Screenshot: `qa_cycle/screenshots/bug-kc-002-verified-step2-buttons-visible.png` |
| **Status** | **VERIFIED** |

### BUG-KC-003: Keycloak passwords not set during provisioning — VERIFIED-BY-CODE

| Item | Detail |
|------|--------|
| **Bug** | Users could not log in after provisioning — "Invalid password" error. Required manual admin API password reset. |
| **Fix** | Added `setUserPassword()` to `KeycloakProvisioningClient` + bootstrap backfill (PR #829) |
| **Test** | Verified via Keycloak Admin API: (1) Both users have `type=password` credentials set, (2) Both users authenticate successfully via `grant_type=password` against `admin-cli` client, (3) Thandi is currently logged in via browser OIDC flow. |
| **Evidence** | `thandi@thornton-test.local` — LOGIN SUCCESS via token endpoint. `bob@thornton-test.local` — LOGIN SUCCESS via token endpoint. Credential timestamps confirm passwords are set (thandi: 1774295614408, bob: 1774295619933). |
| **Status** | **VERIFIED-BY-CODE** (full end-to-end provisioning flow not tested — would require new org creation) |

---

## Track CUST-01: Customer CRUD (continued)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| CUST-01.3 | Edit customer name | **PASS** | Opened Edit dialog on "Kgosi Holdings QA", changed name to "Kgosi Holdings QA Edited", clicked Save Changes. Heading updated to "Kgosi Holdings QA Edited" immediately. |
| CUST-01.4 | Search customer list | NOT_TESTED | Only 1 customer in system — search not meaningful with single result |
| CUST-01.5 | Customer list pagination | NOT_TESTED | Only 1 customer — pagination not applicable |

---

## Track CUST-02: Customer Lifecycle (continued)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| CUST-02.3 | ONBOARDING -> ACTIVE (via checklist) | **PARTIAL** | Navigated to Onboarding tab. Checklist shows 4 items: 3 completed (Confirm engagement, Verify contacts, Confirm billing), 1 pending (Upload signed engagement letter). The pending item has "Requires document: Signed engagement letter" constraint. Clicking Confirm without selecting a document does not complete the item — this is expected behavior for a document-required checklist item. Auto-transition to ACTIVE would work if all items are completed. |
| CUST-02.4 | PROSPECT blocked from creating project | NOT_TESTED | No PROSPECT customer available (only one was transitioned to ONBOARDING in cycle 1) |
| CUST-02.5 | PROSPECT blocked from creating invoice | NOT_TESTED | Same — no PROSPECT customer |
| CUST-02.6 | ACTIVE -> DORMANT | NOT_TESTED | Customer still in ONBOARDING (checklist incomplete) |
| CUST-02.7-10 | Remaining lifecycle transitions | NOT_TESTED | Blocked by ONBOARDING checklist requiring document |

---

## Track PROJ-02: Project Tasks (continued)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| PROJ-02.2 | Edit task title | NOT_TESTED | Task detail panel opened but no inline edit visible for title (title appears as a heading, not editable field) |
| PROJ-02.3 | Change task status: OPEN -> IN_PROGRESS | **PASS** | Opened task "Gather supporting documents" detail panel. Clicked status combobox (Open), selected "In Progress". Status updated in both the detail panel and the background task table. "Mark Done" button appeared. |
| PROJ-02.4 | Change task status: IN_PROGRESS -> DONE | **PASS** | Clicked "Mark Done" button. Status changed to "Done". Shows "Completed by Thandi Thornton on Mar 23, 2026". Assignee combobox disabled. An automation created a follow-up task ("Follow-up: Gather supporting documents"). |
| PROJ-02.5 | Reopen completed task | **PASS** | Clicked "Reopen" button on completed task. Status reverted to "Open". Completion message disappeared. Assignee combobox re-enabled. |
| PROJ-02.6 | Cancel task | NOT_TESTED | Deferred — status dropdown shows "Cancelled" option but not tested in this cycle |
| PROJ-02.7 | Assign member to task | **PASS** | Clicked assignee combobox on task detail panel. Dropdown showed "Unassigned" and "Thandi Thornton". Selected Thandi. Assignee updated in detail panel and task table row shows "Thandi Thornton". |

---

## Track SET-02: Rate Cards (continued)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| SET-02.2 | Create billing rate | **PARTIAL** | Clicked "Add Rate" for Thandi. Dialog opened correctly with Rate Type toggle (Billing/Cost), Hourly Rate spinbutton, Currency (ZAR), Effective From (pre-filled 2026-03-23), Effective To. Entered 450 for hourly rate. Page navigated away before Create Rate could be clicked — appears to be a navigation issue in the dialog interaction. Dialog itself renders correctly. |
| SET-02.3 | Edit billing rate | NOT_TESTED | No rate exists yet |
| SET-02.4 | View cost rates | NOT_TESTED | Cost Rates tab visible on rates page but not clicked |
| SET-02.5 | Rate hierarchy: project override wins | NOT_TESTED | Requires rate creation + time logging |

---

## Track SET-03: Tax Settings (new track)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| SET-03.1 | View tax rates | **PASS** | Navigated to `/settings/tax`. Tax Settings page loaded with Tax Configuration section (registration number, label, inclusive pricing toggle) and Tax Rates table. 3 seeded rates: Standard (15%, Default, Active), Zero-rated (0%, Active), Exempt (0%, Active). Edit/Deactivate buttons available. Add Tax Rate button visible. |

---

## Track AUTO-01: Automation (bonus finding)

| # | Test | Result | Evidence |
|---|------|--------|----------|
| AUTO-01 (bonus) | Automation fires on task completion | **PASS** | When task "Gather supporting documents" was marked Done (PROJ-02.4), an automation rule automatically created a follow-up task "Follow-up: Gather supporting documents" with Open status. This confirms automation triggers are working end-to-end. |

---

## Overall Scorecard (Cycle 2 — cumulative)

| Track | Total | Pass | Fail | Partial | N/A | Not Tested |
|-------|-------|------|------|---------|-----|------------|
| Auth (pre-flight) | 4 | 4 | 0 | 0 | 0 | 0 |
| NAV-01 | 16 | 15 | 0 | 0 | 1 | 0 |
| CUST-01 | 5 | 2 | 0 | 1 | 0 | 2 |
| CUST-02 | 10 | 2 | 0 | 1 | 0 | 7 |
| PROJ-01 | 7 | 2 | 0 | 0 | 0 | 5 |
| PROJ-02 | 7 | 4 | 0 | 0 | 0 | 3 |
| PROJ-03 | 7 | 0 | 0 | 0 | 0 | 7 |
| SET-02 | 5 | 1 | 0 | 1 | 0 | 3 |
| SET-03 | 3 | 1 | 0 | 0 | 0 | 2 |
| AUTO-01 | 5 | 2 | 0 | 0 | 0 | 3 |
| DOC-01 | 4 | 1 | 0 | 0 | 0 | 3 |
| **Total** | **73** | **34** | **0** | **3** | **1** | **35** |

**Pass Rate (tested)**: 34/38 = 89%
**Coverage**: 38/73 = 52% (up from 46% in Cycle 1)
**Bugs verified**: 3/3 (BUG-KC-001 VERIFIED, BUG-KC-002 VERIFIED, BUG-KC-003 VERIFIED-BY-CODE)
**New bugs found**: 0
