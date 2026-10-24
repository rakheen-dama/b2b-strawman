# 624D — reproduction (pre-fix) and observed behaviour (post-fix)

Driver: Playwright MCP (plugin), fresh context each time (`clearCookies()`, probe page, `localStorage.clear()` / `sessionStorage.clear()`). Tokens redacted.

## Pre-fix (KC dev portal :3002, main checkout @ 9b55b3439, 2026-10-24 ~19:00 SAST)

R1 bare `/login` — `pre-01-bare-login-after-submit.png`
- Before submit: URL `http://localhost:3002/login`, localStorage keys `[]`.
- Typed `builder-624d-probe@example.com` → Send Magic Link.
- Page: "Kazi / Sign in to your client portal / … We couldn't determine which organization to sign you into. Please use the original link from your email."
- Network (non-`_next`): only `GET /login`. **No request-link POST** (short-circuit `login/page.tsx:99-106`). REPRODUCED.

R2 624C-shaped deep link — `pre-02-deep-link-bounce.png`
- Opened `/invoices/00000000-0000-0000-0000-000000000001?orgId=mathebula-partners`.
- Final URL `/login?redirectTo=%2Finvoices%2F00000000-0000-0000-0000-000000000001` — **incoming `orgId` dropped**; page unbranded ("Kazi"); no branding request.
- `sessionStorage.portal_post_login_redirect = "/invoices/00000000-0000-0000-0000-000000000001"`. REPRODUCED.

## Post-fix (worktree portal, `next build` + `next start -p 3002`, NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081, e2e backend)

F1 bare `/login` — `post-01-bare-login-after-submit.png`
- Request: `POST http://localhost:8081/portal/auth/request-link` body `{"email":"builder-624d-probe@example.com"}` (no orgId).
- Response 200 `{"message":"If an account exists, a link has been sent.","magicLink":null}`.
- Page: "Kazi / Check your email. / If builder-624d-probe@example.com is registered for a client portal, we've sent a sign-in link. If you work with more than one firm, you'll get one email from each."

F2 fee-note-shaped deep link — `post-02-deep-link-forward.png`
- Opened `/invoices/00000000-0000-0000-0000-000000000001?orgId=e2e-test-org`.
- Final URL `/login?redirectTo=%2Finvoices%2F00000000-0000-0000-0000-000000000001&orgId=e2e-test-org`; `GET /portal/branding?orgId=e2e-test-org`; title "E2E Test Organization".
- `portal_post_login_redirect = "/invoices/00000000-0000-0000-0000-000000000001"`.

F3 digest unsubscribe deep link (R-4)
- Opened `/settings/notifications?unsubscribe=1&orgId=e2e-test-org`.
- Final URL `/login?redirectTo=%2Fsettings%2Fnotifications%3Funsubscribe%3D1&orgId=e2e-test-org`; `portal_post_login_redirect = "/settings/notifications?unsubscribe=1"`.

login-bare spec (Mailpit link, contact@acme.example.com): 2 consecutive runs `1 passed` (3.5s, 3.4s).

## New finding (pre-existing, out of scope): dev-mode exchange double POST

`app/auth/exchange/page.tsx` runs the exchange inside a `useEffect`. Under `next dev` React StrictMode mounts → cleans up → re-runs the effect, so **two** `POST /portal/auth/exchange` calls go out with the same single-use token. The first run's result is discarded (`cancelled = true`); the second run's result drives the UI. Whichever request reaches the backend second gets 401 ("Magic link has already been used").

- `next dev` (worktree portal, leftover contact `sipho-zhod@example.com`, token via backend dev echo): exchange statuses `[401, 200]` → page "Login Failed / Link expired or invalid". Also the first login-bare spec run (dev server): failed at `waitForURL("**/matters")` on that same "Link expired or invalid" page; backend log shows exactly one successful exchange.
- `next start` (production build, same contact, fresh token): exchange statuses `[200]` → `/matters`.

The outcome in dev is a race (it depends on which POST the backend serves first), so a dev-mode walk (the KC dev stack runs `pnpm dev`) can fail at the exchange step intermittently. This affects the 624.11 KC observation.
