# Day 1 — Firm Onboarding Polish — Cycle 2 Results

**Branch**: `bugfix_cycle_2026-04-26`
**Date**: 2026-04-26 SAST (UTC 2026-04-26T19:00–19:06)
**Tenant**: `mathebula-partners` (tenant_5039f2d497cf)
**Actor**: Thandi Mathebula (owner) — logged in via Keycloak
**Stack**: Keycloak dev stack — frontend :3000, BFF gateway :8443, backend :8080, KC :8180, LocalStack S3 :4566

## Login

KC OIDC login at `http://localhost:8180/realms/docteams/protocol/openid-connect/auth?client_id=gateway-bff` using `thandi@mathebula-test.local` / `SecureP@ss1`. Redirected back to `/org/mathebula-partners/dashboard`. Console: 0 functional errors (KC favicon 404 only). Day-0 fix L-22 still holding.

## Checkpoint Results

| ID | Description | Result | Evidence |
|----|-------------|--------|----------|
| 1.1 | Settings > Organization → upload logo (≤200 KB PNG) → set brand colour `#1B3358` → Save | PASS | Settings sidebar entry is labeled **General** (not "Organization") but shows the Branding block with Logo + Brand Color + Footer fields. Uploaded `qa_cycle/test-fixtures/mathebula-logo.png` (10×10 RGB PNG, 75 B). Brand-color text input set to `#1B3358` via native-setter + bubbled events (Playwright `fill()` did not propagate through the controlled input). Save Settings POST body: `["mathebula-partners",{"defaultCurrency":"ZAR","brandColor":"#1B3358","documentFooterText":"$undefined"}]` → 200. DB: `tenant_5039f2d497cf.org_settings.brand_color='#1B3358'`, `logo_s3_key='org/tenant_5039f2d497cf/branding/logo.png'`. Screenshot `day-01-1.1-branding-saved.png`. |
| 1.2 | Refresh → verify brand colour applied to sidebar accent + logo renders | **PARTIAL — GAP-L-90** | Logo renders correctly in sidebar (presigned LocalStack S3 URL, `alt="Mathebula & Partners logo"`). `--brand-color: #1B3358` is injected as an inline CSS variable on `<html>`, but **no stylesheet rule consumes `var(--brand-color)`** anywhere — search of `document.styleSheets` returns 0 references to `var(--brand-color)`. Sidebar background remains the default dark theme (`lab(2.214 -0.13 -1.01)`), not navy. No element has `color`/`background`/`border` containing `rgb(27, 51, 88)`. Screenshot `day-01-1.2-after-refresh.png`. Logging as `GAP-L-90` (BUG severity, not BLOCKER — branding persists in DB and logo renders, just no color tokens are wired). |
| 1.3 | Settings > Rate Cards → verify LSSA tariff rates pre-seeded | PASS (with location note) | Sidebar `Settings > Rates & Currency` shows only per-member billing rates (Thandi/Bob/Carol "Not set"), NOT the LSSA tariff schedule. The LSSA schedule lives at `/org/mathebula-partners/legal/tariffs` (legal sub-nav, accessed by direct URL). Heading "Tariff Schedules — 1 schedule — LSSA 2024/2025 High Court Party-and-Party — 19 items". Schedule expands to 7 sections with all 19 items in ZAR. (Naming-only nit, not a defect: scenario says "Settings > Rate Cards" but the legal tariff entry-point is the standalone Tariffs page.) |
| 1.4 | Verify ≥1 tariff entry: High Court "attending at court, per hour" with current-year (2026) rate in ZAR | **PARTIAL — GAP-L-91** | Closest entry is Section 4(c) "Waiting time at court (per hour)" — Per Hour — **R 780.00 ZAR**. Section 4(a) "Attendance at court (per day) — R 7800.00" and 4(b) "Attendance at court (per half day) — R 4680.00" cover daily/half-day rates. Schedule label is **LSSA 2024/2025**, not 2026. Logging `GAP-L-91` (LOW — LSSA tariff is published every 2-3 years; 2024/2025 schedule is the most recent real-world schedule, scenario expectation of "2026" is aspirational. Recommend dev/product confirm whether a 2026 seed pack should ship before demo.) Screenshot `day-01-1.3-1.4-tariff-schedule.png`. |
| 1.5 | Settings > Trust Accounts → create Mathebula Trust — Main / Standard Bank / 12345678 / SECTION_86 | PASS | No `Settings > Trust Accounts` link; trust accounts are managed at `/org/mathebula-partners/trust-accounting`. Clicked Add Account, dialog `Add Trust Account` opened. Filled: Account Name `Mathebula Trust — Main`, Bank Name `Standard Bank`, Branch Code `051001` (required field — scenario didn't list it, supplied Standard Bank universal code), Account Number `12345678`, Account Type combobox set to **Section 86 Trust Account** via native-setter + bubbled change event (Playwright select-option did not propagate, same MCP-Radix quirk class as BUG-CYCLE26-01/02). Set as primary = checked (default), Require dual approval = unchecked. Create Account → dialog closed, dashboard refreshed. |
| 1.6 | Trust account saves, no validation error, appears in list with R 0.00 | PASS | Trust Accounting dashboard now shows: **Trust Balance R 0,00 — Mathebula Trust — Main cashbook balance**. DB row: `account_name='Mathebula Trust — Main', bank_name='Standard Bank', branch_code='051001', account_number='12345678', account_type='SECTION_86', is_primary=true, status='ACTIVE', opened_date='2026-04-26'`. First Create-Account click failed validation ("Branch code is required" surfaced in dialog) — that was a missing-field error on my part, not a defect. After supplying branch code, save succeeded. |
| 1.7 | 📸 Optional screenshot `day-01-trust-account-created.png` | PASS | `qa_cycle/checkpoint-results/day-01-trust-account-created.png` — Trust Accounting dashboard with Mathebula Trust — Main visible. |

## Day 1 wrap-up checks

- [x] **Firm branding (logo) persists across navigation/reload** — Logo persists across `/dashboard → /settings/general → /legal/tariffs → /trust-accounting` and through cookie-clear navigation; presigned URL is regenerated each request. Brand color is persisted in DB but **does not visually apply** anywhere (see GAP-L-90).
- [x] **LSSA tariff table pre-populated, non-empty** — 1 schedule × 7 sections × 19 items, all in ZAR. (Schedule year 2024/2025, see GAP-L-91.)
- [x] **Trust account created under Section 86 basis** — Mathebula Trust — Main, account_type=SECTION_86, ACTIVE, R 0.00. DB-confirmed.

## Bugs / observations opened this day

- **GAP-L-90** — **Severity: BUG** (not BLOCKER, but impacts demo polish). Brand-colour Save Settings flow correctly persists `brand_color` to `tenant_5039f2d497cf.org_settings` and injects `--brand-color: <hex>` as an inline style on `<html>`, but no Tailwind/CSS rule consumes `var(--brand-color)` anywhere in the bundle. Visual outcome: setting brand colour has zero visible effect on sidebar, header, accent, button hover, or any other UI surface. Repro:
  1. As Thandi, Settings > General > Branding > set Brand Color = `#1B3358`, Save Settings.
  2. Refresh dashboard.
  3. Inspect: `getComputedStyle(document.documentElement).getPropertyValue('--brand-color')` → `#1B3358`.
  4. Search stylesheets for `var(--brand-color)` → 0 hits. No element has `color`/`background`/`border` containing `rgb(27, 51, 88)`.
  5. Sidebar accent renders default theme palette, identical to a fresh tenant with no brand color set.

  Expected: brand color should drive at least the active sidebar nav-item highlight, primary button surface, or breadcrumb accent — somewhere visible during the firm-onboarding wow moment. Recommend dev wire `--brand-color` into Tailwind theme tokens (e.g. expose as `--accent` override, or add a `data-brand-color` selector that styles `[data-active-nav], button[data-variant=primary]`).

- **GAP-L-91** — **Severity: LOW** (data-freshness, not functional). The legal-za rate pack ships only the LSSA 2024/2025 High Court Party-and-Party schedule (19 items). Scenario 1.4 expects "current-year rate (2026 schedule) in ZAR". Two options: (a) accept 2024/2025 as latest real-world LSSA schedule (LSSA tariffs are revised every 2-3 years; no 2026 schedule has been published), and update scenario language; or (b) ship a stub 2026 schedule in the legal-za seed pack with placeholder rates. Recommend product decide before demo. Functionally, scenario 1.4's "non-empty + ZAR + hourly entry" intent is satisfied by Section 4(c) "Waiting time at court (per hour) R 780.00".

## Pre-existing carry-over

- **BUG-CYCLE26-01** / **BUG-CYCLE26-02** (LOW, tooling-only) — not retested per Cycle 2 instructions. Encountered the same class of MCP-Radix-Select desync today on the Trust Account Type combobox; native-setter workaround again worked cleanly.
- **GAP-L-21** (WONT_FIX, no platform-admin access-request detail page) — not relevant on Day 1.

## Console errors observed

- Hydration mismatch on Trust Accounting page: Radix-generated `aria-controls` ID differs server vs client (`radix-_R_1cmbn5rknelb_` vs `radix-_R_5iqbn5rknelb_`). Cosmetic; React patches it up on next render. Pre-existing Radix/Next.js 16 RSC quirk.
- KC favicon 404 (pre-existing).

No functional console errors.

## Wow-moment screenshots

- `day-01-1.1-branding-saved.png` — General Settings with logo + #1B3358 brand color saved.
- `day-01-1.2-after-refresh.png` — Dashboard after refresh: logo renders, sidebar accent **does NOT** show brand color (gap evidence).
- `day-01-1.3-1.4-tariff-schedule.png` — Full LSSA 2024/2025 schedule with all 7 sections expanded.
- `day-01-trust-account-created.png` — Trust Accounting dashboard with Mathebula Trust — Main R 0,00.
- `day-01-branding-persists.png` — Dashboard after navigation/reload showing logo persistence.

## Summary

**5 PASS / 2 PARTIAL / 0 FAIL / 0 BLOCKER** for Day 1 (7 sub-checkpoints + 3 wrap-up checks).

PARTIALs:
- 1.2: Brand color does not propagate to UI accents (GAP-L-90, BUG severity).
- 1.4: LSSA schedule is 2024/2025 not 2026 (GAP-L-91, LOW severity).

Both PARTIALs are non-cascading — Day 2+ does not depend on brand-colour visual rendering or 2026 tariffs. Cycle proceeds to Day 2 next turn.

## Cycle 5 Retest 2026-04-26 SAST — GAP-L-90

Retest of fix landed in PR #1164 (squash merge `68c71cb8`). Branch is `main`. Frontend HMR auto-loaded the new code; no service restart needed. QA Position deliberately held at Day 2 / 2.1 per user directive — this retest does NOT advance.

| Check | Result | Evidence |
|-------|--------|----------|
| 1.2.a — `:root` `--brand-color` value (computed) | PASS | `getComputedStyle(document.documentElement).getPropertyValue('--brand-color')` → `#1B3358`. Inline style on `<html>` also = `#1B3358`. Brand-color was already saved from Day-1 cycle 2 — Save Settings re-clicked to confirm. |
| 1.2.b — Desktop sidebar active-item accent indicator (Dashboard) | PASS | Element `div.absolute.top-1.bottom-1.left-0.w-0.5.rounded-full` inside the Dashboard nav link → `getComputedStyle().backgroundColor === 'rgb(27, 51, 88)'` (navy). Width 2px, full height of item. Matches `#1B3358` exactly. Screenshot `retest-2026-04-26-sidebar-navy.png`. |
| 1.2.c — Desktop sidebar org-name label ("Mathebula & Partners") | PASS | `<span class="truncate text-xs font-medium opacity-80">` inside aside → `getComputedStyle().color === 'rgb(27, 51, 88)'`. Visually navy in screenshot. |
| 1.2.d — Mobile sidebar (375×812) — utility-footer active indicator (Settings) | PASS | After resizing to 375×812 and opening the mobile sheet via "Toggle menu", navigated to /settings/general. Re-opening the sheet shows Settings as the active utility item; its left-edge indicator (`div.absolute.top-1.bottom-1.left-0.w-0.5`) → `backgroundColor === 'rgb(27, 51, 88)'` (navy). Screenshot `retest-2026-04-26-mobile-sidebar-navy.png`. |
| 1.2.e — Viewport reset to desktop after mobile probe | PASS | Resized back to 1440×900. |

**GAP-L-90 → VERIFIED**. Brand-colour now drives the desktop sidebar active-item indicator + org-name label, AND the mobile sidebar utility-footer active indicator (the previously-flagged miss). All three computed-style probes return `rgb(27, 51, 88)` matching the saved navy hex.

Note on mobile org-name: in the mobile dialog, the org-name uses `text-white/60` (deliberate over-dark-background treatment) rather than the brand colour. This is a separate and intentional design choice — not a regression. The active utility-footer indicator is the spec-mandated check, and that's navy.

Note on `data-current-page` warning: the dashboard JSX still has a stray "Current page" warning visible as one Next.js dev-tools issue (1 issue). Unrelated to GAP-L-90 and pre-existed PR #1164.

**Stop condition**: All checks PASS. QA Position **HELD at Day 2 / 2.1** per user directive. Do NOT walk forward.
