# 627D AFTER — observer walk (PR #1696 @ c5733fda9)

Recorded 2026-10-28 ~09:13–09:21Z. The e2e backend image `docteams-e2e-backend` (sha256:4d87dcb0…, created 09:12:58Z) was rebuilt from
`worktree-epic-627D` @ c5733fda9 and started with `up -d --no-deps backend`. Postgres, frontend and mock-idp were left as they were.
Health check returned `{"status":"UP"}`. The endpoint returned 404 in BEFORE and returns 200 now, so the running image has the new code.
Tokens came from the mock IDP (`POST :8090/token {"userId":"user_e2e_<x>","orgSlug":"e2e-test-org"}`) and were held only in shell
variables. No token files were written. No SQL writes and no REST writes. The only writes were made through the UI in O5.

| # | Observation | Result |
|---|---|---|
| O1 | Alice, `?from=2020-01-01&to=2030-12-31` | **PASS** |
| O2 | Bob (admin) | **PASS** (200, body byte-identical to Alice's) |
| O3 | Carol (member) | **PASS** (403 `application/problem+json`) |
| O4 | no dates / from-only / to-only → `profitability.csv` | **PASS** |
| O5 | non-null cost leg, data created through the UI | **PASS** |
| O6 | Reports › Profitability page vs CSV | **PASS** |
| O7 | JSON endpoint unchanged | **PASS** |

## O1
`curl -s -D o1-headers.txt -o o1-alice-wide.csv -H "Authorization: Bearer $A" "localhost:8081/api/reports/profitability/export/csv?from=2020-01-01&to=2030-12-31"`
- status 200
- `Content-Type: text/csv;charset=UTF-8`. Spring writes the header without a space after `;`. This is semantically the same as `text/csv; charset=UTF-8`.
- `Content-Disposition: attachment; filename="profitability-2020-01-01-to-2030-12-31.csv"`. Content-Length is 154.
- Bytes (`od -c`): each line ends in `\r\n`, including the last one. There is no BOM (the file starts `4d 61 74`). The em-dash is
  `e2 80 94` (correct UTF-8, `xxd` offset 0x51).
- Body (`␍` = CR):
  ```
  Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %␍
  Ndlovu v RAF — Depth II mvrufhuu82bf,Sipho Ndlovu mvrufhuu82bf,ZAR,2.00,5000.00,,,␍
  ```
- `cell-compare.txt` compares cells with Python `csv` and `Decimal.quantize(0.01, HALF_UP)` against `o1-alice-wide.json`.
  Row count 1/1, MATCH. Null cost, margin and margin % come out as empty cells.

## O2
Bob returns 200 with the same headers. `cmp o1-alice-wide.csv o2-bob-wide.csv` shows the files are identical.

## O3
Carol returns 403 with `Content-Type: application/problem+json`:
`{"detail":"Insufficient permissions for this operation","instance":"/api/reports/profitability/export/csv","status":403,"title":"Access denied"}`.
With `Accept: text/csv` Carol still gets **403**, not 406, and the response is the same problem+json body (`o3-*accept-csv*`).
This was informational, for the 627E handoff.

## O4
- No dates: 200, `filename="profitability.csv"`, 1 row. MATCH against the no-dates JSON (`o4-alice-nodates.*`).
- `?from=2020-01-01` only: 200, `filename="profitability.csv"`, same body.
- `?to=2030-12-31` only: 200, `filename="profitability.csv"`.

## O5 (UI-created cost data)
Playwright on :3001 as Alice (mock-login):
1. Settings › Fees & Trust › Tariffs › Cost Rates › "Add rate for Alice Owner" › Cost Rate, 1234.56 ZAR, Effective From
   2026-10-01 › Create Rate. The table then showed "R 1 234,56 · ZAR · 1 Oct 2026 · Ongoing" (`627D-obs-o5-01/02-*.png`).
2. Logging time on the BEFORE matter was not possible because it is **Closed**. Time was logged on the Active matter
   "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvrufhuu82bf" instead: Tasks › "Respond to Deeds Office notes"
   › Log Time, 1h 20m, 2026-10-28, billable (the dialog showed "1h 20m x R 2 500,00 = R 3 333,33") › Log Time (`627D-obs-o5-03-*.png`).
   1h 20m was chosen deliberately so that hours and money have repeating decimals and exercise the rounding.
3. The CSV was exported again (`o5-alice-wide.csv`; `o5-alice-default.csv` for 2026-10-01..28 is byte-identical apart from the filename header):
   ```
   Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %␍
   Khumalo Property Holdings — Erf 1234 Sandton Transfer mvrufhuu82bf,Khumalo Property Holdings mvrufhuu82bf,ZAR,1.33,3333.33,1646.08,1687.25,50.62␍
   Ndlovu v RAF — Depth II mvrufhuu82bf,Sipho Ndlovu mvrufhuu82bf,ZAR,2.00,5000.00,,,␍
   ```
   The JSON values were billableHours 1.3333333333333333, billableValue 3333.3333333333333333, costValue 1646.0800000000000000,
   margin 1687.2533333333333333 and marginPercent 50.62. All cells MATCH at 2dp HALF_UP. Rows come out in the same order as the
   JSON (margin DESC, nulls last), and this is the first observed multi-row CSV. Cost and margin are populated, and the null row stays empty.

## O6
Reports › Profitability (`/org/e2e-test-org/reports/profitability`, default 2026-10-01..2026-10-28). The DOM read of the Matter Profitability table gave:
`Khumalo … | Khumalo Property Holdings mvrufhuu82bf | ZAR | 1.3h | R 3 333,33 | R 1 646,08 | R 1 687,25 | 50.6%` and
`Ndlovu v RAF — Depth II … | Sipho Ndlovu … | ZAR | 2.0h | R 5 000,00 | N/A | N/A | N/A`.
These equal the CSV rows. The page rounds hours and % to 1dp for display, the CSV uses 2dp, and both come from the same values.
Row order is the same. Screenshots: `627D-obs-o6-profitability-page.png` (1698px wide), `-wide.png`, and `-scrolled.png`
(the table is scrolled right so the Cost, Margin and Margin % columns are visible).

## O7
`GET /api/reports/profitability` at wide, default and no-params ranges was compared with BEFORE `02-profitability-alice-*.json`
using `cmp`. All three are **byte-identical**. This was checked before the O5 data was added. Carol's call to the JSON endpoint is
still 403 and matches BEFORE `03-profitability-carol.json` exactly.

## Surprises / notes
- The BEFORE matter "Ndlovu v RAF — Depth II" is Closed, so O5 used a different, Active matter. As a side effect, the tenant now has
  a second profitability row and a cost rate for Alice (R1 234,56 from 2026-10-01). Both were created through the UI.
- Content-Type has no space: `text/csv;charset=UTF-8`. This is valid per RFC 9110, but a test that asserts the exact string
  `text/csv; charset=UTF-8` would fail.
- RFC 4180 quoting and formula defusal (`=`/`+`/`-`/`@`/TAB/CR) were **not observed live**, because no live text cell needs them.
  They are covered by the unit tests only.
- The UI does not clip the Matter Profitability table, but it needs horizontal scroll at 1698px (`overflow-x-auto`): Cost, Margin and
  Margin % are off-screen until the user scrolls. This was already the case before this slice and is outside its scope.
