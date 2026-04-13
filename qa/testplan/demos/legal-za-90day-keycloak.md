# QA Lifecycle: Legal-ZA 90-Day Demo Readiness (Keycloak Mode)

**Vertical profile**: `legal-za`
**Story**: "Mathebula & Partners" — Johannesburg litigation firm
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md`

**Supersedes**: `qa/testplan/qa-legal-lifecycle-test-plan.md` (mock-auth port 3001 version) and `qa/testplan/legal-onboarding-keycloak.md` (onboarding-only, Keycloak). Both retained for historical reference only.

---

## Actors

| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner / Senior Partner | Thandi Mathebula | `thandi@mathebula-test.local` | `SecureP@ss1` |
| Admin / Associate | Bob Ndlovu | `bob@mathebula-test.local` | `SecureP@ss2` |
| Member / Candidate Attorney | Carol Mokoena | `carol@mathebula-test.local` | `SecureP@ss3` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

## Clients & matters onboarded

| Client | Type | Matter | Purpose in the story |
|---|---|---|---|
| Sipho Dlamini | INDIVIDUAL | Litigation — General civil dispute | Simplest onboarding path, promoted fields on individual client |
| Moroka Family Trust | TRUST | Deceased Estate — Peter Moroka | Entity client, FICA beneficial-ownership flow, complex matter template |
| Lerato Mthembu | INDIVIDUAL | Litigation — Road Accident Fund claim | RAF-specific matter, court calendar entry, adverse-party registry |

## Demo wow moments (capture 📸 on clean pass)

1. **Day 0** — Firm dashboard with legal nav (Matters, Trust Accounting, Court Calendar, Conflict Check) + Mathebula brand colour applied
2. **Day 2** — Conflict check "CLEAR" result with the SA legal green confirmation state
3. **Day 5** — Matter detail page for a RAF claim, with promoted fields (matter_type, court_name, case_number) rendered inline + vertical terminology ("Matter" not "Project")
4. **Day 33** — Profitability page with three active matters, ZAR revenue vs cost, margin ribbon
5. **Day 47** — Fee note rendered as PDF preview with Mathebula letterhead + LSSA tariff line items
6. **Day 88** — Audit event feed showing 90 days of activity across all three matters, filterable by actor and entity

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition, for this vertical specifically:

- [ ] **0.A** Confirm no tenant schema named `tenant_mathebula*` exists (drop if present)
- [ ] **0.B** Delete any Keycloak users with `@mathebula-test.local` emails from the `docteams` realm

---

## Day 0 — Org onboarding (Keycloak flow)

### Phase A: Access request & OTP verification

**Actor**: Thandi Mathebula (unauthenticated, public)

- [ ] **0.1** Navigate to `http://localhost:3000` → landing page loads, no "Sign In" errors in browser console
- [ ] **0.2** Click **"Get Started"** or **"Request Access"** → navigates to `/request-access`
- [ ] **0.3** Verify form fields visible: Email, Full Name, Organization, Country, Industry
- [ ] **0.4** Fill form:
  - Email: `thandi@mathebula-test.local`
  - Full Name: **Thandi Mathebula**
  - Organization: **Mathebula & Partners**
  - Country: **South Africa**
  - Industry: **Legal Services**
- [ ] **0.5** Click **Submit** → transitions to OTP verification step (same page, step 2)
- [ ] **0.6** Open Mailpit (`http://localhost:8025`) → verify OTP email arrived for `thandi@mathebula-test.local`. Subject line contains "verification" or similar
- [ ] **0.7** Copy 6-digit OTP from email → enter in verification form → click **Verify**
- [ ] **0.8** Success card appears: "Your request has been submitted for review"

### Phase B: Platform admin approval

**Actor**: Platform Admin (`padmin@docteams.local`)

- [ ] **0.9** Open fresh incognito window or clear cookies
- [ ] **0.10** Navigate to `http://localhost:3000/dashboard` → redirected to Keycloak login
- [ ] **0.11** Login as `padmin@docteams.local` / `password` → lands on platform admin home
- [ ] **0.12** Navigate to `/platform-admin/access-requests`
- [ ] **0.13** Verify **Mathebula & Partners** appears in the Pending tab with Industry = Legal Services, Country = South Africa
- [ ] **0.14** Click into the request → verify detail view shows all fields Thandi submitted
- [ ] **0.15** Click **Approve** → AlertDialog confirmation appears → click **Confirm**
- [ ] **0.16** Verify status changes to **Approved** with no provisioning error banner
- [ ] **0.17** Verify vertical profile auto-assigned to `legal-za` (displayed in approval result, or verifiable via `curl -s http://localhost:8443/api/orgs/mathebula-partners/profile` with padmin token)
- [ ] **0.18** Check Mailpit for Keycloak invitation email to `thandi@mathebula-test.local`. Subject contains "Invitation"

### Phase C: Owner Keycloak registration

**Actor**: Thandi (registering via Keycloak invite)

- [ ] **0.19** Open the Keycloak invitation link from the email
- [ ] **0.20** Keycloak registration page loads with organization = Mathebula & Partners pre-bound
- [ ] **0.21** Fill: First Name = **Thandi**, Last Name = **Mathebula**, Password = `SecureP@ss1`, Confirm Password = `SecureP@ss1`
- [ ] **0.22** Submit → redirected back to the app → lands on `/org/mathebula-partners/dashboard` (or similar slug)
- [ ] **0.23** Verify sidebar shows org name **Mathebula & Partners**, user avatar/name **Thandi Mathebula**
- [ ] **0.24** Verify lifecycle profile is active: sidebar shows **Matters** (not "Projects"), **Clients** (not "Customers")
- [ ] **0.25** 📸 **Screenshot**: Dashboard with legal terminology + nav + org name

### Phase D: Team invites

**Actor**: Thandi (Owner, logged in)

- [ ] **0.26** Navigate to **Settings > Team** (`/settings/team`)
- [ ] **0.27** Verify Thandi is listed as Owner. Confirm **no "Upgrade to Pro" gate** exists when inviting members
- [ ] **0.28** Click **Invite Member** → Email = `bob@mathebula-test.local`, Role = **Admin** → Send
- [ ] **0.29** Click **Invite Member** → Email = `carol@mathebula-test.local`, Role = **Member** → Send
- [ ] **0.30** Open Mailpit → verify two Keycloak invitation emails arrived

**Actor**: Bob Ndlovu (accepting invite)

- [ ] **0.31** Open Bob's invitation link
- [ ] **0.32** Register: First Name = **Bob**, Last Name = **Ndlovu**, Password = `SecureP@ss2`
- [ ] **0.33** Redirected to app → lands on Mathebula dashboard → logout

**Actor**: Carol Mokoena (accepting invite)

- [ ] **0.34** Open Carol's invitation link
- [ ] **0.35** Register: First Name = **Carol**, Last Name = **Mokoena**, Password = `SecureP@ss3`
- [ ] **0.36** Redirected to app → lands on Mathebula dashboard → logout

**Day 0 Phase A-D Checkpoints**
- [ ] Org created via real access request → approval → Keycloak registration
- [ ] Three real Keycloak users (owner, admin, member) exist and can log in
- [ ] No "Upgrade to Pro" / plan picker / tier gate anywhere in onboarding or team invite flow
- [ ] Vertical profile `legal-za` is active on the tenant

---

## Day 0 (cont.) — Firm settings & vertical pack verification

**Actor**: Thandi (log back in)

### Phase E: General settings & branding

- [ ] **0.37** Navigate to **Settings > General**
- [ ] **0.38** Verify default currency is **ZAR** (pre-seeded from `legal-za` profile)
- [ ] **0.39** Set brand colour to **#1B3A4B** (dark teal) → Save → verify chip updates and persists on reload
- [ ] **0.40** Upload firm logo (any PNG under 500KB) → Save → verify preview renders

### Phase F: Rates & tax

- [ ] **0.41** Navigate to **Settings > Rates**
- [ ] **0.42** Verify rate cards section renders. If not pre-seeded, create org-level billing rates:
  - Thandi (Senior Partner): **R2,500/hr** (ZAR)
  - Bob (Associate): **R1,200/hr**
  - Carol (Candidate Attorney): **R550/hr**
- [ ] **0.43** Create cost rates:
  - Thandi: **R1,000/hr**
  - Bob: **R500/hr**
  - Carol: **R200/hr**
- [ ] **0.44** Navigate to **Settings > Tax**
- [ ] **0.45** Verify VAT 15% is pre-seeded; if not, create: Name = **VAT**, Rate = **15%**, Active = yes

### Phase G: Custom fields (field promotion check)

- [ ] **0.46** Navigate to **Settings > Custom Fields**
- [ ] **0.47** Verify `legal-za-customer` field group is present with fields including: `client_type`, `id_passport_number`, `physical_address`, `registration_number`, `beneficial_owners`
- [ ] **0.48** Verify `legal-za-project` (matter) field group is present with fields including: `matter_type`, `case_number`, `court_name`, `opposing_party`
- [ ] **0.49** **Field promotion checkpoint (customer)**: on a blank **New Client** dialog (open but don't submit), verify these promoted slugs render as native first-class inputs INLINE (not inside a generic "Other Fields" section): `client_type`, `physical_address`, `registration_number`, `tax_number`, `phone`, `primary_contact_name`, `primary_contact_email`
- [ ] **0.50** **Field promotion checkpoint (customer negative)**: verify these promoted slugs do NOT appear again inside the CustomFieldSection sidebar: same list as 0.49
- [ ] **0.51** **Field promotion checkpoint (matter)**: on a blank **New Matter** dialog, verify `matter_type` renders as a native inline form input (promoted project slug)
- [ ] **0.52** Cancel both dialogs without saving

### Phase H: Templates & automations

- [ ] **0.53** Navigate to **Settings > Templates**
- [ ] **0.54** Verify 4+ matter templates are listed from `legal-za` pack: "Litigation (Personal Injury / General)", "Deceased Estate Administration", "Collections (Debt Recovery)", "Commercial (Corporate & Contract)"
- [ ] **0.55** Navigate to **Settings > Automations**
- [ ] **0.56** Verify legal-za automation rules are listed (may be zero — legal profile has no automation pack today; if the list is empty, confirm no JS errors in console)

### Phase I: Progressive disclosure check

- [ ] **0.57** Navigate to **Settings > Modules**
- [ ] **0.58** Verify **all four legal modules are enabled**: `court_calendar`, `conflict_check`, `lssa_tariff`, `trust_accounting`
- [ ] **0.59** **Sidebar check**: verify sidebar nav shows items for: Trust Accounting, Court Calendar, Conflict Check, Tariffs
- [ ] **0.60** **Cross-vertical leak check**: verify sidebar does NOT show any items like "Engagements", "Year-End Packs", "Campaigns", or other non-legal vertical terminology

### Phase J: Trust account setup

- [ ] **0.61** Navigate to **Trust Accounting** (or `Settings > Trust Accounts`)
- [ ] **0.62** Create trust account: Name = **Mathebula & Partners Trust Account**, Type = **GENERAL**, Bank = **Standard Bank**
- [ ] **0.63** Set LPFF rate to **6.5%** (test rate)
- [ ] **0.64** Verify trust account dashboard renders with zero balance

### Phase K: Billing page (tier removal check)

- [ ] **0.65** Navigate to **Settings > Billing**
- [ ] **0.66** **Tier removal checkpoint**: page shows **flat subscription model** — no Starter/Pro/Business tier picker, no "Upgrade" button, no plan-gated CTA
- [ ] **0.67** Verify page shows: subscription status badge (TRIALING or ACTIVE), member count, monthly amount, and (if applicable) PayFast self-service UI or admin-managed notice
- [ ] **0.68** 📸 **Screenshot**: Settings > Billing showing flat subscription UI with no tier selector

**Day 0 complete checkpoints**
- [ ] Currency ZAR, brand colour + logo set
- [ ] Rate cards configured for 3 members (billing + cost)
- [ ] VAT 15% configured
- [ ] `legal-za-customer`, `legal-za-project` field packs loaded
- [ ] **Field promotion verified**: 7+ customer slugs inline, `matter_type` inline on matter dialog, no duplicates in CustomFieldSection
- [ ] 4 matter templates present from `legal-za` pack
- [ ] **Progressive disclosure verified**: all 4 legal modules enabled + visible in sidebar; no foreign vertical terms
- [ ] Trust account created
- [ ] **Tier removal verified**: billing page flat, no upgrade UI

---

## Days 1–7 — First client onboarding: Sipho Dlamini (litigation)

### Day 1 — Conflict check & client creation

**Actor**: Bob Ndlovu (Admin)

- [ ] **1.1** Login as Bob
- [ ] **1.2** Navigate to **Conflict Check** page
- [ ] **1.3** Search for **"Sipho Dlamini"** → verify result is **CLEAR** (green — no existing matches)
- [ ] **1.4** 📸 **Screenshot**: Conflict check clear result
- [ ] **1.5** Navigate to **Clients** list
- [ ] **1.6** Click **New Client**
- [ ] **1.7** Fill standard fields: Name = **Sipho Dlamini**, Email = `sipho.dlamini@email.co.za`, Phone = **+27-82-555-0101**
- [ ] **1.8** Fill promoted fields (verify inline rendering): `client_type` = **INDIVIDUAL**, `physical_address` = "42 Commissioner St, Johannesburg, 2001", `id_passport_number` = "8501015800083"
- [ ] **1.9** Save → verify client appears in list with status **PROSPECT**
- [ ] **1.10** Click into client detail → verify lifecycle badge shows **PROSPECT**
- [ ] **1.11** Verify promoted fields render inline at the top of the detail page, NOT in the sidebar CustomFieldSection

### Day 2 — FICA/KYC onboarding

- [ ] **2.1** On Sipho's client detail page, transition to **ONBOARDING** → verify badge updates
- [ ] **2.2** Navigate to Onboarding/Compliance tab → verify FICA checklist is auto-instantiated (from `fica-kyc-za` pack)
- [ ] **2.3** Verify checklist contains at minimum: "Certified ID Copy", "Proof of Address" (utility bill), "Source of Funds"
- [ ] **2.4** Mark "Certified ID Copy" ✓ → add note "Verified against home affairs ID"
- [ ] **2.5** Mark "Proof of Address" ✓ → upload a test PDF (any small PDF)
- [ ] **2.6** Mark "Source of Funds" ✓ → add note "Employment income, verified via payslip"
- [ ] **2.7** Verify checklist shows 100% complete → customer auto-transitions to **ACTIVE**
- [ ] **2.8** Verify ACTIVE badge appears on client detail page
- [ ] **2.9** Navigate to audit log for this client → verify FICA completion events are recorded

### Day 3 — Matter creation from template

- [ ] **3.1** On Sipho's client detail, click **New Matter**
- [ ] **3.2** Select template: **Litigation (Personal Injury / General)**
- [ ] **3.3** Fill: Matter Name = "Sipho Dlamini v. Standard Bank (civil)", `matter_type` = **Litigation**, `case_number` = "JHB/CIV/2026/001", `court_name` = "Gauteng High Court, Johannesburg"
- [ ] **3.4** Save → matter created from template with pre-populated task list
- [ ] **3.5** Verify matter appears under Sipho's matter list
- [ ] **3.6** Navigate to matter detail → verify task list is pre-populated from template (expect 5+ tasks)
- [ ] **3.7** Assign first task to Bob, second task to Carol
- [ ] **3.8** Verify terminology: page heading says "Matter" (not "Project"), breadcrumb shows Clients > Sipho Dlamini > Matter > ...

### Day 4 — Engagement letter & first time entry

- [ ] **4.1** Generate engagement letter from matter detail (Document Templates → Engagement Letter)
- [ ] **4.2** Preview PDF → verify Mathebula letterhead + brand colour applied + client details filled from context
- [ ] **4.3** Save generated document → verify it appears under matter's Documents tab
- [ ] **4.4** Log first time entry: Bob, 1.5 hours, "Drafting particulars of claim", billable = yes
- [ ] **4.5** Verify time entry snapshots Bob's billing rate (R1,200/hr → R1,800 value)
- [ ] **4.6** Verify activity feed shows: matter created, engagement letter generated, time entry logged

### Day 5 — 📸 Matter detail wow moment

- [ ] **5.1** Navigate to Sipho's litigation matter detail page
- [ ] **5.2** Verify all promoted fields visible inline at top (matter_type, case_number, court_name)
- [ ] **5.3** Verify Tasks tab, Time tab, Documents tab, Comments tab, Activity tab all load without errors
- [ ] **5.4** 📸 **Screenshot**: Matter detail page with promoted fields + terminology + tabs visible

### Days 6–7 — More activity on Sipho's matter

- [ ] **6.1** Carol logs 2.0 hours: "Legal research — precedent review"
- [ ] **6.2** Bob adds a comment on the matter: "Need to confirm court date by Monday" with `@Carol` mention
- [ ] **6.3** Carol logs in → sees notification bell with 1 unread → clicks → notification routes to the matter comment
- [ ] **6.4** Carol replies to comment: "Confirmed, court date is 2026-05-12"
- [ ] **6.5** Bob uploads a PDF document to matter (label: "Particulars of Claim — draft v1")
- [ ] **6.6** Verify activity feed for the matter shows all events in reverse-chronological order

---

## Days 8–21 — Second client: Moroka Family Trust (deceased estate)

### Day 8 — Trust client onboarding

**Actor**: Thandi (Owner — senior partner handles estate work)

- [ ] **8.1** Conflict check for "Moroka Family Trust" and "Peter Moroka" (deceased) → both CLEAR
- [ ] **8.2** Create new client: Name = **Moroka Family Trust**, `client_type` = **TRUST**, `registration_number` = "IT000456/2018"
- [ ] **8.3** Add beneficial owners custom field: 3 owners (Grace Moroka, James Moroka, Ruth Moroka)
- [ ] **8.4** Transition to ONBOARDING
- [ ] **8.5** Complete FICA checklist with TRUST variant items (trust deed, letter of authority, beneficial owner verification)
- [ ] **8.6** Verify customer transitions to ACTIVE

### Day 9 — Deceased estate matter

- [ ] **9.1** Create matter from template: **Deceased Estate Administration**
- [ ] **9.2** Matter name: "Estate Late Peter Moroka"
- [ ] **9.3** `matter_type` = **Estates**
- [ ] **9.4** Verify template-instantiated tasks include: "L&D documents", "Master of High Court appointment", "Inventory", "Liquidation & Distribution Account", "Advertisement — Section 29"
- [ ] **9.5** Assign first 3 tasks to Thandi, last 2 to Bob

### Days 10–14 — Estate admin work

- [ ] **10.1** Thandi logs 3.0 hours: "Initial consultation with heirs + document gathering"
- [ ] **10.2** Thandi uploads trust deed PDF to matter documents
- [ ] **11.1** Bob logs 1.5 hours: "Master of High Court filing preparation"
- [ ] **12.1** Thandi marks "L&D documents" task as DONE
- [ ] **12.2** Thandi creates an entry in trust accounting: deposit R50,000 for estate admin, source = "Moroka Trust operating account", reference = "Initial fund for estate admin"
- [ ] **12.3** Verify trust account balance updates to R50,000
- [ ] **13.1** Carol logs 1.0 hour: "Drafting Section 29 advertisement notice"
- [ ] **14.1** Thandi comments on matter: "Master's appointment received" with a PDF attachment

### Day 15 — Budget check

- [ ] **15.1** Navigate to Estate matter → Budget tab
- [ ] **15.2** Set estate matter budget: 40 hours total, R80,000 cap (hours + currency mode)
- [ ] **15.3** Verify current burn: ~5.5 hours, ~R11,000 (based on days 10–14 entries)
- [ ] **15.4** Verify budget status indicator shows "on track" (< 50% consumed)

### Days 16–21 — More estate work

- [ ] **16.1** Add 5 more time entries across the team (total accumulated ~18 hours)
- [ ] **18.1** Upload additional documents: "Inventory draft", "Bank statements"
- [ ] **20.1** Complete 2 more tasks from the template
- [ ] **21.1** Verify budget tab now shows ~45% consumed, still on track

---

## Days 22–35 — Third client: Lerato Mthembu (RAF claim)

### Day 22 — RAF client onboarding (full flow with court calendar)

**Actor**: Bob (Admin)

- [ ] **22.1** Conflict check "Lerato Mthembu" → CLEAR
- [ ] **22.2** Create individual client, standard fields + promoted (client_type = INDIVIDUAL, physical_address, id_passport_number)
- [ ] **22.3** Complete FICA onboarding → client ACTIVE
- [ ] **22.4** Create matter from Litigation template, matter_type = **Road Accident Fund**, case_number = "RAF/2026/0042"
- [ ] **22.5** Matter title: "Lerato Mthembu — RAF Claim (MVA 2025-11-03)"

### Day 23 — Adverse party registry & court calendar

- [ ] **23.1** Add adverse party: "Road Accident Fund" (entity type = Government)
- [ ] **23.2** Navigate to **Court Calendar**
- [ ] **23.3** Create court event: Matter = RAF claim, Type = "Case Management Conference", Date = Day 45 from today, Court = "Gauteng High Court"
- [ ] **23.4** Verify event appears on the court calendar view
- [ ] **23.5** Verify the event also appears on the matter detail Activity tab
- [ ] **23.6** 📸 **Screenshot**: Court calendar with the RAF event highlighted

### Days 24–35 — RAF claim work

- [ ] **24.1** Bob logs 4.0 hours: "Medical records collation"
- [ ] **26.1** Bob logs 3.0 hours: "Expert witness statements"
- [ ] **28.1** Bob uploads medical report PDFs to matter documents
- [ ] **30.1** Carol logs 2.0 hours: "Legal research — RAF tariff"
- [ ] **32.1** Add LSSA tariff line item to matter (if LSSA tariff module surfaces this UI)
- [ ] **33.1** Thandi reviews the matter, adds comment: "Ready for summons" with @Bob mention
- [ ] **35.1** Bob marks 4 template tasks as DONE

---

## Day 33 — 📸 Profitability wow moment (mid-month cross-matter view)

- [ ] **33.1** As Thandi, navigate to **Reports > Profitability** (or Profitability nav item)
- [ ] **33.2** Verify all three matters are listed with their billed hours, cost, revenue, margin
- [ ] **33.3** Verify ZAR currency formatting throughout
- [ ] **33.4** Verify each matter has non-zero values (no "N/A" or "–" because rate snapshots should exist)
- [ ] **33.5** 📸 **Screenshot**: Profitability page with 3 matters + margin column + ZAR formatting

---

## Days 36–60 — First fee notes (invoicing)

### Day 36 — First fee note draft (Sipho litigation)

**Actor**: Thandi

- [ ] **36.1** Navigate to Sipho's matter → Billing tab (or Invoicing nav → New Fee Note)
- [ ] **36.2** Create fee note from unbilled time entries → verify all billable entries from days 4–7 are selectable
- [ ] **36.3** Select all entries → preview fee note
- [ ] **36.4** Verify fee note line items show: date, attorney, description, hours, rate, amount
- [ ] **36.5** Verify VAT 15% applied and totals are correct
- [ ] **36.6** Save as DRAFT → verify fee note appears in fee notes list with status DRAFT

### Day 38 — Approve & send

- [ ] **38.1** Open draft fee note → click Approve → status → SENT
- [ ] **38.2** Verify audit event recorded
- [ ] **38.3** Generate PDF of fee note → preview with Mathebula letterhead + brand colour
- [ ] **38.4** Verify time entries are now marked as billed (not selectable for a new fee note)

### Day 45 — Second fee note (Moroka estate) with LSSA tariff

- [ ] **45.1** Navigate to Moroka matter → Billing tab
- [ ] **45.2** Create fee note from unbilled time (days 10–21)
- [ ] **45.3** Verify if LSSA tariff module surfaces tariff-based line items (if not, log as MEDIUM gap)
- [ ] **45.4** Approve → send → generate PDF

### Day 47 — 📸 Fee note PDF wow moment

- [ ] **47.1** Open the Moroka estate fee note PDF
- [ ] **47.2** Verify: Mathebula letterhead, brand colour accent, client details, line items, VAT, total, banking details
- [ ] **47.3** 📸 **Screenshot**: Fee note PDF preview

### Days 50–60 — Third fee note (RAF) + payment recording

- [ ] **50.1** Generate fee note for Lerato's RAF matter (days 24–35 entries)
- [ ] **55.1** Record payment received on Sipho's fee note (day 38 fee note) — amount = full, method = EFT
- [ ] **55.2** Verify fee note status → PAID
- [ ] **58.1** Verify audit log shows payment event

---

## Days 61–75 — Additional cycles & portal interaction

- [ ] **61.1** Create a 4th client (individual, collections matter from template) — compressed onboarding (happy path only)
- [ ] **62.1** Log 4 time entries across the team on the 4th matter
- [ ] **65.1** Generate fee note → approve → send
- [ ] **68.1** **Portal**: generate magic link for Sipho → verify link emails via Mailpit → open link in incognito → portal loads with Sipho's matters, fee notes, and documents visible
- [ ] **68.2** On the portal, verify Sipho can view (but not edit) matter status + fee notes
- [ ] **70.1** Back in the app, Carol logs additional time on Moroka estate
- [ ] **72.1** Thandi adds a trust accounting entry: withdrawal from Moroka trust account for R12,000 (disbursement to third party)
- [ ] **72.2** Verify trust balance updates correctly
- [ ] **75.1** Mark Sipho's litigation matter as COMPLETED (or CLOSED) → verify state transition allowed

---

## Days 76–90 — Reports, close-out & final sweep

### Day 80 — Reports & dashboards

- [ ] **80.1** Navigate to **Reports** (or Company Dashboard)
- [ ] **80.2** Verify cross-matter/cross-client dashboard loads with 90 days of data
- [ ] **80.3** Verify utilization metrics (billable % per team member) render
- [ ] **80.4** Export a report to CSV → verify file downloads with expected columns
- [ ] **80.5** Navigate to **My Work** (as Bob) → verify Bob's assigned tasks, time summary, and upcoming court events render

### Day 85 — Audit log sweep

- [ ] **85.1** Navigate to **Audit Log** (platform or org level)
- [ ] **85.2** Filter by actor = Thandi → verify all Thandi's actions over 90 days are listed
- [ ] **85.3** Filter by entity type = Matter → verify matter CRUD events render
- [ ] **85.4** Verify audit entries include: timestamp, actor, action, entity, details (JSONB expandable)

### Day 88 — 📸 Activity feed wow moment

- [ ] **88.1** Navigate to Moroka estate matter → Activity tab
- [ ] **88.2** Verify full 90-day activity history renders (matter created, tasks completed, time entries, documents, comments, fee notes, payments)
- [ ] **88.3** 📸 **Screenshot**: 90-day activity feed for estate matter

### Day 90 — Final regression sweep

- [ ] **90.1** **Terminology sweep**: walk sidebar, every settings page, every create dialog → zero occurrences of "Project" (should be "Matter"), "Customer" (should be "Client"), "Invoice" (should be "Fee Note"), or any agency/accounting leakage
- [ ] **90.2** **Field promotion sweep**: reopen every create dialog (Client, Matter, Task, Fee Note) → verify no promoted slugs have regressed into the sidebar
- [ ] **90.3** **Progressive disclosure sweep**: confirm all 4 legal modules still visible, no accounting/agency nav items leaked in
- [ ] **90.4** **Tier removal sweep**: navigate to Settings > Billing → confirm still flat, no tier UI
- [ ] **90.5** **Console errors**: open browser devtools → navigate through every top-level nav item → confirm zero JS errors in console
- [ ] **90.6** **Mailpit sweep**: verify no bounced/failed emails

---

## Exit checkpoints (ALL must pass for demo-ready)

- [ ] **E.1** Every step above is checked
- [ ] **E.2** All 6 📸 wow moments captured without visual regression
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report (`qa/gap-reports/demo-readiness-{DATE}/legal-za.md`)
- [ ] **E.4** **Tier removal** verified on 3+ screens (Settings > Billing, team invite flow, member count page)
- [ ] **E.5** **Field promotion** verified on Client, Matter, Task create dialogs
- [ ] **E.6** **Progressive disclosure** verified — all 4 legal modules present, no cross-vertical leaks
- [ ] **E.7** Keycloak flow end-to-end — from `/request-access` → approval → owner register → team invites → logged-in org dashboard, no mock IDP used
- [ ] **E.8** Terminology sweep passed — zero "Project/Customer/Invoice" leaks in sidebar, breadcrumbs, settings, or dialogs
- [ ] **E.9** 90 days of audit events recorded and filterable
- [ ] **E.10** Cycle completed on one clean pass (no dev subagent dispatches mid-loop for blockers)
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] Every fix PR merged during this cycle satisfied the same four gates before merging (not just the final run)

**If any checkpoint fails**: log finding to `qa/gap-reports/demo-readiness-{YYYY-MM-DD}/legal-za.md` using the severity/format defined in the master doc, and let `/qa-cycle-kc` dispatch a fix before re-running the failing step. **Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.
