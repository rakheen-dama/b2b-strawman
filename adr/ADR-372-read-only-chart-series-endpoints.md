# ADR-372: Read-only aggregate endpoints for chart series (trust balance trend, billing trend): shape, gating, SA clock, no persistence; "collected = payments received"

**Status**: Accepted

**Context**:

Phase 90 checked each planned chart against the endpoints that already exist (`.arch-context-phase90-data.md`). Most charts are served today:

- the KPI hours trend (`/api/dashboard/kpis`);
- WIP by fee earner (`/api/dashboard/unbilled-wip`);
- the per-client aged buckets (`/api/collections/debtors`);
- utilization and matter profitability (`/api/reports/{utilization,profitability}`).

Two series are **missing**:

1. **Trust balance per month.** `TrustTransactionRepository.calculateCashbookBalance(accountId)` gives only the current balance. `calculateClientBalanceAsOfDate` works per client and per date. The trust report queries (`ClientTrustBalances`, `TrustReceiptsPayments`, `Section35DataPack`, `TrustReconciliationReport`) each return one as-of or period figure. `GET /api/trust-accounts/{id}/reconciliations` gives one point per reconciliation, includes DRAFTs, and has gaps between reconciliations.
2. **Fees billed vs collected per month.** The only figure is `CashDigestService.BILLED_COLLECTED_SQL`, a trailing-7-day scalar used in the weekly email:
   - billed = `issue_date` with status in `SENT, PARTIALLY_PAID, PAID`;
   - collected = `invoices.paid_at` with status `PAID` only, which **misses part payments** (622D D-3 follow-up).

The data available for these series:

- **Invoices.** `invoices.issue_date` is a business `LocalDate`, stamped from "today" at approval (`Invoice.approve`). `invoices.total` is VAT-inclusive. `InvoiceStatus` is `DRAFT, APPROVED, SENT, PARTIALLY_PAID, PAID, VOID`.
- **Payments.** `payment_events` hold `amount`, `currency`, `status` (`PaymentEventStatus.COMPLETED` among `CREATED, PENDING, COMPLETED, FAILED, EXPIRED, CANCELLED`), `provider_slug` (manual, trust_fee_transfer, xero, PSP) and `created_at` (`Instant`). **There is no payment business-date column.**
- **Trust transactions.** `trust_transactions.transaction_date` is an SA business date since G88-F48.

Every business date in Kazi comes from the injected `Clock.system(KaziTime.ZONE)` ([ADR-358](ADR-358-product-wide-sa-business-clock.md)). Production JVMs and databases run in UTC.

The backend boundary for the phase is set by founder decision 5 and rulings §5: additive `GET` endpoints only, with no migration, entity, cache or write, and at most two Java-touching slices.

**Options Considered**:

1. **Materialised monthly snapshots**: a scheduled job writes month-end trust balances and billed/collected totals into new tables.
   - Pros: Constant-time reads. History is frozen at month end.
   - Cons: It needs a migration, a new entity, a write path, a backfill and a cron, all forbidden this phase. Snapshots also go stale when a back-dated trust posting or a late reversal changes a closed month, and the Section 35 reports already replay current rows, so the two would disagree.
2. **Compute the series in the browser from existing list endpoints** (paged trust transactions, the invoice list and the payment list).
   - Pros: No Java.
   - Cons: It pulls thousands of rows into the client to draw 12 points and duplicates the balance sign rules in TypeScript, where they can drift. Bucketing would use the browser clock, and mixed currencies would be summed silently.
3. **Seed new report definitions in the reporting framework** (`POST /api/report-definitions/{slug}/execute`, `StandardReportPackSeeder`).
   - Pros: It reuses an execution framework.
   - Cons: A new shipped report slug is a pack change (rulings §1: no template pack bump). It is POST-shaped and gated by FINANCIAL_VISIBILITY for every slug, so it can't carry the VIEW_TRUST + module gate. The aging precedent (`InvoiceAgingReportQuery`) sums mixed currencies.
4. **Two small read-only `GET` aggregates computed on read from existing tables, gated like their siblings, dated by the SA clock, uncached (CHOSEN).**
   - Pros: No schema change. One SQL aggregate each, with sign and status rules copied from the queries that already define balance and billing. The tests reconcile the aggregate with existing per-item reads.
   - Cons: Each request recomputes up to 24 months. They add two endpoints to maintain and one or two Java slices.

**Decision**: Option 4 — two uncached, read-only `GET` aggregates (`/api/dashboard/trust-balance-trend` and `/api/reports/billing-trend`) computed on read from existing tables on SA calendar months, where collected = completed payment events bucketed by payment-event creation time (`created_at`) in SA time.

**Rationale**:

Both endpoints are tenant-scoped by the existing filter chain and capability- and module-checked like `trust-position` and `profitability` respectively. They read only `trust_transactions`, `invoices`, `payment_events` and `org_settings`, and write nothing anywhere. The "collected" definition implements founder ruling R3; `payment_events` has no business payment-date column, so its creation time is the payment date.

1. **`GET /api/dashboard/trust-balance-trend?accountId={uuid?}&months={1..24, default 12}`.**
   - **Placement.** It lives in `DashboardController` / `DashboardService` beside `getTrustPosition`. The gate is `@RequiresCapability("VIEW_TRUST")` plus `moduleGuard.requireModule("trust_accounting")` (403 when the module is off). The account defaults to the primary account as in `getTrustPosition`; an unknown id or a firm with no account returns 404. `months` outside 1–24 returns a 400 ProblemDetail.
   - **Computation.** One read-only query in `TrustTransactionRepository` returns an opening balance for `transaction_date < windowStart`, plus `SUM(…) GROUP BY date_trunc('month', transaction_date)` up to `today = LocalDate.now(clock)`. Java runs the cumulative sum and zero-fills empty months.
   - **Sign and status rules are copied verbatim:**
     - `cashbookBalance` uses `calculateCashbookBalance`: DEPOSIT and INTEREST_CREDIT are positive; PAYMENT, FEE_TRANSFER, REFUND and INTEREST_LPFF are negative; TRANSFER_IN and TRANSFER_OUT are neutral.
     - `clientLedgerTotal` uses `calculateClientBalanceAsOfDate` summed over clients: TRANSFER_IN is positive and TRANSFER_OUT is negative.
     - Both use `status IN ('RECORDED','APPROVED')` and exclude REVERSAL.
   - **Response fields:** the account (`trustAccountId`, `accountName`), `currency`, `asOf`, the opening balances before the window (`openingCashbookBalance`, `openingClientLedgerTotal`), `months[]` (each with `month`, `periodEnd`, `cashbookBalance`, `clientLedgerTotal`, `partial`), and `accounts[]` for the account selector. The authoritative shapes are in §15.2.7 of the [architecture doc](../architecture/phase90-ui-modernization.md). The currency is the org default, because trust accounts carry none (as in `MatterTrustBalanceResponse`). The last point is "as of today", not a month end: its `periodEnd` is `asOf` and it carries `partial: true`. The chart says so ([ADR-370](ADR-370-chart-kit-and-palette.md)).
   - **Replay semantics.** The series replays **currently effective** rows by their business date, the same as `calculateClientBalanceAsOfDate` and the Section 35 reports. A payment approved later lands in the month it is dated. `transaction_date` is already an SA business date, so no zone conversion is needed.
2. **`GET /api/reports/billing-trend?months={1..24, default 12}`.**
   - **Placement.** It lives in `report/ReportController` beside `/api/reports/profitability`, with the same `@RequiresCapability("FINANCIAL_VISIBILITY")` gate.
   - **Response fields:** `currency`, the window (`from`, `to` as `YYYY-MM`), `asOf`, and `months[]` (each with `month`, `billed`, `billedCount`, `collected`, `collectedCount`, `partial`). The authoritative shapes are in §15.2.7 of the architecture doc.
   - **Billed** = `SUM(invoices.total)` by `date_trunc('month', issue_date)` with `status IN ('APPROVED','SENT','PARTIALLY_PAID','PAID')`, which excludes DRAFT and VOID. A fee note voided after issue drops out.
   - **Collected** = `SUM(payment_events.amount)` with `status = 'COMPLETED'`, bucketed by `date_trunc('month', created_at AT TIME ZONE 'Africa/Johannesburg')` (`KaziTime.ZONE_ID`). This includes part payments and `trust_fee_transfer` events.
   - **Window bounds** come from `LocalDate.now(clock)` and convert to instants with `start.atStartOfDay(KaziTime.ZONE).toInstant()`. Never `CURRENT_DATE`.
   - **Currency.** Both sums filter to the org default currency, resolved as `outstandingSummary` does (`OrgSettings::getDefaultCurrency`, falling back to `OrgSettingsService.DEFAULT_CURRENCY`). Other currencies are excluded, never converted (rulings §6). Both figures are VAT-inclusive cash amounts, so they are comparable on one axis.
3. **Rejected alternative for "collected": `invoices.paid_at` + `total` (digest parity).** It counts a fee note only when fully PAID and attributes the whole total to the payment date. A R10 000 note paid R4 000 in March and R6 000 in May shows R0 then R10 000, which misrepresents cash for exactly the firms that take part payments and trust fee transfers. Payment events record each receipt where it happened. The digest's `paid_at` definition is left as it is; aligning it is a separate change.
4. **Why no persistence or cache.** Both reads aggregate at most 24 groups over indexed date columns in one tenant schema, and there are no write paths to keep in sync. `unbilled-wip`, `trust-position` and `outstanding-summary` are uncached for the same reason: no tenant invalidation hook exists on trust, invoice or payment writes, and a stale money chart is worse than a slower one.
5. **Tests.** Each endpoint gets an integration test modelled on `dashboard/TrustPositionIntegrationTest.java`:
   - seed through the real REST writes, with relative SA dates (`LocalDate.now(KaziTime.ZONE)`);
   - assert that the latest trust point equals `/cashbook-balance`, and that billed and collected equal sums of the individual fee-note and payment reads;
   - cover 403 (custom role without the capability; module off), 404 (unknown or missing account), 400 (bad `months`) and zero-filled empty months.

   One `Clock.fixed` unit test pins the boundary: a payment at 2026-10-31T22:30Z (00:30 SAST on 1 November) lands in November. The architecture doc §15 decides whether both endpoints ship in one Java slice or each ships with its chart. The budget is ≤ 2 Java slices plus the prerequisite P1.

**Consequences**:

- Positive:
  - The trust dashboard and reports/profitability get real monthly series with no migration, entity, cache or write. Results agree with the balances and fee notes users already see, by test.
  - "Collected" reflects part payments and trust fee transfers, and the definition is recorded here.
  - Both endpoints follow [ADR-358](ADR-358-product-wide-sa-business-clock.md). `ZoneAwareTimeArchTest` keeps them honest.
- Negative:
  - Every chart load recomputes up to 24 months. That is acceptable at design-partner scale, and the materialisation option above is the fallback if profiling shows otherwise.
  - Payment months come from `created_at`, not from when the money arrived. A receipt recorded on 1 November for money received on 31 October lands in November. Fixing that needs a payment business-date column, which is a migration and out of scope.
  - "Billed" now includes APPROVED-but-unsent fee notes, while the weekly digest does not. The two figures differ by design until the digest is aligned.
  - Non-default-currency fee notes and payments are invisible on the chart, which says so.

**Related**: [ADR-358](ADR-358-product-wide-sa-business-clock.md) (SA clock, `KaziTime.ZONE`), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (balances computed server-side in posting order), [ADR-364](ADR-364-payment-receipt-as-generated-document.md) (payment events as the receipt record), [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md) (issue date stamped at approval), [ADR-370](ADR-370-chart-kit-and-palette.md) (charts that consume these series), [ADR-373](ADR-373-detail-hero-kpi-strip.md) (the detail hero uses existing reads only; no new endpoint); [architecture §15](../architecture/phase90-ui-modernization.md).
