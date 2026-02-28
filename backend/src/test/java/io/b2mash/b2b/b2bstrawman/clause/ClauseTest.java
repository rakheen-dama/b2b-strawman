package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClauseTest {

  private static final Map<String, Object> SAMPLE_BODY =
      Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));

  private static final Map<String, Object> UPDATED_BODY =
      Map.of(
          "type",
          "doc",
          "content",
          List.of(
              Map.of(
                  "type",
                  "paragraph",
                  "content",
                  List.of(Map.of("type", "text", "text", "Updated body text")))));

  private Clause buildClause() {
    return new Clause("Confidentiality", "confidentiality", SAMPLE_BODY, "General");
  }

  @Test
  void constructor_sets_required_fields() {
    var clause = new Clause("Termination", "termination", SAMPLE_BODY, "Legal");

    assertThat(clause.getTitle()).isEqualTo("Termination");
    assertThat(clause.getSlug()).isEqualTo("termination");
    assertThat(clause.getBody()).isEqualTo(SAMPLE_BODY);
    assertThat(clause.getCategory()).isEqualTo("Legal");
  }

  @Test
  void constructor_sets_timestamps_and_defaults() {
    var clause = buildClause();

    assertThat(clause.getSource()).isEqualTo(ClauseSource.CUSTOM);
    assertThat(clause.isActive()).isTrue();
    assertThat(clause.getSortOrder()).isZero();
    assertThat(clause.getDescription()).isNull();
    assertThat(clause.getSourceClauseId()).isNull();
    assertThat(clause.getPackId()).isNull();
    // createdAt/updatedAt are set by @PrePersist, so null before persist
    assertThat(clause.getCreatedAt()).isNull();
    assertThat(clause.getUpdatedAt()).isNull();
  }

  @Test
  void update_modifies_fields_and_updatedAt() {
    var clause = buildClause();
    clause.onPrePersist(); // simulate JPA lifecycle
    var originalUpdatedAt = clause.getUpdatedAt();

    clause.update("Updated Title", "updated-title", "A description", UPDATED_BODY, "Compliance");

    assertThat(clause.getTitle()).isEqualTo("Updated Title");
    assertThat(clause.getSlug()).isEqualTo("updated-title");
    assertThat(clause.getDescription()).isEqualTo("A description");
    assertThat(clause.getBody()).isEqualTo(UPDATED_BODY);
    assertThat(clause.getCategory()).isEqualTo("Compliance");
    // source should remain unchanged
    assertThat(clause.getSource()).isEqualTo(ClauseSource.CUSTOM);

    clause.onPreUpdate(); // simulate JPA lifecycle
    assertThat(clause.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
  }

  @Test
  void deactivate_sets_active_false() {
    var clause = buildClause();
    assertThat(clause.isActive()).isTrue();

    clause.deactivate();

    assertThat(clause.isActive()).isFalse();
  }

  @Test
  void cloneFrom_creates_cloned_copy() {
    var original = buildClause();
    original.onPrePersist(); // simulate to set timestamps

    var clone = Clause.cloneFrom(original, "confidentiality-2");

    assertThat(clone.getTitle()).isEqualTo("Confidentiality");
    assertThat(clone.getSlug()).isEqualTo("confidentiality-2");
    assertThat(clone.getBody()).isEqualTo(SAMPLE_BODY);
    assertThat(clone.getCategory()).isEqualTo("General");
    assertThat(clone.getSource()).isEqualTo(ClauseSource.CLONED);
    assertThat(clone.getSourceClauseId()).isEqualTo(original.getId());
    assertThat(clone.isActive()).isTrue();
    assertThat(clone.getSortOrder()).isZero();
    // clone is not persisted, so no id or timestamps
    assertThat(clone.getId()).isNull();
    assertThat(clone.getCreatedAt()).isNull();
  }
}
