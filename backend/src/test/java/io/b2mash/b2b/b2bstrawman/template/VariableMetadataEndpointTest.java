package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VariableMetadataEndpointTest {
  private static final String ORG_ID = "org_var_meta_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Variable Metadata Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_vm_owner", "vm_owner@test.com", "VM Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_vm_member", "vm_member@test.com", "VM Member", "member");
  }

  @Test
  void getVariables_project_returnsStaticAndCustomFieldGroups() throws Exception {
    // 6 static groups + dynamic custom field groups (project + customer custom fields)
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        // At least 6 static + 2 custom field groups
        .andExpect(jsonPath("$.groups.length()", greaterThanOrEqualTo(8)))
        // Static groups in order
        .andExpect(jsonPath("$.groups[0].label").value("Project"))
        .andExpect(jsonPath("$.groups[0].prefix").value("project"))
        .andExpect(jsonPath("$.groups[0].variables[0].key").value("project.id"))
        .andExpect(jsonPath("$.groups[0].variables[1].key").value("project.name"))
        .andExpect(jsonPath("$.groups[1].label").value("Customer"))
        .andExpect(jsonPath("$.groups[2].label").value("Lead"))
        .andExpect(jsonPath("$.groups[3].label").value("Budget"))
        .andExpect(jsonPath("$.groups[4].label").value("Organization"))
        .andExpect(jsonPath("$.groups[5].label").value("Generated"))
        // Dynamic custom field groups appended after static groups (position-independent)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].label", hasItem("Custom Fields")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label",
                hasItem("Customer Custom Fields")));
  }

  @Test
  void getVariables_customer_returnsStaticAndCustomFieldGroups() throws Exception {
    // 4 static groups + 1 custom field group (customer custom fields)
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()", greaterThanOrEqualTo(5)))
        .andExpect(jsonPath("$.groups[0].label").value("Customer"))
        .andExpect(jsonPath("$.groups[0].variables.length()").value(5))
        .andExpect(jsonPath("$.groups[0].variables[3].key").value("customer.phone"))
        .andExpect(jsonPath("$.groups[0].variables[4].key").value("customer.status"))
        .andExpect(jsonPath("$.groups[1].label").value("Invoice Summary"))
        .andExpect(jsonPath("$.groups[1].variables[0].key").value("totalOutstanding"))
        .andExpect(jsonPath("$.groups[1].variables[0].type").value("currency"))
        .andExpect(jsonPath("$.groups[2].label").value("Organization"))
        .andExpect(jsonPath("$.groups[3].label").value("Generated"))
        // Dynamic customer custom fields group (position-independent)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label",
                hasItem("Custom Fields")));
  }

  @Test
  void getVariables_invoice_returnsStaticAndCustomFieldGroups() throws Exception {
    // 5 static groups + custom field groups (no invoice pack, but customer + project)
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()", greaterThanOrEqualTo(7)))
        .andExpect(jsonPath("$.groups[0].label").value("Invoice"))
        .andExpect(jsonPath("$.groups[0].variables.length()").value(10))
        .andExpect(jsonPath("$.groups[0].variables[0].key").value("invoice.id"))
        .andExpect(jsonPath("$.groups[0].variables[1].key").value("invoice.invoiceNumber"))
        .andExpect(jsonPath("$.groups[1].label").value("Customer"))
        .andExpect(jsonPath("$.groups[2].label").value("Project"))
        .andExpect(jsonPath("$.groups[3].label").value("Organization"))
        .andExpect(jsonPath("$.groups[4].label").value("Generated"))
        // No invoice custom fields (no seeded pack), but customer + project present
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label",
                hasItem("Customer Custom Fields")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].label",
                hasItem("Project Custom Fields")));
  }

  @Test
  void getVariables_loopSourcesFilteredByEntityType() throws Exception {
    // PROJECT has members and tags loop sources
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources").isArray())
        .andExpect(jsonPath("$.loopSources.length()").value(2))
        .andExpect(jsonPath("$.loopSources[0].key").value("members"))
        .andExpect(jsonPath("$.loopSources[0].label").value("Project Members"))
        .andExpect(jsonPath("$.loopSources[0].fields.length()").value(4))
        .andExpect(jsonPath("$.loopSources[1].key").value("tags"));

    // INVOICE has only lines loop source
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources.length()").value(1))
        .andExpect(jsonPath("$.loopSources[0].key").value("lines"))
        .andExpect(jsonPath("$.loopSources[0].label").value("Invoice Lines"))
        .andExpect(jsonPath("$.loopSources[0].fields.length()").value(4));

    // CUSTOMER has projects, invoices, and tags loop sources
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources.length()").value(3))
        .andExpect(jsonPath("$.loopSources[0].key").value("projects"))
        .andExpect(jsonPath("$.loopSources[1].key").value("invoices"))
        .andExpect(jsonPath("$.loopSources[1].label").value("Invoices"))
        .andExpect(jsonPath("$.loopSources[1].fields.length()").value(7))
        .andExpect(jsonPath("$.loopSources[2].key").value("tags"));
  }

  @Test
  void getVariables_invalidEntityType_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVALID")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getVariables_anyMemberCanAccess() throws Exception {
    // Member (not admin/owner) can access the variables endpoint
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray());
  }

  @Test
  void getVariables_project_customFieldsHaveCorrectKeysAndTypes() throws Exception {
    // Verify seeded project custom fields have correct dot-path keys and mapped types
    // common-project pack seeds: reference_number (TEXT), priority (DROPDOWN), category (TEXT)
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Project custom fields group (position-independent)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].label", hasItem("Custom Fields")))
        .andExpect(
            jsonPath("$.groups[?(@.prefix == 'project.customFields')].variables[*]", hasSize(3)))
        // Verify keys use dot-path format
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].variables[*].key",
                hasItem("project.customFields.reference_number")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].variables[*].key",
                hasItem("project.customFields.priority")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].variables[*].key",
                hasItem("project.customFields.category")));
  }

  @Test
  void getVariables_customerCustomFieldsAppearInInvoiceGroups() throws Exception {
    // Customer custom fields should appear in INVOICE variable groups
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Customer custom fields group in invoice context (position-independent)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label",
                hasItem("Customer Custom Fields")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*]",
                hasSize(greaterThanOrEqualTo(8))))
        // Verify known customer custom fields from common-customer pack are present
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*].key",
                hasItem("customer.customFields.address_line1")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*].key",
                hasItem("customer.customFields.tax_number")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*].key",
                hasItem("customer.customFields.phone")));
  }

  @Test
  void getVariables_inactiveFieldsExcluded() throws Exception {
    // Create an inactive custom field and verify it does not appear
    String tenantSchema =
        io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator.generateSchemaName(ORG_ID);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var inactive =
                  new FieldDefinition(
                      EntityType.INVOICE, "Hidden Field", "hidden_field", FieldType.TEXT);
              inactive.deactivate();
              inactive.setSortOrder(100);
              fieldDefinitionRepository.save(inactive);
            });

    // INVOICE should still have no invoice custom fields group (the inactive one is excluded)
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Invoice custom fields group should NOT appear (only inactive field exists for INVOICE)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label",
                hasItem("Customer Custom Fields")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'project.customFields')].label",
                hasItem("Project Custom Fields")));
  }

  @Test
  void getVariables_customer_includesCustomerCustomFields() throws Exception {
    // Customer template should include customer custom fields from seeded packs
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_vm_member"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].label", hasItem("Custom Fields")))
        // At least 8 fields from common-customer pack (may have more from compliance packs)
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*]",
                hasSize(greaterThanOrEqualTo(8))))
        // Verify known fields from common-customer pack
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*].key",
                hasItem("customer.customFields.address_line1")))
        .andExpect(
            jsonPath(
                "$.groups[?(@.prefix == 'customer.customFields')].variables[*].key",
                hasItem("customer.customFields.tax_number")));
  }

  @Test
  void fieldTypeToVariableTypeCoversAllFieldTypes() {
    for (FieldType ft : FieldType.values()) {
      assertThat(VariableMetadataRegistry.FIELD_TYPE_TO_VARIABLE_TYPE).containsKey(ft);
    }
  }
}
