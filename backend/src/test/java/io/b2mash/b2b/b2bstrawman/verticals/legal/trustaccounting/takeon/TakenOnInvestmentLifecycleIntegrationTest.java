package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnCommitService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDrainJobHandler;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Accounts;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Validated;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 93 657C (task 657.24, §17.3.9, §17.3.13 "A taken-on investment after the cutover",
 * ADR-387): an investment taken on by the real commit path is an ordinary investment afterwards —
 * the interest entry and the withdrawal both find the take-on row as its placement ({@code
 * deposit_transaction_id}, posted {@code APPROVED} by the sign-off), the investment account
 * reconciles on its own statement, the withdrawal returns the investment balance (take-on row
 * included) to the take-on row's matter on the banking account, and a section 86(3) investment
 * withdraws as a firm investment. Dates are after the cutover date and no later than today (the
 * worked example's 30 November / 10 December become 20 / 23 November 2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakenOnInvestmentLifecycleIntegrationTest {

  private static final String ORG_ID = "org_taken_on_investment_test";
  private static final String OWNER = "user_til_owner";
  private static final LocalDate INTEREST_DATE = LocalDate.of(2026, 11, 20);
  private static final LocalDate WITHDRAWAL_DATE = LocalDate.of(2026, 11, 23);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;

  private String schema;
  private TrustBooksFixture.Tenant books;
  private TakeOnTrustWorld world;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Investment Attorneys", "legal-za")
            .schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "til_owner@test.com", "Investment Owner", "owner"));
    books = fixture.tenant(schema, ORG_ID, ownerId);
    world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, "til");
    world.takeOnClientsAndMatters(List.of("A", "B", "C"));
  }

  @AfterAll
  void booksStayBalanced() {
    books.assertFirmBalanced();
  }

  // 1, 2, 3, 4
  @Test
  void theSection864InvestmentEarnsInterest_reconciles_andWithdrawsToItsMatter() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID investment = investment(batch, "CLIENT_INSTRUCTION");
    UUID placement = placementOf(investment);
    assertThat(typeOf(placement)).isEqualTo("TAKEON_INVESTMENT");

    // 1. I4's statement: one line, the capitalised interest (it includes the R1 234,56 accrued at
    // cutover, a memo never posted — nothing counts twice), recorded GROSS: the client's net and
    // the Fund's 5% share. The entry is accepted: its placement is the approved take-on row.
    String statementId =
        importStatement(
            acc.i4(), acc.i4Number(), "20/11/2026,CAPITALISED INTEREST,1600.00,201600.00,INT-NOV");
    UUID line =
        jdbc.queryForObject(
            "SELECT id FROM \"%s\".bank_statement_lines WHERE bank_statement_id = ?::uuid"
                .formatted(schema),
            UUID.class,
            statementId);
    var entry =
        books.recordInterest(
            acc.i4(),
            new Expected(
                new BigDecimal("1600.00"),
                BigDecimal.ZERO,
                new BigDecimal("1520.00"),
                new BigDecimal("80.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            investment,
            new RecordInvestmentInterestRequest(
                INTEREST_DATE, new BigDecimal("1600.00"), null, InterestPresentation.GROSS, line));
    assertThat(entry.netAmount()).isEqualByComparingTo("1520.00");
    assertThat(entry.fundShareAmount()).isEqualByComparingTo("80.00");
    assertThat(books.read(() -> ledgerQueries.investmentBalance(investment, acc.i4())))
        .as("the take-on row and the net interest")
        .isEqualByComparingTo("201520.00");

    // 2. I4 reconciles on its own statement: cashbook 201 600 = bank, every line resolved
    reconcile(acc.i4(), statementId, "2026-11-20");

    // 3, 4. the withdrawal returns the whole investment balance to A / A1 on G
    UUID a = world.client("A");
    UUID a1 = world.matter("A1");
    var withdrawn =
        books.withdraw(
            acc.g(),
            Expected.cashbookAndClient("201520.00"),
            acc.i4(),
            Expected.cashbookAndInvestment("-201520.00"),
            investment,
            WITHDRAWAL_DATE);
    assertThat(withdrawn.withdrawalAmount()).isEqualByComparingTo("201520.00");
    var legs =
        jdbc.queryForList(
            ("SELECT trust_account_id, transaction_type, amount, customer_id, project_id FROM"
                    + " \"%s\".trust_transactions WHERE investment_id = ? AND transaction_type IN"
                    + " ('INV_WITHDRAW', 'INV_RETURN')")
                .formatted(schema),
            investment);
    assertThat(legs).hasSize(2);
    assertThat(legs)
        .anySatisfy(
            r -> {
              assertThat(r.get("transaction_type")).isEqualTo("INV_RETURN");
              assertThat(r.get("trust_account_id")).isEqualTo(acc.g());
              assertThat(r.get("customer_id")).isEqualTo(a);
              assertThat(r.get("project_id")).isEqualTo(a1);
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("201520.00");
            })
        .anySatisfy(
            r -> {
              assertThat(r.get("transaction_type")).isEqualTo("INV_WITHDRAW");
              assertThat(r.get("trust_account_id")).isEqualTo(acc.i4());
            });
    assertThat(books.read(() -> ledgerQueries.investmentBalance(investment, acc.i4())))
        .isEqualByComparingTo("0.00");
  }

  // 5
  @Test
  void theSection863InvestmentWithdrawsAsAFirmInvestment() throws Exception {
    Accounts acc = world.freshAccounts();
    Validated batch = world.committed(acc);
    UUID investment = investment(batch, "FIRM_DISCRETION");
    assertThat(typeOf(placementOf(investment))).isEqualTo("TAKEON_FIRM_INV_IN");

    books.withdraw(
        acc.g(),
        Expected.cashbookAndInterAccount("100000.00"),
        acc.i3(),
        Expected.cashbookAndInterAccount("-100000.00"),
        investment,
        WITHDRAWAL_DATE);
    assertThat(
            jdbc.queryForList(
                ("SELECT transaction_type FROM \"%s\".trust_transactions WHERE investment_id = ?"
                        + " AND take_on_batch_id IS NULL")
                    .formatted(schema),
                String.class,
                investment))
        .containsExactlyInAnyOrder("FIRM_INV_WITHDRAW", "FIRM_INV_RETURN");
  }

  // --- helpers ---

  private UUID investment(Validated batch, String basis) {
    return jdbc.queryForObject(
        ("SELECT id FROM \"%s\".trust_investments WHERE take_on_batch_id = ?"
                + " AND investment_basis = ?")
            .formatted(schema),
        UUID.class,
        batch.batchId(),
        basis);
  }

  private UUID placementOf(UUID investment) {
    return jdbc.queryForObject(
        "SELECT deposit_transaction_id FROM \"%s\".trust_investments WHERE id = ?"
            .formatted(schema),
        UUID.class,
        investment);
  }

  private String typeOf(UUID transaction) {
    return jdbc.queryForObject(
        "SELECT transaction_type FROM \"%s\".trust_transactions WHERE id = ?".formatted(schema),
        String.class,
        transaction);
  }

  /** Imports a one-statement CSV on {@code account} through the API; returns its id. */
  private String importStatement(UUID account, String accountNumber, String line) throws Exception {
    var csv =
        """
        FNB Trust Account Statement - Account %s
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(accountNumber, line);
    return JsonPath.read(
            json(
                mockMvc
                    .perform(
                        multipart("/api/trust-accounts/{accountId}/bank-statements", account)
                            .file(
                                new MockMultipartFile(
                                    "file",
                                    "til-statement-" + account + ".csv",
                                    "text/csv",
                                    csv.getBytes(StandardCharsets.UTF_8)))
                            .with(world.owner()))
                    .andExpect(status().isCreated())),
            "$.id")
        .toString();
  }

  /**
   * A reconciliation of {@code account} at {@code periodEnd} on the statement: calculate, complete.
   */
  private void reconcile(UUID account, String statementId, String periodEnd) throws Exception {
    String reconciliationId =
        JsonPath.read(
                json(
                    mockMvc
                        .perform(
                            post("/api/trust-accounts/{accountId}/reconciliations", account)
                                .with(world.owner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {"periodEnd": "%s", "bankStatementId": "%s"}
                                    """
                                        .formatted(periodEnd, statementId)))
                        .andExpect(status().isCreated())),
                "$.id")
            .toString();
    String expected = "{\"expectedBankStatementId\": \"%s\"}".formatted(statementId);
    String calculated =
        json(
            mockMvc
                .perform(
                    post("/api/trust-reconciliations/{id}/calculate", reconciliationId)
                        .with(world.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expected))
                .andExpect(status().isOk()));
    assertThat((Boolean) JsonPath.read(calculated, "$.isBalanced")).isTrue();
    mockMvc
        .perform(
            post("/api/trust-reconciliations/{id}/complete", reconciliationId)
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(expected))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }
}
