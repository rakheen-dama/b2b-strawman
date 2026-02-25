package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link DatabaseAuditService} covering event logging, JSONB details, query
 * filters, pagination, and countByEventType. Tests run against a real Postgres via Testcontainers,
 * using a Pro-tier tenant with a dedicated schema.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditServiceIntegrationTest {

  private static final String ORG_ID = "org_audit_svc_test";

  @Autowired private AuditService auditService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;

  @BeforeAll
  void provisionTenant() {
    provisioningService.provisionTenant(ORG_ID, "Audit Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Resolve the schema name for this Pro org
    schemaName = provisioningService.provisionTenant(ORG_ID, "Audit Service Test Org").schemaName();
  }

  @Test
  void logEventAndVerifyPersisted() {
    var entityId = UUID.randomUUID();
    var actorId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "project.created", "project", entityId, actorId, "USER", "API", null, null, null);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(record);

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("project.created");
              assertThat(event.getEntityType()).isEqualTo("project");
              assertThat(event.getEntityId()).isEqualTo(entityId);
              assertThat(event.getActorId()).isEqualTo(actorId);
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
              assertThat(event.getOccurredAt()).isNotNull();
            });
  }

  @Test
  void logEventWithNullDetails() {
    var entityId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "task.deleted", "task", entityId, null, "SYSTEM", "SCHEDULED", null, null, null);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(record);

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // After BUG-010 fix: enrichActorName always populates actor_name, even when
              // original details are null. System actors get "System" as default actor_name.
              assertThat(event.getDetails()).containsEntry("actor_name", "System");
              assertThat(event.getActorId()).isNull();
            });
  }

  @Test
  void logEventWithJsonbDetails() {
    var entityId = UUID.randomUUID();
    var details = Map.<String, Object>of("old_status", "OPEN", "new_status", "CLOSED", "count", 5);
    var record =
        new AuditEventRecord(
            "task.updated",
            "task",
            entityId,
            UUID.randomUUID(),
            "USER",
            "API",
            null,
            null,
            details);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(record);

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getDetails()).containsEntry("old_status", "OPEN");
              assertThat(event.getDetails()).containsEntry("new_status", "CLOSED");
              assertThat(event.getDetails()).containsEntry("count", 5);
            });
  }

  @Test
  void queryByEntityType() {
    var entityId1 = UUID.randomUUID();
    var entityId2 = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "project.created",
                      "project",
                      entityId1,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "document.created",
                      "document",
                      entityId2,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var page =
                  auditService.findEvents(
                      new AuditEventFilter("project", null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent())
                  .allMatch(e -> e.getEntityType().equals("project"))
                  .anyMatch(e -> e.getEntityId().equals(entityId1));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityId2));
            });
  }

  @Test
  void queryByEntityId() {
    var targetId = UUID.randomUUID();
    var otherId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      targetId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      otherId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, targetId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              assertThat(page.getContent().getFirst().getEntityId()).isEqualTo(targetId);
            });
  }

  @Test
  void queryByActorId() {
    var entityId = UUID.randomUUID();
    var actorA = UUID.randomUUID();
    var actorB = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.created", "task", entityId, actorA, "USER", "API", null, null, null));
              auditService.log(
                  new AuditEventRecord(
                      "task.updated",
                      "task",
                      UUID.randomUUID(),
                      actorB,
                      "USER",
                      "API",
                      null,
                      null,
                      null));

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, actorA, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getContent()).allMatch(e -> e.getActorId().equals(actorA));
              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityId));
            });
  }

  @Test
  void queryByEventTypePrefix() {
    var taskEntityId = UUID.randomUUID();
    var projEntityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      taskEntityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "task.updated",
                      "task",
                      taskEntityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "project.created",
                      "project",
                      projEntityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              // Prefix "task." should match task.created and task.updated
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, "task.", null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).allMatch(e -> e.getEventType().startsWith("task."));
              assertThat(page.getContent().size()).isGreaterThanOrEqualTo(2);
            });
  }

  @Test
  void queryByTimeRange() {
    var entityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.time_range_test",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var now = Instant.now();
              var oneHourAgo = now.minus(1, ChronoUnit.HOURS);
              var oneHourFromNow = now.plus(1, ChronoUnit.HOURS);

              // Event occurred just now — should be within the range
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, oneHourAgo, oneHourFromNow),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);

              // Event occurred just now — should NOT be in a past-only range
              var pastOnly = now.minus(2, ChronoUnit.HOURS);
              var pastEnd = now.minus(1, ChronoUnit.HOURS);
              var emptyPage =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, pastOnly, pastEnd),
                      PageRequest.of(0, 10));

              assertThat(emptyPage.getTotalElements()).isZero();
            });
  }

  @Test
  void queryWithPagination() {
    var entityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Create 5 events with the same entityId
              for (int i = 0; i < 5; i++) {
                auditService.log(
                    new AuditEventRecord(
                        "task.pagination_test",
                        "task",
                        entityId,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null));
              }

              // Page 0, size 2 — should get 2 events, total 5
              var page0 =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 2));

              assertThat(page0.getContent()).hasSize(2);
              assertThat(page0.getTotalElements()).isEqualTo(5);
              assertThat(page0.getTotalPages()).isEqualTo(3);

              // Page 2, size 2 — should get 1 event (remainder)
              var page2 =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(2, 2));

              assertThat(page2.getContent()).hasSize(1);
            });
  }

  /**
   * Regression test for BUG-010: When an audit event has actorType PORTAL_USER and details already
   * contains "actor_name", the enrichActorName method must preserve the existing value (not
   * overwrite it). The cross-project activity query's COALESCE falls back to details->>'actor_name'
   * when no member match exists, so this ensures portal user names are correctly displayed.
   */
  @Test
  void enrichActorNamePreservesExistingActorNameInDetails() {
    var entityId = UUID.randomUUID();
    var details =
        Map.<String, Object>of(
            "actor_name", "Customer Jane", "project_id", UUID.randomUUID().toString());
    var record =
        new AuditEventRecord(
            "comment.created",
            "comment",
            entityId,
            null, // no actorId — portal users don't have member records
            "PORTAL_USER",
            "PORTAL",
            null,
            null,
            details);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(record);

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // BUG-010 fix: actor_name must be preserved, not overwritten
              assertThat(event.getDetails()).containsEntry("actor_name", "Customer Jane");
              assertThat(event.getActorType()).isEqualTo("PORTAL_USER");
            });
  }

  /**
   * Regression test for BUG-010: When actorType is USER and no actor_name is provided,
   * enrichActorName should resolve the name from the member repository. When actorId is null and no
   * actor_name is set, it should default to "System".
   */
  @Test
  void enrichActorNameDefaultsToSystemWhenNoActorIdAndNoExistingName() {
    var entityId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "task.system_update",
            "task",
            entityId,
            null, // no actorId
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            null); // no details at all

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(record);

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // enrichActorName should default to "System" when no actorId and no existing name
              assertThat(event.getDetails()).containsEntry("actor_name", "System");
            });
  }

  @Test
  void countByEventTypeReturnsCorrectCounts() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Log events with known types to ensure some counts exist
              var entityId = UUID.randomUUID();
              auditService.log(
                  new AuditEventRecord(
                      "count.test_a",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "count.test_a",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "count.test_b",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var counts = auditEventRepository.countByEventType();

              assertThat(counts).isNotEmpty();
              var testACounts =
                  counts.stream().filter(c -> "count.test_a".equals(c.getEventType())).findFirst();
              assertThat(testACounts).isPresent();
              assertThat(testACounts.get().getCount()).isGreaterThanOrEqualTo(2);

              var testBCounts =
                  counts.stream().filter(c -> "count.test_b".equals(c.getEventType())).findFirst();
              assertThat(testBCounts).isPresent();
              assertThat(testBCounts.get().getCount()).isGreaterThanOrEqualTo(1);
            });
  }
}
