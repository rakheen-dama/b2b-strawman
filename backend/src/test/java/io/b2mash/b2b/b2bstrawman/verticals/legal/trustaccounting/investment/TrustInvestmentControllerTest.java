package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestIdempotency;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.idempotency.TrustIdempotencyService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentControllerTest {

  private static final String ORG_ID = "org_trust_invest_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.customer.CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbc;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;
  private String firmInvestmentAccountId;
  private String clientInvestmentAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Investment Controller Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_invest_ctrl_owner",
        "invest_ctrl_owner@test.com",
        "Invest Ctrl Owner",
        "owner");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_invest_ctrl_member",
        "invest_ctrl_member@test.com",
        "Invest Ctrl Member",
        "member");
    // Approves placement payments (single-approval mode refuses self-approval)
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_invest_ctrl_approver",
        "invest_ctrl_approver@test.com",
        "Invest Ctrl Approver",
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

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

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
                          "accountName": "Investment Ctrl Test Trust Account",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000300",
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
            mockMvc, ownerJwt, "Investment Ctrl Test Customer", "invest_ctrl_test@test.com");

    // Deposit funds for the customer (100,000)
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
                      "amount": 100000.00,
                      "reference": "DEP-INVEST-CTRL-001",
                      "description": "Setup deposit for investment controller tests",
                      "transactionDate": "2026-01-15"
                    }
                    """
                        .formatted(customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    // 648A: investments are held in INVESTMENT accounts of their basis
    firmInvestmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
                mockMvc,
                ownerJwt,
                "Ctrl Section 86(3) Investments",
                "62000000301",
                InvestmentBasis.FIRM_DISCRETION)
            .toString();
    clientInvestmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
                mockMvc,
                ownerJwt,
                "Ctrl Section 86(4) Investments",
                "62000000302",
                InvestmentBasis.CLIENT_INSTRUCTION)
            .toString();
  }

  // --- 446.8: Controller integration tests ---

  @Test
  void postInvestment_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "ABSA Bank",
                      "accountNumber": "9012345678",
                      "principal": 20000.00,
                      "interestRate": 0.0650,
                      "depositDate": "2026-02-01",
                      "maturityDate": "2026-08-01",
                      "notes": "6-month fixed deposit",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(
                            clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.principal").value(20000.00))
        .andExpect(jsonPath("$.institution").value("ABSA Bank"))
        .andExpect(jsonPath("$.clientName").value("Investment Ctrl Test Customer"))
        .andExpect(jsonPath("$.clientId").value(customerId))
        .andExpect(jsonPath("$.matterId").value(trustMatterOf(customerId).toString()))
        .andExpect(jsonPath("$.investmentAccountId").value(clientInvestmentAccountId))
        .andExpect(jsonPath("$.investmentAccountName").value("Ctrl Section 86(4) Investments"))
        .andExpect(jsonPath("$.balance").value(0))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  // --- 648B: section 86(4) interest entries (§16.4.5) ---

  @Test
  void postInterest_returns201WithTheEntry() throws Exception {
    var investmentId = approvedClientInvestment("Interest Bank", "5550000001", "10000.00");

    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/interest")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entryDate": "2026-03-01", "grossAmount": 100.00, "presentation": "NET"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.investmentId").value(investmentId))
        .andExpect(jsonPath("$.entryDate").value("2026-03-01"))
        .andExpect(jsonPath("$.grossAmount").value(100.00))
        .andExpect(jsonPath("$.fundShareAmount").value(5.00))
        .andExpect(jsonPath("$.netAmount").value(95.00))
        .andExpect(jsonPath("$.presentation").value("NET"))
        .andExpect(jsonPath("$.status").value("POSTED"))
        .andExpect(jsonPath("$.netTransactionId").isNotEmpty())
        .andExpect(jsonPath("$.fundShareTransactionId").value(nullValue()))
        .andExpect(jsonPath("$.bankStatementLineId").value(nullValue()))
        .andExpect(jsonPath("$.clientTaxReference").value(nullValue()));

    // The investment's figures follow its entries; its balance carries the net interest
    mockMvc
        .perform(
            get("/api/trust-investments/" + investmentId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grossInterest").value(100.00))
        .andExpect(jsonPath("$.fundShare").value(5.00))
        .andExpect(jsonPath("$.netInterest").value(95.00))
        .andExpect(jsonPath("$.balance").value(10095.00))
        .andExpect(jsonPath("$.interestEarned").doesNotExist());
  }

  @Test
  void getInterest_returnsTheEntries() throws Exception {
    var investmentId = approvedClientInvestment("Interest List Bank", "5550000002", "5000.00");
    postNetInterest(investmentId, "2026-03-01", "40.00");
    postNetInterest(investmentId, "2026-03-02", "60.00");

    mockMvc
        .perform(
            get("/api/trust-investments/" + investmentId + "/interest")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].entryDate").value("2026-03-01"))
        .andExpect(jsonPath("$[0].grossAmount").value(40.00))
        .andExpect(jsonPath("$[0].fundShareAmount").value(2.00))
        .andExpect(jsonPath("$[1].entryDate").value("2026-03-02"))
        .andExpect(jsonPath("$[1].netAmount").value(57.00));
  }

  @Test
  void reverseInterest_returns200Reversed() throws Exception {
    var investmentId = approvedClientInvestment("Interest Undo Bank", "5550000003", "5000.00");
    var interestId = postNetInterest(investmentId, "2026-03-01", "100.00");

    mockMvc
        .perform(
            post("/api/trust-investment-interest/" + interestId + "/reverse")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Bank advice captured twice\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(interestId))
        .andExpect(jsonPath("$.status").value("REVERSED"));

    mockMvc
        .perform(
            get("/api/trust-investments/" + investmentId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.netInterest").value(0))
        .andExpect(jsonPath("$.balance").value(5000.00));

    // D-2 (2026-11-23): the reason is the contra's description and the audit reason, as for every
    // other trust reversal — never the old fixed text
    var netRowId =
        jdbc.queryForObject(
            "SELECT net_transaction_id FROM \"%s\".trust_investment_interest WHERE id = ?::uuid"
                .formatted(tenantSchema),
            UUID.class,
            interestId);
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM \"%s\".trust_transactions WHERE reversal_of = ?"
                    .formatted(tenantSchema),
                String.class,
                netRowId))
        .isEqualTo("Bank advice captured twice");
    assertThat(
            jdbc.queryForList(
                """
                SELECT details->>'reason' FROM "%s".audit_events
                WHERE event_type = 'trust_transaction.reversed'
                  AND details->>'original_transaction_id' = ?
                """
                    .formatted(tenantSchema),
                String.class,
                netRowId.toString()))
        .containsExactly("Bank advice captured twice");
  }

  /**
   * D-2 + F-11: the reversal keeps {@code @IdempotentTrustWrite}, and the request hash now covers
   * the reason — the same key with the same reason replays; with another reason it is refused 409.
   */
  @Test
  void reverseInterest_sameKey_replaysTheSameReason_andRefusesADifferentOne() throws Exception {
    var investmentId = approvedClientInvestment("Interest Key Bank", "5550000014", "5000.00");
    var interestId = postNetInterest(investmentId, "2026-03-01", "100.00");
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");
    var key = UUID.randomUUID().toString();
    var url = "/api/trust-investment-interest/" + interestId + "/reverse";

    mockMvc
        .perform(
            post(url)
                .with(owner)
                .header(TestIdempotency.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Captured twice\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVERSED"));
    mockMvc
        .perform(
            post(url)
                .with(owner)
                .header(TestIdempotency.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Captured twice\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(TrustIdempotencyService.REPLAYED_HEADER, "true"));
    mockMvc
        .perform(
            post(url)
                .with(owner)
                .header(TestIdempotency.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Another reason\"}"))
        .andExpect(status().isConflict());
  }

  /** D-2: an interest reversal requires a reason, like every other trust reversal (400). */
  @Test
  void reverseInterest_withoutAReason_returns400_andReversesNothing() throws Exception {
    var investmentId = approvedClientInvestment("Interest Reason Bank", "5550000013", "5000.00");
    var interestId = postNetInterest(investmentId, "2026-03-01", "100.00");
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    // No body at all
    mockMvc
        .perform(post("/api/trust-investment-interest/" + interestId + "/reverse").with(owner))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Malformed request body"));
    // A body without a reason, and a blank one
    for (var body : List.of("{}", "{\"reason\": \"   \"}")) {
      mockMvc
          .perform(
              post("/api/trust-investment-interest/" + interestId + "/reverse")
                  .with(owner)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.title").value("Validation failed"))
          .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
    }

    mockMvc
        .perform(get("/api/trust-investments/" + investmentId + "/interest").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(interestId))
        .andExpect(jsonPath("$[0].status").value("POSTED"));
  }

  @Test
  void postInterest_onASection86_3Investment_returns400() throws Exception {
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "institution": "Nedbank",
                          "accountNumber": "5551234567",
                          "principal": 10000.00,
                          "interestRate": 0.0700,
                          "depositDate": "2026-02-01",
                          "maturityDate": "2026-05-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(firmInvestmentAccountId)))
            .andExpect(status().isCreated())
            .andReturn();
    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/interest")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entryDate": "2026-03-01", "grossAmount": 175.00, "presentation": "NET"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Cannot record investment interest"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Interest on a section 86(3) investment belongs to the Legal Practitioners'"
                        + " Fidelity Fund — record it from the investment account's statement as"
                        + " interest received"));
  }

  @Test
  void postInterest_memberRole_returns403() throws Exception {
    var investmentId = approvedClientInvestment("Interest Member Bank", "5550000004", "1000.00");

    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/interest")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entryDate": "2026-03-01", "grossAmount": 10.00, "presentation": "NET"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    mockMvc
        .perform(
            post("/api/trust-investment-interest/" + UUID.randomUUID() + "/reverse")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Captured twice\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void putInterest_isGone() throws Exception {
    var investmentId = approvedClientInvestment("Interest Put Bank", "5550000005", "1000.00");

    mockMvc
        .perform(
            put("/api/trust-investments/" + investmentId + "/interest")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 175.00}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.title").value("Method Not Allowed"))
        .andExpect(jsonPath("$.detail").value("Method 'PUT' is not supported."));
  }

  @Test
  void getMaturing_returnsInvestments() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    var maturingDate = LocalDate.now().plusDays(10).toString();

    // Place an investment maturing soon
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "institution": "FNB",
                      "accountNumber": "1112223334",
                      "principal": 5000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-01-01",
                      "maturityDate": "%s",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(firmInvestmentAccountId, maturingDate)))
        .andExpect(status().isCreated());

    // Query maturing within 30 days
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/investments/maturing")
                .param("daysAhead", "30")
                .with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  // --- 446.9: Controller authorization tests ---

  @Test
  void postWithdraw_returns200WithWithdrawnStatus() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    // Place an investment
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "institution": "Standard Bank",
                          "accountNumber": "7778889990",
                          "principal": 3000.00,
                          "interestRate": 0.0550,
                          "depositDate": "2026-02-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(firmInvestmentAccountId)))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    // PR #1757 review: only a completed (approved) placement can be withdrawn
    var placementId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.depositTransactionId")
            .toString();
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_approver")))
        .andExpect(status().isOk());

    // Withdraw
    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/withdraw")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"withdrawalDate\": \"2026-03-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"))
        .andExpect(jsonPath("$.withdrawalAmount").value(3000.00))
        .andExpect(jsonPath("$.withdrawalDate").value("2026-03-01"))
        .andExpect(jsonPath("$.balance").value(0));
  }

  @Test
  void postInvestment_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "institution": "Test Bank",
                      "accountNumber": "0001112223",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-02-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(firmInvestmentAccountId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getInvestments_memberRole_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member")))
        .andExpect(status().isOk());
  }

  @Test
  void postWithdraw_memberRole_returns403() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    // Place an investment as owner first
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "institution": "Capitec",
                          "accountNumber": "4445556667",
                          "principal": 2000.00,
                          "interestRate": 0.0450,
                          "depositDate": "2026-02-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(firmInvestmentAccountId)))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    // Attempt withdraw as member — should be forbidden
    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/withdraw")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"withdrawalDate\": \"2026-03-01\"}"))
        .andExpect(status().isForbidden());
  }

  // --- 453.11: Controller basis CRUD tests ---

  @Test
  void postInvestment_withClientInstruction_returns201WithBasis() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "Investec",
                      "accountNumber": "8880001111",
                      "principal": 5000.00,
                      "interestRate": 0.0750,
                      "depositDate": "2026-03-01",
                      "maturityDate": "2026-09-01",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(
                            clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentBasis").value("CLIENT_INSTRUCTION"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void postInvestment_withFirmDiscretion_returns201WithBasis() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "institution": "Capitec Basis",
                      "accountNumber": "8880002222",
                      "principal": 4000.00,
                      "interestRate": 0.0600,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(firmInvestmentAccountId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.investmentBasis").value("FIRM_DISCRETION"));
  }

  @Test
  void getInvestment_returnsBasisInResponse() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "clientId": "%s",
                          "matterId": "%s",
                          "institution": "FNB Basis",
                          "accountNumber": "8880003333",
                          "principal": 3000.00,
                          "interestRate": 0.0500,
                          "depositDate": "2026-03-01",
                          "investmentBasis": "CLIENT_INSTRUCTION"
                        }
                        """
                            .formatted(
                                clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(get("/api/trust-investments/" + investmentId).with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.investmentBasis").value("CLIENT_INSTRUCTION"));
  }

  @Test
  void listInvestments_filteredByBasis_returnsMatchingOnly() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    // Create a CLIENT_INSTRUCTION investment
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "Filter Test Bank CI",
                      "accountNumber": "8880004444",
                      "principal": 2000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(
                            clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
        .andExpect(status().isCreated());

    // Also create a FIRM_DISCRETION investment so the filter has something to exclude
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "institution": "Filter Test Bank FD",
                      "accountNumber": "8880005555",
                      "principal": 2000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(firmInvestmentAccountId)))
        .andExpect(status().isCreated());

    // Filter by CLIENT_INSTRUCTION
    var result =
        mockMvc
            .perform(
                get("/api/trust-accounts/" + trustAccountId + "/investments")
                    .param("investmentBasis", "CLIENT_INSTRUCTION")
                    .with(ownerJwt))
            .andExpect(status().isOk())
            .andReturn();

    var content = result.getResponse().getContentAsString();
    var investments =
        (net.minidev.json.JSONArray) JsonPath.read(content, "$.content[*].investmentBasis");
    assertThat(investments).isNotEmpty();
    for (Object basis : investments) {
      assertThat(basis.toString()).isEqualTo("CLIENT_INSTRUCTION");
    }
  }

  // --- 453.12: Controller basis validation tests ---

  @Test
  void postInvestment_withoutBasis_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "No Basis Bank",
                      "accountNumber": "9990001111",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01"
                    }
                    """
                        .formatted(
                            clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postInvestment_withInvalidBasis_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "Invalid Basis Bank",
                      "accountNumber": "9990002222",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "INVALID_VALUE"
                    }
                    """
                        .formatted(
                            clientInvestmentAccountId, customerId, trustMatterOf(customerId))))
        .andExpect(status().isBadRequest());
  }

  // --- 648A: section 86(3) names no client; the withdrawal takes only its date ---

  @Test
  void postInvestment_firmDiscretionNamingAClient_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "institution": "Firm With Client Bank",
                      "accountNumber": "9990003333",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(firmInvestmentAccountId, customerId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot place investment"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "A section 86(3) investment is the firm's placement of surplus trust money and"
                        + " names no client or matter."));
  }

  @Test
  void postInvestment_withoutAnInvestmentAccount_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "institution": "No Account Bank",
                      "accountNumber": "9990004444",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("investmentAccountId"));
  }

  @Test
  void postWithdraw_withoutADate_returns400() throws Exception {
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "institution": "No Date Bank",
                          "accountNumber": "9990005555",
                          "principal": 1000.00,
                          "interestRate": 0.0500,
                          "depositDate": "2026-03-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(firmInvestmentAccountId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value(nullValue()))
            .andExpect(jsonPath("$.clientName").value(nullValue()))
            .andExpect(jsonPath("$.matterId").value(nullValue()))
            .andReturn();
    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();
    // Approved first, so the request reaches the date's validation (a pending placement is
    // refused on its own)
    var placementId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.depositTransactionId")
            .toString();
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_approver")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/withdraw")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("withdrawalDate"));
  }

  /** A section 86(4) investment placed from the trust account and approved; its id. */
  private String approvedClientInvestment(
      String institution, String accountNumber, String principal) throws Exception {
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "clientId": "%s",
                          "matterId": "%s",
                          "institution": "%s",
                          "accountNumber": "%s",
                          "principal": %s,
                          "interestRate": 0.0500,
                          "depositDate": "2026-02-01",
                          "investmentBasis": "CLIENT_INSTRUCTION"
                        }
                        """
                            .formatted(
                                clientInvestmentAccountId,
                                customerId,
                                trustMatterOf(customerId),
                                institution,
                                accountNumber,
                                principal)))
            .andExpect(status().isCreated())
            .andReturn();
    var body = investResult.getResponse().getContentAsString();
    mockMvc
        .perform(
            post("/api/trust-transactions/"
                    + JsonPath.read(body, "$.depositTransactionId")
                    + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_approver")))
        .andExpect(status().isOk());
    return JsonPath.read(body, "$.id").toString();
  }

  /** Records a bank-advice NET entry of {@code gross} on {@code date}; the entry's id. */
  private String postNetInterest(String investmentId, String date, String gross) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-investments/" + investmentId + "/interest")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entryDate\": \"%s\", \"grossAmount\": %s, \"presentation\": \"NET\"}"
                            .formatted(date, gross)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
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
