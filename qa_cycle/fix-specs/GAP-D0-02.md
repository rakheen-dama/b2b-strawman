# Fix Spec: GAP-D0-02 — OTP + invitation emails branded "DocTeams"

## Problem
Both the access-request OTP email (sent by the backend via SMTP → Mailpit) and the Keycloak organization invitation email (sent by Keycloak's realm SMTP settings) are still branded "DocTeams". The OTP email subject reads "Your DocTeams verification code", the body text uses "DocTeams", and the default sender address is `noreply@docteams.app`. The KC invitation email's `From:` header is `noreply@docteams.local` with display name "DocTeams". Cannot show "DocTeams" emails to a law firm during a demo.

## Root Cause (confirmed)

### 1. Backend OTP email (Kazi-controlled — high leverage)
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestService.java`

Lines 42–43 (constructor default for sender address):
```java
@Value("${docteams.email.sender-address:noreply@docteams.app}") String senderAddress) {
```

Line 143 (hard-coded subject):
```java
helper.setSubject("Your DocTeams verification code");
```

Line 144–146 (hard-coded body):
```java
helper.setText(
    "Hi %s,\n\nYour DocTeams verification code is: %s\n\nThis code expires in %d minutes.\n\nIf you did not request this, please ignore this email."
        .formatted(fullName, otp, expiryMinutes),
    false);
```

### 2. Backend application config (property namespace + default sender)
File: `backend/src/main/resources/application.yml`, lines 74–76:
```yaml
docteams:
  email:
    sender-address: ${EMAIL_SENDER_ADDRESS:noreply@docteams.app}
```

### 3. Keycloak realm SMTP settings (KC-controlled — affects invitation email)
File: `compose/keycloak/realm-export.json`, lines 108–114:
```json
"smtpServer": {
  "host": "mailpit",
  "port": "1025",
  "from": "noreply@docteams.local",
  "fromDisplayName": "DocTeams",
  ...
}
```

KC invitation subject uses the built-in realm message bundle (not overridden) so it ends up as "Invitation to join the ... organization" — the sender is what reads "DocTeams".

## Fix

### Backend OTP email + sender default
1. In `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestService.java`:
   - Line 43: change the `@Value` default from `noreply@docteams.app` to `noreply@kazi.africa`.
   - Line 143: change subject from `"Your DocTeams verification code"` to `"Your Kazi verification code"`.
   - Lines 144–146: change the body string — replace both occurrences of "DocTeams" with "Kazi". New text:
     ```
     Hi %s,\n\nYour Kazi verification code is: %s\n\nThis code expires in %d minutes.\n\nIf you did not request this, please ignore this email.
     ```

2. In `backend/src/main/resources/application.yml`, lines 74–76:
   - Rename the top-level property key from `docteams:` to `kazi:` (keep `email.sender-address` under it).
   - Change default env fallback from `noreply@docteams.app` to `noreply@kazi.africa`.
   - **Must** also update the `@Value` key in `AccessRequestService.java` line 43 from `${docteams.email.sender-address:...}` to `${kazi.email.sender-address:noreply@kazi.africa}` to match.
   - Grep the repo for other consumers of the `docteams.email.*` namespace before merging — if any other service reads it, update those together (there should be none based on current search, but do the sweep).

### Keycloak realm SMTP
3. In `compose/keycloak/realm-export.json`, lines 108–114:
   - Change `"from": "noreply@docteams.local"` → `"from": "noreply@kazi.africa"`.
   - Change `"fromDisplayName": "DocTeams"` → `"fromDisplayName": "Kazi"`.

4. After modifying `realm-export.json`, the realm must be re-imported for the change to take effect in the running KC container. Document in the spec: either restart the KC container with `compose/scripts/dev-down.sh && dev-up.sh`, or invoke `compose/scripts/keycloak-bootstrap.sh` if it performs a realm update.

### Out of scope for this spec
- The 25+ other backend email templates under `backend/src/main/resources/templates/email/*.html` that contain "DocTeams" branding. These are not exercised in the Day 0 demo narrative. QA will surface them as separate gaps on later days if they appear. Do NOT bundle them into this spec — keep it focused.

## Scope
- Backend / Config / Keycloak realm
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestService.java`
  - `backend/src/main/resources/application.yml`
  - `compose/keycloak/realm-export.json`
- Files to create: none
- Migration needed: no
- Restart required: backend (Java change) + keycloak (realm re-import)

## Verification
1. Re-run CP 0.6 (OTP email in Mailpit).
   - Expected: Mailpit message subject = "Your Kazi verification code", From header = `noreply@kazi.africa`, body contains "Kazi" not "DocTeams".
2. Re-run CP 0.18 (KC invitation email in Mailpit after access-request approval).
   - Expected: From header = `"Kazi" <noreply@kazi.africa>`. Subject can still say "Invitation to join the ... organization" — that's KC's message bundle, acceptable for this gap.
3. Spot-check other backend startup: no yaml parse errors, no missing `@Value` key errors in logs.

## Estimated Effort
S (< 30 min)

## Priority Reason
LOW severity per QA, but a customer demo to a law firm CANNOT show "DocTeams" emails. Source is findable and change is small, so SPEC_READY.
