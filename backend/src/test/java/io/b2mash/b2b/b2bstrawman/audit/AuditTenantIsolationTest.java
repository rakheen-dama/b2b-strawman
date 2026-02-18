package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.provisioning.Tier;
import java.lang.reflect.Method;
import java.util.Arrays;
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
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String proSchemaA;
  private String proSchemaB;
  private String starterSchemaA;
  private String starterSchemaB;

  @BeforeAll
  void provisionTenants() {
    // Pro org A — dedicated schema
    var proOrgA = new Organization(PRO_ORG_A_ID, "Pro Audit Iso A");
    proOrgA.updatePlan(Tier.PRO, "pro_plan");
    organizationRepository.save(proOrgA);
    var resultA = provisioningService.provisionTenant(PRO_ORG_A_ID, "Pro Audit Iso A");
    proSchemaA = resultA.schemaName();

    // Pro org B — dedicated schema
    var proOrgB = new Organization(PRO_ORG_B_ID, "Pro Audit Iso B");
    proOrgB.updatePlan(Tier.PRO, "pro_plan");
    organizationRepository.save(proOrgB);
    var resultB = provisioningService.provisionTenant(PRO_ORG_B_ID, "Pro Audit Iso B");
    proSchemaB = resultB.schemaName();

    // Starter orgs — each gets its own dedicated schema
    starterSchemaA =
        provisioningService.provisionTenant(STARTER_ORG_A_ID, "Starter Audit Iso A").schemaName();
    starterSchemaB =
        provisioningService.provisionTenant(STARTER_ORG_B_ID, "Starter Audit Iso B").schemaName();
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
                      new AuditEventFilter(null, null, null, null, null, null),
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
                      new AuditEventFilter(null, null, null, null, null, null),
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
                      new AuditEventFilter(null, null, null, null, null, null),
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
                      new AuditEventFilter(null, null, null, null, null, null),
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
                      new AuditEventFilter(null, entityId, null, null, null, null),
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
                      new AuditEventFilter(null, entityId, null, null, null, null),
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
