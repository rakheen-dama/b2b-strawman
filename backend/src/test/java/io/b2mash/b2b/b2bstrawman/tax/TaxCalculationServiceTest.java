package io.b2mash.b2b.b2bstrawman.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
}
