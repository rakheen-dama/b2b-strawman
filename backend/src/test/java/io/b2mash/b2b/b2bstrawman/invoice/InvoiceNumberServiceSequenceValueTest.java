package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Phase 93 658A (D-253): which brought-forward numbers move the firm's counter. */
class InvoiceNumberServiceSequenceValueTest {

  @Test
  void aNumberInTheFirmsOwnFormat_hasItsSequenceValue_inAnyCaseAndWithLeadingZeros() {
    assertThat(InvoiceNumberService.sequenceValueOf("INV-0005")).hasValue(5);
    assertThat(InvoiceNumberService.sequenceValueOf("inv-0005")).hasValue(5);
    assertThat(InvoiceNumberService.sequenceValueOf(" INV-12345 ")).hasValue(12345);
    assertThat(InvoiceNumberService.sequenceValueOf("INV-00007")).hasValue(7);
  }

  @Test
  void anyOtherNumber_zero_andAValueAboveTheCeiling_leaveTheCounterAlone() {
    assertThat(InvoiceNumberService.sequenceValueOf(null)).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("BF-0005")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-12A")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("XINV-0005")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-0000")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-999999999"))
        .hasValue(InvoiceNumberService.MAX_SEQUENCE_VALUE);
    assertThat(InvoiceNumberService.sequenceValueOf("INV-1000000000")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-2147483646")).isEmpty();
    assertThat(InvoiceNumberService.sequenceValueOf("INV-1234567890123456789")).isEmpty();
  }
}
