# Day 1 Checkpoint Results — Cycle 2 — 2026-04-12

**Scenario**: qa/testplan/demos/legal-za-90day-keycloak.md
**Executed by**: QA Agent, Cycle 2 Turn 1
**Dev Stack**: Keycloak dev stack (all fixes from PR #1021 on main)
**Actor**: Bob Ndlovu (Admin role)

## Checkpoints

### CP-1.01: Login as Bob
- **Result**: PASS
- **Evidence**: Navigated to `/dashboard`, redirected to KC login, entered `bob@mathebula-test.local` / `<redacted>`, redirected to `/org/mathebula-partners/dashboard`. Sidebar shows "Bob Ndlovu" and "Mathebula & Partners".
- **Gap**: —

### CP-1.02: Navigate to Conflict Check page
- **Result**: PASS
- **Evidence**: Navigated to `/org/mathebula-partners/conflict-check`. Page loads with heading "Conflict Check", subtitle "Run conflict of interest checks and review history". Form shows "Name to Check", "ID Number", "Registration Number", "Check Type" dropdown (New Client/New Matter/Periodic Review), Customer and Matter optional dropdowns. Breadcrumb: "Mathebula & Partners > Conflict Check".
- **Gap**: —

### CP-1.03: Search for "Sipho Dlamini" — verify CLEAR result
- **Result**: PASS
- **Evidence**: Entered "Sipho Dlamini" in Name field, clicked "Run Conflict Check". Result rendered: green checkmark icon, heading "No Conflict", text `Checked "Sipho Dlamini" at 12/04/2026, 14:57:04`, badge "No Conflict" in green. History tab updated to "(1)".
- **Gap**: —

### CP-1.04: Screenshot — conflict check clear result
- **Result**: PASS
- **Evidence**: Screenshot saved to `qa_cycle/screenshots/cycle-2/day01-cp1.4-conflict-check-clear.png`. Shows sidebar with legal terminology, conflict check form with "Sipho Dlamini", green "No Conflict" result banner.
- **Gap**: —

### CP-1.05: Navigate to Clients list
- **Result**: PASS
- **Evidence**: Clicked "Clients" in sidebar to expand, then "Clients" link. Navigated to `/org/mathebula-partners/customers`. Page heading "Clients" with count "0", lifecycle tabs (All, Prospect, Onboarding, Active, Dormant, Offboarding, Offboarded), "New Client" button. Sidebar Clients section shows: Clients, Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties — all legal terminology.
- **Gap**: LOW — empty state text says "No customers yet" and "Customers represent the organisations you work with" (generic term instead of "clients"). See GAP-D1-01.

### CP-1.06: Click New Client
- **Result**: PASS
- **Evidence**: Dialog opened: "Create Client" (Step 1 of 2). Form fields visible: Name, Type (Individual/Company/Trust), Email, Phone, ID Number, Notes, Address section (Line 1, Line 2, City, State/Province, Postal Code, Country), Contact section (Contact Name, Contact Email, Contact Phone), Business Details (Registration Number, Tax Number, Entity Type, Financial Year End).
- **Gap**: —

### CP-1.07: Fill standard fields
- **Result**: PASS
- **Evidence**: Filled: Name = "Sipho Dlamini", Email = `sipho.dlamini@email.co.za`, Phone = `+27 XX XXX XXXX`.
- **Gap**: —

### CP-1.08: Fill promoted fields — verify inline rendering
- **Result**: PASS
- **Evidence**: Promoted fields rendered inline in Step 1 form: Type (client_type) = "Individual", Address (physical_address) = "<redacted-street>, Johannesburg, South Africa (ZA)", ID Number (id_passport_number) = "<redacted-id>". Step 2 "Additional Information" shows non-promoted custom fields only: "SA Legal — Client Details" section (empty — all fields promoted to step 1) and "Additional Information (4)" section with: ID / Passport Number, Postal Address, Preferred Correspondence, Referred By. No duplication of promoted fields in step 2.
- **Gap**: —

### CP-1.09: Save — verify client appears in list with status PROSPECT
- **Result**: PASS
- **Evidence**: Clicked "Create Client". Client list now shows count "1". Table row: Name = "Sipho Dlamini" (linked), Email = sipho.dlamini@email.co.za, Phone = +27 XX XXX XXXX, Lifecycle = "Prospect", Status = "Active", Completeness = "N/A", Created = "Apr 12, 2026".
- **Gap**: —

### CP-1.10: Click into client detail — verify lifecycle badge shows PROSPECT
- **Result**: PASS
- **Evidence**: Clicked into Sipho Dlamini detail page. Heading "Sipho Dlamini" with two badges: "Active" and "Prospect". Subtitle: `sipho.dlamini@email.co.za`. Detail line: `+27 XX XXX XXXX · <redacted-id> · Created Apr 12, 2026 · 0 projects`. Breadcrumb: "Mathebula & Partners > Clients > Client".
- **Gap**: LOW — detail line says "0 projects" instead of "0 matters". See GAP-D1-02. Also breadcrumb terminal segment says "Client" instead of client name "Sipho Dlamini" — cosmetic, not a gap.

### CP-1.11: Verify promoted fields render inline at top, NOT in sidebar CustomFieldSection
- **Result**: PASS
- **Evidence**: Client detail page top section shows: Address card ("<redacted-street>", "Johannesburg", "ZA") and Primary Contact card ("No contact on file."). These are promoted fields rendered inline. Below, the custom fields section "SA Legal — Client Details" shows only non-promoted fields: ID / Passport Number, Postal Address, Preferred Correspondence, Referred By. No duplication.
- **Gap**: —

## D0-10 Verification (Member terminology)
- **Result**: VERIFIED
- **Evidence**: Logged in as Carol Mokoena (member role, `carol@mathebula-test.local`). Sidebar shows: org display name "Mathebula & Partners" (not slug), "Matters" (not "Projects"), "Clients" (not "Customers"), "Court Calendar", "Finance", "Team". Dashboard stats show "Active Matters", "Matter Health". No 403 errors in console for org settings fetch.
- **Status update**: GAP-D0-10 FIXED → VERIFIED

## D0-11 Verification (Custom Fields tab labels)
- **Result**: VERIFIED
- **Evidence**: Navigated to Settings > Custom Fields (`/settings/custom-fields`) as Bob (Admin). Tab labels show: "Matters" (not "Projects"), "Action Items", "Clients" (not "Customers"), "Fee Notes" (not "Invoices"). Page description: "Define custom fields and groups for matters, action items, clients, and fee notes." All legal terminology applied correctly.
- **Status update**: GAP-D0-11 SPEC_READY → VERIFIED

## Additional Observations

### Client detail page terminology gaps
- Tab bar on client detail page shows: "Projects" (should be "Matters"), "Invoices" (should be "Fee Notes"). Other tabs: Documents, Retainer, Requests, Rates, Generated Docs, Financials, Trust.
- "0 projects" text in header subtitle — should be "0 matters".
- Empty state text on Clients list says "No customers yet" — should say "No clients yet".
- These are captured as GAP-D1-01 and GAP-D1-02.

### Positive observations
- Sidebar terminology consistent for both Admin (Bob) and Member (Carol) roles — D0-10 fix confirmed
- Legal document templates visible on client detail: Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt
- Trust Balance section present with setup prompt
- Client Readiness checklist shows 67% progress with actionable setup steps
- "Ready to start onboarding?" CTA at bottom with "Start Onboarding" link
- Settings navigation uses correct terminology: "Matter Templates", "Matter Naming"
- Zero console errors during Day 1 execution (only hydration mismatch warning on Radix aria-controls, cosmetic)

## Summary
- **PASS**: 11 of 11
- **FAIL**: 0
- **PARTIAL**: 0
- **New Gaps**: 2 (both LOW)
  - GAP-D1-01: Generic "customers" text in Clients list empty state + client detail tabs ("Projects", "Invoices")
  - GAP-D1-02: Client detail header says "0 projects" instead of "0 matters"
- **D0-10**: VERIFIED (member role sees legal terminology + org display name)
- **D0-11**: VERIFIED (custom fields tabs use vertical terminology)
