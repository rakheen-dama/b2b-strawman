# ADR-041: Multi-Currency Store-in-Original

**Status**: Accepted

**Context**: Phase 8 introduces monetary values throughout the platform: billing rates, cost rates, rate snapshots on time entries, project budgets, and profitability calculations. DocTeams serves organizations across multiple countries and currencies -- a South African firm bills in ZAR, a UK firm in GBP, and a US firm in USD. Some organizations operate across currency boundaries: a consulting firm might bill European clients in EUR and domestic clients in USD, while tracking internal costs in USD.

The platform must decide how to handle multiple currencies. The core tension is between simplicity (single currency) and flexibility (multi-currency). Currency conversion is a complex domain involving exchange rates that fluctuate daily, conversion timing semantics (at invoice date? at payment date? at a fixed monthly rate?), and reconciliation workflows. The platform has no external exchange rate service and the Phase 8 constraints explicitly prohibit introducing one.

There is currently no currency concept anywhere in the codebase. No entity stores a currency field, no `OrgSettings` entity exists (see context inventory Section 10), and the existing `TimeEntry.rateCents` is an untyped integer with no currency attribution.

**Options Considered**:

1. **Single currency per organization (force conversion at entry)** -- Each organization configures one currency. All rates, budgets, and values use that currency. If an organization deals with a foreign-currency client, the user must mentally convert and enter the rate in the org's currency.
   - Pros:
     - Simplest model -- no currency field needed on individual entities (inherited from org setting)
     - All aggregation is straightforward: `SUM(value)` without grouping by currency
     - Frontend always displays one currency -- no multi-currency UI complexity
     - No risk of mixed-currency comparisons or invalid cross-currency arithmetic
   - Cons:
     - Forces users to perform manual currency conversion when billing in a client's currency
     - Cannot accurately represent a client agreement in the client's currency (e.g., "we agreed on EUR 150/hr" must be stored as the USD equivalent, which changes daily)
     - Loses the original billing currency -- invoices generated later cannot show the agreed client currency
     - Does not match real-world practice for firms operating across borders
     - Changing the org's currency retroactively invalidates all stored monetary values

2. **Store in original currency, aggregate by currency (no conversion)** -- Every monetary value stores its own currency code alongside the amount. Aggregation queries group results by currency and return per-currency totals. No conversion between currencies.
   - Pros:
     - Preserves the original currency of each financial agreement (billing rates in client currency, cost rates in org currency)
     - Forward-compatible with future invoicing -- invoice line items carry the correct client currency
     - No exchange rate dependency -- no stale rates, no conversion errors, no rate source to maintain
     - Honest representation: if the system cannot convert currencies accurately, it should not pretend to
     - Aggregation by currency is semantically correct -- adding ZAR and USD amounts is meaningless without conversion
     - Supports the common pattern where billing and cost currencies differ (bill client in EUR, track internal costs in USD)
   - Cons:
     - No single "total revenue" or "total profit" number across currencies -- each currency is a separate line item
     - Frontend must handle per-currency display in tables, charts, and summaries
     - Users managing multi-currency organizations see more rows in profitability reports
     - Project budgets in one currency cannot be compared against time entries billed in another currency

3. **Store in original + convert to base currency at entry time** -- Every monetary value stores both its original currency/amount and a converted amount in the organization's base currency, using a conversion rate captured at entry time.
   - Pros:
     - Preserves original currency for invoicing and client-facing reports
     - Provides a base-currency total for internal reporting and cross-currency comparison
     - Conversion rate is frozen at entry time (similar to rate snapshotting in [ADR-040](ADR-040-point-in-time-rate-snapshotting.md))
   - Cons:
     - Requires an exchange rate source -- where does the conversion rate come from? Manual entry per transaction is burdensome; an external API is explicitly out of scope for Phase 8
     - Two monetary values per field (original + base) doubles the column count for every monetary column
     - Frozen conversion rates become stale -- a report showing base-currency totals uses a mix of historical exchange rates that may not reflect current values
     - Adds significant complexity to the data model, service layer, and frontend without a reliable exchange rate source
     - The "base currency total" gives a false sense of precision when exchange rates are manually entered or outdated

**Decision**: Store in original currency, aggregate by currency (Option 2).

**Rationale**: Option 2 is the only honest approach given the platform's constraints and the inherent complexity of currency conversion:

1. **No exchange rate source**: The Phase 8 constraints explicitly prohibit external exchange rate APIs and background workers. Without a reliable, automated exchange rate source, any conversion would require manual rate entry per transaction -- a burden that negates the automation goals of Phase 8. Option 2 sidesteps this entirely by not converting.

2. **Semantic correctness**: Adding $10,000 USD and R180,000 ZAR to get a "total" of $190,000 is wrong. It is not a simplification -- it is an error. Grouping by currency and showing two separate totals is factually correct. Users of multi-currency organizations understand this; they already manage multiple bank accounts and separate P&L statements by currency.

3. **Forward compatibility with invoicing**: Future invoice generation (explicitly out of scope for Phase 8, planned for a later phase) will need time entries valued in the client's billing currency. If the system stores everything in a base currency, invoice generation would need to reverse-convert -- introducing yet another source of rounding errors and exchange rate discrepancies. Storing in original currency means invoice line items carry the correct amount in the correct currency from the start.

4. **BigDecimal + ISO 4217**: All monetary amounts use `BigDecimal` (Java) / `DECIMAL` (PostgreSQL) with ISO 4217 currency codes stored as `VARCHAR(3)` alongside every monetary value. Currency is never implicit. The `BillingRate` entity has `hourlyRate` + `currency`. The `TimeEntry` snapshots have `billingRateSnapshot` + `billingRateCurrency` and `costRateSnapshot` + `costRateCurrency`. `ProjectBudget` has `budgetAmount` + `budgetCurrency`. This explicit pairing prevents accidental cross-currency arithmetic.

5. **Migration path to conversion**: If the platform later introduces currency conversion, the path is additive, not restructuring:
   - Add an `exchange_rates` table (date, from_currency, to_currency, rate)
   - Add a `base_currency` setting to `OrgSettings`
   - Add a conversion-on-read utility that converts per-currency results to base currency for reporting
   - No schema changes to existing entities -- the original currency values remain the source of truth

**Consequences**:
- Every monetary field in the schema is paired with a currency field: `hourly_rate` + `currency`, `billing_rate_snapshot` + `billing_rate_currency`, `budget_amount` + `budget_currency`, etc.
- All aggregation queries (`SUM`, profitability calculations) include `GROUP BY currency` -- results are arrays of per-currency objects
- Profitability API responses return `List<CurrencyProfitability>` where each element has `currency`, `billableValue`, `costValue`, `margin`, `marginPercent`
- Frontend tables and charts display per-currency rows; multi-currency projects show one row per currency
- A project budget set in USD cannot be compared against time entries billed in EUR -- budget status calculation only includes time entries whose `billing_rate_currency` matches the `budget_currency`
- The organization's `default_currency` (stored in a new `OrgSettings` entity) is used as the default when creating new rates and budgets, but can always be overridden per entity
- No "total across all currencies" number exists in the system -- this is a deliberate limitation, not a bug
- Future conversion support requires an exchange rate table, a base currency config, and a conversion utility -- but zero changes to existing entities or stored data
