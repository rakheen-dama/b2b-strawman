# 637A evidence — tokens + Kazi chart palette

Head under test: **`874ba8df5`** (PR #1759). Baseline: `../baseline/README.md` (`main` @ `f7b7dbdf1`).

## Gates (orchestrator re-run on `874ba8df5`)

| Gate | Result |
|---|---|
| FE-GATE | exit 0 — 459 files, **3879 passed / 2 skipped** (baseline 3763 / 2) |
| PORTAL-GATE | exit 0 — 51 files, **284 passed** (baseline 284) |

## E2E-LEGAL + E2E-DEFAULT (fresh stack, mock auth :3001, started 02:19 SAST) — `e2e/summary.txt`

| Suite | Baseline | 637A | ≥ baseline |
|---|---|---|---|
| legal-lifecycle | 105 passed | **105 passed** | yes |
| legal-depth-ii | 40 passed, 4 skipped | **40 passed, 4 skipped** | yes |
| E2E-DEFAULT | 157 passed, 1 failed (INV-02, cause unconfirmed), 7 did not run, 14 skipped | **165 passed, 14 skipped** | yes |

No spec edited; no role or test id changed.

## OBS — light only (dark is not reachable until 637B)

Driven in a real headless Chromium by `obs.cjs` / `portal-obs.cjs` (Playwright library, fresh browser context). The Playwright **MCP** browser profile was held by another live Claude session on this machine, so the MCP tools could not launch. The scripts do the same thing the MCP would: navigate, screenshot, and read computed styles plus console. Raw output: `obs-report.json`, `portal/obs-report.json`. The committed scripts are byte-for-byte the ones executed (absolute paths kept on purpose — provenance, not a reusable tool). Page readiness: every one of the 9 entries in the two reports carries the page's `h1` (`Home`, `Matters`, `Trust Accounting`; portal `Home`, `Trust statement`, `Fee notes`), so no observation silently timed out. `e2e-up exit 0` is recorded in both `e2e/summary.txt` files, so both suite runs were against a freshly built stack.

**Console: 0 errors, 0 warnings, 0 hydration warnings** on all 9 page loads.

Computed tokens confirmed in the browser on every page: body background `lab(96.74 …)` (= `oklch(97.2% 0.004 260)`, the lighter canvas), `--ring` `lab(52.87% -45.8 -2.8)` (= `oklch(58% 0.13 183)`, ruling 1).

| Surface | 1024×768 | 1440×900 |
|---|---|---|
| Home (`/org/e2e-test-org/dashboard`) | `home-1024-light.png` | `home-1440-light.png` |
| Matters list | `matters-1024-light.png` | `matters-1440-light.png` |
| Trust dashboard (`/trust-accounting`) | `trust-1024-light.png` | `trust-1440-light.png` |
| Portal `/home` | `portal/home-1024-light.png` | — |
| Portal `/trust` | `portal/trust-1024-light.png` | — |
| Portal `/invoices` | `portal/invoices-1024-light.png` | — |

The portal was run from this branch (`next dev --port 3012`, `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`) against the e2e backend. The magic link was exchanged through the backend API, the same way as `portal/e2e/helpers/auth.ts`. :3002, the only CORS-allowed portal origin, is held by the Keycloak dev portal on `main`. A Playwright route therefore rewrote `Origin` to `:3002` and added CORS response headers inside the test browser only. No app or backend code was changed.

### What changed visibly (intended)

- Lighter canvas and new border/card steps on every page, frontend and portal.
- **Debtors widget (Home):** the aging bars now use the one-hue orange `--aging-*` ramp (ADR-370; ordinal data). Before, they used teal, slate and amber. On this data every client is "Not yet overdue", so every bar shows the `--aging-current` slot.
- Focus ring is `oklch(58% 0.13 183)` in light mode.

### Recorded, not caused by 637A

- **Trust dashboard at 1024:** the Trust Balance KPI value `R 290 249,9…` overflows its card. This is a layout problem in `trust-accounting/page.tsx`, which is untouched by this PR (`git diff main...874ba8df5 --stat` lists no trust file). Owner: **643A**, which rebuilds the trust dashboard.
- **Sidebar (OQ #3):** still slate-950 in light mode, as decided. `desktop-sidebar.tsx` hard-codes `bg-slate-950`, so the new `--sidebar-*` tokens take effect from 638A.
