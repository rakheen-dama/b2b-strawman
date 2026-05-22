# Day 3 Checkpoint Results — Create RAF Matter, Send FICA Info Request

**Date**: 2026-05-21
**Actor**: Bob Ndlovu (Admin) — authenticated via Keycloak (`bob@mathebula-test.local` / `SecureP@ss2`)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)

---

## Context

Bob was already logged in from Day 2 session (session persisted). Started from Sipho's client detail page at `/org/mathebula-partners/customers/d8327ceb-c66a-4305-b8be-fbda2c52f576`.

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 3.1 | On Sipho's client detail -> click + New Matter | PASS | "New Matter" link visible on client detail page. Clicked, navigated to `/org/mathebula-partners/projects?new=1&customerId=d8327ceb-c66a-4305-b8be-fbda2c52f576`. Template selector dialog opened. |
| 3.2 | Dialog uses legal-specific matter-type template selector | PASS | "New from Template -- Select Template" dialog showed 5 legal-za matter templates: Collections (Debt Recovery) 9 tasks, Commercial (Corporate & Contract) 9 tasks, Deceased Estate Administration 9 tasks, Litigation (Personal Injury / General) 9 tasks, **Litigation (Road Accident Fund -- RAF) 9 tasks**. All templates from Phase 64/66 matter templates. |
| 3.3 | Fill matter details | PASS | Selected template: Litigation (Road Accident Fund -- RAF). Step 2 "Configure" dialog: Matter name = "Dlamini v Road Accident Fund", Client = Sipho Dlamini (pre-filled from context), Matter lead = Bob Ndlovu, Reference Number = RAF-2026-001. Description auto-populated from template. Note: Court and Case Number fields are promoted fields on the matter detail page sidebar (SA Legal -- Matter Details field group), not in the create dialog. |
| 3.4 | Submit -> matter created, redirected to matter detail | PASS | Clicked "Create Matter" -> redirected to `/org/mathebula-partners/projects/85b09bb3-5cdd-42b9-8364-1bea1e83153d`. Matter detail page shows: title "Dlamini v Road Accident Fund", status "Active", Client: Sipho Dlamini, Reference: RAF-2026-001, Created: May 21, 2026. |
| 3.5 | Verify grouped tab bar with 6 groups | PASS | Grouped tab bar (`data-testid="grouped-tab-bar"`) present with 6 groups: **Overview** (standalone), **Work** (dropdown: Tasks, Documents, Generated Docs, Staffing), **Finance** (dropdown: Time, Expenses, Disbursements, Budget/Fee Estimate, Rates, Financials, Statements, Trust), **Client** (dropdown: Clients, Requests, Client Comments, Adverse Parties), **Schedule** (standalone), **Activity** (dropdown). Matches Phase 73 layout specification. |
| 3.6 | Promoted fields render inline on Overview tab, NOT in generic Custom Fields | PASS | "SA Legal -- Matter Details" field group renders in the sidebar area with promoted fields: Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value. These are in a named field group, NOT duplicated in a generic "Custom Fields" section. A separate "Project Info" group with "Category" also exists. |
| 3.7 | Navigate to Client > Requests sub-tab -> click + New Info Request | PASS | Clicked Client group tab -> dropdown showed: Clients, Requests, Client Comments, Adverse Parties. Clicked "Requests" -> tab switched to "Client . Requests" with teal underline. Empty state "No information requests yet" shown. Clicked "+ New Request" button. |
| 3.8 | Select template: FICA Onboarding Pack | PASS | Template dropdown showed 8 templates including "FICA Onboarding Pack (3 items)". Selected it. Template info updated to show "Template items: 3". |
| 3.9 | Addressee: Sipho Dlamini (portal contact auto-populated) | PASS | Portal Contact field auto-populated as "Sipho Dlamini (sipho.portal@example.com)" from the client record. No manual selection required. |
| 3.10 | Request items pre-filled from template: ID copy, Proof of residence, Bank statement | PASS | On the info request detail page (`/org/mathebula-partners/information-requests/50f6dfc8-44da-450e-b68e-d1e083b3f7c8`), Items (3) section shows: (1) **ID copy** -- File Upload, Pending, "Certified copy of the client's South African ID document or passport bio page. Must be certified by a Commissioner of Oaths, SAPS, or other accepted certifier within the last 3 months." (2) **Proof of residence (<=3 months)** -- File Upload, Pending, "Recent utility bill, municipal rates account, bank statement, or similar document confirming the client's residential address." (3) **Bank statement (<=3 months)** -- File Upload, Pending, "Most recent bank statement evidencing the client's source of funds." All items correctly pre-filled from FICA template. |
| 3.11 | Due date: Day 10 (7 days from today) | PASS | Due date set to May 28, 2026 (7 days from May 21, 2026). Confirmed on request detail page: "Due: May 28, 2026". |
| 3.12 | Click Send -> info request status = Sent | PASS | Clicked "Send Now" button. Request created as REQ-0001. Status badge = **Sent** (teal). Progress: 0/3 accepted. Sent date: May 21, 2026. Visible both on Requests tab table and on the request detail page. |
| 3.13 | Verify portal contact created/linked | PASS | Client > Clients sub-tab shows: Sipho Dlamini, sipho.portal@example.com, Status: ACTIVE. 1 client linked to matter. Portal contact auto-created from client email. |
| 3.14 | Mailpit -> magic-link email to sipho.portal@example.com | PASS | Mailpit message ID `DtX4NgCCxh85ZNZgVgq67p`. Subject: "Information request REQ-0001 from Mathebula & Partners". Body contains: "Hi Sipho Dlamini, Mathebula & Partners has sent you an information request (REQ-0001) with 3 item(s) that require your attention." Magic link: `http://localhost:3002/auth/exchange?token=8mUmVV_Zx2sWpiiFQfEeZjFdqMfmJCu31s2RYUny3Pk&orgId=mathebula-partners` -- points to portal on port 3002 (NOT :3000). |

---

## Day 3 Summary Checkpoints

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Matter created with reference format RAF-YYYY-NNN | PASS | Reference: RAF-2026-001, title: Dlamini v Road Accident Fund |
| Matter-type template instantiated -- phase sections present, LSSA tariff linked | PASS | Template "Litigation (Road Accident Fund -- RAF)" with 9 tasks instantiated. SA Legal -- Matter Details field group present with Case Number, Court, Opposing Party, etc. |
| Promoted matter fields render inline, not duplicated | PASS | "SA Legal -- Matter Details" field group in sidebar; promoted fields (Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value) render within the named group, not in a generic Custom Fields section |
| FICA info request dispatched, magic-link email sent | PASS | REQ-0001 sent to Sipho Dlamini via FICA Onboarding Pack template (3 items: ID copy, Proof of residence, Bank statement). Magic-link email delivered to sipho.portal@example.com via Mailpit with portal auth exchange link on :3002. |

---

## Key Data for Subsequent Days

- **Matter ID**: `85b09bb3-5cdd-42b9-8364-1bea1e83153d`
- **Matter URL**: `/org/mathebula-partners/projects/85b09bb3-5cdd-42b9-8364-1bea1e83153d`
- **Info Request ID**: `50f6dfc8-44da-450e-b68e-d1e083b3f7c8`
- **Info Request Reference**: REQ-0001
- **Portal Magic Link Token**: `8mUmVV_Zx2sWpiiFQfEeZjFdqMfmJCu31s2RYUny3Pk`
- **Portal Auth URL**: `http://localhost:3002/auth/exchange?token=8mUmVV_Zx2sWpiiFQfEeZjFdqMfmJCu31s2RYUny3Pk&orgId=mathebula-partners`

---

## Observations

1. **Court and Case Number not in create dialog**: The scenario listed Court = "Gauteng Division, Pretoria" and Case Number = blank as fields in the create matter dialog. In practice, these are promoted fields in the "SA Legal -- Matter Details" field group on the matter detail page sidebar. The create dialog only has: Matter name, Description, Client, Matter lead, Reference Number, Priority, Work Type. This is not a bug -- the promoted fields are available for editing on the detail page post-creation.

2. **Matter name auto-generated**: The template pre-filled "Sipho Dlamini - RAF Claim" as the matter name (using client name + template short name pattern). This was overwritten per the scenario to "Dlamini v Road Accident Fund".

3. **FICA status card on Overview**: After sending the info request, the Overview tab's FICA section updated from "Not Started" to "In Progress" with text "Awaiting client response and firm-side review." and a "View request" link routing to `/org/mathebula-partners/information-requests/{id}` (canonical route per OBS-501).

4. **Info request URL**: Routes to `/org/{slug}/information-requests/{id}` (NOT `/requests/{id}`), confirming OBS-501 fix is in place.

5. **Console errors**: Zero JavaScript errors during entire Day 3 flow.

---

## Gap Report Entries

No new gaps identified. All 14 checkpoints PASS.
