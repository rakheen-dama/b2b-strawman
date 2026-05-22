# Day 2 Checkpoint Results — Onboard Sipho as Client, Conflict Check + KYC

**Date**: 2026-05-21
**Actor**: Bob Ndlovu (Admin) — authenticated via Keycloak (`bob@mathebula-test.local` / `SecureP@ss2`)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)

---

## Context Swap

| Step | Result | Evidence |
|------|--------|----------|
| Gateway restart | PASS | `svc.sh restart gateway` completed, PID rotated from 55688 → 59502 → new |
| Keycloak login as Bob | PASS | Two-step KC login (email → password), landed on `/org/mathebula-partners/dashboard`, initials "BN" in top-right avatar |
| Legal terminology active | PASS | Sidebar shows: Matters, Clients, Engagement Letters, Court Calendar, Conflict Check, Adverse Parties — no generic vocabulary |

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 2.1 | Navigate to Clients → click + New Client | PASS | Clients page at `/org/mathebula-partners/customers` rendered with "No clients yet" empty state and "+ New Client" button. Status filters visible: All, Prospect, Onboarding, Active, Dormant, Offboarding, Offboarded |
| 2.2 | Dialog shows legal-specific promoted fields for INDIVIDUAL | PASS | Step 1: Standard fields (Name, Type=Individual, Email, Phone, Tax Number, Notes, Address, Contact, Business Details). Step 2 ("Additional Information"): **"SA Legal — Client Details"** section with legal-za promoted fields: ID / Passport Number, Postal Address, Preferred Correspondence, Referred By. The Tax Number label in Step 1 dynamically changed to "Tax Number (auto-filled from ID Number; editable)" after Entity Type = Individual was selected. |
| 2.3 | Fill client form with all required fields | PASS | Filled: Name=Sipho Dlamini, Type=Individual, Email=sipho.portal@example.com, Phone=+27 82 555 0101, Address=12 Loveday St / Johannesburg / Gauteng / 2001 / ZA, Entity Type=Individual, ID/Passport Number=8501015800088 |
| 2.4 | Submit → client created, redirected to client detail | PASS | Client created at `/org/mathebula-partners/customers/d8327ceb-c66a-4305-b8be-fbda2c52f576`. Detail page shows: Sipho Dlamini, sipho.portal@example.com, +27 82 555 0101, ID 8501015800088, Created May 21 2026, 0 matters, Address card, Business Details (Entity Type: Individual), SA Legal — Client Details field group, Trust Balance R 0,00 |
| 2.5 | Click Run Conflict Check → search runs | PASS | Navigated to `/org/mathebula-partners/conflict-check?customerId=...&checkedName=Sipho+Dlamini&checkedIdNumber=8501015800088`. Form pre-filled: Name=Sipho Dlamini, ID=8501015800088, Check Type=New Client, Client=Sipho Dlamini. Clicked Run Conflict Check. |
| 2.6 | Result = CLEAR (no pre-existing records) — green confirmation | PASS | Result: **"No Conflict"** — green checkmark icon with text "Checked 'Sipho Dlamini' at 2026/05/21, 17:59:08". Green "No Conflict" badge on right side. History tab incremented to (1). |
| 2.7 | Screenshot: conflict-check-clear | N/A | Screenshot not captured (QA agent, not Playwright baseline) — result observed and documented |
| 2.8 | Click Run KYC Verification | SKIPPED | **No "Run KYC Verification" button exists on the client detail page.** Available actions are: Summarise customer activity, Change Status, Run Conflict Check, Generate Document, Export Data, Edit, Archive. KYC adapter is not configured. Scenario allows skip: "if KYC adapter configured; otherwise skip and note in gap report." |
| 2.9 | KYC adapter returns Verified | SKIPPED | KYC adapter not configured — no KYC button or surface available on client detail |
| 2.10 | Screenshot: kyc-verified | SKIPPED | N/A — KYC not available |

---

## Day 2 Summary Checkpoints

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Client created with INDIVIDUAL type and legal-specific fields | PASS | SA Legal — Client Details field group with ID/Passport Number present on Step 2 of create dialog and on client detail page |
| Conflict check CLEAR (no false positive hits) | PASS | "No Conflict" result at 2026/05/21 17:59:08 |
| KYC verification badge visible, or KYC not-configured state logged | SKIPPED | KYC adapter not configured — no Run KYC Verification button on client detail page. Logged to gap report. |

---

## Observations

1. **No separate First/Last Name fields**: The Create Client dialog uses a single "Name" field rather than separate First Name / Last Name. The scenario specified First Name=Sipho, Last Name=Dlamini — entered as "Sipho Dlamini" in the combined Name field. This is a UX choice, not a bug.

2. **Legal-specific fields in Step 2**: The scenario expected legal-specific promoted fields (ID Number, preferred contact) to appear directly in the main create dialog. They actually appear in Step 2 ("Additional Information") under the "SA Legal — Client Details" collapsible group. This is functionally correct — the fields exist and are populated.

3. **No KYC adapter**: The scenario allowed for KYC not being configured. There is no "Run KYC Verification" action available. This is a known feature gap (KYC integration not yet wired), consistent with the mandate that KYC and Payments integrations are acceptable open gaps.

4. **Console errors**: Zero JavaScript errors observed during the entire Day 2 flow.

5. **Bob's password**: The user-provided context said `Test1234!` but the actual Keycloak password is `SecureP@ss2` (from the scenario file). `Test1234!` failed with "Invalid password." Note for future reference.

---

## Gap Report Entries

| Gap ID | Summary | Severity | Notes |
|--------|---------|----------|-------|
| OBS-201 | KYC Verification not available on client detail page — no adapter configured | LOW | Expected gap per mandate (KYC integration not yet wired). Scenario explicitly allows skip. |
