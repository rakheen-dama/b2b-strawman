# Day 1 — Firm onboarding polish — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Thandi Mathebula (Owner) — session carried over from Day 0 registration; logout/login cycle performed at end of day for the branding-persistence summary checkpoint
**Driver**: QA agent via Playwright MCP against Keycloak dev stack (frontend :3000, gateway :8443, KC :8180)
**Result**: 7/7 checkpoints PASS + 3/3 day-summary checkpoints PASS. **Zero gaps filed.**

## Checkpoints

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 1.1 Settings > Organization → upload logo + brand colour `#1B3358` → Save | PASS | Settings > General (`/org/mathebula-partners/settings/general`) Branding section: uploaded `qa_cycle/mathebula-logo.png` (50×50 PNG, 145 B ≤ 200 KB) via Upload Logo file chooser — preview rendered with Remove button; Brand Color set to `#1B3358` (colour-picker mirror updated to `#1b3358`); Save Settings clicked — sidebar immediately re-rendered with "Mathebula & Partners logo" img at top. No error toast, no console error. | — |
| 1.2 Refresh → brand colour applied to sidebar accent + logo at top of sidebar | PASS | Hard re-navigation to settings/general: logo persists, served from LocalStack S3 (`localhost:4566/docteams-dev/org/tenant_5039f2d497cf/branding/logo.png`, presigned). CSS var `--brand-color: #1B3358` set on `<html>`; computed-style scan found rgb(27,51,88) applied inside the sidebar on the org-name span (color) and the active-nav accent rail (`w-0.5 rounded-full` div, backgroundColor). Screenshot: `day-01-branding-applied.png`. | — |
| 1.3 Tariffs via Finance group → `/org/{slug}/legal/tariffs`, LSSA rates pre-seeded | PASS | Main sidebar Finance group expanded → "Tariffs" entry → `/org/mathebula-partners/legal/tariffs`. Page "Tariff Schedules" shows 1 schedule: **LSSA 2024/2025 High Court Party-and-Party** — Effective from 2024-04-01 · 19 items, expandable Sections 1–7. | — |
| 1.4 Section 4 values: 4(c) Waiting time R 780.00/hr OR 4(a) Attendance R 7800.00/day, all ZAR | PASS | **Both** present in Section 4: 4(a) "Attendance at court (per day)" Per Day **R 7800.00**; 4(b) per half day R 4680.00; 4(c) "Waiting time at court (per hour)" Per Hour **R 780.00**. All 19 items across Sections 1–7 render in ZAR (R). Screenshot: `day-01-lssa-tariffs.png`. | — |
| 1.5 Settings > Trust Accounting → Add Account → fill form | PASS | `/org/mathebula-partners/settings/trust-accounting` → Add Account dialog: Name **Mathebula Trust — Main**, Bank **Standard Bank**, Branch Code **051001** (hard-required field present per OBS-103 closure), Account Number **12345678**, Type **Section 86 Trust Account** (select options: General / Investment / Section 86 Trust Account), Opened Date defaulted 2026-06-13, "Set as primary" pre-checked → Create Account. | — |
| 1.6 Trust account saves, no validation error, appears in list with balance R 0.00 | PASS | Settings list shows "Mathebula Trust — Main" — Primary, ACTIVE, "Standard Bank · 051001 · 12345678", **SECTION_86**, plus LPFF Section 86(6) arrangement notice; Approval Settings row auto-created (Single approval). Trust Accounting dashboard (`/trust-accounting`) Trust Balance card: **R 0,00** "Mathebula Trust — Main cashbook balance" (SA locale formatting). | — |
| 1.7 📸 Optional screenshot | PASS | `day-01-trust-account-created.png`. | — |

## Day 1 summary checkpoints

| Checkpoint | Result | Evidence |
|---|---|---|
| Firm branding (logo + colour) persists across logout/login | PASS | Signed out via user menu (landed on landing page) → `/dashboard` → KC `realms/docteams` login as thandi@mathebula-test.local / SecureP@ss1 → dashboard. Post-login DOM check: sidebar logo present (alt "Mathebula & Partners logo", S3 `org/tenant_5039f2d497cf/branding/logo.png`), `--brand-color: #1B3358`, sidebar shows Thandi Mathebula. |
| LSSA tariff table pre-populated, non-empty | PASS | LSSA 2024/2025 High Court Party-and-Party, 19 items, seeded from `legal-za` rate pack — no manual entry performed. |
| Trust account created under Section 86 basis | PASS | SECTION_86 badge on account card; LPFF Section 86(6) advisory rendered; account ACTIVE + Primary with R 0,00 cashbook balance. |

## Console / log health

- Frontend console: zero errors on every navigation (settings/general, legal/tariffs, settings/trust-accounting, trust-accounting, logout/login round-trip). Only the known Next.js `scroll-behavior: smooth` **warning** appeared once on the tariffs navigation (classified INFO/not-an-error in the 2026-05-30 cycle; not re-filed).
- `.svc/logs/frontend.log`: no errors/exceptions. `.svc/logs/backend.log`: no ERROR lines.

## Gaps filed

None.
