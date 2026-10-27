# Slice 625C — AFTER (observed on the e2e stack, PR #1681 head 520f22eda)

Captured 2026-10-27 by the observer. The stack is e2e mock-auth (frontend :3001, backend :8081, mock IDP :8090), tenant `tenant_7d218705360b`.
Only the backend was rebuilt from the PR worktree, without dependencies or data loss:
`SHELL=/bin/bash docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend` (project `docteams-e2e`, container `e2e-backend`,
recreated 13:27:41Z, health UP). DB access was read-only SELECT/EXPLAIN only (EXPLAIN ran inside `BEGIN READ ONLY … ROLLBACK`). New state was created only through the browser UI.
Tokens came from the mock IDP recipe in `../before/README.md` and were never saved.

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Flyway V6 applied, `idx_audit_customer` present | **PASS** | `sql-1-flyway-and-index.txt`, `log-0-backend-startup-flyway.txt` |
| 2 | Acme `GET /api/clients/{id}/activity`: three-branch union, same shape as before, ordering, paging | **PASS** | `api-2-*.json`, `api-2b-*.json`, `api-2c-*.json`, `check-2-acme-shape-paging.txt` |
| 3 | No rows from other clients (Sipho, Acme, Khumalo) | **PASS** | `check-3a/3b/3c-*.txt`, `api-3a/3b/3c-*.json` |
| 4 | Member gets 403, unknown client gets 404 | **PASS** | `api-4-authz-carol-403-unknown-404.txt` |
| 5 | Legal labels (metadata, audit-events, facets, browser) | **PASS** | `api-5a..5d-*.json`, `check-5-labels.txt`, `625C-after-01-*.png`, `625C-after-02-*.png`, `ui-5-firm-audit-trail-snapshot.yml` |
| 6 | New checklist-item event carries `customer_id` and shows first in the client activity | **PASS** | `sql-6-new-checklist-item-row.txt`, `api-6-observer-client-activity.json`, `check-6-*.txt`, `625C-after-03-*.png` |
| 7 | EXPLAIN uses `idx_audit_customer` in a BitmapOr | **PASS (informational)** | `sql-7-explain-client-activity.txt` |

## 1. Flyway
`flyway_schema_history` has `6 | phase88 audit customer index | t` (installed 13:27:53). The backend log shows "Migrating schema … to version 6 … Successfully applied 1 migration".
Running `\d audit_events` lists `"idx_audit_customer" btree ((details ->> 'customer_id'::text))` next to the existing `idx_audit_project`.

## 2. Acme Corp `b6dc030f-bb6f-45c6-844f-a673637c5da9`
- The endpoint returns HTTP 200 with **totalElements 15**. Before the fix the tab showed 4 rows; this matches the expected union of 15.
  - 4 client rows: customer.created 1, customer.lifecycle.transitioned 2, customer.lifecycle.auto_dormancy 1.
  - 11 checklist rows: checklist.instance.created 1, checklist.instance.completed 1, checklist.item.completed 1, checklist.item.skipped 8.
- The page shape matches the legacy `GET /api/audit-events/customer/{id}`: top-level `{content, page}`, page `{number,size,totalElements,totalPages}`, and the same 15 row keys. The legacy endpoint still returns 4, because it only reads the entity branch.
- Labels are legal: "Client created", "Client lifecycle transitioned", "Client lifecycle auto dormancy", "Checklist instance created/completed", "Checklist item completed/skipped".
- Rows come back newest first (occurredAt DESC).
- Paging with size=5: pages 0, 1 and 2 each return 5 rows, page 3 returns 0, and every page reports totalElements 15 and totalPages 3. That gives 15 distinct ids, identical in order to the size=20 list.

## 3. Cross-client isolation
The script (kept outside the repo) fetched every page and checked each row. A row passes only if `details.customer_id` is absent or equals the client, and its entity belongs to the client: either the client row itself, or a checklist instance/item, information request/item or portal contact that the DB shows the client owns. Any other row must name the client in `details.customer_id`. The total was also compared against an independent SQL count of the three-branch union.
- **Sipho Ndlovu mvmzw78f31c9** `1d7cae32…`: 28 rows, SQL union 28, **0 violations**. By branch: 2 entity, 1 child, 25 `details.customer_id`. The 25 are fee notes ("Fee note" ×4, "Fee note partially paid" ×1), trust deposit/payment recorded/approved (7), acceptance created/sent/viewed/accepted/declined/certificate (10) and disbursement.created (3).
- **Acme Corp**: 15 rows, SQL 15, **0 violations** (4 entity, 11 child).
- **Khumalo Property Holdings mvmzw78f31c9** `218397cf…`: 8 rows, SQL 8, **0 violations** (3 entity, 1 child, 4 details: trust ×3, acceptance.created ×1).
- After step 6, Sipho was re-checked: still 28 rows and 0 violations. The new observer row did not leak into Sipho's feed.

## 4. Authorisation
- Carol (`org:member`) → **403** "Insufficient permissions for this operation".
- Alice on a random UUID → **404** "Customer not found". The message wording is legacy, noted only.

## 5. Labels
- `GET /api/audit-events/metadata` returns 33 entries. `invoice.*` is now **"Fee note"**, with "Fee note partially paid" and "Fee note payment exceeded balance". No label contains "Invoice".
- `GET /api/audit-events?size=200` returns 156 rows. The resolved labels include "Matter created", "Matter created from template", "Matter updated", "Matter team member added/removed/role changed", "Client created", "Client linked", "Client lifecycle transitioned", "Client lifecycle auto dormancy", "Fee note" (invoice.created/approved/sent), "Disbursement …" and "Checklist item skipped". **No label contains Invoice, Project or Customer.** The full list is in `check-5-labels.txt`.
- `GET /api/audit-events/facets/entity-types` maps project → "Matter", customer → "Client", invoice → "Fee note", legal_disbursement → "Disbursement" and project_member → "Matter team member". Others are sentence-cased, e.g. "Trust transaction", "Checklist instance item".
- Browser (Settings › Firm › Audit trail): the EVENT column reads "Fee note", "Fee note partially paid", "Matter created from template", "Matter closure closed", "Client linked" and "Client lifecycle auto dormancy" (`625C-after-01-firm-audit-trail-labels-fullpage.png`). Filtering `?eventType=project_member.added` gives 3 rows reading "Matter team member added" (`625C-after-02-…png`).
- Notes, not failures:
  - (a) invoice.created/approved/sent all read just "Fee note", because the `invoice.*` prefix entry carries one label.
  - (b) The exact registry entries "Matter Closure Override Used" and "Security Event" keep Title Case.
  - (c) The ENTITY column still prints raw literals (`project`, `invoice`, `customer`). That is frontend work; the facet Selects are in 625D.
  - (d) The React #418 hydration warning and two RSC-prefetch 404s for trust-transaction links are on the console. #418 was already present before.

## 6. New-row `customer_id` (U5 obligation), created through the UI
1. Clients › New Client: "Observer 625C Test Client" (`31775a3a-1ce2-4de0-8b26-a93202eda787`). Step 2 was skipped and Create Client clicked.
2. Start Onboarding › confirm. This auto-created checklist instance `e5278c50…` with 9 items.
3. Onboarding tab: on "Conflict Check Performed" (required, no document, no dependency), Mark Complete, enter a note, Confirm.
4. The newest `checklist.item.*` row is `885894bd…` (`checklist.item.completed`, 13:31:26Z). Its details include **`"customer_id": "31775a3a-1ce2-4de0-8b26-a93202eda787"`**, alongside itemName/instanceId/notes/completedBy. The item it points to belongs to that client.
5. `GET /api/clients/31775a3a…/activity` returns totalElements 4, and **the first row is `885894bd…` "Checklist item completed"**. The rest follow: Client lifecycle transitioned, Checklist instance created, Client created. Cross-client check: 0 violations.
- Sipho's demo data was not touched.

## 7. EXPLAIN (informational)
The WHERE clause was copied from `AuditEventRepository.CLIENT_ACTIVITY_WHERE` with Acme's id substituted.
- With `enable_seqscan = off` the plan is a **BitmapOr** over `idx_audit_entity` ×6 (one per entity branch, child ids as `= ANY($n)` InitPlans) plus **`idx_audit_customer`** for branch (c).
- With default settings the 156-row table uses a Seq Scan, which is expected at this size.
