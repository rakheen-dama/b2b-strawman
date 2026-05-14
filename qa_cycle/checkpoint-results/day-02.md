# Day 2 Checkpoint Results — Legal-ZA Full Lifecycle (Keycloak)

**Date**: 2026-05-13
**Branch**: `bugfix_cycle_2026-05-13`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, keycloak :8180, mailpit :8025
**Actor**: Bob Ndlovu (Admin) — `bob@mathebula-test.local` / `<redacted>`

---

## Summary

| Checkpoint | Title | Result |
|---|---|---|
| 2.1 | Navigate to Clients -> click + New Client | PASS |
| 2.2 | Dialog shows legal-specific promoted fields for INDIVIDUAL | PASS |
| 2.3 | Fill Type=INDIVIDUAL, name, email, ID, phone, address | PASS |
| 2.4 | Submit -> client created, redirected to client detail | PASS |
| 2.5 | Click Run Conflict Check -> check runs | PASS |
| 2.6 | Result = CLEAR (No Conflict) — green confirmation | PASS |
| 2.7 | Screenshot `day-02-conflict-check-clear.png` | PASS |
| 2.8 | Click Run KYC Verification (or skip if not configured) | SKIPPED — KYC not wired (legitimate per mandate; OBS-202) |
| 2.9 | KYC verified badge on client detail | N/A — KYC not configured |
| 2.10 | Screenshot `day-02-kyc-verified.png` | N/A — KYC not configured |

---

## Step-by-step

### Authentication — Context swap to Bob

- Signed out Thandi from user menu -> Sign out.
- Navigated to `http://localhost:3000/dashboard` -> redirected to Keycloak login.
- Filled email `bob@mathebula-test.local`, then password `<redacted>`.
- Clicked Sign In -> redirected to `/org/mathebula-partners/dashboard`.
- Verified sidebar shows **Bob Ndlovu** / `bob@mathebula-test.local`.
- Console: 0 errors.

### 2.1 Navigate to Clients -> + New Client (PASS)

- Expanded **Clients** group in sidebar -> clicked **Clients** link.
- Landed on `/org/mathebula-partners/customers`.
- Empty state: "No clients yet" with heading "Clients" and count "0".
- Two "New Client" buttons visible (header + empty-state card).
- Console: 0 errors, 1 warning (unrelated).
- Evidence: via screenshot.

### 2.2 Dialog shows legal-specific promoted fields for INDIVIDUAL (PASS)

- Clicked **New Client** button in page header -> dialog opened.
- **Step 1 of 2 ("Create Client")**: Name, Type (Individual/Company/Trust, defaulting to Individual), Email, Phone, Tax Number, Notes, Address (Line 1/2, City, State/Province, Postal Code, Country with country picker), Contact (name/email/phone), Business Details (Reg Number, Entity Type, Financial Year End).
- **Step 2 of 2 ("Additional Information")**: "SA Legal — Client Details" field group is **expanded by default** (improvement from previous cycle where it was behind two nested accordions). Contains:
  - **ID / Passport Number** — "South African ID number or passport number for natural persons"
  - **Postal Address** — "Postal address if different from physical address"
  - **Preferred Correspondence** — dropdown (Email/Post/Hand Delivery)
  - **Referred By** — text field
- Scenario asked for: ID number (present), preferred contact (present as "Preferred Correspondence"), matter_type hint (N/A — matter type is on the matter form, not client form).
- Note: No generic "ID Number" field on Step 1 (previous cycle OBS-201 has been addressed — the Step 1 ID Number field has been removed). SA Legal field group auto-expands on Step 2. This is an improvement.

### 2.3 Fill form (PASS)

- **Step 1**: Name = "Sipho Dlamini", Type = Individual (default), Email = `sipho.portal@example.com`, Phone = `+27 82 555 0101`, Address Line 1 = `12 Loveday St`, City = `Johannesburg`, Postal Code = `2001`, Country = `South Africa (ZA)`.
- **Step 2**: ID / Passport Number = `<redacted-id>`.
- All fields accepted input without validation errors.

### 2.4 Submit -> client created, redirected to client detail (PASS)

- Clicked "Create Client" on Step 2.
- Dialog closed. Redirected to `/org/mathebula-partners/customers/334bf98f-9f02-4d2f-9ee8-80bbed65ea5b`.
- Client detail page shows:
  - Header: **Sipho Dlamini** with [Active] [Prospect] badges
  - Contact: `sipho.portal@example.com` / `+27 82 555 0101` / `<redacted-id>` / Created May 13, 2026 / 0 matters
  - Address: 12 Loveday St / Johannesburg, 2001 / ZA
  - Field Groups: "SA Legal — Client Details" with ID/Passport = `<redacted-id>`
  - Trust Balance: R 0,00 (No Funds)
  - Client Readiness: 67% (Projects linked: needs setup, no onboarding checklist, no required fields)
  - Document Templates: Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt
  - Tabs: Matters, Documents, Fee Notes, Mandate, Requests, Rates, Generated Docs, Financials, Trust, Audit Trail
  - Action buttons: Summarise customer activity, Change Status, Run Conflict Check, Generate Document, Export Data, Edit, Archive
- Evidence: `qa_cycle/evidence/day-02/2.4-client-detail.png`

### 2.5-2.7 Conflict Check CLEAR (PASS)

- Clicked **Run Conflict Check** on client detail header.
- Redirected to `/org/mathebula-partners/conflict-check?customerId=334bf98f-...&checkedName=Sipho+Dlamini&checkedIdNumber=<redacted-id>`.
- Form pre-populated: Name to Check = `Sipho Dlamini`, ID Number = `<redacted-id>`, Check Type = `New Client`, Client = `Sipho Dlamini`.
- Clicked **Run Conflict Check** button.
- Result card rendered immediately:
  - Green tick icon + heading **"No Conflict"**
  - Sub-line: `Checked "Sipho Dlamini" at 13/05/2026, 23:36:23`
  - Right-side badge: **"No Conflict"**
  - History tab counter incremented to (1)
- "No Conflict" is the product's CLEAR-equivalent confirmation state.
- Evidence: `qa_cycle/evidence/day-02/day-02-conflict-check-clear.png`

### 2.8-2.10 KYC Verification (SKIPPED — KYC not wired)

- Returned to Sipho's client detail page.
- Action buttons present: Summarise customer activity, Change Status, Run Conflict Check, Generate Document, Export Data, Edit, Archive.
- **No "Run KYC Verification" button** rendered anywhere on the client detail page.
- Per scenario 2.8: "if KYC adapter configured; otherwise skip and note in gap report."
- Per user mandate: KYC is a legitimate open gap.
- Filed as **OBS-202** (KYC adapter not wired) — SKIPPED, not failed.

---

## Day 2 Closing Checkpoints

- [x] Client created with INDIVIDUAL type and legal-specific fields (SA Legal field group present and populated on detail)
- [x] Conflict check CLEAR (No Conflict; no false positive hits; History tab updated)
- [ ] KYC verification — feature not wired in this build -> logged as OBS-202 (legitimate gap per user mandate)

---

## Console Errors

- **3 `error`-level entries** across the Day 2 walkthrough — all are the same 404 on `/api/assistant/invocations?contextEntityType=customer&contextEntityId=...&status=PENDING_APPROVAL&size=10`. This is a non-critical AI assistant feature endpoint that is not routed through the gateway proxy. Filed as **OBS-203** (cosmetic, non-blocking).
- No JavaScript runtime errors, no hydration mismatches, no Next.js errors.

---

## Mandate Compliance

- No SQL shortcuts. UI + browser navigation only.
- Mailpit not needed in Day 2 (no emails issued).
- No workarounds applied.

---

## Gaps Filed in Day 2

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|---|---|---|---|---|---|---|
| OBS-202 | "Run KYC Verification" button not present on client detail. KYC adapter not wired. | exempt | Product | OPEN-EXEMPT | 2 | User mandate explicitly permits KYC as unwired open gap. Will remain OPEN-EXEMPT for lifecycle duration. |
| OBS-203 | `/api/assistant/invocations` returns 404 on every client detail page load. Non-critical AI assistant feature endpoint not proxied through gateway. | nit | Dev | OPEN | 2 | 3 occurrences during Day 2. Does not block any user workflow. Consider either wiring the route or suppressing the fetch when the feature is disabled. |

---

## Created Entities

| Entity | ID | Details |
|---|---|---|
| Client (Sipho Dlamini) | `334bf98f-9f02-4d2f-9ee8-80bbed65ea5b` | INDIVIDUAL, `sipho.portal@example.com`, SA-ID `<redacted-id>`, Prospect |
| Conflict Check #1 | (auto) | No Conflict, checked at 13/05/2026 23:36:23 |

---

## Day 2 Verdict — COMPLETE

7/10 scenario checkpoints PASS, 3 SKIPPED/N/A (KYC not wired — legitimate per mandate). Day 2 closing checkpoints satisfied. Ready to advance to Day 3 (Create RAF matter, send FICA info request).
