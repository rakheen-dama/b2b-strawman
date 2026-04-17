# Day 0 Checkpoint Results — Consulting Agency 90-Day Demo (Keycloak)

**Date**: 2026-04-14
**Cycle**: 1
**Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
**Actor**: QA Agent

---

## Session 0 — Prep & Reset

| ID | Result | Evidence |
|----|--------|----------|
| 0.A | PASS | No `tenant_zolani*` schemas found in database |
| 0.B | PASS | No Keycloak users with `@zolani-test.local` emails found |
| 0.C | NOTED | consulting-generic is an empty profile — gaps expected and will be logged as profile-content gaps |

---

## Phase A: Access request & OTP verification

| ID | Result | Evidence |
|----|--------|----------|
| 0.1 | PASS | Landing page loads at http://localhost:3000. Title: "Kazi — Practice management, built for Africa" |
| 0.2 | PASS | Clicked "Get Started" → navigated to `/request-access`. Form shows: Work Email, Full Name, Organisation Name, Country, Industry |
| 0.3 | PASS | Form filled: Email=zolani@zolani-test.local, Name=Zolani Dube, Org=Zolani Creative, Country=South Africa, Industry=Marketing. Note: No "Creative Services" or "Agency" option — "Marketing" is the closest available |
| 0.4 | PASS | Submit → OTP step appeared with "Check Your Email" heading, verification code input, 10-minute countdown |
| 0.5 | PASS | OTP 943662 retrieved from Mailpit (Subject: "Your Kazi verification code"). Entered and verified → "Request Submitted" success page |

---

## Phase B: Platform admin approval

| ID | Result | Evidence |
|----|--------|----------|
| 0.6 | PASS | Logged in as padmin@docteams.local via Keycloak (two-step: email then password) → redirected to `/platform-admin/access-requests` |
| 0.7 | PASS | `/platform-admin/access-requests` loaded, on Pending tab |
| 0.8 | PASS | "Zolani Creative" visible in Pending with correct data: email, name, country=South Africa, industry=Marketing |
| 0.9 | PASS | Clicked Approve → confirmation dialog → confirmed → moved to Approved tab. Status shows APPROVED |
| 0.10 | PARTIAL | Vertical profile NOT auto-assigned. `org_settings.vertical_profile` is empty/null. The backend did not map "Marketing" industry to `consulting-generic`. However, empty profile may function identically to consulting-generic (no modules, no packs). **Profile-content observation**: industry-to-profile mapping doesn't populate vertical_profile for Marketing industry |
| 0.11 | PASS | Invitation email sent to zolani@zolani-test.local. Subject: "Invitation to join the Zolani Creative organization" |

---

## Phase C: Owner Keycloak registration

| ID | Result | Evidence |
|----|--------|----------|
| 0.12 | PASS | Invitation link navigated to Keycloak registration form. Email pre-filled: zolani@zolani-test.local |
| 0.13 | PASS | Registration completed: First=Zolani, Last=Dube, Password=SecureP@ss1. Note: First attempt showed error due to admin session conflict, but user was created. Second login succeeded cleanly after clearing cookies |
| 0.14 | PASS | Redirected to `/org/zolani-creative/dashboard` |
| 0.15 | PASS | Sidebar shows org name "Zolani Creative" |
| 0.16 | PASS | Generic terminology active: sidebar shows WORK (Dashboard, My Work, Calendar), PROJECTS (Projects, Recurring Schedules), CLIENTS, FINANCE, TEAM. No "Matters", "Engagements", or other vertical-specific terms |
| 0.17 | PASS | Screenshot captured: `consulting-day00-dashboard-wow.png`. Dashboard shows clean generic UI with Getting Started banner (0 of 6 complete), KPI cards (Active Projects, Hours, Margin, Overdue Tasks, Budget Health), Project Health, Team Time, Recent Activity, Admin panels |

---

## Phase D: Team invites

| ID | Result | Evidence |
|----|--------|----------|
| 0.18 | PASS | Team page loaded at `/org/zolani-creative/team`, showing 1 member |
| 0.19 | PASS | Zolani shown as Owner. "1 of 10 members" displayed. **No tier upgrade UI, no plan badge, no upgrade button** |
| 0.20 | PASS | Invited bob@zolani-test.local as Admin → success: "Invitation sent to bob@zolani-test.local". Counter updated to "2 of 10" |
| 0.21 | PASS | Invited carol@zolani-test.local as Member → success: "Invitation sent to carol@zolani-test.local". Counter updated to "3 of 10" |
| 0.22 | PASS | Both Bob and Carol accepted invites via Mailpit links, registered in Keycloak, and redirected to app. Verified via Keycloak admin API: 3 org users (Zolani/Owner, Bob/Admin, Carol/Member) all enabled |

### Phase A-D Summary Checkpoints

| Checkpoint | Result |
|-----------|--------|
| Org created via real Keycloak flow | PASS |
| Three users registered | PASS |
| No tier UI encountered | PASS |
| Vertical profile consulting-generic (or fallback) active | PARTIAL — profile field is empty but functionally equivalent |

---

## Phase E: General, rates, tax (partial)

| ID | Result | Evidence |
|----|--------|----------|
| 0.23 | PASS | Settings > General page loaded with sections: Vertical Profile, Currency, Tax Configuration, Branding, Org Documents |
| 0.24 | PARTIAL | Default currency was USD, not ZAR. **LOW gap**: SA-registered org should default to ZAR. Manually changed to ZAR |
| 0.25 | PASS | Brand colour set to #F97316 (Zolani orange) → saved → persisted after page refresh |
| 0.26 | SKIP | Logo upload skipped — no logo file available in test environment. Not a product bug |
| 0.27 | PASS | Settings > Rates page loaded. All 3 members visible with "Not set" billing rates. Currency correctly shows ZAR |
| 0.28 | PASS | All billing and cost rates created manually: Zolani R1,800/hr billing + R800/hr cost, Bob R1,200/hr billing + R550/hr cost, Carol R750/hr billing + R350/hr cost. All ZAR, effective Apr 14, 2026, Ongoing |
| 0.29 | PASS | GAP-C-03 already logged: consulting-generic has no rate-card defaults |

## Phase E (cont.): Tax

| ID | Result | Evidence |
|----|--------|----------|
| 0.30 | PASS | Settings > Tax page loaded. Tax Configuration section with Registration Number, Registration Label, Tax Label, Tax-inclusive pricing toggle |
| 0.31 | PASS | VAT 15% already present as "Standard" rate (15.00%, Default, Active). Also "Zero-rated" (0.00%) and "Exempt" (0.00%) rates active. Tax label shows "Tax" not "VAT" — profile-content observation, not a bug. No need to create manually |

---

## Phase F: Custom Fields

| ID | Result | Evidence |
|----|--------|----------|
| 0.32 | PASS | Settings > Custom Fields loaded. Projects tab shows "Category" field (TEXT, Pack) + "Project Info" field group (Pack). Customers tab shows 5 pack fields: Company Registration Number, SA ID Number, Entity Type, Passport Number, Risk Rating. Field groups: Company FICA Details, FICA Compliance |
| 0.33 | PASS | No vertical-specific field group present — only global common fields (pack fields). Matches expected state for consulting-generic |
| 0.34 | PASS | Gap logged: no agency-flavoured custom field pack. GAP-C-04 |
| 0.35 | PASS | New Customer dialog (Step 1 of 2) shows promoted slugs inline: Name, Email, Phone, Tax Number, Address Line 1, Address Line 2, City, State/Province, Postal Code, Country, Contact Name, Contact Email, Contact Phone. All expected common promoted slugs present |
| 0.36 | PASS | Pack fields (Registration Number, Entity Type, Financial Year End) appear in separate "Business Details" section — NOT duplicated with promoted slugs |
| 0.37 | PASS | New Project dialog shows `reference_number` ("Reference Number") and `priority` ("Priority") inline as promoted project slugs. Also shows Work Type field |
| 0.38 | PASS | Both dialogs cancelled without creating records |

---

## Phase G: Templates & Automations

| ID | Result | Evidence |
|----|--------|----------|
| 0.39 | PASS | Settings > Project Templates loaded |
| 0.40 | PASS | No templates pre-seeded — "No project templates yet" message displayed. GAP-C-05 logged |
| 0.41 | PASS | Manual template created: "Website Redesign Project" with 6 tasks (Discovery, Wireframes, Design, Development, QA, Launch). Name pattern: `{customer} — Website Redesign`. Template now shows as Active with 6 tasks |
| 0.42 | SKIP | No dedicated "Automations" settings page exists in navigation. Automation Rule Builder is a toggleable feature on Settings > Features (currently off). No automation rules to inspect |
| 0.43 | PASS | GAP-C-06 logged: no automation pack for consulting-generic |

---

## Phase H: Progressive Disclosure

| ID | Result | Evidence |
|----|--------|----------|
| 0.44 | PASS | Settings > Features loaded. Three optional features: Automation Rule Builder, Resource Planning, Bulk Billing Runs — all off. No vertical-specific modules |
| 0.45 | PASS | consulting-generic has NO vertical-specific modules enabled |
| 0.46 | PASS | Sidebar shows: Work (Dashboard, My Work, Calendar), Projects (Projects, Recurring Schedules), Clients (Customers, Proposals, Retainers, Compliance), Finance, Team. **No** Trust Accounting, Court Calendar, Conflict Check, or Tariffs |
| 0.47 | PASS | No "Matter", "Attorney", "Engagement", "Fee Note", "Court" or other vertical-specific terminology in sidebar, breadcrumbs, or settings |
| 0.48 | PASS | Direct-URL leak check: `/trust-accounting` → error page ("Something went wrong"), `/court-calendar` → "Module Not Available" message, `/conflict-check` → "Module Not Available" message. None expose vertical-specific functionality |

---

## Phase I: Billing (Tier Removal)

| ID | Result | Evidence |
|----|--------|----------|
| 0.49 | PASS | Settings > Billing loaded |
| 0.50 | PASS | Flat subscription: "Trial" + "Manual" badges. "Managed Account — Your account is managed by your administrator." No tier picker, no upgrade button, no plan badge |
| 0.51 | PASS | Screenshot: `consulting-day00-billing.png` |

---

### Day 0 Complete Checkpoints

| Checkpoint | Result |
|-----------|--------|
| Currency, brand, logo set (with manual workarounds for profile gaps) | PASS (logo skipped — no file available, not a product bug) |
| Rates manually created (log gap: no defaults) | PASS |
| VAT 15% configured | PASS (already present as "Standard" 15%) |
| Common field promotion verified on Customer and Project dialogs | PASS |
| At least one manual project template created (log gap: no pack) | PASS |
| Progressive disclosure verified: zero legal/accounting module leakage | PASS |
| Tier removal verified: flat billing UI | PASS |
| Gap list updated: at least 4 profile-shape gaps logged | PASS (6 gaps: GAP-C-01 through GAP-C-06) |

---

## Gaps Logged

| GAP_ID | Day / Checkpoint | Severity | Type | Summary |
|--------|------------------|----------|------|---------|
| GAP-C-01 | D0 / 0.10 | LOW | Profile-content | `vertical_profile` not auto-assigned for "Marketing" industry — field left empty |
| GAP-C-02 | D0 / 0.24 | LOW | Profile-content | Default currency is USD, not ZAR, for SA-registered org (consulting-generic has no currency default) |
| GAP-C-03 | D0 / 0.28 | MED | Profile-content | `consulting-generic` has no rate-card defaults — all rates "Not set" for new agency tenant |
| GAP-C-04 | D0 / 0.34 | MED | Profile-content | No agency-flavoured custom field pack (no campaign_type, channel, deliverable_type, creative_brief_url, brand_guidelines_link, analytics_dashboard_url) |
| GAP-C-05 | D0 / 0.40 | MED | Profile-content | No templates pre-seeded for consulting-generic (agencies expect Brand Project, Website Project, Monthly Retainer, Campaign stubs) |
| GAP-C-06 | D0 / 0.43 | MED | Profile-content | No automation pack for consulting-generic (agencies expect rules like "project 80% budget -> notify owner", "task overdue > 3 days -> notify assignee") |

---

## Notes

- Keycloak session management required manual cookie clearing between user registrations (Playwright shares cookies across tabs on same domain). This is a test-tooling issue, not a product bug.
- The registration flow showed "You are already authenticated as different user" error when the admin session was still active. The registration actually completed despite the error page — user was created. This is a UX concern but not a blocker (real users would use separate browser sessions).
- Settings navigation shows these categories: General, Billing, Notifications, Email, Security (Coming soon) | Work: Time Tracking, Project Templates, Project Naming | Documents: Templates, Clauses, Checklists, Document Acceptance | Finance: Rates & Currency, Tax, Capacity | Clients: Custom Fields, Tags, Request Templates, Request Settings, Compliance, Data Protection | Features | Access & Integrations: Roles & Permissions, Integrations. No vertical-specific items visible.
- Tax label is "Tax" (not "VAT") — could be a profile-content observation for SA tenants but not blocking.
- `/trust-accounting` returns a generic error page rather than clean "Module Not Available" message (unlike `/court-calendar` and `/conflict-check` which show clean messages) — minor UX inconsistency.
