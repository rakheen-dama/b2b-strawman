package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistDependencyTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ci_dep_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CI Dependency Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_ci_dep_owner", "ci_dep_owner@test.com", "CI Dep Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create template: Step A (required), Step B (required, depends on A), Step C (optional)
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Dep Test Template",
                          "description": "Template with dependencies",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 1,
                          "items": [
                            {
                              "name": "Step A",
                              "description": "First step",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Step B",
                              "description": "Depends on A",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Step C",
                              "description": "Optional step",
                              "sortOrder": 3,
                              "required": false,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    templateId = JsonPath.read(json, "$.template.id");
    String itemAId = JsonPath.read(json, "$.items[0].id");
    String itemBId = JsonPath.read(json, "$.items[1].id");

    // Update template to add dependency: B depends on A
    mockMvc
        .perform(
            put("/api/checklist-templates/" + templateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Dep Test Template",
                      "description": "Template with dependencies",
                      "autoInstantiate": false,
                      "sortOrder": 1,
                      "items": [
                        {
                          "id": "%s",
                          "name": "Step A",
                          "description": "First step",
                          "sortOrder": 1,
                          "required": true,
                          "requiresDocument": false
                        },
                        {
                          "id": "%s",
                          "name": "Step B",
                          "description": "Depends on A",
                          "sortOrder": 2,
                          "required": true,
                          "requiresDocument": false,
                          "dependsOnItemId": "%s"
                        },
                        {
                          "name": "Step C",
                          "description": "Optional step",
                          "sortOrder": 3,
                          "required": false,
                          "requiresDocument": false
                        }
                      ]
                    }
                    """
                        .formatted(itemAId, itemBId, itemAId)))
        .andExpect(status().isOk());
  }

  /** Helper: creates a customer and instantiates the dependency template, returns item map. */
  private record InstanceSetup(String instanceId, String itemAId, String itemBId, String itemCId) {}

  private InstanceSetup createInstanceForTest(String suffix) throws Exception {
    UUID custId =
        createCustomer("Dep Cust " + suffix, "dep_" + suffix + "_" + UUID.randomUUID() + "@t.com");

    var instResult =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.id");

    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    List<Map<String, Object>> items =
        JsonPath.read(itemsResult.getResponse().getContentAsString(), "$.items");

    String aId = null, bId = null, cId = null;
    for (var item : items) {
      switch ((String) item.get("name")) {
        case "Step A" -> aId = (String) item.get("id");
        case "Step B" -> bId = (String) item.get("id");
        case "Step C" -> cId = (String) item.get("id");
      }
    }
    return new InstanceSetup(instanceId, aId, bId, cId);
  }

  @Test
  void shouldBlockItemWhenDependencyNotCompleted() throws Exception {
    var setup = createInstanceForTest("block");

    // Try to complete B without completing A first — should fail
    mockMvc
        .perform(
            put("/api/checklist-items/" + setup.itemBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Trying to skip ahead\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dependency")));
  }

  @Test
  void shouldAllowCompletionAfterDependencyCompleted() throws Exception {
    var setup = createInstanceForTest("allow");

    // Complete A first
    mockMvc
        .perform(
            put("/api/checklist-items/" + setup.itemAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Step A done\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Now complete B — should succeed
    mockMvc
        .perform(
            put("/api/checklist-items/" + setup.itemBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Step B done\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void shouldRejectSkipOfRequiredItem() throws Exception {
    var setup = createInstanceForTest("rejskip");

    mockMvc
        .perform(
            put("/api/checklist-items/" + setup.itemAId + "/skip")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Want to skip\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldAllowSkipOfOptionalItem() throws Exception {
    var setup = createInstanceForTest("optskip");

    mockMvc
        .perform(
            put("/api/checklist-items/" + setup.itemCId + "/skip")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Not needed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"))
        .andExpect(jsonPath("$.notes").value("Not needed"));
  }

  // --- Helpers ---

  private UUID createCustomer(String name, String email) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var customer =
                          new Customer(name, email, "+1-555-0001", "ID-001", "Test", memberIdOwner);
                      customer = customerRepository.save(customer);
                      return customer.getId();
                    }));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ci_dep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
