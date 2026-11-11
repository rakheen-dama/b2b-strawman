package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * G89 W2-c2 (CodeRabbit on #1755): the digest prints ZAR fee-note totals in the house format and
 * keeps the ISO currency code for any other currency, so "$" can never be read as the wrong dollar.
 */
class DigestInvoiceTotalFormatTest {

  private static final BigDecimal AMOUNT = new BigDecimal("5335.00");

  @Test
  void zarAndMissingCurrency_printTheHouseRandFormat() {
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(AMOUNT, "ZAR"))
        .isEqualTo("R 5 335,00");
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(AMOUNT, null))
        .isEqualTo("R 5 335,00");
  }

  @Test
  void otherCurrencies_keepTheirIsoCode() {
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(AMOUNT, "USD"))
        .isEqualTo("$ 5,335.00 USD");
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(AMOUNT, "AUD"))
        .endsWith(" AUD");
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(AMOUNT, "EUR"))
        .endsWith(" EUR");
  }

  @Test
  void missingAmount_staysNull() {
    assertThat(PortalDigestContentAssembler.DigestInvoiceLine.digestTotal(null, "USD")).isNull();
  }
}
