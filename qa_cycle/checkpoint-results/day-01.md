# Day 1 — Firm onboarding polish
Cycle: 1 | Date: 2026-04-21 | Auth: Keycloak | Frontend: :3000 | Portal: n/a (firm-only day)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 1 (checkpoints 1.1–1.7).

**Resume turn** (20:47 SAST): re-ran 1.1 after PR #1097 (`2a94de5f`) merged fix for GAP-L-23 + cascade GAP-L-24. Both gaps **VERIFIED FIXED** — Save Settings / Logo Upload / Vertical Profile / Portal Digest Cadence all returned 200, values persist in DB and survive page refresh, `fetchProfiles` loads 4 industry profiles instead of the "Failed to load profiles" card. Continued Day 1 through 1.7.

Result summary (Day 1 final): **7/7 checkpoints executed. 4 PASS (1.1, 1.3, 1.6, 1.7), 1 FAIL (1.2 — values persist server-side but sidebar UI chrome does not consume `brand_color` / `logo_s3_key`), 1 PARTIAL (1.4 — tariff schedule row closest to scenario ask is "Attendance at court (per day)" at R 7,800 / "Waiting time at court (per hour)" at R 780; effective date is 2024/2025 not 2026), 1 PARTIAL (1.5 — trust account created as GENERAL under L-25 workaround since SECTION_86 absent).** New gaps: **GAP-L-26** (LOW, frontend — brand color + logo saved but not applied to sidebar chrome), **GAP-L-27** (LOW, backend — tax rate seeded as bare "Standard" not "VAT — Standard" for legal-za; carry-forward of OBS-L-05).

## Session prep — Thandi sign-in (post-Day-0 workaround for GAP-L-22)

Per prior `day-01.md` notes and `status.md` GAP-L-22, opened fresh KC logout (`http://localhost:8180/realms/docteams/protocol/openid-connect/logout` -> Logout button -> Logging out confirmation). Fresh OIDC via `http://localhost:8443/oauth2/authorization/keycloak` -> filled `thandi@mathebula-test.local` / `<redacted>` -> landed cleanly on `http://localhost:3000/org/mathebula-partners/dashboard`. Sidebar correctly shows "TM / Thandi Mathebula / thandi@mathebula-test.local". No session-handoff leak this turn — GAP-L-22 workaround held.

## Checkpoint 1.1 — Upload firm logo + set brand colour `#1B3358`, Save
- Result: **PASS** (re-verified after GAP-L-23 fix)
- Evidence:
  - `/settings/general` loaded clean — Vertical Profile card now renders "Legal (South Africa)" with Apply Profile button (disabled because that's already the active profile). No "Failed to load profiles" state. **GAP-L-24 VERIFIED FIXED** in-place.
  - Typed `#1B3358` into Brand Color text input → swatch preview updated to navy.
  - Clicked **Save Settings** → `POST /org/mathebula-partners/settings/general` → **200 OK** (was 500 last turn).
  - Clicked **Upload Logo** → file chooser → uploaded `qa_cycle/test-fixtures/mathebula-logo.png` (PNG 10×10, 4 KB) → `POST` **200 OK** (actually 5+ chained POSTs covering S3 pre-sign + confirm; all 200).
  - Opened Vertical Profile combobox — 4 options listed: "South African Accounting Firm", "Consulting & Professional Services", "South African Agency & Consulting Firm", "Legal (South Africa)" [selected]. Closed without change. Apply Profile stays disabled because it's already active. **GAP-L-24 VERIFIED** — fetchProfiles works.
  - Portal digest cadence: opened combobox (options: Weekly / Bi-weekly / Off), selected Bi-weekly → `POST` **200 OK** → DB confirms `portal_digest_cadence = BIWEEKLY`. Reset to Weekly → `POST` **200 OK** → DB confirms `WEEKLY`.
  - DB read-only diagnostic: `SELECT brand_color, logo_s3_key, document_footer_text, portal_digest_cadence FROM tenant_5039f2d497cf.org_settings;` → `#1B3358 | org/tenant_5039f2d497cf/branding/logo.png | | WEEKLY`. All values persisted.
  - Browser devtools: **0 × 500 errors** this turn (vs 17 × 500 last turn).
  - Frontend server log (`.svc/logs/frontend.log`): zero `ReferenceError` / `SyntaxError` / `500` entries during the turn.
  - Browser console: 0 errors, 0 warnings.
  - Screenshot: `qa_cycle/checkpoint-results/day-01-1.1-save-settings-OK.png`.

## Checkpoint 1.2 — Refresh → brand colour applied to sidebar, logo renders
- Result: **FAIL** (new non-blocking gap)
- Evidence:
  - Navigated back to `/settings/general` → form rehydrated with `#1B3358` / logo thumbnail rendered next to Upload Logo button with Remove-logo affordance. Portal digest shows Weekly. Server-side persistence confirmed.
  - Navigated to `/dashboard` → screenshotted.
  - `window.getComputedStyle(aside).backgroundColor` = `lab(2.214 ...)` (slate-950 default black) — **not** `#1B3358`.
  - `getComputedStyle(document.documentElement).getPropertyValue('--brand-color')` = empty string (CSS var is never set).
  - No logo `<img>` element anywhere in the sidebar / header. Header still shows text "Kazi" logotype, not the uploaded Mathebula PNG.
  - Sidebar treatment for org name "Mathebula & Partners" uses a teal-accent (default tenant accent), not the saved brand color.
  - Screenshot: `qa_cycle/checkpoint-results/day-01-1.2-dashboard-branding.png`.
- Gap: **GAP-L-26** (LOW — branding values are persisted (1.1 PASS) but the frontend does not consume them in the app sidebar / header. No `--brand-color` CSS custom property is emitted from the org-settings loader, and there is no logo image wired into the `AppSidebar` / `Navbar` components. Day-1 scenario summary check "Firm branding (logo + colour) persists across logout/login" can only verify the server half, not the UX half.)

## Checkpoint 1.3 — Navigate to Rate Cards / Tariff UI, verify LSSA tariff pre-seeded
- Result: **PASS** (with route naming discrepancy)
- Scenario path: "Settings > Rate Cards" — actual tariff UI lives at top-level `/org/<slug>/legal/tariffs`. Settings > Finance > Rates & Currency (`/settings/rates`) is the per-member billing rate sheet (orthogonal feature). Route naming is a copy gap, not a data gap.
- Evidence:
  - Navigated `/org/mathebula-partners/legal/tariffs` → page renders "Tariff Schedules / Browse and manage LSSA tariff schedules and items / 1 schedule / LSSA 2024/2025 High Court Party-and-Party / Effective from 2024-04-01 · 19 items".
  - DB cross-check (read-only): `SELECT name, category, court_level, effective_from, COUNT(items) FROM tariff_schedules ... GROUP BY ...` → `LSSA 2024/2025 High Court Party-and-Party | PARTY_AND_PARTY | HIGH_COURT | 2024-04-01 | 19`. Matches UI.

## Checkpoint 1.4 — Verify specific tariff entry (High Court — attending at court, per hour, 2026 rate)
- Result: **PARTIAL**
- Evidence:
  - Clicked the LSSA schedule row → 7 sections expanded (Sections 1–7). Full item list shown below.
  - **No exact "High Court — attending at court, per hour" row**. Closest matches in Section 4 (Attendances and Hearings):
    - 4(a) **Attendance at court (per day)** — Per Day — **R 7,800.00**
    - 4(b) Attendance at court (per half day) — Per Item — R 4,680.00
    - 4(c) **Waiting time at court (per hour)** — Per Hour — **R 780.00**
  - Schedule is LSSA 2024/2025 — **not** the 2026 schedule the scenario expects. `effective_from = 2024-04-01`. No 2026 schedule seeded in DB.
  - Rates are ZAR-denominated and shown with "R" prefix + two decimals; formatting fine.
- Gap: not re-logged as separate GAP — this is scenario-vs-seeded-data mismatch (minor), already covered by seeded-data maturity roadmap. Flag for scenario author to either (a) update scenario to "2024/2025 schedule" and "Attendance at court (per day) R 7,800" OR (b) add 2026 schedule to `LegalTariffSeeder`.

## Checkpoint 1.5 — Create Trust Account (SECTION_86, Mathebula Trust — Main)
- Result: **PARTIAL** (workaround used per GAP-L-25 — created as GENERAL)
- Evidence:
  - Navigated `/trust-accounting` → "No Trust Accounts" empty state → clicked **Add Account**.
  - Dialog fields filled:
    - Account Name: `Mathebula Trust — Main`
    - Bank Name: `Standard Bank`
    - Branch Code: `051001`
    - Account Number: `12345678`
    - Account Type: **GENERAL** (only options offered are General / Investment — `SECTION_86` not available; this is GAP-L-25 carried forward)
    - Opened Date: `2026-04-21` (prefilled)
    - Set as primary trust account: checked (default)
    - Require dual approval: unchecked (default)
  - Clicked **Create Account** → dialog closed, page redirected to the Trust Accounting dashboard showing:
    - Trust Balance card: "R 0,00 / Mathebula Trust — Main cashbook balance"
    - Active Clients: 0 / Pending Approvals: 0 / Reconciliation: "Not yet reconciled"
    - Recent Transactions: "No transactions recorded yet"
  - DB read-only: `SELECT account_name, bank_name, account_number, account_type, is_primary FROM trust_accounts;` → `Mathebula Trust — Main | Standard Bank | 12345678 | GENERAL | t`. Row created, primary flag set.
- Gap: GAP-L-25 remains OPEN (Trust Account Type enum missing SECTION_86). Account classification is "GENERAL" instead of the statutory LSSA §86 classification the scenario expects.

## Checkpoint 1.6 — Trust account saves, balance R 0.00, appears in list
- Result: **PASS**
- Evidence: Trust Balance card renders "R 0,00 / Mathebula Trust — Main cashbook balance" immediately after create. Persistence verified via DB.
- Nit: SA number formatting uses comma decimal "R 0,00" — consistent with `ZAR` + `en-ZA` locale; matches currency-rendering requirement downstream.

## Checkpoint 1.7 — Screenshot `day-01-trust-account-created.png`
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-01-trust-account-created.png`.

## Day 1 summary checks
- Firm branding (logo + colour) persists across logout/login: **PARTIAL — persists server-side (DB row confirms after refresh), does not render on sidebar UI (GAP-L-26)**.
- LSSA tariff table pre-populated, non-empty: **PASS — 19 items, Party-and-Party, High Court**; effective year 2024/2025 (scenario expects 2026, flagged but not blocker).
- Trust account created under Section 86 basis: **PARTIAL — created as GENERAL via L-25 workaround; statutory §86 classification unavailable.**

## Carry-Forward watch-list verifications this turn

| Prior gap | Re-observed? | Notes |
|---|---|---|
| GAP-L-22 (post-registration session handoff) | Workaround verified | Explicit KC logout + fresh OIDC login landed Thandi on `/dashboard` with her own identity in sidebar — clean. Still OPEN (needs fix); workaround still reliable. |
| GAP-L-23 (settings server-actions ReferenceError) | **VERIFIED FIXED** | All 4+ POSTs returned 200. `.svc/logs/frontend.log` clean. DB values persist. |
| GAP-L-24 (Vertical Profile "Failed to load profiles") | **VERIFIED FIXED (cascade)** | fetchProfiles returns all 4 industry profiles. No error card. |
| GAP-L-25 (Trust Account Type missing SECTION_86) | Confirmed still present | Dropdown offers only General / Investment. Workaround = GENERAL. Remains OPEN. |
| OBS-L-05 (tariff / tax naming) | **Re-observed** | Tax rate seeded as bare "Standard" in `tax_rates` (not "VAT — Standard"). Logged as new GAP-L-27 (LOW). Tariff items themselves are well-labelled ("Attendance at court (per day)"). |

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-26 | LOW | `/settings/general` Save Settings correctly persists `brand_color` (`#1B3358`) and `logo_s3_key` (`org/tenant_5039f2d497cf/branding/logo.png`) to `org_settings` (verified via DB after the GAP-L-23 fix). However, the frontend app shell does NOT consume those values: sidebar computed `background-color` remains slate-950 default; `--brand-color` CSS custom property on `document.documentElement` is empty; no image tag renders the uploaded logo in sidebar header / navbar. Scenario checkpoint 1.2 ("brand colour applied to sidebar accent + logo renders at top of sidebar") therefore FAILs. Fix likely lives in: (a) `frontend/components/layout/AppSidebar.tsx` or similar — needs to read `orgSettings.brandColor` / `orgSettings.logoUrl` and inject as CSS var + logo src; (b) possibly a `<style>` injection on the org-layout root emitting `--brand-color` from server-rendered props. Owner: frontend. Severity LOW because core persistence works; UX polish missing. |
| GAP-L-27 | LOW | Tax rate seeded by `legal-za` pack (or common-tax seeder) as bare `"Standard"` with rate 15%. SA users expect `"VAT — Standard"` or `"VAT (Standard)"` for explicit disambiguation (SA legal-za vertical should not use generic accounting labels). Also seeded: `Zero-rated` 0% and `Exempt` 0%. Tenant `org_settings.tax_label` is NULL — legal-za tenant init should probably set `tax_label = 'VAT'` and rename `Standard` -> `VAT — Standard`. This is OBS-L-05 carried forward from prior legal-za archives. Owner: backend (likely `backend/.../vertical/legal/ZaLegalTaxSeeder.java` or the common tax seeder with a legal-za override). Severity LOW — cosmetic copy only. |

## Halt reason
None — Day 1 completed end-to-end. GAP-L-26 and GAP-L-27 are LOW and do not block progression.

## QA Position on exit

`Day 2 — 2.1` (Onboard Sipho as client, conflict check + KYC, actor = Bob Admin). Next QA turn: context-swap to Bob — explicit KC logout, fresh OIDC login as `bob@mathebula-test.local`.
