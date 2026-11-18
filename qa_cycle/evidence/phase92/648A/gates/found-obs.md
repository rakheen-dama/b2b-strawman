# 648A gate run: found items (report only; the gate runner changed no source)

## D-1 (DEFECT, HIGH): once any section 86(4) placement is approved, the client-ledger reads return 500

**Kind:** product defect, reproduced on two fresh stacks and in OBS. It is not a flake.

**Finding:** `LedgerTotals.plus` still throws for the two client-ledger types that 648A now posts:

```java
// CLIENT-ledger types not posted before 648A: the slice that posts one places it here.
case INV_PLACE, INV_RETURN ->
    throw new IllegalStateException(
        transactionType + " has no client-ledger total yet (648A places it)");
```

The guard sits at `backend/src/main/java/.../trustaccounting/ledger/LedgerTotals.java:108-110`.

**How it is reached:**
- `ClientLedgerService.totalsFor` → `LedgerTotals.byCustomer` folds every posted `CLIENT`-ledger row by type.
- `TrustLedgerQueryRepository.clientTotalsByType` returns those rows, filtered on `ledger = 'CLIENT' AND posted_at IS NOT NULL`.
- So the first approved `INV_PLACE` makes these two endpoints throw `IllegalStateException`, which surfaces as **HTTP 500**:
  - `GET /api/trust-accounts/{id}/client-ledgers/{clientId}`, for that client;
  - `GET /api/trust-accounts/{id}/client-ledgers`, the list, for **every** client of the firm.

A pending (unposted) placement does not trigger it.

**Correction to `found.md` F-1:** F-1 says "the card's `balance` is R15 000; deposits R45 000 and every other total R0". It is wrong. The card does not render at all; it throws.

**Observed:**

1. **E2E-LEGAL lifecycle, run 1 (stack #1).** `day-60-interest.spec.ts:458` "Bob: Approve the s86(4) placement …" failed at line 473:
   - `GET /api/trust-accounts/5b3e…/client-ledgers/26c0… → 500` after the approval.
   - The matter-total assertion at line 472 had already passed.
   - Because the suite is serial, `day-90-review.spec.ts:94` then failed ("Run-state value siphoFeeNote2 was not recorded"), and 12 tests did not run.
   - Evidence: `lifecycle-run1.log`, `lifecycle-run1-artifacts/day-60-*/` (screenshot and error-context.md), `stack1-backend.log`.
2. **E2E-LEGAL lifecycle, run 2 (fresh stack #2).** The same 2 failures at the same lines, and the same 93 passed / 12 did not run. The per-test outcome diff against run 1 is empty.
   - Backend: 3 × the same `IllegalStateException`.
   - Evidence: `lifecycle-run2.log`, `lifecycle-run2-artifacts/`, `stack2-backend.log`, `stack2-log-breakdown.txt`.
3. **OBS on stack #1 (Moroka, a R7 500 s86(4) placement):**
   - **Before the OBS:** the client-ledgers **list** API was already 500 at baseline, from the lifecycle's approved QuickCollect placement. Moroka's own card was 200 (R241 500).
   - **After Bob approved the OBS placement:**
     - Moroka's card returned **500** (`obs/run1/api-a-approved-client-ledger-card.json`).
     - The client-ledger detail page showed "Something went wrong", and its document returned HTTP 404 (`obs/run1/13-a8-client-ledger-page-approved-1440.png`, `probe-clip.json` badResponses).
     - The Client Ledgers list page read "Unable to load client ledgers. Please try again later." (`obs/g-client-ledgers-list-page-1440.png`).
     - The Trust Accounting overview page read "Unable to load trust account data. Please try again later." (`obs/54-c4-trust-accounting-overview-1440.png`).
     - The matter Trust tab rendered the matter total but **dropped the client line** "… trust balance across all matters: R …" (`obs/run1/12-a8-matter-trust-card-approved-crop.png`).
   - **After the withdrawal:** the same, because the posted `INV_PLACE` row remains and `INV_RETURN` is now posted too.
   - The 16 backend ERROR lines on stack #1 are all this exception: 8 from `getByCustomer` and 8 from `listClientLedgers` (`stack1-error-kinds.txt`).

**Consequence for the gates:**
- E2E-LEGAL lifecycle **FAIL**: 93/105 on both runs, against a baseline of 105/105.
- OBS a and c cannot show the client's general balance as UI = API = SQL. Only SQL could be observed.

**Disposition:** reported to the orchestrator. The fix is not the gate runner's to make.
- 648A's brief marks `LedgerTotals` as 648E's file ("do not touch").
- Either way, the slice that posts `INV_PLACE` / `INV_RETURN` must give them a client-ledger total, or a no-op branch. That is a scope and ownership call for the orchestrator.
- No backend test hits the client-ledger card or list with a posted `INV_PLACE`. That is why Tier 2 and the full verify stayed green.

## W-1 (LAYOUT, MEDIUM): the investments register hides Status and Actions behind a horizontal scroll at 1280 and 1440

**Kind:** layout clip, made worse by 648A, which adds the "Investment account" and "Balance" columns. 648A gives the register 13 columns.

**Finding:** the table is 1344 px wide inside an `overflow-x-auto` wrapper:

| Viewport | Wrapper width | Not visible until scrolled horizontally |
|---|---|---|
| 1280 | 926 px | Balance (cut mid-figure), Status, Actions |
| 1440 | 1086 px | Actions; the Status badge "Withdrawn" is cut at the edge |
| 1920 (reference) | 1182 px | the table still scrolls |

Two consequences:
- At the common widths, the Withdraw / Record Interest buttons and the "Awaiting approval" status are off-screen in the initial view.
- This slice's acceptance relies on both of them: there is no Withdraw while the placement is pending, and the status reads "Awaiting approval".

Nothing is truncated inside a cell. Badges have `scrollWidth = clientWidth`, and the card itself stays inside the viewport.

**Evidence:**
- `obs/g-register-table-1280-crop.png`, `obs/g-register-table-1440-crop.png`.
- `obs/obs-cont-result.json` `steps.g.register-table`: `tableScrollWidth 1344`, `wrapClientWidth 926/1086`, `horizontalScroll true`.

**Disposition:** report only. Whether the pre-648A 11-column table already scrolled at 1280 was not measured on this run. A UI/design call for the orchestrator.

## L-1 (COPY, LOW): the two placement type labels differ in form

On Transactions, the type badge reads:
- "Placed in section 86(4) investment" for `INV_PLACE`;
- "Placed in section 86(3) investment **account**" for `FIRM_INV_PLACE`.

See `obs/55-d1-inv4Place-row-crop.png` / `obs/55-d1-inv3Place-row-crop.png` and `obs-cont-result.json` `steps.d.ui`.

Disposition: report only (registry labels).

## P-1 (pre-existing, known): Transactions status column shows enum-cased status words

The Transactions table renders `RECORDED`, `APPROVED` and `AWAITING APPROVAL` (the status enum with `_` → space) in `textContent`. They are not CSS-uppercased labels. 648A did not change this cell: the E2E asserts "AWAITING APPROVAL" there.

No raw underscore token appears on any surface that 648A touched, including `AWAITING_APPROVAL`, `INV_PLACE`, `FIRM_INV_PLACE`, `CLIENT_INVESTMENT`, `INTER_ACCOUNT`, `FIRM_DISCRETION` and `CLIENT_INSTRUCTION`.

## P-2 (pre-existing, known F-4): raw `ACTIVE` badge on Settings › Trust accounts

The badge appears 5× in `obs/scan-e-settings-trust-accounts-card-KNOWN-F4.txt`. It is recorded as known, not as a failure.

## P-3 (known product shape): the Place Investment dialog takes raw UUIDs for Client ID / Matter ID

These are the "Client UUID" / "Matter UUID" inputs, unchanged by 648A. The E2E fills them the same way. Noted, not scored.

## H-1 (harness): OBS run 1 stopped on an ambiguous SQL column after the UI withdrawal

Run 1 (`obs/run1/`, tag DE43H) changed state through the UI for every step up to and including Alice's "Confirm Withdrawal". Then the evidence query `select transaction_type, status …` (trust_transactions ⋈ trust_accounts) failed with `column reference "status" is ambiguous`.

The query was fixed (qualified as `t.status`), and `obs-cont.cjs` was run.
- It changed **no** state through the UI.
- It read c's post-withdrawal evidence (UI, API and SQL).
- It ran d and e. These are the UI checks plus API probes, which were refused and created nothing: trust_transactions count 14 → 14; the s86(3) account stayed ACTIVE with no closed_date.
- It ran g.

No OBS action was repeated.

## H-2 (harness): the whole-token raw-enum regex missed tokens glued to letters in `textContent`

`textContent` concatenates adjacent elements (for example `PrimaryACTIVEStandard`), so the boundary regex reported F-4's `ACTIVE` as "CSS-uppercase only".

All 18 scan files were re-checked with a case-sensitive **substring** search on `textContent`. The only hits were:
- `ACTIVE` on the settings card (P-2);
- `RECORDED`, `APPROVED` and `AWAITING` on the Transactions page and the approvals row (P-1).

There were no other tokens.

## E-1 (environment): a console 404 per client-ledger page visit

The 404 is the client-ledger detail page's own document request: the page falls into its error state under D-1. It is not a missing asset. There were 0 page errors.
