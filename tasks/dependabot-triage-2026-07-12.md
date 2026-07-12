# Dependabot Triage — 2026-07-12 (89 open alerts: 37 high / 36 medium / 16 low)

Raw dump: 89 alerts, but heavy multiple-counting — GitHub raises one alert per advisory **per manifest**,
and the pnpm workspace means most advisories appear 3× (frontend/package.json + portal/package.json +
root pnpm-lock.yaml). Distinct fix units: **three waves**.

## Headline

- **All 21 high `next` alerts are 7 distinct advisories, fixed by one bump: `next 16.2.4 → 16.2.6`**
  (both apps pin exactly; ranges are `>=16.0.0 <16.2.5` ×6 and `<16.2.6` ×1). Runtime, prod-facing —
  this is the only genuinely urgent item.
- The 8 remaining root-workspace highs are transitives (`undici` ×3, `vite` ×3, `ws` ×1) — `vite` is a
  devDependency (vitest tooling), so dev-only exposure despite the "runtime" label.
- 13 alerts (8 high: axios ×6, form-data, ws) live in `tools/claude-slack-bot/` — a semi-orphan dev
  tool (own lockfile, NOT in pnpm-workspace, last functional change 2026-02-19, no CI/compose
  references). **Decision needed: delete vs keep** (frontend-v2 precedent: deletion killed more alerts
  than upgrading). If Claude Tag / Claude-in-Slack has replaced it, delete.
- 4 alerts in compose build tooling (keycloak theme: vite 6.4.3 + @babel/core; mock-idp: qs) —
  build-time/local-dev only, never serves traffic.
- One `NO-PATCH` low (dompurify ≤3.4.6): moot — moving the lock past 3.4.6 (Wave 1 bumps to 3.4.11)
  clears it.

## Wave 1 — root workspace refresh (ONE PR, kills ~63 alerts incl. 28/37 highs)

| Package | Action | Alerts killed | Notes |
|---|---|---|---|
| next | `16.2.4 → 16.2.6` in frontend+portal package.json | 39 (21 high) | patch-level; prod runtime |
| vite | `7.3.2 → 7.3.5` (devDep, both apps) | 6 (3 high) | vitest tooling only |
| undici | targeted `pnpm update undici` → 7.28.0 | 7 (3 high) | transitive |
| dompurify / mermaid | `pnpm update mermaid dompurify` → 11.15.0 / 3.4.11 | 12 | transitive (root lock); clears the NO-PATCH low |
| ws, js-yaml, postcss, uuid, brace-expansion, esbuild, @babel/core | targeted `pnpm update <pkg>` each | ~9 | transitives |

Method (per lessons.md 2026-04-29): **per-package `pnpm update <pkg>`, never bare `pnpm update`**
(pnpm 10 rewrites specifiers and can pull breaking minors — the recharts incident). Verify per repo
rules: `pnpm lint && pnpm build && pnpm test` **and `format:check`** for frontend AND portal (+ docs
workspace build). One PR, reviewed (no exemptions).

## Wave 2 — tools/claude-slack-bot (13 alerts, 8 high) — DECISION REQUIRED

- **Option A (recommended if the bot is dead): delete the directory.** Evidence of orphanhood: not in
  `pnpm-workspace.yaml`, own lockfile, zero references from `.github/`, `compose/`, `scripts/`, last
  functional commit 2026-02-19 ("replace Agent SDK with local Claude CLI"). Claude-in-Slack (Claude
  Tag) now exists as a product. Kills all 13 alerts.
- **Option B (if still used): lockfile refresh** — `pnpm update axios form-data ws esbuild qs` inside
  the dir → axios 1.16.0, form-data 4.0.6, ws 8.21.0, qs 6.15.2, esbuild 0.28.1. ~30 min incl. a
  smoke run. Real-world risk while unfixed is modest (bot runs locally with dev creds; axios highs are
  SSRF/DoS-class) but 8 highs is 8 highs.

## Wave 3 — compose tooling (4 alerts, 1 high) — low urgency, batch with Wave 1 or defer

- `compose/keycloak/theme`: vite `→ 6.4.3` (high+med), @babel/core `→ 7.29.6` (low). Theme BUILD
  tooling; output is static CSS/HTML for Keycloak. Bump + rebuild theme to verify.
- `compose/mock-idp`: qs `→ 6.15.2` (medium). E2E-only mock IDP, never deployed.

## Severity reconciliation (37 high)

next 21 + axios 6 (slack-bot) + vite 4 (3 workspace + 1 keycloak-theme) + undici 3 + ws 2 +
form-data 1 = 37. → Wave 1 removes 28, Wave 2 removes 8, Wave 3 removes 1.

---
## Wave 1 EXECUTED — PR #1548 merged (`069d99c70`, 2026-07-12)

Result: **89 → 20 open alerts** (69 closed; better than the ~63 estimate because review caught
`docs/package.json` still pinning next `^16.2.4` — both reviewers, independently). Verified post-merge
via the Dependabot API: 9 high / 6 medium / 5 low remain, reconciling exactly to:
- 13 = tools/claude-slack-bot (8 high) — **Wave 2, awaiting delete-vs-keep decision**
- 4 = compose tooling (1 high: keycloak-theme vite) — Wave 3
- 3 = upstream-blocked: postcss 8.4.31 (exact-pinned inside next 16.2.6), esbuild 0.27.7 (pinned by
  vite 7.3.5 `^0.27`). No in-range patch exists; re-check on each next/vite release. Candidates for
  dismissal with reason "vulnerable code not reachable / awaiting upstream".

Verification beyond CI: auth e2e specs (`e2e/tests/auth/*`, 11 passed / 0 failed) ran against the
branch-built mock stack, observing the proxy/middleware surface that next 16.2.6's auth-bypass fixes
touch (reviewer-B requirement; vitest cannot execute edge middleware).

## Wave 2 EXECUTED — PR #1549 merged (`e3e96180b`, 2026-07-12)

`tools/claude-slack-bot/` deleted (owner-authorized). **20 → 7 open alerts**; zero high remain outside
build tooling. Untracked local leftovers (node_modules, .env with Slack tokens) swept from disk too.

Remaining 7 (verified post-merge):
- Wave 3 (4): keycloak-theme vite →6.4.3 (1 high + 1 med, build-time only), @babel/core →7.29.6 (low);
  mock-idp qs →6.15.2 (med, E2E-only).
- Wave 1 straggler (1): root-lock @babel/core 7.29.0 → 7.29.6 (low) — the one targeted transitive the
  lockfile heal missed; fixable, fold into Wave 3.
- Upstream-blocked (2): postcss 8.4.31 (pinned inside next 16.2.6, med), esbuild 0.27.7 (pinned by
  vite 7.3.5, low). Re-check on next/vite releases; dismissal candidates.

## Wave 3 EXECUTED — PR #1550 merged (`6f77138d4`, 2026-07-12)

Theme vite 6.4.3 + babel 7.29.7 (standalone lockfile regen — NOTE: `pnpm install` inside
compose/keycloak/theme picks up the ROOT workspace context and mutates the root lockfile; use
`--ignore-workspace`), mock-idp qs 6.15.3 via npm overrides (express 4 pins ~6.14.0 even at 4.22.1),
root-lock @babel/core 7.29.7 straggler healed. One pre-existing CI flake triaged in the PR body
(invoice-generation-with-expenses waitFor timeout — follow-up candidate, unbundled).

## FINAL STATE: 89 → 2 open alerts (both upstream-blocked)

- postcss 8.4.31 (medium, root lock) — exact-pinned inside next 16.2.6; re-check each next release.
- esbuild 0.27.7 (low, root lock) — pinned by vite 7.3.5 `^0.27`; re-check each vite release.
Both are dismissal candidates ("awaiting upstream patch"); left open so Dependabot re-flags on new advisories.
