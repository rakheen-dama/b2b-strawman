package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class FieldGroupDependencyIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fg_dep_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FG Dep Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_fg_dep_owner", "fg_dep_owner@test.com", "FG Dep Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void apply_group_with_deps_also_applies_dependency_groups() throws Exception {
    // Create group B (the dependency)
    String groupBId = createGroup("Dep Group B", "dep_group_b");

    // Create group A that depends on B
    String groupAId = createGroupWithDeps("Dep Group A", "dep_group_a", List.of(groupBId));

    // Create a customer
    UUID customerId = createCustomer("Dep Test Customer 1");

    // Apply only group A to the customer
    var result =
        mockMvc
            .perform(
                put("/api/customers/" + customerId + "/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"appliedFieldGroups\":[\"" + groupAId + "\"]}"))
            .andExpect(status().isOk())
            .andReturn();

    // Verify the customer has both groups applied
    var customer =
        runInTenantAndReturn(() -> customerRepository.findById(customerId).orElseThrow());
    assertThat(customer.getAppliedFieldGroups())
        .containsExactlyInAnyOrder(UUID.fromString(groupAId), UUID.fromString(groupBId));
  }

  @Test
  void one_level_only_no_cascade() throws Exception {
    // Create group C
    String groupCId = createGroup("Cascade Group C", "cascade_group_c");

    // Create group B that depends on C
    String groupBId = createGroupWithDeps("Cascade Group B", "cascade_group_b", List.of(groupCId));

    // Create group A that depends on B (but NOT C directly)
    String groupAId = createGroupWithDeps("Cascade Group A", "cascade_group_a", List.of(groupBId));

    // Create a customer
    UUID customerId = createCustomer("Cascade Test Customer");

    // Apply only group A
    mockMvc
        .perform(
            put("/api/customers/" + customerId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedFieldGroups\":[\"" + groupAId + "\"]}"))
        .andExpect(status().isOk());

    // Should have A and B but NOT C (one-level only)
    var customer =
        runInTenantAndReturn(() -> customerRepository.findById(customerId).orElseThrow());
    assertThat(customer.getAppliedFieldGroups())
        .containsExactlyInAnyOrder(UUID.fromString(groupAId), UUID.fromString(groupBId));
    assertThat(customer.getAppliedFieldGroups()).doesNotContain(UUID.fromString(groupCId));
  }

  @Test
  void circular_dependency_rejected() throws Exception {
    // Create group X
    String groupXId = createGroup("Circular X", "circular_x");

    // Create group Y that depends on X
    String groupYId = createGroupWithDeps("Circular Y", "circular_y", List.of(groupXId));

    // Try to update X to depend on Y — should be rejected (mutual dependency)
    mockMvc
        .perform(
            put("/api/field-groups/" + groupXId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Circular X",
                      "description": null,
                      "sortOrder": 0,
                      "dependsOn": ["%s"]
                    }
                    """
                        .formatted(groupYId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void self_reference_rejected() throws Exception {
    // Create a group
    String groupId = createGroup("Self Ref Group", "self_ref_group");

    // Try to update it to depend on itself
    mockMvc
        .perform(
            put("/api/field-groups/" + groupId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Self Ref Group",
                      "description": null,
                      "sortOrder": 0,
                      "dependsOn": ["%s"]
                    }
                    """
                        .formatted(groupId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void remove_dependency_group_allowed() throws Exception {
    // Create groups
    String groupBId = createGroup("Removable Dep B", "removable_dep_b");
    String groupAId = createGroupWithDeps("Removable Dep A", "removable_dep_a", List.of(groupBId));

    // Create customer and apply both groups
    UUID customerId = createCustomer("Remove Dep Test Customer");
    mockMvc
        .perform(
            put("/api/customers/" + customerId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedFieldGroups\":[\"" + groupAId + "\",\"" + groupBId + "\"]}"))
        .andExpect(status().isOk());

    // Now remove group B (the dependency) — should be allowed
    mockMvc
        .perform(
            put("/api/customers/" + customerId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedFieldGroups\":[\"" + groupAId + "\"]}"))
        .andExpect(status().isOk());

    // Verify: A is still applied, B is re-added via dependency resolution
    var customer =
        runInTenantAndReturn(() -> customerRepository.findById(customerId).orElseThrow());
    assertThat(customer.getAppliedFieldGroups())
        .containsExactlyInAnyOrder(UUID.fromString(groupAId), UUID.fromString(groupBId));
  }

  // --- Helper methods ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fg_dep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String createGroup(String name, String slug) throws Exception {
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

  private String createGroupWithDeps(String name, String slug, List<String> dependsOnIds)
      throws Exception {
    String depsJson =
        "[" + String.join(",", dependsOnIds.stream().map(id -> "\"" + id + "\"").toList()) + "]";
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
                          "sortOrder": 0,
                          "dependsOn": %s
                        }
                        """
                            .formatted(name, slug, depsJson)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dependsOn").isNotEmpty())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private UUID createCustomer(String name) {
    return runInTenantAndReturn(
        () -> {
          var customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", memberIdOwner);
          return customerRepository.save(customer).getId();
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  private <T> T runInTenantAndReturn(java.util.function.Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(() -> transactionTemplate.execute(tx -> action.get()));
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
