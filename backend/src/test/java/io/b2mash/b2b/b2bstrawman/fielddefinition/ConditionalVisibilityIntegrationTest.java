package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
class ConditionalVisibilityIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cond_vis_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cond Vis Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_cond_vis_owner",
                "cond_vis_owner@test.com",
                "Cond Vis Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void eq_operator_hides_field_when_value_differs() throws Exception {
    // Create controlling DROPDOWN field
    String controllingId = createDropdownField("Status Type", "status_type_eq", "type_a", "type_b");

    // Create dependent required TEXT field with eq condition (visible when status_type_eq = type_a)
    String dependentId =
        createFieldWithCondition(
            "Dependent EQ", "dependent_eq", true, "status_type_eq", "eq", "\"type_a\"");

    UUID customerId = createCustomer("EQ Test Customer");

    String groupId = createFieldGroup("EQ Group", "eq_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = type_b (not matching eq condition) => dependent hidden => required skipped
    updateCustomerWithFields(
        customerId, "EQ Test Customer", "eq@test.com", "{\"status_type_eq\": \"type_b\"}");
  }

  @Test
  void neq_operator_hides_field_when_value_matches() throws Exception {
    String controllingId = createDropdownField("Status NEQ", "status_neq", "active", "inactive");

    // Dependent is visible when status_neq != "inactive" (hidden when == "inactive")
    String dependentId =
        createFieldWithCondition(
            "Dependent NEQ", "dependent_neq", true, "status_neq", "neq", "\"inactive\"");

    UUID customerId = createCustomer("NEQ Test Customer");

    String groupId = createFieldGroup("NEQ Group", "neq_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = "inactive" => neq("inactive") = false => hidden => required skipped
    updateCustomerWithFields(
        customerId, "NEQ Test Customer", "neq@test.com", "{\"status_neq\": \"inactive\"}");
  }

  @Test
  void in_operator_shows_field_when_value_in_list() throws Exception {
    String controllingId =
        createDropdownField("Category In", "category_in", "alpha", "beta", "gamma");

    // Dependent is visible when category_in is in [alpha, beta]
    String dependentId =
        createFieldWithCondition(
            "Dependent In", "dependent_in", true, "category_in", "in", "[\"alpha\", \"beta\"]");

    UUID customerId = createCustomer("In Test Customer");

    String groupId = createFieldGroup("In Group", "in_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = "alpha" => in [alpha, beta] = true => visible => must provide value
    updateCustomerWithFields(
        customerId,
        "In Test Customer",
        "in@test.com",
        "{\"category_in\": \"alpha\", \"dependent_in\": \"some value\"}");
  }

  @Test
  void hidden_required_field_skips_validation() throws Exception {
    String controllingId = createDropdownField("Toggle Field", "toggle_field", "show", "hide");

    // Dependent is visible only when toggle_field = "show"
    String dependentId =
        createFieldWithCondition(
            "Hidden Required", "hidden_required", true, "toggle_field", "eq", "\"show\"");

    UUID customerId = createCustomer("Hidden Req Customer");

    String groupId = createFieldGroup("Hidden Req Group", "hidden_req_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = "hide" => dependent not visible => required check skipped
    updateCustomerWithFields(
        customerId, "Hidden Req Customer", "hidden@test.com", "{\"toggle_field\": \"hide\"}");
  }

  @Test
  void deactivated_controlling_field_makes_dependent_visible() throws Exception {
    String controllingId = createDropdownField("Deact Control", "deact_control", "yes", "no");

    // Dependent is visible when deact_control = "yes"
    String dependentId =
        createFieldWithCondition(
            "Deact Dependent", "deact_dependent", true, "deact_control", "eq", "\"yes\"");

    // Deactivate the controlling field
    mockMvc
        .perform(delete("/api/field-definitions/" + controllingId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    UUID customerId = createCustomer("Deact Test Customer");

    String groupId = createFieldGroup("Deact Group", "deact_group");
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling deactivated => not in slugToDefinition => dependent always visible
    // Since dependent is required and visible, we must provide a value
    updateCustomerWithFields(
        customerId,
        "Deact Test Customer",
        "deact@test.com",
        "{\"deact_dependent\": \"provided value\"}");
  }

  @Test
  void self_reference_condition_rejected() throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "CUSTOMER",
                      "name": "Self Ref",
                      "slug": "self_ref",
                      "fieldType": "TEXT",
                      "required": false,
                      "sortOrder": 0,
                      "visibilityCondition": {
                        "dependsOnSlug": "self_ref",
                        "operator": "eq",
                        "value": "x"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cross_entity_type_condition_rejected() throws Exception {
    // Create a PROJECT field
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "PROJECT",
                      "name": "Project Only",
                      "slug": "project_only",
                      "fieldType": "TEXT",
                      "required": false,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isCreated());

    // Try to create a CUSTOMER field that depends on the PROJECT field slug
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "CUSTOMER",
                      "name": "Cross Entity Dep",
                      "slug": "cross_entity_dep",
                      "fieldType": "TEXT",
                      "required": false,
                      "sortOrder": 0,
                      "visibilityCondition": {
                        "dependsOnSlug": "project_only",
                        "operator": "eq",
                        "value": "x"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void visible_required_field_without_value_returns_400() throws Exception {
    String controllingId =
        createDropdownField("Vis Req Control", "vis_req_control", "show", "hide");

    // Dependent is visible when vis_req_control = "show", and it is required
    String dependentId =
        createFieldWithCondition(
            "Vis Req Dep", "vis_req_dep", true, "vis_req_control", "eq", "\"show\"");

    UUID customerId = createCustomer("Vis Req Customer");

    String groupId = createFieldGroup("Vis Req Group", "vis_req_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = "show" => dependent is visible => required => no value provided => 400
    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vis Req Customer",
                      "email": "visreq@test.com",
                      "customFields": {"vis_req_control": "show"}
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void hidden_field_with_invalid_type_value_is_still_rejected() throws Exception {
    String controllingId =
        createDropdownField("Type Val Control", "type_val_control", "show", "hide");

    // Create a NUMBER field that is hidden when type_val_control = "hide"
    String dependentId =
        createNumberFieldWithCondition(
            "Type Val Number", "type_val_number", false, "type_val_control", "eq", "\"show\"");

    UUID customerId = createCustomer("Type Val Customer");

    String groupId = createFieldGroup("Type Val Group", "type_val_group");
    addMemberToGroup(groupId, controllingId);
    addMemberToGroup(groupId, dependentId);
    applyFieldGroups(customerId, List.of(groupId));

    // Controlling = "hide" => dependent is hidden, but sending a string for a NUMBER field
    // should still be rejected by type validation
    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Type Val Customer",
                      "email": "typeval@test.com",
                      "customFields": {"type_val_control": "hide", "type_val_number": "not_a_number"}
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Helper methods ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cond_vis_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String createDropdownField(String name, String slug, String... optionValues)
      throws Exception {
    StringBuilder optionsJson = new StringBuilder("[");
    for (int i = 0; i < optionValues.length; i++) {
      if (i > 0) optionsJson.append(",");
      optionsJson.append(
          "{\"label\": \"%s\", \"value\": \"%s\"}".formatted(optionValues[i], optionValues[i]));
    }
    optionsJson.append("]");

    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "%s",
                          "slug": "%s",
                          "fieldType": "DROPDOWN",
                          "required": false,
                          "sortOrder": 0,
                          "options": %s
                        }
                        """
                            .formatted(name, slug, optionsJson.toString())))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createFieldWithCondition(
      String name,
      String slug,
      boolean required,
      String dependsOnSlug,
      String operator,
      String valueJson)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "%s",
                          "slug": "%s",
                          "fieldType": "TEXT",
                          "required": %s,
                          "sortOrder": 0,
                          "visibilityCondition": {
                            "dependsOnSlug": "%s",
                            "operator": "%s",
                            "value": %s
                          }
                        }
                        """
                            .formatted(name, slug, required, dependsOnSlug, operator, valueJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createFieldGroup(String name, String slug) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "%s",
                          "slug": "%s",
                          "sortOrder": 0
                        }
                        """
                            .formatted(name, slug)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addMemberToGroup(String groupId, String fieldDefinitionId) throws Exception {
    mockMvc
        .perform(
            post("/api/field-groups/" + groupId + "/fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fieldDefinitionId": "%s",
                      "sortOrder": 0
                    }
                    """
                        .formatted(fieldDefinitionId)))
        .andExpect(status().isCreated());
  }

  private UUID createCustomer(String name) {
    return runInTenantAndReturn(
        () -> {
          var customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", memberIdOwner);
          return customerRepository.save(customer).getId();
        });
  }

  private void applyFieldGroups(UUID customerId, List<String> groupIds) throws Exception {
    String idsJson =
        "[" + String.join(",", groupIds.stream().map(id -> "\"" + id + "\"").toList()) + "]";
    mockMvc
        .perform(
            put("/api/customers/" + customerId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedFieldGroups\":" + idsJson + "}"))
        .andExpect(status().isOk());
  }

  private void updateCustomerWithFields(
      UUID customerId, String name, String email, String customFieldsJson) throws Exception {
    mockMvc
        .perform(
            put("/api/customers/" + customerId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "%s",
                      "email": "%s",
                      "customFields": %s
                    }
                    """
                        .formatted(name, email, customFieldsJson)))
        .andExpect(status().isOk());
  }

  private <T> T runInTenantAndReturn(java.util.function.Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(() -> transactionTemplate.execute(tx -> action.get()));
  }

  private String createNumberFieldWithCondition(
      String name,
      String slug,
      boolean required,
      String dependsOnSlug,
      String operator,
      String valueJson)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "%s",
                          "slug": "%s",
                          "fieldType": "NUMBER",
                          "required": %s,
                          "sortOrder": 0,
                          "visibilityCondition": {
                            "dependsOnSlug": "%s",
                            "operator": "%s",
                            "value": %s
                          }
                        }
                        """
                            .formatted(name, slug, required, dependsOnSlug, operator, valueJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
