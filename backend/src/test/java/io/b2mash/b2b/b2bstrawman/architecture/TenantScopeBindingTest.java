package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Bans the duplicated tenant-scope-binding helper method names that were consolidated into {@code
 * RequestScopes.runForTenant} / {@code callForTenant} on 2026-05-02 (ADR-T008 PR #1), plus direct
 * {@code ScopedValue.where(...)} calls outside the canonical {@code RequestScopes} API on
 * 2026-05-02 (ADR-T008 PR #2 / Surface 2).
 *
 * <p>If a future contributor reintroduces a method named {@code handleInTenantScope}, {@code
 * runInTenantScope}, or {@code executeInTenantScope} anywhere outside the {@code multitenancy}
 * package, this rule fails the build. Bind tenant scope via the canonical static API on {@code
 * RequestScopes} — see ADR-T008.
 *
 * <p>{@code withTenantScope} is intentionally NOT banned: ADR-204 reserves that name for a proposed
 * (deferred) {@code RequestScopes.withCurrentScopes()} capture-and-rebind utility.
 *
 * <p><b>Companion rule (PR #2):</b> {@link
 * #no_direct_scopedvalue_binding_outside_multitenancy_and_exempt} bans direct {@code
 * ScopedValue.where(...)} calls outside the {@code multitenancy} package and a documented exemption
 * set. The exemption set captures the boundary-binders (servlet filter, virtual-thread
 * capture-rebind, dev tooling, cross-tenant search) that cannot be expressed as {@code
 * runForTenant} today; they await ADR-204's {@code withCurrentScopes()} for a sanctioned API.
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

  /**
   * PR #2 / ADR-T008 Surface 2 companion guard.
   *
   * <p>Bans direct {@code ScopedValue.where(...)} method calls in classes outside the {@code
   * ..multitenancy..} package and outside the documented exemption set. Catches re-introduction of
   * the inline binding pattern that PR #2 just consolidated to {@code RequestScopes.runForTenant} /
   * {@code callForTenant} / {@code runForTenantWithMember} / {@code
   * TenantScopedRunner.forEachTenant}.
   *
   * <p><b>Why broader than TENANT_ID-specific:</b> the precise rule "ban {@code
   * ScopedValue.where(RequestScopes.TENANT_ID, ...)}" requires first-argument-value introspection
   * that ArchUnit's DSL doesn't expose cleanly. A custom {@code ArchCondition} could approximate it
   * via combined "calls {@code ScopedValue.where} AND reads {@code RequestScopes.TENANT_ID} field"
   * checks, but is fragile under JDK-25 bytecode evolution. The broader "ban all {@code
   * ScopedValue.where} outside exemption set" form has identical semantics in this codebase today —
   * every non-multitenancy {@code ScopedValue.where} call binds {@code TENANT_ID} (with the
   * documented exemptions) — and is robust to JDK changes. See spec §ArchUnit Conditional deferral
   * (Fallback A).
   *
   * <p><b>Exemption catalogue</b> (each entry MUST have a corresponding ADR-T008 entry — adding a
   * new exemption requires explicit ADR amendment):
   *
   * <ul>
   *   <li>{@code ..multitenancy..} package — owns the canonical APIs (RequestScopes,
   *       TenantScopedRunner) and the boundary-binding TenantFilter.
   *   <li>{@code ..dev..} package — profile-gated dev test harness ({@code DevPortalController}).
   *   <li>{@code CustomerAuthFilter} — servlet filter; binding from JWT IS the request boundary
   *       (multi-binding CUSTOMER_ID + TENANT_ID + ORG_ID + conditional PORTAL_CONTACT_ID).
   *   <li>{@code AssistantController} — 5-binding capture-and-rebind to bridge servlet thread →
   *       virtual thread for SSE LLM streaming. Awaits ADR-204's {@code withCurrentScopes()}.
   *   <li>{@code MockPaymentController} — profile-gated dev payment mock; site at line 182 is also
   *       a cross-tenant invoice search (find-which-owns).
   *   <li>{@code MemberFilter} — servlet filter; binds {@code MEMBER_ID} + {@code ORG_ROLE} from a
   *       tenant-scoped member lookup. Filter boundary, like {@code CustomerAuthFilter}.
   *   <li>{@code PlatformAdminFilter} — servlet filter; binds {@code GROUPS} from JWT claims.
   *       Filter boundary.
   *   <li>{@code AutomationActionExecutor} — binds {@code AUTOMATION_EXECUTION_ID} on the
   *       scheduler→action-execution boundary. Not a filter, but the same boundary-binder pattern.
   * </ul>
   */
  @ArchTest
  static final ArchRule no_direct_scopedvalue_binding_outside_multitenancy_and_exempt =
      noClasses()
          .that()
          .resideOutsideOfPackage("..multitenancy..")
          .and()
          .resideOutsideOfPackage("..dev..")
          .and()
          .doNotHaveFullyQualifiedName("io.b2mash.b2b.b2bstrawman.portal.CustomerAuthFilter")
          .and()
          .doNotHaveFullyQualifiedName("io.b2mash.b2b.b2bstrawman.assistant.AssistantController")
          .and()
          .doNotHaveFullyQualifiedName(
              "io.b2mash.b2b.b2bstrawman.integration.payment.MockPaymentController")
          .and()
          .doNotHaveFullyQualifiedName("io.b2mash.b2b.b2bstrawman.member.MemberFilter")
          .and()
          .doNotHaveFullyQualifiedName("io.b2mash.b2b.b2bstrawman.security.PlatformAdminFilter")
          .and()
          .doNotHaveFullyQualifiedName(
              "io.b2mash.b2b.b2bstrawman.automation.AutomationActionExecutor")
          .should()
          .callMethod(
              java.lang.ScopedValue.class, "where", java.lang.ScopedValue.class, Object.class)
          .because(
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                  + "runForTenantWithMember or TenantScopedRunner.forEachTenant. "
                  + "See ADR-T008. Adding a new exemption to this rule requires explicit "
                  + "ADR-T008 amendment. Exemptions use fully-qualified class names so a future "
                  + "class with the same simple name in a different package is not accidentally "
                  + "exempted.")
          .allowEmptyShould(true);
}
