# Day 1 Checkpoint Results — Legal-ZA Full Lifecycle (Keycloak)

**Date**: 2026-05-13
**Branch**: `bugfix_cycle_2026-05-13`
**QA Driver**: Playwright MCP against Keycloak dev stack
**Stack**: backend :8080, gateway :8443, frontend :3000, portal :3002, keycloak :8180, mailpit :8025
**Actor**: Thandi Mathebula (Owner) — `thandi@mathebula-test.local`
**Status**: **DAY 1 COMPLETE** — all checkpoints passed, 0 gaps

## Summary

| Checkpoint | Title | Result |
|---|---|---|
| 1.1 | Settings > Organization → upload firm logo + brand colour `#1B3358` → Save | PASS |
| 1.2 | Refresh → brand colour applied (sidebar accent `--brand-color: #1B3358`) + logo renders at top of sidebar | PASS |
| 1.3 | Navigate to Tariffs via Finance sidebar group → LSSA tariff rates pre-seeded | PASS |
| 1.4 | Verify tariff entry: section 4 — 4(c) Waiting time at court R 780.00 + 4(a) Attendance at court R 7800.00 | PASS |
| 1.5 | Settings > Trust Accounting → create Mathebula Trust — Main, Standard Bank, 051001, 12345678, SECTION_86 | PASS |
| 1.6 | Trust account saves; appears in list with balance R 0,00 | PASS |
| 1.7 | Optional screenshot `day-01-trust-account-created.png` | PASS |
| Day 1 ck | Firm branding persists across logout/login | PASS |
| Day 1 ck | LSSA tariff table pre-populated, non-empty (19 items, 7 sections) | PASS |
| Day 1 ck | Trust account created under Section 86 basis | PASS |

## Step-by-step

### Checkpoint 1.1 — Branding upload + save (PASS)
- Signed out Carol (leftover from Day 0 session), re-authenticated as Thandi via Keycloak (`thandi@mathebula-test.local` / `SecureP@ss1`).
- Navigated to `/org/mathebula-partners/settings/general` → Branding card.
- Uploaded `qa_cycle/test-fixtures/mathebula-logo.png` via the "Upload Logo" button.
- Set Brand Color text input to `#1B3358` (color picker synced to `#1b3358`).
- Clicked "Save Settings". No console errors.
- Evidence: `qa_cycle/evidence/day-01/1.1-branding-saved.png`.

### Checkpoint 1.2 — Persistence on refresh (PASS)
- Hard reload of `/settings/general`:
  - `--brand-color: #1B3358` confirmed on document root via `getComputedStyle`.
  - Logo rendered in sidebar top-left: `img[alt="Mathebula & Partners logo"]` served from LocalStack S3 path `org/tenant_5039f2d497cf/branding/logo.png`.
  - Brand Color text input retains `#1B3358` value.
- Evidence: `qa_cycle/evidence/day-01/1.2-after-refresh-persisted.png`.

### Checkpoint 1.3 — Tariff schedules reachable via Finance sidebar (PASS)
- Expanded **Finance** group in main sidebar → clicked **Tariffs** link (`/org/mathebula-partners/legal/tariffs`).
- Page loaded: "Tariff Schedules — Browse and manage LSSA tariff schedules and items".
- 1 schedule listed: **"LSSA 2024/2025 High Court Party-and-Party"** — `Effective from 2024-04-01 · 19 items`.
- Note: Previous cycle (OBS-101) reported Tariffs was not in the sidebar. It is now correctly in the Finance nav group. OBS-101 is resolved.
- Evidence: `qa_cycle/evidence/day-01/1.3-tariffs-page.png`.

### Checkpoint 1.4 — Tariff entry verification (PASS)
- Expanded the LSSA 2024/2025 schedule. All 7 sections with 19 items rendered.
- Section 4 contains 3 items:
  - **4(a)** Attendance at court (per day) — Per Day — **R 7800.00**
  - **4(b)** Attendance at court (per half day) — Per Item — **R 4680.00**
  - **4(c)** Waiting time at court (per hour) — Per Hour — **R 780.00**
- Both scenario-specified values confirmed. All values in ZAR.
- Evidence: `qa_cycle/evidence/day-01/1.4-tariffs-expanded.png`.

### Checkpoint 1.5 — Create trust account (PASS)
- Navigated to `/org/mathebula-partners/settings/trust-accounting` → "Trust Accounting Settings".
- Also reachable via Settings sidebar under Finance → Trust Accounting. OBS-102 from previous cycle is resolved.
- Clicked "Add Account" → modal "Add Trust Account" opened.
- Filled:
  - Account Name = `Mathebula Trust — Main`
  - Bank Name = `Standard Bank`
  - Branch Code = `051001` (required field per OBS-103 — scenario now includes it)
  - Account Number = `12345678`
  - Account Type = `SECTION_86` (label "Section 86 Trust Account")
  - Set as primary trust account: checked (default)
- Clicked "Create Account" → created successfully, no validation errors.
- Evidence: `qa_cycle/evidence/day-01/1.5-trust-form-filled.png`.

### Checkpoint 1.6 — Trust account saved, balance R 0,00 (PASS)
- Settings page shows trust account:
  - **Mathebula Trust — Main** [Primary] [ACTIVE] — Standard Bank · 051001 · 12345678 — `SECTION_86`
  - LPFF disclaimer: "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6)). Contact the LPFF to verify."
  - Approval Settings: "Mathebula Trust — Main · Single approval"
- Trust dashboard at `/trust-accounting`:
  - Trust Balance: **R 0,00** — "Mathebula Trust — Main cashbook balance"
  - Active Clients: 0 · Pending Approvals: 0 · Reconciliation: Not yet reconciled
- Evidence: `qa_cycle/evidence/day-01/1.6-trust-dashboard-balance.png`, `1.7-trust-account-created.png`.

### Day 1 closing checkpoint — Branding persists across logout/login (PASS)
- Signed out via user menu → Sign out.
- Re-authenticated through Keycloak (`thandi@mathebula-test.local` / `SecureP@ss1`).
- Dashboard shows logo in sidebar (`img[alt="Mathebula & Partners logo"]`); document root `--brand-color: #1B3358`.
- Evidence: `qa_cycle/evidence/day-01/1-checkpoints-branding-persists.png`.

## Console errors
- **Zero errors** on `localhost:3000` at any checkpoint throughout Day 1.
- Zero warnings relevant to functionality (only Next.js dev-mode console info).

## Gaps Filed

**None.** All three OBS items from the previous cycle (OBS-101, OBS-102, OBS-103) are resolved in this cycle:
- OBS-101 (Tariffs not in sidebar) → Resolved. Tariffs now appears under Finance group.
- OBS-102 (Trust Accounting not in settings sidebar) → Resolved. Trust Accounting appears under Finance in Settings sidebar.
- OBS-103 (Branch Code required but not in scenario) → Resolved. Scenario now specifies Branch Code `051001`.

## Mandate Compliance Check
- No SQL shortcuts: confirmed — UI-only interactions.
- Mailpit API: not needed in Day 1 (no emails issued).
- Frontend console: clean (zero errors).
- No workarounds needed.

## Day 1 Verdict — **COMPLETE**
Ready to advance to Day 2 (Onboard Sipho as client, run conflict check + KYC).
