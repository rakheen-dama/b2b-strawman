# Test Plan: Legal Foundations — Court Calendar, Conflict Check & LSSA Tariff
## Phase 55 — DocTeams Platform

**Version**: 1.0
**Date**: 2026-04-03
**Author**: Product + QA
**Vertical**: legal-za (Moyo & Dlamini Attorneys)
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180). See `qa/keycloak-e2e-guide.md` for setup.

---

## 1. Purpose

Phase 55 built three legal modules (court calendar, conflict check, LSSA tariff) to prove
that two real verticals coexist cleanly in one deployment. The modules shipped without
end-to-end QA. This test plan verifies:

1. **Court calendar** works end-to-end: create court dates, postpone, cancel, record outcomes,
   prescription tracking with statutory deadlines, reminder notifications
2. **Conflict check** correctly detects conflicts via exact ID match, fuzzy name match, alias
   match, and registration number match — and that resolutions are tracked
3. **LSSA tariff** schedules are seeded correctly, can be browsed/cloned/searched, and tariff
   line items integrate correctly with the invoice system
4. **Module gating** properly isolates legal features from accounting-profile tenants
5. **Multi-vertical coexistence** — a legal tenant and an accounting tenant operating
   simultaneously without data leaks or module interference

**Core question**: If Moyo & Dlamini Attorneys provisioned a legal-za tenant today, would the
court calendar track their hearings correctly, would the conflict check catch a conflict with
an opposing party, and would they be able to bill using LSSA tariff rates — all without
affecting the Thornton & Associates accounting-za tenant running alongside it?

## 2. Scope

### In Scope

| Track | Description | Method |
|-------|-------------|--------|
| T0 | Data preparation — provision legal tenant, seed matters/clients/adverse parties | Automated (Playwright UI + API) |
| T1 | Court calendar — CRUD, state machine, filtering, calendar view | Automated (Playwright) |
| T2 | Prescription tracker — create, calculate dates, interrupt, expiry warnings | Automated (Playwright) |
| T3 | Adverse party registry — CRUD, linking to matters, fuzzy search | Automated (Playwright) |
| T4 | Conflict check — perform checks, verify matching algorithm, resolve conflicts | Automated (Playwright + API) |
| T5 | LSSA tariff — browse seeded schedules, search items, clone, manage custom schedules | Automated (Playwright) |
| T6 | Invoice tariff integration — add tariff line items to invoices, verify amounts | Automated (Playwright) |
| T7 | Module gating — verify legal modules hidden for accounting tenant | Automated (Playwright) |
| T8 | Matter detail — court dates tab, adverse parties tab on project pages | Automated (Playwright) |
| T9 | Notifications & reminders — court date reminders, prescription warnings | Automated (API + Mailpit) |
| T10 | Multi-vertical coexistence — both profiles operating simultaneously | Automated (Playwright + API) |

### Out of Scope

- Trust accounting (stub only — tested separately in Phase 60)
- Template/document content verification (covered by Phase 49 test plan)
- Basic CRUD for customers, projects, invoices (proven in prior phases)
- Backend unit/integration tests (96 existing tests — this plan covers E2E only)
- Performance / load testing
- Court system integrations (CaseLines, e-filing — not built)

## 3. Prerequisites

### 3.1 Keycloak Dev Stack

Start infrastructure and local services per `qa/keycloak-e2e-guide.md`, then run `keycloak-bootstrap.sh`.

Verify: frontend (3000), backend (8080), gateway (8443), keycloak (8180), postgres (5432), localstack (4566), mailpit (8025).

### 3.2 Two Tenants Required

This test plan requires **two tenants** running simultaneously:

| Tenant | Profile | Purpose |
|--------|---------|---------|
| Moyo & Dlamini Attorneys | `legal-za` | Primary test tenant — all legal module testing |
| Thornton & Associates | `accounting-za` | Coexistence control — verify legal modules are hidden |

**Provisioning**: Use the platform admin panel or demo provisioning endpoint
(`POST /internal/demo/provision`) with the appropriate `profileId`. If demo tenants
already exist from prior QA cycles, verify they're active and have the correct profiles.

### 3.3 Test Data — Moyo & Dlamini Attorneys

The following data must be seeded during Track 0. These are the "source of truth" values
that all subsequent tracks verify against.

**Firm context**: Small SA law firm, 3 fee earners. Handles litigation, commercial, and
family law. Based in Johannesburg.

**Clients (Customers)**:

| Client | Entity Type | ID Number | Reg Number | Matters |
|--------|-------------|-----------|------------|---------|
| Sipho Mabena | INDIVIDUAL | 8501015800089 | — | Mabena v Road Accident Fund |
| Kagiso Mining (Pty) Ltd | COMPANY | — | 2018/234567/07 | Mining Rights Application |
| Nkosi Family Trust | TRUST | — | IT456/2020 | Nkosi Estate Administration |
| Precious Modise | INDIVIDUAL | 9203025800184 | — | Modise Divorce Proceedings |

**Matters (Projects)**:

| Matter | Client | Type | Status |
|--------|--------|------|--------|
| Mabena v Road Accident Fund | Sipho Mabena | Litigation | ACTIVE |
| Mining Rights Application — Kagiso Mining | Kagiso Mining | Commercial | ACTIVE |
| Nkosi Estate Administration | Nkosi Family Trust | Estates | ACTIVE |
| Modise Divorce Proceedings | Precious Modise | Family Law | ACTIVE |

**Adverse Parties** (seeded for conflict check testing):

| Name | Type | ID Number | Reg Number | Aliases | Linked Matter | Relationship |
|------|------|-----------|------------|---------|---------------|-------------|
| Road Accident Fund | GOVERNMENT | — | — | RAF | Mabena v RAF | OPPOSING_PARTY |
| BHP Minerals SA (Pty) Ltd | COMPANY | — | 2015/987654/07 | BHP, BHP Billiton SA | Mining Rights | OPPOSING_PARTY |
| Thandi Modise | INDIVIDUAL | 9205085800185 | — | T. Modise | Modise Divorce | OPPOSING_PARTY |
| James Nkosi | INDIVIDUAL | 7801015800082 | — | J.P. Nkosi | Nkosi Estate | RELATED_ENTITY |

**Court Dates** (seeded for calendar testing):

| Matter | Type | Date | Court | Status |
|--------|------|------|-------|--------|
| Mabena v RAF | TRIAL | 2026-05-15 | Johannesburg High Court | SCHEDULED |
| Mabena v RAF | PRE_TRIAL | 2026-04-10 | Johannesburg High Court | SCHEDULED |
| Mining Rights | HEARING | 2026-04-25 | Department of Mineral Resources | SCHEDULED |
| Modise Divorce | MEDIATION | 2026-04-18 | Family Advocate, Johannesburg | SCHEDULED |
| Modise Divorce | CASE_MANAGEMENT | 2026-03-20 | Johannesburg High Court | HEARD |

**Prescription Trackers**:

| Matter | Cause of Action Date | Type | Expected Prescription Date |
|--------|---------------------|------|---------------------------|
| Mabena v RAF | 2024-06-15 | DELICT_3Y | 2027-06-15 |
| Mining Rights | 2023-01-10 | GENERAL_3Y | 2026-01-10 (EXPIRED — past due) |

### 3.4 Notation

- [ ] **PASS** — feature works correctly
- [ ] **FAIL** — feature is broken or produces wrong results
- [ ] **PARTIAL** — works but with issues (note specifics)
- [ ] **BLOCKED** — cannot test due to dependency failure
- [ ] **N/A** — feature doesn't exist or isn't applicable

---

## 4. Test Tracks

---

### Track 0 — Data Preparation

**Goal**: Provision the legal-za tenant and seed all test data. This creates the foundation
for all subsequent tracks.

**Method**: Platform admin provisioning + Playwright UI for entity creation + API for bulk seeding.

#### T0.1 — Provision Legal Tenant

**Actor**: Platform Admin

- [ ] **T0.1.1** Navigate to platform admin panel → Demo Provisioning (or use API: `POST /internal/demo/provision`)
- [ ] **T0.1.2** Provision tenant with `profileId: "legal-za"`, org name: "Moyo & Dlamini Attorneys"
- [ ] **T0.1.3** Verify provisioning completes: schema created, Keycloak org created, admin user created
- [ ] **T0.1.4** Verify subscription status = ACTIVE (or PILOT)
- [ ] **T0.1.5** Log in as the legal tenant admin user → verify dashboard loads
- [ ] **T0.1.6** Verify sidebar shows legal nav items: "Court Calendar", "Conflict Check", "Tariff Schedules"
- [ ] **T0.1.7** Verify sidebar does NOT show accounting-specific items: "Deadlines", "Filing Status"

#### T0.2 — Verify Legal Packs Seeded

**Actor**: Alice (legal tenant admin)

- [ ] **T0.2.1** Navigate to Settings > Custom Fields → verify legal-za field packs loaded
  - Customer fields: `client_type`, `id_passport_number`, `registration_number`, `physical_address`, `postal_address`, `preferred_correspondence`, `referred_by`
- [ ] **T0.2.2** Navigate to Settings > Clause Library → verify legal-za clause packs loaded
  - Expected clauses: Engagement Letter General, Engagement Letter Litigation, Letter of Demand, Notice of Motion, Power of Attorney (at least 5)
- [ ] **T0.2.3** Navigate to Settings > Templates → verify legal-za template pack loaded
- [ ] **T0.2.4** Navigate to Tariff Schedules → verify system schedules seeded (LSSA 2024-2025)
- [ ] **T0.2.5** Navigate to Settings > Compliance → verify FICA KYC pack loaded

**STOP GATE**: If T0.2.1 or T0.2.4 fails (packs not seeded), investigate before proceeding.
Legal modules depend on seed data being present.

#### T0.3 — Create Clients

**Actor**: Alice

For each client in the test data table (Section 3.3):

- [ ] **T0.3.1** Create client "Sipho Mabena":
  - Name = "Sipho Mabena"
  - Set custom field `client_type` = INDIVIDUAL
  - Set custom field `id_passport_number` = "8501015800089"
  - Save → verify client appears in list
- [ ] **T0.3.2** Create client "Kagiso Mining (Pty) Ltd":
  - Name = "Kagiso Mining (Pty) Ltd"
  - Set custom field `client_type` = COMPANY
  - Set custom field `registration_number` = "2018/234567/07"
  - Save → verify
- [ ] **T0.3.3** Create client "Nkosi Family Trust":
  - Name = "Nkosi Family Trust"
  - Set custom field `client_type` = TRUST
  - Set custom field `registration_number` = "IT456/2020"
  - Save → verify
- [ ] **T0.3.4** Create client "Precious Modise":
  - Name = "Precious Modise"
  - Set custom field `client_type` = INDIVIDUAL
  - Set custom field `id_passport_number` = "9203025800184"
  - Save → verify
- [ ] **T0.3.5** Complete FICA checklists for all 4 clients → transition to ACTIVE status

#### T0.4 — Create Matters (Projects)

**Actor**: Alice

- [ ] **T0.4.1** Create project "Mabena v Road Accident Fund" linked to Sipho Mabena
- [ ] **T0.4.2** Create project "Mining Rights Application — Kagiso Mining" linked to Kagiso Mining
- [ ] **T0.4.3** Create project "Nkosi Estate Administration" linked to Nkosi Family Trust
- [ ] **T0.4.4** Create project "Modise Divorce Proceedings" linked to Precious Modise
- [ ] **T0.4.5** Verify all 4 projects appear in project list
- [ ] **T0.4.6** Verify terminology: UI should say "Matters" (not "Projects") due to legal-za terminology overrides

#### T0.5 — Seed Adverse Parties

**Actor**: Alice

- [ ] **T0.5.1** Navigate to Conflict Check > Adverse Parties (or sidebar nav for adverse parties)
- [ ] **T0.5.2** Create adverse party "Road Accident Fund":
  - Name = "Road Accident Fund", Type = GOVERNMENT, Aliases = "RAF"
  - Link to matter "Mabena v RAF", Relationship = OPPOSING_PARTY, Client = Sipho Mabena
- [ ] **T0.5.3** Create adverse party "BHP Minerals SA (Pty) Ltd":
  - Name = "BHP Minerals SA (Pty) Ltd", Type = COMPANY, Reg Number = "2015/987654/07", Aliases = "BHP, BHP Billiton SA"
  - Link to matter "Mining Rights Application", Relationship = OPPOSING_PARTY, Client = Kagiso Mining
- [ ] **T0.5.4** Create adverse party "Thandi Modise":
  - Name = "Thandi Modise", Type = INDIVIDUAL, ID Number = "9205085800185", Aliases = "T. Modise"
  - Link to matter "Modise Divorce", Relationship = OPPOSING_PARTY, Client = Precious Modise
- [ ] **T0.5.5** Create adverse party "James Nkosi":
  - Name = "James Nkosi", Type = INDIVIDUAL, ID Number = "7801015800082", Aliases = "J.P. Nkosi"
  - Link to matter "Nkosi Estate", Relationship = RELATED_ENTITY, Client = Nkosi Family Trust
- [ ] **T0.5.6** Verify all 4 adverse parties appear in the adverse party list

#### T0.6 — Seed Court Dates

**Actor**: Alice

- [ ] **T0.6.1** Navigate to Court Calendar
- [ ] **T0.6.2** Create court date for "Mabena v RAF":
  - Type = TRIAL, Date = 2026-05-15, Court = "Johannesburg High Court", Court Reference = "2026/12345", Judge = "Molemela J", Description = "Trial — quantum hearing"
- [ ] **T0.6.3** Create court date for "Mabena v RAF":
  - Type = PRE_TRIAL, Date = 2026-04-10, Court = "Johannesburg High Court", Court Reference = "2026/12345", Description = "Pre-trial conference — heads of argument"
- [ ] **T0.6.4** Create court date for "Mining Rights Application":
  - Type = HEARING, Date = 2026-04-25, Court = "Department of Mineral Resources", Description = "Section 22 mining right hearing"
- [ ] **T0.6.5** Create court date for "Modise Divorce":
  - Type = MEDIATION, Date = 2026-04-18, Court = "Family Advocate, Johannesburg", Description = "Mediation — property and custody"
- [ ] **T0.6.6** Verify all 4 court dates appear in the calendar list view

#### T0.7 — Seed Prescription Trackers

**Actor**: Alice

- [ ] **T0.7.1** Create prescription tracker for "Mabena v RAF":
  - Cause of Action Date = 2024-06-15, Type = DELICT_3Y
  - Verify calculated prescription date = 2027-06-15
  - Verify status = RUNNING
- [ ] **T0.7.2** Create prescription tracker for "Mining Rights Application":
  - Cause of Action Date = 2023-01-10, Type = GENERAL_3Y
  - Verify calculated prescription date = 2026-01-10
  - Verify status: should show as EXPIRED or WARNED (prescription date is in the past)

#### T0.8 — Verify Accounting Tenant Exists

- [ ] **T0.8.1** Verify Thornton & Associates (accounting-za) tenant is still active
- [ ] **T0.8.2** Log in as Thornton admin → verify dashboard loads
- [ ] **T0.8.3** Verify sidebar shows "Deadlines" (accounting module) but NOT "Court Calendar"

#### T0.9 — Data Readiness Checkpoint

- [ ] **T0.9.1** Legal tenant: 4 clients visible in client list
- [ ] **T0.9.2** Legal tenant: 4 matters visible in project list
- [ ] **T0.9.3** Legal tenant: 4 adverse parties in adverse party list
- [ ] **T0.9.4** Legal tenant: 4 court dates in court calendar
- [ ] **T0.9.5** Legal tenant: 2 prescription trackers visible
- [ ] **T0.9.6** Legal tenant: Tariff schedules seeded (at least 1 system schedule with items)
- [ ] **T0.9.7** Accounting tenant: Dashboard loads, no legal nav items visible

**STOP GATE**: If any T0.9.x fails, fix before proceeding.

---

### Track 1 — Court Calendar

**Goal**: Verify the full court date lifecycle — create, view, postpone, cancel, record outcome —
and that the calendar view, list view, and filtering all work correctly.

#### T1.1 — Court Date List & Filtering

**Actor**: Alice (legal tenant)

- [ ] **T1.1.1** Navigate to Court Calendar → List View
- [ ] **T1.1.2** Verify all 4 seeded court dates are visible
- [ ] **T1.1.3** Verify columns display correctly: Matter, Client, Type, Date, Court, Status
- [ ] **T1.1.4** Verify status badges: all 4 show "SCHEDULED" badge
- [ ] **T1.1.5** Filter by Type = TRIAL → verify only "Mabena v RAF — Trial" shows
- [ ] **T1.1.6** Clear filter → filter by Client = "Precious Modise" → verify only "Modise Divorce" entries show
- [ ] **T1.1.7** Filter by date range: from 2026-04-01 to 2026-04-30 → verify 3 results (excludes the May trial)
- [ ] **T1.1.8** Clear all filters → verify all 4 dates show again

#### T1.2 — Court Date Calendar View

- [ ] **T1.2.1** Switch to Calendar View (if month grid exists)
- [ ] **T1.2.2** Navigate to April 2026
- [ ] **T1.2.3** Verify markers on: April 10 (PRE_TRIAL), April 18 (MEDIATION), April 25 (HEARING)
- [ ] **T1.2.4** Navigate to May 2026
- [ ] **T1.2.5** Verify marker on May 15 (TRIAL)
- [ ] **T1.2.6** Click a date marker → verify detail popup/panel shows court date info

#### T1.3 — Create New Court Date

**Actor**: Alice

- [ ] **T1.3.1** Click "New Court Date" (or equivalent button)
- [ ] **T1.3.2** Fill form:
  - Matter = "Nkosi Estate Administration"
  - Type = TAXATION
  - Date = 2026-05-20
  - Time = 10:00
  - Court = "Master of the High Court, Johannesburg"
  - Court Reference = "DE789/2020"
  - Description = "Taxation of estate costs"
  - Reminder Days = 14
- [ ] **T1.3.3** Submit → verify success
- [ ] **T1.3.4** Verify new court date appears in list with status = SCHEDULED
- [ ] **T1.3.5** Click on the new court date → verify all entered fields display correctly
- [ ] **T1.3.6** Verify client auto-resolved: "Nkosi Family Trust" (derived from matter linkage)

#### T1.4 — Postpone Court Date

**Actor**: Alice

- [ ] **T1.4.1** Open the PRE_TRIAL court date for Mabena v RAF (2026-04-10)
- [ ] **T1.4.2** Click "Postpone" action
- [ ] **T1.4.3** Fill: New Date = 2026-04-17, Reason = "Counsel unavailable — briefing conflict"
- [ ] **T1.4.4** Submit → verify status changes to POSTPONED
- [ ] **T1.4.5** Verify the date now shows 2026-04-17 (not 2026-04-10)
- [ ] **T1.4.6** Verify the postponement reason is visible in the detail view

#### T1.5 — Cancel Court Date

- [ ] **T1.5.1** Open the MEDIATION court date for Modise Divorce (2026-04-18)
- [ ] **T1.5.2** Click "Cancel" action
- [ ] **T1.5.3** Fill: Reason = "Parties reached settlement agreement"
- [ ] **T1.5.4** Submit → verify status changes to CANCELLED
- [ ] **T1.5.5** Verify the cancellation reason is visible
- [ ] **T1.5.6** Verify the cancelled court date is still visible in the list (not deleted)
- [ ] **T1.5.7** Verify no "Edit", "Postpone", or "Outcome" actions are available (CANCELLED is terminal)

#### T1.6 — Record Outcome

- [ ] **T1.6.1** Open the HEARING court date for Mining Rights (2026-04-25)
- [ ] **T1.6.2** Click "Record Outcome" action
- [ ] **T1.6.3** Fill: Outcome = "Application granted subject to environmental impact assessment conditions"
- [ ] **T1.6.4** Submit → verify status changes to HEARD
- [ ] **T1.6.5** Verify outcome text is visible in detail view
- [ ] **T1.6.6** Verify no "Edit", "Postpone", or "Cancel" actions are available (HEARD is terminal)

#### T1.7 — State Machine Edge Cases

- [ ] **T1.7.1** Attempt to postpone the CANCELLED mediation (T1.5) → verify action is unavailable or returns error
- [ ] **T1.7.2** Attempt to record outcome on the CANCELLED mediation → verify blocked
- [ ] **T1.7.3** Attempt to cancel the HEARD mining hearing (T1.6) → verify blocked
- [ ] **T1.7.4** The POSTPONED pre-trial (T1.4) should still allow: Edit, Cancel, Record Outcome
- [ ] **T1.7.5** Edit the POSTPONED pre-trial → change description to "Amended pre-trial — include expert report" → verify saved

#### T1.8 — Multiple Court Dates Per Matter

- [ ] **T1.8.1** Mabena v RAF already has 2 court dates (TRIAL + PRE_TRIAL)
- [ ] **T1.8.2** Create a 3rd: Type = CASE_MANAGEMENT, Date = 2026-04-05, Court = "Johannesburg High Court", Description = "Case management — set trial timetable"
- [ ] **T1.8.3** Verify all 3 court dates for Mabena v RAF are visible
- [ ] **T1.8.4** Filter by matter = "Mabena v Road Accident Fund" → verify exactly 3 results

---

### Track 2 — Prescription Tracker

**Goal**: Verify prescription date calculation (Prescription Act 68 of 1969), status
tracking, interruption flow, and warning indicators.

#### T2.1 — Prescription List & Status

**Actor**: Alice (legal tenant)

- [ ] **T2.1.1** Navigate to Court Calendar → Prescription View (or separate tab)
- [ ] **T2.1.2** Verify 2 seeded trackers are visible
- [ ] **T2.1.3** Mabena tracker: Type = DELICT_3Y, Prescription Date = 2027-06-15, Status = RUNNING
  - Days remaining: ~438 days (positive, green indicator)
- [ ] **T2.1.4** Mining Rights tracker: Type = GENERAL_3Y, Prescription Date = 2026-01-10
  - This date is in the past → Status should be WARNED or EXPIRED
  - Days remaining: negative number (red indicator)
- [ ] **T2.1.5** Verify sorting: expired/warned trackers should appear first (most urgent)

#### T2.2 — Create Prescription Tracker with Different Types

- [ ] **T2.2.1** Create tracker for "Nkosi Estate Administration":
  - Cause of Action Date = 2022-03-01, Type = MORTGAGE_30Y
  - Verify calculated prescription date = 2052-03-01 (30 years)
  - Verify status = RUNNING
- [ ] **T2.2.2** Create tracker for "Modise Divorce":
  - Cause of Action Date = 2025-06-01, Type = GENERAL_3Y
  - Verify calculated prescription date = 2028-06-01
  - Verify status = RUNNING
- [ ] **T2.2.3** Create tracker with CUSTOM type:
  - Matter = "Mabena v RAF", Cause of Action Date = 2024-01-01, Type = CUSTOM, Custom Years = 5
  - Verify calculated prescription date = 2029-01-01
  - Verify status = RUNNING

#### T2.3 — Interrupt Prescription

- [ ] **T2.3.1** Open the Mabena DELICT_3Y tracker (prescription date 2027-06-15)
- [ ] **T2.3.2** Click "Interrupt" action
- [ ] **T2.3.3** Fill: Interruption Date = 2025-12-01, Reason = "Service of combined summons on defendant"
- [ ] **T2.3.4** Submit → verify status changes to INTERRUPTED
- [ ] **T2.3.5** Verify interruption date and reason are visible in detail
- [ ] **T2.3.6** Verify no further actions available on an INTERRUPTED tracker (terminal state)

#### T2.4 — Prescription Edge Cases

- [ ] **T2.4.1** Attempt to create tracker with CUSTOM type and Custom Years = 0 → verify validation error
- [ ] **T2.4.2** Attempt to create tracker with CUSTOM type without Custom Years → verify validation error
- [ ] **T2.4.3** Attempt to interrupt the already-INTERRUPTED Mabena tracker → verify blocked
- [ ] **T2.4.4** Attempt to interrupt the EXPIRED/WARNED Mining Rights tracker → verify blocked (past due)

#### T2.5 — Prescription Warnings in Upcoming View

- [ ] **T2.5.1** Navigate to Court Calendar → Upcoming view
- [ ] **T2.5.2** Verify upcoming section includes prescription warnings
- [ ] **T2.5.3** Expired Mining Rights tracker should appear with urgent/red styling
- [ ] **T2.5.4** Verify court dates and prescription warnings are combined in the upcoming view

---

### Track 3 — Adverse Party Registry

**Goal**: Verify CRUD operations for the adverse party registry, linking to matters,
fuzzy name search, and delete protection when links exist.

#### T3.1 — Adverse Party List

**Actor**: Alice (legal tenant)

- [ ] **T3.1.1** Navigate to Conflict Check > Adverse Parties
- [ ] **T3.1.2** Verify all 4 seeded adverse parties are visible
- [ ] **T3.1.3** Verify columns: Name, Type, ID Number, Reg Number, Linked Matters

#### T3.2 — Search Adverse Parties

- [ ] **T3.2.1** Type "BHP" in search → verify "BHP Minerals SA (Pty) Ltd" appears
- [ ] **T3.2.2** Type "Modise" → verify "Thandi Modise" appears
- [ ] **T3.2.3** Type "RAF" → verify "Road Accident Fund" appears (matches alias)
- [ ] **T3.2.4** Type "Billiton" → verify "BHP Minerals SA (Pty) Ltd" appears (matches alias "BHP Billiton SA")
- [ ] **T3.2.5** Type "zzzznonexistent" → verify empty state shown

#### T3.3 — Create New Adverse Party

- [ ] **T3.3.1** Click "New Adverse Party"
- [ ] **T3.3.2** Fill: Name = "Anglo Gold Ashanti Ltd", Type = COMPANY, Reg Number = "2004/031746/06", Aliases = "AngloGold, AGA"
- [ ] **T3.3.3** Submit → verify party appears in list
- [ ] **T3.3.4** Verify all entered fields display correctly in detail view

#### T3.4 — Edit Adverse Party

- [ ] **T3.4.1** Open "Anglo Gold Ashanti Ltd" → click Edit
- [ ] **T3.4.2** Change Aliases to "AngloGold, AGA, Ashanti Gold"
- [ ] **T3.4.3** Save → verify updated aliases display
- [ ] **T3.4.4** Search "Ashanti" → verify "Anglo Gold Ashanti Ltd" appears (new alias works)

#### T3.5 — Link Adverse Party to Matter

- [ ] **T3.5.1** Open "Anglo Gold Ashanti Ltd" → add link
- [ ] **T3.5.2** Link to matter "Mining Rights Application", Relationship = OPPOSING_PARTY, Client = Kagiso Mining
- [ ] **T3.5.3** Verify link appears on the adverse party detail
- [ ] **T3.5.4** Note: "Mining Rights Application" now has TWO adverse parties: BHP Minerals and Anglo Gold

#### T3.6 — Multiple Links Same Party

- [ ] **T3.6.1** Link "James Nkosi" to a second matter: "Modise Divorce", Relationship = WITNESS
- [ ] **T3.6.2** Verify James Nkosi shows 2 linked matters (Nkosi Estate + Modise Divorce)
- [ ] **T3.6.3** Verify each link has the correct relationship badge

#### T3.7 — Delete Protection

- [ ] **T3.7.1** Attempt to delete "Road Accident Fund" (has 1 active link) → verify blocked with error message
- [ ] **T3.7.2** Error message should indicate active links exist
- [ ] **T3.7.3** Delete "Anglo Gold Ashanti Ltd" link first → then delete the party itself → verify success

#### T3.8 — All Party Types

- [ ] **T3.8.1** Verify the party type dropdown includes all types: INDIVIDUAL, COMPANY, TRUST, CLOSE_CORPORATION, PARTNERSHIP, ESTATE, GOVERNMENT
- [ ] **T3.8.2** Existing data covers: GOVERNMENT (RAF), COMPANY (BHP), INDIVIDUAL (Thandi, James)
- [ ] **T3.8.3** Create a CLOSE_CORPORATION party: "Van Der Merwe CC", Reg Number = "CK2010/123456/23"
- [ ] **T3.8.4** Create a PARTNERSHIP party: "Smith & Associates"
- [ ] **T3.8.5** Verify type badges render correctly for all types

---

### Track 4 — Conflict Check

**Goal**: Test the full conflict detection pipeline — exact ID match, exact registration
number match, fuzzy name match, alias match — and the resolution workflow.

#### T4.1 — Conflict Check: Exact ID Number Match

**Actor**: Alice (legal tenant)

- [ ] **T4.1.1** Navigate to Conflict Check
- [ ] **T4.1.2** Click "New Conflict Check"
- [ ] **T4.1.3** Fill:
  - Name = "Thandi Modise"
  - ID Number = "9205085800185"
  - Check Type = NEW_MATTER
- [ ] **T4.1.4** Submit → verify result = **CONFLICT_FOUND**
- [ ] **T4.1.5** Verify conflict details show:
  - Matched adverse party: "Thandi Modise"
  - Match type: exact ID number
  - Linked matter: "Modise Divorce Proceedings"
  - Relationship: OPPOSING_PARTY
- [ ] **T4.1.6** Verify result is displayed with red/danger styling

#### T4.2 — Conflict Check: Exact Registration Number Match

- [ ] **T4.2.1** Perform new conflict check:
  - Name = "BHP SA Mining"
  - Registration Number = "2015/987654/07"
  - Check Type = NEW_CLIENT
- [ ] **T4.2.2** Verify result = **CONFLICT_FOUND**
- [ ] **T4.2.3** Conflict details show: matched "BHP Minerals SA (Pty) Ltd" via registration number
- [ ] **T4.2.4** Linked matter: "Mining Rights Application"

#### T4.3 — Conflict Check: Fuzzy Name Match

- [ ] **T4.3.1** Perform new conflict check:
  - Name = "Road Accident Fund SA" (slightly different from seeded "Road Accident Fund")
  - Check Type = NEW_MATTER
- [ ] **T4.3.2** Verify result = **CONFLICT_FOUND** or **POTENTIAL_CONFLICT** (fuzzy match via pg_trgm)
- [ ] **T4.3.3** Conflict details reference "Road Accident Fund"
- [ ] **T4.3.4** Match type should indicate name similarity (not exact ID)

#### T4.4 — Conflict Check: Alias Match

- [ ] **T4.4.1** Perform new conflict check:
  - Name = "BHP Billiton"
  - Check Type = NEW_MATTER
- [ ] **T4.4.2** Verify result finds a match via alias ("BHP Billiton SA" is an alias of BHP Minerals)
- [ ] **T4.4.3** Conflict details reference "BHP Minerals SA (Pty) Ltd"

#### T4.5 — Conflict Check: No Conflict

- [ ] **T4.5.1** Perform new conflict check:
  - Name = "Shoprite Holdings Ltd"
  - Registration Number = "1990/004118/06"
  - Check Type = NEW_CLIENT
- [ ] **T4.5.2** Verify result = **NO_CONFLICT**
- [ ] **T4.5.3** Verify result displayed with green/success styling
- [ ] **T4.5.4** No conflict details section visible

#### T4.6 — Conflict Check: Customer Table Cross-Check

- [ ] **T4.6.1** Perform conflict check for an existing client name:
  - Name = "Sipho Mabena"
  - ID Number = "8501015800089"
  - Check Type = PERIODIC_REVIEW
- [ ] **T4.6.2** Verify the system searches both the adverse party table AND the customer table
- [ ] **T4.6.3** If "Sipho Mabena" appears as a match from the customer table: verify the result
        correctly identifies this as an existing client (not necessarily a conflict — context matters)

#### T4.7 — Resolve Conflict

- [ ] **T4.7.1** Open the CONFLICT_FOUND result from T4.1 (Thandi Modise)
- [ ] **T4.7.2** Click "Resolve" action
- [ ] **T4.7.3** Fill: Resolution = PROCEED, Notes = "Separate matter — different cause of action. No conflict of interest."
- [ ] **T4.7.4** Submit → verify resolution saved
- [ ] **T4.7.5** Verify the check record now shows: Resolution = PROCEED, Resolved by = Alice, Resolution date = today

#### T4.8 — Resolve with Waiver

- [ ] **T4.8.1** Open the CONFLICT_FOUND result from T4.2 (BHP registration number)
- [ ] **T4.8.2** Resolve with: Resolution = WAIVER_OBTAINED, Notes = "Written waiver obtained from both clients — filed in matter"
- [ ] **T4.8.3** Verify resolution saved with WAIVER_OBTAINED status

#### T4.9 — Conflict Check History

- [ ] **T4.9.1** Navigate to Conflict Check → History/List view
- [ ] **T4.9.2** Verify all 6 conflict checks (T4.1-T4.6) appear in chronological order
- [ ] **T4.9.3** Filter by Result = CONFLICT_FOUND → verify 3 results (T4.1, T4.2, T4.3 or T4.4)
- [ ] **T4.9.4** Filter by Check Type = NEW_CLIENT → verify correct subset
- [ ] **T4.9.5** Verify "Checked By" column shows "Alice" for all entries

#### T4.10 — Conflict Check Input Validation

- [ ] **T4.10.1** Attempt conflict check with empty name → verify validation error
- [ ] **T4.10.2** Attempt conflict check with only whitespace → verify validation error
- [ ] **T4.10.3** Verify the form accepts names with special characters: "O'Brien", "van der Merwe", "Müller" — no crash or error

---

### Track 5 — LSSA Tariff Management

**Goal**: Verify tariff schedule browsing, item search, system schedule immutability,
custom schedule cloning and editing.

#### T5.1 — Browse Seeded Tariff Schedules

**Actor**: Alice (legal tenant)

- [ ] **T5.1.1** Navigate to Tariff Schedules
- [ ] **T5.1.2** Verify at least 1 system schedule exists (LSSA 2024-2025 High Court)
- [ ] **T5.1.3** Verify system schedule shows a "System" badge (indicating immutability)
- [ ] **T5.1.4** Click on the system schedule → verify items load
- [ ] **T5.1.5** Verify items are grouped by section (e.g., "Consultations", "Court Proceedings")
- [ ] **T5.1.6** Verify each item shows: Item Number, Description, Amount (ZAR), Unit

#### T5.2 — Search Tariff Items

- [ ] **T5.2.1** In the schedule detail, type a search term (e.g., "consultation") → verify filtered results
- [ ] **T5.2.2** Search for a more specific term → verify results narrow
- [ ] **T5.2.3** Search for non-existent term → verify empty state

#### T5.3 — System Schedule Immutability

- [ ] **T5.3.1** On the system schedule: verify "Edit" button is absent or disabled
- [ ] **T5.3.2** Verify "Delete" button is absent or disabled for the schedule
- [ ] **T5.3.3** Verify individual items cannot be edited (no edit action available)
- [ ] **T5.3.4** Verify individual items cannot be deleted
- [ ] **T5.3.5** If the API is called directly (PUT /api/tariff-schedules/{id}): expect 400 error

#### T5.4 — Clone Schedule

- [ ] **T5.4.1** Click "Clone" on the system schedule
- [ ] **T5.4.2** Verify clone dialog shows name pre-filled with "(Copy)" suffix
- [ ] **T5.4.3** Change name to "Moyo & Dlamini — Custom Rates 2026"
- [ ] **T5.4.4** Submit → verify new schedule appears in list
- [ ] **T5.4.5** Open the cloned schedule → verify all items were deep-copied
- [ ] **T5.4.6** Verify item count matches the original
- [ ] **T5.4.7** Verify the cloned schedule does NOT have the "System" badge (it's custom)

#### T5.5 — Edit Custom Schedule

- [ ] **T5.5.1** Open "Moyo & Dlamini — Custom Rates 2026"
- [ ] **T5.5.2** Find a consultation item → edit amount (e.g., increase by 10%)
- [ ] **T5.5.3** Verify amount saved correctly
- [ ] **T5.5.4** Add a new item: Item Number = "99.1", Section = "Custom Services", Description = "Mining rights advisory consultation", Amount = 3500.00, Unit = "Per consultation"
- [ ] **T5.5.5** Verify new item appears in the schedule
- [ ] **T5.5.6** Delete the custom item → verify removed
- [ ] **T5.5.7** Verify original system schedule items are unchanged (clone is independent)

#### T5.6 — Create Custom Schedule from Scratch

- [ ] **T5.6.1** Click "New Schedule"
- [ ] **T5.6.2** Fill: Name = "Internal Advisory Rates", Category = CIVIL, Court Level = HIGH_COURT, Effective From = 2026-01-01
- [ ] **T5.6.3** Save → verify schedule created (empty, no items)
- [ ] **T5.6.4** Add 3 items with different sections
- [ ] **T5.6.5** Verify items sort by sortOrder

---

### Track 6 — Invoice Tariff Integration

**Goal**: Verify that tariff items can be added as line items on invoices, amounts calculate
correctly, and the tariff badge distinguishes them from time-based lines.

#### T6.1 — Prepare Invoice

**Actor**: Alice (legal tenant)

- [ ] **T6.1.1** Log time entries for "Mining Rights Application" (if not already done):
  - 2 hours at the org default rate
- [ ] **T6.1.2** Create an invoice for "Mining Rights Application" → DRAFT status
- [ ] **T6.1.3** Verify invoice created with time-based line items

#### T6.2 — Add Tariff Line Item

- [ ] **T6.2.1** Open the draft invoice
- [ ] **T6.2.2** Click "Add Tariff Item" (or equivalent button)
- [ ] **T6.2.3** Verify tariff item browser/dialog opens
- [ ] **T6.2.4** Browse or search for a tariff item (e.g., "consultation")
- [ ] **T6.2.5** Select an item → verify amount pre-fills from the tariff schedule
- [ ] **T6.2.6** Set quantity = 2
- [ ] **T6.2.7** Submit → verify tariff line item added to the invoice
- [ ] **T6.2.8** Verify the line item shows:
  - Description from tariff item
  - Amount = tariff amount × quantity
  - A "TARIFF" badge (distinguishing it from TIME lines)
  - Line source = TARIFF

#### T6.3 — Tariff Line with Amount Override

- [ ] **T6.3.1** Add another tariff item to the same invoice
- [ ] **T6.3.2** Override the amount (change from tariff default to a custom amount)
- [ ] **T6.3.3** Verify the custom amount is used (not the tariff default)
- [ ] **T6.3.4** Verify the tariff item reference is preserved (for audit trail)

#### T6.4 — Tariff with Tax

- [ ] **T6.4.1** Verify the invoice calculates tax correctly on tariff lines:
  - Tariff line subtotal + Time line subtotal = Invoice subtotal
  - Tax = 15% of subtotal
  - Total = subtotal + tax
- [ ] **T6.4.2** Verify no math errors (line totals sum to subtotal)

#### T6.5 — Tariff Module Gate on Invoice

- [ ] **T6.5.1** Log in as Thornton (accounting tenant)
- [ ] **T6.5.2** Open a draft invoice → verify "Add Tariff Item" button is NOT visible
- [ ] **T6.5.3** Invoice should only show time-based and manual line item options

---

### Track 7 — Module Gating

**Goal**: Verify that legal modules are strictly isolated — visible for legal tenants,
completely hidden for accounting tenants. No leakage at the UI or API level.

#### T7.1 — Accounting Tenant: Legal Nav Hidden

**Actor**: Alice (Thornton accounting tenant)

- [ ] **T7.1.1** Log in as Thornton admin
- [ ] **T7.1.2** Verify sidebar does NOT contain: "Court Calendar", "Conflict Check", "Tariff Schedules"
- [ ] **T7.1.3** Verify sidebar DOES contain: "Deadlines" (accounting-specific module)

#### T7.2 — Accounting Tenant: Direct URL Blocked

- [ ] **T7.2.1** Navigate directly to `/legal/court-calendar` → verify access denied or redirect
- [ ] **T7.2.2** Navigate to `/legal/conflict-check` → verify blocked
- [ ] **T7.2.3** Navigate to `/legal/tariff-schedules` → verify blocked

#### T7.3 — Accounting Tenant: API Blocked

- [ ] **T7.3.1** Call `GET /api/court-dates` as Thornton tenant → verify 403 Forbidden
- [ ] **T7.3.2** Call `GET /api/conflict-checks` as Thornton tenant → verify 403 Forbidden
- [ ] **T7.3.3** Call `GET /api/tariff-schedules` as Thornton tenant → verify 403 Forbidden
- [ ] **T7.3.4** Call `POST /api/court-dates` with valid body → verify 403 (not just empty result)

#### T7.4 — Legal Tenant: Accounting Nav Hidden

**Actor**: Alice (Moyo legal tenant)

- [ ] **T7.4.1** Log in as Moyo admin
- [ ] **T7.4.2** Verify sidebar does NOT contain: "Deadlines" (accounting module)
- [ ] **T7.4.3** Navigate to accounting deadline URL (if known) → verify blocked

#### T7.5 — RBAC Within Legal Tenant

**Actor**: Member-role user (if available in legal tenant)

- [ ] **T7.5.1** Log in as a member-role user (VIEW_LEGAL only, no MANAGE_LEGAL)
- [ ] **T7.5.2** Navigate to Court Calendar → verify view works (list, detail)
- [ ] **T7.5.3** Verify "New Court Date" button is hidden or disabled (requires MANAGE_LEGAL)
- [ ] **T7.5.4** Navigate to Conflict Check → verify view works
- [ ] **T7.5.5** Verify "New Conflict Check" button is hidden or disabled
- [ ] **T7.5.6** Navigate to Tariff Schedules → verify view works
- [ ] **T7.5.7** Verify "New Schedule" and "Clone" buttons are hidden or disabled

---

### Track 8 — Matter Detail Integration

**Goal**: Verify that court dates and adverse parties appear on the matter (project)
detail page in the correct tabs.

#### T8.1 — Court Dates Tab on Matter

**Actor**: Alice (legal tenant)

- [ ] **T8.1.1** Navigate to "Mabena v Road Accident Fund" matter detail page
- [ ] **T8.1.2** Verify a "Court Dates" tab exists (module-gated: should only appear for legal tenants)
- [ ] **T8.1.3** Click "Court Dates" tab
- [ ] **T8.1.4** Verify 3 court dates are listed (TRIAL, PRE_TRIAL, CASE_MANAGEMENT — from T0.6 and T1.8)
- [ ] **T8.1.5** Verify dates are sorted chronologically
- [ ] **T8.1.6** Verify status badges: SCHEDULED, POSTPONED (from T1.4), SCHEDULED
- [ ] **T8.1.7** Verify quick action "New Court Date" is available within this tab

#### T8.2 — Prescription Tab/Section on Matter

- [ ] **T8.2.1** On the same matter detail page, verify prescription tracker(s) are visible
  - May be in the Court Dates tab or a separate section
- [ ] **T8.2.2** Verify the Mabena DELICT_3Y tracker shows (status: INTERRUPTED from T2.3)
- [ ] **T8.2.3** Verify the CUSTOM 5-year tracker shows (status: RUNNING from T2.2.3)

#### T8.3 — Adverse Parties on Matter

- [ ] **T8.3.1** Navigate to "Mining Rights Application" matter detail page
- [ ] **T8.3.2** Verify an adverse parties section or tab exists
- [ ] **T8.3.3** Verify 2 adverse parties listed: "BHP Minerals SA" and "Anglo Gold Ashanti" (if T3.5 link was kept before deletion in T3.7)
  - Note: If Anglo Gold was deleted in T3.7, only BHP should show
- [ ] **T8.3.4** Verify relationship badges (OPPOSING_PARTY)
- [ ] **T8.3.5** Verify quick action to link additional adverse parties

#### T8.4 — Accounting Tenant: No Legal Tabs

- [ ] **T8.4.1** Log in as Thornton (accounting tenant)
- [ ] **T8.4.2** Open any project detail page
- [ ] **T8.4.3** Verify "Court Dates" tab does NOT appear
- [ ] **T8.4.4** Verify adverse parties section does NOT appear

---

### Track 9 — Notifications & Reminders

**Goal**: Verify that court date reminders and prescription warnings generate
notifications correctly.

#### T9.1 — Court Date Reminder Notification

**Actor**: Alice (legal tenant)

- [ ] **T9.1.1** Verify that a court date within the reminder window (default 7 days) triggers a notification
  - If today is within 7 days of any seeded court date → check notification bell
  - If no court dates are imminent → create one for tomorrow and wait for the reminder job to run (or trigger manually via API if available)
- [ ] **T9.1.2** If notification exists: verify it references the correct court date, matter, and date
- [ ] **T9.1.3** Verify notification links to the court date detail

#### T9.2 — Prescription Warning Notification

- [ ] **T9.2.1** The Mining Rights tracker (expired) should have generated a prescription warning
- [ ] **T9.2.2** Check notifications for any PRESCRIPTION_WARNING type notifications
- [ ] **T9.2.3** If present: verify it references the correct tracker, matter, and prescription date
- [ ] **T9.2.4** If absent: note as GAP (reminder job may not have run yet — check if it needs manual trigger)

#### T9.3 — Notification Deduplication

- [ ] **T9.3.1** If the reminder job runs again (or is manually triggered): verify duplicate notifications are NOT created
- [ ] **T9.3.2** The same court date should have at most 1 reminder notification per creator

---

### Track 10 — Multi-Vertical Coexistence

**Goal**: Prove that a legal tenant and an accounting tenant can operate simultaneously
without data leaks, module interference, or shared state issues.

#### T10.1 — Data Isolation

- [ ] **T10.1.1** Log in as Moyo (legal) → note court date count (e.g., 5 dates)
- [ ] **T10.1.2** Log in as Thornton (accounting) → verify 0 court dates visible (even if navigating directly to the API)
- [ ] **T10.1.3** Create a customer in Moyo tenant → verify it does NOT appear in Thornton's customer list
- [ ] **T10.1.4** Create a customer in Thornton tenant → verify it does NOT appear in Moyo's client list

#### T10.2 — Shared Endpoints Correct Per Profile

- [ ] **T10.2.1** Both tenants: navigate to Projects → verify each sees only their own projects
- [ ] **T10.2.2** Both tenants: navigate to Invoices → verify each sees only their own invoices
- [ ] **T10.2.3** Both tenants: navigate to Dashboard → verify each gets correct dashboard data

#### T10.3 — Terminology

- [ ] **T10.3.1** Moyo (legal): verify UI says "Matters" (not "Projects") throughout
- [ ] **T10.3.2** Moyo (legal): verify UI says "Clients" (not "Customers") throughout
- [ ] **T10.3.3** Thornton (accounting): verify UI says "Projects" and "Customers"
- [ ] **T10.3.4** Switching between tenants: terminology updates immediately (no stale cache)

#### T10.4 — Pack Isolation

- [ ] **T10.4.1** Moyo (legal): Settings > Custom Fields → verify legal-za field packs
- [ ] **T10.4.2** Thornton (accounting): Settings > Custom Fields → verify accounting-za field packs
- [ ] **T10.4.3** Legal field packs should NOT appear in accounting tenant
- [ ] **T10.4.4** Accounting field packs should NOT appear in legal tenant

#### T10.5 — Tariff Isolation

- [ ] **T10.5.1** Moyo (legal): Tariff Schedules page shows LSSA schedules
- [ ] **T10.5.2** Thornton (accounting): Tariff Schedules page is inaccessible (module gated)
- [ ] **T10.5.3** Call `GET /api/tariff-schedules` as Thornton → verify 403

---

## 5. Gap Reporting Format

For each issue found, log a gap entry:

```markdown
### GAP-P55-NNN: [Short title]

**Track**: T[N].[N] — [Test case name]
**Step**: [Step number]
**Category**: state-machine-error | module-leak | data-isolation | missing-feature | search-error | ui-error
**Severity**: blocker | major | minor | cosmetic
**Description**: [What was expected vs what was found]
**Evidence**:
- Module: [court_calendar | conflict_check | lssa_tariff]
- Endpoint: [API path, if applicable]
- Expected: [expected behaviour]
- Actual: [actual behaviour]
- Screenshot: [path]
**Suggested fix**: [If obvious]
```

**Severity guide**:

| Severity | Criteria |
|----------|----------|
| blocker | Data leaks between tenants; module accessible when it shouldn't be; court date/prescription data corruption |
| major | State machine allows illegal transition; conflict check misses an exact match; tariff amounts calculate incorrectly; RBAC bypass |
| minor | Fuzzy search doesn't match a reasonable variant; UI label wrong; filter returns wrong count |
| cosmetic | Badge color wrong; sort order inconsistent; empty state message unclear |

---

## 6. Success Criteria

| Criterion | Target |
|-----------|--------|
| All court date state transitions follow the documented state machine | 100% |
| Terminal states (HEARD, CANCELLED, INTERRUPTED, EXPIRED) block further mutations | 100% |
| Prescription dates calculate correctly for all 6 types (3Y, 6Y, 30Y, DELICT, CONTRACT, CUSTOM) | 100% |
| Conflict check finds exact ID match | 100% |
| Conflict check finds exact registration number match | 100% |
| Conflict check finds fuzzy name match (reasonable threshold) | ≥80% |
| Conflict check finds alias match | 100% |
| System tariff schedules are immutable (no edit/delete at UI or API) | 100% |
| Tariff line items on invoices calculate correct amounts | 100% |
| Module gating: zero legal module visibility for accounting tenant | 100% (zero leaks) |
| Module gating: zero accounting module visibility for legal tenant | 100% (zero leaks) |
| Data isolation: zero cross-tenant data visibility | 100% |
| Terminology overrides: legal tenant uses "Matters"/"Clients" throughout | 100% |
| Zero blocker gaps | 0 |

---

## 7. Execution Notes

### Execution Order

1. **Track 0 — Data Preparation**: Provision tenants, seed all data, pass readiness checkpoint.
   **STOP if T0.9 fails.**
2. **Track 7 — Module Gating**: Run this early — if modules leak, everything else is suspect.
3. **Track 1 — Court Calendar**: Core feature with most complexity (state machine).
4. **Track 2 — Prescription Tracker**: Depends on court calendar page navigation.
5. **Track 3 — Adverse Parties**: Independent of calendar, but needed before T4.
6. **Track 4 — Conflict Check**: Depends on adverse parties being seeded (T0.5 + T3).
7. **Track 5 — LSSA Tariff**: Independent of other legal modules.
8. **Track 6 — Invoice Tariff Integration**: Depends on tariff schedules existing.
9. **Track 8 — Matter Detail Integration**: Depends on court dates and adverse parties existing.
10. **Track 9 — Notifications**: Run after court dates and prescriptions are seeded.
    May need to wait for reminder job to execute, or trigger manually.
11. **Track 10 — Multi-Vertical Coexistence**: Final validation — run last, needs both tenants.

### When to Stop

- If Track 0 fails (tenant provisioning, pack seeding) → STOP. Infrastructure broken.
- If Track 7 shows module leaks (accounting tenant can see legal pages) → STOP. Architecture
  issue must be fixed before feature testing is meaningful.
- If Track 1 state machine allows illegal transitions (e.g., cancelling a HEARD date) → log
  as blocker but continue testing other tracks (may be isolated bug).
- Otherwise: complete all tracks and produce the gap report.

### Notification Testing

The `CourtDateReminderJob` runs on a cron schedule (default 6 AM daily). For QA purposes:
- If a manual trigger endpoint exists (`POST /api/internal/jobs/court-date-reminder`): use it
- If not: create a court date with tomorrow's date and wait for the next scheduled run
- Alternatively: verify via API that notification records exist in the database after the job runs

### PDF / Screenshot Artifacts

Save evidence in:

```
qa/testplan/artifacts/phase55/
├── t1-court-calendar-list.png
├── t1-court-date-postponed.png
├── t4-conflict-found-exact-id.png
├── t4-conflict-found-fuzzy.png
├── t5-system-schedule-items.png
├── t6-invoice-tariff-line.png
├── t7-accounting-no-legal-nav.png
├── t10-legal-terminology.png
└── ...
```
