package io.b2mash.b2b.b2bstrawman.tax;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaxCalculationServiceTest {

  private final TaxCalculationService service = new TaxCalculationService();

  // --- Tax-exclusive tests ---

  @Test
  void calculateLineTax_exclusive_standardRate() {
    // 15% on R1000.00 = R150.00
    var result =
        service.calculateLineTax(new BigDecimal("1000.00"), new BigDecimal("15.00"), false, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void calculateLineTax_exclusive_zeroRate() {
    // 0% on R1000.00 = R0.00 (zero-rated, not short-circuited)
    var result =
        service.calculateLineTax(new BigDecimal("1000.00"), new BigDecimal("0.00"), false, false);
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void calculateLineTax_exclusive_rounding() {
    // 15% on R33.33 = R4.9995 -> R5.00 (HALF_UP)
    var result =
        service.calculateLineTax(new BigDecimal("33.33"), new BigDecimal("15.00"), false, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("5.00"));
  }

  @Test
  void calculateLineTax_exclusive_fractionalRate() {
    // 12.50% on R200.00 = R25.00
    var result =
        service.calculateLineTax(new BigDecimal("200.00"), new BigDecimal("12.50"), false, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("25.00"));
  }

  // --- Tax-inclusive tests ---

  @Test
  void calculateLineTax_inclusive_standardRate() {
    // 15% inclusive on R115.00: tax = 115 - (115 / 1.15) = 115 - 100 = R15.00
    var result =
        service.calculateLineTax(new BigDecimal("115.00"), new BigDecimal("15.00"), true, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("15.00"));
  }

  @Test
  void calculateLineTax_inclusive_rounding() {
    // 15% inclusive on R100.00: tax = 100 - (100 / 1.15) = 100 - 86.96 = R13.04
    var result =
        service.calculateLineTax(new BigDecimal("100.00"), new BigDecimal("15.00"), true, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("13.04"));
  }

  @Test
  void calculateLineTax_inclusive_zeroRate() {
    // 0% inclusive on R1000.00: tax = 1000 - (1000 / 1.00) = R0.00
    var result =
        service.calculateLineTax(new BigDecimal("1000.00"), new BigDecimal("0.00"), true, false);
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- Exempt tests ---

  @Test
  void calculateLineTax_exempt_returnsZero() {
    // Exempt short-circuits regardless of rate or inclusive flag
    var result =
        service.calculateLineTax(new BigDecimal("1000.00"), new BigDecimal("15.00"), false, true);
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void calculateLineTax_exempt_inclusive_returnsZero() {
    var result =
        service.calculateLineTax(new BigDecimal("1000.00"), new BigDecimal("15.00"), true, true);
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- Edge cases ---

  @Test
  void calculateLineTax_zeroAmount() {
    var result = service.calculateLineTax(BigDecimal.ZERO, new BigDecimal("15.00"), false, false);
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void calculateLineTax_exclusive_largeAmount() {
    // 15% on R999999.99 = R149999.9985 -> R150000.00
    var result =
        service.calculateLineTax(
            new BigDecimal("999999.99"), new BigDecimal("15.00"), false, false);
    assertThat(result).isEqualByComparingTo(new BigDecimal("150000.00"));
  }

  // --- hasPerLineTax tests ---

  @Test
  void hasPerLineTax_returns_false_for_empty_list() {
    assertThat(TaxCalculationService.hasPerLineTax(List.of())).isFalse();
  }

  @Test
  void hasPerLineTax_returns_false_when_no_tax_rate_ids() {
    var line = createTestLine(new BigDecimal("1000.00"));
    assertThat(TaxCalculationService.hasPerLineTax(List.of(line))).isFalse();
  }

  @Test
  void hasPerLineTax_returns_true_when_any_line_has_tax_rate_id() {
    var taxedLine = createTestLine(new BigDecimal("1000.00"));
    setLineTaxFields(
        taxedLine,
        UUID.randomUUID(),
        "VAT",
        new BigDecimal("15.00"),
        new BigDecimal("150.00"),
        false);
    var untaxedLine = createTestLine(new BigDecimal("500.00"));
    assertThat(TaxCalculationService.hasPerLineTax(List.of(taxedLine, untaxedLine))).isTrue();
  }

  // --- Tax breakdown tests ---

  @Test
  void buildTaxBreakdown_groups_by_rate() {
    var line1 = createTestLine(new BigDecimal("1000.00"));
    setLineTaxFields(
        line1, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("150.00"), false);

    var line2 = createTestLine(new BigDecimal("500.00"));
    setLineTaxFields(
        line2, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("75.00"), false);

    var result = service.buildTaxBreakdown(List.of(line1, line2));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().rateName()).isEqualTo("VAT");
    assertThat(result.getFirst().ratePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
    assertThat(result.getFirst().taxableAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    assertThat(result.getFirst().taxAmount()).isEqualByComparingTo(new BigDecimal("225.00"));
  }

  @Test
  void buildTaxBreakdown_excludes_exempt() {
    var taxedLine = createTestLine(new BigDecimal("1000.00"));
    setLineTaxFields(
        taxedLine,
        UUID.randomUUID(),
        "VAT",
        new BigDecimal("15.00"),
        new BigDecimal("150.00"),
        false);

    var exemptLine = createTestLine(new BigDecimal("500.00"));
    setLineTaxFields(
        exemptLine, UUID.randomUUID(), "Exempt", BigDecimal.ZERO, BigDecimal.ZERO, true);

    var result = service.buildTaxBreakdown(List.of(taxedLine, exemptLine));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().rateName()).isEqualTo("VAT");
  }

  @Test
  void buildTaxBreakdown_empty_when_no_per_line_tax() {
    var line = createTestLine(new BigDecimal("1000.00"));
    // No tax fields set — taxRateId is null

    var result = service.buildTaxBreakdown(List.of(line));

    assertThat(result).isEmpty();
  }

  @Test
  void buildTaxBreakdown_multiple_rates() {
    var line1 = createTestLine(new BigDecimal("1000.00"));
    setLineTaxFields(
        line1, UUID.randomUUID(), "VAT", new BigDecimal("15.00"), new BigDecimal("150.00"), false);

    var line2 = createTestLine(new BigDecimal("200.00"));
    setLineTaxFields(
        line2,
        UUID.randomUUID(),
        "Reduced",
        new BigDecimal("5.00"),
        new BigDecimal("10.00"),
        false);

    var result = service.buildTaxBreakdown(List.of(line1, line2));

    assertThat(result).hasSize(2);
    assertThat(result).extracting(TaxBreakdownEntry::rateName).containsExactly("VAT", "Reduced");
  }

  // --- Test helpers ---

  private InvoiceLine createTestLine(BigDecimal amount) {
    return new InvoiceLine(UUID.randomUUID(), null, null, "Test line", BigDecimal.ONE, amount, 0);
  }

  private void setLineTaxFields(
      InvoiceLine line,
      UUID taxRateId,
      String taxRateName,
      BigDecimal taxRatePercent,
      BigDecimal taxAmount,
      boolean taxExempt) {
    try {
      var f1 = InvoiceLine.class.getDeclaredField("taxRateId");
      f1.setAccessible(true);
      f1.set(line, taxRateId);
      var f2 = InvoiceLine.class.getDeclaredField("taxRateName");
      f2.setAccessible(true);
      f2.set(line, taxRateName);
      var f3 = InvoiceLine.class.getDeclaredField("taxRatePercent");
      f3.setAccessible(true);
      f3.set(line, taxRatePercent);
      var f4 = InvoiceLine.class.getDeclaredField("taxAmount");
      f4.setAccessible(true);
      f4.set(line, taxAmount);
      var f5 = InvoiceLine.class.getDeclaredField("taxExempt");
      f5.setAccessible(true);
      f5.set(line, taxExempt);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
