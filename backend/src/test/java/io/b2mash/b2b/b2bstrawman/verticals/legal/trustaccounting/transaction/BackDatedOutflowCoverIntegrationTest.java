package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/**
 * Phase 92 residuals §1.1 / §1.2 (founder ruling D-1, 2026-11-23; extends S2 / FU-5 D-1 and
 * F-646D-1 D-1): every client-ledger outflow dated before today must be covered by the client
 * balance, and by the matter cell, at their lowest point from the outflow's date on — not only as
 * at today. An outflow dated today is checked as before, with the same message. The refusal is 400
 * "Insufficient trust balance", naming "as at" the (earliest) date of the lowest balance; a matter
 * refusal keeps its properties.
 *
 * <p>One case per outflow, each back-dated and short on an intermediate date: {@code PAYMENT} and
 * {@code REFUND} (at approval), {@code TRANSFER_OUT} (posts at once), the section 86(4) placement
 * {@code INV_PLACE} (at the request and at approval), the receiving client's side of a transfer
 * reversal, and the client's side of an unidentified-deposit allocation's reversal. Then: covered
 * on every date → approved; dated today → as before.
 *
 * <p>Every case uses a fresh account, so a client's history is only what the case posts. The class
 * ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackDatedOutflowCoverIntegrationTest {

  private static final String ORG_ID = "org_trust_backdated_outflow_cover";
  private static final String TITLE = "Insufficient trust balance";
  private static final String MATTER_CODE = "INSUFFICIENT_MATTER_TRUST_BALANCE";

  private static final LocalDate FIRST_DEPOSIT = LocalDate.of(2026, 3, 1);
  private static final LocalDate OUTFLOW_DATE = LocalDate.of(2026, 3, 5);
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 3, 10);
  private static final LocalDate LATER_DEPOSIT = LocalDate.of(2026, 3, 15);

  /** Deposit R1 500 → payment R1 000 → deposit R1 000: R1 500 today, R500 as at 10–14 March. */
  private static final String DIP_SHORT_DETAIL =
      " as at 10 March 2026. Available: R500,00, Requested: R1\u00a0150,00";

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private UnidentifiedDepositService unidentifiedDepositService;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Back-dated Outflow Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_out_owner",
                "backdated_out_owner@test.com",
                "Back-dated Outflow Owner",
                "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_out_approver",
                "backdated_out_approver@test.com",
                "Back-dated Outflow Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- PAYMENT ------------------------------------------------------------------------------

  /**
   * The dip history: R1 500 today and as at 5 March, but R500 as at 10 March. A payment of R1 150
   * dated 5 March would leave the client R650 in debit as at 10 to 14 March. Refused at approval,
   * naming 10 March; nothing moves.
   */
  @Test
  void backDatedPayment_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 payment dip");
    dipHistory(account, client);
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("1500.00");

    var payment = books.requestPayment(account, client, "1150.00", OUTFLOW_DATE);

    books.assertApprovalRefused(
        account,
        payment,
        approverId,
        TITLE,
        "Insufficient trust balance for " + client.name() + DIP_SHORT_DETAIL);
    assertThat(books.statusOf(payment)).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * The matter cell: the client's other matter holds R2 000 from 1 March, so the client covers the
   * payment on every date; the payment's matter has the dip history (R500 as at 10 March). Refused
   * by the matter guard (G89-F12), naming 10 March, with its properties.
   */
  @Test
  void backDatedPayment_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 payment matter dip");
    var otherMatter = books.onNewMatter(client);
    books.deposit(account, otherMatter, "2000.00", FIRST_DEPOSIT);
    dipHistory(account, client);

    var payment = books.requestPayment(account, client, "1150.00", OUTFLOW_DATE);

    assertMatterRefused(
        account,
        () -> transactionService.approveTransaction(payment, approverId),
        approverId,
        client,
        "10 March 2026",
        "R500,00",
        "500.00",
        "1150.00");
    assertThat(books.statusOf(payment)).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * Covered on every date: deposits of R1 500 (1 March) and R1 000 (3 March), payment R1 000 (10
   * March). From 5 March the lowest balance is R1 500, so a payment of R1 150 dated 5 March is
   * approved and posts on 5 March.
   */
  @Test
  void backDatedPayment_isApproved_whenCoveredOnEveryDateFromItsDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 payment covered");
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    books.deposit(account, client, "1000.00", LocalDate.of(2026, 3, 3));
    pay(account, client, "1000.00", PAYMENT_DATE);

    var payment = books.requestPayment(account, client, "1150.00", OUTFLOW_DATE);
    books.approve(account, payment, approverId, Expected.cashbookAndClient("-1150.00"));

    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    assertThat(books.clientLedgerAsAt(account, client, OUTFLOW_DATE.minusDays(1)))
        .isEqualByComparingTo("2500.00");
    assertThat(books.clientLedgerAsAt(account, client, OUTFLOW_DATE))
        .isEqualByComparingTo("1350.00");
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("350.00");
    books.assertBalancedAsAt(account, OUTFLOW_DATE);
    books.assertBalancedAsAt(account, books.today());
  }

  /** A payment dated today on the dip history lowers no earlier balance: approved as before. */
  @Test
  void paymentDatedToday_isApproved_whenTheClientCoversItToday() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 payment today approved");
    dipHistory(account, client);

    var payment = books.requestPayment(account, client, "1150.00", books.today());
    books.approve(account, payment, approverId, Expected.cashbookAndClient("-1150.00"));

    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("350.00");
  }

  /** A payment dated today that today's balance cannot cover: today's message, unchanged. */
  @Test
  void paymentDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 payment today refused");
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    pay(account, client, "1000.00", PAYMENT_DATE);

    var payment = books.requestPayment(account, client, "1150.00", books.today());

    books.assertApprovalRefused(
        account,
        payment,
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + ". Available: R500,00, Requested: R1\u00a0150,00");
    assertThat(books.statusOf(payment)).isEqualTo("AWAITING_APPROVAL");
  }

  // --- REFUND -------------------------------------------------------------------------------

  /** A refund dated 5 March on the dip history: refused at approval, naming 10 March. */
  @Test
  void backDatedRefund_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 refund dip");
    dipHistory(account, client);

    var refund = books.requestRefund(account, client, "1150.00", OUTFLOW_DATE);

    books.assertApprovalRefused(
        account,
        refund,
        approverId,
        TITLE,
        "Insufficient trust balance for " + client.name() + DIP_SHORT_DETAIL);
    assertThat(books.statusOf(refund)).isEqualTo("AWAITING_APPROVAL");
  }

  // --- TRANSFER_OUT -------------------------------------------------------------------------

  /**
   * A transfer of R1 150 from a source with the dip history, dated 5 March: it posts at once, so
   * the refusal is at record time, naming 10 March; nothing moves on either client.
   */
  @Test
  void backDatedTransfer_isRefused_whenTheSourceWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var source = books.newClient("D-1 transfer source dip");
    var target = books.newClient("D-1 transfer target");
    dipHistory(account, source);

    assertRefused(
        account,
        ownerId,
        () -> transfer(account, source, target, "1150.00", OUTFLOW_DATE),
        "Insufficient trust balance for " + source.name() + DIP_SHORT_DETAIL);
    assertThat(books.clientLedger(account, target)).isEqualByComparingTo("0");
  }

  /**
   * The source's other matter holds R2 000, so the source client covers on every date; the
   * transfer's source matter has the dip history. Refused by the matter guard, naming 10 March.
   */
  @Test
  void backDatedTransfer_isRefused_whenTheSourceMatterCellWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var source = books.newClient("D-1 transfer source matter dip");
    var otherMatter = books.onNewMatter(source);
    var target = books.newClient("D-1 transfer target matter");
    books.deposit(account, otherMatter, "2000.00", FIRST_DEPOSIT);
    dipHistory(account, source);

    assertMatterRefused(
        account,
        () -> transfer(account, source, target, "1150.00", OUTFLOW_DATE),
        ownerId,
        source,
        "10 March 2026",
        "R500,00",
        "500.00",
        "1150.00");
  }

  // --- INV_PLACE (section 86(4) placement) ---------------------------------------------------

  /**
   * The request: the client's only deposit (R2 000) lands on 10 March; a placement of R1 150 dated
   * 5 March draws on it before it arrived. Refused at the request by the matter-cell check, naming
   * 5 March; nothing is saved and nothing moves.
   */
  @Test
  void backDatedPlacementRequest_isRefused_whenTheMatterCellIsEmptyAsAtItsDate() {
    var account = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("D-1 placement request");
    books.deposit(account, client, "2000.00", PAYMENT_DATE);

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                investmentAccount,
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () ->
                                investmentService.placeInvestment(
                                    account,
                                    placement(investmentAccount, client, "1150.00", OUTFLOW_DATE)))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(TITLE);
                              assertThat(e.getBody().getDetail())
                                  .startsWith("Insufficient trust balance on matter ")
                                  .endsWith(
                                      " for "
                                          + client.name()
                                          + " as at 5 March 2026. Available on matter: R0,00,"
                                          + " Requested: R1\u00a0150,00");
                            })));
  }

  /**
   * The approval: deposit R1 500 on 1 March; a placement of R1 150 dated 5 March is requested
   * (covered from 5 March then); a payment of R1 000 on 10 March and a deposit of R1 000 on 15
   * March follow. Today the client holds R1 500, but as at 10 March only R500 — the approval is
   * refused, naming 10 March; nothing moves on either account.
   */
  @Test
  void backDatedPlacementApproval_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("D-1 placement approval");
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    var placed =
        books.requestPlacement(account, investmentAccount, client, "1150.00", OUTFLOW_DATE);
    pay(account, client, "1000.00", PAYMENT_DATE);
    books.deposit(account, client, "1000.00", LATER_DEPOSIT);

    books.assertApprovalRefused(
        account,
        placed.depositTransactionId(),
        approverId,
        TITLE,
        "Insufficient trust balance for " + client.name() + DIP_SHORT_DETAIL);
    assertThat(books.statusOf(placed.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.clientInvestmentLedger(investmentAccount, client)).isEqualByComparingTo("0");
  }

  // --- the pair reversals (receiving client's side) ------------------------------------------

  /**
   * A transfer of R500 from A to B on 1 March; B pays R400 on 10 March and receives R1 000 on 15
   * March. Reversing the transfer as at 5 March takes R500 off B from 5 March on — B holds R100 as
   * at 10 March. Refused, naming 10 March; nothing moves.
   */
  @Test
  void backDatedTransferReversal_isRefused_whenTheTargetWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var source = books.newClient("D-1 reversal source");
    var target = books.newClient("D-1 reversal target");
    books.deposit(account, source, "500.00", FIRST_DEPOSIT);
    var legs = transfer(account, source, target, "500.00", FIRST_DEPOSIT);
    pay(account, target, "400.00", PAYMENT_DATE);
    books.deposit(account, target, "1000.00", LATER_DEPOSIT);
    assertThat(books.clientLedger(account, target)).isEqualByComparingTo("1100.00");

    assertRefused(
        account,
        ownerId,
        () -> reverse(legs.getFirst().id(), OUTFLOW_DATE),
        "Insufficient trust balance for "
            + target.name()
            + " as at 10 March 2026. Available: R100,00, Requested: R500,00");
    assertThat(books.statusOf(legs.getFirst().id())).isEqualTo("RECORDED");
  }

  /**
   * An unidentified deposit of R500 received and allocated to the client on 1 March; the client
   * pays R400 on 10 March and receives R1 000 on 15 March. Undoing the allocation as at 5 March
   * takes R500 off the client from 5 March on (R100 as at 10 March). Refused, naming 10 March; the
   * item stays allocated and nothing moves.
   */
  @Test
  void backDatedAllocationReversal_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("D-1 allocation reversal");
    var depositRow = books.unidentifiedDepositDirect(account, "500.00", FIRST_DEPOSIT);
    var itemId =
        jdbc.queryForObject(
            "SELECT unidentified_deposit_id FROM \"%s\".trust_transactions WHERE id = ?"
                .formatted(books.schema()),
            UUID.class,
            depositRow);
    var allocation = new UUID[1];
    books.as(
        ownerId,
        () ->
            allocation[0] =
                unidentifiedDepositService
                    .allocate(
                        itemId,
                        new AllocateRequest(client.id(), client.matter(), FIRST_DEPOSIT, null))
                    .resolutionTransactionId());
    books.as(approverId, () -> transactionService.approveTransaction(allocation[0], approverId));
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("500.00");
    pay(account, client, "400.00", PAYMENT_DATE);
    books.deposit(account, client, "1000.00", LATER_DEPOSIT);

    assertRefused(
        account,
        ownerId,
        () -> reverse(allocation[0], OUTFLOW_DATE),
        "Insufficient trust balance for "
            + client.name()
            + " as at 10 March 2026. Available: R100,00, Requested: R500,00");
    assertThat(books.statusOf(allocation[0])).isEqualTo("APPROVED");
  }

  // --- helpers --------------------------------------------------------------------------------

  /** Deposit R1 500 on 1 March, payment R1 000 on 10 March, deposit R1 000 on 15 March. */
  private void dipHistory(UUID account, Client client) {
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    pay(account, client, "1000.00", PAYMENT_DATE);
    books.deposit(account, client, "1000.00", LATER_DEPOSIT);
  }

  /** A payment dated {@code date}, requested and approved: cashbook and client fall together. */
  private void pay(UUID account, Client client, String amount, LocalDate date) {
    var paymentId = books.requestPayment(account, client, amount, date);
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-" + amount));
  }

  /** An inter-client transfer dated {@code date} (the caller binds the request scope). */
  private List<TrustTransactionResponse> transfer(
      UUID account, Client source, Client target, String amount, LocalDate date) {
    @SuppressWarnings("unchecked")
    List<TrustTransactionResponse>[] legs = new List[1];
    books.as(
        ownerId,
        () ->
            legs[0] =
                transactionService.recordTransfer(
                    account,
                    new RecordTransferRequest(
                        source.id(),
                        source.matter(),
                        target.id(),
                        target.matter(),
                        new BigDecimal(amount),
                        "D1-TRF-" + SEQUENCE.incrementAndGet(),
                        "Inter-client transfer",
                        date)));
    return legs[0];
  }

  private void reverse(UUID transactionId, LocalDate reversalDate) {
    books.as(
        ownerId,
        () ->
            transactionService.reverseTransaction(
                transactionId, new ReverseRequest("D-1 back-dated reversal", reversalDate)));
  }

  private PlaceInvestmentRequest placement(
      UUID investmentAccount, Client client, String principal, LocalDate depositDate) {
    return new PlaceInvestmentRequest(
        investmentAccount,
        client.id(),
        client.matter(),
        "Trust Books Bank",
        "D1-INV-" + SEQUENCE.incrementAndGet(),
        new BigDecimal(principal),
        new BigDecimal("0.0500"),
        depositDate,
        null,
        null,
        InvestmentBasis.CLIENT_INSTRUCTION);
  }

  /** {@code action}, run as {@code member}, is refused 400 {@link #TITLE} / {@code detail}. */
  private void assertRefused(UUID account, UUID member, ThrowingCallable action, String detail) {
    books.as(
        member,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    assertThatThrownBy(action)
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(TITLE);
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
  }

  /**
   * {@code action}, run as {@code member}, is refused by the matter sub-ledger guard: 400, the
   * shared title, a detail naming {@code asAt} and {@code availableText}, and the unchanged
   * properties; nothing moves.
   */
  private void assertMatterRefused(
      UUID account,
      ThrowingCallable action,
      UUID member,
      Client client,
      String asAt,
      String availableText,
      String available,
      String requested) {
    books.as(
        member,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    assertThatThrownBy(action)
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              var body = e.getBody();
                              assertThat(body.getStatus()).isEqualTo(400);
                              assertThat(body.getTitle()).isEqualTo(TITLE);
                              assertThat(body.getDetail())
                                  .startsWith("Insufficient trust balance on matter ")
                                  .endsWith(
                                      " for "
                                          + client.name()
                                          + " as at "
                                          + asAt
                                          + ". Available on matter: "
                                          + availableText
                                          + ", Requested: R1\u00a0150,00");
                              var properties = body.getProperties();
                              assertThat(properties).isNotNull();
                              assertThat(properties.get("code")).isEqualTo(MATTER_CODE);
                              assertThat(properties.get("matterId")).isEqualTo(client.matter());
                              assertThat((BigDecimal) properties.get("available"))
                                  .isEqualByComparingTo(available);
                              assertThat((BigDecimal) properties.get("requested"))
                                  .isEqualByComparingTo(requested);
                            })));
  }
}
