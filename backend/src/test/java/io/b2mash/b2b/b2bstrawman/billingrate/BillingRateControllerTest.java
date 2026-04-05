package io.b2mash.b2b.b2bstrawman.billingrate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BillingRateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_ctrl_test";
  private static final String ORG_ID_B = "org_billing_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String projectId;
  private String customerId;

  // Tenant B for isolation tests
  private String memberIdOwnerB;

  // Stored rate IDs for subsequent tests
  private String createdRateId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    // Provision tenant A
    provisioningService.provisionTenant(ORG_ID, "Billing Ctrl Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_brc_owner", "brc_owner@test.com", "BRC Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_brc_admin", "brc_admin@test.com", "BRC Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_brc_member", "brc_member@test.com", "BRC Member", "member");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Billing Rate Test Project", "description": "For billing rate tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Billing Rate Test Customer", "email": "brc_customer@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId =
        JsonPath.read(customerResult.getResponse().getContentAsString(), "$.id").toString();

    // Transition customer to ACTIVE (lifecycle guard blocks PROSPECT from linking)
    transitionCustomerToActive(customerId);

    // Link customer to project (for resolve cascade testing)
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isCreated());

    // Provision tenant B for isolation tests
    provisioningService.provisionTenant(ORG_ID_B, "Billing Ctrl Test Org B", null);
    memberIdOwnerB =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID_B, "user_brc_owner_b", "brc_owner_b@test.com", "BRC Owner B", "owner");
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void postCreatesBillingRateAndReturns201() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-rates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "USD",
                          "hourlyRate": 150.00,
                          "effectiveFrom": "2025-01-01"
                        }
                        """
                            .formatted(memberIdOwner)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.memberId").value(memberIdOwner))
            .andExpect(jsonPath("$.memberName").value("BRC Owner"))
            .andExpect(jsonPath("$.scope").value("MEMBER_DEFAULT"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.hourlyRate").value(150.00))
            .andExpect(jsonPath("$.effectiveFrom").value("2025-01-01"))
            .andExpect(jsonPath("$.projectId").isEmpty())
            .andExpect(jsonPath("$.customerId").isEmpty())
            .andReturn();

    createdRateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void getListsAllBillingRates() throws Exception {
    mockMvc
        .perform(get("/api/billing-rates").with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberName").value("BRC Owner"));
  }

  @Test
  @Order(3)
  void getListsFiltersByMemberId() throws Exception {
    // Create a rate for a different member
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_brc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "EUR",
                      "hourlyRate": 120.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    // Filter by the owner member — should only return 1
    mockMvc
        .perform(
            get("/api/billing-rates")
                .param("memberId", memberIdOwner)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberId").value(memberIdOwner));
  }

  @Test
  @Order(4)
  void putUpdatesBillingRate() throws Exception {
    mockMvc
        .perform(
            put("/api/billing-rates/" + createdRateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "GBP",
                      "hourlyRate": 175.00,
                      "effectiveFrom": "2025-01-01",
                      "effectiveTo": "2025-12-31"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.hourlyRate").value(175.00))
        .andExpect(jsonPath("$.effectiveTo").value("2025-12-31"));
  }

  @Test
  @Order(5)
  void deleteRemovesBillingRate() throws Exception {
    // Create a rate to delete
    var result =
        mockMvc
            .perform(
                post("/api/billing-rates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "CAD",
                          "hourlyRate": 100.00,
                          "effectiveFrom": "2026-01-01"
                        }
                        """
                            .formatted(memberIdMember)))
            .andExpect(status().isCreated())
            .andReturn();

    var rateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            delete("/api/billing-rates/" + rateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isNoContent());
  }

  // --- Resolve Tests ---

  @Test
  @Order(6)
  void resolveReturnsMemberDefaultRate() throws Exception {
    mockMvc
        .perform(
            get("/api/billing-rates/resolve")
                .param("memberId", memberIdOwner)
                .param("projectId", projectId)
                .param("date", "2025-06-15")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hourlyRate").value(175.00))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.source").value("MEMBER_DEFAULT"))
        .andExpect(jsonPath("$.billingRateId").value(createdRateId));
  }

  @Test
  @Order(7)
  void resolveReturnsNullWhenNoRateFound() throws Exception {
    mockMvc
        .perform(
            get("/api/billing-rates/resolve")
                .param("memberId", memberIdMember)
                .param("projectId", projectId)
                .param("date", "2025-06-15")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hourlyRate").isEmpty())
        .andExpect(jsonPath("$.currency").isEmpty())
        .andExpect(jsonPath("$.source").isEmpty())
        .andExpect(jsonPath("$.billingRateId").isEmpty());
  }

  @Test
  @Order(8)
  void resolveReturnsProjectOverrideWhenExists() throws Exception {
    // Create a project override for the owner on the test project
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "currency": "USD",
                      "hourlyRate": 200.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdOwner, projectId)))
        .andExpect(status().isCreated());

    // Resolve should now return the project override instead of member default
    mockMvc
        .perform(
            get("/api/billing-rates/resolve")
                .param("memberId", memberIdOwner)
                .param("projectId", projectId)
                .param("date", "2025-06-15")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hourlyRate").value(200.00))
        .andExpect(jsonPath("$.source").value("PROJECT_OVERRIDE"));
  }

  // --- Validation Tests ---

  @Test
  @Order(9)
  void postWithInvalidDataReturns400() throws Exception {
    // Missing required fields
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "US",
                      "hourlyRate": -10
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(10)
  void postOverlappingRateReturns409() throws Exception {
    // Try to create another member default for the same member in the same date range
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyRate": 160.00,
                      "effectiveFrom": "2025-06-01"
                    }
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isConflict());
  }

  // --- Permission Tests ---

  @Test
  @Order(11)
  void memberCannotCreateBillingRateForOtherMembers() throws Exception {
    // Regular member should be forbidden from creating rates (not admin/owner/project lead)
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_brc_member", "member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyRate": 100.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdOwner)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(12)
  void adminCanCreateBillingRate() throws Exception {
    // Admin should be able to create rates
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_brc_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "CHF",
                      "hourlyRate": 130.00,
                      "effectiveFrom": "2026-01-01"
                    }
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());
  }

  // --- Tenant Isolation Tests ---

  @Test
  @Order(13)
  void billingRateInTenantAIsInvisibleInTenantB() throws Exception {
    // List rates in tenant B — should be empty (no rates created there)
    mockMvc
        .perform(
            get("/api/billing-rates").with(TestJwtFactory.ownerJwt(ORG_ID_B, "user_brc_owner_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @Order(14)
  void resolveInTenantBReturnsNullForTenantAMember() throws Exception {
    // Resolve in tenant B with tenant A's member — should not find any rate
    mockMvc
        .perform(
            get("/api/billing-rates/resolve")
                .param("memberId", memberIdOwner)
                .param("projectId", projectId)
                .param("date", "2025-06-15")
                .with(TestJwtFactory.ownerJwt(ORG_ID_B, "user_brc_owner_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hourlyRate").isEmpty());
  }

  @Test
  @Order(15)
  void postWithBothProjectAndCustomerReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "customerId": "%s",
                      "currency": "USD",
                      "hourlyRate": 100.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdOwner, projectId, customerId)))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_brc_owner"));
  }
}
