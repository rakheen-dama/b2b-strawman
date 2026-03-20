package io.b2mash.b2b.b2bstrawman.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeadlineTypeRegistryTest {

  @Test
  void getDeadlineTypes_za_returnsEightTypes() {
    var types = DeadlineTypeRegistry.getDeadlineTypes("ZA");

    assertThat(types).hasSize(8);
  }

  @Test
  void sarsProvisional1_calculatesCorrectDate_fromKnownFye() {
    // FYE = 2026-02-28 (February)
    // 6th month after February = August
    // Last day of August = 2026-08-31
    var type = DeadlineTypeRegistry.getDeadlineType("sars_provisional_1");

    assertThat(type).isPresent();
    var dueDate = type.get().calculationRule().apply(LocalDate.of(2026, 2, 28), "2026");
    assertThat(dueDate).isEqualTo(LocalDate.of(2026, 8, 31));
  }

  @Test
  void sarsAnnualReturn_calculatesFyePlus12Months() {
    // FYE = 2025-03-31
    // Annual return = FYE + 12 months = 2026-03-31
    var type = DeadlineTypeRegistry.getDeadlineType("sars_annual_return");

    assertThat(type).isPresent();
    var dueDate = type.get().calculationRule().apply(LocalDate.of(2025, 3, 31), "2025");
    assertThat(dueDate).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void vatApplicability_returnsFalse_whenVatNumberNullOrEmpty() {
    var type = DeadlineTypeRegistry.getDeadlineType("sars_vat_return");

    assertThat(type).isPresent();
    // No vat_number key
    assertThat(type.get().applicabilityRule().test(Map.of())).isFalse();
    // Empty vat_number
    assertThat(type.get().applicabilityRule().test(Map.of("vat_number", ""))).isFalse();
  }

  @Test
  void vatApplicability_returnsTrue_whenVatNumberPopulated() {
    var type = DeadlineTypeRegistry.getDeadlineType("sars_vat_return");

    assertThat(type).isPresent();
    assertThat(type.get().applicabilityRule().test(Map.of("vat_number", "4123456789"))).isTrue();
  }

  @Test
  void cipcApplicability_checksCipcRegistrationNumber() {
    var type = DeadlineTypeRegistry.getDeadlineType("cipc_annual_return");

    assertThat(type).isPresent();
    // No cipc_registration_number key
    assertThat(type.get().applicabilityRule().test(Map.of())).isFalse();
    // With cipc_registration_number
    assertThat(
            type.get()
                .applicabilityRule()
                .test(Map.of("cipc_registration_number", "2020/123456/07")))
        .isTrue();
  }

  @Test
  void getDeadlineTypes_unknownJurisdiction_returnsEmptyList() {
    assertThat(DeadlineTypeRegistry.getDeadlineTypes("US")).isEmpty();
    assertThat(DeadlineTypeRegistry.getDeadlineTypes(null)).isEmpty();
  }

  @Test
  void getDeadlineType_bySlug_returnsCorrectType() {
    var type = DeadlineTypeRegistry.getDeadlineType("sars_provisional_1");

    assertThat(type).isPresent();
    assertThat(type.get().name()).isEqualTo("Provisional Tax \u2014 1st Payment");
    assertThat(type.get().category()).isEqualTo("tax");
    assertThat(type.get().jurisdiction()).isEqualTo("ZA");
  }
}
