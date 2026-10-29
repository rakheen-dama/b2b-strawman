# 629C — E2E-LEGAL lifecycle failures: root-cause diagnosis

Read-only investigation, 2026-10-29/30. Code: `/Users/rakheendama/Projects/2026/worktree-epic-629C` (product byte-identical to 628I gate head cb10c5d63).

## 0. Timing and timezones (common cause)

| Where | Zone | Evidence |
|---|---|---|
| Host (runs Playwright Node + Chromium) | Africa/Johannesburg (SAST, UTC+2) | `date` → `Fri Oct 30 00:09:01 SAST 2026` while `date -u` → `Thu Oct 29 22:09:01 UTC 2026` |
| e2e-frontend (Next.js server, RSC) | UTC, `TZ` unset | `docker exec e2e-frontend date` → `Thu Oct 29 22:09:25 UTC 2026`, `TZ=` |
| e2e-backend (JVM) | UTC, `TZ`/`JAVA_TOOL_OPTIONS`/`JAVA_OPTS` unset | `Thu Oct 29 22:09:01 UTC 2026`, `TZ=` |
| e2e-postgres | UTC | `SHOW timezone` → `UTC`; `now(), current_date` → `2026-10-29 22:09:01+00 / 2026-10-29` |

Playwright config sets no `timezoneId` (`frontend/e2e/playwright.legal-lifecycle.config.ts:26-39`), so Chromium uses the host zone (SAST).
No `TZ` / `user.timezone` / firm-timezone setting anywhere in `compose/`, `infra/`, the Dockerfiles or `backend/src/main` (grep found no hits). Production containers will also run in UTC.

**Correction to the brief's window.** `/tmp/629C-e2e/chain.log` shows `e2e-up exit=0` and `BEGIN lifecycle` at `2026-10-29T22:00:09Z` and `STEP lifecycle exit=1` at `22:03:28Z`. So the whole suite ran from **00:00:09 to 00:03:28 SAST**, after local midnight. For the entire run the host/browser calendar date was **2026-10-30** and the containers' date was **2026-10-29**. This is why every "today" in the run split in two.

Tenant schema:
```sql
select external_org_id, schema_name from public.org_schema_mapping;
-- e2e-test-org|tenant_7d218705360b
```

---

## Failure 1: `day-07-14-work-trust.spec.ts:167` "Alice: My Work lists today's Moroka time entry"

### Test side
- `day-07-14-work-trust.spec.ts:152-163`: Alice logs the entry with `date: isoDate()`.
- `isoDate()` is at `frontend/e2e/helpers/legal-depth-ii.ts:94-100`. It uses `new Date()` with `getFullYear/getMonth/getDate`, which is the Node process's local zone (SAST), so it returned **2026-10-30**.
- The product's own default is the same. `components/tasks/log-time-dialog.tsx:58,159` default the Date field to `new Date().toLocaleDateString("en-CA")` in the browser's zone (SAST), which is also 2026-10-30. The test therefore reproduces what a real SA user's browser does.

### Product side (My Work page, a Server Component running in the UTC container)
- `app/(app)/org/[slug]/my-work/page.tsx:109`: `const today = new Date().toLocaleDateString("en-CA");` runs on the Next.js server, so it uses UTC and returned **2026-10-29**.
- `page.tsx:111-114`: `GET /api/my-work/time-entries?from=${today}&to=${today}`, i.e. 2026-10-29 to 2026-10-29. This feeds `<TodayTimeEntries>` (the "Matter — description" lines the test looks for) and the "Time Today" counter.
- The week range comes from `resolveMyWorkDateRange` (`page.tsx:45-71`), also in server zone. It was Mon 26 Oct to Sun 1 Nov, which is the same in both zones, so the weekly strip did include the entry.

### Stored data
```sql
set search_path=tenant_7d218705360b;
select te.date, te.created_at, te.duration_minutes, m.name, te.description
from time_entries te left join members m on m.id=te.member_id
where te.description ilike '%Report to Master%' or te.description ilike '%Research case law%'
   or te.description ilike '%Collate medical%' or te.description ilike '%Draft letter of demand%'
order by te.created_at;
-- 2026-10-30|2026-10-29 22:01:09.116875+00|90|Carol Member|Research case law — personal injury precedents
-- 2026-10-30|2026-10-29 22:01:11.314869+00|120|Bob Admin|Draft letter of demand to Road Accident Fund
-- 2026-10-30|2026-10-29 22:01:20.704178+00|60|Alice Owner|Report to Master of the High Court — initial filing
-- 2026-10-30|2026-10-29 22:01:46.824597+00|45|Carol Member|Collate medical records for the letter of demand
```
The entries are dated 2026-10-30 (browser/SAST) but were created at 2026-10-29 22:01Z.

### Page snapshot (error-context.md)
`/tmp/629C-e2e/artefacts/lifecycle/frontend_test-results/day-07-14-work-trust-Day-7-e20d7-s-today-s-Moroka-time-entry-chromium/error-context.md`:
- "Time Today" shows `0m / 8h` (lines 165-165+).
- The "Today" card shows `heading "No time recordings yet"` (lines 570-574).
- The weekly strip shows `Fri: 1.0 hours logged` and `1.0h this week` (lines 188, 201-202), and the Time Breakdown lists Moroka at 1.0h. So the entry exists and is in the week; it is only missing from "Today", because the server's today is Thursday the 29th.
- The Upcoming link reads `from=2026-10-30` (line 176). That is `addDays(today, 1)` (`my-work/todays-diary.ts:65`), which again confirms the server's today = 2026-10-29.

### Verdict
**(a) Product defect, frontend server layer.** It is caused by a mismatch inside the product: the client components (log-time dialog, trust dialogs) take "today" from the browser zone, while the My Work RSC takes "today" from the server process zone. The harness is legitimate: it mirrors the dialog's browser-local default. Its only weakness is that it does not pin `timezoneId`, so it is exposed to the host zone.
- **Who is affected:** any SA user from 00:00 to 02:00 SAST while the server runs in UTC, which is the production default since no TZ is set. Their just-logged time is missing from My Work "Today" and "Time Today" reads 0m. The same pattern appears in `dashboard/page.tsx:92` and in the week boundary at Sunday/Monday midnight. Any tenant whose zone differs from the server's would see the same thing during their offset window.
- **Confidence:** high. Stored dates, the snapshot (Fri 1.0h vs Today empty, Upcoming from=10-30) and the code all agree.

## Failure 2: `day-07-14-work-trust.spec.ts:306` "Carol: Log additional time on the Sipho matter"

Same mechanism. At `:315` the test logs with `isoDate()` (2026-10-30), then opens `/my-work` and expects both of today's Sipho entries as `"<matter> — <description>"` in `<TodayTimeEntries>` (`:318-326`). The server queries only 2026-10-29 (`my-work/page.tsx:109-114`). Both "Research case law…" (failed first, `:323`) and "Collate medical records…" are stored as `2026-10-30` (SQL above).
**Verdict (a)**, same defect and confidence as failure 1.
Side effect: `:330` "Trust dashboard overview" did not run (serial describe).

---

## Failure 3: `day-60-interest.spec.ts:96` interest run, Moroka avg daily `7 790,32` expected, `7 516,13` received

### Test expectation
- `day-60-interest.spec.ts:103-104`: `periodStart = isoDate(-30)` and `periodEnd = isoDate()` in host zone, giving 2026-09-30 to 2026-10-30, which is 31 days inclusive.
- `:115-124` expect Moroka `7 790,32` / gross `43,01` and QC `1 451,61` / `8,01`. The comment at `:112-114` says "both funded today". It assumes the R250,000 deposit and the R8,500 fee transfer share one date (today), giving a closing balance of R241,500 for one day.
  - 241 500 / 31 = **7 790,32**
  - 241 500 × 0.065 / 365 = **43,01**

### Product computation
- `InterestService.java:188`: `daysInPeriod = DAYS.between(start,end)+1` = 31.
- `:234-276`: a transaction-weighted walk over `findForStatement` rows in `transaction_date` order. Each transaction adds `currentBalance × daysBetween`, and the tail adds `currentBalance × (DAYS.between(currentDate, segEnd)+1)`.
- `:280-286`: `avg = balanceDays / segDays` (2 dp), then `gross = avg × rate × segDays / 365`.

### Where the two dates come from
- **Deposit** date comes from the browser. `components/trust/RecordDepositDialog.tsx:48-57` defaults `transactionDate` to browser-local today, giving **2026-10-30**. The helper `e2e/helpers/legal-lifecycle.ts:336-361` does not touch the date.
- **Fee transfer** date comes from the server. `backend/.../trustaccounting/transaction/TrustTransactionService.java:622` hard-codes `LocalDate.now()` in the JVM default zone (UTC), giving **2026-10-29**. The Record Fee Transfer dialog has no date field (`RecordFeeTransferDialog.tsx`, no date input; helper `legal-lifecycle.ts:369-391`).

### Stored data
```sql
set search_path=tenant_7d218705360b;
select t.transaction_date, t.created_at, t.transaction_type, t.amount, t.status, c.name, t.reference
from trust_transactions t left join customers c on c.id=t.customer_id order by t.created_at;
-- 2026-10-30|2026-10-29 22:01:39.397212+00|DEPOSIT|250000.00|RECORDED|Moroka Family Trust mvu36zag0e84|DEP/MOROKA/mvu36zag0e84
-- 2026-10-30|2026-10-29 22:01:43.281989+00|DEPOSIT|45000.00|RECORDED|QuickCollect Services mvu36zag0e84|DEP/QC/mvu36zag0e84
-- 2026-10-29|2026-10-29 22:02:21.49381+00|FEE_TRANSFER|8500.00|APPROVED|Moroka Family Trust mvu36zag0e84|FEE/MOROKA/mvu36zag0e84
-- 2026-10-29|2026-10-29 22:03:14.564356+00|FEE_TRANSFER|500.00|AWAITING_APPROVAL|Sipho Ndlovu mvu36zag0e84|FEE/SIPHO/mvu36zag0e84
-- 2026-10-30|2026-10-29 22:03:40.902287+00|DEPOSIT|5000.00|RECORDED|Sipho Ndlovu mvu3b7txec54|DEP/mvu3b7txec54   (depth-ii run)
-- 2026-10-30|2026-10-29 22:03:43.09591+00|PAYMENT|1250.00|APPROVED|Sipho Ndlovu mvu3b7txec54|PAY/DEEDS/mvu3b7txec54 (depth-ii run)

select * from interest_runs;
-- e3beed0a-…|8c57748f-…|2026-09-30|2026-10-30|…|49.50|24.76|24.74|DRAFT|||2026-10-29 22:02:43…

select a.*, c.name from interest_allocations a left join customers c on c.id=a.customer_id;
-- …|bd5d8a71-…|1451.61|31|8.01|4.01|4.00|…|QuickCollect Services mvu36zag0e84
-- …|acae43b8-…|7516.13|31|41.49|20.75|20.74|…|Moroka Family Trust mvu36zag0e84
```
The Moroka fee transfer (dated 10-29) is dated **one day before** the deposit that funds it (10-30). The deposits by themselves are on browser-local dates. Every server-dated fee transfer is on the UTC date.

### Arithmetic: the one-day shift reproduces both received values exactly
Moroka walk, opening 0 at 2026-09-29:
1. FEE_TRANSFER on 10-29: adds 0 × 29 = 0. Balance becomes −8 500.
2. DEPOSIT on 10-30: adds −8 500 × 1 = −8 500. Balance becomes 241 500.
3. Tail: 241 500 × (0 + 1) = 241 500.

Results:
- balanceDays = **233 000**
- avg = 233 000 / 31 = 7 516.129 → **7 516,13** (matches the received value)
- gross = 7 516.13 × 0.065 × 31 / 365 = 41.493 → **41,49** (matches)
- LPFF/client split 20,75 / 20,74 (matches the row)

QC has no server-dated transaction, so its date did not shift. It matched the expected 1 451,61 / 8,01 exactly (45 000 / 31; 45 000 × 0.065 / 365). This is a built-in control.

### Verdict
**(a) Product defect, backend.** `TrustTransactionService.java:622` stamps `FEE_TRANSFER.transaction_date` with `LocalDate.now()` in the JVM default zone (UTC). Sibling client flows stamp the browser-local date (deposit/payment/refund/transfer dialogs: `RecordDepositDialog.tsx:57`, `RecordPaymentDialog.tsx:56`, `RecordRefundDialog.tsx:56`, `RecordTransferDialog.tsx:45`). The same `LocalDate.now()` pattern is in reversal posting (`TrustTransactionService.java:1114,1139,1264,1281,1328`), `TrustAccountService.java:147,244` and `TrustInvestmentService.java:243,289`.
- **Real use:** a fee transfer recorded from 00:00 to 02:00 SAST is dated the previous day. If that is the same night the deposit was recorded, the client ledger shows a debit before its funding credit: a one-day **negative client trust balance** in the Section 86 ledger, the statement running balance and the interest balance-days. Interest is understated (here R41,49 instead of R43,01). Any tenant whose zone differs from the server's has the same exposure during its offset window.
- **Harness:** the expected values are correct under the product's own intended semantics (both dated "today").
- **Confidence:** high, because the exact numbers reproduce and QC acts as a control.

---

## Failure 4: `day-90-review.spec.ts:95` `Run-state value "siphoFeeNote2" was not recorded`

- `siphoFeeNote2` is recorded only at `day-60-interest.spec.ts:269`, inside test `:252` "Alice: Second billing cycle — log new Sipho time and generate its fee note".
- That test is in the same `test.describe.serial("Day 60 — …")` block as failure 3 (`:85`). The lifecycle log lists it as `-   70 … day-60-interest.spec.ts:252:7 … Second billing cycle` (did not run), along with 66–72, after `✘ 65` (failure 3).
- `day-90-review.spec.ts:108` then calls `recordedValue("siphoFeeNote2")` (`helpers/legal-lifecycle.ts:164-166`), which throws. 85–93 did not run after it (serial).

**Verdict: pure cascade from failure 3.** It is not an independent defect. Test `:252` itself was never exercised in this run. It passed at 628I. Whether it would have passed tonight (its `isoDate()` time entry is 10-30) is unproven. Nothing in its path compares against a server-side "today" that I found, but this was not observed.

---

## Prediction

The browser/Node date and the container date agree whenever the run lies entirely within **00:00Z–22:00Z (02:00–24:00 SAST)** on one calendar day. In that window all four failures should disappear and the suite should return to 628I's 101/0/3. This is a prediction, not an observation. To confirm it:
- re-run the lifecycle suite after 00:00Z, or
- reproduce deterministically now by running the same suite with `TZ=UTC` on the Playwright process plus Playwright `timezoneId: "UTC"`. That would make it pass, and would prove the harness is only an exposure, not the cause.

To reproduce the product defect at any hour, set Playwright `timezoneId: "Pacific/Kiritimati"` (UTC+14), or record a fee transfer in a browser whose local date is ahead of UTC.

## Severity and suggested fix location (not fixed)

| # | Verdict | Severity | Reason | Suggested fix location |
|---|---|---|---|---|
| 1, 2 | (a) product | **LOW** | Display-only (My Work "Today" / Time Today). 2-hour nightly window for SAST. Self-corrects. Data is correct. | `frontend/app/(app)/org/[slug]/my-work/page.tsx:109` (and `:45-71`, `dashboard/page.tsx:92`): derive "today"/week in the firm's zone (e.g. `Africa/Johannesburg` / tenant setting via `Intl.DateTimeFormat(..., {timeZone})`), not the server process zone. |
| 3 | (a) product | **MEDIUM** (arguably HIGH for trust compliance) | Regulated trust ledger: fee transfer back-dated a day can produce a negative client balance day and wrong interest allocation. Only from 00:00–02:00 SAST with a UTC server, but it fails silently and persists in posted records. | `backend/.../trustaccounting/transaction/TrustTransactionService.java:622` (plus the other `LocalDate.now()` sites listed): use a firm-zone `Clock`/`ZoneId` (or accept a client-supplied transaction date like the other dialogs). The systemic option is to set `TZ=Africa/Johannesburg` on backend+frontend containers, but that is a deploy decision for the orchestrator. |
| 4 | cascade of 3 | n/a | Not a defect. | none |

Harness hardening (optional, separate decision): pin `timezoneId` in `e2e/playwright.legal-lifecycle.config.ts` and compute `isoDate()` in the same zone. That would mask defects 1–3 rather than fix them, so it should not be done in place of the product fixes.
