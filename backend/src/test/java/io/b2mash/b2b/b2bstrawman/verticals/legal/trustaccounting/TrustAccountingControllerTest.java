package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountingControllerTest {

  private static final String API_KEY = "test-api-key";
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
    syncMember(ORG_ID, "user_trust_ctrl_owner", "trust_ctrl@test.com", "Trust Ctrl Owner", "owner");
    syncMember(
        ORG_ID,
        "user_trust_ctrl_member",
        "trust_ctrl_member@test.com",
        "Trust Ctrl Member",
        "member");

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
                .with(ownerJwt())
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
                .with(ownerJwt())
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
        .perform(get("/api/trust-accounts").with(ownerJwt()))
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
                    .with(ownerJwt())
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
        .perform(get("/api/trust-accounts/" + id).with(ownerJwt()))
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
                    .with(ownerJwt())
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
                .with(ownerJwt())
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
                    .with(ownerJwt())
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
        .perform(post("/api/trust-accounts/" + id + "/close").with(ownerJwt()))
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
                    .with(ownerJwt())
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
                .with(ownerJwt())
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
                .with(memberJwt())
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

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_trust_ctrl_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_trust_ctrl_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
