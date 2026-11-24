package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.dashboard.DashboardService;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrustBalanceTrendResponse;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.CreditorLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.ClientLedgerCardResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.LedgerStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.ClientLedgerStatementQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.Section35DataPackQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.TrustInterestChargesScheduleQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.TrustReceiptsPaymentsQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.UnidentifiedDepositsAgeQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Phase 93 / 657A (task 657.7, §17.3.15): the exhaustive folds over a take-on, on the harness's
 * take-on path (§17.3.13 figures, C floating with today). The client ledger card's {@code
 * broughtForward} keeps {@code derivedBalance() = clientBalance} with a cutover cheque standing and
 * after it is cancelled; the Rule 54.28 schedule's month of C opens with the Fund ledger brought
 * forward and balances; the Rule 54.15 list carries the b/f figure on each client's first line; a
 * taken-on section 86(4) investment's placement is its {@code TAKEON_INVESTMENT}.
 *
 * <p>657D (task 657.31, §17.3.15): Receipts &amp; Payments, Section 35 part 1 and the staff
 * statements show the take-on as one brought-forward line; the trust trend starts at the month of
 * C; the age report bands a taken-on item by its original date.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnReportsOverCutoverIntegrationTest {

  private static final String ORG_ID = "org_takeon_reports_over_cutover";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private ClientLedgerService clientLedgerService;
  @Autowired private TrustInterestChargesScheduleQuery scheduleQuery;
  @Autowired private TrustMonthlyListService monthlyListService;
  @Autowired private TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  @Autowired private Section35DataPackQuery section35Query;
  @Autowired private ClientLedgerStatementQuery clientLedgerStatementQuery;
  @Autowired private UnidentifiedDepositsAgeQuery ageQuery;
  @Autowired private DashboardService dashboardService;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Take-on Reports Over Cutover Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_takeon_reports_owner",
                "takeon_reports@test.com",
                "Reports Owner",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  private LocalDate cutover() {
    return YearMonth.from(books.today()).minusMonths(4).atEndOfMonth();
  }

  @Test
  void ledgerCard_broughtForwardKeepsTheIdentity_beforeAndAfterTheCutoverChequeIsCancelled() {
    var takeOn = books.takeOnSection17313(cutover());
    var a = takeOn.a();

    var card = card(a.id(), takeOn.g());
    assertThat(card.broughtForward()).isEqualByComparingTo("300000.00");
    assertThat(card.totalDeposits()).isEqualByComparingTo("0");
    assertThat(card.totalPayments()).isEqualByComparingTo("0");
    assertThat(card.balance()).isEqualByComparingTo("300000.00");
    assertThat(card.balance()).isEqualByComparingTo(books.clientLedger(takeOn.g(), a));
    assertThat(card.investedBalance()).isEqualByComparingTo("200000.00");

    // the cheque is cancelled today: the money comes back into the brought-forward figure
    books.reverse(takeOn.g(), takeOn.payUnpres(), Expected.cashbookAndClient("+20000.00"));
    var after = card(a.id(), takeOn.g());
    assertThat(after.broughtForward()).isEqualByComparingTo("320000.00");
    assertThat(after.balance()).isEqualByComparingTo("320000.00");
    assertThat(after.balance()).isEqualByComparingTo(books.clientLedger(takeOn.g(), a));

    // client B: the deposit in transit and its offset net to nothing while it stands
    assertThat(card(takeOn.b().id(), takeOn.g()).broughtForward())
        .isEqualByComparingTo("150000.00");
  }

  @Test
  void interestAndChargesSchedule_theMonthOfTheCutoverOpensWithTheFundBroughtForward() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    ReportResult result =
        books.read(
            () ->
                scheduleQuery.executeAll(
                    new HashMap<>(
                        Map.of(
                            "trust_account_id",
                            takeOn.g().toString(),
                            "dateFrom",
                            c.withDayOfMonth(1).toString(),
                            "dateTo",
                            c.toString()))));
    var rows =
        result.rows().stream().filter(r -> takeOn.g().equals(r.get("trustAccountId"))).toList();
    assertThat(rows).hasSize(1);
    var month = rows.getFirst();
    assertThat(month.get("identityHolds")).isEqualTo(true);
    assertThat(month.get("check")).isEqualTo("Balanced");
    assertThat((java.math.BigDecimal) month.get("unexplainedMovement")).isEqualByComparingTo("0");
    assertThat((java.math.BigDecimal) month.get("fundBroughtForward"))
        .isEqualByComparingTo("1250.00");
    assertThat((java.math.BigDecimal) month.get("fundCarriedForward"))
        .isEqualByComparingTo("1250.00");
    assertThat((String) month.get("month"))
        .endsWith(" — Brought forward at take-on (" + IntegrityMessages.date(c) + ")");
    // one month: the period's totals are that month's figures
    var totals = block(result, takeOn.g());
    assertThat(money(totals, "fundBroughtForward")).isEqualByComparingTo("1250.00");
    assertTotalsIdentity(totals);
    assertThat(totals.get("check")).isEqualTo("Balanced");
  }

  @Test
  void interestAndChargesSchedule_cutoverInALaterMonth_periodTotalsCarryTheTakeOnOnce() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var result = schedule(takeOn.g(), c.minusMonths(2).withDayOfMonth(1), c.plusMonths(1));
    var rows =
        result.rows().stream().filter(r -> takeOn.g().equals(r.get("trustAccountId"))).toList();
    assertThat(rows).hasSize(4);
    assertThat(money(rows.getFirst(), "fundBroughtForward")).isEqualByComparingTo("0");
    assertThat(money(rows.get(2), "fundBroughtForward")).isEqualByComparingTo("1250.00");

    var totals = block(result, takeOn.g());
    // the period's b/f is the ledger's at its start (nil) plus the Fund brought forward at take-on
    assertThat(money(totals, "fundBroughtForward")).isEqualByComparingTo("1250.00");
    assertThat(money(totals, "fundCarriedForward")).isEqualByComparingTo("1250.00");
    assertTotalsIdentity(totals);
    assertThat(totals.get("identityHolds")).isEqualTo(true);
    assertThat(totals.get("check")).isEqualTo("Balanced");
  }

  @Test
  void interestAndChargesSchedule_cutoverInTheFirstMonth_periodTotalsDoNotCountTheTakeOnTwice() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var result =
        schedule(takeOn.g(), c.withDayOfMonth(1), YearMonth.from(c).plusMonths(2).atEndOfMonth());
    var rows =
        result.rows().stream().filter(r -> takeOn.g().equals(r.get("trustAccountId"))).toList();
    assertThat(rows).hasSize(3);
    assertThat(money(rows.getFirst(), "fundBroughtForward")).isEqualByComparingTo("1250.00");

    var totals = block(result, takeOn.g());
    assertThat(money(totals, "fundBroughtForward")).isEqualByComparingTo("1250.00");
    assertThat(money(totals, "fundCarriedForward")).isEqualByComparingTo("1250.00");
    assertTotalsIdentity(totals);
    assertThat(totals.get("check")).isEqualTo("Balanced");
  }

  @Test
  void monthlyList_carriesTheBroughtForwardOnEachClientsFirstLine() {
    var takeOn = books.takeOnSection17313(cutover());

    var list = new TrustMonthlyListService.CreditorList[1];
    books.as(ownerId, () -> list[0] = monthlyListService.liveList(takeOn.g(), takeOn.cutover()));
    var lines = list[0].lines();

    assertThat(clientLine(lines, takeOn.a().id()).broughtForward())
        .isEqualByComparingTo("300000.00");
    assertThat(clientLine(lines, takeOn.b().id()).broughtForward())
        .isEqualByComparingTo("150000.00");
    assertThat(clientLine(lines, takeOn.c().id()).broughtForward())
        .isEqualByComparingTo("49500.00");
    assertThat(lines)
        .filteredOn(l -> l.kind() != CreditorLine.Kind.CLIENT)
        .allSatisfy(l -> assertThat(l.broughtForward()).as("%s", l.kind()).isNull());
  }

  @Test
  void investedStatement_aTakenOnInvestmentIsPlacedByItsOpeningRow() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var statement = new ClientLedgerService.InvestedStatement[1];
    books.as(
        ownerId,
        () ->
            statement[0] =
                clientLedgerService.getClientInvestedStatement(
                    takeOn.a().id(), takeOn.g(), c.withDayOfMonth(1), c));
    assertThat(statement[0].openingBalance()).isEqualByComparingTo("0");
    assertThat(statement[0].capitalPlaced()).isEqualByComparingTo("200000.00");
    assertThat(statement[0].closingBalance()).isEqualByComparingTo("200000.00");
  }

  @Test
  void ledgerCard_aClientTakenOnWithOnlyASection864Investment_showsItsInvestedBalance() {
    var c = cutover();
    var g = books.freshAccount(false);
    var i4 = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var d = books.newClient("TOD");
    var batch = books.takeOnBatch(g, c);
    var authority = books.authority(batch, c);
    books.takenOnInvestment(authority, g, i4, d, "75000.00", c.minusMonths(3));

    var card = card(d.id(), g);
    assertThat(card.investedBalance()).isEqualByComparingTo("75000.00");
    assertThat(card.balance()).isEqualByComparingTo("0");
    assertThat(card.broughtForward()).isEqualByComparingTo("0");

    // a client with nothing on the account still reads the zero card
    var empty = card(books.newClient("TOE").id(), g);
    assertThat(empty.id()).isNull();
    assertThat(empty.investedBalance()).isEqualByComparingTo("0");
  }

  /**
   * 657D (657.27 red first, §17.3.15): Receipts &amp; Payments over the month containing C lists
   * the take-on as one brought-forward line dated C (credit CB = R403 500,00), never as receipts
   * and payments; the totals exclude it and {@code summary.broughtForward} carries it.
   */
  @Test
  void receiptsAndPayments_theMonthOfTheCutoverShowsOneBroughtForwardLine_notTheTakeOnRows() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var result = receiptsPayments(takeOn.g(), c.withDayOfMonth(1), c);

    assertThat(money(result.summary(), "totalReceipts")).isEqualByComparingTo("0");
    assertThat(money(result.summary(), "totalPayments")).isEqualByComparingTo("0");
    assertThat(result.rows()).hasSize(1);
    var bf = result.rows().getFirst();
    assertThat(bf.get("broughtForward")).isEqualTo(true);
    assertThat(bf.get("date")).isEqualTo(c.toString());
    assertThat(bf.get("type")).isEqualTo("Balance brought forward — take-on");
    assertThat(money(bf, "credit")).isEqualByComparingTo("403500.00");
    assertThat(money(bf, "balance")).isEqualByComparingTo("403500.00");
    assertThat(money(result.summary(), "broughtForward")).isEqualByComparingTo("403500.00");
    assertThat(result.summary().get("transactionCount")).isEqualTo(0);
  }

  /** 657D (A-1): a range containing C and the month after: the b/f line, then November only. */
  @Test
  void receiptsAndPayments_aRangeContainingTheCutover_opensWithTheBroughtForwardLine() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    var mEnd = YearMonth.from(c).plusMonths(1).atEndOfMonth();
    books.deposit(takeOn.g(), takeOn.c(), "60000.00", c.plusDays(10));

    var result = receiptsPayments(takeOn.g(), c.withDayOfMonth(1), mEnd);

    assertThat(result.rows()).hasSize(2);
    var bf = result.rows().getFirst();
    assertThat(bf.get("broughtForward")).isEqualTo(true);
    assertThat(bf.get("date")).isEqualTo(c.toString());
    assertThat(money(bf, "credit")).isEqualByComparingTo("403500.00");
    assertThat(money(bf, "balance")).isEqualByComparingTo("403500.00");
    var deposit = result.rows().get(1);
    assertThat(deposit.get("broughtForward")).isNull();
    assertThat(money(deposit, "credit")).isEqualByComparingTo("60000.00");
    assertThat(money(deposit, "balance")).isEqualByComparingTo("463500.00");
    assertThat(money(result.summary(), "totalReceipts")).isEqualByComparingTo("60000.00");
    assertThat(money(result.summary(), "totalPayments")).isEqualByComparingTo("0");
    assertThat(money(result.summary(), "netMovement")).isEqualByComparingTo("60000.00");
    assertThat(result.summary().get("transactionCount")).isEqualTo(1);
    assertThat(money(result.summary(), "broughtForward")).isEqualByComparingTo("403500.00");
    assertThat(result.summary().get("broughtForwardNote"))
        .isEqualTo(
            "The opening position taken on at "
                + IntegrityMessages.date(c)
                + " is shown as one brought-forward line, not as receipts.");
  }

  /** 657D (A-1, P-21): a range starting after C is unchanged — no b/f line, no take-on rows. */
  @Test
  void receiptsAndPayments_theMonthsAfterTheCutover_haveNoBroughtForwardLine() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    var first = YearMonth.from(c).plusMonths(1);
    books.deposit(takeOn.g(), takeOn.c(), "60000.00", c.plusDays(10));

    var month1 = receiptsPayments(takeOn.g(), first.atDay(1), first.atEndOfMonth());
    assertThat(month1.rows()).hasSize(1);
    assertThat(month1.rows().getFirst().get("broughtForward")).isNull();
    assertThat(money(month1.rows().getFirst(), "credit")).isEqualByComparingTo("60000.00");
    assertThat(month1.summary()).doesNotContainKeys("broughtForward", "broughtForwardNote");

    var month2 =
        receiptsPayments(
            takeOn.g(), first.plusMonths(1).atDay(1), first.plusMonths(1).atEndOfMonth());
    assertThat(month2.rows()).isEmpty();
    assertThat(month2.summary()).doesNotContainKeys("broughtForward", "broughtForwardNote");
  }

  /** 657D (A-3): Section 35 part 1 prints the b/f line; the section total excludes it. */
  @Test
  void section35_partOne_printsTheBroughtForwardLine_andItsTotalExcludesIt() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    books.deposit(takeOn.g(), takeOn.c(), "60000.00", c.plusDays(10));
    var yearEnd = YearMonth.from(c).plusMonths(1).atEndOfMonth();

    var result =
        books.read(
            () ->
                section35Query.executeAll(
                    new HashMap<>(
                        Map.of(
                            "trust_account_id",
                            takeOn.g().toString(),
                            "financial_year_end",
                            yearEnd.toString()))));
    var part1 =
        result.rows().stream()
            .filter(r -> "Trust Receipts & Payments".equals(r.get("_section")))
            .toList();
    assertThat(part1).hasSize(3);
    assertThat(part1.getFirst().get("label"))
        .isEqualTo(IntegrityMessages.date(c) + " · Balance brought forward — take-on");
    assertThat(money(part1.getFirst(), "amount")).isEqualByComparingTo("403500.00");
    assertThat(money(part1.get(1), "amount")).isEqualByComparingTo("60000.00");
    var total = part1.getLast();
    assertThat(total.get("_total")).isEqualTo(true);
    assertThat(money(total, "amount")).isEqualByComparingTo("60000.00");
    // the section's row count is the movements only, as its transactionCount
    @SuppressWarnings("unchecked")
    var meta =
        ((List<Map<String, Object>>) result.summary().get("sections"))
            .stream()
                .filter(m -> "Trust Receipts & Payments".equals(m.get("sectionName")))
                .findFirst()
                .orElseThrow();
    assertThat(meta.get("rowCount")).isEqualTo(1);
    assertThat(meta.get("transactionCount")).isEqualTo(1);
  }

  /**
   * 657D (§17.3.15, A-7): the staff statement for Client A over a range containing C has one b/f
   * line (R300 000,00 = 300 000 − 20 000 + 20 000), no item or offset rows, and closes at the
   * derived balance; a later cancellation of the cutover cheque is an ordinary REVERSAL after it.
   */
  @Test
  void staffStatement_foldsTheClientsTakeOnRowsIntoOneBroughtForwardLine() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    var a = takeOn.a();

    var statement = statement(a.id(), takeOn.g(), c.withDayOfMonth(1), books.today());
    assertThat(statement.openingBalance()).isEqualByComparingTo("0");
    assertThat(statement.transactions()).hasSize(1);
    var bf = statement.transactions().getFirst();
    assertThat(bf.transactionType()).isEqualTo("TAKEON_CLIENT");
    assertThat(bf.transactionId()).isEqualTo(takeOn.clientRowA());
    assertThat(bf.description()).isEqualTo("Balance brought forward — take-on");
    assertThat(bf.transactionDate()).isEqualTo(c);
    assertThat(bf.amount()).isEqualByComparingTo("300000.00");
    assertThat(bf.ledgerEffect()).isEqualByComparingTo("300000.00");
    assertThat(statement.closingBalance()).isEqualByComparingTo(books.clientLedger(takeOn.g(), a));

    // the first post-cutover month alone opens at the take-on balance, with no take-on row
    var first = YearMonth.from(c).plusMonths(1);
    var month = statement(a.id(), takeOn.g(), first.atDay(1), first.atEndOfMonth());
    assertThat(month.openingBalance()).isEqualByComparingTo("300000.00");
    assertThat(month.transactions()).isEmpty();

    // the report query (client-ledger-statement) folds the same way
    var report = clientStatement(a.id(), takeOn.g(), c.withDayOfMonth(1), books.today());
    var rows = report.rows();
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("broughtForward")).isEqualTo(true);
    assertThat(rows.getFirst().get("type")).isEqualTo("Balance brought forward — take-on");
    assertThat(money(rows.getFirst(), "credit")).isEqualByComparingTo("300000.00");
    assertThat(money(rows.getFirst(), "debit")).isEqualByComparingTo("0");
    // one brought-forward figure, never a movement: not in the totals nor the count (as R&P)
    assertThat(money(report.summary(), "totalCredits")).isEqualByComparingTo("0");
    assertThat(money(report.summary(), "totalDebits")).isEqualByComparingTo("0");
    assertThat(report.summary().get("transactionCount")).isEqualTo(0);
    assertThat(money(report.summary(), "broughtForward")).isEqualByComparingTo("300000.00");
    assertThat(report.summary().get("broughtForwardNote"))
        .isEqualTo(TrustReceiptsPaymentsQuery.broughtForwardNote(c));
    assertThat(money(report.summary(), "closingBalance")).isEqualByComparingTo("300000.00");

    // the cheque is cancelled today: an ordinary REVERSAL after the brought-forward line
    books.reverse(takeOn.g(), takeOn.payUnpres(), Expected.cashbookAndClient("+20000.00"));
    var after = statement(a.id(), takeOn.g(), c.withDayOfMonth(1), books.today());
    assertThat(after.transactions()).hasSize(2);
    assertThat(after.transactions().getFirst().amount()).isEqualByComparingTo("300000.00");
    assertThat(after.transactions().get(1).transactionType()).isEqualTo("REVERSAL");
    assertThat(after.transactions().get(1).ledgerEffect()).isEqualByComparingTo("20000.00");
    assertThat(after.closingBalance()).isEqualByComparingTo("320000.00");
    assertThat(after.closingBalance()).isEqualByComparingTo(books.clientLedger(takeOn.g(), a));
  }

  /** 657D: the interest-and-charges schedule for the month after C on G balances. */
  @Test
  void interestAndChargesSchedule_theMonthAfterTheCutoverBalances() {
    var takeOn = books.takeOnSection17313(cutover());
    var first = YearMonth.from(takeOn.cutover()).plusMonths(1);

    var result = schedule(takeOn.g(), first.atDay(1), first.atEndOfMonth());
    var totals = block(result, takeOn.g());
    assertThat(money(totals, "fundBroughtForward")).isEqualByComparingTo("1250.00");
    assertTotalsIdentity(totals);
    assertThat(totals.get("check")).isEqualTo("Balanced");
  }

  /** 657D (A-2): the trust trend of a taken-on account starts at the month of C. */
  @Test
  void trustBalanceTrend_startsAtTheMonthOfTheCutover() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var trend = new TrustBalanceTrendResponse[1];
    books.as(ownerId, () -> trend[0] = dashboardService.getTrustBalanceTrend(takeOn.g(), 12));
    assertThat(trend[0].takenOnAt()).isEqualTo(c);
    assertThat(trend[0].months().getFirst().month()).isEqualTo(YearMonth.from(c).toString());
    assertThat(trend[0].months().getFirst().cashbookBalance()).isEqualByComparingTo("403500.00");
    assertThat(trend[0].months())
        .hasSize(
            (int) YearMonth.from(c).until(YearMonth.from(books.today()), ChronoUnit.MONTHS) + 1);
    assertThat(trend[0].openingCashbookBalance()).isEqualByComparingTo("0");
  }

  /** 657D (age report unchanged): an item received 500 days before C bands "Over one year". */
  @Test
  void unidentifiedAgeReport_bandsATakenOnItemByItsOriginalDate() {
    var takeOn = books.takeOnSection17313(cutover());

    var result =
        books.read(
            () ->
                ageQuery.executeAll(
                    new HashMap<>(
                        Map.of(
                            "trust_account_id",
                            takeOn.g().toString(),
                            "asOfDate",
                            takeOn.cutover().toString()))));
    var old =
        result.rows().stream()
            .filter(r -> takeOn.unidentifiedOld().itemId().equals(r.get("unidentifiedDepositId")))
            .findFirst()
            .orElseThrow();
    assertThat(old.get("band")).isEqualTo("Over one year");
    assertThat(old.get("receivedDate")).isEqualTo(takeOn.cutover().minusDays(500).toString());
  }

  private LedgerStatementResponse statement(
      UUID clientId, UUID account, LocalDate from, LocalDate to) {
    var statement = new LedgerStatementResponse[1];
    books.as(
        ownerId,
        () ->
            statement[0] =
                clientLedgerService.getClientLedgerStatement(clientId, account, from, to));
    return statement[0];
  }

  private ReportResult clientStatement(UUID clientId, UUID account, LocalDate from, LocalDate to) {
    var result = new ReportResult[1];
    books.as(
        ownerId,
        () ->
            result[0] =
                books.read(
                    () ->
                        clientLedgerStatementQuery.executeAll(
                            new HashMap<>(
                                Map.of(
                                    "trust_account_id",
                                    account.toString(),
                                    "customer_id",
                                    clientId.toString(),
                                    "dateFrom",
                                    from.toString(),
                                    "dateTo",
                                    to.toString())))));
    return result[0];
  }

  private ReportResult receiptsPayments(UUID account, LocalDate from, LocalDate to) {
    return books.read(
        () ->
            receiptsPaymentsQuery.executeAll(
                new HashMap<>(
                    Map.of(
                        "trust_account_id",
                        account.toString(),
                        "dateFrom",
                        from.toString(),
                        "dateTo",
                        to.toString()))));
  }

  private ReportResult schedule(UUID account, LocalDate from, LocalDate to) {
    return books.read(
        () ->
            scheduleQuery.executeAll(
                new HashMap<>(
                    Map.of(
                        "trust_account_id",
                        account.toString(),
                        "dateFrom",
                        from.toString(),
                        "dateTo",
                        to.toString()))));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> block(ReportResult result, UUID account) {
    return ((List<Map<String, Object>>) result.summary().get("blocks"))
        .stream().filter(b -> account.equals(b.get("trustAccountId"))).findFirst().orElseThrow();
  }

  /** b/f + interest − charges + refunds − paid to the Fund = c/f, on the printed totals. */
  private static void assertTotalsIdentity(Map<String, Object> totals) {
    assertThat(
            money(totals, "fundBroughtForward")
                .add(money(totals, "grossInterestCashbook"))
                .subtract(money(totals, "chargesRecoverable"))
                .subtract(money(totals, "chargesNonRecoverable"))
                .add(money(totals, "chargesRefunded"))
                .subtract(money(totals, "paidToFund")))
        .isEqualByComparingTo(money(totals, "fundCarriedForward"));
  }

  private static BigDecimal money(Map<String, Object> row, String key) {
    var value = (BigDecimal) row.get(key);
    return value != null ? value : BigDecimal.ZERO;
  }

  private ClientLedgerCardResponse card(UUID clientId, UUID account) {
    var card = new ClientLedgerCardResponse[1];
    books.as(ownerId, () -> card[0] = clientLedgerService.getClientLedger(clientId, account));
    return card[0];
  }

  private static CreditorLine clientLine(java.util.List<CreditorLine> lines, UUID clientId) {
    return lines.stream()
        .filter(l -> l.kind() == CreditorLine.Kind.CLIENT && clientId.equals(l.clientId()))
        .findFirst()
        .orElseThrow();
  }
}
