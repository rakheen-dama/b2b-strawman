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
}
