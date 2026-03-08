# Handover: Phase 39 Platform Admin Flow Fixes

## Branch: `fix/phase39-platform-admin-flow`

All fixes so far are committed. One remaining bug needs fixing.

## Remaining Bugs (2 issues)

### Bug 1: Approval fails with "URI with undefined scheme"

**Error**: `KeycloakProvisioningClient.getAdminToken()` (line 140) throws `IllegalArgumentException: URI with undefined scheme` when platform admin approves an access request.

**Root cause**: The `keycloak.admin.auth-server-url` property resolves to empty string in the local profile. The backend's `application-keycloak.yml` doesn't include Keycloak admin credentials, and `application-local.yml` doesn't either. The default in `application.yml` uses `${KEYCLOAK_ADMIN_URL:http://localhost:8180}` but that's under the `keycloak:` prefix which may not map to what `KeycloakProvisioningClient` expects.

**File to investigate**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java` — check what config properties it reads for the admin URL, username, and password. Then ensure `application-keycloak.yml` (or `application-local.yml`) provides them.

**The fix is likely**: Add the correct `keycloak.admin.*` properties to `application-keycloak.yml` pointing to `http://localhost:8180` with admin/admin credentials. The properties are already in `application.yml` under `keycloak.admin` — just verify `KeycloakProvisioningClient` reads from the same prefix.

### Bug 2: Approve button does nothing — no backend logs, no errors

**Symptom**: Clicking Approve on the platform admin panel produces no backend logs, no gateway logs, and no visible error on the frontend. Nothing happens.

**Likely cause**: The approve action uses `api.post()` which in keycloak/BFF mode forwards the SESSION cookie and includes a CSRF token (`X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie). The gateway has CSRF protection enabled for non-BFF/non-API paths. Check:

1. **CSRF token missing**: The `api.post()` in `getAuthFetchOptions()` reads `XSRF-TOKEN` cookie. If this cookie isn't set on the frontend domain, the POST will be rejected silently by the gateway's CSRF filter. However, the gateway config ignores CSRF for `/bff/**` and `/api/**` so this shouldn't be the issue.

2. **Gateway not proxying POST**: The gateway route `Path=/api/**` should catch POST requests too. Check if the gateway even receives the request — add debug logging to gateway.

3. **Frontend server action swallowing errors**: The `approveAccessRequest()` function in `actions.ts` catches all errors. The `api.post()` may be failing (redirect to sign-in, connection error, etc.) but the error is caught and returned as `{ success: false, error: "..." }`. The `AccessRequestsTable` component may not be displaying the error result from the approve action.

4. **`redirect("/sign-in")` swallowed**: In `apiRequest()`, if `getAuthFetchOptions()` throws, `redirect("/sign-in")` is called. But this is inside a server action called from a client component — the redirect may be swallowed. Check if the SESSION cookie is actually available when the approve server action runs.

**Files to investigate**:
- `frontend/app/(app)/platform-admin/access-requests/actions.ts` — `approveAccessRequest()`
- `frontend/components/access-request/access-requests-table.tsx` — how approve result is handled
- `frontend/components/access-request/approve-dialog.tsx` — the approve UI component
- `frontend/lib/api.ts` — `apiRequest()` and `getAuthFetchOptions()`

**Debug approach**: Add `console.log` to `approveAccessRequest()` (same pattern as was done for `listAccessRequests`) to see if the server action is called and what error it returns.

## What Was Fixed (already committed)

1. **Dashboard routing** (`frontend/app/(app)/dashboard/page.tsx`): Platform admins redirected to `/platform-admin/access-requests` instead of "Waiting for Access". Fixed `redirect()` inside try-catch (Next.js throws NEXT_REDIRECT which was swallowed).

2. **Create-org routing** (`frontend/app/(app)/create-org/page.tsx`): Same platform admin redirect fix.

3. **Gateway security** (`gateway/.../GatewaySecurityConfig.java`): Added `permitAll()` for `/api/access-requests` and `/api/access-requests/verify` — these are public endpoints but were blocked by `/api/**` authenticated rule.

4. **Access requests response type** (`frontend/.../platform-admin/access-requests/actions.ts`): Backend returns `List<AccessRequestResponse>` (plain array), not `{ content: [...] }`. Fixed `AccessRequestsResponse` type and removed `.content` accessor. Data was loading but silently returning undefined.

5. **Error visibility** (`frontend/.../platform-admin/access-requests/page.tsx`): Added error banner — previously API failures showed as empty list with no feedback.

6. **Landing page links** (`frontend/components/marketing/hero-section.tsx`, `pricing-preview.tsx`): "Get Started" now goes to `/request-access` in keycloak mode instead of `/sign-up` (Keycloak login).

7. **Keycloak registration disabled** (`compose/keycloak/realm-export.json`): `registrationAllowed: false` — prevents bypassing admin-approved flow via Keycloak's built-in registration page. Note: existing Keycloak instances need manual update via Admin Console or kcadm.

8. **Seed script** (`compose/scripts/keycloak-seed.sh`): Creates dedicated `padmin@docteams.local` platform admin user instead of making Alice (tenant owner) a platform admin. Platform admins should never be tenant members.

9. **Mail config** (`backend/src/main/resources/application-local.yml`): Added Mailpit SMTP settings (localhost:1025, no auth) so OTP emails work locally.

## Key Architecture Notes

- **Platform admins have no org** — they authenticate via Keycloak but have no `organization` claim in their JWT. The `TenantFilter` falls through (line 107), `PlatformAdminFilter` binds groups from JWT `groups` claim.
- **Gateway BFF mode**: Frontend → SESSION cookie → gateway `/bff/me` → returns groups. Frontend `api.get()` forwards SESSION cookie to gateway which uses `TokenRelay` to proxy to backend with access token.
- **Keycloak groups protocol mapper** must exist on `gateway-bff` client for `groups` claim to appear in tokens. Defined in `realm-export.json` but only applies on fresh realm import.
- The `@ConditionalOnProperty` on `KeycloakProvisioningClient` means it's only created when keycloak admin config is present — if the bean is null, `AccessRequestApprovalService` catches it with the null check on line 49.

## Testing After Fix

1. Start Mailpit + Postgres: `bash compose/scripts/dev-up.sh`
2. Start backend with keycloak profile: `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run`
3. Start gateway: `cd gateway && ./mvnw spring-boot:run`
4. Start frontend: `cd frontend && NODE_OPTIONS="" pnpm dev`
5. Navigate to `http://localhost:3000` → "Get Started" → fill request access form → check OTP in Mailpit (localhost:8025) → verify
6. Login as platform admin → should redirect to access requests page → approve → should provision KC org + tenant schema
