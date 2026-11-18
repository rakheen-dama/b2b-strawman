package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerControllerTest {
  private static final String ORG_ID = "org_client_ledger_ctrl_test";
  private static final String OWNER_SUB = "user_ledger_ctrl_owner";
  private static final String APPROVER_SUB = "user_ledger_ctrl_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.customer.CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;
  // 648E: a second account funds section 86(4) placements (the first is pinned at one client)
  private String fundingAccountId;
  private String investmentAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Client Ledger Controller Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_ledger_ctrl_owner",
        "ledger_ctrl_owner@test.com",
        "Ledger Ctrl Owner",
        "owner");

    // Enable trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner");

    // Create a trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Ledger Controller Test Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000002",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);

    // Create a customer
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Ledger Test Customer", "ledger_test@test.com");

    // Deposit funds to create a ledger card
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 25000.00,
                      "reference": "DEP-LEDGER-SETUP",
                      "description": "Setup deposit for ledger tests",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    // 648E: single-approval mode refuses self-approval — a second owner approves placements
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        APPROVER_SUB,
        "ledger_ctrl_approver@test.com",
        "Ledger Ctrl Approver",
        "owner");
    var fundingResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Ledger Controller s86(4) Funding Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000012",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    fundingAccountId = TestEntityHelper.extractId(fundingResult);
    investmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
                mockMvc,
                ownerJwt,
                "Ledger Ctrl s86(4) Investments",
                "62000000013",
                InvestmentBasis.CLIENT_INSTRUCTION)
            .toString();
  }

  // --- 442.9: Controller integration tests -- ledger ---

  @Test
  void getClientLedgers_returnsPaginatedList() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/client-ledgers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isNotEmpty())
        .andExpect(jsonPath("$.content[0].clientId").value(customerId))
        .andExpect(jsonPath("$.content[0].clientName").value("Ledger Test Customer"))
        .andExpect(jsonPath("$.content[0].balance").isNumber())
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void getByCustomer_returnsLedgerCard() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/client-ledgers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(customerId))
        .andExpect(jsonPath("$.clientName").value("Ledger Test Customer"))
        .andExpect(jsonPath("$.balance").value(25000.00))
        .andExpect(jsonPath("$.totalDeposits").value(25000.00));
  }

  // GAP-S4-06: Customers without a ledger card yet (no deposits made) must return a synthetic
  // zero-balance response rather than 404, so the Project Trust tab on new matters renders R0.00
  // instead of an error state.
  @Test
  void getByCustomer_returnsEmptyZeroBalanceWhenNoLedgerCardExists() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner");

    // Create a brand-new customer that has never had a trust transaction.
    String newCustomerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "No Ledger Customer", "no_ledger@test.com");

    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/client-ledgers/" + newCustomerId)
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(nullValue()))
        .andExpect(jsonPath("$.clientId").value(newCustomerId))
        .andExpect(jsonPath("$.clientName").value("No Ledger Customer"))
        .andExpect(jsonPath("$.trustAccountId").value(trustAccountId))
        .andExpect(jsonPath("$.balance").value(0))
        .andExpect(jsonPath("$.totalDeposits").value(0))
        .andExpect(jsonPath("$.totalPayments").value(0))
        .andExpect(jsonPath("$.totalFeeTransfers").value(0))
        .andExpect(jsonPath("$.totalInterestCredited").doesNotExist())
        .andExpect(jsonPath("$.investedBalance").value(0))
        .andExpect(jsonPath("$.totalInvestmentPlacements").value(0))
        .andExpect(jsonPath("$.totalInvestmentReturns").value(0))
        .andExpect(jsonPath("$.lastTransactionDate").value(nullValue()))
        .andExpect(jsonPath("$.createdAt").value(nullValue()))
        .andExpect(jsonPath("$.updatedAt").value(nullValue()));
  }

  // --- 648E: the invested balance beside the general balance (ADR-379 L7) ---

  @Test
  void getByCustomer_afterAnApprovedSection86_4Placement_showsGeneralInvestedAndPlacementTotals()
      throws Exception {
    var inv = investor("Ledger Ctrl Investor", "50000.00", "30000.00", "LC-FD-1");

    // Awaiting approval: nothing has moved
    var pending = card(fundingAccountId, inv.clientId());
    assertThat(money(pending.get("balance"))).isEqualByComparingTo("50000.00");
    assertThat(money(pending.get("investedBalance"))).isEqualByComparingTo("0");
    assertThat(money(pending.get("totalInvestmentPlacements"))).isEqualByComparingTo("0");

    approve(inv.placementId());

    // Approved: P left the general balance for the client's section 86(4) investment
    var card = card(fundingAccountId, inv.clientId());
    assertThat(money(card.get("balance"))).isEqualByComparingTo("20000.00");
    assertThat(money(card.get("investedBalance"))).isEqualByComparingTo("30000.00");
    assertThat(money(card.get("totalInvestmentPlacements"))).isEqualByComparingTo("30000.00");
    assertThat(money(card.get("totalInvestmentReturns"))).isEqualByComparingTo("0");
    assertThat(money(card.get("totalTransfersOut"))).isEqualByComparingTo("0");
    // The client's total held in trust is unchanged by the placement
    assertThat(money(card.get("balance")).add(money(card.get("investedBalance"))))
        .isEqualByComparingTo("50000.00");

    // Nothing is placed from the investment account: its card for the client is empty
    var onInvestmentAccount = card(investmentAccountId, inv.clientId());
    assertThat(money(onInvestmentAccount.get("balance"))).isEqualByComparingTo("0");
    assertThat(money(onInvestmentAccount.get("investedBalance"))).isEqualByComparingTo("0");
  }

  @Test
  void getClientLedgers_listRowsCarryInvestedBalance_zeroForAClientWhoNeverInvested()
      throws Exception {
    var inv = investor("Ledger Ctrl List Investor", "20000.00", "12500.00", "LC-FD-2");
    approve(inv.placementId());
    var bystander =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
            "Ledger Ctrl Bystander",
            "ledger_ctrl_bystander@test.com");
    deposit(fundingAccountId, bystander, "4000.00", "LC-DEP-BYSTANDER");

    var list =
        mockMvc
            .perform(
                get("/api/trust-accounts/" + fundingAccountId + "/client-ledgers?size=200")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var investorRow = row(list, inv.clientId());
    assertThat(money(investorRow.get("balance"))).isEqualByComparingTo("7500.00");
    assertThat(money(investorRow.get("investedBalance"))).isEqualByComparingTo("12500.00");
    var bystanderRow = row(list, bystander);
    assertThat(money(bystanderRow.get("balance"))).isEqualByComparingTo("4000.00");
    assertThat(money(bystanderRow.get("investedBalance"))).isEqualByComparingTo("0");
    assertThat(money(bystanderRow.get("totalInvestmentPlacements"))).isEqualByComparingTo("0");
  }

  // GAP-S4-06 follow-up (PR #1004 review): an unknown customer id must still 404 — the
  // zero-balance synthetic fallback is reserved for real customers who simply have no ledger
  // card yet. Otherwise arbitrary customer UUIDs look indistinguishable from valid new ones.
  @Test
  void getByCustomer_returns404WhenCustomerDoesNotExist() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner");
    UUID unknownCustomerId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/client-ledgers/" + unknownCustomerId)
                .with(ownerJwt))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Client not found"));
  }

  @Test
  void getHistory_returnsTransactionList() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/"
                    + trustAccountId
                    + "/client-ledgers/"
                    + customerId
                    + "/history")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isNotEmpty())
        .andExpect(jsonPath("$.content[0].transactionType").value("DEPOSIT"))
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void getTotalBalance_returnsCorrectSum() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/total-balance")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(25000.00));
  }

  @Test
  void getStatement_returnsStatementWithBalances() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/"
                    + trustAccountId
                    + "/client-ledgers/"
                    + customerId
                    + "/statement")
                .param("startDate", "2026-02-01")
                .param("endDate", "2026-03-31")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingBalance").isNumber())
        .andExpect(jsonPath("$.closingBalance").isNumber())
        .andExpect(jsonPath("$.closingBalance").value(25000.00))
        .andExpect(jsonPath("$.transactions").isArray())
        .andExpect(jsonPath("$.transactions").isNotEmpty());
  }

  @Test
  void getStatement_rejectsInvalidDateRange() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/"
                    + trustAccountId
                    + "/client-ledgers/"
                    + customerId
                    + "/statement")
                .param("startDate", "2026-04-10")
                .param("endDate", "2026-04-01")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("endDate must be on or after startDate"));
  }

  // --- 442.10: Closing guard integration tests ---

  @Test
  void closeTrustAccount_failsWhenClientHasNonZeroBalance() throws Exception {
    // Create a separate trust account with a non-zero client balance
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner");

    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Close Guard Test Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000003",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var closeTestAccountId = TestEntityHelper.extractId(accountResult);

    // Deposit funds so account has non-zero balance
    mockMvc
        .perform(
            post("/api/trust-accounts/" + closeTestAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 5000.00,
                      "reference": "DEP-CLOSE-GUARD",
                      "description": "Deposit for close guard test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    // Attempt to close -- should fail
    mockMvc
        .perform(post("/api/trust-accounts/" + closeTestAccountId + "/close").with(ownerJwt))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Non-zero client trust balance of R5000.00 must be resolved first."));
  }

  @Test
  void closeTrustAccount_succeedsWhenAllBalancesAreZero() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_ledger_ctrl_owner");

    // Create a trust account with no deposits (zero balance)
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Close Success Test Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000004",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var zeroBalanceAccountId = TestEntityHelper.extractId(accountResult);

    // Close should succeed since no client ledger cards exist (total balance = 0)
    mockMvc
        .perform(post("/api/trust-accounts/" + zeroBalanceAccountId + "/close").with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  // --- 648E helpers (domain-specific: placement + approval) ---

  private record Investor(String clientId, String investmentId, String placementId) {}

  /** A client with {@code deposit} on the funding account and a pending s86(4) placement. */
  private Investor investor(String name, String deposit, String principal, String accountNumber)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
    var clientId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, name, name.toLowerCase().replace(' ', '_') + "@test.com");
    deposit(fundingAccountId, clientId, deposit, "DEP-" + accountNumber);
    var placed =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + fundingAccountId + "/investments")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "clientId": "%s",
                          "matterId": "%s",
                          "institution": "Investec",
                          "accountNumber": "%s",
                          "principal": %s,
                          "interestRate": 0.0650,
                          "depositDate": "2026-03-05",
                          "maturityDate": null,
                          "investmentBasis": "CLIENT_INSTRUCTION"
                        }
                        """
                            .formatted(
                                investmentAccountId,
                                clientId,
                                trustMatterOf(clientId),
                                accountNumber,
                                principal)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Investor(
        clientId,
        (String) JsonPath.read(placed, "$.id"),
        (String) JsonPath.read(placed, "$.depositTransactionId"));
  }

  private void approve(String placementId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());
  }

  private void deposit(String accountId, String clientId, String amount, String reference)
      throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
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
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(clientId, trustMatterOf(clientId), amount, reference)))
        .andExpect(status().isCreated());
  }

  private Map<String, Object> card(String accountId, String clientId) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(
                get("/api/trust-accounts/" + accountId + "/client-ledgers/" + clientId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$");
  }

  private static Map<String, Object> row(String listBody, String clientId) {
    List<Map<String, Object>> rows =
        JsonPath.read(listBody, "$.content[?(@.clientId == '" + clientId + "')]");
    assertThat(rows).as("list row for " + clientId).hasSize(1);
    return rows.getFirst();
  }

  private static BigDecimal money(Object jsonNumber) {
    assertThat(jsonNumber).as("money value").isNotNull();
    return new BigDecimal(jsonNumber.toString());
  }

  private final java.util.Map<String, UUID> trustMatters = new java.util.HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(String clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                customerRepository,
                UUID.fromString(id),
                "Trust matter " + id));
  }
}
