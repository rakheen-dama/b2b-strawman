# Slice 624A — observed evidence (PR #1670, head c68529891)

- Observed: 2026-10-24 15:09–15:20 SAST
- Observer: read-only (no code changes, no commits, no pushes; SELECT-only SQL)
- Stack: E2E mock-auth stack (frontend :3001, backend :8081, `e2e-postgres`, `e2e-mailpit` HTTP :8026). Keycloak dev stack untouched.
- Backend image: rebuilt from worktree `/Users/rakheendama/Projects/2026/worktree-epic-624A` at `c68529891b2015f7b9e25db38cae21f135523fe1` with `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh backend` (exit 0, log `rebuild.log`). New image `sha256:b41ea133365…` started 13:16:41Z (previous image `sha256:de68ad77ec39…` was built from worktree-epic-622F). The jar contains `portal/directory/PortalContactDirectory{Service,Entry,Repository,BackfillRunner}.class` and `db/migration/global/V2__portal_contact_directory.sql` (checked with `unzip -l /app/app.jar` inside the container).
- Tenant: `e2e-test-org` → `tenant_7d218705360b` (one tenant on this stack).
- Global Flyway history is in `public.flyway_schema_history` (`FlywayConfig` → `classpath:db/migration/global`; it is the only non-tenant `flyway_schema_history`).

## Verdicts

| # | Check | Verdict |
|---|-------|---------|
| 1 | Before rebuild: global history only V1; directory tables and `amount_paid` absent | PASS |
| 2 | Rebuild from worktree; backend healthy | PASS |
| 3a | Global history has V2 `portal contact directory`, success = t | PASS |
| 3b | `portal.portal_contact_directory` schema (PK `(email_lower, org_id)`, tenant_schema, org_name, updated_at) | PASS |
| 3c | `portal.portal_contact_directory_backfill` schema (PK tenant_schema, backfilled_at, row_count) | PASS |
| 3d | `portal.portal_invoices.amount_paid numeric(14,2) NOT NULL DEFAULT 0` | PASS |
| 3e | Backfill marker for each tenant, row_count = number of distinct ACTIVE emails | PASS |
| 3f | Directory = distinct ACTIVE contact set per tenant (EXCEPT both ways = 0) | PASS |
| 3g | Pre-V2 PAID portal invoice backfilled with amount_paid = total; SENT rows 0 | PASS |
| 3h | Runner logged one INFO summary line; no ERROR lines in the backend log | PASS |
| 4a | New client created through the UI → ACTIVE portal contact → directory row (normalised email, right org_id / tenant_schema / org_name) | PASS |
| 4b | Suspend / archive / anonymize maintenance through the product | DEFERRED (no UI for these actions; covered by the slice's integration tests only, not observed here) |
| 5 | Part payment through the UI → `portal.portal_invoices` status PARTIALLY_PAID, amount_paid = 600.50 | PASS |
| 6a | Real portal auth flow (request-link → Mailpit → exchange) | PASS |
| 6b | `GET /portal/invoices` returns amountPaid + balanceDue, correct values | PASS |
| 6c | `GET /portal/invoices/{id}` returns amountPaid + balanceDue, correct values | PASS |

## Step 1 — before rebuild

```
SELECT installed_rank, version, description, success, installed_on FROM public.flyway_schema_history ORDER BY installed_rank;
 1 | 1 | baseline | t | 2026-10-24 06:07:41.644265
SELECT to_regclass('portal.portal_contact_directory'), to_regclass('portal.portal_contact_directory_backfill');
 (null) | (null)
amount_paid column on portal.portal_invoices: 0 rows
portal.portal_invoices: 80ef4e4a… SENT 4025.00 | ca289b97… PAID 1725.00 | e808fc08… SENT 1725.00
tenant_7d218705360b.portal_contacts: 8 rows, all ACTIVE
```

## Step 2 — rebuild

`e2e-rebuild.sh backend` exit 0. `curl -sf http://localhost:8081/actuator/health` → `{"status":"UP"}`.

## Step 3 — after rebuild (full output in `step3-sql.txt`, `step3-log.txt`)

```
public.flyway_schema_history
 1 | 1 | baseline                 | t | 2026-10-24 06:07:41.644265
 2 | 2 | portal contact directory | t | 2026-10-24 13:16:51.17308

\d portal.portal_contact_directory
 email_lower varchar(255) not null | org_id varchar(255) not null | tenant_schema varchar(63) not null
 org_name varchar(255) not null | updated_at timestamptz not null default now()
 "portal_contact_directory_pkey" PRIMARY KEY, btree (email_lower, org_id)

\d portal.portal_contact_directory_backfill
 tenant_schema varchar(63) not null | backfilled_at timestamptz not null default now() | row_count integer not null
 "portal_contact_directory_backfill_pkey" PRIMARY KEY, btree (tenant_schema)

amount_paid | numeric | 14 | 2 | NO | 0

SELECT tenant_schema, row_count, backfilled_at FROM portal.portal_contact_directory_backfill;
 tenant_7d218705360b | 8 | 2026-10-24 13:16:59.619001+00

SELECT email_lower, org_id, tenant_schema, org_name FROM portal.portal_contact_directory ORDER BY org_id, email_lower;
 8 rows, every row: org_id e2e-test-org | tenant_7d218705360b | E2E Test Organization
 (apex-zhod@, apex.holdings.mvm024uad719@, contact@acme.example.com, khumalo.property.mvm024uad719@,
  moroka-zhod@, quickcollect-zhod@, sipho-zhod@, sipho.ndlovu.mvm024uad719@ — all @example.com except acme)

Cross-check (distinct lower(trim(email)) WHERE status='ACTIVE' vs directory for e2e-test-org):
 active_not_in_dir | 0
 dir_not_active    | 0
tenant status counts: ACTIVE | 8   (no non-ACTIVE contacts exist on this stack, so the
                                     "excludes non-ACTIVE" side of the backfill is not exercised here)

SELECT id, status, total, amount_paid FROM portal.portal_invoices;
 80ef4e4a… | SENT | 4025.00 |    0.00
 e808fc08… | SENT | 1725.00 |    0.00
 ca289b97… | PAID | 1725.00 | 1725.00
```

Backend log (`docker logs e2e-backend | grep -i directory`):
```
13:16:51Z INFO DbMigrate  Migrating schema "public" to version "2 - portal contact directory"
13:16:59Z INFO PortalContactDirectoryBackfillRunner  Portal contact directory backfill: 1 tenants scanned, 0 skipped (already marked), 0 failed, 8 rows upserted
```
`grep -c '"level":"ERROR"'` → 0 (also 0 again at the end of the run).

## Step 4 — live directory maintenance via the UI (`step4-sql.txt`, `624A-01-client-created.png`)

UI: :3001/mock-login → Sign In (Alice) → Clients → New Client → Name "Observer 624A Client", Email `Observer624A+1792847890@Example.com` (mixed case on purpose, to test normalisation) → Next → Create Client → client `cf37f585-c6b3-413f-95c4-01e55c2b9e26`.

```
tenant_7d218705360b.portal_contacts WHERE customer_id='cf37f585…'
 70994606… | Observer624A+1792847890@Example.com | ACTIVE | created 13:18:28.694687+00
portal.portal_contact_directory WHERE email_lower LIKE 'observer624a%'
 observer624a+1792847890@example.com | e2e-test-org | tenant_7d218705360b | E2E Test Organization | updated 13:18:28.705364+00
directory count: 9 (was 8)
```
The row is written about 11 ms after the contact commit. The email is lower-cased.

Suspend, archive and anonymize: the firm app has no UI for these, so they are DEFERRED here and covered only by the slice's integration tests.

## Step 5 — part payment → amount_paid (`step5-sql.txt`, `624A-02-part-payment-form.png`, `624A-03-partially-paid.png`)

UI: /org/e2e-test-org/invoices/e808fc08-3170-45d2-906b-7e08859c667d (INV-0003, Acme Corp, SENT, R 1 725,00; client contact contact@acme.example.com) → Record Payment → Amount 600.50, EFT, ref OBS-624A-PART → Confirm Payment. The page then shows "Partially Paid" and "Paid R 600,50 · Balance due R 1 124,50", and the payment history row reads Completed / Manual / EFT / R 600,50.

```
tenant invoices:        INV-0003 | PARTIALLY_PAID | 1725.00 | 600.50
portal.portal_invoices: INV-0003 | PARTIALLY_PAID | 1725.00 | 600.50 | synced_at 13:19:01.088716
```

## Step 6 — portal API with a real token (`rl.json`, `list.pretty.json`, `detail.pretty.json`)

```
POST :8081/portal/auth/request-link {"email":"contact@acme.example.com","orgId":"e2e-test-org"}
 → 200 {"message":"If an account exists, a link has been sent.","magicLink":"/auth/exchange?token=<redacted>&orgId=e2e-test-org"}
Mailpit (:8026) message gvt3YpCZmGjo6nUrY2S7Cf 13:19:22Z "Your portal access link from E2E Test Organization" → token parsed from the mail body (<redacted>)
POST :8081/portal/auth/exchange {"token":"<redacted>","orgId":"e2e-test-org"}
 → 200 {"token":"<redacted>","clientId":"10af99bc-549b-4fc8-a976-2665e7379a65","clientName":"Acme Corp"}
GET :8081/portal/invoices (Bearer <redacted>) → 200
 INV-0002 | PAID           | total 1725.0 | amountPaid 1725.0 | balanceDue 0.0
 INV-0003 | PARTIALLY_PAID | total 1725.0 | amountPaid 600.5  | balanceDue 1124.5
GET :8081/portal/invoices/e808fc08-3170-45d2-906b-7e08859c667d → 200
 status PARTIALLY_PAID | total 1725.0 | amountPaid 600.5 | balanceDue 1124.5 | currency ZAR
```
balanceDue = total − amountPaid holds for both rows (1725.0 − 600.5 = 1124.5; 1725.0 − 1725.0 = 0.0). The list correctly leaves out Acme's APPROVED INV-0004, which has not been sent.

## Notes (not failures)
- The money fields come back as JSON numbers (`1725.0`, `600.5`), not as fixed-scale strings. This is consistent with the existing `total` field.
- `request-link` returns `magicLink` in the response body on this e2e profile. It is redacted above. The flow still went through Mailpit as the brief asked.
- Screenshots were also left in `/Users/rakheendama/Projects/2026/b2b-strawman/.playwright-mcp/624A-0*.png` (the Playwright MCP write root). Nothing was removed from that directory.
