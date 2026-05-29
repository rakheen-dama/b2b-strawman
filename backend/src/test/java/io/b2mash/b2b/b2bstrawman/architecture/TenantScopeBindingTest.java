package io.b2mash.b2b.b2bstrawman.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

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
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                  + "runForTenantOnShard / callForTenantOnShard. "
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
          .because(
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                  + "runForTenantOnShard / callForTenantOnShard. See ADR-T008.")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_executeInTenantScope_outside_multitenancy =
      methods()
          .that()
          .haveName("executeInTenantScope")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..multitenancy..")
          .because(
              "Bind tenant scope via RequestScopes.runForTenant / callForTenant / "
                  + "runForTenantOnShard / callForTenantOnShard. See ADR-T008.")
          .allowEmptyShould(true);

  /**
   * PR #2 / ADR-T008 Surface 2 companion guard.
   *
   * <p>Bans direct {@code ScopedValue.where(...)} method calls in classes outside the {@code
   * ..multitenancy..} package and outside the documented exemption set. Catches re-introduction of
   * the inline binding pattern that PR #2 just consolidated to {@code RequestScopes.runForTenant} /
   * {@code callForTenant} / {@code runForTenantOnShard} / {@code callForTenantOnShard} / {@code
   * runForTenantWithMember} / {@code TenantScopedRunner.forEachTenant}.
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
                  + "runForTenantOnShard / callForTenantOnShard / "
                  + "runForTenantWithMember or TenantScopedRunner.forEachTenant. "
                  + "See ADR-T008. Adding a new exemption to this rule requires explicit "
                  + "ADR-T008 amendment. Exemptions use fully-qualified class names so a future "
                  + "class with the same simple name in a different package is not accidentally "
                  + "exempted.")
          .allowEmptyShould(true);

  // ---------------------------------------------------------------------------
  // D5 (kazi-infra-review-scheduling-sharding.md): shard-unaware tenant binding
  // ---------------------------------------------------------------------------

  /**
   * RequestScopes binders that do NOT bind SHARD_ID — they default routing to the primary shard.
   */
  private static final Set<String> SHARD_UNAWARE_BINDERS =
      Set.of("runForTenant", "callForTenant", "runForTenantWithMember");

  private static final String REQUEST_SCOPES_FQN =
      "io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes";

  /**
   * Classes that still call a shard-unaware binder as of the D5 guard's introduction (2026-05-29).
   * These are latent wrong-shard routes once secondary shards go live and are tracked in {@code
   * documentation/tech-debt.md} (D5). The set may only SHRINK — new code must use {@code
   * runForTenantOnShard} / {@code callForTenantOnShard}. Most are
   * {@code @TransactionalEventListener} handlers whose domain events do not carry a shard id yet
   * (migrating them requires threading shardId through the event payloads).
   */
  private static final Set<String> D5_GRANDFATHERED =
      Set.of(
          "io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceService",
          "io.b2mash.b2b.b2bstrawman.audit.InternalAuditController",
          "io.b2mash.b2b.b2bstrawman.audit.export.AuditExportService",
          "io.b2mash.b2b.b2bstrawman.customerbackend.handler.PortalEventHandler",
          "io.b2mash.b2b.b2bstrawman.customerbackend.service.DeadlinePortalSyncService",
          "io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalResyncService",
          "io.b2mash.b2b.b2bstrawman.customerbackend.service.RetainerPortalSyncService",
          "io.b2mash.b2b.b2bstrawman.customerbackend.service.TrustLedgerPortalSyncService",
          "io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEmailEventListener",
          "io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestNotificationEventListener",
          "io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEventListener",
          "io.b2mash.b2b.b2bstrawman.integration.email.EmailWebhookService",
          "io.b2mash.b2b.b2bstrawman.integration.email.UnsubscribeService",
          "io.b2mash.b2b.b2bstrawman.invoice.InvoiceEmailEventListener",
          "io.b2mash.b2b.b2bstrawman.member.MemberSyncService",
          "io.b2mash.b2b.b2bstrawman.notification.NotificationEventHandler",
          "io.b2mash.b2b.b2bstrawman.packs.PackInstallService",
          "io.b2mash.b2b.b2bstrawman.portal.CustomerAuthFilter",
          "io.b2mash.b2b.b2bstrawman.portal.PortalBrandingController",
          "io.b2mash.b2b.b2bstrawman.portal.PortalDocumentNotificationHandler",
          "io.b2mash.b2b.b2bstrawman.portal.notification.PortalEmailNotificationChannel",
          "io.b2mash.b2b.b2bstrawman.proposal.ProposalAcceptedEventHandler",
          "io.b2mash.b2b.b2bstrawman.proposal.ProposalExpiredEventHandler",
          "io.b2mash.b2b.b2bstrawman.proposal.ProposalPortalSyncEventHandler",
          "io.b2mash.b2b.b2bstrawman.proposal.ProposalSentEmailHandler",
          "io.b2mash.b2b.b2bstrawman.provisioning.PackReconciliationRunner",
          "io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustNotificationHandler");

  private static final DescribedPredicate<JavaClass> OUTSIDE_MULTITENANCY_AND_NOT_GRANDFATHERED =
      new DescribedPredicate<>("outside ..multitenancy.. and not a D5-grandfathered class") {
        @Override
        public boolean test(JavaClass clazz) {
          return !clazz.getPackageName().contains(".multitenancy")
              && !D5_GRANDFATHERED.contains(clazz.getFullName());
        }
      };

  private static final DescribedPredicate<JavaMethodCall> A_SHARD_UNAWARE_REQUEST_SCOPES_BINDER =
      new DescribedPredicate<>("a shard-unaware RequestScopes tenant binding") {
        @Override
        public boolean test(JavaMethodCall call) {
          var target = call.getTarget();
          return REQUEST_SCOPES_FQN.equals(target.getOwner().getFullName())
              && SHARD_UNAWARE_BINDERS.contains(target.getName());
        }
      };

  /**
   * D5 guard: new code outside the grandfathered set must not call the shard-unaware binders
   * ({@code runForTenant} / {@code callForTenant} / {@code runForTenantWithMember}). Use {@code
   * runForTenantOnShard} / {@code callForTenantOnShard} (or {@code
   * TenantScopedRunner.forEachTenant}, which is shard-aware) so a job/handler for a secondary-shard
   * tenant routes to the correct database instead of defaulting to primary. To migrate a
   * grandfathered class, remove it from {@link #D5_GRANDFATHERED} once its shard id is available at
   * the call site.
   */
  @ArchTest
  static final ArchRule no_new_shard_unaware_tenant_binding =
      noClasses()
          .that(OUTSIDE_MULTITENANCY_AND_NOT_GRANDFATHERED)
          .should()
          .callMethodWhere(A_SHARD_UNAWARE_REQUEST_SCOPES_BINDER)
          .because(
              "shard-unaware RequestScopes binders leave SHARD_ID unbound (routing defaults to the "
                  + "primary shard). New code must use runForTenantOnShard / callForTenantOnShard. "
                  + "Grandfathered classes are tracked in documentation/tech-debt.md (D5) and must "
                  + "be migrated before enabling secondary shards in production.")
          .allowEmptyShould(true);
}
