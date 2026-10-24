# 624.11 — KC-observed: bare login → branded link → /matters

Observer: orchestrator's observer subagent, 2026-10-24 19:14–19:18 SAST (17:14–17:18Z).
Driver: Playwright MCP (plugin Chromium). Each context was made fresh: `clearCookies()`, then `localStorage.clear()` / `sessionStorage.clear()` on a same-origin probe page. Emptiness was verified before each step: 0 cookies, `[]` localStorage keys, `[]` sessionStorage keys.
Tokens are redacted everywhere. In the success-page screenshots, the dev-echo token text was replaced in the DOM with `[REDACTED]` before capture. Nothing else on the page was changed.

## Stack facts

| Item | Value |
|---|---|
| KC backend | :8080. `svc.sh` PID 89035 (`mvnw spring-boot:run`, cwd main checkout `backend/`) started Sat 24 Oct 19:12:52 SAST. The JVM listener is PID 89234, started 19:13:04, running from `b2b-strawman/backend/target/classes`. Health `UP`. |
| Backend code | main checkout @ `9b55b343915aafea6ff5b782c5275964e2cd328a`, which includes 624A/B/C |
| Global Flyway | `1 baseline` (2026-10-18) and `2 portal contact directory` (installed 2026-10-24 19:13:06) |
| Directory row | `portal.portal_contact_directory`: `sipho.dlamini@email.co.za / mathebula-partners / tenant_5039f2d497cf / Mathebula & Partners` |
| Portal under test | worktree `worktree-epic-624D/portal` @ `43985b215` (branch `phase88/07D-portal-login-bare`). It was served as a **production build**: `rm -rf .next`, then `NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8080 pnpm run build` (exit 0), then `pnpm start` (`next start --port 3002`, next-server PID 90196, cwd the worktree portal). The bundle grep found `localhost:8080` in 4 static chunks and `localhost:8081` in 0. |
| Why prod build | Under `next dev`, `app/auth/exchange/page.tsx` POSTs the single-use token twice (React StrictMode effect re-run). The second POST can then 401 (see `repro-and-observed.md`). That bug is pre-existing and not under test. The production build avoids it. |
| KC portal | stopped via `svc.sh stop portal` before the walk and restored after it (see Restore) |
| Rate-cap budget | 2 of 3 requests used for Sipho's email (cap is fresh after the 19:12 restart) |

## Verdicts

| Step | Verdict |
|---|---|
| 1 Bare `/login` → request-link with no orgId → 200 + truthful copy | **PASS** |
| 2 Exactly one branded Mailpit link with `orgId=mathebula-partners` → one exchange POST → `/matters` | **PASS** |
| 3 Fee-note deep link (fresh context) → branded bounce → link → `/invoices/<id>` | **PARTIAL (constructed link, not an email)**. The behaviour was observed PASS end-to-end. The link was hand-built in PortalLinkBuilder shape, not taken from a real fee-note email (see 3a). |
| 4 Second firm, same email → two links | **DEFERRED to 624I** (ruling R-15) |
| 5 Restore | **DONE** |

## Step 1 — bare login (fresh context)

- Opened `http://localhost:3002/login` with no query string. Pre-state: 0 cookies, localStorage `[]`, sessionStorage `[]`. The page showed "Kazi / Sign in to your client portal / Email address / Send Magic Link" (unbranded, as expected with no orgId). Screenshot: `kc-01-bare-login.png`.
- Typed `sipho.dlamini@email.co.za` and clicked Send Magic Link at 17:15:31Z.
- Network (non-static), with exactly one request-link call:
  - `POST http://localhost:8080/portal/auth/request-link` → **200** (331 ms)
  - request body: `{"email":"sipho.dlamini@email.co.za"}` (**no orgId**)
  - response body: `{"message":"If an account exists, a link has been sent.","magicLink":"/auth/exchange?token=[REDACTED]&orgId=mathebula-partners"}` (KC `local` profile dev echo; not used for sign-in)
- Success copy: "Check your email. If sipho.dlamini@email.co.za is registered for a client portal, we've sent a sign-in link. If you work with more than one firm, you'll get one email from each." Screenshot: `kc-02-bare-login-success.png`.
- localStorage stayed `[]`, so no hint was written or used.

## Step 2 — Mailpit and exchange

- Mailpit `to:sipho.dlamini@email.co.za` total went from 14 to 15. Exactly **one** new message was created after the submit:
  - **`KTQZxn2RX48unAR7Dsur6s`**, created 2026-10-24T17:15:31.849Z
  - Subject: "Your portal access link from Mathebula & Partners"
  - From `noreply@kazi.app`, To `sipho.dlamini@email.co.za`
  - The body is branded "Mathebula & Partners" (header and sign-off) and reads "Hi Sipho Dlamini, … This link will expire in 15 minutes."
  - Link: `http://localhost:3002/auth/exchange?token=[REDACTED]&orgId=mathebula-partners`. It carries `orgId=mathebula-partners`, and its token equals the dev-echo token.
- Opened that Mailpit link in the same context:
  - `/portal/auth/exchange` responses: **exactly one**, `POST 200`
  - final URL `http://localhost:3002/matters`
  - localStorage now holds `portal_jwt`, `portal_customer`, and `portal_last_org_id=mathebula-partners`
- Matters listed (All tab): **"Sipho Dlamini v. Standard Bank (civil)"**, the only one ("Standard litigation workflow for personal injury and general civil matters. Matter type: LITIGATION", 0 documents). Header user "Sipho Dlamini". Screenshot: `kc-03-matters-after-exchange.png`.

## Step 3 — fee-note deep link without localStorage hint

### 3a — sourcing the link

- **Mailpit:** no fee-note mail to Sipho after the 19:12/19:13 backend restart. The only fee-note mail is `XjvC3zG6yBNwkHKNfVV69w` (INV-0001, 08:37Z, pre-624C, bare link).
- **Resend:** none exists for a SENT fee note.
  - `InvoiceController` exposes `POST /{id}/send`, but `Invoice` only allows send from `APPROVED` (`Invoice.java:243/261`). INV-0001 is `SENT`.
  - The other endpoints (`/payment`, `/refresh-payment-link`, `/void`) mutate data and don't send the fee-note email.
- **Getting a real 624C fee-note email would need a new fee note.** That is forbidden, because the KC data is reserved for the final re-walk.
- **Constructed link used instead, in PortalLinkBuilder shape:** `http://localhost:3002/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556?orgId=mathebula-partners`. Its id came from a read-only SELECT on `portal.portal_invoices` (INV-0001, SENT, customer `ad774504-…` = Sipho Dlamini).
- **Label: PARTIAL (constructed link, not an email).** 624C's real e2e fee-note email was already observed carrying `?orgId=` (624C evidence). The remaining real-email-on-KC check belongs to the final re-walk.

### 3b — observed behaviour

- **New fresh context:** browser closed, then cookies, localStorage and sessionStorage cleared. Verified 0 cookies, `[]`, `[]`, so there was no `portal_last_org_id` and no session.
- **Bounce:** opened the constructed link.
  - Final URL: `http://localhost:3002/login?redirectTo=%2Finvoices%2F8878574f-e15f-4936-a8c0-dd05d17b5556&orgId=mathebula-partners`. The incoming orgId was forwarded, and redirectTo has no orgId.
  - Requests made: `GET http://localhost:8080/portal/branding?orgId=mathebula-partners`, then the tenant logo from LocalStack.
  - Page: "Mathebula & Partners / Sign in to your client portal" with the firm logo and teal brand button. Screenshot: `kc-04-feenote-deeplink-bounce.png`.
  - `sessionStorage.portal_post_login_redirect = "/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556"`
- **Request link (Sipho request 2 of 3):**
  - `POST /portal/auth/request-link` → **200**
  - body `{"email":"sipho.dlamini@email.co.za","orgId":"mathebula-partners"}`. The orgId comes from the `?orgId=` query, which ruling R-1 says is the correct source; localStorage was empty.
  - Branded success copy, same text as step 1. Screenshot: `kc-05-feenote-login-success.png`.
- **Mailpit:** exactly one new message:
  - **`KiXVGm28CJ9vwoGf2UU5tv`**, 2026-10-24T17:16:34.32Z, "Your portal access link from Mathebula & Partners", branded
  - link `http://localhost:3002/auth/exchange?token=[REDACTED]&orgId=mathebula-partners`
- **Exchange:** opened the Mailpit link in the same context.
  - `/portal/auth/exchange`: exactly one `POST 200`
  - final URL **`http://localhost:3002/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556`**
  - `portal_post_login_redirect` consumed (sessionStorage now `{}`)
- **Page shows the fee note:**
  - "INV-0001 · SENT · Issued 24 Oct 2026 · Due 23 Nov 2026", with Download PDF and Pay Now
  - 3 line items: Bob Ndlovu drafting 1.5 × R 1 200,00; Carol Mokoena research 2 × R 550,00; Deeds office search fee R 1 250,00 zero-rated
  - Subtotal R 4 150,00, VAT R 435,00, Total R 4 585,00
  - Screenshot: `kc-06-feenote-landing.png`

## Step 4 — multi-firm

**DEFERRED to 624I** (R-15). The KC stack has only one firm with portal contacts. The backend proof in the meantime is the 624B `PortalAuthIntegrationTest` N-firm case. Not attempted.

## Restore

- The worktree `next start` was killed and :3002 confirmed free.
- `bash compose/scripts/svc.sh start portal` from the main checkout: "ready (3s)".
- `svc.sh status` portal row: `portal 91456 yes yes 3002`. The listener is next-server PID 91499, cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`; `/login` returns 200.
- No worktree portal processes remain. The worktree `git status` was clean (`portal/next-env.d.ts` not dirtied; `.next` is ignored and not staged).

## Notes (not defects under test)

- The firm logo renders as a solid dark-blue square, both on the branded login card and in the portal header. It is served from LocalStack `org/tenant_5039f2d497cf/branding/logo.png`, which appears to be the QA placeholder asset. This is out of scope and not investigated.
- `next start` warns that it "does not work with output: standalone". The server still served every route used here correctly.
