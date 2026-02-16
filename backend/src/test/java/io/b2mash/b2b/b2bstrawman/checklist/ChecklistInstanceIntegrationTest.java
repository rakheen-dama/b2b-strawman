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
class ChecklistInstanceIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ci_crud_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String templateId;
  // Second template for the "list" test (so we can have 2 instances for 1 customer)
  private String template2Id;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CI CRUD Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_ci_crud_owner", "ci_crud_owner@test.com", "CI CRUD Owner", "owner"));
    syncMember("user_ci_crud_member", "ci_crud_member@test.com", "CI CRUD Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create template 1
    var result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "CI Test Onboarding",
                          "description": "Integration test template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 1,
                          "items": [
                            {
                              "name": "Step 1 - Consultation",
                              "description": "Initial call",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            },
                            {
                              "name": "Step 2 - Upload Agreement",
                              "description": "Upload signed agreement",
                              "sortOrder": 2,
                              "required": true,
                              "requiresDocument": true,
                              "requiredDocumentLabel": "Signed Agreement"
                            },
                            {
                              "name": "Step 3 - Optional Feedback",
                              "description": "Optional survey",
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

    // Create template 2 (for list test)
    var result2 =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "CI Test Secondary",
                          "description": "Second template for list test",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 2,
                          "items": [
                            {
                              "name": "Secondary Step 1",
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
  void shouldInstantiateTemplate() throws Exception {
    UUID custId = createCustomer("Inst Cust", "inst_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.templateId").value(templateId))
            .andExpect(jsonPath("$.customerId").value(custId.toString()))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.startedAt").isNotEmpty())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.id").value(instanceId))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].name").value("Step 1 - Consultation"))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.items[1].requiresDocument").value(true))
        .andExpect(jsonPath("$.items[2].required").value(false));
  }

  @Test
  void shouldListInstancesForCustomer() throws Exception {
    UUID custId = createCustomer("List Cust", "list_" + UUID.randomUUID() + "@test.com");

    // Create two instances using different templates (unique constraint: 1 per template+customer)
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": \"" + templateId + "\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/customers/" + custId + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": \"" + template2Id + "\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/customers/" + custId + "/checklists").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void shouldGetInstanceWithItems() throws Exception {
    UUID custId = createCustomer("Get Cust", "get_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].sortOrder").value(1))
        .andExpect(jsonPath("$.items[1].sortOrder").value(2))
        .andExpect(jsonPath("$.items[2].sortOrder").value(3));
  }

  @Test
  void shouldIsolateInstancesBetweenTenants() throws Exception {
    UUID custId = createCustomer("Iso Cust", "iso_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Different org should get 404
    provisioningService.provisionTenant("org_ci_other", "Other Org");
    planSyncService.syncPlan("org_ci_other", "pro-plan");
    syncMemberForOrg(
        "org_ci_other", "user_ci_other_owner", "ci_other@test.com", "Other Owner", "owner");

    var otherOrgJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_ci_other_owner")
                        .claim("o", Map.of("id", "org_ci_other", "rol", "owner")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));

    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(otherOrgJwt))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectCompletionWhenDocumentRequired() throws Exception {
    UUID custId = createCustomer("DocReq Cust", "docreq_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String itemsJson = itemsResult.getResponse().getContentAsString();
    List<Map<String, Object>> items = JsonPath.read(itemsJson, "$.items");
    String docItemId = null;
    for (var item : items) {
      if (Boolean.TRUE.equals(item.get("requiresDocument"))) {
        docItemId = (String) item.get("id");
        break;
      }
    }
    assertThat(docItemId).isNotNull();

    // Try to complete without a document — should fail with 409
    mockMvc
        .perform(
            put("/api/checklist-items/" + docItemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"No doc yet\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldCompleteItemWithoutDocument() throws Exception {
    UUID custId = createCustomer("NoDocComp Cust", "nodoccomp_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String itemsJson = itemsResult.getResponse().getContentAsString();
    List<Map<String, Object>> items = JsonPath.read(itemsJson, "$.items");
    String step1ItemId = null;
    for (var item : items) {
      if ("Step 1 - Consultation".equals(item.get("name"))) {
        step1ItemId = (String) item.get("id");
        break;
      }
    }
    assertThat(step1ItemId).isNotNull();

    mockMvc
        .perform(
            put("/api/checklist-items/" + step1ItemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Consultation done\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.notes").value("Consultation done"));
  }

  @Test
  void shouldReopenCompletedItem() throws Exception {
    UUID custId = createCustomer("Reopen Cust", "reopen_" + UUID.randomUUID() + "@test.com");

    var result =
        mockMvc
            .perform(
                post("/api/customers/" + custId + "/checklists")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + templateId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String itemsJson = itemsResult.getResponse().getContentAsString();
    List<Map<String, Object>> items = JsonPath.read(itemsJson, "$.items");
    String step1ItemId = null;
    for (var item : items) {
      if ("Step 1 - Consultation".equals(item.get("name"))) {
        step1ItemId = (String) item.get("id");
        break;
      }
    }

    // Complete then reopen
    mockMvc
        .perform(
            put("/api/checklist-items/" + step1ItemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Done\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(put("/api/checklist-items/" + step1ItemId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.completedAt").doesNotExist())
        .andExpect(jsonPath("$.notes").doesNotExist());
  }

  @Test
  void shouldAllowMemberToViewInstances() throws Exception {
    UUID custId = createCustomer("View Cust", "view_" + UUID.randomUUID() + "@test.com");

    var memberJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_ci_crud_member")
                        .claim("o", Map.of("id", ORG_ID, "rol", "member")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));

    mockMvc
        .perform(get("/api/customers/" + custId + "/checklists").with(memberJwt))
        .andExpect(status().isOk());
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
        .jwt(j -> j.subject("user_ci_crud_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    return syncMemberForOrg(ORG_ID, clerkUserId, email, name, orgRole);
  }

  private String syncMemberForOrg(
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
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
