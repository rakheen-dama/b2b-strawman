package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountingControllerTest {
  private static final String ORG_ID = "org_trust_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Controller Test Org", null).schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_trust_ctrl_owner",
        "trust_ctrl@test.com",
        "Trust Ctrl Owner",
        "owner");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_trust_ctrl_member",
        "trust_ctrl_member@test.com",
        "Trust Ctrl Member",
        "member");
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_trust_ctrl_admin",
        "trust_ctrl_admin@test.com",
        "Trust Ctrl Admin",
        "admin");

    // Enable the trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  // --- Task 439.10: CRUD integration tests ---

  @Test
  void postTrustAccount_returns201WithCreatedAccount() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountName": "Main Trust Account",
                      "bankName": "First National Bank",
                      "branchCode": "250655",
                      "accountNumber": "62000000001",
                      "accountType": "GENERAL",
                      "isPrimary": false,
                      "requireDualApproval": false,
                      "openedDate": "2026-01-15",
                      "notes": "Primary trust account"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountName").value("Main Trust Account"))
        .andExpect(jsonPath("$.bankName").value("First National Bank"))
        .andExpect(jsonPath("$.branchCode").value("250655"))
        .andExpect(jsonPath("$.accountNumber").value("62000000001"))
        .andExpect(jsonPath("$.accountType").value("GENERAL"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void getTrustAccounts_returns200WithList() throws Exception {
    // Create an account first
    mockMvc
        .perform(
            post("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountName": "List Test Account",
                      "bankName": "ABSA Bank",
                      "branchCode": "632005",
                      "accountNumber": "90000000001",
                      "isPrimary": false
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  void getTrustAccountById_returns200WithDetail() throws Exception {
    // Create an account
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Detail Test Account",
                          "bankName": "Standard Bank",
                          "branchCode": "051001",
                          "accountNumber": "70000000001",
                          "isPrimary": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            get("/api/trust-accounts/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.accountName").value("Detail Test Account"))
        .andExpect(jsonPath("$.bankName").value("Standard Bank"));
  }

  @Test
  void putTrustAccount_returns200WithUpdatedAccount() throws Exception {
    // Create an account
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Update Test Account",
                          "bankName": "Nedbank",
                          "branchCode": "198765",
                          "accountNumber": "80000000001",
                          "isPrimary": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/trust-accounts/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountName": "Updated Trust Account",
                      "notes": "Updated notes"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.accountName").value("Updated Trust Account"))
        .andExpect(jsonPath("$.notes").value("Updated notes"));
  }

  // --- Task 439.11: Lifecycle + rates integration tests ---

  @Test
  void postCloseTrustAccount_returns200WhenBalanceIsZero() throws Exception {
    // Create an account (no client ledger cards = zero balance)
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Close Test Account",
                          "bankName": "Capitec Bank",
                          "branchCode": "470010",
                          "accountNumber": "99000000001",
                          "isPrimary": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/trust-accounts/" + id + "/close")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.closedDate").isNotEmpty());
  }

  @Test
  void postLpffRate_returns201WithRateDetail() throws Exception {
    // Create an account
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "LPFF Rate Test Account",
                          "bankName": "Investec",
                          "branchCode": "580105",
                          "accountNumber": "88000000001",
                          "isPrimary": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/trust-accounts/" + id + "/lpff-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-04-01",
                      "ratePercent": 0.0725,
                      "lpffSharePercent": 0.0150,
                      "notes": "Q2 2026 rate"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.trustAccountId").value(id))
        .andExpect(jsonPath("$.effectiveFrom").value("2026-04-01"))
        .andExpect(jsonPath("$.notes").value("Q2 2026 rate"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  // --- Task 439.12: Authorization test ---

  @Test
  void postTrustAccount_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_trust_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountName": "Forbidden Account",
                      "bankName": "Forbidden Bank",
                      "branchCode": "000000",
                      "accountNumber": "00000000000",
                      "isPrimary": false
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getLpffRates_returnsCreatedRate() throws Exception {
    // Create an account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "LPFF List Test Account",
                          "bankName": "FNB",
                          "branchCode": "250655",
                          "accountNumber": "77000000001",
                          "isPrimary": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String accountId = JsonPath.read(accountResult.getResponse().getContentAsString(), "$.id");

    // Create an LPFF rate
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/lpff-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-07-01",
                      "ratePercent": 0.0800,
                      "lpffSharePercent": 0.0175,
                      "notes": "Q3 2026 rate"
                    }
                    """))
        .andExpect(status().isCreated());

    // GET the rates and verify the created rate is returned
    mockMvc
        .perform(
            get("/api/trust-accounts/" + accountId + "/lpff-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].trustAccountId").value(accountId))
        .andExpect(jsonPath("$[0].effectiveFrom").value("2026-07-01"))
        .andExpect(jsonPath("$[0].notes").value("Q3 2026 rate"));
  }

  @Test
  void getTrustAccounts_adminWithViewTrust_returns200() throws Exception {
    // Create an account as owner first
    mockMvc
        .perform(
            post("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_trust_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountName": "Admin View Test Account",
                      "bankName": "Nedbank",
                      "branchCode": "198765",
                      "accountNumber": "66000000001",
                      "isPrimary": false
                    }
                    """))
        .andExpect(status().isCreated());

    // Admin role has VIEW_TRUST capability — verify read access succeeds
    mockMvc
        .perform(
            get("/api/trust-accounts")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_trust_ctrl_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }
}
