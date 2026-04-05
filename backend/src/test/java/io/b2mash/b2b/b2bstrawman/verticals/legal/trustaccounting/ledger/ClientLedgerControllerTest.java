package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerControllerTest {
  private static final String ORG_ID = "org_client_ledger_ctrl_test";

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
                      "customerId": "%s",
                      "amount": 25000.00,
                      "reference": "DEP-LEDGER-SETUP",
                      "description": "Setup deposit for ledger tests",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());
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
        .andExpect(jsonPath("$.content[0].customerId").value(customerId))
        .andExpect(jsonPath("$.content[0].customerName").value("Ledger Test Customer"))
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
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.customerName").value("Ledger Test Customer"))
        .andExpect(jsonPath("$.balance").value(25000.00))
        .andExpect(jsonPath("$.totalDeposits").value(25000.00));
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
                      "customerId": "%s",
                      "amount": 5000.00,
                      "reference": "DEP-CLOSE-GUARD",
                      "description": "Deposit for close guard test",
                      "transactionDate": "2026-03-01"
                    }
                    """
                        .formatted(customerId)))
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
}
