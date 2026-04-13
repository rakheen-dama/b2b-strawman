# QA Lifecycle: Consulting/Agency 90-Day Demo Readiness (Keycloak Mode)

**Vertical profile**: `consulting-generic`
**Story**: "Zolani Creative" — Johannesburg digital/marketing agency
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md`

**Context**: There is no dedicated `agency-generic` vertical profile today. The `consulting-generic` profile is a near-empty shell (no template packs, no custom field groups, no automation pack, no rate defaults). This plan uses `consulting-generic` as the base and tells an agency story on top of it. Wherever the story needs something the profile lacks, the step logs a gap to the **Agency gap list** in `.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md` and provides a manual workaround so the plan still runs end-to-end.

This is intentional. The agency plan is the most likely of the three to surface genuine missing-primitive gaps, which is exactly the signal we want for a future vertical-profile ideation pass.

---

## Actors

| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner / Founder | Zolani Dube | `zolani@zolani-test.local` | `SecureP@ss1` |
| Admin / Account Director | Bob Ndlovu | `bob@zolani-test.local` | `SecureP@ss2` |
| Member / Designer | Carol Mokoena | `carol@zolani-test.local` | `SecureP@ss3` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

## Clients & projects onboarded

| Client | Type | Project / Work | Purpose in the story |
|---|---|---|---|
| BrightCup Coffee Roasters | COMPANY | Brand refresh + website redesign (one-off project) | Happy-path project, fixed-fee + T&M mix |
| Ubuntu Startup (Pty) Ltd | COMPANY | Monthly marketing retainer (20 hrs/month) | Retainer use case (exposes retainer-primitive gap) |
| Masakhane Foundation | NGO | Annual report design + fundraising campaign | Project with multiple creative deliverables, budget caps |

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Dashboard with generic terminology (Projects, Customers) + Zolani brand colour + clean sidebar (no legal or accounting-za leakage)
2. **Day 5** — Project detail page with custom fields for an agency use-case, budget configured
3. **Day 34** — Profitability dashboard with 3 active projects + utilization metrics per team member
4. **Day 52** — Invoice PDF for the BrightCup project with Zolani letterhead
5. **Day 76** — Retainer renewal moment — Ubuntu Startup project budget exhausted, new cycle created
6. **Day 88** — Cross-project time and utilization summary for the team

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition:

- [ ] **0.A** Confirm no tenant schema named `tenant_zolani*` exists (drop if present)
- [ ] **0.B** Delete any Keycloak users with `@zolani-test.local` emails from the `docteams` realm
- [ ] **0.C** **IMPORTANT**: Because `consulting-generic.json` is an empty profile, be prepared to flag gaps liberally in this cycle — the point of the agency pass is to surface what the profile lacks.

---

## Day 0 — Org onboarding (Keycloak flow)

### Phase A: Access request & OTP verification

**Actor**: Zolani Dube (unauthenticated)

- [ ] **0.1** Navigate to `http://localhost:3000` → landing page loads
- [ ] **0.2** Click **"Request Access"** → `/request-access`
- [ ] **0.3** Fill form:
  - Email: `zolani@zolani-test.local`
  - Full Name: **Zolani Dube**
  - Organization: **Zolani Creative**
  - Country: **South Africa**
  - Industry: **Marketing / Creative Services** (or nearest option; if no agency/marketing option exists, select **Other** or **Consulting** → **log MEDIUM gap**: industry dropdown lacks agency/creative option)
- [ ] **0.4** Submit → OTP step appears
- [ ] **0.5** Retrieve 6-digit OTP from Mailpit → enter → Verify → success

### Phase B: Platform admin approval

**Actor**: Platform Admin

- [ ] **0.6** Login as `padmin@docteams.local` / `password` in fresh incognito
- [ ] **0.7** Navigate to `/platform-admin/access-requests`
- [ ] **0.8** Verify **Zolani Creative** appears in Pending
- [ ] **0.9** Click **Approve** → confirm dialog → status → Approved
- [ ] **0.10** Verify vertical profile auto-assigned. Expected: `consulting-generic` (fallback for agency/marketing/other). If a different profile is assigned (e.g., profile assignment refuses to fall back to consulting-generic for "Marketing / Creative Services"), **log HIGH gap**: backend industry-to-profile mapping does not handle agency/marketing
- [ ] **0.11** Check Mailpit for Keycloak invitation email

### Phase C: Owner Keycloak registration

**Actor**: Zolani

- [ ] **0.12** Open invitation link
- [ ] **0.13** Register: First Name = Zolani, Last Name = Dube, Password = `SecureP@ss1`
- [ ] **0.14** Redirected to `/org/zolani-creative/dashboard`
- [ ] **0.15** Verify sidebar shows org name **Zolani Creative**
- [ ] **0.16** Verify **generic terminology** is active: sidebar shows **Projects** (not "Matters" or "Engagements") and **Customers** or **Clients** (consistent with consulting-generic default — note which label is used)
- [ ] **0.17** 📸 **Screenshot**: Dashboard with generic terminology + agency brand + clean sidebar

### Phase D: Team invites

- [ ] **0.18** Navigate to **Settings > Team**
- [ ] **0.19** Verify Zolani is Owner. **Confirm no tier upgrade UI**
- [ ] **0.20** Invite `bob@zolani-test.local` as Admin → send
- [ ] **0.21** Invite `carol@zolani-test.local` as Member → send
- [ ] **0.22** Bob and Carol accept invites via Mailpit, register with passwords 2/3, redirect to app, log out

**Day 0 Phase A-D Checkpoints**
- [ ] Org created via real Keycloak flow
- [ ] Three users registered
- [ ] No tier UI encountered
- [ ] Vertical profile `consulting-generic` (or fallback) active

---

## Day 0 (cont.) — Firm settings & vertical pack verification

**Actor**: Zolani

### Phase E: General, rates, tax

- [ ] **0.23** Navigate to **Settings > General**
- [ ] **0.24** Verify default currency. Expected: **ZAR** if SA-registered. If consulting-generic does not set ZAR by default, **log LOW gap**: profile has no currency default; set manually to ZAR
- [ ] **0.25** Set brand colour = **#F97316** (Zolani orange) → Save → verify persists
- [ ] **0.26** Upload firm logo

- [ ] **0.27** Navigate to **Settings > Rates**
- [ ] **0.28** **Expected gap**: `consulting-generic` has **no rate-card defaults**. Rate section will be empty. Create manually:
  - Zolani (Owner, strategist): **R1,800/hr** billing, **R800/hr** cost
  - Bob (Account Director): **R1,200/hr** billing, **R550/hr** cost
  - Carol (Designer): **R750/hr** billing, **R350/hr** cost
- [ ] **0.29** **Log MEDIUM gap**: `consulting-generic` profile should ship with sensible rate-card defaults or a prompt to seed them — new agency tenants start with an empty rate table

- [ ] **0.30** Navigate to **Settings > Tax**
- [ ] **0.31** Verify VAT 15% is present or create manually. If consulting-generic does not pre-seed VAT, **log LOW gap**

### Phase F: Custom fields (field promotion check — generic slugs only)

- [ ] **0.32** Navigate to **Settings > Custom Fields**
- [ ] **0.33** **Expected state**: `consulting-generic` has **no vertical-specific field group** — only the global common fields (address, contact, tax_number) should be available
- [ ] **0.34** **Log MEDIUM gap**: no agency-flavoured custom field pack (no campaign_type, channel, deliverable_type, creative_brief_url, brand_guidelines_link, analytics_dashboard_url)
- [ ] **0.35** **Field promotion check (common slugs only)**: open blank **New Customer** dialog → verify common promoted slugs render inline: `address_line1`, `city`, `postal_code`, `country`, `tax_number`, `phone`, `primary_contact_name`, `primary_contact_email`, `primary_contact_phone`
- [ ] **0.36** Verify those same slugs are NOT duplicated in the CustomFieldSection sidebar
- [ ] **0.37** Open blank **New Project** dialog → verify `reference_number` and `priority` render inline (promoted project slugs available to all profiles)
- [ ] **0.38** Cancel both dialogs

### Phase G: Templates & automations

- [ ] **0.39** Navigate to **Settings > Templates**
- [ ] **0.40** **Expected state**: **No templates pre-seeded** (consulting-generic has no template pack). **Log MEDIUM gap**: agencies expect at least stub templates (e.g., "Brand Project", "Website Project", "Monthly Retainer", "Campaign")
- [ ] **0.41** As a workaround, create one manual template: **"Website Redesign Project"** with tasks: Discovery, Wireframes, Design, Development, QA, Launch. Save.
- [ ] **0.42** Navigate to **Settings > Automations**
- [ ] **0.43** **Expected state**: **No automation rules pre-seeded**. **Log MEDIUM gap**: no automation pack for consulting-generic (agencies expect rules like "project 80% budget → notify owner", "task overdue > 3 days → notify assignee")

### Phase H: Progressive disclosure check (critical)

- [ ] **0.44** Navigate to **Settings > Modules**
- [ ] **0.45** Verify `consulting-generic` has **NO vertical-specific modules enabled**
- [ ] **0.46** **Sidebar check (CRITICAL — same as accounting)**: confirm sidebar does **NOT** show:
  - Trust Accounting, Court Calendar, Conflict Check, Tariffs (legal)
  - Any accounting-za-specific automation rule name showing in nav
- [ ] **0.47** **Terminology check**: confirm no "Matter", "Attorney", "Engagement", "Fee Note", "Court", or other vertical-specific terminology in sidebar/breadcrumbs/settings
- [ ] **0.48** **Direct-URL leak check**: attempt `/trust-accounting`, `/court-calendar`, `/conflict-check` → verify clean 404/redirect

### Phase I: Billing page (tier removal check)

- [ ] **0.49** Navigate to **Settings > Billing**
- [ ] **0.50** **Tier removal checkpoint**: flat subscription states only, no tier picker, no upgrade button, no plan badge
- [ ] **0.51** 📸 **Screenshot**: Flat billing page for agency tenant

**Day 0 complete checkpoints**
- [ ] Currency, brand, logo set (with manual workarounds for profile gaps)
- [ ] Rates manually created (log gap: no defaults)
- [ ] VAT 15% configured
- [ ] Common field promotion verified on Customer and Project dialogs
- [ ] At least one manual project template created (log gap: no pack)
- [ ] **Progressive disclosure verified**: zero legal/accounting module leakage
- [ ] **Tier removal verified**: flat billing UI
- [ ] **Gap list updated**: at least 4 profile-shape gaps logged (rate defaults, custom field pack, template pack, automation pack)

---

## Days 1–7 — First client: BrightCup Coffee Roasters (project work)

### Day 1 — Client creation

**Actor**: Bob (Account Director)

- [ ] **1.1** Login as Bob
- [ ] **1.2** Navigate to **Customers** (or whichever label is used — log terminology drift if inconsistent)
- [ ] **1.3** Click **New Customer** → fill:
  - Name: **BrightCup Coffee Roasters**
  - Email: `finance@brightcup.co.za`
  - Phone: +27-21-555-0401
  - `primary_contact_name`: "Naledi Sithole"
  - `primary_contact_email`: `naledi@brightcup.co.za`
  - `tax_number`: "9876543210"
  - `address_line1`: "45 Kloof Nek Rd, Tamboerskloof, 8001"
  - `city`: "Cape Town"
- [ ] **1.4** Verify all fields render inline (no promoted slug duplicated in sidebar)
- [ ] **1.5** Save → customer created, status PROSPECT
- [ ] **1.6** Complete onboarding checklist → ACTIVE

### Day 2 — First project

- [ ] **2.1** On BrightCup detail, click **New Project**
- [ ] **2.2** Select the manual template created on Day 0 ("Website Redesign Project")
- [ ] **2.3** Fill: Name = "BrightCup — Brand Refresh + Website Redesign", `reference_number` = "BC-2026-001", `priority` = HIGH
- [ ] **2.4** Save → project created with 6 template tasks
- [ ] **2.5** Assign tasks: Discovery → Bob, Wireframes & Design → Carol, Development → Carol, QA → Bob, Launch → Zolani

### Days 3–4 — Initial work

- [ ] **3.1** Bob logs 2.0 hours on Discovery: "Client kickoff + brand audit"
- [ ] **3.2** Bob uploads brand audit doc to project documents
- [ ] **4.1** Carol logs 3.0 hours on Wireframes: "Homepage + 3 key pages"
- [ ] **4.2** Bob comments on project: "Nice wireframes — let's review with client Friday" with @Carol mention
- [ ] **4.3** Carol sees notification, replies

### Day 5 — 📸 Project wow moment + budget

- [ ] **5.1** Navigate to project detail → Budget tab
- [ ] **5.2** Set budget: **40 hours, R40,000 cap** (hours + currency)
- [ ] **5.3** Verify budget status indicator shows "on track" (~5.0 hours consumed)
- [ ] **5.4** Verify project detail page shows promoted fields inline at top, tabs (Tasks, Time, Documents, Comments, Activity, Budget, Billing) all load
- [ ] **5.5** 📸 **Screenshot**: Project detail with budget tab + promoted fields + clean agency terminology

### Days 6–7 — More work

- [ ] **6.1** Carol logs 4.0 hours on Design
- [ ] **7.1** Carol uploads Figma preview PNG

---

## Days 8–20 — Second client: Ubuntu Startup (retainer)

### Day 8 — Retainer client

**Actor**: Zolani

- [ ] **8.1** Create customer: Name = **Ubuntu Startup (Pty) Ltd**, `primary_contact_name` = "Sipho Khumalo", `tax_number`, `address`
- [ ] **8.2** Complete onboarding → ACTIVE

### Day 9 — Retainer project (exposes retainer-primitive gap)

- [ ] **9.1** Create project: Name = "Ubuntu Startup — Monthly Marketing Retainer (Mar 2026)", `reference_number` = "UBUNTU-RET-2026-03"
- [ ] **9.2** Set budget: **20 hours, R24,000** for this cycle
- [ ] **9.3** **Log HIGH gap**: no retainer primitive exists in consulting-generic profile. Today's workaround is "one project per retainer cycle, manually cloned monthly". A real agency vertical needs: (a) retainer entity, (b) rollover behaviour for unused hours, (c) automatic cycle creation, (d) retainer consumption dashboard
- [ ] **9.4** Manually add custom text field on the project: "retainer_cycle" = "2026-03"
- [ ] **9.5** Assign recurring-style tasks: Social Media Management, Email Campaign, Analytics Review, Content Writing, Strategy Call

### Days 10–20 — Retainer work

- [ ] **10.1** Bob logs 2.0 hrs: "Monthly strategy call with Sipho"
- [ ] **12.1** Carol logs 3.0 hrs: "Social media posts for March week 2"
- [ ] **14.1** Carol logs 2.5 hrs: "Email campaign design + copy"
- [ ] **16.1** Bob logs 1.5 hrs: "Analytics dashboard review"
- [ ] **18.1** Carol logs 2.0 hrs: "Blog post writing"
- [ ] **20.1** Verify retainer budget burn: ~11 hours consumed (~55%)

---

## Days 21–35 — Third client: Masakhane Foundation (NGO annual report)

### Day 21 — NGO onboarding

- [ ] **21.1** Create customer: Name = **Masakhane Foundation**, `primary_contact_name` = "Thandiwe Nkosi", `tax_number` (NPO number), `address`
- [ ] **21.2** Onboarding → ACTIVE

### Day 22 — Annual report project

- [ ] **22.1** Create project: "Masakhane — 2025 Annual Report + Fundraising Campaign"
- [ ] **22.2** Set budget: **60 hours, R60,000**
- [ ] **22.3** Assign tasks: Content Gathering (Bob), Design (Carol), Copywriting (Bob), Print-Ready (Carol), Digital Campaign (Carol), Distribution (Bob)

### Days 23–35 — NGO work

- [ ] **23.1** Bob logs 3.0 hrs: "Content gathering meeting with Masakhane team"
- [ ] **25.1** Carol logs 4.0 hrs: "Report layout design"
- [ ] **27.1** Bob uploads content briefs to project documents
- [ ] **28.1** Carol logs 3.5 hrs: "Illustration + photo treatments"
- [ ] **30.1** Bob logs 2.0 hrs: "Copywriting — executive summary"
- [ ] **32.1** Verify NGO budget burn: ~12.5 hours (~21%)
- [ ] **34.1** 📸 **Profitability wow moment** (same as other plans):
  - Navigate to **Reports > Profitability**
  - Verify all 3 projects listed with ZAR revenue/cost/margin
  - Verify utilization per team member renders
  - 📸 **Screenshot**: Profitability dashboard with 3 agency projects + margins + utilization

---

## Days 36–60 — Invoices, retainer renewal, more work

### Day 36 — First invoice (BrightCup)

- [ ] **36.1** Navigate to BrightCup project → Billing tab
- [ ] **36.2** Create invoice from unbilled time entries (days 3–7)
- [ ] **36.3** Verify invoice promoted fields (`purchase_order_number`, `tax_type`, `billing_period_start`, `billing_period_end`) render inline on the invoice dialog
- [ ] **36.4** Save → Approve → Send
- [ ] **36.5** Generate PDF → verify Zolani letterhead + brand colour + VAT breakdown

### Day 40 — Ubuntu retainer cycle close

- [ ] **40.1** Log a few more time entries to bring retainer burn close to 20 hours cap
- [ ] **40.2** Generate retainer invoice from this cycle (fixed-fee or T&M — pick whichever matches the story)
- [ ] **40.3** **Log MEDIUM gap**: retainer invoice flow is indistinguishable from a project invoice — no retainer-specific billing artifact, no "hours consumed / hours remaining" summary on the invoice

### Day 42 — Retainer cycle 2 (April)

- [ ] **42.1** Create new project: "Ubuntu Startup — Monthly Marketing Retainer (Apr 2026)" (manual clone of March project)
- [ ] **42.2** Set budget: 20 hours, R24,000
- [ ] **42.3** **Log HIGH gap (second occurrence)**: having to manually re-create a "retainer project" every month is high-friction. A real agency vertical needs recurring project generation

### Day 48 — Masakhane interim invoice

- [ ] **48.1** Create invoice for work to date on NGO project
- [ ] **48.2** Approve → send → PDF

### Day 52 — 📸 Invoice PDF wow moment

- [ ] **52.1** Open BrightCup invoice PDF
- [ ] **52.2** Verify Zolani letterhead, orange brand accent, VAT, banking
- [ ] **52.3** 📸 **Screenshot**: Invoice PDF with Zolani branding

### Days 53–60 — Continue work + payment recording

- [ ] **53.1** More time logged on all 3 projects
- [ ] **55.1** Record payment on BrightCup invoice → PAID
- [ ] **58.1** Record payment on Ubuntu March retainer invoice → PAID
- [ ] **60.1** Continue Masakhane design work, upload design iterations

---

## Days 61–80 — Retainer cycle 3, portal, reports

- [ ] **61.1** Log time in April retainer cycle (Ubuntu)
- [ ] **65.1** **Portal**: generate magic link for BrightCup primary contact → verify Mailpit → open in incognito → portal loads with BrightCup's projects, invoices, documents visible
- [ ] **65.2** Verify portal uses generic terminology (no vertical-specific leaks)
- [ ] **70.1** Create third retainer cycle for Ubuntu (May)
- [ ] **72.1** Near-final milestone on Masakhane project — mark content tasks complete
- [ ] **76.1** 📸 **Retainer renewal wow moment**:
  - Ubuntu March cycle PAID, April cycle in progress
  - Navigate to Ubuntu Startup customer detail
  - Show three retainer "projects" stacked (Mar, Apr, May)
  - 📸 **Screenshot**: Customer detail showing retainer cycles as stacked projects (reveals the gap as well as the workaround)

### Day 80 — Reports & utilization

- [ ] **80.1** Navigate to **Reports / Company Dashboard**
- [ ] **80.2** Verify utilization dashboard: billable % per team member, per week, across 90 days
- [ ] **80.3** Verify cross-project health/status dashboard (Phase 9 ops dashboards)
- [ ] **80.4** Export report to CSV → verify download

---

## Days 81–90 — Close-out & final sweep

### Day 85 — Audit log sweep

- [ ] **85.1** Navigate to Audit Log
- [ ] **85.2** Filter by actor, project, action type → verify all filters work on agency data

### Day 88 — 📸 Utilization wow moment

- [ ] **88.1** Navigate to My Work / team utilization / cross-project time summary (whichever surface shows team-wide billable%)
- [ ] **88.2** Verify all 3 team members show utilization % over the 90 days
- [ ] **88.3** 📸 **Screenshot**: Team utilization summary

### Day 90 — Final regression sweep

- [ ] **90.1** **Terminology sweep**: walk every nav item, every dialog, every settings page → zero vertical-specific terminology leaks (no "Matter", "Engagement", "Fee Note", "Attorney", "Court", etc.)
- [ ] **90.2** **Field promotion sweep**: reopen New Customer, New Project, New Invoice → confirm common promoted slugs still inline, no regressions
- [ ] **90.3** **Progressive disclosure sweep**: confirm zero legal modules in sidebar, zero accounting-za-specific automations leaking
- [ ] **90.4** **Tier removal sweep**: Settings > Billing, team invite flow → confirm flat, no tier UI
- [ ] **90.5** **Console errors**: devtools, walk every page → zero JS errors
- [ ] **90.6** **Gap list review**: open `.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md` and verify every gap flagged during this run has been logged to the Agency gap list section

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked
- [ ] **E.2** All 6 📸 wow moments captured without visual regression
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report **for the product itself** (gaps in the `consulting-generic` profile content — missing packs — do NOT block demo-ready; they are logged to the Agency gap list as future phase input, not as product bugs)
- [ ] **E.4** **Tier removal** verified on 3+ screens
- [ ] **E.5** **Field promotion** verified for common slugs on Customer, Project, Invoice dialogs
- [ ] **E.6** **Progressive disclosure** verified — zero cross-vertical leaks
- [ ] **E.7** Keycloak onboarding end-to-end (no mock IDP)
- [ ] **E.8** Three customers + 5+ projects/retainer-cycles handled across 90 days
- [ ] **E.9** At least 4 gaps logged to the Agency gap list in the ideation log
- [ ] **E.10** Cycle completed on one clean pass (for product bugs — profile-content gaps excluded)
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] Every fix PR merged during this cycle satisfied the same four gates before merging (not just the final run)

**If any checkpoint fails due to a product bug**: log to `qa/gap-reports/demo-readiness-{YYYY-MM-DD}/consulting-agency.md` per master doc format, let `/qa-cycle-kc` dispatch a fix.
**If a checkpoint fails due to a profile-content gap** (no template pack, no automation pack, etc.): log to the Agency gap list in `.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md` and use the manual workaround to keep the cycle moving.
**Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.

---

## Why this plan has two-tier failure handling

Unlike the legal and accounting plans — where profile content is expected to be rich and any missing artifact is a bug — the agency plan runs on a deliberately empty profile (`consulting-generic`). Gaps in profile content are a **separate class of finding**: they inform whether the platform should grow a dedicated `agency-generic` (or `agency-za`) vertical profile later. They do not block the product from being demo-ready with a manual setup.

Product bugs (broken pages, missing buttons, terminology leaks, field promotion regressions, tier UI reappearing) remain blockers like in the other two plans.

This split is intentional. The agency plan's job is threefold:
1. Prove the platform runs cleanly for a generic-profile tenant
2. Surface every gap between `consulting-generic` and a real agency use case, for future phase planning
3. Give the founder a demo-able agency story today using manual setup workarounds
