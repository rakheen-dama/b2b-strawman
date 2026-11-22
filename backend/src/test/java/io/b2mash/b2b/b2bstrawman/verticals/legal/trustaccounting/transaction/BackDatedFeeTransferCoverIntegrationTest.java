package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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

/**
 * F-646D-1 (Phase 92 646D, orchestrator ruling D8; default F-646D-1 D-1 by analogy with founder
 * ruling S2 / FU-5 D-1): a fee transfer is saved awaiting approval and, on approval, posts on its
 * own transaction date. A back-dated one (dated before today) lowers the client's balance — and the
 * matter cell's — as at that date and every later date, so it is approved only if the client and
 * the matter cell cover it at their lowest point from its date on; the refusal names the date of
 * that lowest balance. A fee transfer dated today is checked as before.
 *
 * <p>Every case uses a fresh account, so a client's history is only what the case posts. The fee
 * note (fixture) is R1 150,00 on the client's first matter, and the transfer is for its whole
 * amount. The class ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackDatedFeeTransferCoverIntegrationTest {

  private static final String ORG_ID = "org_trust_backdated_fee_transfer_cover";
  private static final String TITLE = "Insufficient trust balance";
  private static final String MATTER_CODE = "INSUFFICIENT_MATTER_TRUST_BALANCE";

  private static final LocalDate FIRST_DEPOSIT = LocalDate.of(2026, 3, 1);
  private static final LocalDate TRANSFER_DATE = LocalDate.of(2026, 3, 5);
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 3, 10);
  private static final LocalDate LATER_DEPOSIT = LocalDate.of(2026, 3, 15);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustTransactionService transactionService;

  private TrustBooksFixture.Tenant books;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Back-dated Fee Transfer Org", null)
            .schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_ft_owner",
                "backdated_ft_owner@test.com",
                "Back-dated Fee Transfer Owner",
                "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_ft_approver",
                "backdated_ft_approver@test.com",
                "Back-dated Fee Transfer Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * The defect (646D F-646D-1): the only deposit lands on 10 March; a fee transfer dated 5 March
   * draws on it before it arrived. Today the client holds R2 000 (it covers R1 150), but as at 5
   * March it held nothing. Refused, naming 5 March; nothing moves.
   */
  @Test
  void backDatedFeeTransfer_isRefused_whenDatedBeforeTheDepositItDrawsOn() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 before deposit");
    books.deposit(account, client, "2000.00", PAYMENT_DATE);
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("2000.00");

    var transfer = books.requestFeeTransfer(account, client, TRANSFER_DATE);

    books.assertApprovalRefused(
        account,
        transfer.transactionId(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + " as at 5 March 2026. Available: R0,00, Requested: R1 150,00");
    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.clientLedgerAsAt(account, client, TRANSFER_DATE)).isEqualByComparingTo("0");
  }

  /**
   * An intermediate dip: deposit R1 500 on 1 March, payment R1 000 on 10 March, deposit R1 000 on
   * 15 March. Today and as at 5 March the client holds R1 500, but as at 10 March only R500, so a
   * fee transfer dated 5 March would leave its ledger R650 in debit as at 10 to 14 March. Refused,
   * naming 10 March.
   */
  @Test
  void backDatedFeeTransfer_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 client dip");
    dipHistory(account, client);
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("1500.00");

    var transfer = books.requestFeeTransfer(account, client, TRANSFER_DATE);

    books.assertApprovalRefused(
        account,
        transfer.transactionId(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + " as at 10 March 2026. Available: R500,00, Requested: R1 150,00");
    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("500.00");
  }

  /**
   * The matter cell, before the deposit: the client's other matter holds R2 000 from 1 March, so
   * the client covers the transfer on every date; the fee note's matter receives its R2 000 only on
   * 10 March. A transfer dated 5 March is refused by the matter sub-ledger guard (G89-F12), naming
   * 5 March, with its code and properties.
   */
  @Test
  void backDatedFeeTransfer_isRefused_whenTheMatterCellIsEmptyAsAtItsDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 matter before deposit");
    var otherMatter = books.onNewMatter(client);
    books.deposit(account, otherMatter, "2000.00", FIRST_DEPOSIT);
    books.deposit(account, client, "2000.00", PAYMENT_DATE);

    var transfer = books.requestFeeTransfer(account, client, TRANSFER_DATE);

    assertMatterRefused(account, transfer, client, "5 March 2026", "R0,00", "0");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("4000.00");
  }

  /**
   * The matter cell, an intermediate dip: the client's other matter holds R2 000, so the client
   * covers the transfer on every date; the fee note's matter has the dip history and holds only
   * R500 as at 10 March. Refused by the matter guard, naming 10 March; nothing moves.
   */
  @Test
  void backDatedFeeTransfer_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 matter dip");
    var otherMatter = books.onNewMatter(client);
    books.deposit(account, otherMatter, "2000.00", FIRST_DEPOSIT);
    dipHistory(account, client);

    var transfer = books.requestFeeTransfer(account, client, TRANSFER_DATE);

    assertMatterRefused(account, transfer, client, "10 March 2026", "R500,00", "500.00");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("3500.00");
  }

  /**
   * Covered on every date: deposits of R1 500 on 1 March and R1 000 on 3 March, payment R1 000 on
   * 10 March. From 5 March the client's lowest balance is R1 500 (10 March), so a transfer dated 5
   * March is approved; it posts on 5 March and every earlier as-at balance is unchanged.
   */
  @Test
  void backDatedFeeTransfer_isApproved_whenCoveredOnEveryDateFromItsDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 covered");
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    books.deposit(account, client, "1000.00", LocalDate.of(2026, 3, 3));
    pay(account, client, "1000.00", PAYMENT_DATE);

    var transfer = books.requestFeeTransfer(account, client, TRANSFER_DATE);
    books.approve(
        account, transfer.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));

    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("APPROVED");
    assertThat(books.transactionDateOf(transfer.transactionId())).isEqualTo(TRANSFER_DATE);
    assertThat(books.clientLedgerAsAt(account, client, TRANSFER_DATE.minusDays(1)))
        .isEqualByComparingTo("2500.00");
    assertThat(books.clientLedgerAsAt(account, client, TRANSFER_DATE))
        .isEqualByComparingTo("1350.00");
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("350.00");
    books.assertBalancedAsAt(account, TRANSFER_DATE);
    books.assertBalancedAsAt(account, books.today());
  }

  /**
   * A fee transfer dated today, on the dip history: it lowers no earlier as-at balance, and the
   * client holds R1 500 today, so it is approved as before.
   */
  @Test
  void feeTransferDatedToday_isApproved_whenTheClientCoversItToday() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 today approved");
    dipHistory(account, client);

    var transfer = books.requestFeeTransfer(account, client);
    books.approve(
        account, transfer.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));

    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("APPROVED");
    assertThat(books.transactionDateOf(transfer.transactionId())).isEqualTo(books.today());
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("500.00");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("350.00");
  }

  /**
   * A fee transfer dated today that the client cannot cover today is refused with today's message,
   * unchanged (no date named).
   */
  @Test
  void feeTransferDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt() {
    var account = books.freshAccount(false);
    var client = books.newClient("F-646D-1 today refused");
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    pay(account, client, "1000.00", PAYMENT_DATE);

    var transfer = books.requestFeeTransfer(account, client);

    books.assertApprovalRefused(
        account,
        transfer.transactionId(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + ". Available: R500,00, Requested: R1 150,00");
    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
  }

  /** Deposit R1 500 on 1 March, payment R1 000 on 10 March, deposit R1 000 on 15 March. */
  private void dipHistory(UUID account, TrustBooksFixture.Client client) {
    books.deposit(account, client, "1500.00", FIRST_DEPOSIT);
    pay(account, client, "1000.00", PAYMENT_DATE);
    books.deposit(account, client, "1000.00", LATER_DEPOSIT);
  }

  /** A payment dated {@code date}, requested and approved: cashbook and client fall together. */
  private void pay(UUID account, TrustBooksFixture.Client client, String amount, LocalDate date) {
    var paymentId = books.requestPayment(account, client, amount, date);
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-" + amount));
  }

  /**
   * The approval is refused by the matter sub-ledger guard: 400, the shared title, a detail naming
   * {@code asAt} and {@code availableText}, and the unchanged properties; the row still awaits.
   */
  private void assertMatterRefused(
      UUID account,
      TrustBooksFixture.FeeTransfer transfer,
      TrustBooksFixture.Client client,
      String asAt,
      String availableText,
      String available) {
    books.as(
        approverId,
        () ->
            assertThatThrownBy(
                    () ->
                        transactionService.approveTransaction(transfer.transactionId(), approverId))
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
                                  + ", Requested: R1 150,00");
                      var properties = body.getProperties();
                      assertThat(properties).isNotNull();
                      assertThat(properties.get("code")).isEqualTo(MATTER_CODE);
                      assertThat(properties.get("matterId")).isEqualTo(client.matter());
                      assertThat((BigDecimal) properties.get("available"))
                          .isEqualByComparingTo(available);
                      assertThat((BigDecimal) properties.get("requested"))
                          .isEqualByComparingTo("1150.00");
                    }));
    assertThat(books.statusOf(transfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
  }
}
