# Fix Spec: BUG-KC-003 — Keycloak user passwords not set during provisioning

## Problem
Users created through the access-request approval flow (Thandi Thornton, Bob Ndlovu) cannot log in because their passwords are not set in Keycloak. The admin had to manually reset passwords via the Keycloak Admin REST API (`PUT /admin/realms/docteams/users/{id}/reset-password`). This happens because the provisioning flow uses Keycloak's invite-user endpoint which sends an email invitation, but in local dev the user never completes the Keycloak registration form to set their password.

## Root Cause (hypothesis)
The `AccessRequestApprovalService` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java`, line 104) calls `keycloakProvisioningClient.inviteUser(kcOrgId, request.getEmail())`, which calls the Keycloak Admin REST API endpoint `/organizations/{orgId}/members/invite-user` (in `KeycloakProvisioningClient.java`, line 137-147).

This Keycloak endpoint sends an invitation email. The invited user must:
1. Receive the email (captured by Mailpit in local dev)
2. Click the registration link
3. Set their password via Keycloak's registration form
4. Accept the org membership

In local dev/QA, users are not expected to complete this multi-step email flow. The `keycloak-bootstrap.sh` script (`compose/scripts/keycloak-bootstrap.sh`) only sets a password for the platform admin (`padmin@docteams.local`, line 168) and does not handle passwords for users created through the provisioning flow.

The `keycloak-seed.sh` script (`compose/scripts/keycloak-seed.sh`) correctly sets passwords via `$KCADM set-password` (line 98 in the `create_user()` function), but this script is for the separate seed scenario (acme-corp), not for the provisioning flow used in QA.

## Fix
Add a `setUserPassword` method to `KeycloakProvisioningClient` and call it after inviting the user, gated behind a dev/local flag so production retains the email-based invite flow.

### Step 1: Add `setUserPassword` to `KeycloakProvisioningClient`
In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java`:

Add a new method:
```java
public void setUserPassword(String email, String password) {
    String userId = findUserIdByEmail(email);
    if (userId == null) {
        log.warn("Cannot set password — user {} not found in Keycloak", email);
        return;
    }
    restClient
        .put()
        .uri("/users/{userId}/reset-password", userId)
        .header("Authorization", "Bearer " + getAdminToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("type", "password", "value", password, "temporary", false))
        .retrieve()
        .toBodilessEntity();
    log.info("Set password for user {}", email);
}
```

### Step 2: Call in approval flow for local/dev profiles
In `AccessRequestApprovalService.approve()`, after the invite call (line 104-105), add:
```java
// In local/dev, set a default password so users can log in without email flow
keycloakProvisioningClient.setUserPassword(request.getEmail(), "password");
```

Gate this behind a config property (e.g., `app.keycloak.set-default-password=true` in `application-local.yml` only).

### Step 3: Update keycloak-bootstrap.sh as a fallback
In `compose/scripts/keycloak-bootstrap.sh`, after the backfill loop (step 6/6), add a step that iterates all org members and ensures they have usable passwords:
```bash
# Set default password for all org members (dev convenience)
for j in $(seq 0 $((MEMBER_COUNT - 1))); do
    MEMBER_ID=$(echo "$MEMBERS_JSON" | jq -r ".[$j].id")
    $KCADM set-password -r "${REALM}" --userid "${MEMBER_ID}" --new-password "password" 2>/dev/null || true
done
```

## Scope
Backend + Seed script.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java` (add `setUserPassword` method)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java` (call `setUserPassword` after invite)
- `backend/src/main/resources/application-local.yml` (add `app.keycloak.set-default-password: true`)
- `compose/scripts/keycloak-bootstrap.sh` (add password backfill step for existing org members)

Files to create: none
Migration needed: no

## Verification
- Re-run the Keycloak auth pre-flight: Login as thandi@thornton-test.local with password "password" should succeed without manual admin API reset.
- Approve a new access request and verify the created user can log in immediately.
- Verify production profile does NOT set default passwords (config flag only in local profile).

## Estimated Effort
M (30 min - 2 hr) — backend method + config flag + bootstrap script update + testing
