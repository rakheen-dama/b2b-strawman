package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.ArrayList;
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
class FieldDefinitionContextTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_fd_context_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Autowired private FieldDefinitionService fieldDefinitionService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FD Context Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_fdc_owner", "fdc_owner@test.com", "FDC Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void findRequiredForContext_returnsFieldsForContext() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "ID Number", "fica_id_number_ctx1", FieldType.TEXT);
                  fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
                  fieldDefinitionRepository.save(fd);

                  var results =
                      fieldDefinitionRepository.findRequiredForContext(
                          "CUSTOMER", "[\"LIFECYCLE_ACTIVATION\"]");
                  assertThat(results).anyMatch(r -> r.getSlug().equals("fica_id_number_ctx1"));
                }));
  }

  @Test
  void findRequiredForContext_excludesInactiveFields() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER,
                          "Inactive Field",
                          "inactive_ctx_field",
                          FieldType.TEXT);
                  fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
                  fd.deactivate();
                  fieldDefinitionRepository.save(fd);

                  var results =
                      fieldDefinitionRepository.findRequiredForContext(
                          "CUSTOMER", "[\"LIFECYCLE_ACTIVATION\"]");
                  assertThat(results).noneMatch(r -> r.getSlug().equals("inactive_ctx_field"));
                }));
  }

  @Test
  void findRequiredForContext_excludesFieldsForOtherContexts() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER,
                          "Invoice Field",
                          "invoice_only_field",
                          FieldType.TEXT);
                  fd.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
                  fieldDefinitionRepository.save(fd);

                  var results =
                      fieldDefinitionRepository.findRequiredForContext(
                          "CUSTOMER", "[\"LIFECYCLE_ACTIVATION\"]");
                  assertThat(results).noneMatch(r -> r.getSlug().equals("invoice_only_field"));
                }));
  }

  @Test
  void findRequiredForContext_multipleContexts_returnedForEach() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER,
                          "Multi Context",
                          "multi_context_field",
                          FieldType.TEXT);
                  fd.setRequiredForContexts(
                      new ArrayList<>(List.of("LIFECYCLE_ACTIVATION", "INVOICE_GENERATION")));
                  fieldDefinitionRepository.save(fd);

                  var activation =
                      fieldDefinitionRepository.findRequiredForContext(
                          "CUSTOMER", "[\"LIFECYCLE_ACTIVATION\"]");
                  var invoice =
                      fieldDefinitionRepository.findRequiredForContext(
                          "CUSTOMER", "[\"INVOICE_GENERATION\"]");

                  assertThat(activation).anyMatch(r -> r.getSlug().equals("multi_context_field"));
                  assertThat(invoice).anyMatch(r -> r.getSlug().equals("multi_context_field"));
                }));
  }

  @Test
  void getRequiredFieldsForContext_delegatesToRepository() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER,
                          "Service Field",
                          "service_ctx_field",
                          FieldType.TEXT);
                  fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
                  fieldDefinitionRepository.save(fd);

                  var results =
                      fieldDefinitionService.getRequiredFieldsForContext(
                          EntityType.CUSTOMER,
                          io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext
                              .LIFECYCLE_ACTIVATION);
                  assertThat(results).anyMatch(r -> r.getSlug().equals("service_ctx_field"));
                }));
  }

  @Test
  void getIntakeFields_returnsGroupsWithFields() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create field definitions
                  var fd1 =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "Intake Field 1", "intake_field_1", FieldType.TEXT);
                  fd1 = fieldDefinitionRepository.save(fd1);

                  var fd2 =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "Intake Field 2", "intake_field_2", FieldType.TEXT);
                  fd2 = fieldDefinitionRepository.save(fd2);

                  // Create auto-apply group
                  var group = new FieldGroup(EntityType.CUSTOMER, "Intake Group", "intake_group");
                  group.setAutoApply(true);
                  group.setSortOrder(1);
                  group = fieldGroupRepository.save(group);

                  // Add members
                  var member1 = new FieldGroupMember(group.getId(), fd1.getId(), 1);
                  fieldGroupMemberRepository.save(member1);
                  var member2 = new FieldGroupMember(group.getId(), fd2.getId(), 2);
                  fieldGroupMemberRepository.save(member2);

                  var intakeFields = fieldDefinitionService.getIntakeFields(EntityType.CUSTOMER);
                  assertThat(intakeFields).anyMatch(g -> g.slug().equals("intake_group"));
                  var intakeGroup =
                      intakeFields.stream()
                          .filter(g -> g.slug().equals("intake_group"))
                          .findFirst()
                          .orElseThrow();
                  assertThat(intakeGroup.fields()).hasSize(2);
                  assertThat(intakeGroup.fields().get(0).getSlug()).isEqualTo("intake_field_1");
                  assertThat(intakeGroup.fields().get(1).getSlug()).isEqualTo("intake_field_2");
                }));
  }

  @Test
  void getIntakeFields_nonAutoApplyGroupsExcluded() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "Non Auto Field", "non_auto_field", FieldType.TEXT);
                  fd = fieldDefinitionRepository.save(fd);

                  // Create non-auto-apply group
                  var group = new FieldGroup(EntityType.CUSTOMER, "Manual Group", "manual_group");
                  group.setAutoApply(false);
                  group.setSortOrder(1);
                  group = fieldGroupRepository.save(group);

                  var member = new FieldGroupMember(group.getId(), fd.getId(), 1);
                  fieldGroupMemberRepository.save(member);

                  var intakeFields = fieldDefinitionService.getIntakeFields(EntityType.CUSTOMER);
                  assertThat(intakeFields).noneMatch(g -> g.slug().equals("manual_group"));
                }));
  }

  @Test
  void createCustomer_rejectsUnknownFieldSlug_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Test Unknown Slug Customer",
                      "email": "unknown-slug-test@test.com",
                      "customFields": {
                        "nonexistent_field_slug": "some value"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Unknown custom field"));
  }

  // --- Helper methods ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_fdc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
