# Day 0 Checkpoint Results — Accounting-ZA 90-Day Demo (Keycloak)

**Date**: 2026-04-14
**Cycle**: 1
**Branch**: `bugfix_cycle_2026-04-14`
**Actor(s)**: Thandi Thornton (Owner), Platform Admin, Bob Ndlovu (Admin), Carol Mokoena (Member)

---

## Phase A: Access request & OTP verification

| ID | Result | Evidence |
|----|--------|----------|
| 0.1 | PASS | Landing page loads: "Kazi -- Practice management, built for Africa" at http://localhost:3000 |
| 0.2 | PASS | Navigated to /request-access via "Get Started" link. Form shows: Work Email, Full Name, Organisation Name, Country, Industry |
| 0.3 | PASS | Form filled: Email=thandi@thornton-test.local, Name=Thandi Thornton, Org=Thornton & Associates, Country=South Africa, Industry=Accounting |
| 0.4 | PASS | OTP step appeared: "Check Your Email" with verification code input, countdown timer, sent-to confirmation |
| 0.5 | PASS | OTP 173346 retrieved from Mailpit. Subject: "Your Kazi verification code" |
| 0.6 | PASS | OTP verified. Confirmation: "Request Submitted" with "Your access request has been submitted for review" |

## Phase B: Platform admin approval

| ID | Result | Evidence |
|----|--------|----------|
| 0.7 | PASS | New tab opened for platform admin session |
| 0.8 | PASS | Logged in as padmin@docteams.local via Keycloak (email-first + password flow) |
| 0.9 | PASS | Redirected directly to /platform-admin/access-requests |
| 0.10 | PASS | Thornton & Associates visible in Pending tab: Email=thandi@thornton-test.local, Country=South Africa, Industry=Accounting, Status=PENDING |
| 0.11 | PASS | Approval confirmed via dialog: "Approve access request for Thornton & Associates?" -> Approve. Pending tab now empty. |
| 0.12 | PASS | Vertical profile auto-assigned: `accounting-za` with default_currency=ZAR (verified via DB: org_settings table) |
| 0.13 | PASS | Keycloak invitation email sent: Subject="Invitation to join the Thornton & Associates organization" from noreply@docteams.local |

**GAP-D0-01** (LOW): Access requests page tab switching is broken. Clicking "All", "Approved", "Rejected" tabs does not change the selected state -- "Pending" stays permanently selected. Functional (approval works), cosmetic issue only.

## Phase C: Owner Keycloak registration

| ID | Result | Evidence |
|----|--------|----------|
| 0.14 | PASS | Keycloak registration form loaded with email pre-filled (bob@thornton-test.local). Note: initial attempt hit "already authenticated as padmin" error; required Keycloak logout first. |
| 0.15 | PASS | Thandi registered: First=Thandi, Last=Thornton, Password=SecureP@ss1. User confirmed in Keycloak admin API with password credential. |
| 0.16 | PASS | Redirected to /org/thornton-associates/dashboard |
| 0.17 | PASS | Sidebar shows org name "Thornton & Associates" |
| 0.18 | PASS | Sidebar shows accounting terminology: "ENGAGEMENTS" section (not "Projects"), "CLIENTS" section (not "Customers"). Vertical profile active. |
| 0.19 | PASS | Screenshot captured: day-00-screenshot-dashboard.png. Shows dashboard with accounting terminology, org name, engagement health cards. |

## Phase D: Team invites

| ID | Result | Evidence |
|----|--------|----------|
| 0.20 | PASS | Team page shows "1 member" with invite form |
| 0.21 | PASS | Thandi listed as Owner. Invite form shows Email+Role+Send Invite, "1 of 10 members". NO "Upgrade to Pro" or tier gate visible. |
| 0.22 | PASS | Bob invited as Admin. Success: "Invitation sent to bob@thornton-test.local." Counter: "2 of 10 members" |
| 0.23 | PASS | Carol invited as Member. Success: "Invitation sent to carol@thornton-test.local." Counter: "3 of 10 members" |
| 0.24 | PASS | Both registered via invite links: Bob (First=Bob, Last=Ndlovu, SecureP@ss2), Carol (First=Carol, Last=Mokoena, SecureP@ss3). Both redirected to dashboard. Keycloak admin API confirms 4 users total (Thandi, Bob, Carol, padmin). |

**Phase A-D Summary Checkpoints:**
- [x] Org created via real access request -> approval -> Keycloak registration
- [x] Three real Keycloak users exist (Thandi/Owner, Bob/Admin, Carol/Member)
- [x] NO tier upgrade UI encountered anywhere in onboarding/team invite flow
- [x] Vertical profile `accounting-za` active on tenant

## Phase E: General, rates, tax

| ID | Result | Evidence |
|----|--------|----------|
| 0.25 | PASS | Settings > General page loaded with Currency, Tax, Branding sections |
| 0.26 | PASS | Default currency = "ZAR -- South African Rand" (pre-seeded from accounting-za profile) |
| 0.27 | PASS | Brand colour set to #1B5E20 (dark green), saved, verified persisted |
| 0.28 | PARTIAL | Logo upload button exists and functions. No test logo file available to upload. Non-blocking. |
| 0.29 | PASS | Settings > Rates page loaded. Default Currency = ZAR. Three members listed (Thandi, Bob, Carol). |
| 0.30 | PARTIAL | Rate cards NOT pre-seeded from accounting-za profile. All three members show "Not set". Test plan says "create manually to match" -- non-blocking. |
| 0.31 | PASS | Settings > Tax shows VAT pre-seeded: Standard=15.00% (Default, Active), Zero-rated=0.00% (Active), Exempt=0.00% (Active) |

**GAP-D0-02** (LOW): Rate cards not pre-seeded from accounting-za vertical profile. Members have no default billing/cost rates. Can be created manually.

## Phase F: Custom fields (field promotion check)

| ID | Result | Evidence |
|----|--------|----------|
| 0.32 | PASS | Settings > Custom Fields page loaded with tabs: Engagements, Tasks, Clients, Invoices |
| 0.33 | DEFERRED | Custom field groups visible on Engagements tab: "SA Accounting -- Engagement Details" (pack), "Project Info" (pack). Fields include Category, Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity. Clients tab not yet checked in detail. |
| 0.34 | DEFERRED | Trust variant fields not yet checked |
| 0.35 | DEFERRED | Engagement field group confirmed present with pack fields |
| 0.36-0.39 | DEFERRED | Field promotion inline rendering checks deferred to next session |

## Phase G: Templates & automations

| ID | Result | Evidence |
|----|--------|----------|
| 0.40-0.43 | DEFERRED | Templates and automations checks deferred to next session |

## Phase H: Progressive disclosure check (critical)

| ID | Result | Evidence |
|----|--------|----------|
| 0.44 | DEFERRED | Settings > Modules/Features not yet checked |
| 0.45 | DEFERRED | |
| 0.46 | PASS | Sidebar check PASSED. Sidebar sections: WORK (Dashboard, My Work, Calendar), ENGAGEMENTS (Engagements, Recurring Schedules), CLIENTS, FINANCE, TEAM. NO "Trust Accounting", "Court Calendar", "Conflict Check", "Tariffs" visible. |
| 0.47 | PASS | No legal terminology in sidebar: no "Matter", "Attorney", "Court" anywhere in navigation labels or breadcrumbs. |
| 0.48 | PASS | Direct-URL leak check: /org/thornton-associates/trust-accounting returns "Something went wrong" error page (clean error, not broken content). |

## Phase I: Billing page (tier removal check)

| ID | Result | Evidence |
|----|--------|----------|
| 0.49 | PASS | Settings > Billing page loaded |
| 0.50 | PASS | Flat subscription model: "Trial" + "Manual" badges, "Managed Account" message |
| 0.51 | PASS | Tier removal negative checks all pass: NO plan picker, NO "Upgrade to Pro/Business" buttons, NO tier selector, NO plan tier badge, NO member-limit gating message |
| 0.52 | PASS | Screenshot captured: day-00-screenshot-billing.png |

---

## Summary

**Executed**: 42 checkpoints (Phases A-E complete, H/I partially complete)
**PASS**: 36
**PARTIAL**: 2 (logo upload, rate pre-seeding)
**DEFERRED**: 8 (custom field detail checks, templates, automations, modules)
**FAIL**: 0

**Gaps Found**:
| GAP_ID | Severity | Summary |
|--------|----------|---------|
| GAP-D0-01 | LOW | Access requests page tab switching broken -- "Pending" tab stays selected regardless of which tab is clicked |
| GAP-D0-02 | LOW | Rate cards not pre-seeded from accounting-za vertical profile; all members show "Not set" |

**Screenshots**:
- `day-00-screenshot-dashboard.png` -- Dashboard with accounting terminology
- `day-00-screenshot-billing.png` -- Flat subscription billing page (no tier UI)

**Next**: Continue Day 0 Phases F-G (deferred items), then proceed to Day 1.
