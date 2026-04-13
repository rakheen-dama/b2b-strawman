# QA Lifecycle: Accounting-ZA 90-Day Demo Readiness (Keycloak Mode) — v2

**Vertical profile**: `accounting-za`
**Story**: "Thornton & Associates" — Johannesburg accounting firm
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md`

**Predecessor**: `qa/testplan/48-lifecycle-script.md` (v1, retained for historical reference). This v2 refreshes the plan with:
- **Tier removal**: all "Upgrade to Pro" / plan picker / tier gate steps deleted (flat subscription model)
- **Field promotion checks**: explicit verification of promoted customer/project/task/invoice slugs
- **Progressive disclosure checks**: confirm no legal-specific modules leak into the accounting sidebar
- Alignment with the shared master-doc structure so all three verticals run identically

---

## Actors

| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner / Senior Partner | Thandi Thornton | `thandi@thornton-test.local` | `SecureP@ss1` |
| Admin / Manager | Bob Ndlovu | `bob@thornton-test.local` | `SecureP@ss2` |
| Member / Junior Accountant | Carol Mokoena | `carol@thornton-test.local` | `SecureP@ss3` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

## Clients & engagements onboarded

| Client | Type | Engagement | Purpose in the story |
|---|---|---|---|
| Sipho Dlamini | INDIVIDUAL (sole trader) | Tax Return 2025/26 | Simplest client, tax return flow, promoted individual slugs |
| Kgosi Holdings (Pty) Ltd | COMPANY | Monthly Bookkeeping + Year-End Pack | Entity client, VAT number, company registration, recurring work |
| Moroka Family Trust | TRUST | Annual Trust Financial Statements | Trust variant custom fields, annual engagement |

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Dashboard with accounting terminology (Engagements, Clients) + Thornton brand colour + sidebar WITHOUT any legal modules
2. **Day 4** — New Client dialog for a Pty Ltd company showing all promoted fields inline (vat_number, company_registration_number, entity_type, financial_year_end, registered_address)
3. **Day 6** — Engagement created from Year-End Pack template with pre-populated task list
4. **Day 34** — Profitability dashboard with 3 active engagements, ZAR revenue/cost/margin
5. **Day 48** — Invoice PDF with Thornton letterhead + VAT breakdown
6. **Day 87** — Automation rule firing notification (engagement 80% budget → notify owner)

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition:

- [ ] **0.A** Confirm no tenant schema named `tenant_thornton*` exists (drop if present)
- [ ] **0.B** Delete any Keycloak users with `@thornton-test.local` emails from the `docteams` realm

---

## Day 0 — Org onboarding (Keycloak flow)

### Phase A: Access request & OTP verification

**Actor**: Thandi Thornton (unauthenticated)

- [ ] **0.1** Navigate to `http://localhost:3000` → landing page loads
- [ ] **0.2** Click **"Request Access"** → `/request-access`
- [ ] **0.3** Fill form:
  - Email: `thandi@thornton-test.local`
  - Full Name: **Thandi Thornton**
  - Organization: **Thornton & Associates**
  - Country: **South Africa**
  - Industry: **Accounting**
- [ ] **0.4** Submit → OTP step appears
- [ ] **0.5** Open Mailpit → retrieve 6-digit OTP from the verification email
- [ ] **0.6** Enter OTP → Verify → success confirmation card

### Phase B: Platform admin approval

**Actor**: Platform Admin

- [ ] **0.7** Open fresh incognito window
- [ ] **0.8** Login as `padmin@docteams.local` / `password`
- [ ] **0.9** Navigate to `/platform-admin/access-requests`
- [ ] **0.10** Verify **Thornton & Associates** is in Pending tab with Industry = Accounting
- [ ] **0.11** Click **Approve** → confirm dialog → status → Approved
- [ ] **0.12** Verify vertical profile auto-assigned to `accounting-za`
- [ ] **0.13** Check Mailpit for Keycloak invitation email to `thandi@thornton-test.local`

### Phase C: Owner Keycloak registration

**Actor**: Thandi

- [ ] **0.14** Open Keycloak invitation link
- [ ] **0.15** Register: First Name = Thandi, Last Name = Thornton, Password = `SecureP@ss1`
- [ ] **0.16** Redirected to `/org/thornton-associates/dashboard` (or similar slug)
- [ ] **0.17** Verify sidebar shows org name **Thornton & Associates**
- [ ] **0.18** Verify lifecycle profile is active: sidebar shows **Engagements** (not "Projects") and **Clients** (not "Customers")
- [ ] **0.19** 📸 **Screenshot**: Dashboard with accounting terminology + nav + org name

### Phase D: Team invites

- [ ] **0.20** Navigate to **Settings > Team**
- [ ] **0.21** Verify Thandi is Owner. **Confirm no "Upgrade to Pro" / tier gate exists when inviting members** — this was the top staleness issue in v1 of this plan
- [ ] **0.22** Invite `bob@thornton-test.local` as Admin → send
- [ ] **0.23** Invite `carol@thornton-test.local` as Member → send
- [ ] **0.24** Bob and Carol each open their invite from Mailpit, register with `SecureP@ss2` / `SecureP@ss3`, redirect to app, then log out

**Day 0 Phase A-D Checkpoints**
- [ ] Org created via real access request → approval → Keycloak registration
- [ ] Three real Keycloak users exist
- [ ] **NO tier upgrade UI encountered anywhere in the onboarding / team invite flow**
- [ ] Vertical profile `accounting-za` active on the tenant

---

## Day 0 (cont.) — Firm settings & vertical pack verification

**Actor**: Thandi

### Phase E: General, rates, tax

- [ ] **0.25** Navigate to **Settings > General**
- [ ] **0.26** Verify default currency = **ZAR** (pre-seeded from accounting-za profile)
- [ ] **0.27** Set brand colour = **#1B5E20** (dark green) → Save → verify persists
- [ ] **0.28** Upload firm logo → verify preview

- [ ] **0.29** Navigate to **Settings > Rates**
- [ ] **0.30** Verify rate cards are **pre-seeded** from accounting-za profile defaults (Owner ~R1,500/hr, Admin ~R850/hr, Member ~R450/hr billing; ~R600/R400/R200 cost). If not pre-seeded, create manually to match
- [ ] **0.31** Navigate to **Settings > Tax** → verify VAT 15% is pre-seeded

### Phase F: Custom fields (field promotion check)

- [ ] **0.32** Navigate to **Settings > Custom Fields**
- [ ] **0.33** Verify `accounting-za-customer` field group is present with fields including: `vat_number`, `primary_contact_name`, `primary_contact_email`, `primary_contact_phone`, `acct_company_registration_number`, `acct_entity_type`, `financial_year_end`, `registered_address`
- [ ] **0.34** Verify `accounting-za-customer-trust` variant fields are present for trust clients
- [ ] **0.35** Verify `accounting-za-project` (engagement) field group is present with fields including: `engagement_type`, `reference_number`
- [ ] **0.36** **Field promotion checkpoint (customer)**: open blank **New Client** dialog → verify these promoted slugs render as native first-class inline inputs (not inside "Other Fields"):
  - `vat_number`, `primary_contact_name`, `primary_contact_email`, `primary_contact_phone`
  - `acct_company_registration_number`, `acct_entity_type`, `financial_year_end`, `registered_address`
  - Common: `tax_number`, `phone`, `address_line1`, `city`, `postal_code`, `country`
- [ ] **0.37** **Field promotion negative check**: verify these promoted slugs do NOT appear in the CustomFieldSection sidebar panel (no duplicates)
- [ ] **0.38** **Field promotion checkpoint (engagement)**: open blank **New Engagement** dialog → verify `engagement_type` and `reference_number` render as native inline inputs
- [ ] **0.39** Cancel both dialogs without saving

### Phase G: Templates & automations

- [ ] **0.40** Navigate to **Settings > Templates**
- [ ] **0.41** Verify accounting template pack is present with engagements like: "Year-End Pack", "Monthly Bookkeeping", "VAT Return (bi-monthly)", "Payroll (monthly)", "Annual Financial Statements", "Tax Return — Individual", "Tax Return — Trust"
- [ ] **0.42** Navigate to **Settings > Automations**
- [ ] **0.43** Verify `automation-accounting-za` rules are present (expect 4+ rules from the pack — e.g. engagement budget alerts, overdue checklist reminders, VAT deadline notifications)

### Phase H: Progressive disclosure check (critical)

- [ ] **0.44** Navigate to **Settings > Modules**
- [ ] **0.45** Verify accounting-za has **NO vertical-specific modules enabled** (empty or default set)
- [ ] **0.46** **Sidebar check (CRITICAL)**: confirm sidebar does **NOT** show any of the following legal-specific items:
  - Trust Accounting
  - Court Calendar
  - Conflict Check
  - Tariffs / LSSA Tariffs
- [ ] **0.47** **Cross-vertical terminology check**: confirm no sidebar label or breadcrumb shows "Matter", "Attorney", "Court", or other legal terminology
- [ ] **0.48** **Direct-URL leak check**: attempt to navigate to `/trust-accounting` and `/court-calendar` directly → verify clean 404 or redirect (not a broken page loading in accounting context)

### Phase I: Billing page (tier removal check — critical regression gate)

- [ ] **0.49** Navigate to **Settings > Billing**
- [ ] **0.50** **Tier removal checkpoint**: page shows **flat subscription model** with subscription status badge (TRIALING, ACTIVE, PAST_DUE, etc.), member count, monthly amount, PayFast self-service (if non-admin-managed), payment history
- [ ] **0.51** **Tier removal negative checks**: verify the following UI does **NOT** exist anywhere on the page:
  - Plan picker / tier selector
  - "Upgrade to Pro" / "Upgrade to Business" buttons
  - Plan tier badge ("Starter", "Pro", "Business")
  - Any member-limit gating message
- [ ] **0.52** 📸 **Screenshot**: Settings > Billing flat subscription UI

**Day 0 complete checkpoints**
- [ ] Currency ZAR, brand colour, logo set
- [ ] Rate cards configured (pre-seeded or manual)
- [ ] VAT 15% configured
- [ ] `accounting-za-customer`, `accounting-za-project` field packs loaded + trust variant
- [ ] **Field promotion verified**: customer + engagement promoted slugs inline, no duplicates
- [ ] Accounting templates + automation pack loaded
- [ ] **Progressive disclosure verified**: NO legal modules visible, no terminology leaks
- [ ] **Tier removal verified**: flat billing page, no upgrade UI anywhere

---

## Days 1–7 — First client: Sipho Dlamini (sole trader, tax return)

### Day 1 — Client creation

**Actor**: Bob (Admin)

- [ ] **1.1** Login as Bob
- [ ] **1.2** Navigate to **Clients** → **New Client**
- [ ] **1.3** Fill standard: Name = **Sipho Dlamini**, Email = `sipho@email.co.za`, Phone = +27-82-555-0201
- [ ] **1.4** Fill promoted fields (verify inline rendering): `acct_entity_type` = **SOLE_PROPRIETOR**, `tax_number` = "1234567890", `registered_address` = "12 Jorissen St, Braamfontein, 2017"
- [ ] **1.5** Save → client appears in list with status **PROSPECT**
- [ ] **1.6** Open detail → verify promoted fields render inline (not in sidebar)

### Day 2 — Onboarding

- [ ] **2.1** Transition Sipho to **ONBOARDING**
- [ ] **2.2** Complete onboarding checklist (accounting-za FICA/KYC variant: Certified ID, Proof of Address, SARS tax confirmation)
- [ ] **2.3** Verify customer transitions to ACTIVE

### Day 3 — Engagement from template

- [ ] **3.1** On Sipho detail, click **New Engagement**
- [ ] **3.2** Select template: **Tax Return — Individual**
- [ ] **3.3** Fill: Name = "Sipho Dlamini — 2025/26 Tax Return", `engagement_type` = **TAX_RETURN**, `reference_number` = "TR-2026-0001"
- [ ] **3.4** Save → engagement created with pre-populated tasks
- [ ] **3.5** Verify tasks present (IT3a collection, medical aid cert, rental schedule, SARS eFiling, review, submit)
- [ ] **3.6** Assign initial tasks to Carol

### Days 4–7 — Work on Sipho's tax return

- [ ] **4.1** Carol logs 1.0 hours: "Document collection — client portal"
- [ ] **5.1** Carol uploads IT3a PDFs as engagement documents
- [ ] **6.1** Bob reviews uploaded docs, adds comment: "Need proof of retirement annuity contribution" with @Sipho... (Sipho is not in-app; instead @Carol)
- [ ] **7.1** Carol logs 1.5 hours: "Drafted tax return in eFiling"

---

## Days 4–6 — Second client: Kgosi Holdings (Pty) Ltd

### Day 4 — 📸 Company client wow moment

**Actor**: Thandi (Owner handles the bigger client)

- [ ] **4.2** Navigate to Clients → New Client
- [ ] **4.3** Fill standard: Name = **Kgosi Holdings (Pty) Ltd**, Email = `finance@kgosi-holdings.co.za`, Phone = +27-11-555-0301
- [ ] **4.4** Fill promoted fields (all inline):
  - `acct_entity_type` = **COMPANY_PTY_LTD**
  - `acct_company_registration_number` = **2018/123456/07**
  - `vat_number` = **4123456789**
  - `financial_year_end` = **2026-02-28**
  - `registered_address` = "Suite 402, Kgosi Towers, 15 Biermann Ave, Rosebank, 2196"
  - `primary_contact_name` = "Lerato Khumalo"
  - `primary_contact_email` = `lerato@kgosi-holdings.co.za`
  - `primary_contact_phone` = "+27-82-555-0302"
- [ ] **4.5** 📸 **Screenshot**: New Client dialog with all promoted fields visible inline, scroll-visible at once, with accounting-za flavor
- [ ] **4.6** Save → client appears
- [ ] **4.7** Open detail → verify all promoted fields render inline on the detail page (not in sidebar)
- [ ] **4.8** Complete onboarding checklist → ACTIVE

### Day 5 — First engagement: Monthly Bookkeeping

- [ ] **5.2** On Kgosi detail, create **New Engagement**
- [ ] **5.3** Select template: **Monthly Bookkeeping**
- [ ] **5.4** Name = "Kgosi Holdings — Monthly Bookkeeping (Mar 2026)", engagement_type = **BOOKKEEPING**, reference = "BK-2026-03-0001"
- [ ] **5.5** Verify template tasks instantiated: bank recon, creditors recon, debtors recon, cashbook, journal entries, management pack

### Day 6 — 📸 Second engagement: Year-End Pack wow moment

- [ ] **6.2** On Kgosi, create **New Engagement**
- [ ] **6.3** Select template: **Year-End Pack**
- [ ] **6.4** Name = "Kgosi Holdings — FY2025/26 Year-End Pack"
- [ ] **6.5** Verify the engagement is created with a **fully populated task list** (expect 15+ tasks covering AFS prep, audit docs, tax packs, CIPC submission)
- [ ] **6.6** 📸 **Screenshot**: Year-End Pack engagement detail with instantiated task list visible

### Days 7–14 — Work on Kgosi engagements

- [ ] **7.2** Thandi logs 2.0 hours on year-end pack: "Initial planning meeting + scope confirmation"
- [ ] **8.1** Bob logs 3.0 hours on bookkeeping: "Mar bank recon + creditors"
- [ ] **9.1** Carol logs 2.0 hours on bookkeeping: "Debtors recon"
- [ ] **10.1** Bob uploads bank statements to bookkeeping engagement documents
- [ ] **12.1** Thandi comments on year-end pack with @Bob: "Need FS draft by day 30"
- [ ] **13.1** Bob acknowledges with comment + 1.0 hour log: "FS structure review"
- [ ] **14.1** Budget tab on year-end pack: set budget to 40 hours, R60,000 → verify burn tracking

---

## Days 15–21 — Third client: Moroka Family Trust (annual AFS)

### Day 15 — Trust client onboarding

**Actor**: Thandi

- [ ] **15.1** Create client: Name = **Moroka Family Trust**, `acct_entity_type` = **TRUST**
- [ ] **15.2** Verify trust-specific custom fields are surfaced (from `accounting-za-customer-trust` variant): trust registration, trustees, beneficiaries, `financial_year_end`
- [ ] **15.3** Fill trust fields
- [ ] **15.4** Complete onboarding → ACTIVE

### Day 16 — Trust AFS engagement

- [ ] **16.1** Create engagement from template: **Annual Trust Financial Statements**
- [ ] **16.2** Verify template-instantiated tasks: trust deed review, beneficial distributions, IT3T generation, AFS drafting
- [ ] **16.3** Assign tasks to Thandi and Bob

### Days 17–21 — Trust AFS work

- [ ] **17.1** Thandi logs 2.5 hours: "Trust deed review + prior year comparison"
- [ ] **19.1** Bob logs 4.0 hours: "Distribution schedule + IT3T prep"
- [ ] **21.1** Upload working papers

---

## Days 22–34 — Continued multi-engagement work & budgets

- [ ] **22.1** Add 3 more time entries across bookkeeping and year-end engagements
- [ ] **24.1** Mark bookkeeping engagement task "Mar bank recon" as DONE
- [ ] **26.1** Add comment on Moroka AFS with @Carol mention → Carol sees notification → responds
- [ ] **28.1** Upload additional working papers to year-end pack
- [ ] **30.1** **Automation trigger check**: engagement budget reaches ~70% on year-end pack → verify automation rule fires notification to Thandi (if `automation-accounting-za` pack includes a budget-alert rule)
- [ ] **32.1** Add 4th client (Pty Ltd, compressed happy-path onboarding) → create VAT Return engagement from template

### Day 34 — 📸 Profitability wow moment

- [ ] **34.1** As Thandi, navigate to **Reports > Profitability**
- [ ] **34.2** Verify all engagements listed with billed hours, cost, revenue, margin, ZAR formatting
- [ ] **34.3** Verify filter by client, by engagement type, by date range works
- [ ] **34.4** 📸 **Screenshot**: Profitability dashboard with 4 engagements + margins

---

## Days 36–60 — First invoices & payment flows

### Day 36 — First invoice (Kgosi bookkeeping)

- [ ] **36.1** Navigate to Kgosi → Monthly Bookkeeping engagement → Billing tab
- [ ] **36.2** Create invoice from unbilled time entries
- [ ] **36.3** Verify line items show date, member, description, hours, rate, amount
- [ ] **36.4** Verify VAT 15% calculation correct
- [ ] **36.5** **Field promotion check (invoice)**: verify promoted invoice slugs render inline on the create dialog: `purchase_order_number`, `tax_type`, `billing_period_start`, `billing_period_end`
- [ ] **36.6** Save as DRAFT → verify invoice in list

### Day 38 — Approve & send

- [ ] **38.1** Approve draft → status SENT → generate PDF
- [ ] **38.2** Verify PDF has Thornton letterhead + brand colour + VAT breakdown + banking details

### Day 45 — Second invoice (Sipho tax return — fixed fee)

- [ ] **45.1** Create fixed-fee invoice for Sipho's tax return engagement (one-off line item R2,500)
- [ ] **45.2** Approve + send

### Day 48 — 📸 Invoice PDF wow moment

- [ ] **48.1** Open the Kgosi bookkeeping invoice PDF
- [ ] **48.2** Verify: Thornton letterhead, green brand accent, VAT breakdown, total, banking details
- [ ] **48.3** 📸 **Screenshot**: Invoice PDF preview

### Days 50–60 — Payment & reconciliation

- [ ] **50.1** Record payment received on Kgosi invoice (full amount, EFT)
- [ ] **50.2** Verify status → PAID, audit event recorded
- [ ] **55.1** Record payment received on Sipho tax return invoice
- [ ] **58.1** Second bookkeeping invoice cycle for April on Kgosi (days 15–30 work)

---

## Days 61–75 — Recurring work & portal

- [ ] **61.1** Create third bookkeeping invoice (May cycle)
- [ ] **65.1** **Portal**: generate magic link for Kgosi Holdings primary contact → verify email via Mailpit → open in incognito → portal loads with Kgosi's engagements, invoices, documents
- [ ] **65.2** Verify portal respects accounting-za terminology (shows "Engagements" not "Matters")
- [ ] **68.1** Run VAT Return engagement workflow on 4th client (created day 32) → work through template tasks
- [ ] **72.1** Mark Sipho tax return engagement as COMPLETED

---

## Days 76–90 — Reports, close-out, final sweep

### Day 80 — Reports & utilization

- [ ] **80.1** Navigate to **Reports** / Company Dashboard
- [ ] **80.2** Verify utilization dashboard shows billable % per team member across 90 days
- [ ] **80.3** Export a report to CSV → verify download
- [ ] **80.4** Navigate to **My Work** (as Bob) → verify Bob's assigned tasks + time summary + upcoming deadlines render

### Day 85 — Audit log sweep

- [ ] **85.1** Navigate to Audit Log
- [ ] **85.2** Filter by actor = Thandi, entity = Engagement → verify expected events
- [ ] **85.3** Filter by action = CREATE_INVOICE → verify all invoices generated over 90 days

### Day 87 — 📸 Automation wow moment

- [ ] **87.1** Force an automation trigger (e.g. push another engagement budget past 80% by logging enough time)
- [ ] **87.2** Verify notification appears in Thandi's notification bell
- [ ] **87.3** 📸 **Screenshot**: Notification triggered by automation rule showing engagement budget alert

### Day 90 — Final regression sweep

- [ ] **90.1** **Terminology sweep**: verify no "Matter", "Attorney", "Conflict", or other legal terminology anywhere in sidebar, breadcrumbs, dialogs, settings pages, or email subjects
- [ ] **90.2** **Field promotion sweep**: reopen New Client, New Engagement, New Invoice dialogs → confirm promoted slugs still inline, no regression into sidebar
- [ ] **90.3** **Progressive disclosure sweep**: confirm sidebar still has NO legal modules (Trust Accounting, Court Calendar, Conflict Check, Tariffs)
- [ ] **90.4** **Tier removal sweep**: reopen Settings > Billing, team invite flow, any member limit surface → confirm no tier UI anywhere
- [ ] **90.5** **Console errors**: devtools open, walk every nav item → zero errors
- [ ] **90.6** **Mailpit sweep**: no bounced or failed emails

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked
- [ ] **E.2** All 6 📸 wow moments captured without visual regression
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report (`qa/gap-reports/demo-readiness-{DATE}/accounting-za.md`)
- [ ] **E.4** **Tier removal** verified on 3+ screens: Settings > Billing, team invite flow, member count surface
- [ ] **E.5** **Field promotion** verified on Client (8+ slugs), Engagement (2 slugs), Invoice (4 slugs) dialogs
- [ ] **E.6** **Progressive disclosure** verified — zero legal modules visible, zero legal terminology leaks
- [ ] **E.7** Keycloak onboarding end-to-end (no mock IDP)
- [ ] **E.8** Accounting-za template pack loaded (at least 4 engagement templates exercised)
- [ ] **E.9** Automation pack `automation-accounting-za` loaded and at least one rule verified firing
- [ ] **E.10** 90 days of audit events recorded
- [ ] **E.11** Cycle completed on one clean pass
- [ ] **E.12** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] Every fix PR merged during this cycle satisfied the same four gates before merging (not just the final run)

**If any checkpoint fails**: log to `qa/gap-reports/demo-readiness-{YYYY-MM-DD}/accounting-za.md` per master doc format, let `/qa-cycle-kc` dispatch a fix, re-run. **Fix PRs that do not pass the test suite gate (E.12) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.

---

## Refresh notes vs v1 (`48-lifecycle-script.md`)

Changes from the v1 plan (for reviewers comparing the two):

1. **Removed all tier upgrade steps** — v1 had "Click Upgrade to Pro → confirm → verify plan shows Pro" (old step 0.22). Deleted. Added a new explicit tier-removal checkpoint instead.
2. **Added field promotion checks** — v1 only verified custom fields existed in the settings page. v2 also verifies they render inline on create dialogs and do NOT duplicate in the sidebar CustomFieldSection.
3. **Added progressive disclosure checks** — v1 did not verify that legal modules are hidden. v2 has an explicit sidebar check + direct-URL leak check.
4. **Promoted invoice slugs added** — v1 predates invoice field promotion. v2 checks the four invoice-level promoted slugs on the create-invoice dialog.
5. **Aligned structure** with `qa/testplan/demo-readiness-keycloak-master.md` so legal / accounting / agency plans all follow the same rhythm.
