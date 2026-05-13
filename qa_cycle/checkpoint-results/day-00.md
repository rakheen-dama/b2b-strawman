# Day 0 Checkpoint Results — Firm Org Onboarding (Keycloak)

**Date**: 2026-05-13
**Cycle**: 1 (clean slate)
**Branch**: `bugfix_cycle_2026-05-13`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)

---

## Phase A: Access request & OTP verification

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.1 | Landing page loads, zero console errors | PASS | URL: `http://localhost:3000/`, title "Kazi -- Practice management, built for Africa", 0 console errors |
| 0.2 | Click "Get Started" routes to `/request-access` | PASS | Navigated to `http://localhost:3000/request-access` |
| 0.3 | Form fields visible: Email, Full Name, Organisation, Country, Industry | PASS | All 5 fields rendered: Work Email, Full Name, Organisation Name, Country (combobox), Industry (combobox) |
| 0.4 | Fill and submit form | PASS | Email=thandi@mathebula-test.local, Name=Thandi Mathebula, Org=Mathebula & Partners, Country=South Africa, Industry=Legal Services |
| 0.5 | Transitions to OTP step | PASS | Page shows "Check Your Email" with OTP input for thandi@mathebula-test.local, countdown timer active |
| 0.6 | OTP email arrives in Mailpit | PASS | Mailpit ID `Ru47URTCyddT9HjHqEkShU`, subject "Your Kazi verification code", to thandi@mathebula-test.local |
| 0.7 | OTP entered and verified | PASS | 6-digit code `154573` entered, Verify button clicked successfully |
| 0.8 | Success card: "Request Submitted" | PASS | Page shows "Request Submitted" with "Your access request has been submitted for review" |

## Phase B: Platform admin approval

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.9 | Navigate to dashboard, redirected to Keycloak login | PASS | Redirected to `http://localhost:8180/realms/docteams/protocol/openid-connect/auth?...` |
| 0.10 | Login as padmin, lands on platform admin home | PASS | Logged in as padmin@docteams.local, landed on `/platform-admin/access-requests` |
| 0.11 | Navigate to `/platform-admin/access-requests` | PASS | Already on the page after login |
| 0.12 | Mathebula & Partners visible in Pending tab | PASS | Row: Org=Mathebula & Partners, Industry=Legal Services, Country=South Africa, Status=PENDING |
| 0.13 | All submitted fields render inline on request row | PASS | Table columns: Org Name, Email, Name, Country, Industry, Submitted (timestamp), Status, Actions -- all populated |
| 0.14 | Click Approve -> AlertDialog -> Confirm | PASS | AlertDialog: "Approve access request for Mathebula & Partners? This will create a Keycloak organization, provision a tenant schema, and send an invitation." |
| 0.15 | Status = APPROVED, no provisioning error | PASS | Approved tab shows APPROVED status, Pending tab empty |
| 0.16 | Vertical profile auto-assigned = legal-za | PASS | DB: `vertical_profile='legal-za'`, `terminology_namespace='en-ZA-legal'`, `default_currency='ZAR'`, modules: court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing |
| 0.17 | Keycloak invitation email to Thandi | PASS | Mailpit ID `cB9cAdvmGbATcmkY4BNy6C`, subject "Invitation to join the Mathebula & Partners organization" |

## Phase C: Owner Keycloak registration

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.18 | Open Keycloak invitation link | PASS | Navigated through accept-invite flow, reached Keycloak registration page |
| 0.19 | Registration page loads with org pre-bound | PASS | Heading: "Create an account to join the Mathebula & Partners organization", email pre-filled |
| 0.20 | Fill registration form | PASS | First Name=Thandi, Last Name=Mathebula, Password=SecureP@ss1 |
| 0.21 | Submit -> redirected to dashboard | PASS | Landed at `/org/mathebula-partners/dashboard` |
| 0.22 | Sidebar shows org name + user name | PASS | org="Mathebula & Partners", user="Thandi Mathebula" |
| 0.23 | Legal terminology active | PASS | Matters (not Projects), Clients (not Customers), Fee Notes (not Invoices), Engagement Letters (not Proposals), Mandates (not Retainers) |
| 0.24 | Legal module nav items visible | PASS | All 4: Matters, Trust Accounting, Court Calendar, Conflict Check. Plus: Adverse Parties, Tariffs, Compliance |
| 0.25 | Screenshot captured | PASS | `day-00-firm-dashboard-legal.png` |

## Phase D: Team invites

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.26 | Navigate to Team via sidebar | PASS | `/org/mathebula-partners/team`, Team is top-level nav item |
| 0.27 | Thandi listed as Owner, no upgrade gate | PASS | Members table: Thandi Mathebula / Owner. No tier/upgrade UI. |
| 0.28 | Invite Bob as Admin | PASS | "Invitation sent to bob@mathebula-test.local", count: "2 members (1 pending)" |
| 0.29 | Invite Carol as Member | PASS | Carol invited with Member role |
| 0.30 | Two Keycloak invitation emails arrived | PASS | Bob: `CoJ9q2keX7TWww4q9iMvDq`, Carol: `EXL52f2DsaDVumnVeTRfaR` |
| 0.31 | Bob registers and reaches dashboard | PASS | Keycloak registration (Bob/Ndlovu/SecureP@ss2), landed at `/org/mathebula-partners/dashboard` |
| 0.32 | Carol registers and reaches dashboard | PASS | Keycloak registration (Carol/Mokoena/SecureP@ss3), landed at `/org/mathebula-partners/dashboard` |

## Day 0 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Org created via real access-request -> approval -> KC registration | PASS | Full onboarding flow end-to-end, no mock IDP |
| Three KC users exist for @mathebula-test.local | PASS | KC Admin API confirms: thandi@, bob@, carol@ all present |
| Vertical profile = legal-za, terminology + nav reflect legal | PASS | DB + UI both confirmed |
| No tier / upgrade / billing upsell visible | PASS | No gates on invite flow or team page |

---

**Overall Day 0 Result: ALL PASS (32/32 checkpoints)**

Zero console errors. Zero gaps filed. Zero blockers.
