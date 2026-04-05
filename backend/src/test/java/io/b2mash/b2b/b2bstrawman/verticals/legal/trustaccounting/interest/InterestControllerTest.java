package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestControllerTest {

  private static final String ORG_ID = "org_interest_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Interest Controller Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_interest_ctrl_owner",
        "interest_ctrl_owner@test.com",
        "Interest Ctrl Owner",
        "owner");

    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_interest_ctrl_admin",
        "interest_ctrl_admin@test.com",
        "Interest Ctrl Admin",
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

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_ctrl_owner");

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
                          "accountName": "Interest Ctrl Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000200",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    // Create a customer and deposit funds
    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Interest Ctrl Client", "ctrl_client@test.com");

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "amount": 50000.00,
                      "reference": "DEP-CTRL-001",
                      "description": "Test deposit for controller tests",
                      "transactionDate": "2026-01-15"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Create LPFF rate
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Controller test rate"
                    }
                    """))
        .andExpect(status().isCreated());
  }

  // ==========================================================================
  // 445.12 -- Controller Tests (2 tests)
  // ==========================================================================

  @Test
  void postInterestRun_returns201() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_ctrl_owner");

    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/interest-runs")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "periodStart": "2027-06-01",
                          "periodEnd": "2027-06-30"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.trustAccountId").value(trustAccountId.toString()))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();

    assertResultIsNotEmpty(result);
  }

  @Test
  void calculateAndGetDetail_returnsAllocations() throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_interest_ctrl_owner");

    // Create an interest run
    var createResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/interest-runs")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "periodStart": "2027-07-01",
                          "periodEnd": "2027-07-31"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var runId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id").toString();

    // Calculate interest
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/calculate").with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.totalInterest").isNumber());

    // Get run detail with allocations
    mockMvc
        .perform(get("/api/interest-runs/" + runId).with(ownerJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.run.id").value(runId))
        .andExpect(jsonPath("$.run.status").value("DRAFT"))
        .andExpect(jsonPath("$.allocations").isArray())
        .andExpect(jsonPath("$.allocations.length()").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  private void assertResultIsNotEmpty(org.springframework.test.web.servlet.MvcResult result)
      throws Exception {
    var content = result.getResponse().getContentAsString();
    var id = JsonPath.read(content, "$.id");
    assert id != null;
  }
}
