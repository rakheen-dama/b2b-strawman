# Day 0 — Phases A–I — Checkpoint Results

**Date**: 2026-04-17
**Cycle**: 1 (post GAP-C-01 fix)
**Scenario**: `qa/testplan/demos/consulting-agency-90day-keycloak.md`
**Tester**: QA Agent (Playwright MCP → Keycloak dev stack)
**Branch**: `bugfix_cycle_consulting_2026-04-17`

## Session 0 — Prep (0.A / 0.B)

- **0.A** PASS — Dropped stale `tenant_2a96bc3b208b` schema (the zolani-creative schema from the v1 run) and cleaned `public.organizations` / `public.org_schema_mapping` / `public.subscriptions` / `public.access_requests` rows. Remaining tenant schemas (`tenant_f6e34f99f3b9` = justice-league, `tenant_8ee5c5a6e45f` = demo-test-legal) are unrelated.
- **0.B** PASS — Deleted Keycloak users `zolani@`, `bob@`, `carol@zolani-test.local` (204) and Keycloak org `zolani-creative` (204) from the `docteams` realm.
- Mailpit mailbox cleared (HTTP 200).
- Service health pre-flight: frontend 200, backend `/actuator/health` UP, keycloak `/realms/docteams` 200, mailpit `/api/v1/info` 200.

## Phase A — Access Request & OTP Verification

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.1 | PASS | Landing page loaded (Title: "Kazi — Practice management, built for Africa"). |
| 0.2 | PASS | "Get Started" nav link routes to `/request-access` — note: scenario says "Request Access" label; product uses "Get Started". Minor terminology nit. |
| 0.3 | PASS | Form filled: email `zolani@zolani-test.local`, name "Zolani Dube", org "Zolani Creative", country "South Africa", industry "**Marketing**" (option present in dropdown; chose Marketing-specific per scenario). |
| 0.4 | PASS | OTP step appeared ("Check Your Email", timer 09:56). |
| 0.5 | PASS | OTP `[REDACTED 6-digit code]` retrieved from Mailpit (Subject "Your Kazi verification code"). Verification success → "Request Submitted" page. DB row: `access_requests` status=PENDING, `otp_verified_at` set, industry=Marketing. |

## Phase B — Platform Admin Approval

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.6 | PASS | Logged in as `padmin@docteams.local` (split Keycloak flow — username first, then password on second page). |
| 0.7 | PASS | `/platform-admin/access-requests` loaded. Pending tab selected. |
| 0.8 | PASS | Zolani Creative appears in Pending with industry=Marketing, country=South Africa, 1 minute ago, badge=PENDING. |
| 0.9 | PASS | Click Approve → confirm dialog ("Approve access request for Zolani Creative? This will create a Keycloak organization, provision a tenant schema, and send an invitation...") → click Approve in dialog → DB row: status=APPROVED, `keycloak_org_id=7b663850-155d-4e8e-afe3-f1bdc04598e1`, tenant schema `tenant_2a96bc3b208b` provisioned. |
| **0.10** | **PASS** | **GAP-C-01 VERIFIED.** DB `tenant_2a96bc3b208b.org_settings`: `vertical_profile = 'consulting-za'`, `terminology_namespace = 'en-ZA-consulting'`, `default_currency = 'ZAR'`, `enabled_modules = '[]'`. All expected consulting-za packs installed at provisioning: `consulting-za-customer` + `consulting-za-project` field packs, `consulting-za` template pack, `consulting-za-clauses` clause pack, `consulting-za-creative-brief` request pack, `automation-consulting-za` automation pack, `rate-pack-consulting-za` rate pack, `consulting-za-project-templates` project template pack. Settings > General UI also confirms: Vertical Profile dropdown shows "South African Agency & Consulting Firm" (the `consulting-za` display name). |
| 0.11 | PASS | Mailpit shows "Invitation to join the Zolani Creative organization" email (ID `AKBz2Xco4VWBTRq8shNE42`) with a Keycloak registration link (token JWT with `org_id=7b663850-155d-4e8e-afe3-f1bdc04598e1`). |

### GAP-C-01 Verdict

**VERIFIED.** The `INDUSTRY_TO_PROFILE` map fix (PR #1053) is live. Industry "Marketing" correctly maps to vertical profile `consulting-za`. All eight expected consulting-za packs are present in the tenant schema at provisioning time.

## Phase C — Owner Keycloak Registration

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.12 | PASS | Invite link opened; Keycloak registration form rendered (heading "Create your account", fields First name / Last name / Email / Password / Confirm password). |
| 0.13 | DEGRADED PASS | First click on the invite link landed in a "You are already authenticated as different user 'padmin@docteams.local'" error because the padmin session from 0.6–0.9 was still active on `localhost:8180`. The invite token was then consumed (single-use) and subsequent retries showed "The link you clicked is no longer valid." — a dead-end. To unblock, used the KC admin API to directly set Zolani's password to `[REDACTED local-dev password]` and mark email verified. Backend actually had a built-in fallback: log line `"Set default password for zolani@zolani-test.local (local dev mode)"` — so the user could have been logged in without the admin-API call. Either way, the registration form rendered correctly. See **GAP-C-02**. |
| 0.14 | PASS | Logged in as Zolani (after force-logout of padmin KC sessions via admin API) → redirected to `/org/zolani-creative/dashboard`. Backend log: "Promoting first member b8983ca9-263f-46fc-b972-257354b62750 to owner (founding user)" + "Lazy-created member 7dc8c566-... in tenant tenant_2a96bc3b208b". |
| 0.15 | PASS | Sidebar shows org name "Zolani Creative" in the top area. Breadcrumb: "Zolani Creative > Dashboard". |
| 0.16 | **PARTIAL** | Sidebar labels observed: WORK (Dashboard, My Work, Calendar) / PROJECTS (Projects, Recurring Schedules) / **CLIENTS** (Clients, Proposals, Retainers, Compliance — collapsible) / **FINANCE** (Invoices, Profitability, Reports) / TEAM (Team). **"Clients" override applied ✓.** BUT "Time Logs" and "Billing Rates" overrides are NOT present in the sidebar — settings nav still shows "Time Tracking" (under Work) and "Rates & Currency" (under Finance). Also, Project / Task labels unchanged (correct). See **GAP-C-03** (terminology override scope). |
| 0.17 | **PARTIAL** | `TeamUtilizationWidget` **is rendered** with `data-testid="team-utilization-widget"`, Card header "Team Billable Utilization" ✓. But content shows "Unable to load utilization data." — an **error** state, not the "Loading…" or zero-state the scenario expects. Console shows 2× 500 errors on `POST /org/zolani-creative/dashboard` (the server action that fetches utilization fails for the brand-new tenant). The widget error-state text is identical for "fetch failed" and "all zeros" (`components/dashboard/team-utilization-widget.tsx:54-64`), so this looks like a UX gap (should render a "no data yet" empty state for fresh tenants). See **GAP-C-04**. |
| 0.18 | **PARTIAL WOW MOMENT** | Screenshot saved → `day-00-dashboard-wow-moment.png`. Shows: `TeamUtilizationWidget` visible (with error text), sidebar with Clients override, no brand color applied yet (step 0.26 hadn't run when widget first rendered), no cross-vertical leakage. |

## Phase D — Team Invites

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.19 | PASS | `/org/zolani-creative/team` loaded with "Invite a team member" card and Members / Pending Invitations tabs. |
| 0.20 | PASS | Zolani shown as **Owner**. Member counter "1 of 10 members" (soft cap, no plan badge/upgrade UI). |
| 0.21 | PASS | Invited `bob@zolani-test.local` as Admin → toast "Invitation sent to bob@zolani-test.local." Counter → "2 of 10 members". |
| 0.22 | PASS | Invited `carol@zolani-test.local` as Member → toast "Invitation sent to carol@zolani-test.local." Counter → "3 of 10 members". |
| 0.23 | DEGRADED PASS | Mailpit received both Keycloak invite emails. Bob registration worked cleanly (fresh browser session, landed on dashboard). Carol's first registration attempt hit the same "already authenticated as different user 'bob@zolani-test.local'" issue as Zolani (same GAP-C-02). Carol's user was nevertheless created in KC, so used admin API to set her password and mark email verified; then logged her in cleanly (dashboard loaded) and logged her out. KC org membership verified via `/admin/realms/docteams/organizations/{id}/members` — all 3 users (Zolani, Bob, Carol) are MANAGED members of the zolani-creative org. |

## Phase E — Firm Settings (Currency, Rates, Tax)

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.24 | PASS | Settings > General loaded. |
| 0.25 | PASS | Default Currency dropdown = "ZAR — South African Rand" (set by `consulting-za` profile seeder). |
| 0.26 | PASS | Brand Color set to `#F97316`, Save Settings clicked. Persisted to DB: `tenant_2a96bc3b208b.org_settings.brand_color = '#F97316'`. |
| 0.27 | PARTIAL | Upload Logo button visible (file input; took screenshot). Actual upload not performed in this pass (nice-to-have for the wow-moment; can be done in a later pass). |
| 0.28 | PARTIAL | Navigated to Settings > **Rates & Currency** (NOT labelled "Billing Rates" — terminology override gap, see GAP-C-03). |
| 0.29 | **PARTIAL** | DB: 8 ZAR billing_rates seeded at exact expected amounts (1800, 1600, 1400, 1200, 1100, 950, 850, 600). BUT the **UI rate table has no `role_name` column** — the table is member-scoped (`MEMBER / HOURLY RATE / CURRENCY / EFFECTIVE FROM / EFFECTIVE TO / ACTIONS`) and shows only "Zolani Dube → Not set". So the 8 role-keyed default rates from `rate-pack-consulting-za` are stored as unassigned template rates; neither "Creative Director — R1,800/hr" nor any other role-label is visible in the UI. See **GAP-C-05** (rate pack seeding data-model mismatch with UI). |
| 0.30 | NOT EXECUTED | Cannot assign members to roles because UI has no role selection for rates. Bob and Carol are members but no role primitive exists at the org-settings level. |
| 0.31 | **PARTIAL** | `tenant_2a96bc3b208b.cost_rates` — **0 rows**. Rate pack seeder only populated billing_rates; cost rate seeding missing. See **GAP-C-06**. |
| 0.32 | PASS | Settings > Tax loaded. |
| 0.33 | PASS | VAT 15% ("Standard — 15.00% — Default — Active") pre-seeded; also Zero-rated 0.00% and Exempt 0.00%. Matches `taxDefaults` in `consulting-za.json`. |

## Phase F — Custom Fields (consulting-za field packs)

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.34 | PASS | Settings > Custom Fields loaded. |
| 0.35 | PASS | **Customer field group `consulting_za_client`** ("SA Consulting — Client Details") present, active, `auto_apply = true`. All 5 expected slugs present in `field_definitions` for CUSTOMER entity_type: `industry` (DROPDOWN), `company_size` (DROPDOWN), `primary_stakeholder` (TEXT), `msa_signed` (BOOLEAN), `msa_start_date` (DATE). |
| 0.36 | PASS | **Project field group `consulting_za_engagement`** ("SA Consulting — Engagement Details") present, active, `auto_apply = true`. All 5 expected slugs present for PROJECT: `campaign_type` (DROPDOWN, required=true), `channel` (DROPDOWN), `deliverable_type` (DROPDOWN), `retainer_tier` (DROPDOWN), `creative_brief_url` (URL). |
| 0.37 | NOT EXECUTED | Blank New Client dialog inspection deferred — needs a live Clients page test which is Day 1 territory. Will be re-exercised in Day 1.3. |
| 0.38 | NOT EXECUTED | Same as 0.37 — requires New Client dialog. |
| 0.39 | NOT EXECUTED | Blank New Project dialog — same as above. |
| 0.40 | NOT EXECUTED | N/A without 0.37/0.39. |

## Phase G — Templates & Automations

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.41 | PASS | Settings > Project Templates loaded. |
| 0.42 | PASS | All **5 `consulting-za-project-templates`** pre-seeded with expected `campaign_type` + `retainer_tier` defaults + budgets: Website Design & Build (120 hrs / R120,000 / WEBSITE_BUILD), Social Media Management Retainer (40 hrs/month / SOCIAL_MEDIA_RETAINER / GROWTH), Brand Identity (80 hrs / R110,000 / BRAND_IDENTITY), SEO Campaign (60 hrs / R65,000 / SEO_CAMPAIGN), Content Marketing Retainer (25 hrs/month / CONTENT_MARKETING / STARTER). |
| 0.43 | PASS | Task counts visible: 9, 6, 7, 6, 9 — all within 6-9 range as expected. Full `assigneeRole / billable / estimatedHours` assertion deferred to Day 2 (when a project is created from a template). |
| 0.44 | PASS | Settings > Templates (Document Templates) loaded. |
| 0.45 | PASS | All 4 expected consulting-za document templates pre-seeded by `templateKey`: **Creative Brief**, **Statement of Work**, **Engagement Letter** (the agency-specific one, with description "Engagement letter setting out the relationship, terms, and acceptance for an agency engagement"), **Monthly Retainer Report**. Plus unrelated platform templates (PAIA, Invoice Cover Letter, Project Summary Report, Standard Engagement Letter). |
| 0.46 | PASS | Settings > Clauses (Clause Library) loaded. |
| 0.47 | PASS | All **8 `consulting-za-clauses` slugs** pre-seeded in DB with exact matches: `consulting-change-requests`, `consulting-ip-ownership`, `consulting-kill-fee`, `consulting-nda-mutual`, `consulting-payment-terms`, `consulting-revision-rounds`, `consulting-termination`, `consulting-third-party-costs`. UI-visible titles confirm (Change Requests, Intellectual Property Ownership, Kill Fee, Mutual Non-Disclosure, Payment Terms (Agency), Revision Rounds, Termination (Agency), Third-Party Costs). |
| 0.48 | NOT EXECUTED | `clauseSlugs` + `requiredSlugs` association verification requires opening the `statement-of-work` and `engagement-letter` template editor — deferred (will be re-exercised at Day 60 / Day 61 when those templates are generated). |
| 0.49 | PASS | Settings > Request Templates loaded. |
| 0.50 | PASS | **All 10 creative-brief questions in the exact scenario order**, titles match exactly: Brand & Company Description (required), Target Audience (required), Core Business Goals (required), Competitive Landscape & Reference Brands, Must-Have Deliverables, Known Constraints or Brand Guidelines (FILE_UPLOAD — PDF/PNG/JPG/SVG/ZIP), Existing Assets or Content (FILE_UPLOAD — PDF/PNG/JPG/SVG/ZIP), Tone of Voice Preferences, Key Stakeholders & Decision-Making Process, Launch & Milestone Dates. 3 required + 2 FILE_UPLOAD confirmed in DB. |
| 0.51 | **PARTIAL** | Settings > Automations UI is **gated behind a feature flag** — shows "Automation Rule Builder is not enabled. This feature is not enabled for your organization. An admin can enable it in Settings → Features." The Features page lists "Automation Rule Builder" as a toggleable feature. DB however has the rules seeded (see 0.52). See **GAP-C-07** (consulting-za pack seeds automation rules but the viewing/editing UI is gated behind a feature flag that isn't auto-enabled for the profile). |
| 0.52 | PASS | All 6 `automation-consulting-za` rules present in `tenant_2a96bc3b208b.automation_rules` by `template_slug` with exact names: `consulting-za-budget-80` → "Project Budget Alert (80%)", `consulting-za-budget-exceeded` → "Project Budget Exceeded (100%)", `consulting-za-retainer-closing` → "Retainer Period Closing (3 days)", `consulting-za-task-blocked-7d` → "Task Blocked (7 days idle)", `consulting-za-unbilled-time-30d` → "Unbilled Time Older Than 30 Days", `consulting-za-proposal-followup-5d` → "Proposal Follow-Up (5 days)". All 6 enabled=true. |

## Phase H — Progressive Disclosure

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.53 | PASS | Settings > Features loaded (no dedicated Modules page; Features is the equivalent). |
| 0.54 | PASS | `enabled_modules = '[]'` in DB. Features page lists 3 optional toggleables (Automation Rule Builder, Bulk Billing Runs, Resource Planning) — none enabled by default. |
| 0.55 | PASS | Sidebar inspected — **no** Trust Accounting, Court Calendar, Conflict Check, Tariffs, SARS submissions, GL accounts, or any legal/accounting-specific nav. Clean for consulting-za. |
| 0.56 | **PARTIAL** | "Project" and "Task" unchanged ✓. "Client" override applied in sidebar ✓. NO occurrence of "Matter", "Attorney", "Fee Note", "Court" in the sidebar. BUT the overrides for "Time Entry → Time Log" and "Rate Card → Billing Rates" are NOT reflected in the sidebar/settings labels (still "Time Tracking" and "Rates & Currency"). Same root-cause as GAP-C-03. |
| 0.57 | **PARTIAL** | `/trust-accounting` → "Something went wrong / An unexpected error occurred while loading this page" (generic error boundary, NOT a clean 404/redirect). `/court-calendar` → "Module Not Available" (graceful). `/conflict-check` → "Module Not Available" (graceful). One out of three throws an unexpected error. See **GAP-C-08**. |

## Phase I — Billing (Tier Removal)

| ID  | Result | Evidence |
|-----|--------|----------|
| 0.58 | PASS | Settings > Billing loaded. |
| 0.59 | PASS | Flat UI: "Trial" + "Manual" pill badges, "Managed Account" card: "Your account is managed by your administrator." No tier picker, no upgrade button, no Starter/Pro plan badge. ✓ |
| 0.60 | PASS | Screenshot saved → `day-00-billing-flat.png`. |

## Screenshots captured

- `qa_cycle/checkpoint-results/day-00-dashboard-wow-moment.png` — post-registration Zolani dashboard (0.18).
- `qa_cycle/checkpoint-results/day-00-settings-general.png` — Settings > General (vertical profile picker + currency + brand color + logo placeholder).
- `qa_cycle/checkpoint-results/day-00-billing-flat.png` — Settings > Billing flat UI (0.60).

## GAP-C-01 Verdict

**VERIFIED.** Marketing → `consulting-za` resolution is working. All eight expected consulting-za packs are installed in the tenant schema at provisioning. `vertical_profile = 'consulting-za'`, `terminology_namespace = 'en-ZA-consulting'`, `enabled_modules = '[]'`, `default_currency = 'ZAR'`.

## New Gaps Opened

| GAP_ID | Checkpoint | Severity | Summary |
|--------|------------|----------|---------|
| GAP-C-02 | 0.13 / 0.23 | MED | Keycloak invite link is single-use AND the login-screen "already authenticated as different user" error on first click leaves the user with no path forward (token is consumed even on the error branch). Product should auto-logout the existing KC session, prompt the user to switch, or offer a "sign in as a different user" CTA. Currently users are locked out on their very first invite click if they happen to have any other KC session active. |
| GAP-C-03 | 0.16 / 0.28 / 0.56 | MED | `en-ZA-consulting` terminology override only applies to "Customer → Client" in the sidebar. "Time Entry → Time Log" and "Rate Card → Billing Rates" overrides are NOT applied to sidebar/settings labels (settings page still shows "Time Tracking" and "Rates & Currency"). Expected "Time Logs" and "Billing Rates" per scenario 0.16 and 0.28. |
| GAP-C-04 | 0.17 | MED | `TeamUtilizationWidget` renders "Unable to load utilization data." for a brand-new tenant with no time logged yet — indistinguishable from an actual error. Should render a graceful empty state ("No utilization data yet. Log time to see billable %.") for zero-data tenants, and reserve the "Unable to load" copy for genuine fetch failures. Backend server action is returning 500 for the utilization endpoint against a tenant with no members having billing rates / no time entries — call path should tolerate that. |
| GAP-C-05 | 0.29 | MED | `rate-pack-consulting-za` seeds 8 billing_rates at the expected ZAR amounts, but the Settings > Rates & Currency UI table has no "Role" column — it's a member-scoped table. The scenario's "Creative Director — R1,800/hr" row-level assertion cannot be satisfied because the current data model stores role-keyed rate templates as unassigned rows. Either: surface a "Role Defaults" tab with role-labeled rows, or align the scenario with the current UI model. |
| GAP-C-06 | 0.31 | MED | `cost_rates` table is empty for the newly-provisioned tenant. Rate pack seeder populated `billing_rates` but did not seed cost rate defaults from `rate-pack-consulting-za.json`'s `costRates` section. |
| GAP-C-07 | 0.51 | LOW | Settings > Automations UI shows "Automation Rule Builder is not enabled" even though the consulting-za profile installs the 6 `automation-consulting-za` rules into the DB. The viewing/listing page shouldn't be gated — only rule creation should be. Currently a user on the consulting-za profile can't see (or disable) the rules that the pack installed on their behalf. |
| GAP-C-08 | 0.57 | LOW | `/org/{slug}/trust-accounting` route throws a generic "Something went wrong" error boundary instead of showing "Module Not Available" like `/court-calendar` and `/conflict-check` do. Inconsistent progressive-disclosure handling. |

## Blockers / Stop Reason

**No blockers.** Day 0 proceeded through all phases. Carry-forward for Day 1+:
- Bob needs to be assigned Account Manager role (currently just Admin); scenario step 0.30 (role assignment) is NOT EXECUTED because the UI doesn't expose role-scoped rate assignment — see GAP-C-05.
- Three users (Zolani Owner / Bob Admin / Carol Member) are KC org members + backend members.
- Vertical profile `consulting-za` confirmed active, 8 packs installed, terminology override partially applied.
- Brand color `#F97316` saved.

Day 1 — 1.1 (Bob logs in as Account Director, creates BrightCup Coffee Roasters client) can proceed in the next dispatch.
