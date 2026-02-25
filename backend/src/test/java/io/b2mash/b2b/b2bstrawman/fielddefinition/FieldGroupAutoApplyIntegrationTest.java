package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class FieldGroupAutoApplyIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fg_auto_apply_test";

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
    provisioningService.provisionTenant(ORG_ID, "FG Auto Apply Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_fg_aa_owner", "fg_aa_owner@test.com", "FG AA Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void toggle_autoApply_updates_field_group() throws Exception {
    // Create a field group with autoApply=false
    var createResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Toggle Test Group",
                          "description": "For toggle test",
                          "sortOrder": 1,
                          "autoApply": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.autoApply").value(false))
            .andReturn();

    String groupId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Toggle autoApply to true
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoApply").value(true));

    // Toggle back to false
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoApply").value(false));
  }

  @Test
  void retroactive_apply_adds_group_to_existing_entities() throws Exception {
    // Create a field group with autoApply=false
    var createResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Retroactive Apply Group",
                          "description": "For retroactive apply test",
                          "sortOrder": 2,
                          "autoApply": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a customer entity in the tenant with empty applied_field_groups
    UUID customerId =
        runInTenantAndReturn(
            () -> {
              Customer customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Retro Test Customer", "retro@test.com", memberIdOwner);
              customer.setAppliedFieldGroups(List.of());
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // Toggle autoApply to true — should retroactively apply to the customer
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoApply").value(true));

    // Verify the customer now has the group in its applied_field_groups
    runInTenant(
        () -> {
          Customer updated = customerRepository.findById(customerId).orElseThrow();
          assertThat(updated.getAppliedFieldGroups()).contains(groupUuid);
        });
  }

  @Test
  void retroactive_apply_skips_entities_already_having_group() throws Exception {
    // Create a field group
    var createResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Idempotent Apply Group",
                          "description": "For idempotent test",
                          "sortOrder": 3,
                          "autoApply": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a customer that already has this group in applied_field_groups
    UUID customerId =
        runInTenantAndReturn(
            () -> {
              Customer customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Already Applied Customer", "already@test.com", memberIdOwner);
              customer.setAppliedFieldGroups(List.of(groupUuid));
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // Toggle autoApply to true
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": true}
                    """))
        .andExpect(status().isOk());

    // Verify the customer still has exactly one entry (not duplicated)
    runInTenant(
        () -> {
          Customer updated = customerRepository.findById(customerId).orElseThrow();
          long count =
              updated.getAppliedFieldGroups().stream().filter(id -> id.equals(groupUuid)).count();
          assertThat(count).isEqualTo(1);
        });
  }

  @Test
  void toggle_autoApply_off_does_not_remove_from_entities() throws Exception {
    // Create a field group with autoApply=false
    var createResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "No Removal Group",
                          "description": "For no-removal test",
                          "sortOrder": 4,
                          "autoApply": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a customer with empty applied_field_groups
    UUID customerId =
        runInTenantAndReturn(
            () -> {
              Customer customer =
                  TestCustomerFactory.createActiveCustomer(
                      "No Removal Customer", "noremoval@test.com", memberIdOwner);
              customer.setAppliedFieldGroups(List.of());
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // Toggle autoApply ON (adds group to customer)
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": true}
                    """))
        .andExpect(status().isOk());

    // Toggle autoApply OFF
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": false}
                    """))
        .andExpect(status().isOk());

    // Verify the customer still has the group (toggling off does NOT remove)
    runInTenant(
        () -> {
          Customer updated = customerRepository.findById(customerId).orElseThrow();
          assertThat(updated.getAppliedFieldGroups()).contains(groupUuid);
        });
  }

  @Test
  void patch_endpoint_requires_admin_role() throws Exception {
    // Create a group first as owner
    var createResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Auth Test Group",
                          "description": "For auth test",
                          "sortOrder": 5,
                          "autoApply": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Try to toggle as MEMBER — should get 403
    mockMvc
        .perform(
            patch("/api/field-groups/" + groupId + "/auto-apply")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"autoApply": true}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fg_aa_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fg_aa_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

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
