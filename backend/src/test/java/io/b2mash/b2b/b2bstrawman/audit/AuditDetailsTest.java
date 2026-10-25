package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-349 / 625A: the matter-scoped audit-details helper (pure unit test, no Spring context). */
class AuditDetailsTest {

  private static final UUID PROJECT = UUID.fromString("9a27cd08-76fc-44b1-8c41-a8dd1aa1af22");
  private static final UUID CUSTOMER = UUID.fromString("1d7cae32-0deb-43fe-af41-a3b9452f3128");

  @Test
  void nullProjectIsWrittenAsEmptyString_andNullCustomerIsOmitted() {
    var details = AuditDetails.matterScoped(null, null);

    assertThat(details).containsExactly(Map.entry("project_id", ""));
    assertThat(details).doesNotContainKey("customer_id");
  }

  @Test
  void knownIdsAreWrittenAsStrings() {
    var details = AuditDetails.matterScoped(PROJECT, CUSTOMER);

    assertThat(details)
        .containsExactly(
            Map.entry("project_id", PROJECT.toString()),
            Map.entry("customer_id", CUSTOMER.toString()));
  }

  @Test
  void restIsMergedAfterTheCorrelationKeys() {
    var details = AuditDetails.matterScoped(PROJECT, null, Map.of("amount", "5000.00"));

    assertThat(details)
        .containsExactly(
            Map.entry("project_id", PROJECT.toString()), Map.entry("amount", "5000.00"));
  }

  @Test
  void restMustNotCarryTheCorrelationKeys() {
    assertThatThrownBy(
            () -> AuditDetails.matterScoped(PROJECT, CUSTOMER, Map.of("project_id", "x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> AuditDetails.matterScoped(PROJECT, CUSTOMER, Map.of("customer_id", "x")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resultIsAFreshMutableMapThatAcceptsNullValues() {
    var first = AuditDetails.matterScoped(PROJECT, CUSTOMER, Map.of("k", "v"));
    first.put("nullable", null);
    first.put("later", 1);

    assertThat(first).containsEntry("later", 1).containsKey("nullable");
    assertThat(AuditDetails.matterScoped(PROJECT, CUSTOMER))
        .as("each call returns a new map")
        .doesNotContainKey("later");
  }
}
