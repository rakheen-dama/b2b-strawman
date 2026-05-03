package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;

/**
 * Integration tests for the {@link AuditEventFilter#severities()} pre-flight (502.4) covering the
 * three architecture §12.3.5 cases:
 *
 * <ul>
 *   <li>{@code severities = {CRITICAL}} → only {@code matter.closure.override_used} (the exact
 *       CRITICAL entry); the prefix-resolved {@code matter.closure.completed} (NOTICE) is excluded.
 *   <li>{@code severities = {NOTICE}} → prefix-matched rows under {@code matter.closure.*}
 *       included, but the CRITICAL exact-string {@code matter.closure.override_used} is excluded
 *       via the prefix-vs-exact conflict logic.
 *   <li>{@code severities = {INFO}} → INFO-registered prefixes (invoice.*, proposal.*) AND
 *       default-fallback rows (eventTypes not in the registry, e.g. {@code task.created}) are
 *       returned.
 * </ul>
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditServiceSeverityFilterIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_severity_filter_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditServiceSeverityFilterIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;

  @BeforeAll
  void provisionTenantAndSeed() {
    schemaName =
        provisioningSvc
            .provisionTenant(ORG_ID, "Audit Severity Filter Test Org", null)
            .schemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // CRITICAL exact: matter.closure.override_used
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.override_used",
                      "matter",
                      UUID.randomUUID(),
                      null,
                      "SYSTEM",
                      "API",
                      null,
                      null,
                      null));
              // NOTICE via matter.closure.* prefix: matter.closure.completed
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.completed",
                      "matter",
                      UUID.randomUUID(),
                      null,
                      "SYSTEM",
                      "API",
                      null,
                      null,
                      null));
              // INFO via invoice.* prefix
              auditService.log(
                  new AuditEventRecord(
                      "invoice.created",
                      "invoice",
                      UUID.randomUUID(),
                      null,
                      "SYSTEM",
                      "API",
                      null,
                      null,
                      null));
              // Default-fallback (not in registry) ⇒ INFO via defaultFor()
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      null,
                      "SYSTEM",
                      "API",
                      null,
                      null,
                      null));
              // WARNING exact: security.login.failure (used to verify it's NOT included on NOTICE)
              auditService.log(
                  new AuditEventRecord(
                      "security.login.failure",
                      "security",
                      UUID.randomUUID(),
                      null,
                      "SYSTEM",
                      "API",
                      null,
                      null,
                      null));
            });
  }

  @Test
  void criticalSeverityReturnsOnlyOverrideUsed() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          null, null, null, null, null, null, Set.of(AuditSeverity.CRITICAL)),
                      PageRequest.of(0, 50));

              assertThat(page.getContent()).isNotEmpty();
              assertThat(page.getContent())
                  .allMatch(e -> e.getEventType().equals("matter.closure.override_used"));
              // The completed row is NOTICE — must not appear.
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("matter.closure.completed"));
            });
  }

  @Test
  void noticeSeverityIncludesPrefixButExcludesCriticalExact() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          null, null, null, null, null, null, Set.of(AuditSeverity.NOTICE)),
                      PageRequest.of(0, 50));

              assertThat(page.getContent())
                  .anyMatch(e -> e.getEventType().equals("matter.closure.completed"));
              // CRITICAL exact must be excluded by the prefix-vs-exact conflict resolver.
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("matter.closure.override_used"));
              // WARNING exact must not appear either.
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("security.login.failure"));
              // INFO rows must not appear.
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("invoice.created"));
              assertThat(page.getContent()).noneMatch(e -> e.getEventType().equals("task.created"));
            });
  }

  @Test
  void infoSeverityIncludesRegisteredAndDefaultFallback() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          null, null, null, null, null, null, Set.of(AuditSeverity.INFO)),
                      PageRequest.of(0, 50));

              // invoice.created falls under invoice.* (INFO) — registered prefix.
              assertThat(page.getContent())
                  .anyMatch(e -> e.getEventType().equals("invoice.created"));
              // task.created is unregistered → defaults to INFO via defaultFor() — must appear.
              assertThat(page.getContent()).anyMatch(e -> e.getEventType().equals("task.created"));
              // CRITICAL/NOTICE/WARNING rows must not appear.
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("matter.closure.override_used"));
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("matter.closure.completed"));
              assertThat(page.getContent())
                  .noneMatch(e -> e.getEventType().equals("security.login.failure"));
            });
  }

  @Test
  void nullOrEmptySeveritiesDoesNotFilter() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var nullPage =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, null),
                      PageRequest.of(0, 50));
              var emptyPage =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, Set.of()),
                      PageRequest.of(0, 50));

              // Both paths must return identical totals (no filter applied either way) and at
              // least the 5 rows we seeded above.
              assertThat(nullPage.getTotalElements()).isEqualTo(emptyPage.getTotalElements());
              assertThat(nullPage.getTotalElements()).isGreaterThanOrEqualTo(5);
            });
  }
}
