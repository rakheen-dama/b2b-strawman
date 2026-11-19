package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientTrustStatementContext;
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
 * Phase 92 slice 652D (task 652.10; §16.6.4, §16.9.3, §16.14.4): the E8 report that the two client
 * documents — the Statement of Account trust section ({@link StatementOfAccountContextBuilder}) and
 * the Client Trust Statement ({@link ClientTrustStatementContext}) — go wrong when a client's
 * primary trust account is closed. A hypothesis: this class is the reproduction. Each case asserts
 * what a correct document shows, so a defect makes it red.
 *
 * <ul>
 *   <li><b>(a)</b> client A's only trust account {@code K} is closed (R5 000 deposited, R5 000 paid
 *       out, account closed): both documents generate, print {@code K}'s history and R0,00.
 *   <li><b>(b)</b> client B's money was on the tenant's primary {@code GENERAL} account {@code P};
 *       it was paid out, {@code P} was closed, and R10 000 was deposited on a replacement account
 *       {@code R}: both documents must print {@code R} and R10 000,00 — the account that holds the
 *       client's money — not the closed primary with R0,00.
 *   <li><b>(c) observation only</b> (§16.14.4: the demo seeder's main account is a primary {@code
 *       SECTION_86} account while the builders look for a primary {@code GENERAL}): client C's only
 *       account is a primary {@code SECTION_86} account holding R7 000. Recorded, not part of the
 *       652D trigger.
 * </ul>
 *
 * Every posting goes through {@code TrustBooksHarness.assertMoves} (the fixture); the class ends
 * with {@code assertFirmBalanced(today)}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementBuilderClosedPrimaryAccountIntegrationTest {

  private static final String ORG_ID = "org_652d_closed_primary_account";
  private static final String OWNER = "user_cpa_owner";
  private static final String APPROVER = "user_cpa_approver";
  private static final Set<String> TRUST_READER =
      Set.of("VIEW_TRUST", "MANAGE_TRUST", "VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
  private static final Locale ZA = VariableFormatter.resolveLocale("ZAR");

  /** Every fixture row is dated {@link TrustBooksFixture#POSTING_DATE} (2026-03-01). */
  private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);

  private static final String CLOSED_ONLY_ACCOUNT = "CPA Closed Only Trust Account";
  private static final String CLOSED_PRIMARY_ACCOUNT = "CPA Closed Primary Trust Account";
  private static final String REPLACEMENT_ACCOUNT = "CPA Replacement Trust Account";
  private static final String SECTION_86_MAIN_ACCOUNT = "CPA Trust Account Main s86";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;
  @Autowired private ClientTrustStatementContext clientTrustStatementContext;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;

  private String schema;
  private UUID ownerId;
  private UUID approverId;
  private TrustBooksFixture.Tenant books;
  private LocalDate today;

  private UUID closedOnlyAccount;
  private UUID closedPrimaryAccount;
  private UUID replacementAccount;
  private UUID section86MainAccount;
  private Client clientA;
  private Client clientB;
  private Client clientC;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Closed Primary Account Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "cpa_owner@test.com", "CPA Owner", "owner"));
    // Single-approval mode refuses self-approval: a second member approves the payments
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER, "cpa_approver@test.com", "CPA Approver", "admin"));
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
    today = books.today();

    // (a) client A: one account K, money in and out, K closed
    closedOnlyAccount = openAccount(CLOSED_ONLY_ACCOUNT, "62000065201", "GENERAL", false);
    clientA = books.newClient("closed only account");
    books.deposit(closedOnlyAccount, clientA, "5000.00");
    payOut(closedOnlyAccount, clientA, "5000.00");
    close(closedOnlyAccount);

    // (b) client B: money on the primary GENERAL account P, paid out, P closed, then R10 000 on a
    // replacement account R
    closedPrimaryAccount = openAccount(CLOSED_PRIMARY_ACCOUNT, "62000065202", "GENERAL", true);
    clientB = books.newClient("closed primary account");
    books.deposit(closedPrimaryAccount, clientB, "10000.00");
    payOut(closedPrimaryAccount, clientB, "10000.00");
    close(closedPrimaryAccount);
    replacementAccount = openAccount(REPLACEMENT_ACCOUNT, "62000065203", "GENERAL", false);
    books.deposit(replacementAccount, clientB, "10000.00");

    // (c) client C: the demo seeder's shape — the main account is a primary SECTION_86 account
    section86MainAccount = openAccount(SECTION_86_MAIN_ACCOUNT, "62000065204", "SECTION_86", true);
    clientC = books.newClient("section 86 main account");
    books.deposit(section86MainAccount, clientC, "7000.00");
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- control: the fixture is what the cases say it is -------------------------------------

  @Test
  void fixture_accountsAndLedgersAreAsDescribed() {
    var closedOnly = books.read(() -> trustAccountRepository.findById(closedOnlyAccount));
    var closedPrimary = books.read(() -> trustAccountRepository.findById(closedPrimaryAccount));
    var section86 = books.read(() -> trustAccountRepository.findById(section86MainAccount));
    assertThat(closedOnly.orElseThrow().getStatus()).isEqualTo(TrustAccountStatus.CLOSED);
    assertThat(closedPrimary.orElseThrow().getStatus()).isEqualTo(TrustAccountStatus.CLOSED);
    assertThat(closedPrimary.orElseThrow().isPrimary())
        .as("closing an account leaves its primary flag set")
        .isTrue();
    assertThat(closedPrimary.orElseThrow().getAccountType()).isEqualTo(TrustAccountType.GENERAL);
    assertThat(section86.orElseThrow().getAccountType()).isEqualTo(TrustAccountType.SECTION_86);
    assertThat(section86.orElseThrow().isPrimary()).isTrue();

    assertThat(books.clientLedger(closedOnlyAccount, clientA)).isEqualByComparingTo("0");
    assertThat(books.clientLedger(closedPrimaryAccount, clientB)).isEqualByComparingTo("0");
    assertThat(books.clientLedger(replacementAccount, clientB)).isEqualByComparingTo("10000.00");
    assertThat(books.clientLedger(section86MainAccount, clientC)).isEqualByComparingTo("7000.00");
  }

  // --- (a) the client's only trust account is closed ----------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void a_onlyAccountClosed_statementOfAccount_printsItsHistoryAndRZero() {
    var context = soaContext(clientA);
    var trust = (Map<String, Object>) context.get("trust");

    assertThat(trust.get("status")).isEqualTo("available");
    assertThat(amount(trust.get("opening_balance"))).isEqualByComparingTo("0");
    assertThat(amount(trust.get("closing_balance"))).isEqualByComparingTo("0");
    assertThat((List<Map<String, Object>>) trust.get("deposits"))
        .extracting(row -> row.get("transactionType"))
        .containsExactly("DEPOSIT");
    assertThat((List<Map<String, Object>>) trust.get("payments"))
        .extracting(row -> row.get("transactionType"))
        .containsExactly("PAYMENT");
    var summary = (Map<String, Object>) context.get("summary");
    assertThat(amount(summary.get("trust_balance_held"))).isEqualByComparingTo("0");
  }

  @Test
  @SuppressWarnings("unchecked")
  void a_onlyAccountClosed_clientTrustStatement_printsItsHistoryAndRZero() {
    var block = inTenant(() -> clientTrustStatementContext.buildFor(clientA.id())).orElseThrow();

    assertThat(block.get("status")).isEqualTo("available");
    assertThat(block.get("account_name")).isEqualTo(CLOSED_ONLY_ACCOUNT);
    assertThat(amount(block.get("closing_balance"))).isEqualByComparingTo("0");
    assertThat((List<Map<String, Object>>) block.get("lines"))
        .extracting(line -> line.get("transactionType"))
        .containsExactly("DEPOSIT", "PAYMENT");
  }

  @Test
  void a_onlyAccountClosed_bothDocumentsRender() throws Exception {
    assertThat(soaHtml(clientA))
        .contains("Trust Activity")
        .contains("Closing balance: </strong>" + currency("0"))
        .doesNotContain("Trust details are not available.");
    assertThat(ctsHtml(clientA))
        .contains(CLOSED_ONLY_ACCOUNT)
        .contains("The current balance held in trust on your behalf is");
  }

  // --- (b) the primary GENERAL account is closed, another account holds the money -----------

  @Test
  @SuppressWarnings("unchecked")
  void b_primaryClosed_statementOfAccount_readsTheAccountHoldingTheMoney() {
    var context = soaContext(clientB);
    var trust = (Map<String, Object>) context.get("trust");

    assertThat(trust.get("status")).isEqualTo("available");
    assertThat(amount(trust.get("closing_balance")))
        .as(
            "SoA trust closing balance: client B holds R10 000 on the replacement account; the"
                + " closed primary holds R0 for them")
        .isEqualByComparingTo("10000.00");
    assertThat((List<Map<String, Object>>) trust.get("payments"))
        .as("no payment was made from the account that holds client B's money")
        .isEmpty();
    assertThat((List<Map<String, Object>>) trust.get("deposits"))
        .extracting(row -> row.get("transactionType"))
        .containsExactly("DEPOSIT");
    var summary = (Map<String, Object>) context.get("summary");
    assertThat(amount(summary.get("trust_balance_held")))
        .as("SoA summary trust balance held")
        .isEqualByComparingTo("10000.00");
  }

  @Test
  @SuppressWarnings("unchecked")
  void b_primaryClosed_clientTrustStatement_readsTheAccountHoldingTheMoney() {
    var block = inTenant(() -> clientTrustStatementContext.buildFor(clientB.id())).orElseThrow();

    assertThat(block.get("status")).isEqualTo("available");
    assertThat(block.get("account_name"))
        .as("Client Trust Statement account: the open account holding client B's money")
        .isEqualTo(REPLACEMENT_ACCOUNT);
    assertThat(amount(block.get("closing_balance")))
        .as("Client Trust Statement closing balance")
        .isEqualByComparingTo("10000.00");
    assertThat((List<Map<String, Object>>) block.get("lines"))
        .extracting(line -> line.get("transactionType"))
        .containsExactly("DEPOSIT");
  }

  @Test
  void b_primaryClosed_bothDocumentsRender_withTheMoneyHeld() throws Exception {
    assertThat(soaHtml(clientB))
        .contains("Trust Activity")
        .contains("Closing balance: </strong>" + currency("10000.00"));
    assertThat(ctsHtml(clientB))
        .contains(REPLACEMENT_ACCOUNT)
        .doesNotContain(CLOSED_PRIMARY_ACCOUNT);
  }

  // --- (c) observation: the main account is a primary SECTION_86 account (§16.14.4) ---------

  @Test
  @SuppressWarnings("unchecked")
  void c_observation_section86MainAccount_bothBuildersReadIt() {
    var trust = (Map<String, Object>) soaContext(clientC).get("trust");
    assertThat(trust.get("status")).isEqualTo("available");
    assertThat(amount(trust.get("closing_balance"))).isEqualByComparingTo("7000.00");

    var block = inTenant(() -> clientTrustStatementContext.buildFor(clientC.id())).orElseThrow();
    assertThat(block.get("account_name")).isEqualTo(SECTION_86_MAIN_ACCOUNT);
    assertThat(amount(block.get("closing_balance"))).isEqualByComparingTo("7000.00");
  }

  // --- helpers ------------------------------------------------------------------------------

  private UUID openAccount(String name, String accountNumber, String type, boolean primary) {
    var id = new UUID[1];
    books.as(
        ownerId,
        () ->
            id[0] =
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            name,
                            "First National Bank",
                            "250655",
                            accountNumber,
                            type,
                            primary,
                            false,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "652D closed-primary-account fixture"))
                    .id());
    return id[0];
  }

  /** A payment of the client's whole balance, approved by the second member. */
  private void payOut(UUID account, Client client, String amount) {
    UUID payment = books.requestPayment(account, client, amount);
    books.approve(account, payment, approverId, Expected.cashbookAndClient("-" + amount));
  }

  private void close(UUID account) {
    books.as(ownerId, () -> trustAccountService.closeTrustAccount(account));
  }

  private Map<String, Object> soaContext(Client client) {
    return inTenant(() -> statementContextBuilder.build(client.matter(), PERIOD_START, today));
  }

  /** The Statement of Account over HTTP (POST /api/matters/{id}/statements → htmlPreview). */
  private String soaHtml(Client client) throws Exception {
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
                            .formatted(PERIOD_START, today)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.htmlPreview");
  }

  /** The Client Trust Statement preview over HTTP. */
  private String ctsHtml(Client client) throws Exception {
    UUID ctsId =
        inTenant(
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

  private static BigDecimal amount(Object value) {
    return new BigDecimal(String.valueOf(value));
  }

  private static String currency(String amount) {
    return VariableFormatter.format(new BigDecimal(amount), "currency", ZA);
  }

  private <T> T inTenant(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, TRUST_READER)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
