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
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
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
class ChecklistCompletionLifecycleTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ci_compl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String templateId; // 2 required items, 1 optional
  private String template2Id; // second template for multi-checklist test

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CI Completion Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_ci_compl_owner", "ci_compl_owner@test.com", "Compl Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create template 1: 2 required, 1 optional
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Completion Test Template",
                          "description": "For testing completion logic",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 1,
                          "items": [
                            {
                              "name": "Required Step 1",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Required Step 2",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Optional Step",
                              "sortOrder": 3,
                              "required": false,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.template.id");

    // Create template 2 (for multi-checklist test)
    var result2 =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Completion Secondary Template",
                          "description": "Second template for multi-checklist test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 2,
                          "items": [
                            {
                              "name": "Secondary Required Step",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    template2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.template.id");
  }

  @Test
  void shouldCompleteInstanceWhenAllRequiredItemsCompleted() throws Exception {
    UUID custId =
        createCustomerWithStatus(
            "Compl Cust 1", "compl1_" + UUID.randomUUID() + "@test.com", "ONBOARDING");

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
        mockMvc.perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt())).andReturn();
    List<Map<String, Object>> items =
        JsonPath.read(itemsResult.getResponse().getContentAsString(), "$.items");

    // Complete both required items
    for (var item : items) {
      if (Boolean.TRUE.equals(item.get("required"))) {
        mockMvc
            .perform(
                put("/api/checklist-items/" + item.get("id") + "/complete")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"notes\": \"Done\"}"))
            .andExpect(status().isOk());
      }
    }

    // Verify instance is COMPLETED
    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.status").value("COMPLETED"))
        .andExpect(jsonPath("$.instance.completedAt").isNotEmpty());
  }

  @Test
  void shouldTransitionCustomerToActiveWhenAllChecklistsCompleted() throws Exception {
    UUID custId =
        createCustomerWithStatus(
            "Compl Cust 2", "compl2_" + UUID.randomUUID() + "@test.com", "ONBOARDING");

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
        mockMvc.perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt())).andReturn();
    List<Map<String, Object>> items =
        JsonPath.read(itemsResult.getResponse().getContentAsString(), "$.items");

    // Complete all required items
    for (var item : items) {
      if (Boolean.TRUE.equals(item.get("required"))) {
        mockMvc
            .perform(
                put("/api/checklist-items/" + item.get("id") + "/complete")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"notes\": \"Done\"}"))
            .andExpect(status().isOk());
      }
    }

    // Verify customer transitioned to ACTIVE
    Customer customer =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx -> customerRepository.findOneById(custId).orElse(null)));

    assertThat(customer).isNotNull();
    assertThat(customer.getLifecycleStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void shouldNotTransitionIfAnyChecklistIncomplete() throws Exception {
    UUID custId =
        createCustomerWithStatus(
            "Compl Cust 3", "compl3_" + UUID.randomUUID() + "@test.com", "ONBOARDING");

    // Create two checklist instances (different templates to avoid unique constraint)
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": \"" + templateId + "\"}"))
        .andExpect(status().isCreated());

    var inst2Result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + template2Id + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    // Only complete the second instance's required items
    String inst2Id = JsonPath.read(inst2Result.getResponse().getContentAsString(), "$.id");
    var items2Result =
        mockMvc.perform(get("/api/checklist-instances/" + inst2Id).with(ownerJwt())).andReturn();
    List<Map<String, Object>> items2 =
        JsonPath.read(items2Result.getResponse().getContentAsString(), "$.items");

    for (var item : items2) {
      if (Boolean.TRUE.equals(item.get("required"))) {
        mockMvc
            .perform(
                put("/api/checklist-items/" + item.get("id") + "/complete")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"notes\": \"Done\"}"))
            .andExpect(status().isOk());
      }
    }

    // Customer should still be ONBOARDING (first instance is incomplete)
    Customer customer =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx -> customerRepository.findOneById(custId).orElse(null)));

    assertThat(customer).isNotNull();
    assertThat(customer.getLifecycleStatus()).isEqualTo("ONBOARDING");
  }

  // --- Helpers ---

  private UUID createCustomerWithStatus(String name, String email, String lifecycleStatus) {
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
                      if (!"PROSPECT".equals(lifecycleStatus)) {
                        customer.transitionLifecycle(
                            lifecycleStatus, memberIdOwner, Instant.now(), null);
                        customer = customerRepository.save(customer);
                      }
                      return customer.getId();
                    }));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ci_compl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
