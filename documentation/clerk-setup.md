# Clerk Setup Guide

Complete manual configuration required in the Clerk Dashboard for this application to work.

## Prerequisites

1. Create a Clerk application at [dashboard.clerk.com](https://dashboard.clerk.com)
2. Choose **Email + Password** as the primary sign-in method

---

## Step 1: API Keys

**Location:** Clerk Dashboard → **API Keys**

Copy the following and add to `frontend/.env.local`:

```bash
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_SECRET_KEY=sk_test_...
```

From the **Advanced** section, note your instance URL (e.g., `https://crucial-sturgeon-38.clerk.accounts.dev`). Add to backend config (`backend/src/main/resources/application-local.yml`):

```yaml
clerk:
  issuer: https://<your-instance>.clerk.accounts.dev
  jwks-uri: https://<your-instance>.clerk.accounts.dev/.well-known/jwks.json
```

---

## Step 2: Enable Organizations

**Location:** Clerk Dashboard → **Configure** → **Organization settings**

| Setting | Value |
|---------|-------|
| Enable Organizations | **ON** |
| Allow users to create organizations | **YES** |

### Roles

The app expects these three built-in roles (Clerk creates them by default):

| Role | JWT short name (`o.rol`) | Backend authority | Used for |
|------|--------------------------|-------------------|----------|
| `org:owner` | `owner` | `ROLE_ORG_OWNER` | Full access, delete projects |
| `org:admin` | `admin` | `ROLE_ORG_ADMIN` | CRUD projects, invite members |
| `org:member` | `member` | `ROLE_ORG_MEMBER` | Read projects, upload documents |

No custom roles or permissions need to be created — the defaults work.

---

## Step 3: Configure Paths (Redirect URLs)

**Location:** Clerk Dashboard → **Configure** → **Paths**

| Setting | Value |
|---------|-------|
| **Home URL** | `http://localhost:3000/dashboard` (dev) or `https://<your-domain>/dashboard` (prod) |
| **Sign-in URL** | `http://localhost:3000/sign-in` (dev) or `https://<your-domain>/sign-in` (prod) |
| **Sign-up URL** | `http://localhost:3000/sign-up` (dev) or `https://<your-domain>/sign-up` (prod) |

These are critical for the invitation flow — without them, invited users land on Clerk's Account Portal instead of the app.

---

## Step 4: Configure Webhooks

**Location:** Clerk Dashboard → **Webhooks**

1. Click **Add Endpoint**
2. **Endpoint URL:**
   - Local dev (requires tunnel): `https://<ngrok-id>.ngrok.io/api/webhooks/clerk`
   - Production: `https://<your-domain>/api/webhooks/clerk`
3. **Subscribe to events:**
   - `organization.created` — triggers tenant schema provisioning
   - `organization.updated`
   - `organization.deleted`
   - `organizationMembership.created`
   - `organizationMembership.updated`
   - `organizationMembership.deleted`
   - `organizationInvitation.created`
   - `organizationInvitation.accepted`
   - `organizationInvitation.revoked`
4. Click **Create**
5. Copy the **Signing Secret** (`whsec_...`) → add to `frontend/.env.local`:

```bash
CLERK_WEBHOOK_SIGNING_SECRET=whsec_...
```

### Verifying Webhooks

Create an organization in the app and check:
- Next.js console: `[webhook] Received event: type=organization.created`
- Backend logs: tenant provisioning success
- Clerk Dashboard → Webhooks → Recent deliveries → all show `200`

---

## Step 5: JWT Configuration

**Location:** Clerk Dashboard → **JWT Templates**

The app uses **Clerk JWT v2 format** (the default). Verify your JWT contains org claims nested under `"o"`:

```json
{
  "sub": "user_abc123",
  "v": 2,
  "o": {
    "id": "org_xyz789",
    "rol": "admin",
    "slg": "acme-corp"
  }
}
```

The backend extracts:
- `o.id` → tenant resolution (maps to schema via `org_schema_mapping`)
- `o.rol` → RBAC enforcement (`@PreAuthorize` annotations)

If using the default session token (no custom template), this should work out of the box. No custom JWT template is needed.

---

## Environment Variable Summary

### Frontend (`frontend/.env.local`)

```bash
# Clerk (from Steps 1 & 4)
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_SECRET_KEY=sk_test_...
CLERK_WEBHOOK_SIGNING_SECRET=whsec_...

# Clerk routes (already set in code, but can override)
NEXT_PUBLIC_CLERK_SIGN_IN_URL=/sign-in
NEXT_PUBLIC_CLERK_SIGN_UP_URL=/sign-up
NEXT_PUBLIC_CLERK_SIGN_IN_FALLBACK_REDIRECT_URL=/dashboard
NEXT_PUBLIC_CLERK_SIGN_UP_FALLBACK_REDIRECT_URL=/dashboard
```

### Backend (`application-local.yml`)

```yaml
clerk:
  issuer: https://<your-instance>.clerk.accounts.dev
  jwks-uri: https://<your-instance>.clerk.accounts.dev/.well-known/jwks.json
```

### AWS Secrets Manager (Production)

| Secret path | Value |
|-------------|-------|
| `docteams/{env}/clerk-secret-key` | `sk_live_...` |
| `docteams/{env}/clerk-webhook-secret` | `whsec_...` |
| `docteams/{env}/clerk-publishable-key` | `pk_live_...` |

### GitHub Actions (CI/CD)

| Secret | Scope | Purpose |
|--------|-------|---------|
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Repository | Baked into Docker image at build time |

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Invited user lands on Clerk portal | Home URL not set in Clerk Paths | Set Home URL to your app's `/dashboard` URL (Step 3) |
| 401 Unauthorized on API calls | Issuer/JWKS mismatch | Verify `CLERK_ISSUER` matches your instance URL exactly |
| Org not provisioned after creation | Webhook not firing | Check Clerk Dashboard → Webhooks → Recent deliveries |
| Webhook returns 400 | Wrong signing secret | Re-copy `whsec_...` from Clerk Dashboard |
| Roles not working | JWT missing `o.rol` claim | Verify JWT v2 format is enabled (Step 5) |
| CSS conflicts with Clerk components | Missing `cssLayerName` | Ensure `<ClerkProvider appearance={{ cssLayerName: "clerk" }}>` in root layout |
