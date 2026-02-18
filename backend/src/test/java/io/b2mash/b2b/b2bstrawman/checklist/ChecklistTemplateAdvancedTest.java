package io.b2mash.b2b.b2bstrawman.checklist;

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
import java.util.UUID;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChecklistTemplateAdvancedTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_checklist_adv_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Checklist Advanced Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_adv_owner", "adv_owner@test.com", "Adv Owner", "owner");
  }

  @Test
  @Order(1)
  void cloneCreatesOrgCustomCopy() throws Exception {
    // Create a template to clone
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Original Template",
                          "description": "Template to be cloned",
                          "customerType": "ANY",
                          "autoInstantiate": true,
                          "items": [
                            {"name": "Step A", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Step B", "sortOrder": 2, "required": false, "requiresDocument": true, "requiredDocumentLabel": "Proof"}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    templateId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Clone it
    mockMvc
        .perform(post("/api/checklist-templates/" + templateId + "/clone").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("ORG_CUSTOM"))
        .andExpect(jsonPath("$.name").value("Original Template (Custom)"))
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(templateId)));
  }

  @Test
  @Order(2)
  void cloneCopiesItems() throws Exception {
    var cloneResult =
        mockMvc
            .perform(post("/api/checklist-templates/" + templateId + "/clone").with(ownerJwt()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Step A"))
            .andExpect(jsonPath("$.items[1].name").value("Step B"))
            .andExpect(jsonPath("$.items[1].requiredDocumentLabel").value("Proof"))
            .andReturn();

    // Verify original is unmodified
    mockMvc
        .perform(get("/api/checklist-templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Original Template"))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  @Order(3)
  void cloneSlugSuffixed() throws Exception {
    // Create a template with a known slug
    var createResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Slug Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "slug": "my-template",
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String slugTestId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Clone it — slug should be "my-template-custom"
    mockMvc
        .perform(post("/api/checklist-templates/" + slugTestId + "/clone").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("my-template-custom"));
  }

  @Test
  @Order(4)
  void dependencyCycleRejected() throws Exception {
    // Create two items that depend on each other (A -> B, B -> A)
    // We need to use UUIDs. Since the items won't exist yet, we generate them.
    UUID itemAId = UUID.randomUUID();
    UUID itemBId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Cycle Test",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "items": [
                        {"name": "Item A", "sortOrder": 1, "required": true, "requiresDocument": false, "dependsOnItemId": "%s"},
                        {"name": "Item B", "sortOrder": 2, "required": true, "requiresDocument": false, "dependsOnItemId": "%s"}
                      ]
                    }
                    """
                        .formatted(itemBId, itemAId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void dependencyToOtherTemplateRejected() throws Exception {
    // dependsOnItemId pointing to a random UUID not in this template
    UUID randomId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Bad Dep Test",
                      "customerType": "ANY",
                      "autoInstantiate": false,
                      "items": [
                        {"name": "Item X", "sortOrder": 1, "required": true, "requiresDocument": false, "dependsOnItemId": "%s"}
                      ]
                    }
                    """
                        .formatted(randomId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(6)
  void slugAutoIncrementOnConflict() throws Exception {
    // Create first template
    var first =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Conflict Slug",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String firstSlug = JsonPath.read(first.getResponse().getContentAsString(), "$.slug");

    // Create second template with the same name
    var second =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Conflict Slug",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String secondSlug = JsonPath.read(second.getResponse().getContentAsString(), "$.slug");

    org.assertj.core.api.Assertions.assertThat(firstSlug).isEqualTo("conflict-slug");
    org.assertj.core.api.Assertions.assertThat(secondSlug).isEqualTo("conflict-slug-2");
  }

  @Test
  @Order(7)
  void orderingPersisted() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Ordered Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 5,
                          "items": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sortOrder").value(5))
            .andReturn();

    String orderedId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Fetch and verify persistence
    mockMvc
        .perform(get("/api/checklist-templates/" + orderedId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sortOrder").value(5));
  }

  @Test
  @Order(8)
  void itemOrderingPreserved() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Item Order Test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "items": [
                            {"name": "Third", "sortOrder": 30, "required": false, "requiresDocument": false},
                            {"name": "First", "sortOrder": 10, "required": true, "requiresDocument": false},
                            {"name": "Second", "sortOrder": 20, "required": true, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String itemOrderId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Fetch — items should come back ordered by sortOrder ascending
    mockMvc
        .perform(get("/api/checklist-templates/" + itemOrderId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("First"))
        .andExpect(jsonPath("$.items[0].sortOrder").value(10))
        .andExpect(jsonPath("$.items[1].name").value("Second"))
        .andExpect(jsonPath("$.items[1].sortOrder").value(20))
        .andExpect(jsonPath("$.items[2].name").value("Third"))
        .andExpect(jsonPath("$.items[2].sortOrder").value(30));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_adv_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
}
