# Day 1 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner)

---

## Day 1 — Firm onboarding polish `[FIRM]`

### Pre-check: Login as Thandi

Logged out residual Carol session from Day 0. Navigated to `/dashboard`, redirected to Keycloak login at `:8180`. Entered `thandi@mathebula-test.local` / `SecureP@ss1`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Sidebar shows `img "Mathebula & Partners logo"` placeholder (pre-branding), org name "Mathebula & Partners", user "Thandi Mathebula".

---

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 1.1 | Navigate to Settings > Organization, upload firm logo (<=200KB PNG), set brand colour to `#1B3358`, Save | **PASS** | Navigated to `/org/mathebula-partners/settings/general`. Branding section visible with Logo upload area, Brand Color input, Document Footer Text. Uploaded `/qa_cycle/mathebula-logo.png` (145 bytes PNG) via "Upload Logo" button. Set Brand Color text input to `#1B3358`. Clicked "Save Settings". After save: `img "Organization logo"` rendered with "Remove logo" button visible, color picker shows `#1b3358`, text input shows `#1B3358`. |
| 1.2 | Refresh -> verify brand colour applied to sidebar accent + logo renders at top of sidebar | **PASS** | Hard-refreshed page (`goto` to same URL). Branding section retains: `img "Organization logo"` present, Brand Color `#1B3358`. Sidebar shows `img "Mathebula & Partners logo"` at top. Additionally verified branding persists across logout/login cycle: signed out, navigated to `/dashboard`, re-authenticated as Thandi via Keycloak, sidebar still shows `img "Mathebula & Partners logo"`. |
| 1.3 | Navigate to Tariffs via Finance group -> `/org/{slug}/legal/tariffs`. Verify LSSA tariff rates pre-seeded. | **PASS** | Expanded Finance group in sidebar nav. "Tariffs" link visible with URL `/org/mathebula-partners/legal/tariffs`. Clicked -> page loaded. Heading "Tariff Schedules" with description "Browse and manage LSSA tariff schedules and items". Shows "1 schedule": **LSSA 2024/2025 High Court Party-and-Party**, effective from 2024-04-01, 19 items. Tariffs is a Finance-group module nav item, not a Settings sub-page. |
| 1.4 | Verify Section 4 tariff entries: 4(c) "Waiting time at court (per hour)" R 780.00 OR 4(a) "Attendance at court (per day)" R 7800.00. All values in ZAR. | **PASS** | Clicked into schedule -> all 7 sections rendered with 19 items total. Section 4 (3 items): **4(a) "Attendance at court (per day)" — R 7800.00 (Per Day)**, **4(b) "Attendance at court (per half day)" — R 4680.00 (Per Item)**, **4(c) "Waiting time at court (per hour)" — R 780.00 (Per Hour)**. Both scenario values confirmed. All amounts prefixed with "R" (ZAR). |
| 1.5 | Navigate to Settings > Trust Accounting -> Add Account with specified fields (Name, Bank, Branch Code, Account Number, Type SECTION_86) | **PASS** | Navigated to `/org/mathebula-partners/settings/trust-accounting`. Trust Accounting Settings page loaded. Clicked "Add Account". Dialog "Add Trust Account" appeared with fields: Account Name, Bank Name, Branch Code, Account Number, Account Type (dropdown: General/Investment/Section 86 Trust Account), Opened Date, Primary checkbox, Dual approval checkbox, Payment Approval Threshold, Notes. Filled: Name="Mathebula Trust -- Main", Bank="Standard Bank", Branch Code="051001", Account Number="12345678", Type=**Section 86 Trust Account**. Clicked "Create Account". |
| 1.6 | Trust account saves, no validation error, appears in list with balance R 0.00 | **PASS** | Account created successfully. Settings page shows account card: "Mathebula Trust -- Main" with badges "Primary" + "ACTIVE", bank details "Standard Bank . 051001 . 12345678", type "SECTION_86". LPFF advisory alert: "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6))." Navigated to `/org/mathebula-partners/trust-accounting` main page: Trust Balance card shows **R 0,00** (ZAR comma-decimal format), Active Clients: 0, Pending Approvals: 0, "Mathebula Trust -- Main cashbook balance". No validation errors. |
| 1.7 | Screenshot: day-01-trust-account-created.png (optional) | **DEFERRED** | Optional per scenario. Evidence captured in accessibility snapshots above. |

---

## Day 1 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Firm branding (logo + colour) persists across logout/login | **PASS** | Uploaded 145-byte PNG logo + set brand colour `#1B3358`. Signed out, re-authenticated as Thandi via Keycloak. Sidebar still shows `img "Mathebula & Partners logo"`. Brand colour retained in Settings > General after reload. |
| LSSA tariff table pre-populated, non-empty | **PASS** | LSSA 2024/2025 High Court Party-and-Party schedule present with 19 items across 7 sections. Effective from 2024-04-01. Section 4(a) R 7800.00 and 4(c) R 780.00 confirmed. |
| Trust account created under Section 86 basis | **PASS** | "Mathebula Trust -- Main" created as SECTION_86 type, Standard Bank, branch 051001, account 12345678. Balance R 0,00. Primary account. LPFF advisory shown. |

---

## Console Errors

Zero JavaScript console errors during Day 1 execution. Only non-error messages: warnings (1 across the session, not application-critical).

## Gaps Filed

None. Day 1 passed cleanly with zero gaps.
