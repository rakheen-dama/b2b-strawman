# Revalidation: No SQL Shortcuts

**Date**: 2026-04-13
**Cycle**: QA Cycle 2026-04-13, Turn 6 (Revalidation)
**Branch**: `bugfix_cycle_2026-04-13`
**Purpose**: Re-exercise UI/API paths that prior QA turns bypassed with SQL INSERT/UPDATE. Prove the full stack works end-to-end through proper channels.

---

## Test 1: FICA Checklist Completion via UI — PASS

**Objective**: Create a new client, transition PROSPECT → ONBOARDING, complete ALL checklist items through the UI (with document upload), and verify auto-transition to ACTIVE.

**Steps executed**:
1. Logged in as Bob (admin)
2. Created new client "Nomsa Khumalo" (Individual, email: nomsa.khumalo@email.co.za, ID: 8805120800087, tax: 4567890123, address: 42 Commissioner Street, Johannesburg, Gauteng, 2001, ZA)
3. Client created with status **PROSPECT** — confirmed in client list
4. Navigated to client detail → clicked "Start Onboarding" link
5. Status transitioned to **ONBOARDING** — confirmed on page reload
6. Onboarding tab showed **Legal Individual Client Onboarding** checklist with 9 items (8 required, 1 optional)
7. **Completed "Conflict Check Performed"** (no doc required) → Mark Complete → Confirm → status: Completed (1/9)
8. **Uploaded 6 documents** via Documents tab (id-document.pdf, proof-of-address.pdf, beneficial-ownership.pdf, source-of-funds.pdf, engagement-letter.pdf, power-of-attorney.pdf) — all showing "Uploaded" status
9. **Completed remaining items with document links** via Onboarding tab:
   - Proof of Identity → linked id-document.pdf → Completed (2/9)
   - Proof of Address → linked proof-of-address.pdf → Completed (3/9)
   - Beneficial Ownership Declaration → linked beneficial-ownership.pdf → Completed (4/9)
   - Source of Funds Declaration → linked source-of-funds.pdf → Completed (5/9)
   - Engagement Letter Signed → linked engagement-letter.pdf → Completed (6/9) — unblocked Power of Attorney
   - FICA Risk Assessment (no doc) → Completed (7/9)
   - Sanctions Screening (no doc) → Completed (8/9)
10. At 8/9 completed (8/8 required), checklist status changed to **"Completed"**
11. **AUTO-TRANSITION FIRED**: Client status automatically changed from ONBOARDING → **ACTIVE**
12. Completed optional Power of Attorney Signed → linked power-of-attorney.pdf → 9/9

**Key verifications**:
- Document upload via UI (drag-and-drop file picker) → presigned URL → S3 → confirmed
- Checklist item completion links documents correctly
- Dependency chains respected (Power of Attorney blocked until Engagement Letter done; FICA Risk Assessment & Sanctions Screening blocked until Proof of Identity done)
- Auto-transition fires when all required items complete (8/8)
- Screenshot captured: `revalidation-fica-checklist-complete.png`

**Zero SQL shortcuts used.**

---

## Test 2: Time Entry via UI (All 3 Users) — PASS

**Objective**: Log a time entry via the Log Time dialog for each of the 3 users, verifying correct rate snapshot.

### Bob Ndlovu (admin) — R 1,200.00/hr
- Navigated to Sipho Dlamini matter → Action Items tab
- Clicked "Log Time" on "Initial consultation & case assessment"
- Dialog showed: **Billing rate: R 1,200.00/hr (member default)**
- Logged 1h, description: "QA revalidation: initial consultation with client"
- Verified: Time tab shows Bob at 2h 30m (was 1h 30m), 3 entries total (was 2), new task entry visible

### Thandi Mathebula (owner) — R 2,500.00/hr
- Signed out, cleared cookies, logged in as thandi@mathebula-test.local
- Navigated to Sipho Dlamini matter → Action Items tab
- Clicked "Log Time" on "Letter of demand"
- Dialog showed: **Billing rate: R 2,500.00/hr (member default)**
- Logged 30min, description: "QA revalidation: drafting letter of demand"
- Dialog closed successfully

### Carol Mokoena (member) — R 550.00/hr
- Signed out, cleared cookies, logged in as carol@mathebula-test.local
- Navigated to Sipho Dlamini matter → Action Items tab
- Clicked "Log Time" on "Pre-trial conference preparation"
- Dialog showed: **Billing rate: R 550.00/hr (member default)**
- Logged 45min, description: "QA revalidation: pre-trial conference preparation notes"
- Dialog closed successfully

**Key verifications**:
- Each user sees their correct billing rate in the Log Time dialog
- Rate snapshots match configured rates (Thandi R2,500, Bob R1,200, Carol R550)
- Activity feed shows all 3 new time entries with correct task names and durations

**Zero SQL shortcuts used.**

---

## Test 3: Budget Creation via UI — PASS

**Objective**: Navigate to a matter's Budget tab and create a budget through the UI.

**Steps executed**:
1. Logged in as Bob
2. Navigated to Sipho Dlamini matter → `?tab=budget`
3. Budget tab showed: "No budget configured" with "Configure budget" button
4. Clicked "Configure budget" → "Set Budget" dialog opened
5. Filled: Budget Hours = 20, Budget Amount = R 30,000, Currency = ZAR (pre-filled), Alert Threshold = 80%, Notes = "QA revalidation: budget set via UI"
6. Clicked "Save Budget"
7. Budget panel now shows:
   - **Hours**: 20h budget, 5h 45m consumed, 14h 15m remaining (29% used) — On Track
   - **Amount**: R 30,000.00 budget, R 5,762.50 consumed, R 24,237.50 remaining (19% used) — On Track
   - Alert threshold: 80%
   - Notes displayed correctly

**Key verifications**:
- Budget consumption calculated correctly from existing time entries
- Status indicators (On Track) correct for both hours and amount
- ZAR currency formatting correct (R X,XXX.XX)

**Zero SQL shortcuts used.**

---

## Test 4: Adverse Party via UI — PASS

**Objective**: Create an adverse party through the UI and link it to a matter.

**Steps executed**:
1. Navigated to Legal → Adverse Parties page (`/legal/adverse-parties`)
2. Existing parties: 1 (Road Accident Fund from prior testing)
3. Clicked "Add Party" → "Add Adverse Party" dialog
4. Filled: Name = "Standard Bank of South Africa", Type = COMPANY, Registration Number = 1969/017128/06, Aliases = "Standard Bank, SBSA", Notes = "QA revalidation: defendant in Sipho Dlamini civil matter"
5. Clicked "Create Party" → party appears in list (now 2 parties)
6. Navigated to Sipho Dlamini matter → Adverse Parties tab
7. Clicked "Link Adverse Party" → dialog showed Standard Bank as available option
8. Selected: Adverse Party = Standard Bank of South Africa, Customer = Sipho Dlamini, Relationship = Opposing Party, Description = "Defendant in civil matter"
9. Clicked "Link Party" → link created successfully
10. Adverse Parties tab now shows linked party with relationship and date

**Key verifications**:
- Adverse party creation with all fields (name, type, reg number, aliases, notes)
- Linking adverse party to a matter with relationship type
- Registry page shows correct party count and details

**Zero SQL shortcuts used.**

---

## Test 5: Edit Client Fields via UI — PASS

**Objective**: Navigate to an existing client, edit fields, save, and verify persistence.

**Steps executed**:
1. Navigated to Sipho Dlamini client detail page
2. Clicked "Edit" button → "Edit Customer" dialog opened with all current values pre-filled
3. Modified fields:
   - Added stateProvince: "Gauteng"
   - Added addressLine2: "Unit 5B, Marshalltown"
   - Added contactName: "Sipho Dlamini"
   - Added contactEmail: sipho.dlamini@email.co.za
   - Added contactPhone: +27-82-555-0101
   - Updated taxNumber: "4890123456-01" (was "4890123456")
   - Added notes: "QA revalidation: client fields edited via UI..."
4. Clicked "Save Changes"
5. Verified on detail page:
   - Address now shows full: "42 Commissioner St, Unit 5B, Marshalltown, Johannesburg, Gauteng, 2001, ZA"
   - Primary Contact now shows: "Sipho Dlamini, sipho.dlamini@email.co.za, +27-82-555-0101" (was "No contact on file")
   - Tax Number updated to "4890123456-01"
   - Notes displayed correctly

**Key verifications**:
- All field types editable: text, tel, email, textarea
- Pre-filled values preserved for unchanged fields
- New values persist after save and page refresh

**Zero SQL shortcuts used.**

---

## Summary

| # | Test | Result | Notes |
|---|------|--------|-------|
| 1 | FICA Checklist Completion via UI | **PASS** | 9/9 items, doc upload + link, auto-transition ONBOARDING→ACTIVE |
| 2 | Time Entry via UI (3 users) | **PASS** | Thandi R2,500/hr, Bob R1,200/hr, Carol R550/hr — all correct |
| 3 | Budget Creation via UI | **PASS** | 20h / R30,000 budget, consumption calculated correctly |
| 4 | Adverse Party via UI | **PASS** | Created + linked to matter with relationship type |
| 5 | Edit Client Fields via UI | **PASS** | Address, contact, tax number, notes — all persisted |

**All 5 tests PASS with zero SQL shortcuts.**

### New Gaps Found
None. All tested paths work correctly through the UI.
