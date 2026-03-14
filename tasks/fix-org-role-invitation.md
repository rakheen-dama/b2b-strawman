# Fix: Invited org members don't get correct role

## Problem
When an org owner invites a user (as admin or member) and the user accepts and logs in, their JWT has no `org_role` claim. The BFF defaults to "member" regardless of intended role. The backend DB also records "member".

## Investigation Results (VERIFIED against live Keycloak 26.5)

All changes were reverted. These findings are from curl probes against the running instance.

| # | Question | Answer |
|---|----------|--------|
| 1 | Does org list/search return attributes? | **NO** — only `GET /organizations/{id}` returns `attributes` |
| 2 | Does partial PUT on org work? | **NO** — `PUT /organizations/{id}` with only `{"attributes": {...}}` fails with "A organization with the same name already exists." |
| 3 | Does invite-user create the user? | **NO** — user only exists after they accept the invitation |
| 4 | Was `creatorUserId` ever set on any org? | **NO** — all orgs have `"attributes": {}` — `setOrgCreator()` has been silently failing |
| 5 | Does full PUT (with name+alias+enabled+attributes) work? | **YES** |
| 6 | Does setting user attribute `org_role` work? | **YES** — persists and appears in JWT after re-auth |
| 7 | Is `org_role` registered in user profile? | **YES** |
| 8 | Is the `oidc-usermodel-attribute-mapper` configured? | **YES** — maps `user.attribute: org_role` to `claim.name: org_role` on ID token, access token, userinfo |

## Root Causes

1. **`setOrgCreator()` in `KeycloakProvisioningClient`** sends a partial PUT (`{"attributes": {...}}`) which Keycloak rejects. The `creatorUserId` attribute was NEVER set on any org.
2. **`inviteMember()` in gateway `KeycloakAdminClient`** receives the `role` parameter but discards it — only sends `email` to Keycloak.
3. **`invite-user` doesn't create the Keycloak user** — the user only exists after accepting. So `org_role` can't be set at invitation time for new users.
4. **BFF `extractOrgInfo()`** defaults to "member" when `org_role` JWT claim is missing.

## Implementation Plan

### Step 1: Fix org attribute writes (CRITICAL — fix the partial PUT bug)

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`

Fix `setOrgCreator()` — must GET the full org first, merge attributes, PUT back complete representation:

```java
public void setOrgCreator(String orgId, String email) {
    String userId = findUserIdByEmail(email);
    if (userId == null) {
        log.warn("Could not find Keycloak user for email {} to set as org creator", email);
        return;
    }
    mergeOrgAttribute(orgId, "creatorUserId", List.of(userId));
    log.info("Set creatorUserId={} on org {}", userId, orgId);
}

// Reusable helper — GET org, merge one attribute, PUT full representation back
@SuppressWarnings("unchecked")
private void mergeOrgAttribute(String orgId, String key, Object value) {
    Map<String, Object> org = restClient.get()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    if (org == null) return;

    Map<String, Object> attrs = org.get("attributes") instanceof Map<?, ?>
        ? new java.util.HashMap<>((Map<String, Object>) org.get("attributes"))
        : new java.util.HashMap<>();
    attrs.put(key, value);

    var body = new java.util.HashMap<>(org);
    body.put("attributes", attrs);
    restClient.put()
        .uri("/organizations/{orgId}", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
}
```

Add import: `import org.springframework.core.ParameterizedTypeReference;`

Also add methods for the user attribute:
```java
// Read user's org_role attribute
@SuppressWarnings("unchecked")
public String getUserOrgRole(String userId) {
    try {
        Map<String, Object> user = restClient.get()
            .uri("/users/{userId}", userId)
            .header("Authorization", "Bearer " + getAdminToken())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (user == null) return null;
        if (user.get("attributes") instanceof Map<?, ?> attrs) {
            Object orgRole = attrs.get("org_role");
            if (orgRole instanceof List<?> list && !list.isEmpty()) return (String) list.getFirst();
        }
    } catch (Exception e) {
        log.debug("Could not read org_role for user {}: {}", userId, e.getMessage());
    }
    return null;
}

// Set user's org_role attribute (full PUT)
@SuppressWarnings("unchecked")
public void setUserOrgRole(String userId, String role) {
    Map<String, Object> user = restClient.get()
        .uri("/users/{userId}", userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    if (user == null) return;
    Map<String, Object> attrs = user.get("attributes") instanceof Map<?, ?>
        ? new java.util.HashMap<>((Map<String, Object>) user.get("attributes"))
        : new java.util.HashMap<>();
    attrs.put("org_role", List.of(role));
    var body = new java.util.HashMap<>(user);
    body.put("attributes", attrs);
    restClient.put()
        .uri("/users/{userId}", userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    log.info("Set org_role={} for user {}", role, userId);
}
```

### Step 2: Store pending invitation role at invite time

**File**: `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`

Add the same `mergeOrgAttribute()` helper (GET full org, merge, PUT full). The gateway's `KeycloakAdminClient` already has `setUserAttribute()` and `getOrganization()` that do full PUTs correctly.

Modify `inviteMember()` to store the pending role:
```java
public void inviteMember(String orgId, String email, String role, String redirectUrl) {
    // Send invitation (existing code)
    String formBody = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    if (redirectUrl != null && !redirectUrl.isBlank()) {
        formBody += "&redirectUrl=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
    }
    restClient.post()
        .uri("/organizations/{orgId}/members/invite-user", orgId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formBody)
        .retrieve()
        .toBodilessEntity();

    // Store intended role as org attribute for later resolution
    // Key: "pending_role:<email>", Value: ["<role>"]
    mergeOrgAttribute(orgId, "pending_role:" + email, List.of(role));
    log.info("Stored pending role {} for {} on org {}", role, email, orgId);
}
```

Also add `findUserByEmail()` and `getUserOrgRole()` methods (same pattern as backend).

### Step 3: BFF resolves role on first `/me` call

**File**: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`

In the `me()` endpoint, when `user.getClaim("org_role") == null` and `orgInfo != null`:

```java
// After extracting orgInfo...
if (orgInfo != null && user.getClaim("org_role") == null) {
    String resolvedRole = resolveAndSyncOrgRole(user.getSubject(), orgInfo.slug(), user.getEmail());
    if (resolvedRole != null) {
        orgInfo = new BffUserInfoExtractor.OrgInfo(orgInfo.slug(), orgInfo.id(), resolvedRole);
    }
}
```

The `resolveAndSyncOrgRole()` method must:
1. Call `resolveOrgId(orgSlug)` to convert alias → UUID
2. Call `getOrganization(orgId)` (**NOT** `findOrganizationByAlias` — search doesn't return attributes!)
3. Check `pending_role:<email>` in org attributes → if found, that's the role
4. Check `creatorUserId` in org attributes → if matches current user, role is "owner"
5. When role found: call `updateMemberRole(orgId, userId, role)` to set `org_role` user attribute
6. If pending role was used: clean up the org attribute (remove `pending_role:<email>`)
7. Use `ConcurrentHashMap.newKeySet()` to avoid repeated calls per user

### Step 4: MemberFilter reads Keycloak attribute

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`

In `lazyCreateMember()`, when `effectiveRole` is "member" and `keycloakProvisioningClient != null`:
- Call `keycloakProvisioningClient.getUserOrgRole(clerkUserId)` to check if BFF already set the attribute
- If found and not "member", use that as `effectiveRole`
- Inject `KeycloakProvisioningClient` as `@Nullable` (it's `@ConditionalOnProperty`)

### Step 5: Fix listMembers to show actual roles

**File**: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`

Check if `listOrgMembers()` response includes user `attributes`. If yes, read `org_role` from there. Verify this with:
```bash
KC_URL="http://localhost:8180"
TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" | jq -r .access_token)
ORG_ID="ce9d5a97-4896-44b2-90fc-95520e4afe27"
curl -s -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/docteams/organizations/$ORG_ID/members" | jq '.[0] | keys'
```

If members response includes `attributes`, read role from `attributes.org_role[0]`. If not, fall back to `creatorUserId` + "member" default.

### Step 6: Set owner role at approval time

**File**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`

After `setOrgCreator()`, also try to set `org_role=owner` on the user:
```java
// Step 4b: Set org_role user attribute (no-op if user hasn't accepted yet)
try {
    String userId = keycloakProvisioningClient.findUserIdByEmail(request.getEmail());
    if (userId != null) {
        keycloakProvisioningClient.setUserOrgRole(userId, "owner");
    }
} catch (Exception e) {
    log.debug("Could not set org_role at approval time: {}", e.getMessage());
}
```

Make `findUserIdByEmail` public (currently private).

## Key Rules

1. **NEVER send a partial PUT to Keycloak**: Always GET the full object, merge changes, PUT back complete
2. **NEVER use `findOrganizationByAlias()` to read attributes**: Use `resolveOrgId()` + `getOrganization()`
3. **`invite-user` does NOT create the user**: Can't set user attributes at invitation time
4. **Test each Keycloak API call with curl first** before writing Java code

## Verification

1. `cd gateway && ./mvnw spotless:apply test` — all 50 tests pass
2. `cd backend && ./mvnw spotless:apply test -Dtest="*MemberFilter*,*AccessRequest*"` — all tests pass
3. Manual: create org → owner gets `org_role=owner` in JWT after login
4. Manual: invite admin → accepts → BFF returns "admin" → DB has "admin"
5. Manual: invite member → accepts → BFF returns "member" → DB has "member"
6. Check gateway logs for "Self-healed" or "Resolved pending" messages
