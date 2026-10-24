# 624.11 (624I gate) — KC-observed: real fee-note email deep link + multi-firm bare login

Observer: 624I KC-stack observer subagent, 2026-10-25 00:22–00:39 SAST (2026-10-24 22:22–22:39Z).
Driver: Playwright MCP (plugin Chromium, persistent profile). Every portal context was made fresh the same way 624D did it: `context.clearCookies()`, then `localStorage.clear()` and `sessionStorage.clear()` on a same-origin probe page (`http://localhost:3002/robots.txt`). Emptiness was verified before each portal step: 0 cookies, `[]` localStorage keys, `[]` sessionStorage keys. Note that the persistent profile keeps `localStorage` across `browser_close` (observed before B4b: `["portal_jwt","portal_customer","portal_last_org_id"]` was still there), so the explicit clear is required.
Every link was opened **from the email itself**: the Mailpit HTML render (`http://localhost:8025/view/<id>.html`) was loaded and its anchor clicked. No link was constructed or hand-typed.
Tokens are redacted everywhere (`[REDACTED]`). In the two request-link success screenshots, the dev-echo token text and the `href` were replaced in the DOM with `[REDACTED]` before capture. Nothing else on the page was changed.

## Stack facts

| Item | Value |
|---|---|
| KC backend | :8080. `svc.sh` PID 20377 (`mvnw spring-boot:run`, main checkout `backend/`) started Sun 25 Oct 00:19:03 SAST. JVM listener PID 20632, started 00:19:20, classpath `b2b-strawman/backend/target/classes`. "Started BackendApplication in 14.042 seconds" at 22:19:35Z (`logs/kc-backend-restart.log`). Health `UP` before and after the walk. |
| Backend code | main checkout @ `bd32f07ff2729c5f5d611a597c61d568cd022663` (= origin/main; includes 624A–624H) |
| Tenant Flyway V5 | from the restart log: `tenant_56399d06a773` → "5 - phase88 acceptance decline" applied 22:19:35.360Z; `tenant_5039f2d497cf` → applied 22:19:35.400Z (`flyway_schema_history` V5 installed 2026-10-25 00:19:35 SAST). The new tenant `tenant_db75730dfc5f` (Khanyile, provisioned during this walk) was migrated V1→V5 at 00:31:23–00:31:25 SAST. |
| Global Flyway | `1 baseline` (2026-10-18) and `2 portal contact directory` (2026-10-24 19:13:06) |
| Portal under test | worktree `worktree-epic-624I/portal` @ `bd32f07ff` (branch `phase88/07I-gate`), served as a **production build**. Steps: `pnpm install --frozen-lockfile` at the worktree root (node_modules was missing; done in 8.6 s); `rm -rf .next`; `NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8080 pnpm run build` (exit 0, `logs/portal-build-tail.log`); `nohup pnpm run start` (`next start --port 3002`). next-server PID 22471 (parent pnpm 22433), cwd `/Users/rakheendama/Projects/2026/worktree-epic-624I/portal`, "Ready in 109ms" (`logs/portal-next-start.log`). The bundle grep found `localhost:8080` in 4 files under `.next/static` and `localhost:8081` in 0. |
| Why prod build | Ruling R-3: under `next dev` the exchange page double-POSTs. That fix has not landed. |
| KC portal | `svc.sh stop portal` before the walk (:3002 confirmed free) and restored after it (see Restore). |
| Firm app / gateway | :3000 frontend (svc.sh PID 68497, `next dev`), :8443 gateway (ext 68482). Both healthy throughout. |
| Rate-cap budget | Sipho's email: 1 request-link used in this walk (cap 3 / 15 min, fresh after the 00:19 restart). `multifirm.624i@email.co.za`: 1 request used. |

## Rulings applied

- **R-1 KC data is not reserved.** A new disbursement and fee note were created for Sipho. A second firm, two clients and two matters were also created.
- **R-2 Browser only for writes.** Every product write went through the firm UI (:3000) or the portal UI (:3002). The only exception is the R-5 Keycloak admin-REST cleanup. Every DB access was a read-only SELECT, and Mailpit was used through its API and HTML view.
- **R-3 Prod build of the worktree portal.** As in Stack facts.
- **R-4 Dedicated multi-firm email.** `multifirm.624i@email.co.za` ("Lerato Multifirm") was used. Sipho's address was not used for Step B.
- **R-5 Second firm through the real flow, then KC cleanup.** The DB tenant was left in place (see Data created). *(Superseded after review H-1: the DB footprint was removed too. See `logs/kc-D-db-cleanup.txt`.)*
- **R-6 Restore.** Done (see Restore).
- **R-7 Nothing PASS unless observed.** Every verdict below cites the Mailpit id, the network request and response, the final URL and a screenshot.

## Verdicts

| Step | Verdict |
|---|---|
| A — real fee-note email deep link (Mathebula, Sipho): new INV-0004 → email link `?orgId=` → fresh context → branded bounce → request-link with orgId → one magic link → one exchange → `/invoices/{id}` | **PASS** |
| B — multi-firm: bare `/login` → one request-link with no orgId → two branded mails, one per firm → each link lands in `/matters` of its own firm, isolated | **PASS** |
| C — Restore (R-6) + KC cleanup (R-5) | **DONE** |

## Step A — real fee-note email deep link

### A1 — create, approve and send a new fee note (firm UI, Thandi)

- Signed in at :3000 as `thandi@mathebula-test.local` through the Keycloak form. Landed on `/org/mathebula-partners/dashboard`.
- **First attempt.** Fees → Fee notes → New fee note → Sipho Dlamini → Fetch Unbilled Time returned "No unbilled time entries found for this period", so "Validate & Create Draft" was disabled. Sipho had no unbilled time or disbursements.
- **Created a small disbursement in the UI.** Matter "Sipho Dlamini v. Standard Bank (civil)" → Fees ▸ Disbursements → New Disbursement:
  - Client: Sipho Dlamini
  - Category: Court Fees
  - Description: "624I court filing fee"
  - Amount: R 100,00, Zero-rated pass-through, Office Account
  - Incurred: 2026-10-25
  - Supplier: "Registrar Johannesburg High Court"
  - Created as Draft / Unbilled. On the disbursement detail (`/legal/disbursements/699277c6-9f9e-4d51-9a4c-93812bf43cfb`): **Submit for Approval**, then **Approve**, then **Approve Disbursement**. Result: "Approved · Unbilled", total R 100,00.
- **Fee note.** Client Sipho → Fees · Fee notes → New fee note → Fetch Unbilled Time. One disbursement was selected (R 100,00) → Validate & Create Draft.
  - The pre-generation check warned "Client has no tax number — the fee note will be sent without a client VAT number" (a warning, not a blocker).
  - Create Draft (1 issues) → Draft `0feb4764-f6f4-425d-9fb1-5ca250ab8c88`, line "Court fees: 624I court filing fee (Registrar Johannesburg High Court, 2026-10-25)", Zero-rated, total R 100,00.
  - **Approve** → **Send fee note** → **INV-0004 · Sent · Issued 25 Oct 2026 · Due 24 Nov 2026**. A mock online payment link was created. Screenshot: `kc-A1-firm-inv0004-sent.png`.
- DB read-back: `tenant_5039f2d497cf.invoices` → `0feb4764-… | INV-0004 | SENT | 100.00`. `portal.portal_invoices` → the same id, SENT, INV-0004 (`logs/kc-B1-readback.txt`).

### A2 — the fee-note email

- Mailpit `to:sipho.dlamini@email.co.za` went from 9 to 10 messages. The new message is **`8aKFRbFgpzPHombJZLFKQx`**:
  - created 2026-10-24T22:28:21.25Z
  - Subject "**Fee Note INV-0004 from Mathebula & Partners**"
  - From `noreply@kazi.app`, To `sipho.dlamini@email.co.za`
  - branded "Mathebula & Partners", "Hi Sipho Dlamini"
- Portal link: **`http://localhost:3002/invoices/0feb4764-f6f4-425d-9fb1-5ca250ab8c88?orgId=mathebula-partners`**. This is the PortalLinkBuilder shape (624C). It appears in **both** parts:
  - HTML: `<a href="…?orgId=mathebula-partners">View Fee Note</a>`, with no `target` attribute
  - text: `View Fee Note (http://localhost:3002/invoices/0feb4764-…?orgId=mathebula-partners)`
- The email also carries the mock payment link `http://localhost:8080/portal/dev/mock-payment?…&returnUrl=…/invoices/0feb4764-…/payment-success?orgId=mathebula-partners`. That link is not under test.
- Saved to `logs/kc-A2-feenote-email.txt`.

### A3 — open the email link in a fresh context

- Pre-state on the :3002 probe: 0 cookies, localStorage `[]`, sessionStorage `[]`. There was no `portal_last_org_id` and no session.
- Loaded the Mailpit HTML render of `8aKFRbFgpzPHombJZLFKQx` and clicked **View Fee Note**.
- Final URL: **`http://localhost:3002/login?redirectTo=%2Finvoices%2F0feb4764-f6f4-425d-9fb1-5ca250ab8c88&orgId=mathebula-partners`**.
  - Request: `GET http://localhost:8080/portal/branding?orgId=mathebula-partners` → 200.
  - Page: "Mathebula & Partners / Sign in to your client portal / Email address / Send Magic Link" (branded, with logo).
  - `sessionStorage.portal_post_login_redirect = "/invoices/0feb4764-f6f4-425d-9fb1-5ca250ab8c88"`, localStorage `[]`.
  - Screenshot: `kc-A3-feenote-deeplink-bounce.png`.

### A4 — request a link

- Typed `sipho.dlamini@email.co.za` and clicked Send Magic Link at ~22:29:04Z (corrected on review: the observer's 22:29:06Z was an estimate; Mailpit created `dQw5LfUsb3nqLjFPLHLsHi` at 22:29:04.937Z, which bounds the submit). There was exactly **one** request-link call:
  - `POST http://localhost:8080/portal/auth/request-link` → **200**
  - request body: **`{"email":"sipho.dlamini@email.co.za","orgId":"mathebula-partners"}`**
  - response body: `{"message":"If an account exists, a link has been sent.","magicLink":"/auth/exchange?token=[REDACTED]&orgId=mathebula-partners"}` (KC `local` profile dev echo; not used to sign in)
- Success copy, quoted: "Check your email. If sipho.dlamini@email.co.za is registered for a client portal, we've sent a sign-in link." This is the orgId form, so the multi-firm sentence is correctly absent (see Notes). Screenshot: `kc-A4-feenote-login-success.png` (token redacted in the DOM).
- Mailpit `to:sipho…` went from 10 to 11: exactly **one** new message, **`dQw5LfUsb3nqLjFPLHLsHi`**:
  - created 22:29:04.937Z (Mailpit clock)
  - "Your portal access link from Mathebula & Partners", branded
  - anchor "Access Portal" → `http://localhost:3002/auth/exchange?token=[REDACTED]&orgId=mathebula-partners`

### A5 — exchange and landing

- In the same context, loaded the Mailpit HTML render of `dQw5LfUsb3nqLjFPLHLsHi` and clicked **Access Portal**.
- `/portal/auth/exchange` responses: **exactly one**, `POST http://localhost:8080/portal/auth/exchange` → **200**. The network list has one request-link (#398) and one exchange (#415).
- Final URL: **`http://localhost:3002/invoices/0feb4764-f6f4-425d-9fb1-5ca250ab8c88`**.
  - `portal_post_login_redirect` was consumed (sessionStorage `{}`).
  - localStorage: `portal_jwt`, `portal_customer = {"id":"ad774504-…","name":"Sipho Dlamini","orgId":"mathebula-partners"}`, `portal_last_org_id = mathebula-partners`.
- Page shows the new fee note:
  - "**INV-0004 · Sent · Issued: 25 Oct 2026 · Due: 24 Nov 2026**", with Download PDF and "Ready to pay? … Pay Now"
  - line "Court fees: 624I court filing fee (Registrar Johannesburg High Court, 2026-10-25) · 1 · R 100,00 · Zero-rated 0% · R 100,00"
  - Subtotal R 100,00 · Zero-rated (0%) R 0,00 · **Total R 100,00**
  - header user "Sipho Dlamini"
  - Screenshot: `kc-A5-feenote-landing.png`

This replaces 624D's PARTIAL step 3 (constructed link) with a real 624C fee-note email observed on KC.

## Step B — multi-firm bare `/login`

### B1 — second firm and the multi-firm contact

**Second firm, provisioned through the real flow:**

- `/request-access` (fresh firm-app context):
  - Work Email `owner@khanyile-624i.local`, Full Name "Zanele Khanyile", Organisation "Khanyile Attorneys 624I", Country South Africa, Industry Legal Services → "Check Your Email".
  - OTP mail **`56TrHdNWvwRZGus8ZXWFRL`** ("Your Kazi verification code") → Verify → "Request Submitted … submitted for review".
- padmin approval (`padmin@docteams.local`) at `/platform-admin/access-requests`:
  - Pending row "Khanyile Attorneys 624I / owner@khanyile-624i.local / Zanele Khanyile / South Africa / Legal Services".
  - Approve → AlertDialog "Approve Access Request … create a Keycloak organization, provision a tenant schema, and send an invitation" → Approve.
  - Tooling incident: the first Approve click did nothing. It is the same stuck MCP input pipeline Session 0 recorded. `browser_close` + re-navigate fixed it, and the next click opened the dialog. Not a product gap.
- Result:
  - `org_schema_mapping` `khanyile-attorneys-624i → tenant_db75730dfc5f`
  - `org_settings.vertical_profile = legal-za`, currency ZAR
  - `access_requests` row APPROVED
  - invitation mail **`Wj9hU6GPhYpU5UViFTKgSR`** ("Invitation to join the Khanyile Attorneys 624I organization")
  - KC org id `be3c9226-a3e8-491f-b90e-4a450dcbd27f`
- Owner registration: the invite link (HTML-entity-decoded from the Mailpit text) was opened in a cleared context. KC page: "Create an account to join the Khanyile Attorneys 624I organization", email pre-filled. Entered Zanele / Khanyile / SecureP@ss9 → **`/org/khanyile-attorneys-624i/dashboard`** ("Zanele Khanyile · owner@khanyile-624i.local").

**How a portal contact is created in the UI.** Creating a client with an email auto-provisions an ACTIVE portal contact through `PortalContactAutoProvisioner` (on `CustomerCreatedEvent`). `PortalContactService.createContact` then reconciles the global directory after commit. There is no separate "add portal contact" UI. The portal `/matters` list shows `portal_projects` for the contact's customer. A matter cannot be created for a PROSPECT client (`CustomerLifecycleGuard` CREATE_PROJECT), so each client was moved to Onboarding first. What was done in each firm:

- **Khanyile (as Zanele):**
  - Clients → New Client: "Lerato Multifirm", Individual, `multifirm.624i@email.co.za`, +27-82-555-0624, Country South Africa → Next → Create Client → client `1f3967e1-795c-4590-bbb9-b8dd6ae03d11` (Prospect).
  - Start Onboarding → Start Onboarding (status Onboarding).
  - New matter: client Lerato Multifirm, template Commercial (Corporate & Contract), name "Lerato Multifirm — Shareholder agreement (Khanyile)" → matter `05a87979-653f-4135-8104-6f34af4d62ab`.
- **Mathebula (as Thandi, after clearing cookies and signing in again):** the same steps → client `98f91243-4156-4a83-b261-45347de14062` → Onboarding → matter "Lerato Multifirm — Lease review (Mathebula)" `a79db65e-c065-4101-b334-d541941b1166`.

**Read-back** (`logs/kc-B1-readback.txt`):

- `portal.portal_contact_directory` has **two rows**:
  - `multifirm.624i@email.co.za | khanyile-attorneys-624i | tenant_db75730dfc5f | Khanyile Attorneys 624I`
  - `multifirm.624i@email.co.za | mathebula-partners | tenant_5039f2d497cf | Mathebula & Partners`
- portal contacts:
  - `5d4bd3b9-8159-42d0-be9a-74730169d7fc` (Khanyile, ACTIVE)
  - `c2e07e34-484f-46d5-b032-f3b78d976702` (Mathebula, ACTIVE)
- `portal.portal_projects`: one row per org for the two matters above.

### B2 — bare `/login` (fresh context)

- Pre-state: 0 cookies, `[]`, `[]`. Opened `http://localhost:3002/login` with no query string. The page showed "Kazi / Sign in to your client portal / Email address / Send Magic Link" (unbranded). Screenshot: `kc-B2-bare-login.png`.
- Mailpit baseline for `to:multifirm.624i@email.co.za`: **0**.
- Submitted `multifirm.624i@email.co.za` at ~22:36:43Z (corrected on review: the observer's 22:36:45Z was an estimate; both magic-link mails were created at 22:36:43.1Z). There was exactly **one** request-link call:
  - `POST http://localhost:8080/portal/auth/request-link` → **200**
  - request body: **`{"email":"multifirm.624i@email.co.za"}`** (**no orgId**)
  - response body: `{"message":"If an account exists, a link has been sent.","magicLink":"/auth/exchange?token=[REDACTED]&orgId=khanyile-attorneys-624i"}` (the dev echo returns the first link in directory order)
- Success copy, quoted: "**Check your email. If multifirm.624i@email.co.za is registered for a client portal, we've sent a sign-in link. If you work with more than one firm, you'll get one email from each.**" The multi-firm sentence is present. localStorage stayed `[]`. Screenshot: `kc-B2-bare-login-success.png` (token redacted in the DOM).

### B3 — two mails

Mailpit `to:multifirm.624i@email.co.za` went from 0 to **2**. Both mails are branded, open with "Hi Lerato Multifirm" and carry an "Access Portal" anchor (`logs/kc-B3-magic-link-mails.txt`, tokens redacted):

| # | Mailpit id | Created | Subject | Link |
|---|---|---|---|---|
| 1 | **`S5khrDgFvRcHzhezSnGGnt`** | 22:36:43.125Z | Your portal access link from Khanyile Attorneys 624I | `http://localhost:3002/auth/exchange?token=[REDACTED]&orgId=khanyile-attorneys-624i` |
| 2 | **`Y7Xvz2bMazmZF6T5SRhJRu`** | 22:36:43.16Z | Your portal access link from Mathebula & Partners | `http://localhost:3002/auth/exchange?token=[REDACTED]&orgId=mathebula-partners` |

### B4 — each link lands in its own firm

**Link 1 (Khanyile)**

- Fresh context (0 cookies, `[]`, `[]`). Loaded the Mailpit render of `S5khrDgFvRcHzhezSnGGnt` and clicked "Access Portal".
- `/portal/auth/exchange`: **exactly one** `POST` → **200**, body `{"token":"[REDACTED]","orgId":"khanyile-attorneys-624i"}`.
- Final URL **`http://localhost:3002/matters`**.
- Header: "Khanyile Attorneys 624I" (no logo uploaded, so the name is shown) and user "Lerato Multifirm".
- `GET /portal/session/context` → `orgName "Khanyile Attorneys 624I"`, `brandColor null`, `logoUrl null`, `tenantProfile legal-za`.
- `GET /portal/matters` → exactly `[{"id":"05a87979-…","name":"Lerato Multifirm — Shareholder agreement (Khanyile)","status":"ACTIVE",…}]`. The page lists only that matter (Active, "Matter type: COMMERCIAL", 0 documents).
- localStorage: `portal_last_org_id = khanyile-attorneys-624i`, `portal_customer = {"id":"1f3967e1-…","name":"Lerato Multifirm","orgId":"khanyile-attorneys-624i"}`.
- Screenshot: `kc-B4-link1-khanyile-matters.png`.

**Link 2 (Mathebula)**

- `browser_close`, then a new page. localStorage from the previous session was still present, so it was cleared, then re-verified 0 cookies / `[]` / `[]`. Loaded the Mailpit render of `Y7Xvz2bMazmZF6T5SRhJRu` and clicked "Access Portal".
- `/portal/auth/exchange`: **exactly one** `POST` → **200**, body `{"token":"[REDACTED]","orgId":"mathebula-partners"}`.
- Final URL **`http://localhost:3002/matters`**.
- Header: "Mathebula & Partners logo" img (LocalStack `tenant_5039f2d497cf/branding/logo.png`) and user "Lerato Multifirm".
- `GET /portal/session/context` → `orgName "Mathebula & Partners"`, `brandColor "#1B3A4B"`.
- `GET /portal/matters` → exactly `[{"id":"a79db65e-…","name":"Lerato Multifirm — Lease review (Mathebula)",…}]`. Only that matter is listed.
- localStorage: `portal_last_org_id = mathebula-partners`, `portal_customer = {"id":"98f91243-…","name":"Lerato Multifirm","orgId":"mathebula-partners"}`.
- Screenshot: `kc-B5-link2-mathebula-matters.png`.

**Isolation:** neither session showed the other firm's matter, customer id, name or branding.

## Data created on KC

| What | Value |
|---|---|
| Disbursement (Mathebula) | `699277c6-9f9e-4d51-9a4c-93812bf43cfb`, "624I court filing fee", Court Fees, R 100,00 zero-rated, Approved, now billed on INV-0004 |
| Fee note (Mathebula) | **INV-0004**, id `0feb4764-f6f4-425d-9fb1-5ca250ab8c88`, Sipho Dlamini, SENT, total R 100,00, mock payment session created at send |
| Second firm | "Khanyile Attorneys 624I", slug `khanyile-attorneys-624i`, schema **`tenant_db75730dfc5f`** (V1–V5), `public.access_requests` row APPROVED for `owner@khanyile-624i.local`. KC org `be3c9226-a3e8-491f-b90e-4a450dcbd27f` was **deleted** (see below)  *(DB footprint removed after review: `logs/kc-D-db-cleanup.txt`.)* |
| Clients | Khanyile `1f3967e1-795c-4590-bbb9-b8dd6ae03d11`; Mathebula `98f91243-4156-4a83-b261-45347de14062`. Both "Lerato Multifirm", `multifirm.624i@email.co.za`, ONBOARDING (Mathebula onboarding checklist instantiated, 0/8) |
| Matters | Khanyile `05a87979-653f-4135-8104-6f34af4d62ab`; Mathebula `a79db65e-c065-4101-b334-d541941b1166` (both Commercial template) |
| Portal contacts | Khanyile `5d4bd3b9-8159-42d0-be9a-74730169d7fc`; Mathebula `c2e07e34-484f-46d5-b032-f3b78d976702` |
| Directory rows | two for `multifirm.624i@email.co.za` (khanyile-attorneys-624i, mathebula-partners); Sipho, Nomvula and Moroka are unchanged |
| Mailpit | OTP `56TrHdNWvwRZGus8ZXWFRL`; invite `Wj9hU6GPhYpU5UViFTKgSR`; fee note `8aKFRbFgpzPHombJZLFKQx`; magic links `dQw5LfUsb3nqLjFPLHLsHi` (Sipho), `S5khrDgFvRcHzhezSnGGnt` and `Y7Xvz2bMazmZF6T5SRhJRu` (multifirm) |

**KC cleanup (R-5)** (`logs/kc-C-keycloak-cleanup.txt`, admin REST as master `admin`, realm `docteams`, same recipe as Session 0 R.1):

- Before:
  - orgs `khanyile-attorneys-624i (be3c9226-…)` and `mathebula-partners (a21f5935-…)`
  - users bob@, carol@, owner@khanyile-624i.local, padmin@, thandi@
- `DELETE /admin/realms/docteams/organizations/be3c9226-a3e8-491f-b90e-4a450dcbd27f` → **204**.
- Owner user lookup afterwards → "already gone". As in Session 0, the org DELETE removed the org-managed member.
- Read-back after:
  - admin REST orgs = `mathebula-partners` only
  - `keycloak.org` = `mathebula-partners`
  - `keycloak.user_entity` (docteams) = `bob@mathebula-test.local, carol@mathebula-test.local, padmin@docteams.local, thandi@mathebula-test.local`
  - The Mathebula KC org and its users were not touched.
- DB left in place (R-5): `org_schema_mapping` still has `khanyile-attorneys-624i → tenant_db75730dfc5f`, and the schema `tenant_db75730dfc5f` exists.
- **Superseded after review (H-1):** the orchestrator then removed the DB footprint with the scoped Session 0 recipe. That covered the schema, the mapping, organizations, subscriptions, access_requests, the `portal_projects` row and the Khanyile directory row. See `logs/kc-D-db-cleanup.txt` and `gate.md` run note 5.

## Restore

- Killed the worktree `next start`: next-server 22471 and its pnpm parent 22433. `lsof :3002` came back empty, so the port was free. No `worktree-epic-624I` processes remain.
- `bash compose/scripts/svc.sh start portal` from the main checkout printed "ready (3s)".
- `svc.sh status` portal row: **`portal 28609 yes yes 3002`**. The listener is PID 28653, cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`. `GET http://localhost:3002/login` → **200**.
- Worktree `git status --short`: only `?? qa_cycle/evidence/phase88-rewalk/624I/`.
  - `portal/next-env.d.ts` was not dirtied.
  - `.next` and `node_modules` are ignored.
  - `logs/` is git-ignored (`.gitignore:74 logs`), so it needs `git add -f`.
- `.playwright-mcp` was not removed. Screenshots were staged in `b2b-strawman/.playwright-mcp/624I/`, because the MCP only writes under the main checkout, and then copied here.
- Backend left running (PID 20632, health UP).

## Notes (not under test — listed, not fixed)

1. **(Resolved after review: see `logs/kc-D-db-cleanup.txt`.) Leftover legal-za tenant vs Session 0 CP-3.** Per R-5, `tenant_db75730dfc5f` (khanyile-attorneys-624i, `vertical_profile legal-za`) and its `org_schema_mapping`, `organizations`, `subscriptions` and `access_requests` rows remain. They are joined by the `portal.portal_contact_directory` row and `portal_projects` row for Khanyile. Session 0's R.2–R.4 only delete Mathebula-keyed rows, and CP-3 expects `tenant_%` = `tenant_56399d06a773` only. The next re-walk's Session 0 must either accept this schema the way it accepts the smoke tenant, or drop it. Orchestrator call.
2. **(Resolved after review: the Khanyile directory row was deleted.) The multifirm address still routes to Khanyile after the KC cleanup.** The directory row and `org_schema_mapping` remain, so a later bare login for `multifirm.624i@email.co.za` would still mail a Khanyile link. Its exchange would still succeed DB-side, because exchange is DB-only. Sipho, Nomvula and Moroka are unaffected: they each still have a single Mathebula directory row.
3. **624D evidence inaccuracy.** 624D step 3 said the branded (`?orgId=`) success copy was the "same text as step 1". On this build the orgId form omits the multi-firm sentence, and that is intentional: `portal/app/login/page.tsx:162` renders it only when `!queryOrgId`, with a comment explaining why. This is an evidence wording error in 624D, not a product defect.
4. The portal header shows the org name as text for Khanyile (no logo) but only the logo img for Mathebula. This is consistent with the branding data and was not investigated.
5. Fee-note pre-generation warned "Client has no tax number" for Sipho. It is a warning, not a blocker, and the fee note sent fine.
6. The firm matter Work ▸ Tasks tab for Sipho's matter showed "No tasks yet", even though Time recording lists entries on the tasks "Letter of demand" and "Initial consultation & case assessment" and Overview says "100% Task completion". This is probably a default view that hides done tasks. Not investigated.
7. `next start` warns "does not work with output: standalone". Every route used here was served correctly (same as 624D).
8. Playwright MCP tooling: the first padmin Approve click was swallowed (Session 0's known incident). `browser_close` cleared it.

## Files

- `kc-A1-firm-inv0004-sent.png`, `kc-A3-feenote-deeplink-bounce.png`, `kc-A4-feenote-login-success.png`, `kc-A5-feenote-landing.png`
- `kc-B2-bare-login.png`, `kc-B2-bare-login-success.png`, `kc-B4-link1-khanyile-matters.png`, `kc-B5-link2-mathebula-matters.png`
- `logs/kc-backend-restart.log` (orchestrator), `logs/kc-A2-feenote-email.txt`, `logs/kc-B1-readback.txt`, `logs/kc-B3-magic-link-mails.txt`, `logs/kc-C-keycloak-cleanup.txt`, `logs/portal-build-tail.log`, `logs/portal-next-start.log`
