# QA Lifecycle: Consulting/Agency 90-Day Demo Readiness (Keycloak Mode)

**Vertical profile**: `consulting-za`
**Story**: "Zolani Creative" — Johannesburg digital/marketing agency
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md`

**Context**: This plan exercises the `consulting-za` vertical profile end-to-end. As of Phase 66 (Epics 480–484), `consulting-za` is a content-rich profile shipping field packs (`consulting-za-customer`, `consulting-za-project`), a 5-template project pack (`consulting-za-project-templates`), an 8-role rate pack (`rate-pack-consulting-za`), a 6-rule automation pack (`automation-consulting-za`), an 8-clause clause pack (`consulting-za-clauses`), a 10-question creative-brief request pack (`consulting-za-creative-brief`), 4 document templates (Creative Brief, Statement of Work, Engagement Letter, Monthly Retainer Report), a `TeamUtilizationWidget` dashboard surface, and an `en-ZA-consulting` terminology override (Customer → Client, Time Entry → Time Log, Rate Card → Billing Rates).

The Zolani Creative narrative tells a Johannesburg digital agency story across 90 days; every checkpoint asserts the corresponding `consulting-za` content actually surfaces — no manual workarounds, no two-tier failure handling.

---

## Actors

| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner / Founder | Zolani Dube | `zolani@zolani-test.local` | `SecureP@ss1` |
| Admin / Account Director | Bob Ndlovu | `bob@zolani-test.local` | `SecureP@ss2` |
| Member / Designer | Carol Mokoena | `carol@zolani-test.local` | `SecureP@ss3` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

## Clients & projects onboarded

| Client | Type | Project / Work | Templates exercised | Purpose in the story |
|---|---|---|---|---|
| BrightCup Coffee Roasters | COMPANY | Brand refresh + website redesign | Website Design & Build, Brand Identity | Happy-path projects, fixed-fee + T&M mix |
| Ubuntu Startup (Pty) Ltd | COMPANY | Monthly marketing retainer + content engine | Social Media Management Retainer, Content Marketing Retainer | Retainer primitive + retainer report use case |
| Masakhane Foundation | NGO | Annual report design + fundraising campaign | SEO Campaign | NGO project with budget caps + creative brief intake |

> Five projects across three clients ensure all five `consulting-za` project templates are exercised at least once.

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Dashboard with `TeamUtilizationWidget` visible, Zolani brand colour applied, `en-ZA-consulting` terminology active (Clients, Time Logs, Billing Rates) and clean sidebar (no legal/accounting leakage)
2. **Day 5** — Project detail page for BrightCup Website Build, with `campaign_type = WEBSITE_BUILD` rendered inline + budget configured
3. **Day 14** — Creative Brief request sent: 10-question intake form rendered with 3 required + 2 file-upload questions
4. **Day 30** — Monthly Retainer Report PDF preview for Ubuntu Startup, all four `retainer.*` variables resolved
5. **Day 60** — Statement of Work PDF for Ubuntu Apr cycle with the three required agency clauses pre-included
6. **Day 75** — `TeamUtilizationWidget` 4-week sparkline rendered on dashboard, CTA navigates to `/org/zolani-creative/resources/utilization`

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition:

- [ ] **0.A** Confirm no tenant schema named `tenant_zolani*` exists (drop if present)
- [ ] **0.B** Delete any Keycloak users with `@zolani-test.local` emails from the `docteams` realm

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
  - Industry: **Marketing / Creative Services** (or **Consulting** if no marketing-specific option) — the `consulting-za` profile is selected by either path
- [ ] **0.4** Submit → OTP step appears
- [ ] **0.5** Retrieve 6-digit OTP from Mailpit → enter → Verify → success

### Phase B: Platform admin approval

**Actor**: Platform Admin

- [ ] **0.6** Login as `padmin@docteams.local` / `password` in fresh incognito
- [ ] **0.7** Navigate to `/platform-admin/access-requests`
- [ ] **0.8** Verify **Zolani Creative** appears in Pending
- [ ] **0.9** Click **Approve** → confirm dialog → status → Approved
- [ ] **0.10** Verify vertical profile auto-assigned to **`consulting-za`** (industry → profile mapping resolves to `consulting-za` for Marketing / Creative Services and Consulting industries)
- [ ] **0.11** Check Mailpit for Keycloak invitation email

### Phase C: Owner Keycloak registration

**Actor**: Zolani

- [ ] **0.12** Open invitation link
- [ ] **0.13** Register: First Name = Zolani, Last Name = Dube, Password = `SecureP@ss1`
- [ ] **0.14** Redirected to `/org/zolani-creative/dashboard`
- [ ] **0.15** Verify sidebar shows org name **Zolani Creative**
- [ ] **0.16** Verify **`en-ZA-consulting` terminology** is active: sidebar shows **Projects**, **Tasks**, **Clients** (not "Customers"), **Time Logs** (not "Time Entries"), **Billing Rates** (not "Rate Cards")
- [ ] **0.17** Verify **`TeamUtilizationWidget`** is rendered on the dashboard with `data-testid="team-utilization-widget"` (initially shows "Loading…" or zero-state because no time logged yet)
- [ ] **0.18** 📸 **Screenshot**: Dashboard with `TeamUtilizationWidget` + agency brand + `en-ZA-consulting` terminology + clean sidebar

### Phase D: Team invites

- [ ] **0.19** Navigate to **Settings > Team**
- [ ] **0.20** Verify Zolani is Owner. **Confirm no tier upgrade UI**
- [ ] **0.21** Invite `bob@zolani-test.local` as Admin → send
- [ ] **0.22** Invite `carol@zolani-test.local` as Member → send
- [ ] **0.23** Bob and Carol accept invites via Mailpit, register with passwords 2/3, redirect to app, log out

**Day 0 Phase A-D Checkpoints**
- [ ] Org created via real Keycloak flow
- [ ] Three users registered
- [ ] No tier UI encountered
- [ ] Vertical profile **`consulting-za`** active
- [ ] `TeamUtilizationWidget` rendered
- [ ] Terminology overrides resolved (Clients, Time Logs, Billing Rates)

---

## Day 0 (cont.) — Firm settings & vertical pack verification

**Actor**: Zolani

### Phase E: General, rates, tax

- [ ] **0.24** Navigate to **Settings > General**
- [ ] **0.25** Verify default currency is **ZAR** (set by `consulting-za.json` profile)
- [ ] **0.26** Set brand colour = **#F97316** (Zolani orange) → Save → verify persists
- [ ] **0.27** Upload firm logo

- [ ] **0.28** Navigate to **Settings > Billing Rates** (label resolved by `en-ZA-consulting` override)
- [ ] **0.29** Verify the `rate-pack-consulting-za` 8 default roles are seeded with the expected ZAR rates:
  - Creative Director — R1,800/hr
  - Strategist — R1,600/hr
  - Art Director — R1,400/hr
  - Account Manager — R1,200/hr
  - Senior Designer / Developer — R1,100/hr
  - Copywriter — R950/hr
  - Designer / Developer — R850/hr
  - Producer / Junior — R600/hr
- [ ] **0.30** Assign team members to roles: Zolani → Creative Director, Bob → Account Manager, Carol → Designer / Developer
- [ ] **0.31** Confirm cost rates (org-side) carry the matching `rate-pack-consulting-za` cost defaults

- [ ] **0.32** Navigate to **Settings > Tax**
- [ ] **0.33** Verify **VAT 15%** is pre-seeded as the default tax (from `taxDefaults` in `consulting-za.json`)

### Phase F: Custom fields (consulting-za field packs)

- [ ] **0.34** Navigate to **Settings > Custom Fields**
- [ ] **0.35** Verify the **Customer field group** `consulting_za_client` is present and `autoApply: true`, with all five slugs visible:
  - `industry` (DROPDOWN: RETAIL / FINANCIAL_SERVICES / TECH / HEALTHCARE / MANUFACTURING / PROFESSIONAL_SERVICES / HOSPITALITY / PUBLIC_SECTOR / NONPROFIT / OTHER)
  - `company_size` (DROPDOWN: SOLO / SMALL / MEDIUM / LARGE / ENTERPRISE)
  - `primary_stakeholder` (TEXT)
  - `msa_signed` (BOOLEAN)
  - `msa_start_date` (DATE, conditional on `msa_signed == true`)
- [ ] **0.36** Verify the **Project field group** `consulting_za_engagement` is present and `autoApply: true`, with all five slugs visible:
  - `campaign_type` (DROPDOWN, **required**: WEBSITE_BUILD / BRAND_IDENTITY / SOCIAL_MEDIA_RETAINER / SEO_CAMPAIGN / CONTENT_MARKETING / PAID_MEDIA / DESIGN_SPRINT / STRATEGY_CONSULTING / OTHER)
  - `channel` (DROPDOWN)
  - `deliverable_type` (DROPDOWN)
  - `retainer_tier` (DROPDOWN, conditional on `campaign_type IN [SOCIAL_MEDIA_RETAINER, CONTENT_MARKETING]`)
  - `creative_brief_url` (URL)
- [ ] **0.37** **Field promotion check (common slugs only)**: open blank **New Client** dialog → verify common promoted slugs render inline: `address_line1`, `city`, `postal_code`, `country`, `tax_number`, `phone`, `primary_contact_name`, `primary_contact_email`, `primary_contact_phone`. Verify the consulting-za-customer pack slugs (`industry`, `company_size`, `primary_stakeholder`, `msa_signed`) render in the CustomFieldSection panel; `msa_start_date` is hidden until `msa_signed` is toggled true.
- [ ] **0.38** Verify those same common slugs are NOT duplicated in the CustomFieldSection sidebar
- [ ] **0.39** Open blank **New Project** dialog → verify `reference_number` and `priority` render inline (promoted project slugs available to all profiles), and verify the consulting-za-project pack slugs render: `campaign_type` (required), `channel`, `deliverable_type`, `creative_brief_url`. `retainer_tier` is hidden until a retainer `campaign_type` is selected.
- [ ] **0.40** Cancel both dialogs

### Phase G: Templates & automations

- [ ] **0.41** Navigate to **Settings > Templates > Project Templates**
- [ ] **0.42** Verify the 5 `consulting-za-project-templates` are pre-seeded:
  - **Website Design & Build** — 120 hrs / R120,000 — `matterType: WEBSITE_BUILD`
  - **Social Media Management Retainer** — 40 hrs/month — `matterType: SOCIAL_MEDIA_RETAINER`, `retainer_tier: GROWTH`
  - **Brand Identity** — 80 hrs / R110,000 — `matterType: BRAND_IDENTITY`
  - **SEO Campaign** — 60 hrs / R65,000 — `matterType: SEO_CAMPAIGN`
  - **Content Marketing Retainer** — 25 hrs/month — `matterType: CONTENT_MARKETING`, `retainer_tier: STARTER`
- [ ] **0.43** Verify each template carries a pre-populated task list (6–9 tasks) with `assigneeRole`, `billable`, `estimatedHours`
- [ ] **0.44** Navigate to **Settings > Templates > Document Templates**
- [ ] **0.45** Verify all 4 document templates are pre-seeded by `templateKey`: `creative-brief`, `statement-of-work`, `engagement-letter`, `monthly-retainer-report`
- [ ] **0.46** Navigate to **Settings > Templates > Clause Library**
- [ ] **0.47** Verify all 8 `consulting-za-clauses` slugs are pre-seeded: `consulting-ip-ownership`, `consulting-revision-rounds`, `consulting-kill-fee`, `consulting-nda-mutual`, `consulting-payment-terms`, `consulting-change-requests`, `consulting-third-party-costs`, `consulting-termination`
- [ ] **0.48** Verify `statement-of-work` template has the expected `clauseSlugs` (7 slugs) and `requiredSlugs` (`consulting-payment-terms`, `consulting-ip-ownership`, `consulting-change-requests`); `engagement-letter` has its own 5 + 3 association
- [ ] **0.49** Navigate to **Settings > Templates > Request Packs**
- [ ] **0.50** Verify `consulting-za-creative-brief` is pre-seeded with all 10 questions (3 required: Brand & Company Description, Target Audience, Core Business Goals; 2 file-upload: Known Constraints or Brand Guidelines, Existing Assets or Content)
- [ ] **0.51** Navigate to **Settings > Automations**
- [ ] **0.52** Verify all 6 `automation-consulting-za` rules are pre-seeded by slug: `consulting-za-budget-80`, `consulting-za-budget-exceeded`, `consulting-za-retainer-closing`, `consulting-za-task-blocked-7d`, `consulting-za-unbilled-time-30d`, `consulting-za-proposal-followup-5d`. Confirm rule names render correctly (e.g., "Project Budget Alert (80%)", "Retainer Period Closing (3 days)").

### Phase H: Progressive disclosure check (critical)

- [ ] **0.53** Navigate to **Settings > Modules**
- [ ] **0.54** Verify `consulting-za` has **no enabled modules** (`enabledModules: []` in profile manifest) — only the global product surface should render
- [ ] **0.55** **Sidebar check (CRITICAL)**: confirm sidebar does **NOT** show:
  - Trust Accounting, Court Calendar, Conflict Check, Tariffs (legal-only)
  - SARS submissions, GL accounts, accounting-za-specific surfaces
- [ ] **0.56** **Terminology check**: confirm no "Matter", "Attorney", "Engagement Letter" (as a nav item — fine as a document template), "Fee Note", "Court", or other vertical-specific terminology in sidebar/breadcrumbs/settings. Confirm "Project" remains "Project" and "Task" remains "Task" (consulting-za only overrides Customer, Time Entry, Rate Card)
- [ ] **0.57** **Direct-URL leak check**: attempt `/trust-accounting`, `/court-calendar`, `/conflict-check` → verify clean 404/redirect

### Phase I: Billing page (tier removal check)

- [ ] **0.58** Navigate to **Settings > Billing**
- [ ] **0.59** **Tier removal checkpoint**: flat subscription states only, no tier picker, no upgrade button, no plan badge
- [ ] **0.60** 📸 **Screenshot**: Flat billing page for agency tenant

**Day 0 complete checkpoints**
- [ ] Currency ZAR + brand + logo set
- [ ] 8 `rate-pack-consulting-za` roles seeded with ZAR rates
- [ ] VAT 15% pre-configured
- [ ] Customer field pack `consulting_za_client` (5 slugs) and Project field pack `consulting_za_engagement` (5 slugs) verified
- [ ] 5 project templates pre-seeded with `campaign_type` + `retainer_tier` defaults
- [ ] 4 document templates, 8 clauses, 10-question request pack, 6 automation rules verified
- [ ] **Progressive disclosure verified**: zero legal/accounting module leakage
- [ ] **Tier removal verified**: flat billing UI
- [ ] **Terminology verified**: Clients, Time Logs, Billing Rates active; Project / Task unchanged

---

## Days 1–7 — First client: BrightCup Coffee Roasters (project work)

### Day 1 — Client creation

**Actor**: Bob (Account Director)

- [ ] **1.1** Login as Bob
- [ ] **1.2** Navigate to **Clients** (label resolved by `en-ZA-consulting` terminology override)
- [ ] **1.3** Click **New Client** → fill:
  - Name: **BrightCup Coffee Roasters**
  - Email: `finance@brightcup.co.za`
  - Phone: +27-21-555-0401
  - `primary_contact_name`: "Naledi Sithole"
  - `primary_contact_email`: `naledi@brightcup.co.za`
  - `tax_number`: "9876543210"
  - `address_line1`: "45 Kloof Nek Rd, Tamboerskloof, 8001"
  - `city`: "Cape Town"
  - `industry`: **RETAIL** (consulting-za-customer field pack)
  - `company_size`: **SMALL**
  - `primary_stakeholder`: "Naledi Sithole"
  - `msa_signed`: **true**
  - `msa_start_date`: **2026-01-15** (only visible because `msa_signed == true`)
- [ ] **1.4** Verify common promoted fields render inline (no duplication in CustomFieldSection); verify `consulting_za_client` slugs render in the side panel; verify `msa_start_date` only appeared after `msa_signed` was toggled
- [ ] **1.5** Save → client created, status PROSPECT
- [ ] **1.6** Complete onboarding checklist → ACTIVE

### Day 2 — First project (Website Design & Build)

- [ ] **2.1** On BrightCup detail, click **New Project**
- [ ] **2.2** Select template **Website Design & Build**
- [ ] **2.3** Fill: Name = "BrightCup — Website Build", `reference_number` = "BC-2026-001", `priority` = HIGH
- [ ] **2.4** Verify `campaign_type` is **automatically set to `WEBSITE_BUILD`** by the template (and is read-only or pre-filled)
- [ ] **2.5** Verify `retainer_tier` field is **NOT visible** (because `campaign_type` is not a retainer type)
- [ ] **2.6** Save → project created with the template's pre-populated tasks
- [ ] **2.7** Assign tasks across the team per template's `assigneeRole` defaults; nudge Carol onto Design tasks and Bob onto Discovery / Account-Management tasks

### Days 3–4 — Initial work

- [ ] **3.1** Bob logs 2.0 hours (Time Log) on Discovery: "Client kickoff + brand audit"
- [ ] **3.2** Bob uploads brand audit doc to project documents
- [ ] **4.1** Carol logs 3.0 hours (Time Log) on Wireframes: "Homepage + 3 key pages"
- [ ] **4.2** Bob comments on project: "Nice wireframes — let's review with client Friday" with @Carol mention
- [ ] **4.3** Carol sees notification, replies

### Day 5 — 📸 Project wow moment + budget + Brand Identity project

- [ ] **5.1** Navigate to project detail → Budget tab
- [ ] **5.2** Set budget: **120 hours, R120,000 cap** (matches Website Design & Build template default)
- [ ] **5.3** Verify budget status indicator shows "on track" (~5.0 hours consumed)
- [ ] **5.4** Verify project detail page shows promoted fields inline at top, the `campaign_type = WEBSITE_BUILD` value is visible, and tabs (Tasks, Time Logs, Documents, Comments, Activity, Budget, Billing) all load
- [ ] **5.5** 📸 **Screenshot**: Project detail with budget tab + `campaign_type` field + en-ZA-consulting terminology
- [ ] **5.6** From BrightCup detail, click **New Project** again → select template **Brand Identity** → Name = "BrightCup — Brand Identity Refresh", `reference_number` = "BC-2026-002"
- [ ] **5.7** Verify `campaign_type` auto-set to `BRAND_IDENTITY`; budget defaults to **80 hrs / R110,000**

### Days 6–7 — More work

- [ ] **6.1** Carol logs 4.0 hours on Design (Website Build)
- [ ] **7.1** Carol uploads Figma preview PNG to BrightCup Website project documents

---

## Days 8–20 — Second client: Ubuntu Startup (retainers)

### Day 8 — Retainer client

**Actor**: Zolani

- [ ] **8.1** Create client: Name = **Ubuntu Startup (Pty) Ltd**, `primary_contact_name` = "Sipho Khumalo", `tax_number`, `address`, `industry` = **TECH**, `company_size` = **SMALL**, `msa_signed` = **true**, `msa_start_date` = **2026-02-01**
- [ ] **8.2** Complete onboarding → ACTIVE

### Day 9 — Retainer projects (Social Media + Content Marketing templates)

- [ ] **9.1** Create project from template **Social Media Management Retainer**: Name = "Ubuntu Startup — Social Media (Mar 2026)", `reference_number` = "UBUNTU-SM-2026-03"
- [ ] **9.2** Verify `campaign_type` auto-set to `SOCIAL_MEDIA_RETAINER`
- [ ] **9.3** Verify `retainer_tier` field becomes visible and is pre-set to **GROWTH** by the template default
- [ ] **9.4** Set budget: **40 hours, R48,000** (template default), set custom field `retainer.periodStart = 2026-03-01`, `retainer.periodEnd = 2026-03-31` (the retainer report context will resolve these on Day 30)
- [ ] **9.5** Assign template tasks: Social Media Management, Email Campaign, Analytics Review, Content Writing, Strategy Call

### Day 10 — Content Marketing retainer (template #5)

- [ ] **10.1** Create project from template **Content Marketing Retainer**: Name = "Ubuntu Startup — Content Engine (Mar 2026)", `reference_number` = "UBUNTU-CM-2026-03"
- [ ] **10.2** Verify `campaign_type` auto-set to `CONTENT_MARKETING`, `retainer_tier` defaulted to **STARTER**
- [ ] **10.3** Set budget: **25 hours, R26,250**, retainer period 2026-03-01 → 2026-03-31

### Days 11–14 — Retainer work + Day 14 Creative Brief request

- [ ] **11.1** Bob logs 2.0 hrs (Time Log) on Ubuntu Social Media: "Monthly strategy call with Sipho"
- [ ] **12.1** Carol logs 3.0 hrs on Ubuntu Social Media: "Social media posts for March week 2"
- [ ] **13.1** Carol logs 2.5 hrs on Ubuntu Content Engine: "Email campaign design + copy"
- [ ] **14.1** 📸 **Creative Brief request wow moment** — On the BrightCup Brand Identity Refresh project (or any active creative project), navigate to project detail → **Requests** tab → click **New Request** → select request pack **`consulting-za-creative-brief`** → assert:
  - All **10 questions** appear in the order shipped by the pack
  - Question titles match: Brand & Company Description, Target Audience, Core Business Goals, Competitive Landscape & Reference Brands, Must-Have Deliverables, Known Constraints or Brand Guidelines, Existing Assets or Content, Tone of Voice Preferences, Key Stakeholders & Decision-Making Process, Launch & Milestone Dates
  - **3 questions are marked required** (Brand & Company Description, Target Audience, Core Business Goals)
  - **2 questions are FILE_UPLOAD type** with PDF / PNG / JPG / SVG / ZIP allowed (Known Constraints, Existing Assets)
- [ ] **14.2** Send the request to the client primary contact → verify Mailpit delivery
- [ ] **14.3** 📸 **Screenshot**: Creative Brief request form (10 questions, required + file-upload markers visible)

### Days 15–20 — Continue retainer work

- [ ] **16.1** Bob logs 1.5 hrs on Ubuntu Social Media: "Analytics dashboard review"
- [ ] **18.1** Carol logs 2.0 hrs on Ubuntu Content Engine: "Blog post writing"
- [ ] **20.1** Verify Ubuntu Social Media retainer burn: ~6.5 hours consumed (~16% of 40-hr cap); Content Engine burn ~4.5 hours (~18% of 25-hr cap)

---

## Days 21–35 — Third client: Masakhane Foundation (NGO + SEO Campaign template)

### Day 21 — NGO onboarding

- [ ] **21.1** Create client: Name = **Masakhane Foundation**, `primary_contact_name` = "Thandiwe Nkosi", `tax_number` (NPO number), `address`, `industry` = **NONPROFIT**, `company_size` = **SMALL**, `msa_signed` = **false** (verify `msa_start_date` field stays hidden)
- [ ] **21.2** Onboarding → ACTIVE

### Day 22 — SEO Campaign project (template #4)

- [ ] **22.1** Create project from template **SEO Campaign**: Name = "Masakhane — Annual Report SEO + Fundraising", `reference_number` = "MASA-SEO-2026-01"
- [ ] **22.2** Verify `campaign_type` auto-set to `SEO_CAMPAIGN`, `retainer_tier` field NOT visible
- [ ] **22.3** Set budget: **60 hours, R65,000** (template default)
- [ ] **22.4** Assign template tasks across team

### Days 23–30 — NGO work + Day 30 Monthly Retainer Report

- [ ] **23.1** Bob logs 3.0 hrs: "Content gathering meeting with Masakhane team"
- [ ] **25.1** Carol logs 4.0 hrs on SEO content brief
- [ ] **27.1** Bob uploads content briefs to Masakhane project documents
- [ ] **28.1** Carol logs 3.5 hrs on SEO assets
- [ ] **29.1** Log additional time on Ubuntu Social Media + Content Engine to bring March cycle close to budget cap (push Social Media to ~32 / 40 hrs to set up Day 45 budget-80 trigger)
- [ ] **30.1** 📸 **Monthly Retainer Report wow moment** — Close the Ubuntu Social Media March cycle:
  - Navigate to Ubuntu Social Media (Mar 2026) project → **Generate Document** → select template **`monthly-retainer-report`**
  - Render the PDF preview
  - Assert all four `retainer.*` context variables resolve (no literal `{{...}}` placeholders in the rendered output): `retainer.periodStart` (2026-03-01), `retainer.periodEnd` (2026-03-31), `retainer.hourBank` (40), `retainer.hoursUsed` (~32)
  - Assert `org.name` = "Zolani Creative", `org.documentFooterText` resolves, `customer.name` = "Ubuntu Startup (Pty) Ltd", `project.name` = "Ubuntu Startup — Social Media (Mar 2026)"
- [ ] **30.2** 📸 **Screenshot**: Monthly Retainer Report PDF preview with all variables resolved

### Days 31–35 — Profitability check

- [ ] **32.1** Verify NGO budget burn: ~10.5 hours (~17% of 60-hr cap)
- [ ] **34.1** **Profitability sweep**:
  - Navigate to **Reports > Profitability**
  - Verify all 5 active projects listed across the 3 clients with ZAR revenue / cost / margin
  - Verify utilization per team member renders (this will be re-verified on Day 75 against the `TeamUtilizationWidget`)

---

## Days 36–60 — Invoices, automation rules, retainer renewal, SOW

### Day 36 — First invoice (BrightCup Website)

- [ ] **36.1** Navigate to BrightCup Website project → Billing tab
- [ ] **36.2** Create invoice from unbilled time entries (days 3–7)
- [ ] **36.3** Verify invoice promoted fields (`purchase_order_number`, `tax_type`, `billing_period_start`, `billing_period_end`) render inline on the invoice dialog
- [ ] **36.4** Save → Approve → Send
- [ ] **36.5** Generate PDF → verify Zolani letterhead + brand colour + VAT breakdown

### Day 40 — Ubuntu retainer cycle close (Mar)

- [ ] **40.1** Bring March cycle burn to ~38 / 40 hrs by adding any remaining time logs
- [ ] **40.2** Generate retainer invoice for the March cycle from unbilled time
- [ ] **40.3** Approve → Send → record receipt of payment

### Day 42 — Ubuntu Apr retainer cycle (clone)

- [ ] **42.1** Create new project from template **Social Media Management Retainer**: Name = "Ubuntu Startup — Social Media (Apr 2026)", `reference_number` = "UBUNTU-SM-2026-04"
- [ ] **42.2** Set budget 40 hrs / R48,000, retainer period 2026-04-01 → 2026-04-30
- [ ] **42.3** Verify the new project also auto-applies the `consulting_za_engagement` field group with `campaign_type = SOCIAL_MEDIA_RETAINER`, `retainer_tier = GROWTH`

### Day 45 — Automation rule trigger sweep 📸

- [ ] **45.1** Force the Ubuntu Apr Social Media project budget to 80%+ (log enough time to cross the 32 / 40 threshold)
- [ ] **45.2** Within minutes, verify a notification fires from rule **`consulting-za-budget-80`** ("Project Budget Alert (80%)") with title containing the substituted `{{project.name}}` ("Ubuntu Startup — Social Media (Apr 2026)") and `{{customer.name}}` ("Ubuntu Startup (Pty) Ltd")
- [ ] **45.3** Set `retainer.periodEnd` on the Apr project to a date 2 days in the future, then verify rule **`consulting-za-retainer-closing`** ("Retainer Period Closing (3 days)") fires a notification — title should resolve `{{project.name}}` and `{{org.name}}` ("Zolani Creative")
- [ ] **45.4** Locate any time entry with `workDate` older than 30 days and still unbilled (e.g., one of the BrightCup early-March entries that did not make it onto the Day 36 invoice) — verify rule **`consulting-za-unbilled-time-30d`** ("Unbilled Time Older Than 30 Days") fires a notification (this rule uses the `FIELD_DATE_APPROACHING` fallback path per ADR-244 — it may rely on a periodic sweep rather than instantaneous fire; allow up to one sweep cycle)
- [ ] **45.5** Confirm rules `consulting-za-budget-exceeded`, `consulting-za-task-blocked-7d`, `consulting-za-proposal-followup-5d` exist in the configuration but are NOT expected to fire in this scripted run (no 100% breach, no 7-day-stale tasks, no PROPOSAL_SENT events emitted) — this is documented expected behavior, not a script bug
- [ ] **45.6** 📸 **Screenshot**: Notifications panel showing the three triggered automation rule notifications side by side

### Day 48 — Masakhane interim invoice

- [ ] **48.1** Create invoice for Masakhane work to date
- [ ] **48.2** Approve → send → PDF

### Days 53–58 — Continue work + payment recording

- [ ] **53.1** More time logged on all 5 projects
- [ ] **55.1** Record payment on BrightCup Website invoice → PAID
- [ ] **58.1** Record payment on Ubuntu March retainer invoice → PAID

### Day 60 — 📸 Statement of Work wow moment (clauses pre-included)

- [ ] **60.1** Navigate to Ubuntu Apr Social Media project → **Generate Document** → select template **`statement-of-work`**
- [ ] **60.2** Render the SOW preview / PDF
- [ ] **60.3** Assert the SOW pre-includes the three required clauses from `requiredSlugs`:
  - **`consulting-payment-terms`** ("Payment Terms (Agency)")
  - **`consulting-ip-ownership`** ("Intellectual Property Ownership")
  - **`consulting-change-requests`** ("Change Requests")
- [ ] **60.4** Confirm the four additional optional clauses from `clauseSlugs` are also pre-attached (toggleable): `consulting-revision-rounds`, `consulting-kill-fee`, `consulting-third-party-costs`, `consulting-termination`
- [ ] **60.5** 📸 **Screenshot**: SOW PDF with the three required agency clauses pre-included

---

## Days 61–80 — Engagement Letter, portal, utilization widget

- [ ] **61.1** From BrightCup Brand Identity project → **Generate Document** → select **`engagement-letter`** → assert pre-included clauses include the engagement-letter `requiredSlugs`: `consulting-payment-terms`, `consulting-termination`, `consulting-nda-mutual`
- [ ] **65.1** **Portal**: generate magic link for BrightCup primary contact (Naledi) → verify Mailpit → open in incognito → portal loads with BrightCup's projects, invoices, documents visible
- [ ] **65.2** Verify portal uses the `en-ZA-consulting` terminology (Clients, Time Logs, Billing Rates) consistently
- [ ] **70.1** Create third Ubuntu retainer cycle (May) by cloning the template again → repeat the `campaign_type` + `retainer_tier` auto-fill assertion
- [ ] **72.1** Mark a chunk of Masakhane SEO tasks complete as the project nears wrap-up

### Day 75 — 📸 TeamUtilizationWidget wow moment

- [ ] **75.1** Login as Zolani → navigate to `/org/zolani-creative/dashboard`
- [ ] **75.2** Locate the `TeamUtilizationWidget` via `data-testid="team-utilization-widget"`
- [ ] **75.3** Verify the Card header reads **"Team Billable Utilization"**
- [ ] **75.4** Verify a `KpiCard` is rendered with:
  - `value` showing the current week's billable %
  - `changePercent` showing the delta (current − prior week, in pp)
  - `trend` rendered as a sparkline with **4 datapoints** (current week + 3 prior — populated by the 75 days of logged time)
- [ ] **75.5** Verify the CTA link "Team utilization →" navigates to `/org/zolani-creative/resources/utilization`
- [ ] **75.6** 📸 **Screenshot**: Dashboard with `TeamUtilizationWidget` rendering 4-week sparkline + KPI delta + CTA

### Day 80 — Reports & utilization

- [ ] **80.1** Navigate to **Reports / Company Dashboard**
- [ ] **80.2** Verify utilization dashboard: billable % per team member, per week, across 90 days
- [ ] **80.3** Verify cross-project health/status dashboard (Phase 9 ops dashboards)
- [ ] **80.4** Export report to CSV → verify download

---

## Days 81–90 — Close-out & final sweep

### Day 85 — Audit log sweep

- [ ] **85.1** Navigate to Audit Log
- [ ] **85.2** Filter by actor, project, action type → verify all filters work on agency data, including events emitted by automation rule triggers on Day 45

### Day 88 — Cross-project utilization

- [ ] **88.1** Navigate to **My Work** / team utilization / cross-project Time Log summary
- [ ] **88.2** Verify all 3 team members show utilization % over the 90 days
- [ ] **88.3** Verify Time Log labels (not "Time Entries") consistent throughout

### Day 90 — Final regression sweep

- [ ] **90.1** **Terminology sweep**: walk every nav item, every dialog, every settings page → confirm `en-ZA-consulting` overrides hold (Clients, Time Logs, Billing Rates) and **Project / Task remain unchanged**; zero legal/accounting terminology leaks
- [ ] **90.2** **Field promotion sweep**: reopen New Client, New Project, New Invoice → confirm common promoted slugs still inline + `consulting_za_client` / `consulting_za_engagement` packs render in CustomFieldSection; conditional visibility (`msa_start_date`, `retainer_tier`) still respects its rules
- [ ] **90.3** **Pack sweep**: re-verify rate pack (8 roles), template pack (5 templates), document templates (4), clauses (8), automation rules (6), request pack (10 questions) all still resolve
- [ ] **90.4** **Progressive disclosure sweep**: confirm zero legal/accounting modules in sidebar
- [ ] **90.5** **Tier removal sweep**: Settings > Billing, team invite flow → confirm flat, no tier UI
- [ ] **90.6** **Console errors**: devtools, walk every page → zero JS errors

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked
- [ ] **E.2** All 6 📸 wow moments captured without visual regression
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report
- [ ] **E.4** **Tier removal** verified on 3+ screens
- [ ] **E.5** **Field promotion** verified for common slugs on Client, Project, Invoice dialogs; `consulting_za_client` and `consulting_za_engagement` packs render in CustomFieldSection
- [ ] **E.6** **Progressive disclosure** verified — zero cross-vertical leaks
- [ ] **E.7** Keycloak onboarding end-to-end (no mock IDP)
- [ ] **E.8** Three clients + 5 projects (one per `consulting-za-project-templates` template) handled across 90 days
- [ ] **E.9** All 6 `automation-consulting-za` rules verified (3 fired in scripted scenarios, 3 documented as expected non-fire under this script)
- [ ] **E.10** Cycle completed on one clean pass
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] Every fix PR merged during this cycle satisfied the same four gates before merging (not just the final run)

**If any checkpoint fails**: log to `qa/gap-reports/demo-readiness-{YYYY-MM-DD}/consulting-agency.md` per master doc format, let `/qa-cycle-kc` dispatch a fix.
**Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.

---

## Notes for the agency lifecycle

This plan asserts the `consulting-za` vertical profile end-to-end against a real Keycloak tenant, exercising every pack shipped in Phase 66 (Epics 480–484): the 8-role rate pack, 5 project templates (each driving a distinct `campaign_type` and, where applicable, `retainer_tier`), 4 document templates (Creative Brief, SOW, Engagement Letter, Monthly Retainer Report), 8-clause clause library with template associations, 10-question Creative Brief request pack, 6 automation rules, the `en-ZA-consulting` terminology overrides (Customer → Client, Time Entry → Time Log, Rate Card → Billing Rates) and the `TeamUtilizationWidget` dashboard surface. Where a primitive previously gapped (the retainer cycle pattern, custom field packs, automation rules) the script now asserts the productised behaviour.
