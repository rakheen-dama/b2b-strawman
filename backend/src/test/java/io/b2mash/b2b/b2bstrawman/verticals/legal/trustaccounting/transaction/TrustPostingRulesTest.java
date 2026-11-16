package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Phase 92 slice 646A (task 646.3, ADR-378, §16.2.2): {@link TrustPostingService} is the only
 * writer of a trust transaction. Two rules, over main classes only (tests may set up rows
 * directly):
 *
 * <ul>
 *   <li>(a) no class but {@code TrustPostingService} calls {@code TrustTransaction.applyPosting} —
 *       the only setter of the effect columns and {@code posted_at};
 *   <li>(b) no class but {@code TrustPostingService} calls {@code TrustTransactionRepository.save*}
 *       ({@code save}, {@code saveAll}, {@code saveAndFlush}, …) — not the transaction service, not
 *       reconciliation, not the demo seeder (R2). Unposted state changes go through {@code
 *       recordFirstApproval} / {@code reject}, and reconciliation's bank-line link through {@code
 *       linkBankLine}; none of them touches effects or {@code posted_at}.
 * </ul>
 *
 * <p>Not enforced here: Hibernate dirty-checking flushes a managed row changed without a {@code
 * save} call, and a posted status passed to the {@link TrustTransaction} constructor is a {@code
 * String} argument — both are invisible to ArchUnit. The DB CHECK {@code chk_trust_txn_posting}
 * (V16) is the runtime half: a posted status cannot be stored without {@code posted_at}, which only
 * {@code applyPosting} sets.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class TrustPostingRulesTest {

  @ArchTest
  static final ArchRule only_the_posting_service_applies_effects =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(TrustPostingService.class.getName())
          .should()
          .callMethodWhere(
              target(owner(equivalentTo(TrustTransaction.class))).and(target(name("applyPosting"))))
          .because(
              "posting effects are written once, by TrustPostingService, from the registry"
                  + " (ADR-378)");

  @ArchTest
  static final ArchRule trust_rows_are_saved_only_by_the_posting_service =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(TrustPostingService.class.getName())
          .should()
          .callMethodWhere(
              target(owner(assignableTo(TrustTransactionRepository.class)))
                  .and(target(nameStartingWith("save"))))
          .because(
              "trust rows are saved only by TrustPostingService — postings, unposted state"
                  + " changes and bank-line links alike (R2, ADR-378)");
}
