# 624F observer — task 624.16 Observed PASS (acceptance decline)

PR #1675, head `cdb1dbb0a`, branch `phase88/07F-acceptance-decline-v5`, worktree `/Users/rakheendama/Projects/2026/worktree-epic-624F` (clean tree).
Stack: E2E mock-auth (:3001 / :8081 / Mailpit :8026), tenant `tenant_7d218705360b`. Tokens are shown as `<first6>…`.
Observed 2026-10-24 19:39–19:45 UTC.

## 1. Rebuild
`SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh backend frontend` from the worktree (nohup + marker). Exit 0 (`rebuild.done`=0, `rebuild.log`).
Containers recreated 21:39:26/27 SAST, both healthy. Data intact (rebuild only, no e2e-down).

`flyway_schema_history` (sql-flyway.txt):
```
 5 | phase88 acceptance decline | t | 2026-10-24 19:39:53.069957
```
`\d acceptance_requests`: `declined_at timestamptz`, `decline_reason text`, `chk_acceptance_requests_status CHECK (status IN (PENDING,SENT,VIEWED,ACCEPTED,DECLINED,EXPIRED,REVOKED))`; partial unique index still on (PENDING,SENT,VIEWED).

## 3a. Before (before.txt)
- B `b7498a5e-…` status SENT, viewed_at/declined_at/decline_reason null
- notifications count 29; `acceptance.declined` audits 0
- portal read model B = SENT (A `b24b31b1` = SENT in the read model although tenant row is VIEWED — the read model does not track VIEWED; pre-existing)
- Mailpit total 27 (latest `jqnhE2in6RuQKxkYXo2qVP` — B's original send)

## 3b. Decline B — `POST /api/portal/acceptance/Hln6OL…/decline {"reason":"Price too high — 624F observer"}`
```
HTTP 200 {"status":"DECLINED","declinedAt":"2026-10-24T19:40:28.253877965Z"}
```
(Declined straight from SENT, no GET first.)

## 3c. Second decline
```
HTTP 409 {"detail":"This request can no longer be declined","status":409,"title":"Invalid status transition", ...}
```

## 3d. Accept after decline — `POST …/accept {"name":"Thandi Khumalo"}` (AcceptanceSubmission = `@NotBlank @Size(2..255) name`)
```
HTTP 400 {"detail":"Cannot mark as accepted acceptance request in status DECLINED","title":"Invalid acceptance request state"}
```
Row stays DECLINED, accepted_at/acceptor_name null. `GET …/Hln6OL…` → 200 `status":"DECLINED"`, `"declinedAt":"2026-10-24T19:40:28.253878Z"`, no side effect.

## 3e. After (after.txt)
Tenant row:
```
b7498a5e-… | DECLINED | declined_at 2026-10-24 19:40:28.253878+00 | decline_reason "Price too high — 624F observer"
```
Audit:
```
acceptance.declined | acceptance_request | b7498a5e-… | PORTAL_CONTACT | actor 1dce89e2-… | PORTAL |
{"actor_name":"System","ip_address":"172.217.76.95","project_id":"dbebf31b-e0d0-4672-ab7e-2bf289b6c262",
 "customer_id":"7c97210a-9d7b-4883-a218-f60bf03cff3c","decline_reason":"Price too high — 624F observer"}
```
Notification (count 29 → 30):
```
recipient 7f8ad470-… (Alice) | ACCEPTANCE_DECLINED |
"Khumalo Property Holdings mvm024uad719 declined Engagement Letter — General: Price too high — 624F observer" |
body "Price too high — 624F observer" | ACCEPTANCE_REQUEST b7498a5e-… | reference_project_id dbebf31b-…
```
Portal read model B → DECLINED (first poll, ~7 s after the call).
Mailpit total still 27 → no client email for the decline.

## 3f. Validation + no-reason decline on A (token 0rnN_2…)
- 2001-char reason → `HTTP 400 "Validation failed" fieldErrors [{field:reason, message:"size must be between 0 and 2000"}]`; A stays VIEWED.
- No body at all → `HTTP 400 "Malformed request body"`; A stays VIEWED (body is required, as the controller javadoc says).
- `{}` → `HTTP 200 {"status":"DECLINED","declinedAt":"2026-10-24T19:40:50.605087503Z"}`; decline_reason NULL; audit `decline_reason:""`, `project_id` dbebf31b-…; notification title "Khumalo Property Holdings mvm024uad719 declined Engagement Letter — Conveyancing" (no colon), body NULL; portal read model DECLINED; Mailpit still 27. (after-A.txt)

## 3g. Re-send after decline (browser, Alice, :3001)
Matter dbebf31b → Work · Generated documents → "Engagement Letter — General" → Send for Acceptance → recipient Khumalo contact → Send.
DB (3g.txt):
```
b7498a5e-… | DECLINED | (unchanged)
1f362e03-f9c7-4848-9294-f8b9a72907a3 | SENT | sent_at 2026-10-24 19:44:14.585621+00
```
Portal read model has the new row SENT. Mailpit total 27 → 28, new message `idkhfohhR3UWBpbaGLMHec` (19:44:14Z) to khumalo.property.mvm024uad719@example.com, subject "…Please review engagement letter for acceptance: engagement-letter-general-…pdf". Row badge now "Awaiting Acceptance".

## 4. Firm UI (Playwright MCP, :3001)
- `624F-01-matter-generated-docs-declined.png` — matter Generated documents: General + Conveyancing rows show "Declined" badge; expanded detail panel renders (badge "Declined", timeline Sent/Viewed/Accepted, Expires). No crash, 0 console errors/warnings on the page.
- `624F-02-client-generated-docs.png` — client page Generated documents tab: "No documents generated yet" (list is CUSTOMER-scoped; these docs are PROJECT-scoped). No crash.
- `624F-03-notification-bell-declined.png` — bell dropdown lists both decline notifications (with and without reason). Clicking the General one navigates to `/org/e2e-test-org/matters/dbebf31b-…` (deep-link works).
- `624F-04-…png` / `624F-04b-notification-prefs-full.png` — Settings → Connections → Notifications: "Acceptance Declined" row (Collaboration), in-app switch checked.
- `624F-05-firm-accept-page-declined-token.png` — see observation O1.
- `624F-06-after-resend-awaiting.png` — after re-send.
Raw snapshots and console logs (redacted): `/tmp/624F-observer/playwright/`. Only my files were removed from `.playwright-mcp/` (29 files); the reproducer's `console-…T19-09-41-124Z.log` was copied, not deleted.

## Observations (non-blocking for 624F)
- O1 Legacy firm-frontend page `http://localhost:3001/accept/<token>` (`frontend/app/accept/[token]/page.tsx:56-72`) has no DECLINED branch: a declined token falls through to "SENT or VIEWED" and shows the "Full Legal Name / I Accept This Document" form. Submitting would be rejected by the backend (3d: 400). Emails link to the portal (`http://localhost:3002/accept/<token>`, from the Mailpit HTML), and the portal page (`portal/app/accept/[token]/acceptance-page.tsx:70-93`) routes unknown statuses to ERROR ("Unable to process this acceptance request…"), so the client-facing path fails safe until 624H adds the decline UI. 624F did widen the type in `frontend/lib/api/portal-acceptance.ts` but left this page alone.
- O2 `GET …/pdf` for the declined token returned 500. Root cause from the backend log: `S3StorageAdapter Download failed … NoSuchKeyException` — LocalStack restarted about an hour earlier and the object is gone. Not caused by the decline. Separately, `streamPdf` (AcceptanceService.java:941) does not check status, so a DECLINED/REVOKED/EXPIRED token still streams the PDF. That behaviour predates 624F.
- O3 The firm detail panel's timeline for a declined request shows Sent / Viewed / Accepted with no "Declined" step, date or reason. The firm sees only the badge, and the reason only in the notification. This is probably 624G's scope ("Declined badge (FE)").
- O4 Audit `details.actor_name` = "System" for a PORTAL_CONTACT actor, and `ip_address` 172.217.76.95 (Docker Desktop NAT for host curl). Both match the existing `acceptance.viewed` rows, so they predate this slice.

## Final stack state
- e2e-backend + e2e-frontend built from worktree `cdb1dbb0a` (624F). Postgres/mock-idp/mailpit unchanged; LocalStack has lost S3 objects (O2).
- Tenant V5 applied to `tenant_7d218705360b` at 2026-10-24 19:39:53 UTC.
- Acceptance requests now: B `b7498a5e` DECLINED (reason), A `b24b31b1` DECLINED (no reason), `1f362e03` SENT (re-send of B's document), `58d37957` SENT (Offer to Purchase, untouched).
- Alice has two new unread ACCEPTANCE_DECLINED notifications (General one now read via click). Mailpit total 28.
