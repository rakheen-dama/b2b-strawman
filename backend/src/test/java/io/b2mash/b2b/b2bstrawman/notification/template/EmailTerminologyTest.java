package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmailTerminology}. LZKC-004 extended the legal-za map beyond invoice nouns
 * to proposal nouns, so client-facing engagement-letter emails and seeded letter bodies stop
 * leaking "proposal" on legal-za tenants.
 */
class EmailTerminologyTest {

  private final EmailTerminology emailTerminology = new EmailTerminology();

  @Test
  void legalZa_mapsInvoiceNouns() {
    var map = emailTerminology.resolve("legal-za");
    assertThat(map).containsEntry("Invoice", "Fee Note");
    assertThat(map).containsEntry("invoices", "fee notes");
  }

  @Test
  void legalZa_mapsProposalNouns() {
    var map = emailTerminology.resolve("legal-za");
    assertThat(map).containsEntry("Proposal", "Engagement Letter");
    assertThat(map).containsEntry("proposal", "engagement letter");
    assertThat(map).containsEntry("Proposals", "Engagement Letters");
    assertThat(map).containsEntry("proposals", "engagement letters");
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
    assertThat(EmailTerminology.withIndefiniteArticle("proposal")).isEqualTo("a proposal");
    assertThat(EmailTerminology.withIndefiniteArticle("engagement letter"))
        .isEqualTo("an engagement letter");
  }

  @Test
  void withIndefiniteArticle_passesThroughNullAndBlank() {
    assertThat(EmailTerminology.withIndefiniteArticle(null)).isNull();
    assertThat(EmailTerminology.withIndefiniteArticle("")).isEmpty();
  }
}
