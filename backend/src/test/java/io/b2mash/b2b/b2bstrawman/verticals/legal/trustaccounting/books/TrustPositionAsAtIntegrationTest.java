package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 646C (task 646.17, architecture §16.3.3, §16.9.2 path 22, ADR-377): the trust
 * equation as at a date. Every term of EQ-2 is a sum over posted rows dated on or before the date;
 * outstanding items are dated by the <b>statement line's</b> date, so a reconciliation of a month
 * gives that month's answer however much later matching is done (path 22), and a reversed pair that
 * never reached the bank is not an outstanding item (649B F4).
 *
 * <p>One harness tenant; a fresh account per test; the class ends with {@code
 * assertFirmBalanced(today)}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustPositionAsAtIntegrationTest {

  private static final String ORG_ID = "org_trust_position_as_at";

  /** Its own tenant for the otherNonClient case (the harness oracle has no such term). */
  private static final String OTHER_ORG_ID = "org_trust_position_as_at_other";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);
  private static final LocalDate APRIL_END = LocalDate.of(2026, 4, 30);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private TrustBooksService trustBooksService;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;

  private TrustBooksFixture.Tenant otherBooks;
  private UUID otherMemberId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Position As At Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_tpaa_owner", "tpaa@test.com", "TPAA Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_tpaa_approver",
                "tpaa_approver@test.com",
                "TPAA Approver",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, memberId);

    var otherSchema =
        provisioningService
            .provisionTenant(OTHER_ORG_ID, "Trust Position As At Other Org", null)
            .schemaName();
    otherMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                OTHER_ORG_ID,
                "user_tpaa_other_owner",
                "tpaa_other@test.com",
                "TPAA Other Owner",
                "owner"));
    fixture.enableTrustModule(otherSchema);
    otherBooks = fixture.tenant(otherSchema, OTHER_ORG_ID, otherMemberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- path 22: period-crossing outstanding item (R6) -------------------------------------------

  /**
   * Path 22 (R6). A deposit of R1 000 and a payment of R400 are both dated 1 March. The March
   * statement shows the deposit (closing R1 000); the payment reaches the bank on 2 April (April
   * statement, closing R600). As at 31 March the payment is outstanding even though it is matched —
   * its line is dated in April — so March balances at R600; as at 30 April nothing is outstanding.
   * EQ-1 holds at both period ends.
   */
  @Test
  void path22_paymentMatchedToANextMonthLine_isOutstandingAtTheFirstMonthEndOnly() {
    var account = books.freshAccount(false);
    var client = books.newClient("path 22");
    var depositId = books.deposit(account, client, "1000.00");
    var paymentId = books.requestPayment(account, client, "400.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-400.00"));

    var march =
        importStatement(
            account, "path22-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,P22-DEP");
    var april =
        importStatement(
            account, "path22-april.csv", "02/04/2026,Payment made,-400.00,600.00,P22-PAY");
    match(march, depositId);
    match(april, paymentId);

    var marchRecon = reconcile(account, MARCH_END, march.id());
    assertThat(marchRecon.bankBalance()).as("March bank").isEqualByComparingTo("1000.00");
    assertThat(marchRecon.outstandingDeposits()).as("March o/s deposits").isEqualByComparingTo("0");
    assertThat(marchRecon.outstandingPayments())
        .as("March o/s payments (its line is dated 2 April)")
        .isEqualByComparingTo("400.00");
    assertThat(marchRecon.adjustedBankBalance()).as("March adjusted").isEqualByComparingTo("600");
    assertThat(marchRecon.cashbookBalance()).as("March cashbook").isEqualByComparingTo("600.00");
    assertThat(marchRecon.isBalanced()).as("March balanced").isTrue();
    books.as(memberId, () -> harness.assertBankAgrees(account, march.id(), MARCH_END));
    assertPosition(account, MARCH_END, "600.00", "600.00", "0", "0", "0", "0");
    assertThat(marchRecon.creditorBreakdown().total())
        .as("March creditors")
        .isEqualByComparingTo("600");
    assertThat(marchRecon.bankToCashbookDifference()).isEqualByComparingTo("0");
    assertThat(marchRecon.cashbookToCreditorsDifference()).isEqualByComparingTo("0");
    assertThat(marchRecon.difference()).isEqualByComparingTo("0");

    var aprilRecon = reconcile(account, APRIL_END, april.id());
    assertThat(aprilRecon.bankBalance()).as("April bank").isEqualByComparingTo("600.00");
    assertThat(aprilRecon.outstandingPayments()).as("April o/s payments").isEqualByComparingTo("0");
    assertThat(aprilRecon.adjustedBankBalance()).as("April adjusted").isEqualByComparingTo("600");
    assertThat(aprilRecon.cashbookBalance()).as("April cashbook").isEqualByComparingTo("600.00");
    assertThat(aprilRecon.isBalanced()).as("April balanced").isTrue();
    books.as(memberId, () -> harness.assertBankAgrees(account, april.id(), APRIL_END));
    assertPosition(account, APRIL_END, "600.00", "600.00", "0", "0", "0", "0");

    // Re-running March after April was matched still gives March's answer (ADR-377)
    var bank = new TrustBooksService.BankPosition[1];
    books.as(
        memberId, () -> bank[0] = trustBooksService.bankPosition(account, march.id(), MARCH_END));
    assertThat(bank[0].outstandingPayments()).as("March re-run").isEqualByComparingTo("400.00");
    assertThat(bank[0].adjustedBankBalance()).as("March re-run").isEqualByComparingTo("600.00");
  }

  // --- 649B F4: a reversed matched deposit (R7) -------------------------------------------------

  /**
   * 649B F4 (R7). A deposit of R1 000 on 1 March reached the bank (matched to a March line); it is
   * reversed by a contra dated 10 March, approved by a second member, which is on no line. As at 31
   * March the contra is an outstanding payment of R1 000: adjusted bank R0 = cashbook R0.
   */
  @Test
  void reversedMatchedDeposit_contraIsAnOutstandingPayment() {
    var account = books.freshAccount(false);
    var client = books.newClient("F4");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(account, "f4-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,F4");
    match(march, depositId);

    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 10));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-1000.00"));

    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.outstandingDeposits()).as("o/s deposits").isEqualByComparingTo("0");
    assertThat(recon.outstandingPayments())
        .as("o/s payments (the unmatched contra)")
        .isEqualByComparingTo("1000.00");
    assertThat(recon.adjustedBankBalance()).as("adjusted").isEqualByComparingTo("0");
    assertThat(recon.cashbookBalance()).as("cashbook").isEqualByComparingTo("0");
    assertThat(recon.isBalanced()).as("balanced").isTrue();
    books.as(memberId, () -> harness.assertBankAgrees(account, march.id(), MARCH_END));
  }

  // --- every term as at a date ---------------------------------------------------------------

  /**
   * Every EQ-2 term is a sum over posted rows dated on or before the date: a client deposit (1
   * March), Fund interest (10 April), a bank charge and an unidentified deposit (10 May) and a
   * section 86(4) investment receipt (10 June) each enter on their own date and not before; the
   * account balances at every date and the service agrees with the oracle at every date.
   */
  @Test
  void everyTerm_isCountedFromItsOwnDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("as at");
    var depositId = books.deposit(account, client, "1000.00");
    books.postNonClientDirect(account, "FUND_INTEREST", "50.00", LocalDate.of(2026, 4, 10));
    books.postNonClientDirect(account, "BANK_CHARGE", "20.00", LocalDate.of(2026, 5, 10));
    books.postNonClientDirect(account, "UNIDENTIFIED_DEPOSIT", "300.00", LocalDate.of(2026, 5, 10));
    var investment = books.investmentDirect(account, client, depositId);
    books.postLedgerRowDirect(
        account,
        "INV_RECEIVE",
        "200.00",
        LocalDate.of(2026, 6, 10),
        client.id(),
        client.matter(),
        null,
        investment);

    assertPosition(account, LocalDate.of(2026, 2, 28), "0", "0", "0", "0", "0", "0");
    assertPosition(account, MARCH_END, "1000.00", "1000.00", "0", "0", "0", "0");
    assertPosition(account, APRIL_END, "1050.00", "1000.00", "0", "50.00", "0", "0");
    assertPosition(
        account, LocalDate.of(2026, 5, 31), "1330.00", "1000.00", "0", "30.00", "300.00", "0");
    assertPosition(
        account, LocalDate.of(2026, 6, 30), "1530.00", "1000.00", "200.00", "30.00", "300.00", "0");
    books.assertServicesAgree(account);
  }

  /** An unknown account is a 404, as everywhere in the trust module. */
  @Test
  void position_ofAnUnknownAccount_isNotFound() {
    var unknown = UUID.randomUUID();
    books.as(
        memberId,
        () ->
            assertThatThrownBy(() -> trustBooksService.position(unknown, MARCH_END))
                .isInstanceOf(ResourceNotFoundException.class));
  }

  // --- outstanding items as at a date --------------------------------------------------------

  /**
   * A reversed deposit and its contra, neither on a statement line, cancel: as at a date after
   * both, nothing is outstanding; as at a date between them, the original alone is an outstanding
   * deposit (its contra is not yet dated).
   */
  @Test
  void outstanding_aCancellingReversedPairIsNotOutstanding() {
    var account = books.freshAccount(false);
    var client = books.newClient("cancelling pair");
    var depositId = books.deposit(account, client, "800.00");
    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 10));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-800.00"));

    var between = books.read(() -> ledgerQueries.outstanding(account, LocalDate.of(2026, 3, 5)));
    assertThat(between.getOutstandingDeposits())
        .as("between: deposits")
        .isEqualByComparingTo("800");
    assertThat(between.getOutstandingPayments()).as("between: payments").isEqualByComparingTo("0");

    var after = books.read(() -> ledgerQueries.outstanding(account, MARCH_END));
    assertThat(after.getOutstandingDeposits()).as("after: deposits").isEqualByComparingTo("0");
    assertThat(after.getOutstandingPayments()).as("after: payments").isEqualByComparingTo("0");
    books.assertServicesAgree(account, LocalDate.of(2026, 3, 5));
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- firm position and EQ-3 ----------------------------------------------------------------

  /**
   * A section 86(3) placement on 1 June: R500 leaves account A ({@code FIRM_INV_PLACE}) and is held
   * on account B ({@code FIRM_INV_RECEIVE}). Each account balances with its inter-account term
   * (−500 / +500); firm-wide the term sums to zero (EQ-3) and the firm balances. Firm figures are
   * asserted by containment and invariants, never counts (shared tenant).
   */
  @Test
  void firmPosition_interAccountCancelsFirmWide() {
    var placing = books.freshAccount(false);
    var holding = books.freshAccount(false);
    var client = books.newClient("EQ-3");
    var depositId = books.deposit(placing, client, "1000.00");
    var investment = books.investmentDirect(holding, client, depositId);
    var placedOn = LocalDate.of(2026, 6, 1);
    books.postLedgerRowDirect(
        placing, "FIRM_INV_PLACE", "500.00", placedOn, null, null, null, investment);
    books.postLedgerRowDirect(
        holding, "FIRM_INV_RECEIVE", "500.00", placedOn, null, null, null, investment);

    assertPosition(placing, LocalDate.of(2026, 5, 31), "1000.00", "1000.00", "0", "0", "0", "0");
    assertPosition(
        placing, LocalDate.of(2026, 6, 30), "500.00", "1000.00", "0", "0", "0", "-500.00");
    assertPosition(holding, LocalDate.of(2026, 6, 30), "500.00", "0", "0", "0", "0", "500.00");

    var firm = new TrustBooksService.FirmTrustPosition[1];
    books.as(memberId, () -> firm[0] = trustBooksService.firmPosition(LocalDate.of(2026, 6, 30)));
    assertThat(firm[0].accounts())
        .extracting(TrustBooksService.TrustPosition::trustAccountId)
        .contains(placing, holding);
    assertThat(firm[0].interAccount()).as("EQ-3: firm inter-account").isEqualByComparingTo("0");
    assertThat(firm[0].difference()).as("firm difference").isEqualByComparingTo("0");
    assertThat(firm[0].cashbook())
        .as("firm cashbook = Σ account cashbooks")
        .isEqualByComparingTo(
            firm[0].accounts().stream()
                .map(TrustBooksService.TrustPosition::cashbook)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  // --- otherNonClient (its own tenant: the oracle has no such term) ---------------------------

  /**
   * {@code otherNonClient} is counted from its first row (ruling O5): a posted {@code NON_CLIENT}
   * row on a creditor account of a third type enters the term — and the creditor total — on its own
   * date. In its own tenant, which never runs the harness's {@code assertFirmBalanced}.
   */
  @Test
  void otherNonClient_isCountedFromItsFirstRow() {
    var account = otherBooks.freshAccount(false);
    var creditor = otherBooks.openCreditorAccountDirect(account, "TEST_OTHER");
    otherBooks.postLedgerRowDirect(
        account, "FUND_INTEREST", "75.00", LocalDate.of(2026, 3, 15), null, null, creditor, null);

    var position = new TrustBooksService.TrustPosition[2];
    otherBooks.as(
        otherMemberId,
        () -> {
          position[0] = trustBooksService.position(account, LocalDate.of(2026, 3, 14));
          position[1] = trustBooksService.position(account, MARCH_END);
        });
    assertThat(position[0].otherNonClient()).as("before").isEqualByComparingTo("0");
    assertThat(position[1].otherNonClient()).as("other non-client").isEqualByComparingTo("75.00");
    assertThat(position[1].fundLedger()).as("fund ledger").isEqualByComparingTo("0");
    assertThat(position[1].cashbook()).as("cashbook").isEqualByComparingTo("75.00");
    assertThat(position[1].creditorTotal()).as("creditor total").isEqualByComparingTo("75.00");
    assertThat(position[1].difference()).as("difference").isEqualByComparingTo("0");
  }

  /**
   * {@code TrustBooksService.position} as at {@code asAt}: each term, a zero difference, and the
   * service agrees with the oracle (every term) at that date.
   */
  private void assertPosition(
      UUID account,
      LocalDate asAt,
      String cashbook,
      String clientLedgers,
      String investmentCreditors,
      String fundLedger,
      String unidentified,
      String interAccount) {
    var p = new TrustBooksService.TrustPosition[1];
    books.as(memberId, () -> p[0] = trustBooksService.position(account, asAt));
    var what = "as at " + asAt + ": ";
    assertThat(p[0].cashbook()).as(what + "cashbook").isEqualByComparingTo(cashbook);
    assertThat(p[0].clientLedgers())
        .as(what + "client ledgers")
        .isEqualByComparingTo(clientLedgers);
    assertThat(p[0].investmentCreditors())
        .as(what + "investment creditors")
        .isEqualByComparingTo(investmentCreditors);
    assertThat(p[0].fundLedger()).as(what + "fund ledger").isEqualByComparingTo(fundLedger);
    assertThat(p[0].unidentifiedDeposits())
        .as(what + "unidentified")
        .isEqualByComparingTo(unidentified);
    assertThat(p[0].interAccount()).as(what + "inter-account").isEqualByComparingTo(interAccount);
    assertThat(p[0].otherNonClient()).as(what + "other non-client").isEqualByComparingTo("0");
    assertThat(p[0].difference()).as(what + "difference").isEqualByComparingTo("0");
    books.assertServicesAgree(account, asAt);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /** Imports a one-line FNB statement through the production import (as the maker). */
  private BankStatementResponse importStatement(UUID account, String fileName, String line) {
    var csv =
        """
        FNB Trust Account Statement - Account 62300000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(line);
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  /** Matches the statement's only line to {@code transactionId} (production manual match). */
  private void match(BankStatementResponse statement, UUID transactionId) {
    var lineId = statement.lines().getFirst().id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  /** Creates and calculates a reconciliation of {@code account} as at {@code periodEnd}. */
  private TrustReconciliationResponse reconcile(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] = reconciliationService.calculateReconciliation(created.id());
        });
    return recon[0];
  }
}
