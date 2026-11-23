package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.TrustInterestChargesScheduleQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
