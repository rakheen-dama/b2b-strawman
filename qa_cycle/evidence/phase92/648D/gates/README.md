# 648D gates — E2E-PORTAL + OBS (PR #1802, head `7a029760e`)

Stack: FRESH e2e mock-auth stack built from `7a029760e` (frontend :3001, backend :8081, mock IDP :8090, Postgres :5433).
Branch portal: `next dev` on :3002 (`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`, nvm node v20.11.1). No CORS shim needed.
Date: 2026-11-18 (all times UTC).

| Gate | Verdict |
|---|---|
| E2E-PORTAL | **PASS** — 34 passed / 0 failed / 0 skipped / 0 flaky |
| OBS (a) balance card | **PASS** |
| OBS (b) transaction list | **PASS** |
| OBS (c) matter selector | **N/A** (Acme has one trust matter → `/trust` redirects; see below) |
| OBS (d) UI = API = SQL = firm ledger | **PASS** |
| OBS (e) clip probe 1440 / 1280 / 390 | **PASS** |
| OBS (f) backend log | **PASS** for the slice (0 "Portal trust sync failed", 0 ERROR in the passing run); 1 ERROR in the window came from a bad OBS request (O-1) |
| OBS (g) raw-enum scan | **PASS** |

No 648D defect found.

## 1. E2E-PORTAL

`cd portal && PATH=~/.nvm/versions/node/v20.11.1/bin:$PATH NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e:portal --reporter=list,json`
(projects `chromium` + `portal-client-90day`, Playwright started its own `next dev` on :3002 pointing at :8081, `E2E_VISUAL` not set.)
Started 12:27:05Z, ran for 29.7 s, exit 0.

- **34 passed, 0 failed, 0 skipped, 0 flaky.** Files: `e2e-portal-summary.txt` (list output) and `e2e-portal-last-run.json` (JSON reporter).
- The test this slice changed: #26 `day-14-30.spec.ts:77` Day 21. It checks that the Type cell equals the API's `typeLabel` for the run's deposit. It **passed**.
- The run left no Playwright changes in the tree (`git status` showed only this directory).

## 2. OBS

The run order guarded the magic-link limits. The E2E run finished at about 12:27:37Z. We waited until 12:33:41Z (more than 5½ min) and then ran `docker restart e2e-backend`, which was healthy again at 12:34:00Z.

The OBS used **one** portal magic link: `request-link` then `exchange` at 12:34:05Z for `contact@acme.example.com`. The JWT was cached in `/tmp` (not committed) and reused by later attempts.

Script: `obs.cjs`, a Node script using `portal/node_modules/@playwright/test` with headless `chromium.launch()`. The committed file is byte-for-byte the version that ran.

Firm-side changes went through the **firm REST API**, using mock-IDP bearer tokens for Alice (owner) and Bob (admin, the approver). psql was used **read-only** for evidence: `obs-sql.sql` holds the queries and `obs-sql.out` their output.

The script ran three times:

1. **Attempt 1** created steps a and b and the portal session, then failed in the script itself: it waited for the first `li` of the mobile list, which is hidden at 1440 px. The fix was to wait on `:visible`.
2. **Attempt 2** got HTTP 500 on the placement. The script sent `interestRate: 7`, but the API takes a fraction (0.07). See O-1. That request was rolled back: 0 `trust_investments` rows and 0 `INV_*` rows remained. Evidence: `obs/attempt2-api-firm-c-place-500-rate7.json` and `obs/attempt2-obs-result.json`.
3. **Attempt 3** passed the full run (`obs-run.txt`, `obs/obs-result.json`). The step functions are idempotent, keyed on `OBS648D-DEP` and `OBS648D-S864-INV`.

### Setup (firm API)
| Step | Request | Result |
|---|---|---|
| a | `POST /api/trust-accounts` as Alice: INVESTMENT, `investmentBasis: CLIENT_INSTRUCTION` (none existed on the fresh stack) | `d6bee85a…` "OBS 648D s86(4) Investment Account", ACTIVE |
| b | `POST /api/trust-accounts/{general}/transactions/deposit` as Alice: Acme / Website Redesign, R20 000,00, ref `OBS648D-DEP` | RECORDED and posted. Acme general goes from R1 500,00 (the E2E Day-21 deposit) to R21 500,00 |
| P0 | portal before the placement | headline R21 500,00, no breakdown. API: general 21500, invested 0 |
| c | `POST /api/trust-accounts/{general}/investments` as Alice: s86(4), principal R10 000,00, rate 0.07, Nedbank `OBS648D-S864-INV`; then `POST /api/trust-transactions/{INV_PLACE}/approve` as **Bob** | INV_PLACE went from AWAITING_APPROVAL to APPROVED (`approved_by` = Bob Admin). Rows: INV_PLACE (CLIENT, −10 000) and INV_RECEIVE (CLIENT_INVESTMENT, +10 000) |
| P1 | portal after the placement | **headline R21 500,00 (unchanged)**. Breakdown: "In the firm's trust account" R11 500,00 and "Invested on your instruction (section 86(4))" R10 000,00 |
| d | `POST /api/trust-investments/{id}/interest` as Alice: `{entryDate: today, grossAmount: 100, presentation: NET}` | entry: gross 100,00 / Fund 5,00 / net 95,00, NET, POSTED. One `INV_INTEREST` row of 95,00 (CLIENT_INVESTMENT). NET creates no `FUND_INV_SHARE` row |

### (a) Balance card: PASS
Screenshots: `obs/p0-before-placement-matter-1440.png`, `obs/p1-after-placement-matter-1440.png`, `obs/p2-final-matter-1440.png`, `obs/p2-final-balance-card-crop.png`.

| | Before | After placement | Final (after interest) |
|---|---|---|---|
| "Held in trust" (`aria-label="Current balance"`) | R21 500,00 | **R21 500,00** | **R21 595,00** |
| "In the firm's trust account" | (not shown, invested = 0) | R11 500,00 | **R11 500,00** |
| "Invested on your instruction (section 86(4))" | (not shown) | R10 000,00 | **R10 095,00** |

- The headline equals general + invested.
- The placement left the total unchanged (21 500 = 11 500 + 10 000).
- The final invested figure is R10 095,00, which is principal plus net interest.

### (b) Transaction list: PASS
Final desktop rows (`obs/p2-final-matter-1440.txt`, `obs/p2-final-interest-row-crop.png`):

| Type | Description | Amount | Running balance |
|---|---|---|---|
| Section 86(4) investment interest | Section 86(4) investment interest (Nedbank) | R 95,00 | R 10 095,00 **Invested** |
| Placed in section 86(4) investment | Placed in section 86(4) investment at Nedbank (OBS 648D s86(4) Investment Account) | R 10 000,00 | R 11 500,00 |
| Deposit | OBS 648D funding deposit | R 20 000,00 | R 21 500,00 |
| Deposit | Portal Day 21 deposit 3jdsh | R 1 500,00 | R 1 500,00 |

What the list shows:
- **Exactly one** interest line, typed "Section 86(4) investment interest", for R95,00. Its balance cell carries the "Invested" qualifier.
- No line for "Fund share…", "Investment capital received" (the INV_RECEIVE row is not visible), "Fund interest" or "Investment withdrawn".
- The placement row is labelled "Placed in section 86(4) investment", with no qualifier.
- Every Type cell is a human label.

On mobile (`obs/p2-final-matter-390.png`), the interest card reads "Invested bal R 10 095,00". The general rows read "Bal R …".

### (c) Matter selector: N/A
Acme has exactly one trust matter: `portal.portal_trust_balance` has one row for the customer. So `/trust` auto-redirects to `/trust/350afd4f…` (`trust/page.tsx`, `matters.length === 1`), and `MatterSelector` never renders. This was observed: `finalUrl` = the matter page, `selectorRendered: false`, screenshot `obs/p2-final-trust-index-1440.png`.

We did not add a second Acme matter. That would change the shared seed, and the brief's setup list did not include it.

The data the selector would print does agree: summary `currentBalance + investedBalance` = 21 595,00 = the card headline. The component behaviour is covered by vitest `matter-selector.test.tsx`.

### (d) UI = API = SQL = firm: PASS
| Source | General | Invested |
|---|---|---|
| Portal UI breakdown | R11 500,00 | R10 095,00 |
| `GET /portal/trust/summary` (`obs/api-portal-p2-final-summary.json`) | 11500 | 10095 |
| `portal.portal_trust_balance` (`current_balance` / `invested_balance`) | 11500.00 | 10095.00 |
| Tenant posted Σ `ledger_effect` for Acme, by ledger | CLIENT 11500.00 | CLIENT_INVESTMENT 10095.00 |
| Firm `GET /api/trust-accounts/{general}/client-ledgers/{acme}` (`obs/api-firm-p2-client-ledger.json`) | `balance` 11500 | `investedBalance` 10095 |
| Firm UI client ledger (`obs/p2-firm-ledger-totals-crop.png`) | General balance R 11 500,00 | Invested (section 86(4)) R 10 095,00; Total held in trust R 21 595,00 |

`portal.portal_trust_transaction` and the `GET /portal/trust/matters/{id}/transactions` API (`obs/api-portal-p2-transactions.json`) agree row for row:

| Type | Ledger | Amount | Running balance |
|---|---|---|---|
| DEPOSIT | CLIENT | 1500 | 1500 |
| DEPOSIT | CLIENT | 20000 | 21500 |
| INV_PLACE | CLIENT | 10000 | 11500 |
| INV_INTEREST | CLIENT_INVESTMENT | 95 | 10095 |

- The API interest row has `typeLabel` "Section 86(4) investment interest" and `ledger` CLIENT_INVESTMENT.
- The only CLIENT_INVESTMENT row in the portal table is the INV_INTEREST row. INV_RECEIVE moved `invested_balance` without being projected as a row.
- `GET /portal/trust/movements` (`obs/api-portal-p2-movements.json`) carries a `typeLabel` on every row.
- UI rows equal API rows on label, amount and balance.

### (e) Clip probe: PASS
`obs/clip-probe.json` uses the 647D probe-clip method. It reads bounding rects for the table's clipping ancestor, every header and body cell plus their child elements, the "Invested" span, and the balance-card text.

- **1440:** the wrapper's scrollWidth (1086) equals its clientWidth (1086), and the document is 1440 wide. All cells sit inside the wrapper and none is truncated. Type cells span x 401–626; balance cells span x 1245–1383. The "Invested" span (x 1261–1367) is inside its cell and inside the clip.
- **1280:** the wrapper is 974 = 974, the document 1280. All cells are inside and none is truncated. The Type column spans x 369–570, the balance column x 1112–1247, and "Invested" x 1128–1231, inside.
- **390 (mobile cards):** the document is 390 wide. Every card spans x 16–374, and every `p` (including "Invested bal R 10 095,00") is inside its card with no truncation. The balance card's `dt`/`dd` lines are inside the card.
- Screenshots: `obs/p2-final-matter-1440.png`, `obs/p2-final-matter-1280.png`, `obs/p2-final-matter-390.png`.

### (f) Backend log: PASS for the slice
`obs-log-summary.txt` covers `docker logs e2e-backend` from 12:33:44Z (right after the restart) to 12:36:13Z. Breakdown made with `logcount.sh`.

- **"Portal trust sync failed": 0**, both in the OBS window and in the whole container log (boot, E2E-PORTAL and OBS).
- ERROR-level lines in the window: **1**, at 12:35:17Z: `GlobalExceptionHandler` "Unhandled exception" (`DataIntegrityViolationException`, `chk_trust_invest_rate`). This was the attempt-2 placement with `interestRate: 7` (O-1). It is the only ERROR in the whole container log.
- ERROR lines from 12:35:30Z on (the passing run): **0**.
- WARN lines: the restart's usual boot warnings, plus the 4 Hibernate WARN lines of that same failed insert.

### (g) Raw-enum check: PASS
The text of the final page at 1440, 1280 and 390 contains none of `INV_INTEREST`, `INV_RECEIVE`, `CLIENT_INVESTMENT`, `FUND_INV_SHARE`, `DEPOSIT`, `INV_PLACE`, `INV_WITHDRAW`, `INV_RETURN`, `FUND_INTEREST`, `NON_CLIENT` or `INTER_ACCOUNT` (case-sensitive, matched on word boundaries).

Console errors: 0. Page errors: 0.

## Observations (none is a 648D defect)
- **O-1 (pre-existing, outside the slice):** `POST /api/trust-accounts/{id}/investments` with `interestRate` above 1 returns **HTTP 500**, not 400.
  - The cause: `PlaceInvestmentRequest.interestRate` is `@PositiveOrZero` with no upper bound, so the DB check `chk_trust_invest_rate` (0 ≤ rate ≤ 1) throws `DataIntegrityViolationException`.
  - The firm UI converts % to a fraction (`investments/actions.ts:76`) and caps it at 100 (`lib/schemas/trust.ts:305`), so the UI cannot hit this.
  - PR #1802 does not touch `verticals/legal/trustaccounting/investment/` (`git diff 2b7be66d6..HEAD` is empty there).
  - Candidate for `found.md` (owner: a firm trust validation slice).
- **O-2:** This OBS used the requested NET bank advice, which by design writes no `FUND_INV_SHARE` row. So "no Fund share line" holds here by the data's shape. The GROSS path does write a `FUND_INV_SHARE` row (customer and matter null) and was not exercised. Keeping NON_CLIENT rows out of the portal is pinned by the slice's backend tests (AC 5).
- **O-3 (pre-existing convention, unchanged by 648D):** the portal Amount column is unsigned. The placement shows "R 10 000,00" while the running balance drops from 21 500 to 11 500, the same way payments already render.

## Files
- `e2e-portal-summary.txt`, `e2e-portal-last-run.json`: E2E-PORTAL.
- `obs.cjs`: the OBS script, exactly as it ran. `obs-run.txt` is its stdout from the passing run.
- `obs-sql.sql` / `obs-sql.out`: the read-only psql queries and their output (all three attempts appended).
- `logcount.sh`, `obs-log-summary.txt`: backend log check (f).
- `obs/`: screenshots, API payloads (`api-firm-*`, `api-portal-*`), `clip-probe.json`, `obs-result.json` and the attempt-2 records.
