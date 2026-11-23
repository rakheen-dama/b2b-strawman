package io.b2mash.b2b.b2bstrawman.takeon;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase 93 (§17.8.1, task 653.10): take-on writes enter the books only through the take-on commit
 * runners — the take-on-only entry points are called from {@code ..takeon..} and nowhere else. Main
 * classes only. The rule passed vacuously in 653A: each later slice adds its class here when it
 * creates it — 655A {@code TakenOnCustomerService}, 657A {@code TrustPostingService.recordOpening}
 * (and {@code OpeningAuthority.forBatch}: only {@code ..takeon..} builds the authority opening
 * entries post under), 657C {@code TrustOpeningPositionService}, 658A {@code
 * BroughtForwardFeeNoteService}. {@link #thePredicatesMatchTheNamesTheyGuard} proves the predicates
 * bite before any class exists; {@link #theGuardedTakeOnMethodsExist} that a rename of the guarded
 * methods cannot make the rule pass silently.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class TakeOnEntryPointsArchTest {

  /**
   * The take-on-only entry points (§17.8.1). Each later slice adds its class when it creates it.
   */
  static final List<String> TAKE_ON_ONLY_CLASSES =
      List.of(
          "io.b2mash.b2b.b2bstrawman.customer.TakenOnCustomerService", // 655A
          "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon"
              + ".TrustOpeningPositionService", // 657C
          "io.b2mash.b2b.b2bstrawman.invoice.BroughtForwardFeeNoteService"); // 658A

  static final String TRUST_POSTING_SERVICE =
      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService";

  static final String OPENING_AUTHORITY =
      "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.OpeningAuthority";

  /** A class and its nested classes (a class may call itself). */
  static DescribedPredicate<HasName> nameStartingWithAny(List<String> names) {
    return DescribedPredicate.describe(
        "name starting with any of " + names,
        c ->
            names.stream().anyMatch(n -> c.getName().equals(n) || c.getName().startsWith(n + "$")));
  }

  static DescribedPredicate<HasName> nameIn(List<String> names) {
    return DescribedPredicate.describe("name in " + names, c -> names.contains(c.getName()));
  }

  @ArchTest
  static final ArchRule take_on_entry_points_are_called_only_from_takeon =
      noClasses()
          .that()
          .resideOutsideOfPackage("..takeon..")
          .and(DescribedPredicate.not(nameStartingWithAny(TAKE_ON_ONLY_CLASSES)))
          .should()
          .callMethodWhere(target(owner(nameIn(TAKE_ON_ONLY_CLASSES))))
          .orShould()
          .callMethodWhere(
              target(owner(name(TRUST_POSTING_SERVICE))).and(target(name("recordOpening"))))
          // 657A: only the take-on commit may build the authority opening entries post under
          .orShould()
          .callMethodWhere(target(owner(name(OPENING_AUTHORITY))).and(target(name("forBatch"))))
          .allowEmptyShould(true)
          .because(
              "take-on writes enter only through the take-on commit runners (architecture"
                  + " §17.8.1)");

  @Test
  void thePredicatesMatchTheNamesTheyGuard() {
    JavaClasses imported =
        new ClassFileImporter().importClasses(TakeOnEntryPointsArchTest.class, Nested.class);
    JavaClass self = imported.get(TakeOnEntryPointsArchTest.class);
    JavaClass nested = imported.get(Nested.class);
    var guardSelf = List.of(TakeOnEntryPointsArchTest.class.getName());
    assertThat(nameIn(guardSelf).test(self)).isTrue();
    assertThat(nameIn(guardSelf).test(nested)).isFalse();
    assertThat(nameStartingWithAny(guardSelf).test(nested))
        .as("nested classes may call home")
        .isTrue();
    assertThat(nameStartingWithAny(TAKE_ON_ONLY_CLASSES).test(self)).isFalse();
    assertThat(TAKE_ON_ONLY_CLASSES).allMatch(n -> n.startsWith("io.b2mash.b2b.b2bstrawman."));
  }

  /** 657A, 655A: the guarded methods exist, so the rule above is not vacuous for them. */
  @Test
  void theGuardedTakeOnMethodsExist() throws Exception {
    assertThat(Class.forName(TAKE_ON_ONLY_CLASSES.getFirst()).getMethods())
        .as("655A: TakenOnCustomerService.createTakenOn")
        .anyMatch(m -> m.getName().equals("createTakenOn"));
    assertThat(Class.forName(TRUST_POSTING_SERVICE).getMethods())
        .anyMatch(m -> m.getName().equals("recordOpening"));
    assertThat(Class.forName(OPENING_AUTHORITY).getMethods())
        .anyMatch(m -> m.getName().equals("forBatch"));
  }

  static final class Nested {}
}
