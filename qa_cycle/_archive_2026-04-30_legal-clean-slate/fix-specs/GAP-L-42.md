# Fix Spec: GAP-L-42 — Info-request email must embed a magic-link token and point to the portal

## Problem

Day 3 Checkpoint 3.14 (re-verified) and Day 4 Checkpoint 4.2 both failed: the information-request email sent after `POST /api/information-requests/{id}/send` contains only one `href`:

```
http://localhost:3000/portal
```

- Wrong host (firm `:3000`, not portal `:3002`)
- Literal path fragment — no deep link to the request
- **No magic-link token** — DB probe confirmed zero `magic_link_tokens` rows minted by the 20:15 info-request send; only the DevPortalController backfill at 20:12 produced rows.

Clicking the email link lands the client on the Keycloak-protected firm dashboard — useless to a portal-only user. QA's workaround was to mint a token via `POST /portal/dev/generate-link` and manually navigate to `/auth/exchange?token=…&orgId=mathebula-partners`, which is not available to real clients.

Every portal-POV day in the 90-day scenario (4 / 8 / 11 / 30 / 46 / 61 / 75) depends on delivered magic-link emails for pickup.

## Root Cause (confirmed via code read)

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailService.java` line 58

```java
context.put("portalUrl", appBaseUrl + "/portal");
```

- `appBaseUrl` is injected as `@Value("${docteams.app.base-url:http://localhost:3000}")` — the firm host, not the portal host.
- No call to `MagicLinkService.generateToken(...)` anywhere in the info-request send path — the `InformationRequestEmailEventListener.onRequestSent` (line 38) calls `emailService.sendRequestSentEmail(...)` with `portalContactId` only used to look up the email address, not to mint a token.
- The template `backend/src/main/resources/templates/email/request-sent.html` renders `${portalUrl}` directly (line 17).

Meanwhile a working magic-link path already exists for `PortalEmailService.sendMagicLinkEmail` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalEmailService.java` line 78+), which correctly constructs:

```
portalBaseUrl + "/auth/exchange?token=" + rawToken + "&orgId=" + orgId
```

using `@Value("${docteams.app.portal-base-url:http://localhost:3002}")`. The `MagicLinkService` (`backend/.../portal/MagicLinkService.java` line 63) exposes `generateToken(UUID portalContactId, String createdIp)` which (a) persists a token with 15-min TTL, (b) fires the generic portal-magic-link email as a side-effect.

## Fix

Inject `MagicLinkService` into `InformationRequestEmailEventListener` (not `InformationRequestEmailService`, so the email-service remains focused on templated content), call `generateToken(portalContactId, null)` **before** calling `emailService.sendRequestSentEmail(...)`, and pass the raw token through so the service builds the correct portal URL.

Because `MagicLinkService.generateToken` already sends a portal-magic-link email as a side-effect, we have two options:

- **Option A (preferred):** let both emails go. The client receives one "Information request REQ-0001 from Mathebula & Partners" email with the correct portal deep-link, AND one "Your portal access link from Mathebula & Partners" email. Redundant but harmless; the user clicks whichever arrives first. Zero refactor.
- **Option B:** expose a token-mint-only method on `MagicLinkService` (`generateTokenSilent`) and only send one email. Slightly cleaner UX but adds a method to the public API and touches the `MagicLinkService` unit tests.

**Choose Option A for this spec** — simpler, lower regression surface, the "extra" email is a magic-link anyway so it fully works as a fallback. Track Option B as optional polish if it surfaces in review.

### Implementation

**Step 1.** Inject `MagicLinkService` into `InformationRequestEmailEventListener`.

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailEventListener.java`

Add `MagicLinkService` field + constructor param. In `onRequestSent`, between the contact resolution and the `emailService.sendRequestSentEmail` call:

```java
// Mint a magic-link token so the email's CTA deep-links into the portal authenticated flow.
// generateToken also dispatches a redundant portal-magic-link email; that is acceptable for now.
String rawToken;
try {
  rawToken = magicLinkService.generateToken(event.portalContactId(), null);
} catch (Exception e) {
  log.warn("Failed to mint magic-link token for request {}; sending email without deep link",
      event.requestId(), e);
  rawToken = null;
}

emailService.sendRequestSentEmail(
    contact.getEmail(),
    contact.getDisplayName(),
    request.getRequestNumber(),
    items.size(),
    request.getId(),
    rawToken,
    contact.getOrgId());
```

Pass `orgId` from the contact (`PortalContact.orgId` stores the Clerk-format id used by `/auth/exchange`).

**Step 2.** Extend `InformationRequestEmailService.sendRequestSentEmail` signature to accept `rawToken` + `orgId`, and build the correct portal URL.

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailService.java`

Add `@Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl` to the constructor. Update the method:

```java
public void sendRequestSentEmail(
    String recipientEmail,
    String contactName,
    String requestNumber,
    int itemCount,
    UUID requestId,
    String rawToken,
    String orgId) {
  var context = emailContextBuilder.buildBaseContext(contactName, null);
  context.put("contactName", contactName);
  context.put("requestNumber", requestNumber);
  context.put("itemCount", String.valueOf(itemCount));

  String portalUrl;
  if (rawToken != null && orgId != null) {
    portalUrl = portalBaseUrl + "/auth/exchange?token=" + rawToken + "&orgId=" + orgId;
  } else {
    // Fallback — still point at the portal, not the firm
    portalUrl = portalBaseUrl + "/requests";
  }
  context.put("portalUrl", portalUrl);

  String orgName = (String) context.get("orgName");
  context.put("subject", "Information request %s from %s".formatted(requestNumber, orgName));
  sendEmail("request-sent", recipientEmail, context, requestId);
}
```

Leave the other `send*Email` methods (item-accepted, item-rejected, reminder, completed) alone — they use `portalUrl` generically for "visit your portal" and are not on the critical path; they can be migrated separately. (Ideally the CTA target on reminder emails would also be a fresh magic-link, but that's a different scope — call it out in the follow-up list.)

**Step 3.** Template does not need changes — `${portalUrl}` now resolves to the correct deep-link.

## Scope

- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailEventListener.java` (add `MagicLinkService` dependency + mint-token step in `onRequestSent`)
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestEmailService.java` (extend `sendRequestSentEmail` signature + inject `portal-base-url`)
- Files to create: none
- Tests to update:
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestNotificationAuditIntegrationTest.java` — assert `magic_link_tokens` row is minted, assert rendered email body contains `/auth/exchange?token=`.
  - Unit tests on `InformationRequestEmailEventListener` will need a `MagicLinkService` mock.
- Migration needed: no
- Env / config: `docteams.app.portal-base-url` should already be set in `application-local.yml` / `application-dev.yml` (used by `PortalEmailService`); no new property. Verify the property exists in the profile that runs QA — if missing, add it alongside `docteams.app.base-url`.

## Verification

1. Backend restart.
2. DB probe: `SELECT count(*) FROM tenant_5039f2d497cf.magic_link_tokens;` — note baseline.
3. QA (or automated test) re-dispatches a FICA info-request for Sipho:
   - `POST /api/information-requests/{id}/send` → 200.
   - `SELECT count(*) FROM tenant_5039f2d497cf.magic_link_tokens;` — incremented by **at least 1** (exactly 1 under Option A when `sendRequestSentEmail` mints once; 2 if both listeners fire).
   - Mailpit shows the info-request email. HTML body `<a href>` matches regex `^http://localhost:3002/auth/exchange\?token=[A-Za-z0-9_-]+&orgId=[a-z0-9-]+$`.
   - Plain-text body contains the same URL.
4. Click the link in a fresh browser (no existing `localStorage`) → lands on portal `/auth/exchange` → POST `/portal/auth/exchange` → 200, `portal_jwt` stored, redirect to portal home. Single-use token — second click → 401 "Magic link has already been used".
5. Re-run Day 4 Checkpoint 4.2 end-to-end without the DevPortalController workaround. PASS.
6. Token TTL observed at 15 min via `expires_at` column.
7. The redundant `portal-magic-link` email (Option A side-effect) arrives too; confirm its link also works. Log this in verification notes so reviewer is not surprised.

## Estimated Effort

**S–M (30–60 min)** — two Java file edits, one constructor param addition, two test updates. No migration, no frontend work. Risk: the shared GreenMail singleton on :13025 receives two emails per send under Option A; tests that assert exact message count need updating. `InformationRequestNotificationAuditIntegrationTest` is the only one that counts specifically.

## Follow-ups tracked separately

- **Reminder / completed / accepted / rejected emails** — currently also point at `appBaseUrl + "/portal"` (lines 74, 92, 113, 132 of `InformationRequestEmailService`). These are not on the Day 4 blocker path but will misbehave for Day 8/11/30 reminders. Spec as a LOW after this lands.
- **Option B refactor** — expose `MagicLinkService.generateTokenSilent(portalContactId)` that mints without firing the portal-magic-link email, and switch info-request send to it. Avoids double-email UX. Not blocking.
- **Email subject keyword drift** — scenario asserts `"sign in"` / `"action required"` / `"your portal"` keywords; current subject is `"Information request REQ-0001 from Mathebula & Partners"`. Out of scope here; adjust `subject` context value later if test-plan copy updates.

## Status Triage

**SPEC_READY.** Well under 2hr, high confidence in root cause + reuse path.
