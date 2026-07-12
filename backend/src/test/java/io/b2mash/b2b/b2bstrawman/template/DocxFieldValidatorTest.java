package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test: all three field paths resolve against the static {@link
 * VariableMetadataRegistry} groups, so the custom-field repository is mocked to return no
 * definitions. Custom-field merge behaviour is covered by {@code VariableMetadataEndpointTest}.
 */
class DocxFieldValidatorTest {

  private final FieldDefinitionRepository fieldDefinitionRepository =
      mock(FieldDefinitionRepository.class);
  private final DocxFieldValidator validator =
      new DocxFieldValidator(new VariableMetadataRegistry(fieldDefinitionRepository));

  @Test
  void validateFields_mixedValidUnknown_returnsCorrectStatuses() {
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(any()))
        .thenReturn(List.of());

    List<String> fieldPaths = List.of("customer.name", "project.name", "nonexistent.field");

    var results = validator.validateFields(fieldPaths, TemplateEntityType.PROJECT);

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
