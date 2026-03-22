# Phase 54 — Keycloak E2E Test Suite

Playwright E2E test suite running against the full Keycloak dev stack, validating accounting firm onboarding, pack seeding, member invites, and RBAC through the product UI. Retires mock IDP as primary test target.

**Architecture doc**: `architecture/phase54-keycloak-e2e-test-suite.md`
**ADRs**: ADR-206 (test stack unification), ADR-207 (test data strategy), ADR-208 (pack verification approach)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 397 | Docker Compose & Scripts | Infra | -- | S | 397A | **Done** (PR #820) |
| 398 | Playwright Harness — Config, Fixtures, Helpers, POMs | E2E | 397 | M | 398A | **Done** (PR #821) |
| 399 | Onboarding Flow Test | Both | 398 | M | 399A | **Done** (PR #822) |
| 400 | Pack Verification Tests | Both | 399 | L | 400A, 400B | **Done** (PR #823, #824) |
| 401 | Member Invite & RBAC Tests | Both | 399 | M | 401A | **Done** (PR #825) |
| 402 | Existing E2E Test Migration | Both | 398 | S | 402A | |

## Dependency Graph

```
397 (Docker Compose & Scripts)
 |
398 (Playwright Harness)
 |
399 (Onboarding Flow Test)
 |
 +------------------+------------------+
 |                  |                  |
400 (Pack           401 (Member        402 (Migration)
Verification)      Invite & RBAC)
```

**Parallel tracks**:
- 397 → 398 → 399 are strictly sequential.
- After 399: epics 400, 401, and 402 can run in parallel (all depend on onboarding state but not on each other).
- 402 also has a direct dependency on 398 only (not 399) since it uses `keycloak-seed.sh` for its own test data.

## Implementation Order

### Stage 1: Infrastructure

| Slice | Epic | Rationale |
|-------|------|-----------|
| **397A** | 397 | E2E lifecycle scripts, Playwright config updates, deprecation markers. Foundation for everything. | **Done** (PR #820) |

### Stage 2: Test Harness

| Slice | Epic | Rationale |
|-------|------|-----------|
| **398A** | 398 | Auth fixtures, Mailpit helpers, Keycloak POMs, selectors, bootstrap check test. All subsequent tests depend on this. | **Done** (PR #821) |

### Stage 3: Core Test Flow

| Slice | Epic | Rationale |
|-------|------|-----------|
| **399A** | 399 | Onboarding flow creates the org, owner, and tenant that pack verification and RBAC tests depend on. | **Done** (PR #822) |

### Stage 4: Test Suites (Parallel)

| Slice | Epic | Rationale |
|-------|------|-----------|
| **400A** | 400 | data-testid additions for settings pages + defaults/fields/compliance verification. | **Done** (PR #823) |
| **400B** | 400 | Templates/clauses/automations/request templates verification + full test file assembly. | **Done** (PR #824) |
| **401A** | 401 | Member invite + RBAC — invite Bob (Admin) and Carol (Member), verify role-based access. | **Done** (PR #825) |
| **402A** | 402 | Migrate 3 smoke tests, write migration guide, create dev-seed-tenant script. |

---

## Epic 397: Docker Compose & Scripts

**Goal**: Create the E2E lifecycle scripts that prepare the Keycloak dev stack for Playwright tests, update the Playwright configuration for Keycloak-appropriate timeouts and worker settings, and formally deprecate the mock IDP E2E stack. This epic produces zero test files — it is pure infrastructure.

**References**: Architecture doc Sections 1, 7.2, 8 (Slice 54A).

**Dependencies**: None (first epic in Phase 54)

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 397.1 | Create `compose/scripts/dev-e2e-up.sh` | -- | Orchestrator script: calls `dev-down.sh --clean` (if `--clean` flag), then `dev-up.sh --all`, then `keycloak-bootstrap.sh`. Prints summary with service URLs and platform admin credentials. Pattern: follow `compose/scripts/dev-up.sh` for health-check wait loops. Must be `chmod +x`. |
| 397.2 | Create `compose/scripts/dev-e2e-down.sh` | -- | Thin wrapper: calls `dev-down.sh --clean` to wipe all Docker volumes. Pattern: follow `compose/scripts/dev-down.sh`. Must be `chmod +x`. |
| 397.3 | Update `frontend/e2e/playwright.config.ts` | -- | Changes: `timeout: 60_000`, `globalTimeout: 600_000`, `workers: 1`, `actionTimeout: 15_000`, `screenshot: 'only-on-failure'`, `trace: 'on-first-retry'`, `retries: process.env.CI ? 1 : 0`. Keep `baseURL` and `projects` unchanged. |
| 397.4 | Add deprecation comment to `compose/docker-compose.e2e.yml` | -- | YAML comment block at top: `# DEPRECATED — Phase 54 (2026-03): Prefer Keycloak dev stack (docker-compose.yml --all).` Do NOT delete any services. |
| 397.5 | Update `CLAUDE.md` agent navigation section | -- | Add "Keycloak Dev Stack (Primary)" sub-section with URLs (Frontend :3000, Gateway :8443, Backend :8080, Keycloak :8180, Mailpit :8025). Start/stop: `dev-e2e-up.sh` / `dev-e2e-down.sh`. Mark mock-auth section as "(Deprecated Fallback)". |
| 397.6 | Create `compose/.env.keycloak` | -- | Environment overrides: `NEXT_PUBLIC_AUTH_MODE=keycloak`, `SPRING_PROFILES_ACTIVE=local,keycloak`, JWT issuer/JWK URIs for Keycloak. |

### Key Files

**Create:** `compose/scripts/dev-e2e-up.sh`, `compose/scripts/dev-e2e-down.sh`, `compose/.env.keycloak`
**Modify:** `frontend/e2e/playwright.config.ts`, `compose/docker-compose.e2e.yml`, `CLAUDE.md`
**Read:** `compose/scripts/dev-up.sh`, `compose/scripts/dev-down.sh`, `compose/scripts/keycloak-bootstrap.sh`, `compose/docker-compose.yml`

### Architecture Decisions

- **ADR-206**: Formalizes deprecation of mock IDP stack. Keycloak dev stack is primary.
- **Serial execution (`workers: 1`)**: Tests share Keycloak state. Parallel execution would cause race conditions.

---

## Epic 398: Playwright Harness — Config, Fixtures, Helpers, POMs

**Goal**: Build the reusable Playwright test infrastructure: Keycloak auth fixtures, Mailpit email helpers, Keycloak page object models, centralized selectors, and a bootstrap-check smoke test.

**References**: Architecture doc Sections 2.3–2.5, 7.3–7.5.

**Dependencies**: Epic 397

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 398.1 | Create `frontend/e2e/constants/keycloak-selectors.ts` | -- | `SELECTORS` const with `LOGIN` (USERNAME: `#username`, PASSWORD: `#password`, SUBMIT: `#kc-login`) and `REGISTER` (FIRST_NAME: `#firstName`, LAST_NAME: `#lastName`, PASSWORD: `#password`, PASSWORD_CONFIRM: `#password-confirm`, SUBMIT: `input[type="submit"]`). |
| 398.2 | Create `frontend/e2e/page-objects/keycloak-login.page.ts` | -- | `KeycloakLoginPage` class: `waitForReady()` (waits for username field, 15s), `login(email, password)` (fill + click). |
| 398.3 | Create `frontend/e2e/page-objects/keycloak-register.page.ts` | -- | `KeycloakRegisterPage` class: `waitForReady()`, `register(firstName, lastName, password)` (fill all + submit). |
| 398.4 | Create `frontend/e2e/fixtures/keycloak-auth.ts` | -- | Three functions: `loginAsPlatformAdmin(page)`, `loginAs(page, email, password)` (navigates to `${GATEWAY_URL}/oauth2/authorization/keycloak`, uses KeycloakLoginPage POM), `registerFromInvite(page, inviteLink, firstName, lastName, password)` (uses KeycloakRegisterPage POM). |
| 398.5 | Create `frontend/e2e/helpers/mailpit.ts` | -- | Mailpit REST API (`http://localhost:8025/api/v1/`): `waitForEmail(recipient, options?)`, `extractOtp(email)`, `extractInviteLink(email)`, `clearMailbox()`, `getEmails(recipient)`. |
| 398.6 | Create `frontend/e2e/tests/keycloak/bootstrap-check.spec.ts` | -- | 2 tests: (1) platform admin login works, (2) Mailpit API is accessible. Validates infrastructure before full suite runs. |

### Key Files

**Create:** `frontend/e2e/constants/keycloak-selectors.ts`, `frontend/e2e/page-objects/keycloak-login.page.ts`, `frontend/e2e/page-objects/keycloak-register.page.ts`, `frontend/e2e/fixtures/keycloak-auth.ts`, `frontend/e2e/helpers/mailpit.ts`, `frontend/e2e/tests/keycloak/bootstrap-check.spec.ts`
**Read:** `frontend/e2e/fixtures/auth.ts` (existing mock fixture — understand interface), `architecture/phase54-keycloak-e2e-test-suite.md` Sections 2.3–2.5

### Architecture Decisions

- **ADR-206**: New `keycloak-auth.ts` coexists with existing `auth.ts`. New tests import from keycloak fixture.
- **Gateway login endpoint**: OAuth2 PKCE flow is initiated by Gateway BFF, matching production behavior.

---

## Epic 399: Onboarding Flow Test

**Goal**: Write the end-to-end onboarding test (access request → OTP → approval → Keycloak invite → registration → JIT sync) and add `data-testid` attributes to the access request form and platform admin page.

**References**: Architecture doc Section 3.2.

**Dependencies**: Epic 398

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 399.1 | Add `data-testid` to access request form | -- | Modify `frontend/app/request-access/page.tsx` (and child components). Add testids: `request-access-form`, `email-input`, `full-name-input`, `org-name-input`, `country-select`, `industry-select`, `submit-request-btn`, `otp-input`, `verify-otp-btn`, `success-message`. |
| 399.2 | Add `data-testid` to platform admin page | -- | Modify `frontend/app/(app)/platform-admin/access-requests/page.tsx` and children. Add: `access-requests-page`, `pending-tab`, `request-row-{orgName}`, `approve-btn`, `confirm-approve-btn`. |
| 399.3 | Add `data-testid` to team page | -- | Modify `frontend/components/team/member-list.tsx`: `member-row-{email}`. Modify `frontend/components/team/invite-member-form.tsx`: `invite-member-btn`. Used by both 399 and 401. |
| 399.4 | Create `frontend/e2e/tests/keycloak/onboarding.spec.ts` | -- | Serial test with 8 steps: (1) Clear Mailpit. (2) Submit access request (owner@thornton-za.e2e-test.local, "Thornton & Associates", SA, Accounting). (3) Extract OTP from Mailpit, verify. (4) Login as platform admin, approve request. (5) Extract invite link from Mailpit. (6) Register owner via invite. (7) Assert dashboard loads. (8) Verify owner in Team page. |
| 399.5 | Verify Keycloak invite email URL uses localhost | -- | Check `application-keycloak.yml` and `docker-compose.yml` that `KEYCLOAK_AUTH_SERVER_URL` resolves to `localhost:8180` (not Docker hostname). Document finding in test comment. |

### Key Files

**Create:** `frontend/e2e/tests/keycloak/onboarding.spec.ts`
**Modify:** `frontend/app/request-access/page.tsx`, `frontend/app/(app)/platform-admin/access-requests/page.tsx`, `frontend/components/team/member-list.tsx`, `frontend/components/team/invite-member-form.tsx`
**Read:** `frontend/e2e/fixtures/keycloak-auth.ts`, `frontend/e2e/helpers/mailpit.ts`, `compose/scripts/keycloak-bootstrap.sh`

### Architecture Decisions

- **ADR-207**: All data created through product UI. Only platform admin is pre-scripted.

---

## Epic 400: Pack Verification Tests

**Goal**: Verify all accounting-ZA packs through the product UI after provisioning. Each pack type gets its own `test.describe` block. Also adds `data-testid` attributes to Settings pages.

**References**: Architecture doc Sections 3.3.1–3.3.9. ADR-208.

**Dependencies**: Epic 399

### Slices

| Slice | Summary |
|-------|---------|
| **400A** | data-testid additions for settings pages + defaults/fields/compliance verification | **Done** (PR #823) |
| **400B** | Templates/clauses/automations/request templates verification + full test file assembly | **Done** (PR #824) |

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 400.1 | Add `data-testid` to Settings > General (currency) | 400A | `data-testid="default-currency"` on currency display element. |
| 400.2 | Add `data-testid` to Settings > Tax | 400A | `tax-rate-row`, `tax-rate-name`, `tax-rate-value`, `tax-rate-default` on each row. |
| 400.3 | Add `data-testid` to Settings > Rates | 400A | `billing-rate-{role}`, `cost-rate-{role}` on rate rows. |
| 400.4 | Add `data-testid` to Settings > Custom Fields | 400A | `field-group-{slug}` on group headers, `field-row` on each field. |
| 400.5 | Add `data-testid` to Settings > Checklists | 400A | `checklist-template-row`, `checklist-item-row`, `checklist-item-required`. |
| 400.6 | Add `data-testid` to Settings > Templates | 400B | `template-list-item` on each template row. |
| 400.7 | Add `data-testid` to Settings > Clauses | 400B | `clause-row`, `clause-category`, `template-clause-association`. |
| 400.8 | Add `data-testid` to Settings > Automations | 400B | `automation-row` on each rule. |
| 400.9 | Add `data-testid` to Settings > Request Templates | 400B | `request-template-row`, `request-item-row`, `request-item-required`. |
| 400.10 | Create `accounting-za-packs.spec.ts` | 400B | 10 describe blocks: (1) Default currency ZAR, (2) VAT 15%, (3) Rate card defaults (known gap), (4) Customer fields (16), (5) Project fields (5), (6) Trust fields, (7) FICA checklist (11 items), (8) Templates (7), (9) Clauses (7 + 3 associations), (10) Automations (4), (11) Request template (8 items). Assert on specific names, not counts. |

### Key Files

**400A Create:** (data-testid additions only)
**400A Modify:** Settings pages for general, tax, rates, custom-fields, checklists
**400B Create:** `frontend/e2e/tests/keycloak/accounting-za-packs.spec.ts`
**400B Modify:** Settings pages for templates, clauses, automations, request-templates

### Architecture Decisions

- **ADR-208**: Pack seeding validated through UI exclusively. Failing tests on rate/tax/currency defaults surface provisioning gaps (not test bugs).

---

## Epic 401: Member Invite & RBAC Tests

**Goal**: Test member invitation via Keycloak (admin + member), registration from invite link, JIT sync, and role-based access control verification for all three roles.

**References**: Architecture doc Sections 3.4, 4.2, 7.4.

**Dependencies**: Epic 399

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 401.1 | Verify/add team page data-testid coverage | -- | Check 399.3 testids are sufficient. Add `role-select`, `pending-invite-row`, `member-role-badge` if missing. |
| 401.2 | Invite admin (Bob) test section | -- | Login as owner, invite `bob@thornton-za.e2e-test.local` with Admin role, extract invite from Mailpit, register in new browser context, verify in team page. |
| 401.3 | Invite member (Carol) test section | -- | Same flow: `carol@thornton-za.e2e-test.local`, Member role, "Carol Mokoena". |
| 401.4 | RBAC admin verification (Bob) | -- | New context: login as Bob. Can access: Dashboard, Settings > General, Customers, Projects, Invoices. Cannot: delete org, billing settings. |
| 401.5 | RBAC member verification (Carol) | -- | New context: login as Carol. Can access: My Work. Cannot: Settings > Rates, Settings > General, profitability, cost rates. |
| 401.6 | Assemble `member-invite-rbac.spec.ts` | -- | Single serial file: invite Bob → invite Carol → RBAC Bob → RBAC Carol. Separate browser contexts per user for RBAC checks. |

### Key Files

**Create:** `frontend/e2e/tests/keycloak/member-invite-rbac.spec.ts`
**Modify:** `frontend/components/team/invite-member-form.tsx`, `frontend/components/team/member-list.tsx` (if additional testids needed)

### Architecture Decisions

- **ADR-207**: Bob and Carol created through product invite flow, not Keycloak admin API.
- **New browser context per user**: Fresh cookie jar per role avoids session leakage.

---

## Epic 402: Existing E2E Test Migration

**Goal**: Migrate 3 smoke tests as proof-of-concept, create migration guide for remaining 50+ files, create optional tenant seed script.

**References**: Architecture doc Section 5.

**Dependencies**: Epic 398 (does NOT depend on 399)

### Tasks

| # | Task | Slice | Notes |
|---|------|-------|-------|
| 402.1 | Create `existing-migration.spec.ts` | -- | Adapted copy of `smoke.spec.ts`: replace mock auth with Keycloak auth, update org slug to `acme-corp`, update URLs. 3 tests. Original retained. |
| 402.2 | Create `compose/scripts/dev-seed-tenant.sh` | -- | Seed script: runs `keycloak-seed.sh`, then provisions `acme-corp` tenant via backend internal API. Decouples migrated tests from onboarding flow. |
| 402.3 | Create `frontend/e2e/MIGRATION-GUIDE.md` | -- | Step-by-step: auth fixture replacement, URL changes (3001→3000, 8081→8080, 8026→8025), user mapping, org slug change, example diff, remaining file checklist. |
| 402.4 | Update `compose/scripts/README.md` | -- | Add Keycloak E2E section, mark mock IDP scripts deprecated. |

### Key Files

**Create:** `frontend/e2e/tests/keycloak/existing-migration.spec.ts`, `compose/scripts/dev-seed-tenant.sh`, `frontend/e2e/MIGRATION-GUIDE.md`
**Modify:** `compose/scripts/README.md`
**Read:** `frontend/e2e/tests/smoke.spec.ts`, `compose/scripts/keycloak-seed.sh`

### Architecture Decisions

- **ADR-206**: Incremental migration. Only 3 smoke tests migrated in Phase 54. Original `smoke.spec.ts` retained.
- **`dev-seed-tenant.sh` decouples migration from onboarding**: Migrated tests don't depend on the onboarding flow test.
