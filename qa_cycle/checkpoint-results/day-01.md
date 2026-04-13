# Day 1 Checkpoint Results — Cycle 2026-04-13

**Executed**: 2026-04-13
**Stack**: Keycloak dev stack (localhost:3000 / 8080 / 8443 / 8180 / 8025)
**Actor**: Bob Ndlovu (Admin)

---

## Day 1 — Conflict check & client creation

| ID | Result | Evidence |
|----|--------|----------|
| 1.1 | PASS | Logged out Thandi, navigated to /dashboard, Keycloak login appeared, logged in as bob@mathebula-test.local / SecureP@ss2. Redirected to /org/mathebula-partners/dashboard. Sidebar shows "BN" avatar, "Bob Ndlovu". 0 console errors. |
| 1.2 | PASS | Navigated to Conflict Check page via sidebar Clients > Conflict Check. URL: /org/mathebula-partners/conflict-check. Page heading "Conflict Check", tabs "Run Check" and "History (0)". Form fields: Name to Check, ID Number, Registration Number, Check Type, Customer, Matter. |
| 1.3 | PASS | Searched "Sipho Dlamini" → result: "No Conflict" with green checkmark. Checked at 13/04/2026, 19:59:14. History tab updated to "(1)". |
| 1.4 | PASS | Screenshot captured: day-01-screenshot-conflict-clear.png — shows green "No Conflict" result with full form and sidebar. |
| 1.5 | PASS | Clients list page loads at /org/mathebula-partners/customers. Heading "Clients", "New Client" button. Lifecycle tabs: All, Prospect, Onboarding, Active, Dormant, Offboarding, Offboarded. Empty state: "No clients yet". |
| 1.6 | PASS | "New Client" dialog opens as 2-step dialog. Step 1 of 2: "Create Client". |
| 1.7 | PASS | Standard fields filled: Name = "Sipho Dlamini", Email = sipho.dlamini@email.co.za, Phone = +27-82-555-0101. |
| 1.8 | PASS | Promoted fields filled inline: Type = Individual (default, correct for client_type=INDIVIDUAL), ID Number = 8501015800083, Address Line 1 = "42 Commissioner St", City = Johannesburg, Postal Code = 2001, Country = South Africa (ZA). |
| 1.9 | PASS | Client created. Appears in list: Name "Sipho Dlamini", Email sipho.dlamini@email.co.za, Phone +27-82-555-0101, Lifecycle **Prospect**, Status Active, Created Apr 13, 2026. |
| 1.10 | PASS | Client detail page: lifecycle badge shows **Prospect**. Also shows "Active" status badge. |
| 1.11 | PASS | Promoted fields render inline: Address card (42 Commissioner St, Johannesburg, 2001, ZA), phone/ID in subtitle line. CustomFieldSection "SA Legal — Client Details" shows only 4 non-promoted fields (ID / Passport Number, Postal Address, Preferred Correspondence, Referred By). No duplication of promoted slugs. |

### Deferred Day 0 Checkpoints (verified during Day 1)

| ID | Result | Evidence |
|----|--------|----------|
| 0.49 | PASS | Field promotion on New Client dialog: Type (client_type), Phone, ID Number (id_passport_number), Tax Number, Address (physical_address decomposed), Contact Name/Email (primary_contact), Registration Number — all inline as native form inputs in Step 1. |
| 0.50 | PASS | Step 2 "Additional Information (4)" section contains only non-promoted fields: ID / Passport Number, Postal Address, Preferred Correspondence, Referred By. No promoted slugs duplicated. |

### Fix Verifications (from prior cycle)

| Prior GAP | Status | Evidence |
|-----------|--------|----------|
| GAP-D1-01 (client detail tabs use "Projects") | **FIXED** | Tab says "Matters" (not "Projects") |
| GAP-D1-02 (client detail tabs use "Invoices") | **FIXED** | Tab says "Fee Notes" (not "Invoices") |
| GAP-D2-01 (tax_number not in creation flow) | **FIXED** | Tax Number field present in Step 1 of Create Client dialog, labeled "(required for activation)" |
| GAP-D3-01 (No "New Matter" on client detail) | **FIXED** | "New Matter" link visible on Matters tab of client detail |

### Console Errors

- 0 JS errors after all Day 1 navigation.
- 1 hydration mismatch warning (Radix ID, cosmetic SSR issue — same as Day 0).

---

## Day 1 Verdict: PASS

All 11 checkpoints passed. Conflict check, client creation, field promotion, and terminology all working correctly. Prior cycle gaps GAP-D1-01, GAP-D1-02, GAP-D2-01, GAP-D3-01 are all verified as fixed.
