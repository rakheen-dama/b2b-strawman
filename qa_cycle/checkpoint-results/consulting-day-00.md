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
| 0.28 | IN PROGRESS | Billing rate for Zolani (R1,800/hr) created. Bob and Carol billing rates + all cost rates still needed |

---

## Gaps Logged

| GAP_ID | Day / Checkpoint | Severity | Type | Summary |
|--------|------------------|----------|------|---------|
| GAP-C-01 | D0 / 0.10 | LOW | Profile-content | `vertical_profile` not auto-assigned for "Marketing" industry — field left empty |
| GAP-C-02 | D0 / 0.24 | LOW | Profile-content | Default currency is USD, not ZAR, for SA-registered org (consulting-generic has no currency default) |
| GAP-C-03 | D0 / 0.28 | MED | Profile-content | `consulting-generic` has no rate-card defaults — all rates "Not set" for new agency tenant |

---

## Notes

- Keycloak session management required manual cookie clearing between user registrations (Playwright shares cookies across tabs on same domain). This is a test-tooling issue, not a product bug.
- The registration flow showed "You are already authenticated as different user" error when the admin session was still active. The registration actually completed despite the error page — user was created. This is a UX concern but not a blocker (real users would use separate browser sessions).
- Settings navigation shows these categories: General, Billing, Notifications, Email, Security (Coming soon) | Work: Time Tracking, Project Templates, Project Naming | Documents: Templates, Clauses, Checklists, Document Acceptance | Finance: Rates & Currency, Tax, Capacity | Clients: Custom Fields, Tags, Request Templates, Request Settings, Compliance, Data Protection | Features | Access & Integrations: Roles & Permissions, Integrations. No vertical-specific items visible.
