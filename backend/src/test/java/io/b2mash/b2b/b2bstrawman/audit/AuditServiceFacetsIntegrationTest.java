package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * Integration tests for {@link AuditService#facets(Instant, Instant)} (502.4) covering:
 *
 * <ul>
 *   <li>empty range → three empty lists
 *   <li>populated range → expected counts plus registry-enriched event-type metadata (label,
 *       severity, group)
 *   <li>actor facet caps at 500 even when 600 distinct actors are seeded
 *   <li>actor facet applies the §12.3.4 fallback (live member name → "Former member ({uuid})") via
 *       the LEFT JOIN on members
 * </ul>
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditServiceFacetsIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_facets_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditServiceFacetsIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private UUID liveMemberId;

  @BeforeAll
  void provisionTenant() throws Exception {
    schemaName =
        provisioningSvc.provisionTenant(ORG_ID, "Audit Facets Test Org", null).schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_audit_facets_live",
            "audit_facets_live@test.com",
            "Live Facet Member",
            "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);
  }

  @Test
  void emptyRangeReturnsThreeEmptyLists() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // A range entirely in the past, before any seeding has happened in this test class.
              var to = Instant.parse("1970-01-02T00:00:00Z");
              var from = Instant.parse("1970-01-01T00:00:00Z");
              var snapshot = auditService.facets(from, to);
              assertThat(snapshot.actors()).isEmpty();
              assertThat(snapshot.eventTypes()).isEmpty();
              assertThat(snapshot.entityTypes()).isEmpty();
            });
  }

  @Test
  void populatedRangeReturnsExpectedCountsAndRegistryEnrichment() {
    var rangeStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.MINUTES);
    var rangeEnd = rangeStart.plus(1, ChronoUnit.HOURS);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // 3 events of two known event types (registry-resolved):
              // matter.closure.override_used (CRITICAL / COMPLIANCE) — 2 events
              // matter.closure.completed (NOTICE / COMPLIANCE via prefix) — 1 event
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.override_used",
                      "matter",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.override_used",
                      "matter",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.completed",
                      "matter",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));

              var snapshot = auditService.facets(rangeStart, rangeEnd);

              // EventType facet — registry enrichment.
              var override =
                  snapshot.eventTypes().stream()
                      .filter(e -> e.eventType().equals("matter.closure.override_used"))
                      .findFirst()
                      .orElseThrow();
              assertThat(override.severity()).isEqualTo(AuditSeverity.CRITICAL);
              assertThat(override.group()).isEqualTo(AuditEventGroup.COMPLIANCE);
              assertThat(override.label()).isEqualTo("Matter Closure Override Used");
              assertThat(override.count()).isEqualTo(2L);

              var prefixHit =
                  snapshot.eventTypes().stream()
                      .filter(e -> e.eventType().equals("matter.closure.completed"))
                      .findFirst()
                      .orElseThrow();
              // matter.closure.* prefix wins ⇒ NOTICE / COMPLIANCE / "Matter Closure"
              assertThat(prefixHit.severity()).isEqualTo(AuditSeverity.NOTICE);
              assertThat(prefixHit.group()).isEqualTo(AuditEventGroup.COMPLIANCE);
              assertThat(prefixHit.label()).isEqualTo("Matter Closure");
              assertThat(prefixHit.count()).isEqualTo(1L);

              // EntityType facet — title-cased label, count = 3.
              var matterEntity =
                  snapshot.entityTypes().stream()
                      .filter(e -> e.entityType().equals("matter"))
                      .findFirst()
                      .orElseThrow();
              assertThat(matterEntity.label()).isEqualTo("Matter");
              assertThat(matterEntity.count()).isEqualTo(3L);

              // Actor facet — live member resolves to its name; count covers all 3 events.
              var liveActor =
                  snapshot.actors().stream()
                      .filter(a -> liveMemberId.equals(a.actorId()))
                      .findFirst()
                      .orElseThrow();
              assertThat(liveActor.actorDisplayName()).isEqualTo("Live Facet Member");
              assertThat(liveActor.actorType()).isEqualTo("USER");
              assertThat(liveActor.eventCount()).isEqualTo(3L);
            });
  }

  @Test
  void actorFacetEnrichesFormerMemberFallback() {
    // Seed an event for an actorId that does NOT match any member row — the LEFT JOIN miss must
    // surface as the "Former member ({uuid})" fallback per architecture §12.3.4.
    var rangeStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.MINUTES);
    var rangeEnd = rangeStart.plus(1, ChronoUnit.HOURS);
    var ghostActorId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      ghostActorId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));

              var snapshot = auditService.facets(rangeStart, rangeEnd);

              var ghost =
                  snapshot.actors().stream()
                      .filter(a -> ghostActorId.equals(a.actorId()))
                      .findFirst()
                      .orElseThrow();
              assertThat(ghost.actorDisplayName())
                  .isEqualTo("Former member (" + ghostActorId + ")");
              assertThat(ghost.actorType()).isEqualTo("USER");
              assertThat(ghost.eventCount()).isEqualTo(1L);
            });
  }

  @Test
  void actorFacetCapsAt500Rows() {
    // Seed 600 distinct actorIds. Each gets a single event so the actor-facet ORDER BY count DESC
    // is stable — but the LIMIT 500 should clip the result regardless of ordering.
    var rangeStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.MINUTES);
    var rangeEnd = rangeStart.plus(1, ChronoUnit.HOURS);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              for (int i = 0; i < 600; i++) {
                auditService.log(
                    new AuditEventRecord(
                        "audit_facet_500_cap.event",
                        "audit_cap_entity",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "USER",
                        "API",
                        null,
                        null,
                        null));
              }

              var snapshot = auditService.facets(rangeStart, rangeEnd);

              // The actor list must be capped — total seeded is 600+ (other tests in this class may
              // contribute more), but the LIMIT clamps the result to <= 500.
              assertThat(snapshot.actors().size()).isLessThanOrEqualTo(500);
              // And we expect the cap to actually fire — at least 500 rows came back.
              assertThat(snapshot.actors().size()).isEqualTo(500);
            });
  }
}
