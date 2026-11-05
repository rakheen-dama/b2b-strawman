# 634B — BEFORE (pre-fix) observation

Observed 2026-11-05 ~10:40 UTC by QA observer agent (Playwright MCP, Chromium). No source modified, no rebuild/reseed, read-only SQL only.

## Stack

| Item | Value |
|------|-------|
| Stack | E2E mock-auth (frontend :3001, backend :8081, postgres :5433) |
| `docker inspect e2e-backend --format '{{.Created}}'` | `2026-11-05T06:44:38.58305163Z` |
| `docker inspect e2e-frontend --format '{{.Created}}'` | `2026-11-05T06:57:22.410978928Z` |
| Main checkout HEAD at observation | `7b7509f87` (main, no 634B code) |
| User | Alice Owner (mock-login default, org role Owner) |
| Org slug | `e2e-test-org` |
| Tenant schema | `tenant_7d218705360b` |

## Matter chosen

**Website Redesign** — `5be2a454-fdb3-4e98-a363-6d91390d8f46` (client Acme Corp, ACTIVE).
URL: `http://localhost:3001/org/e2e-test-org/matters/5be2a454-fdb3-4e98-a363-6d91390d8f46`

Why: it is the **only** project in the tenant (no Moroka matter on this seed). It has all three ingredients: fee notes with lines (DRAFT/SENT/PARTIALLY_PAID), a matter-tagged trust deposit, Alice (org Owner) as the matter lead, and portal-contact audit rows.

## DB facts (read-only SELECTs, schema `tenant_7d218705360b`)

`select id, name, status from projects` → 1 row: `5be2a454-… | Website Redesign | ACTIVE`

`invoice_lines` grouped by project + invoice status:

| project | invoice status | lines | sum(line amount) |
|---|---|---|---|
| Website Redesign | DRAFT | 1 | 3000.00 |
| Website Redesign | PARTIALLY_PAID | 1 | 2000.00 |
| Website Redesign | SENT | 1 | 7500.00 |

`invoices` (all customer Acme Corp, ZAR, 15% VAT):

| number | status | subtotal | tax | total | amount_paid | outstanding (total − paid) |
|---|---|---|---|---|---|---|
| (draft) | DRAFT | 3000.00 | 450.00 | 3450.00 | 0.00 | n/a (draft, not outstanding) |
| INV-0001 | SENT | 7500.00 | 1125.00 | 8625.00 | 0.00 | 8625.00 |
| INV-0002 | PARTIALLY_PAID | 2000.00 | 300.00 | 2300.00 | 500.00 | 1800.00 |

`payment_events`: 1 row, INV-0002, 500.00, COMPLETED.

**Expected outstanding fee notes for the matter (SENT + PARTIALLY_PAID, incl. VAT, net of payments) = R10 425,00** (8625.00 + 1800.00).
Alternative bases, if the fix computes differently: gross totals without payments = R10 925,00; ex-VAT line sums = R9 500,00.

`trust_transactions` grouped by project/customer/type/status:

| project_id | customer | type | status | count | sum |
|---|---|---|---|---|---|
| 5be2a454-… | 906d9e90-… (Acme Corp) | DEPOSIT | RECORDED | 1 | 1500.00 |

`client_ledger_cards`: 1 row, Acme Corp, balance 1500.00 (total_deposits 1500.00).

**Expected matter-tagged trust balance = R1 500,00** (one DEPOSIT, status RECORDED — note: if the fix only counts APPROVED transactions the tile would read R0,00; RECORDED is the non-approval-workflow final state here).

`project_members` joined to `members`/`org_roles`:

| member | email | project_role | org role |
|---|---|---|---|
| Alice Owner | alice@e2e-test.local | lead | Owner |

`audit_events` — portal-origin rows (`actor_type='PORTAL_CONTACT'`, `source='PORTAL'`), all with `details->>'project_id'` = this matter:

| event_type | entity_type | occurred_at |
|---|---|---|
| portal.document.upload_initiated | document | 2026-11-05 05:36:31 |
| portal.request_item.submitted | request_item | 2026-11-05 05:36:31 |
| portal.document.downloaded | document | 2026-11-05 05:36:39 |

Matter-scoped audit events total: 18 (of 70 in tenant). `acceptance_requests`: **0 rows** — no document acceptance exists anywhere in the tenant.

## Observations per gap

### G88-M13 — KPI tiles (Overview tab)

- 1440x900: `before-1440-01-overview-kpi-tiles.png`
- 1024x768: `before-1024-01-overview-kpi-tiles.png`

Three tiles render: `0.0h Hours logged`, `0% Task completion`, **`-- Outstanding fee notes`**. The fee-note tile shows `--` despite R10 425,00 outstanding in the DB. **There is no trust tile at all** (expected R1 500,00). Same at both widths. → Gap reproduced.

### G88-M2 — Team badge (Details · Team tab, `?tab=members`)

- 1440x900: `before-1440-02-team-owner-badge.png`
- 1024x768: `before-1024-02-team-owner-badge.png`

Members table (1 row): Alice Owner, alice@e2e-test.local, Role badge **"Owner"** (amber), Added 5 Nov 2026. DB `project_role = lead`, so the badge reflects her org role, not her matter role "Lead". → Gap reproduced.

### G-B8 — "Client portal" chip (Activity tab, `?tab=activity`)

- 1440x900: `before-1440-03-activity-no-portal-chip.png`

18 activity rows rendered (matches 18 matter-scoped audit events). Three portal-origin rows are present, attributed to "Acme Corp" with an "AC" initials avatar:
- `Acme Corp downloaded document "statement-of-account-website-redesign-2026-11-05.pdf"`
- `Acme Corp submitted "Certified ID copy 44twh" for REQ-0002`
- `Acme Corp started uploading document "id-copy.pdf"`

No row carries a "Client portal" chip. `browser_evaluate` on the tabpanel innerText: `/client portal/i` → false; occurrences of "portal" → 0. → Gap reproduced (for portal-origin rows generally).

**Portal-origin acceptance row: NONE exists.** There are 3 portal-origin rows (download / upload-initiated / request-item submitted) but no document-acceptance event and `acceptance_requests` is empty. If the after-observation specifically needs an acceptance row, one must be created via the portal flow.

## Blockers / notes for the after-observation

1. **No portal container in the E2E stack** (`docker ps -a | grep portal` → none). Port 3002 is served by a host `next dev --port 3002` process (PID 86801, started 07:46, cwd `b2b-strawman/portal`, i.e. main checkout) — that belongs to the Keycloak dev stack; `portal/lib/api-client.ts` defaults `NEXT_PUBLIC_PORTAL_API_URL` to `http://localhost:8080` (KC backend, also running), not the E2E backend :8081. Creating an acceptance row on *this* E2E tenant via the browser would need a portal pointed at :8081, or the after-check can rely on the 3 existing portal-origin rows (which should all gain the chip if the fix keys on `actor_type=PORTAL_CONTACT`/`source=PORTAL`).
2. Creating an acceptance also requires an acceptance request sent from the firm side first (none exist).
3. Trust tile expected value depends on whether RECORDED deposits are counted (only RECORDED exists here).
4. Playwright MCP can only write inside `b2b-strawman/`; screenshots were saved under `.playwright-mcp/634B-before/` and moved here with `mv`.
5. `red-test.md` in this directory pre-existed (not written by this observer).
