package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuditEventTypeRegistry}. No Spring context — instantiate the registry
 * directly because it is a self-contained pure-Java component.
 *
 * <p>Coverage focuses on the longest-prefix-wins resolution (architecture §12.3.3): exact match
 * beats prefix, deeper prefix beats shallower, default fallback synthesises an INFO/STANDARD entry
 * with a title-cased label.
 */
class AuditEventTypeRegistryTest {

  private final AuditEventTypeRegistry registry = new AuditEventTypeRegistry();

  @Test
  void exactMatchWinsOverPrefix() {
    // matter.closure.override_used has an exact entry (CRITICAL) and a parent matter.closure.*
    // (NOTICE). Exact must win.
    var resolved = registry.resolve("matter.closure.override_used");
    assertThat(resolved.eventType()).isEqualTo("matter.closure.override_used");
    assertThat(resolved.label()).isEqualTo("Matter Closure Override Used");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.CRITICAL);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.COMPLIANCE);
  }

  @Test
  void prefixMatchUsedWhenNoExactMatch() {
    // matter.closure.* should match an unregistered child like matter.closure.something_else.
    var resolved = registry.resolve("matter.closure.something_else");
    // The resolver carries the original eventType through but copies the prefix's classification.
    assertThat(resolved.eventType()).isEqualTo("matter.closure.something_else");
    assertThat(resolved.label()).isEqualTo("Matter Closure");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.NOTICE);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.COMPLIANCE);
  }

  @Test
  void deeperPrefixWinsOverShallower() {
    // dataprotection.dsar.* (NOTICE/DATA, label "Data Subject Request") sits beneath
    // dataprotection.* (NOTICE/DATA, label "Data Protection"). For an unregistered child like
    // dataprotection.dsar.fulfilled, the deeper prefix must win — the label distinguishes them.
    var resolved = registry.resolve("dataprotection.dsar.fulfilled");
    assertThat(resolved.eventType()).isEqualTo("dataprotection.dsar.fulfilled");
    assertThat(resolved.label()).isEqualTo("Data Subject Request");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.NOTICE);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.DATA);
  }

  @Test
  void unknownEventTypeFallsBackToTitleCaseInfoStandard() {
    var resolved = registry.resolve("foo.bar.baz");
    assertThat(resolved.eventType()).isEqualTo("foo.bar.baz");
    assertThat(resolved.label()).isEqualTo("Foo Bar Baz");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.STANDARD);
  }

  @Test
  void securityLoginFailureExactMatchHasWarningSeverity() {
    var resolved = registry.resolve("security.login.failure");
    assertThat(resolved.eventType()).isEqualTo("security.login.failure");
    assertThat(resolved.label()).isEqualTo("Login Failed");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.WARNING);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.SECURITY);
  }

  @Test
  void entriesReturnsFullCatalogueExcludingDefaultFallback() {
    // 17 base entries + 7 ai.specialist.* entries (Epic 515A) + 5 deal.* SALES entries (Epic 575A)
    // + 5 mcp.write.* entries (4 exact + 1 prefix, Epic 582A)
    // + 2 ai.gate.* entries (1 exact + 1 prefix, Epic 585)
    // + 5 collections.* entries (5 exact, Epic 588B) = 41. Default fallback is synthesised
    // inside resolve() and must NOT appear in entries().
    assertThat(registry.entries()).hasSize(41);
    assertThat(registry.entries())
        .extracting(AuditEventTypeMetadata::eventType)
        .doesNotContain("(default fallback)")
        .contains(
            "matter.closure.override_used",
            "security.*",
            "trust.*",
            "dataprotection.*",
            "deal.won",
            "mcp.write.correspondence_filed",
            "mcp.write.*",
            "ai.gate.created",
            "ai.gate.*",
            "collections.policy.updated",
            "collections.reminder.sent");
  }

  @Test
  void entriesMatchingFiltersBySeverity() {
    var critical = registry.entriesMatching(Set.of(AuditSeverity.CRITICAL));
    assertThat(critical)
        .extracting(AuditEventTypeMetadata::eventType)
        .containsExactly("matter.closure.override_used");

    var warningOrCritical =
        registry.entriesMatching(Set.of(AuditSeverity.WARNING, AuditSeverity.CRITICAL));
    assertThat(warningOrCritical)
        .extracting(AuditEventTypeMetadata::eventType)
        .contains(
            "security.login.failure",
            "security.permission.denied",
            "matter.closure.override_used",
            "trust.transaction.rejected",
            "member.role_changed",
            "orgrole.capability.changed");
  }

  @Test
  void entriesMatchingWithEmptyOrNullReturnsAll() {
    assertThat(registry.entriesMatching(Set.of())).hasSize(41);
    assertThat(registry.entriesMatching(null)).hasSize(41);
  }

  @Test
  void resolveTolerantOfNullInput() {
    var resolved = registry.resolve(null);
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.STANDARD);
    assertThat(resolved.label()).isEqualTo("");
  }

  @Test
  void productionRegistryPassesPrefixSeverityInvariant() {
    // The production registry must pass the prefix-severity invariant — instantiating it
    // already calls the check, but assert explicitly so a regression that only touches the
    // entries list still fails this test if the catalogue is authored into the broken state.
    var entries = registry.entries();
    AuditEventTypeRegistry.validatePrefixSeverityInvariant(entries);
  }

  @Test
  void invariantRejectsChildPrefixWithDifferentSeverity() {
    // Hypothetical bad authoring: dataprotection.dsar.* (NOTICE) sits under dataprotection.*
    // (WARNING). The pre-flight in DatabaseAuditService.findEvents would over-include the
    // dataprotection.dsar.X rows when querying for WARNING. The invariant must reject this.
    var badEntries =
        List.of(
            new AuditEventTypeMetadata(
                "dataprotection.*", "Data Protection", AuditSeverity.WARNING, AuditEventGroup.DATA),
            new AuditEventTypeMetadata(
                "dataprotection.dsar.*",
                "Data Subject Request",
                AuditSeverity.NOTICE,
                AuditEventGroup.DATA));
    assertThatThrownBy(() -> AuditEventTypeRegistry.validatePrefixSeverityInvariant(badEntries))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dataprotection.dsar.*")
        .hasMessageContaining("dataprotection.*");
  }

  @Test
  void invariantAllowsSiblingPrefixesWithDifferentSeverities() {
    // Sibling prefixes (neither nests inside the other) are unrelated and may carry different
    // severities — only nested-prefix mismatches break the pre-flight.
    var siblingEntries =
        List.of(
            new AuditEventTypeMetadata(
                "alpha.*", "Alpha", AuditSeverity.WARNING, AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "beta.*", "Beta", AuditSeverity.NOTICE, AuditEventGroup.STANDARD));
    AuditEventTypeRegistry.validatePrefixSeverityInvariant(siblingEntries);
  }

  @Test
  void invariantTreatsLiteralPrefixBoundaries() {
    // Regression guard: 'data.*' must NOT be considered a parent of 'dataprotection.*' just
    // because 'dataprotection' starts with 'data'. The invariant compares dot-bounded prefixes.
    var nonNestedEntries =
        List.of(
            new AuditEventTypeMetadata(
                "data.*", "Data", AuditSeverity.WARNING, AuditEventGroup.DATA),
            new AuditEventTypeMetadata(
                "dataprotection.*", "Data Protection", AuditSeverity.NOTICE, AuditEventGroup.DATA));
    AuditEventTypeRegistry.validatePrefixSeverityInvariant(nonNestedEntries);
  }
}
