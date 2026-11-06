# ADR-234: Interest Calculation — Daily Balance Method

**Status**: Accepted

**Context**:

Trust accounts earn interest from the bank. This interest must be allocated between individual clients (proportional to their trust balances) and the Legal Practitioners Fidelity Fund (LPFF). The calculation method determines how accurately interest is attributed to each client. The LPFF publishes an annual rate and share percentage. Rates can change mid-period.

The question is which calculation method to use: a simple monthly average, a precise daily balance approach, or a transaction-weighted approach. The choice affects fairness to clients, auditability, and computational complexity. Trust accounting regulations require that interest allocation be defensible under audit — an inaccurate method exposes the firm to compliance findings and client disputes.

**Options Considered**:

1. **Monthly average balance** — Calculate each client's average balance for the month as (opening balance + closing balance) / 2, then allocate the month's interest proportionally based on each client's share of the total average balance.
   - Pros:
     - Simplest to implement: two balance lookups per client per period (opening and closing), one division, one proportion calculation. No need to iterate over transactions or track daily state.
     - Easy to explain to clients and auditors: "your average balance was R200,000 out of R1,000,000 total, so you receive 20% of the interest."
     - Low computational cost: O(n) where n is the number of clients, regardless of transaction volume.
   - Cons:
     - Fundamentally inaccurate. A client who deposits R1,000,000 on the last day of a 30-day month has a monthly average of R500,000 (assuming zero opening balance), identical to a client who held R500,000 for the entire month. The first client's money was only in the account for 1 day — they should receive ~1/30th of what the second client receives, not the same amount.
     - Auditors will flag this. The daily balance method is the standard expected by the Law Society and LPFF. A firm using monthly averages will face questions during the annual trust audit about why a less accurate method was chosen.
     - Unfair to long-standing clients: clients whose funds sit in trust for extended periods effectively subsidize clients with brief, large deposits. Over time, this creates systematic misallocation.
     - Cannot handle mid-month rate changes accurately. If the LPFF rate changes on the 15th of the month, there is no clean way to split a monthly average into two sub-periods — the average itself is a month-level approximation.

2. **Daily balance method** — For each day in the period, record each client's end-of-day balance. Calculate the sum of daily balances (balance-days) for each client, then allocate interest proportionally to each client's share of total balance-days.
   - Pros:
     - Most accurate method. Each day's balance is captured, so a client who holds R1,000,000 for 1 day gets credited for exactly 1 day's worth of interest, not 15 days' worth.
     - Standard method used by banks and expected by auditors. The Law Society trust accounting guidelines reference daily balance calculations. Using this method means the firm's interest allocation matches what the bank itself calculates.
     - Handles mid-period rate changes naturally: split the period at the rate change date, calculate balance-days for each sub-period separately, apply the respective rate to each sub-period, and sum.
     - Transparent and auditable: the calculation can be broken down day-by-day for any client, showing exactly how their interest was derived. This is valuable during trust audits and client queries.
   - Cons:
     - Naive implementation requires a daily snapshot of every client's balance — potentially thousands of rows per month for a firm with many trust clients. This is storage-intensive and requires either a scheduled job to capture daily balances or a retroactive calculation from transaction history.
     - More complex to implement than monthly averages: requires iterating over every day in the period for every client, or an equivalent mathematical approach.
     - If implemented as literal daily snapshots, requires infrastructure to ensure snapshots are captured reliably (what happens if the job fails on a weekend? Missing snapshots break the calculation).

3. **Transaction-date weighted method** — Weight each transaction by the number of days remaining in the period. For each client, start with the opening balance, then for each transaction, calculate the number of days between that transaction and the next (or period end). Multiply each balance by its duration to get balance-days. Mathematically equivalent to the daily balance method but computed from transactions rather than daily snapshots.
   - Pros:
     - Produces identical results to the daily balance method. The sum of (balance x days at that balance) equals the sum of daily balances — it is the same calculation expressed differently.
     - Far more efficient for periods with few transactions. A client with an opening balance and two transactions in a month requires 3 multiplications instead of 30 daily lookups. For a firm with 200 trust clients averaging 5 transactions per month each, this is ~1,000 operations instead of ~6,000.
     - No daily snapshot infrastructure needed. The calculation works directly from the transaction ledger, which already exists. No scheduled jobs, no missing-snapshot risk, no additional storage.
     - Naturally handles irregular periods (28-day February, partial first month for a new client, mid-month interest runs).
   - Cons:
     - Slightly more complex logic: must handle the opening balance as a pseudo-transaction at period start, sort transactions by date, and correctly calculate the days between each pair of transactions.
     - Debugging is less intuitive than a daily snapshot: instead of "here is your balance on each day," the explanation is "here is your balance after each transaction and how many days it was held." Both are auditable, but the daily view is easier for non-technical people to follow.
     - If the transaction ledger has errors (incorrect dates, missing entries), the calculation silently produces wrong results. The daily snapshot approach would catch discrepancies between the snapshot and the ledger.

**Decision**: Option 2 — daily balance method, implemented via Option 3's computation approach.

**Rationale**:

Option 1 is eliminated on accuracy grounds. A client who deposits R500,000 on day 28 of a 30-day month should earn ~2/30ths of the monthly interest on that amount, not half. Monthly average is a shortcut that only works when balances do not change much — trust accounts have frequent deposits and withdrawals. Auditors will flag this method, and it creates systematic unfairness between clients with different deposit patterns. There is no scenario where the simplicity of monthly averages justifies the inaccuracy.

Option 2 (daily balance) is the standard method used by banks and expected by auditors. The calculation is: for each client, sum their balance on each day of the period to get balance-days, divide by total days to get the average daily balance, multiply by (annual rate / 365 x days in period) to get gross interest, then split into LPFF share and client share. This is the method the Law Society expects and the method the bank itself uses to calculate the interest it pays on the trust account.

Option 3 is the efficient way to compute Option 2. Instead of snapshotting balances for every day, the system reconstructs the daily balance from the transaction history: start with the opening balance at period start, apply each transaction on its date, and calculate balance-days between transactions. This gives the same result as tracking daily balances but without needing a daily snapshot table or a scheduled job to populate it. The transaction ledger already exists as the source of truth for all trust accounting — deriving balance-days from it is both efficient and architecturally clean.

The decision is therefore to define the interest calculation as "daily balance method" (Option 2) for audit and compliance purposes, but implement it using the transaction-weighted computation (Option 3) for efficiency. The two are mathematically identical — the implementation is an optimization of the specification, not a deviation from it.

Mid-period rate changes are handled by splitting the calculation at the rate change date. For the portion before the change, use the old rate; for the portion after, use the new rate. Each sub-period gets its own balance-days calculation, its own rate application, and its own interest total. The per-client allocations from each sub-period are summed to produce the final allocation. This is a straightforward extension of the base algorithm — the only addition is querying the LpffRate table for rate boundaries within the period.

The LPFF rate and share percentage come from the LpffRate table (effective_from + rate_percent + lpff_share_percent). The system looks up the rate effective on each sub-period's start date. If no rate change occurs within the period, the entire period is a single sub-period and the calculation proceeds without splitting.

**Consequences**:

- InterestRun entity stores the period, totals, and status (DRAFT -> APPROVED -> POSTED). A draft run can be recalculated or discarded. An approved run is locked for review. A posted run has generated ledger entries and cannot be modified — only reversed by a new correcting run.
- InterestAllocation entity stores per-client breakdown: average_daily_balance, gross_interest, lpff_share, client_share. Each allocation references its parent InterestRun and the client's trust ledger. This provides the audit trail auditors need to verify the calculation for any individual client.
- The calculation must handle the case where a client's balance is zero for part of the period. This is common: a client deposits funds, the firm pays out on their behalf, and the balance returns to zero. Zero-balance days contribute zero to the client's balance-days sum — they simply receive less interest proportionally. A client with zero balance for the entire period receives zero interest and no InterestAllocation row is created.
- Rate changes mid-period require splitting the calculation. The InterestCalculationService must query the LpffRate table for any rate changes with effective_from dates falling within the period, then calculate each sub-period independently. The splitting logic adds complexity to the service but is essential for correctness — applying a single rate to a period that spans a rate change would over- or under-allocate interest.
- Posted interest creates TrustTransaction entries: one INTEREST_CREDIT per client (crediting their trust ledger) and one INTEREST_LPFF for the fund total (debiting the trust account's LPFF payable). These are real ledger entries, not just reporting entries — they affect client balances, appear on trust statements, and must balance. The sum of all INTEREST_CREDIT transactions plus the INTEREST_LPFF transaction must equal the total interest received from the bank for that period.
- Interest runs must not overlap. A unique constraint on trust_account_id + period_start + period_end (excluding REJECTED status) prevents duplicate runs for the same period. If a posted run needs correction, the firm creates a reversal run (which negates the original entries) and then a new run for the corrected period — not an overlapping second run.
- The transaction-weighted computation approach means the calculation's accuracy depends entirely on the completeness and correctness of the transaction ledger. If a transaction is backdated or missing, the interest allocation will be wrong. This reinforces the importance of ledger integrity controls (immutable posted transactions, sequential numbering, balance reconciliation) established in the trust accounting foundation.
