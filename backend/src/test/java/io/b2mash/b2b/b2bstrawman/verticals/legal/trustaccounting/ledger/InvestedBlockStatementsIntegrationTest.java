package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementOfAccountContextBuilder;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.RecordInvestmentInterestRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 648F (task 648.13; §16.6.4, ADR-379 L7; O-1 / 648E C-1): both client documents —
 * the Statement of Account and the Client Trust Statement — print a separate "Section 86(4)
 * investment" block: capital placed, the period's interest credited net with the gross and the
 * Fund's 5% shown, the capital returned to trust and the invested balance at the statement date.
 * Interest comes only from {@code INV_INTEREST} (E4); a reversed interest entry is not listed and
 * nets out. The general trust block is unchanged.
 *
 * <p>The worked example (brief §4), on one client C, a fresh funding account G and a section 86(4)
 * investment account I:
 *
 * <ol>
 *   <li>2026-03-01 deposit R50 000 on G; place R30 000 (G → I), approved by a second member
 *   <li>2026-03-10 interest NET, gross R100 → Fund R5, net R95
 *   <li>2026-03-15 interest NET, gross R50 → Fund R2,50, net R47,50 — reversed
 *   <li>2026-04-10 interest NET, gross R200 → Fund R10, net R190
 *   <li>2026-04-20 withdraw: R30 285 (capital + net interest) returned to G
 * </ol>
 *
 * The scenario is posted once ({@code @BeforeAll}, every step through {@code
 * TrustBooksHarness.assertMoves}); the tests only read it, and the class ends with {@code
 * assertFirmBalanced(today)}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestedBlockStatementsIntegrationTest {

  private static final String ORG_ID = "org_invested_block_statements_test";
  private static final String OWNER = "user_ibs_owner";
  private static final String APPROVER = "user_ibs_approver";
  private static final Set<String> TRUST_READER =
      Set.of("VIEW_TRUST", "MANAGE_TRUST", "VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
  private static final Set<String> NO_VIEW_TRUST =
      Set.of("VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
  private static final Locale ZA = VariableFormatter.resolveLocale("ZAR");

  private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
  private static final LocalDate MARCH_31 = LocalDate.of(2026, 3, 31);
  private static final LocalDate APRIL_1 = LocalDate.of(2026, 4, 1);
  private static final LocalDate APRIL_30 = LocalDate.of(2026, 4, 30);
  private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
  private static final LocalDate MAY_31 = LocalDate.of(2026, 5, 31);

  /** What no client document may print: the enum codes or their sentence-cased form (N-1). */
  private static final List<String> RAW_TYPES =
      List.of("Inv place", "Inv return", "INV_PLACE", "INV_RETURN", "INV_INTEREST");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;
  @Autowired private ClientTrustStatementContext clientTrustStatementContext;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;

  private String schema;
  private UUID ownerId;
  private TrustBooksFixture.Tenant books;
  private Client investor;
  private Client plainClient;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Invested Block Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "ibs_owner@test.com", "IBS Owner", "owner"));
    UUID approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER, "ibs_approver@test.com", "IBS Approver", "admin"));
    // The statement endpoint needs `disbursements`, the trust blocks `trust_accounting`
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("disbursements", "trust_accounting"));
                      settings.updateCurrency("ZAR");
                      orgSettingsRepository.save(settings);
                    }));
    books = fixture.tenant(schema, ORG_ID, ownerId);

    UUID funding = books.freshAccount(false);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    investor = books.newClient("s86(4) investor");
    books.deposit(funding, investor, "50000.00");
    var placed = books.requestPlacement(funding, investmentAccount, investor, "30000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverId);
    books.recordInterest(
        investmentAccount,
        Expected.cashbookAndInvestment("95.00"),
        placed.id(),
        net(LocalDate.of(2026, 3, 10), "100.00"));
    var reversed =
        books.recordInterest(
            investmentAccount,
            Expected.cashbookAndInvestment("47.50"),
            placed.id(),
            net(LocalDate.of(2026, 3, 15), "50.00"));
    books.reverseInterest(
        investmentAccount, Expected.cashbookAndInvestment("-47.50"), reversed.id());
    books.recordInterest(
        investmentAccount,
        Expected.cashbookAndInvestment("190.00"),
        placed.id(),
        net(LocalDate.of(2026, 4, 10), "200.00"));
    books.withdraw(
        funding,
        Expected.cashbookAndClient("30285.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("-30285.00"),
        placed.id(),
        LocalDate.of(2026, 4, 20));

    plainClient = books.newClient("no investment");
    books.deposit(funding, plainClient, "1000.00");
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- Statement of Account ---------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void statementOfAccount_march_investedBlock_placementAndNetInterest_reversedEntryExcluded() {
    var trust = soaTrust(investor, MARCH_1, MARCH_31, TRUST_READER);

    // The general block is unchanged: R50 000 in, R30 000 placed out
    assertThat(amount(trust.get("closing_balance"))).isEqualByComparingTo("20000.00");
    assertThat((List<Map<String, Object>>) trust.get("payments"))
        .extracting(row -> row.get("transactionTypeLabel"))
        .containsExactly("Placed in section 86(4) investment");

    assertThat(trust).containsKey("invested");
    var invested = (Map<String, Object>) trust.get("invested");
    assertBlock(invested, "0", "30000.00", "100.00", "5.00", "95.00", "0", "30095.00");
    assertThat(amount(invested.get("total_held"))).isEqualByComparingTo("50095.00");
    var entries = (List<Map<String, Object>>) invested.get("interest_entries");
    assertThat(entries)
        .as("the reversed 2026-03-15 entry is not listed")
        .extracting(row -> row.get("entryDate"))
        .containsExactly(LocalDate.of(2026, 3, 10));
    assertEntriesNetEqualTheLedgerNet(invested);
  }

  @Test
  @SuppressWarnings("unchecked")
  void statementOfAccount_april_investedBlock_openingInterestAndReturn() {
    var trust = soaTrust(investor, APRIL_1, APRIL_30, TRUST_READER);

    assertThat(amount(trust.get("opening_balance"))).isEqualByComparingTo("20000.00");
    assertThat(amount(trust.get("closing_balance"))).isEqualByComparingTo("50285.00");
    assertThat((List<Map<String, Object>>) trust.get("deposits"))
        .extracting(row -> row.get("transactionTypeLabel"))
        .containsExactly("Investment returned to trust");

    assertThat(trust).containsKey("invested");
    var invested = (Map<String, Object>) trust.get("invested");
    assertBlock(invested, "30095.00", "0", "200.00", "10.00", "190.00", "30285.00", "0");
    assertThat(amount(invested.get("total_held"))).isEqualByComparingTo("50285.00");
    assertThat((List<Map<String, Object>>) invested.get("interest_entries"))
        .extracting(row -> row.get("entryDate"))
        .containsExactly(LocalDate.of(2026, 4, 10));
    assertEntriesNetEqualTheLedgerNet(invested);
  }

  @Test
  void statementOfAccount_aPeriodWithNothingInvestedAndNoMovement_hasNoInvestedBlock() {
    var trust = soaTrust(investor, MAY_1, MAY_31, TRUST_READER);

    assertThat(trust.get("status")).isEqualTo("available");
    assertThat(trust).doesNotContainKey("invested");
  }

  @Test
  void statementOfAccount_html_printsTheInvestedBlock_inZaCurrency() throws Exception {
    String march = soaHtml(investor, MARCH_1, MARCH_31);

    assertThat(march)
        .contains("Section 86(4) investment")
        .contains("Placed in section 86(4) investment: ")
        .contains("Fund share of section 86(4) interest (5%)")
        .contains("Gross interest", "Net interest")
        .contains("Invested on your instruction (section 86(4)): ")
        // each label with its own amount, so swapped bindings fail
        .contains("Opening balance: </strong>" + currency("0"))
        .contains("Placed in section 86(4) investment: </strong>" + currency("30000.00"))
        .contains("Gross interest: </strong>" + currency("100.00"))
        .contains("Fund share of section 86(4) interest (5%): </strong>" + currency("5.00"))
        .contains("Net interest: </strong>" + currency("95.00"))
        .contains("Investment returned to trust: </strong>" + currency("0"))
        .contains("Invested on your instruction (section 86(4)): </strong>" + currency("30095.00"))
        .contains("10 March 2026")
        .doesNotContain("15 March 2026")
        .doesNotContain("30095.00");
    RAW_TYPES.forEach(raw -> assertThat(march).doesNotContain(raw));

    String april = soaHtml(investor, APRIL_1, APRIL_30);
    assertThat(april)
        .contains("Section 86(4) investment")
        // R-E: the Deposits table's Type column prints the registry's client label
        .contains("<td>Investment returned to trust</td>")
        .contains("Opening balance: </strong>" + currency("30095.00"))
        .contains("Placed in section 86(4) investment: </strong>" + currency("0"))
        .contains("Gross interest: </strong>" + currency("200.00"))
        .contains("Fund share of section 86(4) interest (5%): </strong>" + currency("10.00"))
        .contains("Net interest: </strong>" + currency("190.00"))
        .contains("Investment returned to trust: </strong>" + currency("30285.00"))
        .contains("Invested on your instruction (section 86(4)): </strong>" + currency("0"));
    RAW_TYPES.forEach(raw -> assertThat(april).doesNotContain(raw));
  }

  // --- Client Trust Statement -------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void clientTrustStatement_investedBlock_allHistory() {
    var trust = inTenant(TRUST_READER, () -> clientTrustStatementContext.buildFor(investor.id()));

    assertThat(trust).isPresent();
    var block = trust.orElseThrow();
    assertThat(amount(block.get("closing_balance"))).isEqualByComparingTo("50285.00");
    assertThat((List<Map<String, Object>>) block.get("lines"))
        .extracting(row -> row.get("type"))
        .containsExactly(
            "Deposit", "Placed in section 86(4) investment", "Investment returned to trust");

    assertThat(block).containsKey("invested");
    var invested = (Map<String, Object>) block.get("invested");
    assertBlock(invested, "0", "30000.00", "300.00", "15.00", "285.00", "30285.00", "0");
    assertThat(amount(invested.get("total_held"))).isEqualByComparingTo("50285.00");
    assertThat((List<Map<String, Object>>) invested.get("interest_entries"))
        .extracting(row -> row.get("entryDate"))
        .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10));
    assertEntriesNetEqualTheLedgerNet(invested);
  }

  @Test
  void clientTrustStatement_html_printsTheInvestedBlockAndTheHeldInTrustLines() throws Exception {
    String html = ctsHtml(investor);

    assertThat(html)
        .contains("Section 86(4) investment")
        .contains("Placed in section 86(4) investment")
        .contains("Investment returned to trust")
        .contains("Fund share of section 86(4) interest (5%)")
        // the renderer HTML-escapes the apostrophe
        .contains("In the firm&#39;s trust account:</strong> " + currency("50285.00"))
        .contains("Held in trust:</strong> " + currency("50285.00"))
        .contains("Invested on your instruction (section 86(4)):</strong> " + currency("0"))
        // each label with its own amount, so swapped bindings fail
        .contains("Placed in section 86(4) investment:</strong> " + currency("30000.00"))
        .contains("Gross interest:</strong> " + currency("300.00"))
        .contains("Fund share of section 86(4) interest (5%):</strong> " + currency("15.00"))
        .contains("Net interest:</strong> " + currency("285.00"))
        .contains("Investment returned to trust:</strong> " + currency("30285.00"))
        .contains("10 March 2026", "10 April 2026")
        .doesNotContain("The current balance held in trust on your behalf is")
        .doesNotContain("30285.00");
    RAW_TYPES.forEach(raw -> assertThat(html).doesNotContain(raw));
  }

  // --- No investment, no access -----------------------------------------------------------

  @Test
  void clientWithoutAnInvestment_neitherDocumentShowsTheBlock() throws Exception {
    var cts =
        inTenant(TRUST_READER, () -> clientTrustStatementContext.buildFor(plainClient.id()))
            .orElseThrow();
    assertThat(cts).doesNotContainKey("invested");
    assertThat(soaTrust(plainClient, MARCH_1, MARCH_31, TRUST_READER))
        .doesNotContainKey("invested");

    assertThat(ctsHtml(plainClient))
        .doesNotContain("Section 86(4) investment")
        .contains("The current balance held in trust on your behalf is");
    assertThat(soaHtml(plainClient, MARCH_1, MARCH_31)).doesNotContain("Section 86(4) investment");
  }

  @Test
  void memberWithoutViewTrust_seesNoInvestedBlock() {
    var trust = soaTrust(investor, MARCH_1, MARCH_31, NO_VIEW_TRUST);
    assertThat(trust.get("status")).isEqualTo("withheld");
    assertThat(trust).doesNotContainKey("invested");

    assertThat(inTenant(NO_VIEW_TRUST, () -> clientTrustStatementContext.buildFor(investor.id())))
        .isEmpty();
  }

  // --- helpers ------------------------------------------------------------------------------

  private static RecordInvestmentInterestRequest net(LocalDate date, String gross) {
    return new RecordInvestmentInterestRequest(
        date, new BigDecimal(gross), null, InterestPresentation.NET, null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> soaTrust(
      Client client, LocalDate start, LocalDate end, Set<String> capabilities) {
    var context =
        inTenant(capabilities, () -> statementContextBuilder.build(client.matter(), start, end));
    return (Map<String, Object>) context.get("trust");
  }

  /** The Statement of Account over HTTP (POST /api/matters/{id}/statements → htmlPreview). */
  private String soaHtml(Client client, LocalDate start, LocalDate end) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/matters/" + client.matter() + "/statements")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"periodStart": "%s", "periodEnd": "%s", "templateId": null}
                        """
                            .formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.htmlPreview");
  }

  /** The Client Trust Statement preview over HTTP. */
  private String ctsHtml(Client client) throws Exception {
    UUID ctsId =
        inTenant(
            TRUST_READER,
            () ->
                documentTemplateRepository
                    .findPackSeeded("legal-za", "client-trust-statement")
                    .getFirst()
                    .getId());
    var result =
        mockMvc
            .perform(
                post("/api/templates/" + ctsId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entityId\":\"%s\"}".formatted(client.id())))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.html");
  }

  private static void assertBlock(
      Map<String, Object> invested,
      String opening,
      String placed,
      String gross,
      String fundShare,
      String net,
      String returned,
      String closing) {
    assertThat(amount(invested.get("opening_balance"))).as("opening").isEqualByComparingTo(opening);
    assertThat(amount(invested.get("capital_placed"))).as("placed").isEqualByComparingTo(placed);
    assertThat(amount(invested.get("interest_gross"))).as("gross").isEqualByComparingTo(gross);
    assertThat(amount(invested.get("interest_fund_share")))
        .as("Fund's 5%")
        .isEqualByComparingTo(fundShare);
    assertThat(amount(invested.get("interest_net"))).as("net").isEqualByComparingTo(net);
    assertThat(amount(invested.get("returned_to_trust")))
        .as("returned")
        .isEqualByComparingTo(returned);
    assertThat(amount(invested.get("closing_balance"))).as("closing").isEqualByComparingTo(closing);
    // opening + placed + net interest − returned = closing
    assertThat(
            amount(invested.get("opening_balance"))
                .add(amount(invested.get("capital_placed")))
                .add(amount(invested.get("interest_net")))
                .subtract(amount(invested.get("returned_to_trust"))))
        .isEqualByComparingTo(amount(invested.get("closing_balance")));
  }

  /** Σ the listed entries' net = the period's netted INV_INTEREST on the investment ledger. */
  @SuppressWarnings("unchecked")
  private static void assertEntriesNetEqualTheLedgerNet(Map<String, Object> invested) {
    var entries = (List<Map<String, Object>>) invested.get("interest_entries");
    BigDecimal net =
        entries.stream().map(row -> amount(row.get("netAmount"))).reduce(BigDecimal::add).get();
    BigDecimal gross =
        entries.stream().map(row -> amount(row.get("grossAmount"))).reduce(BigDecimal::add).get();
    BigDecimal fund =
        entries.stream()
            .map(row -> amount(row.get("fundShareAmount")))
            .reduce(BigDecimal::add)
            .get();
    assertThat(net).isEqualByComparingTo(amount(invested.get("interest_net")));
    assertThat(gross).isEqualByComparingTo(amount(invested.get("interest_gross")));
    assertThat(fund).isEqualByComparingTo(amount(invested.get("interest_fund_share")));
    assertThat(gross.subtract(fund)).isEqualByComparingTo(net);
  }

  private static BigDecimal amount(Object value) {
    return new BigDecimal(String.valueOf(value));
  }

  private static String currency(String amount) {
    return VariableFormatter.format(new BigDecimal(amount), "currency", ZA);
  }

  private <T> T inTenant(Set<String> capabilities, Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, capabilities)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
