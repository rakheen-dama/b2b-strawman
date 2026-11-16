# 649A red runs — H-a..H-d and the characterisation paths, intended forms on unmodified `main`

commit: aecf93279c71b999b4f44e609864a2009595a364 (main `aecf93279` + the four new test files only; nothing under backend/src/main touched)
command: cd backend && ./mvnw test -Dtest="TrustEquationCharacterisationIntegrationTest,TrustReversalEquationIntegrationTest" -q  (exit=1)
run date: 2026-11-16

Every test below was written to the **intended** behaviour (architecture §16.3.11; founder ruling #1 for H-d) and run before any
pin was written. Stack frames from JUnit / JDK / Spring / AssertJ internals and the CGLIB proxy frame are elided; everything else is
verbatim from `target/surefire-reports/<FQCN>.txt`.

## Outcome table

| Item | Intended-form test (as run red) | Red? | Route | Measured wrong outcome | Pinned as | Fixing slice |
|---|---|---|---|---|---|---|
| H-a | `TrustReversalEquationIntegrationTest.approvedCreditReversal_movesCashbookAndClient` | **RED** (failure) | service (deposit hand-set `APPROVED`, as `TrustTransactionServiceTest.reverseCreditTransaction_requiresApproval`) | approval of the credit reversal: cashbook moved 0.00 (expected −5000.00), clientLedgers moved 0.00 (expected −5000.00); books stay balanced | `approvedCreditReversal_movesNeitherCashbookNorClient_pinsKnownDefect` | 649B |
| H-b | `TrustReversalEquationIntegrationTest.debitReversalWithoutCard_movesBothSidesOrNeither` | **RED** (failure) | **direct repository writes only** (card-less `APPROVED` PAYMENT) — both service-only probes refused (below) | cashbook − client ledgers moved +4000.00 (cashbook +4000.00, client ledgers 0) | `debitReversalWithoutCard_movesCashbookOnly_pinsKnownDefect` — "structural — closed by derived balances" | 646A |
| H-c | `TrustReversalEquationIntegrationTest.rejectedCreditReversal_leavesOriginalReversible` | **RED** (error) | service (deposit hand-set `APPROVED`) | second `reverseTransaction` refused: title "Cannot reverse transaction", detail "Transaction has already been reversed" (`TrustTransactionService.java:1268`) | `rejectedCreditReversal_blocksLaterReversal_pinsKnownDefect` | 649B |
| H-d | `TrustReversalEquationIntegrationTest.recordedDeposit_isReversible` | **RED** (error) | service only (deposit recorded through `recordDeposit`, status `RECORDED`) | `reverseTransaction` refused: title "Cannot reverse transaction", detail "Transaction must be in APPROVED status to be reversed" (`TrustTransactionService.java:1259`) | `recordedDeposit_cannotBeReversed_pinsKnownDefect` | 649B (ruling #1) |
| Path 7b | `TrustEquationCharacterisationIntegrationTest.creditReversalApproved_movesCashbookAndClient` | **RED** (failure) | as H-a | cashbook moved 0.00, clientLedgers moved 0.00 (expected −5000.00 each) | `creditReversalApproved_movesNothing_pinsKnownDefect` | 649B |
| Path 7d | `TrustEquationCharacterisationIntegrationTest.creditReversalRejectedThenReversedAgain_isAccepted` | **RED** (error) | as H-c | refused "Transaction has already been reversed" | `creditReversalRejectedThenReversedAgain_isRefused_pinsKnownDefect` | 649B |
| Path 11 | `TrustEquationCharacterisationIntegrationTest.seededDemoBooks_balance` | **RED** (failure) | `LegalDemoDataSeeder.seed` on a fresh separate tenant | **cashbook 27000.00, client ledgers 0, difference 27000.00** — matches the expected R27 000,00 (§16.1.6 / inventory §0.2); first measurement of the DEFERRED baseline figure | `seededDemoBooks_cashbookExceedsClientLedgersBy27000_pinsKnownDefect` | 646A |
| Paths 1–6, 7a, 7c, 8 | the other 18 characterisation methods | green | service | balanced; every term moved exactly as expected; `assertServicesAgree` held; `@AfterAll assertFirmBalanced(today)` held on both main tenants | asserted balanced (not pins) | — |

Nothing was struck: all four hypotheses reproduced. No `STRUCK.md`.

## Surefire summary — TrustReversalEquationIntegrationTest (intended forms + the two temporary H-b probes)

```
-------------------------------------------------------------------------------
Test set: io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest
-------------------------------------------------------------------------------
Tests run: 6, Failures: 2, Errors: 2, Skipped: 0, Time elapsed: 26.18 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest
io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.rejectedCreditReversal_leavesOriginalReversible -- Time elapsed: 0.634 s <<< ERROR!
io.b2mash.b2b.b2bstrawman.exception.InvalidStateException: 400 BAD_REQUEST, ProblemDetail[type='null', title='Cannot reverse transaction', status=400, detail='Transaction has already been reversed', instance='null', properties='null']
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.reverseTransaction(TrustTransactionService.java:1268)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.lambda$rejectedCreditReversal_leavesOriginalReversible$4(TrustReversalEquationIntegrationTest.java:333)
	at io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.assertMoves(TrustBooksHarness.java:287)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.lambda$rejectedCreditReversal_leavesOriginalReversible$3(TrustReversalEquationIntegrationTest.java:328)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.asIn(TrustReversalEquationIntegrationTest.java:394)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.as(TrustReversalEquationIntegrationTest.java:385)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.rejectedCreditReversal_leavesOriginalReversible(TrustReversalEquationIntegrationTest.java:325)

io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.recordedDeposit_isReversible -- Time elapsed: 0.039 s <<< ERROR!
io.b2mash.b2b.b2bstrawman.exception.InvalidStateException: 400 BAD_REQUEST, ProblemDetail[type='null', title='Cannot reverse transaction', status=400, detail='Transaction must be in APPROVED status to be reversed', instance='null', properties='null']
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.reverseTransaction(TrustTransactionService.java:1259)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.lambda$recordedDeposit_isReversible$1(TrustReversalEquationIntegrationTest.java:363)
	at io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.assertMoves(TrustBooksHarness.java:287)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.lambda$recordedDeposit_isReversible$0(TrustReversalEquationIntegrationTest.java:358)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.asIn(TrustReversalEquationIntegrationTest.java:394)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.as(TrustReversalEquationIntegrationTest.java:385)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.recordedDeposit_isReversible(TrustReversalEquationIntegrationTest.java:355)

io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.approvedCreditReversal_movesCashbookAndClient -- Time elapsed: 0.061 s <<< FAILURE!
org.assertj.core.error.AssertJMultipleFailuresError: 

Multiple Failures (2 failures)
-- failure 1 --
[cashbook moved by 0.00, expected -5000.00] 
expected: -5000.00
 but was: 0.00
at TrustBooksHarness.term(TrustBooksHarness.java:313)
-- failure 2 --
[clientLedgers moved by 0.00, expected -5000.00] 
expected: -5000.00
 but was: 0.00
at TrustBooksHarness.term(TrustBooksHarness.java:313)
	at io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.assertMoves(TrustBooksHarness.java:305)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.lambda$approvedCreditReversal_movesCashbookAndClient$2(TrustReversalEquationIntegrationTest.java:161)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.asIn(TrustReversalEquationIntegrationTest.java:394)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.as(TrustReversalEquationIntegrationTest.java:385)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.approvedCreditReversal_movesCashbookAndClient(TrustReversalEquationIntegrationTest.java:158)

io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.debitReversalWithoutCard_movesBothSidesOrNeither -- Time elapsed: 0.030 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 
[cashbook − client ledgers moved by 4000.00 (cashbook moved 4000.00, client ledgers moved 0); a reversal must move both sides or neither] 
expected: 0
 but was: 4000.00
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest.debitReversalWithoutCard_movesBothSidesOrNeither(TrustReversalEquationIntegrationTest.java:212)

```

(Two of the six testcases in this run are the temporary `hbProbe_*` methods; both passed — see the H-b section.)

## Surefire summary — TrustEquationCharacterisationIntegrationTest (intended forms of 7b, 7d, 11)

```
-------------------------------------------------------------------------------
Test set: io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest
-------------------------------------------------------------------------------
Tests run: 21, Failures: 2, Errors: 1, Skipped: 0, Time elapsed: 2.321 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest
io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.creditReversalRejectedThenReversedAgain_isAccepted -- Time elapsed: 0.073 s <<< ERROR!
io.b2mash.b2b.b2bstrawman.exception.InvalidStateException: 400 BAD_REQUEST, ProblemDetail[type='null', title='Cannot reverse transaction', status=400, detail='Transaction has already been reversed', instance='null', properties='null']
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.reverseTransaction(TrustTransactionService.java:1268)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.lambda$reverse$1(TrustEquationCharacterisationIntegrationTest.java:736)
	at io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.assertMoves(TrustBooksHarness.java:287)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.lambda$reverse$0(TrustEquationCharacterisationIntegrationTest.java:731)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.as(TrustEquationCharacterisationIntegrationTest.java:473)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.reverse(TrustEquationCharacterisationIntegrationTest.java:728)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.creditReversalRejectedThenReversedAgain_isAccepted(TrustEquationCharacterisationIntegrationTest.java:397)

io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.seededDemoBooks_balance -- Time elapsed: 0.633 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 
[seeded books: cashbook 27000.00 vs creditors 0 (client ledgers 0)] 
expected: 0
 but was: 27000.00
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.seededDemoBooks_balance(TrustEquationCharacterisationIntegrationTest.java:456)

io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.creditReversalApproved_movesCashbookAndClient -- Time elapsed: 0.027 s <<< FAILURE!
org.assertj.core.error.AssertJMultipleFailuresError: 

Multiple Failures (2 failures)
-- failure 1 --
[cashbook moved by 0.00, expected -5000.00] 
expected: -5000.00
 but was: 0.00
at TrustBooksHarness.term(TrustBooksHarness.java:313)
-- failure 2 --
[clientLedgers moved by 0.00, expected -5000.00] 
expected: -5000.00
 but was: 0.00
at TrustBooksHarness.term(TrustBooksHarness.java:313)
	at io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.assertMoves(TrustBooksHarness.java:305)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.lambda$approve$0(TrustEquationCharacterisationIntegrationTest.java:707)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.as(TrustEquationCharacterisationIntegrationTest.java:473)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.approve(TrustEquationCharacterisationIntegrationTest.java:704)
	at io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest.creditReversalApproved_movesCashbookAndClient(TrustEquationCharacterisationIntegrationTest.java:366)

```

The other 18 testcases in that run (all passed): `feeTransferRequested_movesNothing`, `feeTransferReversal_movesBackAndCascadesToFeeNote`,
`paymentRejected_movesNothing`, `refundRejected_movesNothing`, `paymentReversal_movesCashbookAndClientBack`, `deposit_movesCashbookAndClient`,
`refundApproved_movesCashbookAndClient`, `paymentApprovedSingleMode_movesCashbookAndClient`,
`interClientTransfer_isCashbookNeutralAndClientTotalUnchanged`, `paymentRequested_movesNothing`, `transferReversal_reversesBothLegs`,
`refundRequested_movesNothing`, `creditReversalRejected_movesNothing`, `feeTransferRejected_movesNothing`,
`paymentDualMode_firstApprovalMovesNothing_secondPosts`, `refundReversal_movesCashbookAndClientBack`, `creditReversalRequested_movesNothing`,
`feeTransferApproved_movesCashbookAndClient`.

## H-b — service-only attempts (task 649.3: "record whether any service-only path reaches it")

Both probes ran in the same red run (cardless tenant `org_trust_reversal_cardless`) and **passed**, i.e. each service-only route is
refused on `main`. They were temporary and are not committed (OQ #5 spirit: the evidence lives here).

1. `hbProbe_seederShapedRecordedPayment_isRefusedByReversal` — a PAYMENT written exactly as `LegalDemoDataSeeder.saveTrustTxn` writes it
   (`RECORDED`, no card) → `reverseTransaction` throws `InvalidStateException` "Transaction must be in APPROVED status to be reversed". PASSED.
2. `hbProbe_paymentForClientWithoutCard_isRefusedAtApproval` — `recordPayment` for a client with no card (AWAITING_APPROVAL), then
   `approveTransaction` by a second member → `InvalidStateException` containing "Insufficient trust balance" and "Available: R0,00". PASSED.

Cards are never deleted (grep: no `ledgerCardRepository.delete*` and no `DELETE FROM client_ledger_cards` under `backend/src/main`), so no service path produces a posted debit without a card.
**Conclusion: H-b reproduces only through direct repository writes (a card-less row with a hand-set `APPROVED` status) → recorded as
"structural — closed by derived balances", fixing slice 646A.** (After 649B makes `RECORDED` rows reversible, probe 1's seeder-shaped row
becomes reversible — still a direct-repository row; 646A closes the class of defect by deriving client balances from the rows.)

Probe source (as run):

```java
  /** H-b service-only probe 1 (temporary): the seeder's own RECORDED payment is not reversible. */
  @Test
  void hbProbe_seederShapedRecordedPayment_isRefusedByReversal() {
    var account = freshAccount(cardlessSchema, cardlessMemberId);
    var client = newClient(cardlessSchema, cardlessMemberId, "H-b probe 1");
    var matter = TestTrustMatterHelper.openClientMatter(
            cardlessSchema, transactionTemplate, projectRepository, client, "H-b probe 1 matter", cardlessMemberId);
    saveSeederShapedTxn(account, "DEPOSIT", "10000.00", client, matter, "RECORDED");
    var paymentId = saveSeederShapedTxn(account, "PAYMENT", "4000.00", client, matter, "RECORDED");

    asIn(cardlessSchema, CARDLESS_ORG_ID, cardlessMemberId,
        () -> assertThatThrownBy(() -> transactionService.reverseTransaction(paymentId, "probe 1"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Transaction must be in APPROVED status to be reversed"));
  }

  /** H-b service-only probe 2 (temporary): a payment for a client with no card cannot be approved. */
  @Test
  void hbProbe_paymentForClientWithoutCard_isRefusedAtApproval() throws Exception {
    var account = freshAccount(cardlessSchema, cardlessMemberId);
    var client = newClient(cardlessSchema, cardlessMemberId, "H-b probe 2");
    var matter = TestTrustMatterHelper.openClientMatter(
            cardlessSchema, transactionTemplate, projectRepository, client, "H-b probe 2 matter", cardlessMemberId);
    var paymentId = new UUID[1];
    asIn(cardlessSchema, CARDLESS_ORG_ID, cardlessMemberId,
        () -> paymentId[0] = transactionService.recordPayment(account,
                new RecordPaymentRequest(client, matter, new BigDecimal("4000.00"), "PAY-HB-PROBE2", "probe 2", DEPOSIT_DATE)).id());
    var secondMember = UUID.fromString(TestMemberHelper.syncMember(mockMvc, CARDLESS_ORG_ID,
            "user_rev_cardless_approver", "rev_cardless_approver@test.com", "Rev Cardless Approver", "owner"));
    asIn(cardlessSchema, CARDLESS_ORG_ID, secondMember,
        () -> assertThatThrownBy(() -> transactionService.approveTransaction(paymentId[0], secondMember))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Insufficient trust balance")
                .hasMessageContaining("Available: R0,00"));
  }
```

## Intended-form sources (for reviewers re-running the red on `main`)

To reproduce: replace each committed `…_pinsKnownDefect` method body with the intended form below (helpers are unchanged in the
committed class) and run the command above. The pins differ from these only at the lines marked `// PIN — intended: …`.

```java
  // H-a
  @Test
  void approvedCreditReversal_movesCashbookAndClient() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-a");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(tenantSchema, depositId);
    var reversalId = new UUID[1];
    as(memberId, () -> harness.assertMoves(account, Expected.none(),
        () -> reversalId[0] = transactionService.reverseTransaction(depositId, "H-a: deposit banked in error").id()));
    as(approverId, () -> harness.assertMoves(account, Expected.cashbookAndClient("-5000.00"),
        () -> transactionService.approveTransaction(reversalId[0], approverId)));
    assertThat(statusOf(tenantSchema, depositId)).as("original deposit").isEqualTo("REVERSED");
  }

  // H-b (cardless tenant)
  @Test
  void debitReversalWithoutCard_movesBothSidesOrNeither() {
    var account = freshAccount(cardlessSchema, cardlessMemberId);
    var client = newClient(cardlessSchema, cardlessMemberId, "H-b");
    var matter = TestTrustMatterHelper.openClientMatter(
        cardlessSchema, transactionTemplate, projectRepository, client, "H-b matter", cardlessMemberId);
    saveSeederShapedTxn(account, "DEPOSIT", "10000.00", client, matter, "RECORDED");
    var paymentId = saveSeederShapedTxn(account, "PAYMENT", "4000.00", client, matter, "APPROVED");
    var before = new BooksSnapshot[1];
    var after = new BooksSnapshot[1];
    asIn(cardlessSchema, CARDLESS_ORG_ID, cardlessMemberId, () -> {
      before[0] = harness.snapshot(account);
      transactionService.reverseTransaction(paymentId, "H-b: paid to the wrong party");
      after[0] = harness.snapshot(account);
    });
    var moved = after[0].deltaSince(before[0]);
    assertThat(after[0].difference().subtract(before[0].difference()))
        .as("cashbook − client ledgers moved by %s (cashbook moved %s, client ledgers moved %s);"
                + " a reversal must move both sides or neither",
            after[0].difference().subtract(before[0].difference()).toPlainString(),
            moved.cashbook().toPlainString(), moved.clientLedgers().toPlainString())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  // H-c
  @Test
  void rejectedCreditReversal_leavesOriginalReversible() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-c");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(tenantSchema, depositId);
    var firstReversal = new UUID[1];
    as(memberId, () -> firstReversal[0] = transactionService.reverseTransaction(depositId, "H-c: first attempt").id());
    as(approverId, () -> harness.assertMoves(account, Expected.none(),
        () -> transactionService.rejectTransaction(firstReversal[0], approverId, "H-c: wrong deposit chosen")));
    var second = new TrustTransactionResponse[1];
    as(memberId, () -> harness.assertMoves(account, Expected.none(),
        () -> second[0] = transactionService.reverseTransaction(depositId, "H-c: second attempt")));
    assertThat(second[0].transactionType()).isEqualTo("REVERSAL");
    assertThat(second[0].status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(second[0].reversalOf()).isEqualTo(depositId);
  }

  // H-d (ruling #1)
  @Test
  void recordedDeposit_isReversible() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-d");
    var depositId = deposit(account, client, "5000.00");
    assertThat(statusOf(tenantSchema, depositId)).isEqualTo("RECORDED");
    var reversal = new TrustTransactionResponse[1];
    as(memberId, () -> harness.assertMoves(account, Expected.none(),
        () -> reversal[0] = transactionService.reverseTransaction(depositId, "H-d: banked in error")));
    assertThat(reversal[0].transactionType()).isEqualTo("REVERSAL");
    assertThat(reversal[0].status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(reversal[0].reversalOf()).isEqualTo(depositId);
  }

  // Path 7b
  @Test
  void creditReversalApproved_movesCashbookAndClient() {
    var account = freshAccount(false);
    var client = newClient("P7b");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId);
    var reversal = reverse(account, depositId, Expected.none());
    approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-5000.00"));
    assertThat(statusOf(depositId)).isEqualTo("REVERSED");
    assertServicesAgree(account);
  }

  // Path 7d
  @Test
  void creditReversalRejectedThenReversedAgain_isAccepted() {
    var account = freshAccount(false);
    var client = newClient("P7d");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId);
    var first = reverse(account, depositId, Expected.none());
    reject(account, first.id());
    var second = reverse(account, depositId, Expected.none());
    assertThat(second.status()).isEqualTo("AWAITING_APPROVAL");
    assertServicesAgree(account);
  }

  // Path 11
  @Test
  void seededDemoBooks_balance() {
    var slug = "trust-eq-seeded-" + UUID.randomUUID().toString().substring(0, 8);
    var seededSchema = provisioningService.provisionTenant(slug, "Trust Eq Seeded Org", null).schemaName();
    var orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();
    legalDemoDataSeeder.seed(seededSchema, orgId);
    var accountIds = jdbcTemplate.queryForList("SELECT id FROM \"%s\".trust_accounts".formatted(seededSchema), UUID.class);
    assertThat(accountIds).as("the seeder creates one trust account").hasSize(1);
    var snapshot = new BooksSnapshot[1];
    ScopedValue.where(RequestScopes.TENANT_ID, seededSchema).run(() -> snapshot[0] = harness.snapshot(accountIds.get(0)));
    assertThat(snapshot[0].difference())
        .as("seeded books: cashbook %s vs creditors %s (client ledgers %s)",
            snapshot[0].cashbook(), snapshot[0].creditors(), snapshot[0].clientLedgers())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }
```

(Sources shown condensed onto fewer lines; the run used the same statements under google-java-format.)

## Path 11 — measured baseline figure

Freshly seeded legal demo tenant (`LegalDemoDataSeeder.seed`, no Keycloak — the same route as `LegalDemoDataSeederTest`), harness v0:
**cashbook 27000.00, client ledgers (stored card total) 0, difference 27000.00.** This equals the expected R27 000,00 — no discrepancy.
The orchestrator can replace "DEFERRED" in the task-file header and `qa_cycle/evidence/phase92/baseline/README.md` with this figure.

## Green run after pinning (Tier 2)

commit: aecf93279 + the four new test files (pinned forms, as committed)
command: cd backend && ./mvnw test -Dtest="TrustEquationCharacterisationIntegrationTest,TrustReversalEquationIntegrationTest,LegalDemoDataSeederTest,TrustTransactionServiceTest" -q  (exit=0; with `-q` Surefire prints nothing on success, so these are line 4 of each `target/surefire-reports/<FQCN>.txt`)

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.22 s -- in io.b2mash.b2b.b2bstrawman.demo.seed.LegalDemoDataSeederTest
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.815 s -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.083 s -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.630 s -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionServiceTest
```

Total: 80 tests, 0 failures, 0 errors (21 characterisation + 4 reversal + 9 seeder + 46 existing service test). The two `hbProbe_*`
methods were removed, so the reversal class commits 4 methods. Full `./mvnw verify` (Tier 3) is owned by the orchestrator / Backend CI.
