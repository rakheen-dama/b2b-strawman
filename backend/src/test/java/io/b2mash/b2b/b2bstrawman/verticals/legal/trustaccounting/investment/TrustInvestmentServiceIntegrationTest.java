package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.TrustInvestmentResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.WithdrawInvestmentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 648A (task 648.5; ADR-379; architecture §16.3.7, §16.9.2 paths 17–19): investments
 * on the books, across two accounts — the funding trust banking account {@code G} and an {@code
 * INVESTMENT} trust account {@code I} whose basis is the investment's.
 *
 * <ul>
 *   <li><b>Path 17</b> — a section 86(4) placement: the request moves nothing on either account;
 *       the approval posts {@code INV_PLACE} on G (cashbook and client −P) and {@code INV_RECEIVE}
 *       on I (cashbook and investment creditors +P) — the client's total is unchanged; a rejection
 *       moves nothing and cancels the investment.
 *   <li><b>Path 18</b> — a section 86(3) placement and its return: {@code FIRM_INV_*} on the
 *       inter-account ledger of both accounts (the terms cancel firm-wide, EQ-3); no client moves.
 *   <li><b>Path 19</b> — a section 86(4) withdrawal: {@code INV_WITHDRAW} on I and {@code
 *       INV_RETURN} on G (the placement's matter), both RECORDED, for the investment's balance.
 * </ul>
 *
 * <p>Every step is asserted through {@link TrustBooksHarness#assertMoves} on both accounts, which
 * also checks EQ-5 for each investment; the class ends with {@code assertFirmBalanced(today)}.
 * Every refusal moves nothing on either account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentServiceIntegrationTest {

  private static final String ORG_ID = "org_trust_invest_test";
  private static final String OWNER = "user_invest_owner";
  private static final String APPROVER = "user_invest_approver";
  private static final String SECOND = "user_invest_second";

  private static final String PLACE_TITLE = "Cannot place investment";
  private static final String WITHDRAW_TITLE = "Cannot withdraw investment";
  private static final String PLACEMENT_NOT_REVERSIBLE =
      "This entry placed a trust investment. Withdraw the investment instead of reversing its"
          + " placement.";
  private static final String INVESTMENT_ROW_NOT_REVERSIBLE =
      "Investment entries cannot be reversed. Withdraw the investment instead.";
  private static final String INVESTMENT_HELD_ON_CLOSE =
      "An investment placed from or held in this account has not been withdrawn. Withdraw it"
          + " before closing the account.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustInvestmentRepository investmentRepository;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;
  private UUID secondId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Investment Test Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "invest_owner@test.com", "Investment Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER,
                "invest_approver@test.com",
                "Investment Approver",
                "admin"));
    secondId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, SECOND, "invest_second@test.com", "Investment Second", "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- path 17: section 86(4) placement ---------------------------------------------------

  @Test
  void path17_clientInstruction_requestMovesNothing_approvalMovesBothAccounts_clientTotalSame() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4)");
    books.deposit(funding, client, "5000.00");

    var placed = books.requestPlacement(funding, investmentAccount, client, "2000.00");

    var investment = investment(placed.id());
    assertThat(investment.getStatus()).isEqualTo("ACTIVE");
    assertThat(investment.getTrustAccountId()).isEqualTo(funding);
    assertThat(investment.getInvestmentAccountId()).isEqualTo(investmentAccount);
    assertThat(investment.getCustomerId()).isEqualTo(client.id());
    assertThat(investment.getProjectId()).isEqualTo(client.matter());
    assertThat(investment.getInvestmentBasis()).isEqualTo(InvestmentBasis.CLIENT_INSTRUCTION);
    assertThat(investment.getDepositTransactionId()).isEqualTo(placed.depositTransactionId());
    var reference = "INV-" + placed.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    var placement = row(placed.depositTransactionId());
    assertThat(placement.getTransactionType()).isEqualTo("INV_PLACE");
    assertThat(placement.getTrustAccountId()).isEqualTo(funding);
    assertThat(placement.getStatus()).isEqualTo("AWAITING_APPROVAL");
    assertThat(placement.getInvestmentId()).isEqualTo(placed.id());
    assertThat(placement.getCustomerId()).isEqualTo(client.id());
    assertThat(placement.getProjectId()).isEqualTo(client.matter());
    assertThat(placement.getReference()).isEqualTo(reference);
    assertThat(placed.investmentAccountId()).isEqualTo(investmentAccount);
    assertThat(placed.matterId()).isEqualTo(client.matter());
    assertThat(placed.clientName()).isEqualTo(client.name());
    assertThat(placed.balance()).isEqualByComparingTo("0");
    assertThat(placed.awaitingApproval()).isTrue();
    assertThat(
            audit("trust_investment.placed", placed.id()).getDetails().get("investment_account_id"))
        .isEqualTo(investmentAccount.toString());
    assertThat(audit("trust_investment.placed", placed.id()).getDetails())
        .containsEntry("funding_account_id", funding.toString())
        .containsEntry("investment_basis", "CLIENT_INSTRUCTION")
        .containsEntry("reference", reference);

    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-2000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("2000.00"),
        placed.depositTransactionId(),
        approverId);

    assertThat(books.statusOf(placed.depositTransactionId())).isEqualTo("APPROVED");
    var receive = rowOf(placed.id(), "INV_RECEIVE");
    assertThat(receive.getTrustAccountId()).isEqualTo(investmentAccount);
    assertThat(receive.getStatus()).isEqualTo("APPROVED");
    assertThat(receive.getLedger()).isEqualTo(TrustLedger.CLIENT_INVESTMENT);
    assertThat(receive.getAmount()).isEqualByComparingTo("2000.00");
    assertThat(receive.getCustomerId()).isEqualTo(client.id());
    assertThat(receive.getProjectId()).isEqualTo(client.matter());
    assertThat(receive.getReference()).isEqualTo(reference);
    assertThat(receive.getTransactionDate()).isEqualTo(placement.getTransactionDate());
    assertThat(receive.getApprovedBy()).isEqualTo(approverId);

    // The client's total (general on G + invested on I) is unchanged (L7)
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("3000.00");
    assertThat(books.clientInvestmentLedger(investmentAccount, client))
        .isEqualByComparingTo("2000.00");
    assertThat(
            books
                .clientLedger(funding, client)
                .add(books.clientInvestmentLedger(investmentAccount, client)))
        .isEqualByComparingTo("5000.00");
    assertThat(get(placed.id()).balance()).isEqualByComparingTo("2000.00");
    assertThat(get(placed.id()).awaitingApproval()).isFalse();
    books.assertServicesAgree(funding);
    books.assertServicesAgree(investmentAccount);
  }

  @Test
  void path17_rejection_movesNothing_andCancelsTheInvestment() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4) rejected");
    books.deposit(funding, client, "1000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "600.00");

    books.as(
        approverId,
        () ->
            harness.assertMoves(
                funding,
                Expected.none(),
                investmentAccount,
                Expected.none(),
                () ->
                    transactionService.rejectTransaction(
                        placed.depositTransactionId(), approverId, "Client changed their mind")));

    assertThat(books.statusOf(placed.depositTransactionId())).isEqualTo("REJECTED");
    assertThat(investment(placed.id()).getStatus()).isEqualTo("CANCELLED");
    assertThat(audit("trust_investment.cancelled", placed.id()).getDetails())
        .containsEntry("investment_account_id", investmentAccount.toString())
        .containsEntry("funding_account_id", funding.toString())
        .containsEntry("reason", "Client changed their mind");
    // Nothing to withdraw
    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        books.today(),
        "Only an active investment can be withdrawn.");
  }

  @Test
  void path17_dualApproval_postsBothRowsOnlyOnTheSecondApproval() {
    var funding = books.freshAccount(true);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4) dual");
    books.deposit(funding, client, "1000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "400.00");

    books.approvePlacement(
        funding,
        Expected.none(),
        investmentAccount,
        Expected.none(),
        placed.depositTransactionId(),
        approverId);
    assertThat(books.statusOf(placed.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");

    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-400.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("400.00"),
        placed.depositTransactionId(),
        secondId);
    var receive = rowOf(placed.id(), "INV_RECEIVE");
    assertThat(receive.getApprovedBy()).isEqualTo(approverId);
    assertThat(receive.getSecondApprovedBy()).isEqualTo(secondId);
  }

  // --- path 18: section 86(3) placement and return -----------------------------------------

  @Test
  void path18_firmDiscretion_placementAndReturn_interAccountOnBoth_noClientMoves() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("s86(3) funds");
    books.deposit(funding, client, "5000.00");

    var placed = books.requestPlacement(funding, investmentAccount, null, "1500.00");
    assertThat(placed.clientId()).isNull();
    assertThat(placed.clientName()).isNull();
    assertThat(placed.matterId()).isNull();
    var placement = row(placed.depositTransactionId());
    assertThat(placement.getTransactionType()).isEqualTo("FIRM_INV_PLACE");
    assertThat(placement.getCustomerId()).isNull();
    assertThat(placement.getProjectId()).isNull();
    assertThat(placement.getInvestmentId()).isEqualTo(placed.id());
    assertThat(audit("trust_investment.placed", placed.id()).getDetails())
        .doesNotContainKey("customer_id")
        .containsEntry("investment_basis", "FIRM_DISCRETION");

    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-1500.00"),
        investmentAccount,
        Expected.cashbookAndInterAccount("1500.00"),
        placed.depositTransactionId(),
        approverId);
    var receive = rowOf(placed.id(), "FIRM_INV_RECEIVE");
    assertThat(receive.getLedger()).isEqualTo(TrustLedger.INTER_ACCOUNT);
    assertThat(receive.getCustomerId()).isNull();
    assertThat(receive.getStatus()).isEqualTo("APPROVED");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");
    assertThat(get(placed.id()).balance()).isEqualByComparingTo("1500.00");
    books.assertFirmBalanced();

    var withdrawalDate = books.today();
    var withdrawn =
        books.withdraw(
            funding,
            Expected.cashbookAndInterAccount("1500.00"),
            investmentAccount,
            Expected.cashbookAndInterAccount("-1500.00"),
            placed.id(),
            withdrawalDate);

    var wdReference = "INV-WD-" + placed.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    var out = rowOf(placed.id(), "FIRM_INV_WITHDRAW");
    var back = rowOf(placed.id(), "FIRM_INV_RETURN");
    assertThat(out.getTrustAccountId()).isEqualTo(investmentAccount);
    assertThat(back.getTrustAccountId()).isEqualTo(funding);
    for (var leg : List.of(out, back)) {
      assertThat(leg.getStatus()).isEqualTo("RECORDED");
      assertThat(leg.getAmount()).isEqualByComparingTo("1500.00");
      assertThat(leg.getReference()).isEqualTo(wdReference);
      assertThat(leg.getTransactionDate()).isEqualTo(withdrawalDate);
      assertThat(leg.getCustomerId()).isNull();
    }
    assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
    assertThat(withdrawn.withdrawalAmount()).isEqualByComparingTo("1500.00");
    assertThat(withdrawn.withdrawalDate()).isEqualTo(withdrawalDate);
    assertThat(withdrawn.withdrawalTransactionId()).isEqualTo(back.getId());
    assertThat(withdrawn.balance()).isEqualByComparingTo("0");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");
    books.assertFirmBalanced();
  }

  // --- path 19: section 86(4) withdrawal ---------------------------------------------------

  @Test
  void path19_clientInstruction_withdrawal_returnsTheBalanceToThePlacementsMatter() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4) withdrawal");
    books.deposit(funding, client, "5000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "2000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-2000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("2000.00"),
        placed.depositTransactionId(),
        approverId);
    // A net interest entry on the investment (648B records these; written here so the withdrawal
    // amount is shown to be the investment's balance, not its principal)
    books.postLedgerRowDirect(
        investmentAccount,
        "INV_INTEREST",
        "25.00",
        TrustBooksFixture.POSTING_DATE,
        client.id(),
        client.matter(),
        null,
        placed.id());
    assertThat(get(placed.id()).balance()).isEqualByComparingTo("2025.00");

    var withdrawalDate = books.today().minusDays(1);
    var withdrawn =
        books.withdraw(
            funding,
            Expected.cashbookAndClient("2025.00"),
            investmentAccount,
            Expected.cashbookAndInvestment("-2025.00"),
            placed.id(),
            withdrawalDate);

    var wdReference = "INV-WD-" + placed.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    var out = rowOf(placed.id(), "INV_WITHDRAW");
    var back = rowOf(placed.id(), "INV_RETURN");
    assertThat(out.getTrustAccountId()).isEqualTo(investmentAccount);
    assertThat(out.getLedger()).isEqualTo(TrustLedger.CLIENT_INVESTMENT);
    assertThat(back.getTrustAccountId()).isEqualTo(funding);
    assertThat(back.getLedger()).isEqualTo(TrustLedger.CLIENT);
    for (var leg : List.of(out, back)) {
      assertThat(leg.getStatus()).isEqualTo("RECORDED");
      assertThat(leg.getAmount()).isEqualByComparingTo("2025.00");
      assertThat(leg.getCustomerId()).isEqualTo(client.id());
      assertThat(leg.getProjectId()).isEqualTo(client.matter());
      assertThat(leg.getReference()).isEqualTo(wdReference);
      assertThat(leg.getTransactionDate()).isEqualTo(withdrawalDate);
      assertThat(leg.getApprovedBy()).isNull();
    }
    assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
    assertThat(withdrawn.withdrawalAmount()).isEqualByComparingTo("2025.00");
    assertThat(withdrawn.withdrawalTransactionId()).isEqualTo(back.getId());
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5025.00");
    assertThat(books.clientInvestmentLedger(investmentAccount, client)).isEqualByComparingTo("0");
    assertThat(audit("trust_investment.withdrawn", placed.id()).getDetails())
        .containsEntry("withdrawal_amount", "2025.00")
        .containsEntry("investment_account_id", investmentAccount.toString())
        .containsEntry("funding_account_id", funding.toString());

    // Once withdrawn, never again
    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        withdrawalDate,
        "Only an active investment can be withdrawn.");
  }

  @Test
  void withdrawal_isRefused_whileThePlacementAwaitsApproval_andOutsideItsDates() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("withdrawal refusals");
    books.deposit(funding, client, "1000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "500.00");

    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        books.today(),
        "The investment's placement must be approved before the investment can be withdrawn"
            + " (the placement is awaiting approval).");

    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-500.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("500.00"),
        placed.depositTransactionId(),
        approverId);
    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        TrustBooksFixture.POSTING_DATE.minusDays(1),
        "The withdrawal date cannot be before the investment's deposit date.");
    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        books.today().plusDays(1),
        "The withdrawal date cannot be in the future.");
    assertThat(investment(placed.id()).getStatus()).isEqualTo("ACTIVE");
  }

  // --- placement checks ---------------------------------------------------------------------

  @Test
  void placement_basisMustMatchTheInvestmentAccount() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("basis mismatch");
    books.deposit(funding, client, "1000.00");

    assertPlacementRefused(
        funding,
        firmAccount,
        request(firmAccount, client, "100.00", InvestmentBasis.CLIENT_INSTRUCTION),
        PLACE_TITLE,
        "The investment account holds section 86(3) investments; this placement is a section"
            + " 86(4) investment. Choose an investment account of the same basis.");
    assertPlacementRefused(
        funding,
        clientAccount,
        request(clientAccount, null, "100.00", InvestmentBasis.FIRM_DISCRETION),
        PLACE_TITLE,
        "The investment account holds section 86(4) investments; this placement is a section"
            + " 86(3) investment. Choose an investment account of the same basis.");
  }

  @Test
  void placement_needsABankingAccountToFundIt_andAnInvestmentAccountToHoldIt() {
    var funding = books.freshAccount(false);
    var other = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("account types");
    books.deposit(funding, client, "1000.00");

    assertPlacementRefused(
        investmentAccount,
        funding,
        request(investmentAccount, null, "100.00", InvestmentBasis.FIRM_DISCRETION),
        PLACE_TITLE,
        "An investment is placed from a trust banking account, not from an investment account.");
    assertPlacementRefused(
        funding,
        other,
        request(other, null, "100.00", InvestmentBasis.FIRM_DISCRETION),
        PLACE_TITLE,
        "The money must be placed in a trust investment account.");
  }

  @Test
  void placement_section86_4_namesTheClientAndMatter_section86_3_namesNeither() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("client and matter");
    books.deposit(funding, client, "1000.00");

    assertPlacementRefused(
        funding,
        clientAccount,
        new PlaceInvestmentRequest(
            clientAccount,
            null,
            client.matter(),
            "Bank",
            "ACC",
            new BigDecimal("100.00"),
            new BigDecimal("0.05"),
            TrustBooksFixture.POSTING_DATE,
            null,
            null,
            InvestmentBasis.CLIENT_INSTRUCTION),
        "Client required",
        "A section 86(4) investment names the client and the matter whose money is invested.");
    assertPlacementRefused(
        funding,
        clientAccount,
        new PlaceInvestmentRequest(
            clientAccount,
            client.id(),
            null,
            "Bank",
            "ACC",
            new BigDecimal("100.00"),
            new BigDecimal("0.05"),
            TrustBooksFixture.POSTING_DATE,
            null,
            null,
            InvestmentBasis.CLIENT_INSTRUCTION),
        "Matter required",
        "A matter is required for client trust transactions");
    var noClient =
        "A section 86(3) investment is the firm's placement of surplus trust money and names no"
            + " client or matter.";
    assertPlacementRefused(
        funding,
        firmAccount,
        new PlaceInvestmentRequest(
            firmAccount,
            client.id(),
            null,
            "Bank",
            "ACC",
            new BigDecimal("100.00"),
            new BigDecimal("0.05"),
            TrustBooksFixture.POSTING_DATE,
            null,
            null,
            InvestmentBasis.FIRM_DISCRETION),
        PLACE_TITLE,
        noClient);
    assertPlacementRefused(
        funding,
        firmAccount,
        new PlaceInvestmentRequest(
            firmAccount,
            null,
            client.matter(),
            "Bank",
            "ACC",
            new BigDecimal("100.00"),
            new BigDecimal("0.05"),
            TrustBooksFixture.POSTING_DATE,
            null,
            null,
            InvestmentBasis.FIRM_DISCRETION),
        PLACE_TITLE,
        noClient);
  }

  @Test
  void placement_mustBeCovered_byTheMatterCell_orByTheCashbook() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("cover");
    books.deposit(funding, client, "1000.00");

    assertPlacementRefused(
        funding,
        clientAccount,
        request(clientAccount, client, "1000.01", InvestmentBasis.CLIENT_INSTRUCTION),
        "Insufficient trust balance",
        "Insufficient trust balance on matter "
            + matterName(client)
            + " for "
            + client.name()
            + ". Available on matter: R1\u00a0000,00, Requested: R1\u00a0000,01");
    assertPlacementRefused(
        funding,
        firmAccount,
        request(firmAccount, null, "1000.01", InvestmentBasis.FIRM_DISCRETION),
        "Insufficient trust balance",
        "The trust account's cashbook does not cover this section 86(3) placement. Available:"
            + " R1\u00a0000,00, Requested: R1\u00a0000,01");
  }

  /**
   * Review F-3 (OPEN-3 reversed): a section 86(3) placement moves no client money, so the
   * client-cover check at approval never ran for it. Two placements that each pass the request
   * check (pending placements move nothing) must not both post — the second approval re-reads the
   * cashbook under the funding account's lock and is refused, moving nothing on either account.
   */
  @Test
  void
      section86_3_cover_isCheckedAgainAtApproval_soTwoPendingPlacementsCannotOverdrawTheCashbook() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("s86(3) double approval");
    books.deposit(funding, client, "5000.00");

    var first = books.requestPlacement(funding, firmAccount, null, "3000.00");
    var second = books.requestPlacement(funding, firmAccount, null, "3000.00");

    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-3000.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("3000.00"),
        first.depositTransactionId(),
        approverId);

    books.as(
        approverId,
        () ->
            harness.assertMoves(
                funding,
                Expected.none(),
                firmAccount,
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () ->
                                transactionService.approveTransaction(
                                    second.depositTransactionId(), approverId))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Insufficient trust balance");
                              assertThat(e.getBody().getDetail())
                                  .isEqualTo(
                                      "The trust account's cashbook does not cover this section"
                                          + " 86(3) placement. Available: R2 000,00,"
                                          + " Requested: R3 000,00");
                            })));

    assertThat(books.statusOf(second.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(investment(second.id()).getStatus()).isEqualTo("ACTIVE");
    assertThat(get(second.id()).balance()).isEqualByComparingTo("0");
    assertThat(get(second.id()).awaitingApproval()).isTrue();
    assertThat(get(first.id()).awaitingApproval()).isFalse();
    books.assertFirmBalanced();
  }

  /**
   * Review cycle 2 (Reviewer A, Medium): two section 86(3) approvals at once. Both placements pass
   * at request (pending placements move nothing). The first approval's transaction is held open
   * after it has posted; the second approval then blocks on the funding account's lock (observed in
   * {@code pg_stat_activity}: a trust_accounts query blocked by the first transaction's backend,
   * not a sleep), and once the first commits it re-reads the cashbook and is refused — exactly one
   * placement posts, nothing moves for the other, and the books balance.
   */
  @Test
  void twoSection86_3_approvalsAtOnce_secondWaitsOnTheFundingAccountLock_andIsRefused() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("s86(3) concurrent approvals");
    books.deposit(funding, client, "5000.00");
    var first = books.requestPlacement(funding, firmAccount, null, "3000.00");
    var second = books.requestPlacement(funding, firmAccount, null, "3000.00");

    var firstPosted = new CountDownLatch(1);
    var commitFirst = new CountDownLatch(1);
    var firstBackendPid = new AtomicReference<Integer>();
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    var secondFinishedBeforeCommit = new AtomicBoolean(true);

    books.as(
        approverId,
        () ->
            harness.assertMoves(
                funding,
                Expected.cashbookAndInterAccount("-3000.00"),
                firmAccount,
                Expected.cashbookAndInterAccount("3000.00"),
                () -> {
                  Thread firstThread =
                      Thread.ofPlatform()
                          .start(
                              () ->
                                  books.as(
                                      approverId,
                                      () -> {
                                        try {
                                          transactionTemplate.executeWithoutResult(
                                              tx -> {
                                                transactionService.approveTransaction(
                                                    first.depositTransactionId(), approverId);
                                                firstBackendPid.set(
                                                    ((Number)
                                                            entityManager
                                                                .createNativeQuery(
                                                                    "SELECT pg_backend_pid()")
                                                                .getSingleResult())
                                                        .intValue());
                                                firstPosted.countDown();
                                                awaitLatch(commitFirst);
                                              });
                                        } catch (Throwable t) {
                                          firstError.set(t);
                                          firstPosted.countDown();
                                        }
                                      }));
                  awaitLatch(firstPosted);
                  assertThat(firstError.get()).as("the first approval").isNull();

                  Thread secondThread =
                      Thread.ofPlatform()
                          .start(
                              () ->
                                  books.as(
                                      approverId,
                                      () -> {
                                        try {
                                          transactionService.approveTransaction(
                                              second.depositTransactionId(), approverId);
                                        } catch (Throwable t) {
                                          secondError.set(t);
                                        } finally {
                                          secondFinished.set(true);
                                        }
                                      }));
                  try {
                    awaitBlockedOnTrustAccountLockHeldBy(firstBackendPid.get(), secondFinished);
                    secondFinishedBeforeCommit.set(secondFinished.get());
                  } finally {
                    commitFirst.countDown();
                    join(firstThread);
                    join(secondThread);
                  }
                }));

    assertThat(firstError.get()).as("the first approval").isNull();
    assertThat(secondFinishedBeforeCommit.get())
        .as("the second approval waited for the funding account lock the first held")
        .isFalse();
    assertThat(secondError.get())
        .as("the second approval, re-read after the first committed")
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo("Insufficient trust balance");
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "The trust account's cashbook does not cover this section 86(3) placement."
                          + " Available: R2 000,00, Requested: R3 000,00");
            });
    assertThat(books.statusOf(first.depositTransactionId())).isEqualTo("APPROVED");
    assertThat(books.statusOf(second.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(investment(second.id()).getStatus()).isEqualTo("ACTIVE");
    assertThat(get(second.id()).balance()).isEqualByComparingTo("0");
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook())
        .isEqualByComparingTo("2000.00");
    books.assertBalancedAsAt(funding, books.today());
    books.assertBalancedAsAt(firmAccount, books.today());
    books.assertFirmBalanced();
  }

  /**
   * ORCHESTRATOR RULING 648A F-6 (found.md; §16 "sufficiency checks stay where they are", R14 "no
   * hard block on posting", ADR-380): a client payment's sufficiency is checked against the
   * client's ledger and matter cell only — not the account's cashbook. A section 86(3) placement
   * lowers the cashbook and leaves every client ledger as it was (the inter-account ledger carries
   * the offset), so a later payment for the same client posts and takes the cashbook into debit.
   * This test PINS that behaviour until the founder rules on the sufficiency model (owner 649C);
   * the books still balance — EQ-2/EQ-3 hold because the inter-account term carries the placement.
   */
  @Test
  void clientPaymentAfterASection86_3Placement_isCheckedAgainstTheClientLedgerOnly_ruling648A_F6() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("F-6 payment after s86(3)");
    books.deposit(funding, client, "5000.00");

    var placed = books.requestPlacement(funding, firmAccount, null, "3000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-3000.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("3000.00"),
        placed.depositTransactionId(),
        approverId);
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook())
        .isEqualByComparingTo("2000.00");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");

    var payment = books.requestPayment(funding, client, "4000.00");
    books.approve(funding, payment, approverId, Expected.cashbookAndClient("-4000.00"));

    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    var after = books.snapshotAsAt(funding, books.today());
    assertThat(after.cashbook())
        .as("G's cashbook, in debit by R2 000")
        .isEqualByComparingTo("-2000.00");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("1000.00");
    assertThat(after.interAccount()).isEqualByComparingTo("-3000.00");
    books.assertBalancedAsAt(funding, books.today());
    books.assertBalancedAsAt(firmAccount, books.today());
    books.assertFirmBalanced();
  }

  @Test
  void placement_maturityDate_cannotBeBeforeTheDepositDate() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("maturity date");
    books.deposit(funding, client, "1000.00");
    var base = request(firmAccount, null, "100.00", InvestmentBasis.FIRM_DISCRETION);

    assertPlacementRefused(
        funding,
        firmAccount,
        new PlaceInvestmentRequest(
            base.investmentAccountId(),
            null,
            null,
            base.institution(),
            base.accountNumber(),
            base.principal(),
            base.interestRate(),
            base.depositDate(),
            base.depositDate().minusDays(1),
            null,
            base.investmentBasis()),
        PLACE_TITLE,
        "The maturity date cannot be before the deposit date.");
  }

  /**
   * CodeRabbit cycle 3 (Major): a placement's row carries its deposit date, and both bases' cover
   * (the matter cell, the cashbook) is read as at today — a future-dated placement would escape the
   * cover of every row posted before its date arrives. A placement is never future-dated, either
   * basis (the withdrawal and the fee transfer refuse a future date likewise).
   */
  @Test
  void placement_depositDate_cannotBeInTheFuture_eitherBasis() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("future deposit date");
    books.deposit(funding, client, "5000.00");
    var tomorrow = books.today().plusDays(1);

    assertPlacementRefused(
        funding,
        firmAccount,
        request(firmAccount, null, "3000.00", InvestmentBasis.FIRM_DISCRETION, tomorrow),
        PLACE_TITLE,
        "The deposit date cannot be in the future.");
    assertPlacementRefused(
        funding,
        clientAccount,
        request(clientAccount, client, "3000.00", InvestmentBasis.CLIENT_INSTRUCTION, tomorrow),
        PLACE_TITLE,
        "The deposit date cannot be in the future.");

    // Today is not the future
    books.as(
        ownerId,
        () ->
            assertThat(
                    investmentService
                        .placeInvestment(
                            funding,
                            request(
                                firmAccount,
                                null,
                                "3000.00",
                                InvestmentBasis.FIRM_DISCRETION,
                                books.today()))
                        .depositDate())
                .isEqualTo(books.today()));
  }

  /**
   * CodeRabbit cycle 3 (Minor): amounts are stored at cents, so a sub-cent principal is refused
   * with the payment path's rule ({@code validateAmount}) rather than rounded on save; trailing
   * zeros are a whole number of cents and pass.
   */
  @Test
  void placement_principal_hasAtMostTwoDecimalPlaces() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("principal scale");
    books.deposit(funding, client, "1000.00");

    assertPlacementRefused(
        funding,
        firmAccount,
        request(firmAccount, null, "100.005", InvestmentBasis.FIRM_DISCRETION),
        "Invalid amount",
        "Amount must not have more than 2 decimal places");

    for (var principal : List.of("100.00", "100.000")) {
      books.as(
          ownerId,
          () ->
              assertThat(
                      investmentService
                          .placeInvestment(
                              funding,
                              request(
                                  firmAccount, null, principal, InvestmentBasis.FIRM_DISCRETION))
                          .principal())
                  .isEqualByComparingTo("100.00"));
    }
  }

  // --- review F-4: closing an account while an investment is held through it ----------------

  @Test
  void investmentAccount_isNotClosed_whileItHoldsTheInvestment_andWithdrawalNeedsBothActive() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("close guard");
    books.deposit(funding, client, "1000.00");

    // Pending: nothing has reached the investment account yet, but the placement names it
    var placed = books.requestPlacement(funding, investmentAccount, client, "400.00");
    assertCloseRefused(investmentAccount, INVESTMENT_HELD_ON_CLOSE);

    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-400.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("400.00"),
        placed.depositTransactionId(),
        approverId);
    assertCloseRefused(investmentAccount, INVESTMENT_HELD_ON_CLOSE);

    // An account closed before the guard existed: the withdrawal is refused, nothing moves
    setAccountStatusDirect(investmentAccount, "CLOSED");
    assertWithdrawRefused(
        funding,
        investmentAccount,
        placed.id(),
        books.today(),
        "The trust account the investment was placed from and the investment account holding it"
            + " must both be active to withdraw it.");
    setAccountStatusDirect(investmentAccount, "ACTIVE");

    books.withdraw(
        funding,
        Expected.cashbookAndClient("400.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("-400.00"),
        placed.id(),
        books.today());
    books.as(
        ownerId,
        () ->
            assertThat(trustAccountService.closeTrustAccount(investmentAccount).status())
                .isEqualTo(TrustAccountStatus.CLOSED));
  }

  @Test
  void account_isNotClosed_whileItsCashbookIsNotZero() {
    var account = books.freshAccount(false);
    // Money held for no client (an unidentified deposit): every client ledger is zero
    books.unidentifiedDepositDirect(account, "250.00", TrustBooksFixture.POSTING_DATE);
    assertCloseRefused(account, "Non-zero cashbook balance of R250.00 must be resolved first.");
  }

  // --- reversal refused on the type (replaces G89-F86) -------------------------------------

  @Test
  void everyInvestmentRow_isRefusedReversal_onItsType() {
    var funding = books.freshAccount(false);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("reversal refused");
    books.deposit(funding, client, "5000.00");

    var clientPlaced = books.requestPlacement(funding, clientAccount, client, "1000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-1000.00"),
        clientAccount,
        Expected.cashbookAndInvestment("1000.00"),
        clientPlaced.depositTransactionId(),
        approverId);
    var firmPlaced = books.requestPlacement(funding, firmAccount, null, "500.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-500.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("500.00"),
        firmPlaced.depositTransactionId(),
        approverId);

    books.assertReversalRefused(
        funding, clientPlaced.depositTransactionId(), PLACEMENT_NOT_REVERSIBLE);
    books.assertReversalRefused(
        funding, firmPlaced.depositTransactionId(), PLACEMENT_NOT_REVERSIBLE);
    books.assertReversalRefused(
        clientAccount,
        rowOf(clientPlaced.id(), "INV_RECEIVE").getId(),
        INVESTMENT_ROW_NOT_REVERSIBLE);
    books.assertReversalRefused(
        firmAccount,
        rowOf(firmPlaced.id(), "FIRM_INV_RECEIVE").getId(),
        INVESTMENT_ROW_NOT_REVERSIBLE);

    books.withdraw(
        funding,
        Expected.cashbookAndClient("1000.00"),
        clientAccount,
        Expected.cashbookAndInvestment("-1000.00"),
        clientPlaced.id(),
        books.today());
    // After the withdrawal the placement still cannot be reversed (no double credit)
    books.assertReversalRefused(
        funding, clientPlaced.depositTransactionId(), PLACEMENT_NOT_REVERSIBLE);
    books.assertReversalRefused(
        funding, rowOf(clientPlaced.id(), "INV_RETURN").getId(), INVESTMENT_ROW_NOT_REVERSIBLE);
    books.assertReversalRefused(
        clientAccount,
        rowOf(clientPlaced.id(), "INV_WITHDRAW").getId(),
        INVESTMENT_ROW_NOT_REVERSIBLE);
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");
  }

  // --- reads --------------------------------------------------------------------------------

  @Test
  void list_acceptsTheFundingOrTheInvestmentAccount_andFiltersByBasis() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("list");
    books.deposit(funding, client, "2000.00");
    var firm = books.requestPlacement(funding, firmAccount, null, "300.00");
    var forClient = books.requestPlacement(funding, clientAccount, client, "400.00");

    assertThat(list(funding, null))
        .extracting(TrustInvestmentResponse::id)
        .containsExactlyInAnyOrder(firm.id(), forClient.id());
    assertThat(list(funding, InvestmentBasis.FIRM_DISCRETION))
        .extracting(TrustInvestmentResponse::id)
        .containsExactly(firm.id());
    assertThat(list(clientAccount, null))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.id()).isEqualTo(forClient.id());
              assertThat(r.clientName()).isEqualTo(client.name());
              assertThat(r.investmentAccountName()).startsWith("Trust Books Investment");
            });
    assertThat(list(firmAccount, null))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.clientId()).isNull();
              assertThat(r.clientName()).isNull();
            });
  }

  // --- helpers ------------------------------------------------------------------------------

  private PlaceInvestmentRequest request(
      UUID investmentAccount, Client client, String principal, InvestmentBasis basis) {
    return request(investmentAccount, client, principal, basis, TrustBooksFixture.POSTING_DATE);
  }

  private PlaceInvestmentRequest request(
      UUID investmentAccount,
      Client client,
      String principal,
      InvestmentBasis basis,
      LocalDate depositDate) {
    return new PlaceInvestmentRequest(
        investmentAccount,
        client == null ? null : client.id(),
        client == null ? null : client.matter(),
        "Trust Books Bank",
        "ACC-REFUSED",
        new BigDecimal(principal),
        new BigDecimal("0.0500"),
        depositDate,
        null,
        null,
        basis);
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} is running a trust_accounts
   * query — the second approval waiting on the funding account's row lock. Fails if the waiter
   * finishes first (it did not wait) or never blocks.
   */
  private void awaitBlockedOnTrustAccountLockHeldBy(int holderPid, AtomicBoolean waiterFinished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE '%trust_accounts%'",
              Long.class, holderPid);
      if (blocked != null && blocked > 0) {
        return;
      }
      assertThat(waiterFinished.get())
          .as("the second approval finished without waiting for the funding account lock")
          .isFalse();
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the second approval never blocked on the funding account lock");
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      assertThat(latch.await(30, TimeUnit.SECONDS)).as("latch released within 30 s").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void join(Thread thread) {
    try {
      thread.join(30_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    assertThat(thread.isAlive()).as("thread finished within 30 s").isFalse();
  }

  /** The placement is refused (400 {@code title} / {@code detail}); nothing moves on either. */
  private void assertPlacementRefused(
      UUID funding,
      UUID investmentAccount,
      PlaceInvestmentRequest request,
      String title,
      String detail) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                funding,
                Expected.none(),
                investmentAccount,
                Expected.none(),
                () ->
                    assertThatThrownBy(() -> investmentService.placeInvestment(funding, request))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(title);
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
  }

  /** Closing the account is refused (400 "Cannot close trust account"); it stays active. */
  private void assertCloseRefused(UUID accountId, String detail) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                accountId,
                Expected.none(),
                () ->
                    assertThatThrownBy(() -> trustAccountService.closeTrustAccount(accountId))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Cannot close trust account");
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM \"%s\".trust_accounts WHERE id = ?".formatted(books.schema()),
                String.class,
                accountId))
        .isEqualTo("ACTIVE");
  }

  /** Sets an account's status straight in the table (an account closed before the F-4 guard). */
  private void setAccountStatusDirect(UUID accountId, String status) {
    jdbc.update(
        "UPDATE \"%s\".trust_accounts SET status = ? WHERE id = ?".formatted(books.schema()),
        status,
        accountId);
  }

  /** The withdrawal is refused (400 "Cannot withdraw investment"); nothing moves on either. */
  private void assertWithdrawRefused(
      UUID funding, UUID investmentAccount, UUID investmentId, LocalDate date, String detail) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                funding,
                Expected.none(),
                investmentAccount,
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () ->
                                investmentService.withdrawInvestment(
                                    investmentId, new WithdrawInvestmentRequest(date)))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(WITHDRAW_TITLE);
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
  }

  private List<TrustInvestmentResponse> list(UUID accountId, InvestmentBasis basis) {
    @SuppressWarnings("unchecked")
    List<TrustInvestmentResponse>[] page = new List[1];
    books.as(
        ownerId,
        () ->
            page[0] =
                investmentService
                    .listInvestments(accountId, basis, PageRequest.of(0, 50))
                    .getContent());
    return page[0];
  }

  private TrustInvestmentResponse get(UUID investmentId) {
    var response = new TrustInvestmentResponse[1];
    books.as(ownerId, () -> response[0] = investmentService.getInvestment(investmentId));
    return response[0];
  }

  private TrustInvestment investment(UUID id) {
    return books.read(() -> investmentRepository.findById(id).orElseThrow());
  }

  private TrustTransaction row(UUID id) {
    return books.read(() -> transactionRepository.findById(id).orElseThrow());
  }

  /** The investment's one row of {@code type}. */
  private TrustTransaction rowOf(UUID investmentId, String type) {
    var id =
        jdbc.queryForObject(
            "SELECT id FROM \"%s\".trust_transactions WHERE investment_id = ? AND transaction_type = ?"
                .formatted(books.schema()),
            UUID.class,
            investmentId,
            type);
    return row(id);
  }

  private String matterName(Client client) {
    return jdbc.queryForObject(
        "SELECT name FROM \"%s\".projects WHERE id = ?".formatted(books.schema()),
        String.class,
        client.matter());
  }

  private AuditEvent audit(String eventType, UUID investmentId) {
    var events =
        books.read(
            () ->
                auditEventRepository
                    .findByFilter(
                        "trust_investment",
                        investmentId,
                        null,
                        eventType,
                        null,
                        null,
                        PageRequest.of(0, 10))
                    .getContent());
    assertThat(events).as(eventType).hasSize(1);
    return events.getFirst();
  }
}
