package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Bans the duplicated tenant-scope-binding helper method names that were consolidated into {@code
 * RequestScopes.runForTenant} / {@code callForTenant} on 2026-05-02.
 *
 * <p>If a future contributor reintroduces a method named {@code handleInTenantScope}, {@code
 * runInTenantScope}, or {@code executeInTenantScope} anywhere outside the {@code multitenancy}
 * package, this rule fails the build. Bind tenant scope via the canonical static API on {@code
 * RequestScopes} — see ADR-T008.
 *
 * <p>{@code withTenantScope} is intentionally NOT banned: ADR-204 reserves that name for a proposed
 * (deferred) {@code RequestScopes.withCurrentScopes()} capture-and-rebind utility.
 *
 * <p>The companion rule banning direct {@code ScopedValue.where(RequestScopes.TENANT_ID, ...)}
 * calls outside {@code RequestScopes} ships with PR #2 (jobs migration) — it cannot ship now
 * without breaking the 13 unmigrated scheduled jobs.
 */
@AnalyzeClasses(
    packages = "io.b2mash.b2b.b2bstrawman",
    importOptions = ImportOption.DoNotIncludeTests.class)
class TenantScopeBindingTest {

  @ArchTest
  static final ArchRule no_handleInTenantScope_outside_multitenancy =
      methods()
          .that()
          .haveName("handleInTenantScope")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..multitenancy..")
          .because(
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant. "
                  + "Adding a private handleInTenantScope helper recreates the duplication "
                  + "this rule prevents. See ADR-T008.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_runInTenantScope_outside_multitenancy =
      methods()
          .that()
          .haveName("runInTenantScope")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..multitenancy..")
          .because("Bind tenant scope via RequestScopes.runForTenant. See ADR-T008.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_executeInTenantScope_outside_multitenancy =
      methods()
          .that()
          .haveName("executeInTenantScope")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..multitenancy..")
          .because("Bind tenant scope via RequestScopes.runForTenant. See ADR-T008.")
          .allowEmptyShould(true);
}
