# ADR-366: Interest credits allocated to matters

**Status**: Accepted (amends [ADR-234](ADR-234-interest-daily-balance-method.md))

**Context**:

[ADR-234](ADR-234-interest-daily-balance-method.md) allocates trust interest per client by the daily balance method and posts "one INTEREST_CREDIT per client". `InterestService.postInterestRun` wrote that credit with `project_id = null`. Every consumer that reads trust money per matter then disagreed with every consumer that reads it per client (Phase 89 exit test, G89-F12 and G89-F22, both HIGH; scenario 636B):

- the refund/payment/fee-transfer approval guard checked the client card, which included the interest;
- the closure gate `TRUST_BALANCE_ZERO` (`TrustBalanceZeroGate`) reads the matter sub-ledger (`calculateBalanceByProjectId`), which did not;
- the matter Trust tab rendered the client card;
- the portal trust read-model projects per matter only (`TrustLedgerPortalSyncService.hasPortalScope` needs a matter), so the interest never reached the client's portal — and `TrustDomainEvent.InterestPosted` was never published, so the portal's interest fan-out listener never ran.

In 636B a client with R3 750 on one matter earned R1,34 interest. The refund of R3 751,34 on the matter was approved against the card, leaving the matter at −R1,34 and the closure gate showing a balance nobody could clear; the portal showed R3 750 while the firm ledger showed R3 751,34.

The client is the legal owner of trust money, but every movement of it in Kazi happens on a matter, and the Legal Practice Act ledger the firm reconciles and closes is per matter. Interest earned on a matter's money belongs on that matter.

**Options Considered**:

1. **Keep client-level credits; read the client card everywhere (closure gate, portal).**
   - Pros:
     - No allocation change, no schema change.
   - Cons:
     - A client with two matters could never close one while the other held money; the gate would read money that is not on the matter.
     - The portal would have to invent a "client-level" row outside every matter; a co-client on a shared matter would see a balance that is not theirs.
2. **Allocate each client's share to its matters pro-rata by balance-days, recorded as child lines of the allocation (CHOSEN, founder decision D5, 2026-11-06).**
   - Pros:
     - Each matter is credited exactly the interest its money earned; matter, card, gate, Trust tab and portal all agree after a run.
     - The client-level calculation is untouched: ADR-234's numbers, the `interest_allocations` row and its unique key, and the reports built on them (`InterestAllocationReportQuery`, `Section35DataPackQuery`, the trust report pack) do not change.
   - Cons:
     - A child table and a second rounding step (to the cent, across matters).
3. **Compute interest per matter from the start (one allocation per client × matter).**
   - Pros:
     - No second split.
   - Cons:
     - Changes the client-level figures (rounding per matter, not per client), the `uq_interest_alloc_run_customer` key, the reports and the Section 35 pack; ADR-234's audit trail is per client by design.

**Decision**:

Option 2. `calculateInterest` walks each rate segment per **matter bucket** — the client's rows grouped by `project_id`, with a null bucket for unmattered rows. A bucket opens at its balance on the day before the segment (`calculateClientMatterBalancesAsOfDate`, the client-balance query grouped by matter) and each transaction moves its own bucket, while the intervals between transactions are the client's. The client's segment balance-days is the **sum** of its buckets'. BigDecimal addition and multiplication are exact, so the average daily balance, gross interest, LPFF share and client share are identical to ADR-234's client-level walk.

Each bucket accumulates a weight `Σ balanceDays × rate × (1 − LPFF share)` over the segments, so a mid-period rate change weights each part of the period by the money it actually earned for the client; when the statutory 5 % LPFF share applies (a client-instructed investment, s86(5)) the factor is a constant and the weight is `Σ balanceDays × rate`. Only the client share is split; the LPFF share stays one firm-level `INTEREST_LPFF` row.

The split is **largest remainder to the cent**: buckets with a positive weight take part; each gets `clientShare × w / W` rounded down to the cent; the leftover cents go one each by larger fractional remainder, then larger weight, then matter id ascending with the unmattered bucket last. The lines always sum exactly to the client share; R0,00 lines are not stored. A matter whose cell is zero or negative over the period gets no line. Co-clients are credited only on their own cell, because buckets are built from rows whose `customer_id` is that client.

Schema (tenant `V12__g89_interest_matter_allocation.sql`): `interest_allocation_lines(interest_allocation_id, project_id NULL, balance_days, weight, client_share > 0, trust_transaction_id)`, one line per (allocation, matter) and at most one unmattered line per allocation (two partial unique indexes), no ON DELETE CASCADE — lines are deleted before allocations on recalculation and draft deletion. An index on `trust_transactions(project_id, customer_id, trust_account_id)` serves the matter cell reads.

Posting writes **one INTEREST_CREDIT per line**, carrying the line's matter, references `INT-CREDIT-{runId}-{matterId}-{first 8 hex characters of clientId}` (93 characters), so co-clients on one matter in one run get distinct references for reference-based bank matching. Unmattered lines and line-less (pre-V12) runs keep `INT-CREDIT-{runId}-{clientId}`, as before (84 characters). Run + client + matter in full would be 121 characters, past the portal read-model's `reference varchar(100)`, which would abort the portal fan-out for the account. The client card is locked once and credited once with the client share. `interest_allocations.trust_transaction_id` points at the credit only when there is exactly one line. Posting then publishes `TrustDomainEvent.InterestPosted`; both listeners run after commit — the portal sync re-projects every matter on the account, and firm admins/owners get an in-app notification (clients are not notified).

Founder rulings that bound the decision:

- **D6** — no backfill. A run calculated before V12 has no lines and posts one unmattered credit as before.
- **D7** — trust rows with no matter stay out of scope (G89-F28): the unmattered bucket remains a client-level credit. Such a credit is not projected to the portal either: the portal read-model is per matter (`hasPortalScope` needs a matter), so a client's portal balance excludes their unmattered interest until G89-F28 is decided.
- **D8** — a matter closed before the run is still credited for the balance-days it held money.

**Consequences**:

- ADR-234's "one INTEREST_CREDIT per client" becomes "one INTEREST_CREDIT per client per matter bucket"; the sum of INTEREST_CREDIT rows plus the INTEREST_LPFF row still equals the run's total interest.
- A matter's sub-ledger, the client card, the closure gate, the matter Trust tab and the portal agree after an interest run; a refund of matter + interest clears both the matter and the card (636B).
- D8 means a matter closed before the run can show a small positive trust balance afterwards; the firm transfers it out as with any closed-matter residue.
- `InterestAllocationResponse` carries the lines (matter id and name, balance-days, share, transaction). The interest-run wizard does not render them yet: its table is one row per client, as the Day 60 e2e asserts.
- Debits on a matter are now checked against the client's money on that matter (G89-F12, `TrustTransactionService.requireMatterCellCovers`), so the matter credit is what makes "refund matter + interest" possible at all.
