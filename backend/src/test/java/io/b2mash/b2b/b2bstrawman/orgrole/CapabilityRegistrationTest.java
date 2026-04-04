package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapabilityRegistrationTest {

  @Test
  void viewLegal_existsAndResolvesViaFromString() {
    assertThat(Capability.VIEW_LEGAL).isNotNull();
    assertThat(Capability.fromString("VIEW_LEGAL")).isEqualTo(Capability.VIEW_LEGAL);
    assertThat(Capability.ALL_NAMES).contains("VIEW_LEGAL");
  }

  @Test
  void manageLegal_existsAndResolvesViaFromString() {
    assertThat(Capability.MANAGE_LEGAL).isNotNull();
    assertThat(Capability.fromString("MANAGE_LEGAL")).isEqualTo(Capability.MANAGE_LEGAL);
    assertThat(Capability.ALL_NAMES).contains("MANAGE_LEGAL");
  }

  @Test
  void viewTrust_existsAndResolvesViaFromString() {
    assertThat(Capability.VIEW_TRUST).isNotNull();
    assertThat(Capability.fromString("VIEW_TRUST")).isEqualTo(Capability.VIEW_TRUST);
    assertThat(Capability.ALL_NAMES).contains("VIEW_TRUST");
  }

  @Test
  void manageTrust_existsAndResolvesViaFromString() {
    assertThat(Capability.MANAGE_TRUST).isNotNull();
    assertThat(Capability.fromString("MANAGE_TRUST")).isEqualTo(Capability.MANAGE_TRUST);
    assertThat(Capability.ALL_NAMES).contains("MANAGE_TRUST");
  }

  @Test
  void approveTrustPayment_existsAndResolvesViaFromString() {
    assertThat(Capability.APPROVE_TRUST_PAYMENT).isNotNull();
    assertThat(Capability.fromString("APPROVE_TRUST_PAYMENT"))
        .isEqualTo(Capability.APPROVE_TRUST_PAYMENT);
    assertThat(Capability.ALL_NAMES).contains("APPROVE_TRUST_PAYMENT");
  }
}
