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
 * writer of posting effects, and nothing outside the posting core saves a trust transaction — the
 * demo seeder included (R2). Main classes only (tests may set up rows directly).
 *
 * <p>The DB CHECK {@code chk_trust_txn_posting} (V16) is the runtime half: a posted status cannot
 * be stored without {@code posted_at}, which only {@code applyPosting} sets. A posted status passed
 * to the {@link TrustTransaction} constructor is invisible to ArchUnit — the status is a {@code
 * String} argument — so that case is left to the CHECK.
 *
 * <p>Rule (b) allows {@code ..trustaccounting.transaction..} (the posting core and the service that
 * drives it) and {@code ..trustaccounting.reconciliation..}, which saves posted rows only to set
 * {@code bank_statement_line_id} — no effect changes.
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
  static final ArchRule trust_rows_are_saved_only_by_the_posting_core =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "..trustaccounting.transaction..", "..trustaccounting.reconciliation..")
          .should()
          .callMethodWhere(
              target(owner(assignableTo(TrustTransactionRepository.class)))
                  .and(target(nameStartingWith("save"))))
          .because(
              "rows are posted through TrustPostingService; reconciliation only links lines"
                  + " (R2, ADR-378)");
}
