# E2E Tests

Playwright smoke tests for the DocTeams E2E stack.

## Prerequisites

- Docker and Docker Compose installed
- Node.js / pnpm installed
- Playwright browsers installed (see below)

## 1. Start the E2E Stack

From the repo root:

```bash
cd compose
docker compose -f docker-compose.e2e.yml up -d
```

Wait for all services to be healthy. The seed container runs once and exits — that's expected.
You can monitor with:

```bash
docker compose -f docker-compose.e2e.yml logs -f seed
```

When you see `E2E Boot-Seed Complete!`, the stack is ready.

Services exposed on the host:

- Frontend: http://localhost:3001 (mapped from container port 3000)
- Backend: http://localhost:8081
- Mock IDP: http://localhost:8090

Note: Playwright tests default to `http://localhost:3000` as `baseURL`. To run tests against
the Dockerized frontend (port 3001), set the `PLAYWRIGHT_BASE_URL` env var:

```bash
PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e
```

Alternatively, run the frontend locally (`pnpm dev`) pointing at the E2E backend.

## 2. Install Playwright Browsers

From `frontend/`:

```bash
npx playwright install chromium
```

## 3. Run the Tests

From `frontend/`:

```bash
pnpm test:e2e
```

Or to run a specific test file:

```bash
pnpm test:e2e e2e/tests/smoke.spec.ts
```

## 4. Stop the Stack

```bash
cd compose
docker compose -f docker-compose.e2e.yml down
```

To also remove volumes (reset seed data):

```bash
docker compose -f docker-compose.e2e.yml down -v
```

## Legal Lifecycle Screenshots

The legal lifecycle test suite captures screenshots for two purposes:

### Regression Baselines

Visual regression tests use Playwright's `toHaveScreenshot()` to compare against committed
baseline images. If the UI changes unexpectedly, the test fails with a diff.

- **Directory:** `frontend/e2e/screenshots/legal-lifecycle/`
- **Naming convention:** `day-{DD}-{feature}-{state}.png` (e.g., `day-00-dashboard-initial.png`)
- **Committed to git:** Yes — baselines must be checked in so CI can compare against them

To update baselines after intentional UI changes:

```bash
pnpm test:e2e:legal-lifecycle -- --update-snapshots
```

### Curated Documentation Screenshots

High-quality screenshots saved for walkthroughs, blog posts, and documentation.
These are captured using `page.screenshot()` and saved to a separate directory.

- **Directory:** `documentation/screenshots/legal-vertical/`
- **Naming convention:** Descriptive names (e.g., `firm-dashboard-overview.png`)
- **Committed to git:** Yes

### Running the Tests

```bash
# Run all legal lifecycle tests
pnpm test:e2e:legal-lifecycle

# Run against the Dockerized E2E stack
PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle

# List tests without executing (no E2E stack needed)
npx playwright test --config e2e/playwright.legal-lifecycle.config.ts --list
```

### Screenshot Helper

The `captureScreenshot()` helper in `e2e/helpers/screenshot.ts` supports both modes:

```typescript
import { captureScreenshot } from "../../helpers/screenshot";

// Regression baseline (default) — compared against committed snapshot
await captureScreenshot(page, "day-05-matter-list");

// Curated documentation shot — saved to documentation/screenshots/
await captureScreenshot(page, "matter-list-overview", { curated: true });

// Full-page capture
await captureScreenshot(page, "day-05-full-dashboard", { fullPage: true });

// Capture a specific element
await captureScreenshot(page, "day-05-sidebar", { locator: page.locator("nav") });
```

## Legal Depth-II Suite (Phase 67)

`e2e/tests/legal-depth-ii/` (config `e2e/playwright.legal-depth-ii.config.ts`) exercises
disbursements, conveyancing + OTP acceptance, statements of account and matter closure on
the legal-za profile.

**Standalone:** it needs only a freshly seeded mock-auth E2E stack (the seed provisions
`e2e-test-org` on the legal-za profile with the disbursements / trust accounting /
matter-closure modules enabled). It does **not** depend on the legal-lifecycle suite: every
client, matter, billing rate, trust account and record it acts on is created through the UI with run-scoped
names (`Sipho Ndlovu <run>`, `Khumalo Property Holdings <run>`, `Apex Holdings <run>`).

```bash
bash compose/scripts/e2e-up.sh                       # from the repo root
# from frontend/
PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
```

- Run the four files together, in order (`workers: 1`): Day 5 starts the run (run id in
  `$TMPDIR/kazi-legal-depth-ii-run.json`) and creates Sipho's client + matter that Day 30-45
  and Day 85 act on. Days 14 and 75 create their own clients but name them from the run id.
- Safe to re-run on the same stack (each run uses a new run id; a CI retry of a serial group
  creates attempt-scoped records).
- Mailpit: the config defaults `MAILPIT_URL` to the E2E stack's `http://localhost:8026`.
- Tests prefixed `[setup]` prepare state the scenario assumes; `test.fixme` tests document
  reproduced product bugs, each tagged `FIXME(depth-ii:<slug>)` in the spec.

## Notes

- E2E tests (`pnpm test:e2e`) are separate from unit tests (`pnpm test`).
  Unit tests do not require the E2E stack.
- The `loginAs()` fixture in `fixtures/auth.ts` fetches a JWT from the mock IDP
  at `http://localhost:8090` and sets the `mock-auth-token` cookie directly —
  no browser UI interaction required.
- Seed data: org slug `e2e-test-org`, users alice (owner), bob (admin), carol (member),
  customer "Acme Corp", project "Website Redesign".
