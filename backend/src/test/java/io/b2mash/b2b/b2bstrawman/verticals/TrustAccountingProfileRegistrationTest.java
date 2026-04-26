package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TrustAccountingProfileRegistrationTest {

  private VerticalProfileRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    registry = new VerticalProfileRegistry(new ObjectMapper());
  }

  @Test
  void legalZaProfile_includesTrustAccountingModule() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();
    assertThat(profile.get().enabledModules()).contains("trust_accounting");
  }

  @Test
  void legalZaProfile_includesAllLegalModules() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();
    assertThat(profile.get().enabledModules())
        .containsExactlyInAnyOrder(
            "court_calendar",
            "conflict_check",
            "lssa_tariff",
            "trust_accounting",
            "disbursements",
            "matter_closure",
            "deadlines",
            "information_requests",
            "bulk_billing");
  }
}
