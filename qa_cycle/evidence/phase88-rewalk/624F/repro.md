# 624F repro — G-B19: client cannot decline a document sent for acceptance (BEFORE state)

Date: 2026-10-24 ~19:10 UTC. Stack: E2E mock-auth (frontend :3001, backend :8081 = 624E build / main db10fda24).
Tenant: `e2e-test-org` → schema `tenant_7d218705360b` (public.org_schema_mapping). Mailpit HTTP API :8026.
No source modified, nothing committed. Tokens redacted below.

## 1. Fresh acceptance requests created through the firm UI (Playwright MCP, Alice owner)

Matter `dbebf31b-e0d0-4672-ab7e-2bf289b6c262` (Khumalo Property Holdings — Erf 1234 Sandton Transfer mvm024uad719)
→ Generate Document → template → Next: Preview → Save to Documents → Send for Acceptance → Recipient
"Khumalo Property Holdings mvm024uad719 (khumalo.property.mvm024uad719@example.com)" → Send.

| | Request A (reproducer, CONSUMED: now VIEWED) | Request B (UNTOUCHED, for post-fix observer) |
|---|---|---|
| acceptance_requests.id | `b24b31b1-b38e-444f-83f0-df2de01c73be` | `b7498a5e-86c7-4f2b-b72a-2aaeb8e99125` |
| template | Engagement Letter — Conveyancing | Engagement Letter — General |
| generated_document_id | `b80d17df-f9d4-4a36-9de9-34b5e31edf16` | `b71c2ccf-a010-4968-9a61-c9e410d92489` |
| file | engagement-letter-conveyancing-khumalo-…-2026-10-24.pdf | engagement-letter-general-khumalo-…-2026-10-24.pdf |
| portal_contact_id | `1dce89e2-395e-4335-a90b-15f345e5a790` (khumalo.property.mvm024uad719@example.com) | same |
| customer_id | `7c97210a-9d7b-4883-a218-f60bf03cff3c` | same |
| sent_by_member_id | `7f8ad470-cf18-49b7-aa34-c5a3939b062e` (Alice Owner) | same |
| sent_at / expires_at | 19:09:34Z / 2026-11-23 | 19:10:04Z / 2026-11-23 |
| Mailpit message | `L5sxi778v9hLsvUPSN6jS9` | `jqnhE2in6RuQKxkYXo2qVP` |
| link | `http://localhost:3002/accept/0rnN_2…` | `http://localhost:3002/accept/Hln6OL…` |

Raw token is stored in plaintext in `acceptance_requests.request_token` (and `portal.portal_acceptance_requests.request_token`), matching the Mailpit link.
Pre-existing seeded request `58d37957-32a4-4b4f-a8f9-9cf99bc1bab2` (offer-to-purchase, SENT, expires 2026-10-31) left alone.

## 2. HTTP — no decline concept

```
GET  /api/portal/acceptance/0rnN_2…            → HTTP 200
{"requestId":"b24b31b1-…","status":"VIEWED","documentTitle":"engagement-letter-conveyancing-…pdf",
 "documentFileName":"…","expiresAt":"2026-11-23T19:09:34.372771Z","orgName":"E2E Test Organization",
 "orgLogo":null,"brandColor":"#1b3a4b","acceptedAt":null,"acceptorName":null}
   (side effect: GET flips SENT→VIEWED; no decline fields in the DTO)

POST /api/portal/acceptance/0rnN_2…/decline  {"reason":"repro"}   → HTTP 404
{"detail":"No static resource api/portal/acceptance/<token>/decline.","instance":"/api/portal/acceptance/<token>/decline","status":404,"title":"Not Found"}

PUT  …/decline → HTTP 404 (same body)
POST …/accept {} → HTTP 400 (control: real route exists, validation rejects empty body)
```
PortalAcceptanceController exposes only GET /{token}, GET /{token}/pdf, POST /{token}/accept.

## 3. DB

Tenant `\d tenant_7d218705360b.acceptance_requests`: status `varchar(20) default 'PENDING'`; columns end at
`revoked_at, acceptor_*, certificate_*, sent_by_member_id, revoked_by_member_id, reminder_count, last_reminded_at,
created_at, updated_at` — **no `declined_at`, no `decline_reason`**. Partial indexes keyed on status IN (PENDING,SENT,VIEWED)
(`idx_acceptance_requests_active_unique`, `idx_acceptance_requests_expiry`).

```
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='chk_acceptance_requests_status';
(0 rows)
SELECT … FROM pg_constraint WHERE conname ILIKE '%acceptance%';
 portal.portal_acceptance_requests | portal_acceptance_requests_pkey | PRIMARY KEY (id)
 tenant_7d218705360b.acceptance_requests | acceptance_requests_pkey | PRIMARY KEY (id)
```
**SURPRISE: there is no status CHECK constraint at all** (tenant V1 baseline has none for acceptance_requests). Proof — rolled-back txn:
```
BEGIN; UPDATE tenant_7d218705360b.acceptance_requests SET status='DECLINED' WHERE id='b24b31b1-…' RETURNING id,status; ROLLBACK;
 b24b31b1-… | DECLINED / UPDATE 1 / ROLLBACK
```
So the DB layer would not block DECLINED; the gap is entirely in enum / API / projection / UI.

Global `portal.portal_acceptance_requests`: status `varchar(20) NOT NULL`, no CHECK; no declined_at / decline_reason columns.
Read-model status after the GET: A = `SENT` (tenant row = VIEWED) — VIEWED is not projected to the read model.

## 4. Code (worktree-epic-624F @ db10fda24)

- `grep -rni declin` over `backend/…/acceptance/` and `portal/app/accept/` → **no matches**.
- `AcceptanceStatus` enum: PENDING, SENT, VIEWED, ACCEPTED, EXPIRED, REVOKED.
- Portal accept page `portal/app/accept/[token]/acceptance-page.tsx`: page states ACCEPTED/EXPIRED/REVOKED; only buttons are the Accept submit buttons — no Decline. (Portal app not running on e2e stack; grep only.)

Status-keyed places a DECLINED value must reach (silent-drop traps):
- `AcceptanceService.getByCustomer` (~l.590) and the DTO facade (~l.645) use an explicit status allowlist → a DECLINED row would vanish from the firm customer list.
- `AcceptanceRequest` terminal set (l.25: ACCEPTED, EXPIRED, REVOKED).
- `PortalEventHandler` projects only ACCEPTED/REVOKED/EXPIRED (l.724/742/760); `PortalReadModelRepository.findPendingAcceptancesByContactId` = status IN ('SENT','VIEWED') → without a projection a declined request stays "pending" in the portal.
- `AllAcceptanceRequestsFinalGate` (closure gate 9) ACTIVE = PENDING/SENT/VIEWED (DECLINED would correctly count as final).
- `event/DomainEvent.java` lists Acceptance* events (sealed permits likely) — a new Declined event must be added there; `NotificationEventHandler.onAcceptanceRequestAccepted` is the firm-notification analogue.
- FE status maps/unions: `frontend/components/acceptance/AcceptanceStatusBadge.tsx` (Record<status,…>), `AcceptanceDetailPanel.tsx`, `frontend/lib/actions/acceptance-actions.ts`, `frontend/lib/api/portal-acceptance.ts`, `frontend/app/accept/[token]/page.tsx` (a second, firm-app copy of the accept page), `portal/lib/types.ts`, `portal/app/accept/[token]/acceptance-page.tsx`.
