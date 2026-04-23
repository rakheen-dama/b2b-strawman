# Fix Spec: GAP-L-50 — Acceptance email points at dead `:3001` host

## Problem

Day 7 Checkpoint 7.11 (PARTIAL→FAIL) and Day 8 Checkpoint 8.1 (BLOCKER) both halt
because the email sent by `Send for Acceptance` (firm-side Generate Document →
Engagement Letter → Send flow) embeds a single `href`:

```text
http://localhost:3001/accept/<token>
```

- **Wrong port.** Port `3001` is the defunct legacy E2E-mock host — not bound in
  the current Keycloak stack. `curl http://localhost:3001/` returns exit 7
  (connection refused). Portal runs on `3002`.
- **Also mis-configured property key.** Even if `:3001` were swapped for an env
  override, the property name the constructor reads (`docteams.portal.base-url`)
  does not exist in any `application*.yml` — every other portal service reads
  `docteams.app.portal-base-url` (`application.yml:86` = `http://localhost:3002`
  default, env `PORTAL_BASE_URL`). So the literal default is what ships.

Backend API `GET /api/portal/acceptance/{token}` already returns 200 with a valid
payload on the token, so token minting works — the defect is isolated to the URL
builder in the acceptance email service.

Same root-cause shape as fixed **GAP-L-42** (info-request email host), but at a
different service layer (`AcceptanceService` / `AcceptanceNotificationService`,
not `InformationRequestEmailService`).

## Root Cause (confirmed via code read)

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceService.java` line 93

```java
@Value("${docteams.portal.base-url:http://localhost:3001}") String portalBaseUrl)
```

- Property key is `docteams.portal.base-url`. `application.yml:86` actually
  declares `docteams.app.portal-base-url` (env `PORTAL_BASE_URL`, default
  `http://localhost:3002`). Every sibling service uses the canonical name:
  `PortalEmailService.java:58`, `PortalDigestScheduler.java:69`,
  `PortalEmailNotificationChannel.java:74`,
  `InformationRequestEmailService.java:41`, `PaymentLinkService.java:34`. This is
  the only service that guesses a non-existent key.
- Because the key never resolves, the Spring default (`http://localhost:3001`) is
  always used.

The URL is composed in `AcceptanceNotificationService.java` line 87:

```java
String acceptanceUrl = portalBaseUrl + "/accept/" + request.getRequestToken();
```

`portalBaseUrl` is passed in from `AcceptanceService.createAndSend` line 228 and
`AcceptanceService.remind` line 550.

No magic-link-token wiring is needed for this flow — the acceptance token in the
URL is the long-lived `AcceptanceRequest.requestToken` (cryptographically random,
TTL = `expiresAt` days, resolved via `resolveByToken`). That path already works:
the portal `/accept/[token]` page calls `GET /api/portal/acceptance/{token}` and
backend responds 200 with `AcceptancePageData`. Confirmed from the day-08 evidence
(`day-08.md` line 22).

This is strictly a config-key typo / wrong-default fix.

## Fix

One-line edit to `AcceptanceService.java` line 93 — swap the property key and
default to match the canonical convention.

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceService.java`

```diff
-      @Value("${docteams.portal.base-url:http://localhost:3001}") String portalBaseUrl) {
+      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl) {
```

No signature change, no new constructor param, no listener wiring (unlike L-42
which needed `MagicLinkService` injection — acceptance tokens are not magic-link
tokens).

No template changes — `acceptance-request.html` and `acceptance-reminder.html`
already render `${acceptanceUrl}` as-is.

## Scope

- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceService.java` (line 93, one-line property-key + default swap)
- Files to create: none
- Tests to add/update:
  - No existing tests reference `portalBaseUrl` or `http://localhost:3001` for
    acceptance flow (verified by grep). Add a small assertion to
    `PortalAcceptanceControllerIntegrationTest` or an
    `AcceptanceNotificationServiceIntegrationTest` (whichever exercises email
    dispatch) to assert the rendered HTML `<a href>` matches the regex
    `^http://localhost:3002/accept/[A-Za-z0-9_-]+$`. If no such integration test
    exists yet, write a thin one that:
    1. Provisions a tenant + generated document + portal contact.
    2. Calls `AcceptanceService.createAndSend(...)`.
    3. Pulls the message from `GreenMailTestSupport.getInstance()` (shared singleton on `:13025`).
    4. Regex-asserts the `<a href>` host+path.
- Migration needed: no
- Env / config: `PORTAL_BASE_URL` (and `docteams.app.portal-base-url`) are
  already declared in `application.yml` and required by the portal layer. No new
  property; this fix just points to the existing one.

## Verification

1. Backend restart (`bash compose/scripts/svc.sh restart backend`) — Java source
   edit requires rebuild; no hot-reload.
2. Re-send an acceptance request through the firm UI (Thandi → matter →
   Generate Document → Engagement Letter — Litigation → Save to Documents → Send
   for Acceptance → Sipho).
3. Open Mailpit. The new email's single `<a href>` must match:
   ```text
   ^http://localhost:3002/accept/[A-Za-z0-9_-]+$
   ```
   (no `:3001`.)
4. Click the link (or direct-nav on the correct `:3002` host). Without
   GAP-P-06's fix the portal still renders "Unable to process" — that is the
   second half of the compound Day 8 blocker. Pair this fix with GAP-P-06.
5. Direct curl on the rebuilt email path:
   `curl -I http://localhost:3002/accept/<token>` → 200 (Next.js resolved the
   page route, regardless of render outcome).

## Estimated Effort

**S (< 15 min)** — one-character change effectively (key path + default). Zero
regression surface: no other caller of `AcceptanceService` reads the
`portalBaseUrl` field, no test references the :3001 default, and the canonical
property already exists in `application.yml`.

## Status Triage

**SPEC_READY.** Smallest possible unblock; root cause confirmed via direct code
read + property grep. Ship alongside GAP-P-06 so Day 8 flips end-to-end.
