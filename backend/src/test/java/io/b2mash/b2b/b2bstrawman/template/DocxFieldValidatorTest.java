package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocxFieldValidatorTest {

  private static final String ORG_ID = "org_field_validator_test";
  private static final String SCHEMA_NAME = SchemaNameGenerator.generateSchemaName(ORG_ID);

  @Autowired private DocxFieldValidator validator;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Field Validator Test Org");
  }

  @Test
  void validateFields_mixedValidUnknown_returnsCorrectStatuses() {
    List<String> fieldPaths = List.of("customer.name", "project.name", "nonexistent.field");

    // Run validation within tenant scope so FieldDefinitionRepository can resolve custom fields
    var results =
        ScopedValue.where(RequestScopes.TENANT_ID, SCHEMA_NAME)
            .call(() -> validator.validateFields(fieldPaths, TemplateEntityType.PROJECT));

    assertThat(results).hasSize(3);

    Map<String, Object> customerName = results.get(0);
    assertThat(customerName.get("path")).isEqualTo("customer.name");
    assertThat(customerName.get("status")).isEqualTo("VALID");
    assertThat(customerName.get("label")).isEqualTo("Customer Name");

    Map<String, Object> projectName = results.get(1);
    assertThat(projectName.get("path")).isEqualTo("project.name");
    assertThat(projectName.get("status")).isEqualTo("VALID");
    assertThat(projectName.get("label")).isEqualTo("Project Name");

    Map<String, Object> unknown = results.get(2);
    assertThat(unknown.get("path")).isEqualTo("nonexistent.field");
    assertThat(unknown.get("status")).isEqualTo("UNKNOWN");
    assertThat(unknown.get("label")).isNull();
  }
}
