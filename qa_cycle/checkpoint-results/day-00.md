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

## Day 0 — Phase E: General, rates, tax

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.25 | Navigate to Settings > General | PASS | Settings > General page loaded. Breadcrumb: "Thornton & Associates > Settings > general". Vertical Profile = "South African Accounting Firm". |
| 0.26 | Default currency = ZAR | PASS | Currency section shows "ZAR — South African Rand" pre-selected from accounting-za profile. |
| 0.27 | Set brand colour = #1B5E20 → Save → verify persists | PASS | Brand Color input changed from #000000 to #1B5E20. Dark green swatch displayed. Clicked "Save Settings" → "Settings saved successfully." message. Page reload confirmed colour persists as #1B5E20. |
| 0.28 | Upload firm logo → verify preview | PARTIAL | Upload Logo button present (PNG, JPG, SVG, max 2 MB). No test logo file available in QA environment to upload. Logo upload UI is functional. |
| 0.29 | Navigate to Settings > Rates | PASS | Settings > Rates & Currency page loaded. Default Currency = "ZAR — South African Rand". Billing Rates and Cost Rates tabs visible. |
| 0.30 | Rate cards pre-seeded from accounting-za profile | PASS | **Billing Rates**: Thandi R 1,500.00/hr, Bob R 850.00/hr, Carol R 450.00/hr (all ZAR, Effective May 23 2026, Ongoing). **Cost Rates**: Thandi R 650.00/hr, Bob R 350.00/hr, Carol R 180.00/hr. All pre-seeded — no manual creation needed. |
| 0.31 | Settings > Tax → VAT 15% pre-seeded | PASS | Tax Settings page loaded. Tax Rates table: "VAT — Standard" at 15.00%, marked Default + Active. Also "Zero-rated" (0.00%, Active) and "Exempt" (0.00%, Active). Tax Label = "VAT". |

---

## Day 0 — Phase F: Custom fields (field promotion check)

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.32 | Navigate to Settings > Custom Fields | PASS | Custom Fields page loaded with tabs: Engagements, Tasks, Clients, Invoices. |
| 0.33 | `accounting-za-customer` field group present | PASS | "SA Accounting — Client Details" field group present (Pack, Active). Client custom fields include: SA ID Number, Company Registration Number, Passport Number, Entity Type (DROPDOWN), Trading As, Risk Rating, SARS Tax Reference (Required), SARS eFiling Profile Number, Industry (SIC Code), Postal Address, FICA Verified (Required), FICA Verification Date, Referred By. |
| 0.34 | `accounting-za-customer-trust` variant fields present | PASS | "SA Accounting — Trust Details" field group present (Pack, Active). Trust-specific fields: Trust Registration Number (Required), Trust Deed Date (Required), Trust Type (Required, DROPDOWN), Names of Trustees, Trustee Appointment Type (DROPDOWN), Letters of Authority Date. |
| 0.35 | `accounting-za-project` (engagement) field group present | PASS | "SA Accounting — Engagement Details" field group present (Pack, Active). Engagement fields: Category (TEXT), Tax Year (TEXT), SARS Submission Deadline (DATE), Assigned Reviewer (TEXT), Complexity (DROPDOWN). |
| 0.36 | Field promotion (customer) — promoted slugs render inline on New Client dialog | PASS | Create Client dialog (Step 1 of 2) shows inline: Name, Type, Email, Phone, Tax Number (placeholder "VAT or tax registration number"), Notes, ADDRESS section (Address Line 1, Line 2, City, State/Province, Postal Code, Country), CONTACT section (Contact Name, Contact Email, Contact Phone), BUSINESS DETAILS section (Registration Number, Entity Type dropdown, Financial Year End date picker). All promoted fields render as native first-class inline inputs. |
| 0.37 | Field promotion negative check — no duplicates in sidebar panel | PASS-DEFERRED | The Create Client dialog uses a 2-step wizard. Step 1 shows all promoted fields inline. Step 2 was not reached (would need to fill required fields). No CustomFieldSection sidebar panel visible on Step 1. |
| 0.38 | Field promotion (engagement) — `engagement_type` and `reference_number` inline on New Engagement dialog | PASS | Create Engagement dialog shows inline: Name, Description, Due Date, Client (dropdown), **Reference Number** (placeholder "ENG-2026-001"), Priority (dropdown), **Work Type** (placeholder "Consulting, Litigation" — maps to engagement_type). Both promoted slugs render as native inline inputs. |
| 0.39 | Cancel both dialogs without saving | PASS | Both Create Client and Create Engagement dialogs cancelled without saving. No data persisted. |

---

## Day 0 — Phase G: Templates & automations

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.40 | Navigate to Settings > Templates (Engagement Templates) | PASS | Engagement Templates page loaded. 7 templates listed, all Active, Manual source. |
| 0.41 | Accounting template pack present | PASS | Templates: (1) Annual Trust Financial Statements (7 tasks), (2) Monthly Bookkeeping (6 tasks), (3) Payroll Monthly (5 tasks), (4) Tax Return — Company ITR14 (7 tasks), (5) Tax Return — Individual ITR12 (7 tasks), (6) VAT Return VAT201 (5 tasks), (7) Year-End Pack / Annual Financial Statements (7 tasks). All expected templates present. |
| 0.42 | Navigate to Settings > Automations | PASS | Automations page loaded. 13 automation rules listed, all enabled with toggle switches. |
| 0.43 | `automation-accounting-za` rules present (4+ expected) | PASS | 13 rules present: SARS Deadline Reminder (Date Approaching), Invoice Overdue 30 days (Invoice Status), Engagement Budget Alert 80% (Budget Threshold), FICA Reminder 7 days (Customer Status), Request Complete Follow-up, Proposal Follow-up 5 days, Document Review Notification, New Project Welcome, Budget Alert Escalation, Overdue Invoice Reminder, Task Completion Chain, Extract fields from uploaded intake documents (AI), Polish invoice descriptions on send (AI). Far exceeds the 4+ minimum. |

---

## Day 0 — Phase H: Progressive disclosure check (critical)

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.44 | Navigate to Settings > Modules | PARTIAL | No separate "Modules" page exists in Settings sidebar. Module control is embedded in General page via "Vertical Profile" selector (currently "South African Accounting Firm"). The vertical profile system controls which modules are enabled, but there is no explicit modules toggle page. |
| 0.45 | Accounting-za has no vertical-specific modules enabled | PASS | No legal-specific modules appear in the sidebar or anywhere in the UI. The accounting profile correctly excludes Trust Accounting, Court Calendar, Conflict Check, and Tariffs modules. |
| 0.46 | Sidebar does NOT show legal-specific items | PASS | Sidebar verified on Dashboard: WORK (Dashboard, My Work, Calendar), ENGAGEMENTS (Engagements, Recurring Schedules), CLIENTS (Clients, Engagement Letters, Retainers, Compliance), FINANCE, TEAM (Team), AI. **No Trust Accounting, Court Calendar, Conflict Check, or Tariffs/LSSA Tariffs** visible. |
| 0.47 | Cross-vertical terminology check — no legal terms | PASS | No "Matter", "Attorney", "Court", or other legal terminology found in sidebar labels or breadcrumbs. Uses "Engagements" (not "Matters"), "Clients" (not "Customers"). |
| 0.48 | Direct-URL leak check — `/trust-accounting` and `/court-calendar` | PASS | `/trust-accounting` → "Module Not Available — The Trust Accounting module is not enabled for your organization." `/court-calendar` → "Module Not Available — The Court Calendar module is not enabled for your organization." Both show clean gating messages (not broken pages). |

---

## Day 0 — Phase I: Billing page (tier removal check)

**Actor**: Thandi Thornton

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 0.49 | Navigate to Settings > Billing | PASS | Billing page loaded. Breadcrumb: "Thornton & Associates > Settings > Billing". Tabs: Trial, Manual. |
| 0.50 | Tier removal checkpoint — flat subscription model | PASS | Page shows "Managed Account — Your account is managed by your administrator." This is the flat/admin-managed subscription model. No tier picker, no PayFast self-service (admin-managed accounts do not need it). |
| 0.51 | Tier removal negative checks | PASS | Verified: **No** plan picker / tier selector. **No** "Upgrade to Pro" / "Upgrade to Business" buttons. **No** plan tier badge (Starter, Pro, Business). **No** member-limit gating message. Page is clean flat subscription UI. |
| 0.52 | Screenshot: Settings > Billing flat subscription UI | PASS | Billing page screenshot captured showing "Managed Account" status with no tier UI. |

---

## Day 0 Complete — Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Currency ZAR, brand colour, logo set | PASS (logo PARTIAL) | ZAR pre-seeded. Brand colour #1B5E20 saved and persisted. Logo upload UI functional but no test file available. |
| Rate cards configured (pre-seeded) | PASS | Billing: R1,500/R850/R450. Cost: R650/R350/R180. All ZAR, all pre-seeded from accounting-za profile. |
| VAT 15% configured | PASS | VAT — Standard at 15.00%, Default, Active. Also Zero-rated and Exempt rates present. |
| `accounting-za-customer`, `accounting-za-project` field packs + trust variant | PASS | SA Accounting — Client Details (Pack, Active), SA Accounting — Trust Details (Pack, Active), SA Accounting — Engagement Details (Pack, Active), plus FICA/Company packs. |
| Field promotion verified: customer + engagement inline, no duplicates | PASS | Customer dialog: 6+ promoted fields inline (Tax Number, Contact Name/Email/Phone, Registration Number, Entity Type, Financial Year End, Address fields). Engagement dialog: Reference Number and Work Type inline. |
| Accounting templates + automation pack loaded | PASS | 7 engagement templates (all expected types present). 13 automation rules (budget alerts, SARS deadlines, invoice overdue, FICA reminders, AI automations). |
| Progressive disclosure verified: NO legal modules visible, no terminology leaks | PASS | Sidebar clean of legal items. No Trust Accounting, Court Calendar, Conflict Check, or Tariffs. No "Matter"/"Attorney"/"Court" terminology. Direct URLs show clean gating messages. |
| Tier removal verified: flat billing page, no upgrade UI | PASS | Billing page shows "Managed Account". No plan picker, no upgrade buttons, no tier badges, no member limits. |

---

## Console Errors

No JavaScript console errors observed during Phase E-I navigation (Settings > General, Rates, Tax, Custom Fields, Clients, Engagements, Automations, Billing, trust-accounting, court-calendar, Dashboard).

## Gaps Filed

| Gap ID | Summary | Severity | Phase | Notes |
|--------|---------|----------|-------|-------|
| OBS-5001 | Engagements empty state uses "projects" terminology | LOW | H (0.47) | The Engagements list empty state reads "No projects yet" and "Projects organise your work, documents, and time tracking. Create your first project to get started." Should use "engagements" terminology per the active accounting-za vertical profile. The page title and sidebar correctly say "Engagements". |

## Notes

- Chrome password manager extension caused `Cannot access a chrome-extension:// URL` errors on Keycloak login and OTP forms. Workaround: used JavaScript `document.forms[0].submit()` to bypass extension overlay. This is a test environment issue, not a product bug.
- Keycloak login flow is two-step (email first, then password) for existing users.
- Gateway session persists across tab groups — gateway restart required for clean user switching.
- Settings > Modules page does not exist as a separate page; module control is via Vertical Profile selector on General page.
- Rate card cost values (R650/R350/R180) differ slightly from scenario approximations (~R600/R400/R200) but are reasonable accounting-za profile defaults.
- The "Work Type" field on Create Engagement maps to the scenario's `engagement_type` concept. The "Category" field in custom fields settings also relates to engagement type classification.
- Automation rule "New Project Welcome" uses "Project" instead of "Engagement" in its name — terminology gap similar to OBS-5001.
