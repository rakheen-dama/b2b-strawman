package io.b2mash.b2b.b2bstrawman.crm;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

/**
 * Tenant-isolation gate (MANDATORY). Verifies that the pure schema-per-tenant boundary keeps deals
 * created in tenant A invisible to tenant B — {@code findById} under B's schema returns empty (404)
 * and B's list excludes A's deals.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealTenantIsolationTest {

  private static final String ORG_A_ID = "org_deal_iso_a";
  private static final String ORG_B_ID = "org_deal_iso_b";
  private static final String OWNER_A = "user_deal_iso_a_owner";
  private static final String OWNER_B = "user_deal_iso_b_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String dealIdA;
  private String dealNumberA;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_A_ID, "Deal Iso Org A", null);
    provisioningService.provisionTenant(ORG_B_ID, "Deal Iso Org B", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_A_ID, OWNER_A, "deal_iso_a@test.com", "Owner A", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_B_ID, OWNER_B, "deal_iso_b@test.com", "Owner B", "owner");

    String customerA =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_A_ID, OWNER_A), "Tenant A Co", "a@test.com");
    var result =
        mockMvc
            .perform(
                post("/api/deals")
                    .with(TestJwtFactory.ownerJwt(ORG_A_ID, OWNER_A))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId":"%s","title":"Tenant A Secret Deal"}
                        """
                            .formatted(customerA)))
            .andExpect(status().isCreated())
            .andReturn();
    dealIdA = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    dealNumberA = JsonPath.read(result.getResponse().getContentAsString(), "$.dealNumber");
  }

  @Test
  void dealVisibleFromOwningTenantButNotFromOtherTenant() throws Exception {
    // Visible in A
    mockMvc
        .perform(get("/api/deals/" + dealIdA).with(TestJwtFactory.ownerJwt(ORG_A_ID, OWNER_A)))
        .andExpect(status().isOk());

    // Not visible in B → 404 (schema boundary)
    mockMvc
        .perform(get("/api/deals/" + dealIdA).with(TestJwtFactory.ownerJwt(ORG_B_ID, OWNER_B)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }

  @Test
  void tenantBListExcludesTenantADeals() throws Exception {
    mockMvc
        .perform(get("/api/deals").with(TestJwtFactory.ownerJwt(ORG_B_ID, OWNER_B)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[*].id", everyItem(not(dealIdA))))
        .andExpect(jsonPath("$.content[*].dealNumber", everyItem(not(dealNumberA))));
  }
}
