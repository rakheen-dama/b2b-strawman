package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerCustomFieldIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_customer_cf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private int emailCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer CF Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_ccf_owner", "ccf_owner@test.com", "Owner", "owner");

    // Create field definitions for CUSTOMER entity type
    createFieldDefinition("Industry", "industry", "TEXT", "CUSTOMER");
    createFieldDefinition("VIP", "vip", "BOOLEAN", "CUSTOMER");
    createFieldDefinition("Website", "website", "URL", "CUSTOMER");
  }

  @Test
  void shouldCreateCustomerWithCustomFields() throws Exception {
    String email = nextEmail();
    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CF Customer",
                      "email": "%s",
                      "customFields": {
                        "industry": "Legal",
                        "vip": true
                      }
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("CF Customer"))
        .andExpect(jsonPath("$.customFields.industry").value("Legal"))
        .andExpect(jsonPath("$.customFields.vip").value(true));
  }

  @Test
  void shouldUpdateCustomerCustomFields() throws Exception {
    String email = nextEmail();
    // Create customer first
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Update CF Customer", "email": "%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();

    String customerId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update with custom fields
    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update CF Customer",
                      "email": "%s",
                      "customFields": {
                        "industry": "Finance",
                        "website": "https://example.com"
                      }
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.industry").value("Finance"))
        .andExpect(jsonPath("$.customFields.website").value("https://example.com"));
  }

  @Test
  void shouldReturn400ForInvalidCustomerCustomField() throws Exception {
    String email = nextEmail();
    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Bad CF Customer",
                      "email": "%s",
                      "customFields": {
                        "website": "not-a-url"
                      }
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFilterCustomersByCustomField() throws Exception {
    String email = nextEmail();
    // Create customer with specific custom field value
    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Filterable Customer",
                      "email": "%s",
                      "customFields": {
                        "industry": "Healthcare"
                      }
                    }
                    """
                        .formatted(email)))
        .andExpect(status().isCreated());

    // Filter by custom field
    mockMvc
        .perform(
            get("/api/customers").with(ownerJwt()).param("customField[industry]", "Healthcare"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customFields.industry == 'Healthcare')]").exists());
  }

  @Test
  void shouldApplyFieldGroupsToCustomer() throws Exception {
    // Create a field group for CUSTOMER
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Customer CF Test Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Create a customer
    String email = nextEmail();
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "FG Customer", "email": "%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();

    String customerId = JsonPath.read(customerResult.getResponse().getContentAsString(), "$.id");

    // Apply field groups
    mockMvc
        .perform(
            put("/api/customers/" + customerId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isOk());
  }

  // --- Helpers ---

  private String nextEmail() {
    return "ccf_customer_" + (++emailCounter) + "@test.com";
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ccf_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private void createFieldDefinition(String name, String slug, String fieldType, String entityType)
      throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "%s",
                      "name": "%s",
                      "fieldType": "%s",
                      "required": false,
                      "sortOrder": 0
                    }
                    """
                        .formatted(entityType, name, fieldType)))
        .andExpect(status().isCreated());
  }
}
