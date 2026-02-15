package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CustomerTagIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cust_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String tagId1;
  private String tagId2;
  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Tag Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_ct_owner", "ct_owner@test.com", "CT Owner", "owner");

    // Create tags
    tagId1 = createTag("Cust Premium", "#EF4444");
    tagId2 = createTag("Cust Enterprise", "#3B82F6");

    // Create customer
    customerId = createCustomer("Tag Test Customer", "tag_cust@test.com");
  }

  @Test
  void shouldSetTagsOnCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/tags")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void shouldGetTagsForCustomer() throws Exception {
    // Set tags first
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/tags")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s"]}
                    """
                        .formatted(tagId1)))
        .andExpect(status().isOk());

    // Get tags
    mockMvc
        .perform(get("/api/customers/" + customerId + "/tags").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Cust Premium"));
  }

  @Test
  void shouldFilterCustomersByTags() throws Exception {
    // Create a second customer
    String customerId2 = createCustomer("Untagged Customer", "untagged_cust@test.com");

    // Tag only the first customer with both tags
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/tags")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk());

    // Filter by single tag slug
    mockMvc
        .perform(get("/api/customers").with(ownerJwt()).param("tags", "cust_premium"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Customer')]").exists())
        .andExpect(jsonPath("$[?(@.name == 'Untagged Customer')]").doesNotExist());

    // Filter by both tag slugs (AND logic)
    mockMvc
        .perform(
            get("/api/customers").with(ownerJwt()).param("tags", "cust_premium,cust_enterprise"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Customer')]").exists());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ct_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String createTag(String name, String color) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tags")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "color": "%s"}
                        """
                            .formatted(name, color)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
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
        .andExpect(status().isCreated());
  }
}
