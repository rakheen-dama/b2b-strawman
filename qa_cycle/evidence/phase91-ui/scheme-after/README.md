# scheme-after: the 644 companion set, light only (task 661.14)

This folder re-shoots the Phase 90 exit-matrix surfaces in **light only**, at their original widths, on the 661B head.

| | |
|---|---|
| Head | `phase91/61B-light-scheme` @ **`d62c5dc19`** (PR #1863) |
| Phase 90 set | `qa_cycle/evidence/phase90-ui/matrix/` (644B, task 644.4; `README.md` there), taken on the **Keycloak dev stack** at `main` @ `21be78447`. Owner: Thandi, Mathebula & Partners |
| This set | Taken on the **e2e mock-auth stack** (`:3001` / `:8081`; only the frontend was rebuilt from this head, the data was kept). Owner: **Alice**; member: **Carol**; org "E2E Test Organization". Portal: this worktree's portal on `next dev --port 3012` with the Origin shim (memory `env_playwright_mcp_profile_lock_obs`) |
| Method | `scripts/scheme-after.cjs`, a Playwright library script (the MCP profile is in use by the user's session). It uses the same waits as Phase 90's `matrix.cjs`: `load` + h1 + the surface's selector + pointer parked + 3 s. `reducedMotion: reduce`, Africa/Johannesburg, `kazi-theme=light`. It runs the light audit from `../661B/walk/scripts/audit.cjs` on every viewport shot. Raw tiles are in `/tmp/661b-scheme-after-raw`, outside the tree |
| Data files | `obs-report.json` (one line: per shot URL, h1, selected tab, audience, audit, Phase 90 counterpart), `console.json` |

The tenants differ, so these shots are **not** pixel-comparable with Phase 90's: the matter, client, figures and org name all differ. Each pair is the same surface, width and persona role, shown before and after the scheme.

Matter used: the ACTIVE client matter with the largest trust balance, which is Phase 90's selection rule: "Estate Late Peter Moroka mwwjtue3603b" (`682b8e3d-…`). Client: its client, "Moroka Family Trust mwwjtue3603b" (`1ea2df84-…`).

## Mapping (28 captures in 11 contact sheets)

| Sheet | Tile | Phase 90 counterpart (`qa_cycle/evidence/phase90-ui/matrix/…`) | Observation (light, A1) |
|---|---|---|---|
| `home.png` | home 1024 | `home-1024-light.png` | OK. 2×2 KPI grid, diary, WIP, debtors |
| | home 1440 | `home-1440-light.png` | OK. 4-up KPI row |
| | home 1440 full | `home-1440-light-full.png` | OK. Every card is legible on the `#fdfeff` inset; trust position "Balanced"; FICA; matter health; sensitive events; activity |
| `matters.png` | matters 1024 | `matters-1024-light.png` | OK. The table scrolls inside its container (Phase 90 B-12 density item, unchanged) |
| | matters 1440 | `matters-1440-light.png` | OK. The "Next in diary" column reaches the card edge (Phase 90 note, unchanged) |
| `matter-detail.png` | matter detail 1024 | `matter-detail-1024-light.png` | OK. 2×2 hero. Breadcrumb "E2E Test Organization" wraps to two lines (already in `../661B/found.md`) |
| | matter detail 1440 | `matter-detail-1440-light.png` | OK. 4-up hero |
| `matter-tabs.png` | `?tab=time` 1440 | `matter-detail-tab-time-1440-light.png` | OK. Selected tab "Fees · Time recording" |
| | `?tab=trust` 1440 | `matter-detail-tab-trust-1440-light.png` | OK. "Fees · Trust", R 241 500,00 "Funds Held" |
| | `?tab=activity` 1440 | `matter-detail-tab-activity-1440-light.png` | OK. "Activity · Activity" (the Phase 90 642C label note, unchanged) |
| `clients.png` | clients 1024 / 1440 | `clients-1024-light.png` / `clients-1440-light.png` | OK |
| `client-detail.png` | client detail 1024 / 1440 | `client-detail-1024-light.png` / `client-detail-1440-light.png` | OK |
| `trust.png` | trust dashboard 1024 / 1440 | `trust-1024-light.png` / `trust-1440-light.png` | OK. Tiles and the balance-trend chart are legible |
| `profitability.png` | profitability 1024 / 1440 / 1440 full | `profitability-1024-light.png` / `-1440-light.png` / `-1440-light-full.png` | OK. Billing trend, utilisation, matter and client profitability |
| `personas.png` | Carol (Member) Home 1440 | `home-member-carol-1440-light.png` | OK. `data-audience="feeEarner"` and no KPI row, as in Phase 90 |
| | Carol matter detail 1440 | `matter-detail-member-carol-1440-light.png` | OK. "Ndlovu v RAF mwwjtue3603b", the ACTIVE client matter on Carol's own `/api/matters` with the largest trust balance. Carol is not on the Moroka matter's team on this stack |
| | *extra:* Carol on the owner's matter | — | The "This matter isn't on your list" branch, which has **no h1** (an `h2`; W-8 in `../661B/walk/findings.md`) |
| `portal.png` | portal `/login` at rest, 1024 | — (extra; the 661B before/after shots cover it) | OK. Unchanged portal: `--accent` is teal-600 |
| | portal `/login` after "Send Magic Link", 1024 | `portal-login-link-sent-1024-light.png` | OK. The dev-mode link panel shows a token that is single-use, has a 15-min TTL and belongs to a localhost stack |
| | portal `/home` 1024 | `portal-home-1024-light.png` | OK. Acme Corp contact; 1 pending info request; INV-0001 R 8 625,00 |
| | portal `/trust` 1024 | `portal-trust-1024-light.png` | **Empty state**: "No trust activity on your matters". The e2e portal contact is Acme Corp, which has no trust deposit (644A noted the same). Phase 90's shot was populated with the Moroka contact |
| `marketing.png` | marketing `/` 1440, no stored preference | `marketing-control-no-pref-1440.png` | OK. Outside the firm shell (no `data-app-shell`), so the scheme does not apply |
| | marketing `/` 1440 full | `marketing-control-no-pref-1440-full.png` | The page still renders as before. Its product screenshots (`/screenshots/*.png`) show the **pre-scheme dark rail** (W-11 in `../661B/walk/findings.md`; docs/marketing refresh) |

**Not re-shot**, with the reason:

- `home-fv-role-carol-1440-light.png` and the `role-flow/` set: they need a custom role created, assigned and deleted (tenant writes). The persona surface (Home) is covered by the owner and member shots.
- `marketing-stored-dark-*`: a stored-**dark**-preference check, outside "light only". Marketing `/` is pixel-identical to before in `../661B/after-fix/`.
- `stack-proof-*`: a KC-stack provenance artefact, not a surface.

## Audit and console

- **Scheme-attributable contrast items: 0 on every audited shot.** Text, icon and field counts are all 0, from the same counterfactual method as the walk (`obs-report.json` → `audit.scheme*`). `firmLightA` is true on every firm-app shot and false on portal and marketing, which are outside the scheme.
- Remaining sub-floor text is pre-existing and equal with the scheme off. Examples: the notification badge (W-1), slate-400 hints (W-3), green-600 values (W-4). Marketing has 57 / 57, all outside the firm shell.
- `dark=false` on every shot. HTTP 200 everywhere.
- **Console:** 0 hydration warnings, 0 warnings. 1 error: "Failed to load resource: 404". It was logged before a shot label was set, during a page load. It matches marketing `/`'s known 404 (seen before and after in `../661B/after/` and `../661B/after-fix/`), but this run does not pin it to a page.

## Files

11 contact sheets (`*.png`), `obs-report.json`, `console.json`, `scripts/scheme-after.cjs` (as executed; the shared helpers are in `../661B/walk/scripts/`).
