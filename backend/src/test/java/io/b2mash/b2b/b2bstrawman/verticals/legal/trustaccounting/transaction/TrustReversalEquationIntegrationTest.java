package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 649A (task 649.3, architecture §16.3.11, R8): the four reversal hypotheses
 * H-a..H-d, each reproduced red-first on {@code main} against the {@link TrustBooksHarness} oracle.
 * All four reproduced; each is committed as a pinned defect (the assertion states today's wrong
 * outcome, the method name ends {@code _pinsKnownDefect}, the Javadoc states the intended behaviour
 * and the fixing slice). The intended-behaviour versions and their red runs are in {@code
 * qa_cycle/evidence/phase92/649A/RED-RUNS.md}. 649B flips H-a, H-c and H-d; 646A flips H-b.
 *
 * <p>Two tenants: the main tenant holds H-a, H-c and H-d and ends with {@code
 * assertFirmBalanced(today)}; H-b deliberately leaves an account out of balance (cashbook ≠ client
 * ledgers), so it runs in a second, separate tenant that the firm-wide assertion does not cover.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReversalEquationIntegrationTest {

  private static final String ORG_ID = "org_trust_reversal_equation";
  private static final String CARDLESS_ORG_ID = "org_trust_reversal_cardless";
  private static final Set<String> TRUST_CAPABILITIES =
      Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST");
  private static final LocalDate DEPOSIT_DATE = LocalDate.of(2026, 3, 20);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustBooksHarness harness;
  @Autowired private Clock clock;

  private final AtomicInteger sequence = new AtomicInteger();

  private String tenantSchema;
  private UUID memberId;
  private UUID approverId;

  private String cardlessSchema;
  private UUID cardlessMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Reversal Equation Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_rev_eq_owner", "rev_eq@test.com", "Rev Eq Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rev_eq_approver",
                "rev_eq_approver@test.com",
                "Rev Eq Approver",
                "owner"));
    enableTrustModule(tenantSchema);

    cardlessSchema =
        provisioningService
            .provisionTenant(CARDLESS_ORG_ID, "Trust Reversal Cardless Org", null)
            .schemaName();
    cardlessMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                CARDLESS_ORG_ID,
                "user_rev_cardless_owner",
                "rev_cardless@test.com",
                "Rev Cardless Owner",
                "owner"));
    enableTrustModule(cardlessSchema);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    as(memberId, () -> harness.assertFirmBalanced(LocalDate.now(clock)));
  }

  // --- H-a ------------------------------------------------------------------------------------

  /**
   * H-a — pinned defect, fixed in <b>649B</b> (contra reversal model). Reproduced red on {@code
   * main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: approving a credit reversal moves neither the cashbook nor the client's ledger, and
   * the original stays {@code APPROVED} with no {@code reversedById} — {@code
   * performApprovalCompletion} skips its balance block for {@code REVERSAL} (not in {@code
   * DEBIT_TYPES}) and nothing marks the original; the cashbook sign table counts {@code REVERSAL}
   * as 0.
   *
   * <p>Intended (649B flips this pin): the approval step moves {@code
   * Expected.cashbookAndClient("-5000.00")} and the original is {@code REVERSED}; refused at
   * approval if the matter cell no longer covers the amount.
   */
  @Test
  void approvedCreditReversal_movesNeitherCashbookNorClient_pinsKnownDefect() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-a");
    var depositId = deposit(account, client, "5000.00");
    // H-d evidence (ruling #1): a deposit is never APPROVED through the product, so the only way
    // to reach a reversible credit on HEAD is to hand-set the status, as
    // TrustTransactionServiceTest.reverseCreditTransaction_requiresApproval does.
    handSetApproved(tenantSchema, depositId);

    var reversalId = new UUID[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    reversalId[0] =
                        transactionService
                            .reverseTransaction(depositId, "H-a: deposit banked in error")
                            .id()));

    as(
        approverId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(), // PIN — intended: Expected.cashbookAndClient("-5000.00")
                () -> transactionService.approveTransaction(reversalId[0], approverId)));

    assertThat(statusOf(tenantSchema, reversalId[0])).as("the reversal").isEqualTo("APPROVED");
    // PIN — intended: the original is REVERSED and points at its reversal.
    assertThat(statusOf(tenantSchema, depositId)).as("original deposit").isEqualTo("APPROVED");
    assertThat(reversedByOf(tenantSchema, depositId)).as("original deposit reversedById").isNull();
  }

  // --- H-b ------------------------------------------------------------------------------------

  /**
   * H-b — pinned defect, <b>structural — closed by derived balances</b> in <b>646A</b>. Reproduced
   * red on {@code main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>A posted debit for a client with <b>no ledger card</b> is written straight to the
   * repository, with the constructor {@code LegalDemoDataSeeder.saveTrustTxn} uses — but {@code
   * APPROVED}, because the seeder's {@code RECORDED} is refused by the reversal guard. Today the
   * reversal sets the original {@code REVERSED} (it drops out of the cashbook, which rises by the
   * amount) but credits a card only if one exists, so {@code cashbook − client ledgers} grows by
   * the amount.
   *
   * <p><b>Reproduces only through direct repository writes.</b> Two service-only routes were probed
   * on {@code main} and both are refused (probes recorded in RED-RUNS.md, not committed): (1) the
   * seeder's own {@code RECORDED} payment → {@code reverseTransaction} refused "Transaction must be
   * in APPROVED status to be reversed"; (2) {@code recordPayment} for a client with no card then
   * {@code approveTransaction} → refused "Insufficient trust balance … Available: R0,00". Cards are
   * never deleted, so no service path produces a posted debit without a card.
   *
   * <p>Intended (646A flips this pin — the card is gone and client ledgers are derived from the
   * rows): a reversal moves both sides or neither, {@code difference()} unchanged. Runs in a
   * separate tenant because it leaves the account out of balance.
   */
  @Test
  void debitReversalWithoutCard_movesCashbookOnly_pinsKnownDefect() {
    var account = freshAccount(cardlessSchema, cardlessMemberId);
    var client = newClient(cardlessSchema, cardlessMemberId, "H-b");
    var matter =
        TestTrustMatterHelper.openClientMatter(
            cardlessSchema,
            transactionTemplate,
            projectRepository,
            client,
            "H-b matter",
            cardlessMemberId);
    saveSeederShapedTxn(account, "DEPOSIT", "10000.00", client, matter, "RECORDED");
    var paymentId = saveSeederShapedTxn(account, "PAYMENT", "4000.00", client, matter, "APPROVED");

    var before = new BooksSnapshot[1];
    var after = new BooksSnapshot[1];
    asIn(
        cardlessSchema,
        CARDLESS_ORG_ID,
        cardlessMemberId,
        () -> {
          before[0] = harness.snapshot(account);
          transactionService.reverseTransaction(paymentId, "H-b: paid to the wrong party");
          after[0] = harness.snapshot(account);
        });

    var moved = after[0].deltaSince(before[0]);
    // PIN — intended: the difference does not move (both sides move, or neither).
    assertThat(moved.cashbook()).as("cashbook moved").isEqualByComparingTo("4000.00");
    assertThat(moved.clientLedgers()).as("client ledgers moved").isEqualByComparingTo("0.00");
    assertThat(after[0].difference().subtract(before[0].difference()))
        .as("cashbook − client ledgers moved")
        .isEqualByComparingTo("4000.00");
  }

  // --- H-c ------------------------------------------------------------------------------------

  /**
   * H-c — pinned defect, fixed in <b>649B</b>. Reproduced red on {@code main} {@code aecf93279}
   * (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: once a credit reversal is rejected the original can never be reversed again — {@code
   * existsByReversalOf} counts the {@code REJECTED} contra row, so the second attempt is refused
   * "Transaction has already been reversed".
   *
   * <p>Intended (649B flips this pin): a rejected reversal leaves the original reversible — the
   * already-reversed check ignores {@code REJECTED} contra rows; the second attempt returns a
   * {@code REVERSAL} awaiting approval for the same original, and moves nothing.
   */
  @Test
  void rejectedCreditReversal_blocksLaterReversal_pinsKnownDefect() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-c");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(tenantSchema, depositId); // H-d evidence (ruling #1)

    var firstReversal = new UUID[1];
    as(
        memberId,
        () ->
            firstReversal[0] =
                transactionService.reverseTransaction(depositId, "H-c: first attempt").id());
    as(
        approverId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    transactionService.rejectTransaction(
                        firstReversal[0], approverId, "H-c: wrong deposit chosen")));

    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    // PIN — intended: accepted, a REVERSAL AWAITING_APPROVAL for depositId.
                    assertRefused(
                        () -> transactionService.reverseTransaction(depositId, "H-c: second"),
                        "Transaction has already been reversed")));
  }

  // --- H-d ------------------------------------------------------------------------------------

  /**
   * H-d — pinned defect by founder ruling #1 (§16.12.2), fixed in <b>649B</b>. Reproduced red on
   * {@code main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: a deposit recorded through the service stays {@code RECORDED} and is refused by the
   * reversal guard "Transaction must be in APPROVED status to be reversed" — so the only credit a
   * firm can record can never be reversed through the product.
   *
   * <p>Intended (649B flips this pin): any posted row ({@code RECORDED} or {@code APPROVED}) is
   * reversible under its registry rule; reversing the deposit returns a {@code REVERSAL} {@code
   * AWAITING_APPROVAL} (a credit — maker-checker unchanged) for the deposit, and moves nothing yet.
   */
  @Test
  void recordedDeposit_cannotBeReversed_pinsKnownDefect() {
    var account = freshAccount();
    var client = newClient(tenantSchema, memberId, "H-d");
    var depositId = deposit(account, client, "5000.00");
    assertThat(statusOf(tenantSchema, depositId)).isEqualTo("RECORDED");

    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    // PIN — intended: accepted, a REVERSAL AWAITING_APPROVAL for depositId.
                    assertRefused(
                        () -> transactionService.reverseTransaction(depositId, "H-d: banked"),
                        "Transaction must be in APPROVED status to be reversed")));
  }

  // --- helpers --------------------------------------------------------------------------------

  private void enableTrustModule(String schema) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  private void as(UUID member, Runnable action) {
    asIn(tenantSchema, ORG_ID, member, action);
  }

  private static void asIn(String schema, String orgId, UUID member, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, member)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, TRUST_CAPABILITIES)
        .run(action);
  }

  /** A fresh single-approval, non-primary GENERAL account in the main tenant. */
  private UUID freshAccount() {
    return freshAccount(tenantSchema, memberId);
  }

  private UUID freshAccount(String schema, UUID member) {
    var n = sequence.incrementAndGet();
    var id = new UUID[1];
    asIn(
        schema,
        schema.equals(tenantSchema) ? ORG_ID : CARDLESS_ORG_ID,
        member,
        () ->
            id[0] =
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "Reversal Equation " + n,
                            "First National Bank",
                            "250655",
                            "REVEQ-" + n,
                            "GENERAL",
                            false,
                            false,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "649A harness account"))
                    .id());
    return id[0];
  }

  /** An ACTIVE client; in the main tenant it also gets its trust matter (G89-F28). */
  private UUID newClient(String schema, UUID createdBy, String label) {
    var n = sequence.incrementAndGet();
    var id =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            customerRepository
                                .save(
                                    TestCustomerFactory.createActiveCustomer(
                                        "Rev Eq " + label + " " + n,
                                        "rev_eq_" + n + "@test.com",
                                        createdBy))
                                .getId()));
    return id;
  }

  private UUID matterOf(UUID client) {
    return TestTrustMatterHelper.openClientMatter(
        tenantSchema, transactionTemplate, projectRepository, client, "Matter " + client, memberId);
  }

  /** A deposit recorded through the service (status RECORDED), dated in the past. */
  private UUID deposit(UUID account, UUID client, String amount) {
    var matter = matterOf(client);
    var id = new UUID[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.cashbookAndClient(amount),
                () ->
                    id[0] =
                        transactionService
                            .recordDeposit(
                                account,
                                new RecordDepositRequest(
                                    client,
                                    matter,
                                    new BigDecimal(amount),
                                    "DEP-REVEQ-" + sequence.incrementAndGet(),
                                    "Deposit",
                                    DEPOSIT_DATE))
                            .id()));
    return id[0];
  }

  private void handSetApproved(String schema, UUID transactionId) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var txn = transactionRepository.findById(transactionId).orElseThrow();
                      txn.setStatus("APPROVED");
                      transactionRepository.save(txn);
                    }));
  }

  private static void assertRefused(Runnable action, String detail) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getTitle()).isEqualTo("Cannot reverse transaction");
              assertThat(e.getBody().getDetail()).isEqualTo(detail);
            });
  }

  private UUID reversedByOf(String schema, UUID transactionId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        transactionRepository
                            .findById(transactionId)
                            .orElseThrow()
                            .getReversedById()));
  }

  private String statusOf(String schema, UUID transactionId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> transactionRepository.findById(transactionId).orElseThrow().getStatus()));
  }

  /**
   * Writes a row straight to the repository in the cardless tenant, with the constructor {@code
   * LegalDemoDataSeeder.saveTrustTxn} uses — no ledger card, no approval flow.
   */
  private UUID saveSeederShapedTxn(
      UUID account, String type, String amount, UUID client, UUID matter, String status) {
    return ScopedValue.where(RequestScopes.TENANT_ID, cardlessSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        transactionRepository
                            .save(
                                new TrustTransaction(
                                    account,
                                    type,
                                    new BigDecimal(amount),
                                    client,
                                    matter,
                                    null,
                                    "%s-%s".formatted(type.substring(0, 3), DEPOSIT_DATE),
                                    "H-b seeder-shaped row",
                                    DEPOSIT_DATE,
                                    status,
                                    cardlessMemberId))
                            .getId()));
  }
}
