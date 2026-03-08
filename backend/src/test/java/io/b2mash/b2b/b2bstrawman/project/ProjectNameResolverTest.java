package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectNameResolverTest {

  private final ProjectNameResolver resolver = new ProjectNameResolver();

  @Test
  void nullPatternReturnsOriginalName() {
    String result = resolver.resolve(null, "My Project", null, null);
    assertThat(result).isEqualTo("My Project");
  }

  @Test
  void blankPatternReturnsOriginalName() {
    String result = resolver.resolve("  ", "My Project", null, null);
    assertThat(result).isEqualTo("My Project");
  }

  @Test
  void emptyPatternReturnsOriginalName() {
    String result = resolver.resolve("", "My Project", null, null);
    assertThat(result).isEqualTo("My Project");
  }

  @Test
  void patternWithNameOnly() {
    String result = resolver.resolve("{name}", "Tax Return", null, null);
    assertThat(result).isEqualTo("Tax Return");
  }

  @Test
  void patternWithCustomerName() {
    String result = resolver.resolve("{customer.name} - {name}", "Tax Return", null, "Acme Corp");
    assertThat(result).isEqualTo("Acme Corp - Tax Return");
  }

  @Test
  void patternWithCustomFieldReferences() {
    Map<String, Object> fields = Map.of("reference_number", "REF-001", "year", 2026);
    String result =
        resolver.resolve("{reference_number} - {name} ({year})", "Tax Return", fields, null);
    assertThat(result).isEqualTo("REF-001 - Tax Return (2026)");
  }

  @Test
  void patternWithAllPlaceholders() {
    Map<String, Object> fields = Map.of("reference_number", "REF-042");
    String result =
        resolver.resolve(
            "{reference_number} - {customer.name} - {name}", "Tax Return", fields, "Acme Corp");
    assertThat(result).isEqualTo("REF-042 - Acme Corp - Tax Return");
  }

  @Test
  void patternWithMissingCustomerNameCleansTrailingSeparator() {
    Map<String, Object> fields = Map.of("reference_number", "REF-001");
    String result =
        resolver.resolve(
            "{reference_number} - {customer.name} - {name}", "Tax Return", fields, null);
    // {customer.name} becomes empty -> "REF-001 -  - Tax Return"
    // The resolve method replaces with empty string, trailing separator cleanup handles end of
    // string
    assertThat(result).isEqualTo("REF-001 -  - Tax Return");
  }

  @Test
  void patternWithMissingValuesAtEnd() {
    String result = resolver.resolve("{name} - {customer.name}", "Tax Return", null, null);
    // {customer.name} becomes "" -> "Tax Return - " -> trailing separator cleaned
    assertThat(result).isEqualTo("Tax Return");
  }

  @Test
  void patternWithNullProjectName() {
    String result = resolver.resolve("{name} - {customer.name}", null, null, "Acme Corp");
    // {name} becomes "" -> " - Acme Corp"
    assertThat(result).contains("Acme Corp");
  }

  @Test
  void patternWithNullCustomFieldValue() {
    Map<String, Object> fields = new java.util.HashMap<>();
    fields.put("reference_number", null);
    String result = resolver.resolve("{reference_number} - {name}", "Tax Return", fields, null);
    // {reference_number} becomes "" -> " - Tax Return"
    assertThat(result).contains("Tax Return");
  }

  @Test
  void literalTextPassedThrough() {
    String result = resolver.resolve("Project: {name}", "Alpha", null, null);
    assertThat(result).isEqualTo("Project: Alpha");
  }
}
