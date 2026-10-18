package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code applicable_work_types} entries are stored in the canonical work-type form. */
class FieldGroupApplicableWorkTypesTest {

  @Test
  void entriesAreNormalisedAndBlankEntriesDropped() {
    var group = new FieldGroup(EntityType.PROJECT, "Scoped", "scoped");

    group.setApplicableWorkTypes(Arrays.asList(" conveyancing ", "Personal Injury", "  ", null));

    assertThat(group.getApplicableWorkTypes()).containsExactly("CONVEYANCING", "PERSONAL_INJURY");
  }

  @Test
  void canonicalEntriesAreUnchangedAndNullStaysNull() {
    var group = new FieldGroup(EntityType.PROJECT, "Scoped", "scoped");

    group.setApplicableWorkTypes(List.of("CONVEYANCING"));
    assertThat(group.getApplicableWorkTypes()).containsExactly("CONVEYANCING");

    group.setApplicableWorkTypes(null);
    assertThat(group.getApplicableWorkTypes()).isNull();
  }
}
