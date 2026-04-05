package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocxFieldValidatorTest {

  private static final String ORG_ID = "org_field_validator_test";
  private static final String SCHEMA_NAME = SchemaNameGenerator.generateSchemaName(ORG_ID);

  @Autowired private DocxFieldValidator validator;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Field Validator Test Org", null);
  }

  @Test
  void validateFields_mixedValidUnknown_returnsCorrectStatuses() {
    List<String> fieldPaths = List.of("customer.name", "project.name", "nonexistent.field");

    // Run validation within tenant scope so FieldDefinitionRepository can resolve custom fields
    var results =
        ScopedValue.where(RequestScopes.TENANT_ID, SCHEMA_NAME)
            .call(() -> validator.validateFields(fieldPaths, TemplateEntityType.PROJECT));

    assertThat(results).hasSize(3);

    var customerName = results.get(0);
    assertThat(customerName.path()).isEqualTo("customer.name");
    assertThat(customerName.status()).isEqualTo("VALID");
    assertThat(customerName.label()).isEqualTo("Customer Name");

    var projectName = results.get(1);
    assertThat(projectName.path()).isEqualTo("project.name");
    assertThat(projectName.status()).isEqualTo("VALID");
    assertThat(projectName.label()).isEqualTo("Project Name");

    var unknown = results.get(2);
    assertThat(unknown.path()).isEqualTo("nonexistent.field");
    assertThat(unknown.status()).isEqualTo("UNKNOWN");
    assertThat(unknown.label()).isNull();
  }
}
