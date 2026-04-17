# Consulting Lifecycle — Playwright Regression Baselines

Auto-populated by Playwright's `toHaveScreenshot()` on the first green run of `frontend/e2e/tests/consulting-lifecycle/screenshots.spec.ts` (driven by `frontend/e2e/playwright.consulting-lifecycle.config.ts`).

Mirrors `frontend/e2e/screenshots/legal-lifecycle/` — empty placeholder until baselines are captured.

## How to populate

```bash
bash compose/scripts/e2e-up.sh
cd frontend
PLAYWRIGHT_BASE_URL=http://localhost:3001 \
  pnpm exec playwright test \
  --config e2e/playwright.consulting-lifecycle.config.ts \
  --update-snapshots
```

Subsequent runs (without `--update-snapshots`) compare against the committed baselines.

## Baselines captured by `screenshots.spec.ts`

- `day-00-dashboard-utilization-widget.png`
- `day-05-project-campaign-type.png`
- `day-14-creative-brief-request.png`
- `day-30-monthly-retainer-report.png`
- `day-60-sow-agency-clauses.png`
