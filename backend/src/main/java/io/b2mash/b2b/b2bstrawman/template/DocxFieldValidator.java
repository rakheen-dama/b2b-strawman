package io.b2mash.b2b.b2bstrawman.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Validates discovered .docx field paths against the {@link VariableMetadataRegistry} for a given
 * entity type. Each field path is checked for existence in the registry and classified as VALID or
 * UNKNOWN.
 */
@Service
public class DocxFieldValidator {

  /** Result of validating a single field path against the variable metadata registry. */
  public record FieldValidationResult(String path, String status, String label) {}

  private final VariableMetadataRegistry registry;

  public DocxFieldValidator(VariableMetadataRegistry registry) {
    this.registry = registry;
  }

  /**
   * Validates a list of field paths against the variable metadata for the given entity type.
   *
   * @param fieldPaths the field paths discovered from a .docx template
   * @param entityType the template entity type to validate against
   * @return list of validation results with path, status ("VALID"/"UNKNOWN"), and label (or null)
   */
  public List<FieldValidationResult> validateFields(
      List<String> fieldPaths, TemplateEntityType entityType) {
    var response = registry.getVariables(entityType);

    // Build lookup map: field path -> VariableInfo
    Map<String, VariableMetadataRegistry.VariableInfo> lookup = new HashMap<>();
    for (var group : response.groups()) {
      for (var variable : group.variables()) {
        lookup.put(variable.key(), variable);
      }
    }

    return fieldPaths.stream()
        .map(
            path -> {
              var info = lookup.get(path);
              if (info != null) {
                return new FieldValidationResult(path, "VALID", info.label());
              } else {
                return new FieldValidationResult(path, "UNKNOWN", null);
              }
            })
        .toList();
  }
}
