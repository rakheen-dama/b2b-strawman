package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.demo.seed.LegalDemoDataSeeder;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 649A (task 649.2, architecture §16.9.2 paths 1–8 and 11): characterisation of
 * every existing trust posting path on the HEAD model through the {@link TrustBooksHarness} v0
 * oracle. A path that balances is asserted to balance and to move each term by exactly its amount;
 * a path that does not is pinned with the measured outcome in a method named {@code
 * …_pinsKnownDefect}, naming the slice that flips it.
 *
 * <p>Measured on {@code main} {@code aecf93279}: paths 1–6, 7a, 7c and 8 balance and move exactly
 * as expected; three paths are pinned — 7b (H-a, 649B), 7d (H-c, 649B) and 11 (seeded demo data,
 * R27 000,00, 646A). The red runs of their intended forms are in {@code
 * qa_cycle/evidence/phase92/649A/RED-RUNS.md}.
 *
 * <p>646A swaps the harness internals to the effect columns; no expectation here changes except the
 * pins it closes (path 11). 649B flips the reversal pins (paths 7b, 7d).
 *
 * <p>Paths 7 and 8 reach a reversible row only by <b>hand-setting {@code APPROVED}</b> (a deposit
 * or a {@code TRANSFER_OUT} is saved {@code RECORDED} and never becomes {@code APPROVED}); each
 * such setup is itself evidence for H-d (founder ruling #1) and is removed by 649B.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustEquationCharacterisationIntegrationTest {

  private static final String ORG_ID = "org_trust_equation_characterisation";
  private static final Set<String> TRUST_CAPABILITIES =
      Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST");
  private static final LocalDate POSTING_DATE = LocalDate.of(2026, 3, 1);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private LegalDemoDataSeeder legalDemoDataSeeder;
  @Autowired private TrustBooksHarness harness;
  @Autowired private Clock clock;

  private final AtomicInteger sequence = new AtomicInteger();

  /** Each client's trust matter (G89-F28). */
  private final Map<UUID, UUID> matters = new ConcurrentHashMap<>();

  private String tenantSchema;
  private UUID memberId;
  private UUID approverId;
  private UUID secondApproverId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Equation Characterisation Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trust_eq_owner", "trust_eq@test.com", "Eq Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_eq_approver",
                "trust_eq_approver@test.com",
                "Eq Approver",
                "owner"));
    secondApproverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_eq_second",
                "trust_eq_second@test.com",
                "Eq Second Approver",
                "owner"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  @AfterAll
  void booksBalanceFirmWide() {
    as(memberId, () -> harness.assertFirmBalanced(today()));
  }

  // --- Path 1: deposit ------------------------------------------------------------------------

  @Test
  void deposit_movesCashbookAndClient() {
    var account = freshAccount(false);
    var client = newClient("P1");
    deposit(account, client, "10000.00");
    assertServicesAgree(account);
  }

  // --- Path 2: payment ------------------------------------------------------------------------

  @Test
  void paymentRequested_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P2a");
    deposit(account, client, "10000.00");
    requestPayment(account, client, "3000.00");
    assertServicesAgree(account);
  }

  @Test
  void paymentApprovedSingleMode_movesCashbookAndClient() {
    var account = freshAccount(false);
    var client = newClient("P2b");
    deposit(account, client, "10000.00");
    var paymentId = requestPayment(account, client, "3000.00");
    approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));
    assertThat(statusOf(paymentId)).isEqualTo("APPROVED");
    assertServicesAgree(account);
  }

  @Test
  void paymentDualMode_firstApprovalMovesNothing_secondPosts() {
    var account = freshAccount(true);
    var client = newClient("P2c");
    deposit(account, client, "10000.00");
    var paymentId = requestPayment(account, client, "3000.00");
    approve(account, paymentId, approverId, Expected.none());
    assertThat(statusOf(paymentId)).isEqualTo("AWAITING_APPROVAL");
    approve(account, paymentId, secondApproverId, Expected.cashbookAndClient("-3000.00"));
    assertThat(statusOf(paymentId)).isEqualTo("APPROVED");
    assertServicesAgree(account);
  }

  @Test
  void paymentRejected_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P2d");
    deposit(account, client, "10000.00");
    var paymentId = requestPayment(account, client, "3000.00");
    reject(account, paymentId);
    assertThat(statusOf(paymentId)).isEqualTo("REJECTED");
    assertServicesAgree(account);
  }

  // --- Path 3: fee transfer -------------------------------------------------------------------

  @Test
  void feeTransferRequested_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P3a");
    deposit(account, client, "5000.00");
    requestFeeTransfer(account, client);
    assertServicesAgree(account);
  }

  @Test
  void feeTransferApproved_movesCashbookAndClient() {
    var account = freshAccount(false);
    var client = newClient("P3b");
    deposit(account, client, "5000.00");
    var fee = requestFeeTransfer(account, client);
    approve(account, fee.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.PAID);
    assertServicesAgree(account);
  }

  @Test
  void feeTransferRejected_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P3c");
    deposit(account, client, "5000.00");
    var fee = requestFeeTransfer(account, client);
    reject(account, fee.transactionId());
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.SENT);
    assertServicesAgree(account);
  }

  // --- Path 4: refund -------------------------------------------------------------------------

  @Test
  void refundRequested_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P4a");
    deposit(account, client, "8000.00");
    requestRefund(account, client, "2500.00");
    assertServicesAgree(account);
  }

  @Test
  void refundApproved_movesCashbookAndClient() {
    var account = freshAccount(false);
    var client = newClient("P4b");
    deposit(account, client, "8000.00");
    var refundId = requestRefund(account, client, "2500.00");
    approve(account, refundId, approverId, Expected.cashbookAndClient("-2500.00"));
    assertServicesAgree(account);
  }

  @Test
  void refundRejected_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P4c");
    deposit(account, client, "8000.00");
    var refundId = requestRefund(account, client, "2500.00");
    reject(account, refundId);
    assertServicesAgree(account);
  }

  // --- Path 5: inter-client transfer ----------------------------------------------------------

  @Test
  void interClientTransfer_isCashbookNeutralAndClientTotalUnchanged() {
    var account = freshAccount(false);
    var source = newClient("P5 source");
    var target = newClient("P5 target");
    deposit(account, source, "10000.00");

    var sourceBefore = clientLedger(account, source);
    var targetBefore = clientLedger(account, target);
    transfer(account, source, target, "4000.00");

    assertThat(clientLedger(account, source).subtract(sourceBefore))
        .isEqualByComparingTo("-4000.00");
    assertThat(clientLedger(account, target).subtract(targetBefore))
        .isEqualByComparingTo("4000.00");
    assertServicesAgree(account);
  }

  // --- Path 6: reversal of a debit ------------------------------------------------------------

  @Test
  void paymentReversal_movesCashbookAndClientBack() {
    var account = freshAccount(false);
    var client = newClient("P6a");
    deposit(account, client, "10000.00");
    var paymentId = requestPayment(account, client, "3000.00");
    approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));

    var reversal = reverse(account, paymentId, Expected.cashbookAndClient("3000.00"));

    assertThat(reversal.status()).isEqualTo("RECORDED");
    assertThat(statusOf(paymentId)).isEqualTo("REVERSED");
    assertServicesAgree(account);
  }

  @Test
  void feeTransferReversal_movesBackAndCascadesToFeeNote() {
    var account = freshAccount(false);
    var client = newClient("P6b");
    deposit(account, client, "5000.00");
    var fee = requestFeeTransfer(account, client);
    approve(account, fee.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.PAID);

    reverse(account, fee.transactionId(), Expected.cashbookAndClient("1150.00"));

    assertThat(statusOf(fee.transactionId())).isEqualTo("REVERSED");
    assertThat(invoiceStatusOf(fee.invoiceId()))
        .as("fee note PAID → SENT")
        .isEqualTo(InvoiceStatus.SENT);
    assertThat(completedPaymentEventsOf(fee.invoiceId()))
        .as("trust payment event removed")
        .isZero();
    assertServicesAgree(account);
  }

  @Test
  void refundReversal_movesCashbookAndClientBack() {
    var account = freshAccount(false);
    var client = newClient("P6c");
    deposit(account, client, "8000.00");
    var refundId = requestRefund(account, client, "2500.00");
    approve(account, refundId, approverId, Expected.cashbookAndClient("-2500.00"));

    reverse(account, refundId, Expected.cashbookAndClient("2500.00"));

    assertThat(statusOf(refundId)).isEqualTo("REVERSED");
    assertServicesAgree(account);
  }

  // --- Path 7: reversal of a credit -----------------------------------------------------------

  @Test
  void creditReversalRequested_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P7a");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId); // H-d evidence (ruling #1)

    var reversal = reverse(account, depositId, Expected.none());

    assertThat(reversal.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(statusOf(depositId)).isEqualTo("APPROVED");
    assertServicesAgree(account);
  }

  /**
   * Path 7b — pinned defect (same mechanism as H-a in {@code
   * TrustReversalEquationIntegrationTest}), fixed in <b>649B</b>. Measured on {@code main} {@code
   * aecf93279}: approving the credit reversal moved cashbook 0.00 and client ledgers 0.00 (books
   * stay balanced; the money simply never leaves), the original stays {@code APPROVED}.
   *
   * <p>Intended (649B flips this pin): {@code Expected.cashbookAndClient("-5000.00")} and the
   * original {@code REVERSED}.
   */
  @Test
  void creditReversalApproved_movesNothing_pinsKnownDefect() {
    var account = freshAccount(false);
    var client = newClient("P7b");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId); // H-d evidence (ruling #1)
    var reversal = reverse(account, depositId, Expected.none());

    // PIN — intended: Expected.cashbookAndClient("-5000.00")
    approve(account, reversal.id(), approverId, Expected.none());

    assertThat(statusOf(reversal.id())).isEqualTo("APPROVED");
    // PIN — intended: REVERSED
    assertThat(statusOf(depositId)).isEqualTo("APPROVED");
    assertServicesAgree(account);
  }

  @Test
  void creditReversalRejected_movesNothing() {
    var account = freshAccount(false);
    var client = newClient("P7c");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId); // H-d evidence (ruling #1)
    var reversal = reverse(account, depositId, Expected.none());

    reject(account, reversal.id());

    assertThat(statusOf(reversal.id())).isEqualTo("REJECTED");
    assertThat(statusOf(depositId)).isEqualTo("APPROVED");
    assertServicesAgree(account);
  }

  /**
   * Path 7d — pinned defect (same mechanism as H-c in {@code
   * TrustReversalEquationIntegrationTest}), fixed in <b>649B</b>. Measured on {@code main} {@code
   * aecf93279}: after the first reversal is rejected, a second reversal is refused "Transaction has
   * already been reversed" ({@code existsByReversalOf} counts the {@code REJECTED} row); nothing
   * moves.
   *
   * <p>Intended (649B flips this pin): the second reversal is accepted — a {@code REVERSAL}
   * awaiting approval, {@code Expected.none()}.
   */
  @Test
  void creditReversalRejectedThenReversedAgain_isRefused_pinsKnownDefect() {
    var account = freshAccount(false);
    var client = newClient("P7d");
    var depositId = deposit(account, client, "5000.00");
    handSetApproved(depositId); // H-d evidence (ruling #1)
    var first = reverse(account, depositId, Expected.none());
    reject(account, first.id());

    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    // PIN — intended: accepted, status AWAITING_APPROVAL.
                    assertThatThrownBy(
                            () -> transactionService.reverseTransaction(depositId, "second"))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Cannot reverse transaction");
                              assertThat(e.getBody().getDetail())
                                  .isEqualTo("Transaction has already been reversed");
                            })));
    assertServicesAgree(account);
  }

  // --- Path 8: reversal of a transfer ---------------------------------------------------------

  @Test
  void transferReversal_reversesBothLegs() {
    var account = freshAccount(false);
    var source = newClient("P8 source");
    var target = newClient("P8 target");
    deposit(account, source, "10000.00");
    var legs = transfer(account, source, target, "4000.00");
    var transferOutId = legs.get(0).id();
    var transferInId = legs.get(1).id();
    handSetApproved(transferOutId); // H-d evidence (ruling #1)

    var sourceBefore = clientLedger(account, source);
    var targetBefore = clientLedger(account, target);
    reverse(account, transferOutId, Expected.none());

    assertThat(clientLedger(account, source).subtract(sourceBefore))
        .isEqualByComparingTo("4000.00");
    assertThat(clientLedger(account, target).subtract(targetBefore))
        .isEqualByComparingTo("-4000.00");
    assertThat(statusOf(transferOutId)).isEqualTo("REVERSED");
    assertThat(statusOf(transferInId)).isEqualTo("REVERSED");
    assertServicesAgree(account);
  }

  // --- Path 11: the seeded demo data ----------------------------------------------------------

  /**
   * Path 11 — pinned defect, fixed in <b>646A</b> (R2, §16.3.15). First measurement of the baseline
   * figure (§16.1.6, previously DEFERRED): on {@code main} {@code aecf93279} a freshly seeded legal
   * demo tenant has cashbook R27 000,00 and client ledgers R0,00 — a difference of <b>R27
   * 000,00</b>, matching the inventory's expected figure. {@code LegalDemoDataSeeder.saveTrustTxn}
   * writes four {@code RECORDED} rows straight to the repository (deposits 50 000 + 15 000,
   * payments 20 000 + 18 000) and no ledger card.
   *
   * <p>Intended (646A flips this pin): the seeder posts through {@code TrustPostingService}; EQ-2
   * holds (cashbook R27 000,00 = client ledgers R30 000,00 + (−R3 000,00)) and one client is in
   * debit by R3 000,00. Seeded into a separate tenant so the imbalance cannot leak into this
   * class's firm-wide assertion.
   */
  @Test
  void seededDemoBooks_cashbookExceedsClientLedgersBy27000_pinsKnownDefect() {
    var slug = "trust-eq-seeded-" + UUID.randomUUID().toString().substring(0, 8);
    var seededSchema =
        provisioningService.provisionTenant(slug, "Trust Eq Seeded Org", null).schemaName();
    var orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();
    legalDemoDataSeeder.seed(seededSchema, orgId);

    var accountIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM \"%s\".trust_accounts".formatted(seededSchema), UUID.class);
    assertThat(accountIds).as("the seeder creates one trust account").hasSize(1);

    var snapshot = new BooksSnapshot[1];
    ScopedValue.where(RequestScopes.TENANT_ID, seededSchema)
        .run(() -> snapshot[0] = harness.snapshot(accountIds.get(0)));

    assertThat(snapshot[0].cashbook()).as("seeded cashbook").isEqualByComparingTo("27000.00");
    // PIN — intended (646A): client ledgers 27000.00 (30000.00 + -3000.00), difference 0.00.
    assertThat(snapshot[0].clientLedgers())
        .as("seeded client ledgers")
        .isEqualByComparingTo("0.00");
    assertThat(snapshot[0].difference())
        .as("seeded cashbook − creditors")
        .isEqualByComparingTo("27000.00");
  }

  // --- helpers --------------------------------------------------------------------------------

  private record FeeTransfer(UUID transactionId, UUID invoiceId) {}

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private void as(UUID member, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, member)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, TRUST_CAPABILITIES)
        .run(action);
  }

  private void assertServicesAgree(UUID account) {
    as(memberId, () -> harness.assertServicesAgree(account, today()));
  }

  private BigDecimal clientLedger(UUID account, UUID client) {
    var balance = new BigDecimal[1];
    as(memberId, () -> balance[0] = harness.clientLedger(account, client));
    return balance[0];
  }

  /** A fresh non-primary GENERAL account (a tenant holds one primary GENERAL account). */
  private UUID freshAccount(boolean dualApproval) {
    var n = sequence.incrementAndGet();
    var id = new UUID[1];
    as(
        memberId,
        () ->
            id[0] =
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "Equation " + n,
                            "First National Bank",
                            "250655",
                            "EQ-" + n,
                            "GENERAL",
                            false,
                            dualApproval,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "649A harness account"))
                    .id());
    return id[0];
  }

  /** An ACTIVE client with its own trust matter (G89-F28). */
  private UUID newClient(String label) {
    var n = sequence.incrementAndGet();
    var client =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            customerRepository
                                .save(
                                    TestCustomerFactory.createActiveCustomer(
                                        "Eq " + label + " " + n,
                                        "trust_eq_" + n + "@test.com",
                                        memberId))
                                .getId()));
    matters.put(
        client,
        TestTrustMatterHelper.openClientMatter(
            tenantSchema,
            transactionTemplate,
            projectRepository,
            client,
            "Eq matter " + n,
            memberId));
    return client;
  }

  private UUID matterOf(UUID client) {
    return matters.get(client);
  }

  private String reference(String prefix) {
    return prefix + "-EQ-" + sequence.incrementAndGet();
  }

  /** Path 1: a deposit posts immediately (RECORDED). */
  private UUID deposit(UUID account, UUID client, String amount) {
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
                                    matterOf(client),
                                    new BigDecimal(amount),
                                    reference("DEP"),
                                    "Deposit",
                                    POSTING_DATE))
                            .id()));
    assertThat(statusOf(id[0])).isEqualTo("RECORDED");
    return id[0];
  }

  /** A requested payment awaits approval and moves nothing. */
  private UUID requestPayment(UUID account, UUID client, String amount) {
    var id = new UUID[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    id[0] =
                        transactionService
                            .recordPayment(
                                account,
                                new RecordPaymentRequest(
                                    client,
                                    matterOf(client),
                                    new BigDecimal(amount),
                                    reference("PAY"),
                                    "Payment",
                                    POSTING_DATE))
                            .id()));
    assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
    return id[0];
  }

  /** A requested refund awaits approval and moves nothing. */
  private UUID requestRefund(UUID account, UUID client, String amount) {
    var id = new UUID[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    id[0] =
                        transactionService
                            .recordRefund(
                                account,
                                new RecordRefundRequest(
                                    client,
                                    matterOf(client),
                                    new BigDecimal(amount),
                                    reference("REF"),
                                    "Refund",
                                    POSTING_DATE))
                            .id()));
    assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
    return id[0];
  }

  /** A SENT fee note of R1 150,00 on the client's matter, and a requested fee transfer for it. */
  private FeeTransfer requestFeeTransfer(UUID account, UUID client) {
    var invoiceId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".invoices (id, customer_id, invoice_number, status, currency,
            subtotal, tax_amount, total, due_date,
            customer_name, org_name, payment_destination, created_by, created_at, updated_at)
        VALUES (?, ?, ?, 'SENT', 'ZAR', 1000.00, 150.00, 1150.00, '2026-05-30',
            'Eq Client', 'Test Org', 'OPERATING', ?, now(), now())
        """
            .formatted(tenantSchema),
        invoiceId,
        client,
        reference("INV"),
        memberId);
    jdbcTemplate.update(
        """
        INSERT INTO "%s".invoice_lines (id, invoice_id, project_id, line_type,
            description, quantity, unit_price, amount, sort_order, tax_exempt,
            created_at, updated_at)
        VALUES (gen_random_uuid(), ?, ?, 'TIME', 'Eq work', 1, 1150.00, 1150.00, 0, false,
            now(), now())
        """
            .formatted(tenantSchema),
        invoiceId,
        matterOf(client));

    var id = new UUID[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    id[0] =
                        transactionService
                            .recordFeeTransfer(
                                account,
                                new RecordFeeTransferRequest(
                                    client, invoiceId, new BigDecimal("1150.00"), reference("FT")))
                            .id()));
    assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
    return new FeeTransfer(id[0], invoiceId);
  }

  private List<TrustTransactionResponse> transfer(
      UUID account, UUID source, UUID target, String amount) {
    @SuppressWarnings("unchecked")
    List<TrustTransactionResponse>[] legs = new List[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    legs[0] =
                        transactionService.recordTransfer(
                            account,
                            new RecordTransferRequest(
                                source,
                                matterOf(source),
                                target,
                                matterOf(target),
                                new BigDecimal(amount),
                                reference("TRF"),
                                "Inter-client transfer",
                                POSTING_DATE))));
    assertThat(legs[0])
        .extracting(TrustTransactionResponse::transactionType)
        .containsExactly("TRANSFER_OUT", "TRANSFER_IN");
    return legs[0];
  }

  private void approve(UUID account, UUID transactionId, UUID approver, Expected expected) {
    as(
        approver,
        () ->
            harness.assertMoves(
                account,
                expected,
                () -> transactionService.approveTransaction(transactionId, approver)));
  }

  private void reject(UUID account, UUID transactionId) {
    as(
        approverId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    transactionService.rejectTransaction(
                        transactionId, approverId, "649A characterisation: rejected")));
    assertThat(statusOf(transactionId)).isEqualTo("REJECTED");
  }

  private TrustTransactionResponse reverse(UUID account, UUID transactionId, Expected expected) {
    var reversal = new TrustTransactionResponse[1];
    as(
        memberId,
        () ->
            harness.assertMoves(
                account,
                expected,
                () ->
                    reversal[0] =
                        transactionService.reverseTransaction(
                            transactionId, "649A characterisation: reversed")));
    assertThat(reversal[0].transactionType()).isEqualTo("REVERSAL");
    assertThat(reversal[0].reversalOf()).isEqualTo(transactionId);
    return reversal[0];
  }

  private void handSetApproved(UUID transactionId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var txn = transactionRepository.findById(transactionId).orElseThrow();
                      txn.setStatus("APPROVED");
                      transactionRepository.save(txn);
                    }));
  }

  private String statusOf(UUID transactionId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> transactionRepository.findById(transactionId).orElseThrow().getStatus()));
  }

  private InvoiceStatus invoiceStatusOf(UUID invoiceId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> invoiceRepository.findById(invoiceId).orElseThrow().getStatus()));
  }

  private int completedPaymentEventsOf(UUID invoiceId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        paymentEventRepository
                            .findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED)
                            .size()));
  }
}
