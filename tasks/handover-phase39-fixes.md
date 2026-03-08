# Handover: Phase 39 Platform Admin Flow Fixes

## Branch: `fix/phase39-platform-admin-flow`

All fixes so far are committed. One remaining bug needs fixing.

## Remaining Bugs (2 issues)

### Bug 1: Approval fails with "URI with undefined scheme"

**Error**: `KeycloakProvisioningClient.getAdminToken()` (line 140) throws `IllegalArgumentException: URI with undefined scheme` when platform admin approves an access request.

**Root cause**: The `keycloak.admin.auth-server-url` property resolves to empty string in the local profile. The backend's `application-keycloak.yml` doesn't include Keycloak admin credentials, and `application-local.yml` doesn't either. The default in `application.yml` uses `${KEYCLOAK_ADMIN_URL:http://localhost:8180}` but that's under the `keycloak:` prefix which may not map to what `KeycloakProvisioningClient` expects.

**File to investigate**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java` — check what config properties it reads for the admin URL, username, and password. Then ensure `application-keycloak.yml` (or `application-local.yml`) provides them.

**The fix is likely**: Add the correct `keycloak.admin.*` properties to `application-keycloak.yml` pointing to `http://localhost:8180` with admin/admin credentials. The properties are already in `application.yml` under `keycloak.admin` — just verify `KeycloakProvisioningClient` reads from the same prefix.

### Bug 2: Approve succeeds on backend but frontend doesn't reflect it

**Symptom**: Clicking Approve provisions the Keycloak org + tenant schema (backend succeeds), but the frontend approve dialog stays open, the list still shows PENDING, and no success/error feedback appears.

**Root cause (confirmed pattern)**: The backend approval endpoint returns `AccessRequestResponse` (a single object) but the frontend's `api.post()` response handling may be failing. The `approveAccessRequest()` server action catches errors silently — if the response parsing fails or a `revalidatePath` redirect is swallowed, the client component never gets the success signal.

**Most likely issues** (investigate in order):

1. **`revalidatePath()` throws NEXT_REDIRECT inside try-catch**: Look at `approveAccessRequest()` in `actions.ts` — after `api.post()` succeeds, it calls `revalidatePath("/platform-admin/access-requests")` then returns `{ success: true }`. If `revalidatePath` throws (it can in some Next.js versions), the catch block swallows it and returns `{ success: false, error: "An unexpected error occurred." }`. The client component may not be handling that error.

2. **Approve dialog not closing on result**: Check `approve-dialog.tsx` — does it check the result of `approveAccessRequest()` and close itself? If the action returns `{ success: false }` (due to issue #1), the dialog may stay open.

3. **`api.post()` response parsing**: The backend returns a single `AccessRequestResponse` object. `api.post()` parses this as JSON. If the response is unexpected (e.g., gateway returns HTML redirect), parsing fails silently.

**Files to investigate**:
- `frontend/app/(app)/platform-admin/access-requests/actions.ts` — `approveAccessRequest()` — add console.log before/after api.post() and revalidatePath()
- `frontend/components/access-request/approve-dialog.tsx` — how it handles the action result
- `frontend/components/access-request/access-requests-table.tsx` — how approve result triggers UI update

**Quick debug**: Add `console.log` to `approveAccessRequest()`:
```typescript
export async function approveAccessRequest(id: string): Promise<ActionResult> {
  try {
    console.log("[approve] calling api.post for", id);
    await api.post(`/api/platform-admin/access-requests/${id}/approve`);
    console.log("[approve] api.post succeeded");
  } catch (error) {
    console.error("[approve] api.post failed:", error);
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  console.log("[approve] calling revalidatePath");
  revalidatePath("/platform-admin/access-requests");
  console.log("[approve] returning success");
  return { success: true };
}
```

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
