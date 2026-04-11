# QA Test Plan: Legal Vertical Onboarding (Keycloak Mode)

**Target**: DocTeams platform — `legal-za` vertical profile
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025). See `qa/keycloak-e2e-guide.md` for setup details.
**Auth**: Real Keycloak OIDC. **No mock IDP. No `e2e-up.sh`. No `/mock-login`.**
**Vertical profile**: `legal-za`

---

## Goal

Validate the legal-vertical onboarding journey end-to-end against the real Keycloak stack: a brand-new firm signs up through the product, the platform admin approves them, the owner invites their team, and three contrasting customer types (litigation individual, deceased estate trust, Road Accident Fund plaintiff) are onboarded — with increasing complexity each step.

## Scope

**In scope**
- Org access request flow (`/request-access` → OTP → padmin approval → Keycloak invite)
- Owner / admin / member Keycloak registration
- Plan upgrade and team invites (real Keycloak invite emails via Mailpit)
- `legal-za` profile activation: terminology, modules, project templates, custom fields, automation, clause packs
- Customer onboarding for **3 contrasting matter types**: Litigation, Estates, Road Accident Fund
- Conflict check, FICA/KYC checklist, matter creation from template, engagement letter
- Court calendar entry and adverse-party registry on the RAF matter
- Audit-log and activity-feed verification

**Out of scope (not part of this onboarding plan)**
- Multi-week time recording, retainers, billing runs, fee-note generation, profitability reports
- Trust accounting beyond a single sanity-check deposit
- Portal flows (proposal acceptance, magic links)
- Cross-org / cross-tenant testing

## Pre-test state (must be true)

- [ ] Docker is running
- [ ] **Only the platform admin is seeded in Keycloak** (`padmin@docteams.local` / `password`). No `alice/bob/carol`, no demo orgs, no demo data
- [ ] No tenant schemas exist for the test org names below (drop them from Postgres if a previous run left state behind)
- [ ] Mailpit inbox is empty (open `http://localhost:8025` and clear)
- [ ] Browser cookies cleared (or use a fresh incognito profile per actor)

## Test data (created during the test — do not pre-seed)

| Actor | Role | Email | Password |
|---|---|---|---|
| Thandi Mathebula | Owner (Senior Partner) | `thandi@mathebula-test.local` | `SecureP@ss1` |
| Bob Ndlovu | Admin (Associate) | `bob@mathebula-test.local` | `SecureP@ss2` |
| Carol Mokoena | Member (Candidate Attorney) | `carol@mathebula-test.local` | `SecureP@ss3` |

| Customer | Type | Matter |
|---|---|---|
| Sipho Dlamini | INDIVIDUAL | Litigation — General civil dispute |
| Moroka Family Trust | TRUST | Deceased Estate — Peter Moroka |
| Lerato Mthembu | INDIVIDUAL | Litigation — Road Accident Fund claim |

**How to use**: Walk through each step in order. Check the box when done. Note failures with the step number and a one-line description. Stop and triage if a Session checkpoint fails — later sessions depend on earlier state.

---

## Session 0 — Stack startup & sanity (≈ 10 min)

Actor: **Tester (no login required)**

- [ ] **0.1** From repo root: `bash compose/scripts/dev-up.sh` — wait for "infra up"
- [ ] **0.2** First-time-only: `bash compose/scripts/keycloak-bootstrap.sh` — confirm output mentions `padmin@docteams.local` created
- [ ] **0.3** In separate terminals start: `backend` (`SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run`), `gateway` (`./mvnw spring-boot:run`), `frontend` (`NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev`)
- [ ] **0.4** `curl -sf http://localhost:8080/actuator/health` → returns `{"status":"UP"}`
- [ ] **0.5** `curl -sf http://localhost:8443/actuator/health` → returns `{"status":"UP"}`
- [ ] **0.6** `curl -sf http://localhost:3000/` → 200 OK
- [ ] **0.7** `curl -sf http://localhost:8180/realms/docteams` → realm JSON returned
- [ ] **0.8** Open Mailpit (`http://localhost:8025`) → inbox empty
- [ ] **0.9** Confirm Keycloak admin console (`http://localhost:8180/admin`) shows **only** `padmin@docteams.local` under Users in the `docteams` realm — no other users
- [ ] **0.10** Confirm Postgres has no leftover tenant schemas for this test:
  ```
  docker exec b2b-postgres psql -U postgres -d app -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%';"
  ```
  → only previous-test schemas (or empty). Drop any `mathebula` related schemas if present.

**Session 0 checkpoints**
- [ ] All four services healthy
- [ ] `padmin@docteams.local` is the only Keycloak user in the `docteams` realm
- [ ] Mailpit inbox is empty

---

## Session 1 — Firm onboarding via product (Simple) (≈ 15 min)

The owner discovers the product, requests access, verifies email via OTP, and waits for platform admin approval. The platform admin then approves the request, which triggers tenant provisioning + a Keycloak invite to the owner.

### Phase A — Owner submits access request

Actor: **Thandi** (prospective owner — not yet authenticated)

- [ ] **1.1** Open a fresh incognito window → navigate to `http://localhost:3000` → marketing/landing page loads
- [ ] **1.2** Click **"Get Started"** (or equivalent CTA) → page navigates to `/request-access`
- [ ] **1.3** Fill the form:
  - Email: `thandi@mathebula-test.local`
  - Full Name: **Thandi Mathebula**
  - Organization: **Mathebula & Partners**
  - Country: **South Africa**
  - Industry: **Legal**
- [ ] **1.4** Submit → page advances to OTP verification step
- [ ] **1.5** Open Mailpit (`http://localhost:8025`) → confirm OTP email arrived for `thandi@mathebula-test.local` (subject contains "verification" or "code")
- [ ] **1.6** Copy the 6-digit OTP from the email body
- [ ] **1.7** Paste the OTP into the verification form → click **Verify**
- [ ] **1.8** Success message visible: e.g. "Your request has been submitted for review"
- [ ] **1.9** Close this incognito window (we'll come back as Thandi later)

### Phase B — Platform admin approves the request

Actor: **Platform Admin** (`padmin@docteams.local` / `password`)

- [ ] **1.10** Open a new incognito window → navigate to `http://localhost:3000/dashboard` → redirected to Keycloak login
- [ ] **1.11** Login as `padmin@docteams.local` / `password`
- [ ] **1.12** After login, manually navigate to `/platform-admin/access-requests`
- [ ] **1.13** Verify **Mathebula & Partners** appears in the **Pending** tab with industry "Legal", country "South Africa"
- [ ] **1.14** Click **Approve** → confirm in dialog
- [ ] **1.15** Status changes to **Approved** without any error toast
- [ ] **1.16** In the backend log terminal, verify provisioning ran: look for `Provisioning tenant` and `Schema created` entries — no stack trace
- [ ] **1.17** Confirm new Keycloak organization was created — visit Keycloak admin → Organizations (or Groups) — `Mathebula & Partners` should be present
- [ ] **1.18** Open Mailpit → verify a **Keycloak invitation email** arrived for `thandi@mathebula-test.local` (subject usually contains "Invitation" or "Update Account")
- [ ] **1.19** Logout the platform admin (or close window)

### Phase C — Owner registers via Keycloak invite

Actor: **Thandi** (registering for the first time)

- [ ] **1.20** Open a fresh incognito window
- [ ] **1.21** In Mailpit, open Thandi's invitation email and **click the invitation link** (it opens in the new incognito window or copy the URL)
- [ ] **1.22** Keycloak registration form loads → fill:
  - First Name: **Thandi**
  - Last Name: **Mathebula**
  - Password: `SecureP@ss1`
  - Confirm Password: `SecureP@ss1`
- [ ] **1.23** Submit → redirected back to the app → lands on the org dashboard
- [ ] **1.24** Verify the URL contains the slug for Mathebula & Partners (e.g. `/org/mathebula-partners/dashboard`) — record the slug here: `__________`
- [ ] **1.25** Verify the sidebar shows the org name and Thandi's profile

**Session 1 checkpoints**
- [ ] Access request submitted via the real `/request-access` flow (no manual DB inserts)
- [ ] OTP email received and verified through Mailpit
- [ ] Platform admin approved the request without error
- [ ] Keycloak organization created
- [ ] Owner registered via Keycloak invite link
- [ ] Owner is logged into the dashboard with the correct org slug

---

## Session 2 — Plan upgrade, team invites, legal-za activation (Simple) (≈ 25 min)

Actor: **Thandi** (Owner — logged in from Session 1)

### Phase A — Plan upgrade

- [ ] **2.1** Navigate to **Settings → Billing** → verify current plan = **Starter**
- [ ] **2.2** Click **Upgrade to Pro** → confirm in dialog → plan now shows **Pro**
- [ ] **2.3** Reload page → plan persists as Pro

### Phase B — Verify legal-za profile is active

- [ ] **2.4** Navigate to dashboard (`/org/{slug}/dashboard`)
- [ ] **2.5** Verify sidebar terminology uses **legal terms**:
  - "Matters" not "Projects"
  - "Clients" not "Customers"
  - "Fee Notes" not "Invoices"
  - "Action Items" not "Tasks" (or whatever the legal-za map specifies)
- [ ] **2.6** Verify legal-only nav sections appear (module-gated): **Trust Accounting**, **Court Calendar**, **Conflict Check**
- [ ] **2.7** Navigate to **Settings → Modules** → verify enabled: `trust_accounting`, `court_calendar`, `conflict_check`, `lssa_tariff` (whichever the legal-za pack ships)
- [ ] **2.8** Navigate to **Settings → Templates** → verify the **4 legal-za matter templates** are present:
  - Litigation (Personal Injury / General)
  - Deceased Estate Administration
  - Collections (Debt Recovery)
  - Commercial (Corporate & Contract)
- [ ] **2.9** Navigate to **Settings → Custom Fields** → verify a legal client/matter field group exists (look for fields like `matter_type`, `case_number`, `court_name`, `opposing_party`, `id_passport_number`, `client_type`)
- [ ] **2.10** Navigate to **Settings → Automations** → verify legal automation rules from `automation-templates/legal-za.json` are listed

### Phase C — Configure rates and tax

- [ ] **2.11** Navigate to **Settings → General** → set Default Currency = **ZAR** → Save → reload → persists
- [ ] **2.12** Set brand colour to **#1B3A4B** → Save → colour chip updates
- [ ] **2.13** Navigate to **Settings → Tax** → create rate **VAT / 15% / Active** (skip if seeded)
- [ ] **2.14** Navigate to **Settings → Rates**
- [ ] **2.15** Create org-level **billing rate** for Thandi: **R2,500 / hr (ZAR)**
- [ ] **2.16** Create **cost rate** for Thandi: **R1,000 / hr**
- [ ] (Bob and Carol billing/cost rates are added after they register, in Phase E)

### Phase D — Invite team

- [ ] **2.17** Navigate to **Settings → Team** → verify Thandi is listed as **Owner**
- [ ] **2.18** Click **Invite Member** → fill: Email = `bob@mathebula-test.local`, Role = **Admin** → Send
- [ ] **2.19** Open Mailpit → verify Keycloak invitation email arrived for Bob
- [ ] **2.20** Click **Invite Member** → fill: Email = `carol@mathebula-test.local`, Role = **Member** → Send
- [ ] **2.21** Mailpit shows invitation email for Carol
- [ ] **2.22** In a separate incognito window, open Bob's invite link → register: First Name **Bob**, Last Name **Ndlovu**, Password `SecureP@ss2` → lands on org dashboard authenticated
- [ ] **2.23** In another incognito window, open Carol's invite link → register: First Name **Carol**, Last Name **Mokoena**, Password `SecureP@ss3` → lands on org dashboard authenticated

### Phase E — Bob and Carol rates

Actor: **Thandi** (back in the Owner window)

- [ ] **2.24** Navigate to **Settings → Rates**
- [ ] **2.25** Create billing rate Bob = **R1,200 / hr**, cost rate Bob = **R500 / hr**
- [ ] **2.26** Create billing rate Carol = **R550 / hr**, cost rate Carol = **R200 / hr**
- [ ] **2.27** Verify Settings → Team page lists all three with correct roles

**Session 2 checkpoints**
- [ ] Plan = Pro
- [ ] Sidebar and headings use legal terminology end-to-end
- [ ] All 4 matter templates visible under Settings → Templates
- [ ] All 4 legal modules enabled
- [ ] Bob (Admin) and Carol (Member) successfully registered via real Keycloak invites
- [ ] All 3 users have billing + cost rates set
- [ ] VAT 15% configured
- [ ] No errors in backend logs during any of the above

---

## Session 3 — Litigation customer onboarding (Medium) (≈ 25 min)

A walk-in client with a general civil dispute. Tests the **simplest customer flow**: individual person, conflict-check clear, basic FICA, single matter from the Litigation template.

Actor: **Bob Ndlovu** (Admin — log in via Keycloak)

- [ ] **3.1** Open incognito window → navigate to `http://localhost:3000/dashboard` → Keycloak login → `bob@mathebula-test.local` / `SecureP@ss2`
- [ ] **3.2** Lands on Mathebula & Partners dashboard

### Phase A — Conflict check

- [ ] **3.3** Navigate to **Conflict Check** (sidebar)
- [ ] **3.4** Search **"Sipho Dlamini"** → result = **CLEAR** (green / no matches)
- [ ] **3.5** Search **"Dlamini"** → still CLEAR (or shows only the search term, no matter matches)

### Phase B — Create the client

- [ ] **3.6** Navigate to **Clients** → click **New Client**
- [ ] **3.7** Fill standard fields:
  - Name: **Sipho Dlamini**
  - Email: `sipho.dlamini@email.co.za`
  - Phone: `+27-82-555-0101`
- [ ] **3.8** Fill legal custom fields:
  - `client_type` = **INDIVIDUAL**
  - `id_passport_number` = `8501015800083`
  - `physical_address` = `42 Commissioner St, Johannesburg, 2001`
- [ ] **3.9** Save → client appears in client list with status **PROSPECT**
- [ ] **3.10** Open client detail → lifecycle badge = **PROSPECT**

### Phase C — FICA / KYC onboarding

- [ ] **3.11** Click **Transition to Onboarding** → badge updates to **ONBOARDING**
- [ ] **3.12** Open the **Onboarding** (or Compliance / Checklist) tab → FICA checklist auto-populated (legal-za)
- [ ] **3.13** Mark items: "Certified ID Copy" ✓, "Proof of Address" ✓, "FICA declaration signed" ✓ (whatever the pack ships)
- [ ] **3.14** Complete remaining required FICA items
- [ ] **3.15** After last item ticked → client lifecycle auto-transitions to **ACTIVE**
- [ ] **3.16** Reload page → confirm status sticks at ACTIVE

### Phase D — Create matter from Litigation template

- [ ] **3.17** From the client detail page, click **New Matter** (or navigate Matters → New Matter and pick this client)
- [ ] **3.18** Select template: **Litigation (Personal Injury / General)**
- [ ] **3.19** Fill: Name = **"Sipho Dlamini — Civil dispute"**, Client = Sipho Dlamini
- [ ] **3.20** Fill custom fields (if exposed): `matter_type = LITIGATION`, `case_number = "TBD"`, `court_name = "Gauteng Division, Johannesburg"`, `opposing_party = "TBD"`
- [ ] **3.21** Save → matter created with **9 pre-populated action items** from the template (Initial consultation, Letter of demand, Issue summons, Plea, Discovery, Pre-trial, Trial, Post-judgment, Execution)
- [ ] **3.22** Verify each action item shows estimated hours from the template

### Phase E — Engagement letter (proposal)

- [ ] **3.23** Navigate to **Engagement Letters** (Proposals)
- [ ] **3.24** Click **New Engagement Letter**
- [ ] **3.25** Fill: Title = "Civil dispute — Sipho Dlamini", Client = Sipho Dlamini, Fee Model = **Hourly**, Expiry = 30 days from today
- [ ] **3.26** Save → status = **DRAFT**
- [ ] **3.27** Click **Send** → status = **SENT**
- [ ] **3.28** Mailpit → engagement-letter notification visible

**Session 3 checkpoints**
- [ ] Conflict check ran on the new client name
- [ ] Sipho client created and walked PROSPECT → ONBOARDING → ACTIVE
- [ ] Litigation matter exists with 9 action items from template
- [ ] Engagement letter sent
- [ ] No errors in backend logs

---

## Session 4 — Estates customer onboarding (Medium) (≈ 25 min)

A trust entity client (not an individual). Tests **non-individual KYC**, the **Deceased Estate Administration** template, and one **trust account deposit** to confirm the trust module works.

Actor: **Thandi Mathebula** (Owner — log in via Keycloak)

- [ ] **4.1** Open incognito window → log in as `thandi@mathebula-test.local` / `SecureP@ss1`

### Phase A — Conflict check

- [ ] **4.2** Navigate to **Conflict Check**
- [ ] **4.3** Search **"Moroka Family Trust"** → CLEAR
- [ ] **4.4** Search **"Peter Moroka"** (deceased) → CLEAR

### Phase B — Trust account setup (one-time, if not already configured in Session 2)

- [ ] **4.5** Navigate to **Trust Accounting → Trust Accounts** (or Settings → Trust Accounts)
- [ ] **4.6** If no account exists, create one: Name = **"Mathebula & Partners Trust Account"**, Type = **GENERAL**, Bank = **Standard Bank**
- [ ] **4.7** Save → account visible in trust accounts list

### Phase C — Create the trust client

- [ ] **4.8** Navigate to **Clients** → **New Client**
- [ ] **4.9** Fill:
  - Name: **Moroka Family Trust**
  - Email: `trustees@morokatrust.co.za`
  - Phone: `+27-11-555-0202`
- [ ] **4.10** Custom fields:
  - `client_type` = **TRUST**
  - `registration_number` = `IT/2015/000123`
  - `physical_address` = `15 Saxonwold Drive, Saxonwold, Johannesburg, 2196`
  - Notes = "Trustees: James Moroka, Sarah Moroka. Deceased: Peter Moroka (d. 2026-02-15)"
- [ ] **4.11** Save → client appears with status **PROSPECT**

### Phase D — Trust-entity FICA

- [ ] **4.12** Transition to **Onboarding** → checklist appears
- [ ] **4.13** Complete trust-specific items (the legal-za FICA checklist should branch on `client_type = TRUST` — verify the items are appropriate, not the individual checklist):
  - Trust deed copy ✓
  - Letters of Authority (Master) ✓
  - Trustee 1 ID (James Moroka) ✓
  - Trustee 2 ID (Sarah Moroka) ✓
  - Proof of trust banking details ✓
  - SARS tax number for trust ✓
- [ ] **4.14** Mark all checklist items → client auto-transitions to **ACTIVE**

### Phase E — Create matter from Deceased Estate template

- [ ] **4.15** From client detail → **New Matter**
- [ ] **4.16** Select template: **Deceased Estate Administration**
- [ ] **4.17** Fill: Name = **"Estate Late Peter Moroka"**, Client = Moroka Family Trust
- [ ] **4.18** Custom fields: `matter_type = ESTATES`, deceased name = "Peter Moroka", deceased date = 2026-02-15
- [ ] **4.19** Save → matter created with **9 pre-populated action items** (Obtain death certificate & will, Report to Master, Letters of Executorship, Advertise creditors, Inventory, Open estate account, L&D account, Lodge L&D, Distribute & close)
- [ ] **4.20** Verify the first action item ("Obtain death certificate & will") is marked HIGH priority and assigned to PROJECT_LEAD

### Phase F — First trust deposit (smoke check only)

- [ ] **4.21** Navigate to **Trust Accounting → Transactions** → **New Transaction**
- [ ] **4.22** Fill: Type = **DEPOSIT**, Client = Moroka Family Trust, Matter = Estate Late Peter Moroka, Amount = **R250,000**, Reference = "EFT — proceeds from property sale"
- [ ] **4.23** Save → transaction status = **PENDING APPROVAL** (or DRAFT)
- [ ] **4.24** Click **Approve** → status moves to **APPROVED** / **POSTED** without error
- [ ] **4.25** Navigate to **Trust Accounting → Client Ledgers** → open Moroka Family Trust
- [ ] **4.26** Verify ledger balance = **R250,000.00**

**Session 4 checkpoints**
- [ ] Trust account created and visible
- [ ] Moroka Family Trust client created with `client_type = TRUST` and trust-specific FICA completed
- [ ] Deceased Estate matter exists with 9 template action items
- [ ] One trust deposit posted, client ledger balance correct
- [ ] No errors in backend logs

---

## Session 5 — Road Accident Fund client onboarding (Advanced) (≈ 35 min)

A high-value personal-injury claim against the **Road Accident Fund**. The most complex onboarding scenario: **plaintiff client with statutory third-party defendant**, requires capturing accident metadata, court calendar entry, adverse-party registry, and a **contingency-fee** engagement letter.

Actor: **Bob Ndlovu** (Admin — log in via Keycloak)

- [ ] **5.1** Log in as `bob@mathebula-test.local` / `SecureP@ss2`

### Phase A — Conflict check

- [ ] **5.2** Navigate to **Conflict Check**
- [ ] **5.3** Search **"Lerato Mthembu"** → CLEAR
- [ ] **5.4** Search **"Road Accident Fund"** → if Mathebula has handled previous RAF matters in this test session, expect a **historical-counterparty match** (informational, not blocking). On a fresh test stack, expect CLEAR
- [ ] **5.5** Record the result → either CLEAR or counterparty-match-only — both are acceptable to proceed

### Phase B — Create the plaintiff client

- [ ] **5.6** Navigate to **Clients** → **New Client**
- [ ] **5.7** Fill:
  - Name: **Lerato Mthembu**
  - Email: `lerato.mthembu@email.co.za`
  - Phone: `+27-83-555-0303`
- [ ] **5.8** Custom fields:
  - `client_type` = **INDIVIDUAL**
  - `id_passport_number` = `9203045811087`
  - `physical_address` = `7 Vilakazi St, Soweto, Johannesburg, 1804`
  - Notes = "Pedestrian, struck by insured vehicle on N1 Johannesburg, 2025-11-12. Hospitalised 3 weeks. Loss of earnings + general damages claim."
- [ ] **5.9** Save → client status = **PROSPECT**

### Phase C — FICA + medical/RAF document checklist

- [ ] **5.10** Transition to **Onboarding**
- [ ] **5.11** Complete standard FICA: Certified ID, Proof of Address, FICA declaration ✓
- [ ] **5.12** **RAF-specific documents** — record on the matter or client notes (no first-class checklist required):
  - Police accident report (case number) — capture: `CAS 145/11/2025`
  - J88 medical-legal report — note: "Pending, ordered from Chris Hani Bara"
  - Hospital discharge summary — ✓ on file
  - Section 17(4) statutory medical report — note: "Pending RAF prescribed form"
  - Pay-slips (loss of earnings proof) — ✓ on file
- [ ] **5.13** Mark FICA checklist complete → client auto-transitions to **ACTIVE**

### Phase D — Create matter from Litigation template (RAF flavour)

- [ ] **5.14** From client detail → **New Matter**
- [ ] **5.15** Select template: **Litigation (Personal Injury / General)**
- [ ] **5.16** Fill matter:
  - Name: **"Lerato Mthembu — RAF claim (CAS 145/11/2025)"**
  - Client: Lerato Mthembu
  - `matter_type` = **LITIGATION**
  - `case_number` = `TBD (RAF)`
  - `court_name` = `Gauteng Division, Johannesburg`
  - `opposing_party` = **Road Accident Fund**
  - Estimated value = `R1,500,000`
- [ ] **5.17** Save → matter has **9 pre-populated action items**
- [ ] **5.18** Open the matter → action items list visible

### Phase E — Engagement letter (Contingency fee model)

- [ ] **5.19** Navigate to **Engagement Letters** → **New Engagement Letter**
- [ ] **5.20** Fill:
  - Title: "RAF Claim — Lerato Mthembu"
  - Client: Lerato Mthembu
  - Matter: Lerato Mthembu — RAF claim
  - Fee Model: **Contingency** (verify the legal-za clause pack `legal-za-fees-contingency` populates the fee section)
  - Expiry: 30 days from today
- [ ] **5.21** Verify the rendered preview includes the contingency clause text (25% cap clause, statutory disclaimer, etc.)
- [ ] **5.22** Save → status = **DRAFT**
- [ ] **5.23** Click **Send** → status = **SENT**
- [ ] **5.24** Mailpit → engagement-letter email arrived

### Phase F — Court calendar & adverse party

- [ ] **5.25** From the matter, navigate to **Court Calendar** (or open the global calendar and add an entry linked to the matter)
- [ ] **5.26** Add a court date:
  - Matter: Lerato Mthembu — RAF claim
  - Type: **Application** (or "Motion")
  - Date: 60 days from today
  - Court: Gauteng Division, Johannesburg
  - Notes: "Application for interim payment under Section 17(4)"
- [ ] **5.27** Save → court date appears with status **SCHEDULED**
- [ ] **5.28** Reload the matter detail page → court date visible on the matter timeline / sidebar

- [ ] **5.29** From the matter, navigate to **Adverse Parties** (or equivalent section)
- [ ] **5.30** Add adverse party: **"Road Accident Fund"** — Type = STATUTORY ENTITY, Notes = "Statutory third-party defendant under RAF Act 56 of 1996"
- [ ] **5.31** Verify adverse party appears linked to the matter

### Phase G — First substantive action item

- [ ] **5.32** Open the matter → Action Items tab → click **"Initial consultation & case assessment"**
- [ ] **5.33** Mark status → **In Progress**
- [ ] **5.34** Click **Log Time** → 90 minutes, today's date, Description = "Initial consultation with Lerato Mthembu, taking statement re RAF claim, reviewing hospital records", Billable = yes
- [ ] **5.35** Save → time recording listed in the Time tab
- [ ] **5.36** Verify rate snapshot = **R1,200 / hr** (Bob's billing rate)
- [ ] **5.37** Add a comment on the matter: **"J88 ordered from Chris Hani Bara — ETA 3 weeks. Section 17(4) form to follow."**
- [ ] **5.38** Verify comment appears with Bob's name and timestamp

**Session 5 checkpoints**
- [ ] Conflict check executed for both client and RAF
- [ ] Lerato client onboarded through PROSPECT → ONBOARDING → ACTIVE
- [ ] RAF litigation matter created with 9 template action items, opposing party = Road Accident Fund
- [ ] Contingency engagement letter sent (clause pack content present)
- [ ] Court calendar entry exists for the matter (60 days out)
- [ ] Adverse party "Road Accident Fund" linked to the matter
- [ ] First action item moved to In Progress, 90 min logged at correct rate, comment posted

---

## Session 6 — Cross-cutting verification & sign-off (≈ 15 min)

Actor: **Thandi Mathebula** (Owner)

### Phase A — Activity feed and audit trail

- [ ] **6.1** Log in as Thandi
- [ ] **6.2** Navigate to **Dashboard** → recent activity widget should reference today's matter creation events for all 3 clients
- [ ] **6.3** Open Sipho's matter → **Activity** tab → events visible: matter created, action items added, engagement letter sent
- [ ] **6.4** Open Moroka matter → Activity tab → events visible: matter created, trust deposit posted
- [ ] **6.5** Open Lerato matter → Activity tab → events visible: matter created, action item In Progress, time logged, comment added, court date scheduled, adverse party linked
- [ ] **6.6** Navigate to **Audit Log** (Settings → Audit, or Platform Admin → Audit) — verify entries exist for: org provisioned, members invited, clients created, matters created, trust deposit approved, contingency engagement letter sent
- [ ] **6.7** Verify each audit entry has actor (email), timestamp, action, target

### Phase B — Terminology spot-check

- [ ] **6.8** Sidebar still shows: Matters, Clients, Fee Notes, Action Items (no "Project / Customer / Invoice / Task" anywhere outside Settings)
- [ ] **6.9** Client list page heading uses "Clients"
- [ ] **6.10** Matter list page heading uses "Matters"
- [ ] **6.11** Engagement letters list shows "Engagement Letters" (or whatever the legal-za map specifies — verify it isn't "Proposals")
- [ ] **6.12** Empty states / breadcrumbs / browser tab titles also use legal terms

### Phase C — Backend-state sanity (optional, recommended)

- [ ] **6.13** From the backend log terminal, search for `ERROR` or stack traces during the test session — none expected
- [ ] **6.14** Connect to Postgres and verify entity counts in the new tenant schema (replace `<schema>` with the schema for Mathebula & Partners — find via `SELECT * FROM public.org_schema_mapping;`):
  ```
  docker exec -it b2b-postgres psql -U postgres -d app -c "SET search_path = '<schema>'; SELECT 'customers' AS t, COUNT(*) FROM customers UNION ALL SELECT 'projects', COUNT(*) FROM projects UNION ALL SELECT 'tasks', COUNT(*) FROM tasks UNION ALL SELECT 'time_entries', COUNT(*) FROM time_entries UNION ALL SELECT 'proposals', COUNT(*) FROM proposals;"
  ```
  Expected (≥):
  - customers ≥ 3
  - projects ≥ 3 (matters)
  - tasks ≥ 27 (9 per matter × 3)
  - time_entries ≥ 1
  - proposals ≥ 2 (Sipho hourly + Lerato contingency)

### Phase D — Sign-off

- [ ] **6.15** All earlier session checkpoints are green
- [ ] **6.16** No `ERROR` log lines from backend or gateway during the run
- [ ] **6.17** Mailpit shows the expected emails: 1 OTP + 3 Keycloak invites + 2 engagement-letter notifications (≥ 6 emails total)
- [ ] **6.18** Tester signs the run sheet: name `__________`, date `2026-04-11`, result `PASS / FAIL / PARTIAL`

---

## Failure handling

If any checkpoint fails:

1. Stop execution at the failing step.
2. Capture the failing screen (browser screenshot) and the relevant tail of the backend log.
3. Note the step number and a one-line description in the run notes.
4. Triage:
   - Auth / Keycloak failure → re-check `keycloak-bootstrap.sh` ran, gateway is up, browser cookies cleared.
   - Provisioning failure → check `org_schema_mapping` and provisioning status table; look for backend stack traces.
   - Terminology miss → verify the org's vertical profile is set to `legal-za` (likely a profile-binding bug, not a terminology-map bug).
   - Template missing → verify `project-template-packs/legal-za.json` was loaded at startup.
5. Do not work around failures by manipulating the database — this plan exists to validate the real product flow. Surface bugs upstream.

## Tear-down

When the run completes (pass or fail):

- [ ] Stop the local services (Ctrl-C the four terminals)
- [ ] `bash compose/scripts/dev-down.sh --clean` — wipes Docker volumes so the next run starts from a clean platform-admin-only state
- [ ] Clear `/tmp/e2e-keycloak-state.json` if it exists (this plan doesn't write it, but other plans do)
- [ ] Confirm Mailpit and Postgres state is clean before the next run
