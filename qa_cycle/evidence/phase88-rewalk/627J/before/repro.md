# 627J before — portal comment attachment chips (reproduce on main)

**Verdict: REPRODUCED.** The portal comments API returns `attachments[]` on the SHARED comment. The portal matter page renders that comment with **no chip, no list, no link and no download button** for its attachment. The INTERNAL comment and its document are correctly absent from both the API and the page.

- Code under test: `de97fb19d` (main). `portal/` in `$WT` is byte-identical to the main checkout (`diff -rq`, ignoring node_modules/.next).
- Stack: the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, Mailpit UI :8026), rebuilt fresh from f7206efe7, plus the portal on :3002 (see "Portal server note").
- Static cause, for the record: `portal/components/comment-section.tsx` renders only `authorName`, `createdAt` and `content`. `PortalComment` in `portal/lib/types.ts` has no `attachments` field.
- No product code was changed. No SQL writes were made; all SQL was read-only.

## Steps and observations

| # | Action | Result | Evidence |
|---|--------|--------|----------|
| 1 | On :3001 as Alice (`/mock-login` → Sign In), open matter **Website Redesign** (client Acme Corp). Tasks tab → New Task "627J portal chip repro task". Open the task → Comments tab. | Task `550761eb-a356-49ed-a813-45129a582159` created | — |
| 1a | Comment text "627J before: SHARED comment with chip 627J-shared-chip.pdf". Visibility **Customer visible**. Attach `627J-shared-chip.pdf` via the dropzone. Post. | The firm UI shows the comment with the "Customer visible" badge and a "Download 627J-shared-chip.pdf" chip | `01-firm-task-comments.png` |
| 1b | Comment text "627J before: INTERNAL comment with chip 627J-internal-chip.pdf". Visibility **Internal only**. Attach `627J-internal-chip.pdf`. Post. | Posted | `01b-db.txt` |
| 1c | DB (read-only) | Comment `312f7a8b-…` is TASK/SHARED → doc `9b24a239-…` (UPLOADED, SHARED, source COMMENT). Comment `734bac66-…` is TASK/INTERNAL → doc `c72728db-…` (UPLOADED, INTERNAL, source COMMENT). The portal read-model has `portal_comments` with only `312f7a8b`, and `portal_documents` with only `9b24a239` (scope PROJECT). | `01b-db.txt` |
| 2 | `POST /portal/auth/request-link` → `POST /portal/auth/exchange` (contact@acme.example.com, orgId e2e-test-org) | 200 / 200, clientId `755901da-…` Acme Corp | `02a-portal-exchange.summary.txt` |
| 2a | `GET /portal/matters/ad89d02a-…/comments` with the portal JWT | **200**, 1 comment (`312f7a8b`). `attachments: [{documentId 9b24a239-…, fileName 627J-shared-chip.pdf, application/pdf, 41}]`. INTERNAL `734bac66` is absent. **API matches the 627H claim.** | `02b-portal-list-comments.*` |
| 2b | `GET /portal/documents/{id}/presign-download` | shared doc `9b24a239` → **200**. Internal doc `c72728db` → **404** | `02c-portal-presign-download.txt` |
| 3 | Portal UI as the contact: `:3002/login` → email → "Send Magic Link" → link from Mailpit → `/auth/exchange?token=<redacted>&orgId=e2e-test-org` → `/matters/ad89d02a-4990-4707-815e-99dcde0b63f9` | The page's own `GET …/comments` returned 200 **with** the attachment (`03d-…json`). The Comments section shows the author, the time and the body text only. Inside the Comments section: 0 lists, 0 links, 0 download buttons (only "Post Comment"), and 0 exact-text `627J-shared-chip.pdf` elements. The filename appears only inside the comment body text, because I typed it there. **No chip — BUG.** The INTERNAL comment is not rendered (correct). | `03a-portal-comments-section.png`, `03b-portal-matter-fullpage.png`, `03c-portal-matter.snapshot.yml` (lines 72–84 are the Comments section) |

Note: `627J-shared-chip.pdf` **does** appear on the same portal page, but in the separate **Documents** table, with its own "Download 627J-shared-chip.pdf" button. That is the matter document list (a SHARED doc in the portal read-model), not a comment chip. An after-fix observer must scope the chip assertion to the Comments section: heading "Comments" (h2) → parent element.

## Portal server note (surprise)

:3002 was already held by a long-running `next dev --port 3002` from the **main checkout** (`$HOME/Projects/2026/b2b-strawman/portal`, up about 3.5 days, not started by this agent, so it was left alone). That server calls `http://localhost:8080`, the KC dev backend, because `NEXT_PUBLIC_PORTAL_API_URL` is unset there. Because of that, my own `$WT/portal` dev server could not bind :3002 and exited immediately. The e2e backend's CORS allows only `:3002` as the portal origin (`application-e2e.yml`), so another port would not work either.

The workaround: drive the existing :3002 server, whose portal code is identical to main, with a Playwright context route that rewrites `http://localhost:8080/*` to `http://localhost:8081/*`:

```js
await page.context().route(/^http:\/\/localhost:8080\//, r =>
  r.continue({ url: r.request().url().replace('http://localhost:8080/', 'http://localhost:8081/') }));
```

The browser origin stays `http://localhost:3002`, so e2e CORS passes. Network log: every portal API call went to :8081 with 200. The after-fix observer must run the **$WT** portal with the fix, so it either needs that main-checkout process stopped by its owner, or a `$WT` server on :3002 started with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081` (then no route rewrite is needed).

## Reusable recipe for the after-fix observer

- Tenant schema: `tenant_7d218705360b`. Org slug: `e2e-test-org`.
- Matter **Website Redesign** `ad89d02a-4990-4707-815e-99dcde0b63f9`. Client Acme Corp `755901da-629d-442d-80df-4b20bcbfd7d3`. Portal contact `contact@acme.example.com` (ACTIVE, `eb9eeb16-1b6a-4dc0-b8d1-15a6568522d1`).
- Task "627J portal chip repro task" `550761eb-a356-49ed-a813-45129a582159`. Firm URL: `:3001/org/e2e-test-org/matters/ad89d02a-4990-4707-815e-99dcde0b63f9?tab=tasks&taskId=550761eb-a356-49ed-a813-45129a582159` → Comments tab.
- SHARED comment `312f7a8b-0453-41e5-b0a3-c88db701fbbf`, chip doc `9b24a239-a7c4-4f92-b33b-925f6f243978` `627J-shared-chip.pdf` (41 B, application/pdf).
- INTERNAL comment `734bac66-1c95-4d49-9dc4-63f429286b3b`, doc `c72728db-ca26-4c91-b9e3-3fcdb96d6bcc` `627J-internal-chip.pdf` (43 B). This comment must NOT appear on the portal, and neither should its chip.
- Portal sign-in (UI): `http://localhost:3002/login` → email `contact@acme.example.com` → "Send Magic Link". Then either click the "Dev mode — click to sign in" link on the page, or take the link from Mailpit: `curl -s 'http://localhost:8026/api/v1/messages?limit=3'` → newest "Your portal access link from E2E Test Organization" to the contact → `curl -s http://localhost:8026/api/v1/message/<ID> | jq -r .Text` → `http://localhost:3002/auth/exchange?token=…&orgId=e2e-test-org`. The token is single-use; request a fresh one each time.
- Portal sign-in (API): `POST :8081/portal/auth/request-link {"email":"contact@acme.example.com","orgId":"e2e-test-org"}`. The e2e response includes `magicLink`. Take its `token` and call `POST :8081/portal/auth/exchange {"token":…,"orgId":"e2e-test-org"}` → `.token` (JWT). Use it as `Authorization: Bearer <jwt>`.
- Portal comments view: `http://localhost:3002/matters/ad89d02a-4990-4707-815e-99dcde0b63f9` → "Comments" h2 section, at the bottom of the page.
- API: `GET :8081/portal/matters/ad89d02a-4990-4707-815e-99dcde0b63f9/comments`. Chip download: `GET :8081/portal/documents/9b24a239-…/presign-download` → 200. Internal doc `c72728db-…` → 404.
- Expected after fix: inside the Comments section, comment `312f7a8b` shows a chip for `627J-shared-chip.pdf` (41 B) that downloads via presign. There is no chip or entry for `627J-internal-chip.pdf`, and no INTERNAL comment.
