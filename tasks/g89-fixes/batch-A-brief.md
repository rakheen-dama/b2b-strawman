# Batch A brief: trust ledger, client vs matter (G89-F12 HIGH, G89-F22 HIGH, G89-F14, G89-F13)

**Branch:** `fix/g89-a-trust-matter-ledger` from fresh `origin/main` (**after Batch B has merged**; B adds the `Clock` and period guard to `InterestService`).
**PR title:** `fix(trust): allocate interest to matters, matter-level debit guard, publish InterestPosted, derived ledger totals, matter Trust tab (G89-F12/F22/F13/F14)`
**Authorisation:** founder D1/D5/D6/D7/D8 (2026-11-06), recorded in `tasks/g89-fixes/TRACKER.md`. One PR, one reproduce-first test and one commit per gap.
**Gates:** backend full verify (orchestrator), frontend gate, portal gate (verification only, since no portal code is expected), two bound reviews, CodeRabbit, browser-observed S2-12, CORR/S2-13, S2-45 and the Day-75 closure on the Keycloak stack.

Paths below are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` unless shown otherwise. All line numbers were verified at `30317a851`, so re-check them after B merges.

## Root cause

Interest credits are posted per client with `project_id = null` (`interest/InterestService.java` posting around `:512-527`). Each consumer reads a different ledger:
- the refund/payment/fee-transfer guard reads the client card (`transaction/TrustTransactionService.performApprovalCompletion` around `:852-896`);
- the closure gate reads the matter sub-ledger (`verticals/legal/closure/gates/TrustBalanceZeroGate.java:47-62` → `calculateBalanceByProjectId`);
- the matter Trust tab reads the client card (`frontend/components/trust/TrustBalanceCard.tsx:68-80`);
- the portal projects per matter only (`customerbackend/service/TrustLedgerPortalSyncService.java` `recomputeMatter` around `:254-291`, `hasPortalScope` around `:517-519`).

Separately, `TrustDomainEvent.InterestPosted` is **never published** in production (only tests call `.of`), so the portal interest listener (`TrustLedgerPortalSyncService` around `:153-171`) never fires.

## Decisions already taken (do not re-open)

- **D5:** interest is allocated to matters pro-rata by balance-days, and client-level totals are unchanged. Write **ADR-366** ("Interest credits allocated to matters"), which amends ADR-234 (`adr/ADR-234-interest-daily-balance-method.md`). Add a note on ADR-234 pointing to 366.
- **D6:** no backfill. A posted run that has no allocation lines (pre-V12) falls back to today's single unmattered credit.
- **D7:** trust rows with no matter are out of scope (G89-F28). The unmattered bucket stays a client-level credit.
- **D8:** a matter closed before the run is still credited for the balance-days it held.

## Design

### 1. Allocation (`InterestService.calculateInterest`, around `:213-361`)

- New repo method in `transaction/TrustTransactionRepository`: `calculateClientMatterBalancesAsOfDate(customerId, accountId, asOfDate)`. It uses the same CASE sign rules and RECORDED/APPROVED filter as `calculateClientBalanceAsOfDate`, with `GROUP BY t.projectId`, and returns a projection `MatterBalanceRow(projectId, balance)`. Inside the service, key buckets by a private `record Bucket(UUID projectId)` so null is never a raw map key.
- For each rate segment:
  - Take the bucket opening balances from the new query.
  - Walk the existing `findForStatement` rows and apply each to its own bucket.
  - Accumulate `balanceDays` per bucket.
  - The client's segment `balanceDays` is the **sum** of the buckets. Because BigDecimal addition is exact, `avgDailyBalance`, `gross`, `lpffShare` and `clientShare` are **identical to today**. `InterestServiceIntegrationTest` numbers must not move.
- Bucket weight `w_b += balanceDays_b,seg × ratePercent_seg × (1 − lpffSharePercent_seg)`. If the statutory 5 % applies, drop the `(1 − lpff)` factor.
- Only `client_share` is split. LPFF stays one firm-level `INTEREST_LPFF` row, unchanged.
- **Largest remainder to the cent:**
  - Use buckets with w > 0 only. W = Σw.
  - `exact = clientShare × w_b / W`, at scale 12 rounded DOWN. `floor = exact.setScale(2, DOWN)`.
  - Distribute the leftover cents one each, in this order: larger fractional remainder, then larger weight, then projectId ascending with unmattered last.
  - Assert Σ lines == clientShare.
  - Persist only lines with share > 0.
- Co-clients: buckets come from rows where `customer_id` is this client, so each client is credited only on its own matter cell.

### 2. Schema: tenant `V12__g89_interest_matter_allocation.sql`

Confirm V12 is next free: `ls backend/src/main/resources/db/migration/tenant | sort -V | tail -1`.

```sql
CREATE TABLE IF NOT EXISTS interest_allocation_lines (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    interest_allocation_id uuid NOT NULL,
    project_id uuid,                                -- NULL = unmattered (client-level) bucket
    balance_days numeric(20,2) NOT NULL,
    weight numeric(30,10) NOT NULL,
    client_share numeric(15,2) NOT NULL,
    trust_transaction_id uuid,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT interest_allocation_lines_pkey PRIMARY KEY (id),
    CONSTRAINT chk_interest_alloc_line_share CHECK (client_share > 0),
    CONSTRAINT interest_allocation_lines_allocation_fkey FOREIGN KEY (interest_allocation_id) REFERENCES interest_allocations(id),
    CONSTRAINT interest_allocation_lines_project_fkey FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT interest_allocation_lines_txn_fkey FOREIGN KEY (trust_transaction_id) REFERENCES trust_transactions(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_alloc_line_matter ON interest_allocation_lines (interest_allocation_id, project_id) WHERE project_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_interest_alloc_line_unmattered ON interest_allocation_lines (interest_allocation_id) WHERE project_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_trust_txn_project ON trust_transactions (project_id, customer_id, trust_account_id) WHERE project_id IS NOT NULL;
```

- New entity `interest/InterestAllocationLine` and repository with `findByInterestAllocationIdIn` and `deleteByInterestAllocationIdIn`.
- **Delete lines before allocations** on the recalculate path (around `:219`) and in `deleteInterestRun` (around `:612`). There is no ON DELETE CASCADE, by house rule.
- `interest_allocations` and its UNIQUE stay unchanged, so `InterestAllocationReportQuery`, `Section35DataPackQuery` and the report pack are **not touched**.
- `InterestAllocationResponse` gains `List<InterestAllocationLineResponse> lines` (id, projectId, projectName, balanceDays, clientShare, trustTransactionId). Batch-load the project names. Add frontend `lines?` to the type.
- **Do not render the lines in `InterestRunWizard`.** `frontend/e2e/**/day-60-interest.spec.ts` (around `:128`) asserts one row per client.

### 3. Posting (`postInterestRun`, around `:481-587`)

- For each allocation with `clientShare > 0`, write one INTEREST_CREDIT per line, with `projectId = line.projectId` and reference `INT-CREDIT-{runId}-{customerId}[-{projectId}]`. Set `line.trustTransactionId`.
- Lock the card once per client and call `addInterestCredit(allocation.clientShare)` once.
- `allocation.trustTransactionId` is the line's transaction id when there is exactly one line, otherwise null. This keeps `InterestServiceIntegrationTest.postInterestRun_createsInterestCreditPerClient` green, since its deposits have no matter.
- No lines (a pre-V12 run) → single unmattered credit, as today.
- At the end, publish `TrustDomainEvent.InterestPosted.of(...)` through an injected `ApplicationEventPublisher`, using the existing payload. Both listeners are AFTER_COMMIT:
  - the portal sync re-projects the account (this closes F22);
  - `TrustNotificationHandler.onInterestPosted` notifies firm admins and owners only. Assert that no client email is sent.

### 4. Matter debit guard (F12) in `transaction/TrustTransactionService`

- New repo query: `calculateMatterClientBalance(accountId, customerId, projectId)`. Same CASE rules, RECORDED/APPROVED, filtered on all three ids.
- New helper `requireMatterCellCovers(accountId, customerId, projectId, amount, ...)`:
  - no-op when projectId is null;
  - called **after** the existing `findByAccountAndCustomerForUpdate` lock, so it cannot race;
  - the transaction being approved is still AWAITING_APPROVAL, so the sum excludes it.
- Call sites:
  - `performApprovalCompletion` for PAYMENT, FEE_TRANSFER (matter inferred from the invoice) and REFUND, after the client-card check;
  - `recordTransfer` (around `:389-398`) when matterId is set, checking the source cell;
  - `reverseTransferOut` (around `:1305-1343`), checking that the target client card AND the target matter cell cover the amount. Today there is no check at all.
- Error: the same exception type as the existing "Insufficient trust balance" (400 ProblemDetail). Keep the **same title**. Detail: `Insufficient trust balance on matter {matterName} for {clientName}. Available on matter: R…, Requested: R…`. Properties: `code=INSUFFICIENT_MATTER_TRUST_BALANCE`, `matterId`, `available`, `requested`.

### 5. F13 derived ledger totals

- New repo query `sumPostedByCustomerAndType(accountId, customerIds)`: GROUP BY customer and type, RECORDED/APPROVED, excluding REVERSAL.
- It feeds `ledger/LedgerTotals(deposits, interestCredited, transfersIn, payments, feeTransfers, refunds, transfersOut)` with `derivedBalance()`.
- `ledger/ClientLedgerService` get and list (one batch query per page) fill `ClientLedgerCardResponse` from it. Add `totalRefunds`, `totalTransfersIn` and `totalTransfersOut`. The existing fields keep their names but are computed.
- `report/ClientTrustBalancesQuery` uses the same helper. The report shape is unchanged, so there is no pack bump.
- Entity counters stay but are no longer read.
- Frontend:
  - `TrustBalanceCard` (client mode) and `trust-accounting/client-ledgers/[clientId]/page.tsx` show Deposits + Interest + Transfers in − Payments − Fee transfers − Refunds − Transfers out = Balance. Hide transfer rows when they are zero.
  - `client-ledgers/page.tsx` adds Interest and Refunds columns and keeps the existing headers.

### 6. F14 matter Trust tab

- `TrustBalanceCard` gets a matter mode when `projectId` is passed (`matters/[id]/page.tsx` around `:661-668` already passes it).
- The primary figure and badge come from `fetchMatterTrustBalance(projectId)` (`GET /api/matters/{id}/trust-balance` → `TrustAccountService.matterBalance` → the same source as the closure gate and Overview tile). Use SWR key `matter-trust-balance-${slug}-${projectId}` and add it to `handleMutationSuccess`.
- Secondary line: "{clientName} — trust balance across all matters: R x".
- The breakdown grid is shown in client mode only.

## Reproduce-first tests (each red on main before its fix commit, where marked)

Backend:
- `transaction/TrustMatterDebitGuardIntegrationTest`
  - `refund_exceedingMatterSubLedger_rejectedEvenWhenClientCardCovers` (**red**) reproduces the exact 636B rows: deposit R5 000 on matter M; PAYMENT R1 250 on M, approved; an INTEREST_CREDIT R1,34 with no matter, inserted the way the old code posts; REFUND R3 751,34 on M. Approval → 400 with the title and `code`, M stays 3 750,00, card stays 3 751,34.
  - `refund_equalToMatterSubLedger_approved_closureGatePasses`
  - `payment_onMatter_exceedingCell_rejected`
  - `feeTransfer_inferredMatter_exceedingCell_rejected`
  - `coClient_cannotSpendOtherClientsMatterMoney`
  - `transfer_withMatter_exceedingSourceCell_rejected`
  - `reverseTransferOut_targetAlreadySpent_rejected`
  - `unmatteredRefund_guardedByClientCardOnly`
- `interest/InterestMatterAllocationIntegrationTest`
  - `phase89_636B_interestAllocatedToMatter_refundOfMatterPlusInterestApproved_matterAndClientZero_gatePasses`, plus "+R0,01 → 400"
  - `singleMatter_creditCarriesProjectId`
  - `twoMattersPlusUnmattered_proRataByBalanceDays_linesSumExactlyToClientShare`
  - `largestRemainder_threeEqualMatters_oddCent_deterministic`
  - `negativeOrZeroCell_getsNoLine`
  - `coClientMatter_eachClientCreditedOnOwnCell`
  - `midPeriodRateChange_clientTotalsUnchanged`
  - `recalculateAndDeleteDraft_removeLines`
  - `postWithoutLines_fallsBackToSingleUnmatteredCredit`
  - **Use past periods** (Batch B guard).
- `customerbackend/service/InterestPortalProjectionIntegrationTest`
  - `portalTrustBalance_equalsFirmClientLedger_afterInterestRun` (**red**). The run posts committed, with ORG_ID bound. Σ portal balances for the customer == the card balance, and the INTEREST_CREDIT is present in the portal transactions for M.
  - `postInterestRun_notifiesAdminsNotClients`
- `ledger/ClientLedgerTotalsIntegrationTest`
  - `totals_reconcile_5000_plus_1_34_minus_1250_minus_3751_34_equals_0` (**red** for the refunds field)
  - `reversedPayment_excludedFromTotalPayments`
  - `transfers_inAndOut_reported`
- Unit test `ledger/LedgerTotalsTest`.

Frontend:
- `__tests__/trust/trust-balance-card-matter-mode.test.tsx`: the primary figure is the matter balance, the badge follows it, the secondary client line renders.
- Update `trust-client-ledgers.test.tsx`, `trust/client-ledger-page.test.tsx` and `trust-coexistence.test.tsx`.

## Known blast radius (fix seeds, never weaken the guard)

The guard now rejects "deposit with no matter, then debit on a matter". Run and fix, by adding a `matterId` to the seed deposits:
- Backend: `InvoiceLifecycleIntegrationTest`, `ClientLedgerRunningBalanceIntegrationTest`, `TrustPostingDateZoneIntegrationTest`, `TrustReconciliationService*Test`, `PortalEmailNotificationChannelIntegrationTest`, and anything else that goes red in the trust, invoice, closure, portal or report packages.
- e2e (grep and inspect; the orchestrator runs the suites): `legal-lifecycle/day-07-14-work-trust`, `day-30-billing`, `day-90-review`, `legal-depth-ii/day-05`, `day-30-45`, `day-75-85-closure`, `admin-audit-30day/fixtures.ts`, `e2e/helpers/legal-lifecycle.ts`.

List every seed change in the PR body.

## Out of scope (record, do not fix)

- G89-F28: no-matter rows.
- G89-F29: `reverseTransaction` publishes no event.
- G89-F30: credit reversals are a no-op.
- The matters-list "Trust" column showing the client total on each row.
- The SoA trust block reading the client ledger.

## Process

The process is the same as Batch B (`batch-B-brief.md`, "Process"):
- `pwd` check;
- red tests committed before fixes;
- targeted tests covering trustaccounting/**, customerbackend/**, closure/**, invoice/**, report/**, and every importer of the changed classes;
- frontend and portal gates;
- open the PR;
- **no full verify and no merge**;
- report the PR number, head sha, counts, seed changes and judgment calls.
