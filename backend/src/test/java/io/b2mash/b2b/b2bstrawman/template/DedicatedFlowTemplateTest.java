package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** G89 632A / D11: which templates belong to a dedicated flow (pack key, base slug or clone). */
class DedicatedFlowTemplateTest {

  @Test
  void matchesOnThePackTemplateKey_evenWhenTheSlugDiffers() {
    var receipt = template("payment-receipt", "payment-receipt-za");
    assertThat(DedicatedFlowTemplate.of(receipt)).contains(DedicatedFlowTemplate.PAYMENT_RECEIPT);
  }

  @Test
  void matchesAFirmCloneByItsSlug() {
    var clone = template("statement-of-account-custom", null);
    assertThat(DedicatedFlowTemplate.of(clone))
        .contains(DedicatedFlowTemplate.STATEMENT_OF_ACCOUNT);
  }

  @Test
  void ordinaryTemplatesAndLookalikeSlugsAreNotDedicated() {
    assertThat(DedicatedFlowTemplate.of(template("engagement-letter-general", "x"))).isEmpty();
    assertThat(DedicatedFlowTemplate.of(template("trust-receipts-register", null))).isEmpty();
    assertThat(
            DedicatedFlowTemplate.of(template("client-trust-statement", "client-trust-statement")))
        .isEmpty();
  }

  private static DocumentTemplate template(String slug, String packTemplateKey) {
    var dt =
        new DocumentTemplate(
            TemplateEntityType.PROJECT, slug, slug, TemplateCategory.OTHER, Map.of("type", "doc"));
    dt.setPackTemplateKey(packTemplateKey);
    return dt;
  }
}
