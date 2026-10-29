package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link EmailTerminology}. */
class EmailTerminologyTest {

  private final EmailTerminology emailTerminology = new EmailTerminology();

  @Test
  void legalZa_mapsInvoiceNouns() {
    var map = emailTerminology.resolve("legal-za");
    assertThat(map).containsEntry("Invoice", "Fee Note");
    assertThat(map).containsEntry("invoices", "fee notes");
  }

  @Test
  void nullOrUnknownNamespace_returnsTheLegalVocabulary() {
    // 628D (R-4, ADR-330/ADR-338): one vocabulary for every tenant, profile or not.
    assertThat(emailTerminology.resolve(null))
        .containsEntry("Invoice", "Fee Note")
        .containsEntry("invoices", "fee notes")
        .isEqualTo(emailTerminology.resolve("legal-za"));
    assertThat(emailTerminology.resolve("retail-uk"))
        .isEqualTo(emailTerminology.resolve("legal-za"));
  }

  @Test
  void withIndefiniteArticle_matchesLeadingSound() {
    assertThat(EmailTerminology.withIndefiniteArticle("invoice")).isEqualTo("an invoice");
    assertThat(EmailTerminology.withIndefiniteArticle("fee note")).isEqualTo("a fee note");
    assertThat(EmailTerminology.withIndefiniteArticle("matter")).isEqualTo("a matter");
    assertThat(EmailTerminology.withIndefiniteArticle("engagement letter"))
        .isEqualTo("an engagement letter");
  }

  @Test
  void withIndefiniteArticle_passesThroughNullAndBlank() {
    assertThat(EmailTerminology.withIndefiniteArticle(null)).isNull();
    assertThat(EmailTerminology.withIndefiniteArticle("")).isEmpty();
  }
}
