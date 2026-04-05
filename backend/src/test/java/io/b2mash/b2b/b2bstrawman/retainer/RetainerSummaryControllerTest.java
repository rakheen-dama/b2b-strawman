package io.b2mash.b2b.b2bstrawman.retainer;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RetainerSummaryControllerTest {
  private static final String ORG_ID = "org_retainer_summary_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String customerHourBank;
  private String customerFixedFee;
  private String customerNoRetainer;
  private String customerTerminated;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Summary Test Org", null);

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_rst_owner", "rst_owner@test.com", "RST Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_rst_member", "rst_member@test.com", "RST Member", "member");

    // Create customers and transition to ACTIVE (required for retainer creation)
    customerHourBank =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"),
            "HourBank Corp",
            "hourbank@test.com");
    transitionCustomerToActive(customerHourBank);
    customerFixedFee =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"),
            "FixedFee Corp",
            "fixedfee@test.com");
    transitionCustomerToActive(customerFixedFee);
    customerNoRetainer =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"),
            "NoRetainer Corp",
            "noretainer@test.com");
    transitionCustomerToActive(customerNoRetainer);
    customerTerminated =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"),
            "Terminated Corp",
            "terminated@test.com");
    transitionCustomerToActive(customerTerminated);

    // Create HOUR_BANK retainer for customerHourBank
    mockMvc
        .perform(
            post("/api/retainers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Monthly Hour Bank",
                      "type": "HOUR_BANK",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "allocatedHours": 50.00,
                      "periodFee": 25000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                        .formatted(customerHourBank)))
        .andExpect(status().isCreated());

    // Create FIXED_FEE retainer for customerFixedFee
    mockMvc
        .perform(
            post("/api/retainers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Monthly Fixed Fee",
                      "type": "FIXED_FEE",
                      "frequency": "MONTHLY",
                      "startDate": "2026-03-01",
                      "periodFee": 10000.00,
                      "rolloverPolicy": "FORFEIT"
                    }
                    """
                        .formatted(customerFixedFee)))
        .andExpect(status().isCreated());

    // Create and then terminate a retainer for customerTerminated
    var terminateResult =
        mockMvc
            .perform(
                post("/api/retainers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "name": "Terminated Retainer",
                          "type": "HOUR_BANK",
                          "frequency": "MONTHLY",
                          "startDate": "2026-03-01",
                          "allocatedHours": 20.00,
                          "periodFee": 10000.00,
                          "rolloverPolicy": "FORFEIT"
                        }
                        """
                            .formatted(customerTerminated)))
            .andExpect(status().isCreated())
            .andReturn();

    String terminatedRetainerId =
        JsonPath.read(terminateResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/retainers/" + terminatedRetainerId + "/terminate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isOk());
  }

  @Test
  @Order(1)
  void getSummary_activeHourBankRetainer_returns200WithAllFields() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerHourBank + "/retainer-summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true))
        .andExpect(jsonPath("$.agreementId").exists())
        .andExpect(jsonPath("$.agreementName").value("Monthly Hour Bank"))
        .andExpect(jsonPath("$.type").value("HOUR_BANK"))
        .andExpect(jsonPath("$.allocatedHours").value(50.0))
        .andExpect(jsonPath("$.consumedHours").value(0.0))
        .andExpect(jsonPath("$.remainingHours").value(50.0))
        .andExpect(jsonPath("$.percentConsumed").value(0.0))
        .andExpect(jsonPath("$.isOverage").value(false));
  }

  @Test
  @Order(2)
  void getSummary_activeFixedFeeRetainer_returns200WithTypeSpecificFields() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerFixedFee + "/retainer-summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true))
        .andExpect(jsonPath("$.agreementId").exists())
        .andExpect(jsonPath("$.agreementName").value("Monthly Fixed Fee"))
        .andExpect(jsonPath("$.type").value("FIXED_FEE"))
        .andExpect(jsonPath("$.allocatedHours").value(nullValue()))
        .andExpect(jsonPath("$.consumedHours").value(0.0))
        .andExpect(jsonPath("$.remainingHours").value(nullValue()))
        .andExpect(jsonPath("$.percentConsumed").value(nullValue()))
        .andExpect(jsonPath("$.isOverage").value(false));
  }

  @Test
  @Order(3)
  void getSummary_customerWithNoRetainer_returns200WithHasActiveRetainerFalse() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerNoRetainer + "/retainer-summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(false));
  }

  @Test
  @Order(4)
  void getSummary_customerWithTerminatedRetainerOnly_returns200WithHasActiveRetainerFalse()
      throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerTerminated + "/retainer-summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(false));
  }

  @Test
  @Order(5)
  void getSummary_asMember_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerHourBank + "/retainer-summary")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_rst_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActiveRetainer").value(true));
  }

  @Test
  @Order(6)
  void getSummary_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerHourBank + "/retainer-summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(7)
  void getSummary_nonExistentCustomer_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/00000000-0000-0000-0000-000000000099/retainer-summary")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner")))
        .andExpect(status().isNotFound());
  }

  private void transitionCustomerToActive(String custId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, custId, TestJwtFactory.ownerJwt(ORG_ID, "user_rst_owner"));
  }
}
