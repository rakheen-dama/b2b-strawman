package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.time.LocalDate;
import java.util.List;
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
class TrustInvestmentControllerTest {

  private static final String ORG_ID = "org_trust_invest_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;

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
                      "customerId": "%s",
                      "amount": 100000.00,
                      "reference": "DEP-INVEST-CTRL-001",
                      "description": "Setup deposit for investment controller tests",
                      "transactionDate": "2026-01-15"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());
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
                      "customerId": "%s",
                      "institution": "ABSA Bank",
                      "accountNumber": "9012345678",
                      "principal": 20000.00,
                      "interestRate": 0.0650,
                      "depositDate": "2026-02-01",
                      "maturityDate": "2026-08-01",
                      "notes": "6-month fixed deposit",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.principal").value(20000.00))
        .andExpect(jsonPath("$.institution").value("ABSA Bank"))
        .andExpect(jsonPath("$.customerName").value("Investment Ctrl Test Customer"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void putInterest_returnsUpdatedInvestment() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_invest_ctrl_owner");

    // Place an investment first
    var investResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "institution": "Nedbank",
                          "accountNumber": "5551234567",
                          "principal": 10000.00,
                          "interestRate": 0.0700,
                          "depositDate": "2026-02-01",
                          "maturityDate": "2026-05-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    // Record interest
    mockMvc
        .perform(
            put("/api/trust-investments/" + investmentId + "/interest")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "amount": 175.00
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interestEarned").value(175.00))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.id").value(investmentId));
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
                      "customerId": "%s",
                      "institution": "FNB",
                      "accountNumber": "1112223334",
                      "principal": 5000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-01-01",
                      "maturityDate": "%s",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(customerId, maturingDate)))
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
                          "customerId": "%s",
                          "institution": "Standard Bank",
                          "accountNumber": "7778889990",
                          "principal": 3000.00,
                          "interestRate": 0.0550,
                          "depositDate": "2026-02-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    // Withdraw
    mockMvc
        .perform(post("/api/trust-investments/" + investmentId + "/withdraw").with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"))
        .andExpect(jsonPath("$.withdrawalAmount").isNumber())
        .andExpect(jsonPath("$.withdrawalDate").isNotEmpty());
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
                      "customerId": "%s",
                      "institution": "Test Bank",
                      "accountNumber": "0001112223",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-02-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(customerId)))
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
                          "customerId": "%s",
                          "institution": "Capitec",
                          "accountNumber": "4445556667",
                          "principal": 2000.00,
                          "interestRate": 0.0450,
                          "depositDate": "2026-02-01",
                          "investmentBasis": "FIRM_DISCRETION"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var investmentId =
        JsonPath.read(investResult.getResponse().getContentAsString(), "$.id").toString();

    // Attempt withdraw as member — should be forbidden
    mockMvc
        .perform(
            post("/api/trust-investments/" + investmentId + "/withdraw")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invest_ctrl_member")))
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
                      "customerId": "%s",
                      "institution": "Investec",
                      "accountNumber": "8880001111",
                      "principal": 5000.00,
                      "interestRate": 0.0750,
                      "depositDate": "2026-03-01",
                      "maturityDate": "2026-09-01",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(customerId)))
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
                      "customerId": "%s",
                      "institution": "Capitec Basis",
                      "accountNumber": "8880002222",
                      "principal": 4000.00,
                      "interestRate": 0.0600,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "FIRM_DISCRETION"
                    }
                    """
                        .formatted(customerId)))
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
                          "customerId": "%s",
                          "institution": "FNB Basis",
                          "accountNumber": "8880003333",
                          "principal": 3000.00,
                          "interestRate": 0.0500,
                          "depositDate": "2026-03-01",
                          "investmentBasis": "CLIENT_INSTRUCTION"
                        }
                        """
                            .formatted(customerId)))
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
                      "customerId": "%s",
                      "institution": "Filter Test Bank CI",
                      "accountNumber": "8880004444",
                      "principal": 2000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(customerId)))
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
                      "customerId": "%s",
                      "institution": "No Basis Bank",
                      "accountNumber": "9990001111",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
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
                      "customerId": "%s",
                      "institution": "Invalid Basis Bank",
                      "accountNumber": "9990002222",
                      "principal": 1000.00,
                      "interestRate": 0.0500,
                      "depositDate": "2026-03-01",
                      "investmentBasis": "INVALID_VALUE"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isBadRequest());
  }
}
