package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link VerticalProfileRegistry}. Since Phase 86 (Epic 604, ADR-332) the registry
 * loads exactly one profile, {@code legal-za}; the accounting-za, consulting-za and
 * consulting-generic profiles were deleted.
 */
class VerticalProfileRegistryTest {

  private VerticalProfileRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    registry = new VerticalProfileRegistry(new ObjectMapper());
  }

  @Test
  void loadsLegalZaProfileWithCorrectFields() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();
    var p = profile.get();
    assertThat(p.profileId()).isEqualTo("legal-za");
    assertThat(p.name()).isEqualTo("Legal (South Africa)");
    assertThat(p.currency()).isEqualTo("ZAR");
    assertThat(p.terminologyNamespace()).isEqualTo("en-ZA-legal");
    assertThat(p.packs()).containsKey("field");
    assertThat(p.packs()).doesNotContainKey("compliance");
  }

  @Test
  void existsReturnsFalseForNonexistent() {
    assertThat(registry.exists("nonexistent")).isFalse();
  }

  @Test
  void existsReturnsTrueForLegalZa() {
    assertThat(registry.exists("legal-za")).isTrue();
  }

  @Test
  void existsReturnsFalseForRemovedProfiles() {
    assertThat(registry.exists("accounting-za")).isFalse();
    assertThat(registry.exists("consulting-za")).isFalse();
    assertThat(registry.exists("consulting-generic")).isFalse();
  }

  @Test
  void getAllProfilesReturnsOnlyLegalZa() {
    assertThat(registry.getAllProfiles())
        .extracting(VerticalProfileRegistry.ProfileDefinition::profileId)
        .containsExactly("legal-za");
  }

  @Test
  void legalZaProfileIncludesLegalModules() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();

    var p = profile.get();
    // GAP-L-61-followup (E9.2): bulk_billing ships ON by default for legal-za because
    // most SA law firms invoice clients in monthly batches.
    assertThat(p.enabledModules())
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

  @Test
  void legalZaProfileHasNoRateCardDefaults() {
    var profile = registry.getProfile("legal-za").orElseThrow();
    assertThat(profile.rateCardDefaults()).isNull();
  }
}
