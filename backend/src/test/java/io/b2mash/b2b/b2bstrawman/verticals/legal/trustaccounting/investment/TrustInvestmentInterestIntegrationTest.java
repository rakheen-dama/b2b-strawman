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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.InvestmentInterestResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 648B (task 648.10; ADR-379; architecture §16.3.8, §16.9.2 paths 20–21): section
 * 86(4) interest entries. Each entry holds the gross, the Fund's 5% and the client's 95%, the date
 * and the client's tax reference where held (A2, A3):
 *
 * <ul>
 *   <li><b>Path 20</b> — {@code GROSS} from a statement line: {@code INV_INTEREST} (net, the
 *       client's investment creditor) and {@code FUND_INV_SHARE} (the 5%, the investment account's
 *       Fund ledger); the later 5% debit recorded as {@code FUND_SWEEP}. {@code NET} from a bank
 *       advice: {@code INV_INTEREST} only — the 5% never reached the account. The reversal of a
 *       bank-advice entry (a contra dated the entry date); a line-recorded entry reverses by
 *       unmatch.
 *   <li><b>Path 21</b> — section 86(3) interest on an investment account is the Fund's: recorded
 *       from the statement as {@code FUND_INTEREST} and swept (characterisation — 647A's path).
 * </ul>
 *
 * <p>Every step is asserted through {@link TrustBooksHarness#assertMoves} (EQ-5 included: the
 * investment's rows tie to principal + net interest); the class ends with {@code
 * assertFirmBalanced(today)}. Every refusal moves nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentInterestIntegrationTest {

  private static final String ORG_ID = "org_trust_invest_interest_test";
  private static final String OWNER = "user_interest_owner";
  private static final String APPROVER = "user_interest_approver";

  private static final String INTEREST_TITLE = "Cannot record investment interest";
  private static final String REVERSE_TITLE = "Cannot reverse investment interest";
  private static final String RECORD_TITLE = "Cannot record statement line";
  private static final String FUND = "FUND_INTEREST_AND_CHARGES";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private StatementLinePostingService linePostingService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Investment Interest Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "interest_owner@test.com", "Interest Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER,
                "interest_approver@test.com",
                "Interest Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /** Ten days back: after the placement date, and a line on it can be unmatched. */
  private LocalDate pastDate() {
    return books.today().minusDays(10);
  }

  // --- path 20: GROSS from a line, then the 5% sweep ----------------------------------------

  @Test
  void path20_gross_fromALine_postsTheNetAndTheFundShare_thenTheSweepEmptiesTheFundLedger() {
    var held = s86_4Investment("30000.00");
    books.setTaxNumberDirect(held.client(), "9012345678");
    var date = pastDate();
    var line = books.unmatchedLine(held.investmentAccount(), date, "400.00", "INT-OCT");

    var entry =
        books.recordInterest(
            held.investmentAccount(),
            new Expected(
                new BigDecimal("400.00"),
                BigDecimal.ZERO,
                new BigDecimal("380.00"),
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            held.investmentId(),
            new RecordInvestmentInterestRequest(
                date, new BigDecimal("400.00"), null, InterestPresentation.GROSS, line.lineId()));

    assertThat(entry.grossAmount()).isEqualByComparingTo("400.00");
    assertThat(entry.fundShareAmount()).isEqualByComparingTo("20.00");
    assertThat(entry.netAmount()).isEqualByComparingTo("380.00");
    assertThat(entry.presentation()).isEqualTo("GROSS");
    assertThat(entry.status()).isEqualTo("POSTED");
    assertThat(entry.entryDate()).isEqualTo(date);
    assertThat(entry.bankStatementLineId()).isEqualTo(line.lineId());
    assertThat(entry.clientTaxReference()).isEqualTo("9012345678");
    var net = row(entry.netTransactionId());
    var share = row(entry.fundShareTransactionId());
    assertThat(net.getTransactionType()).isEqualTo("INV_INTEREST");
    assertThat(net.getLedger()).isEqualTo(TrustLedger.CLIENT_INVESTMENT);
    assertThat(net.getCustomerId()).isEqualTo(held.client().id());
    assertThat(net.getProjectId()).isEqualTo(held.client().matter());
    assertThat(share.getTransactionType()).isEqualTo("FUND_INV_SHARE");
    assertThat(share.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(share.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(held.investmentAccount(), FUND));
    for (var row : List.of(net, share)) {
      assertThat(row.getInvestmentId()).isEqualTo(held.investmentId());
      assertThat(row.getBankStatementLineId()).isEqualTo(line.lineId());
      assertThat(row.getTransactionDate()).isEqualTo(date);
      assertThat(row.getReference()).isEqualTo("INT-OCT");
      assertThat(row.getStatus()).isEqualTo("RECORDED");
    }
    assertThat(lineStatus(line.lineId())).isEqualTo("RECORDED|INVESTMENT_INTEREST");
    assertThat(get(held.investmentId()).balance()).isEqualByComparingTo("30380.00");
    assertThat(get(held.investmentId()).grossInterest()).isEqualByComparingTo("400.00");
    assertThat(get(held.investmentId()).fundShare()).isEqualByComparingTo("20.00");
    assertThat(get(held.investmentId()).netInterest()).isEqualByComparingTo("380.00");
    assertThat(audit("trust_investment.interest_recorded", held.investmentId()).getDetails())
        .containsEntry("gross", "400.00")
        .containsEntry("fund_share", "20.00")
        .containsEntry("net", "380.00")
        .containsEntry("presentation", "GROSS")
        .containsEntry("interest_id", entry.id().toString());

    // The bank debits the 5% separately: recorded from its line as a transfer to the Fund
    var sweepLine = books.addLine(line.statementId(), 2, date, "-20.00", "LPFF-OCT");
    recordFromLine(
        held.investmentAccount(),
        Expected.cashbookAndFund("-20.00"),
        sweepLine,
        StatementLineClassification.FUND_SWEEP);
    assertThat(books.snapshotAsAt(held.investmentAccount(), books.today()).fundLedger())
        .isEqualByComparingTo("0");

    // A line-recorded entry is reversed by unmatching its line (OQ-3), not here
    assertReverseRefused(
        held.investmentAccount(),
        entry.id(),
        "This interest was recorded from a bank statement line. Unmatch the line to reverse it.");
  }

  // --- path 20: NET from a bank advice, and its reversal ------------------------------------

  @Test
  void path20_net_fromABankAdvice_postsTheNetOnly_andTheEntryHoldsGrossAndFund() {
    var held = s86_4Investment("30000.00");
    var date = pastDate();

    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            net(date, "100.00"));

    assertThat(entry.grossAmount()).isEqualByComparingTo("100.00");
    assertThat(entry.fundShareAmount()).isEqualByComparingTo("5.00");
    assertThat(entry.netAmount()).isEqualByComparingTo("95.00");
    assertThat(entry.presentation()).isEqualTo("NET");
    assertThat(entry.fundShareTransactionId()).isNull();
    assertThat(entry.bankStatementLineId()).isNull();
    assertThat(entry.clientTaxReference()).isNull();
    var row = row(entry.netTransactionId());
    assertThat(row.getAmount()).isEqualByComparingTo("95.00");
    assertThat(row.getBankStatementLineId()).isNull();
    assertThat(row.getReference()).isEqualTo(adviceReference(held.investmentId(), 1));
    assertThat(row.getDescription())
        .isEqualTo("Section 86(4) investment interest (Trust Books Bank)");
    // The posted row's audit key is the registry's, as on the statement-line path
    assertThat(audit("trust_transaction", row.getId(), "trust_inv_interest.recorded").getDetails())
        .containsEntry("amount", "95.00")
        .containsEntry("investment_id", held.investmentId().toString());
    assertThat(get(held.investmentId()).balance()).isEqualByComparingTo("30095.00");
    assertThat(list(held.investmentId()))
        .extracting(InvestmentInterestResponse::id)
        .containsExactly(entry.id());

    // The withdrawal returns capital plus the net interest
    var withdrawn =
        books.withdraw(
            held.funding(),
            Expected.cashbookAndClient("30095.00"),
            held.investmentAccount(),
            Expected.cashbookAndInvestment("-30095.00"),
            held.investmentId(),
            books.today());
    assertThat(withdrawn.withdrawalAmount()).isEqualByComparingTo("30095.00");
    assertThat(withdrawn.netInterest()).isEqualByComparingTo("95.00");
    // Once withdrawn, interest is neither recorded nor reversed
    assertRecordRefused(
        held,
        net(books.today(), "10.00"),
        INTEREST_TITLE,
        "Interest is recorded on an active section 86(4) investment only.");
    assertReverseRefused(
        held.investmentAccount(),
        entry.id(),
        "The investment has been withdrawn with this interest; its interest entries can no longer"
            + " be reversed.");
  }

  @Test
  void path20_reversal_ofABankAdviceEntry_isAContraDatedTheEntryDate() {
    var held = s86_4Investment("2000.00");
    var date = pastDate();
    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            net(date, "100.00"));

    var reversed =
        books.reverseInterest(
            held.investmentAccount(), Expected.cashbookAndInvestment("-95.00"), entry.id());

    assertThat(reversed.status()).isEqualTo("REVERSED");
    var original = row(entry.netTransactionId());
    assertThat(original.getStatus()).isEqualTo("REVERSED");
    var contra = row(original.getReversedById());
    assertThat(contra.getTransactionType()).isEqualTo("REVERSAL");
    assertThat(contra.getReversalOf()).isEqualTo(original.getId());
    assertThat(contra.getTransactionDate()).isEqualTo(date);
    assertThat(contra.getReference()).isEqualTo("REV-" + original.getReference());
    assertThat(contra.getDescription()).isEqualTo("Section 86(4) interest entry reversed");
    assertThat(contra.getInvestmentId()).isEqualTo(held.investmentId());
    assertThat(contra.getLedger()).isEqualTo(TrustLedger.CLIENT_INVESTMENT);
    assertThat(get(held.investmentId()).balance()).isEqualByComparingTo("2000.00");
    assertThat(get(held.investmentId()).netInterest()).isEqualByComparingTo("0");
    // As at the day before the entry the books never saw it; as at the entry date it nets to 0
    books.assertBalancedAsAt(held.investmentAccount(), date.minusDays(1));
    books.assertBalancedAsAt(held.investmentAccount(), date);
    assertThat(audit("trust_investment.interest_reversed", held.investmentId()).getDetails())
        .containsEntry("interest_id", entry.id().toString())
        .containsEntry("reversed_transaction_ids", List.of(original.getId().toString()));

    assertReverseRefused(
        held.investmentAccount(), entry.id(), "This interest entry has already been reversed.");
    // A later bank advice on the same investment has its own reference (the ordinal counts the
    // reversed entry too), so the two entries' rows stay distinguishable
    var second =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("19.00"),
            held.investmentId(),
            net(date, "20.00"));
    assertThat(original.getReference()).isEqualTo(adviceReference(held.investmentId(), 1));
    assertThat(row(second.netTransactionId()).getReference())
        .isEqualTo(adviceReference(held.investmentId(), 2));
    // The withdrawal after a reversal returns the principal and the unreversed entry's net only
    var withdrawn =
        books.withdraw(
            held.funding(),
            Expected.cashbookAndClient("2019.00"),
            held.investmentAccount(),
            Expected.cashbookAndInvestment("-2019.00"),
            held.investmentId(),
            books.today());
    assertThat(withdrawn.withdrawalAmount()).isEqualByComparingTo("2019.00");
  }

  @Test
  void path20_net_fromALine_reversesByUnmatch() {
    var held = s86_4Investment("2000.00");
    var date = pastDate();
    var line = books.unmatchedLine(held.investmentAccount(), date, "95.00", "INT-NET");

    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            new RecordInvestmentInterestRequest(
                date, new BigDecimal("100.00"), null, InterestPresentation.NET, line.lineId()));
    assertThat(entry.bankStatementLineId()).isEqualTo(line.lineId());
    assertThat(lineStatus(line.lineId())).isEqualTo("RECORDED|INVESTMENT_INTEREST");

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                held.investmentAccount(),
                Expected.cashbookAndInvestment("-95.00"),
                () -> linePostingService.unmatch(line.lineId())));
    assertThat(list(held.investmentId()).getFirst().status()).isEqualTo("REVERSED");
    assertThat(lineStatus(line.lineId())).isEqualTo("UNMATCHED|NULL");
    assertThat(get(held.investmentId()).netInterest()).isEqualByComparingTo("0");

    // The reversed entry drops out of the one-POSTED-per-line index: the line records again
    var again =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.00"),
            held.investmentId(),
            new RecordInvestmentInterestRequest(
                date, new BigDecimal("100.00"), null, InterestPresentation.NET, line.lineId()));
    assertThat(again.bankStatementLineId()).isEqualTo(line.lineId());
    assertThat(postedEntriesOnLine(line.lineId())).isEqualTo(1);

    // A second POSTED entry on the same line is refused by the database itself — the backstop
    // behind the service's match-status check, whatever path writes the row
    assertThatThrownBy(
            () ->
                jdbc.update(
                    ("INSERT INTO \"%s\".trust_investment_interest (investment_id, entry_date,"
                            + " gross_amount, fund_share_amount, net_amount, presentation,"
                            + " net_transaction_id, bank_statement_line_id, status, recorded_by)"
                            + " SELECT investment_id, entry_date, gross_amount, fund_share_amount,"
                            + " net_amount, presentation, net_transaction_id,"
                            + " bank_statement_line_id, 'POSTED', recorded_by"
                            + " FROM \"%s\".trust_investment_interest WHERE id = ?")
                        .formatted(books.schema(), books.schema()),
                    again.id()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("idx_invest_interest_posted_line");
    assertThat(postedEntriesOnLine(line.lineId())).isEqualTo(1);
  }

  // --- path 21: section 86(3) interest is the Fund's ----------------------------------------

  @Test
  void path21_section86_3_interestOnAnInvestmentAccount_isFundInterest_andItsSweep() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("s86(3) cover");
    books.deposit(funding, client, "5000.00");
    var placed = books.requestPlacement(funding, investmentAccount, null, "1500.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-1500.00"),
        investmentAccount,
        Expected.cashbookAndInterAccount("1500.00"),
        placed.depositTransactionId(),
        approverId);
    var date = pastDate();
    var credit = books.unmatchedLine(investmentAccount, date, "12.34", "INT-S863");
    var sweep = books.addLine(credit.statementId(), 2, date, "-12.34", "LPFF-S863");

    recordFromLine(
        investmentAccount,
        Expected.cashbookAndFund("12.34"),
        credit.lineId(),
        StatementLineClassification.FUND_INTEREST);
    recordFromLine(
        investmentAccount,
        Expected.cashbookAndFund("-12.34"),
        sweep,
        StatementLineClassification.FUND_SWEEP);

    // The investment's balance is its principal: its interest was never the investment's
    assertThat(get(placed.id()).balance()).isEqualByComparingTo("1500.00");
    assertThat(get(placed.id()).netInterest()).isEqualByComparingTo("0");
    // And no interest entry is ever recorded against it
    assertRecordRefused(
        new Held(funding, investmentAccount, null, placed.id()),
        net(date, "12.34"),
        INTEREST_TITLE,
        "Interest on a section 86(3) investment belongs to the Legal Practitioners' Fidelity Fund"
            + " — record it from the investment account's statement as interest received");
  }

  // --- the 5% and its tolerance (OL-5) ------------------------------------------------------

  @Test
  void fundShare_isFivePercentHalfUp_aTypedShareWithinACentIsKept_furtherIsRefused() {
    var held = s86_4Investment("10000.00");
    var date = pastDate();

    // 5% of 100.10 = 5.005 → 5.01 (half-up)
    var computed =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.09"),
            held.investmentId(),
            net(date, "100.10"));
    assertThat(computed.fundShareAmount()).isEqualByComparingTo("5.01");
    assertThat(computed.netAmount()).isEqualByComparingTo("95.09");

    // The bank's own rounding, a cent away, is mirrored
    var typed =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("95.10"),
            held.investmentId(),
            new RecordInvestmentInterestRequest(
                date,
                new BigDecimal("100.10"),
                new BigDecimal("5.00"),
                InterestPresentation.NET,
                null));
    assertThat(typed.fundShareAmount()).isEqualByComparingTo("5.00");
    assertThat(typed.netAmount()).isEqualByComparingTo("95.10");

    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date, new BigDecimal("100.10"), new BigDecimal("4.99"), InterestPresentation.NET, null),
        INTEREST_TITLE,
        "The Fund's share must be within R0,01 of 5% of the gross interest (R5,01).");
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date,
            new BigDecimal("100.10"),
            new BigDecimal("-1.00"),
            InterestPresentation.NET,
            null),
        INTEREST_TITLE,
        "The Fund's share must be zero or more, in rand and cents.");
    assertRecordRefused(
        held, net(date, "0.00"), "Invalid amount", "Amount must be a positive value, got: 0.00");
    // A tiny NET credit with no Fund share is a valid entry (fund 0,00)
    var tiny =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("0.09"),
            held.investmentId(),
            net(date, "0.09"));
    assertThat(tiny.fundShareAmount()).isEqualByComparingTo("0");
  }

  // --- the tax reference is a snapshot (A3) -------------------------------------------------

  @Test
  void clientTaxReference_isSnapshottedAtEntry() {
    var held = s86_4Investment("1000.00");
    books.setTaxNumberDirect(held.client(), "1111111111");
    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("9.50"),
            held.investmentId(),
            net(pastDate(), "10.00"));
    books.setTaxNumberDirect(held.client(), "2222222222");

    assertThat(entry.clientTaxReference()).isEqualTo("1111111111");
    assertThat(list(held.investmentId()).getFirst().clientTaxReference()).isEqualTo("1111111111");
  }

  // --- refusals: nothing moves --------------------------------------------------------------

  @Test
  void refusals_presentationPlacementDatesAndTheLine() {
    var held = s86_4Investment("1000.00");
    var date = pastDate();

    // OQ-4: a gross credit is recorded from its statement line
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date, new BigDecimal("100.00"), null, InterestPresentation.GROSS, null),
        INTEREST_TITLE,
        "A gross interest credit is recorded from the investment account's statement line (the"
            + " Fund's 5% leaves in a separate debit).");
    // OQ-7: dates
    assertRecordRefused(
        held,
        net(TrustBooksFixture.POSTING_DATE.minusDays(1), "10.00"),
        INTEREST_TITLE,
        "The entry date cannot be before the investment's deposit date.");
    assertRecordRefused(
        held,
        net(books.today().plusDays(1), "10.00"),
        INTEREST_TITLE,
        "The entry date cannot be in the future.");

    // From a line: its date, its amount, its account
    var line = books.unmatchedLine(held.investmentAccount(), date, "400.00", "INT-REF");
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date.minusDays(1),
            new BigDecimal("400.00"),
            null,
            InterestPresentation.GROSS,
            line.lineId()),
        RECORD_TITLE,
        "The entry date is the statement line's date (" + date + ").");
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date, new BigDecimal("300.00"), null, InterestPresentation.GROSS, line.lineId()),
        RECORD_TITLE,
        "The statement line is R400,00; a gross presentation records the line's whole amount as"
            + " gross.");
    var elsewhere = books.unmatchedLine(books.freshAccount(false), date, "400.00", "INT-ELSE");
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date, new BigDecimal("400.00"), null, InterestPresentation.GROSS, elsewhere.lineId()),
        RECORD_TITLE,
        "This statement line is not on the investment account that holds the investment.");
    // OQ-18: a gross credit whose 5% rounds to R0,00
    var tinyLine = books.addLine(line.statementId(), 2, date, "0.09", "INT-TINY");
    assertRecordRefused(
        held,
        new RecordInvestmentInterestRequest(
            date, new BigDecimal("0.09"), null, InterestPresentation.GROSS, tinyLine),
        INTEREST_TITLE,
        "A gross interest credit this small has no Fund share to record separately; record it as"
            + " net of the Fund's 5%.");
    assertThat(lineStatus(line.lineId())).isEqualTo("UNMATCHED|NULL");
    assertThat(list(held.investmentId())).isEmpty();
  }

  @Test
  void refusals_aPlacementAwaitingApproval() {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("interest awaiting approval");
    books.deposit(funding, client, "500.00");
    var placed = books.requestPlacement(funding, investmentAccount, client, "500.00");

    assertRecordRefused(
        new Held(funding, investmentAccount, client, placed.id()),
        net(pastDate(), "10.00"),
        INTEREST_TITLE,
        "The investment's placement must be approved before interest is recorded (the placement"
            + " is awaiting approval).");
  }

  // --- OQ-1: no withdrawal before the last interest entry -----------------------------------

  @Test
  void withdrawal_isRefused_beforeTheLastPostedEntry_butAReversedEntryDoesNotCount() {
    var held = s86_4Investment("1000.00");
    var entry =
        books.recordInterest(
            held.investmentAccount(),
            Expected.cashbookAndInvestment("38.00"),
            held.investmentId(),
            net(books.today(), "40.00"));

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                held.funding(),
                Expected.none(),
                held.investmentAccount(),
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () ->
                                investmentService.withdrawInvestment(
                                    held.investmentId(),
                                    new TrustInvestmentService.WithdrawInvestmentRequest(
                                        books.today().minusDays(1))))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Cannot withdraw investment");
                              assertThat(e.getBody().getDetail())
                                  .isEqualTo(
                                      "The withdrawal date cannot be before the investment's"
                                          + " last interest entry ("
                                          + books.today()
                                          + ").");
                            })));

    books.reverseInterest(
        held.investmentAccount(), Expected.cashbookAndInvestment("-38.00"), entry.id());
    var withdrawn =
        books.withdraw(
            held.funding(),
            Expected.cashbookAndClient("1000.00"),
            held.investmentAccount(),
            Expected.cashbookAndInvestment("-1000.00"),
            held.investmentId(),
            books.today().minusDays(1));
    assertThat(withdrawn.status()).isEqualTo("WITHDRAWN");
  }

  // --- helpers ------------------------------------------------------------------------------

  /** A section 86(4) investment (client may be null for a section 86(3) one in a refusal). */
  private record Held(UUID funding, UUID investmentAccount, Client client, UUID investmentId) {}

  /**
   * A section 86(4) investment of {@code principal}, placed {@link TrustBooksFixture#POSTING_DATE}
   * and approved.
   */
  private Held s86_4Investment(String principal) {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4) interest");
    books.deposit(funding, client, principal);
    var placed = books.requestPlacement(funding, investmentAccount, client, principal);
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-" + principal),
        investmentAccount,
        Expected.cashbookAndInvestment(principal),
        placed.depositTransactionId(),
        approverId);
    return new Held(funding, investmentAccount, client, placed.id());
  }

  /** A bank-advice NET entry of {@code gross} on {@code date} (the 5% computed). */
  private static RecordInvestmentInterestRequest net(LocalDate date, String gross) {
    return new RecordInvestmentInterestRequest(
        date, new BigDecimal(gross), null, InterestPresentation.NET, null);
  }

  /**
   * Records {@code lineId} as {@code classification} (647A); the account moves by {@code expected}.
   */
  private void recordFromLine(
      UUID account, Expected expected, UUID lineId, StatementLineClassification classification) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                expected,
                () ->
                    linePostingService.recordFromLine(
                        lineId,
                        new RecordFromLineRequest(
                            classification, null, null, null, null, null, null, null))));
  }

  /** Kazi's reference for the {@code n}th interest entry on the investment from a bank advice. */
  private static String adviceReference(UUID investmentId, int n) {
    return "INV-INT-" + investmentId.toString().substring(0, 8).toUpperCase(Locale.ROOT) + "-" + n;
  }

  /** The interest entry is refused with 400 {@code title} / {@code detail}; nothing moves. */
  private void assertRecordRefused(
      Held held, RecordInvestmentInterestRequest request, String title, String detail) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                held.funding(),
                Expected.none(),
                held.investmentAccount(),
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () -> investmentService.recordInterest(held.investmentId(), request))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(title);
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
  }

  /** The reversal is refused with 400 {@link #REVERSE_TITLE} / {@code detail}; nothing moves. */
  private void assertReverseRefused(UUID investmentAccount, UUID interestId, String detail) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                investmentAccount,
                Expected.none(),
                () ->
                    assertThatThrownBy(() -> investmentService.reverseInterest(interestId))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle()).isEqualTo(REVERSE_TITLE);
                              assertThat(e.getBody().getDetail()).isEqualTo(detail);
                            })));
  }

  private TrustInvestmentService.TrustInvestmentResponse get(UUID investmentId) {
    var response = new TrustInvestmentService.TrustInvestmentResponse[1];
    books.as(ownerId, () -> response[0] = investmentService.getInvestment(investmentId));
    return response[0];
  }

  private List<InvestmentInterestResponse> list(UUID investmentId) {
    @SuppressWarnings("unchecked")
    List<InvestmentInterestResponse>[] entries = new List[1];
    books.as(ownerId, () -> entries[0] = investmentService.listInterest(investmentId));
    return entries[0];
  }

  private TrustTransaction row(UUID id) {
    return books.read(() -> transactionRepository.findById(id).orElseThrow());
  }

  /** {@code match_status|classification} of a line. */
  private int postedEntriesOnLine(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_investment_interest".formatted(books.schema())
            + " WHERE bank_statement_line_id = ? AND status = 'POSTED'",
        Integer.class,
        lineId);
  }

  private String lineStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status || '|' || COALESCE(classification, 'NULL') FROM"
            + " \"%s\".bank_statement_lines WHERE id = ?".formatted(books.schema()),
        String.class,
        lineId);
  }

  private AuditEvent audit(String eventType, UUID investmentId) {
    return audit("trust_investment", investmentId, eventType);
  }

  private AuditEvent audit(String entityType, UUID entityId, String eventType) {
    var events =
        books.read(
            () ->
                auditEventRepository
                    .findByFilter(
                        entityType, entityId, null, eventType, null, null, PageRequest.of(0, 10))
                    .getContent());
    assertThat(events).as(eventType).hasSize(1);
    return events.getFirst();
  }
}
