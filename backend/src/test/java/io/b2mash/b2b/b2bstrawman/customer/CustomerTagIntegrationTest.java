package io.b2mash.b2b.b2bstrawman.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerTagIntegrationTest {
  private static final String ORG_ID = "org_cust_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String tagId1;
  private String tagId2;
  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Tag Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_ct_owner", "ct_owner@test.com", "CT Owner", "owner");

    // Create tags
    tagId1 =
        TestEntityHelper.createTag(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"), "Cust Premium", "#EF4444");
    tagId2 =
        TestEntityHelper.createTag(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"),
            "Cust Enterprise",
            "#3B82F6");

    // Create customer
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"),
            "Tag Test Customer",
            "tag_cust@test.com");
  }

  @Test
  void shouldSetTagsOnCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s"]}
                    """
                        .formatted(tagId1)))
        .andExpect(status().isOk());

    // Get tags
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Cust Premium"));
  }

  @Test
  void shouldFilterCustomersByTags() throws Exception {
    // Create a second customer
    String customerId2 =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"),
            "Untagged Customer",
            "untagged_cust@test.com");

    // Tag only the first customer with both tags
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk());

    // Filter by single tag slug
    mockMvc
        .perform(
            get("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"))
                .param("tags", "cust_premium"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Customer')]").exists())
        .andExpect(jsonPath("$[?(@.name == 'Untagged Customer')]").doesNotExist());

    // Filter by both tag slugs (AND logic)
    mockMvc
        .perform(
            get("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ct_owner"))
                .param("tags", "cust_premium,cust_enterprise"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Customer')]").exists());
  }

  // --- Helpers ---

}
