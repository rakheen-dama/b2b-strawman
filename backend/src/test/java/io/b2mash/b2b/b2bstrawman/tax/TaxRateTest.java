package io.b2mash.b2b.b2bstrawman.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxRateTest {

  private TaxRate buildRate() {
    return new TaxRate("Standard", new BigDecimal("15.00"), true, false, 0);
  }

  @Test
  void constructor_sets_fields() {
    var rate = new TaxRate("Zero-rated", new BigDecimal("0.00"), false, false, 1);

    assertThat(rate.getName()).isEqualTo("Zero-rated");
    assertThat(rate.getRate()).isEqualByComparingTo(new BigDecimal("0.00"));
    assertThat(rate.isDefault()).isFalse();
    assertThat(rate.isExempt()).isFalse();
    assertThat(rate.getSortOrder()).isEqualTo(1);
  }

  @Test
  void default_active_is_true() {
    var rate = buildRate();
    assertThat(rate.isActive()).isTrue();
  }

  @Test
  void preUpdate_sets_updatedAt() {
    var rate = buildRate();
    assertThat(rate.getUpdatedAt()).isNull();

    rate.onPreUpdate();

    assertThat(rate.getUpdatedAt()).isNotNull();
  }

  @Test
  void deactivate_sets_active_false_and_clears_default() {
    var rate = buildRate();
    assertThat(rate.isActive()).isTrue();
    assertThat(rate.isDefault()).isTrue();

    rate.deactivate();

    assertThat(rate.isActive()).isFalse();
    assertThat(rate.isDefault()).isFalse();
  }
}
