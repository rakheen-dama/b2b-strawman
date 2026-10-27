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
 * with a legal sentence-cased label (AuditEventTypeRegistry.legalLabel).
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
  void unknownEventTypeFallsBackToLegalSentenceCaseInfoStandard() {
    // Phase 88 625C (D12, R-8): the synthesised default label is sentence case.
    var resolved = registry.resolve("foo.bar.baz");
    assertThat(resolved.eventType()).isEqualTo("foo.bar.baz");
    assertThat(resolved.label()).isEqualTo("Foo bar baz");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.STANDARD);
  }

  @Test
  void invoicePrefixReadsFeeNote() {
    // Phase 88 625C (D12, R-8): stored key invoice.* (ADR-342: storage key, do not rename);
    // the display label is the legal "Fee note". Labels derive at read time, so history relabels.
    var resolved = registry.resolve("invoice.sent");
    assertThat(resolved.eventType()).isEqualTo("invoice.sent");
    assertThat(resolved.label()).isEqualTo("Fee note");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    assertThat(registry.entries())
        .filteredOn(e -> e.eventType().equals("invoice.*"))
        .singleElement()
        .satisfies(e -> assertThat(e.label()).isEqualTo("Fee note"));
    assertThat(registry.entries())
        .extracting(AuditEventTypeMetadata::label)
        .doesNotContain("Invoice");
  }

  @Test
  void defaultLabelLegalMapsTheFirstSegmentInSentenceCase() {
    // Phase 88 625C (D12, R-8): the six legal head mappings; the rest of the key keeps its words.
    assertThat(registry.resolve("project.updated").label()).isEqualTo("Matter updated");
    assertThat(registry.resolve("project.created_from_template").label())
        .isEqualTo("Matter created from template");
    assertThat(registry.resolve("customer.lifecycle.transitioned").label())
        .isEqualTo("Client lifecycle transitioned");
    assertThat(registry.resolve("project_member.added").label())
        .isEqualTo("Matter team member added");
    assertThat(registry.resolve("legal_disbursement.created").label())
        .isEqualTo("Disbursement created");
    assertThat(registry.resolve("project_budget.updated").label())
        .isEqualTo("Matter budget updated");
    // invoice.* never reaches the default (the prefix entry wins), but the helper maps it too.
    assertThat(AuditEventTypeRegistry.legalLabel("invoice.voided")).isEqualTo("Fee note voided");
    // Unmapped heads: underscores become spaces, sentence case.
    assertThat(registry.resolve("checklist.item.completed").label())
        .isEqualTo("Checklist item completed");
    // Entity-type literals (the facet labels share the helper — R-9).
    assertThat(AuditEventTypeRegistry.legalLabel("project")).isEqualTo("Matter");
    assertThat(AuditEventTypeRegistry.legalLabel("customer")).isEqualTo("Client");
    assertThat(AuditEventTypeRegistry.legalLabel("invoice")).isEqualTo("Fee note");
    assertThat(AuditEventTypeRegistry.legalLabel("legal_disbursement")).isEqualTo("Disbursement");
    assertThat(AuditEventTypeRegistry.legalLabel("project_member")).isEqualTo("Matter team member");
    assertThat(AuditEventTypeRegistry.legalLabel("project_budget")).isEqualTo("Matter budget");
    assertThat(AuditEventTypeRegistry.legalLabel("trust_account")).isEqualTo("Trust account");
    assertThat(AuditEventTypeRegistry.legalLabel("task")).isEqualTo("Task");
    assertThat(AuditEventTypeRegistry.legalLabel("")).isEmpty();
    assertThat(AuditEventTypeRegistry.legalLabel(null)).isEmpty();
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
    // 16 base entries + 1 ai.specialist.failed entry (Epic 515A; the other 6 removed in Phase 86
    // Epic 601, D2)
    // + 5 mcp.write.* entries (4 exact + 1 prefix, Epic 582A)
    // + 2 ai.gate.* entries (1 exact + 1 prefix, Epic 585)
    // + 5 collections.* entries (5 exact, Epic 588B)
    // + 1 disbursement.restored (exact, Phase 88 Epic 621A — additive ADR-342 key, OQ #10)
    // + 2 invoice.partially_paid / invoice.payment_excess (exact, Phase 88 Epic 622B — additive
    // ADR-342 keys, OQ #10)
    // + 1 acceptance.declined (exact, Phase 88 Epic 624F — additive ADR-342 key, OQ #10) = 33.
    // Default fallback is synthesised inside resolve() and must NOT appear in entries().
    // (Phase 86 slice 2 / Epic 596B removed the 5 deal.* SALES entries; Phase 86 slice 3 / Epic
    // 597B removed proposal.*; Phase 86 slice 7 / Epic 601 removed six ai.specialist.* entries.)
    assertThat(registry.entries()).hasSize(33);
    assertThat(registry.entries())
        .extracting(AuditEventTypeMetadata::eventType)
        .doesNotContain("(default fallback)")
        .contains(
            "matter.closure.override_used",
            "security.*",
            "trust.*",
            "dataprotection.*",
            "mcp.write.correspondence_filed",
            "mcp.write.*",
            "ai.gate.created",
            "ai.gate.*",
            "collections.policy.updated",
            "collections.reminder.sent",
            "ai.specialist.failed",
            "disbursement.restored",
            "invoice.partially_paid",
            "invoice.payment_excess",
            "acceptance.declined")
        .doesNotContain(
            "ai.specialist.*",
            "ai.specialist.invoked",
            "ai.specialist.approved",
            "ai.specialist.rejected",
            "ai.specialist.auto_applied",
            "ai.specialist.expired");
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
    assertThat(registry.entriesMatching(Set.of())).hasSize(33);
    assertThat(registry.entriesMatching(null)).hasSize(33);
  }

  @Test
  void invoicePartiallyPaidExactEntry() {
    // Phase 88 Epic 622B — additive stored key (ADR-342). Keeps the invoice.* parent's
    // INFO/FINANCIAL classification; only the label is explicit.
    var resolved = registry.resolve("invoice.partially_paid");
    assertThat(resolved.eventType()).isEqualTo("invoice.partially_paid");
    assertThat(resolved.label()).isEqualTo("Fee note partially paid");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.FINANCIAL);
  }

  @Test
  void invoicePaymentExcessExactEntry() {
    // Phase 88 Epic 622B — additive stored key (ADR-342). NOTICE: an external source moved more
    // money than the fee note owed and the excess must be reconciled.
    var resolved = registry.resolve("invoice.payment_excess");
    assertThat(resolved.eventType()).isEqualTo("invoice.payment_excess");
    assertThat(resolved.label()).isEqualTo("Fee note payment exceeded balance");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.NOTICE);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.FINANCIAL);
  }

  @Test
  void acceptanceDeclinedExactEntry() {
    // Phase 88 Epic 624F — additive stored key (ADR-342). Same INFO/STANDARD classification the
    // unregistered acceptance.* siblings resolve to; only the label is explicit.
    var resolved = registry.resolve("acceptance.declined");
    assertThat(resolved.eventType()).isEqualTo("acceptance.declined");
    assertThat(resolved.label()).isEqualTo("Acceptance declined");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.STANDARD);
  }

  @Test
  void disbursementRestoredExactEntry() {
    // Phase 88 Epic 621A — additive stored key (ADR-342). Same INFO/STANDARD classification the
    // unregistered disbursement.* siblings resolve to; only the label is explicit.
    var resolved = registry.resolve("disbursement.restored");
    assertThat(resolved.eventType()).isEqualTo("disbursement.restored");
    assertThat(resolved.label()).isEqualTo("Disbursement restored");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.STANDARD);
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
