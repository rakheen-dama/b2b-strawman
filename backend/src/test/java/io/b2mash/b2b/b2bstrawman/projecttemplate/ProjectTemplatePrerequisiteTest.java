package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTemplatePrerequisiteTest {
  private static final String ORG_ID = "org_tmpl_prereq_243b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Prereq 243B Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_tpq243_owner", "tpq243_owner@test.com", "Owner", "owner"));
    memberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_tpq243_member",
                "tpq243_member@test.com",
                "Member",
                "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // --- Task 243.12: Template required fields tests ---

  @Test
  void updateRequiredFields_asAdmin_succeeds() throws Exception {
    final UUID[] ids = createTemplateAndField("update_required_admin");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(templateId.toString()));

    // Verify stored in DB
    final UUID[] storedIds = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var t = templateRepository.findById(templateId).orElseThrow();
                      storedIds[0] =
                          t.getRequiredCustomerFieldIds().isEmpty()
                              ? null
                              : t.getRequiredCustomerFieldIds().get(0);
                    }));
    assertThat(storedIds[0]).isEqualTo(fieldId);
  }

  @Test
  void updateRequiredFields_asMember_returns403() throws Exception {
    final UUID[] ids = createTemplateAndField("update_required_member");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_tpq243_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getPrerequisiteCheck_fieldsComplete_returnsPassed() throws Exception {
    final UUID[] ids = createTemplateAndField("prereq_check_pass");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    // Assign the field as required for this template
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isOk());

    // Create an ACTIVE customer WITH the field filled
    final UUID[] custId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var c =
                          TestCustomerFactory.createActiveCustomer(
                              "Complete Customer",
                              "complete_" + UUID.randomUUID() + "@test.com",
                              ownerMemberId);
                      c.setCustomFields(Map.of("prereq_check_pass_field", "filled-value"));
                      c = customerRepository.saveAndFlush(c);
                      custId[0] = c.getId();
                    }));

    mockMvc
        .perform(
            get("/api/project-templates/" + templateId + "/prerequisite-check")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_tpq243_member"))
                .param("customerId", custId[0].toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true))
        .andExpect(jsonPath("$.violations").isEmpty());
  }

  @Test
  void getPrerequisiteCheck_fieldsMissing_returnsViolations() throws Exception {
    final UUID[] ids = createTemplateAndField("prereq_check_fail");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    // Assign the field as required for this template
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isOk());

    // Create a customer WITHOUT the field filled
    final UUID[] custId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var c =
                          TestCustomerFactory.createActiveCustomer(
                              "Incomplete Customer",
                              "incomplete_" + UUID.randomUUID() + "@test.com",
                              ownerMemberId);
                      c = customerRepository.saveAndFlush(c);
                      custId[0] = c.getId();
                    }));

    mockMvc
        .perform(
            get("/api/project-templates/" + templateId + "/prerequisite-check")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_tpq243_member"))
                .param("customerId", custId[0].toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(false))
        .andExpect(jsonPath("$.violations").isNotEmpty())
        .andExpect(jsonPath("$.violations[0].code").value("MISSING_FIELD"))
        .andExpect(jsonPath("$.violations[0].fieldSlug").value("prereq_check_fail_field"));
  }

  // --- Task 243.13: Project creation gating tests ---

  @Test
  void createProjectFromTemplate_prerequisitesMet_succeeds() throws Exception {
    final UUID[] ids = createTemplateAndField("creation_gating_pass");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    // Set required field on template
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isOk());

    // Customer with field filled
    final UUID[] custId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var c =
                          TestCustomerFactory.createActiveCustomer(
                              "Gate Pass Customer",
                              "gate_pass_" + UUID.randomUUID() + "@test.com",
                              ownerMemberId);
                      c.setCustomFields(Map.of("creation_gating_pass_field", "some-value"));
                      c = customerRepository.saveAndFlush(c);
                      custId[0] = c.getId();
                    }));

    mockMvc
        .perform(
            post("/api/project-templates/" + templateId + "/instantiate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Gated Project OK",
                      "customerId": "%s"
                    }
                    """
                        .formatted(custId[0])))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Gated Project OK"));
  }

  @Test
  void createProjectFromTemplate_prerequisitesNotMet_returns422() throws Exception {
    final UUID[] ids = createTemplateAndField("creation_gating_fail");
    UUID templateId = ids[0];
    UUID fieldId = ids[1];

    // Set required field on template
    mockMvc
        .perform(
            put("/api/project-templates/" + templateId + "/required-customer-fields")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "fieldDefinitionIds": ["%s"] }
                    """
                        .formatted(fieldId)))
        .andExpect(status().isOk());

    // Customer WITHOUT the field filled
    final UUID[] custId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var c =
                          TestCustomerFactory.createActiveCustomer(
                              "Gate Fail Customer",
                              "gate_fail_" + UUID.randomUUID() + "@test.com",
                              ownerMemberId);
                      c = customerRepository.saveAndFlush(c);
                      custId[0] = c.getId();
                    }));

    mockMvc
        .perform(
            post("/api/project-templates/" + templateId + "/instantiate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tpq243_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Gated Project Blocked",
                      "customerId": "%s"
                    }
                    """
                        .formatted(custId[0])))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.title").value("Prerequisites not met"))
        .andExpect(jsonPath("$.violations").isNotEmpty());
  }

  // --- Helpers ---

  private UUID[] createTemplateAndField(String uniqueSuffix) {
    final UUID[] result = new UUID[2];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var template =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Template " + uniqueSuffix,
                                  "{customer}",
                                  null,
                                  true,
                                  "MANUAL",
                                  null,
                                  ownerMemberId));
                      result[0] = template.getId();

                      var fd =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Field " + uniqueSuffix,
                              uniqueSuffix + "_field",
                              FieldType.TEXT);
                      fd = fieldDefinitionRepository.saveAndFlush(fd);
                      result[1] = fd.getId();
                    }));
    return result;
  }
}
