package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroup;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMember;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
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
class PrerequisiteControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_prereq_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldGroupMemberRepository fieldGroupMemberRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String customerId;
  private String fieldDefinitionId;
  private UUID autoApplyGroupId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Prereq Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember("user_prc_owner", "prc_owner@test.com", "PRC Owner", "owner");
    syncMember("user_prc_admin", "prc_admin@test.com", "PRC Admin", "admin");
    syncMember("user_prc_member", "prc_member@test.com", "PRC Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberIdOwner =
        UUID.fromString(
            syncMember("user_prc_owner2", "prc_owner2@test.com", "PRC Owner2", "owner"));

    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Prereq Test Customer",
                          "email": "prereq-test@test.com"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(customerResult.getResponse().getContentAsString(), "$.id");

    // Create a field definition via API and set requiredForContexts via PATCH
    var fdResult =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Tax Reference",
                          "slug": "tax_reference_prc",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 1
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    fieldDefinitionId = JsonPath.read(fdResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            patch("/api/field-definitions/" + fieldDefinitionId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requiredForContexts": ["INVOICE_GENERATION"]
                    }
                    """))
        .andExpect(status().isOk());

    // Create an auto-apply field group with a member for intake tests
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
                          "Intake Test Field",
                          "intake_test_field_prc",
                          FieldType.TEXT);
                  fd.setRequiredForContexts(new ArrayList<>(List.of("LIFECYCLE_ACTIVATION")));
                  fd = fieldDefinitionRepository.save(fd);

                  var group =
                      new FieldGroup(EntityType.CUSTOMER, "Intake Group PRC", "intake_group_prc");
                  group.setAutoApply(true);
                  group.setSortOrder(1);
                  group = fieldGroupRepository.save(group);
                  autoApplyGroupId = group.getId();

                  var member = new FieldGroupMember(group.getId(), fd.getId(), 1);
                  fieldGroupMemberRepository.save(member);
                }));
  }

  // --- 241.11: Prerequisite controller integration tests ---

  @Test
  void shouldCheckPrerequisitesForContext_passed() throws Exception {
    // The customer has no custom fields set, but there are no required fields for
    // LIFECYCLE_ACTIVATION context on our test customer. Create a scenario with no required fields.
    // Use PROJECT_CREATION context which has no required fields configured.
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "PROJECT_CREATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true))
        .andExpect(jsonPath("$.context").value("PROJECT_CREATION"))
        .andExpect(jsonPath("$.violations").isEmpty());
  }

  @Test
  void shouldCheckPrerequisitesForContext_failed() throws Exception {
    // The tax_reference_prc field is required for INVOICE_GENERATION but customer has no value set
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(false))
        .andExpect(jsonPath("$.context").value("INVOICE_GENERATION"))
        .andExpect(jsonPath("$.violations").isNotEmpty())
        .andExpect(
            jsonPath("$.violations[?(@.fieldSlug == 'tax_reference_prc')].code")
                .value("MISSING_FIELD"));
  }

  @Test
  void shouldCheckPrerequisitesForContext_multipleViolations() throws Exception {
    // Create a second required field for the same context
    var fd2Result =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "VAT Number",
                          "slug": "vat_number_prc",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 2
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String fd2Id = JsonPath.read(fd2Result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            patch("/api/field-definitions/" + fd2Id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requiredForContexts": ["INVOICE_GENERATION"]
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(false))
        .andExpect(
            jsonPath("$.violations.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
  }

  @Test
  void shouldCheckPrerequisitesForContext_invalidContext_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVALID_CONTEXT")
                .param("entityType", "CUSTOMER")
                .param("entityId", customerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldCheckPrerequisitesForContext_entityNotFound_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldCheckPrerequisitesForContext_requiresAuthentication_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", customerId))
        .andExpect(status().isUnauthorized());
  }

  // --- 241.12: Intake endpoint integration tests ---

  @Test
  void shouldReturnIntakeFieldGroups() throws Exception {
    mockMvc
        .perform(
            get("/api/field-definitions/intake").with(ownerJwt()).param("entityType", "CUSTOMER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups[?(@.slug == 'intake_group_prc')]").exists())
        .andExpect(
            jsonPath("$.groups[?(@.slug == 'intake_group_prc')].fields[0].slug")
                .value("intake_test_field_prc"));
  }

  @Test
  void shouldExcludeNonAutoApplyGroups() throws Exception {
    // Create a non-auto-apply group
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
                          "Manual Field PRC",
                          "manual_field_prc",
                          FieldType.TEXT);
                  fd = fieldDefinitionRepository.save(fd);

                  var group =
                      new FieldGroup(EntityType.CUSTOMER, "Manual Group PRC", "manual_group_prc");
                  group.setAutoApply(false);
                  group.setSortOrder(2);
                  group = fieldGroupRepository.save(group);

                  var member = new FieldGroupMember(group.getId(), fd.getId(), 1);
                  fieldGroupMemberRepository.save(member);
                }));

    mockMvc
        .perform(
            get("/api/field-definitions/intake").with(ownerJwt()).param("entityType", "CUSTOMER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[?(@.slug == 'manual_group_prc')]").doesNotExist());
  }

  @Test
  void shouldReturnEmptyWhenNoAutoApplyGroups() throws Exception {
    // INVOICE entity type has no auto-apply groups configured
    mockMvc
        .perform(
            get("/api/field-definitions/intake").with(ownerJwt()).param("entityType", "INVOICE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups").isEmpty());
  }

  // --- 241.13: Field definition PATCH context tests ---

  @Test
  void shouldPatchRequiredForContexts() throws Exception {
    var fdResult =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Patch Test Field",
                          "slug": "patch_test_field_prc",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 10
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(fdResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            patch("/api/field-definitions/" + id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requiredForContexts": ["LIFECYCLE_ACTIVATION", "INVOICE_GENERATION"]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requiredForContexts").isArray())
        .andExpect(jsonPath("$.requiredForContexts.length()").value(2))
        .andExpect(jsonPath("$.requiredForContexts[0]").value("LIFECYCLE_ACTIVATION"))
        .andExpect(jsonPath("$.requiredForContexts[1]").value("INVOICE_GENERATION"));
  }

  @Test
  void shouldRejectInvalidContextName() throws Exception {
    mockMvc
        .perform(
            patch("/api/field-definitions/" + fieldDefinitionId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requiredForContexts": ["INVALID_CONTEXT_NAME"]
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRequireAdminRole() throws Exception {
    mockMvc
        .perform(
            patch("/api/field-definitions/" + fieldDefinitionId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "requiredForContexts": ["LIFECYCLE_ACTIVATION"]
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  // --- 241.14: Customer creation with fields tests ---

  @Test
  void shouldCreateCustomerWithCustomFields() throws Exception {
    // First create a field definition so the slug is known
    var fdResult =
        mockMvc
            .perform(
                post("/api/field-definitions")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Company Reg Number",
                          "slug": "company_reg_prc",
                          "fieldType": "TEXT",
                          "required": false,
                          "sortOrder": 20
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Custom Fields Customer",
                      "email": "custom-fields@test.com",
                      "customFields": {
                        "company_reg_prc": "REG-12345"
                      }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customFields.company_reg_prc").value("REG-12345"));
  }

  @Test
  void shouldRejectUnknownCustomFieldSlug() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Unknown Slug Customer",
                      "email": "unknown-slug@test.com",
                      "customFields": {
                        "nonexistent_slug_prc": "some value"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Helper methods ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
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
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_prc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_prc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
