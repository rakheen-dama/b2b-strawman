# Portal Client-POV 90-Day — Playwright Visual Baselines

This directory holds the Playwright `toHaveScreenshot()` baseline PNGs for the **Portal Client-POV 90-Day** lifecycle (Phase 68 / Epic 500), captured at the `lg` viewport via `portal/playwright.portal.config.ts` (project `portal-client-90day`).

## Status

**Empty placeholder.** Baselines are populated on the **first** Playwright run with `--update-snapshots`. Slice 500B (this PR) ships the directory + this README; baseline PNGs are deferred to a human-operator live-stack run because the lifecycle requires three vertical Keycloak tenants to be pre-provisioned (estimated 60+ minutes of firm-side `/qa-cycle-kc` runs before the portal lifecycle can begin).

The deferral is documented in `tasks/phase68-gap-report.md` (`GAP-013` — curated PNGs deferred; `GAP-009` — no three-vertical seed script).

## Snapshot path drift caveat (important)

The path **`portal/e2e/screenshots/portal-v2/portal-client-90day/`** is correct (it matches the `snapshotPathTemplate` in `portal/playwright.portal.config.ts`):

```ts
snapshotPathTemplate: "{testDir}/../../screenshots/portal-v2/{projectName}/{arg}{ext}"
```

Resolving with `testDir = ./e2e/tests`, `projectName = portal-client-90day`, and `arg = "day-00-home-landing.png"` (which already includes its own `.png`), the resolved path is:

```text
portal/e2e/screenshots/portal-v2/portal-client-90day/day-00-home-landing.png
```

**Note**: the original Phase 68 task-brief table (`tasks/phase68-portal-redesign-vertical-parity.md` — task 500.6) documents the path as `portal/e2e/screenshots/portal-client-90day/` (without the `portal-v2/` segment). The actual landing path is the `portal-v2/portal-client-90day/` form because:

- 500A introduced the lifecycle project alongside the existing 499B `chromium` responsive project under a shared `portal-v2/` snapshot root.
- Changing the template now would invalidate the Epic 499B baselines that already live under `portal-v2/chromium/`.

The path drift is logged as `GAP-012` in `tasks/phase68-gap-report.md` (cosmetic — naming-drift between brief and config). Recommended resolution: update the task brief to match the config (not vice versa).

## Snapshot file naming

The current `snapshotPathTemplate` does **not** include `{platform}` or `{browserName}` segments. This means:

- Baselines are platform-implicit (captured on whatever OS first ran `--update-snapshots`).
- Re-running on a different OS will produce visual diff (font rendering, sub-pixel anti-aliasing).
- **Acceptable** for single-platform local capture during Phase 68; flagged as a follow-up if cross-OS CI is later wired up (see `GAP-011` in the gap report).

## Capture procedure

### Prerequisites

1. **Keycloak dev stack**:
   ```bash
   bash compose/scripts/dev-up.sh
   bash compose/scripts/keycloak-bootstrap.sh
   bash compose/scripts/keycloak-seed.sh
   bash compose/scripts/svc.sh start all
   bash compose/scripts/svc.sh status   # all services healthy
   ```

2. **Three firm-side lifecycles run** (each provisions its own vertical tenant + portal contact):
   ```bash
   /qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md           # → mathebula-partners (Days 0–21 minimum)
   /qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md   # → ledger-collective (Days 0–60)
   /qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md  # → keystone-consulting (Days 0–30)
   ```

   **There is no single-command three-vertical seeder today** — see `GAP-009` in the gap report. A future `bash compose/scripts/seed-three-verticals-kc.sh` script would shorten this from ~60 min to ~10 min.

### First run (baseline generation)

Run **per tenant**, swapping `PORTAL_CONTACT_EMAIL` + `PORTAL_ORG_SLUG`:

```bash
cd portal

# Tenant 1: accounting-za (ledger-collective)
PORTAL_CONTACT_EMAIL=zola.portal@example.com \
  PORTAL_ORG_SLUG=ledger-collective \
  BACKEND_URL=http://localhost:8080 \
  PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm exec playwright test \
  --config=playwright.portal.config.ts \
  --project=portal-client-90day \
  --update-snapshots

# Tenant 2: consulting-za (keystone-consulting)
PORTAL_CONTACT_EMAIL=thembi.portal@example.com \
  PORTAL_ORG_SLUG=keystone-consulting \
  BACKEND_URL=http://localhost:8080 \
  PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm exec playwright test \
  --config=playwright.portal.config.ts \
  --project=portal-client-90day \
  --update-snapshots

# Tenant 3: legal-za (mathebula-partners) — run LAST so it wins as canonical narrator
PORTAL_CONTACT_EMAIL=sipho.portal@example.com \
  PORTAL_ORG_SLUG=mathebula-partners \
  BACKEND_URL=http://localhost:8080 \
  PLAYWRIGHT_BASE_URL=http://localhost:3002 \
  NODE_OPTIONS="" pnpm exec playwright test \
  --config=playwright.portal.config.ts \
  --project=portal-client-90day \
  --update-snapshots
```

**Important**: snapshot filenames are tenant-agnostic (e.g. `day-00-home-landing.png`). Three sequential `--update-snapshots` runs **will overwrite each other's baselines**. Strategy:

- For `[all profiles]` checkpoints, the **last tenant to run wins** — accept the legal-za narrator (`sipho.portal@example.com`) as the canonical baseline by running it **last**.
- For profile-gated checkpoints (`day-21-trust-balance.png` is legal-za-only, `day-30-retainer-hour-bank.png` is consulting-za / legal-za, etc.), only the matching tenant's run captures a non-skipped baseline. The skipped tenants leave the existing baseline untouched.

This trade-off is logged in the gap report (`GAP-016` — single-canonical-baseline-per-shot constraint).

### Second run (verification — no `--update-snapshots`)

Run **once** (any tenant) to confirm baselines hold:

```bash
cd portal
NODE_OPTIONS="" pnpm test:e2e:portal-client-90day
```

Or equivalently:

```bash
NODE_OPTIONS="" pnpm exec playwright test \
  --config=playwright.portal.config.ts \
  --project=portal-client-90day
```

Any failure here is either:
- A real flake (suspect timestamp drift — see Risk #8 in the slice brief; the 500A specs do not yet install a frozen clock).
- A real visual regression (log as a new gap; do **not** re-baseline blindly).

## Determinism notes (Risk #8)

The 500A specs use `waitForLoadState('domcontentloaded') + waitForTimeout(500)` before each `toHaveScreenshot()`. If first re-run flakiness appears on timestamp-bearing surfaces, the recommended escalation is:

1. Switch to `await page.waitForLoadState('networkidle')`.
2. Add `await page.clock().install({ time: new Date('2026-01-01T08:00:00Z') })` inside `beforeEach`.

Both are documented as follow-up tooling improvements in `tasks/phase68-gap-report.md` (`GAP-015`). They are **not** applied in slice 500B (no spec-file edits in this PR — that scope belonged to 500A).

## Expected baselines (15 files, profile-gated subset)

| File | Source spec | Profile gating |
|---|---|---|
| `day-00-home-landing.png` | `day-00-07.spec.ts` | all profiles |
| `day-00-info-request-detail.png` | `day-00-07.spec.ts` | all profiles |
| `day-03-info-request-submitted.png` | `day-00-07.spec.ts` | all profiles |
| `day-07-deadlines-list.png` | `day-00-07.spec.ts` | accounting-za / legal-za only |
| `day-14-proposal-review.png` | `day-14-30.spec.ts` | all profiles |
| `day-14-proposal-accepted.png` | `day-14-30.spec.ts` | all profiles |
| `day-21-trust-balance.png` | `day-14-30.spec.ts` | legal-za only |
| `day-30-retainer-hour-bank.png` | `day-14-30.spec.ts` | consulting-za / legal-za |
| `day-45-invoice-detail.png` | `day-45-75.spec.ts` | all profiles |
| `day-45-invoice-paid.png` | `day-45-75.spec.ts` | all profiles |
| `day-60-document-downloaded.png` | `day-45-75.spec.ts` | legal-za / accounting-za |
| `day-75-deadline-nudge-read.png` | `day-45-75.spec.ts` | accounting-za / legal-za |
| `day-85-profile-updated.png` | `day-85-90.spec.ts` | all profiles |
| `day-85-notifications-biweekly.png` | `day-85-90.spec.ts` | all profiles |
| `day-90-activity-trail.png` | `day-85-90.spec.ts` | all profiles |

Total: 15 distinct PNGs after a full three-tenant run.

## Convention

These are **regression baselines** — managed by Playwright. Curated marketing/demo PNGs live in `documentation/screenshots/portal/` (different convention, captured manually).
