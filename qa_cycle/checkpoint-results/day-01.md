# Day 1 Checkpoint Results — Firm Onboarding Polish

**Date**: 2026-05-21
**Actor**: Thandi Mathebula (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)
**Branch**: `bugfix_cycle_2026-05-21`

---

## Authentication

- Logged out previous session (Carol Mokoena) via Keycloak OIDC logout endpoint
- Restarted gateway to clear BFF session store
- Logged in as Thandi Mathebula (`thandi@mathebula-test.local` / `SecureP@ss1`) via Keycloak
- Verified user identity: initials "TM" in top-right, "Thandi Mathebula" in sidebar footer

---

## Checkpoint 1.1 — Upload firm logo + set brand colour

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.1a | Navigate to Settings > Organization (General) | PASS | URL: `/org/mathebula-partners/settings/general` loaded. Branding section visible with Logo upload area and Brand Color input. |
| 1.1b | Upload firm logo (any <= 200KB PNG) | PASS | Created 32x32 navy PNG with white "M" via canvas blob injection (chrome extension blocks native file_upload). Logo preview rendered in Branding section. |
| 1.1c | Set brand colour to #1B3358 (Mathebula navy) | PASS | Color input set to `#1B3358` via both color picker and text input. Swatch updated to dark navy. |
| 1.1d | Save | PASS | Clicked "Save Settings" -- green success message "Settings saved successfully." appeared. |

---

## Checkpoint 1.2 — Refresh and verify persistence

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.2a | Refresh page / navigate to dashboard | PASS | Navigated to `/org/mathebula-partners/dashboard`. |
| 1.2b | Brand colour applied to sidebar accent | PASS | Sidebar background changed to dark navy tone matching #1B3358. Text rendered in white on dark background. |
| 1.2c | Logo renders at top of sidebar | PASS | Zoomed screenshot confirms navy "M" logo icon at top-left of sidebar next to "Kazi" text. |
| 1.2d | Console errors | PASS | Zero JS console errors after navigation. |

---

## Checkpoint 1.3 — Navigate to Tariffs

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.3a | Navigate via sidebar Finance > Tariffs | PASS | Expanded FINANCE group in sidebar. "Tariffs" visible at bottom of Finance group nav. Clicked to navigate to `/org/mathebula-partners/legal/tariffs`. |
| 1.3b | Tariffs page loads with pre-seeded data | PASS | "Tariff Schedules" page loaded. "Browse and manage LSSA tariff schedules and items". 1 schedule: "LSSA 2024/2025 High Court Party-and-Party", Effective from 2024-04-01, 19 items. |
| 1.3c | Tariffs is a Finance-group nav item (not Settings sub-page) | PASS | Confirmed: Tariffs is under FINANCE group in main sidebar, NOT under Settings. OBS-101 closed. |

---

## Checkpoint 1.4 — Verify LSSA tariff entries

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.4a | Expand LSSA 2024/2025 schedule | PASS | Clicked expand arrow. All 19 items rendered across Sections 1-4+. |
| 1.4b | Section 4(a) "Attendance at court (per day)" = R 7800.00 | PASS | Visible: `4(a) Attendance at court (per day) -- Per Day -- R 7800.00` |
| 1.4c | Section 4(c) "Waiting time at court (per hour)" = R 780.00 | PASS | Visible: `4(c) Waiting time at court (per hour) -- Per Hour -- R 780.00` |
| 1.4d | All values in ZAR (R prefix) | PASS | Every tariff item uses "R" currency prefix. Confirmed ZAR. |
| 1.4e | Section 4(b) "Attendance at court (per half day)" = R 4680.00 | PASS | Additional data point: `4(b) -- Per Item -- R 4680.00` |

**Sample tariff items observed**:
- 1(a) Instructions to sue or defend — Per Item — R 780.00
- 1(b) Consultation with client (per quarter-hour) — Per 15 min — R 780.00
- 2(a) Drawing of summons — Per Item — R 1250.00
- 2(d) Drawing of affidavit (per folio) — Per Folio — R 195.00
- 3(a) Letters written (per folio) — Per Folio — R 195.00
- 4(a) Attendance at court (per day) — Per Day — R 7800.00
- 4(b) Attendance at court (per half day) — Per Item — R 4680.00
- 4(c) Waiting time at court (per hour) — Per Hour — R 780.00

---

## Checkpoint 1.5 — Create trust account

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.5a | Navigate to Settings > Trust Accounting | PASS | URL: `/org/mathebula-partners/settings/trust-accounting`. Page shows "Trust Accounting Settings" with empty trust accounts section. |
| 1.5b | Click "+ Add Account" | PASS | "Add Trust Account" dialog opened with all required fields. |
| 1.5c | Fill form | PASS | Name: "Mathebula Trust -- Main", Bank: "Standard Bank", Branch Code: "051001", Account Number: "12345678", Type: "Section 86 Trust Account", Date: 2026/05/21, Primary: checked. |
| 1.5d | Submit (Create Account) | PASS | No validation errors. Account created. Settings page refreshed showing the new account. |

---

## Checkpoint 1.6 — Trust account saved and visible

| ID | Step | Result | Evidence |
|----|------|--------|----------|
| 1.6a | Account appears in settings list | PASS | "Mathebula Trust -- Main" with badges: Primary (green), ACTIVE (green). Type: SECTION_86. Bank details: Standard Bank, 051001, 12345678. |
| 1.6b | Section 86 info banner | PASS | Banner: "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6)). Contact the LPFF to verify." |
| 1.6c | Balance = R 0.00 | PASS | Navigated to Trust Accounting main page (`/trust-accounting`). Trust Balance card shows "R 0,00" (ZAR comma format). |
| 1.6d | Console errors | PASS | Zero JS console errors across all Day 1 navigations. |

---

## Day 1 Summary Checkpoints

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Firm branding (logo + colour) persists across navigation | PASS | Logo ("M" navy icon) renders at sidebar header. Brand colour #1B3358 applied to sidebar background. Persists after page navigation. |
| LSSA tariff table pre-populated, non-empty | PASS | 1 schedule, 19 items. LSSA 2024/2025 High Court Party-and-Party. All values in ZAR. |
| Trust account created under Section 86 basis | PASS | "Mathebula Trust -- Main", Standard Bank, 051001, 12345678. Type: SECTION_86. Balance: R 0,00. Primary + Active. |

---

## Gaps / Observations

| Gap ID | Summary | Severity | Notes |
|--------|---------|----------|-------|
| (none) | No new gaps found on Day 1 | — | All checkpoints passed cleanly. |

**Note on logo upload**: The native `file_upload` tool was blocked by the Chrome extension (OBS-ENV-01). Used canvas blob injection as workaround (created a 32x32 PNG programmatically and set it on the file input via DataTransfer API). The logo uploaded successfully and persists after page navigation.

---

**Day 1 Status**: ALL PASS (7/7 checkpoints, 0 gaps)
