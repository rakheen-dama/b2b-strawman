package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Phase 93 653A (§17.6.4): {@code takeon.*} audit details carry ids, enum names, numbers, booleans
 * and SAFE_TOKEN strings only — no PII can be put in by construction.
 */
class TakeOnAuditDetailsTest {

  @Test
  void acceptsIdsEnumsNumbersBooleansAndSafeTokens() {
    UUID id = UUID.randomUUID();
    var details =
        TakeOnAuditDetails.builder()
            .put("batch_id", id)
            .put("dataset", TakeOnDataset.CLIENTS)
            .put("row_count", 3)
            .put("file_size_kb", 12L)
            .put("eq1_holds", true)
            .token("reason_code", "USER")
            .build();
    assertThat(details)
        .containsExactly(
            java.util.Map.entry("batch_id", id.toString()),
            java.util.Map.entry("dataset", "CLIENTS"),
            java.util.Map.entry("row_count", 3),
            java.util.Map.entry("file_size_kb", 12L),
            java.util.Map.entry("eq1_holds", true),
            java.util.Map.entry("reason_code", "USER"));
  }

  @Test
  void refusesFreeTextNamesEmailsAndAmounts() {
    for (String pii :
        new String[] {
          "Zanele Pii-Canary", "pii.canary@example.test", "R 12 345,67", "a".repeat(65)
        }) {
      assertThatThrownBy(() -> TakeOnAuditDetails.builder().token("reason_code", pii))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void refusesNullValuesUnsafeKeysAndRepeatedKeys() {
    assertThatThrownBy(() -> TakeOnAuditDetails.builder().token("reason_code", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TakeOnAuditDetails.builder().put("batch_id", (UUID) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TakeOnAuditDetails.builder().put("bad key", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TakeOnAuditDetails.builder().put("n", 1).put("n", 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theOnlyStringValuedMethodIsTheTokenCheck() {
    var stringValued =
        Arrays.stream(TakeOnAuditDetails.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .filter(m -> m.getParameterCount() == 2 && m.getParameterTypes()[1] == String.class)
            .map(Method::getName)
            .toList();
    assertThat(stringValued).containsExactly("token");
    assertThat(
            Arrays.stream(TakeOnAuditDetails.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 2)
                .map(m -> m.getParameterTypes()[1])
                .filter(
                    t ->
                        t == Object.class
                            || t == CharSequence.class
                            || t == java.math.BigDecimal.class))
        .isEmpty();
  }
}
