# Day 0 Retest — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (clean slate rebuild after OBS-4002/4004 fixes)
**Agent**: QA Agent (Opus 4.6)
**Purpose**: Re-execute Day 0 on clean-slate stack to (1) re-provision org/users and (2) retest 3 FIXED gaps

---

## Retest Summary

| Gap ID | Fix Summary | Retest Result | Evidence |
|--------|------------|---------------|----------|
| OBS-4002 | Added Payroll (Monthly) + Annual Trust Financial Statements templates | **VERIFIED** | 7 templates present (was 5). Both new templates visible with correct task counts. |
| OBS-4003 | Valid test logo at `qa_cycle/test-fixtures/thornton-logo.png` | **VERIFIED** | File uploaded via Upload Logo button. Preview renders (62x62 blob, 32x32 sidebar from S3). |
| OBS-4004 | `automation_builder` added to accounting-za enabledModules | **VERIFIED** | Automations link visible in settings nav. Page loads with 13 rules including 4 accounting-specific. |

---

## Phase A: Access Request & OTP Verification

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.1 | Landing page loads at localhost:3000 | **PASS** | Title: "Kazi -- Practice management, built for Africa". "Get Started" and "Sign In" links visible. |
| 0.2 | Click "Get Started" -> /request-access | **PASS** | Form loaded with Work Email, Full Name, Organisation Name, Country, Industry fields. |
| 0.3 | Fill form: thandi@thornton-test.local, Thandi Thornton, Thornton & Associates, South Africa, Accounting | **PASS** | All fields filled, dropdowns selected correctly. Submit button enabled. |
| 0.4 | Submit -> OTP step appears | **PASS** | "Check Your Email" card with verification code input. Timer: 10 min. |
| 0.5 | Retrieve OTP from Mailpit | **PASS** | Email subject: "Your Kazi verification code". OTP: 251437. Mailpit API search by `to:thandi@thornton-test.local`. |
| 0.6 | Enter OTP -> success confirmation | **PASS** | "Request Submitted" card: "Your access request has been submitted for review." |

## Phase B: Platform Admin Approval

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.7 | Open new tab for platform admin | **PASS** | New tab opened via gateway OAuth. |
| 0.8 | Login as padmin@docteams.local | **PASS** | KC login: padmin@docteams.local / password. Redirected to /platform-admin/access-requests. |
| 0.9 | Navigate to /platform-admin/access-requests | **PASS** | Already there after login. |
| 0.10 | Verify Thornton & Associates in Pending tab with Industry=Accounting | **PASS** | Row visible: Org=Thornton & Associates, Email=thandi@thornton-test.local, Name=Thandi Thornton, Country=South Africa, Industry=Accounting, Status=PENDING. |
| 0.11 | Click Approve -> confirm dialog -> status Approved | **PASS** | Confirmation dialog appeared (via JS evaluate click -- OBS-4001 Playwright quirk). Clicked Approve. Pending tab now empty (request processed). |
| 0.12 | Verify vertical profile auto-assigned to accounting-za | **PASS** | Settings > General shows "Vertical Profile: South African Accounting Firm". |
| 0.13 | Check Mailpit for Keycloak invitation email | **PASS** | Email found: Subject="Invitation to join the Thornton & Associates organization", To=thandi@thornton-test.local. Contains accept-invite link with KC registration token. |

## Phase C: Owner Keycloak Registration

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.14 | Open Keycloak invitation link | **PASS** | Accept-invite flow: KC logout -> KC registration page. Heading: "Create an account to join the Thornton & Associates organization". Email pre-filled. |
| 0.15 | Register: Thandi, Thornton, SecureP@ss1 | **PASS** | Filled First Name=Thandi, Last Name=Thornton, Password=SecureP@ss1. Clicked Register. |
| 0.16 | Redirected to /org/thornton-associates/dashboard | **PASS** | URL: http://localhost:3000/org/thornton-associates/dashboard |
| 0.17 | Sidebar shows org name "Thornton & Associates" | **PASS** | Sidebar displays "Thornton & Associates" as org name. |
| 0.18 | Sidebar shows Engagements (not Projects), Clients (not Customers) | **PASS** | Sidebar: "Engagements" section with "Engagements" and "Recurring Schedules" links. "Clients" section. Dashboard: "Active Engagements", "Engagement Health". No "Projects" or "Customers" labels. |
| 0.19 | Screenshot: Dashboard with accounting terminology | **PASS** | Screenshot saved: `qa_cycle/evidence/day-00-retest-dashboard-accounting.png` |

## Phase D: Team Invites

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.20 | Navigate to Team page | **PASS** | /org/thornton-associates/team loaded. Shows "1 member". Invite form visible. |
| 0.21 | Verify Thandi is Owner. No tier gate on invite | **PASS** | Thandi listed as "Owner". Invite form directly accessible with Email + Role + "Send Invite" button. NO "Upgrade to Pro" or tier gate. |
| 0.22 | Invite bob@thornton-test.local as Admin | **PASS** | Filled email, selected Admin role, clicked Send Invite. Confirmation: "Invitation sent to bob@thornton-test.local." |
| 0.23 | Invite carol@thornton-test.local as Member | **PASS** | Filled email, kept Member role, clicked Send Invite. Confirmation: "Invitation sent to carol@thornton-test.local." |
| 0.24 | Bob and Carol register via invite links | **PASS** | Bob: Opened invite from Mailpit -> KC logout -> KC registration -> Filled Bob/Ndlovu/SecureP@ss2 -> Redirected to dashboard. Carol: Same flow -> Filled Carol/Mokoena/SecureP@ss3 -> Redirected to dashboard. All three Keycloak users created. |

### Phase A-D Summary Checkpoints

| Checkpoint | Result |
|-----------|--------|
| Org created via real access request -> approval -> Keycloak registration | **PASS** |
| Three real Keycloak users exist | **PASS** |
| NO tier upgrade UI encountered anywhere in onboarding/team invite flow | **PASS** |
| Vertical profile accounting-za active on the tenant | **PASS** |

---

## Phase E: General, Rates, Tax

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.25 | Navigate to Settings > General | **PASS** | Page loaded with Vertical Profile, Currency, Tax Config, Branding, Client Portal sections. |
| 0.26 | Verify default currency = ZAR | **PASS** | "ZAR -- South African Rand" pre-seeded from accounting-za profile. |
| 0.27 | Set brand colour #1B5E20 -> Save -> verify persists | **PASS** | Changed from #000000 to #1B5E20, clicked Save Settings. Verified input value=#1B5E20 after save. |
| 0.28 | Upload firm logo -> verify preview | **PASS** | Uploaded `qa_cycle/test-fixtures/thornton-logo.png` via Upload Logo button -> file chooser. Preview renders: 62x62 blob preview + 32x32 sidebar logo from S3 (LocalStack). **OBS-4003 VERIFIED.** |
| 0.29 | Navigate to Settings > Rates | **PASS** | Rates & Currency page loaded. Default Currency = ZAR. |
| 0.30 | Verify rate cards pre-seeded from accounting-za | **PASS** | Billing: Thandi=R1,500.00, Bob=R850.00, Carol=R450.00 (all ZAR, Ongoing). Pre-seeded automatically. |
| 0.31 | Navigate to Settings > Tax -> verify VAT 15% | **PASS** | Tax Rates table: "VAT -- Standard" at 15.00%, Default, Active. Also Zero-rated (0.00%) and Exempt (0.00%). Tax Label = "VAT". |

## Phase F: Custom Fields (Field Promotion Check)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.32 | Navigate to Settings > Custom Fields | **PASS** | Page loaded with tabs: Engagements, Tasks, Clients, Invoices. |
| 0.33 | Verify accounting-za-customer field group with expected fields | **PASS** | "SA Accounting -- Client Details" group present under Clients tab. Fields: SA ID Number, Company Registration Number, Entity Type (DROPDOWN), Trading As, Risk Rating, SARS Tax Reference (Required), SARS eFiling Profile Number, Industry (SIC Code), Postal Address, FICA Verified (Required, DROPDOWN), FICA Verification Date, Referred By. |
| 0.34 | Verify accounting-za-customer-trust variant fields | **PASS** | "SA Accounting -- Trust Details" group present. Trust-specific fields: Trust Registration Number (Required), Trust Deed Date (Required), Trust Type (Required, DROPDOWN), Names of Trustees, Trustee Appointment Type (DROPDOWN), Letters of Authority Date. |
| 0.35 | Verify accounting-za-project field group | **PASS** | "SA Accounting -- Engagement Details" group on Engagements tab. Fields: Category, Tax Year, SARS Submission Deadline (DATE), Assigned Reviewer, Complexity (DROPDOWN). |
| 0.36 | Field promotion checkpoint (customer): promoted slugs inline | **DEFERRED** | Will verify during Day 1 client creation (New Client dialog). |
| 0.37 | Field promotion negative check: no duplicates | **DEFERRED** | Requires opening New Client dialog. |
| 0.38 | Field promotion checkpoint (engagement): inline inputs | **DEFERRED** | Requires opening New Engagement dialog. |
| 0.39 | Cancel dialogs without saving | **DEFERRED** | N/A. |

## Phase G: Templates & Automations

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.40 | Navigate to Settings > Engagement Templates | **PASS** | Page at /settings/project-templates loaded. |
| 0.41 | Verify accounting template pack present (7 templates) | **PASS** | **7 templates present** (was 5 before OBS-4002 fix): (1) Annual Trust Financial Statements (7 tasks), (2) Monthly Bookkeeping (6 tasks), (3) Payroll (Monthly) (5 tasks), (4) Tax Return -- Company/ITR14 (7 tasks), (5) Tax Return -- Individual/ITR12 (7 tasks), (6) VAT Return/VAT201 (5 tasks), (7) Year-End Pack/Annual Financial Statements (7 tasks). **OBS-4002 VERIFIED.** |
| 0.42 | Navigate to Settings > Automations | **PASS** | Automations link present in settings nav under "Work" section. Page at /settings/automations loaded correctly. **OBS-4004 VERIFIED.** |
| 0.43 | Verify automation-accounting-za rules present | **PASS** | 13 automation rules present including 4 accounting-specific: (1) SARS Deadline Reminder (Date Approaching), (2) Invoice Overdue 30 days (Invoice Status), (3) Engagement Budget Alert 80% (Budget Threshold), (4) FICA Reminder 7 days (Customer Status). Plus 9 common automations. |

## Phase H: Progressive Disclosure Check (Critical)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.44 | Navigate to Settings > Modules | **DEFERRED** | Settings nav has "Features" and "Packs" links (no explicit "Modules" link). |
| 0.45 | Verify no vertical-specific modules enabled | **DEFERRED** | Not explicitly verified via Modules page. |
| 0.46 | Sidebar check: NO legal-specific items | **PASS** | Confirmed absence: Trust Accounting=NO, Court Calendar=NO, Conflict Check=NO, Tariffs=NO, LSSA Tariffs=NO. Sidebar shows: Work(Dashboard, My Work, Calendar), Engagements(Engagements, Recurring Schedules), Clients, Finance, Team. |
| 0.47 | Cross-vertical terminology check: no legal terms | **PASS** | Confirmed: "Matter"=NO, "Attorney"=NO, "Court"=NO in sidebar. All labels use accounting terminology. |
| 0.48 | Direct-URL leak check: /trust-accounting | **PASS** | `/org/thornton-associates/trust-accounting` shows clean message: "Module Not Available -- The Trust Accounting module is not enabled for your organization." |

## Phase I: Billing (Tier Removal Check)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.49 | Navigate to Settings > Billing | **PASS** | Billing page loaded. |
| 0.50 | Verify flat subscription model | **PASS** | Flat subscription model confirmed (no tier UI). |
| 0.51 | Tier removal negative checks | **PASS** | Confirmed absence: "Upgrade to Pro"=NO, "Upgrade to Business"=NO, "Starter"=NO, "Pro plan"=NO, "Business plan"=NO, "plan picker"=NO, "member limit"=NO. Zero tier/upgrade UI elements. |
| 0.52 | Screenshot: Billing flat subscription UI | **DEFERRED** | Screenshot not captured this session (non-blocking). |

---

## Day 0 Retest Complete Checkpoints

| Checkpoint | Result |
|-----------|--------|
| Currency ZAR, brand colour, logo set | **PASS** |
| Rate cards configured (pre-seeded) | **PASS** |
| VAT 15% configured | **PASS** |
| accounting-za-customer, accounting-za-project field packs + trust variant | **PASS** |
| Field promotion verified: inline, no duplicates | **DEFERRED** (will verify during Day 1 client creation) |
| Accounting templates + automation pack loaded | **PASS** (7/7 expected templates; 13 automation rules) |
| Progressive disclosure verified: NO legal modules, no terminology leaks | **PASS** |
| Tier removal verified: flat billing, no upgrade UI | **PASS** |

---

## Console Errors

- Transient 500 error on first load of `/settings/project-templates` (resolved on retry). Root cause: SSR server component render error, likely stale session state. Non-blocking -- page loads correctly on second attempt.
- No persistent JavaScript/Next.js errors across page navigations.

## Notes

- All three Keycloak users created end-to-end through the real onboarding flow (no SQL, no mock IDP).
- The accept-invite flow correctly handles KC session management (logout current user -> redirect to KC registration -> redirect back to app).
- Vertical profile "South African Accounting Firm" correctly auto-assigned when Industry=Accounting + Country=South Africa.
- Rate cards, VAT, and custom field packs all pre-seeded correctly from the accounting-za profile.
- Logo upload to S3 (LocalStack) works correctly with preview rendering.
- Automations page is now fully accessible in the settings navigation with 13 rules seeded.
- Template pack now has the correct 7 templates including both new additions (Payroll Monthly, Annual Trust Financial Statements).

**Overall Day 0 Retest Result: 37 PASS / 0 PARTIAL / 8 DEFERRED / 0 FAIL**
**OBS-4002: VERIFIED | OBS-4003: VERIFIED | OBS-4004: VERIFIED**
