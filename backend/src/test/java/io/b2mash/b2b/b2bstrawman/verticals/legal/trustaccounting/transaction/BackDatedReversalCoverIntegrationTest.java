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
 * FU-5 (Phase 92 649B S2; founder ruling S2, {@code tasks/phase92-decisions.md}): a deposit
 * reversal is a credit contra saved awaiting approval; on approval it posts on its {@code
 * reversalDate}, so it lowers the client's balance — and the matter cell's — as at that date and
 * every later date. A back-dated reversal is therefore approved only if the client and the matter
 * cell cover it as at the reversal date and as at every later posted date up to today (the lowest
 * balance from the reversal date on, orchestrator default FU-5 D-1); the refusal names the date the
 * balance falls short. A reversal dated today is checked as before.
 *
 * <p>Every case uses a fresh account, so a client's history is only what the case posts. The
 * history is the defect's shape: deposit R1 000 on 1 March, payment R800 on 10 March, deposit R900
 * on 15 March, then the first deposit reversed as at 5 March — today the client holds R1 100 (it
 * covers R1 000), but as at 10 March it held only R200. The class ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackDatedReversalCoverIntegrationTest {

  private static final String ORG_ID = "org_trust_backdated_reversal_cover";
  private static final String TITLE = "Insufficient trust balance";
  private static final String MATTER_CODE = "INSUFFICIENT_MATTER_TRUST_BALANCE";

  private static final LocalDate FIRST_DEPOSIT = LocalDate.of(2026, 3, 1);
  private static final LocalDate REVERSAL_DATE = LocalDate.of(2026, 3, 5);
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
            .provisionTenant(ORG_ID, "Trust Back-dated Reversal Org", null)
            .schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_rev_owner",
                "backdated_rev_owner@test.com",
                "Back-dated Reversal Owner",
                "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_backdated_rev_approver",
                "backdated_rev_approver@test.com",
                "Back-dated Reversal Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * The defect (649B S2): today the client covers the reversal, but as at 10 March — after the
   * payment, before the later deposit — it held R200, so approving the reversal dated 5 March would
   * leave its ledger R800 in debit as at 10 to 14 March. Refused, naming 10 March; nothing moves.
   */
  @Test
  void backDatedReversal_isRefused_whenTheClientWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 client dip");
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    pay(account, client, "800.00", PAYMENT_DATE);
    books.deposit(account, client, "900.00", LATER_DEPOSIT);
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("1100.00");

    var reversal = books.reverse(account, depositId, Expected.none(), REVERSAL_DATE);

    books.assertApprovalRefused(
        account,
        reversal.id(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + " as at 10 March 2026. Available: R200,00, Requested: R1 000,00");
    assertThat(books.statusOf(reversal.id())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).isEqualTo("RECORDED");
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("200.00");
  }

  /**
   * As at the reversal date itself: a payment on 3 March leaves R200 as at 5 March, while today the
   * client holds R1 100. Refused, naming the reversal date.
   */
  @Test
  void backDatedReversal_isRefused_whenTheClientWouldBeInDebitAsAtTheReversalDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 reversal date");
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    pay(account, client, "800.00", LocalDate.of(2026, 3, 3));
    books.deposit(account, client, "900.00", LATER_DEPOSIT);

    var reversal = books.reverse(account, depositId, Expected.none(), REVERSAL_DATE);

    books.assertApprovalRefused(
        account,
        reversal.id(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + " as at 5 March 2026. Available: R200,00, Requested: R1 000,00");
    assertThat(books.statusOf(reversal.id())).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * The matter cell: the client's other matter holds R2 000, so the client covers the reversal on
   * every date; the reversed deposit's matter held R200 as at 10 March. Refused by the matter
   * sub-ledger guard (G89-F12) with its code and properties, naming 10 March; nothing moves.
   */
  @Test
  void backDatedReversal_isRefused_whenTheMatterCellWouldBeInDebitAsAtALaterDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 matter dip");
    var otherMatter = books.onNewMatter(client);
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    books.deposit(account, otherMatter, "2000.00", FIRST_DEPOSIT);
    pay(account, client, "800.00", PAYMENT_DATE);
    books.deposit(account, client, "900.00", LATER_DEPOSIT);

    var reversal = books.reverse(account, depositId, Expected.none(), REVERSAL_DATE);

    books.as(
        approverId,
        () ->
            assertThatThrownBy(
                    () -> transactionService.approveTransaction(reversal.id(), approverId))
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
                                  + " as at 10 March 2026. Available on matter: R200,00,"
                                  + " Requested: R1 000,00");
                      var properties = body.getProperties();
                      assertThat(properties).isNotNull();
                      assertThat(properties.get("code")).isEqualTo(MATTER_CODE);
                      assertThat(properties.get("matterId")).isEqualTo(client.matter());
                      assertThat((BigDecimal) properties.get("available"))
                          .isEqualByComparingTo("200.00");
                      assertThat((BigDecimal) properties.get("requested"))
                          .isEqualByComparingTo("1000.00");
                    }));
    assertThat(books.statusOf(reversal.id())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).isEqualTo("RECORDED");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("3100.00");
  }

  /**
   * Covered on every date: the R900 deposit lands on 3 March, before the payment, so the client
   * holds R900 as at 5 March and R100 from 10 March on. Approved; the contra posts on 5 March and
   * every earlier as-at balance is unchanged.
   */
  @Test
  void backDatedReversal_isApproved_whenCoveredOnEveryDateFromTheReversalDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 covered");
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    books.deposit(account, client, "900.00", LocalDate.of(2026, 3, 3));
    pay(account, client, "800.00", PAYMENT_DATE);

    var reversal = books.reverse(account, depositId, Expected.none(), REVERSAL_DATE);
    books.approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-1000.00"));

    assertThat(books.statusOf(reversal.id())).isEqualTo("APPROVED");
    assertThat(books.statusOf(depositId)).isEqualTo("REVERSED");
    assertThat(books.transactionDateOf(reversal.id())).isEqualTo(REVERSAL_DATE);
    assertThat(books.clientLedgerAsAt(account, client, REVERSAL_DATE.minusDays(1)))
        .isEqualByComparingTo("1900.00");
    assertThat(books.clientLedgerAsAt(account, client, REVERSAL_DATE))
        .isEqualByComparingTo("900.00");
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("100.00");
    books.assertBalancedAsAt(account, REVERSAL_DATE);
    books.assertBalancedAsAt(account, books.today());
  }

  /**
   * A reversal dated today, on the defect's history: it lowers no earlier as-at balance, and the
   * client holds R1 100 today, so it is approved as before.
   */
  @Test
  void reversalDatedToday_isApproved_whenTheClientCoversItToday() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 today approved");
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    pay(account, client, "800.00", PAYMENT_DATE);
    books.deposit(account, client, "900.00", LATER_DEPOSIT);

    var reversal = books.reverse(account, depositId, Expected.none());
    books.approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-1000.00"));

    assertThat(books.statusOf(reversal.id())).isEqualTo("APPROVED");
    assertThat(books.transactionDateOf(reversal.id())).isEqualTo(books.today());
    assertThat(books.clientLedgerAsAt(account, client, PAYMENT_DATE))
        .isEqualByComparingTo("200.00");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("100.00");
  }

  /**
   * A reversal dated today that the client cannot cover today is refused with today's message,
   * unchanged (no date named).
   */
  @Test
  void reversalDatedToday_isRefused_withTheUnchangedMessage_whenTheClientCannotCoverIt() {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-5 today refused");
    var depositId = books.deposit(account, client, "1000.00", FIRST_DEPOSIT);
    pay(account, client, "800.00", PAYMENT_DATE);

    var reversal = books.reverse(account, depositId, Expected.none());

    books.assertApprovalRefused(
        account,
        reversal.id(),
        approverId,
        TITLE,
        "Insufficient trust balance for "
            + client.name()
            + ". Available: R200,00, Requested: R1 000,00");
    assertThat(books.statusOf(reversal.id())).isEqualTo("AWAITING_APPROVAL");
  }

  /** A payment dated {@code date}, requested and approved: cashbook and client fall together. */
  private void pay(UUID account, TrustBooksFixture.Client client, String amount, LocalDate date) {
    var paymentId = books.requestPayment(account, client, amount, date);
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-" + amount));
  }
}
