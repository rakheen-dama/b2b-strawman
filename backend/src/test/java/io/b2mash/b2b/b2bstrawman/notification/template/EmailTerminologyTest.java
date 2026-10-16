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
  void nullOrUnknownNamespace_returnsEmptyMap() {
    assertThat(emailTerminology.resolve(null)).isEmpty();
    assertThat(emailTerminology.resolve("retail-uk")).isEmpty();
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
