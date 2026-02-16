package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.atomic.AtomicInteger;
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
class ChecklistInstanceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cli_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private final AtomicInteger customerCounter = new AtomicInteger(0);
  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CLI CRUD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_cli_owner", "cli_owner@test.com", "CLI Owner", "owner");

    // Create a template with items (including one with requiresDocument)
    var templateResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "CLI Instance Test Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {
                              "name": "Step 1",
                              "description": "First step",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Step 2 - Doc Required",
                              "description": "Upload a document",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": true,
                              "requiredDocumentLabel": "Identity Document"
                            },
                            {
                              "name": "Optional Step",
                              "description": "Optional item",
                              "sortOrder": 3,
                              "required": false,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    templateId = JsonPath.read(templateResult.getResponse().getContentAsString(), "$.template.id");
  }

  /**
   * Creates a unique customer per test to avoid unique constraint on (tenant, customer, template).
   */
  private String createUniqueCustomer() throws Exception {
    int n = customerCounter.incrementAndGet();
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "CLI Customer %d", "email": "cli-%d@test.com"}
                        """
                            .formatted(n, n)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void shouldInstantiateChecklistForCustomer() throws Exception {
    String customerId = createUniqueCustomer();

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.instance.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.instance.templateName").value("CLI Instance Test Template"))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].name").value("Step 1"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[1].requiresDocument").value(true))
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.instance.id");
    assertThat(instanceId).isNotNull();
  }

  @Test
  void shouldListInstancesForCustomer() throws Exception {
    String customerId = createUniqueCustomer();

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"templateId": "%s"}
                    """
                        .formatted(templateId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/customers/" + customerId + "/checklists").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void shouldGetInstanceWithItems() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.instance.id");

    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.id").value(instanceId))
        .andExpect(jsonPath("$.items.length()").value(3));
  }

  @Test
  void shouldCompleteItemWithNotes() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String itemId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[0].id");

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Completed initial consultation"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.notes").value("Completed initial consultation"));
  }

  @Test
  void shouldReopenCompletedItem() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String itemId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[0].id");

    // Complete first
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Reopen
    mockMvc
        .perform(put("/api/checklist-items/" + itemId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.notes").isEmpty());
  }

  @Test
  void shouldRejectCompleteRequiresDocumentWithoutDocumentId() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String docItemId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[1].id");

    mockMvc
        .perform(
            put("/api/checklist-items/" + docItemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "No document"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldSkipOptionalItem() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String optionalItemId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[2].id");

    mockMvc
        .perform(
            put("/api/checklist-items/" + optionalItemId + "/skip")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Not applicable for this customer"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"));
  }

  @Test
  void shouldRejectSkipRequiredItem() throws Exception {
    String customerId = createUniqueCustomer();

    var createResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"templateId": "%s"}
                        """
                            .formatted(templateId)))
            .andExpect(status().isCreated())
            .andReturn();

    String requiredItemId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.items[0].id");

    mockMvc
        .perform(
            put("/api/checklist-items/" + requiredItemId + "/skip")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"reason": "Trying to skip required"}
                    """))
        .andExpect(status().isConflict());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cli_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
