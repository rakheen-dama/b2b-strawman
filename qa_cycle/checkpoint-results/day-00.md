# Day 0 — Checkpoint Results (Cycle 1)

**Date**: 2026-05-23
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent

---

## Session 0 — Prep & Reset

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.A | No `tenant_thornton*` schema exists | PASS | `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_thornton%'` returned 0 rows |
| 0.B | No `@thornton-test.local` Keycloak users | PASS | Keycloak admin API search returned 0 users |
| — | Mailpit cleared | PASS | `DELETE /api/v1/messages` returned `ok` |

---

## Day 0 — Phase A: Access request & OTP verification

**Actor**: Thandi Thornton (unauthenticated)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.1 | Landing page loads at `http://localhost:3000` | PASS | Page title "Kazi - Practice management, built for Africa". Hero text, nav bar, Get Started button visible. |
| 0.2 | Click "Get Started" → `/request-access` | PASS | Navigated to `/request-access`. Form loaded with Work Email, Full Name, Organisation Name, Country, Industry fields. Note: button is labeled "Get Started" not "Request Access" on the landing page. |
| 0.3 | Fill form (email, name, org, country=ZA, industry=Accounting) | PASS | All fields populated: thandi@thornton-test.local, Thandi Thornton, Thornton & Associates, South Africa, Accounting |
| 0.4 | Submit → OTP step appears | PASS | "Check Your Email" step displayed with verification code input for thandi@thornton-test.local. Code expires in 10 minutes. |
| 0.5 | Mailpit → retrieve OTP | PASS | Email received from noreply@kazi.app with subject "Your Kazi verification code". OTP: 568295 |
| 0.6 | Enter OTP → verify → success | PASS | OTP verified via API (`POST /api/access-requests/verify`). Response: `{"message":"Email verified successfully"}`. Note: Browser extension popup blocked direct UI interaction on OTP form; verified via API as workaround. |

---

## Day 0 — Phase B: Platform admin approval

**Actor**: Platform Admin (padmin@docteams.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.7 | Open fresh browser context | PASS | New tab created in new tab group |
| 0.8 | Login as padmin@docteams.local | PASS | Keycloak login form submitted via JavaScript. Redirected to platform-admin dashboard. |
| 0.9 | Navigate to `/platform-admin/access-requests` | PASS | Auto-redirected after login. Page shows "Access Requests" with Pending/Approved/Rejected tabs. |
| 0.10 | Thornton & Associates in Pending with Industry=Accounting | PASS | Row visible: Org=Thornton & Associates, Email=thandi@thornton-test.local, Name=Thandi Thornton, Country=South Africa, Industry=Accounting, Status=PENDING |
| 0.11 | Click Approve → confirm → status Approved | PASS | Confirm dialog: "Approve access request for Thornton & Associates? This will create a Keycloak organization, provision a tenant schema, and send an invitation." Clicked Approve. Pending tab shows "No pending access requests". Approved tab shows status APPROVED. |
| 0.12 | Vertical profile auto-assigned to `accounting-za` | PASS | DB query: `org_settings.vertical_profile = 'accounting-za'`, `default_currency = 'ZAR'`, `terminology_namespace = 'en-ZA-accounting'`. Field packs: accounting-za-customer, accounting-za-customer-trust, accounting-za-project. Template packs: common, compliance-za, accounting-za. Automation pack: automation-accounting-za. |
| 0.13 | Mailpit → Keycloak invitation email to thandi | PASS | Email received: To=thandi@thornton-test.local, Subject="Invitation to join the Thornton & Associates organization". Contains registration link with org invite token. |

---

## Day 0 — Phase C: Owner Keycloak registration

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.14 | Open Keycloak invitation link | PASS | Registration form: "Create an account to join the Thornton & Associates organization". Email pre-filled: thandi@thornton-test.local |
| 0.15 | Register: First=Thandi, Last=Thornton, Password=SecureP@ss1 | PASS | Form submitted via JavaScript. Registration completed. |
| 0.16 | Redirected to `/org/thornton-associates/dashboard` | PASS | URL: `http://localhost:3000/org/thornton-associates/dashboard` |
| 0.17 | Sidebar shows org name "Thornton & Associates" | PASS | Green text "Thornton & Associates" at top of sidebar. Breadcrumb: "Thornton & Associates > Dashboard" |
| 0.18 | Sidebar shows Engagements (not Projects) and Clients (not Customers) | PASS | Sidebar sections: WORK (Dashboard, My Work, Calendar), ENGAGEMENTS (Engagements, Recurring Schedules), CLIENTS, FINANCE, TEAM (Team), AI. Accounting terminology active. |
| 0.19 | Screenshot: Dashboard with accounting terminology | PASS | Screenshot saved. Dashboard shows: Active Engagements 0, Hours This Month 0, Avg. Margin --, Overdue Tasks 0, Budget Health. User: Thandi Thornton (thandi@thornton-test.local) |

---

## Day 0 — Phase D: Team invites

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.20 | Navigate to Settings > Team | PASS | Team page loaded. Shows "Team - 1 member". Invite form with Email address, Role dropdown, Send Invite button. |
| 0.21 | Thandi is Owner. No tier gate on invite | PASS | Thandi Thornton listed as Owner. No "Upgrade to Pro" or tier gate visible. Invite form immediately accessible. |
| 0.22 | Invite bob@thornton-test.local as Admin | PASS | Green confirmation: "Invitation sent to bob@thornton-test.local." Pending Invitations tab shows Bob with Role=Admin. |
| 0.23 | Invite carol@thornton-test.local as Member | PASS | Green confirmation: "Invitation sent to carol@thornton-test.local." Count: "3 members (2 pending)" |
| 0.24 | Bob and Carol register via invite links | PASS | Bob: Registered as Bob Ndlovu with SecureP@ss2. Redirected to dashboard. Carol: Registered as Carol Mokoena with SecureP@ss3. Redirected to dashboard. |

### Phase A-D Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Org created via real access request → approval → KC registration | PASS | Full flow: request-access form → OTP → padmin approval → KC registration → dashboard |
| Three real Keycloak users exist | PASS | KC admin API: Thandi Thornton (thandi@), Bob Ndlovu (bob@), Carol Mokoena (carol@) — all enabled=true |
| NO tier upgrade UI encountered anywhere | PASS | No "Upgrade to Pro", no plan picker, no tier badge seen during onboarding or team invite |
| Vertical profile `accounting-za` active on tenant | PASS | DB: vertical_profile=accounting-za, currency=ZAR, terminology=en-ZA-accounting |

---

## Day 0 — Phase E–I: PENDING

Phases E through I (General settings, rates, tax, custom fields, templates, automations, progressive disclosure, billing) have not yet been executed. Thandi is logged in and ready to proceed.

---

## Console Errors

No JavaScript console errors observed on dashboard page after login.

## Gaps Filed

None so far — all Phase A-D checkpoints passed.

## Notes

- Chrome password manager extension caused `Cannot access a chrome-extension:// URL` errors on Keycloak login and OTP forms. Workaround: used JavaScript `document.forms[0].submit()` to bypass extension overlay. This is a test environment issue, not a product bug.
- Keycloak login flow is two-step (email first, then password) for existing users.
- Gateway session persists across tab groups — gateway restart required for clean user switching.
