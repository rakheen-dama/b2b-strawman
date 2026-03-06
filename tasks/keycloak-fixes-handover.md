# Keycloak Fixes — Handover Prompt

You are continuing fixes on the `keycloak_fixes` branch for a B2B SaaS app's Keycloak 26.5 integration.

## What Works Now (Fixed This Session)

1. **User role defaults to owner** — `BffUserInfoExtractor` now returns `"owner"` for the list-format `organization` claim (no roles in Keycloak token). `BffSecurity.isAdmin()` correctly matches.
2. **Org alias → UUID resolution** — `KeycloakAdminClient.resolveOrgId()` converts OIDC `organization` alias (e.g. `"madcorp"`) to Keycloak UUID via `q=alias:{alias}` query param. All `AdminProxyController` endpoints use this.
3. **Member list works** — `AdminProxyController.listMembers()` transforms Keycloak user representation (`firstName`/`lastName`) to frontend-expected shape (`{id, email, name, role}`).
4. **Invitation endpoint fixed** — Keycloak 26.5 uses `POST /organizations/{id}/members/invite-user` with `application/x-www-form-urlencoded` (NOT `POST /invitations` with JSON).
5. **`normalizeRole()` null-safe** — handles undefined role from Keycloak member data.
6. **All gateway tests pass** (46/47 — 1 pre-existing CSRF test failure).

## What Still Needs Fixing

### 1. Invite shows error on frontend (status 404 still)
The gateway was just rebuilt with the `invite-user` endpoint fix. **User needs to restart gateway** to pick up changes. If still failing after restart, check:
- Gateway logs for Keycloak Admin API errors
- The `inviteMember` method now uses form-urlencoded body to `POST /organizations/{orgId}/members/invite-user`

### 2. Keycloak org roles not available
Keycloak 26.5's org roles endpoints (`/organizations/{id}/roles`, `/members/{id}/organization-roles`) return 404. This means:
- `updateMemberRole()` in `KeycloakAdminClient` will fail (it tries to create/grant org roles)
- The `BffController.createOrg()` try-catch around role assignment will log a warning but continue
- Member roles are determined heuristically: current user = owner, others = member

### 3. Keycloak doesn't send invitation emails
Keycloak needs SMTP configured in realm settings to send invitation emails. The invite creates a pending invitation with an `inviteLink` but no email is dispatched. Options:
- Configure SMTP in Keycloak Admin Console → Realm Settings → Email
- Or have the gateway/frontend send the invite link itself

### 4. `updateMemberRole` needs Keycloak org roles feature
The `ensureOrgRole`/`updateMemberRole` methods try to create org roles and grant them. Since org roles return 404, this entire flow is non-functional. Two options:
- **Enable org roles in Keycloak** (may require realm config or Keycloak feature flag)
- **Store roles in backend only** — don't rely on Keycloak for role management, use the backend's `members` table

### 5. Debug logging removed
All `[invite-debug]` console.log statements have been removed from `frontend/app/(app)/org/[slug]/team/actions.ts`.

## Key Files Changed

### Gateway (source)
| File | Changes |
|------|---------|
| `gateway/.../BffController.java` | Assigns owner role on org creation (try-catch, best-effort) |
| `gateway/.../BffUserInfoExtractor.java` | List format → `"owner"` (bare). Consistent bare role format across both list and rich formats |
| `gateway/.../BffSecurity.java` | No changes (already compares bare role names) |
| `gateway/.../AdminProxyController.java` | All endpoints use `resolveOrgId()`. `listMembers()` transforms Keycloak shape. `invite()` returns constructed response |
| `gateway/.../KeycloakAdminClient.java` | `resolveOrgId()` added. `findOrganizationByAlias()` uses `q=alias:` param. `inviteMember()` uses `/members/invite-user` with form body. `updateMemberRole()` rewritten for org roles API (but org roles not available in KC 26.5) |

### Gateway (tests)
| File | Changes |
|------|---------|
| `AdminProxyControllerTest.java` | Fixed WireMock paths (`/orgs/` → `/organizations/`), token realm (`master`), test properties, invite stubs, member assertion, org ID to real UUID |
| `FullFlowIntegrationTest.java` | Same WireMock path and token realm fixes, invite stub updated |
| `GatewayIntegrationTestBase.java` | Added `auth-server-url`, `username`, `password` properties. Org ID to real UUID |

### Frontend
| File | Changes |
|------|---------|
| `frontend/.../team/actions.ts` | `normalizeRole()` null-safe. Debug logging added then removed |

### Not changed (but relevant)
| File | Notes |
|------|-------|
| `frontend/.env.local` | **User must have `NEXT_PUBLIC_AUTH_MODE=keycloak` and `GATEWAY_URL=http://localhost:8443`** |
| `backend/.../ClerkJwtUtils.java` | Already defaults to `ORG_OWNER` for list format — no change needed |
| `backend/.../MemberFilter.java` | Already creates first member as owner — no change needed |

## Keycloak 26.5 Organization API Reference (Discovered)

| Operation | Endpoint | Method | Body |
|-----------|----------|--------|------|
| List orgs | `/organizations` | GET | — |
| Search by alias | `/organizations?q=alias:{alias}` | GET | — |
| Search by name | `/organizations?search={name}` | GET | — |
| Create org | `/organizations` | POST | JSON `{name, alias, enabled}` |
| Add member | `/organizations/{id}/members` | POST | JSON `"userId"` (raw string) |
| List members | `/organizations/{id}/members` | GET | — |
| Invite user | `/organizations/{id}/members/invite-user` | POST | form `email=...` |
| List invitations | `/organizations/{id}/invitations` | GET | — |
| Delete invitation | `/organizations/{id}/invitations/{invId}` | DELETE | — (untested) |
| Org roles | `/organizations/{id}/roles` | — | **404 — not available** |
| Member roles | `/organizations/{id}/members/{uid}/organization-roles` | — | **404 — not available** |

## Pre-existing Issues (Not Part of This Fix)

- `GatewaySecurityConfigTest.csrf_postWithoutTokenReturns403` — returns 405 instead of 403 (Spring framework behavior)
- Clerk CAPTCHA blocks agent browser auth on port 3000
