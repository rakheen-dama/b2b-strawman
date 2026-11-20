package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
 * FU-9 (Phase 92 F-6 / F-6b; founder-signed legal ruling {@code
 * qa_cycle/evidence/phase92/legal/F-6-opinion.md}): a firm-initiated outflow from a trust banking
 * account — a payment, refund, fee transfer or section 86(4) / 86(3) placement — is refused at its
 * final approval unless the account's derived cashbook covers it, under the account's lock; the
 * refusal tells the firm to withdraw from the section 86(3) investment first. A bank-confirmed
 * entry (here a dishonoured deposit's reversal) is never refused: it still posts, and the integrity
 * check raises {@code CASHBOOK_IN_DEBIT} (a warning) when it takes a trust banking account's
 * cashbook below zero.
 *
 * <p>Each case drains the cashbook with an approved section 86(3) placement, which lowers the
 * cashbook and leaves every client ledger as it was, so the client still covers the outflow and
 * only the cashbook check can refuse it. Every refusal moves nothing ({@code assertMoves}); the
 * class ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashbookCoverAtApprovalIntegrationTest {

  private static final String ORG_ID = "org_trust_cashbook_cover_test";
  private static final String OWNER = "user_cashbook_cover_owner";
  private static final String APPROVER = "user_cashbook_cover_approver";
  private static final String TITLE = "Insufficient trust balance";
  private static final String WITHDRAW_FIRST =
      " If trust money has been placed in a section 86(3) investment, withdraw it from the"
          + " investment first.";
  private static final Locale ZAR = VariableFormatter.resolveLocale("ZAR");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustIntegrityService integrityService;

  private TrustBooksFixture.Tenant books;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Cashbook Cover Org", null).schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "cashbook_owner@test.com", "Cashbook Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER,
                "cashbook_approver@test.com",
                "Cashbook Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * (FU-9 ruling 1) A refund, a fee transfer and a section 86(4) placement for a client whose
   * ledger covers each are refused at approval once a section 86(3) placement has left the cashbook
   * short — each stays AWAITING_APPROVAL and nothing moves. After the section 86(3) investment is
   * withdrawn (the refusal's remedy) the same approvals post.
   */
  @Test
  void refundFeeTransferAndSection86_4Placement_areRefusedAtApproval_whenTheCashbookCannotCover() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("FU-9 outflows");
    books.deposit(funding, client, "5000.00");
    var firm = drain(funding, firmAccount, "4500.00");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");

    var refund = books.requestRefund(funding, client, "2000.00");
    var feeTransfer = books.requestFeeTransfer(funding, client);
    var placement = books.requestPlacement(funding, clientAccount, client, "1000.00");

    books.assertApprovalRefused(funding, refund, approverId, TITLE, refused("refund", "2000.00"));
    books.assertApprovalRefused(
        funding,
        feeTransfer.transactionId(),
        approverId,
        TITLE,
        refused("fee transfer", "1150.00"));
    books.assertApprovalRefused(
        funding,
        placement.depositTransactionId(),
        approverId,
        TITLE,
        refused("section 86(4) placement", "1000.00"));
    assertThat(books.statusOf(refund)).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(feeTransfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(placement.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.clientInvestmentLedger(clientAccount, client)).isEqualByComparingTo("0");

    books.withdraw(
        funding,
        Expected.cashbookAndInterAccount("4500.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("-4500.00"),
        firm,
        books.today());
    books.approve(funding, refund, approverId, Expected.cashbookAndClient("-2000.00"));
    books.approve(
        funding, feeTransfer.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-1000.00"),
        clientAccount,
        Expected.cashbookAndInvestment("1000.00"),
        placement.depositTransactionId(),
        approverId);
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook())
        .isEqualByComparingTo("850.00");
    books.assertBalancedAsAt(funding, books.today());
  }

  /**
   * (FU-9 ruling 1, the boundary) A payment the cashbook covers exactly still approves, and the
   * cashbook ends at zero — not in debit, so no {@code CASHBOOK_IN_DEBIT}.
   */
  @Test
  void paymentTheCashbookCoversExactly_stillApproves_andRaisesNoCashbookFlag() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 covered");
    books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "3000.00");

    var payment = books.requestPayment(funding, client, "2000.00");
    books.approve(funding, payment, approverId, Expected.cashbookAndClient("-2000.00"));

    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook()).isEqualByComparingTo("0");
    assertThat(codes(check(funding))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    books.assertServicesAgree(funding);
  }

  /**
   * (FU-9 rulings 2 and 3) A bank-confirmed entry is never refused: a deposit dishonoured after a
   * section 86(3) placement emptied the cashbook still reverses (the contra posts at approval, the
   * client still covers it), taking the cashbook R5 000 into debit — and the integrity check now
   * raises {@code CASHBOOK_IN_DEBIT}, a warning naming the account, prompting the Rule 54.14.8
   * check and the Rule 54.14.10 report. The books still balance (EQ-2 holds through the
   * inter-account term), so the report stays {@code balanced}.
   */
  @Test
  void dishonouredDeposit_afterASection86_3Placement_isStillRecorded_andRaisesCashbookInDebit() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 dishonoured");
    var deposit = books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "5000.00");
    assertThat(codes(check(funding))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);

    var contra = books.reverse(funding, deposit, Expected.none());
    books.approve(funding, contra.id(), approverId, Expected.cashbookAndClient("-5000.00"));

    assertThat(books.statusOf(contra.id())).isEqualTo("APPROVED");
    assertThat(books.statusOf(deposit)).isEqualTo("REVERSED");
    var today = books.today();
    assertThat(books.snapshotAsAt(funding, today).cashbook()).isEqualByComparingTo("-5000.00");
    var report = check(funding);
    assertThat(report.balanced()).as("a warning never unbalances the books").isTrue();
    var flag = only(report, TrustIntegrityService.CASHBOOK_IN_DEBIT);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("5000.00");
    assertThat(flag.message())
        .isEqualTo(
            "The trust banking account's cashbook is "
                + rand("5000.00")
                + " in debit as at "
                + today.format(DATE)
                + ". Check that the trust money in the banking account, the trust investment"
                + " accounts and trust cash still covers the balances of all trust creditors (Rule"
                + " 54.14.8); if it does not, report to the Legal Practice Council immediately"
                + " (Rule 54.14.10).");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_TRUST_ACCOUNT);
              assertThat(s.id()).isEqualTo(funding);
              assertThat(s.name()).startsWith("Trust Books ");
              assertThat(s.amount()).isEqualByComparingTo("5000.00");
            });
    // As at the day before the reversal posted the cashbook was R0 — not in debit
    assertThat(codes(check(funding, today.minusDays(1))))
        .doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    // The investment account's books are not a trust banking account's cashbook
    assertThat(codes(check(firmAccount))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    books.assertServicesAgree(funding);
    books.assertBalancedAsAt(firmAccount, today);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /** An approved section 86(3) placement of {@code amount} from {@code funding}; its id. */
  private UUID drain(UUID funding, UUID firmAccount, String amount) {
    var placed = books.requestPlacement(funding, firmAccount, null, amount);
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-" + amount),
        firmAccount,
        Expected.cashbookAndInterAccount(amount),
        placed.depositTransactionId(),
        approverId);
    return placed.id();
  }

  /** The refusal for a {@code noun} of {@code requested} against R500 left in the cashbook. */
  private static String refused(String noun, String requested) {
    return "The trust account's cashbook does not cover this "
        + noun
        + ". Available: "
        + rand("500.00")
        + ", Requested: "
        + rand(requested)
        + "."
        + WITHDRAW_FIRST;
  }

  private TrustIntegrityReport check(UUID account) {
    return check(account, null);
  }

  private TrustIntegrityReport check(UUID account, LocalDate asAt) {
    var report = new TrustIntegrityReport[1];
    books.as(approverId, () -> report[0] = integrityService.check(account, asAt, null));
    return report[0];
  }

  private static java.util.List<String> codes(TrustIntegrityReport report) {
    return report.flags().stream().map(IntegrityFlag::code).toList();
  }

  private static IntegrityFlag only(TrustIntegrityReport report, String code) {
    var matching = report.flags().stream().filter(f -> f.code().equals(code)).toList();
    assertThat(matching).as("flag %s in %s", code, codes(report)).hasSize(1);
    return matching.getFirst();
  }

  private static String rand(String amount) {
    return VariableFormatter.formatCurrencyText(new BigDecimal(amount), ZAR);
  }
}
