# Keycloak E2E Testing Guide

How to run Playwright E2E tests against the Keycloak dev stack (real OIDC auth, no mock IDP).

## Prerequisites

- Docker running (for Postgres, LocalStack, Mailpit, Keycloak)
- Node.js + pnpm installed
- Java 25 + Maven installed

## 1. Start Infrastructure

```bash
bash compose/scripts/dev-up.sh
```

Waits for: Postgres (5432), LocalStack (4566), Mailpit (8025), Keycloak (8180).

## 2. Bootstrap Keycloak (first time only)

```bash
bash compose/scripts/keycloak-bootstrap.sh
```

Creates: platform admin (`padmin@docteams.local` / `password`), protocol mappers, `platform-admins` group.

## 3. Start Local Services (4 terminals)

```bash
# Terminal 1 — Backend
cd backend
SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run

# Terminal 2 — Gateway
cd gateway
./mvnw spring-boot:run

# Terminal 3 — Frontend
cd frontend
NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev

# Terminal 4 — Portal (optional, for portal tests)
cd portal
pnpm dev
```

## 4. Verify Stack Health

```bash
curl -sf http://localhost:8080/actuator/health  # Backend
curl -sf http://localhost:8443/actuator/health  # Gateway
curl -sf http://localhost:3000/                 # Frontend
curl -sf http://localhost:8180/realms/docteams  # Keycloak
curl -sf http://localhost:8025/api/v1/messages  # Mailpit API
```

## 5. Run Keycloak E2E Tests

```bash
cd frontend

# Run all Keycloak tests (onboarding + member invite/RBAC)
E2E_AUTH_MODE=keycloak npx playwright test keycloak/ --config e2e/playwright.config.ts --reporter=list

# Run just the onboarding test
E2E_AUTH_MODE=keycloak npx playwright test keycloak/onboarding --config e2e/playwright.config.ts --reporter=list

# Debug with visible browser
E2E_AUTH_MODE=keycloak npx playwright test keycloak/onboarding --config e2e/playwright.config.ts --headed
```

### What the Tests Do

**`onboarding.spec.ts`** (4 serial tests):
1. Navigates to `/request-access`, fills form (email, name, org, South Africa, Accounting), submits
2. Reads OTP from Mailpit API, enters it, verifies success
3. Logs in as platform admin, approves the request
4. Reads Keycloak invitation from Mailpit, completes registration, verifies first login

**`member-invite-rbac.spec.ts`** (7 serial tests, depends on onboarding):
1. Owner logs in, upgrades to Pro plan
2. Owner invites Bob (admin role)
3. Bob registers from Keycloak invitation email
4. Owner invites Carol (member role)
5. Carol registers from Keycloak invitation email
6. Verifies Bob (admin) CAN access settings
7. Verifies Carol (member) CANNOT access rates/profitability

### Test Dependencies

The member-invite test reads state from `/tmp/e2e-keycloak-state.json` written by the onboarding test. Always run them in order (Playwright runs files alphabetically within a directory, and `member-invite` comes after `onboarding`).

## 6. Run Existing Mock-Auth Tests (unchanged)

The original E2E tests still work against the mock-auth stack:

```bash
# Start E2E stack
bash compose/scripts/e2e-up.sh

# Run mock-auth tests
cd frontend
PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test smoke --config e2e/playwright.config.ts
```

## Service Port Reference

| Service | Keycloak Dev Stack | E2E Mock-Auth Stack |
|---------|-------------------|---------------------|
| Frontend | localhost:3000 | localhost:3001 |
| Backend | localhost:8080 | localhost:8081 |
| Gateway | localhost:8443 | N/A |
| Keycloak | localhost:8180 | N/A |
| Mock IDP | N/A | localhost:8090 |
| Postgres | localhost:5432 | localhost:5433 |
| Mailpit UI | localhost:8025 | localhost:8026 |
| Mailpit API | localhost:8025/api/v1/ | localhost:8026/api/v1/ |
| Portal | localhost:3002 | N/A |

## Key Files

| File | Purpose |
|------|---------|
| `frontend/e2e/fixtures/keycloak-auth.ts` | `loginAs()`, `registerFromInvite()`, `loginAsPlatformAdmin()` |
| `frontend/e2e/fixtures/keycloak-selectors.ts` | Keycloak form field IDs (update if theme changes) |
| `frontend/e2e/helpers/mailpit.ts` | `waitForEmail()`, `extractOtp()`, `extractInviteLink()` |
| `frontend/e2e/helpers/e2e-state.ts` | Cross-spec state passing via `/tmp/e2e-keycloak-state.json` |
| `frontend/e2e/playwright.config.ts` | Dual-mode config (`E2E_AUTH_MODE=mock|keycloak`) |
| `compose/scripts/check-playwright-port.sh` | PreToolUse hook (allows port 3000 when `E2E_AUTH_MODE=keycloak`) |

## Troubleshooting

**Keycloak login form not loading**: Check gateway is running (`localhost:8443`). The frontend middleware redirects to the gateway OAuth2 endpoint which redirects to Keycloak.

**OTP email not arriving**: Check Mailpit UI at `http://localhost:8025`. Verify the backend's SMTP config points to `localhost:1025` (Mailpit SMTP port).

**Invite email not arriving**: Keycloak sends invites via its own SMTP config (defined in `realm-export.json` — host: `mailpit`, port: 1025). If Keycloak can't reach Mailpit on the Docker network, the invite won't send.

**Login redirects to blank page**: The gateway session may have expired. Clear browser cookies and retry.

**Tests skip with "state file missing"**: The member-invite test requires the onboarding test to run first. Run both: `npx playwright test keycloak/`.

---

## QA Test Plan Compatibility

All existing test plans in `qa/testplan/` were written for the E2E mock-auth stack and need updates to run against the Keycloak dev stack.

| Test Plan | Status | What Needs Changing |
|-----------|--------|---------------------|
| `48-lifecycle-script.md` | **NEEDS UPDATE** | Ports (3001→3000, 8081→8080, 8026→8025), login flow (`/mock-login` → Keycloak OIDC), org slug (`e2e-test-org` → dynamic), `e2e-up.sh` → local services, Day 0 should use real onboarding flow |
| `phase49-document-content-verification.md` | **NEEDS UPDATE** | Stack header (ports), `e2e-up.sh` bootstrap command, mock-idp (8090) reference, port enumeration |
| `portal-experience-proposal-acceptance.md` | **NEEDS UPDATE** | Stack header (ports), explicit "mock-auth E2E stack only" scope note |
| `automation-notification-verification.md` | **NEEDS UPDATE** | Stack header (ports), Mailpit API port (8026→8025) |
| `data-integrity-financial-accuracy.md` | **NEEDS UPDATE** | Stack header only (ports) — low effort |
| `regression-test-suite.md` | **NEEDS UPDATE** | Stack declaration, "E2E mock-auth stack (port 3001)" reference, data strategy section |

### Key Changes for All Plans

When updating test plans for Keycloak mode:

1. **Stack header**: Replace `E2E mock-auth on port 3001 / backend 8081 / Mailpit 8026` with `Keycloak dev stack: frontend 3000 / backend 8080 / gateway 8443 / Keycloak 8180 / Mailpit 8025`

2. **Login instructions**: Replace `/mock-login → click Sign In` with `Navigate to /dashboard → redirected to Keycloak login → fill email + password → submit`

3. **Org slug**: Replace `e2e-test-org` with the org slug created during onboarding (dynamic per test run)

4. **Users**: Replace pre-seeded Alice/Bob/Carol with users created through the real onboarding + invite flow. The platform admin (`padmin@docteams.local`) is the only pre-created user.

5. **Prerequisites**: Replace `bash compose/scripts/e2e-up.sh` with the local services setup (see sections 1-4 above)

6. **Mailpit ports**: Replace `8026` with `8025`

### What Can Run As-Is with `/qa-cycle-kc`

The QA agent dispatched by `/qa-cycle-kc` interprets test plans at runtime, so it can adapt to the Keycloak environment even if the test plan text references mock-auth — **as long as the QA agent prompt in the skill describes the Keycloak environment correctly** (which it now does). However, for human-readable accuracy, the test plans should be updated.

**Recommendation**: Update the stack headers and login instructions in all 6 files. The bulk of the test steps (create customer, log time, generate invoice, etc.) are environment-agnostic and don't need changes. The biggest rewrite is `48-lifecycle-script.md` Day 0 (firm setup) which assumes mock-login and pre-seeded data — this should be replaced with the real onboarding flow.
