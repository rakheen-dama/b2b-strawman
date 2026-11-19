package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportRenderingService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemoRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.PayToFundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 650C: the two new trust reports on real postings — the Rule 54.28 interest-and-charges
 * schedule (§16.8.3: one block per account headed by its section, one row per month, the identity
 * on every row, memo figures outside it, a reversal excluded with its contra) and the unidentified
 * deposits age analysis (§16.8.4: open as at the date by the items' own rows, five bands, the items
 * resolved in the twelve months to the date, Σ open = {@code unidentified(A, D)}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustScheduleAndAgeAnalysisIntegrationTest {

  private static final String ORG_ID = "org_trust_schedule_age_test";
  private static final String OWNER = "user_schedule_owner";
  private static final String APPROVER = "user_schedule_approver";
  private static final String FUND = "FUND_INTEREST_AND_CHARGES";
  private static final Pattern RAW_CODE = Pattern.compile(".*\\b[A-Z]{2,}_[A-Z_]{2,}\\b.*");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustInterestChargesScheduleQuery scheduleQuery;
  @Autowired private UnidentifiedDepositsAgeQuery ageQuery;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;
  @Autowired private TrustBooksService booksService;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private UnidentifiedDepositService depositService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReportRenderingService renderingService;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Schedule Age Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "schedule_owner@test.com", "Schedule Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER,
                "schedule_approver@test.com",
                "Schedule Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  // --- the Rule 54.28 schedule ------------------------------------------------------------------

  /**
   * A section 86(2) block over three months: interest, a recoverable and a non-recoverable charge
   * (with VAT), a refund, a sweep, a reversal in another month than its original (D-8) and a pair
   * reversed in one month (nets to nil); a memo for May sits in its own columns. The identity holds
   * on every row; c/f is the Fund ledger balance at month end and the next month's b/f.
   */
  @Test
  void schedule_s86_2_identityHoldsPerMonth_memoSeparate_reversalsWithTheirContra() {
    var account = books.freshAccount(false);
    var fund = books.creditorAccount(account, FUND);
    books.postNonClientDirect(account, "FUND_INTEREST", "100.00", LocalDate.of(2026, 4, 10));
    var recoverable =
        books.postNonClientDirect(account, "BANK_CHARGE", "15.00", LocalDate.of(2026, 4, 20));
    var nonRecoverable =
        books.postNonClientDirect(account, "BANK_CHARGE", "23.00", LocalDate.of(2026, 4, 25));
    jdbc.update(
        "UPDATE \"%s\".trust_transactions SET charge_recoverable = false, charge_vat_amount = 3.00"
                .formatted(books.schema())
            + " WHERE id = ?",
        nonRecoverable);
    books.postNonClientDirect(account, "CHARGE_REFUND", "15.00", LocalDate.of(2026, 5, 5));
    books.postNonClientDirect(account, "FUND_SWEEP", "100.00", LocalDate.of(2026, 5, 28));
    books.postNonClientDirect(account, "FUND_INTEREST", "50.00", LocalDate.of(2026, 6, 15));
    var inError =
        books.postNonClientDirect(account, "FUND_INTEREST", "30.00", LocalDate.of(2026, 6, 2));
    books.reverse(account, inError, Expected.cashbookAndFund("-30.00"), LocalDate.of(2026, 6, 3));
    // The April recoverable charge reversed in June: April keeps it, June carries the contra
    books.reverse(
        account, recoverable, Expected.cashbookAndFund("15.00"), LocalDate.of(2026, 6, 20));
    memo(account, LocalDate.of(2026, 5, 1), "40.00", "5.00", "0.65");

    var result =
        schedule(
            Map.of("trust_account_id", account, "dateFrom", "2026-04-15", "dateTo", "2026-06-03"));

    assertThat(result.summary().get("periodFrom")).isEqualTo("2026-04-01");
    assertThat(result.summary().get("periodTo")).isEqualTo("2026-06-30");
    assertThat(result.summary().get("identityHolds")).isEqualTo(true);
    assertThat(result.summary().get("scopeNote")).isEqualTo(TrustReportWords.TRUST_ASSETS_NOTE);
    var rows = result.rows();
    assertThat(rows)
        .extracting(r -> r.get("month"))
        .containsExactly("April 2026", "May 2026", "June 2026");
    assertThat(rows)
        .allSatisfy(
            r ->
                assertThat(r.get("sectionLabel")).isEqualTo("Section 86(2) trust banking account"));

    var april = rows.get(0);
    assertMoney(april, "fundBroughtForward", "0.00");
    assertMoney(april, "grossInterestCashbook", "100.00");
    assertMoney(april, "chargesRecoverable", "15.00");
    assertMoney(april, "chargesNonRecoverable", "23.00");
    assertMoney(april, "chargesVat", "3.00");
    assertMoney(april, "fundCarriedForward", "62.00");
    assertThat(april.get("clientShare")).isNull();
    assertThat(april.get("fundShareCredited")).isNull();

    var may = rows.get(1);
    assertMoney(may, "fundBroughtForward", "62.00");
    assertMoney(may, "chargesRefunded", "15.00");
    assertMoney(may, "paidToFund", "100.00");
    assertMoney(may, "grossInterestMemo", "40.00");
    assertMoney(may, "chargesMemo", "5.00");
    assertMoney(may, "chargesMemoVat", "0.65");
    // The memo never reaches the ledger: a Fund ledger may be in debit (founder decision 9)
    assertMoney(may, "fundCarriedForward", "-23.00");

    var june = rows.get(2);
    assertMoney(june, "fundBroughtForward", "-23.00");
    assertMoney(june, "grossInterestCashbook", "50.00");
    assertMoney(june, "chargesRecoverable", "-15.00");
    assertMoney(june, "fundCarriedForward", "42.00");

    for (int i = 0; i < rows.size(); i++) {
      var r = rows.get(i);
      assertIdentity(r, false);
      var monthEnd = LocalDate.parse((String) r.get("monthStart")).plusMonths(1).minusDays(1);
      assertThat((BigDecimal) r.get("fundCarriedForward"))
          .isEqualByComparingTo(
              books.read(() -> ledgerQueries.creditorAccountBalance(fund, monthEnd)));
      if (i > 0) {
        assertThat((BigDecimal) r.get("fundBroughtForward"))
            .isEqualByComparingTo((BigDecimal) rows.get(i - 1).get("fundCarriedForward"));
      }
      assertThat(r.values())
          .filteredOn(v -> v instanceof String)
          .allSatisfy(v -> assertThat((String) v).doesNotMatch(RAW_CODE));
    }

    @SuppressWarnings("unchecked")
    var block = ((List<Map<String, Object>>) result.summary().get("blocks")).getFirst();
    assertMoney(block, "fundBroughtForward", "0.00");
    assertMoney(block, "grossInterestCashbook", "150.00");
    assertMoney(block, "grossInterestMemo", "40.00");
    assertMoney(block, "paidToFund", "100.00");
    assertMoney(block, "fundCarriedForward", "42.00");
  }

  /**
   * A section 86(4) block: the 5% credited to the account (GROSS, a FUND_INV_SHARE row) is the
   * identity's interest term; the 5% paid at source (NET) and the client's 95% never touch the Fund
   * ledger and stay outside it.
   */
  @Test
  void schedule_s86_4_identityUsesTheFundShareCredited_paidAtSourceOutside() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("Schedule s86(4)");
    books.deposit(funding, client, "30000.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "30000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverId);
    var mayDate = LocalDate.of(2026, 5, 10);
    var line = books.unmatchedLine(investmentAccount, mayDate, "400.00", "INT-MAY");
    books.recordInterest(
        investmentAccount,
        new Expected(
            new BigDecimal("400.00"),
            BigDecimal.ZERO,
            new BigDecimal("380.00"),
            new BigDecimal("20.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        placed.id(),
        new RecordInvestmentInterestRequest(
            mayDate, new BigDecimal("400.00"), null, InterestPresentation.GROSS, line.lineId()));
    books.recordInterest(
        investmentAccount,
        new Expected(
            new BigDecimal("95.00"),
            BigDecimal.ZERO,
            new BigDecimal("95.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        placed.id(),
        new RecordInvestmentInterestRequest(
            LocalDate.of(2026, 6, 10),
            new BigDecimal("100.00"),
            null,
            InterestPresentation.NET,
            null));
    books.postNonClientDirect(investmentAccount, "FUND_SWEEP", "20.00", LocalDate.of(2026, 6, 30));

    var rows =
        schedule(
                Map.of(
                    "trust_account_id",
                    investmentAccount,
                    "dateFrom",
                    "2026-05-01",
                    "dateTo",
                    "2026-06-30"))
            .rows();

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .allSatisfy(
            r -> assertThat(r.get("sectionLabel")).isEqualTo("Section 86(4) investment account"));
    var may = rows.get(0);
    assertMoney(may, "grossInterestCashbook", "400.00");
    assertMoney(may, "clientShare", "380.00");
    assertMoney(may, "fundShareCredited", "20.00");
    assertMoney(may, "fundSharePaidAtSource", "0.00");
    assertMoney(may, "fundCarriedForward", "20.00");
    var june = rows.get(1);
    assertMoney(june, "grossInterestCashbook", "100.00");
    assertMoney(june, "clientShare", "95.00");
    assertMoney(june, "fundShareCredited", "0.00");
    assertMoney(june, "fundSharePaidAtSource", "5.00");
    assertMoney(june, "paidToFund", "20.00");
    assertMoney(june, "fundCarriedForward", "0.00");
    rows.forEach(r -> assertIdentity(r, true));

    // Firm-wide: one block per account, ordered by section (86(2) before 86(4)), no set-off
    var firm = schedule(Map.of("dateFrom", "2026-05-01", "dateTo", "2026-06-30"));
    @SuppressWarnings("unchecked")
    var blocks = (List<Map<String, Object>>) firm.summary().get("blocks");
    var sections = blocks.stream().map(b -> (String) b.get("sectionLabel")).toList();
    assertThat(sections).isSortedAccordingTo(java.util.Comparator.comparing(this::sectionOrder));
    assertThat(blocks)
        .extracting(b -> b.get("trustAccountId"))
        .contains(funding, investmentAccount);
    assertThat(firm.rows()).hasSize(blocks.size() * 2);
  }

  /** D-10: month boundaries are snapped; an inverted range or one past 36 months is refused. */
  @Test
  void schedule_parameters_invertedOrTooLongRangeIsRefused() {
    var account = books.freshAccount(false);
    assertThatThrownBy(
            () ->
                schedule(
                    Map.of(
                        "trust_account_id",
                        account,
                        "dateFrom",
                        "2026-05-01",
                        "dateTo",
                        "2026-04-30")))
        .isInstanceOf(InvalidStateException.class);
    assertThatThrownBy(
            () ->
                schedule(
                    Map.of(
                        "trust_account_id",
                        account,
                        "dateFrom",
                        "2023-01-01",
                        "dateTo",
                        "2026-01-31")))
        .isInstanceOf(InvalidStateException.class);
    assertThat(
            schedule(
                    Map.of(
                        "trust_account_id",
                        account,
                        "dateFrom",
                        "2023-02-01",
                        "dateTo",
                        "2026-01-31"))
                .rows())
        .hasSize(36);
  }

  /** CR-2: the dates are compared before they are snapped — an inverted range in one month. */
  @Test
  void schedule_invertedRangeWithinOneMonth_isRefused() {
    var account = books.freshAccount(false);
    assertThatThrownBy(
            () ->
                schedule(
                    Map.of(
                        "trust_account_id",
                        account,
                        "dateFrom",
                        "2026-05-20",
                        "dateTo",
                        "2026-05-10")))
        .isInstanceOf(InvalidStateException.class);
  }

  /**
   * CR-3: firm-wide, an account opened after the period is not in it; one opened inside the period
   * (here on the period's last month's first day) is.
   */
  @Test
  void schedule_firmWide_leavesOutAnAccountOpenedAfterThePeriod() {
    var account = books.freshAccount(false); // opened 1 January 2026
    var before = schedule(Map.of("dateFrom", "2025-11-01", "dateTo", "2025-12-31"));
    assertThat(blockAccountIds(before)).doesNotContain(account);
    var spanning = schedule(Map.of("dateFrom", "2025-12-01", "dateTo", "2026-01-31"));
    assertThat(blockAccountIds(spanning)).contains(account);
  }

  /**
   * B-H1: a Fund ledger group of a type the schedule does not recognise (a registry type that never
   * moves a Fund ledger, or an unknown code) is never dropped silently: its amount is reported as
   * unexplained and the month does not balance — even when the stray rows net to nil.
   */
  @Test
  void schedule_unrecognisedFundLedgerType_isReportedAndBreaksTheIdentity() {
    var accountId = UUID.randomUUID();
    var fundId = UUID.randomUUID();
    var account = Mockito.mock(TrustAccount.class);
    Mockito.when(account.getId()).thenReturn(accountId);
    Mockito.when(account.getAccountName()).thenReturn("Stray Type Account");
    Mockito.when(account.getSection()).thenReturn(TrustAccountSection.S86_2);
    var fund = Mockito.mock(TrustCreditorAccount.class);
    Mockito.when(fund.getId()).thenReturn(fundId);
    var accounts = Mockito.mock(TrustAccountRepository.class);
    Mockito.when(accounts.findById(accountId)).thenReturn(Optional.of(account));
    var creditors = Mockito.mock(TrustCreditorAccountRepository.class);
    Mockito.when(
            creditors.findByTrustAccountIdAndAccountType(
                accountId, TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES))
        .thenReturn(Optional.of(fund));
    var ledger = Mockito.mock(TrustLedgerQueryRepository.class);
    Mockito.when(ledger.creditorAccountBalance(fundId, LocalDate.of(2026, 3, 31)))
        .thenReturn(BigDecimal.ZERO);
    Mockito.when(ledger.creditorAccountBalance(fundId, LocalDate.of(2026, 4, 30)))
        .thenReturn(new BigDecimal("10.00"));
    Mockito.when(ledger.creditorAccountBalance(fundId, LocalDate.of(2026, 5, 31)))
        .thenReturn(new BigDecimal("10.00"));
    var reports = Mockito.mock(TrustReportQueryRepository.class);
    Mockito.when(
            reports.fundLedgerByMonth(fundId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31)))
        .thenReturn(
            List.of(
                fundGroup(LocalDate.of(2026, 4, 1), "NO_SUCH_TYPE", "10.00"),
                fundGroup(LocalDate.of(2026, 5, 1), "FIRM_INV_RECEIVE", "0.00")));
    var memos = Mockito.mock(TrustInterestMemoRepository.class);
    var query = new TrustInterestChargesScheduleQuery(accounts, creditors, ledger, reports, memos);

    var result =
        query.executeAll(
            new HashMap<>(
                Map.of(
                    "trust_account_id",
                    accountId,
                    "dateFrom",
                    "2026-04-01",
                    "dateTo",
                    "2026-05-31")));

    var april = result.rows().get(0);
    var may = result.rows().get(1);
    assertMoney(april, "unexplainedMovement", "10.00");
    assertThat(april.get("identityHolds")).isEqualTo(false);
    assertThat(april.get("check")).isEqualTo(TrustInterestChargesScheduleQuery.CHECK_UNEXPLAINED);
    // Nets to nil, still not silently accepted
    assertMoney(may, "unexplainedMovement", "0.00");
    assertThat(may.get("identityHolds")).isEqualTo(false);
    assertThat(result.summary().get("identityHolds")).isEqualTo(false);
  }

  /** CR-6: each row and each block's period total carry the identity check in words. */
  @Test
  void schedule_checkInWords_perRowAndPerBlock() {
    var account = books.freshAccount(false);
    books.postNonClientDirect(account, "FUND_INTEREST", "12.00", LocalDate.of(2026, 7, 10));
    var result =
        schedule(
            Map.of("trust_account_id", account, "dateFrom", "2026-07-01", "dateTo", "2026-08-31"));
    assertThat(result.rows())
        .extracting(r -> r.get("check"))
        .containsOnly(TrustInterestChargesScheduleQuery.CHECK_BALANCED);
    @SuppressWarnings("unchecked")
    var blocks = (List<Map<String, Object>>) result.summary().get("blocks");
    assertThat(blocks)
        .singleElement()
        .satisfies(
            b ->
                assertThat(b.get("check"))
                    .isEqualTo(TrustInterestChargesScheduleQuery.CHECK_BALANCED));
  }

  // --- the unidentified deposits age analysis --------------------------------------------------

  /**
   * Open items in their bands (0–30, 31–90, over one year), an item paid to the Fund in the year
   * listed as resolved, Σ open = the position's unidentified deposits as at the date.
   */
  @Test
  void ageAnalysis_bandsOpenItems_listsResolved_andTiesToTheBooks() {
    var account = books.freshAccount(false);
    var today = books.today();
    books.unidentifiedDepositDirect(account, "500.00", today.minusDays(10));
    books.unidentifiedDepositDirect(account, "600.00", today.minusDays(60));
    books.unidentifiedDepositDirect(account, "700.00", today.minusDays(400));
    var paidRow = books.unidentifiedDepositDirect(account, "800.00", today.minusDays(100));
    var itemId =
        books.read(
            () -> transactionRepository.findById(paidRow).orElseThrow().getUnidentifiedDepositId());
    var pending = new UUID[1];
    books.as(
        ownerId,
        () ->
            pending[0] =
                depositService
                    .payToFund(itemId, new PayToFundRequest("LPFF-AGE", today.minusDays(50), null))
                    .resolutionTransactionId());
    books.approve(account, pending[0], approverId, Expected.cashbookAndUnidentified("-800.00"));

    var result = age(Map.of("trust_account_id", account, "asOfDate", today.toString()));

    var open = result.rows().stream().filter(r -> "Open".equals(r.get("position"))).toList();
    assertThat(open)
        .extracting(r -> r.get("band") + " " + r.get("amount"))
        .containsExactly("0–30 days 500.00", "31–90 days 600.00", "Over one year 700.00");
    assertThat(open).extracting(r -> r.get("ageDays")).containsExactly(10L, 60L, 400L);
    var resolved = result.rows().stream().filter(r -> "Resolved".equals(r.get("band"))).toList();
    assertThat(resolved)
        .singleElement()
        .satisfies(
            r -> {
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("800.00");
              assertThat((String) r.get("position")).startsWith("Paid to the Fund on ");
              assertThat(r.get("ageDays")).isNull();
            });
    assertMoney(result.summary(), "openTotal", "1800.00");
    assertMoney(result.summary(), "upToOneMonthTotal", "500.00");
    assertMoney(result.summary(), "oneToThreeMonthsTotal", "600.00");
    assertMoney(result.summary(), "threeToSixMonthsTotal", "0.00");
    assertMoney(result.summary(), "overOneYearTotal", "700.00");
    assertThat(result.summary().get("openItems")).isEqualTo(3);
    assertThat(result.summary().get("resolvedItems")).isEqualTo(1);
    var position = books.read(() -> booksService.position(account, today));
    assertThat((BigDecimal) result.summary().get("openTotal"))
        .isEqualByComparingTo(position.unidentifiedDeposits());
    assertThat(result.rows())
        .allSatisfy(
            r ->
                r.values().stream()
                    .filter(v -> v instanceof String)
                    .forEach(v -> assertThat((String) v).doesNotMatch(RAW_CODE)));

    // As at a date before the paid item was paid: it was open then (by its own rows)
    var earlier = today.minusDays(70);
    var then = age(Map.of("trust_account_id", account, "asOfDate", earlier.toString()));
    assertThat(then.rows())
        .filteredOn(r -> "Open".equals(r.get("position")))
        .extracting(r -> r.get("amount"))
        .containsExactlyInAnyOrder(new BigDecimal("700.00"), new BigDecimal("800.00"));
    assertThat((BigDecimal) then.summary().get("openTotal"))
        .isEqualByComparingTo(
            books.read(() -> booksService.position(account, earlier)).unidentifiedDeposits());
  }

  /** EQ-4: an item is open by its own rows, never by its status. */
  @Test
  void ageAnalysis_openByRows_notByStatus() {
    var account = books.freshAccount(false);
    var today = books.today();
    var row = books.unidentifiedDepositDirect(account, "250.00", today.minusDays(5));
    var itemId =
        books.read(
            () -> transactionRepository.findById(row).orElseThrow().getUnidentifiedDepositId());
    // The status says resolved; the rows say held — the report follows the rows
    books.setItemStatusDirect(itemId, "PAID_TO_FUND");

    var result = age(Map.of("trust_account_id", account, "asOfDate", today.toString()));
    assertThat(result.rows())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("position")).isEqualTo("Open");
              assertThat(r.get("band")).isEqualTo("0–30 days");
            });
    // Before it was received it was not open
    var before =
        age(Map.of("trust_account_id", account, "asOfDate", today.minusDays(6).toString()));
    assertThat(before.rows()).isEmpty();
    assertMoney(before.summary(), "openTotal", "0.00");
  }

  /**
   * A-M1: the over-one-year band is L9's predicate (received before the date less one year), so the
   * report and the integrity flag agree across 29 February: received 1 June 2023, as at 1 June 2024
   * (366 days) is not over one year; received 31 May 2023 is.
   */
  @Test
  void ageAnalysis_overOneYear_isL9sPredicate_acrossALeapDay() {
    var account = books.freshAccount(false);
    books.unidentifiedDepositDirect(account, "300.00", LocalDate.of(2023, 6, 1));
    books.unidentifiedDepositDirect(account, "400.00", LocalDate.of(2023, 5, 31));
    var asAt = LocalDate.of(2024, 6, 1);

    var result = age(Map.of("trust_account_id", account, "asOfDate", asAt.toString()));

    assertThat(result.rows())
        .extracting(r -> r.get("band") + " " + r.get("amount") + " " + r.get("ageDays"))
        .containsExactly("181–365 days 300.00 366", "Over one year 400.00 367");
    assertMoney(result.summary(), "overOneYearTotal", "400.00");
    var flags = new IntegrityFlag[1];
    books.as(
        ownerId,
        () ->
            flags[0] =
                integrityService.check(account, asAt, null).flags().stream()
                    .filter(f -> TrustIntegrityService.UNIDENTIFIED_OVER_ONE_YEAR.equals(f.code()))
                    .findFirst()
                    .orElseThrow());
    assertThat(flags[0].amount())
        .isEqualByComparingTo((BigDecimal) result.summary().get("overOneYearTotal"));
  }

  /**
   * CR-8: an allocation reversed after the date still reads as resolved as at the date — from the
   * posted rows, not the item's current state (the reversal reset it to HELD and cleared its
   * resolution) — and as at today the item is open again.
   */
  @Test
  void ageAnalysis_allocationReversedAfterTheDate_isResolvedAsAtTheDate() {
    var account = books.freshAccount(false);
    var today = books.today();
    var row = books.unidentifiedDepositDirect(account, "300.00", today.minusDays(60));
    var itemId =
        books.read(
            () -> transactionRepository.findById(row).orElseThrow().getUnidentifiedDepositId());
    var client = books.newClient("age analysis owner found");
    var allocatedOn = today.minusDays(40);
    var requested = new UUID[1];
    books.as(
        ownerId,
        () ->
            requested[0] =
                depositService
                    .allocate(
                        itemId,
                        new AllocateRequest(client.id(), client.matter(), allocatedOn, null))
                    .resolutionTransactionId());
    books.approve(account, requested[0], approverId, Expected.clientFromUnidentified("+300.00"));
    books.reverse(
        account, requested[0], Expected.clientFromUnidentified("-300.00"), today.minusDays(20));

    var then = age(Map.of("trust_account_id", account, "asOfDate", today.minusDays(30).toString()));
    assertThat(then.rows())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("band")).isEqualTo("Resolved");
              assertThat(r.get("position"))
                  .isEqualTo(
                      "Allocated to "
                          + client.name()
                          + " on "
                          + IntegrityMessages.date(allocatedOn));
            });
    assertThat(then.summary().get("resolvedItems")).isEqualTo(1);

    var now = age(Map.of("trust_account_id", account, "asOfDate", today.toString()));
    assertThat(now.rows())
        .singleElement()
        .satisfies(r -> assertThat(r.get("position")).isEqualTo("Open"));
  }

  // --- firm-wide eligibility (F-650C-OBS-3) ----------------------------------------------------

  /**
   * F-650C-OBS-3: an account whose postings are dated before its opened date is still in the
   * firm-wide schedule of the months it has rows in — the opened-date filter (CR-3) leaves out only
   * an account with nothing on or before the period end.
   */
  @Test
  void schedule_firmWide_includesAnAccountOpenedAfterThePeriodWithPostingsInIt() {
    var account = books.freshAccount(false); // opened 1 January 2026
    books.postNonClientDirect(account, "FUND_INTEREST", "25.00", LocalDate.of(2025, 9, 10));
    var firmWide = schedule(Map.of("dateFrom", "2025-09-01", "dateTo", "2025-10-31"));
    assertThat(blockAccountIds(firmWide)).contains(account);
    var named =
        schedule(
            Map.of("trust_account_id", account, "dateFrom", "2025-09-01", "dateTo", "2025-10-31"));
    var firmWideRows =
        firmWide.rows().stream().filter(r -> account.equals(r.get("trustAccountId"))).toList();
    assertThat(firmWideRows).isEqualTo(named.rows());
  }

  /** F-650C-OBS-3: a firm-wide schedule with no block claims no identity ("—", not "Yes"). */
  @Test
  void schedule_firmWide_noBlocks_claimsNoIdentity() {
    var empty = schedule(Map.of("dateFrom", "2019-01-01", "dateTo", "2019-02-28"));
    assertThat(empty.summary().get("accountCount")).isEqualTo(0);
    assertThat(empty.summary()).containsKey("identityHolds");
    assertThat(empty.summary().get("identityHolds")).isNull();
  }

  // --- the PDF prints every column (F-650C-OBS-1) ------------------------------------------------

  /**
   * F-650C-OBS-1: the schedule's 16 columns fit the page — every glyph inside the page, the last
   * headers (Charges refunded, Paid to the Fund, Fund ledger c/f, Check) and the check in words on
   * every month row and on the Period row.
   */
  @Test
  void schedulePdf_everyColumnAndThePeriodRowAreOnThePage() {
    var account = books.freshAccount(false);
    books.postNonClientDirect(account, "FUND_INTEREST", "1234567.89", LocalDate.of(2026, 3, 10));
    books.postNonClientDirect(account, "BANK_CHARGE", "12345.67", LocalDate.of(2026, 3, 20));
    books.postNonClientDirect(account, "CHARGE_REFUND", "12345.67", LocalDate.of(2026, 3, 25));
    books.postNonClientDirect(account, "FUND_SWEEP", "1000000.00", LocalDate.of(2026, 4, 28));
    memo(account, LocalDate.of(2026, 3, 1), "123456.78", "12345.67", "1610.30");
    var params =
        Map.<String, Object>of(
            "trust_account_id", account, "dateFrom", "2026-03-01", "dateTo", "2026-04-30");
    var result = schedule(params);
    assertThat(result.rows()).hasSize(2);

    var text = pdfTextOnThePage("trust-interest-charges-schedule", result, params);

    assertThat(text).contains("REFUNDED", "C/F", "CHECK", "Period");
    assertThat(text.split("Balanced", -1).length - 1)
        .as("the check on both month rows and on the Period row")
        .isEqualTo(3);
  }

  /**
   * F-650C-OBS-1: the trust position's twelve columns fit with the values the OBS run printed (a
   * long account and section name, every ledger in the rand thousands, the bank note) — the
   * Integrity column was cut to "IN" / "No fla…".
   */
  @Test
  void trustPositionPdf_theIntegrityColumnIsOnThePage() {
    var row = new HashMap<String, Object>();
    row.put("accountName", "Mathebula & Partners Trust Account");
    row.put("sectionLabel", "Section 86(2) trust banking account");
    var summary = new HashMap<String, Object>();
    for (var key :
        List.of(
            "cashbook",
            "clientLedgers",
            "investmentCreditors",
            "fundLedger",
            "unidentifiedDeposits",
            "interAccount",
            "creditorTotal",
            "difference",
            "adjustedBankBalance")) {
      row.put(key, new BigDecimal("1234567.89"));
      summary.put(key, new BigDecimal("1234567.89"));
    }
    row.put("integrity", TrustPositionQuery.NO_FLAGS);
    row.put("bankNote", TrustPositionQuery.NO_BANK_NOTE);
    summary.put("firmFlags", List.of());

    var text =
        pdfTextOnThePage(
            "trust-position",
            new ReportResult(List.of(row), summary),
            Map.of("asOfDate", "2026-09-30"));

    assertThat(text).contains("INTEGRITY");
    for (var word :
        (TrustPositionQuery.NO_FLAGS + " " + TrustPositionQuery.NO_BANK_NOTE).split(" ")) {
      assertThat(text).contains(word);
    }
  }

  /**
   * F-650C-OBS-1 (the other wide template): the investment register's eleven columns fit with long
   * values in every cell.
   */
  @Test
  void investmentRegisterPdf_everyColumnIsOnThePage() {
    var row = new HashMap<String, Object>();
    row.put("clientName", "Mathebula Family Investment Trust (Pty) Ltd");
    row.put("institution", "Standard Bank of South Africa Limited");
    row.put("accountNumber", "0012345678901234");
    row.put("investmentAccountName", "Mathebula & Partners Section 86(4) Investment Account");
    row.put("basisLabel", "Section 86(4)");
    row.put("principal", new BigDecimal("12345678.90"));
    row.put("balance", new BigDecimal("12456789.01"));
    row.put("grossInterest", new BigDecimal("1234567.89"));
    row.put("fundShare", new BigDecimal("61728.39"));
    row.put("interestEarned", new BigDecimal("1172839.50"));
    row.put("statusLabel", "Withdrawn");
    var summary = new HashMap<String, Object>();
    summary.put("totalPrincipal", new BigDecimal("12345678.90"));
    summary.put("totalBalance", new BigDecimal("12456789.01"));
    summary.put("totalGrossInterest", new BigDecimal("1234567.89"));
    summary.put("totalFundShare", new BigDecimal("61728.39"));
    summary.put("totalInterestEarned", new BigDecimal("1172839.50"));

    var text =
        pdfTextOnThePage("investment-register", new ReportResult(List.of(row), summary), Map.of());

    assertThat(text).contains("STATUS", "Withdrawn");
  }

  // --- helpers ----------------------------------------------------------------------------------

  /**
   * Renders the shipped template of {@code slug} to PDF and returns its text, whitespace folded to
   * single spaces, after asserting that nothing is cut at the right: every glyph ends inside the
   * content box, whose right edge is where the right-aligned "Generated: …" header line ends (a
   * table wider than the page is clipped there, or past the page edge where its text is dropped).
   */
  private String pdfTextOnThePage(String slug, ReportResult result, Map<String, Object> params) {
    var definition = TrustReportPackSeeder.shipped(slug);
    byte[] pdf =
        books.read(() -> renderingService.renderPdf(definition, result, new HashMap<>(params)));
    var glyphs = new ArrayList<TextPosition>();
    var contentRight = new float[] {0f};
    try (var doc = Loader.loadPDF(pdf)) {
      var stripper =
          new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition glyph) {
              glyphs.add(glyph);
              super.processTextPosition(glyph);
            }

            @Override
            protected void writeString(String chunk, List<TextPosition> positions)
                throws IOException {
              if (chunk.startsWith("Generated: ") && !positions.isEmpty()) {
                var last = positions.getLast();
                contentRight[0] =
                    Math.max(contentRight[0], last.getXDirAdj() + last.getWidthDirAdj());
              }
              super.writeString(chunk, positions);
            }
          };
      var text = stripper.getText(doc).replaceAll("\\s+", " ");
      assertThat(contentRight[0]).as("the header's right edge in %s", slug).isPositive();
      assertThat(glyphs)
          .as("glyphs past the content box of %s", slug)
          .filteredOn(g -> g.getXDirAdj() + g.getWidthDirAdj() > contentRight[0] + 1f)
          .extracting(TextPosition::getUnicode)
          .isEmpty();
      return text;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> blockAccountIds(ReportResult result) {
    return ((List<Map<String, Object>>) result.summary().get("blocks"))
        .stream().map(b -> b.get("trustAccountId")).toList();
  }

  private static TrustReportQueryRepository.FundLedgerMonthRow fundGroup(
      LocalDate month, String type, String effect) {
    return new TrustReportQueryRepository.FundLedgerMonthRow() {
      @Override
      public LocalDate getPeriodMonth() {
        return month;
      }

      @Override
      public String getTxType() {
        return type;
      }

      @Override
      public Boolean getRecoverable() {
        return null;
      }

      @Override
      public BigDecimal getLedgerEffect() {
        return new BigDecimal(effect);
      }

      @Override
      public BigDecimal getVat() {
        return BigDecimal.ZERO;
      }
    };
  }

  private ReportResult schedule(Map<String, Object> params) {
    return books.read(() -> scheduleQuery.executeAll(new HashMap<>(params)));
  }

  private ReportResult age(Map<String, Object> params) {
    return books.read(() -> ageQuery.executeAll(new HashMap<>(params)));
  }

  private void memo(UUID account, LocalDate month, String interest, String charges, String vat) {
    jdbc.update(
        """
        INSERT INTO "%s".trust_interest_memos (id, trust_account_id, period_month, gross_interest,
            gross_charges, charges_vat, source_note, recorded_by)
        VALUES (?, ?, ?, ?, ?, ?, 'IA/MAY26 bank advice', ?)
        """
            .formatted(books.schema()),
        UUID.randomUUID(),
        account,
        month,
        new BigDecimal(interest),
        new BigDecimal(charges),
        new BigDecimal(vat),
        ownerId);
  }

  /**
   * §16.8.3: b/f + interest term − charges in the cashbook + refunds − paid to the Fund = c/f; the
   * interest term is the Fund's 5% credited for a section 86(4) block, gross interest otherwise.
   */
  private static void assertIdentity(Map<String, Object> r, boolean s864) {
    var interest = s864 ? m(r, "fundShareCredited") : m(r, "grossInterestCashbook");
    var computed =
        m(r, "fundBroughtForward")
            .add(interest)
            .subtract(m(r, "chargesRecoverable"))
            .subtract(m(r, "chargesNonRecoverable"))
            .add(m(r, "chargesRefunded"))
            .subtract(m(r, "paidToFund"));
    assertThat(computed)
        .as("identity of %s", r.get("month"))
        .isEqualByComparingTo(m(r, "fundCarriedForward"));
    assertThat(r.get("identityHolds")).isEqualTo(true);
  }

  private int sectionOrder(String label) {
    return label.contains("86(2)") ? 0 : label.contains("86(3)") ? 1 : 2;
  }

  private static BigDecimal m(Map<String, Object> r, String key) {
    return (BigDecimal) r.get(key);
  }

  private static void assertMoney(Map<String, Object> r, String key, String expected) {
    assertThat((BigDecimal) r.get(key)).as(key).isEqualByComparingTo(expected);
  }
}
