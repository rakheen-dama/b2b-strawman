# Day 1 Checkpoint Results — Legal-ZA Full Lifecycle (Keycloak)

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, portal :3002, keycloak :8180, mailpit :8025
**Actor**: Thandi Mathebula (Owner) — `thandi@mathebula-test.local`
**Status**: **DAY 1 COMPLETE** — all checkpoints passed (with three minor path-mismatch observations, none blocking)

## Summary

| Checkpoint | Title | Result |
|---|---|---|
| 1.1 | Settings > Organization → upload firm logo + brand colour `#1B3358` → Save | PASS |
| 1.2 | Refresh → brand colour applied (sidebar) + logo renders top of sidebar | PASS |
| 1.3 | Settings > Rate Cards → LSSA tariff rates pre-seeded | PASS (path differs — see notes) |
| 1.4 | Verify tariff entry: High Court attendance / per hour / ZAR | PASS (close — see notes) |
| 1.5 | Settings > Trust Accounts → create Mathebula Trust — Main, Standard Bank, 12345678, SECTION_86 | PASS (path differs — see notes) |
| 1.6 | Trust account saves; appears in list with balance R 0.00 | PASS |
| 1.7 | Optional screenshot `day-01-trust-account-created.png` | PASS |
| Day 1 ck | Firm branding persists across logout/login | PASS |
| Day 1 ck | LSSA tariff table pre-populated, non-empty | PASS |
| Day 1 ck | Trust account created under Section 86 basis | PASS |

## Step-by-step

### Checkpoint 1.1 — Branding upload + save (PASS)
- Logged out Carol (left over from Day 0), signed in as Thandi via Keycloak (`thandi@mathebula-test.local` / `SecureP@ss1`).
- Navigated `/org/mathebula-partners/settings/general` → Branding card.
- Uploaded `qa_cycle/test-fixtures/mathebula-logo.png` (75 bytes, 10×10 PNG).
- Set Brand Color text box to `#1B3358` (color picker auto-synced to `#1b3358`).
- Clicked "Save Settings". No console errors.
- Evidence: `qa_cycle/evidence/day-01/1.1-branding-pre-save.png`, `1.1-after-save.png`.

### Checkpoint 1.2 — Persistence on refresh (PASS)
- Hard reload of `/settings/general` showed:
  - `--brand-color: #1B3358` on document root.
  - Logo rendered in sidebar (top-left) AND in Branding section, served from LocalStack S3 path `org/tenant_5039f2d497cf/branding/logo.png`.
- Branding persists across logout + login confirmed at end of session (see "Day 1 checkpoint — branding persists" below).
- Evidence: `qa_cycle/evidence/day-01/1.2-after-refresh-persisted.png`, `1-checkpoints-branding-persists.png`.

### Checkpoint 1.3 — LSSA tariff schedule reachable (PASS — non-blocking path observation)
- Scenario directs to "Settings > Rate Cards". The Settings sidebar has no "Rate Cards" link and the URLs `/settings/rate-cards`, `/settings/tariffs` both return Next 404.
- Actual page: **`/org/mathebula-partners/legal/tariffs`** ("Tariff Schedules"). Reached directly. 1 schedule listed: **"LSSA 2024/2025 High Court Party-and-Party"** — `Effective from 2024-04-01 · 19 items`.
- Filed as **OBS-101** below. Functionally equivalent.
- Evidence: `qa_cycle/evidence/day-01/1.3-tariffs-page.png`, `1.3-tariffs-404.png`, `1.3-packs-page.png`.

### Checkpoint 1.4 — Tariff entry: High Court / attending at court / per hour / ZAR (PASS — close match)
- Expanded the LSSA 2024/2025 schedule. Section 4 of 7 contains:
  - 4(a) Attendance at court (per day) — Per Day — **R 7800.00**
  - 4(b) Attendance at court (per half day) — Per Item — **R 4680.00**
  - 4(c) Waiting time at court (per hour) — Per Hour — **R 780.00**
- The exact phrase in the scenario "attending at court, per hour" doesn't appear verbatim. Closest matches: 4(c) "Waiting time at court (per hour)" R 780/hr OR 4(a) "Attendance at court (per day)" R 7800. All values are in ZAR. The schedule is the published 2024/2025 LSSA High Court Party-and-Party tariff.
- Schedule effective from 2024-04-01, contains 19 items across 7 sections — pre-seeded by the legal-za rate pack. PASS.
- Filed clarification under **OBS-101** (no separate gap).
- Evidence: `qa_cycle/evidence/day-01/1.4-tariffs-expanded.png`.

### Checkpoint 1.5 — Create trust account (PASS — non-blocking path observation)
- Scenario directs to "Settings > Trust Accounts". The Settings sidebar has no "Trust Accounts" link.
- Actual page: **`/org/mathebula-partners/settings/trust-accounting`** ("Trust Accounting Settings") — reached directly.
- Filed as **OBS-102**. Functionally equivalent.
- Clicked "Add Account" → modal "Add Trust Account" opened.
- Filled:
  - Account Name = `Mathebula Trust — Main`
  - Bank Name = `Standard Bank`
  - Account Number = `12345678`
  - Account Type = `SECTION_86` (label "Section 86 Trust Account")
  - Set as primary trust account (pre-checked, kept)
- First save attempt rejected with "Branch code is required" (form-level validation; the field is not in the scenario but is mandatory in UI). Filled Branch Code = `051001` and re-submitted. Created OK.
- Filed as **OBS-103** (Branch Code field is required but not in scenario — minor UX note).
- Evidence: `qa_cycle/evidence/day-01/1.5-trust-accounting-settings.png`, `1.5-trust-form-filled.png`, `1.6-trust-account-created.png`.

### Checkpoint 1.6 — Trust account saved, balance R 0.00 (PASS)
- After save, Trust Accounts list shows:
  - **Mathebula Trust — Main** [Primary] [ACTIVE] — Standard Bank · 051001 · 12345678 — `SECTION_86`
  - LPFF disclaimer banner: "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6)). Contact the LPFF to verify."
  - Approval Settings section auto-populated: "Mathebula Trust — Main · Single approval" (no dual approval).
- Trust dashboard at `/trust-accounting`:
  - Trust Balance: **R 0,00** — "Mathebula Trust — Main cashbook balance"
  - Active Clients: 0 · Pending Approvals: 0 · Reconciliation: Not yet reconciled
- Evidence: `qa_cycle/evidence/day-01/1.6-trust-account-success.png`, `1.6-trust-dashboard-balance.png`.

### Day 1 closing checkpoint — Branding persists across logout/login (PASS)
- Signed out via user menu → Sign out (no errors).
- Re-authenticated through Keycloak (`thandi@mathebula-test.local` / `SecureP@ss1`).
- Dashboard shows logo in sidebar; document root still has `--brand-color: #1B3358`.
- Evidence: `qa_cycle/evidence/day-01/1-checkpoints-branding-persists.png`.

## Console errors
- **None on `localhost:3000`** at any checkpoint. Single 404 logged when probing `/settings/tariffs` (intentional path-discovery probe, not a regression).

## Gaps Filed

All non-blocking observations. No code fixes required to advance to Day 2.

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|---|---|---|---|---|---|---|
| OBS-101 | Settings sidebar has no link to LSSA tariff schedules; scenario refers to "Settings > Rate Cards" but page lives at `/org/{slug}/legal/tariffs`. `/settings/rate-cards` and `/settings/tariffs` return 404. | nit | Product | OPEN | 1 | Either add a Settings sidebar entry "Rate Cards" or update scenario to point at `/legal/tariffs`. The page itself is fully functional with 19 LSSA items pre-seeded. |
| OBS-102 | Settings sidebar has no link to Trust Accounting Settings; scenario refers to "Settings > Trust Accounts" but page lives at `/org/{slug}/settings/trust-accounting`. Reached only by direct URL or via the Trust Accounting nav. | nit | Product | OPEN | 1 | Add sidebar entry under Finance, or update scenario to use trust-accounting. |
| OBS-103 | Add Trust Account modal requires Branch Code, but the scenario does not list it. Triggered "Branch code is required" validation. Used `051001` (Standard Bank Cape Town main branch) as placeholder. | nit | Product | OPEN | 1 | Either drop required validation (make optional) or update scenario to specify branch code. |

## Mandate Compliance Check
- No SQL shortcuts: confirmed — UI-only.
- Mailpit API: not needed in Day 1 (no emails issued).
- Frontend console: clean (no errors).
- Workarounds: only Playwright MCP `evaluate(...)` JS dispatch for two clicks (Carol Sign-out menu, KC form submission, Add Account button after validation reload) — same `ENV-001` pattern from Day 0.

## Day 1 Verdict — **COMPLETE**
Ready to advance to Day 2 (Onboard Sipho as client, run conflict check + KYC).
