# 627D BEFORE — Profitability CSV endpoint (task 627.9)

Recorded 2026-10-28 against the running e2e mock-auth stack (backend :8081, frontend :3001, mock IDP :8090,
`e2e-postgres`), containers created 2026-10-28T08:16Z, repo `main` @ `02fc220ff`. Read-only: no writes, no restarts.
Tenant schema `tenant_7d218705360b` (org slug `e2e-test-org`, from `public.org_schema_mapping`).

Tokens were minted from the mock IDP:

```bash
curl -s -X POST localhost:8090/token -H 'content-type: application/json' \
  -d '{"userId":"user_e2e_<alice|bob|carol>","orgSlug":"e2e-test-org"}'   # -> .access_token
```

## 1. Endpoint absent: REPRODUCED

```bash
curl -s -w "%{http_code}" -H "Authorization: Bearer $ALICE" \
  "localhost:8081/api/reports/profitability/export/csv?from=2020-01-01&to=2030-12-31"
curl -s -w "%{http_code}" -H "Authorization: Bearer $ALICE" \
  "localhost:8081/api/reports/profitability/export/csv?from=2026-10-01&to=2026-10-28"
```

Both return **404 `application/problem+json`**:
`{"detail":"No static resource api/reports/profitability/export/csv.","status":404,"title":"Not Found"}`
(`01-csv-alice-wide.body`, `01-csv-alice-default.body`). Bob and Carol get the same 404 (`03-csv-*.body`).
No handler is mapped; the request falls through to static-resource handling.

## 2. Oracle data (rows the CSV must reproduce)

Existing endpoint: `GET /api/reports/profitability?from&to[&customerId]` (`ReportController.java:61-71`,
`@RequiresCapability("FINANCIAL_VISIBILITY")`) → `OrgProfitabilityResponse { matters: List<MatterProfitabilitySummary> }`.
Row fields, in order: `matterId, matterName, clientName, currency, billableHours, billableValue, costValue, margin, marginPercent`.
The page also calls `GET /api/reports/utilization?from&to` for the Team Utilization card (not part of the CSV scope).

Page default range: `from` = first of the current month, `to` = today (`page.tsx:11-18`) → **2026-10-01 .. 2026-10-28**.

| File | Call | Status |
|---|---|---|
| `02-profitability-alice-default.json` | `?from=2026-10-01&to=2026-10-28` | 200 |
| `02-profitability-alice-wide.json` | `?from=2020-01-01&to=2030-12-31` | 200 |
| `02-profitability-alice-noparams.json` | no params | 200 |
| `02-utilization-alice-{default,wide}.json` | utilization, same ranges | 200 |

All three profitability responses are identical: one row.

```json
{"matters":[{"matterId":"dbbc856f-a71b-4f1b-b3d0-5534a09564ba","matterName":"Ndlovu v RAF — Depth II mvrufhuu82bf",
"clientName":"Sipho Ndlovu mvrufhuu82bf","currency":"ZAR","billableHours":2.0000000000000000,
"billableValue":5000.0000000000000000,"costValue":null,"margin":null,"marginPercent":null}]}
```

The raw JSON numbers have **scale 16** (`2.0000000000000000`), because native SQL `/ 60.0` arithmetic is passed through
unrounded. A CSV that writes `BigDecimal.toString()` verbatim will emit `5000.0000000000000000`, so the exporter has to
choose a scale. `costValue`/`margin`/`marginPercent` are JSON `null` when no time entry on the matter has a
`cost_rate_snapshot`. The CSV must decide how to represent null: empty cell, not `0`.
Sort order is margin DESC, nulls last (`ReportService.java:281-283`).

## 3. Capability / role matrix

```bash
curl -s -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "localhost:8081/api/reports/profitability?from=2020-01-01&to=2030-12-31"
```

| User | Role (`/api/me/capabilities`) | FINANCIAL_VISIBILITY | `/api/reports/profitability` | `/export/csv` |
|---|---|---|---|---|
| Alice | Owner (isOwner) | yes | **200** | 404 |
| Bob | Admin (isAdmin) | yes | **200** | 404 |
| Carol | Member | **no** | **403** `{"detail":"Insufficient permissions for this operation","title":"Access denied"}` | 404 |

(`03-role-matrix.txt`, `03-profitability-*.json`, `03-capabilities-*.json`)

DB (`03-db-roles.txt`, read-only SELECT on `org_roles` / `org_role_capabilities` / `member_capability_overrides`):
Owner (22 caps) and Admin (20) hold `FINANCIAL_VISIBILITY`, Member (5) does not, and there are no member overrides.
Carol is therefore the "403 without the capability" subject the new test or observation needs.

Layering note: the service has a second guard (`ReportService.requireAdminOrOwner`, `:421-429`) that passes
for admin/owner OR `FINANCIAL_VISIBILITY`. It is redundant with the controller annotation but consistent with it.

## 4. UI: no "Export CSV" control (confirmed)

Playwright MCP: `/mock-login` → Sign In (Alice) → `/org/e2e-test-org/reports` → "Profitability — Run report →" →
`/org/e2e-test-org/reports/profitability`. Screenshot: `04-profitability-page-alice.png` (full page).

- Header: "Profitability" with the subtitle and note **"Costs reflect logged expenses only; disbursements are shown as fee-note lines"** (`data-testid=profitability-cost-note`).
- **Team Utilization** card (From/To 2026-10-01 / 2026-10-28): Name | Total Hours | Billable | Non-Billable | Utilization %, with one row: Alice Owner, 2.0h, 2.0h, 0.0h, 100.0%.
- **Matter Profitability** card (own From/To): Matter | Client | Currency | Billable Hours | Revenue | Cost | Margin | Margin %, with one row: Ndlovu v RAF — Depth II mvrufhuu82bf | Sipho Ndlovu mvrufhuu82bf | ZAR | 2.0h | R 5 000,00 | N/A | N/A | N/A.
- **Client Profitability** card (own From/To): (expander) | **Customer** | Currency | Revenue | Cost | Margin | Margin %, with one row: Sipho Ndlovu mvrufhuu82bf | ZAR | R 5 000,00 | N/A | — | —. This card is a client-side aggregation of the same `matters[]` (`customer-profitability-section.tsx:35-73`, key `clientName::currency`, `null` client → "Unassigned").

DOM probe (`04-export-control-probe-alice.json`): 46 interactive elements, **0** matching `/export|csv|download/i`
in text, aria-label or href, and `main` contains no "CSV" text. **No "Export CSV" control: CONFIRMED.**

Page vs JSON: the Matter Profitability row matches `02-profitability-alice-default.json` 1:1 (2.0 → "2.0h",
5000 → "R 5 000,00", null → "N/A"). The page does not filter any rows. The Client card is derived from the same rows, and its
Margin/Margin % show "—" because `costValue` is null. Utilization matches `02-utilization-alice-default.json`.

## 5. Data sufficiency: THIN

Tenant counts: time_entries **1** (1 with billing rate, **0 with cost_rate_snapshot**), projects 11, customers 8,
invoices 1, expenses 0. The only time entry is dated 2026-10-28, 120 min, billable, R2 500/h ZAR, on "Ndlovu v RAF — Depth II".

A CSV over any range therefore has one data row, with an empty cost, margin and margin %. That proves headers, a
row and null handling, but it does **not** exercise a populated cost leg, margin maths, multi-currency or multi-row ordering.
Those need the integration test fixture, or a seeded cost rate plus more time entries in the AFTER walk (via the UI/API, not SQL).

## 6. Surprises and spec discrepancies

1. **`ProfitabilityResponse` does not exist.** The spec (`tasks/phase88-…md:809`, arch §13.3.19) says "the same rows
   `ProfitabilityResponse` returns". The real type is `ReportController.OrgProfitabilityResponse` →
   `MatterProfitabilitySummary` (`ReportController.java:110-122`).
2. **"The two cost legs differ" is not true at the SQL level.** The `project-profitability` report definition
   (`reporting/ProjectProfitabilityReportQuery.java:59-60`) calls the same `ReportRepository.getOrgProjectRevenue` and
   `getOrgProjectCost` as the page. The real differences are in the merge step:
   - The report definition treats missing cost as `0`, so margin = revenue. The page/API keeps `null` and shows N/A.
   - The report definition keys rows by `projectId` only (`:68`), while the page/API keys by `projectId|currency`.
3. **The page's cost note looks inaccurate for this page.** "Costs reflect logged expenses only" contradicts the code: the org profitability cost is
   `time_entries.cost_rate_snapshot × hours` (`ReportRepository.java:436-460`), and no expense table is read. This was
   observed but not acted on; it is outside 627D scope. Flagged for the orchestrator (relates to the Phase 87 §12.14 cost-leg follow-up).
4. **Frontend gating mismatch (relevant to 627E).** The page renders for `FINANCIAL_VISIBILITY` holders (`page.tsx:29-37`),
   but the date-change server actions in `profitability/actions.ts` (`getOrgProfitability`, `getUtilization`) return
   "Forbidden" unless the user is admin or owner. A custom role with only `FINANCIAL_VISIBILITY` would see the initial data
   but fail on a date change. The CSV button should gate on the capability, as the backend does.
5. The Client Profitability header reads **"Customer"**, not "Client" (legacy terminology). This was observed only and is not in 627D scope.
6. The CSV is expected to follow the page's from/to. Each of the three cards has its own independent From/To pickers, so 627E has
   to decide which card's range the Export button uses.
