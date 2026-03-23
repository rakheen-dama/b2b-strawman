You are a senior SaaS architect working on an existing multi-tenant "Kazi" platform (formerly DocTeams).

The current system already has:

- **Auth abstraction** (Phase 20): Build-time provider selection via `NEXT_PUBLIC_AUTH_MODE` (`keycloak` | `mock`). Keycloak mode uses a Spring Cloud Gateway BFF (`/bff/me`). Mock mode uses a Node.js mock IDP with JWT cookies.
- **Keycloak integration**: Keycloak 26.x with realm `docteams`, `gateway-bff` client, `platform-admins` group, org membership, JIT member sync (`MemberSyncService`). Protocol mappers for `groups` and `org_role` claims on JWT.
- **Admin-approved org provisioning** (Phase 39): Public access request form → OTP email verification → platform admin approval → Keycloak org creation → tenant schema provisioning → owner invitation via Keycloak. `AccessRequest` entity with status flow: `PENDING_VERIFICATION` → `PENDING` → `APPROVED`/`REJECTED`.
- **Accounting-ZA vertical profile** (Phase 47): `vertical-profiles/accounting-za.json` manifest drives pack filtering during provisioning. Industry "Accounting" + country "South Africa" maps to profile `accounting-za`.
- **Member invites**: Product-initiated via `KeycloakAdminClient.inviteMember()` → Keycloak sends invitation email → invitee registers → JIT sync on first login creates tenant member with assigned `OrgRole`.
- **Existing E2E stack** (Phase 20): `docker-compose.e2e.yml` with mock IDP on port 3001/8081/8090. Playwright tests at `e2e/`. This stack was designed to bypass Clerk — with Keycloak, it's superseded.
- **Docker Compose dev stack**: `docker-compose.yml` with Keycloak (8180), Gateway (8443), Backend (8080), Frontend (3000), Postgres, LocalStack, Mailpit. Scripts: `keycloak-bootstrap.sh` (platform admin + protocol mappers), `keycloak-seed.sh` (dev org + users).
- **Rate cards** (Phase 8): Billing rates (3-level hierarchy: org → project → customer) and cost rates. `OrgSettings` for default currency.
- **90-day lifecycle test plan**: Manual QA script at `tasks/48-lifecycle-script.md` covering full SA accounting firm lifecycle from firm setup through quarterly review. Currently runs against mock IDP E2E stack.

***

## Objective of Phase 54

Design and specify a **Playwright E2E test suite running against the full Keycloak dev stack** that validates the complete accounting firm onboarding lifecycle through the product UI. This phase:

1. **Retires the mock IDP as the primary test target** — Keycloak provides full control and testability. The mock IDP E2E stack is deprecated (not deleted).
2. **Updates `docker-compose.yml`** to include all services needed for a complete test run (backend, frontend, gateway, portal) built from source.
3. **Tests the real auth path** — Playwright interacts with the Keycloak login page directly. No programmatic shortcuts, no token injection.
4. **Validates accounting-ZA pack seeding** — every pack imported during provisioning is verified through the product UI.
5. **Tests the member invite → registration → RBAC flow** end-to-end via Mailpit.
6. **Migrates existing Playwright smoke tests** to run against the Keycloak dev stack.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack: Playwright, Spring Boot 4, Next.js 16, Keycloak 26.x, Mailpit.
- Do not introduce:
    - Programmatic auth shortcuts (token endpoint calls, cookie injection). All login flows go through the Keycloak login page via Playwright.
    - New auth providers or modes. Tests run against `NEXT_PUBLIC_AUTH_MODE=keycloak`.
    - CI-specific Keycloak images or configurations. Use the same `docker-compose.yml` that developers use locally.
- **Mailpit API** (`localhost:8025/api/v1/`) for email assertions — search by recipient, extract OTP codes, extract invitation links. Do not rely on Mailpit web UI for assertions.
- **Only the platform admin is pre-scripted** via `keycloak-bootstrap.sh`. All other users (org owner, members) are created through the product's UI flows.
- **Keycloak manages**: identity, org registry (names + members), invite email delivery.
- **Product manages**: tenant roles (`OrgRole` entity in tenant schema), not Keycloak's `org_role` attribute.

2. **Docker Compose updates**

The current `docker-compose.yml` starts Keycloak, Postgres, LocalStack, and Mailpit as infrastructure services. It needs to be extended to include application services built from source:

- **Backend** (Spring Boot): Built from `backend/` directory, profile `keycloak`, port 8080. JWT validation against Keycloak JWKS endpoint.
- **Frontend** (Next.js): Built from `frontend/` directory, `NEXT_PUBLIC_AUTH_MODE=keycloak`, port 3000. Routed through Gateway for auth.
- **Gateway** (Spring Cloud Gateway): BFF layer on port 8443. Routes `/bff/*` to Keycloak, proxies `/api/*` to backend.
- **Seed service** (optional one-shot container): Runs `keycloak-bootstrap.sh` after Keycloak is healthy to ensure platform admin exists.

Health checks must be in place for all services before Playwright starts.

3. **Test data strategy**

- Tests create ALL data through the product UI — no direct DB seeding, no Keycloak admin API calls from tests.
- The only pre-existing state is: Keycloak realm (`docteams`), platform admin user (`padmin@docteams.local` / `password`), protocol mappers, `platform-admins` group.
- Each test run starts from a clean state (fresh Postgres, fresh Keycloak — or at minimum, a reseed mechanism).
- Test email addresses use a consistent domain (e.g., `@e2e-test.local`) to make Mailpit searches reliable.

4. **Permissions model**

- Platform admin: identified by `platform-admins` group membership in JWT. Can access `/platform-admin/**` routes.
- Org owner: first user to register after org approval. Full access to all settings.
- Org admin: invited with admin role. Can manage team, customers, projects. Cannot delete org.
- Org member: invited with member role. Can view assigned work, log time. Cannot access Settings > Rates, Settings > General, Settings > Team (invite), or financial data.

***

## Section 1 — Playwright Test Harness

### 1.1 Playwright configuration

- Base URL: `http://localhost:3000` (frontend through gateway at `localhost:8443` for auth flows)
- Timeout: 60s per test (Keycloak login is slower than mock auth)
- Retries: 1 on CI, 0 locally
- Workers: 1 (serial execution — tests share Keycloak state)
- Screenshots: on failure
- Traces: on first retry

### 1.2 Auth fixtures

Create a `keycloak-auth.ts` fixture module:

- `loginAsPlatformAdmin(page)` — navigates to app, redirected to Keycloak login, fills `padmin@docteams.local` / `password`, submits, waits for redirect back to app.
- `loginAs(page, email, password)` — generic Keycloak login for dynamically-created users.
- `registerFromInvite(page, inviteLink, firstName, lastName, password)` — follows Keycloak invite link, fills registration form, completes account creation.
- All fixtures must handle the Keycloak login page's form selectors (username/email field, password field, submit button). These selectors should be in a constants file since Keycloak themes may change them.

### 1.3 Mailpit helpers

Create a `mailpit.ts` helper module using Mailpit's REST API (`http://localhost:8025/api/v1/`):

- `waitForEmail(recipient, subject?, timeout?)` — polls Mailpit API until matching email arrives. Returns parsed email.
- `extractOtp(email)` — extracts 6-digit OTP code from email body (regex).
- `extractInviteLink(email)` — extracts Keycloak invitation/registration link from email body.
- `clearMailbox()` — deletes all emails. Called in test setup.
- `getEmails(recipient)` — returns all emails for a given recipient address.

### 1.4 Stack lifecycle

Create a `dev-e2e-up.sh` script (or update existing `dev-up.sh`) that:

1. Builds backend, frontend, gateway from source.
2. Starts all services via `docker-compose.yml`.
3. Waits for health checks: Keycloak realm ready, backend `/actuator/health`, frontend `200 OK`, gateway `200 OK`, Mailpit API.
4. Runs `keycloak-bootstrap.sh` (idempotent — safe to re-run).
5. Prints summary with service URLs.

Corresponding `dev-e2e-down.sh` for teardown + volume cleanup.

***

## Section 2 — Access Request & Org Provisioning Tests

### Test: `accounting-firm-onboarding.spec.ts`

**Scenario: Full access request → approval → registration flow**

Steps:

1. **Clear Mailpit** — delete all existing emails.
2. **Submit access request** (no auth):
    - Navigate to `/request-access`.
    - Fill form: email = `owner@thornton-za.e2e-test.local`, full name = "Thandi Thornton", org name = "Thornton & Associates", country = "South Africa", industry = "Accounting".
    - Submit → verify success message ("Verification code sent").
3. **Verify OTP email**:
    - `waitForEmail('owner@thornton-za.e2e-test.local')` — verify email arrives in Mailpit.
    - `extractOtp(email)` — extract 6-digit code.
    - Enter OTP on the verification step → submit → verify success ("Your request has been submitted for review").
4. **Platform admin approves**:
    - `loginAsPlatformAdmin(page)` — log in through Keycloak.
    - Navigate to `/platform-admin/access-requests`.
    - Verify "Thornton & Associates" appears in PENDING tab.
    - Click approve → confirm dialog → verify status changes to APPROVED.
    - Verify no provisioning error shown.
5. **Owner receives invitation**:
    - `waitForEmail('owner@thornton-za.e2e-test.local', 'invitation')` — Keycloak sends invite email.
    - `extractInviteLink(email)` — get registration URL.
6. **Owner registers in Keycloak**:
    - `registerFromInvite(page, inviteLink, 'Thandi', 'Thornton', 'SecureP@ss1')`.
    - After registration, Keycloak redirects to app.
    - Verify landing on org dashboard (URL contains org slug, e.g., `/org/thornton-associates/dashboard`).
7. **Verify JIT member sync**:
    - Verify dashboard loads without errors.
    - Navigate to Settings > Team → verify "Thandi Thornton" listed as Owner.

***

## Section 3 — Accounting-ZA Pack Verification Tests

### Test: `accounting-za-packs.spec.ts`

**Prerequisite**: Owner logged in after successful onboarding (depends on Section 2 test, or uses a `beforeAll` that runs the onboarding flow).

**Scenario: Verify all accounting-ZA packs were imported during provisioning**

#### 3.1 Default settings verification

- Navigate to Settings > General.
- **Assert**: Default currency = **ZAR**.

- Navigate to Settings > Tax.
- **Assert**: Tax rate named **VAT** exists with rate **15%**, marked as default/active.

#### 3.2 Rate card defaults

- Navigate to Settings > Rates.
- **Assert**: The rate card defaults from the vertical profile should be pre-populated:
    - Billing rates: Owner = R1,500/hr, Admin = R850/hr, Member = R450/hr.
    - Cost rates: Owner = R650/hr, Admin = R350/hr, Member = R180/hr.
- **NOTE**: If rate card auto-apply from the vertical profile is not yet implemented, this test will fail and surface the gap. The expected behaviour is that the `accounting-za.json` profile's `rateCardDefaults` are seeded into `BillingRate` and `CostRate` entities during provisioning. **This is a known potential gap** — the provisioning pipeline may not currently seed rate cards from the profile. If so, this should be flagged as a prerequisite fix.

#### 3.3 Custom fields (3 field packs, 21+ fields)

- Navigate to Settings > Custom Fields.
- **Assert**: Customer field group "SA Accounting — Client Details" exists with fields including:
    - Company Registration Number, Trading As, VAT Number, SARS Tax Reference, SARS eFiling Profile Number, Financial Year-End, Entity Type (dropdown: Pty Ltd, Sole Proprietor, CC, Trust, Partnership, NPC), Industry (SIC Code), Registered Address, Postal Address, Primary Contact Name, Primary Contact Email, Primary Contact Phone, FICA Verified (dropdown: Not Started, In Progress, Verified), FICA Verification Date, Referred By.
- **Assert**: Project field group "SA Accounting — Engagement Details" exists with fields including:
    - Engagement Type (dropdown: Monthly Bookkeeping, Annual Tax Return, Advisory, Trust Administration, etc.), Tax Year, SARS Submission Deadline, Assigned Reviewer, Complexity (dropdown: Simple, Moderate, Complex).
- **Assert**: Trust-specific customer field group exists (from `accounting-za-customer-trust` pack).

#### 3.4 Compliance checklist template (1 pack, 11 items)

- Navigate to the compliance/checklist template settings (or verify via creating a customer and transitioning to ONBOARDING).
- **Assert**: FICA KYC checklist template "FICA KYC — SA Accounting" exists.
- **Assert**: Checklist contains 11 items:
    1. Certified ID Copy (required)
    2. Proof of Residence (required)
    3. Company Registration CM29/CoR14.3 (required, entity types: Pty Ltd, CC, NPC)
    4. Tax Clearance Certificate (required)
    5. Bank Confirmation Letter (required)
    6. Proof of Business Address (optional)
    7. Resolution / Mandate (optional, entity types: Pty Ltd, CC, NPC, Trust)
    8. Beneficial Ownership Declaration (required, entity types: Pty Ltd, CC, NPC, Trust)
    9. Source of Funds Declaration (optional)
    10. Letters of Authority — Master's Office (required, entity type: Trust)
    11. Trust Deed — Certified Copy (required, entity type: Trust)

#### 3.5 Document templates (1 pack, 7 templates)

- Navigate to Settings > Templates.
- **Assert**: All 7 templates from the `accounting-za` template pack are listed:
    1. Engagement Letter — Monthly Bookkeeping (category: ENGAGEMENT_LETTER)
    2. Engagement Letter — Annual Tax Return (category: ENGAGEMENT_LETTER)
    3. Engagement Letter — Advisory (category: ENGAGEMENT_LETTER)
    4. Monthly Report Cover (category: COVER_LETTER)
    5. SA Tax Invoice (category: OTHER, entity: INVOICE)
    6. Statement of Account (category: REPORT, entity: CUSTOMER)
    7. FICA Confirmation Letter (category: OTHER, entity: CUSTOMER)

#### 3.6 Clauses (1 pack, 7 clauses + 3 template associations)

- Verify clauses are accessible (may be via template editor or a dedicated clauses settings page).
- **Assert**: 7 clauses exist:
    1. Limitation of Liability (Accounting) — category: Legal
    2. Fee Escalation — category: Commercial
    3. Termination (Accounting) — category: Legal
    4. Confidentiality (Accounting) — category: Legal
    5. Document Retention (Accounting) — category: Compliance
    6. Third-Party Reliance — category: Legal
    7. Electronic Communication Consent — category: Compliance
- **Assert**: Engagement Letter — Monthly Bookkeeping has all 7 clauses associated (4 required).
- **Assert**: Engagement Letter — Annual Tax Return has 5 clauses associated (3 required).
- **Assert**: Engagement Letter — Advisory has 4 clauses associated (2 required).

#### 3.7 Automation templates (1 pack, 4 rules)

- Navigate to Settings > Automations.
- **Assert**: 4 automation rules exist:
    1. "FICA Reminder (7 days)" — trigger: CUSTOMER_STATUS_CHANGED to ONBOARDING, action: notify org admins after 7 days
    2. "Engagement Budget Alert (80%)" — trigger: BUDGET_THRESHOLD_REACHED at 80%, action: notify project owner
    3. "Invoice Overdue (30 days)" — trigger: INVOICE_STATUS_CHANGED to OVERDUE, action: notify admins + email customer
    4. "SARS Deadline Reminder" — trigger: FIELD_DATE_APPROACHING (sars_submission_deadline), action: notify org admins

#### 3.8 Request templates (1 pack, 8 items)

- Navigate to where request templates are managed (Settings or via creating a new information request).
- **Assert**: "Year-End Information Request (SA)" template exists with 8 items:
    1. Trial Balance (required, FILE_UPLOAD)
    2. Bank Statements — Full Year (required, FILE_UPLOAD)
    3. Loan Agreements (required, FILE_UPLOAD)
    4. Fixed Asset Register (required, FILE_UPLOAD)
    5. Debtors Age Analysis (optional, FILE_UPLOAD)
    6. Creditors Age Analysis (optional, FILE_UPLOAD)
    7. Insurance Schedule (optional, FILE_UPLOAD)
    8. Payroll Summary (required, FILE_UPLOAD)

***

## Section 4 — Member Invite & RBAC Tests

### Test: `member-invite-rbac.spec.ts`

**Prerequisite**: Org "Thornton & Associates" exists with owner "Thandi Thornton" logged in.

#### 4.1 Invite admin member (Bob)

1. Navigate to Settings > Team.
2. Click invite member.
3. Fill: email = `bob@thornton-za.e2e-test.local`, role = Admin.
4. Submit → verify invitation appears as pending.
5. **Check Mailpit**: `waitForEmail('bob@thornton-za.e2e-test.local')` → verify Keycloak invitation email arrived.
6. `extractInviteLink(email)` → open in new page context.
7. `registerFromInvite(page, link, 'Bob', 'Ndlovu', 'SecureP@ss2')`.
8. After registration → login → verify lands on org dashboard.
9. Navigate to Settings > Team → verify Bob listed with Admin role.

#### 4.2 Invite member (Carol)

1. Same flow as Bob: email = `carol@thornton-za.e2e-test.local`, role = Member.
2. Register as "Carol Mokoena", password `SecureP@ss3`.
3. Verify listed in Team page with Member role.

#### 4.3 RBAC — Admin permissions (Bob)

Login as Bob (`bob@thornton-za.e2e-test.local`):

- **Can access**: Settings > General, Settings > Team (read), Customers, Projects, Invoices.
- **Cannot access**: Org deletion (if exposed), billing/subscription settings.

#### 4.4 RBAC — Member restrictions (Carol)

Login as Carol (`carol@thornton-za.e2e-test.local`):

- **Can access**: My Work page, assigned tasks, time entry logging.
- **Cannot access**: Settings > Rates (expect redirect or forbidden), Settings > General, Settings > Team (invite action — may be able to view but not invite).
- **Cannot see**: Financial data (profitability reports, billing rates, cost rates) — verify these pages/sections are hidden or blocked.

***

## Section 5 — Existing E2E Test Migration

### 5.1 Migrate existing smoke tests

The existing Playwright tests at `e2e/` run against the mock IDP E2E stack. These tests should be migrated to run against the Keycloak dev stack:

- Replace `mock-login` fixture with `keycloak-auth` fixture (`loginAs(page, email, password)`).
- Replace `localhost:3001` references with `localhost:3000` (or `localhost:8443` for gateway).
- Replace `localhost:8081` backend references with `localhost:8080`.
- Tests that depend on pre-seeded users (Alice/Bob/Carol from mock IDP) should use the `keycloak-seed.sh` script's users OR be adapted to create users through the product flow first.
- Update Playwright config to point to the Keycloak dev stack URLs.

### 5.2 Deprecate mock IDP stack

- Add a comment header to `docker-compose.e2e.yml`: "DEPRECATED — prefer Keycloak dev stack (docker-compose.yml). Retained for backwards compatibility."
- Do NOT delete mock IDP code or config — it may be useful as a fallback.
- Update `CLAUDE.md` agent navigation section to recommend Keycloak dev stack as primary.

***

## Section 6 — Prerequisites & Known Gaps

These items may need to be built or fixed before the test suite can pass. The test suite should be written to validate the expected behaviour — failing tests expose the gaps.

### 6.1 Rate card auto-seeding (potential gap)

The `accounting-za.json` vertical profile defines `rateCardDefaults` with billing and cost rates per role. The test in Section 3.2 asserts these are pre-populated after provisioning. If `TenantProvisioningService` does not currently seed rate cards from the profile manifest, this is a gap that must be closed.

**Expected behaviour**: When a tenant is provisioned with profile `accounting-za`, the provisioning pipeline creates `BillingRate` and `CostRate` entries matching the profile's `rateCardDefaults`, using the org's members and the specified `currency`.

**Challenge**: At provisioning time, only the owner has been invited — Bob and Carol don't exist yet. Rate defaults should be **role-based** (Owner = R1500, Admin = R850, Member = R450), not **member-based**. The UI should show default rates by role, and when members are synced via JIT, their rate should be derived from their `OrgRole`.

### 6.2 Tax default auto-seeding (potential gap)

The profile defines `taxDefaults: [{ name: "VAT", rate: 15.00, default: true }]`. If this isn't seeded during provisioning, the Settings > Tax page will be empty.

### 6.3 Currency default auto-seeding (potential gap)

The profile defines `currency: "ZAR"`. `OrgSettings.defaultCurrency` should be set to ZAR during provisioning.

### 6.4 Docker Compose update

`docker-compose.yml` needs backend, frontend, gateway, and portal services added (built from source, with correct profiles and build args). Health check dependencies must ensure correct startup order: Postgres → Keycloak → Backend → Gateway → Frontend.

### 6.5 Keycloak login page selectors

Playwright selectors for the Keycloak login form must match the actual theme. If the `docteams` Keycloak theme has custom selectors, these must be identified. Default Keycloak uses `#username`, `#password`, `#kc-login` (or `input[name=username]`, `input[name=password]`, `button[type=submit]`).

***

## Out of scope

- **Full 90-day lifecycle operations** (time logging, invoicing, profitability) — covered by the existing manual lifecycle script. A future phase may automate those steps.
- **CI/CD integration** — this phase produces a locally-runnable test suite. CI pipeline configuration (GitHub Actions, etc.) is a follow-up.
- **Performance testing** — this is functional validation, not load testing.
- **Portal flow testing** — customer portal auth (magic links) is a separate concern.
- **Multiple org testing** — this phase tests a single accounting firm's lifecycle. Multi-tenant isolation testing is a separate concern.

***

## ADR topics to address

1. **Test stack unification** — why Keycloak replaces mock IDP as the primary test target. Document the decision to deprecate (not delete) the mock IDP stack.
2. **Test data strategy** — why tests create data through the UI (not DB seeding), and the trade-off of slower but more realistic tests.
3. **Pack verification approach** — why pack seeding is validated through the UI (not API assertions), ensuring the full stack from seeder → DB → API → frontend rendering is tested.

***

## Style and boundaries

- Tests must be deterministic and order-independent where possible. Use `test.describe.serial()` for flows that depend on prior state (onboarding → pack verification → member invite).
- Use data-testid attributes for critical assertions where existing selectors are fragile. Add `data-testid` attributes to frontend components as needed (e.g., `data-testid="template-list-item"`, `data-testid="custom-field-group"`).
- Mailpit polling should have a reasonable timeout (30s for Keycloak invite emails, which may be slower than direct SMTP).
- Test file naming: `*.spec.ts` in the `e2e/tests/` directory.
- Fixtures and helpers in `e2e/fixtures/` and `e2e/helpers/`.
- All Keycloak page interactions (login form, registration form) should be abstracted into Page Object Models for maintainability.
