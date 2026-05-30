# Day 0 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)

---

## Session 0 — Prep & Reset

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.A | Firm stack healthy (backend, gateway, frontend, keycloak, mailpit) | PASS | All services returned 200/302 on health check |
| 0.B | Portal running on :3002 | DEFERRED | Portal not exercised on Day 0; will verify on Day 4 |
| 0.C | No `tenant_mathebula*` schema exists | PASS | `\dn` returned no mathebula tenant schemas; clean slate confirmed |
| 0.D | No @mathebula-test.local Keycloak users | PASS | Clean slate - fresh Docker volumes |
| 0.E | No portal contacts for sipho/moroka | PASS | Clean slate - no tenant schemas exist yet |
| 0.F | Mailpit inbox cleared | PASS | `DELETE /api/v1/messages` returned `ok` |

---

## Day 0 — Phase A: Access request & OTP verification

**Actor**: Thandi Mathebula (unauthenticated)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.1 | Landing page loads at `http://localhost:3000`, zero console errors | PASS | Page title "Kazi -- Practice management, built for Africa". Zero console errors. Hero text, nav bar, Get Started / Sign In / Book a Demo visible. |
| 0.2 | Click "Get Started" -> routes to `/request-access` | PASS | Navigated to `/request-access`. Form loaded. |
| 0.3 | Form fields visible: Email, Full Name, Organization, Country, Industry | PASS | Fields: Work Email, Full Name, Organisation Name, Country (dropdown), Industry (dropdown). All present. |
| 0.4 | Fill and submit form | PASS | Email=thandi@mathebula-test.local, Name=Thandi Mathebula, Org=Mathebula & Partners, Country=South Africa, Industry=Legal Services. Request Access button enabled after all fields filled. |
| 0.5 | Transitions to OTP step (step 2) | PASS | "Check Your Email" step with "Enter the verification code sent to thandi@mathebula-test.local". Countdown timer visible (09:55). |
| 0.6 | Mailpit -> OTP email for thandi, subject contains "verification" | PASS | Email from noreply@kazi.app, subject "Your Kazi verification code", OTP=831943. |
| 0.7 | Enter OTP -> Verify | PASS | OTP 831943 entered, Verify button clicked. |
| 0.8 | Success card: "Your request has been submitted for review" | PASS | "Request Submitted" card with "Your access request has been submitted for review. We'll notify you by email once it's been reviewed." and "Back to home" link. |

---

## Day 0 — Phase B: Platform admin approval

**Actor**: Platform Admin (padmin@docteams.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.9 | Navigate to `http://localhost:3000/dashboard` -> Keycloak login | PASS | Redirected to Keycloak login form at `localhost:8180/realms/docteams/...` |
| 0.10 | Login as padmin -> platform admin home | PASS | Logged in, landed on `/platform-admin/access-requests`. Console: only favicon.ico 404 on Keycloak domain (cosmetic, not app error). |
| 0.11 | Navigate to `/platform-admin/access-requests` | PASS | Auto-landed on this page after login. |
| 0.12 | Mathebula & Partners visible in Pending: Industry=Legal Services, Country=South Africa | PASS | Row: Org=Mathebula & Partners, Email=thandi@mathebula-test.local, Name=Thandi Mathebula, Country=South Africa, Industry=Legal Services, Status=PENDING. |
| 0.13 | All submitted fields render inline on request row | PASS | Columns: Org Name, Email, Name, Country, Industry, Submitted (timestamp), Status, Actions. Table row IS the detail surface (no separate detail view). |
| 0.14 | Click Approve -> AlertDialog -> Confirm | PASS | AlertDialog: "Approve Access Request - Approve access request for Mathebula & Partners? This will create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local." Clicked Approve. |
| 0.15 | Status = Approved, no provisioning error banner | PASS | Pending tab: "No pending access requests". Approved tab: Mathebula & Partners row with status APPROVED. No error banners. |
| 0.16 | Vertical profile auto-assigned = `legal-za` | PASS | DB: `vertical_profile='legal-za'`, `default_currency='ZAR'`, `terminology_namespace='en-ZA-legal'`. Enabled modules: court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing. Field packs: legal-za-customer, legal-za-project, conveyancing-za-project. Template packs: legal-za (v5). |
| 0.17 | Mailpit -> Keycloak invitation email to thandi | PASS | Email: Subject="Invitation to join the Mathebula & Partners organization", To=thandi@mathebula-test.local. Contains registration link with org invite token. |

---

## Day 0 — Phase C: Owner Keycloak registration

**Actor**: Thandi Mathebula

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.18 | Open Keycloak invitation link from Mailpit | PASS | App first logged out padmin session (KC logout prompt -> clicked Logout), then redirected to Keycloak registration page. |
| 0.19 | KC registration page loads with org = Mathebula & Partners pre-bound | PASS | Heading: "Create an account to join the Mathebula & Partners organization". Email pre-filled: thandi@mathebula-test.local. |
| 0.20 | Fill: First=Thandi, Last=Mathebula, Password=SecureP@ss1, Confirm=SecureP@ss1 | PASS | All fields filled correctly. |
| 0.21 | Submit -> redirected to `/org/mathebula-partners/dashboard` | PASS | Registered successfully. URL: `http://localhost:3000/org/mathebula-partners/dashboard`. |
| 0.22 | Sidebar shows org name "Mathebula & Partners", user "Thandi Mathebula" | PASS | Sidebar: org="Mathebula & Partners", user="Thandi Mathebula" (thandi@mathebula-test.local). |
| 0.23 | Legal terminology: Matters, Clients, Fee Notes (NOT Projects/Customers/Invoices) | PASS | Sidebar shows: **Matters** (not Projects), **Clients** (not Customers), **Fee Notes** (not Invoices). Also: **Engagement Letters** (not Proposals), **Mandates** (not Retainers). |
| 0.24 | Legal module nav: Matters, Trust Accounting, Court Calendar, Conflict Check | PASS | All 4 visible: **Court Calendar** (Work group), **Matters** (Matters group), **Conflict Check** + **Adverse Parties** (Clients group), **Trust Accounting** + **Tariffs** (Finance group). |
| 0.25 | Screenshot: day-00-firm-dashboard-legal.png | PASS | Screenshot captured with legal nav + terminology visible. |

---

## Day 0 — Phase D: Team invites

**Actor**: Thandi Mathebula (Owner, logged in)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.26 | Navigate to Team via sidebar -> `/org/{slug}/team` | PASS | Team page at `/org/mathebula-partners/team`. Team is a top-level nav item (Team group -> Team entry). |
| 0.27 | Thandi listed as Owner. No "Upgrade to Pro" gate | PASS | Thandi Mathebula listed as Owner in Members table. Invite form freely accessible. No tier gate, no upgrade UI. "1 member" count displayed. |
| 0.28 | Invite bob@mathebula-test.local as Admin -> Send | PASS | Email entered, Role changed to Admin via dropdown. Send Invite clicked. Confirmation: "Invitation sent to bob@mathebula-test.local." Count: "2 members (1 pending)". |
| 0.29 | Invite carol@mathebula-test.local as Member -> Send | PASS | Email entered, Role left as Member (default). Send Invite clicked. Confirmation shown. |
| 0.30 | Mailpit -> two Keycloak invitation emails arrived | PASS | Mailpit shows 4 emails total: 2x invitation to bob@ and carol@, plus earlier thandi@ invite and OTP. Both invites have subject "Invitation to join the Mathebula & Partners organization". |
| 0.31 | Bob's invite: register (First=Bob, Last=Ndlovu, Password=SecureP@ss2) -> dashboard -> logout | PASS | Opened Bob's invite link. App auto-logged out Thandi (KC logout prompt). KC registration: "Create an account to join the Mathebula & Partners organization", email=bob@mathebula-test.local. Registered: First=Bob, Last=Ndlovu, Password=SecureP@ss2. Landed on `/org/mathebula-partners/dashboard`. |
| 0.32 | Carol's invite: register (First=Carol, Last=Mokoena, Password=SecureP@ss3) -> dashboard -> logout | PASS | Opened Carol's invite link. App auto-logged out Bob. KC registration: email=carol@mathebula-test.local. Registered: First=Carol, Last=Mokoena, Password=SecureP@ss3. Landed on `/org/mathebula-partners/dashboard`. |

---

## Day 0 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Org created via real access-request -> approval -> KC registration (no mock IDP) | PASS | Full flow: /request-access form -> OTP email -> padmin approval -> KC registration -> dashboard. Zero mock IDP usage. |
| Three Keycloak users exist under realm `docteams` for @mathebula-test.local | PASS | KC admin API: Thandi Mathebula (thandi@, enabled=True), Bob Ndlovu (bob@, enabled=True), Carol Mokoena (carol@, enabled=True). |
| Vertical profile = `legal-za`, terminology + nav reflect legal | PASS | DB: vertical_profile=legal-za, terminology_namespace=en-ZA-legal. Sidebar: Matters, Clients, Fee Notes, Court Calendar, Trust Accounting, Conflict Check, Tariffs, Engagement Letters, Mandates, Adverse Parties. |
| No tier / upgrade / billing upsell visible | PASS | No "Upgrade to Pro", no plan picker, no tier badge, no member-limit gating anywhere during Day 0 onboarding flow. |

---

## Console Errors

Zero JavaScript console errors during Day 0 execution. Only non-app error: favicon.ico 404 on Keycloak domain (cosmetic).

## Gaps Filed

None. Day 0 passed cleanly with zero gaps.
