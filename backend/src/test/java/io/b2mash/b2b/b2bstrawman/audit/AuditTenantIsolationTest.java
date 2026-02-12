package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.provisioning.Tier;
import java.lang.reflect.Method;
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
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String proSchemaA;
  private String proSchemaB;

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

    // Starter orgs — shared schema
    provisioningService.provisionTenant(STARTER_ORG_A_ID, "Starter Audit Iso A");
    provisioningService.provisionTenant(STARTER_ORG_B_ID, "Starter Audit Iso B");
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

    // Log event in Starter org A context (shared schema with ORG_ID for tenant_id tagging)
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_A_ID)
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

    // Log event in Starter org B context
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_B_ID)
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

    // Query from Starter org A — Hibernate @Filter ensures isolation
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_A_ID)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, null, null, null, null, null),
                      PageRequest.of(0, 100));

              assertThat(page.getContent()).anyMatch(e -> e.getEntityId().equals(entityIdA));
              assertThat(page.getContent()).noneMatch(e -> e.getEntityId().equals(entityIdB));
            });

    // Query from Starter org B
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_B_ID)
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
  void tenantIdAutoPopulatedForStarterOrg() {
    var entityId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_A_ID)
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

              // Use findOneById (JPQL-based, respects @Filter) to verify tenant_id
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(null, entityId, null, null, null, null),
                      PageRequest.of(0, 1));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getTenantId()).isEqualTo(STARTER_ORG_A_ID);
            });
  }

  @Test
  void updateOnAuditEventsRaisesException() {
    var entityId = UUID.randomUUID();
    var eventIdRef = new AtomicReference<UUID>();

    // Use tenant_shared (Starter) where the trigger is guaranteed to exist.
    // The V14 migration creates the trigger with IF NOT EXISTS on pg_trigger.tgname,
    // which is global — so only the first schema to run the migration gets the trigger.
    // tenant_shared is always provisioned first.
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
        .where(RequestScopes.ORG_ID, STARTER_ORG_A_ID)
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
                    "UPDATE \"tenant_shared\".audit_events SET event_type = 'tampered' WHERE id = ?::uuid",
                    eventIdRef.get().toString()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("audit_events rows cannot be updated");
  }

  @Test
  void auditEventHasNoMutableSetters() {
    // Verify that AuditEvent has no setter methods for mutable fields
    // Only setTenantId (from TenantAware interface) should exist
    Set<String> allowedSetters = Set.of("setTenantId");

    Method[] methods = AuditEvent.class.getDeclaredMethods();
    var unexpectedSetters =
        Arrays.stream(methods)
            .filter(m -> m.getName().startsWith("set"))
            .filter(m -> !allowedSetters.contains(m.getName()))
            .map(Method::getName)
            .toList();

    assertThat(unexpectedSetters)
        .as("AuditEvent should have no setters except setTenantId (TenantAware)")
        .isEmpty();
  }
}
