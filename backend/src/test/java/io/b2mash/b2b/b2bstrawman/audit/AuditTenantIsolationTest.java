package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests verifying tenant isolation for audit events across Pro and Starter tiers, as
 * well as append-only enforcement via the database trigger and entity immutability.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditTenantIsolationTest {

  private static final String PRO_ORG_A_ID = "org_audit_iso_pro_a";
  private static final String PRO_ORG_B_ID = "org_audit_iso_pro_b";
  private static final String STARTER_ORG_A_ID = "org_audit_iso_starter_a";
  private static final String STARTER_ORG_B_ID = "org_audit_iso_starter_b";

  @Autowired private AuditService auditService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String proSchemaA;
  private String proSchemaB;
  private String starterSchemaA;
  private String starterSchemaB;

  @BeforeAll
  void provisionTenants() {
    // Org A — dedicated schema
    var resultA = provisioningService.provisionTenant(PRO_ORG_A_ID, "Audit Iso A", null);
    proSchemaA = resultA.schemaName();

    // Org B — dedicated schema
    var resultB = provisioningService.provisionTenant(PRO_ORG_B_ID, "Audit Iso B", null);
    proSchemaB = resultB.schemaName();

    // Additional orgs — each gets its own dedicated schema
    starterSchemaA =
        provisioningService.provisionTenant(STARTER_ORG_A_ID, "Audit Iso C", null).schemaName();
    starterSchemaB =
        provisioningService.provisionTenant(STARTER_ORG_B_ID, "Audit Iso D", null).schemaName();
  }

  @Test
  void proOrgACannotSeeProOrgBEvents() {
    var entityIdA = UUID.randomUUID();
    var entityIdB = UUID.randomUUID();

    // Log event in Pro org A's schema
    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "project.created",
                        "project",
                        entityIdA,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    // Log event in Pro org B's schema
    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "project.created",
                        "project",
                        entityIdB,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    // Query from Pro org A — should only see org A's event
    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityIdA));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityIdB));
            });

    // Query from Pro org B — should only see org B's event
    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityIdB));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityIdA));
            });
  }

  @Test
  void starterOrgACannotSeeStarterOrgBEvents() {
    var entityIdA = UUID.randomUUID();
    var entityIdB = UUID.randomUUID();

    // Log event in Starter org A's dedicated schema
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaA)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "task.created",
                        "task",
                        entityIdA,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    // Log event in Starter org B's dedicated schema
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaB)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "task.created",
                        "task",
                        entityIdB,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    // Query from Starter org A's schema — should only see org A's event
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaA)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityIdA));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityIdB));
            });

    // Query from Starter org B's schema — should only see org B's event
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaB)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityIdB));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityIdA));
            });
  }

  @Test
  void eventPersistedInStarterOrgDedicatedSchema() {
    var entityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaA)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.auto_pop_test",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              // Use findEvents to verify the event was persisted in the current tenant schema
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null, null),
                      PageRequest.of(0, 1));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.auto_pop_test");
            });
  }

  @Test
  void updateOnAuditEventsRaisesException() {
    var entityId = UUID.randomUUID();
    var eventIdRef = new AtomicReference<UUID>();

    // Use Starter org A's dedicated schema for the immutability test.
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaA)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.immutable_test",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null, null),
                      PageRequest.of(0, 1));
              eventIdRef.set(page.getContent().getFirst().getId());
            });

    assertThat(eventIdRef.get()).isNotNull();

    // Attempt to UPDATE via native SQL — the DB trigger should reject it
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE \""
                        + starterSchemaA
                        + "\".audit_events SET event_type = 'tampered' WHERE id = ?::uuid",
                    eventIdRef.get().toString()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("audit_events rows cannot be updated");
  }

  @Test
  void deleteOnAuditEventsRaisesException() {
    var entityId = UUID.randomUUID();
    var eventIdRef = new AtomicReference<UUID>();

    // Use Starter org A's dedicated schema for the delete protection test.
    ScopedValue.where(RequestScopes.TENANT_ID, starterSchemaA)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.delete_test",
                      "task",
                      entityId,
                      null,
                      "SYSTEM",
                      "INTERNAL",
                      null,
                      null,
                      null));

              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null, null),
                      PageRequest.of(0, 1));
              eventIdRef.set(page.getContent().getFirst().getId());
            });

    assertThat(eventIdRef.get()).isNotNull();

    // Attempt to DELETE via native SQL — the DB trigger should reject it
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM \"" + starterSchemaA + "\".audit_events WHERE id = ?::uuid",
                    eventIdRef.get().toString()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("audit_events rows cannot be deleted");
  }

  @Test
  void facetsIsolatedAcrossTenants() {
    // 502A added AuditService.facets(from, to) which routes through three new repository
    // projection queries (projectActorFacets / projectEventTypeFacets / projectEntityTypeFacets).
    // Each must respect the tenant search_path. Seed events with UUID-derived event-types and
    // entity-types (guaranteed unique to this invocation, immune to test-ordering pollution
    // since other tests share the same schemas), then assert each tenant only sees its own.
    var rangeStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.MINUTES);
    var rangeEnd = rangeStart.plus(1, ChronoUnit.HOURS);
    var uniqueA = UUID.randomUUID().toString().substring(0, 8);
    var uniqueB = UUID.randomUUID().toString().substring(0, 8);
    var eventTypeA = "facetiso." + uniqueA;
    var eventTypeB = "facetiso." + uniqueB;
    var entityTypeA = "facetiso_entity_" + uniqueA;
    var entityTypeB = "facetiso_entity_" + uniqueB;

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        eventTypeA,
                        entityTypeA,
                        UUID.randomUUID(),
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        eventTypeB,
                        entityTypeB,
                        UUID.randomUUID(),
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () -> {
              var snapshot = auditService.facets(rangeStart, rangeEnd);
              assertThat(snapshot.eventTypes())
                  .as("tenant A must see its own event-type facet")
                  .anyMatch(et -> eventTypeA.equals(et.eventType()));
              assertThat(snapshot.eventTypes())
                  .as("tenant A must NOT see tenant B's event-type facet")
                  .noneMatch(et -> eventTypeB.equals(et.eventType()));
              assertThat(snapshot.entityTypes())
                  .as("tenant A must see its own entity-type facet")
                  .anyMatch(et -> entityTypeA.equals(et.entityType()));
              assertThat(snapshot.entityTypes())
                  .as("tenant A must NOT see tenant B's entity-type facet")
                  .noneMatch(et -> entityTypeB.equals(et.entityType()));
            });

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () -> {
              var snapshot = auditService.facets(rangeStart, rangeEnd);
              assertThat(snapshot.eventTypes())
                  .as("tenant B must see its own event-type facet")
                  .anyMatch(et -> eventTypeB.equals(et.eventType()));
              assertThat(snapshot.eventTypes())
                  .as("tenant B must NOT see tenant A's event-type facet")
                  .noneMatch(et -> eventTypeA.equals(et.eventType()));
              assertThat(snapshot.entityTypes())
                  .as("tenant B must see its own entity-type facet")
                  .anyMatch(et -> entityTypeB.equals(et.entityType()));
              assertThat(snapshot.entityTypes())
                  .as("tenant B must NOT see tenant A's entity-type facet")
                  .noneMatch(et -> entityTypeA.equals(et.entityType()));
            });
  }

  @Test
  void severityFilteredFindEventsIsolatedAcrossTenants() {
    // 502A added severity-filter routing through findByFilterWithEventTypes (a new native SQL
    // path with TEXT[] parameters and dynamic OR LIKE chain). The new SQL must respect the
    // tenant search_path. Seed CRITICAL events in two tenant schemas, query findEvents with
    // severities=Set.of(CRITICAL) from each tenant, and assert no cross-tenant leakage.
    var entityIdA = UUID.randomUUID();
    var entityIdB = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "matter.closure.override_used",
                        "matter",
                        entityIdA,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () ->
                auditService.log(
                    new AuditEventRecord(
                        "matter.closure.override_used",
                        "matter",
                        entityIdB,
                        null,
                        "SYSTEM",
                        "INTERNAL",
                        null,
                        null,
                        null)));

    var criticalOnly = Set.of(AuditSeverity.CRITICAL);

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaA)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, criticalOnly),
                      PageRequest.of(0, 100));
              assertThat(page.getContent())
                  .as("tenant A severity-filtered query must include its own CRITICAL event")
                  .anyMatch(e -> e.getEntityId().equals(entityIdA));
              assertThat(page.getContent())
                  .as("tenant A severity-filtered query must NOT include tenant B's CRITICAL event")
                  .noneMatch(e -> e.getEntityId().equals(entityIdB));
            });

    ScopedValue.where(RequestScopes.TENANT_ID, proSchemaB)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null, criticalOnly),
                      PageRequest.of(0, 100));
              assertThat(page.getContent())
                  .as("tenant B severity-filtered query must include its own CRITICAL event")
                  .anyMatch(e -> e.getEntityId().equals(entityIdB));
              assertThat(page.getContent())
                  .as("tenant B severity-filtered query must NOT include tenant A's CRITICAL event")
                  .noneMatch(e -> e.getEntityId().equals(entityIdA));
            });
  }

  @Test
  void auditEventHasNoMutableSetters() {
    // Verify that AuditEvent has no setter methods for mutable fields
    // No setter methods should exist on AuditEvent (immutable entity)
    Method[] methods = AuditEvent.class.getDeclaredMethods();
    var unexpectedSetters =
        Arrays.stream(methods)
            .filter(m -> m.getName().startsWith("set"))
            .map(Method::getName)
            .toList();

    assertThat(unexpectedSetters)
        .as("AuditEvent should have no setter methods — it is fully immutable")
        .isEmpty();
  }
}
