# Fix Spec: GAP-D0-01 — KeycloakProvisioningClient idempotency retry broken (alias lookup)

## Problem
When approval of an access request hits Keycloak's `POST /organizations` and the response is 409 Conflict (because a KC org with the same alias already exists from a partial/prior run), the retry path tries to look up the existing org by alias via `GET /organizations?search={alias}&exact=true`. Keycloak's `search` parameter matches on the **name** field, not alias, so this query always returns `[]` and the service throws `IllegalStateException("Keycloak returned 409 but no org found with alias ...")`. QA hit this during CP 0.15 and had to manually delete the stale KC org to unblock Day 0.

## Root Cause (confirmed)
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`, lines 116–130.

```java
private String findOrganizationByAlias(String alias) {
  var orgs =
      restClient
          .get()
          .uri("/organizations?search={alias}&exact=true", alias)  // ← search matches name, not alias
          .header("Authorization", "Bearer " + getAdminToken())
          .retrieve()
          .body(List.class);
  if (orgs == null || orgs.isEmpty()) {
    throw new IllegalStateException(
        "Keycloak returned 409 but no org found with alias: " + alias);
  }
  ...
}
```

QA verified directly against KC Admin API:
- `GET /organizations?search=mathebula-partners&exact=true` → `[]`
- `GET /organizations?search=mathebula` → returns the org (by name substring)

Keycloak 26.x exposes a dedicated `q` query parameter (or equivalent) that supports attribute lookups, plus the Organizations API has a direct filter on `alias`. The correct endpoint is `GET /organizations?q=alias:{alias}` or the newer `search` with filter syntax. The cleanest and most stable approach is: fetch all orgs (the dev realm has at most a handful) and filter client-side on `alias`.

## Fix
In file `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`:

1. Replace `findOrganizationByAlias(String alias)` (lines 116–130) with a version that:
   - Calls `GET /organizations?search={alias}` (no `exact`) — this narrows the result set when the alias happens to also appear in the name, but does NOT rely on the match.
   - Then iterates the returned list and matches on the `alias` field client-side.
   - If still no match, falls back to `GET /organizations?first=0&max=200` (paged) and scans client-side. 200 is safe for the dev realm; production orgs will be provisioned one at a time and 409 only fires for the one we just tried to create, so this fallback is only exercised in anomaly paths.

   New method body (illustrative):

   ```java
   @SuppressWarnings("unchecked")
   private String findOrganizationByAlias(String alias) {
     // First attempt: narrow by search (matches name, but may coincidentally contain alias)
     List<Map<String, Object>> orgs = (List<Map<String, Object>>)
         restClient.get()
             .uri("/organizations?search={alias}", alias)
             .header("Authorization", "Bearer " + getAdminToken())
             .retrieve()
             .body(List.class);
     String id = matchByAlias(orgs, alias);
     if (id != null) return id;

     // Fallback: list all and match client-side
     orgs = (List<Map<String, Object>>)
         restClient.get()
             .uri("/organizations?first=0&max=200")
             .header("Authorization", "Bearer " + getAdminToken())
             .retrieve()
             .body(List.class);
     id = matchByAlias(orgs, alias);
     if (id != null) return id;

     throw new IllegalStateException(
         "Keycloak returned 409 but no org found with alias: " + alias);
   }

   private static String matchByAlias(List<Map<String, Object>> orgs, String alias) {
     if (orgs == null || orgs.isEmpty()) return null;
     for (Map<String, Object> org : orgs) {
       if (alias.equals(org.get("alias"))) {
         return (String) org.get("id");
       }
     }
     return null;
   }
   ```

2. Add a log line at INFO level when the fallback branch (list-all) is used, so we can detect frequency in production.

3. No new imports required beyond `java.util.Map` (already in scope via `List.class` generics — use explicit import if it simplifies the code).

## Scope
- Backend
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`
- Files to create: none
- Migration needed: no

## Verification
1. Pre-state: clean KC org for `mathebula-partners`, clean `public.access_requests` + `public.organizations` rows, clean tenant schema.
2. Re-submit access request + verify OTP end-to-end (CP 0.1–0.11).
3. **Before** approving, use KC Admin API to pre-create a dangling org with alias `mathebula-partners`:
   ```
   curl -X POST http://localhost:8180/admin/realms/docteams/organizations \
     -H "Authorization: Bearer <admin token>" \
     -H "Content-Type: application/json" \
     -d '{"name":"Mathebula & Partners","alias":"mathebula-partners","enabled":true,"redirectUrl":"http://localhost:3000"}'
   ```
4. Approve the access request (CP 0.15).
5. **Expected**: Approval succeeds with 200. Backend logs `"Keycloak org with alias 'mathebula-partners' already exists, looking up ID"` followed by no stack trace. `public.organizations` row gets `COMPLETED`, tenant schema provisioned. Pending tab emptied.
6. Additionally re-run CP 0.15 with a **pristine** KC state — approval should still succeed (regression check that the new code doesn't break the happy path).

## Estimated Effort
S (< 30 min)

## Priority Reason
Only HIGH in this cycle — blocks QA from advancing past Day 0 CP 0.15 any time KC state is not pristine. Also a real production-shaped bug in the retry/idempotency contract that we absolutely want fixed before a customer demo where the environment isn't guaranteed clean.
