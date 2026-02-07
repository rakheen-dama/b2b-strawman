package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchemaNameGeneratorTest {

  @Test
  void generatesDeterministicSchemaName() {
    String name1 = SchemaNameGenerator.generateSchemaName("org_abc123");
    String name2 = SchemaNameGenerator.generateSchemaName("org_abc123");
    assertThat(name1).isEqualTo(name2);
  }

  @Test
  void generatesCorrectFormat() {
    String name = SchemaNameGenerator.generateSchemaName("org_test");
    assertThat(name).matches("^tenant_[0-9a-f]{12}$");
  }

  @Test
  void differentOrgIdsProduceDifferentNames() {
    String name1 = SchemaNameGenerator.generateSchemaName("org_aaa");
    String name2 = SchemaNameGenerator.generateSchemaName("org_bbb");
    assertThat(name1).isNotEqualTo(name2);
  }

  @Test
  void rejectsNullOrgId() {
    assertThatThrownBy(() -> SchemaNameGenerator.generateSchemaName(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankOrgId() {
    assertThatThrownBy(() -> SchemaNameGenerator.generateSchemaName("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
