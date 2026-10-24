# 624.1 — Reproduce G-D1 (portal login org resolution) on the KC dev stack

- **When:** 2026-10-24, 13:43:42 – 13:45:38 SAST
- **Stack:** Keycloak dev stack, already running (backend :8080 pid 70060, gateway :8443, frontend :3000, portal :3002 pid 68555; `svc.sh status` all up). Nothing was restarted, rebuilt or reseeded. Built from the main 6cc2b525e era. Main is now 48575859a, and every commit since then is docs-only.
- **Tenant:** Mathebula & Partners (`mathebula-partners`, schema `tenant_5039f2d497cf`)
- **Client contact:** Sipho Dlamini, `sipho.dlamini@email.co.za`
- **Driver:** Playwright MCP (plugin), not claude-in-chrome. REST was used only for the Mailpit API, plus one direct backend `curl` (step 4) to pin the contract.
- **Secrets:** token values are written as `<redacted>`.

## Verdict summary

| # | Symptom (624.1 spec) | Verdict |
|---|---|---|
| S1 | Fresh context, bare `:3002/login`, Sipho's email, no org → nothing sent / "organisation required" | **REPRODUCED** |
| S2 | Backend contract: `request-link` without `orgId` is refused | **REPRODUCED** (400, `orgId is required`) |
| S3 | A fee-note email link bounces to `/login?redirectTo=…` without `orgId` | **REPRODUCED** |

Mailpit stayed at **14 messages** for the whole run, and the latest id was `CeJo3ft4EfnhuJ4hc5TA2S` before and after. No magic-link mail was sent.

## Step 1 — Mailpit baseline (13:43:42 SAST)

`GET http://localhost:8025/api/v1/messages?limit=50` → `total 14`. Latest id `CeJo3ft4EfnhuJ4hc5TA2S` (09:07:36Z, Trust account activity, moroka.trust@). Mail to Sipho already in the inbox:

| Mailpit id | Created (Z) | Subject |
|---|---|---|
| `YRvSgdpYnmvyouhfPjkp9Q` | 09:04:42 | Your portal access link from Mathebula & Partners |
| `XjvC3zG6yBNwkHKNfVV69w` | 08:37:13 | Fee Note INV-0001 from Mathebula & Partners |
| `YjVWPRYXWBNiu6fVNGw7op` | 07:34:00 | Mathebula & Partners: Trust account activity |
| `TFCEhbVRgeFpbmitdmhXYK` | 07:33:21 | Mathebula & Partners: Trust account activity |
| `d9gZS6wKeVv9x8VLgBGSz8` | 07:29:15 | Confirmed: You have accepted engagement-letter-… |
| `28EhbiQiutroWDXG8HUoB8` | 07:28:46 | … Please review engagement letter for acceptance … |

## Step 2 — Bare `/login` in a fresh context (S1)

**Making the context fresh.** I closed the page first. The context still held 4 `localhost` cookies (`AUTH_SESSION_ID`, `KEYCLOAK_IDENTITY`, `KEYCLOAK_SESSION`, `SESSION`, left over from Keycloak/firm-app use). I cleared them with `context.clearCookies()`. I then loaded a throwaway 404 page on the :3002 origin (`/__624a_blank_probe`) and checked storage: `localStorage` keys `[]`, `portal_last_org_id` = `null`, `sessionStorage` keys `[]`. I ran `localStorage.clear()` / `sessionStorage.clear()` anyway. Only after that did I open `/login`.

**Navigate `http://localhost:3002/login` (no query string).**
- Observed URL: `http://localhost:3002/login`. `localStorage.portal_last_org_id` = `null`, `document.cookie` = `""`.
- The page does **not** short-circuit and shows **no** org error on load. It renders the normal form: "Kazi / Sign in to your client portal / Email address [you@example.com] / Send Magic Link". There is no firm branding because there is no `orgId` to fetch it with. Screenshot: `01-login-bare.png`.

**Enter `sipho.dlamini@email.co.za` → click Send Magic Link.**
- Observed: an inline red error, *"We couldn't determine which organization to sign you into. Please use the original link from your email."* The URL stays `/login`. Screenshot: `02-after-submit.png`.
- Network (`network-login.txt`): **no** request to `/portal/auth/request-link`, `/portal/branding` or `:8080` at all. The only document request is `GET /login → 200`; the other 20 entries are static chunks and fonts. The client-side guard short-circuits before posting (`portal/app/login/page.tsx:99-106`: `if (!orgId) { setError(...); return; }`). So there is no request body or response to capture: **nothing was sent**.
- Expected: nothing sent / "organisation required". **Observed matches → REPRODUCED.** The wording is "We couldn't determine which organization…", not "organisation required". The error appears only after submit, not on page load.

## Step 3 — Mailpit after the UI submit

`total 14`, latest `CeJo3ft4EfnhuJ4hc5TA2S`. Unchanged, and no new mail to Sipho.

## Step 4 — Backend contract (S2)

The portal calls the backend directly, not through the gateway: `portal/lib/api-client.ts:5` uses `NEXT_PUBLIC_PORTAL_API_URL ?? "http://localhost:8080"`. Controller: `backend/.../portal/PortalAuthController.java:28-50`, record `MagicLinkRequest(@NotBlank @Email String email, @NotBlank(message = "orgId is required") String orgId)`.

```
$ curl -s -i -X POST http://localhost:8080/portal/auth/request-link \
    -H 'Content-Type: application/json' -H 'Origin: http://localhost:3002' \
    -d '{"email":"sipho.dlamini@email.co.za"}'
HTTP/1.1 400
Content-Type: application/problem+json
{"detail":"1 field(s) have validation errors","instance":"/portal/auth/request-link","status":400,
 "title":"Validation failed","fieldErrors":[{"field":"orgId","message":"orgId is required"}]}
```

The same call with `"orgId":""` returns the identical 400 / `orgId is required`.

Mailpit after (≈11:44:51Z): `total 14`, latest `CeJo3ft4EfnhuJ4hc5TA2S`. Unchanged. **REPRODUCED**: the backend has no email-only resolution path. `orgId` is mandatory.

## Step 5 — Fee-note email link (S3)

**Link shape.** Mailpit `XjvC3zG6yBNwkHKNfVV69w`, "Fee Note INV-0001 from Mathebula & Partners" → `sipho.dlamini@email.co.za`. Portal link:

```
http://localhost:3002/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556
```

The link is path-only. It has **no `?orgId=`** and no token. The other two fee notes have the same shape: `V3DU5k7H4FtHyDHg3yMznH` (INV-0003, nomvula.khoza@) → `/invoices/b4b05648-…`, and `SBqVXVMDaUU9ynhwcjrwVs` (INV-0002, moroka.trust@) → `/invoices/4591b2c1-…`. Each one also carries a `:8080/portal/dev/mock-payment?...&returnUrl=…/invoices/<id>/payment-success` link, again without an `orgId`.

Other client mail in the inbox, for context only (observed link shapes, not a builder census):

| Mailpit id | Mail | Portal link shape | Carries org? |
|---|---|---|---|
| `YRvSgdpYnmvyouhfPjkp9Q` | Portal access link | `/auth/exchange?token=<redacted>&orgId=mathebula-partners` | yes |
| `YjVWPRYXWBNiu6fVNGw7op` | Trust account activity | `/trust/e794d8d8-974b-4ac2-b073-e7ffc3bc9852` | **no** |
| `28EhbiQiutroWDXG8HUoB8` | Engagement letter acceptance | `/accept/<redacted>` | token-scoped (public page) |
| `d9gZS6wKeVv9x8VLgBGSz8` | Acceptance confirmation | no portal link | n/a |

**Open the fee-note link in a fresh context.** I closed the page and ran the same fresh-context procedure as step 2: cookies before = `[]`, cleared anyway; `localStorage` `[]`, `portal_last_org_id` `null`, cleared anyway. Then I opened `http://localhost:3002/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556`.
- First response: `200`. There is no HTTP redirect (redirect chain = just the invoice URL).
- Main-frame navigations: `/invoices/8878…` → `/invoices/8878…` → **`/login?redirectTo=%2Finvoices%2F8878574f-e15f-4936-a8c0-dd05d17b5556`**. This is a client-side `router.replace` from `portal/app/(authenticated)/layout.tsx:60-75`. It only appends `orgId` when `getLastOrgId()` (localStorage `portal_last_org_id`) is set, and in a fresh context it is not.
- **Final URL:** `http://localhost:3002/login?redirectTo=%2Finvoices%2F8878574f-e15f-4936-a8c0-dd05d17b5556`, with **no `orgId`**. The page shows the same unbranded form. Screenshot: `03-fee-note-link-bounce.png`.
- Expected: bounce to `/login?redirectTo=…` without `orgId`. **Observed matches → REPRODUCED.**

**Follow-through on the bounced page.** I entered Sipho's email and clicked Send Magic Link. The result was the same inline error, *"We couldn't determine which organization to sign you into. Please use the original link from your email."* The "original link from your email" is the link that led here, so the client has no way forward. Screenshot: `04-fee-note-bounce-after-submit.png`. Network (`network-fee-note.txt`): the only non-static requests are the 404 probe and the `/login?redirectTo=…&_rsc=…` RSC fetch. There was no `request-link`, no `branding` and no `:8080` call.

## Final Mailpit check (13:45:38 SAST)

I diffed the full 50-message listing before and after: **identical**. `total 14`, latest `CeJo3ft4EfnhuJ4hc5TA2S`. G-D1 produced no mail at any step.

## Spec deviations / notes

1. The spec says "'organisation required'". The UI actually says *"We couldn't determine which organization to sign you into. Please use the original link from your email."*, and only after submit. The backend's message is literally `orgId is required`, but the UI never reaches the backend. The substance of the symptom is unchanged (nothing sent, org cannot be resolved).
2. The bounce is a client-side navigation (200, then `router.replace`), not an HTTP 3xx.
3. The 4 leftover `localhost` cookies were Keycloak/firm-app cookies, not portal state (the portal keeps its JWT in localStorage). I cleared them anyway so the context was clean.

## Evidence files (this directory)

- `01-login-bare.png`: bare `/login`, fresh context, form rendered, no branding
- `02-after-submit.png`: org-resolution error after submitting Sipho's email
- `03-fee-note-link-bounce.png`: landing page after opening the INV-0001 link (final URL `/login?redirectTo=%2Finvoices%2F8878…`, no `orgId`)
- `04-fee-note-bounce-after-submit.png`: the same error on the bounced page
- `network-login.txt`, `network-fee-note.txt`: Playwright network logs (no `request-link` call in either)
