# Fix Spec: GAP-PE-002 — Magic link URL in email points to :3000 not :3002

## Problem
The magic link email contains a URL pointing to `http://localhost:3000/portal/auth?token=...` (the firm-side frontend) instead of `http://localhost:3002/auth/exchange?token=...&orgId=...` (the portal). The portal exchange page lives at `:3002/auth/exchange`, not `:3000/portal/auth`. In production, the portal and firm app will have separate domains, so this must use the portal base URL.

Evidence from QA Cycle 1 (T1.1.6): "Email link points to `http://localhost:3000/portal/auth?token=...` (frontend :3000, not portal :3002)."

## Root Cause (hypothesis)
`PortalEmailService.java` line 79:
```java
String magicLinkUrl = appBaseUrl + "/portal/auth?token=" + rawToken;
```
The `appBaseUrl` field is injected from `${docteams.app.base-url:http://localhost:3000}` (line 44). This is the **firm-side** frontend URL. The portal URL `${docteams.app.portal-base-url:http://localhost:3002}` exists in `application.yml` (line 86) but is not used by `PortalEmailService`.

Additionally, the URL path `/portal/auth?token=` is wrong — the portal exchange page is at `/auth/exchange?token=...&orgId=...`.

## Fix

1. In `PortalEmailService.java`, replace the `appBaseUrl` field usage with a new `portalBaseUrl` field:
   ```java
   @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl
   ```
   Add this as a constructor parameter alongside the existing `appBaseUrl`.

2. Change line 79 from:
   ```java
   String magicLinkUrl = appBaseUrl + "/portal/auth?token=" + rawToken;
   ```
   To:
   ```java
   String magicLinkUrl = portalBaseUrl + "/auth/exchange?token=" + rawToken + "&orgId=" + orgId;
   ```
   (The `orgId` parameter is needed by the portal exchange page — see `portal/app/auth/exchange/page.tsx`.)

3. The `orgId` value should come from the new parameter added in GAP-PE-001 fix (threaded from the auth controller).

## Scope
Backend only
Files to modify:
- `backend/src/main/java/.../portal/PortalEmailService.java` — inject `portalBaseUrl`, fix URL construction
Migration needed: no

## Verification
- Re-run T1.1.6: Email link should point to `http://localhost:3002/auth/exchange?token=...&orgId=thornton-associates`
- Click the email link: should land on portal exchange page and authenticate successfully

## Estimated Effort
S (< 30 min) — combined with GAP-PE-001 fix
