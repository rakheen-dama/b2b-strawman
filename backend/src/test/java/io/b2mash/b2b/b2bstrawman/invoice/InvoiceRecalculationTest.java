package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceRecalculationTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  private Invoice createInvoice() {
    return new Invoice(
        CUSTOMER_ID, "ZAR", "Test Customer", "test@test.com", null, "Test Org", MEMBER_ID);
  }

  private InvoiceLine createLine(
      UUID invoiceId, BigDecimal quantity, BigDecimal unitPrice, int sortOrder) {
    return new InvoiceLine(invoiceId, null, null, "Line item", quantity, unitPrice, sortOrder);
  }

  /** Computes hasPerLineTax and perLineTaxSum, then delegates to Invoice.recalculateTotals. */
  private void recalculate(
      Invoice invoice, BigDecimal subtotal, List<InvoiceLine> lines, boolean taxInclusive) {
    boolean hasPerLineTax = lines.stream().anyMatch(line -> line.getTaxRateId() != null);
    BigDecimal perLineTaxSum =
        lines.stream()
            .map(line -> line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    invoice.recalculateTotals(subtotal, hasPerLineTax, perLineTaxSum, taxInclusive);
  }

  @Test
  void no_per_line_tax_preserves_manual_taxAmount() {
    var invoice = createInvoice();
    // Manually set taxAmount via updateDraft
    invoice.updateDraft(null, null, null, new BigDecimal("150.00"));

    var line1 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("500.00"), 0);
    var line2 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("500.00"), 1);
    var lines = List.of(line1, line2);

    // No lines have taxRateId set — manual taxAmount should be preserved
    recalculate(invoice, new BigDecimal("1000.00"), lines, false);

    assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("1150.00"));
  }

  @Test
  void per_line_tax_sums_line_amounts() {
    var invoice = createInvoice();
    var line1 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("1000.00"), 0);
    var line2 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("500.00"), 1);

    setLineTaxFields(
        line1, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("150.00"), false);
    setLineTaxFields(
        line2, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("75.00"), false);

    recalculate(invoice, new BigDecimal("1500.00"), List.of(line1, line2), false);

    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("225.00"));
  }

  @Test
  void tax_exclusive_total_is_subtotal_plus_tax() {
    var invoice = createInvoice();
    var line = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("1000.00"), 0);
    setLineTaxFields(
        line, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("150.00"), false);

    recalculate(invoice, new BigDecimal("1000.00"), List.of(line), false);

    assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("1150.00"));
  }

  @Test
  void tax_inclusive_total_equals_subtotal() {
    var invoice = createInvoice();
    var line = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("1150.00"), 0);
    setLineTaxFields(
        line, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("150.00"), false);

    recalculate(invoice, new BigDecimal("1150.00"), List.of(line), true);

    assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("1150.00"));
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("1150.00"));
  }

  @Test
  void mixed_lines_with_and_without_tax() {
    var invoice = createInvoice();
    var taxedLine = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("1000.00"), 0);
    setLineTaxFields(
        taxedLine,
        UUID.randomUUID(),
        "VAT",
        new BigDecimal("15.00"),
        new BigDecimal("150.00"),
        false);

    var untaxedLine = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("200.00"), 1);
    // untaxedLine has no taxRateId — taxAmount is null

    recalculate(invoice, new BigDecimal("1200.00"), List.of(taxedLine, untaxedLine), false);

    // hasPerLineTax is true because at least one line has taxRateId
    // taxAmount sums all line taxAmounts (null treated as zero)
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("1350.00"));
  }

  @Test
  void all_exempt_lines_zero_tax() {
    var invoice = createInvoice();
    var line1 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("500.00"), 0);
    setLineTaxFields(line1, UUID.randomUUID(), "Exempt", BigDecimal.ZERO, BigDecimal.ZERO, true);

    var line2 = createLine(invoice.getId(), BigDecimal.ONE, new BigDecimal("300.00"), 1);
    setLineTaxFields(line2, UUID.randomUUID(), "Exempt", BigDecimal.ZERO, BigDecimal.ZERO, true);

    recalculate(invoice, new BigDecimal("800.00"), List.of(line1, line2), false);

    // hasPerLineTax is true (taxRateId is set), but all taxAmounts are zero
    assertThat(invoice.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("800.00"));
  }

  /**
   * Helper to set tax fields on an InvoiceLine via reflection, since the fields are private and
   * applyTaxRate() requires a TaxRate entity which is cumbersome in a pure unit test.
   */
  private void setLineTaxFields(
      InvoiceLine line,
      UUID taxRateId,
      String taxRateName,
      BigDecimal taxRatePercent,
      BigDecimal taxAmount,
      boolean taxExempt) {
    try {
      var taxRateIdField = InvoiceLine.class.getDeclaredField("taxRateId");
      taxRateIdField.setAccessible(true);
      taxRateIdField.set(line, taxRateId);

      var taxRateNameField = InvoiceLine.class.getDeclaredField("taxRateName");
      taxRateNameField.setAccessible(true);
      taxRateNameField.set(line, taxRateName);

      var taxRatePercentField = InvoiceLine.class.getDeclaredField("taxRatePercent");
      taxRatePercentField.setAccessible(true);
      taxRatePercentField.set(line, taxRatePercent);

      var taxAmountField = InvoiceLine.class.getDeclaredField("taxAmount");
      taxAmountField.setAccessible(true);
      taxAmountField.set(line, taxAmount);

      var taxExemptField = InvoiceLine.class.getDeclaredField("taxExempt");
      taxExemptField.setAccessible(true);
      taxExemptField.set(line, taxExempt);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set tax fields on InvoiceLine", e);
    }
  }
}
