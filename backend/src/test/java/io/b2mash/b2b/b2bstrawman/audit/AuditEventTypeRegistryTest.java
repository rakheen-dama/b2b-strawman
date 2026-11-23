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
  void recordFromStatementKeys_haveTheirFidelityFundLabels() {
    // Phase 92 647A (D5): explicit entries, because the synthesised label would read "Trust fund
    // interest recorded" — "trust fund" is not the Legal Practitioners' Fidelity Fund.
    var resolved = registry.resolve("trust_fund_interest.recorded");
    assertThat(resolved.label()).isEqualTo("Fidelity Fund interest recorded");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    assertThat(registry.resolve("trust_fund_sweep.recorded").label())
        .isEqualTo("Fidelity Fund payment recorded");
    assertThat(registry.resolve("trust_bank_charge.recorded").label())
        .isEqualTo("Bank charge recorded");
    assertThat(registry.resolve("trust_charge_refund.recorded").label())
        .isEqualTo("Bank charge refund recorded");
  }

  @Test
  void investmentInterestKeys_haveTheirLabels() {
    // Phase 92 648B (OQ-16): explicit entries — the synthesised labels would read "Trust inv
    // interest recorded" / "Trust investment interest_recorded".
    var row = registry.resolve("trust_inv_interest.recorded");
    assertThat(row.label()).isEqualTo("Section 86(4) investment interest recorded");
    assertThat(row.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(row.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    var share = registry.resolve("trust_fund_inv_share.recorded");
    assertThat(share.label()).isEqualTo("Fidelity Fund share of section 86(4) interest recorded");
    assertThat(share.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    var recorded = registry.resolve("trust_investment.interest_recorded");
    assertThat(recorded.label()).isEqualTo("Section 86(4) interest entry recorded");
    assertThat(recorded.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(recorded.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    var reversed = registry.resolve("trust_investment.interest_reversed");
    assertThat(reversed.label()).isEqualTo("Section 86(4) interest entry reversed");
    assertThat(reversed.group()).isEqualTo(AuditEventGroup.FINANCIAL);
  }

  @Test
  void unidentifiedDepositKeys_haveTheirLabels() {
    // Phase 92 647B: explicit entries (as 647A D5) — the row recorded from the statement, and the
    // item it opens and its reversal when the line is unmatched.
    var recorded = registry.resolve("trust_unidentified_deposit.recorded");
    assertThat(recorded.label()).isEqualTo("Unidentified deposit recorded");
    assertThat(recorded.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(recorded.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    var opened = registry.resolve("unidentified_deposit.opened");
    assertThat(opened.label()).isEqualTo("Unidentified deposit held");
    assertThat(opened.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(opened.group()).isEqualTo(AuditEventGroup.FINANCIAL);
    var reversed = registry.resolve("unidentified_deposit.reversed");
    assertThat(reversed.label()).isEqualTo("Unidentified deposit reversed");
    assertThat(reversed.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(reversed.group()).isEqualTo(AuditEventGroup.FINANCIAL);
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
    // G88-F17: each published key carries its own "Fee note …" label; the family entry keeps the
    // bare "Fee note" as the fallback for uncatalogued keys.
    var resolved = registry.resolve("invoice.sent");
    assertThat(resolved.eventType()).isEqualTo("invoice.sent");
    assertThat(resolved.label()).isEqualTo("Fee note sent");
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

  /**
   * Keys a publisher actually emits under a wildcard family (grep of {@code .eventType("...")}):
   * invoice.* from InvoiceCreationService / InvoiceTransitionService / PaymentReceiptService,
   * security.* from GlobalExceptionHandler / DocumentService, ai.gate.* from
   * AiExecutionGateService.
   */
  private static final List<String> PUBLISHED_WILDCARD_FAMILY_KEYS =
      List.of(
          "invoice.created",
          "invoice.updated",
          "invoice.deleted",
          "invoice.custom_fields_updated",
          "invoice.field_groups_updated",
          "invoice.approved",
          "invoice.sent",
          "invoice.paid",
          "invoice.partially_paid",
          "invoice.payment_excess",
          "invoice.payment_reversed",
          "invoice.payment_partially_reversed",
          "invoice.voided",
          "invoice.receipt_generated",
          "security.access_denied",
          "security.document_accessed",
          "ai.gate.created",
          "ai.gate.approved",
          "ai.gate.rejected",
          "ai.gate.expired");

  @Test
  void everyPublishedKeyUnderAWildcardFamilyHasItsOwnLabel() {
    // G88-F17: the audit Event-type combobox shows labels only (G-D10b), so two published keys
    // that resolve to the same family label ("Fee note", "Security Event", "AI Execution Gate
    // Activity") are indistinguishable. Every key a publisher emits under a wildcard family must
    // resolve to a distinct label. Stored keys are unchanged (ADR-342) — UI words only.
    var labels =
        PUBLISHED_WILDCARD_FAMILY_KEYS.stream().map(k -> registry.resolve(k).label()).toList();
    assertThat(labels).doesNotHaveDuplicates();
    assertThat(labels)
        .doesNotContain("Fee note", "Security Event", "AI Execution Gate Activity", "Invoice");
    assertThat(registry.resolve("invoice.created").label()).isEqualTo("Fee note created");
    assertThat(registry.resolve("invoice.sent").label()).isEqualTo("Fee note sent");
  }

  @Test
  void perKeyLabelsKeepTheFamilyClassification() {
    // G88-F17: the per-key labels must not reclassify history — each published key keeps the
    // severity and group its wildcard family gives an uncatalogued sibling. invoice.payment_excess
    // is the one pre-existing NOTICE exception (Phase 88 Epic 622B, ADR-347).
    for (var key : PUBLISHED_WILDCARD_FAMILY_KEYS) {
      if (key.equals("invoice.payment_excess")) {
        continue;
      }
      var sibling = registry.resolve(key.substring(0, key.lastIndexOf('.')) + ".zz_uncatalogued");
      var resolved = registry.resolve(key);
      assertThat(resolved.severity()).as(key).isEqualTo(sibling.severity());
      assertThat(resolved.group()).as(key).isEqualTo(sibling.group());
    }
  }

  @Test
  void unknownFutureInvoiceKeyStillFallsBackToTheFeeNoteFamily() {
    // G88-F17: the invoice.* wildcard stays as the fallback for keys not yet catalogued.
    var resolved = registry.resolve("invoice.some_future_event");
    assertThat(resolved.label()).isEqualTo("Fee note");
    assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
    assertThat(resolved.group()).isEqualTo(AuditEventGroup.FINANCIAL);
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
    // invoice.* never reaches the default (an exact or prefix entry wins), but the helper maps it.
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
  void securityAccessDenied_labelIsSentenceCase() {
    // G88-F27 / G-D10c: the audit Event-type filter showed "Access Denied" in Title Case.
    assertThat(registry.resolve("security.access_denied").label()).isEqualTo("Access denied");
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
    // + 1 acceptance.declined (exact, Phase 88 Epic 624F — additive ADR-342 key, OQ #10)
    // + 16 per-key labels under wildcard families (G88-F17: 11 invoice.*, 2 security.*,
    // 3 ai.gate.* — exact, same classification as their prefix; stored keys unchanged)
    // + 1 invoice.receipt_generated (exact, Phase 89 632B — additive ADR-342 key)
    // + 1 trust_creditor_account.created (Phase 92 646B) = 51.
    // + 4 trust_fund_interest/_fund_sweep/_bank_charge/_charge_refund.recorded (Phase 92 647A) =
    // 55.
    // + 3 trust_unidentified_deposit.recorded, unidentified_deposit.opened / .reversed (Phase 92
    // 647B) = 58.
    // + 7 unidentified_deposit.allocation_requested/allocated/allocation_rejected/
    // payment_requested/paid_to_fund/payment_rejected/reopened and 6
    // trust_unident_alloc_out.* / trust_unident_fund_pay.* (first_approved, approved, rejected)
    // (Phase 92 647D) = 71.
    // + 6 trust_inv_place.* / trust_firm_inv_place.* (first_approved, approved, rejected)
    // (Phase 92 648A) = 77.
    // + 4 trust_inv_interest.recorded, trust_fund_inv_share.recorded,
    // trust_investment.interest_recorded / .interest_reversed (Phase 92 648B) = 81.
    // + 1 trust_monthly_list.locked (Phase 92 650A) = 82.
    // + 1 trust_interest_memo.saved (Phase 92 650B) = 83.
    // + 6 takeon.batch.created/.validated/.cancelled, takeon.source.uploaded/.mapped,
    // takeon.file.downloaded (Phase 93 653A) = 89.
    // Default fallback is synthesised inside resolve() and must NOT appear in entries().
    // (Phase 86 slice 2 / Epic 596B removed the 5 deal.* SALES entries; Phase 86 slice 3 / Epic
    // 597B removed proposal.*; Phase 86 slice 7 / Epic 601 removed six ai.specialist.* entries.)
    assertThat(registry.entries()).hasSize(89);
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
            "acceptance.declined",
            "unidentified_deposit.allocation_requested",
            "unidentified_deposit.allocated",
            "unidentified_deposit.allocation_rejected",
            "unidentified_deposit.payment_requested",
            "unidentified_deposit.paid_to_fund",
            "unidentified_deposit.payment_rejected",
            "unidentified_deposit.reopened",
            "trust_unident_alloc_out.first_approved",
            "trust_unident_alloc_out.approved",
            "trust_unident_alloc_out.rejected",
            "trust_unident_fund_pay.first_approved",
            "trust_unident_fund_pay.approved",
            "trust_unident_fund_pay.rejected",
            "trust_inv_place.first_approved",
            "trust_inv_place.approved",
            "trust_inv_place.rejected",
            "trust_firm_inv_place.first_approved",
            "trust_firm_inv_place.approved",
            "trust_firm_inv_place.rejected",
            "takeon.batch.created",
            "takeon.source.uploaded",
            "takeon.source.mapped",
            "takeon.batch.validated",
            "takeon.batch.cancelled",
            "takeon.file.downloaded")
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
  void takeOnEventsAreDataGroupAndOnlyTheRawFileDownloadIsNotice() {
    // Phase 93 653A (C-3): no "Take-on" group exists — the take-on events are the processing of
    // personal information under POPIA, so DATA; reading a raw file is the one NOTICE.
    for (String key :
        List.of(
            "takeon.batch.created",
            "takeon.source.uploaded",
            "takeon.source.mapped",
            "takeon.batch.validated",
            "takeon.batch.cancelled")) {
      var resolved = registry.resolve(key);
      assertThat(resolved.eventType()).isEqualTo(key);
      assertThat(resolved.group()).isEqualTo(AuditEventGroup.DATA);
      assertThat(resolved.severity()).isEqualTo(AuditSeverity.INFO);
      assertThat(resolved.label()).startsWith("Take-on ");
    }
    var downloaded = registry.resolve("takeon.file.downloaded");
    assertThat(downloaded.group()).isEqualTo(AuditEventGroup.DATA);
    assertThat(downloaded.severity()).isEqualTo(AuditSeverity.NOTICE);
    assertThat(downloaded.label()).isEqualTo("Take-on file downloaded");
  }

  @Test
  void entriesMatchingWithEmptyOrNullReturnsAll() {
    assertThat(registry.entriesMatching(Set.of())).hasSize(89);
    assertThat(registry.entriesMatching(null)).hasSize(89);
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
