package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.ClientTrustBalancesQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * 648A gate D-1: once a section 86(4) placement is approved, its {@code INV_PLACE} row (and, after
 * the withdrawal, its {@code INV_RETURN} row) sits on the client's general ledger. Every firm read
 * surface that touches that client — and the client-ledgers list, which carries every client of the
 * account — must still answer 200, and the card's totals must still reconcile to the balance
 * (deposits + transfers in − payments − fee transfers − refunds − transfers out). Before the fix
 * {@code LedgerTotals.plus} threw for both types, so the card, the list (for every client) and the
 * Trust overview that reads the list returned 500.
 *
 * <p>Everything goes through the HTTP API (placement, approval by a second member, withdrawal and
 * every read), except the Client Trust Balances report, which is read through its query bean as in
 * {@link ClientLedgerTotalsIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentClientLedgerReadSurfacesIntegrationTest {

  private static final String ORG_ID = "org_inv_client_ledger_reads";
  private static final String OWNER_SUB = "user_icl_owner";
  private static final String APPROVER_SUB = "user_icl_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ClientTrustBalancesQuery clientTrustBalancesQuery;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID ownerId;
  private LocalDate today;
  private String fundingAccountId;
  private String investmentAccountId;
  private String investor;
  private UUID investorMatter;
  private String bystander;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Investment Client Ledger Reads Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "icl_owner@test.com", "ICL Owner", "owner"));
    // Single-approval mode refuses self-approval: a second owner approves the placement
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "icl_approver@test.com", "ICL Approver", "owner");
    today = LocalDate.now(clock);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "ICL Trust Banking Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000700",
                          "accountType": "GENERAL",
                          "isPrimary": true,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    fundingAccountId = TestEntityHelper.extractId(accountResult);
    investmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
                "ICL Section 86(4) Investments",
                "62000000701",
                InvestmentBasis.CLIENT_INSTRUCTION)
            .toString();

    investor =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
            "ICL Investor",
            "icl_inv@test.com");
    bystander =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
            "ICL Bystander",
            "icl_bystander@test.com");
    investorMatter = matterOf(investor);
    deposit(investor, investorMatter, "50000.00", "ICL-DEP-1");
    deposit(bystander, matterOf(bystander), "10000.00", "ICL-DEP-2");
  }

  @Test
  void approvedSection86_4Placement_andItsWithdrawal_everyClientReadSurfaceAnswers_andReconciles()
      throws Exception {
    var placed = place("30000.00");
    var investmentId = (String) JsonPath.read(placed, "$.id");
    var placementId = (String) JsonPath.read(placed, "$.depositTransactionId");

    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());

    // INV_PLACE posted: the investor's general balance is P lower (R50 000 − R30 000); to their
    // general ledger the placement is a transfer out to their investment
    var card = card(investor);
    assertThat(money(card.get("balance"))).isEqualByComparingTo("20000.00");
    assertThat(money(card.get("totalDeposits"))).isEqualByComparingTo("50000.00");
    assertThat(money(card.get("totalTransfersOut"))).isEqualByComparingTo("30000.00");
    assertThat(money(card.get("totalTransfersIn"))).isEqualByComparingTo("0.00");
    assertReconciles(card);
    assertEveryReadSurfaceAnswers(investmentId, "20000.00", "30000.00");

    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/withdraw")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"withdrawalDate\": \"%s\"}".formatted(today.minusDays(5))))
        .andExpect(status().isOk());

    // INV_RETURN posted: the balance is restored; the return is a transfer back in
    card = card(investor);
    assertThat(money(card.get("balance"))).isEqualByComparingTo("50000.00");
    assertThat(money(card.get("totalTransfersOut"))).isEqualByComparingTo("30000.00");
    assertThat(money(card.get("totalTransfersIn"))).isEqualByComparingTo("30000.00");
    assertReconciles(card);
    assertEveryReadSurfaceAnswers(investmentId, "50000.00", "0.00");
  }

  /**
   * Every read surface the firm app calls for the investor or the account: the card, the list (both
   * clients), the history and statement, the account totals, the Trust overview's reads, and the
   * matter's balance, the register and the investment — all 200, all agreeing on the investor's
   * general balance.
   */
  private void assertEveryReadSurfaceAnswers(
      String investmentId, String investorGeneral, String invested) throws Exception {
    // Client ledgers list — every client of the account, including one who never invested
    var list = getOk("/api/trust-accounts/" + fundingAccountId + "/client-ledgers?size=200");
    var investorRow = row(list, investor);
    assertThat(money(investorRow.get("balance"))).isEqualByComparingTo(investorGeneral);
    assertReconciles(investorRow);
    var bystanderRow = row(list, bystander);
    assertThat(money(bystanderRow.get("balance"))).isEqualByComparingTo("10000.00");
    assertReconciles(bystanderRow);

    // History (newest first, server running balance) and statement
    var history =
        getOk(
            "/api/trust-accounts/" + fundingAccountId + "/client-ledgers/" + investor + "/history");
    List<String> types = JsonPath.read(history, "$.content[*].transactionType");
    assertThat(types).contains("DEPOSIT", "INV_PLACE");
    var statement =
        getOk(
            "/api/trust-accounts/"
                + fundingAccountId
                + "/client-ledgers/"
                + investor
                + "/statement?startDate="
                + today.minusDays(60)
                + "&endDate="
                + today);
    assertThat(money(JsonPath.read(statement, "$.closingBalance")))
        .isEqualByComparingTo(investorGeneral);

    // Account totals: client ledgers R10 000 + the investor's general balance
    var total = getOk("/api/trust-accounts/" + fundingAccountId + "/total-balance");
    assertThat(money(JsonPath.read(total, "$.balance")))
        .isEqualByComparingTo(new BigDecimal(investorGeneral).add(new BigDecimal("10000.00")));

    // The Trust Accounting overview's reads (fetchDashboardData + StatCards)
    getOk("/api/trust-accounts/" + fundingAccountId + "/cashbook-balance");
    getOk("/api/trust-accounts/" + fundingAccountId + "/pending-approvals");
    getOk(
        "/api/trust-accounts/"
            + fundingAccountId
            + "/transactions?size=10&sort=transactionDate,desc");
    getOk("/api/dashboard/trust-position?accountId=" + fundingAccountId);
    getOk("/api/dashboard/trust-balance-trend?accountId=" + fundingAccountId + "&months=12");

    // The matter Trust tab: the matter's total (general + invested) never moves, R50 000
    var matter = getOk("/api/matters/" + investorMatter + "/trust-balance");
    assertThat(money(JsonPath.read(matter, "$.balance"))).isEqualByComparingTo("50000.00");

    // Client Trust Balances report (also folds LedgerTotals)
    var reportRow = reportRow(UUID.fromString(investor));
    assertThat(money(reportRow.get("balance"))).isEqualByComparingTo(investorGeneral);

    // The investment register and the investment itself: what is still invested
    getOk("/api/trust-accounts/" + fundingAccountId + "/investments");
    var investment = getOk("/api/trust-investments/" + investmentId);
    assertThat(money(JsonPath.read(investment, "$.balance"))).isEqualByComparingTo(invested);
  }

  // --- Helpers ---

  private String place(String principal) throws Exception {
    return mockMvc
        .perform(
            post("/api/trust-accounts/" + fundingAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "Investec",
                      "accountNumber": "ICL-FD-1",
                      "principal": %s,
                      "interestRate": 0.0650,
                      "depositDate": "%s",
                      "maturityDate": "%s",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(
                            investmentAccountId,
                            investor,
                            investorMatter,
                            principal,
                            today.minusDays(20),
                            today.plusDays(150))))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void deposit(String client, UUID matter, String amount, String reference)
      throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + fundingAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Deposit",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(client, matter, amount, reference, today.minusDays(30))))
        .andExpect(status().isCreated());
  }

  private Map<String, Object> card(String client) throws Exception {
    return JsonPath.read(
        getOk("/api/trust-accounts/" + fundingAccountId + "/client-ledgers/" + client), "$");
  }

  private static Map<String, Object> row(String listBody, String client) {
    List<Map<String, Object>> rows =
        JsonPath.read(listBody, "$.content[?(@.clientId == '" + client + "')]");
    assertThat(rows).as("list row for " + client).hasSize(1);
    return rows.getFirst();
  }

  private String getOk(String url) throws Exception {
    return mockMvc
        .perform(get(url).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private Map<String, Object> reportRow(UUID client) {
    var params = Map.<String, Object>of("trust_account_id", fundingAccountId);
    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .where(RequestScopes.MEMBER_ID, ownerId)
            .where(RequestScopes.ORG_ROLE, "owner")
            .where(RequestScopes.CAPABILITIES, Set.of("VIEW_TRUST", "MANAGE_TRUST"))
            .call(
                () ->
                    transactionTemplate.execute(tx -> clientTrustBalancesQuery.executeAll(params)));
    var rows = result.rows().stream().filter(r -> client.equals(r.get("customerId"))).toList();
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  /** Deposits + Transfers in − Payments − Fee transfers − Refunds − Transfers out = Balance. */
  private static void assertReconciles(Map<String, Object> ledger) {
    var derived =
        money(ledger.get("totalDeposits"))
            .add(money(ledger.get("totalTransfersIn")))
            .subtract(money(ledger.get("totalPayments")))
            .subtract(money(ledger.get("totalFeeTransfers")))
            .subtract(money(ledger.get("totalRefunds")))
            .subtract(money(ledger.get("totalTransfersOut")));
    assertThat(derived)
        .as("totals reconcile to the balance")
        .isEqualByComparingTo(money(ledger.get("balance")));
  }

  private static BigDecimal money(Object jsonNumber) {
    assertThat(jsonNumber).as("money value").isNotNull();
    return new BigDecimal(jsonNumber.toString());
  }

  private UUID matterOf(String clientId) {
    return TestTrustMatterHelper.openClientMatter(
        tenantSchema,
        transactionTemplate,
        projectRepository,
        customerRepository,
        UUID.fromString(clientId),
        "ICL trust matter " + clientId);
  }
}
