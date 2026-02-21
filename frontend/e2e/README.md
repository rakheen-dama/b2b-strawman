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

## Notes

- E2E tests (`pnpm test:e2e`) are separate from unit tests (`pnpm test`).
  Unit tests do not require the E2E stack.
- The `loginAs()` fixture in `fixtures/auth.ts` fetches a JWT from the mock IDP
  at `http://localhost:8090` and sets the `mock-auth-token` cookie directly —
  no browser UI interaction required.
- Seed data: org slug `e2e-test-org`, users alice (owner), bob (admin), carol (member),
  customer "Acme Corp", project "Website Redesign".
