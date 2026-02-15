package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

class DocumentTemplateSlugTest {

  @Test
  void shouldGenerateSlugFromName() {
    assertThat(DocumentTemplate.generateSlug("My Template")).isEqualTo("my-template");
  }

  @Test
  void shouldHandleSpecialChars() {
    assertThat(DocumentTemplate.generateSlug("Engagement Letter (2024)"))
        .isEqualTo("engagement-letter-2024");
  }

  @Test
  void shouldValidateSlugRegex() {
    String slug = DocumentTemplate.generateSlug("Standard NDA");
    assertThat(slug).matches("^[a-z][a-z0-9-]*$");
  }

  @Test
  void shouldThrowOnBlankName() {
    assertThatThrownBy(() -> DocumentTemplate.generateSlug(""))
        .isInstanceOf(InvalidStateException.class);
    assertThatThrownBy(() -> DocumentTemplate.generateSlug(null))
        .isInstanceOf(InvalidStateException.class);
    assertThatThrownBy(() -> DocumentTemplate.generateSlug("   "))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void shouldThrowOnNonLetterStart() {
    assertThatThrownBy(() -> DocumentTemplate.generateSlug("123test"))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void shouldCollapseMultipleHyphens() {
    assertThat(DocumentTemplate.generateSlug("cover -- letter")).isEqualTo("cover-letter");
  }

  @Test
  void shouldHandleHyphensInInput() {
    assertThat(DocumentTemplate.generateSlug("project-summary")).isEqualTo("project-summary");
  }
}
